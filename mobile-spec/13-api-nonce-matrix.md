# API Nonce Matrix

Purpose: quick reference for every backend HTTP API, showing whether the request includes a nonce and whether that nonce is sent in the payload or in the query string.

Source of truth:
- Derived from 10-api-catalog.md.

| Method | Endpoint | Signed | Nonce | Location | Notes |
| --- | --- | --- | --- | --- | --- |
| POST | `/api/v1/account` | No | Yes | Payload | Registration request includes `nonce` in the request body, but does not send an `x-signature` header. |
| POST | `/api/v1/namespace` | Yes | Yes | Payload | Namespace link request includes `nonce` in the request body and uses it in the signed payload. |
| POST | `/api/v1/fcm` | Yes | Yes | Payload | `nonce` is present in the request body. |
| GET | `/api/v1/root-device` | Yes | Yes | Query | `nonce` is sent as `?nonce={nonce}` and is also part of the signed payload. |
| POST | `/api/v1/root-device` | Yes | Yes | Payload | `nonce` is present in the request body. |
| POST | `/api/v1/authentication/response` | Yes | Yes | Payload | Top-level `nonce` is in the request body; nested `envelop.nonce` also exists. |
| POST | `/api/v1/authentication/response/direct` | Yes | Yes | Payload | Top-level `nonce` is in the request body; nested `envelop.nonce` also exists. |
| GET | `/api/v1/authentication/{accountId}` | Yes | Yes | Query | `nonce` is sent as `?deviceId={deviceId}&nonce={nonce}`. |
| DELETE | `/api/v1/authentication/{accountId}/{desktopDeviceId}/{attestationId}` | Yes | Yes | Query | `nonce` is sent as `?nonce={nonce}`. |
| GET | `/api/v1/account/{accountId}/{deviceId}` | Yes | Yes | Query | `nonce` is sent as `?nonce={nonce}`. |

Summary:
- Nonce in payload: 6 endpoints.
- Nonce in query: 4 endpoints.
- No nonce: 0 endpoints.