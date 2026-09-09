package com.github.serezhka.airplay.launcher;

import java.text.MessageFormat;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

public final class LauncherMessages {
    public enum Key {
        APPLICATION_TITLE,
        LANGUAGE_LABEL,
        LANGUAGE_HINT,
        CONFIGURATION_SECTION,
        SERVER_NAME_LABEL,
        SERVER_NAME_HINT,
        SERVER_PORT_LABEL,
        RESOLUTION_LABEL,
        RESOLUTION_CUSTOM,
        WIDTH_LABEL,
        HEIGHT_LABEL,
        FPS_LABEL,
        PLAYER_LABEL,
        ADVANCED_SETTINGS,
        RESTORE_DEFAULTS,
        VIDEO_EXPERIMENTAL_HINT,
        PLAYER_GSTREAMER,
        PLAYER_FFMPEG,
        PLAYER_VLC,
        PLAYER_H264_DUMP,
        PLAYER_GSTREAMER_HINT,
        PLAYER_FFMPEG_HINT,
        PLAYER_VLC_HINT,
        PLAYER_H264_DUMP_HINT,
        PLAYER_CHECKING,
        PLAYER_AVAILABLE,
        PLAYER_MISSING,
        PLAYER_CHECK_FAILED,
        START_FULLSCREEN,
        START_FULLSCREEN_HINT,
        AUTO_START_LABEL,
        AUTO_START_HINT,
        AUTO_START_AND_RUN,
        AUTO_START_AND_RUN_HINT,
        START_MINIMIZED,
        START_MINIMIZED_HINT,
        CLOSE_TO_TRAY,
        CLOSE_TO_TRAY_HINT,
        SAVE_CONFIGURATION,
        START,
        START_HINT,
        STOP,
        STOP_HINT,
        RESTART,
        RESTART_AND_APPLY,
        RESTART_TOOLTIP,
        CONFIG_STATUS_SAVING,
        CONFIG_STATUS_SAVED,
        CONFIG_STATUS_RESTART_REQUIRED,
        CONFIG_STATUS_INVALID,
        CONFIG_STATUS_SAVE_FAILED,
        CONFIG_STATUS_AUTOSTART,
        DISPLAY_SECTION,
        FULLSCREEN,
        WINDOWED,
        RUNTIME_LOG,
        EXPAND_RUNTIME_LOG,
        COLLAPSE_RUNTIME_LOG,
        CLEAR,
        TRAY_OPEN,
        TRAY_START,
        TRAY_STOP,
        TRAY_SETTINGS,
        TRAY_ABOUT,
        ABOUT_MESSAGE,
        TRAY_EXIT,
        CURRENT_VERSION,
        CHECK_UPDATES,
        CHECK_UPDATES_HINT,
        CHECKING_UPDATES,
        UPDATE_AVAILABLE_TITLE,
        UPDATE_AVAILABLE_MESSAGE,
        UPDATE_CURRENT_TITLE,
        UPDATE_CURRENT_MESSAGE,
        UPDATE_FAILED_TITLE,
        UPDATE_FAILED_MESSAGE,
        UPDATE_OPEN_RELEASE,
        UPDATE_CLOSE,
        UPDATE_BROWSER_ERROR_TITLE,
        UPDATE_AUTOMATIC,
        UPDATE_MANUAL_ONLY,
        UPDATE_WORKING,
        UPDATE_DOWNLOAD_TITLE,
        UPDATE_DOWNLOADING,
        UPDATE_PREPARING,
        UPDATE_CANCEL,
        UPDATE_CANCELLING,
        UPDATE_READY_TITLE,
        UPDATE_READY_MESSAGE,
        UPDATE_RESTART,
        UPDATE_NOT_NOW,
        UPDATE_INSTALL_ERROR_TITLE,

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
        DIALOG_RESET_TITLE,
        DIALOG_RESET_MESSAGE,

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
        LOG_AUTO_RUN_FAILED,

        VALIDATION_ICON_MISSING,
        VALIDATION_INSTALLATION_NOT_FOUND,
        VALIDATION_INVALID_INSTALLATION,
        VALIDATION_FAILED,
        CONFIG_INTEGER_REQUIRED,
        VALIDATION_SERVER_NAME,
        VALIDATION_SERVER_PORT,
        VALIDATION_RESOLUTION,
        VALIDATION_WIDTH,
        VALIDATION_HEIGHT,
        VALIDATION_FPS,
        VALIDATION_PLAYER,

