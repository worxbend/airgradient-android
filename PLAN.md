# PLAN.md — Stylish Android AirGradient Application Implementation Plan

## Objective

Build a stylish Android application inspired by:

* `https://github.com/worxbend/airgradient-desktop`
* `https://github.com/worxbend/airgradient-gnome-extension`

The Android app must provide a modern mobile dashboard for AirGradient-compatible devices exposing a local HTTP endpoint at:

```text
/measures/current
```

The app must be visually polished, fast, offline-tolerant, and suitable for daily glanceable indoor-air monitoring.

The target product is not a direct port. It is an Android-native reinterpretation of the desktop dashboard and GNOME panel extension concepts.

## Mandatory Repository Analysis Phase

Before implementing Android features, the coding agent must clone both reference repositories into `/tmp`, scan them, and update this `PLAN.md` with findings.

```bash
rm -rf /tmp/airgradient-desktop /tmp/airgradient-gnome-extension

git clone https://github.com/worxbend/airgradient-desktop /tmp/airgradient-desktop
git clone https://github.com/worxbend/airgradient-gnome-extension /tmp/airgradient-gnome-extension
```

The agent must inspect at minimum:

```bash
find /tmp/airgradient-desktop -maxdepth 3 -type f | sort
find /tmp/airgradient-gnome-extension -maxdepth 3 -type f | sort
```

Required analysis targets:

```text
airgradient-desktop:
  - README.md
  - docs/ARCHITECTURE.md
  - docs/DEVELOPMENT.md
  - docs/LOCAL_SERVER.md
  - src/device.rs
  - src/config.rs
  - src/sensors/
  - src/notifications.rs
  - src/ui/dashboard.rs
  - src/ui/sensor_card.rs
  - src/ui/aqi_widget.rs
  - assets/dashboard.css
  - tests/

airgradient-gnome-extension:
  - README.md
  - docs/ARCHITECTURE.md
  - extension.js
  - airgradientSensors.js
  - airgradientAlerts.js
  - airgradientPresentation.js
  - airgradientHttpClient.js
  - desktopConfig.js
  - desktopConfigMonitor.js
  - airgradientPopup.js
  - prefs.js
  - stylesheet.css
  - tests/
```

After scanning, the agent must update `PLAN.md` with a new section:

```text
## Reference Implementation Findings
```

This section must document:

```text
- exact JSON payload fields used by the desktop app and extension
- sensor normalization rules
- threshold classification logic
- AQI fallback calculation logic, if present
- trend calculation behavior
- notification rules and cooldown behavior
- config schema
- refresh interval defaults
- error-handling behavior
- UI patterns worth porting to Android
- UI patterns that should be redesigned for mobile
- test cases that should be ported
```

No Android implementation work may begin before this scan is completed and committed.

Commit:

```bash
git add PLAN.md
git commit -m "docs: analyze AirGradient reference implementations"
git push
```

## Reference Implementation Findings

Reference repositories were cloned into `/tmp` and inspected before Android implementation:

```text
airgradient-desktop: db73350
airgradient-gnome-extension: 2812461
```

Scanned files included all mandatory docs, source files, CSS, and smoke tests listed in this plan. The two implementations intentionally share the same core behavior: the GNOME extension ports the desktop parser, thresholds, config contract, presentation mapping, and alert policy into plain JavaScript modules.

### Endpoint And JSON Payload Fields

Both references fetch the same local endpoint:

```text
GET <normalized_server_url>/measures/current
```

Observed sample AirGradient local-server payload fields:

```json
{
  "wifi": -46,
  "serialno": "ecda3b1eaaaf",
  "rco2": 447,
  "pm01": 3,
  "pm02": 7,
  "pm10": 8,
  "pm003Count": 442,
  "atmp": 25.87,
  "atmpCompensated": 24.47,
  "rhum": 43,
  "rhumCompensated": 49,
  "tvocIndex": 100,
  "tvocRaw": 33051,
  "noxIndex": 1,
  "noxRaw": 16307
}
```

Fields consumed by the parser:

```text
AQI:
  - aqi
  - air_quality_index

CO2:
  - rco2
  - co2
  - co2_ppm

PM1.0:
  - pm1
  - pm1.0
  - pm01
  - pm_1_0

PM2.5:
  - pm02
  - pm2_5
  - pm25
  - pm2.5

PM10:
  - pm10
  - pm10_0

PM0.3 count:
  - pm003Count
  - pm003_count
  - pm0_3_count

Temperature:
  - atmpCompensated
  - temperatureCompensated
  - temperature_compensated
  - atmp
  - temperature
  - temp
  - temp_c
  - temperature_c
  - temperatureC

Humidity:
  - rhumCompensated
  - humidityCompensated
  - humidity_compensated
  - rhum
  - humidity
  - hum
  - relative_humidity
  - rh
  - humidity_pct

TVOC:
  - tvoc
  - tvoc_ppb
  - tvoc_ppm
  - voc
  - tvocIndex
  - tvoc_index

NOx:
  - nox
  - no2
  - nox_ppb
  - noxIndex
  - nox_index
```

`wifi`, `serialno`, `tvocRaw`, and `noxRaw` are present in the sample payload but are not displayed by the current desktop or GNOME UIs.

### Sensor Normalization Rules

- Every sensor value is optional. Missing values are represented as `None`/`null` and displayed as `--`; missing values are never treated as zero.
- Numeric JSON numbers and numeric strings are accepted.
- Non-numeric strings, booleans, arrays, and objects are ignored for numeric measurements.
- The parser checks top-level keys first, then recursively searches nested objects and arrays. This supports payloads wrapped under objects such as `device.measurements[]`.
- Candidate key matching includes the exact candidate and a lower-case candidate variant.
- Temperature prefers compensated fields before raw device fields.
- Humidity prefers compensated fields before raw device fields.
- TVOC and NOx units are inferred:
  - if `tvocIndex` or `tvoc_index` is present, TVOC unit is `index`; otherwise parsed TVOC is treated as `ppb`.
  - if `noxIndex` or `nox_index` is present, NOx unit is `index`; otherwise parsed NOx is treated as `ppb`.
- PM2.5 is parsed once and reused for AQI fallback.
- The Android mapper should preserve these aliases and unit-inference rules. DTOs may be modeled around a `JsonObject` or custom serializer so recursive alias lookup remains possible.

### Threshold Classification Logic

Reference semantic colors map to Android statuses as:

```text
green  -> good
yellow -> moderate
orange -> warning
red    -> critical
purple -> critical
maroon -> critical
gray   -> unknown
```

Sensor thresholds:

```text
CO2:
  < 800 ppm      green
  < 1200 ppm     yellow
  < 2000 ppm     orange
  >= 2000 ppm    red

PM2.5:
  < 12 ug/m3     green
  < 35 ug/m3     yellow
  < 55 ug/m3     orange
  >= 55 ug/m3    red

TVOC:
  < 65           green
  < 220          yellow
  < 660          orange
  >= 660         red

NOx:
  < 20           green
  < 50           yellow
  < 150          orange
  >= 150         red

AQI:
  <= 50          green
  <= 100         yellow
  <= 150         orange
  <= 200         red
  <= 300         purple
  > 300          maroon in GNOME, gray in desktop threshold helper
```

PM1.0, PM10, PM0.3 count, temperature, and humidity do not have pollutant threshold classification in the dashboard card logic. They use fixed/informational presentation colors except humidity alert policy, which is notification-only.

Overall status in the GNOME extension chooses the worst status among AQI, PM2.5, CO2, TVOC, and NOx, ignoring missing values. If all are missing, overall status is gray/unknown.

### AQI Fallback Calculation

If `aqi` or `air_quality_index` is present, that value is used. If AQI is missing and PM2.5 is present, both references calculate a US AQI estimate using linear interpolation:

```text
PM2.5 concentration -> AQI
0.0-12.0            -> 0-50
12.1-35.4           -> 51-100
35.5-55.4           -> 101-150
55.5-150.4          -> 151-200
150.5-250.4         -> 201-300
250.5-500.4         -> 301-500
```

Values below zero clamp to `0`; values above `500.4` clamp to `500`. The docs call this a display convenience, not an official regulatory AQI replacement.

### Trend Calculation Behavior

- Trends compare the current reading with the immediately previous successful reading held in memory.
- Desktop keeps a rolling in-memory history of five snapshots, but current UI trend labels use only the newest previous snapshot.
- History is not persisted; restart clears trends.
- Missing current value: label is `No reading`, direction is neutral/none.
- Missing previous value: label is `No previous reading`, direction is neutral/none.
- Delta threshold: absolute delta `< 0.05` is stable and displays as `→ 0 <unit>`.
- Non-stable deltas display an up/down arrow, signed delta for increases, and unit.
- Delta formatting uses no decimals when absolute delta is `>= 10` or fractional component is within `0.05` of an integer; otherwise one decimal is shown.
- Metric value formatting uses no decimals when absolute value is `>= 100` or fractional component is within `0.05` of an integer; otherwise one decimal is shown.
- For pollutants and AQI, lower values are better. For temperature and humidity, the references mark higher values as better for trend coloring by passing `lowerIsBetter = false`; Android may preserve directional arrows while using neutral comfort coloring to avoid implying high humidity is always good.

### Notification Rules And Cooldown Behavior

Both references use the same alert policy:

- Notifications are controlled by a persisted `notifications_enabled` flag.
- Desktop/GNOME default this flag to `true`; Android product scope overrides this to `false` by default because mobile notification permission and user expectations are different.
- Missing/unknown values do not alert and clear consecutive state for that alert kind.
- A sensor alert requires `2` consecutive degraded readings before the first notification.
- Alert cooldown is `20 minutes` per alert kind.
- Severity escalation bypasses cooldown.
- Disabling notifications clears consecutive counters, active severities, last-sent timestamps, and fetch-failure count.
- A successful snapshot resets fetch-failure count.
- Offline alert triggers after `3` consecutive fetch failures and then follows the same 20-minute cooldown.

Alert thresholds:

```text
CO2:
  > 800 ppm      notice
  > 1200 ppm     warning
  > 2000 ppm     critical

AQI:
  > 100          notice
  > 150          warning
  > 200          critical

PM2.5:
  > 35           notice
  > 55           warning
  > 150          critical

TVOC:
  > 220          warning
  > 660          critical

NOx:
  > 50           warning
  > 150          critical

Humidity low:
  < 30%          notice

Humidity high:
  > 65%          notice
  > 75%          critical

Device offline:
  third consecutive fetch error -> warning
```

Reference notification titles and bodies should be adapted for Android notifications, keeping them concise and actionable. Android must request `POST_NOTIFICATIONS` on Android 13+ and should not start background polling unless the user enables notifications/background checks.

### Config Schema And URL Normalization

Shared desktop/GNOME JSON config shape:

```json
{
  "server_url": "http://192.168.1.201",
  "refresh_interval_secs": 30,
  "notifications_enabled": true,
  "start_minimized": false
}
```

Desktop config path:

```text
$XDG_CONFIG_HOME/airgradient-desktop/config.json
$HOME/.config/airgradient-desktop/config.json
./airgradient-desktop/config.json as final fallback
```

Android will use DataStore with equivalent app settings, excluding desktop-only `start_minimized`.

URL normalization:

- Trim whitespace.
- Empty text means not configured.
- Bare host/IP defaults to `http://`.
- Only `http` and `https` schemes are accepted.
- Host is required.
- Path, query, fragment, and trailing slash are removed.
- Stored value is a clean base URL.
- Desktop rejects invalid URLs with typed errors; GNOME returns `null` from `normalizeServerUrl`.
- GNOME additionally rejects whitespace inside the host.

Reference cases to preserve:

```text
""                                      -> null
"192.168.1.201"                         -> "http://192.168.1.201"
"http://192.168.1.201/"                 -> "http://192.168.1.201"
"http://192.168.1.201:80/foo?x=1#frag" -> "http://192.168.1.201:80"
"https://airgradient.local:8443/path"   -> "https://airgradient.local:8443"
"ftp://airgradient.local"               -> invalid
"http://"                               -> invalid
"http://air gradient.local"             -> invalid
```

### Refresh Interval Defaults

- Default refresh interval is `30` seconds.
- Minimum refresh interval is `5` seconds.
- Maximum refresh interval is `3600` seconds.
- GNOME clamps and rounds config values into this range.
- Desktop `RefreshInterval::new` rejects out-of-range values, while `RefreshInterval::clamped` exists for coercion.
- Android settings should store `30` seconds by default and clamp validated user input to `5..3600` unless the UI presents a fixed selector.
- HTTP request timeout is `8` seconds in both references.
- GNOME cancels an active request before starting a new one and ignores stale request IDs.
- Android dashboard refresh must avoid overlapping requests with a mutex or explicit in-flight guard.

### Error Handling Behavior

