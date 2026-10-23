package first.demacia.utils.log;

import org.wpilib.networktables.NetworkTableInstance;
import org.wpilib.networktables.PubSubOption;
import org.wpilib.networktables.StringPublisher;

/**
 * Shows a pop-up notification on the Elastic dashboard.
 */
public interface ElasticNotifier {
  /**
   * Shows a notification.
   *
   * @param severity the notification level
   * @param title the notification title
   * @param description the text under the title, may be empty
   * @param displaySeconds how long it stays on screen, 0 to stay until dismissed
   */
  void notify(Log.Severity severity, String title, String description, double displaySeconds);

  /**
   * Creates a notifier that publishes to Elastic's {@code /Elastic/RobotNotifications} topic.
   *
   * @param inst the NetworkTables instance Elastic is connected to
   * @return the notifier
   */
  static ElasticNotifier networkTables(NetworkTableInstance inst) {
    StringPublisher publisher = inst.getStringTopic("/Elastic/RobotNotifications")
        .publish(PubSubOption.SEND_ALL, PubSubOption.KEEP_DUPLICATES);
    return (severity, title, description, displaySeconds) ->
        publisher.set(toJson(severity, title, description, displaySeconds));
  }

  /**
   * Builds the JSON Elastic reads from its notification topic.
   *
   * @param severity the notification level
   * @param title the notification title
   * @param description the text under the title
   * @param displaySeconds how long it stays on screen, 0 to stay until dismissed
   * @return the JSON text
   */
  static String toJson(Log.Severity severity, String title, String description, double displaySeconds) {
    return "{\"level\":\"" + severity.name()
        + "\",\"title\":\"" + escape(title)
        + "\",\"description\":\"" + escape(description)
        + "\",\"displayTime\":" + (long) (displaySeconds * 1000)
        + ",\"width\":350.0,\"height\":-1.0}";
  }

  private static String escape(String text) {
    StringBuilder out = new StringBuilder(text.length() + 8);
    for (int i = 0; i < text.length(); i++) {
      char c = text.charAt(i);
      switch (c) {
        case '"' -> out.append("\\\"");
        case '\\' -> out.append("\\\\");
        case '\n' -> out.append("\\n");
        case '\r' -> out.append("\\r");
        case '\t' -> out.append("\\t");
        default -> {
          if (c < 0x20) {
            out.append(String.format("\\u%04x", (int) c));
          } else {
            out.append(c);
          }
        }
      }
    }
    return out.toString();
  }
}
