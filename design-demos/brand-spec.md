# RiftDeck 品牌与界面资产

## 当前输入与方向

用户先选定 **C 聚焦舞台**，随后提供 `logo.png` 并要求「基于这个logo可以再设计」。本轮沿用 C 的结构，更新品牌素材、主题色和公共组件，确认记录见 [direction-approved.md](direction-approved.md)。原始 A/B/C 初稿保留作历史对照。

## 原始品牌图

- 来源：用户提供的 `logo.png`，1254×1254 PNG。
- 完整原图只作为设计参考保存在 [assets/riftdeck-brand-board.png](assets/riftdeck-brand-board.png)，与用户原文件的 SHA-256 相同，不进入 Android 安装包。
- 应用使用两张独立素材：`drawable-nodpi/riftdeck_emblem.webp` 和 `drawable-nodpi/riftdeck_wordmark.webp`，仅包含图标或字标。素材经内置 imagegen 参考原图提取生成，再无损转换为 WebP；生成提示见 [素材记录](../branding/asset-extraction-prompts.md)。
- Android 在 `Dispatchers.IO` 使用 `BitmapFactory` 按显示宽度与密度采样，在主题层共享结果。没有设计板区域坐标或区域解码逻辑。
- HTML 内嵌两张独立素材，单文件可离线运行；总览页也直接引用独立图标。

## 视觉语言

| 用途 | 色值 | 应用方式 |
| --- | --- | --- |
| 背景 | `#000000` | 与品牌图的黑色画布衔接 |
| 面板 / 选中面 | `#090D0F` / `#142023` | 拉开层级，保持暗部克制 |
| 主强调 | `#F8F800` | 从字标的亮黄提取；开始按钮、方向焦点 |
| 次强调 | `#00F0F0` | 从字标的青色提取；收藏状态、页码、边框尾端 |
| 主文字 / 次文字 | `#EEF5F3` / `#A5B6B8` | 高对比正文与辅助信息 |
| 分隔线 | `#34484B` | 卡片轮廓和稀疏结构线 |

用右上、左下的对向切角呼应裂隙几何；封面和主按钮在黄、青两端留短线。主操作获得焦点时增加白色轮廓，次操作使用亮黄轮廓，选中状态保留文字与图标，避免只靠颜色传达。

左侧使用独立图标，顶部使用独立字标；保留页面名称和底部控制器提示。正文延用项目的等宽显示字与无衬线正文。无持续背景动画、重模糊或大面积辉光。

## 内容与边界

- 五款示例游戏仍来自 `MockGameRepository`，不编造真实游戏资料。
- 几何封面是 `GameArtwork` 占位素材，并非真实游戏封面。品牌迭代 HTML 仅同步其 SVG 源码色值，原始三方向素材不改动。
- 平台沿用 GBA 文字标识，不引入第三方平台 logo。
- 本轮更新应用内品牌界面；Android 桌面启动图标仍为既有矢量资源。
- ROM 扫描、外部模拟器启动和视频预览仍属后续里程碑，原型与应用均不宣称已接入。

当前已取得真实 Android 手机和模拟器的运行截图，记录见 [logo-iteration-verification.md](logo-iteration-verification.md)。HTML 截图仅是交互原型，原生截图单独存放。
