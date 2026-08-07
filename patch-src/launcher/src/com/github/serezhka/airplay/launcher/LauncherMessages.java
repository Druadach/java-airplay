package com.github.serezhka.airplay.launcher;

import java.text.MessageFormat;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

public final class LauncherMessages {
    public enum Key {
        APPLICATION_TITLE,
        LANGUAGE_LABEL,
        CONFIGURATION_SECTION,
        SERVER_NAME_LABEL,
        SERVER_PORT_LABEL,
        WIDTH_LABEL,
        HEIGHT_LABEL,
        FPS_LABEL,
        PLAYER_LABEL,
        DIRECTORY_LABEL,
        START_FULLSCREEN,
        SAVE_CONFIGURATION,
        START,
        STOP,
        RESTART,
        DISPLAY_SECTION,
        FULLSCREEN,
        WINDOWED,
        RUNTIME_LOG,
        CLEAR,
        TRAY_OPEN,
        TRAY_EXIT,

        STATE_STOPPED,
        STATE_STARTING,
        STATE_RUNNING,
        STATE_STOPPING,
        STATE_FAILED,
        UPTIME,

        DETAIL_SERVICE_NOT_STARTED,
        DETAIL_STARTING_SERVICE,
        DETAIL_WAITING_CONTROL_CHANNEL,
        DETAIL_START_FAILED,
        DETAIL_STOPPING_SERVICE,
        DETAIL_FULLSCREEN,
        DETAIL_WINDOWED,
        DETAIL_SERVICE_RUNNING,
        DETAIL_SERVICE_INITIALIZING,
        DETAIL_CONTROL_UNAVAILABLE,
        DETAIL_ABNORMAL_EXIT,

        DIALOG_SAVE_SUCCESS_TITLE,
        DIALOG_SAVE_SUCCESS_MESSAGE,
        DIALOG_LAUNCHER_ERROR_TITLE,
        DIALOG_LAUNCHER_START_ERROR_TITLE,
        DIALOG_SAVE_ERROR_TITLE,
        DIALOG_START_ERROR_TITLE,
        DIALOG_STOP_ERROR_TITLE,
        DIALOG_RESTART_ERROR_TITLE,
        DIALOG_FULLSCREEN_ERROR_TITLE,
        DIALOG_WINDOWED_ERROR_TITLE,

        LOG_LAUNCHER_READY,
        LOG_CONFIGURATION_SAVED,
        LOG_EXITING,
        LOG_EXCESS_DROPPED,
        LOG_PROCESS_STARTED,
        LOG_QUIT_ACCEPTED,
        LOG_QUIT_FALLBACK,
        LOG_SWITCHED_FULLSCREEN,
        LOG_SWITCHED_WINDOWED,
        LOG_CONTROL_DISCONNECTED,
        LOG_SERVER_OUTPUT_READ_FAILED,
        LOG_PROCESS_EXITED,
        LOG_STATE_LISTENER_FAILED,

        VALIDATION_ICON_MISSING,
        VALIDATION_INSTALLATION_NOT_FOUND,
        VALIDATION_INVALID_INSTALLATION,
        VALIDATION_FAILED,
        CONFIG_INTEGER_REQUIRED,
        VALIDATION_SERVER_NAME,
        VALIDATION_SERVER_PORT,
        VALIDATION_RESOLUTION,
        VALIDATION_FPS,
        VALIDATION_PLAYER,

        ERROR_LAUNCHER_CLOSING,
        ERROR_LAUNCHER_CLOSED_DURING_START,
        ERROR_MISSING_JAVA_RUNTIME,
        ERROR_MISSING_SERVER_JAR,
        ERROR_MISSING_CONFIGURATION,
        ERROR_SERVICE_NOT_RUNNING,
        ERROR_CONFIGURATION_NO_PARENT,
        ERROR_INVALID_STATUS_RESPONSE,
        ERROR_INVALID_FULLSCREEN_RESPONSE,
        ERROR_INVALID_QUIT_RESPONSE,
        ERROR_INVALID_CONTROL_ENDPOINT,
        ERROR_MISSING_CONTROL_RESPONSE,
        ERROR_CONTROL_REJECTED,
        ERROR_INVALID_CONTROL_BOOLEAN,
        ERROR_CAUSE_PREFIX
    }

    private static final EnumMap<UiLanguage, EnumMap<Key, String>> CATALOG = createCatalog();

    private LauncherMessages() {
    }

    public static String text(UiLanguage language, Key key, Object... arguments) {
        Objects.requireNonNull(language, "language");
        Objects.requireNonNull(key, "key");
        String pattern = CATALOG.get(language).get(key);
        if (arguments == null || arguments.length == 0) {
            return pattern;
        }
        return new MessageFormat(pattern, language.locale()).format(arguments);
    }

