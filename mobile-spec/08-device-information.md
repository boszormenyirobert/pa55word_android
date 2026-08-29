# Device Information

Purpose: show the current phone root-device identity and the active backup connection device, and allow selecting the default namespace.

Shown data:
- Root device name
- Error message when namespace selection fails
- Backup connection device name
- Backup connection device URL
- Read-only current default namespace field
- Dropdown list of all locally stored namespace options
- Save button enabled only when the selected namespace differs from the current one

Actions:
- Select a default namespace from local stored namespaces.
- Save the local selection.
- Return to `Settings`.

Selection behavior:
- The current selected namespace is matched by `accountId`, `namespaceId`, and `namespaceUrl`.
- Saving a new selection updates the stored registration state's `accountId`, `namespaceId`, `namespaceName`, and `namespaceUrl`.
- No backend request is sent when changing the default namespace.

Validation and constraints:
- A valid namespace must remain selected so a desktop can still be added as an end root-device.
- Selecting an invalid or incomplete namespace is rejected by local validation.

Data source:
- Root device information comes from the current stored registration state.
- Namespace options come from local stored namespace state. That local namespace state can be refreshed from the account details API response.

Payloads:
- None. Uses local stored namespace state only.