package me.duncanruns.kerykeion.listeners;

import com.google.gson.JsonObject;

import java.util.concurrent.Executor;

public interface HermesInstanceListener extends KerykeionListener {
    static HermesInstanceListener wrap(HermesInstanceListener listener, Executor executor) {
        if (executor == null) return listener;
        return new HermesInstanceListener() {
            @Override
            public void onNewInstance(JsonObject instanceInfo, boolean isNew) {
                executor.execute(() -> listener.onNewInstance(instanceInfo, isNew));
            }

            @Override
            public void onInstanceClosed(JsonObject instanceInfo) {
                executor.execute(() -> listener.onInstanceClosed(instanceInfo));
            }
        };
    }

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
