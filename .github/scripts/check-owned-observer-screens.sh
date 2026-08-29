#!/usr/bin/env bash
set -euo pipefail

root="${1:-.}"
clients="$root/src/main/java/dev/totem/vanillatweaks/client"

legacy_module_clients='Observer(RemnantBackpack|AutomataCopperGolem|Nexus|NexusDeathNodeAdmin|LocksmithManagement|VillagersWoodcutter)ScreenClient'
if rg -n "${legacy_module_clients}\.register\(\)" "$root/src/main/java/dev/totem/vanillatweaks/TotemVanillaTweaksClient.java"; then
  echo 'A duplicated module UI adapter is still registered.' >&2
  exit 1
fi
if rg -n 'class[[:space:]]+[A-Za-z0-9_]*(Mirror|Lookalike)Screen[[:space:]]+extends' "$clients"; then
  echo 'Supported Observer families still contain hand-drawn lookalike renderers.' >&2
  exit 1
fi

forbidden='Screenshot\.takeScreenshot|FrameChunk|FrameRelay|DynamicTexture|observer_frame_chunk|observer_frame_relay|observer_capture_control|glReadPixels|NativeImage\.writeToFile'
if rg -n "$forbidden" "$root/src/main"; then
  echo 'Observer production code is not framebuffer-free.' >&2
  exit 1
fi

for contract in ObserverReadOnlyScreen ObserverOwnedScreenCoordinator ObserverScreenProviders; do
  rg -q "$contract" "$root/src/main" || { echo "Missing owner-screen contract: $contract" >&2; exit 1; }
done

declare -A owner_screens=(
  [TotemRemnant]='BackpackScreen'
  [TotemAutomata]='CopperGolemMenuScreen'
  [TotemNexus]='NexusOwnedScreen'
  [TotemLocksmith]='LocksmithManagementScreen'
  [TotemVillagers]='WoodcutterScreen'
)
for module in "${!owner_screens[@]}"; do
  module_root="$root/.lockstep/$module"
  [[ -d "$module_root" ]] || continue
  rg -q 'totem:observer_screen_provider' "$module_root/src/main/resources/fabric.mod.json" \
    || { echo "$module lacks the TotemCore Observer provider entrypoint" >&2; exit 1; }
  rg -q "class[[:space:]]+${owner_screens[$module]}" \
    "$module_root/src/client" \
    || { echo "$module lacks its production owner Screen" >&2; exit 1; }
  rg -q 'ObserverReadOnlyScreen|NexusOwnedScreen' "$module_root/src/client" \
    || { echo "$module production Screen lacks the read-only Observer contract" >&2; exit 1; }
done
