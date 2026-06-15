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

## Source Layout

Production code lives under:

```text
app/src/main/java/dev/worxbend/airgradient/
```

The project will follow the package boundaries in `TECH.md`: presentation code depends on domain contracts, data code implements repositories, and domain logic remains free of Android framework dependencies.
