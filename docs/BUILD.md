# Build environment

Local builds use the installed JDK 25 and SDK. Android CI bootstraps its own SDK because the hosted runner did not provide sdkmanager on PATH.

The bootstrap archive is Google's stable command-line tools 23.0, channel-0, selected from the [official repository metadata](https://dl.google.com/android/repository/repository2-3.xml) on 2026-10-12. The exact archive is commandlinetools-linux-16111833_latest.zip. Its official SHA-1 is e025545c62a8e64c7559119566a569fb1dec5f60; a local download matched that checksum. The verified download's SHA-256, pinned in the bootstrap script, is 0877a1d048fe4a24efe2eff536ca4223f7adeb58648bb81909d33c446918cfa8.

CI installs only stable-channel platforms;android-37.0 and build-tools;36.0.0. The SDK lives in the runner's temporary directory. The script preserves sdkmanager's real license exit status rather than treating the yes pipe's SIGPIPE as a failed SDK installation.

Core and Android builds run on GitHub. Device persistence verification currently runs on the local Android 17 emulator, not CI or the Android 16 phone. Development APKs from different machines may have different debug signatures; formal release signing remains pending.
