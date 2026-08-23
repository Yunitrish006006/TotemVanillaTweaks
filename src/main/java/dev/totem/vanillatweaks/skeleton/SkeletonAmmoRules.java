package dev.totem.vanillatweaks.skeleton;

import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.DifficultyInstance;

/** Difficulty and regional-difficulty ammunition rules, independent of Fabric attachment runtime. */
public final class SkeletonAmmoRules {
    private SkeletonAmmoRules() {
    }

    public static int initialArrowCount(DifficultyInstance difficulty, RandomSource random) {
        Difficulty base = difficulty.getDifficulty();
        int rolledBase = switch (base) {
            case PEACEFUL -> 0;
            case EASY -> betweenInclusive(random, 4, 7);
            case NORMAL -> betweenInclusive(random, 7, 12);
            case HARD -> betweenInclusive(random, 12, 20);
        };
        return rolledBase + regionalBonus(difficulty);
    }

    static int regionalBonus(DifficultyInstance difficulty) {
        int maxBonus = switch (difficulty.getDifficulty()) {
            case PEACEFUL -> 0;
            case EASY -> 3;
            case NORMAL -> 6;
            case HARD -> 10;
        };
        return Math.round(regionalScale(difficulty) * maxBonus);
    }

    static float regionalScale(DifficultyInstance difficulty) {
        float effective = difficulty.getEffectiveDifficulty();
        float minimum;
        float span;
        switch (difficulty.getDifficulty()) {
            case PEACEFUL -> {
                return 0.0F;
            }
            case EASY -> {
                minimum = 0.75F;
                span = 0.75F;
            }
            case NORMAL -> {
                minimum = 1.5F;
                span = 2.5F;
            }
            case HARD -> {
                minimum = 2.25F;
                span = 4.5F;
            }
            default -> throw new IllegalStateException("Unexpected difficulty: " + difficulty.getDifficulty());
        }
        return Math.max(0.0F, Math.min(1.0F, (effective - minimum) / span));
    }

    private static int betweenInclusive(RandomSource random, int min, int max) {
        return min + random.nextInt(max - min + 1);
    }
}
