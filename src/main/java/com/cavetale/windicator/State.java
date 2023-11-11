package com.cavetale.windicator;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * JSONable.
 */
@ToString @EqualsAndHashCode
public final class State {
    boolean enabled = false;
    boolean victory = false;
    Map<String, List<Vec3>> cores = new HashMap<>();
    Map<UUID, Integer> scores = new HashMap<>();

    boolean isValid() {
        return enabled;
    }

    protected int getScore(UUID uuid) {
        return scores.getOrDefault(uuid, 0);
    }

    protected void addScore(UUID uuid, int value) {
        scores.put(uuid, getScore(uuid) + value);
    }
}
