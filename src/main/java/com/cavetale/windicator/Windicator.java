package com.cavetale.windicator;

import com.cavetale.core.exploits.PlayerPlacedBlocks;
import com.cavetale.core.struct.Vec3i;
import com.cavetale.core.util.Json;
import com.cavetale.fam.trophy.Highscore;
import com.cavetale.mytems.item.trophy.TrophyCategory;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Difficulty;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Wither;
import org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason;
import static com.cavetale.core.util.CamelCase.toCamelCase;
import static com.cavetale.mytems.util.Collision.collidesWithBlock;
import static net.kyori.adventure.text.Component.text;
import static net.kyori.adventure.text.format.NamedTextColor.*;
import static net.kyori.adventure.text.format.TextDecoration.*;

@Getter
@RequiredArgsConstructor
public final class Windicator {
    public static final String WORLD = "windicator";
    public static final String MIRROR_WORLD = "windicator_copy";
    public static final String STATE_PATH = "state.json";
    public static final int BOSS_COOLDOWN = 20 * 60;
    private final WindicatorPlugin plugin;
    private State state;
    private final Map<CoreType, CoreBoss> bossMap = new EnumMap<>(CoreType.class);
    private Wither boss = null;
    private List<Highscore> highscore = List.of();
    private List<Component> highscoreLines = List.of();

    protected void load() {
        state = Json.load(new File(plugin.getDataFolder(), STATE_PATH), State.class, State::new);
        for (Chunk chunk : getWorld().getLoadedChunks()) {
            getMirrorWorld().getChunkAtAsync(chunk.getX(), chunk.getZ(), (Consumer<Chunk>) mchunk -> {
                    mchunk.addPluginChunkTicket(plugin);
                });
        }
        computeHighscore();
    }

    protected void disable() {
        for (CoreType coreType : CoreType.values()) {
            for (Block block : getCoreBlocks(coreType)) {
                for (BlockDisplay bd : block.getLocation().getNearbyEntitiesByType(BlockDisplay.class, 0.5, 0.5, 0.5)) {
                    bd.remove();
                }
            }
        }
    }

    public void save() {
        plugin.getDataFolder().mkdirs();
        Json.save(new File(plugin.getDataFolder(), STATE_PATH), state, true);
    }

    public boolean isValid() {
        return state != null && state.isValid();
    }

    public boolean isPlaying() {
        return state != null && state.isEnabled() && !state.isVictory();
    }

    public void setEnabled(boolean enabled) {
        getWorld().setDifficulty(enabled ? Difficulty.HARD : Difficulty.PEACEFUL);
        state.setEnabled(enabled);
    }

    public boolean isCore(Block block, CoreType coreType) {
        for (Vec3i vec : getCores(coreType)) {
            if (vec.equals(Vec3i.of(block))) return true;
        }
        return false;
    }

    public CoreType coreAt(Block block) {
        for (CoreType coreType : CoreType.values()) {
            if (isCore(block, coreType)) return coreType;
        }
        return null;
    }

    public boolean addCore(Block block, CoreType coreType) {
        List<Vec3i> list = state.getCores().get(coreType);
        if (list == null) {
            list = new ArrayList<>();
            state.getCores().put(coreType, list);
        }
        Vec3i vec = Vec3i.of(block);
        if (list.contains(vec)) return false;
        list.add(vec);
        return true;
    }

    public boolean removeCore(Block block, CoreType coreType, boolean removeIfEmpty) {
        final List<Vec3i> list = state.getCores().get(coreType);
        if (list == null) return false;
        final boolean res = list.remove(Vec3i.of(block));
        if (removeIfEmpty && list.isEmpty()) {
            state.getCores().remove(coreType);
        }
        for (BlockDisplay bd : block.getLocation().getNearbyEntitiesByType(BlockDisplay.class, 0.5, 0.5, 0.5)) {
            bd.remove();
        }
        return res;
    }

