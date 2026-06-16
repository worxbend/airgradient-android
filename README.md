# AirGradient Android

Native Kotlin and Jetpack Compose Android application for local AirGradient-compatible devices.

The app is being implemented incrementally from `PLAN.md`. The current codebase contains the Android project shell, Compose theme, quality tooling, source-derived sensor logic, the local `/measures/current` network repository, DataStore-backed settings persistence, state-driven dashboard UI with app-bar and pull-to-refresh actions, settings/navigation wiring, and foreground air-quality notifications.

## Requirements

- JDK 21
- Android SDK with API 36 installed
- Gradle wrapper from this repository

Set `JAVA_HOME` to a JDK 21 installation before running Gradle. The repository intentionally does not pin `org.gradle.java.home` to a machine-specific path so local builds and GitHub Actions use the same wrapper configuration.

If `ANDROID_HOME` is not exported, create a local `local.properties` file:

```properties
sdk.dir=/home/worxbend/Android/Sdk
```

## Commands

```bash
./gradlew test
./gradlew lint
./gradlew ktlintCheck
./gradlew detekt
./gradlew assembleDebugAndroidTest
./gradlew assembleDebug
./gradlew assembleRelease
```

GitHub Actions runs the same validation gates on pushes and pull requests to `main`, then packages the debug instrumentation APK and unsigned release APK as workflow artifacts.

## Local Device Setup

The application will fetch AirGradient readings from:

```text
<configured-device-base-url>/measures/current
```

Most AirGradient local-server installs use plain HTTP on the local network. The Android manifest deliberately enables cleartext traffic so addresses such as `http://192.168.1.201` and bare inputs such as `192.168.1.201` work after normalization.

Notifications are disabled by default. When enabled, Android 13+ devices request `POST_NOTIFICATIONS`; alerts are evaluated from foreground dashboard refreshes through the persisted notification decision engine. Cooldown and recovery state survive app process restarts. Background polling remains deferred.

## Privacy

The app stores local settings and notification decision state in DataStore and keeps current readings in memory. Historical sensor readings are not persisted, and no analytics, crash-reporting, cloud account, or remote proxy integration is configured.

Android cloud backup and device-transfer rules exclude the AirGradient settings and notification-state DataStore files so the configured local device URL and local alert history are not copied into a backup account or restored onto another device. See [docs/PRIVACY.md](docs/PRIVACY.md) for details.
