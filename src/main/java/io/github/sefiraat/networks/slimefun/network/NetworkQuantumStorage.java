package io.github.sefiraat.networks.slimefun.network;

import io.github.sefiraat.networks.network.stackcaches.QuantumCache;
import io.github.sefiraat.networks.utils.Keys;
import io.github.sefiraat.networks.utils.StackUtils;
import io.github.sefiraat.networks.utils.StringUtils;
import io.github.sefiraat.networks.utils.Theme;
import io.github.sefiraat.networks.utils.datatypes.DataTypeMethods;
import io.github.sefiraat.networks.utils.datatypes.PersistentQuantumStorageType;
import io.github.sefiraat.networks.slimefun.network.grid.GridCacheManager;
import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType;
import io.github.thebusybiscuit.slimefun4.core.attributes.DistinctiveItem;
import io.github.thebusybiscuit.slimefun4.core.handlers.BlockBreakHandler;
import io.github.thebusybiscuit.slimefun4.core.handlers.BlockPlaceHandler;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun4.libraries.dough.data.persistent.PersistentDataAPI;
import io.github.thebusybiscuit.slimefun4.libraries.dough.protection.Interaction;
import io.github.thebusybiscuit.slimefun4.utils.ChestMenuUtils;
import me.mrCookieSlime.CSCoreLibPlugin.Configuration.Config;
import me.mrCookieSlime.CSCoreLibPlugin.general.Inventory.ClickAction;
import me.mrCookieSlime.Slimefun.Objects.handlers.BlockTicker;
import me.mrCookieSlime.Slimefun.api.BlockStorage;
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenu;
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenuPreset;
import me.mrCookieSlime.Slimefun.api.item_transport.ItemTransportFlow;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class NetworkQuantumStorage extends SlimefunItem implements DistinctiveItem {

    private static final int[] SIZES = new int[]{
        4096,
        32768,
        262144,
        2097152,
        16777216,
        134217728,
        1073741824,
        Integer.MAX_VALUE
    };

    public static final String BS_AMOUNT = "stored_amount";
    public static final String BS_VOID = "void_excess";
    public static final String BS_ITEM = "stored_item";

    public static final int INPUT_SLOT = 1;
    public static final int ITEM_SLOT = 4;
    public static final int ITEM_SET_SLOT = 13;
    public static final int OUTPUT_SLOT = 7;
    public static final int QUICK_DEPOSIT_SLOT = 16;
    public static final int QUICK_EXTRACT_SLOT = 17;

    private static final SlimefunItemStack BACK_INPUT = new SlimefunItemStack(
        "NTW_BACK_INPUT",
        Material.GREEN_STAINED_GLASS_PANE,
        Theme.PASSIVE + "Input"
    );

    private static final SlimefunItemStack BACK_ITEM = new SlimefunItemStack(
        "NTW_BACK_ITEM",
        Material.BLUE_STAINED_GLASS_PANE,
        Theme.PASSIVE + "Item Stored"
    );

    private static final SlimefunItemStack NO_ITEM = new SlimefunItemStack(
        "NTW_NO_ITEM",
        Material.RED_STAINED_GLASS_PANE,
        Theme.ERROR + "No Registered Item",
        Theme.PASSIVE + "Click the icon below while",
        Theme.PASSIVE + "holding an item to register it."
    );

    private static final SlimefunItemStack SET_ITEM = new SlimefunItemStack(
        "NTW_SET_ITEM",
        Material.LIME_STAINED_GLASS_PANE,
        Theme.SUCCESS + "Set Item",
        Theme.PASSIVE + "Drag an item on top of this pane to register it.",
        Theme.PASSIVE + "Shift Click to change voiding"
    );

    private static final SlimefunItemStack BACK_OUTPUT = new SlimefunItemStack(
        "NTW_BACK_OUTPUT",
        Material.ORANGE_STAINED_GLASS_PANE,
        Theme.PASSIVE + "Output"
    );

    private static final SlimefunItemStack QUICK_DEPOSIT = new SlimefunItemStack(
        "NTW_QUICK_DEPOSIT",
        Material.PINK_STAINED_GLASS_PANE,
        Theme.CLICK_INFO + "Quick deposit",
        Theme.PASSIVE + "Left Click to move matching items",
        Theme.PASSIVE + "from your inventory into storage."
    );

    private static final SlimefunItemStack QUICK_EXTRACT = new SlimefunItemStack(
        "NTW_QUICK_EXTRACT",
        Material.RED_STAINED_GLASS_PANE,
        Theme.CLICK_INFO + "Quick take out",
        Theme.PASSIVE + "Left Click to fill your inventory.",
        Theme.PASSIVE + "Right Click to take one item.",
        Theme.PASSIVE + "Shift+Right Click to take one stack."
    );

    private static final int[] INPUT_SLOTS = new int[]{0, 2};
    private static final int[] ITEM_SLOTS = new int[]{3, 5};
    private static final int[] OUTPUT_SLOTS = new int[]{6, 8};
    private static final int[] BACKGROUND_SLOTS = new int[]{9, 10, 11, 12, 14, 15};

    private static final Map<Location, QuantumCache> CACHES = new ConcurrentHashMap<>();

    static {
        final ItemMeta itemMeta = NO_ITEM.getItemMeta();
        PersistentDataAPI.setBoolean(itemMeta, Keys.newKey("display"), true);
        NO_ITEM.setItemMeta(itemMeta);
    }

    private final List<Integer> slotsToDrop = new ArrayList<>();
    private final int maxAmount;

    public NetworkQuantumStorage(ItemGroup itemGroup, SlimefunItemStack item, RecipeType recipeType, ItemStack[] recipe, int maxAmount) {
        super(itemGroup, item, recipeType, recipe);
        this.maxAmount = maxAmount;
        slotsToDrop.add(INPUT_SLOT);
        slotsToDrop.add(OUTPUT_SLOT);
    }

    @Override
    public void preRegister() {
        addItemHandler(
            new BlockTicker() {
                @Override
                public boolean isSynchronized() {
                    return true;
                }

                @Override
                public void tick(Block b, SlimefunItem item, Config data) {
                    onTick(b);
                }
            },
            new BlockBreakHandler(false, false) {
                @Override
                @ParametersAreNonnullByDefault
                public void onPlayerBreak(BlockBreakEvent event, ItemStack item, List<ItemStack> drops) {
                    onBreak(event);
                }
            },
            new BlockPlaceHandler(false) {
                @Override
                public void onPlayerPlace(@Nonnull BlockPlaceEvent event) {
                    onPlace(event);
                }
            }
        );
    }

    private void onTick(Block block) {
        final BlockMenu blockMenu = BlockStorage.getInventory(block);

        if (blockMenu == null) {
            CACHES.remove(block.getLocation());
            return;
        }

        final QuantumCache cache = CACHES.get(blockMenu.getLocation());

        if (cache == null) {
            return;
        }

        if (blockMenu.hasViewer()) {
            updateDisplayItem(blockMenu, cache);
        }

        // Move items from the input slot into the card
        final ItemStack input = blockMenu.getItemInSlot(INPUT_SLOT);
        if (input != null && input.getType() != Material.AIR) {
            tryInputItem(blockMenu.getLocation(), new ItemStack[]{input}, cache);
        }

        // Output items
        final ItemStack output = blockMenu.getItemInSlot(OUTPUT_SLOT);
        ItemStack fetched = null;
        if (output == null || output.getType() == Material.AIR) {
            // No item in output, try output
            fetched = cache.withdrawItem();
        } else if (StackUtils.itemsMatch(cache, output, true) && output.getAmount() < output.getMaxStackSize()) {
            // There is an item, but it's not filled so lets top it up if we can
            final int requestAmount = output.getMaxStackSize() - output.getAmount();
            fetched = cache.withdrawItem(requestAmount);
        }

        if (fetched != null && fetched.getType() != Material.AIR) {
            blockMenu.pushItem(fetched, OUTPUT_SLOT);
            syncBlock(blockMenu.getLocation(), cache);
        }

        CACHES.put(blockMenu.getLocation().clone(), cache);
    }

    private void toggleVoid(@Nonnull BlockMenu blockMenu) {
        final QuantumCache cache = CACHES.get(blockMenu.getLocation());
        cache.setVoidExcess(!cache.isVoidExcess());
        updateDisplayItem(blockMenu, cache);
        syncBlock(blockMenu.getLocation(), cache);
        CACHES.put(blockMenu.getLocation(), cache);
    }

    private void setItem(@Nonnull BlockMenu blockMenu, @Nonnull Player player) {
        final ItemStack itemStack = player.getItemOnCursor().clone();

        if (isBlacklisted(itemStack)) {
            return;
        }

        final QuantumCache cache = CACHES.get(blockMenu.getLocation());
        if (cache == null || cache.getAmount() > 0) {
            player.sendMessage(Theme.WARNING + "Quantum Storage must be empty before changing the set item.");
            return;
        }
        itemStack.setAmount(1);
        cache.setItemStack(itemStack);
        updateDisplayItem(blockMenu, cache);
        syncBlock(blockMenu.getLocation(), cache);
        CACHES.put(blockMenu.getLocation(), cache);
    }

    private void quickDeposit(@Nonnull Player player, @Nonnull BlockMenu blockMenu) {
        final QuantumCache cache = CACHES.get(blockMenu.getLocation());

        if (cache == null || cache.getItemStack() == null) {
            return;
        }

        final ItemStack[] contents = player.getInventory().getStorageContents();
        boolean changed = false;

        for (int i = 0; i < contents.length; i++) {
            final ItemStack itemStack = contents[i];

            if (itemStack == null || itemStack.getType() == Material.AIR) {
                continue;
            }

            if (!StackUtils.itemsMatch(cache, itemStack, true)) {
                continue;
            }

            final int leftover = cache.increaseAmount(itemStack.getAmount());
            if (leftover == itemStack.getAmount()) {
                continue;
            }

            changed = true;
            if (leftover <= 0) {
                contents[i] = null;
            } else {
                itemStack.setAmount(leftover);
            }
        }

        if (!changed) {
            return;
        }

        player.getInventory().setStorageContents(contents);
        updateDisplayItem(blockMenu, cache);
        syncBlock(blockMenu.getLocation(), cache);
        CACHES.put(blockMenu.getLocation(), cache);
    }

    private void quickExtract(@Nonnull Player player, @Nonnull BlockMenu blockMenu, @Nonnull ClickAction action) {
        final QuantumCache cache = CACHES.get(blockMenu.getLocation());

        if (cache == null || cache.getItemStack() == null || cache.getAmount() <= 0) {
            return;
        }

        if (action.isRightClicked()) {
            final int amount = action.isShiftClicked() ? cache.getItemStack().getMaxStackSize() : 1;
            giveToPlayerOrDrop(player, cache.withdrawItem(amount));
        } else {
            fillInventory(player, cache);
        }

        updateDisplayItem(blockMenu, cache);
        syncBlock(blockMenu.getLocation(), cache);
        CACHES.put(blockMenu.getLocation(), cache);
    }

    private void fillInventory(@Nonnull Player player, @Nonnull QuantumCache cache) {
        final ItemStack storedItem = cache.getItemStack();

        if (storedItem == null) {
            return;
        }

        while (cache.getAmount() > 0) {
            final ItemStack extracted = cache.withdrawItem(storedItem.getMaxStackSize());

            if (extracted == null || extracted.getType() == Material.AIR) {
                return;
            }

            final Map<Integer, ItemStack> remainder = player.getInventory().addItem(extracted);
            if (remainder.isEmpty()) {
                continue;
            }

            for (ItemStack overflow : remainder.values()) {
                cache.increaseAmount(overflow.getAmount());
            }
            return;
        }
    }

    private void giveToPlayerOrDrop(@Nonnull Player player, @Nullable ItemStack itemStack) {
        if (itemStack == null || itemStack.getType() == Material.AIR) {
            return;
        }

        final Map<Integer, ItemStack> remainder = player.getInventory().addItem(itemStack);
        for (ItemStack overflow : remainder.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), overflow);
        }
    }

    @Override
    public void postRegister() {
        new BlockMenuPreset(this.getId(), this.getItemName()) {

            @Override
            public void init() {
                for (int i : INPUT_SLOTS) {
                    addItem(i, BACK_INPUT.item(), (p, slot, item, action) -> false);
                }
                for (int i : ITEM_SLOTS) {
                    addItem(i, BACK_ITEM.item(), (p, slot, item, action) -> false);
                }
                for (int i : OUTPUT_SLOTS) {
                    addItem(i, BACK_OUTPUT.item(), (p, slot, item, action) -> false);
                }
                addItem(ITEM_SET_SLOT, SET_ITEM.item(), (p, slot, item, action) -> false);
                addItem(QUICK_DEPOSIT_SLOT, QUICK_DEPOSIT.item(), (p, slot, item, action) -> false);
                addItem(QUICK_EXTRACT_SLOT, QUICK_EXTRACT.item(), (p, slot, item, action) -> false);
                addMenuClickHandler(ITEM_SLOT, ChestMenuUtils.getEmptyClickHandler());
                drawBackground(BACKGROUND_SLOTS);
            }

            @Override
            public boolean canOpen(@Nonnull Block block, @Nonnull Player player) {
                return Slimefun.getProtectionManager().hasPermission(player, block.getLocation(), Interaction.INTERACT_BLOCK);
            }

            @Override
            public int[] getSlotsAccessedByItemTransport(ItemTransportFlow flow) {
                if (flow == ItemTransportFlow.INSERT) {
                    return new int[]{INPUT_SLOT};
                } else if (flow == ItemTransportFlow.WITHDRAW) {
                    return new int[]{OUTPUT_SLOT};
                }
                return new int[0];
            }

            @Override
            public void newInstance(@Nonnull BlockMenu menu, @Nonnull Block block) {
                menu.addMenuClickHandler(ITEM_SET_SLOT, (p, slot, item, action) -> {
                    if (action.isShiftClicked()) {
                        toggleVoid(menu);
                    } else {
                        setItem(menu, p);
                    }
                    return false;
                });
                menu.addMenuClickHandler(QUICK_DEPOSIT_SLOT, (p, slot, item, action) -> {
                    quickDeposit(p, menu);
                    return false;
                });
                menu.addMenuClickHandler(QUICK_EXTRACT_SLOT, (p, slot, item, action) -> {
                    quickExtract(p, menu, action);
                    return false;
                });

                // Cache may exist if placed with items held inside.
                QuantumCache cache = CACHES.get(block.getLocation());
                if (cache == null) {
                    cache = addCache(menu);
                }
                updateDisplayItem(menu, cache);
            }
        };
    }

    private QuantumCache addCache(@Nonnull BlockMenu blockMenu) {
        final Location location = blockMenu.getLocation();
        final String amountString = BlockStorage.getLocationInfo(location, BS_AMOUNT);
        final String voidString = BlockStorage.getLocationInfo(location, BS_VOID);
        final String storedItemString = BlockStorage.getLocationInfo(location, BS_ITEM);
        int amount = 0;
        try {
            amount = amountString == null ? 0 : Integer.parseInt(amountString);
        } catch (NumberFormatException ignored) {
        }
        final boolean voidExcess = voidString == null || Boolean.parseBoolean(voidString);
        ItemStack itemStack = deserializeItemStack(storedItemString);
        if (itemStack == null) {
            itemStack = blockMenu.getItemInSlot(ITEM_SLOT);
        }

        QuantumCache cache = createCache(itemStack, blockMenu, amount, voidExcess);

        CACHES.put(location, cache);
        if (cache.getItemStack() != null || amount <= 0) {
            syncBlock(location, cache);
        }
        return cache;
    }

    private QuantumCache createCache(@Nullable ItemStack itemStack, @Nonnull BlockMenu menu, int amount, boolean voidExcess) {
        if (itemStack == null || itemStack.getType() == Material.AIR || isDisplayItem(itemStack)) {
            menu.addItem(ITEM_SLOT, NO_ITEM.item());
            return new QuantumCache(null, 0, this.maxAmount, true);
        } else {
            final ItemStack clone = itemStack.clone();
            final ItemMeta itemMeta = clone.getItemMeta();
            final List<String> lore = itemMeta != null && itemMeta.hasLore() ? new ArrayList<>(itemMeta.getLore()) : null;
            if (lore != null && hasQuantumDisplayLore(lore)) {
                if (lore.get(lore.size() - 1).contains("Capacity:")) {
                    lore.remove(lore.size() - 1);
                }
                lore.remove(lore.size() - 1);
                lore.remove(lore.size() - 1);
                if (!lore.isEmpty() && lore.get(lore.size() - 1).isBlank()) {
                    lore.remove(lore.size() - 1);
                }
            }
            if (itemMeta != null) {
                itemMeta.setLore(lore == null || lore.isEmpty() ? null : lore);
                clone.setItemMeta(itemMeta);
            }

            final QuantumCache cache = new QuantumCache(clone, amount, this.maxAmount, voidExcess);

            updateDisplayItem(menu, cache);
            return cache;
        }
    }

    private boolean isDisplayItem(@Nonnull ItemStack itemStack) {
        return PersistentDataAPI.getBoolean(itemStack.getItemMeta(), Keys.newKey("display"));
    }

    private boolean hasQuantumDisplayLore(@Nonnull List<String> lore) {
        if (lore.size() < 2) {
            return false;
        }

        int amountLineIndex = lore.size() - 1;
        if (lore.get(amountLineIndex).contains("Capacity:")) {
            amountLineIndex--;
        }

        if (amountLineIndex < 1) {
            return false;
        }

        final String amountLine = lore.get(amountLineIndex);
        final String voidLine = lore.get(amountLineIndex - 1);
        return amountLine.contains("Amount:") && voidLine.contains("Voiding:");
    }

    protected void onBreak(@Nonnull BlockBreakEvent event) {
        final Location location = event.getBlock().getLocation();
        final BlockMenu blockMenu = BlockStorage.getInventory(event.getBlock());

        if (blockMenu != null) {
            final QuantumCache cache = CACHES.remove(blockMenu.getLocation());

            if (cache != null && cache.getAmount() > 0 && cache.getItemStack() != null) {
                final ItemStack itemToDrop = this.getItem().clone();
                final ItemMeta itemMeta = itemToDrop.getItemMeta();

                DataTypeMethods.setCustom(itemMeta, Keys.QUANTUM_STORAGE_INSTANCE, PersistentQuantumStorageType.TYPE, cache);
                cache.addMetaLore(itemMeta);
                itemToDrop.setItemMeta(itemMeta);
                location.getWorld().dropItem(location.clone().add(0.5, 0.5, 0.5), itemToDrop);
                event.setDropItems(false);
            }

            for (int i : this.slotsToDrop) {
                blockMenu.dropItems(location, i);
            }
        }
    }

    protected void onPlace(@Nonnull BlockPlaceEvent event) {
        final ItemStack itemStack = event.getItemInHand();
        final ItemMeta itemMeta = itemStack.getItemMeta();
        final QuantumCache cache = DataTypeMethods.getCustom(itemMeta, Keys.QUANTUM_STORAGE_INSTANCE, PersistentQuantumStorageType.TYPE);

        if (cache == null) {
            return;
        }

        syncBlock(event.getBlock().getLocation(), cache);
        CACHES.put(event.getBlock().getLocation(), cache);
    }

    public int getMaxAmount() {
        return maxAmount;
    }

    @ParametersAreNonnullByDefault
    public static void tryInputItem(Location location, ItemStack[] input, QuantumCache cache) {
        if (cache.getItemStack() == null) {
            return;
        }
        for (ItemStack itemStack : input) {
            if (isBlacklisted(itemStack)) {
                continue;
            }
            if (StackUtils.itemsMatch(cache, itemStack, true)) {
                int leftover = cache.increaseAmount(itemStack.getAmount());
                itemStack.setAmount(leftover);
            }
        }
        syncBlock(location, cache);
    }

    private static boolean isBlacklisted(@Nonnull ItemStack itemStack) {
        return itemStack.getType() == Material.AIR
            || itemStack.getType().getMaxDurability() < 0
            || Tag.SHULKER_BOXES.isTagged(itemStack.getType())
            || SlimefunItem.getByItem(itemStack) instanceof NetworkQuantumStorage;
    }

    @ParametersAreNonnullByDefault
    @Nullable
    public static ItemStack getItemStack(@Nonnull QuantumCache cache, @Nonnull BlockMenu blockMenu) {
        if (cache.getItemStack() == null || cache.getAmount() <= 0) {
            return null;
        }
        return getItemStack(cache, blockMenu, cache.getItemStack().getMaxStackSize());
    }

    @ParametersAreNonnullByDefault
    @Nullable
    public static ItemStack getItemStack(@Nonnull QuantumCache cache, @Nonnull BlockMenu blockMenu, int amount) {
        if (cache.getAmount() < amount) {
            // Storage has no content or not enough, mix and match!
            ItemStack output = blockMenu.getItemInSlot(OUTPUT_SLOT);
            ItemStack fetched = cache.withdrawItem(amount);

            if (output != null
                && output.getType() != Material.AIR
                && StackUtils.itemsMatch(cache, output, true)
            ) {
                // We have an output item we can use also
                if (fetched == null || fetched.getType() == Material.AIR) {
                    // Storage is totally empty - just use output slot
                    fetched = output.clone();
                    if (fetched.getAmount() > amount) {
                        fetched.setAmount(amount);
                    }
                    output.setAmount(output.getAmount() - fetched.getAmount());
                } else {
                    // Storage has content, lets add on top of it
                    int additional = Math.min(amount - fetched.getAmount(), output.getAmount());
                    output.setAmount(output.getAmount() - additional);
                    fetched.setAmount(fetched.getAmount() + additional);
                }
            }
            syncBlock(blockMenu.getLocation(), cache);
            return fetched;
        } else {
            // Storage has everything we need
            syncBlock(blockMenu.getLocation(), cache);
            return cache.withdrawItem(amount);
        }
    }

    private static void updateDisplayItem(@Nonnull BlockMenu menu, @Nonnull QuantumCache cache) {
        if (cache.getItemStack() == null) {
            menu.replaceExistingItem(ITEM_SLOT, NO_ITEM.item());
        } else {
            final ItemStack itemStack = cache.getItemStack().clone();
            final ItemMeta itemMeta = itemStack.getItemMeta();
            final List<String> lore = itemMeta.hasLore() ? itemMeta.getLore() : new ArrayList<>();
            lore.add("");
            lore.add(Theme.CLICK_INFO + "Voiding: " + Theme.PASSIVE + StringUtils.toTitleCase(String.valueOf(cache.isVoidExcess())));
            lore.add(Theme.CLICK_INFO + "Amount: " + Theme.PASSIVE + cache.getAmount());
            lore.add(Theme.CLICK_INFO + "Capacity: " + Theme.PASSIVE + cache.getLimit());
            itemMeta.setLore(lore);
            itemStack.setItemMeta(itemMeta);
            itemStack.setAmount(1);
            menu.replaceExistingItem(ITEM_SLOT, itemStack);
        }
    }

    private static void syncBlock(@Nonnull Location location, @Nonnull QuantumCache cache) {
        BlockStorage.addBlockInfo(location, BS_AMOUNT, String.valueOf(cache.getAmount()));
        BlockStorage.addBlockInfo(location, BS_VOID, String.valueOf(cache.isVoidExcess()));
        BlockStorage.addBlockInfo(location, BS_ITEM, serializeItemStack(cache.getItemStack()));

        final BlockMenu blockMenu = BlockStorage.getInventory(location);
        if (blockMenu != null) {
            blockMenu.markDirty();
        }

        GridCacheManager.markAllCachesDirty();
    }

    public static void persistCaches() {
        for (Map.Entry<Location, QuantumCache> entry : CACHES.entrySet()) {
            syncBlock(entry.getKey(), entry.getValue());
        }
    }

    @Nullable
    private static String serializeItemStack(@Nullable ItemStack itemStack) {
        if (itemStack == null || itemStack.getType() == Material.AIR) {
            return null;
        }

        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
             BukkitObjectOutputStream dataOutput = new BukkitObjectOutputStream(outputStream)
        ) {
            final ItemStack clone = itemStack.clone();
            clone.setAmount(1);
            dataOutput.writeObject(clone);
            dataOutput.flush();
            return Base64.getEncoder().encodeToString(outputStream.toByteArray());
        } catch (IOException ignored) {
            return null;
        }
    }

    @Nullable
    private static ItemStack deserializeItemStack(@Nullable String serialized) {
        if (serialized == null || serialized.isBlank()) {
            return null;
        }

        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(Base64.getDecoder().decode(serialized));
             BukkitObjectInputStream dataInput = new BukkitObjectInputStream(inputStream)
        ) {
            final Object object = dataInput.readObject();
            if (object instanceof ItemStack itemStack) {
                return itemStack;
            }
        } catch (IOException | ClassNotFoundException | IllegalArgumentException ignored) {
        }

        return null;
    }

    public static Map<Location, QuantumCache> getCaches() {
        return CACHES;
    }

    public static int[] getSizes() {
        return SIZES;
    }

    @Override
    public boolean canStack(@Nonnull ItemMeta sfItemMeta, @Nonnull ItemMeta itemMeta) {
        return sfItemMeta.getPersistentDataContainer().equals(itemMeta.getPersistentDataContainer());
    }
}
