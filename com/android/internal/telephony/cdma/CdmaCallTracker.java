package com.android.internal.telephony.cdma;

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
import android.text.TextUtils;
import com.android.ims.ImsCallProfile;
import com.android.internal.telephony.Call;
import com.android.internal.telephony.CallDetails;
import com.android.internal.telephony.CallModify;
import com.android.internal.telephony.CallStateException;
import com.android.internal.telephony.CallTracker;
import com.android.internal.telephony.Connection;
import com.android.internal.telephony.DriverCall;
import com.android.internal.telephony.Phone.SuppService;
import com.android.internal.telephony.PhoneBase;
import com.android.internal.telephony.PhoneConstants.State;
import com.sec.android.app.CscFeature;
import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

public final class CdmaCallTracker extends CallTracker {
    private static final boolean DBG_POLL = false;
    static final String LOG_TAG = "CdmaCallTracker";
    static final int MAX_CONNECTIONS;
    static final int MAX_CONNECTIONS_PER_CALL;
    private static final boolean REPEAT_POLLING = false;
    static final String SC_GLOBALDEV_CF_CON = "*71";
    static final String SC_GLOBALDEV_CF_DEAC = "*73";
    static final String SC_GLOBALDEV_CF_UNCON = "*72";
    private int m3WayCallFlashDelay = 0;
    AudioManager mAudioManager;
    CdmaCall mBackgroundCall = new CdmaCall(this);
    RegistrantList mCallWaitingRegistrants = new RegistrantList();
    CdmaConnection[] mConnections = new CdmaConnection[MAX_CONNECTIONS];
    boolean mDesiredMute = false;
    ArrayList<CdmaConnection> mDroppedDuringPoll = new ArrayList(MAX_CONNECTIONS);
    CdmaCall mForegroundCall = new CdmaCall(this);
    boolean mHangupPendingMO;
    private boolean mIsEcmTimerCanceled = false;
    boolean mIsInEmergencyCall = false;
    int mPendingCallClirMode;
    boolean mPendingCallInEcm = false;
    CdmaConnection mPendingMO;
    CDMAPhone mPhone;
    CdmaCall mRingingCall = new CdmaCall(this);
    State mState = State.IDLE;
    RegistrantList mVoiceCallEndedRegistrants = new RegistrantList();
    RegistrantList mVoiceCallStartedRegistrants = new RegistrantList();

    class C00721 implements Runnable {
        C00721() {
        }

        public void run() {
            if (CdmaCallTracker.this.mPendingMO != null) {
                CdmaCallTracker.this.mCi.sendCDMAFeatureCode(CdmaCallTracker.this.mPendingMO.getAddress(), CdmaCallTracker.this.obtainMessage(16));
            }
        }
    }

    static {
        if (CscFeature.getInstance().getEnableStatus("CscFeature_RIL_SupportVolte")) {
            MAX_CONNECTIONS = 7;
            MAX_CONNECTIONS_PER_CALL = 5;
        } else if ("CTC".equals(SystemProperties.get("ro.csc.sales_code", "NONE"))) {
            MAX_CONNECTIONS = 1;
            MAX_CONNECTIONS_PER_CALL = 1;
        } else {
            MAX_CONNECTIONS = 8;
            MAX_CONNECTIONS_PER_CALL = 1;
        }
    }

    CdmaCallTracker(CDMAPhone phone) {
        this.mPhone = phone;
        this.mCi = phone.mCi;
        this.mCi.registerForCallStateChanged(this, 2, null);
        this.mCi.registerForOn(this, 9, null);
        this.mCi.registerForNotAvailable(this, 10, null);
        this.mCi.registerForCallWaitingInfo(this, 15, null);
        this.mCi.registerForModifyCall(this, Threads.ALERT_SEVERE_THREAD, null);
        this.mCi.registerForCsFallback(this, Threads.ALERT_TEST_MESSAGE_THREAD, null);
        this.mForegroundCall.setGeneric(false);
        this.mAudioManager = (AudioManager) phone.getContext().getSystemService("audio");
    }

    public void dispose() {
        Rlog.d(LOG_TAG, "CdmaCallTracker dispose");
        this.mCi.unregisterForLineControlInfo(this);
        this.mCi.unregisterForCallStateChanged(this);
        this.mCi.unregisterForOn(this);
        this.mCi.unregisterForNotAvailable(this);
        this.mCi.unregisterForCallWaitingInfo(this);
        this.mCi.unregisterForModifyCall(this);
        this.mCi.unregisterForCsFallback(this);
        clearDisconnected();
    }

    protected void finalize() {
        Rlog.d(LOG_TAG, "CdmaCallTracker finalized");
    }

