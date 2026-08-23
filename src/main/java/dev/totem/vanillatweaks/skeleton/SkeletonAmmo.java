package dev.totem.vanillatweaks.skeleton;

import com.mojang.serialization.Codec;
import dev.totem.vanillatweaks.TotemVanillaTweaks;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.monster.skeleton.AbstractSkeleton;

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
            if (entity instanceof AbstractSkeleton skeleton) {
                AttachmentTarget target = attachments(skeleton);
                if (target.getAttached(REMAINING_ARROWS) == null) {
                    target.setAttached(
                            REMAINING_ARROWS,
                            SkeletonAmmoRules.initialArrowCount(level.getDifficulty(), skeleton.getRandom())
                    );
                }
            }
        });
    }

    public static boolean hasAmmo(AbstractSkeleton skeleton) {
        Integer remaining = attachments(skeleton).getAttached(REMAINING_ARROWS);
        return remaining == null || remaining > 0;
    }

    public static int remaining(AbstractSkeleton skeleton) {
        Integer remaining = attachments(skeleton).getAttached(REMAINING_ARROWS);
        return remaining == null ? -1 : Math.max(0, remaining);
    }

    public static void consumeArrow(AbstractSkeleton skeleton) {
        AttachmentTarget target = attachments(skeleton);
        Integer remaining = target.getAttached(REMAINING_ARROWS);
        if (remaining == null) {
            Difficulty difficulty = skeleton.level().getDifficulty();
            remaining = SkeletonAmmoRules.initialArrowCount(difficulty, skeleton.getRandom());
        }
        if (remaining <= 0) {
            target.setAttached(REMAINING_ARROWS, 0);
            skeleton.reassessWeaponGoal();
            return;
        }

        int updated = remaining - 1;
        target.setAttached(REMAINING_ARROWS, updated);
        if (updated == 0) {
            skeleton.reassessWeaponGoal();
        }
    }

    private static AttachmentTarget attachments(AbstractSkeleton skeleton) {
        return (AttachmentTarget) (Object) skeleton;
    }
}
