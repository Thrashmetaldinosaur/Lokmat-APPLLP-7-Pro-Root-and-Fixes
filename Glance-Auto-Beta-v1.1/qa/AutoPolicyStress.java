package com.appllp7.glanceauto;
import java.util.Random;

public final class AutoPolicyStress {
    private static int assertions=0;
    private static void check(boolean condition,String why) {
        ++assertions;if(!condition)throw new AssertionError(why+" assertion="+assertions);
    }
    private static void equals(Object actual,Object expected,String why){check(java.util.Objects.equals(actual,expected),why+" actual="+actual);}
    public static void main(String[] args) {
        AutoPolicy refused=new AutoPolicy();
        equals(refused.start(false),AutoPolicy.Action.REFUSE,"cannot attach asleep");
        equals(refused.state(),AutoPolicy.State.NEW,"state unchanged on refused attach");
        equals(refused.start(true),AutoPolicy.Action.ATTACH_PASSIVE,"attach awake");
        equals(refused.start(true),AutoPolicy.Action.REFUSE,"no duplicate overlay");
        for(int j=1;j<=2000;j++){
            equals(refused.screenOff(),AutoPolicy.Action.PROMOTE_SHIELD,"screen-off promotes");
            equals(refused.screenOff(),AutoPolicy.Action.IGNORE,"duplicate screen-off");
            equals(refused.screenOn(),AutoPolicy.Action.CLASSIFY_WAKE,"screen-on must classify");
            equals(refused.screenOn(),AutoPolicy.Action.IGNORE,"duplicate screen-on");
            if(j%4==0){
                equals(refused.wakeResolved(true),AutoPolicy.Action.SHOW_CLOCK,"gesture clock");
                equals(refused.clockTimeout(),AutoPolicy.Action.REQUEST_SLEEP,"no tap requests sleep");
                equals(refused.state(),AutoPolicy.State.CLOCK,"sleep request does not remove shield");
            }else if(j%4==1){
                equals(refused.wakeResolved(true),AutoPolicy.Action.SHOW_CLOCK,"gesture clock");
                equals(refused.tap(),AutoPolicy.Action.RESTORE_PASSIVE,"tap dismiss");
                equals(refused.state(),AutoPolicy.State.PASSIVE,"tap auto rearmed");
            }else if(j%4==2){
                equals(refused.wakeResolved(false),AutoPolicy.Action.RESTORE_PASSIVE,"power/unknown bypass");
            }else{
                equals(refused.wakeGuardExpired(),AutoPolicy.Action.RESTORE_PASSIVE,"delayed logcat fail-open");
            }
            check(refused.state()==AutoPolicy.State.PASSIVE||refused.state()==AutoPolicy.State.CLOCK,
                "awake reachable state");
        }
        equals(refused.cycles(),2000,"cycle counter");
        equals(refused.stop(),AutoPolicy.Action.STOP,"explicit stop");
        equals(refused.screenOff(),AutoPolicy.Action.IGNORE,"no events after stop");
        equals(refused.screenOn(),AutoPolicy.Action.IGNORE,"no restart after stop");
        // Simulated 200,000 random asynchronous/duplicate events, check no invalid transitions.
        Random r=new Random(20261117L);
        AutoPolicy random=new AutoPolicy();
        equals(random.start(true),AutoPolicy.Action.ATTACH_PASSIVE,"random start");
        for(int i=0;i<200000;i++){
            switch(r.nextInt(7)){
                case 0:random.screenOff();break;
                case 1:random.screenOn();break;
                case 2:random.wakeResolved(true);break;
                case 3:random.wakeResolved(false);break;
                case 4:random.wakeGuardExpired();break;
                case 5:random.tap();break;
                case 6:random.sleepFailed();break;
            }
            AutoPolicy.State s=random.state();
            check(s!=AutoPolicy.State.NEW && s!=AutoPolicy.State.STOPPED,"no spurious stop or reset");
        }
        System.out.println("AUTO_POLICY_STRESS_PASS assertions="+assertions+" events=200000 cycles="+random.cycles());
    }
}
