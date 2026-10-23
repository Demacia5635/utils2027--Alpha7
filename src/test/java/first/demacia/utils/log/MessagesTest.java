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
}
