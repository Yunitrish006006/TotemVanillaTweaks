package dev.totem.vanillatweaks.gametest;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

/**
 * Production-runtime regression for skeleton finite ammunition initialization.
 *
 * <p>This deliberately summons a real skeleton through the integrated server so
 * {@code ServerEntityEvents.ENTITY_LOAD -> SkeletonAmmo.rollInitialAmmo()} executes
 * under the distribution namespace. It exists specifically to catch linkage errors
 * that can be invisible in Loom's development/named runtime.</p>
 */
@SuppressWarnings("UnstableApiUsage")
public final class SkeletonAmmoRuntimeClientGameTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext context) {
        try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
            singleplayer.getClientLevel().waitForChunksRender();

            // Summon in the loaded player chunk so entity tracking and the
            // finite-ammunition ENTITY_LOAD callback happen synchronously.
            singleplayer.getServer().runCommand(
                    "/execute at @a run summon minecraft:skeleton ~ ~ ~"
            );
            context.waitTicks(5);

            // Reaching this command proves the server survived the ENTITY_LOAD path.
            singleplayer.getServer().runCommand(
                    "/kill @e[type=minecraft:skeleton,distance=..32]"
            );
        }
    }
}
