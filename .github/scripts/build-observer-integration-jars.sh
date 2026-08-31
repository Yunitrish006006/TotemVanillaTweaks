#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
lockstep_root="${OBSERVER_LOCKSTEP_ROOT:-$repo_root/.lockstep}"
core="$lockstep_root/TotemCore"
core_jar="$core/build/libs/totem-core-0.7.14.jar"
wrapper="$core/gradlew"

assert_checkout() {
  local repo="$1" commit="$2" version="$3"
  test "$(git -C "$lockstep_root/$repo" rev-parse HEAD)" = "$commit"
  test "$(sed -n 's/^mod_version=//p' "$lockstep_root/$repo/gradle.properties")" = "$version"
}

assert_production_jar() {
  local archive="$1" mod_id="$2" version="$3"
  local entries

  test -f "$archive"
  case "$archive" in
    */build/libs/*.jar) ;;
    *)
      printf 'Expected a production JAR under build/libs, got %s\n' "$archive" >&2
      return 1
      ;;
  esac
  case "$(basename "$archive")" in
    *-dev.jar|*-sources.jar)
      printf 'Refusing non-production integration artifact %s\n' "$archive" >&2
      return 1
      ;;
  esac

  entries="$(jar tf "$archive")"
  grep -Fxq 'fabric.mod.json' <<< "$entries"
  if grep -Eq '/(gametest|integrationGametest|e2e)/' <<< "$entries"; then
    printf 'Production integration artifact contains test-only classes: %s\n' "$archive" >&2
    return 1
  fi
  unzip -p "$archive" fabric.mod.json \
    | jq -e --arg mod_id "$mod_id" --arg version "$version" \
        '.id == $mod_id and .version == $version' >/dev/null
}

assert_checkout TotemCore 8407f3ad58c21db03758242a2dea552364b08963 0.7.14
assert_checkout TotemExcavation 6b54011195b81ec9a9a09146d162ba303ebd8ee4 0.1.8
assert_checkout TotemRemnant a3f9231b50c6c55f03a8c145e80644bd6cff7021 0.2.16
assert_checkout TotemAutomata b007eeb0ef0417804fe2abb25524842480419ae1 0.1.18
assert_checkout TotemNexus a2e9a78ae106246999699862df3fb85d4fed3520 0.3.8
assert_checkout TotemVillagers c4d37815c45b86f102939ec5e43b171692a406e0 0.1.33
assert_checkout TotemLocksmith c89d380b88b10a3f0f55044e04266bc8ada70357 0.1.6

chmod +x "$wrapper"
"$wrapper" -p "$core" jar --no-daemon --stacktrace
assert_production_jar "$core_jar" totem-core 0.7.14

"$wrapper" -p "$lockstep_root/TotemExcavation" \
  -PtotemCoreJar="$core_jar" jar --no-daemon --stacktrace
excavation_jar="$lockstep_root/TotemExcavation/build/libs/totem-excavation-0.1.8.jar"
assert_production_jar "$excavation_jar" totem-excavation 0.1.8

"$wrapper" -p "$lockstep_root/TotemRemnant" \
  -PtotemCoreJar="$core_jar" remapJar --no-daemon --stacktrace
remnant_jar="$lockstep_root/TotemRemnant/build/libs/totem-remnant-0.2.16.jar"
assert_production_jar "$remnant_jar" totem-remnant 0.2.16

"$wrapper" -p "$lockstep_root/TotemAutomata" \
  -PtotemCoreJar="$core_jar" \
  -PtotemExcavationJar="$excavation_jar" \
  -PincludeTotemExcavationRuntime=false jar --no-daemon --stacktrace
assert_production_jar \
  "$lockstep_root/TotemAutomata/build/libs/totem-automata-0.1.18.jar" \
  totem-automata 0.1.18

"$wrapper" -p "$lockstep_root/TotemNexus" \
  -PtotemCoreJar="$core_jar" jar --no-daemon --stacktrace
assert_production_jar \
  "$lockstep_root/TotemNexus/build/libs/totem-nexus-0.3.8.jar" \
  totem-nexus 0.3.8

"$wrapper" -p "$lockstep_root/TotemVillagers" \
  -PtotemCoreJar="$core_jar" -PtotemRemnantJar="$remnant_jar" \
  jar --no-daemon --stacktrace
assert_production_jar \
  "$lockstep_root/TotemVillagers/build/libs/totem-villagers-0.1.33.jar" \
  totem-villagers 0.1.33

"$wrapper" -p "$lockstep_root/TotemLocksmith" \
  -PtotemCoreJar="$core_jar" jar --no-daemon --stacktrace
assert_production_jar \
  "$lockstep_root/TotemLocksmith/build/libs/totem-locksmith-0.1.6.jar" \
  totem-locksmith 0.1.6
