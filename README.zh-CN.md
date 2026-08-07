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

1. **修改 AirPlay 服务器名字（可选）**
   用记事本打开 `application.properties`，修改 `airplay.serverName=Mukar` 里的 `Mukar`

2. **启动**
   双击 `run_airplay_server.bat`。
   会弹出一个黑色命令行窗口，**这是正常的，不要关掉**，关掉就等于关闭软件。

3. **在苹果设备上连接**
   确认手机和电脑连的是同一个 WiFi → 打开"控制中心" → 点击"屏幕镜像" → 选择 AirPlay 服务器名字。

第一次启动时，Windows 会弹出防火墙提示，**请点击“允许”**。
如果不允许，AirPlay 会搜不到这台电脑。

---

## 二、常见问题

**手机上搜不到电脑？**
- 手机和电脑是否连的是同一个 WiFi（注意 2.4G 和 5G 有时是两个不同的网络名）？
- 黑色命令行窗口是否还开着？
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
改完需要重启软件（关掉黑窗口，重新双击 `.bat`）。

**想关掉软件？**
直接关闭黑色命令行窗口。

---

## 三、设置项说明

用记事本打开 `application.properties` 修改，**改完必须重启软件才生效**。

```properties
airplay.serverName=Mukar          # AirPlay 服务器名
airplay.width=1920                # 画面宽度
airplay.height=1080               # 画面高度
airplay.fps=60                    # 每秒帧数
player.implementation=gstreamer   # 播放方式，支持 gstreamer、ffmpeg、vlc、h264-dump
player.tray.enabled=true          # 是否显示系统托盘图标
```

关于分辨率和帧率的说明：
这三个值只是**告诉设备“AirPlay 接收端这边支持什么”**，最终画面质量由 AirPlay 发送端决定，软件本身不会缩放或转码。
本包实测跑通的最高档是 **3840 × 2160 / 60 帧**。

---

## 四、本版本修复了哪些问题

本版本已修复原版存在以下问题：

- **连接几分钟后声音消失** —— 已修复。
- **网络轻微卡顿后，声音消失** —— 已修复，现在能自动恢复。
- **软件开久了内存占用越来越大** —— 已修复三处内存泄漏。
- **FFmpeg 模式下完全没声音** —— 已修复。
- **A 设备正在投屏时，B 设备顶掉会出现绿屏、花屏** —— 已修复，现在切换设备会干净地重置画面。

（技术细节见文末"给开发者"部分。）

---

## 五、演示视频

- 树莓派 4B，1280×720 / 24 帧：[观看](https://youtu.be/uRvgVkLWfSI)
- Windows 笔记本，1920×1080 / 30 帧：[观看](https://youtu.be/RT1hVWGJzos)

---

## 六、给开发者

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

脚本会从原始 JAR 提取依赖、编译补丁源码，运行 RTP 序列号、Netty 引用计数、
FFmpeg 音频转发、抢占式会话接管四组回归测试，并输出
`java-airplay-server-fixed.jar`。

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