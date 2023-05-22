package de.berlin.polizei.oidcsso.tasks;

import android.content.Context;
import android.util.Log;

import org.json.JSONObject;

import de.berlin.polizei.oidcsso.R;
import de.berlin.polizei.oidcsso.utils.Utils;

public class RefreshTokenTask extends BasePostTask {

    private String refresh_token;

    public RefreshTokenTask(Context c, String refresh_token){
        super(c);;
        this.refresh_token=refresh_token;
    }

    @Override
    protected void onPostExecute(String result)
    {
        if (result!=null)
        {
            Utils.mergeData(context, Utils.getCurrentUser(context),result);
        }
    }

    @Override
    protected void onPreExecute() {

        boolean mapswitch = Utils.getSharedPrefBoolean(context,context.getString(R.string.toggle_map_key));
        String baseUrl = Utils.getSharedPref(context,context.getString(R.string.baseurl_key),!mapswitch?context.getString(R.string.default_baseurl_map):context.getString(R.string.default_baseurl_poldom));
        String client_id = Utils.getSharedPref(context,context.getString(R.string.client_id_key),!mapswitch?context.getString(R.string.default_client_id_map):context.getString(R.string.default_client_id_poldom));

        String config = Utils.getSharedPref(context,context.getString(R.string.configuration_key));

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
        requestUrl+="&refresh_token="+refresh_token;
        requestUrl+="&scope=openid%20email%20profile";
        requestUrl+="&grant_type=refresh_token";
    }

}
