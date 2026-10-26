# Asset provenance

The launcher bitmap is the approved AI-generated Aurora artwork in `docs/assets/approved-icon.png`. Its bytes are copied to a local drawable and composed inside a native adaptive icon; no external font or image CDN is required at runtime.

The UI vectors are Material Symbols Rounded from Google's [material-design-icons](https://github.com/google/material-design-icons/tree/49d4db35df873165d6bd6ba09b063c7dafbac2f4/symbols/web), commit `49d4db35df873165d6bd6ba09b063c7dafbac2f4`, under Apache-2.0. For each `ic_NAME.xml`, the original is `symbols/web/NAME/materialsymbolsrounded/NAME_24px.svg`. Original path data is preserved; SVG viewport offsets are represented by an Android vector group translation. The sources are menu, add, stop, checklist, more_vert, settings, hub, delete, push_pin, edit, close, search, keyboard_arrow_down, arrow_upward, lightbulb, chat_bubble, folder_open, extension, speed, layers, donut_large, language, arrow_back, palette, shield, terminal and info.

Provider brand shapes come from [Lobe Icons static SVGs](https://github.com/lobehub/lobe-icons/tree/bcdb7c82792a854c2967f7328e11a30205ea584d/packages/static-svg/icons), commit `bcdb7c82792a854c2967f7328e11a30205ea584d`, under MIT. The sources are openai, claude, deepseek, gemini, zai, xiaomimimo, grok, minimax, kimi and qwen `.svg` files. Native `brand_NAME.xml` files preserve original path geometry and use a common navy ink; they do not claim original multicolor styling. GLM uses the current Z.ai shape. The complete [MIT license](licenses/Lobe-Icons-LICENSE.txt) is also packaged as a raw resource referenced from the manifest. Brand identity is matched to the curated exact model IDs, including custom connections with those IDs; family-name prefixes are used for visual identity only, without inferring endpoint trust or capabilities; unknown families use the generic hub icon. Theme-aware tinting makes monochrome marks legible in dark mode and can add selected family accents. Android's system fonts are used.

SVG compact arc flags are expanded to explicit Android path parameters. The installed FontTools SVG parser was used with an arc-preserving pen, coordinates serialized to six decimal places and round-trip checked. Directly copying compact arc flags caused an observed Gemini rendering error; the corrected native vector is checked on the emulator. No rasterized brand assets or runtime icon library are added.

The full [Apache-2.0 license](licenses/Material-Symbols-LICENSE.txt) is also packaged as a local raw resource referenced from the manifest. A monochrome launcher derivative has not yet met visual quality requirements and is not shipped.

0.1.3 removes the unused speed/layers/donut_large vectors after metrics became text-only. Original source attribution above records their history.

0.1.5 adds independently drawn native outline vectors ic_gauge, ic_database, ic_context, ic_sparkle and ic_thinking. They are Aurora geometry, not copied Lucide paths. The thinking control uses the approved lightbulb silhouette.

0.1.6 removes the unused original ic_lightbulb vector after the approved thinking control switched to the independent ic_thinking outline. Earlier Material attribution records its source history.


2026-10-26 Release图标静态审查已接package-release与完整CI：现有aapt2解析实际manifest→adaptive icon→8dp inset/fill bitmap→PNG资源链；已批准原PNG SHA256 274da3ca39c5ea28837a7c44534c6869252c787aa4fca800ab0c60fad1f54226。AAPT重新编码后PNG字节不同，但1254×1254解码RGB SHA256均为f5c4e850ed0d2ecaee4a8b836be794b3375affa5089616564602aaee1838651f，已用独立现有Pillow读取复核。CI检查只用标准库针对该固定RGB8 PNG作有界解码/CRC/filter验证，不新增产品依赖或编辑图像。不将此证明扩展为各手机launcher/mask的实际视觉验收；未新增未经批准的monochrome图标。
