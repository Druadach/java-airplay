package com.github.serezhka.airplay.player.gstreamer;

import com.github.serezhka.airplay.lib.AudioStreamInfo;
import com.github.serezhka.airplay.lib.VideoStreamInfo;
import com.github.serezhka.airplay.server.AirPlayConsumer;
import org.freedesktop.gstreamer.Buffer;
import org.freedesktop.gstreamer.Caps;
import org.freedesktop.gstreamer.Format;
import org.freedesktop.gstreamer.Gst;
import org.freedesktop.gstreamer.Pipeline;
import org.freedesktop.gstreamer.Version;
import org.freedesktop.gstreamer.elements.AppSrc;
import org.freedesktop.gstreamer.glib.GLib;

import java.nio.ByteBuffer;

public abstract class GstPlayer implements AirPlayConsumer {
    private static final String H264_CAPS =
            "video/x-h264,colorimetry=bt709,stream-format=(string)byte-stream,alignment=(string)au";
    private static final String ALAC_CAPS =
            "audio/x-alac,mpegversion=(int)4,channels=(int)2,rate=(int)44100,stream-format=raw,"
                    + "codec_data=(buffer)00000024616c616300000000000001600010280a0e0200ff00000000000000000000ac44";
    private static final String AAC_ELD_CAPS =
            "audio/mpeg,mpegversion=(int)4,channnels=(int)2,rate=(int)44100,stream-format=raw,"
                    + "codec_data=(buffer)f8e85000";

    static {
        GstPlayerUtils.configurePaths();
        GLib.setEnv("GST_DEBUG", "3", true);
        Gst.init(Version.of(1, 10), "BasicPipeline");
    }

    protected final Pipeline h264Pipeline;
    private final Pipeline alacPipeline;
    private final Pipeline aacEldPipeline;

    private final AppSrc h264Src;
    private final AppSrc alacSrc;
    private final AppSrc aacEldSrc;

    private final Object videoPipelineLock = new Object();

    private AudioStreamInfo.CompressionType audioCompressionType;

    public GstPlayer() {
        h264Pipeline = createH264Pipeline();
        h264Src = (AppSrc) h264Pipeline.getElementByName("h264-src");
        configureSource(h264Src, H264_CAPS);

        alacPipeline = (Pipeline) Gst.parseLaunch(
                "appsrc name=alac-src ! avdec_alac ! audioconvert ! audioresample ! autoaudiosink sync=false");
        alacSrc = (AppSrc) alacPipeline.getElementByName("alac-src");
        configureSource(alacSrc, ALAC_CAPS);

        aacEldPipeline = (Pipeline) Gst.parseLaunch(
                "appsrc name=aac-eld-src ! avdec_aac ! audioconvert ! audioresample ! autoaudiosink sync=false");
        aacEldSrc = (AppSrc) aacEldPipeline.getElementByName("aac-eld-src");
        configureSource(aacEldSrc, AAC_ELD_CAPS);
    }

    protected abstract Pipeline createH264Pipeline();

    @Override
    public void onVideoFormat(VideoStreamInfo videoStreamInfo) {
        synchronized (videoPipelineLock) {
            h264Pipeline.stop();
            h264Pipeline.play();
        }
    }

    @Override
    public void onVideo(byte[] bytes) {
        synchronized (videoPipelineLock) {
            h264Src.pushBuffer(createBuffer(bytes));
        }
    }

    @Override
    public void onVideoSrcDisconnect() {
        synchronized (videoPipelineLock) {
            h264Pipeline.stop();
        }
    }

    @Override
    public void onAudioFormat(AudioStreamInfo audioStreamInfo) {
        audioCompressionType = audioStreamInfo.getCompressionType();
        alacPipeline.play();
        aacEldPipeline.play();
    }

    @Override
    public void onAudio(byte[] bytes) {
        AppSrc audioSrc;
        if (audioCompressionType == AudioStreamInfo.CompressionType.ALAC) {
            audioSrc = alacSrc;
        } else if (audioCompressionType == AudioStreamInfo.CompressionType.AAC_ELD) {
            audioSrc = aacEldSrc;
        } else {
            return;
        }

        audioSrc.pushBuffer(createBuffer(bytes));
    }

    @Override
    public void onAudioSrcDisconnect() {
        alacPipeline.stop();
        aacEldPipeline.stop();
    }

    private static void configureSource(AppSrc source, String caps) {
        source.setStreamType(AppSrc.StreamType.STREAM);
        source.setCaps(Caps.fromString(caps));
        source.set("is-live", true);
        source.set("format", Format.TIME);
        source.set("emit-signals", true);
    }

    private static Buffer createBuffer(byte[] bytes) {
        Buffer buffer = new Buffer(bytes.length);
        ByteBuffer mappedBuffer = buffer.map(true);
        if (mappedBuffer == null) {
            throw new IllegalStateException("Unable to map GStreamer buffer");
        }
        try {
            mappedBuffer.put(bytes);
        } finally {
            buffer.unmap();
        }
        return buffer;
    }
}
