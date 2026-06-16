# Privacy

AirGradient Android is designed for local-network monitoring.

## Data Processed

The app fetches the configured device endpoint:

```text
<configured-device-base-url>/measures/current
```

Current readings are kept in memory for dashboard display, trend comparison, and foreground alert evaluation. The app does not persist historical sensor readings.

## Data Stored On Device

The app stores only user settings in Jetpack DataStore:

```text
- normalized AirGradient device base URL
- refresh interval
- notifications enabled flag
- theme mode
```

These settings stay on the device. Android cloud backup and device-transfer extraction rules exclude the AirGradient DataStore file:

```text
datastore/airgradient_settings.preferences_pb
```

This avoids copying the local device address or notification preference into a backup account or to another device.

## Network Behavior

The app contacts only the device URL configured by the user. Many AirGradient local-server deployments expose plain HTTP on a private LAN, so the Android manifest deliberately allows cleartext traffic.

No cloud account, analytics SDK, crash reporter, remote proxy, or third-party telemetry is configured.
