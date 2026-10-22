# Agent Linux qualification: 0.1.31

GT6SE/RMX3850, Android16/API36, existing FolkPatch authorization. Data-preserving
`adb install -r`; no main application uninstall or clear-data. Debug APK SHA256:
`b8361f25ad47182318d551bc039d3d37a6c983880fb889641e0123af5572bf53`.
Main application remains installed, test helper uninstalled after qualification.

## Actual device evidence

- Production AgentExecution, fixed per-run tool definitions, real SQLite/native
  history and Root Binder/native Ubuntu execution: three scripted-model cases.
  Batch exit37/output/attached artifact, PTY Unicode input/read/exit23, timeout
  with stopped proof but no successful exit, selected Root image publication.
- Image case creates PNG, guest stores mode0600/UID0, Agent publishes it through
  the selective Root export path. Android decodes the published image and checks
  its pixel; exporting the artifact reproduces the original bytes. Transfer copy
  is absent afterward; guest source remains UID0 and is not recursively chowned.
- Three isolated database tests: first-use generation pin survives selection
  changes, cancelled finalization releases pin, unused session takes no pin,
  ordinary generation rejected, startup drops process-only pin but preserves
  unconfirmed native-run lease. No database schema migration.
- Ordinary regression91 passed after environment-pin implementation. After final
  UI-result projection and original-exception preservation changes, the exact
  final APK passed Agent3 + pin3 + command-log5 =11 targeted checks. These overlap;
  do not add them as unique regression totals or claim a second full ordinary run.

Transport is an in-process deterministic fixture, not an actual provider or
HTTP protocol test. Its fake key never leaves the device and is deleted afterward.
Temporary fixture conversations, guest files, images and terminals are cleaned;
shared Ubuntu generation and existing user data remain.

Initial test failure incorrectly asserted that AgentExecution refreshed its UI
message projection directly. It persists the final message; ChatController then
reloads it. Test now verifies the persisted SQLite message. Both original failures
are preserved in evidence/0.1.31/agent-fixture-initial-assertion.txt, not counted as
passes or presented as application fixes.

## Runtime contracts

First Linux tool use lazily acquires a generation lease for this Agent run under
the existing catalog lock. Later commands/PTY starts receive that exact generation.
Each native dispatch additionally commits its existing run intent/lease, so a PTY
can survive its Agent round and an uncertain native task stays protected. Process
startup releases only leases without native-run intents before task admission;
it neither replays a task nor substitutes startup cleanup for native stopped proof.

Task-card projection uses the guest result, not the helper process exit. Timeout,
interruption and unknown outcome retain distinct presentation. Bounded stdout and
stderr summaries preserve artifact links; full protocol-native tool result stays
in the tool ledger and model history.

## Build and CI

Core167:160 passed,7 POSIX-only skipped on Windows. Fixed official rootfs sample
supplied. Debug/test APK, Release/R8, lint, dual-ABI ELF/16KiB verification and
corresponding-source packaging passed locally.

CI39790590613 failed before Android compilation: Samba official sources timed out
and returned503. Exact-hash cache restore/check/save reduces repeated downloads;
cache miss still requires a successful bounded official download. Valid cached
source and corrupted-cache rejection verified locally. Cache action pinned to
official [v6.1.0](https://github.com/actions/cache/releases/tag/v6.1.0), commit
55cc8345863c7cc4c66a329aec7e433d2d1c52a9. A new CI result is required.

## Outstanding

PRoot full lifecycle, Root metadata GC, external Root-manager revocation, public
phone image download, long-running pressure/power/performance and actual provider
cache tests remain incomplete. This is not an all-device compatibility or
performance-leadership claim. No new paid model calls were made.
