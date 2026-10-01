package first.demacia.utils.motors;

import org.wpilib.hardware.bus.CANPort;

/**
 * * Configuration class specifically for REV Spark Max motors.
 * Extends the base configuration to support Spark-specific parameters.
 */
public class SparkMaxConfig extends BaseMotorConfig<SparkMaxConfig> {

    // SparkMotorType motorType = SparkMotorType.SparkMax;

    /**
     * * Creates a new Spark Max Configuration.
     * 
     * @param id   The CAN bus ID of the motor
     * @param name The name of the motor for logging and dashboard
     */
    public SparkMaxConfig(String name, int id, CANPort canPort) {
        super(name, id, canPort);
        motorClass = MotorControllerType.SparkMax;
    }

    /**
     * * Creates a new Spark Max Configuration by copying another config.
     * 
     * @param id     The new CAN bus ID
     * @param name   The new name
     * @param config The existing configuration to copy from
     */
    public SparkMaxConfig(String name, int id, CANPort canPort, BaseMotorConfig<?> config) {
        super(name, id, canPort);
        copyBaseFields(config);
        motorClass = MotorControllerType.SparkMax;

    }
}