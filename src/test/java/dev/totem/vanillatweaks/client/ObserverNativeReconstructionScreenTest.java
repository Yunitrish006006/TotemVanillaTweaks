package dev.totem.vanillatweaks.client;

import dev.totem.core.api.v1.client.observer.ObserverReadOnlyScreen;
import net.minecraft.client.gui.screens.Screen;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Enforces Mojang Screen reconstruction instead of the retired hand-drawn mirrors. */
final class ObserverNativeReconstructionScreenTest {
    private static final Set<String> EXPECTED_NATIVE_RECONSTRUCTIONS = Set.of(
            "ObserverAdvancementsScreen", "ObserverAnvilScreen", "ObserverBeaconScreen",
            "ObserverBlastFurnaceScreen", "ObserverBookEditScreen", "ObserverBookSignScreen",
            "ObserverBookViewScreen", "ObserverBrewingScreen", "ObserverCartographyScreen",
            "ObserverContainerScreen", "ObserverCrafterScreen", "ObserverCraftingTableScreen",
            "ObserverDispenserScreen", "ObserverEnchantmentScreen", "ObserverFurnaceScreen",
            "ObserverGrindstoneScreen", "ObserverHangingSignScreen", "ObserverHopperScreen",
            "ObserverInventoryScreen", "ObserverLecternScreen", "ObserverLoomScreen",
            "ObserverMerchantScreen", "ObserverPauseScreen", "ObserverShulkerScreen",
            "ObserverSignScreen", "ObserverSmithingScreen", "ObserverSmokerScreen",
            "ObserverStatsScreen", "ObserverStonecutterScreen"
    );

    private static final List<Class<?>> VANILLA_FAMILY_OWNERS = List.of(
            ObserverAdvancementsScreenClient.class, ObserverBeaconScreenClient.class,
            ObserverBrewingScreenClient.class, ObserverCartographyScreenClient.class,
            ObserverCrafterScreenClient.class, ObserverGrindstoneScreenClient.class,
            ObserverLoomScreenClient.class, ObserverNativeAnvilScreenClient.class,
            ObserverNativeBookScreenClient.class, ObserverNativeCraftingScreenClient.class,
            ObserverNativeEnchantingScreenClient.class, ObserverNativeMerchantScreenClient.class,
            ObserverNativeScreenClient.class, ObserverPauseScreenClient.class,
            ObserverSignScreenClient.class, ObserverSmithingScreenClient.class,
            ObserverStatsScreenClient.class, ObserverStonecutterScreenClient.class
    );

    @Test
    void supportedVanillaFamiliesUseReadOnlyMojangScreensWithoutLookalikes() {
        Set<String> found = new HashSet<>();
        for (Class<?> owner : VANILLA_FAMILY_OWNERS) {
            for (Class<?> nested : owner.getDeclaredClasses()) {
                String name = nested.getSimpleName();
                assertFalse(name.endsWith("Mirror" + "Screen") || name.contains("Lookalike"), nested.getName());
                if (!EXPECTED_NATIVE_RECONSTRUCTIONS.contains(name)) continue;
                found.add(name);
                assertTrue(Screen.class.isAssignableFrom(nested), nested.getName());
                assertTrue(ObserverReadOnlyScreen.class.isAssignableFrom(nested), nested.getName());
            }
        }
        assertEquals(EXPECTED_NATIVE_RECONSTRUCTIONS, found);
    }
}
