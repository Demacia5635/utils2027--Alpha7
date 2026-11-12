package first.demacia.utils.log;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.function.DoubleConsumer;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.wpilib.command2.CommandScheduler;
import org.wpilib.hardware.hal.HAL;
import org.wpilib.networktables.BooleanSubscriber;
import org.wpilib.networktables.FloatSubscriber;
import org.wpilib.networktables.NetworkTableInstance;
import org.wpilib.tunable.MockTunableBackend;
import org.wpilib.tunable.TunableRegistry;
import org.wpilib.util.function.BooleanConsumer;

class LogPutDataTunableTest {
  private static MockTunableBackend backend;

  @BeforeAll
  static void setUp() {
    HAL.initialize();
    backend = new MockTunableBackend();
    TunableRegistry.registerBackend("/", backend);
  }

  /** Simulates an edit made on the dashboard. */
  private static void editDouble(String name, double value) {
    backend.setDouble(name, value);
    TunableRegistry.update();
  }

  private static void editBoolean(String name, boolean value) {
    backend.setBoolean(name, value);
    TunableRegistry.update();
  }

  /** Runs one robot loop, which runs Log.periodic. */
  private static void runLoop() {
    CommandScheduler.getInstance().run();
  }

  private static FloatSubscriber logFloat(String name) {
    return NetworkTableInstance.getDefault().getTable("Log").getFloatTopic(name).subscribe(Float.NaN);
  }

  private static BooleanSubscriber logBoolean(String name) {
    return NetworkTableInstance.getDefault().getTable("Log").getBooleanTopic(name).subscribe(false);
  }

  // ---- double ----

  @Test
  void doubleValueIsLogged() {
    String name = "test/putData/double logged";
    double[] value = {1.5};
    try (FloatSubscriber log = logFloat(name)) {
      Log.putData(name, () -> value[0], v -> value[0] = v);
      runLoop();
      assertEquals(1.5f, log.get(), 1e-6);
    }
  }

  @Test
  void doubleTunableIsPublished() {
    String name = "test/putData/double published";
    double[] value = {2.5};
    Log.putData(name, () -> value[0], v -> value[0] = v);
    assertEquals(2.5, backend.getDouble(name), 1e-9);
  }

  @Test
  void doubleDashboardEditCallsSetter() {
    String name = "test/putData/double edit";
    double[] value = {0};
    Log.putData(name, () -> value[0], v -> value[0] = v);
    editDouble(name, 7.0);
    assertEquals(7.0, value[0], 1e-9);
  }

  @Test
  void doubleDashboardEditIsLogged() {
    String name = "test/putData/double edit logged";
    double[] value = {0};
    try (FloatSubscriber log = logFloat(name)) {
      Log.putData(name, () -> value[0], v -> value[0] = v);
      editDouble(name, 7.0);
      runLoop();
      assertEquals(7.0f, log.get(), 1e-6);
    }
  }

  @Test
  void duplicateNameDoesNotThrow() {
    String name = "test/putData/double duplicate";
    double[] value = {0};
    Log.putData(name, () -> value[0], v -> value[0] = v);
    assertDoesNotThrow(() -> Log.putData(name, () -> value[0], v -> value[0] = v));
  }

  @Test
  void doubleNullSetterThrows() {
    assertThrows(NullPointerException.class,
        () -> Log.putData("test/putData/double null", () -> 0.0, (DoubleConsumer) null));
  }

  // ---- boolean ----

  @Test
  void booleanValueIsLogged() {
    String name = "test/putData/boolean logged";
    boolean[] flag = {true};
    try (BooleanSubscriber log = logBoolean(name)) {
      Log.putData(name, () -> flag[0], v -> flag[0] = v);
      runLoop();
      assertTrue(log.get());
    }
  }

  @Test
  void booleanTunableIsPublished() {
    String name = "test/putData/boolean published";
    boolean[] flag = {true};
    Log.putData(name, () -> flag[0], v -> flag[0] = v);
    assertTrue(backend.getBoolean(name));
  }

  @Test
  void booleanDashboardEditCallsSetter() {
    String name = "test/putData/boolean edit";
    boolean[] flag = {false};
    Log.putData(name, () -> flag[0], v -> flag[0] = v);
    editBoolean(name, true);
    assertTrue(flag[0]);
  }

  @Test
  void booleanNullSetterThrows() {
    assertThrows(NullPointerException.class,
        () -> Log.putData("test/putData/boolean null", () -> false, (BooleanConsumer) null));
  }

  // ---- group (initSendable replacement) ----

  @Test
  void groupValuesArePublished() {
    String name = "test/putData/group published";
    double[] kP = {0.25};
    boolean[] enabled = {true};
    Log.putData(name, "PID", builder -> builder
        .addDoubleProperty("kP", () -> kP[0], v -> kP[0] = v)
        .addBooleanProperty("enabled", () -> enabled[0], v -> enabled[0] = v));

    assertEquals(0.25, backend.getDouble(name + "/kP"), 1e-9);
    assertTrue(backend.getBoolean(name + "/enabled"));
  }

  @Test
  void groupDashboardEditCallsSetters() {
    String name = "test/putData/group edit";
    double[] kP = {0};
    boolean[] enabled = {true};
    Log.putData(name, "PID", builder -> builder
        .addDoubleProperty("kP", () -> kP[0], v -> kP[0] = v)
        .addBooleanProperty("enabled", () -> enabled[0], v -> enabled[0] = v));

    editDouble(name + "/kP", 3.0);
    editBoolean(name + "/enabled", false);

    assertEquals(3.0, kP[0], 1e-9);
    assertFalse(enabled[0]);
  }

  @Test
  void groupValuesAreLogged() {
    String name = "test/putData/group logged";
    double[] kP = {0.5};
    boolean[] enabled = {true};
    try (FloatSubscriber kPLog = logFloat(name + "/kP");
        BooleanSubscriber enabledLog = logBoolean(name + "/enabled")) {
      Log.putData(name, "PID", builder -> builder
          .addDoubleProperty("kP", () -> kP[0], v -> kP[0] = v)
          .addBooleanProperty("enabled", () -> enabled[0], v -> enabled[0] = v));
      runLoop();

      assertEquals(0.5f, kPLog.get(), 1e-6);
      assertTrue(enabledLog.get());
    }
  }
}
