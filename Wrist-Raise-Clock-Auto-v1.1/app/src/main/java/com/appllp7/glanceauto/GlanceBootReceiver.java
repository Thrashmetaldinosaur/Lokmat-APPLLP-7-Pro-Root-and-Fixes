package com.appllp7.glanceauto;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Boot resumes only after explicit user opt-in; never creates a window itself. */
public final class GlanceBootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context,Intent intent){
        if(intent==null || !Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction()))return;
        if(BootPolicy.onBoot(BootSettings.enabled(context),false)==BootPolicy.Decision.IGNORE){
            GlanceService.log(context,"BOOT_RECEIVED_OPT_OUT no_service_started");return;
        }
        GlanceService.log(context,"BOOT_RECEIVED_OPTED_IN starting_foreground_service");
        try{
            Intent start=new Intent(context,GlanceService.class)
                .setAction("com.appllp7.glanceauto.BOOT");
            context.startForegroundService(start);
        }catch(RuntimeException ex){
            GlanceService.log(context,"BOOT_START_REJECTED "+ex.getClass().getSimpleName());
            // Fail safe: don't launch the activity or overlay from the background.
            // User may open Glance Auto Beta manually to start a normal session.
        }
    }
}
