# Packaged Linux native tools

- PRoot 5.1.107.98, termux/proot, commit e2d843ee6dab076ea0ed1ffd5d0ea12232185499.
  Copyright STMicroelectronics and the contributors named in proot-gpl-2.txt
  and the upstream files. GPL-2.0-or-later. Runs as a separate executable.
- talloc 2.5.0, Samba project. Copyright Andrew Tridgell, Stefan Metzmacher
  and contributors. LGPL-3.0-or-later. Shared library, not statically linked.
  See talloc-lgpl-3.txt and gpl-3.txt.
- libandroid-shmem 0.7, Termux contributors. BSD-3-Clause; see shmem-bsd-3.txt.

Exact source URLs, SHA256 digests and build adaptations are in the public
Aurora repository's app/src/main/cpp/linux directory:
https://github.com/Asagnc/Aurora

Development build artifacts include a corresponding-source ZIP alongside
the APK. It includes the upstream sources and the Android API36+ adapter,
including the checked libandroid-shmem temporary-path and failure-loop changes.
Standalone rebuild instructions are in docs/LINUX_NATIVE_BUILD.md.
These components do not require an external Termux application.
