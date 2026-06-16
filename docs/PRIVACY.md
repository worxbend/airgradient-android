# Privacy

AirGradient Android is designed for local-network monitoring.

## Data Processed

The app fetches the configured device endpoint:

```text
<configured-device-base-url>/measures/current
```

Current readings are kept in memory for dashboard display, trend comparison, and foreground alert evaluation. The app does not persist historical sensor readings.

## Data Stored On Device

The app stores user settings in Jetpack DataStore:

```text
- normalized AirGradient device base URL
- refresh interval
- notifications enabled flag
- theme mode
```

The app also stores notification decision state in a separate DataStore file:

```text
- last alert condition status
- last dominant metric key
- alert cooldown and recovery timestamps
- consecutive fetch-failure count
```

This notification state lets cooldown and recovery behavior survive an app process restart. It does not include raw
sensor readings.

These files stay on the device. Android cloud backup and device-transfer extraction rules exclude both AirGradient
DataStore files:

```text
datastore/airgradient_settings.preferences_pb
datastore/airgradient_notification_state.preferences_pb
```

This avoids copying the local device address, notification preference, or local alert history into a backup account or to
another device.

## Network Behavior

The app contacts only the device URL configured by the user. Many AirGradient local-server deployments expose plain HTTP on a private LAN, so the Android manifest deliberately allows cleartext traffic.

No cloud account, analytics SDK, crash reporter, remote proxy, or third-party telemetry is configured.
