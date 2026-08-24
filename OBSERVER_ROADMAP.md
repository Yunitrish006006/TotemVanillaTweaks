# Observer View Roadmap

## Permanent architecture goal

The current framebuffer relay is an **interim compatibility implementation only**. It exists so Observer View can become functional and testable end-to-end before the final protocol is designed.

The permanent target architecture is **protocol-native observation with no full-screen image/framebuffer streaming**.

### Non-negotiable end state

- Do **not** capture the target player's full framebuffer for transport.
- Do **not** encode whole-screen screenshots or video frames for transport.
- Do **not** treat PNG/JPEG/video/framebuffer relay as the final Observer protocol.
- Transmit only structured game/UI state required to reconstruct the observed view on the Observer client.
- Reconstruct and render the observed state locally on the Observer client using Minecraft-native rendering and protocol data.

### Candidate protocol data

The final protocol may carry structured data such as:

- player transform, camera transform, dimension and view state;
- nearby entities and relevant entity state;
- chunk/block state and incremental block updates needed by the observer renderer;
- held item, equipment, health, hunger, effects and HUD state;
- active screen/container type and synchronized menu state;
- cursor/selection/interaction state when required;
- discrete game events and deltas instead of repeated full snapshots wherever practical.

The exact schema should be versioned and capability-negotiated rather than coupled directly to internal client classes.

## Current milestone

For now, prioritize a reliable working Observer View using the existing framebuffer relay:

1. Dedicated Server + Target Client JVM + Observer Client JVM must remain reproducibly green.
2. Normal gameplay framebuffer observation must work with no GUI open.
3. Observer Mirror rendering, Stop, CaptureControl(false), disconnect/reconnect, and cleanup must remain correct.
4. CI artifacts should continue proving that actual Target gameplay reaches the Observer JVM.

Do not block near-term usability on the protocol-native rewrite.

## Migration rule

New Observer features should avoid making the framebuffer relay harder to remove. Where practical, keep session management, authorization, lifecycle, capability negotiation, and transport framing independent from the image-specific payloads.

When the structured protocol reaches feature parity, remove framebuffer capture/PNG encoding/frame relay rather than retaining it as the normal transport path. A temporary diagnostic fallback may exist only if explicitly isolated and disabled by default.

## Success criterion

Observer View is considered architecturally complete only when an Observer can reconstruct the required player/world/UI experience **without receiving full-screen image frames from the Target client**.
