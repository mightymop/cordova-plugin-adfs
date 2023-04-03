package de.mopsdom.adfs;

import static de.mopsdom.adfs.request.RequestManager.REFRESH_TOKEN_EXP;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.AccountManagerCallback;
import android.accounts.AccountManagerFuture;
import android.accounts.AuthenticatorException;
import android.accounts.OperationCanceledException;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

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
    getToken(callbackCtx,AccountUtils.getCurrentUser(cordova.getActivity()),authTokenType);
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
              Log.e(TAG,e.getMessage());
              callbackCtx.sendPluginResult(new PluginResult(PluginResult.Status.ERROR, e.getMessage()));
            } catch (IOException e) {
              Log.e(TAG,e.getMessage());
              callbackCtx.sendPluginResult(new PluginResult(PluginResult.Status.ERROR, e.getMessage()));
            } catch (OperationCanceledException e) {
              Log.e(TAG,e.getMessage());
              callbackCtx.sendPluginResult(new PluginResult(PluginResult.Status.ERROR, e.getMessage()));
            }
          }
        },null);

        //callbackContext.success(authToken);

      } catch (Exception e) {
        Log.e(TAG,e.getMessage(),e);
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
        Log.e(TAG,"RESULTCODE="+String.valueOf(resultCode));
        Log.e(TAG,data!=null?"DATA!=NULL":"DATA=NULL");
        Log.e(TAG,data!=null&&data.getExtras()!=null?data.getExtras().getString("TOKEN_TYPE_ACCESS"):"DATA_EXTRAS=NULL");
        //callbackContext.error(data.getExtras().containsKey("error") ? data.getStringExtra("error") : "Ein unbekannter Fehler ist aufgetreten.");
        callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.ERROR, data!=null&&data.getExtras()!=null&&data.getExtras().containsKey("error") ? data.getStringExtra("error") : "Ein unbekannter Fehler ist aufgetreten."));
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
        Log.e(TAG,"RESULTCODE 1="+String.valueOf(resultCode));
        Log.e(TAG,data!=null?"DATA!=NULL":"DATA=NULL");
        Log.e(TAG,data!=null&&data.hasExtra("TOKEN_TYPE_ACCESS")?data.getExtras().getString("TOKEN_TYPE_ACCESS"):"TOKEN_TYPE_ACCESS=NULL");
        Log.e(TAG,data!=null&&data.hasExtra("error")?data.getExtras().getString("error"):"ERROR=NULL");
        //callbackContext.error(data.getExtras().containsKey("error") ? data.getStringExtra("error") : "Ein unbekannter Fehler ist aufgetreten.");
        callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.ERROR, data!=null&&data.getExtras()!=null && data.hasExtra("error") ? data.getStringExtra("error") : "Ein unbekannter Fehler ist aufgetreten."));
        cordova.getActivity().runOnUiThread(new Runnable() {
          @Override
          public void run() {
            if (data.hasExtra("error")&&data.getStringExtra("error").contains("\r\n"))
            {
              for (int n=0;n<data.getStringExtra("error").split("\r\n").length;n++){
                Toast.makeText(cordova.getActivity(), data.getStringExtra("error").split("\r\n")[n], Toast.LENGTH_LONG).show();
              }
            }
            else {
              Toast.makeText(cordova.getActivity(), (data.hasExtra("error") ? data.getStringExtra("error") : "Ein unbekannter Fehler ist aufgetreten."), Toast.LENGTH_LONG).show();
            }
          }
        });
        System.exit(1);
      }
    }
  }

  private void getRefreshTokenExpTime(CallbackContext callbackContext) {
    Account acc = AccountUtils.getCurrentUser(cordova.getActivity());
    if (acc != null) {
      AccountManager accountManager = AccountManager.get(cordova.getActivity());
      String strexp = accountManager.getUserData(acc, REFRESH_TOKEN_EXP);
      long millis = Long.parseLong(strexp);
      callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.OK, millis));
    } else {
      callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.ERROR, "Kein Benutzer angemeldet"));
    }
  }
  private void checklogin(CallbackContext callbackContext) {
    Account acc = AccountUtils.getCurrentUser(cordova.getActivity());
    if (acc != null) {
      //callbackContext.success(acc.name);
      getIDToken(callbackContext);
      //callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.OK, acc.name));
    } else {
      callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.ERROR, "Kein Benutzer angemeldet"));
     // callbackContext.error("Kein Benutzer angemeldet");
    }
  }

  private void login(CallbackContext callbackContext) {
    this.callbackContext = callbackContext;

    try {
      Intent i = authenticator.getLoginIntent();
      cordova.startActivityForResult(this, i, LOGIN_RES);
    }
    catch (Exception e)
    {
      Log.e(TAG,e.getMessage());
    }
  }



  @RequiresApi(api = Build.VERSION_CODES.O)
  private void logout(CallbackContext callbackContext) {
    Account acc = AccountUtils.getCurrentUser(cordova.getActivity());
    if (acc != null) {
      AccountUtils.setAccountData(cordova.getActivity(), acc, RequestManager.ACCOUNT_STATE_KEY, "0");
      String strconfig = Utils.getSharedPref(cordova.getActivity(), "configuration");
      if (strconfig != null) {
        try {
          JSONObject configjson = new JSONObject(strconfig);
          Uri uri = Uri.parse(configjson.getString("end_session_endpoint"));
          Intent i = new Intent(Intent.ACTION_VIEW, uri);
          i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
          cordova.getActivity().startActivity(i);
        } catch (Exception e) {
          Log.e(TAG,e.getMessage());
          authenticator.logout(cordova.getContext());
        }
      }
      else
      {
        authenticator.logout(cordova.getContext());
      }

      callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.OK,acc.name));
    } else {
      callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.ERROR,"Kein Benutzer angemeldet"));
    }
  }

  @Override
  public boolean execute(@NonNull final String action, final JSONArray data, final CallbackContext callbackContext) {

    cordova.getThreadPool().execute(new Runnable() {
      public void run() {

        JSONObject result = new JSONObject();
        try {
          if (data.length()>0) {
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
