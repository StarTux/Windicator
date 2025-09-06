package com.cavetale.windicator;

import com.cavetale.core.event.hud.PlayerHudEvent;
import com.cavetale.core.event.hud.PlayerHudPriority;
import com.cavetale.core.struct.Vec3i;
import com.cavetale.mytems.Mytems;
import com.destroystokyo.paper.MaterialTags;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import lombok.RequiredArgsConstructor;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.entity.Animals;
import org.bukkit.entity.BlockDisplay;
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
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;
import static net.kyori.adventure.text.Component.join;
import static net.kyori.adventure.text.Component.space;
import static net.kyori.adventure.text.Component.text;
import static net.kyori.adventure.text.Component.textOfChildren;
import static net.kyori.adventure.text.JoinConfiguration.separator;
import static net.kyori.adventure.text.format.NamedTextColor.*;
import static net.kyori.adventure.text.format.TextDecoration.*;

@RequiredArgsConstructor
public final class EventListener implements Listener {
    private final WindicatorPlugin plugin;

    private List<Block> findProtectSpawners(Block origin, CoreType coreType) {
        final List<Block> result = new ArrayList<>();
        Set<EntityType> types = coreType.getCoreEntities();
        for (CreatureSpawner spawner : Blocks.findNearbySpawners(origin, 12, 10, 12)) {
            EntityType entityType = spawner.getSpawnedType();
            if (types.contains(entityType)) {
                result.add(spawner.getBlock());
            }
        }
        return result;
    }

    private int countProtectSpawners(Block origin, CoreType coreType) {
        return findProtectSpawners(origin, coreType).size();
    }

    private void highlightNearbySpawners(List<Block> blocks) {
        for (var block : blocks) {
            final var world = block.getWorld();
            world.playSound(block.getLocation(), Sound.BLOCK_GLASS_BREAK, SoundCategory.MASTER, 1.0f, 2.0f);
            final var entity = world.spawn(block.getLocation().add(0.5, 0.5, 0.5), BlockDisplay.class, e -> {
                    e.setPersistent(false);
                    e.setGlowing(true);
                    e.setBlock(Material.SPAWNER.createBlockData());
                    e.setGlowColorOverride(Color.RED);
                    e.setBrightness(new BlockDisplay.Brightness(15, 15));
                    e.setTransformation(new Transformation(new Vector3f(-0.5125f, -0.5125f, -0.5125f),
                                                           new AxisAngle4f(0f, 0f, 0f, 0f),
                                                           new Vector3f(1.05f, 1.05f, 1.05f),
                                                           new AxisAngle4f(0f, 0f, 0f, 0f)));
                });
            Bukkit.getScheduler().runTaskLater(plugin, entity::remove, 60L);
        }
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        if (!plugin.getWindicator().isPlaying()) return;
        Block block = event.getBlock();
        if (!plugin.getWindicator().isInWorld(block)) return;
        CoreType coreType = plugin.getWindicator().coreAt(block);
        final Player player = event.getPlayer();
        if (coreType != null) {
            final var nearbySpawners = findProtectSpawners(event.getBlock(), coreType);
            if (!nearbySpawners.isEmpty()) {
                event.setCancelled(true);
                player.sendMessage(text("This core is protected by nearby spawners", RED));
                player.sendActionBar(text("This core is protected by nearby spawners", RED));
                highlightNearbySpawners(nearbySpawners);
                return;
            }
            plugin.getWindicator().removeCore(block, coreType);
            plugin.getWindicator().save();
            for (Player other : Bukkit.getOnlinePlayers()) {
                other.sendMessage(text(player.getName() + " broke the " + coreType.getDisplayName() + " core", GOLD));
            }
            plugin.getLogger().info(player.getName() + " broke the " + coreType + " core");
            plugin.getWindicator().addScore(player, 25);
        }
        if (block.getType() == Material.SPAWNER) {
            block.getWorld().dropItem(block.getLocation().add(0.5, 0.5, 0.5),
                                      new ItemStack(Material.EMERALD,
                                                    2 + 2 * plugin.getRandom().nextInt(5)));
            plugin.getWindicator().addScore(player, 10);
        }
        if (block.getType().name().endsWith("_ORE")) {
            event.setDropItems(false);
        }
    }

    @EventHandler
    private void onBlockDamage(BlockDamageEvent event) {
        CoreType coreType = plugin.getWindicator().coreAt(event.getBlock());
        if (coreType == null) return;
        event.getPlayer().addPotionEffect(new PotionEffect(PotionEffectType.MINING_FATIGUE, 20 * 30, 0, true, true, true));
        final var nearbySpawners = findProtectSpawners(event.getBlock(), coreType);
        if (!nearbySpawners.isEmpty()) {
            event.getPlayer().sendMessage(text("This core is protected by nearby spawners!", RED));
            event.getPlayer().sendActionBar(text("This core is protected by nearby spawners!", RED));
            highlightNearbySpawners(nearbySpawners);
        }
    }

