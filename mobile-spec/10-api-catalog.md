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
- `GET /api/v1/root-device`
- `POST /api/v1/root-device`
- `POST /api/v1/authentication/response`
- `POST /api/v1/authentication/response/direct`
- `GET /api/v1/authentication/{accountId}?deviceId={deviceId}&nonce={nonce}`
- `DELETE /api/v1/authentication/{accountId}/{desktopDeviceId}/{attestationId}?nonce={nonce}`
- `GET /api/v1/account/{accountId}/{deviceId}?accountId={accountId}&deviceId={deviceId}&nonce={nonce}`

## POST /api/v1/account

Used by:
- Initial phone registration.
- QR namespace flow phone registration.

Signed:
- No.

Request payload shape:
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
  "phone": "string"
}
```

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
  "deviceId": "string",
  "namespaceId": "string",
  "name": "string",
  "url": "string"
}
```

Signed payload shape:
```json
{
  "accountId": "string",
  "deviceId": "string",
  "namespaceId": "string",
  "name": "string",
  "url": "string"
}
```

Response payload shape:
- `HTTP 200/204` with empty body.

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
  "deviceId": "string",
  "fcmToken": "string",
  "nonce": "string"
}
```

Signed payload shape:
```json
{
  "accountId": "string",
  "deviceId": "string",
  "fcmToken": "string",
  "nonce": "string"
}
```

Response payload shape:
- `HTTP 200/204` with empty body.

## GET /api/v1/root-device

Used by:
- Retrofit endpoint is defined, but no current call site was found in the app code.

Signed:
- Yes.

Signed header:
- `x-signature: <base64-signature>`

HTTP request body:
- None.

Signed payload shape:
- Not determined from a live call site because the endpoint is currently unused.

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
    "deviceIdAdditional": "string"
  },
  "dek": {
    "deviceId": "string",
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
    "deviceIdAdditional": "string"
  },
  "dek": {
    "deviceId": "string",
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
  "deviceId": "string",
  "approved": true,
  "nonce": "string",
  "envelop": {
    "desktopId": "string",
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
  "deviceId": "string",
  "approved": true,
  "nonce": "string",
  "envelop": {
    "desktopId": "string",
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
- Authentication approval or denial mirrored to other locally known namespace base URLs that do not own the incoming notification target.

Signed:
- Yes.

Signed header:
- `x-signature: <base64-signature>`

Request payload shape:
```json
{
  "notificationId": "string",
  "accountId": "string",
  "deviceId": "string",
  "approved": true,
  "nonce": "string",
  "envelop": {
    "desktopId": "string",
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
  "deviceId": "string",
  "approved": true,
  "nonce": "string",
  "envelop": {
    "desktopId": "string",
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

## GET /api/v1/authentication/{accountId}?deviceId={deviceId}&nonce={nonce}

Used by:
- Home live desktop-session polling.
- Dedicated desktop sessions route.

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
  "deviceId": "string",
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

## GET /api/v1/account/{accountId}/{deviceId}?accountId={accountId}&deviceId={deviceId}&nonce={nonce}

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
  "deviceId": "string",
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
          "deviceId": "string"
        }
      ],
      "namespaces": [
        {
          "accountId": "string",
          "deviceId": "string",
          "namespaceId": "string",
          "name": "string",
          "namespace": "string",
          "url": "string"
        }
      ]
    }
  ]
}
```

Rendered domain shape:
```json
[
  {
    "accountId": "string",
    "deviceId": "string",
    "namespaces": [
      {
        "namespaceName": "string",
        "namespaceUrl": "string"
      }
    ]
  }
]
```