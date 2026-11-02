---
paths:
  - "src/main/java/first/demacia/utils/motors/**"
  - "src/main/java/first/demacia/utils/sensors/**"
---

# Motors & sensors (`first/demacia/utils/motors`, `first/demacia/utils/sensors`)

This layer makes everything above it brand-independent. Mechanisms and robot code use `MotorInterface` / `SensorInterface`, never vendor classes. Both interfaces extend `TelemetryLoggable` (not `Sendable`).

## Motors: Config → `BaseMotor` → vendor subclass

- `BaseMotorConfig<T extends BaseMotorConfig<T>>` is a self-typed builder: every `withXxx()` returns `T`.
- `BaseMotor` (abstract, `implements MotorInterface`) holds all shared logic. Its constructor is a template method: `createMotor()` → `configMotor()` (the `configXxx` hooks, then `applyConfigs()`) → `setSignals()` → `addLog()` → `Log.publishTelemetry("motors/<name>")` → `ElasticGenerator.registerMotor` → `Sysid.registerMotor`.
- `TalonFXMotor`, `SparkMaxMotor`, `SparkFlexMotor` **hold** the vendor object (a `motor` field) and implement only the abstract hooks. No TalonSRX in 2027.
- Factory: `config.motorClass.create(config)` (`BaseMotorConfig.MotorControllerType`).

## Constructor order (differs between classes, and from 2026)

| Config | Constructor |
|--------|-------------|
| `TalonFXConfig` | `(String name, int id, Canbus canbus)` |
| `SparkMaxConfig`, `SparkFlexConfig` | `(String name, int id, CANPort canPort)` |
| All motor configs, copy | same as above plus `BaseMotorConfig<?> other` at the end |
| `CancoderConfig`, `PigeonConfig` | `(int id, Canbus canbus, String name)` ← name **last** |
| `LimitSwitchConfig`, `AnalogEncoderConfig`, `DigitalEncoderConfig` | `(int channel, String name)` ← name **last** |
| `OpticalSensorConfig`, `LidarSensorConfig` | `(String name, int port)` |
| `ColorSensorConfig` | `(String name)` |
| `PneumaticsConfig` | `(CANPort busId, PneumaticsModuleType type, String name)` |

`Canbus` is `BaseMotorConfig.Canbus` (`Rio`, `CANIvore`). `CANPort` is `org.wpilib.hardware.bus.CANPort`.

## Motor config cheat-sheet

- `withRadiansMotor(gearRatio)` → radians. `withMeterMotor(gearRatio, diameterMeters)` → meters (divides by the diameter, so it must not be 0). Neither → mechanism rotations. `gearRatio` = motor turns per mechanism turn.
- `withPID(kP, kI, kD, kS, kV, kA, kG, kSin, kV2)`: 9 numbers, slot 0. `withPID(slot, ...)` for slots 1–2. `kSin` is the arm gravity term, `kV2` the velocity² term. There is no shorter overload.
- `withMotionParam(maxVel, maxAccel, maxJerk)` is needed for `setMotion` / `setAngle` (Motion Magic, or MAXMotion on REV).
- Also: `withCurrent(amps)`, `withRampTime(s)`, `withBrake(bool)`, `withInvert(bool)`, `withVolts(max)`, `withCanbus(bus)`, `withCanPort(port)`.

## What every motor already does (don't duplicate)

- Logs position, velocity, acceleration, voltage, closed-loop error and set-point into the grouped file-only log (metadata `"motors"`, read by SysId), plus `motors/<name>/wanted value` and `current value` live.
- Publishes itself to Telemetry at `motors/<name>` and a `test Value` tunable, PID and Motion Magic tuning, and a control-mode `Selectable`.
- Registers with `ElasticGenerator` and `Sysid`.

## Gotchas

- Getters return values cached in `Data`, refreshed once per loop by `Log.periodic()` → `Data.refreshAll()`. Don't `refresh()` signals yourself.
- `ControlMode`: `DISABLE, DUTYCYCLE, VOLTAGE, VELOCITY, POSITION_VOLTAGE, MAGIC_MOTION, ANGLE`.
- `setAngle` on a motor without `withRadiansMotor` falls back to `setMotion` and logs a warning.
- `isReady(allowedError)` checks closed-loop error; use it to end position commands.
- `withDetectStallInMotor(...)` is dead config: nothing reads it.
- `Pneumatics` does not implement `SensorInterface`, so it can't go into a `BaseMechanism`.
- `AnalogSensorInterface.get()` returns `double`, `DigitalSensorInterface.get()` returns `boolean`. All sensors have `checkElectronics()` and `logTo(TelemetryTable)`.

## Adding a device type

Use `/add-device`. For a new motor controller, extend `BaseMotor` and implement its hooks only.
