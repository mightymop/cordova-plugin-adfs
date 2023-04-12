package de.berlin.polizei.oidcsso.authenticator;

import static android.accounts.AccountManager.KEY_ACCOUNT_NAME;
import static android.accounts.AccountManager.KEY_ACCOUNT_TYPE;
import static android.accounts.AccountManager.KEY_AUTHTOKEN;

import static de.berlin.polizei.oidcsso.OIDCActivity.ACTION_LOGIN;
import static de.berlin.polizei.oidcsso.OIDCActivity.ACTION_LOGOUT;

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
import android.os.Build;
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.RequiresApi;
import androidx.core.app.NotificationCompat;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.math.BigInteger;
import java.net.URL;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.RSAPublicKeySpec;
import java.util.Base64;
import java.util.Date;
import java.util.concurrent.ExecutionException;

import javax.net.ssl.HttpsURLConnection;

import de.berlin.polizei.oidcsso.R;
import de.berlin.polizei.oidcsso.interfaces.TaskResultCallback;
import de.berlin.polizei.oidcsso.tasks.LoadconfigTask;
import de.berlin.polizei.oidcsso.tasks.RefreshTokenTask;
import de.berlin.polizei.oidcsso.utils.JKey;
import de.berlin.polizei.oidcsso.utils.Utils;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.JwtParserBuilder;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.gson.io.GsonDeserializer;

public class ADFSAuthenticator extends AbstractAccountAuthenticator {

    public static final String CHANNEL_ID = "Abmeldung";
    public static final String CHANNEL_NAME = "Abmeldung";
    public static final String CHANNEL_DESCRIPTION = "Abmeldung SSO";
    public static final String TOKEN_TYPE_ID = "TOKEN_TYPE_ID";
    public static final String TOKEN_TYPE_ACCESS = "TOKEN_TYPE_ACCESS";
    public static final String TOKEN_TYPE_REFRESH = "TOKEN_TYPE_REFRESH";
    public static final String PUBLIC_TOKEN_KEYS = "PUBLIC_KEYS";

    public static final String TOKEN_DATA = "TOKEN_DATA";

    private static final String TAG = ADFSAuthenticator.class.getSimpleName();
    private Context context;
    private AccountManager accountManager;

