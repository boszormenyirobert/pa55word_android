package com.zerointrusion.data.firebase

interface FirebaseService {

    suspend fun getFcmToken(): String

    suspend fun registerFcmToken(
        accountId: String,
        deviceId: String,
        fcmToken: String,
    )
}