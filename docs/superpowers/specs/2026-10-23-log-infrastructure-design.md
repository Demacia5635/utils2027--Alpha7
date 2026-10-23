# Log infrastructure for 2027 — Design

**Date:** 2026-10-23
**Branch:** log-infrastructure
**Season:** FRC 2027 (WPILib 2027.0.0-alpha-7, Phoenix 6 26.70.0-alpha-2)

## Goal

Make `Log` the single entry point for everything the robot shows, records and lets you tune:
dashboard values, file logging, tunables, dashboard buttons, choosers and alerts. Code outside the
`log` package no longer imports `org.wpilib.telemetry` or `org.wpilib.tunable`, so the next WPILib
alpha only changes the `log` package.

This is the first step of a wider goal: an infrastructure layer that wraps WPILib and the vendor
libraries under our own names. Only the log layer is in scope here.

## What the logs are used for

1. **Live Elastic** during practice and tuning — NT paths must stay stable, and `ElasticGenerator`
   builds layouts from them.
2. **Our own tools reading the file** — SysId (`SysidCommand` on the robot, `SysidApp` on the
   laptop) and `LogReplay`. AdvantageScope compatibility is a side benefit, not a driver.

## Current state (2027 port)

| Channel | API | NT path | In the file? |
|---|---|---|---|
| Polled values | `Log.putData(name, supplier)` → our `LogEntry` | `/Log/...` | yes |
| Grouped signals | `Log.putData(name, Data[]/Supplier[], LogLevel.LOG_ONLY, meta, false)` | — | yes, as `"Float/a: x, y \| b: ..."` arrays |
| Object telemetry | `Log.publishTelemetry(path, TelemetryLoggable)`, `Telemetry.log(...)` | `/Telemetry/...` | yes, since commit `ca11b52` |
| Tunables | `Tunables.*` directly in ~15 files | `/Tunables/...` | **no** |
| Messages | `Log.log(message)` → `ConsoleAlert` + `DataLogManager.log` | Alerts `console` group | yes (`messages`) |

Problems this design removes:

- Three NT roots and three APIs for one job; students must know `Log`, `Telemetry` and `Tunables`.
- Motor and sensor values are written twice (grouped arrays and `logTo`).
- Our own change detection (`Data`, `LogEntry`) duplicates what WPILib 2027 already does: the
  DataLog backend uses `update()` and NT publishers drop duplicate values.
- The grouped format needs a custom parser and rebuilds its name on every registration.
- Tunable changes from the dashboard are not recorded anywhere.
- `Log` is a `SubsystemBase` whose static initializer starts `DataLogManager`, registers the SysId
  and replay buttons and initializes `RobotCommon`. Nothing in it can be unit tested.
- 2026 features lost in the port: `Log.alert(message, condition)` with edge detection and Elastic
  pop-ups held until Elastic connects.

## Decisions

| # | Question | Decision |
|---|---|---|
| 1 | Main uses of logs | Live Elastic + our own tools (SysId, LogReplay) |
| 2 | Migration | Rewrite inside the existing `Log` class; keep the class name and most call sites |
| 3 | Wrapping depth | Full: no `org.wpilib.telemetry` / `org.wpilib.tunable` imports outside `log` |
| 4 | Dashboard vs file | Everything always goes to the file. Each value chooses `ALWAYS` or `DEBUG` for the dashboard; `DEBUG` values stop updating NT while `RobotCommon.getIsComp()` is true |
| 5 | File format | One entry per value (`Telemetry/motors/X/Position`); drop the grouped arrays; `LogReader`, SysId and `LogReplay` read the new format |
| 6 | Tunable changes | Recorded to the file under `Tunables/<path>` whenever the value changes from the dashboard |
| 7 | Alerts | Port the 2026 `Log.alert(message, active)` / `Log.alert(message)` with edge detection and Elastic pop-ups |
| 8 | Signal timestamps | Values are stamped with the current time. Signals are refreshed in the same loop right before they are logged, so the error is a few milliseconds. `Data.toLogTimestamp` is removed with the rest of `Data`'s logging code |
| 9 | `ElasticGenerator` | Keeps working: `/Telemetry` and `/Tunables` paths are unchanged; the `/Log/...` paths it references move to `/Telemetry/...` |

Decisions 6–9 were taken on the recommended option, as agreed.

## Public API

All of it lives in `first.demacia.utils.log`. Names are ours; WPILib types do not appear in any
signature except `Command` and `Field2d`, which are values, not logging API.

### Recording values

