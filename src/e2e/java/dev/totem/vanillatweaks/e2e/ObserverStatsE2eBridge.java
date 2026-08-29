package dev.totem.vanillatweaks.e2e;

import com.mojang.blaze3d.platform.NativeImage;
import dev.totem.vanillatweaks.client.ObserverNativeClient;
import dev.totem.vanillatweaks.client.ObserverNativeScreenClient;
import dev.totem.vanillatweaks.client.ObserverStatsScreenClient;
import dev.totem.vanillatweaks.network.ObserverStatsScreenPayloads;
import dev.totem.vanillatweaks.mixin.client.StatsScreenAccessor;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import dev.totem.core.api.v1.client.observer.ObserverReadOnlyScreen;
import net.minecraft.client.gui.screens.achievement.StatsScreen;
import net.minecraft.client.Screenshot;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Verifies Statistics semantics across the dedicated-server + two-client Observer path. */
public final class ObserverStatsE2eBridge implements ClientModInitializer {
    private static final Class<?> STATS = ObserverStatsScreenClient.class;
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
        ClientTickEvents.END_CLIENT_TICK.register(ObserverStatsE2eBridge::tick);
    }

    private static void tick(Minecraft minecraft) {
        if ("observer".equals(role)) tickObserver(minecraft);
        else if ("target".equals(role)) tickTarget();
    }

    private static void tickObserver(Minecraft minecraft) {
        if (!observerRequested
                && markerExists("observer-native-advancements-closed.txt")
                && markerExists("target-native-advancements-close-sent.txt")) {
            setBoolean(DRIVER, "observerStopRequested", true);
            observerRequested = true;
            ObserverE2eCommon.marker("observer-ready-for-stats.txt", "Target may send Statistics semantic state.\n");
        }

        if (observerRequested && !observerSeen && mirrorVisible(minecraft)) {
            if (!"items".equals(getString(STATS, "remoteActiveTab"))
                    || getDouble(STATS, "remoteScrollAmount") != 20.0D
                    || !"used".equals(getString(STATS, "remoteItemSortColumn"))
                    || getInt(STATS, "remoteItemSortOrder") != -1
                    || getList(STATS, "remoteItemRows").size() != 3) {
                fail("Statistics E2E semantic state mismatch");
                return;
            }
            if (getBoolean(GENERIC, "remoteGenericOpen")) {
                fail("Generic semantic-adapter-pending metadata screen competed with Statistics semantic relay");
                return;
            }
            observerSeen = true;
            ObserverE2eCommon.marker("observer-native-stats-ok.txt", "Observer rendered Statistics semantic state.\n");
            Screenshot.takeScreenshot(minecraft.gameRenderer.mainRenderTarget(), ObserverStatsE2eBridge::saveScreenshot);
        }

        if (observerSaved && !observerClosed && !getBoolean(STATS, "remoteOpen") && minecraft.gui.screen() == null) {
            observerClosed = true;
            ObserverE2eCommon.marker("observer-native-stats-closed.txt", "Statistics semantic view closed.\n");
        }
    }

    private static void tickTarget() {
        if (targetStage == 0 && markerExists("observer-ready-for-stats.txt")) {
            if (!ObserverNativeClient.targetSupportsScreen(ObserverStatsScreenPayloads.CAPABILITY)) {
                fail("Target did not negotiate Statistics semantic capability");
                return;
            }
            targetStage = 1;
            ClientPlayNetworking.send(openState());
            ObserverE2eCommon.marker("target-native-stats-state-sent.txt", "Target sent Statistics semantic state.\n");
        } else if (targetStage == 1 && markerExists("observer-native-stats-saved.txt")) {
            targetStage = 2;
            ClientPlayNetworking.send(ObserverStatsScreenPayloads.closed(++targetSequence));
            ObserverE2eCommon.marker("target-native-stats-close-sent.txt", "Target sent Statistics close state.\n");
        }
    }

    private static ObserverStatsScreenPayloads.StatsState openState() {
        return new ObserverStatsScreenPayloads.StatsState(
                ObserverStatsScreenPayloads.PROTOCOL_VERSION, ++targetSequence, true,
                ObserverStatsScreenPayloads.FAMILY_ID, ObserverStatsScreenPayloads.SCREEN_CLASS,
                "Statistics", "items", false, 20.0D, "used", -1,
                List.of(),
                List.of(
                        new ObserverStatsScreenPayloads.ItemRow("minecraft:stone_pickaxe", 0, 4, 1, 125, 2, 0),
                        new ObserverStatsScreenPayloads.ItemRow("minecraft:torch", 0, 0, 64, 48, 10, 1),
                        new ObserverStatsScreenPayloads.ItemRow("minecraft:oak_log", 37, 0, 0, 12, 20, 3)
                ),
                List.of());
    }

    private static boolean mirrorVisible(Minecraft minecraft) {
        return minecraft.gui.screen() != null
                && minecraft.gui.screen() instanceof StatsScreen
                && minecraft.gui.screen() instanceof ObserverReadOnlyScreen
                && !((StatsScreenAccessor) (Object) minecraft.gui.screen()).totem$isLoading()
                && getBoolean(STATS, "remoteOpen")
                && ObserverE2eSequenceEvidence.accepted(ObserverStatsScreenPayloads.FAMILY_ID) > 0L
                && getLong(STATS, "extractedFrames") > 0L
                && ObserverE2eRenderBarrier.passed(ObserverStatsScreenPayloads.FAMILY_ID,
                        getLong(STATS, "extractedFrames"));
    }

    private static void saveScreenshot(NativeImage image) {
        if (image == null) { fail("Statistics screenshot callback returned null"); return; }
        try (NativeImage owned = image) {
            Path output = ObserverE2eCommon.resultsDir().resolve("observer-native-stats.png");
            Files.createDirectories(output.getParent());
            owned.writeToFile(output);
            observerSaved = true;
            ObserverE2eCommon.marker("observer-native-stats-saved.txt", "Statistics semantic screenshot saved.\n");
        } catch (Exception error) {
            fail("Failed to save Statistics screenshot: " + error);
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
    private static int getInt(Class<?> owner, String name) { try { return field(owner, name).getInt(null); } catch (IllegalAccessException e) { throw new RuntimeException(e); } }
    private static double getDouble(Class<?> owner, String name) { try { return field(owner, name).getDouble(null); } catch (IllegalAccessException e) { throw new RuntimeException(e); } }
    private static String getString(Class<?> owner, String name) { try { return (String) field(owner, name).get(null); } catch (IllegalAccessException e) { throw new RuntimeException(e); } }
    private static List<?> getList(Class<?> owner, String name) { try { return (List<?>) field(owner, name).get(null); } catch (IllegalAccessException e) { throw new RuntimeException(e); } }
    private static void setBoolean(Class<?> owner, String name, boolean value) { try { field(owner, name).setBoolean(null, value); } catch (IllegalAccessException e) { throw new RuntimeException(e); } }
}
