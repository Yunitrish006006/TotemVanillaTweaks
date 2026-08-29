package dev.totem.vanillatweaks.gametest;

import dev.totem.core.api.v1.client.observer.ObserverReadOnlyScreen;
import dev.totem.core.api.v1.client.observer.ObserverScreenSnapshot;
import dev.totem.vanillatweaks.client.ObserverNativeClient;
import dev.totem.vanillatweaks.client.ObserverOwnedScreenCoordinator;
import dev.totem.vanillatweaks.network.ObserverNativePayloads;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.network.chat.Component;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Standard-classpath proof: absent owning modules yield explicit metadata, never a lookalike. */
final class ObserverAbsentOwnerProviderAssertions {
    private ObserverAbsentOwnerProviderAssertions() { }

    static boolean verify(ClientGameTestContext context, String family, String productionClass) {
        try {
            Class.forName(productionClass, false, ObserverAbsentOwnerProviderAssertions.class.getClassLoader());
            throw new AssertionError("Owning module unexpectedly present in the standard Client GameTest: " + family);
        } catch (ClassNotFoundException expected) {
            // This gate deliberately exercises the module-absent contract.
        }
        try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
            context.waitTicks(2);
            singleplayer.getClientLevel().waitForChunksRender();
            UUID targetId = UUID.randomUUID();
            context.runOnClient(minecraft -> {
                applySession(true, targetId);
                ObserverOwnedScreenCoordinator.open(new ObserverScreenSnapshot(family, "", 1, 1L,
                        Component.literal("Unsupported owner provider"), List.of(), new int[0], Map.of(), new byte[0]));
            });
            context.waitFor(minecraft -> minecraft.gui.screen() instanceof ObserverReadOnlyScreen
                    && minecraft.gui.screen().getClass().getSimpleName().equals("ObserverMetadataScreen"), 100);
            context.waitTicks(2);
            for (String name : screenshotNames(family)) {
                persistForCi(context.takeScreenshot(name), name + ".png");
            }
            context.runOnClient(minecraft -> {
                ObserverOwnedScreenCoordinator.close(family);
                applySession(false, new UUID(0L, 0L));
            });
            context.waitForScreen(null);
        }
        return true;
    }

    private static List<String> screenshotNames(String family) {
        return switch (family) {
            case "remnant_backpack" -> List.of("owner-absent-remnant-backpack-unsupported");
            case "automata_copper_golem" -> List.of("owner-absent-automata-copper-golem-unsupported");
            case "nexus" -> List.of("owner-absent-nexus-map-unsupported",
                    "owner-absent-nexus-friends-unsupported", "owner-absent-nexus-registration-unsupported");
            case "villagers_woodcutter" -> List.of("owner-absent-villagers-woodcutter-unsupported");
            case "nexus_death_node_admin" -> List.of("owner-absent-nexus-death-node-admin-unsupported");
            case "locksmith_management" -> List.of("owner-absent-locksmith-management-unsupported");
            default -> throw new IllegalArgumentException("Unknown owner family: " + family);
        };
    }

    private static void persistForCi(Path screenshot, String fileName) {
        String workspace = System.getenv("GITHUB_WORKSPACE");
        if (workspace == null || workspace.isBlank()) return;
        try {
            Path dir = Path.of(workspace).resolve("build/client-gametest-screenshots");
            Files.createDirectories(dir);
            Files.copy(screenshot, dir.resolve(fileName), StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception error) {
            throw new RuntimeException(error);
        }
    }

    private static void applySession(boolean active, UUID targetId) {
        try {
            Method method = ObserverNativeClient.class.getDeclaredMethod("applySession", ObserverNativePayloads.NativeSession.class);
            method.setAccessible(true);
            method.invoke(null, new ObserverNativePayloads.NativeSession(active, targetId,
                    active ? "UnsupportedOwnerTarget" : "", ObserverNativePayloads.PROTOCOL_VERSION, -1L));
        } catch (ReflectiveOperationException error) {
            throw new RuntimeException(error);
        }
    }
}
