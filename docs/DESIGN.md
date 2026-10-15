# Aurora design baseline

Status: product requirements and cache, batch tools, browser and architecture contracts approved. Actual implementation evidence is tracked separately in IMPLEMENTATION.md; this specification is not a performance claim.

Research date: 2026-10-12.

## Approved architecture

- Kotlin is the primary implementation language; the protocol/file core is independent of Android UI.
- Jetpack Compose and Material 3 render the native application. WebView is the browsing engine, not the application UI renderer.
- Privileged Android work uses libsu RootService and Binder after authorization; the ordinary path remains available without Root.
- The app uses the device's existing Linux kernel. No custom kernel, ROM replacement or handset-specific modification is part of the baseline.
- Android-native execution, PRoot and Root chroot are separate execution backends. PRoot is not inserted into every tool or model request.
- NDK C/C++ is used where a native tool or measured bottleneck requires it, rather than rewriting all orchestration into a native layer.
- Latest stable versions are pinned reproducibly and tested as a combination. Android 16 remains the minimum even when compiling and targeting a newer stable SDK.
- Persistence uses Android-native SQLite with WAL and append-only message/native-history records, confirmed on 2026-10-12.

Approved concept assets: [launcher artwork](assets/approved-icon.png), [chat and drawer preview](assets/approved-chat-ui.png). These are visual references, not implemented screenshots.

2026-10-15 UI revision supersedes the earlier composer: [approved capsule A and conversation menu](assets/approved-composer-A.png); [color settings direction](assets/approved-color-settings.png). Keep the originally approved launcher artwork. Generated messages and metrics are illustrative, never verification evidence.

2026-10-15 execution constraint: packet capture is Root-only; do not add a VPN capture backend. Ordinary native features remain available without Root. Avoid overhead-heavy substitutes for unavailable privileged operations. PRoot/chroot remain unfinished; resolve their performance tradeoffs before enabling any default Linux backend.

## Confirmed product requirements

- Application name: Aurora. Application ID: `io.github.asagnc.aurora`.
- Android 16 and newer only. Minimum SDK: 36.
- Ordinary capabilities work without Root. Explicit Root authorization enables privileged Android APIs, system operations, and chroot.
- No dependency on an installed Shizuku application.
- Detect actual Root capabilities across APatch/FolkPatch, KernelSU, and Magisk families instead of branching on handset names.
- PRoot is the default Linux environment; chroot is an available Root backend. Android-native execution remains a separate backend.
- On-device APK builds include Kotlin/Java, Compose, C/C++, JNI, signing, and installation verification.
- Approved launcher artwork: aurora ribbon and meteor against a starry background.
- Use current stable dependencies, plugins, and UI libraries. Model presets cover latest model lines that remain available for purchase.
- Provider presets: OpenAI, Anthropic, DeepSeek, Google Gemini, GLM, Xiaomi MiMo, xAI Grok, MiniMax, Kimi, Alibaba Model Studio.
- All credentials belong to the user. Custom gateways have independent endpoints, protocol settings, and model catalogs.

## Conversation UI

- Drawer action at top left; conversation content occupies the main screen.
- Composer anchored at the bottom and moves above the keyboard.
- Composer controls: attachment, supported thinking level, model identity/selector, send.
- Three independently tappable indicators inside the bottom of the capsule composer: output tokens/s, cache read share, context use. Keep unavailable metrics unknown and distinguish estimates.
- Top-right conversation menu: new conversation, current conversation files, browser. Proxy configuration belongs in browser settings and applies to all browser pages; it does not configure the model transport.
- Appearance supports named palettes, a custom accent, light/dark/system modes and optional colored function icons. Configuration persists locally. Theme changes do not reconstruct browser engines or trigger model requests.
- Drawer footer: settings on the left, providers in the center, Skills on the right.
- Conversation list: multi-selection icon at top right; click opens a conversation; long press or overflow opens rename, pin, select, and delete actions.
- Multi-selection supports batch deletion and pinning.
- Space and tool management remains reachable from the drawer.
- Provider identity, configured connection, and model identity are separate fields. Official and custom connections can offer the same model.
- Brand assets must have verifiable sources; schematic badges in previews are not production logos.
- Missing metrics display an unavailable state. Estimates must be labeled and replaced by actual usage where available.

## Storage ownership

