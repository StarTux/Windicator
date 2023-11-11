package com.cavetale.windicator;

import com.cavetale.core.exploits.PlayerPlacedBlocks;
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
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.ElderGuardian;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Illusioner;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Wither;
import org.bukkit.entity.WitherSkeleton;

@RequiredArgsConstructor
public final class Windicator {
    public static final String WORLD = "windicator";
    public static final String MIRROR_WORLD = "windicator_copy";
    final WindicatorPlugin plugin;
    @Getter private State state;
    ElderGuardian waterBoss;
    Illusioner mansionBoss;
    WitherSkeleton endBoss;
    private int waterBossCooldown;
    private int mansionBossCooldown;
    private int endBossCooldown;
    Wither boss = null;
    static final String STATE_PATH = "state.json";
    static final int BOSS_COOLDOWN = 20 * 30;

    protected void load() {
        state = plugin.json.load(STATE_PATH, State.class, State::new);
        for (Chunk chunk : getWorld().getLoadedChunks()) {
            getMirrorWorld().getChunkAtAsync(chunk.getX(), chunk.getZ(), (Consumer<Chunk>) mchunk -> {
                    mchunk.addPluginChunkTicket(plugin);
                });
        }
    }

    void save() {
        plugin.json.save(STATE_PATH, state, true);
    }

    boolean isValid() {
        return state != null && state.isValid();
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
        List<Vec3> list = getCores(coreType);
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
                              EntityType.DROWNED);
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
        final int dist = 16;
        Set<EntityType> set = getCoreEntities(coreType);
        if (set.isEmpty()) return false;
        Block block = origin.getRelative(plugin.rnd(dist),
                                         plugin.rnd(dist / 2),
                                         plugin.rnd(dist));
        if (!block.isEmpty() && !block.isLiquid()) return false;
        int nbor = 0;
        if (!block.getRelative(1, 0, 0).isEmpty()) nbor += 1;
        if (!block.getRelative(-1, 0, 0).isEmpty()) nbor += 1;
        if (!block.getRelative(0, 0, 1).isEmpty()) nbor += 1;
        if (!block.getRelative(0, 0, -1).isEmpty()) nbor += 1;
        if (!block.getRelative(0, 1, 0).isEmpty()) nbor += 1;
        if (!block.getRelative(0, -1, 0).isEmpty()) nbor += 1;
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

    <T extends Mob> T spawn(CoreType coreType, Class<T> clazz) {
        int x = 0;
        int y = 0;
        int z = 0;
        int s = 0;
        for (Vec3 vec : getCores(coreType)) {
            s += 1;
            x += vec.getX();
            y += vec.getY();
            z += vec.getZ();
        }
        x /= s;
        y /= s;
        z /= s;
        World world = getWorld();
        if (world == null) return null;
        if (!world.isChunkLoaded(x >> 4, z >> 4)) return null;
        Block origin = getWorld().getBlockAt(x, y, z);
        Block block = origin.getRelative(plugin.rnd(16),
                                         plugin.rnd(16),
                                         plugin.rnd(16));
        switch (coreType) {
        case WATER:
            if (!block.isLiquid()) return null;
            break;
        default:
            if (!block.isEmpty()) return null;
            if (!block.getRelative(0, -1, 0).getType().isSolid()) return null;
            break;
        }
        T result = world.spawn(block.getLocation().add(0.5, 0.0, 0.5), clazz);
        final double health = 100.0;
        if (result.getHealth() < health) {
            result.getAttribute(Attribute.GENERIC_MAX_HEALTH).setBaseValue(health);
            result.setHealth(health);
        }
        result.setPersistent(false);
        result.setRemoveWhenFarAway(true);
        if (result == null) return null;
        plugin.getLogger().info(coreType + ": spawned " + result.getType()
                                + " at " + Blocks.toString(block));
        return result;
    }

    void respawn() {
        if (state.cores.isEmpty()) {
            if (boss == null || !boss.isValid()) {
                boss = null;
                World world = getWorld();
                Location loc = world.getSpawnLocation();
                if (loc.getWorld().isChunkLoaded(loc.getBlockX() >> 4,
                                                 loc.getBlockZ() >> 4)) {
                    boss = world.spawn(loc, Wither.class);
                    if (boss != null) {
                        boss.setPersistent(false);
                        boss.setRemoveWhenFarAway(true);
                    }
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
                    waterBoss = spawn(CoreType.WATER, ElderGuardian.class);
                    if (waterBoss != null) {
                        waterBossCooldown = BOSS_COOLDOWN;
                    }
                }
            }
        }
        if (countCoreBlocks(CoreType.MANSION) > 0) {
            if (mansionBoss == null || mansionBoss.isDead()) {
                mansionBoss = null;
                if (mansionBossCooldown > 0) {
                    mansionBossCooldown -= 1;
                } else {
                    mansionBoss = spawn(CoreType.MANSION, Illusioner.class);
                    if (mansionBoss != null) {
                        mansionBossCooldown = BOSS_COOLDOWN;
                    }
                }
            }
        }
        if (countCoreBlocks(CoreType.END) > 0) {
            if (endBoss == null || endBoss.isDead()) {
                endBoss = null;
                if (endBossCooldown > 0) {
                    endBossCooldown -= 1;
                } else {
                    endBoss = spawn(CoreType.END, WitherSkeleton.class);
                    if (endBoss != null) {
                        endBossCooldown = BOSS_COOLDOWN;
                    }
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

    void clearMobs() {
        if (boss != null) boss.remove();
        if (waterBoss != null) waterBoss.remove();
        if (mansionBoss != null) mansionBoss.remove();
        if (endBoss != null) endBoss.remove();
    }
}
