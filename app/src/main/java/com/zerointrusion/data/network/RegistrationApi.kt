package com.zerointrusion.data.network

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.HTTP
import retrofit2.http.POST
import retrofit2.http.Url
import okhttp3.ResponseBody

data class NamespaceDto(
    val namespaceId: String,
    val name: String,
    val url: String,
)

data class DeviceDto(
    val deviceId: String,
    val publicKey: String,
    val deviceName: String? = null,
    val deviceIdAdditional: String? = null,
)

data class DekDto(
    val deviceId: String,
    val encDek: String,
)

data class NewDeviceRegistrationDto(
    val accountId: String,
    val device: DeviceDto,
    val dek: DekDto,
    val nonce: String,
)

data class RootDeviceDekResponseDto(
    val dek: RootDeviceDekDto?,
)

data class RootDeviceDekDto(
    val id: Long?,
    val accountId: String?,
    val deviceId: String?,
    val encDek: String?,
)

data class AccountRegistrationRequestDto(
    val namespace: NamespaceDto,
    val device: DeviceDto,
    val dek: DekDto,
    val email: String,
    val phone: String,
    val nonce: String,
)

data class AccountRegistrationResponseDto(
    val success: Boolean,
    val accountId: String?,
)

data class NamespaceRegistrationRequestDto(
    val accountId: String,
    val deviceId: String,
    val namespaceId: String,
    val name: String,
    val url: String,
    val nonce: String,
)

data class AccountErrorResponseDto(
    val error: Int?,
    val message: String?,
    val internalCode: String?,
)

data class FcmRegistrationRequestDto(
    val accountId: String,
    val deviceId: String,
    val fcmToken: String,
    val nonce: String,
)

data class AuthenticationResponseRequestDto(
    val notificationId: String,
    val accountId: String,
    val deviceId: String,
    val approved: Boolean,
    val nonce: String,
    val envelop: AuthenticationAttestationEnvelopeDto,
)

data class AuthenticationAttestationEnvelopeDto(
    val desktopId: String,
    val deviceId: String,
    val namespaces: List<String>,
    val iat: Long,
    val exp: Long,
    val nonce: String,
)

data class AuthenticationSessionDto(
    val attestationId: String,
    val deviceId: String,
    val issuedAt: String,
    val expiresAt: String,
)

data class AuthenticationSessionRevocationResponseDto(
    val success: Boolean,
    val attestationId: String?,
)

data class AuthenticationSessionRevocationResponseRequestDto(
    val accountId: String,
    val deviceId: String,
    val desktopId: String,
    val attestationId: String,
    val nonce: String,
)

interface RegistrationApi {
    @POST
    suspend fun registerAccount(
        @Url url: String,
        @Body request: AccountRegistrationRequestDto,
    ): Response<AccountRegistrationResponseDto>

    @POST
    suspend fun registerNamespace(
        @Url url: String,
        @Header("x-signature") signature: String,
        @Body request: NamespaceRegistrationRequestDto,
    ): Response<Unit>

    @POST
    suspend fun registerFcmToken(
        @Url url: String,
        @Header("x-signature") signature: String,
        @Body request: FcmRegistrationRequestDto,
    ): Response<Unit>

    @GET
    suspend fun fetchRootDeviceDek(
        @Url url: String,
        @Header("x-signature") signature: String,
    ): Response<RootDeviceDekResponseDto>

    @POST
    suspend fun registerNewDevice(
        @Url url: String,
        @Header("x-signature") signature: String,
        @Body request: NewDeviceRegistrationDto,
    ): Response<Unit>

    @POST
    suspend fun sendAuthenticationResponse(
        @Url url: String,
        @Header("x-signature") signature: String,
        @Body request: AuthenticationResponseRequestDto,
    ): Response<Unit>

    @POST
    suspend fun sendAuthenticationDirectResponse(
        @Url url: String,
        @Header("x-signature") signature: String,
        @Body request: AuthenticationResponseRequestDto,
    ): Response<Unit>

    @GET
    suspend fun fetchAuthenticationSessions(
        @Url url: String,
        @Header("x-signature") signature: String,
    ): Response<List<AuthenticationSessionDto>>

    @HTTP(method = "DELETE", hasBody = true)
    suspend fun revokeAuthenticationSession(
        @Url url: String,
        @Header("x-signature") signature: String,
        @Body request: AuthenticationSessionRevocationResponseRequestDto,
    ): Response<AuthenticationSessionRevocationResponseDto>

    @GET
    suspend fun getAccountDetails(
        @Url url: String,
        @Header("x-signature") signature: String,
    ): Response<ResponseBody>
}