# Log.putData getter/setter overloads Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** One call, `Log.putData(name, getter, setter)`, that logs a value and publishes it as a dashboard-editable Tunable — the 2027 replacement for 2026 `addDoubleProperty/addBooleanProperty(name, getter, setter)`.

**Architecture:** Two overloads in `first.demacia.utils.log.Log`. Each calls `Tunables.publishDouble/publishBoolean(name, getter, setter)` and then delegates logging to the existing `putData(String, Supplier<T>)`. `LogEntry` is untouched. Tests use WPILib's `MockTunableBackend` to read the Tunable and to simulate a dashboard edit.

**Tech Stack:** Java 25, WPILib 2027.0.0-alpha-7 (GradleRIO), JUnit Jupiter 5.10.1 (already in `build.gradle`), simulated HAL via `wpi.java.configureTestTasks(test)`.

**Spec:** `docs/superpowers/specs/2026-10-17-log-putdata-tunable-design.md`

## Global Constraints

- Season 2027 APIs only (`org.wpilib.*`; never `edu.wpi.first.*`). `BooleanConsumer` is `org.wpilib.util.function.BooleanConsumer`.
- Overload signatures, exactly:
  - `public static LogEntry<Double> putData(String name, DoubleSupplier getter, DoubleConsumer setter)`
  - `public static LogEntry<Boolean> putData(String name, BooleanSupplier getter, BooleanConsumer setter)`
- Logging goes through the existing `putData(String, Supplier<T>)` (so `LogLevel.LOG_AND_NT`, separated entry). Do not change `LogEntry`.
- The same `name` is the log entry name (NT `/Log/<name>`) and the Tunable path (Tunables root).
- Only `double` and `boolean`. No generic / int / long / float overloads.
- `RobotCommon` is not changed.
- Run `frc_verify_code` (frc_season `2027`) on every touched file before claiming a task done.
- **Pre-existing uncommitted changes:** `Log.java`, `ConsoleAlert.java` and `simgui-ds.json` already had the user's uncommitted edits before this work. Task 0 decides how commits handle them; no task may silently commit them.

## Review Focus

1. **Name contains spaces** (real usage: `"motors/<name>/test Value"`) — must log and tune the same as a plain name. Pinned by using a spaced name in every test.
2. **Dashboard edit shows up in the log** — after the setter runs, the next `periodic()` logs the new value. Pinned by `doubleDashboardEditIsLogged` / `booleanDashboardEditIsLogged`.
3. **Same name registered twice** (two motors with the same name) — must not throw; Tunables only warns. Pinned by `duplicateNameDoesNotThrow`.
4. **Null setter** (2026 allowed `null` for read-only) — must fail fast at registration with `NullPointerException`, not later when someone edits on the dashboard; read-only values use `putData(name, supplier)`. Pinned by `nullSetterThrows`.
5. **Competition mode** — `Log.removeInComp()` only removes `*_NOT_IN_COMP` entries, so these `LOG_AND_NT` entries and their Tunables stay live in competition. Not unit-tested (needs comp state); called out for the final reviewer.

---

### Task 0: Reset the working tree to a true RED starting point

The overloads were written before the spec (process slip). Remove them so TDD can start from failing tests.

