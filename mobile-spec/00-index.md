# Android secure registration spec index

This folder contains the split version of the Android registration specification.

Reading order:

1. [01-scope-and-decisions.md](01-scope-and-decisions.md)
2. [02-backend-contract.md](02-backend-contract.md)
3. [03-security-and-crypto.md](03-security-and-crypto.md)
4. [04-app-flow-and-storage.md](04-app-flow-and-storage.md)
5. [05-implementation-backlog.md](05-implementation-backlog.md)

Use this split specification instead of the monolithic [mobile.md](../mobile.md) when prompting Copilot.

Document roles:

- `01-scope-and-decisions.md`: fixed product scope, non-goals, and current implementation constraints.
- `02-backend-contract.md`: request/response DTOs and the exact backend endpoint currently available.
- `03-security-and-crypto.md`: keystore, key encoding, DEK handling, signature generation, and platform constraints.
- `04-app-flow-and-storage.md`: screen flow, app lifecycle, state transitions, and persistence.
- `05-implementation-backlog.md`: implementation order for Copilot with clear package-level milestones.