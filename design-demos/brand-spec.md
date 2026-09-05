# 品牌与资产

- 本次用户明确选择沿用现有项目，输入以本地仓库为准。
- 图标：`app/src/main/res/drawable/ic_launcher.xml`，原路径转为 `assets/riftdeck.svg`，JSON 中提供内嵌版本；没有新造 logo。
- 色板：`core/ui/theme/RiftDeckTheme.kt` 中 NeonFrontendTheme；设计 spec 已列明全部色值。
- 字型：既有等宽显示字 + 无衬线正文，在 HTML 用本地字体族迁移。
- 封面：`core/ui/components/GameArtwork.kt` 是几何占位，不是真实游戏素材；`assets/mock-cover-*.svg` 精确沿用其几何构成，三版共享。明确标注示例封面。
- 内容：`data/repository/MockGameRepository.kt` 中五款示例游戏，数据由 `assets/mock-data.json` 固化，不编造真实游戏资料。
- GBA 以既有平台文字呈现；仓库没有平台 logo，本轮按用户「沿用现有项目」选择，不引入第三方品牌图。
- 禁区：禁止重模糊、连续背景运动、手机底部导航、密集小字、彩虹 glow、借用其他产品 UI 资产或虚假实时数据。
- 气质：明确、锋利、轻量，有卡带收藏感的街头科技界面。

现有 UI 信息来自 Compose 源码，当前未取得可运行设备截图；不能把 HTML 预览称为当前 Android 运行画面。
