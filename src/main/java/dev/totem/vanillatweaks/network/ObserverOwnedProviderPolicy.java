package dev.totem.vanillatweaks.network;

import java.util.LinkedHashSet;
import java.util.Set;

/** Bounded, module-agnostic validation for owner-provided Screen identities. */
public final class ObserverOwnedProviderPolicy {
    private static final int MAX_PROTOCOL_VERSION = 32_767;

    private ObserverOwnedProviderPolicy() {}

    public static Set<ObserverOwnedScreenPayloads.ProviderIdentity> validate(
            ObserverOwnedScreenPayloads.ProviderSet payload) {
        if (payload == null || payload.protocolVersion() != ObserverOwnedScreenPayloads.PROTOCOL_VERSION
                || payload.providers() == null || payload.providers().size() > ObserverOwnedScreenPayloads.MAX_PROVIDERS) {
            return null;
        }
        LinkedHashSet<ObserverOwnedScreenPayloads.ProviderIdentity> accepted = new LinkedHashSet<>();
        LinkedHashSet<String> families = new LinkedHashSet<>();
        for (ObserverOwnedScreenPayloads.ProviderIdentity provider : payload.providers()) {
            if (!validIdentity(provider) || !families.add(provider.familyId()) || !accepted.add(provider)) {
                return null;
            }
        }
        return Set.copyOf(accepted);
    }

    public static boolean validIdentity(ObserverOwnedScreenPayloads.ProviderIdentity provider) {
        if (provider == null || provider.familyId() == null || provider.familyId().isBlank()
                || provider.familyId().length() > 64 || provider.protocolVersion() < 1
                || provider.protocolVersion() > MAX_PROTOCOL_VERSION) {
            return false;
        }
        if (ObserverScreenCapabilities.isReservedVanillaFamily(provider.familyId())) {
            return false;
        }
        for (int i = 0; i < provider.familyId().length(); i++) {
            char c = provider.familyId().charAt(i);
            if (!(c >= 'a' && c <= 'z') && !(c >= '0' && c <= '9')
                    && c != '_' && c != '-' && c != '.' && c != ':') {
                return false;
            }
        }
        return true;
    }
}
