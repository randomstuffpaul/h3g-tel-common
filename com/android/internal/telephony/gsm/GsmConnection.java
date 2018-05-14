package com.android.internal.telephony.gsm;

import android.content.Context;
import android.os.AsyncResult;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.os.Registrant;
import android.os.SystemClock;
import android.provider.Telephony.Threads;
import android.telephony.PhoneNumberUtils;
import android.telephony.Rlog;
import android.text.TextUtils;
import com.android.internal.telephony.Call;
import com.android.internal.telephony.CallDetails;
import com.android.internal.telephony.CallManager;
import com.android.internal.telephony.CallStateException;
import com.android.internal.telephony.CallTracker;
import com.android.internal.telephony.CommandsInterface;
import com.android.internal.telephony.Connection;
import com.android.internal.telephony.Connection.PostDialState;
import com.android.internal.telephony.DriverCall;
import com.android.internal.telephony.DriverCall.State;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.UUSInfo;
import com.android.internal.telephony.WspTypeDecoder;
import com.android.internal.telephony.cdma.sms.BearerData;
import com.android.internal.telephony.uicc.IccCardApplicationStatus.AppState;
import com.android.internal.telephony.uicc.UiccCardApplication;
import com.google.android.mms.pdu.PduHeaders;
import com.google.android.mms.pdu.PduPart;
import com.sec.android.app.CscFeature;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class GsmConnection extends Connection {
    private static final boolean DBG = true;
    static final int EVENT_DTMF_DONE = 1;
    static final int EVENT_NEXT_POST_DIAL = 3;
    static final int EVENT_PAUSE_DONE = 2;
    static final int EVENT_WAKE_LOCK_TIMEOUT = 4;
    static final int HD_VOICE_ALARM_EVENT_ABORT_SESSION_BY_PCRF = 990101;
    static final int HD_VOICE_ALARM_EVENT_HANDOVER_FAIL_BY_PCRF = 990102;
    static final int HD_VOICE_ALARM_EVENT_NO_RTP_A = 990103;
    static final int HD_VOICE_ALARM_EVENT_NO_RTP_B = 990104;
    static final int HD_VOICE_ALARM_EVENT_NO_UDP = 990105;
    static final int HD_VOICE_ALARM_EVENT_NO_UDP_RESP = 990106;
    static final int IMS_ABORT_SESSION_BY_PCRF = 2111;
    static final int IMS_HANDOVER_FAIL_BY_PCRF = 2112;
    static final int IMS_NO_RTP_A = 2113;
    static final int IMS_NO_RTP_B = 1401;
    static final int IMS_NO_UDP = 2114;
    static final int IMS_NO_UDP_RESP = 2115;
    private static final String LOG_TAG = "GsmConnection";
    static final int PAUSE_DELAY_MILLIS = 3000;
    static final int WAKE_LOCK_TIMEOUT_MILLIS = 60000;
    String cdnipNumber;
    int cwToneSignal;
    int mCause;
    long mDisconnectTime;
    boolean mDisconnected;
    Handler mHandler;
    int mIndex;
    boolean mIsInAnsweringMessage;
    int mNextPostDialChar;
    Connection mOrigConnection;
    GsmCallTracker mOwner;
    GsmCall mParent;
    private WakeLock mPartialWakeLock;
    PostDialState mPostDialState;
    String mPostDialString;
    int mPreciseCause;
    UUSInfo mUusInfo;
    int rawCause;

    static /* synthetic */ class C01041 {
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
                    GsmConnection.this.processNextPostDialChar();
                    return;
                case 4:
                    GsmConnection.this.releaseWakeLock();
                    return;
                default:
                    return;
            }
        }
    }

    GsmConnection(Context context, DriverCall dc, GsmCallTracker ct, int index) {
        this.mCause = 0;
        this.mPostDialState = PostDialState.NOT_STARTED;
        this.mPreciseCause = 0;
        this.mIsInAnsweringMessage = false;
        this.cwToneSignal = 0;
        this.rawCause = 0;
        createWakeLock(context);
        acquireWakeLock();
        this.mOwner = ct;
        this.mHandler = new MyHandler(this.mOwner.getLooper());
        this.mAddress = dc.number;
        this.mIsIncoming = dc.isMT;
        this.mCreateTime = System.currentTimeMillis();
        this.mCnapNamePresentation = 1;
        this.mCnapName = null;
        this.mNumberPresentation = dc.numberPresentation;
        this.mUusInfo = dc.uusInfo;
        this.mIndex = index;
        setId(dc.id);
        setCallDetails(dc.callDetails);
        this.mParent = parentFromDCState(dc.state);
        this.mParent.attach(this, dc);
    }

    GsmConnection(Context context, String dialString, GsmCallTracker ct, GsmCall parent) {
        this(context, dialString, ct, parent, null);
    }

    GsmConnection(Context context, String dialString, GsmCallTracker ct, GsmCall parent, CallDetails callDetails) {
        this.mCause = 0;
        this.mPostDialState = PostDialState.NOT_STARTED;
        this.mPreciseCause = 0;
        this.mIsInAnsweringMessage = false;
        this.cwToneSignal = 0;
        this.rawCause = 0;
        createWakeLock(context);
        acquireWakeLock();
        this.mOwner = ct;
        this.mHandler = new MyHandler(this.mOwner.getLooper());
        this.mDialString = dialString;
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
        this.cdnipNumber = null;
        this.cwToneSignal = 0;
        this.mParent = parent;
        parent.attachFake(this, Call.State.DIALING);
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
        if ((!this.mIsIncoming && !c.isMT) || this.mOrigConnection != null) {
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

    public GsmCall getCall() {
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
        return super.getState();
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
            Rlog.w(LOG_TAG, "GsmConnection.proceedAfterWaitChar(): Expected getPostDialState() to be WAIT but was " + this.mPostDialState);
            return;
        }
        setPostDialState(PostDialState.STARTED);
        processNextPostDialChar();
    }

    public void proceedAfterWildChar(String str) {
        if (this.mPostDialState != PostDialState.WILD) {
            Rlog.w(LOG_TAG, "GsmConnection.proceedAfterWaitChar(): Expected getPostDialState() to be WILD but was " + this.mPostDialState);
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
        boolean isKor = "SKT".equals("") || "KTT".equals("") || "LGT".equals("");
        Rlog.d(LOG_TAG, "[GSMConn] disconnectCauseFromCode: causeCode=" + causeCode);
        if (!isKor || this.sipError <= 0) {
            switch (causeCode) {
                case 1:
                    return 25;
                case 2:
                case 38:
                case 1100:
                    return Threads.ALERT_AMBER_THREAD;
                case 17:
                    return 4;
                case 18:
                case 19:
                    return 100;
                case 34:
                case 41:
                case 42:
                case 44:
                case 49:
                case 58:
                    return 5;
                case 68:
                    return 15;
                case 240:
                    return 20;
                case 241:
                    return 21;
                default:
                    GSMPhone phone = this.mOwner.mPhone;
                    int serviceState = phone.getServiceState().getState();
                    UiccCardApplication cardApp = phone.getUiccCardApplication();
                    AppState uiccAppState = cardApp != null ? cardApp.getState() : AppState.APPSTATE_UNKNOWN;
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
                    if (uiccAppState != AppState.APPSTATE_READY) {
                        return 19;
                    }
                    if (causeCode == 65535) {
                        if (phone.mSST.mRestrictedState.isCsRestricted()) {
                            return 22;
                        }
                        if (phone.mSST.mRestrictedState.isCsEmergencyRestricted()) {
                            return 24;
                        }
                        return phone.mSST.mRestrictedState.isCsNormalRestricted() ? 23 : 36;
                    } else if (causeCode == 16) {
                        return 2;
                    } else {
                        return 36;
                    }
            }
        }
        Rlog.d(LOG_TAG, "[GSMConn] disconnectCauseFromCode: sipError=" + this.sipError);
        return Threads.ALERT_EXTREME_THREAD;
    }

    int disconnectCauseFromCode(int causeCode, int callFailCause) {
        if (!CscFeature.getInstance().getEnableStatus("CscFeature_VoiceCall_EnableDetailCallEndCause")) {
            return 36;
        }
        switch (callFailCause + 3000) {
            case CallFailCause.CAUSE_UNASSIGNED_NUMBER /*3001*/:
                return 202;
            case CallFailCause.CAUSE_NO_ROUTE_TO_DESTINATION /*3003*/:
                return 203;
            case CallFailCause.CAUSE_CHANNEL_UNACCEPTABLE /*3006*/:
                return 204;
            case CallFailCause.CAUSE_OP_DETERMINED_BARRING /*3008*/:
                return 205;
            case CallFailCause.CAUSE_NORMAL_CLEARING /*3016*/:
                return 2;
            case CallFailCause.CAUSE_USER_BUSY /*3017*/:
                return 4;
            case CallFailCause.CAUSE_NO_USER_RESPONDING /*3018*/:
                return WspTypeDecoder.iCONTENT_TYPE_B_PUSH_DS_SYNCML_NOTI_CE;
            case CallFailCause.CAUSE_USER_ALERTING_NO_ANSWER /*3019*/:
                return 100;
            case CallFailCause.CAUSE_CALL_REJECTED /*3021*/:
                return 207;
            case CallFailCause.CAUSE_NUMBER_CHANGED /*3022*/:
                return BerTlv.BER_PROACTIVE_COMMAND_TAG;
            case CallFailCause.CAUSE_PRE_EMPTION /*3025*/:
                return BerTlv.BER_SMS_PP_DATA_DOWNLOAD_TAG;
            case CallFailCause.CAUSE_NON_SELECTED_USER_CLEARING /*3026*/:
                return 210;
            case CallFailCause.CAUSE_DESTINATION_OUT_OF_ORDER /*3027*/:
                return 211;
            case CallFailCause.CAUSE_INVALID_NUMBER_FORMAT /*3028*/:
                return CommandsInterface.GSM_SMS_FAIL_CAUSE_USIM_APP_TOOLKIT_BUSY;
            case CallFailCause.CAUSE_FACILITY_REJECTED /*3029*/:
                return CommandsInterface.GSM_SMS_FAIL_CAUSE_USIM_DATA_DOWNLOAD_ERROR;
            case CallFailCause.CAUSE_STATUS_ENQUIRY /*3030*/:
                return 214;
            case CallFailCause.CAUSE_NORMAL_UNSPECIFIED /*3031*/:
                return 215;
            case CallFailCause.CAUSE_NO_CIRCUIT_AVAIL /*3034*/:
                return 216;
            case CallFailCause.CAUSE_NETWORK_OUT_OF_ORDER /*3038*/:
                return 217;
            case CallFailCause.CAUSE_TEMPORARY_FAILURE /*3041*/:
                return 218;
            case CallFailCause.CAUSE_SWITCHING_CONGESTION /*3042*/:
                return 219;
            case CallFailCause.CAUSE_ACCESS_INFO_DISCARDED /*3043*/:
                return 220;
            case CallFailCause.CAUSE_CHANNEL_NOT_AVAIL /*3044*/:
                return 221;
            case CallFailCause.CAUSE_RESOURCES_UNAVAILABLE /*3047*/:
                return 222;
            case CallFailCause.CAUSE_QOS_NOT_AVAIL /*3049*/:
                return 223;
            case CallFailCause.CAUSE_FACILITY_NOT_SUBSCRIBED /*3050*/:
                return 224;
            case CallFailCause.CAUSE_INCOMING_CALLS_BARRED_IN_CUG /*3055*/:
                return 225;
            case CallFailCause.CAUSE_BEARER_NOT_ALLOWED /*3057*/:
                return 226;
            case CallFailCause.CAUSE_BEARER_NOT_AVAIL /*3058*/:
                return 227;
            case CallFailCause.CAUSE_SERVICE_NOT_AVAILABLE /*3063*/:
                return 228;
            case CallFailCause.CAUSE_BEARER_NOT_IMPLEMENTED /*3065*/:
                return PduHeaders.RESPONSE_STATUS_ERROR_PERMANENT_CONTENT_NOT_ACCEPTED;
            case CallFailCause.CAUSE_ACM_LIMIT_EXCEEDED /*3068*/:
                return 230;
            case CallFailCause.CAUSE_FACILITY_NOT_IMPLEMENTED /*3069*/:
                return 231;
            case CallFailCause.CAUSE_ONLY_RESTRICTED_DIGITAL /*3070*/:
                return PduHeaders.RESPONSE_STATUS_ERROR_PERMANENT_REPLY_CHARGING_FORWARDING_DENIED;
            case CallFailCause.CAUSE_SERVICE_NOT_IMPLEMENTED /*3079*/:
                return PduHeaders.RESPONSE_STATUS_ERROR_PERMANENT_REPLY_CHARGING_NOT_SUPPORTED;
            case CallFailCause.CAUSE_INVALID_TI_VALUE /*3081*/:
                return PduHeaders.RESPONSE_STATUS_ERROR_PERMANENT_ADDRESS_HIDING_NOT_SUPPORTED;
            case CallFailCause.CAUSE_USER_NOT_IN_CUG /*3087*/:
                return PduHeaders.RESPONSE_STATUS_ERROR_PERMANENT_LACK_OF_PREPAID;
            case CallFailCause.CAUSE_INCOMPATIBLE_DESTINATION /*3088*/:
                return 236;
            case CallFailCause.CAUSE_INVALID_TRANSIT_NETWORK /*3091*/:
                return 237;
            case CallFailCause.CAUSE_SEMANTICAL_INCORRECT_MSG /*3095*/:
                return 238;
            case CallFailCause.CAUSE_MANDATORY_IE_ERROR /*3096*/:
                return 239;
            case CallFailCause.CAUSE_MSG_TYPE_NON_EXIST_OR_NOT_IMPL /*3097*/:
                return 240;
            case CallFailCause.CAUSE_MSG_NOT_COMP_STATE /*3098*/:
                return 241;
            case CallFailCause.CAUSE_IE_NON_EXIST_OR_NOT_IMPL /*3099*/:
                return 242;
            case CallFailCause.CAUSE_INVALID_IE_CONTENTS /*3100*/:
                return 243;
            case CallFailCause.CAUSE_MSG_NOT_COMP_WITH_CALL_STATE /*3101*/:
                return BearerData.RELATIVE_TIME_WEEKS_LIMIT;
            case CallFailCause.CAUSE_RECOVERY_ON_TIMER_EXPIRY /*3102*/:
                return BearerData.RELATIVE_TIME_INDEFINITE;
            case CallFailCause.CAUSE_PROTOCOL_ERROR_UNSPECIFIED /*3111*/:
                return BearerData.RELATIVE_TIME_NOW;
            case CallFailCause.CAUSE_INTERWORKING /*3127*/:
                return BearerData.RELATIVE_TIME_MOBILE_INACTIVE;
            default:
                return disconnectCauseFromCode(causeCode);
        }
    }

    void onRemoteDisconnect(int causeCode) {
        this.mPreciseCause = causeCode;
        onDisconnect(disconnectCauseFromCode(causeCode));
    }

    void onRemoteDisconnect(int causeCode, int callFailCause, int sipError) {
        this.sipError = sipError;
        if (CscFeature.getInstance().getEnableStatus("CscFeature_VoiceCall_EnableDetailCallEndCause")) {
            onDisconnect(disconnectCauseFromCode(causeCode, callFailCause));
            return;
        }
        this.rawCause = callFailCause;
        onRemoteDisconnect(causeCode);
    }

    boolean onDisconnect(int cause) {
        boolean changed = false;
        this.mCause = cause;
        if (!this.mDisconnected) {
            this.mIndex = -1;
            this.mDisconnectTime = System.currentTimeMillis();
            this.mDuration = SystemClock.elapsedRealtime() - this.mConnectTimeReal;
            this.mDisconnected = true;
            Rlog.d(LOG_TAG, "onDisconnect: cause=" + cause);
            this.mOwner.mPhone.notifyDisconnect(this);
            if (CscFeature.getInstance().getEnableStatus("CscFeature_RIL_EnableTotalCallTime")) {
                String imsi = this.mOwner.mPhone.getSubscriberId();
                if (!(TextUtils.isEmpty(imsi) || "001010123456789".equals(imsi) || "999999999999999".equals(imsi) || "520360110000010".equals(imsi) || "512010123456789".equals(imsi))) {
                    updateTotalCallTime();
                }
            }
            if (this.mParent != null) {
                changed = this.mParent.connectionDisconnected(this);
            }
            this.mOrigConnection = null;
        }
        clearPostDialListeners();
        releaseWakeLock();
        return changed;
    }

    boolean update(DriverCall dc) {
        boolean wasHolding;
        boolean changed;
        boolean z = true;
        boolean wasConnectingInOrOut = isConnectingInOrOut();
        if (getState() == Call.State.HOLDING) {
            wasHolding = true;
        } else {
            wasHolding = false;
        }
        GsmCall newParent = parentFromDCState(dc.state);
        if (this.mOrigConnection != null) {
            log("update: mOrigConnection is not null");
        } else {
            log(" mNumberConverted " + this.mNumberConverted);
            if (!(equalsHandlesNulls(this.mAddress, dc.number) || (this.mNumberConverted && equalsHandlesNulls(this.mConvertedNumber, dc.number)))) {
                log("update: phone # changed!");
                this.mAddress = dc.number;
                if (!(!"KTT".equals("") || isIncoming() || TextUtils.isEmpty(this.mDialString) || !this.mDialString.startsWith("#31#") || TextUtils.isEmpty(this.mAddress) || this.mAddress.startsWith("#31#"))) {
                    this.mAddress = "#31#" + this.mAddress;
                }
            }
        }
        if (TextUtils.isEmpty(dc.name)) {
            if (!TextUtils.isEmpty(this.mCnapName)) {
                this.mCnapName = "";
            }
        } else if (!dc.name.equals(this.mCnapName)) {
            this.mCnapName = dc.name;
        }
        log("--dssds----" + this.mCnapName);
        this.mCnapName = null;
        this.mCnapNamePresentation = 1;
        this.mNumberPresentation = dc.numberPresentation;
        if (newParent != this.mParent) {
            if (this.mParent != null) {
                this.mParent.detach(this);
            }
            newParent.attach(this, dc);
            this.mParent = newParent;
            changed = true;
        } else {
            changed = false || this.mParent.update(this, dc);
        }
        if ("VZW-CDMA".equals("")) {
            dc.callDetails.setIsMpty(dc.isMpty);
        }
        setId(dc.id);
        changed |= setCallDetails(dc.callDetails);
        StringBuilder append = new StringBuilder().append("update: parent=").append(this.mParent).append(", hasNewParent=");
        if (newParent == this.mParent) {
            z = false;
        }
        log(append.append(z).append(", wasConnectingInOrOut=").append(wasConnectingInOrOut).append(", wasHolding=").append(wasHolding).append(", isConnectingInOrOut=").append(isConnectingInOrOut()).append(", changed=").append(changed).toString());
        if (wasConnectingInOrOut && !isConnectingInOrOut()) {
            onConnectedInOrOut();
        }
        if (changed && !wasHolding && getState() == Call.State.HOLDING) {
            onStartedHolding();
        }
        log("update() - callDetails: " + this.callDetails);
        return changed;
    }

    void fakeHangupBeforeDial() {
        if (this.mParent != null) {
            this.mParent.detach(this);
        }
    }

    void fakeHoldBeforeDial() {
        if (this.mParent != null) {
            this.mParent.detach(this);
        }
        this.mParent = this.mOwner.mBackgroundCall;
        this.mParent.attachFake(this, Call.State.HOLDING);
        onStartedHolding();
    }

    int getGSMIndex() throws CallStateException {
        if (this.mIndex >= 0) {
            return this.mIndex + 1;
        }
        throw new CallStateException("GSM index not yet assigned");
    }

    void onConnectedInOrOut() {
        this.mConnectTime = System.currentTimeMillis();
        this.mConnectTimeReal = SystemClock.elapsedRealtime();
        this.mDuration = 0;
        log("onConnectedInOrOut: connectTime=" + this.mConnectTime);
        if (!this.mIsIncoming) {
            processNextPostDialChar();
        }
        releaseWakeLock();
    }

    void onStartedHolding() {
        this.mHoldingStartTime = SystemClock.elapsedRealtime();
    }

    private boolean processPostDialChar(char c) {
        if (PhoneNumberUtils.is12Key(c)) {
            this.mOwner.mCi.sendDtmf(c, this.mHandler.obtainMessage(1));
            return true;
        } else if (c == ',') {
            if ("SKT".equals("") || "KTT".equals("") || "LGT".equals("")) {
                this.mPostDialState = PostDialState.PAUSE;
            }
            this.mHandler.sendMessageDelayed(this.mHandler.obtainMessage(2), 3000);
            return true;
        } else if (c == ';') {
            setPostDialState(PostDialState.WAIT);
            return true;
        } else if (c != 'N') {
            return false;
        } else {
            setPostDialState(PostDialState.WILD);
            return true;
        }
    }

    public String getRemainingPostDialString() {
        if (this.mPostDialState == PostDialState.CANCELLED || this.mPostDialState == PostDialState.COMPLETE || this.mPostDialString == null || this.mPostDialString.length() <= this.mNextPostDialChar) {
            return "";
        }
        String subStr = this.mPostDialString.substring(this.mNextPostDialChar);
        if ((!"SKT".equals("") && !"KTT".equals("") && !"LGT".equals("")) || subStr == null) {
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

    protected void finalize() {
        if (this.mPartialWakeLock.isHeld()) {
            Rlog.e(LOG_TAG, "[GSMConn] UNEXPECTED; mPartialWakeLock is held when finalizing.");
        }
        clearPostDialListeners();
        releaseWakeLock();
    }

    private void processNextPostDialChar() {
        if ("KTT".equals("") && CallManager.getInstance().getState() == PhoneConstants.State.RINGING) {
            Rlog.v("GSM", "processNextPostDialChar: KT Ringing return!!!");
        } else if (this.mPostDialState != PostDialState.CANCELLED) {
            char c;
            if (this.mPostDialString == null || this.mPostDialString.length() <= this.mNextPostDialChar) {
                setPostDialState(PostDialState.COMPLETE);
                c = '\u0000';
            } else {
                setPostDialState(PostDialState.STARTED);
                String str = this.mPostDialString;
                int i = this.mNextPostDialChar;
                this.mNextPostDialChar = i + 1;
                c = str.charAt(i);
                if (!processPostDialChar(c)) {
                    this.mHandler.obtainMessage(3).sendToTarget();
                    Rlog.e("GSM", "processNextPostDialChar: c=" + c + " isn't valid!");
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
            if (("SKT".equals("") || "KTT".equals("") || "LGT".equals("")) && this.mPostDialState == PostDialState.PAUSE) {
                this.mPostDialState = PostDialState.STARTED;
            }
        }
    }

    private boolean isConnectingInOrOut() {
        return this.mParent == null || this.mParent == this.mOwner.mRingingCall || this.mParent.mState == Call.State.DIALING || this.mParent.mState == Call.State.ALERTING;
    }

    private GsmCall parentFromDCState(State state) {
        switch (C01041.$SwitchMap$com$android$internal$telephony$DriverCall$State[state.ordinal()]) {
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
        if (this.mPostDialState != PostDialState.STARTED && s == PostDialState.STARTED) {
            acquireWakeLock();
            this.mHandler.sendMessageDelayed(this.mHandler.obtainMessage(4), 60000);
        } else if (this.mPostDialState == PostDialState.STARTED && s != PostDialState.STARTED) {
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

    private void log(String msg) {
        Rlog.d(LOG_TAG, "[GSMConn] " + msg);
    }

    public int getNumberPresentation() {
        return this.mNumberPresentation;
    }

    public UUSInfo getUUSInfo() {
        return this.mUusInfo;
    }

    public String getCdnipNumber() {
        return this.cdnipNumber;
    }

    public int getCWToneSignal() {
        return this.cwToneSignal;
    }

    public int getPreciseDisconnectCause() {
        return this.mPreciseCause;
    }

    public void migrateFrom(Connection c) {
        if (c != null) {
            super.migrateFrom(c);
            this.mUusInfo = c.getUUSInfo();
            setUserData(c.getUserData());
        }
    }

    public Connection getOrigConnection() {
        return this.mOrigConnection;
    }

    public boolean isMultiparty() {
        if (this.mOrigConnection != null) {
            return this.mOrigConnection.isMultiparty();
        }
        return false;
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

    private void updateTotalCallTime() {
        FileNotFoundException e;
        IOException e2;
        InterruptedException e3;
        InputStream inputStream;
        long mTotalCallTime = 0;
        byte[] buffer = new byte[4];
        try {
            File file = new File("/efs/total_call_time");
            File file2;
            if (file == null) {
                try {
                    Rlog.d(LOG_TAG, "NullPointer");
                    file2 = file;
                    return;
                } catch (FileNotFoundException e4) {
                    e = e4;
                    file2 = file;
                    Rlog.e(LOG_TAG, "updateTotalCallTime: [Read] " + e);
                } catch (IOException e5) {
                    e2 = e5;
                    file2 = file;
                    Rlog.e(LOG_TAG, "updateTotalCallTime: [Read] " + e2);
                } catch (InterruptedException e6) {
                    e3 = e6;
                    file2 = file;
                    Rlog.e(LOG_TAG, "updateTotalCallTime: [Create] " + e3);
                }
            }
            if (file.exists()) {
                InputStream in = new BufferedInputStream(new FileInputStream(file));
                try {
                    in.read(buffer, 0, 4);
                    mTotalCallTime = (((0 + ((long) (buffer[0] & 255))) + ((long) ((buffer[1] << 8) & 65280))) + ((long) ((buffer[2] << 16) & 16711680))) + ((long) ((buffer[3] << 24) & -16777216));
                    if (in != null) {
                        in.close();
                    }
                    Rlog.d(LOG_TAG, "updateTotalCallTime: file opened currentCallTime=" + mTotalCallTime);
                    inputStream = in;
                } catch (FileNotFoundException e7) {
                    e = e7;
                    inputStream = in;
                    file2 = file;
                    Rlog.e(LOG_TAG, "updateTotalCallTime: [Read] " + e);
                } catch (IOException e8) {
                    e2 = e8;
                    inputStream = in;
                    file2 = file;
                    Rlog.e(LOG_TAG, "updateTotalCallTime: [Read] " + e2);
                } catch (InterruptedException e9) {
                    e3 = e9;
                    inputStream = in;
                    file2 = file;
                    Rlog.e(LOG_TAG, "updateTotalCallTime: [Create] " + e3);
                }
            }
            file.createNewFile();
            Process process = Runtime.getRuntime().exec("chmod 664 /efs/total_call_time");
            if (process != null) {
                process.waitFor();
            }
            if (mTotalCallTime >= 7200) {
                file2 = file;
                return;
            }
            if (this.mDuration != 0) {
                mTotalCallTime += this.mDuration / 1000;
            }
            if (mTotalCallTime >= 7200) {
                mTotalCallTime = 7200;
            }
            try {
                OutputStream out = new BufferedOutputStream(new FileOutputStream(file));
                OutputStream outputStream;
                try {
                    buffer[0] = (byte) ((int) (255 & mTotalCallTime));
                    buffer[1] = (byte) ((int) ((mTotalCallTime >> 8) & 255));
                    buffer[2] = (byte) ((int) ((mTotalCallTime >> 16) & 255));
                    buffer[3] = (byte) ((int) ((mTotalCallTime >> 24) & 255));
                    out.write(buffer, 0, 4);
                    if (out != null) {
                        out.close();
                    }
                    Rlog.d(LOG_TAG, "updateTotalCallTime: file closed newCallTime=" + mTotalCallTime);
                    outputStream = out;
                } catch (FileNotFoundException e10) {
                    e = e10;
                    outputStream = out;
                    Rlog.e(LOG_TAG, "updateTotalCallTime: [Write] " + e);
                    file2 = file;
                } catch (IOException e11) {
                    e2 = e11;
                    outputStream = out;
                    Rlog.e(LOG_TAG, "updateTotalCallTime: [Write] " + e2);
                    file2 = file;
                }
            } catch (FileNotFoundException e12) {
                e = e12;
                Rlog.e(LOG_TAG, "updateTotalCallTime: [Write] " + e);
                file2 = file;
            } catch (IOException e13) {
                e2 = e13;
                Rlog.e(LOG_TAG, "updateTotalCallTime: [Write] " + e2);
                file2 = file;
            }
            file2 = file;
        } catch (FileNotFoundException e14) {
            e = e14;
            Rlog.e(LOG_TAG, "updateTotalCallTime: [Read] " + e);
        } catch (IOException e15) {
            e2 = e15;
            Rlog.e(LOG_TAG, "updateTotalCallTime: [Read] " + e2);
        } catch (InterruptedException e16) {
            e3 = e16;
            Rlog.e(LOG_TAG, "updateTotalCallTime: [Create] " + e3);
        }
    }

    public int getRawDisconnectCause() {
        return this.rawCause;
    }
}
