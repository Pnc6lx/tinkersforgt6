# GregTech 电池强化

把 GT6 的 EU 电池装进匠魂工具。工具从"消耗耐久"改成"消耗电量"，容量与充电电压继承被装进去的那节电池。

代码位置：

| 文件 | 作用 |
|---|---|
| `src/main/java/com/tinkersgt6/power/GTBattery.java` | NBT 读写、电池识别（支持/搁置分类）、从快捷栏抽电 |
| `src/main/java/com/tinkersgt6/power/GTBatteryModifier.java` | Tool Station 里的改装项，占 1 个升级槽 |
| `src/main/java/com/tinkersgt6/power/GTBatteryToolMod.java` | 付款（`ActiveToolMod.damageTool`）、待机充电、tooltip |

---

## 一、匠魂原版的 RF 电池是怎么做的

结论先说：**匠魂自己并不实现 RF，它只是把 Thermal Expansion 体系的 `IEnergyContainerItem` 电池塞进工具的 NBT，然后在唯一的耐久漏斗里优先扣电。**

### 机制链路

| 环节 | 位置（相对 TinkersConstruct 源码） |
|---|---|
| 工具本身就是一个 CoFH 能源容器 | `library/tools/ToolCore.java:60-62` —— ` implements IEnergyContainerItem`，并用 `@Optional.Interface(modid = "CoFHAPI|energy", ...)` 包起来；没装 CoFH 时 Forge 会把接口与方法一并抽掉，`receiveEnergy` 等变成默认值 |
| 电量存根 NBT 的四个键 | `ToolCore.receiveEnergy / extractEnergy / getEnergyStored / getMaxEnergyStored`（`:746-799`），读的是**根 tag** 的 `Energy` / `EnergyMax` / `EnergyReceiveRate` / `EnergyExtractionRate`，单位 RF |
| 升级本体（`ModFlux`） | `modifiers/tools/ModFlux.java`。`new ModFlux()` + `ModifyBuilder.registerModifier` 在 `tools/TinkerTools.java:431-432`；`batteries` 白名单由 `modIntegration()` 里的 `GameRegistry.findItemStack("ThermalExpansion", ...)`（`TinkerTools.java:1345+`）和一条 IMC（`util/IMCHandler.java:338-340`）填充 |
| 匹配条件 | `ModFlux.matches`（`:25-71`）：排除 `ammo` 特质（弓/弩没有耐久）；物品的 `Item` 必须在 `batteries` 白名单里**且**实现 `IEnergyContainerItem`；放两节电池直接不匹配；开启 `balancedFluxModifier` 时要求 `TotalDurability >= 容量/1000`；装第二块必须更大且不再消耗升级槽；否则要求 `Modifiers >= 1` |
| 写入的数值 | `ModFlux.modify`（`:74-142`）：`Energy = 电池当前电量`（追加到已有电量上）、`EnergyMax = 电池容量`、`EnergyExtractionRate` / `EnergyReceiveRate` 通过"先把复制品充满再抽干"实测出来 |
| 花费 | `AbilityHelper.damageTool(stack, dam, tags, entity, ignoreCharge)`（`:350-392`）。只要 `ignoreCharge == false` 就先问 `damageEnergyTool`（`:394-445`）：算 `usage = trueSpeed * 2.8`（`trueSpeed` 由 `MiningSpeed` 系列 tag 推出），够扣就扣电量并 **return true**，外层于是跳过耐久；不够就把 `Energy` 归零 **return false**，回落到正常扣耐久 |
| 所有花费口都走这里 | 挖方块 `AbilityHelper.onBlockChanged`（`:47`）、近战 `onLeftClickEntity`（`:64`）、锄地 `hoeGround`（`:514`）、弩/弓（`ProjectileWeapon.java:197`、`Crossbow.java:218`）、3×3 范围的 `Chisel`（`items/tools/Chisel.java:41,99`、`ToolCore.setDamage` 那些隐含伤害（`ToolCore.java:719`）—— 全部是 `ignoreCharge = false` |
| 自动充电 | `AbilityHelper.chargeEnergyFromHotbar`（`:447-483`）：遍历快捷栏 9 格，凡 `IEnergyContainerItem` 且其 `receiveEnergy(slot, 1, true) == 1` 就 `extractEnergy` 补进去。仅当 **CoFHCore 未加载** 时由匠魂自己做（`equalityOverrideLoaded`，`:485`），装了 CoFHCore 就交给 CoFHCore 的同款逻辑 |
| 显示 | `ToolCore.getDamage`（`:684-711`）：有 `Energy` 键时耐久条画的是 **电量** 而不是耐久；`ToolCore.addInformation`（`:276-297`）在电量低于 1/3 时红、高于 2/3 时绿，显示 `x/y RF` |

