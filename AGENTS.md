# Yu Flow — Agent Instructions

You are working in the **yu-flow** open-source repository (production-grade embeddable low-code engine). Follow these engineering principles on every change.

## Engineering principles

1. **Simplest implementation that meets the current requirement.** No speculative abstractions, no extra configuration layers “just in case.”
2. **Ship a thin vertical slice first.** Get one end-to-end path working, then layer features. Never rip out a working path for unfinished complexity.
3. **Keep modules modular; separate concerns.** Controllers, domain services, host SPI, ingress, and UI stay at clear boundaries.
4. **Prefer mature, maintained libraries.** Do not rewrite what a well-known library already solves without a documented reason.
5. **Inventory existing dependencies before adding packages or writing new infra.** Check `pom.xml` / `package.json` and in-repo utilities first.
6. **Make durable architecture decisions.** Reject “temporary for now, swap later” shortcuts when a stable pattern already exists in-repo or in mature products.
7. **Study how mature products solve the same problem.** Prefer proven patterns (gateway authorizers, principal + policy, data scope) over inventing a novel auth model from scratch.

### Intentionally omitted

- **“No backward compatibility / delete obsolete without migration”** is **not** adopted here. This is a shipped OSS/production codebase: prefer clean versioned evolution (Flyway, documented breaking changes). Do not pile silent compatibility shims without an explicit product decision; do not casually break published APIs or DB contracts.

## Repo conventions (short)

- Single edition: all work lands in this repo. The former `yu-flow-pro` repo is no longer maintained (since 2026-10-19); do not sync to it. Commit messages in Chinese when committing.
- Backend: `org.yu.flow.module.*`, JPA + `LocalDateTime`, skills under `.agents/skills/`.
- Frontend: Umi/React/Amis; match existing patterns.
- Security defaults are fail-closed; do not weaken ingress/RBAC without an explicit request.
