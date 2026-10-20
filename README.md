# Aurora

Aurora is an Android 16+ phone agent harness in development, with ordinary-device capabilities and optional Root-powered system operations.

Application ID: `io.github.asagnc.aurora`.

Current 0.1.9 adds a user-initiated foreground Agent service, run-scoped state, notification stop/return actions and durable task lifecycle records. Switching conversations or recreating the UI no longer owns task cancellation. Process death records interrupted/uncertain outcomes without replaying tools. [Implementation and verification boundaries](docs/BACKGROUND_TASKS.md). New background behavior awaits Android16 ARM64/Root hardware verification; 0.1.8 hardware evidence below remains historical.

Current 0.1.8: approved capsule B UI, contextual ring and compact execution summaries are implemented. Android16 ARM64 realme/FolkPatch passed33 ordinary tests,1 isolated browser recovery and4 authorized Root tests; emulator33+1 and core45 pass. Root execution/controlled ownership/cancellation/timeout/disconnect/reconnect now have hardware evidence. Existing data was updated with install-r; no uninstall/clear-data. [Current UI and device evidence](docs/COMPOSER_B_2026-10-18.md) supersedes historical pending statements below. External manager revocation, arbitrary Root files/mounts, persistent terminal tasks and compiler/plugin toolchains remain unfinished or unverified.

The repository contains a runnable development app: Compose chat, ten curated BYOK provider presets and custom gateways, three streaming protocols, encrypted credentials, SQLite conversations and owned file tools. Supported official Responses routes preserve request prefixes when changing effort through append-only updates. A single-surface composer, anchored usage details, complete light/dark themes and conversation-owned browser Agent tools are implemented. Agents can open/read pages, click/fill/scroll, run synchronous page JavaScript and open/read batches; users can watch the same pages and inspect execution records. Ordinary Android-native command execution is also connected to the agent through a small packaged supervisor, with exit status, bounded stdout/stderr artifacts and cancellation cleanup. Root execution, Linux environments, Skills and phone APK/JNI builds remain in development. Packet capture is planned for Root only; no VPN fallback. No performance leadership or guaranteed cache hit rate is claimed.

- [Current functionality, gaps and priorities](docs/FEATURE_STATUS.md)
- [Native command execution and boundaries](docs/NATIVE_COMMANDS.md)
- [Root command transport and hardware verification gaps](docs/ROOT_COMMANDS.md)
- [Approved design baseline](docs/DESIGN.md)
- [Engineering rules](AGENTS.md)
- [Next-chat handoff and task checklist](docs/PROGRESS.md)
- [Implemented behavior and verification evidence](docs/IMPLEMENTATION.md)
- [Reverse engineering and network tooling scope](docs/REVERSE_ENGINEERING.md)

Build with `./gradlew :core:test :app:assembleDebug :app:assembleRelease :app:lintDebug` using JDK 25, NDK 30.0.16248370, SDK CMake 4.1.2 and SDK `platforms;android-37.0` / Build Tools 36.0.0. Dependencies are pinned; minimum Android remains 16/API 36. On Windows, `./scripts/verify-emulator.ps1` validates only a real emulator and requires at least thirty-nine passing device tests, an isolated browser recovery check and a two-process Agent interruption check without uninstalling packages. See [test boundaries](docs/BUILD.md).

GitHub Actions stores development APKs. Release output is unsigned; debug builds are development previews. Historical Android 16 ARM64 realme verification covered fourteen ordinary tests and one separate-process browser recovery test; current emulator results are listed below. An explicitly authorized FolkPatch read-only Root connect/disconnect/reconnect test also passes on the phone. Formal signing, privileged tools and on-device compiler validation remain pending.

The design covers conversation-owned storage, shared tools, model protocols and caching, batch file tools, an integrated browser, PRoot/chroot execution, and on-device APK/JNI builds. Implementation and performance claims require the verification gates documented in the design.

0.1.6: core 45 checks, emulator 26 ordinary tests and one separate-process recovery test pass. Eight new device cases exercise the real native executable, bounded output, process cleanup and a deterministic agent loop. ARM64 is compiled, not yet run for this native backend; earlier phone/Root evidence above is historical and does not verify new native or privileged operations.

0.1.7 adds a separate agent Root command tool after explicit connection, asynchronous Binder results, bounded sessions, cancellation/close cleanup and service-death handling. Core 45, emulator 33 ordinary tests plus one isolated recovery pass. New cross-process tests run ordinary UID; actual Root execution, ownership restoration and external-manager revocation still await hardware verification. Arbitrary Root files/mounts, background tasks and system API tools remain unfinished. See ROOT_COMMANDS.md; historical paragraphs above retain their original verification scope.
