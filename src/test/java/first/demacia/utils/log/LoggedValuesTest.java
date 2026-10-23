package first.demacia.utils.log;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.wpilib.telemetry.MockTelemetryBackend;
import org.wpilib.telemetry.Telemetry;
import org.wpilib.telemetry.TelemetryRegistry;

class LoggedValuesTest {
  private MockTelemetryBackend backend;
  private DebugPaths debugPaths;
  private List<String> warnings;
  private LoggedValues values;

  @BeforeEach
  void setUp() {
    backend = new MockTelemetryBackend();
    TelemetryRegistry.registerBackend("", backend);
    debugPaths = new DebugPaths();
    warnings = new ArrayList<>();
    values = new LoggedValues(new LogTable(Telemetry.getTable()), debugPaths, warnings::add);
  }

  @AfterEach
  void tearDown() {
    TelemetryRegistry.reset();
  }

  private static Loggable voltage(double volts, String type) {
    return new Loggable() {
      @Override
      public void log(LogTable table) {
        table.put("Voltage", volts);
      }

      @Override
      public String logType() {
        return type;
      }
    };
  }

  @Test
  void supplierIsWrittenOnEveryUpdate() {
    double[] angle = {1.0};
    values.add("arm/angle", () -> angle[0], Log.Dashboard.ALWAYS);

    values.update();
    assertEquals(1.0, backend.getLastValue("/arm/angle", Double.class));
    angle[0] = 2.0;
    values.update();

    assertEquals(2.0, backend.getLastValue("/arm/angle", Double.class));
  }

  @Test
  void loggableWritesUnderItsPath() {
    values.add("motors/arm", voltage(3.5, null), Log.Dashboard.ALWAYS);

    values.update();

    assertEquals(3.5, backend.getLastValue("/motors/arm/Voltage", Double.class));
  }

  @Test
  void loggableTypeIsWritten() {
    values.add("motors/arm", voltage(3.5, "Motor"), Log.Dashboard.ALWAYS);

    values.update();

    assertEquals("Motor",
        backend.getLastValue("/motors/arm/.type", MockTelemetryBackend.LogStringValue.class).value());
  }

  @Test
  void reRegisteringReplacesAndWarns() {
    values.add("arm/angle", () -> 1.0, Log.Dashboard.ALWAYS);
    values.add("arm/angle", () -> 2.0, Log.Dashboard.ALWAYS);

    values.update();

    assertEquals(2.0, backend.getLastValue("/arm/angle", Double.class));
    assertEquals(1, warnings.size());
    assertTrue(warnings.get(0).contains("arm/angle"));
  }

  @Test
  void removedPathIsNoLongerWritten() {
    double[] angle = {1.0};
    values.add("arm/angle", () -> angle[0], Log.Dashboard.ALWAYS);
    values.update();

    values.remove("arm/angle");
    angle[0] = 2.0;
    backend.clear();
    values.update();

    assertNull(backend.getLastAction("/arm/angle"));
    assertTrue(backend.getActions().isEmpty());
  }

  @Test
  void throwingSupplierDoesNotStopOthers() {
    Supplier<Double> bad = () -> {
      throw new IllegalStateException("boom");
    };
    values.add("bad", bad, Log.Dashboard.ALWAYS);
    values.add("good", () -> 5.0, Log.Dashboard.ALWAYS);

    values.update();
    values.update();

    assertEquals(5.0, backend.getLastValue("/good", Double.class));
    assertEquals(1, warnings.size());
  }

  @Test
  void nullValueIsSkippedWithoutStoppingOthers() {
    values.add("missing", () -> null, Log.Dashboard.ALWAYS);
    values.add("good", () -> 5.0, Log.Dashboard.ALWAYS);

    values.update();

    assertNull(backend.getLastAction("/missing"));
    assertEquals(5.0, backend.getLastValue("/good", Double.class));
    assertTrue(warnings.isEmpty());
  }

  @Test
  void debugRegistrationAddsDebugPath() {
    assertFalse(debugPaths.contains("/arm/angle"));

    values.add("arm/angle", () -> 1.0, Log.Dashboard.DEBUG);

    assertTrue(debugPaths.contains("/arm/angle"));
  }

  @Test
  void recordEveryLoopKeepsDuplicates() {
    Loggable loggable = table -> {
      table.recordEveryLoop("Voltage");
      table.put("Voltage", 3.5);
    };
    values.add("motors/arm", loggable, Log.Dashboard.ALWAYS);

    values.update();
    values.update();

    assertTrue(backend.getActions().stream().anyMatch(action ->
        action.path().equals("/motors/arm/Voltage")
            && action.value() instanceof MockTelemetryBackend.KeepDuplicateValue));
  }
}
