package io.github.sefiraat.networks.utils;

import io.github.sefiraat.networks.Networks;
import me.mrCookieSlime.Slimefun.api.BlockStorage;
import org.bukkit.Location;
import org.bukkit.World;

import javax.annotation.Nonnull;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public final class DeferredBlockStorageSave {

    private static final long SAVE_DELAY_MILLIS = 2000L;
    private static final Object LOCK = new Object();

    private static final Set<World> DIRTY_WORLDS = new HashSet<>();
    private static boolean saveScheduled;

    private DeferredBlockStorageSave() {
    }

    public static void markDirty(@Nonnull Location location) {
        final World world = location.getWorld();
        if (world == null) {
            return;
        }

        synchronized (LOCK) {
            DIRTY_WORLDS.add(world);
            scheduleSaveLocked();
        }
    }

    public static void flushNow() {
        final Set<World> snapshot;

        synchronized (LOCK) {
            saveScheduled = false;
            snapshot = drainDirtyWorldsLocked();
        }

        if (snapshot.isEmpty()) {
            return;
        }

        final Set<World> failedWorlds = saveWorlds(snapshot);

        synchronized (LOCK) {
            DIRTY_WORLDS.addAll(failedWorlds);
            if (!DIRTY_WORLDS.isEmpty()) {
                scheduleSaveLocked();
            }
        }
    }

    private static void scheduleSaveLocked() {
        if (saveScheduled) {
            return;
        }

        saveScheduled = true;
        Networks.getInstance().getServer().getAsyncScheduler().runDelayed(
            Networks.getInstance(),
            task -> flushAsync(),
            SAVE_DELAY_MILLIS,
            TimeUnit.MILLISECONDS
        );
    }

    private static void flushAsync() {
        final Set<World> snapshot;

        synchronized (LOCK) {
            saveScheduled = false;
            snapshot = drainDirtyWorldsLocked();
        }

        if (snapshot.isEmpty()) {
            return;
        }

        final Set<World> failedWorlds = saveWorlds(snapshot);

        synchronized (LOCK) {
            DIRTY_WORLDS.addAll(failedWorlds);
            if (!DIRTY_WORLDS.isEmpty()) {
                scheduleSaveLocked();
            }
        }
    }

    @Nonnull
    private static Set<World> drainDirtyWorldsLocked() {
        if (DIRTY_WORLDS.isEmpty()) {
            return Set.of();
        }

        final Set<World> snapshot = new HashSet<>(DIRTY_WORLDS);
        DIRTY_WORLDS.clear();
        return snapshot;
    }

    @Nonnull
    private static Set<World> saveWorlds(@Nonnull Set<World> worlds) {
        final Set<World> failedWorlds = new HashSet<>();

        for (World world : worlds) {
            final BlockStorage storage = BlockStorage.getStorage(world);
            if (storage == null) {
                continue;
            }

            try {
                storage.save();
            } catch (RuntimeException exception) {
                failedWorlds.add(world);
                Networks.getInstance().getLogger().warning(
                    "Failed to flush Slimefun block storage for world '" + world.getName() + "': " + exception.getMessage()
                );
            }
        }

        return failedWorlds;
    }
}