package com.github.serezhka.airplay.player.ffmpeg;

import com.github.serezhka.airplay.lib.AudioStreamInfo;
import com.github.serezhka.airplay.lib.VideoStreamInfo;
import com.github.serezhka.airplay.player.gstreamer.GstPlayerDefault;
import com.github.serezhka.airplay.server.AirPlayConsumer;
import com.github.serezhka.airplay.server.VolumeController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Objects;

public class FFmpegPlayer implements AirPlayConsumer, VolumeController {
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
    public synchronized void onVideoFormat(VideoStreamInfo videoStreamInfo) {
        stopVideoProcess();
        try {
            ProcessBuilder processBuilder = new ProcessBuilder(
                    "ffplay", "-fs", "-f", "h264", "-codec:v", "h264", "-probesize", "32",
                    "-analyzeduration", "0", "-vf", "setpts=0", "-flags", "low_delay", "-");
            processBuilder.redirectOutput(ProcessBuilder.Redirect.INHERIT);
            processBuilder.redirectError(ProcessBuilder.Redirect.INHERIT);
            h264Process = startVideoProcess(processBuilder);
        } catch (IOException exception) {
            throw new RuntimeException(exception);
        }
    }

    @Override
    public synchronized void onVideo(byte[] bytes) {
        if (h264Process == null) {
            return;
        }
        if (!h264Process.isAlive()) {
            stopVideoProcess();
            return;
        }
        try {
            OutputStream input = h264Process.getOutputStream();
            input.write(bytes);
            input.flush();
        } catch (IOException exception) {
            log.error("Could not write video frame to FFplay", exception);
            stopVideoProcess();
        }
    }

    @Override
    public synchronized void onVideoSrcDisconnect() {
        stopVideoProcess();
    }

    Process startVideoProcess(ProcessBuilder processBuilder) throws IOException {
        return processBuilder.start();
    }

    private void stopVideoProcess() {
        Process process = h264Process;
        h264Process = null;
        if (process == null) {
            return;
        }
        try {
            process.getOutputStream().close();
        } catch (IOException exception) {
            log.debug("Could not close FFplay input stream", exception);
        } finally {
            process.destroy();
        }
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

    @Override
    public void setVolumeDb(double volumeDb) {
        if (audioConsumer instanceof VolumeController volumeController) {
            volumeController.setVolumeDb(volumeDb);
        }
    }
}