- Not configured state shows configuration guidance and does not fetch.
- HTTP non-2xx responses are treated as errors.
- Invalid JSON response is treated as a fetch error in desktop; GNOME parser catches JSON-string parse errors and produces an empty snapshot if called directly, but HTTP fetch callback catches transport/HTTP errors separately.
- Request timeout, connection errors, invalid URL, non-2xx status, and malformed JSON should map to typed Android domain errors.
- Missing optional sensor fields should not make the whole payload invalid.
- Fetch status copy includes:
  - `Fetching measurements...`
  - `Latest measurements loaded.`
  - `Fetch failed: <message>`
  - `Configure an AirGradient local-server URL.`
- Error state should not immediately erase a valid previous reading. The extension keeps the current snapshot in memory on fetch errors; Android should render content with a warning when possible.

### UI Patterns Worth Porting To Android

- Live dashboard first, settings only when needed.
- Large AQI hero card with status label, description, semantic color/gradient, and trend.
- Temperature and humidity as secondary comfort cards near AQI.
- Compact grid of pollutant cards for CO2, TVOC, NOx, PM0.3 count, PM1.0, PM2.5, and PM10.
- Status dot/accent color per classified metric.
- Last updated time and fetch status.
- Manual refresh action.
- Responsive wrapping grid rather than fixed-width rows.
- Semantic color palette based on green/yellow/orange/red/purple/maroon/gray.
- Missing values displayed as `--`.
- Settings fields for base URL, refresh interval, notifications, and configuration summary.
- Clean architecture split: parser/sensor logic and alert policy are framework-free and tested separately from UI adapters.

### UI Patterns To Redesign For Mobile

- Do not port GNOME popup dimensions or desktop row density directly; use a scrollable Compose dashboard with adaptive grids.
- Desktop two-column top row should become a phone-first vertical AQI hero followed by comfort cards, then pollutant grid.
- GNOME panel icon/status-color behavior should become a dashboard header status chip and optional notification, not a persistent top-bar concept.
- Desktop tray and `start_minimized` do not apply to Android.
- Libadwaita preference rows map to Material 3 text fields, switches, segmented buttons, and list rows.
- Hover/focus desktop affordances should be replaced with touch targets, accessible content descriptions, and Android pull/manual refresh interactions.
- Desktop CSS gradients should inspire but not dictate Compose theming; Android must support Material dynamic color, dark mode contrast, and compact card radii.

### Test Cases To Port

Domain/parser tests:

- Parse the sample local-server payload into all normalized fields.
- Prefer compensated temperature and humidity.
- Parse nested payloads and numeric strings.
- Missing optional fields map to null.
- Invalid or non-numeric measurement values are ignored.
- TVOC/NOx `Index` fields set `index` units.
- PM2.5 fallback AQI rounds sample PM2.5 `7` to approximately `29`.
- Explicit AQI field overrides fallback.
- AQI fallback clamps negative PM2.5 to `0` and high PM2.5 to `500`.

Threshold tests:

- CO2 boundaries: `799.9`, `800`, `1200`, `2000`.
- PM2.5 boundaries: `11.9`, `12`, `35`, `55`.
- AQI boundaries: `50`, `100`, `150`, `200`, `300`, `301`.
- Overall status chooses the worst classified value.

Trend/formatting tests:

- Missing current and missing previous readings produce neutral/unknown trend.
- Delta below `0.05` is stable.
- Lower pollutant readings are marked improved.
- Higher pollutant readings are marked worse.
- Value and delta formatting match reference rounding rules.

URL/config tests:

- Empty URL means unconfigured.
- Bare IP/host gains `http://`.
- Path/query/fragment/trailing slash are stripped.
- `http` and `https` are accepted.
- Unsupported schemes, missing host, and host whitespace are rejected.
- Refresh interval defaults to `30` and clamps to `5..3600`.
- Notifications default differs intentionally on Android: `false`.

Alert tests:

- First degraded reading does not notify; second consecutive reading does.
- Cooldown suppresses repeated alerts until 20 minutes pass.
- Severity escalation bypasses cooldown.
- Recovery clears consecutive alert state and requires two bad readings again.
- Third consecutive fetch failure creates a warning offline alert.
- Disabling notifications clears policy state.

Networking/repository tests:

- Successful `/measures/current` request appends the endpoint exactly once.
- HTTP 404/503 map to typed HTTP failures.
- Timeout maps to typed timeout.
- Unreachable host maps to typed unreachable error.
- Malformed JSON maps to malformed-payload error.

## Product Scope

The Android application must support:

```text
- Manual AirGradient device URL configuration
- Local-network HTTP fetch from /measures/current
- Current air-quality dashboard
- AQI headline card
- CO2, PM2.5, PM1.0, PM10, PM0.3, TVOC, NOx cards
- Temperature and humidity cards
- Last successful refresh timestamp
- Manual refresh
- Auto-refresh
- Trend indicators compared to previous reading
- Light, dark, and system theme modes
- Persistent settings
- Error state when device is unreachable
- Empty state before configuration
- Optional notifications for degraded air quality
```

Out of initial scope unless already simple after analysis:

```text
- Historical charts
- Cloud account integration
- Multi-device sync backend
- Login
- Wear OS
- Widgets
- Home Assistant integration
```

These may be planned as later phases.

## Recommended Android Stack

Use native Android with Kotlin.

Preferred stack:

```text
Language:
  - Kotlin

UI:
  - Jetpack Compose
  - Material 3
  - Dynamic color where available
  - Adaptive layouts for phones and tablets

Architecture:
  - Clean Architecture / pragmatic MVVM
  - Repository pattern
  - Unidirectional UI state flow
  - Kotlin coroutines
  - Flow / StateFlow

Networking:
  - Retrofit or Ktor Client
  - OkHttp
  - kotlinx.serialization or Moshi

Persistence:
  - DataStore Preferences for settings
  - Room only if historical samples are implemented

Background work:
  - WorkManager for periodic refresh, only if background notifications are enabled

DI:
  - Hilt or Koin
  - Prefer Hilt for standard Android architecture

Testing:
  - JUnit
  - Turbine for Flow tests
  - MockWebServer for HTTP contract tests
  - Compose UI tests
  - Detekt
  - ktlint
```

Avoid overengineering. Do not introduce Room, WorkManager, or complex background services unless the phase actually needs them.

## Target Architecture

Use this package structure:

```text
app/src/main/java/<package>/
  MainActivity.kt
  AirGradientApplication.kt

  core/
    network/
      HttpClientModule.kt
      NetworkResult.kt
    time/
      ClockProvider.kt
    config/
      AppDispatchers.kt

  data/
    airgradient/
      AirGradientApi.kt
      AirGradientRemoteDataSource.kt
      AirGradientRepositoryImpl.kt
      dto/
        AirGradientMeasureDto.kt
      mapper/
        AirGradientMeasureMapper.kt
    settings/
      SettingsDataSource.kt
      SettingsRepositoryImpl.kt

  domain/
    model/
      AirMeasureSnapshot.kt
      SensorMetric.kt
      SensorStatus.kt
      Trend.kt
      AppThemeMode.kt
    repository/
      AirGradientRepository.kt
      SettingsRepository.kt
    usecase/
      GetCurrentMeasurementUseCase.kt
      ObserveSettingsUseCase.kt
      SaveDeviceUrlUseCase.kt
      RefreshDashboardUseCase.kt
    sensors/
      SensorThresholds.kt
      AqiCalculator.kt
      TrendCalculator.kt
      DeviceUrlNormalizer.kt

  presentation/
    dashboard/
      DashboardScreen.kt
      DashboardViewModel.kt
      DashboardUiState.kt
      components/
        AqiHeroCard.kt
        SensorMetricCard.kt
        ComfortCard.kt
        RefreshStatusBar.kt
        DeviceErrorCard.kt
    settings/
      SettingsScreen.kt
      SettingsViewModel.kt
      SettingsUiState.kt
    theme/
      Color.kt
      Theme.kt
      Type.kt
      Shape.kt
```

Dependency direction:

```text
presentation -> domain <- data
```

The domain layer must not depend on Android framework classes.

## Config Schema

Mirror the reference apps conceptually, but adapt to Android DataStore.

Initial settings:

```kotlin
data class AppSettings(
    val serverUrl: String?,
    val refreshIntervalSeconds: Int,
    val notificationsEnabled: Boolean,
    val themeMode: AppThemeMode,
)
```

Defaults:

```text
serverUrl: null
refreshIntervalSeconds: 30
notificationsEnabled: false
themeMode: system
```

Device URL normalization rules:

```text
Input: 192.168.1.201
Output: http://192.168.1.201

Input: http://192.168.1.201/
Output: http://192.168.1.201

Input: http://192.168.1.201:80/foo?x=1#fragment
Output: http://192.168.1.201:80
```

The app must always fetch:

```text
<normalized_server_url>/measures/current
```

## Sensor Domain Model

Create an Android-native model based on findings from the reference repositories.

Baseline model:

```kotlin
data class AirMeasureSnapshot(
    val aqi: Int?,
    val pm003Count: Double?,
    val pm01: Double?,
    val pm25: Double?,
    val pm10: Double?,
    val co2: Double?,
    val tvoc: Double?,
    val nox: Double?,
    val temperatureCelsius: Double?,
    val humidityPercent: Double?,
    val measuredAt: Instant,
)
```

Each dashboard card should render:

```text
- display name
- value
- unit
- semantic status
- trend direction
- optional delta
- short interpretation
```

Statuses:

```text
good
moderate
warning
critical
unknown
```

Trend:

```text
up
down
stable
unknown
```

The exact threshold values must be copied from the reference implementation if present. If thresholds are absent or incomplete, document the gap in `PLAN.md` and implement clearly named conservative defaults in `SensorThresholds.kt`.

## UI Design Direction

The app must feel modern, premium, and Android-native.

Visual style:

```text
- Material 3 foundation
- soft gradient backgrounds
- glass-like translucent cards where appropriate
- large AQI hero card
- rounded cards
- strong typography hierarchy
- compact sensor grid
- clear color semantics
- smooth state transitions
- high contrast in dark mode
- no noisy skeuomorphism
```

The dashboard should use this layout:

```text
Top app area:
  - app name
  - device status
  - settings icon
  - refresh action

Hero section:
  - large AQI card
  - status label
  - last updated time
  - subtle gradient based on air quality

Comfort row:
  - temperature
  - humidity

Main sensor grid:
  - CO2
  - PM2.5
  - PM1.0
  - PM10
  - PM0.3
  - TVOC
  - NOx

Footer:
  - fetch status
  - next refresh countdown, if simple
```

Empty state:

```text
- show polished illustration or icon
- explain that the device URL must be configured
- primary button: Configure device
```

Error state:

```text
- show last known successful reading if available
- show unreachable-device warning
- allow retry
- do not erase valid previous data immediately
```

Settings screen:

```text
- device URL input
- test connection button
- refresh interval selector
- notifications toggle
- theme selector: System / Light / Dark
- about section
```

## Android Implementation Phases

## Implementation Progress

### Iteration 2 — Phase 1 Android Project Baseline

Implemented a native Android project baseline:

```text
- Gradle wrapper added with Gradle 8.14.3
- Android application module created under app/
- Kotlin + Jetpack Compose + Material 3 configured
- Compose previewable dashboard placeholder added
- Android manifest includes INTERNET and ACCESS_NETWORK_STATE
- adaptive launcher icon placeholder added
- ktlint and detekt configured
- baseline README and docs created
```

Tooling decisions:

```text
- Android Gradle Plugin 8.13.2 is used with Kotlin 2.3.20.
- AGP 9.x was not used because it rejects the explicit org.jetbrains.kotlin.android plugin, while TECH.md requires the standard Kotlin plugin stack.
- compileSdk/targetSdk are 36 because the local SDK has API 36 installed and selected AndroidX versions are pinned to API 36-compatible releases.
- Java bytecode targets JVM 17 while Gradle runs on the configured JDK 21.
- Hilt and KSP plugin aliases/dependencies are present in the catalog for later DI phases, but not applied in the app module until injection is actually introduced.
```

Validation passed:

```bash
./gradlew test ktlintCheck detekt lint
./gradlew clean build
```

### Iteration 3 — Phase 2 Domain Model and Sensor Logic

Implemented the first pure Kotlin domain slice:

```text
- AirMeasureSnapshot, SensorMetric, SensorStatus, Trend, and AppThemeMode domain models
- DeviceUrlNormalizer with reference-compatible base URL normalization
- SensorThresholds with CO2, PM2.5, AQI, TVOC, NOx, and overall status classification
- AqiCalculator with PM2.5 fallback interpolation and clamping
- TrendCalculator with missing/stable/up/down behavior and reference-style number formatting
- SensorMetricFactory for dashboard-ready immutable metric models
```

Behavior notes:

```text
- Domain code is Android-framework-free and lives under app/src/main/java/dev/worxbend/airgradient/domain/.
- Trend labels use Unicode arrows to preserve the reference implementation's presentation semantics; future Compose UI may replace these labels with icons while keeping the same direction model.
- TVOC and NOx metric units are currently emitted as "index" by SensorMetricFactory; the Phase 3 mapper will preserve payload unit inference before this reaches UI.
- PM1.0, PM10, PM0.3 count, temperature, and humidity remain informational/unknown status metrics, matching the reference findings.
```

