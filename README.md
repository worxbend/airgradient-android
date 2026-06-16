# AirGradient Android

Native Kotlin and Jetpack Compose Android application for local AirGradient-compatible devices.

The app is being implemented incrementally from `PLAN.md`. The current codebase contains the Android project shell, Compose theme, quality tooling, source-derived sensor logic, the local `/measures/current` network repository, DataStore-backed settings persistence, state-driven dashboard UI, settings/navigation wiring, and foreground air-quality notifications.

## Requirements

- JDK 21
- Android SDK with API 36 installed
- Gradle wrapper from this repository

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
./gradlew assembleDebug
```

The application will fetch AirGradient readings from:

```text
<configured-device-base-url>/measures/current
```

Notifications are disabled by default. When enabled, Android 13+ devices request `POST_NOTIFICATIONS`; alerts are evaluated from foreground dashboard refreshes and follow the reference consecutive-reading and cooldown policy. Background polling remains deferred.
