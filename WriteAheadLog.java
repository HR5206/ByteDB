import java.io.*;
import java.nio.file.*;
import java.util.*;

public class WriteAheadLog {
    private final Path logPath;
    private final BufferedWriter writer;

    public WriteAheadLog(String fileName) throws IOException {
        this.logPath = Paths.get(fileName);
        Files.createDirectories(logPath.getParent());
        this.writer = Files.newBufferedWriter(logPath, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }

    public synchronized void append(String key, String value) throws IOException {
        // Simple TSV format: key\tvalue\n (tabs avoid commas in values)
        if (value == null) {
            writer.write(key.replace("\t", " ") + "\n");
        } else {
            writer.write(key.replace("\t", " ") + "\t" + value.replace("\t", " ") + "\n");
        }
        writer.flush(); // ensure durability
    }

    public List<String[]> replay() throws IOException {
        List<String[]> records = new ArrayList<>();
        if (!Files.exists(logPath)) return records;
        try (BufferedReader br = Files.newBufferedReader(logPath)) {
            String line;
            while ((line = br.readLine()) != null) {
                String[] parts = line.split("\t", 2);
                if (parts.length == 2) {
                    records.add(parts);
                } else if (parts.length == 1) {
                    records.add(new String[]{parts[0], null});
                }
            }
        }
        return records;
    }
}
