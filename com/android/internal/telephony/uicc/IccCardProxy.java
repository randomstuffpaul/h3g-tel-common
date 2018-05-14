package com.android.internal.telephony.uicc;

import android.app.ActivityManagerNative;
import android.app.Notification.Builder;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.os.AsyncResult;
import android.os.Handler;
import android.os.Message;
import android.os.Registrant;
import android.os.RegistrantList;
import android.os.RemoteException;
import android.os.SystemProperties;
import android.provider.Settings.SettingNotFoundException;
import android.provider.Settings.System;
import android.telephony.Rlog;
import android.telephony.ServiceState;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import com.android.internal.telephony.CommandsInterface;
import com.android.internal.telephony.CommandsInterface.RadioState;
import com.android.internal.telephony.IccCard;
import com.android.internal.telephony.IccCardConstants.State;
import com.android.internal.telephony.MccTable;
import com.android.internal.telephony.PhoneBase;
import com.android.internal.telephony.Subscription;
import com.android.internal.telephony.SubscriptionController;
import com.android.internal.telephony.cdma.CdmaSubscriptionSourceManager;
import com.android.internal.telephony.gsm.GSMPhone;
import com.android.internal.telephony.uicc.IccCardApplicationStatus.AppState;
import com.android.internal.telephony.uicc.IccCardApplicationStatus.AppType;
import com.android.internal.telephony.uicc.IccCardApplicationStatus.PersoSubState;
import com.android.internal.telephony.uicc.IccCardStatus.CardState;
import com.android.internal.telephony.uicc.IccCardStatus.PinState;
import com.samsung.android.telephony.MultiSimManager;
import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.ArrayList;

public class IccCardProxy extends Handler implements IccCard {
    private static final int BYTE_SAP_CARD_STATUS = 1;
    private static final int BYTE_SAP_NOTIFICATION = 0;
    public static final String CUSTOM_INTENT = "com.android.settings.networkmanagement";
    private static final boolean DBG = true;
    private static final int EVENT_APP_READY = 6;
    private static final int EVENT_BAKCUP_SIM_PIN_LOCK_INFO_REFRESH_DONE = 1000;
    private static final int EVENT_CARRIER_PRIVILIGES_LOADED = 503;
    private static final int EVENT_CDMA_SUBSCRIPTION_SOURCE_CHANGED = 11;
    private static final int EVENT_CHANGE_FACILITY_SIM_PERSO_DONE = 112;
    private static final int EVENT_CHANGE_SIM_PERSO_PASSWORD_DONE = 113;
    private static final int EVENT_ENTER_SIM_PERSO_DONE = 114;
    private static final int EVENT_GET_PERSO_STATUS_COMPLETE = 110;
    private static final int EVENT_GET_SIM_ECC_DONE = 200;
    private static final int EVENT_ICCID_READY = 52;
    private static final int EVENT_ICC_ABSENT = 4;
    private static final int EVENT_ICC_CHANGED = 3;
    private static final int EVENT_ICC_LOCKED = 5;
    private static final int EVENT_ICC_RECORD_EVENTS = 500;
    private static final int EVENT_IMSI_READY = 8;
    private static final int EVENT_MDN_READY = 51;
    private static final int EVENT_NETWORK_LOCKED = 9;
    private static final int EVENT_NETWORK_SUBSET_LOCKED = 101;
    private static final int EVENT_QUERY_FACILITY_SIM_PERSO_DONE = 111;
    private static final int EVENT_QUERY_FPLMN_DONE = 20;
    private static final int EVENT_QUERY_OPLMNWACT_DONE = 21;
    private static final int EVENT_QUERY_PLMNWACT_DONE = 19;
    private static final int EVENT_RADIO_OFF_OR_UNAVAILABLE = 1;
    private static final int EVENT_RADIO_ON = 2;
    private static final int EVENT_RECORDS_LOADED = 7;
    private static final int EVENT_SAP_NOTIFICATION = 12;
    private static final int EVENT_SETUP_WIZARD_NOT_START = 510;
    private static final int EVENT_SIM_LOCK_INFO_DONE = 103;
    private static final int EVENT_SIM_NEED_LOCK_INFO_REFRESH = 107;
    private static final int EVENT_SIM_PIN2_LOCK_INFO_DONE = 105;
    private static final int EVENT_SIM_PIN_LOCK_INFO_DONE = 104;
    private static final int EVENT_SIM_PIN_LOCK_INFO_REFRESH_DONE = 106;
    private static final int EVENT_SP_LOCKED = 102;
    private static final int EVENT_SUBSCRIPTION_ACTIVATED = 501;
    private static final int EVENT_SUBSCRIPTION_DEACTIVATED = 502;
    private static final int EVENT_UPDATE_LOCK_INFO = 100;
    private static final String LOG_TAG = "IccCardProxy";
    private static final int OEM_PERSO_CHANGE_PASS_MODE = 4;
    private static final int OEM_PERSO_GET_LOCK_STATUS = 2;
    private static final int OEM_PERSO_GET_LOCK_TYPE = 3;
    private static final int OEM_PERSO_LOCK_MODE = 1;
    private static final int OEM_PERSO_UNLOCK_MODE = 0;
    private static final int OEM_PERSO_VERIFY = 5;
    private static final String PIN_MODE_SIM_CRASH = "3";
    private static String PROPERTY_CDMA_HOME_OPERATOR_NUMERIC = "ro.cdma.home.operator.numeric";
    private static final String PROP_PERSO_NWK_PUK = "ril.perso_nwk_puk";
    private static final int REQ_NO_SIM_NOTIFICATION = 273;
    private static final int SAP_CARD_STATUS_INSERTED = 4;
    private static final int SAP_CARD_STATUS_NOT_ACCESSIBLE = 2;
    private static final int SAP_CARD_STATUS_RECOVERED = 5;
    private static final int SAP_CARD_STATUS_REMOVED = 3;
    private static final int SAP_CARD_STATUS_RESET = 1;
    private static final int SAP_CARD_STATUS_UNKNOWN = 0;
    private static final int SAP_STATUS_NOTIFICATION = 2;
    private static final String SIM_PIN_MODE = "ril.pin_mode";
    private static boolean[] mCardInfoAvailable = new boolean[]{false, false};
    private static boolean sIsStartSimManagement = false;
    final String PROP_ECC_LIST;
    final String PROP_ICC_TYPE;
    private int flightMode;
    private boolean isAlreadyOvercounted;
    private RegistrantList mAbsentRegistrants;
    public boolean mAlreadyReadEcc;
    private Integer mCardIndex;
    private CdmaSubscriptionSourceManager mCdmaSSM;
    private CommandsInterface mCi;
    private Context mContext;
    private int mCurrentAppType;
    private boolean mDesiredSimPersoLocked;
    private String mEmergencyNumber;
    private State mExternalState;
    private String mFPLMN;
    private IccRecords mIccRecords;
    private boolean mInitialized;
    private boolean mInvalidSimNotiDisplayed;
    private boolean mIsPermDisabledBroadcasted;
    private final Object mLock;
    private boolean mLteOnCdma;
    private RegistrantList mNetworkLockedRegistrants;
    private RegistrantList mNetworkSubsetLockedRegistrants;
    private String mNoSimDefaultEccNum;
    private String mOPLMNwAct;
    private String mPLMNwAct;
    private boolean mPersoSimLock;
    private PhoneBase mPhone;
    private RegistrantList mPinLockedRegistrants;
    private boolean mQuietMode;
    private boolean mRadioOn;
    private final BroadcastReceiver mReceiver;
    SimLockInfoResult mResultSIMLOCKINFO;
    private RegistrantList mSPLockedRegistrants;
    private Subscription mSubscriptionData;
    private UiccCardApplication mUiccApplication;
    private UiccCard mUiccCard;
    private UiccController mUiccController;

