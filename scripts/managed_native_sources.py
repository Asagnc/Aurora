"""Exact source inventory for the standalone native managed-task qualification."""
def guard_sources(root):
    native = root / "app/src/main/cpp"
    managed = ["guard_main.c", "guard_runtime.c", "guard_recover.c", "guard_result.c", "reap.c", "cancel.c",
        "journal.c", "journal_io.c", "journal_identity.c", "completion.c", "scope_plan.c", "scope_plan_codec.c",
        "launch.c", "launch_child.c"]
    linux = ["task_scope.c", "scope_identity.c", "scope_state.c", "scope_process.c"]
    frida = ["input.c", "managed_control.c", "managed_protocol.c", "managed_receive.c", "managed_observe.c"]
    return [native / directory / name for directory, names in [("managed", managed), ("linux", linux), ("frida", frida)] for name in names]
