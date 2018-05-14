package com.android.internal.telephony;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.AsyncResult;
import android.os.Handler;
import android.os.Message;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.provider.Telephony.Sms.Intents;
import android.telephony.Rlog;
import android.telephony.SubscriptionManager;
import android.util.secutil.Log;

public final class SmsStorageMonitor extends Handler {
    private static final int EVENT_ICC_FULL = 1;
    private static final int EVENT_RADIO_ON = 3;
    private static final int EVENT_REPORT_MEMORY_STATUS_DONE = 2;
    private static final int EVENT_SET_MEMORY_RSP = 5;
    private static final String TAG = "SmsStorageMonitor";
    private static final int WAKE_LOCK_TIMEOUT = 5000;
    private static boolean gcf_flag = false;
    private static boolean isSimFull = false;
    private static boolean receive_storage_low_event = false;
    private static boolean receive_storage_ok_event = false;
    final CommandsInterface mCi;
    private final Context mContext;
    private BroadcastReceiver mGcfResultReceiver = new C00302();
    protected PhoneBase mPhone;
    private boolean mReportMemoryStatusPending;
    private final BroadcastReceiver mResultReceiver = new C00291();
    boolean mSimStorageAvailable = true;
    boolean mStorageAvailable = true;
    private WakeLock mWakeLock;

    class C00291 extends BroadcastReceiver {
        C00291() {
        }

        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals("android.intent.action.DEVICE_STORAGE_FULL")) {
                SmsStorageMonitor.this.mStorageAvailable = false;
                SmsStorageMonitor.this.mCi.reportSmsMemoryStatus(false, SmsStorageMonitor.this.obtainMessage(2));
            } else if (intent.getAction().equals("android.intent.action.DEVICE_STORAGE_NOT_FULL")) {
                SmsStorageMonitor.this.mStorageAvailable = true;
                SmsStorageMonitor.this.mCi.reportSmsMemoryStatus(true, SmsStorageMonitor.this.obtainMessage(2));
            }
        }
    }

    class C00302 extends BroadcastReceiver {
        C00302() {
        }

        public void onReceive(Context context, Intent intent) {
            Log.secD(SmsStorageMonitor.TAG, "BroadcastReceiver - mGcfResultReceiver ");
            if (intent.getAction().equals("android.intent.action.GCF_DEVICE_STORAGE_LOW")) {
                Log.secD(SmsStorageMonitor.TAG, "ACTION_GCF_DEVICE_STORAGE_LOW ");
                SmsStorageMonitor.this.mCi.reportSmsMemoryStatus(false, SmsStorageMonitor.this.obtainMessage(2));
                SmsStorageMonitor.this.mStorageAvailable = false;
            }
            if (intent.getAction().equals("android.intent.action.GCF_DEVICE_STORAGE_OK")) {
                Log.secD(SmsStorageMonitor.TAG, "ACTION_GCF_DEVICE_STORAGE_OK ");
                SmsStorageMonitor.this.mCi.reportSmsMemoryStatus(true, SmsStorageMonitor.this.obtainMessage(2));
                SmsStorageMonitor.this.mStorageAvailable = true;
            }
        }
    }

    public SmsStorageMonitor(PhoneBase phone) {
        this.mPhone = phone;
        this.mContext = phone.getContext();
        this.mCi = phone.mCi;
        createWakelock();
        this.mCi.setOnIccSmsFull(this, 1, null);
        this.mCi.registerForOn(this, 3, null);
        IntentFilter filter = new IntentFilter();
        filter.addAction("android.intent.action.DEVICE_STORAGE_FULL");
        filter.addAction("android.intent.action.DEVICE_STORAGE_NOT_FULL");
        this.mContext.registerReceiver(this.mResultReceiver, filter);
        IntentFilter gcf_filter = new IntentFilter();
        gcf_filter.addAction("android.intent.action.GCF_DEVICE_STORAGE_LOW");
        gcf_filter.addAction("android.intent.action.GCF_DEVICE_STORAGE_OK");
        this.mContext.registerReceiver(this.mGcfResultReceiver, gcf_filter);
    }

    public void dispose() {
        this.mCi.unSetOnIccSmsFull(this);
        this.mCi.unregisterForOn(this);
        this.mContext.unregisterReceiver(this.mResultReceiver);
    }

    public void handleMessage(Message msg) {
        switch (msg.what) {
            case 1:
                Log.secE(TAG, "EVENT_ICC_FULL");
                this.mSimStorageAvailable = false;
                handleIccFull();
                return;
            case 2:
                if (msg.obj.exception != null) {
                    this.mReportMemoryStatusPending = true;
                    Rlog.v(TAG, "Memory status report to modem pending : mStorageAvailable = " + this.mStorageAvailable);
                    return;
                }
                this.mReportMemoryStatusPending = false;
                return;
            case 3:
                if (this.mReportMemoryStatusPending) {
                    Rlog.v(TAG, "Sending pending memory status report : mStorageAvailable = " + this.mStorageAvailable);
                    this.mCi.reportSmsMemoryStatus(this.mStorageAvailable, obtainMessage(2));
                    return;
                }
                return;
            case 5:
                if (((AsyncResult) msg.obj).result != null) {
                    Log.secD(TAG, "General Response Failed!");
                    this.mCi.reportSmsMemoryStatus(this.mStorageAvailable, obtainMessage(5));
                    return;
                }
                Log.secD(TAG, "reportSmsMemoryStatus set successfully");
                return;
            default:
                return;
        }
    }

    private void createWakelock() {
        this.mWakeLock = ((PowerManager) this.mContext.getSystemService("power")).newWakeLock(1, TAG);
        this.mWakeLock.setReferenceCounted(true);
    }

    public void handleIccFull() {
        Intent intent = new Intent(Intents.SIM_FULL_ACTION);
        if (this.mPhone.getSMSavailable()) {
            Log.secD(TAG, "getSMSavailable is true. Sending intent SIM_FULL_ACTION");
            this.mWakeLock.acquire(5000);
            SubscriptionManager.putPhoneIdAndSubIdExtra(intent, this.mPhone.getPhoneId());
            this.mContext.sendBroadcast(intent, "android.permission.RECEIVE_SMS");
            isSimFull = true;
        }
    }

    public boolean getSimFullStatus() {
        return isSimFull;
    }

    public void resetSimFullStatus() {
        isSimFull = false;
    }

    public boolean isStorageAvailable() {
        return this.mStorageAvailable;
    }

    public boolean isSIMStorageAvailable() {
        return this.mSimStorageAvailable;
    }

    public void setSIMStorageAvailable(boolean available) {
        this.mSimStorageAvailable = available;
    }
}
