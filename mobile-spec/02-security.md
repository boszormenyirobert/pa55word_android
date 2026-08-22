# Security

Purpose: local biometric or device-credential gate before the app becomes usable.

Actions:
- Authenticate locally.
- On success open `Home` or `Registration` depending on stored registration state.

Data source:
- Uses Android local biometric or device-credential authentication only.
- Reads the presence of stored registration state to decide whether success routes to `Home` or `Registration`.
- Does not start any backend request.

Payloads:
- None. No backend request.