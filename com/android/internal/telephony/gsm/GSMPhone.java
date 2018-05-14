package com.android.internal.telephony.gsm;

import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.database.SQLException;
import android.net.Uri;
import android.os.AsyncResult;
import android.os.Binder;
import android.os.Handler;
import android.os.Message;
import android.os.Registrant;
import android.os.RegistrantList;
import android.os.SystemProperties;
import android.preference.PreferenceManager;
import android.provider.Settings.Global;
import android.provider.Telephony.Carriers;
import android.telephony.CellLocation;
import android.telephony.PhoneNumberUtils;
import android.telephony.Rlog;
import android.telephony.ServiceState;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import com.android.ims.ImsManager;
import com.android.internal.telephony.Call;
import com.android.internal.telephony.CallDetails;
import com.android.internal.telephony.CallForwardInfo;
import com.android.internal.telephony.CallStateException;
import com.android.internal.telephony.CallTracker;
import com.android.internal.telephony.CommandsInterface;
import com.android.internal.telephony.CommandsInterface.RadioState;
import com.android.internal.telephony.Connection;
import com.android.internal.telephony.DctConstants.Activity;
import com.android.internal.telephony.DctConstants.State;
import com.android.internal.telephony.IccPhoneBookInterfaceManager;
import com.android.internal.telephony.MccTable;
import com.android.internal.telephony.MmiCode;
import com.android.internal.telephony.OperatorInfo;
import com.android.internal.telephony.Phone.DataActivityState;
import com.android.internal.telephony.Phone.SuppService;
import com.android.internal.telephony.PhoneBase;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.PhoneConstants.DataState;
import com.android.internal.telephony.PhoneNotifier;
import com.android.internal.telephony.PhoneProxy;
import com.android.internal.telephony.PhoneSubInfo;
import com.android.internal.telephony.ServiceStateTracker;
import com.android.internal.telephony.SubscriptionController;
import com.android.internal.telephony.UUSInfo;
import com.android.internal.telephony.WspTypeDecoder;
import com.android.internal.telephony.cdma.CallFailCause;
import com.android.internal.telephony.dataconnection.DcTracker;
import com.android.internal.telephony.imsphone.ImsPhone;
import com.android.internal.telephony.test.SimulatedRadioControl;
import com.android.internal.telephony.uicc.IccRecords;
import com.android.internal.telephony.uicc.IccVmNotSupportedException;
import com.android.internal.telephony.uicc.IsimRecords;
import com.android.internal.telephony.uicc.IsimUiccRecords;
import com.android.internal.telephony.uicc.RuimRecords;
import com.android.internal.telephony.uicc.SIMRecords;
import com.android.internal.telephony.uicc.UiccCard;
import com.android.internal.telephony.uicc.UiccCardApplication;
import com.samsung.android.telephony.MultiSimManager;
import com.sec.android.app.CscFeature;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.FileDescriptor;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;

public class GSMPhone extends PhoneBase {
    public static final String CF_ICONKEY_VIDEO = "cf_iconkey_video";
    public static final String CF_ICONKEY_VOICE = "cf_iconkey_voice";
    public static final String CF_IMSIKEY = "cf_imsikey";
    public static final String CIPHERING_KEY = "ciphering_key";
    static final int EVENT_GET_AVAILABLE_NETWORK_COMPLETE = 100;
    private static final int GLOBAL_CDMAMODE = 7;
    private static final int GLOBAL_GLOBALMODE = 5;
    private static final int GLOBAL_GSMMODE = 6;
    private static final boolean LOCAL_DEBUG = true;
    static final String LOG_TAG = "GSMPhone";
    static String PROPERTY_CDMA_HOME_OPERATOR_NUMERIC = "ro.cdma.home.operator.numeric";
    static final boolean SHIP_BUILD = "true".equals(SystemProperties.get("ro.product_ship", "false"));
    private static final int SIM_CDMAMODE = 3;
    private static final int SIM_GLOBALMODE = 4;
    private static final int SIM_GSMMODE = 2;
    private static final int SIM_POWERCONNECT = 10;
    private static final int SIM_POWERDISCONNECT = 9;
    private static final int SIM_POWERDOWN = 0;
    private static final int SIM_POWERUP = 1;
    private static final int SIM_SETUPMENU = 8;
    private static final int UNKNOWN_NETWORK_MODE = 99;
    private static final boolean VDBG = true;
    public static final String VM_NUMBER = "vm_number_key";
    public static final String VM_NUMBER_CDMA = "vm_number_key_cdma";
    public static final String VM_SIM_IMSI = "vm_sim_imsi_key";
    GsmCallTracker mCT;
    private final RegistrantList mEcmTimerResetRegistrants;
    private String mEsn;
    private String mImei;
    private String mImeiSv;
    private IsimUiccRecords mIsimUiccRecords;
    private String mMdn;
    private String mMeid;
    private String mMmiErrMsg;
    public boolean mMmiInitBySTK;
    ArrayList<GsmMmiCode> mPendingMMIs;
    Registrant mPostDialHandler;
    private int mPreferredNetworkType;
    private String mPrlVersion;
    GsmServiceStateTracker mSST;
    SimPhoneBookInterfaceManager mSimPhoneBookIntManager;
    RegistrantList mSsnRegistrants;
    PhoneSubInfo mSubInfo;
    private String mVmNumber;

