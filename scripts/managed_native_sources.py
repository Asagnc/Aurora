"""Exact source inventory for the standalone native managed-task qualification."""
import re


def guard_sources(root):
    native = root / "app/src/main/cpp"
    sources = (native / "managed/sources.txt").read_text(encoding="utf-8").splitlines()
    if not sources or len(sources) != len(set(sources)) or any(
        not re.fullmatch(r"(managed|linux|frida)/[a-z_]+\.c", name) or not (native / name).is_file() for name in sources):
        raise ValueError("Invalid or duplicate managed guard source inventory")
    return [native / name for name in sources]
