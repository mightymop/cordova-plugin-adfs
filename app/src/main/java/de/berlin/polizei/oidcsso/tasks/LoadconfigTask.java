package de.berlin.polizei.oidcsso.tasks;

import static de.berlin.polizei.oidcsso.authenticator.ADFSAuthenticator.PUBLIC_TOKEN_KEYS;

import android.content.Context;
import android.net.Uri;
import android.os.AsyncTask;
import android.util.Log;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.ConnectException;
import java.net.SocketTimeoutException;

import javax.net.ssl.HttpsURLConnection;

import de.berlin.polizei.oidcsso.R;
import de.berlin.polizei.oidcsso.interfaces.TaskResultCallback;
import de.berlin.polizei.oidcsso.utils.Utils;

public class LoadconfigTask extends AsyncTask<String, Void, Boolean> {

    private Context context;
    private String configJson;

    private TaskResultCallback callback;

    private Exception ex;

    private int countRetry = 0;

    public LoadconfigTask(Context c, TaskResultCallback cb){
        context=c;
        callback=cb;
    }

    @Override
    protected void onPostExecute(Boolean success) {
        if (success)
        {
            callback.onSuccess(this.configJson);
        }
        else
        {
            callback.onError(ex);
        }
    }

    private Boolean run(String configUrl, boolean withProxy)
    {
        try
        {
            HttpsURLConnection connection = Utils.getConnection(context,Uri.parse(configUrl),"GET",withProxy);
            connection.connect();

            InputStream inputStream = connection.getInputStream();
            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
            StringBuilder result = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                result.append(line);
            }

            // Disconnect the connection
            connection.disconnect();

            // Return the result as a string
            this.configJson = result.toString();
            this.countRetry = 0;

            JSONObject localKeys = Utils.getPublicKeys(context);
            if (localKeys!=null) {
                Log.i("LoadconfigTask","PUBLIC KEYS GELADEN!!!");
                Utils.setSharedPref(context,PUBLIC_TOKEN_KEYS,localKeys.toString());
            }
            else {
                Log.e("LoadconfigTask","COULD NOT GET PUBLIC KEYS!!!");
            }

            return true;
        }
        catch (Exception e)
        {
            if ((e instanceof SocketTimeoutException || e instanceof ConnectException) && countRetry < 5)
            {
                countRetry++;
                return run( configUrl,  withProxy);
            }

            this.configJson = null;
            this.ex=e;
            Log.e(LoadconfigTask.class.getSimpleName(),e.getMessage(),e);
            if (!withProxy) {
                this.ex=null;
                return run(configUrl, true);
            }
            return false;
        }
    }

    @Override
    protected Boolean doInBackground(String... args) {
        boolean mapswitch = Utils.getSharedPrefBoolean(context,context.getString(R.string.toggle_map_key));
        String baseUrl = Utils.getSharedPref(context,context.getString(R.string.baseurl_key),!mapswitch?context.getString(R.string.default_baseurl_map):context.getString(R.string.default_baseurl_poldom));
        String configUrl = baseUrl+"/.well-known/openid-configuration";

        return run(configUrl,false);
    }
}
