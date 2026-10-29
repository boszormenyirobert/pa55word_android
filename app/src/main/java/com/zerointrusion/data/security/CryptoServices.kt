package com.zerointrusion.data.security

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyInfo
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import android.security.keystore.UserNotAuthenticatedException
import android.util.Base64
import android.util.Log
import com.zerointrusion.core.AppConstants
import com.zerointrusion.domain.common.RegistrationFailure
import com.zerointrusion.domain.registration.DekPayload
import com.zerointrusion.domain.registration.Device
import com.zerointrusion.domain.registration.Namespace
import java.nio.charset.StandardCharsets
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.ProviderException
import java.security.PublicKey
import java.security.SecureRandom
import java.security.Signature
import java.security.spec.MGF1ParameterSpec
import java.security.spec.PSSParameterSpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.spec.OAEPParameterSpec
import javax.crypto.spec.PSource
import javax.inject.Inject
import javax.inject.Singleton

interface KeyStoreService {
    fun hasKeyPair(): Boolean

    fun ensureKeyPair()

    fun createFreshKeyPair()

    fun verifyBiometricAccess(attempt: Long)

    fun getPublicKey(): PublicKey

    fun sign(data: ByteArray): ByteArray

    fun decrypt(
        data: ByteArray,
        transformation: String = "RSA/ECB/OAEPWithSHA-1AndMGF1Padding",
        oaepSpec: OAEPParameterSpec? = null,
    ): ByteArray
}

interface DeviceService {
    fun createDevice(publicKey: PublicKey): Device
}

interface NamespaceService {
    fun createNamespace(name: String, url: String): Namespace
}

interface DekService {
    fun createEncryptedDek(publicKey: PublicKey, mobileDeviceId: String): DekPayload

    fun decryptDek(encDek: String): ByteArray

    fun encryptDek(rawDek: ByteArray, encodedPublicKey: String): String
}

interface SignatureService {
    fun signJsonString(json: String): String
}

