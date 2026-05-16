package me.duncanruns.kerykeion;

import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import com.google.gson.annotations.SerializedName;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static me.duncanruns.kerykeion.Kerykeion.GSON;
import static me.duncanruns.kerykeion.Kerykeion.errorLogger;

class HermesInstance {
    private final JsonObject json;
    private final InstanceInfo instanceInfo;
    final Path infoFilePath;
    private final boolean deadFile;

    final long infoFileLastModified;

    boolean closing = false;
    RandomAccessFile aliveFile = null;
    boolean consideredOpen = false;


    public static HermesInstance create(Path infoFilePath) {
        String contents;
        long millis;
        try {
            millis = Files.getLastModifiedTime(infoFilePath).toMillis();
            contents = new String(Files.readAllBytes(infoFilePath), Charset.defaultCharset());
        } catch (IOException e) {
            errorLogger.accept("Failed to read instance info file", e);
            return null;
        }
        JsonObject json;
        try {
            json = GSON.fromJson(contents, JsonObject.class);
            if (json == null) throw new IllegalStateException("Json parsed into null (invalid json)");
        } catch (Exception e) {
            errorLogger.accept("Failed to parse instance info file", e);
            return new HermesInstance(infoFilePath, millis);
        }
        return new HermesInstance(json, millis, infoFilePath);
    }

    public HermesInstance(JsonObject json, long mTime, Path infoFilePath) {
        this.json = json;
        this.instanceInfo = tryCreateInstanceInfo(json);
        this.infoFileLastModified = mTime;
        this.infoFilePath = infoFilePath;
        this.deadFile = false;
    }

    public HermesInstance(Path infoFilePath, long mTime) {
        this.json = null;
        this.instanceInfo = null;
        this.infoFileLastModified = mTime;
        this.infoFilePath = infoFilePath;
        this.deadFile = true;
    }

    private static InstanceInfo tryCreateInstanceInfo(JsonObject json) {
        InstanceInfo instanceInfo = null;
        try {
            instanceInfo = Kerykeion.GSON.fromJson(json, InstanceInfo.class);
        } catch (JsonSyntaxException | InvalidPathException e) {
            errorLogger.accept("Failed to parse instance info", e);
        }
        return instanceInfo;
    }


    public JsonObject getInstanceInfoJson() {
        return this.json.deepCopy();
    }

    public Path getGameDir() {
        try {
            return Paths.get(this.instanceInfo.gameDir);
        } catch (InvalidPathException e) {
            return null;
        }
    }

    void close() {
        synchronized (this) {
            if (this.closing) return;
            this.closing = true;
        }
        try {
            if (this.aliveFile != null) {
                this.aliveFile.close();
            }
        } catch (IOException e) {
            errorLogger.accept("Failed to close alive file", e);
        }
    }

    public boolean isAliveFileValid() {
        assert this.getGameDir() != null;
        assert this.instanceInfo.pid != null;
        Path alivePath = this.getGameDir().resolve("hermes").resolve("alive");
        if (!Files.exists(alivePath)) return false;
        try {
            if (this.aliveFile == null) {
                this.aliveFile = new RandomAccessFile(alivePath.toFile(), "r");
            }
            this.aliveFile.seek(0);
            if (this.aliveFile.readLong() != this.instanceInfo.pid) return false;
            return Math.abs(System.currentTimeMillis() - this.aliveFile.readLong()) < 10000;
        } catch (IOException e) {
            errorLogger.accept("Failed to read alive file", e);
            return false;
        }
    }

    public Path getWorldLogPath() {
        return KerykeionUtil.resolveGameDirRelativePath(this.getGameDir(), this.instanceInfo.worldLogRelPath);
    }

    public void reconsider() {
        this.consideredOpen = (!this.deadFile) && (!this.closing) && this.isAliveFileValid();
    }

    public boolean infoFileHasBeenModified() {
        if (!Files.exists(this.infoFilePath)) return false;
        try {
            return Files.getLastModifiedTime(this.infoFilePath).toMillis() != this.infoFileLastModified;
        } catch (IOException e) {
            errorLogger.accept("Failed to get last modified time of info file", e);
        }
        return false;
    }

    public static class InstanceInfo {
        public final Long pid;
        @SerializedName("game_dir")
        public final String gameDir;
        @SerializedName("game_version")
        public final String gameVersion;
        @SerializedName("world_log")
        public final JsonObject worldLogRelPath;
        @SerializedName("is_server")
        public final Boolean isServer;
        public final List<Mod> mods;

        public InstanceInfo(Long pid, String gameDir, String gameVersion, JsonObject worldLogRelPath, Boolean isServer, List<Mod> mods) {
            this.pid = pid;
            this.gameDir = gameDir;
            this.gameVersion = gameVersion;
            this.worldLogRelPath = worldLogRelPath;
            this.isServer = isServer;
            this.mods = mods;
        }

        public static class Mod {
            //name, id, version
            public final String name;
            public final String id;
            public final String version;

            public Mod(String name, String id, String version) {
                this.name = name;
                this.id = id;
                this.version = version;
            }
        }
    }
}
