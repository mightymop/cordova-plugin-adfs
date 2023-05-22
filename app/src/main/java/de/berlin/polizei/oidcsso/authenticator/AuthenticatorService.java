package de.berlin.polizei.oidcsso.authenticator;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;


public class AuthenticatorService extends Service {

    private final String TAG = getClass().getSimpleName();

    @Override
    public IBinder onBind(Intent intent) {
        Log.d(TAG, "Binding Authenticator.");

        try {
            ADFSAuthenticator authenticator = new ADFSAuthenticator(this);
            return authenticator.getIBinder();
        }
        catch (Exception e)
        {
            Log.e("ADFSAuthenticatorService",e.getMessage(),e);
            return null;
        }
    }

}
