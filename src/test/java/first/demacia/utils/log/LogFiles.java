package first.demacia.utils.log;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

import org.wpilib.datalog.DataLogReader;
import org.wpilib.datalog.DataLogRecord;

/** Test helper for reading values back out of a DataLog file. */
final class LogFiles {
  private LogFiles() {}

  /**
   * Reads a DataLog and returns the last value of every entry, keyed by entry name.
   * Values are Double, Boolean, Long, String or double[]; other entry types are skipped.
   *
   * @param wpilog The bytes of a .wpilog file
   * @return The last value of each entry
   */
  static Map<String, Object> lastValues(byte[] wpilog) {
    Map<Integer, DataLogRecord.StartRecordData> starts = new HashMap<>();
    Map<String, Object> values = new HashMap<>();
    // forEach, not a for-each loop: DataLogIterator.hasNext() needs 16 bytes left, so it drops a
    // last record shorter than that (a double with a 4-byte timestamp is 15 bytes)
    new DataLogReader(ByteBuffer.wrap(wpilog)).forEach(record -> {
      if (record.isStart()) {
        DataLogRecord.StartRecordData start = record.getStartData();
        starts.put(start.entry, start);
      } else if (!record.isControl()) {
        DataLogRecord.StartRecordData start = starts.get(record.getEntry());
        if (start == null) {
          return;
        }
        switch (start.type) {
          case "double" -> values.put(start.name, record.getDouble());
          case "boolean" -> values.put(start.name, record.getBoolean());
          case "int64" -> values.put(start.name, record.getInteger());
          case "string" -> values.put(start.name, record.getString());
          case "double[]" -> values.put(start.name, record.getDoubleArray());
          default -> { }
        }
      }
    });
    return values;
  }
}
