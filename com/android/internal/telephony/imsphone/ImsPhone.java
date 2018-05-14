package com.android.internal.telephony.imsphone;

import android.app.ActivityManagerNative;
import android.content.Context;
import android.content.Intent;
import android.net.LinkProperties;
import android.os.AsyncResult;
import android.os.Handler;
import android.os.Message;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.os.Registrant;
import android.os.RegistrantList;
import android.os.SystemProperties;
import android.telephony.CellLocation;
import android.telephony.PhoneNumberUtils;
import android.telephony.Rlog;
import android.telephony.ServiceState;
import android.telephony.SignalStrength;
import android.telephony.SubscriptionManager;
import android.text.TextUtils;
import android.util.Log;
import com.android.ims.ImsCallForwardInfo;
import com.android.ims.ImsEcbmStateListener;
import com.android.ims.ImsException;
import com.android.ims.ImsReasonInfo;
import com.android.ims.ImsSsInfo;
import com.android.internal.telephony.Call.SrvccState;
import com.android.internal.telephony.Call.State;
import com.android.internal.telephony.CallDetails;
import com.android.internal.telephony.CallForwardInfo;
import com.android.internal.telephony.CallStateException;
import com.android.internal.telephony.CallTracker;
import com.android.internal.telephony.CommandException;
import com.android.internal.telephony.CommandException.Error;
import com.android.internal.telephony.CommandsInterface;
import com.android.internal.telephony.Connection;
import com.android.internal.telephony.IccCard;
import com.android.internal.telephony.IccPhoneBookInterfaceManager;
import com.android.internal.telephony.OperatorInfo;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.Phone.DataActivityState;
import com.android.internal.telephony.Phone.SuppService;
import com.android.internal.telephony.PhoneBase;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.PhoneConstants.DataState;
import com.android.internal.telephony.PhoneNotifier;
import com.android.internal.telephony.PhoneSubInfo;
import com.android.internal.telephony.Subscription;
import com.android.internal.telephony.UUSInfo;
import com.android.internal.telephony.cdma.CDMAPhone;
import com.android.internal.telephony.gsm.GSMPhone;
import com.android.internal.telephony.uicc.IccFileHandler;
import com.android.internal.telephony.uicc.IccRecords;
import java.util.ArrayList;
import java.util.List;

public class ImsPhone extends ImsPhoneBase {
    static final int CANCEL_ECM_TIMER = 1;
    public static final String CS_FALLBACK = "cs_fallback";
    private static final boolean DBG = true;
    private static final int DEFAULT_ECM_EXIT_TIMER_VALUE = 300000;
    protected static final int EVENT_GET_CALL_BARRING_DONE = 38;
    protected static final int EVENT_GET_CALL_WAITING_DONE = 40;
    protected static final int EVENT_SET_CALL_BARRING_DONE = 37;
    protected static final int EVENT_SET_CALL_WAITING_DONE = 39;
    private static final String LOG_TAG = "ImsPhone";
    static final int RESTART_ECM_TIMER = 0;
    private static final boolean VDBG = false;
    ImsPhoneCallTracker mCT;
    PhoneBase mDefaultPhone;
    private Registrant mEcmExitRespRegistrant;
    private Runnable mExitEcmRunnable = new C01161();
    ImsEcbmStateListener mImsEcbmStateListener = new C01172();
    protected boolean mIsPhoneInEcmState;
    private String mLastDialString;
    ArrayList<ImsPhoneMmiCode> mPendingMMIs = new ArrayList();
    Registrant mPostDialHandler;
    ServiceState mSS = new ServiceState();
    private final RegistrantList mSilentRedialRegistrants = new RegistrantList();
    WakeLock mWakeLock;

    class C01161 implements Runnable {
        C01161() {
        }

        public void run() {
            ImsPhone.this.exitEmergencyCallbackMode();
        }
    }

    class C01172 extends ImsEcbmStateListener {
        C01172() {
        }

        public void onECBMEntered() {
            Rlog.d(ImsPhone.LOG_TAG, "onECBMEntered");
            ImsPhone.this.handleEnterEmergencyCallbackMode();
        }

        public void onECBMExited() {
            Rlog.d(ImsPhone.LOG_TAG, "onECBMExited");
            ImsPhone.this.handleExitEmergencyCallbackMode();
        }
    }

    private static class Cf {
        final boolean mIsCfu;
        final Message mOnComplete;
        final String mSetCfNumber;

        Cf(String cfNumber, boolean isCfu, Message onComplete) {
            this.mSetCfNumber = cfNumber;
            this.mIsCfu = isCfu;
            this.mOnComplete = onComplete;
        }
    }

    public /* bridge */ /* synthetic */ void activateCellBroadcastSms(int x0, Message x1) {
        super.activateCellBroadcastSms(x0, x1);
    }

    public /* bridge */ /* synthetic */ Connection dial(String x0, UUSInfo x1, int x2) throws CallStateException {
        return super.dial(x0, x1, x2);
    }

    public /* bridge */ /* synthetic */ Connection dial(String x0, UUSInfo x1, int x2, int x3, int x4, String[] x5) throws CallStateException {
        return super.dial(x0, x1, x2, x3, x4, x5);
    }

    public /* bridge */ /* synthetic */ boolean disableDataConnectivity() {
        return super.disableDataConnectivity();
    }

    public /* bridge */ /* synthetic */ void disableLocationUpdates() {
        super.disableLocationUpdates();
    }

    public /* bridge */ /* synthetic */ boolean enableDataConnectivity() {
        return super.enableDataConnectivity();
    }

    public /* bridge */ /* synthetic */ void enableLocationUpdates() {
        super.enableLocationUpdates();
    }

    public /* bridge */ /* synthetic */ List getAllCellInfo() {
        return super.getAllCellInfo();
    }

    public /* bridge */ /* synthetic */ void getAvailableNetworks(Message x0) {
        super.getAvailableNetworks(x0);
    }

