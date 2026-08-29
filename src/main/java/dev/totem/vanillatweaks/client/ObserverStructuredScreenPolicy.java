package dev.totem.vanillatweaks.client;

import dev.totem.vanillatweaks.network.ObserverAdvancementsScreenPayloads;
import dev.totem.vanillatweaks.network.ObserverBeaconScreenPayloads;
import dev.totem.vanillatweaks.network.ObserverBrewingScreenPayloads;
import dev.totem.vanillatweaks.network.ObserverCartographyScreenPayloads;
import dev.totem.vanillatweaks.network.ObserverCrafterScreenPayloads;
import dev.totem.vanillatweaks.network.ObserverGrindstoneScreenPayloads;
import dev.totem.vanillatweaks.network.ObserverLocksmithManagementPayloads;
import dev.totem.vanillatweaks.network.ObserverLoomScreenPayloads;
import dev.totem.vanillatweaks.network.ObserverNativeScreenPayloads;
import dev.totem.vanillatweaks.network.ObserverNexusDeathNodeAdminPayloads;
import dev.totem.vanillatweaks.network.ObserverSignScreenPayloads;
import dev.totem.vanillatweaks.network.ObserverSmithingScreenPayloads;
import dev.totem.vanillatweaks.network.ObserverStatsScreenPayloads;
import dev.totem.vanillatweaks.network.ObserverStonecutterScreenPayloads;
import dev.totem.vanillatweaks.network.ObserverVillagersWoodcutterPayloads;
import dev.totem.vanillatweaks.network.ObserverHorseScreenPayloads;
import java.util.Map;
import java.util.Set;

