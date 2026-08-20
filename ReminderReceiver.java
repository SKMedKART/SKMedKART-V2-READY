package com.skmedkart.app;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

public class ReminderReceiver extends BroadcastReceiver {
    private static final String CHANNEL="skmedkart_reminders";
    @Override public void onReceive(Context context, Intent intent) {
        NotificationManager nm=(NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE);
        if(Build.VERSION.SDK_INT>=26) nm.createNotificationChannel(new NotificationChannel(CHANNEL,"SKMedKART Reminders",NotificationManager.IMPORTANCE_DEFAULT));
        String customer=intent.getStringExtra("customer");
        String message=intent.getStringExtra("message");
        Intent open=new Intent(context,MainActivity.class); open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi=PendingIntent.getActivity(context,7,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        android.app.Notification.Builder b=Build.VERSION.SDK_INT>=26?new android.app.Notification.Builder(context,CHANNEL):new android.app.Notification.Builder(context);
        b.setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle("SKMedKART Reminder").setContentText((customer==null?"Customer":customer)+": "+(message==null?"Medicine refill reminder":message)).setAutoCancel(true).setContentIntent(pi);
        nm.notify((int)(System.currentTimeMillis()/1000),b.build());
    }
}
