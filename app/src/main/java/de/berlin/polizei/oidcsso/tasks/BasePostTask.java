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

import javax.net.ssl.HttpsURLConnection;

import de.berlin.polizei.oidcsso.utils.Utils;

public class BasePostTask extends AsyncTask<String, Void, String> {

    protected Context context;
    protected String resultJson;

    protected String requestUrl;

    protected Exception ex;

    public BasePostTask(Context c){
        context=c;
    }

    public Exception getException()
    {
        return ex;
    }

    private String run(boolean withoutproxy)
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
                    return resultJson;
                } else {
                    Log.e(TokenTask.class.getSimpleName(), connection.getResponseMessage() + ": " + String.valueOf(respCode));
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

            if (!withoutproxy)
            {
                return run(true);
            }
            return null;
        }
    }

    @Override
    protected String doInBackground(String... args) {
        return run(false);
    }
}
