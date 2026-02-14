package me.duncanruns.kerykeion;

import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

class UpdatingJsonFileReader {
    private final Path path;
    private long lastModified = 0;
    private int failures = 0;
    private String lastContents = null;

    public UpdatingJsonFileReader(Path path) {
        this.path = path;
    }

    /**
     * Reads the file if it has been modified since the last read.
     *
     * @return The json if it has been modified, empty otherwise.
     * @throws IOException         if the file cannot be read for any reason other than it not existing
     * @throws JsonSyntaxException if the json is invalid more than 10 times in a row.
     */
    public Optional<JsonObject> read() throws IOException, JsonSyntaxException {
        if (!Files.exists(this.path)) return Optional.empty();

        long mTime = Files.getLastModifiedTime(this.path).toMillis();
        if (this.lastModified == mTime && (System.currentTimeMillis() - this.lastModified) > 50)
            return Optional.empty();

        String contents = new String(Files.readAllBytes(this.path));
        if (contents.equals(this.lastContents))
            return Optional.empty();
        this.lastContents = contents;

        JsonObject json;
        try {
            json = Kerykeion.GSON.fromJson(contents, JsonObject.class);
        } catch (JsonSyntaxException e) {
            this.failures++;
            if (this.failures > 10) { // Extremely unlikely that 10 reads in a row happen in the middle of a write (please do not punish me murphy)
                this.failures = 0;
                throw e;
            }
            return Optional.empty();
        }

        if (json == null) return Optional.empty();
        this.lastModified = mTime;
        return Optional.of(json);
    }
}
