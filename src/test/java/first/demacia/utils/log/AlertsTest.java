package first.demacia.utils.log;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AlertsTest {
  private record Sent(Log.Severity severity, String title, String description, double seconds) {}

  private final List<Sent> sent = new ArrayList<>();
  private final List<String> fileLines = new ArrayList<>();
  private double now;
  private boolean connected;
  private Alerts alerts;

  @BeforeEach
  void setUp() {
    sent.clear();
    fileLines.clear();
    now = 0;
    connected = false;
    alerts = new Alerts(
        (severity, title, description, seconds) -> sent.add(new Sent(severity, title, description, seconds)),
        () -> now,
        () -> connected,
        fileLines::add);
  }

  @AfterEach
  void tearDown() {
    // WPILib alert ids are global, so release them for the next test
    alerts.close();
  }

  /** Elastic connects now and the 2 s hold passes. */
  private void elasticReady() {
    connected = true;
    alerts.update();
    now += 2;
    alerts.update();
  }

  @Test
  void risingEdgeLogsAndNotifiesOnce() {
    elasticReady();

    alerts.alert("Arm hot", true);
    alerts.alert("Arm hot", true);
    alerts.alert("Arm hot", true);

    assertEquals(1, sent.size());
    assertEquals("Arm hot", sent.get(0).title());
    assertEquals(Log.Severity.WARNING, sent.get(0).severity());
    assertEquals(0, sent.get(0).seconds());
    assertEquals(1, fileLines.size());
    assertTrue(fileLines.get(0).contains("Arm hot"));
  }

  @Test
  void falseThenTrueNotifiesAgain() {
    elasticReady();

    alerts.alert("Arm hot", true);
    now += 2;
    alerts.alert("Arm hot", false);
    now += 2;
    alerts.alert("Arm hot", true);

    assertEquals(2, sent.size());
  }

  @Test
  void popupHeldUntilElasticConnectedTwoSeconds() {
    alerts.alert("Arm hot", true);
    assertEquals(0, sent.size());

    connected = true;
    alerts.update();
    now = 1.9;
    alerts.update();
    assertEquals(0, sent.size());

    now = 2.0;
    alerts.update();
    assertEquals(1, sent.size());
    assertEquals("Arm hot", sent.get(0).title());
  }

  @Test
  void heldPopupDroppedIfConditionCleared() {
    alerts.alert("Arm hot", true);
    alerts.alert("Arm hot", false);

    elasticReady();

    assertEquals(0, sent.size());
  }

  @Test
  void fiftyFirstDistinctMessageRaisesOverflow() {
    elasticReady();

    for (int i = 0; i < 51; i++) {
      alerts.alert("message " + i, true);
    }
    alerts.alert("message 51", true);

    assertEquals(51, sent.size());
    for (int i = 0; i < 50; i++) {
      assertEquals("message " + i, sent.get(i).title());
    }
    assertEquals("Too many Log.alert messages", sent.get(50).title());
    assertEquals(Log.Severity.ERROR, sent.get(50).severity());
  }

  @Test
  void ignoredMessagesAfterLimitWriteOneFileLine() {
    elasticReady();
    for (int i = 0; i < 50; i++) {
      alerts.alert("message " + i, true);
    }
    fileLines.clear();

    for (int i = 50; i < 80; i++) {
      alerts.alert("message " + i, true);
    }

    long limitLines = fileLines.stream().filter(l -> l.contains("limit reached")).count();
    assertEquals(1, limitLines);
  }

  @Test
  void oneTimeAlertStaysActive() {
    elasticReady();

    alerts.alert("bad CAN id");
    assertEquals(1, sent.size());

    now += 5;
    alerts.alert("bad CAN id");
    assertEquals(1, sent.size());
  }
}
