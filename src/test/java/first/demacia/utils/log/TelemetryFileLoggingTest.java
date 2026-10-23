package first.demacia.utils.log;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.wpilib.datalog.DataLogReader;
import org.wpilib.datalog.DataLogRecord;
import org.wpilib.datalog.DataLogWriter;
import org.wpilib.networktables.DoubleSubscriber;
import org.wpilib.networktables.NetworkTableInstance;
import org.wpilib.telemetry.Telemetry;
import org.wpilib.telemetry.TelemetryRegistry;

class TelemetryFileLoggingTest {
  private NetworkTableInstance inst;
  private ByteArrayOutputStream file;
  private DataLogWriter log;

  @BeforeEach
  void setUp() {
    inst = NetworkTableInstance.create();
    file = new ByteArrayOutputStream();
    log = new DataLogWriter(file);
  }

  @AfterEach
  void tearDown() {
    // The registry finishes its DataLog entries on reset, so it must run before the log closes
    TelemetryRegistry.reset();
    log.close();
    inst.close();
  }

  @Test
  void telemetryValueIsWrittenToTheLogFile() {
    TelemetryFileLogging.install(inst, log);

    Telemetry.log("motors/arm/Voltage", 3.5);
    // An in-memory DataLogWriter only copies its buffer to the stream on flush, not on close
    log.flush();

    assertEquals(3.5, readLastDoubles(file.toByteArray()).get("Telemetry/motors/arm/Voltage"));
  }

  @Test
  void telemetryValueStillReachesNetworkTables() {
    try (DoubleSubscriber dashboard = inst.getDoubleTopic("/Telemetry/motors/arm/Voltage").subscribe(0)) {
      TelemetryFileLogging.install(inst, log);

      Telemetry.log("motors/arm/Voltage", 3.5);

      assertEquals(3.5, dashboard.get());
    }
  }

  /** Reads a DataLog and returns the last value of every double entry, keyed by entry name. */
  private static Map<String, Double> readLastDoubles(byte[] file) {
    Map<Integer, String> names = new HashMap<>();
    Map<String, Double> values = new HashMap<>();
    // forEach, not a for-each loop: DataLogIterator.hasNext() needs 16 bytes left, so it drops a
    // last record shorter than that (a double with a 4-byte timestamp is 15 bytes)
    new DataLogReader(ByteBuffer.wrap(file)).forEach(record -> {
      if (record.isStart()) {
        DataLogRecord.StartRecordData start = record.getStartData();
        if (start.type.equals("double")) {
          names.put(start.entry, start.name);
        }
      } else if (!record.isControl() && names.containsKey(record.getEntry())) {
        values.put(names.get(record.getEntry()), record.getDouble());
      }
    });
    return values;
  }
}
