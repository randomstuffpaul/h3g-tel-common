package com.android.internal.telephony.gsm;

import android.media.AudioManager;
import android.os.AsyncResult;
import android.os.Handler;
import android.os.Message;
import android.os.Registrant;
import android.os.RegistrantList;
import android.os.SystemProperties;
import android.provider.Telephony.Threads;
import android.telephony.PhoneNumberUtils;
import android.telephony.Rlog;
import android.telephony.TelephonyManager;
import android.telephony.gsm.GsmCellLocation;
import android.util.EventLog;
import com.android.ims.ImsCallProfile;
import com.android.internal.telephony.Call;
import com.android.internal.telephony.Call.SrvccState;
import com.android.internal.telephony.CallDetails;
import com.android.internal.telephony.CallModify;
import com.android.internal.telephony.CallStateBroadcaster;
import com.android.internal.telephony.CallStateBroadcaster.InstanceLock;
import com.android.internal.telephony.CallStateException;
import com.android.internal.telephony.CallTracker;
import com.android.internal.telephony.Connection;
import com.android.internal.telephony.DriverCall;
import com.android.internal.telephony.EventLogTags;
import com.android.internal.telephony.Phone.SuppService;
import com.android.internal.telephony.PhoneBase;
import com.android.internal.telephony.PhoneConstants.State;
import com.android.internal.telephony.UUSInfo;
import com.samsung.android.telephony.MultiSimManager;
import com.sec.android.app.CscFeature;
import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

public final class GsmCallTracker extends CallTracker {
    private static final boolean DBG_POLL = true;
    static final String LOG_TAG = "GsmCallTracker";
    static final int MAX_CONNECTIONS = 7;
    static final int MAX_CONNECTIONS_PER_CALL = 5;
    private static final boolean REPEAT_POLLING = false;
    Boolean[] connWaitActive = new Boolean[7];
    String emergencyCatJPN = null;
    boolean isEmergencyCallPending = false;
    AudioManager mAudioManager;
    GsmCall mBackgroundCall = new GsmCall(this);
    private CallStateBroadcasterA mCallStateBroadcasterA;
    private InstanceLock mCallStateBroadcasterLock;
    GsmConnection[] mConnections = new GsmConnection[7];
    boolean mDesiredMute = false;
    ArrayList<GsmConnection> mDroppedDuringPoll = new ArrayList(7);
    GsmCall mForegroundCall = new GsmCall(this);
    boolean mHangupPendingMO;
    GsmConnection mPendingMO;
    GSMPhone mPhone;
    GsmCall mRingingCall = new GsmCall(this);
    SrvccState mSrvccState = SrvccState.NONE;
    State mState = State.IDLE;
    RegistrantList mVoiceCallEndedRegistrants = new RegistrantList();
    RegistrantList mVoiceCallStartedRegistrants = new RegistrantList();
    private int mVsid = -1;
    String pendingEmergencyNum = null;

    GsmCallTracker(GSMPhone phone) {
        this.mPhone = phone;
        this.mCi = phone.mCi;
        this.mCi.registerForCallStateChanged(this, 2, null);
        this.mCi.registerForOn(this, 9, null);
        this.mCi.registerForNotAvailable(this, 10, null);
        if ("Combination".equals("Combination")) {
            this.mCi.registerForVoiceSystemIdNotified(this, Threads.ALERT_AMBER_THREAD, null);
        }
        this.mAudioManager = (AudioManager) phone.getContext().getSystemService("audio");
        if (CscFeature.getInstance().getEnableStatus("CscFeature_CIQ_BroadcastState")) {
            this.mCallStateBroadcasterLock = new InstanceLock(phone.getContext());
        }
        for (int i = 0; i < 7; i++) {
            this.connWaitActive[i] = Boolean.valueOf(false);
        }
        if ("".equals("ATT")) {
            this.mCallStateBroadcasterA = new CallStateBroadcasterA(phone);
            Rlog.d(LOG_TAG, "new CallStateBroadcasterA");
        }
        this.mCi.registerForModifyCall(this, Threads.ALERT_SEVERE_THREAD, null);
    }

    public void dispose() {
        Rlog.d(LOG_TAG, "GsmCallTracker dispose");
        if ("".equals("ATT")) {
            this.mCallStateBroadcasterA.dispose();
            this.mCallStateBroadcasterA = null;
        }
        if (CscFeature.getInstance().getEnableStatus("CscFeature_CIQ_BroadcastState")) {
            this.mCallStateBroadcasterLock = null;
        }
        this.mCi.unregisterForCallStateChanged(this);
        this.mCi.unregisterForOn(this);
        this.mCi.unregisterForNotAvailable(this);
        this.mCi.unregisterForModifyCall(this);
        if ("Combination".equals("Combination")) {
            this.mCi.unregisterForVoiceSystemIdNotified(this);
        }
        clearDisconnected();
    }

    protected void finalize() {
        Rlog.d(LOG_TAG, "GsmCallTracker finalized");
    }

    public void registerForVoiceCallStarted(Handler h, int what, Object obj) {
        this.mVoiceCallStartedRegistrants.add(new Registrant(h, what, obj));
    }

    public void unregisterForVoiceCallStarted(Handler h) {
        this.mVoiceCallStartedRegistrants.remove(h);
    }

    public void registerForVoiceCallEnded(Handler h, int what, Object obj) {
        this.mVoiceCallEndedRegistrants.add(new Registrant(h, what, obj));
    }

