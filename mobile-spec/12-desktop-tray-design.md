# Desktop Tray Design

Purpose: define the information architecture, tray navigation, view content, and interaction logic for a desktop-tray application that works as the desktop companion for the existing mobile registration and approval flow.

Primary goals:
- Keep the desktop app available without taking permanent screen space.
- Make authentication state and pending approval state visible within one click from the system tray.
- Minimize the number of full windows by using the tray menu for fast actions and dedicated views only for tasks that need more context.
- Keep the desktop side readable for both single-account and multi-namespace setups.

Navigation model:
- The application starts as a tray-first application.
- Left click on the tray icon opens the quick panel.
- Right click on the tray icon opens the system context menu.
- Double click on the tray icon opens the `Home` view.
- The quick panel is the main navigation hub and links to all other views.
- Secondary views open in a single reusable window so the app does not create many parallel windows.

Navigation structure:
- `Quick panel`
- `Home`
- `Pending approval details`
- `Active sessions`
- `Connected devices`
- `Settings`
- `Device sync`
- `Logs and diagnostics`

Window rules:
- Only one main desktop window should exist at a time.
- If the user opens a second view, the existing window is reused and its content is replaced.
- Modal confirmation is allowed only for destructive actions such as revoke, disconnect, or exit.
- Short transient feedback such as `approval sent` or `session revoked` should be shown as toast or inline status, not as a blocking modal.

Tray states:
- `Idle`: no pending approval and no active warning.
- `Pending approval`: at least one authentication request is waiting for user confirmation.
- `Connected`: desktop app is registered and backend reachability is healthy.
- `Attention`: sync failure, expired registration, or local key problem.

Tray icon behavior:
- Icon appearance changes by state.
- A badge or accent color marks `Pending approval` and `Attention`.
- Hover tooltip shows current account label, device name, and a short state summary.

## Quick Panel

Purpose: provide one-click status, primary actions, and view navigation directly from the tray.

Shown data:
- Current account or namespace label
- Desktop device name
- Registration state
- Backend connection state
- Count of pending approvals
- Count of active sessions
- Last successful sync or poll timestamp

Actions:
- Open `Home`
- Open `Pending approval details` when a request exists
- Open `Active sessions`
- Open `Connected devices`
- Open `Settings`
- Open `Device sync`
- Pause notifications for a short duration
- Exit application

Logic:
- This panel is populated from the in-memory application state.
- It must open immediately even when the backend is temporarily unavailable.
- If data is stale, the panel shows the latest cached state and a `refreshing` marker.

## Home

Purpose: show the current desktop-tray status in a full view with the most important actionable information.

Shown data:
- Signed-in account label
- Local desktop identity
- Namespace or tenant selection summary
- Current notification permission state
- Current registration health
- Recent activity list with latest approvals, revokes, and sync events

Actions:
- Open `Pending approval details`
- Open `Active sessions`
- Open `Connected devices`
- Open `Settings`
- Retry failed sync or polling

Logic:
- This view is the full-detail status screen.
- It does not own long-running background work by itself.
- It renders data collected by shared services for polling, push handling, and local storage.

## Pending Approval Details

Purpose: present a clear and secure confirmation screen for a single authentication request.

Shown data:
- Request origin or desktop label
- Request timestamp
- Target account or namespace
- Risk markers such as unknown device or unusual location if available
- Expiration countdown

Actions:
- Approve request
- Decline request
- Open source device details when known

Logic:
- This view is opened from tray state, toast interaction, or the quick panel.
- Approval and decline actions must be explicit and require the focused user action.
- If the request expires while the screen is open, actions are disabled and the view switches to an expired state.
- After a completed decision, the app returns to `Home` or the previous view and refreshes session state.

## Active Sessions

Purpose: show live authenticated desktop sessions and allow revoke actions.

Shown data:
- Session device label
- Session identifier
- Issued-at timestamp
- Expiration timestamp
- Session state

Actions:
- Revoke one session
- Refresh list
- Open related connected device when a mapping exists

Logic:
- Session data is loaded by shared polling and refreshed on demand from this view.
- A revoke updates the list optimistically only after backend confirmation or after a successful refresh.
- Errors remain local to this view and should not block tray usage.

## Connected Devices

Purpose: show registered desktop endpoints and their relationship to namespaces or accounts.

Shown data:
- Root desktop name
- Child namespace entries
- Default namespace marker
- Live session badge
- Last sync timestamp
- Local trust or key status

Actions:
- Open device details
- Rename local device label
- Remove non-default device entry when allowed
- Open `Device sync`

Logic:
- This view reads from local storage first, then enriches with live session status from shared memory.
- Grouping should follow the same root-device rules already used by the mobile app where possible.
- Deleting the active default connection is blocked by local validation.

