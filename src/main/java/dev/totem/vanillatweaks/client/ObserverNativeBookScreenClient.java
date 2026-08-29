package dev.totem.vanillatweaks.client;

import dev.totem.core.api.v1.client.observer.ObserverReadOnlyScreen;
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
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.LecternMenu;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.WritableBookContent;
import net.minecraft.world.item.component.WrittenBookContent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Client-side target adapter and Observer reconstruction for the semantic book family. */
public final class ObserverNativeBookScreenClient {
    private static final long SNAPSHOT_INTERVAL_NANOS = 100_000_000L;
    private static long nextTargetSequence;
    private static long lastSnapshotNanos;
    private static boolean targetBookOpen;

    private static boolean remoteOpen;
    private static String remoteVariant = "";
    private static String remoteScreenClass = "";
    private static String remoteTitle = "";
    private static int remotePageIndex;
    private static int remotePageCount;
    private static String remotePageText = "";
    private static String remoteBookTitle = "";
    private static String remoteAuthor = "";

    private static boolean suppressObserverScreenStop;
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

    static boolean isNativeObserverScreen(Screen screen) {
        return screen instanceof ObserverBookViewScreen
                || screen instanceof ObserverBookEditScreen
                || screen instanceof ObserverBookSignScreen
                || screen instanceof ObserverLecternScreen;
    }

    static boolean hasStructuredRemoteScreen() {
        return remoteOpen && ObserverNativeClient.observerSessionActive();
    }

    static long extractedFrames() {
        return extractedFrames;
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
            closeObserverScreen();
            return;
        }

        if (remoteOpen) {
            ensureObserverScreen();
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
        if (snapshot(screen) == null) {
            closeTargetBook();
            return;
        }

        long now = System.nanoTime();
        if (targetBookOpen && now - lastSnapshotNanos < SNAPSHOT_INTERVAL_NANOS) {
            return;
        }
        ObserverBookScreenPayloads.BookState state = captureTargetState(screen, ++nextTargetSequence);
        if (state == null) {
            closeTargetBook();
            return;
        }
        targetBookOpen = true;
        lastSnapshotNanos = now;
        ClientPlayNetworking.send(state);
    }

    private static ObserverBookScreenPayloads.BookState captureTargetState(Screen screen, long sequence) {
        BookSnapshot snapshot = snapshot(screen);
        if (snapshot == null) return null;
        String title = screen.getTitle() == null ? "" : screen.getTitle().getString();
        return new ObserverBookScreenPayloads.BookState(
                ObserverBookScreenPayloads.PROTOCOL_VERSION,
                sequence,
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
        );
    }

