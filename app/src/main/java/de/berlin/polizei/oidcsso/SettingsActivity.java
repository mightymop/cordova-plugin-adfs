package de.berlin.polizei.oidcsso;

import static de.berlin.polizei.oidcsso.OIDCActivity.ACTION_LOGIN;
import static de.berlin.polizei.oidcsso.OIDCActivity.ACTION_LOGOUT;
import static de.berlin.polizei.oidcsso.authenticator.ADFSAuthenticator.CHANNEL_ID;
import static de.berlin.polizei.oidcsso.authenticator.ADFSAuthenticator.TOKEN_TYPE_ACCESS;
import static de.berlin.polizei.oidcsso.authenticator.ADFSAuthenticator.TOKEN_TYPE_ID;
import static de.berlin.polizei.oidcsso.authenticator.ADFSAuthenticator.TOKEN_TYPE_REFRESH;
import static de.berlin.polizei.oidcsso.utils.Utils.outputResponse;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.AccountManagerCallback;
import android.accounts.AccountManagerFuture;
import android.accounts.AuthenticatorException;
import android.accounts.OperationCanceledException;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.text.InputType;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.NotificationManagerCompat;
import androidx.preference.EditTextPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

import org.json.JSONObject;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.List;

import de.berlin.polizei.oidcsso.authenticator.ADFSAuthenticator;
import de.berlin.polizei.oidcsso.interfaces.ConfirmDialogListener;
import de.berlin.polizei.oidcsso.interfaces.InitCallback;
import de.berlin.polizei.oidcsso.interfaces.TaskResultCallback;
import de.berlin.polizei.oidcsso.tasks.LoadconfigTask;
import de.berlin.polizei.oidcsso.utils.Utils;

public class SettingsActivity extends AppCompatActivity {

    public final static String TAG = SettingsActivity.class.getSimpleName();

    private Button btnLogin;
    private Button btnRefresh;
    private Button btnLogout;

    private Button btnTest;

    private ActivityResultLauncher<Intent> startForResultLauncher;
    //private ActivityResultLauncher<Intent> startForResultLauncherLogin;
   // private ActivityResultLauncher<Intent> startForResultLauncherLogout;

    private Context context;

    private Handler mHandlerCountdown;
    private TextView mTextViewCountdown;
    private TextView mTextViewResult;
    private TextView mTextViewid;
    private TextView mTextViewaccess;
    private TextView mTextViewrefresh;
    private int mCounter = 0;

    private Window window;

    private boolean first = true;
    private boolean activeTest = false;
    private int mInterval = 1000; // Zeitintervall in Millisekunden


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        context = this;
        setContentView(R.layout.settings_activity);
        window = getWindow();
        mHandlerCountdown = new Handler();
        mTextViewCountdown = (TextView)findViewById(R.id.countdown);
        mTextViewResult = (TextView) findViewById(R.id.testresult);
        mTextViewid = (TextView) findViewById(R.id.testid);
        mTextViewaccess = (TextView)findViewById(R.id.testaccess);
        mTextViewrefresh = (TextView)findViewById(R.id.testrefresh);

