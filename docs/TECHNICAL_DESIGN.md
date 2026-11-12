# Technical design

Status: initial implementation plus remaining physical-validation decisions. Platform references checked on 2026-11-12 for configured target SDK 37.

## Implemented decisions

- The service is bound while the activity is visible and owns the single torch controller even for manual use. Enabling monitoring additionally starts it as a foreground service. This avoids a second activity controller and a handoff race.
- Motion samples/filtering run on a dedicated worker. Accepted gestures are posted to the main thread with generation and age checks. Service state and torch callbacks are main-thread confined.
- Launch-time discovery checks the accelerometer and rear flash independently. Missing hardware disables only affected controls; a gyroscope or proximity sensor is not required.
- Prefer a wake-up accelerometer, falling back to the normal accelerometer with an explicit screen-off limitation. No wake-lock permission or permanent lock is used.
- Use `specialUse` for ongoing user-enabled motion monitoring. The torch API does not open a camera. The [Android 17 CameraService torch implementation](https://android.googlesource.com/platform/frameworks/av/+/refs/heads/android17-release/services/camera/libcameraservice/CameraService.cpp) supports this torch-only path without a CAMERA permission check; the application therefore requests no camera permission and declares no camera service type. OEM validation and Google Play review are still pending.
- Reviewed [target SDK 37 behavior changes](https://developer.android.com/about/versions/17/behavior-changes-17) and [changes for all Android 17 apps](https://developer.android.com/about/versions/17/behavior-changes-all). Existing build versions are unchanged.
- Command/callback behavior is tested through a small `TorchHardware` adapter using a fake backend. That validates control logic, not physical flash behavior.

## Components and ownership

Keep a single app module and Java/XML. Proposed package root: `com.example.fasttorch`.

| Proposed component | Responsibility |
| --- | --- |
| `MainActivity` | Onboarding, enable/disable, manual control, settings, and rendering observed state. |
| `service/GestureMonitoringService` | Own monitoring lifecycle, sensor adapter, detector, notification, and controller while enabled. |
| `gesture/SensorSource` | Select available sensors; forward copied sample values and monotonic timestamps on a worker thread. |
| `gesture/ChopGestureDetector` | Pure Java recognition state machine; emit gesture events without Android UI or torch dependencies. |
| `gesture/GestureConfig` | Named sensitivity presets and all timing/filter parameters. |
| `torch/TorchController` | Discover flash, serialize commands, observe `TorchCallback`, and expose actual availability/state. |
| `settings/SettingsRepository` | Small local preferences store for sensitivity and vibration. |
| `notification/MonitoringNotification` | Channel, status text, and explicit Stop action. |

The activity binds to the service while monitoring runs; a shared state snapshot distinguishes disabled, starting, monitoring, unavailable, and error. Torch state is separately unknown, off, on, or unavailable. UI observation attaches/detaches with the activity lifecycle.

The bound service owns manual-only use, and stops its owned light when the visible session ends. There is one command authority and one sensor subscription. Practice mode routes recognition to UI feedback instead of torch commands and resets recognition on entry/exit.

## Gesture algorithm

Start with an accelerometer-based detector; evaluate gyroscope confirmation only if traces show meaningful improvement. Prefer a suitable wake-up accelerometer for locked-screen operation. Linear acceleration is an alternative input to evaluate, not guaranteed hardware.

1. Request approximately 50 Hz initially; measure actual sample intervals. Rate requests are hints, not timing guarantees.
2. Remove gravity with a time-aware low-pass estimate, or use validated linear acceleration input. Reset and warm up after startup or long gaps.
3. Segment strokes using acceleration energy, signed direction, minimum duration, and separate enter/exit thresholds. Learn the dominant direction for a candidate sequence instead of assuming the screen is upright.
4. Treat a chop as a motion envelope including its braking/rebound. Do not count opposite peaks within one physical stroke as two chops. Use trace evidence to distinguish two strokes from one vigorous shake.
5. Require two compatible stroke envelopes within a bounded interval. Reject inconsistent direction, excessive gaps, invalid samples, and isolated impacts.
6. Emit one event, enter cooldown, and require quiet/rearm before recognizing again. Reset after timeout, service stop, practice-mode changes, or sensor discontinuity.

Proposed state sequence: `IDLE -> FIRST_STROKE -> WAIT_SECOND -> COOLDOWN -> IDLE`. Stroke segmentation contains its own peak/rebound tracking. These states describe recognition intent, not final implementation code.

| Parameter | Initial experiment | Finalization |
| --- | --- | --- |
| Sampling | About 50 Hz | Compare latency, power, and sample loss |
| Gap between stroke onsets | 180-750 ms | Tune on real two-chop and single-stroke traces |
| Stroke envelope | 60-450 ms; 60 ms quiet to finish | Tune across users and grips |
| Cooldown after accepted gesture | 1,000 ms plus rearm | Check repeat use and vibration rejection |
| Acceleration / direction thresholds | Low 17, Normal 13, High 10 m/s²; direction agreement >= 0.5 | Experimental defaults; calibrate on device data |
| Sensitivity | Normal default | Adjust thresholds conservatively without removing timing guards |

Use `SensorEvent.timestamp` and elapsed monotonic time, never wall-clock time or sample count, for durations. Ignore stale queued input so unlocking cannot replay an old gesture. Use a serial worker for samples and torch requests; publish UI changes on the main thread.

## Torch control

Use camera discovery and flash characteristics to select a rear flash; do not hard-code camera ID `0`. `CameraManager.setTorchMode` does not require opening a camera session. Observe `TorchCallback` because another app can change availability or torch state. See the [CameraManager contract](https://developer.android.com/reference/android/hardware/camera2/CameraManager#setTorchMode(java.lang.String,boolean)).

Serialize toggle requests using confirmed state, allow at most one pending command, and wait for callback confirmation or a bounded timeout before accepting another. Report unavailable cameras and command failures without optimistic success or automatic retry loops. Clear ownership assumptions after external changes or lost state.

## Background operation and permissions

Continuous background sensor access requires a foreground activity or foreground service on Android 9 and later. Plan a user-started foreground service for ongoing monitoring. See [sensor background restrictions](https://developer.android.com/develop/sensors-and-location/sensors/sensors_overview).

Start from the visible activity after prerequisites are satisfied; promote promptly and handle failure. Use a non-exported service and explicit notification actions. Version one should not attempt boot startup or automatic process resurrection. See [foreground-service start restrictions](https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start).

For Android 14+, choose the service type based on actual work. `specialUse` is a candidate for persistent gesture monitoring, requiring its permission and a declared subtype explanation; store review is not guaranteed. Camera-type services have camera permission and while-in-use constraints. Phase 0 must resolve applicability to this torch-only path rather than choosing a type merely to avoid restrictions. See [foreground-service types](https://developer.android.com/develop/background-work/services/fgs/service-types).

| Declaration / permission | Planned treatment |
| --- | --- |
| Flash and accelerometer features | Declare optional and check at runtime so unsupported devices get a clear explanation. |
| `FOREGROUND_SERVICE` | Needed for the monitoring service. |
| `FOREGROUND_SERVICE_SPECIAL_USE` + subtype property | Only if the validated service design uses `specialUse` on API 34+. |
| `FOREGROUND_SERVICE_CAMERA` | Only if the validated design uses camera type on API 34+. |
| `CAMERA` | Not declared for the implemented torch-only path. No preview or recording. |
| `POST_NOTIFICATIONS` | Request contextually on API 33+ to expose monitoring controls. |
| `VIBRATE` | Only when adding optional haptic feedback. |
| `WAKE_LOCK` | Not part of the default design; conditional on an explicit measured power decision. |

Notification denial does not itself forbid starting a foreground service; the service still needs its notification. Explain reduced notification visibility and keep an in-app Stop action. See [notification permission behavior](https://developer.android.com/develop/ui/compose/notifications/notification-permission).

## Screen off and power

A foreground service does not itself keep the processor awake. Non-wake-up sensors may lose or delay events during suspend; a suitable wake-up sensor is device-dependent. This makes screen-off support a feasibility gate. See [SensorManager delivery behavior](https://developer.android.com/reference/android/hardware/SensorManager) and [sensor suspend behavior](https://source.android.com/docs/core/interaction/sensors/suspend-mode).

Prefer wake-up hardware with low reporting latency. If unavailable, measure alternatives before choosing between limited screen-off support and a clearly disclosed power-intensive mode. A continuous partial wake lock is not an accepted default. Significant-motion sensors alone do not establish that two chopping strokes can be recognized.

Unregister sensors and callbacks, stop the worker, and release any experimental wake lock when monitoring stops. Service destruction callbacks are not guaranteed on process death; state recovery must not depend on cleanup callbacks having run.

## Risks and mitigations

| Risk | Planned response |
| --- | --- |
| Single chop generates several peaks | Segment stroke envelopes; test braking and rebound traces. |
| Walking, running, pocket motion, vehicle vibration | Negative trace suite, conservative defaults, optional proximity guard only if warranted. |
| Pocket guard blocks intended use | Test uncovered/covered sensor and left/right grips; preserve basic mode on phones without proximity. |
| No wake-up sensor / OEM process restrictions | Measure long-idle behavior; publish device limitations and honest stopped state. |
| Battery cost | Paired idle measurements and sampling experiments before feature claims. |
| Camera or external app changes torch | Callback-driven state and no command retry loop. |
| Service type or SDK behavior differs | Resolve with current documentation and device probes before committing manifest design. |

No root, accessibility service, device administrator access, hidden APIs, or external motion-recognition SDK is planned.
