package first.robot;

import org.wpilib.command2.InstantCommand;
import org.wpilib.command2.SubsystemBase;
import org.wpilib.math.geometry.Pose2d;
import org.wpilib.math.geometry.Rotation2d;

import first.demacia.utils.log.Chooser;
import first.demacia.utils.log.Log;
import first.demacia.utils.log.LogTable;
import first.demacia.utils.log.Loggable;

/**
 * Exercises every part of the Log API, so the new log infrastructure can be checked in simulation.
 * Everything it publishes is under {@code demo/}. Remove the {@code new LogDemo()} line in
 * {@link Robot} when done.
 */
public class LogDemo extends SubsystemBase implements Loggable {
  /** Alerts when the dashboard sets {@code demo/speed} above this */
  private static final double SPEED_LIMIT = 5;

  private int loops = 0;
  private double speed = 1;
  private boolean enabled = false;
  private double kP = 0.1;
  private String mode = "none";
  /** Shown on the dashboard, since 2027 alerts are not in NetworkTables */
  private int helloPresses = 0;
  private final Chooser<String> modeChooser;

  public LogDemo() {
    // 1. A value written every loop, shown on the dashboard
    Log.putData("demo/loops", () -> loops);
    // 2. A debug value: in the file always, on the dashboard only while "RC/is comp" is off
    Log.putData("demo/debug sine", () -> Math.sin(loops / 50.0), Log.Dashboard.DEBUG);
    // 3. An object that writes several values every loop (Loggable)
    Log.putData("demo/object", this);
    // 4. Arrays: a primitive array and a struct array (both broke before the fix)
    Log.putData("demo/array", () -> new double[] {loops, speed, kP});
    Log.putData("demo/poses", () -> new Pose2d[] {
        new Pose2d(1, 2, Rotation2d.ZERO), new Pose2d(speed, 0, Rotation2d.ZERO)});
    // 5. A broken supplier: must warn once and not stop the other values
    Log.putData("demo/broken", () -> {
      throw new IllegalStateException("demo failure on purpose");
    });

    // 6. Values edited from the dashboard; every edit is written to the file under Tunables/
    Log.tune("demo/speed", () -> speed, value -> speed = value);
    Log.tune("demo/enabled", () -> enabled, value -> enabled = value);
    Log.tune("demo/PID", "Demo PID", t -> {
      t.number("kP", () -> kP, value -> kP = value);
      t.button("print kP", new InstantCommand(() -> Log.log("demo kP is " + kP)).ignoringDisable(true));
    });

    // 7. A chooser; the selected name is written to the file
    modeChooser = Log.chooser("demo/mode");
    modeChooser.addDefault("none", "none");
    modeChooser.add("fast", "fast");
    modeChooser.add("slow", "slow");
    modeChooser.onChange(value -> mode = value);

    // 8. A button that runs a command, also while disabled
    Log.button("demo/say hello", new InstantCommand(() -> {
      helloPresses++;
      Log.log("hello from the demo button");
    }).ignoringDisable(true));

    // 9. A one-time alert at startup: the Elastic pop-up waits until Elastic is connected
    Log.alert("demo: startup alert (this is a test)");
    Log.log("demo: log demo is running");
  }

  @Override
  public void periodic() {
    loops++;
    // 10. A condition alert: pop-up and log line only when speed goes above the limit
    Log.alert("demo: speed is above " + SPEED_LIMIT, speed > SPEED_LIMIT);
  }

  @Override
  public void log(LogTable table) {
    table.put("speed", speed);
    table.put("enabled", enabled);
    table.put("mode", mode);
    table.put("selected", String.valueOf(modeChooser.getSelected()));
    table.put("hello presses", helloPresses);
    table.put("speed alert active", speed > SPEED_LIMIT);
  }

  @Override
  public String logType() {
    return "LogDemo";
  }
}
