# Native guest command entry (2026-10-22)

Status: native entry implemented; application Root Binder launch, persistent SQL run leases and PTY are not connected. Agent executionReady stays false. No new device evidence.

The `aurora_linux_scope --run` entry accepts a generation SHA256, UUID run identity, fixed rootfs/workspace roots, a 1–300000 ms deadline and absolute guest executable plus argv. It reuses private mounts, readonly shared rootfs, per-run writable HOME/tmp/project, pidfd/subreaper and the task-owned cgroup. It changes cwd to /mnt and supplies a clean environment; close_range(CLOEXEC) prevents host descriptor inheritance. stdout/stderr remain guest streams, and normal nonzero exits are returned unchanged.

A Root-owned one-use state directory retains run.json before worker creation. Its generation and run ID are the future SQL lease association; this metadata does not yet create or release a database lease. Reusing the directory fails. Only after cgroup kill/empty, worker/descendant reap, scope removal and journal retirement may completed.json be forced to disk. interrupted distinguishes deadline/owner loss from an ordinary guest exit. A partial completion file is not valid completion. Journal v1 recovery remains unchanged. If both supervisors die, explicit recovery can remove the scope but cannot invent an exit code or completed.json.

Future application integration must bind this directory to a persisted run/lease before dispatch, join or prove the old Root Binder worker dead before recovery, and block conversation deletion while any run is unconfirmed. A generic command callback or an empty state directory alone is insufficient because a dispatched old worker might not have created its scope yet. Retain unknown leases and files; never infer stopped from service disconnection. Root-created writable conversation files also need tracked privileged cleanup before enabling Agent calls.

Verification: existing fixture group7 plus4 command tests: output/status/artifact/durable identity and reuse rejection; timeout with background child; missing executable; both supervisors killed followed by explicit recovery without fabricated completion. Host /bin/sh and matching dynamic libraries exercise a real ELF, not the pinned Ubuntu26.10 image or Android SELinux. Existing cgroup6 and recovery7 tests remain unchanged.

Platform basis: Linux execve closes only CLOEXEC descriptors and uses the guest PT_INTERP for dynamic ELF loading: https://man7.org/linux/man-pages/man2/execve.2.html . No host-shell fallback or inherited host library environment.
