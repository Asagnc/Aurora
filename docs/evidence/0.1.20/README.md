# 0.1.20 local evidence — 2026-10-22

- build.txt: core:test, Debug, R8 Release, Android test APK and lint passed.
  Core remains 129; no core implementation changed in this revision.
- debug-elf.txt / release-elf.txt: actual ARM64/x86_64 APK payloads including
  the new Root namespace helper; ELF64, load permissions, 16KiB alignment,
  freestanding PRoot loader and packaged dependencies checked.
- Corresponding native source bundle generated successfully for CI delivery.
- Added Linux host Root namespace suite: four tests, executed by the CI gate.
  Host results must be read from the matching workflow run; this Windows host
  has no installed WSL distribution and did not run namespace syscalls.
- New Android ordinary rejection, Root qualification/timeout and explicit
  image-install/PRoot tests compile. No new device result is claimed here.
  Ordinary gate is 67; image tests require auroraLinuxImageTest=true and are
  excluded from ordinary runs to avoid implicit downloads.

This is a fixed qualification workload, not a chroot/PTY implementation or a
performance comparison. Root Linux executionReady is still false. Kernel
namespace teardown on a Linux CI host is not Android16/SELinux acceptance.
