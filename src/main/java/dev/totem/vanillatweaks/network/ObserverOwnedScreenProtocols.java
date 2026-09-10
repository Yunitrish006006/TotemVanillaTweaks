package dev.totem.vanillatweaks.network;

import java.util.Map;
import java.util.Set;

/** Server-controlled screen protocol registry, independent from the generic transport version. */
public final class ObserverOwnedScreenProtocols {
    private static final Map<String, Integer> EXPECTED = Map.of(
            "remnant_backpack", 1,
            "automata_copper_golem", 1,
            "nexus", 3,
            "nexus_death_node_admin", 1,
            "locksmith_management", 1,
            "villagers_woodcutter", 1);
    private static final Map<String, Set<Integer>> COMPATIBLE = Map.of(
            "nexus", Set.of(3, 4));

    private ObserverOwnedScreenProtocols() { }

    /** Legacy/synthetic fixture version. Real owned-screen relays use the provider-advertised snapshot version. */
    public static int expected(String familyId) {
        return EXPECTED.getOrDefault(familyId, 0);
    }

    public static Set<Integer> supported(String familyId) {
        int expected = expected(familyId);
        if (expected <= 0) return Set.of();
        return COMPATIBLE.getOrDefault(familyId, Set.of(expected));
    }

    public static boolean accepts(String familyId, int screenProtocol) {
        return screenProtocol > 0 && supported(familyId).contains(screenProtocol);
    }
}