    public ADFSAuthenticator(Context context) {
        super(context);
        this.context = context;
        accountManager = AccountManager.get(context);
        Utils.initSSL();
        initOIDCServiceConfig();
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    public static void logout(Context context) {

        Intent i = new Intent(ACTION_LOGOUT);

        int NOTIFICATION_ID = 11010;

        PendingIntent pi = null;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            pi = PendingIntent.getActivity(context, 0,
                    i
                    //customTabsIntent.intent
                    , PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        } else {
            pi = PendingIntent.getActivity(context, 0,
                    i
                    // customTabsIntent.intent
                    , PendingIntent.FLAG_UPDATE_CURRENT);
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
                .setContentTitle(CHANNEL_NAME)
                .setContentText("Hier klicken zum Abmelden")
                .setSmallIcon(R.drawable.icon)
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
    }

    public static String refreshToken(Context context) {
        Account acc = Utils.getCurrentUser(context);
        if (acc!=null)
        {
            String refresh_token = Utils.getStringFromTokenData(context,acc,"refresh_token");
            RefreshTokenTask rtask = new RefreshTokenTask(context, refresh_token);
            rtask.execute();
            try {
                String result = rtask.get(); // wait for the AsyncTask to complete and get the result
                return result;
            } catch (InterruptedException e) {
                Log.e(TAG,e.getMessage(),e);
            } catch (ExecutionException e) {
                Log.e(TAG,e.getMessage(),e);
            }
        }

        return null;
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

        result.putParcelable(AccountManager.KEY_INTENT, intent);

        return result;
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
                               String authTokenType, Bundle options) throws NetworkErrorException {

        Log.d(TAG, String.format("getAuthToken called with account.type '%s', account.name '%s', " +
                "authTokenType '%s'.", account.type, account.name, authTokenType));

        String refresh_token = Utils.getStringFromTokenData(context,account,"refresh_token");
        String access_token = Utils.getStringFromTokenData(context,account,"access_token");
        String id_token = Utils.getStringFromTokenData(context,account,"id_token");

        long expires_in = Utils.getNumberFromTokenData(context,account,"expires_in"); //geändert in timestamp wann access_token abläuft
        long refresh_token_expires_in = Utils.getNumberFromTokenData(context,account,"refresh_token_expires_in");//geändert in timestamp wann refresh_token abläuft
        Object exp = Utils.getClaimFromToken(id_token, "exp");
        long exp_id_token = exp instanceof Long ? ((Long) exp).longValue() * 1000 : ((long)((Integer)exp).intValue())*1000; //*1000 weil unix timestamp

        if (exp_id_token<System.currentTimeMillis()&&refresh_token_expires_in<System.currentTimeMillis()) {
            Bundle result = new Bundle();

            Intent intent = createIntentForAuthorization(response);

            result.putParcelable(AccountManager.KEY_INTENT, intent);

            return result;
        }

        String token = null;
        boolean isofflinetoken = false;
        boolean isvalid = false;
        Bundle result = null;
        switch (authTokenType) {
            case TOKEN_TYPE_ACCESS:
                token = access_token;

                Log.d(TAG, "offlineToken = " + String.valueOf(isofflinetoken));

                isvalid = validateToken(context, token);
                Log.d(TAG, "isvalid = " + String.valueOf(isvalid));

                if (token == null || token.isEmpty() || (expires_in<System.currentTimeMillis())) {

                    try {

                        String responseresult = refreshToken(context);
                        //responseresult schon in Account gemerged
                        Log.i(TAG,"merged data: "+responseresult);

                        expires_in = Utils.getNumberFromTokenData(context,account,"expires_in");

                        //Wenn refresh_token abgelaufen, dann wird die exp nicht geändert...
                        if (expires_in <= System.currentTimeMillis()) {
                            result = new Bundle();

                            Intent intent = createIntentForAuthorization(response);

                            result.putParcelable(AccountManager.KEY_INTENT, intent);

                            return result;
                        } else {
                            //nach merge den neuen Token zuweisen
                            token = Utils.getStringFromTokenData(context,account,"access_token");
                        }
                    } catch (Exception e) {
                        Log.e(TAG, e.getMessage());
                        throw new NetworkErrorException(e.getMessage());
                    }
                }

                break;

            case TOKEN_TYPE_ID:
                token = id_token;
                isofflinetoken = isOfflineToken(token);
                Log.d(TAG, "offlineToken = " + String.valueOf(isofflinetoken));

                isvalid = validateToken(context, token);
                Log.d(TAG, "isvalid = " + String.valueOf(isvalid));

                if (token == null || token.isEmpty() || !isvalid || isofflinetoken) {

                    try {
                        String tresponse = refreshToken(context);
                        tresponse = Utils.prepareData(tresponse);
                        Utils.mergeData(context,account,tresponse);
                        expires_in = Utils.getNumberFromTokenData(context,account,"expires_in");

                        if (expires_in <= System.currentTimeMillis()) {
                            result = new Bundle();

                            Intent intent = createIntentForAuthorization(response);

                            result.putParcelable(AccountManager.KEY_INTENT, intent);

                            return result;
                        } else {
                            //nach merge den neuen Token zuweisen
                            token = Utils.getStringFromTokenData(context,account,"id_token");
                        }
                    } catch (Exception e) {
                        Log.e(TAG, e.getMessage());
                        if (token != null && !token.isEmpty()) {
                            //build offline token
                            Log.w(TAG, "build offline token");
                            token = getOfflineToken(token);
                        } else {
                            throw new NetworkErrorException(e.getMessage());
                        }
                    }
                }

                break;

            case TOKEN_TYPE_REFRESH:
                token = refresh_token;
                if (token == null || token.isEmpty() || refresh_token_expires_in<System.currentTimeMillis()) {
                    result = new Bundle();

                    Intent intent = createIntentForAuthorization(response);

                    result.putParcelable(AccountManager.KEY_INTENT, intent);

                    return result;
                }
                else {
                    token = refresh_token;
                }

                break;
        }

        Log.d(TAG, String.format("Returning token '%s' of type '%s'.", token, authTokenType));

        result = new Bundle();

        result.putString(KEY_ACCOUNT_NAME, account.name);
        result.putString(KEY_ACCOUNT_TYPE, account.type);
        result.putString(KEY_AUTHTOKEN, token);

        return result;
    }

    public void saveKeys(Context ctx, JSONObject keys) {
        Utils.setSharedPref(ctx,PUBLIC_TOKEN_KEYS,keys.toString());
    }

    private boolean isOfflineToken(String token) {
        try {
            if (token == null)
                return false;

            String strheaderb64 = token.split("\\.")[1];
            String strheader = null;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                strheader = new String(Base64.getUrlDecoder().decode(strheaderb64));
            } else {
                strheader = new String(android.util.Base64.decode(strheader, android.util.Base64.DEFAULT));
            }
            JSONObject header = new JSONObject(strheader);
            return header.has("alg") && header.getString("alg").equalsIgnoreCase("none");
        } catch (Exception e) {
            Log.e(TAG, e.getMessage());
            return false;
        }
    }

    private String getOfflineToken(String token) {
        if (token.split("\\.").length < 3 || (token.split("\\.").length == 3 && token.split("\\.")[2].isEmpty()))
            return token;

        try {
            String strpayloadb64 = token.split("\\.")[1];
            String strpayload = null;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                strpayload = new String(Base64.getUrlDecoder().decode(strpayloadb64));
            } else {
                strpayload = new String(android.util.Base64.decode(strpayload, android.util.Base64.DEFAULT));
            }
            JSONObject payload = new JSONObject(strpayload);

            long expiration = payload.getLong("iat") + 1000 * 60 * 60 * 2;

            payload.put("exp", expiration);

            JwtBuilder builder = Jwts.builder().setHeaderParam("typ", "JWT")
                    .setPayload(payload.toString());

            String jwt = builder.compact();

            return jwt;
        } catch (Exception e) {
            Log.e(TAG, e.getMessage());
            return token;
        }
    }

