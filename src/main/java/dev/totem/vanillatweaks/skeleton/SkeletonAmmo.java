package dev.totem.vanillatweaks.skeleton;

import com.mojang.serialization.Codec;
import dev.totem.vanillatweaks.TotemVanillaTweaks;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.monster.skeleton.AbstractSkeleton;
import net.minecraft.resources.Identifier;

/** Server-authoritative finite ammunition for bow-using skeletons. */
public final class SkeletonAmmo {
    public static final AttachmentType<Integer> REMAINING_ARROWS = AttachmentRegistry.createPersistent(
            Identifier.fromNamespaceAndPath(TotemVanillaTweaks.MOD_ID, "skeleton_remaining_arrows"),
            Codec.INT
    );

    private SkeletonAmmo() {
    }

    public static void register() {
        ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
            if (entity instanceof AbstractSkeleton skeleton && skeleton.getAttached(REMAINING_ARROWS) == null) {
                skeleton.setAttached(REMAINING_ARROWS, initialArrowCount(level.getDifficulty(), skeleton.getRandom()));
            }
        });
    }

    public static boolean hasAmmo(AbstractSkeleton skeleton) {
        Integer remaining = skeleton.getAttached(REMAINING_ARROWS);
        return remaining == null || remaining > 0;
    }

    public static int remaining(AbstractSkeleton skeleton) {
        Integer remaining = skeleton.getAttached(REMAINING_ARROWS);
        return remaining == null ? -1 : Math.max(0, remaining);
    }

    public static void consumeArrow(AbstractSkeleton skeleton) {
        Integer remaining = skeleton.getAttached(REMAINING_ARROWS);
        if (remaining == null) {
            Difficulty difficulty = skeleton.level().getDifficulty();
            remaining = initialArrowCount(difficulty, skeleton.getRandom());
        }
        if (remaining <= 0) {
            skeleton.setAttached(REMAINING_ARROWS, 0);
            skeleton.reassessWeaponGoal();
            return;
        }

        int updated = remaining - 1;
        skeleton.setAttached(REMAINING_ARROWS, updated);
        if (updated == 0) {
            skeleton.reassessWeaponGoal();
        }
    }

    /** Rolls the reserve once when a skeleton first enters the server world. */
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
