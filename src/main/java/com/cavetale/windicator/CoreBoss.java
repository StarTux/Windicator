package com.cavetale.windicator;

import lombok.Data;
import org.bukkit.entity.Mob;

@Data
public final class CoreBoss {
    private int cooldown;
    private Mob mob;
}
