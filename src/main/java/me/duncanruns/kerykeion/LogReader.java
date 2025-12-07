package me.duncanruns.kerykeion;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;

class LogReader {
    private final Path path;
    private RandomAccessFile file = null;
    private boolean firstRead = true;

    public LogReader(Path path) {
        this.path = path;
    }

    public void read(Consumer<EntryInfo> consumer) throws IOException {
        boolean firstRead = this.firstRead;
        this.firstRead = false;
        if (!Files.exists(this.path)) return;
        if (this.file == null) {
            this.file = new RandomAccessFile(this.path.toFile(), "r");
        }
        long progress;
        while ((progress = this.file.getFilePointer()) < this.file.length()) {
            String line = this.readLine();
            if (!line.endsWith("\n")) {
                // Line hasn't finished writing yet
                this.file.seek(progress);
                break;
            }
            consumer.accept(new EntryInfo(line, !firstRead));
        }
    }

    private String readLine() throws IOException {
        StringBuilder sb = new StringBuilder(256);
        int b;
        do {
            b = this.file.read();
            if (b == -1) break;
            sb.append((char) b);
        } while (b != '\n');
        return sb.toString();
    }

    public void close() {
        try {
            if (this.file != null) {
                this.file.close();
            }
        } catch (IOException e) {
            Kerykeion.errorLogger.accept("Failed to close log file", e);
        } finally {
            this.file = null;
        }
    }

    static class EntryInfo {
        final String entry;
        final boolean isNew;

        public EntryInfo(String entry, boolean isNew) {
            this.entry = entry;
            this.isNew = isNew;
        }
    }
}
