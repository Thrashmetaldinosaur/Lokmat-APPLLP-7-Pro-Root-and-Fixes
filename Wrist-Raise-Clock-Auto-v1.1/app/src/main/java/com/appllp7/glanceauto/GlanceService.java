package com.appllp7.glanceauto;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.provider.Settings;
import android.text.format.DateFormat;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * Reversible auto-rearming beta. One preattached overlay, always touch-through
 * while awake, becomes an opaque shield at SCREEN_OFF without addView.
 * It stays touch-blocking on SCREEN_ON until root wake reason is verified.
 * Unknown/power-button wakes fail open to passive, never launch clock.
 * Optional boot receiver starts a service without adding any overlay while asleep.
 * No changes to firmware, Magisk, OEM wrist wake or timeout.
 */
public final class GlanceService extends Service {
    private static final String CHANNEL="appllp7_glance_auto_beta_v1";
    private static final long WAKE_GUARD_MS=4700L;
    private static final long CLOCK_TIME_MS=8500L;
    private static final long CLOCK_HARD_GUARD_MS=11000L;
    private static final String LOG_COMMAND=
        "logcat -b all -d -t 1200 -v epoch -s PowerManagerService:I WindowWakeUpPolicy:I '*:S' | tail -n 100";
    private final Handler main=new Handler(Looper.getMainLooper());
    private final AutoPolicy policy=new AutoPolicy();
    private WindowManager wm;
    private FrameLayout layer;
    private BroadcastReceiver screenReceiver;
    private PowerManager.WakeLock clockWakeLock;
    private boolean stopped=false;
    private boolean starting=false;
    private boolean bootWaiting=false;
    private int bootRootRetries=0;
    private boolean clockVisible=false;
    private long epoch=0;
    private long screenOnElapsed=0;
    private long lastWakeRecord=0;
    private Runnable clockTimer;
    private Runnable wakeGuard;
    private Runnable hardClockGuard;
    private Runnable shieldWatchdog;
    static synchronized void log(Context c,String line){
        try(FileOutputStream f=c.openFileOutput("events.log",Context.MODE_APPEND)){
            f.write((SystemClock.elapsedRealtime()+" "+line+"\n").getBytes(StandardCharsets.UTF_8));
        }catch(Exception ignored){}
    }
    private static String shortText(String value){
        if(value==null)return "";
        String cleaned=value.replace('\n',' ').replace('\r',' ');
        return cleaned.length()>170?cleaned.substring(0,170):cleaned;
    }
    private static String root(Context c,String command,int timeoutSec){
        Process p=null;
        try{
            p=new ProcessBuilder("su","-c",command).redirectErrorStream(true).start();
            if(!p.waitFor(timeoutSec,TimeUnit.SECONDS)){
                p.destroyForcibly();log(c,"ROOT_TIMEOUT "+command.split(" ")[0]);return "ERROR:TIMEOUT";
            }
            int exit=p.exitValue();
            byte[] data;
            try(InputStream in=p.getInputStream()){data=in.readNBytes(24000);}
            String output=new String(data,StandardCharsets.UTF_8);
            if(exit!=0){log(c,"ROOT_FAILED cmd="+command.split(" ")[0]+" exit="+exit);return "ERROR:"+exit+":"+output;}
            return output;
        }catch(Throwable ex){log(c,"ROOT_EXCEPTION "+ex.getClass().getSimpleName());return "ERROR:"+ex.getClass().getSimpleName();}
        finally{if(p!=null)try{p.destroy();}catch(Throwable ignored){}}
    }
    @Override public void onCreate(){
        super.onCreate();
        wm=(WindowManager)getSystemService(WINDOW_SERVICE);
        NotificationManager nm=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        nm.createNotificationChannel(new NotificationChannel(CHANNEL,"Glance Auto Beta",NotificationManager.IMPORTANCE_MIN));
        Intent open=new Intent(this,GlanceActivity.class);
        PendingIntent openPending=PendingIntent.getActivity(this,0,open,
            PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Intent stop=new Intent(this,GlanceService.class).setAction("com.appllp7.glanceauto.STOP");
        PendingIntent stopPending=PendingIntent.getService(this,1,stop,
            PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Notification notification=new Notification.Builder(this,CHANNEL)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("Glance Auto Beta active")
            .setContentText("Tap to open controls; stop available")
            .setContentIntent(openPending)
            .addAction(new Notification.Action.Builder(android.R.drawable.ic_delete,"STOP",stopPending).build())
            .setOngoing(true).build();
        startForeground(7432,notification);
        log(this,"SERVICE_CREATED auto_beta=1.1");
        screenReceiver=new BroadcastReceiver(){@Override public void onReceive(Context c,Intent i){
            if(Intent.ACTION_SCREEN_OFF.equals(i.getAction()))handleScreenOff();
            else if(Intent.ACTION_SCREEN_ON.equals(i.getAction()))handleScreenOn();
        }};
        IntentFilter filter=new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_OFF);filter.addAction(Intent.ACTION_SCREEN_ON);
        registerReceiver(screenReceiver,filter);
    }
    @Override public int onStartCommand(Intent intent,int flags,int startId){
        String action=intent==null?"":intent.getAction();
        if("com.appllp7.glanceauto.STOP".equals(action)){
            BootSettings.setEnabled(this,false);
            bootWaiting=false;
            log(this,"EXPLICIT_STOP boot_start_disabled=true");shutdown("USER_STOP");
        }else if("com.appllp7.glanceauto.START".equals(action)){
            bootWaiting=false;
            startAuto();
        }else if("com.appllp7.glanceauto.BOOT".equals(action)){
            if(!BootSettings.enabled(this)){
                log(this,"BOOT_SERVICE_IGNORED preference_disabled");shutdown("BOOT_NOT_OPTED_IN");
            } else {
                bootWaiting=true;
                log(this,"BOOT_SERVICE_WAIT_FOR_AWAKE no_overlay_attached");
                PowerManager p=(PowerManager)getSystemService(POWER_SERVICE);
                BootPolicy.Decision boot=BootPolicy.onBoot(true,p!=null && p.isInteractive());
                if(boot==BootPolicy.Decision.ATTEMPT_WHILE_AWAKE)
                    main.postDelayed(()->startAuto(),1000L);
                else log(this,"BOOT_DEFERRED_UNTIL_SCREEN_ON no_overlay_attached");
            }
        }
        return START_NOT_STICKY;
    }
    private void startAuto(){
        if(stopped || starting || policy.state()!=AutoPolicy.State.NEW){
            log(this,"START_IGNORED state="+policy.state());return;
        }
        if(!Settings.canDrawOverlays(this)){
            log(this,"START_REFUSED overlay_permission=false");shutdown("NO_OVERLAY_PERMISSION");return;
        }
        PowerManager pm=(PowerManager)getSystemService(POWER_SERVICE);
        if(pm==null || !pm.isInteractive()){
            if(bootWaiting){
                log(this,"BOOT_DEFERRED_UNTIL_SCREEN_ON no_overlay_attached");
                return;
            }
            log(this,"START_REFUSED screen_not_awake");shutdown("START_NOT_AWAKE");return;
        }
        starting=true;
        log(this,"ROOT_PROBE_BEGIN");
        new Thread(()->{
            String result=root(this,"id -u",15);
            boolean granted="0".equals(result.trim());
            main.post(()->{
                if(stopped)return;
                starting=false;
                if(!granted){
                    log(this,"ROOT_PROBE_DENIED "+shortText(result));
                    if(bootWaiting && bootRootRetries<3){
                        bootRootRetries++;
                        long retryMs=bootRootRetries*5000L;
                        log(this,"BOOT_ROOT_RETRY_DEFERRED attempt="+bootRootRetries+
                            " delay_ms="+retryMs+" no_overlay_attached");
                        main.postDelayed(()->startAuto(),retryMs);
                        return;
                    }
                    // Never create an overlay without Magisk authorization.
                    shutdown("ROOT_NOT_GRANTED");return;
                }
                log(this,"ROOT_PROBE_GRANTED");
                PowerManager power=(PowerManager)getSystemService(POWER_SERVICE);
                if(!BootPolicy.mayAttach(power!=null && power.isInteractive(),granted)){
                    if(bootWaiting){
                        log(this,"BOOT_ROOT_GRANTED_WAIT_FOR_NEXT_WAKE no_overlay_attached");
                        return;
                    }
                    shutdown("ROOT_PROBE_FINISHED_WHILE_ASLEEP");return;
                }
                attachPassive();
            });
        },"glance-auto-root-init").start();
    }
    private void attachPassive(){
        if(stopped || layer!=null || !Settings.canDrawOverlays(this)){shutdown("ATTACH_PRECONDITION_FAILED");return;}
        PowerManager pm=(PowerManager)getSystemService(POWER_SERVICE);
        if(policy.start(pm!=null && pm.isInteractive())!=AutoPolicy.Action.ATTACH_PASSIVE){
            log(this,"PASSIVE_ATTACH_REFUSED not_awake");shutdown("PASSIVE_ATTACH_NOT_AWAKE");return;
        }
        FrameLayout view=new FrameLayout(this);
        view.setBackgroundColor(Color.BLACK);
        view.setClickable(true);
        view.setOnTouchListener((v,event)->{
            if(event.getActionMasked()==MotionEvent.ACTION_DOWN){
                log(this,"SHIELD_TOUCH_DOWN clock="+clockVisible);
            } else if(event.getActionMasked()==MotionEvent.ACTION_UP){
                log(this,"SHIELD_TOUCH_UP clock="+clockVisible);
                if(clockVisible && policy.state()==AutoPolicy.State.CLOCK){
                    final long ticket=epoch;
                    main.postDelayed(()->{
                        if(!stopped && ticket==epoch && clockVisible &&
                            policy.tap()==AutoPolicy.Action.RESTORE_PASSIVE){
                            log(this,"TAP_DISMISS_AUTO_REARM cycle="+policy.cycles());
                            setPassive("CLOCK_TAP");
                        }
                    },150L);
                }
            }
            return true;
        });
        WindowManager.LayoutParams lp=new WindowManager.LayoutParams(-1,-1,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE |
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT);
        lp.gravity=Gravity.CENTER;lp.alpha=0f;
        lp.setTitle("Glance Auto Beta existing passive window");
        try{
            wm.addView(view,lp);layer=view;
            bootWaiting=false;
            log(this,"AUTO_PASSIVE_ATTACHED non_touchable=true alpha=0");
        }catch(Throwable t){log(this,"ATTACH_FAILED "+t.getClass().getSimpleName());shutdown("ATTACH_FAILED");}
    }
    private void handleScreenOff(){
        if(stopped || layer==null)return;
        ++epoch;clearTimers();releaseClockWakeLock("SCREEN_OFF");
        clockVisible=false;if(layer.getChildCount()>0)layer.removeAllViews();
        AutoPolicy.Action action=policy.screenOff();
        log(this,"SCREEN_OFF action="+action+" cycle="+policy.cycles());
        if(action==AutoPolicy.Action.PROMOTE_SHIELD){
            if(updateWindow(true,false,"SCREEN_OFF"))scheduleShieldWatchdog();
        }
    }
    private void handleScreenOn(){
        if(stopped)return;
        if(layer==null){
            if(BootPolicy.retryOnScreenOn(bootWaiting,starting,policy.state()==AutoPolicy.State.NEW)){
                log(this,"BOOT_FIRST_SCREEN_ON begin_delayed_attach");
                main.postDelayed(()->startAuto(),650L);
            }
            return;
        }
        if(shieldWatchdog!=null){main.removeCallbacks(shieldWatchdog);shieldWatchdog=null;}
        AutoPolicy.Action action=policy.screenOn();
        log(this,"SCREEN_ON action="+action+" cycle="+policy.cycles());
        // Duplicate SCREEN_ON must not invalidate the active clock/tap/timeout guards.
        if(action!=AutoPolicy.Action.CLASSIFY_WAKE){return;}
        screenOnElapsed=SystemClock.elapsedRealtime();
        final long ticket=++epoch;
        // Keep the EXISTING opaque, touch-blocking layer until cause is known.
        // Guard always restores normal touch-through even if Magisk/logcat fails.
        wakeGuard=()->{
            if(stopped || ticket!=epoch)return;
            if(policy.wakeGuardExpired()==AutoPolicy.Action.RESTORE_PASSIVE){
                log(this,"WAKE_GUARD_FAIL_OPEN cycle="+policy.cycles());
                setPassive("WAKE_GUARD");
            }
        };
        main.postDelayed(wakeGuard,WAKE_GUARD_MS);
        new Thread(()->{
            final String output=root(this,LOG_COMMAND,6);
            final WakeReason.Result result=output.startsWith("ERROR:")?
                new WakeReason.Result(WakeReason.Kind.UNKNOWN,Long.MAX_VALUE,0):
                WakeReason.classify(output,System.currentTimeMillis(),lastWakeRecord);
            main.post(()->{
                if(stopped || ticket!=epoch || policy.state()!=AutoPolicy.State.CHECKING_WAKE)return;
                if(result.kind!=WakeReason.Kind.UNKNOWN && result.timestampMs>lastWakeRecord){
                    lastWakeRecord=result.timestampMs;
                }
                log(this,"WAKE_CLASSIFIED reason="+result.kind+" age_ms="+result.ageMs+
                    " cycle="+policy.cycles());
                AutoPolicy.Action next=policy.wakeResolved(result.kind==WakeReason.Kind.GESTURE);
                if(next==AutoPolicy.Action.SHOW_CLOCK)showClock();
                else if(next==AutoPolicy.Action.RESTORE_PASSIVE)setPassive("WAKE_"+result.kind);
            });
        },"glance-auto-wake-reader").start();
    }
    /** Polls only while asleep/shielded, never continuously while normally using the watch.
     * Does not acquire a wake lock or introduce another overlay. If SCREEN_ON is lost,
     * the watchdog invokes the same guarded classification path. */
    private void scheduleShieldWatchdog(){
        if(shieldWatchdog!=null){main.removeCallbacks(shieldWatchdog);shieldWatchdog=null;}
        final long ticket=epoch;
        shieldWatchdog=new Runnable(){@Override public void run(){
            if(stopped || ticket!=epoch || policy.state()!=AutoPolicy.State.ASLEEP_SHIELDED)return;
            PowerManager pm=(PowerManager)getSystemService(POWER_SERVICE);
            if(pm!=null && pm.isInteractive()){
                log(GlanceService.this,"MISSED_SCREEN_ON_WATCHDOG_RECOVERY");
                handleScreenOn();
            }else main.postDelayed(this,3000L);
        }};
        main.postDelayed(shieldWatchdog,3000L);
    }
    private boolean updateWindow(boolean protectedMode,boolean keepAwake,String why){
        if(stopped || layer==null){shutdown("WINDOW_MISSING_"+why);return false;}
        try{
            WindowManager.LayoutParams lp=(WindowManager.LayoutParams)layer.getLayoutParams();
            if(protectedMode){lp.flags &= ~WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;lp.alpha=1f;}
            else{lp.flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;lp.alpha=0f;}
            if(keepAwake)lp.flags |= WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON;
            else lp.flags &= ~WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON;
            wm.updateViewLayout(layer,lp);
            log(this,(protectedMode?"SHIELD_ACTIVE":"PASSIVE_RESTORED")+" reason="+why+
                " cycle="+policy.cycles());
            return true;
        }catch(Throwable t){log(this,"WINDOW_UPDATE_FAILED "+why+" "+t.getClass().getSimpleName());shutdown("UPDATE_FAILED");return false;}
    }
    private void showClock(){
        if(stopped || layer==null || policy.state()!=AutoPolicy.State.CLOCK)return;
        PowerManager pm=(PowerManager)getSystemService(POWER_SERVICE);
        if(pm==null || !pm.isInteractive()){
            log(this,"CLOCK_SUPPRESSED_SCREEN_NOT_INTERACTIVE");
            if(policy.sleepFailed()==AutoPolicy.Action.RESTORE_PASSIVE)setPassive("NOT_INTERACTIVE");
            return;
        }
        clearTimers();layer.removeAllViews();
        FrameLayout base=layer;
        LinearLayout group=new LinearLayout(this);
        group.setOrientation(LinearLayout.VERTICAL);group.setGravity(Gravity.CENTER);
        Date now=new Date();
        String time=DateFormat.is24HourFormat(this)?
            new SimpleDateFormat("HH:mm",Locale.getDefault()).format(now):
            new SimpleDateFormat("h:mm",Locale.getDefault()).format(now);
        DisplayMetrics dm=getResources().getDisplayMetrics();
        TextView clock=new TextView(this);clock.setGravity(Gravity.CENTER);
        clock.setTextColor(Color.WHITE);
        clock.setTypeface(Typeface.create("sans-serif-condensed-light",Typeface.NORMAL));
        clock.setIncludeFontPadding(false);clock.setMaxLines(1);clock.setText(time);
        float desired=170f;
        clock.setTextSize(TypedValue.COMPLEX_UNIT_SP,desired);
        float actual=clock.getPaint().measureText(time);
        float font=actual>dm.widthPixels*.86f?desired*(dm.widthPixels*.86f/actual):desired;
        clock.setTextSize(TypedValue.COMPLEX_UNIT_SP,font);
        TextView day=new TextView(this);day.setText(new SimpleDateFormat("EEEE",Locale.getDefault()).format(now));
        day.setGravity(Gravity.CENTER);day.setTextColor(Color.rgb(193,193,193));
        day.setTextSize(TypedValue.COMPLEX_UNIT_SP,34f);day.setIncludeFontPadding(false);
        TextView date=new TextView(this);date.setText(new SimpleDateFormat("MMMM d",Locale.getDefault()).format(now));
        date.setGravity(Gravity.CENTER);date.setTextColor(Color.rgb(193,193,193));
        date.setTextSize(TypedValue.COMPLEX_UNIT_SP,36f);date.setIncludeFontPadding(false);
        group.addView(clock,new LinearLayout.LayoutParams(-1,-2));
        LinearLayout.LayoutParams dayParams=new LinearLayout.LayoutParams(-1,-2);
        dayParams.topMargin=(int)(dm.density*5);group.addView(day,dayParams);
        LinearLayout.LayoutParams dateParams=new LinearLayout.LayoutParams(-1,-2);
        dateParams.topMargin=(int)(dm.density*3);group.addView(date,dateParams);
        base.addView(group,new FrameLayout.LayoutParams(-1,-2,Gravity.CENTER));
        if(!updateWindow(true,true,"SHOW_GESTURE_CLOCK"))return;
        clockVisible=true;
        acquireClockWakeLock();
        final long ticket=epoch;
        log(this,"CLOCK_VISIBLE_AUTO cycle="+policy.cycles()+" font_sp="+font+
            " since_screen_on_ms="+(SystemClock.elapsedRealtime()-screenOnElapsed));
        clockTimer=()->{
            if(stopped || ticket!=epoch || policy.clockTimeout()!=AutoPolicy.Action.REQUEST_SLEEP)return;
            log(this,"CLOCK_NO_TAP_REQUEST_SLEEP cycle="+policy.cycles());
            releaseClockWakeLock("CLOCK_TIMEOUT");
            updateWindow(true,false,"PREPARE_AUTO_SLEEP");
            new Thread(()->{
                String sleep=root(this,"input keyevent 223",7);
                main.post(()->{
                    if(stopped || ticket!=epoch)return;
                    log(this,"CLOCK_SLEEP_COMMAND "+(sleep.startsWith("ERROR:")?"FAILED":"SENT"));
                    if(sleep.startsWith("ERROR:")){
                        if(policy.sleepFailed()==AutoPolicy.Action.RESTORE_PASSIVE)setPassive("ROOT_SLEEP_FAILED");
                    } else {
                        main.postDelayed(()->{
                            if(stopped || ticket!=epoch)return;
                            PowerManager power=(PowerManager)getSystemService(POWER_SERVICE);
                            if(power!=null && power.isInteractive() &&
                                policy.sleepFailed()==AutoPolicy.Action.RESTORE_PASSIVE){
                                log(this,"CLOCK_SLEEP_NOT_CONFIRMED_FAIL_OPEN");
                                setPassive("SLEEP_UNCONFIRMED");
                            }
                        },2500L);
                    }
                });
            },"glance-auto-sleep").start();
        };
        main.postDelayed(clockTimer,CLOCK_TIME_MS);
        hardClockGuard=()->{
            if(stopped || ticket!=epoch || policy.state()!=AutoPolicy.State.CLOCK)return;
            PowerManager p=(PowerManager)getSystemService(POWER_SERVICE);
            if(p!=null && p.isInteractive() &&
                policy.sleepFailed()==AutoPolicy.Action.RESTORE_PASSIVE){
                log(this,"CLOCK_HARD_GUARD_RESTORE_PASSIVE");setPassive("HARD_GUARD");
            }
        };
        main.postDelayed(hardClockGuard,CLOCK_HARD_GUARD_MS);
    }
    private void setPassive(String reason){
        clearTimers();releaseClockWakeLock("PASSIVE_"+reason);
        clockVisible=false;
        if(layer!=null)layer.removeAllViews();
        updateWindow(false,false,reason);
    }
    private void acquireClockWakeLock(){
        releaseClockWakeLock("REPLACE");
        try{
            PowerManager pm=(PowerManager)getSystemService(POWER_SERVICE);
            clockWakeLock=pm.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK,"APPLLP7GlanceAuto:ClockHold");
            clockWakeLock.setReferenceCounted(false);
            clockWakeLock.acquire(9500L);
            log(this,"CLOCK_WAKELOCK_ACQUIRED max_ms=9500");
        }catch(Throwable t){log(this,"CLOCK_WAKELOCK_FAILED "+t.getClass().getSimpleName());clockWakeLock=null;}
    }
    private void releaseClockWakeLock(String cause){
        PowerManager.WakeLock wl=clockWakeLock;clockWakeLock=null;
        if(wl!=null)try{if(wl.isHeld())wl.release();log(this,"CLOCK_WAKELOCK_RELEASED "+cause);}catch(Throwable ignored){}
    }
    private void clearTimers(){
        if(clockTimer!=null){main.removeCallbacks(clockTimer);clockTimer=null;}
        if(wakeGuard!=null){main.removeCallbacks(wakeGuard);wakeGuard=null;}
        if(hardClockGuard!=null){main.removeCallbacks(hardClockGuard);hardClockGuard=null;}
        if(shieldWatchdog!=null){main.removeCallbacks(shieldWatchdog);shieldWatchdog=null;}
    }
    private void shutdown(String why){
        if(stopped)return;
        stopped=true;++epoch;policy.stop();clearTimers();releaseClockWakeLock(why);
        if(layer!=null){try{wm.removeViewImmediate(layer);}catch(Throwable ignored){}layer=null;}
        clockVisible=false;
        log(this,"STOPPED_CLEANLY why="+why);
        stopSelf();
    }
    @Override public void onDestroy(){
        shutdown("SERVICE_DESTROYED");
        if(screenReceiver!=null){try{unregisterReceiver(screenReceiver);}catch(Throwable ignored){}}
        log(this,"SERVICE_DESTROYED");super.onDestroy();
    }
    @Override public IBinder onBind(Intent intent){return null;}
}
