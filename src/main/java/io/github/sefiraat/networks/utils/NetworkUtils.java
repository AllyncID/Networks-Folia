package io.github.sefiraat.networks.utils;

import com.jeff_media.morepersistentdatatypes.DataType;

import io.github.sefiraat.networks.NetworkStorage;
import io.github.sefiraat.networks.network.NetworkNode;
import io.github.sefiraat.networks.network.NodeDefinition;
import io.github.sefiraat.networks.network.NodeType;
import io.github.sefiraat.networks.slimefun.network.NetworkController;
import io.github.sefiraat.networks.slimefun.network.NetworkDirectional;
import io.github.sefiraat.networks.slimefun.network.NetworkPusher;
import io.github.sefiraat.networks.slimefun.tools.NetworkConfigurator;
import io.github.sefiraat.networks.utils.datatypes.DataTypeMethods;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenu;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class NetworkUtils {

    public static void applyConfig(@Nonnull NetworkDirectional directional, @Nullable BlockMenu blockMenu, @Nonnull Player player) {
        if (blockMenu == null) {
            return;
        }

        ItemStack itemStack = player.getInventory().getItemInOffHand();

        if (itemStack != null && SlimefunItem.getByItem(itemStack) instanceof NetworkConfigurator) {
            applyConfig(directional, itemStack, blockMenu, player);
        }
    }

    public static void applyConfig(@Nonnull NetworkDirectional directional, @Nonnull ItemStack itemStack, @Nullable BlockMenu blockMenu, @Nonnull Player player) {
        if (blockMenu == null) {
            player.sendMessage(Theme.ERROR + "This Networks block is not ready yet. Please try again in a moment.");
            return;
        }

        final ItemMeta itemMeta = itemStack.getItemMeta();
        if (itemMeta == null) {
            player.sendMessage(Theme.ERROR + "The held configurator has invalid metadata.");
            return;
        }

        final ItemStack[] templateStacks = DataTypeMethods.getCustom(itemMeta, Keys.ITEM, DataType.ITEM_STACK_ARRAY);
        final String string = DataTypeMethods.getCustom(itemMeta, Keys.FACE, DataType.STRING);

        if (string == null) {
            player.sendMessage(Theme.ERROR + "Direction: " + Theme.PASSIVE + "Not supplied");
            return;
        }

        final BlockFace blockFace;
        try {
            blockFace = BlockFace.valueOf(string);
        } catch (IllegalArgumentException ignored) {
            player.sendMessage(Theme.ERROR + "Direction: " + Theme.PASSIVE + "Invalid stored direction");
            return;
        }

        directional.setDirection(blockMenu, blockFace);
        player.sendMessage(Theme.SUCCESS + "Direction: " + Theme.PASSIVE + "Successfully applied");


        final int[] itemSlots = directional.getItemSlots();
        if (itemSlots.length > 0) {
            for (int slot : itemSlots) {
                final ItemStack stackToDrop = blockMenu.getItemInSlot(slot);
                if (stackToDrop != null && stackToDrop.getType() != Material.AIR) {
                    blockMenu.getLocation().getWorld().dropItem(blockMenu.getLocation(), stackToDrop.clone());
                    stackToDrop.setAmount(0);
                }
            }
        }

        if (templateStacks != null) {
            int i = 0;
            for (ItemStack templateStack : templateStacks) {
                if (i >= itemSlots.length) {
                    break;
                }

                if (templateStack != null && templateStack.getType() != Material.AIR) {
                    boolean worked = false;
                    for (ItemStack stack : player.getInventory()) {
                        if (StackUtils.itemsMatch(stack, templateStack)) {
                            final ItemStack stackClone = StackUtils.getAsQuantity(stack, 1);
                            stack.setAmount(stack.getAmount() - 1);
                            blockMenu.replaceExistingItem(itemSlots[i], stackClone);
                            player.sendMessage(Theme.SUCCESS + "Item [" + i + "]: " + Theme.PASSIVE + "Item added into filter");
                            worked = true;
                            break;
                        }
                    }
                    if (!worked) {
                        player.sendMessage(Theme.WARNING + "Item [" + i + "]: " + Theme.PASSIVE + "Not enough items to fill filter");
                    }
                } else if (directional instanceof NetworkPusher) {
                    player.sendMessage(Theme.WARNING + "Item [" + i + "]: " + Theme.PASSIVE + "No item in stored config");
                }
                i++;
            }
        } else {
            player.sendMessage(Theme.WARNING + "Items: " + Theme.PASSIVE + "No items in stored config");
        }

        blockMenu.markDirty();
        directional.persistBlockMetadata(blockMenu.getLocation());
    }

    public static void clearNetwork(Location location) {
        NodeDefinition definition = NetworkStorage.getAllNetworkObjects().get(location);

        if (definition == null || definition.getNode() == null) {
            return;
        }

        NetworkNode node = definition.getNode();

        if (node != null && node.getNodeType() == NodeType.CONTROLLER) {
            NetworkController.wipeNetwork(location);
        }

        NetworkStorage.removeNode(location);
    }
}
