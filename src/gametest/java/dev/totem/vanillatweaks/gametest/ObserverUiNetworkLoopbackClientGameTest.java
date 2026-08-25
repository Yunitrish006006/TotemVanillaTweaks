package dev.totem.vanillatweaks.gametest;

import dev.totem.vanillatweaks.client.ObserverNativeClient;
import dev.totem.vanillatweaks.client.ObserverUiClient;
import dev.totem.vanillatweaks.network.ObserverAdvancementsScreenPayloads;
import dev.totem.vanillatweaks.network.ObserverBeaconScreenPayloads;
import dev.totem.vanillatweaks.network.ObserverBrewingScreenPayloads;
import dev.totem.vanillatweaks.network.ObserverCartographyScreenPayloads;
import dev.totem.vanillatweaks.network.ObserverCrafterScreenPayloads;
import dev.totem.vanillatweaks.network.ObserverGrindstoneScreenPayloads;
import dev.totem.vanillatweaks.network.ObserverLocksmithManagementPayloads;
import dev.totem.vanillatweaks.network.ObserverLoomScreenPayloads;
import dev.totem.vanillatweaks.network.ObserverNativePayloads;
import dev.totem.vanillatweaks.network.ObserverNativeScreenPayloads;
import dev.totem.vanillatweaks.network.ObserverNexusDeathNodeAdminPayloads;
import dev.totem.vanillatweaks.network.ObserverPayloads;
import dev.totem.vanillatweaks.network.ObserverSignScreenPayloads;
import dev.totem.vanillatweaks.network.ObserverSmithingScreenPayloads;
import dev.totem.vanillatweaks.network.ObserverStatsScreenPayloads;
import dev.totem.vanillatweaks.network.ObserverStonecutterScreenPayloads;
import dev.totem.vanillatweaks.observer.ObserverNativeSessionManager;
import dev.totem.vanillatweaks.observer.ObserverSessionManager;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.UUID;

/** Runs protocol-v4 Observer state in both directions over a real integrated-server connection. */
public final class ObserverUiNetworkLoopbackClientGameTest implements FabricClientGameTest {
    private static final Class<?> NATIVE_CLIENT = ObserverNativeClient.class;