    public /* bridge */ /* synthetic */ boolean getCallForwardingIndicator() {
        return super.getCallForwardingIndicator();
    }

    public /* bridge */ /* synthetic */ void getCellBroadcastSmsConfig(Message x0) {
        super.getCellBroadcastSmsConfig(x0);
    }

    public /* bridge */ /* synthetic */ CellLocation getCellLocation() {
        return super.getCellLocation();
    }

    public /* bridge */ /* synthetic */ List getCurrentDataConnectionList() {
        return super.getCurrentDataConnectionList();
    }

    public /* bridge */ /* synthetic */ DataActivityState getDataActivityState() {
        return super.getDataActivityState();
    }

    public /* bridge */ /* synthetic */ void getDataCallList(Message x0) {
        super.getDataCallList(x0);
    }

    public /* bridge */ /* synthetic */ DataState getDataConnectionState() {
        return super.getDataConnectionState();
    }

    public /* bridge */ /* synthetic */ DataState getDataConnectionState(String x0) {
        return super.getDataConnectionState(x0);
    }

    public /* bridge */ /* synthetic */ boolean getDataEnabled() {
        return super.getDataEnabled();
    }

    public /* bridge */ /* synthetic */ boolean getDataRoamingEnabled() {
        return super.getDataRoamingEnabled();
    }

    public /* bridge */ /* synthetic */ String getDeviceId() {
        return super.getDeviceId();
    }

    public /* bridge */ /* synthetic */ String getDeviceSvn() {
        return super.getDeviceSvn();
    }

    public /* bridge */ /* synthetic */ String getEsn() {
        return super.getEsn();
    }

    public /* bridge */ /* synthetic */ String getGroupIdLevel1() {
        return super.getGroupIdLevel1();
    }

    public /* bridge */ /* synthetic */ IccCard getIccCard() {
        return super.getIccCard();
    }

    public /* bridge */ /* synthetic */ IccFileHandler getIccFileHandler() {
        return super.getIccFileHandler();
    }

    public /* bridge */ /* synthetic */ IccPhoneBookInterfaceManager getIccPhoneBookInterfaceManager() {
        return super.getIccPhoneBookInterfaceManager();
    }

    public /* bridge */ /* synthetic */ boolean getIccRecordsLoaded() {
        return super.getIccRecordsLoaded();
    }

    public /* bridge */ /* synthetic */ String getIccSerialNumber() {
        return super.getIccSerialNumber();
    }

    public /* bridge */ /* synthetic */ String getImei() {
        return super.getImei();
    }

    public /* bridge */ /* synthetic */ String getLine1AlphaTag() {
        return super.getLine1AlphaTag();
    }

    public /* bridge */ /* synthetic */ String getLine1Number() {
        return super.getLine1Number();
    }

    public /* bridge */ /* synthetic */ LinkProperties getLinkProperties(String x0) {
        return super.getLinkProperties(x0);
    }

    public /* bridge */ /* synthetic */ String getMeid() {
        return super.getMeid();
    }

    public /* bridge */ /* synthetic */ boolean getMessageWaitingIndicator() {
        return super.getMessageWaitingIndicator();
    }

    public /* bridge */ /* synthetic */ void getNeighboringCids(Message x0) {
        super.getNeighboringCids(x0);
    }

    public /* bridge */ /* synthetic */ void getOutgoingCallerIdDisplay(Message x0) {
        super.getOutgoingCallerIdDisplay(x0);
    }

    public /* bridge */ /* synthetic */ PhoneSubInfo getPhoneSubInfo() {
        return super.getPhoneSubInfo();
    }

    public /* bridge */ /* synthetic */ int getPhoneType() {
        return super.getPhoneType();
    }

    public /* bridge */ /* synthetic */ String getRuimid() {
        return super.getRuimid();
    }

    public /* bridge */ /* synthetic */ boolean getSMSPavailable() {
        return super.getSMSPavailable();
    }

    public /* bridge */ /* synthetic */ boolean getSMSavailable() {
        return super.getSMSavailable();
    }

    public /* bridge */ /* synthetic */ String getSelectedApn() {
        return super.getSelectedApn();
    }

    public /* bridge */ /* synthetic */ SignalStrength getSignalStrength() {
        return super.getSignalStrength();
    }

    public /* bridge */ /* synthetic */ String getSubscriberId() {
        return super.getSubscriberId();
    }

    public /* bridge */ /* synthetic */ String getVoiceMailAlphaTag() {
        return super.getVoiceMailAlphaTag();
    }

    public /* bridge */ /* synthetic */ String getVoiceMailNumber() {
        return super.getVoiceMailNumber();
    }

    public /* bridge */ /* synthetic */ boolean handlePinMmi(String x0) {
        return super.handlePinMmi(x0);
    }

    public /* bridge */ /* synthetic */ boolean isDataConnectivityPossible() {
        return super.isDataConnectivityPossible();
    }

    public /* bridge */ /* synthetic */ void migrateFrom(PhoneBase x0) {
        super.migrateFrom(x0);
    }

    public /* bridge */ /* synthetic */ boolean needsOtaServiceProvisioning() {
        return super.needsOtaServiceProvisioning();
    }

    public /* bridge */ /* synthetic */ void notifyCallForwardingIndicator() {
        super.notifyCallForwardingIndicator();
    }

    public /* bridge */ /* synthetic */ void registerForOnHoldTone(Handler x0, int x1, Object x2) {
        super.registerForOnHoldTone(x0, x1, x2);
    }

    public /* bridge */ /* synthetic */ void registerForRingbackTone(Handler x0, int x1, Object x2) {
        super.registerForRingbackTone(x0, x1, x2);
    }

    public /* bridge */ /* synthetic */ void registerForSuppServiceNotification(Handler x0, int x1, Object x2) {
        super.registerForSuppServiceNotification(x0, x1, x2);
    }

    public /* bridge */ /* synthetic */ void saveClirSetting(int x0) {
        super.saveClirSetting(x0);
    }

