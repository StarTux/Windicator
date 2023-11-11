package com.cavetale.windicator;

import com.cavetale.sidebar.PlayerSidebarEvent;
import com.cavetale.sidebar.Priority;
import com.destroystokyo.paper.MaterialTags;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import lombok.RequiredArgsConstructor;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
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
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.SpawnerSpawnEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

@RequiredArgsConstructor
public final class EventListener implements Listener {
    final WindicatorPlugin plugin;

    private int countProtectSpawners(Block origin, String name) {
        int count = 0;
        Set<EntityType> types = plugin.windicator.getCoreEntities(name);
        for (CreatureSpawner spawner : Blocks.findNearbySpawners(origin, 10, 4, 10)) {
            EntityType entityType = spawner.getSpawnedType();
            if (types.contains(entityType)) {
                spawner.getWorld().playSound(spawner.getBlock().getLocation(),
                                             Sound.BLOCK_GLASS_BREAK, SoundCategory.MASTER, 1.0f, 2.0f);
                count += 1;
            }
        }
        return count;
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        if (!plugin.windicator.isValid()) return;
        Block block = event.getBlock();
        if (!plugin.windicator.isInWorld(block)) return;
        String name = plugin.windicator.coreAt(block);
        if (name != null) {
            if (countProtectSpawners(block, name) > 0) {
                event.setCancelled(true);
                event.getPlayer().sendMessage(Component.text("This core is protected by nearby spawners",
                                                             NamedTextColor.RED));
                event.getPlayer().sendActionBar(Component.text("This core is protected by nearby spawners",
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
        if (block.getType().name().endsWith("_ORE")) {
            event.setDropItems(false);
        }
    }

    @EventHandler
    void onBlockDamage(BlockDamageEvent event) {
        String name = plugin.windicator.coreAt(event.getBlock());
        if (name == null) return;
        event.getPlayer().addPotionEffect(new PotionEffect(PotionEffectType.SLOW_DIGGING, 20 * 30, 0, true, true, true));
        if (countProtectSpawners(event.getBlock(), name) > 0) {
            event.getPlayer().sendMessage(Component.text("This core is protected by nearby spawners!",
                                                         NamedTextColor.RED));
            event.getPlayer().sendActionBar(Component.text("This core is protected by nearby spawners!",
                                                           NamedTextColor.RED));
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
    public void onChunkLoad(ChunkLoadEvent event) {
        final Chunk chunk = event.getChunk();
        if (chunk.getWorld().equals(plugin.windicator.getWorld())) {
            plugin.windicator.getMirrorWorld().getChunkAtAsync(chunk.getX(), chunk.getZ(), (Consumer<Chunk>) mchunk -> {
                    mchunk.addPluginChunkTicket(plugin);
                });
        }
    }

    @EventHandler
    public void onChunkUnload(ChunkUnloadEvent event) {
        final Chunk chunk = event.getChunk();
        if (chunk.getWorld().equals(plugin.windicator.getWorld())) {
            plugin.windicator.getMirrorWorld().getChunkAtAsync(chunk.getX(), chunk.getZ(), (Consumer<Chunk>) mchunk -> {
                    mchunk.removePluginChunkTicket(plugin);
                });
        }
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
                      .append(Component.text(" " + count, NamedTextColor.GOLD)));
        }
        event.add(plugin, Priority.HIGHEST, lines);
    }

    @EventHandler
    protected void onPlayerJoin(PlayerJoinEvent event) {
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                               "ml add " + event.getPlayer().getName());
    }

    @EventHandler
    protected void onPrepareItemCraft(PrepareItemCraftEvent event) {
        ItemStack result = event.getRecipe().getResult();
        if (result == null) return;
        Material mat = result.getType();
        boolean doNull = MaterialTags.HELMETS.isTagged(mat)
            || MaterialTags.CHESTPLATES.isTagged(mat)
            || MaterialTags.LEGGINGS.isTagged(mat)
            || MaterialTags.BOOTS.isTagged(mat)
            || MaterialTags.SWORDS.isTagged(mat)
            || MaterialTags.BOWS.isTagged(mat)
            || MaterialTags.PICKAXES.isTagged(mat)
            || MaterialTags.SHOVELS.isTagged(mat)
            || MaterialTags.AXES.isTagged(mat)
            || mat.name().endsWith("_INGOT");
        if (doNull) {
            event.getInventory().setResult(null);
        }
    }
}
