package me.duncanruns.kerykeion;

import com.google.gson.JsonObject;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static me.duncanruns.kerykeion.Kerykeion.errorLogger;

class InstanceTracker {
    private static final Path HERMES_GLOBAL_INSTANCES_PATH = Kerykeion.getHermesGlobalPath().resolve("instances");
    private final Map<Path, HermesInstance> instanceMap = new HashMap<>();
    private final Set<HermesInstance> reportedInstances = new HashSet<>();
    private boolean firstTick = true;

    public TickResult tick() {
        TickResult result = new TickResult(this.firstTick);
        this.updateInstanceMap();
        this.updatedOpened(result);
        this.firstTick = false;
        return result;
    }

    private void updateInstanceMap() {
        if (!Files.isDirectory(HERMES_GLOBAL_INSTANCES_PATH)) return;
        Collection<Path> infoFilePaths = retrieveInfoFilePaths(t -> errorLogger.accept("Failed to list instances folder", t));
        if (infoFilePaths == null) return;
        this.closeAllExcept(infoFilePaths);
        infoFilePaths.stream()
                .filter(p -> !this.instanceMap.containsKey(p))
                .map(HermesInstance::create)
                .filter(Objects::nonNull)
                .forEach(i -> this.instanceMap.put(i.infoFilePath, i));
    }

    private void updatedOpened(TickResult result) {
        // Check each instance's alive file and update their consideredOpen field
        this.instanceMap.values().forEach(HermesInstance::reconsider);

        // Find all instances that are currently reported as opened that should be reported as closed
        List<HermesInstance> toRemove = new ArrayList<>();
        this.reportedInstances.forEach(i -> {
            if (!this.instanceMap.containsValue(i) || !i.consideredOpen) toRemove.add(i);
        });
        // Report them as closed, remove from list of reported instances
        toRemove.forEach(i -> {
            result.reportClosed(i);
            this.reportedInstances.remove(i);
        });

        // Find all instances that are currently not reported as opened that should be, report them and store them
        this.instanceMap.values().forEach(i -> {
            if (this.reportedInstances.contains(i)) return;
            if (i.consideredOpen) {
                result.reportOpen(i);
                this.reportedInstances.add(i);
            }
        });
    }

    private void closeAllExcept(Collection<Path> exclude) {
        this.instanceMap.forEach((p, hermesInstance) -> {
            if (exclude.contains(p)) return;
            hermesInstance.close();
        });
        this.instanceMap.entrySet().removeIf(e -> e.getValue().closing);
    }

    private static Collection<Path> retrieveInfoFilePaths(Consumer<Throwable> onFailure) {
        try (Stream<Path> list = Files.list(HERMES_GLOBAL_INSTANCES_PATH)) {
            return list.filter(path -> path.getFileName().toString().endsWith(".json")).collect(Collectors.toList());
        } catch (Exception e) {
            onFailure.accept(e);
            return null;
        }
    }


    public Collection<HermesInstance> getInstances() {
        return null;
    }

    static class TickResult {
        final List<JsonObject> existingInstances;
        final List<JsonObject> newInstances;
        final List<JsonObject> closedInstances;
        private final boolean firstTick;

        public TickResult(boolean firstTick) {
            this.firstTick = firstTick;
            if (firstTick) {
                this.newInstances = Collections.emptyList();
                this.existingInstances = new ArrayList<>();
            } else {
                this.newInstances = new ArrayList<>();
                this.existingInstances = Collections.emptyList();
            }
            this.closedInstances = new ArrayList<>();
        }

        public void reportClosed(HermesInstance i) {
            this.closedInstances.add(i.getInstanceInfoJson());
        }

        public void reportOpen(HermesInstance i) {
            (this.firstTick ? this.existingInstances : this.newInstances).add(i.getInstanceInfoJson());
        }
    }
}
