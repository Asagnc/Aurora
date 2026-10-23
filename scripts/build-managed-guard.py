#!/usr/bin/env python3
"""Build and audit the standalone Android guard; does not package or qualify runtime."""
import argparse
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import subprocess
import tempfile
from managed_native_sources import guard_sources

ROOT = Path(__file__).resolve().parents[1]


def source_manifest():
    sources = guard_sources(ROOT)
    headers = {header for source in sources for header in source.parent.glob("*.h")}
    return {path.relative_to(ROOT).as_posix(): hashlib.sha256(path.read_bytes()).hexdigest()
        for path in sorted(set(sources) | headers)}


def build(ndk, output):
    host = "windows-x86_64" if os.name == "nt" else "linux-x86_64"
    suffix = ".exe" if os.name == "nt" else ""
    tools = ndk.resolve() / "toolchains/llvm/prebuilt" / host / "bin"
    compiler = tools / ("clang" + suffix)
    if not compiler.is_file():
        raise FileNotFoundError("Matching NDK compiler not found: " + str(compiler))
    spec = importlib.util.spec_from_file_location("frida_elf_audit", ROOT / "scripts/build-frida-probe.py")
    auditor = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(auditor)
    output.mkdir(parents=True, exist_ok=True)
    manifest = source_manifest()
    with tempfile.TemporaryDirectory(prefix=".managed-build-", dir=output) as temporary:
        binary = Path(temporary) / "guard"
        subprocess.run([str(compiler), "--target=aarch64-linux-android36", "-Wall", "-Wextra", "-Werror",
            "-Os", "-ffunction-sections", "-fdata-sections", "-fstack-protector-strong",
            *map(str, guard_sources(ROOT)), "-Wl,--gc-sections", "-Wl,-z,relro,-z,now",
            "-Wl,-z,noexecstack", "-Wl,-z,max-page-size=16384", "-o", str(binary)], check=True)
        subprocess.run([str(tools / ("llvm-strip" + suffix)), "--strip-unneeded", str(binary)], check=True)
        elf, needed = auditor.audit(tools / ("llvm-readelf" + suffix), binary)
        if manifest != source_manifest():
            raise ValueError("Native source changed during guard build")
        report = {"protocol": 1, "target": "aarch64-linux-android36", "strippedBytes": binary.stat().st_size,
            "sha256": hashlib.sha256(binary.read_bytes()).hexdigest(), "needed": needed,
            "sources": manifest, "androidRuntime": "NOT TESTED", "packagedInApk": False}
        binary.replace(output / "aurora-managed-guard")
        (output / "readelf.txt").write_text(elf, encoding="utf-8")
        (output / "report.json").write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
        print(json.dumps({key: value for key, value in report.items() if key != "sources"}, indent=2))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--ndk", type=Path, required=True)
    parser.add_argument("--directory", type=Path, required=True)
    args = parser.parse_args()
    build(args.ndk, args.directory.resolve())


if __name__ == "__main__":
    main()
