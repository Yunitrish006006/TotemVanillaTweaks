package dev.totem.vanillatweaks.e2e;

import com.mojang.blaze3d.platform.NativeImage;
import dev.totem.vanillatweaks.client.ObserverNativeScreenClient;
import dev.totem.vanillatweaks.network.ObserverNexusDeathNodeAdminPayloads;
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

/** Runs TotemNexus death-node administration semantics across the real three-JVM Observer path. */
public final class ObserverNexusDeathNodeAdminE2eBridge implements ClientModInitializer {
    private static final Class<?> GENERIC = ObserverNativeScreenClient.class;
    private static final Class<?> DRIVER = ObserverE2eClient.class;
    private static final UUID NODE_A = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID NODE_B = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID NODE_C = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID OWNER_A = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID OWNER_B = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");

    private static String role;
    private static boolean observerRequested;
    private static boolean observerSeen;
    private static volatile boolean observerSaved;
    private static boolean observerClosed;
    private static int targetStage;
    private static long targetSequence;

    @Override
    public void onInitializeClient() {
        if (!Boolean.getBoolean("totem.observer.e2e.enabled")) return;
        role = System.getProperty("totem.observer.e2e.role", "").trim();
        ClientTickEvents.END_CLIENT_TICK.register(ObserverNexusDeathNodeAdminE2eBridge::tick);
    }

    private static void tick(Minecraft minecraft) {
        if ("observer".equals(role)) tickObserver(minecraft);
        else if ("target".equals(role)) tickTarget();
    }

    private static void tickObserver(Minecraft minecraft) {
        if (!observerRequested && markerExists("observer-native-crafter-closed.txt")
                && markerExists("target-native-crafter-close-sent.txt")) {
            setBoolean(DRIVER, "observerStopRequested", true);
            observerRequested = true;
            ObserverE2eCommon.marker("observer-ready-for-nexus-death-node-admin.txt",
                    "Target may send Nexus death-node admin semantic state.\n");
        }
        if (observerRequested && !observerSeen && mirrorVisible(minecraft)) {
            var payload = (dev.totem.nexus.network.DeathNodeAdminPayload) observerPayload(minecraft.gui.screen());
            if (payload.totalEntries() != 43 || !payload.administratorView()
                    || payload.confirmationNodeId() == null || payload.confirmationToken() == null
                    || !"purge".equals(payload.confirmationAction())) {
                fail("Nexus death-node admin production Screen did not apply the later snapshot");
                return;
            }
            if (getBoolean(GENERIC, "remoteGenericOpen")) {
                fail("Generic metadata relay competed with Nexus death-node admin semantic relay");
                return;
            }
            observerSeen = true;
            ObserverE2eCommon.marker("observer-native-nexus-death-node-admin-ok.txt",
                    "Observer rendered Nexus death-node admin semantic state.\n");
            Screenshot.takeScreenshot(minecraft.gameRenderer.mainRenderTarget(), ObserverNexusDeathNodeAdminE2eBridge::saveScreenshot);
        }
        if (observerSaved && !observerClosed
                && !dev.totem.vanillatweaks.client.ObserverOwnedScreenCoordinator.isActive(
                "nexus_death_node_admin", "", 1) && minecraft.gui.screen() == null) {
            observerClosed = true;
            ObserverE2eCommon.marker("observer-native-nexus-death-node-admin-closed.txt",
                    "Nexus death-node admin semantic view closed.\n");
            setBoolean(DRIVER, "observerStopRequested", false);
        }
    }

    private static void tickTarget() {
        if (targetStage == 0 && markerExists("observer-ready-for-nexus-death-node-admin.txt")) {
            if (!dev.totem.vanillatweaks.client.ObserverNativeClient.targetSupportsScreen(
                    ObserverNexusDeathNodeAdminPayloads.CAPABILITY)) {
                fail("Target did not negotiate Nexus death-node admin semantic capability");
                return;
            }
            targetStage = 1;
            ClientPlayNetworking.send(ObserverOwnedE2eSnapshots.deathAdmin(++targetSequence, 42));
            ClientPlayNetworking.send(ObserverOwnedE2eSnapshots.deathAdmin(++targetSequence, 43));
            ClientPlayNetworking.send(ObserverOwnedE2eSnapshots.cursor(
                    "nexus_death_node_admin", "", 1L));
            ObserverE2eCommon.marker("target-native-nexus-death-node-admin-state-sent.txt",
                    "Target sent Nexus death-node admin semantic state.\n");
        } else if (targetStage == 1 && markerExists("observer-native-nexus-death-node-admin-saved.txt")) {
            targetStage = 2;
            ClientPlayNetworking.send(ObserverOwnedE2eSnapshots.close(
                    "nexus_death_node_admin", "", ++targetSequence));
            ObserverE2eCommon.marker("target-native-nexus-death-node-admin-close-sent.txt",
                    "Target sent Nexus death-node admin close state.\n");
        }
    }

