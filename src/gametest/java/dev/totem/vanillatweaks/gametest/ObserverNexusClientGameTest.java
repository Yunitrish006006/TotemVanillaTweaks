package dev.totem.vanillatweaks.gametest;

import dev.totem.vanillatweaks.client.ObserverNativeClient;
import dev.totem.vanillatweaks.client.ObserverNexusScreenClient;
import dev.totem.vanillatweaks.network.ObserverNativePayloads;
import dev.totem.vanillatweaks.network.ObserverNativeScreenPayloads;
import dev.totem.vanillatweaks.network.ObserverNexusScreenPayloads;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.UUID;

/** Client runtime proof for map, friends and registration Nexus semantic variants. */
public final class ObserverNexusClientGameTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext context) {
        try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
            context.waitTicks(2);
            singleplayer.getClientLevel().waitForChunksRender();

            UUID targetId = UUID.randomUUID();
            UUID sourceId = UUID.randomUUID();
            UUID destinationId = UUID.randomUUID();
            UUID friendId = UUID.randomUUID();
            context.runOnClient(minecraft -> {
                applySession(true, targetId, ObserverNativeScreenPayloads.CAPABILITY_NEXUS);
                accept(ObserverNexusScreenPayloads.relay(targetId, mapState(1L, sourceId, destinationId)));
            });
            awaitVariant(context, ObserverNexusScreenPayloads.VARIANT_MAP, 0L);
            if (getListSize("remoteMapEntries") != 2 || !destinationId.equals(getObject("remoteSelectedId"))) {
                throw new AssertionError("Nexus map entries/selection were not reconstructed");
            }
            persistForCi(context.takeScreenshot("observer-ui-native-nexus-map-screen"),
                    "observer-ui-native-nexus-map-screen.png");
            long mapFrames = getLong("extractedFrames");

            context.runOnClient(minecraft -> accept(ObserverNexusScreenPayloads.relay(
                    targetId, friendsState(2L, friendId))));
            awaitVariant(context, ObserverNexusScreenPayloads.VARIANT_FRIENDS, mapFrames);
            if (getListSize("remoteFriendEntries") != 2 || !friendId.equals(getObject("remoteSelectedId"))) {
                throw new AssertionError("Nexus friends state was not reconstructed");
            }
            persistForCi(context.takeScreenshot("observer-ui-native-nexus-friends-screen"),
                    "observer-ui-native-nexus-friends-screen.png");
            long friendFrames = getLong("extractedFrames");

            context.runOnClient(minecraft -> accept(ObserverNexusScreenPayloads.relay(
                    targetId, registrationState(3L))));
            awaitVariant(context, ObserverNexusScreenPayloads.VARIANT_REGISTRATION, friendFrames);
            if (getInt("remoteRegistrationTier") != 3 || getInt("remoteResonancePercent") != 84
                    || getInt("remoteCompletenessPercent") != 92 || getInt("remoteWearPercent") != 7) {
                throw new AssertionError("Nexus registration state was not reconstructed");
            }
            persistForCi(context.takeScreenshot("observer-ui-native-nexus-registration-screen"),
                    "observer-ui-native-nexus-registration-screen.png");

