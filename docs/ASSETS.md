# Asset provenance

The launcher bitmap is the approved AI-generated Aurora artwork in `docs/assets/approved-icon.png`. Its bytes are copied to a local drawable and composed inside a native adaptive icon; no external font or image CDN is required at runtime.

The UI vectors are Material Symbols Rounded from Google's [material-design-icons](https://github.com/google/material-design-icons/tree/49d4db35df873165d6bd6ba09b063c7dafbac2f4/symbols/web), commit `49d4db35df873165d6bd6ba09b063c7dafbac2f4`, under Apache-2.0. For each `ic_NAME.xml`, the original is `symbols/web/NAME/materialsymbolsrounded/NAME_24px.svg`. Original path data is preserved; SVG viewport offsets are represented by an Android vector group translation. The sources are menu, add, send, stop, checklist, more_vert, attach_file, settings, hub, delete, push_pin, edit and close.

Provider brand logos are not yet present. The connection selector currently shows the actual model ID as text; preview badges are not used as fabricated logos. Android's system fonts are used.

The full [Apache-2.0 license](licenses/Material-Symbols-LICENSE.txt) is also packaged as a local raw resource referenced from the manifest. A monochrome launcher derivative has not yet met visual quality requirements and is not shipped.
