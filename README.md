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
2. Try the manual flashlight button, then use **Try the gesture**. Practice gives feedback about stroke strength, timing, direction, and completion, plus a successful-gesture count. These hints reflect the current detector thresholds.
3. Enable **chop-chop**. Allow notifications if you want the Stop control in the notification drawer. Notification denial does not block monitoring.
4. Hold the phone firmly and make two short chopping strokes. Start with Normal sensitivity; try High for gentler strokes or Low if normal handling triggers it.
5. Stop from the switch or notification. Rebooting, force-stopping, or system process termination requires enabling detection again.

The accelerometer is the only required motion sensor. A wake-up accelerometer is preferred when available; without one, detection may stop during screen-off suspend. The app reports this capability and does not hold a permanent wake lock. No camera preview, recording, network access, or camera runtime permission is used by the torch-only path.

Manual-only use ends when leaving the activity. Monitoring keeps a foreground service alive after leaving the app. Practice is limited to the visible activity and temporarily suppresses flashlight gestures.

If flashlight access fails or times out, use **Retry connection** to refresh availability. Monitoring can stay enabled while the connection is checked.

## Verification

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
.\gradlew.bat :app:connectedDebugAndroidTest
```

The connected task needs an emulator or device. See [validation results](docs/VALIDATION.md#improvements-validation-2026-11-16) for what was actually checked and what still needs a physical phone.

## Publish an APK

Run `./scripts/setup-signing.ps1` once to create a persistent release key in the Git-ignored `.signing` directory. Subsequent runs reuse the key. Back up this directory securely, including `release.properties`, to preserve the ability to update installed releases.

Build locally with `./gradlew.bat :app:assembleRelease --no-configuration-cache`. The signed APK is written to `app/build/outputs/apk/release/app-release.apk`. Local builds default to version 1.1.0 / code 2. Pass increasing `-PversionCode` and `-PversionName` values when distributing later local builds, using a code above the last installed release.

For GitHub publishing, install and authenticate the GitHub CLI with `gh auth login`, then run `./scripts/setup-signing.ps1 -UploadToGitHub`. This uploads the existing key and passwords as the four repository secrets `FASTTORCH_KEYSTORE_BASE64`, `FASTTORCH_STORE_PASSWORD`, `FASTTORCH_KEY_ALIAS`, and `FASTTORCH_KEY_PASSWORD`. The script sends secret values through standard input. Keep the same key for all releases.

Once the workflow is on the default branch and secrets are configured, open **Actions → Publish APK → Run workflow**. Each successful run executes unit tests and release lint, builds and verifies a signed APK, and publishes `FastTorch.apk` with a SHA-256 checksum. It creates a new release and marks it as latest. Version codes use `run number * 1000 + attempt`, so new runs and retries get increasing codes. Rerun the newest run when distributing updates.

Switching from the earlier debug APK to the release signing key requires uninstalling the debug app once, which clears its settings. Subsequent releases signed with this key can update it in place. GitHub Actions still requires the account billing lock to be cleared if it remains active.

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
