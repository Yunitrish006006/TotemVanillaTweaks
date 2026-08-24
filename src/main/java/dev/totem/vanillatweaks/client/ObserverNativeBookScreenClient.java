package dev.totem.vanillatweaks.client;

import dev.totem.vanillatweaks.mixin.client.BookEditScreenAccessor;
import dev.totem.vanillatweaks.mixin.client.BookSignScreenAccessor;
import dev.totem.vanillatweaks.mixin.client.BookViewScreenAccessor;
import dev.totem.vanillatweaks.network.ObserverBookScreenPayloads;
import dev.totem.vanillatweaks.network.ObserverNativeScreenPayloads;
import dev.totem.vanillatweaks.network.ObserverPayloads;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.BookEditScreen;
import net.minecraft.client.gui.screens.inventory.BookSignScreen;
import net.minecraft.client.gui.screens.inventory.BookViewScreen;
import net.minecraft.client.gui.screens.inventory.LecternScreen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Client-side target adapter and Observer reconstruction for the semantic book family. */
public final class ObserverNativeBookScreenClient {
    private static final long SNAPSHOT_INTERVAL_NANOS = 100_000_000L;
    private static final int MAX_RENDERED_LINES = 14;
    private static final int APPROX_CHARS_PER_LINE = 24;

    private static long nextTargetSequence;
    private static long lastSnapshotNanos;
    private static boolean targetBookOpen;

    private static long lastRemoteSequence = -1L;
    private static boolean remoteOpen;
    private static String remoteVariant = "";
    private static String remoteScreenClass = "";
    private static String remoteTitle = "";
    private static int remotePageIndex;
    private static int remotePageCount;
    private static String remotePageText = "";
    private static String remoteBookTitle = "";
    private static String remoteAuthor = "";

    private static boolean suppressMirrorStop;
    private static long extractedFrames;

