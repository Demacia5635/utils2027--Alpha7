package first.demacia.utils.log;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.wpilib.command2.Command;
import org.wpilib.command2.CommandScheduler;
import org.wpilib.command2.Commands;
import org.wpilib.datalog.DataLogWriter;
import org.wpilib.hardware.hal.HAL;
import org.wpilib.tunable.MockTunableBackend;
import org.wpilib.tunable.TunableRegistry;
import org.wpilib.tunable.TunableTable;

class TuningTest {
  private MockTunableBackend mock;
  private ByteArrayOutputStream stream;
  private DataLogWriter log;
  private List<String> warnings;
  private Tuning tuning;

  @BeforeAll
  static void initHal() {
    HAL.initialize();
  }

  @BeforeEach
  void setUp() {
    mock = new MockTunableBackend();
    TunableRegistry.registerBackend("", mock);
    TunableTable root = TunableRegistry.getTable("/");
    stream = new ByteArrayOutputStream();
    log = new DataLogWriter(stream);
    warnings = new ArrayList<>();
    tuning = new Tuning(root, log, warnings::add);
  }

  @AfterEach
  void tearDown() {
    CommandScheduler.getInstance().cancelAll();
    // Reset before closing the log, as the registry finishes its entries on reset
    TunableRegistry.reset();
    log.close();
  }

  private Map<String, Object> fileValues() {
    log.flush();
    return LogFiles.lastValues(stream.toByteArray());
  }

  @Test
  void numberEditCallsSetterAndIsRecorded() {
    double[] field = {0.0};
    tuning.number("motors/x/test Value", () -> field[0], v -> field[0] = v);

    mock.setDouble("/motors/x/test Value", 2.0);
    TunableRegistry.update();

    assertEquals(2.0, field[0]);
    assertEquals(2.0, fileValues().get("Tunables/motors/x/test Value"));
  }

  @Test
  void flagEditCallsSetterAndIsRecorded() {
    boolean[] field = {false};
    tuning.flag("motors/x/enabled", () -> field[0], v -> field[0] = v);

    mock.setBoolean("/motors/x/enabled", true);
    TunableRegistry.update();

    assertTrue(field[0]);
    assertEquals(true, fileValues().get("Tunables/motors/x/enabled"));
  }

  @Test
  void arrayEditCallsSetterAndIsRecorded() {
    double[][] field = {new double[0]};
    tuning.array("motors/x/test Values", () -> field[0], v -> field[0] = v);

    mock.setValue("/motors/x/test Values", new double[] {1, 2});
    TunableRegistry.update();

    assertArrayEquals(new double[] {1, 2}, field[0]);
    assertArrayEquals(new double[] {1, 2}, (double[]) fileValues().get("Tunables/motors/x/test Values"));
  }

  @Test
  void groupChildrenArePublishedUnderTheGroupPath() {
    double[] received = {0.0};
    tuning.group("motors/x/Motion Magic Config", "MotionMagic",
        table -> table.number("Vel", () -> received[0], v -> received[0] = v));

    mock.setDouble("/motors/x/Motion Magic Config/Vel", 4.0);
    TunableRegistry.update();

    assertEquals(4.0, received[0]);
    assertEquals(4.0, fileValues().get("Tunables/motors/x/Motion Magic Config/Vel"));
  }

  @Test
  void chooserSelectionNotifiesListener() {
    Chooser<Integer> chooser = tuning.chooser("autos/chooser");
    chooser.addDefault("A", 1);
    chooser.add("B", 2);
    List<Integer> received = new ArrayList<>();
    chooser.onChange(received::add);
    assertEquals(1, chooser.getSelected());

    mock.setValue("/autos/chooser/selected", "B");
    TunableRegistry.update();

    assertEquals(List.of(2), received);
    assertEquals(2, chooser.getSelected());
    assertEquals("B", fileValues().get("Tunables/autos/chooser"));
  }

  @Test
  void buttonRunsCommand() {
    // Commands do not schedule while the robot is disabled unless they ignore it
    Command command = Commands.idle().ignoringDisable(true);
    tuning.button("chassis/reset gyro", command);

    mock.setBoolean("/chassis/reset gyro/running", true);
    TunableRegistry.update();

    assertTrue(CommandScheduler.getInstance().isScheduled(command));
  }

  @Test
  void duplicatePathWarnsAndKeepsFirst() {
    double[] first = {0.0};
    double[] second = {0.0};
    tuning.number("motors/x/dup", () -> first[0], v -> first[0] = v);
    tuning.number("motors/x/dup", () -> second[0], v -> second[0] = v);

    assertEquals(1, warnings.size());
    mock.setDouble("/motors/x/dup", 3.0);
    TunableRegistry.update();

    assertEquals(3.0, first[0]);
    assertEquals(0.0, second[0]);
  }

  @Test
  void throwingSetterDoesNotRecordAndWarns() {
    double[] other = {0.0};
    tuning.number("motors/x/bad", () -> 0.0, v -> {
      throw new IllegalArgumentException("rejected");
    });
    tuning.number("motors/x/good", () -> other[0], v -> other[0] = v);

    mock.setDouble("/motors/x/bad", 9.0);
    mock.setDouble("/motors/x/good", 5.0);
    TunableRegistry.update();

    Map<String, Object> values = fileValues();
    assertFalse(values.containsKey("Tunables/motors/x/bad"));
    assertNull(values.get("Tunables/motors/x/bad"));
    assertEquals(1, warnings.size());
    assertEquals(5.0, other[0]);
    assertEquals(5.0, values.get("Tunables/motors/x/good"));
  }
}
