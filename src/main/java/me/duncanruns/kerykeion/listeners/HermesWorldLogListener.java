package me.duncanruns.kerykeion.listeners;

import com.google.gson.JsonObject;

public interface HermesWorldLogListener extends KerykeionListener {
    /**
     * @param instanceInfo The instance info file contents
     * @param entry        The world log entry
     * @param isNew        Whether this is a new entry or was already in the log when Kerykeion started
     */
    void onWorldLogEntry(JsonObject instanceInfo, JsonObject entry, boolean isNew);
}
