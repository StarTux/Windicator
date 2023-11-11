package com.cavetale.windicator;

import lombok.RequiredArgsConstructor;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Player;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

@RequiredArgsConstructor
public final class Tick implements Runnable {
    final WindicatorPlugin plugin;
    long ticks = 0;

    @Override
    public void run() {
        if (!plugin.windicator.isValid()) return;
        World world = plugin.windicator.getWorld();
        if (plugin.windicator.isVictory()) {
            if (ticks % 20 == 0) {
                for (Player player : plugin.windicator.getWorld().getPlayers()) {
                    player.showTitle(Title.title(Component.text("Victory", NamedTextColor.GREEN),
                                                 Component.text("Windicator", NamedTextColor.GREEN)));
                }
            }
        } else {
            if (ticks % 100 == 0) {
                for (CoreType coreType : CoreType.values()) {
                    boolean spawned = false;
                    for (Block block : plugin.windicator.getCoreBlocks(coreType)) {
                        if (!block.getType().isSolid()) {
                            plugin.windicator.removeCore(block, coreType);
                            plugin.windicator.save();
                            continue;
                        } else {
                            block.getWorld().spawnParticle(org.bukkit.Particle.LAVA,
                                                           block.getLocation().add(0.5, 1.0, 0.5),
                                                           8, 0.125, 0.125, 0.125, 0.0);
                        }
                        spawned = plugin.windicator.createNewSpawner(block, coreType);
                    }
                    plugin.windicator.respawnBosses();
                }
            }
            if (ticks % 10 == 0) plugin.windicator.spawnAll();
            plugin.windicator.regen();
            final double time = (double) ticks * 0.2;
            final float dy = (float) (Math.sin(time) * 0.1);
            for (CoreType coreType : CoreType.values()) {
                for (Block block : plugin.windicator.getCoreBlocks(coreType)) {
                    boolean hasOutline = false;
                    final Location location = block.getLocation().add(-0.05, -0.05, -0.05);
                    for (BlockDisplay bd : location.getNearbyEntitiesByType(BlockDisplay.class, 0.5, 0.5, 0.5)) {
                        bd.setTransformation(new Transformation(new Vector3f(0f, dy, 0f),
                                                                new AxisAngle4f(0f, 0f, 0f, 0f),
                                                                new Vector3f(1.1f, 1.1f, 1.1f),
                                                                new AxisAngle4f(0f, 0f, 0f, 0f)));
                        hasOutline = true;
                    }
                    if (!hasOutline) {
                        BlockDisplay blockDisplay = block.getWorld().spawn(location, BlockDisplay.class, e -> {
                                e.setPersistent(false);
                                e.setBlock(Material.SPAWNER.createBlockData());
                                e.setBrightness(new BlockDisplay.Brightness(15, 15));
                            });
                        plugin.getLogger().info("Spawned outline at " + block.getX() + " " +  block.getY() + " " + block.getZ());
                    }
                }
            }
        }
        ticks += 1;
    }
}
