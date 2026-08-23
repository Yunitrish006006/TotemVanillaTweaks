package dev.totem.vanillatweaks.skeleton;

import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

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
        Set<Integer> observed = new HashSet<>();
        RandomSource random = RandomSource.create(0x544F54454DL + difficulty.ordinal());
        for (int sample = 0; sample < 4096; sample++) {
            int value = SkeletonAmmoRules.initialArrowCount(difficulty, random);
            observed.add(value);
            assertTrue(value >= min && value <= max,
                    () -> difficulty + " rolled " + value + " outside " + min + "-" + max);
        }
        assertTrue(observed.size() > 1, difficulty + " ammunition roll was not random");
    }
}
