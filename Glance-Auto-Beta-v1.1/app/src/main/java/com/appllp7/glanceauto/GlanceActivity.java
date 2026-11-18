package com.appllp7.glanceauto;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.WindowManager;
import android.graphics.Color;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Watch controls. Reboot autostart is always opt-in and STOP disables it. */
public final class GlanceActivity extends Activity {
    private static final String BASE="com.appllp7.glanceauto.";
    private Button button(String label,Runnable action) {
        Button button=new Button(this);button.setText(label);button.setTextSize(16);
        button.setOnClickListener(v->action.run());return button;
    }
    private void send(String action) {
        Intent intent=new Intent(this,GlanceService.class).setAction(BASE+action);
        startForegroundService(intent);
    }
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setPadding(18,16,18,16);
        root.setBackgroundColor(Color.rgb(12,21,30));
        TextView heading=new TextView(this);
        heading.setText("GLANCE AUTO BETA");heading.setTextColor(Color.WHITE);
        heading.setGravity(Gravity.CENTER);heading.setTextSize(23);
        root.addView(heading);
        TextView info=new TextView(this);
        info.setText("Wrist raise: clock. Tap: return. No tap: sleep. Power button: normal wake. Continues automatically until stopped.");
        info.setTextColor(Color.LTGRAY);info.setTextSize(14);info.setGravity(Gravity.CENTER);
        root.addView(info);
        root.addView(button("START AUTO",()->{
            send("START");GlanceService.log(this,"UI_START");moveTaskToBack(true);
        }));
        root.addView(button("STOP / DISARM",()->{
            BootSettings.setEnabled(this,false);
            send("STOP");GlanceService.log(this,"UI_STOP_BOOT_DISABLED");
        }));
        final Button boot=button("REBOOT START: "+(BootSettings.enabled(this)?"ON":"OFF"),()->{});
        boot.setOnClickListener(v->{
            boolean enable=!BootSettings.enabled(this);
            BootSettings.setEnabled(this,enable);
            GlanceService.log(this,"UI_REBOOT_START_"+(enable?"ENABLED":"DISABLED"));
            boot.setText("REBOOT START: "+(enable?"ON":"OFF"));
        });
        root.addView(boot);
        TextView footer=new TextView(this);
        footer.setText("Reboot start OFF by default. No overlay is attached until awake. STOP disables reboot start. Stage 1 preserved.");
        footer.setGravity(Gravity.CENTER);footer.setTextColor(Color.LTGRAY);footer.setTextSize(12);
        root.addView(footer);
        setContentView(root);
        // The installer's explicit autoStart extra starts one opted-in active session.
        if(getIntent()!=null && getIntent().getBooleanExtra("autoStart",false)){
            // Keep the screen awake only for the first Magisk root authorization.
            // The installer sends HOME after passive attachment; an independent
            // 24-second timeout clears this flag even if authorization fails.
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            new Handler(Looper.getMainLooper()).postDelayed(()->
                getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON),24000L);
            send("START");GlanceService.log(this,"INSTALLER_AUTOSTART");
        }
    }
}