@Singleton
class AndroidKeyStoreService
@Inject
constructor(private val context: Context, private val keyAccess: BiometricKeyAccess) :
    KeyStoreService {
    override fun hasKeyPair(): Boolean = loadKeyStore().containsAlias(AppConstants.KEYSTORE_ALIAS)

    override fun ensureKeyPair() {
        val keyStore = loadKeyStore()
        if (keyStore.containsAlias(AppConstants.KEYSTORE_ALIAS)) {
            requireHardwareKey()
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && hasStrongBox()) {
            runCatching { generateKeyPair(useStrongBox = true) }
                .onSuccess {
                    requireHardwareKey()
                    return
                }
        }

        generateKeyPair(useStrongBox = false)
        requireHardwareKey()
    }

    override fun createFreshKeyPair() {
        loadKeyStore().deleteEntry(AppConstants.KEYSTORE_ALIAS)
        ensureKeyPair()
    }

    override fun sign(data: ByteArray): ByteArray {
        keyAccess.requireUnlocked()
        return privateKeyOperation { signInternal(data) }
    }

    override fun verifyBiometricAccess(attempt: Long) {
        keyAccess.requireAuthenticationAttempt(attempt)
        privateKeyOperation {
            val challenge = ByteArray(32).also(SecureRandom()::nextBytes)
            val signed = signInternal(challenge)
            val verified =
                Signature.getInstance("SHA256withRSA/PSS").run {
                    setParameter(
                        PSSParameterSpec("SHA-256", "MGF1", MGF1ParameterSpec.SHA256, 32, 1)
                    )
                    initVerify(getPublicKey())
                    update(challenge)
                    verify(signed)
                }
            if (!verified) throw RegistrationFailure.DeviceKeyInvalidated()
        }
        keyAccess.confirmAuthentication(attempt)
    }

    private fun signInternal(data: ByteArray): ByteArray {
        requireExistingKey()
        return Signature.getInstance("SHA256withRSA/PSS").run {
            setParameter(PSSParameterSpec("SHA-256", "MGF1", MGF1ParameterSpec.SHA256, 32, 1))
            initSign(getPrivateKey())
            update(data)
            sign()
        }
    }

    override fun getPublicKey(): PublicKey {
        ensureKeyPair()
        return loadKeyStore().getCertificate(AppConstants.KEYSTORE_ALIAS)?.publicKey
            ?: throw RegistrationFailure.Security("Unable to read the generated device key.")
    }

    override fun decrypt(
        data: ByteArray,
        transformation: String,
        oaepSpec: OAEPParameterSpec?,
    ): ByteArray {
        keyAccess.requireUnlocked()
        return privateKeyOperation {
            requireExistingKey()
            Cipher.getInstance(transformation).run {
                if (oaepSpec != null) {
                    init(Cipher.DECRYPT_MODE, getPrivateKey(), oaepSpec)
                } else {
                    init(Cipher.DECRYPT_MODE, getPrivateKey())
                }
                doFinal(data)
            }
        }
    }

    private fun requireExistingKey() {
        if (!hasKeyPair()) throw RegistrationFailure.DeviceKeyInvalidated()
        requireHardwareKey()
    }

    private inline fun <T> privateKeyOperation(operation: () -> T): T =
        try {
            operation()
        } catch (_: UserNotAuthenticatedException) {
            keyAccess.lock()
            throw RegistrationFailure.BiometricAuthenticationRequired()
        } catch (_: KeyPermanentlyInvalidatedException) {
            keyAccess.lock()
            throw RegistrationFailure.DeviceKeyInvalidated()
        } catch (_: java.security.UnrecoverableKeyException) {
            keyAccess.lock()
            throw RegistrationFailure.DeviceKeyInvalidated()
        } catch (failure: RegistrationFailure.DeviceKeyInvalidated) {
            keyAccess.lock()
            throw failure
        }

    private fun loadKeyStore(): KeyStore =
        KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }

    private fun generateKeyPair(useStrongBox: Boolean) {
        try {
            val generator =
                KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_RSA, ANDROID_KEY_STORE)
            val builder =
                KeyGenParameterSpec.Builder(
                        AppConstants.KEYSTORE_ALIAS,
                        KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_DECRYPT,
                    )
                    .setKeySize(2048)
                    .setDigests(KeyProperties.DIGEST_SHA1, KeyProperties.DIGEST_SHA256)
                    .setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PSS)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_RSA_OAEP)
                    .setUserAuthenticationRequired(true)
                    .setUserAuthenticationParameters(
                        BiometricKeyAccess.VALIDITY_SECONDS,
                        KeyProperties.AUTH_BIOMETRIC_STRONG,
                    )

            if (useStrongBox && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                builder.setIsStrongBoxBacked(true)
            }

            generator.initialize(builder.build())
            generator.generateKeyPair()
        } catch (exception: ProviderException) {
            if (useStrongBox) {
                throw RegistrationFailure.Security(
                    "StrongBox generation failed; falling back to Keystore."
                )
            }
            throw RegistrationFailure.Security("Unable to generate the device keypair.")
        } catch (exception: Exception) {
            if (useStrongBox) {
                throw RegistrationFailure.Security(
                    "StrongBox generation failed; falling back to Keystore."
                )
            }
            throw RegistrationFailure.Security("Unable to generate the device keypair.")
        }
    }

    private fun requireHardwareKey() {
        val privateKey = getPrivateKey()
        val info =
            KeyFactory.getInstance(KeyProperties.KEY_ALGORITHM_RSA, ANDROID_KEY_STORE)
                .getKeySpec(privateKey, KeyInfo::class.java)
        val hardware =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                info.securityLevel == KeyProperties.SECURITY_LEVEL_TRUSTED_ENVIRONMENT ||
                    info.securityLevel == KeyProperties.SECURITY_LEVEL_STRONGBOX
            } else {
                info.isInsideSecureHardware
            }
        val purposes = KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_DECRYPT
        if (
            !hardware ||
                privateKey.encoded != null ||
                info.origin != KeyProperties.ORIGIN_GENERATED ||
                info.keySize != 2048 ||
                info.purposes and purposes != purposes ||
                KeyProperties.DIGEST_SHA256 !in info.digests ||
                KeyProperties.SIGNATURE_PADDING_RSA_PSS !in info.signaturePaddings ||
                KeyProperties.ENCRYPTION_PADDING_RSA_OAEP !in info.encryptionPaddings ||
                !info.isUserAuthenticationRequired ||
                !info.isUserAuthenticationRequirementEnforcedBySecureHardware ||
                info.userAuthenticationType != KeyProperties.AUTH_BIOMETRIC_STRONG ||
                info.userAuthenticationValidityDurationSeconds !=
                    BiometricKeyAccess.VALIDITY_SECONDS
        ) {
            throw RegistrationFailure.DeviceKeyInvalidated()
        }
    }

    private fun hasStrongBox(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
            context.packageManager.hasSystemFeature("android.hardware.strongbox_keystore")

    private fun getPrivateKey(): PrivateKey =
        loadKeyStore().getKey(AppConstants.KEYSTORE_ALIAS, null) as? PrivateKey
            ?: throw RegistrationFailure.Security("Unable to access the device private key.")

    private companion object {
        const val ANDROID_KEY_STORE = "AndroidKeyStore"
    }
}

@Singleton
class DefaultDeviceService @Inject constructor() : DeviceService {
    private val secureRandom = SecureRandom()
    private val deviceAlphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"

