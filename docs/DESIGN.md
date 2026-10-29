# Aurora design baseline

Status: product requirements and cache, batch tools, browser and architecture contracts approved. Actual implementation evidence is tracked separately in IMPLEMENTATION.md; this specification is not a performance claim.

Research date: 2026-10-12.

2026-10-18 approved UI supersession: [capsule comparison, right-side B selected](assets/approved-composer-B-20261018.png), with [conversation direction](assets/chat-direction-20261018.png). This overrides previous composer A and metric positions: thinking lightbulb, explicit model, context ring in controls; speed/input/cache outside. Default execution summary is collapsed. See COMPOSER_B_2026-10-18.md for implemented behavior and actual device evidence; concept numbers are illustrative.

## Approved architecture

- 2026-10-27 user requested comparison with mainstream Claude Skills practices and delegated the best choice after research. Select pinned BC1.86 lightweight Ed25519 validation to replace unavailable Android16 external-SPKI platform import. Preserve optional publisher trust, existing source/hash/version policies and v1 format; no global provider registration or credential/TLS backend change. See SKILLS_CRYPTO_PROVIDER_PROPOSAL.md for official comparisons and actual device evidence.

- 2026-10-26 confirmed Skills private GitHub repository API/PAT in existing KeyStore vault, repository-scoped credentials, pinned Ed25519 public-key verification, and source policy/evidence in the existing SQLite WAL. See SKILLS_TRUST_PROPOSAL.md and SKILLS_TRUST_FORMAT.md; device qualification remains separate.

- 2026-10-24 confirmed Root managed native tasks: independent SQLite active intents (schema8), sealed memfd snapshots and authenticated one-shot FD handoff, existing cgroup supervision with a Frida cleanup window, exact injection/worker/domain exit facts. No Linux image requirement or existing-target migration into Aurora cgroups. See ROOT_MANAGED_TASKS_PROPOSAL.md; implementation/qualification are separate.

- 2026-10-23 user clarified managed install lifecycle: publish complete bundles only, remove failed/cancelled staging, allow Agent install/update/remove and shared-workspace file management. Unknown live ownership/failed cleanup is explicit; active runtime versions require durable lease/exit proof before removal.

- 2026-10-23 user approved static reverse bundle: pinned JADX1.5.6/Apktool3.0.3/ARM64 Temurin27+35 JRE, shared versioned install with readonly guest mount and leases on existing Root chroot supervision. See STATIC_REVERSE_PROPOSAL.md; preparation is not runtime qualification.

- 2026-10-23 confirmed combined browser transfers: native chooser with exact selected snapshot URI, bounded ArrayBuffer Agent supply/page-readable streaming, existing task/file ownership and no second network stack. Native snapshots remain until page close/deletion to cover deferred upload reads; implementation/qualification in BROWSER_CHOOSER_IMPLEMENTATION.md and BROWSER_TRANSFER_IMPLEMENTATION.md.

- 2026-10-23 confirmed browser async results: per-invocation system WebMessagePort, exact top-level HTTPS target origin and document revision, event-driven results and bounded lifetime; no privileged JavaScript interface or polling fallback. See BROWSER_ASYNC_PROPOSAL.md; device qualification is separate.

- 2026-10-23 confirmed combined Root packet stack: pinned official stable libpcap for kernel capture/filtering, Aurora native worker for bounded live PCAP writing, tcpdump for mature offline reading, Aurora for Agent/task/artifact integration. One active capture chain, no VPN/AF_XDP or extra daemon implied. See ROOT_CAPTURE_PROPOSAL.md; packaging and runtime qualification are separate.

- 2026-10-22 confirmed: explicit bounded Root runtime metadata maintenance, only
  for proven-dead old service epochs without pending SQLite run intents and with
  native stopped proof; retain current/ambiguous state and owner/workspace lock
  barriers. See LINUX_METADATA_GC_PROPOSAL.md; qualification is tracked separately.

- 2026-10-22 confirmed: preserve Root Linux conversation file ownership; route on-demand batch file access, selected image/export publication and deletion through Aurora's built-in Root service. Do not recursively chown the workspace after every command. Keep deletion pending when Root is unavailable. See LINUX_WORKSPACE_ACCESS_PROPOSAL.md and LINUX_ROOT_FILES.md.

- 2026-10-22 confirmed: on-demand shared backend generations: Root chroot preserves original Linux ownership, ordinary PRoot preserves App ownership; Root users default to only the Root generation. No per-conversation rootfs copies, no mandatory OverlayFS; explicit dual-generation storage costs and Root cleanup requirements. See LINUX_OWNERSHIP_PROPOSAL.md.

- 2026-10-22 superseding confirmation after device qualification: Root Linux uses task-owned cgroup v2 scopes for lifecycle teardown, alongside private mount namespaces and subreaper/pidfd/event observation. PID namespaces are optional because the tested Android kernel does not expose them. No resident daemon or global resource-policy changes. See LINUX_CGROUP_PROPOSAL.md. Full execution remains unfinished.

- 2026-10-22 confirmed: Ubuntu Base26.10 same-architecture environment, Root-qualified private-mount-namespace chroot preferred, PRoot5.1.107.98 on demand for ordinary UID, shared version generations/cache and conversation-owned writable homes. See LINUX_PROPOSAL.md. Qualification and runtime implementation are separate from downloading a rootfs.

- 2026-10-21 confirmed: standard SKILL.md, versioned content-addressed shared resources, existing SQLite index/recovery/pins, progressive Agent loading and native management. Dependencies track verified stable releases; active tasks retain pinned versions. See SKILLS_IMPLEMENTATION.md.

