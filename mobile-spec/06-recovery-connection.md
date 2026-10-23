# Recovery Connection

Scope: this is the authenticated Settings view for recovery of the currently
selected namespace server.

Purpose: show recovery contact data from stored registration.

Shown data:
- User email
- Phone

Actions:
- Start `Recovery`. The scanned recovery QR identifies the Recovery Server,
	which may differ from the current namespace URL. The process reuses the
	phone's existing Keystore keypair and mobile device ID.
- Return to `Settings`.

Data source:
- Values are read from the currently stored registration state.

Successful recovery adds or replaces only the current server's local mobile
namespace record with `root = false`. It does not overwrite the active gateway
registration or recover additional servers automatically.

Related specification:
- `14-first-launch-recovery.md` defines the shared recovery route, QR scan,
  recovery APIs, and mode-specific final activation.