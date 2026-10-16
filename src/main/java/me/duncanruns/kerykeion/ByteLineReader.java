package me.duncanruns.kerykeion;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Reads lines from a file where lines are terminated by a newline.
 * <p>
 * Can be used to read live (encrypted/restricted) play logs. Does not provide deciphering functionality.
 */
@SuppressWarnings("unused")
public class ByteLineReader implements Closeable {
    // Fields are protected if users of ByteLineReader want to make an extension that allows for more features such as
    // seeking.
    protected final Path path;
    protected RandomAccessFile file = null;
    protected final ByteArrayOutputStream lineBuffer = new ByteArrayOutputStream(128);

    public ByteLineReader(Path path) {
        this.path = path;
    }

    public Optional<byte[]> nextLine() throws IOException {
        if (!Files.exists(this.path)) return Optional.empty();
        if (this.file == null) this.openFile();

        long progress = this.file.getFilePointer();
        long end = this.file.length();

        this.lineBuffer.reset();

        int lastByte = -1;
        for (long i = progress; i < end; i++) {
            lastByte = this.file.read();
            if (lastByte == -1) return Optional.empty(); // wtf case
            this.lineBuffer.write(lastByte);
            if (lastByte == '\n') break;
        }
        if (lastByte == '\n') {
            return Optional.of(this.lineBuffer.toByteArray());
        }
        return Optional.empty();
    }

    private void openFile() throws IOException {
        if (this.file != null) return;
        this.file = new RandomAccessFile(this.path.toFile(), "r");
    }

    public void restart() throws IOException {
        if (this.file == null) return;
        this.file.seek(0);
    }

    @Override
    public void close() throws IOException {
        if (this.file != null) {
            this.file.close();
        }
    }
}
