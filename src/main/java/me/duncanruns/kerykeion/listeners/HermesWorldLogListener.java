package me.duncanruns.kerykeion.listeners;

import com.google.gson.JsonObject;

import java.util.concurrent.Executor;

public interface HermesWorldLogListener extends KerykeionListener {
    static HermesWorldLogListener wrap(HermesWorldLogListener listener, Executor executor) {
        if (executor == null) return listener;
        return (instanceInfo, entry, isNew) -> executor.execute(() -> listener.onWorldLogEntry(instanceInfo, entry, isNew));
    }

    /**
     * @param instanceInfo The instance info file contents
     * @param entry        The world log entry
     * @param isNew        Whether this is a new entry or was already in the log when Kerykeion started
     */
    void onWorldLogEntry(JsonObject instanceInfo, JsonObject entry, boolean isNew);
}
