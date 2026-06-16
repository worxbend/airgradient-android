# AirGradient Local Protocol

The Android app targets AirGradient-compatible local HTTP devices exposing:

```text
GET /measures/current
```

Reference repository analysis in `PLAN.md` documents the accepted JSON aliases, threshold logic, AQI fallback behavior, trend behavior, notification policy, and URL normalization rules.

Implemented in the current codebase:

- `DeviceUrlNormalizer` normalizes empty, bare-host, HTTP, and HTTPS inputs and rejects unsupported schemes or missing/whitespace hosts.
- `SensorThresholds` classifies AQI, CO2, PM2.5, TVOC, NOx, and overall status from the reference thresholds.
- `AqiCalculator` derives fallback US AQI from PM2.5 when explicit AQI is unavailable.
- `TrendCalculator` compares the current reading with the previous successful reading and formats stable/up/down deltas.
- `AirGradientMeasureMapper` parses the reference aliases, numeric strings, and nested payloads; prefers compensated temperature and humidity; ignores malformed optional sensor values; and infers TVOC/NOx units from `Index` aliases.
- `AirGradientRemoteDataSource` requests `/measures/current` through Retrofit/OkHttp and maps non-2xx, timeout, unreachable, invalid URL, and malformed JSON responses to typed failures.
- `AirQualityAlertPolicy` applies source-derived alert thresholds, consecutive-reading requirements, offline failure behavior, cooldown, escalation, and recovery semantics for optional Android notifications.
