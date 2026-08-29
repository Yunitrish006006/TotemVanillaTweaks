package dev.totem.vanillatweaks.client;

import com.mojang.authlib.GameProfile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import java.util.UUID;

/** Resolves the observed player's real client entity, or a skin-capable profile projection. */
public final class ObserverObservedPlayerIdentity {
    private static UUID cachedId;
    private static Object cachedLevel;
    private static RemotePlayer cachedProjection;

    private ObserverObservedPlayerIdentity() { }

    public static LivingEntity resolve(LivingEntity fallback) {
        Minecraft minecraft = Minecraft.getInstance();
        UUID targetId = ObserverNativeClient.observerTargetId();
        if (targetId == null || minecraft.level == null) return fallback;
        Player tracked = minecraft.level.getPlayerByUUID(targetId);
        if (tracked != null) return tracked;
        if (!targetId.equals(cachedId) || cachedLevel != minecraft.level || cachedProjection == null) {
            String targetName = ObserverNativeClient.observerTargetName();
            if (targetName == null || targetName.isBlank()) targetName = targetId.toString();
            cachedProjection = new RemotePlayer(minecraft.level, new GameProfile(targetId, targetName));
            // A projection is deliberately not added to the Observer's ClientLevel, but vanilla
            // InventoryScreen still asks its entity renderer for an assigned id. Real level
            // entities use positive ids, so a stable non-zero negative id keeps this detached
            // skin projection renderable without competing with tracked entities.
            cachedProjection.setId(-1 - (targetId.hashCode() & Integer.MAX_VALUE));
            cachedId = targetId;
            cachedLevel = minecraft.level;
        }
        return cachedProjection;
    }
}
