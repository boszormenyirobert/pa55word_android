# Loading

Purpose: transitional view while authentication or registration state is being resolved.

Actions:
- No user action.
- Routes automatically to `Security`, `Registration`, or `Home`.

Data source:
- Reads the in-memory bootstrap result derived from local authentication state and stored registration state.
- Does not start any backend request.

Payloads:
- None.