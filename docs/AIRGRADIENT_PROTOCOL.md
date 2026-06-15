# AirGradient Local Protocol

The Android app targets AirGradient-compatible local HTTP devices exposing:

```text
GET /measures/current
```

Reference repository analysis in `PLAN.md` documents the accepted JSON aliases, threshold logic, AQI fallback behavior, trend behavior, notification policy, and URL normalization rules. The domain and data phases will port that behavior into tested Kotlin code.
