# Java AirPlay Server

English | [简体中文](README.zh-CN.md)

[![GitHub release](https://img.shields.io/github/v/release/Druadach/java-airplay)](https://github.com/Druadach/java-airplay/releases)
[![build](https://github.com/Druadach/java-airplay/actions/workflows/build.yaml/badge.svg)](https://github.com/Druadach/java-airplay/actions/workflows/build.yaml)
![ViewCount](https://views.whatilearened.today/views/github/Druadach/java-airplay.svg)
[![License](https://img.shields.io/badge/license-MIT-blue.svg)](http://opensource.org/licenses/MIT)

This software allows you to mirror iPhone, iPad, and Mac screens to a Windows PC, supporting up to 4K @ 60 FPS.

This project is modified based on the original project [serezhka/java-airplay](https://github.com/serezhka/java-airplay) by [serezhka](https://github.com/serezhka). All required runtime components (Java Runtime Environment and GStreamer playback components) are pre-packaged. No additional installation is required—just download and run out of the box.

---

## 1. Quick Start in 3 Steps

1. **Open the Launcher**
   Double-click `run_airplay_gui.bat`. The launcher uses the bundled Java runtime and does not open a command prompt window.

2. **Configure and Start**
   Switch between `中文` and `English` from the top of the window at any time. Set the server name, resolution, frame rate, player, and startup display mode; changes are saved automatically. Then click `Start`. Each width and height choice is labeled with its resolution tier (`1K / 2K / 2.5K / 4K`, for example, `3840 (4K)` and `2160 (4K)`) and remains editable for custom keyboard input. The status changes to `Running` when AirPlay is ready.

3. **Connect from Apple Devices**
   Ensure your mobile device/Mac and PC are connected to the same Wi-Fi network → Open "Control Center" → Tap "Screen Mirroring" → Select your AirPlay server name.

> **Note:** On the first run, Windows Firewall will display a security prompt. **Please click "Allow access."** If denied, AirPlay will not be able to discover this PC.

The original `run_airplay_server.bat` command-line launcher remains available. It reads the same `application.properties` file and keeps the server's legacy tray menu enabled when configured.
The server port rarely needs adjustment, so it is hidden from the GUI. Edit `airplay.airtunesPort` in `application.properties` when a custom port is required.

---

## 2. Frequently Asked Questions (FAQ)

**Cannot find the PC on my Apple device?**
- Are the phone and PC on the exact same Wi-Fi network? (Note: 2.4 GHz and 5 GHz networks are sometimes isolated under different SSIDs).
- Does the GUI launcher show the service as running, or is the command-line server window still open?
- Did you click "Allow" on the Windows Firewall prompt during the first launch? If you accidentally clicked "Cancel," you need to manually allow it in Windows Firewall settings or reinstall the app.
- "AP Isolation" on corporate or hotel Wi-Fi networks blocks device-to-device discovery. In such cases, try using a mobile hotspot or PC hotspot instead.

**Laggy or stuttering playback?**
This is typically caused by poor router signals. You can lower the resolution and frame rate in `application.properties` to reduce bandwidth pressure:
```properties
airplay.width=1280
airplay.height=720
airplay.fps=30
```
After changing the values, restart the service from the GUI or restart the command-line server.

**How to close the application?**
Right-click the launcher tray icon and select `Exit`. In command-line mode, use the server tray's `Quit` item or close its command prompt window.

---

## 3. Configuration Settings

Open `application.properties` with Notepad to edit settings. **Changes will only take effect after restarting the software.**

```properties
airplay.serverName=Mukar          # Device name visible on sender devices
airplay.airtunesPort=5001         # AirPlay control port
airplay.width=1920                # Screen width
airplay.height=1080               # Screen height
airplay.fps=60                    # Frames per second (higher = smoother, but uses more resources)
player.implementation=gstreamer   # Player backend (gstreamer and ffmpeg are verified working)
player.gstreamer.fullscreen=false # Use borderless fullscreen with GStreamer
player.tray.enabled=true          # Enable or disable system tray icon
launcher.language=en-US           # GUI language: zh-CN or en-US
```

**Regarding Resolution and Frame Rate:**
These three parameters only **declare to the sender what the AirPlay receiver supports**. The actual stream quality is determined by the AirPlay sender device; the server itself does not perform downscaling or transcoding.
The highest profile verified in this build is **3840 × 2160 @ 60 FPS**.

---

## 4. Player Options (Optional)

- **GStreamer**: Uses the bundled runtime to play H.264, HEVC Main 10/HDR, ALAC, and AAC-ELD.
- **FFmpeg**: Uses `ffplay` from `Path` to play H.264/HEVC and uses GStreamer for audio.
- **VLC**: Requires VLC to be available on `Path`.
- **h264-dump**: Writes the video stream to `dump.h264`.

### GStreamer Borderless Fullscreen

Enable borderless fullscreen with this option:

```properties
player.gstreamer.fullscreen=true
```

The video window covers the primary display without a title bar while mirroring. Set
`player.gstreamer.fullscreen=false` to start in windowed mode. While the server is running, use the system tray's
`Fullscreen` checkbox to switch the active GStreamer window without restarting the server or reconnecting the sender.
When the native video window has focus, `F11` toggles fullscreen and `Esc` returns to windowed mode.
Live switching uses the bundled Windows D3D11 backend and is available with the default
`player.gstreamer.swing=false` setting. The legacy Swing window does not expose this tray control.

### FFmpeg

#### Prerequisites

FFmpeg mode uses the `ffplay` executable for low-latency H.264 video playback. Audio continues to use the bundled GStreamer runtime because AirPlay sends raw ALAC / AAC-ELD audio streams that `ffplay` cannot consume directly.

Install a [Windows FFmpeg](https://ffmpeg.org/) build that includes both `ffmpeg.exe` and `ffplay.exe`, then add the build's `bin` directory to the Windows `Path` environment variable. For example, if FFmpeg is extracted to `C:\ffmpeg`, add:

```text
C:\ffmpeg\bin
```

Close and reopen PowerShell or Command Prompt after changing `Path`, then verify that `ffplay` can be found:

```powershell
ffplay -version
where.exe ffplay
```

The commands should print the FFmpeg version and the full path to `ffplay.exe`. If `ffplay` is not recognized, the `bin` directory is not on `Path`, or the downloaded build does not include `ffplay.exe`.

#### Enable FFmpeg playback

1. Open `application.properties`.
2. Change the player implementation:

   ```properties
   player.implementation=ffmpeg
   ```

3. Save the file and restart from the GUI, or double-click `run_airplay_server.bat` to restart the command-line server.
4. Connect from an iPhone, iPad, or Mac using Screen Mirroring. FFplay will open a full-screen video window; keep that window and the server command prompt open while mirroring.

To return to the default bundled player, set `player.implementation=gstreamer` and restart the server. The FFmpeg mode still requires the bundled GStreamer files for audio, so do not remove the `gstreamer` directory.

## 5. Fixes in This Release

This version resolves several issues present in the upstream release:

- **Audio dropouts after a few minutes** — Fixed.
- **Audio loss following minor network jitter** — Fixed; audio now recovers automatically.
- **Increasing memory usage over time** — Fixed three native/heap memory leak instances.
- **Complete silence in FFmpeg mode** — Fixed.
- **Green screen / video artifacts when Device B interrupts Device A's stream** — Fixed; switching devices now cleanly resets the rendering pipeline.

*(For technical details, see the "For Developers" section below.)*

---

## 6. Demo Videos

- Raspberry Pi 4B, 1280×720 / 24 FPS: [Watch](https://youtu.be/uRvgVkLWfSI)
- Windows Laptop, 1920×1080 / 30 FPS: [Watch](https://youtu.be/RT1hVWGJzos)

---

## 7. For Developers

<details>
<summary>Expand: PowerShell Launch, Player Options, Recompilation, and Technical Details</summary>

### Launch via PowerShell

```powershell
$env:PATH = "$PWD/jre/bin;$PWD/gstreamer/bin;$env:PATH"
$env:GST_PLUGIN_PATH = "$PWD/gstreamer/lib/gstreamer-1.0"
./jre/bin/java.exe -jar ./java-airplay-server-fixed.jar
```

Please use `java-airplay-server-fixed.jar`. The original `java-airplay-server.jar` is kept as a reference and fallback backup.

The service listens on port `5001` for control connections; media ports are assigned dynamically.

### Player Implementations (`player.implementation`)

| Value | Description |
| --- | --- |
| `gstreamer` | **Default.** Video + ALAC / AAC-ELD audio, using the bundled runtime. |
| `ffmpeg` | Uses `ffplay` on system PATH for video; audio is still routed through GStreamer (FFplay cannot directly consume raw AirPlay streams). |
| `vlc` | Requires VLC installed on system PATH. |
| `h264-dump` | Dumps raw video stream to `dump.h264`. |

### Video Capabilities and Limitations

| Item | Status in this Build |
| --- | --- |
| Mirroring Codec | H.264/AVC byte-stream, access-unit aligned, BT.709 caps |
| Default Announcement | 1920×1080, max 60 FPS, refresh rate declared as 60 Hz |
| Code-level Limit | None. Width/Height/FPS are signed 32-bit integers passed directly without range checks; use positive values. |
| Highest Verified Mode | 3840×2160 (4K) @ 60 FPS |
| Decoder Validation | H.264 High Profile Level 5.2 @ 3840×2160 60 FPS accepted by GStreamer pipeline |
| Beyond 4K 60 FPS | Unverified; not guaranteed |
| Bitrate / Profile / Level | Not limited or enforced by server; determined by sender and player backend |

### Technical Details of Fixes

- **RTP Audio Sequence Number:** Treated sequence numbers as unsigned 16-bit integers to correctly handle the `65535 -> 0` rollover.
- **Audio Jitter Buffer:** Implemented a bounded reordering window to prevent single UDP packet drops from causing permanent audio muting.
- **GStreamer Memory Safety:** Ensured `unmap()` is strictly called on GStreamer audio/video buffers after a successful `map()`, prior to downstream pushing.
- **GStreamer Fullscreen:** Uses one native Direct3D 11 sink for windowed and borderless fullscreen playback, with live switching from the system tray, `F11`, and `Esc`.
- **System Tray Quit:** Performs Spring cleanup in the background and forces process termination after 500 ms so a blocked Bonjour shutdown cannot keep the application windows open.
- **Netty Buffer Leak:** Released consumed `FullHttpRequest` objects in `ControlHandler`, resolving HTTP buffer leaks reported by Netty leak detector.
- **FFmpeg Audio Mode:** Configured FFplay to handle low-latency H.264 video, while forwarding raw ALAC / AAC-ELD audio streams to the internal GStreamer decoder and audio sink.
- **Preemptive Session Hijacking:** On device switch, immediately revokes the previous device's control connection generation and media lease, drops late audio/video frames and delayed TEARDOWN requests, and synchronously resets the GStreamer H.264 decoding pipeline to eliminate reference frame corruption.

Patch sources are located in `patch-src`. The build artifacts are generated via `build_patch.ps1`.

### Recompilation and Testing

Compile using the bundled JDK (no internet access or external build tools required):

```powershell
Set-Location C:/path/to/Druadach-java-airplay
./build_patch.ps1
```

The script extracts dependencies from the original JAR, compiles the server patch and Swing launcher, runs the server regression suites in source and packaged layouts, runs the launcher core tests and packaged installation validation, and outputs `java-airplay-server-fixed.jar` plus `java-airplay-launcher.jar`.

The FFmpeg audio path has been verified with a real AirPlay sender transmitting AAC-ELD 44.1 kHz stereo audio. For production environments, continuous audio playback for >15 minutes is recommended to exceed a full 16-bit RTP sequence cycle, validating both rollover fixes and native memory stability.

</details>

---

## Upstream Projects and Licensing

Upstream repository structure:
- [java-airplay-lib](https://github.com/serezhka/java-airplay-lib)
- [java-airplay-server](https://github.com/serezhka/java-airplay-server)
- [java-airplay-server-examples](https://github.com/serezhka/java-airplay-server-examples)

License: MIT. See [LICENSE](LICENSE) for details.
