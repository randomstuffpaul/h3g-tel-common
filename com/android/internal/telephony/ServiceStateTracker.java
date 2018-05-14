package com.android.internal.telephony;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.ContentObserver;
import android.os.AsyncResult;
import android.os.FileUtils;
import android.os.Handler;
import android.os.Message;
import android.os.Registrant;
import android.os.RegistrantList;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.provider.Settings.System;
import android.provider.Telephony.BaseMmsColumns;
import android.telephony.CellInfo;
import android.telephony.ServiceState;
import android.telephony.SignalStrength;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;
import android.util.Pair;
import android.util.TimeUtils;
import com.android.internal.telephony.dataconnection.DcTrackerBase;
import com.android.internal.telephony.uicc.IccCardApplicationStatus.AppState;
import com.android.internal.telephony.uicc.IccRecords;
import com.android.internal.telephony.uicc.UiccCardApplication;
import com.android.internal.telephony.uicc.UiccController;
import com.samsung.android.telephony.MultiSimManager;
import com.sec.android.app.CscFeature;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;

public abstract class ServiceStateTracker extends Handler {
    protected static final String ACTION_RADIO_OFF = "android.intent.action.ACTION_RADIO_OFF";
    protected static final boolean DBG = true;
    public static final int DEFAULT_GPRS_CHECK_PERIOD_MILLIS = 60000;
    public static final int ECM_EXIT_TIMER_WHEN_NOSVC = 14500;
    public static final int EVENT_CALL_HANGUP_BEFORE_DEACTIVEPDP = 100;
    protected static final int EVENT_CDMA_PRL_VERSION_CHANGED = 40;
    protected static final int EVENT_CDMA_SUBSCRIPTION_SOURCE_CHANGED = 39;
    protected static final int EVENT_CHANGE_IMS_STATE = 45;
    protected static final int EVENT_CHANGE_NETWORK_MODE = 117;
    protected static final int EVENT_CHECK_MULTI_TIME_ZONE = 105;
    protected static final int EVENT_CHECK_NETWORK_MODE = 118;
    protected static final int EVENT_CHECK_REPORT_GPRS = 22;
    protected static final int EVENT_DUALMODE_RECOVER_NETWORK = 119;
    protected static final int EVENT_ERI_FILE_LOADED = 36;
    protected static final int EVENT_EXIT_ECB_MODE_WHEN_NOSVC = 106;
    protected static final int EVENT_FIRST_CDMA_NET_SRCH_TIMER = 74;
    protected static final int EVENT_GET_BASEBAND_VERSION_DONE = 60;
    protected static final int EVENT_GET_CELL_INFO_LIST = 43;
    protected static final int EVENT_GET_LOC_DONE = 15;
    protected static final int EVENT_GET_LOC_DONE_CDMA = 31;
    protected static final int EVENT_GET_PREFERRED_NETWORK_TYPE = 19;
    protected static final int EVENT_GET_PREF_NETWORK_TYPE_DONE = 107;
    protected static final int EVENT_GET_SIGNAL_STRENGTH = 3;
    protected static final int EVENT_GET_SIGNAL_STRENGTH_CDMA = 29;
    protected static final int EVENT_HOME_NETWORK_NOTI = 109;
    public static final int EVENT_ICC_CHANGED = 42;
    protected static final int EVENT_IMS_RETRYOVER = 120;
    protected static final int EVENT_LOCATION_UPDATES_ENABLED = 18;
    protected static final int EVENT_LU_REJECT_CAUSE = 103;
    protected static final int EVENT_NETWORK_STATE_CHANGED = 2;
    protected static final int EVENT_NETWORK_STATE_CHANGED_CDMA = 30;
    protected static final int EVENT_NITZ_TIME = 11;
    protected static final int EVENT_NV_LOADED = 33;
    protected static final int EVENT_NV_READY = 35;
    protected static final int EVENT_OTA_PROVISION_STATUS_CHANGE = 37;
    protected static final int EVENT_PEND_SLEEP_WHILE_NET_SRCH_CDMA = 70;
    protected static final int EVENT_PEND_SLEEP_WHILE_NET_SRCH_GSM = 71;
    protected static final int EVENT_POLL_SIGNAL_STRENGTH = 10;
    protected static final int EVENT_POLL_SIGNAL_STRENGTH_CDMA = 28;
    protected static final int EVENT_POLL_STATE_CDMA_SUBSCRIPTION = 34;
    protected static final int EVENT_POLL_STATE_GPRS = 5;
    protected static final int EVENT_POLL_STATE_NETWORK_SELECTION_MODE = 14;
    protected static final int EVENT_POLL_STATE_OPERATOR = 6;
    protected static final int EVENT_POLL_STATE_OPERATOR_CDMA = 25;
    protected static final int EVENT_POLL_STATE_REGISTRATION = 4;
    protected static final int EVENT_POLL_STATE_REGISTRATION_CDMA = 24;
    protected static final int EVENT_RADIO_AVAILABLE = 13;
    protected static final int EVENT_RADIO_ON = 41;
    protected static final int EVENT_RADIO_STATE_CHANGED = 1;
    protected static final int EVENT_REQUEST_DISCONNECT_DC = 50;
    protected static final int EVENT_RESET_PREFERRED_NETWORK_TYPE = 21;
    protected static final int EVENT_RESTRICTED_STATE_CHANGED = 23;
    protected static final int EVENT_RETRY_GET_PREF_NETWORK_TYPE = 108;
    protected static final int EVENT_RIL_CONNECTED = 62;
    protected static final int EVENT_RUIM_READY = 26;
    protected static final int EVENT_RUIM_RECORDS_LOADED = 27;
    public static final int EVENT_SET_LTE_BAND_MODE = 46;
    protected static final int EVENT_SET_PREFERRED_NETWORK_TYPE = 20;
    protected static final int EVENT_SET_RADIO_POWER_OFF = 38;
    protected static final int EVENT_SIGNAL_STRENGTH_UPDATE = 12;
    protected static final int EVENT_SIM_READY = 17;
    protected static final int EVENT_SIM_RECORDS_LOADED = 16;
    protected static final int EVENT_TDMODEM_NO_SERVICE = 115;
    protected static final int EVENT_UNSOL_CELL_INFO_LIST = 44;
    protected static final int EVENT_VOICE_CALL_ENDED = 102;
    protected static final int GLOBAL_NETWORK_SEARCH_TIMER = 45;
    protected static final int GLOBAL_NOSVC_CHK_TIMER = 5;
    protected static final int GLOBAL_PENDING_INTENT_WAITING_TIME = 10;
    protected static final String[] GMT_COUNTRY_CODES = new String[]{"bf", "ci", "eh", "fo", "gb", "gh", "gm", "gn", "gw", "ie", "lr", "is", "ma", "ml", "mr", "pt", "sl", "sn", BaseMmsColumns.STATUS, "tg"};
    public static boolean IsDispdSwitchToGsm = false;
    public static boolean IsGlobalModeAvail = false;
    public static boolean IsManSelMode = false;
    private static final long LAST_CELL_INFO_LIST_MAX_AGE_MS = 2000;
    static final String LOG_TAG = "SST";
    protected static final int NET_SRCH_NUM_FOR_GOING_TO_PWR_SAVE_MODE = 12;
    public static final int OTASP_COMPLETED = 4;
    public static final int OTASP_NEEDED = 2;
    public static final int OTASP_NOT_NEEDED = 3;
    public static final int OTASP_UNINITIALIZED = 0;
    public static final int OTASP_UNKNOWN = 1;
    protected static final int POLL_PERIOD_MILLIS = 20000;
    protected static final String PROPERTY_2ND_NETSEL_CNF_WAITING = "ril.m2ndNetSelCnfWaiting";
    protected static final String PROPERTY_CDMA_SHORT_SRCHED = "ril.cdmaShortSrched";
    protected static final String PROPERTY_CHINA_NETSEL_CNF_WAITING = "ril.mChinaNetSelCnfWaiting";
    protected static final String PROPERTY_FAKE_DISP_CANCELED = "ril.fakeDispCanceled";
    protected static final String PROPERTY_HAS_EVER_SWITCHED_TO_GSM = "ril.mHasEverSwitchedToGsm";
    protected static final String PROPERTY_IS_SWITCHED_TO_CDMA = "ril.mIsSwitchedToCdma";
    protected static final String PROPERTY_SERVICE_STATE = "ril.servicestate";
    protected static final String PROP_FORCE_ROAMING = "telephony.test.forceRoaming";
    protected static final int PWR_SAVE_MODE_STAY_TIMER = 1200;
    protected static final String REGISTRATION_DENIED_AUTH = "Authentication Failure";
    protected static final String REGISTRATION_DENIED_GEN = "General";
    protected static final int SIMCARDMNG_LAUNCH_TIPER = 25;
    protected static final int SLOT2_GSM_SERVICE_CHECK_TIMER = 70;
    protected static final int SRCH_NET_CDMA = 1;
    protected static final int SRCH_NET_GLOBAL = 3;
    protected static final int SRCH_NET_GSM = 2;
    protected static final int SRCH_NET_NO = 0;
    public static final int THRESHOLD_FOR_DATA_RESUME = 3;
    public static final int THRESHOLD_FOR_DATA_SUSPENDED = 1;
    protected static final String TIMEZONE_PROPERTY = "persist.sys.timezone";
    protected static final boolean VDBG = false;
    public static boolean alreadyExpired = false;
    public static int currGsmMccInt = 0;
    public static int currGsmMccInt2 = 0;
    protected static boolean mCdmaInSvc = false;
    public static int mCdmaSrchCnt = 0;
    protected static int mCurrentSrchNet;
    protected static boolean mFirstCdmaNoSvcChkTimerStarted = false;
    protected static boolean mGsmInSvc = false;
    public static int mGsmSrchCnt = 0;
    protected static boolean mHasTimeDispPopupDispd = false;
    protected static boolean mNetSrchTimerRunning = false;
    protected static boolean mNoSvcChkTimerRunning = false;
    protected static boolean mPendingIntentTimerRunning = false;
    public static int mPrevSrchNet = 0;
    protected static boolean mPsmStayTimerProcessed = false;
    protected static boolean mPsmStayTimerRunning = false;
    public static boolean mReduceSearchTimeShouldProceed = true;
    protected static boolean mRuimRecordsLoadingFinished = false;
    protected static boolean mScreenOn = false;
    static int mSignalBar = -1;
    protected static boolean mSimCardMngEverLaunched = false;
    protected static boolean mSimCardMngLnchTimerRunning = false;
    public static boolean mSlot1ShouldSwitchImmediately = false;
    public static int prevCdmaMcc = 0;
    public static int prevGsmMccInt = 0;
    protected boolean mAlarmSwitch = false;
    protected RegistrantList mAttachedRegistrants = new RegistrantList();
    protected final CellInfo mCellInfo;
    protected CommandsInterface mCi;
    protected RegistrantList mDataRegStateOrRatChangedRegistrants = new RegistrantList();
    protected boolean mDesiredPowerState;
    protected RegistrantList mDetachedRegistrants = new RegistrantList();
    protected boolean mDeviceShuttingDown = false;
    protected boolean mDontPollSignalStrength = false;
    protected IccRecords mIccRecords = null;
    protected boolean mImsRegistrationOnOff = false;
    protected IntentFilter mIntentFilter = null;
    protected List<CellInfo> mLastCellInfoList = null;
    protected long mLastCellInfoListTime;
    private SignalStrength mLastSignalStrength = null;
    protected RegistrantList mNetworkAttachedRegistrants = new RegistrantList();
    protected ServiceState mNewSS = new ServiceState();
    private boolean mPendingRadioPowerOff = false;
    protected boolean mPendingRadioPowerOffAfterDataOff = false;
    protected int mPendingRadioPowerOffAfterDataOffTag = 0;
    protected PhoneBase mPhoneBase;
    protected RegistrantList mPlmnChangeRegistrants = new RegistrantList();
    protected int[] mPollingContext;
    protected boolean mPowerOffDelayNeed = true;
    protected RegistrantList mPsRestrictDisabledRegistrants = new RegistrantList();
    protected RegistrantList mPsRestrictEnabledRegistrants = new RegistrantList();
    protected PendingIntent mRadioOffIntent = null;
    public RestrictedState mRestrictedState = new RestrictedState();
    protected RegistrantList mRoamingOffRegistrants = new RegistrantList();
    protected RegistrantList mRoamingOnRegistrants = new RegistrantList();
    protected RegistrantList mRoutingAreaChangedRegistrants = new RegistrantList();
    public ServiceState mSS = new ServiceState();
    protected SignalStrength mSignalStrength = new SignalStrength();
    private ContentObserver mSlotActiveObserver = new ContentObserver(new Handler()) {
        public void onChange(boolean selfChange) {
            ServiceStateTracker.this.loge("phone" + (ServiceStateTracker.this.mPhoneBase.mPhoneId + 1) + "_on change, updataeSpnDisplay");
            ServiceStateTracker.this.updateSpnDisplay();
        }
    };
    protected UiccCardApplication mUiccApplcation = null;
    protected UiccController mUiccController = null;
    protected boolean mVoiceCapable;
    private boolean mWantContinuousLocationUpdates;
    private boolean mWantSingleLocationUpdate;
    public int newDataRadioTech = 0;
    public int oldDataRadioTech = 0;
    PendingIntent psmChkTimer;
    PendingIntent sender_SimCardMngLaunchTimer;

