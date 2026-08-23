package dev.totem.vanillatweaks.e2e;

import dev.totem.vanillatweaks.client.ObserverUiClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** E2E-only instrumentation that persists the exact PNG reconstructed on the Observer JVM. */
@Mixin(ObserverUiClient.class)
abstract class ObserverUiClientE2eMixin {
    @ModifyArg(
            method = "acceptFrameChunk",
            at = @At(
                    value = "INVOKE",
                    target = "Ldev/totem/vanillatweaks/client/ObserverUiClient;installFrameTexture([B)V"
            ),
            index = 0
    )
    private static byte[] totemVanillaTweaks$captureRelayedFrame(byte[] png) {
        try {
            Path output = ObserverE2eCommon.resultsDir().resolve("observer-relayed-frame.png");
            Files.createDirectories(output.getParent());
            Files.write(output, png);
            ObserverE2eCommon.marker(
                    "observer-relayed-frame-saved.txt",
                    "Observer JVM persisted the exact reassembled FrameRelay PNG before DynamicTexture install; bytes="
                            + png.length + ".\n"
            );
        } catch (IOException error) {
            ObserverE2eCommon.fail("observer", "Failed to persist relayed framebuffer bytes: " + error);
        }
        return png;
    }
}