```java
// Polled every loop, shown on the dashboard
Log.putData("chassis/gyro angle", () -> getGyroAngle().getDegrees());
// Polled every loop, file only during competition
Log.putData("motors/" + name + "/wanted value", this::getWantedValue, Log.Dashboard.DEBUG);

// An object that writes several values every loop (replaces publishTelemetry + TelemetryLoggable)
Log.putData("motors/" + name, this);            // this implements Loggable
Log.putData("motors/" + name, this, Log.Dashboard.DEBUG);
Log.remove("motors/" + name);

// A one-time value, written now (replaces Telemetry.log)
Log.put("elastic/Status", "Saved at: " + path);
```

```java
public interface Loggable {
  /** Writes this object's current values. Called every loop. */
  void log(LogTable table);

  /** Optional type shown by the dashboard, for example "Motor". */
  default String logType() { return null; }
}

public final class LogTable {
  public LogTable table(String name);
  public void put(String name, double value);   // also boolean, long, String,
  public void put(String name, boolean value);  // double[], boolean[], String[],
  public void put(String name, String value);   // and struct types such as Pose2d
  public <T> void put(String name, T value);
  /** Writes this value on every loop even when unchanged (for values SysId pairs by index). */
  public void recordEveryLoop(String name);
}
```

### Tuning from the dashboard

```java
// An editable number or flag; every dashboard change is also written to the file
Log.tune("motors/" + name + "/test Value", this::getTestValue, this::setTestValue);
Log.tune("RC/is comp", RobotCommon::getIsComp, RobotCommon::setIsComp);

// A group of editable values under one path (replaces ComplexTunable)
Log.tune("motors/" + name + "/Motion Magic Config", "Motion Magic Config", t -> {
  t.number("Vel", () -> config.maxVelocity, v -> config.maxVelocity = v);
  t.flag("USE_KS", () -> flags[0], v -> flags[0] = v);
  t.button("Update", applyCommand);
});

// An editable array
Log.tuneArray(getName() + "/" + getName() + " Test Values", this::getTestValues, this::setTestValues);

// A chooser (replaces Selectable)
Chooser<ControlMode> chooser = Log.chooser("motors/" + name + "/Value Control Mode Chooser");
chooser.addDefault("DUTYCYCLE", ControlMode.DUTYCYCLE);
chooser.add("VOLTAGE", ControlMode.VOLTAGE);
chooser.onChange(mode -> valueControlMode = mode);

// A field the dashboard can show and edit
Log.tune("chassis/field", field);
```

```java
@FunctionalInterface
public interface TunableGroup {
  void tune(TuneTable table);
}

public final class TuneTable {
  public void number(String name, DoubleSupplier getter, DoubleConsumer setter);
  public void flag(String name, BooleanSupplier getter, BooleanConsumer setter);
  public void button(String name, Command command);
}
```

`Chooser<T>` exposes `add`, `addDefault`, `onChange` and `getSelected` and wraps `Selectable`.

### Dashboard buttons

```java
// Runs the command while the button is on (replaces Tunables.publish(name, command)
// and the addBoolean + Trigger + set(false) pattern repeated 11 times)
Log.button("chassis/reset gyro", new InstantCommand(() -> setYaw(Rotation2d.ZERO)).ignoringDisable(true));
```

### Messages and alerts

```java
Log.log("arm motor initialized");                       // file + console + Alerts "console" group
Log.log("CAN ID out of range", Log.Severity.ERROR);

Log.alert("Arm motor is hot", motor.getTemperature() > 80);  // safe every loop, acts on the rising edge
Log.alert(name + " has an invalid CAN ID");                    // one-time alert
```

`Log.Severity` is `INFO`, `WARNING`, `ERROR` and maps to WPILib `Alert.Level` `LOW`, `MEDIUM`,
`HIGH` inside the `log` package. `Log.alert` behaves as in 2026: the message is the alert's identity,
the log line and Elastic pop-up happen only when the condition becomes true, pop-ups raised before
Elastic connects are held until it has been connected for 2 seconds, and more than 50 distinct
messages raise a single error alert instead of leaking one alert per loop.

## Architecture

```
          robot code / utils
                 │  Log.putData / put / tune / button / chooser / log / alert
                 ▼
 ┌──────────────── Log (static facade) ─────────────────┐
 │  LoggedValues     Tuning          Alerts   Messages   │
 │  (suppliers,      (tunables,      (edge,   (file,     │
 │   Loggables)       buttons,        pop-ups) console)  │
 │       │            choosers)          │               │
 │       ▼                │              ▼               │
 │   WPILib Telemetry     ▼          WPILib Alert,       │
 │       │           WPILib Tunables  Elastic NT topic   │
 │       ▼                │                              │
 │   LogBackend ──────────┼── file-only recorder         │
 │   ├─ NT  /Telemetry    │   (Tunables/<path>)          │
 │   └─ DataLog Telemetry │                              │
 └───────────────────────────────────────────────────────┘
```

