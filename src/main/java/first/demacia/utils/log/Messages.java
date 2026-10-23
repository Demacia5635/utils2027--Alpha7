package first.demacia.utils.log;

import java.util.ArrayList;
import java.util.function.Consumer;

import org.wpilib.util.Alert;

/**
 * Console messages: each message is written to the log file and shown as a pop-up alert.
 * Repeating a message reuses its alert; at most {@link ConsoleConstants#CONSOLE_LIMIT}
 * alerts are active, the oldest is closed first.
 */
final class Messages {
  private final Consumer<String> fileLog;
  private final ArrayList<ConsoleAlert> active = new ArrayList<>();

  /**
   * @param fileLog writes a message to the log file ({@code DataLogManager::log} in production)
   */
  Messages(Consumer<String> fileLog) {
    this.fileLog = fileLog;
  }

  /**
   * Logs a message to the file and shows it as an alert.
   *
   * @param text     the message
   * @param severity how serious it is
   * @return the alert showing the message
   */
  ConsoleAlert log(String text, Log.Severity severity) {
    fileLog.accept(text);

    // WPILib allows only one live Alert per (group, text) - reuse it instead of allocating a duplicate
    for (int i = 0; i < active.size(); i++) {
      ConsoleAlert existing = active.get(i);
      if (existing.getText().equals(text)) {
        existing.setText(text);
        active.remove(i);
        active.add(existing);
        return existing;
      }
    }

    ConsoleAlert alert = new ConsoleAlert(text, level(severity));
    alert.set(true);
    while (active.size() >= ConsoleConstants.CONSOLE_LIMIT) {
      active.remove(0).close();
    }
    active.add(alert);
    return alert;
  }

  /** Closes alerts that have been shown longer than the allowed time. Call every loop. */
  void update() {
    for (int i = active.size() - 1; i >= 0; i--) {
      ConsoleAlert alert = active.get(i);
      if (alert.isTimerOver()) {
        alert.set(false);
        alert.close();
        active.remove(i);
      }
    }
  }

  private static Alert.Level level(Log.Severity severity) {
    return switch (severity) {
      case INFO -> Alert.Level.LOW;
      case WARNING -> Alert.Level.MEDIUM;
      case ERROR -> Alert.Level.HIGH;
    };
  }
}
