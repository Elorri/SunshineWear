package com.example.android.sunshine.sync;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

import com.example.android.sunshine.Utility;

public class SunshineSyncService extends Service {
    private static final Object sSyncAdapterLock = new Object();
    private static SunshineSyncAdapter sSunshineSyncAdapter = null;

    @Override
    public void onCreate() {
        Log.d("Lifecycle", Thread.currentThread().getStackTrace()[2] + ": " + Utility.thread() + " : " +
                " : SunshineSyncService :  object created");
        Log.d("Lifecycle", Thread.currentThread().getStackTrace()[2] + ": " + Utility.thread() + " : " +
                " : sSyncAdapterLock :  object created");
        synchronized (sSyncAdapterLock) {
            if (sSunshineSyncAdapter == null) {
                Log.d("Lifecycle", Thread.currentThread().getStackTrace()[2] + ": " + Utility.thread() + " : " +
                        " : SunshineSyncAdapter :  object created");
                sSunshineSyncAdapter = new SunshineSyncAdapter(getApplicationContext(), true);
            }
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return sSunshineSyncAdapter.getSyncAdapterBinder();
    }
}