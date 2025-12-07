package me.duncanruns.kerykeion;

import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import com.google.gson.annotations.SerializedName;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

class HermesInstance {
    private final JsonObject json;
    private final InstanceInfo instanceInfo;
    private final Path infoFilePath;

    final long infoFileLastModified;

    boolean closing = false;
    RandomAccessFile aliveFile = null;

    public HermesInstance(JsonObject json, long mTime, Path infoFilePath) {
        this.json = json;
        this.infoFileLastModified = mTime;
        this.infoFilePath = infoFilePath;
        this.instanceInfo = tryCreateInstanceInfo(json);
    }

    private static InstanceInfo tryCreateInstanceInfo(JsonObject json) {
        InstanceInfo instanceInfo = null;
        try {
            instanceInfo = Kerykeion.GSON.fromJson(json, InstanceInfo.class);
        } catch (JsonSyntaxException | InvalidPathException e) {
            Kerykeion.errorLogger.accept("Failed to parse instance info", e);
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

    public InstanceInfo getInstanceInfo() {
        return this.instanceInfo;
    }

    /**
     * An instance being closed does not necessarily mean that the actual Minecraft is closed, just this object
     * representing it is.
     *
     * @return true if the instance is closing or closed, false otherwise.
     */
    public boolean isClosing() {
        return this.closing;
    }

    void destroy() {
        this.close();
        try {
            Files.deleteIfExists(this.infoFilePath);
        } catch (IOException e) {
            Kerykeion.errorLogger.accept("Failed to delete instance info file", e);
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
            Kerykeion.errorLogger.accept("Failed to close alive file", e);
        }
    }

    boolean shouldDestroy() {
        return !this.closing && this.getGameDir() != null && this.instanceInfo.pid != null && !this.isAliveFileValid();
    }

    private boolean isAliveFileValid() {
        assert this.getGameDir() != null;
        assert this.instanceInfo.pid != null;
        Path alivePath = this.getGameDir().resolve("hermes").resolve("alive");
        if (!Files.exists(alivePath)) return false;
        try {
            if (this.aliveFile == null) {
                this.aliveFile = new RandomAccessFile(alivePath.toFile(), "r");
                this.aliveFile.seek(0);
                long l = this.aliveFile.readLong();
                if (l != this.instanceInfo.pid) return false;
            }
            this.aliveFile.seek(8);
            return Math.abs(System.currentTimeMillis() - this.aliveFile.readLong()) < 5000;
        } catch (IOException e) {
            Kerykeion.errorLogger.accept("Failed to read alive file", e);
            return false;
        }
    }

    public Path getWorldLogPath() {
        return KerykeionUtil.resolveGameDirRelativePath(this.getGameDir(), this.instanceInfo.worldLogRelPath);
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