- 2026-10-21 confirmed: on-demand native PTY, pinned independent Termux terminal components, Aurora-owned sessions and Agent tools, Root Binder control/FD transport, reuse the foreground task service. See INTERACTIVE_TERMINALS.md. Ordinary commands retain their pipe executor.

- Kotlin is the primary implementation language; the protocol/file core is independent of Android UI.
- Jetpack Compose and Material 3 render the native application. WebView is the browsing engine, not the application UI renderer.
- Privileged Android work uses libsu RootService and Binder after authorization; the ordinary path remains available without Root.
- The app uses the device's existing Linux kernel. No custom kernel, ROM replacement or handset-specific modification is part of the baseline.
- Android-native execution, PRoot and Root chroot are separate execution backends. PRoot is not inserted into every tool or model request.
- NDK C/C++ is used where a native tool or measured bottleneck requires it, rather than rewriting all orchestration into a native layer.
- Latest stable versions are pinned reproducibly and tested as a combination. Android 16 remains the minimum even when compiling and targeting a newer stable SDK.
- Persistence uses Android-native SQLite with WAL and append-only message/native-history records, confirmed on 2026-10-12.
- 2026-10-21 confirmed: bounded partial CPU wake leases belong to active native/Root commands, never the whole Agent/model wait. See COMMAND_POWER.md; no policy bypass or persistent daemon.
- 2026-10-21 confirmed: native Compose Markdown, Coil3 shared decoding, and conversation-owned image artifacts. Agent image publication is independent of provider vision/image-generation APIs. See RICH_CONTENT_PLAN.md.
- 2026-10-21 confirmed: event-driven viewing of existing command artifacts with FileObserver, no recurring polling/extra daemon. Reuse the command start ID for persisted artifact ownership; see LIVE_COMMAND_LOGS.md.

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
## 0.1.3 implementation checkpoint

The latest calm chat concept and colors-only appearance layout are approved and implemented in 0.1.3; earlier pending/rejected preview sections above are historical. Aurora is agent-first: the user chats and watches, while available file and browser operations are routed through the model tool loop. Current browser Agent tools reuse the approved WebView architecture; this does not establish full phone autonomy. Implementation and measured limits are tracked in PROGRESS.md and IMPLEMENTATION.md.

2026-10-16 composer amendment: the user approved placing speed, cache and context in a separate row below the rounded composer, following their supplied reference. This supersedes the earlier inside-composer placement. Keep details locally anchored and metrics based on actual provider observations.

2026-10-16 approved interactive revision: compact single-surface composer without a nested model field, lightbulb reasoning control, external icon metrics with inline expansion, quieter left-aligned empty state, neutral message surfaces, step-line execution details and bottom-sheet conversation tools. See CHAT_REFINEMENT_2026-10-16.md. Model icons and actual IDs remain; demo tasks and numbers are not production data.


2026-10-28：真实Root/chroot静态逆向5项及代理2项通过；核实原生workspace/project挂载于guest /mnt，修正代理公私有产物、受票据约束的清理及SDK探针路径，ARM64产品脚本同步实际挂载。固定180MiB输入安装通过，APK手机构建/JNI尚未通过；断连批次明确exit1/scopeRemoved并保留自有缓存继续。当前默认224任务构建/lint通过，354核心/232 Android编译JUnit签名检查通过；dbb5a0a CI40524669781成功。精确证据见DEVICE_ACCEPTANCE_20261027的10月28日接续，不调用付费模型或读取正式密钥。


2026-10-28续：8078f46完整CI40685540287、代理ARM64产品40685566575、SDK ARM64产品40685573229全部成功，报告归档。手机标准SDK探针经预热依赖缓存41.816秒通过，真实Compose/双ABI JNI构建、16KiB/ELF检查、Root导出及普通UID实际nativeMarker=owned-42，随后精确删除自有测试会话。此前冷缓存270秒超时仍失败，不冒充首跑资格。诊断旧项目assembleRelease99秒成功；诊断不授予运行资格，不扩大270秒预算。新增详细诊断/失败夹具保留、编译和lint108任务通过。


2026-10-28长时采集接续：实际1800秒入口发现RootManagedProcessWait仍限275秒，启动后立即抛参量异常。增加采集专用awaitCapture上限1,825,000毫秒（已批准1800秒＋25秒现有退出预算），Frida/default await仍275秒；不改普通命令、票据、持久格式或取消证明。Binder命令级不确定响应转成无exit/cleanup事实的managed_outcome_uncertain，保留intent，不再误解为缺少runId的序列化异常。两项核心回归通过；356核心340通过/16跳过，完整Debug/Release/R8/AndroidTest/lint224任务成功，356/233编译JUnit均void。已同签名覆盖并开始实际30分钟亮屏自有loopback测试，目前运行中，尚未通过。


2026-10-29 approved Frida policy qualification: the user explicitly approved the minimal per-boot route in FRIDA_ANDROID_POLICY_PROPOSAL.md. Reuse already pinned SDK/libsepol; keep Enforcing, dedicated object types/rules only, no broad tmpfs execute/domain execmem, persistent module or automatic whole-policy restoration. Implementation/qualification are tracked separately; a successful host build does not enable adaptation. Actual kernel readback needs semantic delta verification as its serialized bytes differ from the submitted image. The bridge evaluation acknowledgment may order spawn resume, but is not a cleanup or user-script business-success proof.
