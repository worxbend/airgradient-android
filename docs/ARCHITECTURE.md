# Architecture

This project uses a pragmatic Clean Architecture and MVVM shape.

```text
presentation -> domain <- data
```

Current baseline:

- `MainActivity` is a thin Compose entry point.
- `AirGradientTheme` centralizes Material 3 theme setup and dynamic color support.
- `DashboardScreen` is a state-driven, previewable Compose dashboard with adaptive AQI, comfort, pollutant, monitoring, loading, warning, error, and unconfigured states.
- `domain/model` contains immutable snapshot, metric, status, theme, and trend models.
- `domain/sensors` contains URL normalization, threshold classification, AQI fallback, trend calculation, and metric creation.
- `domain/error` and `domain/repository` define typed AirGradient failures plus air-quality and settings repository contracts.
- `data/airgradient` contains the Retrofit API, remote data source, DTO wrapper, mapper, and repository implementation for `/measures/current`.
- `data/settings` contains the DataStore preferences delegate, settings data source, and settings repository implementation.
- `data/notifications` contains Android notification-channel setup and notification delivery.
- `core/network` and `core/time` contain app-wide network construction and injectable time access.
- `core/dispatchers` contains injectable coroutine dispatcher grouping for ViewModels and use cases.
- `presentation/dashboard` contains the dashboard UI state model, presentation formatting, monitoring summary, and ViewModel refresh orchestration.
- `presentation/settings` contains the settings form, Android 13+ notification permission request, monitoring controls, and settings ViewModel.
- `service` contains the always-on foreground monitoring service foundation, service controller, persistent status notification, and reusable monitoring loop runner.
- `worker` contains battery-friendly WorkManager scheduling and the periodic check worker.

Planned package responsibilities:

- `presentation`: Compose routes, screens, components, ViewModels, immutable UI state.
- `domain`: sensor models, thresholds, AQI fallback, trend calculation, URL normalization, metric creation, repository interfaces.
- `data`: Retrofit/OkHttp API, DTO mapping, typed network errors, DataStore settings persistence.

Networking, persistence, and sensor business rules must not run inside Composables.

## Data Flow

The repository normalizes the configured server URL with `DeviceUrlNormalizer`, builds a Retrofit API with a trailing-slash base URL, and requests `measures/current` exactly once. The remote data source maps transport failures to `AirGradientError` values and only returns DTOs for successful object JSON payloads.

The mapper accepts flexible JSON payloads rather than fixed DTO fields because AirGradient-compatible devices and the reference apps support multiple aliases and nested wrappers. It looks for top-level aliases first, then recursively searches objects and arrays, accepts JSON numbers and numeric strings, ignores unsupported values, and derives fallback AQI from PM2.5 when an explicit AQI is absent.

## Settings Persistence

Settings are represented by the pure domain `AppSettings` model and exposed through `SettingsRepository.settings` as a `Flow`. The DataStore implementation persists the normalized device base URL, refresh interval, notification toggle, minimum alert severity, recovery/unreachable alert preferences, and theme mode.

The settings repository normalizes device URLs before storage. Blank input clears the configured device, bare hosts gain `http://`, and invalid URLs return `SaveDeviceUrlResult.Invalid` without changing the stored value. When Settings saves a blank device URL, `SettingsViewModel` immediately stops monitoring through `AirQualityMonitoringServiceController` so foreground service and WorkManager state are reconciled through the same controller path used by explicit stop actions. Refresh intervals are clamped to the source-derived `5..3600` second range. Android intentionally defaults notifications to disabled, minimum alert severity to Warning, recovery/unreachable alerts to enabled, and theme mode to system.

## Dashboard State

`DashboardViewModel` observes `SettingsRepository` through `ObserveSettingsUseCase`. When no device URL is configured it emits `DashboardUiState.Unconfigured` and does not fetch. When configured, it performs an initial refresh and starts an active-screen auto-refresh loop using the stored interval.

