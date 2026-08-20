package com.skmedkart.app;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import androidx.core.app.NotificationCompat;

public class ReminderReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        NotificationManager nm=(NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE);
        String channel="skmedkart_reminders";
        if(android.os.Build.VERSION.SDK_INT>=26)
            nm.createNotificationChannel(new NotificationChannel(channel,"SKMedKART Reminders",NotificationManager.IMPORTANCE_DEFAULT));
        nm.notify(1001,new NotificationCompat.Builder(context,channel)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("SKMedKART Reminder")
                .setContentText("Customer refill reminder")
                .setAutoCancel(true).build());
    }
}
