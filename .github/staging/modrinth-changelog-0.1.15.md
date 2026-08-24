## TotemVanillaTweaks 0.1.15 Beta

### Observer View

- Added privileged Spectator Observer View with `/observeui <player>` and `/observeui stop`.
- Added live Target gameplay framebuffer relay through the dedicated server to an Observer client.
- Normal first-person world rendering now streams even when the Target has no GUI open; HUD and opened screens are included as part of the current framebuffer implementation.
- Added authoritative session lifecycle, permission checks, Target capture enable/disable, Stop handling, and cleanup when either side disconnects or leaves the required state.
- Added bounded client reconnect handling for transient dedicated-server connection failures.

### Verification

- Added a real three-JVM E2E gate: Dedicated Server JVM + Target Minecraft Client JVM + Observer Minecraft Client JVM.
- E2E verifies a Target gameplay framebuffer is captured with no test GUI open, relayed through production networking, reassembled on the Observer JVM, rendered by the mirror, then stopped cleanly with `CaptureControl(false)` and server cleanup.
- Server GameTests and Client GameTests remain part of the release gate.

### Architecture note

The framebuffer/image relay is an interim implementation intended to make Observer View usable now. The permanent target is protocol-native observation: no screenshots, video frames, or full framebuffer payloads. Future versions will transmit structured world/player/HUD/UI/event state and reconstruct the view locally on the Observer client.

### Requirements

- Minecraft 26.2
- Fabric Loader 0.19.3+
- Fabric API 0.154.2+26.2
- Java 25+
- TotemCore >=0.7.0 <0.8.0 (0.7.11 recommended)
