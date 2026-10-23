#ifndef AURORA_MOUNT_PROBE_H
#define AURORA_MOUNT_PROBE_H
/* Fixed qualification: native checks and optional fixed guest-shell script.
 * No caller-supplied program is accepted. Caller owns a private mount namespace. */
int scope_chroot_enter(const char *rootfs, const char *workspace, const char *tools);
int scope_chroot_fixture(const char *rootfs, const char *workspace);
int scope_chroot_shell_fixture(void);
#endif
