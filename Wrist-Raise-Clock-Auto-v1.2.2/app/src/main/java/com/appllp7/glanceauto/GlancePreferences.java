package com.appllp7.glanceauto;

import android.content.Context;

/** Local only: neither switch changes OEM device brightness nor global audio. */
final class GlancePreferences {
    private static final String FILE="glance_optional_features";
    static boolean brightnessCap(Context c){return c.getSharedPreferences(FILE,0).getBoolean("brightness_cap",true);}
    static boolean mediaGuard(Context c){return c.getSharedPreferences(FILE,0).getBoolean("prevent_video_resume",true);}
    static void setBrightnessCap(Context c,boolean on){c.getSharedPreferences(FILE,0).edit().putBoolean("brightness_cap",on).apply();}
    static void setMediaGuard(Context c,boolean on){c.getSharedPreferences(FILE,0).edit().putBoolean("prevent_video_resume",on).apply();}
}
