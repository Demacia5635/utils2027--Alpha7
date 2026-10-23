// Copyright (c) FIRST and other WPILib contributors.
// Open Source Software; you can modify and/or share it under the terms of
// the WPILib BSD license file in the root directory of this project.

package first.demacia.utils.log;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;
import java.util.function.Supplier;

import org.wpilib.networktables.ConnectionInfo;
import org.wpilib.networktables.NetworkTable;
import org.wpilib.networktables.NetworkTableInstance;
import com.ctre.phoenix6.StatusSignal;
import org.wpilib.system.DataLogManager;
import org.wpilib.system.Timer;
import org.wpilib.command2.Command;
import org.wpilib.command2.SubsystemBase;
import org.wpilib.datalog.DataLog;
import org.wpilib.driverstation.DriverStation;
import org.wpilib.smartdashboard.Field2d;
import org.wpilib.telemetry.Telemetry;
import org.wpilib.telemetry.TelemetryLoggable;
import org.wpilib.telemetry.TelemetryRegistry;
import org.wpilib.tunable.ComplexTunable;
import org.wpilib.tunable.TunableRegistry;
import org.wpilib.tunable.Tunables;
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
 */
public class Log extends SubsystemBase {

  /**
   * Enumeration for different logging levels.
   * Defines behavior for file logging and NetworkTables updating, both in and out of competition.
   *
   * @deprecated Use {@link Dashboard} with {@link #putData(String, Supplier, Dashboard)}.
   */
  @Deprecated
  public static enum LogLevel {
    /** Log to file only, but remove entirely during competition */
    LOG_ONLY_NOT_IN_COMP,
    /** Log to file only */
    LOG_ONLY,
    /** Log to file and NetworkTables only when not in competition */
    LOG_AND_NT_NOT_IN_COMP,
    /** Log to file and NetworkTables */
    LOG_AND_NT
  }

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

  /** The main DataLog instance for file writing */
  public static DataLog log;
  /**
   * The NetworkTable instance for the "Log" table.
   *
   * @deprecated Used only by the deprecated {@link LogEntry}; values now go to {@code /Telemetry}.
   */
  @Deprecated
  public static NetworkTable table;

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

  /** List of individual log entries that are not grouped */
  private ArrayList<LogEntry<?>> individualLogEntries = new ArrayList<>();

  private LogEntry<float[]> groupFloatEntry;
  private LogEntry<boolean[]> groupBooleanEntry;
  private LogEntry<String[]> groupStringEntry;

  /**
   * Objects that are re-logged to Telemetry every loop, keyed by their telemetry path.
   * Kept for the deprecated {@link #publishTelemetry}.
   */
  private final Map<String, TelemetryLoggable> telemetryObjects = new LinkedHashMap<>();

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
    log = DataLogManager.getLog();
    DriverStation.startDataLog(log);
    table = NetworkTableInstance.getDefault().getTable("Log");

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

    // Deprecated API, removed with it
    for (int i = 0; i < individualLogEntries.size(); i++) {
      individualLogEntries.get(i).log();
    }
    if (groupFloatEntry != null) {
      groupFloatEntry.log();
    }
    if (groupBooleanEntry != null) {
      groupBooleanEntry.log();
    }
    if (groupStringEntry != null) {
      groupStringEntry.log();
    }
    telemetryObjects.forEach(Telemetry::log);
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

  // ---------------------------------------------------------------- deprecated API

  /**
   * Removes non-essential log entries when in competition mode.
   *
   * @deprecated Register values with {@link Dashboard#DEBUG}; competition mode is applied automatically.
   */
  @Deprecated
  public static void removeInComp() {
    if (logManager == null)
      return;

    for (int i = 0; i < logManager.individualLogEntries.size(); i++) {
      LogEntry<?> entry = logManager.individualLogEntries.get(i);
      entry.removeInComp();
      if (entry.getLogLevel() == LogLevel.LOG_ONLY_NOT_IN_COMP
          || logManager.individualLogEntries.get(i).getLogLevel() == LogLevel.LOG_AND_NT_NOT_IN_COMP) {
        logManager.individualLogEntries.remove(i);
        i--;
      }
    }
  }

  /**
   * Clears all log entries from the manager.
   *
   * @deprecated Use {@link #remove(String)}.
   */
  @Deprecated
  public static void clearEntries() {
    if (logManager != null) {
      logManager.individualLogEntries.clear();
    }
  }

  /**
   * Registers an object to be logged to Telemetry every loop.
   *
   * @param path     The telemetry path (for example {@code "motors/intake"})
   * @param loggable The object to log
   * @deprecated Implement {@link Loggable} and use {@link #putData(String, Loggable)}.
   */
  @Deprecated
  public static void publishTelemetry(String path, TelemetryLoggable loggable) {
    logManager.telemetryObjects.put(path, loggable);
  }

  /**
   * Stops logging an object that was registered with {@link #publishTelemetry}.
   *
   * @param path The telemetry path the object was registered with
   * @deprecated Use {@link #remove(String)}.
   */
  @Deprecated
  public static void removeTelemetry(String path) {
    logManager.telemetryObjects.remove(path);
  }

  /**
   * Publishes an editable object.
   *
   * @param key     The dashboard path
   * @param tunable The object
   * @deprecated Use {@link #tune(String, String, TunableGroup)}.
   */
  @Deprecated
  public static void putData(String key, ComplexTunable tunable) {
    Tunables.publish(key, tunable);
  }

