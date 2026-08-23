package dev.totem.vanillatweaks.client;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.NativeImage;
import dev.totem.vanillatweaks.TotemVanillaTweaks;
import dev.totem.vanillatweaks.network.ObserverPayloads;
import dev.totem.vanillatweaks.observer.ObserverFrameRules;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.UUID;

/** Client-side capture and read-only rendering for privileged spectator UI observation. */
public final class ObserverUiClient {
    private static final Identifier FRAME_TEXTURE =
            Identifier.fromNamespaceAndPath(TotemVanillaTweaks.MOD_ID, "observer/live_frame");

    private static boolean captureEnabled;
    private static int captureMaxWidth = ObserverFrameRules.MAX_WIDTH;
    private static int captureMaxHeight = ObserverFrameRules.MAX_HEIGHT;
    private static int captureFps = ObserverFrameRules.TARGET_FPS;
    private static long lastCaptureNanos;
    private static boolean captureInFlight;
    private static long nextFrameId;
    private static String lastScreenKey;

    private static boolean sessionActive;
    private static UUID targetId;
    private static String targetName = "";
    private static boolean remoteScreenOpen;
    private static FrameAssembly assembly;
    private static long lastFrameId = -1L;
    private static int frameWidth;
    private static int frameHeight;
    private static int sourceWidth;
    private static int sourceHeight;
    private static int remoteMouseX;
    private static int remoteMouseY;
    private static boolean textureRegistered;