    public static String failureText(UiLanguage language, Throwable failure) {
        Throwable current = failure;
        while ((current instanceof java.util.concurrent.CompletionException
                || current instanceof java.util.concurrent.ExecutionException)
                && current.getCause() != null) {
            current = current.getCause();
        }
        if (current instanceof LocalizedFailure localized) {
            Object[] arguments = localized.messageArguments();
            for (int index = 0; index < arguments.length; index++) {
                if (arguments[index] instanceof Throwable cause) {
                    arguments[index] = failureText(language, cause);
                }
            }
            return text(language, localized.messageKey(), arguments);
        }
        String message = current.getMessage();
        return message == null || message.isBlank() ? current.getClass().getSimpleName() : message;
    }

    private static EnumMap<UiLanguage, EnumMap<Key, String>> createCatalog() {
        EnumMap<UiLanguage, EnumMap<Key, String>> catalog = new EnumMap<>(UiLanguage.class);
        catalog.put(UiLanguage.ZH_CN, chinese());
        catalog.put(UiLanguage.EN_US, english());
        validate(catalog);
        return catalog;
    }

    private static EnumMap<Key, String> chinese() {
        EnumMap<Key, String> messages = new EnumMap<>(Key.class);
        messages.put(Key.APPLICATION_TITLE, "Java AirPlay 启动器");
        messages.put(Key.LANGUAGE_LABEL, "语言");
        messages.put(Key.CONFIGURATION_SECTION, "服务配置");
        messages.put(Key.SERVER_NAME_LABEL, "服务名称");
        messages.put(Key.SERVER_PORT_LABEL, "服务端口");
        messages.put(Key.WIDTH_LABEL, "宽度");
        messages.put(Key.HEIGHT_LABEL, "高度");
        messages.put(Key.FPS_LABEL, "帧率");
        messages.put(Key.PLAYER_LABEL, "播放器");
        messages.put(Key.DIRECTORY_LABEL, "目录: {0}");
        messages.put(Key.START_FULLSCREEN, "启动时全屏");
        messages.put(Key.SAVE_CONFIGURATION, "保存配置");
        messages.put(Key.START, "启动");
        messages.put(Key.STOP, "停止");
        messages.put(Key.RESTART, "重启");
        messages.put(Key.DISPLAY_SECTION, "播放窗口");
        messages.put(Key.FULLSCREEN, "全屏");
        messages.put(Key.WINDOWED, "窗口模式");
        messages.put(Key.RUNTIME_LOG, "运行日志");
        messages.put(Key.CLEAR, "清空");
        messages.put(Key.TRAY_OPEN, "打开");
        messages.put(Key.TRAY_EXIT, "退出");

        messages.put(Key.STATE_STOPPED, "未启动");
        messages.put(Key.STATE_STARTING, "正在启动");
        messages.put(Key.STATE_RUNNING, "运行中");
        messages.put(Key.STATE_STOPPING, "正在停止");
        messages.put(Key.STATE_FAILED, "异常停止");
        messages.put(Key.UPTIME, "运行时间 {0}");

        messages.put(Key.DETAIL_SERVICE_NOT_STARTED, "服务未启动");
        messages.put(Key.DETAIL_STARTING_SERVICE, "正在启动服务");
        messages.put(Key.DETAIL_WAITING_CONTROL_CHANNEL, "等待服务控制通道");
        messages.put(Key.DETAIL_START_FAILED, "启动失败: {0}");
        messages.put(Key.DETAIL_STOPPING_SERVICE, "正在停止服务");
        messages.put(Key.DETAIL_FULLSCREEN, "全屏模式");
        messages.put(Key.DETAIL_WINDOWED, "窗口模式");
        messages.put(Key.DETAIL_SERVICE_RUNNING, "服务运行中");
        messages.put(Key.DETAIL_SERVICE_INITIALIZING, "服务正在初始化");
        messages.put(Key.DETAIL_CONTROL_UNAVAILABLE, "控制通道暂不可用");
        messages.put(Key.DETAIL_ABNORMAL_EXIT, "服务异常退出，代码 {0}");

        messages.put(Key.DIALOG_SAVE_SUCCESS_TITLE, "保存完成");
        messages.put(Key.DIALOG_SAVE_SUCCESS_MESSAGE, "配置已保存。已运行的服务需要重启后应用配置。");
        messages.put(Key.DIALOG_LAUNCHER_ERROR_TITLE, "Java AirPlay 启动器错误");
        messages.put(Key.DIALOG_LAUNCHER_START_ERROR_TITLE, "无法启动 Java AirPlay 启动器");
        messages.put(Key.DIALOG_SAVE_ERROR_TITLE, "无法保存配置");
        messages.put(Key.DIALOG_START_ERROR_TITLE, "启动服务失败");
        messages.put(Key.DIALOG_STOP_ERROR_TITLE, "停止服务失败");
        messages.put(Key.DIALOG_RESTART_ERROR_TITLE, "重启服务失败");
        messages.put(Key.DIALOG_FULLSCREEN_ERROR_TITLE, "无法切换到全屏");
        messages.put(Key.DIALOG_WINDOWED_ERROR_TITLE, "无法切换到窗口模式");

        messages.put(Key.LOG_LAUNCHER_READY, "启动器已就绪，配置文件: {0}");
        messages.put(Key.LOG_CONFIGURATION_SAVED, "配置已保存");
        messages.put(Key.LOG_EXITING, "正在退出启动器并停止服务");
        messages.put(Key.LOG_EXCESS_DROPPED, "已丢弃 {0} 行过量日志");
        messages.put(Key.LOG_PROCESS_STARTED, "已启动服务进程 PID {0}，控制端口 127.0.0.1:{1}");
        messages.put(Key.LOG_QUIT_ACCEPTED, "服务已接受 QUIT 请求");
        messages.put(Key.LOG_QUIT_FALLBACK, "控制通道退出失败，将回退到进程终止: {0}");
        messages.put(Key.LOG_SWITCHED_FULLSCREEN, "已切换到全屏模式");
        messages.put(Key.LOG_SWITCHED_WINDOWED, "已切换到窗口模式");
        messages.put(Key.LOG_CONTROL_DISCONNECTED, "服务控制通道连接中断: {0}");
        messages.put(Key.LOG_SERVER_OUTPUT_READ_FAILED, "读取服务日志失败: {0}");
        messages.put(Key.LOG_PROCESS_EXITED, "服务进程已退出，代码 {0}");
        messages.put(Key.LOG_STATE_LISTENER_FAILED, "状态监听器失败: {0}");

        messages.put(Key.VALIDATION_ICON_MISSING, "启动器图标资源缺失");
        messages.put(Key.VALIDATION_INSTALLATION_NOT_FOUND,
                "找不到 java-airplay-server-fixed.jar 和 jre 目录，请使用 --base-dir 指定安装目录");
        messages.put(Key.VALIDATION_INVALID_INSTALLATION, "无效的安装目录: {0}");
        messages.put(Key.VALIDATION_FAILED, "启动器验证失败: {0}");
        messages.put(Key.CONFIG_INTEGER_REQUIRED, "{0} 必须是整数");
        messages.put(Key.VALIDATION_SERVER_NAME, "服务名称长度必须为 1 到 64 个字符");
        messages.put(Key.VALIDATION_SERVER_PORT, "服务端口范围为 1 到 65535");
        messages.put(Key.VALIDATION_RESOLUTION, "分辨率范围为 320x240 到 7680x4320");
        messages.put(Key.VALIDATION_FPS, "帧率范围为 1 到 240");
        messages.put(Key.VALIDATION_PLAYER, "不支持的播放器: {0}");

        messages.put(Key.ERROR_LAUNCHER_CLOSING, "启动器正在关闭");
        messages.put(Key.ERROR_LAUNCHER_CLOSED_DURING_START, "启动器已在服务启动期间关闭");
        messages.put(Key.ERROR_MISSING_JAVA_RUNTIME, "缺少 Java 运行时: {0}");
        messages.put(Key.ERROR_MISSING_SERVER_JAR, "缺少服务端 JAR: {0}");
        messages.put(Key.ERROR_MISSING_CONFIGURATION, "缺少外部配置文件: {0}");
        messages.put(Key.ERROR_SERVICE_NOT_RUNNING, "服务未运行");
        messages.put(Key.ERROR_CONFIGURATION_NO_PARENT, "配置文件路径没有父目录: {0}");
        messages.put(Key.ERROR_INVALID_STATUS_RESPONSE, "无效的 STATUS 响应");
        messages.put(Key.ERROR_INVALID_FULLSCREEN_RESPONSE, "无效的 FULLSCREEN 响应");
        messages.put(Key.ERROR_INVALID_QUIT_RESPONSE, "无效的 QUIT 响应");
        messages.put(Key.ERROR_INVALID_CONTROL_ENDPOINT, "无效的控制端点");
        messages.put(Key.ERROR_MISSING_CONTROL_RESPONSE, "控制响应缺失或过长");
        messages.put(Key.ERROR_CONTROL_REJECTED, "控制服务拒绝请求: {0}");
        messages.put(Key.ERROR_INVALID_CONTROL_BOOLEAN, "控制响应包含无效布尔值: {0}");
        messages.put(Key.ERROR_CAUSE_PREFIX, "底层错误: {0}");
        return messages;
    }