Validation passed:

```bash
./gradlew test
./gradlew ktlintCheck
./gradlew detekt
./gradlew lint
./gradlew clean build
```

### Iteration 4 — Phase 3 Networking and Repository Layer

Implemented the local AirGradient data slice:

```text
- typed AirGradientError and AirGradientRepository contracts
- Retrofit API for GET /measures/current backed by OkHttp
- 8-second production request timeout via NetworkModule
- AirGradientRemoteDataSource with typed HTTP, timeout, unreachable, invalid URL, and malformed-payload failures
- AirGradientMeasureDto wrapping flexible JsonObject payloads
- AirGradientMeasureMapper with recursive alias lookup, numeric-string parsing, compensated temperature/humidity preference, AQI fallback, and TVOC/NOx unit inference
- AirGradientRepositoryImpl that normalizes configured URLs before fetching
- SensorMeasurementUnit added so TVOC/NOx cards can render index or ppb units from payload context
```

Validation passed:

```bash
./gradlew test
./gradlew ktlintCheck
./gradlew detekt
./gradlew lint
./gradlew clean build
```

### Iteration 5 — Phase 4 Settings Persistence

Implemented persistent app settings:

```text
- AppSettings domain model with Android defaults
- SettingsRepository contract with typed device URL save results
- ObserveSettingsUseCase and SaveDeviceUrlUseCase
- DataStore-backed SettingsDataSource and SettingsRepositoryImpl
- Context DataStore delegate for application wiring in later UI phases
- refresh interval clamping to 5..3600 seconds
- device URL normalization before persistence, with invalid URLs rejected without mutating stored state
- notifications defaulted to false and theme mode defaulted to system
```

Validation passed:

```bash
./gradlew test
./gradlew ktlintCheck
./gradlew detekt
./gradlew lint
./gradlew clean build
```

### Iteration 6 — Phase 5 Dashboard ViewModel

Implemented dashboard state management:

```text
- AppDispatchers abstraction for injectable coroutine dispatchers
- GetCurrentMeasurementUseCase and RefreshDashboardUseCase
- explicit DashboardUiState sealed model with Unconfigured, Loading, Content, ContentWithWarning, and Error states
- DashboardViewModel observing settings, triggering initial refresh, and auto-refreshing on the configured interval while active
- in-flight refresh guard to prevent overlapping manual and timer refreshes
- previous/latest successful snapshot retention for trend calculation
- transient fetch failures render ContentWithWarning when a last successful reading exists
- presentation formatter for deterministic last-updated labels and safe user-facing error text
- ViewModel unit tests covering unconfigured startup, content loading, stale-content warning, overlap prevention, and auto-refresh interval behavior
```

Behavior notes:

```text
- The Phase 5 slice keeps constructor-injected ViewModel wiring testable without introducing Hilt modules yet.
- Compose route/screen integration remains for Phase 6, where the placeholder dashboard will be replaced with state-driven UI components.
- Auto-refresh uses the configured interval from settings and does not start when no device URL is configured.
```

Validation passed:

```bash
./gradlew test
./gradlew ktlintCheck
./gradlew detekt
./gradlew lint
./gradlew clean build
```

### Iteration 7 — Phase 6 Compose Dashboard UI

Implemented the first production Compose dashboard surface:

```text
- DashboardScreen now renders immutable DashboardUiState instead of the placeholder-only UI
- Material 3 scaffold with app title, device/status subtitle, refresh action, and settings action
- adaptive dashboard grid for compact phones and wider/tablet widths
- AQI hero card with semantic color gradient, status pill, trend label, last-updated label, and refreshing indicator
- comfort cards for temperature and humidity
- pollutant metric cards for CO2, PM2.5, PM1.0, PM10, PM0.3 count, TVOC, and NOx
- stale-content warning panel for ContentWithWarning
- empty configuration panel with Configure device action
- loading skeleton state
- typed error panel with retry and settings actions
- previews for unconfigured, content, wide/tablet content, and error states
```

Behavior notes:

```text
- Screen-level composables receive state and callbacks only; networking and persistence remain outside Compose.
- The current MainActivity still uses the no-argument DashboardScreen fallback, which renders the unconfigured state until Phase 7 navigation/settings wiring or DI/app graph wiring is introduced.
- Pull-to-refresh was deferred in Phase 6 and implemented in Iteration 11; Phase 6 originally provided app-bar/manual refresh callbacks and visual refreshing state.
- Dashboard components were split under presentation/dashboard/components to keep detekt complexity limits passing.
```

Validation passed:

```bash
./gradlew test ktlintCheck detekt lint
./gradlew clean build
```

### Iteration 8 — Phase 7 Settings UI And App Wiring

Implemented the first production settings and navigation slice:

```text
- manual AppGraph wiring for shared repositories, use cases, and lifecycle-scoped ViewModel factories
- MainActivity now hosts AppRoot instead of the unconfigured dashboard fallback
- Compose Navigation added with dashboard and settings destinations
- DashboardRoute collects DashboardViewModel StateFlow and opens settings from the app bar/empty/error actions
- AppRoot observes persisted settings and applies system/light/dark theme changes immediately
- SettingsViewModel added with immutable SettingsUiState and explicit URL preview, save, and connection-test states
- settings screen added with device URL input, normalized endpoint preview, save action, test connection action, refresh interval selector, notification toggle, theme selector, and about section
- small domain use cases added for refresh interval, notifications, theme mode, and test-device-connection workflows
- SettingsViewModel tests cover loading persisted settings, URL normalization/save validation, connection test success/failure, and immediate persistence of refresh/notification/theme changes
```

Behavior notes:

```text
- Hilt is still deferred; the app uses a small manual graph because current dependency wiring remains simple and testable.
- Test connection fetches the normalized /measures/current endpoint without persisting the URL.
- Notifications remain a persisted foreground setting only; Android notification channels, runtime permission, alert policy, and optional background checks remain Phase 8/9 work.
- Pull-to-refresh was still deferred in Phase 7 and implemented in Iteration 11; dashboard manual refresh was available through the app bar at this point.
```

Validation passed:

```bash
./gradlew test
./gradlew ktlintCheck
./gradlew detekt
./gradlew lint
./gradlew clean build
```

### Iteration 9 — Phase 8 Foreground Notifications

Implemented the first Android notification slice:

```text
- pure Kotlin AirQualityAlertPolicy with source-derived thresholds, consecutive-reading rules, 20-minute cooldown, escalation bypass, recovery clearing, and offline failure alerts
- typed AirQualityAlert, AirQualityAlertKind, AirQualityAlertSeverity, and AirQualityAlertNotifier domain contracts
- AndroidAirQualityAlertNotifier with air-quality notification channel, stable per-kind notification IDs, launch intent, concise alert copy, and Android 13+ permission guard
- manifest POST_NOTIFICATIONS permission
- settings toggle now requests POST_NOTIFICATIONS on Android 13+ before persisting enabled notifications
- DashboardViewModel evaluates alerts after successful readings and fetch failures only when notifications are enabled
- disabling notifications or clearing configuration resets alert policy state
- unit tests for sensor alerts, cooldown, escalation, recovery, offline failure count, success reset, and ViewModel notification triggering
```

Behavior notes:

```text
- Notifications remain disabled by default.
- This slice does not add WorkManager or background polling; alerts are driven by foreground dashboard refreshes only.
- If Android notification permission is denied, the setting remains off and the settings screen shows a concise denial message.
```

Validation passed:

```bash
./gradlew test
./gradlew ktlintCheck
./gradlew detekt
./gradlew lint
./gradlew clean build
```

### Iteration 10 — Phase 10 Release Readiness Slice

Implemented a focused startup and documentation polish slice:

```text
- Android 12+ splash-window styling now uses the existing AirGradient launcher foreground, light/dark splash backgrounds, and semantic icon background color
- launcher background color now has a dark-mode resource variant
- README documents local AirGradient setup, deliberate local HTTP cleartext support, notification defaults, and release assembly
- development and protocol docs document release validation, local HTTP behavior, Android 12+ splash behavior, and Android 13+ notification permission behavior
```

Behavior notes:

```text
- Cleartext support was already present in AndroidManifest.xml through android:usesCleartextTraffic="true"; this iteration documents the product reason rather than changing networking behavior.
- No WorkManager/background polling was added because foreground notification evaluation is sufficient for the implemented Phase 8 scope.
```

### Iteration 11 — Dashboard Pull-To-Refresh

Implemented the remaining foreground dashboard refresh gesture:

```text
- DashboardScreen now wraps configured dashboard states in Material 3 PullToRefreshBox
- pull-to-refresh uses the same DashboardViewModel.refresh() path as the app-bar refresh action
- the gesture is enabled for content, stale-content warning, and error states
- unconfigured and initial-loading states do not expose a pull-to-refresh gesture
- the pull indicator follows the existing DashboardUiState isRefreshing flag for successful and stale-content states
```

Validation passed:

```bash
./gradlew test ktlintCheck detekt lint
./gradlew clean build
./gradlew assembleRelease
```

### Iteration 12 — Compose UI Test Coverage

Implemented the first instrumentation UI test slice:

```text
- added standard AndroidX test dependencies and Compose UI test manifest support
- added DashboardScreen Compose tests for unconfigured, content, and error states
- added SettingsScreen Compose tests for URL validation copy, save/test actions, connection success/failure copy, notification permission denial copy, refresh interval selection, and theme selection
- kept tests at the stateless screen boundary so they validate user-visible behavior without networking or persistence
```

Validation passed:

```bash
./gradlew ktlintCheck assembleDebugAndroidTest
./gradlew test ktlintCheck detekt lint
./gradlew clean build
./gradlew assembleRelease
```

Behavior notes:

```text
- `assembleDebugAndroidTest` compiles and packages the instrumentation tests; `connectedDebugAndroidTest` still requires an attached emulator or device and was not run in this headless iteration.
```

### Iteration 13 — Android CI Workflow

Implemented the missing continuous-integration slice:

```text
- added GitHub Actions workflow for pushes, pull requests, and manual runs
- CI sets up Temurin JDK 21 and the repository Gradle wrapper
- CI runs unit tests, ktlint, detekt, Android lint, instrumentation-test packaging, and release assembly
- CI uploads the debug instrumentation APK and unsigned release APK as workflow artifacts
- removed the committed machine-specific org.gradle.java.home path so local and CI builds use JAVA_HOME consistently
- documented CI behavior and local JDK configuration in README and development docs
```

Behavior notes:

```text
- connectedDebugAndroidTest still requires an attached emulator or device and is intentionally not part of the default GitHub-hosted workflow.
- CI runs validation steps separately from artifact-producing tasks to avoid the Gradle scheduling issue noted in earlier iterations when clean is queued alongside build outputs.
```

### Iteration 14 — Privacy And Backup Hardening

Implemented a focused privacy release-hardening slice:

```text
- Android Auto Backup and Android 12+ data extraction rules now exclude the AirGradient DataStore settings file
- local device URL, refresh interval, notification setting, and theme mode are no longer eligible for cloud backup or device transfer
- added docs/PRIVACY.md covering local-only network behavior, in-memory readings, stored settings, backup exclusions, and absence of telemetry/cloud integrations
- README and development docs now summarize the privacy and backup behavior
```

Behavior notes:

```text
- Sensor readings remain in memory only; historical storage is still out of scope.
- Backup exclusion deliberately covers the whole AirGradient settings DataStore rather than individual keys because DataStore stores preferences in a single protobuf file.
```

### Iteration 15 — Dashboard App-Bar Accessibility Polish

Implemented a focused dashboard interaction polish slice:

```text
- replaced text-based dashboard app-bar actions with Material icon buttons for refresh and settings
- added explicit accessibility content descriptions for the app-bar refresh and settings actions
- kept refresh disabled for unconfigured and initial-loading states while preserving pull-to-refresh behavior for configured states
- updated Compose UI tests to assert the icon actions by content description
- added the Compose Material Icons dependency through the existing version catalog
```

Validation passed:

```bash
./gradlew ktlintCheck assembleDebugAndroidTest
./gradlew test ktlintCheck detekt lint
./gradlew clean build
./gradlew assembleRelease
```

### Iteration 16 — Background Monitoring Capability Analysis

Documented the existing app state before implementing always-on monitoring:

```text
- inspected current package structure, settings, repository, notification, dashboard, and manifest behavior
- identified reusable foreground dashboard refresh, parser, settings, and notification components
- documented missing foreground-service, WorkManager, monitoring settings, and persistent notification-state pieces
- added the always-on monitoring behavior contract and migration path to this plan
```

### Iteration 17 — Monitoring Domain Model

Implemented the first pure Kotlin monitoring domain slice:

```text
- MonitoringMode, MonitoringSettings, MonitoringPolicy, MonitoringPermissionState, MonitoringStatus, and MonitoringTickResult
- foreground polling minimum of 30 seconds
- periodic background minimum of 15 minutes
- validation for configured device URL and Android notification permission requirements
- unit tests for monitoring mode capabilities and monitoring policy validation
```

