# Architecture

This project uses a pragmatic Clean Architecture and MVVM shape.

```text
presentation -> domain <- data
```

Current baseline:

- `MainActivity` is a thin Compose entry point.
- `AirGradientTheme` centralizes Material 3 theme setup and dynamic color support.
- `DashboardScreen` is a temporary previewable Compose surface that will be replaced by state-driven dashboard components.
- `domain/model` contains immutable snapshot, metric, status, theme, and trend models.
- `domain/sensors` contains URL normalization, threshold classification, AQI fallback, trend calculation, and metric creation.
- `domain/error` and `domain/repository` define typed AirGradient failures plus air-quality and settings repository contracts.
- `data/airgradient` contains the Retrofit API, remote data source, DTO wrapper, mapper, and repository implementation for `/measures/current`.
- `data/settings` contains the DataStore preferences delegate, settings data source, and settings repository implementation.
- `core/network` and `core/time` contain app-wide network construction and injectable time access.
- `core/dispatchers` contains injectable coroutine dispatcher grouping for ViewModels and use cases.
- `presentation/dashboard` contains the dashboard UI state model, presentation formatting, and ViewModel refresh orchestration.

Planned package responsibilities:

- `presentation`: Compose routes, screens, components, ViewModels, immutable UI state.
- `domain`: sensor models, thresholds, AQI fallback, trend calculation, URL normalization, metric creation, repository interfaces.
- `data`: Retrofit/OkHttp API, DTO mapping, typed network errors, DataStore settings persistence.

Networking, persistence, and sensor business rules must not run inside Composables.

## Data Flow

The repository normalizes the configured server URL with `DeviceUrlNormalizer`, builds a Retrofit API with a trailing-slash base URL, and requests `measures/current` exactly once. The remote data source maps transport failures to `AirGradientError` values and only returns DTOs for successful object JSON payloads.

The mapper accepts flexible JSON payloads rather than fixed DTO fields because AirGradient-compatible devices and the reference apps support multiple aliases and nested wrappers. It looks for top-level aliases first, then recursively searches objects and arrays, accepts JSON numbers and numeric strings, ignores unsupported values, and derives fallback AQI from PM2.5 when an explicit AQI is absent.

## Settings Persistence

Settings are represented by the pure domain `AppSettings` model and exposed through `SettingsRepository.settings` as a `Flow`. The DataStore implementation persists the normalized device base URL, refresh interval, notification toggle, and theme mode.

The settings repository normalizes device URLs before storage. Blank input clears the configured device, bare hosts gain `http://`, and invalid URLs return `SaveDeviceUrlResult.Invalid` without changing the stored value. Refresh intervals are clamped to the source-derived `5..3600` second range. Android intentionally defaults notifications to disabled and theme mode to system.

## Dashboard State

`DashboardViewModel` observes `SettingsRepository` through `ObserveSettingsUseCase`. When no device URL is configured it emits `DashboardUiState.Unconfigured` and does not fetch. When configured, it performs an initial refresh and starts an active-screen auto-refresh loop using the stored interval.

Refreshes are guarded by a coroutine mutex so manual refresh and timer refresh cannot overlap. Successful readings keep both the latest and previous snapshots in memory so `SensorMetricFactory` can produce trend-aware metrics. Fetch failures do not erase the last successful reading: the ViewModel emits `ContentWithWarning` when possible, or `Error` when no snapshot has ever loaded.
