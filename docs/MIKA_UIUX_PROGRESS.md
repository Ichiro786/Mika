# Mika UI/UX implementation progress

- Repository: `Ichiro786/Mika`
- Active branch: `mika-uiux-phase-00`
- Baseline commit: `4ade16b1dac36b1c893c066a6d408be4ba76114c`
- Current phase: **Tasks, navigation and approvals — device verification open**
- Baseline compile/tests: **PASS in GitHub Actions; most recent recorded passing source commit 8a4589aa**
- Device test: **Unverified: no device/emulator**
- Overall status: **CI results recorded by commit below; device verification remains open**

| Phase | Workstream | Status | Commit | Evidence / blockers |
|---|---|---|---|---|
| 00 | Baseline and safety rails | Open | — | Inventory and i18n checks recorded; build and device baselines blocked |
| 01 | Theme and design tokens | In progress | — | Foundation implemented; build and visual verification open |
| 02 | Orbit Spark vector | In progress | — | Reused scalable mark; actual visual verification open |
| 03 | Agent presence animations | In progress | — | Runtime state adapter and motion added; device verification open |
| 04 | App shell and navigation | In progress | — | Primary navigation implemented; CI passed at 8a4589aa; device checks pending |
| 05 | Home and composer | In progress | — | Chat presentation added; device verification open |
| 06 | Chat and activity | In progress | — | Message and activity styling added; device verification open |
| 07 | Tasks and schedules | In progress | — | Real task and schedule views implemented; CI passed at 8a4589aa; device checks pending |
| 08 | Approval cards | In progress | — | Keyed confirmation card implemented; CI passed at 8a4589aa; device checks pending |
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

## Orbit Spark and chat milestone — 2026-11-13

**Status: implementation prepared; Android build, device regressions and visual/motion verification remain OPEN.** Base: `15fb0df8763478d15da38c7c489ef814943e582b`, branch `mika-uiux-phase-00`. The user explicitly requested this combined milestone across identity and chat; no other screen or navigation redesign was started.

- Reused the existing warm/AMOLED palette, typography, shapes, static Canvas mark, composer, Markdown renderer, navigation and approval controls. `MikaPresenceAvatar` adds lifecycle-aware and reduced-motion-aware idle, listening, thinking, working, needs-user and success presentations, plus a static error indicator. Needs-user and success pulse once per state transition and do not replay merely on foregrounding.
- `presenceFor` derives presentation only: listening requires an active hold-to-talk `Listening` state that has not ended; thinking requires a live exchange with actual streamed draft text and the existing model-wait signal; working uses a non-null tool in the live `TaskState.Running`; confirmation, input and takeover use their existing statuses; success and error use live terminal statuses. Old conversation history, queued work and ambiguous preparation do not trigger activity. The existing waiting flag is inferred from task fields, so no thinking animation is shown before streamed text establishes a model response. A running tool name indicates a tool is being handled, not proof an external action succeeded.
- Chat retains its existing send, stop, attachments, voice, drafts, streaming, message menus, source/run links, takeover and approval handlers. The home shows existing recommended tasks and, only when present, an actual count of tasks in other conversations linked to the existing drawer. User bubbles use warm/dark coral, the floating composer has chat-only styling, and live activity steps are compact and expandable. No synthetic briefing, navigation tabs or agent data were added.
- Changed: `ui/ChatScreen.kt`, `ui/ChatViewModel.kt` (UI-only tool-state flag), `ui/PresenceState.kt`, `ui/components/Controls.kt` (optional chat title icon), `ui/identity/MikaMark.kt`, `ui/identity/MikaPresenceAvatar.kt`, `app/src/test/.../PresenceStateTest.kt`, and this tracker. Existing shared composer defaults remain unchanged outside chat; `:core`, authentication, networking, task execution and overlay lifecycle files were not edited.
- `python3 tools/i18n/translations.py check`: **PASS**, 2,808 resources across 11 UI languages. `python3 -m unittest discover -s tools/i18n -p 'test_*.py'`: **PASS**, 12 tests. `git diff --check`: **PASS**. New user-message text/background contrast calculated at 11.56:1 light and 9.39:1 dark; this is not a complete accessibility audit.
- `./gradlew :app:testDebugUnitTest --tests com.wangzan101.mika.ui.PresenceStateTest` and `./gradlew :core:test :app:testDebugUnitTest :app:assembleDebug :app:compileDebugAndroidTestKotlin`: **NOT RUN**, wrapper stopped because Java/JAVA_HOME is unavailable. The new unit tests and all Android compilation remain unverified. GitHub authentication was invalid; no remote CI run was dispatched or verified. No actual before/after screenshots, animations, emulator or physical-device tests were captured.
- Before shipping: run the listed Gradle and existing device regressions in CI/on Android; inspect the six states and reduced-motion/background behavior; compare light/dark renders to the boards at multiple sizes and 200% font; exercise voice, attachments, streaming, approval, long-press actions and IME/insets. In particular, validate live-step expand/collapse and the home briefing in real UI. Roll back this milestone with its single commit if regressions arise.