### Iteration 18 — Monitoring Settings Persistence

Persisted monitoring-specific settings alongside existing app settings:

```text
- monitoring mode
- foreground polling interval
- battery-friendly periodic interval
- persistent notification preference
- repository APIs and DataStore mapping for monitoring settings
- tests for default values, persistence, and interval validation
```

### Iteration 19 — Persistent Notification State

Added restart-safe notification decision state:

```text
- NotificationState domain model
- NotificationStateRepository contract
- DataStore-backed NotificationStateRepositoryImpl
- JSON encoding for per-key cooldown timestamps, active condition, recovery candidate, and fetch-failure count
- tests for persistence, malformed state fallback, update transforms, and clearing state
```

### Iteration 20 — Smart Notification Decision Engine

Implemented the shared notification decision engine for dashboard, future foreground service, and future worker use:

```text
- NotificationPolicy, NotificationType, NotificationSeverity, NotificationMessage, and NotificationDecision
- AirQualityConditionFactory deriving overall status and dominant bad metric from snapshots
- decisions for degraded, critical, persistent, recovered, device-unreachable, and stale-data notifications
- cooldown, escalation bypass, dominant metric changes, recovery confirmation, and disabled-notification suppression
- unit tests for decision behavior and cooldown rules
```

### Iteration 21 — Persisted Dashboard Notification Decisions

Connected the foreground dashboard refresh path to the new persistent notification decision engine:

```text
- added NotificationMessageDispatcher domain contract and AndroidNotificationMessageDispatcher
- DashboardViewModel now evaluates successful readings and fetch failures with NotificationDecisionEngine
- dashboard notification cooldown/recovery state is saved through NotificationStateRepository
- disabling notifications or clearing the configured device URL clears persisted notification decision state
- Android cloud backup and device-transfer rules now also exclude notification decision state
- README, architecture, development, and privacy docs document the persisted decision path
```

Behavior notes:

```text
- Background polling remains deferred; notifications are still triggered only by foreground dashboard refreshes.
- The older AirQualityAlertPolicy remained for source-derived reference policy tests until Iteration 31 removed it.
```

### Iteration 22 — Foreground Monitoring Service Foundation

Implemented the first Android always-on monitoring runtime slice:

```text
- foreground-service and data-sync foreground-service permissions added to the manifest
- AirQualityMonitoringService declared with foregroundServiceType="dataSync"
- AirQualityMonitoringServiceController added as the start/stop/refresh gateway
- Android permission checker validates POST_NOTIFICATIONS before starting always-on monitoring
- persistent monitoring notification channel, stable notification ID, open-app action, Refresh now action, and Stop action added
- MonitoringLoopRunner performs one non-overlapping check through the existing AirGradient repository
- loop runner persists NotificationDecisionEngine state and dispatches smart alert messages on success/failure
- foreground service starts foreground immediately, owns a structured coroutine scope, polls at the persisted foreground interval, updates persistent status, and stops when mode is Off or device URL is removed
- AppGraph wires the controller, loop runner, and persistent status notification updater
- unit tests cover controller validation/start/stop/refresh and loop runner skip/success/failure/overlap behavior
```

Behavior notes:

```text
- Settings and dashboard controls for monitoring are still deferred; the controller exists for the next UI integration slice.
- Battery-friendly WorkManager periodic checks remain deferred.
- The persistent status notification is separate from smart alert notifications.
- Smart alert cooldown/recovery state is shared with dashboard refresh through NotificationStateRepository.
```

### Iteration 23 — Settings Foreground Monitoring Controls

Implemented the first user-facing always-on monitoring controls:

```text
- SettingsViewModel now observes persisted monitoring settings alongside app settings
- added use-case wrappers for observing monitoring settings and saving the foreground polling interval
- AirQualityMonitoringServiceController now exposes a testable MonitoringServiceController interface
- SettingsRoute requests POST_NOTIFICATIONS before starting always-on monitoring on Android 13+
- SettingsScreen includes an opt-in Monitoring section with status, foreground polling interval chips, Start always-on, Stop monitoring, and validation messages
- foreground monitoring intervals are limited to 30 sec, 1 min, 2 min, and 5 min in the UI
- ViewModel and Compose UI tests cover monitoring settings loading, interval persistence, start/stop delegation, validation errors, and user-visible controls
```

Behavior notes:

```text
- WorkManager battery-friendly monitoring remains deferred and is not exposed as a selectable mode yet.
- Dashboard monitoring status/start/stop controls remain deferred.
- The settings screen starts/stops monitoring through the controller; Composables still do not construct service intents.
```

### Iteration 24 — Dashboard Monitoring Controls

Implemented dashboard-facing always-on monitoring controls:

```text
- DashboardViewModel now observes MonitoringSettings alongside app settings
- dashboard content/error states include a DashboardMonitoringSummary
- dashboard route requests POST_NOTIFICATIONS before starting always-on monitoring on Android 13+
- dashboard content shows Background monitoring status, selected foreground interval, and Start always-on / Stop monitoring actions
- dashboard start/stop actions delegate through MonitoringServiceController; Composables still do not create service intents
- grouped dashboard monitoring and notification dependencies to keep ViewModel construction explicit without widening constructor sprawl
- unit tests cover monitoring summary propagation and controller delegation
- Compose UI tests cover the dashboard monitoring card and start action callback
```

Behavior notes:

```text
- Settings remains the full monitoring configuration surface.
- Dashboard exposes quick always-on start/stop only for configured dashboard states.
- Battery-friendly WorkManager monitoring and smart alert preference controls remained deferred at this point.
```

### Iteration 25 — Battery-Friendly WorkManager Monitoring

Implemented battery-friendly periodic monitoring:

```text
- added WorkManager runtime dependency using the official AndroidX 2.11.2 release
- added AirQualityWorkerScheduler with unique periodic work, UPDATE scheduling, and connected-network constraint
- added AirQualityCheckWorker that runs one MonitoringLoopRunner tick when mode is BatteryFriendlyPeriodic
- controller now starts battery-friendly monitoring, cancels periodic work when switching to always-on/off, and keeps service intents out of UI
- settings state and screen now expose battery-friendly interval chips for 15 min, 30 min, and 1 hour
- SettingsViewModel can persist periodic interval changes and start battery-friendly monitoring through the controller
- Compose UI and unit tests cover periodic interval selection, battery-friendly start callbacks, scheduler delegation, and validation errors
- README, architecture, and development docs now document inexact WorkManager behavior
```

Behavior notes:

```text
- WorkManager is only used for 15-minute-or-longer battery-friendly checks; 30-second checks remain foreground-service only.
- Battery-friendly monitoring requires a configured device URL but does not require Android 13+ notification permission because it has no persistent foreground notification.
- Smart alert decisions still use the shared persisted NotificationDecisionEngine path; if alerts are disabled, periodic checks only refresh decision state through the no-alert path.
- The worker disables monitoring and cancels the periodic schedule if the configured device URL is removed before a scheduled run.
- Last background check timestamps and smart alert preference controls remained deferred at this point.
```

### Iteration 26 — Smart Alert Preference Controls

Implemented user-configurable smart alert policy preferences:

```text
- AppSettings now persists minimum alert severity, recovery alert preference, and device-unreachable alert preference
- settings UI exposes Warning/Critical minimum severity chips plus recovery and unreachable alert switches
- dashboard refresh, foreground monitoring, and WorkManager monitoring all derive NotificationPolicy from AppSettings
- warning alerts can be suppressed by a Critical-only policy without disabling critical alerts
- device-unreachable and recovery notifications can be disabled independently while preserving decision state updates
- unit and Compose tests cover persistence, ViewModel updates, policy behavior, dashboard suppression, monitoring-loop suppression, and visible settings controls
- README, architecture, development, and privacy docs document the new smart-alert preferences
```

Validation passed:

```bash
./gradlew test ktlintCheck detekt lint
```

### Iteration 27 — Monitoring Runtime Check Timestamps

Implemented persisted monitoring runtime visibility:

```text
- added MonitoringRuntimeState and MonitoringRuntimeStateRepository as a separate operational-state contract
- added DataStore-backed MonitoringRuntimeStateRepositoryImpl with last checked, last successful check, last successful measurement, last failure, and consecutive failure count fields
- MonitoringLoopRunner now records completed success/failure ticks for both foreground service and WorkManager paths
- skipped ticks do not update the last-check timestamp because no fetch attempt completed
- DashboardViewModel observes monitoring runtime state alongside monitoring settings
- dashboard monitoring card now shows the last background check and last successful background reading when available
- battery-friendly dashboard status now shows the configured periodic interval and notes Android's inexact scheduling
- Android backup and device-transfer rules exclude the monitoring runtime DataStore file
- unit and Compose tests cover runtime persistence, loop-runner recording, dashboard summary mapping, and visible dashboard timestamp labels
```

Behavior notes:

```text
- Monitoring runtime state is operational metadata only; raw sensor history is still not persisted.
- Settings remains the full configuration surface, while dashboard provides quick status visibility and always-on start/stop controls.
- Minimum alert severity, recovery alerts, and device-unreachable toggles were implemented in Iteration 26 and are no longer deferred.
```

Validation passed:

```bash
./gradlew test ktlintCheck detekt lint
./gradlew assembleDebugAndroidTest assembleRelease
./gradlew clean build
```

### Iteration 28 — Battery-Friendly Worker Testability

Implemented a focused WorkManager reliability and testability slice:

```text
- extracted battery-friendly worker branch logic into BatteryFriendlyMonitoringCheckRunner
- kept AirQualityCheckWorker as a thin Android adapter that resolves the runner from AppGraph
- introduced a MonitoringTickRunner contract so MonitoringLoopRunner can be substituted in pure unit tests
- added unit tests for inactive-mode cancellation, missing-device-url disabling, and configured periodic tick execution
- updated architecture docs to reflect the worker adapter and pure runner split
```

Behavior notes:

```text
- WorkManager still treats each scheduled execution as successful after the runner handles mode/config/tick outcomes.
- The runtime behavior is intentionally unchanged; the slice makes periodic monitoring reconciliation directly testable.
```

Validation passed:

```bash
./gradlew :app:testDebugUnitTest --tests dev.worxbend.airgradient.worker.BatteryFriendlyMonitoringCheckRunnerTest
./gradlew ktlintCheck
./gradlew test ktlintCheck detekt lint
./gradlew assembleDebugAndroidTest assembleRelease
./gradlew clean build
```

### Iteration 29 — Device URL Clearing Monitoring Reconciliation

Implemented a focused settings/runtime reconciliation slice:

```text
- saving an empty device URL from Settings now immediately stops monitoring through MonitoringServiceController
- foreground monitoring is stopped and periodic WorkManager monitoring is cancelled by the existing controller path
- invalid device URL saves still report validation errors without mutating the stored URL or stopping monitoring
- SettingsViewModel state now reflects monitoring stopped after the configured device is cleared
- unit tests cover both immediate stop-on-clear and no-stop-on-invalid-save behavior
```

Behavior notes:

```text
- The foreground service and WorkManager runner still retain their defensive missing-device-url reconciliation paths.
- This slice makes user-initiated device removal immediate from the settings surface instead of waiting for the next service or worker check.
```

Validation passed:

```bash
./gradlew :app:testDebugUnitTest --tests dev.worxbend.airgradient.presentation.settings.SettingsViewModelTest
./gradlew test ktlintCheck detekt lint
./gradlew clean build
./gradlew assembleDebugAndroidTest
```

### Iteration 30 — Runtime Failure Count Decoupling

Implemented a focused monitoring metadata correctness slice:

```text
- MonitoringLoopRunner now counts completed failed background checks from MonitoringRuntimeStateRepository
- runtime consecutive failure counts continue to increment when smart alert notifications are disabled
- notification decision state still clears when alerts are disabled, so alert cooldown/recovery behavior remains independent from dashboard operational metadata
- unit tests cover disabled-alert failures preserving runtime failure counts while clearing notification state
```

Validation passed:

```bash
./gradlew :app:testDebugUnitTest --tests dev.worxbend.airgradient.service.MonitoringLoopRunnerTest
./gradlew test ktlintCheck detekt lint
./gradlew assembleDebugAndroidTest assembleRelease
./gradlew clean build
```

### Iteration 31 — Legacy Alert Policy Cleanup

Removed the superseded in-memory notification policy path:

```text
- deleted AirQualityAlertPolicy and its alert-specific domain models
- deleted the legacy AndroidAirQualityAlertNotifier adapter
- moved NotificationMessageDispatcher into its own domain contract file
- removed tests that only covered the unused legacy policy
- updated protocol and architecture docs to point at NotificationDecisionEngine as the single active notification path
```

Behavior notes:

```text
- Dashboard refresh, foreground monitoring, and WorkManager monitoring continue to share NotificationDecisionEngine, NotificationStateRepository, and AndroidNotificationMessageDispatcher.
- Source-derived alert behavior remains covered by NotificationDecisionEngine, NotificationCooldown, dashboard, and monitoring-loop tests.
```

### Iteration 32 — Stale Data Notification Runtime Wiring

