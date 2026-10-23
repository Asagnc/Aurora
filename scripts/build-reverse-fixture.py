#!/usr/bin/env python3
"""Rebuild the tiny owned DEX fixture; no network or phone operations."""
import argparse
import os
import pathlib
import shutil
import subprocess
import tempfile

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("--jdk", required=True, type=pathlib.Path)
parser.add_argument("--build-tools", required=True, type=pathlib.Path)
parser.add_argument("--android-jar", required=True, type=pathlib.Path)
args = parser.parse_args()
if not args.android_jar.is_file():
    parser.error("--android-jar must name an existing SDK platform jar")
repo = pathlib.Path(__file__).resolve().parent.parent
source = repo / "app/src/androidTest/fixtures/AuroraReverseProbe.java"
target = repo / "app/src/androidTest/assets/reverse/aurora-probe.dex"
with tempfile.TemporaryDirectory(prefix="aurora-reverse-fixture-") as temporary:
    root = pathlib.Path(temporary)
    classes = root / "classes"
    dex = root / "dex"
    classes.mkdir()
    dex.mkdir()
    javac = args.jdk / "bin" / ("javac.exe" if os.name == "nt" else "javac")
    d8 = args.build_tools / ("d8.bat" if os.name == "nt" else "d8")
    subprocess.run([str(javac), "--release", "17", "-g:none", "-d", str(classes), str(source)], check=True)
    subprocess.run([str(d8), "--release", "--min-api", "36", "--lib", str(args.android_jar),
                    "--output", str(dex), *map(str, classes.rglob("*.class"))], check=True)
    target.parent.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(dex / "classes.dex", target)
print(target)
