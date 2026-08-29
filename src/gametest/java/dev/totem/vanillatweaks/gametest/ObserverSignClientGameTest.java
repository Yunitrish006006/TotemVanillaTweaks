package dev.totem.vanillatweaks.gametest;

import dev.totem.vanillatweaks.client.ObserverNativeClient;
import dev.totem.vanillatweaks.client.ObserverSignScreenClient;
import dev.totem.vanillatweaks.network.ObserverNativePayloads;
import dev.totem.vanillatweaks.network.ObserverSignScreenPayloads;
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

/** Client runtime proof for Sign semantic reconstruction. */
public final class ObserverSignClientGameTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext context) {
        try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
            context.waitTicks(2);
            singleplayer.getClientLevel().waitForChunksRender();
            UUID targetId = UUID.randomUUID();
            context.runOnClient(minecraft -> {
                applySession(true, targetId, ObserverSignScreenPayloads.CAPABILITY);
                accept(ObserverSignScreenPayloads.relay(targetId, openState(1L)));
            });
            context.waitFor(minecraft -> minecraft.gui.screen() != null
                    && minecraft.gui.screen().getClass().getName().contains("ObserverHangingSignScreen"), 100);
            context.waitFor(minecraft -> getLong("extractedFrames") > 0L, 100);
            if (!"hanging_sign".equals(getString("remoteVariant")) || getBoolean("remoteFrontText")
                    || getInt("remoteCurrentLine") != 2 || !"black".equals(getString("remoteColor"))
                    || !getBoolean("remoteGlowing") || getListSize("remoteLines") != 4) {
                throw new AssertionError("Sign semantic state was not reconstructed correctly");
            }
            persistForCi(context.takeScreenshot("observer-ui-native-sign-screen"),
                    "observer-ui-native-sign-screen.png");
            context.runOnClient(minecraft -> {
                accept(ObserverSignScreenPayloads.relay(targetId, ObserverSignScreenPayloads.closed(2L)));
                applySession(false, new UUID(0L, 0L), 0L);
            });
            context.waitForScreen(null);
            if (getLong("suppressedRemovalPackets") < 1L) {
                throw new AssertionError("Observer Sign removal did not prove sign-update packet suppression");
            }
        }
    }

    private static ObserverSignScreenPayloads.SignState openState(long sequence) {
        return new ObserverSignScreenPayloads.SignState(
                ObserverSignScreenPayloads.PROTOCOL_VERSION, sequence, true,
                ObserverSignScreenPayloads.FAMILY_ID, ObserverSignScreenPayloads.HANGING_SIGN_SCREEN_CLASS,
                "Edit Hanging Sign", "hanging_sign", false, 2, "black", true,
                List.of("Observer", "semantic", "sign editing", "works"));
    }

    private static void accept(ObserverSignScreenPayloads.SignRelay relay) {
        invoke(ObserverSignScreenClient.class, "acceptRelay",
                new Class<?>[]{ObserverSignScreenPayloads.SignRelay.class}, relay);
    }

    private static void applySession(boolean active, UUID targetId, long capabilities) {
        invoke(ObserverNativeClient.class, "applySession", new Class<?>[]{ObserverNativePayloads.NativeSession.class},
                new ObserverNativePayloads.NativeSession(active, targetId, active ? "SignTarget" : "",
                        ObserverNativePayloads.PROTOCOL_VERSION, capabilities));
    }

    private static void invoke(Class<?> owner, String name, Class<?>[] types, Object... args) {
        try { Method method = owner.getDeclaredMethod(name, types); method.setAccessible(true); method.invoke(null, args); }
        catch (ReflectiveOperationException error) { throw new RuntimeException(error); }
    }
    private static Field field(String name) {
        try { Field field = ObserverSignScreenClient.class.getDeclaredField(name); field.setAccessible(true); return field; }
        catch (ReflectiveOperationException error) { throw new RuntimeException(error); }
    }
    private static boolean getBoolean(String name) {
        try { return field(name).getBoolean(null); } catch (IllegalAccessException error) { throw new RuntimeException(error); }
    }
    private static int getInt(String name) {
        try { return field(name).getInt(null); } catch (IllegalAccessException error) { throw new RuntimeException(error); }
    }
    private static long getLong(String name) {
        try { return field(name).getLong(null); } catch (IllegalAccessException error) { throw new RuntimeException(error); }
    }
    private static String getString(String name) {
        try { return (String) field(name).get(null); } catch (IllegalAccessException error) { throw new RuntimeException(error); }
    }
    private static int getListSize(String name) {
        try { Object value = field(name).get(null); return value instanceof List<?> list ? list.size() : -1; }
        catch (IllegalAccessException error) { throw new RuntimeException(error); }
    }

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
