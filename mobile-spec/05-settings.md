# Settings

Purpose: entry menu for view-specific management screens.

Mode:
- Radio buttons at the top select `Private` or `Corporate`.
- New and existing registrations default to `Private`.
- The selection is persisted as `corporateMode` in the main encrypted registration-state JSON.
- Background registration updates preserve the selected mode.

Actions:
- Open `Database connections` (previously `Accounts - Servers`), visible only in `Corporate` mode.
- Open `Connected devices`.
- Open `Device information`.
- Open `Corporate recovery`.

Navigation targets:
- `Database connections` opens database account management. Database connection routes and management actions are unavailable in `Private` mode.
- `Connected devices` opens the local root-device and namespace management view.
- `Device information` opens the current phone identity and default backup-connection selector.
- `Corporate recovery` opens recovery guidance with an inline desktop recovery QR reader.

Data sources:
- No direct backend request is started from this menu.
- The child screens use stored registration state, stored namespace state, and already loaded live desktop-session state.

Payloads:
- None. No backend request.