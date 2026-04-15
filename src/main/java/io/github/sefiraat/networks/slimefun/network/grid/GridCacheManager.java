package io.github.sefiraat.networks.slimefun.network.grid;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class GridCacheManager {

    private static final Set<GridCache> activeCaches = ConcurrentHashMap.newKeySet();

    public static void addCache(GridCache cache) {
        activeCaches.add(cache);
    }

    public static void removeCache(GridCache cache) {
        activeCaches.remove(cache);
    }

    public static void markAllCachesDirty() {
        for (GridCache cache : activeCaches) {
            cache.setDirty(true);
        }
    }
}
