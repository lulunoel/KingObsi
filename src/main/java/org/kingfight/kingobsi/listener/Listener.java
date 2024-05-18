package org.kingfight.kingobsi.listener;

import org.kingfight.kingobsi.handlers.ConfigHandler;
import org.kingfight.kingobsi.kingobsiplugin;
import org.kingfight.kingobsi.model.DamagedBlock;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.Event.Result;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import com.massivecraft.factions.Board;
import com.massivecraft.factions.FLocation;
import com.massivecraft.factions.FPlayers;
import com.massivecraft.factions.Faction;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.HashSet;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;

import com.massivecraft.factions.FPlayer;

public class Listener implements org.bukkit.event.Listener {

    private final kingobsiplugin plugin;
    private final Map<Faction, ConcurrentLinkedQueue<Long>> explosionTimestamps;
    private final Set<Faction> factionCooldowns;

    public Listener(kingobsiplugin blowablePlugin) {
        this.plugin = blowablePlugin;
        this.explosionTimestamps = new ConcurrentHashMap<>();
        this.factionCooldowns = ConcurrentHashMap.newKeySet();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent e) {
        handleExplosionEvent(e.getBlock().getLocation(), e.blockList());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent e) {
        handleExplosionEvent(e.getLocation(), e.blockList());
    }

    private void handleExplosionEvent(Location location, List<Block> blockList) {
        Faction faction = getFactionInChunk(location.getChunk());
        if (faction == null) return;

        // Check if the faction is one of the excluded factions
        if (isExcludedFaction(faction)) {
            return;
        }

        long currentTime = System.currentTimeMillis();

        // Check if faction is on cooldown
        if (factionCooldowns.contains(faction)) {
            notifyNearbyPlayers(location, "Les explosions sont désactivées pendant 3 secondes.");
            blockList.clear(); // Annuler les dégâts
            return;
        }

        // Track explosions
        explosionTimestamps.putIfAbsent(faction, new ConcurrentLinkedQueue<>());
        ConcurrentLinkedQueue<Long> timestamps = explosionTimestamps.get(faction);
        timestamps.add(currentTime);

        // Remove timestamps older than the cooldown window
        while (!timestamps.isEmpty() && timestamps.peek() < currentTime - TimeUnit.SECONDS.toMillis(3)) {
            timestamps.poll();
        }

        // Check if explosion limit is exceeded
        if (timestamps.size() > 8) {
            factionCooldowns.add(faction);
            notifyNearbyPlayers(location, "Les explosions sont désactivées pendant 3 secondes.");
            blockList.clear(); // Annuler les dégâts

            // Schedule removal from cooldown
            plugin.getServer().getScheduler().scheduleSyncDelayedTask(plugin, () -> {
                factionCooldowns.remove(faction);
            }, 60L); // 3 seconds in game ticks (20 ticks per second)
        } else {
            if (isFactionOfflineLongEnough(location, faction)) {
                notifyNearbyPlayers(location, "Explosion annulée car la faction est hors ligne depuis plus de 30 minutes.");
                blockList.clear(); // Annuler les dégâts
                return;
            }
            List<Block> result = onBoom(location, blockList, ConfigHandler.getDefaultDamage(null), ConfigHandler.getDefaultRadius(null));
            for (Block b : blockList) {
                if (!result.contains(b)) {
                    blockList.remove(b);
                }
            }
        }
    }

    private boolean isExcludedFaction(Faction faction) {
        String factionName = faction.getTag();
        return factionName.equals("§2Wilderness") || factionName.equals("§4Warzone") || factionName.equals("§6Safezone");
    }