    public void registerForVoiceCallStarted(Handler h, int what, Object obj) {
        Registrant r = new Registrant(h, what, obj);
        this.mVoiceCallStartedRegistrants.add(r);
        if (this.mState != State.IDLE) {
            r.notifyRegistrant(new AsyncResult(null, null, null));
        }
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

    public void registerForCallWaiting(Handler h, int what, Object obj) {
        this.mCallWaitingRegistrants.add(new Registrant(h, what, obj));
    }

    public void unregisterForCallWaiting(Handler h) {
        this.mCallWaitingRegistrants.remove(h);
    }

    Connection dial(String dialString, int clirMode) throws CallStateException {
        return dial(dialString, clirMode, null);
    }

    /* JADX WARNING: inconsistent code. */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    com.android.internal.telephony.Connection dial(java.lang.String r17, int r18, com.android.internal.telephony.CallDetails r19) throws com.android.internal.telephony.CallStateException {
        /*
        r16 = this;
        r16.clearDisconnected();
        r2 = r16.canDial();
        if (r2 != 0) goto L_0x0011;
    L_0x0009:
        r2 = new com.android.internal.telephony.CallStateException;
        r3 = "cannot dial in current state";
        r2.<init>(r3);
        throw r2;
    L_0x0011:
        r14 = r17;
        r0 = r16;
        r2 = r0.mPhone;
        r3 = "gsm.operator.iso-country";
        r4 = "";
        r13 = r2.getSystemProperty(r3, r4);
        r0 = r16;
        r2 = r0.mPhone;
        r3 = "gsm.sim.operator.iso-country";
        r4 = "";
        r15 = r2.getSystemProperty(r3, r4);
        r2 = android.text.TextUtils.isEmpty(r13);
        if (r2 != 0) goto L_0x0105;
    L_0x0031:
        r2 = android.text.TextUtils.isEmpty(r15);
        if (r2 != 0) goto L_0x0105;
    L_0x0037:
        r2 = r15.equals(r13);
        if (r2 != 0) goto L_0x0105;
    L_0x003d:
        r10 = 1;
    L_0x003e:
        if (r10 == 0) goto L_0x0053;
    L_0x0040:
        r2 = "us";
        r2 = r2.equals(r15);
        if (r2 == 0) goto L_0x010b;
    L_0x0048:
        if (r10 == 0) goto L_0x0108;
    L_0x004a:
        r2 = "vi";
        r2 = r2.equals(r13);
        if (r2 != 0) goto L_0x0108;
    L_0x0052:
        r10 = 1;
    L_0x0053:
        if (r10 == 0) goto L_0x0061;
    L_0x0055:
        r0 = r16;
        r2 = r0.mPhone;
        r0 = r16;
        r1 = r17;
        r17 = r0.convertNumberIfNecessary(r2, r1);
    L_0x0061:
        if (r19 != 0) goto L_0x0068;
    L_0x0063:
        r19 = new com.android.internal.telephony.CallDetails;
        r19.<init>();
    L_0x0068:
        r2 = "ps";
        r3 = "persist.radio.test_calldomain";
        r4 = "cs";
        r3 = android.os.SystemProperties.get(r3, r4);
        r2 = r2.equals(r3);
        if (r2 == 0) goto L_0x007d;
    L_0x0078:
        r2 = 2;
        r0 = r19;
        r0.call_domain = r2;
    L_0x007d:
        r2 = "ril.cdma.inecmmode";
        r3 = "false";
        r9 = android.os.SystemProperties.get(r2, r3);
        r2 = "true";
        r12 = r9.equals(r2);
        r0 = r16;
        r2 = r0.mPhone;
        r2 = r2.getContext();
        r0 = r17;
        r11 = android.telephony.PhoneNumberUtils.isLocalEmergencyNumber(r2, r0);
        if (r12 == 0) goto L_0x00a3;
    L_0x009b:
        if (r11 == 0) goto L_0x00a3;
    L_0x009d:
        r2 = 1;
        r0 = r16;
        r0.handleEcmTimer(r2);
    L_0x00a3:
        r0 = r16;
        r2 = r0.mForegroundCall;
        r3 = 0;
        r2.setGeneric(r3);
        r0 = r16;
        r2 = r0.mForegroundCall;
        r2 = r2.getState();
        r3 = com.android.internal.telephony.Call.State.ACTIVE;
        if (r2 != r3) goto L_0x00c7;
    L_0x00b7:
        r0 = r16;
        r2 = r0.mForegroundCall;
        r2 = r2.isImsCall();
        if (r2 == 0) goto L_0x0122;
    L_0x00c1:
        r16.switchWaitingOrHoldingAndActive();
        r16.fakeHoldForegroundBeforeDial();
    L_0x00c7:
        r2 = "VZW-CDMA";
        r3 = "";
        r2 = r2.equals(r3);
        if (r2 == 0) goto L_0x0127;
    L_0x00d1:
        r0 = r16;
        r2 = r0.mPhone;
        r2 = r2.mSST;
        r2 = r2.mSS;
        r2 = r2.getRoaming();
        if (r2 == 0) goto L_0x0127;
    L_0x00df:
        r2 = "*71";
        r0 = r17;
        r2 = r0.startsWith(r2);
        if (r2 != 0) goto L_0x00fd;
    L_0x00e9:
        r2 = "*72";
        r0 = r17;
        r2 = r0.startsWith(r2);
        if (r2 != 0) goto L_0x00fd;
    L_0x00f3:
        r2 = "*73";
        r0 = r17;
        r2 = r0.startsWith(r2);
        if (r2 == 0) goto L_0x0127;
    L_0x00fd:
        r2 = new com.android.internal.telephony.CallStateException;
        r3 = "cannot dial in current state";
        r2.<init>(r3);
        throw r2;
    L_0x0105:
        r10 = 0;
        goto L_0x003e;
    L_0x0108:
        r10 = 0;
        goto L_0x0053;
    L_0x010b:
        r2 = "vi";
        r2 = r2.equals(r15);
        if (r2 == 0) goto L_0x0053;
    L_0x0113:
        if (r10 == 0) goto L_0x0120;
    L_0x0115:
        r2 = "us";
        r2 = r2.equals(r13);
        if (r2 != 0) goto L_0x0120;
    L_0x011d:
        r10 = 1;
    L_0x011e:
        goto L_0x0053;
    L_0x0120:
        r10 = 0;
        goto L_0x011e;
    L_0x0122:
        r2 = r16.dialThreeWay(r17);
    L_0x0126:
        return r2;
    L_0x0127:
        r2 = new com.android.internal.telephony.cdma.CdmaConnection;
        r0 = r16;
        r3 = r0.mPhone;
        r3 = r3.getContext();
        r4 = r16.checkForTestEmergencyNumber(r17);
        r0 = r16;
        r6 = r0.mForegroundCall;
        r5 = r16;
        r7 = r19;
        r2.<init>(r3, r4, r5, r6, r7);
        r0 = r16;
        r0.mPendingMO = r2;
        r2 = 0;
        r0 = r16;
        r0.mHangupPendingMO = r2;
        r0 = r16;
        r2 = r0.mPendingMO;
        r2 = r2.getAddress();
        if (r2 == 0) goto L_0x0171;
    L_0x0153:
        r0 = r16;
        r2 = r0.mPendingMO;
        r2 = r2.getAddress();
        r2 = r2.length();
        if (r2 == 0) goto L_0x0171;
    L_0x0161:
        r0 = r16;
        r2 = r0.mPendingMO;
        r2 = r2.getAddress();
        r3 = 78;
        r2 = r2.indexOf(r3);
        if (r2 < 0) goto L_0x019c;
    L_0x0171:
        r0 = r16;
        r2 = r0.mPendingMO;
        r3 = 7;
        r2.mCause = r3;
        r16.pollCallsWhenSafe();
    L_0x017b:
        r0 = r16;
        r2 = r0.mNumberConverted;
        if (r2 == 0) goto L_0x018d;
    L_0x0181:
        r0 = r16;
        r2 = r0.mPendingMO;
        r2.setConverted(r14);
        r2 = 0;
        r0 = r16;
        r0.mNumberConverted = r2;
    L_0x018d:
        r16.updatePhoneState();
        r0 = r16;
        r2 = r0.mPhone;
        r2.notifyPreciseCallStateChanged();
        r0 = r16;
        r2 = r0.mPendingMO;
        goto L_0x0126;
    L_0x019c:
        r2 = 0;
        r0 = r16;
        r0.setMute(r2);
        r16.disableDataCallInEmergencyCall(r17);
        if (r12 == 0) goto L_0x01ab;
    L_0x01a7:
        if (r12 == 0) goto L_0x0244;
    L_0x01a9:
        if (r11 == 0) goto L_0x0244;
    L_0x01ab:
        r8 = 0;
        r0 = r16;
        r2 = r0.mPhone;
        r2 = r2.getSubId();
        r0 = r16;
        r4 = r0.mPendingMO;
        r4 = r4.getAddress();
        r8 = android.telephony.PhoneNumberUtils.getEmergencyServiceCategory(r2, r4);
        r2 = "LGT";
        r3 = "";
        r2 = r2.equals(r3);
        if (r2 == 0) goto L_0x01e2;
    L_0x01ca:
        if (r11 == 0) goto L_0x01e2;
    L_0x01cc:
        r0 = r16;
        r2 = r0.mCi;
        r0 = r16;
        r3 = r0.mPendingMO;
        r3 = r3.getAddress();
        r4 = r16.obtainCompleteMessage();
        r0 = r18;
        r2.dialEmergencyCall(r3, r0, r4);
        goto L_0x017b;
    L_0x01e2:
        r0 = r19;
        r2 = r0.call_domain;
        r3 = 2;
        if (r2 != r3) goto L_0x01ee;
    L_0x01e9:
        r2 = 1;
        r0 = r16;
        r0.mHasVolteCall = r2;
    L_0x01ee:
        r0 = r19;
        r2 = r0.call_type;
        r3 = 3;
        if (r2 != r3) goto L_0x01fa;
    L_0x01f5:
        r2 = 1;
        r0 = r16;
        r0.mHasVideoCall = r2;
    L_0x01fa:
        if (r8 == 0) goto L_0x022a;
    L_0x01fc:
        r0 = r16;
        r2 = r0.mCi;
        r3 = new java.lang.StringBuilder;
        r3.<init>();
        r0 = r16;
        r4 = r0.mPendingMO;
        r4 = r4.getAddress();
        r3 = r3.append(r4);
        r4 = "/";
        r3 = r3.append(r4);
        r3 = r3.append(r8);
        r3 = r3.toString();
        r4 = r16.obtainCompleteMessage();
        r0 = r18;
        r2.dialEmergencyCall(r3, r0, r4);
        goto L_0x017b;
    L_0x022a:
        r0 = r16;
        r2 = r0.mCi;
        r0 = r16;
        r3 = r0.mPendingMO;
        r3 = r3.getAddress();
        r5 = 0;
        r7 = r16.obtainCompleteMessage();
        r4 = r18;
        r6 = r19;
        r2.dial(r3, r4, r5, r6, r7);
        goto L_0x017b;
    L_0x0244:
        r0 = r16;
        r2 = r0.mPhone;
        r2.exitEmergencyCallbackMode();
        r0 = r16;
        r2 = r0.mPhone;
        r3 = 14;
        r4 = 0;
        r0 = r16;
        r2.setOnEcbModeExitResponse(r0, r3, r4);
        r0 = r18;
        r1 = r16;
        r1.mPendingCallClirMode = r0;
        r2 = 1;
        r0 = r16;
        r0.mPendingCallInEcm = r2;
        goto L_0x017b;
        */
        throw new UnsupportedOperationException("Method not decompiled: com.android.internal.telephony.cdma.CdmaCallTracker.dial(java.lang.String, int, com.android.internal.telephony.CallDetails):com.android.internal.telephony.Connection");
    }

    Connection dial(String dialString) throws CallStateException {
        return dial(dialString, 0);
    }

    Connection dial(String convertedDialString, String originalDialString) throws CallStateException {
        return dial(convertedDialString, 0);
    }

    private Connection dialThreeWay(String dialString) {
        if (this.mForegroundCall.isIdle()) {
            return null;
        }
        disableDataCallInEmergencyCall(dialString);
        CdmaConnection actConn = (CdmaConnection) this.mForegroundCall.getCdmaCwActiveConnection();
        if (actConn != null) {
            actConn.isCwActive = false;
            actConn.isCwHolding = true;
        }
        this.mPendingMO = new CdmaConnection(this.mPhone.getContext(), checkForTestEmergencyNumber(dialString), this, this.mForegroundCall);
        this.m3WayCallFlashDelay = this.mPhone.getContext().getResources().getInteger(17694838);
        this.m3WayCallFlashDelay = 0;
        connectionDump("dialThreeWay");
        if (this.m3WayCallFlashDelay > 0) {
            this.mCi.sendCDMAFeatureCode("", obtainMessage(20));
        } else {
            this.mCi.sendCDMAFeatureCode(this.mPendingMO.getAddress(), obtainMessage(16));
        }
        return this.mPendingMO;
    }

    void acceptCall() throws CallStateException {
        Rlog.d(LOG_TAG, "No videoState value. Use AUDIO_ONLY");
        acceptCall(0);
    }

    void acceptCall(int videoState) throws CallStateException {
        int callTypeFromVideoState = ImsCallProfile.getCallTypeFromVideoState(videoState);
        int callType = 0;
        if (callTypeFromVideoState == 5) {
            callType = 1;
        } else if (callTypeFromVideoState == 6) {
            callType = 2;
        } else if (callTypeFromVideoState == 4) {
            callType = 3;
        } else if (callTypeFromVideoState != 2) {
            Rlog.d(LOG_TAG, "Unexpected call type: " + callTypeFromVideoState);
        }
        if (this.mRingingCall.getState() == Call.State.INCOMING) {
            Rlog.i("phone", "acceptCall: incoming...");
            setMute(false);
            if (CscFeature.getInstance().getEnableStatus("CscFeature_RIL_SupportVolte")) {
                this.mCi.acceptCall(callType, obtainCompleteMessage());
            } else {
                this.mCi.acceptCall(obtainCompleteMessage());
            }
        } else if (this.mRingingCall.getState() != Call.State.WAITING) {
            throw new CallStateException("phone not ringing");
        } else if (this.mRingingCall.isImsCall()) {
            setMute(false);
            if (getCallType(this.mRingingCall) != callType) {
                Rlog.i(LOG_TAG, "acceptCall(): ringing call " + getCallType(this.mRingingCall) + " accept as " + callType);
                CdmaConnection ringingConn = (CdmaConnection) this.mRingingCall.getLatestConnection();
                modifyCallInitiate(null, new CallModify(new CallDetails(callType, ringingConn.getCallDetails().call_domain, null), ringingConn.mIndex + 1));
            }
            switchWaitingOrHoldingAndActive();
        } else {
            CdmaConnection cwConn = (CdmaConnection) this.mRingingCall.getLatestConnection();
            cwConn.updateParent(this.mRingingCall, this.mForegroundCall);
            cwConn.onConnectedInOrOut();
            updatePhoneState();
            switchWaitingOrHoldingAndActive();
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
        boolean z = true;
        if (this.mRingingCall.getState() == Call.State.INCOMING) {
            throw new CallStateException("cannot be in the incoming state");
        } else if (this.mForegroundCall.isImsCall()) {
            this.mCi.switchWaitingOrHoldingAndActive(obtainCompleteMessage(105));
        } else if (this.mForegroundCall.getConnections().size() > 1) {
            CdmaConnection actConn = (CdmaConnection) this.mForegroundCall.getCdmaCwActiveConnection();
            CdmaConnection holdConn = (CdmaConnection) this.mForegroundCall.getCdmaCwHoldingConnection();
            if (!(actConn == null || holdConn == null)) {
                holdConn.isCwActive = true;
                holdConn.isCwHolding = false;
                actConn.isCwActive = false;
                actConn.isCwHolding = true;
            }
            connectionDump("switchWaitingOrHoldingAndActive - switch");
            flashAndSetGenericTrue();
        } else {
            CdmaConnection conn = (CdmaConnection) this.mForegroundCall.getLatestConnection();
            if (conn != null) {
                conn.isCwActive = !conn.isCwActive;
                if (conn.isCwHolding) {
                    z = false;
                }
                conn.isCwHolding = z;
            }
            connectionDump("switchWaitingOrHoldingAndActive - hold");
            this.mCi.sendCDMAFeatureCode("", obtainMessage(8));
        }
    }

    void conference() {
        Rlog.d(LOG_TAG, "Conference");
        if (this.mForegroundCall.isImsCall()) {
            this.mCi.conference(obtainCompleteMessage(11));
        } else {
            flashAndSetGenericTrue();
        }
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
        boolean ret;
        boolean z = true;
        int serviceState = this.mPhone.getServiceState().getState();
        String disableCall = SystemProperties.get("ro.telephony.disable-call", "false");
        if (serviceState == 3 || this.mPendingMO != null || this.mRingingCall.isRinging() || disableCall.equals("true") || (this.mForegroundCall.getState().isAlive() && this.mForegroundCall.getState() != Call.State.ACTIVE && this.mBackgroundCall.getState().isAlive())) {
            ret = false;
        } else {
            ret = true;
        }
        if (!ret) {
            boolean z2;
            String str = "canDial is false\n((serviceState=%d) != ServiceState.STATE_POWER_OFF)::=%s\n&& pendingMO == null::=%s\n&& !ringingCall.isRinging()::=%s\n&& !disableCall.equals(\"true\")::=%s\n&& (!foregroundCall.getState().isAlive()::=%s\n   || foregroundCall.getState() == CdmaCall.State.ACTIVE::=%s\n   ||!backgroundCall.getState().isAlive())::=%s)";
            Object[] objArr = new Object[8];
            objArr[0] = Integer.valueOf(serviceState);
            if (serviceState != 3) {
                z2 = true;
            } else {
                z2 = false;
            }
            objArr[1] = Boolean.valueOf(z2);
            if (this.mPendingMO == null) {
                z2 = true;
            } else {
                z2 = false;
            }
            objArr[2] = Boolean.valueOf(z2);
            if (this.mRingingCall.isRinging()) {
                z2 = false;
            } else {
                z2 = true;
            }
            objArr[3] = Boolean.valueOf(z2);
            if (disableCall.equals("true")) {
                z2 = false;
            } else {
                z2 = true;
            }
            objArr[4] = Boolean.valueOf(z2);
            if (this.mForegroundCall.getState().isAlive()) {
                z2 = false;
            } else {
                z2 = true;
            }
            objArr[5] = Boolean.valueOf(z2);
            if (this.mForegroundCall.getState() == Call.State.ACTIVE) {
                z2 = true;
            } else {
                z2 = false;
            }
            objArr[6] = Boolean.valueOf(z2);
            if (this.mBackgroundCall.getState().isAlive()) {
                z = false;
            }
            objArr[7] = Boolean.valueOf(z);
            log(String.format(str, objArr));
        }
        return ret;
    }

    boolean canTransfer() {
        Rlog.e(LOG_TAG, "canTransfer: not possible in CDMA");
        return false;
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
        return obtainMessage(what);
    }

    private void operationComplete() {
        this.mPendingOperations--;
        if (this.mPendingOperations == 0 && this.mNeedsPoll) {
            this.mLastRelevantPoll = obtainMessage(1);
            this.mCi.getCurrentCalls(this.mLastRelevantPoll);
        } else if (this.mPendingOperations < 0) {
            Rlog.e(LOG_TAG, "CdmaCallTracker.pendingOperations < 0");
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
        log("update phone state, old=" + oldState + " new=" + this.mState);
        if (this.mState != oldState) {
            this.mPhone.notifyPhoneStateChanged();
        }
    }

    protected void handlePollCalls(AsyncResult ar) {
        List polledCalls;
        int i;
        if (ar.exception == null) {
            polledCalls = ar.result;
        } else {
            if (isCommandExceptionRadioNotAvailable(ar.exception)) {
                polledCalls = new ArrayList();
            } else {
                pollCallsAfterDelay();
                return;
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
        int curDC = 0;
        int dcSize = polledCalls.size();
        for (i = 0; i < this.mConnections.length; i++) {
            Connection conn = this.mConnections[i];
            DriverCall dc = null;
            if (curDC < dcSize) {
                dc = (DriverCall) polledCalls.get(curDC);
                if (dc.index == i + 1) {
                    curDC++;
                } else {
                    dc = null;
                }
            }
            if (conn == null && dc != null) {
                if (this.mPendingMO == null || !this.mPendingMO.compareTo(dc)) {
                    log("pendingMo=" + this.mPendingMO + ", dc=" + dc);
                    this.mConnections[i] = new CdmaConnection(this.mPhone.getContext(), dc, this, i);
                    if (this.mHandoverConnection != null) {
                        this.mPhone.migrateFrom((PhoneBase) this.mPhone.getImsPhone());
                        this.mConnections[i].migrateFrom(this.mHandoverConnection);
                        this.mPhone.notifyHandoverStateChanged(this.mConnections[i]);
                        this.mHandoverConnection = null;
                    } else {
                        newRinging = checkMtFindNewRinging(dc, i);
                        if (newRinging == null) {
                            unknownConnectionAppeared = true;
                            newUnknown = this.mConnections[i];
                        }
                    }
                    checkAndEnableDataCallAfterEmergencyCallDropped();
                } else {
                    this.mConnections[i] = this.mPendingMO;
                    this.mPendingMO.mIndex = i;
                    this.mPendingMO.update(dc);
                    this.mPendingMO = null;
                    if (this.mHangupPendingMO) {
                        this.mHangupPendingMO = false;
                        if (this.mIsEcmTimerCanceled) {
                            handleEcmTimer(0);
                        }
                        try {
                            log("poll: hangupPendingMO, hangup conn " + i);
                            hangup(this.mConnections[i]);
                            return;
                        } catch (CallStateException e) {
                            Rlog.e(LOG_TAG, "unexpected error on hangup");
                            return;
                        }
                    }
                }
                hasNonHangupStateChanged = true;
            } else if (conn != null && dc == null) {
                if (conn.getCall().isImsCall()) {
                    this.mDroppedDuringPoll.add(conn);
                } else {
                    int n;
                    int count = this.mForegroundCall.mConnections.size();
                    for (n = 0; n < count; n++) {
                        log("adding fgCall cn " + n + " to droppedDuringPoll");
                        this.mDroppedDuringPoll.add((CdmaConnection) this.mForegroundCall.mConnections.get(n));
                    }
                    count = this.mRingingCall.mConnections.size();
                    for (n = 0; n < count; n++) {
                        log("adding rgCall cn " + n + " to droppedDuringPoll");
                        this.mDroppedDuringPoll.add((CdmaConnection) this.mRingingCall.mConnections.get(n));
                    }
                    this.mForegroundCall.setGeneric(false);
                    this.mRingingCall.setGeneric(false);
                }
                if (this.mIsEcmTimerCanceled) {
                    handleEcmTimer(0);
                }
                checkAndEnableDataCallAfterEmergencyCallDropped();
                this.mConnections[i] = null;
            } else if (!(conn == null || dc == null)) {
                if (conn.isIncoming() != dc.isMT) {
                    if (dc.isMT) {
                        this.mDroppedDuringPoll.add(conn);
                        newRinging = checkMtFindNewRinging(dc, i);
                        if (newRinging == null) {
                            unknownConnectionAppeared = true;
                            newUnknown = conn;
                        }
                        checkAndEnableDataCallAfterEmergencyCallDropped();
                    } else {
                        Rlog.e(LOG_TAG, "Error in RIL, Phantom call appeared " + dc);
                    }
                } else if ((conn.getState() == Call.State.ACTIVE || conn.getState() == Call.State.HOLDING) && dc.state == DriverCall.State.DIALING) {
                    log("Call collision case (ACTIVE/HOLDING -> DIALING)");
                    this.mDroppedDuringPoll.add(conn);
                    if (this.mPendingMO == null || !this.mPendingMO.compareTo(dc)) {
                        this.mConnections[i] = new CdmaConnection(this.mPhone.getContext(), dc, this, i);
                        if (this.mHandoverConnection != null) {
                            this.mPhone.migrateFrom((PhoneBase) this.mPhone.getImsPhone());
                            this.mConnections[i].migrateFrom(this.mHandoverConnection);
                            this.mPhone.notifyHandoverStateChanged(this.mConnections[i]);
                            this.mHandoverConnection = null;
                        } else {
                            newRinging = checkMtFindNewRinging(dc, i);
                            if (newRinging == null) {
                                unknownConnectionAppeared = true;
                                newUnknown = this.mConnections[i];
                            }
                        }
                    } else {
                        log("pendingMO: " + this.mPendingMO);
                        this.mConnections[i] = this.mPendingMO;
                        this.mPendingMO.mIndex = i;
                        this.mPendingMO.update(dc);
                        this.mPendingMO = null;
                        if (this.mHangupPendingMO) {
                            this.mHangupPendingMO = false;
                        }
                    }
                    checkAndEnableDataCallAfterEmergencyCallDropped();
                } else {
                    hasNonHangupStateChanged = hasNonHangupStateChanged || conn.update(dc);
                }
            }
            if (!(dc == null || dc.callDetails == null)) {
                if (dc.callDetails.call_domain == 2) {
                    this.mHasVolteCall = true;
                }
                if (dc.callDetails.call_type == 3) {
                    this.mHasVideoCall = true;
                }
            }
        }
        if (this.mPendingMO != null) {
            Rlog.d(LOG_TAG, "Pending MO dropped before poll fg state:" + this.mForegroundCall.getState());
            if (PhoneNumberUtils.isLocalEmergencyNumber(this.mPhone.getContext(), this.mPendingMO.getOrigDialString())) {
                checkAndEnableDataCallAfterEmergencyCallDropped();
            }
            this.mDroppedDuringPoll.add(this.mPendingMO);
            this.mPendingMO = null;
            this.mHangupPendingMO = false;
            if (this.mPendingCallInEcm) {
                this.mPendingCallInEcm = false;
            }
        }
        if (newRinging != null) {
            this.mPhone.notifyNewRingingConnection(newRinging);
        }
        for (i = this.mDroppedDuringPoll.size() - 1; i >= 0; i--) {
            CdmaConnection conn2 = (CdmaConnection) this.mDroppedDuringPoll.get(i);
            if (conn2.isIncoming() && conn2.getConnectTime() == 0) {
                int cause;
                if (conn2.mCause == 3) {
                    cause = 16;
                } else {
                    cause = 1;
                }
                if (false) {
                    cause = 1;
                    logCallEvent("missed", conn2.getAddress(), conn2.getCreateTime(), conn2.getDurationMillis(), conn2.isIncoming());
                }
                log("missed/rejected call, conn.cause=" + conn2.mCause);
                log("setting cause to " + cause);
                this.mDroppedDuringPoll.remove(i);
                hasAnyCallDisconnected |= conn2.onDisconnect(cause);
            } else if (conn2.mCause == 3 || conn2.mCause == 7) {
                this.mDroppedDuringPoll.remove(i);
                hasAnyCallDisconnected |= conn2.onDisconnect(conn2.mCause);
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
        String finalRadioTech = null;
        for (Call call : new Call[]{this.mRingingCall, this.mForegroundCall, this.mBackgroundCall}) {
            if (!call.isIdle()) {
                String callRadioTech = call.getCallRadioTech();
                if (!(callRadioTech == null || TextUtils.isEmpty(callRadioTech) || callRadioTech.equals(finalRadioTech))) {
                    if (!(finalRadioTech == null || TextUtils.isEmpty(finalRadioTech))) {
                        log("CallRadioTech collision (Before: " + finalRadioTech + ", After: " + callRadioTech);
                    }
                    finalRadioTech = callRadioTech;
                }
            }
        }
        if (finalRadioTech != null && !TextUtils.isEmpty(finalRadioTech)) {
            SystemProperties.set("ril.cdma.lastcallrat", finalRadioTech);
        }
    }

    void hangup(CdmaConnection conn) throws CallStateException {
        if (conn.mOwner != this) {
            throw new CallStateException("CdmaConnection " + conn + "does not belong to CdmaCallTracker " + this);
        }
        if (conn == this.mPendingMO) {
            log("hangup: set hangupPendingMO to true");
            this.mHangupPendingMO = true;
        } else if (conn.getCall() == this.mRingingCall && this.mRingingCall.getState() == Call.State.WAITING) {
            conn.onLocalDisconnect();
            updatePhoneState();
            this.mPhone.notifyPreciseCallStateChanged();
            return;
        } else {
            try {
                this.mCi.hangupConnection(conn.getCDMAIndex(), obtainCompleteMessage());
            } catch (CallStateException e) {
                Rlog.w(LOG_TAG, "CdmaCallTracker WARN: hangup() on absent connection " + conn);
            }
        }
        conn.onHangupLocal();
    }

    void separate(CdmaConnection conn) throws CallStateException {
        if (conn.mOwner != this) {
            throw new CallStateException("CdmaConnection " + conn + "does not belong to CdmaCallTracker " + this);
        }
        try {
            this.mCi.separateConnection(conn.getCDMAIndex(), obtainCompleteMessage(12));
        } catch (CallStateException e) {
            Rlog.w(LOG_TAG, "CdmaCallTracker WARN: separate() on absent connection " + conn);
        }
    }

    void setMute(boolean mute) {
        this.mDesiredMute = mute;
        this.mCi.setMute(this.mDesiredMute, null);
    }

    boolean getMute() {
        return this.mDesiredMute;
    }

    void hangup(CdmaCall call) throws CallStateException {
        if (call.getConnections().size() == 0) {
            throw new CallStateException("no connections in call");
        }
        if (call == this.mRingingCall) {
            log("(ringing) hangup waiting or background");
            this.mCi.hangupWaitingOrBackground(obtainCompleteMessage());
        } else if (call == this.mForegroundCall) {
            if (call.isDialingOrAlerting()) {
                log("(foregnd) hangup dialing or alerting...");
                hangup((CdmaConnection) call.getConnections().get(0));
            } else {
                hangupForegroundResumeBackground();
            }
        } else if (call != this.mBackgroundCall) {
            throw new RuntimeException("CdmaCall " + call + "does not belong to CdmaCallTracker " + this);
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

    void hangupConnectionByIndex(CdmaCall call, int index) throws CallStateException {
        int count = call.mConnections.size();
        for (int i = 0; i < count; i++) {
            if (((CdmaConnection) call.mConnections.get(i)).getCDMAIndex() == index) {
                this.mCi.hangupConnection(index, obtainCompleteMessage());
                return;
            }
        }
        throw new CallStateException("no gsm index found");
    }

    void hangupAllConnections(CdmaCall call) {
        try {
            int count = call.mConnections.size();
            for (int i = 0; i < count; i++) {
                this.mCi.hangupConnection(((CdmaConnection) call.mConnections.get(i)).getCDMAIndex(), obtainCompleteMessage());
            }
        } catch (CallStateException ex) {
            Rlog.e(LOG_TAG, "hangupConnectionByIndex caught " + ex);
        }
    }

    CdmaConnection getConnectionByIndex(int index) {
        for (CdmaConnection c : this.mConnections) {
            if (c != null) {
                try {
                    if (c.getCDMAIndex() == index) {
                        return c;
                    }
                } catch (CallStateException e) {
                    Rlog.w(LOG_TAG, " absent connection for index " + index);
                }
            }
        }
        return null;
    }

    CdmaConnection getConnectionByIndex(CdmaCall call, int index) throws CallStateException {
        int count = call.mConnections.size();
        for (int i = 0; i < count; i++) {
            CdmaConnection cn = (CdmaConnection) call.mConnections.get(i);
            if (cn.getCDMAIndex() == index) {
                return cn;
            }
        }
        return null;
    }

    private void flashAndSetGenericTrue() {
        this.mCi.sendCDMAFeatureCode("", obtainMessage(8));
        this.mForegroundCall.setGeneric(true);
        this.mPhone.notifyPreciseCallStateChanged();
    }

    private void handleRadioNotAvailable() {
        pollCallsWhenSafe();
    }

    private void notifyCallWaitingInfo(CdmaCallWaitingNotification obj) {
        if (this.mCallWaitingRegistrants != null) {
            this.mCallWaitingRegistrants.notifyRegistrants(new AsyncResult(null, obj, null));
        }
    }

    private void handleCallWaitingInfo(CdmaCallWaitingNotification cw) {
        if (this.mForegroundCall.mConnections.size() > 1) {
            this.mForegroundCall.setGeneric(true);
        }
        this.mRingingCall.setGeneric(false);
        CdmaConnection cdmaConnection = new CdmaConnection(this.mPhone.getContext(), cw, this, this.mRingingCall);
        updatePhoneState();
        notifyCallWaitingInfo(cw);
    }

    public void handleMessage(Message msg) {
        if (this.mPhone.mIsTheCurrentActivePhone) {
            AsyncResult ar;
            switch (msg.what) {
                case 1:
                    Rlog.d(LOG_TAG, "Event EVENT_POLL_CALLS_RESULT Received");
                    ar = msg.obj;
                    if (msg == this.mLastRelevantPoll) {
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
                    operationComplete();
                    return;
                case 5:
                    int causeCode;
                    ar = (AsyncResult) msg.obj;
                    operationComplete();
                    int sipError = 0;
                    if (ar.exception != null) {
                        causeCode = 16;
                        Rlog.i(LOG_TAG, "Exception during getLastCallFailCause, assuming normal disconnect");
                    } else {
                        causeCode = ((int[]) ar.result)[0];
                        try {
                            sipError = ((int[]) ar.result)[2];
                            Rlog.d(LOG_TAG, "SipError " + sipError);
                        } catch (IndexOutOfBoundsException e) {
                            sipError = 0;
                        }
                    }
                    int s = this.mDroppedDuringPoll.size();
                    for (int i = 0; i < s; i++) {
                        ((CdmaConnection) this.mDroppedDuringPoll.get(i)).onRemoteDisconnect(causeCode, sipError);
                    }
                    updatePhoneState();
                    this.mPhone.notifyPreciseCallStateChanged();
                    this.mDroppedDuringPoll.clear();
                    return;
                case 8:
                case 12:
                case 13:
                    return;
                case 9:
                    handleRadioAvailable();
                    return;
                case 10:
                    handleRadioNotAvailable();
                    return;
                case 11:
                case 105:
                    if (CscFeature.getInstance().getEnableStatus("CscFeature_RIL_SupportVolte")) {
                        if (((AsyncResult) msg.obj).exception != null) {
                            this.mPhone.notifySuppServiceFailed(getFailedService(msg.what));
                        }
                        operationComplete();
                        return;
                    }
                    return;
                case 14:
                    if (this.mPendingCallInEcm) {
                        this.mCi.dial(this.mPendingMO.getAddress(), this.mPendingCallClirMode, obtainCompleteMessage());
                        this.mPendingCallInEcm = false;
                    }
                    this.mPhone.unsetOnEcbModeExitResponse(this);
                    return;
                case 15:
                    ar = (AsyncResult) msg.obj;
                    if (ar.exception == null) {
                        handleCallWaitingInfo((CdmaCallWaitingNotification) ar.result);
                        Rlog.d(LOG_TAG, "Event EVENT_CALL_WAITING_INFO_CDMA Received");
                        return;
                    }
                    return;
                case 16:
                    if (((AsyncResult) msg.obj).exception == null) {
                        this.mPendingMO.onConnectedInOrOut();
                        this.mPendingMO = null;
                        return;
                    }
                    CdmaConnection holdConn = (CdmaConnection) this.mForegroundCall.getCdmaCwHoldingConnection();
                    if (holdConn != null) {
                        holdConn.isCwActive = true;
                        holdConn.isCwHolding = false;
                    }
                    connectionDump("EVENT_THREE_WAY_DIAL_L2_RESULT_CDMA");
                    return;
                case 20:
                    if (((AsyncResult) msg.obj).exception == null) {
                        postDelayed(new C00721(), (long) this.m3WayCallFlashDelay);
                        return;
                    }
                    this.mPendingMO = null;
                    Rlog.w(LOG_TAG, "exception happened on Blank Flash for 3-way call");
                    return;
                case Threads.ALERT_SEVERE_THREAD /*102*/:
                    ar = (AsyncResult) msg.obj;
                    if (ar != null && ar.result != null && ar.exception == null) {
                        handleModifyCallRequest((CallModify) ar.result, ar.exception);
                        return;
                    }
                    return;
                case Threads.ALERT_TEST_MESSAGE_THREAD /*104*/:
                    ar = (AsyncResult) msg.obj;
                    if (ar != null && ar.result != null && ar.exception == null) {
                        this.mCsFallback = ((int[]) ar.result)[0];
                        Rlog.e(LOG_TAG, "mCsFallback: " + this.mCsFallback + ", mIsInEmergencyCall: " + this.mIsInEmergencyCall + ", mSkipDisableDataConnection: " + this.mSkipDisableDataConnection);
                        if (this.mIsInEmergencyCall && this.mSkipDisableDataConnection) {
                            this.mPhone.mDcTracker.setInternalDataEnabled(false);
                            this.mSkipDisableDataConnection = false;
                        }
                        this.mPhone.notifyDataConnection(null);
                        return;
                    }
                    return;
                default:
                    throw new RuntimeException("unexpected event not handled");
            }
        }
        Rlog.w(LOG_TAG, "Ignoring events received on inactive CdmaPhone");
    }

    private void handleEcmTimer(int action) {
        this.mPhone.handleTimerInEmergencyCallbackMode(action);
        switch (action) {
            case 0:
                this.mIsEcmTimerCanceled = false;
                return;
            case 1:
                this.mIsEcmTimerCanceled = true;
                return;
            default:
                Rlog.e(LOG_TAG, "handleEcmTimer, unsupported action " + action);
                return;
        }
    }

    private void disableDataCallInEmergencyCall(String dialString) {
        this.mSkipDisableDataConnection = false;
        if (PhoneNumberUtils.isLocalEmergencyNumber(this.mPhone.getContext(), dialString)) {
            log("disableDataCallInEmergencyCall");
            SystemProperties.set("ril.cdma.ine911", "true");
            this.mIsInEmergencyCall = true;
            if (!"LGT".equals("")) {
                if (CscFeature.getInstance().getEnableStatus("CscFeature_RIL_SupportVolte") && "VZW-CDMA".equals("") && (!"READY".equals(SystemProperties.get("gsm.sim.state", "UNKNOWN")) || this.mPhone.getServiceState().getRilDataRadioTechnology() == 14 || this.mPhone.getServiceState().getDataRegState() != 0)) {
                    log("Do not disable mobile data connection for VoLTE");
                    this.mSkipDisableDataConnection = true;
                    return;
                }
                this.mPhone.mDcTracker.setInternalDataEnabled(false);
            }
        }
    }

    private void checkAndEnableDataCallAfterEmergencyCallDropped() {
        if (this.mIsInEmergencyCall) {
            SystemProperties.set("ril.cdma.ine911", "false");
            this.mIsInEmergencyCall = false;
            String inEcm = SystemProperties.get("ril.cdma.inecmmode", "false");
            log("checkAndEnableDataCallAfterEmergencyCallDropped,inEcm=" + inEcm);
            if (inEcm.compareTo("false") == 0) {
                this.mPhone.mDcTracker.setInternalDataEnabled(true);
            }
        }
    }

    private Connection checkMtFindNewRinging(DriverCall dc, int i) {
        if (this.mConnections[i].getCall() == this.mRingingCall) {
            Connection newRinging = this.mConnections[i];
            log("Notify new ring " + dc);
            return newRinging;
        }
        Rlog.e(LOG_TAG, "Phantom call appeared " + dc);
        if (dc.state == DriverCall.State.ALERTING || dc.state == DriverCall.State.DIALING) {
            return null;
        }
        this.mConnections[i].onConnectedInOrOut();
        if (dc.state != DriverCall.State.HOLDING) {
            return null;
        }
        this.mConnections[i].onStartedHolding();
        return null;
    }

    boolean isInEmergencyCall() {
        return this.mIsInEmergencyCall;
    }

    private void fakeHoldForegroundBeforeDial() {
        List<Connection> connCopy = (List) this.mForegroundCall.mConnections.clone();
        int s = connCopy.size();
        for (int i = 0; i < s; i++) {
            ((CdmaConnection) connCopy.get(i)).fakeHoldBeforeDial();
        }
    }

    public boolean isAllActiveCallsOnLTE() {
        boolean ret = CscFeature.getInstance().getEnableStatus("CscFeature_RIL_SupportVolte");
        if (!ret) {
            return ret;
        }
        if (this.mState == State.IDLE) {
            return false;
        }
        for (Call call : new Call[]{this.mRingingCall, this.mForegroundCall, this.mBackgroundCall}) {
            if (!call.isIdle() && !call.isImsCall()) {
                log("Non VoLTE call is active: " + call);
                ret = false;
                break;
            }
        }
        if (this.mCsFallback != 1) {
            return ret;
        }
        log("mCsFallback: " + this.mCsFallback);
        return false;
    }

    public boolean hasCallOnLTE() {
        if (!CscFeature.getInstance().getEnableStatus("CscFeature_RIL_SupportVolte") || this.mCsFallback != 0) {
            return false;
        }
        Call[] arr$ = new Call[]{this.mRingingCall, this.mForegroundCall, this.mBackgroundCall};
        int len$ = arr$.length;
        int i$ = 0;
        while (i$ < len$) {
            Call call = arr$[i$];
            if (call.isIdle() || !call.isImsCall()) {
                i$++;
            } else {
                log("VoLTE call: " + call);
                return true;
            }
        }
        return false;
    }

    private SuppService getFailedService(int what) {
        switch (what) {
            case 8:
            case 105:
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

    private void handleModifyCallRequest(CallModify cm, Throwable e) {
        Rlog.d(LOG_TAG, "handleCallModifyRequest(" + cm + ", " + e + ")");
        if (cm != null) {
            CdmaConnection c = getConnectionByIndex(cm.call_index);
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

    protected void log(String msg) {
        Rlog.d(LOG_TAG, "[CdmaCallTracker] " + msg);
    }

    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        int i;
        pw.println("CdmaCallTracker extends:");
        super.dump(fd, pw, args);
        pw.println("droppedDuringPoll: length=" + this.mConnections.length);
        for (i = 0; i < this.mConnections.length; i++) {
            pw.printf(" mConnections[%d]=%s\n", new Object[]{Integer.valueOf(i), this.mConnections[i]});
        }
        pw.println(" mVoiceCallEndedRegistrants=" + this.mVoiceCallEndedRegistrants);
        pw.println(" mVoiceCallStartedRegistrants=" + this.mVoiceCallStartedRegistrants);
        pw.println(" mCallWaitingRegistrants=" + this.mCallWaitingRegistrants);
        pw.println("droppedDuringPoll: size=" + this.mDroppedDuringPoll.size());
        for (i = 0; i < this.mDroppedDuringPoll.size(); i++) {
            pw.printf(" mDroppedDuringPoll[%d]=%s\n", new Object[]{Integer.valueOf(i), this.mDroppedDuringPoll.get(i)});
        }
        pw.println(" mRingingCall=" + this.mRingingCall);
        pw.println(" mForegroundCall=" + this.mForegroundCall);
        pw.println(" mBackgroundCall=" + this.mBackgroundCall);
        pw.println(" mPendingMO=" + this.mPendingMO);
        pw.println(" mHangupPendingMO=" + this.mHangupPendingMO);
        pw.println(" mPendingCallInEcm=" + this.mPendingCallInEcm);
        pw.println(" mIsInEmergencyCall=" + this.mIsInEmergencyCall);
        pw.println(" mPhone=" + this.mPhone);
        pw.println(" mDesiredMute=" + this.mDesiredMute);
        pw.println(" mPendingCallClirMode=" + this.mPendingCallClirMode);
        pw.println(" mState=" + this.mState);
        pw.println(" mIsEcmTimerCanceled=" + this.mIsEcmTimerCanceled);
    }

    public void connectionDump(String title) {
        if (!(title == null || TextUtils.isEmpty(title))) {
            Rlog.d(LOG_TAG, "ConnectionDump: " + title);
        }
        Rlog.d(LOG_TAG, "----- Ringing Call(" + this.mRingingCall + ") -----");
        this.mRingingCall.connectionDump();
        Rlog.d(LOG_TAG, "----- Foreground Call(" + this.mForegroundCall + ") -----");
        this.mForegroundCall.connectionDump();
        Rlog.d(LOG_TAG, "----- Background Call(" + this.mBackgroundCall + ") -----");
        this.mBackgroundCall.connectionDump();
    }
}
