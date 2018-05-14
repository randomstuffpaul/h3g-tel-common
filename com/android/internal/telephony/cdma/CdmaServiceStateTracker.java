package com.android.internal.telephony.cdma;

import android.app.AlarmManager;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.os.AsyncResult;
import android.os.Build;
import android.os.Handler;
import android.os.Message;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.os.Registrant;
import android.os.RegistrantList;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.preference.PreferenceManager;
import android.provider.Settings.Global;
import android.provider.Settings.Secure;
import android.provider.Settings.SettingNotFoundException;
import android.provider.Settings.System;
import android.provider.Telephony.CellBroadcasts;
import android.provider.Telephony.TextBasedSmsColumns;
import android.telephony.CellInfo;
import android.telephony.CellInfoCdma;
import android.telephony.Rlog;
import android.telephony.ServiceState;
import android.telephony.SignalStrength;
import android.telephony.SubscriptionManager;
import android.telephony.cdma.CdmaCellLocation;
import android.text.TextUtils;
import android.util.EventLog;
import android.util.TimeUtils;
import com.android.internal.telephony.CommandException;
import com.android.internal.telephony.CommandException.Error;
import com.android.internal.telephony.CommandsInterface.RadioState;
import com.android.internal.telephony.EventLogTags;
import com.android.internal.telephony.HbpcdUtils;
import com.android.internal.telephony.MccTable;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneConstants.State;
import com.android.internal.telephony.SMSDispatcher;
import com.android.internal.telephony.ServiceStateTracker;
import com.android.internal.telephony.WspTypeDecoder;
import com.android.internal.telephony.uicc.UiccCardApplication;
import com.sec.android.emergencymode.EmergencyManager;
import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.Date;
import java.util.TimeZone;

public class CdmaServiceStateTracker extends ServiceStateTracker {
    private static final String ACTION_VOICELESS_OTA_PROVISIONING = "com.android.phone.ACTION_VOICELESS_OTA_PROVISIONING";
    private static final String CDMA_MAINT_REQ_ACTION = "android.intent.action.CDMA_MAINT_REQ";
    private static final String CTN_228 = "*228";
    private static final String CTN_22898 = "*22898";
    private static final String CTN_22899 = "*22899";
    private static final String CTN_NUMBER = "ctnnumber";
    protected static final String DEFAULT_MNC = "00";
    private static final int EVENT_TIMEOUT_CTN = 286;
    protected static final String INVALID_MCC = "000";
    private static final String LGT_AUTH_LOCK_ACTION = "android.intent.action.LGT_AUTH_LOCK";
    static final String LOG_TAG = "CdmaSST";
    private static final int MS_PER_HOUR = 3600000;
    private static final int NITZ_UPDATE_DIFF_DEFAULT = 2000;
    private static final int NITZ_UPDATE_SPACING_DEFAULT = 600000;
    static final int RTS_CS = 2;
    static final int RTS_IDLE = 1;
    static final int RTS_PS = 3;
    static final int RTS_STATUS = 0;
    static final String RTS_TOKEN = ";";
    static final String RTS_TOKEN_CS = "CS";
    static final String RTS_TOKEN_IDLE = "Idle";
    static final String RTS_TOKEN_PS = "PS";
    static final String RTS_TOKEN_STATUS = "Status";
    static final int RTS_VALUES_COUNT = 4;
    static final int RTS_VALUE_LOC = 1;
    private static final int TIMEOUT_CTN_OTASP = 300000;
    private static final String UNACTIVATED_MIN2_VALUE = "000000";
    private static final String UNACTIVATED_MIN_VALUE = "1111110111";
    private static final String WAKELOCK_TAG = "ServiceStateTracker";
    private static boolean mScreenState = true;
    protected String CalibrationMcc;
    protected boolean bIsSimAbsent;
    protected String curNetworkReigst;
    private ContentObserver mAutoTimeObserver;
    private ContentObserver mAutoTimeZoneObserver;
    protected RegistrantList mCdmaForSubscriptionInfoReadyRegistrants;
    private boolean mCdmaRoaming;
    private CdmaSubscriptionSourceManager mCdmaSSM;
    CdmaCellLocation mCellLoc;
    protected ContentResolver mCr;
    protected String mCurPlmn;
    private String mCurrentCarrier;
    int mCurrentOtaspMode;
    protected boolean mDataRoaming;
    private int mDefaultRoamingIndicator;
    private EmergencyManager mEmergencyMgr;
    protected boolean mForceHasChanged;
    protected boolean mFromUserSelect;
    protected boolean mGotCountryCode;
    private String mHandset_Auth;
    private String mHandset_Reg;
    protected HbpcdUtils mHbpcdUtils;
    protected int[] mHomeNetworkId;
    protected int[] mHomeSystemId;
    private BroadcastReceiver mIntentReceiver;
    private boolean mIsEriTextLoaded;
    private boolean mIsInPrl;
    protected boolean mIsMinInfoReady;
    protected boolean mIsSubscriptionFromRuim;
    protected String mMdn;
    protected String mMin;
    protected boolean mNeedFixZone;
    CdmaCellLocation mNewCellLoc;
    private int mNitzUpdateDiff;
    private int mNitzUpdateSpacing;
    private boolean mOldIsDomesticRoaming;
    private boolean mOldIsInternationalRoaming;
    private int mOldRoamingIndicator;
    private int mPendingRadioPowerOffAfterHangup;
    CDMAPhone mPhone;
    protected String mPrlVersion;
    private String mRegistrationDeniedReason;
    protected int mRegistrationState;
    private int mRoamingIconMode;
    private int mRoamingIndicator;
    long mSavedAtTime;
    long mSavedTime;
    String mSavedTimeZone;
    protected WakeLock mWakeLock;
    private boolean mZoneDst;
    private int mZoneOffset;
    private long mZoneTime;
    private boolean m_bActionLocaleChanged;

    class C00791 extends BroadcastReceiver {
        C00791() {
        }

        public void onReceive(Context context, Intent intent) {
            String salesCode = SystemProperties.get("ro.csc.sales_code");
            CdmaServiceStateTracker.this.log("Intent : " + intent.getAction());
            if (CdmaServiceStateTracker.this.mPhone.mIsTheCurrentActivePhone) {
                if ("USC-CDMA".equals("") && intent.getAction().equals("android.intent.action.MIN_VALUE_CHANGED")) {
                    CdmaServiceStateTracker.this.mMin = intent.getStringExtra("mMinValue");
                    CdmaServiceStateTracker.this.updateOtaspState();
                }
                if (intent.getAction().equals("android.intent.action.LOCALE_CHANGED")) {
                    CdmaServiceStateTracker.this.updateSpnDisplay();
                }
                if (!"CTC".equals(salesCode)) {
                    return;
                }
                if (intent.getAction().equals("android.intent.action.AIRPLANE_MODE")) {
                    CdmaServiceStateTracker.this.updateSpnDisplay();
                    return;
                } else if (intent.getAction().equals("android.intent.action.SIM_STATE_CHANGED")) {
                    CdmaServiceStateTracker.this.updateSpnDisplay();
                    return;
                } else if (intent.getAction().equals("ACTION_SET_PROPERTY_STATE")) {
                    CdmaServiceStateTracker.this.updateSpnDisplay();
                    return;
                } else {
                    return;
                }
            }
            Rlog.e(CdmaServiceStateTracker.LOG_TAG, "Received Intent " + intent + " while being destroyed. Ignoring.");
        }
    }

    static /* synthetic */ class C00824 {
        static final /* synthetic */ int[] f12x46dd5024 = new int[RadioState.values().length];

        static {
            try {
                f12x46dd5024[RadioState.RADIO_UNAVAILABLE.ordinal()] = 1;
            } catch (NoSuchFieldError e) {
            }
            try {
                f12x46dd5024[RadioState.RADIO_OFF.ordinal()] = 2;
            } catch (NoSuchFieldError e2) {
            }
        }
    }

    public String getNumberToDial(String ctnNumber) {
        String numberToDial = CTN_228;
        if (CTN_22898.equals(ctnNumber) || CTN_22899.equals(ctnNumber)) {
            return ctnNumber;
        }
        return numberToDial;
    }

    public String getHandsetInfo(String id) {
        if ("REG".equals(id)) {
            return this.mHandset_Reg;
        }
        if ("AUTH".equals(id)) {
            return this.mHandset_Auth;
        }
        if ("PHONE_NUMBER".equals(id)) {
            return this.mPhone.getLine1Number();
        }
        if ("ESN".equals(id)) {
            return this.mPhone.getEsn();
        }
        return null;
    }

    public CdmaServiceStateTracker(CDMAPhone phone) {
        this(phone, new CellInfoCdma());
    }

    protected CdmaServiceStateTracker(CDMAPhone phone, CellInfo cellInfo) {
        boolean z;
        super(phone, phone.mCi, cellInfo);
        this.mCurrentOtaspMode = 0;
        this.mNitzUpdateSpacing = SystemProperties.getInt("ro.nitz_update_spacing", NITZ_UPDATE_SPACING_DEFAULT);
        this.mNitzUpdateDiff = SystemProperties.getInt("ro.nitz_update_diff", NITZ_UPDATE_DIFF_DEFAULT);
        this.mCdmaRoaming = false;
        this.mRoamingIndicator = 1;
        this.mRoamingIconMode = 0;
        this.mDefaultRoamingIndicator = 1;
        this.mDataRoaming = false;
        this.mRegistrationState = -1;
        this.mCdmaForSubscriptionInfoReadyRegistrants = new RegistrantList();
        this.mNeedFixZone = false;
        this.mGotCountryCode = false;
        this.mCurPlmn = null;
        this.mHomeSystemId = null;
        this.mHomeNetworkId = null;
        this.mIsMinInfoReady = false;
        this.mIsEriTextLoaded = false;
        this.mIsSubscriptionFromRuim = false;
        this.mHbpcdUtils = null;
        this.mCurrentCarrier = null;
        this.mHandset_Reg = "1";
        this.mHandset_Auth = "1";
        this.m_bActionLocaleChanged = false;
        this.bIsSimAbsent = false;
        this.curNetworkReigst = null;
        this.CalibrationMcc = "";
        this.mOldRoamingIndicator = -1;
        this.mOldIsDomesticRoaming = false;
        this.mOldIsInternationalRoaming = false;
        this.mPendingRadioPowerOffAfterHangup = 0;
        this.mForceHasChanged = false;
        this.mFromUserSelect = false;
        this.mEmergencyMgr = null;
        this.mIntentReceiver = new C00791();
        this.mAutoTimeObserver = new ContentObserver(new Handler()) {
            public void onChange(boolean selfChange) {
                CdmaServiceStateTracker.this.log("Auto time state changed");
                CdmaServiceStateTracker.this.revertToNitzTime();
            }
        };
        this.mAutoTimeZoneObserver = new ContentObserver(new Handler()) {
            public void onChange(boolean selfChange) {
                CdmaServiceStateTracker.this.log("Auto time zone state changed");
                CdmaServiceStateTracker.this.revertToNitzTimeZone();
            }
        };
        this.mPhone = phone;
        this.mCr = phone.getContext().getContentResolver();
        this.mCellLoc = new CdmaCellLocation();
        this.mNewCellLoc = new CdmaCellLocation();
        if ("USC-CDMA".equals("")) {
            this.mMin = UNACTIVATED_MIN_VALUE;
            updateOtaspState();
        }
        this.mCdmaSSM = CdmaSubscriptionSourceManager.getInstance(phone.getContext(), this.mCi, this, 39, null);
        if (this.mCdmaSSM.getCdmaSubscriptionSource() == 0) {
            z = true;
        } else {
            z = false;
        }
        this.mIsSubscriptionFromRuim = z;
        this.mWakeLock = ((PowerManager) phone.getContext().getSystemService("power")).newWakeLock(1, WAKELOCK_TAG);
        this.mCi.registerForRadioStateChanged(this, 1, null);
        this.mCi.registerForVoiceNetworkStateChanged(this, 30, null);
        this.mCi.setOnNITZTime(this, 11, null);
        phone.getCallTracker().registerForVoiceCallEnded(this, 100, null);
        this.mCi.registerForCdmaPrlChanged(this, 40, null);
        phone.registerForEriFileLoaded(this, 36, null);
        this.mCi.registerForCdmaOtaProvision(this, 37, null);
        this.mCi.registerForImsRetryOver(this, 120, null);
        if (Global.getInt(this.mCr, "airplane_mode_on", 0) <= 0) {
            z = true;
        } else {
            z = false;
        }
        this.mDesiredPowerState = z;
        IntentFilter filter = new IntentFilter();
        filter.addAction("android.intent.action.LOCALE_CHANGED");
        filter.addAction("android.intent.action.SERVICE_STATE");
        if ("USC-CDMA".equals("")) {
            filter.addAction("android.intent.action.MIN_VALUE_CHANGED");
        }
        if ("CTC".equals(SystemProperties.get("ro.csc.sales_code"))) {
            filter.addAction("android.intent.action.AIRPLANE_MODE");
            filter.addAction("android.intent.action.SIM_STATE_CHANGED");
            filter.addAction("ACTION_SET_PROPERTY_STATE");
        }
        phone.getContext().registerReceiver(this.mIntentReceiver, filter);
        this.mCr.registerContentObserver(Global.getUriFor("auto_time"), true, this.mAutoTimeObserver);
        this.mCr.registerContentObserver(Global.getUriFor("auto_time_zone"), true, this.mAutoTimeZoneObserver);
        setSignalStrengthDefaultValues();
        this.mHbpcdUtils = new HbpcdUtils(phone.getContext());
        phone.notifyOtaspChanged(0);
    }

