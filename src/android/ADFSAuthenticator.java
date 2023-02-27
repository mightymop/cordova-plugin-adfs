package de.mopsdom.adfs;

import static android.accounts.AccountManager.KEY_ACCOUNT_NAME;
import static android.accounts.AccountManager.KEY_ACCOUNT_TYPE;
import static android.accounts.AccountManager.KEY_AUTHTOKEN;
import static de.mopsdom.adfs.request.RequestManager.KEY_IS_NEW_ACCOUNT;
import static de.mopsdom.adfs.request.RequestManager.REFRESH_TOKEN_EXP;
import static de.mopsdom.adfs.request.RequestManager.TOKEN_TYPE_ACCESS;
import static de.mopsdom.adfs.request.RequestManager.TOKEN_TYPE_ID;
import static de.mopsdom.adfs.request.RequestManager.TOKEN_TYPE_REFRESH;

import android.accounts.AbstractAccountAuthenticator;
import android.accounts.Account;
import android.accounts.AccountAuthenticatorResponse;
import android.accounts.AccountManager;
import android.accounts.NetworkErrorException;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Browser;
import android.text.TextUtils;
import android.util.Log;
import android.webkit.CookieManager;
import android.webkit.CookieSyncManager;
import android.webkit.ValueCallback;

import androidx.browser.customtabs.CustomTabsIntent;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import org.json.JSONArray;
import org.json.JSONObject;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.RSAPublicKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Date;

import de.mopsdom.adfs.http.HttpException;
import de.mopsdom.adfs.request.RequestManager;
import de.mopsdom.adfs.utils.AccountUtils;
import de.mopsdom.adfs.utils.Utils;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.JwtParserBuilder;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.gson.io.GsonDeserializer;

public class ADFSAuthenticator extends AbstractAccountAuthenticator {

  private final String TAG = getClass().getSimpleName();

  private Context context;
  private AccountManager accountManager;
  private RequestManager requestManager;

  private JSONObject configLoadResult;

  public ADFSAuthenticator(Context context) {
    super(context);
    this.context = context;

    accountManager = AccountManager.get(context);
    requestManager = new RequestManager(context);
  }

  /**
   * Called when the user adds a new account through Android's system settings or when an app
   * explicitly calls this.
   */
  @Override
  public Bundle addAccount(AccountAuthenticatorResponse response, String accountType,
                           String authTokenType, String[] requiredFeatures, Bundle options) {

    Bundle result = new Bundle();

    Intent intent = createIntentForAuthorization(response);

    // We're creating a new account, not just renewing our authorisation
    intent.putExtra(KEY_IS_NEW_ACCOUNT, true);

    result.putParcelable(AccountManager.KEY_INTENT, intent);

    return result;
  }

  public Intent getLoginIntent() {

    Intent intent = new Intent(context, ADFSAuthenticatorActivity.class);

    String client_id = Utils.getVal(context, "client_id");
    String scope = Utils.getVal(context, "scope");

    intent.putExtra("client_id", client_id != null ? client_id : "");
    intent.putExtra("scope", scope != null ? scope : "");
    intent.putExtra("nonce", "random-nonce");
    intent.putExtra("response_type", "code id_token");

    // We're creating a new account, not just renewing our authorisation
    intent.putExtra(KEY_IS_NEW_ACCOUNT, true);

    return intent;
  }

  @Override
  public Bundle confirmCredentials(AccountAuthenticatorResponse response, Account account,
                                   Bundle options) throws NetworkErrorException {
    return null;
  }

