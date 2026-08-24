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
提供整理按鍵、目標選擇與 Observer capture/render。使用 DeadRecall 整合 JAR
時不要再安裝獨立 TotemVanillaTweaks。

## Spectator Observer View（0.1.15+ Beta）

管理員可在 Spectator 模式使用：

```text
/observeui <player>
/observeui stop
```

Observer View 目前可把 Target Client 的即時 Minecraft framebuffer 經由
Dedicated Server relay 到 Observer Client，包含正常第一人稱世界、HUD 與
開啟中的 GUI。Server 端控制 session、權限、Target capture 啟停與 cleanup；
Observer 關閉 Mirror 後會送出 Stop，Target 會收到 `CaptureControl(false)`。

目前 framebuffer relay 是 **過渡實作**，不是長期架構。永久目標是完全停止
傳送整張截圖／framebuffer，只透過版本化協定傳送結構化世界、玩家、HUD、
容器／UI 與事件狀態，再由 Observer Client 本地重建畫面。詳細方向見
[`OBSERVER_ROADMAP.md`](OBSERVER_ROADMAP.md)。

Observer 已由 CI 驗證真正的三 JVM 路徑：

```text
Dedicated Server JVM
        +
Target Minecraft Client JVM
        +
Observer Minecraft Client JVM
```

測試會要求 Target 在沒有開 GUI 的正常遊戲世界中持續送出 framebuffer，並驗證
Observer 實際收到、重組與顯示 relayed frame，最後完成 Stop、capture disable
與 server cleanup。

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
production-runtime gate 使用實際 distribution namespace 啟動單人世界並召喚骷髏，
專門攔截開發環境可能看不到的 ABI/remapping 問題。所有權與驗證契約見
[`EXTRACTION.md`](EXTRACTION.md)。
