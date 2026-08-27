package dev.totem.nexus.client;

import dev.totem.nexus.network.SpaceUnitFriendsPayload;
import dev.totem.nexus.network.SpaceUnitRegistrationPreviewPayload;
import net.minecraft.client.gui.screens.Screen;

/** Test-only constructor bridge for Nexus package-private production screens. */
public final class ObserverNexusIntegrationFixture {
    private ObserverNexusIntegrationFixture() {}

    public static Screen friends(SpaceUnitFriendsPayload payload) {
        return new NexusSpaceUnitFriendsScreen(null, payload);
    }

    public static Screen registration(SpaceUnitRegistrationPreviewPayload payload) {
        return new NexusSpaceUnitRegistrationPreviewScreen(payload);
    }
}