### 为什么不能直接复用

1. **单位与规则不同。**
   GT6 只有 EU，而且一次传输的单位不是"多少电"，而是**多少个大小为「电压」的包**：容量与推荐包大小（`size`）一起决定"这是一节 LV 电池还是 IV 电池"（`gregapi/item/multiitem/energy/EnergyStat.java:45-170`），接收窗口是 `[size/2, size*2]`。RF 没有这个概念。
2. **价钱由我们说了算。**
   写了 `Energy` 键就等于把每一次花费的定价权交给 `damageEnergyTool` 的 `trueSpeed * 2.8` 公式 —— 它按工具的挖掘速度算钱，既看不见电压也看不见 GT 的 `toolDamagePerBlockBreak = 100`（`ToolStats.java:63`）。
3. **`ToolCore.receiveEnergy` 那条路走不通。**
   充它要 CoFH API，而我们要的是 GT 的机械/变压器/电池。`ToolCore` 是写死的 final 类层级，无法替一个 addon 加上 `IItemEnergy`。

所以：保留" root NBT 存电 + 在唯一漏斗里付款"的**形状**，键名换成我们自己的（绝不能叫 `Energy`，否则会和 RF 那条链撞车），价钱和电压规则自己实现。

### 我们的做法

| 环节 | 匠魂（RF） | 我们（EU） |
|---|---|---|
| 触发 | `AbilityHelper.damageTool` 内部 | 注册 `ActiveToolMod.damageTool`（`GTBatteryToolMod`），同一个漏斗，返回 true 即"已付款、别扣耐久" |
| 数值键 | 根 tag `Energy` 系列 | 根 tag `TG6.EU` / `TG6.EUCapacity` / `TG6.EUSize`；`InfiTool.GTEnergy` 标记是否装了电池 |
| 单价 | `trueSpeed * 2.8` RF/次 | `batteryEuPerDurability` EU/耐久点（默认 100，与 GT6"挖一格 100 单位"同量级），按本次扣几点耐久计价 |
| 充电 | 快捷栏里的 RF 那只电池 | 快捷栏里的任意 `IItemEnergy` EU 电池，`doEnergyExtraction` 时带上工具的 size，`getEnergySizeInputRecommended` 就是继承来的电压 |
| 显示 |`ToolCore` 自己画 | `ItemTooltipEvent`（`GTBatteryToolMod.onTooltip`） |

---

## 二、耐久扣除的两种方案

配置文件 `TinkerForGT6.cfg` 的 `power` 分类里，`batteryDurabilityMode` 二选一：

| 值 | 行为 |
|---|---|
| `ENERGY_ONLY` | 只扣电量，不扣耐久（匠魂 Flux 的做法） |
| `ENERGY_AND_DURABILITY` | **默认**。扣电量的同时，`1 / max(10, 头部材质工具品质 * 20)` 的概率额外扣一次耐久 —— 与 GT6 完全一致 |

后者的依据在 `gregapi/item/multiitem/MultiItemTool.java:433-469`：

```java
IItemEnergy tElectric = getEnergyStats(aStack);
if (tElectric == null || RNGSUS.nextInt(Math.max(10, getPrimaryMaterial(aStack).mToolQuality * 20)) == 0) {
    // 这一次也扣耐久
    setToolDamage(aStack, getToolDamage(aStack) + aAmount);
    ...
    return tElectric == null || useEnergy(TD.Energy.EU, aStack, aAmount, ...);
}
return useEnergy(TD.Energy.EU, aStack, aAmount, ...);   // 只扣电
```

即：电动工具**每次都付电量**，耐用只按上面那个概率付一次。我们的"工具品质"取**头部材质**的 `OreDictMaterial.mToolQuality`，这是唯一一个既决定工具挖掘等级、又在语义上对应 GT 工具品质的数（结果按 material ID 缓存，`damageTool` 每挖一格都要问）。

**和 GT6 的一处有意偏差**：电量不足时 GT6 电动工具什么都不做（既不扣耐久也不扣电），而电量不足时我们让工具退化成普通工具继续扣耐久。否则工具会表现为"好像坏了但不给任何理由"。

---

## 三、配置

`TinkerForGT6.cfg`：

