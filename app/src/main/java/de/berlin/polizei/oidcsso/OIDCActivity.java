package de.berlin.polizei.oidcsso;

import static de.berlin.polizei.oidcsso.utils.Utils.getDataFromUri;

import android.accounts.Account;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
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

    private boolean isTest;
    private boolean fromNoti;

    private AuthorizeTask atask;

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

        isTest = getIntent().hasExtra("test")&&getIntent().getBooleanExtra("test",false)==true?true:false;
        fromNoti = getIntent().hasExtra("noti")&&getIntent().getBooleanExtra("noti",false)==true?true:false;

        if (fromNoti&&getIntent().getCategories()!=null&&getIntent().getCategories().contains(Intent.CATEGORY_DEFAULT))
        {
            setContentView(R.layout.activity_main);

            Timer t = new Timer();
            t.schedule(new TimerTask() {
                @Override
                public void run() {
                    Intent intent = new Intent(Intent.ACTION_MAIN);
                    intent.addCategory(Intent.CATEGORY_HOME);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(intent);
                    finish();
                }
            },2500);

            return;
        }

        if (getIntent().getCategories()!=null&&getIntent().getCategories().contains(Intent.CATEGORY_BROWSABLE)&&getIntent().getAction()!=null&&getIntent().getAction().equalsIgnoreCase(Intent.ACTION_VIEW))
        {
            runRedirect(getIntent(),false);
            return;
        }

        Utils.initSSL();
        String configStr = Utils.getSharedPref(context,context.getString(R.string.configuration_key));
        if (configStr==null) {
            LoadconfigTask task = new LoadconfigTask(this, new TaskResultCallback() {
                @Override
                public void onSuccess(String data) {
                    Utils.setSharedPref(context, context.getString(R.string.configuration_key), data);

                    if (getIntent().getAction()!=null&&getIntent().getAction().equalsIgnoreCase(ACTION_LOGIN)) {
                        atask = new AuthorizeTask(OIDCActivity.this,fromNoti, new TaskResultCallback() {
                            @Override
                            public void onSuccess(String data) {
                                state = data; //check that the result is from the right request...
                            }

                            @Override
                            public void onError(Exception ex) {
                                Log.e(TAG, ex.getMessage());

                                setResult(RESULT_CANCELED);
                                finish();
                                if (isTest)
                                {
                                    Intent intent = new Intent(context, SettingsActivity.class);
                                    intent.putExtra("result",RESULT_CANCELED);
                                    startActivity(intent);
                                }
                                if (fromNoti)
                                {
                                   myFinish();
                                }
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

                                setResult(RESULT_OK,new Intent());
                                finish();
                                if (isTest)
                                {
                                   Intent intent = new Intent(context, SettingsActivity.class);
                                   intent.putExtra("result",RESULT_OK);
                                   startActivity(intent);
                                }
                                if (fromNoti)
                                {
                                    myFinish();
                                }

                                return;
                            } catch (InterruptedException e) {
                                Log.e(TAG,e.getMessage(),e);
                            } catch (ExecutionException e) {
                                Log.e(TAG,e.getMessage(),e);
                            }

                            setResult(RESULT_CANCELED);
                            finish();
                            if (isTest)
                            {
                                Intent intent = new Intent(context, SettingsActivity.class);
                                intent.putExtra("result",RESULT_CANCELED);
                                startActivity(intent);
                            }
                            if (fromNoti)
                            {
                                myFinish();
                            }
                        }
                        else
                        {
                            setResult(RESULT_CANCELED);
                            finish();
                            if (isTest)
                            {
                                Intent intent = new Intent(context, SettingsActivity.class);
                                intent.putExtra("result",RESULT_CANCELED);
                                startActivity(intent);
                            }
                            if (fromNoti)
                            {
                                myFinish();
                            }
                        }
                    }
                    else {
                        setResult(RESULT_CANCELED);
                        finish();
                        if (isTest)
                        {
                            Intent intent = new Intent(context, SettingsActivity.class);
                            intent.putExtra("result",RESULT_CANCELED);
                            startActivity(intent);
                        }
                        if (fromNoti)
                        {
                            myFinish();
                        }
                    }
                }

                @Override
                public void onError(Exception ex) {
                    Log.e(TAG,ex.getMessage());

                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(context,ex.getMessage(),Toast.LENGTH_LONG).show();
                        }
                    });

                    setResult(RESULT_CANCELED);
                    finish();
                    if (isTest)
                    {
                        Intent intent = new Intent(context, SettingsActivity.class);
                        intent.putExtra("result",RESULT_CANCELED);
                        startActivity(intent);
                    }
                    if (fromNoti)
                    {
                        myFinish();
                    }
                }
            });
            task.execute();
        }
        else
        {
            if (getIntent().getAction()!=null&&getIntent().getAction().equalsIgnoreCase(ACTION_LOGIN)) {
                atask = new AuthorizeTask(OIDCActivity.this,fromNoti, new TaskResultCallback() {

                    @Override
                    public void onSuccess(String data) {
                        Utils.setSharedPref(context,"state",data);
                        state = data; //check that the result is from the right request...
                    }

                    @Override
                    public void onError(Exception ex) {
                        Log.e(TAG, ex.getMessage());

                        setResult(RESULT_CANCELED);
                        finish();
                        if (isTest)
                        {
                            Intent intent = new Intent(context, SettingsActivity.class);
                            intent.putExtra("result",RESULT_CANCELED);
                            startActivity(intent);
                        }
                        if (fromNoti)
                        {
                            myFinish();
                        }
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

                        setResult(RESULT_OK,new Intent());
                        finish();
                        if (isTest)
                        {
                            Intent intent = new Intent(context, SettingsActivity.class);
                            intent.putExtra("result",RESULT_OK);
                            startActivity(intent);
                        }
                        if (fromNoti)
                        {
                            myFinish();
                        }
                        return;
                    } catch (InterruptedException e) {
                        Log.e(TAG,e.getMessage(),e);
                    } catch (ExecutionException e) {
                        Log.e(TAG,e.getMessage(),e);
                    }
                    setResult(RESULT_CANCELED);
                    finish();
                    if (isTest)
                    {
                        Intent intent = new Intent(context, SettingsActivity.class);
                        intent.putExtra("result",RESULT_CANCELED);
                        startActivity(intent);
                    }
                    if (fromNoti)
                    {
                        myFinish();
                    }
                }
                else
                {
                    setResult(RESULT_CANCELED);
                    finish();
                    if (isTest)
                    {
                        Intent intent = new Intent(context, SettingsActivity.class);
                        intent.putExtra("result",RESULT_CANCELED);
                        startActivity(intent);
                    }
                    if (fromNoti)
                    {
                        myFinish();
                    }
                }
            }
            else {
                setResult(RESULT_CANCELED);
                finish();
                if (isTest)
                {
                    Intent intent = new Intent(context, SettingsActivity.class);
                    intent.putExtra("result",RESULT_CANCELED);
                    startActivity(intent);
                }
                if (fromNoti)
                {
                   myFinish();
                }
            }
        }
    }

    private void myFinish() {
        Intent i1 = new Intent(getApplicationContext(),OIDCActivity.class);
        i1.addCategory(Intent.CATEGORY_DEFAULT);
        i1.putExtra("noti",true);
        i1.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY);
        i1.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);
        i1.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        i1.addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
        i1.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(i1);
    }

    private void runRedirect(Intent intent, boolean fromNewIntent)
    {
        if (intent.getAction()==null||!intent.getAction().equalsIgnoreCase(Intent.ACTION_VIEW)) {
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
                                Account acc = Utils.getCurrentUser(context);

                                Intent intent = null;

                                if (!isTest) {
                                    intent = new Intent();
                                } else {
                                    intent = new Intent(context, SettingsActivity.class);
                                    intent.putExtra("result", RESULT_OK);
                                }

                                intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);

                                if (acc!=null) {
                                    String id_token = Utils.getStringFromTokenData(context, Utils.getCurrentUser(context), "id_token");
                                    String access_token = Utils.getStringFromTokenData(context, Utils.getCurrentUser(context), "access_token");
                                    String refresh_token = Utils.getStringFromTokenData(context, Utils.getCurrentUser(context), "refresh_token");
                                    intent.putExtra("id_token", id_token);
                                    intent.putExtra("access_token", access_token);
                                    intent.putExtra("refresh_token", refresh_token);
                                    setResult(RESULT_OK, intent);
                                }
                                else
                                {
                                    setResult(RESULT_CANCELED, intent);
                                }

                                finish();
                                if (isTest) {
                                    startActivity(intent);
                                }
                                if (fromNoti)
                                {
                                    Intent i1 = new Intent(getApplicationContext(),OIDCActivity.class);
                                    i1.addCategory(Intent.CATEGORY_DEFAULT);
                                    i1.putExtra("noti",true);
                                    startActivity(i1);
                                }
                            }

                            @Override
                            public void onError(Exception ex) {
                                Log.e(TAG,ex.getMessage());
                                setResult(RESULT_CANCELED);
                                finish();
                                if (isTest)
                                {
                                    Intent intent = new Intent(context, SettingsActivity.class);
                                    intent.putExtra("result",RESULT_CANCELED);
                                    startActivity(intent);
                                }
                                if (fromNoti)
                                {
                                    Intent i1 = new Intent(getApplicationContext(),OIDCActivity.class);
                                    i1.addCategory(Intent.CATEGORY_DEFAULT);
                                    i1.putExtra("noti",true);
                                    startActivity(i1);
                                }
                            }
                        });
                        ttask.execute();
                    }
                }
            }
        }
    }
}