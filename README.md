# Java AirPlay Server

English | [简体中文](README.zh-CN.md)

[![GitHub release](https://img.shields.io/github/v/release/Druadach/java-airplay)](https://github.com/Druadach/java-airplay/releases)
[![build](https://github.com/Druadach/java-airplay/actions/workflows/build.yaml/badge.svg)](https://github.com/Druadach/java-airplay/actions/workflows/build.yaml)
![ViewCount](https://views.whatilearened.today/views/github/Druadach/java-airplay.svg)
[![License](https://img.shields.io/badge/license-MIT-blue.svg)](http://opensource.org/licenses/MIT)
[![LINUX DO](https://img.shields.io/badge/LINUX-DO-FFB003.svg)](https://linux.do)

This software allows you to mirror iPhone, iPad, and Mac screens to a Windows PC, supporting up to 4K @ 60 FPS.

This project is modified based on [serezhka/java-airplay](https://github.com/serezhka/java-airplay) by [serezhka](https://github.com/serezhka). Download and run it directly; all required runtime components (Java Runtime Environment and GStreamer playback components) are included.

<img width="2658" height="1349" alt="PixPin_2026-09-07_18-09-04" src="https://github.com/user-attachments/assets/4c9228cb-c910-4ec4-88a5-1a7e77c0dc76" />

---

## Download (EXE Packages)

This repository provides two ready-to-run Windows distributions. Direct links:

| File | Type | Description |
| --- | --- | --- |
| [AirPlayReceiver_Setup_1.2.3.exe](https://github.com/Druadach/java-airplay/releases/download/v1.2.3/AirPlayReceiver_Setup_1.2.3.exe) | Installer | Guided setup: choose the install folder, create Start-menu/desktop shortcuts, uninstall from "Settings → Apps"; the installer grants write permission on the install folder so settings can be saved without admin rights |
| [AirPlayReceiver_Portable_1.2.3.zip](https://github.com/Druadach/java-airplay/releases/download/v1.2.3/AirPlayReceiver_Portable_1.2.3.zip) | Portable | Extract anywhere and double-click `AirPlayReceiver.exe`; configuration is kept in `application.properties` next to the exe |

---

## 1. Quick Start in 3 Steps

1. **Launch the App**
   Installed build: double-click the `AirPlay Receiver` desktop/Start-menu shortcut (or `AirPlayReceiver.exe` in the install folder); portable build: extract the ZIP and double-click `AirPlayReceiver.exe` inside.
   The original `run_airplay_gui.bat` works identically.

2. **Configure and Start**
   Defaults are **1080p / 60 FPS, built-in GStreamer, windowed mode, and system language**, with automatic startup disabled. Select `跟随系统 / System default`, `中文`, or `English` at the top of the window. Under `AirPlay Settings`, choose an AirPlay name, video resolution, and maximum frame rate, then click `Start`. Resolution presets are `720p / 1080p / 1440p / 4K / Custom`, with their pixel dimensions shown; width and height inputs appear only for `Custom`. `Playback Mode` is under `Advanced Settings` and defaults to `Built-in player (recommended)`. The status changes to `Running` when AirPlay is ready. The service can also be started or stopped directly from the launcher tray menu.

   Changes save automatically with visible save status and validation errors. Changing the AirPlay name, video capabilities, playback mode, or default fullscreen mode while running shows a restart notice. `Apply & Restart` interrupts the current casting session; reconnect afterwards. Language and startup preferences do not require a service restart. Hover over the name field, startup options, or action buttons for explanations.

3. **Connect from Apple Devices**
   Ensure your mobile device/Mac and PC are on the same local network (Wi-Fi or wired Ethernet both work) → Open "Control Center" → Tap "Screen Mirroring" → Select your AirPlay server name.

> **Note:** On the first run, Windows Firewall will display a security prompt. **Please click "Allow access."** If denied, AirPlay will not be able to discover this PC.

The original `run_airplay_server.bat` command-line launcher remains available. It reads the same `application.properties` file and keeps the server's legacy tray menu enabled when configured.

---

## 2. Frequently Asked Questions (FAQ)

**Cannot find the PC on my Apple device?**
- Are the device and PC on the same local network? (2.4 GHz and 5 GHz networks are sometimes isolated under different SSIDs).
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

Use the control panel for normal changes. Before editing `application.properties` manually, exit the launcher through its tray menu so automatic saves cannot overwrite your edits. The next launch reads the updated file. This default configuration can be copied directly:

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

- **AirPlay Name** appears in the AirPlay / Screen Mirroring list on your iPhone, iPad, or Mac. The default is `AirPlay - PC`.
- **Video Resolution / Max Frame Rate (fps)** tell the sender what this PC can receive, rather than forcing the actual video quality. Presets include pixel dimensions, such as `1080p (1920 × 1080)`, and custom width and height are measured in pixels.
- **Start with Windows** registers a sign-in startup entry for the current user. Separate preferences control window visibility and automatic AirPlay reception. Editing `launcher.autoStart.enabled` alone does not update the Windows registry.
- **Receive AirPlay when the app opens** enables reception without clicking `Start`, independently of Windows startup. It works for both manual and automatic launches. You still need to select this PC on your Apple device; this option does not automatically connect or start mirroring.
- **Start minimized to tray** is off by default. When enabled, both manual and Windows startup hide the main window; reopen it from the tray. Changes apply on the next launch without updating the startup registry entry.
- **Close to tray** is on by default. Closing the main window hides it without stopping the service. When unchecked, closing exits the app and stops casting. The tray menu's `Exit` action always exits.
- **Without a system tray**, startup shows the main window and closing exits, so the application cannot become inaccessible.
- **Custom resolution** width and height choices are plain numbers such as `3840` and `2160`, and still accept keyboard input.
- **Start AirPlay in fullscreen** sets the next built-in GStreamer playback mode, not the main app window. Buttons under `Current AirPlay Window` and `F11 / Esc` change the current video window immediately without changing this preference.
- **Language** uses `跟随系统 / System default` (`system`) to keep following the OS, or `zh-CN` / `en-US` for an explicit language. Changes apply immediately.
- **Advanced Settings → Playback Mode** offers `Built-in player (recommended)` (GStreamer), FFplay from FFmpeg, and VLC. External players require a separate installation, checked on selection and before service startup. `Save raw video (debug)` writes raw `dump.h264` data without a playback window; it is not a normal recording feature. The underlying configuration values remain `gstreamer / ffmpeg / vlc / h264-dump`.
- **Restore Defaults** resets visible preferences and disables automatic startup after confirmation. Startup shows the main window, and closing minimizes to tray. Hidden ports and other configuration-file options are preserved; the current casting session continues until you restart the service.

- **Runtime Log** is hidden on each app launch. Click `Show Runtime Log` to expand it and `Hide Runtime Log` to collapse it. Collapsing does not clear the history or stop reception; messages received while hidden remain available.

### Checking for Updates and Automatic Installation

The bottom of the main window shows the current app version. Use `Check for Updates` there or in the tray menu to check manually.
The launcher reads `tag_name` from this repository's [latest stable GitHub release](https://github.com/Druadach/java-airplay/releases/latest), compares numeric versions (`1.10.0` is newer than `1.9.0`), excludes drafts/prereleases, and never offers a downgrade.

When a newer version is available, choose `Update Automatically` to download a compatible portable package, or `Open Release Page` to read the changes and download manually. Checks and downloads run in the background. Downloads show progress and can be cancelled. Network access requires your click; there are no unattended startup downloads or installations.

The updater verifies the GitHub SHA-256 digest, bundled version and archive paths before asking you to `Update and Restart`. Only this second confirmation stops casting and replaces the runtime. **Your application.properties, AirPlay name, video options and startup/tray preferences are preserved**, and reception returns to its previous running/stopped state after restart. Choosing `Not Now` removes the staged download; you can download it again later.

Complete Windows x64 installer and portable distributions are supported. Missing compatible assets/digests, or packages without the startup-confirmation protocol, require a manual update. Installation or startup validation failure triggers a rollback attempt. If another process prevents restoration, backups and diagnostics are retained rather than forcibly overwritten.

Previous runtime files remain under `.airplay-update/<job ID>/backup` in the installation directory, alongside `update.log`. After confirming the new version works, and while no update is running, you may delete that job directory to reclaim space. Do not delete backups after an incomplete rollback.

The app, About dialog, and installer share the root `VERSION` file. The build embeds it in the launcher JAR, so no external version file is required at runtime. Before publishing, update `VERSION`, rebuild and refresh the packaging stage, and use the matching `vX.Y.Z` GitHub Release tag.

### Resolution and Frame Rate
These three parameters only **declare to the sender what the AirPlay receiver supports**. The actual stream quality is determined by the AirPlay sender device; the server itself does not perform downscaling or transcoding.
The highest profile verified in this build is **3840 × 2160 @ 60 FPS**.
The GUI accepts integer widths of `320–7680`, heights of `240–4320`, and maximum frame rates of `1–240`. Values above the verified 4K / 60 FPS range show an experimental warning; accepting a value does not guarantee sender or receiver support.

---

## 4. Player Options (Optional)

- **GStreamer**: Uses the bundled runtime to play H.264, HEVC Main 10/HDR, ALAC, and AAC-ELD.
- **FFmpeg**: Uses `ffplay` from `Path` to play H.264/HEVC and uses GStreamer for audio.
- **VLC**: Requires VLC to be available on `Path`.
- **h264-dump**: Writes the video stream to `dump.h264`.

When the sender changes its AirPlay volume, the receiver applies the requested
`-144..0 dB` level to the GStreamer audio output. This works in both GStreamer
and FFmpeg modes because FFmpeg mode uses GStreamer for audio; it does not change
the Windows system volume.

### Direct Playback Status

YouTube / HLS direct playback integration has been rolled back and is not included in this build. Screen mirroring remains available; direct playback integration is deferred until upstream crash fixes are available and verified.

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
| GUI Input Bounds | Width 320–7680, height 240–4320, maximum FPS 1–240; values above the verified range show a warning. |
| Command-line Server | Width/height/FPS are signed 32-bit integers passed directly, without GUI range validation. |
| Highest Verified Mode | 3840×2160 (4K) @ 60 FPS |
| Decoder Validation | H.264 High Profile Level 5.2 @ 3840×2160 60 FPS accepted by GStreamer pipeline |
| Beyond 4K 60 FPS | Unverified; not guaranteed |
| Bitrate / Profile / Level | Not limited or enforced by server; determined by sender and player backend |

### Technical Details of Fixes

- **RTP Audio Header:** Backported [upstream f51244f](https://github.com/serezhka/java-airplay/commit/f51244f074b6a7c918a33bbaf91d8858fe391cde) to decode timestamps and SSRC as unsigned 32-bit network-order values, fixing sign extension and incorrect SSRC byte selection. Regression tests cover both source classes and the patched server JAR.
- **RTP Audio Sequence Number:** Treated sequence numbers as unsigned 16-bit integers to correctly handle the `65535 -> 0` rollover.
- **Audio Jitter Buffer:** Implemented a bounded reordering window to prevent single UDP packet drops from causing permanent audio muting.
- **GStreamer Memory Safety:** Ensured `unmap()` is strictly called on GStreamer audio/video buffers after a successful `map()`, prior to downstream pushing.
- **GStreamer Fullscreen:** Uses one native Direct3D 11 sink for windowed and borderless fullscreen playback, with live switching from the system tray, `F11`, and `Esc`.
- **System Tray Quit:** Performs Spring cleanup in the background and forces process termination after 500 ms so a blocked Bonjour shutdown cannot keep the application windows open.
- **Netty Buffer Leak:** Released consumed `FullHttpRequest` objects in `ControlHandler`, resolving HTTP buffer leaks reported by Netty leak detector.
- **FFmpeg Audio Mode:** Configured FFplay to handle low-latency H.264 video, while forwarding raw ALAC / AAC-ELD audio streams to the internal GStreamer decoder and audio sink.
- **FFmpeg Video Lifecycle:** Adapted the video-process safeguards from [upstream 0992fcf](https://github.com/serezhka/java-airplay/commit/0992fcf93c579e243996cc9492b8dc2ee2646521): serialize video callbacks, close old input streams and processes on replacement, and skip frames without a live process. Failed writes also clean up the process; headless regression tests cover source and packaged classes.
- **AirPlay Volume:** Parses RTSP `SET_PARAMETER` volume updates, applies them to the active GStreamer audio pipeline, and reports the current value through `GET_PARAMETER` without changing Windows system volume.
- **Preemptive Session Hijacking:** On device switch, immediately revokes the previous device's control connection generation and media lease, drops late audio/video frames and delayed TEARDOWN requests, and synchronously resets the GStreamer H.264 decoding pipeline to eliminate reference frame corruption.

Patch sources are located in `patch-src`. The build artifacts are generated via `build_patch.ps1`.

### Recompilation and Testing

Compile using the bundled JDK (no internet access or external build tools required):

```powershell
Set-Location C:/path/to/Druadach-java-airplay
./build_patch.ps1
```

The script extracts dependencies from the original JAR, compiles the server patch and Swing launcher, runs the server regression suites in source and packaged layouts, runs the launcher core tests and packaged installation validation, and outputs `java-airplay-server-fixed.jar` plus `java-airplay-launcher.jar`.

### Building the Windows EXE Packages

The installer EXE and portable ZIP are produced from the scripts in the `packaging` directory (Inno Setup + native launcher + icon); see `packaging/README.md` for the exact steps.

The FFmpeg audio path has been verified with a real AirPlay sender transmitting AAC-ELD 44.1 kHz stereo audio. For production environments, continuous audio playback for >15 minutes is recommended to exceed a full 16-bit RTP sequence cycle, validating both rollover fixes and native memory stability.

</details>

---

## Upstream Projects and Licensing

Upstream repository structure:
- [java-airplay-lib](https://github.com/serezhka/java-airplay-lib)
- [java-airplay-server](https://github.com/serezhka/java-airplay-server)
- [java-airplay-server-examples](https://github.com/serezhka/java-airplay-server-examples)

License: MIT. See [LICENSE](LICENSE) for details.
