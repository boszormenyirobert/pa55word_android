package com.zerointrusion.data.network

import com.google.gson.JsonElement
import com.google.gson.annotations.SerializedName
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
    val root: Boolean? = null,
)

data class DeviceDto(
    val deviceId: String,
    val publicKey: String,
    val deviceName: String? = null,
    val desktopDeviceId: String? = null,
)

data class DekDto(
    val deviceId: String,
    val encDek: String,
)

data class NewDeviceRegistrationDto(
    val accountId: String,
    val device: NewDeviceRegistrationDeviceDto,
    val dek: NewDeviceRegistrationDekDto,
    val nonce: String,
)

data class NewDeviceRegistrationDeviceDto(
    val mobileDeviceId: String,
    val publicKey: String,
    val deviceName: String? = null,
    val desktopDeviceId: String,
)

data class NewDeviceRegistrationDekDto(
    val desktopDeviceId: String,
    val encDek: String,
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
    val device: AccountRegistrationDeviceDto,
    val dek: AccountRegistrationDekDto,
    val email: String,
    val phone: String,
    val nonce: String,
)

data class AccountRegistrationDeviceDto(
    @SerializedName("mobileDeviceId")
    val mobileDeviceId: String,
    val publicKey: String,
    val deviceName: String? = null,
)

data class AccountRegistrationDekDto(
    @SerializedName("mobileDeviceId")
    val mobileDeviceId: String,
    val encDek: String,
)

data class AccountRegistrationResponseDto(
    val success: Boolean,
    val accountId: String?,
)

data class AccountErrorResponseDto(
    val error: Int?,
    val message: String?,
    val internalCode: String?,
)

data class FcmRegistrationRequestDto(
    val accountId: String,
    val mobileDeviceId: String,
    val fcmToken: String,
    val nonce: String,
)

data class AuthenticationResponseRequestDto(
    val notificationId: String,
    val accountId: String,
    val mobileDeviceId: String,
    val approved: Boolean,
    val nonce: String,
    val envelop: AuthenticationAttestationEnvelopeDto,
)

data class AuthenticationAttestationEnvelopeDto(
    val desktopDeviceId: String,
    val mobileDeviceId: String,
    val namespaces: List<String>,
    val iat: Long,
    val exp: Long,
    val nonce: String,
)

data class AuthenticationAttestationDto(
    val attestationId: String? = null,
)

data class AuthenticationResponseDto(
    val authorized: Boolean? = null,
    val status: String? = null,
    val notificationId: String? = null,
    val attestationId: JsonElement? = null,
    val attestation: AuthenticationAttestationDto? = null,
)

data class AuthenticationSessionDto(
    val attestationId: String,
    val desktopDeviceId: String? = null,
    val issuedAt: String,
    val expiresAt: String,
)

data class AuthenticationSessionRevocationResponseDto(
    val success: Boolean,
    val attestationId: String?,
)

data class AuthenticationSessionRevocationResponseRequestDto(
    val accountId: String,
    val mobileDeviceId: String,
    val desktopDeviceId: String,
    val attestationId: String,
    val nonce: String,
)

interface RegistrationApi {
    @POST
    suspend fun registerAccount(
        @Url url: String,
        @Body request: AccountRegistrationRequestDto,
    ): Response<ResponseBody>

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
    ): Response<AuthenticationResponseDto>

    @POST
    suspend fun sendAuthenticationDirectResponse(
        @Url url: String,
        @Header("x-signature") signature: String,
        @Body request: AuthenticationResponseRequestDto,
    ): Response<AuthenticationResponseDto>

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