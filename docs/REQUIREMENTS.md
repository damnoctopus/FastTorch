# Product requirements

Status: proposed behavior for the first version; not implemented.

## Core experience

1. The user opens FastTorch and sees whether the phone supports flashlight control and motion detection.
2. The user enables chop-chop detection, completing any necessary permission flow.
3. Two quick, deliberate chopping strokes toggle the flashlight on. Repeating the gesture after cooldown toggles it off.
4. Detection remains active outside the activity while the foreground service is running. Locked-screen use is a core target, gated by real-device feasibility and power testing.
5. The user can stop detection from the app or its ongoing notification.

This emulates the interaction through public Android APIs. Universal equivalence to an OEM-integrated feature is not a requirement or a promise.

## Functional requirements

| ID | Requirement |
| --- | --- |
| F01 | Detection is off on first launch and starts only through a user action. |
| F02 | One accepted two-stroke gesture produces exactly one toggle request. A lone stroke does not toggle. |
| F03 | Recognition includes timeout, hysteresis, and cooldown to reject noise and repeated toggles. |
| F04 | Manual torch control is available in the app even when gesture detection is off. |
| F05 | Display observed torch state, monitoring state, and useful unavailable/error explanations separately. |
| F06 | Offer Low, Normal, and High sensitivity; remember the choice. |
| F07 | Provide short gesture instructions and a practice mode showing recognition without toggling the flashlight. Practice owns no second sensor subscription. |
| F08 | Explain persistent monitoring and provide an ongoing foreground-service notification with Stop. |
| F09 | Stop removes sensor monitoring promptly. If FastTorch last enabled the flashlight and no external change has invalidated that ownership assumption, make a best-effort off request. |
| F10 | Missing flash or accelerometer, camera contention, denied permissions, and service start failure produce a recoverable state, not a crash. Missing optional sensors does not block basic operation. |
| F11 | Optional haptic confirmation defaults off and runs only after a confirmed successful gesture toggle. |
| F12 | Reboot, force-stop, or process loss does not silently re-enable detection in version one. Reopening shows actual state and offers Enable. |

For manual-only use, leaving the visible activity turns off a torch enabled by that session unless an active foreground service has explicitly taken ownership. Do not leave an untracked manual session running in the background.

Torch ownership is best effort: Android permits other apps to change the same flash. Do not claim exclusive ownership or turn off an externally enabled torch just because monitoring stops.

## Proposed home screen

- Title: FastTorch, with a brief two-chop instruction.
- Main switch: Enable chop-chop, accompanied by actual monitoring status.
- Flashlight status and manual on/off button.
- Sensitivity selector and optional vibration switch.
- Practice gesture action and concise troubleshooting/support status.

Use existing XML Views and Material components. Support dark mode, TalkBack labels, large text, and touch targets of at least 48 dp. Express states in text as well as color.

## Quality and privacy

- Process sensor data locally. No network, accounts, ads, analytics, or image/video capture is needed.
- Keep continuous motion traces out of release storage. Development traces are opt-in, local, and removable.
- Avoid polling and per-sample allocation; use modest sensor rates and register only what is needed.
- Do not request unrelated location, microphone, storage, accessibility, overlay, or activity-recognition access.
- Do not request battery-optimization exemption on first launch. Investigate actual device failures before offering device-specific guidance.
- Never display monitoring as active solely because the preference was previously enabled.

## Acceptance summary

On each device claimed as supported, intentional gestures must meet the recognition and latency targets in [VALIDATION.md](VALIDATION.md), routine handling must not trigger the flashlight in the defined negative trials, and screen-off operation must meet the documented power budget. Unsupported locked-screen behavior must be identified explicitly rather than silently treated as working.
