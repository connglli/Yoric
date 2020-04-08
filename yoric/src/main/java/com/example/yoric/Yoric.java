package com.example.yoric;

import android.app.Service;
import android.content.Context;

import androidx.annotation.NonNull;

public class Yoric {

    public static class ConfigPair {
        public final @NonNull Config remote;
        public final @NonNull Config androidRemote;

        public ConfigPair(@NonNull Config remote, @NonNull Config androidRemote) {
            this.remote = remote;
            this.androidRemote = androidRemote;
        }
    }

    public static class Config {
        public final @NonNull String procName;
        public final @NonNull Class<? extends Service> svcClass;

        public Config(@NonNull String procName, @NonNull Class<? extends Service> svcClass) {
            this.procName = procName;
            this.svcClass = svcClass;
        }
    }

    private static Yoric sInstance;
    private final Config mRemote;
    private final Config mAndroidRemote;

    public static void prepare(final Context app, final ConfigPair configPair) {
        prepare(app, configPair, false);
    }

    private static void prepare(final Context app, final ConfigPair configPair, boolean mainThread) {
        if (sInstance == null) {
            synchronized (Yoric.class) {
                if (sInstance == null) {
                    sInstance = new Yoric(configPair);
                    if (mainThread) {
                        new Thread() {
                            @Override
                            public void run() {
                                sInstance.init(app);
                            }
                        }.start();
                    } else {
                        sInstance.init(app);
                    }
                }
            }
        }
    }

    private Yoric(ConfigPair configPair) {
        this.mRemote = configPair.remote;
        this.mAndroidRemote = configPair.androidRemote;
    }

    private void init(Context app) {
        String procName = Utils.getProcessName();

        if (mRemote.procName.equals(procName)) {
            // self is remote, register callback when android-remote dead
            YoricNative.getInstance(app).onAndroidRemoteDead(mRemote.procName,
                    mAndroidRemote.procName, mAndroidRemote.svcClass);
        } else if (mAndroidRemote.procName.equals(procName)) {
            // self is android-remote, register callback when remote dead
            YoricNative.getInstance(app).onRemoteDead(mAndroidRemote.procName,
                    mRemote.procName, mRemote.svcClass);
        }
    }
}
