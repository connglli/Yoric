package com.example.yoric;

import android.app.Application;
import android.content.Context;
import android.util.Log;

public class YoricApp extends Application {

    private static final String TAG = "Yoric";

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        Log.d(TAG, "=====================> YoricApp <=====================");
        Yoric.prepare(this, new Yoric.ConfigPair(
                new Yoric.Config(getPackageName() + ":remote", RemoteService.class),
                new Yoric.Config("android.remote", AndroidRemoteService.class)));
    }
}
