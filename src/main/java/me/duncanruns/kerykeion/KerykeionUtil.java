package me.duncanruns.kerykeion;

import com.google.gson.JsonObject;

import java.nio.file.Path;
import java.nio.file.Paths;

public class KerykeionUtil {
    public static Path resolveGameDirRelativePath(Path gameDir, JsonObject relPathObj) {
        boolean relative = relPathObj.get("relative").getAsBoolean();
        String pathString = relPathObj.get("path").getAsString();
        if (relative) {
            return gameDir.resolve(pathString);
        } else {
            return Paths.get(pathString);
        }
    }
}
