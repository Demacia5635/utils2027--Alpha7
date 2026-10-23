package first.demacia.utils.log;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.BooleanSupplier;

import org.wpilib.backend.DataLogTelemetryBackend;
import org.wpilib.backend.NetworkTablesTelemetryBackend;
import org.wpilib.datalog.DataLog;
import org.wpilib.networktables.NetworkTableInstance;
import org.wpilib.telemetry.TelemetryBackend;
import org.wpilib.telemetry.TelemetryEntry;
import org.wpilib.util.protobuf.Protobuf;
import org.wpilib.util.struct.Struct;

/**
 * Telemetry backend that sends every value to the log file, and to the dashboard unless the path is
 * a debug path and the robot is in competition.
 * <p>
 * The competition check happens on every write, so switching modes takes effect on the next value.
 * </p>
 */
final class LogBackend implements TelemetryBackend {
  private final TelemetryBackend dashboard;
  private final TelemetryBackend file;
  private final DebugPaths debugPaths;
  private final BooleanSupplier isComp;
  private final ConcurrentMap<String, TelemetryEntry> entries = new ConcurrentHashMap<>();

  /**
   * @param dashboard  Backend the dashboard reads
   * @param file       Backend that writes the log file
   * @param debugPaths Paths hidden from the dashboard in competition
   * @param isComp     Whether the robot is currently in competition
   */
  LogBackend(TelemetryBackend dashboard, TelemetryBackend file, DebugPaths debugPaths,
      BooleanSupplier isComp) {
    this.dashboard = Objects.requireNonNull(dashboard, "dashboard");
    this.file = Objects.requireNonNull(file, "file");
    this.debugPaths = Objects.requireNonNull(debugPaths, "debugPaths");
    this.isComp = Objects.requireNonNull(isComp, "isComp");
  }

  /**
   * Creates the backend used on the robot: NetworkTables under {@code /Telemetry} and the DataLog
   * under {@code Telemetry}.
   *
   * @param inst       The NetworkTables instance the dashboard reads
   * @param log        The DataLog to write to
   * @param debugPaths Paths hidden from the dashboard in competition
   * @param isComp     Whether the robot is currently in competition
   * @return The new backend
   */
  static LogBackend create(NetworkTableInstance inst, DataLog log, DebugPaths debugPaths,
      BooleanSupplier isComp) {
    return new LogBackend(new NetworkTablesTelemetryBackend(inst, "/Telemetry"),
        new DataLogTelemetryBackend(log, "Telemetry"), debugPaths, isComp);
  }

  @Override
  public TelemetryEntry getEntry(String path) {
    return entries.computeIfAbsent(path, p -> new Entry(
        file.getEntry(p), dashboard.getEntry(p), debugPaths.contains(p), isComp));
  }

  @Override
  public boolean ownsBackend(TelemetryBackend backend) {
    return this == backend || dashboard.ownsBackend(backend) || file.ownsBackend(backend);
  }

  @Override
  public boolean sharesBackendWith(TelemetryBackend backend) {
    return TelemetryBackend.super.sharesBackendWith(backend)
        || dashboard.sharesBackendWith(backend) || file.sharesBackendWith(backend);
  }

  @Override
  public void removeEntry(String path) {
    entries.remove(path);
    dashboard.removeEntry(path);
    file.removeEntry(path);
  }

  @Override
  public void close() throws Exception {
    entries.clear();
    Exception exception = null;
    for (TelemetryBackend backend : new TelemetryBackend[] {dashboard, file}) {
      try {
        backend.close();
      } catch (Exception e) {
        if (exception == null) {
          exception = e;
        } else {
          exception.addSuppressed(e);
        }
      }
    }
    if (exception != null) {
      throw exception;
    }
  }

  /** Forwards each value to the file entry, and to the dashboard entry unless hidden. */
  private static final class Entry implements TelemetryEntry {
    private final TelemetryEntry file;
    private final TelemetryEntry dashboard;
    private final boolean debug;
    private final BooleanSupplier isComp;

    Entry(TelemetryEntry file, TelemetryEntry dashboard, boolean debug, BooleanSupplier isComp) {
      this.file = file;
      this.dashboard = dashboard;
      this.debug = debug;
      this.isComp = isComp;
    }

    private boolean toDashboard() {
      return !(debug && isComp.getAsBoolean());
    }

