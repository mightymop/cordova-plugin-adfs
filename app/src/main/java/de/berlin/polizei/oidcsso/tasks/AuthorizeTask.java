package de.berlin.polizei.oidcsso.tasks;

import android.accounts.Account;
import android.app.Activity;
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

import java.util.UUID;

import de.berlin.polizei.oidcsso.R;

import de.berlin.polizei.oidcsso.interfaces.TaskResultCallback;
import de.berlin.polizei.oidcsso.utils.Utils;

public class AuthorizeTask extends AsyncTask<String, Void, Boolean> {

    private Activity context;
    private String authorizeUrl;

    private CustomTabsSession session;

    private CustomTabsIntent customTabsIntent;
    private CustomTabsServiceConnection connection;

    private CustomTabsClient customTabsClient;
    private CustomTabsCallback callback;

    private TaskResultCallback tcb;

    public AuthorizeTask(Activity c, TaskResultCallback cb){
        context=c;
        tcb=cb;
    }

    @Override
    protected void onPreExecute() {

        boolean mapswitch = Utils.getSharedPrefBoolean(context,context.getString(R.string.toggle_map_key));
        String baseUrl = Utils.getSharedPref(context,context.getString(R.string.baseurl_key),!mapswitch?context.getString(R.string.default_baseurl_map):context.getString(R.string.default_baseurl_poldom));
        String client_id = Utils.getSharedPref(context,context.getString(R.string.client_id_key),!mapswitch?context.getString(R.string.default_client_id_map):context.getString(R.string.default_client_id_poldom));

        String config = Utils.getSharedPref(context,context.getString(R.string.configuration_key));
        String redirect_uri = Utils.getSharedPref(context,context.getString(R.string.redirect_url_key));
        String state = UUID.randomUUID().toString();

        try
        {
            JSONObject json = new JSONObject(config);
            authorizeUrl = json.getString("authorization_endpoint");
            if (authorizeUrl.endsWith("/"))
            {
                authorizeUrl=authorizeUrl.substring(0,authorizeUrl.length()-1);
            }
        }
        catch (Exception e)
        {
            Log.e(this.getClass().getSimpleName(),e.getMessage());
            authorizeUrl=baseUrl+"/oauth2/authorize";
        }

        authorizeUrl+="?client_id="+client_id;
        authorizeUrl+="&scope=openid%20email%20profile";
        authorizeUrl+="&response_type=code";
        authorizeUrl+="&response_mode=query";
        authorizeUrl+="&redirect_uri="+redirect_uri.replace("*",context.getPackageName());
        authorizeUrl+="&state="+state;

        if (Utils.getSharedPrefBoolean(context, context.getString(R.string.prompt_key))) {
            authorizeUrl+="&prompt=login";
        }

        tcb.onSuccess(state);
    }

    @Override
    protected Boolean doInBackground(String... args) {
        CustomTabsIntent.Builder builder = new CustomTabsIntent.Builder();

        customTabsIntent = builder.build();
        customTabsIntent.intent.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY);
        customTabsIntent.intent.addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);

        final Uri uri = Uri.parse(this.authorizeUrl);

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
                //customTabsIntent.intent.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY | Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_MULTIPLE_TASK);

                context.startActivity(customTabsIntent.intent);
                //customTabsIntent.launchUrl(context, uri);

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
