# 05. Implementation backlog

## Purpose

This document defines the recommended Copilot execution order for the Android app so each prompt can target a narrow, testable slice.

## Milestone 1: project skeleton

Create the application shell with:

- Gradle setup
- Hilt wiring
- package structure aligned to the split specification
- Compose navigation skeleton
- placeholder screens for security, registration, loading, and home

Expected outcome:

- app builds
- navigation graph compiles
- DI container starts

## Milestone 2: domain models and DTOs

Create:

- domain models for namespace, device, DEK, registration payload, registration result
- network DTOs matching [02-backend-contract.md](02-backend-contract.md)
- mapper layer between domain and data models

Expected outcome:

- the request body for `POST /api/v1/account` is represented exactly once in the codebase

## Milestone 3: local validation

Implement validation for:

- email presence and format policy
- phone presence and format policy
- namespace name presence
- API URL validity
- `deviceId` format
- `namespaceId` format
- `dek.deviceId == device.deviceId`

Expected outcome:

- invalid payloads are blocked before network calls

## Milestone 4: keystore and crypto

Implement:

- `KeyStoreService`
- `DeviceService`
- `NamespaceService`
- `DekService`
- `SignatureService`

Expected outcome:

- RSA keypair generation works
- the alias `secure_device_registration_primary` is used consistently
- public key export format matches the spec
- `encDek` generation works
- signature generation works locally

## Milestone 5: storage and app state

Implement:

- secure persistence of registration state
- startup decision logic
- authenticated session gate before screen routing

Expected outcome:

- app launches to registration on first run
- app launches to home on registered state

## Milestone 6: network registration

Implement:

- account registration API client for `POST /api/v1/account`
- repository abstraction
- error mapping
- loading and retry state handling

Expected outcome:

- successful registration persists returned `accountId`
- backend errors are surfaced in a controlled UI state
- the request body sent to `POST /api/v1/account` matches the plain account payload contract exactly

## Milestone 7: UI completion

Complete:

- form UX
- loading state UX
- success state UX
- error presentations without leaking sensitive values

Expected outcome:

- production-oriented registration flow UI exists end to end

## Prompting guidance for Copilot

Use narrow prompts such as:

- `Create only the domain models and DTOs for POST /api/v1/account based on mobile-spec/02-backend-contract.md.`
- `Implement KeyStoreService and SignatureService only, following mobile-spec/03-security-and-crypto.md.`
- `Build RegistrationScreen and its ViewModel only, using the validation rules from mobile-spec/04-app-flow-and-storage.md.`

## Rules Copilot should not violate

- do not change the backend contract silently
- do not log sensitive cryptographic material
- do not export the private key
- do not invent missing business logic without marking it explicitly
- do not send the v1 registration request inside a `payload` and `signature` wrapper
- do not widen scope across milestones unnecessarily