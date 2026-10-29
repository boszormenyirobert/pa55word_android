# Security

Purpose: protect application access and private-key operations with strong biometrics.

Actions:
- Require Android 11 (API 30) or newer and an enrolled strong biometric.
- Create or validate the hardware-backed device RSA key before displaying the biometric prompt.
- Authenticate using `BIOMETRIC_STRONG` only. Device PIN, pattern, password, weak face recognition, and custom app PIN are not fallbacks.
- After biometric success, sign and verify a local random challenge before unlocking the app.
- On success open `Home` or `Registration`, depending on stored registration state.
- Lock after 300 seconds or when the application enters the background. Returning to the foreground requires biometric authentication again.
- While unlocked, tapping Home's biometric countdown starts the same strong-biometric
	prompt and hardware-key proof used for initial authentication. Only a successful
	proof restarts the 300-second window; the current view and application state remain
	unchanged. Starting, cancelling, or failing the prompt does not renew access.

Data source:
- Android Keystore stores one non-exportable, hardware-backed RSA-2048 keypair for RSA-PSS request signatures and RSA-OAEP DEK decryption.
- Key generation requires `AUTH_BIOMETRIC_STRONG` with a 300-second authentication validity window. Hardware enforcement, authentication type, validity window, and the existing cryptographic profile are checked when loading the key.
- Application access state is in memory only and uses a monotonic clock. Private-key operations also require foreground, unlocked application state.
- The Keystore independently rejects operations outside its authentication window. Application background locking does not revoke an already-issued hardware authentication token.
- Reads the presence of stored registration state to decide whether success routes to `Home` or `Registration`.
- Signed FCM synchronization and authentication-status polling wait until the application is unlocked. Polling stops when it locks.
- Expired authentication triggers `SECURITY_BIOMETRIC_REQUIRED`; missing, invalidated, or noncompliant keys trigger `SECURITY_KEY_REENROLLMENT_REQUIRED`. Authentication failures must not be hidden as OAEP compatibility failures.

Payloads:
- Unlocking uses a local random challenge only. No backend session, new public-key field, request-signing algorithm, or DEK protocol is introduced.

Development enrollment:
- Existing unauthenticated keys cannot be upgraded in place and are rejected, not silently deleted or replaced.
- Existing development installations need explicitly authorized clean enrollment or a separately designed replacement flow. Replacing only the local key while keeping a registered device identity would invalidate signatures and stored DEKs.
- Do not uninstall the application or clear its data as part of testing. The isolated test activity uses a unique test-key alias and deletes only that key.

Physical-device verification:
- Samsung SM-A127F, Android 13 / API 33: strong fingerprint authentication supports hardware-enforced, biometric-only timed RSA signing and OAEP decryption.
- The isolated probe verified denied signing and decryption before authentication, successful RSA-PSS verification and OAEP roundtrip after fingerprint authentication, and denied operations after a shortened 10-second test window expired.
- The test key was removed. The installed application's registration and keys were not modified.