    public void dispose() {
        checkCorrectThread();
        log("ServiceStateTracker dispose");
        this.mCi.unregisterForRadioStateChanged(this);
        this.mCi.unregisterForVoiceNetworkStateChanged(this);
        this.mCi.unregisterForCdmaOtaProvision(this);
        this.mPhone.unregisterForEriFileLoaded(this);
        if (this.mUiccApplcation != null) {
            this.mUiccApplcation.unregisterForReady(this);
        }
        if (this.mIccRecords != null) {
            this.mIccRecords.unregisterForRecordsLoaded(this);
        }
        this.mCi.unSetOnNITZTime(this);
        this.mCi.unregisterForImsImsRetryOver(this);
        this.mPhone.getContext().unregisterReceiver(this.mIntentReceiver);
        this.mCr.unregisterContentObserver(this.mAutoTimeObserver);
        this.mCr.unregisterContentObserver(this.mAutoTimeZoneObserver);
        this.mPhone.getCallTracker().unregisterForVoiceCallEnded(this);
        this.mCdmaSSM.dispose(this);
        this.mCi.unregisterForCdmaPrlChanged(this);
        super.dispose();
    }

    protected void finalize() {
        log("CdmaServiceStateTracker finalized");
    }

    public void registerForSubscriptionInfoReady(Handler h, int what, Object obj) {
        Registrant r = new Registrant(h, what, obj);
        this.mCdmaForSubscriptionInfoReadyRegistrants.add(r);
        if (isMinInfoReady()) {
            r.notifyRegistrant();
        }
    }

    public void unregisterForSubscriptionInfoReady(Handler h) {
        this.mCdmaForSubscriptionInfoReadyRegistrants.remove(h);
    }

    private void saveCdmaSubscriptionSource(int source) {
        log("Storing cdma subscription source: " + source);
        Global.putInt(this.mPhone.getContext().getContentResolver(), "subscription_mode", source);
    }

    protected void getSubscriptionInfoAndStartPollingThreads() {
        this.mCi.getCDMASubscription(obtainMessage(34));
        pollState();
    }

    public void handleMessage(Message msg) {
        if (this.mPhone.mIsTheCurrentActivePhone) {
            AsyncResult ar;
            switch (msg.what) {
                case 1:
                    if (this.mCi.getRadioState() == RadioState.RADIO_ON) {
                        handleCdmaSubscriptionSource(this.mCdmaSSM.getCdmaSubscriptionSource());
                        queueNextSignalStrengthPoll();
                    }
                    setPowerStateToDesired();
                    pollState();
                    return;
                case 3:
                    if (this.mCi.getRadioState().isOn()) {
                        onSignalStrengthResult(msg.obj, false);
                        queueNextSignalStrengthPoll();
                        return;
                    }
                    return;
                case 5:
                case 24:
                case 25:
                    ar = (AsyncResult) msg.obj;
                    handlePollStateResult(msg.what, ar);
                    return;
                case 10:
                    this.mCi.getSignalStrength(obtainMessage(3));
                    return;
                case 11:
                    ar = (AsyncResult) msg.obj;
                    setTimeFromNITZString(((Object[]) ar.result)[0], ((Long) ((Object[]) ar.result)[1]).longValue());
                    return;
                case 12:
                    ar = (AsyncResult) msg.obj;
                    this.mDontPollSignalStrength = true;
                    onSignalStrengthResult(ar, false);
                    return;
                case 18:
                    if (((AsyncResult) msg.obj).exception == null) {
                        this.mCi.getVoiceRegistrationState(obtainMessage(31, null));
                        return;
                    }
                    return;
                case 26:
                    if (this.mPhone.getLteOnCdmaMode() == 1) {
                    }
                    if (this.mPhone.getLteOnCdmaMode() == 1) {
                        log("Receive EVENT_RUIM_READY");
                        pollState();
                    } else {
                        log("Receive EVENT_RUIM_READY and Send Request getCDMASubscription.");
                        getSubscriptionInfoAndStartPollingThreads();
                    }
                    this.mPhone.prepareEri();
                    return;
                case WspTypeDecoder.WSP_HEADER_IF_UNMODIFIED_SINCE /*27*/:
                    log("EVENT_RUIM_RECORDS_LOADED: what=" + msg.what);
                    updatePhoneObject();
                    updateSpnDisplay();
                    return;
                case 30:
                    pollState();
                    return;
                case 31:
                    ar = (AsyncResult) msg.obj;
                    if (ar.exception == null) {
                        String[] states = (String[]) ar.result;
                        int baseStationId = -1;
                        int baseStationLatitude = Integer.MAX_VALUE;
                        int baseStationLongitude = Integer.MAX_VALUE;
                        int systemId = -1;
                        int networkId = -1;
                        int tac = -1;
                        int lteCellId = -1;
                        if (states.length > 9) {
                            try {
                                if (states[4] != null) {
                                    baseStationId = Integer.parseInt(states[4]);
                                }
                                if (states[5] != null) {
                                    baseStationLatitude = Integer.parseInt(states[5]);
                                }
                                if (states[6] != null) {
                                    baseStationLongitude = Integer.parseInt(states[6]);
                                }
                                if (baseStationLatitude == 0 && baseStationLongitude == 0) {
                                    baseStationLatitude = Integer.MAX_VALUE;
                                    baseStationLongitude = Integer.MAX_VALUE;
                                }
                                if (states[8] != null) {
                                    systemId = Integer.parseInt(states[8]);
                                }
                                if (states[9] != null) {
                                    networkId = Integer.parseInt(states[9]);
                                }
                                if (states[1] != null) {
                                    tac = Integer.parseInt(states[1], 16);
                                }
                                if (states[2] != null) {
                                    lteCellId = Integer.parseInt(states[2], 16);
                                }
                            } catch (NumberFormatException ex) {
                                loge("error parsing cell location data: " + ex);
                            }
                        }
                        if ("VZW-CDMA".equals("") && states[3] != null && Integer.parseInt(states[3]) == 14 && baseStationId == 0 && lteCellId != -1) {
                            log("set baseStationId as lteCellId: " + lteCellId);
                            baseStationId = lteCellId;
                        }
                        this.mCellLoc.setCellLocationData(baseStationId, baseStationLatitude, baseStationLongitude, systemId, networkId);
                        if (!(tac == -1 && lteCellId == -1)) {
                            this.mCellLoc.setLteCellLocationData(tac, lteCellId);
                        }
                        this.mPhone.notifyLocationChanged();
                    }
                    disableSingleLocationUpdate();
                    return;
                case 34:
                    ar = (AsyncResult) msg.obj;
                    if (ar.exception == null) {
                        String[] cdmaSubscription = (String[]) ar.result;
                        if (cdmaSubscription == null || cdmaSubscription.length < 5) {
                            log("GET_CDMA_SUBSCRIPTION: error parsing cdmaSubscription params num=" + cdmaSubscription.length);
                            return;
                        }
                        this.mMdn = cdmaSubscription[0];
                        parseSidNid(cdmaSubscription[1], cdmaSubscription[2]);
                        this.mMin = cdmaSubscription[3];
                        this.mPrlVersion = cdmaSubscription[4];
                        log("GET_CDMA_SUBSCRIPTION: MDN=" + this.mMdn);
                        this.mIsMinInfoReady = true;
                        updateOtaspState();
                        if (this.mIsSubscriptionFromRuim || this.mIccRecords == null) {
                            log("GET_CDMA_SUBSCRIPTION either mIccRecords is null  or NV type device - not setting Imsi in mIccRecords");
                            return;
                        } else {
                            log("GET_CDMA_SUBSCRIPTION set imsi in mIccRecords");
                            return;
                        }
                    }
                    return;
                case 35:
                    updatePhoneObject();
                    getSubscriptionInfoAndStartPollingThreads();
                    return;
                case 36:
                    log("[CdmaServiceStateTracker] ERI file has been loaded, repolling.");
                    this.mForceHasChanged = true;
                    pollState();
                    return;
                case 37:
                    ar = (AsyncResult) msg.obj;
                    if (ar.exception == null) {
                        int otaStatus = ((int[]) ar.result)[0];
                        if (otaStatus == 8 || otaStatus == 10) {
                            log("EVENT_OTA_PROVISION_STATUS_CHANGE: Complete, Reload MDN");
                            this.mCi.getCDMASubscription(obtainMessage(34));
                            return;
                        }
                        return;
                    }
                    return;
                case 39:
                    handleCdmaSubscriptionSource(this.mCdmaSSM.getCdmaSubscriptionSource());
                    return;
                case 40:
                    ar = (AsyncResult) msg.obj;
                    if (ar.exception == null) {
                        int[] ints = (int[]) ar.result;
                        if (ints.length != 0) {
                            this.mPrlVersion = Integer.toString(ints[0]);
                            return;
                        }
                        return;
                    }
                    return;
                case WspTypeDecoder.WSP_HEADER_WWW_AUTHENTICATE /*45*/:
                    log("EVENT_CHANGE_IMS_STATE");
                    setPowerStateToDesired();
                    return;
                case 100:
                    Rlog.d(LOG_TAG, "Handle Message EVENT_CALL_HANGUP_BEFORE_DEACTIVEPDP");
                    if (this.mPendingRadioPowerOffAfterHangup != 0) {
                        Rlog.d(LOG_TAG, "waiting before radio turn off");
                        this.mPendingRadioPowerOffAfterHangup = 0;
                        powerOffRadioSafely(this.mPhone.mDcTracker);
                        return;
                    }
                    return;
                case 106:
                    log("Exit emergency callback mode because of No service");
                    this.mPhone.exitEmergencyCallbackMode();
                    return;
                case 120:
                    Rlog.d(LOG_TAG, "Handle Message EVENT_IMS_RETRYOVER");
                    SMSDispatcher smsDispatcher = this.mPhone.getSMSDispatcher();
                    if (smsDispatcher != null) {
                        Rlog.d(LOG_TAG, "Sending domain change notification");
                        smsDispatcher.sendDomainChangeSms((byte) 0);
                        return;
                    }
                    return;
                case EVENT_TIMEOUT_CTN /*286*/:
                    removeMessages(EVENT_TIMEOUT_CTN);
                    if (this.mFromUserSelect) {
                        this.mFromUserSelect = false;
                        log("EVENT_TIMEOUT_CTN mFromUserSelect = " + this.mFromUserSelect);
                        return;
                    }
                    return;
                default:
                    super.handleMessage(msg);
                    return;
            }
        }
        loge("Received message " + msg + "[" + msg.what + "]" + " while being destroyed. Ignoring.");
    }

    private void handleCdmaSubscriptionSource(int newSubscriptionSource) {
        log("Subscription Source : " + newSubscriptionSource);
        this.mIsSubscriptionFromRuim = newSubscriptionSource == 0;
        saveCdmaSubscriptionSource(newSubscriptionSource);
        if (!this.mIsSubscriptionFromRuim) {
            sendMessage(obtainMessage(35));
        }
    }

    protected void setPowerStateToDesired() {
        if (this.mDesiredPowerState && this.mCi.getRadioState() == RadioState.RADIO_OFF) {
            this.mCi.setRadioPower(true, null);
        } else if (!this.mDesiredPowerState && this.mCi.getRadioState().isOn()) {
            powerOffRadioSafely(this.mPhone.mDcTracker);
        } else if (this.mDeviceShuttingDown && this.mCi.getRadioState().isAvailable()) {
            this.mCi.requestShutdown(null);
        }
    }

    protected void updateSpnDisplay() {
        String plmn = this.mSS.getOperatorAlphaLong();
        String salesCode = SystemProperties.get("ro.csc.sales_code");
        if ("CTC".equals(salesCode) || "CHC".equals(salesCode)) {
            plmn = updateSpnDisplayChn(plmn);
            log("plmn = " + plmn);
        }
        if (!TextUtils.equals(plmn, this.mCurPlmn)) {
            log(String.format("updateSpnDisplay: changed sending intent showPlmn='%b' plmn='%s'", new Object[]{Boolean.valueOf(plmn != null), plmn}));
            Intent intent = new Intent("android.provider.Telephony.SPN_STRINGS_UPDATED");
            intent.addFlags(536870912);
            intent.putExtra("showSpn", false);
            intent.putExtra("spn", "");
            intent.putExtra("showPlmn", showPlmn);
            intent.putExtra(CellBroadcasts.PLMN, plmn);
            SubscriptionManager.putPhoneIdAndSubIdExtra(intent, this.mPhone.getPhoneId());
            this.mPhone.getContext().sendStickyBroadcastAsUser(intent, UserHandle.ALL);
        }
        this.mCurPlmn = plmn;
    }