**Files:**
- Modify: `src/main/java/first/demacia/utils/log/Log.java` — delete the two getter/setter `putData` overloads and their imports (`BooleanSupplier`, `DoubleConsumer`, `DoubleSupplier`, `BooleanConsumer`). Touch nothing else in the file (it holds the user's other uncommitted edits).
- Modify: `src/main/java/first/demacia/utils/motors/BaseMotor.java:64` — restore
  `Tunables.getTable("motors/" + name).publishDouble("test Value", this::getTestValue, this::setTestValue);`

- [ ] **Step 1: Ask the user how to handle pre-existing uncommitted changes** in `Log.java` / `ConsoleAlert.java` / `simgui-ds.json` (commit them first as their own commit, or leave them and commit only this plan's hunks). Record the answer; Tasks 1–3 follow it.
- [ ] **Step 2: Remove the overloads and restore BaseMotor line 64** as listed above.
- [ ] **Step 3: Verify**
  Run: `./gradlew compileJava` → `BUILD SUCCESSFUL`
  Run: `git diff src/main/java/first/demacia/utils/motors/BaseMotor.java` → empty.

### Task 1: double overload (TDD)

**Files:**
- Create: `src/test/java/first/demacia/utils/log/LogPutDataTunableTest.java`
- Modify: `src/main/java/first/demacia/utils/log/Log.java` (next to `putData(String, Supplier<T>)`)

**Interfaces:**
- Consumes: existing `Log.putData(String, Supplier<T>)`; `org.wpilib.tunable.{Tunables, TunableRegistry, MockTunableBackend}`; `org.wpilib.hardware.hal.HAL#initialize()`; `org.wpilib.command2.CommandScheduler`; `org.wpilib.networktables.NetworkTableInstance`.
- Produces: `Log.putData(String, DoubleSupplier, DoubleConsumer) -> LogEntry<Double>`; test class fixture used by Task 2.

**Test fixture (decided here, reused in Task 2):**
- `@BeforeAll`: `HAL.initialize()`; create one `static MockTunableBackend backend`; `TunableRegistry.registerBackend("/", backend)`.
- Every test uses a unique name under `"test/putData/"` that **contains a space** (Review Focus 1), e.g. `"test/putData/double value"`.
- Read the log side with `NetworkTableInstance.getDefault().getTable("Log").getFloatTopic(name).subscribe(Float.NaN).get()` (double data is published as a Float topic by `LogEntry.createPublisher`).
- "Run one robot loop" = `CommandScheduler.getInstance().run()` (runs `Log.periodic()`).
- Simulate a dashboard edit = `backend.setDouble(name, v); TunableRegistry.update();`

- [ ] **Step 1: Write the failing tests**

```java
@Test void doubleValueIsLogged()          // value[0]=1.5; putData; run loop
  -> assertEquals(1.5f, logSub.get(), 1e-6)
@Test void doubleTunableIsPublished()     // value[0]=2.5; putData
  -> assertEquals(2.5, backend.getDouble(name), 1e-9)
@Test void doubleDashboardEditCallsSetter() // putData; backend.setDouble(name, 7.0); TunableRegistry.update()
  -> assertEquals(7.0, value[0], 1e-9)
@Test void doubleDashboardEditIsLogged()  // as above, then run loop
  -> assertEquals(7.0f, logSub.get(), 1e-6)
@Test void duplicateNameDoesNotThrow()    // putData twice with the same name
  -> assertDoesNotThrow(...)
@Test void nullSetterThrows()             // putData(name, () -> 0.0, (DoubleConsumer) null)
  -> assertThrows(NullPointerException.class, ...)
```
`value` is a `double[1]`; getter `() -> value[0]`, setter `v -> value[0] = v`.

- [ ] **Step 2: Run, verify RED**
  Run: `./gradlew test --tests first.demacia.utils.log.LogPutDataTunableTest`
  Expected: compilation failure — no `putData(String, DoubleSupplier, DoubleConsumer)`.
  If the failure is anything else (e.g. `Log` static init crashes under the simulated HAL), stop and report it; do not work around it silently.

- [ ] **Step 3: Implement `putData(String name, DoubleSupplier getter, DoubleConsumer setter) -> LogEntry<Double>`**
  `Objects.requireNonNull` on `getter` and `setter` first (Review Focus 4), then `Tunables.publishDouble(name, getter, setter)`, then `return putData(name, (Supplier<Double>) getter::getAsDouble);`. Javadoc says it replaces 2026 `addDoubleProperty(name, getter, setter)`.

- [ ] **Step 4: Run, verify GREEN**
  Same command → all 6 tests PASS. Then `frc_verify_code` on `Log.java` and the test file → 0 errors.

- [ ] **Step 5: Commit** (per Task 0 Step 1 decision)
  `git commit -m "Add Log.putData(name, DoubleSupplier, DoubleConsumer) with tests"` — files: the test file and `Log.java`.

### Task 2: boolean overload (TDD)

**Files:**
- Modify: `src/test/java/first/demacia/utils/log/LogPutDataTunableTest.java`
- Modify: `src/main/java/first/demacia/utils/log/Log.java`

**Interfaces:**
- Consumes: Task 1 fixture.
- Produces: `Log.putData(String, BooleanSupplier, BooleanConsumer) -> LogEntry<Boolean>`.

- [ ] **Step 1: Write the failing tests** — log side via `getBooleanTopic(name).subscribe(false)`; edit via `backend.setBoolean(name, v); TunableRegistry.update();`

```java
@Test void booleanValueIsLogged()           // flag[0]=true; putData; run loop -> assertTrue(logSub.get())
@Test void booleanTunableIsPublished()      // flag[0]=true; putData -> assertTrue(backend.getBoolean(name))
@Test void booleanDashboardEditCallsSetter() // flag[0]=false; putData; setBoolean(name,true); update -> assertTrue(flag[0])
@Test void booleanDashboardEditIsLogged()   // as above, then run loop -> assertTrue(logSub.get())
@Test void booleanNullSetterThrows()        // (BooleanConsumer) null -> assertThrows(NullPointerException.class, ...)
```

- [ ] **Step 2: Run, verify RED** — same command; expected: compilation failure (no boolean overload).
- [ ] **Step 3: Implement `putData(String name, BooleanSupplier getter, BooleanConsumer setter) -> LogEntry<Boolean>`** — mirror Task 1 with `Tunables.publishBoolean` and `getter::getAsBoolean`.
- [ ] **Step 4: Run, verify GREEN** — all 11 tests PASS; `frc_verify_code` on both files → 0 errors.
- [ ] **Step 5: Commit** — `"Add Log.putData(name, BooleanSupplier, BooleanConsumer) with tests"`.

### Task 3: First user — BaseMotor test Value

**Files:**
- Modify: `src/main/java/first/demacia/utils/motors/BaseMotor.java:64`

**Interfaces:**
- Consumes: `Log.putData(String, DoubleSupplier, DoubleConsumer)` from Task 1.

No unit test: `BaseMotor` needs real CTRE/REV motor objects. Verified by build + manual sim.

- [ ] **Step 1: Replace line 64** with
  `Log.putData("motors/" + name + "/test Value", this::getTestValue, this::setTestValue);`
  Keep the `Tunables` import (still used at line 123).
- [ ] **Step 2: Verify** — `./gradlew build` → `BUILD SUCCESSFUL` (includes all tests); `frc_verify_code` on `BaseMotor.java` → 0 errors.
- [ ] **Step 3: Manual simulation check (user)** — `./gradlew simulateJava`, open Elastic: `motors/<name>/test Value` is editable; editing it changes the motor's test value; `/Log/motors/<name>/test Value` shows the same number.
- [ ] **Step 4: Commit** — `"Use Log.putData getter/setter for BaseMotor test Value"`.
