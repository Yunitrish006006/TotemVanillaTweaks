package dev.totem.vanillatweaks.client;

import dev.totem.vanillatweaks.network.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.achievement.StatsScreen;
import net.minecraft.client.gui.screens.advancements.AdvancementsScreen;
import net.minecraft.client.gui.screens.inventory.*;

import java.util.Optional;

/** Maps a live Mojang Screen to the semantic family whose adapter owns it. */
public final class ObserverVanillaScreenIdentity {
    private ObserverVanillaScreenIdentity() { }

    public static Optional<Identity> classify(Screen screen) {
        if (screen == null || ObserverOwnedScreenCoordinator.isReadOnlyObserverScreen(screen)) return Optional.empty();
        return classifyKnown(screen);
    }

    /** Classifies the local read-only reconstruction without accepting arbitrary custom screens. */
    public static Optional<Identity> classifyObserver(Screen screen) {
        if (!ObserverOwnedScreenCoordinator.isReadOnlyObserverScreen(screen)) return Optional.empty();
        return classifyKnown(screen);
    }

    private static Optional<Identity> classifyKnown(Screen screen) {
        if (screen instanceof BookSignScreen) return identity(ObserverNativeScreenPayloads.FAMILY_BOOK,
                ObserverBookScreenPayloads.VARIANT_SIGNING, ObserverBookScreenPayloads.PROTOCOL_VERSION);
        if (screen instanceof BookEditScreen) return identity(ObserverNativeScreenPayloads.FAMILY_BOOK,
                ObserverBookScreenPayloads.VARIANT_WRITABLE, ObserverBookScreenPayloads.PROTOCOL_VERSION);
        if (screen instanceof LecternScreen) return identity(ObserverNativeScreenPayloads.FAMILY_BOOK,
                ObserverBookScreenPayloads.VARIANT_LECTERN, ObserverBookScreenPayloads.PROTOCOL_VERSION);
        if (screen instanceof BookViewScreen) return identity(ObserverNativeScreenPayloads.FAMILY_BOOK,
                ObserverBookScreenPayloads.VARIANT_WRITTEN, ObserverBookScreenPayloads.PROTOCOL_VERSION);
        if (screen instanceof BlastFurnaceScreen) return identity(ObserverNativeScreenPayloads.FAMILY_FURNACE,
                BlastFurnaceScreen.class.getName(), ObserverNativeScreenPayloads.FURNACE_PROTOCOL_VERSION);
        if (screen instanceof SmokerScreen) return identity(ObserverNativeScreenPayloads.FAMILY_FURNACE,
                SmokerScreen.class.getName(), ObserverNativeScreenPayloads.FURNACE_PROTOCOL_VERSION);
        if (screen instanceof AbstractFurnaceScreen<?>) return identity(ObserverNativeScreenPayloads.FAMILY_FURNACE,
                FurnaceScreen.class.getName(), ObserverNativeScreenPayloads.FURNACE_PROTOCOL_VERSION);
        if (screen instanceof InventoryScreen) return identity(ObserverNativeScreenPayloads.FAMILY_CRAFTING,
                ObserverCraftingScreenPayloads.VARIANT_PLAYER_2X2, ObserverCraftingScreenPayloads.PROTOCOL_VERSION);
        if (screen instanceof CraftingScreen) return identity(ObserverNativeScreenPayloads.FAMILY_CRAFTING,
                ObserverCraftingScreenPayloads.VARIANT_TABLE_3X3, ObserverCraftingScreenPayloads.PROTOCOL_VERSION);
        if (screen instanceof MerchantScreen) return identity(ObserverNativeScreenPayloads.FAMILY_MERCHANT,
                ObserverMerchantScreenPayloads.VARIANT_VANILLA, ObserverMerchantScreenPayloads.PROTOCOL_VERSION);
        if (screen instanceof AnvilScreen) return identity(ObserverNativeScreenPayloads.FAMILY_ANVIL, "", ObserverAnvilScreenPayloads.PROTOCOL_VERSION);
        if (screen instanceof EnchantmentScreen) return identity(ObserverNativeScreenPayloads.FAMILY_ENCHANTING, "", ObserverEnchantingScreenPayloads.PROTOCOL_VERSION);
        if (screen instanceof BrewingStandScreen) return identity(ObserverBrewingScreenPayloads.FAMILY_ID, "", ObserverBrewingScreenPayloads.PROTOCOL_VERSION);
        if (screen instanceof SmithingScreen) return identity(ObserverSmithingScreenPayloads.FAMILY_ID, "", ObserverSmithingScreenPayloads.PROTOCOL_VERSION);
        if (screen instanceof StonecutterScreen) return identity(ObserverStonecutterScreenPayloads.FAMILY_ID, "", ObserverStonecutterScreenPayloads.PROTOCOL_VERSION);
        if (screen instanceof GrindstoneScreen) return identity(ObserverGrindstoneScreenPayloads.FAMILY_ID, "", ObserverGrindstoneScreenPayloads.PROTOCOL_VERSION);
        if (screen instanceof LoomScreen) return identity(ObserverLoomScreenPayloads.FAMILY_ID, "", ObserverLoomScreenPayloads.PROTOCOL_VERSION);
        if (screen instanceof CartographyTableScreen) return identity(ObserverCartographyScreenPayloads.FAMILY_ID, "", ObserverCartographyScreenPayloads.PROTOCOL_VERSION);
        if (screen instanceof BeaconScreen) return identity(ObserverBeaconScreenPayloads.FAMILY_ID, "", ObserverBeaconScreenPayloads.PROTOCOL_VERSION);
        if (screen instanceof CrafterScreen) return identity(ObserverCrafterScreenPayloads.FAMILY_ID, "", ObserverCrafterScreenPayloads.PROTOCOL_VERSION);
        if (screen instanceof AbstractSignEditScreen) return identity(ObserverSignScreenPayloads.FAMILY_ID, "", ObserverSignScreenPayloads.PROTOCOL_VERSION);
        if (screen instanceof AdvancementsScreen) return identity(ObserverAdvancementsScreenPayloads.FAMILY_ID, "", ObserverAdvancementsScreenPayloads.PROTOCOL_VERSION);
        if (screen instanceof StatsScreen) return identity(ObserverStatsScreenPayloads.FAMILY_ID, "", ObserverStatsScreenPayloads.PROTOCOL_VERSION);
        if (screen instanceof HorseInventoryScreen) return identity(ObserverHorseScreenPayloads.FAMILY_ID, "", ObserverHorseScreenPayloads.PROTOCOL_VERSION);
        if (screen instanceof HopperScreen) return identity(ObserverNativeScreenPayloads.FAMILY_CONTAINER_SLOTS,
                HopperScreen.class.getName(), ObserverNativeScreenPayloads.SCREEN_PROTOCOL_VERSION);
        if (screen instanceof DispenserScreen) return identity(ObserverNativeScreenPayloads.FAMILY_CONTAINER_SLOTS,
                DispenserScreen.class.getName(), ObserverNativeScreenPayloads.SCREEN_PROTOCOL_VERSION);
        if (screen instanceof ShulkerBoxScreen) return identity(ObserverNativeScreenPayloads.FAMILY_CONTAINER_SLOTS,
                ShulkerBoxScreen.class.getName(), ObserverNativeScreenPayloads.SCREEN_PROTOCOL_VERSION);
        if (screen instanceof ContainerScreen) return identity(ObserverNativeScreenPayloads.FAMILY_CONTAINER_SLOTS,
                ContainerScreen.class.getName(), ObserverNativeScreenPayloads.SCREEN_PROTOCOL_VERSION);
        return Optional.empty();
    }

    private static Optional<Identity> identity(String family, String variant, int protocol) {
        return Optional.of(new Identity(family, variant, protocol));
    }

    public record Identity(String family, String variant, int protocol) { }
}
