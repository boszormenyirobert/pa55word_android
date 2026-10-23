# API Catalog

Purpose: single inventory of every backend HTTP API currently defined by the app, including request payload, response payload, signature usage, and whether the request is signed.

Signature header:
- Signed requests send `x-signature`.
- Unsigned requests do not send `x-signature`.

Unsigned requests:
- `POST /api/v1/account`

Signed requests:
- `POST /api/v1/mobile-device`
- `GET /api/v1/mobile-device/{mobileDeviceId}/registration?nonce={nonce}`
- `POST /api/v1/fcm`
- `GET /api/v1/root-device?nonce={nonce}`
- `POST /api/v1/root-device`
- `POST /api/v1/authentication/response`
- `POST /api/v1/authentication/response/direct`
- `GET /api/v1/authentication/{accountId}?deviceId={deviceId}&nonce={nonce}`
- `DELETE /api/v1/authentication/{accountId}/{desktopDeviceId}/{attestationId}?nonce={nonce}`
- `GET /api/v1/account/{accountId}/{mobileDeviceId}?nonce={nonce}`

Recovery signed requests:
- `POST /api/v1/recovery/candidate`
- `POST /api/v1/recovery/candidate/status`
- `POST /api/v1/recovery/candidate/dek`
- `POST /api/v1/recovery/completion`
- `POST /api/v1/recovery/candidate/cancellation`

Recovery request rules:
- Every request body includes the exact `operation` value and a fresh UUID
  `nonce`.
- All modifying requests (`candidate`, `completion`,
  `candidate_cancellation`) also include a UUID `requestId`.
- The `x-signature` value is standard Base64 `SHA1withRSA` over the exact
  UTF-8 JSON bytes sent as the body.
- Retries reuse the same `requestId` and identical semantic payload, but use a
  fresh `nonce` and signature.

## Recovery endpoints

These endpoints are used by both first-launch recovery and authenticated
current-namespace recovery described in `14-first-launch-recovery.md`.
First-launch recovery signs with a new candidate key; authenticated recovery
reuses the phone's existing Android Keystore key. The private key is never
exported.

### POST /api/v1/recovery/candidate

Creates a recovery candidate after the user has scanned and approved a recovery
QR code. A successful response is the point at which the app may show the
pairing code to the user.

```json
{
  "operation": "candidate",
  "nonce": "uuid",
  "requestId": "uuid",
  "recoveryId": "uuid",
  "candidateId": "uuid",
  "mobileDeviceId": "device-id",
  "publicKey": "base64-spki-der",
  "deviceName": "Android device",
  "pairingCodeHash": "lowercase-sha256-hex"
}
```

Successful response (`201`):

```json
{
  "recoveryId": "uuid",
  "candidateId": "uuid",
  "status": "waiting_for_selection",
  "expiresAt": "UTC ISO-8601"
}
```

### POST /api/v1/recovery/candidate/status

Polls only the current candidate. This request has no `requestId`.

```json
{
  "operation": "candidate_status",
  "nonce": "uuid",
  "recoveryId": "uuid",
  "candidateId": "uuid",
  "mobileDeviceId": "device-id"
}
```

The client accepts `waiting_for_selection`, `selected`, `dek_ready`, and
`completed` only as documented state transitions. A terminal response is not a
success indication.

### POST /api/v1/recovery/candidate/dek

Retrieves the selected candidate's encrypted DEK and the account data required
to create the final local registration. This request has no `requestId`.

```json
{
  "operation": "candidate_dek",
  "nonce": "uuid",
  "recoveryId": "uuid",
  "candidateId": "uuid",
  "mobileDeviceId": "device-id"
}
```

Successful response (`200`):

```json
{
  "recoveryId": "uuid",
  "candidateId": "uuid",
  "accountId": "account-id",
  "mobileDeviceId": "device-id",
  "encDek": "base64",
  "email": "user@example.com",
  "phone": "+36123456789",
  "namespaces": [
    {
      "namespaceId": "namespace-id",
      "name": "Primary",
      "url": "https://server.example"
    }
  ]
}
```

The response must match the stored recovery, candidate, and mobile device IDs.
The namespace whose normalized `url` equals the QR-approved `apiBaseUrl` is the
root namespace for recovery activation.
The client decrypts `encDek` with `RSA/ECB/OAEPWithSHA-1AndMGF1Padding` and
requires exactly 32 plaintext bytes before persisting pending activation data.

### POST /api/v1/recovery/completion

Completes recovery after the decrypted DEK and full response have been saved to
the protected pending-recovery store.

```json
{
  "operation": "completion",
  "nonce": "uuid",
  "requestId": "uuid",
  "recoveryId": "uuid",
  "candidateId": "uuid",
  "mobileDeviceId": "device-id"
}
```

Successful response (`200`) contains `recoveryId`, `status=completed`,
`accountId`, and `mobileDeviceId`. A lost response is retried explicitly with
the stored completion request ID.

