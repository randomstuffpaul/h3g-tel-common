package com.android.internal.telephony.dataconnection;

import android.app.PendingIntent;
import android.content.Context;
import android.net.NetworkConfig;
import android.telephony.Rlog;
import com.android.internal.telephony.DctConstants.State;
import com.android.internal.telephony.Phone;
import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class ApnContext {
    protected static final boolean DBG = false;
    public final String LOG_TAG;
    private ApnSetting mApnSetting;
    private final String mApnType;
    private final Context mContext;
    AtomicBoolean mDataEnabled;
    DcAsyncChannel mDcAc;
    private final DcTrackerBase mDcTracker;
    AtomicBoolean mDependencyMet;
    private State mPreviousState;
    String mReason;
    PendingIntent mReconnectAlarmIntent;
    private int mRefCount = 0;
    private final Object mRefCountLock = new Object();
    int mRetryCountPermanent;
    private State mState;
    private ArrayList<ApnSetting> mWaitingApns = null;
    private AtomicInteger mWaitingApnsPermanentFailureCountDown;
    public final int priority;

    public ApnContext(Context context, String apnType, String logTag, NetworkConfig config, DcTrackerBase tracker) {
        this.mContext = context;
        this.mApnType = apnType;
        this.mState = State.IDLE;
        setReason(Phone.REASON_DATA_ENABLED);
        this.mDataEnabled = new AtomicBoolean(false);
        this.mDependencyMet = new AtomicBoolean(config.dependencyMet);
        this.mWaitingApnsPermanentFailureCountDown = new AtomicInteger(0);
        this.priority = config.priority;
        this.LOG_TAG = logTag;
        this.mDcTracker = tracker;
        this.mPreviousState = State.IDLE;
        setPermanentRetryCount(0);
    }

    public String getApnType() {
        return this.mApnType;
    }

    public synchronized DcAsyncChannel getDcAc() {
        return this.mDcAc;
    }

    public synchronized void setDataConnectionAc(DcAsyncChannel dcac) {
        this.mDcAc = dcac;
    }

    public synchronized PendingIntent getReconnectIntent() {
        return this.mReconnectAlarmIntent;
    }

    public synchronized void setReconnectIntent(PendingIntent intent) {
        this.mReconnectAlarmIntent = intent;
    }

    public synchronized ApnSetting getApnSetting() {
        log("getApnSetting: apnSetting=" + this.mApnSetting);
        return this.mApnSetting;
    }

    public synchronized void setApnSetting(ApnSetting apnSetting) {
        log("setApnSetting: apnSetting=" + apnSetting);
        this.mApnSetting = apnSetting;
    }

    public synchronized void setWaitingApns(ArrayList<ApnSetting> waitingApns) {
        this.mWaitingApns = waitingApns;
        this.mWaitingApnsPermanentFailureCountDown.set(this.mWaitingApns.size());
    }

    public int getWaitingApnsPermFailCount() {
        return this.mWaitingApnsPermanentFailureCountDown.get();
    }

    public void decWaitingApnsPermFailCount() {
        this.mWaitingApnsPermanentFailureCountDown.decrementAndGet();
    }

    public synchronized ApnSetting getNextWaitingApn() {
        ApnSetting apn;
        ArrayList<ApnSetting> list = this.mWaitingApns;
        apn = null;
        if (!(list == null || list.isEmpty())) {
            apn = (ApnSetting) list.get(0);
        }
        return apn;
    }

    public synchronized void removeWaitingApn(ApnSetting apn) {
        if (this.mWaitingApns != null) {
            this.mWaitingApns.remove(apn);
        }
    }

    public synchronized ArrayList<ApnSetting> getWaitingApns() {
        return this.mWaitingApns;
    }

    public synchronized void setState(State s) {
        if (!s.equals(this.mState)) {
            this.mPreviousState = this.mState;
        }
        this.mState = s;
        if (this.mState == State.FAILED && this.mWaitingApns != null) {
            this.mWaitingApns.clear();
        }
    }

    public synchronized State getState() {
        return this.mState;
    }

    public boolean isDisconnected() {
        State currentState = getState();
        return currentState == State.IDLE || currentState == State.FAILED;
    }

    public synchronized void setReason(String reason) {
        this.mReason = reason;
    }

    public synchronized String getReason() {
        return this.mReason;
    }

    public boolean isReady() {
        return this.mDataEnabled.get() && this.mDependencyMet.get();
    }

    public boolean isConnectable() {
        return isReady() && (this.mState == State.IDLE || this.mState == State.SCANNING || this.mState == State.RETRYING || this.mState == State.FAILED);
    }

    public boolean isConnectedOrConnecting() {
        return isReady() && (this.mState == State.CONNECTED || this.mState == State.CONNECTING || this.mState == State.SCANNING || this.mState == State.RETRYING);
    }

    public void setEnabled(boolean enabled) {
        this.mDataEnabled.set(enabled);
    }

    public boolean isEnabled() {
        return this.mDataEnabled.get();
    }

    public void setDependencyMet(boolean met) {
        this.mDependencyMet.set(met);
    }

    public boolean getDependencyMet() {
        return this.mDependencyMet.get();
    }

    public boolean isProvisioningApn() {
        String provisioningApn = this.mContext.getResources().getString(17039403);
        if (this.mApnSetting == null || this.mApnSetting.apn == null) {
            return false;
        }
        return this.mApnSetting.apn.equals(provisioningApn);
    }

    public void incRefCount() {
        synchronized (this.mRefCountLock) {
            int i = this.mRefCount;
            this.mRefCount = i + 1;
            if (i == 0) {
                this.mDcTracker.setEnabled(this.mDcTracker.apnTypeToId(this.mApnType), true);
            }
        }
    }

    public void decRefCount() {
        synchronized (this.mRefCountLock) {
            int i = this.mRefCount;
            this.mRefCount = i - 1;
            if (i == 1) {
                this.mDcTracker.setEnabled(this.mDcTracker.apnTypeToId(this.mApnType), false);
            }
        }
    }

    public synchronized String toString() {
        return "{mApnType=" + this.mApnType + " mState=" + getState() + " mWaitingApns={" + this.mWaitingApns + "} mWaitingApnsPermanentFailureCountDown=" + this.mWaitingApnsPermanentFailureCountDown + " mApnSetting={" + this.mApnSetting + "} mReason=" + this.mReason + " mDataEnabled=" + this.mDataEnabled + " mDependencyMet=" + this.mDependencyMet + "}";
    }

    protected void log(String s) {
        Rlog.d(this.LOG_TAG, "[ApnContext:" + this.mApnType + "] " + s);
    }

    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        pw.println("ApnContext: " + toString());
    }

    public synchronized State getPreviousState() {
        return this.mPreviousState;
    }

    public boolean isConnecting() {
        State currentState = getState();
        return currentState == State.CONNECTING || currentState == State.RETRYING || currentState == State.SCANNING;
    }

    public synchronized void setPermanentRetryCount(int retryCount) {
        this.mRetryCountPermanent = retryCount;
    }

    public synchronized int getPermanentRetryCount() {
        return this.mRetryCountPermanent;
    }
}
