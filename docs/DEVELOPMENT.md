# Development

## Local Toolchain

- Kotlin and Jetpack Compose are the only supported application implementation stack.
- Use the repository Gradle wrapper for all builds.
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

Run release assembly before release-readiness checkpoints:

```bash
./gradlew assembleRelease
```

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
