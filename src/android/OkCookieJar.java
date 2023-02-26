package de.mopsdom.adfs.http;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import okhttp3.Cookie;
import okhttp3.HttpUrl;

public class OkCookieJar implements okhttp3.CookieJar {

  private final Map<String, List<Cookie>> cookieStore = new HashMap<>();

  @Override
  public void saveFromResponse(HttpUrl url, List<Cookie> cookies) {
    cookieStore.put(url.host(), cookies);
  }

  public void clear() {
    cookieStore.clear();
  }

  @Override
  public List<Cookie> loadForRequest(HttpUrl url) {
    List<Cookie> cookies = cookieStore.get(url.host());
    return cookies != null ? cookies : new ArrayList<>();
  }
}
