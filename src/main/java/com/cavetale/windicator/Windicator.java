package com.cavetale.windicator;

import com.cavetale.core.exploits.PlayerPlacedBlocks;
import com.cavetale.fam.trophy.Highscore;
import com.cavetale.mytems.item.trophy.TrophyCategory;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.ElderGuardian;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Illusioner;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Wither;
import org.bukkit.entity.WitherSkeleton;
import org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason;
import static com.cavetale.core.util.CamelCase.toCamelCase;
import static com.cavetale.mytems.util.Collision.collidesWithBlock;
import static net.kyori.adventure.text.Component.text;
import static net.kyori.adventure.text.format.NamedTextColor.*;
import static net.kyori.adventure.text.format.TextDecoration.*;

@RequiredArgsConstructor
public final class Windicator {
    public static final String WORLD = "windicator";
    public static final String MIRROR_WORLD = "windicator_copy";
    final WindicatorPlugin plugin;
    @Getter private State state;
    ElderGuardian waterBoss;
    Illusioner mansionBoss;
    WitherSkeleton endBoss;
    protected int waterBossCooldown;
    protected int mansionBossCooldown;
    protected int endBossCooldown;
    Wither boss = null;
    static final String STATE_PATH = "state.json";
    static final int BOSS_COOLDOWN = 20 * 60;
    protected List<Highscore> highscore = List.of();
    protected List<Component> highscoreLines = List.of();

    protected void load() {
        state = plugin.json.load(STATE_PATH, State.class, State::new);
        for (Chunk chunk : getWorld().getLoadedChunks()) {
            getMirrorWorld().getChunkAtAsync(chunk.getX(), chunk.getZ(), (Consumer<Chunk>) mchunk -> {
                    mchunk.addPluginChunkTicket(plugin);
                });
        }
        computeHighscore();
    }

    protected void disable() {
        for (CoreType coreType : CoreType.values()) {
            for (Block block : plugin.windicator.getCoreBlocks(coreType)) {
                for (BlockDisplay bd : block.getLocation().getNearbyEntitiesByType(BlockDisplay.class, 0.5, 0.5, 0.5)) {
                    bd.remove();
                }
            }
        }
    }

    void save() {
        plugin.json.save(STATE_PATH, state, true);
    }

    boolean isValid() {
        return state != null && state.isValid();
    }

    public boolean isPlaying() {
        return state != null && state.enabled && !state.victory;
    }

    void setEnabled(boolean enabled) {
        state.enabled = enabled;
    }

    boolean isCore(Block block, CoreType coreType) {
        List<Vec3> list = getCores(coreType);
        if (list == null) return false;
        for (Vec3 vec : list) {
            if (vec.isSimilar(block)) return true;
        }
        return false;
    }

    CoreType coreAt(Block block) {
        for (CoreType coreType : CoreType.values()) {
            if (isCore(block, coreType)) return coreType;
        }
        return null;
    }

    boolean addCore(Block block, CoreType coreType) {
        List<Vec3> list = state.cores.get(coreType.name().toLowerCase());
        if (list == null) {
            list = new ArrayList<>();
            state.cores.put(coreType.name().toLowerCase(), list);
        }
        Vec3 vec = Vec3.of(block);
        if (list.contains(vec)) return false;
        list.add(vec);
        return true;
    }

    boolean removeCore(Block block, CoreType coreType) {
        List<Vec3> list = getCores(coreType);
        if (list == null) return false;
        boolean res = list.remove(Vec3.of(block));
        if (list.isEmpty()) {
            state.cores.remove(coreType.name().toLowerCase());
        }
        for (BlockDisplay bd : block.getLocation().getNearbyEntitiesByType(BlockDisplay.class, 0.5, 0.5, 0.5)) {
            bd.remove();
        }
        return res;
    }

    List<Vec3> getCores(CoreType coreType) {
        List<Vec3> list = state.cores.get(coreType.name().toLowerCase());
        return list == null
            ? List.of()
            : list;
    }

    List<Block> getCoreBlocks(CoreType coreType) {
        List<Vec3> list = getCores(coreType);
        if (list == null) return List.of();
        World world = getWorld();
        if (world == null) return List.of();
        return list.stream()
            .map(v -> world.getBlockAt(v.getX(), v.getY(), v.getZ()))
            .collect(Collectors.toList());
    }

