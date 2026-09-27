# 石油化工综合体 (Petrochemical Complex)

本模组是 GT: New Horizons 的附属模组，目标 GTNH 版本 **2.9.0-beta-2**（GT5-Unofficial 5.09.54.20）。

## 已实现内容

| 项目 | 位置 |
| --- | --- |
| 机器控制器 | `com.qionsi.simplification.machine.MTEPetrochemicalComplex` |
| 机器 ID | `com.qionsi.simplification.MetaTileIDs.PETROCHEMICAL_COMPLEX_CONTROLLER = 32700` |
| 机器结构（写死在代码里） | `PetrochemicalComplexStructure.shapeText()`（形状文本）+ `StructureBlueprint`（坐标换算） |
| 配方池（配方类型） | `com.qionsi.simplification.recipe.ModRecipeMaps.petrochemicalComplexRecipes` |
| 六个加工配方 + 控制器装配配方 | `com.qionsi.simplification.recipe.ModRecipes` |
| NEI 页面布局 | 直接用 GT 自带的 `LargeNEIFrontend`（在 `ModRecipeMaps` 里指定） |
| 注册入口 | `com.qionsi.simplification.ModContent`（由 `CommonProxy.preInit` 调用） |
| 本地化 | `assets/simplification/lang/{en_US,zh_CN}.lang` |

> 本模组**不新增任何流体**：高十六烷值柴油直接用 GT 已有的 `Materials.NitroFuel`（英文名 Cetane-Boosted Diesel，
> 中文名「高十六烷值柴油」，燃料值 1000，可直接在柴油发电机/内燃机里烧）。

## 机器

- 显示名：**石油化工综合体**（英文 Petrochemical Complex）
- 贴图：与**聚爆压缩机**相同（`OVERLAY_FRONT_IMPLOSION_COMPRESSOR` 系列 + Solid Steel Machine Casing 底材）
- 结构：**7（宽）× 5（高）× 3（深）**，控制器在正面墙的最底层，左边 3 列脱氧钢、右边 4 列镀铜砖块，镀铜部分只有下面 3 层

### 结构写法

结构**写死在代码里**（`PetrochemicalComplexStructure.shapeText()`），一个字符串 = 一个**纵深切片**，
第一个切片是**正面**（控制器所在的那面墙，也就是玩家面对的一面），切片内一行 = 一个**高度层**，
第一行是**最上层**：

```
列:            1234567                列:            1234567                列:            1234567
切片1（正面）  第1层(顶) OOO····       切片2（中间）  第1层(顶) OMO····       切片3（背面）  第1层(顶) OOO····
              第2层     OOO····                     第2层     O·O····                     第2层     OOO····
              第3层     OOOBBBB                     第3层     O·OBBBB                     第3层     OOOBBBB
              第4层     OOOBBBB                     第4层     O·O···B                     第4层     OOOBBBB
              第5层(底) S~SBBBB                     第5层(底) SSSBBBB                     第5层(底) SSSBBBB
```

- `~` = 控制器：正面墙、最底层、左起第 2 列（A/B/C = 1/4/0）
- `M` = 消声仓：最上层 3×3 钢板的中心
- `·`（空格）= 结构校验**不检查**的位置

符号含义与允许的舱室：

| 符号 | 方块 | 允许的舱室 |
| --- | --- | --- |
| `S` | 最底层的脱氧钢机壳 | 能源仓（含无线/多A）、维护仓、输入仓 |
| `O` | 最底层以上的脱氧钢机壳 | 输出仓 |
| `B` | 镀铜砖块 | 输入总线、输出总线、输入仓、输出仓 |
| `M` | 最上层中心的脱氧钢机壳 | 消声仓（有且仅有 1 个） |
| `~` | 控制器本身 | — |
| 空格 | 不校验 | — |

以上位置都放舱室也可以，只要外壳还留着 **10 个脱氧钢机壳**和 **8 个镀铜砖块**。

> 能源仓用的是 `HatchElement.Energy.or(HatchElement.ExoticEnergy)`，因此普通能源仓、无线能源仓
> （`MTEWirelessEnergy`）和 TecTech 多A能源仓都可用，**激光靶仓**（`MTEHatchDynamoTunnel`）不在其中，符合要求。
> 输入/输出总线与输入/输出仓用的是 GT 标准的 HatchElement，因此**样板输入总成**（`MTEHatchCraftingInputME`）
> 以及各种 ME 仓室都能放。

### 舱室分离（输入分离）

GUI 里的「输入分离」（Input Separation）按钮**可用**，怎么用交给玩家：

- **关闭（本机默认）**：所有输入总线合并成一个原料池，一条配方可以从任意总线取材料——本机一条配方最多要
  「编程电路 + 3 种粉料」，玩家自然会分开放，所以默认关闭；
