// Copyright (c) FIRST and other WPILib contributors.
// Open Source Software; you can modify and/or share it under the terms of
// the WPILib BSD license file in the root directory of this project.

package first.demacia.utils.log;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;
import java.util.function.Supplier;

import org.wpilib.networktables.NetworkTable;
import org.wpilib.networktables.NetworkTableInstance;
import com.ctre.phoenix6.StatusSignal;
import org.wpilib.system.DataLogManager;
import org.wpilib.command2.CommandScheduler;
import org.wpilib.command2.SubsystemBase;
import org.wpilib.datalog.DataLog;
import org.wpilib.driverstation.DriverStation;
import org.wpilib.util.Alert.Level;
import org.wpilib.telemetry.Telemetry;
import org.wpilib.telemetry.TelemetryLoggable;
import org.wpilib.tunable.ComplexTunable;
import org.wpilib.tunable.TunableTable;
import org.wpilib.tunable.Tunables;
import org.wpilib.tunable.TunableBoolean;
import org.wpilib.util.function.BooleanConsumer;
import org.wpilib.command2.button.Trigger;

import first.demacia.utils.Data;
import first.demacia.utils.RobotCommon;
import first.demacia.utils.sysid.SysidCommand;

/**
 * Centralized logging system for robot telemetry and diagnostics.
 * <p>
 * Manages the creation, updating, and optimization of log entries.
 * Handles both file logging (DataLog) and live dashboard updates
 * (NetworkTables).
 * </p>
 */
public class Log extends SubsystemBase {

  /**
   * Enumeration for different logging levels.
   * Defines behavior for file logging and NetworkTables updating, both in and out of competition.
   */
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


  /** Singleton instance of the LogManager */
  private static Log logManager;

  /** The main DataLog instance for file writing */
  public static DataLog log;
  /** The NetworkTable instance for the "Log" table */
  public static NetworkTable table = NetworkTableInstance.getDefault().getTable("Log");

  /** List of currently active console alerts */
  private static ArrayList<ConsoleAlert> activeConsole;

  /** List of individual log entries that are not grouped */
  private ArrayList<LogEntry<?>> individualLogEntries = new ArrayList<>();

  private LogEntry<float[]> groupFloatEntry;
  private LogEntry<boolean[]> groupBooleanEntry;
  private LogEntry<String[]> groupStringEntry;

  /**
   * Objects that are re-logged to Telemetry every loop, keyed by their telemetry path.
   * Replaces the automatic refresh that SmartDashboard.putData gave Sendables in 2026.
   */
  private final Map<String, TelemetryLoggable> telemetryObjects = new LinkedHashMap<>();

  /**
   * Private constructor to enforce Singleton pattern.
   * Initializes DataLogManager and starts logging.
   */
  private Log() {
    if (logManager != null) {
      CommandScheduler.getInstance().unregisterSubsystem(this);
      return;
    }
    logManager = this;
    DataLogManager.start();
    DataLogManager.logNetworkTables(false);
    log = DataLogManager.getLog();
    TelemetryFileLogging.install(NetworkTableInstance.getDefault(), log);
    DriverStation.startDataLog(log);

    activeConsole = new ArrayList<>();
    log("log manager is ready");

     TunableBoolean runSysidTrigger = Tunables.addBoolean("sysID/sysidCommand", false);
    new Trigger(runSysidTrigger).onTrue(
        new SysidCommand().andThen(() -> runSysidTrigger.set(false)));

    TunableBoolean loadLatestLogTrigger = Tunables.addBoolean("replay/LoadLatestLog", false);
    new Trigger(loadLatestLogTrigger).onTrue(
        new LogReplayCommand().andThen(() -> loadLatestLogTrigger.set(false)));
    RobotCommon.init();
  }

  /**
   * Static initializer to ensure the LogManager is created.
   */
  static {
    if (logManager == null) {
      new Log();
    }
  }