    public void unregisterForVoiceCallEnded(Handler h) {
        this.mVoiceCallEndedRegistrants.remove(h);
    }

    private void fakeHangupForegroundBeforeDial() {
        int s = this.mForegroundCall.mConnections.size();
        for (int i = 0; i < s; i++) {
            ((GsmConnection) this.mForegroundCall.mConnections.get(i)).fakeHangupBeforeDial();
        }
    }

    private void fakeHoldForegroundBeforeDial() {
        List<Connection> connCopy = (List) this.mForegroundCall.mConnections.clone();
        int s = connCopy.size();
        for (int i = 0; i < s; i++) {
            GsmConnection conn = (GsmConnection) connCopy.get(i);
            conn.fakeHoldBeforeDial();
            if (CscFeature.getInstance().getEnableStatus("CscFeature_CIQ_BroadcastState")) {
                CallStateBroadcaster.SendCallStatus(conn.getAddress(), Call.State.HOLDING);
            }
        }
    }

    synchronized Connection dial(String dialString, int clirMode, UUSInfo uusInfo) throws CallStateException {
        return dial(dialString, clirMode, uusInfo, null);
    }

    synchronized Connection dial(String dialString, int clirMode, UUSInfo uusInfo, CallDetails callDetails) throws CallStateException {
        clearDisconnected();
        if (canDial()) {
            String origNumber = dialString;
            dialString = convertNumberIfNecessary(this.mPhone, dialString);
            if (this.mForegroundCall.getState() == Call.State.ACTIVE) {
                switchWaitingOrHoldingAndActive();
                fakeHoldForegroundBeforeDial();
            }
            if (this.mForegroundCall.getState() != Call.State.IDLE) {
                throw new CallStateException("cannot dial in current state");
            }
            if (callDetails == null) {
                callDetails = new CallDetails();
            }
            if ("ps".equals(SystemProperties.get("persist.radio.test_calldomain", "cs"))) {
                callDetails.call_domain = 2;
            }
            this.mPendingMO = new GsmConnection(this.mPhone.getContext(), checkForTestEmergencyNumber(dialString), this, this.mForegroundCall, callDetails);
            this.mHangupPendingMO = false;
            if (this.mPendingMO.getAddress() == null || this.mPendingMO.getAddress().length() == 0 || this.mPendingMO.getAddress().indexOf(78) >= 0) {
                this.mPendingMO.mCause = 7;
                pollCallsWhenSafe();
            } else {
                setMute(false);
                String emergencyCat = PhoneNumberUtils.getEmergencyServiceCategory(this.mPhone.getSubId(), this.mPendingMO.getAddress());
                if (emergencyCat == null) {
                    if (callDetails.call_domain == 2) {
                        this.mHasVolteCall = true;
                    }
                    if (callDetails.call_type == 3) {
                        this.mHasVideoCall = true;
                    }
                    this.mCi.dial(this.mPendingMO.getAddress(), clirMode, uusInfo, callDetails, obtainCompleteMessage());
                } else if (checkEmergencyCallRedirectToNormalCall()) {
                    this.mCi.dial(this.mPendingMO.getAddress(), clirMode, uusInfo, callDetails, obtainCompleteMessage());
                } else {
                    this.mCi.dialEmergencyCall(this.mPendingMO.getAddress() + "/" + emergencyCat, clirMode, obtainCompleteMessage());
                }
            }
            if (this.mNumberConverted) {
                this.mPendingMO.setConverted(origNumber);
                this.mNumberConverted = false;
            }
            updatePhoneState();
            this.mPhone.notifyPreciseCallStateChanged();
        } else {
            throw new CallStateException("cannot dial in current state");
        }
        return this.mPendingMO;
    }

    Connection dial(String dialString) throws CallStateException {
        return dial(dialString, 0, null);
    }

    Connection dial(String dialString, UUSInfo uusInfo) throws CallStateException {
        return dial(dialString, 0, uusInfo);
    }

    Connection dial(String dialString, int clirMode) throws CallStateException {
        return dial(dialString, clirMode, null);
    }

    Connection dial(String convertedDialString, String originalDialString, UUSInfo uusInfo, CallDetails callDetails) throws CallStateException {
        return dial(convertedDialString, 0, uusInfo, callDetails);
    }

    void acceptCall() throws CallStateException {
        Rlog.d(LOG_TAG, "No videoState value. Use AUDIO_ONLY");
        acceptCall(0);
    }

    void acceptCall(int videoState) throws CallStateException {
        boolean isKor;
        int callType;
        if ("SKT".equals("") || "KTT".equals("") || "LGT".equals("")) {
            isKor = true;
        } else {
            isKor = false;
        }
        if (isKor && videoState == 0) {
            int ringingCallType = getCallType(this.mRingingCall);
            if (ringingCallType == 10) {
                Rlog.e(LOG_TAG, "acceptCall(): unknown ringing call type. bail.");
                return;
            }
            callType = ringingCallType;
        } else {
            int callTypeFromVideoState = ImsCallProfile.getCallTypeFromVideoState(videoState);
            callType = 0;
            if (callTypeFromVideoState == 5) {
                callType = 1;
            } else if (callTypeFromVideoState == 6) {
                callType = 2;
            } else if (callTypeFromVideoState == 4) {
                callType = 3;
            } else if (callTypeFromVideoState != 2) {
                Rlog.d(LOG_TAG, "Unexpected call type: " + callTypeFromVideoState);
            }
        }
        if (this.mRingingCall.getState() == Call.State.INCOMING) {
            Rlog.i("phone", "acceptCall: incoming...");
            setMute(false);
            if (CscFeature.getInstance().getEnableStatus("CscFeature_RIL_SupportVolte")) {
                this.mCi.acceptCall(callType, obtainCompleteMessage());
            } else {
                this.mCi.acceptCall(obtainCompleteMessage());
            }
        } else if (this.mRingingCall.getState() == Call.State.WAITING) {
            setMute(false);
            switchWaitingOrHoldingAndActive();
        } else {
            throw new CallStateException("phone not ringing");
        }
    }