Implemented the stale-data alert trigger path that already existed in the shared notification decision engine:

```text
- dashboard refresh failures now evaluate stale-data alerts after repeated-unreachable-device evaluation
- foreground-service and WorkManager monitoring failures now use the same stale-data fallback through MonitoringLoopRunner
- repeated device-unreachable notifications take priority when their failure threshold is met
- stale-data decisions preserve the persisted fetch-failure count from NotificationState
- unit tests cover stale-data dispatch from dashboard failures and monitoring failures, plus unreachable-alert priority
- README, architecture, and development docs now document active stale-data notification behavior
```

Behavior notes:

```text
- stale-data alerts are emitted only when smart notifications are enabled and a previous successful reading exists in NotificationState
- the stale-data cooldown remains the shared notification cooldown from NotificationDecisionEngine
```

Validation passed:

```bash
./gradlew :app:testDebugUnitTest --tests dev.worxbend.airgradient.service.MonitoringLoopRunnerTest
./gradlew :app:testDebugUnitTest --tests dev.worxbend.airgradient.presentation.dashboard.DashboardViewModelTest
./gradlew test ktlintCheck detekt lint
./gradlew assembleDebugAndroidTest assembleRelease
./gradlew clean build
```

### Phase 0 — Reference Scan and PLAN.md Update

Tasks:

```text
- clone both reference repositories into /tmp
- inspect source, docs, tests, styles, and workflows
- extract payload contract
- extract threshold logic
- extract trend logic
- extract notification behavior
- extract config behavior
- update PLAN.md with findings
- commit and push
```

Acceptance:

```text
- PLAN.md contains Reference Implementation Findings
- implementation decisions are grounded in scanned code
- no Android code is implemented before this commit
```

### Phase 1 — Android Project Baseline

Tasks:

```text
- verify existing Android project structure
- upgrade Gradle/Kotlin/Compose only if safe
- add Detekt and ktlint
- add basic CI if missing
- configure debug/release build variants
- create package structure
- add baseline README instructions
```

Acceptance:

```bash
./gradlew clean build
./gradlew test
./gradlew ktlintCheck
./gradlew detekt
```

Commit:

```bash
git add .
git commit -m "chore: prepare Android project baseline"
git push
```

### Phase 2 — Domain Model and Sensor Logic

Tasks:

```text
- implement AirMeasureSnapshot
- implement SensorMetric
- implement SensorStatus
- implement Trend
- implement DeviceUrlNormalizer
- implement SensorThresholds
- implement AqiCalculator if required
- implement TrendCalculator
- port smoke tests from reference repositories where applicable
```

Testing:

```text
- URL normalization tests
- JSON missing-field behavior tests
- threshold classification tests
- trend calculation tests
- AQI fallback tests
```

Acceptance:

```bash
./gradlew test
```

Commit:

```bash
git add .
git commit -m "feat: add AirGradient domain model and sensor logic"
git push
```

### Phase 3 — Networking and Repository Layer

Tasks:

```text
- implement AirGradientApi
- implement DTO matching /measures/current payload
- implement mapper DTO -> domain model
- implement repository
- handle timeout, malformed payload, connection refused, unknown host
- use MockWebServer tests
```

Rules:

```text
- network failures must return typed errors
- malformed payload must not crash UI
- missing optional fields must map to null
- repository must expose suspend fetch API
```

Acceptance:

```bash
./gradlew test
```

Commit:

```bash
git add .
git commit -m "feat: implement AirGradient network repository"
git push
```

### Phase 4 — Settings Persistence

Tasks:

```text
- implement DataStore-backed settings
- persist normalized device URL
- persist refresh interval
- persist notification toggle
- persist theme mode
- expose settings as Flow
```

Acceptance:

```text
- settings survive app restart
- invalid URL is rejected with user-facing validation
- bare IP address is accepted and normalized
```

Commit:

```bash
git add .
git commit -m "feat: persist AirGradient app settings"
git push
```

### Phase 5 — Dashboard ViewModel

Tasks:

```text
- implement DashboardUiState
- implement DashboardViewModel
- observe settings
- fetch current measurement
- support manual refresh
- support auto-refresh while screen is active
- retain previous reading for trends
- retain last successful reading on transient error
```

UI states:

```text
- Unconfigured
- Loading
- Content
- ContentWithWarning
- Error
```

Acceptance:

```text
- ViewModel tests cover all states
- refresh does not overlap duplicate requests
- errors are deterministic and typed
```

Commit:

```bash
git add .
git commit -m "feat: add dashboard state management"
git push
```

### Phase 6 — Compose Dashboard UI

Tasks:

```text
- implement Material 3 theme
- implement light/dark/system theme support
- implement AQI hero card
- implement sensor cards
- implement comfort cards
- implement status bar
- implement empty state
- implement error state
- add loading skeletons
```

Design requirements:

```text
- polished modern UI
- responsive to compact and medium width
- readable in dark mode
- accessible contrast
- no clipped text
- no hardcoded magic dimensions spread across files
```

Acceptance:

```text
- Compose preview for major states
- dashboard looks good on Pixel 7 / Pixel 8 / small Android phone / tablet width
- no visual overlap
```

Commit:

```bash
git add .
git commit -m "feat: build stylish Compose air quality dashboard"
git push
```

### Phase 7 — Settings UI

Tasks:

```text
- implement settings screen
- add URL input
- add Test Connection action
- add refresh interval selector
- add notifications toggle
- add theme selector
- add about/license section
```

Acceptance:

```text
- settings screen validates URL
- test connection shows success/failure
- theme changes immediately
- settings persist
```

Commit:

```bash
git add .
git commit -m "feat: add settings screen"
git push
```

### Phase 8 — Notifications

Tasks:

```text
- inspect reference notification policy first
- implement Android notification channel
- implement notification permission request for Android 13+
- implement alert policy and cooldown
- avoid notification spam
- notify only on meaningful degraded air quality
```

Rules:

```text
- notifications disabled by default
- cooldown must be explicit and tested
- notification content must be concise
- no background polling unless user enables notifications
```

Commit:

```bash
git add .
git commit -m "feat: add air quality notifications"
git push
```

### Phase 9 — Optional Background Refresh

Only implement if notifications require it.

Tasks:

```text
- use WorkManager
- respect Android background limitations
- use configured refresh interval only where feasible
- avoid battery-hostile polling
- document actual behavior
```

Commit:

```bash
git add .
git commit -m "feat: add background air quality checks"
git push
```

### Phase 10 — Polish, QA, and Release Readiness

Tasks:

```text
- add app icon
- add adaptive icon
- add splash screen
- tune animations
- tune typography
- add accessibility labels
- run Android lint
- run release build
- add screenshots to README
- document local AirGradient setup
```

Acceptance:

```bash
./gradlew clean build
./gradlew test
./gradlew lint
./gradlew assembleRelease
```

Commit:

```bash
git add .
git commit -m "chore: polish Android release readiness"
git push
```

## Testing Requirements

Minimum tests:

```text
Unit:
  - DeviceUrlNormalizerTest
  - SensorThresholdsTest
  - AqiCalculatorTest
  - TrendCalculatorTest
  - AirGradientMeasureMapperTest
  - SettingsRepositoryTest
  - DashboardViewModelTest

Network:
  - successful /measures/current response
  - missing optional fields
  - malformed JSON
  - HTTP 404
  - timeout
  - unreachable host

UI:
  - unconfigured screen
  - loading state
  - content state
  - error state
  - settings validation
```

Use MockWebServer for HTTP tests.

## Quality Gates

Every phase must pass:

```bash
./gradlew test
./gradlew ktlintCheck
./gradlew detekt
```

Before release:

```bash
./gradlew clean build
./gradlew lint
./gradlew assembleRelease
```

No phase is complete unless:

```text
- code compiles
- tests pass
- PLAN.md is updated if implementation reality differs
- commit is created
- commit is pushed
```

## Agent Operating Rules

The coding agent must work incrementally.

Rules:

```text
- read existing repository before changing files
- clone reference repositories into /tmp only
- do not vendor copied source from reference repositories
- port behavior, not code, unless license compatibility is explicitly reviewed
- keep commits small and phase-aligned
- run tests after each phase
- update PLAN.md continuously
- document deviations immediately
- prefer simple architecture over framework-heavy abstractions
- do not add history charts until current dashboard is stable
- do not add cloud features
```

## Final Deliverable

The final Android app must provide:

```text
- elegant Material 3 dashboard
- local AirGradient device support
- robust payload parsing
- typed sensor model
- status classification
- trend indicators
- persistent settings
- light/dark/system theme
- manual and automatic refresh
- safe error handling
- test coverage for core behavior
- updated PLAN.md with implementation details
```

# AirGradient Android Application Plan

## Goal

Build a stylish native Android application inspired by:

- `https://github.com/worxbend/airgradient-desktop`
- `https://github.com/worxbend/airgradient-gnome-extension`

The Android app should provide a polished mobile dashboard for an AirGradient device exposing the local-server endpoint at `/measures/current`. It should preserve the core workflow from the desktop and GNOME projects: configure a local device URL, fetch current readings, normalize the payload, classify air quality, show trends, and alert the user when indoor air needs attention.

## Source Analysis First

Before implementation, clone both reference projects into `/tmp` and scan them to turn this plan into a more detailed implementation plan.

```bash
rm -rf /tmp/airgradient-desktop /tmp/airgradient-gnome-extension
git clone https://github.com/worxbend/airgradient-desktop /tmp/airgradient-desktop
git clone https://github.com/worxbend/airgradient-gnome-extension /tmp/airgradient-gnome-extension
```

Analyze these areas:

- `/tmp/airgradient-desktop/README.md`
- `/tmp/airgradient-desktop/docs/ARCHITECTURE.md`
- `/tmp/airgradient-desktop/docs/LOCAL_SERVER.md`
- `/tmp/airgradient-desktop/src/device.rs`
- `/tmp/airgradient-desktop/src/config.rs`
- `/tmp/airgradient-desktop/src/sensors/`
- `/tmp/airgradient-desktop/src/notifications.rs`
- `/tmp/airgradient-desktop/src/ui/`
- `/tmp/airgradient-desktop/assets/dashboard.css`
- `/tmp/airgradient-gnome-extension/README.md`
- `/tmp/airgradient-gnome-extension/docs/ARCHITECTURE.md`
- `/tmp/airgradient-gnome-extension/airgradientSensors.js`
- `/tmp/airgradient-gnome-extension/airgradientAlerts.js`
- `/tmp/airgradient-gnome-extension/airgradientPresentation.js`
- `/tmp/airgradient-gnome-extension/airgradientHttpClient.js`
- `/tmp/airgradient-gnome-extension/desktopConfig.js`
- `/tmp/airgradient-gnome-extension/airgradientPopup.js`
- `/tmp/airgradient-gnome-extension/prefs.js`
- `/tmp/airgradient-gnome-extension/stylesheet.css`
- test fixtures and smoke tests in both repositories

Produce a short source-analysis report before coding:

- endpoint contract and accepted JSON fields
- nullable or optional sensor behavior
- exact threshold rules and status colors
- AQI fallback logic
- trend calculation rules
- notification severity and cooldown policy
- URL normalization rules
- refresh interval behavior
- desktop/GNOME UI patterns worth translating to Android
- assets, icons, wording, and licensing constraints

Then update this `PLAN.md` with concrete implementation details discovered from the scan.

## Product Scope

### MVP

- Native Android app written in Kotlin.
- Jetpack Compose UI using Material 3.
- Device setup screen accepting:
  - `192.168.1.201`
  - `http://192.168.1.201`
  - `http://192.168.1.201:80`
- URL normalization that stores a clean base URL.
- Current reading fetch from `<server_url>/measures/current`.
- Dashboard for:
  - AQI
  - temperature
  - humidity
  - CO2
  - TVOC
  - NOx
  - PM0.3 count
  - PM1.0
  - PM2.5
  - PM10
  - last successful update time
  - trend indicators compared with the previous reading
- Pull-to-refresh and manual refresh action.
- Configurable refresh interval.
- Light, dark, and system theme modes.
- Local notifications for poor air quality conditions.
- Offline, timeout, invalid URL, and invalid payload states.

### Post-MVP

- Home screen widget with AQI, CO2, PM2.5, and update time.
- Quick Settings tile for current AQI/status.
- Optional persistent status notification.
- Android Wear companion tile if useful later.
- Historical charts persisted locally.
- Device discovery through mDNS or subnet scan.
- Multiple device support.
- Export/share current reading snapshot.
- Remote proxy support if the desktop roadmap backend is built.

## Android Technology Choices

- Language: Kotlin.
- UI: Jetpack Compose + Material 3.
- Architecture: MVVM with unidirectional UI state.
- Async: Kotlin coroutines and Flow.
- HTTP: OkHttp + Kotlin serialization, or Retrofit backed by OkHttp.
- Persistence:
  - DataStore for settings.
  - Room only if historical readings become part of scope.
- Background work:
  - WorkManager for periodic refresh.
  - Foreground service only if Android background restrictions require visible ongoing monitoring.
