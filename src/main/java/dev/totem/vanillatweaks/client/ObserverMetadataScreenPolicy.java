package dev.totem.vanillatweaks.client;

import java.util.Set;

/** Pure classification for screens that intentionally expose only redacted identity metadata. */
final class ObserverMetadataScreenPolicy {
    private static final String DEATH_SCREEN = "net.minecraft.client.gui.screens.DeathScreen";
    private static final Set<String> PRIVATE_TEXT_SCREENS = Set.of(
            "net.minecraft.client.gui.screens.ChatScreen",
            "net.minecraft.client.gui.screens.InBedChatScreen",
            "net.minecraft.client.gui.screens.social.SocialInteractionsScreen",
            "dev.totem.discord.client.DiscordConfigScreen",
            "dev.totem.nexus.client.NexusSpaceUnitMapScreen$RenameSpaceUnitScreen",
            "dev.totem.nexus.client.NexusSpaceUnitMapScreen$AccessSpaceUnitScreen"
    );

    private ObserverMetadataScreenPolicy() {
    }

    static Presentation classify(String screenClass) {
        String safeClass = screenClass == null ? "" : screenClass;
        if (DEATH_SCREEN.equals(safeClass)) {
            return new Presentation(
                    Classification.METADATA_ONLY_BY_DESIGN,
                    "Metadata only by design.",
                    "Death-screen details stay on the target client."
            );
        }
        if (PRIVATE_TEXT_SCREENS.contains(safeClass)) {
            return new Presentation(
                    Classification.METADATA_ONLY_BY_DESIGN,
                    "Metadata only by design.",
                    "Unsent and private text is not transmitted."
            );
        }
        return new Presentation(
                Classification.SEMANTIC_ADAPTER_PENDING,
                "Semantic adapter pending.",
                ""
        );
    }

    static String safeTitle(String screenClass, String title) {
        return PRIVATE_TEXT_SCREENS.contains(screenClass == null ? "" : screenClass)
                ? "Private configuration screen"
                : title == null ? "" : title;
    }

    enum Classification {
        METADATA_ONLY_BY_DESIGN,
        SEMANTIC_ADAPTER_PENDING
    }

    record Presentation(Classification classification, String statusLine, String detailLine) {
    }
}
