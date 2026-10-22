# 0.1.28 Root Linux file channel (2026-10-22)

User approved LINUX_WORKSPACE_ACCESS_PROPOSAL.md. Implemented, not device-qualified; arbitrary Linux command exposure and Linux PTY remain unfinished. No new native dependency, daemon, polling or rootfs copy. New linuxFileProtocol1 and linuxRunProtocol2 require a current Root service; old AIDL transaction IDs remain unchanged.

## Execution and files

Root-created files retain their ownership and modes. Normal command teardown now updates only the fixed workspace identity/active marker; it never walks/chowns the conversation tree. The existing native startup/scope/exit proof and SQL run intent/lease remain mandatory. One Root run per conversation prevents workspace reuse. Four fixed HOME/tmp/project/view directory checks are independent of the number of project files.

Root service handles list, ordered batch read, versioned batch edit, selected export and conversation Root-region removal. Coroutine mutexes plus kernel FileChannel locks in a separate Root-owned private directory serialize access across coroutine threads and old/new Root service processes. Writes/removal refuse an active guest marker; reads/export may observe live files and detect ordinary size/time changes. As with the existing filesystem tools, this is not a security sandbox against adversarial Root programs.

Files under conversation/.aurora-linux/{home,tmp,project} route through the Root channel. Existing App/shared namespaces keep their own file executor. File tool definitions stay constant as Root connection availability changes. Mixed App/Linux reads split into two domain batches then restore input order; mixed edits fail before either backend writes, rather than claiming cross-domain transaction atomicity. Root edits reuse OwnedFiles version preflight and recoverable group rollback. Symlinks/traversal/internal metadata are not targets.

Root reads open with O_NONBLOCK|O_NOFOLLOW and verify a regular inode via fstat; FIFO/device targets cannot block a Binder worker. Offset slices report next and file version. Path metadata plus text slices share a32KiB aggregate budget to bound escaped JSON and concurrent Binder replies; edit text also has a32KiB aggregate budget. Up to64 requests and200 listed entries are supported; directory listing and response metadata are bounded. Larger/binary payloads are exported selectively through files, not embedded into Binder JSON. A descriptor-based large-text transfer remains a future optimization, not implemented.

## User access and deletion

The file browser uses the same Root channel for these paths. Image publication exports only the selected regular file (max32MiB), streams it in64KiB chunks, makes that temporary copy App-readable, publishes through the existing validated image store, then removes the temporary export even on cancellation. It does not copy the entire workspace or change the original's owner. Ordinary device tests for this privileged image-export path have not run.

Conversation deletion first checks SQL leases. Root removal then checks the active marker, acquires the workspace lock and persists a Root-private retirement marker before traversing only the Root region, without following links or crossing visible mounts. It removes the workspace identity last. Late file/guest requests cannot recreate a retired owner. Interrupted/unavailable Root cleanup leaves the conversation pending; startup can still recover unrelated conversations. Reconnection allows retry. Ordinary file deletion happens only after the Root region is gone.

Root-private run/file retirement markers and lock files are retained at present. GC must account for old service epochs and pending DB records; it must not unlink a live lock inode or permit a delayed request to reuse a retired ID. No unlimited-storage/fully-finished-runtime claim is made.

## Verification

Five new JVM tests cover mixed read order/grouping, fail-before-write cross-domain edits, incomplete reply rejection, directory path restoration and connection-independent schemas. Two new ordinary Android tests cover offset/version and FIFO/link handling; two cover retained Root cleanup and unrelated startup recovery, using App-owned fixtures without simulating Root capability. These four Android tests compile only. Existing ordinary Binder fixture now rejects the file entry too.

Local core155: Windows148passed/7POSIXskipped with fixed ARM64 sample; Debug/testAPK/R8Release/lint and dual-ABI native APK checks passed. Ordinary Android gate88; no new physical-device or Ubuntu guest result. See evidence/0.1.28. Previous d841962 fullCI39750999177 passed. Next: Root runtime/file/export/revocation qualification, runtime tombstone GC and Linux PTY.

UTF-8 continuation fix: both ordinary and Root reads use one CharsetDecoder pass with explicit endOfInput. An incomplete trailing character remains at next, rather than making the entire text slice opaque or skipping bytes. Malformed bytes and a corrupt file ending remain opaque. Three JVM tests cover Chinese/emoji continuation, binary/EOF corruption and full OwnedFiles continuation; Android offset test also includes a Unicode boundary (compiled only). Basis: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/nio/charset/CharsetDecoder.html .

After explicit Root connection in Settings, pending conversation deletions are retried and history refreshed. This does not automatically cancel/recover live guest runs; run recovery stays explicit. Root browser code is separated from the chat controller to keep responsibilities and file size bounded.
