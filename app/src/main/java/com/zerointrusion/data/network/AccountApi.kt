package com.zerointrusion.data.network

import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.HTTP
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Url

interface AccountApi {
    @GET
    suspend fun getAccountDetails(
        @Url url: String,
        @Header("x-signature") signature: String,
    ): Response<ResponseBody>

    @POST
    suspend fun getAccountStatus(
        @Url url: String,
        @Header("x-signature") signature: String,
        @Body request: AccountLifecycleRequestDto,
    ): Response<ResponseBody>

    @HTTP(method = "DELETE", hasBody = true)
    suspend fun deleteAccount(
        @Url url: String,
        @Header("x-signature") signature: String,
        @Body request: AccountLifecycleRequestDto,
    ): Response<ResponseBody>

    @GET
    suspend fun loadAccountDevices(
        @Url url: String,
        @Header("x-signature") signature: String,
    ): Response<ResponseBody>

    @HTTP(method = "DELETE", hasBody = true)
    suspend fun deleteAccountDevice(
        @Url url: String,
        @Header("x-signature") signature: String,
        @Body request: DeleteAccountDeviceRequestDto,
    ): Response<ResponseBody>
}