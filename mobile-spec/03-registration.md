# Registration

Purpose: create the phone registration against the selected namespace.

Inputs:
- Email

Data source:
- User-entered form fields.
- Both registration flows use the default namespace name `Pa55word basic`; neither form asks for a namespace name.
- Both new account registration and adding a new mobile to an existing account automatically use `AppConstants.PRODUCTION_API_BASE_URL` (`https://prod-api.pa55word.com`). Neither form asks for an API URL.
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
- Phone numbers are not collected, stored, validated, or sent in registration requests.
- Validation happens before request.
- Success stores registration state locally.
- API URL persistence and later use of saved URLs remain unchanged. Existing saved registrations are not rewritten to use the production URL.
- Initial phone registration does not populate the local namespace list by itself.