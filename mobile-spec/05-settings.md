# Settings

Purpose: entry menu for view-specific management screens.

Service plan:
- The `Service plan` menu opens a selection view with two large installation-style menu items: `Basic` and `Enterprise`.
- Each item opens its own details view containing placeholder description text and a checkbox.
- Selecting a plan immediately persists it. The current plan stays checked and cannot be deselected without choosing the other plan.
- New registrations default to `Basic`.
- The selection is persisted as `servicePlan`, with the string value `basic` or `enterprise`, in the main encrypted registration-state JSON.
- Only the new field and values are used. No previous mode values are migrated or supported.
- Background registration updates preserve the selected service plan.
- This is a local preference, not billing or backend authorization.

Actions:
- Open `Service plan`.
- Open `Database connections` (previously `Accounts - Servers`), visible only for `Enterprise`.
- Open `Connected devices`.
- Open `Device information`.
- Open `Enterprise recovery`.

Navigation targets:
- `Service plan` opens the Basic/Enterprise menu; details return to that menu, which returns to Settings.
- `Database connections` opens database account management. Database connection routes and management actions are unavailable for `Basic`.
- `Connected devices` opens the local root-device and namespace management view.
- `Device information` opens the current phone identity and default backup-connection selector.
- `Enterprise recovery` opens recovery guidance with an inline desktop recovery QR reader.

Data sources:
- No direct backend request is started from this menu.
- The child screens use stored registration state, stored namespace state, and already loaded live desktop-session state.

Database connection email:
- `Add new database` requires an email address, name, and URL. Empty or malformed email addresses are rejected before network requests.
- The account-registration request uses the entered, trimmed email address, not the current gateway email.
- Every locally created `LocalConnectedNamespace` stores its account email. Mobile-only connections store it in `MobileNamespaceRegistration`; `NamespaceEntry` carries it between layers.
- Account refresh reads the server account email. A response without email preserves only a matching local account-and-server email.
- Selecting or replacing the gateway uses that connection's email. Adding a connection does not change the existing gateway registration.

Payloads:
- None. No backend request.