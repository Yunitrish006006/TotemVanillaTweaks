# TotemVanillaTweaks

TotemVanillaTweaks 收納不屬於單一大型功能的原版玩法調整：容器整理、
講台／書櫃規則、混凝土粉末硬化、漏斗取出熔爐成品時釋放經驗，以及
管理員用的 Spectator Observer View。

目前候選版本為 **0.1.16 Beta**。模組支援 TotemCore **>=0.7.0 <0.8.0**；
目前建議搭配已發布的 TotemCore **0.7.11**。

> **0.1.16 hotfix：** 0.1.15 在 Minecraft 26.2 的 production runtime 中，
> 骷髏載入時可能因 namespace/remap ABI 不一致而觸發 `NoSuchMethodError`。
> 0.1.16 停止把 26.2 distribution JAR remap 回 intermediary namespace，並新增
> production-runtime GameTest 直接啟動單人世界、召喚骷髏驗證實際發版環境。

## 安裝

Client 與 Server 都放入：

1. Fabric API `0.154.2+26.2`
2. TotemCore `0.7.x`（`>=0.7.0 <0.8.0`）
3. TotemVanillaTweaks `0.1.16`

| 項目 | 需求 |
| --- | --- |
| Minecraft | 26.2 |
| Fabric Loader | 0.19.3+ |
| Java | 25+ |
| Fabric API | 0.154.2+26.2 |
| 必要 Totem 模組 | `totem-core >=0.7.0 <0.8.0` |

Server 負責規則、整理 transaction 與 Observer session authority；Client 模組
提供整理按鍵、目標選擇，以及 Observer 的 protocol-native state relay／本地重建。
使用 DeadRecall 整合 JAR 時不要再安裝獨立 TotemVanillaTweaks。

## Spectator Observer View（protocol v4 開發版）

管理員可在 Spectator 模式使用：

```text
/observeui <player>
/observeui stop
```

Observer View 現在使用 **protocol-native v4**；semantic screen transport 使用
**screen protocol v2**。Dedicated Server 負責 session、權限、Target／Observer
capability negotiation 與 cleanup；Target Client 只傳送版本化的結構化玩家、HUD、
container／screen 狀態，Observer Client 以 Minecraft 原生渲染與本地 UI 重建觀察畫面。

screen protocol v2 使用 stable screen-family capability mask。第一個已實作的 semantic
family 是 `container_slots`：Server 會為每個 Observer 記錄 negotiated capability，
把同一 Target 所需 capability 的聯集下發給 Target，再按每個 Observer 的 mask 個別
過濾 semantic relay。若某個 screen family 沒有被雙方共同支援，該畫面仍走
metadata-only placeholder，不會中止整個 Observer session，也不會傳送 Target 像素。

production 路徑已完全移除整張 framebuffer／PNG 傳輸，不再存在 `FrameChunk`、
`FrameRelay`、`CaptureControl`、frame texture 或 `DynamicTexture` 安裝 fallback。
如果 Target 或 Observer Client 不支援目前的 protocol-native session capability，
`/observeui` 會拒絕建立 session，而不是退回截圖傳輸；個別 semantic screen family
不支援時則只降級該 GUI 為 metadata-only。

目前支援的觀察面包含正常世界／HUD、`container_slots` family，以及 unsupported／
unnegotiated Screen 的 metadata placeholder。完整架構與剩餘相容性工作見
[`OBSERVER_ROADMAP.md`](OBSERVER_ROADMAP.md)。

Observer 由 CI 驗證真正的三 JVM 路徑：

```text
Dedicated Server JVM
        +
Target Minecraft Client JVM
        +
Observer Minecraft Client JVM
```

三 JVM E2E 會驗證 protocol-native world/HUD、negotiated container、unsupported-screen
metadata、Stop 與 server/client cleanup；Client GameTests 另驗證 capability mask=0 時，
即使 Target metadata 指向 container Screen，也只會建立本地 generic placeholder。
另外有 source-level gate，若 `src/main` 再出現舊的 framebuffer transport surface，CI
會直接失敗。

## 容器整理

預設按鍵為滑鼠中鍵，可在「設定 → 按鍵綁定 → DeadRecall」更改。

1. 開啟物品欄或容器 GUI。
2. 把游標移到要整理的一側。
3. 按滑鼠中鍵。

| 游標位置 | 整理目標 |
| --- | --- |
| 玩家物品欄一側 | 玩家主背包 |
| 箱子／容器一側 | 當前容器 |
| 單純玩家物品欄畫面 | 玩家主背包 |

整理會合併相同 Item 與 Data Components，再依穩定順序排列。整理玩家
物品欄時不移動快捷列、盔甲、副手或其他裝備欄。Server 會驗證目前
開啟的 menu 與 slot 範圍，Client 不能指定任意 inventory。

## 講台配方

模組以較直接的配方覆寫 `minecraft:lectern`：

```text
S S S
_ B _
_ S _
```

`S` 是任意木製半磚，`B` 是書；總共 4 個半磚與 1 本書。

## 書櫃生存規則

- 移除原版普通書櫃工作台配方。
- 生存玩家物品欄中的普通書櫃會轉換成每個 3 本書。
- 物品欄空間不足時，多出的書會安全掉在玩家附近。
- 創造模式玩家不受物品欄轉換影響。
- 結構生成中的普通書櫃與空雕紋書櫃會成為裝有書本的雕紋書櫃。

此規則與 TotemEnchanting 搭配時，可讓探索取得的雕紋書櫃直接參與
加權附魔力。

## 混凝土粉末

所有 16 色混凝土粉末 ItemEntity 實際接觸水時會原地硬化成對應混凝土：

- 保留數量與 Data Components。
- 不建立替代 ItemEntity。
- 僅靠近水、下雨但未浸水時不會硬化。
- 流動水與水源都可生效。

## 熔爐與漏斗經驗

漏斗從熔爐、煙燻爐或高爐的結果槽取出成品時，應得的配方經驗會在
漏斗附近釋放，並清除已結算 recipe bookkeeping，避免自動化吞掉經驗
或重複領取。

## 模組邊界

- 背包與 Shulker／Bundle 的巢狀安全屬於 **TotemRemnant**。
- 缽與燧石、煉金材料及煉藥鍋屬於 **TotemAlchemy**。
- 雕紋書櫃的加權附魔力屬於 **TotemEnchanting**。

Vanilla Tweaks 不直接依賴這些功能模組。

## 開發與驗證

```bash
./gradlew build
```

CI 會另外執行 Server GameTests、Client GameTests、production-runtime Client GameTests，
以及 Observer 的 Dedicated Server + Target Client + Observer Client 三 JVM E2E。
Observer 另有 framebuffer-free production source gate；production-runtime gate 使用實際
distribution namespace 啟動單人世界並召喚骷髏，專門攔截開發環境可能看不到的
ABI/remapping 問題。所有權與驗證契約見 [`EXTRACTION.md`](EXTRACTION.md)。