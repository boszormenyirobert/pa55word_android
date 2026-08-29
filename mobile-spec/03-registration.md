# Registration

Purpose: create the phone registration against the selected namespace.

Inputs:
- Email
- Phone
- Namespace name
- API URL

Data source:
- User-entered form fields.
- Locally generated Android Keystore public key.
- Locally generated device identity.
- Locally generated encrypted DEK.

Request owner:
- This screen directly owns the account registration request.

Request shape:
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

Signed:
- No.
- The request carries `nonce` in the payload, but it does not send an `x-signature` header.

Response shape:
```json
{
  "success": true,
  "accountId": "string"
}
```

Notes:
- Validation happens before request.
- Success stores registration state locally.
- Initial phone registration does not populate the local namespace list by itself.