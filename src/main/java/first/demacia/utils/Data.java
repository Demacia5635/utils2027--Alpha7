package first.demacia.utils;


import java.util.ArrayList;
import java.util.Objects;
import java.util.function.Supplier;

import com.ctre.phoenix6.StatusSignal;
import com.ctre.phoenix6.StatusSignalCollection;

/**
 * A generic wrapper class for data sources (StatusSignals or Suppliers).
 * <p>
 * This class abstracts the source of data and its type, allowing uniform
 * handling
 * of logging and network tables. It caches values to detect changes and convert
 * types (e.g., Boolean to Double) efficiently.
 * </p>
 * 
 * @param <T> The type of the data (Double, Boolean, String, etc.)
 */
public class Data<T> {
    /**
     * Static array of all signals to refresh them all at once (CTRE optimization)
     */
    private static StatusSignalCollection rioSignals = new StatusSignalCollection();
    private static StatusSignalCollection canivoreSignals = new StatusSignalCollection();
    /** List of all Data instances based on Signals */
    private static final ArrayList<Data<?>> signalInstances = new ArrayList<>();
    /** List of all Data instances based on Suppliers */
    private static final ArrayList<Data<?>> supplierInstances = new ArrayList<>();

    private StatusSignal<T> signal;
    private Supplier<T> supplier;

    private boolean isDouble = false;
    private boolean isBoolean = false;

    // Cached primitive arrays to avoid auto-boxing and garbage collection
    private double doubleValue;
    private float floatValue;
    private boolean booleanValue;
    private String stringValue;

    /**
     * Creates a new Data object from Phoenix 6 StatusSignals.
     * 
     * @param signal Variable arguments of StatusSignals
     */
    public Data(StatusSignal<T> signal, boolean isRio) {
        this.signal = signal;

        detectTypeFromSignal();
        registerSignal();
        addSignal(signal, isRio);
        refresh();
    }

    /**
     * Creates a new Data object from Java Suppliers.
     * 
     * @param supplier Variable arguments of Suppliers
     */
    public Data(Supplier<T> supplier) {
        this.supplier = supplier;

        detectTypeFromSupplier();
        registerSupplier();
        refresh();
    }

    /** Registers this instance's signals to the static master list */
    private void registerSignal() {
        signalInstances.add(this);
    }

    /** Registers this instance to the static supplier list */
    private void registerSupplier() {
        supplierInstances.add(this);
    }

    /** Detects the data type based on the first signal's value */
    private void detectTypeFromSignal() {
        T value = signal.getValue();

        if (value instanceof Number) {
            isDouble = true;
        } else if (value instanceof Boolean) {
            isBoolean = true;
        } else {
            try {
                signal.getValueAsDouble();
                isDouble = true;
            } catch (Exception e) {
                isDouble = false;
            }
        }
    }

    /** Detects the data type based on the first supplier's value */
    private void detectTypeFromSupplier() {
        T value = supplier.get();

        if (value instanceof Number) {
            isDouble = true;
        } else if (value instanceof Boolean) {
            isBoolean = true;
        }
    }

    /**
     * Refreshes the local data from the source.
     * If using signals, assumes the master refresh has already been called.
     */
    private void refresh() {
        if (signal != null) {
            StatusSignal.refreshAll(signal);
            updateSignalValue();
        } else {
            refreshSupplier();
        }
    }

    public static void addSignal(StatusSignal<?> signal, boolean isRio) {
        if (isRio) {
            rioSignals.addSignals(signal);
        }
        else{
            canivoreSignals.addSignals(signal);
        }
    }

    public static void addSignals(boolean isRio, StatusSignal<?>... signals) {
        if (isRio) {
            rioSignals.addSignals(signals);
        }
        else{
            canivoreSignals.addSignals(signals);
        }
    }

    /**
     * Updates the internal primitive arrays from the signals.
     */
    private void updateSignalValue() {
        if (isDouble) {
            double val = signal.getValueAsDouble();
            if (doubleValue != val) {
                doubleValue = val;
            }
            if (floatValue != val) {
                floatValue = (float) val;
            }
        } else if (isBoolean) {
            if (booleanValue != (Boolean) signal.getValue()) {
                booleanValue = (Boolean) signal.getValue();
            }
        } else {
            if (!Objects.equals(stringValue, ((signal.getValue() == null) ? "null" : signal.getValue().toString()))) {
                stringValue = (signal.getValue() == null) ? "null" : signal.getValue().toString();;
            }
        }
    }

    /**
     * Updates the internal primitive arrays from the suppliers.
     */
    private void refreshSupplier() {
        if (isDouble) {
            Number val = ((Number) supplier.get());
            double newVal = val.doubleValue();
            if (doubleValue != newVal) {
                doubleValue = newVal;
            }
            float newFloatVal = val.floatValue();
            if (floatValue != newFloatVal) {
                floatValue = newFloatVal;
            }
        } else if (isBoolean) {
            boolean newVal = (Boolean) supplier.get();
            if (booleanValue != newVal) {
                booleanValue = newVal;
            }
        } else {
            String newVal = (supplier.get() == null) ? "null" : supplier.get().toString();
            if (!Objects.equals(stringValue, newVal)) {
                stringValue = newVal;
            }
        }
    }

    public static void setFrequancyAll() {
       // StatusSignal.setUpdateFrequencyForAll(Frequency.ofBaseUnits(100, Hertz), rioSignals);
       // StatusSignal.setUpdateFrequencyForAll(Frequency.ofBaseUnits(100, Hertz), canivoreSignals);
    }

    /**
     * Static method to refresh all registered Data instances.
     * First refreshes all Phoenix signals, then updates local values.
     */
    public static void refreshAll() {
        rioSignals.refreshAll();
        canivoreSignals.refreshAll();

        for (int i = 0; i < signalInstances.size(); i++) {
            signalInstances.get(i).updateSignalValue();
        }

        for (int i = 0; i < supplierInstances.size(); i++) {
            supplierInstances.get(i).refreshSupplier();
        }
    }

    /**
     * @return The value as a double (1.0 for true if boolean)
     */
    public double getDouble() {
        return isDouble ? doubleValue
                        : isBoolean ? booleanValue ? 1f : 0f
                                : 0f;
    }

    /**
     * @return The value as a float (1f for true if boolean)
     */
    public float getFloat() {
        return isDouble ? floatValue
                        : isBoolean ? booleanValue ? 1f : 0f
                                : 0f;
    }

    /**
     * @return The value as a boolean (true for 1 if number)
     */
    public boolean getBoolean() {
        return isBoolean ? booleanValue
                        : isDouble ? doubleValue == 1
                                : false;
    }

    /**
     * @return The value as a string
     */
    public String getString() {
        return isDouble ? ((Double) doubleValue).toString()
                        : isBoolean ? ((Boolean) booleanValue).toString()
                                : stringValue;
    }

    /**
     * @return The first StatusSignal if available
     */
    public StatusSignal<T> getSignal() {
        return (signal != null) ? signal
                : null;
    }

    /**
     * @return The first Supplier if available
     */
    public Supplier<T> getSupplier() {
        return (supplier != null) ? supplier
                : null;
    }

    public boolean isDouble() {
        return isDouble;
    }

    public boolean isBoolean() {
        return isBoolean;
    }


    /**
     * Clears all static references and signals.
     */
    public static void clearAllSignals() {
        rioSignals = new StatusSignalCollection();
        canivoreSignals = new StatusSignalCollection();
        signalInstances.clear();
        supplierInstances.clear();
    }
}