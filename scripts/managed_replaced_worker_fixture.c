#define _GNU_SOURCE
#include <assert.h>
#include <pthread.h>
#include <time.h>
#include "../app/src/main/cpp/managed/guard_recover.c"
#include "../app/src/main/cpp/linux/scope_process.c"

long long task_scope_now(void) {
    struct timespec value;
    assert(!clock_gettime(CLOCK_MONOTONIC, &value));
    return (long long)value.tv_sec * 1000 + value.tv_nsec / 1000000;
}

static int announced[2], release_thread[2];
static void *owned_thread(void *unused) {
    (void)unused;
    pid_t tid = (pid_t)syscall(SYS_gettid);
    assert(write(announced[1], &tid, sizeof(tid)) == sizeof(tid));
    char byte;
    assert(read(release_thread[0], &byte, 1) == 1);
    return NULL;
}

int main(void) {
    assert(!pipe(announced) && !pipe(release_thread));
    pthread_t thread; assert(!pthread_create(&thread, NULL, owned_thread, NULL));
    pid_t tid; assert(read(announced[0], &tid, sizeof(tid)) == sizeof(tid));
    uint64_t ticks; assert(!scope_process_start(tid, &ticks));
    struct managed_admission old = {.worker_pid = (uint32_t)tid, .worker_ticks = ticks + 1};
    /* A stale worker PID reused by a non-leader must be recognized without
     * signalling that thread or depending on pidfd_open supporting TIDs. */
    assert(!stop_worker(&old, task_scope_now() + 1000));
    assert(!pthread_kill(thread, 0));
    assert(write(release_thread[1], "x", 1) == 1);
    assert(!pthread_join(thread, NULL));
    for (unsigned index = 0; index < 2; index++) { close(announced[index]); close(release_thread[index]); }
    puts("managed_replaced_worker_passed");
    return 0;
}
