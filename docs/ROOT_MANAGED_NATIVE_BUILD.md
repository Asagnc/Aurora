# Root managed guard native build

The guard is Aurora-owned C code and uses the Android system libc/libdl only.
It does not link Frida Core, include a Java bridge, or require a Linux image.
Packaging the guard does not make the Frida backend ready.

`app/src/main/cpp/managed/sources.txt` is the explicit source inventory shared by
CMake, host qualification and the standalone ARM64 audit builder. No glob adds
the separate Frida SDK worker to this target.

Android builds use the existing NDK30/API36, ARM64 and x86_64 pipelines, with
stack protection, RELRO/NOW, non-executable stack and 16KiB load alignment.
The APK name is `libaurora_managed_guard.so`; it is a PIE executable, following
the same installed native-library layout as the other Aurora helpers.

For an independent ARM64 build, from this repository or the corresponding
source archive root:

```
python scripts/build-managed-guard.py --ndk /path/to/ndk/30.0.16248370 --directory /path/to/output
```

This builder records selected source/header SHA256, actual binary size, system
dependencies and ELF headers. It does not package or execute the result.
`scripts/verify-linux-native.py` checks the actual APK payload for both ABIs;
`scripts/package-linux-sources.py` includes the guard inventory, build adapter
and sources alongside the existing native source bundle.
CI also extracts the selected source archive into a temporary directory, checks
the manifest, build scripts and source/header hashes before compiling, and
requires the offline rebuild to reproduce the standalone qualification binary.

`--version` is a metadata-only loader probe usable without Root. Task run and
recovery still require UID0 and qualified cgroup/kernel behavior. Their final
Android/SELinux qualification is pending; host execution is separate evidence.
