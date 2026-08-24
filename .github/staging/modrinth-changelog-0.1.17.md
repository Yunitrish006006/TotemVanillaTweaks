## TotemVanillaTweaks 0.1.17 Beta

### Observer View — protocol-native rewrite

- Replaced the old framebuffer/PNG relay with protocol-native observation.
- Observer world rendering now uses Minecraft's native spectator camera and normal chunk/entity synchronization.
- Added locally reconstructed Target HUD state including health, food, saturation, experience and selected hotbar slot.
- Removed production `FrameChunk`, `FrameRelay`, `CaptureControl`, screenshot transport and dynamic frame textures.
- Incompatible Observer clients are rejected instead of falling back to image transport.

### Semantic screen capabilities

- Observer gameplay/session protocol is now v4 with semantic screen protocol v2.
- Added negotiated per-screen-family capability masks.
- Added `container_slots` semantic reconstruction for supported container screens.
- Added dedicated `furnace` semantic reconstruction for furnace, smoker and blast-furnace style screens, including slots, cook progress, fuel progress and lit state.
- Unsupported or unnegotiated screens use a local metadata-only placeholder; Target pixels are never transmitted.

### Validation and runtime fixes

- Added/expanded Server GameTests, Client GameTests, production-runtime tests and a real Dedicated Server + Target Client + Observer Client three-JVM E2E.
- CI now enforces a framebuffer-free production source invariant.
- Fixed client/server side separation for concrete-powder hardening discovered by Observer E2E coverage.
- Keeps the Minecraft 26.2 production namespace/remap fix introduced in 0.1.16.

Minecraft 26.2 · Fabric · Java 25 · requires Fabric API and TotemCore `>=0.7.0 <0.8.0`.
