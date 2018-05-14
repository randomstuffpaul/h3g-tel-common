package com.android.internal.telephony;

import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncResult;
import android.os.Handler;
import android.os.Message;
import android.os.Registrant;
import android.os.RegistrantList;
import android.sec.enterprise.EnterpriseDeviceManager;
import android.sec.enterprise.PhoneRestrictionPolicy;
import android.telephony.PhoneNumberUtils;
import android.telephony.Rlog;
import android.telephony.TelephonyManager;
import android.widget.Toast;
import com.android.internal.telephony.PhoneConstants.State;
import com.android.internal.telephony.imsphone.ImsPhone;
import com.android.internal.telephony.sip.SipPhone;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public final class CallManager {
    private static final boolean DBG = true;
    private static final int EVENT_CALL_WAITING = 108;
    private static final int EVENT_CDMA_OTA_STATUS_CHANGE = 111;
    private static final int EVENT_DISCONNECT = 100;
    private static final int EVENT_DISPLAY_INFO = 109;
    private static final int EVENT_ECM_TIMER_RESET = 115;
    private static final int EVENT_INCOMING_RING = 104;
    private static final int EVENT_IN_CALL_VOICE_PRIVACY_OFF = 107;
    private static final int EVENT_IN_CALL_VOICE_PRIVACY_ON = 106;
    private static final int EVENT_MMI_COMPLETE = 114;
    private static final int EVENT_MMI_INITIATE = 113;
    private static final int EVENT_MODIFY_CALL_REQUEST = 1000;
    private static final int EVENT_NEW_RINGING_CONNECTION = 102;
    private static final int EVENT_ONHOLD_TONE = 120;
    private static final int EVENT_POST_DIAL_CHARACTER = 119;
    private static final int EVENT_PRECISE_CALL_STATE_CHANGED = 101;
    private static final int EVENT_RESEND_INCALL_MUTE = 112;
    private static final int EVENT_RINGBACK_TONE = 105;
    private static final int EVENT_SERVICE_STATE_CHANGED = 118;
    private static final int EVENT_SIGNAL_INFO = 110;
    private static final int EVENT_SUBSCRIPTION_INFO_READY = 116;
    private static final int EVENT_SUPP_SERVICE_FAILED = 117;
    private static final int EVENT_UNKNOWN_CONNECTION = 103;
    private static final CallManager INSTANCE = new CallManager();
    private static final String LOG_TAG = "CallManager";
    private static final boolean VDBG = false;
    private final ArrayList<Call> mBackgroundCalls;
    protected final RegistrantList mCallModifyRegistrants;
    protected final RegistrantList mCallWaitingRegistrants;
    protected final RegistrantList mCdmaOtaStatusChangeRegistrants;
    private Phone mDefaultPhone;
    protected final RegistrantList mDisconnectRegistrants;
    protected final RegistrantList mDisplayInfoRegistrants;
    protected final RegistrantList mEcmTimerResetRegistrants;
    private final ArrayList<Connection> mEmptyConnections;
    private final ArrayList<Call> mForegroundCalls;
    private final HashMap<Phone, CallManagerHandler> mHandlerMap;
    protected final RegistrantList mInCallVoicePrivacyOffRegistrants;
    protected final RegistrantList mInCallVoicePrivacyOnRegistrants;
    protected final RegistrantList mIncomingRingRegistrants;
    protected final RegistrantList mMmiCompleteRegistrants;
    protected final RegistrantList mMmiInitiateRegistrants;
    protected final RegistrantList mMmiRegistrants;
    protected final RegistrantList mNewRingingConnectionRegistrants;
    protected final RegistrantList mOnHoldToneRegistrants;
    private final ArrayList<Phone> mPhones;
    protected final RegistrantList mPostDialCharacterRegistrants;
    protected final RegistrantList mPreciseCallStateRegistrants;
    protected final RegistrantList mResendIncallMuteRegistrants;
    private PhoneRestrictionPolicy mRestrictionPolicy;
    protected final RegistrantList mRingbackToneRegistrants;
    private final ArrayList<Call> mRingingCalls;
    protected final RegistrantList mServiceStateChangedRegistrants;
    protected final RegistrantList mSignalInfoRegistrants;
    private boolean mSpeedUpAudioForMtCall;
    protected final RegistrantList mSubscriptionInfoReadyRegistrants;
    protected final RegistrantList mSuppServiceFailedRegistrants;
    protected final RegistrantList mUnknownConnectionRegistrants;

    private class CallManagerHandler extends Handler {
        private CallManagerHandler() {
        }

        public void handleMessage(Message msg) {
            switch (msg.what) {
                case 100:
                    CallManager.this.mDisconnectRegistrants.notifyRegistrants((AsyncResult) msg.obj);
                    return;
                case 101:
                    CallManager.this.mPreciseCallStateRegistrants.notifyRegistrants((AsyncResult) msg.obj);
                    boolean sealedState = false;
                    boolean extendedCallInfoState = false;
                    Context context = CallManager.this.getContext();
                    Uri sealedStateUri = Uri.parse("content://com.sec.knox.provider2/KnoxCustomManagerService1");
                    Cursor sealedStateCr = null;
                    if (context != null) {
                        sealedStateCr = context.getContentResolver().query(sealedStateUri, null, "getSealedState", null, null);
                    }
                    if (sealedStateCr != null) {
                        try {
                            sealedStateCr.moveToFirst();
                            sealedState = sealedStateCr.getString(sealedStateCr.getColumnIndex("getSealedState")).equals("true");
                        } finally {
                            sealedStateCr.close();
                        }
                    }
                    Uri extendedCallInfoStateUri = Uri.parse("content://com.sec.knox.provider2/KnoxCustomManagerService2");
                    Cursor extendedCallInfoStateCr = null;
                    if (context != null) {
                        extendedCallInfoStateCr = context.getContentResolver().query(extendedCallInfoStateUri, null, "getExtendedCallInfoState", null, null);
                    }
                    if (extendedCallInfoStateCr != null) {
                        try {
                            extendedCallInfoStateCr.moveToFirst();
                            extendedCallInfoState = extendedCallInfoStateCr.getString(extendedCallInfoStateCr.getColumnIndex("getExtendedCallInfoState")).equals("true");
                        } finally {
                            extendedCallInfoStateCr.close();
                        }
                    }
                    if (sealedState && extendedCallInfoState && context != null) {
                        State phoneState = CallManager.this.getState();
                        Call.State callState = CallManager.this.getActiveFgCall().getState();
                        Intent intent = new Intent("com.sec.action.CALL_STATE_CHANGED");
                        intent.putExtra("com.sec.intent.extra.PHONE_STATE", phoneState.name());
                        intent.putExtra("com.sec.intent.extra.CALL_STATE", callState.name());
                        context.sendBroadcast(intent);
                        return;
                    }
                    return;
                case 102:
                    Connection c = ((AsyncResult) msg.obj).result;
                    long subId = c.getCall().getPhone().getSubId();
                    boolean canReceiveCall = true;
                    boolean isEmergencyCallOnly = false;
                    if (CallManager.this.mRestrictionPolicy != null) {
                        canReceiveCall = CallManager.this.mRestrictionPolicy.canIncomingCall(c.getAddress());
                        isEmergencyCallOnly = CallManager.this.mRestrictionPolicy.getEmergencyCallOnly(true);
                    }
                    if (CallManager.this.getActiveFgCallState(subId).isDialing() || CallManager.this.hasMoreThanOneRingingCall(c.getCall().getPhone().getSubId()) || !canReceiveCall || isEmergencyCallOnly) {
                        try {
                            Rlog.d(CallManager.LOG_TAG, "silently drop incoming call: " + c.getCall());
                            c.getCall().hangup();
                            return;
                        } catch (CallStateException e) {
                            Rlog.w(CallManager.LOG_TAG, "new ringing connection", e);
                            return;
                        }
                    }
                    CallManager.this.mNewRingingConnectionRegistrants.notifyRegistrants((AsyncResult) msg.obj);
                    return;
                case 103:
                    CallManager.this.mUnknownConnectionRegistrants.notifyRegistrants((AsyncResult) msg.obj);
                    return;
                case 104:
                    if (!CallManager.this.hasActiveFgCall()) {
                        CallManager.this.mIncomingRingRegistrants.notifyRegistrants((AsyncResult) msg.obj);
                        return;
                    }
                    return;
                case CallManager.EVENT_RINGBACK_TONE /*105*/:
                    CallManager.this.mRingbackToneRegistrants.notifyRegistrants((AsyncResult) msg.obj);
                    return;
                case 106:
                    CallManager.this.mInCallVoicePrivacyOnRegistrants.notifyRegistrants((AsyncResult) msg.obj);
                    return;
                case CallManager.EVENT_IN_CALL_VOICE_PRIVACY_OFF /*107*/:
                    CallManager.this.mInCallVoicePrivacyOffRegistrants.notifyRegistrants((AsyncResult) msg.obj);
                    return;
                case CallManager.EVENT_CALL_WAITING /*108*/:
                    CallManager.this.mCallWaitingRegistrants.notifyRegistrants((AsyncResult) msg.obj);
                    return;
                case CallManager.EVENT_DISPLAY_INFO /*109*/:
                    CallManager.this.mDisplayInfoRegistrants.notifyRegistrants((AsyncResult) msg.obj);
                    return;
                case 110:
                    CallManager.this.mSignalInfoRegistrants.notifyRegistrants((AsyncResult) msg.obj);
                    return;
                case CallManager.EVENT_CDMA_OTA_STATUS_CHANGE /*111*/:
                    CallManager.this.mCdmaOtaStatusChangeRegistrants.notifyRegistrants((AsyncResult) msg.obj);
                    return;
                case CallManager.EVENT_RESEND_INCALL_MUTE /*112*/:
                    CallManager.this.mResendIncallMuteRegistrants.notifyRegistrants((AsyncResult) msg.obj);
                    return;
                case CallManager.EVENT_MMI_INITIATE /*113*/:
                    CallManager.this.mMmiInitiateRegistrants.notifyRegistrants((AsyncResult) msg.obj);
                    return;
                case 114:
                    CallManager.this.mMmiCompleteRegistrants.notifyRegistrants((AsyncResult) msg.obj);
                    return;
                case 115:
                    CallManager.this.mEcmTimerResetRegistrants.notifyRegistrants((AsyncResult) msg.obj);
                    return;
                case 116:
                    CallManager.this.mSubscriptionInfoReadyRegistrants.notifyRegistrants((AsyncResult) msg.obj);
                    return;
                case CallManager.EVENT_SUPP_SERVICE_FAILED /*117*/:
                    CallManager.this.mSuppServiceFailedRegistrants.notifyRegistrants((AsyncResult) msg.obj);
                    return;
                case CallManager.EVENT_SERVICE_STATE_CHANGED /*118*/:
                    CallManager.this.mServiceStateChangedRegistrants.notifyRegistrants((AsyncResult) msg.obj);
                    return;
                case CallManager.EVENT_POST_DIAL_CHARACTER /*119*/:
                    for (int i = 0; i < CallManager.this.mPostDialCharacterRegistrants.size(); i++) {
                        Message notifyMsg = ((Registrant) CallManager.this.mPostDialCharacterRegistrants.get(i)).messageForRegistrant();
                        notifyMsg.obj = msg.obj;
                        notifyMsg.arg1 = msg.arg1;
                        notifyMsg.sendToTarget();
                    }
                    return;
                case CallManager.EVENT_ONHOLD_TONE /*120*/:
                    CallManager.this.mOnHoldToneRegistrants.notifyRegistrants((AsyncResult) msg.obj);
                    return;
                case 1000:
                    Rlog.d(CallManager.LOG_TAG, "CallModifyRequest received");
                    CallManager.this.notifyConnectionTypeChangeRequest(msg.obj);
                    return;
                default:
                    return;
            }
        }
    }

    private CallManager() {
        this.mEmptyConnections = new ArrayList();
        this.mHandlerMap = new HashMap();
        this.mSpeedUpAudioForMtCall = false;
        this.mPreciseCallStateRegistrants = new RegistrantList();
        this.mNewRingingConnectionRegistrants = new RegistrantList();
        this.mIncomingRingRegistrants = new RegistrantList();
        this.mDisconnectRegistrants = new RegistrantList();
        this.mMmiRegistrants = new RegistrantList();
        this.mUnknownConnectionRegistrants = new RegistrantList();
        this.mRingbackToneRegistrants = new RegistrantList();
        this.mOnHoldToneRegistrants = new RegistrantList();
        this.mInCallVoicePrivacyOnRegistrants = new RegistrantList();
        this.mInCallVoicePrivacyOffRegistrants = new RegistrantList();
        this.mCallWaitingRegistrants = new RegistrantList();
        this.mDisplayInfoRegistrants = new RegistrantList();
        this.mSignalInfoRegistrants = new RegistrantList();
        this.mCdmaOtaStatusChangeRegistrants = new RegistrantList();
        this.mResendIncallMuteRegistrants = new RegistrantList();
        this.mMmiInitiateRegistrants = new RegistrantList();
        this.mMmiCompleteRegistrants = new RegistrantList();
        this.mEcmTimerResetRegistrants = new RegistrantList();
        this.mSubscriptionInfoReadyRegistrants = new RegistrantList();
        this.mSuppServiceFailedRegistrants = new RegistrantList();
        this.mServiceStateChangedRegistrants = new RegistrantList();
        this.mPostDialCharacterRegistrants = new RegistrantList();
        this.mCallModifyRegistrants = new RegistrantList();
        this.mRestrictionPolicy = null;
        this.mPhones = new ArrayList();
        this.mRingingCalls = new ArrayList();
        this.mBackgroundCalls = new ArrayList();
        this.mForegroundCalls = new ArrayList();
        this.mDefaultPhone = null;
        this.mRestrictionPolicy = EnterpriseDeviceManager.getInstance().getPhoneRestrictionPolicy();
    }

    public static CallManager getInstance() {
        return INSTANCE;
    }

    private static Phone getPhoneBase(Phone phone) {
        if (phone instanceof PhoneProxy) {
            return phone.getForegroundCall().getPhone();
        }
        return phone;
    }

    public static boolean isSamePhone(Phone p1, Phone p2) {
        return getPhoneBase(p1) == getPhoneBase(p2);
    }

    public List<Phone> getAllPhones() {
        return Collections.unmodifiableList(this.mPhones);
    }

    private Phone getPhone(long subId) {
        Iterator i$ = this.mPhones.iterator();
        while (i$.hasNext()) {
            Phone phone = (Phone) i$.next();
            if (phone.getSubId() == subId && !(phone instanceof ImsPhone)) {
                return phone;
            }
        }
        return null;
    }

    public State getState() {
        State s = State.IDLE;
        Iterator i$ = this.mPhones.iterator();
        while (i$.hasNext()) {
            Phone phone = (Phone) i$.next();
            if (phone.getState() == State.RINGING) {
                s = State.RINGING;
            } else if (phone.getState() == State.OFFHOOK && s == State.IDLE) {
                s = State.OFFHOOK;
            }
        }
        return s;
    }

    public State getState(long subId) {
        State s = State.IDLE;
        Iterator i$ = this.mPhones.iterator();
        while (i$.hasNext()) {
            Phone phone = (Phone) i$.next();
            if (phone.getSubId() == subId) {
                if (phone.getState() == State.RINGING) {
                    s = State.RINGING;
                } else if (phone.getState() == State.OFFHOOK && s == State.IDLE) {
                    s = State.OFFHOOK;
                }
            }
        }
        return s;
    }

    public int getServiceState() {
        int resultState = 1;
        Iterator i$ = this.mPhones.iterator();
        while (i$.hasNext()) {
            int serviceState = ((Phone) i$.next()).getServiceState().getState();
            if (serviceState == 0) {
                return serviceState;
            }
            if (serviceState == 1) {
                if (resultState == 2 || resultState == 3) {
                    resultState = serviceState;
                }
            } else if (serviceState == 2 && resultState == 3) {
                resultState = serviceState;
            }
        }
        return resultState;
    }

    public int getServiceState(long subId) {
        int resultState = 1;
        Iterator i$ = this.mPhones.iterator();
        while (i$.hasNext()) {
            Phone phone = (Phone) i$.next();
            if (phone.getSubId() == subId) {
                int serviceState = phone.getServiceState().getState();
                if (serviceState == 0) {
                    return serviceState;
                }
                if (serviceState == 1) {
                    if (resultState == 2 || resultState == 3) {
                        resultState = serviceState;
                    }
                } else if (serviceState == 2 && resultState == 3) {
                    resultState = serviceState;
                }
            }
        }
        return resultState;
    }

    public Phone getPhoneInCall() {
        if (!getFirstActiveRingingCall().isIdle()) {
            return getFirstActiveRingingCall().getPhone();
        }
        if (getActiveFgCall().isIdle()) {
            return getFirstActiveBgCall().getPhone();
        }
        return getActiveFgCall().getPhone();
    }

    public Phone getPhoneInCall(long subId) {
        if (!getFirstActiveRingingCall(subId).isIdle()) {
            return getFirstActiveRingingCall(subId).getPhone();
        }
        if (getActiveFgCall(subId).isIdle()) {
            return getFirstActiveBgCall(subId).getPhone();
        }
        return getActiveFgCall(subId).getPhone();
    }

    public boolean registerPhone(Phone phone) {
        Phone basePhone = getPhoneBase(phone);
        if (basePhone == null || this.mPhones.contains(basePhone)) {
            return false;
        }
        Rlog.d(LOG_TAG, "registerPhone(" + phone.getPhoneName() + " " + phone + ")");
        if (this.mPhones.isEmpty()) {
            this.mDefaultPhone = basePhone;
        }
        this.mPhones.add(basePhone);
        this.mRingingCalls.add(basePhone.getRingingCall());
        this.mBackgroundCalls.add(basePhone.getBackgroundCall());
        this.mForegroundCalls.add(basePhone.getForegroundCall());
        registerForPhoneStates(basePhone);
        return true;
    }

    public void unregisterPhone(Phone phone) {
        Phone basePhone = getPhoneBase(phone);
        if (basePhone != null && this.mPhones.contains(basePhone)) {
            Rlog.d(LOG_TAG, "unregisterPhone(" + phone.getPhoneName() + " " + phone + ")");
            Phone vPhone = basePhone.getImsPhone();
            if (vPhone != null) {
                unregisterPhone(vPhone);
            }
            this.mPhones.remove(basePhone);
            this.mRingingCalls.remove(basePhone.getRingingCall());
            this.mBackgroundCalls.remove(basePhone.getBackgroundCall());
            this.mForegroundCalls.remove(basePhone.getForegroundCall());
            unregisterForPhoneStates(basePhone);
            if (basePhone != this.mDefaultPhone) {
                return;
            }
            if (this.mPhones.isEmpty()) {
                this.mDefaultPhone = null;
            } else {
                this.mDefaultPhone = (Phone) this.mPhones.get(0);
            }
        }
    }

    public Phone getDefaultPhone() {
        return this.mDefaultPhone;
    }

    public Phone getFgPhone() {
        return getActiveFgCall().getPhone();
    }

    public Phone getFgPhone(long subId) {
        return getActiveFgCall(subId).getPhone();
    }

    public Phone getBgPhone() {
        return getFirstActiveBgCall().getPhone();
    }

    public Phone getBgPhone(long subId) {
        return getFirstActiveBgCall(subId).getPhone();
    }

    public Phone getRingingPhone() {
        return getFirstActiveRingingCall().getPhone();
    }

    public Phone getRingingPhone(long subId) {
        return getFirstActiveRingingCall(subId).getPhone();
    }

    private Context getContext() {
        Phone defaultPhone = getDefaultPhone();
        return defaultPhone == null ? null : defaultPhone.getContext();
    }

    private void registerForPhoneStates(Phone phone) {
        if (((CallManagerHandler) this.mHandlerMap.get(phone)) != null) {
            Rlog.d(LOG_TAG, "This phone has already been registered.");
            return;
        }
        CallManagerHandler handler = new CallManagerHandler();
        this.mHandlerMap.put(phone, handler);
        phone.registerForPreciseCallStateChanged(handler, 101, null);
        phone.registerForDisconnect(handler, 100, null);
        phone.registerForNewRingingConnection(handler, 102, null);
        phone.registerForUnknownConnection(handler, 103, null);
        phone.registerForIncomingRing(handler, 104, null);
        phone.registerForRingbackTone(handler, EVENT_RINGBACK_TONE, null);
        phone.registerForInCallVoicePrivacyOn(handler, 106, null);
        phone.registerForInCallVoicePrivacyOff(handler, EVENT_IN_CALL_VOICE_PRIVACY_OFF, null);
        phone.registerForDisplayInfo(handler, EVENT_DISPLAY_INFO, null);
        phone.registerForSignalInfo(handler, 110, null);
        phone.registerForResendIncallMute(handler, EVENT_RESEND_INCALL_MUTE, null);
        phone.registerForMmiInitiate(handler, EVENT_MMI_INITIATE, null);
        phone.registerForMmiComplete(handler, 114, null);
        phone.registerForSuppServiceFailed(handler, EVENT_SUPP_SERVICE_FAILED, null);
        phone.registerForServiceStateChanged(handler, EVENT_SERVICE_STATE_CHANGED, null);
        phone.registerForModifyCallRequest(handler, 1000, null);
        if (phone.getPhoneType() == 1 || phone.getPhoneType() == 2 || phone.getPhoneType() == 5) {
            phone.setOnPostDialCharacter(handler, EVENT_POST_DIAL_CHARACTER, null);
        }
        if (phone.getPhoneType() == 2) {
            phone.registerForCdmaOtaStatusChange(handler, EVENT_CDMA_OTA_STATUS_CHANGE, null);
            phone.registerForSubscriptionInfoReady(handler, 116, null);
            phone.registerForCallWaiting(handler, EVENT_CALL_WAITING, null);
            phone.registerForEcmTimerReset(handler, 115, null);
        }
        if (phone.getPhoneType() == 5) {
            phone.registerForOnHoldTone(handler, EVENT_ONHOLD_TONE, null);
        }
    }

    private void unregisterForPhoneStates(Phone phone) {
        CallManagerHandler handler = (CallManagerHandler) this.mHandlerMap.get(phone);
        if (handler != null) {
            Rlog.e(LOG_TAG, "Could not find Phone handler for unregistration");
            return;
        }
        this.mHandlerMap.remove(phone);
        phone.unregisterForPreciseCallStateChanged(handler);
        phone.unregisterForDisconnect(handler);
        phone.unregisterForNewRingingConnection(handler);
        phone.unregisterForUnknownConnection(handler);
        phone.unregisterForIncomingRing(handler);
        phone.unregisterForRingbackTone(handler);
        phone.unregisterForInCallVoicePrivacyOn(handler);
        phone.unregisterForInCallVoicePrivacyOff(handler);
        phone.unregisterForDisplayInfo(handler);
        phone.unregisterForSignalInfo(handler);
        phone.unregisterForResendIncallMute(handler);
        phone.unregisterForMmiInitiate(handler);
        phone.unregisterForMmiComplete(handler);
        phone.unregisterForSuppServiceFailed(handler);
        phone.unregisterForServiceStateChanged(handler);
        phone.unregisterForModifyCallRequest(handler);
        if (phone.getPhoneType() == 1 || phone.getPhoneType() == 2 || phone.getPhoneType() == 5) {
            phone.setOnPostDialCharacter(null, EVENT_POST_DIAL_CHARACTER, null);
        }
        if (phone.getPhoneType() == 2) {
            phone.unregisterForCdmaOtaStatusChange(handler);
            phone.unregisterForSubscriptionInfoReady(handler);
            phone.unregisterForCallWaiting(handler);
            phone.unregisterForEcmTimerReset(handler);
        }
        if (phone.getPhoneType() == 5) {
            phone.unregisterForOnHoldTone(handler);
        }
    }

    public boolean isVolteRegistered(Phone phone) {
        Phone basePhone = getPhoneBase(phone);
        if (basePhone == null) {
            return getDefaultPhone().isVolteRegistered();
        }
        return basePhone.isVolteRegistered();
    }

    public boolean isWfcRegistered(Phone phone) {
        Phone basePhone = getPhoneBase(phone);
        if (basePhone == null) {
            return getDefaultPhone().isWfcRegistered();
        }
        return basePhone.isWfcRegistered();
    }

    public void acceptCall(Call ringingCall) throws CallStateException {
        boolean sameChannel = true;
        Phone ringingPhone = ringingCall.getPhone();
        if (hasActiveFgCall()) {
            Phone activePhone = getActiveFgCall().getPhone();
            boolean hasBgCall;
            if (activePhone.getBackgroundCall().isIdle()) {
                hasBgCall = false;
            } else {
                hasBgCall = true;
            }
            if (activePhone != ringingPhone) {
                sameChannel = false;
            }
            if (sameChannel && hasBgCall) {
                getActiveFgCall().hangup();
            } else if (!sameChannel && !hasBgCall) {
                activePhone.switchHoldingAndActive();
            } else if (!sameChannel && hasBgCall) {
                getActiveFgCall().hangup();
            }
        }
        ringingPhone.acceptCall(0);
    }

    public void rejectCall(Call ringingCall) throws CallStateException {
        ringingCall.getPhone().rejectCall();
    }

    public void switchHoldingAndActive(Call heldCall) throws CallStateException {
        Phone activePhone = null;
        Phone heldPhone = null;
        if (hasActiveFgCall()) {
            activePhone = getActiveFgCall().getPhone();
        }
        if (heldCall != null) {
            heldPhone = heldCall.getPhone();
        }
        if (activePhone != null) {
            activePhone.switchHoldingAndActive();
        }
        if (heldPhone != null && heldPhone != activePhone) {
            heldPhone.switchHoldingAndActive();
        }
    }

    public void hangupForegroundResumeBackground(Call heldCall) throws CallStateException {
        if (hasActiveFgCall()) {
            Phone foregroundPhone = getFgPhone();
            if (heldCall == null) {
                return;
            }
            if (foregroundPhone == heldCall.getPhone()) {
                getActiveFgCall().hangup();
                return;
            }
            getActiveFgCall().hangup();
            switchHoldingAndActive(heldCall);
        }
    }

    public boolean canConference(Call heldCall) {
        Phone activePhone = null;
        Phone heldPhone = null;
        if (hasActiveFgCall()) {
            activePhone = getActiveFgCall().getPhone();
        }
        if (heldCall != null) {
            heldPhone = heldCall.getPhone();
        }
        return heldPhone.getClass().equals(activePhone.getClass());
    }

    public boolean canConference(Call heldCall, long subId) {
        Phone activePhone = null;
        Phone heldPhone = null;
        if (hasActiveFgCall(subId)) {
            activePhone = getActiveFgCall(subId).getPhone();
        }
        if (heldCall != null) {
            heldPhone = heldCall.getPhone();
        }
        return heldPhone.getClass().equals(activePhone.getClass());
    }

    public void conference(Call heldCall) throws CallStateException {
        Phone fgPhone = getFgPhone(heldCall.getPhone().getSubId());
        if (fgPhone == null) {
            Rlog.d(LOG_TAG, "conference: fgPhone=null");
        } else if (fgPhone instanceof SipPhone) {
            ((SipPhone) fgPhone).conference(heldCall);
        } else if (canConference(heldCall)) {
            fgPhone.conference();
        } else {
            throw new CallStateException("Can't conference foreground and selected background call");
        }
    }

    public Connection dial(Phone phone, String dialString, int videoState) throws CallStateException {
        return dial(phone, dialString, videoState, 0, 1, null);
    }

    public Connection dial(Phone phone, String dialString, int videoState, int callType, int callDomain, String[] extras) throws CallStateException {
        Phone basePhone = getPhoneBase(phone);
        long subId = phone.getSubId();
        boolean canMakeCall = true;
        boolean isEmergencyCallOnly = false;
        boolean isEmergencyNumber = PhoneNumberUtils.isEmergencyNumber(phone.getSubId(), dialString);
        if (!(this.mRestrictionPolicy == null || isEmergencyNumber)) {
            canMakeCall = this.mRestrictionPolicy.canOutgoingCall(dialString);
            isEmergencyCallOnly = this.mRestrictionPolicy.getEmergencyCallOnly(true);
        }
        if (!isEmergencyNumber) {
            Context context = phone.getContext();
            if (!canMakeCall) {
                if (context != null) {
                    Toast.makeText(context, 17039543, 1).show();
                }
                throw new CallStateException("Admin did not allow dialing this number");
            } else if (isEmergencyCallOnly) {
                if (context != null) {
                    Toast.makeText(context, 17039543, 1).show();
                }
                throw new CallStateException("Administrator blocked call on this device");
            }
        }
        if (canDial(phone)) {
            if (hasActiveFgCall(subId)) {
                Phone activePhone = getActiveFgCall(subId).getPhone();
                boolean hasBgCall = !activePhone.getBackgroundCall().isIdle();
                Rlog.d(LOG_TAG, "hasBgCall: " + hasBgCall + " sameChannel:" + (activePhone == basePhone));
                Phone vPhone = basePhone.getImsPhone();
                if (activePhone != basePhone && (vPhone == null || vPhone != activePhone)) {
                    if (hasBgCall) {
                        Rlog.d(LOG_TAG, "Hangup");
                        getActiveFgCall(subId).hangup();
                    } else {
                        Rlog.d(LOG_TAG, "Switch");
                        activePhone.switchHoldingAndActive();
                    }
                }
            }
            return basePhone.dial(dialString, videoState, callType, callDomain, extras);
        } else if (basePhone.handleInCallMmiCommands(PhoneNumberUtils.stripSeparators(dialString))) {
            return null;
        } else {
            throw new CallStateException("cannot dial in current state");
        }
    }

    public Connection dial(Phone phone, String dialString, UUSInfo uusInfo, int videoState) throws CallStateException {
        return phone.dial(dialString, uusInfo, videoState);
    }

    public Connection dial(Phone phone, String dialString, UUSInfo uusInfo, int videoState, int callType, int callDomain, String[] extras) throws CallStateException {
        return phone.dial(dialString, uusInfo, videoState, callType, callDomain, extras);
    }

    public void clearDisconnected() {
        Iterator i$ = this.mPhones.iterator();
        while (i$.hasNext()) {
            ((Phone) i$.next()).clearDisconnected();
        }
    }

    public void clearDisconnected(long subId) {
        Iterator i$ = this.mPhones.iterator();
        while (i$.hasNext()) {
            Phone phone = (Phone) i$.next();
            if (phone.getSubId() == subId) {
                phone.clearDisconnected();
            }
        }
    }

    private boolean canDial(Phone phone) {
        boolean result;
        int serviceState = phone.getServiceState().getState();
        long subId = phone.getSubId();
        boolean hasRingingCall = hasActiveRingingCall();
        Call.State fgCallState = getActiveFgCallState(subId);
        if ((serviceState != 3 || isVolteRegistered(phone) || isWfcRegistered(phone)) && !hasRingingCall && (fgCallState == Call.State.ACTIVE || fgCallState == Call.State.IDLE || fgCallState == Call.State.DISCONNECTED || fgCallState == Call.State.ALERTING)) {
            result = true;
        } else {
            result = false;
        }
        if (!result) {
            Rlog.d(LOG_TAG, "canDial serviceState=" + serviceState + " hasRingingCall=" + hasRingingCall + " fgCallState=" + fgCallState);
        }
        return result;
    }

    public boolean canTransfer(Call heldCall) {
        Phone activePhone = null;
        Phone heldPhone = null;
        if (hasActiveFgCall()) {
            activePhone = getActiveFgCall().getPhone();
        }
        if (heldCall != null) {
            heldPhone = heldCall.getPhone();
        }
        return heldPhone == activePhone && activePhone.canTransfer();
    }

    public boolean canTransfer(Call heldCall, long subId) {
        Phone activePhone = null;
        Phone heldPhone = null;
        if (hasActiveFgCall(subId)) {
            activePhone = getActiveFgCall(subId).getPhone();
        }
        if (heldCall != null) {
            heldPhone = heldCall.getPhone();
        }
        return heldPhone == activePhone && activePhone.canTransfer();
    }

    public void explicitCallTransfer(Call heldCall) throws CallStateException {
        if (canTransfer(heldCall)) {
            heldCall.getPhone().explicitCallTransfer();
        }
    }

    public List<? extends MmiCode> getPendingMmiCodes(Phone phone) {
        Rlog.e(LOG_TAG, "getPendingMmiCodes not implemented");
        return null;
    }

    public boolean sendUssdResponse(Phone phone, String ussdMessge) {
        Rlog.e(LOG_TAG, "sendUssdResponse not implemented");
        return false;
    }

    public void setMute(boolean muted) {
        if (hasActiveFgCall()) {
            getActiveFgCall().getPhone().setMute(muted);
        }
    }

    public boolean getMute() {
        if (hasActiveFgCall()) {
            return getActiveFgCall().getPhone().getMute();
        }
        if (hasActiveBgCall()) {
            return getFirstActiveBgCall().getPhone().getMute();
        }
        return false;
    }

    public void setEchoSuppressionEnabled() {
        if (hasActiveFgCall()) {
            getActiveFgCall().getPhone().setEchoSuppressionEnabled();
        }
    }

    public boolean sendDtmf(char c) {
        if (!hasActiveFgCall()) {
            return false;
        }
        getActiveFgCall().getPhone().sendDtmf(c);
        return true;
    }

    public boolean startDtmf(char c) {
        if (!hasActiveFgCall()) {
            return false;
        }
        getActiveFgCall().getPhone().startDtmf(c);
        return true;
    }

    public void stopDtmf() {
        if (hasActiveFgCall()) {
            getFgPhone().stopDtmf();
        }
    }

    public boolean sendBurstDtmf(String dtmfString, int on, int off, Message onComplete) {
        if (!hasActiveFgCall()) {
            return false;
        }
        getActiveFgCall().getPhone().sendBurstDtmf(dtmfString, on, off, onComplete);
        return true;
    }

    public void registerForDisconnect(Handler h, int what, Object obj) {
        this.mDisconnectRegistrants.addUnique(h, what, obj);
    }

    public void unregisterForDisconnect(Handler h) {
        this.mDisconnectRegistrants.remove(h);
    }

    public void registerForPreciseCallStateChanged(Handler h, int what, Object obj) {
        this.mPreciseCallStateRegistrants.addUnique(h, what, obj);
    }

    public void unregisterForPreciseCallStateChanged(Handler h) {
        this.mPreciseCallStateRegistrants.remove(h);
    }

    public void registerForUnknownConnection(Handler h, int what, Object obj) {
        this.mUnknownConnectionRegistrants.addUnique(h, what, obj);
    }

    public void unregisterForUnknownConnection(Handler h) {
        this.mUnknownConnectionRegistrants.remove(h);
    }

    public void registerForNewRingingConnection(Handler h, int what, Object obj) {
        this.mNewRingingConnectionRegistrants.addUnique(h, what, obj);
    }

    public void unregisterForNewRingingConnection(Handler h) {
        this.mNewRingingConnectionRegistrants.remove(h);
    }

    public void registerForIncomingRing(Handler h, int what, Object obj) {
        this.mIncomingRingRegistrants.addUnique(h, what, obj);
    }

    public void unregisterForIncomingRing(Handler h) {
        this.mIncomingRingRegistrants.remove(h);
    }

    public void registerForRingbackTone(Handler h, int what, Object obj) {
        this.mRingbackToneRegistrants.addUnique(h, what, obj);
    }

    public void unregisterForRingbackTone(Handler h) {
        this.mRingbackToneRegistrants.remove(h);
    }

    public void registerForOnHoldTone(Handler h, int what, Object obj) {
        this.mOnHoldToneRegistrants.addUnique(h, what, obj);
    }

    public void unregisterForOnHoldTone(Handler h) {
        this.mOnHoldToneRegistrants.remove(h);
    }

    public void registerForResendIncallMute(Handler h, int what, Object obj) {
        this.mResendIncallMuteRegistrants.addUnique(h, what, obj);
    }

    public void unregisterForResendIncallMute(Handler h) {
        this.mResendIncallMuteRegistrants.remove(h);
    }

    public void registerForMmiInitiate(Handler h, int what, Object obj) {
        this.mMmiInitiateRegistrants.addUnique(h, what, obj);
    }

    public void unregisterForMmiInitiate(Handler h) {
        this.mMmiInitiateRegistrants.remove(h);
    }

    public void registerForMmiComplete(Handler h, int what, Object obj) {
        this.mMmiCompleteRegistrants.addUnique(h, what, obj);
    }

    public void unregisterForMmiComplete(Handler h) {
        this.mMmiCompleteRegistrants.remove(h);
    }

    public void registerForEcmTimerReset(Handler h, int what, Object obj) {
        this.mEcmTimerResetRegistrants.addUnique(h, what, obj);
    }

    public void unregisterForEcmTimerReset(Handler h) {
        this.mEcmTimerResetRegistrants.remove(h);
    }

    public void registerForServiceStateChanged(Handler h, int what, Object obj) {
        this.mServiceStateChangedRegistrants.addUnique(h, what, obj);
    }

    public void unregisterForServiceStateChanged(Handler h) {
        this.mServiceStateChangedRegistrants.remove(h);
    }

    public void registerForSuppServiceFailed(Handler h, int what, Object obj) {
        this.mSuppServiceFailedRegistrants.addUnique(h, what, obj);
    }

    public void unregisterForSuppServiceFailed(Handler h) {
        this.mSuppServiceFailedRegistrants.remove(h);
    }

    public void registerForInCallVoicePrivacyOn(Handler h, int what, Object obj) {
        this.mInCallVoicePrivacyOnRegistrants.addUnique(h, what, obj);
    }

    public void unregisterForInCallVoicePrivacyOn(Handler h) {
        this.mInCallVoicePrivacyOnRegistrants.remove(h);
    }

    public void registerForInCallVoicePrivacyOff(Handler h, int what, Object obj) {
        this.mInCallVoicePrivacyOffRegistrants.addUnique(h, what, obj);
    }

    public void unregisterForInCallVoicePrivacyOff(Handler h) {
        this.mInCallVoicePrivacyOffRegistrants.remove(h);
    }

    public void registerForCallWaiting(Handler h, int what, Object obj) {
        this.mCallWaitingRegistrants.addUnique(h, what, obj);
    }

    public void unregisterForCallWaiting(Handler h) {
        this.mCallWaitingRegistrants.remove(h);
    }

    public void registerForSignalInfo(Handler h, int what, Object obj) {
        this.mSignalInfoRegistrants.addUnique(h, what, obj);
    }

    public void unregisterForSignalInfo(Handler h) {
        this.mSignalInfoRegistrants.remove(h);
    }

    public void registerForDisplayInfo(Handler h, int what, Object obj) {
        this.mDisplayInfoRegistrants.addUnique(h, what, obj);
    }

    public void unregisterForDisplayInfo(Handler h) {
        this.mDisplayInfoRegistrants.remove(h);
    }

    public void registerForCdmaOtaStatusChange(Handler h, int what, Object obj) {
        this.mCdmaOtaStatusChangeRegistrants.addUnique(h, what, obj);
    }

    public void unregisterForCdmaOtaStatusChange(Handler h) {
        this.mCdmaOtaStatusChangeRegistrants.remove(h);
    }

    public void registerForSubscriptionInfoReady(Handler h, int what, Object obj) {
        this.mSubscriptionInfoReadyRegistrants.addUnique(h, what, obj);
    }

    public void unregisterForSubscriptionInfoReady(Handler h) {
        this.mSubscriptionInfoReadyRegistrants.remove(h);
    }

    public void registerForPostDialCharacter(Handler h, int what, Object obj) {
        this.mPostDialCharacterRegistrants.addUnique(h, what, obj);
    }

    public void unregisterForPostDialCharacter(Handler h) {
        this.mPostDialCharacterRegistrants.remove(h);
    }

    public void registerForConnectionTypeChangeRequest(Handler h, int what, Object obj) {
        this.mCallModifyRegistrants.addUnique(h, what, obj);
    }

    public void unregisterForConnectionTypeChangeRequest(Handler h) {
        this.mCallModifyRegistrants.remove(h);
    }

    private void notifyConnectionTypeChangeRequest(AsyncResult ar) {
        this.mCallModifyRegistrants.notifyRegistrants(ar);
    }

    public void changeConnectionType(Message result, Connection conn, int newCallType) throws CallStateException {
        changeConnectionType(result, conn, newCallType, null);
    }

    public void changeConnectionType(Message result, Connection conn, int newCallType, Map<String, String> newExtras) throws CallStateException {
        getDefaultPhone().changeConnectionType(result, conn, newCallType, newExtras);
    }

    public void acceptConnectionTypeChange(Connection conn, Map<String, String> newExtras) throws CallStateException {
        getDefaultPhone().acceptConnectionTypeChange(conn, newExtras);
    }

    public void rejectConnectionTypeChange(Connection conn) throws CallStateException {
        getDefaultPhone().rejectConnectionTypeChange(conn);
    }

    public int getProposedConnectionType(Connection conn) throws CallStateException {
        return getDefaultPhone().getProposedConnectionType(conn);
    }

    public List<Call> getRingingCalls() {
        return Collections.unmodifiableList(this.mRingingCalls);
    }

    public List<Call> getForegroundCalls() {
        return Collections.unmodifiableList(this.mForegroundCalls);
    }

    public List<Call> getBackgroundCalls() {
        return Collections.unmodifiableList(this.mBackgroundCalls);
    }

    public boolean hasActiveFgCall() {
        return getFirstActiveCall(this.mForegroundCalls) != null;
    }

    public boolean hasActiveFgCall(long subId) {
        return getFirstActiveCall(this.mForegroundCalls, subId) != null;
    }

    public boolean hasActiveBgCall() {
        return getFirstActiveCall(this.mBackgroundCalls) != null;
    }

    public boolean hasActiveBgCall(long subId) {
        return getFirstActiveCall(this.mBackgroundCalls, subId) != null;
    }

    public boolean hasActiveRingingCall() {
        return getFirstActiveCall(this.mRingingCalls) != null;
    }

    public boolean hasActiveRingingCall(long subId) {
        return getFirstActiveCall(this.mRingingCalls, subId) != null;
    }

    public Call getActiveFgCall() {
        Call call = getFirstNonIdleCall(this.mForegroundCalls);
        if (call == null) {
            return this.mDefaultPhone == null ? null : this.mDefaultPhone.getForegroundCall();
        } else {
            return call;
        }
    }

    public Call getActiveFgCall(long subId) {
        Call call = getFirstNonIdleCall(this.mForegroundCalls, subId);
        if (call != null) {
            return call;
        }
        Phone phone = getPhone(subId);
        return phone == null ? null : phone.getForegroundCall();
    }

    private Call getFirstNonIdleCall(List<Call> calls) {
        Call result = null;
        for (Call call : calls) {
            if (!call.isIdle()) {
                return call;
            }
            if (call.getState() != Call.State.IDLE && result == null) {
                result = call;
            }
        }
        return result;
    }

    private Call getFirstNonIdleCall(List<Call> calls, long subId) {
        Call result = null;
        for (Call call : calls) {
            if (call.getPhone().getSubId() == subId || (call.getPhone() instanceof SipPhone)) {
                if (!call.isIdle()) {
                    return call;
                }
                if (call.getState() != Call.State.IDLE && result == null) {
                    result = call;
                }
            }
        }
        return result;
    }

    public Call getFirstActiveBgCall() {
        Call call = getFirstNonIdleCall(this.mBackgroundCalls);
        if (call == null) {
            return this.mDefaultPhone == null ? null : this.mDefaultPhone.getBackgroundCall();
        } else {
            return call;
        }
    }

    public Call getFirstActiveBgCall(long subId) {
        Phone phone = getPhone(subId);
        if (hasMoreThanOneHoldingCall(subId)) {
            return phone.getBackgroundCall();
        }
        Call call = getFirstNonIdleCall(this.mBackgroundCalls, subId);
        if (call != null) {
            return call;
        }
        return phone == null ? null : phone.getBackgroundCall();
    }

    public Call getFirstActiveRingingCall() {
        Call call = getFirstNonIdleCall(this.mRingingCalls);
        if (call == null) {
            return this.mDefaultPhone == null ? null : this.mDefaultPhone.getRingingCall();
        } else {
            return call;
        }
    }

    public Call getFirstActiveRingingCall(long subId) {
        Phone phone = getPhone(subId);
        Call call = getFirstNonIdleCall(this.mRingingCalls, subId);
        if (call == null) {
            return phone == null ? null : phone.getRingingCall();
        } else {
            return call;
        }
    }

    public Call.State getActiveFgCallState() {
        Call fgCall = getActiveFgCall();
        if (fgCall != null) {
            return fgCall.getState();
        }
        return Call.State.IDLE;
    }

    public Call.State getActiveFgCallState(long subId) {
        Call fgCall = getActiveFgCall(subId);
        if (fgCall != null) {
            return fgCall.getState();
        }
        return Call.State.IDLE;
    }

    public List<Connection> getFgCallConnections() {
        Call fgCall = getActiveFgCall();
        if (fgCall != null) {
            return fgCall.getConnections();
        }
        return this.mEmptyConnections;
    }

    public List<Connection> getFgCallConnections(long subId) {
        Call fgCall = getActiveFgCall(subId);
        if (fgCall != null) {
            return fgCall.getConnections();
        }
        return this.mEmptyConnections;
    }

    public List<Connection> getBgCallConnections() {
        Call bgCall = getFirstActiveBgCall();
        if (bgCall != null) {
            return bgCall.getConnections();
        }
        return this.mEmptyConnections;
    }

    public List<Connection> getBgCallConnections(long subId) {
        Call bgCall = getFirstActiveBgCall(subId);
        if (bgCall != null) {
            return bgCall.getConnections();
        }
        return this.mEmptyConnections;
    }

    public Connection getFgCallLatestConnection() {
        Call fgCall = getActiveFgCall();
        if (fgCall != null) {
            return fgCall.getLatestConnection();
        }
        return null;
    }

    public Connection getFgCallLatestConnection(long subId) {
        Call fgCall = getActiveFgCall(subId);
        if (fgCall != null) {
            return fgCall.getLatestConnection();
        }
        return null;
    }

    public boolean hasDisconnectedFgCall() {
        return getFirstCallOfState(this.mForegroundCalls, Call.State.DISCONNECTED) != null;
    }

    public boolean hasDisconnectedFgCall(long subId) {
        return getFirstCallOfState(this.mForegroundCalls, Call.State.DISCONNECTED, subId) != null;
    }

    public boolean hasDisconnectedBgCall() {
        return getFirstCallOfState(this.mBackgroundCalls, Call.State.DISCONNECTED) != null;
    }

    public boolean hasDisconnectedBgCall(long subId) {
        return getFirstCallOfState(this.mBackgroundCalls, Call.State.DISCONNECTED, subId) != null;
    }

    private Call getFirstActiveCall(ArrayList<Call> calls) {
        Iterator i$ = calls.iterator();
        while (i$.hasNext()) {
            Call call = (Call) i$.next();
            if (!call.isIdle()) {
                return call;
            }
        }
        return null;
    }

    private Call getFirstActiveCall(ArrayList<Call> calls, long subId) {
        Iterator i$ = calls.iterator();
        while (i$.hasNext()) {
            Call call = (Call) i$.next();
            if (!call.isIdle() && (call.getPhone().getSubId() == subId || (call.getPhone() instanceof SipPhone))) {
                return call;
            }
        }
        return null;
    }

    private Call getFirstCallOfState(ArrayList<Call> calls, Call.State state) {
        Iterator i$ = calls.iterator();
        while (i$.hasNext()) {
            Call call = (Call) i$.next();
            if (call.getState() == state) {
                return call;
            }
        }
        return null;
    }

    private Call getFirstCallOfState(ArrayList<Call> calls, Call.State state, long subId) {
        Iterator i$ = calls.iterator();
        while (i$.hasNext()) {
            Call call = (Call) i$.next();
            if (call.getState() == state || call.getPhone().getSubId() == subId) {
                return call;
            }
            if (call.getPhone() instanceof SipPhone) {
                return call;
            }
        }
        return null;
    }

    private boolean hasMoreThanOneRingingCall() {
        int count = 0;
        Iterator i$ = this.mRingingCalls.iterator();
        while (i$.hasNext()) {
            if (((Call) i$.next()).getState().isRinging()) {
                count++;
                if (count > 1) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean hasMoreThanOneRingingCall(long subId) {
        int count = 0;
        Iterator i$ = this.mRingingCalls.iterator();
        while (i$.hasNext()) {
            Call call = (Call) i$.next();
            if (call.getState().isRinging() && (call.getPhone().getSubId() == subId || (call.getPhone() instanceof SipPhone))) {
                count++;
                if (count > 1) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean hasMoreThanOneHoldingCall(long subId) {
        int count = 0;
        Iterator i$ = this.mBackgroundCalls.iterator();
        while (i$.hasNext()) {
            Call call = (Call) i$.next();
            if (call.getState() == Call.State.HOLDING && (call.getPhone().getSubId() == subId || (call.getPhone() instanceof SipPhone))) {
                count++;
                if (count > 1) {
                    return true;
                }
            }
        }
        return false;
    }

    public String toString() {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < TelephonyManager.getDefault().getPhoneCount(); i++) {
            b.append("CallManager {");
            b.append("\nstate = " + getState((long) i));
            Call call = getActiveFgCall((long) i);
            b.append("\n- Foreground: " + getActiveFgCallState((long) i));
            b.append(" from " + call.getPhone());
            b.append("\n  Conn: ").append(getFgCallConnections((long) i));
            call = getFirstActiveBgCall((long) i);
            b.append("\n- Background: " + call.getState());
            b.append(" from " + call.getPhone());
            b.append("\n  Conn: ").append(getBgCallConnections((long) i));
            call = getFirstActiveRingingCall((long) i);
            b.append("\n- Ringing: " + call.getState());
            b.append(" from " + call.getPhone());
        }
        for (Phone phone : getAllPhones()) {
            if (phone != null) {
                b.append("\nPhone: " + phone + ", name = " + phone.getPhoneName() + ", state = " + phone.getState());
                b.append("\n- Foreground: ").append(phone.getForegroundCall());
                b.append(" Background: ").append(phone.getBackgroundCall());
                b.append(" Ringing: ").append(phone.getRingingCall());
            }
        }
        b.append("\n}");
        return b.toString();
    }
}
