package com.android.internal.telephony.cat;

import android.os.SystemProperties;
import android.telephony.Rlog;

public abstract class CatLog {
    private static final boolean DEBUG;
    private static final String SHIP = SystemProperties.get("ro.product_ship", "false").trim();
    public static final String SIM2 = "SIM2";

    static {
        boolean z = true;
        if (SystemProperties.getInt("ro.debuggable", 0) != 1) {
            z = false;
        }
        DEBUG = z;
    }

    public static void m0d(Object caller, String msg) {
        if (isDebuggable()) {
            String className = caller.getClass().getName();
            Rlog.d("CAT", className.substring(className.lastIndexOf(46) + 1) + ": " + msg);
        }
    }

    public static void m2d(String caller, String msg) {
        if (isDebuggable()) {
            Rlog.d("CAT", caller + ": " + msg);
        }
    }

    public static void m1d(String simId, Object caller, String msg) {
        if (isDebuggable()) {
            String className = caller.getClass().getName();
            Rlog.d("CAT", className.substring(className.lastIndexOf(46) + 1) + ": " + "[" + simId + "]" + msg);
        }
    }

    private static boolean isDebuggable() {
        return !SHIP.equalsIgnoreCase("true");
    }
}
