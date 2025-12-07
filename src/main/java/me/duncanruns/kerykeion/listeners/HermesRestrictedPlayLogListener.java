package me.duncanruns.kerykeion.listeners;

import com.google.gson.JsonObject;

import java.nio.file.Path;

/**
 * This will enable live encrypted entries from the play log. speedrun.com/mc and /mcce do not allow tools to use
 * this data, encrypted or not. Kerykeion does not provide unencryption utilities.
 * <p>
 * If a player logs back into a previous world, the entire log will be re-read. Comparing times from the world log with
 * the time in the play log entry is recommended to determine if an entry is new.
 */
public interface HermesRestrictedPlayLogListener extends KerykeionListener {
    void onLivePlayLogEntry(JsonObject instanceInfo, Path worldPath, String line);
}
