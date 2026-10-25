#!/usr/bin/env python3
"""Offline source-material rejection tests; never contacts upstream."""
import hashlib
import importlib.util
import io
from pathlib import Path
import tarfile
import tempfile
import unittest

SPEC = importlib.util.spec_from_file_location('sources', Path(__file__).with_name('prepare-frida-sources.py'))
SOURCES = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(SOURCES)


class SourceTest(unittest.TestCase):
    def test_notice_links_are_resolved_only_inside_the_archive(self):
        with tempfile.TemporaryDirectory() as directory:
            target = Path(directory) / 'linked.tar.gz'
            for link in ['LICENSE', '../outside']:
                with tarfile.open(target, 'w:gz') as archive:
                    text = tarfile.TarInfo('repo/LICENSE'); text.size = 4
                    archive.addfile(text, io.BytesIO(b'text'))
                    member = tarfile.TarInfo('repo/COPYING'); member.type = tarfile.SYMTYPE; member.linkname = link
                    archive.addfile(member)
                blob = hashlib.sha1(b'blob ' + str(len(link)).encode() + b'\0' + link.encode()).hexdigest()
                entry = {'noticeCandidates': [{'path': 'COPYING', 'gitBlobSha1': blob}]}
                if link == 'LICENSE':
                    result = SOURCES.inspect(target, entry, True)
                    self.assertEqual([], result['missingNoticeCandidates'])
                    self.assertEqual('LICENSE', result['notices'][-1]['resolvedPath'])
                else:
                    with self.assertRaises(ValueError):
                        SOURCES.inspect(target, entry, True)

    def archive(self, root, names):
        target = root / 'source.tar.gz'
        with tarfile.open(target, 'w:gz') as archive:
            for name in names:
                member = tarfile.TarInfo(name)
                member.size = 4
                archive.addfile(member, io.BytesIO(b'text'))
        return target

    def test_notice_is_bound_to_the_pinned_git_blob(self):
        with tempfile.TemporaryDirectory() as directory:
            target = self.archive(Path(directory), ['repo/COPYING'])
            blob = hashlib.sha1(b'blob 4\0text').hexdigest()
            entry = {'noticeCandidates': [{'path': 'COPYING', 'gitBlobSha1': blob}]}
            result = SOURCES.inspect(target, entry, True)
            self.assertEqual([], result['missingNoticeCandidates'])
            self.assertEqual(blob, result['notices'][0]['gitBlobSha1'])
            entry['noticeCandidates'][0]['gitBlobSha1'] = '0' * 40
            with self.assertRaises(ValueError):
                SOURCES.inspect(target, entry, True)

    def test_traversal_duplicates_and_wrong_digest_are_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            for names in [['repo/../escape'], ['repo/LICENSE', 'repo/LICENSE']]:
                with self.assertRaises(ValueError):
                    SOURCES.inspect(self.archive(root, names), {}, True)
            with self.assertRaises(ValueError):
                SOURCES.inspect(self.archive(root, ['repo/LICENSE']), {'archiveSha256': '0' * 64}, True)

    def test_unknown_hosts_and_mutable_refs_are_rejected(self):
        for repository, commit in [('https://example.com/a', 'a' * 40), ('https://github.com/frida/v8', 'main')]:
            with self.assertRaises(ValueError):
                SOURCES.location({'repository': repository, 'commit': commit})
        url, prefixed = SOURCES.location({'repository': 'https://chromium.googlesource.com/external/github.com/llvm/llvm-project/libc.git', 'commit': 'a' * 40})
        self.assertFalse(prefixed)
        self.assertIn('/+archive/', url)


if __name__ == '__main__':
    unittest.main()
