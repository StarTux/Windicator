package com.cavetale.windicator;

import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;
import lombok.Getter;
import org.bukkit.plugin.java.JavaPlugin;

@Getter
public final class WindicatorPlugin extends JavaPlugin {
    private final Windicator windicator = new Windicator(this);
    private final EventListener listener = new EventListener(this);
    private final Tick tick = new Tick(this);
    private final Random random = ThreadLocalRandom.current();
    private final WindicatorCommand command = new WindicatorCommand(this);

    @Override
    public void onEnable() {
        getServer().getScheduler().runTaskTimer(this, tick, 1L, 1L);
        getServer().getPluginManager().registerEvents(listener, this);
        command.enable();
        windicator.load();
    }

    @Override
    public void onDisable() {
        windicator.save();
        windicator.clearMobs();
        windicator.disable();
    }

    public int rnd(int len) {
        return random.nextBoolean()
            ? random.nextInt(len)
            : -random.nextInt(len);
    }
}
