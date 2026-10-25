#!/usr/bin/env python3
"""Build the pinned ARM64 Frida qualification worker, without packaging or running it."""
import argparse
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import tarfile
import tempfile
import zipfile

ROOT = Path(__file__).resolve().parents[1]
NAME = "frida-core-devkit-17.24.3-android-arm64.tar.xz"
DIGEST = "0c688824a24949e7be77109fd2c7306bba60cb47f608789e141bb24cd23b1005"
SIZE = 60903232
URL = "https://github.com/frida/frida/releases/download/17.24.3/" + NAME


def source_manifest():
    paths = sorted((ROOT / 'app/src/main/cpp/frida').glob('*.[ch]'))
    paths += [ROOT / 'scripts' / name for name in
              ['build-frida-probe.py', 'audit-frida-resources.py', 'verify-static-reverse-sources.py']]
    return {str(path.relative_to(ROOT)).replace('\\', '/'): hashlib.sha256(path.read_bytes()).hexdigest() for path in paths}


def source_helpers():
    spec = importlib.util.spec_from_file_location("pinned_sources", ROOT / "scripts/verify-static-reverse-sources.py")
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def extract(archive, directory):
    expected = {".", "./libfrida-core.a", "./frida-core.h", "./frida-core.gir", "./frida-core-example.c"}
    with tarfile.open(archive) as source:
        entries = source.getmembers()
        if len(entries) != len(expected) or {entry.name for entry in entries} != expected:
            raise ValueError("Unexpected fixed devkit archive inventory")
        if sum(entry.size for entry in entries) > 350_000_000:
            raise ValueError("Devkit expansion exceeded expected budget")
        for entry in entries:
            if entry.name == ".":
                if not entry.isdir():
                    raise ValueError("Invalid devkit root")
                continue
            if not entry.isfile():
                raise ValueError("Only regular devkit files are accepted")
            if entry.name in {"./libfrida-core.a", "./frida-core.h"}:
                target = directory / entry.name[2:]
                with source.extractfile(entry) as incoming, target.open("xb") as output:
                    shutil.copyfileobj(incoming, output, 65536)


def audit(readelf, binary):
    wire = subprocess.check_output([str(readelf), "-h", "-l", "-d", str(binary)], text=True)
    if "AArch64" not in wire or "/system/bin/linker64" not in wire:
        raise ValueError("Expected Android ARM64 executable")
    loads = [line for line in wire.splitlines() if line.strip().startswith("LOAD ")]
    if not loads or any(int(line.split()[-1], 16) < 16384 for line in loads):
        raise ValueError("Outer ELF does not meet 16KiB segment alignment")
    needed = set(re.findall(r"Shared library: \[([^\]]+)\]", wire))
    if not needed or not needed <= {"liblog.so", "libdl.so", "libm.so", "libc.so"}:
        raise ValueError("Unexpected non-system ELF dependencies: " + repr(needed))
    return wire, sorted(needed)


