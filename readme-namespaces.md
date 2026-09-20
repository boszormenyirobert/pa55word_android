# Namespace-ek lekérése

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
