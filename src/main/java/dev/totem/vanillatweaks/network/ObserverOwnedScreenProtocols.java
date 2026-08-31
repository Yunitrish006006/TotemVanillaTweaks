package dev.totem.vanillatweaks.network;

import java.util.Map;

/** Server-controlled screen protocol registry, independent from the generic transport version. */
public final class ObserverOwnedScreenProtocols {
    private static final Map<String, Integer> EXPECTED = Map.of(
            "remnant_backpack", 1,
            "automata_copper_golem", 1,
            "nexus", 3,
            "nexus_death_node_admin", 1,
            "locksmith_management", 1,
            "villagers_woodcutter", 1);

    private ObserverOwnedScreenProtocols() { }

    public static int expected(String familyId) {
        return EXPECTED.getOrDefault(familyId, 0);
    }

    public static boolean accepts(String familyId, int screenProtocol) {
        return screenProtocol > 0 && expected(familyId) == screenProtocol;
    }
}
