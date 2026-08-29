package dev.totem.vanillatweaks.client;

import dev.totem.vanillatweaks.network.ObserverPayloads;
import org.junit.jupiter.api.Test;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ObserverMetadataScreenPolicyTest {
    @Test
    void deathScreenIsMetadataOnlyByDesign() {
        ObserverMetadataScreenPolicy.Presentation presentation = ObserverMetadataScreenPolicy.classify(
                "net.minecraft.client.gui.screens.DeathScreen"
        );

        assertEquals(ObserverMetadataScreenPolicy.Classification.METADATA_ONLY_BY_DESIGN,
                presentation.classification());
        assertEquals("Metadata only by design.", presentation.statusLine());
        assertTrue(presentation.detailLine().contains("target client"));
    }

    @Test
    void privateTextScreensExplainThatTextIsNotTransmitted() {
        for (String screenClass : List.of(
                "net.minecraft.client.gui.screens.ChatScreen",
                "net.minecraft.client.gui.screens.InBedChatScreen",
                "net.minecraft.client.gui.screens.social.SocialInteractionsScreen",
                "dev.totem.discord.client.DiscordConfigScreen",
                "dev.totem.nexus.client.NexusSpaceUnitMapScreen$RenameSpaceUnitScreen",
                "dev.totem.nexus.client.NexusSpaceUnitMapScreen$AccessSpaceUnitScreen"
        )) {
            ObserverMetadataScreenPolicy.Presentation presentation =
                    ObserverMetadataScreenPolicy.classify(screenClass);

            assertEquals(ObserverMetadataScreenPolicy.Classification.METADATA_ONLY_BY_DESIGN,
                    presentation.classification());
            assertEquals("Metadata only by design.", presentation.statusLine());
            assertEquals("Unsent and private text is not transmitted.", presentation.detailLine());
        }
        assertEquals("Private configuration screen", ObserverMetadataScreenPolicy.safeTitle(
                "dev.totem.discord.client.DiscordConfigScreen", "https://secret.example/token"));
        assertEquals("Private configuration screen", ObserverMetadataScreenPolicy.safeTitle(
                "dev.totem.nexus.client.NexusSpaceUnitMapScreen$RenameSpaceUnitScreen", "private draft"));
    }

    @Test
    void unknownScreensStillReportPendingSemanticAdapter() {
        for (String screenClass : List.of(
                "com.example.UnknownScreen",
                "net.minecraft.client.gui.screens.inventory.InventoryScreen",
                ""
        )) {
            ObserverMetadataScreenPolicy.Presentation presentation =
                    ObserverMetadataScreenPolicy.classify(screenClass);

            assertEquals(ObserverMetadataScreenPolicy.Classification.SEMANTIC_ADAPTER_PENDING,
                    presentation.classification());
            assertEquals("Semantic adapter pending.", presentation.statusLine());
            assertEquals("", presentation.detailLine());
        }
        assertEquals(ObserverMetadataScreenPolicy.Classification.SEMANTIC_ADAPTER_PENDING,
                ObserverMetadataScreenPolicy.classify(null).classification());
    }

    @Test
    void compatibilityMetadataPayloadHasNoInputOrPrivateTextField() {
        assertEquals(List.of("open", "screenClass", "title"),
                recordComponentNames(ObserverPayloads.ScreenState.class));
        assertEquals(List.of("targetId", "open", "screenClass", "title"),
                recordComponentNames(ObserverPayloads.ScreenRelay.class));
    }

    private static List<String> recordComponentNames(Class<?> type) {
        return Arrays.stream(type.getRecordComponents()).map(RecordComponent::getName).toList();
    }
}
