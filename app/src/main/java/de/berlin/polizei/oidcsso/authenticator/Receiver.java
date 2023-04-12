package de.berlin.polizei.oidcsso.authenticator;


import static de.berlin.polizei.oidcsso.authenticator.ADFSAuthenticator.CHANNEL_ID;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.annotation.RequiresApi;

import de.berlin.polizei.oidcsso.BasisPol;
import de.berlin.polizei.oidcsso.R;
import de.berlin.polizei.oidcsso.utils.Utils;


public class Receiver extends BroadcastReceiver {

  @RequiresApi(api = Build.VERSION_CODES.O)
  @Override
  public void onReceive(Context context, Intent intent) {
    String action = intent.getAction();

    if (action.equals("android.accounts.action.ACCOUNT_REMOVED")) {
      String accountType = intent.getStringExtra("accountType");
      if (accountType==null)
      {
        accountType = intent.getStringExtra("account_type");
      }
      String ownKey = context.getString(R.string.account_type);
      if (accountType.equalsIgnoreCase(ownKey))
      {
        String lastlogoutstr = Utils.getSharedPref(context,"lastlogout");
        if (lastlogoutstr==null||Long.parseLong(lastlogoutstr)<System.currentTimeMillis()-60000) {
          ADFSAuthenticator.logout(context);
        }
      }
    }
    else if (action.equalsIgnoreCase(Intent.ACTION_BOOT_COMPLETED)||
    action.equalsIgnoreCase(Intent.ACTION_PACKAGE_ADDED)||
    action.equalsIgnoreCase(Intent.ACTION_PACKAGE_DATA_CLEARED)||
    action.equalsIgnoreCase(Intent.ACTION_PACKAGE_REPLACED))
    {
      NotificationManager notificationManager =
              (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

      NotificationChannel channel = notificationManager.getNotificationChannel(CHANNEL_ID);
      if (channel != null) {
        BasisPol.createNotificationChannel(context);
        channel.setImportance(NotificationManager.IMPORTANCE_DEFAULT);
        notificationManager.createNotificationChannel(channel);
      }
    }
  }
}