    private List<Block> onBoom(Location source, List<Block> blocks, double damage, double dmgRadius) {
        int radius = (int) Math.ceil(dmgRadius);

        blocks.removeIf(ConfigHandler::makeBlowable);

        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    Location loc = new Location(source.getWorld(), x + source.getX(), y + source.getY(), z + source.getZ());
                    if (source.distance(loc) <= dmgRadius) {
                        Block block = damageBlock(loc.getBlock(), source, damage);
                        if (block != null) blocks.add(block);
                    }
                }
            }
        }
        return blocks;
    }

    public Block damageBlock(Block block, Location source, double damage) {
        if (ConfigHandler.makeBlowable(block)) {
            // Check if source is liquid
            if (source != null && source.getBlock().isLiquid()) {
                damage *= plugin.getConfig().getDouble("Liquid Multiplier");
            }

            // Damage the block
            if (damage > 0) {
                DamagedBlock dmgBlock = new DamagedBlock(block);
                if (dmgBlock.damage(damage)) {
                    DamagedBlock.clean(block);
                    return block;
                }
            }
        }
        return null;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent e) {
        if (!e.isCancelled()) {
            Block block = e.getBlock();
            DamagedBlock.clean(block);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent e) {
        if (e.useInteractedBlock() != Result.ALLOW) return;

        if (e.getAction().toString().equalsIgnoreCase(plugin.getConfig().getString("Check.Type"))
                && e.getPlayer().getInventory().getItemInHand().getType() == Material.valueOf(plugin.getConfig().getString("Check.Item"))) {
            Block block = e.getClickedBlock();
            if (block != null) {
                Optional<DamagedBlock> optDmgBlock = DamagedBlock.get(block);
                if (optDmgBlock.isPresent()) {
                    DamagedBlock dmgBlock = optDmgBlock.get();
                    int percent = (int) (((dmgBlock.getHealth() * 100) / ConfigHandler.getDefaultHealth(block.getType())));
                    int health = (int) Math.round(dmgBlock.getHealth());
                    String msg = ChatColor.translateAlternateColorCodes('&', plugin.getConfig().getString("Message.Block Health")
                            .replaceFirst("<percent>", String.valueOf(percent))
                            .replaceFirst("<health>", String.valueOf(health)));
                    e.getPlayer().sendMessage(msg);
                } else if (ConfigHandler.makeBlowable(block) && plugin.getConfig().getBoolean("Always Send Health")) {
                    int health = (int) Math.round(ConfigHandler.getDefaultHealth(block.getType()));
                    String msg = ChatColor.translateAlternateColorCodes('&', plugin.getConfig().getString("Message.Block Health")
                            .replaceFirst("<percent>", "100")
                            .replaceFirst("<health>", String.valueOf(health)));
                    e.getPlayer().sendMessage(msg);
                }
            }
        }

        if (isFactionOfflineLongEnough(e.getClickedBlock().getLocation(), getFactionInChunk(e.getClickedBlock().getLocation().getChunk()))) {
            notifyNearbyPlayers(e.getClickedBlock().getLocation(), "Interaction annulée car la faction est hors ligne depuis plus de 30 minutes.");
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        // Update the last member logout time when someone logs out
        if (event.getPlayer() != null) {
            Faction faction = FPlayers.getInstance().getByPlayer(event.getPlayer()).getFaction();
            if (faction != null && getOnlineMembersOfFaction(faction).isEmpty()) {
                // This part is no longer needed since we use getLastLoginTime()
            }
        }
    }

    private Set<Player> getOnlineMembersOfFaction(Faction faction) {
        Set<Player> onlineMembers = new HashSet<>();
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (FPlayers.getInstance().getByPlayer(player).getFaction().equals(faction)) {
                onlineMembers.add(player);
            }
        }
        return onlineMembers;
    }

    private boolean isFactionOfflineLongEnough(Location location, Faction faction) {
        // Check if the faction is one of the excluded factions
        if (isExcludedFaction(faction)) {
            return false;
        }

        if (faction != null) {
            long currentTime = System.currentTimeMillis();
            int offlineTimeThreshold = (int) plugin.getConfig().getDouble("OfflineTime", 30) * 60 * 1000; // 30 minutes in milliseconds

            boolean allMembersOfflineLongEnough = true;

            for (FPlayer fPlayer : faction.getFPlayers()) {
                long lastLoginTime = fPlayer.getLastLoginTime();
                if (currentTime - lastLoginTime < offlineTimeThreshold) {
                    allMembersOfflineLongEnough = false;
                    break;
                }
            }

            return allMembersOfflineLongEnough;
        }
        return false;
    }

    private Faction getFactionInChunk(org.bukkit.Chunk chunk) {
        String worldName = chunk.getWorld().getName();
        int x = chunk.getX();
        int z = chunk.getZ();

        FLocation flocation = new FLocation(worldName, x, z);
        return Board.getInstance().getFactionAt(flocation);
    }

    private void notifyNearbyPlayers(Location location, String message) {
        double radius = plugin.getConfig().getDouble("NotificationRadius", 50.0); // Par défaut 50 blocs de rayon
        for (Player player : location.getWorld().getPlayers()) {
            if (player.getLocation().distance(location) <= radius) {
                player.sendMessage(ChatColor.translateAlternateColorCodes('&', message));
            }
        }
    }
}
