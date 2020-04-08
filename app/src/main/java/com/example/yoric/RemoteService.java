package com.example.yoric;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

public class RemoteService extends Service {
    private static final String TAG = "Yoric";

    public RemoteService() {
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "service >> RemoteService << started");
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
