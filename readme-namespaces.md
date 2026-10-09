# Namespace-ek lekérése

## Add new database (Accounts)

Accounts has an **Add new database** button opening a separate page with **Name**,
**URL**, and **Back**. Submission validates the form and rejects a normalized URL
already stored for this phone, including its gateway and legacy desktop connections.
The name and URL use the existing deterministic namespace ID generator
(the first 10 hexadecimal characters of SHA-256 of `name|url`).

Before registration, the app requires a successful GET to the URL plus
`AppConstants.HEALTH_ENDPOINT_PATH` (currently `/api/v1/healthy`). It then POSTs to
`{url}/api/v1/account` using the same mobile-registration handler as namespace QR
registration: namespace ID/name/URL, the phone's ID/public key/device name,
its existing encrypted DEK, stored email/phone, and a fresh nonce.
Only a successful HTTP response with `success: true` and a nonblank `accountId`
is persisted in encrypted `mobile_namespace_registrations`.

The Accounts list observes mobile-only registrations as well as desktop connections.
Mobile-registration writes are serialized, and account refresh preserves registrations
added while its requests were in flight.
Success returns to Accounts without registering a desktop, uploading an FCM token,
fetching connected devices as part of submission, or changing the selected gateway.
Failures remain on the form with an error message. Submission disables editing and
Back to prevent duplicate requests or leaving an in-flight registration.

## namespace_sync desktop registration

The phone must already be registered on the target server. Mobile registrations
are read from `devices[].mobileDeviceId`, `devices[].namespaces`, and the account's
mobile `dek` entry. An account with no desktop is valid and produces an empty
desktop list, not a parsing error.

These mobile account/namespace connections are persisted separately from desktop
connections in encrypted preferences (`mobile_namespace_registrations`). Existing
installations populate this key on the next successful account refresh. A sync
uses the target account's encrypted DEK, not the home account's DEK. If several
accounts match the normalized URL for this phone, the first stored registration
is used automatically, without an account-selection dropdown.
Before resolving the account, every sync awaits an account-list refresh, including
when a matching registration is already cached. Refresh failures abort the sync;
cached registrations are not used as a fallback.

Matching uses the normalized URL and mobile identity, not the QR namespace ID.
The selected registration supplies the account, namespace, and encrypted DEK.
Older desktop sync QRs with differently derived namespace IDs remain compatible.
Neither a missing registration nor a failed refresh creates a new mobile account.

Account-list refresh treats HTTP 401 with `e_40101_2` (missing signature device)
and HTTP 410 as a removed mobile registration. Only that server/account connection
is pruned; other servers continue loading. If the removed account was the gateway,
a successfully fetched remaining mobile registration becomes the gateway with its
own DEK. With no remaining registration, local registration is cleared. Invalid
signatures, generic 401/404 responses, and network errors are not treated as removal.

Account connection cleanup is shared by list refresh and account removal. Both the
desktop connections and the mobile registrations are filtered by server/account;
the same snapshot filter excludes removed accounts from memory and merged results.
Stored-account status checks run once per server/account, not once per desktop.

A namespace-ek lekérése kétféleképpen történhet: backend API hívással, valamint az alkalmazáson belüli helyi tárolóból/repository-ból.

---

## 1. Backend API (HTTP GET)

Az alábbi aláírt (signed) API végpont adja vissza a fiókhoz és eszközökhöz tartozó namespace-eket.

### Endpoint
`GET /api/v1/account/{accountId}/{mobileDeviceId}?nonce={nonce}`

### Fejlécek (Headers)
- `x-signature: <base64-signature>`

### Aláírt JSON tartalom (Signature payload)
```json
{
  "accountId": "string",
  "mobileDeviceId": "string",
  "nonce": "string"
}
```

### Válasz struktúra (Response body)
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
          "deviceName": "string",
          "publicKey": "string",
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

---

## 2. Alkalmazáson belüli lekérés (Android / Kotlin)

### Repository-ból (Flow használatával)
```kotlin
val namespacesFlow: Flow<List<NamespaceEntry>> = namespacesRepository.observeNamespaces()
```

### Helyi tárolóból (Local Encrypted Storage)
A namespace-ek helyileg a `namespaces_state` kulcs alatt vannak eltárolva JSON tömbként:
```json
[
  {
    "namespaceName": "string",
    "namespaceId": "string",
    "namespaceUrl": "string",
    "accountId": "string",
    "deviceId": "string",
    "deviceName": "string",
    "publicKey": "string"
  }
]
```
