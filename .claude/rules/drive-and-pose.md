---
paths:
  - "src/main/java/first/demacia/utils/chassis/**"
  - "src/main/java/first/demacia/kinematics/**"
  - "src/main/java/first/demacia/odometry/**"
  - "src/main/java/first/demacia/path/**"
---

# Swerve, kinematics, odometry, path geometry

## Chassis (`utils/chassis`)

- Singleton: `Chassis.initialize(ChassisConfig)` once, then `Chassis.getInstance()` (returns `null` before `initialize`).
- `ChassisConfig(name, SwerveModuleConfig[4], PigeonConfig)`. `SwerveModuleConfig(name, steerConfig, driveConfig, CancoderConfig)`. The test robot's values are in `first/robot/chassis/RobotChassisConstants.java`; `Mk5nConstants` has module presets.
- Pose: WPILib `SwerveDrivePoseEstimator` (`getPoseEstimate()`, `getPose()`, `resetPose`, `addVisionMeasurement`).
- Driving: `setVelocitiesFieldRel(ChassisVelocities)`, `setVelocitiesRobotRel`, `setVelocitiesRobotRelWithAccel`, `setModuleStates(SwerveModuleVelocity[])`.
- `DriveCommand(chassis, Gamepad)`: left stick = translation, right trigger minus left trigger = rotation, field-relative.
- The constructor calls `Vision.getInstance()`, so creating the chassis also creates vision.

## Kinematics (`kinematics`)

- `DemaciaKinematics` is the team's own swerve kinematics with acceleration limits (`toSwerveModuleStatesWithLimit`). `Chassis` keeps both it and WPILib's `SwerveDriveKinematics`.
- Unconfirmed bug: the module-states → chassis-velocities conversion adds the module steer angle (`currentAngle`), which may report forward speed while spinning in place. Don't "fix" it without a robot test.

## Odometry (`odometry`)

- `DemaciaOdometry` / `DemaciaPoseEstimator` are the team's own estimator. Nothing uses them now; `Chassis` uses WPILib's.

## Path geometry (`path`)

- `Leg` and `Circle`: line and arc segments for path following. Pure math, good candidates for JUnit tests.

## Rules

- Units: meters, m/s, radians, rad/s. Field-relative unless the name says `RobotRel`.
- Don't add new `Chassis.getInstance()` calls inside the library; pass `Supplier<Pose2d>` / `Supplier<Rotation2d>` instead (see `Tag`'s constructor).
- `2027` renamed `ChassisSpeeds` → `ChassisVelocities` and `SwerveModuleState` → `SwerveModuleVelocity` (field `velocity`, not `speedMetersPerSecond`).
