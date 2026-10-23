package first.demacia.utils.log;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class MessagesTest {
  private final List<String> written = new ArrayList<>();
  private final List<ConsoleAlert> created = new ArrayList<>();
  private final Messages messages = new Messages(written::add);

  private ConsoleAlert log(String text, Log.Severity severity) {
    ConsoleAlert alert = messages.log(text, severity);
    created.add(alert);
    return alert;
  }

  @AfterEach
  void closeAlerts() {
    created.forEach(ConsoleAlert::close);
  }

  @Test
  void messageIsWrittenToTheFile() {
    log("arm ready", Log.Severity.INFO);
    assertEquals(List.of("arm ready"), written);
  }

  @Test
  void repeatedMessageReusesItsAlert() {
    ConsoleAlert first = log("same", Log.Severity.INFO);
    ConsoleAlert second = log("same", Log.Severity.INFO);
    assertSame(first, second);
  }

  @Test
  void differentMessagesGetDifferentAlerts() {
    ConsoleAlert a = log("a", Log.Severity.INFO);
    ConsoleAlert b = log("b", Log.Severity.WARNING);
    assertNotSame(a, b);
  }

  @Test
  void eleventhMessageEvictsTheOldest() {
    List<ConsoleAlert> alerts = new ArrayList<>();
    for (int i = 0; i < 11; i++) {
      alerts.add(log("message " + i, Log.Severity.INFO));
    }

    // The most recent ten are still the live, reused instances
    for (int i = 1; i < 11; i++) {
      assertSame(alerts.get(i), log("message " + i, Log.Severity.INFO));
    }
    // The first one was closed and evicted, so logging it again makes a fresh alert
    assertNotSame(alerts.get(0), log("message 0", Log.Severity.INFO));
  }
}