### Units

| Unit | Responsibility | Depends on | Tested with |
|---|---|---|---|
| `Log` | Public static API; creates the units below once, with the real WPILib backends; registers itself with the `CommandScheduler` to run `periodic()` | everything below | simulation run |
| `LogBackend` | A `TelemetryBackend` that forwards every entry to a NT backend and a file backend; skips NT for `DEBUG` paths while in competition | two child `TelemetryBackend`s, a `Dashboard` lookup, a competition supplier | `MockTelemetryBackend` children |
| `LoggedValues` | Holds registered suppliers and `Loggable`s by path and writes them all once per loop | a root `LogTable` | `MockTelemetryBackend` |
| `LogTable` | Our wrapper of `TelemetryTable` | `TelemetryTable` | through `LoggedValues` |
| `Tuning` | Publishes tunables, groups, arrays, choosers, fields and buttons; wraps every setter so the new value is recorded to the file | `TunableTable`, a file recorder | `MockTunableBackend` + in-memory `DataLogWriter` |
| `Alerts` | Edge detection, held pop-ups, distinct-message limit | WPILib `Alert`, an Elastic notifier, a clock | fake notifier and clock |
| `Messages` | `Log.log`: file, console, the `console` Alerts group with its limit of 10 | `DataLog`, WPILib `Alert` | in-memory `DataLogWriter` |
| `ElasticNotifier` | Publishes pop-ups to `/Elastic/RobotNotifications` as JSON | NT instance | NT test instance |

Every unit except `Log` takes its dependencies through its constructor, so it can be tested
without HAL, without `DataLogManager` and without the scheduler. `Log` only wires them; its
behavior is checked by the simulation run in the verification section.

### Startup

`Log` is created on first use, as today, but the work moves out of the static initializer into a
private `start()` that runs once:

1. `DataLogManager.start()`, `logNetworkTables(false)`, `DriverStation.startDataLog(log)`.
2. Register `LogBackend` (NT `/Telemetry` + DataLog `Telemetry`) as the Telemetry backend for `""`.
   This replaces `TelemetryFileLogging`, which is deleted.
3. Route `TelemetryRegistry` and `TunableRegistry` warnings to `Log.log(..., WARNING)`, once per path.
4. Publish the SysId and LoadLatestLog buttons with `Log.button`.
5. `RobotCommon.init()`.

### Every loop (`Log.periodic`)

1. `Data.refreshAll()` — refreshes the Phoenix signals so the values below are from this loop.
2. `LoggedValues.update()` — every supplier and `Loggable` writes through Telemetry.
3. `Alerts.update()` — sends held pop-ups once Elastic is ready.
4. `Messages.update()` — expires console alerts.

`TunableRegistry.update()` is already called by `TimedRobot` every loop; dashboard edits reach the
setters there, and `Tuning`'s wrapped setters record them to the file.

### Competition mode

`LogBackend` asks, for each entry, whether its path is under a path registered as `DEBUG`. The
answer is fixed when the entry is created. `RobotCommon.getIsComp()` is read on every write, so
flipping "is comp" on the dashboard takes effect immediately: `DEBUG` entries keep going to the file
and stop updating NT. Tunables are always published; they are not affected by competition mode.

### File format

| Source | File entry |
|---|---|
| `Log.putData` / `Log.put` / `Loggable` | `Telemetry/<path>` |
| `Loggable.logType()` | `Telemetry/<path>/.type` (written by WPILib) |
| Tunable changed from the dashboard | `Tunables/<path>` |
| `Log.log` | `messages` (written by `DataLogManager`) |

### Consumers of the file format

- `LogReader` — reads per-entry data. The grouped `"Float/..."` parsing is removed.
- `SysidCommand` / `SysidApp` — select entries under `Telemetry/motors/` and read each motor's
  `Position`, `Velocity`, `Acceleration` and `Voltage`. Both `Sysid` classes pair these four
  **by sample index**, not by time, which only works if all four have one sample per loop. WPILib
  skips unchanged values, so a constant voltage during a dynamic test would drop samples and shift
  the pairing. Therefore a motor writes these four values **every loop** (`LogTable.recordEveryLoop`,
  which calls WPILib `keepDuplicates`) — the same amount of data as today's grouped array, which is
  also appended every loop.
