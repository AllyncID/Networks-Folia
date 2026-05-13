package io.github.sefiraat.networks.slimefun.network;

import io.github.sefiraat.networks.NetworkStorage;
import io.github.sefiraat.networks.network.NetworkRoot;
import io.github.sefiraat.networks.network.NodeDefinition;
import io.github.sefiraat.networks.network.NodeType;
import io.github.sefiraat.networks.utils.Theme;
import io.github.thebusybiscuit.slimefun4.api.events.PlayerRightClickEvent;
import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType;
import io.github.thebusybiscuit.slimefun4.core.handlers.BlockBreakHandler;
import io.github.thebusybiscuit.slimefun4.core.handlers.BlockPlaceHandler;
import io.github.thebusybiscuit.slimefun4.core.handlers.ItemUseHandler;

import lombok.Getter;
import me.mrCookieSlime.CSCoreLibPlugin.Configuration.Config;
import me.mrCookieSlime.Slimefun.Objects.handlers.BlockTicker;
import me.mrCookieSlime.Slimefun.api.BlockStorage;
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenu;
import org.bukkit.Location;
import org.bukkit.block.BlockFace;
import io.github.sefiraat.networks.utils.DeferredBlockStorageSave;
import org.bukkit.block.Block;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.ItemStack;

import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public abstract class NetworkObject extends SlimefunItem implements AdminDebuggable {

    @Getter
    private final NodeType nodeType;
    private final List<Integer> slotsToDrop = new ArrayList<>();

    public List<Integer> getSlotsToDrop() {
        return this.slotsToDrop;
    }

    protected static final Set<BlockFace> CHECK_FACES = Set.of(
        BlockFace.UP,
        BlockFace.DOWN,
        BlockFace.NORTH,
        BlockFace.SOUTH,
        BlockFace.EAST,
        BlockFace.WEST
    );


    protected NetworkObject(ItemGroup itemGroup, SlimefunItemStack item, RecipeType recipeType, ItemStack[] recipe, NodeType type) {
        this(itemGroup, item, recipeType, recipe, null, type);
    }

    protected NetworkObject(ItemGroup itemGroup, SlimefunItemStack item, RecipeType recipeType, ItemStack[] recipe, ItemStack recipeOutput, NodeType type) {
        super(itemGroup, item, recipeType, recipe, recipeOutput);
        this.nodeType = type;
        addItemHandler(
            new BlockTicker() {

                @Override
                public boolean isSynchronized() {
                    return runSync();
                }

                @Override
                public void tick(Block b, SlimefunItem item, Config data) {
                    addToRegistry(b);
                }
            },
            new BlockBreakHandler(false, false) {
                @Override
                @ParametersAreNonnullByDefault
                public void onPlayerBreak(BlockBreakEvent event, ItemStack item, List<ItemStack> drops) {
                    preBreak(event);
                    onBreak(event);
                }
            },
            new BlockPlaceHandler(false) {
                @Override
                public void onPlayerPlace(@Nonnull BlockPlaceEvent blockPlaceEvent) {
                    onPlace(blockPlaceEvent);
                }
            },
            new ItemUseHandler() {
                @Override
                public void onRightClick(PlayerRightClickEvent playerRightClickEvent) {
                    prePlace(playerRightClickEvent);
                }
            }
        );
    }

    protected void addToRegistry(@Nonnull Block block) {
        final Location location = block.getLocation();
        final NodeDefinition existingDefinition = NetworkStorage.getAllNetworkObjects().get(location);
        final int currentCharge = getCurrentCharge(location);

        if (existingDefinition == null) {
            final NodeDefinition nodeDefinition = new NodeDefinition(nodeType, currentCharge);
            NetworkStorage.getAllNetworkObjects().put(location, nodeDefinition);
            rebuildAdjacentNetwork(location);
            return;
        }

        if (nodeType == NodeType.POWER_NODE) {
            existingDefinition.setCharge(currentCharge);
        }
    }

    private int getCurrentCharge(@Nonnull Location location) {
        if (this instanceof NetworkPowerNode powerNode) {
            return powerNode.getCharge(location);
        }

        return 0;
    }

    protected void rebuildAdjacentNetwork(@Nonnull Location location) {
        for (BlockFace blockFace : CHECK_FACES) {
            final Location testLocation = location.clone().add(blockFace.getDirection());
            final SlimefunItem slimefunItem = BlockStorage.check(testLocation);

            if (slimefunItem instanceof NetworkController controller) {
                NetworkController.scheduleRebuild(testLocation, controller.getConfiguredMaxNodes());
                return;
            }

            final NodeDefinition definition = NetworkStorage.getAllNetworkObjects().get(testLocation);
            if (definition == null || definition.getNode() == null || definition.getNode().getRoot() == null) {
                continue;
            }

            final Location controllerLocation = definition.getNode().getRoot().getController();
            if (controllerLocation != null) {
                final SlimefunItem controllerItem = BlockStorage.check(controllerLocation);
                if (controllerItem instanceof NetworkController controller) {
                    NetworkController.scheduleRebuild(controllerLocation, controller.getConfiguredMaxNodes());
                    return;
                }
            }
        }
    }

    protected void preBreak(@Nonnull BlockBreakEvent event) {

    }

    protected void onBreak(@Nonnull BlockBreakEvent event) {
        final Location location = event.getBlock().getLocation();
        final BlockMenu blockMenu = BlockStorage.getInventory(event.getBlock());

        if (blockMenu != null) {
            for (int i : this.slotsToDrop) {
                blockMenu.dropItems(location, i);
            }
        }
//        NetworkStorage.removeNode(location);
//
//        if (this.nodeType == NodeType.CONTROLLER) {
//            NetworkController.wipeNetwork(location);
//        }

        BlockStorage.clearBlockInfo(location);
    }

    protected void prePlace(@Nonnull PlayerRightClickEvent event) {
        Optional<Block> blockOptional = event.getClickedBlock();
        Location controllerLocation = null;

        if (blockOptional.isPresent()) {
            Block block = blockOptional.get();
            Block target = block.getRelative(event.getClickedFace());

            addToRegistry(block);
            for (BlockFace checkFace : CHECK_FACES) {
                Block checkBlock = target.getRelative(checkFace);

                // Check for node definitions. If there isn't one, we don't care
                NodeDefinition definition = NetworkStorage.getAllNetworkObjects().get(checkBlock.getLocation());
                if (definition == null) {
                    continue;
                }

                // There is a definition, if it has a node, then it's part of an active network.
                if (definition.getNode() != null) {
                    NetworkRoot networkRoot = definition.getNode().getRoot();
                    if (controllerLocation == null) {
                        // First network found, store root location
                        controllerLocation = networkRoot.getController();
                    } else if (!controllerLocation.equals(networkRoot.getController())) {
                        // Location differs from that previously recorded, would result in two controllers
                        cancelPlace(event);
                    }
                }
            }
        }
    }

    protected void cancelPlace(PlayerRightClickEvent event) {
        event.getPlayer().sendMessage(Theme.ERROR.getColor() + "This placement would connect two controllers!");
        event.cancel();
    }

    protected void onPlace(@Nonnull BlockPlaceEvent event) {
        persistBlockMetadata(event.getBlock().getLocation());
    }

    public void persistBlockMetadata(@Nonnull Location location) {
        final String storedId = BlockStorage.getLocationInfo(location, "id");
        if (storedId == null || storedId.isBlank() || !storedId.equals(getId())) {
            BlockStorage.addBlockInfo(location, "id", getId(), true);
        }

        DeferredBlockStorageSave.markDirty(location);
    }

    public boolean runSync() {
        return false;
    }
}
