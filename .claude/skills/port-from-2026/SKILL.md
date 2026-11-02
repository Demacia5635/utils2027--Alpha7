---
name: port-from-2026
description: Port a class or feature from the 2026 DemaciaCode repo (frc.demacia, WPILib 2026) into this 2027 alpha library (first.demacia). Use when the user asks to bring over, migrate or convert 2026 code.
argument-hint: <file or feature, e.g. utils/controller/CommandController.java>
---

# Port from 2026

Source: `https://github.com/Demacia5635/DemaciaCode` (the 2026 code, package `frc.demacia`). Target: this repo, package `first.demacia`.

## Steps

1. Get the 2026 file. If the user didn't paste it, ask for the path, or read it from a local clone of DemaciaCode if one exists next to this repo.
2. Check whether the target already exists here (codebase-memory `search_graph`, or search `src/main/java/first`). Many files were already ported and changed since; merge, don't overwrite.
3. Convert:
   - package `frc.demacia...` → `first.demacia...`; `frc.robot` → `first.robot`.
   - every `edu.wpi.first...` import → its `org.wpilib...` equivalent from `.claude/rules/wpilib-2027.md`. For anything not in that table, use frc-mcp (its migrate tool if available) and confirm the class exists in alpha-7.
   - `Sendable` / `SmartDashboard` / `SendableChooser` → Telemetry / Tunables / `Selectable` per `docs/MIGRATION-2027.md`.
   - Config constructors → the 2027 argument order in `hardware.md`.
   - `ChassisSpeeds` → `ChassisVelocities`, `SwerveModuleState` → `SwerveModuleVelocity`, `XboxController`/`CommandController` → `Gamepad`.
4. Don't carry over 2026 game code (reef, hub, speaker...) into `first.demacia`.
5. Compile with `./gradlew compileJava` and fix only what the port broke.
6. Reply with: what was ported, every API that had no direct 2027 equivalent and what you chose instead, and anything left as `// TODO: verify on alpha-7`.

File or feature: $ARGUMENTS
