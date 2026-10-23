#define _GNU_SOURCE
#include "mount_probe.h"
#include "task_scope.h"
#include <errno.h>
#include <fcntl.h>
#include <linux/mount.h>
#include <stdint.h>
#include <stdio.h>
#include <string.h>
#include <sys/stat.h>
#include <sys/syscall.h>
#include <sys/wait.h>
#include <unistd.h>

/* Clone a pinned source, set attributes while detached, then attach to a pinned
 * target. No remount can accidentally modify a source mount or follow a guest
 * symlink. Unsupported kernel/SELinux calls fail; no global-mount fallback. */
static int attach(int source, int target, uint64_t attributes, int recursive) {
    unsigned flags = AT_EMPTY_PATH | OPEN_TREE_CLONE | OPEN_TREE_CLOEXEC;
    if (recursive) flags |= AT_RECURSIVE;
    int tree = (int)syscall(SYS_open_tree, source, "", flags);
    if (tree < 0) return -1;
    struct mount_attr attr = {.attr_set = attributes,
        .attr_clr = attributes & MOUNT_ATTR_RDONLY ? 0 : MOUNT_ATTR_RDONLY};
    if (syscall(SYS_mount_setattr, tree, "", AT_EMPTY_PATH | (recursive ? AT_RECURSIVE : 0), &attr, sizeof(attr)) < 0 ||
        syscall(SYS_move_mount, tree, "", target, "", MOVE_MOUNT_F_EMPTY_PATH | MOVE_MOUNT_T_EMPTY_PATH) < 0) {
        int saved = errno; close(tree); errno = saved; return -1;
    }
    return tree;
}

static int child_directory(int parent, const char *name) {
    return openat(parent, name, O_RDONLY | O_DIRECTORY | O_CLOEXEC | O_NOFOLLOW);
}

static int bind_child(int source_parent, const char *source_name, int guest, const char *target_name) {
    int source = child_directory(source_parent, source_name);
    int target = child_directory(guest, target_name);
    int tree = source < 0 || target < 0 ? -1 : attach(source, target, MOUNT_ATTR_NOSUID | MOUNT_ATTR_NODEV, 0);
    int saved = errno;
    if (source >= 0) close(source);
    if (target >= 0) close(target);
    if (tree >= 0) close(tree);
    errno = saved; return tree < 0 ? -1 : 0;
}

static int bind_host(int guest, const char *name, uint64_t attributes, int recursive) {
    char absolute[32]; snprintf(absolute, sizeof(absolute), "/%s", name);
    int source = scope_open_directory(absolute);
    int target = child_directory(guest, name);
    int tree = source < 0 || target < 0 ? -1 : attach(source, target, attributes, recursive);
    int saved = errno;
    if (source >= 0) close(source);
    if (target >= 0) close(target);
    if (tree >= 0) close(tree);
    errno = saved; return tree < 0 ? -1 : 0;
}

static int exact_file(const char *path, const char *expected) {
    int file = open(path, O_RDONLY | O_CLOEXEC | O_NOFOLLOW);
    if (file < 0) return -1;
    char buffer[64]; ssize_t count = read(file, buffer, sizeof(buffer));
    int saved = errno; close(file); errno = saved;
    if (count != (ssize_t)strlen(expected) || memcmp(buffer, expected, strlen(expected))) { errno = EINVAL; return -1; }
    return 0;
}

static int write_file(const char *path, const char *text) {
    int file = open(path, O_WRONLY | O_CREAT | O_EXCL | O_CLOEXEC | O_NOFOLLOW, 0600);
    if (file < 0) return -1;
    ssize_t count = write(file, text, strlen(text));
    int saved = errno; close(file); errno = saved;
    if (count != (ssize_t)strlen(text)) { if (count >= 0) errno = EIO; return -1; }
    return exact_file(path, text);
}

