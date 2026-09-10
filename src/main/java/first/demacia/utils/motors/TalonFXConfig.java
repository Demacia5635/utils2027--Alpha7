package first.demacia.utils.motors;

/**
 * * Configuration class specifically for TalonFX motors.
 * Extends the base configuration to support Phoenix 6 specific parameters.
 */
public class TalonFXConfig extends BaseMotorConfig<TalonFXConfig> {

  /**
   * * Creates a new TalonFX Configuration.
   * 
   * @param id     The CAN bus ID of the motor
   * @param canbus The name of the CAN bus
   * @param name   The name of motor for logging and dashboard
   */
  public TalonFXConfig(String name, int id, Canbus canbus) {
    super(name, id, canbus);
    motorClass = MotorControllerType.TalonFX;
  }

  /**
   * * Creates a new TalonFX Configuration by copying another config.
   * 
   * @param id     The new CAN bus ID
   * @param name   The new name
   * @param config The existing configuration to copy from
   */
  public TalonFXConfig(String name, int id, Canbus canbus, BaseMotorConfig<?> config) {
    super(name, id, canbus);
    copyBaseFields(config);
  }

  public int getId() {
    return id;
  }

  public Canbus getCanbus() {
    return canbus;
  }

  public String name() {
    return name;
  }
}