### POST /api/v1/recovery/candidate/cancellation

Cancels the current not-yet-active candidate. If it is selected, the backend
cancels the entire recovery.

```json
{
  "operation": "candidate_cancellation",
  "nonce": "uuid",
  "requestId": "uuid",
  "recoveryId": "uuid",
  "candidateId": "uuid",
  "mobileDeviceId": "device-id"
}
```

## POST /api/v1/mobile-device

Used by the registered phone after scanning an `addition_mobile_device` QR code. The registered phone decrypts its stored DEK and re-encrypts it for the new phone public key.

Signed by the registered phone private key using the complete request JSON. The signature is sent in `x-signature`.

```json
{
  "accountId": "existing-account-id",
  "signingMobileDeviceId": "known-mobile-id",
  "device": {
    "mobileDeviceId": "new-mobile-id",
    "publicKey": "new-mobile-public-key",
    "deviceName": "New phone"
  },
  "dek": {
    "mobileDeviceId": "new-mobile-id",
    "encDek": "encrypted-for-new-mobile"
  },
  "nonce": "unique-nonce"
}
```

## GET /api/v1/mobile-device/{mobileDeviceId}/registration?nonce={nonce}

SSE bootstrap stream opened by the new phone and signed with the new phone private key. The signature payload is:

```json
{
  "mobileDeviceId": "new-mobile-id",
  "nonce": "unique-nonce"
}
```

The successful `data:` event supplies `accountId`, `mobileDeviceId`, `dek`, and `namespaces`. The app combines it with the original registration form and generated device data, then stores the normal encrypted registration state.

## POST /api/v1/account

Used by:
- Initial phone registration.
- QR namespace flow phone registration.
- Not used by QR namespace_sync flow.

Signed:
- No.

Request payload shape for initial phone registration:
```json
{
  "namespace": {
    "namespaceId": "string",
    "name": "string",
    "url": "string"
  },
  "device": {
    "deviceId": "string",
    "publicKey": "string",
    "deviceName": "string"
  },
  "dek": {
    "deviceId": "string",
    "encDek": "string"
  },
  "email": "string",
  "phone": "string",
  "root": true,
  "nonce": "string"
}
```

QR namespace flow uses the same payload shape except it does not send the `root` field.

Response payload shape:
```json
{
  "success": true,
  "accountId": "string"
}
```

