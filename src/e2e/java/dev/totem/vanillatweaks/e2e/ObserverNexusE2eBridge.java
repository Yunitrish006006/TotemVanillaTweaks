package dev.totem.vanillatweaks.e2e;

import com.mojang.blaze3d.platform.NativeImage;
import dev.totem.vanillatweaks.client.ObserverNativeScreenClient;
import dev.totem.vanillatweaks.network.ObserverNativeScreenPayloads;
import dev.totem.vanillatweaks.network.ObserverNexusScreenPayloads;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

/** Runs map -> friends -> registration Nexus semantics across the real three-JVM Observer path. */
public final class ObserverNexusE2eBridge implements ClientModInitializer {
    private static final Class<?> GENERIC = ObserverNativeScreenClient.class;
    private static final Class<?> DRIVER = ObserverE2eClient.class;
    private static final UUID SOURCE = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID DESTINATION = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID FRIEND = UUID.fromString("33333333-3333-3333-3333-333333333333");

    private static String role;
    private static boolean observerRequested;
    private static boolean mapSeen;
    private static volatile boolean mapSaved;
    private static boolean friendsSeen;
    private static volatile boolean friendsSaved;
    private static boolean registrationSeen;
    private static volatile boolean registrationSaved;
    private static boolean observerClosed;
    private static RenderBarrier mapRenderBarrier;
    private static RenderBarrier friendsRenderBarrier;
    private static RenderBarrier registrationRenderBarrier;
    private static int targetStage;
    private static long targetSequence;

    @Override
    public void onInitializeClient() {
        if (!Boolean.getBoolean("totem.observer.e2e.enabled")) return;
        role = System.getProperty("totem.observer.e2e.role", "").trim();
        ClientTickEvents.END_CLIENT_TICK.register(ObserverNexusE2eBridge::tick);
    }

    private static void tick(Minecraft minecraft) {
        if ("observer".equals(role)) tickObserver(minecraft);
        else if ("target".equals(role)) tickTarget();
    }

    private static void tickObserver(Minecraft minecraft) {
        if (!observerRequested
                && markerExists("observer-native-automata-copper-golem-closed.txt")
                && markerExists("target-native-automata-copper-golem-close-sent.txt")) {
            setBoolean(DRIVER, "observerStopRequested", true);
            observerRequested = true;
            ObserverE2eCommon.marker("observer-ready-for-nexus-map.txt",
                    "Target may send Nexus map semantic state through the dedicated server.\n");
        }

        if (observerRequested && !mapSeen) {
            mapRenderBarrier = observeVariant(ObserverNexusScreenPayloads.VARIANT_MAP, mapRenderBarrier);
        }
        if (observerRequested && !mapSeen
                && observerScreenVisibleAfterRender(minecraft, ObserverNexusScreenPayloads.VARIANT_MAP, mapRenderBarrier)) {
            var payload = (dev.totem.nexus.network.SpaceUnitMapPayload) observerPayload(minecraft.gui.screen());
            if (!"Home v2".equals(payload.sourceName())) {
                fail("Nexus map production Screen did not apply the later snapshot");
                return;
            }
            ensureNoGenericFallback("map");
            mapSeen = true;
            ObserverE2eCommon.marker("observer-native-nexus-map-ok.txt",
                    "Observer rendered Nexus map semantic state.\n");
            saveScreenshot(minecraft, "observer-native-nexus-map.png", "observer-native-nexus-map-saved.txt", 1);
        }

        if (mapSaved && !friendsSeen) {
            friendsRenderBarrier = observeVariant(ObserverNexusScreenPayloads.VARIANT_FRIENDS, friendsRenderBarrier);
        }
        if (mapSaved && !friendsSeen
                && observerScreenVisibleAfterRender(minecraft, ObserverNexusScreenPayloads.VARIANT_FRIENDS,
                        friendsRenderBarrier)) {
            var payload = (dev.totem.nexus.network.SpaceUnitFriendsPayload) observerPayload(minecraft.gui.screen());
            if (payload.entries().size() != 1 || !"Friend v2".equals(payload.entries().getFirst().name())) {
                fail("Nexus friends production Screen did not apply the later snapshot");
                return;
            }
            ensureNoGenericFallback("friends");
            friendsSeen = true;
            ObserverE2eCommon.marker("observer-native-nexus-friends-ok.txt",
                    "Observer rendered Nexus friends semantic state.\n");
            saveScreenshot(minecraft, "observer-native-nexus-friends.png", "observer-native-nexus-friends-saved.txt", 2);
        }

        if (friendsSaved && !registrationSeen) {
            registrationRenderBarrier = observeVariant(
                    ObserverNexusScreenPayloads.VARIANT_REGISTRATION,
                    registrationRenderBarrier
            );
        }
        if (friendsSaved && !registrationSeen
                && observerScreenVisibleAfterRender(minecraft, ObserverNexusScreenPayloads.VARIANT_REGISTRATION,
                        registrationRenderBarrier)) {
            var payload = (dev.totem.nexus.network.SpaceUnitRegistrationPreviewPayload)
                    observerPayload(minecraft.gui.screen());
            if (payload.tier() != 4) {
                fail("Nexus registration production Screen did not apply the later snapshot");
                return;
            }
            ensureNoGenericFallback("registration");
            registrationSeen = true;
            ObserverE2eCommon.marker("observer-native-nexus-registration-ok.txt",
                    "Observer rendered Nexus registration semantic state.\n");
            saveScreenshot(minecraft, "observer-native-nexus-registration.png",
                    "observer-native-nexus-registration-saved.txt", 3);
        }

        if (registrationSaved && !observerClosed
                && !dev.totem.vanillatweaks.client.ObserverOwnedScreenCoordinator.isActive(
                "nexus", "registration", 1)
                && minecraft.gui.screen() == null) {
            observerClosed = true;
            ObserverE2eCommon.marker("observer-native-nexus-closed.txt",
                    "Nexus semantic view closed after Target close state.\n");
            setBoolean(DRIVER, "observerStopRequested", false);
        }
    }

