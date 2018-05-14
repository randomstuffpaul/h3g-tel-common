package com.android.internal.telephony;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.telephony.Rlog;

public class TelephonyTester {
    private static final boolean DBG = true;
    private static final String LOG_TAG = "TelephonyTester";
    protected BroadcastReceiver mIntentReceiver = new C00371();
    private PhoneBase mPhone;

    class C00371 extends BroadcastReceiver {
        C00371() {
        }

        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            TelephonyTester.log("sIntentReceiver.onReceive: action=" + action);
            if (action.equals(TelephonyTester.this.mPhone.getActionDetached())) {
                TelephonyTester.log("simulate detaching");
                TelephonyTester.this.mPhone.getServiceStateTracker().mDetachedRegistrants.notifyRegistrants();
            } else if (action.equals(TelephonyTester.this.mPhone.getActionAttached())) {
                TelephonyTester.log("simulate attaching");
                TelephonyTester.this.mPhone.getServiceStateTracker().mAttachedRegistrants.notifyRegistrants();
            } else {
                TelephonyTester.log("onReceive: unknown action=" + action);
            }
        }
    }

    TelephonyTester(PhoneBase phone) {
        this.mPhone = phone;
        if (Build.IS_DEBUGGABLE) {
            IntentFilter filter = new IntentFilter();
            filter.addAction(this.mPhone.getActionDetached());
            log("register for intent action=" + this.mPhone.getActionDetached());
            filter.addAction(this.mPhone.getActionAttached());
            log("register for intent action=" + this.mPhone.getActionAttached());
            phone.getContext().registerReceiver(this.mIntentReceiver, filter, null, this.mPhone.getHandler());
        }
    }

    void dispose() {
        if (Build.IS_DEBUGGABLE) {
            this.mPhone.getContext().unregisterReceiver(this.mIntentReceiver);
        }
    }

    private static void log(String s) {
        Rlog.d(LOG_TAG, s);
    }
}