    static /* synthetic */ class C01021 {
        static final /* synthetic */ int[] $SwitchMap$com$android$internal$telephony$DctConstants$Activity = new int[Activity.values().length];
        static final /* synthetic */ int[] $SwitchMap$com$android$internal$telephony$DctConstants$State = new int[State.values().length];

        static {
            try {
                $SwitchMap$com$android$internal$telephony$DctConstants$Activity[Activity.DATAIN.ordinal()] = 1;
            } catch (NoSuchFieldError e) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$DctConstants$Activity[Activity.DATAOUT.ordinal()] = 2;
            } catch (NoSuchFieldError e2) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$DctConstants$Activity[Activity.DATAINANDOUT.ordinal()] = 3;
            } catch (NoSuchFieldError e3) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$DctConstants$Activity[Activity.DORMANT.ordinal()] = 4;
            } catch (NoSuchFieldError e4) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$DctConstants$State[State.RETRYING.ordinal()] = 1;
            } catch (NoSuchFieldError e5) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$DctConstants$State[State.FAILED.ordinal()] = 2;
            } catch (NoSuchFieldError e6) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$DctConstants$State[State.IDLE.ordinal()] = 3;
            } catch (NoSuchFieldError e7) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$DctConstants$State[State.CONNECTED.ordinal()] = 4;
            } catch (NoSuchFieldError e8) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$DctConstants$State[State.DISCONNECTING.ordinal()] = 5;
            } catch (NoSuchFieldError e9) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$DctConstants$State[State.CONNECTING.ordinal()] = 6;
            } catch (NoSuchFieldError e10) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$DctConstants$State[State.SCANNING.ordinal()] = 7;
            } catch (NoSuchFieldError e11) {
            }
        }
    }

    private static class Cfu {
        final Message mOnComplete;
        final String mSetCfNumber;

        Cfu(String cfNumber, Message onComplete) {
            this.mSetCfNumber = cfNumber;
            this.mOnComplete = onComplete;
        }
    }

    public GSMPhone(Context context, CommandsInterface ci, PhoneNotifier notifier) {
        this(context, ci, notifier, false);
    }

    public GSMPhone(Context context, CommandsInterface ci, PhoneNotifier notifier, boolean unitTestMode) {
        super("GSM", notifier, context, ci, unitTestMode);
        this.mPreferredNetworkType = UNKNOWN_NETWORK_MODE;
        this.mPendingMMIs = new ArrayList();
        this.mSsnRegistrants = new RegistrantList();
        this.mEcmTimerResetRegistrants = new RegistrantList();
        this.mEsn = "0";
        this.mMeid = "0";
        this.mMmiInitBySTK = false;
        this.mMmiErrMsg = null;
        if (ci instanceof SimulatedRadioControl) {
            this.mSimulatedRadioControl = (SimulatedRadioControl) ci;
        }
        this.mCi.setPhoneType(1);
        this.mCT = new GsmCallTracker(this);
        this.mSST = new GsmServiceStateTracker(this);
        this.mDcTracker = new DcTracker(this);
        if (!unitTestMode) {
            this.mSimPhoneBookIntManager = new SimPhoneBookInterfaceManager(this);
            this.mSubInfo = new PhoneSubInfo(this);
        }
        this.mCi.registerForAvailable(this, 1, null);
        this.mCi.registerForOffOrNotAvailable(this, 8, null);
        this.mCi.registerForOn(this, 5, null);
        this.mCi.setOnUSSD(this, 7, null);
        this.mCi.setOnSuppServiceNotification(this, 2, null);
        this.mCi.setOnSS(this, 36, null);
        this.mSST.registerForNetworkAttached(this, 19, null);
        registerForServiceStateChanged(this, CallFailCause.CDMA_ACCESS_FAILURE, null);
        setProperties();
    }

    public GSMPhone(Context context, CommandsInterface ci, PhoneNotifier notifier, int phoneId) {
        this(context, ci, notifier, false, phoneId);
    }

    public GSMPhone(Context context, CommandsInterface ci, PhoneNotifier notifier, boolean unitTestMode, int phoneId) {
        super("GSM", notifier, context, ci, unitTestMode, phoneId);
        this.mPreferredNetworkType = UNKNOWN_NETWORK_MODE;
        this.mPendingMMIs = new ArrayList();
        this.mSsnRegistrants = new RegistrantList();
        this.mEcmTimerResetRegistrants = new RegistrantList();
        this.mEsn = "0";
        this.mMeid = "0";
        this.mMmiInitBySTK = false;
        this.mMmiErrMsg = null;
        if (ci instanceof SimulatedRadioControl) {
            this.mSimulatedRadioControl = (SimulatedRadioControl) ci;
        }
        this.mCi.setPhoneType(1);
        this.mCT = new GsmCallTracker(this);
        if ("AP".equals(CscFeature.getInstance().getString("CscFeature_RIL_ConfigDrivenTypeForCtcMtrIR")) && ("DCGGS".equals("DGG") || "DCGS".equals("DGG"))) {
            this.mSST = new IRGsmServiceStateTracker(this);
        } else {
            this.mSST = new GsmServiceStateTracker(this);
        }
        this.mDcTracker = new DcTracker(this);
        if (!unitTestMode) {
            this.mSimPhoneBookIntManager = new SimPhoneBookInterfaceManager(this);
            this.mSubInfo = new PhoneSubInfo(this);
        }
        this.mCi.registerForAvailable(this, 1, null);
        this.mCi.registerForOffOrNotAvailable(this, 8, null);
        this.mCi.registerForOn(this, 5, null);
        this.mCi.setOnUSSD(this, 7, null);
        this.mCi.setOnSuppServiceNotification(this, 2, null);
        this.mCi.setOnSS(this, 36, null);
        this.mSST.registerForNetworkAttached(this, 19, null);
        setProperties();
        registerForServiceStateChanged(this, CallFailCause.CDMA_ACCESS_FAILURE, null);
        log("GSMPhone: constructor: sub = " + this.mPhoneId);
        setProperties();
    }

    protected void setProperties() {
        TelephonyManager.setTelephonyProperty("gsm.current.phone-type", getSubId(), new Integer(1).toString());
    }

    public void dispose() {
        synchronized (PhoneProxy.lockForRadioTechnologyChange) {
            super.dispose();
            this.mCi.unregisterForAvailable(this);
            unregisterForSimRecordEvents();
            this.mCi.unregisterForOffOrNotAvailable(this);
            this.mCi.unregisterForOn(this);
            this.mSST.unregisterForNetworkAttached(this);
            this.mCi.unSetOnUSSD(this);
            this.mCi.unSetOnSuppServiceNotification(this);
            this.mCi.unSetOnSS(this);
            this.mPendingMMIs.clear();
            this.mCT.dispose();
            this.mDcTracker.dispose();
            this.mSST.dispose();
            this.mSimPhoneBookIntManager.dispose();
            this.mSubInfo.dispose();
            unregisterForServiceStateChanged(this);
        }
    }

    public void removeReferences() {
        Rlog.d(LOG_TAG, "removeReferences");
        this.mSimulatedRadioControl = null;
        this.mSimPhoneBookIntManager = null;
        this.mSubInfo = null;
        this.mCT = null;
        this.mSST = null;
        super.removeReferences();
    }

    protected void finalize() {
        Rlog.d(LOG_TAG, "GSMPhone finalized");
    }

    private void onSubscriptionActivated() {
        log("SUBSCRIPTION ACTIVATED : slotId : " + this.mSubscriptionData.slotId + " appid : " + this.mSubscriptionData.m3gppIndex + " subId : " + this.mSubscriptionData.subId + " subStatus : " + this.mSubscriptionData.subStatus);
        setProperties();
        onUpdateIccAvailability();
        this.mSST.sendMessage(this.mSST.obtainMessage(42));
        ((DcTracker) this.mDcTracker).updateRecords();
        if (this.mSST != null) {
            this.mSST.mSS.setStateOutOfService();
        }
    }

    private void onSubscriptionDeactivated() {
        log("SUBSCRIPTION DEACTIVATED");
        this.mSubscriptionData = null;
        resetSubSpecifics();
    }

    public ServiceState getServiceState() {
        if ((this.mSST == null || this.mSST.mSS.getState() != 0) && this.mImsPhone != null && this.mImsPhone.getServiceState().getState() == 0) {
            return this.mImsPhone.getServiceState();
        }
        if (this.mSST != null) {
            return this.mSST.mSS;
        }
        return new ServiceState();
    }

    public CellLocation getCellLocation() {
        return this.mSST.getCellLocation();
    }

    public PhoneConstants.State getState() {
        return this.mCT.mState;
    }

    public int getPhoneType() {
        return 1;
    }

    public ServiceStateTracker getServiceStateTracker() {
        return this.mSST;
    }

    public CallTracker getCallTracker() {
        return this.mCT;
    }

    public List<? extends MmiCode> getPendingMmiCodes() {
        return this.mPendingMMIs;
    }

    public DataState getDataConnectionState(String apnType) {
        DataState ret = DataState.DISCONNECTED;
        if (this.mSST == null) {
            return DataState.DISCONNECTED;
        }
        if (!apnType.equals("emergency") && this.mSST.getCurrentDataConnectionState() != 0 && !"SBM".equals("")) {
            return DataState.DISCONNECTED;
        }
        if (!this.mDcTracker.isApnTypeEnabled(apnType) || !this.mDcTracker.isApnTypeActive(apnType)) {
            return DataState.DISCONNECTED;
        }
        int simSlot;
        boolean isOtherPhoneCalling = false;
        boolean isRoaming = false;
        if ("CG".equals("DGG") || "GG".equals("DGG")) {
            for (simSlot = 0; simSlot < TelephonyManager.getDefault().getPhoneCount(); simSlot++) {
                if (simSlot != SubscriptionController.getInstance().getSlotId(SubscriptionController.getInstance().getDefaultDataSubId())) {
                    try {
                        long[] subId = SubscriptionController.getInstance().getSubId(simSlot);
                        log("getDataConnectionState() isNetworkRoaming(subId[0])= " + TelephonyManager.getDefault().isNetworkRoaming(subId[0]) + ", subId[0]=" + subId[0]);
                        if (TelephonyManager.getDefault().isNetworkRoaming(subId[0])) {
                            isRoaming = true;
                            log("getDataConnectionState() isRoaming is true, simSlot= " + simSlot);
                            break;
                        }
                    } catch (Exception e) {
                    }
                }
            }
        }
        if ((!"dsda".equals(SystemProperties.get("persist.radio.multisim.config")) || (!"GG".equals("DGG") && (!"CG".equals("DGG") || isRoaming))) && !"DGG".equals("DGG") && !"DCGS".equals("DGG") && !"DCGGS".equals("DGG") && !"DCGG".equals("DGG") && SubscriptionController.getInstance().getActiveSubInfoCount() > 1) {
            for (simSlot = 0; simSlot < SubscriptionController.getInstance().getActiveSubInfoCount(); simSlot++) {
                if (simSlot != SubscriptionController.getInstance().getSlotId(SubscriptionController.getInstance().getDefaultDataSubId())) {
                    try {
                        if (TelephonyManager.getDefault().getCallState(SubscriptionController.getInstance().getSubId(simSlot)[0]) != 0) {
                            isOtherPhoneCalling = true;
                            break;
                        }
                    } catch (Exception e2) {
                    }
                }
            }
        }
        switch (C01021.$SwitchMap$com$android$internal$telephony$DctConstants$State[this.mDcTracker.getState(apnType).ordinal()]) {
            case 1:
            case 2:
            case 3:
                return DataState.DISCONNECTED;
            case 4:
            case 5:
                if (isOtherPhoneCalling || (("default".equals(apnType) && this.mSST.needBlockData()) || (this.mCT.mState != PhoneConstants.State.IDLE && !this.mSST.isConcurrentVoiceAndDataAllowed(apnType)))) {
                    return DataState.SUSPENDED;
                }
                return DataState.CONNECTED;
            case 6:
            case 7:
                return DataState.CONNECTING;
            default:
                return ret;
        }
    }

    public DataActivityState getDataActivityState() {
        DataActivityState ret = DataActivityState.NONE;
        if (this.mSST.getCurrentDataConnectionState() != 0) {
            return ret;
        }
        switch (C01021.$SwitchMap$com$android$internal$telephony$DctConstants$Activity[this.mDcTracker.getActivity().ordinal()]) {
            case 1:
                return DataActivityState.DATAIN;
            case 2:
                return DataActivityState.DATAOUT;
            case 3:
                return DataActivityState.DATAINANDOUT;
            case 4:
                return DataActivityState.DORMANT;
            default:
                return DataActivityState.NONE;
        }
    }

    void notifyPhoneStateChanged() {
        this.mNotifier.notifyPhoneState(this);
    }

    void notifyPreciseCallStateChanged() {
        super.notifyPreciseCallStateChangedP();
    }

    public void notifyNewRingingConnection(Connection c) {
        super.notifyNewRingingConnectionP(c);
    }

    void notifyDisconnect(Connection cn) {
        this.mDisconnectRegistrants.notifyResult(cn);
        this.mNotifier.notifyDisconnectCause(cn.getDisconnectCause(), cn.getPreciseDisconnectCause());
    }

    void notifyUnknownConnection(Connection cn) {
        this.mUnknownConnectionRegistrants.notifyResult(cn);
    }

    void notifySuppServiceFailed(SuppService code) {
        this.mSuppServiceFailedRegistrants.notifyResult(code);
    }

    void notifyServiceStateChanged(ServiceState ss) {
        super.notifyServiceStateChangedP(ss);
    }

    void notifyLocationChanged() {
        this.mNotifier.notifyCellLocation(this);
    }

    public void notifyCallForwardingIndicator() {
        this.mNotifier.notifyCallForwardingChanged(this);
    }

    public void setSystemProperty(String property, String value) {
        TelephonyManager.setTelephonyProperty(property, getSubId(), value);
    }

    public void registerForSuppServiceNotification(Handler h, int what, Object obj) {
        this.mSsnRegistrants.addUnique(h, what, obj);
        if (this.mSsnRegistrants.size() == 1) {
            this.mCi.setSuppServiceNotifications(true, null);
        }
    }

    public void unregisterForSuppServiceNotification(Handler h) {
        this.mSsnRegistrants.remove(h);
        if (this.mSsnRegistrants.size() == 0) {
            this.mCi.setSuppServiceNotifications(false, null);
        }
    }

    public void registerForSimRecordsLoaded(Handler h, int what, Object obj) {
        this.mSimRecordsLoadedRegistrants.addUnique(h, what, obj);
    }

    public void unregisterForSimRecordsLoaded(Handler h) {
        this.mSimRecordsLoadedRegistrants.remove(h);
    }

    public void acceptCall(int videoState) throws CallStateException {
        ImsPhone imsPhone = this.mImsPhone;
        if (imsPhone == null || !imsPhone.getRingingCall().isRinging()) {
            this.mCT.acceptCall(videoState);
        } else {
            imsPhone.acceptCall(videoState);
        }
    }

    public void rejectCall() throws CallStateException {
        this.mCT.rejectCall();
    }

    public void switchHoldingAndActive() throws CallStateException {
        this.mCT.switchWaitingOrHoldingAndActive();
    }

    public boolean canConference() {
        boolean canImsConference = false;
        if (this.mImsPhone != null) {
            canImsConference = this.mImsPhone.canConference();
        }
        return this.mCT.canConference() || canImsConference;
    }

    public boolean canDial() {
        return this.mCT.canDial();
    }

    public void conference() {
        if (this.mImsPhone == null || !this.mImsPhone.canConference()) {
            this.mCT.conference();
            return;
        }
        log("conference() - delegated to IMS phone");
        this.mImsPhone.conference();
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

    public GsmCall getForegroundCall() {
        return this.mCT.mForegroundCall;
    }

    public GsmCall getBackgroundCall() {
        return this.mCT.mBackgroundCall;
    }

    public Call getRingingCall() {
        ImsPhone imsPhone = this.mImsPhone;
        if (this.mCT.mRingingCall != null && this.mCT.mRingingCall.isRinging()) {
            return this.mCT.mRingingCall;
        }
        if (imsPhone != null) {
            return imsPhone.getRingingCall();
        }
        return this.mCT.mRingingCall;
    }

    private boolean handleCallDeflectionIncallSupplementaryService(String dialString) {
        if (dialString.length() > 1) {
            return false;
        }
        if (getRingingCall().getState() != Call.State.IDLE) {
            Rlog.d(LOG_TAG, "MmiCode 0: rejectCall");
            try {
                this.mCT.rejectCall();
                return true;
            } catch (CallStateException e) {
                Rlog.d(LOG_TAG, "reject failed", e);
                notifySuppServiceFailed(SuppService.REJECT);
                return true;
            }
        } else if (getBackgroundCall().getState() == Call.State.IDLE) {
            return true;
        } else {
            Rlog.d(LOG_TAG, "MmiCode 0: hangupWaitingOrBackground");
            this.mCT.hangupWaitingOrBackground();
            return true;
        }
    }

    private boolean handleCallWaitingIncallSupplementaryService(String dialString) {
        int len = dialString.length();
        if (len > 2) {
            return false;
        }
        GsmCall call = getForegroundCall();
        if (len > 1) {
            try {
                int callIndex = dialString.charAt(1) - 48;
                if (callIndex < 1 || callIndex > 7) {
                    return true;
                }
                Rlog.d(LOG_TAG, "MmiCode 1: hangupConnectionByIndex " + callIndex);
                this.mCT.hangupConnectionByIndex(call, callIndex);
                return true;
            } catch (CallStateException e) {
                Rlog.d(LOG_TAG, "hangup failed", e);
                notifySuppServiceFailed(SuppService.HANGUP);
                return true;
            }
        } else if (call.getState() != Call.State.IDLE) {
            Rlog.d(LOG_TAG, "MmiCode 1: hangup foreground");
            if ("KTT".equals("")) {
                this.mCT.hangupForegroundResumeBackground();
                return true;
            }
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
        GsmCall call = getForegroundCall();
        if (len > 1) {
            try {
                int callIndex = dialString.charAt(1) - 48;
                GsmConnection conn = this.mCT.getConnectionByIndex(call, callIndex);
                if (conn == null || callIndex < 1 || callIndex > 7) {
                    Rlog.d(LOG_TAG, "separate: invalid call index " + callIndex);
                    notifySuppServiceFailed(SuppService.SEPARATE);
                    return true;
                }
                Rlog.d(LOG_TAG, "MmiCode 2: separate call " + callIndex);
                this.mCT.separate(conn);
                return true;
            } catch (CallStateException e) {
                Rlog.d(LOG_TAG, "separate failed", e);
                notifySuppServiceFailed(SuppService.SEPARATE);
                return true;
            }
        }
        try {
            if (getRingingCall().getState() != Call.State.IDLE) {
                Rlog.d(LOG_TAG, "MmiCode 2: accept ringing call");
                this.mCT.acceptCall();
                return true;
            }
            Rlog.d(LOG_TAG, "MmiCode 2: switchWaitingOrHoldingAndActive");
            this.mCT.switchWaitingOrHoldingAndActive();
            return true;
        } catch (CallStateException e2) {
            Rlog.d(LOG_TAG, "switch failed", e2);
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
        Rlog.d(LOG_TAG, "MmiCode 4: explicit call transfer");
        explicitCallTransfer();
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

    public boolean handleInCallMmiCommands(String dialString) throws CallStateException {
        ImsPhone imsPhone = this.mImsPhone;
        if (imsPhone != null && imsPhone.getServiceState().getState() == 0) {
            return imsPhone.handleInCallMmiCommands(dialString);
        }
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

    public Connection dial(String dialString, int videoState) throws CallStateException {
        return dial(dialString, null, videoState);
    }

    public Connection dial(String dialString, UUSInfo uusInfo, int videoState) throws CallStateException {
        return dial(dialString, uusInfo, videoState, 0, 1, null);
    }

    public Connection dial(String dialString, UUSInfo uusInfo, int videoState, int callType, int callDomain, String[] extras) throws CallStateException {
        ImsPhone imsPhone = this.mImsPhone;
        boolean imsUseEnabled = ImsManager.isEnhanced4gLteModeSettingEnabledByPlatform(this.mContext) && ImsManager.isEnhanced4gLteModeSettingEnabledByUser(this.mContext);
        if (!imsUseEnabled) {
            Rlog.w(LOG_TAG, "IMS is disabled: forced to CS");
        }
        if (imsUseEnabled && imsPhone != null && ((imsPhone.getServiceState().getState() == 0 && !PhoneNumberUtils.isEmergencyNumber(dialString)) || (PhoneNumberUtils.isEmergencyNumber(dialString) && this.mContext.getResources().getBoolean(17956981)))) {
            try {
                Rlog.d(LOG_TAG, "Trying IMS PS call");
                return imsPhone.dial(dialString, uusInfo, videoState, callType, callDomain, extras);
            } catch (CallStateException e) {
                Rlog.d(LOG_TAG, "IMS PS call exception " + e);
                if (!ImsPhone.CS_FALLBACK.equals(e.getMessage())) {
                    CallStateException ce = new CallStateException(e.getMessage());
                    ce.setStackTrace(e.getStackTrace());
                    throw ce;
                }
            }
        }
        Rlog.d(LOG_TAG, "Trying (non-IMS) CS call");
        return dialInternal(dialString, uusInfo, 0, callType, callDomain, extras);
    }

    protected Connection dialInternal(String dialString, UUSInfo uusInfo, int videoState) throws CallStateException {
        return dialInternal(dialString, uusInfo, videoState, 0, 1, null);
    }

    protected Connection dialInternal(String dialString, UUSInfo uusInfo, int videoState, int callType, int callDomain, String[] extras) throws CallStateException {
        String newDialString;
        GsmMmiCode mmi = null;
        CallDetails callDetails = new CallDetails(callType, callDomain, extras);
        if (callDetails == null || !"unknown".equals(callDetails.getExtraValue("participants"))) {
            newDialString = PhoneNumberUtils.stripSeparators(dialString);
        } else {
            newDialString = dialString;
        }
        newDialString = processUkCliPrefix(newDialString);
        String ECN_PREFS_NAME = "com.android.phone.emergency_call_notification_pref";
        String USSD_KEY = "ecn_ussd";
        String ECN_SENT_KEY = "ecn_sent";
        SharedPreferences prefs = getContext().getSharedPreferences("com.android.phone.emergency_call_notification_pref", 0);
        String ecnUSSD = prefs.getString("ecn_ussd", "");
        boolean ecn = prefs.getBoolean("ecn_sent", false);
        if (!("LGT".equals("") && callDetails.call_domain == 2)) {
            mmi = GsmMmiCode.newFromDialString(PhoneNumberUtils.extractNetworkPortionAlt(newDialString), this, (UiccCardApplication) this.mUiccApplication.get());
        }
        if (handleInCallMmiCommands(newDialString)) {
            return null;
        }
        Rlog.d(LOG_TAG, "dialing w/ mmi '" + mmi + "'...");
        if (mmi == null) {
            return this.mCT.dial(newDialString, 0, uusInfo, callDetails);
        }
        if (mmi.isTemporaryModeCLIR()) {
            return this.mCT.dial(mmi.mDialingNumber, mmi.getCLIRMode(), uusInfo, callDetails);
        }
        if (mmi.IsUssdConnectToCall()) {
            Rlog.d(LOG_TAG, "Ussd Connects to call");
            return this.mCT.dial(newDialString, 0, uusInfo, callDetails);
        }
        this.mPendingMMIs.add(mmi);
        this.mMmiRegistrants.notifyRegistrants(new AsyncResult(null, mmi, null));
        mmi.processCode();
        return null;
    }

    public boolean handlePinMmi(String dialString) {
        GsmMmiCode mmi = GsmMmiCode.newFromDialString(dialString, this, (UiccCardApplication) this.mUiccApplication.get());
        if (mmi == null || !mmi.isPinPukCommand()) {
            return false;
        }
        this.mPendingMMIs.add(mmi);
        this.mMmiRegistrants.notifyRegistrants(new AsyncResult(null, mmi, null));
        mmi.processCode();
        return true;
    }

    public void sendUssdResponse(String ussdMessge) {
        GsmMmiCode mmi = GsmMmiCode.newFromUssdUserInput(ussdMessge, this, (UiccCardApplication) this.mUiccApplication.get());
        this.mPendingMMIs.add(mmi);
        this.mMmiRegistrants.notifyRegistrants(new AsyncResult(null, mmi, null));
        mmi.sendUssd(ussdMessge);
    }

    public void sendDtmf(char c) {
        if (!PhoneNumberUtils.is12Key(c)) {
            Rlog.e(LOG_TAG, "sendDtmf called with invalid character '" + c + "'");
        } else if (this.mCT.mState == PhoneConstants.State.OFFHOOK) {
            this.mCi.sendDtmf(c, null);
        }
    }

    public void startDtmf(char c) {
        if (PhoneNumberUtils.is12Key(c)) {
            this.mCi.startDtmf(c, null);
        } else {
            Rlog.e(LOG_TAG, "startDtmf called with invalid character '" + c + "'");
        }
    }

    public void stopDtmf() {
        this.mCi.stopDtmf(null);
    }

    public void sendBurstDtmf(String dtmfString) {
        Rlog.e(LOG_TAG, "[GSMPhone] sendBurstDtmf() is a CDMA method");
    }

    public void setRadioPower(boolean power) {
        this.mSST.setRadioPower(power);
    }

    private void storeVoiceMailNumber(String number) {
        Editor editor = PreferenceManager.getDefaultSharedPreferences(getContext()).edit();
        editor.putString(VM_NUMBER + getPhoneId(), number);
        editor.apply();
        setVmSimImsi(getSubscriberId());
    }

    public String getVoiceMailNumber() {
        IccRecords r = (IccRecords) this.mIccRecords.get();
        String number = r != null ? r.getVoiceMailNumber() : "";
        if (TextUtils.isEmpty(number)) {
            SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(getContext());
            number = sp.getString(VM_NUMBER + getPhoneId(), null);
            if (TextUtils.isEmpty(number)) {
                if (getPhoneId() == 1) {
                    number = sp.getString("vm_number_key2", null);
                } else {
                    number = sp.getString(VM_NUMBER, null);
                }
            }
        }
        if (!TextUtils.isEmpty(number)) {
            return number;
        }
        String[] listArray = getContext().getResources().getStringArray(17236026);
        if (listArray == null || listArray.length <= 0) {
            return number;
        }
        for (int i = 0; i < listArray.length; i++) {
            if (!TextUtils.isEmpty(listArray[i])) {
                String[] defaultVMNumberArray = listArray[i].split(";");
                if (defaultVMNumberArray != null && defaultVMNumberArray.length > 0) {
                    if (defaultVMNumberArray.length == 1) {
                        number = defaultVMNumberArray[0];
                    } else if (defaultVMNumberArray.length == 2 && !TextUtils.isEmpty(defaultVMNumberArray[1]) && defaultVMNumberArray[1].equalsIgnoreCase(getGroupIdLevel1())) {
                        return defaultVMNumberArray[0];
                    }
                }
            }
        }
        return number;
    }

    private String getVmSimImsi() {
        return PreferenceManager.getDefaultSharedPreferences(getContext()).getString(VM_SIM_IMSI + getPhoneId(), null);
    }

    private void setVmSimImsi(String imsi) {
        Editor editor = PreferenceManager.getDefaultSharedPreferences(getContext()).edit();
        editor.putString(VM_SIM_IMSI + getPhoneId(), imsi);
        editor.apply();
    }

    public String getVoiceMailAlphaTag() {
        IccRecords r = (IccRecords) this.mIccRecords.get();
        String ret = r != null ? r.getVoiceMailAlphaTag() : "";
        if (ret == null || ret.length() == 0) {
            return this.mContext.getText(17039364).toString();
        }
        return ret;
    }

    public String getDeviceId() {
        return this.mImei;
    }

    public String getDeviceSvn() {
        return this.mImeiSv;
    }

    public IsimRecords getIsimRecords() {
        return this.mIsimUiccRecords;
    }

    public String getImei() {
        return this.mImei;
    }

    public String getEsn() {
        Rlog.e(LOG_TAG, "[GSMPhone] getEsn() is a CDMA method");
        return "0";
    }

    public String getRuimid() {
        return SystemProperties.get("ril.cdma.phone.id", "");
    }

    public String getMeid() {
        Rlog.e(LOG_TAG, "[GSMPhone] getMeid() is a CDMA method");
        return "0";
    }

    public String getSubscriberId() {
        IccRecords r = (IccRecords) this.mIccRecords.get();
        return r != null ? r.getIMSI() : null;
    }

    public String getGroupIdLevel1() {
        IccRecords r = (IccRecords) this.mIccRecords.get();
        return r != null ? r.getGid1() : null;
    }

    public String getLine1Number() {
        IccRecords r = (IccRecords) this.mIccRecords.get();
        if ("DCG".equals("DGG") || "DCGG".equals("DGG") || "DCGS".equals("DGG") || "DCGGS".equals("DGG") || "CG".equals("DGG")) {
            log("getLine1Number");
            if (getPhoneId() == 0) {
                UiccCardApplication cdmaUiccApp = this.mUiccController.getUiccCardApplication(2);
                if (cdmaUiccApp != null) {
                    IccRecords mRuimRecords = cdmaUiccApp.getIccRecords();
                    if (mRuimRecords != null) {
                        RuimRecords ruim = (RuimRecords) mRuimRecords;
                        if (ruim != null) {
                            return ruim.getMdnNumber();
                        }
                    }
                }
            } else if (r == null) {
                return "";
            } else {
                log("second");
                return ((SIMRecords) r).getMsisdnNumber();
            }
        }
        return r != null ? r.getMsisdnNumber() : null;
    }

    public String getMsisdn() {
        IccRecords r = (IccRecords) this.mIccRecords.get();
        return r != null ? r.getMsisdnNumber() : null;
    }

    public String getLine1AlphaTag() {
        IccRecords r = (IccRecords) this.mIccRecords.get();
        return r != null ? r.getMsisdnAlphaTag() : null;
    }

    public boolean hasIsim() {
        IccRecords r = (IccRecords) this.mIccRecords.get();
        return r != null ? r.hasIsim() : false;
    }

    public byte[] getPsismsc() {
        IccRecords r = (IccRecords) this.mIccRecords.get();
        return r != null ? r.getPsismsc() : null;
    }

    public void setLine1Number(String alphaTag, String number, Message onComplete) {
        IccRecords r = (IccRecords) this.mIccRecords.get();
        if (r != null) {
            r.setMsisdnNumber(alphaTag, number, onComplete);
        }
    }

    public void setVoiceMailNumber(String alphaTag, String voiceMailNumber, Message onComplete) {
        this.mVmNumber = voiceMailNumber;
        Message resp = obtainMessage(20, 0, 0, onComplete);
        IccRecords r = (IccRecords) this.mIccRecords.get();
        if (r != null) {
            r.setVoiceMailNumber(alphaTag, this.mVmNumber, resp);
        }
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

    public String getSystemProperty(String property, String defValue) {
        if (getUnitTestMode()) {
            return null;
        }
        return TelephonyManager.getTelephonyProperty(property, getSubId(), defValue);
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

    public void updateDataConnectionTracker() {
        ((DcTracker) this.mDcTracker).update();
    }

    public String getCdmaPrlVersion() {
        if ("CTC".equals(SystemProperties.get("ro.csc.sales_code", "NONE"))) {
            if (SystemProperties.get("gsm.sim.state", "").equals("ABSENT") || (this.mPrlVersion != null && "1".equals(this.mPrlVersion))) {
                return "default";
            }
            log("mPrlVersion_gsmphone_3 =" + this.mPrlVersion);
        }
        return this.mPrlVersion;
    }

    protected boolean isCfEnable(int action) {
        return action == 1 || action == 3;
    }

    public void getCallForwardingOption(int commandInterfaceCFReason, String dialingNumber, int serviceClass, Message onComplete) {
        ImsPhone imsPhone = this.mImsPhone;
        if (imsPhone != null && imsPhone.getServiceState().getState() == 0) {
            imsPhone.getCallForwardingOption(commandInterfaceCFReason, dialingNumber, serviceClass, onComplete);
        } else if (isValidCommandInterfaceCFReason(commandInterfaceCFReason)) {
            Message resp;
            Rlog.d(LOG_TAG, "requesting call forwarding query.");
            if (commandInterfaceCFReason == 0) {
                resp = obtainMessage(13, onComplete);
            } else {
                resp = onComplete;
            }
            this.mCi.queryCallForwardStatus(commandInterfaceCFReason, serviceClass, dialingNumber, resp);
        }
    }

    public void setCallForwardingOption(int commandInterfaceCFAction, int commandInterfaceCFReason, String dialingNumber, int timerSeconds, int serviceClass, Message onComplete) {
        ImsPhone imsPhone = this.mImsPhone;
        if (imsPhone != null && imsPhone.getServiceState().getState() == 0) {
            imsPhone.setCallForwardingOption(commandInterfaceCFAction, commandInterfaceCFReason, dialingNumber, timerSeconds, serviceClass, onComplete);
        } else if (isValidCommandInterfaceCFAction(commandInterfaceCFAction) && isValidCommandInterfaceCFReason(commandInterfaceCFReason)) {
            Message resp;
            if (commandInterfaceCFReason == 0) {
                int i;
                Cfu cfu = new Cfu(dialingNumber, onComplete);
                if (isCfEnable(commandInterfaceCFAction)) {
                    i = 1;
                } else {
                    i = 0;
                }
                resp = obtainMessage(12, i, serviceClass, cfu);
            } else {
                resp = onComplete;
            }
            this.mCi.setCallForward(commandInterfaceCFAction, commandInterfaceCFReason, serviceClass, dialingNumber, timerSeconds, resp);
        }
    }

    public void getOutgoingCallerIdDisplay(Message onComplete) {
        this.mCi.getCLIR(onComplete);
    }

    public void setOutgoingCallerIdDisplay(int commandInterfaceCLIRMode, Message onComplete) {
        this.mCi.setCLIR(commandInterfaceCLIRMode, obtainMessage(18, commandInterfaceCLIRMode, 0, onComplete));
    }

    public void getCallWaiting(Message onComplete) {
        ImsPhone imsPhone = this.mImsPhone;
        if (imsPhone == null || imsPhone.getServiceState().getState() != 0) {
            this.mCi.queryCallWaiting(0, onComplete);
        } else {
            imsPhone.getCallWaiting(onComplete);
        }
    }

    public void setCallWaiting(boolean enable, Message onComplete) {
        ImsPhone imsPhone = this.mImsPhone;
        if (imsPhone == null || imsPhone.getServiceState().getState() != 0) {
            this.mCi.setCallWaiting(enable, 1, onComplete);
        } else {
            imsPhone.setCallWaiting(enable, onComplete);
        }
    }

    public boolean hasCall(String callType) {
        if ("video".equals(callType)) {
            return getCallTracker().mHasVideoCall;
        }
        if ("volte".equals(callType)) {
            return getCallTracker().mHasVolteCall;
        }
        if ("epdg".equals(callType)) {
            return getCallTracker().mHasEpdgCall;
        }
        return false;
    }

    public void getAvailableNetworks(Message response) {
        this.mCi.getAvailableNetworks(obtainMessage(100, response));
    }

    public void setLteBandMode(int bandMode, Message response) {
        this.mCi.setLteBandMode(bandMode, response);
    }

    public void getNeighboringCids(Message response) {
        this.mCi.getNeighboringCids(response);
    }

    public void setOnPostDialCharacter(Handler h, int what, Object obj) {
        this.mPostDialHandler = new Registrant(h, what, obj);
    }

    public void setMute(boolean muted) {
        this.mCT.setMute(muted);
    }

    public boolean getMute() {
        return this.mCT.getMute();
    }

    public void getDataCallList(Message response) {
        this.mCi.getDataCallList(response);
    }

    public void updateServiceLocation() {
        this.mSST.enableSingleLocationUpdate();
    }

    public void enableLocationUpdates() {
        this.mSST.enableLocationUpdates();
    }

    public void disableLocationUpdates() {
        this.mSST.disableLocationUpdates();
    }

    public boolean getDataRoamingEnabled() {
        return this.mDcTracker.getDataOnRoamingEnabled();
    }

    public void setDataRoamingEnabled(boolean enable) {
        int uid = Binder.getCallingUid();
        if (uid == CallFailCause.CDMA_DROP || uid == 1000) {
            this.mDcTracker.setDataOnRoamingEnabled(enable);
            return;
        }
        throw new SecurityException("Security Exception Occurred. Only SYSTEM app can change Data Roaming");
    }

    public String getSelectedApn() {
        return this.mDcTracker.getSelectedApn();
    }

    public void setSelectedApn() {
        this.mDcTracker.setSelectedApn();
    }

    public boolean getDataEnabled() {
        return this.mDcTracker.getDataEnabled();
    }

    public void setDataEnabled(boolean enable) {
        this.mDcTracker.setDataEnabled(enable);
    }

    void onMMIDone(GsmMmiCode mmi) {
        if (this.mPendingMMIs.remove(mmi) || mmi.isUssdRequest() || mmi.isSsInfo()) {
            if (getMmiErrorMsg() != null) {
                if ("true".equals(SystemProperties.get("persist.radio.ss.fallback", "false"))) {
                    log("Ignore mmi error message from ims network. For SS CS fallback");
                } else {
                    mmi.mMessage = getMmiErrorMsg();
                    log("mmi error message from ims network is set to " + getMmiErrorMsg());
                }
                setMmiErrorMsg(null);
            }
            if (!this.mMmiInitBySTK) {
                this.mMmiCompleteRegistrants.notifyRegistrants(new AsyncResult(null, mmi, null));
            }
        }
        this.mMmiInitBySTK = false;
    }

    private void onNetworkInitiatedUssd(GsmMmiCode mmi) {
        this.mMmiCompleteRegistrants.notifyRegistrants(new AsyncResult(null, mmi, null));
    }

    private void onIncomingUSSD(int ussdMode, String ussdMessage) {
        boolean isUssdRequest;
        boolean isUssdError = true;
        if (ussdMode == 1) {
            isUssdRequest = true;
        } else {
            isUssdRequest = false;
        }
        if (ussdMode == 0 || ussdMode == 1) {
            isUssdError = false;
        }
        GsmMmiCode found = null;
        int s = this.mPendingMMIs.size();
        for (int i = 0; i < s; i++) {
            if (((GsmMmiCode) this.mPendingMMIs.get(i)).isPendingUSSD()) {
                found = (GsmMmiCode) this.mPendingMMIs.get(i);
                break;
            }
        }
        if (found == null) {
            int UssdError = SystemProperties.getInt("ril.ussd.notdone", 0);
            if (this.mMmiInitBySTK && UssdError == 0) {
                this.mMmiInitBySTK = false;
            } else if (!isUssdError && ussdMessage != null) {
                onNetworkInitiatedUssd(GsmMmiCode.newNetworkInitiatedUssd(ussdMessage, isUssdRequest, this, (UiccCardApplication) this.mUiccApplication.get()));
            }
        } else if (this.mMmiInitBySTK) {
            this.mPendingMMIs.remove(found);
            this.mMmiInitBySTK = false;
        } else if (isUssdError) {
            found.onUssdFinishedError();
        } else {
            found.onUssdFinished(ussdMessage, isUssdRequest);
        }
    }

    protected void syncClirSetting() {
        int clirSetting = PreferenceManager.getDefaultSharedPreferences(getContext()).getInt(PhoneBase.CLIR_KEY + getPhoneId(), -1);
        if (clirSetting >= 0) {
            this.mCi.setCLIR(clirSetting, null);
        }
    }

    public void handleMessage(Message msg) {
        if (this.mIsTheCurrentActivePhone) {
            AsyncResult ar;
            int slotId;
            boolean z;
            Message onComplete;
            switch (msg.what) {
                case 1:
                    this.mCi.getBasebandVersion(obtainMessage(6));
                    this.mCi.getIMEI(obtainMessage(9));
                    this.mCi.getIMEISV(obtainMessage(10));
                    return;
                case 2:
                    ar = (AsyncResult) msg.obj;
                    SuppServiceNotification not = ar.result;
                    this.mSsnRegistrants.notifyRegistrants(ar);
                    return;
                case 3:
                    updateCurrentCarrierInProvider();
                    String imsi = getVmSimImsi();
                    String imsiFromSIM = getSubscriberId();
                    if (!(imsi == null || imsiFromSIM == null || imsiFromSIM.equals(imsi))) {
                        storeVoiceMailNumber(null);
                        setVmSimImsi(null);
                    }
                    this.mSimRecordsLoadedRegistrants.notifyRegistrants();
                    updateVoiceMail();
                    return;
                case 5:
                    if (("DGG".equals("DGG") || "DCG".equals("DGG") || "DCGG".equals("DGG") || "DCGS".equals("DGG") || "DCGGS".equals("DGG")) && "2".equals(getSystemProperty("gsm.sim.active", "0"))) {
                        slotId = SubscriptionController.getInstance().getSlotId(getSubId());
                        if (slotId < 0) {
                            Rlog.e(LOG_TAG, "EVENT_RADIO_ON: invalid slotId:" + slotId);
                            return;
                        } else {
                            notifyCardStateChanged(slotId, 1);
                            return;
                        }
                    }
                    return;
                case 6:
                    ar = msg.obj;
                    if (ar.exception == null) {
                        Rlog.d(LOG_TAG, "Baseband version: " + ar.result);
                        setSystemProperty("gsm.version.baseband", (String) ar.result);
                        return;
                    }
                    return;
                case 7:
                    String[] ussdResult = (String[]) ((AsyncResult) msg.obj).result;
                    if (ussdResult.length > 1) {
                        try {
                            onIncomingUSSD(Integer.parseInt(ussdResult[0]), ussdResult[1]);
                            return;
                        } catch (NumberFormatException e) {
                            Rlog.w(LOG_TAG, "error parsing USSD");
                            return;
                        }
                    }
                    return;
                case 8:
                    for (int i = this.mPendingMMIs.size() - 1; i >= 0; i--) {
                        if (((GsmMmiCode) this.mPendingMMIs.get(i)).isPendingUSSD()) {
                            ((GsmMmiCode) this.mPendingMMIs.get(i)).onUssdFinishedError();
                        }
                    }
                    ImsPhone imsPhone = this.mImsPhone;
                    if (imsPhone != null) {
                        imsPhone.getServiceState().setStateOff();
                    }
                    if (("DGG".equals("DGG") || "DCG".equals("DGG") || "DCGG".equals("DGG") || "DCGS".equals("DGG") || "DCGGS".equals("DGG")) && "1".equals(getSystemProperty("gsm.sim.active", "0"))) {
                        slotId = SubscriptionController.getInstance().getSlotId(getSubId());
                        if (slotId < 0) {
                            Rlog.e(LOG_TAG, "EVENT_RADIO_OFF_OR_NOT_AVAILABLE: invalid slotId:" + slotId);
                            return;
                        } else {
                            notifyCardStateChanged(slotId, 0);
                            return;
                        }
                    }
                    return;
                case 9:
                    ar = (AsyncResult) msg.obj;
                    if (ar.exception == null) {
                        this.mImei = (String) ar.result;
                        return;
                    }
                    return;
                case 10:
                    ar = (AsyncResult) msg.obj;
                    if (ar.exception == null) {
                        this.mImeiSv = (String) ar.result;
                        return;
                    }
                    return;
                case 12:
                    ar = (AsyncResult) msg.obj;
                    IccRecords r = (IccRecords) this.mIccRecords.get();
                    Cfu cfu = ar.userObj;
                    if (ar.exception == null && r != null) {
                        if ((msg.arg2 & 1) != 0) {
                            r.setVoiceCallForwardingFlag(1, msg.arg1 == 1, cfu.mSetCfNumber);
                        }
                        if ((msg.arg2 & 16) != 0) {
                            r.setVideoCallForwardingFlag(1, msg.arg1 == 1, cfu.mSetCfNumber);
                        }
                        if (msg.arg2 == 0) {
                            Boolean valueOf = Boolean.valueOf(msg.arg1 == 1);
                            if (msg.arg1 == 1) {
                                z = true;
                            } else {
                                z = false;
                            }
                            r.setCallForwardingFlag(1, valueOf, Boolean.valueOf(z), cfu.mSetCfNumber);
                        }
                    }
                    if (cfu.mOnComplete != null) {
                        AsyncResult.forMessage(cfu.mOnComplete, ar.result, ar.exception);
                        cfu.mOnComplete.sendToTarget();
                        return;
                    }
                    return;
                case 13:
                    ar = (AsyncResult) msg.obj;
                    if (ar.exception == null) {
                        handleCfuQueryResult((CallForwardInfo[]) ar.result);
                    }
                    onComplete = (Message) ar.userObj;
                    if (onComplete != null) {
                        AsyncResult.forMessage(onComplete, ar.result, ar.exception);
                        onComplete.sendToTarget();
                        return;
                    }
                    return;
                case 18:
                    ar = (AsyncResult) msg.obj;
                    if (ar.exception == null) {
                        saveClirSetting(msg.arg1);
                    }
                    onComplete = (Message) ar.userObj;
                    if (onComplete != null) {
                        AsyncResult.forMessage(onComplete, ar.result, ar.exception);
                        onComplete.sendToTarget();
                        return;
                    }
                    return;
                case 19:
                    syncClirSetting();
                    return;
                case 20:
                    ar = (AsyncResult) msg.obj;
                    if (IccVmNotSupportedException.class.isInstance(ar.exception)) {
                        storeVoiceMailNumber(this.mVmNumber);
                        ar.exception = null;
                    }
                    onComplete = ar.userObj;
                    if (onComplete != null) {
                        AsyncResult.forMessage(onComplete, ar.result, ar.exception);
                        onComplete.sendToTarget();
                        return;
                    }
                    return;
                case 21:
                    ar = (AsyncResult) msg.obj;
                    if (ar.exception == null) {
                        String[] respId = (String[]) ar.result;
                        this.mImei = respId[0];
                        this.mImeiSv = respId[1];
                        this.mEsn = respId[2];
                        this.mMeid = respId[3];
                        return;
                    }
                    return;
                case WspTypeDecoder.WSP_HEADER_LOCATION /*28*/:
                    ar = (AsyncResult) msg.obj;
                    if (this.mSST.mSS.getIsManualSelection()) {
                        setNetworkSelectionModeAutomatic((Message) ar.result);
                        Rlog.d(LOG_TAG, "SET_NETWORK_SELECTION_AUTOMATIC: set to automatic");
                        return;
                    }
                    Rlog.d(LOG_TAG, "SET_NETWORK_SELECTION_AUTOMATIC: already automatic, ignore");
                    return;
                case WspTypeDecoder.WSP_HEADER_LAST_MODIFIED /*29*/:
                    processIccRecordEvents(((Integer) ((AsyncResult) msg.obj).result).intValue());
                    return;
                case 35:
                    ar = (AsyncResult) msg.obj;
                    if (ar.exception != null) {
                        log("Error while fetching Mdn");
                        return;
                    } else {
                        this.mMdn = ((String[]) ar.result)[0];
                        return;
                    }
                case 36:
                    new GsmMmiCode(this, (UiccCardApplication) this.mUiccApplication.get()).processSsData(msg, (AsyncResult) msg.obj);
                    return;
                case 100:
                    ar = (AsyncResult) msg.obj;
                    onComplete = (Message) ar.userObj;
                    if (onComplete != null) {
                        if (!CscFeature.getInstance().getEnableStatus("CscFeature_RIL_UseRatInfoDuringPlmnSelection") && ar.exception == null) {
                            ListIterator li = ar.result.listIterator();
                            while (li.hasNext()) {
                                OperatorInfo ni = (OperatorInfo) li.next();
                                IccRecords iccRecords = (IccRecords) this.mIccRecords.get();
                                if (iccRecords == null || !(iccRecords instanceof SIMRecords)) {
                                    StringBuilder append = new StringBuilder().append("Failed to get IccRecords for GET_AVAILABLE_NETWORK_COMPLETE - ");
                                    if (iccRecords == null) {
                                        z = true;
                                    } else {
                                        z = false;
                                    }
                                    log(append.append(z).toString());
                                } else {
                                    String eonsName = ((SIMRecords) iccRecords).getAllEonsNames(ni.getOperatorNumeric().split("/")[0], ni.getLac(), false);
                                    if (CscFeature.getInstance().getEnableStatus("CscFeature_RIL_ReferSpnOnManualSearch")) {
                                        String spnName = TelephonyManager.getDefault().getSimOperatorName();
                                        String hPlmn = TelephonyManager.getDefault().getSimOperator();
                                        if (!(spnName == null || TextUtils.isEmpty(spnName) || hPlmn == null || TextUtils.isEmpty(hPlmn) || !hPlmn.equals(ni.getOperatorNumeric()) || !"50503".equals(hPlmn))) {
                                            eonsName = spnName;
                                            log("change eonsName with spn name :  " + eonsName);
                                        }
                                    }
                                    if (eonsName != null) {
                                        if (CscFeature.getInstance().getEnableStatus("CscFeature_RIL_DisplayRatInfoInManualNetSearchList")) {
                                            li.set(new OperatorInfo(eonsName, ni.getOperatorAlphaShort(), ni.getOperatorNumeric(), ni.getState(), ni.getOperatorRat()));
                                        } else {
                                            li.set(new OperatorInfo(eonsName, ni.getOperatorAlphaShort(), ni.getOperatorNumeric(), ni.getState(), ni.getLac()));
                                        }
                                        if (!SHIP_BUILD) {
                                            log("change operatorAlphaLong to eonsName : " + eonsName);
                                        }
                                    }
                                }
                            }
                        }
                        AsyncResult.forMessage(onComplete, ar.result, ar.exception);
                        onComplete.sendToTarget();
                        return;
                    }
                    return;
                case 500:
                    log("EVENT_SUBSCRIPTION_ACTIVATED");
                    onSubscriptionActivated();
                    return;
                case 501:
                    log("EVENT_SUBSCRIPTION_DEACTIVATED");
                    onSubscriptionDeactivated();
                    return;
                case CallFailCause.CDMA_ACCESS_FAILURE /*1006*/:
                    log("Service state changed for emergnecy number");
                    setEmergencyNumbers();
                    return;
                case 2001:
                    if ("DCG".equals("DGG") || "DCGG".equals("DGG") || "DCGS".equals("DGG") || "DCGGS".equals("DGG") || "CG".equals("DGG")) {
                        ar = (AsyncResult) msg.obj;
                        if (ar.exception != null) {
                            return;
                        }
                        if (getPhoneId() == 0) {
                            Rlog.d(LOG_TAG, "ar.result" + ar.result);
                            int data = ((Integer) ar.result).intValue();
                            Rlog.e(LOG_TAG, "data" + data);
                            if (data == 0) {
                                Rlog.d(LOG_TAG, "SIM_POWER_DONE is sent in slot1 gsm");
                                this.mContext.sendBroadcast(new Intent("SIM_POWER_DONE"));
                                return;
                            }
                            Rlog.d(LOG_TAG, "SIM_POWER_DONE is not sent");
                            return;
                        }
                        Rlog.d(LOG_TAG, "SIM_POWER_DONE is not sent in gsm");
                        if ("1".equals(SystemProperties.get("ril.Simselswitch"))) {
                            SystemProperties.set("gsm.sim.selectnetwork", "GSM");
                            return;
                        } else {
                            SystemProperties.set("gsm.sim.selectnetwork", "CDMA");
                            return;
                        }
                    }
                    return;
                default:
                    super.handleMessage(msg);
                    return;
            }
        }
        Rlog.e(LOG_TAG, "Received message " + msg + "[" + msg.what + "] while being destroyed. Ignoring.");
    }

    protected UiccCardApplication getUiccCardApplication() {
        return this.mUiccController.getUiccCardApplication(this.mPhoneId, 1);
    }

    protected void onUpdateIccAvailability() {
        if (this.mUiccController != null) {
            UiccCardApplication newUiccApplication = this.mUiccController.getUiccCardApplication(this.mPhoneId, 3);
            IsimUiccRecords newIsimUiccRecords = null;
            if (newUiccApplication != null) {
                newIsimUiccRecords = (IsimUiccRecords) newUiccApplication.getIccRecords();
                log("New ISIM application found");
            }
            this.mIsimUiccRecords = newIsimUiccRecords;
            newUiccApplication = getUiccCardApplication();
            UiccCardApplication app = (UiccCardApplication) this.mUiccApplication.get();
            if (app != newUiccApplication || "DCG".equals("DGG") || "DCGG".equals("DGG") || "DCGS".equals("DGG") || "DCGGS".equals("DGG") || "CG".equals("DGG")) {
                if (app != null) {
                    log("Removing stale icc objects.");
                    if (this.mIccRecords.get() != null) {
                        unregisterForSimRecordEvents();
                        this.mSimPhoneBookIntManager.updateIccRecords(null);
                    }
                    this.mIccRecords.set(null);
                    this.mUiccApplication.set(null);
                }
                if (newUiccApplication != null) {
                    log("New Uicc application found");
                    this.mUiccApplication.set(newUiccApplication);
                    this.mIccRecords.set(newUiccApplication.getIccRecords());
                    registerForSimRecordEvents();
                    this.mSimPhoneBookIntManager.updateIccRecords((IccRecords) this.mIccRecords.get());
                }
            }
        }
    }

    private void processIccRecordEvents(int eventCode) {
        switch (eventCode) {
            case 0:
                notifyMessageWaitingIndicator();
                return;
            case 1:
                notifyCallForwardingIndicator();
                return;
            default:
                return;
        }
    }

    public boolean updateCurrentCarrierInProvider() {
        long currentDds = SubscriptionController.getInstance().getDefaultDataSubId();
        String operatorNumeric = getOperatorNumeric();
        log("updateCurrentCarrierInProvider: mSubId = " + getSubId() + " currentDds = " + currentDds + " operatorNumeric = " + operatorNumeric);
        if (!TextUtils.isEmpty(operatorNumeric)) {
            if (getSubId() != currentDds) {
            }
            try {
                Uri uri = Uri.withAppendedPath(Carriers.CONTENT_URI, TelephonyManager.appendId("current", (long) SubscriptionController.getInstance().getPhoneId(getSubId())));
                ContentValues map = new ContentValues();
                map.put(Carriers.NUMERIC, operatorNumeric);
                this.mContext.getContentResolver().insert(uri, map);
                return true;
            } catch (SQLException e) {
                Rlog.e(LOG_TAG, "Can't store current operator", e);
            }
        }
        return false;
    }

    public void saveClirSetting(int commandInterfaceCLIRMode) {
        Editor editor = PreferenceManager.getDefaultSharedPreferences(getContext()).edit();
        editor.putInt(PhoneBase.CLIR_KEY + getPhoneId(), commandInterfaceCLIRMode);
        if (!editor.commit()) {
            Rlog.e(LOG_TAG, "failed to commit CLIR preference");
        }
    }

    private void handleCfuQueryResult(CallForwardInfo[] infos) {
        IccRecords r = (IccRecords) this.mIccRecords.get();
        if (r == null) {
            return;
        }
        if (infos == null || infos.length == 0) {
            r.setCallForwardingFlag(1, new Boolean(false), new Boolean(false));
            return;
        }
        CallForwardInfo fi_voice = null;
        CallForwardInfo fi_video = null;
        int s = infos.length;
        for (int i = 0; i < s; i++) {
            boolean z;
            if ((infos[i].serviceClass & 1) != 0) {
                fi_voice = infos[i];
                if (infos[i].status == 1) {
                    z = true;
                } else {
                    z = false;
                }
                r.setVoiceCallForwardingFlag(1, z, infos[i].number);
            }
            if ((infos[i].serviceClass & 16) != 0) {
                fi_video = infos[i];
                if (infos[i].status == 1) {
                    z = true;
                } else {
                    z = false;
                }
                r.setVideoCallForwardingFlag(1, z, infos[i].number);
            }
        }
        if (fi_voice == null) {
            r.setVoiceCallForwardingFlag(1, false);
        }
        if (fi_video == null) {
            r.setVideoCallForwardingFlag(1, false);
        }
    }

    public PhoneSubInfo getPhoneSubInfo() {
        return this.mSubInfo;
    }

    public IccPhoneBookInterfaceManager getIccPhoneBookInterfaceManager() {
        return this.mSimPhoneBookIntManager;
    }

    public void activateCellBroadcastSms(int activate, Message response) {
        Rlog.e(LOG_TAG, "[GSMPhone] activateCellBroadcastSms() is obsolete; use SmsManager");
        response.sendToTarget();
    }

    public void getCellBroadcastSmsConfig(Message response) {
        Rlog.e(LOG_TAG, "[GSMPhone] getCellBroadcastSmsConfig() is obsolete; use SmsManager");
        response.sendToTarget();
    }

    public void setCellBroadcastSmsConfig(int[] configValuesArray, Message response) {
        Rlog.e(LOG_TAG, "[GSMPhone] setCellBroadcastSmsConfig() is obsolete; use SmsManager");
        response.sendToTarget();
    }

    public boolean isCspPlmnEnabled() {
        IccRecords r = (IccRecords) this.mIccRecords.get();
        return r != null ? r.isCspPlmnEnabled() : false;
    }

    private void registerForSimRecordEvents() {
        IccRecords r = (IccRecords) this.mIccRecords.get();
        if (r != null) {
            r.registerForNetworkSelectionModeAutomatic(this, 28, null);
            r.registerForRecordsEvents(this, 29, null);
            r.registerForRecordsLoaded(this, 3, null);
        }
    }

    private void unregisterForSimRecordEvents() {
        IccRecords r = (IccRecords) this.mIccRecords.get();
        if (r != null) {
            r.unregisterForNetworkSelectionModeAutomatic(this);
            r.unregisterForRecordsEvents(this);
            r.unregisterForRecordsLoaded(this);
        }
    }

    public void exitEmergencyCallbackMode() {
        if (this.mImsPhone != null) {
            this.mImsPhone.exitEmergencyCallbackMode();
        }
    }

    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        pw.println("GSMPhone extends:");
        super.dump(fd, pw, args);
        pw.println(" mCT=" + this.mCT);
        pw.println(" mSST=" + this.mSST);
        pw.println(" mPendingMMIs=" + this.mPendingMMIs);
        pw.println(" mSimPhoneBookIntManager=" + this.mSimPhoneBookIntManager);
        pw.println(" mSubInfo=" + this.mSubInfo);
        pw.println(" mImei=" + this.mImei);
        pw.println(" mImeiSv=" + this.mImeiSv);
        pw.println(" mVmNumber=" + this.mVmNumber);
    }

    protected void setIsoCountryProperty(String operatorNumeric) {
        if (TextUtils.isEmpty(operatorNumeric)) {
            log("setIsoCountryProperty: clear 'gsm.sim.operator.iso-country'");
            setSystemProperty("gsm.sim.operator.iso-country", "");
            return;
        }
        String iso = "";
        try {
            iso = MccTable.countryCodeForMcc(Integer.parseInt(operatorNumeric.substring(0, 3)));
        } catch (NumberFormatException ex) {
            Rlog.e(LOG_TAG, "[GSMPhone] setIsoCountryProperty: countryCodeForMcc error", ex);
        } catch (StringIndexOutOfBoundsException ex2) {
            Rlog.e(LOG_TAG, "[GSMPhone] setIsoCountryProperty: countryCodeForMcc error", ex2);
        }
        log("setIsoCountryProperty: set 'gsm.sim.operator.iso-country' to iso=" + iso);
        setSystemProperty("gsm.sim.operator.iso-country", iso);
    }

    boolean updateCurrentCarrierInProvider(String operatorNumeric) {
        log("GSMPhone: updateCurrentCarrierInProvider called");
        if (TextUtils.isEmpty(operatorNumeric)) {
            return false;
        }
        try {
            Uri uri = Uri.withAppendedPath(Carriers.CONTENT_URI, "current");
            ContentValues map = new ContentValues();
            map.put(Carriers.NUMERIC, operatorNumeric);
            log("updateCurrentCarrierInProvider from system: numeric=" + operatorNumeric);
            getContext().getContentResolver().insert(uri, map);
            log("update mccmnc=" + operatorNumeric);
            MccTable.updateMccMncConfiguration(this.mContext, operatorNumeric, false);
            return true;
        } catch (SQLException e) {
            Rlog.e(LOG_TAG, "Can't store current operator", e);
            return false;
        }
    }

    public String getMdn() {
        return this.mMdn;
    }

    public boolean setOperatorBrandOverride(String brand) {
        boolean z = false;
        if (this.mUiccController != null) {
            UiccCard card = this.mUiccController.getUiccCard();
            if (card != null) {
                z = card.setOperatorBrandOverride(brand);
                if (z) {
                    IccRecords iccRecords = (IccRecords) this.mIccRecords.get();
                    if (iccRecords != null) {
                        setSystemProperty("gsm.sim.operator.alpha", iccRecords.getServiceProviderName());
                    }
                    if (this.mSST != null) {
                        this.mSST.pollState();
                    }
                }
            }
        }
        return z;
    }

    public String getOperatorNumeric() {
        IccRecords r = (IccRecords) this.mIccRecords.get();
        if (r != null) {
            return r.getOperatorNumeric();
        }
        return null;
    }

    public void registerForAllDataDisconnected(Handler h, int what, Object obj) {
        ((DcTracker) this.mDcTracker).registerForAllDataDisconnected(h, what, obj);
    }

    public void unregisterForAllDataDisconnected(Handler h) {
        ((DcTracker) this.mDcTracker).unregisterForAllDataDisconnected(h);
    }

    public void setInternalDataEnabled(boolean enable, Message onCompleteMsg) {
        ((DcTracker) this.mDcTracker).setInternalDataEnabled(enable, onCompleteMsg);
    }

    public boolean setInternalDataEnabledFlag(boolean enable) {
        return ((DcTracker) this.mDcTracker).setInternalDataEnabledFlag(enable);
    }

    public void notifyEcbmTimerReset(Boolean flag) {
        this.mEcmTimerResetRegistrants.notifyResult(flag);
    }

    public void registerForEcmTimerReset(Handler h, int what, Object obj) {
        this.mEcmTimerResetRegistrants.addUnique(h, what, obj);
    }

    public void unregisterForEcmTimerReset(Handler h) {
        this.mEcmTimerResetRegistrants.remove(h);
    }

    public void resetSubSpecifics() {
    }

    protected void log(String s) {
        Rlog.d(MultiSimManager.appendSimSlot(LOG_TAG, this.mPhoneId), s);
    }

    private String processUkCliPrefix(String dialString) {
        if (!TelephonyManager.getDefault().getNetworkCountryIso().equals("gb")) {
            return dialString;
        }
        if (dialString.startsWith("1470")) {
            return "*31#" + dialString;
        }
        if (dialString.startsWith("141")) {
            return "#31#" + dialString;
        }
        return dialString;
    }

    public int getDataServiceState() {
        if (this.mSST == null) {
            return 1;
        }
        return this.mSST.getCurrentDataConnectionState();
    }

    public int getCurrentGprsState() {
        if (this.mSST == null) {
            return 1;
        }
        return this.mSST.getCurrentDataConnectionState();
    }

    public void sendEncodedUssd(byte[] ussdMessage, int ussdLength, int dcsCode) {
        GsmMmiCode mmi = new GsmMmiCode(this, (UiccCardApplication) this.mUiccApplication.get());
        this.mPendingMMIs.add(mmi);
        mmi.sendEncodedUssd(ussdMessage, ussdLength, dcsCode);
    }

    public void setmMmiInitBySTK(boolean set) {
        log("mMmiInitBySTK set to " + set);
        this.mMmiInitBySTK = set;
    }

    public void invokeOemRilRequestStrings(String[] strings, Message response) {
        if (strings[0].equals("setEmergencyNumbers")) {
            setEmergencyNumbers(strings[1]);
        } else if (strings[0].equals("loadEmergencyCallNumberSpec")) {
            response.obj = loadEmergencyCallNumberSpec();
        } else if (strings[0].equals("getVideoCallForwardingIndicator")) {
            response.obj = new Boolean(getVideoCallForwardingIndicator());
        } else {
            super.invokeOemRilRequestStrings(strings, response);
        }
    }

    public void updateEccNum(String eccNums) {
        this.mEccNums = eccNums;
        setEmergencyNumbers(null);
    }

    public void setEmergencyNumbers() {
        setEmergencyNumbers(null);
    }

    public void setEmergencyNumbers(String customerSpec) {
        if (this.mSST != null) {
            String key;
            ServiceState ss = this.mSST.mSS;
            String op = ss.getOperatorNumeric();
            int simState = TelephonyManager.getDefault().getSimState();
            String simstateProperty = getSystemProperty("gsm.sim.state", "ABSENT");
            String emergencyNumbers = null;
            String PROP_ECC_LIST = "ril.ecclist";
            boolean withSIM = true;
            log("setEmergencyNumbers: mPrevSs=[" + this.mPrevSs + "], ss=[" + ss + "]");
            log("setEmergencyNumbers: customerSpec=" + customerSpec);
            log("setEmergencyNumbers: simstateProperty=" + simstateProperty);
            if ("ABSENT".equals(simstateProperty) || "UNKNOWN".equals(simstateProperty) || "CARD_IO_ERROR".equals(simstateProperty) || "NOT_READY".equals(simstateProperty) || TextUtils.isEmpty(simstateProperty)) {
                withSIM = false;
            }
            log("setEmergencyNumbers: withSIM=" + withSIM);
            if (withSIM) {
                emergencyNumbers = this.mEccNums;
                log("setEmergencyNumbers: ECC from SIM=" + emergencyNumbers);
            }
            String specToUpdate;
            if (customerSpec != null) {
                saveEmergencyCallNumberSpec(customerSpec);
                specToUpdate = customerSpec;
            } else {
                specToUpdate = loadEmergencyCallNumberSpec();
            }
            String salesCode = SystemProperties.get("ro.csc.sales_code", "NONE");
            boolean isCHN = "CHN".equals(salesCode) || "CHU".equals(salesCode) || "CTC".equals(salesCode) || "CHM".equals(salesCode) || "CHC".equals(salesCode);
            boolean isHK = "TGY".equals(salesCode) || "ZZH".equals(salesCode);
            boolean isTW = "BRI".equals(salesCode) || "CWT".equals(salesCode) || "TWN".equals(salesCode) || "FET".equals(salesCode);
            if ((isCHN || isHK || isTW) && (Global.getInt(this.mContext.getContentResolver(), "airplane_mode_on", 0) == 1 || op == null)) {
                if (isCHN || isHK) {
                    log("setEmergencyNumbers: For China mainland or Hong Kong in Airplane mode : " + op);
                    op = "460";
                } else if (isTW) {
                    op = "466";
                }
            }
            String emergencyNumbersForOperator = EccTable.emergencyNumbersForPLMN(op, withSIM);
            String mccForEnc = CscFeature.getInstance().getString("CscFeature_RIL_ConfigEccListDuringEncryptionMode");
            String ENCRYPTED_STATE = "1";
            if (!TextUtils.isEmpty(mccForEnc) && op == null && "1".equals(SystemProperties.get("vold.decrypt"))) {
                log("op == null, mccForEnc == " + mccForEnc);
                emergencyNumbersForOperator = EccTable.emergencyNumbersForPLMN(mccForEnc, false);
            }
            log("setEmergencyNumbers: ECC for operator=" + emergencyNumbersForOperator);
            if (emergencyNumbers == null || emergencyNumbers.equals("")) {
                emergencyNumbers = emergencyNumbersForOperator;
            } else {
                emergencyNumbers = emergencyNumbers + "," + emergencyNumbersForOperator;
            }
            log("setEmergencyNumbers: emergencyNumbers=" + emergencyNumbers);
            this.mPrevSs = new ServiceState(ss);
            int i = 0;
            while (true) {
                key = "ril.ecclist" + getPhoneId() + Integer.toString(i);
                if (SystemProperties.get(key).length() == 0) {
                    break;
                }
                SystemProperties.set(key, "");
                i++;
            }
            for (i = 0; i * 91 < emergencyNumbers.length(); i++) {
                key = "ril.ecclist" + getPhoneId() + Integer.toString(i);
                int start = i * 91;
                int end = Math.min(emergencyNumbers.length(), (i + 1) * 91);
                log("setEmergencyNumbers: " + key + " - " + emergencyNumbers.substring(start, end));
                SystemProperties.set(key, emergencyNumbers.substring(start, end));
            }
        }
    }

    public boolean getSMSavailable() {
        IccRecords iccRecords = (IccRecords) this.mIccRecords.get();
        if (iccRecords != null && (iccRecords instanceof SIMRecords)) {
            return ((SIMRecords) iccRecords).isAvailableSMS;
        }
        boolean z;
        String str = LOG_TAG;
        StringBuilder append = new StringBuilder().append("Failed to get IccRecords for getSMSavailable - ");
        if (iccRecords == null) {
            z = true;
        } else {
            z = false;
        }
        Rlog.e(str, append.append(z).toString());
        return false;
    }

    public boolean getSMSPavailable() {
        IccRecords iccRecords = (IccRecords) this.mIccRecords.get();
        if (iccRecords != null && (iccRecords instanceof SIMRecords)) {
            return ((SIMRecords) iccRecords).isAvailableSMSP;
        }
        boolean z;
        String str = LOG_TAG;
        StringBuilder append = new StringBuilder().append("Failed to get IccRecords for getSMSPavailable - ");
        if (iccRecords == null) {
            z = true;
        } else {
            z = false;
        }
        Rlog.e(str, append.append(z).toString());
        return false;
    }

    public String getSktImsiM() {
        IccRecords r = (IccRecords) this.mIccRecords.get();
        return r != null ? r.getSktIMSIM() : "";
    }

    public String getSktIrm() {
        IccRecords r = (IccRecords) this.mIccRecords.get();
        return r != null ? r.getSktIRM() : "";
    }

    public void getPreferredNetworkList(Message response) {
        this.mCi.getPreferredNetworkList(response);
    }

    public void setPreferredNetworkList(int index, String operator, String plmn, int gsmAct, int gsmCompactAct, int utranAct, int mode, Message response) {
        this.mCi.setPreferredNetworkList(index, operator, plmn, gsmAct, gsmCompactAct, utranAct, mode, response);
    }

    public boolean getVideoCallForwardingIndicator() {
        IccRecords r = (IccRecords) this.mIccRecords.get();
        return r != null ? r.getVideoCallForwardingFlag() : false;
    }

    public void getCallForwardingOption(int commandInterfaceCFReason, Message onComplete) {
        getCallForwardingOption(commandInterfaceCFReason, null, 0, onComplete);
    }

    public void setCallForwardingOption(int commandInterfaceCFAction, int commandInterfaceCFReason, String dialingNumber, int timerSeconds, Message onComplete) {
        setCallForwardingOption(commandInterfaceCFAction, commandInterfaceCFReason, dialingNumber, timerSeconds, 1, onComplete);
    }

    private boolean isValidFacilityString(String facility) {
        if (facility.equals(CommandsInterface.CB_FACILITY_BAOC) || facility.equals(CommandsInterface.CB_FACILITY_BAOIC) || facility.equals(CommandsInterface.CB_FACILITY_BAOICxH) || facility.equals(CommandsInterface.CB_FACILITY_BAIC) || facility.equals(CommandsInterface.CB_FACILITY_BAICr) || facility.equals(CommandsInterface.CB_FACILITY_BA_ALL) || facility.equals(CommandsInterface.CB_FACILITY_BA_MO) || facility.equals(CommandsInterface.CB_FACILITY_BA_MT) || facility.equals(CommandsInterface.CB_FACILITY_BA_SIM) || facility.equals(CommandsInterface.CB_FACILITY_BA_FD)) {
            return true;
        }
        Rlog.e(LOG_TAG, " Invalid facility String : " + facility);
        return false;
    }

    public void getCallBarringOption(String facility, String password, Message onComplete) {
        if (isValidFacilityString(facility)) {
            this.mCi.queryFacilityLock(facility, password, 0, onComplete);
        }
    }

    public void setCallBarringOption(String facility, boolean lockState, String password, Message onComplete) {
        if (isValidFacilityString(facility)) {
            this.mCi.setFacilityLock(facility, lockState, password, 1, onComplete);
        }
    }

    public void requestChangeCbPsw(String facility, String oldPwd, String newPwd, Message result) {
        this.mCi.changeBarringPassword(facility, oldPwd, newPwd, result);
    }

    private boolean isValidCommandInterfaceCBFlavour(String cbFlavour) {
        if (cbFlavour.equals(CommandsInterface.CB_FACILITY_BAOC) || cbFlavour.equals(CommandsInterface.CB_FACILITY_BAOIC) || cbFlavour.equals(CommandsInterface.CB_FACILITY_BAOICxH) || cbFlavour.equals(CommandsInterface.CB_FACILITY_BAIC) || cbFlavour.equals(CommandsInterface.CB_FACILITY_BAICr) || cbFlavour.equals(CommandsInterface.CB_FACILITY_BA_ALL) || cbFlavour.equals(CommandsInterface.CB_FACILITY_BA_MO) || cbFlavour.equals(CommandsInterface.CB_FACILITY_BA_MT)) {
            return true;
        }
        return false;
    }

    public void getCallBarringOption(String commandInterfacecbFlavour, Message onComplete) {
        getCallBarringOption(commandInterfacecbFlavour, null, 0, onComplete);
    }

    public void getCallBarringOption(String commandInterfacecbFlavour, String password, int serviceClass, Message onComplete) {
        log("getCallBarringOption password:" + password + "serviceClass:" + serviceClass);
        if (isValidCommandInterfaceCBFlavour(commandInterfacecbFlavour)) {
            log("requesting call barring query.");
            this.mCi.queryFacilityLock(commandInterfacecbFlavour, password, serviceClass, onComplete);
        }
    }

    public boolean setCallBarringOption(boolean cbAction, String commandInterfacecbFlavour, String password, Message onComplete) {
        return setCallBarringOption(cbAction, commandInterfacecbFlavour, password, 1, onComplete);
    }

    public boolean setCallBarringOption(boolean cbAction, String commandInterfacecbFlavour, String password, int serviceClass, Message onComplete) {
        if (password == null) {
            return false;
        }
        if (isValidCommandInterfaceCBFlavour(commandInterfacecbFlavour)) {
            log("requesting set call barring .");
            this.mCi.setFacilityLock(commandInterfacecbFlavour, cbAction, password, serviceClass, onComplete);
        }
        return true;
    }

    public boolean changeBarringPassword(String facility, String oldPwd, String newPwd, Message onComplete) {
        if (oldPwd == null || newPwd == null) {
            return false;
        }
        log("requesting change call barring password");
        this.mCi.changeBarringPassword(facility, oldPwd, newPwd, onComplete);
        return true;
    }

    public boolean changeBarringPassword(String facility, String oldPwd, String newPwd, String newPwdAgain, Message onComplete) {
        if (oldPwd == null || newPwd == null || newPwdAgain == null) {
            return false;
        }
        log("requesting change call barring password");
        this.mCi.changeBarringPassword(facility, oldPwd, newPwd, newPwdAgain, onComplete);
        return true;
    }

    public String getHandsetInfo(String ID) {
        log("GSMPhone - getHandsetInfo");
        return this.mSST.getHandsetInfo(ID);
    }

    public String getMmiErrorMsg() {
        return this.mMmiErrMsg;
    }

    public void setMmiErrorMsg(String errMsg) {
        log("setMmiErrorMsg with msg:" + errMsg);
        this.mMmiErrMsg = errMsg;
    }

    public int getVoiceMessageCount() {
        IccRecords r = (IccRecords) this.mIccRecords.get();
        return r != null ? r.getVoiceMessageCount() : -1;
    }

    public void updateMessageWaitingIndicatorWithCount(int mwi) {
        IccRecords r = (IccRecords) this.mIccRecords.get();
        if (r != null) {
            r.setVoiceMessageWaiting(1, mwi);
            if (!r.chekcMWISavailable()) {
                storeVoiceMailCount(mwi);
            }
        }
    }

    public void storeVoiceMailCount(int mwi) {
        IccRecords r = (IccRecords) this.mIccRecords.get();
        if (r != null && !r.chekcMWISavailable()) {
            String imsi = getSubscriberId();
            if (SHIP_BUILD) {
                log(" Storing Voice Mail Count = " + mwi + " for imsi = ******** " + " for mVmCountKey = " + this.mVmCountKey + " vmId = " + this.mVmId + " in preferences.");
            } else {
                log(" Storing Voice Mail Count = " + mwi + " for imsi = " + imsi + " for mVmCountKey = " + this.mVmCountKey + " vmId = " + this.mVmId + " in preferences.");
            }
            Editor editor = PreferenceManager.getDefaultSharedPreferences(this.mContext).edit();
            editor.putInt(this.mVmCountKey, mwi);
            editor.putString(this.mVmId, imsi);
            editor.commit();
        }
    }

    protected int getStoredVoiceMessageCount() {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this.mContext);
        String imsi = sp.getString(this.mVmId, null);
        String currentImsi = getSubscriberId();
        if (imsi == null || currentImsi == null || !currentImsi.equals(imsi)) {
            return 0;
        }
        int countVoiceMessages = sp.getInt(this.mVmCountKey, 0);
        log("Voice Mail Count from preference = " + countVoiceMessages);
        return countVoiceMessages;
    }

    public String[] getSponImsi() {
        IccRecords r = (IccRecords) this.mIccRecords.get();
        if (r != null) {
            return r.getSponImsi();
        }
        return null;
    }

    private void updateVoiceMail() {
        IccRecords r = (IccRecords) this.mIccRecords.get();
        if (r != null && !r.chekcMWISavailable()) {
            setVoiceMessageWaiting(1, getStoredVoiceMessageCount());
        }
    }

    public void SimSlotActivation(boolean activation) {
        int uid = Binder.getCallingUid();
        if (uid == CallFailCause.CDMA_DROP || uid == 1000) {
            int slotId = SubscriptionController.getInstance().getSlotId(getSubId());
            if (slotId < 0) {
                Rlog.e(LOG_TAG, "SimSlotActivation: invalid slotId:" + slotId);
                return;
            }
            if (activation) {
                setSystemProperty("gsm.sim.active", "2");
            } else {
                setSystemProperty("gsm.sim.active", "1");
            }
            if (activation && this.mCi.getRadioState() == RadioState.RADIO_ON) {
                Rlog.e(LOG_TAG, "SimSlotActivation: card already active, slotId:" + slotId);
                notifyCardStateChanged(slotId, 1);
                return;
            } else if (activation || this.mCi.getRadioState() != RadioState.RADIO_OFF) {
                Rlog.d(LOG_TAG, "SimSlotActivation: setRadioPower:" + activation + ", phoneId:" + getPhoneId() + ", slotId:" + slotId);
                this.mSST.setRadioPower(activation);
                return;
            } else {
                Rlog.e(LOG_TAG, "SimSlotActivation: card already deactivated, slotId:" + slotId);
                notifyCardStateChanged(slotId, 0);
                return;
            }
        }
        throw new SecurityException("Security Exception Occurred. Only SYSTEM can use clearData() function.");
    }

    public void SimSlotOnOff(int on) {
        Rlog.i(LOG_TAG, "SimSlotOnOff on : " + on);
        int uid = Binder.getCallingUid();
        if (uid == CallFailCause.CDMA_DROP || uid == 1000) {
            switch (on) {
                case 0:
                    if ("DCGS".equals("DGG") || "DCGGS".equals("DGG")) {
                        this.mSST.SimSlotOnOff(on, obtainMessage(2001));
                        if (getPhoneId() == 0) {
                            setSystemProperty("gsm.sim.currentcardstatus", "3");
                            return;
                        } else if ("DCGS".equals("DGG") && getPhoneId() == 1) {
                            setSystemProperty("gsm.sim.currentcardstatus", "3");
                            return;
                        } else {
                            return;
                        }
                    }
                    return;
                case 1:
                    this.mSST.SimSlotOnOff(on, obtainMessage(2001));
                    return;
                case 2:
                    this.mSST.SimSlotOnOff(on, obtainMessage(2001));
                    SystemProperties.set("gsm.globalmode", "GSM");
                    return;
                case 3:
                    this.mSST.SimSlotOnOff(on, obtainMessage(2001));
                    SystemProperties.set("gsm.globalmode", "CDMA");
                    return;
                case 4:
                    this.mSST.SimSlotOnOff(3, obtainMessage(2001));
                    SystemProperties.set("gsm.globalmode", "GLOBAL");
                    return;
                case 5:
                    SystemProperties.set("gsm.globalmode", "GLOBAL");
                    return;
                case 6:
                    SystemProperties.set("gsm.globalmode", "GSM");
                    return;
                case 7:
                    SystemProperties.set("gsm.globalmode", "CDMA");
                    return;
                case 8:
                    this.mSST.SimSlotOnOff(on, obtainMessage(2001));
                    return;
                case 9:
                    this.mCi.setSimPower(9, null);
                    return;
                case 10:
                    this.mCi.setSimPower(10, null);
                    return;
                default:
                    Rlog.e(LOG_TAG, "SimSlotOnOff() error");
                    return;
            }
        }
        throw new SecurityException("Security Exception Occurred. Only SYSTEM can use clearData() function.");
    }

    public String getLine1NumberType(int SimType) {
        return null;
    }

    public String getSubscriberIdType(int SimType) {
        return null;
    }

    public boolean getMsisdnavailable() {
        return false;
    }

    public boolean getMdnavailable() {
        return false;
    }

    public boolean getDualSimSlotActivationState() {
        long subId = SubscriptionController.getInstance().getSubIdUsingPhoneId(getPhoneId());
        int subStatus = SubscriptionController.getInstance().getSubState(subId);
        Rlog.d(LOG_TAG, "getDualSimSlotActivationState subStatus:" + subStatus + "phoneId:" + getPhoneId() + ", subId:" + subId);
        if (subStatus == 1) {
            return true;
        }
        return false;
    }

    public String getIccSerialNumber() {
        IccRecords r = (IccRecords) this.mIccRecords.get();
        return r != null ? r.getIccId() : null;
    }

    public void setGbaBootstrappingParams(byte[] rand, String btid, String keyLifetime, Message onComplete) {
        if (this.mIsimUiccRecords != null) {
            this.mIsimUiccRecords.setGbaBootstrappingParams(rand, btid, keyLifetime, onComplete);
        }
    }

    public void holdCall() throws CallStateException {
        this.mCT.holdCall();
    }

    public boolean getOCSGLAvailable() {
        IccRecords iccRecords = (IccRecords) this.mIccRecords.get();
        if (iccRecords == null || !(iccRecords instanceof SIMRecords)) {
            Rlog.e(LOG_TAG, "Failed to get IccRecords for getOCSGLAvailable " + (iccRecords == null));
            return false;
        } else if (!((SIMRecords) iccRecords).isAvailableOCSGL || ((SIMRecords) iccRecords).isAvailableOCSGLList) {
            return true;
        } else {
            return false;
        }
    }

    public void selectCsg(Message response) {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(bos);
        log("selectCsg");
        try {
            dos.writeByte(2);
            dos.writeByte(11);
            dos.writeShort(4);
        } catch (IOException e) {
            Rlog.w(LOG_TAG, "IOException!!!");
            if (dos != null) {
                try {
                    dos.close();
                } catch (IOException e2) {
                    Rlog.w(LOG_TAG, "close fail!!!");
                }
            }
        }
        this.mCi.invokeOemRilRequestRaw(bos.toByteArray(), response);
        if (dos != null) {
            try {
                dos.close();
            } catch (IOException e3) {
                Rlog.w(LOG_TAG, "close fail!!!");
                return;
            }
        }
        if (bos != null) {
            bos.close();
        }
    }

    public boolean getFDNavailable() {
        IccRecords iccRecords = (IccRecords) this.mIccRecords.get();
        if (iccRecords != null && (iccRecords instanceof SIMRecords)) {
            return ((SIMRecords) iccRecords).isAvailableFDN;
        }
        boolean z;
        String str = LOG_TAG;
        StringBuilder append = new StringBuilder().append("Failed to get IccRecords for getFDNavailable - ");
        if (iccRecords == null) {
            z = true;
        } else {
            z = false;
        }
        Rlog.e(str, append.append(z).toString());
        return false;
    }

    public void startGlobalNetworkSearchTimer() {
        this.mSST.startGlobalNetworkSearchTimer();
    }

    public void stopGlobalNetworkSearchTimer() {
        this.mSST.stopGlobalNetworkSearchTimer();
    }

    public void startGlobalNoSvcChkTimer() {
        this.mSST.startGlobalNoSvcChkTimer();
    }

    public void stopGlobalNoSvcChkTimer() {
        this.mSST.stopGlobalNoSvcChkTimer();
    }
}
