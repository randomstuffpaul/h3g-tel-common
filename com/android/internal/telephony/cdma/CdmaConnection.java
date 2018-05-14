package com.android.internal.telephony.cdma;

import android.content.Context;
import android.os.AsyncResult;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.os.Registrant;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.provider.Telephony.Threads;
import android.telephony.PhoneNumberUtils;
import android.telephony.Rlog;
import android.text.TextUtils;
import com.android.internal.telephony.Call;
import com.android.internal.telephony.CallDetails;
import com.android.internal.telephony.CallStateException;
import com.android.internal.telephony.CallTracker;
import com.android.internal.telephony.Connection;
import com.android.internal.telephony.Connection.PostDialState;
import com.android.internal.telephony.DriverCall;
import com.android.internal.telephony.DriverCall.State;
import com.android.internal.telephony.SMSDispatcher;
import com.android.internal.telephony.UUSInfo;
import com.android.internal.telephony.uicc.IccCardApplicationStatus.AppState;
import com.android.internal.telephony.uicc.UiccCardApplication;
import com.android.internal.telephony.uicc.UiccController;
import com.google.android.mms.pdu.PduPart;

public class CdmaConnection extends Connection {
    static final int EVENT_DTMF_DONE = 1;
    static final int EVENT_NEXT_POST_DIAL = 3;
    static final int EVENT_PAUSE_DONE = 2;
    static final int EVENT_WAKE_LOCK_TIMEOUT = 4;
    static final String LOG_TAG = "CdmaConnection";
    static final int PAUSE_DELAY_MILLIS = 2000;
    private static final boolean VDBG = false;
    static final int WAKE_LOCK_TIMEOUT_MILLIS = 60000;
    boolean isCwActive;
    boolean isCwHolding;
    int mCause;
    long mDisconnectTime;
    boolean mDisconnected;
    Handler mHandler;
    int mIndex;
    boolean mIsInAnsweringMessage;
    boolean mNeedSendDAN;
    int mNextPostDialChar;
    CdmaCallTracker mOwner;
    CdmaCall mParent;
    private WakeLock mPartialWakeLock;
    PostDialState mPostDialState;
    String mPostDialString;
    int mPreciseCause;