    public /* bridge */ /* synthetic */ void selectNetworkManually(OperatorInfo x0, Message x1) {
        super.selectNetworkManually(x0, x1);
    }

    public /* bridge */ /* synthetic */ void setCellBroadcastSmsConfig(int[] x0, Message x1) {
        super.setCellBroadcastSmsConfig(x0, x1);
    }

    public /* bridge */ /* synthetic */ void setDataEnabled(boolean x0) {
        super.setDataEnabled(x0);
    }

    public /* bridge */ /* synthetic */ void setDataRoamingEnabled(boolean x0) {
        super.setDataRoamingEnabled(x0);
    }

    public /* bridge */ /* synthetic */ void setLine1Number(String x0, String x1, Message x2) {
        super.setLine1Number(x0, x1, x2);
    }

    public /* bridge */ /* synthetic */ void setNetworkSelectionModeAutomatic(Message x0) {
        super.setNetworkSelectionModeAutomatic(x0);
    }

    public /* bridge */ /* synthetic */ void setOutgoingCallerIdDisplay(int x0, Message x1) {
        super.setOutgoingCallerIdDisplay(x0, x1);
    }

    public /* bridge */ /* synthetic */ void setRadioPower(boolean x0) {
        super.setRadioPower(x0);
    }

    public /* bridge */ /* synthetic */ void setSelectedApn() {
        super.setSelectedApn();
    }

    public /* bridge */ /* synthetic */ void setVoiceMailNumber(String x0, String x1, Message x2) {
        super.setVoiceMailNumber(x0, x1, x2);
    }

    public /* bridge */ /* synthetic */ void unregisterForOnHoldTone(Handler x0) {
        super.unregisterForOnHoldTone(x0);
    }

    public /* bridge */ /* synthetic */ void unregisterForRingbackTone(Handler x0) {
        super.unregisterForRingbackTone(x0);
    }

    public /* bridge */ /* synthetic */ void unregisterForSuppServiceNotification(Handler x0) {
        super.unregisterForSuppServiceNotification(x0);
    }

    public /* bridge */ /* synthetic */ void updateServiceLocation() {
        super.updateServiceLocation();
    }

    ImsPhone(Context context, PhoneNotifier notifier, Phone defaultPhone) {
        super(LOG_TAG, context, notifier);
        this.mDefaultPhone = (PhoneBase) defaultPhone;
        this.mCT = new ImsPhoneCallTracker(this);
        this.mSS.setStateOff();
        this.mPhoneId = this.mDefaultPhone.getPhoneId();
        this.mIsPhoneInEcmState = SystemProperties.getBoolean("ril.cdma.inecmmode", false);
        this.mWakeLock = ((PowerManager) context.getSystemService("power")).newWakeLock(1, LOG_TAG);
        this.mWakeLock.setReferenceCounted(false);
    }

    public void updateParentPhone(PhoneBase parentPhone) {
        this.mDefaultPhone = parentPhone;
        this.mPhoneId = this.mDefaultPhone.getPhoneId();
    }

    public void dispose() {
        Rlog.d(LOG_TAG, "dispose");
        this.mPendingMMIs.clear();
        this.mCT.dispose();
    }

    public void removeReferences() {
        Rlog.d(LOG_TAG, "removeReferences");
        super.removeReferences();
        this.mCT = null;
        this.mSS = null;
    }

    public ServiceState getServiceState() {
        return this.mSS;
    }

    void setServiceState(int state) {
        this.mSS.setState(state);
    }

    public CallTracker getCallTracker() {
        return this.mCT;
    }

    public List<? extends ImsPhoneMmiCode> getPendingMmiCodes() {
        return this.mPendingMMIs;
    }

    public void acceptCall(int videoState) throws CallStateException {
        this.mCT.acceptCall(videoState);
    }

    public void rejectCall() throws CallStateException {
        this.mCT.rejectCall();
    }

    public void switchHoldingAndActive() throws CallStateException {
        this.mCT.switchWaitingOrHoldingAndActive();
    }

    public boolean canConference() {
        return this.mCT.canConference();
    }

    public boolean canDial() {
        return this.mCT.canDial();
    }

    public void conference() {
        this.mCT.conference();
    }

    public void clearDisconnected() {
        this.mCT.clearDisconnected();
    }

    public boolean canTransfer() {
        return this.mCT.canTransfer();
    }

    public void explicitCallTransfer() {
        this.mCT.explicitCallTransfer();
    }

    public ImsPhoneCall getForegroundCall() {
        return this.mCT.mForegroundCall;
    }

    public ImsPhoneCall getBackgroundCall() {
        return this.mCT.mBackgroundCall;
    }

    public ImsPhoneCall getRingingCall() {
        return this.mCT.mRingingCall;
    }

    private boolean handleCallDeflectionIncallSupplementaryService(String dialString) {
        if (dialString.length() > 1) {
            return false;
        }
        if (getRingingCall().getState() != State.IDLE) {
            Rlog.d(LOG_TAG, "MmiCode 0: rejectCall");
            try {
                this.mCT.rejectCall();
                return true;
            } catch (CallStateException e) {
                Rlog.d(LOG_TAG, "reject failed", e);
                notifySuppServiceFailed(SuppService.REJECT);
                return true;
            }
        } else if (getBackgroundCall().getState() == State.IDLE) {
            return true;
        } else {
            Rlog.d(LOG_TAG, "MmiCode 0: hangupWaitingOrBackground");
            try {
                this.mCT.hangup(getBackgroundCall());
                return true;
            } catch (CallStateException e2) {
                Rlog.d(LOG_TAG, "hangup failed", e2);
                return true;
            }
        }
    }

