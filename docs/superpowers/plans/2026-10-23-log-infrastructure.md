# Log Infrastructure 2027 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make `Log` the single entry point for dashboard values, file logging, tunables, buttons,
choosers and alerts, with no `org.wpilib.telemetry` / `org.wpilib.tunable` imports outside the
`log` package.

**Architecture:** Small units with injected dependencies (`LogBackend`, `LoggedValues`, `Tuning`,
`Alerts`, `Messages`, `ElasticNotifier`) are built and unit-tested first; then `Log` is rewritten as
a static facade that wires them; then every call site is migrated; then the old API is deleted.
The robot code compiles and runs after every task.

**Tech Stack:** Java 25, WPILib 2027.0.0-alpha-7 (Telemetry, Tunables, DataLog, NT, Commands v2),
Phoenix 6 26.70.0-alpha-2, JUnit 5, GradleRIO.

**Spec:** `docs/superpowers/specs/2026-10-23-log-infrastructure-design.md`

## Global Constraints

- Package for all new log types: `first.demacia.utils.log`.
- After Task 11, no file outside `src/main/java/first/demacia/utils/log/` imports `org.wpilib.telemetry.*` or `org.wpilib.tunable.*`.
- NT roots stay `/Telemetry` (values) and `/Tunables` (editable); `ElasticGenerator` layouts depend on them.
- File entries: values at `Telemetry/<path>`, dashboard edits at `Tunables/<path>`, messages at `messages`.
- `Log.Dashboard` is `ALWAYS` or `DEBUG`; `DEBUG` paths stop updating NT while `RobotCommon.getIsComp()` is true; the file always gets everything.
- `Log.Severity` is `INFO`, `WARNING`, `ERROR` → WPILib `Alert.Level` `LOW`, `MEDIUM`, `HIGH`.
- Alerts: pop-ups held until Elastic (NT client id starting with `"Elastic"`) has been connected 2 s; at most 50 distinct `Log.alert` messages, then one error alert `"Too many Log.alert messages"`.
- Console messages: Alerts group `"console"`, at most 10 active (`ConsoleConstants.CONSOLE_LIMIT`).
- Motors write `Position`, `Velocity`, `Acceleration`, `Voltage` every loop (`LogTable.recordEveryLoop`).
- Run tests with `./gradlew test --offline`; one class with `--tests <fqcn>`.
- Every test that registers a WPILib backend calls `TelemetryRegistry.reset()` / `TunableRegistry.reset()` in `@AfterEach`, **before** closing any `DataLog`.
- An in-memory `DataLogWriter` writes to its stream only on `flush()`; call it before reading.
- The working tree on this branch may carry uncommitted `Log.putData(name, getter, setter)` overloads in `Log.java` and one `BaseMotor.java` line using them. They are superseded by `Log.tune` (Task 6/7). Never commit them as they are; never commit files a task does not list.

## Review Focus

