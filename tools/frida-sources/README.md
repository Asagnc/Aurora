# Fixed Frida source materials

`catalog.json` pins 65 actual archives by SHA256 and Git commit or npm SHA512
integrity. This is a source superset, including build tools and optional source
files. It is not a minimal runtime dependency list or proof that the upstream
Frida SDK has been rebuilt from these sources.

The Frida releng dependency graph is fixed at
8429c0c9caf8b071955f64869a1b0ede58ad9f34. Nested source pins use the actual
repository gitlinks, including V8's custom Meson source tree; its generic DEPS
file contains different pins and must not replace these gitlinks.

The NDK29 embedded agent/helper note identifies r29/14206865. Its LLVM base
and Android patch revisions are separate from the installed NDK30 used for
the outer worker. Runtime source subtrees and both patch collections are
included. This does not install another NDK or change Android Studio.

OpenSSL integration/test submodules, HTTP library example/test submodules and
tomlkit's test corpora are omitted. The pinned Frida Meson adaptations build
OpenSSL's library source/generated config and the HTTP lib/crypto directories;
these optional integrations are not imported by those adaptations. Android
excludes the releng libusb and Apple libcxx entries. Freestanding toolchain
entries are separate from the Android target. The parent source archives and
their original .gitmodules remain intact for review.

The Java bridge's audit source map contains @frida/base64-js2.0.3,
@frida/ieee7542.0.2 and @frida/buffer7.1.0. Their versions/integrities are from
the fixed Core compiler package lock. These are emitted runtime shims; the
complete npm development dependency graph is not an Android runtime list.
Both the bridge npm source and fixed upstream repository are included. The
bridge publication has a package license declaration but no standalone license
file; do not invent an upstream COPYING filename for it.

Acquire complete archives without extracting or executing their code:

```
python scripts/prepare-frida-sources.py --catalog tools/frida-sources/catalog.json --directory app/build/outputs/frida-source-materials
```

Downloads have byte/time limits and at most three transport attempts; digest,
format and filesystem failures are not retried. An owned failed partial is
removed. Existing files require the pinned digest and are never taken as valid
just because their name resembles a source archive. Archive paths, expansion
budgets, duplicate entries, notice Git blobs and archive-local license links
are checked without extracting links onto the host filesystem.

`package-frida-materials.py` packages these sources, Aurora's exact worker
sources/build adapters and the official devkit static archive needed to relink.
`verify-frida-source-roundtrip.py` checks current trusted inputs before running
build code and verifies the offline worker binary SHA256. This is an Aurora
worker relink, not a reconstruction of the upstream SDK. APK packaging,
complete notice delivery, ART and Android/SELinux qualification remain separate.
