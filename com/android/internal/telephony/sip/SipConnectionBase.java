package com.android.internal.telephony.sip;

import android.os.SystemClock;
import android.telephony.PhoneNumberUtils;
import android.telephony.Rlog;
import com.android.internal.telephony.Call.State;
import com.android.internal.telephony.Connection;
import com.android.internal.telephony.Connection.PostDialState;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.UUSInfo;

abstract class SipConnectionBase extends Connection {
    private static final boolean DBG = true;
    private static final String LOG_TAG = "SipConnBase";
    private static final boolean VDBG = false;
    private int mCause = 0;
    private long mConnectTime;
    private long mConnectTimeReal;
    private long mCreateTime;
    private long mDisconnectTime;
    private long mDuration = -1;
    private long mHoldingStartTime;
    private int mNextPostDialChar;
    private PostDialState mPostDialState = PostDialState.NOT_STARTED;
    private String mPostDialString;

    static /* synthetic */ class C01231 {
        static final /* synthetic */ int[] $SwitchMap$com$android$internal$telephony$Call$State = new int[State.values().length];

        static {
            try {
                $SwitchMap$com$android$internal$telephony$Call$State[State.ACTIVE.ordinal()] = 1;
            } catch (NoSuchFieldError e) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$Call$State[State.DISCONNECTED.ordinal()] = 2;
            } catch (NoSuchFieldError e2) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$Call$State[State.HOLDING.ordinal()] = 3;
            } catch (NoSuchFieldError e3) {
            }
        }
    }

    protected abstract Phone getPhone();

    SipConnectionBase(String dialString) {
        log("SipConnectionBase: ctor dialString=" + dialString);
        this.mPostDialString = PhoneNumberUtils.extractPostDialPortion(dialString);
        this.mCreateTime = System.currentTimeMillis();
    }

    protected void setState(State state) {
        log("setState: state=" + state);
        switch (C01231.$SwitchMap$com$android$internal$telephony$Call$State[state.ordinal()]) {
            case 1:
                if (this.mConnectTime == 0) {
                    this.mConnectTimeReal = SystemClock.elapsedRealtime();
                    this.mConnectTime = System.currentTimeMillis();
                    return;
                }
                return;
            case 2:
                this.mDuration = getDurationMillis();
                this.mDisconnectTime = System.currentTimeMillis();
                return;
            case 3:
                this.mHoldingStartTime = SystemClock.elapsedRealtime();
                return;
            default:
                return;
        }
    }

    public long getCreateTime() {
        return this.mCreateTime;
    }

    public long getConnectTime() {
        return this.mConnectTime;
    }

    public long getDisconnectTime() {
        return this.mDisconnectTime;
    }

    public long getDurationMillis() {
        if (this.mConnectTimeReal == 0) {
            return 0;
        }
        if (this.mDuration < 0) {
            return SystemClock.elapsedRealtime() - this.mConnectTimeReal;
        }
        return this.mDuration;
    }

    public long getHoldDurationMillis() {
        if (getState() != State.HOLDING) {
            return 0;
        }
        return SystemClock.elapsedRealtime() - this.mHoldingStartTime;
    }

    public int getDisconnectCause() {
        return this.mCause;
    }

    void setDisconnectCause(int cause) {
        log("setDisconnectCause: prev=" + this.mCause + " new=" + cause);
        this.mCause = cause;
    }

    public PostDialState getPostDialState() {
        return this.mPostDialState;
    }

    public void proceedAfterWaitChar() {
        log("proceedAfterWaitChar: ignore");
    }

    public void proceedAfterWildChar(String str) {
        log("proceedAfterWildChar: ignore");
    }

    public void cancelPostDial() {
        log("cancelPostDial: ignore");
    }

    public String getRemainingPostDialString() {
        if (this.mPostDialState != PostDialState.CANCELLED && this.mPostDialState != PostDialState.COMPLETE && this.mPostDialString != null && this.mPostDialString.length() > this.mNextPostDialChar) {
            return this.mPostDialString.substring(this.mNextPostDialChar);
        }
        log("getRemaingPostDialString: ret empty string");
        return "";
    }

    private void log(String msg) {
        Rlog.d(LOG_TAG, msg);
    }

    public int getNumberPresentation() {
        return 1;
    }

    public UUSInfo getUUSInfo() {
        return null;
    }

    public int getPreciseDisconnectCause() {
        return 0;
    }

    public long getHoldingStartTime() {
        return this.mHoldingStartTime;
    }

    public long getConnectTimeReal() {
        return this.mConnectTimeReal;
    }

    public Connection getOrigConnection() {
        return null;
    }

    public boolean isMultiparty() {
        return false;
    }
}
