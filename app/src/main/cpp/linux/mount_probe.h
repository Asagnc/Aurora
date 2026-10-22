#ifndef AURORA_MOUNT_PROBE_H
#define AURORA_MOUNT_PROBE_H
/* Fixed native qualification only: no shell, APK, image install or arbitrary
 * guest program is accepted. Caller has entered its private mount namespace. */
int scope_chroot_fixture(const char *rootfs, const char *workspace);
#endif
