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
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.RequiresApi;
import androidx.core.app.NotificationCompat;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.math.BigInteger;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.RSAPublicKeySpec;
import java.util.Base64;
import java.util.Date;

import de.mopsdom.adfs.http.HttpException;
import de.mopsdom.adfs.request.RequestManager;
import de.mopsdom.adfs.utils.AccountUtils;
import de.mopsdom.adfs.utils.Utils;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtBuilder;
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
          String resultJson = requestManager.refresh_token(Utils.getVal(context, "client_id"),
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
            long time = System.currentTimeMillis()+(exp*1000)-60000;
            Log.i(TAG, "Token expires at: "+String.valueOf(time));
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
          Log.e(TAG,e.getMessage(),e);
        }

        // Now, let's return the token that was requested
        token = accountManager.peekAuthToken(account, authTokenType);
      }
    }
    else {
      //validating token
      String strexp = accountManager.getUserData(account, REFRESH_TOKEN_EXP);
      if (strexp == null || strexp.trim().length() == 0) {
        strexp = "0";
      }
      long exp = Long.parseLong(strexp);

      if (!authTokenType.equalsIgnoreCase(TOKEN_TYPE_REFRESH)) {

       if (requestManager.isServerReachable()) {

         boolean isofflinetoken=isOfflineToken(token);

         boolean isvalid = validateToken(account, token, TOKEN_TYPE_ACCESS.equalsIgnoreCase(authTokenType));

          if (isofflinetoken||!isvalid) {

            if (exp < System.currentTimeMillis()) {
              Log.w(TAG,"INVALIDIATE "+TOKEN_TYPE_REFRESH+" (EXPIRED)!!!");
              accountManager.invalidateAuthToken(TOKEN_TYPE_REFRESH, token);
              accountManager.setAuthToken(account, TOKEN_TYPE_REFRESH, "");
            }
            Log.w(TAG,"INVALIDIATE "+authTokenType+" (EXPIRED)!!!");
            accountManager.invalidateAuthToken(authTokenType, token);
            accountManager.setAuthToken(account, authTokenType, "");
            return getAuthToken(response, account, authTokenType, options);
          }
        }
        else {
          if (exp < System.currentTimeMillis()) { //token abgelaufen
            Log.w(TAG,"INVALIDIATE "+TOKEN_TYPE_REFRESH+" (EXPIRED)!!!");
            accountManager.invalidateAuthToken(TOKEN_TYPE_REFRESH, token);
            accountManager.setAuthToken(account, TOKEN_TYPE_REFRESH, "");
            accountManager.invalidateAuthToken(authTokenType, token);
            accountManager.setAuthToken(account, authTokenType, "");
            return getAuthToken(response, account, authTokenType, options);
          }
          else
          { //offline aber refresh_token noch gültig
            Log.w(TAG,"BUILD OFFLINE TOKEN!!!");
            String offlinetoken = getOfflineToken(token,exp);
            accountManager.invalidateAuthToken(authTokenType, token);
            accountManager.setAuthToken(account, authTokenType, offlinetoken);
            Bundle result = new Bundle();

            result.putString(KEY_ACCOUNT_NAME, account.name);
            result.putString(KEY_ACCOUNT_TYPE, account.type);
            result.putString(KEY_AUTHTOKEN, offlinetoken);
            return result;
          }
        }
      }
      else
      {
        if (exp < System.currentTimeMillis())
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

  private boolean isOfflineToken(String token)
  {
    try {
      String strheaderb64 = token.split("\\.")[1];
      String strheader = null;
      if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
        strheader = new String(Base64.getUrlDecoder().decode(strheaderb64));
      } else {
        strheader = new String(android.util.Base64.decode(strheader, android.util.Base64.DEFAULT));
      }
      JSONObject header = new JSONObject(strheader);
      return header.has("alg") && header.getString("alg").equalsIgnoreCase("none");
    }
    catch (Exception e)
    {
      Log.e(TAG,e.getMessage());
      return false;
    }
  }

  private long getExpirationFromToken(String token) {

    String offlinetoken = token.substring(token.lastIndexOf(".") + 1);
    Gson gson = new GsonBuilder().disableHtmlEscaping().create(); //implement me
    JwtParserBuilder parserBuilder = Jwts.parserBuilder();
    parserBuilder.deserializeJsonWith(new GsonDeserializer(gson));
    parserBuilder.setAllowedClockSkewSeconds(60);

    Jws<Claims> jws = parserBuilder
      .build()
      .parseClaimsJws(offlinetoken);

    // Get the token claims
    Claims claims = jws.getBody();

    Date exp = claims.getExpiration();

    return exp.getTime();
  }

  private String getOfflineToken(String token, long expiration)
  {
    if (token.split("\\.").length<3||(token.split("\\.").length==3&&token.split("\\.")[2].isEmpty()))
      return token;

    try {
      String strpayloadb64 = token.split("\\.")[1];
      String strpayload = null;
      if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
        strpayload = new String(Base64.getUrlDecoder().decode(strpayloadb64));
      }
      else
      {
        strpayload = new String(android.util.Base64.decode(strpayload, android.util.Base64.DEFAULT));
      }
      JSONObject payload = new JSONObject(strpayload);

      payload.put("exp",expiration);

      JwtBuilder builder = Jwts.builder().setHeaderParam("typ","JWT")
        .setPayload(payload.toString());

      String jwt = builder.compact();

      return jwt;
    }
    catch (Exception e)
    {
      Log.e(TAG,e.getMessage());
      return token;
    }
  }

  public JSONObject getKeys(Account acc)
  {
    try {
      String res = accountManager.getUserData(acc, RequestManager.PUBLIC_TOKEN_KEYS);
      if (res!=null&&res.trim().length()>0) {
        return new JSONObject(res);
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

        if (localKeys==null||!localKeys.has("keys"))
        {
          return null;
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

  @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)

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
      Jws<Claims> jws ;
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
        jws = (Jws<Claims>) parserBuilder
          .build()
          .parse(token);
      }
      else {
        token = token.substring(0,token.lastIndexOf(".")+1);
        jws = parserBuilder
          .build()
          .parseClaimsJws(token);
      }

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

  @RequiresApi(api = Build.VERSION_CODES.O)
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
        int iconIdentifier = Utils.getLogoutIconIdentifier(context);
        Intent i = new Intent(Intent.ACTION_VIEW,uri);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        int NOTIFICATION_ID = 11010;
        String CHANNEL_ID = "Abmeldung";
        String CHANNEL_NAME = "Abmeldung";
        String CHANNEL_DESCRIPTION = "Abmeldung SSO";

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

        // Erstellen der NotificationManager-Instanz
        NotificationManager notificationManager =
          (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

        // Überprüfen, ob das Gerät Android 8.0 oder höher ausführt, da ab Android 8.0 ein Notification-Kanal erforderlich ist
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
          NotificationChannel channel = new NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_HIGH
          );
          channel.setDescription(CHANNEL_DESCRIPTION);
          channel.enableLights(true);
          channel.setLightColor(Color.RED);
          notificationManager.createNotificationChannel(channel);
        }

        // Erstellen der Notification-Instanz mit dem PendingIntent
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
          .setContentTitle("Abmeldung")
          .setContentText("Hier klicken zum Abmelden")
          .setSmallIcon(iconIdentifier)
          .setContentIntent(pi)
          .setPriority(Notification.PRIORITY_HIGH)
          .setAutoCancel(true);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
          // Setzen der Farbe des Notification-Icons für Android 5.0 und höher
          builder.setColor(Color.RED);
        }

        Notification notification;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
          // Setzen der Benachrichtigungssummary für Android 8.0 und höher
          builder.setGroupSummary(true);
          notification = builder.build();
        } else {
          notification = builder.getNotification();
        }

        // Anzeigen der Notification
        notificationManager.notify(NOTIFICATION_ID, notification);

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