    private boolean handleCallWaitingIncallSupplementaryService(String dialString) {
        int len = dialString.length();
        if (len > 2) {
            return false;
        }
        ImsPhoneCall call = getForegroundCall();
        if (len > 1) {
            try {
                Rlog.d(LOG_TAG, "not support 1X SEND");
                notifySuppServiceFailed(SuppService.HANGUP);
                return true;
            } catch (CallStateException e) {
                Rlog.d(LOG_TAG, "hangup failed", e);
                notifySuppServiceFailed(SuppService.HANGUP);
                return true;
            }
        } else if (call.getState() != State.IDLE) {
            Rlog.d(LOG_TAG, "MmiCode 1: hangup foreground");
            this.mCT.hangup(call);
            return true;
        } else {
            Rlog.d(LOG_TAG, "MmiCode 1: switchWaitingOrHoldingAndActive");
            this.mCT.switchWaitingOrHoldingAndActive();
            return true;
        }
    }

    private boolean handleCallHoldIncallSupplementaryService(String dialString) {
        int len = dialString.length();
        if (len > 2) {
            return false;
        }
        ImsPhoneCall call = getForegroundCall();
        if (len > 1) {
            Rlog.d(LOG_TAG, "separate not supported");
            notifySuppServiceFailed(SuppService.SEPARATE);
            return true;
        }
        try {
            if (getRingingCall().getState() != State.IDLE) {
                Rlog.d(LOG_TAG, "MmiCode 2: accept ringing call");
                this.mCT.acceptCall(2);
                return true;
            }
            Rlog.d(LOG_TAG, "MmiCode 2: switchWaitingOrHoldingAndActive");
            this.mCT.switchWaitingOrHoldingAndActive();
            return true;
        } catch (CallStateException e) {
            Rlog.d(LOG_TAG, "switch failed", e);
            notifySuppServiceFailed(SuppService.SWITCH);
            return true;
        }
    }

    private boolean handleMultipartyIncallSupplementaryService(String dialString) {
        if (dialString.length() > 1) {
            return false;
        }
        Rlog.d(LOG_TAG, "MmiCode 3: merge calls");
        conference();
        return true;
    }

    private boolean handleEctIncallSupplementaryService(String dialString) {
        if (dialString.length() != 1) {
            return false;
        }
        Rlog.d(LOG_TAG, "MmiCode 4: not support explicit call transfer");
        notifySuppServiceFailed(SuppService.TRANSFER);
        return true;
    }

    private boolean handleCcbsIncallSupplementaryService(String dialString) {
        if (dialString.length() > 1) {
            return false;
        }
        Rlog.i(LOG_TAG, "MmiCode 5: CCBS not supported!");
        notifySuppServiceFailed(SuppService.UNKNOWN);
        return true;
    }

    public boolean handleInCallMmiCommands(String dialString) {
        if (!isInCall()) {
            return false;
        }
        if (TextUtils.isEmpty(dialString)) {
            return false;
        }
        switch (dialString.charAt(0)) {
            case '0':
                return handleCallDeflectionIncallSupplementaryService(dialString);
            case '1':
                return handleCallWaitingIncallSupplementaryService(dialString);
            case '2':
                return handleCallHoldIncallSupplementaryService(dialString);
            case '3':
                return handleMultipartyIncallSupplementaryService(dialString);
            case '4':
                return handleEctIncallSupplementaryService(dialString);
            case '5':
                return handleCcbsIncallSupplementaryService(dialString);
            default:
                return false;
        }
    }

    boolean isInCall() {
        return getForegroundCall().getState().isAlive() || getBackgroundCall().getState().isAlive() || getRingingCall().getState().isAlive();
    }

    void notifyNewRingingConnection(Connection c) {
        this.mDefaultPhone.notifyNewRingingConnectionP(c);
    }

    public Connection dial(String dialString, int videoState) throws CallStateException {
        return dial(dialString, videoState, 0, 2, null);
    }

    public Connection dial(String dialString, int videoState, int callType, int callDomain, String[] extras) throws CallStateException {
        return dialInternal(dialString, videoState, callType, callDomain, extras);
    }

    protected Connection dialInternal(String dialString, int videoState) throws CallStateException {
        return dialInternal(dialString, videoState, 0, 2, null);
    }

    protected Connection dialInternal(String dialString, int videoState, int callType, int callDomain, String[] extras) throws CallStateException {
        String newDialString;
        CallDetails callDetails = new CallDetails(callType, callDomain, extras);
        if (callDetails == null || !"unknown".equals(callDetails.getExtraValue("participants"))) {
            newDialString = PhoneNumberUtils.stripSeparators(dialString);
        } else {
            newDialString = dialString;
        }
        if (handleInCallMmiCommands(newDialString)) {
            return null;
        }
        if (this.mDefaultPhone.getPhoneType() == 2) {
            return this.mCT.dial(dialString, videoState, callDetails);
        }
        ImsPhoneMmiCode mmi = ImsPhoneMmiCode.newFromDialString(PhoneNumberUtils.extractNetworkPortionAlt(newDialString), this);
        Rlog.d(LOG_TAG, "dialing w/ mmi '" + mmi + "'...");
        if (mmi == null) {
            return this.mCT.dial(dialString, videoState, callDetails);
        }
        if (mmi.isTemporaryModeCLIR()) {
            return this.mCT.dial(mmi.getDialingNumber(), mmi.getCLIRMode(), videoState, callDetails);
        }
        if (mmi.isSupportedOverImsPhone()) {
            this.mPendingMMIs.add(mmi);
            this.mMmiRegistrants.notifyRegistrants(new AsyncResult(null, mmi, null));
            mmi.processCode();
            return null;
        }
        throw new CallStateException(CS_FALLBACK);
    }

    public void sendDtmf(char c) {
        if (!PhoneNumberUtils.is12Key(c)) {
            Rlog.e(LOG_TAG, "sendDtmf called with invalid character '" + c + "'");
        } else if (this.mCT.mState == PhoneConstants.State.OFFHOOK) {
            this.mCT.sendDtmf(c);
        }
    }

    public void startDtmf(char c) {
        if (PhoneNumberUtils.is12Key(c)) {
            sendDtmf(c);
        } else {
            Rlog.e(LOG_TAG, "startDtmf called with invalid character '" + c + "'");
        }
    }

