package io.github.sefiraat.networks.utils;

import javax.annotation.Nonnull;

import org.bukkit.Location;
import org.bukkit.plugin.Plugin;

public final class FoliaSupport {

    private FoliaSupport() {
    }

    public static void executeRegion(@Nonnull Plugin plugin, @Nonnull Location location, @Nonnull Runnable runnable) {
        plugin.getServer().getRegionScheduler().execute(plugin, location, runnable);
    }

    public static void runGlobalLater(@Nonnull Plugin plugin, long delayTicks, @Nonnull Runnable runnable) {
        plugin.getServer().getGlobalRegionScheduler().runDelayed(
            plugin,
            task -> runnable.run(),
            Math.max(0L, delayTicks)
        );
    }
}