## GitHub Actions Android build gate — 2026-11-13

- `.github/workflows/android-ci.yml` runs on pushes, pull requests and manual dispatch. It calls the existing `ci.yml` with `android_only: true`, reusing its Ubuntu runners, Temurin 17, SDK 36/build tools 35.0.0, Gradle cache, core tests, app unit tests, debug build and localization checks. The reusable path skips the unrelated CLI and relay integration tests; the original CI keeps those tests and its original triggers.
- The existing app job now uploads `app/build/outputs/apk/debug/*.apk` as `Mika-debug-<commit>` after a successful build. No APK has been generated or uploaded yet. The existing and Android entry points both run on pull requests and main pushes; this preserves existing checks at the cost of duplicate runs on those events.
- Validation: actionlint 1.7.12 passed on both workflow files; a PyYAML structural check passed for triggers, reusable inputs, JDK, SDK, cache, test commands, localization and APK upload. `python3 tools/i18n/translations.py check` passed (2,808 resources, 11 UI languages). These are static/local checks, not an Actions execution.
- Before the user designated GitHub Actions as the sole Android build environment, a project-local JDK 17 and Android command-line tools were downloaded and checksum-verified under ignored `build/`; SDK package and Gradle downloads failed with a Java proxy handshake error. Local toolchain setup stopped at the user's direction. GitHub authentication remains invalid, so CI has not been dispatched or verified and no screenshots exist.
- Next gate: restore GitHub authentication, push the committed branch only when authorized, inspect the Android CI run and download its APK. Fix any compilation/test failures and verify actual Android renders before implementing the Tasks/navigation/approval milestone.


## CI trigger consolidation — 2026-11-13

- `android-ci.yml` is the only automatic entry point among the two workflows. It calls the full `ci.yml` suite on pushes and pull requests; both workflows retain manual dispatch and the debug APK upload remains in the app job. Core, app, instrumentation compilation, localization, CLI and relay checks remain enabled.
- A branch push with a known mergeable open PR yields to the PR run. Default-branch pushes, tags, manual runs and PR events run the full suite. PR lookup errors, unknown mergeability and conflicts fall back to running the push suite to avoid losing validation; an occasional duplicate is possible in those fallback cases. Existing specialized dev-APK workflows for their own named branches remain separate.
- `actionlint` 1.7.12 and static workflow structure checks passed. Mocked PR lookup checks covered no PR, open PR and API failure; GitHub authentication now succeeds. Android build/test and actual CI outcomes remain unverified until the pushed commit finishes on GitHub.


## GitHub Actions baseline recovery — 2026-11-13

- Push run 43400356724 at commit `b51999c24090dead0358903bd3cee1a96d9e29c1`: Android app unit tests, debug APK assembly and device-test compilation **passed**; Python CLI, localization and Go relay jobs **passed**. The APK artifact is `Mika-debug-b51999c24090dead0358903bd3cee1a96d9e29c1` in that run. No Android device tests or screenshots were run.
- Core tests **failed**: 3,274 tests, one failure and two skipped. The failing documentation consistency test treated names in the supplied audit and design plans as claims about existing repository classes. Existing names, external Android APIs and proposed concepts retain their displayed code formatting using HTML code tags; the document text and the core check remain intact. A new GitHub Actions run is required to verify the correction.
- No local Android builds were attempted. Visual fidelity, navigation, approvals and device behavior remain unverified.


