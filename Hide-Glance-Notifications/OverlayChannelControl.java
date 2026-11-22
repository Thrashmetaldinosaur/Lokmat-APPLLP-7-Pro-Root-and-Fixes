import android.os.IBinder;
import android.app.NotificationChannel;
import java.lang.reflect.Method;

/**
 * Per-overlay-app Android System notification-channel reader/editor.
 * Run on an authorized rooted Android device via app_process.
 * Operates on exactly one existing channel; does not modify overlay permissions.
 */
public final class OverlayChannelControl {
  private static final String PREFIX="com.android.server.wm.AlertWindowNotification - ";
  public static void main(String[] args) {
    try {
      if (args.length != 2 || !args[0].matches("[a-zA-Z0-9_]+(\\.[a-zA-Z0-9_]+)+")
          || !(args[1].equals("read") || args[1].equals("hide") || args[1].equals("restore"))) {
        throw new IllegalArgumentException("Usage: OverlayChannelControl PACKAGE read|hide|restore");
      }
      String id=PREFIX+args[0];
      Class<?> serviceManager=Class.forName("android.os.ServiceManager");
      IBinder binder=(IBinder)serviceManager.getDeclaredMethod("getService",String.class).invoke(null,"notification");
      if(binder==null)throw new IllegalStateException("Notification service missing");
      Class<?> stub=Class.forName("android.app.INotificationManager$Stub");
      Object notification=stub.getDeclaredMethod("asInterface",IBinder.class).invoke(null,binder);
      Class<?> manager=Class.forName("android.app.INotificationManager");
      Method get=manager.getMethod("getNotificationChannelForPackage",
          String.class,int.class,String.class,String.class,boolean.class);
      NotificationChannel channel=(NotificationChannel)get.invoke(notification,"android",1000,id,null,true);
      if(channel==null)throw new IllegalStateException("No existing overlay warning channel for "+args[0]);
      System.out.println("BEFORE package="+args[0]+" importance="+channel.getImportance()
          +" channel="+channel.getId());
      if(args[1].equals("read"))return;
      // Android System is UID 1000 on the tested model. Channel may be updated as root.
      Method update=manager.getMethod("updateNotificationChannelForPackage",
          String.class,int.class,NotificationChannel.class);
      channel.setImportance(args[1].equals("hide")?0:1);
      update.invoke(notification,"android",1000,channel);
      NotificationChannel result=(NotificationChannel)get.invoke(notification,"android",1000,id,null,true);
      if(result==null || result.getImportance()!=(args[1].equals("hide")?0:1))
          throw new IllegalStateException("Channel update not persisted");
      System.out.println("AFTER importance="+result.getImportance()+" success=true");
    } catch(Throwable error) {
      System.err.println("ERROR "+error);
      System.exit(1);
    }
  }
}
