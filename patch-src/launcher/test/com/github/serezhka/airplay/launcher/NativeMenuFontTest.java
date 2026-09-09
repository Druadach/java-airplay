package com.github.serezhka.airplay.launcher;

import java.awt.Font;
import java.awt.Toolkit;

public final class NativeMenuFontTest {
    public static void main(String[] args) throws Exception {
        Toolkit.getDefaultToolkit();
        // Font.canDisplay tests Java2D fallback fonts, not the native AWT menu font chain.
        var constructor = Class.forName("sun.awt.windows.WFontPeer")
                .getDeclaredConstructor(String.class, int.class);
        constructor.setAccessible(true);
        Object peer = constructor.newInstance(Font.DIALOG, Font.PLAIN);
        var convert = Class.forName("sun.awt.PlatformFont")
                .getMethod("makeMultiCharsetString", String.class, boolean.class);
        convert.setAccessible(true);
        String sample = "\u663e\u793a\u4e3b\u7a97\u53e3\u542f\u52a8\u505c\u6b62"
                + "AirPlay\u63a5\u6536\u5168\u5c4f\u8bbe\u7f6e\u68c0\u67e5\u66f4\u65b0\u5173\u4e8e\u9000\u51fa";
        if (convert.invoke(peer, sample, false) == null) {
            throw new AssertionError("Native AWT menu font chain cannot encode Chinese labels");
        }
        System.out.println("Native menu font configuration tests passed");
    }
}
