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
