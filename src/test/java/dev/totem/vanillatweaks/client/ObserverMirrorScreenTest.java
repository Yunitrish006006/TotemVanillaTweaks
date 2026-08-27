package dev.totem.vanillatweaks.client;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ObserverMirrorScreenTest {
    private static final Set<String> EXPECTED_MIRRORS = Set.of(
            "NativeAdvancementsMirrorScreen",
            "NativeAnvilMirrorScreen",
            "NativeAutomataCopperGolemMirrorScreen",
            "NativeBeaconMirrorScreen",
            "NativeBookMirrorScreen",
            "NativeBrewingMirrorScreen",
            "NativeCartographyMirrorScreen",
            "NativeContainerMirrorScreen",
            "NativeCrafterMirrorScreen",
            "NativeCraftingMirrorScreen",
            "NativeDeathNodeAdminMirrorScreen",
            "NativeEnchantingMirrorScreen",
            "NativeFurnaceMirrorScreen",
            "NativeGenericMirrorScreen",
            "NativeGrindstoneMirrorScreen",
            "NativeLocksmithManagementMirrorScreen",
            "NativeLoomMirrorScreen",
            "NativeMerchantMirrorScreen",
            "NativeNexusMirrorScreen",
            "NativePauseMirrorScreen",
            "NativeRemnantBackpackMirrorScreen",
            "NativeSignMirrorScreen",
            "NativeSmithingMirrorScreen",
            "NativeStatsMirrorScreen",
            "NativeStonecutterMirrorScreen",
            "NativeVillagersWoodcutterMirrorScreen"
    );

    private static final List<Class<?>> MIRROR_OWNERS = List.of(
            ObserverAdvancementsScreenClient.class,
            ObserverAutomataCopperGolemScreenClient.class,
            ObserverBeaconScreenClient.class,
            ObserverBrewingScreenClient.class,
            ObserverCartographyScreenClient.class,
            ObserverCrafterScreenClient.class,
            ObserverGrindstoneScreenClient.class,
            ObserverLocksmithManagementScreenClient.class,
            ObserverLoomScreenClient.class,
            ObserverNativeAnvilScreenClient.class,
            ObserverNativeBookScreenClient.class,
            ObserverNativeCraftingScreenClient.class,
            ObserverNativeEnchantingScreenClient.class,
            ObserverNativeMerchantScreenClient.class,
            ObserverNativeScreenClient.class,
            ObserverNexusDeathNodeAdminScreenClient.class,
            ObserverNexusScreenClient.class,
            ObserverPauseScreenClient.class,
            ObserverRemnantBackpackScreenClient.class,
            ObserverSignScreenClient.class,
            ObserverSmithingScreenClient.class,
            ObserverStatsScreenClient.class,
            ObserverStonecutterScreenClient.class,
            ObserverVillagersWoodcutterScreenClient.class
    );

    @Test
    void everyObserverMirrorFamilyUsesTheCentralMarker() {
        Set<String> found = new HashSet<>();
        for (Class<?> owner : MIRROR_OWNERS) {
            for (Class<?> nested : owner.getDeclaredClasses()) {
                if (!nested.getSimpleName().endsWith("MirrorScreen")) continue;
                found.add(nested.getSimpleName());
                assertTrue(ObserverMirrorScreen.class.isAssignableFrom(nested), nested.getName());
            }
        }
        assertEquals(EXPECTED_MIRRORS, found);
    }

}
