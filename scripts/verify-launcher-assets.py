#!/usr/bin/env python3
"""Verify actual APK launcher resource references and the approved artwork pixels using the existing SDK."""
import argparse
import json
from pathlib import Path
from launcher_assets import verify

if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('apk', type=Path)
    parser.add_argument('--aapt2', type=Path, required=True)
    args = parser.parse_args()
    print(json.dumps(verify(args.apk, args.aapt2)))