    static /* synthetic */ class C01271 {
        static final /* synthetic */ int[] $SwitchMap$com$android$internal$telephony$IccCardConstants$State = new int[State.values().length];
        static final /* synthetic */ int[] f15xec503f36 = new int[AppState.values().length];

        static {
            try {
                $SwitchMap$com$android$internal$telephony$IccCardConstants$State[State.ABSENT.ordinal()] = 1;
            } catch (NoSuchFieldError e) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$IccCardConstants$State[State.PIN_REQUIRED.ordinal()] = 2;
            } catch (NoSuchFieldError e2) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$IccCardConstants$State[State.PUK_REQUIRED.ordinal()] = 3;
            } catch (NoSuchFieldError e3) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$IccCardConstants$State[State.NETWORK_LOCKED.ordinal()] = 4;
            } catch (NoSuchFieldError e4) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$IccCardConstants$State[State.PERSO_LOCKED.ordinal()] = 5;
            } catch (NoSuchFieldError e5) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$IccCardConstants$State[State.NETWORK_SUBSET_LOCKED.ordinal()] = 6;
            } catch (NoSuchFieldError e6) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$IccCardConstants$State[State.SIM_SERVICE_PROVIDER_LOCKED.ordinal()] = 7;
            } catch (NoSuchFieldError e7) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$IccCardConstants$State[State.READY.ordinal()] = 8;
            } catch (NoSuchFieldError e8) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$IccCardConstants$State[State.NOT_READY.ordinal()] = 9;
            } catch (NoSuchFieldError e9) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$IccCardConstants$State[State.PERM_DISABLED.ordinal()] = 10;
            } catch (NoSuchFieldError e10) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$IccCardConstants$State[State.CARD_IO_ERROR.ordinal()] = 11;
            } catch (NoSuchFieldError e11) {
            }
            try {
                f15xec503f36[AppState.APPSTATE_UNKNOWN.ordinal()] = 1;
            } catch (NoSuchFieldError e12) {
            }
            try {
                f15xec503f36[AppState.APPSTATE_DETECTED.ordinal()] = 2;
            } catch (NoSuchFieldError e13) {
            }
            try {
                f15xec503f36[AppState.APPSTATE_PIN.ordinal()] = 3;
            } catch (NoSuchFieldError e14) {
            }
            try {
                f15xec503f36[AppState.APPSTATE_PUK.ordinal()] = 4;
            } catch (NoSuchFieldError e15) {
            }
            try {
                f15xec503f36[AppState.APPSTATE_SUBSCRIPTION_PERSO.ordinal()] = 5;
            } catch (NoSuchFieldError e16) {
            }
            try {
                f15xec503f36[AppState.APPSTATE_READY.ordinal()] = 6;
            } catch (NoSuchFieldError e17) {
            }
        }
    }

    private class iccCardProxyBroadcastReceiver extends BroadcastReceiver {
        private iccCardProxyBroadcastReceiver() {
        }

        public void onReceive(Context context, Intent intent) {
            if ("com.sec.android.app.secsetupwizard.SETUPWIZARD_COMPLETE".equals(intent.getAction())) {
                IccCardProxy.this.prepareStartSimManagement();
            }
        }
    }

    public IccCardProxy(Context context, CommandsInterface ci, PhoneBase phone) {
        this.mCardIndex = null;
        this.mSubscriptionData = null;
        this.mLock = new Object();
        this.mAbsentRegistrants = new RegistrantList();
        this.mPinLockedRegistrants = new RegistrantList();
        this.mNetworkLockedRegistrants = new RegistrantList();
        this.mSPLockedRegistrants = new RegistrantList();
        this.mNetworkSubsetLockedRegistrants = new RegistrantList();
        this.mCurrentAppType = 1;
        this.mUiccController = null;
        this.mUiccCard = null;
        this.mUiccApplication = null;
        this.mIccRecords = null;
        this.mCdmaSSM = null;
        this.mRadioOn = false;
        this.mQuietMode = false;
        this.mInitialized = false;
        this.mExternalState = State.UNKNOWN;
        this.mIsPermDisabledBroadcasted = false;
        this.mPhone = null;
        this.mEmergencyNumber = "112,911";
        this.PROP_ECC_LIST = "ro.ril.ecclist";
        this.PROP_ICC_TYPE = "ril.ICC_TYPE";
        this.mNoSimDefaultEccNum = "112,911,08,000,110,118,119,999";
        this.mAlreadyReadEcc = false;
        this.mInvalidSimNotiDisplayed = false;
        this.mPersoSimLock = false;
        this.mDesiredSimPersoLocked = false;
        this.mResultSIMLOCKINFO = new SimLockInfoResult(0, 0, 0, 0);
        this.mReceiver = new iccCardProxyBroadcastReceiver();
        this.isAlreadyOvercounted = false;
        this.mPLMNwAct = null;
        this.mFPLMN = null;
        this.mOPLMNwAct = null;
        this.mLteOnCdma = false;
        this.mPhone = phone;
        this.mCardIndex = Integer.valueOf(this.mPhone.getPhoneId());
        log("Creating");
        this.mContext = context;
        this.mCi = ci;
        this.mCdmaSSM = CdmaSubscriptionSourceManager.getInstance(context, ci, this, 11, null);
        this.mUiccController = UiccController.getInstance();
        this.mUiccController.registerForIccChanged(this, 3, null);
        ci.registerForOn(this, 2, null);
        ci.registerForOffOrNotAvailable(this, 1, null);
        ci.registerForSap(this, 12, null);
        setExternalState(State.NOT_READY);
    }

    public IccCardProxy(Context context, CommandsInterface ci, int cardIndex, PhoneBase phone) {
        this(context, ci, phone);
        if (this.mCardIndex != null) {
            resetProperties();
        }
        this.mCardIndex = Integer.valueOf(cardIndex);
        setExternalState(State.NOT_READY, false);
    }

    public void dispose() {
        synchronized (this.mLock) {
            log("Disposing");
            this.mUiccController.unregisterForIccChanged(this);
            this.mUiccController = null;
            this.mCi.unregisterForOn(this);
            this.mCi.unregisterForOffOrNotAvailable(this);
            this.mCi.unregisterForSap(this);
            this.mCdmaSSM.dispose(this);
        }
    }

    public void setVoiceRadioTech(int radioTech) {
        synchronized (this.mLock) {
            log("Setting radio tech " + ServiceState.rilRadioTechnologyToString(radioTech));
            if (ServiceState.isGsm(radioTech)) {
                this.mCurrentAppType = 1;
            } else {
                this.mCurrentAppType = 2;
            }
            if ("DCG".equals("DGG") || "DCGG".equals("DGG") || "DCGS".equals("DGG") || "DCGGS".equals("DGG") || "CG".equals("DGG")) {
                log("skip updateQuietMode / set mCurrentAppType : " + this.mCurrentAppType);
                return;
            }
        }
    }

    private void updateQuietMode() {
        boolean newQuietMode = false;
        synchronized (this.mLock) {
            boolean isLteOnCdmaMode;
            boolean oldQuietMode = this.mQuietMode;
            int cdmaSource = -1;
            if (TelephonyManager.getLteOnCdmaModeStatic() == 1) {
                isLteOnCdmaMode = true;
            } else {
                isLteOnCdmaMode = false;
            }
            if (this.mCurrentAppType == 1) {
                newQuietMode = false;
                log("updateQuietMode: 3GPP subscription -> newQuietMode=" + false);
            } else {
                if (isLteOnCdmaMode) {
                    log("updateQuietMode: is cdma/lte device, force IccCardProxy into 3gpp mode");
                    this.mCurrentAppType = 1;
                }
                cdmaSource = this.mCdmaSSM != null ? this.mCdmaSSM.getCdmaSubscriptionSource() : -1;
                if (cdmaSource == 1 && this.mCurrentAppType == 2 && !isLteOnCdmaMode) {
                    newQuietMode = true;
                }
            }
            if (!this.mQuietMode && newQuietMode) {
                log("Switching to QuietMode.");
                setExternalState(State.READY);
                this.mQuietMode = newQuietMode;
            } else if (this.mQuietMode && !newQuietMode) {
                log("updateQuietMode: Switching out from QuietMode. Force broadcast of current state=" + this.mExternalState);
                this.mQuietMode = newQuietMode;
                setExternalState(this.mExternalState, true);
            }
            log("updateQuietMode: QuietMode is " + this.mQuietMode + " (app_type=" + this.mCurrentAppType + " isLteOnCdmaMode=" + isLteOnCdmaMode + " cdmaSource=" + cdmaSource + ")");
            this.mInitialized = true;
            sendMessage(obtainMessage(3));
        }
    }

    public void handleMessage(Message msg) {
        log("IccCardProxy handleMessage : " + msg.what);
        int slotId;
        AsyncResult ar;
        switch (msg.what) {
            case 1:
                this.mRadioOn = false;
                if (RadioState.RADIO_UNAVAILABLE == this.mCi.getRadioState()) {
                    setExternalState(State.NOT_READY);
                    return;
                }
                return;
            case 2:
                this.mRadioOn = true;
                return;
            case 3:
                updateIccAvailability();
                return;
            case 4:
                this.mAbsentRegistrants.notifyRegistrants();
                setExternalState(State.ABSENT);
                return;
            case 5:
                processLockedState();
                getEccListFromSim();
                return;
            case 6:
                setExternalState(State.READY);
                getEccListFromSim();
                if ("KTT".equals("")) {
                    reloadPLMNs();
                }
                prepareStartSimManagement();
                return;
            case 7:
                if ("DCG".equals("DGG") || "DCGG".equals("DGG") || "DCGS".equals("DGG") || "DCGGS".equals("DGG") || "CG".equals("DGG")) {
                    String OperatorNumeric = getSystemProperty("gsm.sim.operator.numeric", this.mCardIndex.intValue(), "0");
                    log("OperatorNumeric = " + OperatorNumeric);
                    if (!"".equals(OperatorNumeric)) {
                        return;
                    }
                }
                if (this.mIccRecords != null) {
                    String operator = this.mIccRecords.getOperatorNumeric();
                    slotId = this.mCardIndex.intValue();
                    log("operator = " + operator + " slotId = " + slotId);
                    if (operator != null) {
                        log("update icc_operator_numeric=" + operator);
                        setSystemProperty("gsm.sim.operator.numeric", slotId, operator);
                        String countryCode = operator.substring(0, 3);
                        if (countryCode != null) {
                            setSystemProperty("gsm.sim.operator.iso-country", slotId, MccTable.countryCodeForMcc(Integer.parseInt(countryCode)));
                        } else {
                            loge("EVENT_RECORDS_LOADED Country code is null");
                        }
                        long[] subId = SubscriptionController.getInstance().getSubId(slotId);
                        if (subId[0] == SubscriptionController.getInstance().getDefaultSubId()) {
                            log("update mccmnc=" + operator + " config for default subscription.");
                            MccTable.updateMccMncConfiguration(this.mContext, operator, false);
                        }
                        SubscriptionController.getInstance().setMccMnc(operator, subId[0]);
                    } else {
                        loge("EVENT_RECORDS_LOADED Operator name is null");
                    }
                }
                if (this.mUiccCard == null || this.mUiccCard.areCarrierPriviligeRulesLoaded()) {
                    onRecordsLoaded();
                    return;
                } else {
                    this.mUiccCard.registerForCarrierPrivilegeRulesLoaded(this, EVENT_CARRIER_PRIVILIGES_LOADED, null);
                    return;
                }
            case 8:
                broadcastIccStateChangedIntent("IMSI", null);
                return;
            case 9:
                this.mNetworkLockedRegistrants.notifyRegistrants();
                setExternalState(State.NETWORK_LOCKED);
                return;
            case 11:
                return;
            case 12:
                byte[] sapdata = (byte[]) ((AsyncResult) msg.obj).result;
                if (sapdata[0] != (byte) 2) {
                    return;
                }
                if (sapdata[1] == (byte) 4) {
                    Rlog.d(LOG_TAG, "EVENT_SAP_NOTIFICATION - SAP_CARD_STATUS_INSERTED : SIM state is changed to UNKNOWN by SAP connection");
                    setExternalState(State.UNKNOWN);
                    return;
                } else if (sapdata[1] == (byte) 3) {
                    Rlog.d(LOG_TAG, "EVENT_SAP_NOTIFICATION - SAP_CARD_STATUS_REMOVED");
                    updateExternalState();
                    return;
                } else {
                    return;
                }
            case 19:
                ar = (AsyncResult) msg.obj;
                if (ar.exception == null) {
                    this.mPLMNwAct = IccUtils.bytesToHexString((byte[]) ar.result);
                    log("EVENT_QUERY_PLMNWACT_DONE:" + this.mPLMNwAct);
                    return;
                }
                return;
            case 20:
                ar = (AsyncResult) msg.obj;
                if (ar.exception == null) {
                    this.mFPLMN = IccUtils.bytesToHexString((byte[]) ar.result);
                    log("EVENT_QUERY_FPLMN_DONE:" + this.mFPLMN);
                    return;
                }
                return;
            case 21:
                ar = (AsyncResult) msg.obj;
                if (ar.exception == null) {
                    this.mOPLMNwAct = IccUtils.bytesToHexString((byte[]) ar.result);
                    log("EVENT_QUERY_OPLMNWACT_DONE:" + this.mOPLMNwAct);
                    return;
                }
                return;
            case 51:
                broadcastIccStateChangedIntent("MDN", null);
                return;
            case 52:
                broadcastIccStateChangedIntent("ICCID", null);
                return;
            case 100:
                simLockInfoRefresh(obtainMessage(106));
                prepareStartSimManagement();
                return;
            case 101:
                this.mNetworkSubsetLockedRegistrants.notifyRegistrants();
                setExternalState(State.NETWORK_SUBSET_LOCKED);
                return;
            case 102:
                this.mSPLockedRegistrants.notifyRegistrants();
                setExternalState(State.SIM_SERVICE_PROVIDER_LOCKED);
                return;
            case 104:
                logi("EVENT_SIM_PIN_LOCK_INFO_DONE");
                ar = (AsyncResult) msg.obj;
                if (ar.exception != null) {
                    loge("Error in get SIM LOCK INFO" + ar.exception);
                } else {
                    this.mResultSIMLOCKINFO.setLockInfoResult((SimLockInfoResult) ar.result);
                }
                this.mCi.getSIMLockInfo(1, 9, obtainMessage(EVENT_SIM_PIN2_LOCK_INFO_DONE, (Message) ar.userObj));
                prepareStartSimManagement();
                return;
            case EVENT_SIM_PIN2_LOCK_INFO_DONE /*105*/:
                logi("EVENT_SIM_PIN2_LOCK_INFO_DONE");
                ar = (AsyncResult) msg.obj;
                if (ar.exception != null) {
                    loge("Error in get SIM LOCK INFO" + ar.exception);
                } else {
                    this.mResultSIMLOCKINFO.setLockInfoResult((SimLockInfoResult) ar.result);
                }
                AsyncResult.forMessage((Message) ar.userObj).exception = ar.exception;
                ((Message) ar.userObj).sendToTarget();
                prepareStartSimManagement();
                return;
            case 106:
                logi("EVENT_SIM_PIN_LOCK_INFO_REFRESH_DONE");
                ar = (AsyncResult) msg.obj;
                if (ar.userObj != null) {
                    handleMessage((Message) ar.userObj);
                    ar.userObj = null;
                }
                prepareStartSimManagement();
                return;
            case EVENT_SIM_NEED_LOCK_INFO_REFRESH /*107*/:
                logi("EVENT_SIM_NEED_LOCK_INFO_REFRESH");
                ar = (AsyncResult) msg.obj;
                if (ar.exception != null) {
                    log("exception is occurred : " + ar.exception);
                }
                AsyncResult.forMessage((Message) ar.userObj).exception = ar.exception;
                ((Message) ar.userObj).sendToTarget();
                return;
            case 110:
                ar = (AsyncResult) msg.obj;
                if (ar.exception != null) {
                    return;
                }
                if (ar.result != null) {
                    byte[] simLock = (byte[]) ar.result;
                    logi("EVENT_GET_PERSO_STATUS_COMPLETE" + simLock[0] + "/" + simLock[1]);
                    if (simLock[0] == (byte) 4) {
                        this.mPersoSimLock = true;
                        return;
                    } else {
                        this.mPersoSimLock = false;
                        return;
                    }
                }
                loge("EVENT_GET_PERSO_STATUS_COMPLETE ar.result null");
                return;
            case EVENT_QUERY_FACILITY_SIM_PERSO_DONE /*111*/:
                onQueryFacilitySimPerso((AsyncResult) msg.obj);
                return;
            case EVENT_CHANGE_FACILITY_SIM_PERSO_DONE /*112*/:
                ar = (AsyncResult) msg.obj;
                if (ar.exception == null) {
                    this.mPersoSimLock = this.mDesiredSimPersoLocked;
                    this.mDesiredSimPersoLocked = false;
                    log("EVENT_CHANGE_FACILITY_SIM_PERSO_DONE: mPersoSimLock= " + this.mPersoSimLock);
                } else {
                    log("Error change facility lock with exception " + ar.exception);
                }
                AsyncResult.forMessage((Message) ar.userObj).exception = ar.exception;
                ((Message) ar.userObj).sendToTarget();
                return;
            case EVENT_CHANGE_SIM_PERSO_PASSWORD_DONE /*113*/:
                ar = (AsyncResult) msg.obj;
                if (ar.exception != null) {
                    log("Error in change sim password with exception" + ar.exception);
                }
                AsyncResult.forMessage((Message) ar.userObj).exception = ar.exception;
                ((Message) ar.userObj).sendToTarget();
                return;
            case 114:
                ar = (AsyncResult) msg.obj;
                if (ar.exception != null) {
                    log("Error in enter sim password with exception" + ar.exception);
                }
                AsyncResult.forMessage((Message) ar.userObj).exception = ar.exception;
                ((Message) ar.userObj).sendToTarget();
                return;
            case 200:
                ar = (AsyncResult) msg.obj;
                if (ar.exception != null) {
                    loge("Failed to get Ecc List from SIM");
                    this.mEmergencyNumber = "";
                    if (this.mPhone instanceof GSMPhone) {
                        ((GSMPhone) this.mPhone).updateEccNum(this.mEmergencyNumber);
                        return;
                    } else {
                        loge("Invalid Phone, so can't call setEmergencyNumbers()");
                        return;
                    }
                }
                StringBuffer eccString = new StringBuffer("");
                String simType = getSystemProperty("ril.ICC_TYPE", this.mCardIndex.intValue(), "0");
                if ("1".equals(simType)) {
                    eccString = read2GEccList(ar);
                } else if ("2".equals(simType)) {
                    eccString = read3GEccList(ar);
                } else {
                    loge("Invalid Phone, so can't read EccList");
                }
                this.mEmergencyNumber = eccString.toString();
                if (this.mPhone instanceof GSMPhone) {
                    ((GSMPhone) this.mPhone).updateEccNum(this.mEmergencyNumber);
                    this.mAlreadyReadEcc = true;
                    return;
                }
                loge("Invalid Phone so can't call setEmergencyNumbers()");
                return;
            case EVENT_ICC_RECORD_EVENTS /*500*/:
                if (this.mCurrentAppType == 1 && this.mIccRecords != null) {
                    slotId = this.mCardIndex.intValue();
                    if (((Integer) msg.obj.result).intValue() == 2) {
                        setSystemProperty("gsm.sim.operator.alpha", slotId, this.mIccRecords.getServiceProviderName());
                        return;
                    }
                    return;
                }
                return;
            case EVENT_SUBSCRIPTION_ACTIVATED /*501*/:
                log("EVENT_SUBSCRIPTION_ACTIVATED");
                onSubscriptionActivated();
                return;
            case EVENT_SUBSCRIPTION_DEACTIVATED /*502*/:
                log("EVENT_SUBSCRIPTION_DEACTIVATED");
                onSubscriptionDeactivated();
                return;
            case EVENT_CARRIER_PRIVILIGES_LOADED /*503*/:
                log("EVENT_CARRIER_PRIVILEGES_LOADED");
                if (this.mUiccCard != null) {
                    this.mUiccCard.unregisterForCarrierPrivilegeRulesLoaded(this);
                }
                onRecordsLoaded();
                return;
            case EVENT_SETUP_WIZARD_NOT_START /*510*/:
                startSimManagement();
                return;
            default:
                loge("Unhandled message with number: " + msg.what);
                return;
        }
    }

    private void onSubscriptionActivated() {
        updateIccAvailability();
        updateStateProperty();
    }

    private void onSubscriptionDeactivated() {
        resetProperties();
        this.mSubscriptionData = null;
        updateIccAvailability();
        updateStateProperty();
    }

    private void onRecordsLoaded() {
        broadcastIccStateChangedIntent("LOADED", null);
    }

    private void updateIccAvailability() {
        synchronized (this.mLock) {
            UiccCard newCard = this.mUiccController.getUiccCard(this.mCardIndex.intValue());
            CardState state = CardState.CARDSTATE_ABSENT;
            UiccCardApplication newApp = null;
            IccRecords newRecords = null;
            if (newCard != null) {
                state = newCard.getCardState();
                if ("DCG".equals("DGG") || "DCGG".equals("DGG") || "DCGS".equals("DGG") || "DCGGS".equals("DGG") || "CG".equals("DGG")) {
                    log("updateIccAvailability mCardIndex: " + this.mCardIndex);
                    if (this.mCardIndex.intValue() == 0) {
                        String iccType = getSystemProperty("ril.ICC_TYPE", this.mCardIndex.intValue(), "0");
                        log("iccType = " + iccType);
                        if ("1".equals(iccType) || "2".equals(iccType)) {
                            this.mCurrentAppType = 1;
                        }
                    }
                }
                if ("LGT".equals("")) {
                    newApp = newCard.getApplication(1);
                } else {
                    newApp = newCard.getApplication(this.mCurrentAppType);
                }
                if (this.mCurrentAppType == 2) {
                    UiccCardApplication newUsimApp = newCard.getApplication(1);
                    if (newApp == null || !(newApp.getState() != AppState.APPSTATE_DETECTED || newUsimApp == null || newUsimApp.getState() == AppState.APPSTATE_DETECTED || newUsimApp.getState() == AppState.APPSTATE_UNKNOWN)) {
                        newApp = newCard.getApplication(1);
                    }
                }
                if (newApp != null) {
                    newRecords = newApp.getIccRecords();
                }
            }
            if (!(this.mIccRecords == newRecords && this.mUiccApplication == newApp && this.mUiccCard == newCard)) {
                log("Icc changed. Reregestering.");
                unregisterUiccCardEvents();
                this.mUiccCard = newCard;
                this.mUiccApplication = newApp;
                this.mIccRecords = newRecords;
                registerUiccCardEvents();
            }
            updateExternalState();
            updateSimLockInfo();
        }
    }

    void resetProperties() {
        if (this.mCurrentAppType == 1) {
            log("update icc_operator_numeric=");
            setSystemProperty("gsm.sim.operator.numeric", this.mCardIndex.intValue(), "");
            setSystemProperty("gsm.sim.operator.iso-country", this.mCardIndex.intValue(), "");
            setSystemProperty("gsm.sim.operator.alpha", this.mCardIndex.intValue(), "");
        }
    }

    private void HandleDetectedState() {
        if (this.mUiccApplication.getPin1State() == PinState.PINSTATE_ENABLED_PERM_BLOCKED) {
            log("Send PermBlock Intent in DETECTED + BLOCKED PIN State.");
            makeInvalidSIMNotification(State.PERM_DISABLED);
            setExternalState(State.PERM_DISABLED);
            sendIntent();
            return;
        }
        setExternalState(State.UNKNOWN);
    }

    private void updateExternalState() {
        String iccType;
        if ("DCGS".equals("DGG") || "DCGG".equals("DGG") || "DCGGS".equals("DGG") || "DGG".equals("DGG")) {
            if (this.mUiccCard == null) {
                setExternalState(State.NOT_READY);
                return;
            } else if (this.mUiccCard.getCardState() == CardState.CARDSTATE_ABSENT) {
                iccType = getSystemProperty("ril.ICC_TYPE", this.mCardIndex.intValue(), "0");
                log("updateExternalState iccType = " + iccType);
                if (iccType == null || iccType.equals("") || iccType.equals("0")) {
                    setExternalState(State.ABSENT);
                    return;
                }
                return;
            }
        } else if (this.mUiccCard == null || this.mUiccCard.getCardState() == CardState.CARDSTATE_ABSENT) {
            if (this.mRadioOn) {
                iccType = getSystemProperty("ril.ICC_TYPE", this.mCardIndex.intValue(), "0");
                log("updateExternalState iccType = " + iccType);
                if (iccType == null || iccType.equals("")) {
                    setExternalState(State.ABSENT);
                    return;
                }
                return;
            }
            setExternalState(State.NOT_READY);
            return;
        }
        if (this.mUiccCard.getCardState() == CardState.CARDSTATE_ERROR) {
            if (PIN_MODE_SIM_CRASH.equals(SystemProperties.get(SIM_PIN_MODE))) {
                this.mPhone.getContext().sendBroadcast(new Intent("android.provider.Telephony.SPN_STRINGS_UPDATED"));
                makeInvalidSIMNotification(State.CARD_IO_ERROR);
                setExternalState(State.CARD_IO_ERROR);
                return;
            }
            setExternalState(State.UNKNOWN);
        } else if (this.mUiccApplication == null) {
            setExternalState(State.NOT_READY);
        } else {
            PinState pin1State = PinState.PINSTATE_UNKNOWN;
            switch (C01271.f15xec503f36[this.mUiccApplication.getState().ordinal()]) {
                case 1:
                    setExternalState(State.UNKNOWN);
                    return;
                case 2:
                    HandleDetectedState();
                    return;
                case 3:
                    setExternalState(State.PIN_REQUIRED);
                    return;
                case 4:
                    if (this.mUiccApplication.getPin1State() == PinState.PINSTATE_ENABLED_PERM_BLOCKED) {
                        makeInvalidSIMNotification(State.PERM_DISABLED);
                        setExternalState(State.PERM_DISABLED);
                        sendIntent();
                        return;
                    }
                    setExternalState(State.PUK_REQUIRED);
                    return;
                case 5:
                    if (this.mUiccApplication.getPersoSubState() == PersoSubState.PERSOSUBSTATE_SIM_NETWORK) {
                        setExternalState(State.NETWORK_LOCKED);
                        return;
                    } else if (this.mUiccApplication.getPersoSubState() == PersoSubState.PERSOSUBSTATE_SIM_NETWORK_SUBSET) {
                        setExternalState(State.NETWORK_SUBSET_LOCKED);
                        makeInvalidSIMNotification(State.NETWORK_LOCKED);
                        return;
                    } else if (this.mUiccApplication.getPersoSubState() == PersoSubState.PERSOSUBSTATE_SIM_SERVICE_PROVIDER) {
                        setExternalState(State.SIM_SERVICE_PROVIDER_LOCKED);
                        makeInvalidSIMNotification(State.NETWORK_LOCKED);
                        return;
                    } else {
                        setExternalState(State.UNKNOWN);
                        return;
                    }
                case 6:
                    if (this.mInvalidSimNotiDisplayed) {
                        removeInvalidSIMNotification();
                    }
                    setExternalState(State.READY);
                    return;
                default:
                    return;
            }
        }
    }

    private void makeInvalidSIMNotification(State simState) {
        this.mInvalidSimNotiDisplayed = true;
        Resources res = this.mContext.getResources();
        String title = res.getString(17041516);
        String text = null;
        if (simState == State.PERM_DISABLED) {
            text = res.getString(17041518);
        } else if (simState == State.NETWORK_LOCKED) {
            text = res.getString(17041519);
        }
        Builder builder = new Builder(this.mContext);
        builder.setContentTitle(title);
        builder.setContentText(text);
        builder.setSmallIcon(17304426);
        builder.setWhen(0);
        builder.setTicker(title);
        builder.setOngoing(true);
        ((NotificationManager) this.mContext.getSystemService("notification")).notify(REQ_NO_SIM_NOTIFICATION, builder.getNotification());
    }

    private void removeInvalidSIMNotification() {
        this.mInvalidSimNotiDisplayed = false;
        ((NotificationManager) this.mPhone.getContext().getSystemService("notification")).cancel(REQ_NO_SIM_NOTIFICATION);
    }

    private void registerUiccCardEvents() {
        if (this.mUiccCard != null) {
            this.mUiccCard.registerForAbsent(this, 4, null);
        }
        if (this.mUiccApplication != null) {
            this.mUiccApplication.registerForReady(this, 6, null);
            this.mUiccApplication.registerForLocked(this, 5, null);
            this.mUiccApplication.registerForNetworkLocked(this, 9, null);
            this.mUiccApplication.registerForNetworkSubsetLocked(this, 101, null);
            this.mUiccApplication.registerForSPLocked(this, 102, null);
        }
        if (this.mIccRecords != null) {
            this.mIccRecords.registerForImsiReady(this, 8, null);
            this.mIccRecords.registerForRecordsLoaded(this, 7, null);
            this.mIccRecords.registerForRecordsEvents(this, EVENT_ICC_RECORD_EVENTS, null);
            if ("DCG".equals("DGG") || "DCGG".equals("DGG") || "DCGS".equals("DGG") || "DCGGS".equals("DGG") || "CG".equals("DGG")) {
                this.mIccRecords.registerForMdnReady(this, 51, null);
                this.mIccRecords.registerForIccIdReady(this, 52, null);
            }
        }
    }

    private void unregisterUiccCardEvents() {
        if (this.mUiccCard != null) {
            this.mUiccCard.unregisterForAbsent(this);
        }
        if (this.mUiccApplication != null) {
            this.mUiccApplication.unregisterForReady(this);
        }
        if (this.mUiccApplication != null) {
            this.mUiccApplication.unregisterForLocked(this);
        }
        if (this.mUiccApplication != null) {
            this.mUiccApplication.unregisterForNetworkLocked(this);
        }
        if (this.mUiccApplication != null) {
            this.mUiccApplication.unregisterForNetworkSubsetLocked(this);
        }
        if (this.mUiccApplication != null) {
            this.mUiccApplication.unregisterForSPLocked(this);
        }
        if (this.mIccRecords != null) {
            this.mIccRecords.unregisterForImsiReady(this);
        }
        if (this.mIccRecords != null) {
            this.mIccRecords.unregisterForRecordsLoaded(this);
        }
        if (this.mIccRecords != null) {
            this.mIccRecords.unregisterForRecordsEvents(this);
        }
        if ("DCG".equals("DGG") || "DCGG".equals("DGG") || "DCGS".equals("DGG") || "DCGGS".equals("DGG") || "CG".equals("DGG")) {
            if (this.mIccRecords != null) {
                this.mIccRecords.unregisterForMdnReady(this);
            }
            if (this.mIccRecords != null) {
                this.mIccRecords.unregisterForIccIdReady(this);
            }
        }
    }

    private void updateStateProperty() {
        setSystemProperty("gsm.sim.state", this.mCardIndex.intValue(), getState().toString());
    }

    private void broadcastIccStateChangedIntent(String value, String reason) {
        synchronized (this.mLock) {
            if (this.mCardIndex == null) {
                loge("broadcastIccStateChangedIntent: Card Index is not set; Return!!");
            } else if (this.mQuietMode) {
                log("QuietMode: NOT Broadcasting intent ACTION_SIM_STATE_CHANGED " + value + " reason " + reason);
            } else {
                if ("DGG".equals("DGG") && !sIsStartSimManagement && ("IMSI".equals(value) || "ABSENT".equals(value))) {
                    if (this.mPhone.getPhoneId() == 0) {
                        mCardInfoAvailable[0] = true;
                    } else {
                        mCardInfoAvailable[1] = true;
                    }
                    if (mCardInfoAvailable[0] && mCardInfoAvailable[1]) {
                        logi("Calling startSimManagement");
                        startSimManagement();
                    } else {
                        logi("All card is not stanby yet, skip calling startSimManagement");
                    }
                }
                Intent intent = new Intent("android.intent.action.SIM_STATE_CHANGED");
                intent.addFlags(536870912);
                intent.putExtra("phoneName", "Phone");
                intent.putExtra(IccCard.INTENT_KEY_ICC_STATE, value);
                intent.putExtra("reason", reason);
                if ("DCG".equals("DGG") || "DCGG".equals("DGG") || "DCGS".equals("DGG") || "DCGGS".equals("DGG") || "CG".equals("DGG")) {
                    intent.putExtra("ICC_TYPE", this.mPhone.getPhoneName());
                }
                SubscriptionManager.putPhoneIdAndSubIdExtra(intent, this.mCardIndex.intValue());
                log("Broadcasting intent ACTION_SIM_STATE_CHANGED " + value + " reason " + reason + " for mCardIndex : " + this.mCardIndex);
                ActivityManagerNative.broadcastStickyIntent(intent, "android.permission.READ_PHONE_STATE", -1);
            }
        }
    }

    /* JADX WARNING: inconsistent code. */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    private void setExternalState(com.android.internal.telephony.IccCardConstants.State r5, boolean r6) {
        /*
        r4 = this;
        r1 = r4.mLock;
        monitor-enter(r1);
        r0 = r4.mCardIndex;	 Catch:{ all -> 0x0016 }
        if (r0 != 0) goto L_0x000e;
    L_0x0007:
        r0 = "setExternalState: Card Index is not set; Return!!";
        r4.loge(r0);	 Catch:{ all -> 0x0016 }
        monitor-exit(r1);	 Catch:{ all -> 0x0016 }
    L_0x000d:
        return;
    L_0x000e:
        if (r6 != 0) goto L_0x0019;
    L_0x0010:
        r0 = r4.mExternalState;	 Catch:{ all -> 0x0016 }
        if (r5 != r0) goto L_0x0019;
    L_0x0014:
        monitor-exit(r1);	 Catch:{ all -> 0x0016 }
        goto L_0x000d;
    L_0x0016:
        r0 = move-exception;
        monitor-exit(r1);	 Catch:{ all -> 0x0016 }
        throw r0;
    L_0x0019:
        r4.mExternalState = r5;	 Catch:{ all -> 0x0016 }
        r0 = "gsm.sim.state";
        r2 = r4.mCardIndex;	 Catch:{ all -> 0x0016 }
        r2 = r2.intValue();	 Catch:{ all -> 0x0016 }
        r3 = r4.getState();	 Catch:{ all -> 0x0016 }
        r3 = r3.toString();	 Catch:{ all -> 0x0016 }
        r4.setSystemProperty(r0, r2, r3);	 Catch:{ all -> 0x0016 }
        r0 = r4.mExternalState;	 Catch:{ all -> 0x0016 }
        r0 = r4.getIccStateIntentString(r0);	 Catch:{ all -> 0x0016 }
        r2 = r4.mExternalState;	 Catch:{ all -> 0x0016 }
        r2 = r4.getIccStateReason(r2);	 Catch:{ all -> 0x0016 }
        r4.broadcastIccStateChangedIntent(r0, r2);	 Catch:{ all -> 0x0016 }
        r0 = com.android.internal.telephony.IccCardConstants.State.ABSENT;	 Catch:{ all -> 0x0016 }
        r2 = r4.mExternalState;	 Catch:{ all -> 0x0016 }
        if (r0 != r2) goto L_0x0048;
    L_0x0043:
        r0 = r4.mAbsentRegistrants;	 Catch:{ all -> 0x0016 }
        r0.notifyRegistrants();	 Catch:{ all -> 0x0016 }
    L_0x0048:
        monitor-exit(r1);	 Catch:{ all -> 0x0016 }
        goto L_0x000d;
        */
        throw new UnsupportedOperationException("Method not decompiled: com.android.internal.telephony.uicc.IccCardProxy.setExternalState(com.android.internal.telephony.IccCardConstants$State, boolean):void");
    }

    /* JADX WARNING: inconsistent code. */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    private void processLockedState() {
        /*
        r5 = this;
        r3 = r5.mLock;
        monitor-enter(r3);
        r2 = r5.mUiccApplication;	 Catch:{ all -> 0x001a }
        if (r2 != 0) goto L_0x0009;
    L_0x0007:
        monitor-exit(r3);	 Catch:{ all -> 0x001a }
    L_0x0008:
        return;
    L_0x0009:
        r2 = r5.mUiccApplication;	 Catch:{ all -> 0x001a }
        r1 = r2.getPin1State();	 Catch:{ all -> 0x001a }
        r2 = com.android.internal.telephony.uicc.IccCardStatus.PinState.PINSTATE_ENABLED_PERM_BLOCKED;	 Catch:{ all -> 0x001a }
        if (r1 != r2) goto L_0x001d;
    L_0x0013:
        r2 = com.android.internal.telephony.IccCardConstants.State.PERM_DISABLED;	 Catch:{ all -> 0x001a }
        r5.setExternalState(r2);	 Catch:{ all -> 0x001a }
        monitor-exit(r3);	 Catch:{ all -> 0x001a }
        goto L_0x0008;
    L_0x001a:
        r2 = move-exception;
        monitor-exit(r3);	 Catch:{ all -> 0x001a }
        throw r2;
    L_0x001d:
        r2 = r5.mUiccApplication;	 Catch:{ all -> 0x001a }
        r0 = r2.getState();	 Catch:{ all -> 0x001a }
        r2 = com.android.internal.telephony.uicc.IccCardProxy.C01271.f15xec503f36;	 Catch:{ all -> 0x001a }
        r4 = r0.ordinal();	 Catch:{ all -> 0x001a }
        r2 = r2[r4];	 Catch:{ all -> 0x001a }
        switch(r2) {
            case 3: goto L_0x0030;
            case 4: goto L_0x003b;
            default: goto L_0x002e;
        };	 Catch:{ all -> 0x001a }
    L_0x002e:
        monitor-exit(r3);	 Catch:{ all -> 0x001a }
        goto L_0x0008;
    L_0x0030:
        r2 = r5.mPinLockedRegistrants;	 Catch:{ all -> 0x001a }
        r2.notifyRegistrants();	 Catch:{ all -> 0x001a }
        r2 = com.android.internal.telephony.IccCardConstants.State.PIN_REQUIRED;	 Catch:{ all -> 0x001a }
        r5.setExternalState(r2);	 Catch:{ all -> 0x001a }
        goto L_0x002e;
    L_0x003b:
        r2 = com.android.internal.telephony.IccCardConstants.State.PUK_REQUIRED;	 Catch:{ all -> 0x001a }
        r5.setExternalState(r2);	 Catch:{ all -> 0x001a }
        goto L_0x002e;
        */
        throw new UnsupportedOperationException("Method not decompiled: com.android.internal.telephony.uicc.IccCardProxy.processLockedState():void");
    }

    private void setExternalState(State newState) {
        setExternalState(newState, false);
    }

    public boolean getIccRecordsLoaded() {
        boolean recordsLoaded;
        synchronized (this.mLock) {
            if (this.mIccRecords != null) {
                recordsLoaded = this.mIccRecords.getRecordsLoaded();
            } else {
                recordsLoaded = false;
            }
        }
        return recordsLoaded;
    }

    private String getIccStateIntentString(State state) {
        switch (C01271.$SwitchMap$com$android$internal$telephony$IccCardConstants$State[state.ordinal()]) {
            case 1:
                return "ABSENT";
            case 2:
                return "LOCKED";
            case 3:
                return "LOCKED";
            case 4:
                return "LOCKED";
            case 5:
                return "LOCKED";
            case 6:
                return "LOCKED";
            case 7:
                return "LOCKED";
            case 8:
                return "READY";
            case 9:
                return "NOT_READY";
            case 10:
                return "LOCKED";
            case 11:
                return "CARD_IO_ERROR";
            default:
                return "UNKNOWN";
        }
    }

    private String getIccStateReason(State state) {
        switch (C01271.$SwitchMap$com$android$internal$telephony$IccCardConstants$State[state.ordinal()]) {
            case 2:
                return "PIN";
            case 3:
                return "PUK";
            case 4:
                return "NETWORK";
            case 5:
                return "PERSO";
            case 10:
                return "PERM_DISABLED";
            case 11:
                return "CARD_IO_ERROR";
            default:
                return null;
        }
    }

    public State getState() {
        State state;
        synchronized (this.mLock) {
            state = this.mExternalState;
        }
        return state;
    }

    public IccRecords getIccRecords() {
        IccRecords iccRecords;
        synchronized (this.mLock) {
            iccRecords = this.mIccRecords;
        }
        return iccRecords;
    }

    public IccFileHandler getIccFileHandler() {
        IccFileHandler iccFileHandler;
        synchronized (this.mLock) {
            if (this.mUiccApplication != null) {
                iccFileHandler = this.mUiccApplication.getIccFileHandler();
            } else {
                iccFileHandler = null;
            }
        }
        return iccFileHandler;
    }

    public void registerForAbsent(Handler h, int what, Object obj) {
        synchronized (this.mLock) {
            Registrant r = new Registrant(h, what, obj);
            this.mAbsentRegistrants.add(r);
            if (getState() == State.ABSENT) {
                r.notifyRegistrant();
            }
        }
    }

    public void unregisterForAbsent(Handler h) {
        synchronized (this.mLock) {
            this.mAbsentRegistrants.remove(h);
        }
    }

    public void registerForNetworkLocked(Handler h, int what, Object obj) {
        synchronized (this.mLock) {
            Registrant r = new Registrant(h, what, obj);
            this.mNetworkLockedRegistrants.add(r);
            if (getState() == State.NETWORK_LOCKED) {
                r.notifyRegistrant();
            }
        }
    }

    public void unregisterForNetworkLocked(Handler h) {
        synchronized (this.mLock) {
            this.mNetworkLockedRegistrants.remove(h);
        }
    }

    public void registerForNetworkSubsetLocked(Handler h, int what, Object obj) {
        Registrant r = new Registrant(h, what, obj);
        this.mNetworkSubsetLockedRegistrants.add(r);
        if (getState() == State.NETWORK_SUBSET_LOCKED) {
            r.notifyRegistrant();
        }
    }

    public void unregisterForNetworkSubsetLocked(Handler h) {
        this.mNetworkSubsetLockedRegistrants.remove(h);
    }

    public void registerForSPLocked(Handler h, int what, Object obj) {
        Registrant r = new Registrant(h, what, obj);
        this.mSPLockedRegistrants.add(r);
        if (getState() == State.SIM_SERVICE_PROVIDER_LOCKED) {
            r.notifyRegistrant();
        }
    }

    public void unregisterForSPLocked(Handler h) {
        this.mSPLockedRegistrants.remove(h);
    }

    public void registerForLocked(Handler h, int what, Object obj) {
        synchronized (this.mLock) {
            Registrant r = new Registrant(h, what, obj);
            this.mPinLockedRegistrants.add(r);
            if (getState().isPinLocked()) {
                r.notifyRegistrant();
            }
        }
    }

    public void unregisterForLocked(Handler h) {
        synchronized (this.mLock) {
            this.mPinLockedRegistrants.remove(h);
        }
    }

    /* JADX WARNING: inconsistent code. */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    public void supplyPin(java.lang.String r4, android.os.Message r5) {
        /*
        r3 = this;
        r2 = r3.mLock;
        monitor-enter(r2);
        r1 = r3.mUiccApplication;	 Catch:{ all -> 0x0022 }
        if (r1 == 0) goto L_0x000e;
    L_0x0007:
        r1 = r3.mUiccApplication;	 Catch:{ all -> 0x0022 }
        r1.supplyPin(r4, r5);	 Catch:{ all -> 0x0022 }
    L_0x000c:
        monitor-exit(r2);	 Catch:{ all -> 0x0022 }
    L_0x000d:
        return;
    L_0x000e:
        if (r5 == 0) goto L_0x000c;
    L_0x0010:
        r0 = new java.lang.RuntimeException;	 Catch:{ all -> 0x0022 }
        r1 = "ICC card is absent.";
        r0.<init>(r1);	 Catch:{ all -> 0x0022 }
        r1 = android.os.AsyncResult.forMessage(r5);	 Catch:{ all -> 0x0022 }
        r1.exception = r0;	 Catch:{ all -> 0x0022 }
        r5.sendToTarget();	 Catch:{ all -> 0x0022 }
        monitor-exit(r2);	 Catch:{ all -> 0x0022 }
        goto L_0x000d;
    L_0x0022:
        r1 = move-exception;
        monitor-exit(r2);	 Catch:{ all -> 0x0022 }
        throw r1;
        */
        throw new UnsupportedOperationException("Method not decompiled: com.android.internal.telephony.uicc.IccCardProxy.supplyPin(java.lang.String, android.os.Message):void");
    }

    /* JADX WARNING: inconsistent code. */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    public void supplyPuk(java.lang.String r4, java.lang.String r5, android.os.Message r6) {
        /*
        r3 = this;
        r2 = r3.mLock;
        monitor-enter(r2);
        r1 = r3.mUiccApplication;	 Catch:{ all -> 0x0022 }
        if (r1 == 0) goto L_0x000e;
    L_0x0007:
        r1 = r3.mUiccApplication;	 Catch:{ all -> 0x0022 }
        r1.supplyPuk(r4, r5, r6);	 Catch:{ all -> 0x0022 }
    L_0x000c:
        monitor-exit(r2);	 Catch:{ all -> 0x0022 }
    L_0x000d:
        return;
    L_0x000e:
        if (r6 == 0) goto L_0x000c;
    L_0x0010:
        r0 = new java.lang.RuntimeException;	 Catch:{ all -> 0x0022 }
        r1 = "ICC card is absent.";
        r0.<init>(r1);	 Catch:{ all -> 0x0022 }
        r1 = android.os.AsyncResult.forMessage(r6);	 Catch:{ all -> 0x0022 }
        r1.exception = r0;	 Catch:{ all -> 0x0022 }
        r6.sendToTarget();	 Catch:{ all -> 0x0022 }
        monitor-exit(r2);	 Catch:{ all -> 0x0022 }
        goto L_0x000d;
    L_0x0022:
        r1 = move-exception;
        monitor-exit(r2);	 Catch:{ all -> 0x0022 }
        throw r1;
        */
        throw new UnsupportedOperationException("Method not decompiled: com.android.internal.telephony.uicc.IccCardProxy.supplyPuk(java.lang.String, java.lang.String, android.os.Message):void");
    }

    /* JADX WARNING: inconsistent code. */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    public void supplyPin2(java.lang.String r4, android.os.Message r5) {
        /*
        r3 = this;
        r2 = r3.mLock;
        monitor-enter(r2);
        r1 = r3.mUiccApplication;	 Catch:{ all -> 0x0022 }
        if (r1 == 0) goto L_0x000e;
    L_0x0007:
        r1 = r3.mUiccApplication;	 Catch:{ all -> 0x0022 }
        r1.supplyPin2(r4, r5);	 Catch:{ all -> 0x0022 }
    L_0x000c:
        monitor-exit(r2);	 Catch:{ all -> 0x0022 }
    L_0x000d:
        return;
    L_0x000e:
        if (r5 == 0) goto L_0x000c;
    L_0x0010:
        r0 = new java.lang.RuntimeException;	 Catch:{ all -> 0x0022 }
        r1 = "ICC card is absent.";
        r0.<init>(r1);	 Catch:{ all -> 0x0022 }
        r1 = android.os.AsyncResult.forMessage(r5);	 Catch:{ all -> 0x0022 }
        r1.exception = r0;	 Catch:{ all -> 0x0022 }
        r5.sendToTarget();	 Catch:{ all -> 0x0022 }
        monitor-exit(r2);	 Catch:{ all -> 0x0022 }
        goto L_0x000d;
    L_0x0022:
        r1 = move-exception;
        monitor-exit(r2);	 Catch:{ all -> 0x0022 }
        throw r1;
        */
        throw new UnsupportedOperationException("Method not decompiled: com.android.internal.telephony.uicc.IccCardProxy.supplyPin2(java.lang.String, android.os.Message):void");
    }

    /* JADX WARNING: inconsistent code. */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    public void supplyPuk2(java.lang.String r4, java.lang.String r5, android.os.Message r6) {
        /*
        r3 = this;
        r2 = r3.mLock;
        monitor-enter(r2);
        r1 = r3.mUiccApplication;	 Catch:{ all -> 0x0022 }
        if (r1 == 0) goto L_0x000e;
    L_0x0007:
        r1 = r3.mUiccApplication;	 Catch:{ all -> 0x0022 }
        r1.supplyPuk2(r4, r5, r6);	 Catch:{ all -> 0x0022 }
    L_0x000c:
        monitor-exit(r2);	 Catch:{ all -> 0x0022 }
    L_0x000d:
        return;
    L_0x000e:
        if (r6 == 0) goto L_0x000c;
    L_0x0010:
        r0 = new java.lang.RuntimeException;	 Catch:{ all -> 0x0022 }
        r1 = "ICC card is absent.";
        r0.<init>(r1);	 Catch:{ all -> 0x0022 }
        r1 = android.os.AsyncResult.forMessage(r6);	 Catch:{ all -> 0x0022 }
        r1.exception = r0;	 Catch:{ all -> 0x0022 }
        r6.sendToTarget();	 Catch:{ all -> 0x0022 }
        monitor-exit(r2);	 Catch:{ all -> 0x0022 }
        goto L_0x000d;
    L_0x0022:
        r1 = move-exception;
        monitor-exit(r2);	 Catch:{ all -> 0x0022 }
        throw r1;
        */
        throw new UnsupportedOperationException("Method not decompiled: com.android.internal.telephony.uicc.IccCardProxy.supplyPuk2(java.lang.String, java.lang.String, android.os.Message):void");
    }

    /* JADX WARNING: inconsistent code. */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    public void supplyNetworkDepersonalization(java.lang.String r4, android.os.Message r5) {
        /*
        r3 = this;
        r2 = r3.mLock;
        monitor-enter(r2);
        r1 = r3.mUiccApplication;	 Catch:{ all -> 0x0022 }
        if (r1 == 0) goto L_0x000e;
    L_0x0007:
        r1 = r3.mUiccApplication;	 Catch:{ all -> 0x0022 }
        r1.supplyNetworkDepersonalization(r4, r5);	 Catch:{ all -> 0x0022 }
    L_0x000c:
        monitor-exit(r2);	 Catch:{ all -> 0x0022 }
    L_0x000d:
        return;
    L_0x000e:
        if (r5 == 0) goto L_0x000c;
    L_0x0010:
        r0 = new java.lang.RuntimeException;	 Catch:{ all -> 0x0022 }
        r1 = "CommandsInterface is not set.";
        r0.<init>(r1);	 Catch:{ all -> 0x0022 }
        r1 = android.os.AsyncResult.forMessage(r5);	 Catch:{ all -> 0x0022 }
        r1.exception = r0;	 Catch:{ all -> 0x0022 }
        r5.sendToTarget();	 Catch:{ all -> 0x0022 }
        monitor-exit(r2);	 Catch:{ all -> 0x0022 }
        goto L_0x000d;
    L_0x0022:
        r1 = move-exception;
        monitor-exit(r2);	 Catch:{ all -> 0x0022 }
        throw r1;
        */
        throw new UnsupportedOperationException("Method not decompiled: com.android.internal.telephony.uicc.IccCardProxy.supplyNetworkDepersonalization(java.lang.String, android.os.Message):void");
    }

    public boolean getIccLockEnabled() {
        boolean booleanValue;
        synchronized (this.mLock) {
            booleanValue = Boolean.valueOf(this.mUiccApplication != null ? this.mUiccApplication.getIccLockEnabled() : false).booleanValue();
        }
        return booleanValue;
    }

    public boolean getIccFdnEnabled() {
        boolean booleanValue;
        synchronized (this.mLock) {
            booleanValue = Boolean.valueOf(this.mUiccApplication != null ? this.mUiccApplication.getIccFdnEnabled() : false).booleanValue();
        }
        return booleanValue;
    }

    public boolean getIccFdnAvailable() {
        return this.mUiccApplication != null ? this.mUiccApplication.getIccFdnAvailable() : false;
    }

    public boolean getIccPin2Blocked() {
        return Boolean.valueOf(this.mUiccApplication != null ? this.mUiccApplication.getIccPin2Blocked() : false).booleanValue();
    }

    public boolean getIccPuk2Blocked() {
        return Boolean.valueOf(this.mUiccApplication != null ? this.mUiccApplication.getIccPuk2Blocked() : false).booleanValue();
    }

    /* JADX WARNING: inconsistent code. */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    public void setIccLockEnabled(boolean r4, java.lang.String r5, android.os.Message r6) {
        /*
        r3 = this;
        r2 = r3.mLock;
        monitor-enter(r2);
        r1 = r3.mUiccApplication;	 Catch:{ all -> 0x0022 }
        if (r1 == 0) goto L_0x000e;
    L_0x0007:
        r1 = r3.mUiccApplication;	 Catch:{ all -> 0x0022 }
        r1.setIccLockEnabled(r4, r5, r6);	 Catch:{ all -> 0x0022 }
    L_0x000c:
        monitor-exit(r2);	 Catch:{ all -> 0x0022 }
    L_0x000d:
        return;
    L_0x000e:
        if (r6 == 0) goto L_0x000c;
    L_0x0010:
        r0 = new java.lang.RuntimeException;	 Catch:{ all -> 0x0022 }
        r1 = "ICC card is absent.";
        r0.<init>(r1);	 Catch:{ all -> 0x0022 }
        r1 = android.os.AsyncResult.forMessage(r6);	 Catch:{ all -> 0x0022 }
        r1.exception = r0;	 Catch:{ all -> 0x0022 }
        r6.sendToTarget();	 Catch:{ all -> 0x0022 }
        monitor-exit(r2);	 Catch:{ all -> 0x0022 }
        goto L_0x000d;
    L_0x0022:
        r1 = move-exception;
        monitor-exit(r2);	 Catch:{ all -> 0x0022 }
        throw r1;
        */
        throw new UnsupportedOperationException("Method not decompiled: com.android.internal.telephony.uicc.IccCardProxy.setIccLockEnabled(boolean, java.lang.String, android.os.Message):void");
    }

    /* JADX WARNING: inconsistent code. */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    public void setIccFdnEnabled(boolean r4, java.lang.String r5, android.os.Message r6) {
        /*
        r3 = this;
        r2 = r3.mLock;
        monitor-enter(r2);
        r1 = r3.mUiccApplication;	 Catch:{ all -> 0x0022 }
        if (r1 == 0) goto L_0x000e;
    L_0x0007:
        r1 = r3.mUiccApplication;	 Catch:{ all -> 0x0022 }
        r1.setIccFdnEnabled(r4, r5, r6);	 Catch:{ all -> 0x0022 }
    L_0x000c:
        monitor-exit(r2);	 Catch:{ all -> 0x0022 }
    L_0x000d:
        return;
    L_0x000e:
        if (r6 == 0) goto L_0x000c;
    L_0x0010:
        r0 = new java.lang.RuntimeException;	 Catch:{ all -> 0x0022 }
        r1 = "ICC card is absent.";
        r0.<init>(r1);	 Catch:{ all -> 0x0022 }
        r1 = android.os.AsyncResult.forMessage(r6);	 Catch:{ all -> 0x0022 }
        r1.exception = r0;	 Catch:{ all -> 0x0022 }
        r6.sendToTarget();	 Catch:{ all -> 0x0022 }
        monitor-exit(r2);	 Catch:{ all -> 0x0022 }
        goto L_0x000d;
    L_0x0022:
        r1 = move-exception;
        monitor-exit(r2);	 Catch:{ all -> 0x0022 }
        throw r1;
        */
        throw new UnsupportedOperationException("Method not decompiled: com.android.internal.telephony.uicc.IccCardProxy.setIccFdnEnabled(boolean, java.lang.String, android.os.Message):void");
    }

    /* JADX WARNING: inconsistent code. */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    public void changeIccLockPassword(java.lang.String r4, java.lang.String r5, android.os.Message r6) {
        /*
        r3 = this;
        r2 = r3.mLock;
        monitor-enter(r2);
        r1 = r3.mUiccApplication;	 Catch:{ all -> 0x0022 }
        if (r1 == 0) goto L_0x000e;
    L_0x0007:
        r1 = r3.mUiccApplication;	 Catch:{ all -> 0x0022 }
        r1.changeIccLockPassword(r4, r5, r6);	 Catch:{ all -> 0x0022 }
    L_0x000c:
        monitor-exit(r2);	 Catch:{ all -> 0x0022 }
    L_0x000d:
        return;
    L_0x000e:
        if (r6 == 0) goto L_0x000c;
    L_0x0010:
        r0 = new java.lang.RuntimeException;	 Catch:{ all -> 0x0022 }
        r1 = "ICC card is absent.";
        r0.<init>(r1);	 Catch:{ all -> 0x0022 }
        r1 = android.os.AsyncResult.forMessage(r6);	 Catch:{ all -> 0x0022 }
        r1.exception = r0;	 Catch:{ all -> 0x0022 }
        r6.sendToTarget();	 Catch:{ all -> 0x0022 }
        monitor-exit(r2);	 Catch:{ all -> 0x0022 }
        goto L_0x000d;
    L_0x0022:
        r1 = move-exception;
        monitor-exit(r2);	 Catch:{ all -> 0x0022 }
        throw r1;
        */
        throw new UnsupportedOperationException("Method not decompiled: com.android.internal.telephony.uicc.IccCardProxy.changeIccLockPassword(java.lang.String, java.lang.String, android.os.Message):void");
    }

    /* JADX WARNING: inconsistent code. */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    public void changeIccFdnPassword(java.lang.String r4, java.lang.String r5, android.os.Message r6) {
        /*
        r3 = this;
        r2 = r3.mLock;
        monitor-enter(r2);
        r1 = r3.mUiccApplication;	 Catch:{ all -> 0x0022 }
        if (r1 == 0) goto L_0x000e;
    L_0x0007:
        r1 = r3.mUiccApplication;	 Catch:{ all -> 0x0022 }
        r1.changeIccFdnPassword(r4, r5, r6);	 Catch:{ all -> 0x0022 }
    L_0x000c:
        monitor-exit(r2);	 Catch:{ all -> 0x0022 }
    L_0x000d:
        return;
    L_0x000e:
        if (r6 == 0) goto L_0x000c;
    L_0x0010:
        r0 = new java.lang.RuntimeException;	 Catch:{ all -> 0x0022 }
        r1 = "ICC card is absent.";
        r0.<init>(r1);	 Catch:{ all -> 0x0022 }
        r1 = android.os.AsyncResult.forMessage(r6);	 Catch:{ all -> 0x0022 }
        r1.exception = r0;	 Catch:{ all -> 0x0022 }
        r6.sendToTarget();	 Catch:{ all -> 0x0022 }
        monitor-exit(r2);	 Catch:{ all -> 0x0022 }
        goto L_0x000d;
    L_0x0022:
        r1 = move-exception;
        monitor-exit(r2);	 Catch:{ all -> 0x0022 }
        throw r1;
        */
        throw new UnsupportedOperationException("Method not decompiled: com.android.internal.telephony.uicc.IccCardProxy.changeIccFdnPassword(java.lang.String, java.lang.String, android.os.Message):void");
    }

    public String getServiceProviderName() {
        String serviceProviderName;
        synchronized (this.mLock) {
            if (this.mIccRecords != null) {
                serviceProviderName = this.mIccRecords.getServiceProviderName();
            } else {
                serviceProviderName = null;
            }
        }
        return serviceProviderName;
    }

    public boolean isApplicationOnIcc(AppType type) {
        boolean booleanValue;
        synchronized (this.mLock) {
            booleanValue = Boolean.valueOf(this.mUiccCard != null ? this.mUiccCard.isApplicationOnIcc(type) : false).booleanValue();
        }
        return booleanValue;
    }

    public boolean hasIccCard() {
        boolean z;
        synchronized (this.mLock) {
            if (this.mUiccCard == null || this.mUiccCard.getCardState() == CardState.CARDSTATE_ABSENT) {
                z = false;
            } else {
                z = true;
            }
        }
        return z;
    }

    private void sendIntent() {
        if (!this.mIsPermDisabledBroadcasted) {
            this.mIsPermDisabledBroadcasted = true;
            logi("PUK permenant blocked");
            this.mContext.sendBroadcast(new Intent("android.intent.action.RIL_PERM_BLOCKED"));
            broadcastIccStateChangedIntent("ABSENT", "PERM_DISABLED");
        }
    }

    public void simLockInfoRefresh(Message onComplete) {
        if ("true".equals(getSystemProperty("ro.ril.sim_multi_apps_suppport", this.mCardIndex.intValue(), "true"))) {
            log("Do not use simLockInfoRefresh() which is deprecated");
            return;
        }
        logi("simLockInfoRefresh");
        this.mCi.getSIMLockInfo(1, 3, obtainMessage(104, onComplete));
    }

    private void updateSimLockInfo() {
        int pin1 = getIccPin1RetryCount();
        int pin2 = getIccPin2RetryCount();
        this.mResultSIMLOCKINFO.setLockInfoResult(pin1, getIccPuk1RetryCount(), pin2, getIccPuk2retryCount(), getIccPinBlocked() ? 2 : 1, getIccPin2Blocked() ? 4 : 3);
    }

    void getEccListFromSim() {
        if (this.mAlreadyReadEcc) {
            log("Ecclist already have been read" + this.mEmergencyNumber);
            return;
        }
        IccFileHandler iccFh = getIccFileHandler();
        if (iccFh == null) {
            loge("Failed to get IccFileHandler for making EccList");
            SystemProperties.set("ro.ril.ecclist" + this.mPhone.getPhoneId(), this.mNoSimDefaultEccNum);
            return;
        }
        String simType = getSystemProperty("ril.ICC_TYPE", this.mCardIndex.intValue(), "0");
        if ("1".equals(simType)) {
            iccFh.loadEFTransparent(IccConstants.EF_ECC, obtainMessage(200));
        } else if ("2".equals(simType)) {
            iccFh.loadEFLinearFixedAll(IccConstants.EF_ECC, obtainMessage(200));
        } else {
            SystemProperties.set("ro.ril.ecclist" + this.mPhone.getPhoneId(), this.mNoSimDefaultEccNum);
        }
    }

    public void getSimLockInfo(int num_lock_type, int lock_type) {
        if ("true".equals(getSystemProperty("ro.ril.sim_multi_apps_suppport", this.mCardIndex.intValue(), "true"))) {
            log("Do not use getSIMLockInfo() which is deprecated");
            return;
        }
        logi("IccCard: getSimLockInfo");
        this.mCi.getSIMLockInfo(num_lock_type, lock_type, obtainMessage(103));
    }

    public int getIccPin1RetryCount() {
        return this.mUiccApplication != null ? this.mUiccApplication.getIccPin1RetryCount() : -1;
    }

    public int getIccPin2RetryCount() {
        return this.mUiccApplication != null ? this.mUiccApplication.getIccPin2RetryCount() : -1;
    }

    public int getIccPuk1RetryCount() {
        return this.mUiccApplication != null ? this.mUiccApplication.getIccPuk1RetryCount() : -1;
    }

    public int getIccPuk2retryCount() {
        return this.mUiccApplication != null ? this.mUiccApplication.getIccPuk2RetryCount() : -1;
    }

    public boolean getIccPinBlocked() {
        return Boolean.valueOf(this.mUiccApplication != null ? this.mUiccApplication.getIccPinBlocked() : false).booleanValue();
    }

    private StringBuffer read2GEccList(AsyncResult ar) {
        StringBuffer eccString = new StringBuffer("");
        try {
            byte[] data = (byte[]) ar.result;
            int numOfEcc = data.length / 3;
            for (int i = 0; i < numOfEcc; i++) {
                String tempEccString = bcdToString(data, i * 3, 3);
                if (tempEccString.length() != 0) {
                    if (!eccString.toString().equals("")) {
                        eccString.append(',');
                    }
                    eccString.append(tempEccString);
                }
            }
        } catch (Exception ex) {
            loge("Process 2G ECC failed -" + ex);
        }
        log("read2GEccList: " + eccString);
        return eccString;
    }

    private StringBuffer read3GEccList(AsyncResult ar) {
        StringBuffer eccString = new StringBuffer("");
        try {
            ArrayList<byte[]> datas = (ArrayList) ar.result;
            int s = datas.size();
            for (int i = 0; i < s; i++) {
                byte[] record = (byte[]) datas.get(i);
                String tempEccString = bcdToString(record, 0, 3);
                if (tempEccString.length() != 0) {
                    if (!eccString.toString().equals("")) {
                        eccString.append(',');
                    }
                    eccString.append(tempEccString);
                    eccString.append('/');
                    eccString.append(Integer.toString(record[record.length - 1] & 255));
                }
            }
        } catch (Exception ex) {
            loge("Process 3G ECC failed -" + ex);
        }
        log("read3GEccList: " + eccString);
        return eccString;
    }

    private String bcdToString(byte[] data, int offset, int length) {
        StringBuilder ret = new StringBuilder(length * 2);
        int i = offset;
        while (i < offset + length) {
            int v = data[i] & 15;
            if (v <= 9) {
                ret.append((char) (v + 48));
                v = (data[i] >> 4) & 15;
                if (v > 9) {
                    break;
                }
                ret.append((char) (v + 48));
                i++;
            } else {
                break;
            }
        }
        return ret.toString();
    }

    private void getIccUsimPersoStatus() {
        loge("getIccUsimPersoStatus");
        this.mPhone.mCi.queryFacilityLock(CommandsInterface.CB_FACILITY_BA_PS, "", 7, obtainMessage(EVENT_QUERY_FACILITY_SIM_PERSO_DONE));
    }

    public boolean getIccUsimPersoEnabled() {
        loge("getIccUsimPersoEnabled : " + this.mPersoSimLock);
        return this.mPersoSimLock;
    }

    public int getIccPersoRetryCount() {
        return this.mUiccApplication != null ? this.mUiccApplication.getIccPersoRetryCount() : -1;
    }

    public void setIccSimPersoEnabled(boolean enabled, String password, Message onComplete) {
        loge("setIccSimPersoEnabled  password : " + password);
        logi("SEC_PRODUCT_FEATURE_RIL_USIM_PERSONALIZATION NOT defined");
        AsyncResult ar = new AsyncResult(null, null, new RemoteException("Not Supported"));
        AsyncResult.forMessage((Message) ar.userObj).exception = ar.exception;
        ((Message) ar.userObj).sendToTarget();
    }

    public void changeIccSimPersoPassword(String oldPassword, String newPassword, Message onComplete) {
        loge("changeIccSimPersoPassword  old : " + oldPassword + " // new : " + newPassword);
        logi("SEC_PRODUCT_FEATURE_RIL_USIM_PERSONALIZATION NOT defined");
        AsyncResult ar = new AsyncResult(null, null, new RemoteException("Not Supported"));
        AsyncResult.forMessage((Message) ar.userObj).exception = ar.exception;
        ((Message) ar.userObj).sendToTarget();
    }

    public void supplyPerso(String pin, Message onComplete) {
        loge("supplyIccPerso  pin : " + pin);
        logi("SEC_PRODUCT_FEATURE_RIL_USIM_PERSONALIZATION NOT defined");
        AsyncResult ar = new AsyncResult(null, null, new RemoteException("Not Supported"));
        AsyncResult.forMessage((Message) ar.userObj).exception = ar.exception;
        ((Message) ar.userObj).sendToTarget();
    }

    /* JADX WARNING: inconsistent code. */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    private void invokeSimPerso(int r8, java.lang.String r9, android.os.Message r10) {
        /*
        r7 = this;
        r0 = new java.io.ByteArrayOutputStream;
        r0.<init>();
        r1 = new java.io.DataOutputStream;
        r1.<init>(r0);
        r5 = r9.length();
        r4 = r5 + 6;
        r5 = new java.lang.StringBuilder;
        r5.<init>();
        r6 = "invokeSimPerso  Lock Mode : ";
        r5 = r5.append(r6);
        r5 = r5.append(r8);
        r6 = " // data :";
        r5 = r5.append(r6);
        r5 = r5.append(r9);
        r5 = r5.toString();
        r7.loge(r5);
        r5 = 4;
        r1.writeByte(r5);	 Catch:{ IOException -> 0x0059 }
        r5 = 1;
        r1.writeByte(r5);	 Catch:{ IOException -> 0x0059 }
        r1.writeShort(r4);	 Catch:{ IOException -> 0x0059 }
        r5 = 1;
        r1.writeByte(r5);	 Catch:{ IOException -> 0x0059 }
        r1.writeByte(r8);	 Catch:{ IOException -> 0x0059 }
        r1.writeBytes(r9);	 Catch:{ IOException -> 0x0059 }
        r1.close();	 Catch:{ Exception -> 0x0052 }
    L_0x0048:
        r5 = r7.mPhone;
        r6 = r0.toByteArray();
        r5.invokeOemRilRequestRaw(r6, r10);
    L_0x0051:
        return;
    L_0x0052:
        r3 = move-exception;
        r5 = "finally Exception";
        r7.loge(r5);
        goto L_0x0048;
    L_0x0059:
        r2 = move-exception;
        r5 = "IOException in invokeSimPerso!!!";
        r7.loge(r5);	 Catch:{ all -> 0x006a }
        r1.close();	 Catch:{ Exception -> 0x0063 }
        goto L_0x0051;
    L_0x0063:
        r3 = move-exception;
        r5 = "finally Exception";
        r7.loge(r5);
        goto L_0x0051;
    L_0x006a:
        r5 = move-exception;
        r1.close();	 Catch:{ Exception -> 0x006f }
    L_0x006e:
        throw r5;
    L_0x006f:
        r3 = move-exception;
        r6 = "finally Exception";
        r7.loge(r6);
        goto L_0x006e;
        */
        throw new UnsupportedOperationException("Method not decompiled: com.android.internal.telephony.uicc.IccCardProxy.invokeSimPerso(int, java.lang.String, android.os.Message):void");
    }

    private void onQueryFacilitySimPerso(AsyncResult ar) {
        if (ar.exception != null) {
            log("Error in querying facility lock:" + ar.exception);
            return;
        }
        int[] ints = (int[]) ar.result;
        if (ints.length != 0) {
            boolean z;
            if (ints[0] != 0) {
                z = true;
            } else {
                z = false;
            }
            this.mPersoSimLock = z;
            log("Query facility Usim Perso : " + this.mPersoSimLock);
            return;
        }
        log("[IccCard] Bogus facility Usim Perso response");
    }

    public SimLockInfoResult getSimLockInfoResult() {
        return this.mResultSIMLOCKINFO;
    }

    public void setRoaming(byte[] data, Message onComplete) {
        log("setRoaming");
        try {
            this.mUiccApplication.getIccFileHandler().updateEFTransparent(IccConstants.EF_ROAMING, data, onComplete);
        } catch (NullPointerException e) {
            loge("Fail to get iccFh");
        }
    }

    public void setEPSLOCI(byte[] newEpsloci) {
        this.mUiccApplication.getIccFileHandler().updateEFTransparent(IccConstants.EF_EPSLOCI, newEpsloci, null);
    }

    public void setPSLOCI(byte[] newPsloci) {
        this.mUiccApplication.getIccFileHandler().updateEFTransparent(IccConstants.EF_PSLOCI, newPsloci, null);
    }

    public void setLOCI(byte[] newLoci) {
        this.mUiccApplication.getIccFileHandler().updateEFTransparent(IccConstants.EF_LOCI, newLoci, null);
    }

    public void setEPSLOCI(byte[] newEpsloci, Message onComplete) {
        this.mUiccApplication.getIccFileHandler().updateEFTransparent(IccConstants.EF_EPSLOCI, newEpsloci, onComplete);
    }

    public void setPSLOCI(byte[] newPsloci, Message onComplete) {
        this.mUiccApplication.getIccFileHandler().updateEFTransparent(IccConstants.EF_PSLOCI, newPsloci, onComplete);
    }

    public void setLOCI(byte[] newLoci, Message onComplete) {
        this.mUiccApplication.getIccFileHandler().updateEFTransparent(IccConstants.EF_LOCI, newLoci, onComplete);
    }

    private void setSystemProperty(String property, int slotId, String value) {
        MultiSimManager.setTelephonyProperty(property, slotId, value);
    }

    private void sendPersoBlockedIntent(String prop) {
        logi("Perso blocked");
        if (!this.isAlreadyOvercounted) {
            logi("sending broadcast");
            Intent intent = new Intent("android.intent.action.RIL_PERSO_BLOCKED");
            intent.setComponent(new ComponentName("com.sec.app.RilErrorNotifier", "com.sec.app.RilErrorNotifier.PhoneErrorReceiver"));
            this.mContext.sendBroadcast(intent);
            if ("1".equals(prop)) {
                this.isAlreadyOvercounted = true;
            }
        }
    }

    private void log(String s) {
        if (this.mCardIndex != null) {
            Rlog.d(MultiSimManager.appendSimSlot(LOG_TAG, this.mCardIndex.intValue()), s);
        } else {
            Rlog.d(LOG_TAG, s);
        }
    }

    private void loge(String msg) {
        if (this.mCardIndex != null) {
            Rlog.e(MultiSimManager.appendSimSlot(LOG_TAG, this.mCardIndex.intValue()), msg);
        } else {
            Rlog.e(LOG_TAG, msg);
        }
    }

    private void logi(String s) {
        if (this.mCardIndex != null) {
            Rlog.i(MultiSimManager.appendSimSlot(LOG_TAG, this.mCardIndex.intValue()), s);
        } else {
            Rlog.i(LOG_TAG, s);
        }
    }

    private void logw(String msg) {
        if (this.mCardIndex != null) {
            Rlog.w(MultiSimManager.appendSimSlot(LOG_TAG, this.mCardIndex.intValue()), msg);
        } else {
            Rlog.w(LOG_TAG, msg);
        }
    }

    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        int i;
        pw.println("IccCardProxy: " + this);
        pw.println(" mContext=" + this.mContext);
        pw.println(" mCi=" + this.mCi);
        pw.println(" mAbsentRegistrants: size=" + this.mAbsentRegistrants.size());
        for (i = 0; i < this.mAbsentRegistrants.size(); i++) {
            pw.println("  mAbsentRegistrants[" + i + "]=" + ((Registrant) this.mAbsentRegistrants.get(i)).getHandler());
        }
        pw.println(" mPinLockedRegistrants: size=" + this.mPinLockedRegistrants.size());
        for (i = 0; i < this.mPinLockedRegistrants.size(); i++) {
            pw.println("  mPinLockedRegistrants[" + i + "]=" + ((Registrant) this.mPinLockedRegistrants.get(i)).getHandler());
        }
        pw.println(" mNetworkLockedRegistrants: size=" + this.mNetworkLockedRegistrants.size());
        for (i = 0; i < this.mNetworkLockedRegistrants.size(); i++) {
            pw.println("  mNetworkLockedRegistrants[" + i + "]=" + ((Registrant) this.mNetworkLockedRegistrants.get(i)).getHandler());
        }
        pw.println(" mCurrentAppType=" + this.mCurrentAppType);
        pw.println(" mUiccController=" + this.mUiccController);
        pw.println(" mUiccCard=" + this.mUiccCard);
        pw.println(" mUiccApplication=" + this.mUiccApplication);
        pw.println(" mIccRecords=" + this.mIccRecords);
        pw.println(" mCdmaSSM=" + this.mCdmaSSM);
        pw.println(" mRadioOn=" + this.mRadioOn);
        pw.println(" mQuietMode=" + this.mQuietMode);
        pw.println(" mInitialized=" + this.mInitialized);
        pw.println(" mExternalState=" + this.mExternalState);
        pw.flush();
    }

    public int flightmodecheck() {
        try {
            this.flightMode = System.getInt(this.mPhone.getContext().getContentResolver(), "airplane_mode_on");
            logi("flightmodecheck" + this.flightMode);
        } catch (SettingNotFoundException e) {
            e.printStackTrace();
        }
        return this.flightMode;
    }

    public synchronized void startSimManagement() {
        String telephonySimState;
        Intent i;
        if ("DGG".equals("DGG")) {
            telephonySimState = getSystemProperty("gsm.sim.state", 0, "ABSENT");
            String telephonySimStateSecondary = getSystemProperty("gsm.sim.state", 1, "ABSENT");
            logi("startSimManagement: TelephonyProperties:SIM1 State=" + telephonySimState);
            logi("startSimManagement: TelephonyProperties:SIM2 State=" + telephonySimStateSecondary);
            if (("READY".equals(telephonySimState) || "ABSENT".equals(telephonySimState)) && (("READY".equals(telephonySimStateSecondary) || "ABSENT".equals(telephonySimStateSecondary)) && flightmodecheck() != 1)) {
                if (SystemProperties.get("persist.sys.setupwizard").isEmpty()) {
                    sendMessageDelayed(obtainMessage(EVENT_SETUP_WIZARD_NOT_START), 3000);
                } else {
                    i = new Intent();
                    i.setAction(CUSTOM_INTENT);
                    logi("startSimManagement:sendBroadcast sim check details");
                    SystemProperties.set("gsm.sim.SimMgrDone", "1");
                    this.mPhone.getContext().sendBroadcast(i);
                    sIsStartSimManagement = true;
                }
            }
        } else if ("trigger_restart_min_framework".equals(SystemProperties.get("vold.decrypt"))) {
            logi("[IccCardProxy]startSimManagement() return due to Decrypt pwd checking & setupwizard not complete");
        } else if (MultiSimManager.getSimSlotCount() > 1 && TelephonyManager.getDefault().getSimState(this.mPhone.getPhoneId()) == 5) {
            if (MultiSimManager.getInsertedSimCount() > 1) {
                telephonySimState = getSystemProperty("gsm.sim.state", 0, "ABSENT");
                String telephonySimState2 = getSystemProperty("gsm.sim.state", 1, "ABSENT");
                logi("getSimState: TelephonyProperties:simSlot0 State=" + telephonySimState);
                logi("getSimState: TelephonyProperties:simSlot1 State=" + telephonySimState2);
                if ("READY".equals(telephonySimState) && "READY".equals(telephonySimState2) && flightmodecheck() != 1) {
                    if (SystemProperties.get("persist.sys.setupwizard").isEmpty()) {
                        sendMessageDelayed(obtainMessage(EVENT_SETUP_WIZARD_NOT_START), 3000);
                    } else {
                        i = new Intent();
                        i.setAction(CUSTOM_INTENT);
                        logi("startSimManagement:sendBroadcast sim check details");
                        this.mPhone.getContext().sendBroadcast(i);
                        sIsStartSimManagement = true;
                    }
                }
            } else if (SystemProperties.get("persist.sys.setupwizard").isEmpty()) {
                sendMessageDelayed(obtainMessage(EVENT_SETUP_WIZARD_NOT_START), 3000);
            } else {
                i = new Intent();
                i.setAction(CUSTOM_INTENT);
                logi("startSimManagement:sendBroadcast sim check details");
                this.mPhone.getContext().sendBroadcast(i);
                sIsStartSimManagement = true;
            }
        }
    }

    private void prepareStartSimManagement() {
        if ("DCG".equals("DGG") || "DCGG".equals("DGG") || "DCGS".equals("DGG") || "DCGGS".equals("DGG") || "DGG".equals("DGG") || "CG".equals("DGG")) {
            logi("skip prepareStartSimManagement");
            return;
        }
        logi("sIsStartSimManagement : " + sIsStartSimManagement);
        if (!sIsStartSimManagement) {
            String fs_prop = SystemProperties.get("ril.FS", "false");
            String imsi = MultiSimManager.getSubscriberId(this.mPhone.getPhoneId());
            if (imsi == null) {
                imsi = "0";
            }
            if ((imsi == null || !imsi.startsWith("99999")) && !"true".equals(fs_prop)) {
                logi("Calling startSimManagement");
                startSimManagement();
                return;
            }
            logi("With Factory SIM. SKIP startSimManagement");
        }
    }

    private String getSystemProperty(String property, int slotId, String defValue) {
        return TelephonyManager.getTelephonyProperty(property, SubscriptionController.getInstance().getSubId(slotId)[0], defValue);
    }

    public void reloadPLMNs() {
        IccFileHandler iccFh = this.mUiccApplication.getIccFileHandler();
        log("reloadPLMNs ");
        if (iccFh == null) {
            loge("Failed to get IccFileHandler");
            return;
        }
        iccFh.loadEFTransparent(IccConstants.EF_PLMNwAct, obtainMessage(19));
        iccFh.loadEFTransparent(IccConstants.EF_FPLMN, obtainMessage(20));
        iccFh.loadEFTransparent(IccConstants.EF_OPLMNwAct, obtainMessage(21));
    }

    public String getPLMNwAcT() {
        log("getPLMNwAcT: " + this.mPLMNwAct);
        return this.mPLMNwAct;
    }

    public void setPLMNwAcT(byte[] newPlmn) {
        this.mUiccApplication.getIccFileHandler().updateEFTransparent(IccConstants.EF_PLMNwAct, newPlmn, null);
        this.mPLMNwAct = IccUtils.bytesToHexString(newPlmn);
    }

    public String getFPLMN() {
        log("getFPLMN: " + this.mFPLMN);
        return this.mFPLMN;
    }

    public void setFPLMN(byte[] newPlmn) {
        this.mUiccApplication.getIccFileHandler().updateEFTransparent(IccConstants.EF_FPLMN, newPlmn, null);
        this.mFPLMN = IccUtils.bytesToHexString(newPlmn);
    }

    public void setFPLMN(byte[] newPlmn, Message onComplete) {
        this.mUiccApplication.getIccFileHandler().updateEFTransparent(IccConstants.EF_FPLMN, newPlmn, onComplete);
        this.mFPLMN = IccUtils.bytesToHexString(newPlmn);
    }

    public String getOPLMNwAct() {
        log("getOPLMNwAct: " + this.mOPLMNwAct);
        return this.mOPLMNwAct;
    }

    public void setOPLMNwAct(byte[] newPlmn) {
        this.mUiccApplication.getIccFileHandler().updateEFTransparent(IccConstants.EF_OPLMNwAct, newPlmn, null);
        this.mOPLMNwAct = IccUtils.bytesToHexString(newPlmn);
    }
}