## Tasks, navigation and approvals milestone — 2026-11-13

- Baseline GitHub Actions run 43402986805 **passed** at commit `68d06720f089bd9c9b5291869fffd338718743f7`: core, Android unit tests, debug APK, Android-test compilation, localization, CLI and relay. APK artifact: `Mika-debug-68d06720f089bd9c9b5291869fffd338718743f7`. This result predates the changes below.
- Added a Tasks surface using personal tasks, current runs, reachable persisted run records and enabled schedules. Actual confirmation requests are distinguished from other user-attention states; progress bars use only reported completed/total counts. Existing task details, replay, stop, schedule actions and conversations remain the action targets. Loading, empty and read-error states are shown.
- Added Chat / Tasks / Make navigation within the saved route stack, retaining the drawer and history; Make links to existing teaching, skills, browser and files. The bar hides while the keyboard is open. No provider, task engine, scheduling service, overlay or core files changed.
- The confirmation card displays existing request details and calls the existing keyed approve/decline handlers. Its local guard prevents repeated taps while a request is pending; a synchronous dispatch error re-enables the buttons. Runtime state, not the card, determines the eventual outcome.
- Local static checks: translation validation **PASS** (2,819 resources across 11 languages), i18n unit tests **PASS** (12 tests), actionlint **PASS**, and `git diff --check` **PASS**. New Kotlin tests cover task grouping, genuine progress, navigation and repeat submissions; they await GitHub Actions. No local Android build was attempted.
- Current changes have not yet been compiled or rendered. Device tests, screenshots, actual edge-to-edge/IME behavior, large text, TalkBack, task action flows and approval execution remain **UNVERIFIED**. Review light and AMOLED captures against the supplied boards before accepting visual fidelity.

- Review follow-up: the existing UI adapter for opening a personal task rebuilt the composer and could discard drafts. It now keeps and restores per-conversation text, attachments and quotes, with a regression test. New light-theme attention text measures 6.12:1 on the page and 6.44:1 on the card; approval button text measures 14.99:1 light and 18.45:1 dark. These are limited static color checks, not a device accessibility audit. The follow-up changes also await CI.

- GitHub Actions run 43405454116 **passed** for milestone commit `3b7c83e696a4b92200d54c3ae75288f87d5a11ba`: core, Android unit tests, debug APK and Android-test compilation, CLI/localization, and relay. Its APK artifact is `Mika-debug-3b7c83e696a4b92200d54c3ae75288f87d5a11ba`. The subsequent draft-preservation and contrast follow-up is not covered by this run and requires its own CI result.

- Task rows ignore changes that only add streamed reply text; chat still receives the original stream. A presentation regression test covers this mapping; the GitHub Actions run below passed.

- Added instrumentation tests for primary-navigation selection and for approval/decline card behavior; GitHub CI compiles device tests but does not run them.

- GitHub Actions run 43406406518 **passed** for commit `27ca52eb874c43784c6fe087da48e3c188f31211`, including the draft/quote/attachment preservation regression, core and app unit tests, Android debug build and device-test compilation, CLI/localization and relay. Its APK artifact is `Mika-debug-27ca52eb874c43784c6fe087da48e3c188f31211`. The later streaming and device-test additions require the Actions result for their own commit.

- GitHub Actions run 43407595152 **passed** for source commit `8a4589aa32d323a6ee81100b2253d25fa5c9333b`: core tests, app unit tests, debug APK assembly, Android-test compilation, CLI/localization and relay. APK artifact: `Mika-debug-8a4589aa32d323a6ee81100b2253d25fa5c9333b`. The new Android device tests were compiled, not executed. No Android screenshots were captured.

## Visual correction pass — 2026-11-13