    private class CellInfoResult {
        List<CellInfo> list;
        Object lockObj;

        private CellInfoResult() {
            this.lockObj = new Object();
        }
    }

    public abstract int getCurrentDataConnectionState();

    protected abstract Phone getPhone();

    protected abstract void handlePollStateResult(int i, AsyncResult asyncResult);

    protected abstract void hangupAndPowerOff();

    protected abstract boolean hangupBeforeDeactivePDP();

    public abstract boolean isConcurrentVoiceAndDataAllowed();

    protected abstract void log(String str);

    protected abstract void loge(String str);

    protected abstract void onUpdateIccAvailability();

    public abstract void pollState();

    public abstract void setImsRegistrationState(boolean z);

    protected abstract void setPowerStateToDesired();

    protected abstract void updateSpnDisplay();

    protected ServiceStateTracker(PhoneBase phoneBase, CommandsInterface ci, CellInfo cellInfo) {
        this.mPhoneBase = phoneBase;
        this.mCellInfo = cellInfo;
        this.mCi = ci;
        this.mVoiceCapable = this.mPhoneBase.getContext().getResources().getBoolean(17956933);
        this.mUiccController = UiccController.getInstance();
        this.mUiccController.registerForIccChanged(this, 42, null);
        this.mCi.setOnSignalStrengthUpdate(this, 12, null);
        this.mCi.registerForCellInfoList(this, 44, null);
        this.mPhoneBase.setSystemProperty("gsm.network.type", ServiceState.rilRadioTechnologyToString(0));
        if (!"CTC".equals(SystemProperties.get("ro.csc.sales_code"))) {
            return;
        }
        if (this.mPhoneBase.mPhoneId == 0) {
            this.mPhoneBase.getContext().getContentResolver().registerContentObserver(System.getUriFor("phone1_on"), false, this.mSlotActiveObserver);
        } else if (this.mPhoneBase.mPhoneId == 1) {
            this.mPhoneBase.getContext().getContentResolver().registerContentObserver(System.getUriFor("phone2_on"), false, this.mSlotActiveObserver);
        }
    }

