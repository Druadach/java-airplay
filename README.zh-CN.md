# Java AirPlay Server

[English](README.md) | 简体中文

[![GitHub release](https://img.shields.io/github/v/release/Druadach/java-airplay)](https://github.com/Druadach/java-airplay/releases)
[![build](https://github.com/Druadach/java-airplay/actions/workflows/build.yaml/badge.svg)](https://github.com/Druadach/java-airplay/actions/workflows/build.yaml)
![ViewCount](https://views.whatilearened.today/views/github/Druadach/java-airplay.svg)
[![License](https://img.shields.io/badge/license-MIT-blue.svg)](http://opensource.org/licenses/MIT)
[![LINUX DO](https://img.shields.io/badge/LINUX-DO-FFB003.svg)](https://linux.do)

本软件用于将 iPhone、iPad、Mac 投屏到 Windows 电脑上，最高支持 4K 60 帧。

本项目基于原作者 [serezhka](https://github.com/serezhka) 的 [serezhka/java-airplay](https://github.com/serezhka/java-airplay) 修改，下载即可使用，已包含全部运行所需组件（Java 运行环境、GStreamer 播放组件）。

<img width="2659" height="1349" alt="PixPin_2026-09-07_18-07-55" src="https://github.com/user-attachments/assets/924b4f18-005e-4978-b95c-3748f13c66fc" />

---

## v1.2.4 维护更新

- 修正 RTP 音频时间戳和 SSRC 的解析，属于协议正确性修复，不代表音质或延迟提升。
- 完善 FFmpeg 视频进程替换，以及断开连接、写入失败时的清理；内置 GStreamer 视频播放逻辑不变。
- 补充源码和打包后类的回归测试。

v1.2.4 不包含 YouTube／HLS 直投适配，待上游修复崩溃问题并验证后再恢复。

---

## 下载（EXE 安装包）

当前源码版本为 **1.2.4**，对应安装包尚未发布；以下链接仍指向已发布的 **v1.2.3** 安装包。

本仓库提供两种免配置的 Windows 分发形式，下载直链如下：

| 文件 | 类型 | 说明 |
| --- | --- | --- |
| [AirPlayReceiver_Setup_1.2.3.exe](https://github.com/Druadach/java-airplay/releases/download/v1.2.3/AirPlayReceiver_Setup_1.2.3.exe) | 安装版 | 向导式安装：可选安装目录、创建开始菜单/桌面快捷方式、可在“设置 → 应用”中卸载；安装器会对安装目录授予普通用户写权限，运行时无需管理员即可保存设置 |
| [AirPlayReceiver_Portable_1.2.3.zip](https://github.com/Druadach/java-airplay/releases/download/v1.2.3/AirPlayReceiver_Portable_1.2.3.zip) | 便携版 | 解压到任意目录，双击其中的 `AirPlayReceiver.exe` 即可；配置保存在同目录的 `application.properties` |

---

## 一、3 步开始使用

1. **启动程序**
   安装版：双击桌面或开始菜单的“AirPlay 接收器”快捷方式（或安装目录中的 `AirPlayReceiver.exe`）；
   便携版：解压后双击其中的 `AirPlayReceiver.exe`。
   也可使用 `run_airplay_gui.bat`，效果相同。

2. **配置并启动**
   默认使用 **1080p / 60 帧、内置 GStreamer、窗口模式、跟随系统语言**，不自动启动程序或接收投屏。可在窗口顶部选择“跟随系统 / System default、中文、English”。在“投屏设置”中选择投屏名称、画面分辨率和最高帧率后点击“启动AirPlay接收”，状态变为“运行中”后即可投屏。分辨率提供 `720p / 1080p / 1440p / 4K / 自定义`，预设同时显示像素尺寸，仅选择“自定义”时展开宽高输入。“播放方式”位于“高级设置”，默认选择“内置播放器（推荐）”。也可通过启动器托盘菜单直接“启动AirPlay接收 / 停止AirPlay接收”，无需打开主窗口。

   修改会自动保存，界面显示保存状态和输入错误。服务运行期间修改投屏名称、分辨率、帧率、播放方式或默认全屏模式后，会提示“重启服务后生效”；点击“重启AirPlay接收”会中断当前投屏，需要重新连接。语言与启动偏好不需要重启服务。将鼠标停在名称输入框、启动选项或操作按钮上，可查看具体说明。

3. **在苹果设备上连接**
   确认设备和电脑处于同一个局域网（连 WiFi、插网线均可）→ 打开”控制中心” → 点击”屏幕镜像” → 选择 AirPlay 服务器名字。

第一次启动时，Windows 会弹出防火墙提示，**请点击”允许”**。
如果不允许，AirPlay 会搜不到这台电脑。

`run_airplay_server.bat` 为命令行启动方式，读取同一个 `application.properties`，并按配置保留服务端旧版托盘菜单。
服务端口很少需要修改，因此未在 GUI 中展示；如需自定义端口，编辑 `application.properties` 中的 `airplay.airtunesPort`。

---

## 二、常见问题

**设备上搜不到电脑？**
- 设备和电脑是否处于同一个局域网（2.4G 和 5G 有时是两个不同的网络名）？
- GUI 启动器是否显示服务”运行中”，或命令行服务窗口是否仍然打开？
- 首次启动的防火墙提示是否点了”允许”？如果误点了”取消”，需要到
  Windows 防火墙设置里手动放行，或者卸载重装。
- 公司网络、酒店 WiFi的”AP 隔离”功能会阻止设备互相发现，这种情况下换成电脑或设备热点试试。

**画面卡顿？**
原因为路由器信号不佳，可以把 `application.properties` 里的分辨率和帧率调低，降低传输的数据量压力，比如：
```
airplay.width=1280
airplay.height=720
airplay.fps=30
```
改完后在 GUI 中重启服务，或重新启动命令行服务。

**想关掉软件？**
右键启动器托盘图标并选择“退出”。命令行模式可使用服务端托盘的 `Quit`，或关闭命令行窗口。

---

## 三、设置项说明

推荐直接在软件面板内修改。手动编辑 `application.properties` 前，请通过托盘“退出”关闭启动器，避免自动保存覆盖手动修改；下次启动时读取新配置。下面的默认配置示例可以直接复制：

```properties
airplay.serverName=AirPlay - PC
airplay.airtunesPort=5001
airplay.width=1920
airplay.height=1080
airplay.fps=60
player.implementation=gstreamer
player.gstreamer.fullscreen=false
player.tray.enabled=true
launcher.language=system
launcher.autoStart.enabled=false
launcher.autoStart.runService=false
launcher.startMinimized=false
launcher.closeToTray=true
```

- **投屏名称**：在 iPhone、iPad 或 Mac 的 AirPlay / 屏幕镜像列表中显示的名称，默认为 `AirPlay - PC`。
- **画面分辨率 / 最高帧率**：告诉发送设备本电脑可接收的画面规格，不强制改变实际画质。分辨率预设同时显示像素尺寸，例如 `1080p (1920 × 1080)`；自定义宽高的单位为像素。
- **开机自动启动**：通过界面勾选后登记当前用户的 Windows 登录启动项。是否显示主窗口由“软件启动时最小化到托盘”独立控制，是否接收投屏由“打开程序后自动接收投屏”控制。只编辑 `launcher.autoStart.enabled` 不会登记或删除 Windows 启动项。
- **打开程序后自动接收投屏**：与登录启动独立；勾选后，手动打开程序和登录启动都会自动开启 AirPlay 接收，无需点击“启动AirPlay接收”。仍需在苹果设备上选择本电脑，不会自动连接或发起投屏。
- **软件启动时最小化到托盘**：默认关闭；勾选后，手动打开和开机自动启动均隐藏主窗口，可从托盘重新打开。修改此项无需重新登记开机启动项，下次打开程序生效。
- **软件关闭时最小化到托盘**：默认开启；关闭主窗口只隐藏到托盘，不停止服务。取消后，点击关闭按钮会退出程序并停止投屏服务。托盘菜单中的“退出”始终退出程序。
- **托盘不可用时**：启动时显示主窗口，关闭按钮退出程序，避免程序隐藏后无法操作。
- **自定义分辨率**：宽高下拉候选仅显示纯数字，例如 `3840`、`2160`，仍支持手动输入。
- **投屏时默认全屏**：仅适用于内置播放器 GStreamer，控制下次启动服务的默认模式，不影响程序主窗口。下方“当前投屏窗口”按钮和 `F11 / Esc` 则即时切换当前画面，不改写默认值。
- **界面语言**：“跟随系统 / System default”对应 `system`，持续跟随系统；`zh-CN` 和 `en-US` 固定使用对应语言，切换立即生效。
- **高级设置 → 播放方式**：“内置播放器（推荐）”使用 GStreamer；FFplay 属于 FFmpeg，需要另行安装；VLC 也需要另行安装。选择外部播放器时检查对应程序是否存在，启动服务前再次检查依赖。“保存视频数据（调试）”只写入原始 `dump.h264`、不显示播放窗口，不是常规录像功能。界面名称变化不改变配置值 `gstreamer / ffmpeg / vlc / h264-dump`。
- **恢复默认设置**：确认后恢复界面选项并关闭自动启动；启动时显示主窗口、软件关闭时最小化到托盘。保留隐藏端口和配置文件中的其他选项，不立即中断正在进行的投屏。

- **运行日志**：每次启动软件时默认隐藏。点击“展开运行日志”查看，点击“收起运行日志”再次隐藏。折叠不清空日志、不停止接收，展开后可查看隐藏期间的记录。

### 检查新版本与自动更新

主窗口底部显示当前程序版本，旁边的“检查更新”和托盘菜单中的同名选项都可手动检查新版本。
检查时读取本仓库 [GitHub 最新正式版](https://github.com/Druadach/java-airplay/releases/latest) 的 `tag_name`，按数字比较版本号（例如 `1.10.0` 高于 `1.9.0`），不推荐草稿或预发布版本，也不会提示降级。

发现新版本后，可点击“自动更新”下载兼容的便携包，或点击“打开发布页”查看更新说明并手动下载。检查与下载都在后台执行，下载有进度显示，可随时取消；只有点击相应按钮才会联网，不会在开机后静默下载或安装。

自动更新先校验 GitHub 提供的 SHA-256、包内版本和文件路径。准备完成后，必须再次确认“更新并重启”，才会停止投屏、替换程序文件并重新启动；此前不会更改现有安装。**application.properties、自定义名称、画面参数和启动/托盘偏好均保留**，重启后恢复更新前的接收启停状态。选择“暂不更新”会清理本次准备文件，下次更新需重新下载。

支持完整的 Windows x64 安装版和便携版。没有兼容便携包、没有 SHA-256 校验值或旧包不支持启动确认协议时，只能手动更新。安装或新版启动验证失败时会尝试还原旧版；若文件仍被其他进程占用，会保留备份和错误记录，而不是强行覆盖。

旧运行文件保存在安装目录的 `.airplay-update/<任务 ID>/backup`，诊断记录为同一任务目录下的 `update.log`。确认新版运行正常、且没有更新正在进行后，可手动删除该任务目录以释放空间；回滚不完整时不要删除备份。

程序、“关于”和安装器共用根目录 `VERSION` 中的版本号；构建时该值嵌入启动器 JAR，运行时不需要额外的版本文件。发布前应更新 `VERSION`、重新构建并同步打包文件，GitHub Release 标签使用对应的 `vX.Y.Z`。

### 分辨率和帧率说明
这三个值只是**告诉设备“AirPlay 接收器这边支持什么”**，最终画面质量由 AirPlay 发送端决定，软件本身不会缩放或转码。
本包实测跑通的最高档是 **3840 × 2160 / 60 帧**。
GUI 接受宽度 `320–7680`、高度 `240–4320`、帧率 `1–240` 的整数；超出已验证的 4K / 60 帧范围会显示实验配置提示，不代表发送端或本机能够达到该规格。

---

## 四、播放器选项（可选）

- **GStreamer**：使用捆绑运行时播放 H.264、HEVC Main 10/HDR、ALAC 和 AAC-ELD。
- **FFmpeg**：使用 PATH 中的 <code>ffplay</code> 播放 H.264/HEVC，并使用 GStreamer 播放音频。
- **VLC**：要求 VLC 已加入 PATH。
- **h264-dump**：将视频流写入 <code>dump.h264</code>。

发送端调整 AirPlay 音量时，接收端会把 `-144..0 dB` 的音量应用到 GStreamer
音频输出。GStreamer 和 FFmpeg 模式都支持此功能，因为 FFmpeg 模式的音频仍由
GStreamer 播放；该功能不会修改 Windows 系统音量。

### 直投适配状态

当前版本已回滚 YouTube／HLS 直投适配，保留屏幕镜像功能。直投功能暂缓适配，待上游修复崩溃问题并验证后再恢复。

### GStreamer 窗口/全屏模式切换

设置以下选项可启用无标题栏全屏：

```properties
player.gstreamer.fullscreen=true
```

投屏时，视频窗口会覆盖主显示器且不显示标题栏。设置
`player.gstreamer.fullscreen=false` 可让服务以窗口模式启动。服务运行后，可通过系统托盘中的
`Fullscreen` 勾选项直接切换当前 GStreamer 窗口，无需重启服务或重新连接投屏设备。
原生视频窗口获得焦点时，按 `F11` 可切换全屏，按 `Esc` 可返回窗口模式。
实时切换使用随软件提供的 Windows D3D11 后端，在默认的 `player.gstreamer.swing=false` 设置下可用；
旧的 Swing 窗口不会显示该托盘选项。

### 使用 FFmpeg 模式

#### 前置条件

FFmpeg 模式使用 `ffplay` 播放低延迟 H.264 视频。音频仍然使用项目内置的 GStreamer，因为 AirPlay 发送的是裸 ALAC / AAC-ELD 音频码流，`ffplay` 不能直接消费这种输入。

安装包含 `ffmpeg.exe` 和 `ffplay.exe` 的 [Windows FFmpeg](https://ffmpeg.org/) 版本，然后把其中的 `bin` 目录加入 Windows 的 `Path` 环境变量。例如 FFmpeg 解压到 `C:\ffmpeg` 时，加入：

```text
C:\ffmpeg\bin
```

修改 `Path` 后，关闭并重新打开 PowerShell 或命令提示符，然后检查 `ffplay` 是否可用：

```powershell
ffplay -version
where.exe ffplay
```

命令应当输出 FFmpeg 版本以及 `ffplay.exe` 的完整路径。如果提示找不到 `ffplay`，说明 `bin` 目录没有加入 `Path`，或者下载的版本没有包含 `ffplay.exe`。

#### 启用 FFmpeg 播放

1. 打开 `application.properties`。
2. 修改播放器配置：

   ```properties
   player.implementation=ffmpeg
   ```

3. 保存文件并在 GUI 中重启，或双击 `run_airplay_server.bat` 重启命令行服务。
4. 在 iPhone、iPad 或 Mac 上使用“屏幕镜像”连接。投屏后会打开 FFplay 全屏视频窗口，投屏期间请保持该窗口和服务器黑色命令行窗口处于打开状态。

如果要恢复默认的内置播放器，把配置改回 `player.implementation=gstreamer` 并重启服务。
FFmpeg 模式的音频仍依赖项目内置的 GStreamer，请不要删除 `gstreamer` 目录。

## 五、本版本修复了哪些问题

本版本已修复原版存在以下问题：

- **连接几分钟后声音消失** —— 已修复。
- **网络轻微卡顿后，声音消失** —— 已修复，现在能自动恢复。
- **软件开久了内存占用越来越大** —— 已修复三处内存泄漏。
- **FFmpeg 模式下完全没声音** —— 已修复。
- **A 设备正在投屏时，B 设备顶掉会出现绿屏、花屏** —— 已修复，现在切换设备会干净地重置画面。

（技术细节见文末"给开发者"部分。）

---

## 六、演示视频

- 树莓派 4B，1280×720 / 24 帧：[观看](https://youtu.be/uRvgVkLWfSI)
- Windows 笔记本，1920×1080 / 30 帧：[观看](https://youtu.be/RT1hVWGJzos)

---

## 七、给开发者

<details>
<summary>展开：命令行启动、播放器选项、重新编译、技术细节</summary>

### 用 PowerShell 启动

```powershell
$env:PATH = "$PWD/jre/bin;$PWD/gstreamer/bin;$env:PATH"
$env:GST_PLUGIN_PATH = "$PWD/gstreamer/lib/gstreamer-1.0"
./jre/bin/java.exe -jar ./java-airplay-server-fixed.jar
```

请使用 `java-airplay-server-fixed.jar`；原始的 `java-airplay-server.jar`
仅作参考和回退副本保留。

服务监听控制连接端口 `5001`，媒体端口动态分配。

### 播放器选项（`player.implementation`）

| 值 | 说明 |
| --- | --- |
| `gstreamer` | 默认。视频 + ALAC / AAC-ELD 音频，使用本包内置运行时 |
| `ffmpeg` | 视频用 PATH 上的 `ffplay`，音频仍走 GStreamer（AirPlay 发送的是裸码流，FFplay 无法直接消费） |
| `vlc` | 需要 PATH 上有 VLC |
| `h264-dump` | 把视频码流写入 `dump.h264` |

### 视频能力与限制

| 项目 | 本构建的情况 |
| --- | --- |
| 镜像编解码 | H.264/AVC 字节流，access-unit 对齐，BT.709 caps |
| 默认对外声明 | 1920×1080，最高 60 FPS，刷新率声明为 60 Hz |
| GUI 输入范围 | 宽度 320–7680，高度 240–4320，最高帧率 1–240；超过已验证范围会提示 |
| 命令行服务 | 宽/高/FPS 为 32 位有符号整数，直接透传，不经过 GUI 范围校验 |
| 已验证最高模式 | 3840×2160 (4K) @ 60 FPS |
| 内置解码器验证 | H.264 High Profile Level 5.2 @ 3840×2160 60 FPS 可被 GStreamer 管线接受 |
| 4K 60 以上 | 未验证，不保证 |
| 码率 / Profile / Level | 服务端不配置也不限制，由发送端与播放器决定 |

### 修复的技术细节

- 移植上游 [f51244f](https://github.com/serezhka/java-airplay/commit/f51244f074b6a7c918a33bbaf91d8858fe391cde) 的 RTP 音频头修复：时间戳和 SSRC 按网络字节序解析为无符号 32 位值，修正符号扩展及 SSRC 取错字节的问题；源码和补丁 JAR 均有对应回归测试。
- RTP 音频序列号按无符号 16 位处理，正确处理 `65535 -> 0` 回绕。
- 有界重排序窗口，避免单个 UDP 丢包导致音频永久静音。
- GStreamer 音视频缓冲在 `map()` 成功后、推送下游前必定 `unmap()`。
- GStreamer 窗口和无边框全屏共用 Direct3D 11 原生 sink，可通过系统托盘、`F11` 和 `Esc` 实时切换。
- 托盘 `Quit` 在后台执行 Spring 清理；超过 500 毫秒会强制结束进程，避免 Bonjour 注销阻塞导致窗口延迟关闭。
- `ControlHandler` 中释放已消费的 Netty `FullHttpRequest`，
  消除 Netty leak detector 报告的 HTTP 缓冲泄漏。
- FFmpeg 模式下 FFplay 负责低延迟 H.264 视频，
  ALAC / AAC-ELD 音频转发到内置 GStreamer 解码器与音频 sink。
- 同步上游 [0992fcf](https://github.com/serezhka/java-airplay/commit/0992fcf93c579e243996cc9492b8dc2ee2646521) 的 FFmpeg 视频进程防护：串行处理视频回调，替换时关闭旧进程及其输入流，跳过无可用进程的视频帧；写入异常时也清理进程，源码和打包 JAR 均有无窗口回归测试。
- 解析 RTSP `SET_PARAMETER` 的 AirPlay 音量并实时应用到当前 GStreamer 音频管线，
  `GET_PARAMETER` 返回当前值；不会修改 Windows 系统音量。
- 设备抢占接管时，立即吊销前一设备的控制连接 generation 与媒体租约，丢弃迟到的音视频帧和延迟的 TEARDOWN 请求，并在锁同步下重置 GStreamer H.264 解码管线，避免参考帧污染。

补丁源码位于 `patch-src`，打包产物由 `build_patch.ps1` 生成。

### 重新编译与测试

使用内置 JDK 编译，无需联网或额外构建工具：

```powershell
Set-Location C:/path/to/Druadach-java-airplay
./build_patch.ps1
```

脚本会从原始 JAR 提取依赖，编译服务端补丁和 Swing 启动器，在源码和成品布局下运行服务端回归测试，并运行启动器核心测试与成品安装校验，最后输出
`java-airplay-server-fixed.jar` 和 `java-airplay-launcher.jar`。

### 打包 Windows EXE

安装版 EXE 与便携版 ZIP 由 `packaging` 目录下的脚本生成（Inno Setup + 原生启动器 + 图标），步骤与依赖见 `packaging/README.md`。

FFmpeg 音频路径已用真实 AirPlay 发送端以 AAC-ELD 44.1 kHz 立体声验证。
生产环境建议连续播放音频 15 分钟以上，这超过完整的 16 位 RTP 序列周期，
可同时验证回绕修复和原生内存行为。

</details>

---

## 上游项目与许可

上游项目由 [java-airplay-lib](https://github.com/serezhka/java-airplay-lib)、
[java-airplay-server](https://github.com/serezhka/java-airplay-server)、
[java-airplay-server-examples](https://github.com/serezhka/java-airplay-server-examples)
组成。完整源码构建请参考上游仓库。

许可证：MIT，见 [LICENSE](LICENSE)。