- **打开**：每条输入总线各自算作独立的原料组，可以用不同总线同时跑不同配方。

流体仓两种模式下都是合并的（GT 本身如此）。实现上是 `supportsInputSeparation()` 返回 `true` 让按钮不再被禁用，
再重写 `getDefaultInputSeparationMode()` 返回 `false`——因为 GT 的默认实现是「支持就等于默认打开」，
不重写的话机器一建出来就是分离状态。状态随机器存档保存。

### 并行与超频

- 基础并行数 **8**
- 每提升 1 个能源仓电压等级，并行数 ×2（可叠加）
- 启用完美超频（`ProcessingLogic.enablePerfectOverclock()`，即 4/4 超频，超频不损失效率）
- **配方电压不受能源仓电压等级限制**：`ProcessingLogic.setUnlimitedTierSkips()` 关掉了 GT 默认的
  「配方等级高于能源仓等级就报 insufficient voltage」那一步，任何等级的能源仓都能跑任何等级的配方。
  剩下的只是普通的功率检查——能源仓合起来要供得起配方的 EU/t（例如 HV 的电路 5 需要 480 EU/t，
  单个 LV 能源仓供不上，多插几个或插高一档的即可），这属于供电能力而不是电压等级限制。
- 支持批量模式、支持防溢出销毁保护

### 外壳最少方块数

结构校验只要求外壳保留 **脱氧钢机械方块 ≥ 10**、**镀铜砖块 ≥ 8**（`MIN_SOLID_STEEL_CASINGS` /
`MIN_BRONZE_CASINGS`），其余标了符号的位置都可以换成舱室——因为放了舱室的那个位置就不再计入外壳数量。
（原来要求"和形状一样多"，等于一个舱室都不能放，机器永远无法成型，这次一并修掉了。）

## 配方

编程电路 1~12 选择产物。电路 1~6 是第一版设计（以石油为主原料的高分子与燃料），电路 7~12 来自 `more/1.docx`
——高辛烷值汽油仍从石油出发（石油可用 Oil / OilLight / OilHeavy / OilExtraHeavy 任意一种），橡胶、工程塑料和
环氧树脂则由各自的单体构建，所以改为固体粉料加气体作原料。

| 电路 | 产物 | 输入 | 输出 | 耗时 | EU/t | 单次耗电 |
| --- | --- | --- | --- | --- | --- | --- |
| 1 | 聚乙烯 | 石油 10000 + 氧气 10000 + 蒸汽 12000 | 聚乙烯 1656 + 硫粉×1 + 碳粉×1（33%） | 30s | 32（LV） | 19200 EU |
| 2 | 聚氯乙烯 | 石油 10000 + 氯气 1104 + 氧气 10000 + 蒸汽 12000 | 聚氯乙烯 1656 + 硫粉×1 + 碳粉×1（33%） | 30s | 120（MV） | 72000 EU |
| 3 | 聚四氟乙烯 | 石油 10000 + 氢气 2208 + 氟 2208 + 氧气 10000 + 蒸汽 12000 | 聚四氟乙烯 828 + 硫粉×1 + 碳粉×1（33%） | 30s | 120（MV） | 72000 EU |
| 4 | 聚苯乙烯 | 石油 10000 + 氢气 552 + 氧气 10000 + 蒸汽 12000 | 苯乙烯 1656 + 碳粉×1 | 30s | 120（MV） | 72000 EU |
| 5 | 高十六烷值柴油 | 石油 10000 + 蒸汽 12000 + 氧气 4080 + 氮气 960 | 高十六烷值柴油 12000 + 硫粉×1 + 碳粉×12 + 氢气 5520 + 水 2040 + 氦气 1440 + 甲烷 10080 + 乙烷 144 + 丙烯 144 | 16s | 480（HV） | 153600 EU |
| 6 | 柴油 | 石油 10000 + 蒸汽 12000 | 柴油 12000 + 硫粉×2 + 碳粉×3 + 甲烷 10080 + 丙烯 144 + 氦气 1000 | 36s | 120（MV） | 86400 EU |
| 7 | 高辛烷值汽油 | 石油 10000 + 蒸汽 10870 + 氧气 2990 + 氮气 4180 | 高辛烷值汽油 11130 + 碳粉×80（88%）+ 氢气 11620 + 硫粉×3（80%）+ 环烷酸 250 | 30s | 6144（IV） | 3686400 EU |
| 8 | 丁苯橡胶 | 碳粉×9 + 硫粉×1 + 氢气 26000 | 丁苯橡胶 9000 | 60s | 120（MV） | 144000 EU |
| 9 | 硅橡胶 | 碳粉×2 + 硅粉×1 + 硫粉×1 + 氢气 24000 + 氧气 10000 | 硅橡胶 9000 | 16s | 480（HV） | 153600 EU |
| 10 | 聚苯硫醚 | 碳粉×6 + 钠粉×6 + 硫粉×4 + 氢气 4000 + 氧气 8000 + 氯气 2000 | 聚苯硫醚 1000 | 12s | 1960（EV） | 470400 EU |
| 11 | 环氧树脂 | 碳粉×25 + 钠粉×2 + 硫粉×1 + 氢气 30000 + 氧气 31000 + 氯气 32000 | 环氧树脂 9000 | 16s | 480（HV） | 153600 EU |
| 12 | 聚苯并咪唑 | 碳粉×20 + 氢气 12000 + 氧气 14000 + 氮气 4000 + 氯气 4000 | 聚苯并咪唑 1000 | 16s | 10240（LuV） | 3276800 EU |