    private ObserverUiClient() {
    }

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(
                ObserverPayloads.CaptureControl.TYPE,
                (payload, context) -> context.client().execute(() -> applyCaptureControl(payload))
        );
        ClientPlayNetworking.registerGlobalReceiver(
                ObserverPayloads.Session.TYPE,
                (payload, context) -> context.client().execute(() -> applySession(payload))
        );
        ClientPlayNetworking.registerGlobalReceiver(
                ObserverPayloads.ScreenRelay.TYPE,
                (payload, context) -> context.client().execute(() -> applyScreenRelay(payload))
        );
        ClientPlayNetworking.registerGlobalReceiver(
                ObserverPayloads.FrameRelay.TYPE,
                (payload, context) -> context.client().execute(() -> acceptFrameChunk(payload))
        );
        ClientTickEvents.END_CLIENT_TICK.register(ObserverUiClient::tickCapture);
    }

    private static void applyCaptureControl(ObserverPayloads.CaptureControl payload) {
        captureEnabled = payload.enabled();
        captureMaxWidth = clamp(payload.maxWidth(), 1, ObserverFrameRules.MAX_WIDTH);
        captureMaxHeight = clamp(payload.maxHeight(), 1, ObserverFrameRules.MAX_HEIGHT);
        captureFps = clamp(payload.fps(), 1, ObserverFrameRules.TARGET_FPS);
        lastScreenKey = null;
        lastCaptureNanos = 0L;
        if (!captureEnabled) {
            captureInFlight = false;
        }
    }

    private static void applySession(ObserverPayloads.Session payload) {
        sessionActive = payload.active();
        targetId = sessionActive ? payload.targetId() : null;
        targetName = sessionActive ? payload.targetName() : "";
        remoteScreenOpen = false;
        assembly = null;
        lastFrameId = -1L;
        if (!sessionActive) {
            closeMirrorScreen();
            releaseFrameTexture();
        }
    }

    private static void applyScreenRelay(ObserverPayloads.ScreenRelay payload) {
        if (!sessionActive || targetId == null || !targetId.equals(payload.targetId())) {
            return;
        }
        remoteScreenOpen = payload.open();
        assembly = null;
        if (remoteScreenOpen) {
            ensureMirrorScreen();
        } else {
            closeMirrorScreen();
            releaseFrameTexture();
        }
    }

    private static void tickCapture(Minecraft minecraft) {
        if (!captureEnabled || minecraft.player == null || minecraft.level == null) {
            return;
        }
        Screen screen = minecraft.gui.screen();
        boolean screenOpen = screen != null && !(screen instanceof ObserverMirrorScreen);
        String screenClass = screenOpen ? screen.getClass().getName() : "";
        String title = screenOpen && screen.getTitle() != null ? screen.getTitle().getString() : "";
        String key = screenOpen + "\u0000" + screenClass + "\u0000" + title;
        if (!key.equals(lastScreenKey)) {
            lastScreenKey = key;
            ClientPlayNetworking.send(new ObserverPayloads.ScreenState(screenOpen, screenClass, title));
        }
        if (!screenOpen || captureInFlight) {
            return;
        }

        long now = System.nanoTime();
        long interval = 1_000_000_000L / Math.max(1, captureFps);
        if (now - lastCaptureNanos < interval) {
            return;
        }
        lastCaptureNanos = now;
        captureFrame(minecraft);
    }

    private static void captureFrame(Minecraft minecraft) {
        RenderTarget renderTarget = minecraft.gameRenderer.mainRenderTarget();
        if (renderTarget == null) {
            return;
        }
        captureInFlight = true;
        Screenshot.takeScreenshot(renderTarget, image -> {
            try {
                if (!captureEnabled || image == null) {
                    return;
                }
                encodeAndSendFrame(minecraft, image);
            } catch (Throwable error) {
                TotemVanillaTweaks.LOGGER.warn("Observer UI capture failed", error);
            } finally {
                if (image != null) {
                    image.close();
                }
                captureInFlight = false;
            }
        });
    }

    private static void encodeAndSendFrame(Minecraft minecraft, NativeImage source) throws IOException {
        int divisor = Math.max(
                divideRoundUp(source.getWidth(), captureMaxWidth),
                divideRoundUp(source.getHeight(), captureMaxHeight)
        );
        divisor = Math.max(1, divisor);

        NativeImage scaled = null;
        NativeImage output = source;
        if (divisor > 1) {
            int width = Math.max(1, source.getWidth() / divisor);
            int height = Math.max(1, source.getHeight() / divisor);
            scaled = new NativeImage(width, height, false);
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    scaled.setPixel(x, y, source.getPixel(x * divisor, y * divisor));
                }
            }
            output = scaled;
        }

        int encodedWidth = output.getWidth();
        int encodedHeight = output.getHeight();
        byte[] png;
        Path temp = Files.createTempFile("totem-observer-", ".png");
        try {
            output.writeToFile(temp);
            png = Files.readAllBytes(temp);
        } finally {
            Files.deleteIfExists(temp);
            if (scaled != null) {
                scaled.close();
            }
        }
        if (png.length <= 0 || png.length > ObserverFrameRules.MAX_FRAME_BYTES) {
            TotemVanillaTweaks.LOGGER.debug("Skipping observer frame of {} bytes", png.length);
            return;
        }

        int chunkCount = ObserverFrameRules.chunkCount(png.length);
        if (chunkCount <= 0) {
            return;
        }
        long frameId = ++nextFrameId;
        int windowWidth = Math.max(1, minecraft.getWindow().getScreenWidth());
        int windowHeight = Math.max(1, minecraft.getWindow().getScreenHeight());
        int mouseX = (int) Math.round(minecraft.mouseHandler.xpos());
        int mouseY = (int) Math.round(minecraft.mouseHandler.ypos());

        for (int index = 0; index < chunkCount; index++) {
            int from = index * ObserverFrameRules.CHUNK_BYTES;
            int to = Math.min(png.length, from + ObserverFrameRules.CHUNK_BYTES);
            byte[] data = Arrays.copyOfRange(png, from, to);
            ClientPlayNetworking.send(new ObserverPayloads.FrameChunk(
                    frameId,
                    index,
                    chunkCount,
                    encodedWidth,
                    encodedHeight,
                    windowWidth,
                    windowHeight,
                    mouseX,
                    mouseY,
                    data
            ));
        }
    }

    private static void acceptFrameChunk(ObserverPayloads.FrameRelay payload) {
        if (!sessionActive || !remoteScreenOpen || targetId == null || !targetId.equals(payload.targetId())) {
            return;
        }
        if (!ObserverFrameRules.validChunk(
                payload.chunkIndex(), payload.chunkCount(), payload.frameWidth(), payload.frameHeight(),
                payload.sourceWidth(), payload.sourceHeight(), payload.data().length)) {
            return;
        }
        if (payload.frameId() < lastFrameId) {
            return;
        }
        if (assembly == null || assembly.frameId != payload.frameId()) {
            assembly = new FrameAssembly(payload);
        }
        if (!assembly.accept(payload) || !assembly.complete()) {
            return;
        }
        byte[] png = assembly.join();
        lastFrameId = payload.frameId();
        frameWidth = payload.frameWidth();
        frameHeight = payload.frameHeight();
        sourceWidth = payload.sourceWidth();
        sourceHeight = payload.sourceHeight();
        remoteMouseX = payload.mouseX();
        remoteMouseY = payload.mouseY();
        assembly = null;
        installFrameTexture(png);
        ensureMirrorScreen();
    }

    private static void installFrameTexture(byte[] png) {
        Minecraft minecraft = Minecraft.getInstance();
        NativeImage image = null;
        boolean installed = false;
        try {
            image = NativeImage.read(new ByteArrayInputStream(png));
            if (image.getWidth() != frameWidth
                    || image.getHeight() != frameHeight
                    || image.getWidth() > ObserverFrameRules.MAX_WIDTH
                    || image.getHeight() > ObserverFrameRules.MAX_HEIGHT) {
                TotemVanillaTweaks.LOGGER.warn(
                        "Rejected observer UI frame with decoded size {}x{} (declared {}x{})",
                        image.getWidth(), image.getHeight(), frameWidth, frameHeight
                );
                return;
            }
            if (textureRegistered) {
                minecraft.getTextureManager().release(FRAME_TEXTURE);
            }
            minecraft.getTextureManager().register(
                    FRAME_TEXTURE,
                    new DynamicTexture(FRAME_TEXTURE::toString, image)
            );
            textureRegistered = true;
            installed = true;
        } catch (IOException error) {
            TotemVanillaTweaks.LOGGER.warn("Failed to decode observer UI frame", error);
        } finally {
            if (!installed && image != null) {
                image.close();
            }
        }
    }

    private static void ensureMirrorScreen() {
        Minecraft minecraft = Minecraft.getInstance();
        if (!sessionActive || !remoteScreenOpen) {
            return;
        }
        if (!(minecraft.gui.screen() instanceof ObserverMirrorScreen)) {
            minecraft.setScreenAndShow(new ObserverMirrorScreen());
        }
    }

    private static void closeMirrorScreen() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.gui.screen() instanceof ObserverMirrorScreen) {
            minecraft.setScreenAndShow(null);
        }
    }

    private static void releaseFrameTexture() {
        if (!textureRegistered) {
            return;
        }
        Minecraft.getInstance().getTextureManager().release(FRAME_TEXTURE);
        textureRegistered = false;
        frameWidth = 0;
        frameHeight = 0;
    }

    private static void requestStop() {
        if (sessionActive) {
            ClientPlayNetworking.send(new ObserverPayloads.Stop());
        }
    }

    private static int divideRoundUp(int value, int divisor) {
        return (value + divisor - 1) / divisor;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static final class FrameAssembly {
        private final long frameId;
        private final int frameWidth;
        private final int frameHeight;
        private final int sourceWidth;
        private final int sourceHeight;
        private final int chunkCount;
        private final byte[][] chunks;
        private int received;
        private int totalBytes;

        private FrameAssembly(ObserverPayloads.FrameRelay first) {
            this.frameId = first.frameId();
            this.frameWidth = first.frameWidth();
            this.frameHeight = first.frameHeight();
            this.sourceWidth = first.sourceWidth();
            this.sourceHeight = first.sourceHeight();
            this.chunkCount = first.chunkCount();
            this.chunks = new byte[chunkCount][];
        }

        private boolean accept(ObserverPayloads.FrameRelay payload) {
            if (payload.frameId() != frameId
                    || payload.chunkCount() != chunkCount
                    || payload.frameWidth() != frameWidth
                    || payload.frameHeight() != frameHeight
                    || payload.sourceWidth() != sourceWidth
                    || payload.sourceHeight() != sourceHeight) {
                return false;
            }
            int index = payload.chunkIndex();
            if (chunks[index] != null) {
                return true;
            }
            byte[] copy = Arrays.copyOf(payload.data(), payload.data().length);
            chunks[index] = copy;
            received++;
            totalBytes += copy.length;
            return totalBytes <= ObserverFrameRules.MAX_FRAME_BYTES;
        }

        private boolean complete() {
            return received == chunkCount;
        }

        private byte[] join() {
            ByteArrayOutputStream output = new ByteArrayOutputStream(totalBytes);
            for (byte[] chunk : chunks) {
                output.writeBytes(chunk);
            }
            return output.toByteArray();
        }
    }

    private static final class ObserverMirrorScreen extends Screen {
        private ObserverMirrorScreen() {
            super(Component.translatable("screen.totem-vanilla-tweaks.observer_view"));
        }

        @Override
        public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
            graphics.fill(0, 0, width, height, 0xFF000000);
            if (textureRegistered && frameWidth > 0 && frameHeight > 0) {
                float scale = Math.min((float) width / frameWidth, (float) height / frameHeight);
                int drawWidth = Math.max(1, Math.round(frameWidth * scale));
                int drawHeight = Math.max(1, Math.round(frameHeight * scale));
                int drawX = (width - drawWidth) / 2;
                int drawY = (height - drawHeight) / 2;
                graphics.blit(
                        RenderPipelines.GUI_TEXTURED,
                        FRAME_TEXTURE,
                        drawX,
                        drawY,
                        0.0F,
                        0.0F,
                        drawWidth,
                        drawHeight,
                        frameWidth,
                        frameHeight
                );

                if (sourceWidth > 0 && sourceHeight > 0) {
                    int cursorX = drawX + Math.round((remoteMouseX / (float) sourceWidth) * drawWidth);
                    int cursorY = drawY + Math.round((remoteMouseY / (float) sourceHeight) * drawHeight);
                    graphics.fill(cursorX - 5, cursorY - 1, cursorX + 6, cursorY + 2, 0xFF000000);
                    graphics.fill(cursorX - 1, cursorY - 5, cursorX + 2, cursorY + 6, 0xFF000000);
                    graphics.fill(cursorX - 4, cursorY, cursorX + 5, cursorY + 1, 0xFFFFFFFF);
                    graphics.fill(cursorX, cursorY - 4, cursorX + 1, cursorY + 5, 0xFFFFFFFF);
                }
            }
            String label = targetName.isBlank() ? "Observer View" : "Observer View: " + targetName;
            graphics.text(font, Component.literal(label), 6, 6, 0xFFFFFFFF);
            graphics.text(font, Component.translatable("screen.totem-vanilla-tweaks.observer_view.exit"), 6, 18, 0xFFB0B0B0);
        }

        @Override
        public boolean isPauseScreen() {
            return false;
        }

        @Override
        public void onClose() {
            requestStop();
        }
    }
}