- Compared the three user-supplied current AMOLED screenshots with the approved light, AMOLED and Orbit Spark concept boards before editing. The current home used an off-center mark beside a wordmark and a large recommendation list; Tasks hid its second tab and used a blue add button; Make showed ungrouped rows. The current screenshots are in the conversation, not files in this checkout.
- Centered a framed, softly glowing live presence in Chat and matched the static header mark in Tasks and Make. Chat now presents real saved recommendations in a feature card and secondary card, with the remaining suggestions accessible via More; selecting one still uses the existing draft-preservation path. No generated briefing or sample task data was added.
- Corrected Tasks tab sizing, section treatment and coral accents; grouped the existing Make destinations and refined the floating navigation. Existing navigation callbacks, tasks, approvals, composer, voice and reduced-motion state mapping remain in use.
- Added Android device regression cases for tab visibility/selection, true header centering and expanded recommendations. Localization validation **PASS** (2,821 resources, 11 languages); translation tool tests **PASS** (12); actionlint **PASS**; `git diff --check` **PASS**. The sandbox initially prevented temporary files for the translation tests; they passed when rerun with temporary-file access.
- This pass requires GitHub CI on its own commit. New device tests, updated Android screenshots and actual light/dark visual, voice, accessibility and motion checks remain **UNVERIFIED** until run on Android. No Android build or emulator was run in Muse.

- GitHub Actions run 43446704262 **passed** for visual correction commit `2deec4cea1fbae5b5ff676a734a092d1a36a1ef0`: core and app unit tests, debug APK assembly, device-test compilation, CLI/localization and relay. The device tests were compiled, not run. A manual-only GitHub CI option has been prepared to run focused Android interaction tests and capture the three primary screens on a clean emulator; it has not yet been run.

- Manual capture run 43448333615 built the APK and passed core, app unit tests, CLI/localization and relay, but the run **failed** at emulator creation: the selected device profile could not be loaded, and no virtual device or screenshots were produced. Removed that optional profile for a retry. Device tests and updated renders remain unverified.

## Orbit branding, identity and personal hub — 2026-11-13

- The user authorized this combined milestone. Public app resources now use Orbit while upstream project names, integration literals, package/application IDs, storage, provider contracts and licensing remain intact. New hub and identity labels use documented English fallback text in the other ten locales until reviewed translations are available; existing saved user text is not rewritten.
- Added adaptive launcher foreground/background and API 33 themed monochrome geometry, platform splash resources, a notification small icon, a shared persistent Original/Monochrome identity preference and preview, and a theme-aware in-app vector/presence palette. The Android launcher and system splash use static resources and do not follow the in-app identity preference.
- Added an animated personal hub over the existing primary screen, with real task and schedule state and entry points to the existing browser, memory/skills, files and settings. Header presence uses existing runtime-state mapping and voice signal. The original chat, task actions, approvals, navigation contracts and data stores remain in place. Reduced motion skips hub transitions and existing presence motion.
- Chat recommendations are labeled as recommendations rather than a fabricated briefing; the task title and tabs adapt to narrow widths. New preference/palette tests and branding-dependent assertion updates are included.
- Local checks before Android CI: translation validation **PASS** (2,843 resources across 11 UI languages), i18n tool tests **PASS** (12), XML parsing **PASS** (547 resources), actionlint and `git diff --check` **PASS**. No Android build or emulator was run in Muse. GitHub compilation, runtime behavior, screenshots, launcher masking, splash, accessibility, animation and visual fidelity remain **UNVERIFIED** pending this commit's CI and device checks.
- Previous manual screenshot retries 43448333615 and 43450885376 both failed at emulator creation; neither produced screenshots. Some upstream/core-generated messages and existing user-authored content can still contain the historical Mika name; upstream license and external CLI/key identifiers intentionally remain unchanged.
- The existing updater still checks the upstream Mika GitHub releases, not an Orbit release feed. Its visible update labels identify that source using English fallback copy in all locales; the updater, its network endpoints and user preferences are unchanged. Release ownership and signing must be settled before shipping Orbit.