    private static EnumMap<Key, String> english() {
        EnumMap<Key, String> messages = new EnumMap<>(Key.class);
        messages.put(Key.APPLICATION_TITLE, "Java AirPlay Launcher");
        messages.put(Key.LANGUAGE_LABEL, "Language");
        messages.put(Key.CONFIGURATION_SECTION, "Server Configuration");
        messages.put(Key.SERVER_NAME_LABEL, "Server Name");
        messages.put(Key.SERVER_PORT_LABEL, "Server Port");
        messages.put(Key.WIDTH_LABEL, "Width");
        messages.put(Key.HEIGHT_LABEL, "Height");
        messages.put(Key.FPS_LABEL, "Frame Rate");
        messages.put(Key.PLAYER_LABEL, "Player");
        messages.put(Key.DIRECTORY_LABEL, "Directory: {0}");
        messages.put(Key.START_FULLSCREEN, "Start in Fullscreen");
        messages.put(Key.SAVE_CONFIGURATION, "Save Configuration");
        messages.put(Key.START, "Start");
        messages.put(Key.STOP, "Stop");
        messages.put(Key.RESTART, "Restart");
        messages.put(Key.DISPLAY_SECTION, "Playback Window");
        messages.put(Key.FULLSCREEN, "Fullscreen");
        messages.put(Key.WINDOWED, "Windowed");
        messages.put(Key.RUNTIME_LOG, "Runtime Log");
        messages.put(Key.CLEAR, "Clear");
        messages.put(Key.TRAY_OPEN, "Open");
        messages.put(Key.TRAY_EXIT, "Exit");

        messages.put(Key.STATE_STOPPED, "Stopped");
        messages.put(Key.STATE_STARTING, "Starting");
        messages.put(Key.STATE_RUNNING, "Running");
        messages.put(Key.STATE_STOPPING, "Stopping");
        messages.put(Key.STATE_FAILED, "Failed");
        messages.put(Key.UPTIME, "Uptime {0}");

        messages.put(Key.DETAIL_SERVICE_NOT_STARTED, "Service has not started");
        messages.put(Key.DETAIL_STARTING_SERVICE, "Starting service");
        messages.put(Key.DETAIL_WAITING_CONTROL_CHANNEL, "Waiting for the service control channel");
        messages.put(Key.DETAIL_START_FAILED, "Startup failed: {0}");
        messages.put(Key.DETAIL_STOPPING_SERVICE, "Stopping service");
        messages.put(Key.DETAIL_FULLSCREEN, "Fullscreen mode");
        messages.put(Key.DETAIL_WINDOWED, "Windowed mode");
        messages.put(Key.DETAIL_SERVICE_RUNNING, "Service is running");
        messages.put(Key.DETAIL_SERVICE_INITIALIZING, "Service is initializing");
        messages.put(Key.DETAIL_CONTROL_UNAVAILABLE, "Control channel is temporarily unavailable");
        messages.put(Key.DETAIL_ABNORMAL_EXIT, "Service exited unexpectedly with code {0}");

        messages.put(Key.DIALOG_SAVE_SUCCESS_TITLE, "Saved");
        messages.put(Key.DIALOG_SAVE_SUCCESS_MESSAGE,
                "Configuration saved. Restart the running service to apply the changes.");
        messages.put(Key.DIALOG_LAUNCHER_ERROR_TITLE, "Java AirPlay Launcher Error");
        messages.put(Key.DIALOG_LAUNCHER_START_ERROR_TITLE, "Unable to Start Java AirPlay Launcher");
        messages.put(Key.DIALOG_SAVE_ERROR_TITLE, "Unable to Save Configuration");
        messages.put(Key.DIALOG_START_ERROR_TITLE, "Unable to Start Service");
        messages.put(Key.DIALOG_STOP_ERROR_TITLE, "Unable to Stop Service");
        messages.put(Key.DIALOG_RESTART_ERROR_TITLE, "Unable to Restart Service");
        messages.put(Key.DIALOG_FULLSCREEN_ERROR_TITLE, "Unable to Enter Fullscreen");
        messages.put(Key.DIALOG_WINDOWED_ERROR_TITLE, "Unable to Enter Windowed Mode");

        messages.put(Key.LOG_LAUNCHER_READY, "Launcher ready. Configuration file: {0}");
        messages.put(Key.LOG_CONFIGURATION_SAVED, "Configuration saved");
        messages.put(Key.LOG_EXITING, "Exiting the launcher and stopping the service");
        messages.put(Key.LOG_EXCESS_DROPPED, "Dropped {0} excess log lines");
        messages.put(Key.LOG_PROCESS_STARTED, "Started service process PID {0}, control port 127.0.0.1:{1}");
        messages.put(Key.LOG_QUIT_ACCEPTED, "Service accepted the QUIT request");
        messages.put(Key.LOG_QUIT_FALLBACK, "Control-channel quit failed; falling back to process termination: {0}");
        messages.put(Key.LOG_SWITCHED_FULLSCREEN, "Switched to fullscreen mode");
        messages.put(Key.LOG_SWITCHED_WINDOWED, "Switched to windowed mode");
        messages.put(Key.LOG_CONTROL_DISCONNECTED, "Service control channel disconnected: {0}");
        messages.put(Key.LOG_SERVER_OUTPUT_READ_FAILED, "Unable to read service output: {0}");
        messages.put(Key.LOG_PROCESS_EXITED, "Service process exited with code {0}");
        messages.put(Key.LOG_STATE_LISTENER_FAILED, "State listener failed: {0}");

        messages.put(Key.VALIDATION_ICON_MISSING, "Launcher icon resource is missing");
        messages.put(Key.VALIDATION_INSTALLATION_NOT_FOUND,
                "Could not find java-airplay-server-fixed.jar and the jre directory; specify the installation with --base-dir");
        messages.put(Key.VALIDATION_INVALID_INSTALLATION, "Invalid installation directory: {0}");
        messages.put(Key.VALIDATION_FAILED, "Launcher validation failed: {0}");
        messages.put(Key.CONFIG_INTEGER_REQUIRED, "{0} must be an integer");
        messages.put(Key.VALIDATION_SERVER_NAME, "Server name must contain 1 to 64 characters");
        messages.put(Key.VALIDATION_SERVER_PORT, "Server port must be between 1 and 65535");
        messages.put(Key.VALIDATION_RESOLUTION, "Resolution must be between 320x240 and 7680x4320");
        messages.put(Key.VALIDATION_FPS, "Frame rate must be between 1 and 240");
        messages.put(Key.VALIDATION_PLAYER, "Unsupported player: {0}");

        messages.put(Key.ERROR_LAUNCHER_CLOSING, "Launcher is shutting down");
        messages.put(Key.ERROR_LAUNCHER_CLOSED_DURING_START, "Launcher closed while the service was starting");
        messages.put(Key.ERROR_MISSING_JAVA_RUNTIME, "Java runtime is missing: {0}");
        messages.put(Key.ERROR_MISSING_SERVER_JAR, "Server JAR is missing: {0}");
        messages.put(Key.ERROR_MISSING_CONFIGURATION, "External configuration file is missing: {0}");
        messages.put(Key.ERROR_SERVICE_NOT_RUNNING, "Service is not running");
        messages.put(Key.ERROR_CONFIGURATION_NO_PARENT, "Configuration path has no parent: {0}");
        messages.put(Key.ERROR_INVALID_STATUS_RESPONSE, "Invalid STATUS response");
        messages.put(Key.ERROR_INVALID_FULLSCREEN_RESPONSE, "Invalid FULLSCREEN response");
        messages.put(Key.ERROR_INVALID_QUIT_RESPONSE, "Invalid QUIT response");
        messages.put(Key.ERROR_INVALID_CONTROL_ENDPOINT, "Invalid control endpoint");
        messages.put(Key.ERROR_MISSING_CONTROL_RESPONSE, "Missing or oversized control response");
        messages.put(Key.ERROR_CONTROL_REJECTED, "Control server rejected the request: {0}");
        messages.put(Key.ERROR_INVALID_CONTROL_BOOLEAN, "Invalid boolean in control response: {0}");
        messages.put(Key.ERROR_CAUSE_PREFIX, "Underlying error: {0}");
        return messages;
    }

    private static void validate(Map<UiLanguage, EnumMap<Key, String>> catalog) {
        for (UiLanguage language : UiLanguage.values()) {
            EnumMap<Key, String> messages = catalog.get(language);
            if (messages == null) {
                throw new ExceptionInInitializerError("Missing message catalog for " + language.code());
            }
            for (Key key : Key.values()) {
                String message = messages.get(key);
                if (message == null || message.isBlank()) {
                    throw new ExceptionInInitializerError(
                            "Missing message " + key + " for " + language.code());
                }
            }
        }
    }
}
