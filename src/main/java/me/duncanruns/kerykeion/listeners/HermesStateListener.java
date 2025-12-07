package me.duncanruns.kerykeion.listeners;

import com.google.gson.JsonObject;

public interface HermesStateListener extends KerykeionListener {
    /**
     * @param instanceInfo The instance info file contents
     * @param state        The state file contents
     */
    void onInstanceStateChange(JsonObject instanceInfo, JsonObject state);
}
