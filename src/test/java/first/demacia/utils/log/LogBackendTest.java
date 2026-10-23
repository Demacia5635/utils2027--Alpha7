package first.demacia.utils.log;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.wpilib.datalog.DataLogWriter;
import org.wpilib.networktables.DoubleSubscriber;
import org.wpilib.networktables.NetworkTableInstance;
import org.wpilib.telemetry.MockTelemetryBackend;
import org.wpilib.telemetry.Telemetry;
import org.wpilib.telemetry.TelemetryRegistry;

class LogBackendTest {
  private MockTelemetryBackend dashboard;
  private MockTelemetryBackend file;
  private DebugPaths debugPaths;
  private boolean isComp;

  @BeforeEach
  void setUp() {
    dashboard = new MockTelemetryBackend();
    file = new MockTelemetryBackend();
    debugPaths = new DebugPaths();
    isComp = false;
  }

  @AfterEach
  void tearDown() {
    TelemetryRegistry.reset();
  }

  private void registerMocks() {
    TelemetryRegistry.registerBackend("", new LogBackend(dashboard, file, debugPaths, () -> isComp));
  }

  @Test
  void valueReachesDashboardAndFile() {
    registerMocks();

    Telemetry.log("motors/arm/Voltage", 3.5);

    assertEquals(3.5, dashboard.getLastValue("/motors/arm/Voltage", Double.class));
    assertEquals(3.5, file.getLastValue("/motors/arm/Voltage", Double.class));
  }

  @Test
  void debugValueSkipsDashboardInCompetition() {
    debugPaths.add("motors/arm");
    isComp = true;
    registerMocks();

    Telemetry.log("motors/arm/Voltage", 3.5);

    assertNull(dashboard.getLastAction("/motors/arm/Voltage"));
    assertEquals(3.5, file.getLastValue("/motors/arm/Voltage", Double.class));
  }

  @Test
  void debugValueReachesDashboardOutsideCompetition() {
    debugPaths.add("motors/arm");
    isComp = false;
    registerMocks();

    Telemetry.log("motors/arm/Voltage", 3.5);

    assertEquals(3.5, dashboard.getLastValue("/motors/arm/Voltage", Double.class));
    assertEquals(3.5, file.getLastValue("/motors/arm/Voltage", Double.class));
  }

  @Test
  void switchingCompetitionAppliesOnNextWrite() {
    debugPaths.add("motors/arm");
    registerMocks();

    isComp = false;
    Telemetry.log("motors/arm/Voltage", 1.0);
    isComp = true;
    Telemetry.log("motors/arm/Voltage", 2.0);

    assertEquals(1.0, dashboard.getLastValue("/motors/arm/Voltage", Double.class));
    assertEquals(2.0, file.getLastValue("/motors/arm/Voltage", Double.class));
  }

  @Test
  void debugPrefixDoesNotMatchSiblingWithSamePrefix() {
    debugPaths.add("motors/arm");
    isComp = true;
    registerMocks();

    Telemetry.log("motors/armExtension/Voltage", 3.5);

    assertEquals(3.5, dashboard.getLastValue("/motors/armExtension/Voltage", Double.class));
  }

  @Test
  void debugPathsNormalizeSlashes() {
    debugPaths.add("/motors/arm/");

    assertTrue(debugPaths.contains("/motors/arm/Voltage"));
    assertTrue(debugPaths.contains("/motors/arm"));
    assertFalse(debugPaths.contains("/motors/armExtension/Voltage"));
  }

  @Test
  void createWritesTelemetryToTheLogFile() {
    ByteArrayOutputStream stream = new ByteArrayOutputStream();
    try (NetworkTableInstance inst = NetworkTableInstance.create();
        DataLogWriter log = new DataLogWriter(stream)) {
      TelemetryRegistry.registerBackend("", LogBackend.create(inst, log, debugPaths, () -> isComp));

      Telemetry.log("motors/arm/Voltage", 3.5);
      // An in-memory DataLogWriter only copies its buffer to the stream on flush, not on close
      log.flush();

      assertEquals(3.5, LogFiles.lastValues(stream.toByteArray()).get("Telemetry/motors/arm/Voltage"));
      // The registry finishes its DataLog entries on reset, so it must run before the log closes
      TelemetryRegistry.reset();
    }
  }

  @Test
  void createKeepsDashboardPath() {
    try (NetworkTableInstance inst = NetworkTableInstance.create();
        DataLogWriter log = new DataLogWriter(new ByteArrayOutputStream());
        DoubleSubscriber subscriber = inst.getDoubleTopic("/Telemetry/motors/arm/Voltage").subscribe(0)) {
      TelemetryRegistry.registerBackend("", LogBackend.create(inst, log, debugPaths, () -> isComp));

      Telemetry.log("motors/arm/Voltage", 3.5);

      assertEquals(3.5, subscriber.get());
      TelemetryRegistry.reset();
    }
  }
}