    protected String updateSpnDisplayChn(String plmn) {
        String salesCode = SystemProperties.get("ro.csc.sales_code");
        if (Global.getInt(this.mCr, "airplane_mode_on", 0) == 1) {
            plmn = this.mPhone.getContext().getText(17039630).toString();
            log("Airplane mode, plmn = " + plmn);
            return plmn;
        }
        String CardStatusCDMA = getSystemProperty("gsm.sim.state", "ABSENT");
        String CardOffStatus = getSystemProperty("gsm.sim.currentcardstatus", "3");
        boolean plmnCDMAstate = false;
        int simDBvalue0 = System.getInt(this.mPhone.getContext().getContentResolver(), "phone1_on", 1);
        int simDBvalue1 = System.getInt(this.mPhone.getContext().getContentResolver(), "phone2_on", 1);
        log("CardStatusCDMA = " + CardStatusCDMA + " CardOffStatus = " + CardOffStatus);
        log("simDBvalue0 = " + simDBvalue0 + " simDBvalue1 = " + simDBvalue1);
        if (("UNKNOWN".equals(CardStatusCDMA) || "READY".equals(CardStatusCDMA)) && "2".equals(CardOffStatus)) {
            plmn = Resources.getSystem().getText(17041532).toString();
        } else if ("ABSENT".equals(CardStatusCDMA) || "UNKNOWN".equals(CardStatusCDMA) || "NOT_READY".equals(CardStatusCDMA)) {
            plmn = Resources.getSystem().getText(17041533).toString();
            if ("CHC".equals(salesCode)) {
                plmn = Resources.getSystem().getText(17040280).toString();
            }
        } else if ("PIN_REQUIRED".equals(CardStatusCDMA) || "PUK_REQUIRED".equals(CardStatusCDMA)) {
            plmn = Resources.getSystem().getText(17041531).toString();
        } else if ((simDBvalue0 == 0 && this.mPhone.mPhoneId == 0 && "3".equals(CardOffStatus)) || (simDBvalue1 == 0 && this.mPhone.mPhoneId == 1 && "3".equals(CardOffStatus))) {
            log("Sim will be activate/deactivate soon, plmn set to --");
            plmn = "--";
        } else {
            plmn = "--";
            plmnCDMAstate = true;
        }
        log("plmnCDMAstate = " + plmnCDMAstate);
        if (!plmnCDMAstate) {
            return plmn;
        }
        String numeric = this.mSS.getOperatorNumeric();
        String mRuim = getSystemProperty("gsm.sim.operator.numeric", "");
        if ("READY".equals(CardStatusCDMA)) {
            log("RUIM is ready!");
            plmn = this.mSS.getOperatorAlphaLong();
            log("RUIM plmn = " + plmn);
        } else {
            plmn = "--";
        }
        if (plmn != null && plmn.contains("Empty")) {
            plmn = null;
        }
        if (TextUtils.isEmpty(plmn) && this.mSS.getState() != 0) {
            plmn = "--";
        }
        if (mRuim != null) {
            log("RUIM string is = " + mRuim + ", plmn=" + plmn + ", locale=" + SystemProperties.get("persist.sys.language", "zh"));
            if (!TextUtils.isEmpty(numeric) && "46003".equals(numeric)) {
                plmn = this.mPhone.getContext().getText(17041536).toString();
            } else if (!TextUtils.isEmpty(numeric) && "46000".equals(numeric)) {
                plmn = this.mPhone.getContext().getText(17041520).toString();
            } else if (!TextUtils.isEmpty(numeric) && "46001".equals(numeric)) {
                plmn = this.mPhone.getContext().getText(17041535).toString();
            } else if ("45502".equals(numeric)) {
                log("numeric=" + numeric);
            }
        }
        String mLang = SystemProperties.get("persist.sys.language", "zh");
        if (mLang == null || !mLang.equalsIgnoreCase("zh") || !"China Telecom".equalsIgnoreCase(plmn)) {
            return plmn;
        }
        plmn = Resources.getSystem().getText(17041536).toString();
        log("but, language is zh, plmn=" + plmn + ", mRuim=" + mRuim);
        return plmn;
    }

    protected Phone getPhone() {
        return this.mPhone;
    }

    protected void handlePollStateResultMessage(int what, AsyncResult ar) {
        switch (what) {
            case 5:
                Object states = (String[]) ar.result;
                log("handlePollStateResultMessage: EVENT_POLL_STATE_GPRS states.length=" + states.length + " states=" + states);
                int regState = 4;
                int dataRadioTechnology = 0;
                if (states.length > 0) {
                    try {
                        regState = Integer.parseInt(states[0]);
                        if (states.length >= 4 && states[3] != null) {
                            dataRadioTechnology = Integer.parseInt(states[3]);
                        }
                    } catch (NumberFormatException ex) {
                        loge("handlePollStateResultMessage: error parsing GprsRegistrationState: " + ex);
                    }
                }
                int dataRegState = regCodeToServiceState(regState);
                this.mNewSS.setDataRegState(dataRegState);
                this.mNewSS.setRilDataRadioTechnology(dataRadioTechnology);
                log("handlPollStateResultMessage: cdma setDataRegState=" + dataRegState + " regState=" + regState + " dataRadioTechnology=" + dataRadioTechnology);
                return;
            case 24:
                String[] states2 = (String[]) ar.result;
                int registrationState = 4;
                int radioTechnology = -1;
                int baseStationId = -1;
                int baseStationLatitude = Integer.MAX_VALUE;
                int baseStationLongitude = Integer.MAX_VALUE;
                int cssIndicator = 0;
                int systemIsInPrl = 0;
                int reasonForDenial = 0;
                int systemId = -1;
                int networkId = -1;
                int roamingIndicator = 1;
                int defaultRoamingIndicator = 1;
                int tac = -1;
                int lteCellId = -1;
                if (states2.length >= 14) {
                    try {
                        if (states2[0] != null) {
                            registrationState = Integer.parseInt(states2[0]);
                        }
                        if (states2[3] != null) {
                            radioTechnology = Integer.parseInt(states2[3]);
                        }
                        if (states2[4] != null) {
                            baseStationId = Integer.parseInt(states2[4]);
                        }
                        if (states2[5] != null) {
                            baseStationLatitude = Integer.parseInt(states2[5]);
                        }
                        if (states2[6] != null) {
                            baseStationLongitude = Integer.parseInt(states2[6]);
                        }
                        if (baseStationLatitude == 0 && baseStationLongitude == 0) {
                            baseStationLatitude = Integer.MAX_VALUE;
                            baseStationLongitude = Integer.MAX_VALUE;
                        }
                        if (states2[7] != null) {
                            cssIndicator = Integer.parseInt(states2[7]);
                        }
                        if (states2[8] != null) {
                            systemId = Integer.parseInt(states2[8]);
                        }
                        if (states2[9] != null) {
                            networkId = Integer.parseInt(states2[9]);
                        }
                        if (states2[10] != null) {
                            roamingIndicator = Integer.parseInt(states2[10]);
                        }
                        if (states2[11] != null) {
                            systemIsInPrl = Integer.parseInt(states2[11]);
                        }
                        if (states2[12] != null) {
                            defaultRoamingIndicator = Integer.parseInt(states2[12]);
                        }
                        if (states2[13] != null) {
                            reasonForDenial = Integer.parseInt(states2[13]);
                        }
                        if (states2[1] != null) {
                            tac = Integer.parseInt(states2[1], 16);
                        }
                        if (states2[2] != null) {
                            lteCellId = Integer.parseInt(states2[2], 16);
                        }
                    } catch (NumberFormatException ex2) {
                        loge("EVENT_POLL_STATE_REGISTRATION_CDMA: error parsing: " + ex2);
                    }
                    this.mRegistrationState = registrationState;
                    boolean z = regCodeIsRoaming(registrationState) && !isRoamIndForHomeSystem(states2[10]);
                    this.mCdmaRoaming = z;
                    this.mNewSS.setState(regCodeToServiceState(registrationState));
                    this.mNewSS.setRilVoiceRadioTechnology(radioTechnology);
                    this.mNewSS.setCssIndicator(cssIndicator);
                    this.mNewSS.setSystemAndNetworkId(systemId, networkId);
                    this.mRoamingIndicator = roamingIndicator;
                    if ("2".equals("0")) {
                        this.mRoamingIndicator = roamingIndicator & 127;
                        this.mRoamingIconMode = (roamingIndicator >> 7) & 1;
                    }
                    this.mIsInPrl = systemIsInPrl != 0;
                    this.mDefaultRoamingIndicator = defaultRoamingIndicator;
                    if ("VZW-CDMA".equals("") && radioTechnology == 14 && baseStationId == 0 && lteCellId != -1) {
                        log("set baseStationId as lteCellId: " + lteCellId);
                        baseStationId = lteCellId;
                    }
                    this.mNewCellLoc.setCellLocationData(baseStationId, baseStationLatitude, baseStationLongitude, systemId, networkId);
                    if (!(tac == -1 && lteCellId == -1)) {
                        log("tac: " + tac + ", lteCellId: " + lteCellId);
                        this.mNewCellLoc.setLteCellLocationData(tac, lteCellId);
                    }
                    if (reasonForDenial == 0) {
                        this.mRegistrationDeniedReason = "General";
                    } else if (reasonForDenial == 1) {
                        this.mRegistrationDeniedReason = "Authentication Failure";
                    } else {
                        this.mRegistrationDeniedReason = "";
                    }
                    if (this.mRegistrationState == 3) {
                        log("Registration denied, " + this.mRegistrationDeniedReason);
                        return;
                    }
                    return;
                }
                throw new RuntimeException("Warning! Wrong number of parameters returned from RIL_REQUEST_REGISTRATION_STATE: expected 14 or more strings and got " + states2.length + " strings");
            case 25:
                String[] opNames = (String[]) ar.result;
                if (opNames == null || opNames.length < 3) {
                    log("EVENT_POLL_STATE_OPERATOR_CDMA: error parsing opNames");
                    return;
                }
                if (opNames[2] == null || opNames[2].length() < 5 || "00000".equals(opNames[2])) {
                    opNames[2] = SystemProperties.get(CDMAPhone.PROPERTY_CDMA_HOME_OPERATOR_NUMERIC, "00000");
                    log("RIL_REQUEST_OPERATOR.response[2], the numeric,  is bad. Using SystemProperties '" + CDMAPhone.PROPERTY_CDMA_HOME_OPERATOR_NUMERIC + "'= " + opNames[2]);
                }
                if (this.mIsSubscriptionFromRuim) {
                    String brandOverride = this.mUiccController.getUiccCard() != null ? this.mUiccController.getUiccCard().getOperatorBrandOverride() : null;
                    if (brandOverride != null) {
                        this.mNewSS.setOperatorName(brandOverride, brandOverride, opNames[2]);
                        return;
                    } else {
                        this.mNewSS.setOperatorName(opNames[0], opNames[1], opNames[2]);
                        return;
                    }
                }
                this.mNewSS.setOperatorName(null, opNames[1], opNames[2]);
                if ("2".equals("0")) {
                    this.mNewSS.setOperatorName(opNames[0], opNames[1], opNames[2]);
                    return;
                }
                return;
            default:
                loge("handlePollStateResultMessage: RIL response handle in wrong phone! Expected CDMA RIL request and get GSM RIL request.");
                return;
        }
    }

    protected void handlePollStateResult(int what, AsyncResult ar) {
        String salesCode = SystemProperties.get("ro.csc.sales_code");
        if (ar.userObj == this.mPollingContext) {
            if (ar.exception != null) {
                Error err = null;
                if (ar.exception instanceof CommandException) {
                    err = ((CommandException) ar.exception).getCommandError();
                }
                if (err == Error.RADIO_NOT_AVAILABLE) {
                    cancelPollState();
                    return;
                } else if (!this.mCi.getRadioState().isOn()) {
                    cancelPollState();
                    return;
                } else if (err != Error.OP_NOT_ALLOWED_BEFORE_REG_NW) {
                    loge("handlePollStateResult: RIL returned an error where it must succeed" + ar.exception);
                }
            } else {
                try {
                    handlePollStateResultMessage(what, ar);
                } catch (RuntimeException ex) {
                    loge("handlePollStateResult: Exception while polling service state. Probably malformed RIL response." + ex);
                }
            }
            int[] iArr = this.mPollingContext;
            iArr[0] = iArr[0] - 1;
            if (this.mPollingContext[0] == 0) {
                boolean z;
                boolean namMatch = false;
                if (!isSidsAllZeros() && isHomeSid(this.mNewSS.getSystemId())) {
                    namMatch = true;
                }
                if (this.mCdmaRoaming || this.mDataRoaming) {
                    z = true;
                } else {
                    z = false;
                }
                this.mCdmaRoaming = z;
                if (this.mIsSubscriptionFromRuim) {
                    this.mNewSS.setRoaming(isRoamingBetweenOperators(this.mCdmaRoaming, this.mNewSS));
                    if ("2".equals("0")) {
                        this.mNewSS.setRoaming(isRoamingBetweenOperators(this.mCdmaRoaming, this.mRoamingIndicator));
                    }
                } else {
                    this.mNewSS.setRoaming(this.mCdmaRoaming);
                }
                this.mNewSS.setCdmaDefaultRoamingIndicator(this.mDefaultRoamingIndicator);
                this.mNewSS.setCdmaRoamingIndicator(this.mRoamingIndicator);
                boolean isPrlLoaded = true;
                if (TextUtils.isEmpty(this.mPrlVersion)) {
                    isPrlLoaded = false;
                }
                if (!isPrlLoaded || this.mNewSS.getRilVoiceRadioTechnology() == 0) {
                    log("Turn off roaming indicator if !isPrlLoaded or voice RAT is unknown");
                    this.mNewSS.setCdmaRoamingIndicator(1);
                    if (this.mRoamingIndicator == 0 && this.mNewSS.getVoiceRegState() == 0 && this.mNewSS.getRoaming()) {
                        log("Set roaming indicator to ON in roaming network");
                        this.mNewSS.setCdmaRoamingIndicator(0);
                    }
                } else if ("0".equals("0") && !isSidsAllZeros()) {
                    if (!namMatch && !this.mIsInPrl) {
                        this.mNewSS.setCdmaRoamingIndicator(this.mDefaultRoamingIndicator);
                    } else if (!namMatch || this.mIsInPrl) {
                        if (!namMatch && this.mIsInPrl) {
                            this.mNewSS.setCdmaRoamingIndicator(this.mRoamingIndicator);
                        } else if (this.mRoamingIndicator <= 2) {
                            this.mNewSS.setCdmaRoamingIndicator(1);
                        } else {
                            this.mNewSS.setCdmaRoamingIndicator(this.mRoamingIndicator);
                        }
                    } else if (this.mNewSS.getRilVoiceRadioTechnology() == 14) {
                        log("Turn off roaming indicator as voice is LTE");
                        this.mNewSS.setCdmaRoamingIndicator(1);
                    } else {
                        this.mNewSS.setCdmaRoamingIndicator(2);
                    }
                }
                int roamingIndicator = this.mNewSS.getCdmaRoamingIndicator();
                this.mNewSS.setCdmaEriIconIndex(this.mPhone.mEriManager.getCdmaEriIconIndex(roamingIndicator, this.mDefaultRoamingIndicator));
                this.mNewSS.setCdmaEriIconMode(this.mPhone.mEriManager.getCdmaEriIconMode(roamingIndicator, this.mDefaultRoamingIndicator));
                if ("2".equals("0")) {
                    this.mNewSS.setCdmaEriIconIndex(roamingIndicator);
                    this.mNewSS.setCdmaEriIconMode(this.mRoamingIconMode);
                }
                log("EriIconIndex : " + this.mNewSS.getCdmaEriIconIndex() + ", EriIconMode : " + this.mNewSS.getCdmaEriIconMode());
                log("Set CDMA Roaming Indicator to: " + this.mNewSS.getCdmaRoamingIndicator() + ". mCdmaRoaming = " + this.mCdmaRoaming + ", isPrlLoaded = " + isPrlLoaded + ". namMatch = " + namMatch + " , mIsInPrl = " + this.mIsInPrl + ", mRoamingIndicator = " + this.mRoamingIndicator + ", mDefaultRoamingIndicator= " + this.mDefaultRoamingIndicator);
                pollStateDone();
            }
        }
    }

