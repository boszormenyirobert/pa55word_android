package com.zerointrusion.data.firebase

interface FirebaseService {

    suspend fun getFcmToken(): String

    suspend fun registerFcmToken(
        namespaceUrl: String,
        accountId: String,
        mobileDeviceId: String,
        fcmToken: String,
    )
}