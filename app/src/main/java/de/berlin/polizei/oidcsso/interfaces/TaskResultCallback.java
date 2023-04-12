package de.berlin.polizei.oidcsso.interfaces;

public interface TaskResultCallback {
    public void onSuccess(String data);
    public void onError(Exception e);
}
