package de.mopsdom.adfs.cordova;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.AccountManagerCallback;
import android.accounts.AccountManagerFuture;
import android.accounts.AuthenticatorException;
import android.accounts.OperationCanceledException;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;

import de.mopsdom.adfs.cordova.utils.Utils;

public class adfs extends CordovaPlugin {

  public static final String TOKEN_TYPE_ID = "TOKEN_TYPE_ID";
  public static final String TOKEN_TYPE_ACCESS = "TOKEN_TYPE_ACCESS";
  public static final String TOKEN_TYPE_REFRESH = "TOKEN_TYPE_REFRESH";
  public static final String REFRESH_TOKEN_EXP = "refresh_token_expires_in";
  private final static String TAG = "ADFS_PLUGIN";

  private final static int LOGOUT_RES = 100;
  private final static int LOGIN_RES = 110;
  private final static int LOGIN_REAUTH = 111;

  private JSONObject request;
  private CallbackContext callbackContext;

  private void getToken(CallbackContext callbackCtx, String authTokenType) {
    getToken(callbackCtx, Utils.getCurrentUser(cordova.getActivity()), authTokenType);
  }

  private void getToken(CallbackContext callbackCtx, Account acc, String authTokenType) {
    AccountManager accountManager = AccountManager.get(cordova.getActivity());
    if (acc != null) {
      try {
        // Bundle options = new Bundle();

      /* String token = accountManager.blockingGetAuthToken(acc,authTokenType,true);
       if (token==null)
       {
         login(callbackContext);
       }
       else {
         callbackCtx.sendPluginResult(new PluginResult(PluginResult.Status.OK, token));
       }
*/

        accountManager.getAuthToken(acc, authTokenType, null, true, new AccountManagerCallback<Bundle>() {
          @Override
          public void run(AccountManagerFuture<Bundle> future) {
            try {
              Bundle result = future.getResult();

              if (result.keySet().contains(AccountManager.KEY_AUTHTOKEN)) {
                callbackCtx.sendPluginResult(new PluginResult(PluginResult.Status.OK, result.getString(AccountManager.KEY_AUTHTOKEN)));
              } else {
                Intent i = (Intent) result.get(AccountManager.KEY_INTENT);
                callbackContext = callbackCtx;
                runLogin(i,LOGIN_REAUTH);
              }
            } catch (AuthenticatorException e) {
              Log.e(TAG, e.getMessage());
              callbackCtx.sendPluginResult(new PluginResult(PluginResult.Status.ERROR, e.getMessage()));
            } catch (IOException e) {
              Log.e(TAG, e.getMessage());
              callbackCtx.sendPluginResult(new PluginResult(PluginResult.Status.ERROR, e.getMessage()));
            } catch (OperationCanceledException e) {
              Log.e(TAG, e.getMessage());
              callbackCtx.sendPluginResult(new PluginResult(PluginResult.Status.ERROR, e.getMessage()));
            }
          }
        }, null);

        //callbackContext.success(authToken);

      } catch (Exception e) {
        Log.e(TAG, e.getMessage(), e);
        callbackCtx.sendPluginResult(new PluginResult(PluginResult.Status.ERROR, e.getMessage()));
      }
    } else {
      callbackCtx.sendPluginResult(new PluginResult(PluginResult.Status.ERROR, "Es ist aktuell kein Benutzer eingeloggt."));
    }
  }

  private void getAccessToken(CallbackContext callbackContext) {
    getToken(callbackContext, TOKEN_TYPE_ACCESS);
  }

  private void getIDToken(CallbackContext callbackContext) {
    getToken(callbackContext, TOKEN_TYPE_ID);
  }

  private void getRefreshToken(CallbackContext callbackContext) {
    getToken(callbackContext, TOKEN_TYPE_REFRESH);
  }

