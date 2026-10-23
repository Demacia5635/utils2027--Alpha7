package first.demacia.utils.log;

/**
 * An object that writes its own values to the log.
 */
public interface Loggable {
  /**
   * Writes this object's current values.
   *
   * @param table The table to write to, already positioned at this object's path
   */
  void log(LogTable table);

  /**
   * The type name that dashboards use to pick a widget for this object.
   *
   * @return The type name such as {@code "Motor"}, or null for no type
   */
  default String logType() {
    return null;
  }
}