    void rejectCall() throws CallStateException {
        if (this.mRingingCall.getState().isRinging()) {
            this.mCi.rejectCall(obtainCompleteMessage());
            return;
        }
        throw new CallStateException("phone not ringing");
    }

    void switchWaitingOrHoldingAndActive() throws CallStateException {
        if (this.mRingingCall.getState() == Call.State.INCOMING) {
            throw new CallStateException("cannot be in the incoming state");
        }
        this.mCi.switchWaitingOrHoldingAndActive(obtainCompleteMessage(8));
    }

    void conference() {
        this.mCi.conference(obtainCompleteMessage(11));
    }

    void explicitCallTransfer() {
        this.mCi.explicitCallTransfer(obtainCompleteMessage(13));
    }

    void clearDisconnected() {
        internalClearDisconnected();
        updatePhoneState();
        this.mPhone.notifyPreciseCallStateChanged();
    }

    boolean canConference() {
        return this.mForegroundCall.getState() == Call.State.ACTIVE && this.mBackgroundCall.getState() == Call.State.HOLDING && !this.mBackgroundCall.isFull() && !this.mForegroundCall.isFull();
    }

    boolean canDial() {
        return ((this.mPhone.getServiceState().getState() == 3 && !this.mPhone.isWfcRegistered()) || this.mPendingMO != null || this.mRingingCall.isRinging() || SystemProperties.get("ro.telephony.disable-call", "false").equals("true") || ((this.mForegroundCall.getState().isAlive() && this.mBackgroundCall.getState().isAlive()) || this.mForegroundCall.isVideoCall())) ? false : true;
    }

    boolean canTransfer() {
        return (this.mForegroundCall.getState() == Call.State.ACTIVE || this.mForegroundCall.getState() == Call.State.ALERTING || this.mForegroundCall.getState() == Call.State.DIALING) && this.mBackgroundCall.getState() == Call.State.HOLDING;
    }

    private void internalClearDisconnected() {
        this.mRingingCall.clearDisconnected();
        this.mForegroundCall.clearDisconnected();
        this.mBackgroundCall.clearDisconnected();
    }

    private Message obtainCompleteMessage() {
        return obtainCompleteMessage(4);
    }

    private Message obtainCompleteMessage(int what) {
        this.mPendingOperations++;
        this.mLastRelevantPoll = null;
        this.mNeedsPoll = true;
        log("obtainCompleteMessage: pendingOperations=" + this.mPendingOperations + ", needsPoll=" + this.mNeedsPoll);
        return obtainMessage(what);
    }

    private void operationComplete() {
        this.mPendingOperations--;
        log("operationComplete: pendingOperations=" + this.mPendingOperations + ", needsPoll=" + this.mNeedsPoll);
        if (this.mPendingOperations == 0 && this.mNeedsPoll) {
            this.mLastRelevantPoll = obtainMessage(1);
            this.mCi.getCurrentCalls(this.mLastRelevantPoll);
        } else if (this.mPendingOperations < 0) {
            Rlog.e(LOG_TAG, "GsmCallTracker.pendingOperations < 0");
            this.mPendingOperations = 0;
        }
    }

    private void updatePhoneState() {
        State oldState = this.mState;
        if (this.mRingingCall.isRinging()) {
            this.mState = State.RINGING;
        } else if (this.mPendingMO == null && this.mForegroundCall.isIdle() && this.mBackgroundCall.isIdle()) {
            this.mState = State.IDLE;
        } else {
            this.mState = State.OFFHOOK;
        }
        if (this.mState == State.IDLE && oldState != this.mState) {
            this.mVoiceCallEndedRegistrants.notifyRegistrants(new AsyncResult(null, null, null));
        } else if (oldState == State.IDLE && oldState != this.mState) {
            this.mVoiceCallStartedRegistrants.notifyRegistrants(new AsyncResult(null, null, null));
        }
        if (this.mState != oldState) {
            this.mPhone.notifyPhoneStateChanged();
        }
    }