    override fun createDevice(publicKey: PublicKey): Device {
        val encodedKey = Base64.encodeToString(publicKey.encoded, Base64.NO_WRAP)
        val mobileDeviceId =
            buildString(10) {
                repeat(10) { append(deviceAlphabet[secureRandom.nextInt(deviceAlphabet.length)]) }
            }
        val deviceName = buildDeviceName()

        return Device(
            mobileDeviceId = mobileDeviceId,
            publicKey = encodedKey,
            deviceName = deviceName,
        )
    }

    private fun buildDeviceName(): String {
        val manufacturer = Build.MANUFACTURER?.trim().orEmpty()
        val model = Build.MODEL?.trim().orEmpty()
        return when {
            manufacturer.isEmpty() && model.isEmpty() -> "Android device"
            model.startsWith(manufacturer, ignoreCase = true) -> model
            manufacturer.isEmpty() -> model
            model.isEmpty() -> manufacturer
            else -> "$manufacturer $model"
        }
    }
}

@Singleton
class DefaultNamespaceService @Inject constructor() : NamespaceService {
    override fun createNamespace(name: String, url: String): Namespace {
        val normalizedName = name.trim()
        val normalizedUrl = url.trim().trimEnd('/')
        // Keep namespace IDs deterministic while fitting the backend's shorter identifier format.
        val seed = "$normalizedName|$normalizedUrl"
        val digest =
            MessageDigest.getInstance("SHA-256").digest(seed.toByteArray(StandardCharsets.UTF_8))
        val namespaceId = digest.joinToString(separator = "") { "%02x".format(it) }.take(10)

        return Namespace(namespaceId = namespaceId, name = normalizedName, url = normalizedUrl)
    }
}

