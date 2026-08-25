package dev.totem.vanillatweaks.e2e;

import com.mojang.blaze3d.platform.NativeImage;
import dev.totem.vanillatweaks.client.ObserverAdvancementsScreenClient;
import dev.totem.vanillatweaks.client.ObserverNativeClient;
import dev.totem.vanillatweaks.client.ObserverNativeScreenClient;
import dev.totem.vanillatweaks.network.ObserverAdvancementsScreenPayloads;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Verifies Advancements semantic state across the dedicated-server + two-client Observer path. */
public final class ObserverAdvancementsE2eBridge implements ClientModInitializer {
    private static final Class<?> ADVANCEMENTS = ObserverAdvancementsScreenClient.class;
    private static final Class<?> GENERIC = ObserverNativeScreenClient.class;
    private static final Class<?> DRIVER = ObserverE2eClient.class;
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
        ClientTickEvents.END_CLIENT_TICK.register(ObserverAdvancementsE2eBridge::tick);
    }

    private static void tick(Minecraft minecraft) {
        if ("observer".equals(role)) tickObserver(minecraft);
        else if ("target".equals(role)) tickTarget();
    }

    private static void tickObserver(Minecraft minecraft) {
        if (!observerRequested
                && markerExists("observer-native-pause-screen-closed.txt")
                && markerExists("target-native-pause-screen-close-sent.txt")) {
            setBoolean(DRIVER, "observerStopRequested", true);
            observerRequested = true;
            ObserverE2eCommon.marker("observer-ready-for-advancements.txt",
                    "Target may send Advancements semantic state.\n");
        }

        if (observerRequested && !observerSeen && mirrorVisible(minecraft)) {
            if (!"minecraft:story/root".equals(getString(ADVANCEMENTS, "remoteSelectedRootId"))
                    || getList(ADVANCEMENTS, "remoteTabs").size() != 2
                    || getList(ADVANCEMENTS, "remoteNodes").size() != 3
                    || getDouble(ADVANCEMENTS, "remoteScrollX") != -14.0D
                    || getDouble(ADVANCEMENTS, "remoteScrollY") != 9.0D) {
                fail("Advancements E2E semantic state mismatch");
                return;
            }
            if (getBoolean(GENERIC, "remoteGenericOpen")) {
                fail("Generic semantic-adapter-pending mirror competed with Advancements semantic relay");
                return;
            }
            observerSeen = true;
            ObserverE2eCommon.marker("observer-native-advancements-ok.txt",
                    "Observer rendered Advancements semantic state.\n");
            Screenshot.takeScreenshot(minecraft.gameRenderer.mainRenderTarget(), ObserverAdvancementsE2eBridge::saveScreenshot);
        }

        if (observerSaved && !observerClosed && !getBoolean(ADVANCEMENTS, "remoteOpen")
                && minecraft.gui.screen() == null) {
            observerClosed = true;
            ObserverE2eCommon.marker("observer-native-advancements-closed.txt",
                    "Advancements semantic mirror closed.\n");
            setBoolean(DRIVER, "observerStopRequested", false);
        }
    }

    private static void tickTarget() {
        if (targetStage == 0 && markerExists("observer-ready-for-advancements.txt")) {
            if (!ObserverNativeClient.targetSupportsScreen(ObserverAdvancementsScreenPayloads.CAPABILITY)) {
                fail("Target did not negotiate Advancements semantic capability");
                return;
            }
            targetStage = 1;
            ClientPlayNetworking.send(openState());
            ObserverE2eCommon.marker("target-native-advancements-state-sent.txt",
                    "Target sent Advancements semantic state.\n");
        } else if (targetStage == 1 && markerExists("observer-native-advancements-saved.txt")) {
            targetStage = 2;
            ClientPlayNetworking.send(ObserverAdvancementsScreenPayloads.closed(++targetSequence));
            ObserverE2eCommon.marker("target-native-advancements-close-sent.txt",
                    "Target sent Advancements close state.\n");
        }
    }

    private static ObserverAdvancementsScreenPayloads.AdvancementsState openState() {
        return new ObserverAdvancementsScreenPayloads.AdvancementsState(
                ObserverAdvancementsScreenPayloads.PROTOCOL_VERSION, ++targetSequence, true,
                ObserverAdvancementsScreenPayloads.FAMILY_ID, ObserverAdvancementsScreenPayloads.SCREEN_CLASS,
                "Advancements", "minecraft:story/root", -14.0D, 9.0D,
                List.of(
                        new ObserverAdvancementsScreenPayloads.TabState("minecraft:story/root", "Minecraft", "minecraft:grass_block"),
                        new ObserverAdvancementsScreenPayloads.TabState("minecraft:adventure/root", "Adventure", "minecraft:map")
                ),
                List.of(
                        new ObserverAdvancementsScreenPayloads.NodeState(
                                "minecraft:story/root", "minecraft:story/root", "", "Minecraft", "The heart and story of the game",
                                "minecraft:grass_block", "task", 0.0F, 0.0F, 1.0F, true, false),
                        new ObserverAdvancementsScreenPayloads.NodeState(
                                "minecraft:story/mine_stone", "minecraft:story/root", "minecraft:story/root", "Stone Age", "Mine stone with your new pickaxe",
                                "minecraft:cobblestone", "task", 1.5F, 0.0F, 1.0F, true, false),
                        new ObserverAdvancementsScreenPayloads.NodeState(
                                "minecraft:story/upgrade_tools", "minecraft:story/root", "minecraft:story/mine_stone", "Getting an Upgrade", "Construct a better pickaxe",
                                "minecraft:stone_pickaxe", "task", 3.0F, 0.0F, 0.5F, false, false)
                ));
    }

    private static boolean mirrorVisible(Minecraft minecraft) {
        return minecraft.gui.screen() != null
                && minecraft.gui.screen().getClass().getName().contains("NativeAdvancementsMirrorScreen")
                && getBoolean(ADVANCEMENTS, "remoteOpen")
                && getLong(ADVANCEMENTS, "lastRemoteSequence") > 0L
                && getLong(ADVANCEMENTS, "extractedFrames") > 0L;
    }

    private static void saveScreenshot(NativeImage image) {
        if (image == null) { fail("Advancements screenshot callback returned null"); return; }
        try (NativeImage owned = image) {
            Path output = ObserverE2eCommon.resultsDir().resolve("observer-native-advancements.png");
            Files.createDirectories(output.getParent());
            owned.writeToFile(output);
            observerSaved = true;
            ObserverE2eCommon.marker("observer-native-advancements-saved.txt",
                    "Advancements semantic screenshot saved.\n");
        } catch (Exception error) {
            fail("Failed to save Advancements screenshot: " + error);
        }
    }

    private static boolean markerExists(String name) { return Files.isRegularFile(ObserverE2eCommon.resultsDir().resolve(name)); }
    private static void fail(String message) { ObserverE2eCommon.fail(role, message); }
    private static Field field(Class<?> owner, String name) {
        try { Field field = owner.getDeclaredField(name); field.setAccessible(true); return field; }
        catch (ReflectiveOperationException error) { throw new RuntimeException(error); }
    }
    private static boolean getBoolean(Class<?> owner, String name) { try { return field(owner, name).getBoolean(null); } catch (IllegalAccessException e) { throw new RuntimeException(e); } }
    private static long getLong(Class<?> owner, String name) { try { return field(owner, name).getLong(null); } catch (IllegalAccessException e) { throw new RuntimeException(e); } }
    private static double getDouble(Class<?> owner, String name) { try { return field(owner, name).getDouble(null); } catch (IllegalAccessException e) { throw new RuntimeException(e); } }
    private static String getString(Class<?> owner, String name) { try { return (String) field(owner, name).get(null); } catch (IllegalAccessException e) { throw new RuntimeException(e); } }
    private static List<?> getList(Class<?> owner, String name) { try { return (List<?>) field(owner, name).get(null); } catch (IllegalAccessException e) { throw new RuntimeException(e); } }
    private static void setBoolean(Class<?> owner, String name, boolean value) { try { field(owner, name).setBoolean(null, value); } catch (IllegalAccessException e) { throw new RuntimeException(e); } }
}
