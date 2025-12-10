package me.duncanruns.kerykeion;

import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.function.Consumer;

class JsonLogReader {
    private final LogReader logReader;

    public JsonLogReader(Path path) {
        this.logReader = new LogReader(path);
    }

    public void read(Consumer<EntryInfo> consumer) throws IOException {
        this.logReader.read(entryInfo -> {
            try {
                JsonObject entry = Kerykeion.GSON.fromJson(new String(entryInfo.entry, StandardCharsets.UTF_8), JsonObject.class);
                consumer.accept(new EntryInfo(entry, entryInfo.isNew));
            } catch (JsonSyntaxException e) {
                Kerykeion.errorLogger.accept("Failed to parse world log entry", e);
            }
        });
    }

    public void close() {
        this.logReader.close();
    }

    static class EntryInfo {
        final JsonObject entry;
        final boolean isNew;

        public EntryInfo(JsonObject entry, boolean isNew) {
            this.entry = entry;
            this.isNew = isNew;
        }
    }
}
