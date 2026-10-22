package first.demacia.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class DataTest {
  @Test
  void signalSampledTwentyMillisecondsAgoIsLoggedTwentyMillisecondsBeforeLogNow() {
    // Phoenix clock: the signal was sampled at 100.000 s and Phoenix now reads 100.020 s.
    // The log clock (WPIUtilJNI.now) is in nanoseconds and has its own time base.
    long logTimestamp = Data.toLogTimestamp(100.000, 100.020, 5_000_000_000_000L);

    assertEquals(4_999_980_000_000L, logTimestamp);
  }
}
