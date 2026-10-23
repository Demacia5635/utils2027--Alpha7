package first.demacia.utils.log;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.DoubleSupplier;

import org.wpilib.util.Alert;

/**
 * One-line alerts for {@code periodic()} code: a WPILib alert plus an Elastic pop-up on the
 * rising edge of the condition.
 *
 * <p>The pop-up is held until Elastic has been connected for 2 seconds, since Elastic subscribes
 * to the pop-up topic a moment after it connects. A held pop-up whose condition cleared is
 * dropped. At most 50 distinct messages are remembered, then a single error alert is raised
 * instead.
 */
public final class Alerts implements AutoCloseable {
  private static final String GROUP = "Alerts";
  private static final String OVERFLOW_TITLE = "Too many Log.alert messages";
  /** Most distinct messages remembered; a message built from a changing value would leak one per loop. */
  private static final int MESSAGE_LIMIT = 50;
  /** Seconds Elastic must be connected before pop-ups are sent. */
  private static final double POPUP_CONNECT_DELAY = 2;
  /** Minimum seconds between two pop-ups of the same alert. */
  private static final double MIN_NOTIFICATION_INTERVAL = 1;

  private final ElasticNotifier notifier;
  private final DoubleSupplier clockSeconds;
  private final BooleanSupplier elasticConnected;
  private final Consumer<String> fileLog;

  private final Map<String, Entry> entries = new HashMap<>();
  private final List<Entry> pending = new ArrayList<>();
  private Entry overflow;
  private double elasticConnectedSince = Double.NaN;

  private final class Entry {
    final String message;
    final Log.Severity severity;
    final String description;
    final Alert alert;
    boolean active;
    double lastNotification = Double.NEGATIVE_INFINITY;

    Entry(String message, Log.Severity severity, String description, Alert.Level level) {
      this.message = message;
      this.severity = severity;
      this.description = description;
      this.alert = new Alert(GROUP, message, message, level);
    }

    void notifyElastic() {
      double now = clockSeconds.getAsDouble();
      if (now - lastNotification < MIN_NOTIFICATION_INTERVAL) {
        return;
      }
      lastNotification = now;
      notifier.notify(severity, message, description, 0);
    }
  }

  /**
   * Creates the alert handler.
   *
   * @param notifier shows the Elastic pop-ups
   * @param clockSeconds the current time in seconds
   * @param elasticConnected whether Elastic itself (not just any NetworkTables client) is connected
   * @param fileLog receives one line for every alert that becomes active
   */
  public Alerts(ElasticNotifier notifier, DoubleSupplier clockSeconds,
      BooleanSupplier elasticConnected, Consumer<String> fileLog) {
    this.notifier = notifier;
    this.clockSeconds = clockSeconds;
    this.elasticConnected = elasticConnected;
    this.fileLog = fileLog;
  }

  /**
   * Raises or clears an alert. Safe to call every loop: the log line and pop-up happen only when
   * {@code active} changes from false to true. The message identifies the alert, so keep it
   * constant between calls.
   *
   * @param message the alert text
   * @param active whether the condition currently holds
   */
  public void alert(String message, boolean active) {
    Entry entry = entries.get(message);
    if (entry == null) {
      if (entries.size() >= MESSAGE_LIMIT) {
        raiseOverflow(message);
        return;
      }
      entry = new Entry(message, Log.Severity.WARNING, "", Alert.Level.MEDIUM);
      entries.put(message, entry);
    }
    boolean risingEdge = active && !entry.active;
    entry.active = active;
    entry.alert.set(active);
    if (risingEdge) {
      fileLog.accept("[ALERT - " + entry.severity.name() + "] " + message);
      showPopup(entry);
    }
  }

  /**
   * Raises an alert once and leaves it active, for something checked a single time such as an
   * invalid CAN ID in a constructor.
   *
   * @param message the alert text
   */
  public void alert(String message) {
    alert(message, true);
  }

  /** Call every loop: tracks the Elastic connection and sends the held pop-ups once it is ready. */
  public void update() {
    if (!elasticConnected.getAsBoolean()) {
      elasticConnectedSince = Double.NaN;
      return;
    }
    if (Double.isNaN(elasticConnectedSince)) {
      elasticConnectedSince = clockSeconds.getAsDouble();
    }
    if (pending.isEmpty() || !isElasticReady()) {
      return;
    }
    for (Entry entry : pending) {
      if (entry.active) {
        entry.notifyElastic();
      }
    }
    pending.clear();
  }

  /** Removes every alert from the WPILib alerts widget so another instance can reuse the ids. */
  @Override
  public void close() {
    for (Entry entry : entries.values()) {
      entry.alert.close();
    }
    if (overflow != null) {
      overflow.alert.close();
    }
    entries.clear();
    pending.clear();
    overflow = null;
  }

  private void raiseOverflow(String firstIgnoredMessage) {
    if (overflow == null) {
      overflow = new Entry(OVERFLOW_TITLE, Log.Severity.ERROR,
          "More than " + MESSAGE_LIMIT
              + " different alert texts were registered. A message built from a changing value "
              + "creates a new alert every loop - keep the text constant.",
          Alert.Level.HIGH);
      overflow.active = true;
      overflow.alert.set(true);
      fileLog.accept("[ALERT - ERROR] " + OVERFLOW_TITLE);
      fileLog.accept("[Log.alert] message limit reached, ignoring: " + firstIgnoredMessage);
      showPopup(overflow);
    }
  }

  private void showPopup(Entry entry) {
    if (isElasticReady()) {
      entry.notifyElastic();
    } else if (!pending.contains(entry)) {
      pending.add(entry);
    }
  }

  private boolean isElasticReady() {
    return !Double.isNaN(elasticConnectedSince)
        && clockSeconds.getAsDouble() - elasticConnectedSince >= POPUP_CONNECT_DELAY;
  }
}
