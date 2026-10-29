"""Pinned headers for the libsepol already carried by the fixed Frida SDK."""
import re
import shutil
import subprocess
import tarfile

NAME = 'selinux-507985d931eb1f0227218d5d845634e602426c20.tar.gz'
DIGEST = '97cc89bd26d623232233b1ad86fc9322fc00e663f1e80b577ee0cbdd32321090'
SIZE = 6125398
URL = 'https://codeload.github.com/frida/selinux/tar.gz/507985d931eb1f0227218d5d845634e602426c20'


def acquire(output, root, helpers, download):
    archive = output / NAME
    if not archive.exists():
        cached = root / 'app/build/outputs/frida-source-materials' / NAME
        if cached.is_file() and not cached.is_symlink():
            helpers.verify(cached, DIGEST, SIZE)
            shutil.copyfile(cached, archive)
        elif download:
            helpers.fetch(archive, URL, DIGEST, SIZE)
    helpers.verify(archive, DIGEST, SIZE)
    return archive


def headers(archive, output):
    count = total = 0
    with tarfile.open(archive) as source:
        for entry in source.getmembers():
            name = entry.name.split('/', 1)[-1]
            if not name.startswith('libsepol/include/') or entry.isdir():
                continue
            # The pinned public CIL directory link is outside this private
            # policydb header subset; never follow or extract it.
            if name == 'libsepol/include/sepol/cil' and entry.issym() and entry.linkname == '../../cil/include/cil':
                continue
            parts = name.removeprefix('libsepol/include/').split('/')
            if not entry.isfile() or any(p in {'', '.', '..'} or '\\' in p or ':' in p for p in parts):
                raise ValueError('Unexpected pinned libsepol header entry')
            total += entry.size
            if entry.size > 1048576 or total > 2097152:
                raise ValueError('Pinned libsepol headers exceed budget')
            target = output.joinpath('policy-include', *parts)
            target.parent.mkdir(parents=True, exist_ok=True)
            with source.extractfile(entry) as incoming, target.open('xb') as outgoing:
                shutil.copyfileobj(incoming, outgoing, 65536)
            count += 1
    if count != 49:
        raise ValueError('Incomplete fixed libsepol header inventory')
    return count


def symbols(nm, library, output):
    wire = subprocess.check_output([str(nm), '-g', str(library)], text=True)
    names = sorted(set(re.findall(r'\b_frida_((?:policydb|policy_file|type_datum|ebitmap|avtab|hashtab)\w*)', wire)))
    if not {'policydb_init', 'policydb_read', 'policydb_to_image', 'avtab_insert'} <= set(names):
        raise ValueError('SDK does not carry the expected namespaced libsepol ABI')
    (output / 'frida-policy-symbols.h').write_text(''.join('#define ' + n + ' _frida_' + n + '\n' for n in names))
