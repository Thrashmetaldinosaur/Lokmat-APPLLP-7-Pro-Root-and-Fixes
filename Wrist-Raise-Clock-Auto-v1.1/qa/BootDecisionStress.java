package com.appllp7.glanceauto;
import java.util.Random;
public final class BootDecisionStress {
    private static long assertions=0;
    static void check(boolean cond,String message){assertions++;if(!cond)throw new AssertionError(message+" #"+assertions);}
    public static void main(String[] args){
        check(BootPolicy.onBoot(false,false)==BootPolicy.Decision.IGNORE,"opt out asleep");
        check(BootPolicy.onBoot(false,true)==BootPolicy.Decision.IGNORE,"opt out awake");
        check(BootPolicy.onBoot(true,false)==BootPolicy.Decision.WAIT_FOR_AWAKE,"opt in asleep");
        check(BootPolicy.onBoot(true,true)==BootPolicy.Decision.ATTEMPT_WHILE_AWAKE,"opt in awake");
        for(int i=0;i<100000;i++){
            boolean opted=(i&1)!=0;
            boolean awake=(i&2)!=0;
            boolean root=(i&4)!=0;
            BootPolicy.Decision d=BootPolicy.onBoot(opted,awake);
            check((d==BootPolicy.Decision.IGNORE)==!opted,"opt-in check");
            check(d!=BootPolicy.Decision.ATTEMPT_WHILE_AWAKE || awake,"never start awake path asleep");
            check(BootPolicy.mayAttach(awake,root)==(awake&&root),"never attach asleep or without root");
            check(BootPolicy.retryOnScreenOn(opted,false,true)==opted,"first awake retry when opted in");
            check(!BootPolicy.retryOnScreenOn(opted,true,true),"no duplicate root probe");
            check(!BootPolicy.retryOnScreenOn(opted,false,false),"no retry after arming");
        }
        Random random=new Random(2026111702L);
        for(int j=0;j<100000;j++){
            boolean wait=random.nextBoolean(),starting=random.nextBoolean(),fresh=random.nextBoolean();
            boolean retry=BootPolicy.retryOnScreenOn(wait,starting,fresh);
            check(!retry || (wait && !starting && fresh),"fuzz bad retry");
        }
        System.out.println("BOOT_DECISION_STRESS_PASS assertions="+assertions+" cases=200000");
    }
}
