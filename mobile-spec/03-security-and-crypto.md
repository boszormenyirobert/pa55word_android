# 03. Security and crypto

## Private key policy

The RSA private key must never leave Android Keystore.

Requirements:

- algorithm: RSA
- size: 4096 bit
- generation preference: StrongBox first, TEE-backed Keystore fallback
- private key is non-exportable
- private key is not stored in files
- private key is not stored in SharedPreferences
- private key is not stored in a database

## Public key transport format

The `publicKey` field sent to the backend is not PEM text.

Required format:

1. obtain the RSA public key from the generated key pair
2. export the X.509 SubjectPublicKeyInfo DER bytes
3. Base64 encode the DER bytes
4. send the Base64 string as JSON

Rules:

- no `BEGIN PUBLIC KEY`
- no `END PUBLIC KEY`
- no line breaks
- JSON-safe string only

## DEK policy

The app must generate a 256-bit symmetric DEK using a cryptographically secure RNG.

The current registration payload only transports:

- `encDek`
- `deviceId`

For registration, `encDek` is the RSA OAEP encrypted form of the generated DEK.

Required RSA transformation:

- `RSA/ECB/OAEPWithSHA-256AndMGF1Padding`

Required OAEP parameters:

- digest: `SHA-256`
- MGF: `MGF1` with `SHA-256`

## AES-GCM note

The original monolithic specification also mentions AES-256-GCM ciphertext, IV, and authentication tag for local secure storage scenarios.

For v1 registration scope, those values are not part of the backend registration payload.

Implementation guidance:

- keep AES-GCM support isolated in `DekService`
- do not assume a mandatory local encryption use case unless the app flow explicitly needs it
- `decryptDek()` is optional for v1 and should not block delivery of the registration flow

## Signature generation

Every registration payload should be signable with the device private key.

Algorithm:

- `SHA256withRSA`

Canonical JSON rules for the string-to-sign:

- UTF-8 encoding
- alphabetical field order
- no insignificant whitespace
- no line breaks
- no `null` fields
- no optional empty fields

## Canonicalization constraint

If the backend later implements signature verification, canonicalization must be made identical on both sides.

The Android client should use the following v1 canonicalization rule set for its locally generated signature:

- recursive object key sorting
- standard JSON string escaping
- UTF-8 encoding
- omission of `null` values
- omission of empty optional values

Before backend verification work starts, confirm the same rules on both sides, especially:

- Unicode handling
- number formatting rules

## Sensitive data logging policy

The app must never log:

- private key
- full public key
- raw DEK
- encrypted DEK
- signature
- full registration payload

Allowed log content:

- request ID
- error code
- timestamp