    private static void tickTarget() {
        if (targetStage == 0 && markerExists("observer-ready-for-nexus-map.txt")) {
            if (!dev.totem.vanillatweaks.client.ObserverNativeClient.targetSupportsScreen(
                    ObserverNativeScreenPayloads.CAPABILITY_NEXUS)) {
                fail("Target did not negotiate Nexus semantic capability");
                return;
            }
            targetStage = 1;
            ClientPlayNetworking.send(ObserverOwnedE2eSnapshots.nexusMap(++targetSequence, "Home v1"));
            ClientPlayNetworking.send(ObserverOwnedE2eSnapshots.nexusMap(++targetSequence, "Home v2"));
            ClientPlayNetworking.send(ObserverOwnedE2eSnapshots.cursor("nexus", "map", 1L));
            ObserverE2eCommon.marker("target-native-nexus-map-state-sent.txt",
                    "Target sent Nexus map semantic state.\n");
        } else if (targetStage == 1 && markerExists("observer-native-nexus-map-saved.txt")) {
            targetStage = 2;
            ClientPlayNetworking.send(ObserverOwnedE2eSnapshots.nexusFriends(++targetSequence, "Friend v1"));
            ClientPlayNetworking.send(ObserverOwnedE2eSnapshots.nexusFriends(++targetSequence, "Friend v2"));
            ClientPlayNetworking.send(ObserverOwnedE2eSnapshots.cursor("nexus", "friends", 2L));
            ObserverE2eCommon.marker("target-native-nexus-friends-state-sent.txt",
                    "Target sent Nexus friends semantic state.\n");
        } else if (targetStage == 2 && markerExists("observer-native-nexus-friends-saved.txt")) {
            targetStage = 3;
            ClientPlayNetworking.send(ObserverOwnedE2eSnapshots.nexusRegistration(++targetSequence, 3));
            ClientPlayNetworking.send(ObserverOwnedE2eSnapshots.nexusRegistration(++targetSequence, 4));
            ClientPlayNetworking.send(ObserverOwnedE2eSnapshots.cursor("nexus", "registration", 3L));
            ObserverE2eCommon.marker("target-native-nexus-registration-state-sent.txt",
                    "Target sent Nexus registration semantic state.\n");
        } else if (targetStage == 3 && markerExists("observer-native-nexus-registration-saved.txt")) {
            targetStage = 4;
            ClientPlayNetworking.send(ObserverOwnedE2eSnapshots.close("nexus", "registration", ++targetSequence));
            ObserverE2eCommon.marker("target-native-nexus-close-sent.txt",
                    "Target sent Nexus semantic close state.\n");
        }
    }

