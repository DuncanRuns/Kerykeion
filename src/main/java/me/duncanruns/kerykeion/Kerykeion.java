package me.duncanruns.kerykeion;

import com.google.gson.Gson;
import me.duncanruns.kerykeion.listeners.*;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

@SuppressWarnings("unused")
public final class Kerykeion {
    public static final Gson GSON = new Gson();

    private static final List<HermesInstanceListener> instanceListeners = new ArrayList<>();
    private static final List<HermesStateListener> stateListeners = new ArrayList<>();
    private static final List<HermesWorldLogListener> worldLogListeners = new ArrayList<>();
    private static final List<HermesRestrictedPlayLogListener> livePlayLogListeners = new ArrayList<>();
    private static long tickInterval = Long.MAX_VALUE;

    static BiConsumer<String, Throwable> errorLogger = (s, throwable) -> System.err.println(s + "\n" + throwable);


    private static boolean started = false; // has been started at any point in the past even if it was stopped
    private static volatile boolean stopped = false;
    private static final AtomicBoolean shouldRun = new AtomicBoolean(true);

    private static final InstanceTracker instanceTracker = new InstanceTracker();
    private static final WorldLogTracker worldLogTracker = new WorldLogTracker();
    private static final StateTracker stateTracker = new StateTracker();
    private static final LivePlayLogTracker livePlayLogTracker = new LivePlayLogTracker();
    private static long lastInstanceCheck = 0;

    private Kerykeion() {
    }

    /**
     * Adds a listener to Kerykeion. Listeners cannot be added after Kerykeion has been started.
     * <p>
     * The tick interval that Kerykeion will use is the minimum of all required tick intervals, so in an environment
     * such as a Jingle plugin, Kerykeion's tick interval may be lower than expected due to another plugin, so be
     * careful not to rely on the tick interval being what you expect.
     * <p>
     * Instance listening will only be run at minimum every 1000ms, regardless of the used tick interval.
     * <p>
     * It is highly recommended to offload to an executor to avoid blocking Kerykeion's thread.
     *
     * @param listener             The listener to add
     * @param requiredTickInterval The tick interval required by this listener in milliseconds
     * @param executor             The executor to use for this listener, can be null to listen on Kerykeion's thread
     * @throws IllegalStateException    if Kerykeion has already been started
     * @throws IllegalArgumentException if the listener is not one of the more specific interfaces
     */
    public static synchronized void addListener(KerykeionListener listener, long requiredTickInterval, Executor executor) {
        if (started) {
            throw new IllegalStateException("Kerykeion already started, listeners need to be registered earlier!");
        }
        boolean matched = false;
        if (listener instanceof HermesInstanceListener) {
            instanceListeners.add(HermesInstanceListener.wrap((HermesInstanceListener) listener, executor));
            matched = true;
        }
        if (listener instanceof HermesStateListener) {
            stateListeners.add(HermesStateListener.wrap((HermesStateListener) listener, executor));
            matched = true;
        }
        if (listener instanceof HermesWorldLogListener) {
            worldLogListeners.add(HermesWorldLogListener.wrap((HermesWorldLogListener) listener, executor));
            matched = true;
        }
        if (listener instanceof HermesRestrictedPlayLogListener) {
            livePlayLogListeners.add(HermesRestrictedPlayLogListener.wrap((HermesRestrictedPlayLogListener) listener, executor));
            if (!worldLogListeners.contains(livePlayLogTracker)) {
                worldLogListeners.add(livePlayLogTracker);
            }
            matched = true;
        }
        if (!matched) {
            throw new IllegalArgumentException("Unknown listener type! Please implement one of the more specific interfaces.");
        }
        requireTickInterval(requiredTickInterval);
    }

    private static void requireTickInterval(long requiredTickInterval) {
        tickInterval = Math.min(Math.max(requiredTickInterval, 1), tickInterval);
    }

    /**
     * Starts Kerykeion. Listeners need to be added before this is called. The first tick will happen after the tick interval.
     */
    public static void start() {
        start(false);
    }

    /**
     * Starts Kerykeion. Listeners need to be added before this is called. The first tick will happen after the tick interval.
     *
     * @param tickOnce If true, Kerykeion will tick once on the calling thread before starting the scheduled ticks.
     */
    public static synchronized void start(boolean tickOnce) {
        if (started) return;

        if (instanceListeners.isEmpty() && stateListeners.isEmpty() && worldLogListeners.isEmpty() && livePlayLogListeners.isEmpty()) {
            throw new IllegalStateException("No listeners added! Add at least one listener before starting!");
        }

        if (tickOnce) {
            tick();
        }
        new Thread(Kerykeion::mainLoop, "Kerykeion").start();
        started = true;
    }

