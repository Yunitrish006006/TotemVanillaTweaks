package dev.totem.vanillatweaks.network;

/** Capability lookup for screens owned by the Observer runtime itself. */
public final class ObserverScreenCapabilities {
    private ObserverScreenCapabilities() {}

    /**
     * Returns the existing v4 capability for a vanilla semantic family.
     * Feature-module families deliberately return zero; they use the generic
     * owned-screen capability and ProviderIdentity negotiation instead.
     */
    public static long vanillaCapability(String familyId) {
        if (familyId == null) return 0L;
        return switch (familyId) {
            case ObserverNativeScreenPayloads.FAMILY_CONTAINER_SLOTS -> ObserverNativeScreenPayloads.CAPABILITY_CONTAINER_SLOTS;
            case ObserverNativeScreenPayloads.FAMILY_FURNACE -> ObserverNativeScreenPayloads.CAPABILITY_FURNACE;
            case ObserverNativeScreenPayloads.FAMILY_BOOK -> ObserverNativeScreenPayloads.CAPABILITY_BOOK;
            case ObserverNativeScreenPayloads.FAMILY_CRAFTING -> ObserverNativeScreenPayloads.CAPABILITY_CRAFTING;
            case ObserverNativeScreenPayloads.FAMILY_MERCHANT -> ObserverNativeScreenPayloads.CAPABILITY_MERCHANT;
            case ObserverNativeScreenPayloads.FAMILY_ANVIL -> ObserverNativeScreenPayloads.CAPABILITY_ANVIL;
            case ObserverNativeScreenPayloads.FAMILY_ENCHANTING -> ObserverNativeScreenPayloads.CAPABILITY_ENCHANTING;
            case ObserverBrewingScreenPayloads.FAMILY_ID -> ObserverBrewingScreenPayloads.CAPABILITY;
            case ObserverSmithingScreenPayloads.FAMILY_ID -> ObserverSmithingScreenPayloads.CAPABILITY;
            case ObserverStonecutterScreenPayloads.FAMILY_ID -> ObserverStonecutterScreenPayloads.CAPABILITY;
            case ObserverGrindstoneScreenPayloads.FAMILY_ID -> ObserverGrindstoneScreenPayloads.CAPABILITY;
            case ObserverLoomScreenPayloads.FAMILY_ID -> ObserverLoomScreenPayloads.CAPABILITY;
            case ObserverCartographyScreenPayloads.FAMILY_ID -> ObserverCartographyScreenPayloads.CAPABILITY;
            case ObserverBeaconScreenPayloads.FAMILY_ID -> ObserverBeaconScreenPayloads.CAPABILITY;
            case ObserverSignScreenPayloads.FAMILY_ID -> ObserverSignScreenPayloads.CAPABILITY;
            case ObserverCrafterScreenPayloads.FAMILY_ID -> ObserverCrafterScreenPayloads.CAPABILITY;
            case ObserverAdvancementsScreenPayloads.FAMILY_ID -> ObserverAdvancementsScreenPayloads.CAPABILITY;
            case ObserverStatsScreenPayloads.FAMILY_ID -> ObserverStatsScreenPayloads.CAPABILITY;
            case ObserverHorseScreenPayloads.FAMILY_ID -> ObserverHorseScreenPayloads.CAPABILITY;
            default -> 0L;
        };
    }

    public static boolean isReservedVanillaFamily(String familyId) {
        return vanillaCapability(familyId) != 0L;
    }
}
