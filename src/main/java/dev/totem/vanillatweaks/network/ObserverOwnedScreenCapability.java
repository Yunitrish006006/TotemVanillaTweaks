package dev.totem.vanillatweaks.network;

/**
 * One transport capability for every module-owned Observer provider.
 * Family/protocol compatibility is negotiated through ProviderIdentity rather
 * than allocating another global capability bit for every feature module.
 */
public final class ObserverOwnedScreenCapability {
    /** First capability after the current v4 screen/cursor range (bits 0..25). */
    public static final long CAPABILITY = 1L << 26;

    private ObserverOwnedScreenCapability() {}
}
