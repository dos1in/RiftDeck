# RiftDeck UI 设计与原生验证

当前版本是 [C 聚焦舞台 · 裂隙品牌迭代](C-rift-brand.html)，基于用户新提供的 logo 更新。总览页默认打开此版本；手机已安装对应 Compose 实现。原图、配色和加载方式见 [brand-spec.md](brand-spec.md)，本轮构建、按键与实机截图见 [logo-iteration-verification.md](logo-iteration-verification.md)。

双击 `index.html` 比较三方向；也可直接打开任一方向 HTML。三个方向文件独立、离线运行，图标与示例封面均已内嵌。总览页与 screenshots/assets 目录一起保留。

| 方向 | 设计来源 | 结构重点 |
| --- | --- | --- |
| A 几何终端 | 包豪斯几何风格，沿用项目品牌色与素材 | 离散模块、规则线、操作效率 |
| B 主机书架 | [Steam Deck 官方软件页面](https://www.steamdeck.com/en/software)，仅迁移手柄优先与库浏览原则 | 连续封面书架、邻近浏览、位置恢复 |
| C 聚焦舞台 | The Designers Republic 的游戏图形与信息设计思路；参照背景见 [WipEout Futurism 出版方介绍](https://www.thamesandhudson.com/products/wipeout-futurism)，未使用原作素材 | 当前游戏、明确主操作、相邻关系 |

基准画布 1280×800，不代表目标设备参数。总览工具也提供 960×720、1280×720、800×500 与 640×480。总览会缩放展示画布，独立打开可检查实际字号和响应布局。

键盘：方向键移动，Enter / A 确认，Escape / B 返回，X 详情，Y 收藏，Q/E 分类，M 设置。鼠标与触控也可点按。请以各画面上下文提示为准。

资产、事实与假设记录在 `brand-spec.md`、`product-facts.md` 和 `design-spec.md`。五款游戏均来自现有 MockGameRepository；封面沿用 GameArtwork 的几何占位，并非现实游戏素材。启动按钮展示未配置模拟器的引导，不会调用本机程序；目录按钮不读取文件。

用户已选择 **C 聚焦舞台**，确认记录见 `direction-approved.md`。选定结构已落实到 Android Compose 的首页、游戏库、详情和设置。原型保留用于设计对照，品牌迭代之前的原生验证记录见 `native-verification.md`。

浏览器验证与局限详见 `verification.md`。每版首页、游戏库、详情、设置、空态与紧凑尺寸截图在 `screenshots/`。
