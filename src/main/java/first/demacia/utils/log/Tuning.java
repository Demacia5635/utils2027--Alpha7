package first.demacia.utils.log;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;
import java.util.function.Supplier;

import org.wpilib.command2.Command;
import org.wpilib.datalog.BooleanLogEntry;
import org.wpilib.datalog.DataLog;
import org.wpilib.datalog.DoubleArrayLogEntry;
import org.wpilib.datalog.DoubleLogEntry;
import org.wpilib.datalog.StringLogEntry;
import org.wpilib.smartdashboard.Field2d;
import org.wpilib.tunable.ComplexTunable;
import org.wpilib.tunable.TunableTable;
import org.wpilib.util.function.BooleanConsumer;

/**
 * Publishes editable values to the dashboard and appends every accepted edit to the log file at
 * {@code Tunables/<path>}. An edit is recorded only after its setter returned normally; a setter
 * that throws produces a warning and leaves the other tunables working.
 */
final class Tuning {
  private final TunableTable root;
  private final DataLog file;
  private final Consumer<String> warn;
  private final Set<String> paths = new HashSet<>();
  private final Map<String, DoubleLogEntry> numberEntries = new HashMap<>();
  private final Map<String, BooleanLogEntry> flagEntries = new HashMap<>();
  private final Map<String, DoubleArrayLogEntry> arrayEntries = new HashMap<>();
  private final Map<String, StringLogEntry> stringEntries = new HashMap<>();

  Tuning(TunableTable root, DataLog file, Consumer<String> warn) {
    this.root = root;
    this.file = file;
    this.warn = warn;
  }

  void number(String path, DoubleSupplier getter, DoubleConsumer setter) {
    String key = normalize(path);
    if (claim(key)) {
      root.publishDouble(key, getter, recordingNumber(key, setter));
    }
  }

  void flag(String path, BooleanSupplier getter, BooleanConsumer setter) {
    String key = normalize(path);
    if (claim(key)) {
      root.publishBoolean(key, getter, recordingFlag(key, setter));
    }
  }

  void array(String path, Supplier<double[]> getter, Consumer<double[]> setter) {
    String key = normalize(path);
    if (!claim(key)) {
      return;
    }
    root.publishValue(key, getter, value -> {
      try {
        setter.accept(value);
      } catch (RuntimeException e) {
        warnRejected(key, e);
        return;
      }
      arrayEntries.computeIfAbsent(key, k -> new DoubleArrayLogEntry(file, "Tunables/" + k))
          .append(value);
    }, double[].class);
  }

  void group(String path, String type, TunableGroup group) {
    String key = normalize(path);
    if (!claim(key)) {
      return;
    }
    root.publish(key, new ComplexTunable() {
      @Override
      public String getTunableType() {
        return type;
      }

      @Override
      public void publishTunable(TunableTable table) {
        group.tune(new TuneTable(table, key, Tuning.this));
      }
    });
  }

  <T> Chooser<T> chooser(String path) {
    String key = normalize(path);
    Chooser<T> chooser = new Chooser<>(
        name -> stringEntries.computeIfAbsent(key, k -> new StringLogEntry(file, "Tunables/" + k))
            .append(name),
        e -> warnRejected(key, e));
    if (claim(key)) {
      root.publish(key, chooser.selectable());
    }
    return chooser;
  }

  void field(String path, Field2d field) {
    String key = normalize(path);
    if (claim(key)) {
      root.publish(key, field);
    }
  }

  void button(String path, Command command) {
    String key = normalize(path);
    if (claim(key)) {
      root.publish(key, command);
    }
  }

  /** Reserves a path; returns false and warns when it is already published. */
  boolean claim(String normalizedPath) {
    if (paths.add(normalizedPath)) {
      return true;
    }
    warn.accept("Tunable already published, keeping the first: " + normalizedPath);
    return false;
  }

  DoubleConsumer recordingNumber(String key, DoubleConsumer setter) {
    return value -> {
      try {
        setter.accept(value);
      } catch (RuntimeException e) {
        warnRejected(key, e);
        return;
      }
      numberEntries.computeIfAbsent(key, k -> new DoubleLogEntry(file, "Tunables/" + k))
          .append(value);
    };
  }

  BooleanConsumer recordingFlag(String key, BooleanConsumer setter) {
    return value -> {
      try {
        setter.accept(value);
      } catch (RuntimeException e) {
        warnRejected(key, e);
        return;
      }
      flagEntries.computeIfAbsent(key, k -> new BooleanLogEntry(file, "Tunables/" + k))
          .append(value);
    };
  }

  private void warnRejected(String key, RuntimeException e) {
    warn.accept("Tunable " + key + " rejected an edit: " + e);
  }

  private static String normalize(String path) {
    int start = 0;
    int end = path.length();
    while (start < end && path.charAt(start) == '/') {
      start++;
    }
    while (end > start && path.charAt(end - 1) == '/') {
      end--;
    }
    return path.substring(start, end);
  }
}