| 键 | 分类 | 默认 | 含义 |
|---|---|---|---|
| `batteryUpgrade` | power | `true` | 总开关。关掉就完全不注册改装项与 `ActiveToolMod` |
| `batteryDurabilityMode` | power | `ENERGY_AND_DURABILITY` | 上面两种方案 |
| `batteryEuPerDurability` | power | `100` | 每点耐久换多少 EU。`0` = 电池免费（仍然占用安装槽） |
| `batteryRechargeFromHotbar` | power | `true` | 自动从快捷栏里的 EU 电池补电 |
| `batteryRechargeIntervalTicks` | power | `20` | 待机补电的检查间隔 |
| `batteryRechargePacketsPerTick` | power | `4` | 每次补电最多搬几个"包"（一个包 = 工具的电压） |
| `batteryMinCapacity` | power | `1` | 容量低于此值的电池不允许安装 |
| `batteryAllowUpgrade` | power | `true` | 允许换成更大的电池，且不额外花升级槽 |
| `mazebreaker` / `mazebreakerSpeedMultiplier` / `mazebreakerDrops` | general | `true` / `40` / `true` | MazeBreaker 材料特性，见 `compat/MazeBreakerCompat.java` |

---

## 四、哪些电池能用，哪些搁置

识别只走 GT6 的公开接口 `gregapi.item.IItemEnergy`（`MultiItem` 和所有多方块实体类电池的物品形式都实现了它），不认具体物品：

```
capacity = getEnergyCapacity(TD.Energy.EU, stack)     // 容量
size     = getEnergySizeInputRecommended(TD.Energy.EU, stack)   // 电压
charge   = getEnergyStored(TD.Energy.EU, stack)
```

排除条件（返回一个明确的 `Kind`，方便以后逐个开启）：

| Kind | 判定 | 现在能否使用 |
|---|---|---|
| `SUPPORTED` | 有 EU、`capacity > 0`、且 `isEnergyType(EU, stack, false)` 为真（即可充） | **是** |
| `NOT_ENERGY_ITEM` | `Item` 不是 `IItemEnergy` | 否 |
| `OTHER_ENERGY_TYPE` / `LIGHT_ENERGY` | `getEnergyTypes` 里没有 EU | 否 |
| `NO_CAPACITY` | 声称装 EU 但容量为 0 | 否 |
| `SINGLE_USE` | 能抽不能充（`EnergyStat.makeSUBattery`，`mCanCharge = false`） | 搁置 |
| `HYDROGEN_FUEL_CELL` | `IL.Power_Cell_Empty` / `IL.Power_Cell_H` | 搁置 |
| `ANEUTRONIC_FUSION` | `IL.Aneutronic_Fusion_Empty` / `IL.Aneutronic_Fusion_He3` | 搁置 |
| `ZPM` | `IL.ZPM`（存的是 **QU**，不是 EU） | 搁置 |

判定顺序见 `GTBattery.classify`：先用 GT6 自己的 `IL` 名单排除四类明确搁置的，再看能量类型，最后看容量与能否充电。判定结果按 `Item#id + meta` 缓存 —— Tool Station 每次槽位变动都会重跑所有改装项的匹配，而多方块实体类物品的每个 `IItemEnergy` 调用都要过一趟 TileEntity。

### 搁置项备忘录（后续接手用）

下面这些在 GT6 侧的实际数值都不一样，但**没有一个是技术障碍**，卡住的只是"该不该允许"和"允许后怎么定价/怎么补充能源"这两个设计问题。

#### 1. 一次性电池（`SINGLE_USE`）

- GT6 侧：`EnergyStat.makeSUBattery`（`EnergyStat.java:62-63`），`mCanCharge = false`。典型成员 `IL.Battery_SU_LV_SulfuricAcid`、`IL.Battery_SU_*_Mercury`（`IL.java:493-495`，已标 Deprecated）。
- 特性：只能抽、不能充，`setEnergyStored` 到 0 时会变成空壳或直接消失。
- 一旦允许，需要决定：
  1. 电量耗尽后该把工具变回普通工具，还是直接报废整把工具（GT6 的行为是电池消失）；
  2. tooltip 要显示剩余电量而不是容量；
  3. 它与"无限替换"的经济性关系 —— 一次性电池本身是廉价的，等价于"永久工具"。
- 代码改动点：`GTBattery.classify` 把这个 Kind 判为可用（`usable()` 放行），再加一个"不可充电"标记给 `refillFromHotbar` 看。

#### 2. 氢燃料电池（`HYDROGEN_FUEL_CELL`）

