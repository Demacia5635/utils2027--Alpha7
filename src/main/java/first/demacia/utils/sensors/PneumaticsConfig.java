package first.demacia.utils.sensors;

import org.wpilib.hardware.bus.CANPort;
import org.wpilib.hardware.pneumatic.PneumaticsModuleType;

public class PneumaticsConfig {
    public final CANPort busId;
    public final PneumaticsModuleType moduleType;
    public final String name;


    public PneumaticsConfig(CANPort busId, PneumaticsModuleType moduleType,String name) {
        this.busId = busId;
        this.moduleType = moduleType;
        this.name = name;
    }
}