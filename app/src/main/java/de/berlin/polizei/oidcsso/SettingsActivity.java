package de.berlin.polizei.oidcsso;

import static de.berlin.polizei.oidcsso.OIDCActivity.ACTION_LOGIN;
import static de.berlin.polizei.oidcsso.OIDCActivity.ACTION_LOGOUT;
import static de.berlin.polizei.oidcsso.authenticator.ADFSAuthenticator.CHANNEL_ID;
import static de.berlin.polizei.oidcsso.utils.Utils.outputResponse;

import android.accounts.Account;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.NotificationManagerCompat;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

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

    private ActivityResultLauncher<Intent> startForResultLauncher;

    private Context context;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        context = this;

        setContentView(R.layout.settings_activity);
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

        if (!checkNotificationChannelEnabled()) {
            openNotiSettings();
        }

        initAppAuth(new InitCallback() {
            @Override
            public void onInit() {
                initButtons();
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
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

    private void initButtons() {

        // btnInit = (Button) findViewById(R.id.init);
        btnLogin = (Button) findViewById(R.id.login);
        btnRefresh = (Button) findViewById(R.id.refresh);
        btnLogout = (Button) findViewById(R.id.logout);

        btnLogin.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent i = new Intent(ACTION_LOGIN);
                // i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                //i.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY);
                //i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                //i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);
                //i.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
                startActivityForResult(i,1);
            }
        });

        btnLogout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent i = new Intent(ACTION_LOGOUT);
                startActivityForResult(i,2);
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
                            outputResponse(TAG,response);
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    Toast.makeText(context, "Tokenrefresh erfolgreich.", Toast.LENGTH_SHORT).show();
                                }
                            });
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

        btnLogout.setVisibility(View.VISIBLE);
        btnLogin.setVisibility(View.VISIBLE);
        btnRefresh.setVisibility(View.VISIBLE);

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
        startForResultLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                new ActivityResultCallback<ActivityResult>() {
                    @Override
                    public void onActivityResult(ActivityResult aresult) {
                        startForResultLauncher.unregister();
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

        startForResultLauncher.launch(intent);

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

          /*  Preference debugActivityref = findPreference(getString(R.string.debug_activity_key));

            debugActivityref.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {

                    Intent i = new Intent(getContext(), LoginActivity.class);
                    startActivity(i);

                    return true;
                }
            });

            Preference configPref = findPreference(getString(R.string.configuration_key));
            String config = Utils.getSharedPref(getContext(), getContext().getString(R.string.configuration_key));
            if (config!=null&&!config.isEmpty())
            {
                configPref.setSummary(parseConfig(config));
            }
            configPref.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {

                    Thread t = new Thread(new Runnable() {
                        @Override
                        public void run() {
                            RequestManager requestManager = new RequestManager(getContext());
                            try {
                                JSONObject configuration = requestManager.load_config();
                                if (!configuration.has("error")) {
                                    Utils.setSharedPref(getContext(), getContext().getString(R.string.configuration_key), configuration.toString());
                                    getActivity().runOnUiThread(new Runnable() {
                                        @Override
                                        public void run() {
                                            preference.setSummary(parseConfig(configuration.toString()));
                                            Toast.makeText(getContext(),"Konfiguration geladen",Toast.LENGTH_SHORT).show();
                                        }
                                    });
                                }
                            } catch (Exception e) {
                                Log.e(SettingsFragment.class.getSimpleName(),e.getMessage());
                                getActivity().runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        Toast.makeText(getContext(),e.getMessage(),Toast.LENGTH_SHORT).show();
                                    }
                                });
                            }
                        }
                    });
                    t.start();

                    return true;
                }
            });*/
        }
    }
}