- GT6 侧：`IL.Power_Cell_Empty`（未充气）、`IL.Power_Cell_H`（充好氢），注册在 `Loader_MultiTileEntities.java:1096`，`NBT_CAPACITY = 3_200_000`、`NBT_INPUT = V[3]`（= 512 = HV），用 `RM.Canner` 加 `MT.H.gas(U*200)` 充气、反向可以把氢抽出来。
- 它是**多方块实体类物品**，`IItemEnergy` 的实现由 `MultiTileEntityItemInternal` 委托给 TileEntity（`gregapi/block/multitileentity/MultiTileEntityItemInternal.java:562-563`），接口上和别的 EU 电池没区别。
- 搁置原因：容量、电压的语义没问题，问题在"加注" —— 电用完之后，是让快捷栏里继续备着氢/完好的 Power Cell，还是要求一个新的转化率把 GAS 直接转成 EU？以及对工具来说 `3.2M EU` 是不是过强（按 100 EU/耐久点 ≈ 32000 次使用）。
- 一旦允许，需要决定：是否要求工具具备相应的**输入强度限制**（现在的工具只继承 `size`，没有 amperage/数量上限），是否要从用例里排掉自动加注。

#### 3. ZPM（`ZPM`）

- GT6 侧：`IL.ZPM`，注册在 `Loader_MultiTileEntities.java:1103`，`NBT_ENERGY_ACCEPTED = TD.Energy.QU`（**不是 EU**）、`NBT_CAPACITY = 2_000_000_000_000`、`NBT_INPUT = V[7]`（= 131072 = ZPM 电压）。
- 它是个 QU 容器，EU 工具接不了；要支持就得引入 **QU → EU** 的转换（GT6 自己有转换器，`TileEntityBase11MultiBlockConverter`），并决定转换率。
- 一旦允许，需要决定：报什么样的 tooltip（`QU` 单位、`22,000 亿` 量级），以及是否按 `getEnergySizeInputRecommended` 继承 ZPM 的输入窗口（推荐值为 `V[7]`，远远高于任何工具耐久/正常使用所需的 100/次 —— 需要新增一个"每次使用抽多少"的上限）。

#### 4. 无中子聚变电池（`ANEUTRONIC_FUSION`）

- GT6 侧：`IL.Aneutronic_Fusion_Empty` / `IL.Aneutronic_Fusion_He3`，`Loader_MultiTileEntities.java:1099-1100`，`NBT_CAPACITY = 1_024_000_000`、`NBT_INPUT = V[5]`（= 8192 = IV）。和氢燃料电池一样是多方块实体类物品，充的是 `MT.He_3.gas`。
- 情况基本同氢燃料电池，只是量级更大（默认单价下 ≈ 一千万次使用）。
- 一旦允许，额外考虑：是否要加一个 `power.batteryMaxCapacity` 上限，把这些"超长期电源"压回合理区间（默认单价下这块电池够点 ≈ 一千万次）。

#### 5. 存 LU 的电池（`LIGHT_ENERGY`）

- GT6 侧：`TD.Energy.LU` = Light Energy（`TD.java:116`，短名 `LU`）。TileEntity 侧有 `MultiTileEntityBatteryLU8/32/128/512/2048/8192`（`gregtech/tileentity/batteries/lu/`）；物品侧的 IL 条目尚未确认，需要时可先确认后再加判据。
- 它是完全不同的能量类型，EU 工具读不到。要支持意味着工具要能同时携带两种能量，或者给工具加 LU 相关的额外能力（镭射之类的新用途）—— 工作量远超"电池"本身。
- 一旦允许，需要决定：它给工具的到底是"另一种电量"，还是"另一种功能"。

---

## 五、已知限制

1. **不能用 GT6 的机械/变压器直接给工具充电。** GT 的充电机看的是 `IItemEnergy` 实现，而 `ToolCore` 是写死的类我们不能扩展；唯一可行的补电方式是快捷栏里的电池（默认开启，`batteryRechargeFromHotbar`）。
2. **安装后不可卸下。** 匠魂的改装系统没有"移除改装"这个概念；换更大的电池会把旧电量累加进新容量。
3. **一把工具只能有一种电池来源。** 如果工具已经装了匠魂自己的 RF 升级（根 tag 上有 `Energy`），我们的改装项不会匹配，避免两条付款链打架。
4. **电量耗光后工具会重新开始扣耐久。** 见上面"与 GT6 的偏差"。
5. **快捷栏补电要求电池独占一格。** `EnergyStat.doEnergyExtraction` 要求 `stackSize == 1`，堆成一堆的电池不会被扣。
