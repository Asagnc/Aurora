# Root command transport — 0.1.8

Status: implemented over approved libsu6.0.0 RootService/Binder. On2026-10-18, Android16 ARM64 realme/FolkPatch passed4 authorized Root tests: UID0 execution, controlled artifact ownership/readability, direct cancellation/timeout, disconnect cleanup/reconnect and read-only handshake. Ordinary-UID cross-process tests also pass on hardware and emulator. External manager revocation and force-killing the actual Root service remain unverified. Evidence: COMPOSER_B_2026-10-18.md and evidence/0.1.8/phone-root.txt.

## Behavior

- User explicitly connects Root; client checks UID 0, API and command protocol. No agent-triggered permission request.
- `command_execute` stays ordinary UID. Separate `command_execute_root` appears only when a verified connection exists at run construction. Definitions remain fixed for that run. Disconnect invalidates its endpoint; no silent fallback, reconnect or replay.
- Every production Binder method checks caller app UID and service UID 0. Service accepts a canonical conversation UUID, derives its private path and rejects symbolic-link space components. No caller-selected absolute root or artifact-owner UID crosses Binder.
- Start schedules work without occupying a Binder worker until completion. A one-way callback supplies results and a client-death token. Session admits at most two commands, rejects duplicate active transport IDs and performs no transport retries. Existing durable tool ledger handles application-level duplicate IDs.
- Cancellation sends `cancelAndWait`. Explicit disconnect closes admission and cancels/joins jobs before unbinding. Callback Binder death cancels its command; service destruction closes all jobs. Service Binder death returns `command_outcome_uncertain`; inspect side effects before retrying.
- Request budget is 64 KiB parcelled; Root preview is at most 8 KiB per stream and stored output at most 8 MiB per stream. The existing packaged native supervisor handles literal argv, timeouts, group cleanup and result validation. No per-command extra shell or polling daemon.

## Ownership and lifecycle

Root runner gives controlled `runs`/UUID directory and supervisor log to the app UID before spawning. Native helper derives ownership from its opened directory descriptor, creates artifacts using `O_EXCL|O_NOFOLLOW`, and applies ownership through new file descriptors. The Root-only ownership path is now verified on Android16 hardware for completion/cancellation/disconnect. Abrupt actual Root-service death remains a separate pending hardware case. It never recursively traverses arbitrary output or mounts.

Arbitrary Root commands can create root-owned directories, mounts, credentials or sessions and modify external data. They are not sandboxed. Nested root-owned directories/mounts may prevent ordinary conversation deletion; deletion reports failure/recovery instead of silently deleting outside data. Root-aware installation/mount cleanup remains unfinished. Do not recursively chown arbitrary mount trees as a workaround.

The Java thread that starts the native supervisor now stays in its blocking wait until exit. Cancellation interrupts the wait and performs TERM/wait cleanup before returning the thread to its pool. `PR_SET_PDEATHSIG` follows the **creating thread**, not merely the process. Independent daemon sessions, credential changes and uninterruptible kernel waits remain limitations.

Root-manager revocation has no portable Android callback and may leave an existing UID-0 process alive. Aurora observes Binder death/connection loss and provides explicit disconnect; immediate external revocation detection is not claimed. Manager-specific tests and direct system API tools remain pending.

## Verification

Seven ordinary protocol cases use an unexported **debug-only ordinary-UID service in another process**: literal argv/native output, cancellation cleanup, close/rejection, wire budgets, callback-death cancellation, session bounds/duplicate IDs, service-kill/native parent-death cleanup. Fixture is absent from release and does not simulate verified Root.

Three `RootCommandDeviceTest` cases and RootConnectionTest are RootRequired, require auroraRootTest=true and stay excluded from ordinary verification. All4 passed on Android16 ARM64 in0.1.8. Ordinary33 and isolated recovery1 also passed, including actual ARM64 native execution and ordinary-UID service death. They do not establish universal ROM behavior, external manager revocation or containment of arbitrary Root daemons/mounts.

## Primary references checked 2026-10-16

- [Pinned libsu RootService lifecycle and non-daemon behavior](https://github.com/topjohnwu/libsu/blob/6.0.0/service/src/main/java/com/topjohnwu/superuser/ipc/RootService.java)
- [Android Binder death notification](https://developer.android.com/reference/android/os/IBinder.DeathRecipient)
- [Shared Binder transaction buffer](https://developer.android.com/reference/android/os/TransactionTooLargeException)
- [Linux parent-death signal semantics and credential caveats](https://man7.org/linux/man-pages/man2/PR_SET_PDEATHSIG.2const.html)

References establish platform contracts, not measured performance or device validation.
