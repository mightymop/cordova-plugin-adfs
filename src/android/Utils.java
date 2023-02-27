package de.mopsdom.adfs.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.net.Uri;
import android.preference.PreferenceManager;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.InputStream;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

public class Utils {
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
    return sharedPref.getString(key, null);
  }

  public static void setSharedPref(Context ctx, String key, String val) {
    SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(ctx);

    SharedPreferences.Editor editor = sharedPref.edit();

    editor.putString(key, val);

    editor.apply();
  }

  public static Map<String, String> getDataFromIntent(Uri uri) {

    String[] parts = uri.getFragment().split("&");
    Map<String, String> map = new HashMap<String, String>();
    for (int n = 0; n < parts.length; n++) {
      try {
        map.put(parts[n].split("=")[0], parts[n].split("=")[1]);
      } catch (Exception e) {
        Log.w("cordova-plugin-adfs", e.getMessage());
      }
    }

    return map;
  }

  public static String getVal(Context context, String val) {
    String baseurl = Utils.getSharedPref(context, val);
    if (baseurl != null)
      return baseurl;

    JSONObject res = Utils.getConfigFile(context);
    if (res != null) {
      try {
        if (res.has("adfs") && res.getJSONObject("adfs").has(val)) {
          return res.getJSONObject("adfs").getString(val);
        }
      } catch (Exception e) {
        Log.e("cordova-plugin-adfs", e.getMessage());
      }
    }

    String resourceName = val;
    String resourceType = "string";
    String packageName = context.getPackageName();
    int resourceId = context.getResources().getIdentifier(resourceName, resourceType, packageName);
    String resourceValue = context.getString(resourceId);
    return resourceValue;
  }

  public static int getLogoutIconIdentifier(Context context)
  {
    String resourceType = "drawable";
    String packageName = context.getPackageName();
    return context.getResources().getIdentifier("baseline_lock_24", resourceType, packageName);
  }

  public static String getUserIDClaim(Context context) {
    return getVal(context, "key_userid");
  }

  public static String getADFSBaseUrl(Context context) {
    return getVal(context, "baseurl");
  }

  public static JSONObject getConfigFile(Context ctx) {

    try {
      int rawDevel = ctx.getResources().getIdentifier("development", "raw", ctx.getPackageName());
      int rawProd = ctx.getResources().getIdentifier("production", "raw", ctx.getPackageName());

      boolean isDebuggable = (0 != (ctx.getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE));
      InputStream raw = null;
      if (rawDevel!=0||rawProd!=0) {
        raw = ctx.getResources().openRawResource(isDebuggable ? rawDevel : rawProd);
      }
      else
      {
        raw =  ctx.getAssets().open(isDebuggable?"www/assets/config/development.json":"www/assets/config/production.json");
      }

      int size = raw.available();
      byte[] buffer = new byte[size];
      raw.read(buffer);
      raw.close();
      String result = new String(buffer);
      return new JSONObject(result);

    } catch (Exception e) {
      Log.e("cordova-plugin-odic", "getConfigFile: " + e.getMessage());
      return null;
    }
  }

  public static JSONObject getPayload(String token) {

    String[] parts = token.split("\\.");
    String decodedString = decodeBase64(parts[1]);

    JSONObject payload = null;
    try {
      payload = new JSONObject(decodedString);
    } catch (JSONException e) {
      return null;
    }

    return payload;
  }

}
