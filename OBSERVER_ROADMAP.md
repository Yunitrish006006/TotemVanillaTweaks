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

## Migration phases

### Phase 1 — structured-state side channel (in progress)

- Introduce Observer protocol v1 as separate payload types so the existing framebuffer transport remains removable.
- Negotiate support through Fabric payload capability checks on both Target and Observer clients.
- Relay Target camera/HUD state as structured values: yaw, pitch, health, max health, food, saturation and key movement/use flags.
- Keep the existing framebuffer path active as a compatibility fallback while the structured channel is proven in CI.

### Phase 2 — native gameplay rendering

- Use the existing server-authoritative `observer.setCamera(target)` relationship for world/chunk/entity rendering.
- Stop sending gameplay PNG frames when no Target GUI is open and both clients support protocol v1.
- Render Target HUD state locally on the Observer client from protocol data.
- Keep framebuffer only for unsupported GUI states during this transition phase.

### Phase 3 — structured GUI/container replication

- Replace GUI screenshot fallback with versioned screen/container/menu/cursor protocol data.
- Reconstruct supported vanilla screens locally on the Observer client.
- Add explicit capability negotiation for screen families and protocol extensions.

### Phase 4 — remove framebuffer transport

- Remove normal-use framebuffer capture, PNG encoding, chunking and texture relay.
- Retain image transport only as an explicitly isolated diagnostic mechanism if one is still useful, disabled by default.

## Migration rule

New Observer features should avoid making the framebuffer relay harder to remove. Session management, authorization, lifecycle, capability negotiation and transport framing should remain independent from image-specific payloads.

When the structured protocol reaches feature parity, remove framebuffer capture/PNG encoding/frame relay rather than retaining it as the normal transport path.

## Success criterion

Observer View is considered architecturally complete only when an Observer can reconstruct the required player/world/UI experience **without receiving full-screen image frames from the Target client**.
