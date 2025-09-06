package com.cavetale.windicator;

import java.util.Set;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.bukkit.entity.EntityType;
import static com.cavetale.core.util.CamelCase.toCamelCase;

@Getter
@RequiredArgsConstructor
public enum CoreType {
    WATER(EntityType.ELDER_GUARDIAN, Set.of(EntityType.GUARDIAN,
                                            EntityType.DROWNED,
                                            EntityType.PUFFERFISH)),
    MANSION(EntityType.ILLUSIONER, Set.of(EntityType.PILLAGER,
                                          EntityType.RAVAGER,
                                          EntityType.ZOMBIE,
                                          EntityType.SKELETON,
                                          EntityType.VINDICATOR,
                                          EntityType.WITCH,
                                          EntityType.CREEPER,
                                          EntityType.CAVE_SPIDER,
                                          EntityType.SPIDER,
                                          EntityType.WITHER_SKELETON)),
    END(EntityType.WITHER_SKELETON, Set.of(EntityType.ENDERMAN,
                                           EntityType.GHAST,
                                           EntityType.PHANTOM,
                                           EntityType.ENDERMITE)),
    ;

    private final String displayName = toCamelCase(" ", this);
    private final EntityType bossType;
    private final Set<EntityType> coreEntities;
}
