# 0.1.29 local verification — 2026-10-22

- Core159 total,152 passed,7 POSIX-only skipped on Windows; fixed ARM64 sample configured.
- Debug, Android test APK, R8 Release and lintDebug passed in37s.
- Debug/Release dual-ABI Linux ELF dependency and16KiB checks passed.
- Exact corresponding native source bundle regenerated successfully.
- Three new actual Root Linux PTY tests compile only, not device-run.
- Existing ordinary Binder fixture now explicitly refuses privileged Linux terminal open.
- ADB returned no connected devices; no phone update or Root test was attempted.
- Agent Linux execution remains disabled pending qualification. Native timedOut metadata host execution awaits this commit's CI.
- Prior full CI39780230312 succeeded for the previous commit; it is not evidence for these application changes.
