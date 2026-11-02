---
paths:
  - "src/main/java/first/demacia/utils/mechanisms/**"
---

# Mechanisms (`first/demacia/utils/mechanisms`)

## `BaseMechanism` (`extends SubsystemBase`)

- `new BaseMechanism(name, MotorInterface[] motors, SensorInterface[] sensors)`. Every motor is wrapped in a `MotorNode` with its own `minLimit` / `maxLimit` and calibration flag.
- Every setter has a by-name and a by-index form: `setPower`, `setVoltage`, `setVelocity`, `setPositionVoltage`, `setMotion`, `setAngle`, `stop`, `setNeutralMode`, `isReady`.
- Limits: `addLimit(motor, min, max)`, `addLimitMin`, `addLimitMax`. Position targets are clamped to them.
- Calibration: `withCalibration(motor)` marks a motor as needing calibration: `setMotion`, `setAngle` and `setPositionVoltage` are silently ignored until it is calibrated. `withAutoCalibration(motor, atLimit, resetPos)` zeroes the encoder when `atLimit` becomes true. `CalibrationCommand` drives toward the limit and resets.
- `withPowerCommand(motor, DoubleSupplier)` adds a manual `PowerCommand`, also registered with Elastic.
- Registers itself with `ElasticGenerator`.

## `StateBaseMechanism` (`extends BaseMechanism`)

- `new StateBaseMechanism(name, motors, sensors, MyState.class)` where `MyState` is an enum implementing `StateBaseMechanism.MechanismState` (`double[] getValues()`, one value per motor in motor-array order).
- Adds `IDLE` (all zeros, the default) and `TESTING` (values from the `<name> Test Values` tunable) to a `Selectable` published at `<name>/<name> State Chooser`.
- `setState(state)` changes the target. `DefaultCommand(mechanism, ControlMode[])` applies each motor's value with its control mode every loop; set it as the default command.

## Rules

- Season mechanisms live in `first.robot`, not here. This package only has the generic base classes.
- A command that only changes `state` should not require the mechanism, or it interrupts a running calibration.
- Keep `periodic()` cheap. No blocking calls.
