# API Catalog

Purpose: single inventory of every backend HTTP API currently defined by the app, including request payload, response payload, signature usage, and whether the request is signed.

Signature header:
- Signed requests send `x-signature`.
- Unsigned requests do not send `x-signature`.

Unsigned requests:
- `POST /api/v1/account`

Signed requests:
- `POST /api/v1/namespace`
- `POST /api/v1/fcm`
- `GET /api/v1/root-device?nonce={nonce}`
- `POST /api/v1/root-device`
- `POST /api/v1/authentication/response`
- `POST /api/v1/authentication/response/direct`
- `GET /api/v1/authentication/{accountId}?deviceId={deviceId}&nonce={nonce}`
- `DELETE /api/v1/authentication/{accountId}/{desktopDeviceId}/{attestationId}?nonce={nonce}`
- `GET /api/v1/account/{accountId}/{mobileDeviceId}?nonce={nonce}`

## POST /api/v1/account

Used by:
- Initial phone registration.
- QR namespace flow phone registration.

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

## POST /api/v1/namespace

Used by:
- Retrofit endpoint is defined for namespace linking, but no current live call site was found in the app code.

Signed:
- Yes.

Signed header:
- `x-signature: <base64-signature>`

Request payload shape:
```json
{
  "accountId": "string",
  "foreignAccountId": "string",
  "mobileDeviceId": "string",
  "desktopDeviceId": "string",
  "namespaceId": "string",
  "name": "string",
  "url": "string",
  "nonce": "string"
}
```

Signed payload shape:
```json
{
  "accountId": "string",
  "foreignAccountId": "string",
  "mobileDeviceId": "string",
  "desktopDeviceId": "string",
  "namespaceId": "string",
  "name": "string",
  "url": "string",
  "nonce": "string"
}
```

Notes:
- In the QR namespace reverse-link request, `foreignAccountId` is sent as an empty string.

Response payload shape:
```json
{
  "authorized": true,
  "status": "authorized",
  "notificationId": "string",
  "session": {},
  "attestationId": {
    "attestationId": "string"
  }
}
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