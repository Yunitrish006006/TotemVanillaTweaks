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
    void freshRegionsKeepOriginalDifficultyRanges() {
        assertRange(fresh(Difficulty.EASY), 4, 7);
        assertRange(fresh(Difficulty.NORMAL), 7, 12);
        assertRange(fresh(Difficulty.HARD), 12, 20);
    }

    @Test
    void matureRegionsIncreaseArrowRanges() {
        DifficultyInstance easy = mature(Difficulty.EASY);
        DifficultyInstance normal = mature(Difficulty.NORMAL);
        DifficultyInstance hard = mature(Difficulty.HARD);

        assertEquals(3, SkeletonAmmoRules.regionalBonus(easy));
        assertEquals(6, SkeletonAmmoRules.regionalBonus(normal));
        assertEquals(10, SkeletonAmmoRules.regionalBonus(hard));

        assertRange(easy, 7, 10);
        assertRange(normal, 13, 18);
        assertRange(hard, 22, 30);
    }

    @Test
    void regionalScaleGrowsBetweenFreshAndMatureRegions() {
        for (Difficulty difficulty : new Difficulty[]{Difficulty.EASY, Difficulty.NORMAL, Difficulty.HARD}) {
            float fresh = SkeletonAmmoRules.regionalScale(fresh(difficulty));
            float middle = SkeletonAmmoRules.regionalScale(new DifficultyInstance(
                    difficulty,
                    792_000L,
                    1_800_000L,
                    0.5F
            ));
            float mature = SkeletonAmmoRules.regionalScale(mature(difficulty));

            assertEquals(0.0F, fresh, 0.0001F);
            assertTrue(middle > fresh && middle < mature,
                    difficulty + " regional scale did not increase through an inhabited region");
            assertEquals(1.0F, mature, 0.0001F);
        }
    }

    private static DifficultyInstance fresh(Difficulty difficulty) {
        return new DifficultyInstance(difficulty, 0L, 0L, 0.0F);
    }

    private static DifficultyInstance mature(Difficulty difficulty) {
        return new DifficultyInstance(difficulty, 2_000_000L, 4_000_000L, 1.0F);
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
}
