package de.berlin.polizei.oidcsso.tasks;

import android.content.Context;
import android.net.Uri;
import android.os.AsyncTask;
import android.util.Log;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;

import javax.net.ssl.HttpsURLConnection;

import de.berlin.polizei.oidcsso.utils.Utils;

public class BasePostTask extends AsyncTask<String, Void, String> {

    protected Context context;
    protected String resultJson;

    protected String requestUrl;

    protected Exception ex;

    private int countRetry = 0;

    public BasePostTask(Context c){
        context=c;
    }

    public Exception getException()
    {
        return ex;
    }

    private String run(boolean withoutproxy,boolean useip)
    {
        try
        {
            String url = requestUrl;
            Uri requestUri = Uri.parse(requestUrl);
            if (useip) {
                String ip = resolveHost(requestUri);
                if (ip!=null) {
                    url = url.replace(requestUri.getHost(), ip);
                }
            }
            HttpsURLConnection connection = Utils.getConnection(context,useip ? Uri.parse(url) : requestUri,"POST",withoutproxy);

            connection.addRequestProperty("Content-Type", "application/x-www-form-urlencoded");

            String postData = requestUrl.substring(requestUrl.lastIndexOf("?")+1);
            OutputStream outputStream = connection.getOutputStream();
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(outputStream));

            writer.write(postData);
            writer.flush();
            writer.close();

            try {
                int respCode = 0;
                if ((respCode=connection.getResponseCode()) == HttpsURLConnection.HTTP_OK) {
                    InputStream inputStream = connection.getInputStream();
                    BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));

                    StringBuilder result = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        result.append(line);
                    }
                    reader.close();
                    resultJson = result.toString();
                    this.countRetry = 0;
                    return resultJson;
                } else {
                    Log.e(TokenTask.class.getSimpleName(), connection.getResponseMessage() + ": " + String.valueOf(respCode));
                    this.countRetry = 0;
                    return null;
                }
            }
            finally {
                connection.disconnect();
            }

        }
        catch (Exception e)
        {
            Log.e(BasePostTask.class.getSimpleName(),e.getMessage(),e);

            if ((e instanceof SocketTimeoutException || e instanceof ConnectException) && countRetry < 5)
            {
                countRetry++;
                return run(withoutproxy, withoutproxy);
            }

            if (!withoutproxy)
            {
                return run(true, e instanceof UnknownHostException ? true : false);
            }
            return null;
        }
    }

    private String resolveHost(Uri uri)
    {
        try {
            InetAddress[] addresses = InetAddress.getAllByName(uri.getHost());

            for (InetAddress address : addresses) {
                System.out.println("Host: " + uri.getHost());
                System.out.println("IP Address: " + address.getHostAddress());
                System.out.println("Canonical Hostname: " + address.getCanonicalHostName());
                System.out.println();
                return address.getHostAddress();
            }
            return null;
        } catch (UnknownHostException e) {
            Log.e("BasePostTask",e.getMessage(),e);
            return null;
        }
    }

    @Override
    protected String doInBackground(String... args) {
        return run(false,false);
    }
}
