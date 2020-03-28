package com.cavetale.windicator;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * JSONable.
 */
@ToString @EqualsAndHashCode
public final class State {
    boolean enabled = false;
    boolean victory = false;
    String world;
    String mirrorWorld;
    Map<String, List<Vec3>> cores = new HashMap<>();

    boolean isValid() {
        if (!enabled) return false;
        if (world == null) return false;
        return true;
    }
}
