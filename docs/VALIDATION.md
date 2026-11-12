# Validation plan

Status: future tests and proposed acceptance targets. No tests or hardware experiments have been run during planning.

## Detector tests

Use JUnit for pure Java sample-sequence replay with controlled timestamps:

- Valid two-stroke sequences toggle once; a single stroke including braking/rebound does not.
- Too-fast and too-slow pairs, low amplitude, inconsistent direction, noise, and stationary input do not trigger.
- Cooldown prevents duplicate toggles; a later valid pair works after quiet/rearm.
- Different sample rates, jitter, missing samples, long gaps, stale samples, and invalid values reset or reject safely.
- Sensor rotation, left/right grips, and sensitivity presets behave as intended.
- Haptic-induced motion cannot retrigger the detector.

Use synthetic boundary tests plus locally recorded positive and negative traces. Tune on one set and evaluate on separate sessions/users; do not report training-trace results as real-world accuracy.

## Lifecycle and control checks

Test with fakes where practical, then verify on Android:

- Failed torch command never becomes a displayed success; callback changes update UI.
- Pending toggles are serialized; unknown/unavailable state prevents unsafe assumptions.
- Repeated Enable creates no duplicate listener/service; Stop releases resources.
- Rotation and activity recreation do not restart detection or lose service state.
- Practice mode produces feedback without changing the torch.
- External Quick Settings changes, camera use, permission revocation, and missing hardware recover sensibly.
- Notification denial and notification Stop work according to platform behavior.
- Process death, force-stop, task removal, and reboot produce truthful state on reopen and no unauthorized restart.
- Manual-only control shuts down correctly when leaving the activity; ownership handoff preserves one controller.

## Physical-device matrix

At least one real phone is necessary for the first feasibility result. Before claiming broad support, test two or more manufacturers, including devices with and without suitable wake-up sensors if available. Record model, OS/API, sensor names/vendor/wake-up status, app build, settings, and result.

| Environment | Main checks |
| --- | --- |
| API 28 baseline | Permission flow, sensors, torch, foreground service |
| API 31-32 | Service start restrictions and app/background transitions |
| API 33 | Notification allow/deny behavior |
| API 34+ | Service types, permissions, while-in-use constraints |
| Target API 37 device/emulator when available | Target-specific behavior and build compatibility |
| Screen on: app visible / home / another app | Gesture recognition and camera contention |
| Screen off: immediately / 30 minutes / overnight | Timely sensor delivery and first gesture after idle |
| Battery saver / Doze / OEM restrictions | Reliability, honest limitations, and power |

Emulators can validate UI, permission, and lifecycle paths. They do not validate physical chop recognition, real flash performance, or idle battery cost. Mark unavailable matrix entries as untested instead of inferred passes.

## Proposed measurable targets

These are release goals to evaluate, not measured results or guarantees.

| Measure | Initial acceptance target | Method |
| --- | --- | --- |
| Intentional recognition | At least 95% | At least 100 gestures per claimed operating mode/device, split across two users and both hands; include torch on and off. |
| Duplicate toggles | Zero | Check the same gesture trials and repeated/rebound stress cases. |
| Response latency | 95th percentile at most 500 ms after completion of second stroke | Record detector/command/callback timing; spot-check physical light response on video. |
| False activation | Zero in the defined negative suite | At least 15 minutes each: walking, stairs, running, pocket/bag carrying, repeated pickup/putdown, and vehicle travel as a passenger. Report exposure time. |
| Idle behavior | Zero unintended toggles | Overnight stationary monitoring with a first-gesture check afterward. |
| Stop | No further gesture commands; resources released within 1 second of handling Stop | Inspect state/logs and repeat gesture immediately afterward. |
| Idle battery overhead | Provisional budget: at most 3 percentage points over 8 hours above baseline | Paired monitoring-on/off trials with torch off, repeated at least three times per supported device. |

For battery trials, keep charge range, radios, battery saver, screen state, installed workload, and ambient conditions comparable. Use battery statistics/energy tooling where available; report variation because percentage readings are coarse. Measure recognition after idle as well as drain: saving power by ceasing detection is not a pass.

If a target fails, tune or document a narrower support claim before release. If screen-off reliability requires unacceptable power use, revisit the product scope explicitly.

## Commands for implementation time

Run from the project root after implementation is authorized and the toolchain is available:

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
.\gradlew.bat :app:connectedDebugAndroidTest
```

The connected test task requires an attached device or running emulator. Hardware trials remain separate from a passing Gradle test suite.

## Result record for each test run

Record date/build, device/OS, sensor configuration, detection constants, permissions, operating mode, repetitions, successes/misses/duplicates, latency, false-trigger exposure, battery baseline/result, and unresolved defects. Include whether locked-screen operation was tested after genuine idle. Do not fill in passes before measurements exist.
