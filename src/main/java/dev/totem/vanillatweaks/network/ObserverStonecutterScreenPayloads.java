package dev.totem.vanillatweaks.network;

import dev.totem.vanillatweaks.TotemVanillaTweaks;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Stonecutter semantic transport: recipe selector state plus menu slots. */
public final class ObserverStonecutterScreenPayloads {
    public static final int PROTOCOL_VERSION = 2;
    public static final long CAPABILITY = 1L << 13;
    public static final String FAMILY_ID = "stonecutter";
    public static final String SCREEN_CLASS = "net.minecraft.client.gui.screens.inventory.StonecutterScreen";
    private static final int MAX_TEXT = 256;
    public static final int MAX_RECIPES = 512;

    private ObserverStonecutterScreenPayloads() {}

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(TotemVanillaTweaks.MOD_ID, path);
    }

    public record RecipeState(int index, String recipeId, String outputItemId, int outputCount, int outputDamage) {}

    public record StonecutterState(int protocolVersion, long sequence, boolean open, String familyId,
                                   String screenClass, String title, int selectedRecipeIndex, int recipeCount,
                                   int startIndex, float scrollOffset, boolean displayRecipes,
                                   boolean hasInputItem, boolean resultAvailable, List<RecipeState> recipes,
                                   List<ObserverNativeScreenPayloads.SlotState> slots) implements CustomPacketPayload {
        public static final Type<StonecutterState> TYPE = new Type<>(id("observer_stonecutter_state_v2"));
        public static final StreamCodec<FriendlyByteBuf, StonecutterState> CODEC = StreamCodec.of(
                ObserverStonecutterScreenPayloads::writeState, ObserverStonecutterScreenPayloads::readState);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record StonecutterRelay(UUID targetId, int protocolVersion, long sequence, boolean open, String familyId,
                                   String screenClass, String title, int selectedRecipeIndex, int recipeCount,
                                   int startIndex, float scrollOffset, boolean displayRecipes,
                                   boolean hasInputItem, boolean resultAvailable, List<RecipeState> recipes,
                                   List<ObserverNativeScreenPayloads.SlotState> slots) implements CustomPacketPayload {
        public static final Type<StonecutterRelay> TYPE = new Type<>(id("observer_stonecutter_relay_v2"));
        public static final StreamCodec<FriendlyByteBuf, StonecutterRelay> CODEC = StreamCodec.of(
                (buf, value) -> {
                    buf.writeUUID(value.targetId());
                    writeFields(buf, value.protocolVersion(), value.sequence(), value.open(), value.familyId(),
                            value.screenClass(), value.title(), value.selectedRecipeIndex(), value.recipeCount(),
                            value.startIndex(), value.scrollOffset(), value.displayRecipes(), value.hasInputItem(),
                            value.resultAvailable(), value.recipes(), value.slots());
                },
                buf -> relay(buf.readUUID(), readState(buf)));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public static StonecutterState closed(long sequence) {
        return new StonecutterState(PROTOCOL_VERSION, sequence, false, FAMILY_ID, "", "", -1, 0,
                0, 0.0F, false, false, false, List.of(), List.of());
    }

    public static StonecutterRelay relay(UUID targetId, StonecutterState state) {
        return new StonecutterRelay(targetId, state.protocolVersion(), state.sequence(), state.open(), state.familyId(),
                state.screenClass(), state.title(), state.selectedRecipeIndex(), state.recipeCount(),
                state.startIndex(), state.scrollOffset(), state.displayRecipes(), state.hasInputItem(),
                state.resultAvailable(), state.recipes(), state.slots());
    }

    private static void writeState(FriendlyByteBuf buf, StonecutterState value) {
        writeFields(buf, value.protocolVersion(), value.sequence(), value.open(), value.familyId(), value.screenClass(),
                value.title(), value.selectedRecipeIndex(), value.recipeCount(), value.startIndex(),
                value.scrollOffset(), value.displayRecipes(), value.hasInputItem(), value.resultAvailable(),
                value.recipes(), value.slots());
    }

    private static void writeFields(FriendlyByteBuf buf, int protocolVersion, long sequence, boolean open,
                                    String familyId, String screenClass, String title, int selectedRecipeIndex,
                                    int recipeCount, int startIndex, float scrollOffset, boolean displayRecipes,
                                    boolean hasInputItem, boolean resultAvailable, List<RecipeState> recipes,
                                    List<ObserverNativeScreenPayloads.SlotState> slots) {
        buf.writeVarInt(protocolVersion);
        buf.writeLong(sequence);
        buf.writeBoolean(open);
        buf.writeUtf(familyId, MAX_TEXT);
        buf.writeUtf(screenClass, MAX_TEXT);
        buf.writeUtf(title, MAX_TEXT);
        buf.writeVarInt(selectedRecipeIndex);
        buf.writeVarInt(recipeCount);
        buf.writeVarInt(startIndex);
        buf.writeFloat(scrollOffset);
        buf.writeBoolean(displayRecipes);
        buf.writeBoolean(hasInputItem);
        buf.writeBoolean(resultAvailable);
        int recipeSize = Math.min(recipes.size(), MAX_RECIPES);
        buf.writeVarInt(recipeSize);
        for (int i = 0; i < recipeSize; i++) {
            RecipeState recipe = recipes.get(i);
            buf.writeVarInt(recipe.index());
            buf.writeUtf(recipe.recipeId(), MAX_TEXT);
            buf.writeUtf(recipe.outputItemId(), MAX_TEXT);
            buf.writeVarInt(recipe.outputCount());
            buf.writeVarInt(recipe.outputDamage());
        }
        int count = Math.min(slots.size(), ObserverNativeScreenPayloads.MAX_SLOTS);
        buf.writeVarInt(count);
        for (int i = 0; i < count; i++) {
            var slot = slots.get(i);
            buf.writeVarInt(slot.index());
            buf.writeVarInt(slot.x());
            buf.writeVarInt(slot.y());
            buf.writeUtf(slot.itemId(), MAX_TEXT);
            buf.writeVarInt(slot.count());
            buf.writeVarInt(slot.damage());
        }
    }

    private static StonecutterState readState(FriendlyByteBuf buf) {
        int protocolVersion = buf.readVarInt();
        long sequence = buf.readLong();
        boolean open = buf.readBoolean();
        String familyId = buf.readUtf(MAX_TEXT);
        String screenClass = buf.readUtf(MAX_TEXT);
        String title = buf.readUtf(MAX_TEXT);
        int selectedRecipeIndex = buf.readVarInt();
        int recipeCount = buf.readVarInt();
        int startIndex = buf.readVarInt();
        float scrollOffset = buf.readFloat();
        boolean displayRecipes = buf.readBoolean();
        boolean hasInputItem = buf.readBoolean();
        boolean resultAvailable = buf.readBoolean();
        int recipeSize = buf.readVarInt();
        if (recipeSize < 0 || recipeSize > MAX_RECIPES) {
            throw new IllegalArgumentException("Observer stonecutter recipe count out of range: " + recipeSize);
        }
        List<RecipeState> recipes = new ArrayList<>(recipeSize);
        for (int i = 0; i < recipeSize; i++) {
            recipes.add(new RecipeState(buf.readVarInt(), buf.readUtf(MAX_TEXT), buf.readUtf(MAX_TEXT),
                    buf.readVarInt(), buf.readVarInt()));
        }
        int slotCount = buf.readVarInt();
        if (slotCount < 0 || slotCount > ObserverNativeScreenPayloads.MAX_SLOTS) {
            throw new IllegalArgumentException("Observer stonecutter slot count out of range: " + slotCount);
        }
        List<ObserverNativeScreenPayloads.SlotState> slots = new ArrayList<>(slotCount);
        for (int i = 0; i < slotCount; i++) {
            slots.add(new ObserverNativeScreenPayloads.SlotState(buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                    buf.readUtf(MAX_TEXT), buf.readVarInt(), buf.readVarInt()));
        }
        return new StonecutterState(protocolVersion, sequence, open, familyId, screenClass, title,
                selectedRecipeIndex, recipeCount, startIndex, scrollOffset, displayRecipes, hasInputItem,
                resultAvailable, List.copyOf(recipes), List.copyOf(slots));
    }
}
