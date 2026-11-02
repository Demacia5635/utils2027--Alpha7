---
name: new-mechanism
description: Create a new robot subsystem (arm, elevator, intake, shooter, climber...) on the Demacia StateBaseMechanism for WPILib 2027, with a state enum, constants, limits, calibration and a default command. Use when the user asks to add or scaffold a mechanism or subsystem.
---

# New mechanism (2027)

Read `.claude/rules/mechanisms.md`, `.claude/rules/hardware.md` and `.claude/rules/wpilib-2027.md` first if they are not loaded.

## 1. Ask or decide

- Name, motors (controller type, CAN ID, CAN bus or `CANPort`, gear ratio, units), sensors (type, channel).
- Control mode per motor: `ANGLE` (rotating joint), `MAGIC_MOTION` (linear position with a profile), `VELOCITY` (rollers, shooters), `VOLTAGE` / `DUTYCYCLE` (open loop).
- States and the target of each motor in each state.
- Soft limits, and how the encoder is zeroed: limit switch (`withAutoCalibration`), manual button, or none.

Never invent CAN IDs, ports, gear ratios or limits. Unknown → a named constant with `// TODO: real value`, and list them in the reply. For a constants-only file, `/constants` does that part.

## 2. Create the files

In `src/main/java/first/robot/subsystems/<name>/`. Example: an arm with one TalonFX and a limit switch.

`ArmState.java`
```java
package first.robot.subsystems.arm;

import first.demacia.utils.mechanisms.StateBaseMechanism.MechanismState;

/** One target per motor, in motor-array order. Units: radians. */
public enum ArmState implements MechanismState {
    HOME(0.0),
    INTAKE(Math.toRadians(-10)),
    SCORE(Math.toRadians(80));

    private final double[] values;

    ArmState(double... values) {
        this.values = values;
    }

    @Override
    public double[] getValues() {
        return values;
    }
}
```

`ArmConstants.java`
```java
package first.robot.subsystems.arm;

import first.demacia.utils.motors.BaseMotorConfig.Canbus;
import first.demacia.utils.motors.TalonFXConfig;
import first.demacia.utils.sensors.LimitSwitchConfig;

public final class ArmConstants {
    public static final String NAME = "Arm";

    public static final String MOTOR_NAME = "Arm Motor";
    public static final int MOTOR_ID = 0;              // TODO: real CAN ID
    public static final double GEAR_RATIO = 0;         // TODO: motor turns per arm turn
    public static final double KP = 0, KI = 0, KD = 0, KS = 0, KV = 0, KA = 0, KG = 0, KSIN = 0, KV2 = 0;
    public static final double MAX_VEL = 0, MAX_ACCEL = 0, MAX_JERK = 0; // TODO: rad/s, rad/s^2, rad/s^3

    public static final TalonFXConfig MOTOR_CONFIG = new TalonFXConfig(MOTOR_NAME, MOTOR_ID, Canbus.Rio)
            .withRadiansMotor(GEAR_RATIO)
            .withPID(KP, KI, KD, KS, KV, KA, KG, KSIN, KV2)
            .withMotionParam(MAX_VEL, MAX_ACCEL, MAX_JERK)
            .withBrake(true);

    public static final int LIMIT_CHANNEL = 0;         // TODO: real DIO channel
    // Sensor configs take the name LAST in 2027
    public static final LimitSwitchConfig LIMIT_CONFIG = new LimitSwitchConfig(LIMIT_CHANNEL, "Arm Limit");

    public static final double MIN_ANGLE = Math.toRadians(-20); // TODO
    public static final double MAX_ANGLE = Math.toRadians(100); // TODO
    public static final double RESET_ANGLE = MIN_ANGLE;          // angle when the limit switch is pressed
}
```

`Arm.java`
```java
package first.robot.subsystems.arm;

import first.demacia.utils.mechanisms.DefaultCommand;
import first.demacia.utils.mechanisms.StateBaseMechanism;
import first.demacia.utils.motors.MotorInterface;
import first.demacia.utils.motors.MotorInterface.ControlMode;
import first.demacia.utils.motors.TalonFXMotor;
import first.demacia.utils.sensors.LimitSwitch;
import first.demacia.utils.sensors.SensorInterface;

public class Arm extends StateBaseMechanism {
    public Arm() {
        this(new LimitSwitch(ArmConstants.LIMIT_CONFIG));
    }

    private Arm(LimitSwitch limit) {
        super(ArmConstants.NAME,
                new MotorInterface[] { new TalonFXMotor(ArmConstants.MOTOR_CONFIG) },
                new SensorInterface[] { limit },
                ArmState.class);
        addLimit(ArmConstants.MOTOR_NAME, ArmConstants.MIN_ANGLE, ArmConstants.MAX_ANGLE);
        withAutoCalibration(ArmConstants.MOTOR_NAME, limit::get, ArmConstants.RESET_ANGLE);
        setDefaultCommand(new DefaultCommand(this, new ControlMode[] { ControlMode.ANGLE }));
    }
}
```

## 3. Wire it up

- Create it in `Robot` (or `RobotContainer` once there is one) and bind buttons with `org.wpilib.command2.button.Trigger` on a `Gamepad`. A binding that only calls `setState` should be an `InstantCommand` without requirements.
- Don't add logging, Elastic or dashboard code: the motor, sensor and base classes do it.

## 4. Check

Run `./gradlew compileJava` and fix errors in the new files only. Reply with the file list and a table of every `TODO` (constant, what to measure, unit).
