package me.duncanruns.kerykeion;

import com.google.gson.JsonObject;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

public class KerykeionUtil {
    public static Path resolveGameDirRelativePath(Path gameDir, JsonObject relPathObj) {
        if (relPathObj == null) return null;
        boolean relative = relPathObj.get("relative").getAsBoolean();
        String pathString = relPathObj.get("path").getAsString();
        if (relative) {
            return gameDir.resolve(pathString);
        } else {
            return Paths.get(pathString);
        }
    }

    /**
     * Executes the given runnable on the given executor, ignoring any RejectedExecutionException.
     */
    public static void executeIgnore(Executor executor, Runnable runnable){
        try {
            executor.execute(runnable);
        } catch (RejectedExecutionException ignored) {
        }
    }
}
