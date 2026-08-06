# Java AirPlay Server

[![GitHub release](https://img.shields.io/github/v/release/serezhka/java-airplay)](https://github.com/serezhka/java-airplay/releases)
[![build](https://github.com/serezhka/java-airplay/actions/workflows/build.yaml/badge.svg)](https://github.com/serezhka/java-airplay/actions/workflows/build.yaml)
![ViewCount](https://views.whatilearened.today/views/github/serezhka/java-airplay.svg)
[![License](https://img.shields.io/badge/license-MIT-blue.svg)](http://opensource.org/licenses/MIT)

This repository is a patched, self-contained Windows x64 distribution based on the
[java-airplay](https://github.com/serezhka/java-airplay) project. It includes a bundled JRE,
GStreamer runtime, and the patched server JAR, so the package can be run without installing
Java or GStreamer separately.

Many thanks to the original author, [serezhka](https://github.com/serezhka), for creating and
maintaining the Java AirPlay implementation.

## Quick Start on Windows

1. Edit <code>application.properties</code> to change the AirPlay name, resolution, or frame rate. See the Configuration section for the available options.
2. Double-click <code>run_airplay_server.bat</code> to start Airplay Server.
3. Connect to the configured AirPlay name from a device on the same WiFi.

The launcher configures the bundled Java and GStreamer paths automatically. Windows Firewall
must allow the bundled Java process to accept the AirPlay control connection on port
<code>5001</code> and the dynamically advertised media ports.

To start the server from PowerShell instead:

~~~powershell
$env:PATH = "$PWD/jre/bin;$PWD/gstreamer/bin;$env:PATH"
$env:GST_PLUGIN_PATH = "$PWD/gstreamer/lib/gstreamer-1.0"
./jre/bin/java.exe -jar ./java-airplay-server-fixed.jar
~~~

Use <code>java-airplay-server-fixed.jar</code>; the original
<code>java-airplay-server.jar</code> is kept only as a reference and fallback copy.

## Configuration

Edit <code>application.properties</code> in the package directory:

~~~properties
# Name shown in the AirPlay device list
airplay.serverName=Mukar

# Display capabilities advertised to AirPlay senders
airplay.width=1920
airplay.height=1080
airplay.fps=60

# Player: gstreamer, ffmpeg, vlc, or h264-dump
player.implementation=gstreamer
player.tray.enabled=true
~~~

### Video Capabilities and Limits

The <code>airplay.width</code>, <code>airplay.height</code>, and
<code>airplay.fps</code> values describe the receiver to the AirPlay sender. They do not resize,
transcode, or force the sender to use the requested mode.

| Parameter | Support in this build |
| --- | --- |
| Mirroring codec | H.264/AVC byte stream, access-unit aligned, with BT.709 caps |
| Default advertised mode | 1920 x 1080, up to 60 FPS; the refresh rate is advertised as 60 Hz |
| Code-enforced maximum | None. Width, height, and FPS are signed 32-bit integers passed through without range validation; use positive values. |
| Highest verified AirPlay mode | 3840 x 2160 (4K) at 60 FPS |
| Bundled-decoder check | H.264 High Profile Level 5.2 at 3840 x 2160 and 60 FPS is accepted by the GStreamer pipeline |
| Above 4K at 60 FPS | Not verified or guaranteed |
| Bitrate, H.264 profile, and level | Not configurable or capped by the server; the sender and active player/decoder determine compatibility |

The bundled GStreamer decoder does not expose a fixed resolution, frame-rate, H.264 profile, or
level ceiling. The highest mode verified with this build is 4K at 60 FPS; sustained performance
still depends on the sender, display, host hardware, and network. Treat higher settings as
experimental and test them with the intended setup.

The GStreamer player supports video plus ALAC and AAC-ELD audio. FFmpeg mode uses FFplay for
video and the same GStreamer audio pipeline, because AirPlay sends raw codec frames that FFplay
cannot consume directly. A compatible GStreamer installation is required when using another
installation instead of the bundled runtime.

## Fixes in This Build

- **Sound no longer disappears after several minutes.** RTP audio sequence numbers are handled
  as unsigned 16-bit values, including the <code>65535 -> 0</code> wrap.
- **Sound can recover after a brief network packet loss.** A bounded reorder window prevents one
  lost UDP packet from muting the stream indefinitely.
- **Memory usage no longer grows with every audio or video buffer.** GStreamer audio and video
  buffers always call <code>unmap()</code> after a successful <code>map()</code>, before the
  buffer is pushed downstream.
- **Long-running AirPlay sessions no longer leak control-request memory.** Consumed Netty
  <code>FullHttpRequest</code> objects are released in <code>ControlHandler</code>, preventing
  the HTTP buffer leak reported by Netty's leak detector.
- **FFmpeg mode now plays audio.** FFplay continues to handle the low-latency H.264 video while
  ALAC and AAC-ELD audio are forwarded to the bundled GStreamer decoder and audio sink.
- **Device preemptive takeover no longer causes green screens or corrupted video.** Starting a new
  AirPlay session from Device B now immediately revokes Device A's control connection generation and media
  leases, drops any late video/audio frames or delayed TEARDOWN requests from Device A, and resets the
  GStreamer H.264 decoding pipeline under lock synchronization to avoid reference-frame pollution.

The fixes are implemented in <code>patch-src</code>. The packaged result is generated by
<code>build_patch.ps1</code>.

## Rebuild and Test

The bundled JDK is used to compile the patch; no network access or external build tool is
required:

~~~powershell
Set-Location C:/path/to/Druadach-java-airplay
./build_patch.ps1
~~~

The script extracts the dependencies from the original JAR, compiles the patch sources, runs
the RTP sequence, Netty reference-count, FFmpeg audio-forwarding, and preemptive session takeover
regression tests, and writes <code>java-airplay-server-fixed.jar</code>.

The FFmpeg audio path was verified with a real AirPlay sender using AAC-ELD 44.1 kHz stereo.
For a longer production check, keep audio playing for at least 15 minutes. That exceeds the
complete 16-bit RTP sequence period and verifies both the wrap fix and native-memory behavior.

## Demo

- Raspberry Pi 4 Model B, 1280 x 720 at 24 fps:
  [video](https://youtu.be/uRvgVkLWfSI)
- Windows laptop, 1920 x 1080 at 30 fps:
  [video](https://youtu.be/RT1hVWGJzos)

## Player Options

- **GStreamer**: supports video plus ALAC and AAC-ELD audio. The bundled runtime is used by
  this Windows package.
- **FFmpeg**: uses <code>ffplay</code> on PATH for video and GStreamer for ALAC/AAC-ELD audio.
- **VLC**: requires VLC on PATH.
- **h264-dump**: writes the video stream to <code>dump.h264</code>.

## Upstream Project

The original project combines [java-airplay-lib](https://github.com/serezhka/java-airplay-lib),
[java-airplay-server](https://github.com/serezhka/java-airplay-server), and
[java-airplay-server-examples](https://github.com/serezhka/java-airplay-server-examples).

Refer to the upstream repository for the full source build and the FFmpeg, VLC, and
<code>h264-dump</code> player implementations.

## License

MIT. See [LICENSE](LICENSE).