package de.mopsdom.adfs;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.Intent;
import android.util.Log;

import androidx.annotation.NonNull;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CordovaWebView;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import de.mopsdom.adfs.request.RequestManager;
import de.mopsdom.adfs.utils.AccountUtils;
import de.mopsdom.adfs.utils.Utils;

public class adfs extends CordovaPlugin {

  private final static String TAG = "ADFS_PLUGIN";

  private final static int LOGIN_RES = 110;

  private JSONObject request;

  private CallbackContext callbackContext;
  private ADFSAuthenticator authenticator;

  @Override
  public void initialize(CordovaInterface cordova, CordovaWebView webView) {
    super.initialize(cordova, webView);
    authenticator = new ADFSAuthenticator(cordova.getContext());
  }

  private void getToken(CallbackContext callbackContext, String authTokenType) {
    AccountManager accountManager = AccountManager.get(cordova.getActivity());
    Account acc = AccountUtils.getCurrentUser(cordova.getActivity());
    if (acc != null) {
      try {
        String authToken = accountManager.blockingGetAuthToken(acc, authTokenType, true);
        callbackContext.success(authToken);
      } catch (Exception e) {
        callbackContext.error(e.getMessage());
      }
    } else {
      callbackContext.error("Es ist aktuell kein Benutzer eingeloggt.");
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
    if (requestCode == LOGIN_RES) {
      if (resultCode == cordova.getActivity().RESULT_OK && data != null) {
        JSONObject result = new JSONObject();
        try {
          result.put("id_token", data.getExtras().getString("id_token"));
          result.put("access_token", data.getExtras().getString("access_token"));
          result.put("refresh_token", data.getExtras().getString("refresh_token"));
          this.callbackContext.success(result);
        } catch (JSONException e) {
          this.callbackContext.error("Error occurred while creating result object.");
        }
      } else {
        this.callbackContext.error(data.getExtras().containsKey("error") ? data.getStringExtra("error") : "Ein unbekannter Fehler ist aufgetreten.");
      }
    }
  }

  private void checklogin(CallbackContext callbackContext) {
    Account acc = AccountUtils.getCurrentUser(cordova.getActivity());
    if (acc != null) {
      callbackContext.success(acc.name);
    } else {
      callbackContext.error("Kein Benutzer angemeldet");
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
      callbackContext.success();
    } else {
      callbackContext.error("Kein Benutzer angemeldet");
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
