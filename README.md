# FastTorch

An Android app planned to toggle the flashlight with two quick chopping motions, inspired by Motorola's gesture shortcut.

**Status: planning only.** The app currently contains the Android Studio starter screen. No flashlight, gesture, or background-service functionality has been implemented. Implementation should begin only after the user requests it.

## Planning documents

- [Implementation plan](PLAN.md): ordered milestones, dependencies, and completion criteria.
- [Product requirements](docs/REQUIREMENTS.md): intended behavior, interface, scope, and assumptions.
- [Technical design](docs/TECHNICAL_DESIGN.md): gesture recognition, Android lifecycle, permissions, risks, and source references.
- [Validation plan](docs/VALIDATION.md): automated checks, physical-device trials, battery measurements, and release criteria.

## Existing project

Inspected on 2026-11-12:

| Item | Current configuration |
| --- | --- |
| App module | `app` |
| Namespace / application ID | `com.example.fasttorch` |
| App language / UI | Java / XML Views |
| Java source compatibility | 11 |
| Minimum SDK | 28 (Android 9) |
| Compile / target SDK | 37 / 37 |
| Android Gradle Plugin | 9.4.1 |
| Gradle wrapper | 9.6.0 |
| Existing UI | `MainActivity` with a Hello World layout |

These values describe repository configuration, not a verified build. The planning pass does not build the project or change dependencies.

The first implementation milestone will validate real-device torch access and screen-off sensor delivery. Background and locked-screen behavior depends on Android restrictions and device hardware; it is a core validation requirement, not an already proven capability.