            context.runOnClient(minecraft -> {
                accept(ObserverNexusScreenPayloads.relay(targetId, ObserverNexusScreenPayloads.closed(4L)));
                applySession(false, new UUID(0L, 0L), 0L);
            });
            context.waitForScreen(null);
        }
    }

    private static ObserverNexusScreenPayloads.NexusState mapState(long sequence, UUID sourceId, UUID destinationId) {
        var source = new ObserverNexusScreenPayloads.MapEntryState(sourceId, "Home Nexus", "local",
                "minecraft:overworld", "private", false, true, true, true, true, "",
                3, 0.91D, 0, 0, 0, 20, 0, 0, 0);
        var destination = new ObserverNexusScreenPayloads.MapEntryState(destinationId, "Mountain Relay", "remote",
                "minecraft:overworld", "friends", true, false, false, false, true, "",
                2, 0.73D, 1340, 5, 2, 60, 14, 3, 1);
        return new ObserverNexusScreenPayloads.NexusState(
                ObserverNexusScreenPayloads.PROTOCOL_VERSION, sequence, true,
                ObserverNativeScreenPayloads.FAMILY_NEXUS, ObserverNexusScreenPayloads.VARIANT_MAP,
                "dev.totem.nexus.client.NexusSpaceUnitMapScreen", "Nexus Map",
                sourceId, "local", "Home Nexus", "minecraft:overworld", 10, 64, 10,
                "minecraft:overworld", destinationId, 0, 1.25D, "mountain", "all", "friends", "distance", true,
                List.of(source, destination), 0, List.of(), "", 0, 0, 0, 0, 0, 0, 0, 0);
    }

    private static ObserverNexusScreenPayloads.NexusState friendsState(long sequence, UUID selectedId) {
        return new ObserverNexusScreenPayloads.NexusState(
                ObserverNexusScreenPayloads.PROTOCOL_VERSION, sequence, true,
                ObserverNativeScreenPayloads.FAMILY_NEXUS, ObserverNexusScreenPayloads.VARIANT_FRIENDS,
                "dev.totem.nexus.client.NexusSpaceUnitFriendsScreen", "Nexus Friends",
                null, "", "", "", 0, 0, 0, "", selectedId, 0, 1.0D, "", "", "", "", false,
                List.of(), 0, List.of(
                        new ObserverNexusScreenPayloads.FriendEntryState(selectedId, "SkySugarStar520", true, "friend"),
                        new ObserverNexusScreenPayloads.FriendEntryState(UUID.randomUUID(), "Builder", false, "incoming")
                ), "", 0, 0, 0, 0, 0, 0, 0, 0);
    }

    private static ObserverNexusScreenPayloads.NexusState registrationState(long sequence) {
        return new ObserverNexusScreenPayloads.NexusState(
                ObserverNexusScreenPayloads.PROTOCOL_VERSION, sequence, true,
                ObserverNativeScreenPayloads.FAMILY_NEXUS, ObserverNexusScreenPayloads.VARIANT_REGISTRATION,
                "dev.totem.nexus.client.NexusSpaceUnitRegistrationPreviewScreen", "Registration Preview",
                null, "", "", "", 0, 0, 0, "", null, 0, 1.0D, "", "", "", "", false,
                List.of(), 0, List.of(), "minecraft:overworld", 120, 72, -40, 3, 84, 92, 7, 20);
    }

    private static void awaitVariant(ClientGameTestContext context, String variant, long priorFrames) {
        context.waitFor(minecraft -> minecraft.gui.screen() != null
                && minecraft.gui.screen().getClass().getName().contains("NativeNexusMirrorScreen"), 100);
        context.waitFor(minecraft -> variant.equals(getString("remoteVariant"))
                && getLong("extractedFrames") > priorFrames, 100);
    }

    private static void accept(ObserverNexusScreenPayloads.NexusRelay relay) {
        invoke(ObserverNexusScreenClient.class, "acceptRelay",
                new Class<?>[]{ObserverNexusScreenPayloads.NexusRelay.class}, relay);
    }

    private static void applySession(boolean active, UUID targetId, long capabilities) {
        invoke(ObserverNativeClient.class, "applySession",
                new Class<?>[]{ObserverNativePayloads.NativeSession.class},
                new ObserverNativePayloads.NativeSession(active, targetId, active ? "NexusTarget" : "",
                        ObserverNativePayloads.PROTOCOL_VERSION, capabilities));
    }

    private static void invoke(Class<?> owner, String name, Class<?>[] types, Object... args) {
        try {
            Method method = owner.getDeclaredMethod(name, types);
            method.setAccessible(true);
            method.invoke(null, args);
        } catch (ReflectiveOperationException error) {
            throw new RuntimeException(error);
        }
    }

    private static Field field(String name) {
        try {
            Field field = ObserverNexusScreenClient.class.getDeclaredField(name);
            field.setAccessible(true);
            return field;
        } catch (ReflectiveOperationException error) {
            throw new RuntimeException(error);
        }
    }

    private static long getLong(String name) {
        try { return field(name).getLong(null); }
        catch (IllegalAccessException error) { throw new RuntimeException(error); }
    }

    private static int getInt(String name) {
        try { return field(name).getInt(null); }
        catch (IllegalAccessException error) { throw new RuntimeException(error); }
    }

    private static String getString(String name) {
        try { return String.valueOf(field(name).get(null)); }
        catch (IllegalAccessException error) { throw new RuntimeException(error); }
    }

    private static Object getObject(String name) {
        try { return field(name).get(null); }
        catch (IllegalAccessException error) { throw new RuntimeException(error); }
    }

    private static int getListSize(String name) {
        Object value = getObject(name);
        return value instanceof List<?> list ? list.size() : -1;
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
}
