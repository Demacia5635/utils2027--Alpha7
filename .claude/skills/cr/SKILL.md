---
name: cr
description: Code review of a student's robot code (a mechanism branch, PR, folder or the current changes) against Demacia's review checklist, written like a team mentor's CR. Use when the user types /cr or asks for a code review, CR, or feedback on their code.
argument-hint: [branch, PR number, folder or file; empty = this branch's changes vs main]
---

# Code review (CR) in Demacia's style

You review code the way the team's mentors do: a list of concrete comments the student can fix one by one. You don't edit any file. Works on 2026 repos (`edu.wpi.first`, `frc.demacia`) and 2027 repos (`org.wpilib`, `first.demacia`): check `build.gradle` for the GradleRIO version first and judge APIs by that year. In a 2027 repo also read `.claude/rules/wpilib-2027.md`.

## 1. What to review

From the request at the bottom of this file:

- **Empty:** everything changed on this branch: `git diff --name-only $(git merge-base HEAD main)...HEAD` plus `git status --short`. If the base branch isn't `main`, use the repo's default branch.
- **A branch name:** `git diff --name-only $(git merge-base origin/<branch> main) origin/<branch>` (fetch it first).
- **A PR number:** `gh pr diff <n>` / `gh pr view <n>` if `gh` works; otherwise ask for the branch name.
- **A folder or file:** those files.

A new mechanism is usually a whole package (`<mechanism>/subsystems`, `commands`, constants). Read **every file of that package in full**, plus the places that create and bind it (`RobotContainer`, `Robot`). Don't review only the diff lines: most comments below need the whole class.

## 2. Requirements first

Look for the mechanism's software requirements: a requirements doc or README in the package, the PR description, or ask the user in one line ("what should this mechanism do?") if none exist. If there are none, that is the first comment: **write the requirements before programming**. Then check that the commands actually do what the requirements say (for example: "the command only closes the gripper and has no states" is a requirements failure, not a style nit).

## 3. Checklist

Go through every item. Each comment must point to a real line; skip an item when it doesn't apply.

### Requirements and behavior
- The commands meet the requirements. A mechanism with several behaviors uses states (a `State` enum with the target of each motor per state, plus `IDLE` and `TESTING`, like `IntakeConstants.IntakeState`) and one main command that switches on the state. In 2027, `StateBaseMechanism` gives `IDLE` and `TESTING` for free.
- `isFinished()` and `end(interrupted)` make sense: the mechanism is left stopped or holding, and a command that should end actually can.

### Safety
- `setPower` / `setDuty` / voltage methods have some limit that stops before the mechanism reaches its physical end (limit switch, angle limit, current).
- Every `move` / position method checks that the target was reached or the end was hit, instead of running forever.
- **The mechanism doesn't move before it is calibrated.** Calibration (or a reset) happens at a known point, and the encoder is set to the real angle at that point, **not automatically 0**. Ask: when exactly is the reset done, and to which angle? Where 0 is must be a deliberate, written choice (the Gripper CR asked for 0 in the middle of the motion).
- **Motors are set only from commands, never from a subsystem's `periodic()`.** `periodic()` may read sensors, update state and telemetry, nothing that moves hardware. Any `set...`/`setVoltage`/`setDuty`/`setPosition`/`stop` call reachable from `periodic()` is a must-fix (move it into the command, or into the `TESTING` state). Also, one motor is driven by one command at a time (`addRequirements`).
- A command that moves a subsystem calls `addRequirements` for it (including the chassis).
- Doubles are compared with a tolerance (`Math.abs(a - b) < ALLOWED_ERROR`), never `==` / `!=`.
- Limits are the mechanism's real range, not a guess like ±90°.

### Units and math
- Radians everywhere in the code. Degrees only at the dashboard boundary (sending to or reading from Elastic). `getXxxDeg()` style methods that the code itself uses are a comment.
- Check every formula by hand with one example value (kinematics, differential pitch/roll, gear ratios, offsets). Check that each motor is the one the formula needs (motor 1 vs motor 2, signs). Say "check the calculation" with the exact case that looks wrong.

### Constants
- Every number is a named constant in the constants class, in radians/meters/seconds. No magic numbers in subsystems or commands (`3.5`, `-0.6`, channel `9`).
- No unused constants, and no constants that make no sense for the device (`IS_BRAKE` for a limit switch, an optical sensor that isn't on the mechanism).
- PID/FF constants say which motor they belong to (`PITCH_KP`, not `KP`).
- Constants are `UPPER_SNAKE_CASE` **without a `k` prefix** (`ROLLER_ID`, not `kRollerID` or `kp`).
- Configs use the library's builders with the right argument order for that year.

### Naming
- Names say what the thing does on the robot, not its hardware type: `pitchMotor`, `rollerMotor`, not `kraken1Pitch`, `m_SRXSensorMotor`. Names that differ only by `1` / `2` are a comment.
- No `m_` prefix, no `_` in field names (`gripper`, not `gripper_sub`).
- A variable holding a subsystem has the subsystem's name, never a motor's name.
- Method names are short and clear: `isPitchAtLimit()`, not `isPitchLimitSwitchTriggered()`.
- Class and package names follow the repo (`<mechanism>/subsystems/<Mechanism>.java`, `<mechanism>/commands/...`, `<Mechanism>Constants`).

### Structure
- One project, one repository (or one branch per mechanism in the team repo). A mechanism isn't developed inside an unrelated repo.
- The subsystem is created **once** (in `RobotContainer`/`Robot`) and passed to commands; never `new` it twice.
- No sensor or motor objects that are created but not used, or used but never created (null checks hiding a missing sensor).
- Hardware goes through the team's wrappers (`TalonFXMotor`, `LimitSwitch`), not raw `DigitalInput`/`TalonFX`.

### Dashboard and logging
- 2026: values go in `initSendable` (`builder.addDoubleProperty`), not `SmartDashboard.putNumber` / `getNumber` in `periodic()`. Use `LogManager`/`Log` for logging.
- 2027: `Log.publishTelemetry` / `logTo`, `Tunables` for editable values, `Selectable` for choosers (see `docs/MIGRATION-2027.md`).
- Dashboard test inputs drive the mechanism through the `TESTING` state, not by writing motors from `periodic()`.

### Cleanliness
- Delete comments that add nothing: template comments (`/* You should consider using the more terse Command factories API... */`, `// Use addRequirements() here...`, `// Called every time the scheduler runs...`), commented-out code, and comments that repeat the code.
- No leftover merge-conflict markers (`<<<<<<<`, `=======`, `>>>>>>>`). Search for them; they are a must-fix.
- Unused imports and fields removed. Typos in names fixed.
- Compiles: run `./gradlew compileJava` (Windows `gradlew.bat`; for 2027 see the JDK note in `CLAUDE.md`). If it can't run, say so.

In a repo that has `.claude/agents/safety-reviewer.md`, also run that agent on the same files and merge its findings into the Safety section without duplicates.

## 4. Write the review

Reply in the user's language (usually Hebrew). Code, names and paths stay in English. Shape:

```
CR <Mechanism> (<branch or files>)

Must fix
1. <file>:<line>: <what is wrong>. <why it matters on the robot, one short clause>. <what to do>.

Should fix
...

Small things
...

Good
- one or two things done well (only real ones)
```

- One comment per problem, one or two lines each, in the order a student would fix them. Point to `path/File.java:line`.
- Say what to do, not only what is wrong. When a rename is needed, give the new name.
- Group repeats: "all `k`-prefixed constants in `GripperConstants.java` (lines 12–30)" instead of ten comments.
- Must fix = breaks the requirements, can damage the robot, or won't compile. Should fix = team rules (units, constants, naming, structure). Small things = cleanliness.
- Don't invent problems to fill a section, and don't comment on what the checklist doesn't cover unless it is a real bug.
- If the user asks for a file, write the same review to `CR-<Mechanism>.md` in the repo root.

## Example comments (from a real mentor CR, Gripper)

- "Rename `kraken1Pitch` / `kraken2Roll`: the motors don't move those axes one each (it's a differential), and the name shouldn't be the motor type. Use names by role."
- "`IS_BRAKE_LIMIT_SWITCH`: a limit switch has no brake mode. Delete it."
- "`setPower` has no limit, so the gripper can drive into its end. Stop at the limit switch or the angle limit."
- "`periodic()` sets the motors from the dashboard and the command also sets them. Motors are set only from commands, never from `periodic()`."
- "Nothing stops the mechanism from moving before calibration."
- "Angles aren't necessarily ±90°. Use the measured limits from the constants."
- "`getActualRollDeg`: check the calculation; maybe `getMotor1Position` and `getMotor2Position` are swapped."
- "The command only closes the gripper and has no states, so it doesn't meet the requirements."

Scope: $ARGUMENTS
