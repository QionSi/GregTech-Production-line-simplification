# 石油化工综合体 (Petrochemical Complex)

本模组是 GT: New Horizons 的附属模组，目标 GTNH 版本 **2.9.0-beta-2**（GT5-Unofficial 5.09.54.20）。

## 已实现内容

| 项目 | 位置 |
| --- | --- |
| 机器控制器 | `com.qionsi.simplification.machine.MTEPetrochemicalComplex` |
| 机器 ID | `com.qionsi.simplification.MetaTileIDs.PETROCHEMICAL_COMPLEX_CONTROLLER = 32700` |
| 配方池（配方类型） | `com.qionsi.simplification.recipe.ModRecipeMaps.petrochemicalComplexRecipes` |
| 六个配方 | `com.qionsi.simplification.recipe.ModRecipes` |
| 注册入口 | `com.qionsi.simplification.ModContent`（由 `CommonProxy.preInit` 调用） |
| 本地化 | `assets/simplification/lang/{en_US,zh_CN}.lang` |

> 本模组**不新增任何流体**：高十六烷值柴油直接用 GT 已有的 `Materials.NitroFuel`（英文名 Cetane-Boosted Diesel，
> 中文名「高十六烷值柴油」，燃料值 1000，可直接在柴油发电机/内燃机里烧）。

## 机器

- 显示名：**石油化工综合体**（英文 Petrochemical Complex）
- 贴图：与**聚爆压缩机**相同（`OVERLAY_FRONT_IMPLOSION_COMPRESSOR` 系列 + Solid Steel Machine Casing 底材）
- 结构：**7（宽）× 3（高）× 5（深）** —— 正面 3 列脱氧钢，背面 4 列镀铜砖块

> 结构**不再写死在代码里**，而是读 `config/simplification/petrochemical_complex_structure.cfg`。
> 改完文件后 `/simplification reload` 即可生效，无需重启，已建好的机器也会自动重新校验。

```
                列: 1234567
        ┌──────────────────┐
层1 行1 │ OOO····          │   ← 行1 是最前排
   行2 │ OOO····          │
   行3 │ OOOBBBB          │
   行4 │ OOOBBBB          │
   行5 │ S~SBBBB          │   ← 控制器在列2
        ├──────────────────┤
层2 行1 │ OMO····          │   ← M = 消声仓
   行2 │ O·O····          │
   行3 │ O·O···B          │
   行4 │ O·O···B          │
   行5 │ SSSBBBB          │
        ├──────────────────┤
层3 行1 │ OOO····          │
   行2 │ OOO····          │
   行3 │ OOOBBBB          │
   行4 │ OOOBBBB          │
   行5 │ SSSBBBB          │
        └──────────────────┘
```

配置文件里的写法（行从前排到后排，用 `|` 分隔；空白处用空格，行尾无需补齐）：

```ini
[structure]
offsetA = 1          # 控制器所在列，0 = 最左
offsetB = 0          # 控制器所在层，0 = 最底层
offsetC = 4          # 控制器所在行，0 = 最前排

stage1 = OOO | OOO | OOOBBBB | OOOBBBB | S~SBBBB
stage2 = OMO | O O | O O   B | O O   B | SSSBBBB
stage3 = OOO | OOO | OOOBBBB | OOOBBBB | SSSBBBB
```

- `B` = Bronze Plated Bricks，`S` = Solid Steel Machine Casing（可放能源/维护/输入仓），
  `O` = 同 `S` 且额外可放输出仓/输出总线，`M` = 消声仓，`~` = 控制器，空格 = 必须是空气。
- `~` 的位置必须和 `offsetA/offsetB/offsetC` 一致，解析时会交叉校验，不一致直接报错。

### 舱室支持

| 舱室 | 允许位置 |
| --- | --- |
| 能源仓 / 无线能源仓 / TecTech 多A能源仓 | 任意 `S` 或 `O` 脱氧钢方块 |
| 维护仓 | 同上 |
| 输入仓 | 同上 |
| 输入总线 / 输出总线 / 输出仓 | 任意 `B` 镀铜砖块，以及 `O` 标记的脱氧钢方块 |
| 消声仓 | `M` 位置（唯一，有且仅有 1 个） |

> 激光靶仓（Laser Target Hatch）**不在**支持范围内：只启用 `HatchElement.Energy` 与 `HatchElement.ExoticEnergy`，
> 后者只包含 TecTech 的多A能源仓与激光靶仓的输入侧，因此多A能源仓可用而激光靶仓不可用。

### 并行与超频

- 基础并行数 **8**
- 每提升 1 个能源仓电压等级，并行数 ×2（可叠加）
- 启用完美超频（`ProcessingLogic.enablePerfectOverclock()`，即 4/4 超频，超频不损失效率）
- 支持批量模式、支持防溢出销毁保护

## 配方

编程电路 1~6 选择产物，所有配方都需要 10000mB 石油（可用 Oil / OilLight / OilHeavy / OilExtraHeavy 任意一种）。

| 电路 | 产物 | 输入 | 输出 | 耗时 | EU/t | 单次耗电 |
| --- | --- | --- | --- | --- | --- | --- |
| 1 | 聚乙烯 | 石油 10000 + 氧气 10000 + 蒸汽 12000 | 聚乙烯 1656 + 硫粉×1 + 碳粉×1（33%） | 30s | 32（LV） | 19200 EU |
| 2 | 聚氯乙烯 | 石油 10000 + 氯气 1104 + 氧气 10000 + 蒸汽 12000 | 聚氯乙烯 1656 + 硫粉×1 + 碳粉×1（33%） | 30s | 120（MV） | 72000 EU |
| 3 | 聚四氟乙烯 | 石油 10000 + 氢气 2208 + 氟 2208 + 氧气 10000 + 蒸汽 12000 | 聚四氟乙烯 828 + 硫粉×1 + 碳粉×1（33%） | 30s | 120（MV） | 72000 EU |
| 4 | 聚苯乙烯 | 石油 10000 + 氢气 552 + 氧气 10000 + 蒸汽 12000 | 苯乙烯 1656 + 碳粉×1 | 30s | 120（MV） | 72000 EU |
| 5 | 高十六烷值柴油 | 石油 10000 + 蒸汽 12000 + 氧气 4080 + 氮气 960 | 高十六烷值柴油 12000 + 硫粉×1 + 碳粉×12 + 氢气 5520 + 水 2040 + 氦气 1440 + 甲烷 10080 + 乙烷 144 + 丙烯 144 | 16s | 480（HV） | 153600 EU |
| 6 | 柴油 | 石油 10000 + 蒸汽 12000 | 柴油 12000 + 硫粉×2 + 碳粉×3 + 甲烷 10080 + 丙烯 144 + 氦气 1000 | 36s | 120（MV） | 86400 EU |

> 每条配方的「单次耗电 = EU/t × 20 × 运行秒数」，与设计文档一致。

## 构建

```bat
set JAVA_HOME=<你的 JDK 17+ 目录>
gradlew.bat --offline build
```

产物：`build/libs/simplification-<版本>+2.9.0-beta-2.jar`
