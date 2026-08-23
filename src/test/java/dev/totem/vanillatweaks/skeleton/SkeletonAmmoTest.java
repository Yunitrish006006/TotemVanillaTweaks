package dev.totem.vanillatweaks.skeleton;

import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.DifficultyInstance;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkeletonAmmoTest {
    @Test
    void peacefulSkeletonsHaveNoArrows() {
        assertEquals(0, SkeletonAmmoRules.initialArrowCount(fresh(Difficulty.PEACEFUL), RandomSource.create(1L)));
    }

    @Test
    void baseAmmoRangesAreLower() {
        assertBaseRange(Difficulty.EASY, 3, 5);
        assertBaseRange(Difficulty.NORMAL, 5, 8);
        assertBaseRange(Difficulty.HARD, 8, 12);
    }

    @Test
    void matureRegionsUseVanillaSpecialMultiplierForMaximumBonus() {
        DifficultyInstance easy = mature(Difficulty.EASY);
        DifficultyInstance normal = mature(Difficulty.NORMAL);
        DifficultyInstance hard = mature(Difficulty.HARD);

        assertEquals(0.0F, SkeletonAmmoRules.regionalScale(easy), 0.0001F);
        assertEquals(1.0F, SkeletonAmmoRules.regionalScale(normal), 0.0001F);
        assertEquals(1.0F, SkeletonAmmoRules.regionalScale(hard), 0.0001F);

        assertEquals(0, SkeletonAmmoRules.regionalBonus(easy, RandomSource.create(1L)));
        assertEquals(2, SkeletonAmmoRules.regionalBonus(normal, RandomSource.create(2L)));
        assertEquals(4, SkeletonAmmoRules.regionalBonus(hard, RandomSource.create(3L)));

        assertRange(easy, 3, 5);
        assertRange(normal, 7, 10);
        assertRange(hard, 12, 16);
    }

    @Test
    void intermediateRegionsProduceProbabilisticBonus() {
        DifficultyInstance normal = new DifficultyInstance(Difficulty.NORMAL, 792_000L, 1_800_000L, 0.5F);
        DifficultyInstance hard = new DifficultyInstance(Difficulty.HARD, 792_000L, 1_800_000L, 0.5F);

        assertTrue(SkeletonAmmoRules.regionalScale(normal) > 0.0F
                && SkeletonAmmoRules.regionalScale(normal) < 1.0F);
        assertTrue(SkeletonAmmoRules.regionalScale(hard) > 0.0F
                && SkeletonAmmoRules.regionalScale(hard) < 1.0F);

        assertBonusVaries(normal, 0, 2);
        assertBonusVaries(hard, 0, 4);
    }

    private static DifficultyInstance fresh(Difficulty difficulty) {
        return new DifficultyInstance(difficulty, 0L, 0L, 0.0F);
    }

    private static DifficultyInstance mature(Difficulty difficulty) {
        return new DifficultyInstance(difficulty, 2_000_000L, 4_000_000L, 1.0F);
    }

    private static void assertBaseRange(Difficulty difficulty, int min, int max) {
        Set<Integer> observed = new HashSet<>();
        RandomSource random = RandomSource.create(0x42415345L + difficulty.ordinal());
        for (int sample = 0; sample < 4096; sample++) {
            int value = SkeletonAmmoRules.baseArrowCount(difficulty, random);
            observed.add(value);
            assertTrue(value >= min && value <= max,
                    () -> difficulty + " base ammo " + value + " outside " + min + "-" + max);
        }
        assertTrue(observed.size() > 1, difficulty + " base ammunition roll was not random");
    }

    private static void assertRange(DifficultyInstance difficulty, int min, int max) {
        Set<Integer> observed = new HashSet<>();
        RandomSource random = RandomSource.create(0x544F54454DL + difficulty.getDifficulty().ordinal());
        for (int sample = 0; sample < 4096; sample++) {
            int value = SkeletonAmmoRules.initialArrowCount(difficulty, random);
            observed.add(value);
            assertTrue(value >= min && value <= max,
                    () -> difficulty.getDifficulty() + " rolled " + value + " outside " + min + "-" + max);
        }
        assertTrue(observed.size() > 1, difficulty.getDifficulty() + " ammunition roll was not random");
    }

    private static void assertBonusVaries(DifficultyInstance difficulty, int min, int max) {
        Set<Integer> observed = new HashSet<>();
        RandomSource random = RandomSource.create(0x4C4F43414CL + difficulty.getDifficulty().ordinal());
        for (int sample = 0; sample < 4096; sample++) {
            int value = SkeletonAmmoRules.regionalBonus(difficulty, random);
            observed.add(value);
            assertTrue(value >= min && value <= max,
                    () -> difficulty.getDifficulty() + " regional bonus " + value + " outside " + min + "-" + max);
        }
        assertTrue(observed.size() > 1, difficulty.getDifficulty() + " regional bonus did not vary");
    }
}
