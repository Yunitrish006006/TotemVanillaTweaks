package dev.totem.vanillatweaks.gametest;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.entity.EntityType;

/** Integration coverage for the skeleton combat datapack changes. */
public final class SkeletonCombatGameTest {
    @GameTest(maxTicks = 20)
    public void skeletonVariantsAreSensitiveToBaneOfArthropods(GameTestHelper helper) {
        assertBaneSensitive(helper, "skeleton");
        assertBaneSensitive(helper, "stray");
        assertBaneSensitive(helper, "bogged");
        assertBaneSensitive(helper, "wither_skeleton");
        assertBaneSensitive(helper, "skeleton_horse");
        assertBaneSensitive(helper, "parched");
        helper.succeed();
    }

    private static void assertBaneSensitive(GameTestHelper helper, String path) {
        Identifier id = Identifier.fromNamespaceAndPath("minecraft", path);
        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getValue(id);
        if (type == null) {
            throw helper.assertionException("Missing entity type: " + id);
        }
        if (!BuiltInRegistries.ENTITY_TYPE.wrapAsHolder(type).is(EntityTypeTags.SENSITIVE_TO_BANE_OF_ARTHROPODS)) {
            throw helper.assertionException(path + " is not sensitive to Bane of Arthropods");
        }
    }
}
