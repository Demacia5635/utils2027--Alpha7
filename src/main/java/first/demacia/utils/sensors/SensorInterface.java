package first.demacia.utils.sensors;

import org.wpilib.telemetry.TelemetryLoggable;
import org.wpilib.telemetry.TelemetryTable;

/**
 * Base interface for all sensors.
 * 
 * <p>Provides common methods that all sensor types must implement for
 * identification and health monitoring.</p>
 * 
 * <p>Sensors publish their dashboard values through {@link #logTo(TelemetryTable)}.
 * Register a sensor with {@code Log.publishTelemetry("sensors/" + name, this)} so it is
 * logged every loop.</p>
 */
public interface SensorInterface extends TelemetryLoggable {
    /**
     * Gets the sensor's configured name.
     * 
     * @return Sensor name as specified in configuration
     */
    String getName();
    
    /**
     * Checks sensor health and logs any faults.
     * 
     * <p>Should be called periodically (e.g., in subsystem periodic() method).
     * Logs warnings or errors if sensor is disconnected, reporting faults, etc.</p>
     */
    public void checkElectronics();

    @Override
    void logTo(TelemetryTable table);
}