  /**
   * Tries to retrieve a previously stored token of any type. If the token doesn't exist yet or
   * has been invalidated, we need to request a set of replacement tokens.
   */
  @Override
  public Bundle getAuthToken(AccountAuthenticatorResponse response, Account account,
                             String authTokenType, Bundle options) {

    Log.d(TAG, String.format("getAuthToken called with account.type '%s', account.name '%s', " +
      "authTokenType '%s'.", account.type, account.name, authTokenType));

    // Try to retrieve a stored token
    String token = accountManager.peekAuthToken(account, authTokenType);

    if (TextUtils.isEmpty(token)) {
      // If we don't have one or the token has been invalidated, we need to check if we have
      // a refresh token
      Log.d(TAG, "Token empty, checking for refresh token.");
      String refreshToken = accountManager.peekAuthToken(account, TOKEN_TYPE_REFRESH);

      if (TextUtils.isEmpty(refreshToken)) {
        // If we don't even have a refresh token, we need to launch an intent for the user
        // to get us a new set of tokens by authorising us again.

        Log.d(TAG, "Refresh token empty, launching intent for renewing authorisation.");

        Bundle result = new Bundle();

        Intent intent = createIntentForAuthorization(response);

        // Provide the account that we need re-authorised
        intent.putExtra(KEY_ACCOUNT_NAME, account.name);

        result.putParcelable(AccountManager.KEY_INTENT, intent);
        return result;
      } else {
        // Got a refresh token, let's use it to get a fresh set of tokens
        Log.d(TAG, "Got refresh token, getting new tokens.");

        try {
          String resultJson = requestManager.refresh_token(AccountUtils.getAccountData(context, account, "clientid"),
            refreshToken,
            "refresh_token");

          JSONObject jresultJson = new JSONObject(resultJson);

          if (jresultJson.has("id_token")) {
            accountManager.setAuthToken(account, TOKEN_TYPE_ID, jresultJson.getString("id_token"));
          }
          if (jresultJson.has("access_token")) {
            accountManager.setAuthToken(account, TOKEN_TYPE_ACCESS, jresultJson.getString("access_token"));
          }
          if (jresultJson.has("refresh_token")) {
            accountManager.setAuthToken(account, TOKEN_TYPE_REFRESH, jresultJson.getString("refresh_token"));
          }
          if (jresultJson.has("refresh_token_expires_in")) {
            long exp = jresultJson.getLong("refresh_token_expires_in");
            long time = System.currentTimeMillis()+exp;
            accountManager.setUserData(account, REFRESH_TOKEN_EXP, String.valueOf(time));
          }
        } catch (HttpException e) {
          // If the refresh token has expired, we need to launch an intent for the user
          // to get us a new set of tokens by authorising us again.

          Log.d(TAG, "Refresh token expired, launching intent for renewing authorisation.");

          Bundle result = new Bundle();

          Intent intent = createIntentForAuthorization(response);

          // Provide the account that we need re-authorised
          intent.putExtra(KEY_ACCOUNT_NAME, account.name);

          result.putParcelable(AccountManager.KEY_INTENT, intent);
          return result;
        } catch (Exception e) {
          //FIXME: we need to see how to handle this here because we can't do a start activity for result
        }

        // Now, let's return the token that was requested
        token = accountManager.peekAuthToken(account, authTokenType);
      }
    }
    else {
      //validating token

      if (!authTokenType.equalsIgnoreCase(TOKEN_TYPE_REFRESH)) {
        if (!validateToken(account, token,TOKEN_TYPE_ACCESS.equalsIgnoreCase(authTokenType))) {
          String strexp=accountManager.getUserData(account,REFRESH_TOKEN_EXP);
          if (strexp==null||strexp.trim().length()==0)
          {
            strexp = "0";
          }
          long exp = Long.parseLong(strexp);
          if (exp*1000<System.currentTimeMillis())
          {
            accountManager.invalidateAuthToken(TOKEN_TYPE_REFRESH, token);
            accountManager.setAuthToken(account,TOKEN_TYPE_REFRESH, "");
          }
          accountManager.invalidateAuthToken(authTokenType, token);
          accountManager.setAuthToken(account,authTokenType, "");
          return getAuthToken(response, account, authTokenType, options);
        }
      }
      else
      {
        String strexp=accountManager.getUserData(account,REFRESH_TOKEN_EXP);
        if (strexp==null||strexp.trim().length()==0)
        {
          strexp = "0";
        }
        long exp = Long.parseLong(strexp);
        if (exp*1000<System.currentTimeMillis())
        {
          accountManager.invalidateAuthToken(TOKEN_TYPE_REFRESH, token);
          accountManager.setAuthToken(account,TOKEN_TYPE_REFRESH, "");
          return getAuthToken(response, account, authTokenType, options);
        }
      }
    }

    Log.d(TAG, String.format("Returning token '%s' of type '%s'.", token, authTokenType));

    Bundle result = new Bundle();

    result.putString(KEY_ACCOUNT_NAME, account.name);
    result.putString(KEY_ACCOUNT_TYPE, account.type);
    result.putString(KEY_AUTHTOKEN, token);

    return result;
  }

