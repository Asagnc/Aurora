#!/usr/bin/env python3
"""Rebuild the owned guard from its source ZIP, offline, with exact trusted input checks."""
import argparse
import hashlib
import importlib.util
import json
from pathlib import Path
import stat
import subprocess
import sys
import tempfile
import zipfile

ROOT = Path(__file__).resolve().parents[1]
SCRIPTS = ["scripts/build-managed-guard.py", "scripts/managed_native_sources.py", "scripts/build-frida-probe.py"]
MANIFEST = "app/src/main/cpp/managed/sources.txt"
PREFIXES = tuple("app/src/main/cpp/" + name + "/" for name in ["managed", "linux", "frida"])


def extract(archive, work, reference):
    with zipfile.ZipFile(archive) as source:
        names = source.namelist()
        if len(names) != len(set(names)):
            raise ValueError("Duplicate corresponding-source entries")
        selected = [source.getinfo(name) for name in names if name in SCRIPTS or name.startswith(PREFIXES)]
        if sum(item.file_size for item in selected) > 16 * 1024 * 1024:
            raise ValueError("Owned guard source subset exceeds budget")
        for item in selected:
            if item.is_dir() or stat.S_IFMT(item.external_attr >> 16) not in {0, stat.S_IFREG}:
                raise ValueError("Expected ordinary source file")
            target = work / item.filename
            target.resolve().relative_to(work)
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_bytes(source.read(item))
    # Only execute known build scripts, and reject changed manifests/inputs
    # before invoking the compiler on anything from the archive.
    for name in [*SCRIPTS, MANIFEST]:
        if (work / name).read_bytes() != (ROOT / name).read_bytes():
            raise ValueError("Build adapter differs from current trusted tree: " + name)
    for name, digest in reference["sources"].items():
        target = work / name
        target.resolve().relative_to(work)
        if hashlib.sha256(target.read_bytes()).hexdigest() != digest:
            raise ValueError("Corresponding source differs from qualification: " + name)


def verify(args):
    reference = json.loads(args.reference.read_text(encoding="utf-8"))
    spec = importlib.util.spec_from_file_location("guard_builder", ROOT / SCRIPTS[0])
    builder = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(builder)
    if reference["sources"] != builder.source_manifest():
        raise ValueError("Qualification inputs differ from current trusted source tree")
    with tempfile.TemporaryDirectory(prefix="aurora-managed-source-") as temporary:
        work = Path(temporary).resolve()
        extract(args.archive, work, reference)
        subprocess.run([sys.executable, str(work / SCRIPTS[0]), "--ndk", str(args.ndk.resolve()),
            "--directory", str(work / "build")], capture_output=True, check=True)
        actual = json.loads((work / "build/report.json").read_text(encoding="utf-8"))
        if actual["sources"] != reference["sources"] or actual["sha256"] != reference["sha256"]:
            raise ValueError("Offline source rebuild did not reproduce the qualification binary")
        with args.archive.open("rb") as incoming:
            archive_sha = hashlib.file_digest(incoming, "sha256").hexdigest()
        report = {"matchedSha256": actual["sha256"], "sourceArchiveSha256": archive_sha, "strippedBytes": actual["strippedBytes"],
            "sourceHeaderCount": len(actual["sources"]), "offlineRebuild": True, "androidRuntime": "NOT TESTED",
            "scope": "Standalone guard qualification rebuild; APK CMake guard byte identity is not established by this report"}
    args.report.parent.mkdir(parents=True, exist_ok=True)
    args.report.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(report))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("archive", type=Path)
    parser.add_argument("--reference", type=Path, required=True)
    parser.add_argument("--ndk", type=Path, required=True)
    parser.add_argument("--report", type=Path, required=True)
    verify(parser.parse_args())


if __name__ == "__main__":
    main()