    public void stopDtmf() {
    }

    public void setOnPostDialCharacter(Handler h, int what, Object obj) {
        this.mPostDialHandler = new Registrant(h, what, obj);
    }

    void notifyIncomingRing() {
        Rlog.d(LOG_TAG, "notifyIncomingRing");
        sendMessage(obtainMessage(14, new AsyncResult(null, null, null)));
    }

    public void setMute(boolean muted) {
        this.mCT.setMute(muted);
    }

    public boolean getMute() {
        return this.mCT.getMute();
    }

    public PhoneConstants.State getState() {
        return this.mCT.mState;
    }

    private boolean isValidCommandInterfaceCFReason(int commandInterfaceCFReason) {
        switch (commandInterfaceCFReason) {
            case 0:
            case 1:
            case 2:
            case 3:
            case 4:
            case 5:
                return true;
            default:
                return false;
        }
    }

    private boolean isValidCommandInterfaceCFAction(int commandInterfaceCFAction) {
        switch (commandInterfaceCFAction) {
            case 0:
            case 1:
            case 3:
            case 4:
                return true;
            default:
                return false;
        }
    }

    private boolean isCfEnable(int action) {
        return action == 1 || action == 3;
    }

    private int getConditionFromCFReason(int reason) {
        switch (reason) {
            case 0:
                return 0;
            case 1:
                return 1;
            case 2:
                return 2;
            case 3:
                return 3;
            default:
                return -1;
        }
    }

    private int getCFReasonFromCondition(int condition) {
        switch (condition) {
            case 0:
                return 0;
            case 1:
                return 1;
            case 2:
                return 2;
            default:
                return 3;
        }
    }

    private int getActionFromCFAction(int action) {
        switch (action) {
            case 0:
                return 0;
            case 1:
                return 1;
            case 3:
                return 3;
            case 4:
                return 4;
            default:
                return -1;
        }
    }

    public void getCallForwardingOption(int commandInterfaceCFReason, String dialingNumber, int serviceClass, Message onComplete) {
        Rlog.d(LOG_TAG, "getCallForwardingOption reason=" + commandInterfaceCFReason);
        if (isValidCommandInterfaceCFReason(commandInterfaceCFReason)) {
            Rlog.d(LOG_TAG, "requesting call forwarding query.");
            try {
                this.mCT.getUtInterface().queryCallForward(getConditionFromCFReason(commandInterfaceCFReason), null, obtainMessage(13, onComplete));
            } catch (Throwable e) {
                sendErrorResponse(onComplete, e);
            }
        } else if (onComplete != null) {
            sendErrorResponse(onComplete);
        }
    }

    public void setCallForwardingOption(int commandInterfaceCFAction, int commandInterfaceCFReason, String dialingNumber, int timerSeconds, int serviceClass, Message onComplete) {
        int i = 1;
        Rlog.d(LOG_TAG, "setCallForwardingOption action=" + commandInterfaceCFAction + ", reason=" + commandInterfaceCFReason);
        if (isValidCommandInterfaceCFAction(commandInterfaceCFAction) && isValidCommandInterfaceCFReason(commandInterfaceCFReason)) {
            Cf cf = new Cf(dialingNumber, commandInterfaceCFReason == 0, onComplete);
            if (!isCfEnable(commandInterfaceCFAction)) {
                i = 0;
            }
            Message resp = obtainMessage(12, i, serviceClass, cf);
            try {
                this.mCT.getUtInterface().updateCallForward(getActionFromCFAction(commandInterfaceCFAction), getConditionFromCFReason(commandInterfaceCFReason), dialingNumber, timerSeconds, onComplete);
            } catch (Throwable e) {
                sendErrorResponse(onComplete, e);
            }
        } else if (onComplete != null) {
            sendErrorResponse(onComplete);
        }
    }

    public void getCallWaiting(Message onComplete) {
        Rlog.d(LOG_TAG, "getCallWaiting");
        try {
            this.mCT.getUtInterface().queryCallWaiting(obtainMessage(40, onComplete));
        } catch (Throwable e) {
            sendErrorResponse(onComplete, e);
        }
    }

    public void setCallWaiting(boolean enable, Message onComplete) {
        Rlog.d(LOG_TAG, "setCallWaiting enable=" + enable);
        try {
            this.mCT.getUtInterface().updateCallWaiting(enable, obtainMessage(39, onComplete));
        } catch (Throwable e) {
            sendErrorResponse(onComplete, e);
        }
    }

    private int getCBTypeFromFacility(String facility) {
        if (CommandsInterface.CB_FACILITY_BAOC.equals(facility)) {
            return 2;
        }
        if (CommandsInterface.CB_FACILITY_BAOIC.equals(facility)) {
            return 3;
        }
        if (CommandsInterface.CB_FACILITY_BAOICxH.equals(facility)) {
            return 4;
        }
        if (CommandsInterface.CB_FACILITY_BAIC.equals(facility)) {
            return 1;
        }
        if (CommandsInterface.CB_FACILITY_BAICr.equals(facility)) {
            return 5;
        }
        if (CommandsInterface.CB_FACILITY_BA_ALL.equals(facility)) {
            return 7;
        }
        if (CommandsInterface.CB_FACILITY_BA_MO.equals(facility)) {
            return 8;
        }
        if (CommandsInterface.CB_FACILITY_BA_MT.equals(facility)) {
            return 9;
        }
        return 0;
    }

    void getCallBarring(String facility, Message onComplete) {
        Rlog.d(LOG_TAG, "getCallBarring facility=" + facility);
        try {
            this.mCT.getUtInterface().queryCallBarring(getCBTypeFromFacility(facility), obtainMessage(38, onComplete));
        } catch (Throwable e) {
            sendErrorResponse(onComplete, e);
        }
    }

