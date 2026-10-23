package first.demacia.utils.log;

import org.wpilib.telemetry.TelemetryTable;

/**
 * A node in the log tree that values are written to. Hides WPILib's telemetry types from the
 * rest of the robot code.
 */
public final class LogTable {
  private final TelemetryTable table;

  LogTable(TelemetryTable table) {
    this.table = table;
  }

  /**
   * Gets a child table.
   *
   * @param name The child name; may contain slashes
   * @return The child table
   */
  public LogTable table(String name) {
    return new LogTable(table.getTable(name));
  }

  /**
   * Sets the WPILib type of this table.
   *
   * @param type The type name
   * @return false if the table already has a different type
   */
  boolean setType(String type) {
    return table.setType(type);
  }

  /**
   * Writes a double.
   *
   * @param name The value name
   * @param value The value
   */
  public void put(String name, double value) {
    table.log(name, value);
  }

  /**
   * Writes a boolean.
   *
   * @param name The value name
   * @param value The value
   */
  public void put(String name, boolean value) {
    table.log(name, value);
  }

  /**
   * Writes a long.
   *
   * @param name The value name
   * @param value The value
   */
  public void put(String name, long value) {
    table.log(name, value);
  }

  /**
   * Writes a string.
   *
   * @param name The value name
   * @param value The value
   */
  public void put(String name, String value) {
    table.log(name, value);
  }

  /**
   * Writes a double array.
   *
   * @param name The value name
   * @param value The value
   */
  public void put(String name, double[] value) {
    table.log(name, value);
  }

  /**
   * Writes a boolean array.
   *
   * @param name The value name
   * @param value The value
   */
  public void put(String name, boolean[] value) {
    table.log(name, value);
  }

  /**
   * Writes a string array.
   *
   * @param name The value name
   * @param value The value
   */
  public void put(String name, String[] value) {
    table.log(name, value);
  }

  /**
   * Writes any value WPILib knows how to log (structs, enums, collections, ...). A null value is
   * ignored.
   *
   * @param <T> The value type
   * @param name The value name
   * @param value The value, or null to skip
   */
  public <T> void put(String name, T value) {
    if (value == null) {
      return;
    }
    // TelemetryTable's generic log has no array branch and would log value.toString()
    if (value instanceof double[] array) {
      table.log(name, array);
    } else if (value instanceof float[] array) {
      table.log(name, array);
    } else if (value instanceof long[] array) {
      table.log(name, array);
    } else if (value instanceof int[] array) {
      table.log(name, array);
    } else if (value instanceof boolean[] array) {
      table.log(name, array);
    } else if (value instanceof String[] array) {
      table.log(name, array);
    } else {
      table.log(name, value);
    }
  }

  /**
   * Records every write to a value even when it did not change.
   *
   * @param name The value name
   */
  public void recordEveryLoop(String name) {
    table.keepDuplicates(name);
  }
}
