package first.demacia.utils.log;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.wpilib.datalog.DataLogWriter;
import org.wpilib.networktables.NetworkTableInstance;
import org.wpilib.telemetry.Telemetry;
import org.wpilib.telemetry.TelemetryRegistry;

class LogReaderTest {
  @TempDir Path dir;

  private NetworkTableInstance inst;
  private DataLogWriter writer;
  private LoggedValues values;
  private File file;

  @BeforeEach
  void setUp() throws Exception {
    file = dir.resolve("test.wpilog").toFile();
    inst = NetworkTableInstance.create();
    writer = new DataLogWriter(file.toString());
    TelemetryRegistry.registerBackend("",
        LogBackend.create(inst, writer, new DebugPaths(), () -> false));
    values = new LoggedValues(new LogTable(Telemetry.getTable()), new DebugPaths(), s -> {});
  }

  @AfterEach
  void tearDown() {
    TelemetryRegistry.reset();
    writer.close();
    inst.close();
  }

  private static Loggable motor() {
    return table -> {
      for (String name : new String[] {"Position", "Velocity", "Acceleration", "Voltage"}) {
        table.recordEveryLoop(name);
      }
      table.put("Position", 1.0);
      table.put("Velocity", 2.0);
      table.put("Acceleration", 3.0);
      table.put("Voltage", 6.0);
    };
  }

  private Map<String, List<LogReader.Entry>> readAfterFiveUpdates(String path) {
    values.add(path, motor(), Log.Dashboard.ALWAYS);
    for (int i = 0; i < 5; i++) {
      values.update();
    }
    writer.flush();
    TelemetryRegistry.reset();
    writer.close();
    return LogReader.getGroups(file, info -> info.name().startsWith("Telemetry/"));
  }

  @Test
  void motorGroupHasFourEqualLengthSeries() {
    Map<String, List<LogReader.Entry>> groups = readAfterFiveUpdates("motors/arm");

    List<LogReader.Entry> arm = groups.get("Telemetry/motors/arm");
    assertNotNull(arm);
    assertEquals(4, arm.size());
    for (LogReader.Entry entry : arm) {
      assertEquals(5, entry.data.size(), entry.name);
      if (entry.name.equals("Voltage")) {
        for (LogReader.EntryPoint point : entry.data) {
          assertEquals(6.0, point.value);
        }
      }
    }
  }

  @Test
  void replayEntriesAreSkipped() {
    values.add("motors/arm", motor(), Log.Dashboard.ALWAYS);
    values.add("replay/motors/arm", motor(), Log.Dashboard.ALWAYS);
    for (int i = 0; i < 5; i++) {
      values.update();
    }
    writer.flush();
    TelemetryRegistry.reset();
    writer.close();

    Map<String, List<LogReader.Entry>> groups =
        LogReader.getGroups(file, info -> info.name().startsWith("Telemetry/"));

    assertTrue(groups.containsKey("Telemetry/motors/arm"));
    assertFalse(groups.containsKey("Telemetry/replay/motors/arm"));
    assertEquals(1, groups.size());
  }

  @Test
  void arrayEntryIsReadBackAsArray() {
    values.add("chassis", table -> table.put("Pose", new double[] {1.0, 2.5, -3.0}),
        Log.Dashboard.ALWAYS);
    values.update();
    writer.flush();
    TelemetryRegistry.reset();
    writer.close();

    List<LogReader.Entry> chassis =
        LogReader.getGroups(file, info -> true).get("Telemetry/chassis");

    assertNotNull(chassis);
    Object value = chassis.get(0).data.get(0).value;
    assertTrue(value instanceof double[]);
    assertArrayEquals(new double[] {1.0, 2.5, -3.0}, (double[]) value);
  }
}
