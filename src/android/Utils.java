package de.mopsdom.adfs.cordova.utils;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import org.json.JSONObject;

import java.util.Base64;

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
      return accounts[0];
    }

    return null;
  }

  private static String decodeBase64(String data) {
    byte[] result = null;
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
      result = Base64.getDecoder().decode(data);
    } else {
      result = android.util.Base64.decode(data, android.util.Base64.DEFAULT);
    }
    return new String(result);
  }

  public static long getExpFromIDToken(String id_token)
  {
    String[] parts = id_token.split("\\.");
    String decodedString = decodeBase64(parts[1]);

    JSONObject payload = null;
    try {
      payload = new JSONObject(decodedString);
      if (payload.has("exp"))
      {
        String exp = String.valueOf(payload.getLong("exp"));
        if (exp.length()<13)
        {
          return Long.parseLong(exp)*1000;
        }
        return payload.getLong(exp);
      }
    } catch (Exception e) {
      Log.e(Utils.class.getSimpleName(),e.getMessage());
    }
    return -1;
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
  //  intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

    return intent;
  }

}
