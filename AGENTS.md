# Aurora engineering rules

Read `docs/NEXT_STEPS.md` for current status before treating historical entries as unfinished work. Read `docs/DESIGN.md`, `docs/PROGRESS.md` and `docs/IMPLEMENTATION.md` before changing application behavior. Design contracts are approved; implementation and device evidence have separate status.

## Product baseline

- Name: Aurora. Application ID: `io.github.asagnc.aurora`.
- Android 16+ only; minimum SDK 36. Do not add older-Android compatibility paths.
- Ordinary features work without Root; privileged features require Root authorization. Do not require Shizuku.
- Packet capture is Root-only by the user's 2026-10-15 decision; do not implement a VPN capture fallback. Prefer native execution and do not silently add expensive substitutes for unavailable privileges.
- Current stable dependencies and tools only. Preserve reproducible exact versions and verify compatibility before updating.
- Only current available model lines belong in curated presets; model availability and endpoint capabilities need current primary sources.
- Do not claim universal website access, guaranteed cache hits, or performance leadership without evidence.

## Before implementation

- Inspect the actual files, callers, dependency versions and verification environment.
- Research protocol and platform behavior in current official documentation or source.
- Confirm newly introduced architectural choices before implementing them.
- State measurable success criteria. Do not silently reduce the requested scope.
- Keep credentials out of files, logs, URLs and commits.

## Code and concurrency

- Normal hand-written files target 300 lines; review responsibilities and document justified exceptions.
- Ordinary functions target 30–40 lines; review functions above 60 lines.
- Ordinary functions target no more than five parameters and three nesting levels; declarative UI and data declarations have appropriate exceptions.
- Use explicit domain names and keep related declarations together.
- Preserve cancellation and structured errors. Do not turn failed or partial operations into success.
- Keep blocking I/O and expensive parsing outside the main thread.
- Parallelize only independent reads/computation. Serialize mutations of the same file, page, GUI state or terminal session.
- Preserve completed protocol-native state under its documented scope.

## Storage and lifecycle

- Preserve conversation/shared-read/shared-write ownership.
- Never follow a conversation link or mount into shared or external data during deletion.
- Coordinate task cancellation, descriptor closure, browser profile deletion, mount cleanup and database/file deletion with recovery records.
- Pin plugin and dependency versions during a run; installation publishes a verified version atomically where the backend supports it.
- State actual guarantees for multi-file edits and non-filesystem document providers.

## Verification and delivery

- Test meaningful failure and recovery paths for runtime, protocols, file mutations and installations.
- Validate Android and ARM64 behavior on a device; report unverified Root and native-tool behavior plainly.
- Measure performance and cache behavior with reproducible workloads and real provider usage.
- Review comments when behavior changes; keep comments about current invariants, not conversation history.
- Distinguish specifications, implemented functionality and verified results in delivery reports.