- Notifications:
  - Android notification channels.
  - Runtime `POST_NOTIFICATIONS` permission on Android 13+.
- Dependency injection:
  - Hilt for a standard Android stack, or manual wiring if the codebase stays small.
- Testing:
  - JUnit for domain and parser tests.
  - MockWebServer for HTTP behavior.
  - Turbine for Flow tests if needed.
  - Compose UI tests for major dashboard states.

## Proposed Project Structure

```text
airgradient-android/
  app/
    src/main/
      AndroidManifest.xml
      java/dev/worxbend/airgradient/
        MainActivity.kt
        app/
          AirGradientApplication.kt
          AppGraph.kt
        data/
          config/
            AppSettings.kt
            SettingsRepository.kt
            DeviceUrlNormalizer.kt
          network/
            AirGradientApi.kt
            AirGradientHttpClient.kt
            AirGradientPayloadDto.kt
          sensors/
            AirMeasureSnapshot.kt
            AirQualityStatus.kt
            SensorThresholds.kt
            SensorParser.kt
            TrendCalculator.kt
          notifications/
            AirQualityNotifier.kt
            NotificationPolicy.kt
        domain/
          ObserveAirQualityUseCase.kt
          RefreshAirQualityUseCase.kt
        ui/
          theme/
          dashboard/
          settings/
          components/
          widgets/
        workers/
          RefreshWorker.kt
      res/
        drawable/
        mipmap/
        values/
  build.gradle.kts
  settings.gradle.kts
  README.md
```

## Domain Model

Model readings as immutable Kotlin data classes:

- `AirMeasureSnapshot`
  - `aqi: Int?`
  - `pm003Count: Int?`
  - `pm01: Double?`
  - `pm25: Double?`
  - `pm10: Double?`
  - `co2: Int?`
  - `tvoc: Int?`
  - `nox: Int?`
  - `temperatureC: Double?`
  - `humidityPercent: Double?`
  - `fetchedAt: Instant`
- `SensorValue`
  - label
  - value
  - unit
  - status
  - trend
- `AirQualityStatus`
  - good
  - fair
  - moderate
  - poor
  - unhealthy
  - unknown

The exact field names and thresholds must be confirmed from the cloned reference projects before implementation.

## UI Direction

The application should feel like a refined utility, not a generic template.

- First screen is the live dashboard when a device is configured.
- Setup screen appears only when no device URL exists or the stored URL fails validation.
- Use a compact app bar with:
  - refresh action
  - settings action
  - connection/status indicator
- Dashboard layout:
  - large AQI/status panel at the top
  - CO2, PM2.5, temperature, and humidity as prominent quick-read tiles
  - secondary pollutant grid below
  - last update and fetch state in a small status row
- Visual language:
  - Material 3 dynamic color support where available
  - custom air-quality semantic colors independent of dynamic color
  - smooth number/status transitions
  - compact cards with 8dp radius or less
  - clear iconography for refresh, settings, alerts, trends, thermometer, droplets, particles, and gas
- Avoid decorative marketing screens. The app should open directly into useful monitoring.
- Make the UI glanceable on phones and usable on tablets with adaptive grids.

## Data Flow

1. `SettingsRepository` exposes the configured base URL and refresh interval.
2. `RefreshAirQualityUseCase` builds `<server_url>/measures/current`.
3. `AirGradientHttpClient` fetches JSON with timeout and clear error types.
4. `SensorParser` converts DTOs into `AirMeasureSnapshot`.
5. `SensorThresholds` derives semantic status for each metric.
6. `TrendCalculator` compares the current snapshot with the previous in-memory snapshot.
7. `DashboardViewModel` exposes a single `DashboardUiState`.
8. Compose renders loading, success, stale, error, and empty-config states.
9. `NotificationPolicy` evaluates whether a notification should be sent.
10. `WorkManager` schedules background refresh when enabled.

## Permissions And Android Platform Concerns

- `INTERNET` is required.
- `ACCESS_NETWORK_STATE` is useful for clearer offline behavior.
- `POST_NOTIFICATIONS` is required at runtime on Android 13+.
- Cleartext HTTP must be deliberately supported for local AirGradient devices:
  - prefer a network security config limited to local/private IP ranges if feasible
  - document why local HTTP is required
- Background refresh interval must respect Android WorkManager limits.
- Handle battery optimization constraints gracefully rather than promising exact polling.

## Notifications

Mirror the reference app behavior after source analysis:

- severity-based notification titles and body text
- cooldown window to avoid repeated alerts
- no alerts for unknown or missing values
- notification settings toggle
- manual test notification from settings if useful
- separate channel for air quality alerts

Likely MVP alert triggers:

- high CO2
- unhealthy PM2.5 or AQI
- severe TVOC or NOx
- persistent fetch failures only if monitoring is explicitly enabled

## Settings

Settings screen:

- device base URL input with normalization preview
- refresh interval picker
- notification enable toggle
- theme mode selector: system, light, dark
- background monitoring toggle
- last fetch diagnostics:
  - resolved endpoint
  - last success
  - last error
- reset configuration action

## Testing Plan

Source-derived tests:

- port parser and threshold cases from desktop and GNOME tests
- URL normalization compatibility tests
- payload parsing with real sample JSON
- missing/null field behavior
- trend calculation tests
- notification cooldown tests

Android-specific tests:

- repository/DataStore tests
- HTTP success, timeout, invalid JSON, and non-2xx tests using MockWebServer
- ViewModel state transition tests
- Compose screenshot or UI assertions for:
  - no device configured
  - loading
  - healthy readings
  - poor air warning
  - stale data
  - network error
- WorkManager scheduling tests where practical

## Implementation Milestones

### Milestone 0: Reference Scan And Plan Update

- Clone both repositories into `/tmp`.
- Read the listed files and tests.
- Create `.airgradient-android/SOURCE_ANALYSIS.md`.
- Update this `PLAN.md` with exact thresholds, payload mapping, notification rules, and UI details.
- Decide whether any assets can be reused and verify licenses.

### Milestone 1: Android Skeleton

- Create Gradle Android project.
- Configure Kotlin, Compose, Material 3, lint, and unit test dependencies.
- Add package/application ID.
- Add base theme, app icon placeholder, and `MainActivity`.
- Create CI-friendly `./gradlew test` and `./gradlew lint` flow.

### Milestone 2: Domain And Network Layer

- Implement URL normalization.
- Implement DTOs and parser.
- Implement snapshot/status/trend models.
- Implement thresholds from source analysis.
- Implement HTTP client.
- Cover with unit tests and MockWebServer tests.

### Milestone 3: Settings Persistence

- Add DataStore settings.
- Build settings repository.
- Add settings screen with validation and preview.
- Persist refresh interval, notification toggle, theme mode, and background monitoring toggle.

### Milestone 4: Dashboard UI

- Build dashboard state model and ViewModel.
- Implement pull-to-refresh and manual refresh.
- Build AQI hero panel and metric cards.
- Add loading, error, empty, and stale states.
- Add adaptive phone/tablet layout.

### Milestone 5: Background Monitoring And Notifications

- Add notification channel and runtime permission flow.
- Implement notification policy and cooldown.
- Add WorkManager periodic refresh.
- Wire monitoring toggle and interval selection.
- Add tests for notification policy.

### Milestone 6: Polish

- Add app icon and air-quality icon set.
- Tune colors, typography, spacing, and animation.
- Add accessibility labels and content descriptions.
- Verify light/dark/system themes.
- Improve tablet layout.
- Add screenshots for README.

### Milestone 7: Release Readiness

- Add README with setup, local device requirements, and screenshots.
- Add privacy note: data stays local unless remote proxy support is added.
- Add release build config.
- Add signing instructions without committing secrets.
- Add GitHub Actions for test/lint/build.
- Produce debug APK and release APK/AAB.

## Open Decisions

- Minimum Android SDK.
- Whether the app should support multiple devices in the first release.
- Whether background monitoring should be opt-in or enabled by default after setup.
- Whether to include local history in MVP or defer it.
- Whether to use Hilt or manual dependency wiring.
- Whether to publish through GitHub Releases only or prepare Play Store metadata.

## Definition Of Done

- The app accepts and normalizes the same practical device URL formats as the reference projects.
- The app fetches `<server_url>/measures/current` reliably on a local network.
- Sensor parsing, thresholds, AQI fallback, and trends match the reference projects.
- Dashboard is polished, responsive, accessible, and useful at a glance.
- Notifications are controlled, actionable, and do not spam.
- Unit tests cover parser, thresholds, URL normalization, trends, and notification policy.
- HTTP tests cover success and common failure modes.
- README explains setup and local HTTP requirements.
- CI runs tests and lint successfully.




## Mandatory Android Technology Stack

The Android application must be implemented as a fully native Kotlin + Jetpack Compose application.

Hard requirements:

```text
Language:
  - Kotlin only

UI:
  - Jetpack Compose only
  - Material 3
  - Compose Navigation
  - Compose previews for important UI states

Architecture:
  - MVVM
  - Clean Architecture boundaries where useful
  - Repository pattern
  - Unidirectional data flow
  - immutable UI state models
  - Kotlin coroutines
  - Flow / StateFlow

Networking:
  - Retrofit + OkHttp
  - kotlinx.serialization or Moshi
  - MockWebServer for tests

Persistence:
  - Jetpack DataStore Preferences
  - Room only if historical samples are implemented later

Dependency Injection:
  - Hilt preferred
  - Koin acceptable only if the existing project already uses it

Background:
  - WorkManager only for optional notification/background-check phase

Quality:
  - ktlint
  - detekt
  - Android Lint
  - JUnit
  - Turbine
  - Compose UI tests
```

Forbidden unless explicitly justified in `PLAN.md`:

```text
- XML layouts
- legacy Android Views for primary UI
- Java source code
- WebView-based UI
- React Native
- Flutter
- Cordova/Capacitor
- imperative Activity-heavy architecture
- direct networking from Composables
- mutable UI state exposed from ViewModels
```

The implementation must treat Compose as the primary UI runtime. Activities should remain thin entry points. Business logic must live outside Composables.

## Kotlin + Compose Project Shape

Use this baseline structure:

```text
app/src/main/java/<package>/
  MainActivity.kt
  AirGradientApplication.kt

  core/
    dispatchers/
      AppDispatchers.kt
    network/
      NetworkModule.kt
      NetworkResult.kt
    time/
      ClockProvider.kt
    validation/
      UrlValidator.kt

  data/
    airgradient/
      AirGradientApi.kt
      AirGradientRemoteDataSource.kt
      AirGradientRepositoryImpl.kt
      dto/
        AirGradientMeasureDto.kt
      mapper/
        AirGradientMeasureMapper.kt
    settings/
      SettingsDataSource.kt
      SettingsRepositoryImpl.kt

  domain/
    model/
      AirMeasureSnapshot.kt
      SensorMetric.kt
      SensorStatus.kt
      Trend.kt
      AppThemeMode.kt
    repository/
      AirGradientRepository.kt
      SettingsRepository.kt
    usecase/
      GetCurrentMeasurementUseCase.kt
      RefreshDashboardUseCase.kt
      ObserveSettingsUseCase.kt
      SaveDeviceUrlUseCase.kt
    sensors/
      AqiCalculator.kt
      SensorThresholds.kt
      TrendCalculator.kt
      DeviceUrlNormalizer.kt

  presentation/
    AppRoot.kt
    navigation/
      AppNavGraph.kt
      AppDestination.kt
    dashboard/
      DashboardRoute.kt
      DashboardScreen.kt
      DashboardViewModel.kt
      DashboardUiState.kt
      components/
        AqiHeroCard.kt
        SensorMetricCard.kt
        ComfortMetricCard.kt
        DeviceStatusHeader.kt
        RefreshStatusBar.kt
        ErrorPanel.kt
        EmptyConfigurationPanel.kt
    settings/
      SettingsRoute.kt
      SettingsScreen.kt
      SettingsViewModel.kt
      SettingsUiState.kt
      components/
        DeviceUrlInput.kt
        ThemeSelector.kt
        RefreshIntervalSelector.kt
    theme/
      AirGradientTheme.kt
      Color.kt
      Shape.kt
      Type.kt
```

## Compose UI Rules

All UI must be implemented with composable functions.

Rules:

```text
- Screen-level Composables receive immutable UI state and callbacks.
- Screen-level Composables must not perform networking.
- Screen-level Composables must not read repositories directly.
- ViewModels expose StateFlow<UiState>.
- Use collectAsStateWithLifecycle.
- Use remember only for local ephemeral UI state.
- Use LaunchedEffect only for UI-side effects, not business workflows.
- Prefer stateless reusable components.
- Use PreviewParameterProvider for major previews.
```

Example screen boundary:

```kotlin
@Composable
fun DashboardRoute(
    viewModel: DashboardViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    DashboardScreen(
        state = state,
        onRefresh = viewModel::refresh,
        onOpenSettings = viewModel::openSettings,
        onRetry = viewModel::refresh,
    )
}
```

`DashboardScreen` must be previewable without a real ViewModel.

