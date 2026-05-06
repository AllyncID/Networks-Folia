package io.github.sefiraat.networks.integrations;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Item;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public final class OrionStackerSupport {

    private static final String ORION_OPTIMIZE_PLUGIN = "OrionOptimize";
    private static final String STACKABLE_ITEM_DROP_KEY = "stackable_item_drop";
    private static final String LOGICAL_ITEM_AMOUNT_KEY = "stacker_item_logical_amount";
    private static final String STACKER_SERVICE_METHOD = "stackerService";
    private static final String UPDATE_ITEM_DISPLAY_METHOD = "updateItemDisplay";

    private OrionStackerSupport() {
    }

    @Nullable
    public static ItemStack takeVacuumStack(@Nonnull Item item) {
        if (!item.isValid() || item.isDead()) {
            return null;
        }

        final ItemStack currentStack = item.getItemStack();
        if (currentStack == null || currentStack.getType().isAir()) {
            return null;
        }

        final int currentPhysicalAmount = Math.max(1, currentStack.getAmount());
        final int physicalBatchSize = getPhysicalBatchSize(currentStack);
        final int logicalAmount = getLogicalAmount(item, currentStack);
        final int amountToTake = Math.max(1, Math.min(logicalAmount, Math.min(currentPhysicalAmount, physicalBatchSize)));

        final ItemStack extracted = currentStack.clone();
        extracted.setAmount(amountToTake);

        final int remainingLogicalAmount = logicalAmount - amountToTake;
        if (remainingLogicalAmount <= 0) {
            item.remove();
            return extracted;
        }

        final Plugin plugin = getOrionOptimizePlugin();
        if (isManagedByOrion(item, plugin)) {
            updateManagedOrionItem(item, currentStack, remainingLogicalAmount, plugin);
        } else {
            final ItemStack updated = currentStack.clone();
            updated.setAmount(remainingLogicalAmount);
            item.setItemStack(updated);
        }

        return extracted;
    }

    private static int getLogicalAmount(@Nonnull Item item, @Nonnull ItemStack itemStack) {
        final Plugin plugin = getOrionOptimizePlugin();
        if (isManagedByOrion(item, plugin)) {
            final PersistentDataContainer persistentDataContainer = item.getPersistentDataContainer();
            final Integer stored = persistentDataContainer.get(getLogicalAmountKey(plugin), PersistentDataType.INTEGER);
            if (stored != null && stored > 0) {
                return stored;
            }
        }

        return Math.max(1, itemStack.getAmount());
    }

    private static boolean isManagedByOrion(@Nonnull Item item, @Nullable Plugin plugin) {
        if (plugin == null || !plugin.isEnabled()) {
            return false;
        }

        final PersistentDataContainer persistentDataContainer = item.getPersistentDataContainer();
        return persistentDataContainer.has(getStackableItemDropKey(plugin), PersistentDataType.BYTE)
            || persistentDataContainer.has(getLogicalAmountKey(plugin), PersistentDataType.INTEGER);
    }

    private static void updateManagedOrionItem(
        @Nonnull Item item,
        @Nonnull ItemStack currentStack,
        int remainingLogicalAmount,
        @Nonnull Plugin plugin
    ) {
        final int physicalAmount = Math.min(getPhysicalBatchSize(currentStack), remainingLogicalAmount);
        if (currentStack.getAmount() != physicalAmount) {
            final ItemStack updated = currentStack.clone();
            updated.setAmount(physicalAmount);
            item.setItemStack(updated);
        }

        final PersistentDataContainer persistentDataContainer = item.getPersistentDataContainer();
        final NamespacedKey logicalAmountKey = getLogicalAmountKey(plugin);
        if (remainingLogicalAmount > physicalAmount) {
            persistentDataContainer.set(logicalAmountKey, PersistentDataType.INTEGER, remainingLogicalAmount);
        } else {
            persistentDataContainer.remove(logicalAmountKey);
        }

        updateOrionItemDisplay(plugin, item);
    }

    private static void updateOrionItemDisplay(@Nonnull Plugin plugin, @Nonnull Item item) {
        try {
            final Method stackerServiceMethod = plugin.getClass().getMethod(STACKER_SERVICE_METHOD);
            final Object stackerService = stackerServiceMethod.invoke(plugin);
            if (stackerService == null) {
                return;
            }

            final Method updateItemDisplayMethod = stackerService.getClass().getMethod(UPDATE_ITEM_DISPLAY_METHOD, Item.class);
            updateItemDisplayMethod.invoke(stackerService, item);
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException ignored) {
            // If Orion changes its public API, leave the logical remainder intact and skip the hologram refresh.
        }
    }

    @Nullable
    private static Plugin getOrionOptimizePlugin() {
        final Plugin plugin = Bukkit.getPluginManager().getPlugin(ORION_OPTIMIZE_PLUGIN);
        return plugin != null && plugin.isEnabled() ? plugin : null;
    }

    @Nonnull
    private static NamespacedKey getStackableItemDropKey(@Nonnull Plugin plugin) {
        return new NamespacedKey(plugin, STACKABLE_ITEM_DROP_KEY);
    }

    @Nonnull
    private static NamespacedKey getLogicalAmountKey(@Nonnull Plugin plugin) {
        return new NamespacedKey(plugin, LOGICAL_ITEM_AMOUNT_KEY);
    }

    private static int getPhysicalBatchSize(@Nonnull ItemStack itemStack) {
        if (itemStack.getType().isAir()) {
            return 1;
        }

        return Math.max(1, Math.min(64, itemStack.getMaxStackSize()));
    }
}