package first.demacia.utils.log;

import java.util.function.BooleanSupplier;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;

import org.wpilib.command2.Command;
import org.wpilib.tunable.TunableTable;
import org.wpilib.util.function.BooleanConsumer;

/** The children of a {@link TunableGroup}. Dashboard edits are recorded to the log file. */
public final class TuneTable {
  private final TunableTable table;
  private final String path;
  private final Tuning tuning;

  TuneTable(TunableTable table, String path, Tuning tuning) {
    this.table = table;
    this.path = path;
    this.tuning = tuning;
  }

  /**
   * Publishes an editable number.
   *
   * @param name The child name
   * @param getter Reads the current value
   * @param setter Applies a value edited on the dashboard
   */
  public void number(String name, DoubleSupplier getter, DoubleConsumer setter) {
    String childPath = path + "/" + name;
    if (tuning.claim(childPath)) {
      table.publishDouble(name, getter, tuning.recordingNumber(childPath, setter));
    }
  }

  /**
   * Publishes an editable flag.
   *
   * @param name The child name
   * @param getter Reads the current value
   * @param setter Applies a value edited on the dashboard
   */
  public void flag(String name, BooleanSupplier getter, BooleanConsumer setter) {
    String childPath = path + "/" + name;
    if (tuning.claim(childPath)) {
      table.publishBoolean(name, getter, tuning.recordingFlag(childPath, setter));
    }
  }

  /**
   * Publishes a button that schedules the command when pressed.
   *
   * @param name The child name
   * @param command The command to run
   */
  public void button(String name, Command command) {
    if (tuning.claim(path + "/" + name)) {
      table.publish(name, command);
    }
  }
}
