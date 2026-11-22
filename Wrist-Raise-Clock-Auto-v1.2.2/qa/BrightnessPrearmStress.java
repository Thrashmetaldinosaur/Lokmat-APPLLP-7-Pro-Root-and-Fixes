package com.appllp7.glanceauto;

public final class BrightnessPrearmStress {
    private static long assertions=0;
    private static void check(boolean ok,String msg){
        assertions++;
        if(!ok)throw new AssertionError(msg);
    }
    public static void main(String[] args){
        check(!BrightnessPolicy.shouldOverride(false,true),"No override while passive");
        check(!BrightnessPolicy.shouldOverride(true,false),"No override when disabled");
        check(BrightnessPolicy.shouldOverride(true,true),"Pre-arm shield");
        check(Math.abs(BrightnessPolicy.CLOCK_MAX-0.20f)<0.00001f,"Cap 20%");
        for(int v=0;v<=255;v++){
            float result=BrightnessPolicy.clamp(v,0);
            check(result>=0f && result<=0.20001f,"Manual cap v="+v);
            check(result<=v/255f+0.00001f,"Never brighten manual setting v="+v);
        }
        check(Math.abs(BrightnessPolicy.clamp(255,0)-0.20f)<0.00001f,"High manual brightness capped");
        check(Math.abs(BrightnessPolicy.clamp(10,0)-(10f/255f))<0.00001f,"Low manual brightness respected");
        check(Math.abs(BrightnessPolicy.clamp(20,1)-0.20f)<0.00001f,"Auto case caps");
        System.out.println("BRIGHTNESS_PREARM_PASS assertions="+assertions);
    }
}
