package de.berlin.polizei.oidcsso.tasks;

import android.content.Context;
import android.net.Uri;
import android.os.AsyncTask;
import android.util.Log;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.UUID;

import javax.net.ssl.HttpsURLConnection;

import de.berlin.polizei.oidcsso.R;
import de.berlin.polizei.oidcsso.interfaces.TaskResultCallback;
import de.berlin.polizei.oidcsso.utils.Utils;

public class TokenTask extends AsyncTask<String, Void, Boolean> {

    private Context context;
    private String requestUrl;
    private String resultJson;
    private TaskResultCallback callback;
    private String code;

    private Exception ex;

    public TokenTask(Context c, String code, TaskResultCallback cb){
        context=c;
        callback=cb;
        this.code=code;
    }

    @Override
    protected void onPreExecute() {

        boolean mapswitch = Utils.getSharedPrefBoolean(context,context.getString(R.string.toggle_map_key));
        String baseUrl = Utils.getSharedPref(context,context.getString(R.string.baseurl_key),!mapswitch?context.getString(R.string.default_baseurl_map):context.getString(R.string.default_baseurl_poldom));
        String client_id = Utils.getSharedPref(context,context.getString(R.string.client_id_key),!mapswitch?context.getString(R.string.default_client_id_map):context.getString(R.string.default_client_id_poldom));

        String config = Utils.getSharedPref(context,context.getString(R.string.configuration_key));
        String redirect_uri = Utils.getSharedPref(context,context.getString(R.string.redirect_url_key));

        try
        {
            JSONObject json = new JSONObject(config);
            requestUrl = json.getString("token_endpoint");
            if (requestUrl.endsWith("/"))
            {
                requestUrl=requestUrl.substring(0,requestUrl.length()-1);
            }
        }
        catch (Exception e)
        {
            Log.e(this.getClass().getSimpleName(),e.getMessage());
            requestUrl=baseUrl+"/oauth2/token";
        }

        requestUrl+="?client_id="+client_id;
        requestUrl+="&code="+code;
        requestUrl+="&grant_type=authorization_code";
        requestUrl+="&redirect_uri="+redirect_uri.replace("*",context.getPackageName());
    }

    @Override
    protected void onPostExecute(Boolean success) {
        if (success)
        {
            callback.onSuccess(this.resultJson);
        }
        else
        {
            callback.onError(ex);
        }
    }

    private Boolean run(boolean withoutproxy)
    {
        try
        {
            HttpsURLConnection connection = Utils.getConnection(context,Uri.parse(requestUrl),"POST",withoutproxy);
            connection.addRequestProperty("Content-Type", "application/x-www-form-urlencoded");

            String postData = requestUrl.substring(requestUrl.lastIndexOf("?")+1);
            OutputStream outputStream = connection.getOutputStream();
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(outputStream));

            writer.write(postData);
            writer.flush();

            InputStream inputStream = connection.getInputStream();
            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
            StringBuilder result = new StringBuilder();

            String line;
            while ((line = reader.readLine()) != null) {
                result.append(line);
            }

            // Disconnect the connection
            connection.disconnect();

            if (connection.getResponseCode()<300) {
                // Return the result as a string
                this.resultJson = result.toString();
                return true;
            }
            else
            {
                this.ex = new Exception(connection.getResponseMessage());
                Log.e(TokenTask.class.getSimpleName(),connection.getResponseMessage()+": "+result.toString());
                return false;
            }
        }
        catch (Exception e)
        {
            this.resultJson = null;
            this.ex=e;
            Log.e(TokenTask.class.getSimpleName(),e.getMessage(),e);
            if (!withoutproxy)
            {
                this.ex=null;
                return run(true);
            }

            return false;
        }
    }

    @Override
    protected Boolean doInBackground(String... args) {

        return run(false);
    }
}
