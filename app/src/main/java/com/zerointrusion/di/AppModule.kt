package com.zerointrusion.di

import android.util.Log
import android.content.Context
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.zerointrusion.data.firebase.DefaultFirebaseService
import com.zerointrusion.data.firebase.FirebaseService
import com.zerointrusion.data.network.RegistrationApi
import com.zerointrusion.data.notification.NotificationRepositoryImpl
import com.zerointrusion.data.repository.authentication.AuthenticationRepositoryImpl
import com.zerointrusion.data.repository.connecteddevices.AccountDetailsStateHolder
import com.zerointrusion.data.repository.connecteddevices.AccountDetailsSnapshotFetcher
import com.zerointrusion.data.repository.connecteddevices.ConnectedDevicesRepositoryImpl
import com.zerointrusion.data.repository.connecteddevices.InMemoryAccountDetailsStateHolder
import com.zerointrusion.data.repository.namespaces.NamespacesRepositoryImpl
import com.zerointrusion.data.repository.qr.QrNamespaceRegistrationRepositoryImpl
import com.zerointrusion.data.repository.registration.RegistrationRepositoryImpl
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
import com.zerointrusion.data.storage.ConnectedDevicesLocalStore
import com.zerointrusion.data.storage.EncryptedConnectedDevicesLocalStore
import com.zerointrusion.data.storage.EncryptedRegistrationStateStore
import com.zerointrusion.data.storage.RegistrationStateStore
import com.zerointrusion.domain.notification.NotificationRepository
import com.zerointrusion.domain.authentication.AuthenticationRepository
import com.zerointrusion.domain.connecteddevices.ConnectedDevicesRepository
import com.zerointrusion.domain.namespaces.NamespacesRepository
import com.zerointrusion.domain.qr.QrNamespaceRegistrationRepository
import com.zerointrusion.domain.registration.RegistrationRepository
import com.zerointrusion.domain.registration.RegistrationValidator
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
    fun provideConnectedDevicesLocalStore(
        @ApplicationContext context: Context,
        gson: Gson,
    ): ConnectedDevicesLocalStore = EncryptedConnectedDevicesLocalStore(context, gson)

    @Provides
    @Singleton
    fun provideAccountDetailsStateHolder(
        stateHolder: InMemoryAccountDetailsStateHolder,
    ): AccountDetailsStateHolder = stateHolder

    @Provides
    @Singleton
    fun provideAccountDetailsSnapshotFetcher(
        registrationApi: RegistrationApi,
        registrationStateStore: RegistrationStateStore,
        accountDetailsStateHolder: AccountDetailsStateHolder,
        connectedDevicesLocalStore: ConnectedDevicesLocalStore,
        dekService: DekService,
        signatureService: SignatureService,
        gson: Gson,
    ): AccountDetailsSnapshotFetcher = AccountDetailsSnapshotFetcher(
        registrationApi = registrationApi,
        stateStore = registrationStateStore,
        accountDetailsStateHolder = accountDetailsStateHolder,
        connectedDevicesLocalStore = connectedDevicesLocalStore,
        dekService = dekService,
        signatureService = signatureService,
        gson = gson,
    )

    @Provides
    @Singleton
    fun provideFirebaseService(
        @ApplicationContext context: Context,
        registrationApi: RegistrationApi,
        signatureService: SignatureService,
        gson: Gson,
    ): FirebaseService = DefaultFirebaseService(context, registrationApi, signatureService, gson)

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
        registrationStateStore: RegistrationStateStore,
        validator: RegistrationValidator,
        gson: Gson,
        okHttpClient: OkHttpClient,
    ): RegistrationRepository = RegistrationRepositoryImpl(
        registrationApi = registrationApi,
        firebaseService = firebaseService,
        keyStoreService = keyStoreService,
        deviceService = deviceService,
        namespaceService = namespaceService,
        dekService = dekService,
        signatureService = signatureService,
        stateStore = registrationStateStore,
        validator = validator,
        gson = gson,
        okHttpClient = okHttpClient,
    )

    @Provides
    @Singleton
    fun provideNamespacesRepository(
        connectedDevicesLocalStore: ConnectedDevicesLocalStore,
        registrationStateStore: RegistrationStateStore,
        registrationApi: RegistrationApi,
        signatureService: SignatureService,
        gson: Gson,
    ): NamespacesRepository = NamespacesRepositoryImpl(
        connectedDevicesLocalStore = connectedDevicesLocalStore,
        stateStore = registrationStateStore,
        registrationApi = registrationApi,
        signatureService = signatureService,
        gson = gson,
    )

    @Provides
    @Singleton
    fun provideConnectedDevicesRepository(
        accountDetailsSnapshotFetcher: AccountDetailsSnapshotFetcher,
        accountDetailsStateHolder: AccountDetailsStateHolder,
        connectedDevicesLocalStore: ConnectedDevicesLocalStore,
    ): ConnectedDevicesRepository = ConnectedDevicesRepositoryImpl(
        accountDetailsSnapshotFetcher = accountDetailsSnapshotFetcher,
        accountDetailsStateHolder = accountDetailsStateHolder,
        connectedDevicesLocalStore = connectedDevicesLocalStore,
    )

    @Provides
    @Singleton
    fun provideQrNamespaceRegistrationRepository(
        registrationApi: RegistrationApi,
        keyStoreService: KeyStoreService,
        registrationStateStore: RegistrationStateStore,
        dekService: DekService,
        signatureService: SignatureService,
        accountDetailsStateHolder: AccountDetailsStateHolder,
        connectedDevicesRepository: ConnectedDevicesRepository,
        connectedDevicesLocalStore: ConnectedDevicesLocalStore,
        validator: RegistrationValidator,
        firebaseService: FirebaseService,
        gson: Gson,
    ): QrNamespaceRegistrationRepository = QrNamespaceRegistrationRepositoryImpl(
        registrationApi = registrationApi,
        keyStoreService = keyStoreService,
        stateStore = registrationStateStore,
        dekService = dekService,
        signatureService = signatureService,
        accountDetailsStateHolder = accountDetailsStateHolder,
        connectedDevicesRepository = connectedDevicesRepository,
        connectedDevicesLocalStore = connectedDevicesLocalStore,
        validator = validator,
        firebaseService = firebaseService,
        gson = gson,
    )

    @Provides
    @Singleton
    fun provideAuthenticationRepository(
        registrationApi: RegistrationApi,
        accountDetailsStateHolder: AccountDetailsStateHolder,
        registrationStateStore: RegistrationStateStore,
        signatureService: SignatureService,
        gson: Gson,
        connectedDevicesRepository: ConnectedDevicesRepository,
        connectedDevicesLocalStore: ConnectedDevicesLocalStore,
    ): AuthenticationRepository = AuthenticationRepositoryImpl(
        registrationApi = registrationApi,
        accountDetailsStateHolder = accountDetailsStateHolder,
        stateStore = registrationStateStore,
        signatureService = signatureService,
        gson = gson,
        connectedDevicesRepository = connectedDevicesRepository,
        connectedDevicesLocalStore = connectedDevicesLocalStore,
    )
}