    protected void setSignalStrengthDefaultValues() {
        this.mSignalStrength = new SignalStrength(false);
    }

    public void pollState() {
        this.mPollingContext = new int[1];
        this.mPollingContext[0] = 0;
        switch (C00824.f12x46dd5024[this.mCi.getRadioState().ordinal()]) {
            case 1:
                this.mNewSS.setStateOutOfService();
                this.mNewCellLoc.setStateInvalid();
                setSignalStrengthDefaultValues();
                this.mGotCountryCode = false;
                pollStateDone();
                return;
            case 2:
                this.mNewSS.setStateOff();
                this.mNewCellLoc.setStateInvalid();
                setSignalStrengthDefaultValues();
                this.mGotCountryCode = false;
                pollStateDone();
                return;
            default:
                int[] iArr = this.mPollingContext;
                iArr[0] = iArr[0] + 1;
                this.mCi.getOperator(obtainMessage(25, this.mPollingContext));
                iArr = this.mPollingContext;
                iArr[0] = iArr[0] + 1;
                this.mCi.getVoiceRegistrationState(obtainMessage(24, this.mPollingContext));
                iArr = this.mPollingContext;
                iArr[0] = iArr[0] + 1;
                this.mCi.getDataRegistrationState(obtainMessage(5, this.mPollingContext));
                return;
        }
    }

    protected void fixTimeZone(String isoCountryCode) {
        if (this.mNeedFixZone && ((isoCountryCode.equals("us") || "ca".equals(isoCountryCode)) && this.mZoneOffset == 0)) {
            Rlog.w(LOG_TAG, "mZoneOffset is Invalid");
            this.mNeedFixZone = false;
            return;
        }
        TimeZone zone = null;
        String zoneName = SystemProperties.get("persist.sys.timezone");
        log("fixTimeZone zoneName='" + zoneName + "' mZoneOffset=" + this.mZoneOffset + " mZoneDst=" + this.mZoneDst + " iso-cc='" + isoCountryCode + "' iso-cc-idx=" + Arrays.binarySearch(GMT_COUNTRY_CODES, isoCountryCode));
        if (this.mZoneOffset == 0 && !this.mZoneDst && zoneName != null && zoneName.length() > 0 && Arrays.binarySearch(GMT_COUNTRY_CODES, isoCountryCode) < 0) {
            zone = TimeZone.getDefault();
            if (this.mNeedFixZone) {
                long ctm = System.currentTimeMillis();
                long tzOffset = (long) zone.getOffset(ctm);
                log("fixTimeZone: tzOffset=" + tzOffset + " ltod=" + TimeUtils.logTimeOfDay(ctm));
                if (getAutoTime()) {
                    long adj = ctm - tzOffset;
                    log("fixTimeZone: adj ltod=" + TimeUtils.logTimeOfDay(adj));
                    setAndBroadcastNetworkSetTime(adj);
                } else {
                    this.mSavedTime -= tzOffset;
                    log("fixTimeZone: adj mSavedTime=" + this.mSavedTime);
                }
            }
            log("fixTimeZone: using default TimeZone");
        } else if (!isoCountryCode.equals("")) {
            zone = TimeUtils.getTimeZone(this.mZoneOffset, this.mZoneDst, this.mZoneTime, isoCountryCode);
            log("fixTimeZone: using getTimeZone(off, dst, time, iso)");
        } else if (this.mSS.getVoiceRegState() == 0) {
            zone = getNitzTimeZone(this.mZoneOffset, this.mZoneDst, this.mZoneTime);
            log("fixTimeZone: using NITZ TimeZone");
        }
        this.mNeedFixZone = false;
        if (zone != null) {
            log("fixTimeZone: zone != null zone.getID=" + zone.getID());
            if (getAutoTimeZone()) {
                setAndBroadcastNetworkSetTimeZone(zone.getID());
            } else {
                log("fixTimeZone: skip changing zone as getAutoTimeZone was false");
            }
            saveNitzTimeZone(zone.getID());
            return;
        }
        log("fixTimeZone: zone == null, do nothing for zone");
    }

    protected void pollStateDone() {
        log("pollStateDone: cdma oldSS=[" + this.mSS + "] newSS=[" + this.mNewSS + "]");
        String salesCode = SystemProperties.get("ro.csc.sales_code");
        if (Build.IS_DEBUGGABLE && SystemProperties.getBoolean("telephony.test.forceRoaming", false)) {
            this.mNewSS.setRoaming(true);
        }
        useDataRegStateForDataOnlyDevices();
        boolean hasRegistered = this.mSS.getVoiceRegState() != 0 && this.mNewSS.getVoiceRegState() == 0;
        if (this.mSS.getVoiceRegState() != 0 || this.mNewSS.getVoiceRegState() == 0) {
        }
        boolean hasCdmaDataConnectionAttached = this.mSS.getDataRegState() != 0 && this.mNewSS.getDataRegState() == 0;
        boolean hasCdmaDataConnectionDetached = this.mSS.getDataRegState() == 0 && this.mNewSS.getDataRegState() != 0;
        boolean hasCdmaDataConnectionChanged = this.mSS.getDataRegState() != this.mNewSS.getDataRegState();
        boolean hasRilVoiceRadioTechnologyChanged = this.mSS.getRilVoiceRadioTechnology() != this.mNewSS.getRilVoiceRadioTechnology();
        boolean hasRilDataRadioTechnologyChanged = this.mSS.getRilDataRadioTechnology() != this.mNewSS.getRilDataRadioTechnology();
        boolean hasChanged = !this.mNewSS.equals(this.mSS);
        boolean hasRoamingOn = !this.mSS.getRoaming() && this.mNewSS.getRoaming();
        boolean hasRoamingOff = this.mSS.getRoaming() && !this.mNewSS.getRoaming();
        boolean hasLocationChanged = !this.mNewCellLoc.equals(this.mCellLoc);
        if (this.mForceHasChanged) {
            hasChanged = true;
            this.mForceHasChanged = false;
            log("Change hasChanged to " + true);
        }
        boolean hasPlmnChanged = false;
        if (!(this.mSS.getOperatorNumeric() == null || this.mNewSS.getOperatorNumeric() == null || this.mSS.getOperatorNumeric() == this.mNewSS.getOperatorNumeric())) {
            hasPlmnChanged = true;
        }
        if (!(this.mSS.getVoiceRegState() == this.mNewSS.getVoiceRegState() && this.mSS.getDataRegState() == this.mNewSS.getDataRegState())) {
            EventLog.writeEvent(EventLogTags.CDMA_SERVICE_STATE_CHANGE, new Object[]{Integer.valueOf(this.mSS.getVoiceRegState()), Integer.valueOf(this.mSS.getDataRegState()), Integer.valueOf(this.mNewSS.getVoiceRegState()), Integer.valueOf(this.mNewSS.getDataRegState())});
        }
        ServiceState tss = this.mSS;
        this.mSS = this.mNewSS;
        this.mNewSS = tss;
        this.mNewSS.setStateOutOfService();
        CdmaCellLocation tcl = this.mCellLoc;
        this.mCellLoc = this.mNewCellLoc;
        this.mNewCellLoc = tcl;
        if (hasRilVoiceRadioTechnologyChanged) {
            updatePhoneObject();
            if ("CTC".equals(salesCode)) {
                this.mPhone.setSystemProperty("gsm.voice.network.type", ServiceState.rilRadioTechnologyToString(this.mSS.getRilVoiceRadioTechnology()));
            }
        }
        if (hasRilDataRadioTechnologyChanged) {
            this.mPhone.setSystemProperty("gsm.network.type", ServiceState.rilRadioTechnologyToString(this.mSS.getRilDataRadioTechnology()));
        }
        if (hasRegistered) {
            this.mNetworkAttachedRegistrants.notifyRegistrants();
        }
        if (hasChanged) {
            if (this.mCi.getRadioState().isOn() && (!this.mIsSubscriptionFromRuim || this.mPhone.isEriFileLoaded())) {
                String eriText;
                if (this.mSS.getVoiceRegState() == 0) {
                    eriText = this.mPhone.getCdmaEriText();
                } else {
                    eriText = this.mPhone.getContext().getText(17039570).toString();
                }
                if (!"CTC".equals(SystemProperties.get("ro.csc.sales_code"))) {
                    this.mSS.setOperatorAlphaLong(eriText);
                }
            }
            this.mPhone.setSystemProperty("gsm.operator.alpha", this.mSS.getOperatorAlphaLong());
            String prevOperatorNumeric = getSystemProperty("gsm.operator.numeric", "");
            String operatorNumeric = this.mSS.getOperatorNumeric();
            if (isInvalidOperatorNumeric(operatorNumeric)) {
                operatorNumeric = fixUnknownMcc(operatorNumeric, this.mSS.getSystemId());
            }
            this.mPhone.setSystemProperty("gsm.operator.numeric", operatorNumeric);
            updateCarrierMccMncConfiguration(operatorNumeric, prevOperatorNumeric, this.mPhone.getContext());
            if (isInvalidOperatorNumeric(operatorNumeric)) {
                log("operatorNumeric " + operatorNumeric + "is invalid");
                this.mPhone.setSystemProperty("gsm.operator.iso-country", "");
                this.mGotCountryCode = false;
            } else {
                String isoCountryCode = "";
                String mcc = operatorNumeric.substring(0, 3);
                try {
                    isoCountryCode = MccTable.countryCodeForMcc(Integer.parseInt(operatorNumeric.substring(0, 3)));
                } catch (NumberFormatException ex) {
                    loge("pollStateDone: countryCodeForMcc error" + ex);
                } catch (StringIndexOutOfBoundsException ex2) {
                    loge("pollStateDone: countryCodeForMcc error" + ex2);
                }
                this.mPhone.setSystemProperty("gsm.operator.iso-country", isoCountryCode);
                this.mGotCountryCode = true;
                setOperatorIdd(operatorNumeric);
                if (shouldFixTimeZoneNow(this.mPhone, operatorNumeric, prevOperatorNumeric, this.mNeedFixZone)) {
                    fixTimeZone(isoCountryCode);
                }
            }
            this.mPhone.setSystemProperty("gsm.operator.isroaming", this.mSS.getRoaming() ? "true" : "false");
            this.mPhone.setSystemProperty("ril.servicestate", Integer.toString(this.mSS.getState()));
            updateSpnDisplay();
            this.mPhone.notifyServiceStateChanged(this.mSS);
        }
        if (hasCdmaDataConnectionAttached) {
            this.mAttachedRegistrants.notifyRegistrants();
        }
        if (hasCdmaDataConnectionDetached) {
            this.mDetachedRegistrants.notifyRegistrants();
        }
        if (hasCdmaDataConnectionChanged || hasRilDataRadioTechnologyChanged) {
            notifyDataRegStateRilRadioTechnologyChanged();
            this.mPhone.notifyDataConnection(null);
        }
        if (hasRoamingOn) {
            this.mRoamingOnRegistrants.notifyRegistrants();
        }
        if (hasRoamingOff) {
            this.mRoamingOffRegistrants.notifyRegistrants();
        }
        if (hasLocationChanged) {
            this.mPhone.notifyLocationChanged();
        }
        if (hasPlmnChanged) {
            this.mPlmnChangeRegistrants.notifyRegistrants();
        }
    }

    protected boolean isInvalidOperatorNumeric(String operatorNumeric) {
        return operatorNumeric == null || operatorNumeric.length() < 5 || operatorNumeric.startsWith(INVALID_MCC);
    }

    protected String fixUnknownMcc(String operatorNumeric, int sid) {
        if (sid <= 0) {
            return operatorNumeric;
        }
        boolean isNitzTimeZone = false;
        int timeZone = 0;
        if (this.mSavedTimeZone != null) {
            timeZone = TimeZone.getTimeZone(this.mSavedTimeZone).getRawOffset() / MS_PER_HOUR;
            isNitzTimeZone = true;
        } else {
            TimeZone tzone = getNitzTimeZone(this.mZoneOffset, this.mZoneDst, this.mZoneTime);
            if (tzone != null) {
                timeZone = tzone.getRawOffset() / MS_PER_HOUR;
            }
        }
        int mcc = this.mHbpcdUtils.getMcc(sid, timeZone, this.mZoneDst ? 1 : 0, isNitzTimeZone);
        if (mcc > 0) {
            operatorNumeric = Integer.toString(mcc) + DEFAULT_MNC;
        }
        return operatorNumeric;
    }

    protected void setOperatorIdd(String operatorNumeric) {
        String idd = this.mHbpcdUtils.getIddByMcc(Integer.parseInt(operatorNumeric.substring(0, 3)));
        if (idd == null || idd.isEmpty()) {
            this.mPhone.setSystemProperty("gsm.operator.idpstring", "+");
        } else {
            this.mPhone.setSystemProperty("gsm.operator.idpstring", idd);
        }
    }

    private TimeZone getNitzTimeZone(int offset, boolean dst, long when) {
        TimeZone guess = findTimeZone(offset, dst, when);
        if (guess == null) {
            guess = findTimeZone(offset, !dst, when);
        }
        log("getNitzTimeZone returning " + (guess == null ? guess : guess.getID()));
        return guess;
    }

    private TimeZone findTimeZone(int offset, boolean dst, long when) {
        int rawOffset = offset;
        if (dst) {
            rawOffset -= MS_PER_HOUR;
        }
        String[] zones = TimeZone.getAvailableIDs(rawOffset);
        Date d = new Date(when);
        for (String zone : zones) {
            TimeZone tz = TimeZone.getTimeZone(zone);
            if (tz.getOffset(when) == offset && tz.inDaylightTime(d) == dst) {
                return tz;
            }
        }
        return null;
    }

