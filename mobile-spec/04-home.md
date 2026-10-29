# Home

Purpose: main post-login view with QR entry, notification approval, live desktop-session status, and settings entry.

Actions:
- Open `Settings`.
- Open `QR Read`.
- Refresh all configured servers and their live desktop sessions from the header
  icon between QR scan and Settings. Database rows have no separate refresh icon.
- Accept or decline incoming authentication notification.
- Revoke a live desktop session directly from the card.

Data source:
- `namespaceName`, root-device identity, and recovery identity come from the current stored registration state.
- Live desktop-session card labels use the namespace name associated with the namespace URL that returned the session.
- Live desktop sessions come from an in-memory list refreshed by backend polling across all locally known namespaces, then merged client-side.
- The incoming authentication notification is delivered by FCM and held in memory; it is not persisted locally.

Server list:
- Show each registered namespace name and its availability indicator, without a separate domain or URL line.
- Keep stored namespace URLs and their use for backend requests unchanged.

Notification information box:
- Always show `Biometric validity` and a live `mm:ss` countdown, using the existing
  biometric access deadline and a monotonic clock. Update once per second, never
  extend the access window just by ticking or tapping, and reset to zero on lock or expiry.
- Tapping the existing biometric validity row while its countdown is positive opens
  the system strong-biometric prompt without adding a separate button or navigating
  away from Home. Successful biometric authentication and local device-key proof
  restart the full 300-second window. Cancellation or an unsuccessful scan does not
  extend the previous deadline. The row is disabled when the countdown reaches zero.
- When online, show no internet-connection label or positive network indicator.
- Show `No internet connection` with a red 12 dp status circle only when Android
  does not report validated internet access. This warning also appears alongside
  an incoming notification. Observe network changes while the UI is active and
  release callbacks when inactive.
- With no notification, show `Device: X` using the current device identity and a blue border.
- With a notification, additionally show `Incoming notification`, `Device: X`, and
  `Date time`, with a green border. Use the notification device name, falling back
  to its desktop device ID and then the current device identity.
- Display the received timestamp in the device's local date and time format.
- Size the box to its content and keep notification errors and dismissal below it.
- Keep the information box and Allow/Decline buttons anchored at the bottom of
  the safe visible area, outside the independently scrolling Home content.

Request ownership:
- This view owns desktop-session revoke requests.
- This view owns authentication response submission requests.

Post-login background behavior:
- Desktop-session polling runs immediately and then every 30 seconds while the authenticated, registered app is in the foreground. It refreshes immediately again whenever the app returns to the foreground.
- This screen renders the shared in-memory live-session state produced by that background poller.
- Automatic polling never activates the header refresh button's busy indicator.
- Session polling also queries the current phone's mobile registrations when no desktop connections remain. A successful empty session response clears the old live-session list rather than treating the absence of desktop connections as a refresh error.

Manual refresh behavior:
- Reuse the shared availability/session refresh path. Show the header busy indicator
  only for a user-triggered refresh, and clear it on completion or failure.
- If background refresh is already running, a manual click joins that work and
  shows progress without submitting overlapping requests. Further automatic ticks
  cannot hide an active manual refresh indicator.
- Check every known account/server target. A target's two-second timeout or request
  failure marks it unavailable and must not abort checks of remaining targets.
- Skip session requests for targets reported offline. Query reachable targets with
  fresh signed nonces and merge successful session responses, including empty lists.
- Isolate session request failures and two-second timeouts per target; preserve
  genuine coroutine cancellation rather than treating it as an unavailable server.
- Show the generic `HOME_REFRESH_FAILED` code if the refresh cannot complete.

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