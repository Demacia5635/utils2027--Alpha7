// Copyright (c) FIRST and other WPILib contributors.
// Open Source Software; you can modify and/or share it under the terms of
// the WPILib BSD license file in the root directory of this project.

package first.demacia.utils.log;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;
import java.util.function.Supplier;

import org.wpilib.networktables.ConnectionInfo;
import org.wpilib.networktables.NetworkTableInstance;
import org.wpilib.system.DataLogManager;
import org.wpilib.system.Timer;
import org.wpilib.command2.Command;
import org.wpilib.command2.SubsystemBase;
import org.wpilib.datalog.DataLog;
import org.wpilib.driverstation.DriverStation;
import org.wpilib.smartdashboard.Field2d;
import org.wpilib.telemetry.Telemetry;
import org.wpilib.telemetry.TelemetryRegistry;
import org.wpilib.tunable.TunableRegistry;
import org.wpilib.util.function.BooleanConsumer;

import first.demacia.utils.Data;
import first.demacia.utils.RobotCommon;
import first.demacia.utils.sysid.SysidCommand;

/**
 * The robot's logging entry point: values written every loop, values edited from the dashboard,
 * dashboard buttons, console messages and alerts.
 * <p>
 * Everything is written to the log file. Values go to NetworkTables under {@code /Telemetry},
 * editable values under {@code /Tunables}. Values registered as {@link Dashboard#DEBUG} stop
 * updating the dashboard while the robot is in competition.
 * </p>
 * <p>
 * Do not call {@code Log} from static initializers that run before the {@code Robot} constructor:
 * {@code RobotBase} registers its own telemetry backends then and would replace the log backend.
 * Choose {@link Dashboard#DEBUG} before the first write to a path. A chooser registered at a
 * path that is already in use is returned unpublished.
 * </p>
 */
public class Log extends SubsystemBase {

  /**
   * Where a logged value is shown.
   */
  public static enum Dashboard {
    /** Written to the log file and the dashboard */
    ALWAYS,
    /** Written to the log file always, and to the dashboard only outside competition */
    DEBUG
  }

  /**
   * How serious a message or alert is. Maps to the WPILib alert levels LOW, MEDIUM and HIGH.
   */
  public static enum Severity {
    /** Information, nothing is wrong */
    INFO,
    /** Something needs attention */
    WARNING,
    /** Something is broken */
    ERROR
  }

  /** Singleton instance, runs {@link #periodic()} */
  private static Log logManager;

  /** Telemetry paths hidden from the dashboard in competition */
  private static DebugPaths debugPaths;
  /** Root of the telemetry paths, for one-time values */
  private static LogTable root;
  /** Values and objects written every loop */
  private static LoggedValues values;
  /** Values edited from the dashboard */
  private static Tuning tuning;
  /** {@link #alert} alerts and Elastic pop-ups */
  private static Alerts alerts;
  /** {@link #log} console messages */
  private static Messages messages;

  /** Guards {@link #messages}, which WPILib warning reporters may reach from other threads */
  private static final Object messagesLock = new Object();
  /** Registry warnings already reported */
  private static Set<String> reportedWarnings;
  /** True while a message is being written, so a warning raised by writing it does not re-enter Messages */
  private static boolean writingMessage;

  // Runs after every static field above is initialized, so start() may use them.
  static {
    start();
  }

  private Log() {
  }

  /**
   * Starts logging. Runs once, from the static initializer, the first time Log is used.
   * Each unit is created before anything that may write to it.
   */
  private static void start() {
    if (logManager != null) {
      return;
    }
    DataLogManager.start();
    DataLogManager.logNetworkTables(false);
    DataLog log = DataLogManager.getLog();
    DriverStation.startDataLog(log);

    // Messages first: every warning below is reported through it
    messages = new Messages(DataLogManager::log);
    reportedWarnings = ConcurrentHashMap.newKeySet();
    TelemetryRegistry.setReportWarning((path, msg) -> warnOnce("Telemetry " + path + ": " + msg));
    TunableRegistry.setReportWarning(msg -> warnOnce("Tunables: " + msg));

    debugPaths = new DebugPaths();
    TelemetryRegistry.registerBackend("", LogBackend.create(
        NetworkTableInstance.getDefault(), log, debugPaths, RobotCommon::getIsComp));

    Consumer<String> warn = message -> log(message, Severity.WARNING);
    root = new LogTable(Telemetry.getTable());
    values = new LoggedValues(root, debugPaths, warn);
    tuning = new Tuning(TunableRegistry.getTable("/"), log, warn);
    alerts = new Alerts(ElasticNotifier.networkTables(NetworkTableInstance.getDefault()),
        Timer::getTimestamp, Log::isElasticConnected, DataLogManager::log);

    logManager = new Log();
    log("log manager is ready");

    button("sysID/sysidCommand", new SysidCommand());
    button("replay/LoadLatestLog", new LogReplayCommand());
    RobotCommon.init();
  }

  /** True when an Elastic dashboard is connected to NetworkTables. */
  private static boolean isElasticConnected() {
    for (ConnectionInfo connection : NetworkTableInstance.getDefault().getConnections()) {
      if (connection.remoteId.startsWith("Elastic")) {
        return true;
      }
    }
    return false;
  }

  /** Reports a WPILib registry warning once per distinct message. Must not throw. */
  private static void warnOnce(String message) {
    if (!reportedWarnings.add(message)) {
      return;
    }
    try {
      log(message, Severity.WARNING);
    } catch (RuntimeException e) {
      System.err.println(message);
    }
  }

  /**
   * Every loop: refreshes the Phoenix signals, writes every registered value, sends held alert
   * pop-ups and expires console messages.
   */
  @Override
  public void periodic() {
    Data.refreshAll();
    values.update();
    alerts.update();
    synchronized (messagesLock) {
      messages.update();
    }

  }

