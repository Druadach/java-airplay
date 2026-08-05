package com.github.serezhka.airplay.player.ffmpeg;

import com.github.serezhka.airplay.lib.AudioStreamInfo;
import com.github.serezhka.airplay.lib.VideoStreamInfo;
import com.github.serezhka.airplay.player.gstreamer.GstPlayerDefault;
import com.github.serezhka.airplay.server.AirPlayConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Objects;

public class FFmpegPlayer implements AirPlayConsumer {
    private static final Logger log = LoggerFactory.getLogger(FFmpegPlayer.class);

    private final AirPlayConsumer audioConsumer;
    private Process h264Process;

    public FFmpegPlayer() {
        this(new GstPlayerDefault());
    }

    FFmpegPlayer(AirPlayConsumer audioConsumer) {
        this.audioConsumer = Objects.requireNonNull(audioConsumer, "audioConsumer");
    }

    @Override
    public void onVideoFormat(VideoStreamInfo videoStreamInfo) {
        try {
            ProcessBuilder processBuilder = new ProcessBuilder(
                    "ffplay", "-fs", "-f", "h264", "-codec:v", "h264", "-probesize", "32",
                    "-analyzeduration", "0", "-vf", "setpts=0", "-flags", "low_delay", "-");
            processBuilder.redirectOutput(ProcessBuilder.Redirect.INHERIT);
            processBuilder.redirectError(ProcessBuilder.Redirect.INHERIT);
            h264Process = processBuilder.start();
        } catch (IOException exception) {
            throw new RuntimeException(exception);
        }
    }

    @Override
    public void onVideo(byte[] bytes) {
        try {
            h264Process.getOutputStream().write(bytes);
            h264Process.getOutputStream().flush();
        } catch (IOException exception) {
            log.error(exception.getMessage());
        }
    }

    @Override
    public void onVideoSrcDisconnect() {
        h264Process.destroy();
    }

    @Override
    public void onAudioFormat(AudioStreamInfo audioStreamInfo) {
        audioConsumer.onAudioFormat(audioStreamInfo);
    }

    @Override
    public void onAudio(byte[] bytes) {
        audioConsumer.onAudio(bytes);
    }

    @Override
    public void onAudioSrcDisconnect() {
        audioConsumer.onAudioSrcDisconnect();
    }
}
