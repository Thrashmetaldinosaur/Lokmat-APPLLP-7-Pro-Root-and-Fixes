package com.appllp7.glanceauto;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Uses the same PowerManagerService wake lines verified by the Stage 1 manual beta. */
public final class WakeReason {
    public enum Kind { GESTURE, POWER_BUTTON, OTHER, UNKNOWN }
    public static final class Result {
        public final Kind kind;
        public final long ageMs, timestampMs;
        Result(Kind kind,long ageMs,long timestampMs){this.kind=kind;this.ageMs=ageMs;this.timestampMs=timestampMs;}
    }
    private static final Pattern EPOCH=Pattern.compile("^\\s*(\\d{10}\\.\\d+)\\s+");
    public static Result classify(String lines,long nowMs,long previousMs) {
        long last=0;String row="";
        if(lines!=null)for(String line:lines.split("\\r?\\n")) {
            if(!line.contains("Waking up from") || !line.contains("WAKE_REASON_"))continue;
            Matcher m=EPOCH.matcher(line);
            if(!m.find())continue;
            try {
                long when=(long)(Double.parseDouble(m.group(1))*1000);
                if(when>=last){last=when;row=line;}
            }catch(NumberFormatException ignored){}
        }
        if(last==0 || last<=previousMs)return new Result(Kind.UNKNOWN,Long.MAX_VALUE,last);
        long age=nowMs-last;
        if(age < -1000 || age > 5500)return new Result(Kind.UNKNOWN,age,last);
        Kind kind=(row.contains("WAKE_REASON_GESTURE")||row.contains("WAKE_REASON_LIFT"))?Kind.GESTURE:
            row.contains("WAKE_REASON_POWER_BUTTON")?Kind.POWER_BUTTON:Kind.OTHER;
        return new Result(kind,age,last);
    }
}
