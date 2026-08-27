#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
lockstep_root="${OBSERVER_LOCKSTEP_ROOT:-$repo_root/.lockstep}"
core="$lockstep_root/TotemCore"
core_jar="$core/build/libs/totem-core-0.7.11.jar"
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

assert_checkout TotemCore 82b21944b1e4865f5d34f13febc5049d936a636f 0.7.11
assert_checkout TotemExcavation 6b54011195b81ec9a9a09146d162ba303ebd8ee4 0.1.8
assert_checkout TotemRemnant a8eb55ae53f3c6488775467127bab4d972c52a49 0.2.15
assert_checkout TotemAutomata 59b80206768466a4ac96f89e1343849abaa82dd3 0.1.16
assert_checkout TotemNexus a1f00f4e70fcdbe9ee098c21ed0c997bdb130bcb 0.3.5
assert_checkout TotemVillagers d0d287e2df831a44b4b2cab28bbc98e396368cda 0.1.32
assert_checkout TotemLocksmith 9080ac2c37807b539c5d309fe833edb660834f3b 0.1.5

chmod +x "$wrapper"
"$wrapper" -p "$core" jar --no-daemon --stacktrace
assert_production_jar "$core_jar" totem-core 0.7.11

"$wrapper" -p "$lockstep_root/TotemExcavation" \
  -PtotemCoreJar="$core_jar" jar --no-daemon --stacktrace
excavation_jar="$lockstep_root/TotemExcavation/build/libs/totem-excavation-0.1.8.jar"
assert_production_jar "$excavation_jar" totem-excavation 0.1.8

"$wrapper" -p "$lockstep_root/TotemRemnant" \
  -PtotemCoreJar="$core_jar" remapJar --no-daemon --stacktrace
remnant_jar="$lockstep_root/TotemRemnant/build/libs/totem-remnant-0.2.15.jar"
assert_production_jar "$remnant_jar" totem-remnant 0.2.15

"$wrapper" -p "$lockstep_root/TotemAutomata" \
  -PtotemCoreJar="$core_jar" \
  -PtotemExcavationJar="$excavation_jar" \
  -PincludeTotemExcavationRuntime=false jar --no-daemon --stacktrace
assert_production_jar \
  "$lockstep_root/TotemAutomata/build/libs/totem-automata-0.1.16.jar" \
  totem-automata 0.1.16

"$wrapper" -p "$lockstep_root/TotemNexus" \
  -PtotemCoreJar="$core_jar" jar --no-daemon --stacktrace
assert_production_jar \
  "$lockstep_root/TotemNexus/build/libs/totem-nexus-0.3.5.jar" \
  totem-nexus 0.3.5

"$wrapper" -p "$lockstep_root/TotemVillagers" \
  -PtotemCoreJar="$core_jar" -PtotemRemnantJar="$remnant_jar" \
  jar --no-daemon --stacktrace
assert_production_jar \
  "$lockstep_root/TotemVillagers/build/libs/totem-villagers-0.1.32.jar" \
  totem-villagers 0.1.32

"$wrapper" -p "$lockstep_root/TotemLocksmith" \
  -PtotemCoreJar="$core_jar" jar --no-daemon --stacktrace
assert_production_jar \
  "$lockstep_root/TotemLocksmith/build/libs/totem-locksmith-0.1.5.jar" \
  totem-locksmith 0.1.5