    private static ObserverNexusScreenPayloads.NexusState mapState() {
        var source = new ObserverNexusScreenPayloads.MapEntryState(SOURCE, "Home Nexus", "local",
                "minecraft:overworld", "private", false, true, true, true, true, "",
                3, 0.91D, 0, 0, 0, 20, 0, 0, 0);
        var destination = new ObserverNexusScreenPayloads.MapEntryState(DESTINATION, "Mountain Relay", "remote",
                "minecraft:overworld", "friends", true, false, false, false, true, "",
                2, 0.73D, 1340, 5, 2, 60, 14, 3, 1);
        return new ObserverNexusScreenPayloads.NexusState(
                ObserverNexusScreenPayloads.PROTOCOL_VERSION, ++targetSequence, true,
                ObserverNativeScreenPayloads.FAMILY_NEXUS, ObserverNexusScreenPayloads.VARIANT_MAP,
                "dev.totem.nexus.client.NexusSpaceUnitMapScreen", "Nexus Map",
                SOURCE, "local", "Home Nexus", "minecraft:overworld", 10, 64, 10,
                "minecraft:overworld", DESTINATION, 0, 1.25D, "mountain", "all", "friends", "distance", true,
                List.of(source, destination), 0, List.of(), "", 0, 0, 0, 0, 0, 0, 0, 0);
    }

    private static ObserverNexusScreenPayloads.NexusState friendsState() {
        return new ObserverNexusScreenPayloads.NexusState(
                ObserverNexusScreenPayloads.PROTOCOL_VERSION, ++targetSequence, true,
                ObserverNativeScreenPayloads.FAMILY_NEXUS, ObserverNexusScreenPayloads.VARIANT_FRIENDS,
                "dev.totem.nexus.client.NexusSpaceUnitFriendsScreen", "Nexus Friends",
                null, "", "", "", 0, 0, 0, "", FRIEND, 0, 1.0D, "", "", "", "", false,
                List.of(), 0, List.of(
                        new ObserverNexusScreenPayloads.FriendEntryState(FRIEND, "SkySugarStar520", true, "friend"),
                        new ObserverNexusScreenPayloads.FriendEntryState(
                                UUID.fromString("44444444-4444-4444-4444-444444444444"), "Builder", false, "incoming")
                ), "", 0, 0, 0, 0, 0, 0, 0, 0);
    }

    private static ObserverNexusScreenPayloads.NexusState registrationState() {
        return new ObserverNexusScreenPayloads.NexusState(
                ObserverNexusScreenPayloads.PROTOCOL_VERSION, ++targetSequence, true,
                ObserverNativeScreenPayloads.FAMILY_NEXUS, ObserverNexusScreenPayloads.VARIANT_REGISTRATION,
                "dev.totem.nexus.client.NexusSpaceUnitRegistrationPreviewScreen", "Registration Preview",
                null, "", "", "", 0, 0, 0, "", null, 0, 1.0D, "", "", "", "", false,
                List.of(), 0, List.of(), "minecraft:overworld", 120, 72, -40, 3, 84, 92, 7, 20);
    }

    private static RenderBarrier observeVariant(String variant, RenderBarrier current) {
        if (!dev.totem.vanillatweaks.client.ObserverOwnedScreenCoordinator.isActive("nexus", variant, 2)) {
            return current;
        }
        long sequence = ObserverE2eSequenceEvidence.accepted(ObserverNativeScreenPayloads.FAMILY_NEXUS);
        if (sequence <= 0L || (current != null && current.sequence() == sequence)) {
            return current;
        }
        return new RenderBarrier(sequence,
                dev.totem.vanillatweaks.client.ObserverOwnedScreenCoordinator.activeSnapshotRenderBaseline());
    }

