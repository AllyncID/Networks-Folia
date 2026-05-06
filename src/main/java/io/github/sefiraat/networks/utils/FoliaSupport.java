package io.github.sefiraat.networks.utils;

import javax.annotation.Nonnull;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;

public final class FoliaSupport {

    private FoliaSupport() {
    }

    public static void executeRegion(@Nonnull Plugin plugin, @Nonnull Location location, @Nonnull Runnable runnable) {
        plugin.getServer().getRegionScheduler().execute(plugin, location, runnable);
    }

    public static boolean isOwnedByCurrentRegion(@Nonnull Location location) {
        return location.getWorld() != null && Bukkit.isOwnedByCurrentRegion(location);
    }

    public static boolean executeEntity(@Nonnull Plugin plugin, @Nonnull Entity entity, @Nonnull Runnable runnable) {
        return entity.getScheduler().execute(plugin, runnable, null, 1L);
    }

    public static void runGlobalLater(@Nonnull Plugin plugin, long delayTicks, @Nonnull Runnable runnable) {
        plugin.getServer().getGlobalRegionScheduler().runDelayed(
            plugin,
            task -> runnable.run(),
            Math.max(0L, delayTicks)
        );
    }
}
