package dev.totem.vanillatweaks.e2e;

import com.mojang.blaze3d.platform.NativeImage;
import dev.totem.vanillatweaks.client.ObserverLocksmithManagementScreenClient;
import dev.totem.vanillatweaks.client.ObserverNativeScreenClient;
import dev.totem.vanillatweaks.network.ObserverLocksmithManagementPayloads;
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

/** Runs TotemLocksmith management semantics across the real three-JVM Observer path. */
public final class ObserverLocksmithManagementE2eBridge implements ClientModInitializer {
    private static final Class<?> LOCKSMITH = ObserverLocksmithManagementScreenClient.class;
    private static final Class<?> GENERIC = ObserverNativeScreenClient.class;
    private static final Class<?> DRIVER = ObserverE2eClient.class;
    private static final UUID LOCK_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private static final UUID MEMBER_A = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID MEMBER_B = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final UUID CANDIDATE_A = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
    private static final UUID KEY_A = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final UUID KEY_B = UUID.fromString("66666666-7777-8888-9999-aaaaaaaaaaaa");

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
        ClientTickEvents.END_CLIENT_TICK.register(ObserverLocksmithManagementE2eBridge::tick);
    }

    private static void tick(Minecraft minecraft) {
        if ("observer".equals(role)) tickObserver(minecraft);
        else if ("target".equals(role)) tickTarget();
    }

    private static void tickObserver(Minecraft minecraft) {
        if (!observerRequested
                && markerExists("observer-native-nexus-death-node-admin-closed.txt")
                && markerExists("target-native-nexus-death-node-admin-close-sent.txt")) {
            setBoolean(DRIVER, "observerStopRequested", true);
            observerRequested = true;
            ObserverE2eCommon.marker("observer-ready-for-locksmith-management.txt",
                    "Target may send TotemLocksmith management semantic state.\n");
        }

        if (observerRequested && !observerSeen && mirrorVisible(minecraft)) {
            if (!getBoolean(LOCKSMITH, "remoteOwnerActor")
                    || getBoolean(LOCKSMITH, "remoteManagerActor")
                    || !getBoolean(LOCKSMITH, "remotePhysicalKeysRequired")
                    || getInt(LOCKSMITH, "remoteAccessModeOrdinal") != 1
                    || getInt(LOCKSMITH, "remoteAutomationModeOrdinal") != 1
                    || getInt(LOCKSMITH, "remoteLogicalContainerCount") != 6
                    || getInt(LOCKSMITH, "remoteConnectorCount") != 2
                    || !"members".equals(getString(LOCKSMITH, "remoteTab"))
                    || !"Sky".equals(getString(LOCKSMITH, "remoteOwnerName"))
                    || !LOCK_ID.equals(getUuid(LOCKSMITH, "remoteLockId"))
                    || getListSize(LOCKSMITH, "remoteMembers") != 2
                    || getListSize(LOCKSMITH, "remoteKeys") != 2
                    || getListSize(LOCKSMITH, "remoteCandidates") != 1) {
                fail("Locksmith management E2E semantic state mismatch");
                return;
            }
            if (getBoolean(GENERIC, "remoteGenericOpen")) {
                fail("Generic container relay competed with Locksmith management semantic relay");
                return;
            }
            observerSeen = true;
            ObserverE2eCommon.marker("observer-native-locksmith-management-ok.txt",
                    "Observer rendered TotemLocksmith management semantic state.\n");
            Screenshot.takeScreenshot(minecraft.gameRenderer.mainRenderTarget(),
                    ObserverLocksmithManagementE2eBridge::saveScreenshot);
        }

        if (observerSaved && !observerClosed && !getBoolean(LOCKSMITH, "remoteOpen")
                && minecraft.gui.screen() == null) {
            observerClosed = true;
            ObserverE2eCommon.marker("observer-native-locksmith-management-closed.txt",
                    "TotemLocksmith management semantic mirror closed.\n");
            setBoolean(DRIVER, "observerStopRequested", false);
        }
    }

    private static void tickTarget() {
        if (targetStage == 0 && markerExists("observer-ready-for-locksmith-management.txt")) {
            if (!dev.totem.vanillatweaks.client.ObserverNativeClient.targetSupportsScreen(
                    ObserverLocksmithManagementPayloads.CAPABILITY)) {
                fail("Target did not negotiate TotemLocksmith management semantic capability");
                return;
            }
            targetStage = 1;
            ClientPlayNetworking.send(openState());
            ObserverE2eCommon.marker("target-native-locksmith-management-state-sent.txt",
                    "Target sent TotemLocksmith management semantic state.\n");
        } else if (targetStage == 1 && markerExists("observer-native-locksmith-management-saved.txt")) {
            targetStage = 2;
            ClientPlayNetworking.send(ObserverLocksmithManagementPayloads.closed(++targetSequence));
            ObserverE2eCommon.marker("target-native-locksmith-management-close-sent.txt",
                    "Target sent TotemLocksmith management close state.\n");
        }
    }

    private static ObserverLocksmithManagementPayloads.ManagementState openState() {
        return new ObserverLocksmithManagementPayloads.ManagementState(
                ObserverLocksmithManagementPayloads.PROTOCOL_VERSION,
                ++targetSequence,
                true,
                ObserverLocksmithManagementPayloads.FAMILY_ID,
                ObserverLocksmithManagementPayloads.SCREEN_CLASS,
                "Locksmith Management",
                LOCK_ID,
                17L,
                "Sky",
                true,
                false,
                true,
                1,
                1,
                6,
                2,
                "members",
                0,
                0,
                0,
                List.of(
                        new ObserverLocksmithManagementPayloads.MemberState(MEMBER_A, "Alex", 0),
                        new ObserverLocksmithManagementPayloads.MemberState(MEMBER_B, "Steve", 1)
                ),
                List.of(
                        new ObserverLocksmithManagementPayloads.KeyState(KEY_A, "Vault Key"),
                        new ObserverLocksmithManagementPayloads.KeyState(KEY_B, "Workshop Key")
                ),
                List.of(new ObserverLocksmithManagementPayloads.CandidateState(CANDIDATE_A, "Builder"))
        );
    }

    private static boolean mirrorVisible(Minecraft minecraft) {
        return minecraft.gui.screen() != null
                && minecraft.gui.screen().getClass().getName().contains("NativeLocksmithManagementMirrorScreen")
                && getBoolean(LOCKSMITH, "remoteOpen")
                && ObserverE2eSequenceEvidence.accepted(
                        ObserverLocksmithManagementPayloads.FAMILY_ID) > 0L
                && getLong(LOCKSMITH, "extractedFrames") > 0L;
    }

    private static void saveScreenshot(NativeImage image) {
        if (image == null) {
            fail("Locksmith management screenshot callback returned null");
            return;
        }
        try (NativeImage owned = image) {
            Path output = ObserverE2eCommon.resultsDir().resolve("observer-native-locksmith-management.png");
            Files.createDirectories(output.getParent());
            owned.writeToFile(output);
            observerSaved = true;
            ObserverE2eCommon.marker("observer-native-locksmith-management-saved.txt",
                    "TotemLocksmith management semantic screenshot saved.\n");
        } catch (Exception error) {
            fail("Failed to save TotemLocksmith management screenshot: " + error);
        }
    }

    private static boolean markerExists(String name) {
        return Files.isRegularFile(ObserverE2eCommon.resultsDir().resolve(name));
    }

    private static void fail(String message) {
        ObserverE2eCommon.fail(role, message);
    }

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

    private static String getString(Class<?> owner, String name) {
        try { return (String) field(owner, name).get(null); }
        catch (IllegalAccessException error) { throw new RuntimeException(error); }
    }

    private static UUID getUuid(Class<?> owner, String name) {
        try { return (UUID) field(owner, name).get(null); }
        catch (IllegalAccessException error) { throw new RuntimeException(error); }
    }

    private static int getListSize(Class<?> owner, String name) {
        try {
            Object value = field(owner, name).get(null);
            return value instanceof List<?> list ? list.size() : -1;
        } catch (IllegalAccessException error) {
            throw new RuntimeException(error);
        }
    }

    private static void setBoolean(Class<?> owner, String name, boolean value) {
        try { field(owner, name).setBoolean(null, value); }
        catch (IllegalAccessException error) { throw new RuntimeException(error); }
    }
}
