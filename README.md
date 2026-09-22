# Element Mark

给 GUI 中的材料物品绘制左上角化学元素符号角标（Zn、Cu、Fe……）。

不绑定任何具体模组：符号通过物品的 **矿辞 tag（ore dictionary / common tag）** 解析，因此凡是遵循 `c:` / `forge:` 材料 tag 规范的模组（格雷科技、ChemLib、Create 等）都能自动生效。

## 工作原理

1. 客户端 mixin 注入 `ItemRenderer.render` 的 `TAIL`，仅在 GUI 展示语境下追加绘制。
2. `BadgeResolver` 读取物品 `Holder` 上的 tag，只认 `c:` / `forge:` 命名空间、路径形如 `<form>/<material>` 的 tag（如 `c:plates/zinc`），取 `/` 后段查内置的 118 元素表。
3. 命中则绘制符号，双层画法（深灰描边偏移 1px + 白色主体）；合金与虚构材料无命中，不绘制。
4. 结果按 `Item` 缓存在 `ConcurrentHashMap`，每个物品只解析一次。

## 元素表

内置 118 元素符号，含三组英式/美式拼写别名：

| 别名组 |
| --- |
| aluminium / aluminum |
| sulfur / sulphur |
| caesium / cesium |

## 兼容性

| 项目 | 说明 |
| --- | --- |
| Minecraft | 1.20.1 |
| Forge | 47.x |
| 侧 | 仅客户端（纯渲染，服务端不需要装） |

## 构建

需要 JDK 17（Forge 1.20.1 运行时版本）。

```
./gradlew build
```

产物在 `build/libs/`。

## 开发环境

基于 [ModTemplatez](https://github.com/ZZZank/ModTemplatez)（Architectury Loom）。

- 元数据集中在 `gradle.properties`（`mods.toml` / `pack.mcmeta` 由 `processResources` 填充变量）
- 依赖加在 `gradle/scripts/dependencies.gradle`
- 运行 `genIntellijRuns` 生成 IDEA 的 Client / Server 运行配置
- 路径中不要含中文以外的编码问题风险项，clone 后先跑一次 `runClient` 再跑 `genIntellijRuns`

## License

MIT
