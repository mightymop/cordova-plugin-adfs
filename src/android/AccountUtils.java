package de.mopsdom.adfs.utils;

import static de.mopsdom.adfs.request.RequestManager.ACCOUNT_STATE_KEY;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.Context;
import android.webkit.CookieManager;

public class AccountUtils {

  public static Account getAccountByName(Context ctx, String accountName) {
    if (accountName != null) {
      AccountManager am = (AccountManager) ctx.getSystemService(Context.ACCOUNT_SERVICE);
      Account[] accounts = am.getAccountsByType(getAccountTypeKey(ctx));
      for (Account account : accounts) {
        if (accountName.equals(account.name)) {
          return account;
        }
      }
    }
    return null;
  }

  public static String getAccountTypeKey(Context ctx) {
    String resourceName = "account_type_user";
    String resourceType = "string";
    String packageName = ctx.getPackageName();
    int resourceId = ctx.getResources().getIdentifier(resourceName, resourceType, packageName);
    String resourceValue = ctx.getString(resourceId);
    return resourceValue;
  }

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

  public static Account getUserIFExists(Context ctx, String user_id) {
    AccountManager accountManager = (AccountManager) ctx.getSystemService(Context.ACCOUNT_SERVICE);
    Account[] accounts = accountManager.getAccountsByType(getAccountTypeKey(ctx));

    if (accounts != null && accounts.length > 0 && user_id != null) {
      for (Account account : accounts) {
        if (account.name.equalsIgnoreCase(user_id)) {
          return account;
        }
      }
    }

    return null;
  }

  public static Account getCurrentUser(Context ctx) {
    AccountManager am = (AccountManager) ctx.getSystemService(Context.ACCOUNT_SERVICE);
    Account[] accounts = am.getAccountsByType(getAccountTypeKey(ctx));

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
}
