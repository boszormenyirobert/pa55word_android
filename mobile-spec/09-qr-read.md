# QR Read

Purpose: scan a desktop QR code and link the phone to a namespace/root-device relation.

Data source:
- QR payload is read from the camera scanner.
- Current phone registration identity and stored encrypted DEK come from local registration state.
- Existing namespace entries come from local namespace storage.

Request ownership:
- This flow owns the QR-driven phone registration request when the scanned QR contains a namespace.
- This flow owns the desktop root-device registration request.

QR device payload shape:
```json
{
  "type": "deviceRegistration",
  "desktopDeviceId": "string",
  "publicKey": "string",
  "deviceName": "string"
}
```

QR namespace payload shape:
```json
{
  "type": "namespace",
  "desktopDeviceId": "string",
  "publicKey": "string",
  "deviceName": "string",
  "namespaceId": "string",
  "namespaceName": "string",
  "namespaceUrl": "string"
}
```

QR namespace_sync payload shape:
```json
{
  "type": "namespace_sync",
  "desktopDeviceId": "string",
  "publicKey": "string",
  "deviceName": "string",
  "namespaceId": "string",
  "namespaceUrl": "string"
}
```

QR new mobile payload shape:
```json
{
  "publicKey": "new-mobile-public-key",
  "deviceName": "New phone",
  "mobileDeviceId": "new-mobile-id",
  "namespaceUrl": "https://namespace.example",
  "type": "addition_mobile_device"
}
```

For `addition_mobile_device`, the registered phone decrypts its stored DEK, encrypts the raw DEK for the scanned public key, and submits `POST /api/v1/mobile-device`. The request is signed by the registered phone.

After that request succeeds, the registered phone displays a second QR code:

```json
[
  {
    "accountId": "1272709c-0b34-43af-8cbe-ca41cb8bfdbc",
    "desktopDeviceId": "OdXnD5Ehb2",
    "namespaceId": "e06798dafb",
    "namespace": "dev",
    "url": "http://192.168.178.20:3001",
    "root": false
  }
]
```

The namespace used for the initial new-mobile registration is excluded. Other locally stored namespace objects remain in their original order, including entries that share the same URL.

The `root` value is copied from the original server response, preserved in local storage and sent unchanged as `namespace.root` during registration on the target server.

After the new mobile receives and stores its SSE bootstrap payload, it opens QR Read instead of Home. Scanning this array registers the same mobile identity on every listed server through the normal unsigned `POST /api/v1/account` flow. Each successful response's new account ID replaces the source account ID when the namespace is written to the new phone's connected-devices store. Successful entries survive partial failure and are skipped on retry. After all entries succeed, the pending scan flag is cleared and the app navigates to Home.

`namespace_sync` follows a different flow from `namespace`:
- `namespaceName` is omitted from the QR payload.
- The app resolves the namespace locally by normalized `namespaceUrl`.
- The app skips the QR phone-registration `POST /api/v1/account` request.
- The signed namespace-link requests go to `POST /api/v1/namespace/link` instead of `POST /api/v1/namespace`.

Phone registration request from QR shape:
```json
{
  "namespace": {
    "namespaceId": "string",
    "name": "string",
    "url": "string"
  },
  "device": {
    "mobileDeviceId": "string",
    "publicKey": "string",
    "deviceName": "string"
  },
  "dek": {
    "mobileDeviceId": "string",
    "encDek": "string"
  },
  "email": "string",
  "phone": "string",
  "nonce": "string"
}
```

This QR-driven phone registration request does not send the `root` field.

Signed:
- No.
- The app sends the request body with a payload `nonce`, but without a transmitted signature header.

Phone registration response shape:
```json
{
  "success": true,
  "accountId": "string"
}
```

Notes:
- QR scanning is local.
- Device-only QR updates the local namespace list and then registers the desktop root-device relation remotely.
- Namespace QR first performs phone account registration on the scanned namespace using the phone's existing identity data plus the scanned namespace data, stores the new namespace locally, and then registers the desktop root-device relation remotely.
- Namespace_sync QR resolves an already known namespace by URL, submits one signed namespace-link request to `/namespace/link`, and then registers the desktop root-device relation remotely.