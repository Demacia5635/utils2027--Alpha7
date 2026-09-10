package first.demacia.utils.leds;

/**constants for leds */
public class LedConstants {
  /**the size of every strip for every port */
  public static final int LENGTH = 8; // 8 leds block in strip
  /**the port of the leds */
  public static final int PORT = 9;

  /**
   * the blink time between what is color and what is off <br></br>
   * {@code Timer.getTimestamp() % BLINK_TIME != 0 ? color : Color.BLACK}
   */
  public static final double BLINK_TIME = 3;
}