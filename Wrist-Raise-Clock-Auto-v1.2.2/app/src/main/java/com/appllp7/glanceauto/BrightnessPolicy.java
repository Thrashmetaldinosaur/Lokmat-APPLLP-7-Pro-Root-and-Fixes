package com.appllp7.glanceauto;

/** Clock-only brightness ceiling; never changes the system brightness setting. */
public final class BrightnessPolicy {
    public static final float CLOCK_MAX=0.20f;
    private BrightnessPolicy(){}
    /** Activate before SCREEN_ON, on the opaque overlay during SCREEN_OFF. */
    public static boolean shouldOverride(boolean protectedMode,boolean enabled){
        return protectedMode && enabled;
    }
    public static float clamp(int manualBrightness,int mode){
        if(mode==0){
            int v=Math.min(255,Math.max(0,manualBrightness));
            return Math.min(CLOCK_MAX, v/255f);
        }
        return CLOCK_MAX;
    }
}
