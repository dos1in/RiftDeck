# 导航帧采样

`NavigationInput.java` 是独立的 Android shell 诊断工具，不编入 APK。它在一个进程内注入方向键，避免每次输入都启动一条外部命令。默认向下 30 次、向上 30 次，每次释放后等待 145 ms；仅注入方向键，不启动游戏或更改设置。

使用前将前端停在游戏库的某一行，保证下面至少还有 30 个游戏。采样期间不要手动操作。工具不会判断当前应用或焦点，运行前必须确认设备页面；多个设备连接时给 adb 指定目标设备。

在仓库根目录执行，环境需提供 `ANDROID_HOME`、JDK 和 adb；按实际安装版本设置构建工具和平台：

```sh
PERF_BUILD_TOOLS="$ANDROID_HOME/build-tools/36.0.0"
PERF_PLATFORM="$ANDROID_HOME/platforms/android-36/android.jar"
PERF_OUTPUT="$(mktemp -d)"
mkdir -p "$PERF_OUTPUT/classes" "$PERF_OUTPUT/dex"
javac -source 8 -target 8 -classpath "$PERF_PLATFORM" -d "$PERF_OUTPUT/classes" tools/performance/NavigationInput.java
"$PERF_BUILD_TOOLS/d8" --min-api 29 --output "$PERF_OUTPUT/dex" "$PERF_OUTPUT/classes/NavigationInput.class"
adb push "$PERF_OUTPUT/dex/classes.dex" /data/local/tmp/riftdeck-navigation.dex
adb shell dumpsys gfxinfo com.riftdeck reset
adb shell CLASSPATH=/data/local/tmp/riftdeck-navigation.dex app_process /system/bin NavigationInput 30 145
adb shell dumpsys gfxinfo com.riftdeck framestats > "$PERF_OUTPUT/frames.txt"
```

工具依赖 shell 可访问的系统输入接口；厂商限制导致注入失败时会退出并报错。它不属于应用实现，也不能代替实体手柄验收。

每轮记录 APK 配置、编译状态、游戏库规模、图片或视频模式、起始焦点和设备温度，至少重复三轮。保留用户启用的无障碍服务及其他设备配置。比较前后必须使用同一输入工具和间隔，不能将旧的逐键 adb 采样与本工具结果直接归因于代码优化。

`gfxinfo` 只描述已渲染帧，不是实体输入延迟，也不能单独证明稳定帧率。需要进一步定位时，同时采集系统跟踪并区分主线程执行、调度等待及嵌套切片。

## 应用 Baseline Profile

`app/src/main/baseline-prof.txt` 包含从目标掌机运行记录提取的精确应用规则。采集覆盖启动、游戏库方向键浏览、游戏详情及返回；依赖库继续提供各自规则。没有使用包级通配符。

Android 12 的 `pm dump-profiles` 可输出旧版可读格式。将输出保存为文件后转换：

```sh
python3 tools/performance/profile_from_dump.py runtime-profile.txt app/src/main/baseline-prof.txt
python3 -m unittest discover -s tools/performance -p 'test_*.py'
./gradlew :app:assembleRelease
```

转换器只接受 `com.riftdeck` 类与方法，合并 H/S/P 标记，忽略内联缓存数据；无法识别的应用方法会报错。导出前需在相应版本走完核心流程；代码或编译器变化后重新生成，避免沿用旧的合成方法名称。原始运行记录仅作本地输入，不提交仓库。

构建后检查 APK 中的 `assets/dexopt/baseline.prof` 和 `baseline.profm`。文件存在仅证明打包；真机还须验证安装与编译状态，并在控制服务、缓存和温度的条件下测量收益。
