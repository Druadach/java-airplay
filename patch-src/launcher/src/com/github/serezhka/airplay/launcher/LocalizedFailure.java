package com.github.serezhka.airplay.launcher;

interface LocalizedFailure {
    LauncherMessages.Key messageKey();

    Object[] messageArguments();
}

final class LauncherInputException extends IllegalArgumentException implements LocalizedFailure {
    private final LauncherMessages.Key messageKey;
    private final Object[] messageArguments;

    LauncherInputException(LauncherMessages.Key messageKey, Object... messageArguments) {
        super(LauncherMessages.text(UiLanguage.EN_US, messageKey, messageArguments));
        this.messageKey = messageKey;
        this.messageArguments = messageArguments.clone();
    }

    @Override
    public LauncherMessages.Key messageKey() {
        return messageKey;
    }

    @Override
    public Object[] messageArguments() {
        return messageArguments.clone();
    }
}

final class LauncherIOException extends java.io.IOException implements LocalizedFailure {
    private final LauncherMessages.Key messageKey;
    private final Object[] messageArguments;

    LauncherIOException(LauncherMessages.Key messageKey, Object... messageArguments) {
        super(LauncherMessages.text(UiLanguage.EN_US, messageKey, messageArguments));
        this.messageKey = messageKey;
        this.messageArguments = messageArguments.clone();
    }

    @Override
    public LauncherMessages.Key messageKey() {
        return messageKey;
    }

    @Override
    public Object[] messageArguments() {
        return messageArguments.clone();
    }
}
