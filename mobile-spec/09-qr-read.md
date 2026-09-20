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