    private void queueNextSignalStrengthPoll() {
        if (!this.mDontPollSignalStrength) {
            Message msg = obtainMessage();
            msg.what = 10;
            sendMessageDelayed(msg, 20000);
        }
    }

    protected int radioTechnologyToDataServiceState(int code) {
        switch (code) {
            case 0:
            case 1:
            case 2:
            case 3:
            case 4:
            case 5:
                return 1;
            case 6:
            case 7:
            case 8:
            case 12:
            case 13:
                return 0;
            default:
                loge("radioTechnologyToDataServiceState: Wrong radioTechnology code.");
                return 1;
        }
    }

    protected int regCodeToServiceState(int code) {
        switch (code) {
            case 0:
            case 2:
            case 3:
            case 4:
                return 1;
            case 1:
                return 0;
            case 5:
                return 0;
            default:
                loge("regCodeToServiceState: unexpected service state " + code);
                return 1;
        }
    }

    public int getCurrentDataConnectionState() {
        return this.mSS.getDataRegState();
    }

    protected boolean regCodeIsRoaming(int code) {
        return 5 == code;
    }

    private boolean isRoamIndForHomeSystem(String roamInd) {
        String[] homeRoamIndicators = this.mPhone.getContext().getResources().getStringArray(17236023);
        if (homeRoamIndicators == null) {
            return false;
        }
        for (String homeRoamInd : homeRoamIndicators) {
            if (homeRoamInd.equals(roamInd)) {
                return true;
            }
        }
        return false;
    }

    private boolean isRoamingBetweenOperators(boolean cdmaRoaming, ServiceState s) {
        String spn = getSystemProperty("gsm.sim.operator.alpha", "empty");
        String onsl = s.getOperatorAlphaLong();
        String onss = s.getOperatorAlphaShort();
        boolean equalsOnsl;
        if (onsl == null || !spn.equals(onsl)) {
            equalsOnsl = false;
        } else {
            equalsOnsl = true;
        }
        boolean equalsOnss;
        if (onss == null || !spn.equals(onss)) {
            equalsOnss = false;
        } else {
            equalsOnss = true;
        }
        if (!cdmaRoaming || equalsOnsl || equalsOnss) {
            return false;
        }
        return true;
    }

    private boolean isRoamingBetweenOperators(boolean cdmaRoaming, int RoamingIndicator) {
        if (!cdmaRoaming || RoamingIndicator == 1 || RoamingIndicator == 3) {
            return false;
        }
        return true;
    }

