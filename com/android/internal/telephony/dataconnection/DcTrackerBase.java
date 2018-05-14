package com.android.internal.telephony.dataconnection;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.Resources.NotFoundException;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.ConnectivityManager;
import android.net.LinkProperties;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.net.TrafficStats;
import android.net.Uri;
import android.os.AsyncResult;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.preference.PreferenceManager;
import android.provider.Settings.Global;
import android.provider.Settings.SettingNotFoundException;
import android.provider.Settings.System;
import android.provider.Telephony.Carriers;
import android.telephony.Rlog;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.EventLog;
import android.widget.Toast;
import com.android.internal.telephony.DctConstants.Activity;
import com.android.internal.telephony.DctConstants.State;
import com.android.internal.telephony.EventLogTags;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneBase;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.PhoneConstants.DataState;
import com.android.internal.telephony.ServiceStateTracker;
import com.android.internal.telephony.SubscriptionController;
import com.android.internal.telephony.gsm.NetAuthManager;
import com.android.internal.telephony.uicc.IccRecords;
import com.android.internal.telephony.uicc.UiccController;
import com.android.internal.util.ArrayUtils;
import com.android.internal.util.AsyncChannel;
import com.sec.android.app.CscFeature;
import java.io.BufferedReader;
import java.io.FileDescriptor;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public abstract class DcTrackerBase extends Handler {
    protected static final int APN_DELAY_DEFAULT_MILLIS = 5000;
    protected static final int APN_FAIL_FAST_DELAY_DEFAULT_MILLIS = 3000;
    protected static final String APN_RESTORE_DELAY_PROP_NAME = "android.telephony.apn-restore";
    protected static final int DATA_CONNECTION_ACTIVE_PH_LINK_DOWN = 1;
    protected static final int DATA_CONNECTION_ACTIVE_PH_LINK_INACTIVE = 0;
    protected static final int DATA_CONNECTION_ACTIVE_PH_LINK_UP = 2;
    protected static final int DATA_STALL_ALARM_AGGRESSIVE_DELAY_IN_MS_DEFAULT = 60000;
    protected static final int DATA_STALL_ALARM_NON_AGGRESSIVE_DELAY_IN_MS_DEFAULT = 360000;
    protected static final String DATA_STALL_ALARM_TAG_EXTRA = "data.stall.alram.tag";
    protected static final boolean DATA_STALL_NOT_SUSPECTED = false;
    protected static final int DATA_STALL_NO_RECV_POLL_LIMIT = 1;
    protected static final boolean DATA_STALL_SUSPECTED = true;
    protected static final boolean DBG = true;
    protected static final String DEBUG_PROV_APN_ALARM = "persist.debug.prov_apn_alarm";
    protected static final String DEFALUT_DATA_ON_BOOT_PROP = "net.def_data_on_boot";
    protected static final String DEFAULT_DATA_RETRY_CONFIG = "default_randomization=2000,5000,10000,20000,40000,80000:5000,160000:5000,320000:5000,640000:5000,1280000:5000,1800000:5000";
    protected static final int DEFAULT_MAX_PDP_RESET_FAIL = 3;
    private static final int DEFAULT_MDC_INITIAL_RETRY = 1;
    protected static final String INTENT_DATA_ENABLE_DEFAULT_APN = "com.samsung.intent.action.DATA_ENABLE_DEFAULT_APN";
    protected static final String INTENT_DATA_STALL_ALARM = "com.android.internal.telephony.data-stall";
    protected static final String INTENT_DUN_INITIALIZE_STATE = "com.android.internal.telephony.cdma-initializeState";
    protected static final String INTENT_GET_DATA_ENABLED = "com.android.internal.telephony.GET_DATA_ENABLED";
    protected static final String INTENT_GET_DATA_PREFERRED = "com.android.internal.telephony.GET_DATA_PREFERRED";
    protected static final String INTENT_IMS_RECOVERY_REQUEST = "com.samsung.intent.action.IMS_RECOVERY_REQUEST";
    protected static final String INTENT_PREFERED_DATA_1 = "com.android.internal.telephony.preferred_data_1";
    protected static final String INTENT_PREFERED_DATA_2 = "com.android.internal.telephony.preferred_data_2";
    protected static final String INTENT_PROVISIONING_APN_ALARM = "com.android.internal.telephony.provisioning_apn_alarm";
    protected static final String INTENT_RECONNECT_ALARM = "com.android.internal.telephony.data-reconnect";
    protected static final String INTENT_RECONNECT_ALARM_EXTRA_REASON = "reconnect_alarm_extra_reason";
    protected static final String INTENT_RECONNECT_ALARM_EXTRA_TYPE = "reconnect_alarm_extra_type";
    protected static final String INTENT_RESTART_TRYSETUP_ALARM = "com.android.internal.telephony.data-restart-trysetup";
    protected static final String INTENT_RESTART_TRYSETUP_ALARM_EXTRA_TYPE = "restart_trysetup_alarm_extra_type";
    protected static final String INTENT_SET_DATA_ENABLED = "com.android.internal.telephony.SET_DATA_ENABLED";
    protected static final String INTENT_SET_DATA_PREFERRED = "com.android.internal.telephony.SET_DATA_PREFERRED";
    private static final String LOG_TAG = "DctBase";
    protected static final int NO_RECV_POLL_LIMIT = 24;
    protected static final String NULL_IP = "0.0.0.0";
    protected static final int NUMBER_SENT_PACKETS_OF_HANG = 10;
    protected static final String PDP_RESET_TEST = "android.intent.action.PDP_RESET_TEST";
    protected static final int POLL_LONGEST_RTT = 120000;
    protected static final int POLL_NETSTAT_MILLIS = 1000;
    protected static final int POLL_NETSTAT_SCREEN_OFF_MILLIS = 600000;
    protected static final int POLL_NETSTAT_SLOW_MILLIS = 5000;
    private static final Uri PREFERAPN_URI_USING_SUBID = Uri.parse("content://telephony/carriers/preferapn/subId/");
    protected static final int PROVISIONING_APN_ALARM_DELAY_IN_MS_DEFAULT = 900000;
    protected static final String PROVISIONING_APN_ALARM_TAG_EXTRA = "provisioning.apn.alarm.tag";
    protected static final boolean RADIO_TESTS = false;
    protected static final int RESTORE_DEFAULT_APN_DELAY = 60000;
    protected static final String SECONDARY_DATA_RETRY_CONFIG = "max_retries=3, 5000, 5000, 5000";
    protected static final boolean VDBG = false;
    protected static final boolean VDBG_STALL = true;
    static boolean mIsCleanupRequired = false;
    protected static int sEnableFailFastRefCounter = 0;
    protected int MAX_RETRY_FOR_PERMANENT_FAILURE = 2;
    protected String RADIO_RESET_PROPERTY = "gsm.radioreset";
    private int dataStallAlarmCount = 0;
    protected Activity dunActivity = Activity.NONE;
    protected int dunNetworkType = 0;
    protected int dunRxBarLevel;
    protected int dunState;
    protected int dunTxBarLevel;
    private int imsRecoveryStep = 0;
    protected ApnSetting mActiveApn;
    protected Activity mActivity = Activity.NONE;
    AlarmManager mAlarmManager;
    protected ArrayList<ApnSetting> mAllApnSettings = null;
    protected final ConcurrentHashMap<String, ApnContext> mApnContexts = new ConcurrentHashMap();
    protected int mApnDisabledOnSimSlot = -1;
    protected HashMap<String, Integer> mApnToDataConnectionId = new HashMap();
    protected ArrayList<String> mApnTypesAllowedOnDataDisabled = null;
    protected boolean mAutoAttachOnCreation = false;
    protected boolean mAutoAttachOnCreationConfig = false;
    protected boolean mCamaMaintReq = false;
    protected int mCidActive;
    ConnectivityManager mCm;
    protected HashMap<String, Set<String>> mConditions4AlwaysOnApns = null;
    protected boolean mDataAuthFail = false;
    protected HashMap<Integer, DcAsyncChannel> mDataConnectionAcHashMap = new HashMap();
    protected Handler mDataConnectionTracker = null;
    protected HashMap<Integer, DataConnection> mDataConnections = new HashMap();
    private boolean[] mDataEnabled = new boolean[19];
    protected Object mDataEnabledLock = new Object();
    private final DataRoamingSettingObserver mDataRoamingSettingObserver;
    protected PendingIntent mDataStallAlarmIntent = null;
    protected int mDataStallAlarmTag = ((int) SystemClock.elapsedRealtime());
    protected volatile boolean mDataStallDetectionEnabled = true;
    protected TxRxSum mDataStallTxRxSum = new TxRxSum(0, 0);
    protected DcTesterFailBringUpAll mDcTesterFailBringUpAll;
    protected DcController mDcc;
    protected ApnSetting mEmergencyApn = null;
    private int mEnabledCount = 0;
    protected boolean mExcludeImsPacketCount = false;
    protected volatile boolean mFailFast = false;
    protected AtomicReference<IccRecords> mIccRecords = new AtomicReference();
    protected String mImsMobileInterface = null;
    private int[] mImsRecoveryPolicy;
    protected boolean mInVoiceCall = false;
    protected BroadcastReceiver mIntentReceiver = new C00971();
    protected boolean mInternalDataEnabled = true;
    protected boolean mIsDisposed = false;
    protected boolean mIsFotaMode = false;
    protected boolean mIsHoleOfVoiceCall = false;
    private boolean mIsMobileDataOffCalled = false;
    protected boolean mIsProvisioning = false;
    protected boolean mIsPsRestricted = false;
    protected boolean mIsScreenOn = true;
    protected boolean mIsWifiConnected = false;
    private LteRoamingEnableObserver mLteRoamingEnableObserver;
    private final MobileDataSettingObserver mMobileDataSettingObserver;
    protected boolean mNeedDataSelctionPopup = true;
    protected boolean mNeedRoamingDataSelctionPopup = true;
    public NetAuthManager mNetAuthMgr;
    protected boolean mNetStatPollEnabled = false;
    protected int mNetStatPollPeriod;
    protected int mNoRecvPollCount = 0;
    protected ArrayList<String> mPermanentFailedOperatorNumeric = new ArrayList();
    protected PhoneBase mPhone;
    protected Phone[] mPhones;
    private Runnable mPollNetStat = new C00982();
    protected ApnSetting mPreferredApn = null;
    protected final ArrayList<ApnContext> mPrioritySortedApnContexts = new ArrayList();
    protected PendingIntent mProvisioningApnAlarmIntent = null;
    protected int mProvisioningApnAlarmTag = ((int) SystemClock.elapsedRealtime());
    protected String mProvisioningUrl = null;
    protected PendingIntent mReconnectIntent = null;
    protected AsyncChannel mReplyAc = new AsyncChannel();
    protected String mRequestedApnType = "default";
    protected ContentResolver mResolver;
    protected long mRxPkts;
    protected long mSentSinceLastRecv;
    protected State mState = State.IDLE;
    protected long mTxPkts;
    protected UiccController mUiccController;
    protected AtomicInteger mUniqueIdGenerator = new AtomicInteger(0);
    protected boolean mUserDataEnabled = true;
    private final VoLteSettingObserver mVoLteSettingObserver;
    protected boolean mWaitingForUserSelection = false;
    protected boolean sPolicyDataEnabled = true;

    class C00971 extends BroadcastReceiver {
        C00971() {
        }

        public void onReceive(Context context, Intent intent) {
            boolean isPreviouslyWifiConnected = DcTrackerBase.this.mIsWifiConnected;
            String action = intent.getAction();
            DcTrackerBase.this.log("onReceive: action=" + action);
            if (action.equals("android.intent.action.SCREEN_ON")) {
                DcTrackerBase.this.mIsScreenOn = true;
                DcTrackerBase.this.stopNetStatPoll();
                DcTrackerBase.this.startNetStatPoll();
                DcTrackerBase.this.restartDataStallAlarm();
                DcTrackerBase.this.onScreenStateChanged(DcTrackerBase.this.mIsScreenOn);
            } else if (action.equals("android.intent.action.SCREEN_OFF")) {
                DcTrackerBase.this.mIsScreenOn = false;
                DcTrackerBase.this.stopNetStatPoll();
                DcTrackerBase.this.startNetStatPoll();
                DcTrackerBase.this.restartDataStallAlarm();
            } else if (action.equals(DcTrackerBase.PDP_RESET_TEST)) {
                int doRecoveryAction = intent.getIntExtra("actionNum", 1);
                DcTrackerBase.this.log("PDP Rest Test: doRecoveryAction= " + doRecoveryAction);
                if (doRecoveryAction == 0) {
                    DcTrackerBase.this.onCleanUpAllConnections(Phone.REASON_PDP_RESET);
                } else if (doRecoveryAction == 1) {
                    DcTrackerBase.this.mPhone.getServiceStateTracker().reRegisterNetwork(null);
                }
            } else if (action.startsWith(DcTrackerBase.INTENT_RECONNECT_ALARM)) {
                DcTrackerBase.this.log("Reconnect alarm. Previous state was " + DcTrackerBase.this.mState);
                DcTrackerBase.this.onActionIntentReconnectAlarm(intent);
            } else if (action.startsWith(DcTrackerBase.INTENT_RESTART_TRYSETUP_ALARM)) {
                DcTrackerBase.this.log("Restart trySetup alarm");
                DcTrackerBase.this.onActionIntentRestartTrySetupAlarm(intent);
            } else if (action.equals(DcTrackerBase.INTENT_DATA_STALL_ALARM)) {
                DcTrackerBase.this.onActionIntentDataStallAlarm(intent);
            } else if (action.equals(DcTrackerBase.INTENT_PROVISIONING_APN_ALARM)) {
                DcTrackerBase.this.onActionIntentProvisioningApnAlarm(intent);
            } else if (action.equals(DcTrackerBase.INTENT_PREFERED_DATA_1)) {
                subId = SubscriptionController.getInstance().getSubId(0);
                DcTrackerBase.this.log("INTENT_PREFERED_DATA_1, mPhone.getSubId()=" + DcTrackerBase.this.mPhone.getSubId() + ", subId[0]=" + subId[0]);
                SubscriptionController.getInstance().setDefaultDataSubId(subId[0]);
            } else if (action.equals(DcTrackerBase.INTENT_PREFERED_DATA_2)) {
                subId = SubscriptionController.getInstance().getSubId(1);
                DcTrackerBase.this.log("INTENT_PREFERED_DATA_2, mPhone.getSubId()=" + DcTrackerBase.this.mPhone.getSubId() + ", subId[0]=" + subId[0]);
                SubscriptionController.getInstance().setDefaultDataSubId(subId[0]);
            } else if (action.equals(DcTrackerBase.INTENT_GET_DATA_ENABLED)) {
                tm = (TelephonyManager) DcTrackerBase.this.mPhone.getContext().getSystemService("phone");
                DcTrackerBase.this.log("INTENT_GET_DATA_ENABLED, getDataEnabled()=" + tm.getDataEnabled() + ", getDefaultDataSubId()=" + SubscriptionController.getInstance().getDefaultDataSubId() + ", preferred_data=" + SubscriptionController.getInstance().getPhoneId(SubscriptionController.getInstance().getDefaultDataSubId()));
                Toast.makeText(DcTrackerBase.this.mPhone.getContext(), "getDataEnabled()=" + tm.getDataEnabled() + ", getDefaultDataSubId()=" + SubscriptionController.getInstance().getDefaultDataSubId() + ", preferred_data=" + SubscriptionController.getInstance().getPhoneId(SubscriptionController.getInstance().getDefaultDataSubId()), 1).show();
            } else if (action.equals(DcTrackerBase.INTENT_SET_DATA_ENABLED)) {
                boolean enable = intent.getBooleanExtra("enable", false);
                tm = (TelephonyManager) DcTrackerBase.this.mPhone.getContext().getSystemService("phone");
                DcTrackerBase.this.log("INTENT_SET_DATA_ENABLED, enable=" + enable);
                tm.setDataEnabled(enable);
            } else if (action.equals(DcTrackerBase.INTENT_GET_DATA_PREFERRED)) {
                tm = (TelephonyManager) DcTrackerBase.this.mPhone.getContext().getSystemService("phone");
                DcTrackerBase.this.log("INTENT_GET_DATA_PREFERRED, getSelectedApn()=" + tm.getSelectedApn() + ", getDefaultDataSubId()=" + SubscriptionController.getInstance().getDefaultDataSubId() + ", preferred_data=" + SubscriptionController.getInstance().getPhoneId(SubscriptionController.getInstance().getDefaultDataSubId()));
                Toast.makeText(DcTrackerBase.this.mPhone.getContext(), "getSelectedApn()=" + tm.getSelectedApn() + ", getDefaultDataSubId()=" + SubscriptionController.getInstance().getDefaultDataSubId() + ", preferred_data=" + SubscriptionController.getInstance().getPhoneId(SubscriptionController.getInstance().getDefaultDataSubId()), 1).show();
            } else if (action.equals(DcTrackerBase.INTENT_SET_DATA_PREFERRED)) {
                tm = (TelephonyManager) DcTrackerBase.this.mPhone.getContext().getSystemService("phone");
                DcTrackerBase.this.log("INTENT_SET_DATA_PREFERRED");
                tm.setSelectedApn();
            } else if (action.equals("android.net.wifi.STATE_CHANGE")) {
                NetworkInfo networkInfo = (NetworkInfo) intent.getParcelableExtra("networkInfo");
                DcTrackerBase dcTrackerBase = DcTrackerBase.this;
                boolean z = networkInfo != null && networkInfo.isConnected();
                dcTrackerBase.mIsWifiConnected = z;
                DcTrackerBase.this.log("NETWORK_STATE_CHANGED_ACTION: mIsWifiConnected=" + DcTrackerBase.this.mIsWifiConnected);
            } else if (action.equals("android.net.wifi.WIFI_STATE_CHANGED")) {
                enabled = intent.getIntExtra("wifi_state", 4) == 3;
                if (!enabled) {
                    DcTrackerBase.this.mIsWifiConnected = false;
                }
                DcTrackerBase.this.log("WIFI_STATE_CHANGED_ACTION: enabled=" + enabled + " mIsWifiConnected=" + DcTrackerBase.this.mIsWifiConnected);
            } else if (action.equals("android.intent.action.SET_POLICY_DATA_ENABLE")) {
                enabled = intent.getBooleanExtra("enabled", true);
                Message msg = DcTrackerBase.this.obtainMessage(270368);
                msg.arg1 = enabled ? 1 : 0;
                DcTrackerBase.this.sendMessage(msg);
            }
            if (CscFeature.getInstance().getEnableStatus("CscFeature_RIL_PromptToDataRoam") && action.equals("android.intent.action.ACTION_DATA_SELECTION_POPUP_PRESSED")) {
                if (intent.getBooleanExtra("Roaming", false)) {
                    DcTrackerBase.this.mNeedRoamingDataSelctionPopup = false;
                } else {
                    DcTrackerBase.this.mNeedDataSelctionPopup = false;
                }
                DcTrackerBase.this.mWaitingForUserSelection = false;
                if (intent.getBooleanExtra("DataEnable", false)) {
                    DcTrackerBase.this.log("DataEnable = " + intent.getBooleanExtra("DataEnable", false));
                    DcTrackerBase.this.onTrySetupData("nothing");
                }
            }
            if ("LGT".equals("") && action.equals("android.intent.action.AIRPLANE_MODE") && !DcTrackerBase.this.getAirplaneMode() && DcTrackerBase.this.getDataOnRoamingEnabled() && !DcTrackerBase.this.mWaitingForUserSelection) {
                DcTrackerBase.this.log("DATA : air plane mode is disabled ");
                DcTrackerBase.this.mNeedRoamingDataSelctionPopup = true;
            }
            if (DcTrackerBase.this.isDomesticModel() && action.equals("android.intent.action.SET_DEPENDENCY_MET")) {
                boolean met = intent.getBooleanExtra("Met", true);
                DcTrackerBase.this.log("SET_DEPENDENCY_MET: met = " + met);
                DcTrackerBase.this.setDependencyMet(met);
            }
        }
    }

    class C00982 implements Runnable {
        C00982() {
        }

        public void run() {
            DcTrackerBase.this.updateDataActivity();
            if (DcTrackerBase.this.mIsScreenOn) {
                DcTrackerBase.this.mNetStatPollPeriod = Global.getInt(DcTrackerBase.this.mResolver, "pdp_watchdog_poll_interval_ms", 1000);
            } else {
                DcTrackerBase.this.mNetStatPollPeriod = Global.getInt(DcTrackerBase.this.mResolver, "pdp_watchdog_long_poll_interval_ms", DcTrackerBase.POLL_NETSTAT_SCREEN_OFF_MILLIS);
            }
            if (DcTrackerBase.this.mNetStatPollEnabled) {
                DcTrackerBase.this.mDataConnectionTracker.postDelayed(this, (long) DcTrackerBase.this.mNetStatPollPeriod);
            }
        }
    }

    static /* synthetic */ class C00993 {
        static final /* synthetic */ int[] $SwitchMap$com$android$internal$telephony$DctConstants$State = new int[State.values().length];

        static {
            try {
                $SwitchMap$com$android$internal$telephony$DctConstants$State[State.IDLE.ordinal()] = 1;
            } catch (NoSuchFieldError e) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$DctConstants$State[State.RETRYING.ordinal()] = 2;
            } catch (NoSuchFieldError e2) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$DctConstants$State[State.CONNECTING.ordinal()] = 3;
            } catch (NoSuchFieldError e3) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$DctConstants$State[State.SCANNING.ordinal()] = 4;
            } catch (NoSuchFieldError e4) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$DctConstants$State[State.CONNECTED.ordinal()] = 5;
            } catch (NoSuchFieldError e5) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$DctConstants$State[State.DISCONNECTING.ordinal()] = 6;
            } catch (NoSuchFieldError e6) {
            }
        }
    }

    private class DataRoamingSettingObserver extends ContentObserver {
        public DataRoamingSettingObserver(Handler handler, Context context) {
            super(handler);
            DcTrackerBase.this.mResolver = context.getContentResolver();
        }

        public void register() {
            if ("ALL_UNIFIED_CONTROL".equals("ALL_UNIFIED_CONTROL")) {
                DcTrackerBase.this.mResolver.registerContentObserver(Global.getUriFor("data_roaming"), false, this);
            } else {
                DcTrackerBase.this.mResolver.registerContentObserver(Global.getUriFor(TelephonyManager.appendId("data_roaming", (long) SubscriptionController.getInstance().getSlotId(DcTrackerBase.this.mPhone.getSubId()))), false, this);
            }
        }

        public void unregister() {
            DcTrackerBase.this.mResolver.unregisterContentObserver(this);
        }

        public void onChange(boolean selfChange) {
            if (DcTrackerBase.this.mPhone.getServiceState().getRoaming()) {
                DcTrackerBase.this.sendMessage(DcTrackerBase.this.obtainMessage(270347));
            }
        }
    }

    private class LteRoamingEnableObserver extends ContentObserver {
        public LteRoamingEnableObserver(Handler h) {
            super(h);
        }

        public void onChange(boolean selfChange) {
            DcTrackerBase.this.log("Do not support LTE Roaming");
        }
    }

    private class MobileDataSettingObserver extends ContentObserver {
        public MobileDataSettingObserver(Handler handler, Context context) {
            super(handler);
            DcTrackerBase.this.mResolver = context.getContentResolver();
        }

        public void register() {
            DcTrackerBase.this.mResolver.registerContentObserver(Global.getUriFor("mobile_data"), false, this);
        }

        public void unregister() {
            DcTrackerBase.this.mResolver.unregisterContentObserver(this);
        }

        public void onChange(boolean selfChange) {
            boolean z = true;
            DcTrackerBase dcTrackerBase = DcTrackerBase.this;
            if (Global.getInt(DcTrackerBase.this.mResolver, "mobile_data", 1) != 1) {
                z = false;
            }
            dcTrackerBase.mUserDataEnabled = z;
        }
    }

    protected static class RecoveryAction {
        public static final int CLEANUP = 1;
        public static final int GET_DATA_CALL_LIST = 0;
        public static final int RADIO_RESTART = 3;
        public static final int RADIO_RESTART_WITH_PROP = 4;
        public static final int REREGISTER = 2;

        protected RecoveryAction() {
        }

        private static boolean isAggressiveRecovery(int value) {
            return value == 1 || value == 2 || value == 3 || value == 4;
        }
    }

    public class TxRxSum {
        public long rxPkts;
        public long txPkts;

        public TxRxSum() {
            reset();
        }

        public TxRxSum(long txPkts, long rxPkts) {
            this.txPkts = txPkts;
            this.rxPkts = rxPkts;
        }

        public TxRxSum(TxRxSum sum) {
            this.txPkts = sum.txPkts;
            this.rxPkts = sum.rxPkts;
        }

        public void reset() {
            this.txPkts = -1;
            this.rxPkts = -1;
        }

        public String toString() {
            return "{txSum=" + this.txPkts + " rxSum=" + this.rxPkts + "}";
        }

        public void updateTxRxSum() {
            if (!"DCGG".equals("DGG") && !"DGG".equals("DGG") && !"DCGS".equals("DGG") && !"DCGGS".equals("DGG")) {
                this.txPkts = TrafficStats.getMobileTxPackets();
                this.rxPkts = TrafficStats.getMobileRxPackets();
            } else if (DcTrackerBase.this.mPhone.getPhoneId() == 0) {
                this.txPkts = TrafficStats.getMobileTxPackets("rmnet");
                this.rxPkts = TrafficStats.getMobileRxPackets("rmnet");
            } else {
                this.txPkts = TrafficStats.getMobileTxPackets("gsm_rmnet");
                this.rxPkts = TrafficStats.getMobileRxPackets("gsm_rmnet");
            }
        }

        public void updateTcpTxRxSum() {
            this.txPkts = TrafficStats.getMobileTcpTxPackets();
            this.rxPkts = TrafficStats.getMobileTcpRxPackets();
        }
    }

    private class VoLteSettingObserver extends ContentObserver {
        public VoLteSettingObserver(Handler handler, Context context) {
            super(handler);
            DcTrackerBase.this.mResolver = context.getContentResolver();
        }

        public void register() {
            DcTrackerBase.this.mResolver.registerContentObserver(System.getUriFor("voicecall_type"), true, this);
        }

        public void unregister() {
            DcTrackerBase.this.mResolver.unregisterContentObserver(this);
        }

        public void onChange(boolean selfChange) {
            boolean isVoLteOn = true;
            if (System.getInt(DcTrackerBase.this.mPhone.getContext().getContentResolver(), "voicecall_type", 1) != 0) {
                isVoLteOn = false;
            }
            DcTrackerBase.this.log("VoLteSettingObserver: onChange :: isVoLteOn=" + isVoLteOn);
            DcTrackerBase.this.onVoLteOn(isVoLteOn);
        }
    }

    private static native String sbmcgm_genId(String str, String str2, String str3);

    private static native String sbmcgm_genPasswd(String str);

    public abstract void DoGprsAttachOrDetach(Phone phone, int i);

    public abstract boolean IsApnExist(String str);

    public abstract void UpdateIccRecords(boolean z);

    protected abstract void changeConfigureForLteRoaming();

    protected abstract void changeCscUpdateStatus();

    protected abstract void changePreferedNetworkByMobileData();

    protected abstract void completeConnection(ApnContext apnContext);

    public abstract String getDefaultApnName();

    protected abstract State getOverallState();

    public abstract String[] getPcscfAddress(String str);

    public abstract State getState(String str);

    protected abstract void gotoIdleAndNotifyDataConnection(String str);

    public abstract boolean isApnTypeAvailable(String str);

    public abstract boolean isCdmaRat(int i);

    public abstract boolean isConnecting();

    protected abstract boolean isDataAllowed();

    public abstract boolean isDataPossible(String str);

    protected abstract boolean isDebugLevelNotLow();

    public abstract boolean isDisconnected();

    protected abstract boolean isPermanentFail(DcFailCause dcFailCause);

    protected abstract boolean isProvisioningApn(String str);

    protected abstract void log(String str);

    protected abstract void loge(String str);

    public abstract boolean matchIccRecord(String str);

    protected abstract boolean mvnoMatches(IccRecords iccRecords, String str, String str2);

    public abstract void notifyDataConnectionForSST(String str);

    protected abstract void onCleanUpAllConnections(String str);

    protected abstract void onCleanUpConnection(boolean z, int i, String str);

    protected abstract void onDataSetupComplete(AsyncResult asyncResult);

    protected abstract void onDataSetupCompleteError(AsyncResult asyncResult);

    protected abstract void onDisconnectDcRetrying(int i, AsyncResult asyncResult);

    protected abstract void onDisconnectDone(int i, AsyncResult asyncResult);

    protected abstract void onRadioAvailable();

    protected abstract void onRadioOffOrNotAvailable();

    protected abstract void onRoamingOff();

    protected abstract void onRoamingOn();

    protected abstract void onScreenStateChanged(boolean z);

    protected abstract void onTetherStateChanged(boolean z);

    protected abstract boolean onTrySetupData(String str);

    protected abstract void onUpdateIcc();

    protected abstract void onVoLteOn(boolean z);

    protected abstract void onVoiceCallEnded();

    protected abstract void onVoiceCallStarted();

    protected abstract void restartRadio();

    public abstract void setDataAllowed(boolean z, Message message);

    public abstract void setDataSubscription(int i);

    public abstract void setImsRegistrationState(boolean z);

    public void setSelectedApn() {
        /* JADX: method processing error */
/*
Error: java.lang.OutOfMemoryError: Java heap space
	at java.util.Arrays.copyOf(Arrays.java:3181)
	at java.util.ArrayList.grow(ArrayList.java:261)
	at java.util.ArrayList.ensureExplicitCapacity(ArrayList.java:235)
	at java.util.ArrayList.ensureCapacityInternal(ArrayList.java:227)
	at java.util.ArrayList.add(ArrayList.java:458)
	at jadx.core.utils.BlockUtils.collectWhileDominates(BlockUtils.java:463)
	at jadx.core.utils.BlockUtils.collectWhileDominates(BlockUtils.java:464)
	at jadx.core.utils.BlockUtils.collectWhileDominates(BlockUtils.java:464)
	at jadx.core.utils.BlockUtils.collectWhileDominates(BlockUtils.java:464)
	at jadx.core.utils.BlockUtils.collectWhileDominates(BlockUtils.java:464)
	at jadx.core.utils.BlockUtils.collectWhileDominates(BlockUtils.java:464)
	at jadx.core.utils.BlockUtils.collectWhileDominates(BlockUtils.java:464)
	at jadx.core.utils.BlockUtils.collectWhileDominates(BlockUtils.java:464)
	at jadx.core.utils.BlockUtils.collectWhileDominates(BlockUtils.java:464)
	at jadx.core.utils.BlockUtils.collectWhileDominates(BlockUtils.java:464)
	at jadx.core.utils.BlockUtils.collectWhileDominates(BlockUtils.java:464)
	at jadx.core.utils.BlockUtils.collectWhileDominates(BlockUtils.java:464)
	at jadx.core.utils.BlockUtils.collectWhileDominates(BlockUtils.java:464)
	at jadx.core.utils.BlockUtils.collectWhileDominates(BlockUtils.java:464)
	at jadx.core.utils.BlockUtils.collectWhileDominates(BlockUtils.java:464)
	at jadx.core.utils.BlockUtils.collectWhileDominates(BlockUtils.java:464)
	at jadx.core.utils.BlockUtils.collectWhileDominates(BlockUtils.java:464)
	at jadx.core.utils.BlockUtils.collectWhileDominates(BlockUtils.java:464)
	at jadx.core.utils.BlockUtils.collectWhileDominates(BlockUtils.java:464)
	at jadx.core.utils.BlockUtils.collectWhileDominates(BlockUtils.java:464)
	at jadx.core.utils.BlockUtils.collectWhileDominates(BlockUtils.java:464)
	at jadx.core.utils.BlockUtils.collectWhileDominates(BlockUtils.java:464)
	at jadx.core.utils.BlockUtils.collectWhileDominates(BlockUtils.java:464)
	at jadx.core.utils.BlockUtils.collectWhileDominates(BlockUtils.java:464)
	at jadx.core.utils.BlockUtils.collectWhileDominates(BlockUtils.java:464)
	at jadx.core.utils.BlockUtils.collectWhileDominates(BlockUtils.java:464)
	at jadx.core.utils.BlockUtils.collectWhileDominates(BlockUtils.java:464)
*/
        /*
        r22 = this;
        r2 = "ril.ICC_TYPE";
        r0 = r22;
        r3 = r0.mPhone;
        r6 = r3.getSubId();
        r3 = "0";
        r12 = android.telephony.TelephonyManager.getTelephonyProperty(r2, r6, r3);
        r2 = new java.lang.StringBuilder;
        r2.<init>();
        r3 = "Enter setSelectedApn(), iccType=";
        r2 = r2.append(r3);
        r2 = r2.append(r12);
        r2 = r2.toString();
        r0 = r22;
        r0.log(r2);
        r10 = 0;
        r13 = 0;
        r14 = 0;
        r19 = 0;
        r11 = 0;
        r15 = 0;
        r8 = 0;
        r20 = 0;
        r17 = 0;
        r16 = 0;
        r5 = "";
        r2 = "CG";
        r3 = "DGG";
        r2 = r2.equals(r3);
        if (r2 != 0) goto L_0x006a;
    L_0x0042:
        r2 = "DCG";
        r3 = "DGG";
        r2 = r2.equals(r3);
        if (r2 != 0) goto L_0x006a;
    L_0x004c:
        r2 = "DCGS";
        r3 = "DGG";
        r2 = r2.equals(r3);
        if (r2 != 0) goto L_0x006a;
    L_0x0056:
        r2 = "DCGG";
        r3 = "DGG";
        r2 = r2.equals(r3);
        if (r2 != 0) goto L_0x006a;
    L_0x0060:
        r2 = "DCGGS";
        r3 = "DGG";
        r2 = r2.equals(r3);
        if (r2 == 0) goto L_0x0228;
    L_0x006a:
        r2 = new java.lang.StringBuilder;
        r2.<init>();
        r3 = "numeric=\"";
        r2 = r2.append(r3);
        r3 = "gsm.sim.operator.numeric";
        r0 = r22;
        r4 = r0.mPhone;
        r6 = r4.getSubId();
        r4 = "";
        r3 = android.telephony.TelephonyManager.getTelephonyProperty(r3, r6, r4);
        r2 = r2.append(r3);
        r3 = "\"";
        r2 = r2.append(r3);
        r5 = r2.toString();
    L_0x0093:
        r2 = "CG";	 Catch:{ all -> 0x021a }
        r3 = "DGG";	 Catch:{ all -> 0x021a }
        r2 = r2.equals(r3);	 Catch:{ all -> 0x021a }
        if (r2 != 0) goto L_0x00c5;	 Catch:{ all -> 0x021a }
    L_0x009d:
        r2 = "DCGG";	 Catch:{ all -> 0x021a }
        r3 = "DGG";	 Catch:{ all -> 0x021a }
        r2 = r2.equals(r3);	 Catch:{ all -> 0x021a }
        if (r2 != 0) goto L_0x00c5;	 Catch:{ all -> 0x021a }
    L_0x00a7:
        r2 = "DCGS";	 Catch:{ all -> 0x021a }
        r3 = "DGG";	 Catch:{ all -> 0x021a }
        r2 = r2.equals(r3);	 Catch:{ all -> 0x021a }
        if (r2 != 0) goto L_0x00c5;	 Catch:{ all -> 0x021a }
    L_0x00b1:
        r2 = "DCGGS";	 Catch:{ all -> 0x021a }
        r3 = "DGG";	 Catch:{ all -> 0x021a }
        r2 = r2.equals(r3);	 Catch:{ all -> 0x021a }
        if (r2 != 0) goto L_0x00c5;	 Catch:{ all -> 0x021a }
    L_0x00bb:
        r2 = "GG";	 Catch:{ all -> 0x021a }
        r3 = "DGG";	 Catch:{ all -> 0x021a }
        r2 = r2.equals(r3);	 Catch:{ all -> 0x021a }
        if (r2 == 0) goto L_0x013e;	 Catch:{ all -> 0x021a }
    L_0x00c5:
        r2 = android.telephony.TelephonyManager.getDefault();	 Catch:{ all -> 0x021a }
        r0 = r22;	 Catch:{ all -> 0x021a }
        r3 = r0.mPhone;	 Catch:{ all -> 0x021a }
        r6 = r3.getSubId();	 Catch:{ all -> 0x021a }
        r2 = r2.getNetworkType(r6);	 Catch:{ all -> 0x021a }
        r3 = 13;	 Catch:{ all -> 0x021a }
        if (r2 == r3) goto L_0x00ed;	 Catch:{ all -> 0x021a }
    L_0x00d9:
        r2 = android.telephony.TelephonyManager.getDefault();	 Catch:{ all -> 0x021a }
        r0 = r22;	 Catch:{ all -> 0x021a }
        r3 = r0.mPhone;	 Catch:{ all -> 0x021a }
        r6 = r3.getSubId();	 Catch:{ all -> 0x021a }
        r2 = r2.getNetworkType(r6);	 Catch:{ all -> 0x021a }
        r3 = 14;	 Catch:{ all -> 0x021a }
        if (r2 != r3) goto L_0x024b;	 Catch:{ all -> 0x021a }
    L_0x00ed:
        r0 = r22;	 Catch:{ all -> 0x021a }
        r2 = r0.mPhone;	 Catch:{ all -> 0x021a }
        r2 = r2.getContext();	 Catch:{ all -> 0x021a }
        r2 = r2.getContentResolver();	 Catch:{ all -> 0x021a }
        r3 = new java.lang.StringBuilder;	 Catch:{ all -> 0x021a }
        r3.<init>();	 Catch:{ all -> 0x021a }
        r4 = PREFERAPN_URI_USING_SUBID;	 Catch:{ all -> 0x021a }
        r4 = r4.toString();	 Catch:{ all -> 0x021a }
        r3 = r3.append(r4);	 Catch:{ all -> 0x021a }
        r4 = "4G";	 Catch:{ all -> 0x021a }
        r3 = r3.append(r4);	 Catch:{ all -> 0x021a }
        r3 = r3.toString();	 Catch:{ all -> 0x021a }
        r3 = android.net.Uri.parse(r3);	 Catch:{ all -> 0x021a }
        r4 = 6;	 Catch:{ all -> 0x021a }
        r4 = new java.lang.String[r4];	 Catch:{ all -> 0x021a }
        r6 = 0;	 Catch:{ all -> 0x021a }
        r7 = "_id";	 Catch:{ all -> 0x021a }
        r4[r6] = r7;	 Catch:{ all -> 0x021a }
        r6 = 1;	 Catch:{ all -> 0x021a }
        r7 = "name";	 Catch:{ all -> 0x021a }
        r4[r6] = r7;	 Catch:{ all -> 0x021a }
        r6 = 2;	 Catch:{ all -> 0x021a }
        r7 = "apn";	 Catch:{ all -> 0x021a }
        r4[r6] = r7;	 Catch:{ all -> 0x021a }
        r6 = 3;	 Catch:{ all -> 0x021a }
        r7 = "type";	 Catch:{ all -> 0x021a }
        r4[r6] = r7;	 Catch:{ all -> 0x021a }
        r6 = 4;	 Catch:{ all -> 0x021a }
        r7 = "numeric";	 Catch:{ all -> 0x021a }
        r4[r6] = r7;	 Catch:{ all -> 0x021a }
        r6 = 5;	 Catch:{ all -> 0x021a }
        r7 = "network_flag";	 Catch:{ all -> 0x021a }
        r4[r6] = r7;	 Catch:{ all -> 0x021a }
        r6 = 0;	 Catch:{ all -> 0x021a }
        r7 = "name ASC";	 Catch:{ all -> 0x021a }
        r10 = r2.query(r3, r4, r5, r6, r7);	 Catch:{ all -> 0x021a }
    L_0x013e:
        r2 = new java.lang.StringBuilder;	 Catch:{ all -> 0x021a }
        r2.<init>();	 Catch:{ all -> 0x021a }
        r3 = "setSelectedApn: cursor=";	 Catch:{ all -> 0x021a }
        r2 = r2.append(r3);	 Catch:{ all -> 0x021a }
        r2 = r2.append(r10);	 Catch:{ all -> 0x021a }
        r3 = " cursor.count=";	 Catch:{ all -> 0x021a }
        r3 = r2.append(r3);	 Catch:{ all -> 0x021a }
        if (r10 == 0) goto L_0x02a4;	 Catch:{ all -> 0x021a }
    L_0x0155:
        r2 = r10.getCount();	 Catch:{ all -> 0x021a }
    L_0x0159:
        r2 = r3.append(r2);	 Catch:{ all -> 0x021a }
        r2 = r2.toString();	 Catch:{ all -> 0x021a }
        r0 = r22;	 Catch:{ all -> 0x021a }
        r0.log(r2);	 Catch:{ all -> 0x021a }
        if (r10 == 0) goto L_0x0193;	 Catch:{ all -> 0x021a }
    L_0x0168:
        r2 = r10.getCount();	 Catch:{ all -> 0x021a }
        if (r2 <= 0) goto L_0x0193;	 Catch:{ all -> 0x021a }
    L_0x016e:
        r10.moveToFirst();	 Catch:{ all -> 0x021a }
        r2 = "_id";	 Catch:{ all -> 0x021a }
        r2 = r10.getColumnIndexOrThrow(r2);	 Catch:{ all -> 0x021a }
        r14 = r10.getInt(r2);	 Catch:{ all -> 0x021a }
        r2 = new java.lang.StringBuilder;	 Catch:{ all -> 0x021a }
        r2.<init>();	 Catch:{ all -> 0x021a }
        r3 = "setSelectedApn: initialId=";	 Catch:{ all -> 0x021a }
        r2 = r2.append(r3);	 Catch:{ all -> 0x021a }
        r2 = r2.append(r14);	 Catch:{ all -> 0x021a }
        r2 = r2.toString();	 Catch:{ all -> 0x021a }
        r0 = r22;	 Catch:{ all -> 0x021a }
        r0.log(r2);	 Catch:{ all -> 0x021a }
    L_0x0193:
        if (r10 == 0) goto L_0x0198;	 Catch:{ all -> 0x021a }
    L_0x0195:
        r10.close();	 Catch:{ all -> 0x021a }
    L_0x0198:
        r0 = r22;	 Catch:{ all -> 0x021a }
        r2 = r0.mPhone;	 Catch:{ all -> 0x021a }
        r2 = r2.getContext();	 Catch:{ all -> 0x021a }
        r2 = r2.getContentResolver();	 Catch:{ all -> 0x021a }
        r3 = android.provider.Telephony.Carriers.CONTENT_URI;	 Catch:{ all -> 0x021a }
        r4 = 6;	 Catch:{ all -> 0x021a }
        r4 = new java.lang.String[r4];	 Catch:{ all -> 0x021a }
        r6 = 0;	 Catch:{ all -> 0x021a }
        r7 = "_id";	 Catch:{ all -> 0x021a }
        r4[r6] = r7;	 Catch:{ all -> 0x021a }
        r6 = 1;	 Catch:{ all -> 0x021a }
        r7 = "name";	 Catch:{ all -> 0x021a }
        r4[r6] = r7;	 Catch:{ all -> 0x021a }
        r6 = 2;	 Catch:{ all -> 0x021a }
        r7 = "apn";	 Catch:{ all -> 0x021a }
        r4[r6] = r7;	 Catch:{ all -> 0x021a }
        r6 = 3;	 Catch:{ all -> 0x021a }
        r7 = "type";	 Catch:{ all -> 0x021a }
        r4[r6] = r7;	 Catch:{ all -> 0x021a }
        r6 = 4;	 Catch:{ all -> 0x021a }
        r7 = "user";	 Catch:{ all -> 0x021a }
        r4[r6] = r7;	 Catch:{ all -> 0x021a }
        r6 = 5;	 Catch:{ all -> 0x021a }
        r7 = "network_flag";	 Catch:{ all -> 0x021a }
        r4[r6] = r7;	 Catch:{ all -> 0x021a }
        r6 = 0;	 Catch:{ all -> 0x021a }
        r7 = 0;	 Catch:{ all -> 0x021a }
        r10 = r2.query(r3, r4, r5, r6, r7);	 Catch:{ all -> 0x021a }
        r2 = new java.lang.StringBuilder;	 Catch:{ all -> 0x021a }
        r2.<init>();	 Catch:{ all -> 0x021a }
        r3 = "setSelectedApn: where=";	 Catch:{ all -> 0x021a }
        r2 = r2.append(r3);	 Catch:{ all -> 0x021a }
        r2 = r2.append(r5);	 Catch:{ all -> 0x021a }
        r3 = ", cursor=";	 Catch:{ all -> 0x021a }
        r2 = r2.append(r3);	 Catch:{ all -> 0x021a }
        r2 = r2.append(r10);	 Catch:{ all -> 0x021a }
        r3 = " cursor.count=";	 Catch:{ all -> 0x021a }
        r3 = r2.append(r3);	 Catch:{ all -> 0x021a }
        if (r10 == 0) goto L_0x02a7;	 Catch:{ all -> 0x021a }
    L_0x01ee:
        r2 = r10.getCount();	 Catch:{ all -> 0x021a }
    L_0x01f2:
        r2 = r3.append(r2);	 Catch:{ all -> 0x021a }
        r2 = r2.toString();	 Catch:{ all -> 0x021a }
        r0 = r22;	 Catch:{ all -> 0x021a }
        r0.log(r2);	 Catch:{ all -> 0x021a }
        if (r10 == 0) goto L_0x067a;	 Catch:{ all -> 0x021a }
    L_0x0201:
        r10.moveToFirst();	 Catch:{ all -> 0x021a }
    L_0x0204:
        r2 = r10.isAfterLast();	 Catch:{ all -> 0x021a }
        if (r2 != 0) goto L_0x067a;	 Catch:{ all -> 0x021a }
    L_0x020a:
        r2 = "_id";	 Catch:{ all -> 0x021a }
        r2 = r10.getColumnIndexOrThrow(r2);	 Catch:{ all -> 0x021a }
        r13 = r10.getInt(r2);	 Catch:{ all -> 0x021a }
        if (r13 != r14) goto L_0x02aa;	 Catch:{ all -> 0x021a }
    L_0x0216:
        r10.moveToNext();	 Catch:{ all -> 0x021a }
        goto L_0x0204;
    L_0x021a:
        r2 = move-exception;
        r3 = "Exit setSelectedApn(), finally";
        r0 = r22;
        r0.log(r3);
        if (r10 == 0) goto L_0x0227;
    L_0x0224:
        r10.close();
    L_0x0227:
        throw r2;
    L_0x0228:
        r2 = new java.lang.StringBuilder;
        r2.<init>();
        r3 = "numeric=\"";
        r2 = r2.append(r3);
        r3 = "gsm.sim.operator.numeric";
        r4 = "";
        r3 = android.os.SystemProperties.get(r3, r4);
        r2 = r2.append(r3);
        r3 = "\"";
        r2 = r2.append(r3);
        r5 = r2.toString();
        goto L_0x0093;
    L_0x024b:
        r0 = r22;	 Catch:{ all -> 0x021a }
        r2 = r0.mPhone;	 Catch:{ all -> 0x021a }
        r2 = r2.getContext();	 Catch:{ all -> 0x021a }
        r2 = r2.getContentResolver();	 Catch:{ all -> 0x021a }
        r3 = new java.lang.StringBuilder;	 Catch:{ all -> 0x021a }
        r3.<init>();	 Catch:{ all -> 0x021a }
        r4 = PREFERAPN_URI_USING_SUBID;	 Catch:{ all -> 0x021a }
        r4 = r4.toString();	 Catch:{ all -> 0x021a }
        r3 = r3.append(r4);	 Catch:{ all -> 0x021a }
        r0 = r22;	 Catch:{ all -> 0x021a }
        r4 = r0.mPhone;	 Catch:{ all -> 0x021a }
        r6 = r4.getSubId();	 Catch:{ all -> 0x021a }
        r3 = r3.append(r6);	 Catch:{ all -> 0x021a }
        r3 = r3.toString();	 Catch:{ all -> 0x021a }
        r3 = android.net.Uri.parse(r3);	 Catch:{ all -> 0x021a }
        r4 = 6;	 Catch:{ all -> 0x021a }
        r4 = new java.lang.String[r4];	 Catch:{ all -> 0x021a }
        r6 = 0;	 Catch:{ all -> 0x021a }
        r7 = "_id";	 Catch:{ all -> 0x021a }
        r4[r6] = r7;	 Catch:{ all -> 0x021a }
        r6 = 1;	 Catch:{ all -> 0x021a }
        r7 = "name";	 Catch:{ all -> 0x021a }
        r4[r6] = r7;	 Catch:{ all -> 0x021a }
        r6 = 2;	 Catch:{ all -> 0x021a }
        r7 = "apn";	 Catch:{ all -> 0x021a }
        r4[r6] = r7;	 Catch:{ all -> 0x021a }
        r6 = 3;	 Catch:{ all -> 0x021a }
        r7 = "type";	 Catch:{ all -> 0x021a }
        r4[r6] = r7;	 Catch:{ all -> 0x021a }
        r6 = 4;	 Catch:{ all -> 0x021a }
        r7 = "numeric";	 Catch:{ all -> 0x021a }
        r4[r6] = r7;	 Catch:{ all -> 0x021a }
        r6 = 5;	 Catch:{ all -> 0x021a }
        r7 = "network_flag";	 Catch:{ all -> 0x021a }
        r4[r6] = r7;	 Catch:{ all -> 0x021a }
        r6 = 0;	 Catch:{ all -> 0x021a }
        r7 = "name ASC";	 Catch:{ all -> 0x021a }
        r10 = r2.query(r3, r4, r5, r6, r7);	 Catch:{ all -> 0x021a }
        goto L_0x013e;	 Catch:{ all -> 0x021a }
    L_0x02a4:
        r2 = 0;	 Catch:{ all -> 0x021a }
        goto L_0x0159;	 Catch:{ all -> 0x021a }
    L_0x02a7:
        r2 = 0;	 Catch:{ all -> 0x021a }
        goto L_0x01f2;	 Catch:{ all -> 0x021a }
    L_0x02aa:
        r2 = "name";	 Catch:{ all -> 0x021a }
        r2 = r10.getColumnIndexOrThrow(r2);	 Catch:{ all -> 0x021a }
        r15 = r10.getString(r2);	 Catch:{ all -> 0x021a }
        r2 = "apn";	 Catch:{ all -> 0x021a }
        r2 = r10.getColumnIndexOrThrow(r2);	 Catch:{ all -> 0x021a }
        r8 = r10.getString(r2);	 Catch:{ all -> 0x021a }
        r2 = "type";	 Catch:{ all -> 0x021a }
        r2 = r10.getColumnIndexOrThrow(r2);	 Catch:{ all -> 0x021a }
        r20 = r10.getString(r2);	 Catch:{ all -> 0x021a }
        r2 = "network_flag";	 Catch:{ all -> 0x021a }
        r2 = r10.getColumnIndexOrThrow(r2);	 Catch:{ all -> 0x021a }
        r16 = r10.getString(r2);	 Catch:{ all -> 0x021a }
        r2 = "ril.IsCSIM";	 Catch:{ all -> 0x021a }
        r3 = "";	 Catch:{ all -> 0x021a }
        r9 = android.os.SystemProperties.get(r2, r3);	 Catch:{ all -> 0x021a }
        r2 = new java.lang.StringBuilder;	 Catch:{ all -> 0x021a }
        r2.<init>();	 Catch:{ all -> 0x021a }
        r3 = "setSelectedApn: id=";	 Catch:{ all -> 0x021a }
        r2 = r2.append(r3);	 Catch:{ all -> 0x021a }
        r2 = r2.append(r13);	 Catch:{ all -> 0x021a }
        r3 = ", name=";	 Catch:{ all -> 0x021a }
        r2 = r2.append(r3);	 Catch:{ all -> 0x021a }
        r2 = r2.append(r15);	 Catch:{ all -> 0x021a }
        r3 = ", apn=";	 Catch:{ all -> 0x021a }
        r2 = r2.append(r3);	 Catch:{ all -> 0x021a }
        r2 = r2.append(r8);	 Catch:{ all -> 0x021a }
        r3 = ", type=";	 Catch:{ all -> 0x021a }
        r2 = r2.append(r3);	 Catch:{ all -> 0x021a }
        r0 = r20;	 Catch:{ all -> 0x021a }
        r2 = r2.append(r0);	 Catch:{ all -> 0x021a }
        r3 = ", network_flag=";	 Catch:{ all -> 0x021a }
        r2 = r2.append(r3);	 Catch:{ all -> 0x021a }
        r0 = r16;	 Catch:{ all -> 0x021a }
        r2 = r2.append(r0);	 Catch:{ all -> 0x021a }
        r3 = ", card_flag=";	 Catch:{ all -> 0x021a }
        r2 = r2.append(r3);	 Catch:{ all -> 0x021a }
        r2 = r2.append(r9);	 Catch:{ all -> 0x021a }
        r3 = ",network type=";	 Catch:{ all -> 0x021a }
        r2 = r2.append(r3);	 Catch:{ all -> 0x021a }
        r3 = android.telephony.TelephonyManager.getDefault();	 Catch:{ all -> 0x021a }
        r0 = r22;	 Catch:{ all -> 0x021a }
        r4 = r0.mPhone;	 Catch:{ all -> 0x021a }
        r6 = r4.getSubId();	 Catch:{ all -> 0x021a }
        r3 = r3.getNetworkType(r6);	 Catch:{ all -> 0x021a }
        r2 = r2.append(r3);	 Catch:{ all -> 0x021a }
        r2 = r2.toString();	 Catch:{ all -> 0x021a }
        r0 = r22;	 Catch:{ all -> 0x021a }
        r0.log(r2);	 Catch:{ all -> 0x021a }
        r2 = "DCGG";	 Catch:{ all -> 0x021a }
        r3 = "DGG";	 Catch:{ all -> 0x021a }
        r2 = r2.equals(r3);	 Catch:{ all -> 0x021a }
        if (r2 != 0) goto L_0x0360;	 Catch:{ all -> 0x021a }
    L_0x034c:
        r2 = "DCGS";	 Catch:{ all -> 0x021a }
        r3 = "DGG";	 Catch:{ all -> 0x021a }
        r2 = r2.equals(r3);	 Catch:{ all -> 0x021a }
        if (r2 != 0) goto L_0x0360;	 Catch:{ all -> 0x021a }
    L_0x0356:
        r2 = "DCGGS";	 Catch:{ all -> 0x021a }
        r3 = "DGG";	 Catch:{ all -> 0x021a }
        r2 = r2.equals(r3);	 Catch:{ all -> 0x021a }
        if (r2 == 0) goto L_0x04de;	 Catch:{ all -> 0x021a }
    L_0x0360:
        r0 = r22;	 Catch:{ all -> 0x021a }
        r2 = r0.mPhone;	 Catch:{ all -> 0x021a }
        r2 = r2.getPhoneId();	 Catch:{ all -> 0x021a }
        if (r2 != 0) goto L_0x04de;	 Catch:{ all -> 0x021a }
    L_0x036a:
        r2 = "true";	 Catch:{ all -> 0x021a }
        r3 = "gsm.operator.isroaming";	 Catch:{ all -> 0x021a }
        r0 = r22;	 Catch:{ all -> 0x021a }
        r4 = r0.mPhone;	 Catch:{ all -> 0x021a }
        r6 = r4.getSubId();	 Catch:{ all -> 0x021a }
        r4 = "";	 Catch:{ all -> 0x021a }
        r3 = android.telephony.TelephonyManager.getTelephonyProperty(r3, r6, r4);	 Catch:{ all -> 0x021a }
        r2 = r2.equals(r3);	 Catch:{ all -> 0x021a }
        if (r2 != 0) goto L_0x04de;	 Catch:{ all -> 0x021a }
    L_0x0382:
        r2 = "4";	 Catch:{ all -> 0x021a }
        r2 = r2.equals(r12);	 Catch:{ all -> 0x021a }
        if (r2 != 0) goto L_0x0392;	 Catch:{ all -> 0x021a }
    L_0x038a:
        r2 = "3";	 Catch:{ all -> 0x021a }
        r2 = r2.equals(r12);	 Catch:{ all -> 0x021a }
        if (r2 == 0) goto L_0x039f;	 Catch:{ all -> 0x021a }
    L_0x0392:
        r2 = "live.vodafone.com";	 Catch:{ all -> 0x021a }
        r2 = r2.equalsIgnoreCase(r8);	 Catch:{ all -> 0x021a }
        if (r2 == 0) goto L_0x039f;	 Catch:{ all -> 0x021a }
    L_0x039a:
        r10.moveToNext();	 Catch:{ all -> 0x021a }
        goto L_0x0204;	 Catch:{ all -> 0x021a }
    L_0x039f:
        r2 = android.telephony.TelephonyManager.getDefault();	 Catch:{ all -> 0x021a }
        r0 = r22;	 Catch:{ all -> 0x021a }
        r3 = r0.mPhone;	 Catch:{ all -> 0x021a }
        r6 = r3.getSubId();	 Catch:{ all -> 0x021a }
        r2 = r2.getNetworkType(r6);	 Catch:{ all -> 0x021a }
        r3 = 13;	 Catch:{ all -> 0x021a }
        if (r2 == r3) goto L_0x03c7;	 Catch:{ all -> 0x021a }
    L_0x03b3:
        r2 = android.telephony.TelephonyManager.getDefault();	 Catch:{ all -> 0x021a }
        r0 = r22;	 Catch:{ all -> 0x021a }
        r3 = r0.mPhone;	 Catch:{ all -> 0x021a }
        r6 = r3.getSubId();	 Catch:{ all -> 0x021a }
        r2 = r2.getNetworkType(r6);	 Catch:{ all -> 0x021a }
        r3 = 14;	 Catch:{ all -> 0x021a }
        if (r2 != r3) goto L_0x0408;	 Catch:{ all -> 0x021a }
    L_0x03c7:
        r2 = "3";	 Catch:{ all -> 0x021a }
        r0 = r16;	 Catch:{ all -> 0x021a }
        r2 = r2.equals(r0);	 Catch:{ all -> 0x021a }
        if (r2 == 0) goto L_0x05fb;	 Catch:{ all -> 0x021a }
    L_0x03d1:
        r2 = new java.lang.StringBuilder;	 Catch:{ all -> 0x021a }
        r2.<init>();	 Catch:{ all -> 0x021a }
        r3 = "network_flag 3, NetworkType=";	 Catch:{ all -> 0x021a }
        r2 = r2.append(r3);	 Catch:{ all -> 0x021a }
        r3 = android.telephony.TelephonyManager.getDefault();	 Catch:{ all -> 0x021a }
        r0 = r22;	 Catch:{ all -> 0x021a }
        r4 = r0.mPhone;	 Catch:{ all -> 0x021a }
        r6 = r4.getSubId();	 Catch:{ all -> 0x021a }
        r3 = r3.getNetworkType(r6);	 Catch:{ all -> 0x021a }
        r2 = r2.append(r3);	 Catch:{ all -> 0x021a }
        r3 = ", apn=";	 Catch:{ all -> 0x021a }
        r2 = r2.append(r3);	 Catch:{ all -> 0x021a }
        r2 = r2.append(r8);	 Catch:{ all -> 0x021a }
        r2 = r2.toString();	 Catch:{ all -> 0x021a }
        r0 = r22;	 Catch:{ all -> 0x021a }
        r0.log(r2);	 Catch:{ all -> 0x021a }
        r10.moveToNext();	 Catch:{ all -> 0x021a }
        goto L_0x0204;	 Catch:{ all -> 0x021a }
    L_0x0408:
        r2 = android.telephony.TelephonyManager.getDefault();	 Catch:{ all -> 0x021a }
        r0 = r22;	 Catch:{ all -> 0x021a }
        r3 = r0.mPhone;	 Catch:{ all -> 0x021a }
        r6 = r3.getSubId();	 Catch:{ all -> 0x021a }
        r2 = r2.getNetworkType(r6);	 Catch:{ all -> 0x021a }
        r3 = 7;	 Catch:{ all -> 0x021a }
        if (r2 == r3) goto L_0x0441;	 Catch:{ all -> 0x021a }
    L_0x041b:
        r2 = android.telephony.TelephonyManager.getDefault();	 Catch:{ all -> 0x021a }
        r0 = r22;	 Catch:{ all -> 0x021a }
        r3 = r0.mPhone;	 Catch:{ all -> 0x021a }
        r6 = r3.getSubId();	 Catch:{ all -> 0x021a }
        r2 = r2.getNetworkType(r6);	 Catch:{ all -> 0x021a }
        r3 = 5;	 Catch:{ all -> 0x021a }
        if (r2 == r3) goto L_0x0441;	 Catch:{ all -> 0x021a }
    L_0x042e:
        r2 = android.telephony.TelephonyManager.getDefault();	 Catch:{ all -> 0x021a }
        r0 = r22;	 Catch:{ all -> 0x021a }
        r3 = r0.mPhone;	 Catch:{ all -> 0x021a }
        r6 = r3.getSubId();	 Catch:{ all -> 0x021a }
        r2 = r2.getNetworkType(r6);	 Catch:{ all -> 0x021a }
        r3 = 6;	 Catch:{ all -> 0x021a }
        if (r2 != r3) goto L_0x0482;	 Catch:{ all -> 0x021a }
    L_0x0441:
        r2 = "4";	 Catch:{ all -> 0x021a }
        r0 = r16;	 Catch:{ all -> 0x021a }
        r2 = r2.equals(r0);	 Catch:{ all -> 0x021a }
        if (r2 == 0) goto L_0x05fb;	 Catch:{ all -> 0x021a }
    L_0x044b:
        r2 = new java.lang.StringBuilder;	 Catch:{ all -> 0x021a }
        r2.<init>();	 Catch:{ all -> 0x021a }
        r3 = "network_flag 4, NetworkType=";	 Catch:{ all -> 0x021a }
        r2 = r2.append(r3);	 Catch:{ all -> 0x021a }
        r3 = android.telephony.TelephonyManager.getDefault();	 Catch:{ all -> 0x021a }
        r0 = r22;	 Catch:{ all -> 0x021a }
        r4 = r0.mPhone;	 Catch:{ all -> 0x021a }
        r6 = r4.getSubId();	 Catch:{ all -> 0x021a }
        r3 = r3.getNetworkType(r6);	 Catch:{ all -> 0x021a }
        r2 = r2.append(r3);	 Catch:{ all -> 0x021a }
        r3 = ", apn=";	 Catch:{ all -> 0x021a }
        r2 = r2.append(r3);	 Catch:{ all -> 0x021a }
        r2 = r2.append(r8);	 Catch:{ all -> 0x021a }
        r2 = r2.toString();	 Catch:{ all -> 0x021a }
        r0 = r22;	 Catch:{ all -> 0x021a }
        r0.log(r2);	 Catch:{ all -> 0x021a }
        r10.moveToNext();	 Catch:{ all -> 0x021a }
        goto L_0x0204;	 Catch:{ all -> 0x021a }
    L_0x0482:
        r2 = android.telephony.TelephonyManager.getDefault();	 Catch:{ all -> 0x021a }
        r0 = r22;	 Catch:{ all -> 0x021a }
        r3 = r0.mPhone;	 Catch:{ all -> 0x021a }
        r6 = r3.getSubId();	 Catch:{ all -> 0x021a }
        r2 = r2.getNetworkType(r6);	 Catch:{ all -> 0x021a }
        if (r2 != 0) goto L_0x04aa;	 Catch:{ all -> 0x021a }
    L_0x0494:
        r2 = "4";	 Catch:{ all -> 0x021a }
        r0 = r16;	 Catch:{ all -> 0x021a }
        r2 = r2.equals(r0);	 Catch:{ all -> 0x021a }
        if (r2 == 0) goto L_0x05fb;	 Catch:{ all -> 0x021a }
    L_0x049e:
        r2 = "Unknown NETWORK,non-LTE model network_flag 4 skip";	 Catch:{ all -> 0x021a }
        r0 = r22;	 Catch:{ all -> 0x021a }
        r0.log(r2);	 Catch:{ all -> 0x021a }
        r10.moveToNext();	 Catch:{ all -> 0x021a }
        goto L_0x0204;	 Catch:{ all -> 0x021a }
    L_0x04aa:
        r2 = "1";	 Catch:{ all -> 0x021a }
        r2 = r2.equals(r9);	 Catch:{ all -> 0x021a }
        if (r2 == 0) goto L_0x04c8;	 Catch:{ all -> 0x021a }
    L_0x04b2:
        r2 = "3";	 Catch:{ all -> 0x021a }
        r0 = r16;	 Catch:{ all -> 0x021a }
        r2 = r2.equals(r0);	 Catch:{ all -> 0x021a }
        if (r2 == 0) goto L_0x05fb;	 Catch:{ all -> 0x021a }
    L_0x04bc:
        r2 = "4G card -  network_flag 3 skip";	 Catch:{ all -> 0x021a }
        r0 = r22;	 Catch:{ all -> 0x021a }
        r0.log(r2);	 Catch:{ all -> 0x021a }
        r10.moveToNext();	 Catch:{ all -> 0x021a }
        goto L_0x0204;	 Catch:{ all -> 0x021a }
    L_0x04c8:
        r2 = "4";	 Catch:{ all -> 0x021a }
        r0 = r16;	 Catch:{ all -> 0x021a }
        r2 = r2.equals(r0);	 Catch:{ all -> 0x021a }
        if (r2 == 0) goto L_0x05fb;	 Catch:{ all -> 0x021a }
    L_0x04d2:
        r2 = "4G card -  network_flag 4 skip";	 Catch:{ all -> 0x021a }
        r0 = r22;	 Catch:{ all -> 0x021a }
        r0.log(r2);	 Catch:{ all -> 0x021a }
        r10.moveToNext();	 Catch:{ all -> 0x021a }
        goto L_0x0204;	 Catch:{ all -> 0x021a }
    L_0x04de:
        r2 = "CG";	 Catch:{ all -> 0x021a }
        r3 = "DGG";	 Catch:{ all -> 0x021a }
        r2 = r2.equals(r3);	 Catch:{ all -> 0x021a }
        if (r2 != 0) goto L_0x0510;	 Catch:{ all -> 0x021a }
    L_0x04e8:
        r2 = "DCG";	 Catch:{ all -> 0x021a }
        r3 = "DGG";	 Catch:{ all -> 0x021a }
        r2 = r2.equals(r3);	 Catch:{ all -> 0x021a }
        if (r2 != 0) goto L_0x0510;	 Catch:{ all -> 0x021a }
    L_0x04f2:
        r2 = "DCGS";	 Catch:{ all -> 0x021a }
        r3 = "DGG";	 Catch:{ all -> 0x021a }
        r2 = r2.equals(r3);	 Catch:{ all -> 0x021a }
        if (r2 != 0) goto L_0x0510;	 Catch:{ all -> 0x021a }
    L_0x04fc:
        r2 = "DCGG";	 Catch:{ all -> 0x021a }
        r3 = "DGG";	 Catch:{ all -> 0x021a }
        r2 = r2.equals(r3);	 Catch:{ all -> 0x021a }
        if (r2 != 0) goto L_0x0510;	 Catch:{ all -> 0x021a }
    L_0x0506:
        r2 = "DCGGS";	 Catch:{ all -> 0x021a }
        r3 = "DGG";	 Catch:{ all -> 0x021a }
        r2 = r2.equals(r3);	 Catch:{ all -> 0x021a }
        if (r2 == 0) goto L_0x05fb;	 Catch:{ all -> 0x021a }
    L_0x0510:
        r2 = "4";	 Catch:{ all -> 0x021a }
        r2 = r2.equals(r12);	 Catch:{ all -> 0x021a }
        if (r2 != 0) goto L_0x0520;	 Catch:{ all -> 0x021a }
    L_0x0518:
        r2 = "3";	 Catch:{ all -> 0x021a }
        r2 = r2.equals(r12);	 Catch:{ all -> 0x021a }
        if (r2 == 0) goto L_0x052a;	 Catch:{ all -> 0x021a }
    L_0x0520:
        r0 = r22;	 Catch:{ all -> 0x021a }
        r2 = r0.mPhone;	 Catch:{ all -> 0x021a }
        r2 = r2.getPhoneId();	 Catch:{ all -> 0x021a }
        if (r2 == 0) goto L_0x0538;	 Catch:{ all -> 0x021a }
    L_0x052a:
        r2 = "1";	 Catch:{ all -> 0x021a }
        r3 = "ril.CTCDUAL2";	 Catch:{ all -> 0x021a }
        r3 = android.os.SystemProperties.get(r3);	 Catch:{ all -> 0x021a }
        r2 = r2.equals(r3);	 Catch:{ all -> 0x021a }
        if (r2 == 0) goto L_0x059d;	 Catch:{ all -> 0x021a }
    L_0x0538:
        r2 = "live.vodafone.com";	 Catch:{ all -> 0x021a }
        r2 = r2.equalsIgnoreCase(r8);	 Catch:{ all -> 0x021a }
        if (r2 != 0) goto L_0x0550;	 Catch:{ all -> 0x021a }
    L_0x0540:
        r2 = "CTWAP";	 Catch:{ all -> 0x021a }
        r2 = r2.equalsIgnoreCase(r8);	 Catch:{ all -> 0x021a }
        if (r2 != 0) goto L_0x0550;	 Catch:{ all -> 0x021a }
    L_0x0548:
        r2 = "ctlte";	 Catch:{ all -> 0x021a }
        r2 = r2.equalsIgnoreCase(r8);	 Catch:{ all -> 0x021a }
        if (r2 == 0) goto L_0x059d;	 Catch:{ all -> 0x021a }
    L_0x0550:
        r2 = "true";	 Catch:{ all -> 0x021a }
        r3 = "gsm.operator.isroaming";	 Catch:{ all -> 0x021a }
        r0 = r22;	 Catch:{ all -> 0x021a }
        r4 = r0.mPhone;	 Catch:{ all -> 0x021a }
        r6 = r4.getSubId();	 Catch:{ all -> 0x021a }
        r4 = "";	 Catch:{ all -> 0x021a }
        r3 = android.telephony.TelephonyManager.getTelephonyProperty(r3, r6, r4);	 Catch:{ all -> 0x021a }
        r2 = r2.equals(r3);	 Catch:{ all -> 0x021a }
        if (r2 == 0) goto L_0x059d;	 Catch:{ all -> 0x021a }
    L_0x0568:
        r2 = "46003";	 Catch:{ all -> 0x021a }
        r3 = "gsm.sim.operator.numeric";	 Catch:{ all -> 0x021a }
        r0 = r22;	 Catch:{ all -> 0x021a }
        r4 = r0.mPhone;	 Catch:{ all -> 0x021a }
        r6 = r4.getSubId();	 Catch:{ all -> 0x021a }
        r4 = "";	 Catch:{ all -> 0x021a }
        r3 = android.telephony.TelephonyManager.getTelephonyProperty(r3, r6, r4);	 Catch:{ all -> 0x021a }
        r2 = r2.equals(r3);	 Catch:{ all -> 0x021a }
        if (r2 != 0) goto L_0x059d;	 Catch:{ all -> 0x021a }
    L_0x0580:
        r2 = new java.lang.StringBuilder;	 Catch:{ all -> 0x021a }
        r2.<init>();	 Catch:{ all -> 0x021a }
        r3 = "skip live.vodafone.com or CTWAP or ctlte, apn=";	 Catch:{ all -> 0x021a }
        r2 = r2.append(r3);	 Catch:{ all -> 0x021a }
        r2 = r2.append(r8);	 Catch:{ all -> 0x021a }
        r2 = r2.toString();	 Catch:{ all -> 0x021a }
        r0 = r22;	 Catch:{ all -> 0x021a }
        r0.log(r2);	 Catch:{ all -> 0x021a }
        r10.moveToNext();	 Catch:{ all -> 0x021a }
        goto L_0x0204;	 Catch:{ all -> 0x021a }
    L_0x059d:
        r2 = "1";	 Catch:{ all -> 0x021a }
        r3 = "ril.CTCDUAL2";	 Catch:{ all -> 0x021a }
        r3 = android.os.SystemProperties.get(r3);	 Catch:{ all -> 0x021a }
        r2 = r2.equals(r3);	 Catch:{ all -> 0x021a }
        if (r2 == 0) goto L_0x05fb;	 Catch:{ all -> 0x021a }
    L_0x05ab:
        r0 = r22;	 Catch:{ all -> 0x021a }
        r2 = r0.mPhone;	 Catch:{ all -> 0x021a }
        r2 = r2.getPhoneId();	 Catch:{ all -> 0x021a }
        r3 = 1;	 Catch:{ all -> 0x021a }
        if (r2 != r3) goto L_0x05fb;	 Catch:{ all -> 0x021a }
    L_0x05b6:
        r2 = "true";	 Catch:{ all -> 0x021a }
        r3 = "gsm.operator.isroaming";	 Catch:{ all -> 0x021a }
        r0 = r22;	 Catch:{ all -> 0x021a }
        r4 = r0.mPhone;	 Catch:{ all -> 0x021a }
        r6 = r4.getSubId();	 Catch:{ all -> 0x021a }
        r4 = "";	 Catch:{ all -> 0x021a }
        r3 = android.telephony.TelephonyManager.getTelephonyProperty(r3, r6, r4);	 Catch:{ all -> 0x021a }
        r2 = r2.equals(r3);	 Catch:{ all -> 0x021a }
        if (r2 != 0) goto L_0x05fb;	 Catch:{ all -> 0x021a }
    L_0x05ce:
        r2 = "live.vodafone.com";	 Catch:{ all -> 0x021a }
        r2 = r2.equalsIgnoreCase(r8);	 Catch:{ all -> 0x021a }
        if (r2 != 0) goto L_0x05de;	 Catch:{ all -> 0x021a }
    L_0x05d6:
        r2 = "ctlte";	 Catch:{ all -> 0x021a }
        r2 = r2.equalsIgnoreCase(r8);	 Catch:{ all -> 0x021a }
        if (r2 == 0) goto L_0x05fb;	 Catch:{ all -> 0x021a }
    L_0x05de:
        r2 = new java.lang.StringBuilder;	 Catch:{ all -> 0x021a }
        r2.<init>();	 Catch:{ all -> 0x021a }
        r3 = "slot2 CTC card(HOME) - skip live.vodafone.com or ctlte, apn=";	 Catch:{ all -> 0x021a }
        r2 = r2.append(r3);	 Catch:{ all -> 0x021a }
        r2 = r2.append(r8);	 Catch:{ all -> 0x021a }
        r2 = r2.toString();	 Catch:{ all -> 0x021a }
        r0 = r22;	 Catch:{ all -> 0x021a }
        r0.log(r2);	 Catch:{ all -> 0x021a }
        r10.moveToNext();	 Catch:{ all -> 0x021a }
        goto L_0x0204;	 Catch:{ all -> 0x021a }
    L_0x05fb:
        r2 = new java.lang.StringBuilder;	 Catch:{ all -> 0x021a }
        r2.<init>();	 Catch:{ all -> 0x021a }
        r3 = "setSelectedApn: id=";	 Catch:{ all -> 0x021a }
        r2 = r2.append(r3);	 Catch:{ all -> 0x021a }
        r2 = r2.append(r13);	 Catch:{ all -> 0x021a }
        r3 = ", selectedId=";	 Catch:{ all -> 0x021a }
        r2 = r2.append(r3);	 Catch:{ all -> 0x021a }
        r0 = r19;	 Catch:{ all -> 0x021a }
        r2 = r2.append(r0);	 Catch:{ all -> 0x021a }
        r3 = ", apn=";	 Catch:{ all -> 0x021a }
        r2 = r2.append(r3);	 Catch:{ all -> 0x021a }
        r2 = r2.append(r8);	 Catch:{ all -> 0x021a }
        r3 = ", type=";	 Catch:{ all -> 0x021a }
        r2 = r2.append(r3);	 Catch:{ all -> 0x021a }
        r0 = r20;	 Catch:{ all -> 0x021a }
        r2 = r2.append(r0);	 Catch:{ all -> 0x021a }
        r3 = ", name=";	 Catch:{ all -> 0x021a }
        r2 = r2.append(r3);	 Catch:{ all -> 0x021a }
        r2 = r2.append(r15);	 Catch:{ all -> 0x021a }
        r3 = ", numeric=";	 Catch:{ all -> 0x021a }
        r2 = r2.append(r3);	 Catch:{ all -> 0x021a }
        r0 = r17;	 Catch:{ all -> 0x021a }
        r2 = r2.append(r0);	 Catch:{ all -> 0x021a }
        r3 = ", network_flag=";	 Catch:{ all -> 0x021a }
        r2 = r2.append(r3);	 Catch:{ all -> 0x021a }
        r0 = r16;	 Catch:{ all -> 0x021a }
        r2 = r2.append(r0);	 Catch:{ all -> 0x021a }
        r2 = r2.toString();	 Catch:{ all -> 0x021a }
        r0 = r22;	 Catch:{ all -> 0x021a }
        r0.log(r2);	 Catch:{ all -> 0x021a }
        if (r20 == 0) goto L_0x0772;	 Catch:{ all -> 0x021a }
    L_0x0659:
        r2 = "*";	 Catch:{ all -> 0x021a }
        r0 = r20;	 Catch:{ all -> 0x021a }
        r2 = r0.contains(r2);	 Catch:{ all -> 0x021a }
        if (r2 != 0) goto L_0x0673;	 Catch:{ all -> 0x021a }
    L_0x0663:
        r2 = "default";	 Catch:{ all -> 0x021a }
        r0 = r20;	 Catch:{ all -> 0x021a }
        r2 = r0.contains(r2);	 Catch:{ all -> 0x021a }
        if (r2 != 0) goto L_0x0673;	 Catch:{ all -> 0x021a }
    L_0x066d:
        r2 = android.text.TextUtils.isEmpty(r20);	 Catch:{ all -> 0x021a }
        if (r2 == 0) goto L_0x0772;
    L_0x0673:
        if (r14 != 0) goto L_0x0676;
    L_0x0675:
        r14 = r13;
    L_0x0676:
        if (r13 <= r14) goto L_0x076f;
    L_0x0678:
        r19 = r13;
    L_0x067a:
        r2 = "Exit setSelectedApn(), finally";
        r0 = r22;
        r0.log(r2);
        if (r10 == 0) goto L_0x0686;
    L_0x0683:
        r10.close();
    L_0x0686:
        if (r19 != 0) goto L_0x0777;
    L_0x0688:
        if (r11 == 0) goto L_0x0777;
    L_0x068a:
        r2 = new java.lang.StringBuilder;
        r2.<init>();
        r3 = "select apn : ";
        r2 = r2.append(r3);
        r2 = r2.append(r11);
        r2 = r2.toString();
        r0 = r22;
        r0.log(r2);
        r19 = r11;
    L_0x06a4:
        r0 = r22;
        r2 = r0.mPhone;
        r2 = r2.getContext();
        r18 = r2.getContentResolver();
        r21 = new android.content.ContentValues;
        r21.<init>();
        r2 = "apn_id";
        r3 = java.lang.Integer.valueOf(r19);
        r0 = r21;
        r0.put(r2, r3);
        r2 = "CG";
        r3 = "DGG";
        r2 = r2.equals(r3);
        if (r2 != 0) goto L_0x06f2;
    L_0x06ca:
        r2 = "DCGG";
        r3 = "DGG";
        r2 = r2.equals(r3);
        if (r2 != 0) goto L_0x06f2;
    L_0x06d4:
        r2 = "DCGGS";
        r3 = "DGG";
        r2 = r2.equals(r3);
        if (r2 != 0) goto L_0x06f2;
    L_0x06de:
        r2 = "DCGS";
        r3 = "DGG";
        r2 = r2.equals(r3);
        if (r2 != 0) goto L_0x06f2;
    L_0x06e8:
        r2 = "GG";
        r3 = "DGG";
        r2 = r2.equals(r3);
        if (r2 == 0) goto L_0x076e;
    L_0x06f2:
        if (r19 == 0) goto L_0x076e;
    L_0x06f4:
        r2 = new java.lang.StringBuilder;
        r2.<init>();
        r3 = "setSelectedApnKey, set selectedId=";
        r2 = r2.append(r3);
        r0 = r19;
        r2 = r2.append(r0);
        r2 = r2.toString();
        r0 = r22;
        r0.log(r2);
        r2 = android.telephony.TelephonyManager.getDefault();
        r0 = r22;
        r3 = r0.mPhone;
        r6 = r3.getSubId();
        r2 = r2.getNetworkType(r6);
        r3 = 13;
        if (r2 == r3) goto L_0x0736;
    L_0x0722:
        r2 = android.telephony.TelephonyManager.getDefault();
        r0 = r22;
        r3 = r0.mPhone;
        r6 = r3.getSubId();
        r2 = r2.getNetworkType(r6);
        r3 = 14;
        if (r2 != r3) goto L_0x0781;
    L_0x0736:
        r2 = new java.lang.StringBuilder;
        r2.<init>();
        r3 = PREFERAPN_URI_USING_SUBID;
        r3 = r3.toString();
        r2 = r2.append(r3);
        r3 = "4G";
        r2 = r2.append(r3);
        r2 = r2.toString();
        r2 = android.net.Uri.parse(r2);
        r3 = 0;
        r4 = 0;
        r0 = r18;
        r1 = r21;
        r0.update(r2, r1, r3, r4);
    L_0x075c:
        r0 = r22;
        r2 = r0.mPhone;
        r2 = r2.getContext();
        r3 = new android.content.Intent;
        r4 = "android.intent.action.UPDATE_CURRENT_CARRIER_DONE";
        r3.<init>(r4);
        r2.sendBroadcast(r3);
    L_0x076e:
        return;
    L_0x076f:
        if (r11 != 0) goto L_0x0772;
    L_0x0771:
        r11 = r13;
    L_0x0772:
        r10.moveToNext();	 Catch:{ all -> 0x021a }
        goto L_0x0204;
    L_0x0777:
        if (r19 != 0) goto L_0x06a4;
    L_0x0779:
        r2 = "no available next apn, return";
        r0 = r22;
        r0.log(r2);
        goto L_0x076e;
    L_0x0781:
        r2 = new java.lang.StringBuilder;
        r2.<init>();
        r3 = PREFERAPN_URI_USING_SUBID;
        r3 = r3.toString();
        r2 = r2.append(r3);
        r0 = r22;
        r3 = r0.mPhone;
        r6 = r3.getSubId();
        r2 = r2.append(r6);
        r2 = r2.toString();
        r2 = android.net.Uri.parse(r2);
        r3 = 0;
        r4 = 0;
        r0 = r18;
        r1 = r21;
        r0.update(r2, r1, r3, r4);
        goto L_0x075c;
        */
        throw new UnsupportedOperationException("Method not decompiled: com.android.internal.telephony.dataconnection.DcTrackerBase.setSelectedApn():void");
    }

    protected abstract void setState(State state);

    static {
        try {
            System.loadLibrary("sbmcgm-jni");
            Rlog.i(LOG_TAG, "sbmcgm library loaded");
        } catch (UnsatisfiedLinkError e) {
            Rlog.i(LOG_TAG, "sbmcgm library not found!");
        }
    }

    protected int getInitialMaxRetry() {
        if (this.mFailFast) {
            return 0;
        }
        return Global.getInt(this.mResolver, "mdc_initial_max_retry", SystemProperties.getInt("mdc_initial_max_retry", 1));
    }

    protected void onActionIntentReconnectAlarm(Intent intent) {
        String reason = intent.getStringExtra(INTENT_RECONNECT_ALARM_EXTRA_REASON);
        String apnType = intent.getStringExtra(INTENT_RECONNECT_ALARM_EXTRA_TYPE);
        log("onActionIntentReconnectAlarm: currSubId = " + intent.getLongExtra("subscription", -1000) + " phoneSubId=" + this.mPhone.getSubId());
        ApnContext apnContext = (ApnContext) this.mApnContexts.get(apnType);
        log("onActionIntentReconnectAlarm: mState=" + this.mState + " reason=" + reason + " apnType=" + apnType + " apnContext=" + apnContext + " mDataConnectionAsyncChannels=" + this.mDataConnectionAcHashMap);
        if (apnContext != null && apnContext.isEnabled()) {
            apnContext.setReason(reason);
            State apnContextState = apnContext.getState();
            log("onActionIntentReconnectAlarm: apnContext state=" + apnContextState);
            if (apnContextState == State.FAILED || apnContextState == State.IDLE) {
                log("onActionIntentReconnectAlarm: state is FAILED|IDLE, disassociate");
                DcAsyncChannel dcac = apnContext.getDcAc();
                if (dcac != null) {
                    dcac.tearDown(apnContext, "", null);
                }
                apnContext.setDataConnectionAc(null);
                apnContext.setState(State.IDLE);
            } else {
                log("onActionIntentReconnectAlarm: keep associated");
            }
            sendMessage(obtainMessage(270339, apnContext));
            apnContext.setReconnectIntent(null);
        }
    }

    protected void onActionIntentRestartTrySetupAlarm(Intent intent) {
        String apnType = intent.getStringExtra(INTENT_RESTART_TRYSETUP_ALARM_EXTRA_TYPE);
        ApnContext apnContext = (ApnContext) this.mApnContexts.get(apnType);
        log("onActionIntentRestartTrySetupAlarm: mState=" + this.mState + " apnType=" + apnType + " apnContext=" + apnContext + " mDataConnectionAsyncChannels=" + this.mDataConnectionAcHashMap);
        sendMessage(obtainMessage(270339, apnContext));
    }

    protected void onActionIntentDataStallAlarm(Intent intent) {
        log("onActionIntentDataStallAlarm: action=" + intent.getAction());
        Message msg = obtainMessage(270353, intent.getAction());
        msg.arg1 = intent.getIntExtra(DATA_STALL_ALARM_TAG_EXTRA, 0);
        sendMessage(msg);
    }

    protected DcTrackerBase(PhoneBase phone) {
        this.mPhone = phone;
        log("DCT.constructor");
        this.mResolver = this.mPhone.getContext().getContentResolver();
        this.mUiccController = UiccController.getInstance();
        this.mUiccController.registerForIccChanged(this, 270369, null);
        this.mAlarmManager = (AlarmManager) this.mPhone.getContext().getSystemService("alarm");
        this.mCm = (ConnectivityManager) this.mPhone.getContext().getSystemService("connectivity");
        IntentFilter filter = new IntentFilter();
        filter.addAction("android.intent.action.SCREEN_ON");
        filter.addAction("android.intent.action.SCREEN_OFF");
        filter.addAction("android.net.wifi.STATE_CHANGE");
        filter.addAction("android.net.wifi.WIFI_STATE_CHANGED");
        filter.addAction(INTENT_DATA_STALL_ALARM);
        filter.addAction(INTENT_PROVISIONING_APN_ALARM);
        if (this.mPhone.getPhoneId() == 0) {
            filter.addAction(INTENT_PREFERED_DATA_1);
            filter.addAction(INTENT_GET_DATA_ENABLED);
            filter.addAction(INTENT_SET_DATA_ENABLED);
            filter.addAction(INTENT_GET_DATA_PREFERRED);
            filter.addAction(INTENT_SET_DATA_PREFERRED);
        }
        if (this.mPhone.getPhoneId() == 1) {
            filter.addAction(INTENT_PREFERED_DATA_2);
        }
        filter.addAction("android.intent.action.SET_POLICY_DATA_ENABLE");
        if (CscFeature.getInstance().getEnableStatus("CscFeature_RIL_PromptToDataRoam")) {
            filter.addAction("android.intent.action.ACTION_DATA_SELECTION_POPUP_PRESSED");
        }
        if ("LGT".equals("")) {
            filter.addAction("android.intent.action.AIRPLANE_MODE");
        }
        filter.addAction(PDP_RESET_TEST);
        if ("SKT".equals("")) {
            isFotaMode();
        }
        if ("ALL_UNIFIED_CONTROL".equals("ALL_UNIFIED_CONTROL")) {
            this.mUserDataEnabled = Global.getInt(this.mPhone.getContext().getContentResolver(), "mobile_data", 1) == 1;
        } else {
            this.mUserDataEnabled = Global.getInt(this.mPhone.getContext().getContentResolver(), TelephonyManager.appendId("mobile_data", (long) SubscriptionController.getInstance().getSlotId(this.mPhone.getSubId())), 1) == 1;
        }
        if (isDomesticModel()) {
            filter.addAction("android.intent.action.SET_DEPENDENCY_MET");
        }
        this.mPhone.getContext().registerReceiver(this.mIntentReceiver, filter, null, this.mPhone);
        this.mDataEnabled[0] = SystemProperties.getBoolean(DEFALUT_DATA_ON_BOOT_PROP, true);
        if (this.mDataEnabled[0]) {
            this.mEnabledCount++;
        }
        this.mAutoAttachOnCreation = PreferenceManager.getDefaultSharedPreferences(this.mPhone.getContext()).getBoolean(PhoneBase.DATA_DISABLED_ON_BOOT_KEY, false);
        this.mDataRoamingSettingObserver = new DataRoamingSettingObserver(this.mPhone, this.mPhone.getContext());
        this.mDataRoamingSettingObserver.register();
        this.mMobileDataSettingObserver = new MobileDataSettingObserver(this.mPhone, this.mPhone.getContext());
        if ("ALL_UNIFIED_CONTROL".equals("ALL_UNIFIED_CONTROL")) {
            this.mMobileDataSettingObserver.register();
        }
        this.mLteRoamingEnableObserver = new LteRoamingEnableObserver(this);
        this.mPhone.getContext().getContentResolver().registerContentObserver(System.getUriFor("lte_roaming_mode_on"), false, this.mLteRoamingEnableObserver);
        this.mVoLteSettingObserver = new VoLteSettingObserver(this.mPhone, this.mPhone.getContext());
        HandlerThread handlerThread = new HandlerThread("DcHandlerThread");
        handlerThread.start();
        Handler dcHandler = new Handler(handlerThread.getLooper());
        this.mDcc = DcController.makeDcc(this.mPhone, this, dcHandler);
        this.mDcTesterFailBringUpAll = new DcTesterFailBringUpAll(this.mPhone, dcHandler);
        this.mApnTypesAllowedOnDataDisabled = new ArrayList();
        try {
            this.mApnTypesAllowedOnDataDisabled.addAll(Arrays.asList(this.mPhone.getContext().getResources().getStringArray(17236037)));
        } catch (NotFoundException e) {
            log("Allowed on Data Disabled list is not found");
        }
        if (CscFeature.getInstance().getEnableStatus("CscFeature_RIL_ForceConnectMMS")) {
            this.mApnTypesAllowedOnDataDisabled.add("mms");
            if (("DCGG".equals("DGG") || "DGG".equals("DGG") || "DCGS".equals("DGG") || "DCGGS".equals("DGG")) && this.mPhone.getPhoneId() == 1) {
                this.mApnTypesAllowedOnDataDisabled.add("mms2");
            }
        }
        if (CscFeature.getInstance().getEnableStatus("CscFeature_RIL_ForceConnectIMS")) {
            this.mApnTypesAllowedOnDataDisabled.add("ims");
        }
        this.mConditions4AlwaysOnApns = new HashMap();
        String alwaysOnApns = CscFeature.getInstance().getString("CscFeature_RIL_ConfigAlwaysOnApn");
        if (!TextUtils.isEmpty(alwaysOnApns)) {
            for (String arg : alwaysOnApns.split(",")) {
                String[] options = arg.split("_");
                if (options.length <= 2) {
                    String apnType = options[0].trim();
                    int apnTypeId = apnTypeToId(apnType);
                    if (!(apnTypeId == 0 || apnTypeId == -1)) {
                        this.mApnTypesAllowedOnDataDisabled.add(apnType);
                        String logStr = "add " + apnType + " type into mApnTypeAllowedOnDataDisabled";
                        if (options.length == 2) {
                            Set options4ApnType = (Set) this.mConditions4AlwaysOnApns.get(apnType);
                            if (options4ApnType == null) {
                                options4ApnType = new HashSet();
                                this.mConditions4AlwaysOnApns.put(apnType, options4ApnType);
                            }
                            String option = options[1].trim();
                            options4ApnType.add(option);
                            logStr = logStr + " with " + option + " option";
                        }
                        logStr + " by CscFeature";
                    }
                }
            }
        }
    }

    public void dispose() {
        log("DCT.dispose");
        for (DcAsyncChannel dcac : this.mDataConnectionAcHashMap.values()) {
            dcac.disconnect();
        }
        this.mDataConnectionAcHashMap.clear();
        this.mIsDisposed = true;
        this.mPhone.getContext().unregisterReceiver(this.mIntentReceiver);
        this.mUiccController.unregisterForIccChanged(this);
        this.mDataRoamingSettingObserver.unregister();
        if ("ALL_UNIFIED_CONTROL".equals("ALL_UNIFIED_CONTROL")) {
            this.mMobileDataSettingObserver.unregister();
        }
        this.mPhone.getContext().getContentResolver().unregisterContentObserver(this.mLteRoamingEnableObserver);
        this.mDcc.dispose();
        this.mDcTesterFailBringUpAll.dispose();
    }

    public Activity getActivity() {
        return this.mActivity;
    }

    void setActivity(Activity activity) {
        log("setActivity = " + activity);
        this.mActivity = activity;
        this.mPhone.notifyDataActivity();
    }

    public boolean isApnTypeActive(String type) {
        if ("dun".equals(type)) {
            ApnSetting dunApn = fetchDunApn();
            if (dunApn != null) {
                if (this.mActiveApn == null || !dunApn.toString().equals(this.mActiveApn.toString())) {
                    return false;
                }
                return true;
            }
        }
        if (this.mActiveApn == null || !this.mActiveApn.canHandleType(type)) {
            return false;
        }
        return true;
    }

    protected boolean IncludeFixedApn(String requestedApnType) {
        String ConfigFixedApn = CscFeature.getInstance().getString("CscFeature_RIL_ConfigFixedApn");
        if (TextUtils.isEmpty(ConfigFixedApn)) {
            log("IncludeFixedApn : ConfigFixedApn is empty! return..");
            return false;
        }
        String[] types = ConfigFixedApn.split(",");
        for (String trim : types) {
            if (requestedApnType.equals(trim.trim())) {
                return true;
            }
        }
        return false;
    }

    protected ApnSetting fetchDunApn() {
        Iterator i$;
        if (IncludeFixedApn("dun") && !(("TGY".equals(SystemProperties.get("ro.csc.sales_code")) && "45403".equals(SystemProperties.get("gsm.sim.operator.numeric", "none"))) || this.mAllApnSettings == null)) {
            i$ = this.mAllApnSettings.iterator();
            while (i$.hasNext()) {
                ApnSetting apn = (ApnSetting) i$.next();
                if (apn.equalsType("dun")) {
                    log("apn info : " + apn.toString());
                    return apn;
                }
            }
        }
        if (SystemProperties.getBoolean("net.tethering.noprovisioning", false)) {
            log("fetchDunApn: net.tethering.noprovisioning=true ret: null");
            return null;
        }
        int bearer = -1;
        Context c = this.mPhone.getContext();
        for (ApnSetting dunSetting : ApnSetting.arrayFromString(Global.getString(c.getContentResolver(), "tether_dun_apn"))) {
            IccRecords r = (IccRecords) this.mIccRecords.get();
            String operator = r != null ? r.getOperatorNumeric() : "";
            if (dunSetting.bearer != 0) {
                if (bearer == -1) {
                    bearer = this.mPhone.getServiceState().getRilDataRadioTechnology();
                }
                if (dunSetting.bearer != bearer) {
                    continue;
                }
            }
            if (!dunSetting.numeric.equals(operator)) {
                continue;
            } else if (!dunSetting.hasMvnoParams()) {
                return dunSetting;
            } else {
                if (r != null && mvnoMatches(r, dunSetting.mvnoType, dunSetting.mvnoMatchData)) {
                    return dunSetting;
                }
            }
        }
        return ApnSetting.fromString(c.getResources().getString(17039385));
    }

    protected ApnSetting fetchApn(String apnType) {
        if (!(this.mAllApnSettings == null || apnType == null)) {
            Iterator i$ = this.mAllApnSettings.iterator();
            while (i$.hasNext()) {
                ApnSetting apn = (ApnSetting) i$.next();
                if (apn.equalsType(apnType)) {
                    log("apn info : " + apn.toString());
                    return apn;
                }
            }
        }
        return null;
    }

    protected ApnSetting fetchBipApn() {
        SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(this.mPhone.getContext());
        if (pref == null) {
            log("fetchBipApn: there is no default preferences");
            return null;
        }
        if (pref.getBoolean("bip.pref.enable", false)) {
            String apnName = pref.getString("bip.pref.apn", "");
            String user = pref.getString("bip.pref.user", "");
            String passwd = pref.getString("bip.pref.passwd", "");
            String proxy = pref.getString("bip.pref.proxy", "");
            String protocol = pref.getString("bip.pref.protocol", "");
            String roamingProtocol = pref.getString("bip.pref.roaming_protocol", "");
            String[] types = new String[]{"bip"};
            if (TextUtils.equals("", protocol)) {
                protocol = "IP";
            }
            if (TextUtils.equals("", roamingProtocol)) {
                roamingProtocol = "IP";
            }
            ApnSetting apn = new ApnSetting(-1, "", "BipApn", apnName, proxy, "", "", "", "", user, passwd, 0, types, protocol, roamingProtocol, true, 0, 0, false, 0, 0, 0, 0, "", "");
            log("fetchBipApn:" + apn.toString() + "]");
            return apn;
        }
        log("fetchBipApn: BIP apn is not enabled");
        return null;
    }

    public String[] getActiveApnTypes() {
        if (this.mActiveApn != null) {
            return this.mActiveApn.types;
        }
        return new String[]{"default"};
    }

    public String getActiveApnString(String apnType) {
        if (this.mActiveApn != null) {
            return this.mActiveApn.apn;
        }
        return null;
    }

    public void setDataOnRoamingEnabled(boolean enabled) {
        int i = 1;
        if (getDataOnRoamingEnabled() != enabled) {
            ContentResolver resolver = this.mPhone.getContext().getContentResolver();
            String str;
            if ("ALL_UNIFIED_CONTROL".equals("ALL_UNIFIED_CONTROL")) {
                str = "data_roaming";
                if (!enabled) {
                    i = 0;
                }
                Global.putInt(resolver, str, i);
                return;
            }
            str = TelephonyManager.appendId("data_roaming", (long) SubscriptionController.getInstance().getSlotId(this.mPhone.getSubId()));
            if (!enabled) {
                i = 0;
            }
            Global.putInt(resolver, str, i);
        }
    }

    public boolean getDataOnRoamingEnabled() {
        if ("DCGG".equals("DGG") || "DCGS".equals("DGG") || "DCGGS".equals("DGG")) {
            return true;
        }
        if ("ALL_UNIFIED_CONTROL".equals("ALL_UNIFIED_CONTROL")) {
            if (Global.getInt(this.mResolver, "data_roaming", 0) != 1) {
                return false;
            }
            return true;
        } else if (Global.getInt(this.mResolver, TelephonyManager.appendId("data_roaming", (long) SubscriptionController.getInstance().getSlotId(this.mPhone.getSubId())), 0) != 1) {
            return false;
        } else {
            return true;
        }
    }

    public void setLteDataOnRoamingEnabled(boolean enabled) {
        System.putInt(this.mResolver, "lte_roaming_mode_on", enabled ? 1 : 0);
    }

    public boolean getLteDataOnRoamingEnabled() {
        return System.getInt(this.mResolver, "lte_roaming_mode_on", 0) == 1;
    }

    public void setDataEnabled(boolean enable) {
        Message msg = obtainMessage(270366);
        msg.arg1 = enable ? 1 : 0;
        sendMessage(msg);
    }

    public boolean getDataEnabled() {
        try {
            ContentResolver resolver = this.mPhone.getContext().getContentResolver();
            if (isDomesticModel() && this.mPhone.getServiceState().getRoaming()) {
                if (Global.getInt(resolver, "data_roaming", 1) == 1) {
                    return true;
                }
                return false;
            } else if (Global.getInt(resolver, "mobile_data") == 0) {
                return false;
            } else {
                return true;
            }
        } catch (SettingNotFoundException e) {
            return false;
        }
    }

    /* JADX WARNING: inconsistent code. */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    public java.lang.String getSelectedApn() {
        /*
        r21 = this;
        r2 = "ril.ICC_TYPE";
        r0 = r21;
        r3 = r0.mPhone;
        r6 = r3.getSubId();
        r3 = "0";
        r14 = android.telephony.TelephonyManager.getTelephonyProperty(r2, r6, r3);
        r2 = new java.lang.StringBuilder;
        r2.<init>();
        r3 = "Enter getSelectedApn(), iccType=";
        r2 = r2.append(r3);
        r2 = r2.append(r14);
        r2 = r2.toString();
        r0 = r21;
        r0.log(r2);
        r11 = 0;
        r15 = 0;
        r16 = 0;
        r8 = 0;
        r20 = 0;
        r18 = 0;
        r17 = 0;
        r5 = "";
        r19 = 0;
        r12 = 0;
        r13 = 0;
        r2 = "CG";
        r3 = "DGG";
        r2 = r2.equals(r3);
        if (r2 != 0) goto L_0x006b;
    L_0x0043:
        r2 = "DCG";
        r3 = "DGG";
        r2 = r2.equals(r3);
        if (r2 != 0) goto L_0x006b;
    L_0x004d:
        r2 = "DCGS";
        r3 = "DGG";
        r2 = r2.equals(r3);
        if (r2 != 0) goto L_0x006b;
    L_0x0057:
        r2 = "DCGG";
        r3 = "DGG";
        r2 = r2.equals(r3);
        if (r2 != 0) goto L_0x006b;
    L_0x0061:
        r2 = "DCGGS";
        r3 = "DGG";
        r2 = r2.equals(r3);
        if (r2 == 0) goto L_0x032a;
    L_0x006b:
        r2 = new java.lang.StringBuilder;
        r2.<init>();
        r3 = "numeric=\"";
        r2 = r2.append(r3);
        r3 = "gsm.sim.operator.numeric";
        r0 = r21;
        r4 = r0.mPhone;
        r6 = r4.getSubId();
        r4 = "";
        r3 = android.telephony.TelephonyManager.getTelephonyProperty(r3, r6, r4);
        r2 = r2.append(r3);
        r3 = "\"";
        r2 = r2.append(r3);
        r5 = r2.toString();
    L_0x0094:
        r2 = "CG";
        r3 = "DGG";
        r2 = r2.equals(r3);
        if (r2 != 0) goto L_0x00c6;
    L_0x009e:
        r2 = "DCGGS";
        r3 = "DGG";
        r2 = r2.equals(r3);
        if (r2 != 0) goto L_0x00c6;
    L_0x00a8:
        r2 = "DCGS";
        r3 = "DGG";
        r2 = r2.equals(r3);
        if (r2 != 0) goto L_0x00c6;
    L_0x00b2:
        r2 = "DCGG";
        r3 = "DGG";
        r2 = r2.equals(r3);
        if (r2 != 0) goto L_0x00c6;
    L_0x00bc:
        r2 = "GG";
        r3 = "DGG";
        r2 = r2.equals(r3);
        if (r2 == 0) goto L_0x0135;
    L_0x00c6:
        r2 = android.telephony.TelephonyManager.getDefault();
        r0 = r21;
        r3 = r0.mPhone;
        r6 = r3.getSubId();
        r2 = r2.getNetworkType(r6);
        r3 = 13;
        if (r2 == r3) goto L_0x00ee;
    L_0x00da:
        r2 = android.telephony.TelephonyManager.getDefault();
        r0 = r21;
        r3 = r0.mPhone;
        r6 = r3.getSubId();
        r2 = r2.getNetworkType(r6);
        r3 = 14;
        if (r2 != r3) goto L_0x034d;
    L_0x00ee:
        r0 = r21;
        r2 = r0.mPhone;
        r2 = r2.getContext();
        r2 = r2.getContentResolver();
        r3 = new java.lang.StringBuilder;
        r3.<init>();
        r4 = PREFERAPN_URI_USING_SUBID;
        r4 = r4.toString();
        r3 = r3.append(r4);
        r4 = "4G";
        r3 = r3.append(r4);
        r3 = r3.toString();
        r3 = android.net.Uri.parse(r3);
        r4 = 4;
        r4 = new java.lang.String[r4];
        r6 = 0;
        r7 = "_id";
        r4[r6] = r7;
        r6 = 1;
        r7 = "name";
        r4[r6] = r7;
        r6 = 2;
        r7 = "apn";
        r4[r6] = r7;
        r6 = 3;
        r7 = "numeric";
        r4[r6] = r7;
        r6 = 0;
        r7 = "name ASC";
        r11 = r2.query(r3, r4, r5, r6, r7);
    L_0x0135:
        r2 = new java.lang.StringBuilder;
        r2.<init>();
        r3 = "getSelectedApn: cursor=";
        r2 = r2.append(r3);
        r2 = r2.append(r11);
        r3 = " cursor.count=";
        r3 = r2.append(r3);
        if (r11 == 0) goto L_0x039c;
    L_0x014c:
        r2 = r11.getCount();
    L_0x0150:
        r2 = r3.append(r2);
        r2 = r2.toString();
        r0 = r21;
        r0.log(r2);
        if (r11 == 0) goto L_0x01cc;
    L_0x015f:
        r2 = r11.getCount();
        if (r2 <= 0) goto L_0x01cc;
    L_0x0165:
        r11.moveToFirst();
        r2 = "_id";
        r2 = r11.getColumnIndexOrThrow(r2);
        r19 = r11.getString(r2);
        r2 = "apn";
        r2 = r11.getColumnIndexOrThrow(r2);
        r8 = r11.getString(r2);
        r2 = "name";
        r2 = r11.getColumnIndexOrThrow(r2);
        r16 = r11.getString(r2);
        r2 = "numeric";
        r2 = r11.getColumnIndexOrThrow(r2);
        r18 = r11.getString(r2);
        r2 = new java.lang.StringBuilder;
        r2.<init>();
        r3 = "getSelectedApn: id=";
        r2 = r2.append(r3);
        r0 = r19;
        r2 = r2.append(r0);
        r3 = ", apn=";
        r2 = r2.append(r3);
        r2 = r2.append(r8);
        r3 = ", name=";
        r2 = r2.append(r3);
        r0 = r16;
        r2 = r2.append(r0);
        r3 = ", numeric=";
        r2 = r2.append(r3);
        r0 = r18;
        r2 = r2.append(r0);
        r2 = r2.toString();
        r0 = r21;
        r0.log(r2);
    L_0x01cc:
        if (r11 == 0) goto L_0x01d1;
    L_0x01ce:
        r11.close();
    L_0x01d1:
        r0 = r21;
        r2 = r0.mPhone;
        r2 = r2.getContext();
        r2 = r2.getContentResolver();
        r3 = android.provider.Telephony.Carriers.CONTENT_URI;
        r4 = 6;
        r4 = new java.lang.String[r4];
        r6 = 0;
        r7 = "_id";
        r4[r6] = r7;
        r6 = 1;
        r7 = "name";
        r4[r6] = r7;
        r6 = 2;
        r7 = "apn";
        r4[r6] = r7;
        r6 = 3;
        r7 = "type";
        r4[r6] = r7;
        r6 = 4;
        r7 = "user";
        r4[r6] = r7;
        r6 = 5;
        r7 = "network_flag";
        r4[r6] = r7;
        r6 = 0;
        r7 = 0;
        r11 = r2.query(r3, r4, r5, r6, r7);
        r2 = new java.lang.StringBuilder;
        r2.<init>();
        r3 = "getSelectedApn: apn == null, where=";
        r2 = r2.append(r3);
        r2 = r2.append(r5);
        r3 = ", cursor=";
        r2 = r2.append(r3);
        r2 = r2.append(r11);
        r3 = " cursor.count=";
        r3 = r2.append(r3);
        if (r11 == 0) goto L_0x039f;
    L_0x0227:
        r2 = r11.getCount();
    L_0x022b:
        r2 = r3.append(r2);
        r2 = r2.toString();
        r0 = r21;
        r0.log(r2);
        if (r11 == 0) goto L_0x0638;
    L_0x023a:
        r11.moveToFirst();
    L_0x023d:
        r2 = r11.isAfterLast();
        if (r2 != 0) goto L_0x0638;
    L_0x0243:
        r2 = "_id";
        r2 = r11.getColumnIndexOrThrow(r2);
        r15 = r11.getString(r2);
        r2 = "name";
        r2 = r11.getColumnIndexOrThrow(r2);
        r16 = r11.getString(r2);
        r2 = "apn";
        r2 = r11.getColumnIndexOrThrow(r2);
        r8 = r11.getString(r2);
        r2 = "type";
        r2 = r11.getColumnIndexOrThrow(r2);
        r20 = r11.getString(r2);
        r2 = "network_flag";
        r2 = r11.getColumnIndexOrThrow(r2);
        r17 = r11.getString(r2);
        r2 = "ril.IsCSIM";
        r3 = "";
        r10 = android.os.SystemProperties.get(r2, r3);
        r2 = new java.lang.StringBuilder;
        r2.<init>();
        r3 = "getSelectedApn: id=";
        r2 = r2.append(r3);
        r2 = r2.append(r15);
        r3 = ", name=";
        r2 = r2.append(r3);
        r0 = r16;
        r2 = r2.append(r0);
        r3 = ", apn=";
        r2 = r2.append(r3);
        r2 = r2.append(r8);
        r3 = ", type=";
        r2 = r2.append(r3);
        r0 = r20;
        r2 = r2.append(r0);
        r3 = ", network_flag=";
        r2 = r2.append(r3);
        r0 = r17;
        r2 = r2.append(r0);
        r3 = ", card_flag=";
        r2 = r2.append(r3);
        r2 = r2.append(r10);
        r2 = r2.toString();
        r0 = r21;
        r0.log(r2);
        r2 = "DCGG";
        r3 = "DGG";
        r2 = r2.equals(r3);
        if (r2 != 0) goto L_0x02eb;
    L_0x02d7:
        r2 = "DCGS";
        r3 = "DGG";
        r2 = r2.equals(r3);
        if (r2 != 0) goto L_0x02eb;
    L_0x02e1:
        r2 = "DCGGS";
        r3 = "DGG";
        r2 = r2.equals(r3);
        if (r2 == 0) goto L_0x04e1;
    L_0x02eb:
        r0 = r21;
        r2 = r0.mPhone;
        r2 = r2.getPhoneId();
        if (r2 != 0) goto L_0x04e1;
    L_0x02f5:
        r2 = "true";
        r3 = "gsm.operator.isroaming";
        r0 = r21;
        r4 = r0.mPhone;
        r6 = r4.getSubId();
        r4 = "";
        r3 = android.telephony.TelephonyManager.getTelephonyProperty(r3, r6, r4);
        r2 = r2.equals(r3);
        if (r2 != 0) goto L_0x04e1;
    L_0x030d:
        r2 = "4";
        r2 = r2.equals(r14);
        if (r2 != 0) goto L_0x031d;
    L_0x0315:
        r2 = "3";
        r2 = r2.equals(r14);
        if (r2 == 0) goto L_0x03a2;
    L_0x031d:
        r2 = "live.vodafone.com";
        r2 = r2.equalsIgnoreCase(r8);
        if (r2 == 0) goto L_0x03a2;
    L_0x0325:
        r11.moveToNext();
        goto L_0x023d;
    L_0x032a:
        r2 = new java.lang.StringBuilder;
        r2.<init>();
        r3 = "numeric=\"";
        r2 = r2.append(r3);
        r3 = "gsm.sim.operator.numeric";
        r4 = "";
        r3 = android.os.SystemProperties.get(r3, r4);
        r2 = r2.append(r3);
        r3 = "\"";
        r2 = r2.append(r3);
        r5 = r2.toString();
        goto L_0x0094;
    L_0x034d:
        r0 = r21;
        r2 = r0.mPhone;
        r2 = r2.getContext();
        r2 = r2.getContentResolver();
        r3 = new java.lang.StringBuilder;
        r3.<init>();
        r4 = PREFERAPN_URI_USING_SUBID;
        r4 = r4.toString();
        r3 = r3.append(r4);
        r0 = r21;
        r4 = r0.mPhone;
        r6 = r4.getSubId();
        r3 = r3.append(r6);
        r3 = r3.toString();
        r3 = android.net.Uri.parse(r3);
        r4 = 4;
        r4 = new java.lang.String[r4];
        r6 = 0;
        r7 = "_id";
        r4[r6] = r7;
        r6 = 1;
        r7 = "name";
        r4[r6] = r7;
        r6 = 2;
        r7 = "apn";
        r4[r6] = r7;
        r6 = 3;
        r7 = "numeric";
        r4[r6] = r7;
        r6 = 0;
        r7 = "name ASC";
        r11 = r2.query(r3, r4, r5, r6, r7);
        goto L_0x0135;
    L_0x039c:
        r2 = 0;
        goto L_0x0150;
    L_0x039f:
        r2 = 0;
        goto L_0x022b;
    L_0x03a2:
        r2 = android.telephony.TelephonyManager.getDefault();
        r0 = r21;
        r3 = r0.mPhone;
        r6 = r3.getSubId();
        r2 = r2.getNetworkType(r6);
        r3 = 13;
        if (r2 == r3) goto L_0x03ca;
    L_0x03b6:
        r2 = android.telephony.TelephonyManager.getDefault();
        r0 = r21;
        r3 = r0.mPhone;
        r6 = r3.getSubId();
        r2 = r2.getNetworkType(r6);
        r3 = 14;
        if (r2 != r3) goto L_0x040b;
    L_0x03ca:
        r2 = "3";
        r0 = r17;
        r2 = r2.equals(r0);
        if (r2 == 0) goto L_0x05fe;
    L_0x03d4:
        r2 = new java.lang.StringBuilder;
        r2.<init>();
        r3 = "network_flag 3, NetworkType=";
        r2 = r2.append(r3);
        r3 = android.telephony.TelephonyManager.getDefault();
        r0 = r21;
        r4 = r0.mPhone;
        r6 = r4.getSubId();
        r3 = r3.getNetworkType(r6);
        r2 = r2.append(r3);
        r3 = ", apn=";
        r2 = r2.append(r3);
        r2 = r2.append(r8);
        r2 = r2.toString();
        r0 = r21;
        r0.log(r2);
        r11.moveToNext();
        goto L_0x023d;
    L_0x040b:
        r2 = android.telephony.TelephonyManager.getDefault();
        r0 = r21;
        r3 = r0.mPhone;
        r6 = r3.getSubId();
        r2 = r2.getNetworkType(r6);
        r3 = 7;
        if (r2 == r3) goto L_0x0444;
    L_0x041e:
        r2 = android.telephony.TelephonyManager.getDefault();
        r0 = r21;
        r3 = r0.mPhone;
        r6 = r3.getSubId();
        r2 = r2.getNetworkType(r6);
        r3 = 5;
        if (r2 == r3) goto L_0x0444;
    L_0x0431:
        r2 = android.telephony.TelephonyManager.getDefault();
        r0 = r21;
        r3 = r0.mPhone;
        r6 = r3.getSubId();
        r2 = r2.getNetworkType(r6);
        r3 = 6;
        if (r2 != r3) goto L_0x0485;
    L_0x0444:
        r2 = "4";
        r0 = r17;
        r2 = r2.equals(r0);
        if (r2 == 0) goto L_0x05fe;
    L_0x044e:
        r2 = new java.lang.StringBuilder;
        r2.<init>();
        r3 = "network_flag 4, NetworkType=";
        r2 = r2.append(r3);
        r3 = android.telephony.TelephonyManager.getDefault();
        r0 = r21;
        r4 = r0.mPhone;
        r6 = r4.getSubId();
        r3 = r3.getNetworkType(r6);
        r2 = r2.append(r3);
        r3 = ", apn=";
        r2 = r2.append(r3);
        r2 = r2.append(r8);
        r2 = r2.toString();
        r0 = r21;
        r0.log(r2);
        r11.moveToNext();
        goto L_0x023d;
    L_0x0485:
        r2 = android.telephony.TelephonyManager.getDefault();
        r0 = r21;
        r3 = r0.mPhone;
        r6 = r3.getSubId();
        r2 = r2.getNetworkType(r6);
        if (r2 != 0) goto L_0x04ad;
    L_0x0497:
        r2 = "4";
        r0 = r17;
        r2 = r2.equals(r0);
        if (r2 == 0) goto L_0x05fe;
    L_0x04a1:
        r2 = "Unknown NETWORK,non-LTE model network_flag 4 skip";
        r0 = r21;
        r0.log(r2);
        r11.moveToNext();
        goto L_0x023d;
    L_0x04ad:
        r2 = "1";
        r2 = r2.equals(r10);
        if (r2 == 0) goto L_0x04cb;
    L_0x04b5:
        r2 = "3";
        r0 = r17;
        r2 = r2.equals(r0);
        if (r2 == 0) goto L_0x05fe;
    L_0x04bf:
        r2 = "4G card -  network_flag 3 skip";
        r0 = r21;
        r0.log(r2);
        r11.moveToNext();
        goto L_0x023d;
    L_0x04cb:
        r2 = "4";
        r0 = r17;
        r2 = r2.equals(r0);
        if (r2 == 0) goto L_0x05fe;
    L_0x04d5:
        r2 = "4G card -  network_flag 4 skip";
        r0 = r21;
        r0.log(r2);
        r11.moveToNext();
        goto L_0x023d;
    L_0x04e1:
        r2 = "CG";
        r3 = "DGG";
        r2 = r2.equals(r3);
        if (r2 != 0) goto L_0x0513;
    L_0x04eb:
        r2 = "DCG";
        r3 = "DGG";
        r2 = r2.equals(r3);
        if (r2 != 0) goto L_0x0513;
    L_0x04f5:
        r2 = "DCGS";
        r3 = "DGG";
        r2 = r2.equals(r3);
        if (r2 != 0) goto L_0x0513;
    L_0x04ff:
        r2 = "DCGG";
        r3 = "DGG";
        r2 = r2.equals(r3);
        if (r2 != 0) goto L_0x0513;
    L_0x0509:
        r2 = "DCGGS";
        r3 = "DGG";
        r2 = r2.equals(r3);
        if (r2 == 0) goto L_0x05fe;
    L_0x0513:
        r2 = "4";
        r2 = r2.equals(r14);
        if (r2 != 0) goto L_0x0523;
    L_0x051b:
        r2 = "3";
        r2 = r2.equals(r14);
        if (r2 == 0) goto L_0x052d;
    L_0x0523:
        r0 = r21;
        r2 = r0.mPhone;
        r2 = r2.getPhoneId();
        if (r2 == 0) goto L_0x053b;
    L_0x052d:
        r2 = "1";
        r3 = "ril.CTCDUAL2";
        r3 = android.os.SystemProperties.get(r3);
        r2 = r2.equals(r3);
        if (r2 == 0) goto L_0x05a0;
    L_0x053b:
        r2 = "live.vodafone.com";
        r2 = r2.equalsIgnoreCase(r8);
        if (r2 != 0) goto L_0x0553;
    L_0x0543:
        r2 = "CTWAP";
        r2 = r2.equalsIgnoreCase(r8);
        if (r2 != 0) goto L_0x0553;
    L_0x054b:
        r2 = "ctlte";
        r2 = r2.equalsIgnoreCase(r8);
        if (r2 == 0) goto L_0x05a0;
    L_0x0553:
        r2 = "true";
        r3 = "gsm.operator.isroaming";
        r0 = r21;
        r4 = r0.mPhone;
        r6 = r4.getSubId();
        r4 = "";
        r3 = android.telephony.TelephonyManager.getTelephonyProperty(r3, r6, r4);
        r2 = r2.equals(r3);
        if (r2 == 0) goto L_0x05a0;
    L_0x056b:
        r2 = "46003";
        r3 = "gsm.sim.operator.numeric";
        r0 = r21;
        r4 = r0.mPhone;
        r6 = r4.getSubId();
        r4 = "";
        r3 = android.telephony.TelephonyManager.getTelephonyProperty(r3, r6, r4);
        r2 = r2.equals(r3);
        if (r2 != 0) goto L_0x05a0;
    L_0x0583:
        r2 = new java.lang.StringBuilder;
        r2.<init>();
        r3 = "skip live.vodafone.com or CTWAP or ctlte, apn=";
        r2 = r2.append(r3);
        r2 = r2.append(r8);
        r2 = r2.toString();
        r0 = r21;
        r0.log(r2);
        r11.moveToNext();
        goto L_0x023d;
    L_0x05a0:
        r2 = "1";
        r3 = "ril.CTCDUAL2";
        r3 = android.os.SystemProperties.get(r3);
        r2 = r2.equals(r3);
        if (r2 == 0) goto L_0x05fe;
    L_0x05ae:
        r0 = r21;
        r2 = r0.mPhone;
        r2 = r2.getPhoneId();
        r3 = 1;
        if (r2 != r3) goto L_0x05fe;
    L_0x05b9:
        r2 = "true";
        r3 = "gsm.operator.isroaming";
        r0 = r21;
        r4 = r0.mPhone;
        r6 = r4.getSubId();
        r4 = "";
        r3 = android.telephony.TelephonyManager.getTelephonyProperty(r3, r6, r4);
        r2 = r2.equals(r3);
        if (r2 != 0) goto L_0x05fe;
    L_0x05d1:
        r2 = "live.vodafone.com";
        r2 = r2.equalsIgnoreCase(r8);
        if (r2 != 0) goto L_0x05e1;
    L_0x05d9:
        r2 = "ctlte";
        r2 = r2.equalsIgnoreCase(r8);
        if (r2 == 0) goto L_0x05fe;
    L_0x05e1:
        r2 = new java.lang.StringBuilder;
        r2.<init>();
        r3 = "slot2 CTC card(HOME) - skip live.vodafone.com or ctlte, apn=";
        r2 = r2.append(r3);
        r2 = r2.append(r8);
        r2 = r2.toString();
        r0 = r21;
        r0.log(r2);
        r11.moveToNext();
        goto L_0x023d;
    L_0x05fe:
        if (r20 == 0) goto L_0x069d;
    L_0x0600:
        r2 = "*";
        r0 = r20;
        r2 = r0.contains(r2);
        if (r2 != 0) goto L_0x0614;
    L_0x060a:
        r2 = "default";
        r0 = r20;
        r2 = r0.contains(r2);
        if (r2 == 0) goto L_0x069d;
    L_0x0614:
        if (r19 == 0) goto L_0x061e;
    L_0x0616:
        r0 = r19;
        r2 = r0.equals(r15);
        if (r2 == 0) goto L_0x0681;
    L_0x061e:
        r2 = new java.lang.StringBuilder;
        r2.<init>();
        r3 = "getSelectedApn() break;, type=";
        r2 = r2.append(r3);
        r0 = r20;
        r2 = r2.append(r0);
        r2 = r2.toString();
        r0 = r21;
        r0.log(r2);
    L_0x0638:
        if (r11 == 0) goto L_0x063d;
    L_0x063a:
        r11.close();
    L_0x063d:
        if (r13 == 0) goto L_0x0668;
    L_0x063f:
        r2 = r13.equals(r15);
        if (r2 != 0) goto L_0x0668;
    L_0x0645:
        if (r19 == 0) goto L_0x0668;
    L_0x0647:
        r0 = r19;
        r2 = r0.equals(r15);
        if (r2 != 0) goto L_0x0668;
    L_0x064f:
        r8 = r12;
        r2 = new java.lang.StringBuilder;
        r2.<init>();
        r3 = "getSelectedApn() set to the first available apn = ";
        r2 = r2.append(r3);
        r2 = r2.append(r8);
        r2 = r2.toString();
        r0 = r21;
        r0.log(r2);
    L_0x0668:
        r2 = "ctlte";
        r2 = r2.equalsIgnoreCase(r8);
        if (r2 != 0) goto L_0x0680;
    L_0x0670:
        r2 = "ctnet";
        r2 = r2.equalsIgnoreCase(r8);
        if (r2 != 0) goto L_0x0680;
    L_0x0678:
        r2 = "ctwap";
        r2 = r2.equalsIgnoreCase(r8);
        if (r2 == 0) goto L_0x06a2;
    L_0x0680:
        return r8;
    L_0x0681:
        if (r12 != 0) goto L_0x069d;
    L_0x0683:
        r13 = r15;
        r12 = r8;
        r2 = new java.lang.StringBuilder;
        r2.<init>();
        r3 = "getSelectedApn() first available apn=";
        r2 = r2.append(r3);
        r2 = r2.append(r8);
        r2 = r2.toString();
        r0 = r21;
        r0.log(r2);
    L_0x069d:
        r11.moveToNext();
        goto L_0x023d;
    L_0x06a2:
        r0 = r21;
        r2 = r0.mPhone;
        r9 = r2.getContext();
        r2 = new java.lang.StringBuilder;
        r2.<init>();
        r3 = "getSelectedApn() SubscriptionManager.getSlotId(SubscriptionManager.getDefaultDataSubId()) =";
        r2 = r2.append(r3);
        r3 = com.android.internal.telephony.SubscriptionController.getInstance();
        r4 = com.android.internal.telephony.SubscriptionController.getInstance();
        r6 = r4.getDefaultDataSubId();
        r3 = r3.getSlotId(r6);
        r2 = r2.append(r3);
        r3 = " name= ";
        r2 = r2.append(r3);
        r0 = r16;
        r2 = r2.append(r0);
        r2 = r2.toString();
        r0 = r21;
        r0.log(r2);
        r2 = com.android.internal.telephony.SubscriptionController.getInstance();
        r3 = com.android.internal.telephony.SubscriptionController.getInstance();
        r6 = r3.getDefaultDataSubId();
        r2 = r2.getSlotId(r6);
        r3 = 1;
        if (r2 != r3) goto L_0x072d;
    L_0x06f1:
        r2 = r9.getResources();
        r3 = 17041724; // 0x104093c float:2.4251196E-38 double:8.4197304E-317;
        r2 = r2.getString(r3);
        r0 = r16;
        r2 = r0.equals(r2);
        if (r2 == 0) goto L_0x072d;
    L_0x0704:
        r2 = r9.getResources();
        r3 = 17041727; // 0x104093f float:2.4251205E-38 double:8.419732E-317;
        r16 = r2.getString(r3);
    L_0x070f:
        r2 = new java.lang.StringBuilder;
        r2.<init>();
        r3 = "getSelectedApn() name= ";
        r2 = r2.append(r3);
        r0 = r16;
        r2 = r2.append(r0);
        r2 = r2.toString();
        r0 = r21;
        r0.log(r2);
        r8 = r16;
        goto L_0x0680;
    L_0x072d:
        r2 = com.android.internal.telephony.SubscriptionController.getInstance();
        r3 = com.android.internal.telephony.SubscriptionController.getInstance();
        r6 = r3.getDefaultDataSubId();
        r2 = r2.getSlotId(r6);
        r3 = 1;
        if (r2 != r3) goto L_0x075f;
    L_0x0740:
        r2 = r9.getResources();
        r3 = 17041725; // 0x104093d float:2.42512E-38 double:8.419731E-317;
        r2 = r2.getString(r3);
        r0 = r16;
        r2 = r0.equals(r2);
        if (r2 == 0) goto L_0x075f;
    L_0x0753:
        r2 = r9.getResources();
        r3 = 17041728; // 0x1040940 float:2.4251208E-38 double:8.4197324E-317;
        r16 = r2.getString(r3);
        goto L_0x070f;
    L_0x075f:
        r2 = com.android.internal.telephony.SubscriptionController.getInstance();
        r3 = com.android.internal.telephony.SubscriptionController.getInstance();
        r6 = r3.getDefaultDataSubId();
        r2 = r2.getSlotId(r6);
        r3 = 1;
        if (r2 != r3) goto L_0x070f;
    L_0x0772:
        r2 = r9.getResources();
        r3 = 17041726; // 0x104093e float:2.4251202E-38 double:8.4197314E-317;
        r2 = r2.getString(r3);
        r0 = r16;
        r2 = r0.equals(r2);
        if (r2 == 0) goto L_0x070f;
    L_0x0785:
        r2 = r9.getResources();
        r3 = 17041729; // 0x1040941 float:2.425121E-38 double:8.419733E-317;
        r16 = r2.getString(r3);
        goto L_0x070f;
        */
        throw new UnsupportedOperationException("Method not decompiled: com.android.internal.telephony.dataconnection.DcTrackerBase.getSelectedApn():java.lang.String");
    }

    public void handleMessage(Message msg) {
        boolean enabled;
        Bundle bundle;
        String apnType;
        switch (msg.what) {
            case 69636:
                log("DISCONNECTED_CONNECTED: msg=" + msg);
                DcAsyncChannel dcac = msg.obj;
                this.mDataConnectionAcHashMap.remove(Integer.valueOf(dcac.getDataConnectionIdSync()));
                dcac.disconnected();
                return;
            case 270336:
                this.mCidActive = msg.arg1;
                onDataSetupComplete((AsyncResult) msg.obj);
                return;
            case 270337:
                onRadioAvailable();
                return;
            case 270339:
                String reason = null;
                if (msg.obj instanceof String) {
                    reason = msg.obj;
                }
                onTrySetupData(reason);
                return;
            case 270342:
                onRadioOffOrNotAvailable();
                return;
            case 270343:
                onVoiceCallStarted();
                return;
            case 270344:
                onVoiceCallEnded();
                return;
            case 270347:
                onRoamingOn();
                return;
            case 270348:
                onRoamingOff();
                return;
            case 270349:
                onEnableApn(msg.arg1, msg.arg2);
                return;
            case 270351:
                log("DataConnectionTracker.handleMessage: EVENT_DISCONNECT_DONE msg=" + msg);
                onDisconnectDone(msg.arg1, (AsyncResult) msg.obj);
                return;
            case 270353:
                onDataStallAlarm(msg.arg1);
                return;
            case 270360:
                onCleanUpConnection(msg.arg1 != 0, msg.arg2, (String) msg.obj);
                return;
            case 270362:
                restartRadio();
                return;
            case 270363:
                onSetInternalDataEnabled(msg.arg1 == 1);
                return;
            case 270364:
                log("EVENT_RESET_DONE");
                onResetDone((AsyncResult) msg.obj);
                return;
            case 270365:
                onCleanUpAllConnections((String) msg.obj);
                return;
            case 270366:
                enabled = msg.arg1 == 1;
                log("CMD_SET_USER_DATA_ENABLE enabled=" + enabled);
                onSetUserDataEnabled(enabled);
                return;
            case 270367:
                boolean met = msg.arg1 == 1;
                log("CMD_SET_DEPENDENCY_MET met=" + met);
                bundle = msg.getData();
                if (bundle != null) {
                    apnType = (String) bundle.get("apnType");
                    if (apnType != null) {
                        onSetDependencyMet(apnType, met);
                        return;
                    }
                    return;
                }
                return;
            case 270368:
                onSetPolicyDataEnabled(msg.arg1 == 1);
                return;
            case 270369:
                onUpdateIcc();
                return;
            case 270370:
                log("DataConnectionTracker.handleMessage: EVENT_DISCONNECT_DC_RETRYING msg=" + msg);
                onDisconnectDcRetrying(msg.arg1, (AsyncResult) msg.obj);
                return;
            case 270371:
                onDataSetupCompleteError((AsyncResult) msg.obj);
                return;
            case 270372:
                sEnableFailFastRefCounter = (msg.arg1 == 1 ? 1 : -1) + sEnableFailFastRefCounter;
                log("CMD_SET_ENABLE_FAIL_FAST_MOBILE_DATA:  sEnableFailFastRefCounter=" + sEnableFailFastRefCounter);
                if (sEnableFailFastRefCounter < 0) {
                    loge("CMD_SET_ENABLE_FAIL_FAST_MOBILE_DATA: sEnableFailFastRefCounter:" + sEnableFailFastRefCounter + " < 0");
                    sEnableFailFastRefCounter = 0;
                }
                enabled = sEnableFailFastRefCounter > 0;
                log("CMD_SET_ENABLE_FAIL_FAST_MOBILE_DATA: enabled=" + enabled + " sEnableFailFastRefCounter=" + sEnableFailFastRefCounter);
                if (this.mFailFast != enabled) {
                    this.mFailFast = enabled;
                    this.mDataStallDetectionEnabled = !enabled;
                    if (this.mDataStallDetectionEnabled && getOverallState() == State.CONNECTED && (!this.mInVoiceCall || this.mPhone.getServiceStateTracker().isConcurrentVoiceAndDataAllowed())) {
                        log("CMD_SET_ENABLE_FAIL_FAST_MOBILE_DATA: start data stall");
                        stopDataStallAlarm();
                        startDataStallAlarm(false);
                        return;
                    }
                    log("CMD_SET_ENABLE_FAIL_FAST_MOBILE_DATA: stop data stall");
                    stopDataStallAlarm();
                    return;
                }
                return;
            case 270373:
                bundle = msg.getData();
                if (bundle != null) {
                    try {
                        this.mProvisioningUrl = (String) bundle.get("provisioningUrl");
                    } catch (ClassCastException e) {
                        loge("CMD_ENABLE_MOBILE_PROVISIONING: provisioning url not a string" + e);
                        this.mProvisioningUrl = null;
                    }
                }
                if (TextUtils.isEmpty(this.mProvisioningUrl)) {
                    loge("CMD_ENABLE_MOBILE_PROVISIONING: provisioning url is empty, ignoring");
                    this.mIsProvisioning = false;
                    this.mProvisioningUrl = null;
                    return;
                }
                loge("CMD_ENABLE_MOBILE_PROVISIONING: provisioningUrl=" + this.mProvisioningUrl);
                this.mIsProvisioning = true;
                startProvisioningApnAlarm();
                return;
            case 270374:
                boolean isProvApn;
                int i;
                log("CMD_IS_PROVISIONING_APN");
                apnType = null;
                try {
                    bundle = msg.getData();
                    if (bundle != null) {
                        apnType = (String) bundle.get("apnType");
                    }
                    if (TextUtils.isEmpty(apnType)) {
                        loge("CMD_IS_PROVISIONING_APN: apnType is empty");
                        isProvApn = false;
                    } else {
                        isProvApn = isProvisioningApn(apnType);
                    }
                } catch (ClassCastException e2) {
                    loge("CMD_IS_PROVISIONING_APN: NO provisioning url ignoring");
                    isProvApn = false;
                }
                log("CMD_IS_PROVISIONING_APN: ret=" + isProvApn);
                AsyncChannel asyncChannel = this.mReplyAc;
                if (isProvApn) {
                    i = 1;
                } else {
                    i = 0;
                }
                asyncChannel.replyToMessage(msg, 270374, i);
                return;
            case 270375:
                log("EVENT_PROVISIONING_APN_ALARM");
                ApnContext apnCtx = (ApnContext) this.mApnContexts.get("default");
                if (!apnCtx.isProvisioningApn() || !apnCtx.isConnectedOrConnecting()) {
                    log("EVENT_PROVISIONING_APN_ALARM: Not connected ignore");
                    return;
                } else if (this.mProvisioningApnAlarmTag == msg.arg1) {
                    log("EVENT_PROVISIONING_APN_ALARM: Disconnecting");
                    this.mIsProvisioning = false;
                    this.mProvisioningUrl = null;
                    stopProvisioningApnAlarm();
                    sendCleanUpConnection(true, apnCtx);
                    return;
                } else {
                    log("EVENT_PROVISIONING_APN_ALARM: ignore stale tag, mProvisioningApnAlarmTag:" + this.mProvisioningApnAlarmTag + " != arg1:" + msg.arg1);
                    return;
                }
            case 270376:
                if (msg.arg1 == 1) {
                    handleStartNetStatPoll((Activity) msg.obj);
                    return;
                } else if (msg.arg1 == 0) {
                    handleStopNetStatPoll((Activity) msg.obj);
                    return;
                } else {
                    return;
                }
            case 270444:
                onPlmnChanged();
                return;
            default:
                Rlog.e("DATA", "Unidentified event msg=" + msg);
                return;
        }
    }

    public boolean getAnyDataEnabled() {
        boolean result;
        synchronized (this.mDataEnabledLock) {
            result = this.mInternalDataEnabled && this.mUserDataEnabled && this.sPolicyDataEnabled && this.mEnabledCount != 0;
        }
        if (!result) {
            log("getAnyDataEnabled " + result);
        }
        return result;
    }

    protected boolean isEmergency() {
        boolean result;
        synchronized (this.mDataEnabledLock) {
            result = this.mPhone.isInEcm() || this.mPhone.isInEmergencyCall();
        }
        log("isEmergency: result=" + result);
        return result;
    }

    protected int apnTypeToId(String type) {
        if (TextUtils.equals(type, "default")) {
            return 0;
        }
        if (TextUtils.equals(type, "mms")) {
            return 1;
        }
        if (TextUtils.equals(type, "supl")) {
            return 2;
        }
        if (TextUtils.equals(type, "dun")) {
            return 3;
        }
        if (TextUtils.equals(type, "hipri")) {
            return 4;
        }
        if (TextUtils.equals(type, "ims")) {
            return 5;
        }
        if (TextUtils.equals(type, "fota")) {
            return 6;
        }
        if (TextUtils.equals(type, "cbs")) {
            return 7;
        }
        if (TextUtils.equals(type, "ia")) {
            return 8;
        }
        if (TextUtils.equals(type, "emergency")) {
            return 9;
        }
        if (TextUtils.equals(type, "mms2")) {
            return 15;
        }
        if (TextUtils.equals(type, "cmdm")) {
            return 10;
        }
        if (TextUtils.equals(type, "cmmail")) {
            return 11;
        }
        if (TextUtils.equals(type, "wap")) {
            return 12;
        }
        if (TextUtils.equals(type, "bip")) {
            return 14;
        }
        if (TextUtils.equals(type, "cas")) {
            return 13;
        }
        if (TextUtils.equals(type, "xcap")) {
            return 16;
        }
        if (TextUtils.equals(type, "ent1")) {
            return 17;
        }
        if (TextUtils.equals(type, "ent2")) {
            return 18;
        }
        return -1;
    }

    protected String apnIdToType(int id) {
        switch (id) {
            case 0:
                return "default";
            case 1:
                return "mms";
            case 2:
                return "supl";
            case 3:
                return "dun";
            case 4:
                return "hipri";
            case 5:
                return "ims";
            case 6:
                return "fota";
            case 7:
                return "cbs";
            case 8:
                return "ia";
            case 9:
                return "emergency";
            case 10:
                return "cmdm";
            case 11:
                return "cmmail";
            case 12:
                return "wap";
            case 13:
                return "cas";
            case 14:
                return "bip";
            case 15:
                return "mms2";
            case 16:
                return "xcap";
            case 17:
                return "ent1";
            case 18:
                return "ent2";
            default:
                log("Unknown id (" + id + ") in apnIdToType");
                return "default";
        }
    }

    public LinkProperties getLinkProperties(String apnType) {
        if (isApnIdEnabled(apnTypeToId(apnType))) {
            return ((DcAsyncChannel) this.mDataConnectionAcHashMap.get(Integer.valueOf(0))).getLinkPropertiesSync();
        }
        return new LinkProperties();
    }

    public NetworkCapabilities getNetworkCapabilities(String apnType) {
        if (isApnIdEnabled(apnTypeToId(apnType))) {
            return ((DcAsyncChannel) this.mDataConnectionAcHashMap.get(Integer.valueOf(0))).getNetworkCapabilitiesSync();
        }
        return new NetworkCapabilities();
    }

    protected void notifyDataConnection(String reason) {
        for (int id = 0; id < 19; id++) {
            if (this.mDataEnabled[id]) {
                this.mPhone.notifyDataConnection(reason, apnIdToType(id));
            }
        }
        notifyOffApnsOfAvailability(reason);
    }

    private void notifyApnIdUpToCurrent(String reason, int apnId) {
        switch (C00993.$SwitchMap$com$android$internal$telephony$DctConstants$State[this.mState.ordinal()]) {
            case 2:
            case 3:
            case 4:
                this.mPhone.notifyDataConnection(reason, apnIdToType(apnId), DataState.CONNECTING);
                return;
            case 5:
            case 6:
                this.mPhone.notifyDataConnection(reason, apnIdToType(apnId), DataState.CONNECTING);
                this.mPhone.notifyDataConnection(reason, apnIdToType(apnId), DataState.CONNECTED);
                return;
            default:
                return;
        }
    }

    private void notifyApnIdDisconnected(String reason, int apnId) {
        this.mPhone.notifyDataConnection(reason, apnIdToType(apnId), DataState.DISCONNECTED);
    }

    protected void notifyOffApnsOfAvailability(String reason) {
        log("notifyOffApnsOfAvailability - reason= " + reason);
        for (int id = 0; id < 19; id++) {
            if (!isApnIdEnabled(id)) {
                notifyApnIdDisconnected(reason, id);
            }
        }
    }

    public boolean isApnTypeEnabled(String apnType) {
        if (apnType == null) {
            return false;
        }
        return isApnIdEnabled(apnTypeToId(apnType));
    }

    protected synchronized boolean isApnIdEnabled(int id) {
        boolean z;
        if (id != -1) {
            z = this.mDataEnabled[id];
        } else {
            z = false;
        }
        return z;
    }

    protected void setEnabled(int id, boolean enable) {
        log("setEnabled(" + id + ", " + enable + ") with old state = " + this.mDataEnabled[id] + " and enabledCount = " + this.mEnabledCount);
        int radioTech = this.mPhone.getServiceState().getRadioTechnology();
        Message msg = obtainMessage(270349);
        msg.arg1 = id;
        msg.arg2 = enable ? 1 : 0;
        if (radioTech == 18 && id == 0) {
            log("setEnabled : add delay 1 sec for ril rat change time");
            sendMessageDelayed(msg, 1000);
            return;
        }
        sendMessage(msg);
    }

    protected void onEnableApn(int apnId, int enabled) {
        log("EVENT_APN_ENABLE_REQUEST apnId=" + apnId + ", apnType=" + apnIdToType(apnId) + ", enabled=" + enabled + ", dataEnabled = " + this.mDataEnabled[apnId] + ", enabledCount = " + this.mEnabledCount + ", isApnTypeActive = " + isApnTypeActive(apnIdToType(apnId)));
        if (enabled == 1) {
            synchronized (this) {
                if (!this.mDataEnabled[apnId]) {
                    this.mDataEnabled[apnId] = true;
                    this.mEnabledCount++;
                }
            }
            String type = apnIdToType(apnId);
            if (!isApnTypeActive(type)) {
                this.mRequestedApnType = type;
                onEnableNewApn();
                return;
            } else if (!isCdmaRat(this.mPhone.getServiceState().getRilDataRadioTechnology())) {
                return;
            } else {
                return;
            }
        }
        boolean didDisable = false;
        synchronized (this) {
            if (this.mDataEnabled[apnId]) {
                this.mDataEnabled[apnId] = false;
                this.mEnabledCount--;
                didDisable = true;
            }
        }
        if (didDisable) {
            if (this.mEnabledCount == 0 || apnId == 3) {
                this.mRequestedApnType = "default";
                onCleanUpConnection(true, apnId, Phone.REASON_DATA_DISABLED);
            }
            notifyApnIdDisconnected(Phone.REASON_DATA_DISABLED, apnId);
            if (this.mDataEnabled[0] && !isApnTypeActive("default")) {
                this.mRequestedApnType = "default";
                onEnableNewApn();
            }
        }
    }

    protected void onEnableNewApn() {
    }

    protected void onResetDone(AsyncResult ar) {
        log("EVENT_RESET_DONE");
        String reason = null;
        if (ar.userObj instanceof String) {
            reason = ar.userObj;
        }
        gotoIdleAndNotifyDataConnection(reason);
    }

    public boolean setInternalDataEnabled(boolean enable) {
        log("setInternalDataEnabled(" + enable + ")");
        Message msg = obtainMessage(270363);
        msg.arg1 = enable ? 1 : 0;
        sendMessage(msg);
        return true;
    }

    protected void onSetInternalDataEnabled(boolean enabled) {
        synchronized (this.mDataEnabledLock) {
            this.mInternalDataEnabled = enabled;
            if (enabled) {
                log("onSetInternalDataEnabled: changed to enabled, try to setup data call");
                onTrySetupData(Phone.REASON_DATA_ENABLED);
            } else {
                log("onSetInternalDataEnabled: changed to disabled, cleanUpAllConnections");
                cleanUpAllConnections(Phone.REASON_DATA_DISABLED);
            }
        }
    }

    public void cleanUpAllConnections(String cause) {
        Message msg = obtainMessage(270365);
        msg.obj = cause;
        sendMessage(msg);
    }

    protected void onSetUserDataEnabled(boolean enabled) {
        int i = 0;
        synchronized (this.mDataEnabledLock) {
            boolean prevEnabled = getAnyDataEnabled();
            if ("GG".equals("DGG")) {
                this.mUserDataEnabled = Global.getInt(this.mPhone.getContext().getContentResolver(), "mobile_data", 1) == 1;
            }
            if (this.mUserDataEnabled != enabled) {
                this.mUserDataEnabled = enabled;
                this.mPhone.getContext().sendBroadcast(new Intent("com.android.sec.settings.MOBILE_DATA_CHANGED"));
                ContentResolver contentResolver = this.mPhone.getContext().getContentResolver();
                String str = "mobile_data";
                if (enabled) {
                    i = 1;
                }
                Global.putInt(contentResolver, str, i);
                if (!getDataOnRoamingEnabled() && this.mPhone.getServiceState().getRoaming()) {
                    if (enabled) {
                        notifyOffApnsOfAvailability(Phone.REASON_ROAMING_ON);
                    } else {
                        notifyOffApnsOfAvailability(Phone.REASON_DATA_DISABLED);
                    }
                }
                if (prevEnabled != getAnyDataEnabled()) {
                    if (prevEnabled) {
                        onCleanUpAllConnections(Phone.REASON_DATA_SPECIFIC_DISABLED);
                    } else {
                        onTrySetupData(Phone.REASON_DATA_ENABLED);
                    }
                }
            }
        }
    }

    protected void onSetDependencyMet(String apnType, boolean met) {
    }

    protected void onSetPolicyDataEnabled(boolean enabled) {
        boolean z = true;
        synchronized (this.mDataEnabledLock) {
            boolean prevEnabled = getAnyDataEnabled();
            if (this.sPolicyDataEnabled != enabled) {
                boolean z2;
                this.sPolicyDataEnabled = enabled;
                Intent i = new Intent("com.android.intent.action.DATAUSAGE_REACH_TO_LIMIT");
                String str = "policyData";
                if (this.sPolicyDataEnabled) {
                    z2 = false;
                } else {
                    z2 = true;
                }
                i.putExtra(str, z2);
                this.mPhone.getContext().sendBroadcast(i);
                StringBuilder append = new StringBuilder().append("DATAUSAGE_REACH_TO_LIMIT = ");
                if (this.sPolicyDataEnabled) {
                    z = false;
                }
                log(append.append(z).toString());
                if (prevEnabled != getAnyDataEnabled()) {
                    if (prevEnabled) {
                        onCleanUpAllConnections(Phone.REASON_DATA_SPECIFIC_DISABLED);
                    } else {
                        onTrySetupData(Phone.REASON_DATA_ENABLED);
                    }
                }
            }
        }
    }

    protected String getReryConfig(boolean forDefault) {
        int nt = this.mPhone.getServiceState().getNetworkType();
        if (nt == 4 || nt == 7 || nt == 5 || nt == 6 || nt == 12 || nt == 14) {
            return SystemProperties.get("ro.cdma.data_retry_config");
        }
        if (forDefault) {
            return SystemProperties.get("ro.gsm.data_retry_config");
        }
        return SystemProperties.get("ro.gsm.2nd_data_retry_config");
    }

    protected void resetPollStats() {
        this.mTxPkts = -1;
        this.mRxPkts = -1;
        this.mNetStatPollPeriod = 1000;
    }

    void startNetStatPoll() {
        if (getOverallState() == State.CONNECTED && !this.mNetStatPollEnabled) {
            log("startNetStatPoll");
            resetPollStats();
            this.mNetStatPollEnabled = true;
            this.mPollNetStat.run();
        }
    }

    void stopNetStatPoll() {
        this.mNetStatPollEnabled = false;
        removeCallbacks(this.mPollNetStat);
        log("stopNetStatPoll");
    }

    public void sendStartNetStatPoll(Activity activity) {
        Message msg = obtainMessage(270376);
        msg.arg1 = 1;
        msg.obj = activity;
        sendMessage(msg);
    }

    protected void handleStartNetStatPoll(Activity activity) {
        startNetStatPoll();
        startDataStallAlarm(false);
        setActivity(activity);
    }

    public void sendStopNetStatPoll(Activity activity) {
        Message msg = obtainMessage(270376);
        msg.arg1 = 0;
        msg.obj = activity;
        sendMessage(msg);
    }

    protected void handleStopNetStatPoll(Activity activity) {
        stopNetStatPoll();
        stopDataStallAlarm();
        setActivity(activity);
    }

    public void updateDataActivity() {
        TxRxSum preTxRxSum = new TxRxSum(this.mTxPkts, this.mRxPkts);
        TxRxSum curTxRxSum = new TxRxSum();
        curTxRxSum.updateTxRxSum();
        this.mTxPkts = curTxRxSum.txPkts;
        this.mRxPkts = curTxRxSum.rxPkts;
        if (!this.mNetStatPollEnabled) {
            return;
        }
        if (preTxRxSum.txPkts > 0 || preTxRxSum.rxPkts > 0) {
            Activity newActivity;
            long sent = this.mTxPkts - preTxRxSum.txPkts;
            long received = this.mRxPkts - preTxRxSum.rxPkts;
            if (sent > 0 && received > 0) {
                newActivity = Activity.DATAINANDOUT;
            } else if (sent > 0 && received == 0) {
                newActivity = Activity.DATAOUT;
            } else if (sent != 0 || received <= 0) {
                newActivity = this.mActivity == Activity.DORMANT ? this.mActivity : Activity.NONE;
            } else {
                newActivity = Activity.DATAIN;
            }
            if (this.mActivity != newActivity && this.mIsScreenOn) {
                if ("CG".equals("DGG")) {
                    log("updateDataActivity: newActivity=" + newActivity);
                }
                this.mActivity = newActivity;
                this.mPhone.notifyDataActivity();
            }
        }
    }

    public int getRecoveryAction() {
        int action = System.getInt(this.mPhone.getContext().getContentResolver(), "radio.data.stall.recovery.action", 0);
        log("getRecoveryAction: " + action);
        return action;
    }

    public void putRecoveryAction(int action) {
        System.putInt(this.mPhone.getContext().getContentResolver(), "radio.data.stall.recovery.action", action);
        log("putRecoveryAction: " + action);
    }

    protected boolean isConnected() {
        return false;
    }

    protected void doRecovery() {
        if (getOverallState() == State.CONNECTED) {
            int recoveryAction = getRecoveryAction();
            switch (recoveryAction) {
                case 0:
                    EventLog.writeEvent(EventLogTags.DATA_STALL_RECOVERY_GET_DATA_CALL_LIST, this.mSentSinceLastRecv);
                    log("doRecovery() get data call list");
                    this.mPhone.mCi.getDataCallList(obtainMessage(270340));
                    putRecoveryAction(1);
                    break;
                case 1:
                    this.mPhone.getContext().sendBroadcast(new Intent("com.android.sec.mobilecare.data.stall"));
                    EventLog.writeEvent(EventLogTags.DATA_STALL_RECOVERY_CLEANUP, this.mSentSinceLastRecv);
                    log("doRecovery() cleanup all connections");
                    cleanUpAllConnections(Phone.REASON_PDP_RESET);
                    putRecoveryAction(2);
                    break;
                case 2:
                    EventLog.writeEvent(EventLogTags.DATA_STALL_RECOVERY_REREGISTER, this.mSentSinceLastRecv);
                    log("doRecovery() re-register");
                    this.mPhone.getServiceStateTracker().reRegisterNetwork(null);
                    if (!this.mIsScreenOn && "DCM".equals("")) {
                        putRecoveryAction(3);
                        break;
                    } else {
                        putRecoveryAction(0);
                        break;
                    }
                    break;
                case 3:
                    EventLog.writeEvent(EventLogTags.DATA_STALL_RECOVERY_RADIO_RESTART, this.mSentSinceLastRecv);
                    log("restarting radio");
                    if ("DCM".equals("")) {
                        putRecoveryAction(0);
                    } else {
                        putRecoveryAction(4);
                    }
                    restartRadio();
                    break;
                case 4:
                    EventLog.writeEvent(EventLogTags.DATA_STALL_RECOVERY_RADIO_RESTART_WITH_PROP, -1);
                    log("restarting radio with gsm.radioreset to true");
                    SystemProperties.set(this.RADIO_RESET_PROPERTY, "true");
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                    }
                    restartRadio();
                    putRecoveryAction(0);
                    break;
                default:
                    throw new RuntimeException("doRecovery: Invalid recoveryAction=" + recoveryAction);
            }
            this.mSentSinceLastRecv = 0;
        }
    }

    private void updateDataStallInfo() {
        TxRxSum preTxRxSum = new TxRxSum(this.mDataStallTxRxSum);
        if ("TMO".equals(SystemProperties.get("ro.csc.sales_code"))) {
            this.mDataStallTxRxSum.updateTcpTxRxSum();
        } else {
            this.mDataStallTxRxSum.updateTxRxSum();
        }
        log("updateDataStallInfo: mDataStallTxRxSum=" + this.mDataStallTxRxSum + " preTxRxSum=" + preTxRxSum);
        long sent = this.mDataStallTxRxSum.txPkts - preTxRxSum.txPkts;
        long received = this.mDataStallTxRxSum.rxPkts - preTxRxSum.rxPkts;
        if (sent > 0 && received > 0) {
            log("updateDataStallInfo: IN/OUT");
            this.mSentSinceLastRecv = 0;
            putRecoveryAction(0);
        } else if (sent > 0 && received == 0) {
            if (this.mPhone.getState() == PhoneConstants.State.IDLE) {
                this.mSentSinceLastRecv += sent;
            } else {
                this.mSentSinceLastRecv = 0;
            }
            log("updateDataStallInfo: OUT sent=" + sent + " mSentSinceLastRecv=" + this.mSentSinceLastRecv);
        } else if (sent != 0 || received <= 0) {
            log("updateDataStallInfo: NONE");
        } else {
            log("updateDataStallInfo: IN");
            this.mSentSinceLastRecv = 0;
            putRecoveryAction(0);
        }
    }

    protected void onDataStallAlarm(int tag) {
        if (this.mDataStallAlarmTag != tag) {
            log("onDataStallAlarm: ignore, tag=" + tag + " expecting " + this.mDataStallAlarmTag);
            return;
        }
        updateDataStallInfo();
        int hangWatchdogTrigger = Global.getInt(this.mResolver, "pdp_watchdog_trigger_packet_count", 10);
        boolean suspectedStall = false;
        if (this.mSentSinceLastRecv >= ((long) hangWatchdogTrigger)) {
            log("onDataStallAlarm: tag=" + tag + " do recovery action=" + getRecoveryAction());
            suspectedStall = true;
            boolean needRecovery = true;
            int dataStallAlarmMaxDelayCount = CscFeature.getInstance().getInteger("CscFeature_RIL_ConfigDataRecoveryTimeFor2G", 0);
            if (dataStallAlarmMaxDelayCount > 0) {
                switch (this.mPhone.getServiceState().getRilDataRadioTechnology()) {
                    case 1:
                    case 2:
                        if (this.dataStallAlarmCount >= dataStallAlarmMaxDelayCount) {
                            this.dataStallAlarmCount = 0;
                            break;
                        }
                        log("onDataStallAlarm: 2G skip alarm Count=" + this.dataStallAlarmCount);
                        needRecovery = false;
                        this.dataStallAlarmCount++;
                        break;
                    default:
                        this.dataStallAlarmCount = 0;
                        break;
                }
            }
            String numeric = SystemProperties.get("gsm.sim.operator.numeric", "none");
            if ("SKT".equals("") || numeric.equals("00101")) {
                needRecovery = false;
            }
            if (needRecovery) {
                sendMessage(obtainMessage(270354));
            }
        } else {
            log("onDataStallAlarm: tag=" + tag + " Sent " + String.valueOf(this.mSentSinceLastRecv) + " pkts since last received, < watchdogTrigger=" + hangWatchdogTrigger);
        }
        startDataStallAlarm(suspectedStall);
    }

    protected void startDataStallAlarm(boolean suspectedStall) {
        int nextAction = getRecoveryAction();
        if (this.mDataStallDetectionEnabled && getOverallState() == State.CONNECTED) {
            int delayInMs;
            if (this.mIsScreenOn || suspectedStall || RecoveryAction.isAggressiveRecovery(nextAction)) {
                delayInMs = Global.getInt(this.mResolver, "data_stall_alarm_aggressive_delay_in_ms", ServiceStateTracker.DEFAULT_GPRS_CHECK_PERIOD_MILLIS);
            } else {
                delayInMs = Global.getInt(this.mResolver, "data_stall_alarm_non_aggressive_delay_in_ms", DATA_STALL_ALARM_NON_AGGRESSIVE_DELAY_IN_MS_DEFAULT);
            }
            this.mDataStallAlarmTag++;
            log("startDataStallAlarm: tag=" + this.mDataStallAlarmTag + " delay=" + (delayInMs / 1000) + "s");
            Intent intent = new Intent(INTENT_DATA_STALL_ALARM);
            intent.putExtra(DATA_STALL_ALARM_TAG_EXTRA, this.mDataStallAlarmTag);
            this.mDataStallAlarmIntent = PendingIntent.getBroadcast(this.mPhone.getContext(), 0, intent, 134217728);
            this.mAlarmManager.set(3, SystemClock.elapsedRealtime() + ((long) delayInMs), this.mDataStallAlarmIntent);
            return;
        }
        log("startDataStallAlarm: NOT started, no connection tag=" + this.mDataStallAlarmTag);
    }

    protected void stopDataStallAlarm() {
        log("stopDataStallAlarm: current tag=" + this.mDataStallAlarmTag + " mDataStallAlarmIntent=" + this.mDataStallAlarmIntent);
        this.mDataStallAlarmTag++;
        if (this.mDataStallAlarmIntent != null) {
            this.mAlarmManager.cancel(this.mDataStallAlarmIntent);
            this.mDataStallAlarmIntent = null;
        }
    }

    protected void restartDataStallAlarm() {
        if (!isConnected()) {
            return;
        }
        if (RecoveryAction.isAggressiveRecovery(getRecoveryAction())) {
            log("restartDataStallAlarm: action is pending. not resetting the alarm.");
            return;
        }
        log("restartDataStallAlarm: stop then start.");
        stopDataStallAlarm();
        startDataStallAlarm(false);
    }

    protected void setInitialAttachApn() {
        ApnSetting iaApnSetting = null;
        ApnSetting defaultApnSetting = null;
        ApnSetting firstApnSetting = null;
        log("setInitialApn: E mPreferredApn=" + this.mPreferredApn);
        if (this.mAllApnSettings != null && !this.mAllApnSettings.isEmpty()) {
            firstApnSetting = (ApnSetting) this.mAllApnSettings.get(0);
            log("setInitialApn: firstApnSetting=" + firstApnSetting);
            Iterator i$ = this.mAllApnSettings.iterator();
            while (i$.hasNext()) {
                ApnSetting apn = (ApnSetting) i$.next();
                if (ArrayUtils.contains(apn.types, "ia") && apn.carrierEnabled) {
                    log("setInitialApn: iaApnSetting=" + apn);
                    iaApnSetting = apn;
                    break;
                } else if (defaultApnSetting == null && apn.canHandleType("default")) {
                    log("setInitialApn: defaultApnSetting=" + apn);
                    defaultApnSetting = apn;
                }
            }
        }
        ApnSetting initialAttachApnSetting = null;
        if (iaApnSetting != null) {
            log("setInitialAttachApn: using iaApnSetting");
            initialAttachApnSetting = iaApnSetting;
        } else if (this.mPreferredApn != null) {
            log("setInitialAttachApn: using mPreferredApn");
            initialAttachApnSetting = this.mPreferredApn;
        } else if (defaultApnSetting != null) {
            log("setInitialAttachApn: using defaultApnSetting");
            initialAttachApnSetting = defaultApnSetting;
        } else if (firstApnSetting != null) {
            log("setInitialAttachApn: using firstApnSetting");
            initialAttachApnSetting = firstApnSetting;
        }
        if (initialAttachApnSetting == null) {
            log("setInitialAttachApn: X There in no available apn");
            return;
        }
        log("setInitialAttachApn: X selected Apn=" + initialAttachApnSetting);
        this.mPhone.mCi.setInitialAttachApn(initialAttachApnSetting.apn, initialAttachApnSetting.protocol, initialAttachApnSetting.authType, initialAttachApnSetting.user, initialAttachApnSetting.password, null);
    }

    protected void setDataProfilesAsNeeded() {
        log("setDataProfilesAsNeeded");
        if (this.mAllApnSettings != null && !this.mAllApnSettings.isEmpty()) {
            ArrayList<DataProfile> dps = new ArrayList();
            Iterator it = this.mAllApnSettings.iterator();
            while (it.hasNext()) {
                ApnSetting apn = (ApnSetting) it.next();
                if (apn.modemCognitive) {
                    DataProfile dp = new DataProfile(apn, this.mPhone.getServiceState().getRoaming());
                    boolean isDup = false;
                    Iterator i$ = dps.iterator();
                    while (i$.hasNext()) {
                        if (dp.equals((DataProfile) i$.next())) {
                            isDup = true;
                            break;
                        }
                    }
                    if (!isDup) {
                        dps.add(dp);
                    }
                }
            }
            if (dps.size() > 0) {
                this.mPhone.mCi.setDataProfile((DataProfile[]) dps.toArray(new DataProfile[0]), null);
            }
        }
    }

    protected void onActionIntentProvisioningApnAlarm(Intent intent) {
        log("onActionIntentProvisioningApnAlarm: action=" + intent.getAction());
        Message msg = obtainMessage(270375, intent.getAction());
        msg.arg1 = intent.getIntExtra(PROVISIONING_APN_ALARM_TAG_EXTRA, 0);
        sendMessage(msg);
    }

    protected void onPlmnChanged() {
        String opNumeric = this.mPhone.getServiceState().getOperatorNumeric();
        if (opNumeric == null) {
            loge("onPlmnChanged: Skip if current operator numeric is null");
        } else if (this.mPermanentFailedOperatorNumeric == null || this.mPermanentFailedOperatorNumeric.size() == 0) {
            log("onPlmnChanged: Skip if NOT permanent failed yet");
        } else {
            int size = this.mPermanentFailedOperatorNumeric.size();
            log("onPlmnChanged: Number of permanent failed PLMNs=" + size);
            for (int i = 0; i < size; i++) {
                if (opNumeric.equals((String) this.mPermanentFailedOperatorNumeric.get(i))) {
                    log("onPlmnChanged: Skip if " + opNumeric + " is permanent failed");
                    return;
                }
            }
            for (ApnContext apnContext : this.mApnContexts.values()) {
                apnContext.setPermanentRetryCount(0);
            }
            onTrySetupData(Phone.REASON_PLMN_CHANGED);
        }
    }

    protected void startProvisioningApnAlarm() {
        int delayInMs = Global.getInt(this.mResolver, "provisioning_apn_alarm_delay_in_ms", PROVISIONING_APN_ALARM_DELAY_IN_MS_DEFAULT);
        if (Build.IS_DEBUGGABLE) {
            try {
                delayInMs = Integer.parseInt(System.getProperty(DEBUG_PROV_APN_ALARM, Integer.toString(delayInMs)));
            } catch (NumberFormatException e) {
                loge("startProvisioningApnAlarm: e=" + e);
            }
        }
        this.mProvisioningApnAlarmTag++;
        log("startProvisioningApnAlarm: tag=" + this.mProvisioningApnAlarmTag + " delay=" + (delayInMs / 1000) + "s");
        Intent intent = new Intent(INTENT_PROVISIONING_APN_ALARM);
        intent.putExtra(PROVISIONING_APN_ALARM_TAG_EXTRA, this.mProvisioningApnAlarmTag);
        this.mProvisioningApnAlarmIntent = PendingIntent.getBroadcast(this.mPhone.getContext(), 0, intent, 134217728);
        this.mAlarmManager.set(2, SystemClock.elapsedRealtime() + ((long) delayInMs), this.mProvisioningApnAlarmIntent);
    }

    protected void stopProvisioningApnAlarm() {
        log("stopProvisioningApnAlarm: current tag=" + this.mProvisioningApnAlarmTag + " mProvsioningApnAlarmIntent=" + this.mProvisioningApnAlarmIntent);
        this.mProvisioningApnAlarmTag++;
        if (this.mProvisioningApnAlarmIntent != null) {
            this.mAlarmManager.cancel(this.mProvisioningApnAlarmIntent);
            this.mProvisioningApnAlarmIntent = null;
        }
    }

    void sendCleanUpConnection(boolean tearDown, ApnContext apnContext) {
        int i;
        log("sendCleanUpConnection: tearDown=" + tearDown + " apnContext=" + apnContext);
        Message msg = obtainMessage(270360);
        if (tearDown) {
            i = 1;
        } else {
            i = 0;
        }
        msg.arg1 = i;
        msg.arg2 = 0;
        msg.obj = apnContext;
        sendMessage(msg);
    }

    void sendRestartRadio() {
        log("sendRestartRadio:");
        sendMessage(obtainMessage(270362));
    }

    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        int i;
        pw.println("DataConnectionTrackerBase:");
        pw.println(" RADIO_TESTS=false");
        pw.println(" mInternalDataEnabled=" + this.mInternalDataEnabled);
        pw.println(" mUserDataEnabled=" + this.mUserDataEnabled);
        pw.println(" sPolicyDataEnabed=" + this.sPolicyDataEnabled);
        pw.println(" mDataEnabled:");
        for (i = 0; i < this.mDataEnabled.length; i++) {
            pw.printf("  mDataEnabled[%d]=%b\n", new Object[]{Integer.valueOf(i), Boolean.valueOf(this.mDataEnabled[i])});
        }
        pw.flush();
        pw.println(" mEnabledCount=" + this.mEnabledCount);
        pw.println(" mRequestedApnType=" + this.mRequestedApnType);
        pw.println(" mPhone=" + this.mPhone.getPhoneName());
        pw.println(" mActivity=" + this.mActivity);
        pw.println(" mState=" + this.mState);
        pw.println(" mTxPkts=" + this.mTxPkts);
        pw.println(" mRxPkts=" + this.mRxPkts);
        pw.println(" mNetStatPollPeriod=" + this.mNetStatPollPeriod);
        pw.println(" mNetStatPollEnabled=" + this.mNetStatPollEnabled);
        pw.println(" mDataStallTxRxSum=" + this.mDataStallTxRxSum);
        pw.println(" mDataStallAlarmTag=" + this.mDataStallAlarmTag);
        pw.println(" mDataStallDetectionEanbled=" + this.mDataStallDetectionEnabled);
        pw.println(" mSentSinceLastRecv=" + this.mSentSinceLastRecv);
        pw.println(" mNoRecvPollCount=" + this.mNoRecvPollCount);
        pw.println(" mResolver=" + this.mResolver);
        pw.println(" mIsWifiConnected=" + this.mIsWifiConnected);
        pw.println(" mReconnectIntent=" + this.mReconnectIntent);
        pw.println(" mCidActive=" + this.mCidActive);
        pw.println(" mAutoAttachOnCreation=" + this.mAutoAttachOnCreation);
        pw.println(" mIsScreenOn=" + this.mIsScreenOn);
        pw.println(" mUniqueIdGenerator=" + this.mUniqueIdGenerator);
        pw.flush();
        pw.println(" ***************************************");
        DcController dcc = this.mDcc;
        if (dcc != null) {
            dcc.dump(fd, pw, args);
        } else {
            pw.println(" mDcc=null");
        }
        pw.println(" ***************************************");
        if (this.mDataConnections != null) {
            Set<Entry<Integer, DataConnection>> mDcSet = this.mDataConnections.entrySet();
            pw.println(" mDataConnections: count=" + mDcSet.size());
            for (Entry<Integer, DataConnection> entry : mDcSet) {
                pw.printf(" *** mDataConnection[%d] \n", new Object[]{entry.getKey()});
                ((DataConnection) entry.getValue()).dump(fd, pw, args);
            }
        } else {
            pw.println("mDataConnections=null");
        }
        pw.println(" ***************************************");
        pw.flush();
        HashMap<String, Integer> apnToDcId = this.mApnToDataConnectionId;
        if (apnToDcId != null) {
            Set<Entry<String, Integer>> apnToDcIdSet = apnToDcId.entrySet();
            pw.println(" mApnToDataConnectonId size=" + apnToDcIdSet.size());
            for (Entry<String, Integer> entry2 : apnToDcIdSet) {
                pw.printf(" mApnToDataConnectonId[%s]=%d\n", new Object[]{entry2.getKey(), entry2.getValue()});
            }
        } else {
            pw.println("mApnToDataConnectionId=null");
        }
        pw.println(" ***************************************");
        pw.flush();
        ConcurrentHashMap<String, ApnContext> apnCtxs = this.mApnContexts;
        if (apnCtxs != null) {
            Set<Entry<String, ApnContext>> apnCtxsSet = apnCtxs.entrySet();
            pw.println(" mApnContexts size=" + apnCtxsSet.size());
            for (Entry<String, ApnContext> entry3 : apnCtxsSet) {
                ((ApnContext) entry3.getValue()).dump(fd, pw, args);
            }
            pw.println(" ***************************************");
        } else {
            pw.println(" mApnContexts=null");
        }
        pw.flush();
        pw.println(" mActiveApn=" + this.mActiveApn);
        ArrayList<ApnSetting> apnSettings = this.mAllApnSettings;
        if (apnSettings != null) {
            pw.println(" mAllApnSettings size=" + apnSettings.size());
            for (i = 0; i < apnSettings.size(); i++) {
                pw.printf(" mAllApnSettings[%d]: %s\n", new Object[]{Integer.valueOf(i), apnSettings.get(i)});
            }
            pw.flush();
        } else {
            pw.println(" mAllApnSettings=null");
        }
        pw.println(" mPreferredApn=" + this.mPreferredApn);
        pw.println(" mIsPsRestricted=" + this.mIsPsRestricted);
        pw.println(" mIsDisposed=" + this.mIsDisposed);
        pw.println(" mIntentReceiver=" + this.mIntentReceiver);
        pw.println(" mDataRoamingSettingObserver=" + this.mDataRoamingSettingObserver);
        pw.flush();
    }

    public boolean isDataInLegacy3gpp(int radioTechnology) {
        return radioTechnology == 1 || radioTechnology == 2 || radioTechnology == 3 || radioTechnology == 9 || radioTechnology == 10 || radioTechnology == 11 || radioTechnology == 15 || radioTechnology == 16;
    }

    public static boolean is1xEvdo(int radioTechnology) {
        return radioTechnology == 4 || radioTechnology == 5 || radioTechnology == 6 || radioTechnology == 7 || radioTechnology == 8 || radioTechnology == 12;
    }

    protected int getImsRecoveryAction() {
        int recoveryAction = -1;
        log("getImsRecoveryAction: imsRecoveryStep = " + this.imsRecoveryStep);
        if (this.mImsRecoveryPolicy.length < this.imsRecoveryStep + 1) {
            log("getImsRecoveryAction: max action already taken");
        } else {
            recoveryAction = this.mImsRecoveryPolicy[this.imsRecoveryStep];
            log("getImsRecoveryAction: " + recoveryAction);
            if (getState("ims") == State.CONNECTED) {
                this.imsRecoveryStep++;
            } else {
                this.imsRecoveryStep = 0;
            }
        }
        return recoveryAction;
    }

    protected void doImsRecovery(int recoveryAction) {
        if (getState("ims") == State.CONNECTED) {
            switch (recoveryAction) {
                case 0:
                    log("doImsRecovery() get data call list");
                    this.mPhone.mCi.getDataCallList(obtainMessage(270340));
                    return;
                case 1:
                    log("doImsRecovery() cleanup all connections");
                    sendCleanUpConnection(true, (ApnContext) this.mApnContexts.get("ims"));
                    return;
                case 2:
                    log("doImsRecovery() re-register");
                    this.mPhone.getServiceStateTracker().reRegisterNetwork(null);
                    return;
                case 3:
                    log("doImsRecovery() restarting radio");
                    restartRadio();
                    return;
                default:
                    log("doImsRecovery() Invalid recoveryAction=" + recoveryAction);
                    return;
            }
        }
    }

    public boolean isDomesticCard() {
        IccRecords r = (IccRecords) this.mIccRecords.get();
        String operator = r != null ? r.getOperatorNumeric() : "";
        String mcc = (operator == null || operator == "") ? "" : operator.substring(0, 3);
        if ("450".equals(mcc)) {
            return true;
        }
        return false;
    }

    public boolean isDomesticModel() {
        if ("SKT".equals("") || "KTT".equals("") || "LGT".equals("")) {
            return true;
        }
        return false;
    }

    public boolean needToRunLteRoaming() {
        String simType = SystemProperties.get("ril.simtype", "");
        if (("2".equals(simType) && "KTT".equals("")) || (("3".equals(simType) && "LGT".equals("")) || ("4".equals(simType) && "SKT".equals("")))) {
            return true;
        }
        return false;
    }

    public boolean needToRunAutoRoamingApn() {
        String simType = SystemProperties.get("ril.simtype", "");
        if (("2".equals(simType) && "KTT".equals("")) || (("3".equals(simType) && "LGT".equals("")) || ("4".equals(simType) && "SKT".equals("")))) {
            return true;
        }
        return false;
    }

    public boolean IgnoreDataEnabledOnRoaming() {
        if (isDomesticModel() && this.mPhone.getServiceState().getRoaming()) {
            return true;
        }
        return false;
    }

    public boolean isCarrierEnabledOnHome(ApnSetting apn) {
        boolean carrierEnabledOnHome = true;
        Cursor cursor = null;
        if (apn == null) {
            return false;
        }
        try {
            cursor = this.mPhone.getContext().getContentResolver().query(Uri.parse("content://nwkinfo/nwkinfo/carriers"), null, new String("(apn = '" + apn.apn + "' and name = '" + apn.carrier + "')"), null, null);
            if (cursor == null) {
                loge("isCarrierEnabledOnHome: No record found. ");
            } else if (cursor.getCount() == 1 && cursor.moveToFirst()) {
                do {
                    carrierEnabledOnHome = "1".equals(cursor.getString(cursor.getColumnIndexOrThrow(Carriers.CARRIER_ENABLED)));
                } while (cursor.moveToNext());
            }
            if (cursor != null) {
                cursor.close();
            }
        } catch (Exception e) {
            loge("isCarrierEnabledOnHome: exception caught  : " + e);
            if (cursor != null) {
                cursor.close();
            }
        } catch (Throwable th) {
            if (cursor != null) {
                cursor.close();
            }
        }
        loge("isCarrierEnabledOnHome: apn = " + apn.apn + " carrierEnabledOnHome = " + carrierEnabledOnHome);
        return carrierEnabledOnHome;
    }

    public boolean isCarrierEnabledOnRoaming(ApnSetting apn) {
        boolean carrierEnabledOnRoaming = false;
        Cursor cursor = null;
        if (apn == null) {
            return false;
        }
        try {
            cursor = this.mPhone.getContext().getContentResolver().query(Uri.parse("content://nwkinfo/nwkinfo/carriers"), null, new String("(apn = '" + apn.apn + "' and name = '" + apn.carrier + "')"), null, null);
            if (cursor == null) {
                loge("isCarrierEnabledOnRoaming: No record found.");
            } else if (cursor.getCount() == 1 && cursor.moveToFirst()) {
                do {
                    carrierEnabledOnRoaming = "1".equals(cursor.getString(cursor.getColumnIndexOrThrow(Carriers.CARRIER_ENABLED_ROAMING)));
                } while (cursor.moveToNext());
            }
            if (cursor != null) {
                cursor.close();
            }
        } catch (Exception e) {
            loge("isCarrierEnabledOnRoaming: exception caught  : " + e);
            if (cursor != null) {
                cursor.close();
            }
        } catch (Throwable th) {
            if (cursor != null) {
                cursor.close();
            }
        }
        loge("isCarrierEnabledOnRoaming: apn = " + apn.apn + " carrierEnabledOnRoaming = " + carrierEnabledOnRoaming);
        return carrierEnabledOnRoaming;
    }

    private boolean getAirplaneMode() {
        if (System.getInt(this.mPhone.getContext().getContentResolver(), "airplane_mode_on", 0) != 0) {
            return true;
        }
        return false;
    }

    private void isFotaMode() {
        IOException e;
        Throwable th;
        FileReader fileReader = null;
        BufferedReader bufReader = null;
        String str = null;
        try {
            FileReader fileReader2 = new FileReader("/efs/auto_reboot/autoinstall.status");
            try {
                BufferedReader bufReader2 = new BufferedReader(fileReader2);
                try {
                    str = bufReader2.readLine();
                    if (fileReader2 != null) {
                        try {
                            fileReader2.close();
                        } catch (IOException e2) {
                            e2.printStackTrace();
                            bufReader = bufReader2;
                            fileReader = fileReader2;
                        }
                    }
                    if (bufReader2 != null) {
                        bufReader2.close();
                    }
                    bufReader = bufReader2;
                    fileReader = fileReader2;
                } catch (IOException e3) {
                    e2 = e3;
                    bufReader = bufReader2;
                    fileReader = fileReader2;
                    try {
                        loge("IOException: " + e2.getMessage());
                        if (fileReader != null) {
                            try {
                                fileReader.close();
                            } catch (IOException e22) {
                                e22.printStackTrace();
                            }
                        }
                        if (bufReader != null) {
                            bufReader.close();
                        }
                        if (!"AUTO_INSTALL".equals(str)) {
                            this.mIsFotaMode = true;
                            loge("FotaMode : " + str);
                        }
                    } catch (Throwable th2) {
                        th = th2;
                        if (fileReader != null) {
                            try {
                                fileReader.close();
                            } catch (IOException e222) {
                                e222.printStackTrace();
                                throw th;
                            }
                        }
                        if (bufReader != null) {
                            bufReader.close();
                        }
                        throw th;
                    }
                } catch (Throwable th3) {
                    th = th3;
                    bufReader = bufReader2;
                    fileReader = fileReader2;
                    if (fileReader != null) {
                        fileReader.close();
                    }
                    if (bufReader != null) {
                        bufReader.close();
                    }
                    throw th;
                }
            } catch (IOException e4) {
                e222 = e4;
                fileReader = fileReader2;
                loge("IOException: " + e222.getMessage());
                if (fileReader != null) {
                    fileReader.close();
                }
                if (bufReader != null) {
                    bufReader.close();
                }
                if (!"AUTO_INSTALL".equals(str)) {
                    this.mIsFotaMode = true;
                    loge("FotaMode : " + str);
                }
            } catch (Throwable th4) {
                th = th4;
                fileReader = fileReader2;
                if (fileReader != null) {
                    fileReader.close();
                }
                if (bufReader != null) {
                    bufReader.close();
                }
                throw th;
            }
        } catch (IOException e5) {
            e222 = e5;
            loge("IOException: " + e222.getMessage());
            if (fileReader != null) {
                fileReader.close();
            }
            if (bufReader != null) {
                bufReader.close();
            }
            if (!"AUTO_INSTALL".equals(str)) {
                this.mIsFotaMode = true;
                loge("FotaMode : " + str);
            }
        }
        if (!"AUTO_INSTALL".equals(str)) {
            this.mIsFotaMode = true;
            loge("FotaMode : " + str);
        }
    }

    public boolean isWifiConnected() {
        if (this.mIsWifiConnected) {
            return true;
        }
        return false;
    }

    public ApnSetting getPreferredApnEx() {
        log("getPreferredApnEx() Need to implement in sub classs");
        return null;
    }

    public void setDependencyMet(boolean met) {
        for (ApnContext apnContext : this.mApnContexts.values()) {
            Bundle bundle = Bundle.forPair("apnType", apnContext.getApnType());
            Message msg = Message.obtain();
            msg.what = 270367;
            msg.arg1 = met ? 1 : 0;
            msg.setData(bundle);
            sendMessage(msg);
        }
    }

    private ApnSetting setForNamAccount(ApnSetting initialAttachApnSetting) {
        String imsi = "";
        String iccid = "";
        String msisdn = "";
        String imei = "";
        String aid = "";
        String apass = "";
        String testNum = "99999,00101,45001";
        String mccmncforSBM = "44020";
        boolean mNamState = true;
        ApnSetting apnSetting = null;
        IccRecords r = (IccRecords) this.mIccRecords.get();
        if (r != null) {
            imsi = r.getIMSI();
            iccid = r.getIccId();
            msisdn = r.getMsisdnNumber();
        }
        if (this.mPhone != null) {
            imei = this.mPhone.getDeviceId();
            apnSetting = initialAttachApnSetting;
        }
        Rlog.i(LOG_TAG, "[SBMNAM] setForNamAccount()");
        if (apnSetting != null) {
            aid = apnSetting.user;
            apass = apnSetting.password;
        }
        if (imsi != null && imsi.length() > 5) {
            if (testNum.contains(imsi.substring(0, 5))) {
                if (msisdn == null || msisdn.length() < 11) {
                    Rlog.i(LOG_TAG, "[SBMNAM] setForNamAccount() msisdn length is less than 11.");
                    if (imsi.length() >= 15) {
                        msisdn = imsi.substring(4, 15);
                    } else {
                        msisdn = "01234567890";
                    }
                    Rlog.i(LOG_TAG, "[SBMNAM] setForNamAccount() msisdn is " + msisdn);
                }
                if (imei == null || imei.length() < 14) {
                    Rlog.i(LOG_TAG, "[SBMNAM] setForNamAccount() imei length is less than 14.");
                    imei = "12345678901234";
                    Rlog.i(LOG_TAG, "[SBMNAM] setForNamAccount() imei is " + imei);
                }
            }
        }
        if (TextUtils.isEmpty(imsi) || TextUtils.isEmpty(iccid) || TextUtils.isEmpty(msisdn) || TextUtils.isEmpty(imei)) {
            mNamState = false;
        } else if ((aid == null && apass == null) || (aid.length() == 0 && apass.length() == 0)) {
            if (msisdn.length() > 11) {
                msisdn = msisdn.substring(0, 11);
            }
            if (imei.length() > 14) {
                imei = imei.substring(0, 14);
            }
            if (imsi.length() > 15) {
                imsi = imsi.substring(0, 15);
            }
            if (iccid.length() > 19) {
                iccid = iccid.substring(0, 19);
            }
            Rlog.i(LOG_TAG, "[SBMNAM] setForNamAccount() msisdn : " + msisdn + " / imei : " + imei + " / imsi : " + imsi + " / iccid : " + iccid);
            if ("44020".equals(imsi.substring(0, 5)) || "NAM".equals(SystemProperties.get("ril.testmode"))) {
                aid = sbmcgm_genId(msisdn, imei, imsi);
                if (aid == null || aid.length() <= 0) {
                    mNamState = false;
                } else {
                    apass = sbmcgm_genPasswd(iccid);
                }
            }
        }
        if (aid == null) {
            aid = "";
        }
        if (apass == null) {
            apass = "";
        }
        SystemProperties.set("ril.allowDataEnableByNam", mNamState ? "true" : "false");
        return new ApnSetting(initialAttachApnSetting.id, initialAttachApnSetting.numeric, initialAttachApnSetting.carrier, initialAttachApnSetting.apn, initialAttachApnSetting.proxy, initialAttachApnSetting.port, initialAttachApnSetting.mmsc, initialAttachApnSetting.mmsProxy, initialAttachApnSetting.mmsPort, aid, apass, initialAttachApnSetting.authType, initialAttachApnSetting.types, initialAttachApnSetting.protocol, initialAttachApnSetting.roamingProtocol, initialAttachApnSetting.carrierEnabled, initialAttachApnSetting.bearer, initialAttachApnSetting.profileId, initialAttachApnSetting.modemCognitive, initialAttachApnSetting.maxConns, initialAttachApnSetting.waitTime, initialAttachApnSetting.maxConnsTime, initialAttachApnSetting.mtu, initialAttachApnSetting.mvnoType, initialAttachApnSetting.mvnoMatchData);
    }
}
