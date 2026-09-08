## TotemVanillaTweaks 0.1.27

- 修正通用容器整理可能繞過合成扣料、搬動功能格，或破壞開啟中模組背包狀態的問題。
- 容器側整理僅支援原版箱子、漏斗、發射器／投擲器與界伏盒；支援的原版 GUI 仍可整理玩家主背包，保留快捷列、裝備、功能格及游標上的物品。自訂 menu 兩側皆拒絕此通用整理。
- 伺服器在寫入前驗證 menu 使用權限及全部目的格的拿取、放入與堆疊限制；任一驗證失敗時不修改物品。
- 回歸驗證涵蓋數量與 Data Components 保留、正常合成扣料、拒絕後物品不變，以及支援容器的整理行為；24 個伺服器 GameTests 與 87 個單元測試通過。

- Fixes unsafe generic sorting that could bypass crafting ingredient consumption,
  move functional slots, or detach an open modded backpack from its tracked stack.
- Container-side sorting is limited to vanilla chests, hoppers,
  dispensers/droppers, and shulker boxes. Supported vanilla menus retain player
  main-inventory sorting while preserving hotbar, equipment, functional slots,
  and the carried stack. Custom menus reject both generic sorting targets.
- The server validates menu access and every destination's pickup, placement,
  and stack limits before writing; failed validation leaves items unchanged.
- Regression coverage checks item counts/components, normal crafting
  consumption, unchanged rejected requests, and supported storage sorting.
  All 24 server GameTests and 87 unit tests passed.

Minecraft 26.2 · Fabric · Java 25 · requires Fabric API and TotemCore
>=0.7.18 <0.8.0 (external dependency). Existing Observer protocols and optional
module integrations are unchanged.
