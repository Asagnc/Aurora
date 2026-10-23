#!/usr/bin/env python3
"""Install only the fixed official build-host binding; never runs npm install hooks or a source fallback."""
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import platform
import shutil
import tarfile
import tempfile

ROOT = Path(__file__).resolve().parents[1]
PINS = {
    "Windows": ("win32-x64", 57831167, "16d1579a529d505a0ebfad2ef5ae0c0b613c1759a98b9334fca14bc29192d023"),
    "Linux": ("linux-x64", 48366672, "f7eee2cf1dfa7a46d7f403d33506dba93af44a3b3b109cf214b2037ca914ec9b"),
}


def digest(path):
    with path.open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()


def main():
    if platform.system() not in PINS or platform.machine().lower() not in {"amd64", "x86_64"}:
        raise OSError("This development binding is pinned only for Windows/Linux x64")
    tool = ROOT / "tools/frida-bridge"
    package = tool / "node_modules/frida"
    if json.loads((package / "package.json").read_text(encoding="utf-8"))["version"] != "17.24.3":
        raise ValueError("Run npm ci --ignore-scripts for the exact checked-in lock first")
    spec = importlib.util.spec_from_file_location("frida_build", ROOT / "scripts/build-frida-probe.py")
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    helpers = module.source_helpers()
    label, size, expected = PINS[platform.system()]
    name = "frida-v17.24.3-napi-v8-" + label + ".tar.gz"
    downloads = tool / "build/downloads"
    downloads.mkdir(parents=True, exist_ok=True)
    archive = downloads / name
    if not archive.exists():
        helpers.fetch(archive, "https://github.com/frida/frida/releases/download/17.24.3/" + name, expected, size)
    helpers.verify(archive, expected, size)
    target = package / "build/frida_binding.node"
    target.parent.mkdir(exist_ok=True)
    with tempfile.TemporaryDirectory(prefix=".binding-", dir=target.parent) as temporary:
        prepared = Path(temporary) / "frida_binding.node"
        with tarfile.open(archive) as source:
            entries = source.getmembers()
            if len(entries) != 1 or entries[0].name != "build/frida_binding.node" or not entries[0].isfile() or entries[0].size > 200_000_000:
                raise ValueError("Unexpected official native binding archive")
            with source.extractfile(entries[0]) as incoming, prepared.open("xb") as output:
                shutil.copyfileobj(incoming, output, 65536)
        if target.is_symlink() or (target.exists() and (not target.is_file() or digest(target) != digest(prepared))):
            raise ValueError("Unexpected existing binding retained; rebuild this development node_modules with npm ci --ignore-scripts")
        if not target.exists():
            os.replace(prepared, target)
    print("Verified official Frida17.24.3 " + label + " development binding")


if __name__ == "__main__":
    main()