    @Override
    public boolean isDiscard() {
      return file.isDiscard() && (dashboard.isDiscard() || !toDashboard());
    }

    @Override
    public void keepDuplicates() {
      file.keepDuplicates();
      dashboard.keepDuplicates();
    }

    @Override
    public void setProperty(String key, String value) {
      file.setProperty(key, value);
      dashboard.setProperty(key, value);
    }

    @Override
    public <T> void logStruct(T value, Struct<? super T> struct, long timestamp) {
      file.logStruct(value, struct, timestamp);
      if (toDashboard()) {
        dashboard.logStruct(value, struct, timestamp);
      }
    }

    @Override
    public <T> void logProtobuf(T value, Protobuf<? super T, ?> proto, long timestamp) {
      file.logProtobuf(value, proto, timestamp);
      if (toDashboard()) {
        dashboard.logProtobuf(value, proto, timestamp);
      }
    }

    @Override
    public <T> void logStructArray(T[] value, Struct<? super T> struct, long timestamp) {
      file.logStructArray(value, struct, timestamp);
      if (toDashboard()) {
        dashboard.logStructArray(value, struct, timestamp);
      }
    }

    @Override
    public void logBoolean(boolean value, long timestamp) {
      file.logBoolean(value, timestamp);
      if (toDashboard()) {
        dashboard.logBoolean(value, timestamp);
      }
    }

    @Override
    public void logByte(byte value, long timestamp) {
      file.logByte(value, timestamp);
      if (toDashboard()) {
        dashboard.logByte(value, timestamp);
      }
    }

    @Override
    public void logShort(short value, long timestamp) {
      file.logShort(value, timestamp);
      if (toDashboard()) {
        dashboard.logShort(value, timestamp);
      }
    }

    @Override
    public void logInt(int value, long timestamp) {
      file.logInt(value, timestamp);
      if (toDashboard()) {
        dashboard.logInt(value, timestamp);
      }
    }

    @Override
    public void logLong(long value, long timestamp) {
      file.logLong(value, timestamp);
      if (toDashboard()) {
        dashboard.logLong(value, timestamp);
      }
    }

    @Override
    public void logFloat(float value, long timestamp) {
      file.logFloat(value, timestamp);
      if (toDashboard()) {
        dashboard.logFloat(value, timestamp);
      }
    }

    @Override
    public void logDouble(double value, long timestamp) {
      file.logDouble(value, timestamp);
      if (toDashboard()) {
        dashboard.logDouble(value, timestamp);
      }
    }

    @Override
    public void logString(String value, String typeString, long timestamp) {
      file.logString(value, typeString, timestamp);
      if (toDashboard()) {
        dashboard.logString(value, typeString, timestamp);
      }
    }

    @Override
    public void logBooleanArray(boolean[] value, long timestamp) {
      file.logBooleanArray(value, timestamp);
      if (toDashboard()) {
        dashboard.logBooleanArray(value, timestamp);
      }
    }

    @Override
    public void logShortArray(short[] value, long timestamp) {
      file.logShortArray(value, timestamp);
      if (toDashboard()) {
        dashboard.logShortArray(value, timestamp);
      }
    }

    @Override
    public void logIntArray(int[] value, long timestamp) {
      file.logIntArray(value, timestamp);
      if (toDashboard()) {
        dashboard.logIntArray(value, timestamp);
      }
    }

    @Override
    public void logLongArray(long[] value, long timestamp) {
      file.logLongArray(value, timestamp);
      if (toDashboard()) {
        dashboard.logLongArray(value, timestamp);
      }
    }

    @Override
    public void logFloatArray(float[] value, long timestamp) {
      file.logFloatArray(value, timestamp);
      if (toDashboard()) {
        dashboard.logFloatArray(value, timestamp);
      }
    }

    @Override
    public void logDoubleArray(double[] value, long timestamp) {
      file.logDoubleArray(value, timestamp);
      if (toDashboard()) {
        dashboard.logDoubleArray(value, timestamp);
      }
    }

    @Override
    public void logStringArray(String[] value, long timestamp) {
      file.logStringArray(value, timestamp);
      if (toDashboard()) {
        dashboard.logStringArray(value, timestamp);
      }
    }

    @Override
    public void logRaw(byte[] value, String typeString, long timestamp) {
      file.logRaw(value, typeString, timestamp);
      if (toDashboard()) {
        dashboard.logRaw(value, typeString, timestamp);
      }
    }
  }
}
