# Mika UI/UX implementation progress

- Repository: `Ichiro786/Mika`
- Active branch: `mika-uiux-phase-00`
- Baseline commit: `4ade16b1dac36b1c893c066a6d408be4ba76114c`
- Current phase: **00 recovery and 01 — foundation; static identity started**
- Baseline compile/tests: **Blocked: JDK unavailable**
- Device test: **Unverified: no device/emulator**
- Overall status: **Phase 00 and Phase 01 gates open: build and device verification unavailable**

| Phase | Workstream | Status | Commit | Evidence / blockers |
|---|---|---|---|---|
| 00 | Baseline and safety rails | Open | — | Inventory and i18n checks recorded; build and device baselines blocked |
| 01 | Theme and design tokens | In progress | — | Foundation implemented; build and visual verification open |
| 02 | Orbit Spark vector | Started | — | Static component only; visual verification open |
| 03 | Agent presence animations | Pending | — | — |
| 04 | App shell and navigation | Pending | — | — |
| 05 | Home and composer | Pending | — | — |
| 06 | Chat and activity | Pending | — | — |
| 07 | Tasks and schedules | Pending | — | — |
| 08 | Approval cards | Pending | — | — |
| 09 | Make and skills | Pending | — | — |
| 10 | Capsule, voice and system assets | Pending | — | — |
| 11 | Secondary screens and accessibility | Pending | — | — |
| 12 | Performance and release candidate | Pending | — | — |

## Findings needing verification before implementation

- Custom navigation and predictive back might be a risk, not a proven critical defect.
- Markdown O(N²) hypothesis must be profiled before rewriting the parser.
- Keep per-state animation claims grounded in actual signals; some concept states may lack direct engine state equivalents.
- Model-provider authentication should remain untouched unless an independently proven regression exists.

## Change record

Append a row/note after each completed phase with date, checks actually run, captures, exact commit, changes and remaining risks.

## Phase 00 evidence — 2026-11-13

**Status: OPEN — baseline build and Android runtime gates are unverified.** No runtime code was changed. Initial checkout was clean, detached at `4ade16b1dac36b1c893c066a6d408be4ba76114c`, matching the audit's snapshot. Branch: `mika-uiux-phase-00`. The user-supplied standing workflow also applies alongside the program's root `AGENTS.md`.

### Architecture and dependency inventory

- `:core` is Kotlin/JVM agent and supporting logic; `:app` is Android/Compose and depends on `:core`. Kotlin 2.1.20, AGP 8.7.3, Compose BOM 2024.12.01, Material3 via BOM, Gradle 8.11.1; Java 17 toolchain, compile SDK 36, min SDK 30, target SDK 35. The resolved Material3 version and Expressive API compatibility remain unverified; do not upgrade without the Phase 01 review.
- `ui/Navigation.kt` owns a saved custom `NavStack`, rooted at Chat, with drawer reopening and reset semantics. Routes: Chat, Personal, Settings, Memory, Plans, Browser, Files, Run, Source, PhoneSetup, Models, Usage, SpeechModel, Welcome, ModelSetup, About, License, Screens, Teach, Calls. Preserve names, entry paths and back/restoration behavior.
- `ui/ChatScreen.kt` connects composer send/cancel, attachments and hold-to-talk to its view model; conversation persistence is in `chat/ConversationStore.kt`. Preserve session IDs, drafts, history, streaming, branch/quote, retry, attachments, voice, interjections and source/run inspection.
- `ui/theme/Theme.kt` bridges custom `MikaColors` to Material3 and follows saved system/light/dark and accent choices, skin accents, RTL and reduced-motion support. Preserve live appearance changes and existing preferences.
- `task/TaskService.kt` manages task state and confirmation/takeover actions; `capsule/CapsuleController.kt` observes task and takeover state. Preserve explicit approve/decline, takeover ordering, overlay touch/IME behavior, scheduling, persistence and background lifecycles. `settings/ProviderStore.kt` manages provider configurations and encrypted keys and uses token providers for sign-in; no credential values were inspected or logged. Preserve login, refresh, model selection and authentication.
- Reference images and HTML, audit and program documents are stored under `docs/design-references/` and `docs/MIKA_UIUX_*.md`. All eight supplied images were viewed; the audit is a hypothesis source, not runtime proof. Existing screenshot is supplied context, not a capture of this checkout.

### Actual checks

