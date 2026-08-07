# Java AirPlay Server

[English](README.md) | 简体中文

[![GitHub release](https://img.shields.io/github/v/release/Druadach/java-airplay)](https://github.com/Druadach/java-airplay/releases)
[![build](https://github.com/Druadach/java-airplay/actions/workflows/build.yaml/badge.svg)](https://github.com/Druadach/java-airplay/actions/workflows/build.yaml)
![ViewCount](https://views.whatilearened.today/views/github/Druadach/java-airplay.svg)
[![License](https://img.shields.io/badge/license-MIT-blue.svg)](http://opensource.org/licenses/MIT)

本软件用于将 iPhone、iPad、Mac 投屏到 Windows 电脑上，最高支持 4K 60 帧。

本项目基于原作者 [serezhka](https://github.com/serezhka) 的 [serezhka/java-airplay](https://github.com/serezhka/java-airplay) 修改，已经把运行所需组件（Java 运行环境、GStreamer 播放组件）全部打包在内，无需另外安装，下载即可使用。

---

## 一、3 步开始使用

1. **打开启动器**
   双击 `run_airplay_gui.bat`。启动器使用项目内置 Java，不会弹出命令行窗口。

2. **配置并启动**
   可在窗口顶部随时切换“中文 / English”。设置服务名称、分辨率、帧率、播放器和启动显示模式；修改会自动保存，然后点击“启动”。每个宽度和高度候选都会标注对应的分辨率档位 `1K / 2K / 2.5K / 4K`（例如 `3840 (4K)`、`2160 (4K)`），也可直接键盘输入自定义数值。状态变为“运行中”后即可投屏。

3. **在苹果设备上连接**
   确认手机和电脑连的是同一个 WiFi → 打开"控制中心" → 点击"屏幕镜像" → 选择 AirPlay 服务器名字。

第一次启动时，Windows 会弹出防火墙提示，**请点击“允许”**。
如果不允许，AirPlay 会搜不到这台电脑。

原有的 `run_airplay_server.bat` 命令行启动方式继续保留。它读取同一个 `application.properties`，并可继续使用服务端原有托盘菜单。
服务端口通常无需调整，因此 GUI 不显示该字段；需要修改时可编辑 `application.properties` 中的 `airplay.airtunesPort`。

---

## 二、常见问题

**手机上搜不到电脑？**
- 手机和电脑是否连的是同一个 WiFi（注意 2.4G 和 5G 有时是两个不同的网络名）？
- GUI 启动器是否显示服务“运行中”，或命令行服务窗口是否仍然打开？
- 首次启动的防火墙提示是否点了"允许"？如果误点了"取消"，需要到
  Windows 防火墙设置里手动放行，或者卸载重装。
- 公司网络、酒店 WiFi的"AP 隔离"功能会阻止设备互相发现，这种情况下换成电脑或手机热点试试。

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

用记事本打开 `application.properties` 修改，**改完必须重启软件才生效**。

```properties
airplay.serverName=Mukar          # AirPlay 服务器名
airplay.airtunesPort=5001         # AirPlay 控制端口
airplay.width=1920                # 画面宽度
airplay.height=1080               # 画面高度
airplay.fps=60                    # 每秒帧数
player.implementation=gstreamer   # 播放方式，支持 gstreamer、ffmpeg、vlc、h264-dump
player.gstreamer.fullscreen=false # GStreamer 模式是否全屏
player.tray.enabled=true          # 是否显示系统托盘图标
launcher.language=zh-CN           # GUI 语言：zh-CN 或 en-US
```

关于分辨率和帧率的说明：
这三个值只是**告诉设备“AirPlay 接收端这边支持什么”**，最终画面质量由 AirPlay 发送端决定，软件本身不会缩放或转码。
本包实测跑通的最高档是 **3840 × 2160 / 60 帧**。

---

## 四、播放器选项（可选）

- **GStreamer**：使用捆绑运行时播放 H.264、HEVC Main 10/HDR、ALAC 和 AAC-ELD。
- **FFmpeg**：使用 PATH 中的 <code>ffplay</code> 播放 H.264/HEVC，并使用 GStreamer 播放音频。
- **VLC**：要求 VLC 已加入 PATH。
- **h264-dump**：将视频流写入 <code>dump.h264</code>。

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
| 代码层上限 | 无。宽/高/FPS 为 32 位有符号整数，直接透传，无范围校验，请填正数 |
| 已验证最高模式 | 3840×2160 (4K) @ 60 FPS |
| 内置解码器验证 | H.264 High Profile Level 5.2 @ 3840×2160 60 FPS 可被 GStreamer 管线接受 |
| 4K 60 以上 | 未验证，不保证 |
| 码率 / Profile / Level | 服务端不配置也不限制，由发送端与播放器决定 |

### 修复的技术细节

- RTP 音频序列号按无符号 16 位处理，正确处理 `65535 -> 0` 回绕。
- 有界重排序窗口，避免单个 UDP 丢包导致音频永久静音。
- GStreamer 音视频缓冲在 `map()` 成功后、推送下游前必定 `unmap()`。
- GStreamer 窗口和无边框全屏共用 Direct3D 11 原生 sink，可通过系统托盘、`F11` 和 `Esc` 实时切换。
- 托盘 `Quit` 在后台执行 Spring 清理；超过 500 毫秒会强制结束进程，避免 Bonjour 注销阻塞导致窗口延迟关闭。
- `ControlHandler` 中释放已消费的 Netty `FullHttpRequest`，
  消除 Netty leak detector 报告的 HTTP 缓冲泄漏。
- FFmpeg 模式下 FFplay 负责低延迟 H.264 视频，
  ALAC / AAC-ELD 音频转发到内置 GStreamer 解码器与音频 sink。
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
```
