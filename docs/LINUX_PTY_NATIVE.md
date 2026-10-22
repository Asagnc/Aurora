# Linux PTY native integration

2026-10-22. Reuses the approved PTY transport and cgroup/private mount lifecycle.
This is a native composition step, not an exposed Agent Linux terminal.

`libaurora_pty.so ... -- libaurora_linux_scope.so --run-pty ...` retains the
existing framed input, resize and raw output contract. The scope arguments are
identical to `--run`; only the explicit mode selects terminal setup. Pipe mode
keeps its existing environment and behavior.

The coordinator verifies all three standard descriptors refer to a terminal,
that it leads its own session and owns the foreground controlling terminal.
It releases that terminal before forking, ignoring the resulting HUP only in
the supervisors. The admitted, privately mounted/chrooted worker starts a new
session, acquires the now unowned terminal with `TIOCSCTTY` argument zero, sets
its foreground process group, resets HUP and adds `TERM=xterm-256color`.
No terminal is forcibly stolen from another session.

Rationale follows the Linux man-pages
[controlling terminal rules](https://man7.org/linux/man-pages/man2/TIOCSCTTY.2const.html)
and [foreground process group contract](https://man7.org/linux/man-pages/man3/tcsetpgrp.3.html).
Blindly wrapping the previous scope runner in a PTY leaves the guest outside
the controlling session; the detached guard must stay independent of terminal
interrupts, while Ctrl-C and window signals belong to the guest foreground job.

The PTY runner still owns transport deadlines, backpressure and its coordinator
child. The separate guard observes coordinator death and drains the task-owned
cgroup, including guest processes that create new sessions. Native completed
records remain gated on actual scope cleanup. PTY transport exit alone is not
a Linux stopped proof, and the application must retain its durable run lease
until explicit scope recovery confirms it. A guard can briefly retain its run
lock after transport exit; failed recovery means retain, never assume stopped.

## Verification status

Local Android Debug/test APK/R8 Release/lint builds pass with both ABIs. The new
Root-host suite `scripts/verify-linux-pty.py` is wired into CI and initially
awaits results. It composes real binaries with a host shell and matching host
libraries; it is not Ubuntu26.10 or Android evidence.

Five new assertions: guest TTY/Unicode/resize/nonzero exit; Ctrl-C reaches the
guest without killing supervision; transport stop drains a detached session;
transport deadline does the same; terminal mode refuses ordinary pipes before
creating run state. Existing pipe/cgroup/mount suites remain required.

Still pending: Binder terminal activation linked to the Linux ticket/lease,
terminal record backend selection and UI/Agent routing, real Android/Ubuntu
TTY tests, Root/service death and revocation qualification, ordinary PRoot
lifecycle. `executionReady=false` remains unchanged. The internal native mode
adds no resident service, new dependencies, global resource policy or polling.
