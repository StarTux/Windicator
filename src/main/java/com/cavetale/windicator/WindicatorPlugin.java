package com.cavetale.windicator;

import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.plugin.java.JavaPlugin;

public final class WindicatorPlugin extends JavaPlugin {
    final Windicator windicator = new Windicator(this);
    final EventListener listener = new EventListener(this);
    final Tick tick = new Tick(this);
    final Json json = new Json(this);
    final Random random = ThreadLocalRandom.current();
    final WindicatorCommand command = new WindicatorCommand(this);

    @Override
    public void onEnable() {
        getServer().getScheduler().runTaskTimer(this, tick, 1L, 1L);
        getServer().getPluginManager().registerEvents(listener, this);
        getCommand("windicator").setExecutor(command);
        windicator.load();
    }

    @Override
    public void onDisable() {
        windicator.save();
    }

    int rnd(int len) {
        return random.nextBoolean()
            ? random.nextInt(len)
            : -random.nextInt(len);
    }
}