  /**
   * Removes non-essential log entries when in competition mode.
   * Cleans up both individual and categorized entries based on their LogLevel.
   */
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
   */
  public static void clearEntries() {
    if (logManager != null) {
      logManager.individualLogEntries.clear();
    }
  }

  /**
   * Logs a message to the console and creates an alert.
   * Manages the console limit by removing old alerts.
   * 
   * @param message   The message to log
   * @param alertType The severity of the alert
   * @return The created ConsoleAlert
   */
  public static ConsoleAlert log(Object message, Level level) {
    String text = String.valueOf(message);
    DataLogManager.log(text);

    // WPILib allows only one live Alert per (group, text) - reuse it instead of allocating a duplicate
    for (int i = 0; i < activeConsole.size(); i++) {
      ConsoleAlert existing = activeConsole.get(i);
      if (existing.getText().equals(text)) {
        existing.setText(text);
        activeConsole.remove(i);
        activeConsole.add(existing);
        return existing;
      }
    }

    ConsoleAlert alert = new ConsoleAlert(text, level);
    alert.set(true);
    if (activeConsole.size() > ConsoleConstants.CONSOLE_LIMIT) {
      activeConsole.get(0).close();
      activeConsole.remove(0);
    }
    activeConsole.add(alert);
    return alert;
  }

  /**
   * Logs an info message to the console.
   * 
   * @param message The message to log
   * @return The created ConsoleAlert
   */
  public static ConsoleAlert log(Object message) {
    return log(message, Level.LOW);
  }

