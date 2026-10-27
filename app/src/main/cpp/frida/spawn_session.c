#define _GNU_SOURCE
#include "session.h"
#include <fcntl.h>
#include <poll.h>
#include <string.h>
#include <sys/stat.h>
#include <sys/syscall.h>
#include <unistd.h>

gboolean aurora_spawn_prepare(struct aurora_frida_session *run, GError **error) {
    if (!run->input->spawn_program) return TRUE;
    run->spawn_directory = open(".", O_RDONLY | O_DIRECTORY | O_NOFOLLOW | O_CLOEXEC);
    struct stat info;
    if (run->spawn_directory < 0 || fstat(run->spawn_directory, &info) ||
        info.st_uid != geteuid() || (info.st_mode & 077)) return FALSE;
    memcpy(run->spawned.ticket, run->input->ticket, 32);
    if (aurora_spawn_store(run->spawn_directory, 1, &run->spawned)) return FALSE;
    FridaSpawnOptions *options = frida_spawn_options_new();
    /* Android package spawning uses the platform launcher, which cannot
     * redirect app stdio. Script events retain the managed FD log channel.
     * Native Linux targets keep pipes so target stdout cannot corrupt JSON. */
#if defined(__ANDROID__)
    frida_spawn_options_set_stdio(options, FRIDA_STDIO_INHERIT);
#else
    frida_spawn_options_set_stdio(options, FRIDA_STDIO_PIPE);
#endif
    guint pid = frida_device_spawn_sync(run->device, run->input->spawn_program, options, run->operation, error);
    g_object_unref(options);
    if (!pid || *error) return FALSE; /* A failed call does not prove that no process was created. */
    run->target_fd = (int)syscall(SYS_pidfd_open, pid, 0);
    struct pollfd event = {.fd = run->target_fd, .events = POLLIN};
    uint64_t ticks;
    if (run->target_fd < 0 || frida_process_start(pid, &ticks) || poll(&event, 1, 0) != 0) return FALSE;
    run->spawned.pid = pid; run->spawned.ticks = ticks;
    if (aurora_spawn_store(run->spawn_directory, 2, &run->spawned)) return FALSE;
    run->input->pid = (pid_t)pid; run->input->start_ticks = ticks;
    return TRUE;
}

gboolean aurora_spawn_resume(struct aurora_frida_session *run, GError **error) {
    if (!run->input->spawn_program) return TRUE;
    if (g_cancellable_is_cancelled(run->operation) || !frida_target_matches(run->input)) return FALSE;
    /* Persist before the side effect: uncertainty must never trigger a later automatic kill. */
    if (aurora_spawn_store(run->spawn_directory, 3, &run->spawned)) return FALSE;
    run->resume_requested = TRUE;
    frida_device_resume_sync(run->device, run->spawned.pid, run->operation, error);
    return *error == NULL && aurora_spawn_store(run->spawn_directory, 5, &run->spawned) == 0;
}

void aurora_spawn_cleanup(struct aurora_frida_session *run) {
    if (!run->input->spawn_program) return;
    if (run->spawn_directory >= 0 && run->spawned.pid && !run->resume_requested) {
        if (aurora_spawn_terminate(&run->spawned, 2000) ||
            aurora_spawn_store(run->spawn_directory, 4, &run->spawned)) g_atomic_int_set(&run->failed, 1);
    }
    if (run->spawn_directory >= 0) close(run->spawn_directory);
}