    void setCallBarring(String facility, boolean lockState, String password, Message onComplete) {
        Rlog.d(LOG_TAG, "setCallBarring facility=" + facility + ", lockState=" + lockState);
        try {
            this.mCT.getUtInterface().updateCallBarring(getCBTypeFromFacility(facility), lockState, obtainMessage(37, onComplete), null);
        } catch (Throwable e) {
            sendErrorResponse(onComplete, e);
        }
    }

    public void sendUssdResponse(String ussdMessge) {
        Rlog.d(LOG_TAG, "sendUssdResponse");
        ImsPhoneMmiCode mmi = ImsPhoneMmiCode.newFromUssdUserInput(ussdMessge, this);
        this.mPendingMMIs.add(mmi);
        this.mMmiRegistrants.notifyRegistrants(new AsyncResult(null, mmi, null));
        mmi.sendUssd(ussdMessge);
    }

    void sendUSSD(String ussdString, Message response) {
        this.mCT.sendUSSD(ussdString, response);
    }

    void cancelUSSD() {
        this.mCT.cancelUSSD();
    }

    void sendErrorResponse(Message onComplete) {
        Rlog.d(LOG_TAG, "sendErrorResponse");
        if (onComplete != null) {
            AsyncResult.forMessage(onComplete, null, new CommandException(Error.GENERIC_FAILURE));
            onComplete.sendToTarget();
        }
    }

    void sendErrorResponse(Message onComplete, Throwable e) {
        Rlog.d(LOG_TAG, "sendErrorResponse");
        if (onComplete != null) {
            AsyncResult.forMessage(onComplete, null, getCommandException(e));
            onComplete.sendToTarget();
        }
    }

    void sendErrorResponse(Message onComplete, ImsReasonInfo reasonInfo) {
        Rlog.d(LOG_TAG, "sendErrorResponse reasonCode=" + reasonInfo.getCode());
        if (onComplete != null) {
            AsyncResult.forMessage(onComplete, null, getCommandException(reasonInfo.getCode()));
            onComplete.sendToTarget();
        }
    }

    CommandException getCommandException(int code) {
        Rlog.d(LOG_TAG, "getCommandException code=" + code);
        Error error = Error.GENERIC_FAILURE;
        switch (code) {
            case 801:
                error = Error.REQUEST_NOT_SUPPORTED;
                break;
            case 821:
                error = Error.PASSWORD_INCORRECT;
                break;
        }
        return new CommandException(error);
    }

    CommandException getCommandException(Throwable e) {
        if (e instanceof ImsException) {
            return getCommandException(((ImsException) e).getCode());
        }
        Rlog.d(LOG_TAG, "getCommandException generic failure");
        return new CommandException(Error.GENERIC_FAILURE);
    }

    private void onNetworkInitiatedUssd(ImsPhoneMmiCode mmi) {
        Rlog.d(LOG_TAG, "onNetworkInitiatedUssd");
        this.mMmiCompleteRegistrants.notifyRegistrants(new AsyncResult(null, mmi, null));
    }

    void onIncomingUSSD(int ussdMode, String ussdMessage) {
        boolean isUssdRequest;
        boolean isUssdError = true;
        Rlog.d(LOG_TAG, "onIncomingUSSD ussdMode=" + ussdMode);
        if (ussdMode == 1) {
            isUssdRequest = true;
        } else {
            isUssdRequest = false;
        }
        if (ussdMode == 0 || ussdMode == 1) {
            isUssdError = false;
        }
        ImsPhoneMmiCode found = null;
        int s = this.mPendingMMIs.size();
        for (int i = 0; i < s; i++) {
            if (((ImsPhoneMmiCode) this.mPendingMMIs.get(i)).isPendingUSSD()) {
                found = (ImsPhoneMmiCode) this.mPendingMMIs.get(i);
                break;
            }
        }
        if (found != null) {
            if (isUssdError) {
                found.onUssdFinishedError();
            } else {
                found.onUssdFinished(ussdMessage, isUssdRequest);
            }
        } else if (!isUssdError && ussdMessage != null) {
            onNetworkInitiatedUssd(ImsPhoneMmiCode.newNetworkInitiatedUssd(ussdMessage, isUssdRequest, this));
        }
    }

    void onMMIDone(ImsPhoneMmiCode mmi) {
        if (this.mPendingMMIs.remove(mmi) || mmi.isUssdRequest()) {
            this.mMmiCompleteRegistrants.notifyRegistrants(new AsyncResult(null, mmi, null));
        }
    }

    public ImsPhoneConnection getHandoverConnection() {
        ImsPhoneConnection conn = getForegroundCall().getHandoverConnection();
        if (conn == null) {
            conn = getBackgroundCall().getHandoverConnection();
        }
        if (conn == null) {
            return getRingingCall().getHandoverConnection();
        }
        return conn;
    }

    public void notifySrvccState(SrvccState state) {
        this.mCT.notifySrvccState(state);
    }

    void initiateSilentRedial() {
        AsyncResult ar = new AsyncResult(null, this.mLastDialString, null);
        if (ar != null) {
            this.mSilentRedialRegistrants.notifyRegistrants(ar);
        }
    }

    public void registerForSilentRedial(Handler h, int what, Object obj) {
        this.mSilentRedialRegistrants.addUnique(h, what, obj);
    }

    public void unregisterForSilentRedial(Handler h) {
        this.mSilentRedialRegistrants.remove(h);
    }

    public long getSubId() {
        return this.mDefaultPhone.getSubId();
    }

    public int getPhoneId() {
        return this.mDefaultPhone.getPhoneId();
    }

    public Subscription getSubscriptionInfo() {
        return this.mDefaultPhone.getSubscriptionInfo();
    }

    private IccRecords getIccRecords() {
        return (IccRecords) this.mDefaultPhone.mIccRecords.get();
    }

    private CallForwardInfo getCallForwardInfo(ImsCallForwardInfo info) {
        CallForwardInfo cfInfo = new CallForwardInfo();
        cfInfo.status = info.mStatus;
        cfInfo.reason = getCFReasonFromCondition(info.mCondition);
        cfInfo.serviceClass = 1;
        cfInfo.toa = info.mToA;
        cfInfo.number = info.mNumber;
        cfInfo.timeSeconds = info.mTimeSeconds;
        return cfInfo;
    }

