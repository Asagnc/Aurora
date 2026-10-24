# Frida release notice inputs

Exact upstream documents, kept outside APK assets until the Frida worker is packaged.
`sources.json` records immutable upstream commits, Git blob identities, byte lengths and SHA256.
`.gitattributes` preserves their bytes across Windows/Linux checkouts. This directory does not
claim a complete static dependency, embedded agent or Java bridge notice/source closure.

- Core COPYING: the actual Frida17.24.3 Core gitlink commit.
- Gum COPYING: the actual Frida17.24.3 Gum gitlink commit, including its additional notices.
- LGPL2 text: the canonical SPDX license-list copy at an immutable commit. Core's COPYING refers
  to a COPYING.LIB, but that file is absent from this pinned Core repository root; this text is
  explicitly recorded as a separate source rather than invented as an upstream Core file.

Java bridge7.0.13 declares `LGPL-2.0 WITH WxWindows-exception-3.1`; its pinned npm package and
upstream v7.0.13 tree do not contain a standalone COPYING/LICENSE. Preserve its actual package
metadata and source notices when preparing its source bundle. Core/Gum notices must not be
misrepresented as bridge-specific copyright attribution.

Current audit and remaining packaging gates: [FRIDA_RELEASE_AUDIT.md](../../docs/FRIDA_RELEASE_AUDIT.md).
