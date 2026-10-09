package com.zerointrusion.data.repository.connecteddevices

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.zerointrusion.data.network.AccountDeviceDto
import com.zerointrusion.data.network.AccountDevicesResponseDto
import com.zerointrusion.data.network.DeleteAccountDeviceRequestDto
import com.zerointrusion.data.network.DeleteAccountDeviceResponseDto
import com.zerointrusion.data.network.RegistrationApi
import com.zerointrusion.data.repository.common.ACCOUNT_LOG_TAG
import com.zerointrusion.data.repository.common.buildAccountDeviceRemovalUrl
import com.zerointrusion.data.repository.common.buildAccountDevicesListUrl
import com.zerointrusion.data.repository.common.buildSignedAccountMobileDeviceNonceJson
import com.zerointrusion.data.repository.common.mapNetworkException
import com.zerointrusion.data.repository.common.newRequestNonce
import com.zerointrusion.data.security.SignatureService
import com.zerointrusion.data.storage.ConnectedDevicesLocalStore
import com.zerointrusion.data.storage.RegistrationStateStore
import com.zerointrusion.data.storage.toConnectedDeviceGroups
import com.zerointrusion.data.storage.toLocalConnectedDeviceEntries
import com.zerointrusion.data.storage.toNamespaceEntries
import com.zerointrusion.domain.common.RegistrationFailure
import com.zerointrusion.domain.connecteddevices.ConnectedDeviceGroup
import com.zerointrusion.domain.connecteddevices.ConnectedDevicesRepository
import com.zerointrusion.domain.connecteddevices.MobileNamespaceRegistration
import com.zerointrusion.domain.connecteddevices.RemovableAccountDevice
import com.zerointrusion.domain.namespaces.NamespaceEntry
import com.zerointrusion.domain.registration.RegistrationState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class ConnectedDevicesRepositoryImpl(
    private val accountDetailsSnapshotFetcher: AccountDetailsSnapshotFetcher,
    private val accountDetailsStateHolder: AccountDetailsStateHolder,
    private val connectedDevicesLocalStore: ConnectedDevicesLocalStore,
    private val stateStore: RegistrationStateStore,
    private val registrationApi: RegistrationApi,
    private val signatureService: SignatureService,
    private val gson: Gson,
) : ConnectedDevicesRepository {
    override fun observeConnectedDevices(): Flow<List<ConnectedDeviceGroup>> =
        connectedDevicesLocalStore.observeEntries().map { entries -> entries.toConnectedDeviceGroups() }

    override suspend fun fetchConnectedDevices(): List<ConnectedDeviceGroup> {
        val registrationsBeforeRefresh = connectedDevicesLocalStore.currentMobileRegistrations()
        val snapshot = accountDetailsSnapshotFetcher.fetchSnapshot()
        accountDetailsStateHolder.replaceSnapshot(snapshot)
        connectedDevicesLocalStore.replaceEntries(snapshot.namespaces.toLocalConnectedDeviceEntries())
        connectedDevicesLocalStore.updateMobileRegistrations { current ->
            mergeRefreshedMobileRegistrations(registrationsBeforeRefresh, current, snapshot.mobileRegistrations)
        }
        Log.d(
            ACCOUNT_LOG_TAG,
            "ACCOUNT details parsed response. groups=${accountDetailsSnapshotFetcher.summarizeGroups(snapshot.groups)}",
        )

        return connectedDevicesLocalStore.currentEntries().toConnectedDeviceGroups()
    }

    override suspend fun loadRemovableDevices(): List<RemovableAccountDevice> = withContext(Dispatchers.IO) {
        val state = requireRegistrationState()
        accountTargets(state).flatMap { target ->
            loadRemovableDevicesForTarget(
                namespaceUrl = target.namespaceUrl,
                accountId = target.accountId,
                mobileDeviceId = state.deviceId.trim(),
            )
        }.filterUniqueRemovableDevices(currentMobileDeviceId = state.deviceId)
    }

    override suspend fun deleteDevice(device: RemovableAccountDevice) = withContext(Dispatchers.IO) {
        val state = requireRegistrationState()
        val targetDesktopDeviceId = device.targetDesktopDeviceId?.trim()?.takeIf { it.isNotEmpty() }
        val targetMobileDeviceId = device.targetMobileDeviceId?.trim()?.takeIf { it.isNotEmpty() }
        if ((targetDesktopDeviceId == null) == (targetMobileDeviceId == null)) {
            throw RegistrationFailure.Validation("Select exactly one mobile or desktop device to remove.")
        }

        val request = DeleteAccountDeviceRequestDto(
            accountId = device.accountId.trim(),
            mobileDeviceId = state.deviceId.trim(),
            targetDesktopDeviceId = targetDesktopDeviceId,
            targetMobileDeviceId = targetMobileDeviceId,
            nonce = newRequestNonce(),
        )
        val url = buildAccountDeviceRemovalUrl(device.namespaceUrl)
        val response = try {
            registrationApi.deleteAccountDevice(
                url = url,
                signature = signatureService.signJsonString(gson.toJson(request)),
                request = request,
            )
        } catch (exception: Exception) {
            throw mapNetworkException(url, exception)
        }
        val body = response.body()?.string()?.trim().orEmpty()
        if (!response.isSuccessful) {
            throw RegistrationFailure.Server(body.ifEmpty { "Request failed with HTTP ${response.code()} at $url." })
        }
        val parsed = parseResponse<DeleteAccountDeviceResponseDto>(body, "device delete")
        if (parsed.success != true) {
            throw RegistrationFailure.Server(parsed.error ?: "The account device could not be deleted.")
        }

        if (targetMobileDeviceId == state.deviceId.trim()) {
            fetchConnectedDevices()
        }
    }

    private suspend fun requireRegistrationState() = stateStore.observeState().first()
        ?.also { state ->
            if (state.accountId.isBlank() || state.deviceId.isBlank() || state.namespaceUrl.isBlank()) {
                throw RegistrationFailure.Validation("The stored registration state does not contain valid account, device, and namespace data.")
            }
        }
        ?: throw RegistrationFailure.Validation("No registered account is stored on this device.")

    private suspend fun loadRemovableDevicesForTarget(
        namespaceUrl: String,
        accountId: String,
        mobileDeviceId: String,
    ): List<RemovableAccountDevice> {
        val nonce = newRequestNonce()
        val url = buildAccountDevicesListUrl(
            baseUrl = namespaceUrl,
            accountId = accountId,
            mobileDeviceId = mobileDeviceId,
            nonce = nonce,
        )
        val response = try {
            registrationApi.loadAccountDevices(
                url = url,
                signature = signatureService.signJsonString(
                    buildSignedAccountMobileDeviceNonceJson(
                        gson = gson,
                        accountId = accountId,
                        mobileDeviceId = mobileDeviceId,
                        nonce = nonce,
                    ),
                ),
            )
        } catch (exception: Exception) {
            throw mapNetworkException(url, exception)
        }
        val body = response.body()?.string()?.trim().orEmpty()
        if (!response.isSuccessful) {
            throw RegistrationFailure.Server(body.ifEmpty { "Request failed with HTTP ${response.code()} at $url." })
        }
        val parsed = parseResponse<AccountDevicesResponseDto>(body, "device list")
        if (parsed.success != true) {
            throw RegistrationFailure.Server(parsed.error ?: "The account devices could not be loaded.")
        }
        return parsed.devices.orEmpty().flatMap { device ->
            device.toRemovableDevices(accountId, namespaceUrl)
        }
    }

    private fun accountTargets(state: RegistrationState): List<AccountTarget> = resolveDeviceRemovalAccountTargets(
        state = state,
        namespaces = connectedDevicesLocalStore.currentEntries().toNamespaceEntries(),
        mobileRegistrations = connectedDevicesLocalStore.currentMobileRegistrations(),
    )

    private inline fun <reified T> parseResponse(body: String, operation: String): T {
        if (body.isEmpty()) {
            throw RegistrationFailure.Server("The backend returned an empty $operation response.")
        }
        val parsed = try {
            gson.fromJson(body, T::class.java)
        } catch (_: JsonSyntaxException) {
            throw RegistrationFailure.Server("The backend returned invalid JSON for the $operation response.")
        }
        return parsed ?: throw RegistrationFailure.Server("The backend returned an empty $operation response.")
    }

    private fun AccountDeviceDto.toRemovableDevices(
        accountId: String,
        namespaceUrl: String,
    ): List<RemovableAccountDevice> {
        val desktopTarget = desktopDeviceId?.trim()?.takeIf { it.isNotEmpty() }?.let { targetId ->
            RemovableAccountDevice(
                accountId = accountId,
                namespaceUrl = namespaceUrl,
                targetDesktopDeviceId = targetId,
                deviceName = deviceName?.trim()?.takeIf { it.isNotEmpty() },
                lastTimeUsed = lastTimeUsed?.trim()?.takeIf { it.isNotEmpty() },
            )
        }
        val mobileTarget = mobileDeviceId?.trim()?.takeIf { it.isNotEmpty() }?.let { targetId ->
            RemovableAccountDevice(
                accountId = accountId,
                namespaceUrl = namespaceUrl,
                targetMobileDeviceId = targetId,
                deviceName = deviceName?.trim()?.takeIf { it.isNotEmpty() },
                lastTimeUsed = lastTimeUsed?.trim()?.takeIf { it.isNotEmpty() },
            )
        }
        return listOfNotNull(mobileTarget, desktopTarget)
    }
}