    int countCoreBlocks(CoreType coreType) {
        List<Vec3> list = getCores(coreType);
        if (list == null) return 0;
        return list.size();
    }

    void clearCores() {
        state.cores.clear();
    }

    boolean isInWorld(@NonNull Block block) {
        return block.getWorld().getName().equals(WORLD);
    }

    boolean isInWorld(@NonNull Entity entity) {
        return entity.getWorld().getName().equals(WORLD);
    }

    boolean isWorld(@NonNull World world) {
        return world.getName().equals(WORLD);
    }

    World getWorld() {
        return Bukkit.getWorld(WORLD);
    }

    World getMirrorWorld() {
        return Bukkit.getWorld(MIRROR_WORLD);
    }

    Set<EntityType> getCoreEntities(CoreType coreType) {
        switch (coreType) {
        case WATER:
            return EnumSet.of(EntityType.GUARDIAN,
                              EntityType.DROWNED,
                              EntityType.PUFFERFISH);
        case MANSION:
            return EnumSet.of(EntityType.PILLAGER,
                              EntityType.RAVAGER,
                              EntityType.ZOMBIE,
                              EntityType.SKELETON,
                              EntityType.VINDICATOR,
                              EntityType.WITCH,
                              EntityType.CREEPER,
                              EntityType.CAVE_SPIDER,
                              EntityType.SPIDER,
                              EntityType.WITHER_SKELETON);
        case END:
            return EnumSet.of(EntityType.SHULKER,
                              EntityType.ENDERMAN,
                              EntityType.GHAST,
                              EntityType.PHANTOM);
        default: return Collections.emptySet();
        }
    }

    public CoreType coreOf(EntityType entityType) {
        for (CoreType coreType : CoreType.values()) {
            if (getCoreEntities(coreType).contains(entityType)) return coreType;
        }
        return null;
    }

    boolean createNewSpawner(Block origin, CoreType coreType) {
        final int dist = 12;
        Set<EntityType> set = getCoreEntities(coreType);
        if (set.isEmpty()) return false;
        Block block = origin.getRelative(plugin.rnd(dist),
                                         plugin.rnd(dist),
                                         plugin.rnd(dist));
        if (block.getY() <= 0) return false; // bedrock on old map
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
        List<EntityType> opts = new ArrayList<>(set);
        EntityType entityType = opts.get(plugin.random.nextInt(opts.size()));
        block.setType(Material.SPAWNER);
        CreatureSpawner spawner = (CreatureSpawner) block.getState();
        spawner.setSpawnedType(entityType);
        spawner.update(true, false);
        plugin.getLogger().info(coreType + ": created " + entityType
                                + " spawner at " + Blocks.toString(block)
                                + " (" + nbor + ")");
        return true;
    }

    boolean isVictory() {
        return state.victory;
    }

    void setVictory(boolean victory) {
        state.victory = victory;
    }

    <T extends Mob> T spawnBoss(CoreType coreType, Class<T> clazz) {
        List<Vec3> cores = getCores(coreType);
        if (cores == null || cores.isEmpty()) return null;
        Vec3 v = cores.get(plugin.random.nextInt(cores.size()));
        World world = getWorld();
        if (world == null) return null;
        if (!world.isChunkLoaded(v.getX() >> 4, v.getZ() >> 4)) return null;
        Block origin = world.getBlockAt(v.getX(), v.getY(), v.getZ());
        Block block = origin.getRelative(plugin.rnd(8), plugin.rnd(8), plugin.rnd(8));
        switch (coreType) {
        case WATER:
            if (!block.isLiquid()) return null;
            break;
        default:
            if (!block.isEmpty()) return null;
            if (!block.getRelative(0, -1, 0).isSolid()) return null;
            break;
        }
        T result = world.spawn(block.getLocation().add(0.5, 0.0, 0.5), clazz, e -> {
                final double health = 100.0;
                if (e.getHealth() < health) {
                    e.getAttribute(Attribute.GENERIC_MAX_HEALTH).setBaseValue(health);
                    e.setHealth(health);
                    e.getAttribute(Attribute.GENERIC_ATTACK_DAMAGE).setBaseValue(10.0);
                    e.getAttribute(Attribute.GENERIC_KNOCKBACK_RESISTANCE).setBaseValue(1.0);
                }
                e.setPersistent(false);
                e.setRemoveWhenFarAway(false);
                e.customName(text(toCamelCase(" ", coreType), GOLD, BOLD));
                e.setCustomNameVisible(true);
                if (collidesWithBlock(world, e.getBoundingBox())) {
                    e.remove();
                }
            });
        if (result == null || result.isDead()) return null;
        plugin.getLogger().info(coreType + ": spawned " + result.getType()
                                + " at " + Blocks.toString(block));
        return result;
    }

