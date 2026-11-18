package com.appllp7.glanceauto;
import java.util.Random;

public final class WakeReasonStress {
    static int assertions=0;
    static void check(boolean yes,String what){assertions++;if(!yes)throw new AssertionError(what);}
    static String line(long when,String reason){
        return String.format(java.util.Locale.US,"%.3f  1000  1000 I PowerManagerService: Waking up from Asleep (uid=1000, reason=WAKE_REASON_%s)",when/1000.0,reason);
    }
    static WakeReason.Result parse(String s,long now,long last){return WakeReason.classify(s,now,last);}
    public static void main(String[] args){
        long base=1700000000000L;
        check(parse(line(base,"GESTURE"),base+750,0).kind==WakeReason.Kind.GESTURE,"gesture");
        check(parse(line(base,"LIFT"),base+750,0).kind==WakeReason.Kind.GESTURE,"lift");
        check(parse(line(base,"POWER_BUTTON"),base+750,0).kind==WakeReason.Kind.POWER_BUTTON,"power");
        check(parse(line(base,"APPLICATION"),base+750,0).kind==WakeReason.Kind.OTHER,"other");
        check(parse(line(base,"GESTURE"),base+6000,0).kind==WakeReason.Kind.UNKNOWN,"stale");
        check(parse(line(base,"GESTURE"),base-1500,0).kind==WakeReason.Kind.UNKNOWN,"future");
        check(parse(line(base,"GESTURE"),base+750,base).kind==WakeReason.Kind.UNKNOWN,"duplicate");
        check(parse("bad log",base+750,0).kind==WakeReason.Kind.UNKNOWN,"missing");
        check(parse(line(base-4000,"POWER_BUTTON")+"\n"+line(base,"GESTURE"),base+750,0).kind==WakeReason.Kind.GESTURE,"newest record");
        Random r=new Random(4700);
        for(int n=0;n<100000;n++){
            long event=base+n*6500;
            int variant=r.nextInt(6);
            String reason=variant==0?"GESTURE":variant==1?"LIFT":variant==2?"POWER_BUTTON":"APPLICATION";
            long age=variant==4?6000:variant==5?-1500:r.nextInt(2000);
            WakeReason.Kind expected=(age>5500||age< -1000)?WakeReason.Kind.UNKNOWN:
                (variant<2?WakeReason.Kind.GESTURE:variant==2?WakeReason.Kind.POWER_BUTTON:WakeReason.Kind.OTHER);
            WakeReason.Result result=parse(line(event-2000,"POWER_BUTTON")+"\n"+line(event,reason),event+age,0);
            check(result.kind==expected,"mutated wake record n="+n+" variant="+variant+" age="+age);
        }
        System.out.println("WAKE_REASON_STRESS_PASS assertions="+assertions+" cases=100000");
    }
}
