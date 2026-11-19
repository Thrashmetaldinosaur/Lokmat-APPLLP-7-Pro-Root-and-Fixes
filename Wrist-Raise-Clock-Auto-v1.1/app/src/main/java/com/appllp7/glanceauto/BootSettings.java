package com.appllp7.glanceauto;

import android.content.Context;

/** Private persistent opt-in; disabled until the user explicitly turns it on. */
final class BootSettings {
    private static final String FILE="glance_auto_preferences";
    private static final String KEY="reboot_start_enabled";
    static boolean enabled(Context c){
        return c.getSharedPreferences(FILE,Context.MODE_PRIVATE).getBoolean(KEY,false);
    }
    static void setEnabled(Context c,boolean value){
        c.getSharedPreferences(FILE,Context.MODE_PRIVATE).edit().putBoolean(KEY,value).apply();
    }
    private BootSettings(){}
}
