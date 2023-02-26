package de.mopsdom.adfs;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;


public class AuthenticatorService extends Service {

  private final String TAG = getClass().getSimpleName();

  @Override
  public IBinder onBind(Intent intent) {
    Log.d(TAG, "Binding Authenticator.");

    ADFSAuthenticator authenticator = new ADFSAuthenticator(this);
    return authenticator.getIBinder();
  }

}
