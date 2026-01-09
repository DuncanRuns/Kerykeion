package me.duncanruns.kerykeion;

import com.google.gson.JsonObject;

import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

class WorldLogTracker {
    private final Map<HermesInstance, JsonLogReader> worldLogs = new HashMap<>();

    public void tick(Collection<HermesInstance> instances, Consumer<EntryInfo> consumer) {
        this.worldLogs.keySet().removeIf(i -> {
            if (instances.contains(i)) return false;
            this.worldLogs.get(i).close();
            return true;
        });

        for (HermesInstance instance : instances) {
            JsonLogReader worldLog = this.worldLogs.computeIfAbsent(
                    instance,
                    i -> Optional.ofNullable(instance.getWorldLogPath()).map(JsonLogReader::new).orElse(null)
            );
            if (worldLog == null) continue;
            try {
                worldLog.read(e -> consumer.accept(new EntryInfo(e.entry, instance, e.isNew)));
            } catch (IOException e) {
                worldLog.close();
                this.worldLogs.remove(instance);
                Kerykeion.errorLogger.accept("Failed to read world log", e);
            }
        }
    }

    static class EntryInfo {
        final JsonObject entry;
        final HermesInstance instance;
        final boolean isNew;

        public EntryInfo(JsonObject entry, HermesInstance instance, boolean isNew) {
            this.entry = entry;
            this.instance = instance;
            this.isNew = isNew;
        }
    }
}
