
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

## Enterprise-Grade Engineering Standards

The main implementation priority is not only visual quality. The application must be implemented as a production-quality, enterprise-grade Android codebase using Kotlin and Jetpack Compose.

The codebase must be clean, modular, testable, maintainable, observable, and easy for another senior engineer to extend without reverse-engineering hidden assumptions.

## Hard Engineering Requirements

The implementation must follow these principles:

```text
- Kotlin-first idiomatic code
- Jetpack Compose-first UI
- strict separation of concerns
- clear architectural boundaries
- small focused classes
- small focused functions
- immutable state models
- explicit error modeling
- deterministic behavior
- testable business logic
- no hidden global state
- no God classes
- no direct networking from UI
- no persistence logic in ViewModels
- no business logic inside Composables
- no duplicated threshold logic
- no magic strings or magic numbers scattered across the codebase
- no unstructured exception swallowing
- no TODO-driven incomplete implementation
```

## Architecture Standard

Use a pragmatic Clean Architecture + MVVM structure.

Required dependency direction:

```text
presentation -> domain <- data
```

Layer responsibilities:

```text
presentation:
  - Compose screens
  - Compose components
  - ViewModels
  - immutable UI state
  - user event handling
  - display formatting only

domain:
  - domain models
  - repository interfaces
  - use cases
  - sensor classification logic
  - AQI calculation
  - trend calculation
  - URL normalization
  - business rules
  - no Android framework dependency

data:
  - Retrofit API
  - DTOs
  - DTO mappers
  - repository implementations
  - DataStore implementation
  - network error mapping
```

The domain layer must be pure Kotlin where possible. It must be unit-testable without Android runtime, Compose, Retrofit, DataStore, or Hilt.

## Package Discipline

Use package names that communicate responsibility, not technical randomness.

Required structure:

```text
core/
  dispatchers/
  network/
  time/
  validation/
  result/

data/
  airgradient/
    dto/
    mapper/
  settings/

domain/
  model/
  repository/
  usecase/
  sensors/
  error/

presentation/
  navigation/
  dashboard/
    components/
  settings/
    components/
  theme/
```

Forbidden package names:

```text
utils
helpers
common
misc
manager
stuff
base
```

If a utility-like function is needed, create a named package that expresses its domain, for example:

```text
domain/sensors/
core/time/
core/validation/
data/airgradient/mapper/
```

## Naming Standards

Names must be explicit, boring, and domain-driven.

Good examples:

```kotlin
AirGradientRepository
AirGradientRemoteDataSource
AirGradientMeasureDto
AirGradientMeasureMapper
AirMeasureSnapshot
SensorThresholds
AqiCalculator
TrendCalculator
DeviceUrlNormalizer
DashboardUiState
RefreshDashboardUseCase
ObserveSettingsUseCase
```

Bad examples:

```kotlin
DataManager
ApiHelper
CommonUtils
MainModel
SensorData2
StuffRepository
BaseViewModel
AppHelper
```

Rules:

```text
- DTO classes must end with Dto
- UI state classes must end with UiState
- ViewModels must end with ViewModel
- repository interfaces must be in domain
- repository implementations must end with Impl
- mappers must end with Mapper
- use cases must describe action in verb form
- avoid abbreviations unless they are domain-standard: AQI, CO2, PM25
```

## Kotlin Code Style

Use idiomatic Kotlin.

Required:

```text
- val by default
- var only when mutation is necessary and localized
- data class for immutable state
- sealed interface for finite state and errors
- explicit visibility for non-public APIs
- expression bodies where they improve clarity
- constructor injection
- suspend functions for one-shot async operations
- Flow / StateFlow for observable state
- Result-like typed domain results
```

Avoid:

```text
- nullable Boolean flags for state machines
- primitive obsession for domain values
- unchecked casts
- broad catch Exception without mapping
- lateinit except Android/Hilt-required cases
- companion object dumping ground
- excessive inheritance
- abstract base classes without strong reason
```

## Compose Code Style

Compose code must be declarative, stateless where possible, and previewable.

Rules:

