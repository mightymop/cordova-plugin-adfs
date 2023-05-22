package de.berlin.polizei.oidcsso.authenticator;

import static android.accounts.AccountManager.KEY_ACCOUNT_NAME;
import static android.accounts.AccountManager.KEY_ACCOUNT_TYPE;
import static android.accounts.AccountManager.KEY_AUTHTOKEN;
import static de.berlin.polizei.oidcsso.OIDCActivity.ACTION_LOGIN;
import android.accounts.AbstractAccountAuthenticator;
import android.accounts.Account;
import android.accounts.AccountAuthenticatorResponse;
import android.accounts.AccountManager;
import android.accounts.NetworkErrorException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.RequiresApi;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigInteger;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.RSAPublicKeySpec;
import java.util.Base64;
import java.util.Date;
import java.util.concurrent.ExecutionException;

import javax.net.ssl.HttpsURLConnection;

import de.berlin.polizei.oidcsso.BuildConfig;
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
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.gson.io.GsonDeserializer;

public class ADFSAuthenticator extends AbstractAccountAuthenticator {


    public static final String TOKEN_TYPE_ID = "TOKEN_TYPE_ID";
    public static final String TOKEN_TYPE_ACCESS = "TOKEN_TYPE_ACCESS";
    public static final String TOKEN_TYPE_REFRESH = "TOKEN_TYPE_REFRESH";
    public static final String PUBLIC_TOKEN_KEYS = "PUBLIC_KEYS";

    public static final String TOKEN_DATA = "TOKEN_DATA";

    private static final String TAG = ADFSAuthenticator.class.getSimpleName();
    private Context context;
    private AccountManager accountManager;

    private boolean sslInitOK = false;