    void requestShutdown() {
        if (!this.mDeviceShuttingDown) {
            this.mDeviceShuttingDown = true;
            this.mDesiredPowerState = false;
            setPowerStateToDesired();
        }
    }

    public void dispose() {
        this.mCi.unSetOnSignalStrengthUpdate(this);
        this.mUiccController.unregisterForIccChanged(this);
        this.mCi.unregisterForCellInfoList(this);
        if ("CTC".equals(SystemProperties.get("ro.csc.sales_code"))) {
            this.mPhoneBase.getContext().getContentResolver().unregisterContentObserver(this.mSlotActiveObserver);
        }
    }

    public boolean getDesiredPowerState() {
        return this.mDesiredPowerState;
    }

    protected boolean notifySignalStrength() {
        boolean notified = false;
        synchronized (this.mCellInfo) {
            if (!this.mSignalStrength.equals(this.mLastSignalStrength)) {
                try {
                    this.mPhoneBase.notifySignalStrength();
                    notified = true;
                } catch (NullPointerException ex) {
                    loge("updateSignalStrength() Phone already destroyed: " + ex + "SignalStrength not notified");
                }
            }
        }
        return notified;
    }

    protected void notifyDataRegStateRilRadioTechnologyChanged() {
        int rat = this.mSS.getRilDataRadioTechnology();
        int drs = this.mSS.getDataRegState();
        log("notifyDataRegStateRilRadioTechnologyChanged: drs=" + drs + " rat=" + rat);
        this.mPhoneBase.setSystemProperty("gsm.network.type", ServiceState.rilRadioTechnologyToString(rat));
        this.mDataRegStateOrRatChangedRegistrants.notifyResult(new Pair(Integer.valueOf(drs), Integer.valueOf(rat)));
    }

    protected void useDataRegStateForDataOnlyDevices() {
        if (!"DCM".equals("") && !"KDI".equals("") && !"SBM".equals("") && !this.mVoiceCapable) {
            log("useDataRegStateForDataOnlyDevice: VoiceRegState=" + this.mNewSS.getVoiceRegState() + " DataRegState=" + this.mNewSS.getDataRegState());
            this.mNewSS.setVoiceRegState(this.mNewSS.getDataRegState());
        }
    }

    protected void updatePhoneObject() {
        if (this.mPhoneBase.getContext().getResources().getBoolean(17956990)) {
            this.mPhoneBase.updatePhoneObject(this.mSS.getRilVoiceRadioTechnology());
        }
    }

    public void registerForRoamingOn(Handler h, int what, Object obj) {
        Registrant r = new Registrant(h, what, obj);
        this.mRoamingOnRegistrants.add(r);
        if (this.mSS.getRoaming()) {
            r.notifyRegistrant();
        }
    }

    public void unregisterForRoamingOn(Handler h) {
        this.mRoamingOnRegistrants.remove(h);
    }

    public void registerForRoamingOff(Handler h, int what, Object obj) {
        Registrant r = new Registrant(h, what, obj);
        this.mRoamingOffRegistrants.add(r);
        if (!this.mSS.getRoaming()) {
            r.notifyRegistrant();
        }
    }

    public void unregisterForRoamingOff(Handler h) {
        this.mRoamingOffRegistrants.remove(h);
    }

    public void reRegisterNetwork(Message onComplete) {
        this.mCi.getPreferredNetworkType(obtainMessage(19, onComplete));
    }

    public void setRadioPower(boolean power) {
        this.mDesiredPowerState = power;
        setPowerStateToDesired();
    }

    public void enableSingleLocationUpdate() {
        if (!this.mWantSingleLocationUpdate && !this.mWantContinuousLocationUpdates) {
            this.mWantSingleLocationUpdate = true;
            this.mCi.setLocationUpdates(true, obtainMessage(18));
        }
    }

    public void enableLocationUpdates() {
        if (!this.mWantSingleLocationUpdate && !this.mWantContinuousLocationUpdates) {
            this.mWantContinuousLocationUpdates = true;
            this.mCi.setLocationUpdates(true, obtainMessage(18));
        }
    }

    protected void disableSingleLocationUpdate() {
        this.mWantSingleLocationUpdate = false;
        if (!this.mWantSingleLocationUpdate && !this.mWantContinuousLocationUpdates) {
            this.mCi.setLocationUpdates(false, null);
        }
    }

    public void disableLocationUpdates() {
        this.mWantContinuousLocationUpdates = false;
        if (!this.mWantSingleLocationUpdate && !this.mWantContinuousLocationUpdates) {
            this.mCi.setLocationUpdates(false, null);
        }
    }

    public void handleMessage(Message msg) {
        AsyncResult ar;
        switch (msg.what) {
            case 38:
                synchronized (this) {
                    if (this.mPendingRadioPowerOffAfterDataOff && msg.arg1 == this.mPendingRadioPowerOffAfterDataOffTag) {
                        log("EVENT_SET_RADIO_OFF, turn radio off now.");
                        hangupAndPowerOff();
                        this.mPendingRadioPowerOffAfterDataOffTag++;
                        this.mPendingRadioPowerOffAfterDataOff = false;
                    } else {
                        log("EVENT_SET_RADIO_OFF is stale arg1=" + msg.arg1 + "!= tag=" + this.mPendingRadioPowerOffAfterDataOffTag);
                    }
                }
                return;
            case 42:
                onUpdateIccAvailability();
                return;
            case 43:
                ar = msg.obj;
                CellInfoResult result = ar.userObj;
                synchronized (result.lockObj) {
                    if (ar.exception != null) {
                        log("EVENT_GET_CELL_INFO_LIST: error ret null, e=" + ar.exception);
                        result.list = null;
                    } else {
                        result.list = (List) ar.result;
                    }
                    this.mLastCellInfoListTime = SystemClock.elapsedRealtime();
                    this.mLastCellInfoList = result.list;
                    result.lockObj.notify();
                }
                return;
            case 44:
                ar = (AsyncResult) msg.obj;
                if (ar.exception != null) {
                    log("EVENT_UNSOL_CELL_INFO_LIST: error ignoring, e=" + ar.exception);
                    return;
                }
                List<CellInfo> list = ar.result;
                log("EVENT_UNSOL_CELL_INFO_LIST: size=" + list.size() + " list=" + list);
                this.mLastCellInfoListTime = SystemClock.elapsedRealtime();
                this.mLastCellInfoList = list;
                this.mPhoneBase.notifyCellInfo(list);
                return;
            case 100:
                log("EVENT_CALL_HANGUP_BEFORE_DEACTIVEPDP");
                if (!this.mPendingRadioPowerOffAfterDataOff) {
                    log("pending radio power off after hangup voice call");
                    powerOffRadioSafely(this.mPhoneBase.mDcTracker);
                    return;
                }
                return;
            default:
                log("Unhandled message with number: " + msg.what);
                return;
        }
    }

    public void registerForDataConnectionAttached(Handler h, int what, Object obj) {
        Registrant r = new Registrant(h, what, obj);
        this.mAttachedRegistrants.add(r);
        if (getCurrentDataConnectionState() == 0) {
            r.notifyRegistrant();
        }
    }

    public void unregisterForDataConnectionAttached(Handler h) {
        this.mAttachedRegistrants.remove(h);
    }

    public void registerForDataConnectionDetached(Handler h, int what, Object obj) {
        Registrant r = new Registrant(h, what, obj);
        this.mDetachedRegistrants.add(r);
        if (getCurrentDataConnectionState() != 0) {
            r.notifyRegistrant();
        }
    }

    public void unregisterForDataConnectionDetached(Handler h) {
        this.mDetachedRegistrants.remove(h);
    }

