# Home

Purpose: main post-login view with QR entry, notification approval, live desktop-session status, and settings entry.

Actions:
- Open `Settings`.
- Open `QR Read`.
- Accept or decline incoming authentication notification.
- Revoke a live desktop session directly from the card.

Data source:
- `namespaceName`, root-device identity, and recovery identity come from the current stored registration state.
- Live desktop-session card labels use the namespace name associated with the namespace URL that returned the session.
- Live desktop sessions come from an in-memory list refreshed by backend polling across all locally known namespaces, then merged client-side.
- The incoming authentication notification is delivered by FCM and held in memory; it is not persisted locally.

Request ownership:
- This view owns desktop-session revoke requests.
- This view owns authentication response submission requests.

Post-login background behavior:
- Desktop-session polling runs immediately and then every 30 seconds while the authenticated, registered app is in the foreground. It refreshes immediately again whenever the app returns to the foreground.
- This screen renders the shared in-memory live-session state produced by that background poller.
- Session polling also queries the current phone's mobile registrations when no desktop connections remain. A successful empty session response clears the old live-session list rather than treating the absence of desktop connections as a refresh error.

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
    "namespaceId": "string",
    "namespaceUrl": "string",
    "deviceName": "string",
    "type": "string"
  },
  "receivedAtEpochMillis": 0
}
```

Incoming message notes:
- `namespaceId` and `namespaceUrl` are optional but, when present, they are used to match the exact local namespace that should receive the authentication response.
- If those keys are absent, the app falls back to matching by `accountId + deviceId`.

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
- The request is issued once per locally known namespace base URL for the same mobile device identity.

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

Desktop session poll notes:
- The backend response does not include `namespaceName`.
- The app enriches each returned session locally with the namespace name of the namespace URL that produced that response.
- Results from all known namespace base URLs are merged in memory and deduplicated by `attestationId`.

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
- The Home screen shows only per-session live cards with revoke actions; it does not render a separate authentication namespace status list.