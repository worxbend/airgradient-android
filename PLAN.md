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
- Pull-to-refresh is not implemented yet; Phase 6 currently provides app-bar/manual refresh callbacks and visual refreshing state.
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
- Pull-to-refresh remains unimplemented; dashboard manual refresh is available through the app bar.
```

Validation passed:

```bash
./gradlew test
./gradlew ktlintCheck
./gradlew detekt
./gradlew lint
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