    public List<Vec3i> getCores(CoreType coreType) {
        List<Vec3i> list = state.getCores().get(coreType);
        return list == null
            ? List.of()
            : list;
    }

    public List<Block> getCoreBlocks(CoreType coreType) {
        List<Vec3i> list = getCores(coreType);
        if (list == null) return List.of();
        World world = getWorld();
        if (world == null) return List.of();
        return list.stream()
            .map(v -> world.getBlockAt(v.getX(), v.getY(), v.getZ()))
            .collect(Collectors.toList());
    }

    public int countCoreBlocks(CoreType coreType) {
        List<Vec3i> list = getCores(coreType);
        if (list == null) return 0;
        return list.size();
    }

    public void clearCores() {
        state.getCores().clear();
    }

    public boolean isInWorld(@NonNull Block block) {
        return block.getWorld().getName().equals(WORLD);
    }

    public boolean isInWorld(@NonNull Entity entity) {
        return entity.getWorld().getName().equals(WORLD);
    }

    public boolean isWorld(@NonNull World world) {
        return world.getName().equals(WORLD);
    }

    public World getWorld() {
        return Bukkit.getWorld(WORLD);
    }

    public World getMirrorWorld() {
        return Bukkit.getWorld(MIRROR_WORLD);
    }

    public CoreType coreOf(EntityType entityType) {
        for (CoreType coreType : CoreType.values()) {
            if (coreType.getCoreEntities().contains(entityType)) return coreType;
        }
        return null;
    }

    public boolean createNewSpawner(Block origin, CoreType coreType) {
        final int dist = 12;
        final Set<EntityType> set = coreType.getCoreEntities();
        if (set.isEmpty()) return false;
        Block block = origin.getRelative(plugin.rnd(dist),
                                         plugin.rnd(dist),
                                         plugin.rnd(dist));
        final int min = block.getWorld().getMinHeight();
        final int max = block.getWorld().getMaxHeight();
        while (!block.isEmpty() && !block.isLiquid() && block.getY() < max) {
            block = block.getRelative(0, 1, 0);
        }
        if (block.getY() < min) return false; // bedrock on old map
        if (!block.isEmpty() && !block.isLiquid()) return false;
        int nbor = 0;
        final int radius = 2;
        for (int dy = -radius; dy <= radius; dy += 1) {
            for (int dz = -radius; dz <= radius; dz += 1) {
                for (int dx = -radius; dx <= radius; dx += 1) {
                    Block rel = block.getRelative(dx, dy, dz);
                    switch (rel.getType()) {
                    case LIGHT: case AIR: case SPAWNER: continue;
                    default: break;
                    }
                    if (!rel.isEmpty()) nbor += 1;
                }
            }
        }
        if (nbor == 0) return false;
        for (CreatureSpawner spawner : Blocks.findNearbySpawners(block, 10)) {
            EntityType spawnerType = spawner.getSpawnedType();
            if (set.contains(spawnerType)) return false;
        }
        System.out.println("D");
        final List<EntityType> opts = List.copyOf(set);
        final EntityType entityType = opts.get(plugin.getRandom().nextInt(opts.size()));
        block.setType(Material.SPAWNER);
        final CreatureSpawner spawner = (CreatureSpawner) block.getState();
        spawner.setSpawnedType(entityType);
        spawner.update(true, false);
        plugin.getLogger().info(coreType + ": created " + entityType
                                + " spawner at " + Blocks.toString(block)
                                + " (" + nbor + ")");
        return true;
    }

    public boolean isVictory() {
        return state.isVictory();
    }

    public void setVictory(boolean value) {
        state.setVictory(value);
    }

