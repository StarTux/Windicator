package com.cavetale.windicator;

import java.util.List;
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
        plugin.sidebar.clear();
        for (String core : Windicator.listCores()) {
            List<Vec3> list = plugin.windicator.getState().cores.get(core);
            int count = list != null ? list.size() : 0;
            plugin.sidebar.newLine(ChatColor.GRAY + core + " " + ChatColor.YELLOW + count);
        }
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            plugin.sidebar.addPlayer(player);
        }
        plugin.sidebar.setTitle(ChatColor.RED + "Cores");
        plugin.sidebar.update();
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
