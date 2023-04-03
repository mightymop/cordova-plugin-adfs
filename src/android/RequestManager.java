package de.mopsdom.adfs.request;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.net.NetworkRequest;
import android.net.Uri;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;

import de.mopsdom.adfs.http.HTTPUtil;
import de.mopsdom.adfs.http.HttpException;
import de.mopsdom.adfs.utils.Utils;

public class RequestManager {

  public static final String KEY_IS_NEW_ACCOUNT = "NEW_ACCOUNT";

  public static final String TOKEN_TYPE_ID = "TOKEN_TYPE_ID";
  public static final String TOKEN_TYPE_ACCESS = "TOKEN_TYPE_ACCESS";
  public static final String TOKEN_TYPE_REFRESH = "TOKEN_TYPE_REFRESH";

  public static final String REFRESH_TOKEN_EXP = "refresh_token_expires_in";

  public static final String PUBLIC_TOKEN_KEYS = "AUTH_KEYS";

  public final static String ACCOUNT_STATE_KEY = "STATUS";
  public final static String REDIRECT_URI = "adfs://*.adfs/redirect";
  private HTTPUtil http;

  private Context context;

  private JSONObject configuration;

  public RequestManager(Context ctx){
    this.http = new HTTPUtil(ctx);
    this.context=ctx;
    try{
      String config = Utils.getSharedPref(context,"configuration");
      if (config!=null)
      {
        configuration = new JSONObject(config);
      }
    }
    catch (Exception e)
    {
      Log.e("cordova-plugin-adfs",e.getMessage());
    }
  }

  public JSONObject load_config() throws HttpException {

    String baseurl = Utils.getADFSBaseUrl(context);
    Uri config_uri = Uri.parse(baseurl + "/.well-known/openid-configuration");

    return this.http.retrieveDataJson(config_uri.toString(), "get");
  }

  public JSONObject loadKeys() throws HttpException {

    if (configuration==null)
    {
      configuration = load_config();
      if (!configuration.has("error")) {
        Utils.setSharedPref(context, "configuration", configuration.toString());
      }
      else {
        try {
          throw new HttpException(500, "error", configuration.getString("error"));
        }
        catch (Exception e)
        {
          throw new HttpException(500, "error", "ADFS Konfuguration konnte nicht geladen werden.");
        }
      }
    }

    String url;
    try {
       url = configuration.getString("jwks_uri");
    }
    catch (Exception e)
    {
      Log.e("cordova-plugin-adfs",e.getMessage());
      return null;
    }

    return this.http.retrieveDataJson(url, "get");
  }

  public boolean isServerReachable() {
    try {
      if (configuration==null)
      {
        configuration = load_config();
        if (!configuration.has("error")) {
          Utils.setSharedPref(context, "configuration", configuration.toString());
        }
        else {
          try {
            throw new HttpException(500, "error", configuration.getString("error"));
          }
          catch (Exception e)
          {
            throw new HttpException(500, "error", "ADFS Konfuguration konnte nicht geladen werden.");
          }
        }
      }

      URL url = new URL(configuration.getString("token_endpoint"));
      HttpURLConnection connection = (HttpURLConnection) url.openConnection();
      connection.setRequestMethod("HEAD");
      int responseCode = connection.getResponseCode();
      return responseCode == HttpURLConnection.HTTP_OK;
    } catch (Exception e) {
      return false;
    }
  }

  public String getAccesTokenTrustIssuer() {
    if (configuration==null)
    {
      try {
        configuration = load_config();
        if (!configuration.has("error")) {
          Utils.setSharedPref(context, "configuration", configuration.toString());
        }
        else {
          try {
            throw new HttpException(500, "error", configuration.getString("error"));
          }
          catch (Exception e)
          {
            throw new HttpException(500, "error", "ADFS Konfuguration konnte nicht geladen werden.");
          }
        }
      }
      catch (Exception e)
      {
        Log.w("cordova-plugin-adfs",e.getMessage());
        return Utils.getADFSBaseUrl(context)+"/services/trust";
      }
    }

    try {
      return configuration.getString("access_token_issuer");
    }
    catch (Exception e)
    {
      Log.w("cordova-plugin-adfs",e.getMessage());
      return Utils.getADFSBaseUrl(context)+"/services/trust";
    }
  }

  public JSONObject access_token(String code,
                                 String client_id,
                                 String redirect_uri,
                                 String grant_type) throws HttpException, JSONException {

    if (configuration==null)
    {
      configuration = load_config();
      if (!configuration.has("error")) {
        Utils.setSharedPref(context, "configuration", configuration.toString());
      }
      else {
        try {
          throw new HttpException(500, "error", configuration.getString("error"));
        }
        catch (Exception e)
        {
          throw new HttpException(500, "error", "ADFS Konfuguration konnte nicht geladen werden.");
        }
      }
    }

    Uri token_endpoint = Uri.parse(configuration.getString("token_endpoint"));

    String token_request_uri = token_endpoint.toString() + "?";
    token_request_uri += "client_id=" + client_id;
    token_request_uri += "&redirect_uri=" + redirect_uri;
    token_request_uri += "&grant_type=" + grant_type;
    token_request_uri += "&code=" + code;

    return this.http.retrieveDataJson(token_request_uri, "post");
  }

  public String refresh_token(String client_id,
                              String refresh_token,
                              String grant_type) throws HttpException, JSONException {


      if (configuration==null)
      {
        configuration = load_config();
        if (!configuration.has("error")) {
          Utils.setSharedPref(context, "configuration", configuration.toString());
        }
        else {
          try {
            throw new HttpException(500, "error", configuration.getString("error"));
          }
          catch (Exception e)
          {
            throw new HttpException(500, "error", "ADFS Konfuguration konnte nicht geladen werden.");
          }
        }
      }
      Uri token_endpoint = Uri.parse(configuration.getString("token_endpoint"));

      String token_request_uri = token_endpoint.toString() + "?";
      token_request_uri += "client_id=" + client_id;
      token_request_uri += "&refresh_token=" + refresh_token;
      token_request_uri += "&grant_type=" + grant_type;

      return this.http.retrieveData(token_request_uri, "post");
  }


}