        ERROR_LAUNCHER_CLOSING,
        ERROR_LAUNCHER_CLOSED_DURING_START,
        ERROR_MISSING_JAVA_RUNTIME,
        ERROR_MISSING_SERVER_JAR,
        ERROR_MISSING_CONFIGURATION,
        ERROR_EXTERNAL_PLAYER_MISSING,
        ERROR_AUTOSTART_PENDING,
        ERROR_SERVICE_NOT_RUNNING,
        ERROR_CONFIGURATION_NO_PARENT,
        ERROR_INVALID_STATUS_RESPONSE,
        ERROR_INVALID_FULLSCREEN_RESPONSE,
        ERROR_INVALID_QUIT_RESPONSE,
        ERROR_INVALID_CONTROL_ENDPOINT,
        ERROR_MISSING_CONTROL_RESPONSE,
        ERROR_CONTROL_REJECTED,
        ERROR_INVALID_CONTROL_BOOLEAN,
        ERROR_CURRENT_VERSION,
        ERROR_UPDATE_NO_RELEASE,
        ERROR_UPDATE_RATE_LIMIT,
        ERROR_UPDATE_HTTP,
        ERROR_UPDATE_TIMEOUT,
        ERROR_UPDATE_CONNECTION,
        ERROR_UPDATE_RESPONSE,
        ERROR_UPDATE_VERSION,
        ERROR_UPDATE_DOWNLOAD,
        ERROR_UPDATE_PACKAGE,
        ERROR_UPDATE_CHECKSUM,
        ERROR_UPDATE_SPACE,
        ERROR_UPDATE_UNSUPPORTED,
        ERROR_UPDATE_BUSY,
        ERROR_UPDATE_HELPER,
        ERROR_UPDATE_STARTUP,
        ERROR_UPDATE_SERVICE_STOP,
        ERROR_OPEN_RELEASE,
        ERROR_CAUSE_PREFIX
    }

    private static final EnumMap<UiLanguage, EnumMap<Key, String>> CATALOG = createCatalog();

    private LauncherMessages() {
    }