| Class | Contents | Deletion and mutation rules |
| --- | --- | --- |
| Conversation space | Imported copies, downloads, generated artifacts, temporary builds, conversation memory and task records | Deleted with its conversation after owned tasks and resources are closed |
| Shared resources | Versioned tools, plugins, immutable dependencies, dictionaries and base environments | Tasks read; the installer publishes and removes versions |
| Shared writable workspace | Explicitly retained projects/artifacts and namespaced mutable tool/build caches | Persists across conversation deletion |

- Default task output belongs to its conversation. Persistent shared output has an explicit destination.
- Deletion never follows links or mounts into shared resources or external user files.
- User originals and exported files remain at their original destinations.
- Conversation deletion coordinates database records, task ownership, file descriptors, browser profiles, mounts, and files with a recoverable deletion record.
- Shared immutable dependency bytes are not repeatedly copied into each conversation.
- A running task pins its dependency versions. Updates publish a new version rather than modifying a live version.
- Read-only task views are enforced where the backend allows it. Arbitrary privileged programs must not be described as safely contained by directory permissions alone.
- Namespaced writable caches have independent accounting and cleanup; installed tools are not treated as disposable download caches.

## Source conventions

- Normal hand-written source files target at most 300 lines. Review responsibilities before splitting; document justified exceptions.
- Ordinary functions target 30–40 lines; functions over 60 lines require responsibility/complexity review.
- Ordinary functions target at most five parameters and three nested control-flow levels.
- Generated code, data declarations and declarative UI use rules appropriate to their structure.
- Names express the domain and action; avoid catch-all utility containers.
- Blocking I/O does not run on the main thread. Coroutine cancellation propagates.
- Errors at protocol, permission, file, installation and process boundaries have structured, actionable results.
- Comments explain non-obvious behavior and invariants; they do not narrate development requests.
- File length rules improve reviewability; performance improvements require measurements.

## Approved cache contract

### Policy dimensions

Resolve protocol defaults, model-specific behavior, and provider/endpoint capabilities separately. Relevant dimensions include model identity/revision, account and region where known, request settings, tool definitions and order, prefix content, serialization/projection version, and cache lifetime.

A client fingerprint is diagnostic metadata. It is not a server cache address and cannot guarantee cache reuse across providers, credentials, model changes, or regions.

- Keep reusable instructions and tool definitions stable.
- Keep completed message blocks and protocol-specific reasoning/tool state unchanged within their valid provider scope.
- Append current observations and new turns instead of rewriting earlier history.
- Preserve native blocks, signatures and opaque response items under their documented scope; do not blindly copy them to another protocol/model.
- Use supported explicit breakpoints and lifetime settings per model and endpoint.
- Pin routing where the gateway documents support. Unknown gateways are capability-tested and reported as unknown until evidence is available.
- Track actual read, write and ordinary-input usage. A high hit ratio alone is not the optimization target; include cost, first-response latency, task duration and quality.
- Do not add recurring paid keepalive or prewarming calls without an explicitly selected policy.
- Current provider documentation, not a field's apparent age, determines supported protocol headers and parameters.

### Switching model or thinking level

An in-flight request keeps its original configuration. Accepted changes apply at a documented next-request/turn boundary. Do not resend executed actions as a side effect of configuration changes.

Before applying a change after requests exist, classify its cache effect:

| Classification | Behavior |
| --- | --- |
| Supported prefix-preserving update | Explain what is retained; apply the provider's supported append-only update |
| Known invalidation | Ask for confirmation with the affected setting, likely reprocessing/write cost, and effective boundary |
| Unknown gateway behavior | Explain that retention cannot be confirmed; ask before applying |
| No effective configuration change | Do not show a cache-loss confirmation |

Current evidence:

- Supported GPT-6 Responses models can append `configuration_update` for reasoning effort changes while retaining earlier cached prefixes.
- Anthropic top-level thinking/effort changes invalidate cache breakpoints according to its current documentation.
- Anthropic per-message effort is currently beta; the stable-only baseline does not enable it by default.
- Switching provider or model starts a new reuse scope; continuation retains valid conversation facts and completed tool outcomes without replaying operations.

Confirmation text is specific, for example: “Changing this model starts a new cache scope. Existing conversation content stays available; the next request may need to process it again. Apply after the current turn?” Actual localized copy will reflect the precise known capability.

## Approved batch file tools

### Batch reads

