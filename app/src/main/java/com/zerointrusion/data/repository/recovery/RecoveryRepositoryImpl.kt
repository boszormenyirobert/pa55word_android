package com.zerointrusion.data.repository.recovery

import android.util.Base64
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.zerointrusion.BuildConfig
import com.zerointrusion.data.firebase.FirebaseService
import com.zerointrusion.data.network.RecoveryApi
import com.zerointrusion.data.security.DekService
import com.zerointrusion.data.security.DeviceService
import com.zerointrusion.data.security.KeyStoreService
import com.zerointrusion.data.security.SignatureService
import com.zerointrusion.data.storage.ConnectedDevicesLocalStore
import com.zerointrusion.data.storage.RecoveryPendingStateStore
import com.zerointrusion.data.storage.RegistrationStateStore
import com.zerointrusion.domain.common.RegistrationFailure
import com.zerointrusion.domain.connecteddevices.MobileNamespaceRegistration
import com.zerointrusion.domain.recovery.RecoveryNamespace
import com.zerointrusion.domain.recovery.RecoveryPendingState
import com.zerointrusion.domain.recovery.RecoveryProgress
import com.zerointrusion.domain.recovery.RecoveryQrPayload
import com.zerointrusion.domain.recovery.RecoveryRepository
import com.zerointrusion.domain.recovery.RecoveryStage
import com.zerointrusion.domain.registration.RegistrationState
import java.net.URI
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody

class RecoveryRepositoryImpl @Inject constructor(
    private val recoveryApi: RecoveryApi,
    private val gson: Gson,
    private val keyStoreService: KeyStoreService,
    private val deviceService: DeviceService,
    private val dekService: DekService,
    private val signatureService: SignatureService,
    private val pendingStore: RecoveryPendingStateStore,
    private val registrationStateStore: RegistrationStateStore,
    private val connectedDevicesStore: ConnectedDevicesLocalStore,
    private val firebaseService: FirebaseService,
) : RecoveryRepository {
    override suspend fun current(): RecoveryPendingState? = pendingStore.observe().first()

    override suspend fun start(qrPayload: RecoveryQrPayload): RecoveryProgress {
        validateOrigin(qrPayload.apiBaseUrl)
        val existing = current()
        if (existing != null) {
            if (
                existing.recoveryId == qrPayload.recoveryId &&
                sameOrigin(existing.apiBaseUrl, qrPayload.apiBaseUrl)
            ) {
                return retryCandidate(existing)
            }
            discardPendingCandidate(existing)
        }

        val existingRegistration = registrationStateStore.observeState().first()
        val isAdditionalNamespaceRecovery = existingRegistration != null
        val device = if (existingRegistration == null) {
            keyStoreService.createFreshKeyPair()
            deviceService.createDevice(keyStoreService.getPublicKey())
        } else {
            if (!keyStoreService.hasKeyPair()) {
                throw RegistrationFailure.Security("The existing device keypair is unavailable. Use first-launch recovery on a new device.")
            }
            deviceService.createDevice(keyStoreService.getPublicKey()).copy(
                mobileDeviceId = existingRegistration.mobileDeviceId,
                deviceName = existingRegistration.deviceName,
            )
        }
        val candidateId = UUID.randomUUID().toString()
        val pairingCode = buildPairingCode()
        val hash = pairingHash(qrPayload.recoveryId, candidateId, device.mobileDeviceId, device.publicKey, pairingCode)
        val pending = RecoveryPendingState(
            recoveryId = qrPayload.recoveryId,
            apiBaseUrl = qrPayload.apiBaseUrl.trimEnd('/'),
            candidateId = candidateId,
            mobileDeviceId = device.mobileDeviceId,
            deviceName = device.deviceName,
            publicKey = device.publicKey,
            pairingCode = pairingCode,
            pairingCodeHash = hash,
            candidateRequestId = UUID.randomUUID().toString(),
            stage = RecoveryStage.CANDIDATE_SUBMITTING,
            isAdditionalNamespaceRecovery = isAdditionalNamespaceRecovery,
        )
        pendingStore.save(pending)
        return retryCandidate(pending)
    }

    override suspend fun refresh(): RecoveryProgress {
        val pending = current() ?: throw RegistrationFailure.Validation("No recovery is waiting on this device.")
        if (pending.stage == RecoveryStage.CANDIDATE_SUBMITTING) {
            return retryCandidate(pending)
        }
        if (pending.stage == RecoveryStage.DEK_RECEIVED) {
            return complete(pending)
        }
        val body = candidateBody("candidate_status", pending, null)
        val response = post(pending.apiBaseUrl + "/api/v1/recovery/candidate/status", body)
        val json = responseJson(response)
        val status = json.requiredString("status")
        return when (status) {
            "waiting_for_selection" -> RecoveryProgress(RecoveryStage.WAITING_FOR_SELECTION, pending.pairingCode, json.optionalString("expiresAt"))
            "selected", "dek_ready" -> retrieveDek(pending)
            "completed" -> throw RegistrationFailure.Server("Recovery was completed without local activation. Scan a new recovery code.")
            else -> throw RegistrationFailure.Server("Recovery cannot continue: $status.")
        }
    }

    override suspend fun cancel() {
        val pending = current() ?: return
        cancelPendingCandidate(pending)
        pendingStore.clear()
    }

    override suspend fun retryFcm(): String? {
        val registration = registrationStateStore.observeState().first()
            ?: return "No recovered registration is available for push retry."
        val namespaces = connectedDevicesStore.currentMobileRegistrations()
            .filter { it.mobileDeviceId == registration.mobileDeviceId && it.accountId == registration.accountId }
            .map { RecoveryNamespace(it.namespaceId, it.namespaceName, it.namespaceUrl, it.root) }
            .ifEmpty { listOf(RecoveryNamespace(registration.namespaceId, registration.namespaceName, registration.namespaceUrl, true)) }
        return syncFcm(registration, namespaces)
    }

    private suspend fun retryCandidate(pending: RecoveryPendingState): RecoveryProgress {
        val body = JsonObject().apply {
            addProperty("operation", "candidate")
            addProperty("nonce", UUID.randomUUID().toString())
            addProperty("requestId", pending.candidateRequestId)
            addProperty("recoveryId", pending.recoveryId)
            addProperty("candidateId", pending.candidateId)
            addProperty("mobileDeviceId", pending.mobileDeviceId)
            addProperty("publicKey", pending.publicKey)
            addProperty("deviceName", pending.deviceName)
            addProperty("pairingCodeHash", pending.pairingCodeHash)
        }
        val json = responseJson(post(pending.apiBaseUrl + "/api/v1/recovery/candidate", body))
        val status = json.requiredString("status")
        if (status != "waiting_for_selection") {
            throw RegistrationFailure.Server("Unexpected recovery candidate status: $status.")
        }
        val waiting = pending.copy(stage = RecoveryStage.WAITING_FOR_SELECTION, expiresAt = json.optionalString("expiresAt"))
        pendingStore.save(waiting)
        return RecoveryProgress(waiting.stage, waiting.pairingCode, waiting.expiresAt)
    }

    private suspend fun discardPendingCandidate(pending: RecoveryPendingState) {
        try {
            cancelPendingCandidate(pending)
        } catch (exception: CancellationException) {
            throw exception
        } catch (_: Exception) { }
        pendingStore.clear()
    }

    private suspend fun cancelPendingCandidate(pending: RecoveryPendingState) {
        val body = candidateBody("candidate_cancellation", pending, UUID.randomUUID().toString())
        responseJson(post(pending.apiBaseUrl + "/api/v1/recovery/candidate/cancellation", body))
    }

    private suspend fun retrieveDek(pending: RecoveryPendingState): RecoveryProgress {
        val body = candidateBody("candidate_dek", pending, null)
        val json = responseJson(post(pending.apiBaseUrl + "/api/v1/recovery/candidate/dek", body))
        require(json.requiredString("recoveryId") == pending.recoveryId) { "Recovery ID mismatch." }
        require(json.requiredString("candidateId") == pending.candidateId) { "Candidate ID mismatch." }
        require(json.requiredString("mobileDeviceId") == pending.mobileDeviceId) { "Mobile device ID mismatch." }
        val encryptedDek = json.requiredString("encDek")
        if (dekService.decryptDek(encryptedDek).size != 32) {
            throw RegistrationFailure.Security("The recovered DEK has an invalid length.")
        }
        val recoveryOrigin = pending.apiBaseUrl.trimEnd('/')
        val namespaces = json.getAsJsonArray("namespaces")?.map { element ->
            val namespace = element.asJsonObject
            val namespaceUrl = namespace.requiredString("url")
            RecoveryNamespace(
                namespaceId = namespace.requiredString("namespaceId"),
                name = namespace.requiredString("name"),
                url = namespaceUrl,
                root = namespaceUrl.trimEnd('/').equals(recoveryOrigin, ignoreCase = true),
            )
        }.orEmpty()
        if (namespaces.count { it.root } != 1) {
            throw RegistrationFailure.Server("The recovery response must contain exactly one namespace for the approved recovery server URL.")
        }
        val recoveredNamespaces = if (pending.isAdditionalNamespaceRecovery) {
            listOf(namespaces.single { it.root })
        } else {
            namespaces
        }
        val ready = pending.copy(
            stage = RecoveryStage.DEK_RECEIVED,
            completionRequestId = pending.completionRequestId ?: UUID.randomUUID().toString(),
            accountId = json.requiredString("accountId"),
            encDek = encryptedDek,
            email = json.requiredString("email"),
            phone = json.requiredString("phone"),
            namespaces = recoveredNamespaces,
        )
        pendingStore.save(ready)
        return complete(ready)
    }

    private suspend fun complete(pending: RecoveryPendingState): RecoveryProgress {
        val body = candidateBody("completion", pending, requireNotNull(pending.completionRequestId))
        val json = responseJson(post(pending.apiBaseUrl + "/api/v1/recovery/completion", body))
        if (json.requiredString("status") != "completed") {
            throw RegistrationFailure.Server("Recovery completion was not accepted.")
        }
        val fcmErrorMessage = activate(pending)
        return RecoveryProgress(
            stage = RecoveryStage.DEK_RECEIVED,
            pairingCode = null,
            expiresAt = pending.expiresAt,
            isCompleted = true,
            fcmErrorMessage = fcmErrorMessage,
        )
    }

    private suspend fun activate(pending: RecoveryPendingState): String? {
        if (pending.isAdditionalNamespaceRecovery) {
            return activateAdditionalNamespace(pending)
        }
        val root = pending.namespaces.single { it.root }
        val registration = RegistrationState(
            accountId = requireNotNull(pending.accountId),
            mobileDeviceId = pending.mobileDeviceId,
            deviceName = pending.deviceName,
            namespaceName = root.name,
            namespaceUrl = root.url,
            namespaceId = root.namespaceId,
            email = requireNotNull(pending.email),
            phone = requireNotNull(pending.phone),
            encDek = requireNotNull(pending.encDek),
            root = true,
        )
        registrationStateStore.save(registration)
        connectedDevicesStore.updateMobileRegistrations { existing ->
            (existing.filterNot { it.mobileDeviceId == pending.mobileDeviceId } + pending.namespaces.map { namespace ->
                MobileNamespaceRegistration(
                    accountId = registration.accountId,
                    mobileDeviceId = registration.mobileDeviceId,
                    namespaceId = namespace.namespaceId,
                    namespaceName = namespace.name,
                    namespaceUrl = namespace.url,
                    encDek = requireNotNull(pending.encDek),
                    root = namespace.root,
                )
            }).distinctBy { it.accountId + "|" + it.mobileDeviceId + "|" + it.namespaceId + "|" + it.namespaceUrl }
        }
        pendingStore.clear()
        return syncFcm(registration, pending.namespaces)
    }

    private suspend fun activateAdditionalNamespace(pending: RecoveryPendingState): String? {
        val registration = registrationStateStore.observeState().first()
            ?: throw RegistrationFailure.Validation("The existing phone registration is no longer available.")
        val recoveredNamespace = pending.namespaces.single()
        val recoveredAccountId = requireNotNull(pending.accountId)

        connectedDevicesStore.updateMobileRegistrations { existing ->
            val updated = existing.filterNot { stored ->
                stored.accountId == recoveredAccountId &&
                    stored.mobileDeviceId == registration.mobileDeviceId &&
                    sameOrigin(stored.namespaceUrl, recoveredNamespace.url)
            } + MobileNamespaceRegistration(
                accountId = recoveredAccountId,
                mobileDeviceId = registration.mobileDeviceId,
                namespaceId = recoveredNamespace.namespaceId,
                namespaceName = recoveredNamespace.name,
                namespaceUrl = recoveredNamespace.url,
                encDek = requireNotNull(pending.encDek),
                root = false,
            )
            updated.distinctBy { stored ->
                listOf(
                    stored.accountId.trim().lowercase(),
                    stored.mobileDeviceId.trim().lowercase(),
                    stored.namespaceId.trim().lowercase(),
                    stored.namespaceUrl.trim().trimEnd('/').lowercase(),
                ).joinToString(separator = "|")
            }
        }
        return syncFcm(
            registration = registration,
            namespaces = listOf(recoveredNamespace),
            accountId = recoveredAccountId,
        )
    }

    private suspend fun syncFcm(
        registration: RegistrationState,
        namespaces: List<RecoveryNamespace>,
        accountId: String = registration.accountId,
    ): String? {
        val token = runCatching { firebaseService.getFcmToken() }
            .getOrElse { return it.message ?: "Push token retrieval failed." }
        namespaces.forEach { namespace ->
            val failure = runCatching {
                firebaseService.registerFcmToken(namespace.url, accountId, registration.mobileDeviceId, token)
            }.exceptionOrNull()
            if (failure != null) {
                return failure.message ?: "Push registration failed."
            }
        }
        return null
    }

    private suspend fun post(url: String, payload: JsonObject): retrofit2.Response<okhttp3.ResponseBody> {
        val json = gson.toJson(payload)
        return recoveryApi.post(
            url = url,
            signature = signatureService.signJsonString(json),
            body = json.toRequestBody("application/json; charset=utf-8".toMediaType()),
        )
    }

    private fun responseJson(response: retrofit2.Response<okhttp3.ResponseBody>): JsonObject {
        if (!response.isSuccessful) {
            val body = response.errorBody()?.string()?.takeIf { it.isNotBlank() }
            throw RegistrationFailure.Server(body ?: "Recovery request failed with HTTP ${response.code()}.")
        }
        return runCatching { JsonParser.parseString(response.body()?.string().orEmpty()).asJsonObject }
            .getOrElse { throw RegistrationFailure.Server("The recovery server returned an invalid response.") }
    }

    private fun candidateBody(operation: String, pending: RecoveryPendingState, requestId: String?): JsonObject = JsonObject().apply {
        addProperty("operation", operation)
        addProperty("nonce", UUID.randomUUID().toString())
        requestId?.let { addProperty("requestId", it) }
        addProperty("recoveryId", pending.recoveryId)
        addProperty("candidateId", pending.candidateId)
        addProperty("mobileDeviceId", pending.mobileDeviceId)
    }

    private fun pairingHash(recoveryId: String, candidateId: String, mobileDeviceId: String, publicKey: String, pairingCode: String): String {
        val values = JsonArray().apply {
            add("recovery-pairing-v1")
            add(recoveryId)
            add(candidateId)
            add(mobileDeviceId)
            add(publicKey)
            add(pairingCode)
        }
        return MessageDigest.getInstance("SHA-256").digest(gson.toJson(values).toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }

    private fun buildPairingCode(): String = buildString(12) {
        val random = SecureRandom()
        repeat(12) { append(random.nextInt(10)) }
    }

    private fun validateOrigin(origin: String) {
        val uri = runCatching { URI(origin) }.getOrElse { throw RegistrationFailure.Validation("The recovery server URL is invalid.") }
        if (uri.host.isNullOrBlank() || uri.userInfo != null || uri.query != null || uri.fragment != null || uri.path !in listOf("", "/")) {
            throw RegistrationFailure.Validation("The recovery server URL is invalid.")
        }
        if (uri.scheme != "https" && (uri.scheme != "http" || !BuildConfig.DEBUG)) {
            throw RegistrationFailure.Validation("Recovery requires HTTPS outside development builds.")
        }
    }

    private fun sameOrigin(first: String, second: String): Boolean =
        first.trim().trimEnd('/').equals(second.trim().trimEnd('/'), ignoreCase = true)

    private fun JsonObject.requiredString(name: String): String = get(name)?.asString?.takeIf { it.isNotBlank() }
        ?: throw RegistrationFailure.Server("The recovery response is missing $name.")

    private fun JsonObject.optionalString(name: String): String? = get(name)?.asString?.takeIf { it.isNotBlank() }
}