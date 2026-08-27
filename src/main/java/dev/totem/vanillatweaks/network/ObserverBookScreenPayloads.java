package dev.totem.vanillatweaks.network;

import dev.totem.vanillatweaks.TotemVanillaTweaks;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.UUID;

/** Structured Book semantic-family transport for protocol-native Observer View. */
public final class ObserverBookScreenPayloads {
    public static final int PROTOCOL_VERSION = 1;
    public static final int MAX_PAGE_COUNT = 100;
    public static final int MAX_PAGE_TEXT = 8192;
    public static final int MAX_METADATA_TEXT = 256;

    public static final String VARIANT_WRITTEN = "written";
    public static final String VARIANT_WRITABLE = "writable";
    public static final String VARIANT_LECTERN = "lectern";
    public static final String VARIANT_SIGNING = "signing";

    private ObserverBookScreenPayloads() {
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(TotemVanillaTweaks.MOD_ID, path);
    }

    public record BookState(
            int protocolVersion,
            long sequence,
            boolean open,
            String familyId,
            String variant,
            String screenClass,
            String title,
            int pageIndex,
            int pageCount,
            String pageText,
            String bookTitle,
            String author
    ) implements CustomPacketPayload {
        public static final Type<BookState> TYPE = new Type<>(id("observer_native_book_state_v1"));
        public static final StreamCodec<FriendlyByteBuf, BookState> CODEC = StreamCodec.of(
                ObserverBookScreenPayloads::writeState,
                ObserverBookScreenPayloads::readState
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record BookRelay(
            UUID targetId,
            int protocolVersion,
            long sequence,
            boolean open,
            String familyId,
            String variant,
            String screenClass,
            String title,
            int pageIndex,
            int pageCount,
            String pageText,
            String bookTitle,
            String author
    ) implements CustomPacketPayload {
        public static final Type<BookRelay> TYPE = new Type<>(id("observer_native_book_relay_v1"));
        public static final StreamCodec<FriendlyByteBuf, BookRelay> CODEC = StreamCodec.of(
                (buf, value) -> {
                    buf.writeUUID(value.targetId());
                    writeFields(
                            buf,
                            value.protocolVersion(),
                            value.sequence(),
                            value.open(),
                            value.familyId(),
                            value.variant(),
                            value.screenClass(),
                            value.title(),
                            value.pageIndex(),
                            value.pageCount(),
                            value.pageText(),
                            value.bookTitle(),
                            value.author()
                    );
                },
                buf -> {
                    UUID targetId = buf.readUUID();
                    BookState state = readState(buf);
                    return new BookRelay(
                            targetId,
                            state.protocolVersion(),
                            state.sequence(),
                            state.open(),
                            state.familyId(),
                            state.variant(),
                            state.screenClass(),
                            state.title(),
                            state.pageIndex(),
                            state.pageCount(),
                            state.pageText(),
                            state.bookTitle(),
                            state.author()
                    );
                }
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public static BookState closed(long sequence) {
        return new BookState(PROTOCOL_VERSION, sequence, false,
                ObserverNativeScreenPayloads.FAMILY_BOOK, "", "", "",
                0, 0, "", "", "");
    }

    private static void writeState(FriendlyByteBuf buf, BookState value) {
        writeFields(
                buf,
                value.protocolVersion(),
                value.sequence(),
                value.open(),
                value.familyId(),
                value.variant(),
                value.screenClass(),
                value.title(),
                value.pageIndex(),
                value.pageCount(),
                value.pageText(),
                value.bookTitle(),
                value.author()
        );
    }

    private static void writeFields(
            FriendlyByteBuf buf,
            int protocolVersion,
            long sequence,
            boolean open,
            String familyId,
            String variant,
            String screenClass,
            String title,
            int pageIndex,
            int pageCount,
            String pageText,
            String bookTitle,
            String author
    ) {
        buf.writeVarInt(protocolVersion);
        buf.writeLong(sequence);
        buf.writeBoolean(open);
        buf.writeUtf(familyId, MAX_METADATA_TEXT);
        buf.writeUtf(variant, MAX_METADATA_TEXT);
        buf.writeUtf(screenClass, MAX_METADATA_TEXT);
        buf.writeUtf(title, MAX_METADATA_TEXT);
        buf.writeVarInt(pageIndex);
        buf.writeVarInt(pageCount);
        buf.writeUtf(pageText, MAX_PAGE_TEXT);
        buf.writeUtf(bookTitle, MAX_METADATA_TEXT);
        buf.writeUtf(author, MAX_METADATA_TEXT);
    }

    private static BookState readState(FriendlyByteBuf buf) {
        return new BookState(
                buf.readVarInt(),
                buf.readLong(),
                buf.readBoolean(),
                buf.readUtf(MAX_METADATA_TEXT),
                buf.readUtf(MAX_METADATA_TEXT),
                buf.readUtf(MAX_METADATA_TEXT),
                buf.readUtf(MAX_METADATA_TEXT),
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readUtf(MAX_PAGE_TEXT),
                buf.readUtf(MAX_METADATA_TEXT),
                buf.readUtf(MAX_METADATA_TEXT)
        );
    }
}