- Accept an ordered list of file requests with identity, path, range and output budget.
- Read independent files concurrently under a bounded resource budget.
- Return per-file status, requested/resolved range, encoding, version/digest, bytes read and continuation cursor.
- Preserve request order in the result; partial failure remains explicit.
- Stream large files. Binary files use bounded metadata or artifacts rather than pretending to be text.

### Batch edits

- Accept file edits bound to expected versions/content; preflight all targets before writes.
- Serialize overlapping edits and explicitly reject ambiguous overlapping ranges.
- Stage changes and publish with per-file atomic replacement where supported.
- Use a recoverable transaction journal for coordinated multi-file edits.
- Distinguish atomic-per-file, recoverable-group, and unsupported atomicity. A normal filesystem does not provide instant atomic multi-file replacement.
- Different volumes, Android document providers and privileged files retain their real backend limitations.
- Cancellation, interruption and recovery never report partially written changes as full success.
- Return per-file result and a group summary with applied/rolled-back/uncertain states as applicable.

## Approved browser contract

Recommended baseline: system WebView plus current stable AndroidX WebKit, hosted separately from the conversation UI lifecycle.

- Multiple pages: open, list, navigate, close, screenshot, bounded text/DOM query, structured element actions and JavaScript evaluation.
- Independent page reads/navigation can be scheduled concurrently; mutations within one page remain ordered.
- Per-conversation browsing profiles isolate cookies and storage when `MULTI_PROFILE` is supported.
- Profiles owned by a deleted conversation are removed after associated WebViews are closed. Persisting a login profile is an explicit separate choice.
- Loaded Chromium profiles may remain in use after WebView destruction. Persist the conversation deletion marker before cleanup, close owned views, await native deletion of cache/cookies/web storage, and retain the empty profile metadata for next-process recovery when immediate removal is rejected. Do not delete conversation files when browsing-data cleanup fails or times out.
- Browser proxy is a browser-level configuration. Current `ProxyController` overrides all app WebViews; profiles do not imply separate per-tab proxy routing.
- Independent per-page proxy identities would require an additional isolation design and measurement; they are not claimed by this baseline.
- Downloads are assigned to the owning conversation or an explicit retained destination.
- JavaScript results are bounded, typed and treated as page data. Web pages do not receive a privileged Android bridge.
- Read actions expose pagination/cursors instead of unbounded DOM or screenshot dumps.
- Renderer failures and navigation errors return actionable statuses; a changed page is not acted on using stale references.
- Login, CAPTCHA, rate limiting, DRM and site-controlled restrictions can require visible user takeover. No browser or proxy guarantees unrestricted access to every website.

Alternatives assessed: Playwright Android currently remains experimental and requires ADB; it is not the default on-device browser runtime. Bundling a complete browser engine adds package, memory and update responsibilities requiring separate evidence.

## Verification gates

- Build the confirmed latest-stable toolchain combination rather than selecting versions solely because each is individually latest.
- Verify debug/release builds, lint and meaningful runtime/protocol/storage tests.
- Verify APK and JNI builds on an ARM64 device; desktop builds alone do not establish on-device toolchain support.
- Root service handshake, SELinux behavior, mount isolation, process cancellation and cleanup require device evidence.
- Measure startup, frame behavior, model-independent tool latency, peak memory, sustained power and task success on repeatable workloads.
- Compare cache reuse using actual usage/diagnostics; unsupported or hidden gateway statistics remain unknown.
- Verify switching during streaming and tool execution does not replay completed operations.
- Verify interrupted conversation deletion and plugin installation recover without deleting shared/external assets.
- Verify batch-edit conflicts and interruption recovery, and browser profile isolation, proxy application, JS result bounds and renderer recovery.

## Sources

