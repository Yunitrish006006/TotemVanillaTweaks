package dev.totem.vanillatweaks.e2e;

import com.mojang.blaze3d.platform.NativeImage;
import dev.totem.vanillatweaks.client.ObserverCrafterScreenClient;
import dev.totem.vanillatweaks.client.ObserverNativeScreenClient;
import dev.totem.vanillatweaks.network.ObserverCrafterScreenPayloads;
import dev.totem.vanillatweaks.network.ObserverNativeScreenPayloads;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Runs Crafter disabled-slot semantics across the dedicated-server + two-client Observer path. */
public final class ObserverCrafterE2eBridge implements ClientModInitializer {
    private static final Class<?> CRAFTER = ObserverCrafterScreenClient.class;
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
        ClientTickEvents.END_CLIENT_TICK.register(ObserverCrafterE2eBridge::tick);
    }

    private static void tick(Minecraft minecraft) {
        if ("observer".equals(role)) tickObserver(minecraft);
        else if ("target".equals(role)) tickTarget();
    }

    private static void tickObserver(Minecraft minecraft) {
        if (!observerRequested && markerExists("observer-native-sign-closed.txt")
                && markerExists("target-native-sign-close-sent.txt")) {
            setBoolean(DRIVER, "observerStopRequested", true);
            observerRequested = true;
            ObserverE2eCommon.marker("observer-ready-for-crafter.txt", "Target may send Crafter semantic state.\n");
        }
        if (observerRequested && !observerSeen && mirrorVisible(minecraft)) {
            if (!getBoolean(CRAFTER, "remotePowered")
                    || getInt(CRAFTER, "remoteDisabledMask") != ((1 << 1) | (1 << 7))
                    || getInt(CRAFTER, "remoteOccupiedInputSlots") != 3
                    || getListSize(CRAFTER, "remoteSlots") != 45) {
                fail("Crafter E2E semantic state mismatch");
                return;
            }
            if (getBoolean(GENERIC, "remoteGenericOpen")) {
                fail("Generic container relay competed with Crafter semantic relay");
                return;
            }
            observerSeen = true;
            ObserverE2eCommon.marker("observer-native-crafter-ok.txt", "Observer rendered Crafter semantic state.\n");
            Screenshot.takeScreenshot(minecraft.gameRenderer.mainRenderTarget(), ObserverCrafterE2eBridge::saveScreenshot);
        }
        if (observerSaved && !observerClosed && !getBoolean(CRAFTER, "remoteOpen") && minecraft.gui.screen() == null) {
            observerClosed = true;
            ObserverE2eCommon.marker("observer-native-crafter-closed.txt", "Crafter semantic mirror closed.\n");
            setBoolean(DRIVER, "observerStopRequested", false);
        }
    }

    private static void tickTarget() {
        if (targetStage == 0 && markerExists("observer-ready-for-crafter.txt")) {
            if (!dev.totem.vanillatweaks.client.ObserverNativeClient.targetSupportsScreen(ObserverCrafterScreenPayloads.CAPABILITY)) {
                fail("Target did not negotiate Crafter semantic capability");
                return;
            }
            targetStage = 1;
            ClientPlayNetworking.send(openState());
            ObserverE2eCommon.marker("target-native-crafter-state-sent.txt", "Target sent Crafter semantic state.\n");
        } else if (targetStage == 1 && markerExists("observer-native-crafter-saved.txt")) {
            targetStage = 2;
            ClientPlayNetworking.send(ObserverCrafterScreenPayloads.closed(++targetSequence));
            ObserverE2eCommon.marker("target-native-crafter-close-sent.txt", "Target sent Crafter close state.\n");
        }
    }

    private static ObserverCrafterScreenPayloads.CrafterState openState() {
        return new ObserverCrafterScreenPayloads.CrafterState(
                ObserverCrafterScreenPayloads.PROTOCOL_VERSION, ++targetSequence, true,
                ObserverCrafterScreenPayloads.FAMILY_ID, ObserverCrafterScreenPayloads.SCREEN_CLASS,
                "Crafter", true, (1 << 1) | (1 << 7), 3, slots());
    }

    private static List<ObserverNativeScreenPayloads.SlotState> slots() {
        List<ObserverNativeScreenPayloads.SlotState> result = new ArrayList<>();
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                int index = row * 3 + col;
                String item = switch (index) {
                    case 0 -> "minecraft:iron_ingot";
                    case 4 -> "minecraft:redstone";
                    case 8 -> "minecraft:crafting_table";
                    default -> "";
                };
                result.add(new ObserverNativeScreenPayloads.SlotState(index, 26 + col * 18, 17 + row * 18,
                        item, item.isEmpty() ? 0 : 1, 0));
            }
        }
        int index = 9;
        for (int row = 0; row < 3; row++) for (int col = 0; col < 9; col++)
            result.add(new ObserverNativeScreenPayloads.SlotState(index++, 8 + col * 18, 84 + row * 18, "", 0, 0));
        for (int col = 0; col < 9; col++)
            result.add(new ObserverNativeScreenPayloads.SlotState(index++, 8 + col * 18, 142, "", 0, 0));
        return List.copyOf(result);
    }

    private static boolean mirrorVisible(Minecraft minecraft) {
        return minecraft.gui.screen() != null
                && minecraft.gui.screen().getClass().getName().contains("NativeCrafterMirrorScreen")
                && getBoolean(CRAFTER, "remoteOpen") && getLong(CRAFTER, "lastRemoteSequence") > 0L
                && getLong(CRAFTER, "extractedFrames") > 0L;
    }

    private static void saveScreenshot(NativeImage image) {
        if (image == null) { fail("Crafter screenshot callback returned null"); return; }
        try (NativeImage owned = image) {
            Path output = ObserverE2eCommon.resultsDir().resolve("observer-native-crafter.png");
            Files.createDirectories(output.getParent());
            owned.writeToFile(output);
            observerSaved = true;
            ObserverE2eCommon.marker("observer-native-crafter-saved.txt", "Crafter semantic screenshot saved.\n");
        } catch (Exception error) { fail("Failed to save Crafter screenshot: " + error); }
    }

    private static boolean markerExists(String name) { return Files.isRegularFile(ObserverE2eCommon.resultsDir().resolve(name)); }
    private static void fail(String message) { ObserverE2eCommon.fail(role, message); }
    private static Field field(Class<?> owner, String name) {
        try { Field field = owner.getDeclaredField(name); field.setAccessible(true); return field; }
        catch (ReflectiveOperationException error) { throw new RuntimeException(error); }
    }
    private static boolean getBoolean(Class<?> owner, String name) { try { return field(owner, name).getBoolean(null); } catch (IllegalAccessException error) { throw new RuntimeException(error); } }
    private static int getInt(Class<?> owner, String name) { try { return field(owner, name).getInt(null); } catch (IllegalAccessException error) { throw new RuntimeException(error); } }
    private static long getLong(Class<?> owner, String name) { try { return field(owner, name).getLong(null); } catch (IllegalAccessException error) { throw new RuntimeException(error); } }
    private static int getListSize(Class<?> owner, String name) { try { Object value = field(owner, name).get(null); return value instanceof List<?> list ? list.size() : -1; } catch (IllegalAccessException error) { throw new RuntimeException(error); } }
    private static void setBoolean(Class<?> owner, String name, boolean value) { try { field(owner, name).setBoolean(null, value); } catch (IllegalAccessException error) { throw new RuntimeException(error); } }
}
