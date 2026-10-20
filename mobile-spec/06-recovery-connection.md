# Recovery Connection

Scope: this is the authenticated Settings view. It is not the account-recovery
workflow used before an initial registration.

Purpose: show recovery contact data from stored registration.

Shown data:
- User email
- Phone

Actions:
- Return to `Settings`.

Data source:
- Values are read from the currently stored registration state.

Payloads:
- None. Data is read from local registration state.

Related specification:
- `14-first-launch-recovery.md` defines the separate unauthenticated recovery
	route, QR scan, recovery APIs, and final registration activation.