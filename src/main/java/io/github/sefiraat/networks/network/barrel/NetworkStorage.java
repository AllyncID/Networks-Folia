package io.github.sefiraat.networks.network.barrel;

import io.github.sefiraat.networks.network.stackcaches.BarrelIdentity;
import io.github.sefiraat.networks.network.stackcaches.ItemRequest;
import io.github.sefiraat.networks.network.stackcaches.QuantumCache;
import io.github.sefiraat.networks.slimefun.network.NetworkQuantumStorage;
import io.github.sefiraat.networks.utils.FoliaSupport;
import me.mrCookieSlime.Slimefun.api.BlockStorage;
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenu;
import org.bukkit.Location;
import org.bukkit.inventory.ItemStack;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class NetworkStorage extends BarrelIdentity {

    public NetworkStorage(Location location, ItemStack itemStack, int amount) {
        super(location, itemStack, amount, BarrelType.NETWORKS);
    }

    @Override
    @Nullable
    public ItemStack requestItem(@Nonnull ItemRequest itemRequest) {
        final Location location = this.getLocation();
        final QuantumCache cache = NetworkQuantumStorage.getCaches().get(location);

        if (cache == null) {
            return null;
        }

        if (FoliaSupport.isOwnedByCurrentRegion(location)) {
            final BlockMenu blockMenu = BlockStorage.getInventory(location);
            if (blockMenu != null) {
                return NetworkQuantumStorage.getItemStack(cache, blockMenu, itemRequest.getAmount());
            }
        }

        return NetworkQuantumStorage.getItemStack(location, cache, itemRequest.getAmount());
    }

    @Override
    public void depositItemStack(ItemStack[] itemsToDeposit) {
        final QuantumCache cache = NetworkQuantumStorage.getCaches().get(this.getLocation());
        if (cache != null) {
            NetworkQuantumStorage.tryInputItem(this.getLocation(), itemsToDeposit, cache);
        }
    }


    @Override
    public int getInputSlot() {
        return NetworkQuantumStorage.INPUT_SLOT;
    }

    @Override
    public int getOutputSlot() {
        return NetworkQuantumStorage.OUTPUT_SLOT;
    }
}
