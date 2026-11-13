# utils2027--Alpha7: Demacia library on WPILib 2027 (FRC 5635)

Java 25 · **WPILib 2027.0.0-alpha-7** (GradleRIO `org.wpilib.GradleRIO`) · SystemCore · Commands v2 · CTRE Phoenix 6 `26.70.0-alpha-2` · REVLib 2027 alpha · team 5635.

This repo is the team's library (`first.demacia`) ported from the 2026 code in `Demacia5635/DemaciaCode` to the 2027 alpha. **The 2027 API is not the 2026 API.** Most of what you remember about WPILib (`edu.wpi.first.*`, `SmartDashboard`, `Sendable`, `XboxController`, `ChassisSpeeds`) is from 2026 and does not compile here. Read `.claude/rules/wpilib-2027.md` before writing or reviewing any code, and check every WPILib signature you are not sure of (see Tools).

- `src/main/java/first/demacia/`: **the library**: motors, sensors, mechanisms, swerve, kinematics, odometry, vision, log, Elastic, SysId, LEDs.
- `src/main/java/first/robot/`: a thin test robot (`Robot`, `RobotChassisConstants`). Most of `Robot()` is commented out.
- `docs/MIGRATION-2027.md`: how the dashboard code moved from `SmartDashboard`/`Sendable` to Telemetry and Tunables. Read it before touching dashboard code.
- `docs/ai/ARCHITECTURE.md`: Mermaid diagrams of the packages and the 20 ms loop. Open the section you need only.

## Module map: read the area's rule file before working there

Each rule file loads automatically when you open a file in its area. When you are only planning, read it yourself first.

| Area | Code under `src/main/java/first/` | Rule file |
|------|------|-----------|
| 2027 API traps (applies everywhere) | all | `.claude/rules/wpilib-2027.md` |
| Motors & sensors | `demacia/utils/motors`, `demacia/utils/sensors` | `.claude/rules/hardware.md` |
| Mechanisms (subsystem base classes, states) | `demacia/utils/mechanisms` | `.claude/rules/mechanisms.md` |
| Swerve, kinematics, odometry, path geometry | `demacia/utils/chassis`, `demacia/kinematics`, `demacia/odometry`, `demacia/path` | `.claude/rules/drive-and-pose.md` |
| Vision (Limelight tags, objects) | `demacia/vision` | `.claude/rules/vision.md` |
| Log, Telemetry/Tunables, Elastic, SysId, replay | `demacia/utils/log`, `demacia/utils/Data.java`, `demacia/utils/elastic`, `demacia/utils/sysid`, `demacia/sysid` | `.claude/rules/logging.md` |
| LEDs, `LookUpTable`, `RobotCommon` | `demacia/utils/leds`, `demacia/utils/*.java` | `.claude/rules/utilities.md` |
| Test robot | `robot` | `.claude/rules/robot.md` |

Skills: `/new-mechanism` (a subsystem on `StateBaseMechanism`), `/add-device` (a motor or sensor, or a new wrapper type), `/port-from-2026` (bring a file over from DemaciaCode), `/constants` (a constants template), `/teach` (a lesson page), `/safety` (runs the `safety-reviewer` agent on this branch's changes), `/teacher on|off` (teacher mode), `/cr [branch|PR|folder]` (code review in the mentors' style), `/check-releases` (is there a newer WPILib 2027 or vendordep, and should we update).

Agent: `safety-reviewer` checks changed robot code for missing limits, unsafe default commands, wrong units and blocking calls. Run `/safety` before merging a mechanism or config change.

## Commands

- Compile: `./gradlew compileJava` (Windows: `gradlew.bat compileJava`). Run it before saying a change is done. If it can't run (no internet for the first download), say so; don't claim the code compiles.
- Tests: `./gradlew test` (no tests on `main` yet; the `log-infrastructure` branch adds JUnit tests under `src/test/java`).
- Simulator: `./gradlew simulateJava`. Deploy: `./gradlew deploy` (needs a SystemCore on the network).
- Java 25 is required. Building from the WPILib VS Code commands handles this. From a terminal (including when Claude runs Gradle), point Gradle at the 2027 WPILib JDK, for example `gradlew.bat compileJava -Dorg.gradle.java.home=C:/Users/Public/wpilib/<2027 folder>/jdk` (check the folder name under `C:\Users\Public\wpilib`). The error `invalid source release: 25` means the wrong JDK, not broken code.

## Libraries

| Library | Version | Docs |
|---|---|---|
| WPILib | 2027.0.0-alpha-7 | https://docs.wpilib.org/en/latest/ (the `stable` docs are 2026) and the allwpilib source at tag `v2027.0.0-alpha-7` |
| Commands v2 | bundled with WPILib (`vendordeps/CommandsV2.json`) | table in `.claude/rules/wpilib-2027.md` |
| CTRE Phoenix 6 | 26.70.0-alpha-2 | https://v6.docs.ctr-electronics.com/ |
| REVLib | 2027.0.0-alpha-7 | https://docs.revrobotics.com/revlib |

