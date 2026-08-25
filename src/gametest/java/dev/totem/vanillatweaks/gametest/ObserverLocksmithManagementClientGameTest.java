package dev.totem.vanillatweaks.gametest;

import dev.totem.vanillatweaks.client.ObserverLocksmithManagementScreenClient;
import dev.totem.vanillatweaks.client.ObserverNativeClient;
import dev.totem.vanillatweaks.network.ObserverLocksmithManagementPayloads;
import dev.totem.vanillatweaks.network.ObserverNativePayloads;
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

/** Client runtime proof for TotemLocksmith management semantic reconstruction. */
public final class ObserverLocksmithManagementClientGameTest implements FabricClientGameTest {
    private static final UUID LOCK_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private static final UUID MEMBER_A = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID MEMBER_B = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final UUID CANDIDATE_A = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
    private static final UUID KEY_A = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final UUID KEY_B = UUID.fromString("66666666-7777-8888-9999-aaaaaaaaaaaa");

    @Override
    public void runTest(ClientGameTestContext context) {
        try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
            context.waitTicks(2);
            singleplayer.getClientLevel().waitForChunksRender();
            UUID targetId = UUID.randomUUID();
            context.runOnClient(minecraft -> {
                applySession(true, targetId, ObserverLocksmithManagementPayloads.CAPABILITY);
                accept(ObserverLocksmithManagementPayloads.relay(targetId, openState(1L)));
            });
            context.waitFor(minecraft -> minecraft.gui.screen() != null
                    && minecraft.gui.screen().getClass().getName().contains("NativeLocksmithManagementMirrorScreen"), 100);
            context.waitFor(minecraft -> getLong("extractedFrames") > 0L, 100);
            if (!getBoolean("remoteOwnerActor")
                    || getBoolean("remoteManagerActor")
                    || !getBoolean("remotePhysicalKeysRequired")
                    || getInt("remoteAccessModeOrdinal") != 1
                    || getInt("remoteAutomationModeOrdinal") != 1
                    || getInt("remoteLogicalContainerCount") != 6
                    || getInt("remoteConnectorCount") != 2
                    || !"members".equals(getString("remoteTab"))
                    || getListSize("remoteMembers") != 2
                    || getListSize("remoteKeys") != 2
                    || getListSize("remoteCandidates") != 1
                    || !LOCK_ID.equals(getUuid("remoteLockId"))) {
                throw new AssertionError("Locksmith management semantic state was not reconstructed correctly");
            }
            persistForCi(context.takeScreenshot("observer-ui-native-locksmith-management-screen"),
                    "observer-ui-native-locksmith-management-screen.png");
            context.runOnClient(minecraft -> {
                accept(ObserverLocksmithManagementPayloads.relay(targetId, ObserverLocksmithManagementPayloads.closed(2L)));
                applySession(false, new UUID(0L, 0L), 0L);
            });
            context.waitForScreen(null);
        }
    }

    private static ObserverLocksmithManagementPayloads.ManagementState openState(long sequence) {
        return new ObserverLocksmithManagementPayloads.ManagementState(
                ObserverLocksmithManagementPayloads.PROTOCOL_VERSION, sequence, true,
                ObserverLocksmithManagementPayloads.FAMILY_ID, ObserverLocksmithManagementPayloads.SCREEN_CLASS,
                "Locksmith Management", LOCK_ID, 17L, "Sky", true, false, true,
                1, 1, 6, 2, "members", 0, 0, 0,
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

    private static void accept(ObserverLocksmithManagementPayloads.ManagementRelay relay) {
        invoke(ObserverLocksmithManagementScreenClient.class, "acceptRelay",
                new Class<?>[]{ObserverLocksmithManagementPayloads.ManagementRelay.class}, relay);
    }

    private static void applySession(boolean active, UUID targetId, long capabilities) {
        invoke(ObserverNativeClient.class, "applySession", new Class<?>[]{ObserverNativePayloads.NativeSession.class},
                new ObserverNativePayloads.NativeSession(active, targetId, active ? "LockTarget" : "",
                        ObserverNativePayloads.PROTOCOL_VERSION, capabilities));
    }

    private static void invoke(Class<?> owner, String name, Class<?>[] types, Object... args) {
        try { Method method = owner.getDeclaredMethod(name, types); method.setAccessible(true); method.invoke(null, args); }
        catch (ReflectiveOperationException error) { throw new RuntimeException(error); }
    }

    private static Field field(String name) {
        try { Field field = ObserverLocksmithManagementScreenClient.class.getDeclaredField(name); field.setAccessible(true); return field; }
        catch (ReflectiveOperationException error) { throw new RuntimeException(error); }
    }
    private static boolean getBoolean(String name) { try { return field(name).getBoolean(null); } catch (IllegalAccessException e) { throw new RuntimeException(e); } }
    private static int getInt(String name) { try { return field(name).getInt(null); } catch (IllegalAccessException e) { throw new RuntimeException(e); } }
    private static long getLong(String name) { try { return field(name).getLong(null); } catch (IllegalAccessException e) { throw new RuntimeException(e); } }
    private static String getString(String name) { try { return (String) field(name).get(null); } catch (IllegalAccessException e) { throw new RuntimeException(e); } }
    private static UUID getUuid(String name) { try { return (UUID) field(name).get(null); } catch (IllegalAccessException e) { throw new RuntimeException(e); } }
    private static int getListSize(String name) { try { Object value = field(name).get(null); return value instanceof List<?> list ? list.size() : -1; } catch (IllegalAccessException e) { throw new RuntimeException(e); } }

    private static void persistForCi(Path screenshot, String fileName) {
        String workspace = System.getenv("GITHUB_WORKSPACE");
        if (workspace == null || workspace.isBlank()) return;
        try {
            Path dir = Path.of(workspace).resolve("build/client-gametest-screenshots");
            Files.createDirectories(dir);
            Files.copy(screenshot, dir.resolve(fileName), StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception error) { throw new RuntimeException(error); }
    }
}