```

Notes:
- `attestationId` may be `null` when the request is rejected or no attestation was created.
- Some backend variants may return only `HTTP 204` with no body.

## POST /api/v1/fcm

Used by:
- Post-registration FCM token sync.
- Namespace-specific FCM sync after QR desktop linking.

Signed:
- Yes.

Signed header:
- `x-signature: <base64-signature>`

Request payload shape:
```json
{
  "accountId": "string",
  "mobileDeviceId": "string",
  "fcmToken": "string",
  "nonce": "string"
}
```

Signed payload shape:
```json
{
  "accountId": "string",
  "mobileDeviceId": "string",
  "fcmToken": "string",
  "nonce": "string"
}
```

Response payload shape:
```json
{
  "authorized": true,
  "status": "authorized",
  "notificationId": "string",
  "attestation": {
    "attestationId": "string"
  }
}
```

Notes:
- `attestation` is omitted or `null` when the request is rejected.
- Some backend variants may return only `HTTP 204` with no body.

## GET /api/v1/root-device?nonce={nonce}

Used by:
- Retrofit endpoint is defined, but no current call site was found in the app code.

Signed:
- Yes.

Signed header:
- `x-signature: <base64-signature>`

HTTP request body:
- None.

Query parameters:
- `nonce`

Signed payload shape:
```json
{
  "accountId": "string",
  "deviceId": "string",
  "nonce": "string"
}
```

Response payload shape:
```json
{
  "dek": {
    "id": 0,
    "accountId": "string",
    "deviceId": "string",
    "encDek": "string"
  }
}
```

## POST /api/v1/root-device

Used by:
- QR desktop root-device registration submit.

Signed:
- Yes.

Signed header:
- `x-signature: <base64-signature>`

Request payload shape:
```json
{
  "accountId": "string",
  "device": {
    "deviceId": "string",
    "publicKey": "string",
    "deviceName": "string",
    "desktopDeviceId": "string"
  },
  "dek": {
    "desktopDeviceId": "string",
    "encDek": "string"
  },
  "nonce": "string"
}
```

Signed payload shape:
```json
{
  "accountId": "string",
  "device": {
    "deviceId": "string",
    "publicKey": "string",
    "deviceName": "string",
    "desktopDeviceId": "string"
  },
  "dek": {
    "desktopDeviceId": "string",
    "encDek": "string"
  },
  "nonce": "string"
}
```

Response payload shape:
- `HTTP 200/204` with empty body.

## POST /api/v1/authentication/response

Used by:
- Authentication approval or denial for the namespace that matches the incoming notification target.

Signed:
- Yes.

Signed header:
- `x-signature: <base64-signature>`

Request payload shape:
```json
{
  "notificationId": "string",
  "accountId": "string",
  "mobileDeviceId": "string",
  "approved": true,
  "nonce": "string",
  "envelop": {
    "desktopDeviceId": "string",
    "deviceId": "string",
    "namespaces": ["namespaceId"],
    "iat": 0,
    "exp": 0,
    "nonce": "string"
  }
}
```

Signed payload shape:
```json
{
  "notificationId": "string",
  "accountId": "string",
  "mobileDeviceId": "string",
  "approved": true,
  "nonce": "string",
  "envelop": {
    "desktopDeviceId": "string",
    "deviceId": "string",
    "namespaces": ["namespaceId"],
    "iat": 0,
    "exp": 0,
    "nonce": "string"
  }
}
```

Response payload shape:
- `HTTP 200/204` with empty body.

## POST /api/v1/authentication/response/direct

Used by:
- No current mobile call site. Authentication approval or denial is sent only to the namespace that matches the incoming notification target.

Signed:
- Yes.

Signed header:
- `x-signature: <base64-signature>`

Request payload shape:
```json
{
  "notificationId": "string",
  "accountId": "string",
  "mobileDeviceId": "string",
  "approved": true,
  "nonce": "string",
  "envelop": {
    "desktopDeviceId": "string",
    "mobileDeviceId": "string",
    "namespaces": ["namespaceId"],
    "iat": 0,
    "exp": 0,
    "nonce": "string"
  }
}
```

Signed payload shape:
```json
{
  "notificationId": "string",
  "accountId": "string",
  "mobileDeviceId": "string",
  "approved": true,
  "nonce": "string",
  "envelop": {
    "desktopDeviceId": "string",
    "mobileDeviceId": "string",
    "namespaces": ["namespaceId"],
    "iat": 0,
    "exp": 0,
    "nonce": "string"
  }
}
```

Response payload shape:
- `HTTP 200/204` with empty body.

## GET /api/v1/authentication/{accountId}?mobileDeviceId={mobileDeviceId}&nonce={nonce}

Used by:
- Home live desktop-session polling.
- Dedicated desktop sessions route.
- Called once per locally known namespace base URL; the client merges successful responses into a single in-memory live-session list.

Signed:
- Yes.

Signed header:
- `x-signature: <base64-signature>`

HTTP request body:
- None.

Signed payload shape:
```json
{
  "accountId": "string",
  "mobileDeviceId": "string",
  "nonce": "string"
}
```

Response payload shape:
```json
[
  {
    "attestationId": "string",
    "deviceId": "string",
    "issuedAt": "ISO-8601",
    "expiresAt": "ISO-8601"
  }
]
```

Client-side handling notes:
- The response body does not contain `namespaceName`.
- The Android client annotates each returned session with the local namespace name that corresponds to the namespace URL used for that request.
- Responses are merged and deduplicated by `attestationId` before they are exposed to the UI.

## DELETE /api/v1/authentication/{accountId}/{desktopDeviceId}/{attestationId}?nonce={nonce}

Used by:
- Home live desktop-session revoke.
- Dedicated desktop sessions route.

Signed:
- Yes.

Signed header:
- `x-signature: <base64-signature>`

Request payload shape:
```json
{
  "accountId": "string",
  "deviceId": "string",
  "desktopId": "string",
  "attestationId": "string",
  "nonce": "string"
}
```

Signed payload shape:
```json
{
  "accountId": "string",
  "deviceId": "string",
  "desktopId": "string",
  "attestationId": "string",
  "nonce": "string"
}
```

Response payload shape:
```json
{
  "success": true,
  "attestationId": "string"
}
```

## GET /api/v1/account/{accountId}/{mobileDeviceId}?nonce={nonce}

Used by:
- Dedicated backend-driven connected-devices route.

Signed:
- Yes.

Signed header:
- `x-signature: <base64-signature>`

HTTP request body:
- None.

Signed payload shape:
```json
{
  "accountId": "string",
  "mobileDeviceId": "string",
  "nonce": "string"
}
```

Response payload shape:
```json
{
  "success": true,
  "accounts": [
    {
      "accountId": "string",
      "devices": [
        {
          "accountId": "string",
          "deviceId": "string",
          "desktopDeviceId": "string",
          "deviceName": "string|null",
          "publicKey": "string|null",
          "namespaces": [
            {
              "accountId": "string",
              "desktopDeviceId": "string",
              "namespaceId": "string",
              "namespace": "string",
              "url": "string",
              "root": false
            }
          ]
        }
      ]
    }
  ]
}
```

Notes:
- The parser accepts `deviceId` and `desktopDeviceId` as aliases in account-device and namespace entries.

Rendered domain shape:
```json
[
  {
    "accountId": "string",
    "deviceId": "string",
    "namespaces": [
      {
        "namespaceId": "string",
        "namespaceName": "string",
        "namespaceUrl": "string",
        "deviceId": "string"
      }
    ]
  }
]
```