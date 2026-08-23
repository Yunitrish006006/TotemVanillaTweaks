package dev.totem.vanillatweaks.skeleton;

import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.DifficultyInstance;

/** Difficulty and regional-difficulty ammunition rules, independent of Fabric attachment runtime. */
public final class SkeletonAmmoRules {
    private SkeletonAmmoRules() {
    }

    public static int initialArrowCount(DifficultyInstance difficulty, RandomSource random) {
        int base = baseArrowCount(difficulty.getDifficulty(), random);
        return base + regionalBonus(difficulty, random);
    }

    static int baseArrowCount(Difficulty difficulty, RandomSource random) {
        return switch (difficulty) {
            case PEACEFUL -> 0;
            case EASY -> betweenInclusive(random, 3, 5);
            case NORMAL -> betweenInclusive(random, 5, 8);
            case HARD -> betweenInclusive(random, 8, 12);
        };
    }

    static int regionalBonus(DifficultyInstance difficulty, RandomSource random) {
        int bonusAttempts = switch (difficulty.getDifficulty()) {
            case PEACEFUL, EASY -> 0;
            case NORMAL -> 2;
            case HARD -> 4;
        };
        float chance = regionalScale(difficulty);
        int bonus = 0;
        for (int attempt = 0; attempt < bonusAttempts; attempt++) {
            if (random.nextFloat() < chance) {
                bonus++;
            }
        }
        return bonus;
    }

    static float regionalScale(DifficultyInstance difficulty) {
        return difficulty.getSpecialMultiplier();
    }

    private static int betweenInclusive(RandomSource random, int min, int max) {
        return min + random.nextInt(max - min + 1);
    }
}