    public void registerForDataRegStateOrRatChanged(Handler h, int what, Object obj) {
        this.mDataRegStateOrRatChangedRegistrants.add(new Registrant(h, what, obj));
        notifyDataRegStateRilRadioTechnologyChanged();
    }

    public void unregisterForDataRegStateOrRatChanged(Handler h) {
        this.mDataRegStateOrRatChangedRegistrants.remove(h);
    }

    public void registerForNetworkAttached(Handler h, int what, Object obj) {
        Registrant r = new Registrant(h, what, obj);
        this.mNetworkAttachedRegistrants.add(r);
        if (this.mSS.getVoiceRegState() == 0) {
            r.notifyRegistrant();
        }
    }

    public void unregisterForNetworkAttached(Handler h) {
        this.mNetworkAttachedRegistrants.remove(h);
    }

    public void registerForPsRestrictedEnabled(Handler h, int what, Object obj) {
        Registrant r = new Registrant(h, what, obj);
        this.mPsRestrictEnabledRegistrants.add(r);
        if (this.mRestrictedState.isPsRestricted()) {
            r.notifyRegistrant();
        }
    }

    public void unregisterForPsRestrictedEnabled(Handler h) {
        this.mPsRestrictEnabledRegistrants.remove(h);
    }

    public void registerForPsRestrictedDisabled(Handler h, int what, Object obj) {
        Registrant r = new Registrant(h, what, obj);
        this.mPsRestrictDisabledRegistrants.add(r);
        if (this.mRestrictedState.isPsRestricted()) {
            r.notifyRegistrant();
        }
    }

    public void unregisterForPsRestrictedDisabled(Handler h) {
        this.mPsRestrictDisabledRegistrants.remove(h);
    }

    public void registerForRoutingAreaChanged(Handler h, int what, Object obj) {
        Registrant r = new Registrant(h, what, obj);
        this.mRoutingAreaChangedRegistrants.add(r);
        if (getCurrentDataConnectionState() == 0) {
            r.notifyRegistrant();
        }
    }

    public void unregisterForRoutingAreaChanged(Handler h) {
        this.mRoutingAreaChangedRegistrants.remove(h);
    }

    /* JADX WARNING: inconsistent code. */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    public void powerOffRadioSafely(com.android.internal.telephony.dataconnection.DcTrackerBase r9) {
        /*
        r8 = this;
        monitor-enter(r8);
        r5 = r8.mPendingRadioPowerOffAfterDataOff;	 Catch:{ all -> 0x0052 }
        if (r5 != 0) goto L_0x00d0;
    L_0x0005:
        r5 = r8.mPhoneBase;	 Catch:{ all -> 0x0052 }
        r5 = r5.getContext();	 Catch:{ all -> 0x0052 }
        r5 = r5.getResources();	 Catch:{ all -> 0x0052 }
        r6 = 17236028; // 0x107003c float:2.4795752E-38 double:8.5157293E-317;
        r3 = r5.getStringArray(r6);	 Catch:{ all -> 0x0052 }
        r5 = r8.mSS;	 Catch:{ all -> 0x0052 }
        r0 = r5.getOperatorNumeric();	 Catch:{ all -> 0x0052 }
        if (r3 == 0) goto L_0x004a;
    L_0x001e:
        if (r0 == 0) goto L_0x004a;
    L_0x0020:
        r1 = 0;
    L_0x0021:
        r5 = r3.length;	 Catch:{ all -> 0x0052 }
        if (r1 >= r5) goto L_0x004a;
    L_0x0024:
        r5 = r3[r1];	 Catch:{ all -> 0x0052 }
        r5 = r0.equals(r5);	 Catch:{ all -> 0x0052 }
        if (r5 == 0) goto L_0x0047;
    L_0x002c:
        r5 = new java.lang.StringBuilder;	 Catch:{ all -> 0x0052 }
        r5.<init>();	 Catch:{ all -> 0x0052 }
        r6 = "Not disconnecting data for ";
        r5 = r5.append(r6);	 Catch:{ all -> 0x0052 }
        r5 = r5.append(r0);	 Catch:{ all -> 0x0052 }
        r5 = r5.toString();	 Catch:{ all -> 0x0052 }
        r8.log(r5);	 Catch:{ all -> 0x0052 }
        r8.hangupAndPowerOff();	 Catch:{ all -> 0x0052 }
        monitor-exit(r8);	 Catch:{ all -> 0x0052 }
    L_0x0046:
        return;
    L_0x0047:
        r1 = r1 + 1;
        goto L_0x0021;
    L_0x004a:
        r5 = r8.hangupBeforeDeactivePDP();	 Catch:{ all -> 0x0052 }
        if (r5 == 0) goto L_0x0055;
    L_0x0050:
        monitor-exit(r8);	 Catch:{ all -> 0x0052 }
        goto L_0x0046;
    L_0x0052:
        r5 = move-exception;
        monitor-exit(r8);	 Catch:{ all -> 0x0052 }
        throw r5;
    L_0x0055:
        r5 = "ro.csc.sales_code";
        r4 = android.os.SystemProperties.get(r5);	 Catch:{ all -> 0x0052 }
        r5 = "CHC";
        r5 = r5.equals(r4);	 Catch:{ all -> 0x0052 }
        if (r5 != 0) goto L_0x00b3;
    L_0x0063:
        r5 = "CHU";
        r5 = r5.equals(r4);	 Catch:{ all -> 0x0052 }
        if (r5 != 0) goto L_0x00b3;
    L_0x006b:
        r5 = "CHM";
        r5 = r5.equals(r4);	 Catch:{ all -> 0x0052 }
        if (r5 != 0) goto L_0x00b3;
    L_0x0073:
        r5 = "CTC";
        r5 = r5.equals(r4);	 Catch:{ all -> 0x0052 }
        if (r5 != 0) goto L_0x00b3;
    L_0x007b:
        r5 = "BRI";
        r5 = r5.equals(r4);	 Catch:{ all -> 0x0052 }
        if (r5 != 0) goto L_0x00b3;
    L_0x0083:
        r5 = "TGY";
        r5 = r5.equals(r4);	 Catch:{ all -> 0x0052 }
        if (r5 != 0) goto L_0x00b3;
    L_0x008b:
        r5 = "CWT";
        r5 = r5.equals(r4);	 Catch:{ all -> 0x0052 }
        if (r5 != 0) goto L_0x00b3;
    L_0x0093:
        r5 = "FET";
        r5 = r5.equals(r4);	 Catch:{ all -> 0x0052 }
        if (r5 != 0) goto L_0x00b3;
    L_0x009b:
        r5 = "TWM";
        r5 = r5.equals(r4);	 Catch:{ all -> 0x0052 }
        if (r5 != 0) goto L_0x00b3;
    L_0x00a3:
        r5 = "CHZ";
        r5 = r5.equals(r4);	 Catch:{ all -> 0x0052 }
        if (r5 != 0) goto L_0x00b3;
    L_0x00ab:
        r5 = "CHN";
        r5 = r5.equals(r4);	 Catch:{ all -> 0x0052 }
        if (r5 == 0) goto L_0x00bd;
    L_0x00b3:
        r5 = "Skip Data disconnect in CHINA model, turn off radio right away.";
        r8.log(r5);	 Catch:{ all -> 0x0052 }
        r8.hangupAndPowerOff();	 Catch:{ all -> 0x0052 }
        monitor-exit(r8);	 Catch:{ all -> 0x0052 }
        goto L_0x0046;
    L_0x00bd:
        r5 = r9.isDisconnected();	 Catch:{ all -> 0x0052 }
        if (r5 == 0) goto L_0x00d3;
    L_0x00c3:
        r5 = "radioTurnedOff";
        r9.cleanUpAllConnections(r5);	 Catch:{ all -> 0x0052 }
        r5 = "Data disconnected, turn off radio right away.";
        r8.log(r5);	 Catch:{ all -> 0x0052 }
        r8.hangupAndPowerOff();	 Catch:{ all -> 0x0052 }
    L_0x00d0:
        monitor-exit(r8);	 Catch:{ all -> 0x0052 }
        goto L_0x0046;
    L_0x00d3:
        r5 = r9.isConnecting();	 Catch:{ all -> 0x0052 }
        if (r5 == 0) goto L_0x0108;
    L_0x00d9:
        r5 = "radioTurnedOff";
        r9.cleanUpAllConnections(r5);	 Catch:{ all -> 0x0052 }
        r2 = android.os.Message.obtain(r8);	 Catch:{ all -> 0x0052 }
        r5 = 38;
        r2.what = r5;	 Catch:{ all -> 0x0052 }
        r5 = r8.mPendingRadioPowerOffAfterDataOffTag;	 Catch:{ all -> 0x0052 }
        r5 = r5 + 1;
        r8.mPendingRadioPowerOffAfterDataOffTag = r5;	 Catch:{ all -> 0x0052 }
        r2.arg1 = r5;	 Catch:{ all -> 0x0052 }
        r6 = 2000; // 0x7d0 float:2.803E-42 double:9.88E-321;
        r5 = r8.sendMessageDelayed(r2, r6);	 Catch:{ all -> 0x0052 }
        if (r5 == 0) goto L_0x00ff;
    L_0x00f6:
        r5 = "Wait upto 2s for data to disconnect, then turn off radio.";
        r8.log(r5);	 Catch:{ all -> 0x0052 }
        r5 = 1;
        r8.mPendingRadioPowerOffAfterDataOff = r5;	 Catch:{ all -> 0x0052 }
        goto L_0x00d0;
    L_0x00ff:
        r5 = "Cannot send delayed Msg, turn off radio right away.";
        r8.log(r5);	 Catch:{ all -> 0x0052 }
        r8.hangupAndPowerOff();	 Catch:{ all -> 0x0052 }
        goto L_0x00d0;
    L_0x0108:
        r5 = "radioTurnedOff";
        r9.cleanUpAllConnections(r5);	 Catch:{ all -> 0x0052 }
        r2 = android.os.Message.obtain(r8);	 Catch:{ all -> 0x0052 }
        r5 = 38;
        r2.what = r5;	 Catch:{ all -> 0x0052 }
        r5 = r8.mPendingRadioPowerOffAfterDataOffTag;	 Catch:{ all -> 0x0052 }
        r5 = r5 + 1;
        r8.mPendingRadioPowerOffAfterDataOffTag = r5;	 Catch:{ all -> 0x0052 }
        r2.arg1 = r5;	 Catch:{ all -> 0x0052 }
        r6 = 10000; // 0x2710 float:1.4013E-41 double:4.9407E-320;
        r5 = r8.sendMessageDelayed(r2, r6);	 Catch:{ all -> 0x0052 }
        if (r5 == 0) goto L_0x012e;
    L_0x0125:
        r5 = "Wait upto 10s for data to disconnect, then turn off radio.";
        r8.log(r5);	 Catch:{ all -> 0x0052 }
        r5 = 1;
        r8.mPendingRadioPowerOffAfterDataOff = r5;	 Catch:{ all -> 0x0052 }
        goto L_0x00d0;
    L_0x012e:
        r5 = "Cannot send delayed Msg, turn off radio right away.";
        r8.log(r5);	 Catch:{ all -> 0x0052 }
        r8.hangupAndPowerOff();	 Catch:{ all -> 0x0052 }
        goto L_0x00d0;
        */
        throw new UnsupportedOperationException("Method not decompiled: com.android.internal.telephony.ServiceStateTracker.powerOffRadioSafely(com.android.internal.telephony.dataconnection.DcTrackerBase):void");
    }