    /* JADX WARNING: inconsistent code. */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    private void setTimeFromNITZString(java.lang.String r39, long r40) {
        /*
        r38 = this;
        r26 = android.os.SystemClock.elapsedRealtime();
        r34 = new java.lang.StringBuilder;
        r34.<init>();
        r35 = "NITZ: ";
        r34 = r34.append(r35);
        r0 = r34;
        r1 = r39;
        r34 = r0.append(r1);
        r35 = ",";
        r34 = r34.append(r35);
        r0 = r34;
        r1 = r40;
        r34 = r0.append(r1);
        r35 = " start=";
        r34 = r34.append(r35);
        r0 = r34;
        r1 = r26;
        r34 = r0.append(r1);
        r35 = " delay=";
        r34 = r34.append(r35);
        r36 = r26 - r40;
        r0 = r34;
        r1 = r36;
        r34 = r0.append(r1);
        r34 = r34.toString();
        r0 = r38;
        r1 = r34;
        r0.log(r1);
        r34 = 1050101; // 0x1005f5 float:1.471505E-39 double:5.18819E-318;
        r0 = r34;
        r1 = r39;
        android.util.EventLog.writeEvent(r0, r1);
        r34 = "GMT";
        r34 = java.util.TimeZone.getTimeZone(r34);	 Catch:{ RuntimeException -> 0x0356 }
        r6 = java.util.Calendar.getInstance(r34);	 Catch:{ RuntimeException -> 0x0356 }
        r6.clear();	 Catch:{ RuntimeException -> 0x0356 }
        r34 = 16;
        r35 = 0;
        r0 = r34;
        r1 = r35;
        r6.set(r0, r1);	 Catch:{ RuntimeException -> 0x0356 }
        r34 = "[/:,+-]";
        r0 = r39;
        r1 = r34;
        r21 = r0.split(r1);	 Catch:{ RuntimeException -> 0x0356 }
        r34 = 0;
        r34 = r21[r34];	 Catch:{ RuntimeException -> 0x0356 }
        r34 = java.lang.Integer.parseInt(r34);	 Catch:{ RuntimeException -> 0x0356 }
        r0 = r34;
        r0 = r0 + 2000;
        r32 = r0;
        r34 = 1;
        r0 = r34;
        r1 = r32;
        r6.set(r0, r1);	 Catch:{ RuntimeException -> 0x0356 }
        r34 = 1;
        r34 = r21[r34];	 Catch:{ RuntimeException -> 0x0356 }
        r34 = java.lang.Integer.parseInt(r34);	 Catch:{ RuntimeException -> 0x0356 }
        r20 = r34 + -1;
        r34 = 2;
        r0 = r34;
        r1 = r20;
        r6.set(r0, r1);	 Catch:{ RuntimeException -> 0x0356 }
        r34 = 2;
        r34 = r21[r34];	 Catch:{ RuntimeException -> 0x0356 }
        r7 = java.lang.Integer.parseInt(r34);	 Catch:{ RuntimeException -> 0x0356 }
        r34 = 5;
        r0 = r34;
        r6.set(r0, r7);	 Catch:{ RuntimeException -> 0x0356 }
        r34 = 3;
        r34 = r21[r34];	 Catch:{ RuntimeException -> 0x0356 }
        r14 = java.lang.Integer.parseInt(r34);	 Catch:{ RuntimeException -> 0x0356 }
        r34 = 10;
        r0 = r34;
        r6.set(r0, r14);	 Catch:{ RuntimeException -> 0x0356 }
        r34 = 4;
        r34 = r21[r34];	 Catch:{ RuntimeException -> 0x0356 }
        r17 = java.lang.Integer.parseInt(r34);	 Catch:{ RuntimeException -> 0x0356 }
        r34 = 12;
        r0 = r34;
        r1 = r17;
        r6.set(r0, r1);	 Catch:{ RuntimeException -> 0x0356 }
        r34 = 5;
        r34 = r21[r34];	 Catch:{ RuntimeException -> 0x0356 }
        r24 = java.lang.Integer.parseInt(r34);	 Catch:{ RuntimeException -> 0x0356 }
        r34 = 13;
        r0 = r34;
        r1 = r24;
        r6.set(r0, r1);	 Catch:{ RuntimeException -> 0x0356 }
        r34 = 45;
        r0 = r39;
        r1 = r34;
        r34 = r0.indexOf(r1);	 Catch:{ RuntimeException -> 0x0356 }
        r35 = -1;
        r0 = r34;
        r1 = r35;
        if (r0 != r1) goto L_0x02b4;
    L_0x00f6:
        r25 = 1;
    L_0x00f8:
        r34 = 6;
        r34 = r21[r34];	 Catch:{ RuntimeException -> 0x0356 }
        r30 = java.lang.Integer.parseInt(r34);	 Catch:{ RuntimeException -> 0x0356 }
        r0 = r21;
        r0 = r0.length;	 Catch:{ RuntimeException -> 0x0356 }
        r34 = r0;
        r35 = 8;
        r0 = r34;
        r1 = r35;
        if (r0 < r1) goto L_0x02b8;
    L_0x010d:
        r34 = 7;
        r34 = r21[r34];	 Catch:{ RuntimeException -> 0x0356 }
        r8 = java.lang.Integer.parseInt(r34);	 Catch:{ RuntimeException -> 0x0356 }
    L_0x0115:
        if (r25 == 0) goto L_0x02bb;
    L_0x0117:
        r34 = 1;
    L_0x0119:
        r34 = r34 * r30;
        r34 = r34 * 15;
        r34 = r34 * 60;
        r0 = r34;
        r0 = r0 * 1000;
        r30 = r0;
        r33 = 0;
        r0 = r21;
        r0 = r0.length;	 Catch:{ RuntimeException -> 0x0356 }
        r34 = r0;
        r35 = 9;
        r0 = r34;
        r1 = r35;
        if (r0 < r1) goto L_0x0144;
    L_0x0134:
        r34 = 8;
        r34 = r21[r34];	 Catch:{ RuntimeException -> 0x0356 }
        r35 = 33;
        r36 = 47;
        r31 = r34.replace(r35, r36);	 Catch:{ RuntimeException -> 0x0356 }
        r33 = java.util.TimeZone.getTimeZone(r31);	 Catch:{ RuntimeException -> 0x0356 }
    L_0x0144:
        r34 = "gsm.operator.iso-country";
        r35 = "";
        r0 = r38;
        r1 = r34;
        r2 = r35;
        r16 = r0.getSystemProperty(r1, r2);	 Catch:{ RuntimeException -> 0x0356 }
        r34 = new java.lang.StringBuilder;	 Catch:{ RuntimeException -> 0x0356 }
        r34.<init>();	 Catch:{ RuntimeException -> 0x0356 }
        r35 = "[NITZ] setTimeFromNITZString: iso = ";
        r34 = r34.append(r35);	 Catch:{ RuntimeException -> 0x0356 }
        r0 = r34;
        r1 = r16;
        r34 = r0.append(r1);	 Catch:{ RuntimeException -> 0x0356 }
        r35 = ", dst = ";
        r34 = r34.append(r35);	 Catch:{ RuntimeException -> 0x0356 }
        r0 = r34;
        r34 = r0.append(r8);	 Catch:{ RuntimeException -> 0x0356 }
        r35 = ", ZoneOffset = ";
        r34 = r34.append(r35);	 Catch:{ RuntimeException -> 0x0356 }
        r0 = r34;
        r1 = r30;
        r34 = r0.append(r1);	 Catch:{ RuntimeException -> 0x0356 }
        r35 = ", zone = ";
        r34 = r34.append(r35);	 Catch:{ RuntimeException -> 0x0356 }
        r0 = r34;
        r1 = r33;
        r34 = r0.append(r1);	 Catch:{ RuntimeException -> 0x0356 }
        r35 = ", mGotCountryCode = ";
        r34 = r34.append(r35);	 Catch:{ RuntimeException -> 0x0356 }
        r0 = r38;
        r0 = r0.mGotCountryCode;	 Catch:{ RuntimeException -> 0x0356 }
        r35 = r0;
        r34 = r34.append(r35);	 Catch:{ RuntimeException -> 0x0356 }
        r34 = r34.toString();	 Catch:{ RuntimeException -> 0x0356 }
        r0 = r38;
        r1 = r34;
        r0.log(r1);	 Catch:{ RuntimeException -> 0x0356 }
        if (r33 != 0) goto L_0x01ce;
    L_0x01aa:
        r0 = r38;
        r0 = r0.mGotCountryCode;	 Catch:{ RuntimeException -> 0x0356 }
        r34 = r0;
        if (r34 == 0) goto L_0x01ce;
    L_0x01b2:
        if (r16 == 0) goto L_0x02c3;
    L_0x01b4:
        r34 = r16.length();	 Catch:{ RuntimeException -> 0x0356 }
        if (r34 <= 0) goto L_0x02c3;
    L_0x01ba:
        if (r8 == 0) goto L_0x02bf;
    L_0x01bc:
        r34 = 1;
    L_0x01be:
        r36 = r6.getTimeInMillis();	 Catch:{ RuntimeException -> 0x0356 }
        r0 = r30;
        r1 = r34;
        r2 = r36;
        r4 = r16;
        r33 = android.util.TimeUtils.getTimeZone(r0, r1, r2, r4);	 Catch:{ RuntimeException -> 0x0356 }
    L_0x01ce:
        if (r33 == 0) goto L_0x01ec;
    L_0x01d0:
        r0 = r38;
        r0 = r0.mZoneOffset;	 Catch:{ RuntimeException -> 0x0356 }
        r34 = r0;
        r0 = r34;
        r1 = r30;
        if (r0 != r1) goto L_0x01ec;
    L_0x01dc:
        r0 = r38;
        r0 = r0.mZoneDst;	 Catch:{ RuntimeException -> 0x0356 }
        r35 = r0;
        if (r8 == 0) goto L_0x02dc;
    L_0x01e4:
        r34 = 1;
    L_0x01e6:
        r0 = r35;
        r1 = r34;
        if (r0 == r1) goto L_0x020e;
    L_0x01ec:
        r34 = 1;
        r0 = r34;
        r1 = r38;
        r1.mNeedFixZone = r0;	 Catch:{ RuntimeException -> 0x0356 }
        r0 = r30;
        r1 = r38;
        r1.mZoneOffset = r0;	 Catch:{ RuntimeException -> 0x0356 }
        if (r8 == 0) goto L_0x02e0;
    L_0x01fc:
        r34 = 1;
    L_0x01fe:
        r0 = r34;
        r1 = r38;
        r1.mZoneDst = r0;	 Catch:{ RuntimeException -> 0x0356 }
        r34 = r6.getTimeInMillis();	 Catch:{ RuntimeException -> 0x0356 }
        r0 = r34;
        r2 = r38;
        r2.mZoneTime = r0;	 Catch:{ RuntimeException -> 0x0356 }
    L_0x020e:
        r34 = new java.lang.StringBuilder;	 Catch:{ RuntimeException -> 0x0356 }
        r34.<init>();	 Catch:{ RuntimeException -> 0x0356 }
        r35 = "NITZ: tzOffset=";
        r34 = r34.append(r35);	 Catch:{ RuntimeException -> 0x0356 }
        r0 = r34;
        r1 = r30;
        r34 = r0.append(r1);	 Catch:{ RuntimeException -> 0x0356 }
        r35 = " dst=";
        r34 = r34.append(r35);	 Catch:{ RuntimeException -> 0x0356 }
        r0 = r34;
        r34 = r0.append(r8);	 Catch:{ RuntimeException -> 0x0356 }
        r35 = " zone=";
        r35 = r34.append(r35);	 Catch:{ RuntimeException -> 0x0356 }
        if (r33 == 0) goto L_0x02e4;
    L_0x0235:
        r34 = r33.getID();	 Catch:{ RuntimeException -> 0x0356 }
    L_0x0239:
        r0 = r35;
        r1 = r34;
        r34 = r0.append(r1);	 Catch:{ RuntimeException -> 0x0356 }
        r35 = " iso=";
        r34 = r34.append(r35);	 Catch:{ RuntimeException -> 0x0356 }
        r0 = r34;
        r1 = r16;
        r34 = r0.append(r1);	 Catch:{ RuntimeException -> 0x0356 }
        r35 = " mGotCountryCode=";
        r34 = r34.append(r35);	 Catch:{ RuntimeException -> 0x0356 }
        r0 = r38;
        r0 = r0.mGotCountryCode;	 Catch:{ RuntimeException -> 0x0356 }
        r35 = r0;
        r34 = r34.append(r35);	 Catch:{ RuntimeException -> 0x0356 }
        r35 = " mNeedFixZone=";
        r34 = r34.append(r35);	 Catch:{ RuntimeException -> 0x0356 }
        r0 = r38;
        r0 = r0.mNeedFixZone;	 Catch:{ RuntimeException -> 0x0356 }
        r35 = r0;
        r34 = r34.append(r35);	 Catch:{ RuntimeException -> 0x0356 }
        r34 = r34.toString();	 Catch:{ RuntimeException -> 0x0356 }
        r0 = r38;
        r1 = r34;
        r0.log(r1);	 Catch:{ RuntimeException -> 0x0356 }
        if (r33 == 0) goto L_0x0298;
    L_0x027c:
        r34 = r38.getAutoTimeZone();	 Catch:{ RuntimeException -> 0x0356 }
        if (r34 == 0) goto L_0x028d;
    L_0x0282:
        r34 = r33.getID();	 Catch:{ RuntimeException -> 0x0356 }
        r0 = r38;
        r1 = r34;
        r0.setAndBroadcastNetworkSetTimeZone(r1);	 Catch:{ RuntimeException -> 0x0356 }
    L_0x028d:
        r34 = r33.getID();	 Catch:{ RuntimeException -> 0x0356 }
        r0 = r38;
        r1 = r34;
        r0.saveNitzTimeZone(r1);	 Catch:{ RuntimeException -> 0x0356 }
    L_0x0298:
        r34 = "gsm.ignore-nitz";
        r15 = android.os.SystemProperties.get(r34);	 Catch:{ RuntimeException -> 0x0356 }
        if (r15 == 0) goto L_0x02e8;
    L_0x02a0:
        r34 = "yes";
        r0 = r34;
        r34 = r15.equals(r0);	 Catch:{ RuntimeException -> 0x0356 }
        if (r34 == 0) goto L_0x02e8;
    L_0x02aa:
        r34 = "NITZ: Not setting clock because gsm.ignore-nitz is set";
        r0 = r38;
        r1 = r34;
        r0.log(r1);	 Catch:{ RuntimeException -> 0x0356 }
    L_0x02b3:
        return;
    L_0x02b4:
        r25 = 0;
        goto L_0x00f8;
    L_0x02b8:
        r8 = 0;
        goto L_0x0115;
    L_0x02bb:
        r34 = -1;
        goto L_0x0119;
    L_0x02bf:
        r34 = 0;
        goto L_0x01be;
    L_0x02c3:
        if (r8 == 0) goto L_0x02d9;
    L_0x02c5:
        r34 = 1;
    L_0x02c7:
        r36 = r6.getTimeInMillis();	 Catch:{ RuntimeException -> 0x0356 }
        r0 = r38;
        r1 = r30;
        r2 = r34;
        r3 = r36;
        r33 = r0.getNitzTimeZone(r1, r2, r3);	 Catch:{ RuntimeException -> 0x0356 }
        goto L_0x01ce;
    L_0x02d9:
        r34 = 0;
        goto L_0x02c7;
    L_0x02dc:
        r34 = 0;
        goto L_0x01e6;
    L_0x02e0:
        r34 = 0;
        goto L_0x01fe;
    L_0x02e4:
        r34 = "NULL";
        goto L_0x0239;
    L_0x02e8:
        r0 = r38;
        r0 = r0.mWakeLock;	 Catch:{ all -> 0x057f }
        r34 = r0;
        r34.acquire();	 Catch:{ all -> 0x057f }
        r34 = android.os.SystemClock.elapsedRealtime();	 Catch:{ all -> 0x057f }
        r18 = r34 - r40;
        r34 = 0;
        r34 = (r18 > r34 ? 1 : (r18 == r34 ? 0 : -1));
        if (r34 >= 0) goto L_0x0383;
    L_0x02fd:
        r34 = new java.lang.StringBuilder;	 Catch:{ all -> 0x057f }
        r34.<init>();	 Catch:{ all -> 0x057f }
        r35 = "NITZ: not setting time, clock has rolled backwards since NITZ time was received, ";
        r34 = r34.append(r35);	 Catch:{ all -> 0x057f }
        r0 = r34;
        r1 = r39;
        r34 = r0.append(r1);	 Catch:{ all -> 0x057f }
        r34 = r34.toString();	 Catch:{ all -> 0x057f }
        r0 = r38;
        r1 = r34;
        r0.log(r1);	 Catch:{ all -> 0x057f }
        r10 = android.os.SystemClock.elapsedRealtime();	 Catch:{ RuntimeException -> 0x0356 }
        r34 = new java.lang.StringBuilder;	 Catch:{ RuntimeException -> 0x0356 }
        r34.<init>();	 Catch:{ RuntimeException -> 0x0356 }
        r35 = "NITZ: end=";
        r34 = r34.append(r35);	 Catch:{ RuntimeException -> 0x0356 }
        r0 = r34;
        r34 = r0.append(r10);	 Catch:{ RuntimeException -> 0x0356 }
        r35 = " dur=";
        r34 = r34.append(r35);	 Catch:{ RuntimeException -> 0x0356 }
        r36 = r10 - r26;
        r0 = r34;
        r1 = r36;
        r34 = r0.append(r1);	 Catch:{ RuntimeException -> 0x0356 }
        r34 = r34.toString();	 Catch:{ RuntimeException -> 0x0356 }
        r0 = r38;
        r1 = r34;
        r0.log(r1);	 Catch:{ RuntimeException -> 0x0356 }
        r0 = r38;
        r0 = r0.mWakeLock;	 Catch:{ RuntimeException -> 0x0356 }
        r34 = r0;
        r34.release();	 Catch:{ RuntimeException -> 0x0356 }
        goto L_0x02b3;
    L_0x0356:
        r9 = move-exception;
        r34 = new java.lang.StringBuilder;
        r34.<init>();
        r35 = "NITZ: Parsing NITZ time ";
        r34 = r34.append(r35);
        r0 = r34;
        r1 = r39;
        r34 = r0.append(r1);
        r35 = " ex=";
        r34 = r34.append(r35);
        r0 = r34;
        r34 = r0.append(r9);
        r34 = r34.toString();
        r0 = r38;
        r1 = r34;
        r0.loge(r1);
        goto L_0x02b3;
    L_0x0383:
        r34 = 2147483647; // 0x7fffffff float:NaN double:1.060997895E-314;
        r34 = (r18 > r34 ? 1 : (r18 == r34 ? 0 : -1));
        if (r34 <= 0) goto L_0x03ee;
    L_0x038a:
        r34 = new java.lang.StringBuilder;	 Catch:{ all -> 0x057f }
        r34.<init>();	 Catch:{ all -> 0x057f }
        r35 = "NITZ: not setting time, processing has taken ";
        r34 = r34.append(r35);	 Catch:{ all -> 0x057f }
        r36 = 86400000; // 0x5265c00 float:7.82218E-36 double:4.2687272E-316;
        r36 = r18 / r36;
        r0 = r34;
        r1 = r36;
        r34 = r0.append(r1);	 Catch:{ all -> 0x057f }
        r35 = " days";
        r34 = r34.append(r35);	 Catch:{ all -> 0x057f }
        r34 = r34.toString();	 Catch:{ all -> 0x057f }
        r0 = r38;
        r1 = r34;
        r0.log(r1);	 Catch:{ all -> 0x057f }
        r10 = android.os.SystemClock.elapsedRealtime();	 Catch:{ RuntimeException -> 0x0356 }
        r34 = new java.lang.StringBuilder;	 Catch:{ RuntimeException -> 0x0356 }
        r34.<init>();	 Catch:{ RuntimeException -> 0x0356 }
        r35 = "NITZ: end=";
        r34 = r34.append(r35);	 Catch:{ RuntimeException -> 0x0356 }
        r0 = r34;
        r34 = r0.append(r10);	 Catch:{ RuntimeException -> 0x0356 }
        r35 = " dur=";
        r34 = r34.append(r35);	 Catch:{ RuntimeException -> 0x0356 }
        r36 = r10 - r26;
        r0 = r34;
        r1 = r36;
        r34 = r0.append(r1);	 Catch:{ RuntimeException -> 0x0356 }
        r34 = r34.toString();	 Catch:{ RuntimeException -> 0x0356 }
        r0 = r38;
        r1 = r34;
        r0.log(r1);	 Catch:{ RuntimeException -> 0x0356 }
        r0 = r38;
        r0 = r0.mWakeLock;	 Catch:{ RuntimeException -> 0x0356 }
        r34 = r0;
        r34.release();	 Catch:{ RuntimeException -> 0x0356 }
        goto L_0x02b3;
    L_0x03ee:
        r34 = 14;
        r0 = r18;
        r0 = (int) r0;
        r35 = r0;
        r0 = r34;
        r1 = r35;
        r6.add(r0, r1);	 Catch:{ all -> 0x057f }
        r34 = r38.getAutoTime();	 Catch:{ all -> 0x057f }
        if (r34 == 0) goto L_0x04af;
    L_0x0402:
        r34 = r6.getTimeInMillis();	 Catch:{ all -> 0x057f }
        r36 = java.lang.System.currentTimeMillis();	 Catch:{ all -> 0x057f }
        r12 = r34 - r36;
        r34 = android.os.SystemClock.elapsedRealtime();	 Catch:{ all -> 0x057f }
        r0 = r38;
        r0 = r0.mSavedAtTime;	 Catch:{ all -> 0x057f }
        r36 = r0;
        r28 = r34 - r36;
        r0 = r38;
        r0 = r0.mCr;	 Catch:{ all -> 0x057f }
        r34 = r0;
        r35 = "nitz_update_spacing";
        r0 = r38;
        r0 = r0.mNitzUpdateSpacing;	 Catch:{ all -> 0x057f }
        r36 = r0;
        r23 = android.provider.Settings.Global.getInt(r34, r35, r36);	 Catch:{ all -> 0x057f }
        r0 = r38;
        r0 = r0.mCr;	 Catch:{ all -> 0x057f }
        r34 = r0;
        r35 = "nitz_update_diff";
        r0 = r38;
        r0 = r0.mNitzUpdateDiff;	 Catch:{ all -> 0x057f }
        r36 = r0;
        r22 = android.provider.Settings.Global.getInt(r34, r35, r36);	 Catch:{ all -> 0x057f }
        r0 = r38;
        r0 = r0.mSavedAtTime;	 Catch:{ all -> 0x057f }
        r34 = r0;
        r36 = 0;
        r34 = (r34 > r36 ? 1 : (r34 == r36 ? 0 : -1));
        if (r34 == 0) goto L_0x045e;
    L_0x0448:
        r0 = r23;
        r0 = (long) r0;	 Catch:{ all -> 0x057f }
        r34 = r0;
        r34 = (r28 > r34 ? 1 : (r28 == r34 ? 0 : -1));
        if (r34 > 0) goto L_0x045e;
    L_0x0451:
        r34 = java.lang.Math.abs(r12);	 Catch:{ all -> 0x057f }
        r0 = r22;
        r0 = (long) r0;	 Catch:{ all -> 0x057f }
        r36 = r0;
        r34 = (r34 > r36 ? 1 : (r34 == r36 ? 0 : -1));
        if (r34 <= 0) goto L_0x0514;
    L_0x045e:
        r34 = new java.lang.StringBuilder;	 Catch:{ all -> 0x057f }
        r34.<init>();	 Catch:{ all -> 0x057f }
        r35 = "NITZ: Auto updating time of day to ";
        r34 = r34.append(r35);	 Catch:{ all -> 0x057f }
        r35 = r6.getTime();	 Catch:{ all -> 0x057f }
        r34 = r34.append(r35);	 Catch:{ all -> 0x057f }
        r35 = " NITZ receive delay=";
        r34 = r34.append(r35);	 Catch:{ all -> 0x057f }
        r0 = r34;
        r1 = r18;
        r34 = r0.append(r1);	 Catch:{ all -> 0x057f }
        r35 = "ms gained=";
        r34 = r34.append(r35);	 Catch:{ all -> 0x057f }
        r0 = r34;
        r34 = r0.append(r12);	 Catch:{ all -> 0x057f }
        r35 = "ms from ";
        r34 = r34.append(r35);	 Catch:{ all -> 0x057f }
        r0 = r34;
        r1 = r39;
        r34 = r0.append(r1);	 Catch:{ all -> 0x057f }
        r34 = r34.toString();	 Catch:{ all -> 0x057f }
        r0 = r38;
        r1 = r34;
        r0.log(r1);	 Catch:{ all -> 0x057f }
        r34 = r6.getTimeInMillis();	 Catch:{ all -> 0x057f }
        r0 = r38;
        r1 = r34;
        r0.setAndBroadcastNetworkSetTime(r1);	 Catch:{ all -> 0x057f }
    L_0x04af:
        r34 = "NITZ: update nitz time property";
        r0 = r38;
        r1 = r34;
        r0.log(r1);	 Catch:{ all -> 0x057f }
        r34 = "gsm.nitz.time";
        r36 = r6.getTimeInMillis();	 Catch:{ all -> 0x057f }
        r35 = java.lang.String.valueOf(r36);	 Catch:{ all -> 0x057f }
        android.os.SystemProperties.set(r34, r35);	 Catch:{ all -> 0x057f }
        r34 = r6.getTimeInMillis();	 Catch:{ all -> 0x057f }
        r0 = r34;
        r2 = r38;
        r2.mSavedTime = r0;	 Catch:{ all -> 0x057f }
        r34 = android.os.SystemClock.elapsedRealtime();	 Catch:{ all -> 0x057f }
        r0 = r34;
        r2 = r38;
        r2.mSavedAtTime = r0;	 Catch:{ all -> 0x057f }
        r10 = android.os.SystemClock.elapsedRealtime();	 Catch:{ RuntimeException -> 0x0356 }
        r34 = new java.lang.StringBuilder;	 Catch:{ RuntimeException -> 0x0356 }
        r34.<init>();	 Catch:{ RuntimeException -> 0x0356 }
        r35 = "NITZ: end=";
        r34 = r34.append(r35);	 Catch:{ RuntimeException -> 0x0356 }
        r0 = r34;
        r34 = r0.append(r10);	 Catch:{ RuntimeException -> 0x0356 }
        r35 = " dur=";
        r34 = r34.append(r35);	 Catch:{ RuntimeException -> 0x0356 }
        r36 = r10 - r26;
        r0 = r34;
        r1 = r36;
        r34 = r0.append(r1);	 Catch:{ RuntimeException -> 0x0356 }
        r34 = r34.toString();	 Catch:{ RuntimeException -> 0x0356 }
        r0 = r38;
        r1 = r34;
        r0.log(r1);	 Catch:{ RuntimeException -> 0x0356 }
        r0 = r38;
        r0 = r0.mWakeLock;	 Catch:{ RuntimeException -> 0x0356 }
        r34 = r0;
        r34.release();	 Catch:{ RuntimeException -> 0x0356 }
        goto L_0x02b3;
    L_0x0514:
        r34 = new java.lang.StringBuilder;	 Catch:{ all -> 0x057f }
        r34.<init>();	 Catch:{ all -> 0x057f }
        r35 = "NITZ: ignore, a previous update was ";
        r34 = r34.append(r35);	 Catch:{ all -> 0x057f }
        r0 = r34;
        r1 = r28;
        r34 = r0.append(r1);	 Catch:{ all -> 0x057f }
        r35 = "ms ago and gained=";
        r34 = r34.append(r35);	 Catch:{ all -> 0x057f }
        r0 = r34;
        r34 = r0.append(r12);	 Catch:{ all -> 0x057f }
        r35 = "ms";
        r34 = r34.append(r35);	 Catch:{ all -> 0x057f }
        r34 = r34.toString();	 Catch:{ all -> 0x057f }
        r0 = r38;
        r1 = r34;
        r0.log(r1);	 Catch:{ all -> 0x057f }
        r10 = android.os.SystemClock.elapsedRealtime();	 Catch:{ RuntimeException -> 0x0356 }
        r34 = new java.lang.StringBuilder;	 Catch:{ RuntimeException -> 0x0356 }
        r34.<init>();	 Catch:{ RuntimeException -> 0x0356 }
        r35 = "NITZ: end=";
        r34 = r34.append(r35);	 Catch:{ RuntimeException -> 0x0356 }
        r0 = r34;
        r34 = r0.append(r10);	 Catch:{ RuntimeException -> 0x0356 }
        r35 = " dur=";
        r34 = r34.append(r35);	 Catch:{ RuntimeException -> 0x0356 }
        r36 = r10 - r26;
        r0 = r34;
        r1 = r36;
        r34 = r0.append(r1);	 Catch:{ RuntimeException -> 0x0356 }
        r34 = r34.toString();	 Catch:{ RuntimeException -> 0x0356 }
        r0 = r38;
        r1 = r34;
        r0.log(r1);	 Catch:{ RuntimeException -> 0x0356 }
        r0 = r38;
        r0 = r0.mWakeLock;	 Catch:{ RuntimeException -> 0x0356 }
        r34 = r0;
        r34.release();	 Catch:{ RuntimeException -> 0x0356 }
        goto L_0x02b3;
    L_0x057f:
        r34 = move-exception;
        r10 = android.os.SystemClock.elapsedRealtime();	 Catch:{ RuntimeException -> 0x0356 }
        r35 = new java.lang.StringBuilder;	 Catch:{ RuntimeException -> 0x0356 }
        r35.<init>();	 Catch:{ RuntimeException -> 0x0356 }
        r36 = "NITZ: end=";
        r35 = r35.append(r36);	 Catch:{ RuntimeException -> 0x0356 }
        r0 = r35;
        r35 = r0.append(r10);	 Catch:{ RuntimeException -> 0x0356 }
        r36 = " dur=";
        r35 = r35.append(r36);	 Catch:{ RuntimeException -> 0x0356 }
        r36 = r10 - r26;
        r35 = r35.append(r36);	 Catch:{ RuntimeException -> 0x0356 }
        r35 = r35.toString();	 Catch:{ RuntimeException -> 0x0356 }
        r0 = r38;
        r1 = r35;
        r0.log(r1);	 Catch:{ RuntimeException -> 0x0356 }
        r0 = r38;
        r0 = r0.mWakeLock;	 Catch:{ RuntimeException -> 0x0356 }
        r35 = r0;
        r35.release();	 Catch:{ RuntimeException -> 0x0356 }
        throw r34;	 Catch:{ RuntimeException -> 0x0356 }
        */
        throw new UnsupportedOperationException("Method not decompiled: com.android.internal.telephony.cdma.CdmaServiceStateTracker.setTimeFromNITZString(java.lang.String, long):void");
    }

