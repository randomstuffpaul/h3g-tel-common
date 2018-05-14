package com.android.internal.telephony;

import android.telephony.Rlog;
import android.telephony.TelephonyManager;
import com.android.internal.telephony.uicc.IccCardProxy;
import com.android.internal.telephony.uicc.UiccController;
import java.io.FileDescriptor;
import java.io.PrintWriter;

public class DebugService {
    private static String TAG = "DebugService";

    public DebugService() {
        log("DebugService:");
    }

    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        log("dump: +");
        int phoneId = 0;
        while (phoneId < TelephonyManager.getDefault().getPhoneCount()) {
            try {
                PhoneProxy phoneProxy = (PhoneProxy) PhoneFactory.getPhone(phoneId);
                try {
                    PhoneBase phoneBase = (PhoneBase) phoneProxy.getActivePhone();
                    pw.println();
                    pw.println("PhoneId : " + phoneId + "++++++++++++++++++++");
                    pw.println("++++++++++++++++++++++++++++++++");
                    pw.flush();
                    try {
                        phoneBase.dump(fd, pw, args);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    pw.flush();
                    pw.println("++++++++++++++++++++++++++++++++");
                    try {
                        phoneBase.mDcTracker.dump(fd, pw, args);
                    } catch (Exception e2) {
                        e2.printStackTrace();
                    }
                    pw.flush();
                    pw.println("++++++++++++++++++++++++++++++++");
                    try {
                        phoneBase.getServiceStateTracker().dump(fd, pw, args);
                    } catch (Exception e22) {
                        e22.printStackTrace();
                    }
                    pw.flush();
                    pw.println("++++++++++++++++++++++++++++++++");
                    try {
                        phoneBase.getCallTracker().dump(fd, pw, args);
                    } catch (Exception e222) {
                        e222.printStackTrace();
                    }
                    pw.flush();
                    pw.println("++++++++++++++++++++++++++++++++");
                    try {
                        ((RIL) phoneBase.mCi).dump(fd, pw, args);
                    } catch (Exception e2222) {
                        e2222.printStackTrace();
                    }
                    pw.flush();
                    pw.println("++++++++++++++++++++++++++++++++");
                    try {
                        UiccController.getInstance().dump(fd, pw, args);
                    } catch (Exception e22222) {
                        e22222.printStackTrace();
                    }
                    pw.flush();
                    pw.println("++++++++++++++++++++++++++++++++");
                    try {
                        ((IccCardProxy) phoneProxy.getIccCard()).dump(fd, pw, args);
                    } catch (Exception e222222) {
                        e222222.printStackTrace();
                    }
                    pw.flush();
                    pw.println("++++++++++++++++++++++++++++++++");
                    phoneId++;
                } catch (Exception e2222222) {
                    pw.println("Telephony DebugService: Could not PhoneBase e=" + e2222222);
                    return;
                }
            } catch (Exception e22222222) {
                pw.println("Telephony DebugService: Could not getDefaultPhone e=" + e22222222);
                return;
            }
        }
        try {
            SubscriptionController.getInstance().dump(fd, pw, args);
        } catch (Exception e222222222) {
            e222222222.printStackTrace();
        }
        pw.flush();
        log("dump: -");
    }

    private static void log(String s) {
        Rlog.d(TAG, "DebugService " + s);
    }
}