    void respawnBosses() {
        if (state.cores.isEmpty()) {
            if (boss == null || !boss.isValid()) {
                boss = null;
                World world = getWorld();
                Location loc = world.getSpawnLocation();
                if (loc.getWorld().isChunkLoaded(loc.getBlockX() >> 4,
                                                 loc.getBlockZ() >> 4)) {
                    boss = world.spawn(loc, Wither.class, e -> {
                            e.setPersistent(false);
                            e.setRemoveWhenFarAway(true);
                            final double health = 1024.0;
                            e.getAttribute(Attribute.GENERIC_MAX_HEALTH).setBaseValue(health);
                            e.setHealth(health);
                            e.getAttribute(Attribute.GENERIC_ARMOR).setBaseValue(10);
                            e.getAttribute(Attribute.GENERIC_ARMOR_TOUGHNESS).setBaseValue(10);
                        });
                }
            }
            return;
        }
        if (countCoreBlocks(CoreType.WATER) > 0) {
            if (waterBoss == null || waterBoss.isDead()) {
                waterBoss = null;
                if (waterBossCooldown > 0) {
                    waterBossCooldown -= 1;
                } else {
                    waterBoss = spawnBoss(CoreType.WATER, ElderGuardian.class);
                }
            }
        }
        if (countCoreBlocks(CoreType.MANSION) > 0) {
            if (mansionBoss == null || mansionBoss.isDead()) {
                mansionBoss = null;
                if (mansionBossCooldown > 0) {
                    mansionBossCooldown -= 1;
                } else {
                    mansionBoss = spawnBoss(CoreType.MANSION, Illusioner.class);
                }
            }
        }
        if (countCoreBlocks(CoreType.END) > 0) {
            if (endBoss == null || endBoss.isDead()) {
                endBoss = null;
                if (endBossCooldown > 0) {
                    endBossCooldown -= 1;
                } else {
                    endBoss = spawnBoss(CoreType.END, WitherSkeleton.class);
                }
            }
        }
    }

    protected void regen() {
        World world = getWorld();
        World mirror = getMirrorWorld();
        if (world == null || mirror == null) return;
        int inx = plugin.random.nextInt(16);
        int inz = plugin.random.nextInt(16);
        List<Chunk> chunks = new ArrayList<>();
        for (Chunk chunk : world.getLoadedChunks()) chunks.add(chunk);
        Collections.shuffle(chunks, plugin.random);
        for (Chunk chunk : chunks) {
            if (chunk.getLoadLevel() != Chunk.LoadLevel.ENTITY_TICKING) continue;
            int x = (chunk.getX() << 4) + inx;
            int z = (chunk.getZ() << 4) + inz;
            int hi = mirror.getHighestBlockYAt(x, z);
            if (hi <= 0) continue;
            for (int y = world.getMinHeight(); y <= hi; y += 1) {
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
    protected void spawnAll() {
        World world = getWorld();
        World mirror = getMirrorWorld();
        if (world == null || mirror == null) return;
        int inx = plugin.random.nextInt(16);
        int inz = plugin.random.nextInt(16);
        List<Chunk> chunks = new ArrayList<>();
        for (Chunk chunk : world.getLoadedChunks()) chunks.add(chunk);
        Collections.shuffle(chunks, plugin.random);
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

    void clearMobs() {
        if (boss != null) boss.remove();
        if (waterBoss != null) waterBoss.remove();
        if (mansionBoss != null) mansionBoss.remove();
        if (endBoss != null) endBoss.remove();
    }

    protected void addScore(Player player, int value) {
        state.addScore(player.getUniqueId(), value);
        computeHighscore();
    }

    protected void computeHighscore() {
        highscore = Highscore.of(plugin.windicator.getState().scores);
        highscoreLines = Highscore.sidebar(highscore, TrophyCategory.SWORD);
    }
}
