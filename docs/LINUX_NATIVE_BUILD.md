# Aurora packaged Linux tools

The corresponding-source ZIP also contains the separately built packet tools;
see PACKET_NATIVE_BUILD.md for pinned tcpdump/libpcap and rebuild instructions.

Source lock and Android build adapter: `app/src/main/cpp/linux/Sources.cmake`.
PRoot 5.1.107.98 (GPL-2.0-or-later), talloc 2.5.0 (LGPL-3.0-or-later),
libandroid-shmem 0.7 (BSD-3-Clause). Upstream notices and license texts ship
in APK assets/licenses/linux. They remain independent native programs/libraries;
Aurora invokes PRoot as a process, and PRoot dynamically links talloc.

## Rebuild

Use Android NDK 30.0.16248370 and CMake 4.1.2. Minimum API is 36.
For the complete application use the repository Gradle wrapper. For just these
native programs, configure `app/src/main/cpp/linux` with the NDK toolchain:

```sh
cmake -S app/src/main/cpp/linux -B build-linux \
  -DCMAKE_TOOLCHAIN_FILE="$ANDROID_NDK_HOME/build/cmake/android.toolchain.cmake" \
  -DANDROID_ABI=arm64-v8a -DANDROID_PLATFORM=android-36 \
  -DCMAKE_BUILD_TYPE=RelWithDebInfo
cmake --build build-linux --target aurora_proot
cmake --build build-linux --target aurora_linux_namespace
```

Repeat with x86_64 for that architecture. Only same-architecture 64-bit guests
are supported; a 32-bit loader is not distributed. API 23 workarounds, older
PRoot binaries and fixed loader addresses from other versions are not used.
The loader address comes directly from the pinned upstream architecture header.
Its ARM64 pokedata offset comes from actual linked symbols.

Corresponding-source ZIPs contain the exact upstream trees and Aurora's adapter.
To rebuild offline, add these CMake definitions, using absolute paths:

```text
-DFETCHCONTENT_SOURCE_DIR_AURORA_PROOT_SOURCE=/path/upstream/aurora_proot_source-src
-DFETCHCONTENT_SOURCE_DIR_AURORA_TALLOC_SOURCE=/path/upstream/aurora_talloc_source-src
-DFETCHCONTENT_SOURCE_DIR_AURORA_SHMEM_SOURCE=/path/upstream/aurora_shmem_source-src
```

## Build adaptations

- talloc uses an explicit Bionic API36+ portability header and shared linkage.
  Its getauxval-based randomized allocation magic remains enabled.
- PRoot's process_vm/seccomp headers are compile/link checked, not run during
  cross compilation. These macros do not prove device/kernel capability.
- The ashmem extension gets its missing string.h declaration without disabling
  implicit-function-declaration errors for the rest of the program.
- The SysV pending-mapping impossible state calls abort even under NDEBUG,
  preventing a missing return from becoming Release undefined behavior.
- The generated PRoot trace-event source requires PTRACE_O_EXITKILL in the
  default ptrace options, including the seccomp fallback. SIGKILL cannot run
  upstream signal handlers; this asks the kernel to kill tracees on tracer
  death. Rejected options fail startup. The upstream tree stays unchanged and
  an unexpected patch location fails configuration. See the Linux
  [ptrace contract](https://man7.org/linux/man-pages/man2/ptrace.2.html).
  The initial child also requires PR_SET_PDEATHSIG(SIGKILL) and checks its
  parent against the PID captured before fork, covering tracer death before
  the first ptrace options are installed. Parent-death signals do not survive
  fork, so this guard complements rather than replaces inherited EXITKILL;
  see [PR_SET_PDEATHSIG](https://man7.org/linux/man-pages/man2/PR_SET_PDEATHSIG.2const.html).
  This is only one lifecycle prerequisite: descendant teardown, startup races,
  Android qualification and durable recovery still need verification before
  enabling ordinary-UID Linux execution.

- ARM64 loader linking explicitly retains pokedata_workaround; otherwise
  Release section garbage collection can discard the tracer's external entry.
- libandroid-shmem is built from a checked derived copy. Key files use the
  supplied PROOT_TMP_DIR; absent/oversized paths and non-EEXIST symlink failures
  return errors instead of retrying forever. Upstream's original source is
  retained unchanged in the source bundle. Further concurrency and crash
  recovery qualification remains required before enabling SysV IPC for users.
- The upstream optional port-switch extension is compiled, but is not enabled
  by Aurora. Its warnings and the upstream helper mktemp path are not treated
  as verified behavior; device runtime qualification remains pending.

## Verification boundary

`scripts/verify-ptrace-exitkill.py` exercises the Linux kernel prerequisite
with an independent native tracer: child and two setsid generations, tracer
SIGKILL with/without the seccomp option, tracer SIGTERM, and controlled parent
death before/after the startup guard. It uses a subreaper to reap its fixtures
and does not require Root. Passing it does not verify upstream PRoot syscall
translation, Android SELinux, hardlinks or the application recovery protocol.

CI prefetches the pinned talloc source once using `scripts/prefetch-native-source.sh`
with HTTPS/HTTP1.1 and bounded retries, avoiding an observed Samba HTTP2
REFUSED_STREAM failure. It exports `AURORA_TALLOC_ARCHIVE`; Sources.cmake accepts
only an existing absolute archive and retains the same URL_HASH check for every
ABI/build type. No insecure transport, alternate source or unverified binary is
substituted. Both download.samba.org/pub and www.samba.org/ftp are official
archive endpoints; only a transport failure tries the second. Hash mismatch
fails immediately. Without that variable, ordinary builds retain the official URL.

`scripts/verify-linux-native.py APK` checks both ABI payloads, 16KiB ELF load
alignment, non-executable stacks, absence of writable/executable segments,
freestanding loader structure and packaged dynamic dependencies.
`LinuxNativePackageTest` checks ordinary-UID PRoot --version on Android; compiling
this test does not count as running it or successfully executing a guest.
PRoot packaging does not mark the Linux environment executionReady.
