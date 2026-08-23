package dev.totem.vanillatweaks.e2e.mixin;

import dev.totem.vanillatweaks.client.ObserverUiClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
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
        Path results = Path.of(System.getProperty(
                "totem.observer.e2e.results",
                "build/e2e/results"
        )).toAbsolutePath();
        try {
            Files.createDirectories(results);
            Files.write(results.resolve("observer-relayed-frame.png"), png);
            Files.writeString(
                    results.resolve("observer-relayed-frame-saved.txt"),
                    "Observer JVM persisted the exact reassembled FrameRelay PNG before DynamicTexture install; bytes="
                            + png.length + ".\n",
                    StandardCharsets.UTF_8
            );
        } catch (IOException error) {
            try {
                Files.createDirectories(results);
                Files.writeString(
                        results.resolve("failure-observer.txt"),
                        "Failed to persist relayed framebuffer bytes: " + error + "\n",
                        StandardCharsets.UTF_8
                );
            } catch (IOException ignored) {
                // The main E2E timeout/logging path will surface the failure if even the marker cannot be written.
            }
        }
        return png;
    }
}
