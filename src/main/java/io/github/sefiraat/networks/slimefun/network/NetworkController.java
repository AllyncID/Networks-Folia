package io.github.sefiraat.networks.slimefun.network;

import io.github.sefiraat.networks.NetworkStorage;
import io.github.sefiraat.networks.network.NetworkNode;
import io.github.sefiraat.networks.network.NetworkRoot;
import io.github.sefiraat.networks.network.NodeDefinition;
import io.github.sefiraat.networks.network.NodeType;
import io.github.sefiraat.networks.utils.Theme;
import io.github.thebusybiscuit.slimefun4.api.events.PlayerRightClickEvent;
import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.ItemSetting;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun4.api.items.settings.IntRangeSetting;
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType;
import me.mrCookieSlime.CSCoreLibPlugin.Configuration.Config;
import me.mrCookieSlime.Slimefun.Objects.handlers.BlockTicker;
import me.mrCookieSlime.Slimefun.api.BlockStorage;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.ItemStack;

import javax.annotation.Nonnull;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class NetworkController extends NetworkObject {

    private static final String CRAYON = "crayon";
    private static final Map<Location, NetworkRoot> NETWORKS = new ConcurrentHashMap<>();
    private static final Set<Location> CRAYONS = ConcurrentHashMap.newKeySet();
    // Cooldown Map
    private static final Map<UUID, Long> PLACEMENT_COOLDOWN = new ConcurrentHashMap<>();
    private static final int COOLDOWN_SECONDS = 8;

    private final ItemSetting<Integer> maxNodes;
    protected final Map<Location, Boolean> firstTickMap = new ConcurrentHashMap<>();

    public NetworkController(ItemGroup itemGroup, SlimefunItemStack item, RecipeType recipeType, ItemStack[] recipe) {
        super(itemGroup, item, recipeType, recipe, NodeType.CONTROLLER);

        maxNodes = new IntRangeSetting(this, "max_nodes", 10, 2000, 5000);
        addItemSetting(maxNodes);

        addItemHandler(
                new BlockTicker() {
                    @Override
                    public boolean isSynchronized() {
                        return true;
                    }

                    @Override
                    public void tick(Block block, SlimefunItem item, Config data) {
                        if (!NETWORKS.containsKey(block.getLocation())) {
                            if (!firstTickMap.containsKey(block.getLocation())) {
                                onFirstTick(block, data);
                                firstTickMap.put(block.getLocation(), true);
                            }

                            addToRegistry(block);
                            rebuildNetwork(block.getLocation(), getConfiguredMaxNodes());
                        }

                        NetworkRoot root = NETWORKS.get(block.getLocation());
                        if (root != null) {
                            boolean crayon = CRAYONS.contains(block.getLocation());
                            if (crayon) {
                                root.setDisplayParticles(true);
                            } else {
                                root.setDisplayParticles(false);
                            }
                        }
                    }
                }
        );
    }

    @Override
    protected void prePlace(@Nonnull PlayerRightClickEvent event) {
        // Cooldown Logic
        if (PLACEMENT_COOLDOWN.containsKey(event.getPlayer().getUniqueId())) {
            long lastPlaced = PLACEMENT_COOLDOWN.get(event.getPlayer().getUniqueId());
            long timeElapsed = System.currentTimeMillis() - lastPlaced;

            if (timeElapsed < COOLDOWN_SECONDS * 1000L) {
                long timeLeft = (COOLDOWN_SECONDS * 1000L - timeElapsed) / 1000L;
                event.getPlayer().sendMessage(ChatColor.RED + "You must wait " + (timeLeft + 1) + " seconds before placing another Network Controller.");
                event.cancel();
                return;
            }
        }

        Optional<Block> blockOptional = event.getClickedBlock();

        if (blockOptional.isPresent()) {
            Block block = blockOptional.get();
            Block target = block.getRelative(event.getClickedFace());

            for (BlockFace checkFace : CHECK_FACES) {
                Block checkBlock = target.getRelative(checkFace);
                SlimefunItem slimefunItem = BlockStorage.check(checkBlock);

                // For directly adjacent controllers
                if (slimefunItem instanceof NetworkController) {
                    cancelPlace(event);
                    return;
                }

                // Check for node definitions. If there isn't one, we don't care
                NodeDefinition definition = NetworkStorage.getAllNetworkObjects().get(checkBlock.getLocation());
                if (definition == null) {
                    continue;
                }

                // There is a definition, if it has a node, then it's part of an active network.
                if (definition.getNode() != null) {
                    cancelPlace(event);
                    return;
                }
            }
        }
    }

    @Override
    protected void onPlace(@Nonnull BlockPlaceEvent event) {
        super.onPlace(event);
        PLACEMENT_COOLDOWN.put(event.getPlayer().getUniqueId(), System.currentTimeMillis());
    }

    @Override
    protected void onBreak(@Nonnull BlockBreakEvent event) {
        super.onBreak(event);
        // Reset/Set cooldown saat block dihancurkan juga
        PLACEMENT_COOLDOWN.put(event.getPlayer().getUniqueId(), System.currentTimeMillis());
    }

    @Override
    protected void cancelPlace(PlayerRightClickEvent event) {
        event.getPlayer().sendMessage(Theme.ERROR.getColor() + "This network already has a controller!");
        event.cancel();
    }

    private void onFirstTick(@Nonnull Block block, @Nonnull Config data) {
        final String crayon = data.getString(CRAYON);
        if (Boolean.parseBoolean(crayon)) {
            CRAYONS.add(block.getLocation());
        }
    }

    public int getConfiguredMaxNodes() {
        return maxNodes.getValue();
    }

    public static Map<Location, NetworkRoot> getNetworks() {
        return NETWORKS;
    }

    public static Set<Location> getCrayons() {
        return CRAYONS;
    }

    public static void addCrayon(@Nonnull Location location) {
        BlockStorage.addBlockInfo(location, CRAYON, String.valueOf(true));
        CRAYONS.add(location);
    }

    public static void removeCrayon(@Nonnull Location location) {
        BlockStorage.addBlockInfo(location, CRAYON, null);
        CRAYONS.remove(location);
    }

    public static boolean hasCrayon(@Nonnull Location location) {
        return CRAYONS.contains(location);
    }

    public static synchronized void rebuildNetwork(@Nonnull Location location, int maxNodes) {
        final NetworkRoot existingRoot = NETWORKS.remove(location);
        if (existingRoot != null) {
            clearAssignedNodes(existingRoot);
        }

        final NodeDefinition definition = NetworkStorage.getAllNetworkObjects().get(location);
        if (definition == null) {
            return;
        }

        final NetworkRoot networkRoot = new NetworkRoot(location, NodeType.CONTROLLER, maxNodes);
        networkRoot.addAllChildren();
        definition.setNode(networkRoot);
        NetworkStorage.getAllNetworkObjects().put(location, definition);
        NETWORKS.put(location, networkRoot);
    }

    private static void clearAssignedNodes(@Nonnull NetworkNode node) {
        final NodeDefinition definition = NetworkStorage.getAllNetworkObjects().get(node.getNodePosition());
        if (definition != null) {
            definition.setNode(null);
        }

        for (NetworkNode childNode : node.getChildrenNodes()) {
            clearAssignedNodes(childNode);
        }
    }

    public static void wipeNetwork(@Nonnull Location location) {
        NetworkRoot networkRoot = NETWORKS.remove(location);
        if (networkRoot != null) {
            for (NetworkNode node : networkRoot.getChildrenNodes()) {
                NetworkStorage.removeNode(node.getNodePosition());
            }
        }
    }
}
