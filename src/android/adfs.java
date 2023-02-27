package de.mopsdom.adfs;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.AccountManagerCallback;
import android.accounts.AccountManagerFuture;
import android.accounts.AuthenticatorException;
import android.accounts.OperationCanceledException;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.NonNull;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CordovaWebView;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;

import de.mopsdom.adfs.request.RequestManager;
import de.mopsdom.adfs.utils.AccountUtils;
import de.mopsdom.adfs.utils.Utils;

public class adfs extends CordovaPlugin {

  private final static String TAG = "ADFS_PLUGIN";

  private final static int LOGIN_RES = 110;
  private final static int LOGIN_REAUTH = 111;

  private JSONObject request;

  private CallbackContext callbackContext;
  private ADFSAuthenticator authenticator;

  @Override
  public void initialize(CordovaInterface cordova, CordovaWebView webView) {
    super.initialize(cordova, webView);
    authenticator = new ADFSAuthenticator(cordova.getContext());
  }

  private void getToken(CallbackContext callbackCtx, String authTokenType) {
    AccountManager accountManager = AccountManager.get(cordova.getActivity());
    Account acc = AccountUtils.getCurrentUser(cordova.getActivity());
    if (acc != null) {
      try {
       Bundle options = new Bundle();
       accountManager.getAuthToken(acc, authTokenType, options, true, new AccountManagerCallback<Bundle>() {
          @Override
          public void run(AccountManagerFuture<Bundle> future) {
            try {
              Bundle result = future.getResult();

              if (result.keySet().contains(AccountManager.KEY_AUTHTOKEN))
              {
                callbackCtx.sendPluginResult(new PluginResult(PluginResult.Status.OK, result.getString(AccountManager.KEY_AUTHTOKEN)));
              }
              else {
                Intent i = (Intent) result.get(AccountManager.KEY_INTENT);
                callbackContext = callbackCtx;
                cordova.startActivityForResult(adfs.this, i, LOGIN_REAUTH);
              }
            } catch (AuthenticatorException e) {
              callbackCtx.sendPluginResult(new PluginResult(PluginResult.Status.ERROR, e.getMessage()));
            } catch (IOException e) {
              callbackCtx.sendPluginResult(new PluginResult(PluginResult.Status.ERROR, e.getMessage()));
            } catch (OperationCanceledException e) {
              callbackCtx.sendPluginResult(new PluginResult(PluginResult.Status.ERROR, e.getMessage()));
            }
          }
        },null);

        //callbackContext.success(authToken);

      } catch (Exception e) {
        //callbackContext.error(e.getMessage());
        callbackCtx.sendPluginResult(new PluginResult(PluginResult.Status.ERROR, e.getMessage()));
      }
    } else {
      //callbackContext.error("Es ist aktuell kein Benutzer eingeloggt.");
      callbackCtx.sendPluginResult(new PluginResult(PluginResult.Status.ERROR, "Es ist aktuell kein Benutzer eingeloggt."));
    }
  }

  private void getAccessToken(CallbackContext callbackContext) {
    getToken(callbackContext, RequestManager.TOKEN_TYPE_ACCESS);
  }

  private void getIDToken(CallbackContext callbackContext) {
    getToken(callbackContext, RequestManager.TOKEN_TYPE_ID);
  }

  private void getRefreshToken(CallbackContext callbackContext) {
    getToken(callbackContext, RequestManager.TOKEN_TYPE_REFRESH);
  }

  @Override
  public void onActivityResult(int requestCode, int resultCode, Intent data) {
    super.onActivityResult(requestCode, resultCode, data);
    if (requestCode == LOGIN_REAUTH)
    {
      if (resultCode == cordova.getActivity().RESULT_OK && data != null) {
        callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.OK, data.getExtras().getString("TOKEN_TYPE_ACCESS")));
      }
      else {
        //callbackContext.error(data.getExtras().containsKey("error") ? data.getStringExtra("error") : "Ein unbekannter Fehler ist aufgetreten.");
        callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.ERROR, data.getExtras().containsKey("error") ? data.getStringExtra("error") : "Ein unbekannter Fehler ist aufgetreten."));
      }
    }
    else
    if (requestCode == LOGIN_RES) {
      if (resultCode == cordova.getActivity().RESULT_OK && data != null) {
        JSONObject result = new JSONObject();
        try {
          result.put("id_token", data.getExtras().getString("TOKEN_TYPE_ID"));
          result.put("access_token", data.getExtras().getString("TOKEN_TYPE_ACCESS"));
          result.put("refresh_token", data.getExtras().getString("TOKEN_TYPE_REFRESH"));
          //this.callbackContext.success(result);
          callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.OK, result));
        } catch (JSONException e) {
          //this.callbackContext.error("Error occurred while creating result object.");
          callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.ERROR, e.getMessage()));
        }
      } else {
        //callbackContext.error(data.getExtras().containsKey("error") ? data.getStringExtra("error") : "Ein unbekannter Fehler ist aufgetreten.");
        callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.ERROR, data.getExtras().containsKey("error") ? data.getStringExtra("error") : "Ein unbekannter Fehler ist aufgetreten."));
      }
    }
  }

  private void checklogin(CallbackContext callbackContext) {
    Account acc = AccountUtils.getCurrentUser(cordova.getActivity());
    if (acc != null) {
      //callbackContext.success(acc.name);
      callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.OK, acc.name));
    } else {
      callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.ERROR, "Kein Benutzer angemeldet"));
     // callbackContext.error("Kein Benutzer angemeldet");
    }
  }

  private void login(CallbackContext callbackContext) {
    this.callbackContext = callbackContext;
    Intent i = authenticator.getLoginIntent();
    cordova.startActivityForResult(this, i, LOGIN_RES);
  }

  private void logout(CallbackContext callbackContext) {
    Account acc = AccountUtils.getCurrentUser(cordova.getActivity());
    if (acc != null) {
      AccountUtils.setAccountData(cordova.getActivity(), acc, RequestManager.ACCOUNT_STATE_KEY, "0");
      authenticator.logout(cordova.getContext());
      //callbackContext.success();
      callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.OK,acc.name));
    } else {
      //callbackContext.error("Kein Benutzer angemeldet");
      callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.ERROR,"Kein Benutzer angemeldet"));
    }
  }

  @Override
  public boolean execute(@NonNull final String action, final JSONArray data, final CallbackContext callbackContext) {

    cordova.getThreadPool().execute(new Runnable() {
      public void run() {

        JSONObject result = new JSONObject();
        try {
          request = data.getJSONObject(0);
          result.put("request", request);
        } catch (Exception e) {
          Log.e(TAG, e.getMessage());
        }

        switch (action) {

          case "logout":
            logout(callbackContext);
            break;

          case "getAccessToken":
            getAccessToken(callbackContext);
            break;

          case "getIDToken":
            getIDToken(callbackContext);
            break;

          case "getRefreshToken":
            getRefreshToken(callbackContext);
            break;

          case "login":
            login(callbackContext);
            break;

          case "checklogin":
            checklogin(callbackContext);
            break;
        }
      }
    });

    return true;
  }


}
