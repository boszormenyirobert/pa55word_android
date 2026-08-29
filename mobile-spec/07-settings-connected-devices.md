# Settings Connected Devices

Purpose: show locally stored desktop root devices grouped by root-device name, list their namespace children, show whether a grouped desktop currently has a live session, and allow deleting non-default namespace entries.

Shown data:
- Root device name
- `live session` badge on the root desktop box when any namespace in that grouped root device matches an active desktop session
- Namespace name
- Namespace URL
- Backup connection badge for the active default namespace
- Delete action for non-default namespace entries
- Inline error message for failed delete attempts
- Delete confirmation state for the selected namespace entry

Actions:
- Delete non-default namespace entries from local storage.
- Return to `Settings`.

Grouping rules:
- Namespace entries are grouped by `deviceName` when present.
- If `deviceName` is blank, grouping falls back to `deviceId`.

Live session source:
- This screen does not fetch desktop sessions by itself.
- The `live session` badge is derived from the already loaded active desktop-session list.
- A grouped root device is marked as live when any namespace in the group has a `deviceId` matching an active desktop session.
- The underlying live-session list is already merged across all locally known namespace base URLs before this screen reads it.

Delete behavior:
- The current default backup connection cannot be deleted.
- Attempting to delete the active gateway namespace returns a local validation error.
- Successful delete updates only local namespace storage.

Data source:
- Root-device and namespace entries are read from the local namespace state, which is refreshed from the account details API response after connected-devices fetch.
- Gateway/default namespace state comes from the current stored registration state.
- Live desktop-session status comes from the shared in-memory desktop session state populated elsewhere in the app.

Payloads:
- None. Data is read from local namespace state.