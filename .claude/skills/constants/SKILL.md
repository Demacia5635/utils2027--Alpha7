---
name: constants
description: Create a <Mechanism>Constants.java template in the team's style, with every motor, sensor and tuning value as a named constant and TODO placeholders to fill in. Runs only when the user types /constants.
argument-hint: <mechanism and its hardware, e.g. elevator, 2 TalonFX, limit switch at the bottom>
disable-model-invocation: true
---

# New mechanism constants

Build a constants file for one mechanism that the user only has to fill in. Never invent a hardware value.

## No mechanism given

If the request at the bottom of this file is empty, do not create anything. Ask in one short message, in the user's language: the mechanism name, its motors (controller type, and whether each one moves in a line, rotates, or just spins), its sensors (limit switch, CANcoder, etc.), and whether it has states (intake, eject, ...). Say a short answer is fine and missing details will become TODOs. Build from the reply.

## Learn this repo first

1. Read the motor config base class (`BaseMotorConfig.java`) and the config class of each controller you will use (`TalonFXConfig`, `SparkMaxConfig`, `SparkFlexConfig`, `TalonSRXConfig`), plus each sensor config (`LimitSwitchConfig`, `CancoderConfig`, ...). Use only constructors and `with...` methods that exist here, with their real parameter order. The package may be `frc.demacia.utils` (2026) or `first.demacia` / `org.wpilib` (2027): copy the imports from this repo, never from memory.
2. Read two or three existing `*Constants.java` files under the robot code (for example `intake/IntakeConstants.java`) and follow their layout and naming.
3. Collect every CAN ID and DIO channel already used in the repo's constants, so the new file doesn't collide.

## File layout

Write `src/main/java/<robot package>/<mechanism>/<Mechanism>Constants.java` (all lowercase folder, PascalCase class). If it exists, stop and ask before touching it.

Top of the class:

```java
public class ElevatorConstants {
    public static final String NAME = "Elevator";
    public static final Canbus CANBUS = Canbus.Rio; // TODO: Rio or CANIvore

    // CAN IDs already used in this repo: 20, 30, 33, 34, 40, 41, 50, 51
    // DIO channels already used: 5, 8, 9
```

One block per motor, prefixed with the motor's name, every value its own constant with its unit in a comment:

```java
    // ===== Lift motor =====
    public static final int LIFT_ID = 0;                          // TODO: CAN ID
    public static final String LIFT_NAME = "Elevator Lift Motor";
    public static final boolean LIFT_INVERTED = false;            // TODO: check direction on the robot
    public static final boolean LIFT_BRAKE = true;                // TODO
    public static final double LIFT_CURRENT_LIMIT = 0;            // TODO: amps
    public static final double LIFT_GEAR_RATIO = 0;               // TODO: motor turns per output turn
    public static final double LIFT_PULLEY_DIAMETER = 0;          // TODO: meters (linear mechanisms only)

    // Closed loop (0 until tuned)
    public static final double LIFT_KP = 0;
    public static final double LIFT_KI = 0;
    public static final double LIFT_KD = 0;
    public static final double LIFT_KS = 0;
    public static final double LIFT_KV = 0;
    public static final double LIFT_KA = 0;
    public static final double LIFT_KG = 0;

    // Motion profile (position control only)
    public static final double LIFT_MAX_VELOCITY = 0;             // TODO: m/s or rad/s
    public static final double LIFT_MAX_ACCELERATION = 0;         // TODO
    public static final double LIFT_MAX_JERK = 0;                 // TODO

    public static final TalonFXConfig LIFT_CONFIG = new TalonFXConfig(LIFT_ID, CANBUS, LIFT_NAME)
            .withMeterMotor(LIFT_GEAR_RATIO, LIFT_PULLEY_DIAMETER)
            .withPID(LIFT_KP, LIFT_KI, LIFT_KD, LIFT_KS, LIFT_KV, LIFT_KA, LIFT_KG)
            .withMotionParam(LIFT_MAX_VELOCITY, LIFT_MAX_ACCELERATION, LIFT_MAX_JERK)
            .withBrake(LIFT_BRAKE)
            .withInvert(LIFT_INVERTED)
            .withCurrent(LIFT_CURRENT_LIMIT);
```

Pick the unit method from the motion: moves in a line → `withMeterMotor(gearRatio, diameter)`; rotates to an angle → `withRadiansMotor(gearRatio)`; only spins (roller, feeder) with no position or velocity control → neither, and leave out PID and motion profile. A spinning roller with velocity control gets `withMeterMotor` and PID but no motion profile.

The config line always uses the named constants above it, never literal numbers inside `with...(...)`.

Then, as needed:

- **Sensors:** a block per sensor (`<NAME>_CHANNEL` or `<NAME>_ID`, `<NAME>_NAME`, then its config object). CANcoder offset as its own constant in radians.
- **Positions and limits:** `MIN_...`, `MAX_...`, setpoints and `ALLOWED_ERROR`, in radians or meters. Write angles as `Math.toRadians(0) // TODO: degrees`.
- **States:** an enum `<Mechanism>State` whose fields are what each state commands (duty, velocity or position), following `IntakeConstants.IntakeState`. Always include `IDLE` and `TESTING`.

## Placeholder rules

- Unknown value → `0` (or `false`) with `// TODO: <what to measure and its unit>`. Never guess a CAN ID, gear ratio, diameter, current limit or gain, even a "typical" one.
- A value the user gave goes in as given, no TODO.
- `withMeterMotor` divides by the diameter, so add `// must not stay 0` next to any diameter TODO.
- Spell names correctly (`GEAR_RATIO`, `DIAMETER`, `LIMIT_SWITCH`), even if older files don't.
- Comments in English.

## After writing

Run `./gradlew compileJava` if it's available and fix any compile error in the new file only. Then reply with:

1. The file path.
2. A short table of the TODOs: constant, what to measure, unit.
3. Any CAN ID or channel the user gave that is already taken.

Do not create the subsystem or commands unless the user asked.

Mechanism: $ARGUMENTS
