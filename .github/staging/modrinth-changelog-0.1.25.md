## TotemVanillaTweaks 0.1.25

- Publishes the canonical Totem ID migration with its one-way legacy
  advancement migration layer retained.
- Rebuilds the complete Observer integration bundle against Nexus 0.3.16,
  whose legacy gamerules now register before the registry freezes and only
  migrate persisted values after server startup.
- Keeps the exact released TotemCore 0.7.18 and owning-module lockstep bundle
  used by the release Client GameTest.

Minecraft 26.2 · Fabric · Java 25 · requires TotemCore 0.7.18 or newer within
the 0.7.x compatibility line.
