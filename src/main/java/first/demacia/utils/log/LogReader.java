package first.demacia.utils.log;

import java.io.DataInputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import org.wpilib.framework.RobotBase;
import org.wpilib.system.DataLogManager;

public class LogReader {
    private static final Map<String, List<Entry>> groups = new HashMap<>();
    private static final List<Entry> entries = new ArrayList<>();

    private static final Map<Integer, ActiveLogData> activeEntriesMap = new HashMap<>(); // for adding values to entries

    private static final String REPLAY_PREFIX = "Telemetry/replay/";

    private static Predicate<EntryInfo> filter;

    private record ActiveLogData(String type, List<Entry> targetEntries) {}
    public record EntryInfo(String name, String type, String metadata) {}

    public static class Entry {
        public String groupName;
        public String name;
        public List<EntryPoint> data = new ArrayList<>();

        public Entry(String groupName, String name) {
            this.groupName = groupName;
            this.name = name;
        }
    }

    public static class EntryPoint {
        public Object value;
        public long time;

        public EntryPoint(Object value, long time) {
            this.value = value;
            this.time = time;
        }
    }

    public static List<Entry> getEntries(boolean isLatestLog, Predicate<EntryInfo> filter) {
        processLogFile(isLatestLog, filter);
        
        return entries;
    }

    public static Map<String, List<Entry>> getGroups(boolean isLatestLog, Predicate<EntryInfo> filter) {
        processLogFile(isLatestLog, filter);
        
        return groups;
    }

    /**
     * Reads a log file and groups its entries by parent path, for example
     * {@code "Telemetry/motors/arm"} holds {@code Position}, {@code Velocity}, ...
     * Entries under {@code Telemetry/replay/} are skipped.
     *
     * @param file   The .wpilog file
     * @param filter Selects the entries to read; null reads all
     * @return The groups, keyed by parent path
     */
    public static Map<String, List<Entry>> getGroups(File file, Predicate<EntryInfo> filter) {
        processLogFile(file.getAbsolutePath(), filter);

        return groups;
    }

