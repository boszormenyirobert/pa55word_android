# Home

Purpose: main post-login view with QR entry, notification approval, live desktop-session status, and settings entry.

Actions:
- Open `Settings`.
- Open `QR Read`.
- Accept or decline incoming authentication notification.
- Poll desktop sessions every 15 seconds.
- Revoke a live desktop session directly from the card.

Data source:
- `namespaceName`, root-device identity, and recovery identity come from the current stored registration state.
- Desktop labels are resolved from the locally stored namespace list.
- Live desktop sessions come from an in-memory list refreshed by backend polling.
- The incoming authentication notification is delivered by FCM and held in memory; it is not persisted locally.

Request ownership:
- This view owns desktop-session polling and session revoke requests.
- This view owns authentication response submission requests.

Incoming message shape:
```json
{
  "title": "string",
  "body": "string",
  "type": "string",
  "screen": "string",
  "accountId": "string",
  "deviceId": "string",
  "data": {
    "notificationId": "string",
    "deviceName": "string",
    "type": "string"
  },
  "receivedAtEpochMillis": 0
}
```

Authentication response request shape:
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

Signed:
- Yes.
- Signed payload is the same as the transmitted request body.

Authentication response shape:
- `HTTP 200/204` with empty body.

Desktop session poll request shape:
```json
{
  "accountId": "string",
  "deviceId": "string",
  "nonce": "string"
}
```

Signed:
- Yes.
- Signed payload differs from the HTTP request because the HTTP call is `GET` with query parameters and no body.

Desktop session poll signed payload shape:
```json
{
  "accountId": "string",
  "deviceId": "string",
  "nonce": "string"
}
```

Desktop session poll response shape:
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

Desktop session revoke request shape:
```json
{
  "accountId": "string",
  "deviceId": "string",
  "desktopId": "string",
  "attestationId": "string",
  "nonce": "string"
}
```

Signed:
- Yes.
- Signed payload is the same as the transmitted request body.

Desktop session revoke response shape:
```json
{
  "success": true,
  "attestationId": "string"
}
```

Notes:
- Desktop-session polling is started here and the resulting state is reused by `Settings Connected Devices` for its `live session` badge.