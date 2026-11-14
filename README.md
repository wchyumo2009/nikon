# 钢琴节拍器（Android）

依据 `spec/ME2AI/20261113需求描述.md` 构建的原生 Android 应用：可在 Android 手机与平板运行，实现钢琴/机械节拍器功能，摆锤支持**上端摆动、下端摆动、弹簧式摆动**自由切换，界面采用明亮阳光的卡片式布局，搭配浅枫木拟真机身。

## 一键准备环境并构建

在 Windows PowerShell 中于项目根目录执行：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\setup-env.ps1
```

脚本是幂等的，先检查再补齐，已存在的组件不会重复下载：

1. **JDK 17**：本机只有 JDK 25 时自动下载并解压 Temurin 17（Gradle 8.2 / AGP 8.2 需要 JDK 17）。
2. **Android SDK**：自动下载 Android 命令行工具，安装 `platform-tools`、`platforms;android-34`、`build-tools;34.0.0`，并写入 SDK 许可与 `local.properties`。
3. **Gradle Wrapper**：优先复用本机已缓存的 Gradle 8.2 生成 `gradlew` / `gradle-wrapper.jar`，否则自动下载。
4. **构建**：`gradlew.bat assembleDebug`，产物 `app/build/outputs/apk/debug/app-debug.apk`。
5. **运行**：检测到已连接的设备/模拟器时自动 `adb install -r` 并启动主界面；无设备时输出安装命令。加 `-WithEmulator` 会自动准备并启动两台华为设备规格的模拟器（P50 手机 + MatePad 平板，默认 `-Device All`），并分别安装、启动；单台用 `-Device P50` 或 `-Device Pad`。需要 CPU 硬件加速/WHPX，首次下载约 1.5 GB、解压后约 4 GB，脚本使用 `-gpu host`；会先重启当前 SDK 的 adb server 以免第三方旧版 adb 抢占 5037 端口。

常用参数：

| 参数 | 作用 |
| --- | --- |
| `-WithEmulator` | 创建并启动模拟器 AVD（默认两台华为设备） |
| `-Device <P50\|Pad\|All\|Default>` | 选择设备档位；`All` 两台同时在线（默认），`Default` 恢复旧的通用 320×640 AVD |
| `-SkipBuild` | 跳过构建，用已有 APK 继续安装/启动；没有 APK 时跳过安装 |
| `-SkipInstall` | 只准备/启动模拟器，不安装 APK |
| `-SdkRoot <路径>` / `-JdkRoot <路径>` | 复用已有 Android SDK / JDK 17 |

## 华为设备模拟器档位

`-WithEmulator` 按真实屏幕参数创建两台独立 AVD（系统为 AOSP Android 14 / API 34，模拟的是屏幕规格与布局限定符，不含 EMUI/HarmonyOS 与厂商服务）：

| 档位 | AVD | 分辨率 | 密度 | 屏幕 / 方向 | 应用布局 |
| --- | --- | --- | --- | --- | --- |
| `-Device P50` | `nikon_huawei_p50` | 1224×2700 | 460 dpi | 6.5 英寸、约 458 ppi、竖屏 | `smallestWidth ≈ 426dp`，手机竖屏上下布局 |
| `-Device Pad` | `nikon_huawei_pad` | 2560×1600 | 280 dpi | 10.95 英寸、约 276 ppi、横屏 | `smallestWidth ≈ 914dp`（≥600dp），平板左右分栏 |

```powershell
# 只跑手机 / 只跑平板 / 两台同时在线
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\setup-env.ps1 -WithEmulator -Device P50
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\setup-env.ps1 -WithEmulator -Device Pad
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\setup-env.ps1 -WithEmulator -Device All

