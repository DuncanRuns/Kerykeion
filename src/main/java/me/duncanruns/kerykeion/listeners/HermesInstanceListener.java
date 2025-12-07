package me.duncanruns.kerykeion.listeners;

import com.google.gson.JsonObject;

public interface HermesInstanceListener extends KerykeionListener {
    /**
     * @param instanceInfo The instance info file contents
     * @param isNew        Whether this is a new instance or was already opened when Kerykeion started
     */
    void onNewInstance(JsonObject instanceInfo, boolean isNew);

    /**
     * @param instanceInfo The instance info file contents
     */
    void onInstanceClosed(JsonObject instanceInfo);
}
