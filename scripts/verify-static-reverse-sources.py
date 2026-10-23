#!/usr/bin/env python3
"""Verify compiled tool pins and official archive layout. Does not prove Android execution."""
import argparse
import hashlib
import json
from pathlib import Path
import re
import struct
import tarfile
import urllib.request
import zipfile


def catalogue():
    source = (Path(__file__).resolve().parents[1] /
              "core/src/main/kotlin/io/github/asagnc/aurora/core/StaticReverseTools.kt").read_text(encoding="utf-8")
    pattern = r'StaticReverseComponent\("([^"\n]+)", "([^"\n]+)",\s*"([^"\n]+)",\s*"([a-f0-9]{64})", (\d+)\)'
    values = re.findall(pattern, source)
    if {value[0] for value in values} != {"jre", "jadx", "apktool"} or len(values) != 3:
        raise ValueError("Compiled catalogue could not be read exactly")
    return values


def verify(path, digest, size):
    if path.is_symlink() or not path.is_file() or path.stat().st_size != int(size):
        raise ValueError("Archive type or byte count mismatch: " + path.name)
    with path.open("rb") as stream:
        actual = hashlib.file_digest(stream, "sha256").hexdigest()
    if actual != digest:
        raise ValueError("Archive SHA256 mismatch: " + path.name)


def fetch(path, url, digest, size):
    temporary = path.with_name(path.name + ".partial")
    owned = False
    try:
        with temporary.open("xb") as output:
            owned = True
            with urllib.request.urlopen(url, timeout=60) as incoming:
                count = 0
                while block := incoming.read(65536):
                    count += len(block)
                    if count > int(size):
                        raise ValueError("Download exceeded pinned byte count")
                    output.write(block)
        verify(temporary, digest, size)
        temporary.replace(path)
    except BaseException as failure:
        if owned:
            try:
                temporary.unlink(missing_ok=True)
            except OSError as cleanup:
                failure.add_note("Owned partial cleanup failed: " + str(cleanup))
        raise



def inspect(component, path):
    if component == "jre":
        with tarfile.open(path) as archive:
            members = archive.getmembers()
            if not all(item.name == "jdk-27+35-jre" or item.name.startswith("jdk-27+35-jre/") for item in members):
                raise ValueError("Unexpected runtime prefix")
            with archive.extractfile("jdk-27+35-jre/bin/java") as executable:
                header = executable.read(64)
            if header[:6] != b"\x7fELF\x02\x01" or struct.unpack_from("<H", header, 18)[0] != 183:
                raise ValueError("Runtime is not little-endian ELF64 AArch64")
            licenses = [item.name for item in members if "/legal/" in item.name]
            if not licenses:
                raise ValueError("Runtime legal notices missing")
            return {"entries": len(members), "expandedFileBytes": sum(item.size for item in members if item.isfile()),
                    "machine": "AArch64", "legalEntries": len(licenses)}
    with zipfile.ZipFile(path) as archive:
        names = archive.namelist()
        required = "lib/jadx-1.5.6-all.jar" if component == "jadx" else "brut/apktool/Main.class"
        if required not in names:
            raise ValueError("CLI entry/closure missing: " + required)
        if component == "jadx" and b"jadx.cli.JadxCLI" not in archive.read("bin/jadx"):
            raise ValueError("JADX launcher class changed")
        return {"entries": len(names), "expandedFileBytes": sum(item.file_size for item in archive.infolist()),
                "cliEntry": required}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--directory", type=Path, required=True)
    parser.add_argument("--download", action="store_true", help="explicitly download absent pinned sources")
    args = parser.parse_args()
    root = args.directory.resolve()
    root.mkdir(parents=True, exist_ok=True)
    report = {"androidExecution": "NOT TESTED", "components": []}
    for component, version, url, digest, size in catalogue():
        path = root / url.rsplit("/", 1)[1]
        if not path.exists() and args.download:
            fetch(path, url, digest, size)
        verify(path, digest, size)
        report["components"].append({"id": component, "version": version, "sha256": digest,
                                     "compressedBytes": int(size), **inspect(component, path)})
    print(json.dumps(report, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