    public Mob tryToSpawnBoss(CoreType coreType) {
        List<Vec3i> cores = getCores(coreType);
        if (cores == null || cores.isEmpty()) return null;
        final Vec3i v = cores.get(plugin.getRandom().nextInt(cores.size()));
        final World world = getWorld();
        if (world == null) return null;
        if (!world.isChunkLoaded(v.getX() >> 4, v.getZ() >> 4)) return null;
        final Block origin = world.getBlockAt(v.getX(), v.getY(), v.getZ());
        final Block block = origin.getRelative(plugin.rnd(8), plugin.rnd(8), plugin.rnd(8));
        switch (coreType) {
        case WATER:
            if (!block.isLiquid()) return null;
            break;
        default:
            if (!block.isEmpty()) return null;
            if (!block.getRelative(0, 1, 0).isEmpty()) return null;
            if (!block.getRelative(0, -1, 0).isSolid()) return null;
            break;
        }
        @SuppressWarnings("unchecked")
        final Mob result = (Mob) world.spawn(block.getLocation().add(0.5, 0.0, 0.5), coreType.getBossType().getEntityClass(), who -> {
                if (!(who instanceof Mob e)) return;
                final double health = 100.0;
                if (e.getHealth() < health) {
                    e.getAttribute(Attribute.MAX_HEALTH).setBaseValue(health);
                    e.setHealth(health);
                }
                e.getAttribute(Attribute.ATTACK_DAMAGE).setBaseValue(10.0);
                e.getAttribute(Attribute.KNOCKBACK_RESISTANCE).setBaseValue(1.0);
                e.getAttribute(Attribute.SCALE).setBaseValue(2.0);
                e.setPersistent(false);
                e.setRemoveWhenFarAway(false);
                e.customName(text(toCamelCase(" ", coreType), GOLD, BOLD));
                e.setCustomNameVisible(true);
                if (collidesWithBlock(world, e.getBoundingBox())) {
                    e.remove();
                }
            });
        if (result == null || result.isDead()) {
            plugin.getLogger().severe("Failed to spawn boss: " + coreType);
            return null;
        }
        plugin.getLogger().info(coreType + ": spawned " + result.getType()
                                + " at " + Blocks.toString(block));
        return result;
    }

    public void respawnBosses() {
        int totalCores = 0;
        for (CoreType coreType : CoreType.values()) {
            final int count = countCoreBlocks(coreType);
            if (count == 0) {
                continue;
            }
            totalCores += count;
            CoreBoss coreBoss = bossMap.get(coreType);
            if (coreBoss == null) {
                coreBoss = new CoreBoss();
                bossMap.put(coreType, coreBoss);
            }
            if (coreBoss.getMob() != null && !coreBoss.getMob().isDead()) {
                continue;
            }
            coreBoss.setMob(null);
            final int cooldown = coreBoss.getCooldown();
            if (cooldown > 0) {
                coreBoss.setCooldown(cooldown - 1);
            } else {
                coreBoss.setMob(tryToSpawnBoss(coreType));
            }
        }
        // Spawn the Wither Boss
        if (totalCores == 0 && (boss == null || !boss.isValid())) {
            boss = null;
            World world = getWorld();
            Location loc = world.getSpawnLocation();
            if (loc.getWorld().isChunkLoaded(loc.getBlockX() >> 4,
                                             loc.getBlockZ() >> 4)) {
                boss = world.spawn(loc, Wither.class, e -> {
                        e.setPersistent(false);
                        e.setRemoveWhenFarAway(true);
                        final double health = 1024.0;
                        e.getAttribute(Attribute.MAX_HEALTH).setBaseValue(health);
                        e.setHealth(health);
                        e.getAttribute(Attribute.ARMOR).setBaseValue(10);
                        e.getAttribute(Attribute.ARMOR_TOUGHNESS).setBaseValue(10);
                    });
            }
        }
        return;
    }

