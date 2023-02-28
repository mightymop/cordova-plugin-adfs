package de.mopsdom.adfs;

import static android.accounts.AccountManager.KEY_ACCOUNT_NAME;
import static androidx.browser.customtabs.CustomTabsIntent.EXTRA_ENABLE_INSTANT_APPS;
import static androidx.browser.customtabs.CustomTabsIntent.EXTRA_SESSION;
import static de.mopsdom.adfs.request.RequestManager.KEY_IS_NEW_ACCOUNT;
import static de.mopsdom.adfs.request.RequestManager.REFRESH_TOKEN_EXP;
import static de.mopsdom.adfs.request.RequestManager.TOKEN_TYPE_ACCESS;
import static de.mopsdom.adfs.request.RequestManager.TOKEN_TYPE_ID;
import static de.mopsdom.adfs.request.RequestManager.TOKEN_TYPE_REFRESH;
import static de.mopsdom.adfs.utils.Utils.getDataFromIntent;

import android.accounts.Account;
import android.accounts.AccountAuthenticatorActivity;
import android.accounts.AccountManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.browser.customtabs.CustomTabsCallback;
import androidx.browser.customtabs.CustomTabsClient;
import androidx.browser.customtabs.CustomTabsIntent;
import androidx.browser.customtabs.CustomTabsServiceConnection;
import androidx.browser.customtabs.CustomTabsSession;
import androidx.core.content.ContextCompat;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;

import de.mopsdom.adfs.http.HttpException;
import de.mopsdom.adfs.request.RequestManager;
import de.mopsdom.adfs.utils.AccountUtils;
import de.mopsdom.adfs.utils.Utils;

public class ADFSAuthenticatorActivity extends AccountAuthenticatorActivity   {

  private final String TAG = getClass().getSimpleName();

  private String tmp_client_id = null;

  private AccountManager accountManager;
  private RequestManager requestManager;

  private Account account;
  private boolean isNewAccount;

  protected String secureState;

  private ArrayList<String> errList = new ArrayList<>();

  private CustomTabsSession session;

  private CustomTabsIntent customTabsIntent;
  private CustomTabsServiceConnection connection;

  private CustomTabsClient customTabsClient;
  private CustomTabsCallback callback;



  @Override
  public void onNewIntent(Intent intent) {
    super.onNewIntent(intent);

    if (!intent.getAction().equalsIgnoreCase(Intent.ACTION_VIEW)) {
      return;
    }

    if (intent.getCategories().contains(Intent.CATEGORY_BROWSABLE)) {
      Uri redirectUri = intent.getData();

      Map<String,String> results = getDataFromIntent(redirectUri);

      // The URL will contain a `code` parameter when the user has been authenticated
      if (results.containsKey("state")) {
        String state = results.get("state");
        if (results.containsKey("code")) {
          String authToken = results.get("code");
          // Request the ID token
          CodeFlowTask task = new CodeFlowTask();
          task.execute(authToken, state);
        } else {
          String err = String.format(
            "redirectUriString '%1$s' doesn't contain code param; can't extract authCode",
            intent.getDataString());
            Log.e(TAG, err);

            errList.add(err);
        }
      }
    }
  }

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    Bundle extras = getIntent().getExtras();

    // Are we supposed to create a new account or renew the authorisation of an old one?
    isNewAccount = extras.getBoolean(KEY_IS_NEW_ACCOUNT, false);

    // In case we're renewing authorisation, we also got an Account object that we're supposed
    // to work with.
    String accountName = extras.getString(KEY_ACCOUNT_NAME);

    accountManager = AccountManager.get(this);
    requestManager = new RequestManager(this);

    if (accountName != null) {
      account = AccountUtils.getAccountByName(this, accountName);
    }

