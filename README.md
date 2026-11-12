# FastTorch

An Android app that toggles the flashlight with two quick chopping motions, inspired by Motorola's gesture shortcut. Built with Java and XML Views.

**Status: first development build.** Manual flashlight control, chop-chop detection, foreground monitoring, sensitivity settings, optional vibration, and gesture practice are implemented. Physical-phone calibration, screen-off reliability, and battery measurements remain unverified. This is not a production release.

## Run and try it

Open the project in Android Studio and run the `app` configuration on a phone with Android 9 or newer. Or build the debug APK:

```powershell
.\gradlew.bat :app:assembleDebug
```

Output: `app/build/outputs/apk/debug/app-debug.apk`.

1. Open FastTorch. It checks for an accelerometer and a rear flashlight automatically. Missing required hardware is explained and the affected controls are disabled.
2. Try the manual flashlight button, then use **Try the gesture** to practice without changing the light.
3. Enable **chop-chop**. Allow notifications if you want the Stop control in the notification drawer. Notification denial does not block monitoring.
4. Hold the phone firmly and make two short chopping strokes. Start with Normal sensitivity; try High for gentler strokes or Low if normal handling triggers it.
5. Stop from the switch or notification. Rebooting, force-stopping, or system process termination requires enabling detection again.

The accelerometer is the only required motion sensor. A wake-up accelerometer is preferred when available; without one, detection may stop during screen-off suspend. The app reports this capability and does not hold a permanent wake lock. No camera preview, recording, network access, or camera runtime permission is used by the torch-only path.

Manual-only use ends when leaving the activity. Monitoring keeps a foreground service alive after leaving the app. Practice is limited to the visible activity and temporarily suppresses flashlight gestures.

## Verification

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
.\gradlew.bat :app:connectedDebugAndroidTest
```

The connected task needs an emulator or device. See [validation results](docs/VALIDATION.md#implementation-validation-2026-11-12) for what was actually checked and what still needs a physical phone.

## Planning documents

- [Implementation plan](PLAN.md): ordered milestones, dependencies, and completion criteria.
- [Product requirements](docs/REQUIREMENTS.md): intended behavior, interface, scope, and assumptions.
- [Technical design](docs/TECHNICAL_DESIGN.md): gesture recognition, Android lifecycle, permissions, risks, and source references.
- [Validation plan](docs/VALIDATION.md): automated checks, physical-device trials, battery measurements, and release criteria.

## Project configuration

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
| UI | Material home screen with light/dark themes |

The existing SDK, dependency, and toolchain versions have been preserved. The project builds with JDK 21 on the development machine; Java source/target compatibility remains 11.

The service uses `specialUse` for user-enabled gesture monitoring. Store publication and foreground-service policy review remain outside this development build.
