# 0.1.21 — Android16 ARM64 small-scope qualification

2026-10-22. Scope and conclusions: ../../DEVICE_LINUX_2026-10-22.md.

- phone-final-six.txt: final Debug payload, six actual device tests passed,
  no skips. Three ordinary tests and three explicit Root tests.
- phone-initial-package.txt: initial two startup/refusal tests, not added
  again to the final six count.
- phone-initial-filestore-failure.txt: real Android Files.getFileStore
  SecurityException, subsequently fixed using StatFs.
- phone-hardlink-failure.txt: ordinary-UID rootfs extraction failure after
  that space-query fix. Failed installation was not published. The updated
  negative capability test now fails early before network access.
- phone-pid-namespace-unavailable.txt: original mandatory-PID probe failure,
  real UID0, errno2. User approved task cgroup revision; this is not Root
  authorization loss or a passing PID namespace result.
- build.txt: core test, Debug, R8 Release, Android test APK and lint passed.
- test-final-build.txt: final capability-precondition test compilation/lint.
- debug-elf.txt and release-elf.txt: exact packaged native payload hashes,
  dependencies and 16KiB checks for both ABIs.

Aurora was updated with data preserved, and opened afterward. Only its
instrumentation helper package was uninstalled after tests. No system-wide
SELinux, scheduling, freezing or Root-manager settings were changed. Cgroup
tests created and removed only their own unique scope under the caller's
current cgroup. Event subscription/pidfd/Root install/chroot remain pending.