    private CallForwardInfo[] handleCfQueryResult(ImsCallForwardInfo[] infos) {
        CallForwardInfo[] cfInfos = null;
        if (!(infos == null || infos.length == 0)) {
            cfInfos = new CallForwardInfo[infos.length];
        }
        IccRecords r = getIccRecords();
        if (infos != null && infos.length != 0) {
            int s = infos.length;
            for (int i = 0; i < s; i++) {
                if (infos[i].mCondition == 0 && r != null) {
                    boolean z;
                    if (infos[i].mStatus == 1) {
                        z = true;
                    } else {
                        z = false;
                    }
                    r.setVoiceCallForwardingFlag(1, z, infos[i].mNumber);
                }
                cfInfos[i] = getCallForwardInfo(infos[i]);
            }
        } else if (r != null) {
            r.setVoiceCallForwardingFlag(1, false, null);
        }
        return cfInfos;
    }

    private int[] handleCbQueryResult(ImsSsInfo[] infos) {
        int[] cbInfos = new int[]{0};
        if (infos[0].mStatus == 1) {
            cbInfos[0] = 1;
        }
        return cbInfos;
    }

    private int[] handleCwQueryResult(ImsSsInfo[] infos) {
        int[] cwInfos = new int[2];
        cwInfos[0] = 0;
        if (infos[0].mStatus == 1) {
            cwInfos[0] = 1;
            cwInfos[1] = 1;
        }
        return cwInfos;
    }

    private void sendResponse(Message onComplete, Object result, Throwable e) {
        if (onComplete != null) {
            CommandException ex = null;
            if (e != null) {
                ex = getCommandException(e);
            }
            AsyncResult.forMessage(onComplete, result, ex);
            onComplete.sendToTarget();
        }
    }

    public void handleMessage(Message msg) {
        AsyncResult ar = msg.obj;
        Rlog.d(LOG_TAG, "handleMessage what=" + msg.what);
        switch (msg.what) {
            case 12:
                IccRecords r = getIccRecords();
                Cf cf = ar.userObj;
                if (cf.mIsCfu && ar.exception == null && r != null) {
                    r.setVoiceCallForwardingFlag(1, msg.arg1 == 1, cf.mSetCfNumber);
                }
                sendResponse(cf.mOnComplete, null, ar.exception);
                return;
            case 13:
                CallForwardInfo[] cfInfos = null;
                if (ar.exception == null) {
                    cfInfos = handleCfQueryResult((ImsCallForwardInfo[]) ar.result);
                }
                sendResponse((Message) ar.userObj, cfInfos, ar.exception);
                return;
            case 37:
            case 39:
                sendResponse((Message) ar.userObj, null, ar.exception);
                return;
            case 38:
            case 40:
                int[] ssInfos = null;
                if (ar.exception == null) {
                    if (msg.what == 38) {
                        ssInfos = handleCbQueryResult((ImsSsInfo[]) ar.result);
                    } else if (msg.what == 40) {
                        ssInfos = handleCwQueryResult((ImsSsInfo[]) ar.result);
                    }
                }
                sendResponse((Message) ar.userObj, ssInfos, ar.exception);
                return;
            default:
                super.handleMessage(msg);
                return;
        }
    }

    public boolean isInEmergencyCall() {
        return this.mCT.isInEmergencyCall();
    }

    public boolean isInEcm() {
        return this.mIsPhoneInEcmState;
    }

    void sendEmergencyCallbackModeChange() {
        Intent intent = new Intent("android.intent.action.EMERGENCY_CALLBACK_MODE_CHANGED");
        intent.putExtra("phoneinECMState", this.mIsPhoneInEcmState);
        SubscriptionManager.putPhoneIdAndSubIdExtra(intent, getPhoneId());
        ActivityManagerNative.broadcastStickyIntent(intent, null, -1);
        Rlog.d(LOG_TAG, "sendEmergencyCallbackModeChange");
    }

    public void exitEmergencyCallbackMode() {
        if (this.mWakeLock.isHeld()) {
            this.mWakeLock.release();
        }
        Rlog.d(LOG_TAG, "exitEmergencyCallbackMode()");
        try {
            this.mCT.getEcbmInterface().exitEmergencyCallbackMode();
        } catch (ImsException e) {
            e.printStackTrace();
        }
    }

    private void handleEnterEmergencyCallbackMode() {
        Rlog.d(LOG_TAG, "handleEnterEmergencyCallbackMode,mIsPhoneInEcmState= " + this.mIsPhoneInEcmState);
        if (!this.mIsPhoneInEcmState) {
            this.mIsPhoneInEcmState = true;
            sendEmergencyCallbackModeChange();
            setSystemProperty("ril.cdma.inecmmode", "true");
            postDelayed(this.mExitEcmRunnable, SystemProperties.getLong("ro.cdma.ecmexittimer", 300000));
            this.mWakeLock.acquire();
        }
    }

    private void handleExitEmergencyCallbackMode() {
        Rlog.d(LOG_TAG, "handleExitEmergencyCallbackMode: mIsPhoneInEcmState = " + this.mIsPhoneInEcmState);
        removeCallbacks(this.mExitEcmRunnable);
        if (this.mEcmExitRespRegistrant != null) {
            this.mEcmExitRespRegistrant.notifyResult(Boolean.TRUE);
        }
        if (this.mIsPhoneInEcmState) {
            this.mIsPhoneInEcmState = false;
            setSystemProperty("ril.cdma.inecmmode", "false");
        }
        sendEmergencyCallbackModeChange();
    }

