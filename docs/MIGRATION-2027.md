# Dashboard migration to WPILib 2027 (alpha-7)

In 2027, `SmartDashboard`, `Sendable`, `SendableBuilder`, `SendableRegistry` and `SendableChooser` were removed. They are replaced by two APIs:

- **Telemetry** (`org.wpilib.telemetry`) is read-only output. It appears in NetworkTables under `/Telemetry/...`.
- **Tunables** (`org.wpilib.tunable`) are values the dashboard can change, plus commands and choosers. They appear under `/Tunables/...`.

## Conventions in this library

| 2026 | 2027 |
| --- | --- |
| `implements Sendable` + `initSendable(builder)` with read-only properties | `implements TelemetryLoggable` + `logTo(TelemetryTable table)` |
| `builder.setSmartDashboardType("X")` | override `getTelemetryType()` (or `getTunableType()` on a `ComplexTunable`) |
| `SmartDashboard.putData(name, sendable)` for read-only data | `Log.publishTelemetry(name, loggable)`, which `Log.periodic()` re-logs every loop |
| `builder.addDoubleProperty(key, getter, setter)` with a setter | `ComplexTunable.publishTunable(table)` with `table.publishDouble(key, getter, setter)` |
| `SmartDashboard.putData(name, command)` | `Tunables.publish(name, command)` (a `Command` is a `ComplexTunable` and shows a run button) |
| `SendableChooser<T>` (`setDefaultOption` / `addOption`) | `Selectable<T>` (`addDefault` / `add`), published with `Tunables.publish` |
| `SmartDashboard.putData(name, field2d)` | display only: `Log.publishTelemetry(name, field)`; editable from the dashboard: `Tunables.publish(name, field)` |
| `SmartDashboard.putNumber` / `getNumber` for tuning | `Tunables.addDouble(name, initial)`, then `.get()` |

### Why `Log.publishTelemetry`?

`SmartDashboard.putData` refreshed every `Sendable` automatically. Telemetry has no automatic refresh, so you must call `Telemetry.log(...)` every loop. `Log` keeps a map of registered `TelemetryLoggable` objects and logs each one in its `periodic()`, so motors and sensors only register once.

### `SensorInterface` / `MotorInterface`

Both interfaces now extend `TelemetryLoggable` instead of `Sendable`. Names come from each class's own config or field rather than `SendableRegistry`.

### Elastic layout

`ElasticGenerator` now points read-only widgets at `/Telemetry/...` and editable widgets, commands and choosers at `/Tunables/...`. Chassis, sysid and Elastic buttons that are now `TunableBoolean`s use the `Toggle Button` widget. Elastic's own 2027 support is still in progress upstream (Gold872/elastic_dashboard#388), so widget types may need tweaking once it ships.

## References

- [Telemetry design and migration guide](https://github.com/wpilibsuite/allwpilib/blob/v2027.0.0-alpha-7/telemetry/doc/telemetry.md)
- [Tunables design and migration guide](https://github.com/wpilibsuite/allwpilib/blob/v2027.0.0-alpha-7/tunables/doc/tunables.md)
- [New for 2027](https://docs.wpilib.org/en/latest/docs/yearly-overview/yearly-changelog.html)