    private static boolean observerScreenVisibleAfterRender(
            Minecraft minecraft,
            String variant,
            RenderBarrier barrier
    ) {
        String expected = switch (variant) {
            case "map" -> "dev.totem.nexus.client.NexusSpaceUnitMapScreen";
            case "friends" -> "dev.totem.nexus.client.NexusSpaceUnitFriendsScreen";
            case "registration" -> "dev.totem.nexus.client.NexusSpaceUnitRegistrationPreviewScreen";
            default -> "";
        };
        return barrier != null && minecraft.gui.screen() != null
                && minecraft.gui.screen().getClass().getName().equals(expected)
                && minecraft.gui.screen() instanceof dev.totem.core.api.v1.client.observer.ObserverReadOnlyScreen
                && ObserverE2eSequenceEvidence.accepted(ObserverNativeScreenPayloads.FAMILY_NEXUS) == barrier.sequence()
                && dev.totem.vanillatweaks.client.ObserverOwnedScreenCoordinator.hasRemoteCursor()
                && dev.totem.vanillatweaks.client.ObserverOwnedScreenCoordinator.renderGeneration()
                > barrier.frameBaseline();
    }

    private static Object observerPayload(Object screen) {
        try {
            var method = screen.getClass().getDeclaredMethod("observerPayload");
            method.setAccessible(true);
            return method.invoke(screen);
        } catch (ReflectiveOperationException error) {
            throw new RuntimeException("Nexus production Screen did not expose Observer payload", error);
        }
    }

    private static void ensureNoGenericFallback(String variant) {
        if (getBoolean(GENERIC, "remoteGenericOpen")) {
            fail("Metadata fallback competed with Nexus " + variant + " semantic relay");
        }
    }

    private static void saveScreenshot(Minecraft minecraft, String name, String marker, int stage) {
        Screenshot.takeScreenshot(minecraft.gameRenderer.mainRenderTarget(), image -> {
            if (image == null) {
                fail("Nexus screenshot callback returned null for " + name);
                return;
            }
            try (NativeImage owned = image) {
                Path output = ObserverE2eCommon.resultsDir().resolve(name);
                Files.createDirectories(output.getParent());
                owned.writeToFile(output);
                if (stage == 1) mapSaved = true;
                else if (stage == 2) friendsSaved = true;
                else if (stage == 3) registrationSaved = true;
                ObserverE2eCommon.marker(marker, "Nexus semantic screenshot saved locally.\n");
            } catch (Exception error) {
                fail("Failed to save Nexus E2E screenshot: " + error);
            }
        });
    }

    private static boolean markerExists(String name) {
        return Files.isRegularFile(ObserverE2eCommon.resultsDir().resolve(name));
    }

    private static void fail(String message) { ObserverE2eCommon.fail(role, message); }

    private static Field field(Class<?> owner, String name) {
        try {
            Field field = owner.getDeclaredField(name);
            field.setAccessible(true);
            return field;
        } catch (ReflectiveOperationException error) {
            throw new RuntimeException(error);
        }
    }
    private static boolean getBoolean(Class<?> owner, String name) {
        try { return field(owner, name).getBoolean(null); }
        catch (IllegalAccessException error) { throw new RuntimeException(error); }
    }
    private static int getInt(Class<?> owner, String name) {
        try { return field(owner, name).getInt(null); }
        catch (IllegalAccessException error) { throw new RuntimeException(error); }
    }
    private static long getLong(Class<?> owner, String name) {
        try { return field(owner, name).getLong(null); }
        catch (IllegalAccessException error) { throw new RuntimeException(error); }
    }
    private static Object getObject(Class<?> owner, String name) {
        try { return field(owner, name).get(null); }
        catch (IllegalAccessException error) { throw new RuntimeException(error); }
    }
    private static void setBoolean(Class<?> owner, String name, boolean value) {
        try { field(owner, name).setBoolean(null, value); }
        catch (IllegalAccessException error) { throw new RuntimeException(error); }
    }

    private record RenderBarrier(long sequence, long frameBaseline) {}
}
