package me.duncanruns.kerykeion;

import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;

import java.io.IOException;
import java.util.*;

class StateTracker {
    private static final Map<HermesInstance, UpdatingJsonFileReader> stateLogs = new HashMap<>();

    public TickResult tick(Collection<HermesInstance> instances) {
        TickResult tickResult = new TickResult();
        stateLogs.keySet().removeIf(i -> !instances.contains(i));
        for (HermesInstance instance : instances) {
            UpdatingJsonFileReader reader = stateLogs.computeIfAbsent(instance, i -> new UpdatingJsonFileReader(i.getGameDir().resolve("hermes").resolve("state.json")));
            try {
                reader.read().ifPresent(
                        json -> tickResult.entries.add(new TickResult.EntryWithInstance(json, instance))
                );
            } catch (IOException | JsonSyntaxException e) {
                Kerykeion.errorLogger.accept("Failed to read state log", e);
            }
        }
        return tickResult;
    }

    static class TickResult {
        final List<EntryWithInstance> entries;

        public TickResult() {
            this.entries = new ArrayList<>();
        }

        static class EntryWithInstance {
            final JsonObject entry;
            final HermesInstance instance;

            public EntryWithInstance(JsonObject entry, HermesInstance instance) {
                this.entry = entry;
                this.instance = instance;
            }
        }
    }
}
