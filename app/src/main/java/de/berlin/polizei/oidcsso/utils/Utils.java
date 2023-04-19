package de.berlin.polizei.oidcsso.utils;


import static de.berlin.polizei.oidcsso.authenticator.ADFSAuthenticator.TOKEN_DATA;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.preference.PreferenceManager;
import android.util.Log;


import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URL;
import java.security.cert.X509Certificate;
import java.util.Base64;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import de.berlin.polizei.oidcsso.R;

public class Utils {

    public static final String TAG = Utils.class.getSimpleName();

    private static String decodeBase64(String data) {
        byte[] result = null;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            result = Base64.getDecoder().decode(data);
        } else {
            result = android.util.Base64.decode(data, android.util.Base64.DEFAULT);
        }
        return new String(result);
    }

    public static String getSharedPref(Context ctx, String key) {
        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(ctx);
        String resourceType = "string";
        String packageName = ctx.getPackageName();
        int identifier =  ctx.getResources().getIdentifier("default_"+(key.replace("_key","")), resourceType, packageName);
        if (identifier!=0)
        {
            return sharedPref.getString(key, ctx.getString(identifier));
        }
        else
        {
            return sharedPref.getString(key, null);
        }
    }

    public static String getSharedPref(Context ctx, String key, String def) {
        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(ctx);
        return sharedPref.getString(key, def);
    }

    public static boolean getSharedPrefBoolean(Context ctx, String key) {
        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(ctx);
        return sharedPref.getBoolean(key, false);
    }

    public static Account getCurrentUser(Context ctx) {
        AccountManager am = (AccountManager) ctx.getSystemService(Context.ACCOUNT_SERVICE);
        Account[] accounts = am.getAccountsByType(ctx.getString(R.string.account_type));

        if (accounts != null && accounts.length > 0) {
            return accounts[0];
        }

        return null;
    }

    public static String getTokenData(Context context, Account account)
    {
        AccountManager amgr = AccountManager.get(context);
        return amgr.getUserData(account,TOKEN_DATA);
    }

    public static long getNumberFromTokenData(Context context, Account account, String key)
    {
        String data = getTokenData(context,account);
        try {
            JSONObject json = new JSONObject(data);
            return (json.has(key)?json.getLong(key):-1);
        }
        catch (Exception e)
        {
            Log.e(TAG,e.getMessage(),e);
            return -1;
        }
    }

    public static String getStringFromTokenData(Context context, Account account, String key)
    {
        AccountManager amgr = AccountManager.get(context);
        String data = amgr.getUserData(account,TOKEN_DATA);
        try {
            JSONObject json = new JSONObject(data);
            return (json.has(key)?json.getString(key):null);
        }
        catch (Exception e)
        {
            Log.e(TAG,e.getMessage(),e);
            return null;
        }
    }

    public static void outputResponse(String TAG, String response) {
        try {
            JSONObject json = new JSONObject(response);
            Log.i(TAG, "New access token: " + (json.has("access_token")?json.getString("access_token"):"?"));
            Log.i(TAG, "New id token: " + (json.has("id_token")?json.getString("id_token"):"?"));
            Log.i(TAG, "New refresh token: " + (json.has("refresh_token")?json.getString("refresh_token"):"?"));

            Log.i(TAG, "AccessToken Exp Time: " +  (json.has("expires_in")?String.valueOf(json.getLong("expires_in")):"?"));

            Log.i(TAG, "RefreshToken Exp Time: " +  (json.has("refresh_token_expires_in")?String.valueOf(json.getLong("refresh_token_expires_in")):"?"));
        }
        catch (Exception e)
        {
            Log.w(TAG,e.getMessage(),e);
        }
    }


    public static String getNameFromToken(Context context, String id_token) {
        return (String) getClaimFromToken(id_token,Utils.getSharedPref(context,context.getString(R.string.user_id_claim_key),context.getString(R.string.default_user_id_claim)));
    }

    public static Object getClaimFromToken(String id_token, String claim) {
        JSONObject payload = Utils.getPayload(id_token);
        try {
            return payload.get(claim);
        } catch (Exception e) {
            Log.e(TAG, e.getMessage());
            return null;
        }
    }

    public static void setSharedPref(Context ctx, String key, String val) {
        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(ctx);

        SharedPreferences.Editor editor = sharedPref.edit();

        editor.putString(key, val);

        editor.apply();
    }

    public static String prepareData(String data)
    {
        try {
            JSONObject jdata = new JSONObject(data);

            if (jdata.has("expires_in"))
            {
                long expires_in = jdata.getLong("expires_in")*1000;
                expires_in = System.currentTimeMillis()+expires_in;
                jdata.put("expires_in",expires_in);
            }

            if (jdata.has("refresh_token_expires_in"))
            {
                long refresh_token_expires_in = jdata.getLong("refresh_token_expires_in")*1000;
                refresh_token_expires_in = System.currentTimeMillis()+refresh_token_expires_in;
                jdata.put("refresh_token_expires_in",refresh_token_expires_in);
            }

            return jdata.toString();
        }
        catch (Exception e)
        {
            Log.e(TAG,e.getMessage(),e);
            return data;
        }
    }

    public static void mergeData(Context context, Account account, String data)
    {
        AccountManager amgr = AccountManager.get(context);
        if (account==null)
        {
            return;
        }

        try {
            data = prepareData(data);

            JSONObject jdata_neu = new JSONObject(data);

            String data_alt = amgr.getUserData(account,TOKEN_DATA);
            if (data_alt!=null&&!data_alt.isEmpty()) {
                JSONObject jdata_alt = new JSONObject(data_alt);

                // Überprüfen, ob der Schlüssel auch im zweiten JSONObject vorhanden ist
                Iterator<String> keys = jdata_alt.keys();

                while (keys.hasNext()) {
                    // Überprüfen, ob der Schlüssel auch im zweiten JSONObject vorhanden ist
                    String key = keys.next();
                    if (jdata_neu.has(key)) {
                        Object value1 = jdata_alt.get(key);
                        Object value2 = jdata_neu.get(key);

                        // Überprüfen, ob beide Werte nicht null bzw. "" sind
                        if (value1 != null && !value1.equals("") && value2 != null && !value2.equals("")) {
                            // Wenn ja, füge den Schlüssel und Wert zum Ergebnis-JSONObject hinzu
                            jdata_alt.put(key, value2);
                        }
                    }
                }

                String result = jdata_alt.toString();
                Log.i(TAG,"Set merged data: "+result);
                amgr.setUserData(account,TOKEN_DATA,result);
            }
            else {
                Log.i(TAG,"Set new data: "+data);
                amgr.setUserData(account,TOKEN_DATA,data);
            }
        }
        catch (Exception e)
        {
            Log.e(TAG,e.getMessage(),e);
        }
    }

    public static void createAccount(Context context, String data)
    {
        AccountManager amgr = AccountManager.get(context);
        Account accounts[] = amgr.getAccountsByType(context.getString(R.string.account_type));
        Account account=accounts.length>0?accounts[0]:null;

        try {
            data = prepareData(data);

            JSONObject jdata = new JSONObject(data);
            String name = getNameFromToken(context, jdata.getString("id_token"));

            if (account==null)
            {
                Log.i(TAG,"Create account: "+name);
                account = new Account(name, context.getString(R.string.account_type));
                amgr.addAccountExplicitly(account, null, null);
            }
            else {
                if (!name.equalsIgnoreCase(account.name)) {
                    Log.i(TAG,"Rename account: "+name);
                    amgr.renameAccount(account, name , null, null);
                }
            }
            Log.i(TAG,"Set data: "+data);

            amgr.setUserData(account,TOKEN_DATA,data);
        }
        catch (Exception e)
        {
            Log.e(TAG,e.getMessage(),e);
        }

    }

    public static Map<String, String> getDataFromUri(Uri uri) {

        String[] parts = uri.getFragment()!=null?uri.getFragment().split("&"):null;
        if (parts==null)
        {
            String queryparams = uri.toString().substring(uri.toString().lastIndexOf("?")+1);
            parts = queryparams.split("&");
        }
        Map<String, String> map = new HashMap<String, String>();
        for (int n = 0; n < parts.length; n++) {
            try {
                map.put(parts[n].split("=")[0], parts[n].split("=")[1]);
            } catch (Exception e) {
                Log.w(TAG, e.getMessage());
            }
        }

        return map;
    }

    public static JSONObject getPayload(String token) {

        String[] parts = token.split("\\.");
        String decodedString = decodeBase64(parts[1]);

        JSONObject payload = null;
        try {
            payload = new JSONObject(decodedString);
        } catch (JSONException e) {
            Log.e(TAG, e.getMessage());
            return null;
        }

        return payload;
    }

    public static void initSSL() {
        try {
            TrustManager[] trustAllCerts = new TrustManager[]{
                    new X509TrustManager() {
                        public X509Certificate[] getAcceptedIssuers() {
                            return null;
                        }

                        public void checkClientTrusted(X509Certificate[] certs, String authType) {
                        }

                        public void checkServerTrusted(X509Certificate[] certs, String authType) {
                        }
                    }
            };

            // Install the all-trusting trust manager
            SSLContext sslContext = SSLContext.getInstance("SSL");
            sslContext.init(null, trustAllCerts, new java.security.SecureRandom());
            HttpsURLConnection.setDefaultSSLSocketFactory(sslContext.getSocketFactory());


            HostnameVerifier hostnameVerifier = new HostnameVerifier() {
                @Override
                public boolean verify(String hostname, SSLSession session) {
                    return true;
                }
            };

            HttpsURLConnection.setDefaultHostnameVerifier(hostnameVerifier);
        } catch (Exception e) {
            Log.e(TAG, e.getMessage());
        }
    }

    public static HttpsURLConnection getConnection(Context context, Uri uri, String method, boolean withoutProxy) {
        try
        {
            URL url = new URL(uri.toString());

            boolean mapswitch = Utils.getSharedPrefBoolean(context,context.getString(R.string.toggle_map_key));
            String strproxy = Utils.getSharedPref(context,context.getString(R.string.proxy_key),!mapswitch?context.getString(R.string.default_proxy_map):context.getString(R.string.default_proxy_poldom));
            Proxy proxy;
            if (!withoutProxy&&strproxy!=null&&!strproxy.isEmpty()) {
                if (strproxy.contains(":")) {
                    proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress(strproxy.split(":")[0], Integer.parseInt(strproxy.split(":")[1])));
                } else {
                    proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress(strproxy, 80));
                }
            }
            else {
                proxy = Proxy.NO_PROXY;
            }

            HttpsURLConnection conn = null;

            conn = (HttpsURLConnection) url.openConnection(proxy);

            // Set up the connection properties
            conn.setRequestMethod(method);
            int timeout = 20000;

            try {
               String strtimeout =  Utils.getSharedPref(context,context.getString(R.string.timeout_key));
               timeout = Integer.parseInt(strtimeout)*1000;
            }
            catch (Exception e)
            {
                timeout = 20000;
            }

            conn.setReadTimeout(timeout /* milliseconds */);
            conn.setConnectTimeout(timeout /* milliseconds */);
            conn.setDoInput(true);
            if (method.equalsIgnoreCase("post")) {
                conn.setDoOutput(true);
            }

            return conn;
        } catch (IOException e) {
            Log.e(TAG,e.getMessage());
            return null;
        }
    }

    public static JSONObject getHeader(String token) {

        String[] parts = token.split("\\.");
        String decodedString = decodeBase64(parts[0]);

        JSONObject header = null;
        try {
            header = new JSONObject(decodedString);
        } catch (JSONException e) {
            Log.e(TAG, e.getMessage());
            return null;
        }

        return header;
    }

}
