package com.github.serezhka.airplay.launcher;

import java.util.Locale;

public enum UiLanguage {
    SYSTEM("system", "跟随系统 / System default", null),
    ZH_CN("zh-CN", "中文", Locale.SIMPLIFIED_CHINESE),
    EN_US("en-US", "English", Locale.US);

    private final String code;
    private final String label;
    private final Locale locale;

    UiLanguage(String code, String label, Locale locale) {
        this.code = code;
        this.label = label;
        this.locale = locale;
    }

    public String code() {
        return code;
    }

    public String label() {
        return label;
    }

    public Locale locale() {
        return resolved().locale;
    }

    public UiLanguage resolved() {
        return this == SYSTEM ? systemDefault() : this;
    }

    @Override
    public String toString() {
        return label;
    }

    public static UiLanguage fromCode(String code) {
        if (code == null || code.isBlank()) {
            return SYSTEM;
        }

        String normalized = code.trim().replace('_', '-');
        for (UiLanguage language : values()) {
            if (language.code.equalsIgnoreCase(normalized)) {
                return language;
            }
        }
        if (normalized.equalsIgnoreCase(Locale.CHINESE.getLanguage())) {
            return ZH_CN;
        }
        if (normalized.equalsIgnoreCase(Locale.ENGLISH.getLanguage())) {
            return EN_US;
        }
        return SYSTEM;
    }

    public static UiLanguage systemDefault() {
        return Locale.CHINESE.getLanguage().equalsIgnoreCase(Locale.getDefault().getLanguage())
                ? ZH_CN
                : EN_US;
    }
}
