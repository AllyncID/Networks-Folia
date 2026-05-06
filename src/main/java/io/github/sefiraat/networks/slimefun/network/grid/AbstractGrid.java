package io.github.sefiraat.networks.slimefun.network.grid;

import io.github.sefiraat.networks.Networks;
import io.github.sefiraat.networks.NetworkStorage;
import io.github.sefiraat.networks.network.GridItemRequest;
import io.github.sefiraat.networks.network.NetworkRoot;
import io.github.sefiraat.networks.network.NodeDefinition;
import io.github.sefiraat.networks.network.NodeType;
import io.github.sefiraat.networks.slimefun.network.NetworkObject;
import io.github.sefiraat.networks.utils.FoliaSupport;
import io.github.sefiraat.networks.utils.StackUtils;
import io.github.sefiraat.networks.utils.Theme;
import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.ItemSetting;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun4.api.items.settings.IntRangeSetting;
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType;
import io.github.thebusybiscuit.slimefun4.utils.ChatUtils;
import me.mrCookieSlime.CSCoreLibPlugin.Configuration.Config;
import me.mrCookieSlime.CSCoreLibPlugin.general.Inventory.ClickAction;
import me.mrCookieSlime.Slimefun.Objects.handlers.BlockTicker;
import me.mrCookieSlime.Slimefun.api.BlockStorage;
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenu;
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenuPreset;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public abstract class AbstractGrid extends NetworkObject {

    private static final String ADMIN_PERMISSION = "networks.admin";

    private static final SlimefunItemStack BLANK_SLOT_STACK = new SlimefunItemStack(
            "NTW_BLANK_SLOT",
            Material.LIGHT_GRAY_STAINED_GLASS_PANE,
            " "
    );

    private static final SlimefunItemStack PAGE_PREVIOUS_STACK = new SlimefunItemStack(
            "NTW_PAGE_PREVIOUS",
            Material.RED_STAINED_GLASS_PANE,
            Theme.CLICK_INFO.getColor() + "Previous Page"
    );

    private static final SlimefunItemStack PAGE_NEXT_STACK = new SlimefunItemStack(
            "NTW_PAGE_NEXT",
            Material.RED_STAINED_GLASS_PANE,
            Theme.CLICK_INFO.getColor() + "Next Page"
    );

    private static final SlimefunItemStack CHANGE_SORT_STACK = new SlimefunItemStack(
            "NTW_CHANGE_SORT",
            Material.BLUE_STAINED_GLASS_PANE,
            Theme.CLICK_INFO.getColor() + "Change Sort Order"
    );

    private static final SlimefunItemStack FILTER_STACK = new SlimefunItemStack(
            "NTW_FILTER",
            Material.NAME_TAG,
            Theme.CLICK_INFO.getColor() + "Set Filter (Right Click to Clear)"
    );

    private static final Comparator<Map.Entry<ItemStack, Integer>> ALPHABETICAL_SORT = Comparator.comparing(
            itemStackIntegerEntry -> {
                ItemStack itemStack = itemStackIntegerEntry.getKey();
                SlimefunItem slimefunItem = SlimefunItem.getByItem(itemStack);
                if (slimefunItem != null) {
                    return ChatColor.stripColor(slimefunItem.getItemName());
                } else {
                    ItemMeta itemMeta = itemStackIntegerEntry.getKey().getItemMeta();
                    return itemMeta.hasDisplayName()
                            ? ChatColor.stripColor(itemMeta.getDisplayName())
                            : itemStackIntegerEntry.getKey().getType().name();
                }
            }
    );

    private static final Comparator<Map.Entry<ItemStack, Integer>> NUMERICAL_SORT =
            Map.Entry.<ItemStack, Integer>comparingByValue(Comparator.reverseOrder())
                    .thenComparing(ALPHABETICAL_SORT);

    private final ItemSetting<Integer> tickRate;

    protected AbstractGrid(ItemGroup itemGroup, SlimefunItemStack item, RecipeType recipeType, ItemStack[] recipe) {
        super(itemGroup, item, recipeType, recipe, NodeType.GRID);

        this.getSlotsToDrop().add(getInputSlot());

        this.tickRate = new IntRangeSetting(this, "tick_rate", 1, 1, 10);
        addItemSetting(this.tickRate);

        addItemHandler(
                new BlockTicker() {

                    private int tick = 1;

                    @Override
                    public boolean isSynchronized() {
                        return true;
                    }

                    @Override
                    public void tick(Block block, SlimefunItem item, Config data) {
                        if (tick <= 1) {
                            final BlockMenu blockMenu = BlockStorage.getInventory(block);
                            addToRegistry(block);
                            tryAddItem(blockMenu);
                            updateDisplay(blockMenu);
                        }
                    }

                    @Override
                    public void uniqueTick() {
                        tick = tick <= 1 ? tickRate.getValue() : tick - 1;
                    }
                }
        );
    }

    protected void tryAddItem(@Nonnull BlockMenu blockMenu) {
        final ItemStack itemStack = blockMenu.getItemInSlot(getInputSlot());

        if (itemStack == null || itemStack.getType() == Material.AIR) {
            return;
        }

        final NodeDefinition definition = NetworkStorage.getAllNetworkObjects().get(blockMenu.getLocation());
        if (definition.getNode() == null) {
            return;
        }

        definition.getNode().getRoot().addItemStack(itemStack);
        blockMenu.replaceExistingItem(getInputSlot(), null);
    }

    @Override
    protected void onBreak(@Nonnull BlockBreakEvent event) {
        BlockMenu blockMenu = BlockStorage.getInventory(event.getBlock());
        if (blockMenu != null) {
            GridCache cache = getCacheMap().get(blockMenu.getLocation());
            if (cache != null) {
                GridCacheManager.removeCache(cache);
                getCacheMap().remove(blockMenu.getLocation());
            }
        }
        super.onBreak(event);
    }

    protected void updateDisplay(@Nonnull BlockMenu blockMenu) {
        // No viewer - lets not bother updating
        if (!blockMenu.hasViewer()) {
            return;
        }

        final NodeDefinition definition = NetworkStorage.getAllNetworkObjects().get(blockMenu.getLocation());

        // No node located, weird
        if (definition == null || definition.getNode() == null) {
            clearDisplay(blockMenu);
            return;
        }

        // Update Screen
        final NetworkRoot root = definition.getNode().getRoot();
        final GridCache gridCache = getCacheMap().get(blockMenu.getLocation().clone());

        if (gridCache.isDirty()) {
            gridCache.setCachedEntries(getEntries(root, gridCache));
            gridCache.setDirty(false);
        }

        final List<Map.Entry<ItemStack, Integer>> entries = gridCache.getCachedEntries();
        if (entries == null) {
            clearDisplay(blockMenu);
            return;
        }

        final int pages = (int) Math.ceil(entries.size() / (double) getDisplaySlots().length) - 1;

        gridCache.setMaxPages(pages);

        // Set everything to blank and return if there are no pages (no items)
        if (pages < 0) {
            clearDisplay(blockMenu);
            return;
        }

        // Reset selected page if it no longer exists due to items being removed
        if (gridCache.getPage() > pages) {
            gridCache.setPage(0);
        }

        final int start = gridCache.getPage() * getDisplaySlots().length;
        final int end = Math.min(start + getDisplaySlots().length, entries.size());
        final List<Map.Entry<ItemStack, Integer>> validEntries = entries.subList(start, end);

        getCacheMap().put(blockMenu.getLocation(), gridCache);

        for (int i = 0; i < getDisplaySlots().length; i++) {
            if (validEntries.size() > i) {
                final Map.Entry<ItemStack, Integer> entry = validEntries.get(i);
                final ItemStack displayStack = entry.getKey().clone();
                final ItemMeta itemMeta = displayStack.getItemMeta();
                List<String> lore = itemMeta.getLore();

                if (lore == null) {
                    lore = getLoreAddition(entry.getValue());
                } else {
                    lore.addAll(getLoreAddition(entry.getValue()));
                }

                itemMeta.setLore(lore);
                displayStack.setItemMeta(itemMeta);
                blockMenu.replaceExistingItem(getDisplaySlots()[i], displayStack);
                blockMenu.addMenuClickHandler(getDisplaySlots()[i], (player, slot, item, action) -> {
                    retrieveItem(player, definition, item, action, blockMenu);
                    return false;
                });
            } else {
                blockMenu.replaceExistingItem(getDisplaySlots()[i], BLANK_SLOT_STACK.item());
                blockMenu.addMenuClickHandler(getDisplaySlots()[i], (p, slot, item, action) -> false);
            }
        }
    }

    protected void clearDisplay(BlockMenu blockMenu) {
        for (int displaySlot : getDisplaySlots()) {
            blockMenu.replaceExistingItem(displaySlot, BLANK_SLOT_STACK.item());
            blockMenu.addMenuClickHandler(displaySlot, (p, slot, item, action) -> false);
        }
    }

    @Nonnull
    protected List<Map.Entry<ItemStack, Integer>> getEntries(@Nonnull NetworkRoot networkRoot, @Nonnull GridCache cache) {
        return networkRoot.getAllNetworkItems().entrySet().stream()
                .filter(entry -> {
                    if (cache.getFilter() == null) {
                        return true;
                    }

                    final ItemStack itemStack = entry.getKey();
                    String name = itemStack.getType().name().toLowerCase(Locale.ROOT);
                    if (itemStack.hasItemMeta()) {
                        final ItemMeta itemMeta = itemStack.getItemMeta();
                        if (itemMeta.hasDisplayName()) {
                            name = ChatColor.stripColor(itemMeta.getDisplayName().toLowerCase(Locale.ROOT));
                        }
                    }
                    return name.contains(cache.getFilter());
                })
                .sorted(cache.getSortOrder() == GridCache.SortOrder.ALPHABETICAL ? ALPHABETICAL_SORT : NUMERICAL_SORT)
                .toList();
    }

    protected boolean setFilter(@Nonnull Player player, @Nonnull BlockMenu blockMenu, @Nonnull GridCache gridCache, @Nonnull ClickAction action) {
        if (action.isRightClicked()) {
            gridCache.setFilter(null);
        } else {
            player.closeInventory();
            player.sendMessage(Theme.WARNING + "Type what you would like to filter this grid to");
            ChatUtils.awaitInput(player, s -> {
                if (s.isBlank()) {
                    return;
                }

                // --- Fix: Strip colors and gradients from input ---
                String cleanInput = s;
                // Remove hex patterns like &#123456
                cleanInput = cleanInput.replaceAll("&#[a-fA-F0-9]{6}", "");
                // Remove legacy & patterns and strip colors
                cleanInput = ChatColor.stripColor(ChatColor.translateAlternateColorCodes('&', cleanInput));
                // --------------------------------------------------

                gridCache.setFilter(cleanInput.toLowerCase(Locale.ROOT));
                player.sendMessage(Theme.SUCCESS + "Filter applied");
            });
        }
        return false;
    }

    @ParametersAreNonnullByDefault
    protected void retrieveItem(Player player, NodeDefinition definition, @Nullable ItemStack itemStack, ClickAction action, BlockMenu blockMenu) {
        // Todo Item can be null here. No idea how - investigate later
        if (itemStack == null || itemStack.getType() == Material.AIR) {
            return;
        }

        final ItemStack clone = itemStack.clone();
        final ItemMeta cloneMeta = clone.getItemMeta();

        if (cloneMeta != null && cloneMeta.hasLore()) {
            final List<String> cloneLore = new ArrayList<>(cloneMeta.getLore());
            if (cloneLore.size() >= 2) {
                cloneLore.remove(cloneLore.size() - 1);
                cloneLore.remove(cloneLore.size() - 1);
                cloneMeta.setLore(cloneLore);
                clone.setItemMeta(cloneMeta);
            }
        }

        if (canRequestCustomAmount(player, action)) {
            promptCustomAmount(player, clone, blockMenu);
            return;
        }

        int amount = 1;

        if (action.isRightClicked()) {
            amount = clone.getMaxStackSize();
        }

        final GridItemRequest request = new GridItemRequest(clone, amount, player);

        if (action.isShiftClicked()) {
            addToInventory(player, definition, request, action);
        } else {
            addToCursor(player, definition, request, action);
        }

        updateDisplay(blockMenu);
    }

    private boolean canRequestCustomAmount(@Nonnull Player player, @Nonnull ClickAction action) {
        return action.isShiftClicked() && action.isRightClicked() && (player.isOp() || player.hasPermission(ADMIN_PERMISSION));
    }

    @ParametersAreNonnullByDefault
    private void promptCustomAmount(Player player, ItemStack itemStack, BlockMenu blockMenu) {
        final Location gridLocation = blockMenu.getLocation().clone();
        final String itemName = getItemName(itemStack);

        player.closeInventory();
        player.sendMessage(Theme.WARNING + "Type the refund amount for " + Theme.PASSIVE + itemName + Theme.WARNING + " in chat.");
        player.sendMessage(Theme.WARNING + "Type " + Theme.PASSIVE + "cancel" + Theme.WARNING + " to abort.");

        ChatUtils.awaitInput(player, input -> {
            final String trimmed = input.trim();
            if (trimmed.isBlank() || trimmed.equalsIgnoreCase("cancel")) {
                sendMessageToPlayer(player, Theme.WARNING + "Refund cancelled.");
                return;
            }

            final Integer amount = parseRequestedAmount(trimmed);
            if (amount == null) {
                sendMessageToPlayer(player, Theme.ERROR + "Enter a whole number greater than 0.");
                return;
            }

            FoliaSupport.executeRegion(Networks.getInstance(), gridLocation, () -> fulfillCustomRequest(player, gridLocation, itemStack, itemName, amount));
        });
    }

    @ParametersAreNonnullByDefault
    private void fulfillCustomRequest(Player player, Location gridLocation, ItemStack itemStack, String itemName, int amount) {
        final NodeDefinition currentDefinition = NetworkStorage.getAllNetworkObjects().get(gridLocation);
        if (currentDefinition == null || currentDefinition.getNode() == null) {
            sendMessageToPlayer(player, Theme.ERROR + "This grid is no longer connected to a network.");
            return;
        }

        final BlockMenu currentMenu = BlockStorage.getInventory(gridLocation);
        if (currentMenu == null) {
            sendMessageToPlayer(player, Theme.ERROR + "This grid is no longer available.");
            return;
        }

        final int refundedAmount = refundIntoNetwork(currentDefinition.getNode().getRoot(), itemStack, amount);
        if (refundedAmount <= 0) {
            sendMessageToPlayer(player, Theme.WARNING + "The network had no room for that refund.");
            updateDisplay(currentMenu);
            return;
        }

        if (refundedAmount < amount) {
            sendMessageToPlayer(
                player,
                Theme.WARNING + "Refunded " + Theme.PASSIVE + refundedAmount + Theme.WARNING + "x " + Theme.PASSIVE
                    + itemName + Theme.WARNING + " into the network. The rest could not fit."
            );
        } else {
            sendMessageToPlayer(
                player,
                Theme.SUCCESS + "Refunded " + Theme.PASSIVE + refundedAmount + Theme.SUCCESS + "x " + Theme.PASSIVE
                    + itemName + Theme.SUCCESS + " into the network."
            );
        }

        updateDisplay(currentMenu);
    }

    @ParametersAreNonnullByDefault
    private int refundIntoNetwork(NetworkRoot root, ItemStack itemStack, int amount) {
        int remaining = amount;

        while (remaining > 0) {
            final ItemStack stackToRefund = itemStack.clone();
            final int stackAmount = Math.min(stackToRefund.getMaxStackSize(), remaining);
            stackToRefund.setAmount(stackAmount);

            root.addItemStack(stackToRefund);

            final int refunded = stackAmount - stackToRefund.getAmount();
            if (refunded <= 0) {
                break;
            }

            remaining -= refunded;
        }

        return amount - remaining;
    }

    @Nullable
    private Integer parseRequestedAmount(@Nonnull String input) {
        final String normalized = input.replace(",", "").replace("_", "");

        try {
            final int amount = Integer.parseInt(normalized);
            return amount > 0 ? amount : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private void sendMessageToPlayer(@Nonnull Player player, @Nonnull String message) {
        FoliaSupport.executeEntity(Networks.getInstance(), player, () -> player.sendMessage(message));
    }

    @Nonnull
    private String getItemName(@Nonnull ItemStack itemStack) {
        final SlimefunItem slimefunItem = SlimefunItem.getByItem(itemStack);
        if (slimefunItem != null) {
            return ChatColor.stripColor(slimefunItem.getItemName());
        }

        final ItemMeta itemMeta = itemStack.getItemMeta();
        if (itemMeta != null && itemMeta.hasDisplayName()) {
            return ChatColor.stripColor(itemMeta.getDisplayName());
        }

        return ChatUtils.humanize(itemStack.getType().name());
    }

    @ParametersAreNonnullByDefault
    private void addToInventory(Player player, NodeDefinition definition, GridItemRequest request, ClickAction action) {
        ItemStack requestingStack = definition.getNode().getRoot().getItemStack(request);

        if (requestingStack == null) {
            return;
        }

        HashMap<Integer, ItemStack> remnant = player.getInventory().addItem(requestingStack);
        requestingStack = remnant.values().stream().findFirst().orElse(null);
        if (requestingStack != null) {
            definition.getNode().getRoot().addItemStack(requestingStack);
        }
    }

    @ParametersAreNonnullByDefault
    private void addToCursor(Player player, NodeDefinition definition, GridItemRequest request, ClickAction action) {
        final ItemStack cursor = player.getItemOnCursor();

        // Quickly check if the cursor has an item and if we can add more to it
        if (cursor.getType() != Material.AIR && !canAddMore(action, cursor, request)) {
            return;
        }

        ItemStack requestingStack = definition.getNode().getRoot().getItemStack(request);
        setCursor(player, cursor, requestingStack);
    }

    private void setCursor(Player player, ItemStack cursor, ItemStack requestingStack) {
        if (requestingStack != null) {
            if (cursor.getType() != Material.AIR) {
                requestingStack.setAmount(cursor.getAmount() + 1);
            }
            player.setItemOnCursor(requestingStack);
        }
    }

    private boolean canAddMore(@Nonnull ClickAction action, @Nonnull ItemStack cursor, @Nonnull GridItemRequest request) {
        return !action.isRightClicked()
                && request.getAmount() == 1
                && cursor.getAmount() < cursor.getMaxStackSize()
                && StackUtils.itemsMatch(request, cursor, true);
    }

    @Override
    public void postRegister() {
        getPreset();
    }

    @Nonnull
    protected abstract BlockMenuPreset getPreset();

    @Nonnull
    protected abstract Map<Location, GridCache> getCacheMap();

    protected abstract int[] getBackgroundSlots();

    protected abstract int[] getDisplaySlots();

    protected abstract int getInputSlot();

    protected abstract int getChangeSort();

    protected abstract int getPagePrevious();

    protected abstract int getPageNext();

    protected abstract int getFilterSlot();

    protected ItemStack getBlankSlotStack() {
        return BLANK_SLOT_STACK.item();
    }

    protected ItemStack getPagePreviousStack() {
        return PAGE_PREVIOUS_STACK.item();
    }

    protected ItemStack getPageNextStack() {
        return PAGE_NEXT_STACK.item();
    }

    protected ItemStack getChangeSortStack() {
        return CHANGE_SORT_STACK.item();
    }

    protected ItemStack getFilterStack() {
        return FILTER_STACK.item();
    }


    @Nonnull
    private static List<String> getLoreAddition(int amount) {
        final MessageFormat format = new MessageFormat("{0}Amount: {1}{2}", Locale.ROOT);
        return List.of(
                "",
                format.format(new Object[]{Theme.CLICK_INFO.getColor(), Theme.PASSIVE.getColor(), amount}, new StringBuffer(), null).toString()
        );
    }
}