```text
- Route Composables may know about ViewModels
- Screen Composables must receive state and callbacks only
- Reusable components must be stateless
- Composables must not call repositories
- Composables must not perform HTTP requests
- Composables must not write DataStore
- Composables must not contain business rules
- Composables must not calculate sensor thresholds
- use collectAsStateWithLifecycle
- provide previews for important states
```

Required split:

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
    )
}

@Composable
fun DashboardScreen(
    state: DashboardUiState,
    onRefresh: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    // Pure UI rendering only.
}
```

## UI State Modeling

Do not model screen state as loosely coupled nullable fields.

Bad:

```kotlin
data class DashboardUiState(
    val isLoading: Boolean,
    val error: String?,
    val data: AirMeasureSnapshot?,
)
```

Better:

```kotlin
sealed interface DashboardUiState {
    data object Unconfigured : DashboardUiState
    data object Loading : DashboardUiState

    data class Content(
        val snapshot: AirMeasureSnapshot,
        val metrics: List<SensorMetric>,
        val lastUpdatedLabel: String,
        val isRefreshing: Boolean,
    ) : DashboardUiState

    data class ContentWithWarning(
        val snapshot: AirMeasureSnapshot,
        val metrics: List<SensorMetric>,
        val warning: DashboardWarning,
        val lastUpdatedLabel: String,
        val isRefreshing: Boolean,
    ) : DashboardUiState

    data class Error(
        val reason: DashboardError,
        val lastKnownSnapshot: AirMeasureSnapshot?,
    ) : DashboardUiState
}
```

State must be explicit, finite, and impossible to misinterpret.

## Error Handling Standard

All meaningful failures must be modeled explicitly.

Required domain errors:

```kotlin
sealed interface AirGradientError {
    data object MissingDeviceUrl : AirGradientError
    data object InvalidDeviceUrl : AirGradientError
    data object DeviceUnreachable : AirGradientError
    data object Timeout : AirGradientError
    data class HttpFailure(val statusCode: Int) : AirGradientError
    data object MalformedPayload : AirGradientError
    data object Unknown : AirGradientError
}
```

Rules:

```text
- do not expose raw exceptions to UI
- do not render Throwable.message directly
- map network errors in data layer
- map domain errors to user-facing messages in presentation layer
- preserve technical details in logs where appropriate
```

## Data Mapping Standard

DTOs must not leak into domain or presentation layers.

Required flow:

```text
Retrofit DTO -> Mapper -> Domain Model -> UI Model
```

Forbidden:

```text
- using DTO directly in ViewModel
- using DTO directly in Composable
- putting display labels into DTOs
- putting Retrofit annotations into domain models
```

Mapper behavior must be tested.

Required tests:

```text
- full valid payload
- missing optional sensor field
- null sensor field
- malformed numeric field if possible
- AQI present
- AQI absent with fallback calculation
```

## Sensor Logic Standard

Sensor interpretation must be centralized.

Required files:

```text
domain/sensors/SensorThresholds.kt
domain/sensors/AqiCalculator.kt
domain/sensors/TrendCalculator.kt
domain/sensors/SensorMetricFactory.kt
```

Rules:

```text
- threshold values must be named constants
- each threshold must include source comment or reference finding
- classification must be deterministic
- UI must never duplicate threshold logic
- tests must cover boundary values
```

Example:

```kotlin
object SensorThresholds {
    fun classifyCo2(ppm: Double?): SensorStatus =
        when {
            ppm == null -> SensorStatus.Unknown
            ppm < 800.0 -> SensorStatus.Good
            ppm < 1_200.0 -> SensorStatus.Moderate
            ppm < 2_000.0 -> SensorStatus.Warning
            else -> SensorStatus.Critical
        }
}
```

## Dependency Injection Standard

Use constructor injection everywhere possible.

Rules:

```text
- no service locators
- no static mutable dependencies
- no manually constructed repositories inside ViewModels
- no direct Retrofit construction outside DI module
- no direct DataStore construction outside DI module
```

Required modules:

```text
NetworkModule
RepositoryModule
DataStoreModule
DispatcherModule
ClockModule
```

ViewModel example:

```kotlin
@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val observeSettings: ObserveSettingsUseCase,
    private val refreshDashboard: RefreshDashboardUseCase,
    private val dispatchers: AppDispatchers,
) : ViewModel()
```

## Concurrency Standard

Use structured concurrency.

Rules:

```text
- all ViewModel work must run in viewModelScope
- repository suspend functions must be cancellable
- no GlobalScope
- no raw Thread
- no Timer
- no blocking I/O on Main dispatcher
- no overlapping refresh requests
```

Use a mutex or explicit refresh guard if needed.

```kotlin
private val refreshMutex = Mutex()

