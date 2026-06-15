# Architecture

This project uses a pragmatic Clean Architecture and MVVM shape.

```text
presentation -> domain <- data
```

Current baseline:

- `MainActivity` is a thin Compose entry point.
- `AirGradientTheme` centralizes Material 3 theme setup and dynamic color support.
- `DashboardScreen` is a temporary previewable Compose surface that will be replaced by state-driven dashboard components.

Planned package responsibilities:

- `presentation`: Compose routes, screens, components, ViewModels, immutable UI state.
- `domain`: sensor models, thresholds, AQI fallback, trend calculation, URL normalization, repository interfaces.
- `data`: Retrofit/OkHttp API, DTO mapping, typed network errors, DataStore settings persistence.

Networking, persistence, and sensor business rules must not run inside Composables.
