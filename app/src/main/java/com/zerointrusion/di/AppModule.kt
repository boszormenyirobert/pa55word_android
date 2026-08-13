package com.zerointrusion.di

import android.util.Log
import android.content.Context
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.zerointrusion.data.firebase.DefaultFirebaseService
import com.zerointrusion.data.firebase.FirebaseService
import com.zerointrusion.data.network.RegistrationApi
import com.zerointrusion.data.notification.NotificationRepositoryImpl
import com.zerointrusion.data.repository.DefaultRegistrationRepository
import com.zerointrusion.data.security.AndroidKeyStoreService
import com.zerointrusion.data.security.DekService
import com.zerointrusion.data.security.DefaultDekService
import com.zerointrusion.data.security.DefaultDeviceService
import com.zerointrusion.data.security.DefaultNamespaceService
import com.zerointrusion.data.security.DefaultSignatureService
import com.zerointrusion.data.security.DeviceService
import com.zerointrusion.data.security.KeyStoreService
import com.zerointrusion.data.security.NamespaceService
import com.zerointrusion.data.security.SignatureService
import com.zerointrusion.data.storage.EncryptedNamespaceStore
import com.zerointrusion.data.storage.NamespaceStore
import com.zerointrusion.data.storage.EncryptedRegistrationStateStore
import com.zerointrusion.data.storage.RegistrationStateStore
import com.zerointrusion.domain.NotificationRepository
import com.zerointrusion.domain.RegistrationRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    private const val NETWORK_LOG_TAG = "ZeroIntrusionNetwork"

    @Provides
    @Singleton
    fun provideGson(): Gson = GsonBuilder().disableHtmlEscaping().create()

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val request = chain.request()
                val requestUrl = request.url.toString()
                val requestLabel = if (requestUrl.contains("/api/v1/root-device")) {
                    "DEK --> ${request.method} ${request.url}"
                } else if (requestUrl.contains("/api/v1/account")) {
                    "REG --> ${request.method} ${request.url}"
                } else {
                    "--> ${request.method} ${request.url}"
                }
                Log.d(NETWORK_LOG_TAG, requestLabel)
                val response = chain.proceed(request)
                val responseLabel = if (requestUrl.contains("/api/v1/root-device")) {
                    "DEK <-- ${response.code} ${request.method} ${request.url}"
                } else if (requestUrl.contains("/api/v1/account")) {
                    "REG <-- ${response.code} ${request.method} ${request.url}"
                } else {
                    "<-- ${response.code} ${request.method} ${request.url}"
                }
                Log.d(NETWORK_LOG_TAG, responseLabel)
                response
            }

        builder.addInterceptor(
            HttpLoggingInterceptor { message ->
                Log.d(NETWORK_LOG_TAG, message)
            }.apply {
                level = HttpLoggingInterceptor.Level.BASIC
            },
        )

        return builder.build()
    }

    @Provides
    @Singleton
    fun provideRegistrationApi(
        okHttpClient: OkHttpClient,
        gson: Gson,
    ): RegistrationApi = Retrofit.Builder()
        .baseUrl("https://localhost/")
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create(gson))
        .build()
        .create(RegistrationApi::class.java)

    @Provides
    @Singleton
    fun provideKeyStoreService(
        @ApplicationContext context: Context,
    ): KeyStoreService = AndroidKeyStoreService(context)

    @Provides
    @Singleton
    fun provideDeviceService(): DeviceService = DefaultDeviceService()

    @Provides
    @Singleton
    fun provideNamespaceService(): NamespaceService = DefaultNamespaceService()

    @Provides
    @Singleton
    fun provideDekService(
        keyStoreService: KeyStoreService,
    ): DekService = DefaultDekService(keyStoreService)

    @Provides
    @Singleton
    fun provideSignatureService(
        gson: Gson,
        keyStoreService: KeyStoreService,
    ): SignatureService = DefaultSignatureService(gson, keyStoreService)

    @Provides
    @Singleton
    fun provideRegistrationStateStore(
        @ApplicationContext context: Context,
        gson: Gson,
    ): RegistrationStateStore = EncryptedRegistrationStateStore(context, gson)

    @Provides
    @Singleton
    fun provideNamespaceStore(
        @ApplicationContext context: Context,
        gson: Gson,
    ): NamespaceStore = EncryptedNamespaceStore(context, gson)

    @Provides
    @Singleton
    fun provideFirebaseService(
        @ApplicationContext context: Context,
        registrationApi: RegistrationApi,
        registrationStateStore: RegistrationStateStore,
        signatureService: SignatureService,
        gson: Gson,
    ): FirebaseService = DefaultFirebaseService(context, registrationApi, registrationStateStore, signatureService, gson)

    @Provides
    @Singleton
    fun provideNotificationRepository(
        repository: NotificationRepositoryImpl,
    ): NotificationRepository = repository

    @Provides
    @Singleton
    fun provideRegistrationRepository(
        registrationApi: RegistrationApi,
        firebaseService: FirebaseService,
        keyStoreService: KeyStoreService,
        deviceService: DeviceService,
        namespaceService: NamespaceService,
        dekService: DekService,
        signatureService: SignatureService,
        namespaceStore: NamespaceStore,
        registrationStateStore: RegistrationStateStore,
        validator: com.zerointrusion.domain.RegistrationValidator,
        gson: Gson,
    ): RegistrationRepository = DefaultRegistrationRepository(
        registrationApi = registrationApi,
        firebaseService = firebaseService,
        keyStoreService = keyStoreService,
        deviceService = deviceService,
        namespaceService = namespaceService,
        dekService = dekService,
        signatureService = signatureService,
        namespaceStore = namespaceStore,
        stateStore = registrationStateStore,
        validator = validator,
        gson = gson,
    )
}