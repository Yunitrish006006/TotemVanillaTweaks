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

    public static String mapName(Screen screen) {
        return ((NexusSpaceUnitMapScreen) screen).observerPayload().sourceName();
    }

    public static int friendCount(Screen screen) {
        return ((NexusSpaceUnitFriendsScreen) screen).observerPayload().entries().size();
    }

    public static int registrationTier(Screen screen) {
        return ((NexusSpaceUnitRegistrationPreviewScreen) screen).observerPayload().tier();
    }

    public static int deathEntryCount(Screen screen) {
        return ((NexusDeathNodeAdminScreen) screen).observerPayload().entries().size();
    }
}
