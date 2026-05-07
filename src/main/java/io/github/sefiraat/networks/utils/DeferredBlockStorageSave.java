package io.github.sefiraat.networks.utils;

import io.github.sefiraat.networks.Networks;
import me.mrCookieSlime.Slimefun.api.BlockStorage;
import org.bukkit.Location;
import org.bukkit.World;

import javax.annotation.Nonnull;
import java.util.HashSet;
import java.util.Set;

public final class DeferredBlockStorageSave {

    private static final Object LOCK = new Object();

    private static final Set<World> DIRTY_WORLDS = new HashSet<>();

    private DeferredBlockStorageSave() {
    }

    public static void markDirty(@Nonnull Location location) {
        final World world = location.getWorld();
        if (world == null) {
            return;
        }

        synchronized (LOCK) {
            DIRTY_WORLDS.add(world);
        }
    }

    public static void flushNow() {
        final Set<World> snapshot;

        synchronized (LOCK) {
            snapshot = drainDirtyWorldsLocked();
        }

        if (snapshot.isEmpty()) {
            return;
        }

        final Set<World> failedWorlds = saveWorlds(snapshot);

        synchronized (LOCK) {
            DIRTY_WORLDS.addAll(failedWorlds);
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