> 每条配方的「单次耗电 = EU/t × 20 × 运行秒数」，与设计文档一致（电路 7~12 来自 `more/1.docx`）。
> 产物全部使用 GT 已有材料，不新增任何流体：高辛烷值汽油 `Materials.GasolinePremium`、环烷酸
> `Materials.NaphthenicAcid`（这两个是 `getFluid`）、丁苯橡胶 `Materials.StyreneButadieneRubber`、硅橡胶
> `Materials.RubberSilicone`、聚苯硫醚 `Materials.PolyphenyleneSulfide`、环氧树脂 `Materials.Epoxid`、
> 聚苯并咪唑 `Materials.Polybenzimidazole`（这五个是 `getMolten`）。
> 电路 7/10/12 的电压高于常见能源仓等级，靠上面那条「配方电压不受能源仓等级限制」才跑得起来。

NEI 页面用的是 GT 自带的 `LargeNEIFrontend`（`ModRecipeMaps` 里 `.frontend(LargeNEIFrontend::new)`）：
物品格 3 个一行放在最上面，流体格在下面同样 3 个一行，物品和流体各占一条带；页面高度由行数自动算出（这里
`maxIO(4, 3, 9, 9)` → 物品 2 行 + 流体 3 行 → 170×100），GT 图标被它挪到 (80,62) 这个没有格子的地方。
所以电路 5 的 7 种流体输出、电路 11 的 3 种固体+3 种流体输入都不会超出页面。

控制器本体用**装配机**合成：编程电路 15 + 1 个 LV 机器外壳 + 2 个任意 LV 电路 + 1 台 LV 蒸馏塔 + 1 台 LV 化学反应釜，30 秒 / 32 EU/t。

## 构建

```bat
set JAVA_HOME=<你的 JDK 17+ 目录>
gradlew.bat --offline build
```

产物：`build/libs/simplification-<版本>+2.9.0-beta-2.jar`

## 调试时的热更新（HotSwap）

数据文件重载那套框架已经去掉，改成**调试器热替换（HotSwap）**：

1. 以调试模式启动客户端（会挂在 5005 端口等调试器连上来）：

   ```bat
   gradlew.bat runClient --debug-jvm
   ```

2. IDEA：`Run → Attach to Process…` 选中客户端进程（或者建一个 `Remote JVM Debug` 配置，端口 5005）连接。
3. 改完代码后按 `Ctrl+Shift+F9`（或调试器里的 *Reload changed classes*）把新字节码换进正在跑的进程里。

能生效的范围：

| 改动 | HotSwap | 说明 |
| --- | --- | --- |
| `shapeText()` 里的结构字符串 | ✅ 立刻生效 | `getStructureDefinition()` 每次结构校验都会把形状文本和上次的比一遍（只比字符串，不重新解析），不一样就重建结构定义 |
| 各结构方块允许的舱室（`PetrochemicalComplexStructure.build()` 的方法体） | ✅ 立刻生效 | 同上，重建时会重新绑定元素 |
| 配方数字（`ModRecipes` 里某个方法的方法体） | ⚠️ 需重跑注册 | 配方只在 preInit 注册一次，改完要么重启，要么手动重进一次游戏 |
| 机器名称、tooltip、语言文件、贴图 | ❌ 需重启 | tooltip 在注册时生成一次，语言/资源在启动时加载 |
| 新增/删除字段、方法、类，或改方法签名 | ❌ 需重启 | JVM 的 HotSwap 只能替换方法体 |

> 结构定义不再放在类初始化块里，而是在第一次结构校验时构建（日志里会打印 `Petrochemical Complex structure: …`），
> 这样即使形状写错（例如用了没绑定的符号），也只是回退成单方块结构并在日志里报错，不会把整个游戏带崩。
