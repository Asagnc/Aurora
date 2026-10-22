# Root Linux runtime metadata collection proposal

Status: proposed, awaiting confirmation. Existing runtime journals and retirement
barriers are retained until this protocol is implemented and qualified.

## Problem and comparison

Each Linux run currently retains its Root-owned ticket, completion/retirement
markers and native state directory after the SQL run lease is released. Keeping
everything preserves recovery and replay rejection but accumulates inodes.
Deleting by age alone cannot distinguish a slow/uncertain task from completed
work. Unlinking a workspace lock while another process has it open can split
mutual exclusion between the old inode and a newly created file.

Recommended first collection policy: collect only records from a proven-dead
previous Root-service epoch, absent from pending SQLite run intents, after native
scope recovery confirms stopped state. Keep current-epoch records and every
unconfirmed/ambiguous record. Keep the existing owner-retirement barriers and
workspace lock inodes; do not create a new per-run tombstone format or weaken
RootLinuxRuns' requirement that a starting ticket belongs to the current epoch.

Alternative1: keep every journal forever (current behavior; simplest but grows).
Alternative2: TTL or delete-on-command-exit (insufficient for late callbacks,
uncertain cleanup and old dispatchers). Alternative3: compact every run into a
new persistent index (potentially smaller but introduces another durability and
migration protocol). The recommendation reuses current journals and epoch proof.

## Proposed flow

1. Expose bounded inventory and collection through an optional appended Root
   Binder capability, preserving existing transaction numbers. Inventory reads
   only Root-owned runtime metadata, with bounded batches and a resumable scan;
   it does not scan guest projects or rootfs trees.
2. App excludes pending run IDs using existing SQLite records. The collection
   operation rechecks the exact ticket, old boot/PID/start/epoch and current
   service identity. No candidate from the current epoch is eligible.
3. Acquire that run's native lock without waiting. Reuse native state recovery
   to establish that its guard/cgroup are gone. Live or damaged evidence is
   retained, not treated as completed.
4. Remove only recognized regular metadata files and the empty task directory,
   with no links, mounted descendants or recursive unknown-tree removal. Keep
   owner retirement and workspace lock files. Interrupted deletion is retryable;
   unknown/partial files remain for inspection.
5. Run bounded maintenance outside command execution, initially through an
   explicit settings action. No periodic polling, resident collector, forced
   Root-service restart or per-command global directory scan. Report collected
   and retained counts; do not promise all metadata disappears.

The resumable inventory lifetime/cancellation, native lock ownership through
unlink, and partially deleted-directory retry must be specified and tested before
enabling collection. This proposal is not permission to delete existing records
using a generic recursive filesystem command.

## Verification required

- Current epoch, live old service, pending SQL intent, held native/workspace lock,
  live guard, malformed ticket and unknown/link/mount entries are retained.
- Old stopped records are reclaimed; interrupted batches resume; late old-epoch
  start requests remain rejected before creating or executing a guest.
- Concurrent new commands and PTYs remain functional; no replay or lost leases.
- Real ARM64/FolkPatch run, service reconnect and conversation deletion checks.

## Already implemented separately

The in-memory coroutine lock registry now counts holders and waiters and releases
idle keys. Disk lock inodes are unchanged. This is independent of the proposed
persistent metadata collector, and does not claim that disk GC is implemented.

## Primary references

- [Linux flock semantics](https://man7.org/linux/man-pages/man2/flock.2.html): locks
  are tied to open file descriptions and survive descriptor duplication/fork.
- [Kernel cgroupv2 documentation](https://docs.kernel.org/admin-guide/cgroup-v2.html):
  task membership and empty/populated lifetime checks are separate from a
  userspace command's exit status.
- Aurora current RootLinuxRuns, RootLinuxRunFiles, RootWorkspaceLocks and native
  guest_run/scope_state implementation are the implementation-specific basis.