    void handleTimerInEmergencyCallbackMode(int action) {
        switch (action) {
            case 0:
                postDelayed(this.mExitEcmRunnable, SystemProperties.getLong("ro.cdma.ecmexittimer", 300000));
                if (this.mDefaultPhone.getPhoneType() == 1) {
                    ((GSMPhone) this.mDefaultPhone).notifyEcbmTimerReset(Boolean.FALSE);
                    return;
                } else {
                    ((CDMAPhone) this.mDefaultPhone).notifyEcbmTimerReset(Boolean.FALSE);
                    return;
                }
            case 1:
                removeCallbacks(this.mExitEcmRunnable);
                if (this.mDefaultPhone.getPhoneType() == 1) {
                    ((GSMPhone) this.mDefaultPhone).notifyEcbmTimerReset(Boolean.TRUE);
                    return;
                } else {
                    ((CDMAPhone) this.mDefaultPhone).notifyEcbmTimerReset(Boolean.TRUE);
                    return;
                }
            default:
                Rlog.e(LOG_TAG, "handleTimerInEmergencyCallbackMode, unsupported action " + action);
                return;
        }
    }

    public void setOnEcbModeExitResponse(Handler h, int what, Object obj) {
        this.mEcmExitRespRegistrant = new Registrant(h, what, obj);
    }

    public void unsetOnEcbModeExitResponse(Handler h) {
        this.mEcmExitRespRegistrant.clear();
    }

    public boolean isVolteEnabled() {
        return this.mCT.isVolteEnabled();
    }

    public boolean isVtEnabled() {
        return this.mCT.isVtEnabled();
    }

    public int getDataServiceState() {
        return getServiceState().getDataRegState();
    }

    public String getSktIrm() {
        Rlog.e(LOG_TAG, "Error! getSktIrm() is not supported by IMS");
        return null;
    }

    public String getSktImsiM() {
        Rlog.e(LOG_TAG, "Error! getSktImsiM() is not supported by IMS");
        return null;
    }

    public boolean hasIsim() {
        logUnexpectedMethodCall("hasIsim");
        return false;
    }

    public byte[] getPsismsc() {
        logUnexpectedMethodCall("getPsismsc");
        return null;
    }

    public void getPreferredNetworkList(Message response) {
        logUnexpectedMethodCall("getPreferredNetworkList");
    }

    public void setPreferredNetworkList(int index, String operator, String plmn, int gsmAct, int gsmCompactAct, int utranAct, int mode, Message response) {
        logUnexpectedMethodCall("setPreferredNetworkList");
    }

    private void logUnexpectedMethodCall(String name) {
        Log.e(LOG_TAG, "Error! " + name + "() is not supported by " + getPhoneName());
    }

    public void getCallForwardingOption(int commandInterfaceCFReason, Message onComplete) {
        getCallForwardingOption(commandInterfaceCFReason, null, 0, onComplete);
    }

    public void setCallForwardingOption(int commandInterfaceCFAction, int commandInterfaceCFReason, String dialingNumber, int timerSeconds, Message onComplete) {
        setCallForwardingOption(commandInterfaceCFAction, commandInterfaceCFReason, dialingNumber, timerSeconds, 1, onComplete);
    }

    public void getCallBarringOption(String commandInterfacecbFlavour, Message onComplete) {
    }

    public void getCallBarringOption(String commandInterfacecbFlavour, String password, int serviceClass, Message onComplete) {
    }

    public boolean setCallBarringOption(boolean cbAction, String commandInterfacecbFlavour, String password, Message onComplete) {
        return false;
    }

    public boolean setCallBarringOption(boolean cbAction, String commandInterfacecbFlavour, String password, int serviceClass, Message onComplete) {
        return false;
    }

    public boolean changeBarringPassword(String facility, String oldPwd, String newPwd, Message onComplete) {
        return false;
    }

    public boolean changeBarringPassword(String facility, String oldPwd, String newPwd, String newPwdAgain, Message onComplete) {
        return false;
    }

    public String getLine1NumberType(int SimType) {
        logUnexpectedMethodCall("getLine1NumberType");
        return null;
    }

    public String getSubscriberIdType(int SimType) {
        logUnexpectedMethodCall("getSubscriberIdType");
        return null;
    }

    public void SimSlotOnOff(int on) {
        logUnexpectedMethodCall("SimSlotOnOff");
    }

    public boolean getMdnavailable() {
        logUnexpectedMethodCall("getMdnavailable");
        return false;
    }

    public boolean getMsisdnavailable() {
        logUnexpectedMethodCall("getMsisdnavailable");
        return false;
    }

    public boolean getDualSimSlotActivationState() {
        logUnexpectedMethodCall("getDualSimSlotActivationState");
        return false;
    }

    public void startGlobalNetworkSearchTimer() {
        logUnexpectedMethodCall("startGlobalNetworkSearchTimer");
    }

    public void stopGlobalNetworkSearchTimer() {
        logUnexpectedMethodCall("stopGlobalNetworkSearchTimer");
    }

    public void startGlobalNoSvcChkTimer() {
        logUnexpectedMethodCall("startGlobalNoSvcChkTimer");
    }

    public void stopGlobalNoSvcChkTimer() {
        logUnexpectedMethodCall("stopGlobalNoSvcChkTimer");
    }

    public String getHandsetInfo(String ID) {
        return this.mDefaultPhone.getHandsetInfo(ID);
    }

    public void setGbaBootstrappingParams(byte[] rand, String btid, String keyLifetime, Message onComplete) {
        if (onComplete != null) {
            onComplete.sendToTarget();
        }
    }

    public String[] getSponImsi() {
        return this.mDefaultPhone.getSponImsi();
    }

    public void holdCall() throws CallStateException {
        this.mCT.switchWaitingOrHoldingAndActive();
    }

    public boolean getOCSGLAvailable() {
        logUnexpectedMethodCall("getOCSGLAvailable");
        return false;
    }

    public void selectCsg(Message response) {
        logUnexpectedMethodCall("selectCsg");
    }

    public boolean getFDNavailable() {
        logUnexpectedMethodCall("getFDNavailable");
        return false;
    }
}