## Settings

Purpose: manage desktop-tray behavior, notification preferences, startup behavior, and maintenance actions.

Shown data:
- Launch on system startup toggle
- Notification toggle
- Sound or silent mode toggle
- Polling interval policy summary if configurable
- Current environment or backend endpoint label
- App version

Actions:
- Save preference changes
- Reset local cache
- Reconnect backend session
- Open `Logs and diagnostics`

Logic:
- Preference changes are persisted locally.
- Sensitive actions such as reset local cache must require confirmation.
- Settings changes should apply immediately when safe, otherwise on the next background cycle.

## Device Sync

Purpose: establish or repair the trust relationship between the desktop-tray app and the mobile-managed account state.

Shown data:
- Current pairing state
- Desktop device name
- Generated public key fingerprint
- QR code or sync token when the desktop is waiting for pairing
- Last completed sync result

Actions:
- Start new pairing
- Regenerate local key pair
- Show QR code again
- Cancel pairing flow

Logic:
- Pairing starts by generating or loading the local desktop key material.
- A temporary pairing payload is produced and displayed as QR code or equivalent transfer token.
- While pairing is pending, the desktop app starts a short-interval polling cycle for registration completion.
- When the backend confirms the pairing, the desktop stores the returned registration payload locally and promotes the app to `Connected` state.
- If the flow times out, the temporary token is discarded and the tray returns to the previous stable state.

## Logs and Diagnostics

Purpose: give support-friendly visibility into the last operations without exposing secrets.

Shown data:
- Recent sync events
- Recent approval events
- Polling health
- Last backend error code and timestamp
- App version and platform details

Actions:
- Copy diagnostic summary
- Clear local logs
- Retry failed background job

Logic:
- Secret material, raw private keys, and full signed payloads must never be shown here.
- This view is read-only except for support actions such as clear and retry.

Background services:
- `Approval listener`: receives push or poll-driven authentication requests and updates tray state.
- `Session poller`: refreshes active sessions on an interval and on demand.
- `Connectivity monitor`: tracks backend reachability and local network status.
- `Local storage manager`: persists preferences, device identity, cached sessions, and sync metadata.
- `Notification coordinator`: maps background events to tray badges, toasts, and focus behavior.

Shared logic:
- Background services own data fetching and state transitions.
- Views consume shared state and trigger commands; they should not each implement their own backend workflow.
- Every backend action should move through the same command pipeline: validate local prerequisites, build request, sign if required, submit, update shared state, then notify the UI.

State model:
- `Unpaired`: no valid local registration is available.
- `Pairing`: waiting for mobile-side confirmation or backend completion.
- `Connected`: registration is valid and the app can receive or poll for work.
- `Action required`: registration exists but needs repair because of expiry, signature failure, or connectivity problems.

View-to-state behavior:
- `Unpaired` routes the main window to `Device sync` first.
- `Pairing` keeps the tray accessible but prioritizes sync progress and timeout information.
- `Connected` enables the full navigation tree.
- `Action required` surfaces the failure in the quick panel, `Home`, and `Settings` with a direct recovery action.

Notification logic:
- Pending approval events raise the tray to `Pending approval` state.
- The first pending request should open a toast with `Approve` and `Decline` shortcuts when the operating system allows action buttons.
- Multiple concurrent requests should be queued and shown in the details view in arrival order.
- Completing the last pending request removes the pending badge and restores the previous tray state.

Error handling:
- Network errors should degrade into cached read-only UI where possible.
- Invalid local identity or key material should move the app to `Action required` and point the user to `Device sync`.
- Repeated background failures should be collapsed into one visible error state rather than many repeated popups.

Security logic:
- Private keys stay only in secure local storage.
- The tray UI may show public fingerprints and state summaries but never secrets.
- Destructive actions and approval actions should require the desktop window to be focused to reduce accidental clicks.
- Expired approvals and expired sessions should be revalidated against backend state before final UI confirmation.

Recommended implementation split:
- `Tray shell`: icon, tooltip, quick panel, notification bridge.
- `Application state`: global state container and reducers or equivalent state machine.
- `Background workers`: polling, push handling, retry scheduling.
- `View layer`: reusable window with navigable views.
- `Storage layer`: secure key storage plus normal preference cache.
- `API layer`: typed request builders, signing, transport, response mapping.

Open design decisions:
- Whether `Pending approval details` should be a standalone route or an overlay on `Home`.
- Whether the tray quick panel supports multi-account switching or only a single active account.
- Whether pairing uses QR only or also a manual token fallback.
- Whether diagnostics are end-user visible or gated behind a support mode.