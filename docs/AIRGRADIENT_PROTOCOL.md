# AirGradient Local Protocol

The Android app targets AirGradient-compatible local HTTP devices exposing:

```text
GET /measures/current
```

Reference repository analysis in `PLAN.md` documents the accepted JSON aliases, threshold logic, AQI fallback behavior, trend behavior, notification policy, and URL normalization rules.

Implemented in the current domain layer:

- `DeviceUrlNormalizer` normalizes empty, bare-host, HTTP, and HTTPS inputs and rejects unsupported schemes or missing/whitespace hosts.
- `SensorThresholds` classifies AQI, CO2, PM2.5, TVOC, NOx, and overall status from the reference thresholds.
- `AqiCalculator` derives fallback US AQI from PM2.5 when explicit AQI is unavailable.
- `TrendCalculator` compares the current reading with the previous successful reading and formats stable/up/down deltas.

The next data phase will add JSON alias parsing, recursive payload lookup, unit inference for TVOC/NOx, and typed HTTP error mapping.
