# Root runtime metadata maintenance

Implemented in0.1.33 after approval of LINUX_METADATA_GC_PROPOSAL.md.
Host qualification and Android device qualification are separate. Physical-device
checks are deferred to the user's final combined acceptance; phone remains0.1.32.

## Admission

The settings action is explicit. Nothing scans the runtime directory on each
command, polls in the background or restarts Root automatically. Optional
linuxMetadataProtocol1 is appended to AIDL; an older service leaves this action
disabled until reconnect. Ordinary Binder fixtures reject this privileged entry.

A Root service maintains at most one inventory cursor, returning16 directory
entries per page. A30-second idle threshold is applied on the next maintenance
request, without a timer. App cancellation/finalization explicitly closes the
cursor; service shutdown also closes it. Directory-read failures clear and close
the cursor immediately rather than retaining a permanently busy scan. Inventory
is best-effort under concurrent directory changes, not an atomic filesystem snapshot.

Inventory verifies private Root-owned runtime directories and ticket ownership,
identity and App UID. It excludes the current Root epoch and any old epoch whose
process death cannot be established from boot/PID/start ticks. Each admitted
ticket is read/parsed once during inventory, and independently again just before
collection; its hash must still match. The App excludes pending SQLite run IDs
with bounded indexed queries. Per-native-run leases are not released by GC.

## Native removal

The helper retains a stable GC-parent flock and the per-run flock through
validation, scope recovery, atomic quarantine rename and final removal. The
GC-parent lock inode stays on disk, so removing a child lock cannot split
concurrent collectors into separate lock domains. Workspace lock inodes and
owner-retirement markers also remain.

Native admission requires Root ownership/private directories, a stopped marker,
known regular metadata files, single links and no group/other write permission.
Historical Root ticket files may have read-only group/other bits inside a private
Root directory. Runtime scope journals must pass the existing checksum/identity
recovery protocol. Unknown entries, links, nested directories, writable files,
held locks and unresolved native state cause retention.

statx mount IDs reject both mounted candidate directories and bind-mounted files
before unlink. Eligible task directories move to Root-private linux-runtime-gc
before flat metadata deletion. If interrupted afterward, recognized remaining
files can be removed from quarantine even when the ticket was already deleted.
No generic recursive removal traverses a guest directory or rootfs. Temporary
helper output is cleaned in NonCancellable; cleanup errors preserve the original
failure rather than replacing it.

The known native .recovery.lock is included along with .run.lock. The initial
host run omitted it and correctly refused normal collection after the recovery
helper created the file; that failure is retained in evidence/0.1.33.

## Evidence and remaining checks

CI39818966808's native-host step passed the eleven GC cases plus the existing
31 scope/recovery/mount/guest-PTY cases. GC cases include normal and partial
quarantine cleanup, held task/global locks, unknown/link/hardlink/directory
entries, missing stopped proof, historical file modes, missing paths, writable
metadata and file/directory bind mounts. Synthetic native fixtures do not prove
the Android epoch or SQLite admission chain.

Local core, Debug/test APK, Release/R8 and lint pass. Android tests compile for
current-epoch retention, stopped old-epoch collection and late-start rejection,
pending-intent preservation followed by explicit recovery, and ordinary-UID
Binder refusal. They have not run on the physical device for this release.
The explicit device tests limit mutation to their own fixture run IDs and require
no other pending Linux task before restarting Aurora's own verified Root service.

Current or ambiguous records and permanent owner/workspace barriers intentionally
remain. This is not a promise of zero metadata, arbitrary Root program isolation
or universally supported filesystems/kernels.
