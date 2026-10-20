package com.zerointrusion.data.network

import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Url

interface RecoveryApi {
    @POST
    suspend fun post(
        @Url url: String,
        @Header("x-signature") signature: String,
        @Body body: RequestBody,
    ): Response<ResponseBody>
}