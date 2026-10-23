#!/usr/bin/env python3
"""Guard the known API36.1 Files regression in Android-bound source, including JVM core.

This targeted source check does not replace lint, dependency audits or Android36 tests.
SDK platforms/android-37.0/data/api-versions.xml marks readString/writeString since36.1.
"""
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
DIRECTORIES = ["core/src/main", "app/src/main", "app/src/androidTest"]


def unsupported_calls(source):
    aliases = {"Files", *re.findall(r"import\s+java\.nio\.file\.Files\s+as\s+(\w+)", source)}
    direct = r"import\s+(?:static\s+)?java\.nio\.file\.Files\.(?:readString|writeString)\b"
    calls = r"\b(?:" + "|".join(re.escape(alias) for alias in sorted(aliases)) + r")\s*\.\s*(?:readString|writeString)\s*\("
    return [source.count("\n", 0, match.start()) + 1 for match in re.finditer(direct + "|" + calls, source)]


def main():
    failures = []
    checked = 0
    for directory in DIRECTORIES:
        for path in sorted((ROOT / directory).rglob("*")):
            if not path.is_file() or path.suffix not in {".kt", ".java"}:
                continue
            checked += 1
            failures.extend(f"{path.relative_to(ROOT).as_posix()}:{line}: Files.readString/writeString requires Android36.1"
                for line in unsupported_calls(path.read_text(encoding="utf-8")))
    if failures:
        raise SystemExit("\n".join(failures))
    print(f"Android36 Files API regression check passed ({checked} source files; targeted check only)")


if __name__ == "__main__":
    main()