static int check_guest(void) {
    char current[4];
    if (!getcwd(current, sizeof(current)) || strcmp(current, "/") ||
        exact_file("/aurora-fixture", "aurora-chroot-fixture-v1\n") < 0) return -1;
    int forbidden = open("/must-remain-readonly", O_WRONLY | O_CREAT | O_EXCL | O_CLOEXEC, 0600);
    int saved = errno;
    if (forbidden >= 0) { close(forbidden); errno = EINVAL; return -1; }
    if (saved != EROFS) { errno = saved; return -1; }
    if (write_file("/root/home.txt", "home") < 0 || write_file("/tmp/source", "temporary") < 0 ||
        link("/tmp/source", "/tmp/hardlink") < 0 || exact_file("/tmp/hardlink", "temporary") < 0 ||
        write_file("/mnt/project.txt", "project") < 0) return -1;
    int proc = open("/proc/self/status", O_RDONLY | O_CLOEXEC);
    if (proc < 0) return -1;
    char byte; ssize_t read_count = read(proc, &byte, 1); close(proc);
    if (read_count != 1) { errno = EIO; return -1; }
    int device = open("/dev/null", O_WRONLY | O_CLOEXEC);
    if (device < 0) return -1;
    ssize_t written = write(device, "x", 1); close(device);
    if (written != 1) { errno = EIO; return -1; }
    return 0;
}

static int bind_tools(int guest, const char *tools) {
    if (!tools) return 0;
    int source = scope_open_directory(tools);
    int target = child_directory(guest, "opt");
    int tree = source < 0 || target < 0 ? -1 : attach(source, target,
        MOUNT_ATTR_RDONLY | MOUNT_ATTR_NOSUID | MOUNT_ATTR_NODEV, 0);
    int saved = errno;
    if (source >= 0) close(source);
    if (target >= 0) close(target);
    if (tree >= 0) close(tree);
    errno = saved; return tree < 0 ? -1 : 0;
}

int scope_chroot_enter(const char *rootfs, const char *workspace, const char *tools) {
    int source = scope_open_directory(rootfs);
    int work = scope_open_directory(workspace);
    int target = work < 0 ? -1 : child_directory(work, "view");
    int guest = source < 0 || target < 0 ? -1 : attach(source, target,
        MOUNT_ATTR_RDONLY | MOUNT_ATTR_NOSUID | MOUNT_ATTR_NODEV, 0);
    int saved = errno;
    if (source >= 0) close(source);
    if (target >= 0) close(target);
    if (guest < 0) { if (work >= 0) close(work); errno = saved; return -1; }
    int prepared = bind_child(work, "home", guest, "root") == 0 && bind_child(work, "tmp", guest, "tmp") == 0 &&
        bind_child(work, "project", guest, "mnt") == 0 && bind_tools(guest, tools) == 0 &&
        bind_host(guest, "proc", MOUNT_ATTR_RDONLY | MOUNT_ATTR_NOSUID | MOUNT_ATTR_NODEV | MOUNT_ATTR_NOEXEC, 0) == 0 &&
        bind_host(guest, "dev", MOUNT_ATTR_RDONLY | MOUNT_ATTR_NOSUID, 1) == 0;
    saved = errno; close(work);
    if (!prepared || fchdir(guest) < 0 || chroot(".") < 0 || chdir("/") < 0) {
        if (!prepared) errno = saved;
        saved = errno; close(guest); errno = saved; return -1;
    }
    close(guest);
    return 0;
}

int scope_chroot_fixture(const char *rootfs, const char *workspace) {
    if (scope_chroot_enter(rootfs, workspace, NULL) < 0) return -1;
    return check_guest();
}

/* Execute a real guest ELF with a deliberately nonzero status. No host
 * environment or arbitrary caller command is inherited by this fixture. */
int scope_chroot_shell_fixture(void) {
    pid_t child = fork();
    if (child < 0) return -1;
    if (child == 0) {
        char *const args[] = {"/bin/sh", "-c",
            "test \"$HOME\" = /root && test \"$PWD\" = /mnt && "
            "test -z \"$AURORA_HOST_SECRET\" || exit 91; "
            "printf guest-shell > /root/shell.txt; exit 37", NULL};
        char *const env[] = {"HOME=/root", "PATH=/usr/bin:/bin", "TMPDIR=/tmp", "LANG=C", NULL};
        if (chdir("/mnt") < 0) _exit(125);
        execve(args[0], args, env);
        _exit(127);
    }
    int status;
    while (waitpid(child, &status, 0) < 0) { if (errno != EINTR) return -1; }
    if (!WIFEXITED(status) || WEXITSTATUS(status) != 37) { errno = ECHILD; return -1; }
    return exact_file("/root/shell.txt", "guest-shell");
}
