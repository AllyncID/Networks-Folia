package io.github.sefiraat.networks.slimefun.groups;

import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;

import javax.annotation.ParametersAreNonnullByDefault;

public class DummyItemGroup extends ItemGroup {

    @ParametersAreNonnullByDefault
    public DummyItemGroup(NamespacedKey key, SlimefunItemStack item) {
        super(key, item.item());
    }

    @Override
    @ParametersAreNonnullByDefault
    public boolean isHidden(Player p) {
        return true;
    }

}