    private ObserverNativeBookScreenClient() {
    }

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(
                ObserverBookScreenPayloads.BookRelay.TYPE,
                (payload, context) -> context.client().execute(() -> acceptRelay(payload))
        );
        ClientTickEvents.END_CLIENT_TICK.register(ObserverNativeBookScreenClient::tick);
    }

    static boolean isNativeMirrorScreen(Screen screen) {
        return screen instanceof NativeBookMirrorScreen;
    }

    static boolean hasStructuredRemoteScreen() {
        return remoteOpen && ObserverNativeClient.observerSessionActive();
    }

    static long extractedFrames() {
        return extractedFrames;
    }

    static long lastRemoteSequence() {
        return lastRemoteSequence;
    }

    private static void tick(Minecraft minecraft) {
        if (!ObserverNativeClient.targetStateEnabled() || minecraft.player == null || minecraft.level == null) {
            targetBookOpen = false;
            lastSnapshotNanos = 0L;
        } else {
            tickTarget(minecraft);
        }

        if (!ObserverNativeClient.observerSessionActive()) {
            clearRemote();
            closeMirror();
            return;
        }

        if (remoteOpen) {
            ensureMirror();
        }
    }

    private static void tickTarget(Minecraft minecraft) {
        boolean supported = ObserverNativeClient.targetSupportsScreen(ObserverNativeScreenPayloads.CAPABILITY_BOOK);
        if (!supported) {
            targetBookOpen = false;
            lastSnapshotNanos = 0L;
            return;
        }

        Screen screen = minecraft.gui.screen();
        BookSnapshot snapshot = snapshot(screen);
        if (snapshot == null) {
            closeTargetBook();
            return;
        }

        long now = System.nanoTime();
        if (targetBookOpen && now - lastSnapshotNanos < SNAPSHOT_INTERVAL_NANOS) {
            return;
        }
        targetBookOpen = true;
        lastSnapshotNanos = now;
        String title = screen.getTitle() == null ? "" : screen.getTitle().getString();
        ClientPlayNetworking.send(new ObserverBookScreenPayloads.BookState(
                ObserverBookScreenPayloads.PROTOCOL_VERSION,
                ++nextTargetSequence,
                true,
                ObserverNativeScreenPayloads.FAMILY_BOOK,
                snapshot.variant(),
                screen.getClass().getName(),
                title,
                snapshot.pageIndex(),
                snapshot.pageCount(),
                limit(snapshot.pageText(), ObserverBookScreenPayloads.MAX_PAGE_TEXT),
                limit(snapshot.bookTitle(), ObserverBookScreenPayloads.MAX_METADATA_TEXT),
                limit(snapshot.author(), ObserverBookScreenPayloads.MAX_METADATA_TEXT)
        ));
    }

    private static BookSnapshot snapshot(Screen screen) {
        if (screen instanceof BookSignScreen signScreen) {
            BookSignScreenAccessor accessor = (BookSignScreenAccessor) signScreen;
            List<String> pages = accessor.totemVanillaTweaks$getPages();
            Component ownerText = accessor.totemVanillaTweaks$getOwnerText();
            return new BookSnapshot(
                    ObserverBookScreenPayloads.VARIANT_SIGNING,
                    0,
                    pages == null ? 0 : pages.size(),
                    "",
                    nullToEmpty(accessor.totemVanillaTweaks$getTitleValue()),
                    ownerText == null ? "" : ownerText.getString()
            );
        }

        if (screen instanceof BookEditScreen editScreen) {
            BookEditScreenAccessor accessor = (BookEditScreenAccessor) editScreen;
            List<String> pages = accessor.totemVanillaTweaks$getPages();
            int count = pages == null ? 0 : pages.size();
            int page = count == 0 ? 0 : clamp(accessor.totemVanillaTweaks$getCurrentPage(), 0, count - 1);
            String text = count == 0 ? "" : nullToEmpty(pages.get(page));
            return new BookSnapshot(
                    ObserverBookScreenPayloads.VARIANT_WRITABLE,
                    page,
                    count,
                    text,
                    "",
                    ""
            );
        }

        if (screen instanceof BookViewScreen viewScreen) {
            BookViewScreenAccessor accessor = (BookViewScreenAccessor) viewScreen;
            BookViewScreen.BookAccess access = accessor.totemVanillaTweaks$getBookAccess();
            int count = access == null ? 0 : access.getPageCount();
            int page = count == 0 ? 0 : clamp(accessor.totemVanillaTweaks$getCurrentPage(), 0, count - 1);
            Component component = count == 0 ? Component.empty() : access.getPage(page);
            String variant = screen instanceof LecternScreen
                    ? ObserverBookScreenPayloads.VARIANT_LECTERN
                    : ObserverBookScreenPayloads.VARIANT_WRITTEN;
            return new BookSnapshot(
                    variant,
                    page,
                    count,
                    component == null ? "" : component.getString(),
                    "",
                    ""
            );
        }

        return null;
    }

    private static void closeTargetBook() {
        if (!targetBookOpen) {
            return;
        }
        targetBookOpen = false;
        lastSnapshotNanos = 0L;
        ClientPlayNetworking.send(new ObserverBookScreenPayloads.BookState(
                ObserverBookScreenPayloads.PROTOCOL_VERSION,
                ++nextTargetSequence,
                false,
                ObserverNativeScreenPayloads.FAMILY_BOOK,
                "",
                "",
                "",
                0,
                0,
                "",
                "",
                ""
        ));
    }

    private static void acceptRelay(ObserverBookScreenPayloads.BookRelay payload) {
        UUID targetId = ObserverNativeClient.observerTargetId();
        if (!ObserverNativeClient.observerSessionActive()
                || !ObserverNativeClient.observerSupportsScreen(ObserverNativeScreenPayloads.CAPABILITY_BOOK)
                || targetId == null
                || !targetId.equals(payload.targetId())
                || payload.protocolVersion() != ObserverBookScreenPayloads.PROTOCOL_VERSION
                || !ObserverNativeScreenPayloads.FAMILY_BOOK.equals(payload.familyId())
                || payload.sequence() <= lastRemoteSequence) {
            return;
        }

        lastRemoteSequence = payload.sequence();
        if (!payload.open()) {
            clearRemote();
            closeMirror();
            return;
        }

        ObserverNativeScreenClient.applyGenericScreenState(false, "", "");
        remoteOpen = true;
        remoteVariant = payload.variant();
        remoteScreenClass = payload.screenClass();
        remoteTitle = payload.title();
        remotePageCount = clamp(payload.pageCount(), 0, ObserverBookScreenPayloads.MAX_PAGE_COUNT);
        remotePageIndex = remotePageCount == 0 ? 0 : clamp(payload.pageIndex(), 0, remotePageCount - 1);
        remotePageText = limit(payload.pageText(), ObserverBookScreenPayloads.MAX_PAGE_TEXT);
        remoteBookTitle = limit(payload.bookTitle(), ObserverBookScreenPayloads.MAX_METADATA_TEXT);
        remoteAuthor = limit(payload.author(), ObserverBookScreenPayloads.MAX_METADATA_TEXT);
        ensureMirror();
    }

    private static void ensureMirror() {
        Minecraft minecraft = Minecraft.getInstance();
        if (!remoteOpen || !ObserverNativeClient.observerSessionActive()) {
            return;
        }
        if (!(minecraft.gui.screen() instanceof NativeBookMirrorScreen)) {
            suppressMirrorStop = true;
            try {
                minecraft.setScreenAndShow(new NativeBookMirrorScreen());
            } finally {
                suppressMirrorStop = false;
            }
        }
    }

    private static void closeMirror() {
        Minecraft minecraft = Minecraft.getInstance();
        if (!(minecraft.gui.screen() instanceof NativeBookMirrorScreen)) {
            return;
        }
        suppressMirrorStop = true;
        try {
            minecraft.setScreenAndShow(null);
        } finally {
            suppressMirrorStop = false;
        }
    }

    private static void clearRemote() {
        remoteOpen = false;
        remoteVariant = "";
        remoteScreenClass = "";
        remoteTitle = "";
        remotePageIndex = 0;
        remotePageCount = 0;
        remotePageText = "";
        remoteBookTitle = "";
        remoteAuthor = "";
    }

    private static List<String> wrap(String text) {
        List<String> lines = new ArrayList<>();
        String normalized = nullToEmpty(text).replace("\r", "");
        for (String paragraph : normalized.split("\n", -1)) {
            String remaining = paragraph;
            if (remaining.isEmpty()) {
                lines.add("");
                continue;
            }
            while (!remaining.isEmpty() && lines.size() < MAX_RENDERED_LINES) {
                int take = Math.min(APPROX_CHARS_PER_LINE, remaining.length());
                int split = take;
                if (take < remaining.length()) {
                    int space = remaining.lastIndexOf(' ', take);
                    if (space > 0) {
                        split = space;
                    }
                }
                lines.add(remaining.substring(0, split));
                remaining = remaining.substring(split).stripLeading();
            }
            if (lines.size() >= MAX_RENDERED_LINES) {
                break;
            }
        }
        return lines;
    }

    private static String limit(String value, int maxLength) {
        String safe = nullToEmpty(value);
        return safe.length() <= maxLength ? safe : safe.substring(0, maxLength);
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private record BookSnapshot(
            String variant,
            int pageIndex,
            int pageCount,
            String pageText,
            String bookTitle,
            String author
    ) {
    }

    private static final class NativeBookMirrorScreen extends Screen {
        private NativeBookMirrorScreen() {
            super(Component.literal("Observer Book"));
        }

        @Override
        public void onClose() {
            if (!suppressMirrorStop && ObserverNativeClient.observerSessionActive()) {
                ClientPlayNetworking.send(new ObserverPayloads.Stop());
            }
            super.onClose();
        }

        @Override
        public boolean isPauseScreen() {
            return false;
        }

        @Override
        public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
            graphics.fill(0, 0, width, height, 0x90000000);
            int bookWidth = Math.min(220, Math.max(180, width - 80));
            int bookHeight = Math.min(190, Math.max(150, height - 70));
            int left = (width - bookWidth) / 2;
            int top = (height - bookHeight) / 2;

            graphics.fill(left, top, left + bookWidth, top + bookHeight, 0xFFF1E4C2);
            graphics.fill(left + 5, top + 5, left + bookWidth - 5, top + bookHeight - 5, 0xFFFFF8DC);
            graphics.fill(left + 9, top + 28, left + bookWidth - 9, top + 29, 0xFFB8A77E);

            String title = remoteTitle.isBlank() ? "Book" : remoteTitle;
            graphics.text(this.minecraft.font, title, left + 12, top + 10, 0xFF3B2A1F, true);
            String mode = "book/" + (remoteVariant.isBlank() ? "unknown" : remoteVariant);
            graphics.text(
                    this.minecraft.font,
                    mode,
                    left + bookWidth - 12 - this.minecraft.font.width(mode),
                    top + 10,
                    0xFF6D5A45,
                    false
            );

            if (ObserverBookScreenPayloads.VARIANT_SIGNING.equals(remoteVariant)) {
                graphics.text(this.minecraft.font, "Sign book", left + 16, top + 45, 0xFF3B2A1F, true);
                graphics.text(this.minecraft.font, remoteBookTitle, left + 16, top + 68, 0xFF202020, false);
                graphics.text(this.minecraft.font, remoteAuthor, left + 16, top + 92, 0xFF6D5A45, false);
            } else {
                int y = top + 40;
                for (String line : wrap(remotePageText)) {
                    graphics.text(this.minecraft.font, line, left + 16, y, 0xFF202020, false);
                    y += 10;
                    if (y > top + bookHeight - 30) {
                        break;
                    }
                }
                String page = remotePageCount <= 0
                        ? "Page 0/0"
                        : "Page " + (remotePageIndex + 1) + "/" + remotePageCount;
                graphics.text(
                        this.minecraft.font,
                        page,
                        left + bookWidth - 14 - this.minecraft.font.width(page),
                        top + bookHeight - 20,
                        0xFF6D5A45,
                        false
                );
            }

            extractedFrames++;
        }
    }
}