- `python3 tools/i18n/translations.py check`: **PASS**, 2,808 resources across 11 UI languages.
- `python3 -m unittest discover -s tools/i18n -p 'test_*.py'`: initially 12 setup errors because the read-only sandbox provided no writable temporary directory; rerun with temporary-file access: **PASS**, 12 tests.
- `./gradlew :core:test :app:testDebugUnitTest :app:assembleDebug`: **NOT RUN**: wrapper exited before Gradle started, `JAVA_HOME` unset and `java` unavailable. No JDK found in inspected standard locations. No Android SDK, `adb`, emulator, or `local.properties` found in inspected locations. CI specifies JDK 17, Android platform 36 and build-tools 35.0.0. No dependencies were changed or installed.
- CI additionally specifies `:app:compileDebugAndroidTestKotlin`, `:core:cliTest`, and `:core:relayTest`; these are **NOT RUN**. Instrumentation, actual light/dark screenshots, accessibility, contrast, lifecycle and device regression checks are **UNVERIFIED**. No before/after Android screenshots were captured.

### Risk ledger and next checks

1. Navigation, system back, deep links, self-operation routes and drawer restoration: validate on device before replacing shell.
2. Chat streaming, drafts, history, attachments, voice and mid-run controls: exercise on a working configured build; profile Markdown before acting on audit performance claims.
3. Confirmations, payments, takeover and capsule: retain existing authority and test explicit approval/decline, overlay touch, IME and process recovery.
4. Provider secrets, sign-in, networking, automation, scheduling and `:core`: preserve unchanged; verify with appropriate credentials and device without logging secrets.
5. Theme, accessibility, localization and motion: preserve current preferences, RTL and reduced motion; test 200% font, TalkBack, contrast and actual renders.
6. Restore JDK 17 and Android SDK 36/build tools in an authorized environment, then run CI tasks above and capture light/dark device baselines. Record actual results before advancing the open Phase 00 gate.

Rollback: remove only the newly added program documents/references and root program instructions; no application or runtime files were changed. Phase 01 (tokens and isolated previews) has not started.

## Phase 00 recovery and Phase 01 / static identity — 2026-11-13

**Status: implementation in progress; build, device and visual gates remain OPEN.** The user explicitly authorized beginning Phase 01 and static identity while Phase 00 environment gates are open.

- Existing `.github/workflows/ci.yml` already specifies JDK 17, Android platform 36/build-tools 35.0.0, Gradle caching, `:core:test`, `:app:testDebugUnitTest`, `:app:assembleDebug`, device-test compilation and localization validation. `test-apk.yml` separately uploads a debug APK. Parsed both workflow files and checked required configuration with Python/PyYAML; this is a static check, not a GitHub Actions run. `gh auth status` reports an invalid GitHub token; no CI run was dispatched or verified. No workflow change was necessary.
- The user supplied and authorized a repository-local Git author identity after the initial commit attempt failed. No push was attempted.
- `ui/theme/Colors.kt`: warm light and true-black dark base colors and readable neutral text; existing accent selection and skin accent behavior retained. The default accent remains blue because the existing preference identifies it as blue; coral appears in the identity mark, not as a silent replacement of that preference.
- `ui/theme/Tokens.kt` and `Theme.kt`: editorial headline, spacing and rounded shape tokens, Material typography mapping, opaque semantic surface variant, and reduced-motion-aware expressive spatial/effects spring specs. Existing shared card/button components use the existing tokens. Existing navigation, service and page motion contracts remain in place.
- `ui/identity/MikaMark.kt`: standalone scalable Canvas orb and ribbon, light/dark ribbon variants and static `MikaPresenceAvatar` entry point. No animation states, screen integration or runtime bitmap assets. Geometry is an initial implementation; **visual fidelity is unverified**.
- `EditorialPaletteTest` asserts warm and black backgrounds for every existing accent preference. Attempted `./gradlew :app:testDebugUnitTest --tests com.wangzan101.mika.ui.theme.EditorialPaletteTest`: **NOT RUN**, no Java. Compile and all Android/JVM tests remain unverified. Re-ran localization check: **PASS**, 2,808 resources / 11 languages; i18n unit tests: **PASS**, 12 tests. Static sRGB calculations for muted ink on page/answer surfaces: light 5.35:1 / 4.89:1; dark 8.77:1 / 6.73:1. These are limited color-pair checks, not a complete accessibility audit.
- Screenshots, emulator/device tests, actual light/dark comparison, 200% text, TalkBack, preview lab, resolved Material3 version and runtime animation verification are **UNVERIFIED**. No Expressive library upgrade or experimental API was introduced. Before considering Phase 01 or identity complete, run CI on these changes, verify Gradle tests and compilation, add/capture actual previews, and review the mark against references at multiple sizes.
