package com.cavetale.windicator;

import lombok.RequiredArgsConstructor;
import org.bukkit.ChatColor;
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
                    player.sendTitle(ChatColor.GREEN + "Victory",
                                     ChatColor.GREEN + "Windicator");
                }
            }
        } else {
            if (ticks % 200 == 0) {
                for (String name : plugin.windicator.listCores()) {
                    boolean spawned = false;
                    for (Block block : plugin.windicator.getCoreBlocks(name)) {
                        if (!block.getType().isSolid()) {
                            plugin.windicator.removeCore(block, name);
                            plugin.windicator.save();
                            continue;
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
