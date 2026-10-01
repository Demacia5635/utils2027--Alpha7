package first.demacia.utils.mechanisms;


import first.demacia.utils.log.Log;
import first.demacia.utils.motors.MotorInterface;
import first.demacia.utils.sensors.SensorInterface;
import org.wpilib.telemetry.TelemetryTable;
import org.wpilib.tunable.Selectable;
import org.wpilib.tunable.Tunables;

/**
 * An extension of BaseMechanism that introduces the concept of States.
 * <p>
 * This class allows controlling the mechanism using a State Machine approach.
 * Each state defines a set of target values (e.g., positions or velocities) for the motors.
 * It includes a {@link Selectable} on the dashboard to manually switch states for testing.
 * </p>
 */
public class StateBaseMechanism extends BaseMechanism {

    /**
     * Interface representing a state of the mechanism.
     * Usually implemented by an Enum.
     */
    public interface MechanismState {
        /** @return The target values for the motors in this state */
        double[] getValues();
        /** @return The name of the state */
        String name();
    }

    /** Chooser for selecting states via the Dashboard */
    Selectable<MechanismState> stateChooser = new Selectable<>();
    
    /** The current active state */
    public MechanismState state;
    
    /** * Default IDLE state.
     * Sets all motor targets to 0.
     */
    public final MechanismState IDLE_STATE = new MechanismState() {
        double[] idleValues = new double[motorsAmount];
        @Override 
        public double[] getValues() { 
            return idleValues; 
        }
        @Override
        public String name() {
            return "IDLE";
        }
    };

    /**
     * Special TESTING state.
     * Uses values from a specific 'Test Values' array that can be edited on the Dashboard.
     */
    public final MechanismState TESTING_STATE = new MechanismState() {
        @Override 
        public double[] getValues() { 
            return getTestValues(); 
        }
        @Override
        public String name() {
            return "TESTING";
        }
    };

    /** Stores the values used when in TESTING state */
    protected double[] testValues;

    /**
     * Constructs a new StateBaseMechanism.
     * @param name The name of the mechanism
     * @param motors Array of motors
     * @param sensors Array of sensors
     * @param enumClass The Enum class defining the mechanism's states
     */
    public StateBaseMechanism(String name, MotorInterface[] motors, SensorInterface[] sensors, Class<? extends MechanismState> enumClass){
        super(name, motors, sensors);
        testValues = new double[motors.length];
        addNT(enumClass);
    }

    /**
     * Populates the NetworkTable (Dashboard) with the state chooser.
     * Adds TESTING, IDLE, and all values from the provided Enum.
     * @param enumClass The state Enum class
     */
    private void addNT(Class<? extends MechanismState> enumClass) {
        stateChooser.add(TESTING_STATE.name(), TESTING_STATE);
        stateChooser.addDefault(IDLE_STATE.name(), IDLE_STATE);
        state = IDLE_STATE;
        
        for (MechanismState state : enumClass.getEnumConstants()) {
            stateChooser.add(state.name(), state);
        }
        
        // Listener to update the local state variable when dashboard selection changes
        stateChooser.onChange(state -> this.state = state);
        
        Tunables.publish(getName() + "/" + getName() + " State Chooser", stateChooser);
        Tunables.getTable(getName()).publishValue(getName() + " Test Values",
            this::getTestValues, this::setTestValues, double[].class);

        for (int i = 0; i < getState().getValues().length; i++){
            final int index = i;
            Log.putData(getName() + "/" + motorNames[i] + "/" + motorNames[i] + " targetValue: ", () -> getValue(index));
        }
    }

    /**
     * Sets the default option selected in the dashboard chooser on startup.
     * @param state The state to be default
     */
    public void setStartingOption(MechanismState state){
        if (state == null) {
            Log.log("Starting state cannot be null");
            return;
        }

        stateChooser.addDefault(state.name(), state);
    }

    /**
     * Logs the mechanism's dashboard data, adding the current state name to the
     * default subsystem telemetry. The editable 'Test Values' array is published
     * as a tunable when the state chooser is created.
     */
    @Override
    public void logTo(TelemetryTable table) {
        super.logTo(table);
        table.log(getName() + " State", (getState() == null) ? "" : getState().name());
    }

    /**
     * Manually sets the current state of the mechanism.
     * @param state The new state
     */
    public void setState(MechanismState state) {
        this.state = state;
    }

    public void setStateIdle() {
        state = IDLE_STATE;
    }

    public void setStateTesting() {
        state = TESTING_STATE;
    }

    /**
     * @return The current state of the mechanism
     */
    public MechanismState getState() {
        return state != null ? state : IDLE_STATE;
    }

    /**
     * @return The array of current state values
     */
    public double[] getValues() {
        double[] values = getState().getValues();
        return values != null ? values : new double[0];
    }

    /**
     * @return The current target value for a specific motor index from the active state.
     * @param index The index of the motor in the motorArray.
     */
    public double getValue(int index) {
        double value = getState().getValues()[index];
        return value;
    }

    /**
     * @return The array of values used for the TESTING state
     */
    public double[] getTestValues(){
        return testValues != null? testValues : new double[0];
    }

    /**
     * Updates the values used for the TESTING state.
     * @param testValues The new values array
     */
    public void setTestValues(double[] testValues){
        this.testValues = testValues;
    }
}