    @SuppressWarnings("BusyWait")
    private static void mainLoop() {
        try {
            while (shouldRun.get()) {
                tick();
                try {
                    Thread.sleep(tickInterval);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        } catch (Throwable t) {
            errorLogger.accept("Kerykeion encountered an error and will stop!", t);
            throw t;
        } finally {
            stopped = true;
            shouldRun.set(false);
        }
    }

    private static synchronized void tick() {
        long l = System.currentTimeMillis();
        if (Math.abs(l - lastInstanceCheck) > 950) {
            lastInstanceCheck = l;
            tickInstance();
        }
        if (!worldLogListeners.isEmpty()) {
            tickWorldLog();
        }
        if (!stateListeners.isEmpty()) {
            tickState();
        }
        if (!livePlayLogListeners.isEmpty()) {
            tickLivePlayLog();
        }
    }

    private static void tickLivePlayLog() {
        livePlayLogTracker.tick(entryInfo -> livePlayLogListeners.forEach(lis -> lis.onLivePlayLogEntry(entryInfo.instanceInfo, entryInfo.worldPath, entryInfo.line.clone())));
    }

    private static void tickState() {
        StateTracker.TickResult result = stateTracker.tick(instanceTracker.getInstances());
        result.entries.forEach(e -> stateListeners.forEach(lis -> lis.onInstanceStateChange(e.instance.getInstanceInfoJson(), e.entry)));
    }

    private static void tickInstance() {
        InstanceTracker.TickResult result = instanceTracker.tick();
        instanceListeners.forEach(lis -> {
            result.existingInstances.forEach(i -> lis.onNewInstance(i, false));
            result.newInstances.forEach(i -> lis.onNewInstance(i, true));
            result.closedInstances.forEach(lis::onInstanceClosed);
        });
    }

    private static void tickWorldLog() {
        worldLogTracker.tick(
                instanceTracker.getInstances(),
                entryInfo -> worldLogListeners.forEach(
                        lis -> lis.onWorldLogEntry(
                                entryInfo.instance.getInstanceInfoJson(),
                                entryInfo.entry,
                                entryInfo.isNew
                        )
                )
        );
    }

    public static synchronized boolean stop() {
        if (!started) return false;
        shouldRun.set(false);
        int tries;
        for (tries = 0; tries < 100 && !stopped; tries++) {
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
        return tries < 100;
    }

    public static boolean hasStarted() {
        return started;
    }

    public static boolean hasStopped() {
        return stopped;
    }

    /**
     * @author me-nx, DuncanRuns
     */
    public static Path getHermesGlobalPath() {
        String osName = System.getProperty("os.name").toLowerCase(Locale.ROOT);
        if (osName.contains("win")) {
            for (Supplier<String> possibleEnv : Arrays.<Supplier<String>>asList(
                    () -> System.getenv("LOCALAPPDATA"),
                    () -> System.getenv("APPDATA"),
                    () -> System.getProperty("user.home")
            )) {
                String base = possibleEnv.get();
                if (base == null) continue;
                return Paths.get(base, "MCSRHermes");
            }
            throw new RuntimeException("Failed to find a suitable path for Hermes");
        } else if (osName.contains("mac")) {
            return Paths.get(System.getProperty("user.home"), "Library", "Application Support", "MCSRHermes");
        } else if (osName.contains("linux") || osName.contains("unix")) {
            return Optional.ofNullable(System.getenv("XDG_RUNTIME_DIR"))
                    .map((runtimeDir) -> Paths.get(runtimeDir, "MCSRHermes"))
                    .orElse(Paths.get(System.getProperty("user.home"), ".local", "share", "MCSRHermes"));
        } else {
            return Paths.get(System.getProperty("user.home"), "MCSRHermes");
        }
    }

    /**
     * Sets the error logger used for Kerykeion.
     * Should be set if Kerykeion is used in an environment with proper logging.
     */
    public static void setErrorLogger(BiConsumer<String, Throwable> errorLogger) {
        Kerykeion.errorLogger = errorLogger;
    }
}