    protected synchronized void handlePollCalls(AsyncResult ar) {
        List polledCalls;
        if (ar.exception == null) {
            polledCalls = ar.result;
        } else {
            if (isCommandExceptionRadioNotAvailable(ar.exception)) {
                polledCalls = new ArrayList();
            } else {
                pollCallsAfterDelay();
            }
        }
        Connection newRinging = null;
        Connection newUnknown = null;
        boolean hasNonHangupStateChanged = false;
        boolean hasAnyCallDisconnected = false;
        boolean unknownConnectionAppeared = false;
        this.mCsFallback = 0;
        this.mHasVolteCall = false;
        this.mHasVideoCall = false;
        this.mHasEpdgCall = false;
        int i = 0;
        int curDC = 0;
        int dcSize = polledCalls.size();
        while (i < this.mConnections.length) {
            GsmConnection conn = this.mConnections[i];
            DriverCall dc = null;
            if (curDC < dcSize) {
                dc = (DriverCall) polledCalls.get(curDC);
                if (dc.index == i + 1) {
                    curDC++;
                } else {
                    dc = null;
                }
            }
            if (CscFeature.getInstance().getEnableStatus("CscFeature_CIQ_BroadcastState")) {
                boolean callStatusAvailable = true;
                boolean sendCalldisconnect = false;
                boolean isMissedCall = false;
                Call.State dcState = Call.State.IDLE;
                Call.State connState;
                if (dc != null) {
                    dcState = GsmCall.stateFromDCState(dc.state);
                    if (conn != null) {
                        connState = conn.getState();
                        if (connState == dcState) {
                            callStatusAvailable = false;
                        } else if (connState == Call.State.DISCONNECTING) {
                            callStatusAvailable = false;
                        }
                    } else if (dcState == Call.State.INCOMING) {
                        callStatusAvailable = false;
                    }
                } else if (conn != null) {
                    connState = conn.getState();
                    if (connState == Call.State.DISCONNECTING) {
                        sendCalldisconnect = true;
                    } else if (connState == Call.State.INCOMING || connState == Call.State.WAITING) {
                        sendCalldisconnect = true;
                        isMissedCall = true;
                    } else {
                        callStatusAvailable = false;
                    }
                } else {
                    callStatusAvailable = false;
                }
                if (callStatusAvailable) {
                    String phNumber = !sendCalldisconnect ? dc.number : conn.getAddress();
                    if (!sendCalldisconnect) {
                        CallStateBroadcaster.SendCallStatus(phNumber, dcState);
                    } else {
                        if (!isMissedCall) {
                            CallStateBroadcaster.SendCallStatus(phNumber, Call.State.DISCONNECTING);
                        }
                        CallStateBroadcaster.SendCallDisconnected(phNumber, 16);
                    }
                }
            }
            log("poll: conn[i=" + i + "]=" + conn + ", dc=" + dc);
            if (conn == null && dc != null) {
                if (this.mPendingMO == null || !this.mPendingMO.compareTo(dc)) {
                    this.mConnections[i] = new GsmConnection(this.mPhone.getContext(), dc, this, i);
                    if (this.mConnections[i].getCall() == this.mRingingCall) {
                        newRinging = this.mConnections[i];
                    } else if (this.mHandoverConnection != null) {
                        this.mPhone.migrateFrom((PhoneBase) this.mPhone.getImsPhone());
                        this.mConnections[i].migrateFrom(this.mHandoverConnection);
                        this.mPhone.notifyHandoverStateChanged(this.mConnections[i]);
                        this.mHandoverConnection = null;
                    } else {
                        Rlog.i(LOG_TAG, "Phantom call appeared " + dc);
                        if (!(dc.state == DriverCall.State.ALERTING || dc.state == DriverCall.State.DIALING)) {
                            this.mConnections[i].onConnectedInOrOut();
                            if (dc.state == DriverCall.State.HOLDING) {
                                this.mConnections[i].onStartedHolding();
                            }
                        }
                        newUnknown = this.mConnections[i];
                        unknownConnectionAppeared = true;
                    }
                } else {
                    log("poll: pendingMO=" + this.mPendingMO);
                    this.mConnections[i] = this.mPendingMO;
                    this.mPendingMO.mIndex = i;
                    this.mPendingMO.update(dc);
                    this.mPendingMO = null;
                    if (this.mHangupPendingMO) {
                        this.mHangupPendingMO = false;
                        try {
                            log("poll: hangupPendingMO, hangup conn " + i);
                            hangup(this.mConnections[i]);
                            break;
                        } catch (CallStateException e) {
                            Rlog.e(LOG_TAG, "unexpected error on hangup");
                        }
                    }
                }
                hasNonHangupStateChanged = true;
            } else if (conn != null && dc == null) {
                this.mDroppedDuringPoll.add(conn);
                this.mConnections[i] = null;
            } else if (conn != null && dc != null && !conn.compareTo(dc)) {
                this.mDroppedDuringPoll.add(conn);
                this.mConnections[i] = new GsmConnection(this.mPhone.getContext(), dc, this, i);
                if (this.mConnections[i].getCall() == this.mRingingCall) {
                    newRinging = this.mConnections[i];
                }
                hasNonHangupStateChanged = true;
            } else if (!(conn == null || dc == null)) {
                hasNonHangupStateChanged = hasNonHangupStateChanged || conn.update(dc);
            }
            if (!(dc == null || dc.callDetails == null)) {
                if (dc.callDetails.call_domain == 2) {
                    this.mHasVolteCall = true;
                }
                if (dc.callDetails.call_type == 3) {
                    this.mHasVideoCall = true;
                }
            }
            if (this.mConnections[i] == null) {
                this.connWaitActive[i] = Boolean.valueOf(false);
            } else {
                Call.State state = this.mConnections[i].getState();
                if (state == Call.State.ALERTING || state == Call.State.INCOMING || state == Call.State.WAITING) {
                    if (PhoneNumberUtils.isEmergencyNumber(this.mPhone.getSubId(), this.mConnections[i].getOrigDialString())) {
                        Rlog.d(LOG_TAG, "Emergency call");
                        this.connWaitActive[i] = Boolean.valueOf(false);
                    } else {
                        this.connWaitActive[i] = Boolean.valueOf(true);
                    }
                } else if (state == Call.State.ACTIVE && this.connWaitActive[i].booleanValue()) {
                    Rlog.d(LOG_TAG, "ADD CALL " + (this.mConnections[i].isIncoming() ? "INCOMING" : "OUTGOING"));
                    addNumberOfCalls(this.mConnections[i].isIncoming());
                    this.connWaitActive[i] = Boolean.valueOf(false);
                } else {
                    this.connWaitActive[i] = Boolean.valueOf(false);
                }
            }
            i++;
        }
        if (this.mPendingMO != null) {
            Rlog.d(LOG_TAG, "Pending MO dropped before poll fg state:" + this.mForegroundCall.getState());
            this.mDroppedDuringPoll.add(this.mPendingMO);
            this.mPendingMO = null;
            this.mHangupPendingMO = false;
        }
        if (newRinging != null) {
            this.mPhone.notifyNewRingingConnection(newRinging);
        }
        for (i = this.mDroppedDuringPoll.size() - 1; i >= 0; i--) {
            conn = (GsmConnection) this.mDroppedDuringPoll.get(i);
            if (conn.isIncoming() && conn.getConnectTime() == 0) {
                int cause;
                if (conn.mCause == 3) {
                    cause = 16;
                } else {
                    cause = 1;
                    logCallEvent("missed", conn.getAddress(), conn.getCreateTime(), conn.getDurationMillis(), conn.isIncoming());
                }
                if (false) {
                    cause = 1;
                    logCallEvent("missed", conn.getAddress(), conn.getCreateTime(), conn.getDurationMillis(), conn.isIncoming());
                }
                log("missed/rejected call, conn.cause=" + conn.mCause);
                log("setting cause to " + cause);
                this.mDroppedDuringPoll.remove(i);
                hasAnyCallDisconnected |= conn.onDisconnect(cause);
            } else if (conn.mCause == 3 || conn.mCause == 7) {
                if (!(conn.getConnectTime() == 0 || PhoneNumberUtils.isEmergencyNumber(this.mPhone.getSubId(), conn.getOrigDialString()))) {
                    logCallEvent("success", conn.getAddress(), conn.getCreateTime(), conn.getDurationMillis(), conn.isIncoming());
                }
                this.mDroppedDuringPoll.remove(i);
                hasAnyCallDisconnected |= conn.onDisconnect(conn.mCause);
            }
        }
        if (this.mDroppedDuringPoll.size() > 0) {
            this.mCi.getLastCallFailCause(obtainNoPollCompleteMessage(5));
        }
        if (false) {
            pollCallsAfterDelay();
        }
        if (newRinging != null || hasNonHangupStateChanged || hasAnyCallDisconnected) {
            internalClearDisconnected();
        }
        updatePhoneState();
        if (unknownConnectionAppeared) {
            this.mPhone.notifyUnknownConnection(newUnknown);
        }
        if (hasNonHangupStateChanged || newRinging != null || hasAnyCallDisconnected) {
            this.mPhone.notifyPreciseCallStateChanged();
        }
        if (newRinging != null && newRinging.getState() == Call.State.INCOMING && CscFeature.getInstance().getEnableStatus("CscFeature_CIQ_BroadcastState")) {
            CallStateBroadcaster.SendCallStatus(newRinging.getAddress(), Call.State.INCOMING);
        }
    }

