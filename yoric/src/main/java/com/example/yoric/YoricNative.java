package com.example.yoric;

import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.os.Parcel;
import android.os.Process;
import android.os.RemoteException;
import android.util.Log;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class YoricNative {

    private static final String TAG = "Yoric";

    static {
        System.loadLibrary("yoric");
    }

    public static final String YORIC_DIR = "yoric-indicators";
    public static final String REMOTE_PATH = "remote";
    public static final String REMOTE_FLAG_PATH = "remote_flag";
    public static final String ANDROID_REMOTE_PATH = "android-remote";
    public static final String ANDROID_REMOTE_FLAG_PATH = "android-remote_flag";

    private static YoricNative sInstance;
    private Context mApp;
    private IBinder mAMSRemote;
    private Parcel mServiceParcel;

    public static YoricNative getInstance(Context app) {
        if (sInstance == null) {
            synchronized (YoricNative.class) {
                if (sInstance == null) {
                    sInstance = new YoricNative(app);
                }
            }
        }
        return sInstance;
    }

    public void onAndroidRemoteDead(final String remoteName /* selfName */,
                                    final String androidRemoteName,
                                    final Class<? extends Service> androidRemoteSvcClass) {
        // self is remote, he is android-remote
        initAMSRemote();
        initServiceParcel(androidRemoteSvcClass);
        // important! when started, start him, or we will wait for him forever
        startServiceByAMSRemote();
        new Thread() {
            @Override
            public void run() {
                File dir = mApp.getDir(YORIC_DIR, Context.MODE_PRIVATE);
                doPrepare(remoteName, androidRemoteName,
                        dir.getAbsolutePath() + "/" + REMOTE_PATH,
                        dir.getAbsolutePath() + "/" + ANDROID_REMOTE_PATH,
                        dir.getAbsolutePath() + "/" + REMOTE_FLAG_PATH,
                        dir.getAbsolutePath() + "/" + ANDROID_REMOTE_FLAG_PATH);
            }
        }.start();
    }

    public void onRemoteDead(final String androidRemoteName /* selfName */,
                             final String remoteName,
                             final Class<? extends Service> remoteSvcClass) {
        // self is android-remote, he is remote
        initAMSRemote();
        initServiceParcel(remoteSvcClass);
        // important! when started, start him, or we will wait for him forever
        startServiceByAMSRemote();
        new Thread() {
            @Override
            public void run() {
                File dir = mApp.getDir(YORIC_DIR, Context.MODE_PRIVATE);
                doPrepare(androidRemoteName, remoteName,
                        dir.getAbsolutePath() + "/" + ANDROID_REMOTE_PATH,
                        dir.getAbsolutePath() + "/" + REMOTE_PATH,
                        dir.getAbsolutePath() + "/" + ANDROID_REMOTE_FLAG_PATH,
                        dir.getAbsolutePath() + "/" + REMOTE_FLAG_PATH);
            }
        }.start();
    }

    public void onProcessDead() {
        Log.d(TAG, "start his service");
        if (startServiceByAMSRemote()) {
            Process.killProcess(Process.myPid());
        }
    }

    private YoricNative(Context app) {
        this.mApp = app;
        File dir = mApp.getDir(YORIC_DIR, Context.MODE_PRIVATE);
        if (!dir.exists()) {
            if(!dir.mkdir()) {
                dir.mkdir();
            }
        }
    }

    private void initAMSRemote() {
        try {
            Class<?> AMN = Class.forName("android.app.ActivityManagerNative");
            Method getDefault = AMN.getMethod("getDefault");
            Object ams = getDefault.invoke(AMN);
            Field mRemoteField = ams.getClass().getDeclaredField("mRemote");
            mRemoteField.setAccessible(true);
            mAMSRemote = (IBinder) mRemoteField.get(ams);
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    private void initServiceParcel(Class<? extends Service> svcClass) {
        Intent intent = new Intent();
        ComponentName component = new ComponentName(mApp.getPackageName(), svcClass.getCanonicalName());
        intent.setComponent(component);

        Parcel parcel = Parcel.obtain();
        intent.writeToParcel(parcel, 0);

        mServiceParcel = Parcel.obtain();
        if (Build.VERSION.SDK_INT >= 26) {
            // Android 8.1
            mServiceParcel.writeInterfaceToken("android.app.IActivityManager");
            mServiceParcel.writeStrongBinder(null);
            mServiceParcel.writeInt(1);
            intent.writeToParcel(mServiceParcel, 0);
            mServiceParcel.writeString(null);
            mServiceParcel.writeInt(mApp.getApplicationInfo().targetSdkVersion >= Build.VERSION_CODES.O ? 1 : 0);
            mServiceParcel.writeString(mApp.getPackageName());
            mServiceParcel.writeInt(0);
        } else {
            // http://aospxref.com/android-7.1.2_r36/xref/frameworks/base/core/java/android/app/ActivityManagerNative.java
            mServiceParcel.writeInterfaceToken("android.app.IActivityManager");
            mServiceParcel.writeStrongBinder(null);
            intent.writeToParcel(mServiceParcel, 0);
            mServiceParcel.writeString(null);
            mServiceParcel.writeString(mApp.getPackageName());
            mServiceParcel.writeInt(0);
        }
    }

    private boolean startServiceByAMSRemote() {
        if (mAMSRemote == null) {
            Log.w(TAG, "mAMSRemote is null");
            return false;
        } else if (mServiceParcel == null) {
            Log.w(TAG, "mServiceParcel is null");
            return false;
        }

        try {
            int code;
            switch (Build.VERSION.SDK_INT) {
                case 26: case 27:
                    code = 26;
                    break;
                case 28:
                    code = 30;
                    break;
                case 29:
                    code = 24;
                    break;
                default:
                    code = 34;
                    break;
            }
            mAMSRemote.transact(code, mServiceParcel, null, 1); // 1 for one-way
            return true;
        } catch (RemoteException e) {
            e.printStackTrace();
            return false;
        }
    }

    public native void doPrepare(String selfName, String hisName,
                                 String selfPath, String hisPath,
                                 String selfFlag, String hisFlag);
}