  /**
   * Periodic method called by the scheduler.
   * Refreshes data, updates console alerts (handling expiration), and updates all
   * log entries.
   */
  @Override
  public void periodic() {
    Data.refreshAll();
    for (int i = activeConsole.size() - 1; i >= 0; i--) {
      ConsoleAlert alert = activeConsole.get(i);
      if (alert.isTimerOver()) {
        alert.set(false);
        alert.close();
        activeConsole.remove(i);
      }
    }
    
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

  /**
   * Registers an object to be logged to Telemetry every loop.
   * 
   * <p>This is the 2027 replacement for {@code SmartDashboard.putData(key, sendable)} for
   * read-only values. Telemetry has no automatic refresh, so the Log subsystem calls
   * {@link Telemetry#log(String, Object)} for every registered object in {@link #periodic()}.</p>
   * 
   * @param path     The telemetry path (for example {@code "motors/intake"})
   * @param loggable The object to log
   */
  public static void publishTelemetry(String path, TelemetryLoggable loggable) {
    logManager.telemetryObjects.put(path, loggable);
  }

  /**
   * Stops logging an object that was registered with {@link #publishTelemetry}.
   * 
   * @param path The telemetry path the object was registered with
   */
  public static void removeTelemetry(String path) {
    logManager.telemetryObjects.remove(path);
  }
    public static void putData(String key, ComplexTunable tunable) {
    Tunables.publish(key, tunable);
  }

  /**
   * Logs a double and makes it editable from the dashboard.
   * <p>
   * The 2027 replacement for the 2026 {@code builder.addDoubleProperty(name, getter, setter)}
   * in {@code initSendable}. The getter is logged like {@link #putData(String, Supplier)}
   * and the getter and setter are published as a Tunable at the same path.
   * </p>
   *
   * @param name   The log entry name and Tunable path
   * @param getter Reads the current value
   * @param setter Applies a value edited on the dashboard
   * @return The created LogEntry
   */
  public static LogEntry<Double> putData(String name, DoubleSupplier getter, DoubleConsumer setter) {
    Objects.requireNonNull(getter, "getter");
    Objects.requireNonNull(setter, "setter");
    Tunables.publishDouble(name, getter, setter);
    return putData(name, (Supplier<Double>) getter::getAsDouble);
  }

  /**
   * Logs a boolean and makes it editable from the dashboard.
   * <p>
   * The 2027 replacement for the 2026 {@code builder.addBooleanProperty(name, getter, setter)}
   * in {@code initSendable}. The getter is logged like {@link #putData(String, Supplier)}
   * and the getter and setter are published as a Tunable at the same path.
   * </p>
   *
   * @param name   The log entry name and Tunable path
   * @param getter Reads the current value
   * @param setter Applies a value edited on the dashboard
   * @return The created LogEntry
   */
  public static LogEntry<Boolean> putData(String name, BooleanSupplier getter, BooleanConsumer setter) {
    Objects.requireNonNull(getter, "getter");
    Objects.requireNonNull(setter, "setter");
    Tunables.publishBoolean(name, getter, setter);
    return putData(name, (Supplier<Boolean>) getter::getAsBoolean);
  }

  /**
   * Publishes a group of editable values without writing a {@link ComplexTunable} class.
   * <p>
   * The 2027 replacement for overriding {@code initSendable} in 2026: the values are added
   * with a {@link TunableBuilder} instead of a {@code SendableBuilder}. Every double and
   * boolean property is also logged at {@code name/<property>}.
   * </p>
   *
   * <pre>{@code
   * Log.putData("motors/arm/Motion Magic", "Motion Magic Config", builder -> builder
   *     .addDoubleProperty("Vel", () -> config.maxVelocity, v -> config.maxVelocity = v)
   *     .addDoubleProperty("Acc", () -> config.maxAcceleration, v -> config.maxAcceleration = v)
   *     .addCommand("Update", applyCommand));
   * }</pre>
   *
   * @param name The Tunable path of the group
   * @param type The widget type shown on the dashboard
   * @param init Adds the group's values
   */
  public static void putData(String name, String type, Consumer<TunableBuilder> init) {
    Objects.requireNonNull(init, "init");
    putData(name, new ComplexTunable() {
      /** The getters are logged only once, even if the group is published to more than one backend */
      private boolean logged = false;

      @Override
      public String getTunableType() {
        return type;
      }

      @Override
      public void publishTunable(TunableTable table) {
        init.accept(new TunableBuilder(table, name, !logged));
        logged = true;
      }
    });
  }

  /**
   * Starts building a new log entry from Phoenix6 StatusSignals.
   * 
   * @param <T>           The type of data
   * @param name          The name of the log entry
   * @param statusSignals The signals to log
   * @return A new LogEntryBuilder
   */
  @SuppressWarnings("unchecked")
  public static <T> LogEntry<T> putData(String name, StatusSignal<T> statusSignal, boolean isRio) {
    return putData(name, new StatusSignal[] {statusSignal}, LogLevel.LOG_AND_NT, "", true, isRio);
  } 

  /**
   * Starts building a new log entry from Suppliers.
   * 
   * @param <T>       The type of data
   * @param name      The name of the log entry
   * @param suppliers The suppliers to log
   * @return A new LogEntryBuilder
   */
  @SuppressWarnings("unchecked")
  public static <T> LogEntry<T> putData(String name, Supplier<T> supplier) {
    return putData(name, new Supplier[] {supplier}, LogLevel.LOG_AND_NT, "", true);
  }

  /**
   * Starts building a new log entry from data.
   * 
   * @param <T>       The type of data
   * @param name      The name of the log entry
   * @param data The data to log
   * @return A new LogEntryBuilder
   */
  @SuppressWarnings("unchecked")
  public static <T> LogEntry<T> putData(String name, Data<T> data) {
    return putData(name, new Data[] {data}, LogLevel.LOG_AND_NT, "", true);
  }

  @SuppressWarnings("unchecked")
  public static <T> LogEntry<T> putData(String name, StatusSignal<T>[] statusSignals, LogLevel logLevel, String metaData, boolean isSeparated, boolean isRio) {
    Data<T>[] data;
    data = (Data<T>[]) new Data[statusSignals.length];
    for (int i = 0; i < statusSignals.length; i++) {
        data[i] = new Data<>(statusSignals[i], isRio);
    }
    return putData(name, data, logLevel, metaData, isSeparated);
  } 

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
   */
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