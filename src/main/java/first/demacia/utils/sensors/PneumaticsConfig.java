package first.demacia.utils.sensors;

import org.wpilib.hardware.pneumatic.PneumaticsModuleType;

public class PneumaticsConfig {
    public final int module;
    public final PneumaticsModuleType moduleType;
    public final String name;


    public PneumaticsConfig(int module, PneumaticsModuleType moduleType,String name) {
        this.module = module;
        this.moduleType = moduleType;
        this.name = name;
    }
}