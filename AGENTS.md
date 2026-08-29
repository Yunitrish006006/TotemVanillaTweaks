# TotemVanillaTweaks instructions

## Observer UI architecture

- Vanilla families must reconstruct with the matching Minecraft `Screen` and
  `Menu`, call the vanilla renderer, and remain read-only.
- Totem collaboration UIs must be supplied by the owning module through the
  TotemCore Observer provider contract. Never create a hand-drawn replacement
  Screen or renderer lookalike.
- TotemVanillaTweaks owns session/capability negotiation, server validation,
  semantic relay, target display identity, sequence cleanup, generic priority
  suppression and the bounded remote cursor transport only.
- Remain permanently framebuffer-free. Never transmit screenshots, framebuffer,
  video, secrets, tokens, passwords, API keys, prompts or unsent chat/commands.
- Treat anvil rename text, writable-book pages/signing fields and sign-editor
  lines as unsent private input: reconstruct the native Screen with those draft
  fields blank, and test the redaction with sentinel values.
- All observer screens are read-only by contract; Escape only stops observing.
- Every family requires unit tests, integrated loopback, Client GameTest visual
  evidence, dedicated three-JVM E2E and Production Runtime validation.
- Provider capture/create and all handle methods are client-thread-only. Never
  call them directly from the GameTest thread.