    private static ObserverNexusDeathNodeAdminPayloads.AdminState openState() {
        return new ObserverNexusDeathNodeAdminPayloads.AdminState(
                ObserverNexusDeathNodeAdminPayloads.PROTOCOL_VERSION, ++targetSequence, true,
                ObserverNexusDeathNodeAdminPayloads.FAMILY_ID, ObserverNexusDeathNodeAdminPayloads.SCREEN_CLASS,
                "Death Node Administration", "Steve", "minecraft:overworld", "active", "recent",
                1, 1, 20, 42, true, true, NODE_B, true, "purge", entries());
    }

    private static List<ObserverNexusDeathNodeAdminPayloads.EntryState> entries() {
        return List.of(
                new ObserverNexusDeathNodeAdminPayloads.EntryState(NODE_A, OWNER_A, "Steve", "Mine shaft",
                        "active", "minecraft:overworld", 120, 42, -360, 1000L, 1200L, List.of()),
                new ObserverNexusDeathNodeAdminPayloads.EntryState(NODE_B, OWNER_A, "Steve", "Ancient city",
                        "disabled", "minecraft:overworld", -840, -47, 510, 800L, 1100L,
                        List.of("stale_chunk", "low_support")),
                new ObserverNexusDeathNodeAdminPayloads.EntryState(NODE_C, OWNER_B, "Alex", "Nether hub",
                        "active", "minecraft:the_nether", 75, 64, 22, 900L, 1250L,
                        List.of("cross_dimension"))
        );
    }

    private static boolean mirrorVisible(Minecraft minecraft) {
        return minecraft.gui.screen() != null
                && minecraft.gui.screen().getClass().getName().equals("dev.totem.nexus.client.NexusDeathNodeAdminScreen")
                && minecraft.gui.screen() instanceof dev.totem.core.api.v1.client.observer.ObserverReadOnlyScreen
                && ObserverE2eSequenceEvidence.accepted(
                        ObserverNexusDeathNodeAdminPayloads.FAMILY_ID) > 0L
                && dev.totem.vanillatweaks.client.ObserverOwnedScreenCoordinator.hasRemoteCursor()
                && dev.totem.vanillatweaks.client.ObserverOwnedScreenCoordinator.hasRenderedActiveSnapshot();
    }

    private static Object observerPayload(Object screen) {
        try {
            var method = screen.getClass().getDeclaredMethod("observerPayload");
            method.setAccessible(true);
            return method.invoke(screen);
        } catch (ReflectiveOperationException error) {
            throw new RuntimeException("Death Admin production Screen did not expose Observer payload", error);
        }
    }

    private static void saveScreenshot(NativeImage image) {
        if (image == null) { fail("Nexus death-node admin screenshot callback returned null"); return; }
        try (NativeImage owned = image) {
            Path output = ObserverE2eCommon.resultsDir().resolve("observer-native-nexus-death-node-admin.png");
            Files.createDirectories(output.getParent());
            owned.writeToFile(output);
            observerSaved = true;
            ObserverE2eCommon.marker("observer-native-nexus-death-node-admin-saved.txt",
                    "Nexus death-node admin semantic screenshot saved.\n");
        } catch (Exception error) { fail("Failed to save Nexus death-node admin screenshot: " + error); }
    }

    private static boolean markerExists(String name) { return Files.isRegularFile(ObserverE2eCommon.resultsDir().resolve(name)); }
    private static void fail(String message) { ObserverE2eCommon.fail(role, message); }
    private static Field field(Class<?> owner, String name) {
        try { Field field = owner.getDeclaredField(name); field.setAccessible(true); return field; }
        catch (ReflectiveOperationException error) { throw new RuntimeException(error); }
    }
    private static boolean getBoolean(Class<?> owner, String name) { try { return field(owner, name).getBoolean(null); } catch (IllegalAccessException e) { throw new RuntimeException(e); } }
    private static int getInt(Class<?> owner, String name) { try { return field(owner, name).getInt(null); } catch (IllegalAccessException e) { throw new RuntimeException(e); } }
    private static long getLong(Class<?> owner, String name) { try { return field(owner, name).getLong(null); } catch (IllegalAccessException e) { throw new RuntimeException(e); } }
    private static String getString(Class<?> owner, String name) { try { return (String) field(owner, name).get(null); } catch (IllegalAccessException e) { throw new RuntimeException(e); } }
    private static UUID getUuid(Class<?> owner, String name) { try { return (UUID) field(owner, name).get(null); } catch (IllegalAccessException e) { throw new RuntimeException(e); } }
    private static int getListSize(Class<?> owner, String name) { try { Object value = field(owner, name).get(null); return value instanceof List<?> list ? list.size() : -1; } catch (IllegalAccessException e) { throw new RuntimeException(e); } }
    private static void setBoolean(Class<?> owner, String name, boolean value) { try { field(owner, name).setBoolean(null, value); } catch (IllegalAccessException e) { throw new RuntimeException(e); } }
}
