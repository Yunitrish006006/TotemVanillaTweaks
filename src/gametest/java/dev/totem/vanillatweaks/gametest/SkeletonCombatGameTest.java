package dev.totem.vanillatweaks.gametest;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.entity.EntityType;

/** Integration coverage for the skeleton combat datapack changes. */
public final class SkeletonCombatGameTest {
    @GameTest(maxTicks = 20)
    public void skeletonVariantsAreSensitiveToBaneOfArthropods(GameTestHelper helper) {
        assertBaneSensitive(helper, EntityType.SKELETON, "skeleton");
        assertBaneSensitive(helper, EntityType.STRAY, "stray");
        assertBaneSensitive(helper, EntityType.BOGGED, "bogged");
        assertBaneSensitive(helper, EntityType.WITHER_SKELETON, "wither skeleton");
        assertBaneSensitive(helper, EntityType.SKELETON_HORSE, "skeleton horse");
        assertBaneSensitive(helper, EntityType.PARCHED, "parched");
        helper.succeed();
    }

    private static void assertBaneSensitive(GameTestHelper helper, EntityType<?> type, String name) {
        if (!BuiltInRegistries.ENTITY_TYPE.wrapAsHolder(type).is(EntityTypeTags.SENSITIVE_TO_BANE_OF_ARTHROPODS)) {
            throw helper.assertionException(name + " is not sensitive to Bane of Arthropods");
        }
    }
}