  @Override
  public void onActivityResult(int requestCode, int resultCode, Intent data) {
    super.onActivityResult(requestCode, resultCode, data);
    Log.e(TAG,"onActivityResult");
    if (requestCode == LOGIN_REAUTH) {
      Log.e(TAG,"onActivityResult LOGIN_REAUTH");
      if (resultCode == cordova.getActivity().RESULT_OK && data != null) {
        Log.e(TAG,"onActivityResult LOGIN_REAUTH OK");
        callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.OK, data.getExtras().getString("TOKEN_TYPE_ACCESS")));
      } else {
        Log.e(TAG,"onActivityResult LOGIN_REAUTH ERROR");
        Log.e(TAG, "RESULTCODE=" + String.valueOf(resultCode));
        Log.e(TAG, data != null ? "DATA!=NULL" : "DATA=NULL");
        Log.e(TAG, data != null && data.getExtras() != null ? data.getExtras().getString("TOKEN_TYPE_ACCESS") : "DATA_EXTRAS=NULL");
        callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.ERROR, data != null && data.getExtras() != null && data.getExtras().containsKey("error") ? data.getStringExtra("error") : "Ein unbekannter Fehler ist aufgetreten."));
      }
    } else if (requestCode == LOGOUT_RES) {
      Log.e(TAG,"onActivityResult LOGOUT_RES");
      cordova.getActivity().finish();
      return;
    } else if (requestCode == LOGIN_RES) {
      Log.e(TAG,"onActivityResult LOGIN_RES");
      if (resultCode == cordova.getActivity().RESULT_OK && data != null) {
        Log.e(TAG,"onActivityResult LOGIN_RES OK");
        JSONObject result = new JSONObject();
        try {
          result.put("id_token", data.getExtras().getString("TOKEN_TYPE_ID"));
          result.put("access_token", data.getExtras().getString("TOKEN_TYPE_ACCESS"));
          result.put("refresh_token", data.getExtras().getString("TOKEN_TYPE_REFRESH"));
          callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.OK, result));
        } catch (JSONException e) {
          callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.ERROR, e.getMessage()));
        }
      } else {
        Log.e(TAG,"onActivityResult LOGIN_RES ERROR");
        Log.e(TAG, "RESULTCODE 1=" + String.valueOf(resultCode));
        Log.e(TAG, data != null ? "DATA!=NULL" : "DATA=NULL");
        Log.e(TAG, data != null && data.hasExtra("TOKEN_TYPE_ACCESS") ? data.getExtras().getString("TOKEN_TYPE_ACCESS") : "TOKEN_TYPE_ACCESS=NULL");
        Log.e(TAG, data != null && data.hasExtra("error") ? data.getExtras().getString("error") : "ERROR=NULL");
        callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.ERROR, data != null && data.getExtras() != null && data.hasExtra("error") ? data.getStringExtra("error") : "Ein unbekannter Fehler ist aufgetreten."));
        /*cordova.getActivity().runOnUiThread(new Runnable() {
          @Override
          public void run() {
            if (data != null && data.hasExtra("error") && data.getStringExtra("error").contains("\r\n")) {
              for (int n = 0; n < data.getStringExtra("error").split("\r\n").length; n++) {
                Toast.makeText(cordova.getActivity(), data.getStringExtra("error").split("\r\n")[n], Toast.LENGTH_LONG).show();
              }
            } else {
              Toast.makeText(cordova.getActivity(), (data != null && data.hasExtra("error") ? data.getStringExtra("error") : "Ein unbekannter Fehler ist aufgetreten."), Toast.LENGTH_LONG).show();
            }
          }
        });*/

        Log.e(TAG,"onActivityResult LOGIN_RES EXIT APP");
       // System.exit(1);
      }
    }

    Log.e(TAG,"onActivityResult ???");
  }

  private void getRefreshTokenExpTime(CallbackContext callbackCtx) {
    Account acc = Utils.getCurrentUser(cordova.getActivity());
    if (acc != null) {
      AccountManager accountManager = AccountManager.get(cordova.getActivity());
      accountManager.getAuthToken(acc, TOKEN_TYPE_ID, null, true, new AccountManagerCallback<Bundle>() {
        @Override
        public void run(AccountManagerFuture<Bundle> future) {
          try {
            Bundle result = future.getResult();

            if (result.keySet().contains(AccountManager.KEY_AUTHTOKEN)) {
              callbackCtx.sendPluginResult(new PluginResult(PluginResult.Status.OK, result.getString(AccountManager.KEY_AUTHTOKEN)));
              String id_token = result.getString(AccountManager.KEY_AUTHTOKEN);

              long millis = Utils.getExpFromIDToken(id_token);
              callbackCtx.sendPluginResult(new PluginResult(PluginResult.Status.OK, millis));
            } else {
              callbackContext = callbackCtx;
              runLogin((Intent)result.get(AccountManager.KEY_INTENT),LOGIN_REAUTH);
            }
          } catch (AuthenticatorException e) {
            Log.e(TAG, e.getMessage());
            callbackCtx.sendPluginResult(new PluginResult(PluginResult.Status.ERROR, e.getMessage()));
          } catch (IOException e) {
            Log.e(TAG, e.getMessage());
            callbackCtx.sendPluginResult(new PluginResult(PluginResult.Status.ERROR, e.getMessage()));
          } catch (OperationCanceledException e) {
            Log.e(TAG, e.getMessage());
            callbackCtx.sendPluginResult(new PluginResult(PluginResult.Status.ERROR, e.getMessage()));
          }
        }
      }, null);

    } else {
      callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.ERROR, "Kein Benutzer angemeldet"));
    }
  }

  private void runLogin(Intent i, int requestCode)
  {
    //i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    //cordova.setActivityResultCallback(adfs.this);
    cordova.startActivityForResult(adfs.this, i, requestCode);
  }

  private void checklogin(CallbackContext callbackContext) {
    Account acc = Utils.getCurrentUser(cordova.getActivity());
    if (acc != null) {
      getIDToken(callbackContext);
    } else {
      callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.ERROR, "Kein Benutzer angemeldet"));
    }
  }

  private void login(CallbackContext callbackContext) {
    this.callbackContext = callbackContext;

    try {
      Intent i = Utils.getLoginIntent();
      runLogin(i,LOGIN_RES);
    } catch (Exception e) {
      Log.e(TAG, e.getMessage());
      cordova.getActivity().runOnUiThread(new Runnable() {
        @Override
        public void run() {
          Toast.makeText(cordova.getActivity(), e.getMessage(), Toast.LENGTH_LONG).show();
          System.exit(1);
        }
      });
    }
  }

  private void logout(CallbackContext callbackContext) {
    Account acc = Utils.getCurrentUser(cordova.getActivity());
    if (acc != null) {
      Intent i = Utils.getLogoutIntent();
      runLogin(i,LOGOUT_RES);

      callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.OK, acc.name));
    } else {
      callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.ERROR, "Kein Benutzer angemeldet"));
    }
  }

  @Override
  public boolean execute(@NonNull final String action, final JSONArray data, final CallbackContext callbackContext) {

    cordova.getThreadPool().execute(new Runnable() {
      public void run() {

        JSONObject result = new JSONObject();
        try {
          if (data.length() > 0) {
            request = data.getJSONObject(0);
            result.put("request", request);
          }
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

          case "getRefreshTokenExpTime":
            getRefreshTokenExpTime(callbackContext);
            break;
        }
      }
    });

    return true;
  }


}