    private static void processLogFile(boolean isLatestLog, Predicate<EntryInfo> filter) {
        try {
            System.out.println("Reading log file...");
            String filePath = isLatestLog ? loadLatestRobotLog() : LogFileChooser.selectFileFromComputer();

            processLogFile(filePath, filter);
        } catch (IOException e) {
            System.err.println("Error reading log file: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void processLogFile(String filePath, Predicate<EntryInfo> filter) {
        LogReader.filter = filter != null ? filter : info -> true;

        entries.clear();
        groups.clear();
        activeEntriesMap.clear();

        try {
            wpilogReader(filePath);
        } catch (IOException e) {
            System.err.println("Error reading log file: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static String loadLatestRobotLog() throws IOException {
        DataLogManager.getLog().flush();

        File latestLogFile = getLatestLogFile();
        if (latestLogFile == null) {
            throw new IOException("SysID Error: No .wpilog files found on the robot!");
        }

        System.out.println("SysID: Successfully found and loading latest log: " + latestLogFile.getAbsolutePath());

        return latestLogFile.getAbsolutePath(); 
    }

    private static File getLatestLogFile() {
        File logDir;

        if (RobotBase.isSimulation()) {
            logDir = new File("logs/");
        } else {
            // alpha-7 DataLogManager writes here (only call this on the robot: needs natives)
            logDir = new File(DataLogManager.getLogDir());
        }

        if (!logDir.exists()) {
            return null;
        }
        
        File[] files = logDir.listFiles((dir, name) -> name.endsWith(".wpilog"));

        if (files == null || files.length == 0) {
            return null;
        }

        File latestFile = files[0];
        for (File file : files) {
            if (file.lastModified() > latestFile.lastModified()) {
                latestFile = file;
            }
        }
        return latestFile;
    }

    private static void wpilogReader(String fileName) throws IOException {
        try (FileInputStream fileInputStream = new FileInputStream(fileName);
             DataInputStream dataInputStream = new DataInputStream(fileInputStream)) {
            
            byte[] signature = readHeader(dataInputStream);
            if (!Arrays.equals(signature, "WPILOG".getBytes())) {
                throw new IOException("Invalid WPILOG");
            }

            skipHeaderExtra(dataInputStream);
            System.out.println("Header read successfully. Starting to read records...");
            readRecords(dataInputStream);
            System.out.println("File Read Successfully. Total entries: " + entries.size());
        }
    }

    private static byte[] readHeader(DataInputStream dataInputStream) throws IOException {
        byte[] signature = new byte[6];
        dataInputStream.readFully(signature);
        return signature;
    }

    private static void skipHeaderExtra(DataInputStream dataInputStream) throws IOException {
        dataInputStream.readShort();
        int extraLength = Integer.reverseBytes(dataInputStream.readInt());
        if(extraLength > 0) {
            dataInputStream.skipBytes(extraLength);
        }
    }

    private static void readRecords(DataInputStream dataInputStream) throws IOException {
        int n = 0;
        while (true) {
            try {
                readRecord(dataInputStream);
                n++;
                if(n%1000 == 0) {
                    System.out.println("Read " + n + " records...");
                }
            } catch (EOFException e) {
                break;
            }
        }
    }

    private static void readRecord(DataInputStream dataInputStream) throws IOException {
        int headerByte = dataInputStream.readUnsignedByte();
        int idLength = (headerByte & 0x3) + 1;
        int payloadLength = (headerByte >> 2 & 0x3) + 1;
        int timestampLength = (headerByte >> 4 & 0x7) + 1;
        int recordId = readLittleEndianInt(dataInputStream, idLength);
        int payloadSize = readLittleEndianInt(dataInputStream, payloadLength);
        long timestamp = readLittleEndianLong(dataInputStream, timestampLength);

        if (recordId == 0) {
            addEntryFromControlRecord(dataInputStream, payloadSize);
            return;
        }

        ActiveLogData activeData = activeEntriesMap.get(recordId);
        
        if (activeData == null) {
            dataInputStream.skipBytes(payloadSize);
            return;
        }

        String type = activeData.type();
        List<Entry> targets = activeData.targetEntries();

        if (type.equals("float[]")) {
            int count = payloadSize / 4;
            double[] vals = new double[count];
            for (int i = 0; i < count; i++) {
                vals[i] = Float.intBitsToFloat(Integer.reverseBytes(dataInputStream.readInt()));
            }
            skipRemainder(dataInputStream, payloadSize - count * 4);
            addValue(targets, vals, timestamp);

        } else if (type.equals("double[]")) {
            int count = payloadSize / 8;
            double[] vals = new double[count];
            for (int i = 0; i < count; i++) {
                vals[i] = Double.longBitsToDouble(Long.reverseBytes(dataInputStream.readLong()));
            }
            skipRemainder(dataInputStream, payloadSize - count * 8);
            addValue(targets, vals, timestamp);

        } else if (type.equals("int64[]")) {
            int count = payloadSize / 8;
            long[] vals = new long[count];
            for (int i = 0; i < count; i++) {
                vals[i] = Long.reverseBytes(dataInputStream.readLong());
            }
            skipRemainder(dataInputStream, payloadSize - count * 8);
            addValue(targets, vals, timestamp);

        } else if (type.equals("boolean[]")) {
            boolean[] vals = new boolean[payloadSize];
            for (int i = 0; i < payloadSize; i++) {
                vals[i] = dataInputStream.readByte() != 0;
            }
            addValue(targets, vals, timestamp);

        } else if (type.equals("string[]")) {
            int count = Integer.reverseBytes(dataInputStream.readInt());
            String[] vals = new String[count];
            int bytesRead = 4;
            for (int i = 0; i < count; i++) {
                int strLen = Integer.reverseBytes(dataInputStream.readInt());
                byte[] strBytes = new byte[strLen];
                dataInputStream.readFully(strBytes);
                bytesRead += 4 + strLen;
                vals[i] = new String(strBytes, "UTF-8");
            }
            skipRemainder(dataInputStream, payloadSize - bytesRead);
            addValue(targets, vals, timestamp);
        } else if (type.equals("float")) {
            if (payloadSize >= 4) {
                float val = Float.intBitsToFloat(Integer.reverseBytes(dataInputStream.readInt()));
                if (!targets.isEmpty() && targets.get(0) != null) {
                    targets.get(0).data.add(new EntryPoint((double) val, timestamp));
                }
                if (payloadSize > 4) dataInputStream.skipBytes(payloadSize - 4);
            } else {
                dataInputStream.skipBytes(payloadSize);
            }
        } else if (type.equals("double")) {
            if (payloadSize >= 8) {
                double val = Double.longBitsToDouble(Long.reverseBytes(dataInputStream.readLong()));
                if (!targets.isEmpty() && targets.get(0) != null) {
                    targets.get(0).data.add(new EntryPoint(val, timestamp));
                }
                if (payloadSize > 8) dataInputStream.skipBytes(payloadSize - 8);
            } else {
                dataInputStream.skipBytes(payloadSize);
            }
        } else if (type.equals("int64")) {
            if (payloadSize >= 8) {
                long val = Long.reverseBytes(dataInputStream.readLong());
                if (!targets.isEmpty() && targets.get(0) != null) {
                    targets.get(0).data.add(new EntryPoint(val, timestamp));
                }
                if (payloadSize > 8) dataInputStream.skipBytes(payloadSize - 8);
            } else {
                dataInputStream.skipBytes(payloadSize);
            }
        } else if (type.equals("boolean")) {
            if (payloadSize >= 1) {
                boolean val = dataInputStream.readByte() != 0;
                if (!targets.isEmpty() && targets.get(0) != null) {
                    targets.get(0).data.add(new EntryPoint(val, timestamp));
                }
                if (payloadSize > 1) dataInputStream.skipBytes(payloadSize - 1);
            } else {
                dataInputStream.skipBytes(payloadSize);
            }
        } else if (type.equals("string")) {
            byte[] strBytes = new byte[payloadSize];
            dataInputStream.readFully(strBytes);
            String val = new String(strBytes, "UTF-8");
            
            if (!targets.isEmpty() && targets.get(0) != null) {
                targets.get(0).data.add(new EntryPoint(val, timestamp));
            }
        } else {
            dataInputStream.skipBytes(payloadSize);
        }
    }

    private static void addValue(List<Entry> targets, Object value, long timestamp) {
        if (!targets.isEmpty() && targets.get(0) != null) {
            targets.get(0).data.add(new EntryPoint(value, timestamp));
        }
    }

    private static void skipRemainder(DataInputStream dataInputStream, int bytes) throws IOException {
        if (bytes > 0) dataInputStream.skipBytes(bytes);
    }

    private static int readLittleEndianInt(DataInputStream dis, int bytes) throws IOException {
        int result = 0;
        for (int i = 0; i < bytes; i++) {
            result |= (dis.readUnsignedByte() << (i * 8));
        }
        return result;
    }

    private static long readLittleEndianLong(DataInputStream dis, int bytes) throws IOException {
        long result = 0L;
        for (int i = 0; i < bytes; i++) {
            result |= ((long) dis.readUnsignedByte() << (i * 8));
        }
        return result;
    }

    private static void addEntryFromControlRecord(DataInputStream dataInputStream, int payloadSize) throws IOException {
        int recordType = dataInputStream.readUnsignedByte();
        if (recordType == 0) { 
            int entryId = Integer.reverseBytes(dataInputStream.readInt());
            int nameLength = Integer.reverseBytes(dataInputStream.readInt());
            String name = readString(dataInputStream, nameLength);
            int typeLength = Integer.reverseBytes(dataInputStream.readInt());
            String type = readString(dataInputStream, typeLength).trim();
            int metaLength = Integer.reverseBytes(dataInputStream.readInt());
            String metadata = readString(dataInputStream, metaLength);

            List<Entry> targets = new ArrayList<>();

            EntryInfo info = new EntryInfo(name, type, metadata);

            if (filter.test(info) && !name.startsWith(REPLAY_PREFIX)) {
                String fieldName = name;
                String groupName = "";

                if (name.contains("/")) {
                    fieldName = name.substring(name.lastIndexOf('/') + 1);
                    groupName = name.substring(0, name.lastIndexOf('/'));
                }

                Entry newEntry = new Entry(groupName, fieldName);
                entries.add(newEntry);
                groups.computeIfAbsent(groupName, key -> new ArrayList<>()).add(newEntry);
                targets.add(newEntry);
            } else {
                targets.add(null);
            }

            activeEntriesMap.put(entryId, new ActiveLogData(type, targets));
        } else {
            if (payloadSize > 1) {
                dataInputStream.skipBytes(payloadSize - 1);
            }
        }
    }

    private static String readString(DataInputStream dataInputStream, int length) throws IOException {
        byte[] bytes = new byte[length];
        dataInputStream.readFully(bytes);
        return new String(bytes, "UTF-8");
    }
}
