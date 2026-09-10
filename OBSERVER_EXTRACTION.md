# TotemObserver extraction contract

Observer View has outgrown the VanillaTweaks domain. This branch establishes a migration seam so the runtime can be extracted into a dedicated `TotemObserver` module without changing the public observation model.

## Target ownership

- **TotemCore** owns stable Observer contracts only: provider discovery, bounded snapshots, read-only handles/context, remote cursor contracts and compatible protocol primitives.
- **TotemObserver** owns `/observeui`, permission checks, session lifecycle, spectator camera coordination, capability negotiation, semantic transport, vanilla Screen adapters, HUD/world observation, privacy redaction, metadata-only fallback and Observer E2E coverage.
- **Owning feature modules** keep their own production Screen provider and module-present tests. A module must not depend on TotemObserver merely to expose a provider; it depends on TotemCore contracts.
- **TotemVanillaTweaks** returns to vanilla gameplay tweaks: sorting, bookshelf/lectern rules, concrete-powder behavior, furnace/hopper XP and other feature-local behavior.

## Extraction invariants

1. Observer remains server-authoritative, spectator-only for the observing player, read-only, monotonic, privacy-redacted and framebuffer-free.
2. No screenshot/framebuffer/video fallback may be reintroduced during migration.
3. Unsupported or unnegotiated Screen families degrade to bounded metadata instead of failing the whole gameplay/HUD session.
4. A feature module owns reconstruction of its production Screen. TotemObserver coordinates transport and must not draw lookalike copies of another module's UI.
5. Normal provider onboarding must become data/provider-driven. Adding a new owning module must not require a module-ID-specific branch in TotemObserver.
6. The migration must preserve the current three-JVM Dedicated Server + Target Client + Observer Client E2E path before the old VanillaTweaks-owned runtime is removed.

## Migration sequence

### Phase 1 — bootstrap seam

- isolate Observer server/network registration behind `ObserverServerRuntime`;
- isolate Observer client registration behind `ObserverClientRuntime`;
- keep VanillaTweaks payload registration feature-local.

### Phase 2 — generic provider registry

- remove the centralized owned-family/variant table and module-specific capability switch;
- derive negotiated owning-module families from provider identity/protocol data with bounded validation;
- retain explicit protocol compatibility rules where a wire contract requires them, without importing owning-module implementation code.

### Phase 3 — dedicated module

- create `TotemObserver` with its own Fabric mod id, Gradle build, client/server entrypoints, mixin configs, tests and release workflow;
- move Observer runtime, payloads, vanilla adapters, resources and E2E infrastructure without changing wire semantics in the same step;
- keep TotemCore as the only hard Totem dependency.

### Phase 4 — remove VanillaTweaks ownership

- delete Observer sources, mixins, resources, integration JAR plumbing and E2E configuration from TotemVanillaTweaks;
- remove the two temporary facade calls;
- update README/manual/release metadata so Observer is no longer presented as a VanillaTweaks feature.

### Phase 5 — workspace/release cutover

- register TotemObserver in TotemWorkspace only after its repository/build evidence exists;
- classify feature-module providers as Observer provider contracts, not hard feature dependencies;
- validate standalone VanillaTweaks, standalone Observer + Core, and Observer with each supported owner module before release.

## Current branch scope

This branch implements Phase 1 only. It intentionally does not change packet IDs, protocol versions, privacy semantics, provider ownership or release metadata.
