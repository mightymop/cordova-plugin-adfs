package de.berlin.polizei.oidcsso;

import static de.berlin.polizei.oidcsso.utils.Utils.getDataFromUri;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;

import androidx.appcompat.app.AppCompatActivity;

import java.util.Map;
import java.util.concurrent.ExecutionException;

import de.berlin.polizei.oidcsso.interfaces.TaskResultCallback;
import de.berlin.polizei.oidcsso.tasks.AuthorizeTask;
import de.berlin.polizei.oidcsso.tasks.LoadconfigTask;
import de.berlin.polizei.oidcsso.tasks.LogoutTask;
import de.berlin.polizei.oidcsso.tasks.TokenTask;
import de.berlin.polizei.oidcsso.utils.Utils;

public class OIDCActivity extends AppCompatActivity {

    public final static String ACTION_LOGIN = "de.mopsdom.adfs.LOGIN_START";

    public final static String ACTION_LOGOUT = "de.mopsdom.adfs.LOGOUT_START";

    public final static String TAG = OIDCActivity.class.getSimpleName();

    private Context context;

    private String state=null;


    @Override
    protected void onNewIntent(Intent intent) {

        Log.e(TAG,"onNewIntent!!!!");

        runRedirect(intent,true);

        super.onNewIntent(intent);
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        context = getApplicationContext();

        Log.d(TAG,"onCREATE!!!!");

        if (getIntent().getCategories()!=null&&getIntent().getCategories().contains(Intent.CATEGORY_BROWSABLE)&&getIntent().getAction()!=null&&getIntent().getAction().equalsIgnoreCase(Intent.ACTION_VIEW))
        {
            runRedirect(getIntent(),false);
            return;
        }

        Utils.initSSL();
        if (Utils.getSharedPref(context,context.getString(R.string.configuration_key))==null) {
            LoadconfigTask task = new LoadconfigTask(this, new TaskResultCallback() {
                @Override
                public void onSuccess(String data) {
                    Utils.setSharedPref(context, context.getString(R.string.configuration_key), data);

                    if (getIntent().getAction()!=null&&getIntent().getAction().equalsIgnoreCase(ACTION_LOGIN)) {
                        AuthorizeTask atask = new AuthorizeTask(OIDCActivity.this, new TaskResultCallback() {
                            @Override
                            public void onSuccess(String data) {
                                state = data; //check that the result is from the right request...
                            }

                            @Override
                            public void onError(Exception ex) {
                                Log.e(TAG, ex.getMessage());

                                setResult(RESULT_CANCELED);
                                finish();
                            }
                        });
                        atask.execute();
                    }
                    else
                    if (getIntent().getAction()!=null&&getIntent().getAction().equalsIgnoreCase(ACTION_LOGOUT)) {
                        if (Utils.getCurrentUser(context)!=null) {
                            LogoutTask ltask = new LogoutTask(OIDCActivity.this);
                            ltask.execute();
                            try {
                                Boolean done = ltask.get(); // wait for the AsyncTask to complete and get the result
                                Thread.sleep(1000);
                                Log.d(TAG,"onCREATE B");
                                setResult(RESULT_OK,new Intent());
                                finish();
                                return;
                            } catch (InterruptedException e) {
                                Log.e(TAG,e.getMessage(),e);
                            } catch (ExecutionException e) {
                                Log.e(TAG,e.getMessage(),e);
                            }

                            setResult(RESULT_CANCELED);
                            finish();
                        }
                        else
                        {
                            setResult(RESULT_CANCELED);
                            finish();
                        }
                    }
                    else {

                        setResult(RESULT_CANCELED);
                        finish();
                    }
                }

                @Override
                public void onError(Exception ex) {
                    Log.e(TAG,ex.getMessage());

                    Log.d(TAG,"onCREATE F");
                    setResult(RESULT_CANCELED);
                    finish();
                }
            });
            task.execute();
        }
        else
        {
            if (getIntent().getAction()!=null&&getIntent().getAction().equalsIgnoreCase(ACTION_LOGIN)) {
                AuthorizeTask atask = new AuthorizeTask(OIDCActivity.this, new TaskResultCallback() {

                    @Override
                    public void onSuccess(String data) {
                        Utils.setSharedPref(context,"state",data);
                        state = data; //check that the result is from the right request...
                        Log.d(TAG,"onCREATE H");
                    }

                    @Override
                    public void onError(Exception ex) {
                        Log.e(TAG, ex.getMessage());

                        Log.d(TAG,"onCREATE G");
                        setResult(RESULT_CANCELED);
                        finish();
                    }
                });
                atask.execute();
            }
            else
            if (getIntent().getAction()!=null&&getIntent().getAction().equalsIgnoreCase(ACTION_LOGOUT)) {
                if (Utils.getCurrentUser(context)!=null) {
                    LogoutTask ltask = new LogoutTask(OIDCActivity.this);
                    ltask.execute();
                    try {
                        Boolean done = ltask.get(); // wait for the AsyncTask to complete and get the result
                        Thread.sleep(1000);
                        Log.d(TAG,"onCREATE I");
                        setResult(RESULT_OK,new Intent());
                        finish();
                        return;
                    } catch (InterruptedException e) {
                        Log.e(TAG,e.getMessage(),e);
                    } catch (ExecutionException e) {
                        Log.e(TAG,e.getMessage(),e);
                    }
                    Log.d(TAG,"onCREATE J");
                    setResult(RESULT_CANCELED);
                    finish();
                }
                else
                {
                    Log.d(TAG,"onCREATE K");
                    setResult(RESULT_CANCELED);
                    finish();
                }
            }
            else {
                Log.d(TAG,"onCREATE L");
                setResult(RESULT_CANCELED);
                finish();
            }
        }
    }