    @Override
    public void runTest(ClientGameTestContext context) {
        TestSingleplayerContext singleplayer = context.worldBuilder().create();
        UUID playerId = null;
        try {
            context.waitTicks(2);
            singleplayer.getClientLevel().waitForChunksRender();
            singleplayer.getServer().runCommand("gamemode spectator @a");

            playerId = singleplayer.getServer().computeOnServer(server -> {
                if (server.getPlayerList().getPlayers().size() != 1) throw new AssertionError("Loopback test expected exactly one connected player");
                ServerPlayer player = server.getPlayerList().getPlayers().get(0);
                if (!player.isSpectator()) throw new AssertionError("Loopback player did not enter spectator mode");

                assertCanSend(player, ObserverNativePayloads.NativeControl.TYPE, "NativeControl v4");
                assertCanSend(player, ObserverNativePayloads.NativeSession.TYPE, "NativeSession v4");
                assertCanSend(player, ObserverNativePayloads.NativeViewRelay.TYPE, "NativeViewRelay v4");
                assertCanSend(player, ObserverNativeScreenPayloads.ContainerRelay.TYPE, "ContainerRelay v2");
                assertCanSend(player, ObserverNativeScreenPayloads.FurnaceRelay.TYPE, "FurnaceRelay v1");
                assertCanSend(player, ObserverBrewingScreenPayloads.BrewingRelay.TYPE, "BrewingRelay v1");
                assertCanSend(player, ObserverSmithingScreenPayloads.SmithingRelay.TYPE, "SmithingRelay v1");
                assertCanSend(player, ObserverStonecutterScreenPayloads.StonecutterRelay.TYPE, "StonecutterRelay v1");
                assertCanSend(player, ObserverGrindstoneScreenPayloads.GrindstoneRelay.TYPE, "GrindstoneRelay v1");
                assertCanSend(player, ObserverLoomScreenPayloads.LoomRelay.TYPE, "LoomRelay v1");
                assertCanSend(player, ObserverCartographyScreenPayloads.CartographyRelay.TYPE, "CartographyRelay v1");
                assertCanSend(player, ObserverBeaconScreenPayloads.BeaconRelay.TYPE, "BeaconRelay v1");
                assertCanSend(player, ObserverSignScreenPayloads.SignRelay.TYPE, "SignRelay v1");
                assertCanSend(player, ObserverCrafterScreenPayloads.CrafterRelay.TYPE, "CrafterRelay v1");
                assertCanSend(player, ObserverNexusDeathNodeAdminPayloads.AdminRelay.TYPE, "NexusDeathNodeAdminRelay v1");
                assertCanSend(player, ObserverLocksmithManagementPayloads.ManagementRelay.TYPE, "LocksmithManagementRelay v1");
                assertCanSend(player, ObserverAdvancementsScreenPayloads.AdvancementsRelay.TYPE, "AdvancementsRelay v1");
                assertCanSend(player, ObserverStatsScreenPayloads.StatsRelay.TYPE, "StatsRelay v1");
                assertCanSend(player, ObserverPayloads.ScreenRelay.TYPE, "ScreenRelay");
                assertNoFramebufferPayloadTypes();

                UUID id = player.getUUID();
                mainTargetMap().put(id, id);
                if (!ObserverNativeSessionManager.start(player, player)) throw new AssertionError("Protocol-native loopback session negotiation failed");
                return id;
            });

            long expectedCapabilities = ObserverNativeScreenPayloads.KNOWN_CAPABILITIES
                    | ObserverBrewingScreenPayloads.CAPABILITY
                    | ObserverSmithingScreenPayloads.CAPABILITY
                    | ObserverStonecutterScreenPayloads.CAPABILITY
                    | ObserverGrindstoneScreenPayloads.CAPABILITY
                    | ObserverLoomScreenPayloads.CAPABILITY
                    | ObserverCartographyScreenPayloads.CAPABILITY
                    | ObserverBeaconScreenPayloads.CAPABILITY
                    | ObserverSignScreenPayloads.CAPABILITY
                    | ObserverCrafterScreenPayloads.CAPABILITY
                    | ObserverNexusDeathNodeAdminPayloads.CAPABILITY
                    | ObserverLocksmithManagementPayloads.CAPABILITY
                    | ObserverAdvancementsScreenPayloads.CAPABILITY
                    | ObserverStatsScreenPayloads.CAPABILITY;
            context.waitFor(minecraft -> nativeGetBoolean("observerSessionActive")
                    && nativeGetBoolean("targetStateEnabled")
                    && nativeGetInt("observerProtocolVersion") == ObserverNativePayloads.PROTOCOL_VERSION
                    && nativeGetInt("targetProtocolVersion") == ObserverNativePayloads.PROTOCOL_VERSION
                    && nativeGetLong("observerScreenCapabilities") == expectedCapabilities
                    && nativeGetLong("targetScreenCapabilities") == expectedCapabilities, 100);

            context.waitFor(minecraft -> nativeGetLong("nextTargetStateSequence") > 0L
                    && nativeGetLong("lastNativeStateSequence") > 0L, 200);

            if (hasField(ObserverUiClient.class, "captureEnabled") || hasField(ObserverUiClient.class, "nextFrameId")
                    || hasField(ObserverUiClient.class, "lastFrameId") || hasField(ObserverUiClient.class, "textureRegistered")) {
                throw new AssertionError("Framebuffer state unexpectedly exists on ObserverUiClient");
            }

            persistForCi(context.takeScreenshot("observer-ui-network-loopback"), "observer-ui-network-loopback.png");
            context.runOnClient(minecraft -> ClientPlayNetworking.send(new ObserverPayloads.Stop()));
            context.waitFor(minecraft -> !nativeGetBoolean("observerSessionActive"), 100);
            context.waitFor(minecraft -> !nativeGetBoolean("targetStateEnabled"), 100);
            context.waitFor(minecraft -> nativeGetLong("observerScreenCapabilities") == 0L
                    && nativeGetLong("targetScreenCapabilities") == 0L, 100);

            UUID expected = playerId;
            boolean serverCleanedUp = singleplayer.getServer().computeOnServer(server ->
                    !mainTargetMap().containsKey(expected) && !nativeTargetMap().containsKey(expected)
                            && !nativeScreenCapabilityMap().containsKey(expected));
            if (!serverCleanedUp) throw new AssertionError("Observer Stop did not clean native loopback session state");
        } finally {
            cleanupServer(singleplayer, playerId);
            singleplayer.close();
        }
    }