- GitHub Actions run 43466558493 compiled the Android source and reached app unit tests, but the app job **FAILED**: 4 of 2,146 tests still expected the previous localized product name. Core, CLI/localization and relay jobs **PASSED**. The affected expected text and the intentional upstream tool-label exception were corrected; this does not establish a passing Android build or APK. A new CI run is required for the corrections and follow-up hub changes.
- The identity preference default/persistence/observation tests, both palette tests and all three presence-state tests passed in that failed run. The app's built-in Chinese call greeting now says Orbit; only the two exact historical default greetings are mapped to the new default, while custom greetings remain unchanged. This narrow branding change does not change call authorization or controls and awaits CI.
- Hub section follow-up reads recent browser pages, available memory and learned skills, saved file summaries, and current appearance from the existing stores. Actions continue to open the original screens; partial/unreadable stores show their existing error text. This follow-up awaits a new Android CI run and device review.

- GitHub Actions run 43468383851 **PASSED** for commit `ad713c47097b8922615533b99732144869183ff6`: core and Android unit tests, debug APK assembly, device-test compilation, CLI/localization and relay. APK artifact: `Mika-debug-ad713c47097b8922615533b99732144869183ff6` (artifact 14058179853). Device tests were compiled, not executed. The later hub section previews and hub interaction tests are not included in this result and require their own CI run.

- GitHub Actions run 43469784265 **PASSED** for hub integration commit `e01edf7d047e94ea42118c63fc845d6dcd2f0b0d`: core and Android unit tests, debug APK assembly, Android-test compilation, CLI/localization and relay. APK artifact: `Mika-debug-e01edf7d047e94ea42118c63fc845d6dcd2f0b0d` (artifact 14060761535). The new hub device tests were compiled but not executed; no Android render or screenshot was available. A later call-status copy-only follow-up requires its own CI run.

- GitHub Actions run 43494762274 for commit `23d21733e6d2c6a51d02a6d3284c039148f7e51a` **FAILED** in app unit tests: 1 of 2,146 tests failed. A background browser-download test coroutine outlived its test and called unmocked Android logging; its exception surfaced in an unrelated capsule preview test. Core, CLI/localization and relay **PASSED**. The download test now waits for its own work and reports background errors in the originating test; this test-only correction requires CI.
- The MCP OAuth completion page still contains the historical Mika name; the authentication implementation was left unchanged. The new Orbit identity preference does not alter the static launcher or system splash. Device screenshots, launcher masking, theme and identity switching, hub navigation, voice, approvals and reduced motion remain unverified on Android.
- Latest local checks: localization validation **PASS** (2,845 resources across 11 UI languages), workflow actionlint **PASS**, translation tool tests **PASS** (12), XML parsing **PASS** (548 resources) and `git diff --check` **PASS**.

- GitHub Actions run 43496124615 **PASSED** for commit `5b228d3b2fb49ea09bb3d7216080fbe92a494ea4`: core unit tests, Android app unit tests, debug APK assembly and Android-test compilation, CLI/localization and relay. APK artifact: `Mika-debug-5b228d3b2fb49ea09bb3d7216080fbe92a494ea4` (artifact 14073090446). The Android device tests were compiled, not executed; visual and device verification remain open.

- Hub transition follow-up: the presence now grows and moves between its compact header and expanded hub positions in sync with the panel fade; reduced motion snaps to the target. This source change requires its own CI and Android motion review.

- GitHub Actions run 43498113257 **PASSED** for transition commit `7daa9bdc6367065e264acffff1710e3cb2dc6cea`: core and Android app unit tests, debug APK assembly, Android-test compilation, CLI/localization and relay. APK artifact: `Mika-debug-7daa9bdc6367065e264acffff1710e3cb2dc6cea` (artifact 14073566214). Android interaction tests and visual comparison remain unexecuted.

## Conversation workspaces — stage A, 2026-11-13