  /**
   * Starts building a new log entry from a Phoenix6 StatusSignal.
   *
   * @param <T>          The type of data
   * @param name         The name of the log entry
   * @param statusSignal The signal to log
   * @param isRio        Whether the signal is on the RIO CAN bus
   * @return The log entry
   * @deprecated Use {@link #putData(String, Supplier)}.
   */
  @Deprecated
  @SuppressWarnings("unchecked")
  public static <T> LogEntry<T> putData(String name, StatusSignal<T> statusSignal, boolean isRio) {
    return putData(name, new StatusSignal[] {statusSignal}, LogLevel.LOG_AND_NT, "", true, isRio);
  }

  /**
   * Starts building a new log entry from data.
   *
   * @param <T>  The type of data
   * @param name The name of the log entry
   * @param data The data to log
   * @return The log entry
   * @deprecated Use {@link #putData(String, Supplier)}.
   */
  @Deprecated
  @SuppressWarnings("unchecked")
  public static <T> LogEntry<T> putData(String name, Data<T> data) {
    return putData(name, new Data[] {data}, LogLevel.LOG_AND_NT, "", true);
  }

  /**
   * @deprecated Use {@link #putData(String, Loggable)} with {@link LogTable#recordEveryLoop}.
   */
  @Deprecated
  @SuppressWarnings("unchecked")
  public static <T> LogEntry<T> putData(String name, StatusSignal<T>[] statusSignals, LogLevel logLevel, String metaData, boolean isSeparated, boolean isRio) {
    Data<T>[] data;
    data = (Data<T>[]) new Data[statusSignals.length];
    for (int i = 0; i < statusSignals.length; i++) {
        data[i] = new Data<>(statusSignals[i], isRio);
    }
    return putData(name, data, logLevel, metaData, isSeparated);
  }

  /**
   * @deprecated Use {@link #putData(String, Supplier, Dashboard)}.
   */
  @Deprecated
  @SuppressWarnings("unchecked")
  public static <T> LogEntry<T> putData(String name, Supplier<T>[] suppliers, LogLevel logLevel, String metaData, boolean isSeparated) {
    Data<T>[] data;
    data = (Data<T>[]) new Data[suppliers.length];
    for (int i = 0; i < suppliers.length; i++) {
        data[i] = new Data<>(suppliers[i]);
    }
    return putData(name, data, logLevel, metaData, isSeparated);
  }

  /**
   * Internal method to add a log entry to the manager.
   *
   * @param <T>         The data type
   * @param name        Name of the entry
   * @param data        Data wrapper
   * @param logLevel    Logging level
   * @param metaData    Metadata
   * @param isSeparated Whether to force a separate entry
   * @return The created or updated LogEntry
   * @deprecated Use {@link #putData(String, Supplier, Dashboard)} or {@link #putData(String, Loggable, Dashboard)}.
   */
  @Deprecated
  @SuppressWarnings("unchecked")
  public static <T> LogEntry<T> putData(String name, Data<T>[] data, LogLevel logLevel, String metaData, boolean isSeparated) {
    LogEntry<T> entry = null;

    if (isSeparated && data.length == 1) {
      entry = new LogEntry<T>(name, data[0], logLevel, metaData);
      logManager.individualLogEntries.add(entry);
    } else {
      if (data[0].isDouble()) {
        Data.addToGroupFloat(name, metaData, data);
        if (logManager.groupFloatEntry == null){
          logManager.groupFloatEntry = new LogEntry<float[]>(Data.getGroupFloatName(), () -> Data.getGroupFloat(), LogLevel.LOG_ONLY, Data.getGroupDoubleMetaData(), true, false);
        } else {
          logManager.groupFloatEntry.initialize(Data.getGroupFloatName(), () -> Data.getGroupFloat(), LogLevel.LOG_ONLY, Data.getGroupDoubleMetaData(), true, false);
        }
        entry = (LogEntry<T>) logManager.groupFloatEntry;

      } else if (data[0].isBoolean()) {
        Data.addToGroupBoolean(name, metaData, data);
        if (logManager.groupBooleanEntry == null){
          logManager.groupBooleanEntry = new LogEntry<boolean[]>(Data.getGroupBooleanName(), () -> Data.getGroupBoolean(), LogLevel.LOG_ONLY, Data.getGroupBooleanMetaData(), false, true);
        } else {
          logManager.groupBooleanEntry.initialize(Data.getGroupBooleanName(), () -> Data.getGroupBoolean(), LogLevel.LOG_ONLY, Data.getGroupBooleanMetaData(), false, true);
        }
        entry = (LogEntry<T>) logManager.groupBooleanEntry;

      } else {
        Data.addToGroupString(name, metaData, data);
        if (logManager.groupStringEntry == null){
          logManager.groupStringEntry = new LogEntry<String[]>(Data.getGroupStringName(), () -> Data.getGroupString(), LogLevel.LOG_ONLY, Data.getGroupStringMetaData(), false, false);
        } else {
          logManager.groupStringEntry.initialize(Data.getGroupStringName(), () -> Data.getGroupString(), LogLevel.LOG_ONLY, Data.getGroupStringMetaData(), false, false);
        }
        entry = (LogEntry<T>) logManager.groupStringEntry;
      }
    }

    return entry;
  }
}
