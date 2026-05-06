package io.github.sefiraat.networks.utils;

import io.github.sefiraat.networks.Networks;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public final class PersistentNetworkMetadata {

    private static final String FILE_NAME = "network-metadata.yml";
    private static final long SAVE_DELAY_MILLIS = 250L;
    private static final Object LOCK = new Object();

    private static File file;
    private static Map<String, Map<String, String>> metadataByLocation;
    private static long modificationVersion;
    private static long persistedVersion;
    private static boolean saveScheduled;

    private PersistentNetworkMetadata() {
    }

    @Nullable
    public static String getString(@Nonnull Location location, @Nonnull String key) {
        synchronized (LOCK) {
            return getMetadataByLocation()
                .getOrDefault(getLocationPath(location), Collections.emptyMap())
                .get(key);
        }
    }

    public static void setString(@Nonnull Location location, @Nonnull String key, @Nullable String value) {
        synchronized (LOCK) {
            final Map<String, Map<String, String>> metadata = getMetadataByLocation();
            final String locationPath = getLocationPath(location);
            final Map<String, String> locationValues = metadata.get(locationPath);

            if (value == null) {
                if (locationValues == null || locationValues.remove(key) == null) {
                    return;
                }

                if (locationValues.isEmpty()) {
                    metadata.remove(locationPath);
                }
            } else {
                final Map<String, String> targetValues = locationValues == null
                    ? metadata.computeIfAbsent(locationPath, ignored -> new HashMap<>())
                    : locationValues;
                final String currentValue = targetValues.get(key);

                if (value.equals(currentValue)) {
                    return;
                }

                targetValues.put(key, value);
            }

            markDirtyAndScheduleSaveLocked();
        }
    }

    public static void clearLocation(@Nonnull Location location) {
        synchronized (LOCK) {
            if (getMetadataByLocation().remove(getLocationPath(location)) != null) {
                markDirtyAndScheduleSaveLocked();
            }
        }
    }

    public static void initialize() {
        synchronized (LOCK) {
            getMetadataByLocation();
        }
    }

    public static void flushNow() {
        final MetadataSnapshot snapshot;

        synchronized (LOCK) {
            saveScheduled = false;
            snapshot = createSnapshotIfDirtyLocked();
        }

        if (snapshot == null) {
            return;
        }

        if (saveSnapshot(snapshot)) {
            synchronized (LOCK) {
                persistedVersion = Math.max(persistedVersion, snapshot.version);
            }
        }
    }

    @Nonnull
    private static File getFile() {
        if (file == null) {
            final File dataFolder = Networks.getInstance().getDataFolder();
            if (!dataFolder.exists()) {
                dataFolder.mkdirs();
            }

            file = new File(dataFolder, FILE_NAME);
        }

        return file;
    }

    @Nonnull
    private static Map<String, Map<String, String>> getMetadataByLocation() {
        if (metadataByLocation == null) {
            metadataByLocation = loadMetadata();
        }

        return metadataByLocation;
    }

    @Nonnull
    private static Map<String, Map<String, String>> loadMetadata() {
        final YamlConfiguration yaml = YamlConfiguration.loadConfiguration(getFile());
        final Map<String, Map<String, String>> loadedMetadata = new HashMap<>();

        for (Map.Entry<String, Object> entry : yaml.getValues(true).entrySet()) {
            if (!(entry.getValue() instanceof String stringValue)) {
                continue;
            }

            final String path = entry.getKey();
            final int separatorIndex = path.lastIndexOf('.');

            if (separatorIndex <= 0 || separatorIndex >= path.length() - 1) {
                continue;
            }

            final String locationPath = path.substring(0, separatorIndex);
            final String key = path.substring(separatorIndex + 1);
            loadedMetadata
                .computeIfAbsent(locationPath, ignored -> new HashMap<>())
                .put(key, stringValue);
        }

        return loadedMetadata;
    }

    private static void markDirtyAndScheduleSaveLocked() {
        modificationVersion++;
        scheduleSaveLocked();
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
        final MetadataSnapshot snapshot;

        synchronized (LOCK) {
            saveScheduled = false;
            snapshot = createSnapshotIfDirtyLocked();
        }

        if (snapshot == null) {
            return;
        }

        final boolean saved = saveSnapshot(snapshot);

        synchronized (LOCK) {
            if (saved) {
                persistedVersion = Math.max(persistedVersion, snapshot.version);
            }

            if (persistedVersion < modificationVersion) {
                scheduleSaveLocked();
            }
        }
    }

    @Nullable
    private static MetadataSnapshot createSnapshotIfDirtyLocked() {
        if (metadataByLocation == null || modificationVersion == persistedVersion) {
            return null;
        }

        final Map<String, Map<String, String>> snapshot = new HashMap<>(metadataByLocation.size());
        for (Map.Entry<String, Map<String, String>> entry : metadataByLocation.entrySet()) {
            snapshot.put(entry.getKey(), new HashMap<>(entry.getValue()));
        }

        return new MetadataSnapshot(snapshot, modificationVersion);
    }

    private static boolean saveSnapshot(@Nonnull MetadataSnapshot snapshot) {
        final YamlConfiguration yaml = new YamlConfiguration();

        for (Map.Entry<String, Map<String, String>> entry : snapshot.values.entrySet()) {
            final String locationPath = entry.getKey();
            for (Map.Entry<String, String> valueEntry : entry.getValue().entrySet()) {
                yaml.set(locationPath + '.' + valueEntry.getKey(), valueEntry.getValue());
            }
        }

        try {
            yaml.save(getFile());
            return true;
        } catch (IOException exception) {
            Networks.getInstance().getLogger().warning("Failed to save persistent Networks metadata: " + exception.getMessage());
            return false;
        }
    }

    @Nonnull
    private static String getLocationPath(@Nonnull Location location) {
        final World world = location.getWorld();
        final String worldName = world == null ? "unknown" : world.getName();
        final String encodedWorld = Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(worldName.getBytes(StandardCharsets.UTF_8));

        return "blocks."
            + encodedWorld
            + '.'
            + location.getBlockX() + '_'
            + location.getBlockY() + '_'
            + location.getBlockZ();
    }

    private static final class MetadataSnapshot {

        private final Map<String, Map<String, String>> values;
        private final long version;

        private MetadataSnapshot(@Nonnull Map<String, Map<String, String>> values, long version) {
            this.values = values;
            this.version = version;
        }
    }
}