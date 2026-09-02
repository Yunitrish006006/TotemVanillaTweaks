## TotemVanillaTweaks 0.1.22

### Nexus Observer update

- Pins the production integration gate to TotemNexus 0.3.13.
- Reconstructs the ordinary Nexus compass list, Nexus map marker view, and
  management-only screen through the module-owned production Screen.
- Relays bounded map zoom, pan, and selected-destination semantics without map
  pixels, framebuffer capture, or renderer duplication.
- Extends the dedicated three-JVM E2E sequence across compass, map,
  management, friends, and registration variants.
- Keeps Observer input, packets, scrolling, widgets, and lifecycle mutation
  suppressed except for the explicit stop-observing action.

Minecraft 26.2 · Fabric · Java 25 · requires TotemCore 0.7.16 or newer within
the 0.7.x compatibility line. Nexus integration targets TotemNexus 0.3.13.
