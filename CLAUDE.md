# Yu Flow — Claude Instructions

Follow **[AGENTS.md](./AGENTS.md)** as the system-level engineering contract for this repository.

Summary:

- Simplest solution for the stated need; no speculative layers.
- Vertical slice first; do not break working E2E for unfinished design.
- Modular boundaries; reuse mature libs and existing repo dependencies.
- Durable architecture; copy proven product patterns, don’t invent auth/security models cold.
- **Production OSS:** do **not** apply “delete without migration / no compatibility.” Evolve with Flyway and explicit breaking-change notes when needed.
