package first.demacia.utils.log;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.wpilib.networktables.NetworkTableInstance;
import org.wpilib.networktables.PubSubOption;
import org.wpilib.networktables.StringSubscriber;

class ElasticNotifierTest {
  @Test
  void notificationJsonHasElasticFields() {
    assertEquals(
        "{\"level\":\"WARNING\",\"title\":\"Arm hot\",\"description\":\"\","
            + "\"displayTime\":0,\"width\":350.0,\"height\":-1.0}",
        ElasticNotifier.toJson(Log.Severity.WARNING, "Arm hot", "", 0));
  }

  @Test
  void notificationJsonConvertsSecondsToMilliseconds() {
    assertTrue(ElasticNotifier.toJson(Log.Severity.INFO, "t", "d", 3).contains("\"displayTime\":3000,"));
  }

  @Test
  void notificationJsonEscapesQuotesAndBackslashes() {
    String json = ElasticNotifier.toJson(Log.Severity.ERROR, "say \"hi\" \\ bye", "", 0);

    assertTrue(json.contains("\"title\":\"say \\\"hi\\\" \\\\ bye\""), json);
  }

  @Test
  void networkTablesPublishesToElasticTopic() {
    try (NetworkTableInstance inst = NetworkTableInstance.create();
        StringSubscriber subscriber = inst.getStringTopic("/Elastic/RobotNotifications")
            .subscribe("", PubSubOption.SEND_ALL, PubSubOption.KEEP_DUPLICATES)) {
      ElasticNotifier notifier = ElasticNotifier.networkTables(inst);

      notifier.notify(Log.Severity.WARNING, "Arm hot", "", 0);

      assertEquals(ElasticNotifier.toJson(Log.Severity.WARNING, "Arm hot", "", 0), subscriber.get());
    }
  }
}
