package com.cavetale.windicator;

import com.cavetale.sidebar.PlayerSidebarEvent;
import com.cavetale.sidebar.Priority;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.entity.Animals;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.SpawnerSpawnEvent;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
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
        if (name != null) {
            Set<EntityType> types = plugin.windicator.getCoreEntities(name);
            int count = 0;
            for (CreatureSpawner spawner : Blocks.findNearbySpawners(block, 8)) {
                EntityType entityType = spawner.getSpawnedType();
                if (types.contains(entityType)) count += 1;
            }
            if (count > 0) {
                event.setCancelled(true);
                event.getPlayer().sendMessage(Component.text("This core is protected by nearby spawners",
                                                             NamedTextColor.RED));
                return;
            }
            plugin.windicator.removeCore(block, name);
            plugin.windicator.save();
            event.getPlayer().sendMessage(Component.text("Core block broken", NamedTextColor.GREEN));
        }
        if (block.getType() == Material.SPAWNER) {
            block.getWorld().dropItem(block.getLocation().add(0.5, 0.5, 0.5),
                                      new ItemStack(Material.EMERALD,
                                                    2 + 2 * plugin.random.nextInt(5)));
        }
        if (block.getType() == Material.IRON_ORE
            || block.getType() == Material.DIAMOND_ORE
            || block.getType() == Material.GOLD_ORE
            || block.getType() == Material.REDSTONE_ORE) {
            event.setCancelled(true);
            block.setType(Material.AIR);
        }
    }

    @EventHandler
    public void onSpawnerSpawn(SpawnerSpawnEvent event) {
        if (!plugin.windicator.isValid()) return;
        Block block = event.getSpawner().getBlock();
        if (!plugin.windicator.isInWorld(block)) return;
        EntityType entityType = event.getEntity().getType();
        String name = plugin.windicator.coreOf(entityType);
        if (name == null) return;
        if (plugin.windicator.countCoreBlocks(name) == 0) return;
        if (plugin.random.nextInt(3) == 0) {
            plugin.windicator.createNewSpawner(block, name);
        }
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
        if (entity instanceof Mob && !(entity instanceof Animals)) {
            event.getDrops().clear();
            if (entity.getKiller() == null) return;
            String name = plugin.windicator.coreOf(entity.getType());
            if (name == null) return;
            switch (name) {
            case Windicator.WATER:
            case Windicator.MANSION:
            case Windicator.END:
                event.getDrops().add(new ItemStack(Material.EMERALD,
                                                   1 + plugin.random.nextInt(5)));
                break;
            default: break;
            }
        }
    }

    @EventHandler
    public void onChunkUnload(ChunkUnloadEvent event) {
        for (Entity e : event.getChunk().getEntities()) {
            if (e.equals(plugin.windicator.boss)
                || e.equals(plugin.windicator.waterBoss)
                || e.equals(plugin.windicator.mansionBoss)
                || e.equals(plugin.windicator.endBoss)) {
                e.remove();
            }
        }
    }

    @EventHandler
    public void onPlayerItemDamage(PlayerItemDamageEvent event) {
        if (!plugin.windicator.isValid()) return;
        Player player = event.getPlayer();
        if (!plugin.windicator.isInWorld(player)) return;
        event.setCancelled(true);
    }

    @EventHandler
    protected void onPlayerSidebar(PlayerSidebarEvent event) {
        if (!plugin.windicator.isValid()) return;
        List<Component> lines = new ArrayList<>();
        lines.add(Component.text("Cores", NamedTextColor.RED));
        for (String core : Windicator.listCores()) {
            List<Vec3> list = plugin.windicator.getState().cores.get(core);
            int count = list != null ? list.size() : 0;
            lines.add(Component.text(core, NamedTextColor.GRAY)
                      .append(Component.text(" " + count, NamedTextColor.YELLOW)));
        }
        event.add(plugin, Priority.HIGHEST, lines);
    }
}
