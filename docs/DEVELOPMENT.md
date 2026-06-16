# Development

## Local Toolchain

- Kotlin and Jetpack Compose are the only supported application implementation stack.
- Use the repository Gradle wrapper for all builds.
- Set `JAVA_HOME` to a JDK 21 installation. Do not commit `org.gradle.java.home`; it is machine-specific and breaks CI runners.
- Keep local SDK paths in `local.properties`; do not commit machine-specific paths.

## Validation

Run the focused checks before committing:

```bash
./gradlew test
./gradlew ktlintCheck
./gradlew detekt
./gradlew lint
```

Run `./gradlew clean build` before phase-level checkpoints when practical.

Compile/package Compose instrumentation tests with:

```bash
./gradlew assembleDebugAndroidTest
```

Run `./gradlew connectedDebugAndroidTest` only when an emulator or physical device is attached.

Run release assembly before release-readiness checkpoints:

```bash
./gradlew assembleRelease
```

## Continuous Integration

GitHub Actions validates pushes and pull requests to `main` with:

```bash
./gradlew test ktlintCheck detekt lint
./gradlew assembleDebugAndroidTest
./gradlew assembleRelease
```

The workflow uploads the packaged debug instrumentation APK and unsigned release APK as artifacts. `connectedDebugAndroidTest` remains a local/device-lab command because the default workflow does not start an emulator.

## Source Layout

Production code lives under:

```text
app/src/main/java/dev/worxbend/airgradient/
```

The project will follow the package boundaries in `TECH.md`: presentation code depends on domain contracts, data code implements repositories, and domain logic remains free of Android framework dependencies.

## Android Platform Notes

- Local AirGradient devices commonly expose HTTP, not HTTPS. `AndroidManifest.xml` sets `android:usesCleartextTraffic="true"` so normalized local URLs can be fetched.
- Android 12+ uses `Theme.AirGradient` splash-window attributes from `values-v31/styles.xml`; pre-Android 12 devices fall back to the normal launch theme.
- Android 13+ requires runtime `POST_NOTIFICATIONS`; the settings flow keeps alerts disabled if permission is denied.
- Always-on monitoring uses a `dataSync` foreground service with `FOREGROUND_SERVICE` and `FOREGROUND_SERVICE_DATA_SYNC` manifest permissions. The service must show its persistent notification immediately and must only be started through `AirQualityMonitoringServiceController`.
- Settings owns the full monitoring configuration surface, while the dashboard exposes quick always-on start/stop controls for configured states. Android 13+ notification permission must be granted before starting the service, and both routes must start monitoring through `AirQualityMonitoringServiceController`.
- Clearing the configured device URL from Settings must also stop monitoring through `AirQualityMonitoringServiceController`; do not bypass the controller with direct service intents or direct WorkManager calls.
- Battery-friendly monitoring uses WorkManager through `AirQualityWorkerScheduler` and `AirQualityCheckWorker`. It is limited to 15 minute or longer intervals, requires a configured device URL before scheduling, and must be described as inexact Android background scheduling rather than real-time polling.
- Smart alert preferences live in `AppSettings`: notifications enabled, minimum severity, notify-on-recovery, and notify-on-device-unreachable. Use `NotificationPolicyFactory.fromSettings()` for every notification decision path so dashboard, service, and worker behavior stays consistent.
- Failed dashboard, foreground-service, and WorkManager checks must evaluate the notification decision state for repeated unreachable-device alerts and stale-data alerts. Repeated unreachable-device alerts take priority when their threshold is met; otherwise stale-data alerts may fire from the last successful reading timestamp.
- DataStore settings live at `datastore/airgradient_settings.preferences_pb`; notification decision state lives at `datastore/airgradient_notification_state.preferences_pb`. Both backup XML files exclude those paths from cloud backup and device transfer.