    public boolean processPendingRadioPowerOffAfterDataOff() {
        boolean z = false;
        synchronized (this) {
            if (this.mPendingRadioPowerOffAfterDataOff) {
                log("Process pending request to turn radio off.");
                this.mPendingRadioPowerOffAfterDataOffTag++;
                hangupAndPowerOff();
                this.mPendingRadioPowerOffAfterDataOff = false;
                z = true;
            }
        }
        return z;
    }

    protected boolean onSignalStrengthResult(AsyncResult ar, boolean isGsm) {
        SignalStrength oldSignalStrength = this.mSignalStrength;
        int oldSignalBar = mSignalBar;
        if (ar.exception != null || ar.result == null) {
            log("onSignalStrengthResult() Exception from RIL : " + ar.exception);
            this.mSignalStrength = new SignalStrength(isGsm);
        } else {
            this.mSignalStrength = (SignalStrength) ar.result;
            this.mSignalStrength.validateInput();
            this.mSignalStrength.setGsm(isGsm);
        }
        try {
            if (!this.mSignalStrength.equals(oldSignalStrength) && CscFeature.getInstance().getEnableStatus("CscFeature_RIL_EnableVoicePriority")) {
                mSignalBar = this.mSignalStrength.getSignalBar();
                if (!(mSignalBar == oldSignalBar || mSignalBar == -1 || ((mSignalBar < 3 || oldSignalBar >= 3) && mSignalBar > 1))) {
                    String notiName = "WeakSignal";
                    DcTrackerBase dcTracker = this.mPhoneBase.mDcTracker;
                    if (mSignalBar >= 3) {
                        notiName = "StrongSignal";
                    }
                    dcTracker.notifyDataConnectionForSST(notiName);
                }
            }
            return notifySignalStrength();
        } catch (NullPointerException ex) {
            log("onSignalStrengthResult() Phone already destroyed: " + ex + "SignalStrength not notified");
            return false;
        }
    }

    protected boolean onSignalStrengthResult(boolean isGsm) {
        log("onSignalStrengthResult() - isGsm: " + isGsm);
        this.mSignalStrength.setGsm(isGsm);
        return notifySignalStrength();
    }

    protected void cancelPollState() {
        this.mPollingContext = new int[1];
    }

    protected boolean shouldFixTimeZoneNow(PhoneBase phoneBase, String operatorNumeric, String prevOperatorNumeric, boolean needToFixTimeZone) {
        try {
            int prevMcc;
            boolean retVal;
            int mcc = Integer.parseInt(operatorNumeric.substring(0, 3));
            try {
                prevMcc = Integer.parseInt(prevOperatorNumeric.substring(0, 3));
            } catch (Exception e) {
                prevMcc = mcc + 1;
            }
            boolean iccCardExist = false;
            if (this.mUiccApplcation != null) {
                if (this.mUiccApplcation.getState() != AppState.APPSTATE_UNKNOWN) {
                    iccCardExist = true;
                } else {
                    iccCardExist = false;
                }
            }
            if ((!iccCardExist || mcc == prevMcc) && !needToFixTimeZone) {
                retVal = false;
            } else {
                retVal = true;
            }
            log("shouldFixTimeZoneNow: retVal=" + retVal + " iccCardExist=" + iccCardExist + " operatorNumeric=" + operatorNumeric + " mcc=" + mcc + " prevOperatorNumeric=" + prevOperatorNumeric + " prevMcc=" + prevMcc + " needToFixTimeZone=" + needToFixTimeZone + " ltod=" + TimeUtils.logTimeOfDay(System.currentTimeMillis()));
            return retVal;
        } catch (Exception e2) {
            log("shouldFixTimeZoneNow: no mcc, operatorNumeric=" + operatorNumeric + " retVal=false");
            return false;
        }
    }

    public String getSystemProperty(String property, String defValue) {
        return TelephonyManager.getTelephonyProperty(property, this.mPhoneBase.getSubId(), defValue);
    }

