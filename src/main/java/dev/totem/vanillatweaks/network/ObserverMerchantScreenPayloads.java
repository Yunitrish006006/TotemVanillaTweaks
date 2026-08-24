package dev.totem.vanillatweaks.network;

import dev.totem.vanillatweaks.TotemVanillaTweaks;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Structured merchant-family transport for villager and wandering-trader screens. */
public final class ObserverMerchantScreenPayloads {
    public static final int PROTOCOL_VERSION = 1;
    public static final String VARIANT_VANILLA = "vanilla_merchant";
    public static final int MAX_OFFERS = 128;
    private static final int MAX_TEXT = 256;

    private ObserverMerchantScreenPayloads() {
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(TotemVanillaTweaks.MOD_ID, path);
    }

    public record ItemState(String itemId, int count, int damage) {
    }

    public record OfferState(
            int index,
            ItemState costA,
            ItemState costB,
            ItemState result,
            int uses,
            int maxUses,
            int xp,
            boolean outOfStock
    ) {
    }

    public record MerchantState(
            int protocolVersion,
            long sequence,
            boolean open,
            String familyId,
            String variant,
            String screenClass,
            String title,
            int selectedOffer,
            int traderLevel,
            int traderXp,
            int futureTraderXp,
            boolean showProgressBar,
            boolean canRestock,
            List<OfferState> offers
    ) implements CustomPacketPayload {
        public static final Type<MerchantState> TYPE = new Type<>(id("observer_native_merchant_state_v1"));
        public static final StreamCodec<FriendlyByteBuf, MerchantState> CODEC = StreamCodec.of(
                ObserverMerchantScreenPayloads::writeState,
                ObserverMerchantScreenPayloads::readState
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record MerchantRelay(
            UUID targetId,
            int protocolVersion,
            long sequence,
            boolean open,
            String familyId,
            String variant,
            String screenClass,
            String title,
            int selectedOffer,
            int traderLevel,
            int traderXp,
            int futureTraderXp,
            boolean showProgressBar,
            boolean canRestock,
            List<OfferState> offers
    ) implements CustomPacketPayload {
        public static final Type<MerchantRelay> TYPE = new Type<>(id("observer_native_merchant_relay_v1"));
        public static final StreamCodec<FriendlyByteBuf, MerchantRelay> CODEC = StreamCodec.of(
                (buf, value) -> {
                    buf.writeUUID(value.targetId());
                    writeFields(buf, value.protocolVersion(), value.sequence(), value.open(), value.familyId(),
                            value.variant(), value.screenClass(), value.title(), value.selectedOffer(),
                            value.traderLevel(), value.traderXp(), value.futureTraderXp(), value.showProgressBar(),
                            value.canRestock(), value.offers());
                },
                buf -> {
                    UUID targetId = buf.readUUID();
                    MerchantState state = readState(buf);
                    return new MerchantRelay(targetId, state.protocolVersion(), state.sequence(), state.open(),
                            state.familyId(), state.variant(), state.screenClass(), state.title(), state.selectedOffer(),
                            state.traderLevel(), state.traderXp(), state.futureTraderXp(), state.showProgressBar(),
                            state.canRestock(), state.offers());
                }
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    private static void writeState(FriendlyByteBuf buf, MerchantState value) {
        writeFields(buf, value.protocolVersion(), value.sequence(), value.open(), value.familyId(), value.variant(),
                value.screenClass(), value.title(), value.selectedOffer(), value.traderLevel(), value.traderXp(),
                value.futureTraderXp(), value.showProgressBar(), value.canRestock(), value.offers());
    }

    private static void writeFields(FriendlyByteBuf buf, int protocolVersion, long sequence, boolean open,
                                    String familyId, String variant, String screenClass, String title,
                                    int selectedOffer, int traderLevel, int traderXp, int futureTraderXp,
                                    boolean showProgressBar, boolean canRestock, List<OfferState> offers) {
        buf.writeVarInt(protocolVersion);
        buf.writeLong(sequence);
        buf.writeBoolean(open);
        buf.writeUtf(familyId, MAX_TEXT);
        buf.writeUtf(variant, MAX_TEXT);
        buf.writeUtf(screenClass, MAX_TEXT);
        buf.writeUtf(title, MAX_TEXT);
        buf.writeVarInt(selectedOffer);
        buf.writeVarInt(traderLevel);
        buf.writeVarInt(traderXp);
        buf.writeVarInt(futureTraderXp);
        buf.writeBoolean(showProgressBar);
        buf.writeBoolean(canRestock);
        int count = Math.min(offers.size(), MAX_OFFERS);
        buf.writeVarInt(count);
        for (int i = 0; i < count; i++) {
            OfferState offer = offers.get(i);
            buf.writeVarInt(offer.index());
            writeItem(buf, offer.costA());
            writeItem(buf, offer.costB());
            writeItem(buf, offer.result());
            buf.writeVarInt(offer.uses());
            buf.writeVarInt(offer.maxUses());
            buf.writeVarInt(offer.xp());
            buf.writeBoolean(offer.outOfStock());
        }
    }

    private static MerchantState readState(FriendlyByteBuf buf) {
        int protocolVersion = buf.readVarInt();
        long sequence = buf.readLong();
        boolean open = buf.readBoolean();
        String familyId = buf.readUtf(MAX_TEXT);
        String variant = buf.readUtf(MAX_TEXT);
        String screenClass = buf.readUtf(MAX_TEXT);
        String title = buf.readUtf(MAX_TEXT);
        int selectedOffer = buf.readVarInt();
        int traderLevel = buf.readVarInt();
        int traderXp = buf.readVarInt();
        int futureTraderXp = buf.readVarInt();
        boolean showProgressBar = buf.readBoolean();
        boolean canRestock = buf.readBoolean();
        int offerCount = buf.readVarInt();
        if (offerCount < 0 || offerCount > MAX_OFFERS) {
            throw new IllegalArgumentException("Observer merchant offer count out of range: " + offerCount);
        }
        List<OfferState> offers = new ArrayList<>(offerCount);
        for (int i = 0; i < offerCount; i++) {
            offers.add(new OfferState(buf.readVarInt(), readItem(buf), readItem(buf), readItem(buf),
                    buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readBoolean()));
        }
        return new MerchantState(protocolVersion, sequence, open, familyId, variant, screenClass, title,
                selectedOffer, traderLevel, traderXp, futureTraderXp, showProgressBar, canRestock,
                List.copyOf(offers));
    }

    private static void writeItem(FriendlyByteBuf buf, ItemState item) {
        buf.writeUtf(item.itemId(), MAX_TEXT);
        buf.writeVarInt(item.count());
        buf.writeVarInt(item.damage());
    }

    private static ItemState readItem(FriendlyByteBuf buf) {
        return new ItemState(buf.readUtf(MAX_TEXT), buf.readVarInt(), buf.readVarInt());
    }
}
