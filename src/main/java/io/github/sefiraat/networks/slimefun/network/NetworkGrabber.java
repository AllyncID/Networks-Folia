package io.github.sefiraat.networks.slimefun.network;

import io.github.sefiraat.networks.NetworkStorage;
import io.github.sefiraat.networks.network.NodeDefinition;
import io.github.sefiraat.networks.network.NodeType;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun4.libraries.dough.protection.Interaction;
import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType;
import me.mrCookieSlime.Slimefun.api.BlockStorage;
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenu;
import me.mrCookieSlime.Slimefun.api.item_transport.ItemTransportFlow;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Particle;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.UUID;

public class NetworkGrabber extends NetworkDirectional {


    public NetworkGrabber(ItemGroup itemGroup, SlimefunItemStack item, RecipeType recipeType, ItemStack[] recipe) {
        super(itemGroup, item, recipeType, recipe, NodeType.GRABBER);
    }

    @Override
    protected void onTick(@Nullable BlockMenu blockMenu, @Nonnull Block block) {
        super.onTick(blockMenu, block);
        if (blockMenu != null) {
            tryGrabItem(blockMenu);
        }
    }

    private void tryGrabItem(@Nonnull BlockMenu blockMenu) {
        final NodeDefinition definition = NetworkStorage.getAllNetworkObjects().get(blockMenu.getLocation());

        if (definition == null || definition.getNode() == null) {
            return;
        }

        final BlockFace direction = this.getCurrentDirection(blockMenu);
        final Block targetBlock = getAdjacentOwnedBlock(blockMenu.getBlock(), direction);

        if (targetBlock == null) {
            return;
        }

        final BlockMenu targetMenu = BlockStorage.getInventory(targetBlock);

        if (targetMenu != null) {
            tryGrabFromMenu(blockMenu, targetMenu, definition, direction);
            return;
        }

        tryGrabFromInventory(blockMenu, targetBlock, definition, direction);
    }

    private void tryGrabFromMenu(@Nonnull BlockMenu sourceMenu,
                                 @Nonnull BlockMenu targetMenu,
                                 @Nonnull NodeDefinition definition,
                                 @Nonnull BlockFace direction
    ) {

        final int[] slots = getTransportSlots(targetMenu, ItemTransportFlow.WITHDRAW, null);
        if (slots.length == 0) {
            return;
        }

        for (int slot : slots) {
            final ItemStack itemStack = targetMenu.getItemInSlot(slot);

            if (itemStack != null && itemStack.getType() != Material.AIR) {
                int before = itemStack.getAmount();
                definition.getNode().getRoot().addItemStack(itemStack);
                if (itemStack.getAmount() < before) {
                    targetMenu.markDirty();
                }
                if (definition.getNode().getRoot().isDisplayParticles() && itemStack.getAmount() < before) {
                    showParticle(sourceMenu.getLocation(), direction);
                }
                break;
            }
        }
    }

    private void tryGrabFromInventory(@Nonnull BlockMenu sourceMenu,
                                      @Nonnull Block targetBlock,
                                      @Nonnull NodeDefinition definition,
                                      @Nonnull BlockFace direction
    ) {
        final UUID uuid = getOwnerUuid(sourceMenu.getLocation());
        if (uuid == null) {
            return;
        }

        final OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(uuid);
        if (!Slimefun.getProtectionManager().hasPermission(offlinePlayer, targetBlock, Interaction.INTERACT_BLOCK)) {
            return;
        }

        final BlockState blockState = targetBlock.getState();
        if (!(blockState instanceof InventoryHolder holder)) {
            return;
        }

        final ItemStack[] contents = holder.getInventory().getContents();
        for (int slot = contents.length - 1; slot >= 0; slot--) {
            final ItemStack itemStack = contents[slot];
            if (itemStack == null || itemStack.getType() == Material.AIR) {
                continue;
            }

            final int before = itemStack.getAmount();
            definition.getNode().getRoot().addItemStack(itemStack);
            if (definition.getNode().getRoot().isDisplayParticles() && itemStack.getAmount() < before) {
                showParticle(sourceMenu.getLocation(), direction);
            }
            break;
        }
    }

    @Override
    protected Particle.DustOptions getDustOptions() {
        return new Particle.DustOptions(Color.FUCHSIA, 1);
    }
}
