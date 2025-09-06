package com.cavetale.windicator;

import com.cavetale.core.struct.Vec3i;
import java.io.Serializable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.Data;

/**
 * JSONable.
 */
@Data
public final class State implements Serializable {
    private boolean enabled = false;
    private boolean victory = false;
    private Map<CoreType, List<Vec3i>> cores = new HashMap<>();
    private Map<UUID, Integer> scores = new HashMap<>();

    public boolean isValid() {
        return enabled;
    }

    public int getScore(UUID uuid) {
        return scores.getOrDefault(uuid, 0);
    }

    public void addScore(UUID uuid, int value) {
        scores.put(uuid, getScore(uuid) + value);
    }
}
