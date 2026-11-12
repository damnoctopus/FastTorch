# FastTorch implementation plan

Created: 2026-11-12. Status: ready for review; implementation not started.

## Objective

Build a Java Android app that recognizes a deliberate chop-chop gesture and toggles the rear flashlight once. After the user enables detection, aim to support use outside the app and with the screen locked, with measured reliability and battery cost.

Keep the existing Java/XML project and SDK configuration initially. Use platform sensors and Camera2 torch APIs, with a small deterministic detector that can be tested without Android hardware.

## Planning completed

- [x] Inspect the starter project and existing configuration.
- [x] Define requirements and initial defaults.
- [x] Outline components and gesture recognition.
- [x] Identify background execution and screen-off feasibility risks using Android documentation.
- [x] Define implementation milestones and validation gates.

All tasks below are future work. No implementation is authorized by this plan alone.

## Phase 0 - Establish device feasibility

Dependencies: user requests implementation; at least one physical Android phone is available.

- [ ] Confirm the primary phone model, OS, available accelerometer/gyroscope/proximity sensors, and wake-up sensor variants.
- [ ] Run a baseline build with the installed SDK/JDK; preserve configured versions unless a concrete incompatibility requires a documented change.
- [ ] Recheck target SDK 37 behavior changes and current foreground-service requirements before finalizing manifest declarations.
- [ ] Make a minimal development probe for torch on/off, callbacks, sensor timestamps, and sensor delivery while locked and idle.
- [ ] Resolve the minimum torch permissions and correct foreground-service type. Evaluate `specialUse` for continuous gesture monitoring; determine whether the actual torch path requires `camera` type and its runtime prerequisites. Do not assume one replaces the other.
- [ ] Compare wake-up sensor support with screen-off behavior and battery cost. Keep any wake-lock experiment explicit and temporary.
- [ ] Record results, limitations, chosen service type, and a supported operating mode in the technical design.

Exit: evidence that the planned background/locked-screen experience is viable on the primary device, or a clearly documented limitation requiring a scope decision before claiming the full feature. In-app-only functionality is a useful prototype, not completion of the intended experience.

## Phase 1 - Flashlight control and minimal UI

Dependencies: Phase 0 permission and service decisions.

- [ ] Discover a rear camera with a flash and report missing hardware gracefully.
- [ ] Implement centralized torch commands and callback-driven state, including unavailable and error states.
- [ ] Add a manual flashlight button and the permission/recovery flow required by the validated implementation.
- [ ] Handle camera contention, permission changes, and external flashlight toggles.

Exit: manual control and displayed state agree on a physical device; failures do not crash or show false success.

## Phase 2 - Chop-chop detection

Dependencies: Phase 0 sensor evidence and Phase 1 torch controller.

- [ ] Capture local development traces for intentional gestures and ordinary handling.
- [ ] Implement a pure Java detector with timestamp-based filtering, two-stroke recognition, hysteresis, timeout, and cooldown.
- [ ] Connect the sensor adapter and detector to the shared torch controller.
- [ ] Add Low / Normal / High sensitivity presets, with Normal as default.
- [ ] Add detector unit tests and evaluate held-out traces before choosing final constants.

Exit: the foreground prototype meets the detection and false-trigger criteria in the validation plan; one gesture produces at most one toggle.

## Phase 3 - Background and locked-screen operation

Dependencies: Phases 0-2; service type and power strategy resolved.

- [ ] Add one user-started foreground service owning sensor monitoring and torch state while enabled.
- [ ] Add the ongoing notification and a Stop action; start monitoring from the visible activity after prerequisites are met.
- [ ] Implement idempotent enable/disable, listener cleanup, state restoration on activity recreation, and honest stopped/error states.
- [ ] Validate behavior after leaving the app, screen locking, deep idle, battery saver, task removal, and process termination.
- [ ] Measure screen-off battery overhead before enabling that mode by default on supported devices.

Exit: documented background and lock-screen trials pass on the primary device; unsupported behavior is visible to the user; stopping detection releases resources.

## Phase 4 - Usability and reliability

Dependencies: Phase 3.

- [ ] Finish the home screen, gesture instructions, practice feedback, sensitivity control, and troubleshooting text.
- [ ] Add optional haptic confirmation after a successful toggle, with detector suppression to avoid vibration feedback loops.
- [ ] Persist settings without treating a saved preference as proof that the service is running.
- [ ] Verify accessibility, dark mode, large text, rotation, notification denial, and unsupported hardware.
- [ ] Tune against multiple users, grips, and devices; keep a separate validation set.

Exit: acceptance criteria pass with no unresolved critical lifecycle, battery, or false-trigger defect.

## Phase 5 - Release readiness

Dependencies: Phases 0-4.

- [ ] Run unit tests, lint, build, instrumentation checks, and the physical-device matrix.
- [ ] Record measured support, limitations, battery results, and final gesture parameters.
- [ ] Decide release application ID, signing/distribution method, name, and icon before distributing.
- [ ] If publishing to Google Play, complete the applicable foreground-service declarations and review the selected use case against current policy.
- [ ] Update the README with verified setup and usage instructions.

Exit: a tested build and accurate user documentation. Publishing remains a separate action.

## Decisions to resolve during implementation

| Decision | Initial direction | Resolve by |
| --- | --- | --- |
| Primary phone / OS | Not yet supplied; do not block planning | Phase 0 |
| Foreground-service type and torch permissions | Validate actual API path and current platform rules | Phase 0 |
| Screen-off sensor strategy | Prefer a suitable wake-up sensor; no indefinite wake lock by default | Phase 0 |
| Detection thresholds | Tune from traces; provisional timing in technical design | Phase 2 |
| Pocket guard | Evaluate proximity only if negative trials show a need | Phase 4 |
| Distribution | Local development first; store publication not assumed | Phase 5 |

## Deferred scope

Automatic boot startup, Quick Settings tile, widgets, brightness control, SOS/strobe, cloud services, analytics, machine-learning recognition, and permanent CPU wake locks are outside the initial implementation. Revisit only after the core gesture and power behavior are validated.
