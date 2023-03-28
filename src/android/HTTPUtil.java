package de.mopsdom.adfs.http;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.security.cert.CertificateException;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import de.mopsdom.adfs.utils.Utils;
import okhttp3.Cache;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class HTTPUtil {

  private OkHttpClient httpClient;
  private OkCookieJar cookies;

  public HTTPUtil(Context ctx){
    this.httpClient = createClient(ctx);
  }

  public OkHttpClient getClient() {
      return this.httpClient;
  }
  public OkHttpClient createClient(Context ctx) {

    if (httpClient != null)
      return httpClient;

    String strproxy = Utils.getProxy(ctx);

    Proxy proxy;
    if (strproxy!=null&&!strproxy.isEmpty()) {
      if (strproxy.contains(":")) {
        proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress(strproxy.split(":")[0], Integer.parseInt(strproxy.split(":")[1])));
      } else {
        proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress(strproxy, 80));
      }
    }
    else {
      proxy = Proxy.NO_PROXY;
    }

    OkHttpClient.Builder builder = new OkHttpClient.Builder();
    builder.hostnameVerifier(new HostnameVerifier() {
      @Override
      public boolean verify(String hostname, SSLSession session) {
        // ignore hostname verification
        return true;
      }
    });
    final TrustManager[] trustAllCerts = new TrustManager[]{new X509TrustManager() {
      @Override
      public void checkClientTrusted(java.security.cert.X509Certificate[] chain, String authType) throws CertificateException {
      }

      @Override
      public void checkServerTrusted(java.security.cert.X509Certificate[] chain, String authType) throws CertificateException {
      }

      @Override
      public java.security.cert.X509Certificate[] getAcceptedIssuers() {
        return new java.security.cert.X509Certificate[]{};
      }
    }};
    try {
      SSLContext sslContext = SSLContext.getInstance("SSL");
      sslContext.init(null, trustAllCerts, new java.security.SecureRandom());
      builder.sslSocketFactory(sslContext.getSocketFactory(), (X509TrustManager) trustAllCerts[0]);
    } catch (Exception e) {
      Log.e(getClass().getCanonicalName(),e.getMessage());
    }

    builder.proxy(proxy);
    builder.connectTimeout(5, TimeUnit.SECONDS);
    builder.readTimeout(5, TimeUnit.SECONDS);
    if (cookies == null) {
      cookies = new OkCookieJar();
    }
    builder.cookieJar(cookies);

    Cache appCache = new Cache(new File("cacheDir", "okhttpcache"), 10 * 1024 * 1024);
    builder.cache(appCache);

    builder.followRedirects(true);
    builder.followSslRedirects(true);

    httpClient = builder.build();

    return httpClient;
  }

  public void clearClient() {

    try {
      httpClient.dispatcher().cancelAll();
    } finally {
      cookies.clear();
      Cache cache = httpClient.cache();
      if (cache != null) {
        try {
          cache.close();
          httpClient.dispatcher().executorService().shutdown();
        } catch (IOException e) {
          Log.e(getClass().getCanonicalName(),e.getMessage());
        }
      }
    }
  }


  public JSONObject retrieveDataJson(String url, String method) throws HttpException {
    JSONObject result = null;

    try {
      String res = retrieveData(url, method);
      result = new JSONObject(res);
    } catch (Exception e) {
      result = new JSONObject();
      result.put("error", "could not load data: " + e.getMessage());
    } finally {
      return result;
    }
  }


  public String retrieveData(String url, String method) throws HttpException {
    Response response;

    Request.Builder builder = new Request.Builder();

    if (method.equalsIgnoreCase("post")) {
      Uri uri = Uri.parse(url);
      builder.post(RequestBody.create(uri.getQuery(), MediaType.parse("application/x-www-form-urlencoded")));
      url = url.replace(uri.getQuery(), "");
    }

    builder.url(url);

    try {
      response = this.getClient().newCall(builder.build()).execute();
      if (response.isSuccessful()) {
        return new String(response.body().string());
      }
      else
      {
        throw new HttpException(response.code(),response.message(),response.body().string());
      }
    }
    catch (Exception e)
    {
       throw new HttpException(e.getMessage());
    }
    finally {
      this.clearClient();
    }
  }

}
