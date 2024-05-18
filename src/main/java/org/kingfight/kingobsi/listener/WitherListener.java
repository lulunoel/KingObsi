package org.kingfight.kingobsi.listener;

import org.kingfight.kingobsi.handlers.ConfigHandler;
import org.bukkit.block.Block;
import org.bukkit.entity.Wither;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.kingfight.kingobsi.kingobsiplugin;

public class WitherListener implements Listener {

    private kingobsiplugin plugin;

    public WitherListener(kingobsiplugin kingobsiplugin) {
        plugin = kingobsiplugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onWitherGrief(EntityChangeBlockEvent event) {
        if (!(event.getEntity() instanceof Wither)) return;
        Block block = plugin.Listener.damageBlock(event.getBlock(), null, ConfigHandler.getWitherMoveDamage());
        if (block == null) {
            event.setCancelled(true);
        }
    }
}