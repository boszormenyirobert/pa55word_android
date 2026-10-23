package com.zerointrusion.data.network

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Url

interface FcmApi {
    @POST
    suspend fun registerFcmToken(
        @Url url: String,
        @Header("x-signature") signature: String,
        @Body request: FcmRegistrationRequestDto,
    ): Response<Unit>
}