    private void handleRadioNotAvailable() {
        pollCallsWhenSafe();
    }

    private void dumpState() {
        int i;
        Rlog.i(LOG_TAG, "Phone State:" + this.mState);
        Rlog.i(LOG_TAG, "Ringing call: " + this.mRingingCall.toString());
        List l = this.mRingingCall.getConnections();
        int s = l.size();
        for (i = 0; i < s; i++) {
            Rlog.i(LOG_TAG, l.get(i).toString());
        }
        Rlog.i(LOG_TAG, "Foreground call: " + this.mForegroundCall.toString());
        l = this.mForegroundCall.getConnections();
        s = l.size();
        for (i = 0; i < s; i++) {
            Rlog.i(LOG_TAG, l.get(i).toString());
        }
        Rlog.i(LOG_TAG, "Background call: " + this.mBackgroundCall.toString());
        l = this.mBackgroundCall.getConnections();
        s = l.size();
        for (i = 0; i < s; i++) {
            Rlog.i(LOG_TAG, l.get(i).toString());
        }
    }

    void hangup(GsmConnection conn) throws CallStateException {
        if (conn.mOwner != this) {
            throw new CallStateException("GsmConnection " + conn + "does not belong to GsmCallTracker " + this);
        }
        if (conn == this.mPendingMO) {
            log("hangup: set hangupPendingMO to true");
            this.mHangupPendingMO = true;
        } else {
            try {
                this.mCi.hangupConnection(conn.getGSMIndex(), obtainCompleteMessage());
            } catch (CallStateException e) {
                Rlog.w(LOG_TAG, "GsmCallTracker WARN: hangup() on absent connection " + conn);
            }
        }
        conn.onHangupLocal();
    }

    void separate(GsmConnection conn) throws CallStateException {
        if (conn.mOwner != this) {
            throw new CallStateException("GsmConnection " + conn + "does not belong to GsmCallTracker " + this);
        }
        try {
            this.mCi.separateConnection(conn.getGSMIndex(), obtainCompleteMessage(12));
        } catch (CallStateException e) {
            Rlog.w(LOG_TAG, "GsmCallTracker WARN: separate() on absent connection " + conn);
        }
    }