  public void saveKeys(Account acc, JSONObject keys)
  {
    accountManager.setUserData(acc,RequestManager.PUBLIC_TOKEN_KEYS,keys.toString());
  }

  public JSONObject getKeys(Account acc)
  {
    try {
      String res = accountManager.getUserData(acc, RequestManager.PUBLIC_TOKEN_KEYS);
      if (res!=null&&res.trim().length()>0) {
        return new JSONObject();
      }
      return null;
    }
    catch (Exception e)
    {
      Log.e("cordova-plugin-adfs",e.getMessage());
      return null;
    }
  }

  public JKey getKey(Account acc, String token) {

    try {
      JSONObject header = Utils.getHeader(token);
      if (header!=null) {
        String x5t = header.getString("x5t");
        String alg = header.getString("alg");
        String kid = header.getString("kid");

        JSONObject localKeys = getKeys(acc);

        if (localKeys==null) {
          localKeys = getPublicKeys();
          saveKeys(acc,localKeys);
        }

        JKey key;
        if ((key=searchKey(localKeys,x5t,alg,kid))!=null)
        {
          return key;
        }

        localKeys = getPublicKeys();
        saveKeys(acc,localKeys);

        if ((key=searchKey(localKeys,x5t,alg,kid))!=null)
        {
          return key;
        }
        return null;
      }
    }
    catch (Exception e)
    {
      Log.e("cordova-plugin-adfs",e.getMessage());
    }

    return null;
  }

  class JKey {
    public String n;
    public String e;
  }
  private JKey searchKey(JSONObject localKeys, String x5t, String alg, String kid) {

    try {
      JSONArray keys = localKeys.getJSONArray("keys");
      for (int n = 0; n < keys.length(); n++) {
        JSONObject key = keys.getJSONObject(n);
        boolean bx5t = key.has("x5t") && key.getString("x5t").equalsIgnoreCase(x5t);
        boolean balg = key.has("alg") && key.getString("alg").equalsIgnoreCase(alg);
        boolean bkid = key.has("kid") && key.getString("kid").equalsIgnoreCase(kid);

        if (bx5t && balg && bkid) {
          JKey result = new JKey();
          result.n=key.getString("n");
          result.e=key.getString("e");
          return result;
        }
      }
    }
    catch (Exception e)
    {
      Log.e("cordova-plugin-adfs",e.getMessage());
    }
    return null;
  }


  public JSONObject getPublicKeys()
  {
    try {
      return  this.requestManager.loadKeys();
    }
    catch (Exception e)
    {
      Log.e("cordova-plugin-adfs",e.getMessage());
      return null;
    }
  }