- Removed duplicate browser, files, memory and schedules drawer links after checking their existing hub, Make and Tasks entry points. Preserved personal-agent, running screens, conversation history, archive, settings and quit access. Side Chats and Channels are not implemented in this stage.
- Hub tabs are icon-only with accessible labels and selected state; tabs stay available while the presence collapses into the header. Memory and skill summaries use separate existing-data cards. Updated browser navigation and hub accessibility device tests; they require Android execution.
- Local localization validation **PASS** (2,845 resources, 11 languages), localization tool tests **PASS** (12), and `git diff --check` **PASS**. Android compilation and device tests await GitHub Actions; no new screenshots or visual/performance checks were possible in Muse.

- Stage A GitHub Actions run 43507619564 **FAILED** at Android compilation: the collapsing header passed a general animation specification where Compose requires a finite float specification. Core, CLI/localization and relay jobs passed. The header now uses an explicit finite specification; a new Android CI run is required.

- Stage A GitHub Actions run 43508864408 **PASSED** for commit `cf3b6c9d85c208f2b14b1d27895affd1cb5bc105`: core and Android unit tests, debug APK assembly, Android-test compilation, CLI/localization and relay. APK artifact: `Mika-debug-cf3b6c9d85c208f2b14b1d27895affd1cb5bc105` (artifact 14076543758). The new device tests were compiled, not run; no Android screenshots were captured.

## Conversation workspaces — stage B, 2026-11-13

- The tests-first Actions run 43510028828 reached Android unit tests and **FAILED as expected**: two Side Chat persistence/isolation regressions failed out of 2,148 tests. The earlier stage A commit remains the latest passing APK.
- Added independent Side Chat metadata beside the existing conversation files, without copying or inheriting parent context. Existing conversation history and file formats remain unchanged. The drawer offers creation, the two latest active sides and searchable full history using existing rename/delete actions; it blocks switching during a serial active run. Per-conversation drafts use the existing state owner and chat scroll positions use saved UI state.
- New UI text uses documented English fallback in the other locales. Android CI, actual persistence across device restarts, scrolling, voice, attachment and accessibility behavior require verification for this stage. No Android screenshot or device test has been run in Muse.

- GitHub Actions run 43512065153 **FAILED** for commit `54d64d99`: Android source and tests compiled, then one Android unit test failed (2,153 total) because the new workspace listing read every conversation body before paging. Core, CLI/localization and relay passed. The listing now sorts file metadata before lazily reading page contents; this correction needs a new CI run.

- The paging correction is committed at `84239bf9`; its result is **UNVERIFIED**. GitHub accepted the push but created no run; manual workflow dispatch returned HTTP 422, “Actions has been disabled for this repository,” even though the repository Actions permissions API reports enabled. No Android build was attempted in Muse.

## Conversation workspaces — stage C, 2026-11-14

- Channels use small `.workspace` companion metadata and independent existing conversation IDs. Creation, editing, contextual chat labels and removal are wired to the existing drawer and chat state. Removing a channel retains its transcript, task records and files; serial active work is not cancelled. Channel-scoped model instructions are omitted because the existing execution path has no supported per-channel instruction interface.
- Local localization validation **PASS** (2,862 resources in 11 languages); translation-tool tests **PASS** (12); XML parsing **PASS** (548 files); actionlint and `git diff --check` **PASS**. New Kotlin and device tests, Android compilation and device behavior remain **UNVERIFIED** because GitHub Actions rejected dispatch. New labels use the existing documented English fallback for non-English locales.

## Conversation workspaces — stage D, 2026-11-14

- Added persistent header (44–76 dp) and hub bubble (130–220 dp) controls with live Original/Monochrome previews and reset. The header reserves space for the selected size; the hub clamps its rendered size to window dimensions and font scale without changing the saved preference. Existing avatar geometry and state mapping are unchanged.
- Preference, clamping and header touch-target tests are included. They and Android layout, animation, font-scaling and restart behavior remain **UNVERIFIED** while GitHub Actions dispatch is blocked. New labels use the documented English fallback in other locales.

## Conversation workspaces — stage E, 2026-11-14

