# CLAUDE.md – Stimmzug

Android intonation trainer for tuba: plays an exactly tuned drone, listens via microphone, confirms when the
player holds the pitch within a tolerance for a hold time, then advances. See `docs/roadmap.md`.

## Language

- The owner communicates in **German**: UI strings, issues, PRs, docs (`docs/`) in German.
- Code, identifiers, code comments and commit messages in English (Conventional Commits).

## Commands

```sh
./gradlew check                       # spotless (ktlint), detekt, Android lint, unit tests, Kover verification
./gradlew :core:dsp:test              # fast DSP unit tests (JVM)
./gradlew assembleDebug               # debug APK (applicationId suffix .debug)
./gradlew connectedDebugAndroidTest   # instrumented tests on emulator/device
./gradlew spotlessApply               # auto-format
./gradlew :tools:replay:run --args="<file.wav> --target Kontra-B --calibrate 1.5"   # replay a recording, see tools/replay/README.md
```

Machine-specific paths (JDK, SDK, AVD, adb) are in `CLAUDE.local.md` (not committed).

## Architecture

- `core/dsp` – pure Kotlin/JVM, **no Android dependencies**. Pitch detection (MPM, YIN for comparison), FFT,
  level gate, note math, drone synthesis, match state machine, exercise sequencer. Time base = audio sample time.
  Keep new core code free of JVM-only APIs where practical: it is meant to become a Kotlin Multiplatform module
  for the iPhone app (M7, #49).
- `app` – Compose UI, ViewModels, audio adapters (`AudioRecord`, `AudioTrack`), settings, session logging,
  debug-only "virtual tubist" audio input for E2E tests.
- `tools/` – replay CLI (WAV → detector → CSV/PNG), QA helpers.
- Decisions are recorded as ADRs in `docs/adr/`.

## Workflow rules

- Every change starts with a GitHub issue (labels + milestone M0–M7, see `docs/roadmap.md`).
- Branch `feat/<issue>-<slug>` or `fix/<issue>-<slug>`; PR body contains `Fixes #<issue>` and test evidence.
- Run `./gradlew check` locally before pushing. Merge (squash) only when CI is green.
- Work autonomously within a milestone; at milestone end: tag `vX.Y.Z` → release → owner tests with a real tuba.
- Exploratory testing: follow charters in `docs/qa/charters.md`, log sessions in `docs/qa/sessions/`,
  file findings with the "Explorativer Testbefund" template.
- Never commit signing material (`*.jks`, `keystore.properties`) or field recordings (`testdata/local/`).
- The app must not request the INTERNET permission.