    private void runRedirect(Intent intent, boolean fromNewIntent)
    {
        if (intent.getAction()==null||!intent.getAction().equalsIgnoreCase(Intent.ACTION_VIEW)) {
            Log.d(TAG,"runRedirect D "+String.valueOf(fromNewIntent));
            return;
        }

        if (intent.getCategories()!=null&&intent.getCategories().contains(Intent.CATEGORY_BROWSABLE)) {
            Uri redirectUri = intent.getData();

            Map<String,String> results = getDataFromUri(redirectUri);

            // The URL will contain a `code` parameter when the user has been authenticated
            if (results.containsKey("state")) {
                String istate = results.get("state");
                if (state==null)
                {
                    state = Utils.getSharedPref(context,"state");
                }
                if (istate.equalsIgnoreCase(state)) {
                    if (results.containsKey("code")) {
                        String authToken = results.get("code");
                        TokenTask ttask = new TokenTask(context, authToken, new TaskResultCallback() {
                            @Override
                            public void onSuccess(String data) {
                                Utils.createAccount(context,data);
                                Intent intent = new Intent();
                                String id_token = Utils.getStringFromTokenData(context,Utils.getCurrentUser(context),"id_token");
                                String access_token = Utils.getStringFromTokenData(context,Utils.getCurrentUser(context),"access_token");
                                String refresh_token = Utils.getStringFromTokenData(context,Utils.getCurrentUser(context),"refresh_token");
                                intent.putExtra("id_token",id_token);
                                intent.putExtra("access_token",access_token);
                                intent.putExtra("refresh_token",refresh_token);

                                Log.d(TAG,"runRedirect A "+String.valueOf(fromNewIntent));
                                setResult(RESULT_OK,intent);
                                finish();
                            }

                            @Override
                            public void onError(Exception ex) {
                                Log.e(TAG,ex.getMessage());
                                Log.d(TAG,"runRedirect B "+String.valueOf(fromNewIntent));
                                setResult(RESULT_CANCELED);
                                finish();
                            }
                        });
                        ttask.execute();
                    }
                }
            }
        }

        Log.d(TAG,"runRedirect C "+String.valueOf(fromNewIntent));
    }
}