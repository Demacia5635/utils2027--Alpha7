package first.demacia.utils.log;

import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * The set of telemetry paths (or path prefixes) whose values are debug-only: they are written to
 * the log file always, but are kept off the dashboard during competition.
 */
final class DebugPaths {
  private final Set<String> paths = new CopyOnWriteArraySet<>();

  /**
   * Marks a path, and everything below it, as debug.
   *
   * @param path A telemetry path such as {@code "motors/arm"}; leading and trailing slashes are optional
   */
  void add(String path) {
    paths.add(normalize(path));
  }

  /**
   * Whether a path is a registered debug path or lies below one.
   *
   * @param telemetryPath The path as WPILib passes it to backends, such as {@code "/motors/arm/Voltage"}
   * @return true if the path equals a registered path or starts with it followed by a slash
   */
  boolean contains(String telemetryPath) {
    String path = normalize(telemetryPath);
    for (String debug : paths) {
      if (path.equals(debug) || path.startsWith(debug + "/")) {
        return true;
      }
    }
    return false;
  }

  private static String normalize(String path) {
    String result = path;
    while (result.endsWith("/")) {
      result = result.substring(0, result.length() - 1);
    }
    return result.startsWith("/") ? result : "/" + result;
  }
}
