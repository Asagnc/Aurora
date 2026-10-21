# 0.1.19 local build evidence — 2026-10-22

- build.txt: core:test, Debug, R8 Release, Android test APK and lintDebug passed.
  Core suite remains 129; Android tests were compiled, not run.
- debug-elf.txt / release-elf.txt: actual APK payloads for ARM64 and x86_64,
  ELF/16KiB/dependency checks, byte sizes and SHA256 per packaged Linux binary.
- standalone-build.txt: independent API36 ARM64 native build with the same
  original pinned sources, without the application Gradle build.
- Corresponding source ZIP generated successfully (about 1.4 MiB); delivered
  with CI development artifacts, not checked into Git as a redundant binary.

No new Android device, guest execution, Root, PTY Linux, package installation,
provider, battery or performance measurements were performed. The new ordinary
Android PRoot --version smoke test is awaiting execution. Fixed probe success
does not set executionReady, and has not yet been observed on Android.
Remaining upstream optional extension and ARM64 inline-assembly compiler
warnings are not hidden or presented as runtime qualification.

Previous metadata fix 245d64a passed full CI39638040603, including 129 Linux
core tests and official image extraction. That is prior-commit evidence.
