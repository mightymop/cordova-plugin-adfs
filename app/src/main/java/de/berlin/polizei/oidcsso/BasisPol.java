package de.berlin.polizei.oidcsso;

import static de.berlin.polizei.oidcsso.authenticator.ADFSAuthenticator.CHANNEL_DESCRIPTION;
import static de.berlin.polizei.oidcsso.authenticator.ADFSAuthenticator.CHANNEL_ID;
import static de.berlin.polizei.oidcsso.authenticator.ADFSAuthenticator.CHANNEL_NAME;

import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.graphics.Color;
import android.os.Build;

public class BasisPol extends Application {

    @Override
    public void onCreate() {
        super.onCreate();

        createNotificationChannel(this);
    }

    public static void createNotificationChannel(Context ctx)
    {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager notificationManager =
                    (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);


            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription(CHANNEL_DESCRIPTION);
            channel.enableLights(true);
            channel.setLightColor(Color.RED);
            notificationManager.createNotificationChannel(channel);
        }
    }
}