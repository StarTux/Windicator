package com.cavetale.windicator;

import lombok.RequiredArgsConstructor;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

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
                for (String name : plugin.windicator.listCores()) {
                    boolean spawned = false;
                    for (Block block : plugin.windicator.getCoreBlocks(name)) {
                        if (!block.getType().isSolid()) {
                            plugin.windicator.removeCore(block, name);
                            plugin.windicator.save();
                            continue;
                        } else {
                            block.getWorld().spawnParticle(org.bukkit.Particle.LAVA,
                                                           block.getLocation().add(0.5, 1.0, 0.5),
                                                           8, 0.125, 0.125, 0.125, 0.0);
                        }
                        spawned = plugin.windicator.createNewSpawner(block, name);
                    }
                    plugin.windicator.respawn();
                }
            }
            plugin.windicator.regen();
        }
        ticks += 1;
    }
}