    private boolean getAutoTime() {
        try {
            return Global.getInt(this.mCr, "auto_time") > 0;
        } catch (SettingNotFoundException e) {
            return true;
        }
    }

    private boolean getAutoTimeZone() {
        try {
            return Global.getInt(this.mCr, "auto_time_zone") > 0;
        } catch (SettingNotFoundException e) {
            return true;
        }
    }

    private void saveNitzTimeZone(String zoneId) {
        this.mSavedTimeZone = zoneId;
    }

    private void setAndBroadcastNetworkSetTimeZone(String zoneId) {
        log("setAndBroadcastNetworkSetTimeZone: setTimeZone=" + zoneId);
        ((AlarmManager) this.mPhone.getContext().getSystemService("alarm")).setTimeZone(zoneId);
        Intent intent = new Intent("android.intent.action.NETWORK_SET_TIMEZONE");
        intent.addFlags(536870912);
        intent.putExtra("time-zone", zoneId);
        this.mPhone.getContext().sendStickyBroadcastAsUser(intent, UserHandle.ALL);
    }

    private void setAndBroadcastNetworkSetTime(long time) {
        log("setAndBroadcastNetworkSetTime: time=" + time + "ms");
        SystemClock.setCurrentTimeMillis(time);
        Intent intent = new Intent("android.intent.action.NETWORK_SET_TIME");
        intent.addFlags(536870912);
        intent.putExtra("time", time);
        this.mPhone.getContext().sendStickyBroadcastAsUser(intent, UserHandle.ALL);
    }

    private void revertToNitzTime() {
        if (Global.getInt(this.mCr, "auto_time", 0) != 0) {
            log("revertToNitzTime: mSavedTime=" + this.mSavedTime + " mSavedAtTime=" + this.mSavedAtTime);
            if (this.mSavedTime != 0 && this.mSavedAtTime != 0) {
                setAndBroadcastNetworkSetTime(this.mSavedTime + (SystemClock.elapsedRealtime() - this.mSavedAtTime));
            }
        }
    }

    private void revertToNitzTimeZone() {
        if (Global.getInt(this.mPhone.getContext().getContentResolver(), "auto_time_zone", 0) != 0) {
            log("revertToNitzTimeZone: tz='" + this.mSavedTimeZone);
            if (this.mSavedTimeZone != null) {
                setAndBroadcastNetworkSetTimeZone(this.mSavedTimeZone);
            }
        }
    }

    protected boolean isSidsAllZeros() {
        if (this.mHomeSystemId != null) {
            for (int i : this.mHomeSystemId) {
                if (i != 0) {
                    return false;
                }
            }
        }
        return true;
    }

    private boolean isHomeSid(int sid) {
        if (this.mHomeSystemId != null) {
            for (int i : this.mHomeSystemId) {
                if (sid == i) {
                    return true;
                }
            }
        }
        return false;
    }

    public boolean needBlockData() {
        boolean result = false;
        if (this.mEmergencyMgr != null && this.mEmergencyMgr.isEmergencyMode()) {
            log("needBlockData(): mScreenState = " + mScreenState + ", needMobileDataBlock = " + this.mEmergencyMgr.needMobileDataBlock());
            if (!mScreenState && this.mEmergencyMgr.needMobileDataBlock()) {
                result = true;
            }
        }
        log("needBlockData(): result = " + result);
        return result;
    }

    public boolean isConcurrentVoiceAndDataAllowed() {
        return false;
    }

    public String getMdnNumber() {
        return this.mMdn;
    }

    public String getCdmaMin() {
        return this.mMin;
    }

    public String getPrlVersion() {
        return this.mPrlVersion;
    }

    String getImsi() {
        String operatorNumeric = getSystemProperty("gsm.sim.operator.numeric", "");
        if (TextUtils.isEmpty(operatorNumeric) || getCdmaMin() == null) {
            return null;
        }
        return operatorNumeric + getCdmaMin();
    }

    public boolean isMinInfoReady() {
        return this.mIsMinInfoReady;
    }

    int getOtasp() {
        if (this.mIsSubscriptionFromRuim && this.mMin == null) {
            return 2;
        }
        int provisioningState;
        if (this.mMin == null || this.mMin.length() < 6) {
            log("getOtasp: bad mMin='" + this.mMin + "'");
            provisioningState = 1;
        } else if (this.mMin.equals(UNACTIVATED_MIN_VALUE) || this.mMin.substring(0, 6).equals(UNACTIVATED_MIN2_VALUE) || SystemProperties.getBoolean("test_cdma_setup", false)) {
            provisioningState = 2;
        } else {
            provisioningState = 3;
        }
        log("getOtasp: state=" + provisioningState);
        return provisioningState;
    }

    protected void hangupAndPowerOff() {
        this.mCi.setRadioPower(false, null);
    }

    protected void parseSidNid(String sidStr, String nidStr) {
        int i;
        if (sidStr != null) {
            String[] sid = sidStr.split(",");
            this.mHomeSystemId = new int[sid.length];
            for (i = 0; i < sid.length; i++) {
                try {
                    this.mHomeSystemId[i] = Integer.parseInt(sid[i]);
                } catch (NumberFormatException ex) {
                    loge("error parsing system id: " + ex);
                }
            }
        }
        log("CDMA_SUBSCRIPTION: SID=" + sidStr);
        if (nidStr != null) {
            String[] nid = nidStr.split(",");
            this.mHomeNetworkId = new int[nid.length];
            for (i = 0; i < nid.length; i++) {
                try {
                    this.mHomeNetworkId[i] = Integer.parseInt(nid[i]);
                } catch (NumberFormatException ex2) {
                    loge("CDMA_SUBSCRIPTION: error parsing network id: " + ex2);
                }
            }
        }
        log("CDMA_SUBSCRIPTION: NID=" + nidStr);
    }

    protected void updateOtaspState() {
        int otaspMode = getOtasp();
        int oldOtaspMode = this.mCurrentOtaspMode;
        this.mCurrentOtaspMode = otaspMode;
        if (this.mCdmaForSubscriptionInfoReadyRegistrants != null) {
            log("CDMA_SUBSCRIPTION: call notifyRegistrants()");
            this.mCdmaForSubscriptionInfoReadyRegistrants.notifyRegistrants();
        }
        if ("USC-CDMA".equals("") && oldOtaspMode == 1 && this.mCurrentOtaspMode == 3) {
            this.mCurrentOtaspMode = 4;
        }
        if (oldOtaspMode != this.mCurrentOtaspMode) {
            log("CDMA_SUBSCRIPTION: call notifyOtaspChanged old otaspMode=" + oldOtaspMode + " new otaspMode=" + this.mCurrentOtaspMode);
            if ("VZW-CDMA".equals("") && PreferenceManager.getDefaultSharedPreferences(this.mPhone.getContext()).getBoolean("setup_wizard_skip", false)) {
                this.mPhone.notifyOtaspChanged(3);
                log("SetupWizardSkip: true, send OTASP_NOT_NEEDED to setup Wizard for skipping.");
                return;
            }
            if (this.mCurrentOtaspMode == 3 || this.mCurrentOtaspMode == 4) {
                SystemProperties.set("ril.otasp_state", "3");
            }
            if (!"VZW-CDMA".equals("")) {
                this.mPhone.notifyOtaspChanged(this.mCurrentOtaspMode);
            } else if (PreferenceManager.getDefaultSharedPreferences(this.mPhone.getContext()).getBoolean("setup_wizard_skip", false)) {
                this.mPhone.notifyOtaspChanged(3);
                log("SetupWizardSkip: true, send OTASP_NOT_NEEDED to setup Wizard for skipping.");
            } else {
                log("SetupWizardSkip: false, send Current OtaspMode to setup Wizard");
                this.mPhone.notifyOtaspChanged(this.mCurrentOtaspMode);
            }
        }
    }

    protected UiccCardApplication getUiccCardApplication() {
        return this.mUiccController.getUiccCardApplication(this.mPhone.getPhoneId(), 2);
    }

