#!/usr/bin/env bash
set -euo pipefail

# Remove Observer production/runtime surfaces. Gameplay tweaks remain in VanillaTweaks.
rm -rf src/main/java/dev/totem/vanillatweaks/observer
find src/main/java/dev/totem/vanillatweaks/network -maxdepth 1 -type f -name 'Observer*.java' -delete
find src/main/java/dev/totem/vanillatweaks/client -maxdepth 1 -type f -name 'Observer*.java' -delete
find src/main/java/dev/totem/vanillatweaks/mixin -type f -name 'Observer*.java' -delete

# Observer-only vanilla Screen accessors. OptionsMixin and AbstractContainerScreenMixin are gameplay-owned.
for file in \
  AbstractContainerScreenMenuAccessor \
  AbstractMountInventoryScreenAccessor \
  AbstractRecipeBookScreenAccessor \
  AbstractSignEditScreenAccessor \
  AdvancementTabAccessor \
  AdvancementsScreenAccessor \
  AnvilScreenAccessor \
  BeaconScreenAccessor \
  BookEditScreenAccessor \
  BookSignScreenAccessor \
  BookViewScreenAccessor \
  ClientAdvancementsAccessor \
  LoomScreenAccessor \
  MerchantScreenAccessor \
  RecipeBookComponentAccessor \
  RecipeBookPageAccessor \
  StatsScreenAccessor \
  StatsScreenItemStatisticsListAccessor \
  StatsScreenStatisticsTabAccessor \
  StonecutterScreenAccessor; do
  rm -f "src/main/java/dev/totem/vanillatweaks/mixin/client/${file}.java"
done

# Validation ownership now lives in TotemObserver.
find src/test -type f -name 'Observer*.java' -delete 2>/dev/null || true
find src/gametest -type f -name 'Observer*.java' -delete 2>/dev/null || true
rm -rf src/integrationGametest src/e2e
rm -f OBSERVER_ROADMAP.md OBSERVER_EXTRACTION.md OBSERVER_SCREEN_GAP_AUDIT.md
find .github/scripts -maxdepth 1 -type f \( -iname '*observer*' -o -iname '*owned-screen*' \) -delete 2>/dev/null || true

python3 - <<'PY'
from pathlib import Path
import json

# Remove the extraction facades from VanillaTweaks initializers.
for rel in [
    'src/main/java/dev/totem/vanillatweaks/TotemVanillaTweaks.java',
    'src/main/java/dev/totem/vanillatweaks/TotemVanillaTweaksClient.java',
]:
    path = Path(rel)
    text = path.read_text()
    lines = []
    for line in text.splitlines():
        if 'ObserverServerRuntime' in line or 'ObserverClientRuntime' in line:
            continue
        lines.append(line)
    path.write_text('\n'.join(lines) + '\n')

# VanillaTweaks client mixins are now gameplay-only.
client_mixins = {
    'required': True,
    'minVersion': '0.8',
    'package': 'dev.totem.vanillatweaks.mixin.client',
    'compatibilityLevel': 'JAVA_25',
    'client': ['OptionsMixin', 'AbstractContainerScreenMixin'],
    'injectors': {'defaultRequire': 1},
}
Path('src/main/resources/totem-vanilla-tweaks.client.mixins.json').write_text(
    json.dumps(client_mixins, indent=2) + '\n'
)

# Keep the seven gameplay server GameTests and the one gameplay client runtime test.
gametest_path = Path('src/gametest/resources/fabric.mod.json')
gametest = json.loads(gametest_path.read_text())
gametest['entrypoints']['fabric-client-gametest'] = [
    value for value in gametest['entrypoints'].get('fabric-client-gametest', [])
    if value.endswith('SkeletonAmmoRuntimeClientGameTest')
]
gametest_path.write_text(json.dumps(gametest, indent=2) + '\n')

# Metadata no longer claims ownership of Observer View.
mod_path = Path('src/main/resources/fabric.mod.json')
mod = json.loads(mod_path.read_text())
mod['description'] = (
    'Vanilla-focused gameplay tweaks for the Totem ecosystem, including container sorting, '
    'bookshelf rules, concrete hardening, and furnace/hopper automation fixes.'
)
mod_path.write_text(json.dumps(mod, indent=2) + '\n')