    initCodeFlow();
  }

  public void initCodeFlow() {

    secureState = UUID.randomUUID().toString();

    tmp_client_id = getIntent().hasExtra("client_id") ? getIntent().getStringExtra("client_id") : null;
    if (tmp_client_id==null)
    {
      tmp_client_id=Utils.getVal(this,"client_id");
    }
    String scope = getIntent().hasExtra("scope") ? getIntent().getStringExtra("scope") : null;
    if (scope==null)
    {
      scope=Utils.getVal(this,"scope");
    }
    String nonce = getIntent().hasExtra("nonce") ? getIntent().getStringExtra("nonce") : "random-nonce";
    String response_type = getIntent().hasExtra("response_type") ? getIntent().getStringExtra("response_type") : "code id_token";

    if (tmp_client_id != null && scope != null && nonce != null && response_type != null) {
      try {
        String redirecturl = RequestManager.REDIRECT_URI+"/"+getPackageName();
        AuthorizeFlowTask task = new AuthorizeFlowTask(this, tmp_client_id, scope, nonce, response_type, redirecturl);
        task.execute();
      } catch (Exception e) {
        String err = String.join(" ",errList)+" "+e.getMessage();
        Log.e(TAG,err);

        Toast.makeText(this,e.getMessage(),Toast.LENGTH_LONG).show();
        setErrorResult(err.trim());
        finish();
      }
    } else {
      String err = String.join(" ",errList)+" client_id nicht angegeben!";
      Log.e(TAG,err);

      Toast.makeText(this,"client_id nicht angegeben!",Toast.LENGTH_LONG).show();
      setErrorResult(err.trim());
      finish();
    }
  }

  private void setErrorResult(String error)
  {
    Intent i = new Intent();
    i.putExtra("error",error);

    setResult(RESULT_CANCELED,i);
  }

  private class CodeFlowTask extends BaseFlowTask {
    @Override
    protected Boolean doInBackground(String... args) {
      String authCode = args[0];
      String returnedState = args[1];
      boolean didStoreTokens = false;

      if (secureState.equalsIgnoreCase(returnedState)) {
        Log.i(TAG, "Requesting access_token with AuthCode : " + authCode);
        try {

          String redirecturl = RequestManager.REDIRECT_URI+"/"+getPackageName();

          JSONObject response = requestManager.access_token(
            authCode,
            tmp_client_id, redirecturl,
            "authorization_code");

          didStoreTokens = createOrUpdateAccount(response);
        } catch (HttpException e) {
          String err = "Could not get response from the token endpoint: " + e.getMessage() + " (HTTP: " + String.valueOf(e.code) + " " + e.message + " (" + e.details + ")";
          Log.e(TAG, err);
          errList.add(err);
        } catch (JSONException ex) {
          String err = "Error while parsing JSON: " + ex.getMessage();
          Log.e(TAG, err);
          errList.add(err);
        }
      } else {
        String err = "Local and returned states don't match";
        Log.e(TAG, err);
        errList.add(err);
      }
      return didStoreTokens;
    }
  }

  private class AuthorizeFlowTask extends AsyncTask<String, Void, Boolean> {

    private JSONObject configuration;
    private String client_id; //from adfs admin
    private String scope; //offline_access email allatclaims profile openid
    private String nonce; //"random-nonce"
    private String response_type; //code id_token token
    private String redirect_uri;

    private Context context;

    public AuthorizeFlowTask(Context c,
                             String client_id,
                             String scope,
                             String nonce,
                             String response_type,
                             String redirect_uri) {
      this.context = c;
      this.client_id = client_id;
      this.scope = scope;
      this.nonce = nonce;
      this.response_type = response_type;
      this.redirect_uri = redirect_uri;
    }

    @Override
    protected Boolean doInBackground(String... args) {

      try {
        String strconfig = Utils.getSharedPref(context, "configuration");

        if (strconfig == null) {
          configuration = requestManager.load_config();
          if (configuration != null) {
            Utils.setSharedPref(context, "configuration", configuration.toString());
          } else {
            throw new Exception("Laden der Konfiguration nicht möglich!");
          }
        } else {
          configuration = new JSONObject(strconfig);
        }

        return true;
      } catch (Exception e) {
        errList.add(e.getMessage());
        return false;
      }
    }

    @Override
    protected void onPostExecute(Boolean wasSuccess) {
      try {
        String authorization_endpoint = configuration.has("authorization_endpoint") ? configuration.getString("authorization_endpoint") : null;
        if (authorization_endpoint == null) {
          String err = "Keinen Authorization Endpunkt in der oidc-configuration gefunden.";
          errList.add(err);
          return;
        }

        if (client_id == null) {
          String err = "Es wurde keine client_id angegeben.";
          errList.add(err);
          return;
        }

        if (scope == null) {
          String err = "Es wurde kein scope angegeben.";
          errList.add(err);
          return;
        } else {
          if (configuration.has("scopes_supported")) {
            JSONArray scopes_supported = configuration.getJSONArray("scopes_supported");

            String[] scopes = scope.split(" ");
            String newscopestring = "";
            for (int n = 0; n < scopes.length; n++) {
              for (int m = 0; m < scopes_supported.length(); m++) {
                if (scopes_supported.getString(m).equalsIgnoreCase(scopes[n])) {
                  newscopestring += " " + scopes[n];
                  break;
                }
              }
            }

            if (newscopestring.trim().length() == 0) {
              String err = "Scope wird nicht unterstützt.";
              errList.add(err);
              return;
            }

            scope = newscopestring;
          }
        }

        if (response_type == null) {
          String err = "Es wurde kein response_type angegeben.";
          errList.add(err);
          return;
        } else {
          if (configuration.has("response_types_supported")) {
            JSONArray response_types_supported = configuration.getJSONArray("response_types_supported");

            String[] test = response_type.split(" ");
            String res_typ = null;
            for (int n = 0; n < response_types_supported.length(); n++) {
              boolean allin = true;
              for (int m = 0; m < test.length; m++) {
                if (!response_types_supported.getString(n).contains(test[m])) {
                  allin = false;
                  break;
                }
              }
              if (allin) {
                res_typ = response_type;
                break;
              }
            }

            if (res_typ == null) {
              String err = "Response_type wird nicht unterstützt.";
              errList.add(err);
              return;
            }
          }
        }


        String url = authorization_endpoint + "?";
        url += "nonce=" + nonce;
        url += "&response_type=" + response_type;
        url += "&scope=" + scope;
        url += "&client_id=" + client_id;
        url += "&state=" + secureState;
        url += "&redirect_uri=" + redirect_uri;// redirect_uri;

        CustomTabsIntent.Builder builder = new CustomTabsIntent.Builder();

        customTabsIntent = builder.build();
        customTabsIntent.intent.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY);
        customTabsIntent.intent.addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);

        final Uri uri = Uri.parse(url);

        connection = new CustomTabsServiceConnection() {
          @Override
          public void onCustomTabsServiceConnected(ComponentName componentName, CustomTabsClient cTabsClient) {
            customTabsClient = cTabsClient;

            callback = new CustomTabsCallback() {
              @Override
              public void onNavigationEvent(int navigationEvent, Bundle extras) {
                Log.e(TAG,"=================onNavigationEvent==================");
                if (navigationEvent == TAB_HIDDEN) {
                  // The Chrome tab has been closed
                  Log.e(TAG,"=================onNavigationEvent==================TAB_HIDDEN");
                } else if (navigationEvent == NAVIGATION_FAILED) {
                  Log.e(TAG,"=================onNavigationEvent==================NAVIGATION_FAILED");
                  // The navigation failed
                  setErrorResult("Navigation failed: " + extras.getString("error_message"));
                  finish();
                }
              }
            };

            customTabsClient.warmup(0L);

            session = customTabsClient.newSession(callback);

            session.mayLaunchUrl(uri, null, null);

            customTabsIntent.intent.setData(uri);
            customTabsIntent.intent.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY | Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_MULTIPLE_TASK);

            customTabsIntent.launchUrl(context, uri);

          }

          @Override
          public void onServiceDisconnected(ComponentName name) {
            customTabsClient = null;
          }
        };

        CustomTabsClient.bindCustomTabsService(context, "com.android.chrome", connection);
      //  customTabsIntent.launchUrl(context, Uri.parse(url));
      } catch (Exception e) {
        String err = e.getMessage();
        errList.add(err);
        return;
      }
    }
  }


  @Override
  public void onDestroy()
  {
    super.onDestroy();
    if (connection != null) {
      unbindService(connection);
    }
  }

  private class LoadKeysTask extends AsyncTask<String, Void, Boolean> {

    private Context context;
    private JSONObject keys;

    private Account acc;

    public LoadKeysTask(Context c, Account a){
      context=c;
      acc=a;
    }

    @Override
    protected Boolean doInBackground(String... args) {

      try {
         keys = requestManager.loadKeys();
         return true;
      }
      catch (Exception e)
      {
        Log.e(TAG, e.getMessage());
        keys=null;
        return false;
      }
    }

    @Override
    protected void onPostExecute(Boolean wasSuccess) {
      accountManager.setUserData(acc,RequestManager.PUBLIC_TOKEN_KEYS,keys.toString());
    }
  }


  private abstract class BaseFlowTask extends AsyncTask<String, Void, Boolean> {
    @Override
    protected void onPostExecute(Boolean wasSuccess) {
      if (wasSuccess) {
        if (errList.size()>0)
        {
          String warn = ("Warnung: "+String.join(" ",errList)).trim();
          Toast.makeText(ADFSAuthenticatorActivity.this,warn,Toast.LENGTH_LONG).show();
        }
      } else {
        String err = ("Fehler: "+String.join(" ",errList)).trim();
        Toast.makeText(ADFSAuthenticatorActivity.this,err,Toast.LENGTH_LONG).show();
        setErrorResult(err);
      }

      finish();
    }

    protected boolean createOrUpdateAccount(JSONObject response) {
      if (isNewAccount) {
        return createAccount(response);
      } else {
        return saveTokens(response);
      }
    }
  }


  private boolean createAccount(JSONObject response) {

    try {
      String accountType = AccountUtils.getAccountTypeKey(this);

      String id_token = response.getString("id_token");

      JSONObject payload = Utils.getPayload(id_token);

      String accountName = payload.getString(Utils.getUserIDClaim(this));

      if ((account=AccountUtils.getAccountByName(this,accountName))==null) {
        account = new Account(accountName, accountType);
        accountManager.addAccountExplicitly(account, null, null);
      }

      return saveTokens(response);
    } catch (Exception e) {
      Log.e(TAG, e.getMessage());
      final String err = e.getMessage();
      errList.add(err);
      return false;
    }
  }

  private boolean saveTokens(JSONObject response) {
    try {
      Account accCurrent = AccountUtils.getCurrentUser(this);
      if (accCurrent!=null)
      {
        AccountUtils.setAccountData(this,accCurrent,RequestManager.ACCOUNT_STATE_KEY,"0");
      }

      AccountUtils.setAccountData(this,account,RequestManager.ACCOUNT_STATE_KEY,"1");

      if (response.has("id_token")) {
        accountManager.setAuthToken(account, TOKEN_TYPE_ID, response.getString("id_token"));
      }
      if (response.has("access_token")) {
        accountManager.setAuthToken(account, TOKEN_TYPE_ACCESS, response.getString("access_token"));
      }
      if (response.has("refresh_token")) {
        accountManager.setAuthToken(account, TOKEN_TYPE_REFRESH, response.getString("refresh_token"));
      }
      if (response.has("refresh_token_expires_in")) {
        long exp = response.getLong("refresh_token_expires_in");
        long time = System.currentTimeMillis()+(exp*1000)-60000; //-60000 as buffer
        accountManager.setUserData(account, REFRESH_TOKEN_EXP, String.valueOf(time));
      }

      String keys = accountManager.getUserData(account, RequestManager.PUBLIC_TOKEN_KEYS);
      if (keys==null||keys.trim().length()==0)
      {
        LoadKeysTask task = new LoadKeysTask(this, account);
        task.execute();
      }

      Intent i = new Intent();
      i.putExtra(TOKEN_TYPE_ID,response.getString("id_token"));
      i.putExtra(TOKEN_TYPE_ACCESS,response.getString("access_token"));
      i.putExtra(TOKEN_TYPE_REFRESH,response.getString("refresh_token"));
      setResult(RESULT_OK,i);
      return true;
    } catch (Exception e) {
      Log.e(TAG, e.getMessage());
      String err = e.getMessage();
      errList.add(err);
      return false;
    }
  }


}