Refreshes are guarded by a coroutine mutex so manual refresh and timer refresh cannot overlap. Successful readings keep both the latest and previous snapshots in memory so `SensorMetricFactory` can produce trend-aware metrics. Fetch failures do not erase the last successful reading: the ViewModel emits `ContentWithWarning` when possible, or `Error` when no snapshot has ever loaded.

## Notifications

Notification decision rules live in pure Kotlin under `domain/notifications`. `NotificationDecisionEngine` evaluates
current air-quality condition, fetch failures, stale data, cooldowns, severity escalation, persistent degraded readings,
and recovery confirmation against a persisted `NotificationState`.

`NotificationPolicyFactory` derives the runtime policy from `AppSettings`, so dashboard refreshes, the foreground
service loop, and WorkManager checks all honor the same minimum severity, recovery-alert, and device-unreachable-alert
preferences.

`data/notifications/NotificationStateRepositoryImpl` stores notification decision state in a dedicated DataStore file so
cooldown and recovery state survive process restart. `DashboardViewModel`, the foreground service, and the WorkManager
worker all use the same decision engine and state repository. Successful checks update recovery/cooldown state, while
failed checks evaluate unreachable-device alerts and stale-data alerts from the same persisted state. Unreachable-device
alerts take priority on the repeated-failure threshold; otherwise a failed check can notify that the last successful
reading is stale. Disabling notifications or clearing the device URL clears the persisted decision state.

Android delivery is isolated in `data/notifications/AndroidNotificationMessageDispatcher`. It creates the air-quality
alert channel, checks `POST_NOTIFICATIONS` on Android 13+, uses deterministic notification IDs by message type/key, and
never sends when permission is missing. The legacy in-memory alert policy has been removed so dashboard refreshes,
foreground monitoring, and WorkManager checks all use the persisted decision engine.

## Foreground Monitoring

Always-on monitoring is hosted by `service/AirQualityMonitoringService`, which is declared as a `dataSync`
foreground service. The service starts foreground immediately with a persistent monitoring notification, owns a
structured coroutine scope, and delegates each device check to `MonitoringLoopRunner`.

`AirQualityMonitoringServiceController` is the only app-facing gateway for start, stop, and refresh-now commands. It
validates that a device URL is configured and that Android 13+ notification permission is available before starting
foreground monitoring. Settings and dashboard UI routes request notification permission through the route layer, then
their ViewModels call the controller instead of constructing service intents directly.

`MonitoringLoopRunner` reuses `GetCurrentMeasurementUseCase`, `NotificationDecisionEngine`,
`NotificationStateRepository`, and `NotificationMessageDispatcher`. It prevents overlapping checks with a mutex, clears
notification decision state when alerts are disabled, persists cooldown/recovery state when alerts are enabled, and
returns typed `MonitoringTickResult` values for the service to render in the persistent notification.

The dashboard observes `MonitoringSettings` and `MonitoringRuntimeState`, then renders a compact monitoring card with
the current mode, configured interval, last background check, last successful background reading, and quick start/stop
actions.

## Battery-Friendly Monitoring

Battery-friendly monitoring is scheduled by `worker/AirQualityWorkerScheduler` as unique periodic WorkManager work with
a connected-network constraint. It is only started through `AirQualityMonitoringServiceController`, which validates that
a device URL is configured, stops any active foreground-service command path, persists `BatteryFriendlyPeriodic` mode,
and schedules the worker at the persisted periodic interval.

`worker/AirQualityCheckWorker` is a thin WorkManager adapter. It loads
`BatteryFriendlyMonitoringCheckRunner` from `AppGraph`, and that pure Kotlin runner refuses to run when mode is no
longer `BatteryFriendlyPeriodic`, disables monitoring if the device URL has been removed, and delegates the actual fetch
and notification decision path to `MonitoringLoopRunner`. The worker intentionally treats each scheduled execution as
best-effort success after the runner handles the result so WorkManager keeps the periodic schedule. WorkManager intervals
are 15 minutes or longer and are inexact by Android design.

Completed foreground-service and WorkManager checks are recorded in a separate monitoring runtime DataStore. Skipped
ticks do not update the timestamp because no fetch attempt completed. The dashboard uses this operational state for
visibility only; historical sensor readings are still not persisted.