    public void regen() {
        World world = getWorld();
        World mirror = getMirrorWorld();
        if (world == null || mirror == null) return;
        int inx = plugin.getRandom().nextInt(16);
        int inz = plugin.getRandom().nextInt(16);
        List<Chunk> chunks = new ArrayList<>();
        for (Chunk chunk : world.getLoadedChunks()) chunks.add(chunk);
        Collections.shuffle(chunks, plugin.getRandom());
        for (Chunk chunk : chunks) {
            if (chunk.getLoadLevel() != Chunk.LoadLevel.ENTITY_TICKING) continue;
            final int x = (chunk.getX() << 4) + inx;
            final int z = (chunk.getZ() << 4) + inz;
            final int lo = world.getMinHeight();
            final int hi = mirror.getHighestBlockYAt(x, z);
            if (hi <= lo) continue;
            final Location center = new Location(world, (double) x + 0.5, 0.5 * ((double) lo + (double) hi), (double) z + 0.5);
            if (!center.getNearbyEntitiesByType(Player.class, 0.5, 0.5 * (double) (hi - lo + 1), 0.5).isEmpty()) {
                continue;
            }
            for (int y = lo; y <= hi; y += 1) {
                Block block = world.getBlockAt(x, y, z);
                if (PlayerPlacedBlocks.isPlayerPlaced(block)) continue;
                if (block.getType().isSolid()) continue;
                Block mblock = mirror.getBlockAt(block.getX(),
                                                 block.getY(),
                                                 block.getZ());
                if (!mblock.getType().isSolid() || mblock.isEmpty() || mblock.isLiquid()) {
                    continue;
                }
                BlockData data = mblock.getBlockData();
                if (data.equals(block.getBlockData())) continue;
                block.setBlockData(data, false);
                return;
            }
        }
    }

    /**
     * Try to spawn mobs from any spawner in the world.
     * Called regularly by Tick.
     */
    public void spawnAll() {
        World world = getWorld();
        World mirror = getMirrorWorld();
        if (world == null || mirror == null) return;
        int inx = plugin.getRandom().nextInt(16);
        int inz = plugin.getRandom().nextInt(16);
        List<Chunk> chunks = new ArrayList<>();
        for (Chunk chunk : world.getLoadedChunks()) chunks.add(chunk);
        Collections.shuffle(chunks, plugin.getRandom());
        for (Chunk chunk : chunks) {
            if (chunk.getLoadLevel() != Chunk.LoadLevel.ENTITY_TICKING) continue;
            for (BlockState blockState : chunk.getTileEntities()) {
                if (!(blockState instanceof CreatureSpawner spawner)) continue;
                EntityType entityType = spawner.getSpawnedType();
                if (entityType == null) continue;
                int nearbyCount = 0;
                for (Entity nearby : blockState.getBlock().getLocation().getNearbyEntitiesByType(entityType.getEntityClass(), 12.0, 32.0, 12.0)) {
                    if (nearby.getType() == entityType) nearbyCount += 1;
                }
                if (nearbyCount > 3) continue;
                for (int i = 0; i < 10; i += 1) {
                    Block spawnBlock = blockState.getBlock().getRelative(plugin.rnd(8), plugin.rnd(8), plugin.rnd(8));
                    Location spawnLocation = spawnBlock.getLocation().add(0.5, 0.0, 0.5);
                    Entity entity = spawnLocation.getWorld().spawnEntity(spawnLocation, entityType, SpawnReason.SPAWNER, e -> {
                            e.setPersistent(false);
                            if (e instanceof Mob mob) {
                                mob.setRemoveWhenFarAway(true);
                            }
                            if (collidesWithBlock(spawnLocation.getWorld(), e.getBoundingBox())) {
                                e.remove();
                            }
                        });
                    if (entity != null && !entity.isDead()) return;
                }
            }
        }
    }

    public void clearMobs() {
        if (boss != null) boss.remove();
        for (CoreBoss coreBoss : bossMap.values()) {
            if (coreBoss.getMob() != null) {
                coreBoss.getMob().remove();
            }
        }
        bossMap.clear();
    }

    public void addScore(Player player, int value) {
        state.addScore(player.getUniqueId(), value);
        computeHighscore();
    }

    public void computeHighscore() {
        highscore = Highscore.of(state.getScores());
        highscoreLines = Highscore.sidebar(highscore, TrophyCategory.SWORD);
    }
}
