package com.zerointrusion.data.network

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.HTTP
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Url

data class MobileAuthenticationStatusDto(
    val authorized: Boolean? = null,
    val status: String? = null,
    val notificationId: String? = null,
)

interface AuthenticationApi {
    @POST
    suspend fun sendAuthenticationResponse(
        @Url url: String,
        @Header("x-signature") signature: String,
        @Body request: AuthenticationResponseRequestDto,
    ): Response<AuthenticationResponseDto>

    @GET
    suspend fun fetchAuthenticationSessions(
        @Url url: String,
        @Header("x-signature") signature: String,
    ): Response<List<AuthenticationSessionDto>>

    @GET
    suspend fun fetchMobileAuthenticationStatus(
        @Url url: String,
        @Header("x-signature") signature: String,
    ): Response<MobileAuthenticationStatusDto>

    @HTTP(method = "DELETE", hasBody = true)
    suspend fun revokeAuthenticationSession(
        @Url url: String,
        @Header("x-signature") signature: String,
        @Body request: AuthenticationSessionRevocationResponseRequestDto,
    ): Response<AuthenticationSessionRevocationResponseDto>
}