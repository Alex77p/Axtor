# Phase 12 — Real-Device Validation

Phase 12 validates the shipped Axtor architecture on a physical Android device. GitHub CI proves that the source builds and tests; it cannot prove microphone, screen-off, Bluetooth, battery, or real GGUF performance on the user's phone.

## Before testing

- Install the latest successful `Axtor-release-apk` from GitHub Actions.
- Import a known-good GGUF model that fits the phone's available RAM.
- Grant microphone and notification permissions when requested.
- If using hands-free mode, enable the Axtor Accessibility Service only when needed and review Android battery/background restrictions.
- Enroll the user's snap from **Snap & hands-free** before testing snap commands.
- Record the phone model, Android version, RAM, model name/quantization, and available storage.

## Test matrix

| ID | Test | Expected result | Status |
|---|---|---|---|
| 12.1 | Cold start | Axtor opens to Chat without crash | ⬜ |
| 12.2 | Low-RAM operation | App remains responsive; OOM produces a clear smaller-model message | ⬜ |
| 12.3 | Screen off | Supported hands-free service continues only where Android permits it | ⬜ |
| 12.4 | Bluetooth/headphones | Voice input/output follows the active Android audio route | ⬜ |
| 12.5 | Microphone | Permission works and microphone capture starts/stops cleanly | ⬜ |
| 12.6 | Voice command | A supported command executes and returns a verification response | ⬜ |
| 12.7 | Personal snap | Enrolled snap triggers the configured hands-free flow | ⬜ |
| 12.8 | Snap distance | Measure reliable distance in the actual room; do not treat outdoor-to-indoor operation as guaranteed | ⬜ |
| 12.9 | GGUF inference | Model loads and generates without crash; note tokens/sec and peak memory | ⬜ |
| 12.10 | Device automation | Safe allowlisted commands execute and unsupported/risky commands are rejected | ⬜ |
| 12.11 | Emergency stop | Three rapid enrolled snaps request cancellation/stop of an active Axtor flow | ⬜ |
| 12.12 | Battery/network | Background behavior, charging drain, Wi-Fi/mobile fallback, and recovery are acceptable | ⬜ |

## Recommended execution order

1. Run 12.1–12.5 with the phone cool and charging.
2. Run 12.6–12.8 with the screen on, then repeat the supported hands-free cases with the screen off.
3. Run 12.9 with the selected GGUF model and record first-token latency, generation speed, and whether memory pressure occurs.
4. Run 12.10 using harmless commands such as volume, home/back, settings, and opening an installed app.
5. Start a long-running voice/task flow and perform 12.11.
6. Run 12.12 over at least one normal usage period, including Wi-Fi loss/recovery if online fallback is configured.
7. Re-run the diagnostics/status command after any failure and save the reported component/error information.

## Evidence to record

For every failure, capture:

- phone model and Android version;
- Axtor APK version/commit;
- active GGUF filename and quantization;
- available RAM/storage;
- exact command or snap scenario;
- whether the screen was on/off;
- Bluetooth/audio route state;
- network state;
- Axtor diagnostics output;
- Android crash/system message, if any.

## Pass criteria

Phase 12 is **device-validated** only after the physical-device results are recorded. A green GitHub Actions run alone must not be reported as real-device validation.

Known limitations:

- Acoustic snap recognition is not cryptographic authentication.
- Walls, doors, distance, microphone placement, noise, and phone hardware can prevent a snap from being detected.
- Android may restrict microphone/background operation depending on OS state, permissions, battery policy, and user settings.
- Online AI availability depends on the configured provider and network; no external provider is assumed to be free forever.