# 不重新构建，只准备并启动设备
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\setup-env.ps1 -WithEmulator -Device All -SkipBuild -SkipInstall
```

实现要点：脚本写入各 AVD 的 `config.ini`（`hw.lcd.width` / `hw.lcd.height` / `hw.lcd.density`、`hw.mainKeys=no`），删除缓存的 `hardware-qemu.ini`，以固定控制台端口启动（P50 = 5556、平板 = 5558），待 `sys.boot_completed=1` 后用 `wm size` / `wm density` 复核并锁定屏幕参数，再按 `adb -s <serial>` 分别安装与启动，避免两台设备同时在线时 adb 报 "more than one device"。`-Device Default` 保留原来的通用 320×640 AVD 行为。

工具链默认放在 `%LOCALAPPDATA%\NikonMetronome` 与 `%LOCALAPPDATA%\Android\Sdk`（纯英文路径，避免 Java properties 与原生工具解析中文路径），不写入系统环境变量；脚本仅在当前进程内设置 `JAVA_HOME` / `ANDROID_HOME`。

已准备好环境时可跳过脚本直接构建：

```powershell
.\gradlew.bat assembleDebug
```

安装到设备：

```powershell
adb install -r app\build\outputs\apk\debug\app-debug.apk
adb shell am start -n com.nikon.metronome/.MainActivity
```

## 需求对应

| 需求 | 实现 |
| --- | --- |
| Android 平板与手机可运行，环境不存在时自动准备 | 原生 Android 应用（`minSdk 23`、`targetSdk 34`），手机竖屏上下布局、横屏与平板左右分栏；`scripts/setup-env.ps1` 自动准备 JDK / SDK / Wrapper / 设备环境 |
| 钢琴节拍器，摆可自由切换三种摆动 | 40–240 BPM、拍号 2/4·3/4·4/4·6/8、重拍、钢琴/机械/木鱼/电子音色、音量、常用速度预设、TAP 测速；`上端摆动`、`下端摆动`、`弹簧式摆动` 一键切换，运行中也可切换 |
| 页面美观、类似真实节拍器 | `PendulumView` 自绘浅枫木金字塔机身、木纹、亮白刻度盘与 BPM 刻度、亮金摆杆与配重滑块、青绿支点、"SUNNY BEAT" 铭牌；配重位置随 BPM 改变，摆的相位与声音严格同步；浅色渐变背景、白色圆角卡片与暖橙/青绿点缀呼应 |

## 实现要点

- **节拍精度**：`MetronomeEngine` 在独立线程以 `System.nanoTime()` 累积下一拍时间，用 `LockSupport.parkNanos` 对齐，不依赖 UI 刷新；设备休眠落后超过一拍时重新对齐。
- **声画同步**：引擎暴露连续拍相位 `getBeatPhase()`，摆角取 `A·cos(π·phase)`，每拍摆到一侧极限并发出"嗒"声；停止后按阻尼摆回中位。
- **声音**：`SoundSynth` 在内存中合成钢琴（基频+泛音+击弦噪声+指数衰减）、机械 tick、木鱼、电子四组 PCM，用 4 路静态 `AudioTrack` 轮播，钢琴音可自然叠音；无音频资源文件、无第三方依赖。
- **三种摆动**：`TOP` 支点在上、摆杆向下；`BOTTOM` 经典机械式支点在下、摆杆向上；`SPRING` 顶部悬挂弹簧，随拍伸缩并轻微摆动。
- **视觉风格**：奶油黄→天蓝→薄荷绿的渐变背景叠加阳光与天空光斑，白色圆角卡片分区，暖橙主按钮、青绿辅助色、胶囊控件与大号 BPM 数字；`PendulumView` 以透明底只绘阳光光晕与明亮枫木机身。
- **状态 / 熄屏续播**：BPM、拍号、模式、音色、音量、重拍保存在 `SharedPreferences`；`MetronomeService` 以进程内单例持有引擎，旋转屏幕、按 Home 或熄屏后节拍与声音都不中断。播放时服务进入前台（`mediaPlayback`）并持有 `PARTIAL_WAKE_LOCK`，通知可返回应用或一键停止；`MainActivity.onStop()` 只解绑 UI 回调，不再停表；按返回键退出应用时才停止。
- **屏幕适配**：`MainActivity` 按 `smallestScreenWidthDp ≥ 600` 或横屏选择 `root_wide`（左右分栏），否则 `root_portrait`（上下布局）；两套布局均为「品牌行 + 摆锤卡片 + 控制卡片」，设置项在 `ScrollView` 内，开始/停止与 TAP 按钮由 `control_actions.xml` 固定在面板底部。
- **依赖**：仅 Android 平台 API + AGP 8.2.2 / Gradle 8.2，无 AndroidX 与第三方库。`settings.gradle` 优先使用阿里云 Google/Central 镜像（本机 `maven.google.com` 不可达），官方源作为后备。

## 目录

```
app/src/main/java/com/nikon/metronome/
  MainActivity.java      界面交互、设置持久化、播放状态同步
  MetronomeEngine.java   精准节拍调度与低延迟播放
  MetronomeService.java  前台服务、唤醒锁、播放通知（熄屏续播）
  SoundSynth.java        PCM 音色合成
  PendulumView.java      拟真机身 Canvas 绘制与三种摆动
  PendulumMode.java      摆动模式枚举
