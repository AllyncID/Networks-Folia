package io.github.sefiraat.networks.slimefun.network;

import io.github.sefiraat.networks.NetworkStorage;
import io.github.sefiraat.networks.network.NodeType;
import io.github.sefiraat.networks.utils.NetworkUtils;
import io.github.sefiraat.networks.utils.FoliaSupport;
import io.github.sefiraat.networks.utils.PersistentNetworkMetadata;
import io.github.sefiraat.networks.utils.Theme;
import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.ItemSetting;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun4.api.items.settings.IntRangeSetting;
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType;
import io.github.thebusybiscuit.slimefun4.core.handlers.BlockPlaceHandler;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun4.libraries.dough.protection.Interaction;
import me.mrCookieSlime.CSCoreLibPlugin.Configuration.Config;
import me.mrCookieSlime.CSCoreLibPlugin.general.Inventory.ClickAction;
import me.mrCookieSlime.Slimefun.Objects.handlers.BlockTicker;
import me.mrCookieSlime.Slimefun.api.BlockStorage;
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenu;
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenuPreset;
import me.mrCookieSlime.Slimefun.api.item_transport.ItemTransportFlow;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.util.Vector;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.OverridingMethodsMustInvokeSuper;
import javax.annotation.ParametersAreNonnullByDefault;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public abstract class NetworkDirectional extends NetworkObject {

    private static final int NORTH_SLOT = 12;
    private static final int SOUTH_SLOT = 30;
    private static final int EAST_SLOT = 22;
    private static final int WEST_SLOT = 20;
    private static final int UP_SLOT = 15;
    private static final int DOWN_SLOT = 33;

    protected static final String DIRECTION = "direction";
    protected static final String OWNER_KEY = "uuid";

    private static final Set<BlockFace> VALID_FACES = EnumSet.of(
            BlockFace.UP,
            BlockFace.DOWN,
            BlockFace.NORTH,
            BlockFace.EAST,
            BlockFace.SOUTH,
            BlockFace.WEST
    );

    private static final Map<Location, BlockFace> SELECTED_DIRECTION_MAP = new ConcurrentHashMap<>();
    private static final int[] NO_SLOTS = new int[0];

    private final ItemSetting<Integer> tickRate;

    protected NetworkDirectional(ItemGroup itemGroup, SlimefunItemStack item, RecipeType recipeType, ItemStack[] recipe, NodeType type) {
        super(itemGroup, item, recipeType, recipe, type);
        this.tickRate = new IntRangeSetting(this, "tick_rate", 1, 1, 10);
        addItemSetting(this.tickRate);

        addItemHandler(
                new BlockPlaceHandler(false) {
                    @Override
                    public void onPlayerPlace(@Nonnull BlockPlaceEvent event) {
                        final Location location = event.getBlock().getLocation();
                        NetworkStorage.removeNode(location);
                        SELECTED_DIRECTION_MAP.remove(location);
                        BlockStorage.addBlockInfo(location, OWNER_KEY, event.getPlayer().getUniqueId().toString());
                        BlockStorage.addBlockInfo(location, DIRECTION, BlockFace.SELF.name());
                        PersistentNetworkMetadata.setString(location, OWNER_KEY, event.getPlayer().getUniqueId().toString());
                        PersistentNetworkMetadata.setString(location, DIRECTION, BlockFace.SELF.name());
                        SELECTED_DIRECTION_MAP.put(location.clone(), BlockFace.SELF);
                        NetworkUtils.applyConfig(NetworkDirectional.this, BlockStorage.getInventory(event.getBlock()), event.getPlayer());
                        persistBlockMetadata(location);
                    }
                },
                new BlockTicker() {

                    private int tick = 1;

                    @Override
                    public boolean isSynchronized() {
                        return runSync();
                    }

                    @Override
                    public void tick(Block block, SlimefunItem slimefunItem, Config config) {
                        if (tick <= 1) {
                            final BlockMenu blockMenu = BlockStorage.getInventory(block);
                            onTick(blockMenu, block);
                        }
                    }

                    @Override
                    public void uniqueTick() {
                        tick = tick <= 1 ? tickRate.getValue() : tick - 1;
                        if (tick <= 1) {
                            onUniqueTick();
                        }
                    }
                }
        );
    }

    private void updateGui(@Nullable BlockMenu blockMenu) {
        if (blockMenu == null || !blockMenu.hasViewer()) {
            return;
        }

        BlockFace direction = getCurrentDirection(blockMenu);

        for (BlockFace blockFace : VALID_FACES) {
            final Block block = getAdjacentOwnedBlock(blockMenu.getBlock(), blockFace);
            if (block == null) {
                continue;
            }
            final SlimefunItem slimefunItem = BlockStorage.check(block);
            if (slimefunItem != null) {
                switch (blockFace) {
                    case NORTH -> blockMenu.replaceExistingItem(getNorthSlot(), getDirectionalSlotPane(blockFace, slimefunItem, blockFace == direction));
                    case SOUTH -> blockMenu.replaceExistingItem(getSouthSlot(), getDirectionalSlotPane(blockFace, slimefunItem, blockFace == direction));
                    case EAST -> blockMenu.replaceExistingItem(getEastSlot(), getDirectionalSlotPane(blockFace, slimefunItem, blockFace == direction));
                    case WEST -> blockMenu.replaceExistingItem(getWestSlot(), getDirectionalSlotPane(blockFace, slimefunItem, blockFace == direction));
                    case UP -> blockMenu.replaceExistingItem(getUpSlot(), getDirectionalSlotPane(blockFace, slimefunItem, blockFace == direction));
                    case DOWN -> blockMenu.replaceExistingItem(getDownSlot(), getDirectionalSlotPane(blockFace, slimefunItem, blockFace == direction));
                    default -> throw new IllegalStateException("Unexpected value: " + blockFace);
                }
            } else {
                final Material material = block.getType();
                switch (blockFace) {
                    case NORTH -> blockMenu.replaceExistingItem(getNorthSlot(), getDirectionalSlotPane(blockFace, material, blockFace == direction));
                    case SOUTH -> blockMenu.replaceExistingItem(getSouthSlot(), getDirectionalSlotPane(blockFace, material, blockFace == direction));
                    case EAST -> blockMenu.replaceExistingItem(getEastSlot(), getDirectionalSlotPane(blockFace, material, blockFace == direction));
                    case WEST -> blockMenu.replaceExistingItem(getWestSlot(), getDirectionalSlotPane(blockFace, material, blockFace == direction));
                    case UP -> blockMenu.replaceExistingItem(getUpSlot(), getDirectionalSlotPane(blockFace, material, blockFace == direction));
                    case DOWN -> blockMenu.replaceExistingItem(getDownSlot(), getDirectionalSlotPane(blockFace, material, blockFace == direction));
                    default -> throw new IllegalStateException("Unexpected value: " + blockFace);
                }
            }
        }
    }

    @Nonnull
    protected BlockFace getCurrentDirection(@Nonnull BlockMenu blockMenu) {
        final Location location = blockMenu.getLocation().clone();
        BlockFace direction = SELECTED_DIRECTION_MAP.get(location);

        if (direction == null) {
            direction = readStoredDirection(blockMenu.getLocation());
            SELECTED_DIRECTION_MAP.put(location, direction);
        }
        return direction;
    }

    @Nonnull
    private BlockFace readStoredDirection(@Nonnull Location location) {
        final String storedDirection = getStoredDirectionString(location);

        if (storedDirection == null || storedDirection.isBlank()) {
            return BlockFace.SELF;
        }

        try {
            final BlockFace blockFace = BlockFace.valueOf(storedDirection);
            return isValidStoredDirection(blockFace) ? blockFace : BlockFace.SELF;
        } catch (IllegalArgumentException ignored) {
            return BlockFace.SELF;
        }
    }

    private static boolean isValidStoredDirection(@Nonnull BlockFace blockFace) {
        return blockFace == BlockFace.SELF || VALID_FACES.contains(blockFace);
    }

    @Nullable
    protected UUID getOwnerUuid(@Nonnull Location location) {
        final String storedOwner = getStoredOwnerString(location);

        if (storedOwner == null || storedOwner.isBlank()) {
            return null;
        }

        try {
            return UUID.fromString(storedOwner);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    @Nonnull
    protected int[] getTransportSlots(@Nullable BlockMenu targetMenu,
                                      @Nonnull ItemTransportFlow flow,
                                      @Nullable ItemStack transportItem
    ) {
        if (targetMenu == null) {
            return NO_SLOTS;
        }

        final BlockMenuPreset preset = targetMenu.getPreset();
        if (preset == null) {
            return NO_SLOTS;
        }

        final int[] slots = preset.getSlotsAccessedByItemTransport(targetMenu, flow, transportItem);
        return slots == null ? NO_SLOTS : slots;
    }

    protected static boolean hasStoredSlimefunData(@Nonnull Block block) {
        return BlockStorage.checkID(block) != null || BlockStorage.hasBlockInfo(block);
    }

    @OverridingMethodsMustInvokeSuper
    protected void onTick(@Nullable BlockMenu blockMenu, @Nonnull Block block) {
        addToRegistry(block);
        updateGui(blockMenu);
    }

    protected void onUniqueTick() {}

    @Override
    protected void onBreak(@Nonnull BlockBreakEvent event) {
        PersistentNetworkMetadata.clearLocation(event.getBlock().getLocation());
        SELECTED_DIRECTION_MAP.remove(event.getBlock().getLocation());
        super.onBreak(event);
    }

    @Override
    public void postRegister() {
        new BlockMenuPreset(this.getId(), this.getItemName()) {

            @Override
            public void init() {
                drawBackground(getBackgroundSlots());

                if (getOtherBackgroundSlots() != null && getOtherBackgroundStack() != null) {
                    drawBackground(getOtherBackgroundStack().item(), getOtherBackgroundSlots());
                }

                addItem(getNorthSlot(), getDirectionalSlotPane(BlockFace.NORTH, Material.AIR, false), (player, i, itemStack, clickAction) -> false);
                addItem(getSouthSlot(), getDirectionalSlotPane(BlockFace.SOUTH, Material.AIR, false), (player, i, itemStack, clickAction) -> false);
                addItem(getEastSlot(), getDirectionalSlotPane(BlockFace.EAST, Material.AIR, false), (player, i, itemStack, clickAction) -> false);
                addItem(getWestSlot(), getDirectionalSlotPane(BlockFace.WEST, Material.AIR, false), (player, i, itemStack, clickAction) -> false);
                addItem(getUpSlot(), getDirectionalSlotPane(BlockFace.UP, Material.AIR, false), (player, i, itemStack, clickAction) -> false);
                addItem(getDownSlot(), getDirectionalSlotPane(BlockFace.DOWN, Material.AIR, false), (player, i, itemStack, clickAction) -> false);
            }

            @Override
            public void newInstance(@Nonnull BlockMenu blockMenu, @Nonnull Block b) {
                final BlockFace direction = readStoredDirection(blockMenu.getLocation());
                SELECTED_DIRECTION_MAP.put(blockMenu.getLocation().clone(), direction);

                blockMenu.addMenuClickHandler(getNorthSlot(), (player, i, itemStack, clickAction) ->
                        directionClick(player, clickAction, blockMenu, BlockFace.NORTH));
                blockMenu.addMenuClickHandler(getSouthSlot(), (player, i, itemStack, clickAction) ->
                        directionClick(player, clickAction, blockMenu, BlockFace.SOUTH));
                blockMenu.addMenuClickHandler(getEastSlot(), (player, i, itemStack, clickAction) ->
                        directionClick(player, clickAction, blockMenu, BlockFace.EAST));
                blockMenu.addMenuClickHandler(getWestSlot(), (player, i, itemStack, clickAction) ->
                        directionClick(player, clickAction, blockMenu, BlockFace.WEST));
                blockMenu.addMenuClickHandler(getUpSlot(), (player, i, itemStack, clickAction) ->
                        directionClick(player, clickAction, blockMenu, BlockFace.UP));
                blockMenu.addMenuClickHandler(getDownSlot(), (player, i, itemStack, clickAction) ->
                        directionClick(player, clickAction, blockMenu, BlockFace.DOWN));
            }

            @Override
            public boolean canOpen(@Nonnull Block block, @Nonnull Player player) {
                return this.getSlimefunItem().canUse(player, false)
                        && Slimefun.getProtectionManager().hasPermission(player, block.getLocation(), Interaction.INTERACT_BLOCK);
            }

            @Override
            public int[] getSlotsAccessedByItemTransport(ItemTransportFlow flow) {
                if (flow == ItemTransportFlow.INSERT) {
                    return getInputSlots();
                } else {
                    return getOutputSlots();
                }
            }
        };
    }

    @Nullable
    protected Block getAdjacentOwnedBlock(@Nonnull Block sourceBlock, @Nonnull BlockFace blockFace) {
        final Block targetBlock = sourceBlock.getRelative(blockFace);
        return FoliaSupport.isOwnedByCurrentRegion(targetBlock.getLocation()) ? targetBlock : null;
    }

    @Nullable
    protected BlockMenu getAdjacentOwnedMenu(@Nonnull BlockMenu blockMenu, @Nonnull BlockFace blockFace) {
        final Block targetBlock = getAdjacentOwnedBlock(blockMenu.getBlock(), blockFace);
        return targetBlock == null ? null : BlockStorage.getInventory(targetBlock);
    }

    @ParametersAreNonnullByDefault
    public boolean directionClick(Player player, ClickAction action, BlockMenu blockMenu, BlockFace blockFace) {
        if (action.isShiftClicked()) {
            openDirection(player, blockMenu, blockFace);
        } else {
            setDirection(blockMenu, blockFace);
        }
        return false;
    }

    @ParametersAreNonnullByDefault
    public void openDirection(Player player, BlockMenu blockMenu, BlockFace blockFace) {
        final BlockMenu targetMenu = getAdjacentOwnedMenu(blockMenu, blockFace);
        if (targetMenu != null) {
            final Location location = targetMenu.getLocation();
            final SlimefunItem item = BlockStorage.check(location);
            if (item != null
                    && item.canUse(player, true)
                    && Slimefun.getProtectionManager().hasPermission(player, blockMenu.getLocation(), Interaction.INTERACT_BLOCK)
            ) {
                targetMenu.open(player);
            }
        }
    }

    @ParametersAreNonnullByDefault
    public void setDirection(BlockMenu blockMenu, BlockFace blockFace) {
        final BlockFace storedFace = isValidStoredDirection(blockFace) ? blockFace : BlockFace.SELF;
        final Location location = blockMenu.getLocation();
        SELECTED_DIRECTION_MAP.put(location.clone(), storedFace);
        BlockStorage.addBlockInfo(location, DIRECTION, storedFace.name());
        PersistentNetworkMetadata.setString(location, DIRECTION, storedFace.name());
        persistBlockMetadata(location);
    }

    @Nullable
    private static String getStoredDirectionString(@Nonnull Location location) {
        final String persistedDirection = PersistentNetworkMetadata.getString(location, DIRECTION);
        return persistedDirection != null ? persistedDirection : BlockStorage.getLocationInfo(location, DIRECTION);
    }

    @Nullable
    private static String getStoredOwnerString(@Nonnull Location location) {
        final String persistedOwner = PersistentNetworkMetadata.getString(location, OWNER_KEY);
        return persistedOwner != null ? persistedOwner : BlockStorage.getLocationInfo(location, OWNER_KEY);
    }

    @Nonnull
    protected int[] getBackgroundSlots() {
        return new int[]{
                0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 13, 14, 16, 17, 18, 19, 21, 23, 24, 25, 26, 27, 28, 29, 21, 31, 32, 34, 35, 36, 37, 38, 39, 40, 41, 42, 43, 44
        };
    }

    @Nullable
    protected int[] getOtherBackgroundSlots() {
        return null;
    }

    @Nullable
    protected SlimefunItemStack getOtherBackgroundStack() {
        return null;
    }

    public int getNorthSlot() {
        return NORTH_SLOT;
    }

    public int getSouthSlot() {
        return SOUTH_SLOT;
    }

    public int getEastSlot() {
        return EAST_SLOT;
    }

    public int getWestSlot() {
        return WEST_SLOT;
    }

    public int getUpSlot() {
        return UP_SLOT;
    }

    public int getDownSlot() {
        return DOWN_SLOT;
    }

    public int[] getItemSlots() {
        return new int[]{};
    }

    public int[] getInputSlots() { return new int[0]; }

    public int[] getOutputSlots() { return new int[0]; }

    @Override
    public boolean runSync() {
        return true;
    }

    @Nonnull
    public static ItemStack getDirectionalSlotPane(@Nonnull BlockFace blockFace, @Nonnull SlimefunItem slimefunItem, boolean active) {
        final ItemStack displayStack = createDisplayStack(
                slimefunItem.getItem(),
                Theme.PASSIVE + "Direction " + blockFace.name() + " (" + ChatColor.stripColor(slimefunItem.getItemName()) + ")"
        );
        final ItemMeta itemMeta = displayStack.getItemMeta();
        if (active) {
            itemMeta.addEnchant(Enchantment.LUCK_OF_THE_SEA, 1, true);
            itemMeta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        }
        itemMeta.setLore(List.of(
                Theme.CLICK_INFO + "Left Click: " + Theme.PASSIVE + "Set Direction",
                Theme.CLICK_INFO + "Shift Left Click: " + Theme.PASSIVE + "Open Target Block"
        ));
        displayStack.setItemMeta(itemMeta);
        return displayStack;
    }

    @Nonnull
    public static ItemStack getDirectionalSlotPane(@Nonnull BlockFace blockFace, @Nonnull Material blockMaterial, boolean active) {
        if (blockMaterial.isItem() && !blockMaterial.isAir()) {
            final ItemStack displayStack = createDisplayStack(
                    blockMaterial,
                    Theme.PASSIVE + "Direction " + blockFace.name() + " (" + blockMaterial.name() + ")"
            );
            final ItemMeta itemMeta = displayStack.getItemMeta();
            if (active) {
                itemMeta.addEnchant(Enchantment.LUCK_OF_THE_SEA, 1, true);
                itemMeta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            }
            itemMeta.setLore(List.of(
                    Theme.CLICK_INFO + "Left Click: " + Theme.PASSIVE + "Set Direction",
                    Theme.CLICK_INFO + "Shift Left Click: " + Theme.PASSIVE + "Open Target Block"
            ));
            displayStack.setItemMeta(itemMeta);
            return displayStack;
        } else {
            Material material = active ? Material.GREEN_STAINED_GLASS_PANE : Material.RED_STAINED_GLASS_PANE;
            return createDisplayStack(
                    material,
                    ChatColor.GRAY + "Set direction: " + blockFace.name()
            );
        }
    }

    @Nonnull
    private static ItemStack createDisplayStack(@Nonnull ItemStack itemStack, @Nonnull String name) {
        final ItemStack displayStack = itemStack.clone();
        final ItemMeta itemMeta = displayStack.getItemMeta();
        itemMeta.setDisplayName(name);
        displayStack.setItemMeta(itemMeta);
        return displayStack;
    }

    @Nonnull
    private static ItemStack createDisplayStack(@Nonnull Material material, @Nonnull String name) {
        return createDisplayStack(new ItemStack(material), name);
    }

    @Nullable
    public static BlockFace getSelectedFace(@Nonnull Location location) {
        final BlockFace cached = SELECTED_DIRECTION_MAP.get(location);
        if (cached != null) {
            return isValidStoredDirection(cached) ? cached : BlockFace.SELF;
        }

        final String storedDirection = getStoredDirectionString(location);
        if (storedDirection == null || storedDirection.isBlank()) {
            return BlockFace.SELF;
        }

        try {
            final BlockFace direction = BlockFace.valueOf(storedDirection);
            final BlockFace storedFace = isValidStoredDirection(direction) ? direction : BlockFace.SELF;
            SELECTED_DIRECTION_MAP.put(location.clone(), storedFace);
            return storedFace;
        } catch (IllegalArgumentException ignored) {
            return BlockFace.SELF;
        }
    }

    protected Particle.DustOptions getDustOptions() {
        return new Particle.DustOptions(Color.RED, 1);
    }

    protected void showParticle(@Nonnull Location location, @Nonnull BlockFace blockFace) {
        if (location.getWorld() == null) {
            return;
        }
        final Vector faceVector = blockFace.getDirection().clone().multiply(-1);
        final Vector pushVector = faceVector.clone().multiply(2);
        final Location displayLocation = location.clone().add(0.5, 0.5, 0.5).add(faceVector);
        location.getWorld().spawnParticle(Particle.DUST, displayLocation, 0, pushVector.getX(), pushVector.getY(), pushVector.getZ(), getDustOptions());
    }
}