def build(args, archive, output):
    host = "windows-x86_64" if os.name == "nt" else "linux-x86_64"
    tools = args.ndk.resolve() / "toolchains/llvm/prebuilt" / host / "bin"
    compiler = tools / ("aarch64-linux-android36-clang.cmd" if os.name == "nt" else "aarch64-linux-android36-clang")
    suffix = ".exe" if os.name == "nt" else ""
    if not compiler.is_file():
        raise FileNotFoundError("Matching NDK compiler not found: " + str(compiler))
    if shutil.disk_usage(output).free < 900 * 1024 * 1024:
        raise OSError("At least 900MiB temporary build space required")
    with tempfile.TemporaryDirectory(prefix=".frida-build-", dir=output) as temporary:
        work = Path(temporary)
        extract(archive, work)
        binary = work / "worker"
        link_map = work / "worker.map"
        argv = [str(compiler), "-DANDROID", "-Wall", "-Wextra", "-Werror", "-Os",
                "-ffunction-sections", "-fdata-sections", "-fstack-protector-strong", "-I" + str(work),
                *[str(ROOT / "app/src/main/cpp/frida" / name) for name in ["worker.c", "input.c", "channel_input.c", "fd_channel.c", "fd_io.c", "managed_control.c", "managed_protocol.c", "managed_watch.c", "events.c", "session.c", "spawn_record.c", "spawn_session.c"]],
                "-o", str(binary), "-L" + str(work),
                "-lfrida-core", "-llog", "-ldl", "-lm", "-latomic", "-pthread", "-Wl,--export-dynamic",
                "-Wl,--gc-sections", "-Wl,-Map," + str(link_map),
                "-Wl,-z,relro,-z,now", "-Wl,-z,noexecstack", "-Wl,-z,max-page-size=16384"]
        subprocess.run(argv, check=True)
        unstripped = binary.stat().st_size
        stripped = work / "worker.stripped"
        subprocess.run([str(tools / ("llvm-strip" + suffix)), "--strip-unneeded", "-o", str(stripped), str(binary)], check=True)
        elf, needed = audit(tools / ("llvm-readelf" + suffix), stripped)
        packed = work / "size-only.zip"
        with zipfile.ZipFile(packed, "w", zipfile.ZIP_DEFLATED) as archive_zip:
            archive_zip.write(stripped, "lib/arm64-v8a/libaurora_frida.so")
        with stripped.open("rb") as stream:
            worker_digest = hashlib.file_digest(stream, "sha256").hexdigest()
        report = {"version": "17.24.3", "archiveSha256": DIGEST, "unstrippedBytes": unstripped,
                  "strippedBytes": stripped.stat().st_size, "deflatedWorkerZipBytes": packed.stat().st_size,
                  "workerSha256": worker_digest,
                  "needed": needed, "androidRuntime": "NOT TESTED", "packagedInApk": False,
                  "alignmentScope": "Outer worker only; not injected agents or loader/SELinux qualification",
                  "sources": source_manifest()}
        # This is the final link's retained section provenance, not the entire devkit inventory.
        map_text = link_map.read_text(encoding="utf-8")
        members = sorted(set(re.findall(r"libfrida-core\.a\(([^)]+)\):", map_text)))
        if not members:
            raise ValueError("Final linker map has no retained Frida archive members")
        (output / "linked-members.json").write_text(json.dumps(members, indent=2) + "\n", encoding="utf-8")
        report["retainedArchiveMembers"] = len(members)
        report["linkMapSha256"] = hashlib.sha256(link_map.read_bytes()).hexdigest()
        resource_spec = importlib.util.spec_from_file_location("frida_resources", ROOT / "scripts/audit-frida-resources.py")
        resource_audit = importlib.util.module_from_spec(resource_spec)
        resource_spec.loader.exec_module(resource_audit)
        resources = resource_audit.audit(stripped, link_map)
        report["resourceDescriptors"] = len(resources["resources"])
        (output / "resources.json").write_text(json.dumps(resources, indent=2) + "\n", encoding="utf-8")
        stripped.replace(output / "aurora-frida-probe")
        (output / "readelf.txt").write_text(elf, encoding="utf-8")
        link_map.replace(output / "worker.map")
        (output / "report.json").write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
        (output / "worker.sha256").write_text(worker_digest, encoding="ascii")
        print(json.dumps(report, indent=2))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--directory", type=Path, required=True)
    parser.add_argument("--ndk", type=Path, required=True)
    parser.add_argument("--download", action="store_true")
    args = parser.parse_args()
    output = args.directory.resolve()
    output.mkdir(parents=True, exist_ok=True)
    archive = output / NAME
    helpers = source_helpers()
    if not archive.exists() and args.download:
        helpers.fetch(archive, URL, DIGEST, SIZE)
    helpers.verify(archive, DIGEST, SIZE)
    build(args, archive, output)


if __name__ == "__main__":
    main()