internal data class AccountTarget(
    val namespaceUrl: String,
    val accountId: String,
)

internal fun resolveDeviceRemovalAccountTargets(
    state: RegistrationState,
    namespaces: List<NamespaceEntry>,
    mobileRegistrations: List<MobileNamespaceRegistration>,
): List<AccountTarget> {
    val storedTargets = namespaces.map { AccountTarget(it.namespaceUrl, it.accountId) }
    val mobileTargets = mobileRegistrations
        .filter { it.mobileDeviceId.trim() == state.deviceId.trim() }
        .map { AccountTarget(it.namespaceUrl, it.accountId) }
    return (storedTargets + mobileTargets + AccountTarget(state.namespaceUrl, state.accountId))
        .mapNotNull { target ->
            val namespaceUrl = target.namespaceUrl.trim().trimEnd('/')
            val accountId = target.accountId.trim()
            AccountTarget(namespaceUrl, accountId).takeIf { namespaceUrl.isNotEmpty() && accountId.isNotEmpty() }
        }
        .distinctBy { it.namespaceUrl.lowercase() + "|" + it.accountId.lowercase() }
        .sortedWith(compareBy({ it.namespaceUrl.lowercase() }, { it.accountId.lowercase() }))
}

internal fun List<RemovableAccountDevice>.filterUniqueRemovableDevices(
    currentMobileDeviceId: String,
): List<RemovableAccountDevice> {
    val currentMobileDeviceKey = currentMobileDeviceId.trim().lowercase()
    val seenDeviceKeys = mutableSetOf<String>()

    return filter { device ->
        val deviceId = device.id.trim().lowercase()
        if (device.isMobile && deviceId == currentMobileDeviceKey) {
            seenDeviceKeys.add("current-mobile|${device.namespaceUrl.trim().trimEnd('/').lowercase()}|$deviceId")
        } else {
            seenDeviceKeys.add("${if (device.isMobile) "mobile" else "desktop"}|$deviceId")
        }
    }
}
