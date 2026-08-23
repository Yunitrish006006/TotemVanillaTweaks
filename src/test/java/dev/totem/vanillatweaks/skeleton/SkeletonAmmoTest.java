package dev.totem.vanillatweaks.skeleton;

import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkeletonAmmoTest {
    @Test
    void peacefulSkeletonsHaveNoArrows() {
        assertEquals(0, SkeletonAmmoRules.initialArrowCount(Difficulty.PEACEFUL, RandomSource.create(1L)));
    }

    @Test
    void ammoRangesIncreaseWithDifficulty() {
        assertRange(Difficulty.EASY, 4, 7);
        assertRange(Difficulty.NORMAL, 7, 12);
        assertRange(Difficulty.HARD, 12, 20);
    }

    private static void assertRange(Difficulty difficulty, int min, int max) {
        boolean sawMin = false;
        boolean sawMax = false;
        for (long seed = 0; seed < 4096; seed++) {
            int value = SkeletonAmmoRules.initialArrowCount(difficulty, RandomSource.create(seed));
            assertTrue(value >= min && value <= max,
                    () -> difficulty + " rolled " + value + " outside " + min + "-" + max);
            sawMin |= value == min;
            sawMax |= value == max;
        }
        assertTrue(sawMin, difficulty + " never reached configured minimum " + min);
        assertTrue(sawMax, difficulty + " never reached configured maximum " + max);
    }
}
