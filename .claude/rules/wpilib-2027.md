# WPILib 2027 (alpha-7): what changed from 2026

This file has no `paths:` filter on purpose: it applies to all code in the repo. Every row below is taken from imports that compile in this repo today. When something isn't listed, look it up with frc-mcp; don't guess from 2026 memory.

## Packages

| 2026 (`edu.wpi.first...`) | 2027 (used in this repo) |
|---|---|
| `wpilibj2.command.*` (`Command`, `SubsystemBase`, `CommandScheduler`, `InstantCommand`, `RunCommand`, `Commands`) | `org.wpilib.command2.*` |
| `wpilibj2.command.button.Trigger` | `org.wpilib.command2.button.Trigger` |
| `math.geometry.*` (`Pose2d`, `Rotation2d`, `Translation2d`, `Transform3d`...) | `org.wpilib.math.geometry.*` |
| `math.kinematics.ChassisSpeeds` | `org.wpilib.math.kinematics.ChassisVelocities` |
| `math.kinematics.SwerveModuleState` | `org.wpilib.math.kinematics.SwerveModuleVelocity` |
| `math.kinematics.SwerveModulePosition`, `SwerveDriveKinematics` | `org.wpilib.math.kinematics.*` (same names) |
| `math.estimator.SwerveDrivePoseEstimator` | `org.wpilib.math.estimator.SwerveDrivePoseEstimator` |
| `math.MathUtil` | `org.wpilib.math.util.MathUtil` |
| `math.Matrix`, `VecBuilder`, `Nat`, `numbers.N3` | `org.wpilib.math.linalg.Matrix`, `org.wpilib.math.linalg.VecBuilder`, `org.wpilib.math.util.Nat`, `org.wpilib.math.numbers.N3` |
| `wpilibj.Timer` (`getFPGATimestamp()`) | `org.wpilib.system.Timer` (`Timer.getTimestamp()`) |
| `wpilibj.DataLogManager`, `Filesystem` | `org.wpilib.system.DataLogManager`, `org.wpilib.system.Filesystem` |
| `util.datalog.*` | `org.wpilib.datalog.*` |
| `networktables.*` | `org.wpilib.networktables.*` |
| `wpilibj.DriverStation`, `RobotState` | `org.wpilib.driverstation.DriverStation`, `org.wpilib.driverstation.RobotState` |
| `wpilibj.XboxController` / `PS5Controller` / `CommandXboxController` | `org.wpilib.driverstation.Gamepad` (`getLeftX()`, `getRightTrigger()`...) |
| `wpilibj.TimedRobot`, `RobotBase` | `org.wpilib.framework.TimedRobot`, `org.wpilib.framework.RobotBase` |
| `testInit()` / `testPeriodic()` | `utilityInit()` / `utilityPeriodic()` |
| `wpilibj.DigitalInput`, `AnalogInput` | `org.wpilib.hardware.discrete.*` |
| `wpilibj.DutyCycleEncoder`, `AnalogEncoder` | `org.wpilib.hardware.rotation.*` |
| `wpilibj.Compressor`, `PneumaticsModuleType` | `org.wpilib.hardware.pneumatic.*` |
| `wpilibj.AddressableLED`, `AddressableLEDBuffer` | `org.wpilib.hardware.led.*` |
| `wpilibj.I2C` | `org.wpilib.hardware.bus.I2C` |
| CAN bus id `int` for REV / pneumatics | `org.wpilib.hardware.bus.CANPort` (for example `CANPort.CAN_D0`) |
| `wpilibj.util.Color` | `org.wpilib.util.Color` |
| `wpilibj.Alert` | `org.wpilib.util.Alert` (`Alert.Level`) |
| `units.measure.*` | `org.wpilib.units.measure.*` |
| `smartdashboard.Field2d` | `org.wpilib.smartdashboard.Field2d` (the class kept its old package) |

## Dashboard: no more `SmartDashboard` / `Sendable`

Full guide in `docs/MIGRATION-2027.md`. Short version:

