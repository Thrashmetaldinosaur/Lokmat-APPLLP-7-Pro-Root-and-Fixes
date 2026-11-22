package com.appllp7.glanceauto;

/** Do not interfere with media that was intentionally already playing at sleep. */
public final class MediaGuardPolicy {
    private MediaGuardPolicy(){}
    public static boolean shouldGuard(boolean enabled,boolean audioPlayingAtSleep){
        return enabled && !audioPlayingAtSleep;
    }
}
