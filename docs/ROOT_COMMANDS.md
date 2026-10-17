# Root command transport — 0.1.7

Status: implemented over approved libsu 6.0.0 RootService/Binder. Ordinary-UID cross-process tests pass on the emulator. New privileged execution, artifact ownership, Root disconnect/reconnect and manager revocation **have not yet been verified on hardware**. Earlier FolkPatch evidence only establishes the read-only handshake.

## Behavior

- User explicitly connects Root; client checks UID 0, API and command protocol. No agent-triggered permission request.
- `command_execute` stays ordinary UID. Separate `command_execute_root` appears only when a verified connection exists at run construction. Definitions remain fixed for that run. Disconnect invalidates its endpoint; no silent fallback, reconnect or replay.
- Every production Binder method checks caller app UID and service UID 0. Service accepts a canonical conversation UUID, derives its private path and rejects symbolic-link space components. No caller-selected absolute root or artifact-owner UID crosses Binder.
- Start schedules work without occupying a Binder worker until completion. A one-way callback supplies results and a client-death token. Session admits at most two commands, rejects duplicate active transport IDs and performs no transport retries. Existing durable tool ledger handles application-level duplicate IDs.
- Cancellation sends `cancelAndWait`. Explicit disconnect closes admission and cancels/joins jobs before unbinding. Callback Binder death cancels its command; service destruction closes all jobs. Service Binder death returns `command_outcome_uncertain`; inspect side effects before retrying.
- Request budget is 64 KiB parcelled; Root preview is at most 8 KiB per stream and stored output at most 8 MiB per stream. The existing packaged native supervisor handles literal argv, timeouts, group cleanup and result validation. No per-command extra shell or polling daemon.

## Ownership and lifecycle

Root runner gives controlled `runs`/UUID directory and supervisor log to the app UID before spawning. Native helper derives ownership from its opened directory descriptor, creates artifacts using `O_EXCL|O_NOFOLLOW`, and applies ownership through new file descriptors. Stdout, stderr and atomic result record are intended to remain app-readable after completion/cancellation/service exit. The actual Root-only `fchown` path still requires hardware verification; it never recursively traverses arbitrary output or mounts.

Arbitrary Root commands can create root-owned directories, mounts, credentials or sessions and modify external data. They are not sandboxed. Nested root-owned directories/mounts may prevent ordinary conversation deletion; deletion reports failure/recovery instead of silently deleting outside data. Root-aware installation/mount cleanup remains unfinished. Do not recursively chown arbitrary mount trees as a workaround.

The Java thread that starts the native supervisor now stays in its blocking wait until exit. Cancellation interrupts the wait and performs TERM/wait cleanup before returning the thread to its pool. `PR_SET_PDEATHSIG` follows the **creating thread**, not merely the process. Independent daemon sessions, credential changes and uninterruptible kernel waits remain limitations.

Root-manager revocation has no portable Android callback and may leave an existing UID-0 process alive. Aurora observes Binder death/connection loss and provides explicit disconnect; immediate external revocation detection is not claimed. Manager-specific tests and direct system API tools remain pending.

## Verification

Seven ordinary protocol cases use an unexported **debug-only ordinary-UID service in another process**: literal argv/native output, cancellation cleanup, close/rejection, wire budgets, callback-death cancellation, session bounds/duplicate IDs, service-kill/native parent-death cleanup. Fixture is absent from release and does not simulate verified Root.

Two `RootCommandDeviceTest` cases await the later authorized phone run: UID 0 plus app-readable/owned artifacts; disconnect/cleanup/reconnect. Both are `RootRequired`, require `auroraRootTest=true` and are excluded from ordinary verification. They have **not run**. ARM64 compiles but the new native backend still needs Android 16 runtime tests.

## Primary references checked 2026-10-16

- [Pinned libsu RootService lifecycle and non-daemon behavior](https://github.com/topjohnwu/libsu/blob/6.0.0/service/src/main/java/com/topjohnwu/superuser/ipc/RootService.java)
- [Android Binder death notification](https://developer.android.com/reference/android/os/IBinder.DeathRecipient)
- [Shared Binder transaction buffer](https://developer.android.com/reference/android/os/TransactionTooLargeException)
- [Linux parent-death signal semantics and credential caveats](https://man7.org/linux/man-pages/man2/PR_SET_PDEATHSIG.2const.html)

References establish platform contracts, not measured performance or device validation.
