#!/usr/bin/env python3
"""Reject compiled JUnit @Test methods with non-void signatures; never executes tests or Android APIs."""
import argparse
import json
import os
from pathlib import Path
import re
import shutil
import subprocess


def data(path):
    absolute = str(path.resolve())
    if os.name == 'nt' and not absolute.startswith('\\\\?\\'):
        absolute = '\\\\?\\UNC\\' + absolute[2:] if absolute.startswith('\\\\') else '\\\\?\\' + absolute
    return Path(absolute).read_bytes()


def verify(directory, javap):
    if not directory.is_dir():
        raise ValueError('Compiled test directory missing')
    classes = [path for path in directory.rglob('*.class') if any(marker in data(path)
        for marker in [b'Lorg/junit/jupiter/api/Test;', b'Lorg/junit/Test;'])]
    count = 0; failures = []
    for start in range(0, len(classes), 20):
        # Only ASCII method/annotation headers are inspected; javap constant strings may use the host encoding.
        output = subprocess.check_output([str(javap), '-v', *map(str, classes[start:start + 20])]).decode('utf-8', errors='replace')
        for block in re.split(r'\n  (?=(?:public|private|protected|static) )', output):
            if re.search(r'\n\s+org\.junit\.(?:jupiter\.api\.)?Test\s*(?:\n|\()', block):
                count += 1; header = block.splitlines()[0].strip()
                if ' void ' not in header:
                    failures.append(header)
    if not count or failures:
        raise ValueError('JUnit @Test must return void/Unit; otherwise discovery may ignore it: ' + repr(failures))
    return dict(directory=str(directory), annotatedMethods=count, nonVoidMethods=0)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--classes', type=Path, nargs='+', required=True)
    parser.add_argument('--javap', type=Path, default=Path(shutil.which('javap') or 'javap'))
    args = parser.parse_args()
    print(json.dumps([verify(path, args.javap) for path in args.classes]))