    public ADFSAuthenticator(Context context) {
        super(context);
        try {
            this.context = context;
            accountManager = AccountManager.get(context);
        }
        catch (Exception e)
        {
            Log.e(TAG,e.getMessage(),e);
        }
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
                if (result!=null)
                {
                    String resultMerged = Utils.getTokenData(context,Utils.getCurrentUser(context));
                    if (resultMerged!=null)
                    {
                        return resultMerged;
                    }
                    else {
                        return result;
                    }
                }

                Log.e(TAG,rtask.getException()!=null?rtask.getException().getMessage():"UNBEKANNTER FEHLER BEIM TOKEN REFRESH");
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

        if (!sslInitOK) {
            Utils.initSSL();
        }

        if (!initOIDCServiceConfig())
        {
            Bundle result = new Bundle();

            Intent intent = createIntentForAuthorization(response);

            result.putParcelable(AccountManager.KEY_INTENT, intent);

            return result;
        }


        if (BuildConfig.DEBUG)
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
        int isvalid = -1;
        Bundle result = null;
        switch (authTokenType) {
            case TOKEN_TYPE_ACCESS:
                token = access_token;

                if (BuildConfig.DEBUG)
                Log.d(TAG, "offlineToken = " + String.valueOf(isofflinetoken));

                isvalid = validateToken(context, token);
                if (BuildConfig.DEBUG)
                Log.d(TAG, "isvalid = " + String.valueOf(isvalid));

                if (token == null || token.isEmpty() || (expires_in<System.currentTimeMillis())) {

                    try {
                        String oldtoken = token;
                        String responseresult = refreshToken(context);
                        if (responseresult!=null) {
                            responseresult = Utils.prepareData(responseresult);
                        }
                        if (responseresult==null)
                        {
                            Log.e(TAG,"TOKEN_TYPE_ACCESS -------- refreshToken failed ------- result was null >> reauth with login");
                            result = new Bundle();

                            Intent intent = createIntentForAuthorization(response);

                            result.putParcelable(AccountManager.KEY_INTENT, intent);

                            return result;
                        }
                        //responseresult schon in Account gemerged
                        if (BuildConfig.DEBUG) {
                            Log.d(TAG, "merged data: " + responseresult);
                            Log.d(TAG, "oldtoken: " + oldtoken);
                        }

                        expires_in = Utils.getNumberFromTokenData(context,account,"expires_in");

                        //Wenn refresh_token abgelaufen, dann wird die exp nicht geändert...
                        if (expires_in <= System.currentTimeMillis()) {
                            if (BuildConfig.DEBUG)
                            Log.e(TAG,"TOKEN_TYPE_ACCESS -------- refreshToken failed, refresh token abgelaufen ------- expires_in <= System.currentTimeMillis() >> reauth with login");

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
                if (BuildConfig.DEBUG)
                Log.d(TAG, "offlineToken = " + String.valueOf(isofflinetoken));

                isvalid = validateToken(context, token);
                if (BuildConfig.DEBUG)
                Log.d(TAG, "isvalid = " + String.valueOf(isvalid));

                if (token == null || token.isEmpty() || isvalid==-1 || isofflinetoken) {

                    try {
                        String oldtoken = token;
                        String tresponse = refreshToken(context);
                        if (tresponse!=null) {
                            tresponse = Utils.prepareData(tresponse);
                        }
                        if (tresponse==null)
                        {
                            if (BuildConfig.DEBUG)
                            Log.e(TAG,"TOKEN_TYPE_ID -------- refreshToken failed, tresponse is null >> reauth with login");

                            result = new Bundle();

                            Intent intent = createIntentForAuthorization(response);

                            result.putParcelable(AccountManager.KEY_INTENT, intent);

                            return result;
                        }
                        if (BuildConfig.DEBUG) {
                            Log.d(TAG, "merged data: " + tresponse);
                            Log.d(TAG, "oldtoken: " + oldtoken);
                        }
                        token = Utils.getStringFromTokenData(context,account,"id_token");

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

                    if (BuildConfig.DEBUG)
                    Log.e(TAG,"TOKEN_TYPE_REFRESH -------- token == null || token.isEmpty() || refresh_token_expires_in<System.currentTimeMillis() >> reauth with login");


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

        if (BuildConfig.DEBUG)
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
                    localKeys = Utils.getPublicKeys(ctx);
                    if (localKeys!=null) {
                        saveKeys(ctx, localKeys);
                    }
                    else {
                        Log.e(TAG,"COULD NOT GET PUBLIC KEYS!!!");
                    }
                }

                if (localKeys == null || !localKeys.has("keys")) {
                    return null;
                }

                JKey key;
                if ((key = searchKey(localKeys, x5t, alg, kid)) != null) {
                    return key;
                }

                Utils.setSharedPref(context,PUBLIC_TOKEN_KEYS,"");
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



    public int validateToken(Context ctx, String token) {
        try {
            if (token == null)
                return -1;

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
                return -2;
            }

            // Check the "nbf" claim to ensure the token is not used before it's valid
            Date notBefore = claims.getNotBefore();
            if (notBefore != null && notBefore.after(new Date())) {
                return -3;
            }

            return 0;
        } catch (Exception e) {
            Log.e(TAG, e.getMessage());
            if (e instanceof UnsupportedJwtException)
            {
                try {
                    JSONObject payload = Utils.getPayload(token);

                    Date expiration = payload.has("exp")?new Date(payload.getLong("exp")):null;
                    if (expiration != null && expiration.before(new Date())) {
                        return -2;
                    }

                    // Check the "nbf" claim to ensure the token is not used before it's valid
                    Date notBefore = payload.has("nbf")?new Date(payload.getLong("nbf")):null;
                    if (notBefore != null && notBefore.after(new Date())) {
                        return -3;
                    }

                    return 0;
                }
                catch (Exception ex2)
                {
                    Log.e(TAG, ex2.getMessage());
                }
            }
            // Token is not valid
            return -1;
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

    private boolean initOIDCServiceConfig() {
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
            boolean result = false;
            try {
                result = task.get(); // wait for the AsyncTask to complete and get the result
                Log.e(TAG,"RESULT LoadconfigTask: "+String.valueOf((result)));
            } catch (InterruptedException e) {
                Log.e(TAG,e.getMessage(),e);
            } catch (ExecutionException e) {
                Log.e(TAG,e.getMessage(),e);
            }
            return result;
        }
        return true;
    }


}
