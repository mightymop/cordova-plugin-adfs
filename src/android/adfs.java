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
  private final static String TAG = "ADFS_PLUGIN";

  private final static int LOGOUT_RES = 100;
  private final static int LOGIN_RES = 110;
  private final static int LOGIN_REAUTH = 111;

  private Boolean isInAuthProcess = false;

  private JSONObject request;
  private CallbackContext callbackContext;

  private int currentAction = 0;
  private static int MASK_RELOGIN = 1;
  private static int MASK_ACCESS_TOKEN = 2;
  private static int MASK_ID_TOKEN = 4;
  private static int MASK_REFRESH_TOKEN = 8;

  private void getToken(CallbackContext callbackCtx, String authTokenType) {
    getToken(callbackCtx, Utils.getCurrentUser(cordova.getActivity()), authTokenType);
  }

  private void getToken(CallbackContext callbackCtx, Account acc, String authTokenType) {
	int errcount=0;
    while (isInAuthProcess)
    {
		errcount++;
		try {
			Thread.sleep(200);
		}
		catch (Exception e)
		{
      Log.e(TAG,e.getMessage());
		}

		if (errcount>20)
			break;
    }
    AccountManager accountManager = AccountManager.get(cordova.getActivity());
    if (acc != null) {
      try {
        accountManager.getAuthToken(acc, authTokenType, null, false, new AccountManagerCallback<Bundle>() {
          @Override
          public void run(AccountManagerFuture<Bundle> future) {
            try {
              Bundle result = future.getResult();

              if (result.keySet().contains(AccountManager.KEY_AUTHTOKEN)) {
                callbackCtx.sendPluginResult(new PluginResult(PluginResult.Status.OK, result.getString(AccountManager.KEY_AUTHTOKEN)));
              } else {


                if (result != null && (result.keySet().contains(AccountManager.KEY_ERROR_CODE)||
                  result.keySet().contains(AccountManager.KEY_ERROR_MESSAGE))) {
                  int errorCode = result.getInt(AccountManager.KEY_ERROR_CODE);
                  String errorMessage = result.getString(AccountManager.KEY_ERROR_MESSAGE);
                  callbackCtx.sendPluginResult(new PluginResult(PluginResult.Status.ERROR,  getErrorJson(errorCode,errorMessage)));
                  return;
                }

                currentAction |=MASK_RELOGIN;
                Intent i = (Intent) result.get(AccountManager.KEY_INTENT);
                callbackContext = callbackCtx;
                String resstart = runLogin(i,LOGIN_REAUTH);
                if (resstart!=null)
                {
                  callbackCtx.sendPluginResult(new PluginResult(PluginResult.Status.ERROR,  getErrorJson(resstart)));
                  return;
                }
                else {
                  PluginResult presult = new PluginResult(PluginResult.Status.NO_RESULT);
                  presult.setKeepCallback(true);
                  callbackCtx.sendPluginResult(presult);
                  return;
                }
              }
            } catch (AuthenticatorException e) {
              Log.e(TAG, e.getMessage());
              callbackCtx.sendPluginResult(new PluginResult(PluginResult.Status.ERROR,  getErrorJson(e)));
            } catch (IOException e) {
              Log.e(TAG, e.getMessage());
              callbackCtx.sendPluginResult(new PluginResult(PluginResult.Status.ERROR,  getErrorJson(e)));
            } catch (OperationCanceledException e) {
              Log.e(TAG, e.getMessage());
              callbackCtx.sendPluginResult(new PluginResult(PluginResult.Status.ERROR,  getErrorJson(e)));
            }
          }
        }, null);

    } catch (Exception e) {
        Log.e(TAG, e.getMessage(), e);
        callbackCtx.sendPluginResult(new PluginResult(PluginResult.Status.ERROR, getErrorJson(e)));
      }
    } else {
      callbackCtx.sendPluginResult(new PluginResult(PluginResult.Status.ERROR, getErrorJson("Es ist aktuell kein Benutzer eingeloggt.")));
    }
  }

  private String getErrorJson(int code,String str)
  {
    try {
      JSONObject result = new JSONObject();
      result.put("errortype",String.valueOf(code));
      result.put("description",str!=null?str:"Unknown Error");

      return result.toString();
    } catch (JSONException ex) {
      return str;
    }
  }
  private String getErrorJson(String str)
  {
    try {
      JSONObject result = new JSONObject();
      result.put("errortype","unknown");
      result.put("description",str);

      return result.toString();
    } catch (JSONException ex) {
      return str;
    }
  }
  private String getErrorJson(Exception e)
  {
    try {
      JSONObject result = new JSONObject();
      result.put("errortype",e.getClass().getName());
      result.put("description",e.getMessage());

      return result.toString();
    } catch (JSONException ex) {
      return e.getMessage();
    }
  }

  private void getAccessToken(CallbackContext callbackContext) {
    currentAction = 0;
    currentAction |= MASK_ACCESS_TOKEN;
    getToken(callbackContext, TOKEN_TYPE_ACCESS);
  }

  private void getIDToken(CallbackContext callbackContext) {
    currentAction = 0;
    currentAction |= MASK_ID_TOKEN;
    getToken(callbackContext, TOKEN_TYPE_ID);
  }

  private void getRefreshToken(CallbackContext callbackContext) {
    currentAction = 0;
    currentAction |= MASK_REFRESH_TOKEN;
    getToken(callbackContext, TOKEN_TYPE_REFRESH);
  }

  @Override
  public void onActivityResult(int requestCode, int resultCode, Intent data) {
    super.onActivityResult(requestCode, resultCode, data);
    Log.d(TAG,"onActivityResult");
    if (requestCode == LOGIN_REAUTH) {
      Log.d(TAG,"onActivityResult LOGIN_REAUTH");
      if (resultCode == cordova.getActivity().RESULT_OK && data != null) {
        Log.d(TAG,"onActivityResult LOGIN_REAUTH OK");
        JSONObject result = new JSONObject();
        try {
          if ((currentAction&MASK_RELOGIN)==MASK_RELOGIN)
          {
            if ((currentAction&MASK_ACCESS_TOKEN)==MASK_ACCESS_TOKEN)
            {
              result.put("access_token", data.getExtras().getString("access_token"));
            }
            else
            if ((currentAction&MASK_ID_TOKEN)==MASK_ID_TOKEN)
            {
              result.put("id_token", data.getExtras().getString("id_token"));
            }
            else
            if ((currentAction&MASK_REFRESH_TOKEN)==MASK_REFRESH_TOKEN)
            {
              result.put("refresh_token", data.getExtras().getString("refresh_token"));
            }
          }
          else {
            result.put("id_token", data.getExtras().getString("id_token"));
            result.put("access_token", data.getExtras().getString("access_token"));
            result.put("refresh_token", data.getExtras().getString("refresh_token"));
          }
          callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.OK, result));
        } catch (JSONException e) {
          callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.ERROR, e.getMessage()));
        }
      } else {
        Log.d(TAG,"onActivityResult LOGIN_REAUTH ERROR");
        Log.e(TAG, "RESULTCODE=" + String.valueOf(resultCode));
        callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.ERROR, data != null && data.getExtras() != null && data.getExtras().containsKey("error") ? data.getStringExtra("error") : "Fehler beim Login (1)."));
      }
      isInAuthProcess=false;
    } else if (requestCode == LOGOUT_RES) {
      Log.d(TAG,"onActivityResult LOGOUT_RES");
      cordova.getActivity().finish();
      return;
    } else if (requestCode == LOGIN_RES) {
      Log.d(TAG,"onActivityResult LOGIN_RES");
      if (resultCode == cordova.getActivity().RESULT_OK && data != null) {
        Log.d(TAG,"onActivityResult LOGIN_RES OK");
        JSONObject result = new JSONObject();
        try {
          if ((currentAction&MASK_RELOGIN)==MASK_RELOGIN)
          {
            if ((currentAction&MASK_ACCESS_TOKEN)==MASK_ACCESS_TOKEN)
            {
              result.put("access_token", data.getExtras().getString("access_token"));
            }
            else
            if ((currentAction&MASK_ID_TOKEN)==MASK_ID_TOKEN)
            {
              result.put("id_token", data.getExtras().getString("id_token"));
            }
            else
            if ((currentAction&MASK_REFRESH_TOKEN)==MASK_REFRESH_TOKEN)
            {
              result.put("refresh_token", data.getExtras().getString("refresh_token"));
            }
          }
          else {
            result.put("id_token", data.getExtras().getString("id_token"));
            result.put("access_token", data.getExtras().getString("access_token"));
            result.put("refresh_token", data.getExtras().getString("refresh_token"));
          }
          callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.OK, result));
        } catch (JSONException e) {
          callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.ERROR, e.getMessage()));
        }
      } else {
        Log.d(TAG,"onActivityResult LOGIN_RES ERROR");
        Log.e(TAG, "RESULTCODE 1=" + String.valueOf(resultCode));
        callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.ERROR, data != null && data.getExtras() != null && data.hasExtra("error") ? data.getStringExtra("error") : "Fehler beim Login (2)."));

      }

      isInAuthProcess=false;
    }

    Log.d(TAG,"onActivityResult ???");
  }

  private void getRefreshTokenExpTime(CallbackContext callbackCtx) {
    Account acc = Utils.getCurrentUser(cordova.getActivity());
    if (acc != null) {

      long refresh_token_expires_in = Utils.getNumberFromTokenData(cordova.getActivity(), acc, "refresh_token_expires_in");//geändert in timestamp wann refresh_token abläuft
      if (refresh_token_expires_in!=-1&&System.currentTimeMillis()<refresh_token_expires_in)
      {
        callbackCtx.sendPluginResult(new PluginResult(PluginResult.Status.OK, refresh_token_expires_in));
      }
      else
      {
        AccountManager accountManager = AccountManager.get(cordova.getActivity());
        accountManager.getAuthToken(acc, TOKEN_TYPE_ID, null, true, new AccountManagerCallback<Bundle>() {
          @Override
          public void run(AccountManagerFuture<Bundle> future) {
            try {
              Bundle result = future.getResult();

              if (result.keySet().contains(AccountManager.KEY_AUTHTOKEN)) {

                  String id_token = result.getString(AccountManager.KEY_AUTHTOKEN);

                  long millis = Utils.getExpFromIDToken(id_token);
                  callbackCtx.sendPluginResult(new PluginResult(PluginResult.Status.OK, millis));

              } else {
                callbackContext = callbackCtx;
                String resstart =  runLogin((Intent)result.get(AccountManager.KEY_INTENT),LOGIN_REAUTH);
                if (resstart!=null)
                {
                  callbackCtx.sendPluginResult(new PluginResult(PluginResult.Status.ERROR, resstart));
                  return;
                }
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
      }

    } else {
      callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.ERROR, "Kein Benutzer angemeldet"));
    }
  }

  private String runLogin(Intent i, int requestCode)
  {
    //i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    //cordova.setActivityResultCallback(adfs.this);
    isInAuthProcess=true;
    try {
      cordova.startActivityForResult(this,i, requestCode);
      return null;
    }
    catch (Exception e)
    {
      return e.getMessage();
    }
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

      Intent i = Utils.getLoginIntent();
      final String resstart =  runLogin(i,LOGIN_RES);
      if (resstart!=null)
      {
        cordova.getActivity().runOnUiThread(new Runnable() {
          @Override
          public void run() {
            Toast.makeText(cordova.getActivity(), resstart, Toast.LENGTH_LONG).show();
          }
        });
        callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.ERROR, resstart));
      }
  }

  private void logout(CallbackContext callbackContext) {
    Account acc = Utils.getCurrentUser(cordova.getActivity());
    if (acc != null) {
      Intent i = Utils.getLogoutIntent();

      String resstart =  runLogin(i,LOGOUT_RES);
      if (resstart!=null)
      {
        callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.ERROR, resstart));
        return;
      }

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
