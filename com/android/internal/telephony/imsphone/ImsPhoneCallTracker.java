package com.android.internal.telephony.imsphone;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.AsyncResult;
import android.os.Handler;
import android.os.Message;
import android.os.Registrant;
import android.os.RegistrantList;
import android.os.RemoteException;
import android.os.SystemProperties;
import android.preference.PreferenceManager;
import android.telephony.PhoneNumberUtils;
import android.telephony.Rlog;
import com.android.ims.ImsCall;
import com.android.ims.ImsCall.Listener;
import com.android.ims.ImsCallProfile;
import com.android.ims.ImsConnectionStateListener;
import com.android.ims.ImsEcbm;
import com.android.ims.ImsException;
import com.android.ims.ImsManager;
import com.android.ims.ImsReasonInfo;
import com.android.ims.ImsUtInterface;
import com.android.ims.internal.IImsVideoCallProvider;
import com.android.ims.internal.ImsVideoCallProviderWrapper;
import com.android.internal.telephony.Call;
import com.android.internal.telephony.Call.SrvccState;
import com.android.internal.telephony.CallDetails;
import com.android.internal.telephony.CallStateException;
import com.android.internal.telephony.CallTracker;
import com.android.internal.telephony.CommandException;
import com.android.internal.telephony.CommandException.Error;
import com.android.internal.telephony.Connection;
import com.android.internal.telephony.Phone.SuppService;
import com.android.internal.telephony.PhoneBase;
import com.android.internal.telephony.PhoneConstants.State;
import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public final class ImsPhoneCallTracker extends CallTracker {
    private static final boolean DBG = true;
    private static final int EVENT_DIAL_PENDINGMO = 20;
    private static final int EVENT_HANGUP_PENDINGMO = 18;
    private static final int EVENT_RESUME_BACKGROUND = 19;
    static final String LOG_TAG = "ImsPhoneCallTracker";
    static final int MAX_CONNECTIONS = 7;
    static final int MAX_CONNECTIONS_PER_CALL = 5;
    private static final int TIMEOUT_HANGUP_PENDINGMO = 500;
    ImsPhoneCall mBackgroundCall = new ImsPhoneCall(this);
    private int mClirMode = 0;
    private ArrayList<ImsPhoneConnection> mConnections = new ArrayList();
    private boolean mDesiredMute = false;
    ImsPhoneCall mForegroundCall = new ImsPhoneCall(this);
    ImsPhoneCall mHandoverCall = new ImsPhoneCall(this);
    private Listener mImsCallListener = new C01203();
    private ImsConnectionStateListener mImsConnectionStateListener = new C01225();
    private ImsManager mImsManager;
    private Listener mImsUssdListener = new C01214();
    private boolean mIsInEmergencyCall = false;
    private boolean mIsVolteEnabled = false;
    private boolean mIsVtEnabled = false;
    private boolean mOnHoldToneStarted = false;
    private ImsPhoneConnection mPendingMO;
    private Message mPendingUssd = null;
    ImsPhone mPhone;
    private BroadcastReceiver mReceiver = new C01181();
    ImsPhoneCall mRingingCall = new ImsPhoneCall(this);
    private int mServiceId = -1;
    private SrvccState mSrvccState = SrvccState.NONE;
    State mState = State.IDLE;
    private Object mSyncHold = new Object();
    private ImsCall mUssdSession = null;
    private RegistrantList mVoiceCallEndedRegistrants = new RegistrantList();
    private RegistrantList mVoiceCallStartedRegistrants = new RegistrantList();
    private int pendingCallClirMode;
    private boolean pendingCallInEcm = false;
    private int pendingCallVideoState;

    class C01181 extends BroadcastReceiver {
        C01181() {
        }

        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals("com.android.ims.IMS_INCOMING_CALL")) {
                ImsPhoneCallTracker.this.log("onReceive : incoming call intent");
                if (ImsPhoneCallTracker.this.mImsManager != null && ImsPhoneCallTracker.this.mServiceId >= 0) {
                    try {
                        if (intent.getBooleanExtra("android:ussd", false)) {
                            ImsPhoneCallTracker.this.log("onReceive : USSD");
                            ImsPhoneCallTracker.this.mUssdSession = ImsPhoneCallTracker.this.mImsManager.takeCall(ImsPhoneCallTracker.this.mServiceId, intent, ImsPhoneCallTracker.this.mImsUssdListener);
                            if (ImsPhoneCallTracker.this.mUssdSession != null) {
                                ImsPhoneCallTracker.this.mUssdSession.accept(2);
                                return;
                            }
                            return;
                        }
                        ImsCall imsCall = ImsPhoneCallTracker.this.mImsManager.takeCall(ImsPhoneCallTracker.this.mServiceId, intent, ImsPhoneCallTracker.this.mImsCallListener);
                        ImsPhoneConnection conn = new ImsPhoneConnection(ImsPhoneCallTracker.this.mPhone.getContext(), imsCall, ImsPhoneCallTracker.this, ImsPhoneCallTracker.this.mRingingCall);
                        ImsPhoneCallTracker.this.addConnection(conn);
                        IImsVideoCallProvider imsVideoCallProvider = imsCall.getCallSession().getVideoCallProvider();
                        if (imsVideoCallProvider != null) {
                            conn.setVideoProvider(new ImsVideoCallProviderWrapper(imsVideoCallProvider));
                        }
                        if (!(ImsPhoneCallTracker.this.mForegroundCall.getState() == Call.State.IDLE && ImsPhoneCallTracker.this.mBackgroundCall.getState() == Call.State.IDLE)) {
                            conn.update(imsCall, Call.State.WAITING);
                        }
                        ImsPhoneCallTracker.this.mPhone.notifyNewRingingConnection(conn);
                        ImsPhoneCallTracker.this.mPhone.notifyIncomingRing();
                        ImsPhoneCallTracker.this.updatePhoneState();
                        ImsPhoneCallTracker.this.mPhone.notifyPreciseCallStateChanged();
                    } catch (ImsException e) {
                        ImsPhoneCallTracker.this.loge("onReceive : exception " + e);
                    } catch (RemoteException e2) {
                    }
                }
            }
        }
    }

    class C01192 extends Thread {
        C01192() {
        }

        public void run() {
            ImsPhoneCallTracker.this.getImsService();
        }
    }

    class C01203 extends Listener {
        C01203() {
        }

        public void onCallProgressing(ImsCall imsCall) {
            ImsPhoneCallTracker.this.log("onCallProgressing");
            ImsPhoneCallTracker.this.mPendingMO = null;
            ImsPhoneCallTracker.this.processCallStateChange(imsCall, Call.State.ALERTING, 0);
        }

        public void onCallStarted(ImsCall imsCall) {
            ImsPhoneCallTracker.this.log("onCallStarted");
            ImsPhoneCallTracker.this.mPendingMO = null;
            ImsPhoneCallTracker.this.processCallStateChange(imsCall, Call.State.ACTIVE, 0);
        }

        public void onCallStartFailed(ImsCall imsCall, ImsReasonInfo reasonInfo) {
            ImsPhoneCallTracker.this.log("onCallStartFailed reasonCode=" + reasonInfo.getCode());
            if (ImsPhoneCallTracker.this.mPendingMO == null) {
                return;
            }
            if (reasonInfo.getCode() == 146 && ImsPhoneCallTracker.this.mBackgroundCall.getState() == Call.State.IDLE && ImsPhoneCallTracker.this.mRingingCall.getState() == Call.State.IDLE) {
                ImsPhoneCallTracker.this.mForegroundCall.detach(ImsPhoneCallTracker.this.mPendingMO);
                ImsPhoneCallTracker.this.removeConnection(ImsPhoneCallTracker.this.mPendingMO);
                ImsPhoneCallTracker.this.mPendingMO.finalize();
                ImsPhoneCallTracker.this.mPendingMO = null;
                ImsPhoneCallTracker.this.mPhone.initiateSilentRedial();
                return;
            }
            ImsPhoneCallTracker.this.mPendingMO = null;
        }

        public void onCallTerminated(ImsCall imsCall, ImsReasonInfo reasonInfo) {
            ImsPhoneCallTracker.this.log("onCallTerminated reasonCode=" + reasonInfo.getCode());
            Call.State oldState = ImsPhoneCallTracker.this.mForegroundCall.getState();
            int cause = ImsPhoneCallTracker.this.getDisconnectCauseFromReasonInfo(reasonInfo);
            ImsPhoneConnection conn = ImsPhoneCallTracker.this.findConnection(imsCall);
            ImsPhoneCallTracker.this.log("cause = " + cause + " conn = " + conn);
            if (conn != null && conn.isIncoming() && conn.getConnectTime() == 0) {
                cause = cause == 3 ? 16 : 1;
            }
            ImsPhoneCallTracker.this.processCallStateChange(imsCall, Call.State.DISCONNECTED, cause);
            if (reasonInfo.getCode() == 501 && oldState == Call.State.DISCONNECTING && ImsPhoneCallTracker.this.mForegroundCall.getState() == Call.State.DISCONNECTED && ImsPhoneCallTracker.this.mBackgroundCall.getState() == Call.State.HOLDING) {
                ImsPhoneCallTracker.this.sendEmptyMessage(19);
            }
        }

        public void onCallHeld(ImsCall imsCall) {
            ImsPhoneCallTracker.this.log("onCallHeld");
            synchronized (ImsPhoneCallTracker.this.mSyncHold) {
                Call.State oldState = ImsPhoneCallTracker.this.mBackgroundCall.getState();
                ImsPhoneCallTracker.this.processCallStateChange(imsCall, Call.State.HOLDING, 0);
                if (oldState == Call.State.ACTIVE) {
                    if (ImsPhoneCallTracker.this.mForegroundCall.getState() == Call.State.HOLDING || ImsPhoneCallTracker.this.mRingingCall.getState() == Call.State.WAITING) {
                        ImsPhoneCallTracker.this.sendEmptyMessage(19);
                    } else if (ImsPhoneCallTracker.this.mPendingMO != null) {
                        ImsPhoneCallTracker.this.sendEmptyMessage(20);
                    }
                }
            }
        }

        public void onCallHoldFailed(ImsCall imsCall, ImsReasonInfo reasonInfo) {
            ImsPhoneCallTracker.this.log("onCallHoldFailed reasonCode=" + reasonInfo.getCode());
            synchronized (ImsPhoneCallTracker.this.mSyncHold) {
                Call.State bgState = ImsPhoneCallTracker.this.mBackgroundCall.getState();
                if (reasonInfo.getCode() == 148) {
                    if (ImsPhoneCallTracker.this.mPendingMO != null) {
                        ImsPhoneCallTracker.this.sendEmptyMessage(20);
                    }
                } else if (bgState == Call.State.ACTIVE) {
                    ImsPhoneCallTracker.this.mForegroundCall.switchWith(ImsPhoneCallTracker.this.mBackgroundCall);
                    if (ImsPhoneCallTracker.this.mPendingMO != null) {
                        ImsPhoneCallTracker.this.mPendingMO.setDisconnectCause(36);
                        ImsPhoneCallTracker.this.sendEmptyMessageDelayed(18, 500);
                    }
                }
            }
        }

        public void onCallResumed(ImsCall imsCall) {
            ImsPhoneCallTracker.this.log("onCallResumed");
            ImsPhoneCallTracker.this.processCallStateChange(imsCall, Call.State.ACTIVE, 0);
        }

        public void onCallResumeFailed(ImsCall imsCall, ImsReasonInfo reasonInfo) {
        }

        public void onCallResumeReceived(ImsCall imsCall) {
            ImsPhoneCallTracker.this.log("onCallResumeReceived");
            if (ImsPhoneCallTracker.this.mOnHoldToneStarted) {
                ImsPhoneCallTracker.this.mPhone.stopOnHoldTone();
                ImsPhoneCallTracker.this.mOnHoldToneStarted = false;
            }
        }

        public void onCallHoldReceived(ImsCall imsCall) {
            ImsPhoneCallTracker.this.log("onCallHoldReceived");
            ImsPhoneConnection conn = ImsPhoneCallTracker.this.findConnection(imsCall);
            if (conn != null && conn.getState() == Call.State.ACTIVE && !ImsPhoneCallTracker.this.mOnHoldToneStarted && ImsPhoneCall.isLocalTone(imsCall)) {
                ImsPhoneCallTracker.this.mPhone.startOnHoldTone();
                ImsPhoneCallTracker.this.mOnHoldToneStarted = true;
            }
        }

        public void onCallMerged(ImsCall call, ImsCall newCall) {
            ImsPhoneCallTracker.this.log("onCallMerged");
            ImsPhoneCallTracker.this.mForegroundCall.merge(ImsPhoneCallTracker.this.mBackgroundCall, ImsPhoneCallTracker.this.mForegroundCall.getState());
            ImsPhoneCallTracker.this.updatePhoneState();
            ImsPhoneCallTracker.this.mPhone.notifyPreciseCallStateChanged();
        }

        public void onCallMergeFailed(ImsCall call, ImsReasonInfo reasonInfo) {
            ImsPhoneCallTracker.this.log("onCallMergeFailed reasonCode=" + reasonInfo.getCode());
            ImsPhoneCallTracker.this.mPhone.notifySuppServiceFailed(SuppService.CONFERENCE);
        }
    }

    class C01214 extends Listener {
        C01214() {
        }

        public void onCallStarted(ImsCall imsCall) {
            ImsPhoneCallTracker.this.log("mImsUssdListener onCallStarted");
            if (imsCall == ImsPhoneCallTracker.this.mUssdSession && ImsPhoneCallTracker.this.mPendingUssd != null) {
                AsyncResult.forMessage(ImsPhoneCallTracker.this.mPendingUssd);
                ImsPhoneCallTracker.this.mPendingUssd.sendToTarget();
                ImsPhoneCallTracker.this.mPendingUssd = null;
            }
        }

        public void onCallStartFailed(ImsCall imsCall, ImsReasonInfo reasonInfo) {
            ImsPhoneCallTracker.this.log("mImsUssdListener onCallStartFailed reasonCode=" + reasonInfo.getCode());
            onCallTerminated(imsCall, reasonInfo);
        }

        public void onCallTerminated(ImsCall imsCall, ImsReasonInfo reasonInfo) {
            ImsPhoneCallTracker.this.log("mImsUssdListener onCallTerminated reasonCode=" + reasonInfo.getCode());
            if (imsCall == ImsPhoneCallTracker.this.mUssdSession) {
                ImsPhoneCallTracker.this.mUssdSession = null;
                if (ImsPhoneCallTracker.this.mPendingUssd != null) {
                    AsyncResult.forMessage(ImsPhoneCallTracker.this.mPendingUssd, null, new CommandException(Error.GENERIC_FAILURE));
                    ImsPhoneCallTracker.this.mPendingUssd.sendToTarget();
                    ImsPhoneCallTracker.this.mPendingUssd = null;
                }
            }
            imsCall.close();
        }

        public void onCallUssdMessageReceived(ImsCall call, int mode, String ussdMessage) {
            ImsPhoneCallTracker.this.log("mImsUssdListener onCallUssdMessageReceived mode=" + mode);
            int ussdMode = -1;
            switch (mode) {
                case 0:
                    ussdMode = 0;
                    break;
                case 1:
                    ussdMode = 1;
                    break;
            }
            ImsPhoneCallTracker.this.mPhone.onIncomingUSSD(ussdMode, ussdMessage);
        }
    }

    class C01225 extends ImsConnectionStateListener {
        C01225() {
        }

        public void onImsConnected() {
            ImsPhoneCallTracker.this.log("onImsConnected");
            ImsPhoneCallTracker.this.mPhone.setServiceState(0);
        }

        public void onImsDisconnected() {
            ImsPhoneCallTracker.this.log("onImsDisconnected");
            ImsPhoneCallTracker.this.mPhone.setServiceState(1);
        }

        public void onImsResumed() {
            ImsPhoneCallTracker.this.log("onImsResumed");
            ImsPhoneCallTracker.this.mPhone.setServiceState(0);
        }

        public void onImsSuspended() {
            ImsPhoneCallTracker.this.log("onImsSuspended");
            ImsPhoneCallTracker.this.mPhone.setServiceState(1);
        }

        public void onFeatureCapabilityChanged(int serviceClass, int[] enabledFeatures, int[] disabledFeatures) {
            if (serviceClass == 1) {
                if (enabledFeatures[0] == 0) {
                    ImsPhoneCallTracker.this.mIsVolteEnabled = true;
                }
                if (enabledFeatures[1] == 1) {
                    ImsPhoneCallTracker.this.mIsVtEnabled = true;
                }
                if (disabledFeatures[0] == 0) {
                    ImsPhoneCallTracker.this.mIsVolteEnabled = false;
                }
                if (disabledFeatures[1] == 1) {
                    ImsPhoneCallTracker.this.mIsVtEnabled = false;
                }
            }
            ImsPhoneCallTracker.this.log("onFeatureCapabilityChanged, mIsVolteEnabled = " + ImsPhoneCallTracker.this.mIsVolteEnabled + " mIsVtEnabled = " + ImsPhoneCallTracker.this.mIsVtEnabled);
        }
    }

    ImsPhoneCallTracker(ImsPhone phone) {
        this.mPhone = phone;
        IntentFilter intentfilter = new IntentFilter();
        intentfilter.addAction("com.android.ims.IMS_INCOMING_CALL");
        this.mPhone.getContext().registerReceiver(this.mReceiver, intentfilter);
        new C01192().start();
    }

    private PendingIntent createIncomingCallPendingIntent() {
        Intent intent = new Intent("com.android.ims.IMS_INCOMING_CALL");
        intent.addFlags(268435456);
        return PendingIntent.getBroadcast(this.mPhone.getContext(), 0, intent, 134217728);
    }

    private void getImsService() {
        log("getImsService");
        this.mImsManager = ImsManager.getInstance(this.mPhone.getContext(), this.mPhone.getSubId());
        try {
            this.mServiceId = this.mImsManager.open(1, createIncomingCallPendingIntent(), this.mImsConnectionStateListener);
            getEcbmInterface().setEcbmStateListener(this.mPhone.mImsEcbmStateListener);
            if (this.mPhone.isInEcm()) {
                this.mPhone.exitEmergencyCallbackMode();
            }
        } catch (ImsException e) {
            loge("getImsService: " + e);
            this.mImsManager = null;
        }
    }

    public void dispose() {
        log("dispose");
        this.mRingingCall.dispose();
        this.mBackgroundCall.dispose();
        this.mForegroundCall.dispose();
        this.mHandoverCall.dispose();
        clearDisconnected();
        this.mPhone.getContext().unregisterReceiver(this.mReceiver);
    }

    protected void finalize() {
        log("ImsPhoneCallTracker finalized");
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

    Connection dial(String dialString, int videoState) throws CallStateException {
        return dial(dialString, videoState, null);
    }

    Connection dial(String dialString, int videoState, CallDetails callDetails) throws CallStateException {
        return dial(dialString, PreferenceManager.getDefaultSharedPreferences(this.mPhone.getContext()).getInt(PhoneBase.CLIR_KEY, 0), videoState, callDetails);
    }

    synchronized Connection dial(String dialString, int clirMode, int videoState) throws CallStateException {
        return dial(dialString, clirMode, videoState, null);
    }

    synchronized Connection dial(String dialString, int clirMode, int videoState, CallDetails callDetails) throws CallStateException {
        boolean isPhoneInEcmMode = SystemProperties.getBoolean("ril.cdma.inecmmode", false);
        boolean isEmergencyNumber = PhoneNumberUtils.isEmergencyNumber(dialString);
        log("dial clirMode=" + clirMode);
        clearDisconnected();
        if (this.mImsManager == null) {
            throw new CallStateException("service not available");
        } else if (canDial()) {
            if (callDetails == null) {
                callDetails = new CallDetails();
            }
            if (isPhoneInEcmMode && isEmergencyNumber) {
                handleEcmTimer(1);
            }
            boolean holdBeforeDial = false;
            if (this.mForegroundCall.getState() == Call.State.ACTIVE) {
                if (this.mBackgroundCall.getState() != Call.State.IDLE) {
                    throw new CallStateException("cannot dial in current state");
                }
                holdBeforeDial = true;
                switchWaitingOrHoldingAndActive();
            }
            Call.State fgState = Call.State.IDLE;
            Call.State bgState = Call.State.IDLE;
            this.mClirMode = clirMode;
            synchronized (this.mSyncHold) {
                if (holdBeforeDial) {
                    fgState = this.mForegroundCall.getState();
                    bgState = this.mBackgroundCall.getState();
                    if (fgState == Call.State.ACTIVE) {
                        throw new CallStateException("cannot dial in current state");
                    } else if (bgState == Call.State.HOLDING) {
                        holdBeforeDial = false;
                    }
                }
                this.mPendingMO = new ImsPhoneConnection(this.mPhone.getContext(), checkForTestEmergencyNumber(dialString), this, this.mForegroundCall, callDetails);
            }
            addConnection(this.mPendingMO);
            if (!holdBeforeDial) {
                if (!isPhoneInEcmMode || (isPhoneInEcmMode && isEmergencyNumber)) {
                    dialInternal(this.mPendingMO, clirMode, videoState, callDetails);
                } else {
                    try {
                        getEcbmInterface().exitEmergencyCallbackMode();
                        this.mPhone.setOnEcbModeExitResponse(this, 14, null);
                        this.pendingCallClirMode = clirMode;
                        this.pendingCallVideoState = videoState;
                        this.pendingCallInEcm = true;
                    } catch (ImsException e) {
                        e.printStackTrace();
                        throw new CallStateException("service not available");
                    }
                }
            }
            updatePhoneState();
            this.mPhone.notifyPreciseCallStateChanged();
        } else {
            throw new CallStateException("cannot dial in current state");
        }
        return this.mPendingMO;
    }

    private void handleEcmTimer(int action) {
        this.mPhone.handleTimerInEmergencyCallbackMode(action);
        switch (action) {
            case 0:
            case 1:
                return;
            default:
                log("handleEcmTimer, unsupported action " + action);
                return;
        }
    }

    private void dialInternal(ImsPhoneConnection conn, int clirMode, int videoState) {
        dialInternal(conn, clirMode, videoState, null);
    }

    private void dialInternal(ImsPhoneConnection conn, int clirMode, int videoState, CallDetails callDetails) {
        if (conn != null) {
            if (conn.getAddress() == null || conn.getAddress().length() == 0 || conn.getAddress().indexOf(78) >= 0) {
                conn.setDisconnectCause(7);
                sendEmptyMessageDelayed(18, 500);
                return;
            }
            setMute(false);
            int serviceType = PhoneNumberUtils.isEmergencyNumber(conn.getAddress()) ? 2 : 1;
            int callType = ImsCallProfile.getCallTypeFromVideoState(videoState);
            conn.setVideoState(videoState);
            try {
                String[] callees = new String[]{conn.getAddress()};
                ImsCallProfile profile = this.mImsManager.createCallProfile(this.mServiceId, serviceType, callType);
                profile.setCallExtraInt("oir", clirMode);
                ImsCall imsCall = this.mImsManager.makeCall(this.mServiceId, profile, callees, this.mImsCallListener);
                conn.setImsCall(imsCall);
                IImsVideoCallProvider imsVideoCallProvider = imsCall.getCallSession().getVideoCallProvider();
                if (imsVideoCallProvider != null) {
                    conn.setVideoProvider(new ImsVideoCallProviderWrapper(imsVideoCallProvider));
                }
            } catch (ImsException e) {
                loge("dialInternal : " + e);
                conn.setDisconnectCause(36);
                sendEmptyMessageDelayed(18, 500);
            } catch (RemoteException e2) {
            }
        }
    }

    void acceptCall(int videoState) throws CallStateException {
        log("acceptCall");
        if (this.mForegroundCall.getState().isAlive() && this.mBackgroundCall.getState().isAlive()) {
            throw new CallStateException("cannot accept call");
        } else if (this.mRingingCall.getState() == Call.State.WAITING && this.mForegroundCall.getState().isAlive()) {
            setMute(false);
            switchWaitingOrHoldingAndActive();
        } else if (this.mRingingCall.getState().isRinging()) {
            log("acceptCall: incoming...");
            setMute(false);
            try {
                ImsCall imsCall = this.mRingingCall.getImsCall();
                if (imsCall != null) {
                    imsCall.accept(ImsCallProfile.getCallTypeFromVideoState(videoState));
                    return;
                }
                throw new CallStateException("no valid ims call");
            } catch (ImsException e) {
                throw new CallStateException("cannot accept call");
            }
        } else {
            throw new CallStateException("phone not ringing");
        }
    }

    void rejectCall() throws CallStateException {
        log("rejectCall");
        if (this.mRingingCall.getState().isRinging()) {
            hangup(this.mRingingCall);
            return;
        }
        throw new CallStateException("phone not ringing");
    }

    void switchWaitingOrHoldingAndActive() throws CallStateException {
        log("switchWaitingOrHoldingAndActive");
        if (this.mRingingCall.getState() == Call.State.INCOMING) {
            throw new CallStateException("cannot be in the incoming state");
        } else if (this.mForegroundCall.getState() == Call.State.ACTIVE) {
            ImsCall imsCall = this.mForegroundCall.getImsCall();
            if (imsCall == null) {
                throw new CallStateException("no ims call");
            }
            this.mForegroundCall.switchWith(this.mBackgroundCall);
            try {
                imsCall.hold();
            } catch (ImsException e) {
                this.mForegroundCall.switchWith(this.mBackgroundCall);
                throw new CallStateException(e.getMessage());
            }
        } else if (this.mBackgroundCall.getState() == Call.State.HOLDING) {
            resumeWaitingOrHolding();
        }
    }

    void conference() {
        log("conference");
        ImsCall fgImsCall = this.mForegroundCall.getImsCall();
        if (fgImsCall == null) {
            log("conference no foreground ims call");
            return;
        }
        ImsCall bgImsCall = this.mBackgroundCall.getImsCall();
        if (bgImsCall == null) {
            log("conference no background ims call");
            return;
        }
        try {
            fgImsCall.merge(bgImsCall);
        } catch (ImsException e) {
            log("conference " + e.getMessage());
        }
    }

    void explicitCallTransfer() {
    }

    void clearDisconnected() {
        log("clearDisconnected");
        internalClearDisconnected();
        updatePhoneState();
        this.mPhone.notifyPreciseCallStateChanged();
    }

    boolean canConference() {
        return this.mForegroundCall.getState() == Call.State.ACTIVE && this.mBackgroundCall.getState() == Call.State.HOLDING && !this.mBackgroundCall.isFull() && !this.mForegroundCall.isFull();
    }

    boolean canDial() {
        return (this.mPhone.getServiceState().getState() == 3 || this.mPendingMO != null || this.mRingingCall.isRinging() || SystemProperties.get("ro.telephony.disable-call", "false").equals("true") || (this.mForegroundCall.getState().isAlive() && this.mBackgroundCall.getState().isAlive())) ? false : true;
    }

    boolean canTransfer() {
        return this.mForegroundCall.getState() == Call.State.ACTIVE && this.mBackgroundCall.getState() == Call.State.HOLDING;
    }

    private void internalClearDisconnected() {
        this.mRingingCall.clearDisconnected();
        this.mForegroundCall.clearDisconnected();
        this.mBackgroundCall.clearDisconnected();
        this.mHandoverCall.clearDisconnected();
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
        log("updatePhoneState oldState=" + oldState + ", newState=" + this.mState);
        if (this.mState != oldState) {
            this.mPhone.notifyPhoneStateChanged();
        }
    }

    private void handleRadioNotAvailable() {
        pollCallsWhenSafe();
    }

    private void dumpState() {
        int i;
        log("Phone State:" + this.mState);
        log("Ringing call: " + this.mRingingCall.toString());
        List l = this.mRingingCall.getConnections();
        int s = l.size();
        for (i = 0; i < s; i++) {
            log(l.get(i).toString());
        }
        log("Foreground call: " + this.mForegroundCall.toString());
        l = this.mForegroundCall.getConnections();
        s = l.size();
        for (i = 0; i < s; i++) {
            log(l.get(i).toString());
        }
        log("Background call: " + this.mBackgroundCall.toString());
        l = this.mBackgroundCall.getConnections();
        s = l.size();
        for (i = 0; i < s; i++) {
            log(l.get(i).toString());
        }
    }

    void setMute(boolean mute) {
        this.mDesiredMute = mute;
        this.mForegroundCall.setMute(mute);
    }

    boolean getMute() {
        return this.mDesiredMute;
    }

    void sendDtmf(char c) {
        log("sendDtmf");
        ImsCall imscall = this.mForegroundCall.getImsCall();
        if (imscall != null) {
            imscall.sendDtmf(c);
        }
    }

    void hangup(ImsPhoneConnection conn) throws CallStateException {
        log("hangup connection");
        if (conn.getOwner() != this) {
            throw new CallStateException("ImsPhoneConnection " + conn + "does not belong to ImsPhoneCallTracker " + this);
        }
        hangup(conn.getCall());
    }

    void hangup(ImsPhoneCall call) throws CallStateException {
        log("hangup call");
        if (call.getConnections().size() == 0) {
            throw new CallStateException("no connections");
        }
        ImsCall imsCall = call.getImsCall();
        boolean rejectCall = false;
        if (call == this.mRingingCall) {
            log("(ringing) hangup incoming");
            rejectCall = true;
        } else if (call == this.mForegroundCall) {
            if (call.isDialingOrAlerting()) {
                log("(foregnd) hangup dialing or alerting...");
            } else {
                log("(foregnd) hangup foreground");
            }
        } else if (call == this.mBackgroundCall) {
            log("(backgnd) hangup waiting or background");
        } else {
            throw new CallStateException("ImsPhoneCall " + call + "does not belong to ImsPhoneCallTracker " + this);
        }
        call.onHangupLocal();
        if (imsCall != null) {
            if (rejectCall) {
                try {
                    imsCall.reject(504);
                } catch (ImsException e) {
                    throw new CallStateException(e.getMessage());
                }
            }
            imsCall.terminate(501);
        } else if (this.mPendingMO != null && call == this.mForegroundCall) {
            this.mPendingMO.update(null, Call.State.DISCONNECTED);
            this.mPendingMO.onDisconnect();
            removeConnection(this.mPendingMO);
            this.mPendingMO = null;
            updatePhoneState();
            removeMessages(20);
        }
        this.mPhone.notifyPreciseCallStateChanged();
    }

    void resumeWaitingOrHolding() throws CallStateException {
        log("resumeWaitingOrHolding");
        try {
            ImsCall imsCall;
            if (this.mForegroundCall.getState().isAlive()) {
                imsCall = this.mForegroundCall.getImsCall();
                if (imsCall != null) {
                    imsCall.resume();
                }
            } else if (this.mRingingCall.getState() == Call.State.WAITING) {
                imsCall = this.mRingingCall.getImsCall();
                if (imsCall != null) {
                    imsCall.accept(2);
                }
            } else {
                imsCall = this.mBackgroundCall.getImsCall();
                if (imsCall != null) {
                    imsCall.resume();
                }
            }
        } catch (ImsException e) {
            throw new CallStateException(e.getMessage());
        }
    }

    void sendUSSD(String ussdString, Message response) {
        log("sendUSSD");
        try {
            if (this.mUssdSession != null) {
                this.mUssdSession.sendUssd(ussdString);
                AsyncResult.forMessage(response, null, null);
                response.sendToTarget();
                return;
            }
            String[] callees = new String[]{ussdString};
            ImsCallProfile profile = this.mImsManager.createCallProfile(this.mServiceId, 1, 2);
            profile.setCallExtraInt("dialstring", 2);
            this.mUssdSession = this.mImsManager.makeCall(this.mServiceId, profile, callees, this.mImsUssdListener);
        } catch (Throwable e) {
            loge("sendUSSD : " + e);
            this.mPhone.sendErrorResponse(response, e);
        }
    }

    void cancelUSSD() {
        if (this.mUssdSession != null) {
            try {
                this.mUssdSession.terminate(501);
            } catch (ImsException e) {
            }
        }
    }

    private synchronized ImsPhoneConnection findConnection(ImsCall imsCall) {
        ImsPhoneConnection conn;
        Iterator i$ = this.mConnections.iterator();
        while (i$.hasNext()) {
            conn = (ImsPhoneConnection) i$.next();
            if (conn.getImsCall() == imsCall) {
                break;
            }
        }
        conn = null;
        return conn;
    }

    private synchronized void removeConnection(ImsPhoneConnection conn) {
        this.mConnections.remove(conn);
    }

    private synchronized void addConnection(ImsPhoneConnection conn) {
        this.mConnections.add(conn);
    }

    private void processCallStateChange(ImsCall imsCall, Call.State state, int cause) {
        log("processCallStateChange state=" + state + " cause=" + cause);
        if (imsCall != null) {
            ImsPhoneConnection conn = findConnection(imsCall);
            if (conn != null) {
                boolean changed = conn.update(imsCall, state);
                if (state == Call.State.DISCONNECTED) {
                    changed = conn.onDisconnect(cause) || changed;
                    removeConnection(conn);
                }
                if (changed && conn.getCall() != this.mHandoverCall) {
                    updatePhoneState();
                    this.mPhone.notifyPreciseCallStateChanged();
                }
            }
        }
    }

    private int getDisconnectCauseFromReasonInfo(ImsReasonInfo reasonInfo) {
        switch (reasonInfo.getCode()) {
            case 106:
            case 121:
            case 122:
            case 123:
            case 124:
            case 131:
            case 132:
            case 144:
                return 18;
            case 111:
            case 112:
                return 17;
            case 201:
            case 202:
            case 203:
            case 335:
                return 13;
            case 321:
            case 331:
            case 332:
            case 340:
            case 361:
            case 362:
                return 12;
            case 333:
            case 352:
            case 354:
                return 9;
            case 337:
            case 341:
                return 8;
            case 338:
                return 4;
            case 501:
                return 3;
            case 510:
                return 2;
            default:
                return 36;
        }
    }

    ImsUtInterface getUtInterface() throws ImsException {
        if (this.mImsManager != null) {
            return this.mImsManager.getSupplementaryServiceConfiguration(this.mServiceId);
        }
        throw new ImsException("no ims manager", 0);
    }

    void notifySrvccState(SrvccState state) {
        log("notifySrvccState state=" + state);
        this.mSrvccState = state;
        if (this.mSrvccState == SrvccState.COMPLETED) {
            if (this.mForegroundCall.getConnections().size() > 0) {
                this.mHandoverCall.switchWith(this.mForegroundCall);
            } else if (this.mBackgroundCall.getConnections().size() > 0) {
                this.mHandoverCall.switchWith(this.mBackgroundCall);
            }
            ImsPhoneConnection con = this.mHandoverCall.getHandoverConnection();
            if (con != null) {
                con.releaseWakeLock();
            }
        }
    }

    public void handleMessage(Message msg) {
        log("handleMessage what=" + msg.what);
        switch (msg.what) {
            case 14:
                if (this.pendingCallInEcm) {
                    dialInternal(this.mPendingMO, this.pendingCallClirMode, this.pendingCallVideoState, this.mPendingMO.getCallDetails());
                    this.pendingCallInEcm = false;
                }
                this.mPhone.unsetOnEcbModeExitResponse(this);
                return;
            case 18:
                if (this.mPendingMO != null) {
                    this.mPendingMO.onDisconnect();
                    removeConnection(this.mPendingMO);
                    this.mPendingMO = null;
                }
                updatePhoneState();
                this.mPhone.notifyPreciseCallStateChanged();
                return;
            case 19:
                try {
                    resumeWaitingOrHolding();
                    return;
                } catch (CallStateException e) {
                    loge("handleMessage EVENT_RESUME_BACKGROUND exception=" + e);
                    return;
                }
            case 20:
                dialInternal(this.mPendingMO, this.mClirMode, 0, this.mPendingMO.getCallDetails());
                return;
            default:
                return;
        }
    }

    protected void log(String msg) {
        Rlog.d(LOG_TAG, "[ImsPhoneCallTracker] " + msg);
    }

    protected void loge(String msg) {
        Rlog.e(LOG_TAG, "[ImsPhoneCallTracker] " + msg);
    }

    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        pw.println("ImsPhoneCallTracker extends:");
        super.dump(fd, pw, args);
        pw.println(" mVoiceCallEndedRegistrants=" + this.mVoiceCallEndedRegistrants);
        pw.println(" mVoiceCallStartedRegistrants=" + this.mVoiceCallStartedRegistrants);
        pw.println(" mRingingCall=" + this.mRingingCall);
        pw.println(" mForegroundCall=" + this.mForegroundCall);
        pw.println(" mBackgroundCall=" + this.mBackgroundCall);
        pw.println(" mHandoverCall=" + this.mHandoverCall);
        pw.println(" mPendingMO=" + this.mPendingMO);
        pw.println(" mPhone=" + this.mPhone);
        pw.println(" mDesiredMute=" + this.mDesiredMute);
        pw.println(" mState=" + this.mState);
    }

    protected void handlePollCalls(AsyncResult ar) {
    }

    ImsEcbm getEcbmInterface() throws ImsException {
        if (this.mImsManager != null) {
            return this.mImsManager.getEcbmInterface(this.mServiceId);
        }
        throw new ImsException("no ims manager", 0);
    }

    public boolean isInEmergencyCall() {
        return this.mIsInEmergencyCall;
    }

    public boolean isVolteEnabled() {
        return this.mIsVolteEnabled;
    }

    public boolean isVtEnabled() {
        return this.mIsVtEnabled;
    }
}
