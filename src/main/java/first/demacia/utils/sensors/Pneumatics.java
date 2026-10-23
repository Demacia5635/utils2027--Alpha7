package first.demacia.utils.sensors;

import org.wpilib.hardware.pneumatic.Compressor;

import first.demacia.utils.log.Log;
import first.demacia.utils.log.LogTable;
import first.demacia.utils.log.Loggable;

public class Pneumatics extends Compressor implements Loggable {
    PneumaticsConfig config;
    String name;
	
    public Pneumatics(PneumaticsConfig config) {
        super(config.busId, config.moduleType);
        this.config = config;
        this.name= config.name;
        Log.log(name + " Pneumatics initialized");
        Log.putData("sensors/" + name, this);
    }

    @Override
    public void log(LogTable table) {
        table.put("Compressor State", getCompressorState());
        table.put("Pressure Switch", getPressureSwitch());
        table.put("Current", getCurrent());
        table.put("Analog Voltage", getAnalogVoltage());
        table.put("Analog Pressure", getAnalogPressure());
    }

    public String getName() {
        return config.name;   
    }
    public boolean getCompressorState() {
        return super.isEnabled();
    }
    public boolean getPressureSwitch() {
        return super.getPressureSwitchValue();
    }
    public double getCurrent() {
        return super.getCurrent();
    }
    public double getAnalogVoltage() {
        return super.getAnalogVoltage();
    }
    public double getAnalogPressure() {
        return super.getPressure();
    }
    public void disableCompressor() {
        super.disable();
    }
    public void enableCompressorDigital() {
        super.enableDigital();
    }
    public void enableCompressorAnalog(double minPressure, double maxPressure) {
        super.enableAnalog(minPressure, maxPressure);
    }
    public void enableHybrid(double minPressure, double maxPressure) {
        super.enableHybrid(minPressure, maxPressure);
    }

}