    protected void onUpdateIccAvailability() {
        if (this.mUiccController != null) {
            UiccCardApplication newUiccApplication = getUiccCardApplication();
            if (this.mUiccApplcation != newUiccApplication) {
                if (this.mUiccApplcation != null) {
                    log("Removing stale icc objects.");
                    this.mUiccApplcation.unregisterForReady(this);
                    if (this.mIccRecords != null) {
                        this.mIccRecords.unregisterForRecordsLoaded(this);
                    }
                    this.mIccRecords = null;
                    this.mUiccApplcation = null;
                }
                if (newUiccApplication != null) {
                    log("New card found");
                    this.mUiccApplcation = newUiccApplication;
                    this.mIccRecords = this.mUiccApplcation.getIccRecords();
                    if (this.mIsSubscriptionFromRuim) {
                        this.mUiccApplcation.registerForReady(this, 26, null);
                        if (this.mIccRecords != null) {
                            this.mIccRecords.registerForRecordsLoaded(this, 27, null);
                            return;
                        }
                        return;
                    }
                    log("Subscription from NV");
                }
            }
        }
    }

    protected void log(String s) {
        Rlog.d(LOG_TAG, "[CdmaSST] " + s);
    }

    protected void loge(String s) {
        Rlog.e(LOG_TAG, "[CdmaSST] " + s);
    }

    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        pw.println("CdmaServiceStateTracker extends:");
        super.dump(fd, pw, args);
        pw.println(" mPhone=" + this.mPhone);
        pw.println(" mSS=" + this.mSS);
        pw.println(" mNewSS=" + this.mNewSS);
        pw.println(" mCellLoc=" + this.mCellLoc);
        pw.println(" mNewCellLoc=" + this.mNewCellLoc);
        pw.println(" mCurrentOtaspMode=" + this.mCurrentOtaspMode);
        pw.println(" mCdmaRoaming=" + this.mCdmaRoaming);
        pw.println(" mRoamingIndicator=" + this.mRoamingIndicator);
        pw.println(" mIsInPrl=" + this.mIsInPrl);
        pw.println(" mDefaultRoamingIndicator=" + this.mDefaultRoamingIndicator);
        pw.println(" mRegistrationState=" + this.mRegistrationState);
        pw.println(" mNeedFixZone=" + this.mNeedFixZone);
        pw.println(" mZoneOffset=" + this.mZoneOffset);
        pw.println(" mZoneDst=" + this.mZoneDst);
        pw.println(" mZoneTime=" + this.mZoneTime);
        pw.println(" mGotCountryCode=" + this.mGotCountryCode);
        pw.println(" mSavedTimeZone=" + this.mSavedTimeZone);
        pw.println(" mSavedTime=" + this.mSavedTime);
        pw.println(" mSavedAtTime=" + this.mSavedAtTime);
        pw.println(" mWakeLock=" + this.mWakeLock);
        pw.println(" mCurPlmn=" + this.mCurPlmn);
        pw.println(" mMdn=" + this.mMdn);
        pw.println(" mHomeSystemId=" + this.mHomeSystemId);
        pw.println(" mHomeNetworkId=" + this.mHomeNetworkId);
        pw.println(" mMin=" + this.mMin);
        pw.println(" mPrlVersion=" + this.mPrlVersion);
        pw.println(" mIsMinInfoReady=" + this.mIsMinInfoReady);
        pw.println(" mIsEriTextLoaded=" + this.mIsEriTextLoaded);
        pw.println(" mIsSubscriptionFromRuim=" + this.mIsSubscriptionFromRuim);
        pw.println(" mCdmaSSM=" + this.mCdmaSSM);
        pw.println(" mRegistrationDeniedReason=" + this.mRegistrationDeniedReason);
        pw.println(" mCurrentCarrier=" + this.mCurrentCarrier);
    }

    protected void CalibrationTimezoneUsingMcc(String operatorNumeric) {
        boolean mZoneDstRevert = true;
        if (operatorNumeric == null) {
            Rlog.w(LOG_TAG, "operatorNumeric is null");
        } else if (operatorNumeric.length() < 3) {
            Rlog.w(LOG_TAG, "operatorNumeric is Invalid");
        } else {
            String NewMcc = operatorNumeric.substring(0, 3);
            if (!NewMcc.equals(this.CalibrationMcc) && this.mNeedFixZone && !NewMcc.equals(INVALID_MCC) && !NewMcc.equals("111") && !NewMcc.equals("001") && !NewMcc.equals("010") && !NewMcc.equals("002") && !NewMcc.equals("003")) {
                Rlog.w(LOG_TAG, "[NITZ] 1step : we need to calibrate for Mcc : " + this.CalibrationMcc + " --> " + NewMcc);
                this.CalibrationMcc = NewMcc;
                String CalibrationIso = MccTable.countryCodeForMcc(Integer.parseInt(operatorNumeric.substring(0, 3)));
                TimeZone CalibrationTimeZone = TimeUtils.getTimeZone(this.mZoneOffset, this.mZoneDst, this.mZoneTime, CalibrationIso);
                if (CalibrationTimeZone == null) {
                    Rlog.w(LOG_TAG, "[NITZ] 2step. we need to calibrate for Iso : " + CalibrationIso);
                    String CalibrationZoneId = MccTable.defaultTimeZoneForMcc(Integer.parseInt(operatorNumeric.substring(0, 3)));
                    log("[NITZ] CalibrationZoneId : " + CalibrationZoneId);
                    if (CalibrationZoneId != null) {
                        CalibrationTimeZone = TimeZone.getTimeZone(CalibrationZoneId);
                    }
                    if (CalibrationTimeZone != null && CalibrationIso.equals("us") && (this.mZoneOffset == 32400000 || this.mZoneOffset == 36000000)) {
                        CalibrationIso = "kr";
                        this.mZoneOffset = 32400000;
                        this.mZoneDst = false;
                        Rlog.w(LOG_TAG, "[NITZ] 2-1step. testbed's exceptional case : " + CalibrationIso);
                        CalibrationTimeZone = TimeUtils.getTimeZone(this.mZoneOffset, this.mZoneDst, this.mZoneTime, CalibrationIso);
                    }
                    if (!(CalibrationTimeZone == null || !CalibrationIso.equals("us") || this.mZoneOffset == 0)) {
                        if (this.mZoneDst) {
                            mZoneDstRevert = false;
                        }
                        Rlog.w(LOG_TAG, "[NITZ] 2-2step. DST's exceptional case(mZoneDstRevert) : " + mZoneDstRevert);
                        TimeZone CalibrationTimeZoneRevert = TimeUtils.getTimeZone(this.mZoneOffset, mZoneDstRevert, this.mZoneTime, CalibrationIso);
                        if (CalibrationTimeZoneRevert != null) {
                            Rlog.w(LOG_TAG, "[NITZ] CalibrationTimeZoneRevert.getID() : " + CalibrationTimeZoneRevert.getID());
                            if (!CalibrationTimeZone.getID().equals(CalibrationTimeZoneRevert.getID())) {
                                CalibrationTimeZone = CalibrationTimeZoneRevert;
                            }
                        }
                    }
                    if (CalibrationTimeZone != null && CalibrationIso.equals("us") && CalibrationTimeZone.getID().equals("America/New_York") && this.mZoneOffset == -14400000 && !this.mZoneDst) {
                        Rlog.w(LOG_TAG, "[NITZ] 2-3 step. Wrong MCC/MNC in Puerto Rico");
                        CalibrationTimeZone = TimeUtils.getTimeZone(this.mZoneOffset, this.mZoneDst, this.mZoneTime, "pr");
                    }
                    if (CalibrationTimeZone == null) {
                        Rlog.w(LOG_TAG, "[NITZ] 3step.  next time... ");
                        return;
                    }
                }
                this.mNeedFixZone = false;
                Rlog.w(LOG_TAG, "[NITZ] CalibrationTimeZone.getID() : " + CalibrationTimeZone.getID());
                if (getAutoTimeZone()) {
                    setAndBroadcastNetworkSetTimeZone(CalibrationTimeZone.getID());
                }
                saveNitzTimeZone(CalibrationTimeZone.getID());
            } else if (NewMcc.equals("001") || NewMcc.equals("010") || NewMcc.equals("002") || NewMcc.equals("003")) {
                this.mNeedFixZone = false;
                Rlog.w(LOG_TAG, "[NITZ] Test mcc using embedded board : " + NewMcc);
            }
        }
    }

    public void setImsRegistrationState(boolean registered) {
        log("ImsRegistrationState - registered : " + registered);
        if (this.mImsRegistrationOnOff && !registered && this.mAlarmSwitch) {
            this.mImsRegistrationOnOff = registered;
            ((AlarmManager) this.mPhone.getContext().getSystemService("alarm")).cancel(this.mRadioOffIntent);
            this.mAlarmSwitch = false;
            sendMessage(obtainMessage(45));
            return;
        }
        this.mImsRegistrationOnOff = registered;
    }

    protected boolean hangupBeforeDeactivePDP() {
        this.mPendingRadioPowerOffAfterHangup = 0;
        if (this.mPhone.isInCall()) {
            this.mPhone.mCT.mRingingCall.hangupIfAlive();
            this.mPhone.mCT.mBackgroundCall.hangupIfAlive();
            this.mPhone.mCT.mForegroundCall.hangupIfAlive();
            this.mPendingRadioPowerOffAfterHangup++;
        }
        log("hangupBeforeDeactivePDP() : post pending value=" + this.mPendingRadioPowerOffAfterHangup);
        if (this.mPendingRadioPowerOffAfterHangup == 0) {
            return false;
        }
        sendCallHangupDelayed(1000, this.mPendingRadioPowerOffAfterHangup);
        return true;
    }

    private void onSprintRoamingIndicator() {
        boolean domesticDataGuardEnabled;
        if (Secure.getInt(this.mCr, "roam_guard_data_domestic", 1) == 1) {
            domesticDataGuardEnabled = true;
        } else {
            domesticDataGuardEnabled = false;
        }
        boolean internationalDataGuardEnabled;
        if (Secure.getInt(this.mCr, "roam_guard_data_international", 1) == 1) {
            internationalDataGuardEnabled = true;
        } else {
            internationalDataGuardEnabled = false;
        }
        boolean IsDomesticRoaming = IsDomesticRoaming();
        boolean IsInternationalRoaming = IsInternationalRoaming();
        if (((domesticDataGuardEnabled && IsDomesticRoaming) || (internationalDataGuardEnabled && IsInternationalRoaming)) && ((this.mOldIsDomesticRoaming != IsDomesticRoaming || this.mOldIsInternationalRoaming != IsInternationalRoaming) && this.mPhone.getState() == State.IDLE)) {
            showDataGuard();
            this.mRoamingOnRegistrants.notifyRegistrants();
        } else if (this.mRoamingIndicator == 1) {
            this.mPhone.getContext().sendStickyBroadcastAsUser(new Intent("android.intent.action.ACTION_CLOSE_DIALOG_DATA_ROAMING_GUARD"), UserHandle.ALL);
        } else {
            this.mRoamingOnRegistrants.notifyRegistrants();
        }
        if (!(this.mOldIsDomesticRoaming == IsDomesticRoaming && this.mOldIsInternationalRoaming == IsInternationalRoaming)) {
            String currentRoam;
            Intent intent = new Intent("android.intent.action.ACTION_ROAMING_STATUS_CHANGED");
            if (IsDomesticRoaming) {
                currentRoam = "domesticRoam";
            } else if (IsInternationalRoaming) {
                currentRoam = "internationalRoam";
            } else {
                currentRoam = "home";
            }
            intent.putExtra(TextBasedSmsColumns.STATUS, currentRoam);
            Rlog.i(LOG_TAG, "ACTION_ROAMING_STATUS_CHANGED : " + currentRoam);
            this.mPhone.getContext().sendBroadcast(intent);
        }
        this.mOldIsDomesticRoaming = IsDomesticRoaming;
        this.mOldIsInternationalRoaming = IsInternationalRoaming;
    }

    private void showDataGuard() {
        Secure.putInt(this.mCr, "data_roaming", 0);
        this.mPhone.getContext().sendStickyBroadcastAsUser(new Intent("android.intent.action.ACTION_SHOW_DIALOG_DATA_ROAMING_GUARD"), UserHandle.ALL);
    }

    private boolean IsInternationalRoaming() {
        return this.mPhone.mEriManager.IsInternationalRoaming(this.mRoamingIndicator, this.mDefaultRoamingIndicator);
    }

    private boolean IsDomesticRoaming() {
        return this.mPhone.mEriManager.IsDomesticRoaming(this.mRoamingIndicator, this.mDefaultRoamingIndicator);
    }

    private void sendCallHangupDelayed(long delayMillis, int pendingRadioOff) {
        log("sendCallHangupDelayed");
        Message msg = Message.obtain(this);
        msg.what = 100;
        msg.arg1 = pendingRadioOff;
        if (sendMessageDelayed(msg, delayMillis)) {
            log("Wait upto %d s for hanging up voice call." + delayMillis);
        } else {
            log("sendCallHangupDelayed() Cannot send delayed Msg");
        }
    }

    protected void displayTimeDisplayScheme(String operatorNumeric, int serviceState) {
        Rlog.d(LOG_TAG, "displayTimeDisplayScheme()");
        if (!TextUtils.isEmpty(operatorNumeric)) {
            int mcc = Integer.parseInt(operatorNumeric.substring(0, 3));
            int previousMcc = Integer.parseInt(SystemProperties.get("gsm.ctc.timedispschmmcc", "0"));
            if (serviceState == 0) {
                if ("READY".equals(getSystemProperty("gsm.sim.state", ""))) {
                    if (!(mcc == 460 || mcc == 455 || mcc <= 0 || previousMcc == mcc)) {
                        this.mPhone.getContext().sendStickyBroadcast(new Intent("android.intent.action.ACTION_TIME_DISP_SCHM_LAUNCH"));
                        Rlog.d(LOG_TAG, "lunch displayTimeDisplayScheme");
                    }
                    if (mcc > 0 && previousMcc != mcc) {
                        SystemProperties.set("gsm.ctc.timedispschmmcc", Integer.toString(mcc));
                    }
                }
            }
        }
    }
}