        if (savedInstanceState == null)
        {
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.settings, new SettingsFragment())
                    .commit();
        }
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }

        initActivityResult();

        if (!checkNotificationChannelEnabled()) {
            openNotiSettings();
        }

        initAppAuth(new InitCallback() {
            @Override
            public void onInit() {
                initButtons();

                Account acc = Utils.getCurrentUser(context);
                if (acc!=null)
                {
                    btnLogout.setEnabled(true);
                    btnRefresh.setEnabled(true);
                    btnLogin.setEnabled(false);
                }
                else {
                    btnLogin.setEnabled(true);
                    btnRefresh.setEnabled(false);
                    btnLogout.setEnabled(false);
                }
            }
        });
    }

    private void initActivityResult(){

        startForResultLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                new ActivityResultCallback<ActivityResult>() {
                    @Override
                    public void onActivityResult(ActivityResult aresult) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                if (!checkNotificationChannelEnabled()) {
                                    Toast.makeText(getApplicationContext(), "Die Abmeldungsbenachrichtigung ist deaktiviert!", Toast.LENGTH_SHORT).show();
                                    openNotiSettings();
                                }
                            }
                        });
                    }
                });

        /*startForResultLauncherLogin = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                new ActivityResultCallback<ActivityResult>() {
                    @Override
                    public void onActivityResult(ActivityResult aresult) {
                        onAResult(aresult.getResultCode(),aresult.getData());
                    }
                });

        startForResultLauncherLogout = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                new ActivityResultCallback<ActivityResult>() {
                    @Override
                    public void onActivityResult(ActivityResult aresult) {
                        onAResult(aresult.getResultCode(),aresult.getData());
                    }
                });*/
    }

    private void showResult(String id,String access, String refresh)
    {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mTextViewResult.setText("Token request erfolgreich:");
                if (id!=null) {
                    mTextViewid.setText(id);
                }
                if (access!=null) {
                    mTextViewaccess.setText(access);
                }
                if (refresh!=null) {
                    mTextViewrefresh.setText(refresh);
                }
            }
        });
    }

    private void showError(String err)
    {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mTextViewResult.setText(err);
            }
        });
    }

    private Runnable mRunnableCountdown = new Runnable() {
        @Override
        public void run() {
            mCounter++;
            if (!first) {
                mTextViewCountdown.setText("nächster Test: " + String.valueOf(60 - mCounter) + " Sek."); // Anzeige des Timerwerts in TextView
            }
            else {
                mTextViewCountdown.setText("nächster Test: " + String.valueOf(10-mCounter)+" Sek."); // Anzeige des Timerwerts in TextView
            }

            if (first&&mCounter>10)
            {
                first=false;
                runTest();
            }

            if (mCounter>=60)
            {
                mCounter=0;

                runTest();
            }

            mHandlerCountdown.postDelayed(this, mInterval); // wiederholen der Aktion nach dem Intervall
        }
    };

    public static String convertTimestamp(long timestamp) {
        Instant instant = Instant.ofEpochMilli(timestamp);
        ZoneId zoneId = ZoneId.systemDefault();
        ZonedDateTime zonedDateTime = instant.atZone(zoneId);

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss z");
        return formatter.format(zonedDateTime);
    }

    private void runTest() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mTextViewResult.setText("");
                mTextViewaccess.setText("");
                mTextViewid.setText("");
                mTextViewrefresh.setText("");
            }
        });

        Account current = Utils.getCurrentUser(context);
        if (current!=null) {
            AccountManager amgr = AccountManager.get(context);
            amgr.getAuthToken(current, TOKEN_TYPE_ID, null, SettingsActivity.this, new AccountManagerCallback<Bundle>() {
                @Override
                public void run(AccountManagerFuture<Bundle> future) {
                    try {
                        Bundle result = future.getResult();

                        if (result.keySet().contains(AccountManager.KEY_AUTHTOKEN)) {

                            String id_token=result.keySet().contains(AccountManager.KEY_AUTHTOKEN)?result.getString(AccountManager.KEY_AUTHTOKEN):null;
                            String exp="";
                            if (id_token!=null)
                            {
                                JSONObject payload = Utils.getPayload(id_token);
                                long lexp = payload.getLong("exp");
                                Log.d(TAG,"ID TOKEN EXP: "+String.valueOf(lexp));
                                lexp=lexp*1000;
                                exp = convertTimestamp(lexp);
                            }
                            String id="ID TOKEN: "+(id_token!=null?("JA gültig bis: "+exp):"NEIN");

                            showResult(id,null,null);
                        } else {
                            showError("Login nötig. Refreshtoken abgelaufen oder nicht erneuerbar? Rufe Login auf.");
                            Intent i = (Intent) result.get(AccountManager.KEY_INTENT);
                            i.putExtra("test",true);
                            startActivity(i);
                        }
                    } catch (AuthenticatorException e) {
                        Log.e(TAG, e.getMessage());
                        showError(e.getMessage());
                    } catch (IOException e) {
                        Log.e(TAG, e.getMessage());
                        showError(e.getMessage());
                    } catch (OperationCanceledException e) {
                        Log.e(TAG, e.getMessage());
                        showError(e.getMessage());
                    }
                    catch (Exception e) {
                        Log.e(TAG, e.getMessage());
                        showError(e.getMessage());
                    }
                }
            },null);

            amgr.getAuthToken(current, TOKEN_TYPE_ACCESS, null, SettingsActivity.this, new AccountManagerCallback<Bundle>() {
                @Override
                public void run(AccountManagerFuture<Bundle> future) {
                    try {
                        Bundle result = future.getResult();

                        if (result.keySet().contains(AccountManager.KEY_AUTHTOKEN)) {
                            String access_token=result.keySet().contains(AccountManager.KEY_AUTHTOKEN)?result.getString(AccountManager.KEY_AUTHTOKEN):null;
                            String exp="";
                            if (access_token!=null)
                            {
                                JSONObject payload = Utils.getPayload(access_token);
                                long lexp = payload.getLong("exp");
                                Log.d(TAG,"ACCESS TOKEN EXP: "+String.valueOf(lexp));
                                lexp=lexp*1000;
                                exp = convertTimestamp(lexp);
                            }
                            String access="ACCESS TOKEN: "+(access_token!=null?("JA gültig bis: "+exp):"NEIN");

                            showResult(null,access,null);
                        } else {
                            showError("Login nötig. Refreshtoken abgelaufen oder nicht erneuerbar? Rufe Login auf.");
                            Intent i = (Intent) result.get(AccountManager.KEY_INTENT);
                            i.putExtra("test",true);
                            startActivity(i);
                        }
                    } catch (AuthenticatorException e) {
                        Log.e(TAG, e.getMessage());
                        showError(e.getMessage());
                    } catch (IOException e) {
                        Log.e(TAG, e.getMessage());
                        showError(e.getMessage());
                    } catch (OperationCanceledException e) {
                        Log.e(TAG, e.getMessage());
                        showError(e.getMessage());
                    } catch (Exception e) {
                        Log.e(TAG, e.getMessage());
                        showError(e.getMessage());
                    }
                }
            },null);

            amgr.getAuthToken(current, TOKEN_TYPE_REFRESH, null, SettingsActivity.this, new AccountManagerCallback<Bundle>() {
                @Override
                public void run(AccountManagerFuture<Bundle> future) {
                    try {
                        Bundle result = future.getResult();

                        if (result.keySet().contains(AccountManager.KEY_AUTHTOKEN)) {
                            String refresh_token="REFRESH TOKEN: "+(result.keySet().contains(AccountManager.KEY_AUTHTOKEN)
                                    &&
                                    result.getString(AccountManager.KEY_AUTHTOKEN)!=null?"JA":"NEIN");

                            showResult(null,null, refresh_token);
                        } else {
                            showError("Login nötig. Refreshtoken abgelaufen oder nicht erneuerbar? Rufe Login auf.");
                            Intent i = (Intent) result.get(AccountManager.KEY_INTENT);
                            i.putExtra("test",true);
                            startActivity(i);
                        }
                    } catch (AuthenticatorException e) {
                        Log.e(TAG, e.getMessage());
                        showError(e.getMessage());
                    } catch (IOException e) {
                        Log.e(TAG, e.getMessage());
                        showError(e.getMessage());
                    } catch (OperationCanceledException e) {
                        Log.e(TAG, e.getMessage());
                        showError(e.getMessage());
                    }
                }
            },null);
        }
        else
        {
            showError("Login nötig. Refreshtoken abgelaufen oder nicht erneuerbar? Rufe Login auf.");
            Intent i = new Intent(ACTION_LOGIN);
            i.putExtra("test",true);
            startActivity(i);
        }
    }
   /* @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        onAResult(resultCode,data);
    }*/

    @Override
    protected void onNewIntent(Intent data) {
        super.onNewIntent(data);
        onAResult(data.hasExtra("result")?data.getIntExtra("result",RESULT_CANCELED):RESULT_CANCELED,data);
    }

    private void initButtons() {

        // btnInit = (Button) findViewById(R.id.init);
        btnLogin = (Button) findViewById(R.id.login);
        btnRefresh = (Button) findViewById(R.id.refresh);
        btnLogout = (Button) findViewById(R.id.logout);
        btnTest = (Button) findViewById(R.id.test);

        btnTest.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (activeTest)
                {
                    btnTest.setText("Start Test");
                    activeTest=false;
                    mHandlerCountdown.removeCallbacks(mRunnableCountdown);
                    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                }
                else
                {
                    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                    first=true;
                    mCounter=0;
                    btnTest.setText("Stop Test");
                    activeTest=true;
                    mTextViewCountdown.setText("");
                    mTextViewResult.setText("");
                    mHandlerCountdown.postDelayed(mRunnableCountdown, mInterval);
                }
            }
        });

        btnLogin.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent i = new Intent(ACTION_LOGIN);
                //startForResultLauncherLogin.launch(i);
                i.putExtra("test",true);
                //startActivityForResult(i,1);
                startActivity(i);
            }
        });

        btnLogout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent i = new Intent(ACTION_LOGOUT);
                i.putExtra("test",true);
                //startForResultLauncherLogout.launch(i);
                //startActivityForResult(i,2);
                startActivity(i);
            }
        });

        btnRefresh.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                Account account = Utils.getCurrentUser(context);
                if (account!=null) {
                    long refresh_token_expires_in = Utils.getNumberFromTokenData(context, account, "refresh_token_expires_in");//geändert in timestamp wann refresh_token abläuft

                    if (refresh_token_expires_in > System.currentTimeMillis()) {

                        try
                        {
                            String response = ADFSAuthenticator.refreshToken(context);
                            if (response!=null) {
                                outputResponse(TAG, response);
                                runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        Toast.makeText(context, "Tokenrefresh erfolgreich.", Toast.LENGTH_SHORT).show();
                                    }
                                });
                            }
                            else {
                                runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        Toast.makeText(context, "Nutzer nicht eingeloggt.", Toast.LENGTH_SHORT).show();
                                    }
                                });
                            }
                        }
                        catch (Exception ex)
                        {
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    Toast.makeText(context, ex.getMessage(), Toast.LENGTH_SHORT).show();
                                }
                            });
                        }
                        return;
                    }
                }

                Toast.makeText(getApplicationContext(), "Nutzer nicht eingeloggt.", Toast.LENGTH_SHORT).show();
            }
        });


    }

    @Override
    protected void onDestroy(){
        super.onDestroy();
        if (activeTest)
        {
            btnTest.callOnClick();
        }
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        //startForResultLauncherLogin.unregister();
        //startForResultLauncherLogout.unregister();
        startForResultLauncher.unregister();
    }

    private void initAppAuth(InitCallback callback) {

        Utils.initSSL();
        if (Utils.getSharedPref(context,context.getString(R.string.configuration_key))==null) {
            LoadconfigTask task = new LoadconfigTask(this, new TaskResultCallback() {
                @Override
                public void onSuccess(String data) {
                    Utils.setSharedPref(context, context.getString(R.string.configuration_key), data);
                    callback.onInit();
                }

                @Override
                public void onError(Exception ex) {
                    Log.e(TAG,ex.getMessage());
                    setResult(RESULT_CANCELED);
                    finish();
                }
            });
            task.execute();
        }
        else {
            callback.onInit();
        }
    }

    private void openNotiSettings() {

        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName())
                .putExtra(Settings.EXTRA_CHANNEL_ID, CHANNEL_ID);
        Uri uri = Uri.fromParts("package", getPackageName(), null);
        intent.setData(uri);
        //startActivityForResult(intent,NOTICHECK);


        startForResultLauncher.launch(intent);

    }

    private void onAResult(int resultCode, Intent data){
        Account acc = Utils.getCurrentUser(context);
        if (acc!=null)
        {
            btnLogout.setEnabled(true);
            btnRefresh.setEnabled(true);
            btnLogin.setEnabled(false);
        }
        else {
            btnLogin.setEnabled(true);
            btnRefresh.setEnabled(false);
            btnLogout.setEnabled(false);
        }

        if (resultCode==RESULT_OK)
        {

            runOnUiThread(new Runnable() {
                @Override
                public void run() {

                    String result = "Aktion erfolgreich";

                    if (data!=null&&data.hasExtra("id_token")&&data.hasExtra("access_token") &&data.hasExtra("refresh_token")) {
                        result = "Ergebnis: \r\nid_token: " + data.getStringExtra("id_token");
                        result += "\r\naccess_token: " + data.getStringExtra("access_token");
                        result += "\r\nrefresh_token: " + data.getStringExtra("refresh_token");
                    }

                    Toast.makeText(context, result, Toast.LENGTH_SHORT).show();
                }
            });
        }
        else
        if (resultCode==RESULT_CANCELED)
        {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {

                    String result = "Aktion nicht erfolgreich";

                    Toast.makeText(context, result, Toast.LENGTH_SHORT).show();
                }
            });
        }
    }

    public boolean checkNotificationChannelEnabled() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (!manager.areNotificationsEnabled()) {
                return false;
            }
            List<NotificationChannel> channels = manager.getNotificationChannels();
            for (NotificationChannel channel : channels) {
                if (channel.getImportance() == NotificationManager.IMPORTANCE_NONE) {
                    return false;
                }
            }
            return true;
        } else {
            return NotificationManagerCompat.from(this).areNotificationsEnabled();
        }
    }

    public static class SettingsFragment extends PreferenceFragmentCompat {
        public void show(Context context, String title, String message, ConfirmDialogListener listener) {
            AlertDialog.Builder builder = new AlertDialog.Builder(context);
            builder.setTitle(title);
            builder.setMessage(message);
            builder.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    if (listener != null) {
                        listener.onConfirm();
                    }
                    dialog.dismiss();
                }
            });
            builder.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    dialog.dismiss();
                }
            });
            AlertDialog dialog = builder.create();
            dialog.show();
        }

        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            setPreferencesFromResource(R.xml.root_preferences, rootKey);

            EditTextPreference myPreference = findPreference(getContext().getString(R.string.timeout_key));
            myPreference.setOnBindEditTextListener(new EditTextPreference.OnBindEditTextListener() {
                @Override
                public void onBindEditText(@NonNull EditText editText) {
                    editText.setInputType(InputType.TYPE_CLASS_NUMBER);
                }
            });

            Preference togglePreference = findPreference(getContext().getString(R.string.toggle_map_key));
            togglePreference.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {

                    if (((Boolean) newValue).booleanValue()) {
                        show(getContext(), getString(R.string.confirm), getString(R.string.confirm_poldom), new ConfirmDialogListener() {
                            @Override
                            public void onConfirm() {
                                Utils.setSharedPref(getContext(), getString(R.string.client_id_key), getString(R.string.default_client_id_poldom));
                                Utils.setSharedPref(getContext(), getString(R.string.baseurl_key), getString(R.string.default_baseurl_poldom));
                                Utils.setSharedPref(getContext(), getString(R.string.proxy_key), getString(R.string.default_proxy_poldom));
                                getActivity().finish();
                                startActivity(getActivity().getIntent());
                            }

                        });
                    } else {
                        show(getContext(), getString(R.string.confirm), getString(R.string.confirm_map), new ConfirmDialogListener() {
                            @Override
                            public void onConfirm() {
                                Utils.setSharedPref(getContext(), getString(R.string.client_id_key), getString(R.string.default_client_id_map));
                                Utils.setSharedPref(getContext(), getString(R.string.baseurl_key), getString(R.string.default_baseurl_map));
                                Utils.setSharedPref(getContext(), getString(R.string.proxy_key), getString(R.string.default_proxy_map));
                                getActivity().finish();
                                startActivity(getActivity().getIntent());
                            }

                        });
                    }
                    return true;
                }
            });
        }
    }
}