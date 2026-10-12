# Aurora

Aurora is an Android 16+ phone agent harness in development, with ordinary-device capabilities and optional Root-powered system operations.

Application ID: `io.github.asagnc.aurora`.

The repository contains approved design contracts and a tested Kotlin protocol/file core. The Android application is not yet runnable; no installable release or performance leadership is claimed.

- [Approved design baseline](docs/DESIGN.md)
- [Engineering rules](AGENTS.md)
- [Next-chat handoff and task checklist](docs/PROGRESS.md)
- [Implemented behavior and verification evidence](docs/IMPLEMENTATION.md)

Run core verification with `./gradlew :core:test` using JDK 25. Gradle dependencies are pinned. Android SDK 37 is required for the app build configuration; the application minimum remains Android 16/API 36.

The design covers conversation-owned storage, shared tools, model protocols and caching, batch file tools, an integrated browser, PRoot/chroot execution, and on-device APK/JNI builds. Implementation and performance claims require the verification gates documented in the design.