    public List<CellInfo> getAllCellInfo() {
        List<CellInfo> list = null;
        CellInfoResult result = new CellInfoResult();
        if (this.mCi.getRilVersion() < 8) {
            log("SST.getAllCellInfo(): not implemented");
            result.list = null;
        } else if (!isCallerOnDifferentThread()) {
            log("SST.getAllCellInfo(): return last, same thread can't block");
            result.list = this.mLastCellInfoList;
        } else if (SystemClock.elapsedRealtime() - this.mLastCellInfoListTime > LAST_CELL_INFO_LIST_MAX_AGE_MS) {
            Message msg = obtainMessage(43, result);
            synchronized (result.lockObj) {
                result.list = null;
                this.mCi.getCellInfoList(msg);
                try {
                    result.lockObj.wait(5000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        } else {
            log("SST.getAllCellInfo(): return last, back to back calls");
            result.list = this.mLastCellInfoList;
        }
        synchronized (result.lockObj) {
            if (result.list != null) {
                log("SST.getAllCellInfo(): X size=" + result.list.size() + " list=" + result.list);
                list = result.list;
            } else {
                log("SST.getAllCellInfo(): X size=0 list=null");
            }
        }
        return list;
    }

    public SignalStrength getSignalStrength() {
        SignalStrength signalStrength;
        synchronized (this.mCellInfo) {
            signalStrength = this.mSignalStrength;
        }
        return signalStrength;
    }

    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        pw.println("ServiceStateTracker:");
        pw.println(" mSS=" + this.mSS);
        pw.println(" mNewSS=" + this.mNewSS);
        pw.println(" mCellInfo=" + this.mCellInfo);
        pw.println(" mRestrictedState=" + this.mRestrictedState);
        pw.println(" mPollingContext=" + this.mPollingContext);
        pw.println(" mDesiredPowerState=" + this.mDesiredPowerState);
        pw.println(" mDontPollSignalStrength=" + this.mDontPollSignalStrength);
        pw.println(" mPendingRadioPowerOffAfterDataOff=" + this.mPendingRadioPowerOffAfterDataOff);
        pw.println(" mPendingRadioPowerOffAfterDataOffTag=" + this.mPendingRadioPowerOffAfterDataOffTag);
    }

    protected void checkCorrectThread() {
        if (Thread.currentThread() != getLooper().getThread()) {
            throw new RuntimeException("ServiceStateTracker must be used from within one thread");
        }
    }

    protected boolean isCallerOnDifferentThread() {
        return Thread.currentThread() != getLooper().getThread();
    }

    protected void updateCarrierMccMncConfiguration(String newOp, String oldOp, Context context) {
        if ((newOp == null && !TextUtils.isEmpty(oldOp)) || (newOp != null && !newOp.equals(oldOp))) {
            log("update mccmnc=" + newOp + " fromServiceState=true");
            MccTable.updateMccMncConfiguration(context, newOp, true);
        }
    }

    protected boolean isTwochipDsdsOnRoamingModel() {
        String model = SystemProperties.get("ro.product.model", "Unknown");
        if ("SM-N9108W".equals(model) || "SM-N9109W".equals(model)) {
            return true;
        }
        return false;
    }

    protected boolean isTwochipDsdsOnRoaming() {
        if (!isTwochipDsdsOnRoamingModel()) {
            return false;
        }
        if ("true".equals(SystemProperties.get("ril.twochip.roaming", "false"))) {
            log("[DSDS_TWOCHIP] ril.twochip.roaming is true");
            return true;
        }
        log("[DSDS_TWOCHIP] ril.twochip.roaming is false");
        return false;
    }

    public void SimSlotOnOff(int on, Message msg) {
        this.mDesiredPowerState = true;
        this.mCi.setSimPower(on, msg);
    }

    public boolean isGlobalMode(PhoneBase phone) {
        boolean slot1Active;
        int slot1SimStatus = Integer.parseInt(MultiSimManager.getTelephonyProperty("gsm.sim.currentcardstatus", 0, "9"));
        if ("DCGS".equals("DGG") && getSlotSelectionInformation().equals("1")) {
            slot1SimStatus = Integer.parseInt(MultiSimManager.getTelephonyProperty("gsm.sim.currentcardstatus", 1, "9"));
        }
        if (slot1SimStatus == 3) {
            slot1Active = true;
        } else {
            slot1Active = false;
        }
        Log.d(LOG_TAG, "[Global mode] slot1Active: " + slot1Active + ", IsGlobalModeAvail = " + IsGlobalModeAvail + ", slot1CardStatus = " + slot1SimStatus);
        if (slot1Active && IsGlobalModeAvail && isSlot1DualCard()) {
            return true;
        }
        return false;
    }

    public boolean isSlot1DualCard() {
        String icc1Type = MultiSimManager.getTelephonyProperty("ril.ICC_TYPE", 0, "0");
        if ("DCGS".equals("DGG") && getSlotSelectionInformation().equals("1")) {
            icc1Type = MultiSimManager.getTelephonyProperty("ril.ICC_TYPE", 1, "0");
        }
        Log.d(LOG_TAG, "[Global mode] isSlot1DualCard: icc1Type=" + icc1Type);
        return "4".equals(icc1Type);
    }

    public void SetRuimRecordsLoadFinishFlag(boolean loadFinished) {
        log("[global mode]RuimRecords loading finished.");
        mRuimRecordsLoadingFinished = loadFinished;
    }

    public boolean GetRuimRecordsLoadFinishFlag() {
        return mRuimRecordsLoadingFinished;
    }

    public void startPwrSaveModeTimer(PhoneBase phone, int currSrchType) {
        if ("DCGGS".equals("DGG") || "DCGS".equals("DGG")) {
            Log.d(LOG_TAG, "[Global mode] startPwrSaveModeTimer currSrchType: " + currSrchType);
            if (mPsmStayTimerRunning) {
                Log.d(LOG_TAG, "[Global mode] PSM stay timer already running");
                return;
            }
            mPsmStayTimerRunning = true;
            if (currSrchType == 1) {
                PhoneFactory.getPhone(0).stopGlobalNetworkSearchTimer();
                PhoneFactory.getPhone(0).stopGlobalNoSvcChkTimer();
            } else {
                PhoneFactory.getPhone(1).stopGlobalNetworkSearchTimer();
                PhoneFactory.getPhone(1).stopGlobalNoSvcChkTimer();
            }
            AlarmManager am = (AlarmManager) phone.getContext().getSystemService("alarm");
            Intent intent = new Intent("android.intent.action.ACTION_GLOBAL_PWR_SAVE_MODE_STAY_TIMER_EXPIRED");
            mPrevSrchNet = currSrchType;
            this.psmChkTimer = PendingIntent.getBroadcast(phone.getContext(), 0, intent, 268435456);
            am.set(2, SystemClock.elapsedRealtime() + 1200000, this.psmChkTimer);
        }
    }

    public void stopPwrSaveModeTimer(PhoneBase phone) {
        if ("DCGGS".equals("DGG") || "DCGS".equals("DGG")) {
            Log.d(LOG_TAG, "[Global mode] stopPwrSaveModeTimer mPsmStayTimerRunning: " + mPsmStayTimerRunning);
            mPsmStayTimerRunning = false;
            ((AlarmManager) phone.getContext().getSystemService("alarm")).cancel(this.psmChkTimer);
        }
    }

    public int getPrevSrchNetType() {
        return mPrevSrchNet;
    }

    public boolean isPwrSaveModeTimerRunning() {
        return mPsmStayTimerRunning;
    }

    public boolean isPwrSaveModeRequired() {
        Log.d(LOG_TAG, "[Global mode] mCdmaSrchCnt: " + mCdmaSrchCnt + " mGsmSrchCnt: " + mGsmSrchCnt);
        if (mCdmaSrchCnt < 12 || mGsmSrchCnt < 11) {
            return false;
        }
        return true;
    }

    public void incNetSrchCnt(int srchType) {
        if (srchType == 1) {
            mCdmaSrchCnt++;
            Log.d(LOG_TAG, "[global mode] mCdmaSrchCnt increased to : " + mCdmaSrchCnt);
        } else if (srchType == 2) {
            mGsmSrchCnt++;
            Log.d(LOG_TAG, "[global mode] mGsmSrchCnt increased to : " + mGsmSrchCnt);
        } else if (srchType == 3) {
            mCdmaSrchCnt++;
            mGsmSrchCnt++;
            Log.d(LOG_TAG, "[global mode] mCdmaSrchCnt/mGsmSrchCnt increased to : " + mCdmaSrchCnt + "/" + mGsmSrchCnt);
        } else {
            Log.d(LOG_TAG, "[global mode] incNetSrchCnt no type.");
        }
    }

    public void resetNetSrchCnt(int srchType) {
        if (srchType == 1) {
            mCdmaSrchCnt = 0;
        } else if (srchType == 2) {
            mGsmSrchCnt = 0;
        } else if (srchType == 3) {
            mCdmaSrchCnt = 0;
            mGsmSrchCnt = 0;
        } else {
            Log.d(LOG_TAG, "[global mode] resetNetSrchCnt no type.");
        }
    }

    public void processPwrSaveModeExpdTimer(PhoneBase phone, boolean inSvc) {
        Log.d(LOG_TAG, "[Global mode] PSM timer expd. srchType: " + getPrevSrchNetType() + " inSvc : " + inSvc);
        stopPwrSaveModeTimer(phone);
        resetNetSrchCnt(3);
        if (!isGlobalMode(phone)) {
            Log.d(LOG_TAG, "[Global mode] global mode is off. process nothing.");
        } else if (inSvc) {
            Log.d(LOG_TAG, "[Global mode] svc acquired: " + getPrevSrchNetType() + " process nothing.");
        } else if ("DCGGS".equals("DGG") && phone.getPhoneId() == 0) {
            PhoneFactory.getPhone(0).startGlobalNetworkSearchTimer();
        } else if (getPrevSrchNetType() == 1) {
            PhoneFactory.getPhone(1).stopGlobalNoSvcChkTimer();
            PhoneFactory.getPhone(1).stopGlobalNetworkSearchTimer();
            PhoneFactory.getPhone(0).startGlobalNetworkSearchTimer();
        } else if (getPrevSrchNetType() == 2) {
            PhoneFactory.getPhone(0).stopGlobalNoSvcChkTimer();
            PhoneFactory.getPhone(0).stopGlobalNetworkSearchTimer();
            PhoneFactory.getPhone(1).startGlobalNetworkSearchTimer();
        } else {
            Log.d(LOG_TAG, "[Global mode] PSM timer expd. Prev srch net is unknown : " + getPrevSrchNetType());
            if (isSlot1CdmaActive()) {
                PhoneFactory.getPhone(1).stopGlobalNoSvcChkTimer();
                PhoneFactory.getPhone(1).stopGlobalNetworkSearchTimer();
                PhoneFactory.getPhone(0).startGlobalNetworkSearchTimer();
            } else if (isGsmActive(0)) {
                PhoneFactory.getPhone(0).stopGlobalNoSvcChkTimer();
                PhoneFactory.getPhone(0).stopGlobalNetworkSearchTimer();
                PhoneFactory.getPhone(1).startGlobalNetworkSearchTimer();
            }
        }
    }

    public void startSimCardMngLaunchTimer(PhoneBase phone) {
        Log.d(LOG_TAG, "[SimCardMngLaunch] startSimCardMngLaunchTimer");
        AlarmManager am = (AlarmManager) phone.getContext().getSystemService("alarm");
        this.sender_SimCardMngLaunchTimer = PendingIntent.getBroadcast(phone.getContext(), 0, new Intent("android.intent.action.ACTION_SIMCARDMANAGER_LAUNCH_TIMER_EXPIRED"), 0);
        am.set(2, SystemClock.elapsedRealtime() + 25000, this.sender_SimCardMngLaunchTimer);
        mSimCardMngLnchTimerRunning = true;
    }

    public void stopSimCardMngLaunchTimer(PhoneBase phone) {
        Log.d(LOG_TAG, "[SimCardMngLaunch] stopSimCardMngLaunchTimer!!! ");
        ((AlarmManager) phone.getContext().getSystemService("alarm")).cancel(this.sender_SimCardMngLaunchTimer);
        mSimCardMngLnchTimerRunning = false;
    }

    public String getSlotSelectionInformation() {
        Throwable th;
        BufferedReader in = null;
        String current_slot = "0";
        try {
            BufferedReader in2 = new BufferedReader(new FileReader("/sys/class/sec/slot_switch/slot_sel"));
            try {
                current_slot = in2.readLine();
                in2.close();
                if (in2 != null) {
                    try {
                        in2.close();
                        in = in2;
                    } catch (IOException e) {
                        Log.d(LOG_TAG, "BufferedReader close error");
                        in = in2;
                    }
                }
            } catch (IOException e2) {
                in = in2;
                try {
                    Log.d(LOG_TAG, "File open error");
                    current_slot = "0";
                    if (in != null) {
                        try {
                            in.close();
                        } catch (IOException e3) {
                            Log.d(LOG_TAG, "BufferedReader close error");
                        }
                    }
                    if (current_slot == null) {
                        return "0";
                    }
                    return current_slot;
                } catch (Throwable th2) {
                    th = th2;
                    if (in != null) {
                        try {
                            in.close();
                        } catch (IOException e4) {
                            Log.d(LOG_TAG, "BufferedReader close error");
                        }
                    }
                    throw th;
                }
            } catch (Throwable th3) {
                th = th3;
                in = in2;
                if (in != null) {
                    in.close();
                }
                throw th;
            }
        } catch (IOException e5) {
            Log.d(LOG_TAG, "File open error");
            current_slot = "0";
            if (in != null) {
                in.close();
            }
            if (current_slot == null) {
                return current_slot;
            }
            return "0";
        }
        if (current_slot == null) {
            return "0";
        }
        return current_slot;
    }

    public boolean globalNoSvcChkTimerRequired(PhoneBase phone) {
        boolean isNoSvc = (mCdmaInSvc || mGsmInSvc) ? false : true;
        TelephonyManager tm = TelephonyManager.getDefault();
        boolean isAirplaneMode = System.getInt(phone.getContext().getContentResolver(), "airplane_mode_on", 0) == 1;
        boolean pinEnabled = tm.getSimState(0) == 2 || tm.getSimState(0) == 3;
        if ("DCGS".equals("DGG") && getSlotSelectionInformation().equals("1")) {
            pinEnabled = tm.getSimState(1) == 2 || tm.getSimState(1) == 3;
        }
        boolean isFactoryCard = isFactoryMode(phone);
        boolean currentNetwork = System.getInt(phone.getContext().getContentResolver(), "CURRENT_NETWORK", 0) == 0;
        int slot1CardStatus = Integer.parseInt(MultiSimManager.getTelephonyProperty("gsm.sim.currentcardstatus", 0, "9"));
        if ("DCGS".equals("DGG") && getSlotSelectionInformation().equals("1")) {
            slot1CardStatus = Integer.parseInt(MultiSimManager.getTelephonyProperty("gsm.sim.currentcardstatus", 1, "9"));
        }
        boolean slot1Active = slot1CardStatus == 3;
        int slotOnOffProc1 = Integer.parseInt(MultiSimManager.getTelephonyProperty("gsm.sim.active", 0, "0"));
        int slotOnOffProc2 = Integer.parseInt(MultiSimManager.getTelephonyProperty("gsm.sim.active", 1, "0"));
        boolean isSlotOnOffNotReqd = slotOnOffProc1 == 0 && slotOnOffProc2 == 0;
        Log.d(LOG_TAG, "[Global mode] mCdmaInSvc:" + mCdmaInSvc + " mGsmInSvc:" + mGsmInSvc + " RuimRecordsLoadFinished:" + GetRuimRecordsLoadFinishFlag() + " pin:" + pinEnabled + " AirPlane:" + isAirplaneMode + " isFactoryCard:" + isFactoryCard + " currentNetwork:" + currentNetwork + " slot1Active:" + slot1Active + " slotOnOffProc1: " + slotOnOffProc1 + " slotOnOffProc2: " + slotOnOffProc2);
        if ("DCGGS".equals("DGG") && phone.getPhoneId() != 0) {
            Log.d(LOG_TAG, "[Global Mode] CURRENT PHONE IS NOT A FIRST PHONE. DO NOT START NO SERVICE CHECK TIMER");
            return false;
        } else if (isNoSvc && isSlot1DualCard() && !pinEnabled && !isAirplaneMode && !isFactoryCard && slot1Active && isSlotOnOffNotReqd) {
            return true;
        } else {
            return false;
        }
    }

    public boolean isChinaAreas() {
        if ("true".equals(MultiSimManager.getTelephonyProperty("gsm.ctc.chinamainland", 0, "false"))) {
            return true;
        }
        return false;
    }

    public boolean isCdmaPrefAreas(int mcc) {
        switch (mcc) {
            case 302:
            case 310:
            case 311:
            case 316:
            case 440:
            case 441:
            case 450:
            case 466:
                return true;
            default:
                return false;
        }
    }

    public boolean isUsAreas(int mcc) {
        switch (mcc) {
            case 310:
            case 311:
                return true;
            default:
                return false;
        }
    }

    public boolean isGsmActive(int slotId) {
        boolean gsmActive;
        int simStatus = Integer.parseInt(MultiSimManager.getTelephonyProperty("gsm.sim.currentcardstatus", slotId, "9"));
        if (simStatus == 3) {
            gsmActive = true;
        } else {
            gsmActive = false;
        }
        boolean isSlotSwitched = "1".equals(getSlotSelectionInformation());
        Log.i(LOG_TAG, "[global mode] slotId=" + slotId + ", gsmActive=" + gsmActive + ", isSlotSwitched=" + isSlotSwitched + ", slot1CardStatus = " + simStatus);
        if (slotId != 0) {
            return gsmActive;
        }
        if (gsmActive && isSlotSwitched) {
            return true;
        }
        return false;
    }

    public boolean isSlot1CdmaActive() {
        boolean cdmaActive;
        int slot1SimStatus = Integer.parseInt(MultiSimManager.getTelephonyProperty("gsm.sim.currentcardstatus", 0, "9"));
        if (slot1SimStatus == 3) {
            cdmaActive = true;
        } else {
            cdmaActive = false;
        }
        boolean isSlotSwitched = "1".equals(getSlotSelectionInformation());
        Log.i(LOG_TAG, "[global mode] cdmaActive : " + cdmaActive + " isSlotSwitched : " + isSlotSwitched + " slot1CardStatus = " + slot1SimStatus);
        if (!cdmaActive || isSlotSwitched) {
            return false;
        }
        return true;
    }

    public boolean isDualSlotActive() {
        boolean slot1CardReady = "3".equals(MultiSimManager.getTelephonyProperty("gsm.sim.currentcardstatus", 0, "9"));
        boolean slot2CardReady = "3".equals(MultiSimManager.getTelephonyProperty("gsm.sim.currentcardstatus", 1, "9"));
        if (slot1CardReady && slot2CardReady) {
            return true;
        }
        return false;
    }

    public boolean isGsmDfltPhoneIdx(PhoneBase phone) {
        if (!"DCGGS".equals("DGG")) {
            return true;
        }
        if (phone.getPhoneType() == 1 && phone.getPhoneId() == 0) {
            return true;
        }
        return false;
    }

    public boolean isCdmaManSel(PhoneBase phone) {
        return System.getInt(phone.getContext().getContentResolver(), "CDMA_MANUAL_SELECTED", 0) == 1;
    }

    public boolean isFactoryMode(PhoneBase phone) {
        boolean isFactoryMode = false;
        TelephonyManager mTelephonyManager = TelephonyManager.getDefault();
        if (null == null && System.getInt(phone.getContext().getContentResolver(), "SHOULD_SHUT_DOWN", 0) == 1) {
            Log.d(LOG_TAG, "Factory mode is enabled by Case #1");
            isFactoryMode = true;
        }
        if (!isFactoryMode && ("999999999999999".equals(mTelephonyManager.getSubscriberId(0)) || "999999999999999".equals(mTelephonyManager.getSubscriberId(1)))) {
            Log.d(LOG_TAG, "Factory mode is enabled by Case #2");
            isFactoryMode = true;
        }
        if (isFactoryMode) {
            return isFactoryMode;
        }
        String imeiBlocked;
        try {
            imeiBlocked = FileUtils.readTextFile(new File("/efs/FactoryApp/factorymode"), 32, null);
        } catch (IOException e) {
            imeiBlocked = "OFF";
            Log.e(LOG_TAG, "cannot open file : /efs/FactoryApp/factorymode");
        }
        if (imeiBlocked == null || !imeiBlocked.contains("ON")) {
            Log.d(LOG_TAG, "Factory mode is enabled by Case #3");
            return true;
        }
        Log.d(LOG_TAG, "Not factory mode");
        return isFactoryMode;
    }

    public void startGlobalNetworkSearchTimer() {
        Log.d(LOG_TAG, "Need to implement in sub classs");
    }

    public void stopGlobalNetworkSearchTimer() {
        Log.d(LOG_TAG, "Need to implement in sub classs");
    }

    public void startGlobalNoSvcChkTimer() {
        Log.d(LOG_TAG, "Need to implement in sub classs");
    }

    public void stopGlobalNoSvcChkTimer() {
        Log.d(LOG_TAG, "Need to implement in sub classs");
    }

    public void registerForPlmnChanged(Handler h, int what, Object obj) {
        this.mPlmnChangeRegistrants.add(new Registrant(h, what, obj));
    }

    public void unregisterForPlmnChanged(Handler h) {
        this.mPlmnChangeRegistrants.remove(h);
    }

    public boolean isSameGroupRat() {
        log("For same group RAT return TRUE");
        return ServiceState.isSameGroupRat(this.oldDataRadioTech, this.newDataRadioTech);
    }

    protected void setChinaMainlandProperty() {
        String slot1OperatorNumeric = MultiSimManager.getTelephonyProperty("gsm.operator.numeric", 0, "00000");
        String slot2OperatorNumeric = MultiSimManager.getTelephonyProperty("gsm.operator.numeric", 1, "00000");
        boolean isSlot1Home = false;
        boolean isSlot2Home = false;
        if (slot1OperatorNumeric != null && slot1OperatorNumeric.length() >= 5 && ("460".equals(slot1OperatorNumeric.substring(0, 3)) || "455".equals(slot1OperatorNumeric.substring(0, 3)))) {
            isSlot1Home = true;
        }
        if (slot2OperatorNumeric != null && slot2OperatorNumeric.length() >= 5 && ("460".equals(slot2OperatorNumeric.substring(0, 3)) || "455".equals(slot2OperatorNumeric.substring(0, 3)))) {
            isSlot2Home = true;
        }
        if (isSlot1Home || isSlot2Home) {
            MultiSimManager.setTelephonyProperty("gsm.ctc.chinamainland", 0, "true");
        } else {
            MultiSimManager.setTelephonyProperty("gsm.ctc.chinamainland", 0, "false");
        }
    }
}
