#!/usr/bin/env python3
"""Acquire only the already reviewed standalone archive for a Kotlin POSIX import test. Does not execute it."""
import argparse
import importlib.util
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def acquire(directory):
    spec = importlib.util.spec_from_file_location('fixed_proxy_archive', ROOT / 'scripts/qualify-apk-jvm.py')
    module = importlib.util.module_from_spec(spec); spec.loader.exec_module(module)
    directory.mkdir(parents=True, exist_ok=True)
    pin = json.loads((ROOT / 'tools/network-proxy/catalog.json').read_text())
    print(module.acquire(pin, directory))


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--directory', type=Path, required=True)
    acquire(parser.parse_args().directory)
