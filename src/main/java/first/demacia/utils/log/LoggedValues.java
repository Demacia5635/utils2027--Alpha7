package first.demacia.utils.log;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * The values and objects that are written to the log on every robot loop, in registration order.
 */
final class LoggedValues {
  private final LogTable root;
  private final DebugPaths debugPaths;
  private final Consumer<String> warn;
  private final Map<String, Consumer<LogTable>> entries = new LinkedHashMap<>();
  private final Set<String> warned = new HashSet<>();

  LoggedValues(LogTable root, DebugPaths debugPaths, Consumer<String> warn) {
    this.root = root;
    this.debugPaths = debugPaths;
    this.warn = warn;
  }

  /**
   * Registers a value that is read and written on every update.
   *
   * @param path The log path
   * @param supplier Provides the value; a null value is skipped
   * @param level DEBUG keeps the path off the dashboard during competition
   */
  void add(String path, Supplier<?> supplier, Log.Dashboard level) {
    register(path, level, table -> table.put(path, supplier.get()));
  }

  /**
   * Registers an object that writes its own values on every update.
   *
   * @param path The log path of the object's table
   * @param loggable The object
   * @param level DEBUG keeps the path off the dashboard during competition
   */
  void add(String path, Loggable loggable, Log.Dashboard level) {
    register(path, level, table -> {
      LogTable objectTable = table.table(path);
      String type = loggable.logType();
      if (type != null) {
        objectTable.setType(type);
      }
      loggable.log(objectTable);
    });
  }

  /**
   * Stops writing a path.
   *
   * @param path The log path it was registered with
   */
  void remove(String path) {
    entries.remove(path);
    warned.remove(path);
  }

  /** Writes every registered value; a failing one is warned about once and does not stop the rest. */
  void update() {
    // Copy so that a registration made by a loggable does not break the iteration
    for (Map.Entry<String, Consumer<LogTable>> entry : new LinkedHashMap<>(entries).entrySet()) {
      try {
        entry.getValue().accept(root);
      } catch (RuntimeException e) {
        if (warned.add(entry.getKey())) {
          warn.accept("Failed to log " + entry.getKey() + ": " + e);
        }
      }
    }
  }

  private void register(String path, Log.Dashboard level, Consumer<LogTable> writer) {
    if (level == Log.Dashboard.DEBUG) {
      debugPaths.add(path);
    }
    if (entries.put(path, writer) != null) {
      warn.accept("Log path " + path + " was registered twice; the later registration replaces the earlier one");
    }
    warned.remove(path);
  }
}
