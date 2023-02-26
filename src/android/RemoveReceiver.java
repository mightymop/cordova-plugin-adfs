package de.mopsdom.adfs;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import de.mopsdom.adfs.utils.AccountUtils;

public class RemoveReceiver  extends BroadcastReceiver {
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
  }
}
