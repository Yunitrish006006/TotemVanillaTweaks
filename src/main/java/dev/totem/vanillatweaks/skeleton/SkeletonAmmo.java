package dev.totem.vanillatweaks.skeleton;

import com.mojang.serialization.Codec;
import dev.totem.vanillatweaks.TotemVanillaTweaks;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.DifficultyInstance;
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
                    target.setAttached(REMAINING_ARROWS, rollInitialAmmo(level, skeleton));
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
            if (skeleton.level() instanceof ServerLevel serverLevel) {
                remaining = rollInitialAmmo(serverLevel, skeleton);
            } else {
                return;
            }
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

    private static int rollInitialAmmo(ServerLevel level, AbstractSkeleton skeleton) {
        DifficultyInstance localDifficulty = level.getCurrentDifficultyAt(skeleton.blockPosition());
        return SkeletonAmmoRules.initialArrowCount(localDifficulty, skeleton.getRandom());
    }

    private static AttachmentTarget attachments(AbstractSkeleton skeleton) {
        return (AttachmentTarget) (Object) skeleton;
    }
}