    void setMute(boolean mute) {
        this.mDesiredMute = mute;
        this.mCi.setMute(this.mDesiredMute, null);
    }

    boolean getMute() {
        return this.mDesiredMute;
    }

    void hangup(GsmCall call) throws CallStateException {
        if (call.getConnections().size() == 0) {
            throw new CallStateException("no connections in call");
        }
        if (call == this.mRingingCall) {
            log("(ringing) hangup waiting or background");
            this.mCi.hangupWaitingOrBackground(obtainCompleteMessage());
        } else if (call == this.mForegroundCall) {
            if (call.isDialingOrAlerting()) {
                log("(foregnd) hangup dialing or alerting...");
                hangup((GsmConnection) call.getConnections().get(0));
            } else if (this.mRingingCall.isRinging()) {
                log("hangup all conns in active/background call, without affecting ringing call");
                hangupForegroundResumeBackground();
            } else if ("KTT".equals("")) {
                for (int i = 0; i < call.getConnections().size(); i++) {
                    if (((Connection) call.getConnections().get(i)).getState() == Call.State.ACTIVE) {
                        hangup((GsmConnection) call.getConnections().get(i));
                        log("(TN_KOR_KT) hangup...");
                        break;
                    }
                }
            } else {
                hangupForegroundResumeBackground();
            }
        } else if (call != this.mBackgroundCall) {
            throw new RuntimeException("GsmCall " + call + "does not belong to GsmCallTracker " + this);
        } else if (this.mRingingCall.isRinging()) {
            log("hangup all conns in background call");
            hangupAllConnections(call);
        } else {
            hangupWaitingOrBackground();
        }
        call.onHangupLocal();
        this.mPhone.notifyPreciseCallStateChanged();
    }

    void hangupWaitingOrBackground() {
        log("hangupWaitingOrBackground");
        this.mCi.hangupWaitingOrBackground(obtainCompleteMessage());
    }

    void hangupForegroundResumeBackground() {
        log("hangupForegroundResumeBackground");
        this.mCi.hangupForegroundResumeBackground(obtainCompleteMessage());
    }

    void hangupConnectionByIndex(GsmCall call, int index) throws CallStateException {
        int count = call.mConnections.size();
        for (int i = 0; i < count; i++) {
            if (((GsmConnection) call.mConnections.get(i)).getGSMIndex() == index) {
                this.mCi.hangupConnection(index, obtainCompleteMessage());
                return;
            }
        }
        throw new CallStateException("no gsm index found");
    }

    void hangupAllConnections(GsmCall call) {
        try {
            int count = call.mConnections.size();
            for (int i = 0; i < count; i++) {
                this.mCi.hangupConnection(((GsmConnection) call.mConnections.get(i)).getGSMIndex(), obtainCompleteMessage());
            }
        } catch (CallStateException ex) {
            Rlog.e(LOG_TAG, "hangupConnectionByIndex caught " + ex);
        }
    }

    GsmConnection getConnectionByIndex(int index) {
        for (GsmConnection c : this.mConnections) {
            if (c != null) {
                try {
                    if (c.getGSMIndex() == index) {
                        return c;
                    }
                } catch (CallStateException e) {
                    Rlog.w(LOG_TAG, " absent connection for index " + index);
                }
            }
        }
        return null;
    }

    GsmConnection getConnectionByIndex(GsmCall call, int index) throws CallStateException {
        int count = call.mConnections.size();
        for (int i = 0; i < count; i++) {
            GsmConnection cn = (GsmConnection) call.mConnections.get(i);
            if (cn.getGSMIndex() == index) {
                return cn;
            }
        }
        return null;
    }

    private SuppService getFailedService(int what) {
        switch (what) {
            case 8:
                return SuppService.SWITCH;
            case 11:
                return SuppService.CONFERENCE;
            case 12:
                return SuppService.SEPARATE;
            case 13:
                return SuppService.TRANSFER;
            default:
                return SuppService.UNKNOWN;
        }
    }