- Reused the existing Compose graphics-layer backdrop and RenderEffect blur for the floating primary navigation and composer; unsupported devices use a translucent surface. Primary content now extends behind navigation, while measured navigation height is reserved for the composer, task action and final list content. Selection color respects reduced motion.
- No Android rendering, IME, scrolling, accessibility, GPU or performance measurements were possible. Blur appearance and the overlay/inset behavior remain **UNVERIFIED**, as does Android compilation, while Actions dispatch is blocked.

## Conversation workspaces — stage F, 2026-11-14

- Replaced the permission-driven recent-photo grid with a compact chooser for the existing camera, Android photo/video picker and document picker. Multiple selection still uses the existing attachment limit, tray, import validation and draft state; cancellation leaves the draft alone. Removed media-library permission declarations that were used only by the old grid. Videos use the existing file attachment path; model video understanding is not implied.
- Picker, camera, keyboard transitions and attachment handling still require device verification. The new Kotlin and device regression tests and Android compilation remain **UNVERIFIED** while Actions dispatch is blocked. New labels use the documented English fallback in other locales.

- Review follow-up: Channels are excluded from automatic archiving and remain listed if already archived; their existing unarchive action remains available. The change has a source regression test but awaits Android CI.

- Review follow-up: See All expands the Side Chat history to the available drawer width and preserves the existing search/actions. Conversation navigation rows now have at least 48 dp touch targets. Full-screen behavior and accessibility remain unverified on a device.

- Final local source checks: translation validation **PASS** (2,868 resources, 11 languages), translation-tool unit tests **PASS** (12), XML parsing **PASS** (549 files), actionlint and `git diff --check` **PASS**. GitHub still has not scheduled a run for the paging fix or later commits. Ordinary drafts use the existing in-memory draft owner; restart persistence and all device behavior remain unverified.

- Dispatch recovery, 2026-11-14: main commit `510469165fa5` adds only a dispatch-only Android workflow; actionlint passed and the existing push/PR CI remains unchanged. GitHub confirms the file is on `main`, yet dispatching `mika-uiux-phase-00` with screenshot capture still returns HTTP 422: Actions has been disabled for this repository. The repository permissions API says enabled, and the main push created no CI run. GitHub documents a possible separate repository/account disabled state requiring Support review; the cause remains unconfirmed. No newer Android build or device result is available.

- Restored CI run 43569132846: Android compilation, app unit tests and APK upload **PASSED**; CLI/localization and relay **PASSED**. Core had one documentation consistency failure caused by a historical test-count sentence; that wording is corrected. Emulator setup exited during AVD creation before any device tests or screenshots. The workflow now selects a standard device profile, removes the input pipe and requests the workspace, hub and attachment device tests on the next capture run. These fixes require CI verification.

- CI run 43569822883: core, CLI/localization, relay, Android compilation and app unit tests **PASSED**; debug APK artifact 14104286451 was uploaded. The overall run **FAILED** because the AVD tool could not load a `devices.xml` file absent from the system image. The capture workflow now uses the Android CLI device-creation command; screenshots and device tests still require a new run.

- Stage 1 redesign, 2026-11-14: Make now lists only files the agent produced (shared outputs, newest first, tap to preview, "Show in chat" to jump to the source turn); browser, files, memory and skills stay in the Orbit hub and drawer, and Teach Orbit moved into the composer's plus menu. The plus opens a compact smoked-glass menu above the composer (live RenderEffect blur on Android 12+, near-opaque fallback on 11) instead of a keyboard-height panel below it; pickers stay registered while the menu closes. The primary navigation stays composed while the keyboard is up and the composer sits at max(keyboard, navigation height), removing the overshoot and snap on keyboard close (ORB-003). The chat header gains a zero-height fade (ORB-004). Tasks + creates a scheduled task in place over Tasks (ORB-001). The hub caches Today so tab switches no longer flash Loading (ORB-002) and drives its page, content and single presence from one expansion value with header avatars hidden while it shows (ORB-005). Launcher, themed and splash marks are redrawn from the supplied logo with a separated, tapered wave inside the adaptive-icon safe zone (ORB-006). Android compilation and device behavior await CI.
