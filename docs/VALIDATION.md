# Validation plan

Status: development checks below have passed; physical-phone targets remain unverified.

## Improvements validation (2026-11-16)

Version 1.1.0 / code 2. Tested on the same Android 17 `Medium_Phone` emulator; no physical phone was connected.

| Check | Result |
| --- | --- |
| Local unit tests | 22 passed, including practice feedback for weak, incomplete, mistimed, and inconsistent strokes |
| Android instrumentation | 13 passed; includes reconnect after timeout, rejection of stale callbacks, retained light ownership, practice, and settings persistence |
| Debug and release lint | 0 errors; 1 existing AGP update advisory in each variant |
| Debug and signed release builds | Passed; release signature verified with `apksigner` |
| Signed APK smoke check | Installed and launched on the emulator; missing-flash state and touch scrolling checked |
| Signing setup | Persistent local key generated, Git-ignored, and unchanged on repeated setup |
| Publishing shell steps | Syntax and isolated mock checks passed for secret restoration, missing-secret failure, release arguments, and APK checksum |
| Live GitHub publishing | Not run; repository signing secrets still need uploading and the reported billing lock must be resolved |

The UI tests now use touch swipes and wait for scrolling to settle before tapping. Espresso's `scrollTo` action failed to reveal lower controls on this API 37 emulator; touch scrolling works. Initial boot/locked-screen attempts were not counted as passes.

Release signing files are local under the ignored `.signing/` directory. Back up both the key and its properties securely. The emulator cannot verify real flashlight behavior, motion accuracy, screen-off reliability, or battery overhead.

## Implementation validation (2026-11-12)

Environment: Windows, JDK 21, existing Gradle 9.6.0 / AGP 9.4.1 / SDK 37 configuration. Android emulator: `Medium_Phone`, Android 17. No physical phone was connected.

| Check | Result |
| --- | --- |
| Baseline and implemented debug build | Passed |
| Local unit tests | 18 passed: stroke envelopes, timing/cooldown, axes/rates, noise, invalid samples, gravity removal, raw-sample pipeline |
| Android instrumentation | 10 passed: absent hardware, practice, settings across recreation, service launch/failure/stop, torch command/callback races |
| Android lint | 0 errors; 1 existing dependency-update advisory for AGP |
| Visual inspection | Light and dark screens, scrolling, and 1.5x font size checked on emulator |
| Real flashlight / gesture trials | Not run; emulator has no rear flash |
| Locked-screen and overnight battery trials | Not run |

The service instrumentation test exercised foreground promotion followed by graceful missing-flash shutdown on this emulator. It did not prove sustained monitoring with a real flashlight. Torch command tests use a fake hardware adapter to check failure handling, external changes, pending-command serialization, and Stop arriving before an ON acknowledgement.

Screenshots and generated reports are under ignored `build/verification/` and `app/build/` directories. Synthetic tests are not evidence of 95% real-world accuracy. Next: install the debug APK on the user's phone, run manual control and practice, then complete the physical acceptance trials below.

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