## Compose Design Requirements

The app must use a polished Material 3 visual language:

```text
- large AQI hero card
- responsive sensor grid
- animated refresh state
- smooth status transitions
- readable dark theme
- dynamic color support where available
- accessible contrast
- no clipped text
- no hardcoded strings inside reusable components
```

Use Compose APIs:

```text
- MaterialTheme
- Scaffold
- LazyVerticalGrid or adaptive LazyColumn layout
- Card
- ElevatedCard
- AnimatedContent
- Crossfade
- pull-to-refresh where available/stable
- WindowSizeClass if tablet support is implemented
```

## Gradle Requirements

The project must use modern Android Gradle configuration:

```kotlin
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.dagger.hilt.android")
    id("com.google.devtools.ksp")
}
```

Required dependencies:

```text
androidx.core:core-ktx
androidx.lifecycle:lifecycle-runtime-ktx
androidx.lifecycle:lifecycle-runtime-compose
androidx.activity:activity-compose
androidx.compose:compose-bom
androidx.compose.ui:ui
androidx.compose.ui:ui-tooling-preview
androidx.compose.material3:material3
androidx.navigation:navigation-compose
androidx.datastore:datastore-preferences
androidx.hilt:hilt-navigation-compose

org.jetbrains.kotlinx:kotlinx-coroutines-android
org.jetbrains.kotlinx:kotlinx-serialization-json

com.squareup.retrofit2:retrofit
com.squareup.okhttp3:okhttp
com.squareup.okhttp3:logging-interceptor

com.google.dagger:hilt-android
```

Testing dependencies:

```text
junit
app.cash.turbine:turbine
org.jetbrains.kotlinx:kotlinx-coroutines-test
com.squareup.okhttp3:mockwebserver
androidx.compose.ui:ui-test-junit4
androidx.test.ext:junit
androidx.test.espresso:espresso-core
```

## Acceptance Criteria Update

The implementation is acceptable only if:

```text
- the app is written in Kotlin
- the UI is written in Jetpack Compose
- no XML layout is used for core screens
- dashboard state is driven by StateFlow
- Composables are previewable
- business logic is testable without Android UI runtime
- networking and persistence are outside Composables
- tests pass
- lint passes
```

Required commands:

```bash
./gradlew clean build
./gradlew test
./gradlew lint
./gradlew ktlintCheck
./gradlew detekt
```

## Existing Application Background Monitoring Analysis

Phase 0 inspection was run from:

```text
/home/worxbend/Worxpace/airgradient-android
```

The working tree already contained an unrelated local modification to `AGENT_LOG.md`; this follow-up plan must not stage or rewrite that file.

### Current Package Structure

The Android app is a single-module project under `:app` with package `dev.worxbend.airgradient`.

Current production packages:

```text
app/
core/dispatchers/
core/network/
core/time/
data/airgradient/
data/airgradient/dto/
data/airgradient/mapper/
data/notifications/
data/settings/
domain/error/
domain/model/
domain/notifications/
domain/repository/
domain/sensors/
domain/usecase/
presentation/
presentation/dashboard/
presentation/dashboard/components/
presentation/navigation/
presentation/settings/
presentation/settings/components/
presentation/theme/
```

Current tests cover repository mapping/fetching, settings persistence, sensor thresholds, AQI fallback, trends, notification decision behavior, dashboard ViewModel behavior, and settings ViewModel behavior.

### Current Architecture Layers

The app already follows a pragmatic MVVM and Clean Architecture split:

```text
presentation -> domain <- data
```

- `MainActivity` only creates Compose content and obtains `AppGraph`.
- `AirGradientApplication` owns a lazy `AppGraph`.
- `AppGraph` performs manual dependency wiring. Hilt versions exist in `gradle/libs.versions.toml`, but the app module does not currently apply Hilt.
- Compose screens observe `StateFlow` through lifecycle-aware collection.
- Domain logic is mostly Android-free and testable with JUnit.
- Networking, parsing, settings persistence, threshold classification, and notification policy are outside Composables.

This architecture can support background monitoring, but the service/worker entry points need reusable use cases and repositories instead of dashboard-owned polling.

### Existing Notification Implementation

Existing classes:

```text
domain/notifications/NotificationDecisionEngine.kt
domain/notifications/NotificationPolicy.kt
domain/notifications/NotificationState.kt
domain/notifications/NotificationMessageDispatcher.kt
data/notifications/NotificationStateRepositoryImpl.kt
data/notifications/AndroidNotificationMessageDispatcher.kt
presentation/settings/SettingsRoute.kt
```

What exists:

- `NotificationDecisionEngine` evaluates current conditions, fetch failures, stale data, persistent degradation, recovery, cooldown, escalation, and dominant-metric changes.
- notification decision state is persisted through `NotificationStateRepositoryImpl`.
- device-unreachable alerts require repeated fetch failures.
- repeated alerts use a 20 minute cooldown.
- severity escalation and dominant-metric changes can bypass repeated-warning suppression.
- disabling notifications clears persisted notification decision state.
- `AndroidNotificationMessageDispatcher` creates one `air_quality_alerts` channel and checks `POST_NOTIFICATIONS` on Android 13+ before posting.
- the settings route requests `android.permission.POST_NOTIFICATIONS` before enabling the alert toggle.

Current limitation:

- alert notifications share one air-quality alert channel.
- stale-data notification evaluation exists in the decision engine but is not yet wired into a runtime trigger path.

### Existing Settings Persistence

Existing files:

```text
domain/model/AppSettings.kt
domain/repository/SettingsRepository.kt
data/settings/SettingsDataStore.kt
data/settings/SettingsDataSource.kt
data/settings/SettingsRepositoryImpl.kt
domain/usecase/ObserveSettingsUseCase.kt
domain/usecase/SaveDeviceUrlUseCase.kt
domain/usecase/SaveRefreshIntervalUseCase.kt
domain/usecase/SaveNotificationsEnabledUseCase.kt
domain/usecase/SaveThemeModeUseCase.kt
```

What exists:

- DataStore Preferences backs app settings.
- stored fields are device URL, dashboard refresh interval, smart alert toggle, and theme mode.
- device URLs are normalized before storage.
- dashboard refresh interval is clamped to `5..3600` seconds.
- notifications default to disabled.
- theme defaults to system.

Needed for monitoring:

- add monitoring mode, foreground polling interval, periodic background interval, and persistent-notification preference.
- keep monitoring-specific validation separate from dashboard refresh validation because always-on foreground polling has a 30 second minimum, while current dashboard refresh permits 5 seconds.
- make mode changes start/stop/reschedule background work through use cases/controllers, not directly from Composables.
- persist smart notification decision state separately from settings.

### Existing AirGradient Repository And API

Existing files:

```text
domain/repository/AirGradientRepository.kt
domain/usecase/GetCurrentMeasurementUseCase.kt
domain/usecase/RefreshDashboardUseCase.kt
data/airgradient/AirGradientApi.kt
data/airgradient/AirGradientApiFactory.kt
data/airgradient/AirGradientRemoteDataSource.kt
data/airgradient/AirGradientRepositoryImpl.kt
data/airgradient/dto/AirGradientMeasureDto.kt
data/airgradient/mapper/AirGradientMeasureMapper.kt
core/network/NetworkModule.kt
```

What exists:

- `AirGradientRepository.fetchCurrentMeasurement(serverUrl)` returns a typed `AirGradientFetchResult`.
- missing and invalid device URLs are modeled as domain failures.
- Retrofit fetches `/measures/current`.
- mapper supports flexible local-server payload aliases and nested JSON lookup.
- OkHttp currently uses an 8 second call/connect/read timeout.

Needed for monitoring:

- reuse `AirGradientRepository` for foreground service and WorkManager checks.
- ensure fetch timeout stays lower than the selected foreground polling interval.
- consider dedicated monitoring timeout constants if 30 second foreground checks require stricter failure bounds.

### Existing Sensor Classification Logic

Existing files:

```text
domain/sensors/AqiCalculator.kt
domain/sensors/DeviceUrlNormalizer.kt
domain/sensors/SensorMetricFactory.kt
domain/sensors/SensorThresholds.kt
domain/sensors/TrendCalculator.kt
domain/model/SensorMetric.kt
domain/model/SensorStatus.kt
```

What exists:

- threshold classification is already pure Kotlin.
- AQI fallback from PM2.5 is covered by tests.
- sensor metric creation includes trend-aware display metrics.
- `SensorThresholds.overallStatus(snapshot)` gives dashboard-level status.

Needed for monitoring:

- create a reusable air-quality condition model/factory for notification decisions.
- avoid duplicating dashboard presentation formatting inside service or worker code.
- keep status classification in domain, not in Android service classes.

### Existing Dashboard ViewModel

Existing file:

```text
presentation/dashboard/DashboardViewModel.kt
```

What exists:

- observes settings.
- when a device URL is configured, performs an initial refresh.
- starts a `viewModelScope` auto-refresh loop using the stored dashboard interval.
- prevents overlapping refreshes with a coroutine `Mutex`.
- evaluates `NotificationDecisionEngine` after foreground dashboard refresh success/failure.

Current limitation:

- dashboard refresh is UI-lifecycle scoped, not process-independent monitoring.
- `onCleared()` cancels the dashboard auto-refresh job.
- minimized behavior is not guaranteed, and killed/closed app behavior is not supported.
- dashboard currently owns alert evaluation for foreground refreshes; monitoring needs a shared decision engine.

Needed for monitoring:

- dashboard should show monitoring state and trigger start/stop use cases only.
- dashboard should not own background checks.
- alert evaluation should be moved or duplicated through a shared domain decision engine used by dashboard, foreground service, and worker.

### Existing Compose Settings Screen

Existing files:

```text
presentation/settings/SettingsRoute.kt
presentation/settings/SettingsScreen.kt
presentation/settings/SettingsUiState.kt
presentation/settings/SettingsViewModel.kt
presentation/settings/components/DeviceUrlInput.kt
presentation/settings/components/RefreshIntervalSelector.kt
presentation/settings/components/ThemeSelector.kt
```

What exists:

- device URL configuration.
- manual connection test.
- dashboard refresh interval selector.
- smart alert notifications toggle with Android 13 permission request.
- theme selector.
- permission denial is visible in settings UI.

Needed for monitoring:

- add monitoring mode controls.
- add foreground and battery-friendly interval controls.
- add explicit start/stop actions through ViewModel/use cases.
- surface validation errors for missing device URL and missing notification permission.
- keep service starts/stops outside Composables.

### Dependency Injection Setup

The app currently uses manual dependency wiring through:

```text
app/AppGraph.kt
```

Hilt plugin and library versions are present in `gradle/libs.versions.toml`, but `app/build.gradle.kts` does not apply Hilt and there are no `@Inject`, `@AndroidEntryPoint`, or Hilt modules in production code.

Migration options:

- prefer extending `AppGraph` for the first foreground-service implementation to minimize architecture churn.
- if service, worker, notification dispatching, and repositories make manual wiring too complex, introduce Hilt deliberately in one phase and update app/module setup consistently.
- do not mix partially configured Hilt annotations with manual factories.

### Android Manifest And SDK Baseline

Current `app/build.gradle.kts`:

```text
compileSdk = 36
minSdk = 26
targetSdk = 36
versionCode = 1
versionName = 0.1.0
```

Current manifest permissions:

```text
android.permission.ACCESS_NETWORK_STATE
android.permission.INTERNET
android.permission.POST_NOTIFICATIONS
```

Current manifest components:

```text
AirGradientApplication
MainActivity
```

Needed for always-on monitoring:

- add `android.permission.FOREGROUND_SERVICE`.
- add `android.permission.FOREGROUND_SERVICE_DATA_SYNC` for API 34+ foreground-service type requirements.
- declare `AirQualityMonitoringService` with `android:foregroundServiceType="dataSync"` unless a later implementation documents a more appropriate type.
- add any receiver declarations needed for notification actions.

### Existing WorkManager And Background Execution

There is currently no WorkManager dependency, worker class, foreground service, lifecycle service, broadcast receiver, alarm manager, raw service intent gateway, `GlobalScope`, `Thread`, or `Timer` based background execution.

Implications:

- always-on 30 second polling must be a new foreground service feature.
- WorkManager must be introduced only for battery-friendly periodic checks at 15 minutes or longer.
- foreground-service and WorkManager paths must share domain decision logic and notification state to avoid inconsistent alerts.

### What Can Be Reused

- `AirGradientRepository` and its DTO mapper.
- `DeviceUrlNormalizer` and settings device URL validation.
- `SensorThresholds`, `SensorMetricFactory`, `AqiCalculator`, and `TrendCalculator`.
- `AppDispatchers` and `ClockProvider` patterns.
- DataStore Preferences infrastructure.
- notification permission request pattern from `SettingsRoute`.
- current tests as examples for repository, policy, ViewModel, and Compose UI coverage.

### What Must Be Refactored Or Added