    public JSONObject getKeys(Context ctx) {
        try {
            String res = Utils.getSharedPref(ctx, PUBLIC_TOKEN_KEYS);
            if (res != null && res.trim().length() > 0) {
                JSONObject resJson = new JSONObject(res);
                if (resJson.has("error")) {
                    Log.e(TAG, resJson.getString("error"));
                    return null;
                }
                return resJson;
            }
            return null;
        } catch (Exception e) {
            Log.e(TAG, e.getMessage());
            return null;
        }
    }

    public JKey getKey(Context ctx, String token) {

        try {
            JSONObject header = Utils.getHeader(token);
            if (header != null) {
                String x5t = header.getString("x5t");
                String alg = header.getString("alg");
                String kid = header.getString("kid");

                JSONObject localKeys = getKeys(ctx);

                if (localKeys == null) {
                    localKeys = getPublicKeys();
                    saveKeys(ctx, localKeys);
                }

                if (localKeys == null || !localKeys.has("keys")) {
                    return null;
                }

                JKey key;
                if ((key = searchKey(localKeys, x5t, alg, kid)) != null) {
                    return key;
                }

                localKeys = getPublicKeys();
                saveKeys(ctx, localKeys);

                if ((key = searchKey(localKeys, x5t, alg, kid)) != null) {
                    return key;
                }
                return null;
            }
        } catch (Exception e) {
            Log.e("cordova-plugin-adfs", e.getMessage());
        }

        return null;
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
                    result.n = key.getString("n");
                    result.e = key.getString("e");
                    return result;
                }
            }
        } catch (Exception e) {
            Log.e("cordova-plugin-adfs", e.getMessage());
        }
        return null;
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)

    public JSONObject getPublicKeys() {
        try {

            JSONObject config = new JSONObject(Utils.getSharedPref(context,context.getString(R.string.configuration_key)));
            if (config.has("jwks_uri")) {
                URL url = new URL(config.getString("jwks_uri"));
                HttpsURLConnection con = (HttpsURLConnection) url.openConnection();

                try (BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()))) {
                    String inputLine;
                    StringBuilder content = new StringBuilder();
                    while ((inputLine = in.readLine()) != null) {
                        content.append(inputLine);
                    }

                    return new JSONObject(content.toString());
                } finally {
                    con.disconnect();
                }
            }

        } catch (Exception e) {
            Log.e(TAG, e.getMessage());
        }

        return null;
    }

    public boolean validateToken(Context ctx, String token) {
        try {
            if (token == null)
                return false;

            Gson gson = new GsonBuilder().disableHtmlEscaping().create();

            JKey jsonKey = getKey(ctx, token);

            JwtParserBuilder parserBuilder = Jwts.parserBuilder();

            parserBuilder.deserializeJsonWith(new GsonDeserializer(gson));
            parserBuilder.setAllowedClockSkewSeconds(60);

            Jws<Claims> jws;
            if (jsonKey != null) {
                BigInteger modulus = null;
                BigInteger exponent = null;

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    modulus = new BigInteger(1, Base64.getUrlDecoder().decode(jsonKey.n));
                    exponent = new BigInteger(1, Base64.getUrlDecoder().decode(jsonKey.e));
                } else {
                    modulus = new BigInteger(1, android.util.Base64.decode(jsonKey.n, android.util.Base64.DEFAULT));
                    exponent = new BigInteger(1, android.util.Base64.decode(jsonKey.e, android.util.Base64.DEFAULT));
                }

                PublicKey publicKey = KeyFactory.getInstance("RSA").generatePublic(new RSAPublicKeySpec(modulus, exponent));
                parserBuilder.setSigningKey(publicKey);
                jws = (Jws<Claims>) parserBuilder
                        .build()
                        .parse(token);
            } else {
                token = token.substring(0, token.lastIndexOf(".") + 1);
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
            Log.e(TAG, e.getMessage());
            // Token is not valid
            return false;
        }
    }

    /**
     * Create an intent for showing the authorisation web page.
     */
    private Intent createIntentForAuthorization(AccountAuthenticatorResponse response) {
        Intent intent = new Intent(ACTION_LOGIN);
        return intent;
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

    private void initOIDCServiceConfig() {
        String configJson = Utils.getSharedPref(context, context.getString(R.string.configuration_key));
        if (configJson==null)
        {
            LoadconfigTask task = new LoadconfigTask(context, new TaskResultCallback() {
                @Override
                public void onSuccess(String data) {
                    Utils.setSharedPref(context, context.getString(R.string.configuration_key), data);
                }

                @Override
                public void onError(Exception e) {
                    Log.e(TAG,e.getMessage(),e);
                }
            });
            task.execute();
        }
    }


}
