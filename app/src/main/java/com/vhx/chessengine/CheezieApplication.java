package com.vhx.chessengine;

import android.app.Application;
import android.os.Build;

public final class CheezieApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        DebugLog.info(this, "Application", "Started Android " + Build.VERSION.RELEASE + " API " + Build.VERSION.SDK_INT + " " + Build.MANUFACTURER + " " + Build.MODEL);

        Thread.setDefaultUncaughtExceptionHandler((thread, error) -> {
            DebugLog.error(this, "Crash", "Uncaught exception on " + thread.getName(), error);
            android.os.SystemClock.sleep(150);
            android.os.Process.killProcess(android.os.Process.myPid());
            System.exit(10);
        });
    }
}