    public void handleMessage(Message msg) {
        if (this.mPhone.mIsTheCurrentActivePhone) {
            AsyncResult ar;
            switch (msg.what) {
                case 1:
                    ar = msg.obj;
                    if (msg == this.mLastRelevantPoll) {
                        log("handle EVENT_POLL_CALL_RESULT: set needsPoll=F");
                        this.mNeedsPoll = false;
                        this.mLastRelevantPoll = null;
                        handlePollCalls((AsyncResult) msg.obj);
                        return;
                    }
                    return;
                case 2:
                case 3:
                    pollCallsWhenSafe();
                    return;
                case 4:
                    ar = (AsyncResult) msg.obj;
                    operationComplete();
                    return;
                case 5:
                    int causeCode;
                    int call_fail_reason = 0;
                    int sipError = 0;
                    ar = (AsyncResult) msg.obj;
                    operationComplete();
                    if (ar.exception != null) {
                        causeCode = 16;
                        Rlog.i(LOG_TAG, "Exception during getLastCallFailCause, assuming normal disconnect");
                    } else {
                        causeCode = ((int[]) ar.result)[0];
                        try {
                            call_fail_reason = ((int[]) ar.result)[1];
                            Rlog.d(LOG_TAG, "Call Fail Reason " + call_fail_reason);
                        } catch (IndexOutOfBoundsException e) {
                            call_fail_reason = 0;
                        }
                        try {
                            sipError = ((int[]) ar.result)[2];
                            Rlog.d(LOG_TAG, "SipError " + sipError);
                        } catch (IndexOutOfBoundsException e2) {
                            sipError = 0;
                        }
                    }
                    boolean isDroppedCall = false;
                    if (causeCode == 34 || causeCode == 41 || causeCode == 42 || causeCode == 44 || causeCode == 49 || causeCode == 58 || causeCode == 65535) {
                        GsmCellLocation loc = (GsmCellLocation) this.mPhone.getCellLocation();
                        Object[] objArr = new Object[3];
                        objArr[0] = Integer.valueOf(causeCode);
                        objArr[1] = Integer.valueOf(loc != null ? loc.getCid() : -1);
                        objArr[2] = Integer.valueOf(TelephonyManager.getDefault().getNetworkType());
                        EventLog.writeEvent(EventLogTags.CALL_DROP, objArr);
                        isDroppedCall = false;
                    }
                    int s = this.mDroppedDuringPoll.size();
                    for (int i = 0; i < s; i++) {
                        GsmConnection conn = (GsmConnection) this.mDroppedDuringPoll.get(i);
                        if (isDroppedCall) {
                            Rlog.d(LOG_TAG, "EVENT_GET_LAST_CALL_FAIL_CAUSE - CALL_DROP - causeCode = " + causeCode);
                            logCallEvent("dropped", conn.getAddress(), conn.getCreateTime(), conn.getDurationMillis(), conn.isIncoming());
                        } else {
                            Rlog.d(LOG_TAG, "EVENT_GET_LAST_CALL_FAIL_CAUSE - SUCCESS - causeCode = " + causeCode);
                            if (!(conn.getConnectTime() == 0 || PhoneNumberUtils.isEmergencyNumber(this.mPhone.getSubId(), conn.getOrigDialString()))) {
                                logCallEvent("success", conn.getAddress(), conn.getCreateTime(), conn.getDurationMillis(), conn.isIncoming());
                            }
                        }
                        if (CscFeature.getInstance().getEnableStatus("CscFeature_CIQ_BroadcastState")) {
                            String phNumber = conn.getAddress();
                            if (causeCode != 16) {
                                CallStateBroadcaster.SendCallStatus(phNumber, Call.State.DISCONNECTED);
                            }
                            if (causeCode == 19) {
                                CallStateBroadcaster.SendCallDisconnected(phNumber, 65535);
                            } else {
                                CallStateBroadcaster.SendCallDisconnected(phNumber, causeCode);
                            }
                        }
                        conn.onRemoteDisconnect(causeCode, call_fail_reason, sipError);
                    }
                    updatePhoneState();
                    this.mPhone.notifyPreciseCallStateChanged();
                    this.mDroppedDuringPoll.clear();
                    return;
                case 8:
                case 11:
                case 12:
                case 13:
                    if (((AsyncResult) msg.obj).exception != null) {
                        this.mPhone.notifySuppServiceFailed(getFailedService(msg.what));
                    }
                    operationComplete();
                    return;
                case 9:
                    handleRadioAvailable();
                    return;
                case 10:
                    handleRadioNotAvailable();
                    return;
                case Threads.ALERT_SEVERE_THREAD /*102*/:
                    ar = (AsyncResult) msg.obj;
                    if (ar != null && ar.result != null && ar.exception == null) {
                        handleModifyCallRequest((CallModify) ar.result, ar.exception);
                        return;
                    }
                    return;
                case Threads.ALERT_AMBER_THREAD /*103*/:
                    this.mVsid = ((int[]) ((AsyncResult) msg.obj).result)[0];
                    log("EVENT_VOICE_SYSTEM_ID, vsid = " + this.mVsid);
                    return;
                case 204:
                    log("EVENT_HANGUP_FG_RESUME_BG_RESULT");
                    ar = (AsyncResult) msg.obj;
                    if (ar.exception != null) {
                        log("handle EVENT_HANGUP_FG_RESUME_BG_RESULT");
                    } else if (ar.userObj != null) {
                        Message delayedMsg = obtainMessage(205);
                        delayedMsg.obj = (Integer) ar.userObj;
                        sendMessageDelayed(delayedMsg, 50);
                    }
                    operationComplete();
                    return;
                case 205:
                    try {
                        dialPendingEMRCall(((Integer) msg.obj).intValue());
                    } catch (CallStateException e3) {
                        Rlog.e(LOG_TAG, "unexpected error on dialPendingEMRCall");
                    }
                    operationComplete();
                    return;
                default:
                    return;
            }
        }
        Rlog.e(LOG_TAG, "Received message " + msg + "[" + msg.what + "] while being destroyed. Ignoring.");
    }

