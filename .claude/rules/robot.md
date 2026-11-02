---
paths:
  - "src/main/java/first/robot/**"
---

# Test robot (`first/robot`)

This repo is a library port, so `first.robot` is only a test bench for it.

- `Main.java`: entry point. Don't touch it.
- `Robot.java` (`TimedRobot`): `robotPeriodic()` runs `CommandScheduler.getInstance().run()`. The constructor's chassis and motor setup is commented out; uncomment only what you are testing, and don't commit a robot that needs hardware you don't have in sim.
- 2027 mode methods: `utilityInit()` / `utilityPeriodic()` replace `testInit()` / `testPeriodic()`.
- Controllers are `org.wpilib.driverstation.Gamepad`.
- `chassis/RobotChassisConstants.java`: the full `ChassisConfig` (CAN IDs, gear ratios, PID, offsets). Confirm which physical robot it matches before changing values.

## Conventions for real season code (when it starts)

- One package per subsystem: `first/robot/subsystems/<name>/` with `<Name>.java`, `<Name>Constants.java`, `<Name>State.java`.
- Build subsystems on `StateBaseMechanism` (`/new-mechanism`).
- Create objects here and pass them into constructors. Avoid new singletons.