    public static String text(UiLanguage language, Key key, Object... arguments) {
        Objects.requireNonNull(language, "language");
        Objects.requireNonNull(key, "key");
        String pattern = CATALOG.get(language.resolved()).get(key);
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
        messages.put(Key.APPLICATION_TITLE, "AirPlay 接收器");
        messages.put(Key.LANGUAGE_LABEL, "界面语言");
        messages.put(Key.LANGUAGE_HINT, "选择“跟随系统”时，根据系统语言自动使用中文或英文。");
        messages.put(Key.CONFIGURATION_SECTION, "投屏设置");
        messages.put(Key.SERVER_NAME_LABEL, "投屏名称");
        messages.put(Key.SERVER_NAME_HINT, "在 iPhone、iPad 或 Mac 的 AirPlay / 屏幕镜像列表中显示的名称。");
        messages.put(Key.SERVER_PORT_LABEL, "服务端口");
        messages.put(Key.RESOLUTION_LABEL, "画面分辨率");
        messages.put(Key.RESOLUTION_CUSTOM, "自定义");
        messages.put(Key.WIDTH_LABEL, "宽度（像素）");
        messages.put(Key.HEIGHT_LABEL, "高度（像素）");
        messages.put(Key.FPS_LABEL, "最高帧率");
        messages.put(Key.PLAYER_LABEL, "播放方式");
        messages.put(Key.ADVANCED_SETTINGS, "高级设置");
        messages.put(Key.RESTORE_DEFAULTS, "恢复默认设置");
        messages.put(Key.VIDEO_EXPERIMENTAL_HINT, "已验证的最高规格为 4K / 60 帧。当前设置超出此范围，不保证能够正常投屏。");
        messages.put(Key.PLAYER_GSTREAMER, "内置播放器（推荐）");
        messages.put(Key.PLAYER_FFMPEG, "FFplay（需另行安装）");
        messages.put(Key.PLAYER_VLC, "VLC（需另行安装）");
        messages.put(Key.PLAYER_H264_DUMP, "保存视频数据（调试）");
        messages.put(Key.PLAYER_GSTREAMER_HINT, "使用内置 GStreamer 播放音视频，无需另行安装；支持窗口与全屏实时切换。");
        messages.put(Key.PLAYER_FFMPEG_HINT, "FFmpeg 的视频播放器，需要安装 ffplay.exe 并加入系统 PATH。视频默认全屏，音频由内置 GStreamer 播放。");
        messages.put(Key.PLAYER_VLC_HINT, "使用外部 VLC 播放视频，需要安装 vlc.exe 并加入系统 PATH。");
        messages.put(Key.PLAYER_H264_DUMP_HINT, "将原始视频数据写入程序目录下的 dump.h264，不显示播放窗口；仅供调试，不是常规录像功能。");
        messages.put(Key.PLAYER_CHECKING, "正在检查 {0}…");
        messages.put(Key.PLAYER_AVAILABLE, "已找到 {0}");
        messages.put(Key.PLAYER_MISSING, "未找到 {0}。请安装并加入系统 PATH 后重新打开程序，或选择“内置播放器（推荐）”。");
        messages.put(Key.PLAYER_CHECK_FAILED, "无法检查播放器：{0}");
        messages.put(Key.START_FULLSCREEN, "投屏时默认全屏");
        messages.put(Key.START_FULLSCREEN_HINT, "仅设置内置播放器的初始显示方式，不会让程序主窗口全屏。投屏过程中仍可用 F11 / Esc 切换。");
        messages.put(Key.AUTO_START_LABEL, "开机自动启动");
        messages.put(Key.AUTO_START_HINT, "登录 Windows 后自动打开本程序。要同时开启投屏接收，请勾选“打开程序后自动接收投屏”。");
        messages.put(Key.AUTO_START_AND_RUN, "打开程序后自动接收投屏");
        messages.put(Key.AUTO_START_AND_RUN_HINT, "打开本程序后自动开启接收，无需点击“启动AirPlay接收”；仍需在 iPhone、iPad 或 Mac 上选择本电脑来投屏。");
        messages.put(Key.START_MINIMIZED, "软件启动时最小化到托盘");
        messages.put(Key.START_MINIMIZED_HINT, "打开程序时隐藏主窗口，仅显示任务栏右下角的托盘图标。手动打开和开机启动均适用；托盘不可用时仍显示主窗口。");
        messages.put(Key.CLOSE_TO_TRAY, "软件关闭时最小化到托盘");
        messages.put(Key.CLOSE_TO_TRAY_HINT, "点击主窗口的 × 时隐藏到任务栏右下角，投屏继续。取消勾选或托盘不可用时会退出并停止投屏；也可从托盘菜单选择“退出”。");
        messages.put(Key.SAVE_CONFIGURATION, "保存配置");
        messages.put(Key.START, "启动AirPlay接收");
        messages.put(Key.START_HINT, "开启 AirPlay 接收后，在 iPhone、iPad 或 Mac 上选择本电脑即可投屏。");
        messages.put(Key.STOP, "停止AirPlay接收");
        messages.put(Key.STOP_HINT, "关闭 AirPlay 接收并断开当前投屏，程序仍保持打开。");
        messages.put(Key.RESTART, "重启AirPlay接收");
        messages.put(Key.RESTART_AND_APPLY, "重启AirPlay接收");
        messages.put(Key.RESTART_TOOLTIP, "重启的是投屏接收服务，不是本程序。当前投屏会断开，需要从发送设备重新连接。");
        messages.put(Key.CONFIG_STATUS_SAVING, "正在保存更改…");
        messages.put(Key.CONFIG_STATUS_SAVED, "配置已保存");
        messages.put(Key.CONFIG_STATUS_RESTART_REQUIRED, "配置已保存，重启服务后生效；重启会中断当前投屏。");
        messages.put(Key.CONFIG_STATUS_INVALID, "尚未保存：{0}");
        messages.put(Key.CONFIG_STATUS_SAVE_FAILED, "保存失败：{0}");
        messages.put(Key.CONFIG_STATUS_AUTOSTART, "正在更新 Windows 登录启动项…");
        messages.put(Key.DISPLAY_SECTION, "当前投屏窗口");
        messages.put(Key.FULLSCREEN, "全屏");
        messages.put(Key.WINDOWED, "窗口模式");
        messages.put(Key.RUNTIME_LOG, "运行日志");
        messages.put(Key.EXPAND_RUNTIME_LOG, "展开运行日志");
        messages.put(Key.COLLAPSE_RUNTIME_LOG, "收起运行日志");
        messages.put(Key.CLEAR, "清空");
        messages.put(Key.TRAY_OPEN, "显示主窗口");
        messages.put(Key.TRAY_START, "启动AirPlay接收");
        messages.put(Key.TRAY_STOP, "停止AirPlay接收");
        messages.put(Key.TRAY_SETTINGS, "设置...");
        messages.put(Key.TRAY_ABOUT, "关于");
        messages.put(Key.ABOUT_MESSAGE, "AirPlay 接收器 v{0}\n\n接收 Apple 设备的音视频与屏幕镜像\n基于 serezhka/java-airplay，由 Druadach 维护\n\nhttps://github.com/Druadach/java-airplay");
        messages.put(Key.TRAY_EXIT, "退出");
        messages.put(Key.CURRENT_VERSION, "当前版本：{0}");
        messages.put(Key.CHECK_UPDATES, "检查更新");
        messages.put(Key.CHECK_UPDATES_HINT, "从 GitHub 检查最新正式版本。发现新版后可选择自动更新；下载不中断投屏，安装前会再次确认重启。");
        messages.put(Key.CHECKING_UPDATES, "正在检查…");
        messages.put(Key.UPDATE_AVAILABLE_TITLE, "发现新版本");
        messages.put(Key.UPDATE_AVAILABLE_MESSAGE, "当前版本：{0}\nGitHub 最新版：{1}\n\n可选择自动下载并校验更新包，或打开发布页查看更新说明。\n安装前会再次确认重启；现有设置将保留。");
        messages.put(Key.UPDATE_CURRENT_TITLE, "未发现新版本");
        messages.put(Key.UPDATE_CURRENT_MESSAGE, "当前版本：{0}\nGitHub 最新版：{1}\n\n当前已是最新版本。");
        messages.put(Key.UPDATE_FAILED_TITLE, "无法检查更新");
        messages.put(Key.UPDATE_FAILED_MESSAGE, "{0}\n\n也可打开 GitHub 发布页手动查看版本。");
        messages.put(Key.UPDATE_OPEN_RELEASE, "打开发布页");
        messages.put(Key.UPDATE_CLOSE, "关闭");
        messages.put(Key.UPDATE_BROWSER_ERROR_TITLE, "无法打开发布页");
        messages.put(Key.UPDATE_AUTOMATIC, "自动更新");
        messages.put(Key.UPDATE_MANUAL_ONLY, "\n\n此版本未提供兼容的自动更新包或 SHA-256 校验值，\n或当前运行环境不支持自动更新。请通过发布页手动更新。");
        messages.put(Key.UPDATE_WORKING, "正在更新…");
        messages.put(Key.UPDATE_DOWNLOAD_TITLE, "下载更新");
        messages.put(Key.UPDATE_DOWNLOADING, "正在下载：{0} / {1} MB\n下载期间可继续投屏，取消不会更改当前安装。");
        messages.put(Key.UPDATE_PREPARING, "正在校验并准备更新文件…\n当前安装和设置尚未更改。");
        messages.put(Key.UPDATE_CANCEL, "取消下载");
        messages.put(Key.UPDATE_CANCELLING, "正在取消…");
        messages.put(Key.UPDATE_READY_TITLE, "更新已就绪");
        messages.put(Key.UPDATE_READY_MESSAGE, "版本 {0} 已下载并通过校验。\n\n更新会停止当前投屏并重启程序，保留所有设置。\n安装或启动失败时会尝试还原旧版本。\n\n现在更新并重启吗？");
        messages.put(Key.UPDATE_RESTART, "更新并重启");
        messages.put(Key.UPDATE_NOT_NOW, "暂不更新");
        messages.put(Key.UPDATE_INSTALL_ERROR_TITLE, "无法自动更新");

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
        messages.put(Key.DIALOG_LAUNCHER_ERROR_TITLE, "AirPlay 接收器错误");
        messages.put(Key.DIALOG_LAUNCHER_START_ERROR_TITLE, "无法启动 AirPlay 接收器");
        messages.put(Key.DIALOG_SAVE_ERROR_TITLE, "无法保存配置");
        messages.put(Key.DIALOG_START_ERROR_TITLE, "启动服务失败");
        messages.put(Key.DIALOG_STOP_ERROR_TITLE, "停止服务失败");
        messages.put(Key.DIALOG_RESTART_ERROR_TITLE, "重启服务失败");
        messages.put(Key.DIALOG_FULLSCREEN_ERROR_TITLE, "无法切换到全屏");
        messages.put(Key.DIALOG_WINDOWED_ERROR_TITLE, "无法切换到窗口模式");
        messages.put(Key.DIALOG_RESET_TITLE, "恢复默认设置");
        messages.put(Key.DIALOG_RESET_MESSAGE,
                "恢复投屏名称、1080p / 60 帧、内置播放器、窗口模式和跟随系统语言，并关闭开机自动启动与自动接收投屏？\n\n启动时显示主窗口，关闭时最小化到托盘。\n自定义端口和其他配置文件选项会保留。\n投屏设置需要重启接收服务后生效，当前投屏不会立即中断。");

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
        messages.put(Key.LOG_AUTO_RUN_FAILED, "自动启动服务失败：{0}");

        messages.put(Key.VALIDATION_ICON_MISSING, "启动器图标资源缺失");
        messages.put(Key.VALIDATION_INSTALLATION_NOT_FOUND,
                "找不到 java-airplay-server-fixed.jar 和 jre 目录，请使用 --base-dir 指定安装目录");
        messages.put(Key.VALIDATION_INVALID_INSTALLATION, "无效的安装目录: {0}");
        messages.put(Key.VALIDATION_FAILED, "启动器验证失败: {0}");
        messages.put(Key.CONFIG_INTEGER_REQUIRED, "{0} 必须是整数");
        messages.put(Key.VALIDATION_SERVER_NAME, "投屏名称长度必须为 1 到 64 个字符");
        messages.put(Key.VALIDATION_SERVER_PORT, "服务端口范围为 1 到 65535");
        messages.put(Key.VALIDATION_RESOLUTION, "分辨率范围为 320x240 到 7680x4320");
        messages.put(Key.VALIDATION_WIDTH, "宽度必须为 320 到 7680 的整数");
        messages.put(Key.VALIDATION_HEIGHT, "高度必须为 240 到 4320 的整数");
        messages.put(Key.VALIDATION_FPS, "帧率范围为 1 到 240");
        messages.put(Key.VALIDATION_PLAYER, "不支持的播放方式: {0}");

        messages.put(Key.ERROR_LAUNCHER_CLOSING, "启动器正在关闭");
        messages.put(Key.ERROR_LAUNCHER_CLOSED_DURING_START, "启动器已在服务启动期间关闭");
        messages.put(Key.ERROR_MISSING_JAVA_RUNTIME, "缺少 Java 运行时: {0}");
        messages.put(Key.ERROR_MISSING_SERVER_JAR, "缺少服务端 JAR: {0}");
        messages.put(Key.ERROR_MISSING_CONFIGURATION, "缺少外部配置文件: {0}");
        messages.put(Key.ERROR_EXTERNAL_PLAYER_MISSING, "未找到 {0}。请安装并加入系统 PATH 后重新打开程序，或选择“内置播放器（推荐）”。");
        messages.put(Key.ERROR_AUTOSTART_PENDING, "正在更新 Windows 登录启动项，请稍后再试。");
        messages.put(Key.ERROR_SERVICE_NOT_RUNNING, "服务未运行");
        messages.put(Key.ERROR_CONFIGURATION_NO_PARENT, "配置文件路径没有父目录: {0}");
        messages.put(Key.ERROR_INVALID_STATUS_RESPONSE, "无效的 STATUS 响应");
        messages.put(Key.ERROR_INVALID_FULLSCREEN_RESPONSE, "无效的 FULLSCREEN 响应");
        messages.put(Key.ERROR_INVALID_QUIT_RESPONSE, "无效的 QUIT 响应");
        messages.put(Key.ERROR_INVALID_CONTROL_ENDPOINT, "无效的控制端点");
        messages.put(Key.ERROR_MISSING_CONTROL_RESPONSE, "控制响应缺失或过长");
        messages.put(Key.ERROR_CONTROL_REJECTED, "控制服务拒绝请求: {0}");
        messages.put(Key.ERROR_INVALID_CONTROL_BOOLEAN, "控制响应包含无效布尔值: {0}");
        messages.put(Key.ERROR_CURRENT_VERSION, "无法读取程序版本号，请重新安装或重新构建启动器。");
        messages.put(Key.ERROR_UPDATE_NO_RELEASE, "GitHub 暂未提供可用的正式版本，请稍后再试。");
        messages.put(Key.ERROR_UPDATE_RATE_LIMIT, "GitHub 请求次数受限，请稍后再试。");
        messages.put(Key.ERROR_UPDATE_HTTP, "GitHub 返回 HTTP {0}，请稍后再试。");
        messages.put(Key.ERROR_UPDATE_TIMEOUT, "检查更新超时，请检查网络连接后重试。");
        messages.put(Key.ERROR_UPDATE_CONNECTION, "无法连接 GitHub，请检查网络连接或代理设置后重试。");
        messages.put(Key.ERROR_UPDATE_RESPONSE, "GitHub 返回的版本信息无效，请稍后再试或手动查看发布页。");
        messages.put(Key.ERROR_UPDATE_VERSION, "无法识别 GitHub 发布的版本号，请手动查看发布页。");
        messages.put(Key.ERROR_UPDATE_DOWNLOAD, "下载更新失败或超时，请检查网络后重试，也可通过发布页手动更新。当前安装未更改。");
        messages.put(Key.ERROR_UPDATE_PACKAGE, "更新包不兼容、不完整或包含不安全的文件路径，已拒绝安装。请通过发布页手动更新。");
        messages.put(Key.ERROR_UPDATE_CHECKSUM, "更新包的 SHA-256 校验不通过，已拒绝安装。请重新下载或通过发布页手动更新。");
        messages.put(Key.ERROR_UPDATE_SPACE, "安装所在磁盘的可用空间不足，请清理空间后重试。");
        messages.put(Key.ERROR_UPDATE_UNSUPPORTED, "自动更新需要完整的 Windows x64 发行包，请通过发布页手动更新。");
        messages.put(Key.ERROR_UPDATE_BUSY, "更新或其他程序操作正在进行，请等待完成后重试。");
        messages.put(Key.ERROR_UPDATE_HELPER, "无法启动更新助手，当前安装未更改。请检查目录写入权限或安全软件拦截。\n诊断文件：{0}");
        messages.put(Key.ERROR_UPDATE_STARTUP, "更新后的启动确认失败，更新助手将尝试还原旧版本。");
        messages.put(Key.ERROR_UPDATE_SERVICE_STOP, "投屏服务尚未完全停止，已取消安装。请停止接收后重试。");
        messages.put(Key.ERROR_OPEN_RELEASE, "无法打开默认浏览器，请复制下面的链接手动访问：");
        messages.put(Key.ERROR_CAUSE_PREFIX, "底层错误: {0}");
        return messages;
    }

