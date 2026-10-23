#!/usr/bin/env python3
"""Offline DT_NEEDED name inventory; not a dynamic-loader, symbol-version or Android execution test."""
import argparse
import hashlib
import importlib.util
import json
from pathlib import Path
import re
import struct
import subprocess
import tarfile


def inventory(directory, rootfs, readelf):
    script = Path(__file__).with_name("verify-static-reverse-sources.py")
    spec = importlib.util.spec_from_file_location("tool_sources", script)
    sources = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(sources)
    component = next(item for item in sources.catalogue() if item[0] == "jre")
    _, _, url, digest, size = component
    runtime = directory / url.rsplit("/", 1)[1]
    sources.verify(runtime, digest, size)
    definition = (Path(__file__).resolve().parents[1] /
                  "core/src/main/kotlin/io/github/asagnc/aurora/core/LinuxImage.kt").read_text(encoding="utf-8")
    expected = re.search(r'LinuxArchitecture.ARM64 -> "([a-f0-9]{64})"', definition).group(1)
    with rootfs.open("rb") as source:
        if hashlib.file_digest(source, "sha256").hexdigest() != expected:
            raise ValueError("Rootfs does not match the compiled ARM64 source pin")
    with tarfile.open(rootfs) as base:
        available = {Path(item.name).name for item in base.getmembers() if item.isfile() or item.issym() or item.islnk()}
    report = {"scope": "DT_NEEDED basename inventory only; paths, symbol versions and dlopen are not resolved",
              "androidExecution": "NOT TESTED", "runtimeSha256": digest, "rootfsSha256": expected, "objects": []}
    with tarfile.open(runtime) as archive:
        members = archive.getmembers()
        available |= {Path(item.name).name for item in members if item.isfile() or item.issym() or item.islnk()}
        for item in members:
            if not item.isfile():
                continue
            with archive.extractfile(item) as source:
                header = source.read(64)
                if header[:4] != b"\x7fELF":
                    continue
                if header[:6] != b"\x7fELF\x02\x01" or struct.unpack_from("<H", header, 18)[0] != 183:
                    raise ValueError("Unexpected ELF architecture: " + item.name)
                data = header + source.read()
            parsed = subprocess.run([str(readelf), "--dynamic", "--program-headers", "-"], input=data,
                                    capture_output=True, check=True, timeout=30).stdout.decode("utf-8")
            needed = re.findall(r"\(NEEDED\).*?\[([^]]+)\]", parsed)
            interpreter = re.search(r"Requesting program interpreter: ([^]]+)", parsed)
            report["objects"].append({"path": item.name, "needed": needed,
                                      "interpreter": interpreter.group(1) if interpreter else None,
                                      "missingNames": [name for name in needed if name not in available]})
    if not report["objects"]:
        raise ValueError("No ELF objects audited")
    return report


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--directory", type=Path, required=True)
    parser.add_argument("--rootfs", type=Path, required=True)
    parser.add_argument("--readelf", type=Path, required=True)
    args = parser.parse_args()
    print(json.dumps(inventory(args.directory.resolve(), args.rootfs.resolve(), args.readelf.resolve()), indent=2))


if __name__ == "__main__":
    main()