- add `domain/monitoring` models and validation policy.
- add monitoring settings persistence and repository APIs.
- add persistent notification state storage.
- replace the legacy alert policy with a persistent `NotificationDecisionEngine` that handles degradation, critical escalation, persistent bad conditions, recovery, unreachable device, stale data, cooldown, and deduplication.
- split Android notification channel creation and notification dispatching into reusable classes.
- add foreground service manifest support.
- add a service controller as the only foreground-service start/stop gateway.
- add a structured coroutine monitoring loop owned by the service.
- add persistent notification actions for open app, refresh now, and stop monitoring.
- integrate monitoring controls into settings.
- integrate monitoring status into the dashboard.
- add WorkManager only for battery-friendly periodic checks.
- add focused unit tests and Android-facing tests around service/controller/worker behavior where practical.

### Risks

- Android 13+ notification permission is required before starting always-on monitoring because the foreground service must show a visible notification.
- Android 14+ foreground-service type rules require correct manifest permission/type pairing.
- 30 second network polling may increase battery usage and should be opt-in with persistent visibility.
- foreground services can still be stopped by the system or user; persistent settings must reconcile actual service state on restart.
- local AirGradient devices may be reachable only on the current Wi-Fi network; unreachable detection must avoid noisy alerts.
- in-memory alert cooldown would spam after process restart unless notification state is persisted.
- dashboard refresh interval and monitoring interval have different minimums and should not share one validation rule.
- manual `AppGraph` may become large; introducing Hilt mid-feature should be a deliberate phase, not incidental churn.

### Exact Files And Classes Expected To Change

Likely existing files:

```text
PLAN.md
README.md
docs/ARCHITECTURE.md
docs/DEVELOPMENT.md
app/build.gradle.kts
gradle/libs.versions.toml
app/src/main/AndroidManifest.xml
app/src/main/java/dev/worxbend/airgradient/AirGradientApplication.kt
app/src/main/java/dev/worxbend/airgradient/app/AppGraph.kt
app/src/main/java/dev/worxbend/airgradient/core/network/NetworkModule.kt
app/src/main/java/dev/worxbend/airgradient/data/settings/SettingsDataSource.kt
app/src/main/java/dev/worxbend/airgradient/data/settings/SettingsRepositoryImpl.kt
app/src/main/java/dev/worxbend/airgradient/domain/model/AppSettings.kt
app/src/main/java/dev/worxbend/airgradient/domain/repository/SettingsRepository.kt
app/src/main/java/dev/worxbend/airgradient/presentation/dashboard/DashboardScreen.kt
app/src/main/java/dev/worxbend/airgradient/presentation/dashboard/DashboardUiState.kt
app/src/main/java/dev/worxbend/airgradient/presentation/dashboard/DashboardViewModel.kt
app/src/main/java/dev/worxbend/airgradient/presentation/settings/SettingsRoute.kt
app/src/main/java/dev/worxbend/airgradient/presentation/settings/SettingsScreen.kt
app/src/main/java/dev/worxbend/airgradient/presentation/settings/SettingsUiState.kt
app/src/main/java/dev/worxbend/airgradient/presentation/settings/SettingsViewModel.kt
```

Likely new production files:

```text
app/src/main/java/dev/worxbend/airgradient/domain/monitoring/MonitoringMode.kt
app/src/main/java/dev/worxbend/airgradient/domain/monitoring/MonitoringPolicy.kt
app/src/main/java/dev/worxbend/airgradient/domain/monitoring/MonitoringStatus.kt
app/src/main/java/dev/worxbend/airgradient/domain/monitoring/MonitoringTickResult.kt
app/src/main/java/dev/worxbend/airgradient/domain/monitoring/MonitoringPermissionState.kt
app/src/main/java/dev/worxbend/airgradient/domain/monitoring/MonitoringSettings.kt
app/src/main/java/dev/worxbend/airgradient/domain/repository/MonitoringSettingsRepository.kt
app/src/main/java/dev/worxbend/airgradient/domain/notifications/NotificationDecisionEngine.kt
app/src/main/java/dev/worxbend/airgradient/domain/notifications/NotificationPolicy.kt
app/src/main/java/dev/worxbend/airgradient/domain/notifications/NotificationDecision.kt
app/src/main/java/dev/worxbend/airgradient/domain/notifications/NotificationState.kt
app/src/main/java/dev/worxbend/airgradient/domain/notifications/NotificationType.kt
app/src/main/java/dev/worxbend/airgradient/domain/notifications/AirQualityConditionFactory.kt
app/src/main/java/dev/worxbend/airgradient/data/notifications/NotificationStateRepositoryImpl.kt
app/src/main/java/dev/worxbend/airgradient/presentation/notification/NotificationChannelFactory.kt
app/src/main/java/dev/worxbend/airgradient/presentation/notification/AndroidNotificationDispatcher.kt
app/src/main/java/dev/worxbend/airgradient/presentation/monitoring/MonitoringPermissionController.kt
app/src/main/java/dev/worxbend/airgradient/service/AirQualityMonitoringService.kt
app/src/main/java/dev/worxbend/airgradient/service/AirQualityMonitoringServiceController.kt
app/src/main/java/dev/worxbend/airgradient/service/AirQualityMonitoringNotificationFactory.kt
app/src/main/java/dev/worxbend/airgradient/service/PersistentStatusNotificationUpdater.kt
app/src/main/java/dev/worxbend/airgradient/service/MonitoringLoopRunner.kt
app/src/main/java/dev/worxbend/airgradient/service/MonitoringActionReceiver.kt
app/src/main/java/dev/worxbend/airgradient/worker/AirQualityCheckWorker.kt
app/src/main/java/dev/worxbend/airgradient/worker/AirQualityWorkerScheduler.kt
```

Likely new docs:

```text
docs/BACKGROUND_MONITORING.md
docs/NOTIFICATIONS.md
```

Likely new tests:

```text
app/src/test/java/dev/worxbend/airgradient/domain/monitoring/MonitoringPolicyTest.kt
app/src/test/java/dev/worxbend/airgradient/domain/monitoring/MonitoringModeTest.kt
app/src/test/java/dev/worxbend/airgradient/data/settings/MonitoringSettingsRepositoryTest.kt
app/src/test/java/dev/worxbend/airgradient/domain/notifications/NotificationDecisionEngineTest.kt
app/src/test/java/dev/worxbend/airgradient/domain/notifications/NotificationCooldownTest.kt
app/src/test/java/dev/worxbend/airgradient/domain/notifications/AirQualityConditionFactoryTest.kt
app/src/test/java/dev/worxbend/airgradient/service/MonitoringLoopRunnerTest.kt
app/src/test/java/dev/worxbend/airgradient/service/AirQualityMonitoringNotificationFactoryTest.kt
app/src/test/java/dev/worxbend/airgradient/service/PersistentStatusNotificationUpdaterTest.kt
app/src/test/java/dev/worxbend/airgradient/service/AirQualityMonitoringServiceControllerTest.kt
app/src/test/java/dev/worxbend/airgradient/worker/AirQualityCheckWorkerTest.kt
```

### Migration Path

1. Add monitoring domain models and validation without Android dependencies.
2. Extend DataStore-backed settings persistence with monitoring fields.
3. Introduce persistent notification state before service polling to avoid restart spam.
4. Build a domain notification decision engine that can be called by dashboard, foreground service, and WorkManager.
5. Split Android notification infrastructure into channels, dispatcher, persistent status factory/updater, and alert dispatching.
6. Add manifest permissions and foreground-service declaration.
7. Add a service controller and keep raw service intents out of ViewModels and Composables.
8. Add the foreground-service monitoring loop with structured concurrency and no overlapping ticks.
9. Add notification actions for stop and refresh-now.
10. Integrate monitoring controls in settings and monitoring status in dashboard.
11. Add WorkManager only after the foreground-service path is working, and keep it limited to 15 minute or longer battery-friendly checks.
12. Update README and docs once behavior is implemented and validated.

## Always-On Monitoring Product Behavior

This feature adds explicit background monitoring modes. The app must keep the existing foreground dashboard behavior, but dashboard refresh must not be treated as background monitoring.

### Monitoring Modes

Monitoring mode is represented in the domain layer as:

```kotlin
enum class MonitoringMode {
    Off,
    AlwaysOnForegroundService,
    BatteryFriendlyPeriodic,
}
```

The two background modes have different Android runtime contracts:

```text
Always-on monitoring:
  Implementation: Android foreground service
  User visibility: persistent notification is always visible while active
  Polling: supports 30-second checks
  Use case: near-real-time indoor-air monitoring

Battery-friendly periodic monitoring:
  Implementation: WorkManager
  User visibility: no always-visible monitoring notification required
  Polling: minimum interval is 15 minutes and execution is inexact
  Use case: coarse background checks with lower battery impact
```

WorkManager must never be used for 30-second polling. Any WorkManager UI or documentation must state that checks are not real-time and may run at or after the configured periodic interval according to Android scheduling policy.

### Defaults

```text
Monitoring mode: Off
Foreground polling interval: 30 seconds
Battery-friendly interval: 15 minutes
Persistent notification: enabled only while foreground monitoring is active
Smart alerts: disabled until the user enables notifications
Minimum foreground interval: 30 seconds
Minimum battery-friendly interval: 15 minutes
```

### Always-On Foreground Service Behavior

Always-on monitoring must behave as follows:

```text
- user explicitly enables it
- foreground service starts only after a valid device URL exists
- foreground service starts only after notification permission is available on Android 13+
- persistent notification appears immediately with a starting/checking state
- app checks the AirGradient device every configured foreground interval
- 30 seconds is the minimum allowed foreground interval
- persistent notification updates in place with current air-quality status
- alert notifications are emitted only by smart notification rules
- user can stop monitoring from the persistent notification
- user can request Refresh now from the persistent notification
- monitoring stops if the device URL is removed
- monitoring stops or refuses to start if configuration becomes invalid
- stopping monitoring updates mode to Off and removes the persistent notification
```

The persistent notification and alert notifications are separate:

```text
- persistent notification uses one stable notification ID and is updated in place
- smart alert notifications use deterministic IDs by notification key/type
- alert notification cooldown and deduplication state must survive process restart
```

### Battery-Friendly Periodic Behavior

Battery-friendly monitoring must behave as follows:

```text
- user explicitly enables it
- WorkManager schedules periodic checks only when mode is BatteryFriendlyPeriodic
- minimum repeat interval is 15 minutes
- no exact timing guarantee is presented to the user
- no persistent foreground-service notification is required
- smart notification decision engine is still used
- periodic work is cancelled when mode becomes Off
- periodic work is cancelled when mode becomes AlwaysOnForegroundService
- periodic work refuses to run without a configured device URL
```

### Smart Alert Behavior

Smart alerts are controlled by the notification decision engine, not by the service, worker, dashboard, or notification dispatcher.

The decision engine must handle:

```text
- notifications disabled
- first warning
- repeated warning suppression
- warning to critical escalation
- dominant bad metric changes
- persistent bad air quality
- recovery after confirmation window
- device unreachable after repeated failures
- stale data
- per-key cooldown
- restart-safe cooldown and recovery state
```

Alert notification types:

```kotlin
enum class NotificationType {
    AirQualityDegraded,
    AirQualityCritical,
    AirQualityPersistent,
    AirQualityRecovered,
    DeviceUnreachable,
    StaleData,
}
```

### Settings And Dashboard Behavior

Settings must expose:

```text
- Monitoring mode: Off / Always-on / Battery-friendly
- Foreground polling interval: 30 sec / 1 min / 2 min / 5 min
- Battery-friendly interval: 15 min / 30 min / 1 hour
- Smart alert notifications toggle
- Minimum alert severity: Warning / Critical
- Notify on recovery
- Notify on device unreachable
- Stop monitoring button when monitoring is active
```

Settings must show explanatory copy:

```text
Always-on mode keeps a persistent notification visible and checks your AirGradient device every 30 seconds. This may increase battery usage.

Battery-friendly mode uses Android background scheduling. It is not real-time and may run every 15 minutes or later.
```

Dashboard must show:

```text
- Monitoring off
- Always-on monitoring active
- Battery-friendly monitoring active
- Last background check time
- Current polling interval
- Start monitoring action
- Stop monitoring action
```

UI rules:

```text
- Composables do not start services directly
- ViewModels call use cases/controllers
- validation errors are visible
- notification permission denial is visible
- dashboard does not own the monitoring loop
- dashboard does not run background checks
```

### Permission And Configuration Rules

Validation rules:

```text
- foreground polling interval must be >= 30 seconds
- periodic background interval must be >= 15 minutes
- always-on monitoring requires a configured device URL
- battery-friendly monitoring requires a configured device URL
- always-on monitoring requires notification permission on Android 13+
- foreground service must never start silently without user-visible notification support
- removing the device URL stops or disables monitoring
```

Android runtime rules:

```text
- foreground service mode must declare the proper foreground-service permission and type
- foreground service must call startForeground promptly with the persistent notification
- service owns coroutine scope and cancels work on destroy
- no GlobalScope, raw Thread, or Timer is allowed
- one check must not overlap another
- fetch timeout must stay below the foreground polling interval
```
