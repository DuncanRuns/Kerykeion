package me.duncanruns.kerykeion;

import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import com.google.gson.annotations.SerializedName;
import me.duncanruns.kerykeion.listeners.HermesWorldLogListener;

import java.io.IOException;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

class LivePlayLogTracker implements HermesWorldLogListener {
    private final Map<Path, LivePlayLog> livePlayLogs = new HashMap<>(); // world path -> stuff

    @Override
    public void onWorldLogEntry(JsonObject instanceInfo, JsonObject entry, boolean isNew) {
        // {"world":{"relative":true,"path":"saves/New World (2)"},"type":"entering","time":1763434215136}
        // {"world":{"relative":true,"path":"saves/New World (2)"},"type":"leave","time":1763434227668}
        WorldLogEntry worldLogEntry;
        try {
            worldLogEntry = Kerykeion.GSON.fromJson(entry, WorldLogEntry.class);
        } catch (JsonSyntaxException e) {
            Kerykeion.errorLogger.accept("Failed to parse world log entry for live play log", e);
            return;
        }

        Path worldPath;
        try {
            worldPath = KerykeionUtil.resolveGameDirRelativePath(
                    Paths.get(instanceInfo.get("game_dir").getAsString()),
                    worldLogEntry.worldPath
            );
        } catch (InvalidPathException e) {
            Kerykeion.errorLogger.accept("Failed to resolve world path for live play log", e);
            return;
        }

        if (Objects.equals(worldLogEntry.type, "entering")) {
            Optional.ofNullable(this.livePlayLogs.get(worldPath))
                    .ifPresent(livePlayLog -> livePlayLog.logReader.close());
            LivePlayLog lpl = new LivePlayLog(instanceInfo, worldPath, worldPath.resolve("hermes").resolve("restricted").resolve("play.log.enc"));
            this.livePlayLogs.put(
                    worldPath,
                    lpl
            );
        } else if (Objects.equals(worldLogEntry.type, "leave")) {
            Optional.ofNullable(this.livePlayLogs.get(worldPath))
                    .ifPresent(livePlayLog -> {
                        if (!livePlayLog.used) {
                            livePlayLog.logReader.close();
                            this.livePlayLogs.remove(worldPath);
                        } else {
                            livePlayLog.expiration = worldLogEntry.time + 1000;
                        }
                    });
        }
    }

    public void tick(Consumer<EntryInfo> consumer) {
        long currentTime = System.currentTimeMillis();
        this.livePlayLogs.values().forEach(livePlayLog -> {
            livePlayLog.used = true;
            try {
                livePlayLog.logReader.read(entryInfo -> consumer.accept(new EntryInfo(livePlayLog.instanceInfo, livePlayLog.worldPath, entryInfo.entry)));
            } catch (IOException e) {
                Kerykeion.errorLogger.accept("Failed to read live play log", e);
                livePlayLog.logReader.close();
                this.livePlayLogs.remove(livePlayLog.worldPath);
                return;
            }
            if (livePlayLog.hasExpired(currentTime)) {
                livePlayLog.logReader.close();
            }
        });
        this.livePlayLogs.values().removeIf(livePlayLog -> livePlayLog.hasExpired(currentTime));
    }

    static class EntryInfo {
        final JsonObject instanceInfo;
        final Path worldPath;
        final String line;

        public EntryInfo(JsonObject instanceInfo, Path worldPath, String line) {
            this.instanceInfo = instanceInfo;
            this.worldPath = worldPath;
            this.line = line;
        }
    }

    private static class LivePlayLog {
        private final JsonObject instanceInfo;
        private final Path worldPath;
        private final LogReader logReader;
        private long expiration = -1;
        private boolean used = false;

        public LivePlayLog(JsonObject instanceInfo, Path worldPath, Path path) {
            this.instanceInfo = instanceInfo;
            this.worldPath = worldPath;
            this.logReader = new LogReader(path);
        }

        public boolean hasExpired(long currentTime) {
            return this.expiration != -1 && this.expiration < currentTime;
        }
    }

    private static class WorldLogEntry {
        @SerializedName("world")
        public final JsonObject worldPath;
        public final String type;
        public final long time;

        public WorldLogEntry(JsonObject worldPath, String type, long time) {
            this.worldPath = worldPath;
            this.type = type;
            this.time = time;
        }
    }
}
