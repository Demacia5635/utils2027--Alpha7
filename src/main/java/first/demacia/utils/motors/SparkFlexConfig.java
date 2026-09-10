package first.demacia.utils.motors;

/**
 * * Configuration class specifically for REV Spark Flex motors.
 * Extends the base configuration to support Spark-specific parameters.
 */
public class SparkFlexConfig extends BaseMotorConfig<SparkFlexConfig> {

    // SparkMotorType motorType = SparkMotorType.SparkMax;

    /**
     * * Creates a new Spark Flex Configuration.
     * 
     * @param id   The CAN bus ID of the motor
     * @param name The name of the motor for logging and dashboard
     */
    public SparkFlexConfig(String name, int id, int canBusId) {
        super(name, id, canBusId);
        motorClass = MotorControllerType.SparkFlex;
    }

    /**
     * * Creates a new Spark Flex Configuration by copying another config.
     * 
     * @param id     The new CAN bus ID
     * @param name   The new name
     * @param config The existing configuration to copy from
     */
    public SparkFlexConfig(String name, int id, int canBusId, BaseMotorConfig<?> config) {
        super(name, id, canBusId);
        copyBaseFields(config);
    }
}