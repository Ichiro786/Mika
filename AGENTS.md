# Codex project rules — Mika UI/UX program

These rules apply to this **UI/UX redesign program**, and supplement rather than replace existing repository instructions.

## Mission

Deliver native Android Kotlin/Jetpack Compose UI/UX and original Orbit Spark identity inspired by the supplied concept boards, without sacrificing Mika's working functionality. Optimise for correctness, accessibility, visual fidelity, reliable motion, and maintainability.

## Source priority

1. Current, checked-out source code and tests, plus verified Android runtime behavior.
2. User-approved reference boards in `docs/design-references/` and `docs/MIKA_UIUX_DESIGN_SPEC.md` for intended appearance.
3. `docs/MIKA_UIUX_MASTER_PLAN.md` and `docs/MIKA_UIUX_PROGRESS.md` for scope and sequencing.
4. The Argon audit for leads; confirm findings independently before acting.
5. Official Android documentation for APIs. Verify compatible versions; do not assume an API exists from the audit.

## Non-negotiable boundaries

- One numbered phase at a time. Work autonomously **within** that phase; stop after its verification and handoff. Do not start the next phase until the user explicitly advances it.
- Check `git status`, branch, commit and build files before editing. Do not delete/overwrite user modifications. If working tree is dirty, identify the state and avoid destructive reset/checkout/clean.
- Never silently reimplement the core agent loop, model-provider authentication, task scheduling, conversation store, WebView/ADB/PRoot tools, voice input, payment/confirmation gates, accessibility service, or overlays.
- `:core` and high-risk `:app` services are **preserve by default**. Seek explicit approval before any necessary changes beyond UI and UI state adapters.
- Preserve existing public routes, deep links, test tags, resources, conversation identifiers, localization and self-operation entry points until parity is verified.
- Never treat a concept screenshot as a functional specification for capabilities Mika does not already have. Fake content may appear in `@Preview` fixtures only, clearly marked. Production UI must use real state and handle empty states honestly.
- Preserve user approvals for consequential tasks (payments, sending messages, external actions); UI buttons must connect to the existing confirmed approval action and must not automatically authorize any operation.
- No quiet dependency upgrades. For Material 3 Expressive: verify current dependency version, proposed artifact, Kotlin/AGP/BOM compatibility, API opt-ins, release notes and test/build impact before making a minimal change.
- Do not introduce unnecessary dependencies, network services, analytics, telemetry, dynamic fonts, or background loops.
- Keep SVG/raster reference boards out of the app runtime as literal UI. Implement Orbit Spark with purpose-built vector geometry or Compose Canvas and provide high-resolution exports only for platform assets as appropriate.

## Quality gate for every phase

Before editing: inspect exact relevant files, identify behavior invariants, state intended change, tests and rollback plan. After editing: run project-supported compile/tests, inspect changed files, run localization checks, capture before/after UI if possible, verify accessibility/contrast, provide a complete diff summary, and update `docs/MIKA_UIUX_PROGRESS.md`.

Report in this order: phase, commit/branch, files touched, implemented behavior, before/after screenshots paths, commands and results, manual checks performed, untouched invariants, issues/unverified points, commit hash, recommendation for next phase. Never claim an unavailable emulator or device test passed.

Do not commit generated build products, local credentials, SDK paths, or secrets. Avoid editing upstream Git remotes.