@Singleton
class DefaultDekService @Inject constructor(private val keyStoreService: KeyStoreService) :
    DekService {
    private companion object {
        const val DEK_LOG_TAG = "ZeroIntrusionQr"
        const val OAEP_SHA1_TRANSFORMATION = "RSA/ECB/OAEPWithSHA-1AndMGF1Padding"

        val OAEP_PROBE_ORDER = listOf(OaepCompatibilityMode.ExplicitSha1Mgf1Sha1)
    }

    private enum class OaepCompatibilityMode {
        ExplicitSha1Mgf1Sha1
    }

    private val secureRandom = SecureRandom()
    private val oaepSpecSha1Mgf1Sha1 =
        OAEPParameterSpec("SHA-1", "MGF1", MGF1ParameterSpec.SHA1, PSource.PSpecified.DEFAULT)
    @Volatile private var resolvedOaepMode: OaepCompatibilityMode? = null

    override fun createEncryptedDek(publicKey: PublicKey, mobileDeviceId: String): DekPayload {
        val rawDek = ByteArray(32).also(secureRandom::nextBytes)
        val mode = resolveOaepCompatibilityMode()
        val encrypted = encryptWithMode(rawDek, publicKey, mode)

        return DekPayload(
            mobileDeviceId = mobileDeviceId,
            encDek = Base64.encodeToString(encrypted, Base64.NO_WRAP),
        )
    }

    override fun decryptDek(encDek: String): ByteArray {
        Log.d(DEK_LOG_TAG, "DEK decrypt start. encDekLength=${encDek.length}")
        val encrypted =
            decodeBase64(encDek, "The encrypted DEK returned by the backend is not valid Base64.")
        val preferredMode = resolvedOaepMode ?: resolveOaepCompatibilityMode()
        val candidateModes =
            listOf(preferredMode) + OAEP_PROBE_ORDER.filterNot { it == preferredMode }

        candidateModes.forEach { mode ->
            runCatching { decryptWithMode(encrypted, mode) }
                .onSuccess { rawDek ->
                    resolvedOaepMode = mode
                    Log.d(DEK_LOG_TAG, "DEK decrypt success. mode=$mode rawDekSize=${rawDek.size}")
                    return rawDek
                }
                .onFailure { failure ->
                    if (
                        failure is RegistrationFailure.BiometricAuthenticationRequired ||
                            failure is RegistrationFailure.DeviceKeyInvalidated
                    )
                        throw failure
                    Log.w(DEK_LOG_TAG, "DEK decrypt failed in mode=$mode: ${failure.message}")
                }
        }

        Log.e(DEK_LOG_TAG, "DEK decrypt failed with all supported OAEP compatibility modes.")
        throw RegistrationFailure.Security(
            "Unable to decrypt the stored DEK with the device private key."
        )
    }

    override fun encryptDek(rawDek: ByteArray, encodedPublicKey: String): String {
        Log.d(
            DEK_LOG_TAG,
            "DEK re-encrypt start. rawDekSize=${rawDek.size}, publicKeyLength=${encodedPublicKey.length}",
        )
        val publicKey = parsePublicKey(encodedPublicKey)
        val mode = resolveOaepCompatibilityMode()
        val encrypted =
            try {
                encryptWithMode(rawDek, publicKey, mode)
            } catch (failure: RegistrationFailure) {
                Log.e(
                    DEK_LOG_TAG,
                    "DEK re-encrypt failed with registration error: ${failure.message}",
                    failure,
                )
                throw failure
            } catch (_: Exception) {
                Log.e(DEK_LOG_TAG, "DEK re-encrypt failed for scanned device public key.")
                throw RegistrationFailure.Security(
                    "Unable to encrypt the DEK for the scanned device public key."
                )
            }

        return Base64.encodeToString(encrypted, Base64.NO_WRAP).also { encodedDek ->
            Log.d(
                DEK_LOG_TAG,
                "DEK re-encrypt success. mode=$mode encDekLength=${encodedDek.length}",
            )
        }
    }

    private fun resolveOaepCompatibilityMode(): OaepCompatibilityMode {
        resolvedOaepMode?.let {
            return it
        }

        val probePublicKey = keyStoreService.getPublicKey()
        val probeDek = ByteArray(32).also(secureRandom::nextBytes)

        OAEP_PROBE_ORDER.forEach { mode ->
            val encrypted =
                runCatching { encryptWithMode(probeDek, probePublicKey, mode) }
                    .getOrElse { failure ->
                        Log.w(
                            DEK_LOG_TAG,
                            "DEK OAEP probe encrypt failed for mode=$mode: ${failure.message}",
                        )
                        return@forEach
                    }

            val decrypted =
                runCatching { decryptWithMode(encrypted, mode) }
                    .getOrElse { failure ->
                        if (
                            failure is RegistrationFailure.BiometricAuthenticationRequired ||
                                failure is RegistrationFailure.DeviceKeyInvalidated
                        )
                            throw failure
                        Log.w(
                            DEK_LOG_TAG,
                            "DEK OAEP probe decrypt failed for mode=$mode: ${failure.message}",
                        )
                        return@forEach
                    }

            if (decrypted.contentEquals(probeDek)) {
                resolvedOaepMode = mode
                Log.d(DEK_LOG_TAG, "DEK OAEP compatibility mode selected: $mode")
                return mode
            }
        }

        throw RegistrationFailure.Security(
            "Unable to find a compatible RSA OAEP mode for the current device keystore."
        )
    }

    private fun encryptWithMode(
        rawDek: ByteArray,
        publicKey: PublicKey,
        mode: OaepCompatibilityMode,
    ): ByteArray {
        val transformation =
            when (mode) {
                OaepCompatibilityMode.ExplicitSha1Mgf1Sha1 -> OAEP_SHA1_TRANSFORMATION
            }
        val spec =
            when (mode) {
                OaepCompatibilityMode.ExplicitSha1Mgf1Sha1 -> oaepSpecSha1Mgf1Sha1
            }

        return Cipher.getInstance(transformation).run {
            init(Cipher.ENCRYPT_MODE, publicKey, spec)
            doFinal(rawDek)
        }
    }

    private fun decryptWithMode(encryptedDek: ByteArray, mode: OaepCompatibilityMode): ByteArray =
        when (mode) {
            OaepCompatibilityMode.ExplicitSha1Mgf1Sha1 ->
                decryptWithSpec(encryptedDek, OAEP_SHA1_TRANSFORMATION, oaepSpecSha1Mgf1Sha1)
        }

    private fun decryptWithSpec(
        encryptedDek: ByteArray,
        transformation: String,
        spec: OAEPParameterSpec,
    ): ByteArray =
        keyStoreService.decrypt(
            data = encryptedDek,
            transformation = transformation,
            oaepSpec = spec,
        )

    private fun parsePublicKey(encodedPublicKey: String): PublicKey {
        val keyBytes =
            decodeBase64(
                encodedPublicKey,
                "The QR payload publicKey is not valid Base64 X.509 SubjectPublicKeyInfo data.",
            )
        return try {
            KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(keyBytes))
        } catch (_: Exception) {
            throw RegistrationFailure.Validation(
                "The QR payload publicKey is not a valid RSA X.509 SubjectPublicKeyInfo key."
            )
        }
    }

    private fun decodeBase64(value: String, message: String): ByteArray =
        try {
            Base64.decode(value, Base64.DEFAULT)
        } catch (_: IllegalArgumentException) {
            throw RegistrationFailure.Validation(message)
        }
}

@Singleton
class DefaultSignatureService @Inject constructor(private val keyStoreService: KeyStoreService) :
    SignatureService {
    override fun signJsonString(json: String): String {
        val signature = keyStoreService.sign(json.toByteArray(StandardCharsets.UTF_8))
        return Base64.encodeToString(signature, Base64.NO_WRAP)
    }
}
