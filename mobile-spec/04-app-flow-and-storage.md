# 04. App flow and storage

## Launch flow

At app launch the app must decide between two high-level states:

1. security gate
2. registration or registered-home flow

Recommended sequence:

1. check whether the app has an existing registration state
2. prompt for biometric or allowed device credential
3. if the device is not yet registered, show registration
4. if the device is already registered, show home

## Security gate

Use Android `BiometricPrompt`.

Accepted platform interpretation for this version:

- primary authenticator: `BIOMETRIC_STRONG`
- fallback: `DEVICE_CREDENTIAL`

Because of Android platform behavior, fallback means the system credential configured on the device, not a guaranteed pattern-only flow.

## First launch flow

If no usable app registration state exists:

1. show `RegistrationScreen`
2. collect `email`
3. collect `phone`
4. collect namespace name
5. collect API URL
6. generate or confirm the device keypair
7. generate `deviceId`
8. generate `namespaceId`
9. derive `publicKey`
10. generate DEK and `encDek`
11. assemble payload
12. sign payload for future backend compatibility and optional local diagnostics without persisting the signature
13. submit payload to `POST /api/v1/account`
14. persist success state on success

## Registered launch flow

If a valid registration state already exists:

1. pass the security gate
2. skip registration
3. show `HomeScreen`
4. render `Device Registered`
5. render the stored `deviceId`

## Persisted state

Persist with `Encrypted DataStore` or an equivalently secure Android storage layer.

Persisted fields:

- `accountId`
- `deviceId`
- namespace name
- namespace URL
- namespace ID
- `email`
- `phone`
- `encDek` if the app requires later reuse

Must never be persisted in exportable form:

- private key

## Keystore alias

Use the fixed alias below for v1:

- `secure_device_registration_primary`

Reason:

- the app needs a deterministic alias before `namespaceId` exists
- the current app scope handles a single primary registration state
- this keeps key existence checks simple at startup

## Error handling surface

The app must handle at least:

- no internet
- timeout
- HTTP 400
- HTTP 500
- keystore failure
- StrongBox unavailable with fallback
- biometric failure
- device credential failure
- biometric not enrolled

UX policy for v1:

- show a clear blocking error state
- offer manual retry
- do not implement automatic background retry for registration

## UI screens

Required screens:

- `SecurityScreen`
- `RegistrationScreen`
- `LoadingScreen`
- `HomeScreen`

Required `HomeScreen` text:

```text
Device Registered

Device ID:
XXXXXXXXXX
```