# AirGradient Android

Native Kotlin and Jetpack Compose Android application for local AirGradient-compatible devices.

The app is being implemented incrementally from `PLAN.md`. The current baseline contains the Android project shell, Compose theme, manifest permissions, quality tooling, source-derived sensor logic, the local `/measures/current` network repository, and DataStore-backed settings persistence. Dashboard state, full Compose UI, and notifications are planned as the next vertical slices.

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