    @EventHandler
    public void onSpawnerSpawn(SpawnerSpawnEvent event) {
        if (!plugin.getWindicator().isPlaying()) return;
        Block block = event.getSpawner().getBlock();
        if (!plugin.getWindicator().isInWorld(block)) return;
        EntityType entityType = event.getEntity().getType();
        CoreType coreType = plugin.getWindicator().coreOf(entityType);
        if (coreType == null) return;
        if (plugin.getWindicator().countCoreBlocks(coreType) == 0) return;
        if (plugin.getRandom().nextInt(5) == 0) {
            plugin.getWindicator().createNewSpawner(block, coreType);
        }
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        if (!plugin.getWindicator().isPlaying()) return;
        LivingEntity entity = event.getEntity();
        if (!plugin.getWindicator().isInWorld(entity)) return;
        // Wither boss
        if (entity.equals(plugin.getWindicator().getBoss())) {
            plugin.getWindicator().setVictory(true);
            plugin.getWindicator().save();
            return;
        }
        for (CoreBoss coreBoss : plugin.getWindicator().getBossMap().values()) {
            if (entity.equals(coreBoss.getMob())) {
                coreBoss.setCooldown(Windicator.BOSS_COOLDOWN);
            }
        }
        if (entity instanceof Mob && !(entity instanceof Animals)) {
            event.getDrops().clear();
            if (entity.getKiller() == null) return;
            CoreType coreType = plugin.getWindicator().coreOf(entity.getType());
            if (coreType == null) return;
            event.getDrops().add(new ItemStack(Material.EMERALD, 1 + plugin.getRandom().nextInt(5)));
            plugin.getWindicator().addScore(entity.getKiller(), 1);
        }
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        final Chunk chunk = event.getChunk();
        if (chunk.getWorld().equals(plugin.getWindicator().getWorld())) {
            plugin.getWindicator().getMirrorWorld().getChunkAtAsync(chunk.getX(), chunk.getZ(), (Consumer<Chunk>) mchunk -> {
                    mchunk.addPluginChunkTicket(plugin);
                });
        }
    }

    @EventHandler
    public void onChunkUnload(ChunkUnloadEvent event) {
        final Chunk chunk = event.getChunk();
        if (chunk.getWorld().equals(plugin.getWindicator().getWorld())) {
            plugin.getWindicator().getMirrorWorld().getChunkAtAsync(chunk.getX(), chunk.getZ(), (Consumer<Chunk>) mchunk -> {
                    mchunk.removePluginChunkTicket(plugin);
                });
        }
        for (Entity entity : event.getChunk().getEntities()) {
            if (entity.equals(plugin.getWindicator().getBoss())) {
                entity.remove();
            }
            for (CoreBoss coreBoss : plugin.getWindicator().getBossMap().values()) {
                if (entity.equals(coreBoss.getMob())) {
                    entity.remove();
                    coreBoss.setMob(null);
                }
            }
        }
    }

    @EventHandler
    public void onPlayerItemDamage(PlayerItemDamageEvent event) {
        Player player = event.getPlayer();
        if (!plugin.getWindicator().isInWorld(player)) return;
        event.setCancelled(true);
    }

    @EventHandler
    private void onPlayerHud(PlayerHudEvent event) {
        if (!plugin.getWindicator().isValid()) return;
        List<Component> lines = new ArrayList<>();
        lines.add(text("Windicator", GOLD, BOLD));
        for (CoreType core : CoreType.values()) {
            List<Vec3i> list = plugin.getWindicator().getCores(core);
            if (list == null) continue;
            if (list.isEmpty()) {
                lines.add(text(core.getDisplayName(), DARK_GRAY, STRIKETHROUGH));
            } else {
                lines.add(textOfChildren(text(core.getDisplayName(), GRAY),
                                         text(" " + list.size(), GOLD)));
            }
        }
        lines.add(textOfChildren(text("Score ", DARK_GRAY), text(plugin.getWindicator().getState().getScore(event.getPlayer().getUniqueId()), YELLOW)));
        event.bossbar(PlayerHudPriority.HIGH, join(separator(space()), lines), BossBar.Color.RED, BossBar.Overlay.PROGRESS, 1.0f);
        if (plugin.getWindicator().isVictory()) {
            lines.addAll(plugin.getWindicator().getHighscoreLines());
        }
        event.sidebar(PlayerHudPriority.HIGH, lines);
    }

    @EventHandler
    private void onPlayerJoin(PlayerJoinEvent event) {
        final Player player = event.getPlayer();
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                               "ml add " + player.getName());
        if (!player.hasPlayedBefore()) {
            player.getInventory().addItem(new ItemStack(Material.WOODEN_SWORD));
            player.getInventory().addItem(Mytems.ORANGE_CANDY.createItemStack());
            player.getInventory().addItem(Mytems.CANDY_CORN.createItemStack());
            player.getInventory().addItem(Mytems.CHOCOLATE_BAR.createItemStack());
        }
    }

    @EventHandler
    private void onPrepareItemCraft(PrepareItemCraftEvent event) {
        if (event.getRecipe() == null) return;
        ItemStack result = event.getRecipe().getResult();
        if (result == null) return;
        Material mat = result.getType();
        boolean doNull = Tag.ITEMS_HEAD_ARMOR.isTagged(mat)
            || Tag.ITEMS_CHEST_ARMOR.isTagged(mat)
            || Tag.ITEMS_LEG_ARMOR.isTagged(mat)
            || Tag.ITEMS_FOOT_ARMOR.isTagged(mat)
            || Tag.ITEMS_SWORDS.isTagged(mat)
            || MaterialTags.BOWS.isTagged(mat)
            || Tag.ITEMS_PICKAXES.isTagged(mat)
            || Tag.ITEMS_SHOVELS.isTagged(mat)
            || Tag.ITEMS_AXES.isTagged(mat)
            || mat.name().endsWith("_INGOT")
            || mat == Material.BREAD;
        if (doNull) {
            event.getInventory().setResult(null);
        }
    }
}