    private static BookSnapshot snapshot(Screen screen) {
        if (screen instanceof BookSignScreen signScreen) {
            BookSignScreenAccessor accessor = (BookSignScreenAccessor) signScreen;
            List<String> pages = accessor.totemVanillaTweaks$getPages();
            return new BookSnapshot(
                    ObserverBookScreenPayloads.VARIANT_SIGNING,
                    0,
                    pages == null ? 0 : pages.size(),
                    "",
                    "",
                    ""
            );
        }

        if (screen instanceof BookEditScreen editScreen) {
            BookEditScreenAccessor accessor = (BookEditScreenAccessor) editScreen;
            List<String> pages = accessor.totemVanillaTweaks$getPages();
            int count = pages == null ? 0 : pages.size();
            int page = count == 0 ? 0 : clamp(accessor.totemVanillaTweaks$getCurrentPage(), 0, count - 1);
            return new BookSnapshot(
                    ObserverBookScreenPayloads.VARIANT_WRITABLE,
                    page,
                    count,
                    "",
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
        ClientPlayNetworking.send(ObserverBookScreenPayloads.closed(++nextTargetSequence));
    }

    private static void acceptRelay(ObserverBookScreenPayloads.BookRelay payload) {
        UUID targetId = ObserverNativeClient.observerTargetId();
        if (!ObserverNativeClient.observerSessionActive()
                || !ObserverNativeClient.observerSupportsScreen(ObserverNativeScreenPayloads.CAPABILITY_BOOK)
                || targetId == null
                || !targetId.equals(payload.targetId())
                || payload.protocolVersion() != ObserverBookScreenPayloads.PROTOCOL_VERSION
                || !ObserverNativeScreenPayloads.FAMILY_BOOK.equals(payload.familyId())
                || !ObserverRemoteSequenceTracker.accept(
                        ObserverNativeScreenPayloads.FAMILY_BOOK,
                        payload.targetId(), payload.sequence())) {
            return;
        }
        if (!payload.open()) {
            clearRemote();
            closeObserverScreen();
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
        ensureObserverScreen();
    }

    private static void ensureObserverScreen() {
        Minecraft minecraft = Minecraft.getInstance();
        if (!remoteOpen || !ObserverNativeClient.observerSessionActive()) {
            return;
        }
        Screen current = minecraft.gui.screen();
        if (!matchesRemoteVariant(current)) {
            suppressObserverScreenStop = true;
            try {
                minecraft.setScreenAndShow(createRemoteScreen(minecraft));
            } finally {
                suppressObserverScreenStop = false;
            }
        }
        applyRemoteState(minecraft.gui.screen());
    }

    private static void closeObserverScreen() {
        Minecraft minecraft = Minecraft.getInstance();
        if (!isNativeObserverScreen(minecraft.gui.screen())) {
            return;
        }
        suppressObserverScreenStop = true;
        try {
            minecraft.setScreenAndShow(null);
        } finally {
            suppressObserverScreenStop = false;
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

    private static boolean matchesRemoteVariant(Screen screen) {
        return switch (remoteVariant) {
            case ObserverBookScreenPayloads.VARIANT_WRITABLE -> screen instanceof ObserverBookEditScreen;
            case ObserverBookScreenPayloads.VARIANT_SIGNING -> screen instanceof ObserverBookSignScreen;
            case ObserverBookScreenPayloads.VARIANT_LECTERN -> screen instanceof ObserverLecternScreen;
            default -> screen instanceof ObserverBookViewScreen;
        };
    }

    private static Screen createRemoteScreen(Minecraft minecraft) {
        List<Component> pages = componentPages();
        if (ObserverBookScreenPayloads.VARIANT_WRITABLE.equals(remoteVariant)) {
            WritableBookContent content = new WritableBookContent(stringPages().stream()
                    .map(Filterable::passThrough).toList());
            ItemStack stack = new ItemStack(Items.WRITABLE_BOOK);
            stack.set(DataComponents.WRITABLE_BOOK_CONTENT, content);
            return new ObserverBookEditScreen(minecraft.player, stack, InteractionHand.MAIN_HAND, content);
        }
        if (ObserverBookScreenPayloads.VARIANT_SIGNING.equals(remoteVariant)) {
            WritableBookContent content = new WritableBookContent(stringPages().stream()
                    .map(Filterable::passThrough).toList());
            ItemStack stack = new ItemStack(Items.WRITABLE_BOOK);
            stack.set(DataComponents.WRITABLE_BOOK_CONTENT, content);
            ObserverBookEditScreen edit = new ObserverBookEditScreen(
                    minecraft.player, stack, InteractionHand.MAIN_HAND, content);
            ObserverBookSignScreen sign = new ObserverBookSignScreen(
                    edit, minecraft.player, InteractionHand.MAIN_HAND, stringPages());
            BookSignScreenAccessor accessor = (BookSignScreenAccessor) (Object) sign;
            accessor.totemVanillaTweaks$setTitleValue(remoteBookTitle);
            accessor.totemVanillaTweaks$setOwnerText(Component.literal(remoteAuthor));
            return sign;
        }
        if (ObserverBookScreenPayloads.VARIANT_LECTERN.equals(remoteVariant)) {
            SimpleContainer container = new SimpleContainer(1);
            ItemStack writtenBook = new ItemStack(Items.WRITTEN_BOOK);
            writtenBook.set(DataComponents.WRITTEN_BOOK_CONTENT, new WrittenBookContent(
                    Filterable.passThrough(remoteBookTitle), remoteAuthor, 0,
                    pages.stream().map(Filterable::passThrough).toList(), true));
            container.setItem(0, writtenBook);
            SimpleContainerData data = new SimpleContainerData(1);
            data.set(0, remotePageIndex);
            Inventory inventory = ObserverVanillaScreenSupport.detachedInventory();
            return new ObserverLecternScreen(
                    new LecternMenu(0, container, data), inventory,
                    Component.literal(remoteTitle.isBlank() ? "Lectern" : remoteTitle));
        }
        return new ObserverBookViewScreen(new BookViewScreen.BookAccess(pages));
    }

    private static void applyRemoteState(Screen screen) {
        if (screen instanceof ObserverBookViewScreen view) {
            view.setBookAccess(new BookViewScreen.BookAccess(componentPages()));
            view.setPage(remotePageIndex);
        } else if (screen instanceof ObserverBookEditScreen edit) {
            BookEditScreenAccessor accessor = (BookEditScreenAccessor) (Object) edit;
            accessor.totemVanillaTweaks$getPages().clear();
            accessor.totemVanillaTweaks$getPages().addAll(stringPages());
            accessor.totemVanillaTweaks$setCurrentPage(remotePageIndex);
            accessor.totemVanillaTweaks$updatePageContent();
        } else if (screen instanceof ObserverBookSignScreen sign) {
            BookSignScreenAccessor accessor = (BookSignScreenAccessor) (Object) sign;
            accessor.totemVanillaTweaks$getPages().clear();
            accessor.totemVanillaTweaks$getPages().addAll(stringPages());
            accessor.totemVanillaTweaks$setTitleValue(remoteBookTitle);
            accessor.totemVanillaTweaks$setOwnerText(Component.literal(remoteAuthor));
            if (accessor.totemVanillaTweaks$getTitleBox() != null) {
                accessor.totemVanillaTweaks$getTitleBox().setValue(remoteBookTitle);
            }
        } else if (screen instanceof ObserverLecternScreen lectern) {
            lectern.getMenu().setData(0, remotePageIndex);
            lectern.setBookAccess(new BookViewScreen.BookAccess(componentPages()));
            lectern.setPage(remotePageIndex);
        }
    }

    private static List<String> stringPages() {
        int count = Math.max(1, remotePageCount);
        List<String> pages = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            pages.add(index == remotePageIndex ? remotePageText : "");
        }
        return pages;
    }

    private static List<Component> componentPages() {
        return stringPages().stream().<Component>map(Component::literal).toList();
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

    private static final class ObserverBookViewScreen extends BookViewScreen implements ObserverReadOnlyScreen {
        private ObserverBookViewScreen(BookAccess access) { super(access); }
        @Override public boolean totem$isObserverReadOnly() { return true; }
        @Override public boolean isPauseScreen() { return false; }
        @Override public void onClose() { if (!suppressObserverScreenStop) ObserverVanillaScreenSupport.stopObserving(); }
        @Override public void extractRenderState(GuiGraphicsExtractor g, int x, int y, float tick) {
            super.extractRenderState(g, x, y, tick); extractedFrames++;
        }
    }

    private static final class ObserverBookEditScreen extends BookEditScreen implements ObserverReadOnlyScreen {
        private ObserverBookEditScreen(net.minecraft.world.entity.player.Player player, ItemStack book,
                                       InteractionHand hand, WritableBookContent content) {
            super(player, book, hand, content);
        }
        @Override public boolean totem$isObserverReadOnly() { return true; }
        @Override public boolean isPauseScreen() { return false; }
        @Override public void onClose() { if (!suppressObserverScreenStop) ObserverVanillaScreenSupport.stopObserving(); }
        @Override public void extractRenderState(GuiGraphicsExtractor g, int x, int y, float tick) {
            super.extractRenderState(g, x, y, tick); extractedFrames++;
        }
    }

    private static final class ObserverBookSignScreen extends BookSignScreen implements ObserverReadOnlyScreen {
        private ObserverBookSignScreen(BookEditScreen edit, net.minecraft.world.entity.player.Player player,
                                       InteractionHand hand, List<String> pages) { super(edit, player, hand, pages); }
        @Override public boolean totem$isObserverReadOnly() { return true; }
        @Override public boolean isPauseScreen() { return false; }
        @Override public void onClose() { if (!suppressObserverScreenStop) ObserverVanillaScreenSupport.stopObserving(); }
        @Override public void extractRenderState(GuiGraphicsExtractor g, int x, int y, float tick) {
            super.extractRenderState(g, x, y, tick); extractedFrames++;
        }
    }

    private static final class ObserverLecternScreen extends LecternScreen implements ObserverReadOnlyScreen {
        private ObserverLecternScreen(LecternMenu menu, Inventory inventory, Component title) {
            super(menu, inventory, title);
        }
        @Override public boolean totem$isObserverReadOnly() { return true; }
        @Override public void onClose() { if (!suppressObserverScreenStop) ObserverVanillaScreenSupport.stopObserving(); }
        @Override public void extractRenderState(GuiGraphicsExtractor g, int x, int y, float tick) {
            super.extractRenderState(g, x, y, tick); extractedFrames++;
        }
    }
}