# This is a behavior/ownership release: Observer View moved to TotemObserver.
props = Path('gradle.properties')
text = props.read_text()
text = text.replace(
    '# 0.1.27 prevents unsafe container sorting with vanilla menu eligibility and full preflight.\nmod_version=0.1.27',
    '# 0.1.28 removes the embedded Observer runtime; install TotemObserver for observation.\nmod_version=0.1.28'
)
props.write_text(text)
PY

cat > build.gradle <<'EOF'
plugins {
    id 'net.fabricmc.fabric-loom' version '1.17.12'
    id 'maven-publish'
}

version = project.mod_version
group = project.maven_group
base { archivesName = project.archives_base_name }

def totemCoreJar = file(providers.gradleProperty('totemCoreJar')
        .getOrElse('../TotemCore/build/libs/totem-core-0.7.18.jar'))

repositories { mavenCentral() }

dependencies {
    minecraft "com.mojang:minecraft:${project.minecraft_version}"
    implementation "net.fabricmc:fabric-loader:${project.loader_version}"
    implementation "net.fabricmc.fabric-api:fabric-api:${project.fabric_version}"
    implementation files(totemCoreJar)
    productionRuntimeMods "net.fabricmc.fabric-api:fabric-api:${project.fabric_version}"
    productionRuntimeMods files(totemCoreJar)
    testImplementation 'org.junit.jupiter:junit-jupiter:5.11.4'
    testRuntimeOnly 'org.junit.platform:junit-platform-launcher'
}

fabricApi {
    configureTests {
        createSourceSet = true
        modId = 'totem-vanilla-tweaks-gametest'
        enableGameTests = true
        enableClientGameTests = true
        eula = true
    }
}

tasks.withType(Test).configureEach { useJUnitPlatform() }

tasks.register('productionGametestModJar', Jar) {
    dependsOn(sourceSets.gametest.classesTaskName, sourceSets.gametest.processResourcesTaskName)
    archiveClassifier = 'production-gametest'
    from sourceSets.gametest.output
    preserveFileTimestamps = false
    reproducibleFileOrder = true
}

tasks.register('runProductionClientGameTest', net.fabricmc.loom.task.prod.ClientProductionRunTask) {
    dependsOn('productionGametestModJar')
    mods.from(tasks.named('productionGametestModJar').flatMap { it.archiveFile })
    jvmArgs.add('-Dfabric.client.gametest')
    jvmArgs.add('-Dfabric.client.gametest.disableNetworkSynchronizer=true')
    useXVFB = true
}

java {
    withSourcesJar()
    sourceCompatibility = JavaVersion.VERSION_25
    targetCompatibility = JavaVersion.VERSION_25
}

tasks.named('processResources', ProcessResources) {
    inputs.property 'version', project.version
    filesMatching('fabric.mod.json') { expand(version: project.version) }
    doLast {
        copy {
            from(file('src/main/resources/assets/totem/lang/zh_tw.json'))
            into(file("${destinationDir}/assets/totem/lang"))
            rename { 'zh_cn.json' }
        }
    }
}

tasks.named('jar', Jar) {
    preserveFileTimestamps = false
    reproducibleFileOrder = true
}
EOF

cat > README.md <<'EOF'
# TotemVanillaTweaks

TotemVanillaTweaks 收納不屬於單一大型功能的原版玩法調整。自 **0.1.28** 起，Observer View 已完整移至獨立的 **TotemObserver**；本模組不再註冊 `/observeui`、Observer session、semantic relay 或 Observer Screen adapters。

## 安裝

Client 與 Server 都放入：

1. Fabric API `0.154.2+26.2`
2. TotemCore `0.7.18`（`>=0.7.18 <0.8.0`）
3. TotemVanillaTweaks `0.1.28`

| 項目 | 需求 |
| --- | --- |
| Minecraft | 26.2 |
| Fabric Loader | 0.19.3+ |
| Java | 25+ |
| Fabric API | 0.154.2+26.2 |
| 必要 Totem 模組 | `totem-core >=0.7.18 <0.8.0` |