    protected void log(String msg) {
        Rlog.d(MultiSimManager.appendSimSlot(LOG_TAG, this.mPhone.mPhoneId), msg);
    }

    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        int i;
        pw.println("GsmCallTracker extends:");
        super.dump(fd, pw, args);
        pw.println("mConnections: length=" + this.mConnections.length);
        for (i = 0; i < this.mConnections.length; i++) {
            pw.printf("  mConnections[%d]=%s\n", new Object[]{Integer.valueOf(i), this.mConnections[i]});
        }
        pw.println(" mVoiceCallEndedRegistrants=" + this.mVoiceCallEndedRegistrants);
        pw.println(" mVoiceCallStartedRegistrants=" + this.mVoiceCallStartedRegistrants);
        pw.println(" mDroppedDuringPoll: size=" + this.mDroppedDuringPoll.size());
        for (i = 0; i < this.mDroppedDuringPoll.size(); i++) {
            pw.printf("  mDroppedDuringPoll[%d]=%s\n", new Object[]{Integer.valueOf(i), this.mDroppedDuringPoll.get(i)});
        }
        pw.println(" mRingingCall=" + this.mRingingCall);
        pw.println(" mForegroundCall=" + this.mForegroundCall);
        pw.println(" mBackgroundCall=" + this.mBackgroundCall);
        pw.println(" mPendingMO=" + this.mPendingMO);
        pw.println(" mHangupPendingMO=" + this.mHangupPendingMO);
        pw.println(" mPhone=" + this.mPhone);
        pw.println(" mDesiredMute=" + this.mDesiredMute);
        pw.println(" mState=" + this.mState);
    }

    boolean canVideoCallDial() {
        return (this.mPhone.getServiceState().getState() == 3 || this.mPendingMO != null || this.mRingingCall.isRinging() || this.mForegroundCall.getState().isAlive() || this.mBackgroundCall.getState().isAlive()) ? false : true;
    }

    void dialPendingEMRCall(int clirMode) throws CallStateException {
        if (!this.isEmergencyCallPending || this.pendingEmergencyNum == null) {
            throw new CallStateException("can not dial pending emergency call");
        }
        setMute(false);
        this.mCi.dialEmergencyCall(this.pendingEmergencyNum + "/" + this.emergencyCatJPN, clirMode, obtainCompleteMessage());
        this.isEmergencyCallPending = false;
        updatePhoneState();
        this.mPhone.notifyPreciseCallStateChanged();
    }

    private Message obtainCompleteMessage(int what, int clirMode) {
        this.mPendingOperations++;
        this.mLastRelevantPoll = null;
        this.mNeedsPoll = true;
        log("obtainCompleteMessage: pendingOperations=" + this.mPendingOperations + ", needsPoll=" + this.mNeedsPoll);
        return obtainMessage(what, Integer.valueOf(clirMode));
    }

    void hangupWaitingOrBackground(int clirMode) {
        log("hangupWaitingOrBackground, clirMode : " + clirMode);
        this.mCi.hangupWaitingOrBackground(obtainCompleteMessage(204, clirMode));
    }

    void hangupForegroundResumeBackground(int clirMode) {
        log("hangupForegroundResumeBackground, clirMode : " + clirMode);
        this.mCi.hangupForegroundResumeBackground(obtainCompleteMessage(204, clirMode));
    }

    private void handleModifyCallRequest(CallModify cm, Throwable e) {
        Rlog.d(LOG_TAG, "handleCallModifyRequest(" + cm + ", " + e + ")");
        if (cm != null) {
            GsmConnection c = getConnectionByIndex(cm.call_index);
            if (c == null) {
                Rlog.e(LOG_TAG, "Null Call Modify request ");
            } else if (c.onReceivedModifyCall(cm)) {
                this.mPhone.notifyModifyCallRequest(c, e);
            } else {
                try {
                    c.rejectConnectionTypeChange();
                } catch (CallStateException ex) {
                    Rlog.e(LOG_TAG, "Exception while rejecting ConnectionTypeChange", ex);
                }
            }
        }
    }

    boolean checkEmergencyCallRedirectToNormalCall() {
        int simState = TelephonyManager.getDefault().getSimState();
        int serviceState = 1;
        String mcc = "000";
        String plmn = TelephonyManager.getDefault().getNetworkOperator();
        if (this.mPhone != null) {
            simState = TelephonyManager.getDefault().getSimState(this.mPhone.getPhoneId());
            plmn = TelephonyManager.getDefault().getNetworkOperator(this.mPhone.getSubId());
        }
        if (plmn != null && plmn.length() > 4) {
            mcc = plmn.substring(0, 3);
        }
        if ("460".equals(mcc) || "466".equals(mcc)) {
            if (!(this.mPhone == null || this.mPhone.getServiceState() == null)) {
                serviceState = this.mPhone.getServiceState().getState();
            }
            log("state1:" + simState + " state2:" + serviceState + " " + mcc);
            if ("460".equals(mcc) && (("110".equals(this.mPendingMO.getAddress()) || "119".equals(this.mPendingMO.getAddress()) || "999".equals(this.mPendingMO.getAddress()) || "120".equals(this.mPendingMO.getAddress()) || "122".equals(this.mPendingMO.getAddress())) && simState != 1 && simState != 0 && serviceState == 0)) {
                log("redirect ecc to normal because here is china");
                return true;
            } else if (!"466".equals(mcc) || (!("110".equals(this.mPendingMO.getAddress()) || "119".equals(this.mPendingMO.getAddress())) || simState == 1 || simState == 0 || serviceState != 0)) {
                log("no redirection");
                return false;
            } else {
                log("redirect ecc to normal because here is taiwan");
                return true;
            }
        }
        log("no redirect mcc:" + mcc);
        return false;
    }

    void holdCall() throws CallStateException {
        if (this.mBackgroundCall.getState() == Call.State.HOLDING) {
            throw new CallStateException("cannot be in the holding state");
        }
        this.mCi.holdCall(obtainCompleteMessage());
    }
}
