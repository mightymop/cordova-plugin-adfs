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
  public static final String TOKEN_DATA = "TOKEN_DATA";

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

  public static String getTokenData(Context context, Account account)
  {
    AccountManager amgr = AccountManager.get(context);
    return amgr.getUserData(account,TOKEN_DATA);
  }

  public static long getNumberFromTokenData(Context context, Account account, String key)
  {
    String data = getTokenData(context,account);
    try {
      JSONObject json = new JSONObject(data);
      return (json.has(key)?json.getLong(key):-1);
    }
    catch (Exception e)
    {
      Log.e(Utils.class.getSimpleName(),e.getMessage(),e);
      return -1;
    }
  }

  public static Intent getLoginIntent() {

    Intent intent = new Intent("de.mopsdom.adfs.LOGIN_START");

    return intent;
  }

  public static Intent getLogoutIntent() {

    Intent intent = new Intent("de.mopsdom.adfs.LOGOUT_START");

    return intent;
  }

}
