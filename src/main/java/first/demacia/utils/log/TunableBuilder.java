package first.demacia.utils.log;

import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;
import java.util.function.Supplier;

import org.wpilib.command2.Command;
import org.wpilib.tunable.TunableTable;
import org.wpilib.util.function.BooleanConsumer;

/**
 * Builds the values of a complex tunable registered with
 * {@link Log#putData(String, String, java.util.function.Consumer)}.
 * <p>
 * The 2027 replacement for the 2026 {@code SendableBuilder} used in {@code initSendable}:
 * every property is published as a dashboard-editable Tunable and its getter is also
 * logged with {@link Log#putData(String, Supplier)}.
 * </p>
 */
public final class TunableBuilder {
  private final TunableTable table;
  private final String path;
  private final boolean logValues;

  TunableBuilder(TunableTable table, String path, boolean logValues) {
    this.table = table;
    this.path = path;
    this.logValues = logValues;
  }

  /**
   * Adds an editable double, like 2026 {@code addDoubleProperty(name, getter, setter)}.
   *
   * @param name   The child name inside the group
   * @param getter Reads the current value
   * @param setter Applies a value edited on the dashboard
   */
  public TunableBuilder addDoubleProperty(String name, DoubleSupplier getter, DoubleConsumer setter) {
    Objects.requireNonNull(getter, "getter");
    Objects.requireNonNull(setter, "setter");
    table.publishDouble(name, getter, setter);
    if (logValues) {
      Log.putData(path + "/" + name, (Supplier<Double>) getter::getAsDouble);
    }
    return this;
  }

  /**
   * Adds an editable boolean, like 2026 {@code addBooleanProperty(name, getter, setter)}.
   *
   * @param name   The child name inside the group
   * @param getter Reads the current value
   * @param setter Applies a value edited on the dashboard
   */
  public TunableBuilder addBooleanProperty(String name, BooleanSupplier getter, BooleanConsumer setter) {
    Objects.requireNonNull(getter, "getter");
    Objects.requireNonNull(setter, "setter");
    table.publishBoolean(name, getter, setter);
    if (logValues) {
      Log.putData(path + "/" + name, (Supplier<Boolean>) getter::getAsBoolean);
    }
    return this;
  }

  /**
   * Adds a button that schedules the command when pressed (for example an "Update" button
   * that applies the edited values).
   *
   * @param name    The child name inside the group
   * @param command The command to run
   */
  public TunableBuilder addCommand(String name, Command command) {
    Objects.requireNonNull(command, "command");
    table.publish(name, command);
    return this;
  }
}