app/src/main/res/        布局（竖屏 / 宽屏 / 固定操作栏）、drawable、values
scripts/setup-env.ps1    自动环境准备 + 构建 + 安装
```

## 验证记录（本机 Windows 11，2026-11-13 ~ 2026-11-14）

| 项目 | 结果 |
| --- | --- |
| 环境自动准备 | 本机初始仅有 JDK 25、无 Android SDK/adb；`setup-env.ps1 -SkipBuild` 自动准备 Temurin 17.0.21、`platform-tools` / `android-34` / `build-tools;34.0.0`、Gradle 8.2 wrapper，退出码 0 |
| 无设备时自动准备运行环境 | `setup-env.ps1 -WithEmulator` 自动安装 emulator 与 x86_64 系统镜像、创建并启动 AVD、`adb install -r` 成功、`am start` 成功，退出码 0 |
| 构建与签名 | `assembleDebug` BUILD SUCCESSFUL；APK 约 62 KB；`apksigner verify` 退出码 0（v1/v2 签名有效）；`aapt2 dump badging` 显示 minSdk 23 / targetSdk 34、启动 Activity 与图标资源正确 |
| 手机界面（320×640 模拟器） | 启动无 `FATAL EXCEPTION`；清除数据后默认为 120 BPM / 下端摆动 / 钢琴音色；点击开始后状态变为"运行中"，连续两帧截图摆杆角度明显不同、拍点依次点亮 |
| 三种摆动 | 逐个切换后选中态与画面分别对应：上端支点下摆、下端支点上摆、弹簧线圈 + 球形摆锤 |
| 平板 / 横屏（1280×800、160 dpi） | 机身与控制面板左右分栏；在节拍运行中改变屏幕尺寸与密度后状态仍为"运行中"、按钮仍为"停止"，节拍未中断，无崩溃日志 |
| 华为 P50 模拟器（1224×2700 @460 dpi，sw426dp，竖屏） | `-Device P50` 自动创建 `nikon_huawei_p50`、写入 AVD 屏幕参数，启动后 `wm size` / `wm density` 复核通过；上下布局，开始/TAP 按钮完整固定在面板底部（此前会被滚动区裁掉，已修复）；点击开始后按钮变为"停止"，无 `FATAL EXCEPTION` |
| 华为平板模拟器（2560×1600 @280 dpi，sw914dp，横屏） | `-Device Pad` 同上；配置显示 `xlrg land sw914dp w1463dp`，机身与右侧控制面板左右分栏，按钮固定在右下；点击开始后按钮变为"停止"，无 `FATAL EXCEPTION` |
| 双设备同时在线 | `-Device All` 复用/启动 `emulator-5556`（P50）与 `emulator-5558`（平板），按 `adb -s <serial>` 分别 `install -r` 与 `am start` 均成功 |

说明与边界：

- 本机 `maven.google.com` 超时，项目仓库优先使用阿里云 Google/Central 镜像；工作区路径含中文，使用 AGP 官方开关 `android.overridePathCheck=true`。
- 本机 WHPX 下 `-gpu swiftshader_indirect` 曾导致 `qemu-system-x86_64.exe` 崩溃，脚本已改用 `-gpu host`；模拟器长时间运行后仍可能自行退出，重新执行 `-WithEmulator` 即可再次准备。
- 熄屏续播验证（2026-11-14，API 34 模拟器）：按电源键后 `mWakefulness=Asleep`，前台服务、`nikon:metronome` 唤醒锁与本应用 `state:started` 的 AudioTrack 仍保持；亮屏后界面仍为"运行中"；从通知点"停止"后服务、音频通道、唤醒锁与界面状态同步释放，无异常日志。
- 华为设备档位模拟的是屏幕规格与布局限定符，系统为 AOSP Android 14，不含 EMUI/HarmonyOS、厂商省电策略与挖孔屏；冷启动时系统可能出现一次 "Process system isn't responding"（keystore 初始化）以及前台服务的通知权限弹窗，点 "Wait" / "Don't allow" 后即可继续，与应用逻辑无关。
- 未验证：实体手机/平板上的音效听感与震动、无障碍与键盘操作，以及厂商省电策略下熄屏长时间续播（个别厂商需在系统设置中允许应用后台运行）。构建产物为同一 APK，通过 `minSdk 23` 与两套布局适配不同屏幕。
