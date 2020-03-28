package com.cavetale.windicator;

import lombok.Value;
import org.bukkit.block.Block;

/**
 * JSONable.
 */
@Value
final class Vec3 {
    final int x;
    final int y;
    final int z;

    public boolean isSimilar(Block block) {
        return x == block.getX()
            && y == block.getY()
            && z == block.getZ();
    }

    public static Vec3 of(Block block) {
        return new Vec3(block.getX(),
                        block.getY(),
                        block.getZ());
    }

    @Override
    public String toString() {
        return x + "," + y + "," + z;
    }
}
