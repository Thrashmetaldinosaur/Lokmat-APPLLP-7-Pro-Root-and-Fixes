package com.zvi.squarescale;

import android.os.Bundle;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class Hook implements IXposedHookLoadPackage {
    private static boolean done = false;
    private static final String DEF = "0.85";

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lp) throws Throwable {
        if (done || !"android".equals(lp.packageName)) return;
        done = true;
        XposedBridge.hookAllMethods(android.content.ContentProvider.class, "call", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam p) {
                try {
                    if (p.thisObject == null || !p.thisObject.getClass().getName().contains("SettingsProvider")) return;
                    Object[] a = p.args;
                    if (a == null || a.length < 3 || !(a[a.length - 1] instanceof Bundle)) return;
                    String method = a.length >= 4 ? (String) a[a.length - 3] : (String) a[0];
                    String name = (String) a[a.length - 2];
                    if (method == null || name == null || !method.startsWith("PUT_system")) return;
                    if (!name.equals("watch_small_screen_scale_h") && !name.equals("watch_small_screen_scale_v")) return;
                    Bundle b = (Bundle) a[a.length - 1];
                    String v = b.getString("value");
                    if (v == null) return;
                    float f;
                    try { f = Float.parseFloat(v.trim()); } catch (NumberFormatException e) { return; }
                    if (Math.abs(f - 0.72f) > 0.001f) return;
                    String want = target();
                    b.putString("value", want);
                    XposedBridge.log("SquareScale: " + name + " " + v + " -> " + want);
                } catch (Throwable t) {
                    XposedBridge.log(t);
                }
            }
        });
        XposedBridge.log("SquareScale: hooked SettingsProvider call");
    }

    private static String target() {
        try {
            Class<?> sp = Class.forName("android.os.SystemProperties");
            String s = (String) sp.getMethod("get", String.class, String.class).invoke(null, "persist.sys.square_scale", DEF);
            float f = Float.parseFloat(s.trim());
            if (f >= 0.5f && f <= 1.0f) return s.trim();
        } catch (Throwable ignored) {}
        return DEF;
    }
}