    private static EnumMap<Key, String> english() {
        EnumMap<Key, String> messages = new EnumMap<>(Key.class);
        messages.put(Key.APPLICATION_TITLE, "AirPlay Receiver");
        messages.put(Key.LANGUAGE_LABEL, "Language");
        messages.put(Key.LANGUAGE_HINT, "System default automatically selects Chinese or English based on your system language.");
        messages.put(Key.CONFIGURATION_SECTION, "AirPlay Settings");
        messages.put(Key.SERVER_NAME_LABEL, "AirPlay Name");
        messages.put(Key.SERVER_NAME_HINT, "Shown in the AirPlay / Screen Mirroring list on your iPhone, iPad, or Mac.");
        messages.put(Key.SERVER_PORT_LABEL, "Server Port");
        messages.put(Key.RESOLUTION_LABEL, "Video Resolution");
        messages.put(Key.RESOLUTION_CUSTOM, "Custom");
        messages.put(Key.WIDTH_LABEL, "Width (px)");
        messages.put(Key.HEIGHT_LABEL, "Height (px)");
        messages.put(Key.FPS_LABEL, "Max Frame Rate (fps)");
        messages.put(Key.PLAYER_LABEL, "Playback Mode");
        messages.put(Key.ADVANCED_SETTINGS, "Advanced Settings");
        messages.put(Key.RESTORE_DEFAULTS, "Restore Defaults");
        messages.put(Key.VIDEO_EXPERIMENTAL_HINT, "The highest tested setting is 4K / 60 fps. Higher settings may not work with your device.");
        messages.put(Key.PLAYER_GSTREAMER, "Built-in player (recommended)");
        messages.put(Key.PLAYER_FFMPEG, "FFplay (install separately)");
        messages.put(Key.PLAYER_VLC, "VLC (install separately)");
        messages.put(Key.PLAYER_H264_DUMP, "Save raw video (debug)");
        messages.put(Key.PLAYER_GSTREAMER_HINT, "Uses bundled GStreamer for audio and video, with no extra installation. Switch between windowed and fullscreen playback at any time.");
        messages.put(Key.PLAYER_FFMPEG_HINT, "The video player from FFmpeg. Requires ffplay.exe on the system PATH. Video starts fullscreen; audio uses bundled GStreamer.");
        messages.put(Key.PLAYER_VLC_HINT, "Uses an external VLC installation for video playback. Requires vlc.exe on the system PATH.");
        messages.put(Key.PLAYER_H264_DUMP_HINT, "Writes raw video data to dump.h264 in the application directory without a playback window. For debugging, not normal recording.");
        messages.put(Key.PLAYER_CHECKING, "Checking for {0}…");
        messages.put(Key.PLAYER_AVAILABLE, "Found {0}");
        messages.put(Key.PLAYER_MISSING, "Cannot find {0}. Install it on the system PATH and reopen the app, or select Built-in player (recommended).");
        messages.put(Key.PLAYER_CHECK_FAILED, "Cannot check the player: {0}");
        messages.put(Key.START_FULLSCREEN, "Start AirPlay in fullscreen");
        messages.put(Key.START_FULLSCREEN_HINT, "Sets the initial display mode of the built-in player, not the main app window. You can still use F11 / Esc during playback.");
        messages.put(Key.AUTO_START_LABEL, "Start with Windows");
        messages.put(Key.AUTO_START_HINT, "Opens this app when you sign in to Windows. Enable automatic reception separately to make this PC ready for AirPlay.");
        messages.put(Key.AUTO_START_AND_RUN, "Receive AirPlay when the app opens");
        messages.put(Key.AUTO_START_AND_RUN_HINT, "Starts AirPlay reception when this app opens, without clicking Start. You still need to select this PC on your Apple device to connect.");
        messages.put(Key.START_MINIMIZED, "Start minimized to tray");
        messages.put(Key.START_MINIMIZED_HINT, "Opens with only an icon in the taskbar notification area. Applies to manual and Windows startup; shows the main window if the tray is unavailable.");
        messages.put(Key.CLOSE_TO_TRAY, "Close to tray");
        messages.put(Key.CLOSE_TO_TRAY_HINT, "The main window close button hides the app in the taskbar notification area while casting continues. Closing exits and stops casting when unchecked or the tray is unavailable. Tray Exit always quits.");
        messages.put(Key.SAVE_CONFIGURATION, "Save Configuration");
        messages.put(Key.START, "Start");
        messages.put(Key.START_HINT, "Makes this PC available in AirPlay / Screen Mirroring. Select it on your iPhone, iPad, or Mac to connect.");
        messages.put(Key.STOP, "Stop");
        messages.put(Key.STOP_HINT, "Stops AirPlay reception and disconnects the current session. The app stays open.");
        messages.put(Key.RESTART, "Restart");
        messages.put(Key.RESTART_AND_APPLY, "Apply & Restart");
        messages.put(Key.RESTART_TOOLTIP, "Restarts AirPlay reception, not this app. This disconnects the current session; reconnect from your Apple device.");
        messages.put(Key.CONFIG_STATUS_SAVING, "Saving changes…");
        messages.put(Key.CONFIG_STATUS_SAVED, "Configuration saved");
        messages.put(Key.CONFIG_STATUS_RESTART_REQUIRED, "Saved. Restart the service to apply; this interrupts the current casting session.");
        messages.put(Key.CONFIG_STATUS_INVALID, "Not saved: {0}");
        messages.put(Key.CONFIG_STATUS_SAVE_FAILED, "Save failed: {0}");
        messages.put(Key.CONFIG_STATUS_AUTOSTART, "Updating the Windows sign-in entry…");
        messages.put(Key.DISPLAY_SECTION, "Current AirPlay Window");
        messages.put(Key.FULLSCREEN, "Fullscreen");
        messages.put(Key.WINDOWED, "Windowed");
        messages.put(Key.RUNTIME_LOG, "Runtime Log");
        messages.put(Key.EXPAND_RUNTIME_LOG, "Show Runtime Log");
        messages.put(Key.COLLAPSE_RUNTIME_LOG, "Hide Runtime Log");
        messages.put(Key.CLEAR, "Clear");
        messages.put(Key.TRAY_OPEN, "Show Main Window");
        messages.put(Key.TRAY_START, "Start Service");
        messages.put(Key.TRAY_STOP, "Stop Service");
        messages.put(Key.TRAY_SETTINGS, "Settings...");
        messages.put(Key.TRAY_ABOUT, "About");
        messages.put(Key.ABOUT_MESSAGE, "AirPlay Receiver v{0}\n\nReceive media and screen mirroring from Apple devices\nBased on serezhka/java-airplay, maintained by Druadach\n\nhttps://github.com/Druadach/java-airplay");
        messages.put(Key.TRAY_EXIT, "Exit");
        messages.put(Key.CURRENT_VERSION, "Version: {0}");
        messages.put(Key.CHECK_UPDATES, "Check for Updates");
        messages.put(Key.CHECK_UPDATES_HINT, "Checks GitHub for the latest stable release. You can choose to update automatically. Downloads do not interrupt casting; installation requires restart confirmation.");
        messages.put(Key.CHECKING_UPDATES, "Checking…");
        messages.put(Key.UPDATE_AVAILABLE_TITLE, "Update Available");
        messages.put(Key.UPDATE_AVAILABLE_MESSAGE, "Current version: {0}\nLatest on GitHub: {1}\n\nDownload and verify the update automatically, or open the release notes.\nYou will confirm the restart before installation. Your settings will be preserved.");
        messages.put(Key.UPDATE_CURRENT_TITLE, "No Newer Version");
        messages.put(Key.UPDATE_CURRENT_MESSAGE, "Current version: {0}\nLatest on GitHub: {1}\n\nYour version is at least as recent as the latest stable release. No update is needed.");
        messages.put(Key.UPDATE_FAILED_TITLE, "Could Not Check for Updates");
        messages.put(Key.UPDATE_FAILED_MESSAGE, "{0}\n\nYou can also open the GitHub releases page to check manually.");
        messages.put(Key.UPDATE_OPEN_RELEASE, "Open Release Page");
        messages.put(Key.UPDATE_CLOSE, "Close");
        messages.put(Key.UPDATE_BROWSER_ERROR_TITLE, "Could Not Open Release Page");
        messages.put(Key.UPDATE_AUTOMATIC, "Update Automatically");
        messages.put(Key.UPDATE_MANUAL_ONLY, "\n\nThis release has no compatible automatic update package or SHA-256 digest,\nor your environment does not support automatic updates. Please update from the release page.");
        messages.put(Key.UPDATE_WORKING, "Updating…");
        messages.put(Key.UPDATE_DOWNLOAD_TITLE, "Download Update");
        messages.put(Key.UPDATE_DOWNLOADING, "Downloading: {0} / {1} MB\nCasting can continue. Cancelling will not change your installation.");
        messages.put(Key.UPDATE_PREPARING, "Verifying and preparing update files…\nYour installation and settings have not been changed.");
        messages.put(Key.UPDATE_CANCEL, "Cancel Download");
        messages.put(Key.UPDATE_CANCELLING, "Cancelling…");
        messages.put(Key.UPDATE_READY_TITLE, "Update Ready");
        messages.put(Key.UPDATE_READY_MESSAGE, "Version {0} has been downloaded and verified.\n\nUpdating stops casting and restarts the app, preserving all settings.\nIf installation or startup fails, the updater will attempt to restore the previous version.\n\nUpdate and restart now?");
        messages.put(Key.UPDATE_RESTART, "Update and Restart");
        messages.put(Key.UPDATE_NOT_NOW, "Not Now");
        messages.put(Key.UPDATE_INSTALL_ERROR_TITLE, "Could Not Update Automatically");

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
        messages.put(Key.DIALOG_LAUNCHER_ERROR_TITLE, "AirPlay Receiver Error");
        messages.put(Key.DIALOG_LAUNCHER_START_ERROR_TITLE, "Unable to Start AirPlay Receiver");
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
        messages.put(Key.VALIDATION_SERVER_NAME, "AirPlay name must contain 1 to 64 characters");
        messages.put(Key.VALIDATION_SERVER_PORT, "Server port must be between 1 and 65535");
        messages.put(Key.VALIDATION_RESOLUTION, "Resolution must be between 320x240 and 7680x4320");
        messages.put(Key.VALIDATION_FPS, "Frame rate must be between 1 and 240");
        messages.put(Key.VALIDATION_PLAYER, "Unsupported playback mode: {0}");

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
        messages.put(Key.ERROR_CURRENT_VERSION, "Cannot read the app version. Please reinstall or rebuild the launcher.");
        messages.put(Key.ERROR_UPDATE_NO_RELEASE, "GitHub has no available stable release. Please try again later.");
        messages.put(Key.ERROR_UPDATE_RATE_LIMIT, "GitHub is rate-limiting requests. Please try again later.");
        messages.put(Key.ERROR_UPDATE_HTTP, "GitHub returned HTTP {0}. Please try again later.");
        messages.put(Key.ERROR_UPDATE_TIMEOUT, "The update check timed out. Check your connection and try again.");
        messages.put(Key.ERROR_UPDATE_CONNECTION, "Cannot connect to GitHub. Check your network or proxy settings and try again.");
        messages.put(Key.ERROR_UPDATE_RESPONSE, "GitHub returned invalid release information. Try again later or check the release page manually.");
        messages.put(Key.ERROR_UPDATE_VERSION, "The GitHub release version is not recognized. Please check the release page manually.");
        messages.put(Key.ERROR_UPDATE_DOWNLOAD, "The update download failed or timed out. Check your connection and retry, or use the release page. Your installation is unchanged.");
        messages.put(Key.ERROR_UPDATE_PACKAGE, "The update package is incompatible, incomplete, or contains unsafe paths. Installation was refused. Please update from the release page.");
        messages.put(Key.ERROR_UPDATE_CHECKSUM, "The update package failed SHA-256 verification and was not installed. Download it again or update from the release page.");
        messages.put(Key.ERROR_UPDATE_SPACE, "There is not enough free space on the installation drive. Free some space and try again.");
        messages.put(Key.ERROR_UPDATE_UNSUPPORTED, "Automatic updates require a complete Windows x64 distribution. Please update from the release page.");
        messages.put(Key.ERROR_UPDATE_BUSY, "An update or another app operation is in progress. Wait for it to finish and try again.");
        messages.put(Key.ERROR_UPDATE_HELPER, "The update helper could not start. Your installation is unchanged. Check write permissions and security software.\nDiagnostics: {0}");
        messages.put(Key.ERROR_UPDATE_STARTUP, "The updated app did not confirm startup. The updater will attempt to restore the previous version.");
        messages.put(Key.ERROR_UPDATE_SERVICE_STOP, "The reception process has not fully stopped. Installation was cancelled. Stop reception and try again.");
        messages.put(Key.ERROR_OPEN_RELEASE, "Cannot open the default browser. Copy this link to open it manually:");
        messages.put(Key.ERROR_CAUSE_PREFIX, "Underlying error: {0}");
        messages.put(Key.DIALOG_RESET_TITLE, "Restore Defaults");
        messages.put(Key.DIALOG_RESET_MESSAGE,
                "Restore the AirPlay name, 1080p / 60 FPS, built-in player, windowed mode and system language, and disable Windows startup and automatic reception?\n\nShow the main window on startup and minimize to tray on close.\nCustom ports and other configuration-file options are preserved.\nAirPlay settings apply after restarting reception; the current session is not interrupted now.");
        messages.put(Key.LOG_AUTO_RUN_FAILED, "Automatic service startup failed: {0}");
        messages.put(Key.VALIDATION_WIDTH, "Width must be an integer from 320 to 7680");
        messages.put(Key.VALIDATION_HEIGHT, "Height must be an integer from 240 to 4320");
        messages.put(Key.ERROR_EXTERNAL_PLAYER_MISSING,
                "Cannot find {0}. Install it on the system PATH and reopen the app, or select Built-in player (recommended).");
        messages.put(Key.ERROR_AUTOSTART_PENDING, "The Windows sign-in entry is being updated. Please try again shortly.");
        return messages;
    }

    private static void validate(Map<UiLanguage, EnumMap<Key, String>> catalog) {
        for (UiLanguage language : UiLanguage.values()) {
            EnumMap<Key, String> messages = catalog.get(language.resolved());
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
