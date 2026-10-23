package first.demacia.utils.log;

/** A set of related tunable values published together under one dashboard group. */
@FunctionalInterface
public interface TunableGroup {
  /**
   * Publishes the group's values.
   *
   * @param table The table to publish the group's number, flag and button children to
   */
  void tune(TuneTable table);
}