    static /* synthetic */ class C00731 {
        static final /* synthetic */ int[] $SwitchMap$com$android$internal$telephony$DriverCall$State = new int[State.values().length];

        static {
            try {
                $SwitchMap$com$android$internal$telephony$DriverCall$State[State.ACTIVE.ordinal()] = 1;
            } catch (NoSuchFieldError e) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$DriverCall$State[State.DIALING.ordinal()] = 2;
            } catch (NoSuchFieldError e2) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$DriverCall$State[State.ALERTING.ordinal()] = 3;
            } catch (NoSuchFieldError e3) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$DriverCall$State[State.HOLDING.ordinal()] = 4;
            } catch (NoSuchFieldError e4) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$DriverCall$State[State.INCOMING.ordinal()] = 5;
            } catch (NoSuchFieldError e5) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$DriverCall$State[State.WAITING.ordinal()] = 6;
            } catch (NoSuchFieldError e6) {
            }
        }
    }

    class MyHandler extends Handler {
        MyHandler(Looper l) {
            super(l);
        }

        public void handleMessage(Message msg) {
            switch (msg.what) {
                case 1:
                case 2:
                case 3:
                    CdmaConnection.this.processNextPostDialChar();
                    return;
                case 4:
                    CdmaConnection.this.releaseWakeLock();
                    return;
                default:
                    return;
            }
        }
    }

    CdmaConnection(Context context, DriverCall dc, CdmaCallTracker ct, int index) {
        this.isCwActive = true;
        this.isCwHolding = false;
        this.mNeedSendDAN = false;
        this.mCause = 0;
        this.mPostDialState = PostDialState.NOT_STARTED;
        this.mPreciseCause = 0;
        this.mIsInAnsweringMessage = false;
        createWakeLock(context);
        acquireWakeLock();
        this.mOwner = ct;
        this.mHandler = new MyHandler(this.mOwner.getLooper());
        this.mAddress = dc.number;
        this.mIsIncoming = dc.isMT;
        this.mCreateTime = System.currentTimeMillis();
        this.mCnapName = dc.name;
        this.mCnapNamePresentation = dc.namePresentation;
        this.mNumberPresentation = dc.numberPresentation;
        this.mIndex = index;
        setId(dc.id);
        setCallDetails(dc.callDetails);
        if (dc.state == State.HOLDING || dc.state == State.WAITING) {
            this.isCwHolding = true;
            this.isCwActive = false;
        }
        this.mParent = parentFromDCState(dc.state);
        this.mParent.attach(this, dc);
    }

    CdmaConnection(Context context, String dialString, CdmaCallTracker ct, CdmaCall parent) {
        this(context, dialString, ct, parent, null);
    }

    CdmaConnection(Context context, String dialString, CdmaCallTracker ct, CdmaCall parent, CallDetails callDetails) {
        this.isCwActive = true;
        this.isCwHolding = false;
        this.mNeedSendDAN = false;
        this.mCause = 0;
        this.mPostDialState = PostDialState.NOT_STARTED;
        this.mPreciseCause = 0;
        this.mIsInAnsweringMessage = false;
        createWakeLock(context);
        acquireWakeLock();
        this.mOwner = ct;
        this.mHandler = new MyHandler(this.mOwner.getLooper());
        this.mDialString = dialString;
        dialString = formatDialString(dialString);
        this.mAddress = PhoneNumberUtils.extractNetworkPortionAlt(dialString);
        this.mPostDialString = PhoneNumberUtils.extractPostDialPortion(dialString);
        if (callDetails != null && "unknown".equals(callDetails.getExtraValue("participants"))) {
            this.mAddress = dialString;
        }
        setId(-1);
        setCallDetails(callDetails);
        this.mIndex = -1;
        this.mIsIncoming = false;
        this.mCnapName = null;
        this.mCnapNamePresentation = 1;
        this.mNumberPresentation = 1;
        this.mCreateTime = System.currentTimeMillis();
        if (parent != null) {
            this.mParent = parent;
            if (parent.mState == Call.State.ACTIVE) {
                parent.attachFake(this, Call.State.ACTIVE);
            } else {
                parent.attachFake(this, Call.State.DIALING);
            }
        }
    }

    CdmaConnection(Context context, CdmaCallWaitingNotification cw, CdmaCallTracker ct, CdmaCall parent) {
        this.isCwActive = true;
        this.isCwHolding = false;
        this.mNeedSendDAN = false;
        this.mCause = 0;
        this.mPostDialState = PostDialState.NOT_STARTED;
        this.mPreciseCause = 0;
        this.mIsInAnsweringMessage = false;
        createWakeLock(context);
        acquireWakeLock();
        this.mOwner = ct;
        this.mHandler = new MyHandler(this.mOwner.getLooper());
        this.mAddress = cw.number;
        this.mNumberPresentation = cw.numberPresentation;
        this.mCnapName = cw.name;
        this.mCnapNamePresentation = cw.namePresentation;
        this.mIndex = -1;
        this.mIsIncoming = true;
        this.mCreateTime = System.currentTimeMillis();
        this.mConnectTime = 0;
        this.mParent = parent;
        setId(-1);
        setCallDetails(new CallDetails());
        this.isCwHolding = true;
        this.isCwActive = false;
        parent.attachFake(this, Call.State.WAITING);
    }

    public void dispose() {
    }

    static boolean equalsHandlesNulls(Object a, Object b) {
        if (a == null) {
            return b == null;
        } else {
            return a.equals(b);
        }
    }

    boolean compareTo(DriverCall c) {
        if (!this.mIsIncoming && !c.isMT) {
            return true;
        }
        String cAddress = PhoneNumberUtils.stringFromStringAndTOA(c.number, c.TOA);
        if (this.mIsIncoming == c.isMT && equalsHandlesNulls(this.mAddress, cAddress)) {
            return true;
        }
        return false;
    }

    public String getOrigDialString() {
        return this.mDialString;
    }

    public String getAddress() {
        return this.mAddress;
    }

    public CdmaCall getCall() {
        return this.mParent;
    }

    public long getDisconnectTime() {
        return this.mDisconnectTime;
    }

    public long getHoldDurationMillis() {
        if (getState() != Call.State.HOLDING) {
            return 0;
        }
        return SystemClock.elapsedRealtime() - this.mHoldingStartTime;
    }

    public int getDisconnectCause() {
        return this.mCause;
    }

    public Call.State getState() {
        if (this.mDisconnected) {
            return Call.State.DISCONNECTED;
        }
        if (!isCdmaCwHolding() || (this.callDetails != null && (this.callDetails == null || this.callDetails.call_domain == 2))) {
            return super.getState();
        }
        return Call.State.HOLDING;
    }

    public void hangup() throws CallStateException {
        if (this.mDisconnected) {
            throw new CallStateException("disconnected");
        }
        this.mOwner.hangup(this);
    }

    public void separate() throws CallStateException {
        if (this.mDisconnected) {
            throw new CallStateException("disconnected");
        }
        this.mOwner.separate(this);
    }

    public PostDialState getPostDialState() {
        return this.mPostDialState;
    }

    public void proceedAfterWaitChar() {
        if (this.mPostDialState != PostDialState.WAIT) {
            Rlog.w(LOG_TAG, "CdmaConnection.proceedAfterWaitChar(): Expected getPostDialState() to be WAIT but was " + this.mPostDialState);
            return;
        }
        setPostDialState(PostDialState.STARTED);
        processNextPostDialChar();
    }

    public void proceedAfterWildChar(String str) {
        if (this.mPostDialState != PostDialState.WILD) {
            Rlog.w(LOG_TAG, "CdmaConnection.proceedAfterWaitChar(): Expected getPostDialState() to be WILD but was " + this.mPostDialState);
            return;
        }
        setPostDialState(PostDialState.STARTED);
        StringBuilder buf = new StringBuilder(str);
        buf.append(this.mPostDialString.substring(this.mNextPostDialChar));
        this.mPostDialString = buf.toString();
        this.mNextPostDialChar = 0;
        log("proceedAfterWildChar: new postDialString is " + this.mPostDialString);
        processNextPostDialChar();
    }

    public void cancelPostDial() {
        setPostDialState(PostDialState.CANCELLED);
    }

    void onHangupLocal() {
        this.mCause = 3;
        this.mPreciseCause = 0;
    }

    int disconnectCauseFromCode(int causeCode) {
        switch (causeCode) {
            case 17:
                return 4;
            case 34:
                return 5;
            case 68:
                return 15;
            case 240:
                return 20;
            case 241:
                return 21;
            case 1000:
                return 26;
            case CallFailCause.CDMA_DROP /*1001*/:
                return 27;
            case CallFailCause.CDMA_INTERCEPT /*1002*/:
                return 28;
            case CallFailCause.CDMA_REORDER /*1003*/:
                return 29;
            case CallFailCause.CDMA_SO_REJECT /*1004*/:
                return 30;
            case CallFailCause.CDMA_RETRY_ORDER /*1005*/:
                return 31;
            case CallFailCause.CDMA_ACCESS_FAILURE /*1006*/:
                return 32;
            case CallFailCause.CDMA_PREEMPTED /*1007*/:
                return 33;
            case CallFailCause.CDMA_NOT_EMERGENCY /*1008*/:
                return 34;
            case CallFailCause.CDMA_ACCESS_BLOCKED /*1009*/:
                return 35;
            case 1100:
                return Threads.ALERT_AMBER_THREAD;
            default:
                CDMAPhone phone = this.mOwner.mPhone;
                int serviceState = phone.getServiceState().getState();
                UiccCardApplication app = UiccController.getInstance().getUiccCardApplication(2);
                AppState uiccAppState = app != null ? app.getState() : AppState.APPSTATE_UNKNOWN;
                if (this.sipError > 0 && this.sipError != PduPart.P_CONTENT_TRANSFER_ENCODING) {
                    return Threads.ALERT_EXTREME_THREAD;
                }
                if (this.sipError == PduPart.P_CONTENT_TRANSFER_ENCODING) {
                    return 2;
                }
                if (serviceState == 3) {
                    return 17;
                }
                if (serviceState == 1 || serviceState == 2) {
                    return 18;
                }
                if (phone.mCdmaSubscriptionSource == 0 && uiccAppState != AppState.APPSTATE_READY) {
                    return 19;
                }
                if (causeCode != 16) {
                    return 36;
                }
                return 2;
        }
    }

    void onRemoteDisconnect(int causeCode, int sipError) {
        this.sipError = sipError;
        onRemoteDisconnect(causeCode);
    }

    void onRemoteDisconnect(int causeCode) {
        this.mPreciseCause = causeCode;
        onDisconnect(disconnectCauseFromCode(causeCode));
    }

    boolean onDisconnect(int cause) {
        boolean changed = false;
        this.mCause = cause;
        if (!this.mDisconnected) {
            doDisconnect();
            this.mOwner.mPhone.notifyDisconnect(this);
            if (this.mParent != null) {
                changed = this.mParent.connectionDisconnected(this);
            }
        }
        releaseWakeLock();
        return changed;
    }

    void onLocalDisconnect() {
        if (!this.mDisconnected) {
            doDisconnect();
            if (this.mParent != null) {
                this.mParent.detach(this);
            }
        }
        releaseWakeLock();
    }

    boolean update(DriverCall dc) {
        boolean wasHolding;
        boolean changed = false;
        boolean wasConnectingInOrOut = isConnectingInOrOut();
        if (getState() == Call.State.HOLDING) {
            wasHolding = true;
        } else {
            wasHolding = false;
        }
        CdmaCall newParent = parentFromDCState(dc.state);
        log("parent= " + this.mParent + ", newParent= " + newParent);
        log(" mNumberConverted " + this.mNumberConverted);
        if (!(equalsHandlesNulls(this.mAddress, dc.number) || (this.mNumberConverted && equalsHandlesNulls(this.mConvertedNumber, dc.number)))) {
            log("update: phone # changed!");
            this.mAddress = dc.number;
            changed = true;
        }
        if (TextUtils.isEmpty(dc.name)) {
            if (!TextUtils.isEmpty(this.mCnapName)) {
                changed = true;
                this.mCnapName = "";
            }
        } else if (!dc.name.equals(this.mCnapName)) {
            changed = true;
            this.mCnapName = dc.name;
        }
        log("--dssds----" + this.mCnapName);
        this.mCnapNamePresentation = dc.namePresentation;
        this.mNumberPresentation = dc.numberPresentation;
        if (newParent != this.mParent) {
            if (this.mParent != null) {
                this.mParent.detach(this);
            }
            newParent.attach(this, dc);
            this.mParent = newParent;
            changed = true;
        } else {
            changed = changed || this.mParent.update(this, dc);
        }
        if (dc.state == State.HOLDING || dc.state == State.WAITING) {
            this.isCwHolding = true;
            this.isCwActive = false;
        } else {
            this.isCwActive = true;
            this.isCwHolding = false;
        }
        if ("VZW-CDMA".equals("")) {
            dc.callDetails.setIsMpty(dc.isMpty);
        }
        setId(dc.id);
        changed |= setCallDetails(dc.callDetails);
        log("Update, wasConnectingInOrOut=" + wasConnectingInOrOut + ", wasHolding=" + wasHolding + ", isConnectingInOrOut=" + isConnectingInOrOut() + ", changed=" + changed);
        if (wasConnectingInOrOut && !isConnectingInOrOut()) {
            onConnectedInOrOut();
        }
        if (changed && !wasHolding && getState() == Call.State.HOLDING) {
            onStartedHolding();
        }
        log("update() - callDetails: " + this.callDetails);
        return changed;
    }

    void fakeHoldBeforeDial() {
        if (this.mParent != null) {
            this.mParent.detach(this);
        }
        this.mParent = this.mOwner.mBackgroundCall;
        this.mParent.attachFake(this, Call.State.HOLDING);
        onStartedHolding();
    }

    int getCDMAIndex() throws CallStateException {
        if (this.mIndex >= 0) {
            return this.mIndex + 1;
        }
        throw new CallStateException("CDMA connection index not assigned");
    }

    void onConnectedInOrOut() {
        this.mConnectTime = System.currentTimeMillis();
        this.mConnectTimeReal = SystemClock.elapsedRealtime();
        this.mDuration = 0;
        log("onConnectedInOrOut: connectTime=" + this.mConnectTime);
        if (this.mIsIncoming) {
            releaseWakeLock();
        } else {
            processNextPostDialChar();
        }
    }

    private void doDisconnect() {
        String salesCode = SystemProperties.get("ro.csc.sales_code");
        this.mIndex = -1;
        this.mDisconnectTime = System.currentTimeMillis();
        this.mDuration = SystemClock.elapsedRealtime() - this.mConnectTimeReal;
        if ("LGT".equals("")) {
            if (this.mParent.getEarliestConnection() == this) {
                this.mDuration = SystemClock.elapsedRealtime() - this.mConnectTimeReal;
            } else {
                this.mDuration = 0;
            }
        }
        if (this.mNeedSendDAN) {
            SMSDispatcher smsDispatcher = this.mOwner.mPhone.getSMSDispatcher();
            if (smsDispatcher != null) {
                Rlog.d(LOG_TAG, "Send DAN - Start");
                smsDispatcher.sendDomainChangeSms((byte) 0);
            }
            this.mNeedSendDAN = false;
        }
        if ("CTC".equals(salesCode)) {
            this.mDuration = SystemClock.elapsedRealtime() - this.mConnectTimeReal;
        }
        this.mDisconnected = true;
        clearPostDialListeners();
    }

    void onStartedHolding() {
        this.mHoldingStartTime = SystemClock.elapsedRealtime();
    }

    private boolean processPostDialChar(char c) {
        if (PhoneNumberUtils.is12Key(c)) {
            this.mOwner.mCi.sendBurstDtmf(Character.toString(c), 0, 0, null);
            this.mHandler.sendMessageDelayed(this.mHandler.obtainMessage(1), 200);
        } else if (c == ',') {
            setPostDialState(PostDialState.PAUSE);
            this.mHandler.sendMessageDelayed(this.mHandler.obtainMessage(2), 2000);
        } else if (c == ';') {
            setPostDialState(PostDialState.WAIT);
        } else if (c != 'N') {
            return false;
        } else {
            setPostDialState(PostDialState.WILD);
        }
        return true;
    }

    public String getRemainingPostDialString() {
        if (this.mPostDialState == PostDialState.CANCELLED || this.mPostDialState == PostDialState.COMPLETE || this.mPostDialString == null || this.mPostDialString.length() <= this.mNextPostDialChar) {
            return "";
        }
        String subStr = this.mPostDialString.substring(this.mNextPostDialChar);
        if (subStr == null) {
            return subStr;
        }
        int wIndex = subStr.indexOf(59);
        int pIndex = subStr.indexOf(44);
        if (wIndex > 0 && (wIndex < pIndex || pIndex <= 0)) {
            return subStr.substring(0, wIndex);
        }
        if (pIndex > 0) {
            return subStr.substring(0, pIndex);
        }
        return subStr;
    }

    public void updateParent(CdmaCall oldParent, CdmaCall newParent) {
        if (newParent != oldParent) {
            if (oldParent != null) {
                oldParent.detach(this);
            }
            newParent.attachFake(this, Call.State.ACTIVE);
            this.mParent = newParent;
        }
    }

    protected void finalize() {
        if (this.mPartialWakeLock.isHeld()) {
            Rlog.e(LOG_TAG, "[CdmaConn] UNEXPECTED; mPartialWakeLock is held when finalizing.");
        }
        releaseWakeLock();
    }

    void processNextPostDialChar() {
        if (this.mPostDialState == PostDialState.CANCELLED) {
            releaseWakeLock();
            return;
        }
        char c;
        if (this.mPostDialString == null || this.mPostDialString.length() <= this.mNextPostDialChar) {
            setPostDialState(PostDialState.COMPLETE);
            releaseWakeLock();
            c = '\u0000';
        } else {
            setPostDialState(PostDialState.STARTED);
            String str = this.mPostDialString;
            int i = this.mNextPostDialChar;
            this.mNextPostDialChar = i + 1;
            c = str.charAt(i);
            if (!processPostDialChar(c)) {
                this.mHandler.obtainMessage(3).sendToTarget();
                Rlog.e("CDMA", "processNextPostDialChar: c=" + c + " isn't valid!");
                return;
            }
        }
        Registrant postDialHandler = this.mOwner.mPhone.mPostDialHandler;
        if (postDialHandler != null) {
            Message notifyMessage = postDialHandler.messageForRegistrant();
            if (notifyMessage != null) {
                PostDialState state = this.mPostDialState;
                AsyncResult ar = AsyncResult.forMessage(notifyMessage);
                ar.result = this;
                ar.userObj = state;
                notifyMessage.arg1 = c;
                notifyMessage.sendToTarget();
            }
        }
    }

    private boolean isConnectingInOrOut() {
        return this.mParent == null || this.mParent == this.mOwner.mRingingCall || this.mParent.mState == Call.State.DIALING || this.mParent.mState == Call.State.ALERTING;
    }

    private CdmaCall parentFromDCState(State state) {
        switch (C00731.$SwitchMap$com$android$internal$telephony$DriverCall$State[state.ordinal()]) {
            case 1:
            case 2:
            case 3:
                return this.mOwner.mForegroundCall;
            case 4:
                return this.mOwner.mBackgroundCall;
            case 5:
            case 6:
                return this.mOwner.mRingingCall;
            default:
                throw new RuntimeException("illegal call state: " + state);
        }
    }

    private void setPostDialState(PostDialState s) {
        if (s == PostDialState.STARTED || s == PostDialState.PAUSE) {
            synchronized (this.mPartialWakeLock) {
                if (this.mPartialWakeLock.isHeld()) {
                    this.mHandler.removeMessages(4);
                } else {
                    acquireWakeLock();
                }
                this.mHandler.sendMessageDelayed(this.mHandler.obtainMessage(4), 60000);
            }
        } else {
            this.mHandler.removeMessages(4);
            releaseWakeLock();
        }
        this.mPostDialState = s;
        notifyPostDialListeners();
    }

    private void createWakeLock(Context context) {
        this.mPartialWakeLock = ((PowerManager) context.getSystemService("power")).newWakeLock(1, LOG_TAG);
    }

    private void acquireWakeLock() {
        log("acquireWakeLock");
        this.mPartialWakeLock.acquire();
    }

    private void releaseWakeLock() {
        synchronized (this.mPartialWakeLock) {
            if (this.mPartialWakeLock.isHeld()) {
                log("releaseWakeLock");
                this.mPartialWakeLock.release();
            }
        }
    }

    private static boolean isPause(char c) {
        return c == ',';
    }

    private static boolean isWait(char c) {
        return c == ';';
    }

    private static int findNextPCharOrNonPOrNonWCharIndex(String phoneNumber, int currIndex) {
        boolean wMatched = isWait(phoneNumber.charAt(currIndex));
        int index = currIndex + 1;
        int length = phoneNumber.length();
        while (index < length) {
            char cNext = phoneNumber.charAt(index);
            if (isWait(cNext)) {
                wMatched = true;
            }
            if (!isWait(cNext) && !isPause(cNext)) {
                break;
            }
            index++;
        }
        if (index >= length || index <= currIndex + 1 || wMatched || !isPause(phoneNumber.charAt(currIndex))) {
            return index;
        }
        return currIndex + 1;
    }

    private static char findPOrWCharToAppend(String phoneNumber, int currPwIndex, int nextNonPwCharIndex) {
        char ret = isPause(phoneNumber.charAt(currPwIndex)) ? ',' : ';';
        if (nextNonPwCharIndex > currPwIndex + 1) {
            return ';';
        }
        return ret;
    }

    public static String formatDialString(String phoneNumber) {
        if (phoneNumber == null) {
            return null;
        }
        int length = phoneNumber.length();
        StringBuilder ret = new StringBuilder();
        int currIndex = 0;
        while (currIndex < length) {
            char c = phoneNumber.charAt(currIndex);
            if (!isPause(c) && !isWait(c)) {
                ret.append(c);
            } else if (currIndex < length - 1) {
                int nextIndex = findNextPCharOrNonPOrNonWCharIndex(phoneNumber, currIndex);
                if (nextIndex < length) {
                    ret.append(findPOrWCharToAppend(phoneNumber, currIndex, nextIndex));
                    if (nextIndex > currIndex + 1) {
                        currIndex = nextIndex - 1;
                    }
                } else if (nextIndex == length) {
                    currIndex = length - 1;
                }
            }
            currIndex++;
        }
        return PhoneNumberUtils.cdmaCheckAndProcessPlusCode(ret.toString());
    }

    public static String formatDialString(String phoneNumber, Context context) {
        if (phoneNumber == null) {
            return null;
        }
        int length = phoneNumber.length();
        StringBuilder ret = new StringBuilder();
        int currIndex = 0;
        while (currIndex < length) {
            char c = phoneNumber.charAt(currIndex);
            if (!isPause(c) && !isWait(c)) {
                ret.append(c);
            } else if (currIndex < length - 1) {
                int nextIndex = findNextPCharOrNonPOrNonWCharIndex(phoneNumber, currIndex);
                if (nextIndex < length) {
                    ret.append(findPOrWCharToAppend(phoneNumber, currIndex, nextIndex));
                    if (nextIndex > currIndex + 1) {
                        currIndex = nextIndex - 1;
                    }
                } else if (nextIndex == length) {
                    currIndex = length - 1;
                }
            }
            currIndex++;
        }
        return PhoneNumberUtils.cdmaCheckAndProcessPlusCode(ret.toString(), context);
    }

    private void log(String msg) {
        Rlog.d(LOG_TAG, "[CDMAConn] " + msg);
    }

    public int getNumberPresentation() {
        return this.mNumberPresentation;
    }

    public UUSInfo getUUSInfo() {
        return null;
    }

    public int getPreciseDisconnectCause() {
        return this.mPreciseCause;
    }

    public Connection getOrigConnection() {
        return null;
    }

    public boolean isMultiparty() {
        return false;
    }

    public boolean isCdmaCwActive() {
        return this.isCwActive;
    }

    public boolean isCdmaCwHolding() {
        return this.isCwHolding;
    }

    public void resetConnectTimeReal() {
        this.mConnectTime = System.currentTimeMillis();
        this.mConnectTimeReal = SystemClock.elapsedRealtime();
    }

    protected int getIndex() {
        return this.mIndex;
    }

    protected CallTracker getOwner() {
        return this.mOwner;
    }

    public void setAnsweringMessageState(boolean enabled) {
        this.mIsInAnsweringMessage = enabled;
    }
}