1. Alert text containing `"` or `\` must still produce valid Elastic JSON — Task 4, `notificationJsonEscapesQuotesAndBackslashes`.
2. `DEBUG` prefix `motors/arm` must not hide `motors/armExtension` — Task 1, `debugPrefixDoesNotMatchSiblingWithSamePrefix`.
3. Paths registered with or without leading/trailing `/` must match the same entries — Task 1, `debugPathsNormalizeSlashes`.
4. A supplier returning `null` must not throw or stop other values — Task 2, `nullValueIsSkippedWithoutStoppingOthers`.
5. A tunable setter that throws must not record the rejected value or break the update loop — Task 3, `throwingSetterDoesNotRecordAndWarns`.

---

## File Structure

| File | Responsibility |
|---|---|
| `log/DebugPaths.java` (new) | Set of path prefixes registered as `DEBUG`; normalized matching |
| `log/LogBackend.java` (new) | `TelemetryBackend` forwarding to NT + file; skips NT for debug paths in competition |
| `log/Loggable.java` (new) | Our replacement for `TelemetryLoggable` |
| `log/LogTable.java` (new) | Our wrapper of `TelemetryTable` |
| `log/LoggedValues.java` (new) | Registered suppliers / `Loggable`s, written once per loop |
| `log/TunableGroup.java`, `log/TuneTable.java`, `log/Chooser.java` (new) | Our tunable types |
| `log/Tuning.java` (new) | Publishes tunables, records dashboard edits to the file |
| `log/ElasticNotifier.java` (new) | Interface + NT JSON implementation of Elastic pop-ups |
| `log/Alerts.java` (new) | `Log.alert` behavior |
| `log/Messages.java` (new) | `Log.log` behavior (file + console alerts) |
| `log/Log.java` (rewrite) | Static facade and wiring |
| `log/LogReader.java`, `log/LogReplay.java`, `sysid/*`, `utils/sysid/*` (modify) | Per-entry file format |
| `log/LogEntry.java`, `log/TelemetryFileLogging.java` (delete) | Replaced |
| `src/test/java/first/demacia/utils/log/LogFiles.java` (new, test utility) | Read an in-memory DataLog back |

All paths below are under `src/main/java/first/demacia/utils/` or `src/test/java/first/demacia/utils/` unless written in full.

---

### Task 1: DebugPaths and LogBackend

**Files:**
- Create: `log/DebugPaths.java`, `log/LogBackend.java`, test `log/LogBackendTest.java`, test utility `log/LogFiles.java`
- Modify: `log/Log.java` (constructor: replace the `TelemetryFileLogging.install` line)
- Delete: `log/TelemetryFileLogging.java`, test `log/TelemetryFileLoggingTest.java` (their two behaviors move into `LogBackendTest`)

**Interfaces:**
- Produces:
  - `final class DebugPaths { void add(String path); boolean contains(String telemetryPath); }` — `telemetryPath` is the normalized form WPILib passes to backends (`"/motors/arm/Voltage"`).
  - `final class LogBackend implements TelemetryBackend { LogBackend(TelemetryBackend dashboard, TelemetryBackend file, DebugPaths debugPaths, BooleanSupplier isComp); static LogBackend create(NetworkTableInstance inst, DataLog log, DebugPaths debugPaths, BooleanSupplier isComp); }` — `create` uses `new NetworkTablesTelemetryBackend(inst, "/Telemetry")` and `new DataLogTelemetryBackend(log, "Telemetry")`.
  - Test utility `final class LogFiles { static Map<String, Object> lastValues(byte[] wpilog); }` — last value of every entry by name (double, boolean, long, String, double[]).

- [ ] **Step 1: Write the failing tests** in `LogBackendTest` using two `MockTelemetryBackend`s registered via `TelemetryRegistry.registerBackend("", new LogBackend(dashboard, file, debugPaths, () -> isComp))`:
  - `valueReachesDashboardAndFile` — `Telemetry.log("motors/arm/Voltage", 3.5)`; both mocks' `getLastValue("/motors/arm/Voltage", Double.class)` equal `3.5`.
  - `debugValueSkipsDashboardInCompetition` — `debugPaths.add("motors/arm")`, `isComp = true`; log `3.5`; `dashboard.getLastAction("/motors/arm/Voltage")` is `null`, file has `3.5`.
  - `debugValueReachesDashboardOutsideCompetition` — same with `isComp = false`; dashboard has `3.5`.
  - `switchingCompetitionAppliesOnNextWrite` — `isComp=false`, log `1.0`; `isComp=true`, log `2.0`; dashboard last value `1.0`, file last value `2.0`.
  - `debugPrefixDoesNotMatchSiblingWithSamePrefix` — `add("motors/arm")`, comp on; `motors/armExtension/Voltage` reaches the dashboard.
  - `debugPathsNormalizeSlashes` — `add("/motors/arm/")`; `contains("/motors/arm/Voltage")` and `contains("/motors/arm")` are true.
  - `createWritesTelemetryToTheLogFile` — real backends via `LogBackend.create(NetworkTableInstance.create(), new DataLogWriter(stream), ...)`; after `flush()`, `LogFiles.lastValues(bytes).get("Telemetry/motors/arm/Voltage")` is `3.5`.
  - `createKeepsDashboardPath` — subscriber on `/Telemetry/motors/arm/Voltage` reads `3.5`.

- [ ] **Step 2: Run** `./gradlew test --offline --tests first.demacia.utils.log.LogBackendTest` — Expected: compile FAIL (`LogBackend` / `DebugPaths` missing).

- [ ] **Step 3: Implement `DebugPaths` and `LogBackend`.** `LogBackend.getEntry(path)` decides once per entry whether the path is debug and returns an entry that forwards every `TelemetryEntry` method to the file entry, and to the dashboard entry unless `debug && isComp.getAsBoolean()` at call time. Override `ownsBackend` / `close` / `removeEntry` to cover both children (mirror `MultiTelemetryBackend`). Normalize in `DebugPaths` with a leading `/` and no trailing `/`; match exact path or `prefix + "/"`.

- [ ] **Step 4: Run** the test class — Expected: PASS (8 tests).

- [ ] **Step 5: Wire it:** in the `Log` constructor replace `TelemetryFileLogging.install(...)` with `TelemetryRegistry.registerBackend("", LogBackend.create(NetworkTableInstance.getDefault(), log, debugPaths, RobotCommon::getIsComp))`, holding `debugPaths` in a private static field. Delete `TelemetryFileLogging` and its test. Run `./gradlew build --offline` — Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit** the listed files: `git commit -m "Add LogBackend: Telemetry to NT and file, debug values hidden in competition"`

---

### Task 2: Loggable, LogTable and LoggedValues

**Files:**
- Create: `log/Loggable.java`, `log/LogTable.java`, `log/LoggedValues.java`, test `log/LoggedValuesTest.java`

**Interfaces:**
- Consumes: `DebugPaths.add(String)` (Task 1).
- Produces:
  - `public interface Loggable { void log(LogTable table); default String logType() { return null; } }`
  - `public final class LogTable { LogTable(TelemetryTable table); public LogTable table(String name); public void put(String name, double|boolean|long|String|double[]|boolean[]|String[] value); public <T> void put(String name, T value); public void recordEveryLoop(String name); }` — constructor package-private.
  - `final class LoggedValues { LoggedValues(LogTable root, DebugPaths debugPaths, Consumer<String> warn); void add(String path, Supplier<?> supplier, Log.Dashboard level); void add(String path, Loggable loggable, Log.Dashboard level); void remove(String path); void update(); }`
  - `Log.Dashboard` enum `{ ALWAYS, DEBUG }` — declare it in `Log.java` in this task.

- [ ] **Step 1: Write the failing tests** in `LoggedValuesTest` with a `MockTelemetryBackend` at `""` and `new LogTable(Telemetry.getTable())`:
  - `supplierIsWrittenOnEveryUpdate` — supplier backed by a mutable field; update with `1.0` then `2.0`; mock last value at `/arm/angle` is `2.0`.
  - `loggableWritesUnderItsPath` — a `Loggable` putting `Voltage = 3.5`, registered at `motors/arm`; `/motors/arm/Voltage` is `3.5`.
  - `loggableTypeIsWritten` — `logType()` returns `"Motor"`; `/motors/arm/.type` is `"Motor"`.
  - `reRegisteringReplacesAndWarns` — register two suppliers at the same path; only the second's value is written; `warn` received one message containing the path.
  - `removedPathIsNoLongerWritten` — after `remove`, the next `update` adds no action for that path.
  - `throwingSupplierDoesNotStopOthers` — first supplier throws, second returns `5.0`; second is written; one warning.
  - `nullValueIsSkippedWithoutStoppingOthers` — supplier returns `null`; no exception, other values written.
  - `debugRegistrationAddsDebugPath` — `add(path, supplier, DEBUG)` makes `debugPaths.contains("/" + path)` true.
  - `recordEveryLoopKeepsDuplicates` — `recordEveryLoop("Voltage")` then put `3.5` twice; mock records a `KeepDuplicateValue` action for `/motors/arm/Voltage`.

- [ ] **Step 2: Run** `--tests first.demacia.utils.log.LoggedValuesTest` — Expected: compile FAIL.

- [ ] **Step 3: Implement.** `LoggedValues.update()` iterates a `LinkedHashMap<String, …>` in registration order; a `Loggable` is written by `root.table(path)`, setting the WPILib type via `TelemetryTable.setType(logType())` when not null; catch `RuntimeException` per path and warn once per path (a `Set<String>` of warned paths). `LogTable.put(name, Object)` ignores `null`.

- [ ] **Step 4: Run** — Expected: PASS (9 tests).

- [ ] **Step 5: Commit** `git commit -m "Add Loggable, LogTable and LoggedValues"`

---

### Task 3: Tuning, TuneTable, TunableGroup and Chooser

**Files:**
- Create: `log/TunableGroup.java`, `log/TuneTable.java`, `log/Chooser.java`, `log/Tuning.java`, test `log/TuningTest.java`

**Interfaces:**
- Produces:
  - `@FunctionalInterface public interface TunableGroup { void tune(TuneTable table); }`
  - `public final class TuneTable { public void number(String name, DoubleSupplier getter, DoubleConsumer setter); public void flag(String name, BooleanSupplier getter, BooleanConsumer setter); public void button(String name, Command command); }`
  - `public final class Chooser<T> { public void add(String name, T value); public void addDefault(String name, T value); public void onChange(Consumer<T> listener); public T getSelected(); }` — wraps `Selectable<T>`; constructor package-private.
  - `final class Tuning { Tuning(TunableTable root, DataLog file, Consumer<String> warn); void number(String path, DoubleSupplier, DoubleConsumer); void flag(String path, BooleanSupplier, BooleanConsumer); void array(String path, Supplier<double[]>, Consumer<double[]>); void group(String path, String type, TunableGroup group); <T> Chooser<T> chooser(String path); void field(String path, Field2d field); void button(String path, Command command); }`
  - Every dashboard edit of a number, flag, array, group child or chooser selection is appended to the file at `Tunables/<path>` (group child: `Tunables/<groupPath>/<name>`; chooser: the selected name as a string). The value is recorded only after the setter returned normally.

- [ ] **Step 1: Write the failing tests** in `TuningTest` with a `MockTunableBackend` registered at `""`, `root = TunableRegistry.getTable("/")`, an in-memory `DataLogWriter`; each edit is `mock.setDouble/setBoolean/setValue(...)` then `TunableRegistry.update()`:
  - `numberEditCallsSetterAndIsRecorded` — `number("motors/x/test Value", ...)`; edit to `2.0`; field equals `2.0`; `LogFiles.lastValues(...).get("Tunables/motors/x/test Value")` is `2.0`.
  - `flagEditCallsSetterAndIsRecorded` — same for `true`.
  - `arrayEditCallsSetterAndIsRecorded` — `double[] {1, 2}`.
  - `groupChildrenArePublishedUnderTheGroupPath` — group `"motors/x/Motion Magic Config"` with `number("Vel", ...)`; edit `/motors/x/Motion Magic Config/Vel` to `4.0`; setter received `4.0`; recorded at `Tunables/motors/x/Motion Magic Config/Vel`.
  - `chooserSelectionNotifiesListener` — `addDefault("A", 1)`, `add("B", 2)`; set the chooser's `selected` child to `"B"`; listener received `2`; `getSelected()` is `2`.
  - `buttonRunsCommand` — `button("chassis/reset gyro", command)`; set `/chassis/reset gyro/running` to `true`; `CommandScheduler.getInstance().isScheduled(command)` is true. `@AfterEach`: `CommandScheduler.getInstance().cancelAll()`. If the scheduler needs HAL in tests, call `HAL.initialize(500, 0)` in `@BeforeAll`.
  - `duplicatePathWarnsAndKeepsFirst` — `number` twice at one path; one warning; edits reach the first setter.
  - `throwingSetterDoesNotRecordAndWarns` — setter throws `IllegalArgumentException`; no `Tunables/...` entry in the file; one warning; a second tunable edited in the same update still works.

- [ ] **Step 2: Run** `--tests first.demacia.utils.log.TuningTest` — Expected: compile FAIL.

- [ ] **Step 3: Implement.** Publish through `root.publishDouble/publishBoolean/publishValue(..., double[].class)`; groups through `root.publish(path, ComplexTunable)` with an adapter whose `getTunableType()` returns `type` and whose `publishTunable(TunableTable t)` calls `group.tune(new TuneTable(t, ...))`; `button` through `root.publish(path, command)`; `field` through `root.publish(path, field)`. File entries are `DoubleLogEntry` / `BooleanLogEntry` / `DoubleArrayLogEntry` / `StringLogEntry` created lazily per path. Duplicate detection: a `Set<String>` of published paths.

- [ ] **Step 4: Run** — Expected: PASS (8 tests).

- [ ] **Step 5: Commit** `git commit -m "Add Tuning: tunables, groups, choosers and buttons recorded to the log file"`

---

### Task 4: ElasticNotifier and Alerts

**Files:**
- Create: `log/ElasticNotifier.java`, `log/Alerts.java`, tests `log/ElasticNotifierTest.java`, `log/AlertsTest.java`

**Interfaces:**
- Produces:
  - `interface ElasticNotifier { void notify(Log.Severity severity, String title, String description, double displaySeconds); static ElasticNotifier networkTables(NetworkTableInstance inst); static String toJson(Log.Severity severity, String title, String description, double displaySeconds); }` — publishes `toJson(...)` to `/Elastic/RobotNotifications` with `PubSubOption.sendAll(true)` and `keepDuplicates(true)`.
  - `final class Alerts { Alerts(ElasticNotifier notifier, DoubleSupplier clockSeconds, BooleanSupplier elasticConnected, Consumer<String> fileLog); void alert(String message, boolean active); void alert(String message); void update(); }`
  - `Log.Severity` enum `{ INFO, WARNING, ERROR }` — declare in `Log.java` in this task.
- JSON shape (Elastic): `{"level":"WARNING","title":"<title>","description":"<description>","displayTime":<ms>,"width":350.0,"height":-1.0}`; `displayTime` is `displaySeconds * 1000` as an integer; `0` means "stay until dismissed".

- [ ] **Step 1: Write the failing tests.**
  - `ElasticNotifierTest.notificationJsonHasElasticFields` — `toJson(WARNING, "Arm hot", "", 0)` equals `{"level":"WARNING","title":"Arm hot","description":"","displayTime":0,"width":350.0,"height":-1.0}`.
  - `ElasticNotifierTest.notificationJsonEscapesQuotesAndBackslashes` — title `say "hi" \ bye` appears as `say \"hi\" \\ bye`.
  - `ElasticNotifierTest.networkTablesPublishesToElasticTopic` — subscriber on `/Elastic/RobotNotifications` of a `NetworkTableInstance.create()` receives the JSON.
  - `AlertsTest` uses a recording fake notifier, a mutable clock, a mutable `elasticConnected`:
    - `risingEdgeLogsAndNotifiesOnce` — Elastic ready; `alert("Arm hot", true)` three times; one notification, one file line containing `Arm hot`.
    - `falseThenTrueNotifiesAgain` — true, false, true (clock advanced 2 s between) → two notifications.
    - `popupHeldUntilElasticConnectedTwoSeconds` — not connected; `alert(..., true)`; connect; `update()` at +1.9 s → none; at +2.0 s → one.
    - `heldPopupDroppedIfConditionCleared` — raised, cleared, then Elastic ready → no notification.
    - `fiftyFirstDistinctMessageRaisesOverflow` — 51 distinct messages with Elastic ready; the notifications are the first 50 messages plus one titled `Too many Log.alert messages`; a 52nd distinct message adds no notification.
    - `oneTimeAlertStaysActive` — `alert("bad CAN id")`; one notification; calling again sends nothing.

- [ ] **Step 2: Run** `--tests first.demacia.utils.log.ElasticNotifierTest --tests first.demacia.utils.log.AlertsTest` — Expected: compile FAIL.

- [ ] **Step 3: Implement** following the 2026 `Log.alert` in `DemaciaCode-3` branch `tashtiot` (`src/main/java/frc/demacia/utils/log/Log.java`, methods `alert`, `showPopup`, `sendPendingPopups`, `alertOverflow`) with these differences: the WPILib alert is `new Alert("Alerts", message, message, Alert.Level.MEDIUM)` (2027 needs an id; the message is the id); time comes from `clockSeconds`; Elastic readiness from `elasticConnected` plus the 2 s hold. Pop-ups from `Alerts` use `displaySeconds = 0`.

- [ ] **Step 4: Run** — Expected: PASS (9 tests).

- [ ] **Step 5: Commit** `git commit -m "Add Alerts with Elastic pop-ups (ported from 2026)"`

---

### Task 5: Messages

**Files:**
- Create: `log/Messages.java`, test `log/MessagesTest.java`
- Modify: `log/ConsoleAlert.java` only if a signature must change for `Messages` (keep its behavior)

**Interfaces:**
- Produces: `final class Messages { Messages(Consumer<String> fileLog); ConsoleAlert log(String text, Log.Severity severity); void update(); }` — `fileLog` is `DataLogManager::log` in production. `Log.log` ignores the returned alert.

- [ ] **Step 1: Write the failing tests:**
  - `messageIsWrittenToTheFile` — `log("arm ready", INFO)`; `fileLog` received `"arm ready"`.
  - `repeatedMessageReusesItsAlert` — logging the same text twice returns the same `ConsoleAlert` instance (`assertSame`).
  - `differentMessagesGetDifferentAlerts` — two texts return two different instances.
- [ ] **Step 2: Run** `--tests first.demacia.utils.log.MessagesTest` — Expected: compile FAIL.
- [ ] **Step 3: Implement** by moving today's `Log.log(Object, Level)` body (reuse by text, `CONSOLE_LIMIT`, expiry in `update()`) into `Messages`, mapping `Log.Severity` to `Alert.Level`.
- [ ] **Step 4: Run** — Expected: PASS.
- [ ] **Step 5: Commit** `git commit -m "Move Log.log behavior into Messages"`

---

### Task 6: Rewrite the Log facade (new API next to the old one)

**Files:**
- Modify: `log/Log.java`

**Interfaces:**
- Consumes: Tasks 1–5.
- Produces (all `public static`):
  - `void putData(String path, Supplier<?> supplier)`, `void putData(String path, Supplier<?> supplier, Dashboard level)`
  - `void putData(String path, Loggable loggable)`, `void putData(String path, Loggable loggable, Dashboard level)`
  - `void put(String path, double|boolean|String value)`, `<T> void put(String path, T value)`
  - `void remove(String path)`
  - `void tune(String path, DoubleSupplier, DoubleConsumer)`, `void tune(String path, BooleanSupplier, BooleanConsumer)`, `void tune(String path, String type, TunableGroup group)`, `void tune(String path, Field2d field)`, `void tuneArray(String path, Supplier<double[]>, Consumer<double[]>)`, `<T> Chooser<T> chooser(String path)`, `void button(String path, Command command)`
  - `void log(Object message)`, `void log(Object message, Severity severity)` — return `void`.
  - `void alert(String message, boolean active)`, `void alert(String message)`
- The old methods (`putData(..., LogLevel, ...)` grouped forms, `publishTelemetry`, `removeTelemetry`, `removeInComp`, `clearEntries`, `LogLevel`, the uncommitted `putData(name, getter, setter)` overloads) stay, marked `@Deprecated`, until Task 11 — except `putData(String, Supplier)` whose body now delegates to `LoggedValues`.

- [ ] **Step 1: Restructure startup** per the spec's "Startup" list: a private `start()` called once from the static initializer creates `LogBackend`, `LoggedValues` (root `new LogTable(Telemetry.getTable())`), `Tuning` (root `TunableRegistry.getTable("/")`, `DataLogManager.getLog()`), `Alerts` (`ElasticNotifier.networkTables(NetworkTableInstance.getDefault())`, `Timer::getFPGATimestamp`, Elastic check = any `NetworkTableInstance.getDefault().getConnections()` whose `remote_id` starts with `"Elastic"`, `DataLogManager::log`), `Messages(DataLogManager::log)`; sets `TelemetryRegistry.setReportWarning` and `TunableRegistry.setReportWarning` to `Log.log(..., WARNING)` deduplicated by message; publishes SysId and LoadLatestLog with `button("sysID/sysidCommand", new SysidCommand())` and `button("replay/LoadLatestLog", new LogReplayCommand())`.
- [ ] **Step 2: `periodic()`** = `Data.refreshAll()`, `values.update()`, `alerts.update()`, `messages.update()`, then the deprecated group entries (until Task 11).
- [ ] **Step 3: Run** `./gradlew build --offline` — Expected: BUILD SUCCESSFUL, all tests pass.
- [ ] **Step 4: Verify in simulation** (headless; see Task 12 Step 1 for the command): no exceptions in the console; the `.wpilog` has `Telemetry/chassis/gyro angle`.
- [ ] **Step 5: Commit** `git commit -m "Rewrite Log as a facade over the new log units"`

---

### Task 7: Migrate motors

**Files:**
- Modify: `motors/MotorInterface.java`, `motors/BaseMotor.java` (and `TalonFXMotor`, `SparkMaxMotor`, `SparkFlexMotor` only if they reference changed signatures)

No new unit test: constructing a `BaseMotor` pulls in Phoenix simulation, `ElasticGenerator` and
`Sysid`. The behavior this task must keep — four SysId values written every loop — is pinned by
Task 10's `motorGroupHasFourEqualLengthSeries` (the same `recordEveryLoop` contract) and checked on
the real motors in Task 12 Step 3.

- [ ] **Step 1: Migrate** per the spec's Migration table: `MotorInterface extends Loggable`; `logTo(TelemetryTable)` → `log(LogTable)` with `recordEveryLoop` for `Position`, `Velocity`, `Acceleration`, `Voltage` and `logType()` returning `"Motor"`; `Log.publishTelemetry` → `Log.putData("motors/" + name, this)`; delete the grouped `Log.putData(name + ": Position, …", new Data[]{…}, LogLevel.LOG_ONLY, "motors", false)`; `wanted value` / `current value` / `is Connected` → `Log.putData(..., Log.Dashboard.ALWAYS)`; test Value → `Log.tune(...)`; test value command → `Log.button`; control mode `Selectable` → `Log.chooser`; PID+FF and Motion Magic `ComplexTunable`s → `Log.tune(path, type, t -> …)` with `t.button("Update", cmd)` replacing the hand-written `Update` boolean.
- [ ] **Step 2: Run** `./gradlew build --offline` — Expected: PASS.
- [ ] **Step 3: Commit** `git commit -m "Migrate motors to the new Log API"`

---

### Task 8: Migrate sensors

**Files:**
- Modify: `sensors/SensorInterface.java`, `AnalogEncoder`, `Cancoder`, `ColorSensor`, `DigitalEncoder`, `LimitSwitch`, `OpticalSensor`, `Pigeon`, `Pneumatics`

- [ ] **Step 1:** For each sensor, list the values its grouped `Log.putData(..., LogLevel.LOG_ONLY, …)` call logs and check that its `logTo` writes each of them; add any missing one to the new `log(LogTable)`. `Pneumatics` has no `logTo`: make it implement `Loggable` writing `Compressor State`, `Pressure Switch`, `Current`, `Analog Voltage`, `Analog Pressure`.
- [ ] **Step 2: Migrate:** `SensorInterface extends Loggable`; `logTo` → `log(LogTable)`; `Log.publishTelemetry("sensors/" + name, this)` → `Log.putData("sensors/" + name, this)`; delete the grouped calls; update the `SensorInterface` Javadoc.
- [ ] **Step 3: Run** `./gradlew build --offline` — Expected: PASS.
- [ ] **Step 4: Commit** `git commit -m "Migrate sensors to the new Log API"`

---

### Task 9: Migrate mechanisms, chassis, vision, RobotCommon, Robot and ElasticGenerator

**Files:**
- Modify: `mechanisms/BaseMechanism.java`, `mechanisms/StateBaseMechanism.java`, `chassis/Chassis.java`, `RobotCommon.java`, `elastic/ElasticGenerator.java`, `src/main/java/first/demacia/vision/ObjectPose.java`, `src/main/java/first/demacia/vision/subsystem/ObjectPose.java`, `src/main/java/first/demacia/vision/subsystem/Camera.java`, `src/main/java/first/robot/Robot.java`

- [ ] **Step 1: Migrate** per the Migration table:
  - `BaseMechanism implements Loggable`; `log(LogTable)` writes `command` and `default command` names (what `SubsystemBase.logTo` wrote) plus the three `Telemetry.log` values now in `periodic` (lines ~776–778); `StateBaseMechanism.log` adds the state name; `Tunables.publish(…, command)` → `Log.button`; `Selectable` → `Log.chooser`; `publishValue(..., double[].class)` → `Log.tuneArray`.
  - `Chassis`: field → `Log.tune("chassis/field", field)`; each `addBoolean` + `Trigger` + `set(false)` block → one `Log.button(path, command)`.
  - `RobotCommon`: drop `implements ComplexTunable`; `Log.tune("RC", "RobotCommon", t -> { t.flag("is red", …); t.flag("is comp", …); })`.
  - `ElasticGenerator`: `Telemetry.log` → `Log.put`; `/Log/motors/...` → `/Telemetry/motors/...`; `/Log/quest/...` → `/Telemetry/quest/...`; button paths unchanged (`/Tunables/...`).
  - Vision `ObjectPose` (both), `Camera`, `Robot`: `Telemetry.log` → `Log.put`; `publishTelemetry(…, field)` and `Tunables.publish(…, field)` → `Log.tune(path, field)`; `subsystem/ObjectPose.logTo` → `log(LogTable)`; `addBoolean` + `Trigger` → `Log.button`.
- [ ] **Step 2: Run** `./gradlew build --offline` — Expected: PASS.
- [ ] **Step 3: Check** `grep -rlE "import org\.wpilib\.(telemetry|tunable)\." src/main --include=*.java | grep -v /utils/log/` prints nothing except `log/LogReplay.java` (Task 10).
- [ ] **Step 4: Commit** `git commit -m "Migrate mechanisms, chassis, vision and dashboard tools to the new Log API"`

---

### Task 10: LogReader, SysId and LogReplay on the per-entry format

**Files:**
- Modify: `log/LogReader.java`, `utils/sysid/SysidCommand.java`, `src/main/java/first/demacia/sysid/SysidApp.java`, `log/LogReplay.java`
- Test: `log/LogReaderTest.java`

**Interfaces:**
- Produces: `public static Map<String, List<Entry>> LogReader.getGroups(File file, Predicate<EntryInfo> filter)` — groups by parent path (`"Telemetry/motors/arm"` → entries `Position`, `Velocity`, …); the existing `getGroups(boolean isLatestLog, Predicate<EntryInfo>)` finds the file and calls it. Entries under `Telemetry/replay/` are skipped.

- [ ] **Step 1: Write the failing tests** — each writes a `.wpilog` into a JUnit `@TempDir` with `new DataLogWriter(path)` through `LogBackend` + `LoggedValues` and a motor-like `Loggable` that calls `recordEveryLoop` on its four values, run for 5 updates with a constant voltage of `6.0`:
  - `motorGroupHasFourEqualLengthSeries` — `getGroups(file, info -> info.name().startsWith("Telemetry/motors/"))` contains `"Telemetry/motors/arm"`; its `Position`, `Velocity`, `Acceleration`, `Voltage` entries each have 5 points; every `Voltage` value is `6.0`.
  - `replayEntriesAreSkipped` — an entry at `replay/motors/arm/Voltage` does not appear in any group.
- [ ] **Step 2: Run** `--tests first.demacia.utils.log.LogReaderTest` — Expected: FAIL (`getGroups(File, …)` missing).
- [ ] **Step 3: Implement** `getGroups(File, …)`; delete the grouped-array (`"Float/"…`) parsing branch. `LogReader` must not call `Log` (it also runs in the laptop `SysidApp`); use `System.out` for its messages.
- [ ] **Step 4: Update callers:** `SysidCommand` and `SysidApp` filter `info -> info.name().startsWith("Telemetry/motors/")`; motor matching by `groupName.endsWith("/" + rawName)` stays. `LogReplay` publishes with `Log.putData("replay/" + path, supplier)` and uses `Log.tune`/`Log.button` instead of `ComplexTunable`.
- [ ] **Step 5: Run** `./gradlew build --offline` — Expected: PASS.
- [ ] **Step 6: Commit** `git commit -m "Read the per-entry log format in LogReader, SysId and LogReplay"`

---

### Task 11: Delete the old log API

**Files:**
- Modify: `log/Log.java`, `utils/Data.java`
- Delete: `log/LogEntry.java`, test `DataTest.java`

- [ ] **Step 1: Delete** from `Log`: `LogLevel`, all `@Deprecated` methods, the group entry fields, the public static fields `log` and `table`. From `Data`: the group arrays and their methods, `hasChanged`/change detection, `getTime`, `toLogTimestamp` (keep signal refresh and value accessors used by motors). Delete `LogEntry` and `DataTest`.
- [ ] **Step 2: Run** `./gradlew build --offline` — Expected: BUILD SUCCESSFUL; fix any remaining caller by the Migration table.
- [ ] **Step 3: Check** `grep -rlE "import org\.wpilib\.(telemetry|tunable)\." src/main --include=*.java | grep -v /utils/log/` prints nothing.
- [ ] **Step 4: Commit** `git commit -m "Remove the old log API, LogEntry and Data grouping"`

---

### Task 12: End-to-end verification in simulation

**Files:** none committed (scripts go to the scratchpad).

- [ ] **Step 1: Run the robot headless for 30 s:** print the runtime classpath with an init script (`tasks.register('printRuntimeCp') { doLast { println sourceSets.main.runtimeClasspath.asPath } }`), then `timeout 30 java -Djava.library.path=build/jni/release -cp <classpath> first.Main`. Expected: no exception in the output.
- [ ] **Step 2: While it runs, change a tunable** with a small NT client program (`NetworkTableInstance.create().startClient("localhost")`, set `/Tunables/motors/<any motor>/test Value` to `1.5`).
- [ ] **Step 3: Read the new `.wpilog`** with `DataLogReader`. Expected: entries under `Telemetry/motors/`, `Telemetry/sensors/`; `Tunables/motors/<motor>/test Value` with `1.5`; no entry starting with `Float/`, `Boolean/` or `String/`; each motor's `Voltage` has as many records as its `Position`.
- [ ] **Step 4:** Report results; nothing to commit.