  // ---------------------------------------------------------------- recording values

  /**
   * Writes a value every loop and shows it on the dashboard.
   *
   * @param path     The log path, for example {@code "chassis/gyro angle"}
   * @param supplier Provides the value; a null value is skipped
   */
  public static void putData(String path, Supplier<?> supplier) {
    putData(path, supplier, Dashboard.ALWAYS);
  }

  /**
   * Writes a value every loop.
   *
   * @param path     The log path
   * @param supplier Provides the value; a null value is skipped
   * @param level    {@link Dashboard#DEBUG} keeps the value off the dashboard in competition
   */
  public static void putData(String path, Supplier<?> supplier, Dashboard level) {
    values.add(path, supplier, level);
  }

  /**
   * Lets an object write its values every loop, under {@code path}, shown on the dashboard.
   *
   * @param path     The log path of the object's table
   * @param loggable The object
   */
  public static void putData(String path, Loggable loggable) {
    putData(path, loggable, Dashboard.ALWAYS);
  }

  /**
   * Lets an object write its values every loop, under {@code path}.
   *
   * @param path     The log path of the object's table
   * @param loggable The object
   * @param level    {@link Dashboard#DEBUG} keeps the values off the dashboard in competition
   */
  public static void putData(String path, Loggable loggable, Dashboard level) {
    values.add(path, loggable, level);
  }

  /**
   * Writes a value once, now.
   *
   * @param path  The log path
   * @param value The value
   */
  public static void put(String path, double value) {
    root.put(path, value);
  }

  /**
   * Writes a value once, now.
   *
   * @param path  The log path
   * @param value The value
   */
  public static void put(String path, boolean value) {
    root.put(path, value);
  }

  /**
   * Writes a value once, now.
   *
   * @param path  The log path
   * @param value The value
   */
  public static void put(String path, String value) {
    root.put(path, value);
  }

  /**
   * Writes a value once, now. A null value is skipped.
   *
   * @param <T>   The value type, for example {@code Pose2d}
   * @param path  The log path
   * @param value The value
   */
  public static <T> void put(String path, T value) {
    root.put(path, value);
  }

  /**
   * Stops writing a value or object registered with {@code putData}.
   *
   * @param path The path it was registered with
   */
  public static void remove(String path) {
    values.remove(path);
  }

  // ---------------------------------------------------------------- tuning

  /**
   * Publishes a number the dashboard can edit; every accepted edit is written to the log file.
   *
   * @param path   The dashboard path
   * @param getter Reads the current value
   * @param setter Applies an edit
   */
  public static void tune(String path, DoubleSupplier getter, DoubleConsumer setter) {
    tuning.number(path, getter, setter);
  }

  /**
   * Publishes a flag the dashboard can edit; every accepted edit is written to the log file.
   *
   * @param path   The dashboard path
   * @param getter Reads the current value
   * @param setter Applies an edit
   */
  public static void tune(String path, BooleanSupplier getter, BooleanConsumer setter) {
    tuning.flag(path, getter, setter);
  }

  /**
   * Publishes a group of editable values under one path.
   *
   * @param path  The dashboard path of the group
   * @param type  The type shown by the dashboard
   * @param group Adds the group's values
   */
  public static void tune(String path, String type, TunableGroup group) {
    tuning.group(path, type, group);
  }

  /**
   * Publishes a field the dashboard can show and edit.
   *
   * @param path  The dashboard path
   * @param field The field
   */
  public static void tune(String path, Field2d field) {
    tuning.field(path, field);
  }

  /**
   * Publishes an array the dashboard can edit; every accepted edit is written to the log file.
   *
   * @param path   The dashboard path
   * @param getter Reads the current values
   * @param setter Applies an edit
   */
  public static void tuneArray(String path, Supplier<double[]> getter, Consumer<double[]> setter) {
    tuning.array(path, getter, setter);
  }

  /**
   * Publishes a chooser; every selection is written to the log file.
   *
   * @param <T>  The option type
   * @param path The dashboard path
   * @return The chooser, to add options to
   */
  public static <T> Chooser<T> chooser(String path) {
    return tuning.chooser(path);
  }

  /**
   * Publishes a dashboard button that runs the command while it is on.
   *
   * @param path    The dashboard path
   * @param command The command to run
   */
  public static void button(String path, Command command) {
    tuning.button(path, command);
  }

  // ---------------------------------------------------------------- messages and alerts

  /**
   * Writes a message to the log file and shows it in the Alerts "console" group.
   *
   * @param message The message
   */
  public static void log(Object message) {
    log(message, Severity.INFO);
  }

  /**
   * Writes a message to the log file and shows it in the Alerts "console" group.
   *
   * @param message  The message
   * @param severity How serious it is
   */
  public static void log(Object message, Severity severity) {
    String text = String.valueOf(message);
    synchronized (messagesLock) {
      if (messages == null || writingMessage) {
        // Before startup created Messages, or a warning raised while writing a message
        System.err.println("[Log] " + text);
        return;
      }
      writingMessage = true;
      try {
        messages.log(text, severity);
      } finally {
        writingMessage = false;
      }
    }
  }

  /**
   * Raises or clears an alert. Safe to call every loop: the log line and Elastic pop-up happen
   * only when {@code active} becomes true. The message identifies the alert, keep it constant.
   *
   * @param message The alert text
   * @param active  Whether the condition holds
   */
  public static void alert(String message, boolean active) {
    alerts.alert(message, active);
  }

  /**
   * Raises an alert once and leaves it active.
   *
   * @param message The alert text
   */
  public static void alert(String message) {
    alerts.alert(message);
  }
}