  public boolean validateToken(Account acc, String token, boolean accessToken) {
    try {
      // Parse the token without verifying the signature
      Gson gson = new GsonBuilder().disableHtmlEscaping().create(); //implement me

      JKey jsonKey = getKey(acc,token);
      JwtParserBuilder parserBuilder = Jwts.parserBuilder();
      parserBuilder.deserializeJsonWith(new GsonDeserializer(gson));
      parserBuilder.setAllowedClockSkewSeconds(60)
      .requireIssuer(accessToken? requestManager.getAccesTokenTrustIssuer() : Utils.getADFSBaseUrl(context));

      if (jsonKey!=null)
      {
        BigInteger modulus = null;
        BigInteger exponent = null;

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
          modulus =  new BigInteger(1, Base64.getUrlDecoder().decode(jsonKey.n));
          exponent = new BigInteger(1, Base64.getUrlDecoder().decode(jsonKey.e));;
        }
        else
        {
          modulus =  new BigInteger(1, android.util.Base64.decode(jsonKey.n, android.util.Base64.DEFAULT));
          exponent = new BigInteger(1, android.util.Base64.decode(jsonKey.e, android.util.Base64.DEFAULT));
        }

        PublicKey publicKey = KeyFactory.getInstance("RSA").generatePublic(new RSAPublicKeySpec(modulus, exponent));
        parserBuilder.setSigningKey(publicKey);
      }

      Jws<Claims> jws = parserBuilder
              .build()
              .parseClaimsJws(token);

      // Get the token claims
      Claims claims = jws.getBody();

      // Check the "exp" claim to ensure the token has not expired
      Date expiration = claims.getExpiration();
      if (expiration != null && expiration.before(new Date())) {
        return false;
      }

      // Check the "nbf" claim to ensure the token is not used before it's valid
      Date notBefore = claims.getNotBefore();
      if (notBefore != null && notBefore.after(new Date())) {
        return false;
      }

      return true;
    } catch (Exception e) {
      // Token is not valid
      return false;
    }
  }

  /**
   * Create an intent for showing the authorisation web page.
   */
  private Intent createIntentForAuthorization(AccountAuthenticatorResponse response) {
    Intent intent = new Intent(context, ADFSAuthenticatorActivity.class);

    String client_id = Utils.getVal(context, "client_id");
    String scope = Utils.getVal(context, "scope");

    intent.putExtra("client_id", client_id != null ? client_id : "");
    intent.putExtra("scope", scope != null ? scope : "");
    intent.putExtra("nonce", "random-nonce");
    intent.putExtra("response_type", "code id_token");

    intent.putExtra(AccountManager.KEY_ACCOUNT_AUTHENTICATOR_RESPONSE, response);
    return intent;
  }

  public static void logout(Context context) {
    Account acc = AccountUtils.getCurrentUser(context);
    if (acc != null) {
      AccountUtils.setAccountData(context, acc, RequestManager.ACCOUNT_STATE_KEY, "0");
    }



    String strconfig = Utils.getSharedPref(context, "configuration");
    if (strconfig != null) {

      try
      {
        JSONObject configjson = new JSONObject(strconfig);
        Uri uri = Uri.parse(configjson.getString("end_session_endpoint"));

        // CustomTabsIntent.Builder builder = new CustomTabsIntent.Builder();
        // CustomTabsIntent customTabsIntent = builder.build();
        // customTabsIntent.intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);

        // customTabsIntent.intent.setData(uri);
       // customTabsIntent.launchUrl(context, uri);

        Intent i = new Intent(Intent.ACTION_VIEW,uri);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        context.startActivity(i);

        PendingIntent pi=null;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
          pi = PendingIntent.getActivity(context,0,
                  i
                  //customTabsIntent.intent
                  ,PendingIntent.FLAG_UPDATE_CURRENT| PendingIntent.FLAG_IMMUTABLE);
        }else {
          pi = PendingIntent.getActivity(context,0,
                  i
                  // customTabsIntent.intent
                  ,PendingIntent.FLAG_UPDATE_CURRENT);
        }

        String CHANNEL_WHATEVER = "SSO ABMELDUNG";
        NotificationCompat.Builder nbuilder = new NotificationCompat.Builder(context,CHANNEL_WHATEVER)
                .setContentTitle("Abmeldung")
                .setContentText("Klicken Sie, um die Abmeldung durchzuführen!")
                .setAutoCancel(true)
                .setSmallIcon(Utils.getLogoutIconIdentifier(context))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pi);

        NotificationManager mgr = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                && mgr.getNotificationChannel(CHANNEL_WHATEVER) == null
        ) {
          mgr.createNotificationChannel(new
                  NotificationChannel(
                          CHANNEL_WHATEVER,
                          "Abmeldung",
                          NotificationManager.IMPORTANCE_HIGH
                  )
          );
        }

        mgr.notify(1010101, nbuilder.build());

      } catch (Exception e) {
        Log.e("cordova-plugin-adfs", e.getMessage());
      }
    }

  }

  @Override
  public Bundle editProperties(AccountAuthenticatorResponse response, String accountType) {
    return null;
  }

  @Override
  public String getAuthTokenLabel(String authTokenType) {
    switch (authTokenType) {
      case TOKEN_TYPE_ID:
        return "id_token";

      case TOKEN_TYPE_ACCESS:
        return "access_token";

      case TOKEN_TYPE_REFRESH:
        return "refresh_token";

      default:
        return null;
    }
  }

  @Override
  public Bundle hasFeatures(AccountAuthenticatorResponse response, Account account,
                            String[] features) throws NetworkErrorException {
    return null;
  }

  @Override
  public Bundle updateCredentials(AccountAuthenticatorResponse response, Account account,
                                  String authTokenType, Bundle options)
    throws NetworkErrorException {
    return null;
  }

}