- [Kotlin conventions](https://kotlinlang.org/docs/coding-conventions.html)
- [detekt complexity rules](https://detekt.dev/docs/rules/complexity/)
- [libsu](https://github.com/topjohnwu/libsu)
- [APatch](https://github.com/bmax121/APatch)
- [KernelSU](https://kernelsu.org/guide/what-is-kernelsu.html)
- [PRoot](https://proot-me.github.io/)
- [Android ARM build-tool reference](https://github.com/termux/android-build-tools)
- [NDK downloads](https://developer.android.com/ndk/downloads)
- [OpenAI prompt caching](https://developers.openai.com/api/docs/guides/prompt-caching)
- [OpenAI cache diagnostics](https://developers.openai.com/api/docs/guides/prompt-caching/diagnostics)
- [Anthropic thinking and caching](https://platform.claude.com/docs/en/build-with-claude/thinking)
- [Anthropic effort steering](https://platform.claude.com/docs/en/build-with-claude/thinking-steering-and-cost)
- [WebKit stable releases](https://developer.android.com/jetpack/androidx/releases/webkit)
- [WebView profiles](https://developer.android.com/reference/androidx/webkit/Profile)
- [WebView proxy scope](https://developer.android.com/reference/androidx/webkit/ProxyController)
- [WebView JavaScript execution](https://developer.android.com/reference/android/webkit/WebView#evaluateJavascript(java.lang.String,%20android.webkit.ValueCallback%3Cjava.lang.String%3E))
- [Playwright Android status](https://playwright.dev/docs/api/class-android)
- [Compose performance](https://developer.android.com/develop/ui/compose/performance/bestpractices)
- [Macrobenchmark](https://developer.android.com/topic/performance/benchmarking/macrobenchmark-overview)

## 2026-10-15 integrated UI revision — pending preview approval

User clarification supersedes the drawer new-conversation contract: remove the large new-conversation button from the drawer; retain new conversation in the main top-right conversation tools. A fresh installation must have empty history, without a seeded or test-created conversation. Never remove existing user conversations merely because their title is the default. Current startup opens an existing row; it does not itself create a conversation. QA-created empty rows must not be confused with clean-install behavior.

Proposed interaction for approval: expand conversation tools inline below the header and expand metric details within the composer instead of using generic centered dialogs. Use consistent typography, icon dimensions, shape and surface tokens across these surfaces. Previews are illustrative; implementation is not yet changed.

Reference source inspection: [FolkPatch themed menu](https://github.com/LyraVoid/FolkPatch/blob/main/app/src/main/java/me/bmax/apatch/ui/component/DropdownMenu.kt) centralizes shape, surface and elevation; [Eta conversation pane](https://github.com/Mangi-11/Eta/blob/main/app/src/main/kotlin/io/github/mangi/eta/ui/components/ConversationPanePanel.kt) uses shared Miuix components. Learn visual consistency without copying code or introducing a new UI framework solely for appearance.
### Preview feedback, 2026-10-15

The user rejected the first integrated UI preview as still visually behind DeepSeek Harness and Claude. Do not implement that preview. Keep the confirmed removal of the drawer new-conversation button and empty fresh-install history. Reconsider the overall typography, density, surface hierarchy and composer proportions before implementation. A replacement concept is being reviewed: fewer borders and colored icon tiles, one input surface without nested fields, secondary inline metrics, compact anchored metric details instead of a large inline expansion. The metric detail treatment is proposed, not approved.

Research: DeepSeek Harness official product demo https://www.deepseek.com/en/harness/ and `packages/client/ui-conversation/src/client/skeleton/InputBar.module.css`, `ContextMeter.module.css` in https://github.com/deepseek-ai/deepseek-harness (master). Composer and menus share design tokens; the context readout uses a compact anchored panel. Claude public overview https://claude.com/product/overview was inspected; the actual claude.ai chat redirects to login, so no claim to inspecting an authenticated current mobile UI.
### Approved chat direction; theme revision pending, 2026-10-15

The user approved the replacement calm chat / compact anchored metric panel / empty drawer concept generated as exec-0d21a553-be61-4389-9244-4a128cf375da.png. Keep Aurora's original icon and the top-right conversation-tools entry; generated flower/branding and demo text are not production assets. The user now requests a better appearance/theme settings design, referencing Eta and FolkPatch.

Current theme diagnosis: Aurora overrides only some Material color roles and derives accents with fixed interpolation, leaving several foreground/container roles fixed; this is not a complete per-theme light/dark palette. Proposed for review: full role-based theme pairs, coordinated live chat preview, light/dark/system thumbnails, mutually exclusive preset/system/custom color source, palette styles, optional pure-black dark background and local color picker with secondary hex input. The user confirmed colors only; do not add wallpaper in this revision. Keep existing palette preferences migratable. Do not copy GPL source into Aurora or adopt a new UI framework solely to imitate a setting page.

Sources inspected: Eta data/model/AppearanceSettings.kt and ui/AppearanceSettingsScreen.kt in https://github.com/Mangi-11/Eta ; FolkPatch AppearanceThemeSection.kt and core/designsystem/.../FolkThemeCatalog.kt in https://github.com/LyraVoid/FolkPatch . Eta separates mode, palette style and accent; FolkPatch enumerates full paired light/dark color roles.