需要管理員 Spectator 觀察功能時，另外安裝 **TotemObserver**。TotemVanillaTweaks 與 TotemObserver 不互相承擔對方的功能 ownership。

## 容器整理

預設按鍵為滑鼠中鍵，可在「設定 → 按鍵綁定 → Totem Vanilla Tweaks」更改。

1. 開啟物品欄或容器 GUI。
2. 把游標移到要整理的一側。
3. 按滑鼠中鍵。

| 游標位置 | 整理目標 |
| --- | --- |
| 支援的原版 GUI 中，玩家物品欄一側 | 玩家主背包 |
| 原版箱子、漏斗、發射器／投擲器、界伏盒的容器一側 | 當前容器 |
| 單純玩家物品欄畫面 | 玩家主背包 |

整理會合併相同 Item 與 Data Components，再依穩定順序排列。玩家物品欄整理不移動快捷列、盔甲、副手或其他裝備欄。Server 會驗證目前 menu、使用權限與目的格限制後才寫入；工作台合成區、熔爐、鐵砧等功能格不支援通用整理，自訂 menu 也不由此功能處理。

## 講台配方

模組覆寫 `minecraft:lectern`：

```text
S S S
_ B _
_ S _
```

`S` 是任意木製半磚，`B` 是書；共 4 個半磚與 1 本書。

## 書櫃生存規則

- 移除原版普通書櫃工作台配方。
- 生存玩家物品欄中的普通書櫃轉換成每個 3 本書；空間不足的書安全掉落。
- 創造模式玩家不受物品欄轉換影響。
- 結構生成中的普通書櫃與空雕紋書櫃會成為裝有書本的雕紋書櫃。

## 混凝土粉末

16 色混凝土粉末 ItemEntity 實際接觸水時原地硬化成對應混凝土，保留數量與 Data Components；僅靠近水或下雨不觸發，流動水與水源都可生效。

## 熔爐與漏斗經驗

漏斗從熔爐、煙燻爐或高爐結果槽取出成品時，應得配方經驗會在漏斗附近釋放，並清除已結算 recipe bookkeeping，避免自動化吞掉經驗或重複領取。

## 模組邊界

- Observer session、semantic relay、vanilla Screen adapters 與 `/observeui`：**TotemObserver**。
- 背包與 Shulker／Bundle 巢狀安全：**TotemRemnant**。
- 缽、燧石、煉金材料與煉藥鍋：**TotemAlchemy**。
- 雕紋書櫃加權附魔力：**TotemEnchanting**。

VanillaTweaks 不直接依賴這些功能模組。

## 開發與驗證

```bash
./gradlew build
```

CI 驗證 Java 25 compile/test、Server GameTests、gameplay Client GameTest，以及 built-artifact production-namespace Client GameTest。Observer 的 protocol、cross-module、GameTest 與三 JVM E2E 驗證由 TotemObserver 自己負責。所有權與 gameplay 驗證契約見 [`EXTRACTION.md`](EXTRACTION.md)。
EOF

# Hard ownership gates before any commit.
if grep -R -n -E 'Observer(Server|Client)Runtime|/observeui|dev\.totem\.vanillatweaks\.observer' src/main; then
  echo 'Embedded Observer runtime reference remains in VanillaTweaks production source.' >&2
  exit 1
fi
if find src/main/java -type f -name 'Observer*.java' | grep -q .; then
  echo 'Observer production classes remain in VanillaTweaks.' >&2
  find src/main/java -type f -name 'Observer*.java' >&2
  exit 1
fi
if grep -R -n 'dev\.totem\.vanillatweaks.*Observer' src/gametest src/test 2>/dev/null; then
  echo 'Observer validation ownership remains in VanillaTweaks.' >&2
  exit 1
fi
jq -e '.entrypoints["fabric-client-gametest"] == ["dev.totem.vanillatweaks.gametest.SkeletonAmmoRuntimeClientGameTest"]' \
  src/gametest/resources/fabric.mod.json >/dev/null
jq -e '.description | contains("Observer") | not' src/main/resources/fabric.mod.json >/dev/null
