# Corporate Recovery

Scope: this is the authenticated Settings view for recovery of the currently
selected namespace server.

Purpose: restore a corporate account connection using a recovery QR code from
the desktop device when the previously linked phone has been lost.

Layout:
- The Settings action and page title are `Corporate recovery`.
- A description panel explains starting recovery on the desktop and scanning
	its recovery QR code. Stored email and other contact details are not shown.
- The QR reader is embedded directly below the description, with the existing
	camera permission request and retry action. It is not a bottom sheet.
- `Back` remains fixed at the bottom of the screen.

Actions:
- Scan the desktop recovery QR directly on this screen and confirm the server
	before starting the existing recovery process. The QR identifies the Recovery Server,
	which may differ from the current namespace URL. The process reuses the
	phone's existing Keystore keypair and mobile device ID.
- Return to `Settings`.

Data source:
- The shared recovery view model validates the QR, manages pairing, and polls
	recovery progress. First-launch recovery keeps its separate entry screen.

Validation and state:
- QR payloads are limited to 4096 characters and must contain exactly the
	expected typed fields, integer version 1, and a canonical recovery UUID.
- The recovery URL must be an HTTP(S) origin with a valid port, without user
	credentials, query, fragment, or non-root path. HTTP is development-only.
- The UI uses typed scan, confirmation, waiting, and completed steps and
	displays generic recovery error codes rather than raw exception messages.
- Pending recovery can open the inline scanner again. Reading another QR
	still requires explicit server confirmation before any recovery request.
- Scan cancellation does not cancel the underlying recovery. Request actions
	are disabled while a recovery operation is in progress.

Successful recovery adds or replaces only the current server's local mobile
namespace record with `root = false`. It does not overwrite the active gateway
registration or recover additional servers automatically.

Related specification:
- `14-first-launch-recovery.md` defines the shared recovery route, QR scan,
  recovery APIs, and mode-specific final activation.