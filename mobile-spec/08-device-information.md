# Devices

Purpose: provide access to current phone information and server-backed device removal.

Actions:
- Open `Device information` to show the current phone root-device identity and select the default namespace.
- Open `Remove devices` to load devices from the active namespace and remove a selected mobile or desktop device.
- Return to `Settings`.

Remove devices behavior:
- The screen loads devices from every server available in `Accounts` with `GET {namespaceUrl}/api/v1/account/devices/{accountId}/{mobileDeviceId}?nonce={nonce}` and a signature over the account, mobile-device, and nonce payload.
- Request targets include the primary registration, stored desktop connections, and stored mobile registrations for the current phone. Mobile-only servers remain visible even without desktop connections; each server/account pair is queried once.
- Each device shows its source server. Removing it sends `DELETE {namespaceUrl}/api/v1/account/devices/mobile` with a signature over the complete body and exactly one target property: `targetMobileDeviceId` or `targetDesktopDeviceId`.
- The device list reloads after a successful deletion while the phone remains registered.
- Removing the current mobile device refreshes all known account registrations. Only the removed server/account connection is pruned; if it was the default, another verified mobile registration becomes the default with its own encrypted DEK. The app returns to registration only when no valid mobile registration remains. Refresh errors are reported without blindly clearing the remaining registrations.
- API response errors are displayed; an unsuccessful or otherwise invalid response reports `The account device could not be deleted.` when no backend error is provided.

## Device Information

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
- Return to `Devices`.

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