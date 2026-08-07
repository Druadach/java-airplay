package com.github.serezhka.airplay.launcher;

final class LauncherStatusText {
    record Display(String state, String detail, String uptime) {
    }

    private LauncherStatusText() {
    }

    static Display render(UiLanguage language, ServerProcessManager.Snapshot snapshot) {
        Object detailArgument = snapshot.detailArgument();
        if (detailArgument instanceof Throwable failure) {
            detailArgument = LauncherMessages.failureText(language, failure);
        }
        return new Display(
                LauncherMessages.text(language, stateKey(snapshot.state())),
                LauncherMessages.text(language, detailKey(snapshot.detail()), detailArgument),
                LauncherMessages.text(language, LauncherMessages.Key.UPTIME, snapshot.uptime()));
    }

    private static LauncherMessages.Key stateKey(ServerProcessManager.State state) {
        return switch (state) {
            case STOPPED -> LauncherMessages.Key.STATE_STOPPED;
            case STARTING -> LauncherMessages.Key.STATE_STARTING;
            case RUNNING -> LauncherMessages.Key.STATE_RUNNING;
            case STOPPING -> LauncherMessages.Key.STATE_STOPPING;
            case FAILED -> LauncherMessages.Key.STATE_FAILED;
        };
    }

    private static LauncherMessages.Key detailKey(ServerProcessManager.Detail detail) {
        return switch (detail) {
            case SERVICE_STOPPED -> LauncherMessages.Key.DETAIL_SERVICE_NOT_STARTED;
            case STARTING_SERVICE -> LauncherMessages.Key.DETAIL_STARTING_SERVICE;
            case WAITING_FOR_CONTROL -> LauncherMessages.Key.DETAIL_WAITING_CONTROL_CHANNEL;
            case START_FAILED -> LauncherMessages.Key.DETAIL_START_FAILED;
            case STOPPING_SERVICE -> LauncherMessages.Key.DETAIL_STOPPING_SERVICE;
            case FULLSCREEN_MODE -> LauncherMessages.Key.DETAIL_FULLSCREEN;
            case WINDOWED_MODE -> LauncherMessages.Key.DETAIL_WINDOWED;
            case SERVICE_RUNNING -> LauncherMessages.Key.DETAIL_SERVICE_RUNNING;
            case SERVICE_INITIALIZING -> LauncherMessages.Key.DETAIL_SERVICE_INITIALIZING;
            case CONTROL_UNAVAILABLE -> LauncherMessages.Key.DETAIL_CONTROL_UNAVAILABLE;
            case ABNORMAL_EXIT -> LauncherMessages.Key.DETAIL_ABNORMAL_EXIT;
        };
    }
}
