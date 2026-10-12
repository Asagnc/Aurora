# Aurora

Aurora is an Android 16+ phone agent harness in development, with ordinary-device capabilities and optional Root-powered system operations.

Application ID: `io.github.asagnc.aurora`.

The repository contains a runnable development app: Compose chat, ten curated BYOK provider presets and custom gateways, three streaming protocols, encrypted credentials, SQLite conversations and owned file tools. Supported official Responses routes preserve request prefixes when changing effort through append-only updates. Root, the browser, Linux environments, Skills and phone APK/JNI builds remain in development. No performance leadership or guaranteed cache hit rate is claimed.

- [Approved design baseline](docs/DESIGN.md)
- [Engineering rules](AGENTS.md)
- [Next-chat handoff and task checklist](docs/PROGRESS.md)
- [Implemented behavior and verification evidence](docs/IMPLEMENTATION.md)

Build with `./gradlew :core:test :app:assembleDebug :app:assembleRelease :app:lintDebug` using JDK 25 and SDK `platforms;android-37.0` / Build Tools 36.0.0. Dependencies are pinned; minimum Android remains 16/API 36. With a running emulator, `:app:verifyDevicePersistence` enforces non-empty passing device tests.

GitHub Actions stores development APKs. Release output is unsigned; debug builds are development previews. Eight storage/recovery tests pass on an Android 16 ARM64 realme device and an Android 17 emulator. Formal signing, Root execution and on-device compiler validation remain pending.

The design covers conversation-owned storage, shared tools, model protocols and caching, batch file tools, an integrated browser, PRoot/chroot execution, and on-device APK/JNI builds. Implementation and performance claims require the verification gates documented in the design.
