# Mika visual and motion specification (draft v1)

> Proposed and Android-provided identifiers use HTML code formatting; verify their availability before implementation.

## Direction

Original native Android personal agent. Quiet, editorial, tactile, highly legible. The design references are **aspirational visual targets**. Keep Mika's name and identity. Duet-inspired interaction patterns are references, not assets to copy. The original symbol is **Orbit Spark**, showing a warm coral orb crossed by a flowing orbit/wave. Do not substitute a generic sparkles icon or the Claude logo.

## Design reference hierarchy

- `mika_light_concept_board.png` — main layouts and light styling.
- `mika_dark_concept_board.png` — contrast, AMOLED variant, dark presentation.
- `mika_identity_exploration_board.png` — Orbit Spark selected over the other four exploratory marks.
- `mika_orbit_spark_presence_study.png` — six illustrated avatar states (concept, **not** animation frames).
- `mika_orbit_spark_identity_board.png` — icon, splash, floating capsule, voice and notification uses.
- `mika_concepts.html` — earlier static UI study with concrete color and spacing samples. Translate into native Compose; don't embed an HTML WebView to recreate Mika UI.
- `original_mika_screen.jpg`, `duet_reference_1.jpg`, `duet_reference_2.webp` — context/before state.

## Candidate palette, adapted from concept HTML

| Semantic role | Warm light | AMOLED dark |
|---|---|---|
| Background | `#FAF9F7` | **True black option `#000000`** (concept originally `#08090A`) |
| Main content text | `#222322` | `#F2F0EC` |
| Muted text | `#82807C` | `#92918F` |
| Lower surface | `#F0EFEC` | `#18191B` |
| Raised surface | `#FFFFFF` | `#202123` |
| Hairline | `#E9E6E2` | `#2A2B2D` |
| Coral accent | `#DB816A` | `#DC8066` |
| Soft coral | `#F9D5CA` | `#54372F` |
| Success | `#428565` | `#8ED7A7` |

These are **starting tokens**, not pre-approved final accessible combinations. Evaluate actual foreground/background contrast. Support light, AMOLED, and system theme behavior without removing Mika's existing appearance preferences. Keep readable container contrast without decorating everything with gradients. Use dynamic color only as a well-defined opt-in/alternative if it preserves the brand identity.

## Type, shapes, spacing

- Type: strong editorial greeting headline around 30–32sp equivalent, structured body 16sp+, tool/status metadata smaller but accessible, consistent titles and semantically named tokens. Respect Android font scaling up to 200%; avoid relying on a non-bundled proprietary font.
- Shapes: naturally rounded corners: approx 20–24dp cards, 22–30dp pills, responsive rounded bottom bar. Avoid pill overload and redundant nested cards.
- Content: 16–24dp horizontal margins as screen width permits. 8dp-derived spacing scale; visually inspect actual devices instead of blindly using one width.
- Navigation: compact bottom Chat / Tasks / Make. Preserve system gesture navigation, IME insets and back action. Do not let the bar obscure a composer or agent status. Adapt when keyboard is open.
- Touch/semantics: minimum 48x48dp effective touch targets, readable state labels in addition to color, TalkBack descriptions/actions and deterministic screen focus.
- Home: greeting + honest, contextual content from user-owned persisted data, not fictional proactive summaries. If no briefing exists, show a beautiful empty state and existing recommended tasks.
- Chat: preserve streaming, Markdown, quoting/branching, attachments, voice, full run/source inspection, current task status and task controls.
- Tasks: group real task data (ready for review, working, scheduled, resolved) using existing stores. Only show status distinctions supported by code; never create fictional approvals or tasks in production.
- Make: use real existing skills, teaching and creation capabilities; inspect whether 'Make' is a truthful label. If not, propose a better term before shipping.

## Orbit Spark static mark

- One **canonical vector geometry** for the coral orb + sweeping dark/light ribbon. Create a single reusable `MikaMark` renderer, with semantic size variants for app chrome, chat avatar, capsule and splash.
- Geometry must be deliberately constructed from paths/arcs, not automatically traced from the rendered identity-board bitmap. Match silhouette/negative space across themes at 24dp, 40dp, 64dp, 128dp and launcher-icon sizes.
- Theme-aware ribbon/orb colors; monochrome and high-contrast fallback; Android adaptive icon foreground/background safe zone. Keep marks readable with disabled transparency and in small sizes.
- Add Compose `@Preview` grids for all mark sizes/themes. Compare with reference visually and revise geometry before animation.

## Presence state machine: 7 states

UI presence must be driven by **real application state** and a deterministic, testable mapping. Suggested states:

| State | Source concept | Motion sketch |
|---|---|---|
| Idle | No active task/audio request | Very slow subtle breathing (roughly 4–6 s cycle) or static in reduced motion |
| Listening | Real voice capture active | Soft rings pulsing, optionally reacting to available audio amplitude; no invented microphone status |
| Thinking | Waiting for model or planning, distinct from active tool action | Low-amplitude orbit/ribbon drift; roughly 2–3 s cycle |
| Working | A tool/task is actively executing | Purposeful circular motion with restrained halo, not a strobing spinner |
| Needs input | Existing confirmation/takeover workflow awaiting user | One gentle attention pulse, then stable until acknowledged; text label and alert semantics |
| Success | A real task completed | Short, one-shot settle/halo, then return to idle when appropriate |
| Error | An actual error is surfaced | Short shift + label/status affordance; no color-only error and no strong shake |

Implement <code>AgentPresenceState</code> as UI presentation state, not as a new authority for agent execution. Determine priority and transitions from existing Task/Voice/Confirmation/Provider sources after inspecting them. Document ambiguous or unsupported mappings. Use Compose Canvas + transitions, animation state keyed to current lifecycle. Pause off-screen or background infinite animations; respect system animator settings and reduced-motion behavior. Prefer 60/120Hz-friendly lightweight draw operations, minimal invalidation and no per-frame bitmap generation. <code>AnimatedVectorDrawable</code> is also acceptable for simple path animations. Only add Lottie if justified and approved.

## Surface applications

- Launcher icon and Android 13+ monochrome themed icon (preserve existing package/application id unless instructed otherwise).
- Splash: platform-compliant Android SplashScreen; avoid introducing a slow custom splash blocking startup. Extra motion only after startup, if appropriate.
- Small assistant avatar in chat, header, brief and task cards.
- Capsule: animate within existing overlay lifecycle; no disruption of touch-through, keyboard avoidance, PiP sizing, or foreground task controls.
- Notification icon: conform to Android notification small-icon requirements (monochrome silhouette) rather than reusing colored launcher artwork.
- Voice/live panel: draw from actual mic/listening state. Accessibility labels, mic permissions, cancel and pause semantics must remain correct.

## Visual verification

Screenshot matrix for each touched screen: warm light, AMOLED, large-font 200%, narrow Android phone, and when relevant keyboard open + active task + no-data/error/loading. Compare **layout intent and brand mood**, not pixel-match to a perspective mockup with simulated iOS chrome. Use native Android bars. Show real device captures, not generated mockups, for acceptance.
