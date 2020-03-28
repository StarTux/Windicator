package com.cavetale.windicator;

import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.SpawnerSpawnEvent;
import org.bukkit.inventory.ItemStack;

@RequiredArgsConstructor
public final class EventListener implements Listener {
    final WindicatorPlugin plugin;

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        if (!plugin.windicator.isValid()) return;
        Block block = event.getBlock();
        if (!plugin.windicator.isInWorld(block)) return;
        String name = plugin.windicator.coreAt(block);
        if (name == null) return;
        Set<EntityType> types = plugin.windicator.getCoreEntities(name);
        int count = 0;
        for (CreatureSpawner spawner : Blocks.findNearbySpawners(block, 12)) {
            EntityType entityType = spawner.getSpawnedType();
            if (types.contains(entityType)) count += 1;
        }
        if (count > 0) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(ChatColor.RED
                                          + "This core is protected by nearby spawners.");
            return;
        }
        plugin.windicator.removeCore(block, name);
        plugin.windicator.save();
        event.getPlayer().sendMessage(ChatColor.GREEN
                                      + "Core block broken.");
    }

    @EventHandler
    public void onSpawnerDpawn(SpawnerSpawnEvent event) {
        if (!plugin.windicator.isValid()) return;
        Block block = event.getSpawner().getBlock();
        if (!plugin.windicator.isInWorld(block)) return;
        EntityType entityType = event.getEntity().getType();
        String name = plugin.windicator.coreOf(entityType);
        if (name == null) return;
        if (plugin.windicator.countCoreBlocks(name) == 0) return;
        plugin.windicator.createNewSpawner(block, name);
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        if (!plugin.windicator.isValid()) return;
        LivingEntity entity = event.getEntity();
        if (!plugin.windicator.isInWorld(entity)) return;
        // Wither boss
        if (entity.equals(plugin.windicator.boss)) {
            plugin.windicator.setVictory(true);
            plugin.windicator.save();
            return;
        }
        event.getDrops().clear();
        if (entity.getKiller() == null) return;
        String name = plugin.windicator.coreOf(entity.getType());
        if (name == null) return;
        switch (name) {
        case Windicator.WATER:
            event.getDrops().add(new ItemStack(Material.DIAMOND));
            break;
        case Windicator.MANSION:
            event.getDrops().add(new ItemStack(Material.EMERALD));
            break;
        case Windicator.END:
            event.getDrops().add(new ItemStack(Material.GOLD_INGOT));
            break;
        default: break;
        }
    }
}
