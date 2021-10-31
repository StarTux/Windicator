package com.cavetale.windicator;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.CreatureSpawner;

public final class Blocks {
    private Blocks() { }

    public static Collection<CreatureSpawner> findNearbySpawners(Block origin, int radiusX, int radiusY, int radiusZ) {
        final int ox = origin.getX();
        final int oy = origin.getY();
        final int oz = origin.getZ();
        int ax = (ox - radiusX) >> 4;
        int az = (oz - radiusZ) >> 4;
        int bx = (ox + radiusX) >> 4;
        int bz = (oz + radiusZ) >> 4;
        World world = origin.getWorld();
        List<CreatureSpawner> list = new ArrayList<>();
        for (int cz = az; cz <= bz; cz += 1) {
            for (int cx = ax; cx <= bx; cx += 1) {
                Chunk chunk = world.getChunkAt(cx, cz);
                for (BlockState state : chunk.getTileEntities()) {
                    if (!(state instanceof CreatureSpawner)) continue;
                    Block block = state.getBlock();
                    if (block.equals(origin)) continue;
                    if (Math.abs(block.getX() - ox) > radiusX) continue;
                    if (Math.abs(block.getY() - oy) > radiusY) continue;
                    if (Math.abs(block.getZ() - oz) > radiusZ) continue;
                    list.add((CreatureSpawner) state);
                }
            }
        }
        return list;
    }

    public static Collection<CreatureSpawner> findNearbySpawners(Block origin, int radius) {
        return findNearbySpawners(origin, radius, radius, radius);
    }

    public static String toString(Block block) {
        return block.getWorld().getName()
            + " " + block.getX() + " " + block.getY() + " " + block.getZ();
    }
}
