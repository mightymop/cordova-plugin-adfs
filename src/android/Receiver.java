package de.mopsdom.adfs;

import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.annotation.RequiresApi;

import de.mopsdom.adfs.utils.AccountUtils;

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
      String ownKey = AccountUtils.getAccountTypeKey(context);
      if (accountType.equalsIgnoreCase(ownKey))
      {
        ADFSAuthenticator.logout(context);
      }
    }
    else if (action.equalsIgnoreCase(Intent.ACTION_BOOT_COMPLETED)||
    action.equalsIgnoreCase(Intent.ACTION_PACKAGE_ADDED)||
    action.equalsIgnoreCase(Intent.ACTION_PACKAGE_DATA_CLEARED)||
    action.equalsIgnoreCase(Intent.ACTION_PACKAGE_REPLACED))
    {
      NotificationManager notificationManager =
        (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
      if (notificationManager != null) {
        notificationManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL);
      }
    }
  }
}
