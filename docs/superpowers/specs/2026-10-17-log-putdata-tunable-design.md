# Log.putData with getter/setter (Tunable) — Design

**Date:** 2026-10-17
**Branch:** testing-telemetry
**Season:** FRC 2027 (WPILib 2027.0.0-alpha-7)

## Goal

Bring back the 2026 `initSendable` pattern `builder.addDoubleProperty(name, getter, setter)` as a single
call in the 2027 utils: one line that both **logs** a value and makes it **editable from the dashboard**.

## Background

- **2026 (`DemaciaCode-3`)** — a `Sendable` did both jobs in one place. Example, `BaseMotor.initSendable`:
  `builder.addDoubleProperty("test Value", this::getTestValue, (value) -> setTestValue(value));`
  The getter fed the dashboard, the setter received edits.
- **2027 (this repo)** — WPILib split the two jobs:
  - read-only values: `Log.putData(name, Supplier)` → DataLog file + NetworkTables `/Log/<name>`
  - editable values: `Tunables.publishDouble(name, getter, setter)` → a Tunable, **not** logged to file
  So today an editable value needs two calls, and in practice only the Tunable is published
  (`BaseMotor` line 64), so the value is never in the log.

## Decisions (from brainstorming)

| Question | Decision |
|---|---|
| Behavior | Log + Tunable: getter is logged like any `putData`; getter+setter are published as a Tunable |
| Types | `double` and `boolean` only — the two types the 2026 code used with a setter |
| Generic `<T>` / `int` / `long` / `float` | Out of scope (YAGNI) |
| `RobotCommon` | Unchanged — it is a typed `ComplexTunable`, not a single value |

## Approaches considered

1. **Two overloads on `Log.putData` (chosen)** — smallest change, same name the team already uses, no new class.
2. **Tunable-only wrapper** — rejected: does not log, which is the main gap.
3. **SendableBuilder-like builder** (`Log.putData(path, b -> b.addDouble(...))`) — rejected for now: more
   API surface; can be added later if porting whole `initSendable` blocks becomes common.

## Design

### API (in `first.demacia.utils.log.Log`)

```java
public static LogEntry<Double>  putData(String name, DoubleSupplier getter,  DoubleConsumer setter)
public static LogEntry<Boolean> putData(String name, BooleanSupplier getter, BooleanConsumer setter)
```

Each overload:
1. Calls `Tunables.publishDouble(name, getter, setter)` / `Tunables.publishBoolean(name, getter, setter)`
   with the **full path** (2027 `Tunables` accepts a full path; no `getTable` split needed).
2. Delegates logging to the existing `putData(String, Supplier<T>)` by adapting the getter
   (`getter::getAsDouble` / `getter::getAsBoolean`), so it gets `LogLevel.LOG_AND_NT`, separated entry,
   and existing `removeInComp` behavior. `LogEntry` is not changed.

Imports: `java.util.function.{DoubleSupplier, DoubleConsumer, BooleanSupplier}` and
`org.wpilib.util.function.BooleanConsumer` (2027 package, verified with frc-mcp; 2026 was `edu.wpi.first.util.function`).

### Paths

The same `name` is used for both outputs. The log entry lives under the `Log` NetworkTable
(`/Log/<name>`), the Tunable under the Tunables root — they do not collide.

### Overload resolution

- `this::getTestValue` (returns `double`) only matches `DoubleSupplier`; `this::isX` only matches
  `BooleanSupplier` — no ambiguity.
- Existing 2-argument `putData(name, supplier)` calls are unaffected (different arity).

### First user

`BaseMotor` constructor, replacing
`Tunables.getTable("motors/" + name).publishDouble("test Value", this::getTestValue, this::setTestValue);`
with
`Log.putData("motors/" + name + "/test Value", this::getTestValue, this::setTestValue);`
Side effect (intended): `test Value` is now also logged.

## Error handling

No new error paths. Behavior on a duplicate name is exactly what `Tunables.publish*` and the existing
`putData` already do when called separately — this design adds no handling of its own.

## Testing

The repo has no unit tests (`src/test` is empty) and Tunables/DataLog need the HAL, so TDD is not
practical here. Verification:
1. `frc_verify_code` (season 2027) on every touched file — 0 errors.
2. `gradlew compileJava` — succeeds.
3. Simulation check (manual): `motors/<name>/test Value` appears as an editable Tunable in Elastic,
   editing it changes `getTestValue()`, and the value appears in the log (`/Log/motors/<name>/test Value`).

## Out of scope

- Generic / other primitive overloads
- A SendableBuilder-style builder
- Migrating other call sites beyond `BaseMotor` line 64
