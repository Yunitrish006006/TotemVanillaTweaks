package dev.totem.vanillatweaks.skeleton;

import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;

/** Pure difficulty-to-ammunition rules, independent of Fabric attachment runtime. */
public final class SkeletonAmmoRules {
    private SkeletonAmmoRules() {
    }

    public static int initialArrowCount(Difficulty difficulty, RandomSource random) {
        return switch (difficulty) {
            case PEACEFUL -> 0;
            case EASY -> betweenInclusive(random, 4, 7);
            case NORMAL -> betweenInclusive(random, 7, 12);
            case HARD -> betweenInclusive(random, 12, 20);
        };
    }

    private static int betweenInclusive(RandomSource random, int min, int max) {
        return min + random.nextInt(max - min + 1);
    }
}