    private static void assertCanSend(ServerPlayer player,
                                      net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type<?> type,
                                      String name) {
        if (!ServerPlayNetworking.canSend(player, type)) throw new AssertionError("Connected client does not advertise Observer " + name + " support");
    }

    private static void assertNoFramebufferPayloadTypes() {
        for (Class<?> nested : ObserverPayloads.class.getDeclaredClasses()) {
            String name = nested.getSimpleName();
            if (name.equals("CaptureControl") || name.equals("Session") || name.equals("FrameChunk") || name.equals("FrameRelay"))
                throw new AssertionError("Removed framebuffer payload still exists: " + name);
        }
    }

    private static boolean hasField(Class<?> owner, String name) {
        try { owner.getDeclaredField(name); return true; } catch (NoSuchFieldException expected) { return false; }
    }

    private static void cleanupServer(TestSingleplayerContext singleplayer, UUID playerId) {
        if (playerId == null) return;
        try { singleplayer.getServer().runOnServer(server -> {
            mainTargetMap().remove(playerId); nativeTargetMap().remove(playerId); nativeScreenCapabilityMap().remove(playerId);
        }); } catch (Throwable ignored) {}
    }

    private static void persistForCi(Path screenshot, String fileName) {
        String workspace = System.getenv("GITHUB_WORKSPACE");
        if (workspace == null || workspace.isBlank()) return;
        try {
            Path destinationDir = Path.of(workspace).resolve("build/client-gametest-screenshots");
            Files.createDirectories(destinationDir);
            Files.copy(screenshot, destinationDir.resolve(fileName), StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception error) { throw new RuntimeException("Failed to persist client gametest screenshot " + screenshot, error); }
    }

    @SuppressWarnings("unchecked") private static Map<UUID, UUID> mainTargetMap() { return (Map<UUID, UUID>) getStatic(ObserverSessionManager.class, "TARGET_BY_OBSERVER"); }
    @SuppressWarnings("unchecked") private static Map<UUID, UUID> nativeTargetMap() { return (Map<UUID, UUID>) getStatic(ObserverNativeSessionManager.class, "TARGET_BY_OBSERVER"); }
    @SuppressWarnings("unchecked") private static Map<UUID, Long> nativeScreenCapabilityMap() { return (Map<UUID, Long>) getStatic(ObserverNativeSessionManager.class, "SCREEN_CAPABILITIES_BY_OBSERVER"); }
    private static Object getStatic(Class<?> owner, String name) {
        try { Field field = owner.getDeclaredField(name); field.setAccessible(true); return field.get(null); }
        catch (ReflectiveOperationException error) { throw new RuntimeException("Missing " + owner.getSimpleName() + " field: " + name, error); }
    }
    private static Field nativeField(String name) {
        try { Field field = NATIVE_CLIENT.getDeclaredField(name); field.setAccessible(true); return field; }
        catch (ReflectiveOperationException error) { throw new RuntimeException("Missing ObserverNativeClient field: " + name, error); }
    }
    private static boolean nativeGetBoolean(String name) { try { return nativeField(name).getBoolean(null); } catch (IllegalAccessException error) { throw new RuntimeException(error); } }
    private static int nativeGetInt(String name) { try { return nativeField(name).getInt(null); } catch (IllegalAccessException error) { throw new RuntimeException(error); } }
    private static long nativeGetLong(String name) { try { return nativeField(name).getLong(null); } catch (IllegalAccessException error) { throw new RuntimeException(error); } }
}
