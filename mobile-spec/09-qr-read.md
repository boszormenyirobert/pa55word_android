# QR Read

Purpose: scan a desktop QR code and link the phone to a namespace/root-device relation.

Data source:
- QR payload is read from the camera scanner.
- Current phone registration identity and stored encrypted DEK come from local registration state.
- Existing namespace entries come from local namespace storage.

Request ownership:
- This flow owns the QR-driven phone registration request when the scanned QR contains a namespace.
- This flow owns the desktop root-device registration request.
- The Retrofit namespace-link endpoint exists in the codebase, but no current live call site was found for it in this flow.

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

Namespace link request shape:
```json
{
  "accountId": "string",
  "deviceId": "string",
  "namespaceId": "string",
  "name": "string",
  "url": "string"
}
```

Signed:
- Yes.
- Signed payload is the same as the transmitted request body.

Namespace link response shape:
- `HTTP 200/204` with empty body.

Notes:
- QR scanning is local.
- Device-only QR updates the local namespace list and then registers the desktop root-device relation remotely.
- Namespace QR first performs phone account registration on the scanned namespace, stores the new namespace locally, and then registers the desktop root-device relation remotely.