# Build environment

Local builds use the installed JDK 25 and SDK. Android CI bootstraps its own SDK because the hosted runner did not provide sdkmanager on PATH.

The bootstrap archive is Google's stable command-line tools 23.0, channel-0, selected from the [official repository metadata](https://dl.google.com/android/repository/repository2-3.xml) on 2026-10-12. The exact archive is commandlinetools-linux-16111833_latest.zip. Its official SHA-1 is e025545c62a8e64c7559119566a569fb1dec5f60; a local download matched that checksum. The verified download's SHA-256, pinned in the bootstrap script, is 0877a1d048fe4a24efe2eff536ca4223f7adeb58648bb81909d33c446918cfa8.

CI installs only stable-channel platforms;android-37.0 and build-tools;36.0.0. The SDK lives in the runner's temporary directory. The script preserves sdkmanager's real license exit status rather than treating the yes pipe's SIGPIPE as a failed SDK installation.

Core and Android builds run on GitHub. Android 16 ARM64 hardware passed eight storage tests before the UI redesign; current UI regression runs on the local Android 17 emulator. Hardware testing of the redesign is deferred until the implementation is ready, as requested. Development APKs from different machines may have different debug signatures; formal release signing remains pending.

For ordinary local device checks on Windows, set JAVA_HOME to a compatible JDK 25 and run `./scripts/verify-emulator.ps1 -Serial emulator-5554`. The script checks both the serial format and ro.kernel.qemu, builds the test packages, updates only that emulator with install -r, excludes RootRequired tests and requires at least nine passing tests. It never uninstalls packages or addresses a physical device. Its report is build/reports/emulator-check.txt.

Do not run connectedDebugAndroidTest against an attached everyday phone as routine verification: the Gradle runner may uninstall the application after testing. RootConnectionTest requires an explicitly authorized device and the auroraRootTest=true argument; it is excluded from ordinary Gradle verification using the runner's notAnnotation filter. Root behavior remains unverified until that dedicated test succeeds on hardware.
