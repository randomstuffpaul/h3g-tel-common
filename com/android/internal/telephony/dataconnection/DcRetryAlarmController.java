package com.android.internal.telephony.dataconnection;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.AsyncResult;
import android.os.SystemClock;
import android.telephony.Rlog;
import android.text.TextUtils;
import com.android.internal.telephony.PhoneBase;

public class DcRetryAlarmController {
    private static final boolean DBG = true;
    private static final String INTENT_RETRY_ALARM_TAG = "tag";
    private static final String INTENT_RETRY_ALARM_WHAT = "what";
    private String mActionRetry;
    private AlarmManager mAlarmManager;
    private DataConnection mDc;
    private BroadcastReceiver mIntentReceiver = new C00871();
    private String mLogTag = "DcRac";
    private PhoneBase mPhone;

    class C00871 extends BroadcastReceiver {
        C00871() {
        }

        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (TextUtils.isEmpty(action)) {
                DcRetryAlarmController.this.log("onReceive: ignore empty action='" + action + "'");
            } else if (!TextUtils.equals(action, DcRetryAlarmController.this.mActionRetry)) {
                DcRetryAlarmController.this.log("onReceive: unknown action=" + action);
            } else if (!intent.hasExtra(DcRetryAlarmController.INTENT_RETRY_ALARM_WHAT)) {
                throw new RuntimeException(DcRetryAlarmController.this.mActionRetry + " has no INTENT_RETRY_ALRAM_WHAT");
            } else if (intent.hasExtra(DcRetryAlarmController.INTENT_RETRY_ALARM_TAG)) {
                int what = intent.getIntExtra(DcRetryAlarmController.INTENT_RETRY_ALARM_WHAT, Integer.MAX_VALUE);
                int tag = intent.getIntExtra(DcRetryAlarmController.INTENT_RETRY_ALARM_TAG, Integer.MAX_VALUE);
                DcRetryAlarmController.this.log("onReceive: action=" + action + " sendMessage(what:" + DcRetryAlarmController.this.mDc.getWhatToString(what) + ", tag:" + tag + ")");
                DcRetryAlarmController.this.mDc.sendMessage(DcRetryAlarmController.this.mDc.obtainMessage(what, tag, 0));
            } else {
                throw new RuntimeException(DcRetryAlarmController.this.mActionRetry + " has no INTENT_RETRY_ALRAM_TAG");
            }
        }
    }

    DcRetryAlarmController(PhoneBase phone, DataConnection dc) {
        this.mLogTag = dc.getName();
        this.mPhone = phone;
        this.mDc = dc;
        this.mAlarmManager = (AlarmManager) this.mPhone.getContext().getSystemService("alarm");
        this.mActionRetry = this.mDc.getClass().getCanonicalName() + "." + this.mDc.getName() + ".action_retry";
        IntentFilter filter = new IntentFilter();
        filter.addAction(this.mActionRetry);
        log("DcRetryAlarmController: register for intent action=" + this.mActionRetry);
        this.mPhone.getContext().registerReceiver(this.mIntentReceiver, filter, null, this.mDc.getHandler());
    }

    void dispose() {
        log("dispose");
        this.mPhone.getContext().unregisterReceiver(this.mIntentReceiver);
        this.mPhone = null;
        this.mDc = null;
        this.mAlarmManager = null;
        this.mActionRetry = null;
    }

    int getSuggestedRetryTime(DataConnection dc, AsyncResult ar) {
        DataCallResponse response = ar.result;
        int retryDelay = response.suggestedRetryTime;
        if (retryDelay == Integer.MAX_VALUE) {
            log("getSuggestedRetryTime: suggestedRetryTime is MAX_INT, retry NOT needed");
            retryDelay = -1;
        } else if (retryDelay >= 0) {
            log("getSuggestedRetryTime: suggestedRetryTime is >= 0 use it");
            log("getSuggestedRetryTime: resetRetryCount after suggestedRetryTime comes from modem");
            dc.mRetryManager.resetRetryCount();
        } else if (dc.mRetryManager.isRetryNeeded()) {
            retryDelay = dc.mRetryManager.getRetryTimer();
            if (retryDelay < 0) {
                retryDelay = 0;
            }
            log("getSuggestedRetryTime: retry is needed");
        } else {
            log("getSuggestedRetryTime: retry is NOT needed");
            retryDelay = -1;
        }
        log("getSuggestedRetryTime: " + retryDelay + " response=" + response + " dc=" + dc);
        return retryDelay;
    }

    public void startRetryAlarm(int what, int tag, int delay) {
        Intent intent = new Intent(this.mActionRetry);
        intent.addFlags(268435456);
        intent.putExtra(INTENT_RETRY_ALARM_WHAT, what);
        intent.putExtra(INTENT_RETRY_ALARM_TAG, tag);
        log("startRetryAlarm: next attempt in " + (delay / 1000) + "s" + " what=" + what + " tag=" + tag);
        this.mAlarmManager.setExact(2, SystemClock.elapsedRealtime() + ((long) delay), PendingIntent.getBroadcast(this.mPhone.getContext(), 0, intent, 134217728));
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(this.mLogTag).append(" [dcRac] ");
        sb.append(" mPhone=").append(this.mPhone);
        sb.append(" mDc=").append(this.mDc);
        sb.append(" mAlaramManager=").append(this.mAlarmManager);
        sb.append(" mActionRetry=").append(this.mActionRetry);
        return sb.toString();
    }

    private void log(String s) {
        Rlog.d(this.mLogTag, "[dcRac] " + s);
    }
}