- `LogReplay` — replays per-entry data through `Log.putData` under `replay/<original path>`;
  `LogReader` skips entries under `Telemetry/replay/`.

## Migration

| Today | After |
|---|---|
| `Log.publishTelemetry(path, this)` + `logTo(TelemetryTable)` | `Log.putData(path, this)` + `log(LogTable)` |
| `MotorInterface` / `SensorInterface extends TelemetryLoggable` | `extends Loggable` |
| Grouped `Log.putData(name, Data[] / Supplier[], LogLevel.LOG_ONLY, meta, false)` | removed — the same values are already written by `log(LogTable)`; any value missing there is added to it |
| `LogLevel.LOG_ONLY` on a supplier | `Log.Dashboard.DEBUG` |
| `Telemetry.log(path, value)` | `Log.put(path, value)` |
| `Tunables.publish(path, command)`, `addBoolean` + `Trigger` | `Log.button(path, command)` |
| `Tunables.publish(path, new ComplexTunable() {...})`, `RobotCommon implements ComplexTunable` | `Log.tune(path, type, t -> {...})` |
| `Selectable<T>` + `Tunables.publish` | `Log.chooser(path)` |
| `Tunables.getTable(..).publishValue(.., double[].class)` | `Log.tuneArray(path, getter, setter)` |
| `Tunables.publish(path, field)` | `Log.tune(path, field)` |
| `Log.putData(name, getter, setter)` (uncommitted overloads on `testing-telemetry`) | `Log.tune(name, getter, setter)` |
| `ElasticGenerator` `/Log/...` paths | `/Telemetry/...` |

Removed: `LogEntry`, `LogLevel`, `Data`'s group arrays and change detection (`Data` keeps refreshing
Phoenix signals for the motors), `Data.toLogTimestamp`, `TelemetryFileLogging`, `Log.publishTelemetry`,
`Log.removeTelemetry`, `Log.removeInComp`, `Log.clearEntries`, and the public static fields
`Log.log` (the `DataLog`) and `Log.table` (the `/Log` NT table). The `Log.log(message)` methods stay.

## Error handling

- Registering a path that is already registered replaces the old supplier or `Loggable` and logs a
  warning with the path.
- A supplier or `Loggable` that throws is caught in `LoggedValues.update()`; the exception is logged
  once per path and that path is skipped for the rest of the loop. One broken value never stops the
  others or the robot loop.
- A tunable path that is already published is not published again (WPILib returns `false`); `Tuning`
  logs a warning.
- WPILib Telemetry and Tunables warnings (type mismatch, missing backend) go to `Log.log` as
  warnings, deduplicated by path.

## Testing

TDD, JUnit 5, `./gradlew test`. WPILib's `MockTelemetryBackend` and `MockTunableBackend` are pure
Java; the DataLog and NT tests use an in-memory `DataLogWriter` and `NetworkTableInstance.create()`,
whose natives are already available to tests (proven by the tests added in `ca11b52`).

Each unit's behaviors to cover:

- `LogBackend`: value reaches both children; `DEBUG` path skips NT only while in competition; switching
  competition at runtime takes effect on the next write.
- `LoggedValues`: a supplier is written every update; a `Loggable` writes its sub-values under its
  path; re-registering replaces; `remove` stops writing; a throwing supplier does not stop the others.
- `Tuning`: a dashboard edit calls the setter and records the value under `Tunables/<path>`; a group's
  children are published under the group path; a chooser's `onChange` fires on a dashboard selection;
  a button runs its command.
- `Alerts`: logs and pops up only on the rising edge; a pop-up raised before Elastic is ready is sent
  once it is ready, and dropped if the condition cleared meanwhile; the 51st distinct message raises
  the overflow alert.
- `Messages`: a message reaches the file; the 11th message closes the oldest console alert.
- `LogReader` / SysId: a log written through `Log` for a motor is read back as that motor's
  position, velocity, acceleration and voltage.

Verification beyond unit tests: a headless simulation run, then reading its `.wpilog`, must show
`Telemetry/motors/...`, `Telemetry/sensors/...`, no `Float/` or `Boolean/` group entries, and a
`Tunables/...` entry after a tunable is changed.

## Out of scope

- Wrapping anything other than logging (motors, commands, hardware).
- Struct logging for our own types and AdvantageScope layouts.
- `DriverStationDisplay` output for messages.
- Keeping Phoenix signal timestamps (decision 8).
- Persisting tunable values between restarts.
