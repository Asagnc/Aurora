# Linux PTY application integration

0.1.29/code30, 2026-10-22. Uses the approved SQLite run ledger, Root service,
native PTY socket transport, cgroup scopes, foreground task service and terminal
records. No new dependency, database schema or resident process.

Implementation review rechecked Android's [ParcelFileDescriptor lifecycle](https://developer.android.com/reference/android/os/ParcelFileDescriptor),
[Binder death callbacks](https://developer.android.com/reference/android/os/IBinder.DeathRecipient)
and Kotlin's [NonCancellable cleanup guidance](https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines/-non-cancellable/).

## Implemented

- AIDL appends `openLinuxTerminal` after every existing transaction. Optional
  `linuxTerminalProtocol=1` requires run protocol2 as well; old Root services
  continue ordinary operations but must reconnect to provide this new entry.
- The Root terminal broker retains its FD receipt/activation gate. An optional
  lifecycle opens the scope runner using `--run-pty`, then explicitly recovers
  its scope after the PTY runner has joined. The response carries the existing
  outcome plus `linuxProof` identifying the exact stopped run and generation.
- App `RootTerminalClient` validates the response and durably finishes the run
  ledger on an IO coroutine before completing its exit deferred. Binder callback
  threads do not perform SQLite work. Missing/mismatched proof, invalid state,
  oversized metadata or normal exit inconsistent with guest completion fails.
- `LinuxRunManager.terminal` reserves the run intent and version lease atomically
  before offering the terminal opener. Its final recovery joins both command
  and terminal dispatch for that ID. Binder death or transport exit alone cannot
  release the lease. Failed recovery leaves the existing explicit recovery path.
- Internal `TerminalManager.startLinux` uses the existing global terminal budget,
  OwnedTasks, foreground service, wake-lease policy, VT projection and persisted
  logs. Backend `linux-root` is labelled Linux by the existing terminal page.
  The same read/write/resize/stop/list machinery handles its sessions.
- Native completion adds optional `timedOut`; a guest deadline is displayed as
  timed_out after confirmed cleanup, rather than a normal helper exit. Existing
  completion readers may ignore the new field. Native deadline assertions were
  updated; their new execution result awaits CI.

## Current bounds

Only the Root Linux qualification entry is connected. The UI does not offer a
new Linux launch button and Agent tool definitions still offer Android-native
terminals. Do not expose the new selection as generally ready before actual
Android/Ubuntu execution and recovery qualification. `executionReady=false`.

Linux starts at `/mnt` with its fixed guest environment. Arbitrary host directory
and environment overrides are rejected. Guest timeout is at most270000ms, with
20000ms transport cleanup reserve; idle terminals hold no permanent CPU lock.
Request argv/environment are snapshotted before async admission. As with the
existing run ledger, only one unconfirmed Linux run per conversation is allowed;
the ordinary Android terminal limit remains four per conversation/eight globally.

Run tickets and terminal IDs match, so process-death recovery never replays old
input. Root runtime tombstone/lock metadata GC and PRoot lifecycle remain pending.

## Verification

- Previous exact native integration full CI39780230312 succeeded.
- Core159 total:152 passed,7 POSIX-only skipped on Windows, no failures. Four new
  tests check exact proof/nonzero exit, missing/mismatched proof, fabricated normal
  completion after recovery/interruption, malformed state and metadata budgets.
- Debug/test APK/R8 Release/lint passed locally. Ordinary cross-process Binder
  fixture explicitly rejects the new privileged transaction; it never simulates
  a Root Linux guest. Ordinary Android gate remains88, not device-run this turn.
- Three `RootLinuxTerminalDeviceTest` tests compile: actual guest TTY/Unicode/resize
  and persisted exit; stop/recovery/lease/delete; guest deadline state. All require
  explicit Root and image flags plus a prepared real selected CHROOT generation.
  No Rootfs download or user-conversation replacement occurs in this suite.
- ADB had no connected devices. These three tests, the existing actual guest
  suite, Root revocation and service-death tests remain **unexecuted on Android**.

Review: RootTerminalSessions.open and TerminalManager.startBackend retain the
existing reviewed lifecycle scopes above60lines. FD activation, cancellation,
endpoint join, Linux proof and final record persistence need joint review; no
fragmented helper is used merely to meet a line count. Other new normal functions
stay within the routine size targets.
