package first.demacia.utils.log;

import org.wpilib.backend.DataLogTelemetryBackend;
import org.wpilib.backend.NetworkTablesTelemetryBackend;
import org.wpilib.datalog.DataLog;
import org.wpilib.networktables.NetworkTableInstance;
import org.wpilib.telemetry.MultiTelemetryBackend;
import org.wpilib.telemetry.TelemetryRegistry;

/**
 * Makes every {@code Telemetry.log} value reach the log file as well as the dashboard.
 * <p>
 * RobotBase registers a NetworkTables-only Telemetry backend, and Log turns off recording
 * NetworkTables to the file, so values logged with Telemetry (every {@code logTo} of motors,
 * sensors and mechanisms) were shown on the dashboard but never saved.
 * </p>
 */
public class TelemetryFileLogging {
  /**
   * Replaces the default Telemetry backend with one that writes to both NetworkTables, under
   * {@code /Telemetry} as before, and the DataLog, under {@code Telemetry}.
   *
   * @param inst The NetworkTables instance the dashboard reads
   * @param log  The DataLog to write to
   */
  public static void install(NetworkTableInstance inst, DataLog log) {
    TelemetryRegistry.registerBackend("", new MultiTelemetryBackend(
        new NetworkTablesTelemetryBackend(inst, "/Telemetry"),
        new DataLogTelemetryBackend(log, "Telemetry")));
  }
}
