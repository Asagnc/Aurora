# 0.1.30 device evidence

See ../../DEVICE_LINUX_2026-10-22_FINAL.md for exact scope, failures, fixes and remaining work.

GT6SE/RMX3850 Android16 API36, existing FolkPatch Aurora authorization. All final passing logs target0.1.30/code31, except the explicit qualification-owner recovery run before the UTF8 fix; its native stopped proof and SQL recovery code were unchanged afterward.

The archive came from a previously signature/hash-verified official ARM64 image. Public phone download failed; do not count it as a download pass. Initial failures are preserved in failures/. No paid provider call, manager revocation or general performance result is asserted.

Main app and data retained. Temporary test APK and transferred archive files removed. Core161 total/154 pass/7 POSIX-only Windows skips. Debug/testAPK/R8 Release/lint/dual-ABI ELF and16KiB packaging checks passed. CI39784460008 was successful for the preceding commit; new code needs a new CI result.
