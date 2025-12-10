package me.duncanruns.kerykeion;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;

class LogReader {
    private final Path path;
    private RandomAccessFile file = null;
    private boolean firstRead = true;
    private final ByteArrayOutputStream lineBuffer = new ByteArrayOutputStream(128);

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
            byte[] line = this.readLine();
            if (line.length == 0 || line[line.length - 1] != '\n') {
                // Line hasn't finished writing yet or no bytes were read
                this.file.seek(progress);
                break;
            }
            consumer.accept(new EntryInfo(line, !firstRead));
        }
    }

    private byte[] readLine() throws IOException {
        try {
            int c;
            do {
                c = this.file.read();
                if (c == -1) break;
                this.lineBuffer.write(c);
            } while (c != '\n');
            return this.lineBuffer.toByteArray();
        } finally {
            this.lineBuffer.reset();
        }
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
        final byte[] entry;
        final boolean isNew;

        public EntryInfo(byte[] entry, boolean isNew) {
            this.entry = entry;
            this.isNew = isNew;
        }
    }
}