fun refresh() {
    viewModelScope.launch(dispatchers.io) {
        refreshMutex.withLock {
            // fetch safely
        }
    }
}
```

## Testing Standard

The implementation must be test-first for business rules and regression-heavy areas.

Minimum required tests:

```text
domain:
  - DeviceUrlNormalizerTest
  - SensorThresholdsTest
  - AqiCalculatorTest
  - TrendCalculatorTest
  - SensorMetricFactoryTest

data:
  - AirGradientMeasureMapperTest
  - AirGradientRemoteDataSourceTest
  - AirGradientRepositoryImplTest
  - SettingsRepositoryImplTest

presentation:
  - DashboardViewModelTest
  - SettingsViewModelTest

ui:
  - DashboardScreenContentTest
  - DashboardScreenErrorTest
  - SettingsScreenValidationTest
```

MockWebServer must be used for HTTP behavior.

Turbine must be used for Flow assertions.

Compose UI tests must validate user-visible behavior, not implementation details.

## Static Analysis and Formatting

Add and enforce:

```text
- ktlint
- detekt
- Android Lint
- Kotlin compiler warnings
```

Required commands:

```bash
./gradlew clean build
./gradlew test
./gradlew lint
./gradlew ktlintCheck
./gradlew detekt
```

The agent must not commit code if any of these fail.

## Documentation Standard

Every major architectural decision must be documented.

Required docs:

```text
PLAN.md
README.md
docs/ARCHITECTURE.md
docs/DEVELOPMENT.md
docs/AIRGRADIENT_PROTOCOL.md
```

`docs/ARCHITECTURE.md` must explain:

```text
- package structure
- dependency direction
- UI state strategy
- error model
- networking flow
- settings persistence
- sensor classification design
```

`docs/AIRGRADIENT_PROTOCOL.md` must document:

```text
- /measures/current endpoint
- observed JSON fields
- optional fields
- mapping rules
- assumptions copied from reference repo analysis
```

## Review Checklist Per Phase

Before each commit, the agent must verify:

```text
- Does this change preserve architecture boundaries?
- Is business logic outside UI?
- Are DTOs isolated to data layer?
- Are errors typed?
- Is the code readable without hidden context?
- Are names precise?
- Are tests included for new logic?
- Are there duplicated rules?
- Did static analysis pass?
- Did PLAN.md need an update?
```

## Commit Standard

Each phase must produce a focused commit.

Commit examples:

```bash
git commit -m "docs: analyze AirGradient reference implementations"
git commit -m "chore: prepare Kotlin Compose project baseline"
git commit -m "feat: add sensor domain model"
git commit -m "feat: implement AirGradient network repository"
git commit -m "feat: persist application settings"
git commit -m "feat: add dashboard state management"
git commit -m "feat: build Compose dashboard"
git commit -m "test: cover sensor threshold boundaries"
git commit -m "docs: document AirGradient protocol mapping"
```

Do not mix unrelated work in one commit.

## Definition of Done

The application is not done until:

```text
- written in Kotlin
- UI written in Jetpack Compose
- Material 3 design implemented
- architecture boundaries are clean
- reference repositories were scanned in /tmp
- PLAN.md contains reference findings
- endpoint contract is documented
- sensor logic is centralized
- errors are typed
- settings are persisted
- dashboard is previewable
- ViewModels are tested
- mappers are tested
- sensor thresholds are tested
- MockWebServer tests pass
- static analysis passes
- release build succeeds
```

Required final verification:

```bash
./gradlew clean build
./gradlew test
./gradlew lint
./gradlew ktlintCheck
./gradlew detekt
./gradlew assembleRelease
```
