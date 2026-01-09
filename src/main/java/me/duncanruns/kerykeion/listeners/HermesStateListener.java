package me.duncanruns.kerykeion.listeners;

import com.google.gson.JsonObject;

import java.util.concurrent.Executor;

public interface HermesStateListener extends KerykeionListener {
    /**
     * @param instanceInfo The instance info file contents
     * @param state        The state file contents
     */
    void onInstanceStateChange(JsonObject instanceInfo, JsonObject state);

    static HermesStateListener wrap(HermesStateListener listener, Executor executor) {
        if(executor == null) return listener;
        return (instanceInfo, state) -> executor.execute(() -> listener.onInstanceStateChange(instanceInfo, state));
    }
}
