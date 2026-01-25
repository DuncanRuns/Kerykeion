package me.duncanruns.kerykeion.listeners;

import com.google.gson.JsonObject;
import me.duncanruns.kerykeion.KerykeionUtil;

import java.util.concurrent.Executor;

public interface HermesStateListener extends KerykeionListener {
    static HermesStateListener wrap(HermesStateListener listener, Executor executor) {
        if (executor == null) return listener;
        return (instanceInfo, state) -> KerykeionUtil.executeIgnore(executor, () -> listener.onInstanceStateChange(instanceInfo, state));
    }

    /**
     * @param instanceInfo The instance info file contents
     * @param state        The state file contents
     */
    void onInstanceStateChange(JsonObject instanceInfo, JsonObject state);
}
