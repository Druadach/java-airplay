package com.github.serezhka.airplay.app.config;

import com.github.serezhka.airplay.app.control.LocalControlServer;
import com.github.serezhka.airplay.app.lifecycle.ApplicationShutdown;
import com.github.serezhka.airplay.app.menu.SystemTrayMenu;
import com.github.serezhka.airplay.player.ffmpeg.FFmpegPlayer;
import com.github.serezhka.airplay.player.gstreamer.GstPlayerFullscreen;
import com.github.serezhka.airplay.player.gstreamer.GstPlayerSwing;
import com.github.serezhka.airplay.player.h264dump.H264Dump;
import com.github.serezhka.airplay.player.vlc.VlcPlayer;
import com.github.serezhka.airplay.server.AirPlayConfig;
import com.github.serezhka.airplay.server.AirPlayConsumer;
import com.github.serezhka.airplay.server.AirPlayServer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PlayerConfig {

    @Bean
    @ConditionalOnProperty(value = "player.implementation", havingValue = "gstreamer")
    public AirPlayConsumer gstreamer(
            @Value("#{new Boolean('${player.gstreamer.swing}')}") boolean useSwing,
            @Value("#{new Boolean('${player.gstreamer.fullscreen:true}')}") boolean fullscreen) {
        if (useSwing && !fullscreen) {
            return new GstPlayerSwing();
        }
        return new GstPlayerFullscreen(fullscreen);
    }

    @Bean
    @ConditionalOnProperty(value = "player.implementation", havingValue = "h264-dump", matchIfMissing = true)
    public AirPlayConsumer h264dump() throws Exception {
        return new H264Dump();
    }

    @Bean
    @ConditionalOnProperty(value = "player.implementation", havingValue = "vlc")
    public AirPlayConsumer vlc() {
        return new VlcPlayer();
    }

    @Bean
    @ConditionalOnProperty(value = "player.implementation", havingValue = "ffmpeg")
    public AirPlayConsumer ffmpeg() {
        return new FFmpegPlayer();
    }

    @Bean
    @ConfigurationProperties(prefix = "airplay")
    public AirPlayConfig airPlayConfig() {
        return new AirPlayConfig();
    }

    @Bean
    public ApplicationShutdown applicationShutdown(ApplicationContext context) {
        return new ApplicationShutdown(context);
    }

    @Bean
    @ConditionalOnProperty(value = "player.tray.enabled", havingValue = "true")
    public SystemTrayMenu systemTrayMenu(
            ApplicationShutdown applicationShutdown,
            AirPlayConsumer airPlayConsumer) {
        return new SystemTrayMenu(applicationShutdown, airPlayConsumer);
    }

    @Bean
    @ConditionalOnProperty(value = "launcher.control.enabled", havingValue = "true")
    public LocalControlServer localControlServer(
            @Value("${launcher.control.port:0}") int port,
            @Value("${launcher.control.token:}") String token,
            ApplicationShutdown applicationShutdown,
            AirPlayConsumer airPlayConsumer) {
        return new LocalControlServer(port, token, applicationShutdown, airPlayConsumer);
    }

    @Bean
    public AirPlayServer airPlayServer(AirPlayConfig airPlayConfig,
                                       AirPlayConsumer airPlayConsumer) {
        return new AirPlayServer(airPlayConfig, airPlayConsumer);
    }
}