Online docs often lag the alpha. When they disagree with the code that compiles here, trust the code and frc-mcp.

## Golden rules

1. **2027 only.** Imports are `org.wpilib.*`, never `edu.wpi.first.*`. No `SmartDashboard`, `Sendable`, `SendableChooser`, `Shuffleboard`. If a class or method is not already used in this repo, verify it exists in alpha-7 before using it.
2. **Never block the 20 ms loop.** No `Thread.sleep`, busy-waits or file/network I/O in `periodic()`, `execute()`, or anything they call.
3. **Hardware only through the wrappers.** Create devices from an `XxxConfig` and use them as `MotorInterface` / `SensorInterface`. Don't call `TalonFX`, `SparkMax` etc. directly from mechanism or robot code.
4. **Constructor argument order is not uniform.** Motor configs take the name first, most sensor configs take it last. Use the table in `hardware.md`; don't copy the 2026 order.
5. **Units.** `withMeterMotor` → meters and m/s. `withRadiansMotor` → radians and rad/s. Neither → mechanism rotations. Angles are radians unless the name says `Degrees`.
6. **Commands own subsystems.** A command that moves hardware calls `addRequirements(...)` for every subsystem it drives.
7. **Dashboard and log through `Log`** (`first.demacia.utils.log.Log`): `Log.putData` for values, `Log.publishTelemetry` for read-only objects, `Tunables` for anything the dashboard edits. No `System.out.println` for telemetry.
8. **Library stays game-free.** `first.demacia` must not import `first.robot`. Field positions, game pieces and season names belong in `first.robot`. (The 2026 hub tag offsets in `vision/VisionConstants.java` are an existing exception; don't add more.)
9. **Match the surrounding file.** Indentation is 2 spaces in some files and 4 in others; keep each file's style. Public library methods get Javadoc. Code and comments in English.

## Hooks (`.claude/settings.json`)

- **After every Java edit** `.claude/hooks/check-java.sh` checks the lines that differ from the last commit for: merge conflict markers, `edu.wpi.first` imports (wrong year), `frc.demacia` (the 2026 library package), `SmartDashboard`/`Sendable`, `Thread.sleep`/`Timer.delay`, and motor commands inside `periodic()`. When it reports a problem, fix it, or tell the user why the finding is wrong. A line that really needs one of these (for example a sleep on its own thread) ends with `// hook-ok: <reason>`.
- **Compile when Claude finishes** (`.claude/hooks/compile-on-stop.sh`): off by default. Turn it on per computer with `{ "env": { "DEMACIA_COMPILE_ON_STOP": "1" } }` in `.claude/settings.local.json`.
- On Windows the hooks run in Git Bash, which comes with Git for Windows. See `docs/ai/SETUP-WINDOWS.md`.

## Known issues: check before relying on these

- The log system is being rewritten on the `log-infrastructure` branch (alpha-8, new `Log`, `LogTable`, `Tuning`, `Alerts`, tests). `logging.md` describes `main`. When that branch merges, update `logging.md` and this section.
- `Chassis` and `vision` depend on each other: `Chassis` calls `Vision.getInstance()`, and `Vision`/`Camera` call `Chassis.getInstance()`. Don't add more `getInstance()` calls inside the library; pass `Supplier`s instead.
- `Chassis` uses WPILib's `SwerveDrivePoseEstimator`. `odometry/DemaciaPoseEstimator` is not used by anything.
- `Log.removeInComp()` exists but nothing calls it, so `*_NOT_IN_COMP` entries are never removed.
- `withDetectStallInMotor(...)` stores thresholds that nothing reads.
- Unconfirmed: `DemaciaKinematics` may report forward speed while spinning in place (it adds the module steer angle when converting module states to chassis velocities). Test on the robot: spin in place and check the reported linear speed is zero.
- Two `Sysid` classes exist: `first.demacia.sysid.Sysid` (desktop analysis, `SysidApp`) and `first.demacia.utils.sysid.Sysid` (on-robot, used by `SysidCommand`).

## Tools

- **frc-mcp** (`frc` in `.mcp.json`): WPILib/vendor docs for the season this repo targets. Use it for any WPILib, Phoenix 6 or REVLib signature you are not sure of, and its migrate tool when porting 2026 code. Prefer it over memory and over web search.
- **codebase-memory-mcp** (`codebase-memory` in `.mcp.json`): a graph of this repo. Use it for structural questions (who calls X, what breaks if Y changes, call chains) before grepping: `search_graph`, `trace_path`, `get_code_snippet`, `get_architecture`. If the project isn't indexed yet, run `index_repository` on the repo root. Call resolution is by name, so `getInstance` or `periodic` match many classes; confirm surprising edges in the source.
- **Mermaid**: diagrams in `docs/` are Mermaid (GitHub renders them, and VS Code does with the Markdown Mermaid extension). In chat replies use plain-text diagrams instead, because the Claude Code chat panel doesn't render Mermaid.
- Install steps for all of these on Windows: `docs/ai/SETUP-WINDOWS.md`.
