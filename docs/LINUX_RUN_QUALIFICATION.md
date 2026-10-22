# Root Linux runtime qualification

2026-10-22: `RootLinuxRunDeviceTest` compiles in the Android test APK. It has
**not run on Android**. Previous full CI 39755532891 succeeded; that evidence
does not qualify an actual Ubuntu guest or these new tests.

## Preconditions

- Update Aurora and its test APK without uninstalling the application.
- Explicitly connect a current Root service with run protocol2 and file protocol1.
- Prepare and select the real same-architecture CHROOT generation using the
  existing installer. This suite never downloads a rootfs or replaces it with
  a host shell. Missing generation/capability fails the suite, rather than skips.
- Keep existing user conversations untouched. Every test creates its own
  conversation and deletes only that owner after its process barrier permits it.
  Failed recovery retains the lease and owner for investigation.

Run only after informing the user about the Root device test:

```powershell
adb -s DEVICE shell am instrument -w `
  -e auroraRootTest true -e auroraLinuxImageTest true `
  -e class io.github.asagnc.aurora.linux.RootLinuxRunDeviceTest `
  io.github.asagnc.aurora.test/androidx.test.runner.AndroidJUnitRunner
```

Require `OK (3 tests)` with no instrumentation failure, crash, assumption skip,
or unresolved cleanup error. Preserve output as device evidence; compilation
alone is not a pass. No model API or external workload is involved.

## Assertions

1. Actual `/bin/sh` starts inside the installed guest at `/mnt`, writes a0600
   Unicode file, exits37 and returns a completed record plus stopped proof.
   Root batch reads continue across a Chinese UTF-8 boundary without losing the
   emoji. Selective export produces readable App-owned bytes. A versioned edit
   succeeds, stale retry is rejected, then conversation deletion removes files.
2. A shell and background writer time out; stopped proof and lease release are
   required. File size and SHA-256 remain unchanged after an observation interval.
3. Cancellation waits for an actually observable writer before cancelling.
   Editing an active workspace is refused. Cancellation joins recovery, clears
   the lease and leaves the writer's output unchanged afterward.

The short delayed observations are test assertions, not production polling.
They supplement the native cgroup empty/reap proof; they do not prove every
possible child-process behavior. This suite does not test Root revocation,
Binder/service death, Linux PTY, ordinary PRoot guest execution, selected image
decoding or universal Agent Linux readiness. Those remain separate work.
