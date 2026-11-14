package com.kelvin.hpoi.exhobby.hook;

import android.util.Log;

import de.robv.android.xposed.XposedBridge;

/** Logs to logcat and to the LSPosed log; hooks must never throw into the host. */
public final class XLog {

    public static final String TAG = "HpoiExhobby";

    private XLog() {
    }

    public static void d(String msg) {
        Log.d(TAG, msg);
        XposedBridge.log(TAG + ": " + msg);
    }

    public static void w(String msg, Throwable t) {
        Log.w(TAG, msg, t);
        XposedBridge.log(TAG + ": " + msg + " -> " + t);
    }
}
