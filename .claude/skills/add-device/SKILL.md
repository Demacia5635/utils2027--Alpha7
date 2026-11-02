---
name: add-device
description: Add a motor or sensor using the Demacia config/wrapper classes on WPILib 2027, or write a new wrapper type (sensor or motor controller) for the library. Use when the user asks to add, configure or support a motor, encoder, gyro, limit switch, beam break or other hardware device.
---

# Add a device (2027)

Read `.claude/rules/hardware.md` and `.claude/rules/wpilib-2027.md` first if they are not loaded.

## A. An existing device type (most common)

1. Find the constructor in the table in `hardware.md`. The order is not uniform: `new TalonFXConfig("Arm Motor", 12, Canbus.Rio)`, `new SparkMaxConfig("Roller", 7, CANPort.CAN_D0)`, but `new LimitSwitchConfig(0, "Arm Limit")` and `new CancoderConfig(21, Canbus.CANIvore, "FL Cancoder")`.
2. Put the config in the subsystem's `XxxConstants` as a `public static final` field, every number named. Unknown IDs or ports → `// TODO: real value`, and tell the user.
3. Units first: `withRadiansMotor(gearRatio)` or `withMeterMotor(gearRatio, diameterMeters)`. Then `withPID(kP, kI, kD, kS, kV, kA, kG, kSin, kV2)` (9 numbers), `withMotionParam`, `withCurrent`, `withBrake`.
4. Create the wrapper inside the subsystem (`new TalonFXMotor(config)`, `new LimitSwitch(config)`) and pass it to the mechanism as `MotorInterface` / `SensorInterface`.
5. Don't add logging, Telemetry, Elastic or SysId registration: the wrappers do it.

## B. A new sensor type

Follow an existing pair exactly, for example `LimitSwitch` + `LimitSwitchConfig`:

- `XxxConfig extends BaseSensorConfig<XxxConfig>` (or `AnalogSensorConfig`) with the same constructor order as its closest sibling, setting `sensorType = Xxx.class`.
- `Xxx extends <WPILib or vendor class> implements DigitalSensorInterface | AnalogSensorInterface | SensorInterface`.
- In the constructor: store the name, `Log.publishTelemetry("sensors/" + name, this)`, `ElasticGenerator.getInstance().registerSensor(this)`, and add file logging like the sibling does.
- Implement `getName()`, `get()`, `checkElectronics()` and `logTo(TelemetryTable table)`.
- WPILib hardware classes are under `org.wpilib.hardware.*` (see `wpilib-2027.md`). Verify the class exists in alpha-7 with frc-mcp before extending it.

## C. A new motor controller

Extend `BaseMotor`, implement only the abstract hooks (`createMotor`, `configXxx`, `applyXxx`, `setMotorXxx`, `setSignals`...), add a `XxxConfig extends BaseMotorConfig<XxxConfig>` and an entry in `BaseMotorConfig.MotorControllerType`. Don't re-implement control logic. The vendor library must have a 2027 build.

## Check

`./gradlew compileJava`, then reply with what was added and any TODO values.
