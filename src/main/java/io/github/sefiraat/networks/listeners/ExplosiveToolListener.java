package io.github.sefiraat.networks.listeners;

import io.github.sefiraat.networks.slimefun.network.NetworkController;
import io.github.sefiraat.networks.slimefun.network.NetworkQuantumStorage;
import io.github.thebusybiscuit.slimefun4.api.events.ExplosiveToolBreakBlocksEvent;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.EntityExplodeEvent;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class ExplosiveToolListener implements Listener {

    @EventHandler
    public void onExplosiveBlockBreak(@Nonnull ExplosiveToolBreakBlocksEvent event) {
        final List<Block> blocksToRemove = new ArrayList<>();
        for (Block block : event.getAdditionalBlocks()) {
            final Location location = block.getLocation();
            // Cek apakah blok tersebut adalah Quantum Storage atau Network Controller
            if (NetworkQuantumStorage.getCaches().containsKey(location) || NetworkController.getNetworks().containsKey(location)) {
                blocksToRemove.add(block);
            }
        }
        event.getAdditionalBlocks().removeAll(blocksToRemove);
    }

    @EventHandler
    public void onEntityExplode(@Nonnull EntityExplodeEvent event) {
        final Iterator<Block> iterator = event.blockList().iterator();
        while (iterator.hasNext()) {
            final Block block = iterator.next();
            final Location location = block.getLocation();
            // Lindungi dari ledakan entity (misal: Creeper, TNT)
            if (NetworkQuantumStorage.getCaches().containsKey(location) || NetworkController.getNetworks().containsKey(location)) {
                iterator.remove();
            }
        }
    }

    @EventHandler
    public void onBlockExplode(@Nonnull BlockExplodeEvent event) {
        final Iterator<Block> iterator = event.blockList().iterator();
        while (iterator.hasNext()) {
            final Block block = iterator.next();
            final Location location = block.getLocation();
            // Lindungi dari ledakan blok lain
            if (NetworkQuantumStorage.getCaches().containsKey(location) || NetworkController.getNetworks().containsKey(location)) {
                iterator.remove();
            }
        }
    }
}