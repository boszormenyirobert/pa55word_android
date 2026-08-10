# 01. Scope and decisions

## Goal

Build a new Android application in Kotlin that performs secure device registration against the current backend in this workspace.

The application must:

- register a user with `email`, `phone`, and namespace data
- generate and retain an RSA private key in Android Keystore only
- expose a Base64 encoded X.509 DER public key in the payload
- generate an AES-256 DEK and send its RSA-encrypted form as `encDek`
- sign the outgoing registration payload with `SHA256withRSA`
- persist the returned `accountId` securely after successful registration

## Fixed technical stack

- Kotlin
- Android SDK 28+
- Jetpack Compose
- Navigation Compose
- MVVM
- Clean Architecture
- Repository pattern
- Hilt
- Coroutines
- Flow

## Backend-aligned decisions

The mobile app must target the backend that already exists in this workspace.

For v1 the registration endpoint is:

- `POST /api/v1/account`

For v1 the backend does not validate request signatures yet.

That means:

- the Android client still generates the signature
- the client does not wrap the registration request in a `payload` and `signature` envelope for `POST /api/v1/account`
- the generated signature remains a local client-side artifact reserved for future backend support
- signature verification is out of scope for backend compatibility in this version

## Non-goals for this version

- backend signature verification
- extension registration flow
- root-device follow-up registration flow
- access-registry sync flow
- push notification token registration

## Android platform constraints accepted in this spec

- The app may require `BIOMETRIC_STRONG` plus `DEVICE_CREDENTIAL` fallback.
- Android cannot reliably enforce pattern-only fallback. If device credential fallback is allowed, the system may use pattern, PIN, or password.
- Face authentication cannot be filtered with perfect device-independent certainty beyond the authenticator classes exposed by Android.

## Final implementation decisions

- The keystore alias is fixed to `secure_device_registration_primary` for v1.
- `decryptDek()` is not part of the required v1 registration flow. If the service interface keeps it for future use, it should be clearly marked as unused in v1.
- Timeout and offline recovery UX is limited to explicit user-visible error state plus manual retry initiated from the UI.