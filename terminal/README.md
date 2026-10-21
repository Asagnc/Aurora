# Embedded terminal components

Vendored from Termux **v0.118.3**, commit `5b657c6adf4304e5198951ce815fe0205dcac29c`.
`UPSTREAM.json` fixes the source revision and SHA-256 of each imported Java file.
Adapted upstream files: `TerminalSessionClient.java` removes callbacks requiring
Termux process/session ownership, retaining emulator/log callbacks;
`TerminalEmulator.java` adds a maximum cell budget during buffer resize, preventing
narrow-to-wide resizing from multiplying the retained scrollback memory budget.

Includes terminal-emulator's VT/Unicode/buffer/key classes and terminal-view's
`TerminalRenderer`. Does **not** include TerminalSession, JNI, termux-shared,
Termux application, repositories or Linux distribution. Aurora owns PTY process
supervision, lifecycle, IPC, background parsing and Compose/native View integration.

The upstream `LICENSE.md` explicitly identifies terminal-emulator/terminal-view
as Apache 2.0 exceptions to the application's GPLv3 license. The original declaration,
Apache license and WcWidth's MIT-derived table license are retained and packaged
under APK assets/licenses. Existing in-file attribution is preserved. No claim is
made that the whole Termux repository is Apache licensed.

These upstream files intentionally exceed Aurora's normal 300-line file target:
preserving upstream structure simplifies license review, patch audits and upgrades.
The imported width table describes Unicode 15; this is the stable upstream baseline,
not a claim of support for every newest emoji/grapheme combination. Upgrade only
after comparing upstream stable sources and repeating compatibility tests.
