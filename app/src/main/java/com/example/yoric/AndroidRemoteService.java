package com.example.yoric;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

public class AndroidRemoteService extends Service {
    private static final String TAG = "Yoric";

    public AndroidRemoteService() {
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "service >> AndroidRemoteService << started");
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