- Read-only output → `TelemetryLoggable` + `logTo(TelemetryTable)`, registered once with `Log.publishTelemetry(path, obj)`.
- Values the dashboard edits → `org.wpilib.tunable.Tunables` (`addDouble`, `addBoolean`, `publish`) or a `ComplexTunable` with `publishTunable(TunableTable)`.
- Choosers → `Selectable<T>` (`addDefault` / `add` / `onChange`), published with `Tunables.publish(name, selectable)`.
- A dashboard button → a `TunableBoolean` plus `new Trigger(bool).onTrue(...)` (see `Log`'s constructor), or `Tunables.publish(name, command)`.

## Vendors

- CTRE Phoenix 6 is `26.70.0-alpha-2` (the 2027 alpha track despite the 26 number). Same `com.ctre.phoenix6` packages. `CANBus` objects come from `BaseMotorConfig.Canbus`.
- REVLib is the 2027 alpha (`com.revrobotics`). REV devices take a `CANPort`, not a CAN bus name.
- No PathPlanner, Choreo, AdvantageKit or PhotonVision here. Don't add a vendordep without asking: it must have a 2027 alpha build.

## Before you write a WPILib call

1. Is it already used in this repo? Copy that usage.
2. If not, ask frc-mcp (it reads `build.gradle` and knows the repo is 2027).
3. Still unsure → say so in the reply and leave a `// TODO: verify on alpha-7` comment. Never present an unverified 2027 signature as fact.

## Commands v2 in alpha-7

This repo uses **Commands v2** (`vendordeps/CommandsV2.json`, package `org.wpilib.command2`). 2027 also ships Commands v3 with a different API (coroutines, OpModes); the two can't be used together, so don't copy v3 examples. Everything below was checked against the alpha-7 source of `Command`, `Commands`, `Subsystem`, `Trigger` and `CommandScheduler`.

**`Command` has no `schedule()` method in 2027.** Use `CommandScheduler.getInstance().schedule(cmd)`.

### Composition: when does it end?

| Method | Group type | Ends when |
|---|---|---|
| `a.andThen(b)` / `Commands.sequence(a, b)` | `SequentialCommandGroup` | the **last** one ends, one after another |
| `a.alongWith(b)` / `Commands.parallel(a, b)` | `ParallelCommandGroup` | **all** of them end |
| `a.raceWith(b)` / `Commands.race(a, b)` | `ParallelRaceGroup` | **any one** ends; the rest are interrupted |
| `a.deadlineFor(b)` / `Commands.deadline(a, b)` | `ParallelDeadlineGroup` | **`a`** (the deadline) ends; the rest are interrupted |
| `a.withDeadline(d)` | `ParallelDeadlineGroup` | **`d`** ends (the argument is the deadline here, the opposite of `deadlineFor`) |
| `a.withTimeout(s)` | `ParallelRaceGroup` | `a` ends or `s` seconds pass |
| `a.until(cond)` | `ParallelRaceGroup` | `a` ends or `cond` becomes true |
| `a.onlyWhile(cond)` | `ParallelRaceGroup` | `a` ends or `cond` becomes false |
| `a.repeatedly()` | `RepeatCommand` | never (restarts `a` each time it ends) |

Other decorators: `beforeStarting(...)`, `unless(cond)` / `onlyIf(cond)` (checked once at start), `ignoringDisable(true)`, `finallyDo(...)`, `handleInterrupt(...)`, `withName(...)`, `asProxy()`.

Once a command is put in a group it can't be scheduled on its own or put in another group. Create a new instance each time.

### Factories: which ones never end on their own

Available as `Commands.xxx(..., requirements)` and as `subsystem.xxx(...)` (which adds the subsystem as a requirement).

| Factory | Behavior |
|---|---|
| `runOnce(action)` | runs once, ends right away |
| `run(action)` | every loop, **never ends** |
| `runEnd(run, end)` | every loop, `end` when interrupted, **never ends** |
| `startEnd(start, end)` | `start` once, `end` when interrupted, **never ends** |
| `startRun(start, run)` | `start` once, then `run` every loop, **never ends** |
| `idle()` | does nothing, **never ends** (holds the requirement) |
| `Commands.waitSeconds(s)`, `waitUntil(cond)` | ends after `s` seconds / when `cond` is true |
| `Commands.either(a, b, cond)`, `select(map, key)`, `defer(supplier, reqs)` | picks a command when scheduled |

A command that never ends must get an end condition (`until`, `withTimeout`, a deadline) before it goes inside a sequence, or the sequence stalls there.

### Triggers (`org.wpilib.command2.button.Trigger`)

`onTrue`, `onFalse`, `onChange`, `whileTrue` (cancels on release), `whileFalse`, `toggleOnTrue`, `toggleOnFalse`; combine with `and`, `or`, `negate`, `debounce(seconds)`, `multiPress(count, windowSeconds)`.
