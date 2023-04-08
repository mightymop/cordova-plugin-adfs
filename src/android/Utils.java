package de.mopsdom.adfs.cordova.utils;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.Context;
import android.content.Intent;

public class Utils {

  private final static String ACCOUNT_TYPE = "SSO";
  public static final String KEY_IS_NEW_ACCOUNT = "NEW_ACCOUNT";
  public final static String ACCOUNT_STATE_KEY = "STATUS";

  public static void setAccountData(Context ctx, Account acc, String key, String data) {
    AccountManager am = (AccountManager) ctx.getSystemService(Context.ACCOUNT_SERVICE);
    if (acc != null) {
      am.setUserData(acc, key, data);
    }
  }

  public static String getAccountData(Context ctx, Account acc, String key) {
    AccountManager am = (AccountManager) ctx.getSystemService(Context.ACCOUNT_SERVICE);
    if (acc != null) {
      return am.getUserData(acc, key);
    }

    return null;
  }

  public static Account getCurrentUser(Context ctx) {
    AccountManager am = (AccountManager) ctx.getSystemService(Context.ACCOUNT_SERVICE);
    Account[] accounts = am.getAccountsByType(ACCOUNT_TYPE);

    if (accounts != null && accounts.length > 0) {
      for (Account account : accounts) {
        String state = getAccountData(ctx, account, ACCOUNT_STATE_KEY);
        if (state != null && (state.equalsIgnoreCase("1") || state.equalsIgnoreCase("true"))) {
          return account;
        }
      }
    }

    return null;
  }

  public static Intent getLoginIntent() {

    Intent intent = new Intent();
    intent.setAction("de.mopsdom.adfs.LOGIN_START");

    intent.putExtra("ACCOUNT_TYPE", ACCOUNT_TYPE);
    intent.putExtra(KEY_IS_NEW_ACCOUNT, true);

    return intent;
  }

  public static Intent getLogoutIntent() {

    Intent intent = new Intent();
    intent.setAction("de.mopsdom.adfs.LOGOUT_START");
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_CLEAR_TASK|Intent.FLAG_ACTIVITY_NO_HISTORY);

    return intent;
  }

}
