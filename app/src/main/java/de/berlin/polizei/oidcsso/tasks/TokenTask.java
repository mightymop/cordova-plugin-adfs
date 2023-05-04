package de.berlin.polizei.oidcsso.tasks;

import android.content.Context;
import android.util.Log;

import org.json.JSONObject;

import de.berlin.polizei.oidcsso.R;
import de.berlin.polizei.oidcsso.interfaces.TaskResultCallback;
import de.berlin.polizei.oidcsso.utils.Utils;

public class TokenTask extends BasePostTask {

    private String code;
    private TaskResultCallback callback;

    public TokenTask(Context c, String code, TaskResultCallback cb){
        super(c);
        this.code=code;
        this.callback=cb;
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
    protected void onPostExecute(String success) {
        if (success!=null)
        {
            callback.onSuccess(resultJson);
        }
        else
        {
            callback.onError(ex);
        }
    }

}
