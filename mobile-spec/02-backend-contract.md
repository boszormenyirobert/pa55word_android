# 02. Backend contract

## Active registration endpoint

Current backend route:

- `POST /api/v1/account`

This route is defined by the backend server registration in [hub-be/server.ts](../hub-be/server.ts).

## Request body for v1 registration

The current backend accepts a plain JSON payload shaped like this:

```json
{
  "namespace": {
    "namespaceId": "7f423b441b0c0d100ef963a097c697a946e51195adf49a1201fd8f30b30b22b2",
    "name": "example-namespace",
    "url": "https://api.example.com"
  },
  "device": {
    "deviceId": "A82kL91zQ0",
    "publicKey": "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQE..."
  },
  "dek": {
    "deviceId": "A82kL91zQ0",
    "encDek": "BASE64_RSA_OAEP_ENCRYPTED_DEK"
  },
  "email": "user@example.com",
  "phone": "+36123456789"
}
```

## Important compatibility note

The current backend does not accept the wrapper form below as the main account registration contract:

```json
{
  "payload": {},
  "signature": "..."
}
```

For compatibility with the existing backend, the Android app must submit the plain account payload above.

For v1 the Android app must not send a wrapper object to `POST /api/v1/account`.

## Response body on success

```json
{
  "success": true,
  "accountId": "3def50d7-625f-4fb4-9b8a-7c754607a797"
}
```

## Error response body

```json
{
  "error": 40001,
  "message": "Database write failed",
  "internalCode": "e_40001_0"
}
```

## Field rules aligned to backend expectations

### namespace

```json
{
  "namespaceId": "",
  "name": "",
  "url": ""
}
```

- `namespaceId`: 64 lowercase hex characters
- `name`: user-provided namespace name
- `url`: user-provided API base URL

### device

```json
{
  "publicKey": "",
  "deviceId": ""
}
```

- `deviceId`: exactly 10 alphanumeric characters using `A-Z`, `a-z`, `0-9`
- `publicKey`: Base64 encoded X.509 SubjectPublicKeyInfo DER bytes, single-line, no PEM header/footer

### dek

```json
{
  "encDek": "",
  "deviceId": ""
}
```

- `encDek`: Base64 encoded RSA OAEP ciphertext of the generated DEK
- `deviceId`: must equal `device.deviceId`

## Client-side validation required before request

- all required fields non-empty
- `device.deviceId` length is exactly 10
- `device.deviceId` charset is alphanumeric only
- `namespace.namespaceId` length is exactly 64
- `namespace.namespaceId` charset is lowercase hex only
- `dek.deviceId` equals `device.deviceId`
- `publicKey` is Base64 and contains no PEM wrapper text

## Signature status in v1

The Android app should still produce a deterministic signature string for future compatibility, but the backend contract for `POST /api/v1/account` does not currently require signature verification.

For v1 the generated signature is local-only and is not part of the request body contract.