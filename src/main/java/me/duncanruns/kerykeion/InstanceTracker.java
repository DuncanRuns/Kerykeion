package me.duncanruns.kerykeion;

import com.google.gson.JsonObject;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static me.duncanruns.kerykeion.Kerykeion.GSON;
import static me.duncanruns.kerykeion.Kerykeion.errorLogger;

class InstanceTracker {
    private static final Path HERMES_GLOBAL_INSTANCES_PATH = Kerykeion.getHermesGlobalPath().resolve("instances");
    private final Map<String, HermesInstance> instanceMap = new HashMap<>();
    private boolean firstTick = true;

    public TickResult tick() {
        TickResult result = new TickResult();
        this.checkInstancesFolder(result);
        this.firstTick = false;
        return result;
    }

    private void checkInstancesFolder(TickResult result) {
        try (Stream<Path> list = Files.list(HERMES_GLOBAL_INSTANCES_PATH)) {
            List<Path> infoFiles = list
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".json"))
                    .collect(Collectors.toList());
            List<String> infoFileNames = infoFiles.stream().map(p -> p.getFileName().toString()).collect(Collectors.toList());
            for (Map.Entry<String, HermesInstance> e : this.instanceMap.entrySet()) {
                if (!infoFileNames.contains(e.getKey())) {
                    e.getValue().destroy();
                    this.instanceMap.remove(e.getKey());
                    result.closedInstances.add(e.getValue().getInstanceInfoJson());
                }
            }
            infoFiles.forEach(path -> this.checkoutInstanceInfoFile(path, result));
        } catch (Exception e) {
            errorLogger.accept("Failed to list instances folder", e);
        }
    }

    private void checkoutInstanceInfoFile(Path path, TickResult result) {
        String instanceInfoFileName = path.getFileName().toString();
        long mTime;
        try {
            mTime = Files.getLastModifiedTime(path).toMillis();
        } catch (IOException e) {
            errorLogger.accept("Failed to get last modified time of instance info file", e);
            return;
        }
        Optional<HermesInstance> existingInstance = Optional.ofNullable(this.instanceMap.get(instanceInfoFileName));
        long lastMTime = existingInstance.map(i -> i.infoFileLastModified).orElse(-1L);

        if (mTime == lastMTime) {
            if (existingInstance.isPresent() && existingInstance.get().shouldDestroy()) {
                existingInstance.get().destroy();
                this.instanceMap.remove(instanceInfoFileName);
                result.closedInstances.add(existingInstance.get().getInstanceInfoJson());
            }
            return;
        }
        existingInstance.ifPresent(i -> {
            i.close();
            this.instanceMap.remove(instanceInfoFileName);
            result.closedInstances.add(i.getInstanceInfoJson());
        });
        JsonObject json;
        try {
            String contents = new String(Files.readAllBytes(path));
            json = GSON.fromJson(contents, JsonObject.class);
        } catch (IOException e) {
            errorLogger.accept("Failed to read instance info file", e);
            return;
        }
        HermesInstance i = new HermesInstance(json, mTime, path);
        if (i.shouldDestroy()) {
            i.destroy();
            return;
        }
        this.instanceMap.put(instanceInfoFileName, i);
        if (this.firstTick) {
            result.existingInstances.add(i.getInstanceInfoJson());
        } else {
            result.newInstances.add(i.getInstanceInfoJson());
        }
    }

    public Collection<HermesInstance> getInstances() {
        return this.instanceMap.values();
    }

    static class TickResult {
        final List<JsonObject> existingInstances;
        final List<JsonObject> newInstances;
        final List<JsonObject> closedInstances;

        public TickResult() {
            this.existingInstances = new ArrayList<>();
            this.newInstances = new ArrayList<>();
            this.closedInstances = new ArrayList<>();
        }
    }
}
