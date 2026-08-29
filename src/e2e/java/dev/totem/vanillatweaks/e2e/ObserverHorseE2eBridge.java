package dev.totem.vanillatweaks.e2e;

import com.mojang.blaze3d.platform.NativeImage;
import dev.totem.core.api.v1.client.observer.ObserverReadOnlyScreen;
import dev.totem.vanillatweaks.client.ObserverHorseScreenClient;
import dev.totem.vanillatweaks.client.ObserverNativeClient;
import dev.totem.vanillatweaks.network.*;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.inventory.HorseInventoryScreen;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Dedicated-server + two-client proof for Horse/Mount inventory semantics. */
public final class ObserverHorseE2eBridge implements ClientModInitializer {
    private static final Class<?> HORSE = ObserverHorseScreenClient.class;
    private static final Class<?> DRIVER = ObserverE2eClient.class;
    private static final UUID MOUNT = UUID.fromString("25000000-0000-0000-0000-000000000025");
    private static String role;
    private static boolean requested, seen, saved, closed;
    private static int targetStage;
    private static long targetSequence, renderBarrier = -1;

    @Override public void onInitializeClient() {
        if (!Boolean.getBoolean("totem.observer.e2e.enabled")) return;
        role = System.getProperty("totem.observer.e2e.role", "").trim();
        ClientTickEvents.END_CLIENT_TICK.register(ObserverHorseE2eBridge::tick);
    }

    private static void tick(Minecraft minecraft) {
        if ("observer".equals(role)) tickObserver(minecraft);
        else if ("target".equals(role)) tickTarget();
    }

    private static void tickObserver(Minecraft minecraft) {
        if (!requested && marker("observer-native-stats-closed.txt") && marker("target-native-stats-close-sent.txt")) {
            setDriverStop(true); requested = true;
            ObserverE2eCommon.marker("observer-ready-for-horse.txt", "Target may send Horse inventory semantics.\n");
        }
        var relay = remote();
        if (requested && !seen && relay != null && relay.sequence() >= 2 && renderBarrier < 0)
            renderBarrier = extractedFrames();
        if (requested && !seen && relay != null && relay.sequence() >= 2
                && minecraft.gui.screen() instanceof HorseInventoryScreen horse
                && minecraft.gui.screen() instanceof ObserverReadOnlyScreen readOnly
                && readOnly.totem$isObserverReadOnly() && horse.getMenu().getSlot(2).getItem().getCount() == 4
                && extractedFrames() > renderBarrier) {
            seen = true;
            ObserverE2eCommon.marker("observer-native-horse-ok.txt",
                    "Observer rendered the later Horse inventory state with the production Screen.\n");
            Screenshot.takeScreenshot(minecraft.gameRenderer.mainRenderTarget(), ObserverHorseE2eBridge::save);
        }
        if (saved && !closed && remote() == null && minecraft.gui.screen() == null) {
            closed = true; setDriverStop(false);
            ObserverE2eCommon.marker("observer-native-horse-closed.txt", "Horse inventory semantic screen closed.\n");
        }
    }

    private static void tickTarget() {
        if (targetStage == 0 && marker("observer-ready-for-horse.txt")) {
            if (!ObserverNativeClient.targetSupportsScreen(ObserverHorseScreenPayloads.CAPABILITY)) {
                fail("Target did not negotiate Horse inventory capability"); return;
            }
            targetStage = 1;
            ClientPlayNetworking.send(open(++targetSequence, 1));
            ClientPlayNetworking.send(open(++targetSequence, 4));
            ObserverE2eCommon.marker("target-native-horse-state-sent.txt", "Target sent two Horse inventory states.\n");
        } else if (targetStage == 1 && marker("observer-native-horse-saved.txt")) {
            targetStage = 2;
            ClientPlayNetworking.send(ObserverHorseScreenPayloads.closed(++targetSequence));
            ObserverE2eCommon.marker("target-native-horse-close-sent.txt", "Target sent Horse inventory close.\n");
        }
    }

    private static ObserverHorseScreenPayloads.HorseState open(long sequence, int diamonds) {
        List<ObserverHorseScreenPayloads.HorseSlotState> slots = new ArrayList<>();
        for (int i = 0; i < 47; i++) slots.add(new ObserverHorseScreenPayloads.HorseSlotState(i, 0, 0, ItemStack.EMPTY));
        slots.set(2, new ObserverHorseScreenPayloads.HorseSlotState(2, 80, 18, new ItemStack(Items.DIAMOND, diamonds)));
        return new ObserverHorseScreenPayloads.HorseState(1, sequence, true,
                ObserverHorseScreenPayloads.FAMILY_ID, ObserverHorseScreenPayloads.SCREEN_CLASS,
                "Llama", 2525, MOUNT, "minecraft:llama", 3, slots);
    }

    private static void save(NativeImage image) {
        if (image == null) { fail("Horse screenshot callback returned null"); return; }
        try (NativeImage owned = image) {
            Path output = ObserverE2eCommon.resultsDir().resolve("observer-native-horse.png");
            Files.createDirectories(output.getParent()); owned.writeToFile(output); saved = true;
            ObserverE2eCommon.marker("observer-native-horse-saved.txt", "Horse semantic screenshot saved.\n");
        } catch (Exception error) { fail("Failed to save Horse screenshot: " + error); }
    }

    private static ObserverHorseScreenPayloads.HorseRelay remote() {
        try { return (ObserverHorseScreenPayloads.HorseRelay) field(HORSE, "remote").get(null); }
        catch (IllegalAccessException error) { throw new RuntimeException(error); }
    }
    private static long extractedFrames() {
        try { return field(HORSE, "extractedFrames").getLong(null); }
        catch (IllegalAccessException error) { throw new RuntimeException(error); }
    }
    private static void setDriverStop(boolean value) {
        try { field(DRIVER, "observerStopRequested").setBoolean(null, value); }
        catch (IllegalAccessException error) { throw new RuntimeException(error); }
    }
    private static Field field(Class<?> owner, String name) {
        try { Field field = owner.getDeclaredField(name); field.setAccessible(true); return field; }
        catch (ReflectiveOperationException error) { throw new RuntimeException(error); }
    }
    private static boolean marker(String name) { return Files.isRegularFile(ObserverE2eCommon.resultsDir().resolve(name)); }
    private static void fail(String message) { ObserverE2eCommon.fail(role, message); }
}
