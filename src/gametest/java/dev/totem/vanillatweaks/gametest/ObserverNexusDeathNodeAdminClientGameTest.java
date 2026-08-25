package dev.totem.vanillatweaks.gametest;

import dev.totem.vanillatweaks.client.ObserverNativeClient;
import dev.totem.vanillatweaks.client.ObserverNexusDeathNodeAdminScreenClient;
import dev.totem.vanillatweaks.network.ObserverNativePayloads;
import dev.totem.vanillatweaks.network.ObserverNexusDeathNodeAdminPayloads;
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

/** Client runtime proof for TotemNexus death-node administration semantic reconstruction. */
public final class ObserverNexusDeathNodeAdminClientGameTest implements FabricClientGameTest {
    private static final UUID NODE_A = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID NODE_B = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID NODE_C = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID OWNER_A = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID OWNER_B = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");

    @Override
    public void runTest(ClientGameTestContext context) {
        try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
            context.waitTicks(2);
            singleplayer.getClientLevel().waitForChunksRender();
            UUID targetId = UUID.randomUUID();
            context.runOnClient(minecraft -> {
                applySession(true, targetId, ObserverNexusDeathNodeAdminPayloads.CAPABILITY);
                accept(ObserverNexusDeathNodeAdminPayloads.relay(targetId, openState(1L)));
            });
            context.waitFor(minecraft -> minecraft.gui.screen() != null
                    && minecraft.gui.screen().getClass().getName().contains("NativeDeathNodeAdminMirrorScreen"), 100);
            context.waitFor(minecraft -> getLong("extractedFrames") > 0L, 100);
            if (!getBoolean("remoteAdministratorView")
                    || !getBoolean("remoteTruncated")
                    || !getBoolean("remoteConfirmationActive")
                    || getInt("remotePage") != 1
                    || getInt("remoteTotalEntries") != 42
                    || getInt("remoteScrollIndex") != 1
                    || !"Steve".equals(getString("remoteOwnerQuery"))
                    || !"minecraft:overworld".equals(getString("remoteDimensionQuery"))
                    || !"active".equals(getString("remoteStatusFilter"))
                    || !"recent".equals(getString("remoteTimeFilter"))
                    || !"purge".equals(getString("remoteConfirmationAction"))
                    || !NODE_B.equals(getUuid("remoteSelectedNodeId"))
                    || getListSize("remoteEntries") != 3) {
                throw new AssertionError("Nexus death-node admin semantic state was not reconstructed correctly");
            }
            persistForCi(context.takeScreenshot("observer-ui-native-nexus-death-node-admin-screen"),
                    "observer-ui-native-nexus-death-node-admin-screen.png");
            context.runOnClient(minecraft -> {
                accept(ObserverNexusDeathNodeAdminPayloads.relay(targetId, ObserverNexusDeathNodeAdminPayloads.closed(2L)));
                applySession(false, new UUID(0L, 0L), 0L);
            });
            context.waitForScreen(null);
        }
    }

    private static ObserverNexusDeathNodeAdminPayloads.AdminState openState(long sequence) {
        return new ObserverNexusDeathNodeAdminPayloads.AdminState(
                ObserverNexusDeathNodeAdminPayloads.PROTOCOL_VERSION, sequence, true,
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
                        "active", "minecraft:the_nether", 75, 64, 22, 900L, 1250L, List.of("cross_dimension"))
        );
    }

    private static void accept(ObserverNexusDeathNodeAdminPayloads.AdminRelay relay) {
        invoke(ObserverNexusDeathNodeAdminScreenClient.class, "acceptRelay",
                new Class<?>[]{ObserverNexusDeathNodeAdminPayloads.AdminRelay.class}, relay);
    }

    private static void applySession(boolean active, UUID targetId, long capabilities) {
        invoke(ObserverNativeClient.class, "applySession", new Class<?>[]{ObserverNativePayloads.NativeSession.class},
                new ObserverNativePayloads.NativeSession(active, targetId, active ? "AdminTarget" : "",
                        ObserverNativePayloads.PROTOCOL_VERSION, capabilities));
    }

    private static void invoke(Class<?> owner, String name, Class<?>[] types, Object... args) {
        try { Method method = owner.getDeclaredMethod(name, types); method.setAccessible(true); method.invoke(null, args); }
        catch (ReflectiveOperationException error) { throw new RuntimeException(error); }
    }

    private static Field field(String name) {
        try { Field field = ObserverNexusDeathNodeAdminScreenClient.class.getDeclaredField(name); field.setAccessible(true); return field; }
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
