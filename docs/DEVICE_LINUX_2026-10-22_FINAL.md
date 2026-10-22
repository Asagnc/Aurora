# Android16 ARM64 Linux qualification — 2026-10-22

Final application0.1.30/code31, realme GT6SE/RMX3850, API36, kernel
6.1.141-android14-11-o-gd896e1db71a8, existing FolkPatch Aurora grant.
Updated with install-r throughout; no main-app uninstall or data clearing.
Final debug APK SHA256:
`b0438e379313f8a0a0d6f9826a4a37b005918d1f82fc77e1a40232938ffb24a0`.

## Passed on this phone

| Scope | Tests | Evidence under evidence/0.1.30 |
| --- | ---: | --- |
| Ordinary application, files, Skills, images, Binder, commands, logs, terminal and theme regression |88|ordinary.txt|
| Separate-process browser profile recovery |1|browser-recovery.txt|
| Root Android commands, PTY, cancellation/deadline and disconnect/reconnect |5|root-native.txt|
| Root connect/disconnect/reconnect plus original-ownership image metadata/hardlink recheck |2|root-install-connect.txt|
| Actual Ubuntu guest command, Unicode slices, version edit/stale rejection, export/deletion; background writer timeout and cancellation/leases |3|root-runs.txt|
| Actual Ubuntu PTY, merged output, Unicode input, resize, persisted nonzero exit, stop/delete and guest deadline |3|root-pty.txt|
| Kill only Aurora's verified Root-service PID, retain lease, reconnect a new epoch, recover without command replay |1|root-service-death.txt|
| Explicit retained qualification-owner recovery through native proof and SQL lease completion |1|qualification-recovery.txt|

The last recovery test only selects diagnostic owner titles after explicit test
flags, confirms stopped proof, then deletes those owners. It never wipes the
database or manually drops protection records. The three old PTY checks and new
service-death check were separate invocations; no duplicate runs are added to
these counts. Temporary test APK and both transferred archive copies were removed
at completion. Aurora and the shared Root Ubuntu generation remain installed.

## Failures found and corrected

1. Phone public HTTPS image download stopped at128KiB and ultimately raised an
   OkHttp timeout. This path **did not pass**. The computer's existing exact
   official36,245,313-byte ARM64 archive, SHA256
   `8b7ea4ed71a92873f7f4e260a3defe0d9ed85cb69c0155b12414208f79fa0f2d`,
   was transferred to a temporary App cache file. A new internal qualification
   input streams at most64MiB into the same staging transaction and rechecks the
   same fixed digest before scan/extraction/Root ownership/publication. It is
   not a different image or an exposed UI import feature.
2. Initial offline install published successfully but its assertion called Java
   Files.readString, absent on this Android16 runtime. Replaced readString and
   writeString across Android tests with explicit UTF8 byte operations. Production
   sources contained no such calls. The image test now validates existing as well
   as newly published generations and preserves their respective ownerships.
3. Native run startup rejected the trusted Context `/data/user/0` alias with
   state_directory errno20. Canonicalize the trusted App files base first, while
   continuing to reject links in every resource/workspace component below it.
   Retained leases were released only after successful native recovery proof.
4. Android's UTF8 decoder consumed an incomplete trailing byte into internal
   state; the returned text was correct but its cursor skipped a byte. Determine
   a valid potentially incomplete RFC3629 tail first, then strictly decode only
   the complete prefix. This examines at most four tail bytes, does not copy the
   whole input and retains invalid overlong/surrogate/out-of-range input as opaque.
   Added two JVM cases; actual Android Root and ordinary file regression pass.
5. Qualification cleanup initially masked primary failures with the expected
   pending-lease refusal. It now preserves the original exception with cleanup
   suppressed, while keeping uncertain files and leases protected.

All initial failures are retained under evidence/0.1.30/failures. Failed installs
and method assertions are not counted as passes. The successful image recheck
reuses the generation published by the earlier offline run; it is not a second
fresh installation.

## Local build and CI

Core161 total:154 passed/7 POSIX-only skipped on Windows, fixed signed-image
sample supplied. Debug/test APK/R8 Release/lint, dual-ABI ELF/16KiB and source
packaging passed. CI39784460008 fully succeeded for the preceding download fix;
the0.1.30 device fixes require their own new CI result.

## Still incomplete

Agent/UI Linux launch selection remains internal, executionReady=false. PRoot
full guest lifecycle, Root metadata GC, external manager revocation, a public
image download success on this phone, selected Root image decode/publication,
long-duration screen-off/pressure/throughput/battery and real model/cache tests
remain separate work. This qualification does not establish all-handset/root
manager compatibility, a security sandbox or performance leadership.
