package com.appllp7.glanceauto;

/** Side-effect free boot safety decisions, also used by regression tests. */
public final class BootPolicy {
    public enum Decision { IGNORE, WAIT_FOR_AWAKE, ATTEMPT_WHILE_AWAKE }
    public static Decision onBoot(boolean optedIn, boolean awake){
        if(!optedIn)return Decision.IGNORE;
        return awake?Decision.ATTEMPT_WHILE_AWAKE:Decision.WAIT_FOR_AWAKE;
    }
    public static boolean mayAttach(boolean awake,boolean rootGranted){
        return awake && rootGranted;
    }
    public static boolean retryOnScreenOn(boolean bootWaiting,boolean alreadyStarting,boolean fresh){
        return bootWaiting && !alreadyStarting && fresh;
    }
    private BootPolicy(){}
}
