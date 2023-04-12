package de.berlin.polizei.oidcsso.tasks;

import static de.berlin.polizei.oidcsso.authenticator.ADFSAuthenticator.TOKEN_DATA;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;

import androidx.browser.customtabs.CustomTabsCallback;
import androidx.browser.customtabs.CustomTabsClient;
import androidx.browser.customtabs.CustomTabsIntent;
import androidx.browser.customtabs.CustomTabsServiceConnection;
import androidx.browser.customtabs.CustomTabsSession;

import org.json.JSONObject;

import de.berlin.polizei.oidcsso.R;
import de.berlin.polizei.oidcsso.utils.Utils;

public class LogoutTask extends AsyncTask<String, Void, Boolean> {

    private Context context;
    private String logoutUrl;

    private CustomTabsSession session;

    private CustomTabsIntent customTabsIntent;
    private CustomTabsServiceConnection connection;

    private CustomTabsClient customTabsClient;
    private CustomTabsCallback callback;

    public LogoutTask(Context c){
        context=c;
    }

    @Override
    protected void onPreExecute() {
        AccountManager amgr = AccountManager.get(context);
        Account accounts[] = amgr.getAccountsByType(context.getString(R.string.account_type));
        Account account=accounts.length>0?accounts[0]:null;

        if (account==null)
        {
            return;
        }

        boolean mapswitch = Utils.getSharedPrefBoolean(context,context.getString(R.string.toggle_map_key));
        String baseUrl = Utils.getSharedPref(context,context.getString(R.string.baseurl_key),!mapswitch?context.getString(R.string.default_baseurl_map):context.getString(R.string.default_baseurl_poldom));

        String config = Utils.getSharedPref(context,context.getString(R.string.configuration_key));
        String redirect_url = Utils.getSharedPref(context,context.getString(R.string.redirect_url_key));

        String id_token = Utils.getStringFromTokenData(context,account,"id_token");

        if (account!=null)
        {
            Utils.setSharedPref(context,"lastlogout",String.valueOf(System.currentTimeMillis()));
            amgr.removeAccountExplicitly(account);
        }

        String sid = id_token!=null?(String) Utils.getClaimFromToken(id_token,"sid"):null;

        try
        {
            JSONObject json = new JSONObject(config);
            logoutUrl = json.getString("end_session_endpoint");
            if (logoutUrl.endsWith("/"))
            {
                logoutUrl=logoutUrl.substring(0,logoutUrl.length()-1);
            }
        }
        catch (Exception e)
        {
            Log.e(this.getClass().getSimpleName(),e.getMessage());
            logoutUrl=baseUrl+"/oauth2/logout";
        }

        if (id_token!=null) {
            logoutUrl += "?id_token_hint =" + id_token;
        }
        if (sid!=null) {
            logoutUrl += "&sid=" + sid;
        }
        if (redirect_url!=null&&!redirect_url.isEmpty()&&Utils.getSharedPrefBoolean(context,context.getString(R.string.logout_redirect_key))) {
            logoutUrl+="&post_logout_redirect_uri="+redirect_url.replace("*",context.getPackageName());
        }
    }

    @Override
    protected Boolean doInBackground(String... args) {
        CustomTabsIntent.Builder builder = new CustomTabsIntent.Builder();

        customTabsIntent = builder.build();
        customTabsIntent.intent.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY);
        customTabsIntent.intent.addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);

        final Uri uri = Uri.parse(this.logoutUrl);

        connection = new CustomTabsServiceConnection() {
            @Override
            public void onCustomTabsServiceConnected(ComponentName componentName, CustomTabsClient cTabsClient) {
                customTabsClient = cTabsClient;

                callback = new CustomTabsCallback() {
                    @Override
                    public void onNavigationEvent(int navigationEvent, Bundle extras) {

                        if (navigationEvent == TAB_HIDDEN) {

                        } else if (navigationEvent == NAVIGATION_FAILED) {


                        }
                        else if (navigationEvent == NAVIGATION_FINISHED) {

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

        return true;
    }
}
