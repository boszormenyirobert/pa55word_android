# Implementation Plan - Secure Device Registration

Build an Android application that performs secure device registration against a backend. The app uses Android Keystore for RSA key management, AES for payload encryption, and follows a Clean Architecture with MVVM.

## User Review Required

> [!IMPORTANT]
> The backend URL is currently assumed to be `https://localhost:8080/` (or similar) as no specific base URL was provided in the prompt. Please confirm the actual backend URL if different.
>
> Signature verification on the backend is disabled for v1, but the client will still generate and send the signature as requested.

## Proposed Changes

### 1. Project Infrastructure

#### [MODIFY] [libs.versions.toml](file:///C:/Users/boszo/Desktop/ZeroAndroid/gradle/libs.versions.toml)
Add dependencies for Hilt, Retrofit, OkHttp, Navigation Compose, and Security-Crypto.

#### [MODIFY] [build.gradle.kts](file:///C:/Users/boszo/Desktop/ZeroAndroid/app/build.gradle.kts)
Apply Hilt plugin and add new dependencies.

#### [NEW] [ZeroApplication.kt](file:///C:/Users/boszo/Desktop/ZeroAndroid/app/src/main/java/com/zille/zerointrusion/ZeroApplication.kt)
Initialize Hilt.

---

### 2. Security Layer

#### [NEW] [KeyStoreManager.kt](file:///C:/Users/boszo/Desktop/ZeroAndroid/app/src/main/java/com/zille/zerointrusion/security/KeyStoreManager.kt)
Manage RSA key pair in Android Keystore with alias `secure_device_registration_primary`. Provides the public key in Base64 encoded X.509 DER format.

#### [NEW] [EncryptionManager.kt](file:///C:/Users/boszo/Desktop/ZeroAndroid/app/src/main/java/com/zille/zerointrusion/security/EncryptionManager.kt)
Generate AES-256 DEK, encrypt it with the RSA public key, and sign payloads with SHA256withRSA.

#### [NEW] [SecureStorage.kt](file:///C:/Users/boszo/Desktop/ZeroAndroid/app/src/main/java/com/zille/zerointrusion/security/SecureStorage.kt)
Persist `accountId` securely using `EncryptedSharedPreferences`.

---

### 3. Data Layer

#### [NEW] [AccountApiService.kt](file:///C:/Users/boszo/Desktop/ZeroAndroid/app/src/main/java/com/zille/zerointrusion/data/remote/AccountApiService.kt)
Retrofit interface for `POST /api/v1/account`.

#### [NEW] [Models.kt](file:///C:/Users/boszo/Desktop/ZeroAndroid/app/src/main/java/com/zille/zerointrusion/data/model/RegistrationModels.kt)
Data classes for registration request/response.

#### [NEW] [RegistrationRepositoryImpl.kt](file:///C:/Users/boszo/Desktop/ZeroAndroid/app/src/main/java/com/zille/zerointrusion/data/repository/RegistrationRepositoryImpl.kt)
Implements the registration logic, coordinating security and network layers.

---

### 4. Domain Layer

#### [NEW] [RegisterDeviceUseCase.kt](file:///C:/Users/boszo/Desktop/ZeroAndroid/app/src/main/java/com/zille/zerointrusion/domain/usecase/RegisterDeviceUseCase.kt)
The use case that orchestrates the registration flow.

---

### 5. UI Layer (MVVM + Compose)

#### [NEW] [RegistrationViewModel.kt](file:///C:/Users/boszo/Desktop/ZeroAndroid/app/src/main/java/com/zille/zerointrusion/ui/registration/RegistrationViewModel.kt)
Handles registration UI state and logic.

#### [NEW] [RegistrationScreen.kt](file:///C:/Users/boszo/Desktop/ZeroAndroid/app/src/main/java/com/zille/zerointrusion/ui/registration/RegistrationScreen.kt)
Compose UI for the registration form.

#### [MODIFY] [MainActivity.kt](file:///C:/Users/boszo/Desktop/ZeroAndroid/app/src/main/java/com/zille/zerointrusion/MainActivity.kt)
Set up Hilt and Navigation.

## Verification Plan

### Automated Tests
- Unit tests for `KeyStoreManager` and `EncryptionManager` to verify key generation and encryption/signing logic.
- MockWebServer tests for `AccountApiService`.

### Manual Verification
- Deploy to an Android device/emulator (API 28+).
- Fill in the registration form and verify the process completes (or shows appropriate error if backend is unreachable).
- Inspect logs to verify the payload structure (Base64 public key, encDek, etc.).