/** Selects semantic Screen reconstruction before metadata-only compatibility status. */
public final class ObserverStructuredScreenPolicy {
    private static final Map<String, Long> DEDICATED_CAPABILITIES = Map.ofEntries(
            Map.entry("net.minecraft.client.gui.screens.inventory.InventoryScreen",
                    ObserverNativeScreenPayloads.CAPABILITY_CRAFTING),
            Map.entry("net.minecraft.client.gui.screens.inventory.CraftingScreen",
                    ObserverNativeScreenPayloads.CAPABILITY_CRAFTING),
            Map.entry("net.minecraft.client.gui.screens.inventory.FurnaceScreen",
                    ObserverNativeScreenPayloads.CAPABILITY_FURNACE),
            Map.entry("net.minecraft.client.gui.screens.inventory.BlastFurnaceScreen",
                    ObserverNativeScreenPayloads.CAPABILITY_FURNACE),
            Map.entry("net.minecraft.client.gui.screens.inventory.SmokerScreen",
                    ObserverNativeScreenPayloads.CAPABILITY_FURNACE),
            Map.entry("net.minecraft.client.gui.screens.inventory.MerchantScreen",
                    ObserverNativeScreenPayloads.CAPABILITY_MERCHANT),
            Map.entry("net.minecraft.client.gui.screens.inventory.AnvilScreen",
                    ObserverNativeScreenPayloads.CAPABILITY_ANVIL),
            Map.entry("net.minecraft.client.gui.screens.inventory.EnchantmentScreen",
                    ObserverNativeScreenPayloads.CAPABILITY_ENCHANTING),
            Map.entry("dev.totem.remnant.client.screen.BackpackScreen",
                    ObserverNativeScreenPayloads.CAPABILITY_REMNANT_BACKPACK),
            Map.entry("dev.totem.automata.client.CopperGolemMenuScreen",
                    ObserverNativeScreenPayloads.CAPABILITY_AUTOMATA_COPPER_GOLEM),
            Map.entry(ObserverVillagersWoodcutterPayloads.SCREEN_CLASS,
                    ObserverVillagersWoodcutterPayloads.CAPABILITY),
            Map.entry(ObserverBrewingScreenPayloads.SCREEN_CLASS,
                    ObserverBrewingScreenPayloads.CAPABILITY),
            Map.entry(ObserverSmithingScreenPayloads.SCREEN_CLASS,
                    ObserverSmithingScreenPayloads.CAPABILITY),
            Map.entry(ObserverStonecutterScreenPayloads.SCREEN_CLASS,
                    ObserverStonecutterScreenPayloads.CAPABILITY),
            Map.entry(ObserverGrindstoneScreenPayloads.SCREEN_CLASS,
                    ObserverGrindstoneScreenPayloads.CAPABILITY),
            Map.entry(ObserverLoomScreenPayloads.SCREEN_CLASS,
                    ObserverLoomScreenPayloads.CAPABILITY),
            Map.entry(ObserverCartographyScreenPayloads.SCREEN_CLASS,
                    ObserverCartographyScreenPayloads.CAPABILITY),
            Map.entry(ObserverBeaconScreenPayloads.SCREEN_CLASS,
                    ObserverBeaconScreenPayloads.CAPABILITY),
            Map.entry(ObserverSignScreenPayloads.SIGN_SCREEN_CLASS,
                    ObserverSignScreenPayloads.CAPABILITY),
            Map.entry(ObserverSignScreenPayloads.HANGING_SIGN_SCREEN_CLASS,
                    ObserverSignScreenPayloads.CAPABILITY),
            Map.entry(ObserverCrafterScreenPayloads.SCREEN_CLASS,
                    ObserverCrafterScreenPayloads.CAPABILITY),
            Map.entry(ObserverNexusDeathNodeAdminPayloads.SCREEN_CLASS,
                    ObserverNexusDeathNodeAdminPayloads.CAPABILITY),
            Map.entry(ObserverLocksmithManagementPayloads.SCREEN_CLASS,
                    ObserverLocksmithManagementPayloads.CAPABILITY),
            Map.entry(ObserverAdvancementsScreenPayloads.SCREEN_CLASS,
                    ObserverAdvancementsScreenPayloads.CAPABILITY),
            Map.entry(ObserverStatsScreenPayloads.SCREEN_CLASS,
                    ObserverStatsScreenPayloads.CAPABILITY),
            Map.entry(ObserverHorseScreenPayloads.SCREEN_CLASS,
                    ObserverHorseScreenPayloads.CAPABILITY),
            Map.entry("net.minecraft.client.gui.screens.inventory.BookViewScreen",
                    ObserverNativeScreenPayloads.CAPABILITY_BOOK),
            Map.entry("net.minecraft.client.gui.screens.inventory.BookEditScreen",
                    ObserverNativeScreenPayloads.CAPABILITY_BOOK),
            Map.entry("net.minecraft.client.gui.screens.inventory.BookSignScreen",
                    ObserverNativeScreenPayloads.CAPABILITY_BOOK),
            Map.entry("net.minecraft.client.gui.screens.inventory.LecternScreen",
                    ObserverNativeScreenPayloads.CAPABILITY_BOOK),
            Map.entry("dev.totem.nexus.client.NexusMapScreen",
                    ObserverNativeScreenPayloads.CAPABILITY_NEXUS),
            Map.entry("dev.totem.nexus.client.NexusSpaceUnitMapScreen",
                    ObserverNativeScreenPayloads.CAPABILITY_NEXUS),
            Map.entry("dev.totem.nexus.client.NexusFriendsScreen",
                    ObserverNativeScreenPayloads.CAPABILITY_NEXUS),
            Map.entry("dev.totem.nexus.client.NexusSpaceUnitFriendsScreen",
                    ObserverNativeScreenPayloads.CAPABILITY_NEXUS),
            Map.entry("dev.totem.nexus.client.NexusRegistrationPreviewScreen",
                    ObserverNativeScreenPayloads.CAPABILITY_NEXUS),
            Map.entry("dev.totem.nexus.client.NexusSpaceUnitRegistrationPreviewScreen",
                    ObserverNativeScreenPayloads.CAPABILITY_NEXUS)
    );
    private static final Set<String> EXACT_GENERIC_CONTAINERS = Set.of(
            "net.minecraft.client.gui.screens.inventory.ContainerScreen",
            "net.minecraft.client.gui.screens.inventory.HopperScreen",
            "net.minecraft.client.gui.screens.inventory.DispenserScreen",
            "net.minecraft.client.gui.screens.inventory.ShulkerBoxScreen");

    private ObserverStructuredScreenPolicy() {}

    public static boolean suppressGenericMetadata(String screenClass, long negotiatedCapabilities) {
        if (screenClass == null || screenClass.isBlank()) return false;
        long dedicated = DEDICATED_CAPABILITIES.getOrDefault(screenClass, 0L);
        if (dedicated != 0L && supports(negotiatedCapabilities, dedicated)) return true;
        return supports(negotiatedCapabilities, ObserverNativeScreenPayloads.CAPABILITY_CONTAINER_SLOTS)
                && isContainerScreenClass(screenClass);
    }

    private static boolean isContainerScreenClass(String screenClass) {
        return EXACT_GENERIC_CONTAINERS.contains(screenClass);
    }

    private static boolean supports(long capabilities, long capability) {
        return (capabilities & capability) == capability;
    }
}
