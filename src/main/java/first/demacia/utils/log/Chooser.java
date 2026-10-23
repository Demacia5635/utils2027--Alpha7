package first.demacia.utils.log;

import java.util.function.Consumer;

import org.wpilib.tunable.Selectable;

/**
 * A dashboard selection between named options.
 *
 * @param <T> The type of the options
 */
public final class Chooser<T> {
  private record Option<T>(String name, T value) {}

  private final Selectable<Option<T>> selectable = new Selectable<>();
  private Consumer<T> listener;

  Chooser(Consumer<String> recordSelection, Consumer<RuntimeException> warn) {
    selectable.onChange(option -> {
      try {
        if (listener != null) {
          listener.accept(option.value());
        }
        recordSelection.accept(option.name());
      } catch (RuntimeException e) {
        warn.accept(e);
      }
    });
  }

  Selectable<?> selectable() {
    return selectable;
  }

  /**
   * Adds an option.
   *
   * @param name The name shown on the dashboard
   * @param value The value selected by that name
   */
  public void add(String name, T value) {
    selectable.add(name, new Option<>(name, value));
  }

  /**
   * Adds an option and makes it the one selected until the dashboard picks another.
   *
   * @param name The name shown on the dashboard
   * @param value The value selected by that name
   */
  public void addDefault(String name, T value) {
    selectable.addDefault(name, new Option<>(name, value));
  }

  /**
   * Sets the listener called when the dashboard selects an option.
   *
   * @param listener Receives the selected value
   */
  public void onChange(Consumer<T> listener) {
    this.listener = listener;
  }

  /**
   * Returns the selected value.
   *
   * @return The selected value, or null when nothing is selected and there is no default
   */
  public T getSelected() {
    Option<T> option = selectable.getSelected();
    return option == null ? null : option.value();
  }
}
