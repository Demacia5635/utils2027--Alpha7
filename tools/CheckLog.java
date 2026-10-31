import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.wpilib.datalog.DataLogReader;
import org.wpilib.datalog.DataLogRecord;

/**
 * Checks a .wpilog written by the robot with LogDemo running.
 * Usage: tools/check-log.ps1 [path-to.wpilog]   (default: the newest file in logs/)
 */
public class CheckLog {
  record Info(String name, String type, List<Object> values) {}

  static int failures = 0;

  public static void main(String[] args) throws IOException {
    File file = args.length > 0 ? new File(args[0]) : newest(new File("logs"));
    if (file == null) {
      System.out.println("No .wpilog found in logs/ - run the robot in simulation first.");
      System.exit(2);
    }
    System.out.println("Checking " + file + "\n");
    Map<String, Info> entries = read(file);
    List<String> messages = strings(entries.get("messages"));

    System.out.println("== Automatic checks (must all PASS) ==");
    Info loops = entries.get("Telemetry/demo/loops");
    check("putData value is written every loop", loops != null && loops.values.size() > 50,
        loops == null ? "missing" : loops.values.size() + " records");
    check("DEBUG value is in the file", entries.containsKey("Telemetry/demo/debug sine"), "");
    check("Loggable object writes its values", entries.containsKey("Telemetry/demo/object/speed"), "");
    check("Loggable type is written", strings(entries.get("Telemetry/demo/object/.type")).contains("LogDemo"), "");
    check("double[] is logged as an array, not text", typeOf(entries, "Telemetry/demo/array").equals("double[]"),
        typeOf(entries, "Telemetry/demo/array"));
    check("Pose2d[] is logged as a struct array, not text",
        typeOf(entries, "Telemetry/demo/poses").startsWith("struct:Pose2d"), typeOf(entries, "Telemetry/demo/poses"));
    long brokenWarnings = messages.stream().filter(m -> m.contains("Failed to log demo/broken")).count();
    check("a throwing supplier is warned about exactly once", brokenWarnings == 1, brokenWarnings + " warnings");
    check("other values keep working after the broken one", loops != null && loops.values.size() > 50, "");
    check("Log.log message reaches the file", messages.stream().anyMatch(m -> m.contains("demo: log demo is running")), "");
    check("one-time alert is written to the file", messages.stream().anyMatch(m -> m.contains("demo: startup alert")), "");
    List<String> groups = entries.keySet().stream()
        .filter(n -> n.startsWith("Float/") || n.startsWith("Boolean/") || n.startsWith("String/")).toList();
    check("no old grouped arrays (Float/, Boolean/, String/)", groups.isEmpty(), groups.toString());
    checkMotors(entries);

    System.out.println("\n== Dashboard actions (INFO until you do the step in Elastic / the Sim GUI) ==");
    info("Tunables/demo/speed", entries, "set demo/speed in the dashboard");
    info("Tunables/demo/enabled", entries, "toggle demo/enabled");
    info("Tunables/demo/PID/kP", entries, "change demo/PID -> kP");
    info("Tunables/demo/mode", entries, "pick another option in demo/mode");
    infoMessage(messages, "hello from the demo button", "press demo/say hello");
    infoMessage(messages, "demo kP is", "press demo/PID -> print kP");
    infoMessage(messages, "[ALERT - WARNING] demo: speed is above", "set demo/speed above 5");

    System.out.println(failures == 0 ? "\nALL AUTOMATIC CHECKS PASSED" : "\n" + failures + " CHECK(S) FAILED");
    System.exit(failures == 0 ? 0 : 1);
  }

  static void checkMotors(Map<String, Info> entries) {
    Map<String, Map<String, Integer>> motors = new TreeMap<>();
    for (Info info : entries.values()) {
      if (!info.name.startsWith("Telemetry/motors/")) continue;
      String rest = info.name.substring("Telemetry/motors/".length());
      int slash = rest.lastIndexOf('/');
      if (slash < 0) continue;
      motors.computeIfAbsent(rest.substring(0, slash), k -> new HashMap<>())
          .put(rest.substring(slash + 1), info.values.size());
    }
    if (motors.isEmpty()) {
      check("motors are logged under Telemetry/motors/", false, "no motor entries");
      return;
    }
    for (var motor : motors.entrySet()) {
      Map<String, Integer> c = motor.getValue();
      List<Integer> counts = Arrays.asList(c.get("Position"), c.get("Velocity"), c.get("Acceleration"), c.get("Voltage"));
      boolean equal = !counts.contains(null) && counts.stream().distinct().count() == 1;
      check("SysId series have equal length: " + motor.getKey(), equal, "Pos/Vel/Acc/Volt = " + counts);
    }
  }

  static void check(String what, boolean ok, String detail) {
    if (!ok) failures++;
    System.out.printf("  [%s] %s%s%n", ok ? "PASS" : "FAIL", what, detail.isEmpty() ? "" : "  (" + detail + ")");
  }

  static void info(String entry, Map<String, Info> entries, String step) {
    Info info = entries.get(entry);
    if (info == null) {
      System.out.printf("  [INFO] %s not in the file yet -> %s%n", entry, step);
    } else {
      System.out.printf("  [PASS] %s recorded: %s%n", entry, info.values);
    }
  }

  static void infoMessage(List<String> messages, String text, String step) {
    boolean found = messages.stream().anyMatch(m -> m.contains(text));
    System.out.printf(found ? "  [PASS] message \"%s\" found%n" : "  [INFO] message \"%s\" not found -> %s%n", text, step);
  }

  static String typeOf(Map<String, Info> entries, String name) {
    Info info = entries.get(name);
    return info == null ? "missing" : info.type;
  }

  static List<String> strings(Info info) {
    List<String> out = new ArrayList<>();
    if (info != null) info.values.forEach(v -> out.add(String.valueOf(v)));
    return out;
  }

  static Map<String, Info> read(File file) throws IOException {
    Map<Integer, Info> byId = new HashMap<>();
    Map<String, Info> byName = new TreeMap<>();
    // forEach, not a for-each loop: WPILib's iterator drops a short last record
    new DataLogReader(file.getPath()).forEach(record -> {
      if (record.isStart()) {
        var start = record.getStartData();
        Info info = new Info(start.name, start.type, new ArrayList<>());
        byId.put(start.entry, info);
        byName.put(start.name, info);
      } else if (!record.isControl()) {
        Info info = byId.get(record.getEntry());
        if (info == null) return;
        switch (info.type) {
          case "double" -> info.values.add(record.getDouble());
          case "boolean" -> info.values.add(record.getBoolean());
          case "string" -> info.values.add(record.getString());
          case "int64" -> info.values.add(record.getInteger());
          default -> info.values.add(info.type);
        }
      }
    });
    return byName;
  }

  static File newest(File dir) {
    File[] files = dir.listFiles((d, name) -> name.endsWith(".wpilog"));
    if (files == null || files.length == 0) return null;
    return Arrays.stream(files).max(Comparator.comparingLong(File::lastModified)).get();
  }
}
