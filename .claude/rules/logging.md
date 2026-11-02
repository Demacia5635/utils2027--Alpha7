---
paths:
  - "src/main/java/first/demacia/utils/log/**"
  - "src/main/java/first/demacia/utils/Data.java"
  - "src/main/java/first/demacia/utils/elastic/**"
  - "src/main/java/first/demacia/utils/sysid/**"
  - "src/main/java/first/demacia/sysid/**"
---

# Log, Telemetry/Tunables, Elastic, SysId, replay

> This describes `main`. The `log-infrastructure` branch replaces most of it (new `Log`, `LogTable`, `LogBackend`, `Tuning`, `Alerts`, `Messages`, JUnit tests, alpha-8). If you are on that branch, read its `handOff.md` and `docs/superpowers/specs/` instead, and update this file when it merges.

## API on `main`

```java
Log.log("Arm calibrated");                                  // event → log file + dashboard alert
Log.log("Bad config", Level.kError);                        // org.wpilib.util.Alert.Level
Log.putData("Arm/current", () -> motor.getCurrentCurrent()); // file + live NT
Log.putData("Arm/angle", statusSignal, isRio);              // one Phoenix 6 signal
Log.putData("Arm/debug", new Supplier[] { () -> x },        // full control
        LogLevel.LOG_ONLY, "myMeta", /* isSeparated */ true);
Log.publishTelemetry("arm", armLoggable);                   // TelemetryLoggable, re-logged every loop
Log.putData("arm/tuning", someComplexTunable);              // = Tunables.publish
```

| `LogLevel` | File | NetworkTables |
|-----------|:---:|:---:|
| `LOG_ONLY_NOT_IN_COMP` | ✅ | ❌ |
| `LOG_ONLY` | ✅ | ❌ |
| `LOG_AND_NT_NOT_IN_COMP` | ✅ | ✅ |
| `LOG_AND_NT` (default of the short `putData`) | ✅ | ✅ |

## How it works

- `Log` is a singleton `SubsystemBase` created by a static initializer the first time any class touches it. It starts `DataLogManager`, adds the `sysID/sysidCommand` and `replay/LoadLatestLog` dashboard buttons (`TunableBoolean` + `Trigger`), and calls `RobotCommon.init()`.
- `periodic()`: `Data.refreshAll()` (bulk-refresh of all Phoenix 6 signals, split by Rio/CANivore) → expire console alerts → write every entry → `Telemetry.log` every registered `TelemetryLoggable`.
- Grouping: `isSeparated=false` puts values into shared `float[]` / `boolean[]` / `String[]` entries whose name is the member names joined with `" | "`. Groups are always file-only. Motors and sensors log this way.
- `Log.removeInComp()` exists but nothing calls it.

## Tools built on the log

- On-robot SysId: `utils/sysid/SysidCommand` reads the latest `.wpilog`, fits each motor registered with `Sysid.registerMotor` (done by `BaseMotor`), and applies the gains live. `first.demacia.sysid` is the desktop version (`SysidApp`).
- Replay: `LogReplay` republishes a log under `replay/...`. `LogFileChooser` is a Windows file dialog (desktop only).
- Elastic: `ElasticGenerator` collects motors, sensors, mechanisms, power commands, calibrations and cameras and writes `Generated_Elastic_Layout.json`. Read-only widgets point at `/Telemetry/...`, editable ones at `/Tunables/...`.

## Rules

- New telemetry → `Log.putData` with names like `"<Subsystem>/<value>"`. Choose the level on purpose.
- Never call `Log.log(...)` every loop; it creates an alert each time. For a condition checked in `periodic()`, keep a `ConsoleAlert` field and call `set(condition)`.
- Nothing here may block: no file reads in `periodic()` (replay and SysId run as commands).
