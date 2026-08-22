# Settings

Purpose: entry menu for view-specific management screens.

Actions:
- Open `Connected devices`.
- Open `Device information`.
- Open `Recovery connection`.

Navigation targets:
- `Connected devices` opens the local root-device and namespace management view.
- `Device information` opens the current phone identity and default backup-connection selector.
- `Recovery connection` opens the stored recovery contact view.

Data sources:
- No direct backend request is started from this menu.
- The child screens use stored registration state, stored namespace state, and already loaded live desktop-session state.

Payloads:
- None. No backend request.