package com.android.internal.telephony.gsm;

import android.app.AlarmManager;
import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.DialogInterface.OnClickListener;
import android.content.DialogInterface.OnDismissListener;
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
import android.os.SystemClock;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.preference.PreferenceManager;
import android.provider.Settings.Global;
import android.provider.Settings.Secure;
import android.provider.Settings.SettingNotFoundException;
import android.provider.Settings.System;
import android.provider.Telephony.TextBasedSmsColumns;
import android.provider.Telephony.Threads;
import android.telephony.CellIdentityGsm;
import android.telephony.CellIdentityLte;
import android.telephony.CellIdentityWcdma;
import android.telephony.CellInfo;
import android.telephony.CellInfoGsm;
import android.telephony.CellInfoLte;
import android.telephony.CellInfoWcdma;
import android.telephony.CellLocation;
import android.telephony.Rlog;
import android.telephony.ServiceState;
import android.telephony.SignalStrength;
import android.telephony.TelephonyManager;
import android.telephony.gsm.GsmCellLocation;
import android.text.TextUtils;
import android.util.EventLog;
import android.util.TimeUtils;
import com.android.internal.telephony.CommandsInterface.RadioState;
import com.android.internal.telephony.EventLogTags;
import com.android.internal.telephony.MccTable;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneConstants.DataState;
import com.android.internal.telephony.PhoneConstants.State;
import com.android.internal.telephony.PhoneFactory;
import com.android.internal.telephony.RILConstants;
import com.android.internal.telephony.RestrictedState;
import com.android.internal.telephony.ServiceStateTracker;
import com.android.internal.telephony.SubInfoRecordUpdater;
import com.android.internal.telephony.WspTypeDecoder;
import com.android.internal.telephony.cdma.CallFailCause;
import com.android.internal.telephony.dataconnection.DcTrackerBase;
import com.android.internal.telephony.uicc.IccCardApplicationStatus.AppState;
import com.android.internal.telephony.uicc.IccRecords;
import com.android.internal.telephony.uicc.RuimRecords;
import com.android.internal.telephony.uicc.SIMRecords;
import com.android.internal.telephony.uicc.UiccCardApplication;
import com.samsung.android.telephony.MultiSimManager;
import com.sec.android.app.CscFeature;
import com.sec.android.emergencymode.EmergencyManager;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.FileDescriptor;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;

public class GsmServiceStateTracker extends ServiceStateTracker {
    static final String BOOT_WITH_TD = "1";
    static final String BOOT_WITH_WCDMA = "2";
    static final int CS_DISABLED = 1004;
    static final int CS_EMERGENCY_ENABLED = 1006;
    static final int CS_ENABLED = 1003;
    static final int CS_NORMAL_ENABLED = 1005;
    static final int CS_NOTIFICATION = 999;
    static final int DTM_SUPPORT_NETWORK = 100;
    private static final int EMERGENCY_MAX_TIMEOUT = 600000;
    private static final int EMERGENCY_TERMINATE_TIMEOUT = 60000;
    private static final int EVENT_ALL_DATA_DISCONNECTED = 1001;
    private static final int EVENT_EMERGENCY_TIMEOUT = 0;
    protected static final int EVENT_NETWORK_STATE_CHANGED_BY_RESCAN = 4000;
    private static final String INTENT_WFC_SWITCH_PROFILE = "action_wfc_switch_profile_broadcast";
    static final String LOG_TAG = "GsmSST";
    private static final int LTE_DATA_OFF = 0;
    private static final int LTE_DATA_ON = 1;
    static final int PS_DISABLED = 1002;
    static final int PS_ENABLED = 1001;
    static final int PS_NOTIFICATION = 888;
    public static final int ROAMING_MODE_ALL_NETWORKS = 2;
    public static final int ROAMING_MODE_DISABLE = 0;
    public static final int ROAMING_MODE_NATIONAL_ROMING_ONLY = 1;
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
    static final boolean SHIP_BUILD = "true".equals(SystemProperties.get("ro.product_ship", "false"));
    static final String TDSCDMA_NOT_SUPPORT = "0";
    static final String TDSCDMA_ONLY_SUPPORT = "2";
    private static final String UNACTIVATED_MIN2_VALUE = "000000";
    private static final String UNACTIVATED_MIN_VALUE = "1111110111";
    static final boolean VDBG = false;
    private static final String WAKELOCK_TAG = "ServiceStateTracker";
    static final int WFC_CS_PREF = 2;
    static final int WFC_STATUS_OFF = 2;
    static final int WFC_STATUS_ON = 1;
    static final int WFC_WIFI_ONLY = 3;
    static final int WFC_WIFI_PREF = 1;
    protected static AlertDialog deniedDialog = null;
    private static boolean emergencyDataOpened = false;
    public static boolean isSetNoserviceTimer = false;
    protected static boolean isWFCReigstered = false;
    private static boolean isWfcWifiOnlyMode = false;
    private static boolean mDataSuspended = false;
    static int mDsCallCnt = 0;
    private static boolean mHasDisconnectedLte = false;
    private static int mHasIncomingCall = -1;
    private static int mHasRinging = -1;
    protected static AlertDialog mRescanDialog = null;
    private static boolean mScreenState = true;
    private static int mSlot1OldCallState = 0;
    static int mSlot1Type = 0;
    private static int mSlot2OldCallState = 0;
    static int mSlot2Type = 0;
    private static boolean mTetherState = false;
    static final int noserviceAlarmCode = 0;
    public static int noserviceCount = 0;
    static int oldDsCallStatus = 0;
    protected boolean IsFlightMode = false;
    protected boolean IsSIMLoadDone = false;
    protected boolean IsShow = false;
    private boolean MccNumChanged = false;
    private int NITZCount = 0;
    protected boolean NetworkStateChangedByRescanDialog = false;
    private String PrevMcc = null;
    private boolean RuimLoadedEvent = false;
    boolean after2min = false;
    protected boolean bIsSimAbsent = false;
    protected boolean bshowDataGuard = false;
    protected boolean bshowSmsGuard = false;
    protected String curNetworkReigst = null;
    private boolean getRejectCauseDisplay = false;
    private boolean hasOperatorNotSet = false;
    protected boolean isBootCompleted = false;
    protected boolean isFirstRescanDialogCheckAfterBoot = false;
    private AlarmManager mAlarmManager;
    private ContentObserver mAutoTimeObserver = new ContentObserver(new Handler()) {
        public void onChange(boolean selfChange) {
            Rlog.i("GsmServiceStateTracker", "Auto time state changed");
            GsmServiceStateTracker.this.revertToNitzTime();
        }
    };
    private ContentObserver mAutoTimeZoneObserver = new ContentObserver(new Handler()) {
        public void onChange(boolean selfChange) {
            Rlog.i("GsmServiceStateTracker", "Auto time zone state changed");
            GsmServiceStateTracker.this.revertToNitzTimeZone();
        }
    };
    GsmCellLocation mCellLoc;
    protected ContentResolver mCr;
    private String mCurPlmn = null;
    private boolean mCurShowPlmn = false;
    private boolean mCurShowSpn = false;
    private String mCurSpn = null;
    private int mCurrentOtaspMode = 0;
    private ContentObserver mDataNationalRoamingObserver = new ContentObserver(new Handler()) {
        public void onChange(boolean selfChange) {
            if ("2GNRP".equals(CscFeature.getInstance().getString("CscFeature_RIL_FakeRoamingOption4"))) {
                GsmServiceStateTracker.this.log("Data National Roaming Mode is changed");
                GsmServiceStateTracker.this.updateNationalRoamingMode();
            }
        }
    };
    private boolean mDataRoaming = false;
    protected int mDtmSupport = -1;
    private EmergencyManager mEmergencyMgr = null;
    protected boolean mEmergencyOnly = false;
    protected boolean mGotCountryCode = false;
    private boolean mGsmRoaming = false;
    protected String mHandset_Auth = TDSCDMA_NOT_SUPPORT;
    protected String mHandset_Reg = TDSCDMA_NOT_SUPPORT;
    private BroadcastReceiver mIntentReceiver = new C01061();
    private boolean mIsNitzReceived = false;
    int mLuRejCause = -1;
    protected int mMaxDataCalls = 1;
    private String mMdn;
    private String mMin;
    protected boolean mNeedFixZoneAfterNitz = false;
    GsmCellLocation mNewCellLoc;
    int mNewLuRejCause = -1;
    protected int mNewMaxDataCalls = 1;
    protected int mNewReasonDataDenied = -1;
    int mNewRilRegState;
    protected boolean mNitzUpdatedTime = false;
    private Notification mNotification;
    private PendingIntent mPendingIntent;
    private int mPendingRadioPowerOffAfterHangup = 0;
    protected GSMPhone mPhone;
    private boolean mPhoneOnMode = true;
    int mPreferredNetworkType;
    private boolean mPreviousAirplanemode = false;
    protected int mReasonDataDenied = -1;
    private boolean mReceivedHomeNetowkNoti = false;
    private final BroadcastReceiver mReceiver = new SSTBroadcastReceiver();
    protected int mRegistrationState = -1;
    protected boolean mReportedGprsNoReg = false;
    private boolean mRetrySyncPrefMode = false;
    int mRilRegState;
    private int mRoamingMode = -1;
    long mSavedAtTime;
    long mSavedTime;
    String mSavedTimeZone;
    private int mSlot1CallState = 0;
    private int mSlot2CallState = 0;
    protected boolean mStartedGprsRegCheck = false;
    protected WakeLock mWakeLock;
    protected byte mWfcPrefMode = (byte) 0;
    protected byte mWfcStatus = (byte) 0;
    protected String mWipiNetValInit = "-1";
    protected boolean mZoneDst;
    protected int mZoneOffset;
    protected long mZoneTime;
    protected boolean m_bActionLocaleChanged = false;
    private long maxTimerMS = 0;
    protected int oldRac = -1;
    OnClickListener onDenidedDialogClickListener = new C01105();
    private boolean onOffLock = false;
    OnClickListener onRescanDialogClickListener = new C01116();
    protected int rac = -1;
    int simSlotCount = SystemProperties.getInt("ro.multisim.simslotcount", 1);

    class C01061 extends BroadcastReceiver {
        C01061() {
        }

        public void onReceive(Context context, Intent intent) {
            GsmServiceStateTracker.this.log("Intent : " + intent.getAction());
            if (GsmServiceStateTracker.this.mPhone.mIsTheCurrentActivePhone) {
                DcTrackerBase dcTracker;
                if (intent.getAction().equals("android.intent.action.LOCALE_CHANGED")) {
                    GsmServiceStateTracker.this.updateSpnDisplay();
                } else if (intent.getAction().equals("android.intent.action.ACTION_RADIO_OFF")) {
                    GsmServiceStateTracker.this.mAlarmSwitch = false;
                    GsmServiceStateTracker.this.powerOffRadioSafely(GsmServiceStateTracker.this.mPhone.mDcTracker);
                }
                if (CscFeature.getInstance().getEnableStatus("CscFeature_RIL_EnableVoicePriority")) {
                    if (intent.getAction().equals("android.intent.action.SCREEN_ON")) {
                        GsmServiceStateTracker.mScreenState = true;
                        if (CscFeature.getInstance().getEnableStatus("CscFeature_RIL_EnableVoicePriority")) {
                            GsmServiceStateTracker.this.mPhone.mDcTracker.notifyDataConnectionForSST("SCREEN_ON");
                        } else {
                            GsmServiceStateTracker.this.mPhone.notifyDataConnection("SCREEN_ON");
                        }
                    } else if (intent.getAction().equals("android.intent.action.SCREEN_OFF")) {
                        GsmServiceStateTracker.mScreenState = false;
                        if (CscFeature.getInstance().getEnableStatus("CscFeature_RIL_EnableVoicePriority")) {
                            GsmServiceStateTracker.this.mPhone.mDcTracker.notifyDataConnectionForSST("SCREEN_OFF");
                        } else {
                            GsmServiceStateTracker.this.mPhone.notifyDataConnection("SCREEN_OFF");
                        }
                    } else if ("jp.co.nttdocomo.lcsapp.ACTION_STATUS_CHANGED".equals(intent.getAction())) {
                        int status = intent.getIntExtra(TextBasedSmsColumns.STATUS, 2);
                        if (status == 0) {
                            GsmServiceStateTracker.this.removeMessages(0);
                            GsmServiceStateTracker.this.sendMessageDelayed(GsmServiceStateTracker.this.obtainMessage(0), 60000);
                            GsmServiceStateTracker.this.log("Send Message TERMINATE TIMEOUT(60000)");
                        } else if (status == 1) {
                            GsmServiceStateTracker.this.removeMessages(0);
                            GsmServiceStateTracker.emergencyDataOpened = true;
                            GsmServiceStateTracker.this.mPhone.notifyDataConnection("LCSAPP_START");
                            GsmServiceStateTracker.this.sendMessageDelayed(GsmServiceStateTracker.this.obtainMessage(0), 600000);
                            GsmServiceStateTracker.this.log("Send Message MAX TIMEOUT(600000)");
                        } else {
                            GsmServiceStateTracker.this.log("lscapp sent wrong data. status : " + status);
                        }
                    }
                }
                if (CscFeature.getInstance().getEnableStatus("CscFeature_RIL_EnableVoicePriority")) {
                    if (intent.getAction().equals("android.intent.action.ANY_DATA_STATE")) {
                        boolean z;
                        if (((DataState) Enum.valueOf(DataState.class, intent.getStringExtra("state"))) != DataState.SUSPENDED) {
                            z = false;
                        } else {
                            z = true;
                        }
                        GsmServiceStateTracker.mDataSuspended = z;
                    } else if ("android.net.conn.TETHER_STATE_CHANGED".equals(intent.getAction())) {
                        dcTracker = GsmServiceStateTracker.this.mPhone.mDcTracker;
                        if (intent.getStringArrayListExtra("activeArray").size() > 0) {
                            GsmServiceStateTracker.mTetherState = true;
                            dcTracker.notifyDataConnectionForSST("TetherOn");
                        } else {
                            GsmServiceStateTracker.mTetherState = false;
                            dcTracker.notifyDataConnectionForSST("TetherOff");
                        }
                    }
                }
                String salesCode = SystemProperties.get("ro.csc.sales_code");
                if ("CHN".equals(salesCode) || "CHC".equals(salesCode) || "CHU".equals(salesCode) || "CHM".equals(salesCode) || "CTC".equals(salesCode)) {
                    if (intent.getAction().equals("ACTION_SET_PROPERTY_STATE")) {
                        GsmServiceStateTracker.this.updateSpnDisplay();
                    }
                    if ("CTC".equals(salesCode) && intent.getAction().equals("android.intent.action.AIRPLANE_MODE")) {
                        GsmServiceStateTracker.this.updateSpnDisplay();
                    }
                }
                if (("DGG".equals("DGG") && "dsds".equals(SystemProperties.get("persist.radio.multisim.config"))) || GsmServiceStateTracker.this.isTwochipDsdsOnRoaming()) {
                    if (intent.getAction().equals("android.intent.action.PHONE_STATE")) {
                        String sim1StateCheck = TelephonyManager.getTelephonyProperty("gsm.sim.state", 0, "ABSENT");
                        String sim2StateCheck = TelephonyManager.getTelephonyProperty("gsm.sim.state", 1, "ABSENT");
                        String dataPrefer = SystemProperties.get("persist.sys.dataprefer.simid", GsmServiceStateTracker.TDSCDMA_NOT_SUPPORT);
                        GsmServiceStateTracker.this.mSlot1CallState = MultiSimManager.getCallState(0);
                        GsmServiceStateTracker.this.mSlot2CallState = MultiSimManager.getCallState(1);
                        GsmServiceStateTracker.this.log("[DSDS_TWOCHIP] , mSlot1CallState : " + GsmServiceStateTracker.this.mSlot1CallState + ", mSlot2CallState : " + GsmServiceStateTracker.this.mSlot2CallState + ", mSlot1OldCallState : " + GsmServiceStateTracker.mSlot1OldCallState + ", mSlot2OldCallState : " + GsmServiceStateTracker.mSlot2OldCallState + ", mHasDisconnectedLte : " + GsmServiceStateTracker.mHasDisconnectedLte + ", getPhoneId()" + GsmServiceStateTracker.this.mPhone.getPhoneId());
                        if (!("ABSENT".equals(sim1StateCheck) || "ABSENT".equals(sim2StateCheck) || (GsmServiceStateTracker.mSlot1OldCallState == GsmServiceStateTracker.this.mSlot1CallState && GsmServiceStateTracker.mSlot2OldCallState == GsmServiceStateTracker.this.mSlot2CallState))) {
                            GsmServiceStateTracker.this.log("[DSDS_TWOCHIP] First mSlot1CallState : " + GsmServiceStateTracker.this.mSlot1CallState + ", mSlot2CallState : " + GsmServiceStateTracker.this.mSlot2CallState);
                            if (GsmServiceStateTracker.this.mSlot1CallState == 1 || GsmServiceStateTracker.this.mSlot2CallState == 1) {
                                GsmServiceStateTracker.mHasRinging = 1;
                            }
                            Intent powerIntent;
                            if (GsmServiceStateTracker.this.mSlot1CallState == 1 || GsmServiceStateTracker.this.mSlot1CallState == 2) {
                                GsmServiceStateTracker.mHasIncomingCall = 0;
                                if (GsmServiceStateTracker.this.mPhone.getPhoneId() == 0) {
                                    powerIntent = new Intent("android.intent.action.DUOS_CP_CTRL_BY_CALL");
                                    powerIntent.addFlags(536870912);
                                    powerIntent.putExtra("state", 0);
                                    powerIntent.putExtra("callslot", 0);
                                    if (GsmServiceStateTracker.BOOT_WITH_TD.equals(dataPrefer)) {
                                        powerIntent.putExtra("slot", 1);
                                        GsmServiceStateTracker.this.log("[DSDS_TWOCHIP] Add slot2 data off param in intent");
                                    }
                                    GsmServiceStateTracker.this.mPhone.getContext().sendStickyBroadcast(powerIntent);
                                    if (GsmServiceStateTracker.this.mSlot1CallState == 1 || GsmServiceStateTracker.mHasRinging != 1) {
                                        GsmServiceStateTracker.this.log("[DSDS_TWOCHIP] send sendCallState message once for incomming call in CP1");
                                        GsmServiceStateTracker.this.sendCallState(0, 1);
                                    }
                                    GsmServiceStateTracker.this.log("[DSDS_TWOCHIP] send DUOS_CP_CTRL_BY_CALL block broadcast");
                                    GsmServiceStateTracker.mSlot1OldCallState = GsmServiceStateTracker.this.mSlot1CallState;
                                }
                            } else if (GsmServiceStateTracker.this.mSlot2CallState == 1 || GsmServiceStateTracker.this.mSlot2CallState == 2) {
                                GsmServiceStateTracker.mHasIncomingCall = 1;
                                if (GsmServiceStateTracker.this.mPhone.getPhoneId() == 1) {
                                    powerIntent = new Intent("android.intent.action.DUOS_CP_CTRL_BY_CALL");
                                    powerIntent.addFlags(536870912);
                                    powerIntent.putExtra("state", 0);
                                    powerIntent.putExtra("callslot", 1);
                                    if (GsmServiceStateTracker.TDSCDMA_NOT_SUPPORT.equals(dataPrefer)) {
                                        powerIntent.putExtra("slot", 0);
                                        GsmServiceStateTracker.this.log("[DSDS_TWOCHIP] Add slot1 data off param in intent");
                                    }
                                    GsmServiceStateTracker.this.mPhone.getContext().sendStickyBroadcast(powerIntent);
                                    if (GsmServiceStateTracker.this.mSlot2CallState == 1 || GsmServiceStateTracker.mHasRinging != 1) {
                                        GsmServiceStateTracker.this.log("[DSDS_TWOCHIP] send sendCallState message once for incomming call in CP2");
                                        GsmServiceStateTracker.this.sendCallState(1, 1);
                                    }
                                    GsmServiceStateTracker.this.log("[DSDS_TWOCHIP] send DUOS_CP_CTRL_BY_CALL block broadcast");
                                    GsmServiceStateTracker.mSlot2OldCallState = GsmServiceStateTracker.this.mSlot2CallState;
                                }
                            } else if (GsmServiceStateTracker.this.mSlot1CallState == 0 && GsmServiceStateTracker.this.mSlot2CallState == 0) {
                                GsmServiceStateTracker.this.log("[DSDS_TWOCHIP] IDLE Setting");
                                GsmServiceStateTracker.mHasRinging = -1;
                                powerIntent = new Intent("android.intent.action.DUOS_CP_CTRL_BY_CALL");
                                powerIntent.addFlags(536870912);
                                powerIntent.putExtra("state", 1);
                                if (GsmServiceStateTracker.this.mPhone.getPhoneId() == 0 && GsmServiceStateTracker.mHasIncomingCall == 0) {
                                    if (GsmServiceStateTracker.BOOT_WITH_TD.equals(dataPrefer)) {
                                        powerIntent.putExtra("slot", 1);
                                        GsmServiceStateTracker.this.log("[DSDS_TWOCHIP] Add slot2 data on param in intent");
                                    }
                                    GsmServiceStateTracker.this.mPhone.getContext().sendStickyBroadcast(powerIntent);
                                    GsmServiceStateTracker.this.sendCallState(0, 0);
                                    GsmServiceStateTracker.this.log("[DSDS_TWOCHIP] send DUOS_CP_CTRL_BY_CALL unblock broadcast");
                                    GsmServiceStateTracker.mHasIncomingCall = -1;
                                    GsmServiceStateTracker.mSlot1OldCallState = GsmServiceStateTracker.this.mSlot1CallState;
                                } else if (GsmServiceStateTracker.this.mPhone.getPhoneId() == 1 && GsmServiceStateTracker.mHasIncomingCall == 1) {
                                    if (GsmServiceStateTracker.TDSCDMA_NOT_SUPPORT.equals(dataPrefer)) {
                                        powerIntent.putExtra("slot", 0);
                                        GsmServiceStateTracker.this.log("[DSDS_TWOCHIP] Add slot1 data on param in intent");
                                    }
                                    GsmServiceStateTracker.this.mPhone.getContext().sendStickyBroadcast(powerIntent);
                                    GsmServiceStateTracker.this.sendCallState(1, 0);
                                    GsmServiceStateTracker.this.log("[DSDS_TWOCHIP] send DUOS_CP_CTRL_BY_CALL unblock broadcast");
                                    GsmServiceStateTracker.mHasIncomingCall = -1;
                                    GsmServiceStateTracker.mSlot2OldCallState = GsmServiceStateTracker.this.mSlot2CallState;
                                }
                            }
                        }
                    }
                    if (intent.getAction().equals("android.intent.action.DUOS_CP_CTRL_BY_CALL")) {
                        int state = intent.getIntExtra("state", 0);
                        int callslot = intent.getIntExtra("callslot", 100);
                        int slot = intent.getIntExtra("slot", 100);
                        dcTracker = GsmServiceStateTracker.this.mPhone.mDcTracker;
                        if (state == 0) {
                            if (slot == GsmServiceStateTracker.this.mPhone.getPhoneId()) {
                                GsmServiceStateTracker.this.log("[DSDS_TWOCHIP] Data Off slot " + slot);
                                dcTracker.setInternalDataEnabled(false);
                            }
                            if (callslot == 0) {
                                GsmServiceStateTracker.this.log("[DSDS_TWOCHIP] slot1 has incomming call");
                                SystemProperties.set("ril.call_block", "slot1call");
                                return;
                            } else if (callslot == 1) {
                                GsmServiceStateTracker.this.log("[DSDS_TWOCHIP] slot2 has incomming call");
                                SystemProperties.set("ril.call_block", "slot2call");
                                return;
                            } else {
                                GsmServiceStateTracker.this.log("[DSDS_TWOCHIP] get DUOS_CP_CTRL_BY_CALL block broadcast");
                                return;
                            }
                        }
                        if (slot == GsmServiceStateTracker.this.mPhone.getPhoneId()) {
                            GsmServiceStateTracker.this.log("[DSDS_TWOCHIP] Data On slot" + slot);
                            dcTracker.setInternalDataEnabled(true);
                        }
                        SystemProperties.set("ril.call_block", "false");
                        GsmServiceStateTracker.this.log("[DSDS_TWOCHIP] get DUOS_CP_CTRL_BY_CALL unblock broadcast");
                        return;
                    }
                    return;
                }
                return;
            }
            Rlog.e(GsmServiceStateTracker.LOG_TAG, "Received Intent " + intent + " while being destroyed. Ignoring.");
        }
    }

    class C01105 implements OnClickListener {
        C01105() {
        }

        public void onClick(DialogInterface dialog, int which) {
            dialog.dismiss();
            GsmServiceStateTracker.this.log("sendMessage(EVENT_LU_REJECT_CAUSE)");
            GsmServiceStateTracker.this.sendMessage(GsmServiceStateTracker.this.obtainMessage(Threads.ALERT_AMBER_THREAD));
        }
    }

    class C01116 implements OnClickListener {
        C01116() {
        }

        public void onClick(DialogInterface dialog, int which) {
            dialog.dismiss();
            switch (which) {
                case SubInfoRecordUpdater.SIM_NEW /*-2*/:
                    Rlog.v("ManualSelectionReceiver", "sendMessageDelayed(EVENT_NETWORK_STATE_CHANGED_BY_RESCAN)");
                    GsmServiceStateTracker.this.sendMessageDelayed(GsmServiceStateTracker.this.obtainMessage(GsmServiceStateTracker.EVENT_NETWORK_STATE_CHANGED_BY_RESCAN, null), 30000);
                    break;
                case -1:
                    Intent intent = new Intent("android.intent.action.MAIN");
                    intent.setClassName("com.android.phone", "com.android.phone.NetworkSetting");
                    intent.addFlags(268435456);
                    intent.putExtra("search-type", "manual");
                    GsmServiceStateTracker.this.mPhone.getContext().startActivity(intent);
                    break;
            }
            GsmServiceStateTracker.mRescanDialog = null;
        }
    }

    class C01127 implements OnCancelListener {
        C01127() {
        }

        public void onCancel(DialogInterface dialog) {
            GsmServiceStateTracker.mRescanDialog = null;
        }
    }

    class C01138 implements OnDismissListener {
        C01138() {
        }

        public void onDismiss(DialogInterface dialog) {
            GsmServiceStateTracker.mRescanDialog = null;
        }
    }

    static /* synthetic */ class C01149 {
        static final /* synthetic */ int[] f14x46dd5024 = new int[RadioState.values().length];

        static {
            try {
                f14x46dd5024[RadioState.RADIO_UNAVAILABLE.ordinal()] = 1;
            } catch (NoSuchFieldError e) {
            }
            try {
                f14x46dd5024[RadioState.RADIO_OFF.ordinal()] = 2;
            } catch (NoSuchFieldError e2) {
            }
        }
    }

    private class SSTBroadcastReceiver extends BroadcastReceiver {
        private SSTBroadcastReceiver() {
        }

        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals("android.intent.action.CONFIGURATION_CHANGED")) {
                GsmServiceStateTracker.this.updateSpnDisplay();
            } else if (action.equals(GsmServiceStateTracker.INTENT_WFC_SWITCH_PROFILE)) {
                byte[] data = intent.getByteArrayExtra("oem_request");
                GsmServiceStateTracker.isWfcWifiOnlyMode = false;
                GsmServiceStateTracker.this.mWfcPrefMode = data[4];
                GsmServiceStateTracker.this.mWfcStatus = data[5];
                GsmServiceStateTracker.this.log("status has : " + GsmServiceStateTracker.this.mWfcStatus);
                GsmServiceStateTracker.this.log("prefMode has : " + GsmServiceStateTracker.this.mWfcPrefMode);
                if (GsmServiceStateTracker.this.mWfcStatus == (byte) 1 && GsmServiceStateTracker.this.mWfcPrefMode == (byte) 3) {
                    GsmServiceStateTracker.isWfcWifiOnlyMode = true;
                }
            }
        }
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
        if ("WIPINET_VAL".equals(id)) {
            return this.mWipiNetValInit;
        }
        return null;
    }

    public GsmServiceStateTracker(GSMPhone phone) {
        boolean z;
        boolean z2 = true;
        super(phone, phone.mCi, new CellInfoGsm());
        String salesCode = SystemProperties.get("ro.csc.sales_code");
        this.mPhone = phone;
        this.mCellLoc = new GsmCellLocation();
        this.mNewCellLoc = new GsmCellLocation();
        this.mWakeLock = ((PowerManager) phone.getContext().getSystemService("power")).newWakeLock(1, WAKELOCK_TAG);
        this.mCi.registerForAvailable(this, 13, null);
        this.mCi.registerForRadioStateChanged(this, 1, null);
        this.mCi.registerForVoiceNetworkStateChanged(this, 2, null);
        this.mCi.setOnNITZTime(this, 11, null);
        this.mCi.setOnRestrictedStateChanged(this, 23, null);
        phone.getCallTracker().registerForVoiceCallEnded(this, Threads.ALERT_SEVERE_THREAD, null);
        if (Global.getInt(phone.getContext().getContentResolver(), "airplane_mode_on", 0) <= 0) {
            z = true;
        } else {
            z = false;
        }
        this.mDesiredPowerState = z;
        this.mCr = phone.getContext().getContentResolver();
        this.mCr.registerContentObserver(Global.getUriFor("auto_time"), true, this.mAutoTimeObserver);
        this.mCr.registerContentObserver(Global.getUriFor("auto_time_zone"), true, this.mAutoTimeZoneObserver);
        if ("2GNRP".equals(CscFeature.getInstance().getString("CscFeature_RIL_FakeRoamingOption4"))) {
            this.mCr.registerContentObserver(Secure.getUriFor("data_national_roaming_mode"), true, this.mDataNationalRoamingObserver);
        }
        setSignalStrengthDefaultValues();
        updateRuimLoadedEvent();
        IntentFilter filter = new IntentFilter();
        filter.addAction("android.intent.action.LOCALE_CHANGED");
        filter.addAction("android.intent.action.ACTION_RADIO_OFF");
        this.PrevMcc = "";
        filter.addAction("android.intent.action.SIM_STATE_CHANGED");
        if (CscFeature.getInstance().getEnableStatus("CscFeature_RIL_EnableVoicePriority")) {
            filter.addAction("android.intent.action.SCREEN_ON");
            filter.addAction("android.intent.action.SCREEN_OFF");
            if (CscFeature.getInstance().getEnableStatus("CscFeature_RIL_EnableVoicePriority")) {
                filter.addAction("android.intent.action.ANY_DATA_STATE");
                filter.addAction("android.net.conn.TETHER_STATE_CHANGED");
            } else {
                filter.addAction("jp.co.nttdocomo.lcsapp.ACTION_STATUS_CHANGED");
            }
        }
        if ("CHN".equals(salesCode) || "CHC".equals(salesCode) || "CHU".equals(salesCode) || "CHM".equals(salesCode) || "CTC".equals(salesCode)) {
            filter.addAction("ACTION_SET_PROPERTY_STATE");
            if ("CTC".equals(salesCode)) {
                filter.addAction("android.intent.action.AIRPLANE_MODE");
            }
        }
        if (("DGG".equals("DGG") && "dsds".equals(SystemProperties.get("persist.radio.multisim.config"))) || isTwochipDsdsOnRoamingModel()) {
            filter.addAction("android.intent.action.PHONE_STATE");
            filter.addAction("android.intent.action.DUOS_CP_CTRL_BY_CALL");
        }
        this.mPhone.getContext().registerReceiver(this.mIntentReceiver, filter);
        if (TelephonyManager.getDefault().getPhoneCount() > 1) {
            if (this.mPhone.getPhoneId() == 1) {
                this.mPhoneOnMode = true;
                if (MultiSimManager.getInsertedSimCount() == 2) {
                    this.mPhoneOnMode = System.getInt(this.mPhone.getContext().getContentResolver(), "phone2_on", 1) != 0;
                }
            } else {
                this.mPhoneOnMode = true;
                if (MultiSimManager.getInsertedSimCount() == 2) {
                    this.mPhoneOnMode = System.getInt(this.mPhone.getContext().getContentResolver(), "phone1_on", 1) != 0;
                }
            }
            if ("Combination".equals("Combination") || ("Strawberry".equals("Combination") && "DCG".equals("DGG"))) {
                log("force radio on");
                this.mPhoneOnMode = true;
            }
            if (!(this.mDesiredPowerState && this.mPhoneOnMode)) {
                z2 = false;
            }
            this.mDesiredPowerState = z2;
        }
    }

    public void dispose() {
        checkCorrectThread();
        log("ServiceStateTracker dispose");
        this.mCi.unregisterForAvailable(this);
        this.mCi.unregisterForRadioStateChanged(this);
        this.mCi.unregisterForVoiceNetworkStateChanged(this);
        if (this.mUiccApplcation != null) {
            this.mUiccApplcation.unregisterForReady(this);
        }
        if (this.mIccRecords != null) {
            this.mIccRecords.unregisterForRecordsLoaded(this);
        }
        this.mCi.unSetOnRestrictedStateChanged(this);
        this.mCi.unSetOnNITZTime(this);
        this.mCr.unregisterContentObserver(this.mAutoTimeObserver);
        this.mCr.unregisterContentObserver(this.mAutoTimeZoneObserver);
        this.mPhone.getContext().unregisterReceiver(this.mIntentReceiver);
        if ("2GNRP".equals(CscFeature.getInstance().getString("CscFeature_RIL_FakeRoamingOption4"))) {
            this.mCr.unregisterContentObserver(this.mDataNationalRoamingObserver);
        }
        this.mPhone.getCallTracker().unregisterForVoiceCallEnded(this);
        super.dispose();
    }

    protected void finalize() {
        log("finalize");
    }

    protected Phone getPhone() {
        return this.mPhone;
    }

    public void handleMessage(Message msg) {
        String salesCode = SystemProperties.get("ro.csc.sales_code");
        if (this.mPhone.mIsTheCurrentActivePhone) {
            AsyncResult ar;
            String bootModem;
            switch (msg.what) {
                case 0:
                    log("EVENT_EMERGENCY_TIMEOUT. Screen[" + mScreenState + "]");
                    emergencyDataOpened = false;
                    this.mPhone.notifyDataConnection("LCSAPP_TERMINATED");
                    return;
                case 1:
                    setPowerStateToDesired();
                    pollState();
                    this.mPendingRadioPowerOffAfterHangup = 0;
                    if (isWfcWifiOnlyMode) {
                        Rlog.d(LOG_TAG, "isWfcWifiOnlyMode: " + isWfcWifiOnlyMode);
                        this.mPhone.notifyServiceStateChanged(this.mSS);
                        return;
                    }
                    return;
                case 2:
                    pollState();
                    return;
                case 3:
                    if (this.mCi.getRadioState().isOn()) {
                        onSignalStrengthResult(msg.obj, true);
                        queueNextSignalStrengthPoll();
                        return;
                    }
                    return;
                case 4:
                case 5:
                case 6:
                case 14:
                    handlePollStateResult(msg.what, (AsyncResult) msg.obj);
                    return;
                case 10:
                    this.mCi.getSignalStrength(obtainMessage(3));
                    return;
                case 11:
                    ar = (AsyncResult) msg.obj;
                    String nitzString = ((Object[]) ar.result)[0];
                    long nitzReceiveTime = ((Long) ((Object[]) ar.result)[1]).longValue();
                    if ("CTC".equals(salesCode)) {
                        this.mIsNitzReceived = true;
                    }
                    setTimeFromNITZString(nitzString, nitzReceiveTime);
                    return;
                case 12:
                    ar = (AsyncResult) msg.obj;
                    this.mDontPollSignalStrength = true;
                    onSignalStrengthResult(ar, true);
                    return;
                case 13:
                    return;
                case 15:
                    ar = (AsyncResult) msg.obj;
                    if (ar.exception == null) {
                        String[] states = (String[]) ar.result;
                        int lac = -1;
                        int cid = -1;
                        if (states.length >= 3) {
                            try {
                                if (states[1] != null && states[1].length() > 0) {
                                    lac = Integer.parseInt(states[1], 16);
                                }
                                if (states[2] != null && states[2].length() > 0) {
                                    cid = Integer.parseInt(states[2], 16);
                                }
                            } catch (NumberFormatException ex) {
                                Rlog.w(LOG_TAG, "error parsing location: " + ex);
                            }
                        }
                        this.mCellLoc.setLacAndCid(lac, cid);
                        this.mPhone.notifyLocationChanged();
                    }
                    disableSingleLocationUpdate();
                    return;
                case 16:
                    log("EVENT_SIM_RECORDS_LOADED: what=" + msg.what);
                    this.mPhone.notifyOtaspChanged(3);
                    this.IsSIMLoadDone = true;
                    updatePhoneObject();
                    updateSpnWithEons(this.mSS, this.mCellLoc);
                    updateSpnDisplay();
                    return;
                case 17:
                    if (!this.mPhone.getContext().getResources().getBoolean(17956938)) {
                        this.mPhone.restoreSavedNetworkSelection(null);
                    }
                    pollState();
                    queueNextSignalStrengthPoll();
                    return;
                case 18:
                    if (((AsyncResult) msg.obj).exception == null) {
                        this.mCi.getVoiceRegistrationState(obtainMessage(15, null));
                        return;
                    }
                    return;
                case 19:
                    ar = (AsyncResult) msg.obj;
                    if (ar.exception == null) {
                        this.mPreferredNetworkType = ((int[]) ar.result)[0];
                        SystemProperties.get("ril.dualmode.network-reset", "false");
                    } else {
                        this.mPreferredNetworkType = 7;
                    }
                    Message message = obtainMessage(20, ar.userObj);
                    int toggledNetworkType = 7;
                    if ("CHN".equals(salesCode) || "CHC".equals(salesCode) || "CHU".equals(salesCode) || "CHM".equals(salesCode)) {
                        toggledNetworkType = 9;
                        log("[CHN] set toggledNetworkType as LWG");
                    }
                    String currenModem = SystemProperties.get("persist.radio.boot.modem", "");
                    this.mCi.setPreferredNetworkType(toggledNetworkType, message);
                    return;
                case 20:
                    this.mCi.setPreferredNetworkType(this.mPreferredNetworkType, obtainMessage(21, ((AsyncResult) msg.obj).userObj));
                    return;
                case 21:
                    ar = (AsyncResult) msg.obj;
                    if (ar.userObj != null) {
                        AsyncResult.forMessage((Message) ar.userObj).exception = ar.exception;
                        ((Message) ar.userObj).sendToTarget();
                        return;
                    }
                    return;
                case 22:
                    if (this.mSS != null) {
                        if (!isGprsConsistent(this.mSS.getDataRegState(), this.mSS.getVoiceRegState())) {
                            GsmCellLocation loc = (GsmCellLocation) this.mPhone.getCellLocation();
                            Object[] objArr = new Object[2];
                            objArr[0] = this.mSS.getOperatorNumeric();
                            objArr[1] = Integer.valueOf(loc != null ? loc.getCid() : -1);
                            EventLog.writeEvent(EventLogTags.DATA_NETWORK_REGISTRATION_FAIL, objArr);
                            this.mReportedGprsNoReg = true;
                        }
                    }
                    this.mStartedGprsRegCheck = false;
                    return;
                case 23:
                    log("EVENT_RESTRICTED_STATE_CHANGED");
                    return;
                case WspTypeDecoder.WSP_HEADER_IF_UNMODIFIED_SINCE /*27*/:
                    loge("EVENT_RUIM_RECORDS_LOADED ");
                    UiccCardApplication cdmaUiccApp = this.mUiccController.getUiccCardApplication(2);
                    if (cdmaUiccApp == null) {
                        log("cdmaUiccApp is Null. ");
                        return;
                    }
                    IccRecords mRuimRecords = cdmaUiccApp.getIccRecords();
                    if (mRuimRecords != null) {
                        RuimRecords ruim = (RuimRecords) mRuimRecords;
                        if (ruim != null) {
                            this.mMdn = ruim.getMdn();
                            this.mMin = ruim.getMin();
                            if (!"3".equals(SystemProperties.get("ril.otasp_state"))) {
                                updateOtaspState();
                                return;
                            }
                            return;
                        }
                        return;
                    }
                    return;
                case WspTypeDecoder.WSP_HEADER_WWW_AUTHENTICATE /*45*/:
                    log("EVENT_CHANGE_IMS_STATE:");
                    setPowerStateToDesired();
                    return;
                case Threads.ALERT_SEVERE_THREAD /*102*/:
                    if (this.mPendingRadioPowerOffAfterHangup != 0) {
                        log("waiting before radio turn off");
                        this.mPendingRadioPowerOffAfterHangup = 0;
                        powerOffRadioSafely(this.mPhone.mDcTracker);
                        return;
                    }
                    return;
                case Threads.ALERT_AMBER_THREAD /*103*/:
                    log("EVENT_LU_REJECT_CAUSE");
                    if (this.mWakeLock.isHeld()) {
                        this.mWakeLock.release();
                    }
                    dismissDeniedDialog();
                    this.after2min = true;
                    updateSpnDisplay();
                    return;
                case 105:
                    String[] res = (String[]) msg.obj;
                    GetTimezoneInfoUsingMcc(res[0], true, true, res[1]);
                    return;
                case 107:
                    ar = (AsyncResult) msg.obj;
                    if (ar.exception == null) {
                        SyncPreferredNetworkType(((int[]) ar.result)[0]);
                        return;
                    } else {
                        log("LTE_ROAMING : Failed EVENT_GET_PREF_NETWORK_TYPE_DONE");
                        return;
                    }
                case 109:
                    log("LTE_ROAMING : Received EVENT_HOME_NETWORK_NOTI");
                    notiHomePlmn();
                    return;
                case CallFailCause.KTF_FAIL_CAUSE_115 /*115*/:
                    log("[DUALMODE] Handle Message No Service Timer");
                    noserviceCount++;
                    isSetNoserviceTimer = false;
                    log("[DUALMODE] Timeout!, Cancel timer");
                    if (this.mPendingIntent == null || this.mAlarmManager == null) {
                        log("[DUALMODE] Already canceled.");
                    } else {
                        log("[DUALMODE] Alarm should be canceled.");
                        this.mAlarmManager.cancel(this.mPendingIntent);
                    }
                    if (this.mSS.getVoiceRegState() == 1 && !this.onOffLock) {
                        log("[DUALMODE] Acquire wakelock! :" + this.onOffLock);
                        this.mWakeLock.acquire();
                        this.onOffLock = true;
                        return;
                    }
                    return;
                case 117:
                    ar = (AsyncResult) msg.obj;
                    bootModem = SystemProperties.get("persist.radio.boot.modem", "");
                    String imsi = ((TelephonyManager) this.mPhone.getContext().getSystemService("phone")).getSubscriberId();
                    if (ar.exception == null) {
                        this.mPreferredNetworkType = ((int[]) ar.result)[0];
                    } else if (BOOT_WITH_TD.equals(bootModem)) {
                        this.mPreferredNetworkType = 23;
                    } else if ("2".equals(bootModem)) {
                        this.mPreferredNetworkType = 3;
                    }
                    if (this.mPreferredNetworkType == 23 && BOOT_WITH_TD.equals(bootModem)) {
                        log("[DUALMODE] Current pref mode : TDSCDMA, change to WCDMA pref");
                        if (imsi == null || !(imsi.startsWith("46001") || imsi.startsWith("46009"))) {
                            this.mPreferredNetworkType = 3;
                        } else {
                            log("[DUALMODE] CU SIM -> GSM_ONLY");
                            this.mPreferredNetworkType = 1;
                        }
                        SystemProperties.set("persist.radio.tdscdma_present", TDSCDMA_NOT_SUPPORT);
                        this.mCi.setPreferredNetworkType(this.mPreferredNetworkType, null);
                        SystemProperties.set("persist.radio.boot.modem", "2");
                        return;
                    } else if (this.mPreferredNetworkType == 23 || !"2".equals(bootModem)) {
                        log("[DUALMODE] Current pref mode : Not T/G, W/G so make change mode from boot modem");
                        if (BOOT_WITH_TD.equals(bootModem)) {
                            if (imsi == null || !(imsi.startsWith("46001") || imsi.startsWith("46009"))) {
                                this.mPreferredNetworkType = 3;
                            } else {
                                log("[DUALMODE] CU SIM -> GSM_ONLY");
                                this.mPreferredNetworkType = 1;
                            }
                            SystemProperties.set("persist.radio.tdscdma_present", TDSCDMA_NOT_SUPPORT);
                            SystemProperties.set("persist.radio.boot.modem", "2");
                        } else if ("2".equals(bootModem)) {
                            this.mPreferredNetworkType = 23;
                            SystemProperties.set("persist.radio.tdscdma_present", "2");
                            SystemProperties.set("persist.radio.boot.modem", BOOT_WITH_TD);
                        }
                        this.mCi.setPreferredNetworkType(this.mPreferredNetworkType, null);
                        return;
                    } else {
                        log("[DUALMODE] Current pref mode : Not TDSCDMA, change to TDSCDMA pref");
                        this.mPreferredNetworkType = 23;
                        SystemProperties.set("persist.radio.tdscdma_present", "2");
                        this.mCi.setPreferredNetworkType(this.mPreferredNetworkType, null);
                        SystemProperties.set("persist.radio.boot.modem", BOOT_WITH_TD);
                        return;
                    }
                case 118:
                    ar = (AsyncResult) msg.obj;
                    bootModem = SystemProperties.get("persist.radio.boot.modem", "");
                    String sImsi = ((TelephonyManager) this.mPhone.getContext().getSystemService("phone")).getSubscriberId();
                    if (ar.exception == null) {
                        this.mPreferredNetworkType = ((int[]) ar.result)[0];
                    } else if (BOOT_WITH_TD.equals(bootModem)) {
                        this.mPreferredNetworkType = 23;
                    } else if ("2".equals(bootModem)) {
                        this.mPreferredNetworkType = 3;
                    }
                    if (sImsi != null && (sImsi.startsWith("46001") || sImsi.startsWith("46009"))) {
                        log("[DUALMODE] EVENT_CHECK_NETWORK_MODE: CU SIM Case");
                        if (this.mPreferredNetworkType != 1) {
                            this.mPreferredNetworkType = 1;
                            SystemProperties.set("persist.radio.tdscdma_present", TDSCDMA_NOT_SUPPORT);
                            this.mCi.setPreferredNetworkType(this.mPreferredNetworkType, null);
                        }
                        SystemProperties.set("persist.radio.boot.modem", "2");
                        return;
                    } else if (this.mPreferredNetworkType != 23 && BOOT_WITH_TD.equals(bootModem)) {
                        log("[DUALMODE] bootModem and PreferredNetworkType is not same Set T/G");
                        this.mPreferredNetworkType = 23;
                        SystemProperties.set("persist.radio.tdscdma_present", "2");
                        this.mCi.setPreferredNetworkType(this.mPreferredNetworkType, null);
                        return;
                    } else if (this.mPreferredNetworkType != 3 && "2".equals(bootModem)) {
                        this.mPreferredNetworkType = 3;
                        SystemProperties.set("persist.radio.tdscdma_present", TDSCDMA_NOT_SUPPORT);
                        log("[DUALMODE] bootModem and PreferredNetworkType is not same Set W/G");
                        this.mCi.setPreferredNetworkType(this.mPreferredNetworkType, null);
                        return;
                    } else {
                        return;
                    }
                case 119:
                    log("[DUALMODE] Handle message to recover network in dualmode");
                    SystemProperties.set("ril.dualmode.network-reset", "true");
                    reRegisterNetwork(null);
                    return;
                case EVENT_NETWORK_STATE_CHANGED_BY_RESCAN /*4000*/:
                    this.NetworkStateChangedByRescanDialog = true;
                    this.IsFlightMode = false;
                    pollState();
                    return;
                default:
                    super.handleMessage(msg);
                    return;
            }
        }
        Rlog.e(LOG_TAG, "Received message " + msg + "[" + msg.what + "] while being destroyed. Ignoring.");
    }

    protected void setPowerStateToDesired() {
        log("mDeviceShuttingDown = " + this.mDeviceShuttingDown);
        log("mDesiredPowerState = " + this.mDesiredPowerState);
        log("getRadioState = " + this.mCi.getRadioState());
        log("mPowerOffDelayNeed = " + this.mPowerOffDelayNeed);
        log("mAlarmSwitch = " + this.mAlarmSwitch);
        if (TelephonyManager.getDefault().getPhoneCount() > 1) {
            boolean z;
            boolean bAirplaneMode = System.getInt(this.mPhone.getContext().getContentResolver(), "airplane_mode_on", 0) != 0;
            if (bAirplaneMode) {
                if (bAirplaneMode) {
                    z = false;
                } else {
                    z = true;
                }
                this.mDesiredPowerState = z;
                this.mPreviousAirplanemode = true;
            } else if (!bAirplaneMode && this.mPreviousAirplanemode) {
                if (bAirplaneMode) {
                    z = false;
                } else {
                    z = true;
                }
                this.mDesiredPowerState = z;
                this.mPreviousAirplanemode = false;
            }
            if (this.mPhone.getPhoneId() == 1) {
                this.mPhoneOnMode = true;
                if (MultiSimManager.getInsertedSimCount() == 2) {
                    if (System.getInt(this.mPhone.getContext().getContentResolver(), "phone2_on", 1) != 0) {
                        z = true;
                    } else {
                        z = false;
                    }
                    this.mPhoneOnMode = z;
                }
            } else {
                this.mPhoneOnMode = true;
                if (MultiSimManager.getInsertedSimCount() == 2) {
                    this.mPhoneOnMode = System.getInt(this.mPhone.getContext().getContentResolver(), "phone1_on", 1) != 0;
                }
            }
            if ("Combination".equals("Combination") || ("Strawberry".equals("Combination") && "DCG".equals("DGG"))) {
                log("force radio on");
                this.mPhoneOnMode = true;
            }
            z = this.mDesiredPowerState && this.mPhoneOnMode;
            this.mDesiredPowerState = z;
            log("setPowerStateToDesired(), mPhone.getSimSlot():" + this.mPhone.getPhoneId() + ": mDesiredPowerState=" + this.mDesiredPowerState + " mPhoneOnMode=" + this.mPhoneOnMode + "mCi.getRadioState()=" + this.mCi.getRadioState() + " mCi.getRadioState().isOn()= " + this.mCi.getRadioState().isOn() + "bAirplaneMode=" + bAirplaneMode);
        }
        if (this.mAlarmSwitch) {
            log("mAlarmSwitch == true");
            ((AlarmManager) this.mPhone.getContext().getSystemService("alarm")).cancel(this.mRadioOffIntent);
            this.mAlarmSwitch = false;
        }
        if (this.mDesiredPowerState && this.mCi.getRadioState() == RadioState.RADIO_OFF) {
            this.mCi.setRadioPower(true, null);
        } else if (this.mDesiredPowerState || !this.mCi.getRadioState().isOn()) {
            if (this.mDeviceShuttingDown && this.mCi.getRadioState().isAvailable()) {
                this.mCi.requestShutdown(null);
            }
        } else if (!this.mPowerOffDelayNeed) {
            powerOffRadioSafely(this.mPhone.mDcTracker);
        } else if (!this.mImsRegistrationOnOff || this.mAlarmSwitch) {
            powerOffRadioSafely(this.mPhone.mDcTracker);
        } else {
            log("mImsRegistrationOnOff == true");
            Context context = this.mPhone.getContext();
            AlarmManager am = (AlarmManager) context.getSystemService("alarm");
            this.mRadioOffIntent = PendingIntent.getBroadcast(context, 0, new Intent("android.intent.action.ACTION_RADIO_OFF"), 0);
            this.mAlarmSwitch = true;
            log("Alarm setting");
            am.set(2, SystemClock.elapsedRealtime() + 3000, this.mRadioOffIntent);
        }
    }

    protected void hangupAndPowerOff() {
        this.mCi.setRadioPower(false, null);
    }

    protected boolean hangupBeforeDeactivePDP() {
        this.mPendingRadioPowerOffAfterHangup = 0;
        log("hangupBeforeDeactivePDP() : previous pending value=" + this.mPendingRadioPowerOffAfterHangup);
        if (this.mPhone.isInCall()) {
            this.mPhone.mCT.mRingingCall.hangupIfAlive();
            this.mPhone.mCT.mBackgroundCall.hangupIfAlive();
            this.mPhone.mCT.mForegroundCall.hangupIfAlive();
            this.mPendingRadioPowerOffAfterHangup++;
        }
        if (this.mPendingRadioPowerOffAfterHangup != 0) {
            return true;
        }
        return false;
    }

    /* JADX WARNING: inconsistent code. */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    protected void updateSpnDisplay() {
        /*
        r25 = this;
        r0 = r25;
        r4 = r0.mIccRecords;
        r13 = 0;
        r17 = 0;
        r21 = "ro.csc.sales_code";
        r16 = android.os.SystemProperties.get(r21);
        r7 = 0;
        r3 = "isValidPlmn";
        r8 = 1;
        r0 = r25;
        r0 = r0.mPhone;
        r21 = r0;
        r21 = r21.isWfcRegistered();
        isWFCReigstered = r21;
        if (r4 == 0) goto L_0x01f4;
    L_0x001f:
        r0 = r25;
        r0 = r0.mSS;
        r21 = r0;
        r21 = r21.getOperatorNumeric();
        r0 = r21;
        r15 = r4.getDisplayRule(r0);
    L_0x002f:
        r0 = r25;
        r0 = r0.mSS;
        r21 = r0;
        r21 = r21.getVoiceRegState();
        r22 = 1;
        r0 = r21;
        r1 = r22;
        if (r0 == r1) goto L_0x0053;
    L_0x0041:
        r0 = r25;
        r0 = r0.mSS;
        r21 = r0;
        r21 = r21.getVoiceRegState();
        r22 = 2;
        r0 = r21;
        r1 = r22;
        if (r0 != r1) goto L_0x0208;
    L_0x0053:
        r17 = 1;
        r0 = r25;
        r0 = r0.mEmergencyOnly;
        r21 = r0;
        if (r21 == 0) goto L_0x01f7;
    L_0x005d:
        r21 = android.content.res.Resources.getSystem();
        r22 = 17040280; // 0x1040398 float:2.424715E-38 double:8.419017E-317;
        r21 = r21.getText(r22);
        r13 = r21.toString();
    L_0x006c:
        r21 = new java.lang.StringBuilder;
        r21.<init>();
        r22 = "updateSpnDisplay: radio is on but out of service, set plmn='";
        r21 = r21.append(r22);
        r0 = r21;
        r21 = r0.append(r13);
        r22 = "'";
        r21 = r21.append(r22);
        r21 = r21.toString();
        r0 = r25;
        r1 = r21;
        r0.log(r1);
    L_0x008e:
        r21 = com.sec.android.app.CscFeature.getInstance();
        r22 = "CscFeature_RIL_PLMNFaking4Mvno";
        r21 = r21.getEnableStatus(r22);
        if (r21 == 0) goto L_0x00cd;
    L_0x009a:
        r0 = r25;
        r0 = r0.mPhone;
        r21 = r0;
        r21 = r21.getContext();
        r22 = "phone";
        r20 = r21.getSystemService(r22);
        r20 = (android.telephony.TelephonyManager) r20;
        r5 = r20.getSubscriberId();
        r0 = r25;
        r0 = r0.mSS;
        r21 = r0;
        r12 = r21.getOperatorNumeric();
        r9 = new com.android.internal.telephony.MVNOSupportList;
        r9.<init>();
        if (r9 != 0) goto L_0x00c6;
    L_0x00c1:
        r9 = new com.android.internal.telephony.MVNOSupportList;
        r9.<init>();
    L_0x00c6:
        r11 = r9.getMVNOName(r12, r5);
        if (r11 == 0) goto L_0x00cd;
    L_0x00cc:
        r13 = r11;
    L_0x00cd:
        if (r4 == 0) goto L_0x010c;
    L_0x00cf:
        r0 = r25;
        r0 = r0.mSS;
        r21 = r0;
        r21 = r21.getOperatorNumeric();
        r0 = r21;
        r4.setSpnDynamic(r0);
        r0 = r25;
        r0 = r0.mSS;
        r21 = r0;
        r21 = r21.getOperatorNumeric();
        r0 = r21;
        r15 = r4.getDisplayRule(r0);
        r0 = r25;
        r0 = r0.mSS;
        r21 = r0;
        r21 = r21.getVoiceRegState();
        if (r21 != 0) goto L_0x010c;
    L_0x00fa:
        r21 = android.text.TextUtils.isEmpty(r13);
        if (r21 != 0) goto L_0x0261;
    L_0x0100:
        r21 = r15 & 2;
        r22 = 2;
        r0 = r21;
        r1 = r22;
        if (r0 != r1) goto L_0x0261;
    L_0x010a:
        r17 = 1;
    L_0x010c:
        r0 = r25;
        r0 = r0.mIccRecords;
        r21 = r0;
        if (r21 == 0) goto L_0x011c;
    L_0x0114:
        r0 = r25;
        r0 = r0.IsSIMLoadDone;
        r21 = r0;
        if (r21 != 0) goto L_0x029a;
    L_0x011c:
        r21 = "CTC";
        r0 = r21;
        r1 = r16;
        r21 = r0.equals(r1);
        if (r21 != 0) goto L_0x029a;
    L_0x0128:
        r14 = 0;
        r0 = r25;
        r0 = r0.mEmergencyOnly;
        r21 = r0;
        if (r21 == 0) goto L_0x0265;
    L_0x0131:
        r0 = r25;
        r0 = r0.mCi;
        r21 = r0;
        r21 = r21.getRadioState();
        r21 = r21.isOn();
        if (r21 == 0) goto L_0x0265;
    L_0x0141:
        r21 = android.content.res.Resources.getSystem();
        r22 = 17040280; // 0x1040398 float:2.424715E-38 double:8.419017E-317;
        r21 = r21.getText(r22);
        r14 = r21.toString();
    L_0x0150:
        r21 = new java.lang.StringBuilder;
        r21.<init>();
        r22 = "updateSpnDisplay: SIM records are not loaded - plmn='";
        r21 = r21.append(r22);
        r0 = r21;
        r21 = r0.append(r14);
        r22 = "'";
        r21 = r21.append(r22);
        r21 = r21.toString();
        r0 = r25;
        r1 = r21;
        r0.log(r1);
        r0 = r25;
        r0 = r0.mCurPlmn;
        r21 = r0;
        r0 = r21;
        r21 = android.text.TextUtils.equals(r14, r0);
        if (r21 != 0) goto L_0x01ef;
    L_0x0180:
        r6 = new android.content.Intent;
        r21 = "android.provider.Telephony.SPN_STRINGS_UPDATED";
        r0 = r21;
        r6.<init>(r0);
        r0 = r25;
        r0 = r0.simSlotCount;
        r21 = r0;
        r22 = 2;
        r0 = r21;
        r1 = r22;
        if (r0 >= r1) goto L_0x019e;
    L_0x0197:
        r21 = 536870912; // 0x20000000 float:1.0842022E-19 double:2.652494739E-315;
        r0 = r21;
        r6.addFlags(r0);
    L_0x019e:
        r21 = "showSpn";
        r22 = 0;
        r0 = r21;
        r1 = r22;
        r6.putExtra(r0, r1);
        r21 = "spn";
        r22 = "";
        r0 = r21;
        r1 = r22;
        r6.putExtra(r0, r1);
        r21 = "showPlmn";
        r22 = 1;
        r0 = r21;
        r1 = r22;
        r6.putExtra(r0, r1);
        r21 = "isValidPlmn";
        r0 = r21;
        r6.putExtra(r0, r8);
        r21 = "plmn";
        r0 = r21;
        r6.putExtra(r0, r14);
        r0 = r25;
        r0 = r0.mPhone;
        r21 = r0;
        r21 = r21.getPhoneId();
        r0 = r21;
        android.telephony.SubscriptionManager.putPhoneIdAndSubIdExtra(r6, r0);
        r0 = r25;
        r0 = r0.mPhone;
        r21 = r0;
        r21 = r21.getContext();
        r22 = android.os.UserHandle.ALL;
        r0 = r21;
        r1 = r22;
        r0.sendStickyBroadcastAsUser(r6, r1);
    L_0x01ef:
        r0 = r25;
        r0.mCurPlmn = r14;
    L_0x01f3:
        return;
    L_0x01f4:
        r15 = 0;
        goto L_0x002f;
    L_0x01f7:
        r21 = android.content.res.Resources.getSystem();
        r22 = 17040252; // 0x104037c float:2.424707E-38 double:8.419003E-317;
        r21 = r21.getText(r22);
        r13 = r21.toString();
        goto L_0x006c;
    L_0x0208:
        r0 = r25;
        r0 = r0.mSS;
        r21 = r0;
        r21 = r21.getVoiceRegState();
        if (r21 != 0) goto L_0x0235;
    L_0x0214:
        r0 = r25;
        r0 = r0.mSS;
        r21 = r0;
        r13 = r21.getOperatorAlphaLong();
        r21 = android.text.TextUtils.isEmpty(r13);
        if (r21 != 0) goto L_0x0232;
    L_0x0224:
        r21 = r15 & 2;
        r22 = 2;
        r0 = r21;
        r1 = r22;
        if (r0 != r1) goto L_0x0232;
    L_0x022e:
        r17 = 1;
    L_0x0230:
        goto L_0x008e;
    L_0x0232:
        r17 = 0;
        goto L_0x0230;
    L_0x0235:
        r21 = new java.lang.StringBuilder;
        r21.<init>();
        r22 = "updateSpnDisplay: radio is off w/ showPlmn=";
        r21 = r21.append(r22);
        r0 = r21;
        r1 = r17;
        r21 = r0.append(r1);
        r22 = " plmn=";
        r21 = r21.append(r22);
        r0 = r21;
        r21 = r0.append(r13);
        r21 = r21.toString();
        r0 = r25;
        r1 = r21;
        r0.log(r1);
        goto L_0x008e;
    L_0x0261:
        r17 = 0;
        goto L_0x010c;
    L_0x0265:
        r0 = r25;
        r0 = r0.mSS;
        r21 = r0;
        r21 = r21.getState();
        r22 = 1;
        r0 = r21;
        r1 = r22;
        if (r0 == r1) goto L_0x0289;
    L_0x0277:
        r0 = r25;
        r0 = r0.mSS;
        r21 = r0;
        r21 = r21.getState();
        r22 = 3;
        r0 = r21;
        r1 = r22;
        if (r0 != r1) goto L_0x0150;
    L_0x0289:
        r21 = android.content.res.Resources.getSystem();
        r22 = 17040252; // 0x104037c float:2.424707E-38 double:8.419003E-317;
        r21 = r21.getText(r22);
        r14 = r21.toString();
        goto L_0x0150;
    L_0x029a:
        if (r4 == 0) goto L_0x046f;
    L_0x029c:
        r19 = r4.getServiceProviderName();
    L_0x02a0:
        r0 = r25;
        r0 = r0.mEmergencyOnly;
        r21 = r0;
        if (r21 != 0) goto L_0x0473;
    L_0x02a8:
        r21 = android.text.TextUtils.isEmpty(r19);
        if (r21 != 0) goto L_0x0473;
    L_0x02ae:
        r21 = r15 & 1;
        r22 = 1;
        r0 = r21;
        r1 = r22;
        if (r0 != r1) goto L_0x0473;
    L_0x02b8:
        r18 = 1;
    L_0x02ba:
        r21 = "CHM";
        r0 = r21;
        r1 = r16;
        r21 = r0.equals(r1);
        if (r21 != 0) goto L_0x033e;
    L_0x02c6:
        r21 = "TGY";
        r0 = r21;
        r1 = r16;
        r21 = r0.equals(r1);
        if (r21 != 0) goto L_0x033e;
    L_0x02d2:
        r21 = "BRI";
        r0 = r21;
        r1 = r16;
        r21 = r0.equals(r1);
        if (r21 != 0) goto L_0x033e;
    L_0x02de:
        r21 = "CWT";
        r0 = r21;
        r1 = r16;
        r21 = r0.equals(r1);
        if (r21 != 0) goto L_0x033e;
    L_0x02ea:
        r21 = "FET";
        r0 = r21;
        r1 = r16;
        r21 = r0.equals(r1);
        if (r21 != 0) goto L_0x033e;
    L_0x02f6:
        r21 = "TWM";
        r0 = r21;
        r1 = r16;
        r21 = r0.equals(r1);
        if (r21 != 0) goto L_0x033e;
    L_0x0302:
        r21 = "CHZ";
        r0 = r21;
        r1 = r16;
        r21 = r0.equals(r1);
        if (r21 != 0) goto L_0x033e;
    L_0x030e:
        r21 = "CHU";
        r0 = r21;
        r1 = r16;
        r21 = r0.equals(r1);
        if (r21 != 0) goto L_0x033e;
    L_0x031a:
        r21 = "CHC";
        r0 = r21;
        r1 = r16;
        r21 = r0.equals(r1);
        if (r21 != 0) goto L_0x033e;
    L_0x0326:
        r21 = "CHN";
        r0 = r21;
        r1 = r16;
        r21 = r0.equals(r1);
        if (r21 != 0) goto L_0x033e;
    L_0x0332:
        r21 = "CTC";
        r0 = r21;
        r1 = r16;
        r21 = r0.equals(r1);
        if (r21 == 0) goto L_0x03ea;
    L_0x033e:
        r21 = "gsm.sim.operator.numeric";
        r22 = "";
        r0 = r25;
        r1 = r21;
        r2 = r22;
        r10 = r0.getSystemProperty(r1, r2);
        r15 = 2;
        r0 = r25;
        r13 = r0.updateChinaSpnDisplay(r13);
        if (r10 == 0) goto L_0x047f;
    L_0x0355:
        r21 = android.text.TextUtils.isEmpty(r10);
        if (r21 != 0) goto L_0x047f;
    L_0x035b:
        r21 = "45400";
        r0 = r21;
        r21 = r0.equals(r10);
        if (r21 != 0) goto L_0x03a1;
    L_0x0365:
        r21 = "45402";
        r0 = r21;
        r21 = r0.equals(r10);
        if (r21 != 0) goto L_0x03a1;
    L_0x036f:
        r21 = "45410";
        r0 = r21;
        r21 = r0.equals(r10);
        if (r21 != 0) goto L_0x03a1;
    L_0x0379:
        r21 = "46605";
        r0 = r21;
        r21 = r0.equals(r10);
        if (r21 != 0) goto L_0x03a1;
    L_0x0383:
        r21 = "45418";
        r0 = r21;
        r21 = r0.equals(r10);
        if (r21 != 0) goto L_0x03a1;
    L_0x038d:
        r21 = "45416";
        r0 = r21;
        r21 = r0.equals(r10);
        if (r21 != 0) goto L_0x03a1;
    L_0x0397:
        r21 = "45419";
        r0 = r21;
        r21 = r0.equals(r10);
        if (r21 == 0) goto L_0x0477;
    L_0x03a1:
        r21 = android.text.TextUtils.isEmpty(r19);
        if (r21 != 0) goto L_0x0477;
    L_0x03a7:
        r0 = r25;
        r0 = r0.mSS;
        r21 = r0;
        r21 = r21.getRoaming();
        if (r21 != 0) goto L_0x0477;
    L_0x03b3:
        r15 = 1;
    L_0x03b4:
        r21 = new java.lang.StringBuilder;
        r21.<init>();
        r22 = "updateSpnDisplay : updateChinaSpnDisplay() rule : ";
        r21 = r21.append(r22);
        r0 = r21;
        r21 = r0.append(r15);
        r21 = r21.toString();
        r0 = r25;
        r1 = r21;
        r0.log(r1);
    L_0x03d0:
        r0 = r25;
        r0 = r0.mEmergencyOnly;
        r21 = r0;
        if (r21 != 0) goto L_0x04ad;
    L_0x03d8:
        r21 = android.text.TextUtils.isEmpty(r19);
        if (r21 != 0) goto L_0x04ad;
    L_0x03de:
        r21 = r15 & 1;
        r22 = 1;
        r0 = r21;
        r1 = r22;
        if (r0 != r1) goto L_0x04ad;
    L_0x03e8:
        r18 = 1;
    L_0x03ea:
        r21 = com.sec.android.app.CscFeature.getInstance();
        r22 = "CscFeature_RIL_IgnoreWrongNITZInformation";
        r21 = r21.getEnableStatus(r22);
        if (r21 == 0) goto L_0x040a;
    L_0x03f6:
        if (r13 == 0) goto L_0x040a;
    L_0x03f8:
        r0 = r25;
        r0 = r0.mSS;
        r21 = r0;
        r21 = r21.getOperatorNumeric();
        r0 = r25;
        r1 = r21;
        r13 = r0.checkIgnoreNITZ(r13, r1);
    L_0x040a:
        if (r19 == 0) goto L_0x0421;
    L_0x040c:
        r0 = r19;
        r21 = r0.equals(r13);
        if (r21 == 0) goto L_0x0421;
    L_0x0414:
        r21 = "spn string == plmn string, showing only plmn";
        r0 = r25;
        r1 = r21;
        r0.log(r1);
        r17 = 1;
        r18 = 0;
    L_0x0421:
        r0 = r25;
        r0 = r0.mCurShowPlmn;
        r21 = r0;
        r0 = r17;
        r1 = r21;
        if (r0 != r1) goto L_0x04b1;
    L_0x042d:
        r0 = r25;
        r0 = r0.mCurShowSpn;
        r21 = r0;
        r0 = r18;
        r1 = r21;
        if (r0 != r1) goto L_0x04b1;
    L_0x0439:
        r0 = r25;
        r0 = r0.mCurSpn;
        r21 = r0;
        r0 = r19;
        r1 = r21;
        r21 = android.text.TextUtils.equals(r0, r1);
        if (r21 == 0) goto L_0x04b1;
    L_0x0449:
        r0 = r25;
        r0 = r0.mCurPlmn;
        r21 = r0;
        r0 = r21;
        r21 = android.text.TextUtils.equals(r13, r0);
        if (r21 == 0) goto L_0x04b1;
    L_0x0457:
        r0 = r18;
        r1 = r25;
        r1.mCurShowSpn = r0;
        r0 = r17;
        r1 = r25;
        r1.mCurShowPlmn = r0;
        r0 = r19;
        r1 = r25;
        r1.mCurSpn = r0;
        r0 = r25;
        r0.mCurPlmn = r13;
        goto L_0x01f3;
    L_0x046f:
        r19 = "";
        goto L_0x02a0;
    L_0x0473:
        r18 = 0;
        goto L_0x02ba;
    L_0x0477:
        r15 = 2;
        r7 = 1;
        r18 = 0;
        r17 = 1;
        goto L_0x03b4;
    L_0x047f:
        r21 = "CSL";
        r0 = r21;
        r21 = android.text.TextUtils.equals(r13, r0);
        if (r21 == 0) goto L_0x03d0;
    L_0x0489:
        if (r17 == 0) goto L_0x03d0;
    L_0x048b:
        if (r18 != 0) goto L_0x03d0;
    L_0x048d:
        r0 = r25;
        r0 = r0.mSS;
        r21 = r0;
        r21 = r21.getRoaming();
        if (r21 != 0) goto L_0x03d0;
    L_0x0499:
        r21 = "HongKong CSL special requirment";
        r0 = r25;
        r1 = r21;
        r0.log(r1);
        r15 = 2;
        r17 = 1;
        r18 = 0;
        r15 = 0;
        r19 = 0;
        r13 = 0;
        goto L_0x03d0;
    L_0x04ad:
        r18 = 0;
        goto L_0x03ea;
    L_0x04b1:
        r0 = r25;
        r0 = r0.mSS;
        r21 = r0;
        r21 = r21.getVoiceRegState();
        if (r21 == 0) goto L_0x04c8;
    L_0x04bd:
        r21 = "ServiceState isn't in service, do not show spn";
        r0 = r25;
        r1 = r21;
        r0.log(r1);
        r18 = 0;
    L_0x04c8:
        r21 = "CTC";
        r0 = r21;
        r1 = r16;
        r21 = r0.equals(r1);
        if (r21 == 0) goto L_0x04dc;
    L_0x04d4:
        r21 = android.text.TextUtils.isEmpty(r13);
        if (r21 != 0) goto L_0x04dc;
    L_0x04da:
        r17 = 1;
    L_0x04dc:
        r21 = new java.lang.StringBuilder;
        r21.<init>();
        r22 = "updateSpnDisplay: changed sending intent rule=";
        r21 = r21.append(r22);
        r0 = r21;
        r21 = r0.append(r15);
        r22 = " showPlmn='%b' plmn='%s' showSpn='%b' spn='%s' phoneId='%d'";
        r21 = r21.append(r22);
        r21 = r21.toString();
        r22 = 5;
        r0 = r22;
        r0 = new java.lang.Object[r0];
        r22 = r0;
        r23 = 0;
        r24 = java.lang.Boolean.valueOf(r17);
        r22[r23] = r24;
        r23 = 1;
        r22[r23] = r13;
        r23 = 2;
        r24 = java.lang.Boolean.valueOf(r18);
        r22[r23] = r24;
        r23 = 3;
        r22[r23] = r19;
        r23 = 4;
        r0 = r25;
        r0 = r0.mPhone;
        r24 = r0;
        r24 = r24.getPhoneId();
        r24 = java.lang.Integer.valueOf(r24);
        r22[r23] = r24;
        r21 = java.lang.String.format(r21, r22);
        r0 = r25;
        r1 = r21;
        r0.log(r1);
        r6 = new android.content.Intent;
        r21 = "android.provider.Telephony.SPN_STRINGS_UPDATED";
        r0 = r21;
        r6.<init>(r0);
        r0 = r25;
        r0 = r0.simSlotCount;
        r21 = r0;
        r22 = 2;
        r0 = r21;
        r1 = r22;
        if (r0 >= r1) goto L_0x0552;
    L_0x054b:
        r21 = 536870912; // 0x20000000 float:1.0842022E-19 double:2.652494739E-315;
        r0 = r21;
        r6.addFlags(r0);
    L_0x0552:
        r21 = "showSpn";
        r0 = r21;
        r1 = r18;
        r6.putExtra(r0, r1);
        r21 = "spn";
        r0 = r21;
        r1 = r19;
        r6.putExtra(r0, r1);
        r21 = "showPlmn";
        r0 = r21;
        r1 = r17;
        r6.putExtra(r0, r1);
        r21 = "isValidPlmn";
        r0 = r21;
        r6.putExtra(r0, r8);
        r21 = "plmn";
        r0 = r21;
        r6.putExtra(r0, r13);
        r0 = r25;
        r0 = r0.mPhone;
        r21 = r0;
        r21 = r21.getPhoneId();
        r0 = r21;
        android.telephony.SubscriptionManager.putPhoneIdAndSubIdExtra(r6, r0);
        r0 = r25;
        r0 = r0.mPhone;
        r21 = r0;
        r21 = r21.getContext();
        r22 = android.os.UserHandle.ALL;
        r0 = r21;
        r1 = r22;
        r0.sendStickyBroadcastAsUser(r6, r1);
        r21 = android.telephony.TelephonyManager.getDefault();
        r21 = r21.getPhoneCount();
        r22 = 2;
        r0 = r21;
        r1 = r22;
        if (r0 != r1) goto L_0x05fc;
    L_0x05ad:
        if (r13 == 0) goto L_0x05fc;
    L_0x05af:
        r0 = r25;
        r0 = r0.mSS;
        r21 = r0;
        r21 = r21.getVoiceRegState();
        if (r21 == 0) goto L_0x05c7;
    L_0x05bb:
        r0 = r25;
        r0 = r0.mSS;
        r21 = r0;
        r21 = r21.getDataRegState();
        if (r21 != 0) goto L_0x05fc;
    L_0x05c7:
        r21 = 1;
        r0 = r25;
        r0 = r0.mPhone;
        r22 = r0;
        r22 = r22.getPhoneId();
        r0 = r21;
        r1 = r22;
        if (r0 != r1) goto L_0x060d;
    L_0x05d9:
        r21 = "persist.radio.plmnname_2";
        r0 = r21;
        android.os.SystemProperties.set(r0, r13);
        r21 = new java.lang.StringBuilder;
        r21.<init>();
        r22 = "SIM2 plmn : ";
        r21 = r21.append(r22);
        r0 = r21;
        r21 = r0.append(r13);
        r21 = r21.toString();
        r0 = r25;
        r1 = r21;
        r0.log(r1);
    L_0x05fc:
        r0 = r25;
        r0 = r0.mPhone;
        r21 = r0;
        r0 = r25;
        r0 = r0.mSS;
        r22 = r0;
        r21.notifyServiceStateChanged(r22);
        goto L_0x0457;
    L_0x060d:
        r21 = "persist.radio.plmnname_1";
        r0 = r21;
        android.os.SystemProperties.set(r0, r13);
        r21 = new java.lang.StringBuilder;
        r21.<init>();
        r22 = "SIM1 plmn : ";
        r21 = r21.append(r22);
        r0 = r21;
        r21 = r0.append(r13);
        r21 = r21.toString();
        r0 = r25;
        r1 = r21;
        r0.log(r1);
        goto L_0x05fc;
        */
        throw new UnsupportedOperationException("Method not decompiled: com.android.internal.telephony.gsm.GsmServiceStateTracker.updateSpnDisplay():void");
    }

    /* JADX WARNING: inconsistent code. */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    protected void handlePollStateResult(int r25, android.os.AsyncResult r26) {
        /*
        r24 = this;
        r0 = r26;
        r0 = r0.userObj;
        r20 = r0;
        r0 = r24;
        r0 = r0.mPollingContext;
        r21 = r0;
        r0 = r20;
        r1 = r21;
        if (r0 == r1) goto L_0x0013;
    L_0x0012:
        return;
    L_0x0013:
        r0 = r26;
        r0 = r0.exception;
        r20 = r0;
        if (r20 == 0) goto L_0x013c;
    L_0x001b:
        r6 = 0;
        r0 = r26;
        r0 = r0.exception;
        r20 = r0;
        r0 = r20;
        r0 = r0 instanceof com.android.internal.telephony.CommandException;
        r20 = r0;
        if (r20 == 0) goto L_0x0038;
    L_0x002a:
        r0 = r26;
        r0 = r0.exception;
        r20 = r0;
        r20 = (com.android.internal.telephony.CommandException) r20;
        r20 = (com.android.internal.telephony.CommandException) r20;
        r6 = r20.getCommandError();
    L_0x0038:
        r20 = com.android.internal.telephony.CommandException.Error.RADIO_NOT_AVAILABLE;
        r0 = r20;
        if (r6 != r0) goto L_0x0042;
    L_0x003e:
        r24.cancelPollState();
        goto L_0x0012;
    L_0x0042:
        r0 = r24;
        r0 = r0.mCi;
        r20 = r0;
        r20 = r20.getRadioState();
        r20 = r20.isOn();
        if (r20 != 0) goto L_0x0056;
    L_0x0052:
        r24.cancelPollState();
        goto L_0x0012;
    L_0x0056:
        r20 = com.android.internal.telephony.CommandException.Error.OP_NOT_ALLOWED_BEFORE_REG_NW;
        r0 = r20;
        if (r6 == r0) goto L_0x007c;
    L_0x005c:
        r20 = new java.lang.StringBuilder;
        r20.<init>();
        r21 = "RIL implementation has returned an error where it must succeed";
        r20 = r20.append(r21);
        r0 = r26;
        r0 = r0.exception;
        r21 = r0;
        r20 = r20.append(r21);
        r20 = r20.toString();
        r0 = r24;
        r1 = r20;
        r0.loge(r1);
    L_0x007c:
        r0 = r24;
        r0 = r0.mPollingContext;
        r20 = r0;
        r21 = 0;
        r22 = r20[r21];
        r22 = r22 + -1;
        r20[r21] = r22;
        r0 = r24;
        r0 = r0.mPollingContext;
        r20 = r0;
        r21 = 0;
        r20 = r20[r21];
        if (r20 != 0) goto L_0x0012;
    L_0x0096:
        r0 = r24;
        r0 = r0.mGsmRoaming;
        r20 = r0;
        if (r20 != 0) goto L_0x00a6;
    L_0x009e:
        r0 = r24;
        r0 = r0.mDataRoaming;
        r20 = r0;
        if (r20 == 0) goto L_0x05fe;
    L_0x00a6:
        r15 = 1;
    L_0x00a7:
        r20 = "2GNRP";
        r21 = com.sec.android.app.CscFeature.getInstance();
        r22 = "CscFeature_RIL_FakeRoamingOption4";
        r21 = r21.getString(r22);
        r20 = r20.equals(r21);
        if (r20 == 0) goto L_0x060d;
    L_0x00b9:
        r20 = "gsm.sim.operator.numeric";
        r21 = "";
        r0 = r24;
        r1 = r20;
        r2 = r21;
        r20 = r0.getSystemProperty(r1, r2);
        r20 = android.text.TextUtils.isEmpty(r20);
        if (r20 != 0) goto L_0x0601;
    L_0x00cd:
        r20 = new java.lang.StringBuilder;
        r20.<init>();
        r21 = "Control fake roaming, mRoamingMode = (";
        r20 = r20.append(r21);
        r0 = r24;
        r0 = r0.mRoamingMode;
        r21 = r0;
        r20 = r20.append(r21);
        r21 = ")";
        r20 = r20.append(r21);
        r20 = r20.toString();
        r0 = r24;
        r1 = r20;
        r0.log(r1);
        r0 = r24;
        r0 = r0.mRoamingMode;
        r20 = r0;
        if (r20 == 0) goto L_0x011d;
    L_0x00fb:
        r0 = r24;
        r0 = r0.mNewSS;
        r20 = r0;
        r0 = r24;
        r1 = r20;
        r20 = r0.isFakeHomeOn(r1);
        if (r20 == 0) goto L_0x010c;
    L_0x010b:
        r15 = 0;
    L_0x010c:
        r0 = r24;
        r0 = r0.mNewSS;
        r20 = r0;
        r0 = r24;
        r1 = r20;
        r20 = r0.isFakeRoamingOn(r1);
        if (r20 == 0) goto L_0x011d;
    L_0x011c:
        r15 = 1;
    L_0x011d:
        r0 = r24;
        r0 = r0.mNewSS;
        r20 = r0;
        r0 = r20;
        r0.setRoaming(r15);
        r0 = r24;
        r0 = r0.mNewSS;
        r20 = r0;
        r0 = r24;
        r0 = r0.mEmergencyOnly;
        r21 = r0;
        r20.setEmergencyOnly(r21);
        r24.pollStateDone();
        goto L_0x0012;
    L_0x013c:
        switch(r25) {
            case 4: goto L_0x0141;
            case 5: goto L_0x02d2;
            case 6: goto L_0x046d;
            case 14: goto L_0x05d1;
            default: goto L_0x013f;
        };
    L_0x013f:
        goto L_0x007c;
    L_0x0141:
        r0 = r26;
        r0 = r0.result;	 Catch:{ RuntimeException -> 0x0278 }
        r20 = r0;
        r20 = (java.lang.String[]) r20;	 Catch:{ RuntimeException -> 0x0278 }
        r0 = r20;
        r0 = (java.lang.String[]) r0;	 Catch:{ RuntimeException -> 0x0278 }
        r18 = r0;
        r10 = -1;
        r4 = -1;
        r19 = 0;
        r14 = 4;
        r13 = -1;
        r12 = -1;
        r0 = r18;
        r0 = r0.length;	 Catch:{ RuntimeException -> 0x0278 }
        r20 = r0;
        if (r20 <= 0) goto L_0x01e8;
    L_0x015d:
        r20 = 0;
        r20 = r18[r20];	 Catch:{ NumberFormatException -> 0x0297 }
        r14 = java.lang.Integer.parseInt(r20);	 Catch:{ NumberFormatException -> 0x0297 }
        r0 = r18;
        r0 = r0.length;	 Catch:{ NumberFormatException -> 0x0297 }
        r20 = r0;
        r21 = 3;
        r0 = r20;
        r1 = r21;
        if (r0 < r1) goto L_0x01c1;
    L_0x0172:
        r20 = 1;
        r20 = r18[r20];	 Catch:{ NumberFormatException -> 0x0297 }
        if (r20 == 0) goto L_0x018c;
    L_0x0178:
        r20 = 1;
        r20 = r18[r20];	 Catch:{ NumberFormatException -> 0x0297 }
        r20 = r20.length();	 Catch:{ NumberFormatException -> 0x0297 }
        if (r20 <= 0) goto L_0x018c;
    L_0x0182:
        r20 = 1;
        r20 = r18[r20];	 Catch:{ NumberFormatException -> 0x0297 }
        r21 = 16;
        r10 = java.lang.Integer.parseInt(r20, r21);	 Catch:{ NumberFormatException -> 0x0297 }
    L_0x018c:
        r20 = 2;
        r20 = r18[r20];	 Catch:{ NumberFormatException -> 0x0297 }
        if (r20 == 0) goto L_0x01a6;
    L_0x0192:
        r20 = 2;
        r20 = r18[r20];	 Catch:{ NumberFormatException -> 0x0297 }
        r20 = r20.length();	 Catch:{ NumberFormatException -> 0x0297 }
        if (r20 <= 0) goto L_0x01a6;
    L_0x019c:
        r20 = 2;
        r20 = r18[r20];	 Catch:{ NumberFormatException -> 0x0297 }
        r21 = 16;
        r4 = java.lang.Integer.parseInt(r20, r21);	 Catch:{ NumberFormatException -> 0x0297 }
    L_0x01a6:
        r0 = r18;
        r0 = r0.length;	 Catch:{ NumberFormatException -> 0x0297 }
        r20 = r0;
        r21 = 4;
        r0 = r20;
        r1 = r21;
        if (r0 < r1) goto L_0x01c1;
    L_0x01b3:
        r20 = 3;
        r20 = r18[r20];	 Catch:{ NumberFormatException -> 0x0297 }
        if (r20 == 0) goto L_0x01c1;
    L_0x01b9:
        r20 = 3;
        r20 = r18[r20];	 Catch:{ NumberFormatException -> 0x0297 }
        r19 = java.lang.Integer.parseInt(r20);	 Catch:{ NumberFormatException -> 0x0297 }
    L_0x01c1:
        r0 = r18;
        r0 = r0.length;	 Catch:{ NumberFormatException -> 0x0297 }
        r20 = r0;
        r21 = 14;
        r0 = r20;
        r1 = r21;
        if (r0 <= r1) goto L_0x01e8;
    L_0x01ce:
        r20 = 14;
        r20 = r18[r20];	 Catch:{ NumberFormatException -> 0x0297 }
        if (r20 == 0) goto L_0x01e8;
    L_0x01d4:
        r20 = 14;
        r20 = r18[r20];	 Catch:{ NumberFormatException -> 0x0297 }
        r20 = r20.length();	 Catch:{ NumberFormatException -> 0x0297 }
        if (r20 <= 0) goto L_0x01e8;
    L_0x01de:
        r20 = 14;
        r20 = r18[r20];	 Catch:{ NumberFormatException -> 0x0297 }
        r21 = 16;
        r12 = java.lang.Integer.parseInt(r20, r21);	 Catch:{ NumberFormatException -> 0x0297 }
    L_0x01e8:
        r0 = r24;
        r20 = r0.regCodeIsRoaming(r14);	 Catch:{ RuntimeException -> 0x0278 }
        r0 = r20;
        r1 = r24;
        r1.mGsmRoaming = r0;	 Catch:{ RuntimeException -> 0x0278 }
        r0 = r24;
        r0 = r0.mWfcStatus;	 Catch:{ RuntimeException -> 0x0278 }
        r20 = r0;
        r21 = 2;
        r0 = r20;
        r1 = r21;
        if (r0 != r1) goto L_0x02b6;
    L_0x0202:
        r0 = r24;
        r0 = r0.mDesiredPowerState;	 Catch:{ RuntimeException -> 0x0278 }
        r20 = r0;
        if (r20 != 0) goto L_0x02b6;
    L_0x020a:
        r20 = "Change service state to STATE_POWER_OFF becuase EPDG deregi.";
        r0 = r24;
        r1 = r20;
        r0.log(r1);	 Catch:{ RuntimeException -> 0x0278 }
        r0 = r24;
        r0 = r0.mNewSS;	 Catch:{ RuntimeException -> 0x0278 }
        r20 = r0;
        r21 = 3;
        r20.setState(r21);	 Catch:{ RuntimeException -> 0x0278 }
    L_0x021e:
        r0 = r24;
        r0 = r0.mNewSS;	 Catch:{ RuntimeException -> 0x0278 }
        r20 = r0;
        r0 = r20;
        r1 = r19;
        r0.setRilVoiceRadioTechnology(r1);	 Catch:{ RuntimeException -> 0x0278 }
        r0 = r24;
        r0 = r0.mPhoneBase;	 Catch:{ RuntimeException -> 0x0278 }
        r20 = r0;
        r20 = r20.getContext();	 Catch:{ RuntimeException -> 0x0278 }
        r20 = r20.getResources();	 Catch:{ RuntimeException -> 0x0278 }
        r21 = 17956933; // 0x1120045 float:2.6816158E-38 double:8.8719037E-317;
        r9 = r20.getBoolean(r21);	 Catch:{ RuntimeException -> 0x0278 }
        r20 = 13;
        r0 = r20;
        if (r14 == r0) goto L_0x02c7;
    L_0x0246:
        r20 = 10;
        r0 = r20;
        if (r14 == r0) goto L_0x02c7;
    L_0x024c:
        r20 = 12;
        r0 = r20;
        if (r14 == r0) goto L_0x02c7;
    L_0x0252:
        r20 = 14;
        r0 = r20;
        if (r14 == r0) goto L_0x02c7;
    L_0x0258:
        r20 = 0;
        r0 = r20;
        r1 = r24;
        r1.mEmergencyOnly = r0;	 Catch:{ RuntimeException -> 0x0278 }
    L_0x0260:
        r0 = r24;
        r0 = r0.mNewCellLoc;	 Catch:{ RuntimeException -> 0x0278 }
        r20 = r0;
        r0 = r20;
        r0.setLacAndCid(r10, r4);	 Catch:{ RuntimeException -> 0x0278 }
        r0 = r24;
        r0 = r0.mNewCellLoc;	 Catch:{ RuntimeException -> 0x0278 }
        r20 = r0;
        r0 = r20;
        r0.setPsc(r12);	 Catch:{ RuntimeException -> 0x0278 }
        goto L_0x007c;
    L_0x0278:
        r7 = move-exception;
        r20 = new java.lang.StringBuilder;
        r20.<init>();
        r21 = "Exception while polling service state. Probably malformed RIL response.";
        r20 = r20.append(r21);
        r0 = r20;
        r20 = r0.append(r7);
        r20 = r20.toString();
        r0 = r24;
        r1 = r20;
        r0.loge(r1);
        goto L_0x007c;
    L_0x0297:
        r7 = move-exception;
        r20 = new java.lang.StringBuilder;	 Catch:{ RuntimeException -> 0x0278 }
        r20.<init>();	 Catch:{ RuntimeException -> 0x0278 }
        r21 = "error parsing RegistrationState: ";
        r20 = r20.append(r21);	 Catch:{ RuntimeException -> 0x0278 }
        r0 = r20;
        r20 = r0.append(r7);	 Catch:{ RuntimeException -> 0x0278 }
        r20 = r20.toString();	 Catch:{ RuntimeException -> 0x0278 }
        r0 = r24;
        r1 = r20;
        r0.loge(r1);	 Catch:{ RuntimeException -> 0x0278 }
        goto L_0x01e8;
    L_0x02b6:
        r0 = r24;
        r0 = r0.mNewSS;	 Catch:{ RuntimeException -> 0x0278 }
        r20 = r0;
        r0 = r24;
        r21 = r0.regCodeToServiceState(r14);	 Catch:{ RuntimeException -> 0x0278 }
        r20.setState(r21);	 Catch:{ RuntimeException -> 0x0278 }
        goto L_0x021e;
    L_0x02c7:
        if (r9 == 0) goto L_0x0258;
    L_0x02c9:
        r20 = 1;
        r0 = r20;
        r1 = r24;
        r1.mEmergencyOnly = r0;	 Catch:{ RuntimeException -> 0x0278 }
        goto L_0x0260;
    L_0x02d2:
        r0 = r26;
        r0 = r0.result;	 Catch:{ RuntimeException -> 0x0278 }
        r20 = r0;
        r20 = (java.lang.String[]) r20;	 Catch:{ RuntimeException -> 0x0278 }
        r0 = r20;
        r0 = (java.lang.String[]) r0;	 Catch:{ RuntimeException -> 0x0278 }
        r18 = r0;
        r19 = 0;
        r14 = 4;
        r20 = -1;
        r0 = r20;
        r1 = r24;
        r1.mNewReasonDataDenied = r0;	 Catch:{ RuntimeException -> 0x0278 }
        r20 = 1;
        r0 = r20;
        r1 = r24;
        r1.mNewMaxDataCalls = r0;	 Catch:{ RuntimeException -> 0x0278 }
        r0 = r18;
        r0 = r0.length;	 Catch:{ RuntimeException -> 0x0278 }
        r20 = r0;
        if (r20 <= 0) goto L_0x03c0;
    L_0x02fa:
        r20 = 0;
        r20 = r18[r20];	 Catch:{ NumberFormatException -> 0x0440 }
        r14 = java.lang.Integer.parseInt(r20);	 Catch:{ NumberFormatException -> 0x0440 }
        r0 = r18;
        r0 = r0.length;	 Catch:{ NumberFormatException -> 0x0440 }
        r20 = r0;
        r21 = 4;
        r0 = r20;
        r1 = r21;
        if (r0 < r1) goto L_0x031d;
    L_0x030f:
        r20 = 3;
        r20 = r18[r20];	 Catch:{ NumberFormatException -> 0x0440 }
        if (r20 == 0) goto L_0x031d;
    L_0x0315:
        r20 = 3;
        r20 = r18[r20];	 Catch:{ NumberFormatException -> 0x0440 }
        r19 = java.lang.Integer.parseInt(r20);	 Catch:{ NumberFormatException -> 0x0440 }
    L_0x031d:
        r0 = r18;
        r0 = r0.length;	 Catch:{ NumberFormatException -> 0x0440 }
        r20 = r0;
        r21 = 5;
        r0 = r20;
        r1 = r21;
        if (r0 < r1) goto L_0x033e;
    L_0x032a:
        r20 = 3;
        r0 = r20;
        if (r14 != r0) goto L_0x033e;
    L_0x0330:
        r20 = 4;
        r20 = r18[r20];	 Catch:{ NumberFormatException -> 0x0440 }
        r20 = java.lang.Integer.parseInt(r20);	 Catch:{ NumberFormatException -> 0x0440 }
        r0 = r20;
        r1 = r24;
        r1.mNewReasonDataDenied = r0;	 Catch:{ NumberFormatException -> 0x0440 }
    L_0x033e:
        r0 = r18;
        r0 = r0.length;	 Catch:{ NumberFormatException -> 0x0440 }
        r20 = r0;
        r21 = 6;
        r0 = r20;
        r1 = r21;
        if (r0 < r1) goto L_0x0359;
    L_0x034b:
        r20 = 5;
        r20 = r18[r20];	 Catch:{ NumberFormatException -> 0x0440 }
        r20 = java.lang.Integer.parseInt(r20);	 Catch:{ NumberFormatException -> 0x0440 }
        r0 = r20;
        r1 = r24;
        r1.mNewMaxDataCalls = r0;	 Catch:{ NumberFormatException -> 0x0440 }
    L_0x0359:
        r20 = "TCE";
        r21 = com.sec.android.app.CscFeature.getInstance();	 Catch:{ NumberFormatException -> 0x0440 }
        r22 = "CscFeature_RIL_PDPRetryMechanism4";
        r21 = r21.getString(r22);	 Catch:{ NumberFormatException -> 0x0440 }
        r20 = r20.equals(r21);	 Catch:{ NumberFormatException -> 0x0440 }
        if (r20 == 0) goto L_0x03ae;
    L_0x036b:
        r0 = r18;
        r0 = r0.length;	 Catch:{ NumberFormatException -> 0x0440 }
        r20 = r0;
        r21 = 7;
        r0 = r20;
        r1 = r21;
        if (r0 < r1) goto L_0x03ae;
    L_0x0378:
        r20 = 6;
        r20 = r18[r20];	 Catch:{ NumberFormatException -> 0x0440 }
        if (r20 == 0) goto L_0x03ae;
    L_0x037e:
        r20 = 6;
        r20 = r18[r20];	 Catch:{ NumberFormatException -> 0x0440 }
        r21 = 16;
        r20 = java.lang.Integer.parseInt(r20, r21);	 Catch:{ NumberFormatException -> 0x0440 }
        r0 = r20;
        r1 = r24;
        r1.rac = r0;	 Catch:{ NumberFormatException -> 0x0440 }
        r20 = new java.lang.StringBuilder;	 Catch:{ NumberFormatException -> 0x0440 }
        r20.<init>();	 Catch:{ NumberFormatException -> 0x0440 }
        r21 = "TCE rac ";
        r20 = r20.append(r21);	 Catch:{ NumberFormatException -> 0x0440 }
        r0 = r24;
        r0 = r0.rac;	 Catch:{ NumberFormatException -> 0x0440 }
        r21 = r0;
        r20 = r20.append(r21);	 Catch:{ NumberFormatException -> 0x0440 }
        r20 = r20.toString();	 Catch:{ NumberFormatException -> 0x0440 }
        r0 = r24;
        r1 = r20;
        r0.log(r1);	 Catch:{ NumberFormatException -> 0x0440 }
    L_0x03ae:
        r20 = 100;
        r0 = r19;
        r1 = r20;
        if (r0 <= r1) goto L_0x0437;
    L_0x03b6:
        r19 = r19 + -100;
        r20 = 1;
        r0 = r20;
        r1 = r24;
        r1.mDtmSupport = r0;	 Catch:{ NumberFormatException -> 0x0440 }
    L_0x03c0:
        r0 = r24;
        r5 = r0.regCodeToServiceState(r14);	 Catch:{ RuntimeException -> 0x0278 }
        r0 = r24;
        r0 = r0.mNewSS;	 Catch:{ RuntimeException -> 0x0278 }
        r20 = r0;
        r0 = r20;
        r0.setDataRegState(r5);	 Catch:{ RuntimeException -> 0x0278 }
        r0 = r24;
        r20 = r0.regCodeIsRoaming(r14);	 Catch:{ RuntimeException -> 0x0278 }
        r0 = r20;
        r1 = r24;
        r1.mDataRoaming = r0;	 Catch:{ RuntimeException -> 0x0278 }
        r0 = r24;
        r0 = r0.mDataRoaming;	 Catch:{ RuntimeException -> 0x0278 }
        r20 = r0;
        if (r20 == 0) goto L_0x045f;
    L_0x03e5:
        r0 = r24;
        r0 = r0.mPhone;	 Catch:{ RuntimeException -> 0x0278 }
        r20 = r0;
        r21 = "gsm.operator.ispsroaming";
        r22 = "true";
        r20.setSystemProperty(r21, r22);	 Catch:{ RuntimeException -> 0x0278 }
    L_0x03f2:
        r0 = r24;
        r0 = r0.mNewSS;	 Catch:{ RuntimeException -> 0x0278 }
        r20 = r0;
        r0 = r20;
        r1 = r19;
        r0.setRilDataRadioTechnology(r1);	 Catch:{ RuntimeException -> 0x0278 }
        r20 = new java.lang.StringBuilder;	 Catch:{ RuntimeException -> 0x0278 }
        r20.<init>();	 Catch:{ RuntimeException -> 0x0278 }
        r21 = "handlPollStateResultMessage: GsmSST setDataRegState=";
        r20 = r20.append(r21);	 Catch:{ RuntimeException -> 0x0278 }
        r0 = r20;
        r20 = r0.append(r5);	 Catch:{ RuntimeException -> 0x0278 }
        r21 = " regState=";
        r20 = r20.append(r21);	 Catch:{ RuntimeException -> 0x0278 }
        r0 = r20;
        r20 = r0.append(r14);	 Catch:{ RuntimeException -> 0x0278 }
        r21 = " dataRadioTechnology=";
        r20 = r20.append(r21);	 Catch:{ RuntimeException -> 0x0278 }
        r0 = r20;
        r1 = r19;
        r20 = r0.append(r1);	 Catch:{ RuntimeException -> 0x0278 }
        r20 = r20.toString();	 Catch:{ RuntimeException -> 0x0278 }
        r0 = r24;
        r1 = r20;
        r0.log(r1);	 Catch:{ RuntimeException -> 0x0278 }
        goto L_0x007c;
    L_0x0437:
        r20 = 0;
        r0 = r20;
        r1 = r24;
        r1.mDtmSupport = r0;	 Catch:{ NumberFormatException -> 0x0440 }
        goto L_0x03c0;
    L_0x0440:
        r7 = move-exception;
        r20 = new java.lang.StringBuilder;	 Catch:{ RuntimeException -> 0x0278 }
        r20.<init>();	 Catch:{ RuntimeException -> 0x0278 }
        r21 = "error parsing GprsRegistrationState: ";
        r20 = r20.append(r21);	 Catch:{ RuntimeException -> 0x0278 }
        r0 = r20;
        r20 = r0.append(r7);	 Catch:{ RuntimeException -> 0x0278 }
        r20 = r20.toString();	 Catch:{ RuntimeException -> 0x0278 }
        r0 = r24;
        r1 = r20;
        r0.loge(r1);	 Catch:{ RuntimeException -> 0x0278 }
        goto L_0x03c0;
    L_0x045f:
        r0 = r24;
        r0 = r0.mPhone;	 Catch:{ RuntimeException -> 0x0278 }
        r20 = r0;
        r21 = "gsm.operator.ispsroaming";
        r22 = "false";
        r20.setSystemProperty(r21, r22);	 Catch:{ RuntimeException -> 0x0278 }
        goto L_0x03f2;
    L_0x046d:
        r0 = r26;
        r0 = r0.result;	 Catch:{ RuntimeException -> 0x0278 }
        r20 = r0;
        r20 = (java.lang.String[]) r20;	 Catch:{ RuntimeException -> 0x0278 }
        r0 = r20;
        r0 = (java.lang.String[]) r0;	 Catch:{ RuntimeException -> 0x0278 }
        r11 = r0;
        if (r11 == 0) goto L_0x007c;
    L_0x047c:
        r0 = r11.length;	 Catch:{ RuntimeException -> 0x0278 }
        r20 = r0;
        r21 = 3;
        r0 = r20;
        r1 = r21;
        if (r0 < r1) goto L_0x007c;
    L_0x0487:
        r0 = r24;
        r0 = r0.mUiccController;	 Catch:{ RuntimeException -> 0x0278 }
        r20 = r0;
        r20 = r20.getUiccCard();	 Catch:{ RuntimeException -> 0x0278 }
        if (r20 == 0) goto L_0x04b6;
    L_0x0493:
        r0 = r24;
        r0 = r0.mUiccController;	 Catch:{ RuntimeException -> 0x0278 }
        r20 = r0;
        r20 = r20.getUiccCard();	 Catch:{ RuntimeException -> 0x0278 }
        r3 = r20.getOperatorBrandOverride();	 Catch:{ RuntimeException -> 0x0278 }
    L_0x04a1:
        if (r3 == 0) goto L_0x04b8;
    L_0x04a3:
        r0 = r24;
        r0 = r0.mNewSS;	 Catch:{ RuntimeException -> 0x0278 }
        r20 = r0;
        r21 = 2;
        r21 = r11[r21];	 Catch:{ RuntimeException -> 0x0278 }
        r0 = r20;
        r1 = r21;
        r0.setOperatorName(r3, r3, r1);	 Catch:{ RuntimeException -> 0x0278 }
        goto L_0x007c;
    L_0x04b6:
        r3 = 0;
        goto L_0x04a1;
    L_0x04b8:
        r20 = "gsm.sim.operator.numeric";
        r21 = "";
        r0 = r24;
        r1 = r20;
        r2 = r21;
        r16 = r0.getSystemProperty(r1, r2);	 Catch:{ RuntimeException -> 0x0278 }
        r20 = "gsm.sim.operator.alpha";
        r21 = "";
        r0 = r24;
        r1 = r20;
        r2 = r21;
        r17 = r0.getSystemProperty(r1, r2);	 Catch:{ RuntimeException -> 0x0278 }
        r20 = 2;
        r20 = r11[r20];	 Catch:{ RuntimeException -> 0x0278 }
        if (r20 == 0) goto L_0x0583;
    L_0x04da:
        r20 = 2;
        r20 = r11[r20];	 Catch:{ RuntimeException -> 0x0278 }
        r21 = "45400";
        r20 = r20.equals(r21);	 Catch:{ RuntimeException -> 0x0278 }
        if (r20 != 0) goto L_0x0522;
    L_0x04e6:
        r20 = 2;
        r20 = r11[r20];	 Catch:{ RuntimeException -> 0x0278 }
        r21 = "45402";
        r20 = r20.equals(r21);	 Catch:{ RuntimeException -> 0x0278 }
        if (r20 != 0) goto L_0x0522;
    L_0x04f2:
        r20 = 2;
        r20 = r11[r20];	 Catch:{ RuntimeException -> 0x0278 }
        r21 = "45410";
        r20 = r20.equals(r21);	 Catch:{ RuntimeException -> 0x0278 }
        if (r20 != 0) goto L_0x0522;
    L_0x04fe:
        r20 = 2;
        r20 = r11[r20];	 Catch:{ RuntimeException -> 0x0278 }
        r21 = "45418";
        r20 = r20.equals(r21);	 Catch:{ RuntimeException -> 0x0278 }
        if (r20 != 0) goto L_0x0522;
    L_0x050a:
        r20 = 2;
        r20 = r11[r20];	 Catch:{ RuntimeException -> 0x0278 }
        r21 = "45416";
        r20 = r20.equals(r21);	 Catch:{ RuntimeException -> 0x0278 }
        if (r20 != 0) goto L_0x0522;
    L_0x0516:
        r20 = 2;
        r20 = r11[r20];	 Catch:{ RuntimeException -> 0x0278 }
        r21 = "45419";
        r20 = r20.equals(r21);	 Catch:{ RuntimeException -> 0x0278 }
        if (r20 == 0) goto L_0x0583;
    L_0x0522:
        if (r16 == 0) goto L_0x0583;
    L_0x0524:
        r20 = "45400";
        r0 = r16;
        r1 = r20;
        r20 = r0.equals(r1);	 Catch:{ RuntimeException -> 0x0278 }
        if (r20 != 0) goto L_0x056c;
    L_0x0530:
        r20 = "45402";
        r0 = r16;
        r1 = r20;
        r20 = r0.equals(r1);	 Catch:{ RuntimeException -> 0x0278 }
        if (r20 != 0) goto L_0x056c;
    L_0x053c:
        r20 = "45410";
        r0 = r16;
        r1 = r20;
        r20 = r0.equals(r1);	 Catch:{ RuntimeException -> 0x0278 }
        if (r20 != 0) goto L_0x056c;
    L_0x0548:
        r20 = "45418";
        r0 = r16;
        r1 = r20;
        r20 = r0.equals(r1);	 Catch:{ RuntimeException -> 0x0278 }
        if (r20 != 0) goto L_0x056c;
    L_0x0554:
        r20 = "45416";
        r0 = r16;
        r1 = r20;
        r20 = r0.equals(r1);	 Catch:{ RuntimeException -> 0x0278 }
        if (r20 != 0) goto L_0x056c;
    L_0x0560:
        r20 = "45419";
        r0 = r16;
        r1 = r20;
        r20 = r0.equals(r1);	 Catch:{ RuntimeException -> 0x0278 }
        if (r20 == 0) goto L_0x0583;
    L_0x056c:
        r20 = "[CSL PCCW-HKT] CSL PCCW-HKT Network, SPN should be displayed instead of PLMN";
        r0 = r24;
        r1 = r20;
        r0.log(r1);	 Catch:{ RuntimeException -> 0x0278 }
        if (r17 == 0) goto L_0x0583;
    L_0x0577:
        r20 = 0;
        r11[r20] = r17;	 Catch:{ RuntimeException -> 0x0278 }
        r20 = 1;
        r21 = 0;
        r21 = r11[r21];	 Catch:{ RuntimeException -> 0x0278 }
        r11[r20] = r21;	 Catch:{ RuntimeException -> 0x0278 }
    L_0x0583:
        r20 = 2;
        r20 = r11[r20];	 Catch:{ RuntimeException -> 0x0278 }
        if (r20 == 0) goto L_0x05ba;
    L_0x0589:
        r20 = 2;
        r20 = r11[r20];	 Catch:{ RuntimeException -> 0x0278 }
        r21 = "46697";
        r20 = r20.equals(r21);	 Catch:{ RuntimeException -> 0x0278 }
        if (r20 == 0) goto L_0x05ba;
    L_0x0595:
        if (r16 == 0) goto L_0x05ba;
    L_0x0597:
        r20 = "46605";
        r0 = r16;
        r1 = r20;
        r20 = r0.equals(r1);	 Catch:{ RuntimeException -> 0x0278 }
        if (r20 == 0) goto L_0x05ba;
    L_0x05a3:
        r20 = "[APT TWM] APT USE TWM Network, SPN should be displayed instead of PLMN";
        r0 = r24;
        r1 = r20;
        r0.log(r1);	 Catch:{ RuntimeException -> 0x0278 }
        if (r17 == 0) goto L_0x05ba;
    L_0x05ae:
        r20 = 0;
        r11[r20] = r17;	 Catch:{ RuntimeException -> 0x0278 }
        r20 = 1;
        r21 = 0;
        r21 = r11[r21];	 Catch:{ RuntimeException -> 0x0278 }
        r11[r20] = r21;	 Catch:{ RuntimeException -> 0x0278 }
    L_0x05ba:
        r0 = r24;
        r0 = r0.mNewSS;	 Catch:{ RuntimeException -> 0x0278 }
        r20 = r0;
        r21 = 0;
        r21 = r11[r21];	 Catch:{ RuntimeException -> 0x0278 }
        r22 = 1;
        r22 = r11[r22];	 Catch:{ RuntimeException -> 0x0278 }
        r23 = 2;
        r23 = r11[r23];	 Catch:{ RuntimeException -> 0x0278 }
        r20.setOperatorName(r21, r22, r23);	 Catch:{ RuntimeException -> 0x0278 }
        goto L_0x007c;
    L_0x05d1:
        r0 = r26;
        r0 = r0.result;	 Catch:{ RuntimeException -> 0x0278 }
        r20 = r0;
        r20 = (int[]) r20;	 Catch:{ RuntimeException -> 0x0278 }
        r0 = r20;
        r0 = (int[]) r0;	 Catch:{ RuntimeException -> 0x0278 }
        r8 = r0;
        r0 = r24;
        r0 = r0.mNewSS;	 Catch:{ RuntimeException -> 0x0278 }
        r21 = r0;
        r20 = 0;
        r20 = r8[r20];	 Catch:{ RuntimeException -> 0x0278 }
        r22 = 1;
        r0 = r20;
        r1 = r22;
        if (r0 != r1) goto L_0x05fb;
    L_0x05f0:
        r20 = 1;
    L_0x05f2:
        r0 = r21;
        r1 = r20;
        r0.setIsManualSelection(r1);	 Catch:{ RuntimeException -> 0x0278 }
        goto L_0x007c;
    L_0x05fb:
        r20 = 0;
        goto L_0x05f2;
    L_0x05fe:
        r15 = 0;
        goto L_0x00a7;
    L_0x0601:
        r20 = "Control fake roaming, SIM is not ready. roaming set as false";
        r0 = r24;
        r1 = r20;
        r0.log(r1);
        r15 = 0;
        goto L_0x011d;
    L_0x060d:
        r0 = r24;
        r0 = r0.mNewSS;
        r20 = r0;
        r0 = r24;
        r1 = r20;
        r20 = r0.isFakeHomeOn(r1);
        if (r20 == 0) goto L_0x061e;
    L_0x061d:
        r15 = 0;
    L_0x061e:
        r0 = r24;
        r0 = r0.mNewSS;
        r20 = r0;
        r0 = r24;
        r1 = r20;
        r20 = r0.isFakeRoamingOn(r1);
        if (r20 == 0) goto L_0x011d;
    L_0x062e:
        r15 = 1;
        goto L_0x011d;
        */
        throw new UnsupportedOperationException("Method not decompiled: com.android.internal.telephony.gsm.GsmServiceStateTracker.handlePollStateResult(int, android.os.AsyncResult):void");
    }

    protected void setSignalStrengthDefaultValues() {
        this.mSignalStrength = new SignalStrength(true);
    }

    public void pollState() {
        this.mPollingContext = new int[1];
        this.mPollingContext[0] = 0;
        switch (C01149.f14x46dd5024[this.mCi.getRadioState().ordinal()]) {
            case 1:
                this.mNewSS.setStateOutOfService();
                this.mNewCellLoc.setStateInvalid();
                setSignalStrengthDefaultValues();
                this.mGotCountryCode = false;
                this.mNitzUpdatedTime = false;
                pollStateDone();
                return;
            case 2:
                this.mNewSS.setStateOff();
                this.mNewCellLoc.setStateInvalid();
                setSignalStrengthDefaultValues();
                this.mGotCountryCode = false;
                this.mNitzUpdatedTime = false;
                pollStateDone();
                return;
            default:
                int[] iArr = this.mPollingContext;
                iArr[0] = iArr[0] + 1;
                this.mCi.getOperator(obtainMessage(6, this.mPollingContext));
                iArr = this.mPollingContext;
                iArr[0] = iArr[0] + 1;
                this.mCi.getDataRegistrationState(obtainMessage(5, this.mPollingContext));
                iArr = this.mPollingContext;
                iArr[0] = iArr[0] + 1;
                this.mCi.getVoiceRegistrationState(obtainMessage(4, this.mPollingContext));
                iArr = this.mPollingContext;
                iArr[0] = iArr[0] + 1;
                this.mCi.getNetworkSelectionMode(obtainMessage(14, this.mPollingContext));
                return;
        }
    }

    protected void pollStateDone() {
        String salesCode = SystemProperties.get("ro.csc.sales_code");
        log("Poll ServiceState done:  oldSS=[" + this.mSS + "] newSS=[" + this.mNewSS + "]" + " oldMaxDataCalls=" + this.mMaxDataCalls + " mNewMaxDataCalls=" + this.mNewMaxDataCalls + " oldReasonDataDenied=" + this.mReasonDataDenied + " mNewReasonDataDenied=" + this.mNewReasonDataDenied);
        if (Build.IS_DEBUGGABLE && SystemProperties.getBoolean("telephony.test.forceRoaming", false)) {
            this.mNewSS.setRoaming(true);
        }
        useDataRegStateForDataOnlyDevices();
        updateSpnWithEons(this.mNewSS, this.mNewCellLoc);
        boolean hasRegistered = this.mSS.getVoiceRegState() != 0 && this.mNewSS.getVoiceRegState() == 0;
        if (this.mSS.getVoiceRegState() != 0 || this.mNewSS.getVoiceRegState() == 0) {
        }
        boolean hasGprsAttached = this.mSS.getDataRegState() != 0 && this.mNewSS.getDataRegState() == 0;
        boolean hasGprsDetached = this.mSS.getDataRegState() == 0 && this.mNewSS.getDataRegState() != 0;
        boolean hasDataRegStateChanged = this.mSS.getDataRegState() != this.mNewSS.getDataRegState();
        boolean hasVoiceRegStateChanged = this.mSS.getVoiceRegState() != this.mNewSS.getVoiceRegState();
        boolean hasRilVoiceRadioTechnologyChanged = this.mSS.getRilVoiceRadioTechnology() != this.mNewSS.getRilVoiceRadioTechnology();
        boolean hasRilDataRadioTechnologyChanged = this.mSS.getRilDataRadioTechnology() != this.mNewSS.getRilDataRadioTechnology();
        this.oldDataRadioTech = this.mSS.getRilDataRadioTechnology();
        this.newDataRadioTech = this.mNewSS.getRilDataRadioTechnology();
        boolean hasChanged = !this.mNewSS.equals(this.mSS);
        boolean hasRoamingOn = !this.mSS.getRoaming() && this.mNewSS.getRoaming();
        boolean hasRoamingOff = this.mSS.getRoaming() && !this.mNewSS.getRoaming();
        boolean hasLocationChanged = !this.mNewCellLoc.equals(this.mCellLoc);
        if (this.mSS.getVoiceRegState() == 2 || this.mNewSS.getVoiceRegState() != 2) {
        }
        String dualmodeTest = "";
        if (hasVoiceRegStateChanged || hasDataRegStateChanged) {
            EventLog.writeEvent(EventLogTags.GSM_SERVICE_STATE_CHANGE, new Object[]{Integer.valueOf(this.mSS.getVoiceRegState()), Integer.valueOf(this.mSS.getDataRegState()), Integer.valueOf(this.mNewSS.getVoiceRegState()), Integer.valueOf(this.mNewSS.getDataRegState())});
        }
        if (this.mSS.getVoiceRegState() == 3 && this.mNewSS.getVoiceRegState() != 3) {
            this.IsFlightMode = true;
            sendMessageDelayed(obtainMessage(EVENT_NETWORK_STATE_CHANGED_BY_RESCAN, null), 30000);
        }
        if (this.mNewSS.getVoiceRegState() == 0) {
            this.IsFlightMode = false;
        }
        if (hasRilVoiceRadioTechnologyChanged) {
            int cid = -1;
            GsmCellLocation loc = this.mNewCellLoc;
            if (loc != null) {
                cid = loc.getCid();
            }
            EventLog.writeEvent(EventLogTags.GSM_RAT_SWITCHED_NEW, new Object[]{Integer.valueOf(cid), Integer.valueOf(this.mSS.getRilVoiceRadioTechnology()), Integer.valueOf(this.mNewSS.getRilVoiceRadioTechnology())});
            log("RAT switched " + ServiceState.rilRadioTechnologyToString(this.mSS.getRilVoiceRadioTechnology()) + " -> " + ServiceState.rilRadioTechnologyToString(this.mNewSS.getRilVoiceRadioTechnology()) + " at cell " + cid);
        }
        ServiceState tss = this.mSS;
        this.mSS = this.mNewSS;
        this.mNewSS = tss;
        this.mNewSS.setStateOutOfService();
        GsmCellLocation tcl = this.mCellLoc;
        this.mCellLoc = this.mNewCellLoc;
        this.mNewCellLoc = tcl;
        this.mReasonDataDenied = this.mNewReasonDataDenied;
        this.mMaxDataCalls = this.mNewMaxDataCalls;
        isWFCReigstered = this.mPhone.isWfcRegistered();
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
            log("pollStateDone: registering current mNitzUpdatedTime=" + this.mNitzUpdatedTime + " changing to false");
            this.mNitzUpdatedTime = false;
        }
        if ("DGG".equals("DGG") && !"dsds".equals(SystemProperties.get("persist.radio.multisim.config"))) {
            String mSlot1SIM = TelephonyManager.getTelephonyProperty("gsm.sim.operator.numeric", 0, "");
            String mSlot2SIM = TelephonyManager.getTelephonyProperty("gsm.sim.operator.numeric", 1, "");
            String sim1StateCheck = TelephonyManager.getTelephonyProperty("gsm.sim.state", 0, "ABSENT");
            String sim2StateCheck = TelephonyManager.getTelephonyProperty("gsm.sim.state", 1, "ABSENT");
            if (this.mPhone.getPhoneId() == 0) {
                mSlot1Type = this.mSS.getRilVoiceRadioTechnology();
            } else if (this.mPhone.getPhoneId() == 1) {
                mSlot2Type = this.mSS.getRilVoiceRadioTechnology();
            }
            ByteArrayOutputStream bos;
            DataOutputStream dos;
            if ("ABSENT".equals(sim1StateCheck) || "ABSENT".equals(sim2StateCheck) || !((mSlot1Type == 1 || mSlot1Type == 2 || mSlot1Type == 16) && (mSlot2Type == 1 || mSlot2Type == 2 || mSlot2Type == 16))) {
                if (oldDsCallStatus != 4) {
                    try {
                        bos = new ByteArrayOutputStream();
                        dos = new DataOutputStream(bos);
                        dos.writeByte(17);
                        dos.writeByte(3);
                        dos.writeShort(5);
                        dos.writeByte(4);
                        this.mPhone.invokeOemRilRequestRaw(bos.toByteArray(), null);
                        log("[DUOS] Send IPC for CP2 ON - mode 4");
                        mDsCallCnt++;
                        if (mDsCallCnt > 3) {
                            oldDsCallStatus = 4;
                            mDsCallCnt = 0;
                        }
                    } catch (IOException e) {
                        Rlog.d(LOG_TAG, "Error in setting call status to 4, exception is :" + e);
                    }
                }
            } else if (oldDsCallStatus != 3) {
                try {
                    bos = new ByteArrayOutputStream();
                    dos = new DataOutputStream(bos);
                    dos.writeByte(17);
                    dos.writeByte(3);
                    dos.writeShort(5);
                    dos.writeByte(3);
                    this.mPhone.invokeOemRilRequestRaw(bos.toByteArray(), null);
                    log("[DUOS] Send IPC for CP2 OFF - mode 3");
                    mDsCallCnt++;
                    if (mDsCallCnt > 3) {
                        oldDsCallStatus = 3;
                        mDsCallCnt = 0;
                    }
                } catch (IOException e2) {
                    Rlog.d(LOG_TAG, "Error in setting call status to 3, exception is :" + e2);
                }
            }
        }
        if (hasChanged) {
            if (this.mIccRecords == null || !this.IsSIMLoadDone) {
                log("Network State Changed, NO SIM or EONS not loaded: So update Service state display");
                updateSpnDisplay();
            } else {
                updateSpnDisplay();
            }
            this.mPhone.setSystemProperty("gsm.operator.alpha", this.mSS.getOperatorAlphaLong());
            String prevOperatorNumeric = getSystemProperty("gsm.sim.operator.numeric", "");
            String operatorNumeric = this.mSS.getOperatorNumeric();
            this.mPhone.setSystemProperty("gsm.operator.numeric", operatorNumeric);
            updateCarrierMccMncConfiguration(operatorNumeric, prevOperatorNumeric, this.mPhone.getContext());
            if (this.mSS.getVoiceRegState() == 0 && getAutoTimeZone()) {
                GetTimezoneInfoUsingMcc(operatorNumeric, true, false, null);
            }
            if (operatorNumeric == null) {
                log("operatorNumeric is null");
                this.mPhone.setSystemProperty("gsm.operator.iso-country", "");
                this.mGotCountryCode = false;
                this.mNitzUpdatedTime = false;
            } else {
                TimeZone zone;
                String iso = "";
                String mcc = "";
                try {
                    mcc = operatorNumeric.substring(0, 3);
                    iso = MccTable.countryCodeForMcc(Integer.parseInt(mcc));
                } catch (NumberFormatException ex) {
                    loge("pollStateDone: countryCodeForMcc error" + ex);
                } catch (StringIndexOutOfBoundsException ex2) {
                    loge("pollStateDone: countryCodeForMcc error" + ex2);
                }
                this.mPhone.setSystemProperty("gsm.operator.iso-country", iso);
                this.mGotCountryCode = true;
                if (!(this.mNitzUpdatedTime || mcc.equals("000") || TextUtils.isEmpty(iso) || !getAutoTimeZone())) {
                    boolean testOneUniqueOffsetPath = SystemProperties.getBoolean("telephony.test.ignore.nitz", false) && (SystemClock.uptimeMillis() & 1) == 0;
                    ArrayList<TimeZone> uniqueZones = TimeUtils.getTimeZonesWithUniqueOffsets(iso);
                    if (uniqueZones.size() == 1 || testOneUniqueOffsetPath) {
                        zone = (TimeZone) uniqueZones.get(0);
                        log("pollStateDone: no nitz but one TZ for iso-cc=" + iso + " with zone.getID=" + zone.getID() + " testOneUniqueOffsetPath=" + testOneUniqueOffsetPath);
                        setAndBroadcastNetworkSetTimeZone(zone.getID());
                    } else {
                        log("pollStateDone: there are " + uniqueZones.size() + " unique offsets for iso-cc='" + iso + " testOneUniqueOffsetPath=" + testOneUniqueOffsetPath + "', do nothing");
                    }
                }
                if (shouldFixTimeZoneNow(this.mPhone, operatorNumeric, prevOperatorNumeric, this.mNeedFixZoneAfterNitz)) {
                    String zoneName = SystemProperties.get("persist.sys.timezone");
                    log("pollStateDone: fix time zone zoneName='" + zoneName + "' mZoneOffset=" + this.mZoneOffset + " mZoneDst=" + this.mZoneDst + " iso-cc='" + iso + "' iso-cc-idx=" + Arrays.binarySearch(GMT_COUNTRY_CODES, iso));
                    if ("".equals(iso) && this.mNeedFixZoneAfterNitz) {
                        zone = getNitzTimeZone(this.mZoneOffset, this.mZoneDst, this.mZoneTime);
                        log("pollStateDone: using NITZ TimeZone");
                    } else if (this.mZoneOffset != 0 || this.mZoneDst || zoneName == null || zoneName.length() <= 0 || Arrays.binarySearch(GMT_COUNTRY_CODES, iso) >= 0) {
                        zone = TimeUtils.getTimeZone(this.mZoneOffset, this.mZoneDst, this.mZoneTime, iso);
                        log("pollStateDone: using getTimeZone(off, dst, time, iso)");
                    } else {
                        zone = TimeZone.getDefault();
                        if (this.mNeedFixZoneAfterNitz) {
                            long ctm = System.currentTimeMillis();
                            long tzOffset = (long) zone.getOffset(ctm);
                            log("pollStateDone: tzOffset=" + tzOffset + " ltod=" + TimeUtils.logTimeOfDay(ctm));
                            if (getAutoTime()) {
                                long adj = ctm - tzOffset;
                                log("pollStateDone: adj ltod=" + TimeUtils.logTimeOfDay(adj));
                                setAndBroadcastNetworkSetTime(adj);
                            } else {
                                this.mSavedTime -= tzOffset;
                            }
                        }
                        log("pollStateDone: using default TimeZone");
                    }
                    this.mNeedFixZoneAfterNitz = false;
                    if (zone != null) {
                        log("pollStateDone: zone != null zone.getID=" + zone.getID());
                        if (getAutoTimeZone()) {
                            setAndBroadcastNetworkSetTimeZone(zone.getID());
                        }
                        saveNitzTimeZone(zone.getID());
                    } else {
                        log("pollStateDone: zone == null");
                    }
                }
            }
            this.mPhone.setSystemProperty("gsm.operator.isroaming", this.mSS.getRoaming() ? "true" : "false");
            this.mPhone.setSystemProperty("ril.servicestate", Integer.toString(this.mSS.getVoiceRegState()));
            this.mPhone.notifyServiceStateChanged(this.mSS);
            if ("CTC".equals(salesCode)) {
                setChinaMainlandProperty();
                displayTimeZoneRecommend(this.mSS.getOperatorNumeric(), this.mSS.getState());
            }
            if ((CscFeature.getInstance().getEnableStatus("CscFeature_RIL_UseRatInfoDuringPlmnSelection") || "BRI".equals(salesCode) || "TGY".equals(salesCode) || "CWT".equals(salesCode) || "FET".equals(salesCode) || "TWM".equals(salesCode) || "CHZ".equals(salesCode)) && this.mSS.getVoiceRegState() == 0 && this.mSS.getDataRegState() != 0 && this.mSS.getVoiceNetworkType() != 0) {
                log("Need to pollState to update Voice Network Type");
                pollState();
            }
        }
        if (hasGprsAttached) {
            this.mAttachedRegistrants.notifyRegistrants();
        }
        if (hasGprsDetached) {
            this.mDetachedRegistrants.notifyRegistrants();
        }
        if (hasDataRegStateChanged || hasRilDataRadioTechnologyChanged) {
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
        if ("TCE".equals(CscFeature.getInstance().getString("CscFeature_RIL_PDPRetryMechanism4"))) {
            Rlog.v(LOG_TAG, "TCE [" + this.mSS.getDataRegState() + "," + this.mCellLoc.getLac() + "," + this.mNewCellLoc.getLac() + "," + this.oldRac + "," + this.rac + "]");
            if (this.mSS.getDataRegState() == 0 && !((this.mCellLoc.getLac() == 0 || this.mCellLoc.getLac() == this.mNewCellLoc.getLac()) && (this.rac == 0 || this.oldRac == this.rac))) {
                this.oldRac = this.rac;
                this.mRoutingAreaChangedRegistrants.notifyRegistrants();
            }
        }
        if (isGprsConsistent(this.mSS.getDataRegState(), this.mSS.getVoiceRegState())) {
            this.mReportedGprsNoReg = false;
        } else if (!this.mStartedGprsRegCheck && !this.mReportedGprsNoReg) {
            this.mStartedGprsRegCheck = true;
            sendMessageDelayed(obtainMessage(22), (long) Global.getInt(this.mPhone.getContext().getContentResolver(), "gprs_register_check_period_ms", 60000));
        }
    }

    protected boolean isGprsConsistent(int dataRegState, int voiceRegState) {
        return voiceRegState != 0 || dataRegState == 0;
    }

    protected TimeZone getNitzTimeZone(int offset, boolean dst, long when) {
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
            rawOffset -= 3600000;
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

    private void onRestrictedStateChanged(AsyncResult ar) {
        boolean z = true;
        RestrictedState newRs = new RestrictedState();
        log("onRestrictedStateChanged: E rs " + this.mRestrictedState);
        if (ar.exception == null) {
            boolean z2;
            int state = ((int[]) ar.result)[0];
            if ((state & 1) == 0 && (state & 4) == 0) {
                z2 = false;
            } else {
                z2 = true;
            }
            newRs.setCsEmergencyRestricted(z2);
            if (this.mUiccApplcation != null && this.mUiccApplcation.getState() == AppState.APPSTATE_READY) {
                if ((state & 2) == 0 && (state & 4) == 0) {
                    z2 = false;
                } else {
                    z2 = true;
                }
                newRs.setCsNormalRestricted(z2);
                if ((state & 16) == 0) {
                    z = false;
                }
                newRs.setPsRestricted(z);
            }
            log("onRestrictedStateChanged: new rs " + newRs);
            if (!this.mRestrictedState.isPsRestricted() && newRs.isPsRestricted()) {
                this.mPsRestrictEnabledRegistrants.notifyRegistrants();
                setNotification(CallFailCause.CDMA_DROP);
            } else if (this.mRestrictedState.isPsRestricted() && !newRs.isPsRestricted()) {
                this.mPsRestrictDisabledRegistrants.notifyRegistrants();
                setNotification(1002);
            }
            if (this.mRestrictedState.isCsRestricted()) {
                if (!newRs.isCsRestricted()) {
                    setNotification(1004);
                } else if (!newRs.isCsNormalRestricted()) {
                    setNotification(1006);
                } else if (!newRs.isCsEmergencyRestricted()) {
                    setNotification(1005);
                }
            } else if (!this.mRestrictedState.isCsEmergencyRestricted() || this.mRestrictedState.isCsNormalRestricted()) {
                if (this.mRestrictedState.isCsEmergencyRestricted() || !this.mRestrictedState.isCsNormalRestricted()) {
                    if (newRs.isCsRestricted()) {
                        setNotification(1003);
                    } else if (newRs.isCsEmergencyRestricted()) {
                        setNotification(1006);
                    } else if (newRs.isCsNormalRestricted()) {
                        setNotification(1005);
                    }
                } else if (!newRs.isCsRestricted()) {
                    setNotification(1004);
                } else if (newRs.isCsRestricted()) {
                    setNotification(1003);
                } else if (newRs.isCsEmergencyRestricted()) {
                    setNotification(1006);
                }
            } else if (!newRs.isCsRestricted()) {
                setNotification(1004);
            } else if (newRs.isCsRestricted()) {
                setNotification(1003);
            } else if (newRs.isCsNormalRestricted()) {
                setNotification(1005);
            }
            this.mRestrictedState = newRs;
        }
        log("onRestrictedStateChanged: X rs " + this.mRestrictedState);
    }

    private int regCodeToServiceState(int code) {
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
            case 10:
            case 12:
            case 13:
            case 14:
                return 2;
            default:
                loge("regCodeToServiceState: unexpected service state " + code);
                return 1;
        }
    }

    private boolean regCodeIsRoaming(int code) {
        return 5 == code;
    }

    private boolean isSameNamedOperators(ServiceState s) {
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
        if (currentMccEqualsSimMcc(s) && (equalsOnsl || equalsOnss)) {
            return true;
        }
        return false;
    }

    private boolean currentMccEqualsSimMcc(ServiceState s) {
        boolean equalsMcc = true;
        try {
            equalsMcc = getSystemProperty("gsm.sim.operator.numeric", "").substring(0, 3).equals(s.getOperatorNumeric().substring(0, 3));
        } catch (Exception e) {
        }
        return equalsMcc;
    }

    private boolean isOperatorConsideredNonRoaming(ServiceState s) {
        String operatorNumeric = s.getOperatorNumeric();
        String[] numericArray = this.mPhone.getContext().getResources().getStringArray(17236018);
        if (numericArray.length == 0 || operatorNumeric == null) {
            return false;
        }
        for (String numeric : numericArray) {
            if (operatorNumeric.startsWith(numeric)) {
                return true;
            }
        }
        return false;
    }

    private boolean isOperatorConsideredRoaming(ServiceState s) {
        String operatorNumeric = s.getOperatorNumeric();
        String[] numericArray = this.mPhone.getContext().getResources().getStringArray(17236019);
        if (numericArray.length == 0 || operatorNumeric == null) {
            return false;
        }
        for (String numeric : numericArray) {
            if (operatorNumeric.startsWith(numeric)) {
                return true;
            }
        }
        return false;
    }

    public int getCurrentDataConnectionState() {
        return this.mSS.getDataRegState();
    }

    public boolean needBlockData() {
        boolean result = false;
        if (this.mEmergencyMgr != null && this.mEmergencyMgr.isEmergencyMode()) {
            log("needBlockData(): mScreenState = " + mScreenState + ", needMobileDataBlock = " + this.mEmergencyMgr.needMobileDataBlock());
            if (!(mScreenState || !this.mEmergencyMgr.needMobileDataBlock() || emergencyDataOpened)) {
                result = true;
            }
        }
        log("needBlockData(): result = " + result);
        return result;
    }

    public boolean isConcurrentVoiceAndDataAllowed() {
        if (this.mDtmSupport == 1) {
            log("isConcurrentVoiceAndDataAllowed : network support DTM, return true");
            return true;
        } else if (this.mSS.getRilDataRadioTechnology() < 3) {
            return false;
        } else {
            return true;
        }
    }

    public boolean isConcurrentVoiceAndDataAllowed(String apnType) {
        if (CscFeature.getInstance().getEnableStatus("CscFeature_RIL_EnableVoicePriority")) {
            if ("ims".equals(apnType) || "bip".equals(apnType)) {
                return isConcurrentVoiceAndDataAllowed();
            }
            if (!(mScreenState || mTetherState || this.mSS.getRilDataRadioTechnology() == 14)) {
                int antennabar = this.mSignalStrength.getLevel();
                if ((!mDataSuspended && antennabar <= 1) || (mDataSuspended && antennabar < 3)) {
                    return false;
                }
            }
        }
        return isConcurrentVoiceAndDataAllowed();
    }

    public CellLocation getCellLocation() {
        if (this.mCellLoc.getLac() < 0 || this.mCellLoc.getCid() < 0) {
            List<CellInfo> result = getAllCellInfo();
            if (result != null) {
                CellLocation cellLocOther = new GsmCellLocation();
                for (CellInfo ci : result) {
                    if (ci instanceof CellInfoGsm) {
                        CellIdentityGsm cellIdentityGsm = ((CellInfoGsm) ci).getCellIdentity();
                        cellLocOther.setLacAndCid(cellIdentityGsm.getLac(), cellIdentityGsm.getCid());
                        cellLocOther.setPsc(cellIdentityGsm.getPsc());
                        return cellLocOther;
                    } else if (ci instanceof CellInfoWcdma) {
                        CellIdentityWcdma cellIdentityWcdma = ((CellInfoWcdma) ci).getCellIdentity();
                        cellLocOther.setLacAndCid(cellIdentityWcdma.getLac(), cellIdentityWcdma.getCid());
                        cellLocOther.setPsc(cellIdentityWcdma.getPsc());
                        return cellLocOther;
                    } else if ((ci instanceof CellInfoLte) && (cellLocOther.getLac() < 0 || cellLocOther.getCid() < 0)) {
                        CellIdentityLte cellIdentityLte = ((CellInfoLte) ci).getCellIdentity();
                        if (!(cellIdentityLte.getTac() == Integer.MAX_VALUE || cellIdentityLte.getCi() == Integer.MAX_VALUE)) {
                            cellLocOther.setLacAndCid(cellIdentityLte.getTac(), cellIdentityLte.getCi());
                            cellLocOther.setPsc(0);
                        }
                    }
                }
                return cellLocOther;
            }
            if (SHIP_BUILD) {
                log("getCellLocation(): X empty mCellLoc and CellInfo mCellLoc= xxx");
            } else {
                log("getCellLocation(): X empty mCellLoc and CellInfo mCellLoc=" + this.mCellLoc);
            }
            return this.mCellLoc;
        }
        if (SHIP_BUILD) {
            log("getCellLocation(): X good mCellLoc= xxx");
        } else {
            log("getCellLocation(): X good mCellLoc=" + this.mCellLoc);
        }
        return this.mCellLoc;
    }

    /* JADX WARNING: inconsistent code. */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    private void setTimeFromNITZString(java.lang.String r35, long r36) {
        /*
        r34 = this;
        r22 = android.os.SystemClock.elapsedRealtime();
        r29 = new java.lang.StringBuilder;
        r29.<init>();
        r30 = "NITZ: ";
        r29 = r29.append(r30);
        r0 = r29;
        r1 = r35;
        r29 = r0.append(r1);
        r30 = ",";
        r29 = r29.append(r30);
        r0 = r29;
        r1 = r36;
        r29 = r0.append(r1);
        r30 = " start=";
        r29 = r29.append(r30);
        r0 = r29;
        r1 = r22;
        r29 = r0.append(r1);
        r30 = " delay=";
        r29 = r29.append(r30);
        r30 = r22 - r36;
        r29 = r29.append(r30);
        r29 = r29.toString();
        r0 = r34;
        r1 = r29;
        r0.log(r1);
        r29 = 1050101; // 0x1005f5 float:1.471505E-39 double:5.18819E-318;
        r0 = r29;
        r1 = r35;
        android.util.EventLog.writeEvent(r0, r1);
        r6 = 0;
        r29 = "[NAM] Close Manual Selection Popup. Send Intent ACTION_NITZ_SET_TIME.";
        r0 = r34;
        r1 = r29;
        r0.log(r1);
        r13 = new android.content.Intent;
        r29 = "android.intent.action.NITZ_SET_TIME";
        r0 = r29;
        r13.<init>(r0);
        r29 = 536870912; // 0x20000000 float:1.0842022E-19 double:2.652494739E-315;
        r0 = r29;
        r13.addFlags(r0);
        r0 = r34;
        r0 = r0.mPhone;
        r29 = r0;
        r29 = r29.getContext();
        r0 = r29;
        r0.sendBroadcast(r13);
        r29 = "GMT";
        r29 = java.util.TimeZone.getTimeZone(r29);	 Catch:{ RuntimeException -> 0x02c8 }
        r7 = java.util.Calendar.getInstance(r29);	 Catch:{ RuntimeException -> 0x02c8 }
        r7.clear();	 Catch:{ RuntimeException -> 0x02c8 }
        r29 = 16;
        r30 = 0;
        r0 = r29;
        r1 = r30;
        r7.set(r0, r1);	 Catch:{ RuntimeException -> 0x02c8 }
        r29 = "[/:,+-]";
        r0 = r35;
        r1 = r29;
        r19 = r0.split(r1);	 Catch:{ RuntimeException -> 0x02c8 }
        r29 = 0;
        r29 = r19[r29];	 Catch:{ RuntimeException -> 0x02c8 }
        r29 = java.lang.Integer.parseInt(r29);	 Catch:{ RuntimeException -> 0x02c8 }
        r0 = r29;
        r0 = r0 + 2000;
        r27 = r0;
        r29 = 1;
        r0 = r29;
        r1 = r27;
        r7.set(r0, r1);	 Catch:{ RuntimeException -> 0x02c8 }
        r29 = 1;
        r29 = r19[r29];	 Catch:{ RuntimeException -> 0x02c8 }
        r29 = java.lang.Integer.parseInt(r29);	 Catch:{ RuntimeException -> 0x02c8 }
        r18 = r29 + -1;
        r29 = 2;
        r0 = r29;
        r1 = r18;
        r7.set(r0, r1);	 Catch:{ RuntimeException -> 0x02c8 }
        r29 = 2;
        r29 = r19[r29];	 Catch:{ RuntimeException -> 0x02c8 }
        r8 = java.lang.Integer.parseInt(r29);	 Catch:{ RuntimeException -> 0x02c8 }
        r29 = 5;
        r0 = r29;
        r7.set(r0, r8);	 Catch:{ RuntimeException -> 0x02c8 }
        r29 = 3;
        r29 = r19[r29];	 Catch:{ RuntimeException -> 0x02c8 }
        r11 = java.lang.Integer.parseInt(r29);	 Catch:{ RuntimeException -> 0x02c8 }
        r29 = 10;
        r0 = r29;
        r7.set(r0, r11);	 Catch:{ RuntimeException -> 0x02c8 }
        r29 = 4;
        r29 = r19[r29];	 Catch:{ RuntimeException -> 0x02c8 }
        r15 = java.lang.Integer.parseInt(r29);	 Catch:{ RuntimeException -> 0x02c8 }
        r29 = 12;
        r0 = r29;
        r7.set(r0, r15);	 Catch:{ RuntimeException -> 0x02c8 }
        r29 = 5;
        r29 = r19[r29];	 Catch:{ RuntimeException -> 0x02c8 }
        r20 = java.lang.Integer.parseInt(r29);	 Catch:{ RuntimeException -> 0x02c8 }
        r29 = 13;
        r0 = r29;
        r1 = r20;
        r7.set(r0, r1);	 Catch:{ RuntimeException -> 0x02c8 }
        r29 = 45;
        r0 = r35;
        r1 = r29;
        r29 = r0.indexOf(r1);	 Catch:{ RuntimeException -> 0x02c8 }
        r30 = -1;
        r0 = r29;
        r1 = r30;
        if (r0 != r1) goto L_0x02b2;
    L_0x0119:
        r21 = 1;
    L_0x011b:
        r29 = 6;
        r29 = r19[r29];	 Catch:{ RuntimeException -> 0x02c8 }
        r25 = java.lang.Integer.parseInt(r29);	 Catch:{ RuntimeException -> 0x02c8 }
        r0 = r19;
        r0 = r0.length;	 Catch:{ RuntimeException -> 0x02c8 }
        r29 = r0;
        r30 = 8;
        r0 = r29;
        r1 = r30;
        if (r0 < r1) goto L_0x02b6;
    L_0x0130:
        r29 = 7;
        r29 = r19[r29];	 Catch:{ RuntimeException -> 0x02c8 }
        r9 = java.lang.Integer.parseInt(r29);	 Catch:{ RuntimeException -> 0x02c8 }
    L_0x0138:
        if (r21 == 0) goto L_0x02b9;
    L_0x013a:
        r29 = 1;
    L_0x013c:
        r29 = r29 * r25;
        r29 = r29 * 15;
        r29 = r29 * 60;
        r0 = r29;
        r0 = r0 * 1000;
        r25 = r0;
        r28 = 0;
        r0 = r19;
        r0 = r0.length;	 Catch:{ RuntimeException -> 0x02c8 }
        r29 = r0;
        r30 = 9;
        r0 = r29;
        r1 = r30;
        if (r0 < r1) goto L_0x01ba;
    L_0x0157:
        r29 = 8;
        r29 = r19[r29];	 Catch:{ RuntimeException -> 0x02c8 }
        r30 = 33;
        r31 = 47;
        r26 = r29.replace(r30, r31);	 Catch:{ RuntimeException -> 0x02c8 }
        r28 = java.util.TimeZone.getTimeZone(r26);	 Catch:{ RuntimeException -> 0x02c8 }
        r29 = r26.length();	 Catch:{ RuntimeException -> 0x02c8 }
        r30 = 3;
        r0 = r29;
        r1 = r30;
        if (r0 != r1) goto L_0x02bd;
    L_0x0173:
        r29 = new java.lang.StringBuilder;	 Catch:{ RuntimeException -> 0x02c8 }
        r29.<init>();	 Catch:{ RuntimeException -> 0x02c8 }
        r30 = "[NITZ] get the MCC. ";
        r29 = r29.append(r30);	 Catch:{ RuntimeException -> 0x02c8 }
        r0 = r29;
        r1 = r26;
        r29 = r0.append(r1);	 Catch:{ RuntimeException -> 0x02c8 }
        r29 = r29.toString();	 Catch:{ RuntimeException -> 0x02c8 }
        r0 = r34;
        r1 = r29;
        r0.loge(r1);	 Catch:{ RuntimeException -> 0x02c8 }
        r29 = java.lang.Integer.parseInt(r26);	 Catch:{ RuntimeException -> 0x02c8 }
        r24 = com.android.internal.telephony.MccTable.countryCodeForMcc(r29);	 Catch:{ RuntimeException -> 0x02c8 }
        r0 = r34;
        r0 = r0.mPhone;	 Catch:{ RuntimeException -> 0x02c8 }
        r29 = r0;
        r30 = "gsm.operator.iso-country";
        r0 = r29;
        r1 = r30;
        r2 = r24;
        r0.setSystemProperty(r1, r2);	 Catch:{ RuntimeException -> 0x02c8 }
        r0 = r26;
        r1 = r34;
        r1.PrevMcc = r0;	 Catch:{ RuntimeException -> 0x02c8 }
        r28 = 0;
        r29 = 1;
        r0 = r29;
        r1 = r34;
        r1.mGotCountryCode = r0;	 Catch:{ RuntimeException -> 0x02c8 }
    L_0x01ba:
        r29 = "gsm.operator.iso-country";
        r30 = "";
        r0 = r34;
        r1 = r29;
        r2 = r30;
        r14 = r0.getSystemProperty(r1, r2);	 Catch:{ RuntimeException -> 0x02c8 }
        r29 = new java.lang.StringBuilder;	 Catch:{ RuntimeException -> 0x02c8 }
        r29.<init>();	 Catch:{ RuntimeException -> 0x02c8 }
        r30 = "[NITZ] setTimeFromNITZString: iso = ";
        r29 = r29.append(r30);	 Catch:{ RuntimeException -> 0x02c8 }
        r0 = r29;
        r29 = r0.append(r14);	 Catch:{ RuntimeException -> 0x02c8 }
        r30 = ", dst = ";
        r29 = r29.append(r30);	 Catch:{ RuntimeException -> 0x02c8 }
        r0 = r29;
        r29 = r0.append(r9);	 Catch:{ RuntimeException -> 0x02c8 }
        r30 = ", ZoneOffset = ";
        r29 = r29.append(r30);	 Catch:{ RuntimeException -> 0x02c8 }
        r0 = r29;
        r1 = r25;
        r29 = r0.append(r1);	 Catch:{ RuntimeException -> 0x02c8 }
        r30 = ", Year =";
        r29 = r29.append(r30);	 Catch:{ RuntimeException -> 0x02c8 }
        r0 = r29;
        r1 = r27;
        r29 = r0.append(r1);	 Catch:{ RuntimeException -> 0x02c8 }
        r29 = r29.toString();	 Catch:{ RuntimeException -> 0x02c8 }
        r0 = r34;
        r1 = r29;
        r0.log(r1);	 Catch:{ RuntimeException -> 0x02c8 }
        if (r28 != 0) goto L_0x0230;
    L_0x020e:
        r0 = r34;
        r0 = r0.mGotCountryCode;	 Catch:{ RuntimeException -> 0x02c8 }
        r29 = r0;
        if (r29 == 0) goto L_0x0230;
    L_0x0216:
        if (r14 == 0) goto L_0x02f8;
    L_0x0218:
        r29 = r14.length();	 Catch:{ RuntimeException -> 0x02c8 }
        if (r29 <= 0) goto L_0x02f8;
    L_0x021e:
        if (r9 == 0) goto L_0x02f4;
    L_0x0220:
        r29 = 1;
    L_0x0222:
        r30 = r7.getTimeInMillis();	 Catch:{ RuntimeException -> 0x02c8 }
        r0 = r25;
        r1 = r29;
        r2 = r30;
        r28 = android.util.TimeUtils.getTimeZone(r0, r1, r2, r14);	 Catch:{ RuntimeException -> 0x02c8 }
    L_0x0230:
        if (r28 == 0) goto L_0x024e;
    L_0x0232:
        r0 = r34;
        r0 = r0.mZoneOffset;	 Catch:{ RuntimeException -> 0x02c8 }
        r29 = r0;
        r0 = r29;
        r1 = r25;
        if (r0 != r1) goto L_0x024e;
    L_0x023e:
        r0 = r34;
        r0 = r0.mZoneDst;	 Catch:{ RuntimeException -> 0x02c8 }
        r30 = r0;
        if (r9 == 0) goto L_0x0311;
    L_0x0246:
        r29 = 1;
    L_0x0248:
        r0 = r30;
        r1 = r29;
        if (r0 == r1) goto L_0x0270;
    L_0x024e:
        r29 = 1;
        r0 = r29;
        r1 = r34;
        r1.mNeedFixZoneAfterNitz = r0;	 Catch:{ RuntimeException -> 0x02c8 }
        r0 = r25;
        r1 = r34;
        r1.mZoneOffset = r0;	 Catch:{ RuntimeException -> 0x02c8 }
        if (r9 == 0) goto L_0x0315;
    L_0x025e:
        r29 = 1;
    L_0x0260:
        r0 = r29;
        r1 = r34;
        r1.mZoneDst = r0;	 Catch:{ RuntimeException -> 0x02c8 }
        r30 = r7.getTimeInMillis();	 Catch:{ RuntimeException -> 0x02c8 }
        r0 = r30;
        r2 = r34;
        r2.mZoneTime = r0;	 Catch:{ RuntimeException -> 0x02c8 }
    L_0x0270:
        if (r28 == 0) goto L_0x0296;
    L_0x0272:
        r29 = 0;
        r0 = r29;
        r1 = r34;
        r1.mNeedFixZoneAfterNitz = r0;	 Catch:{ RuntimeException -> 0x02c8 }
        r29 = r34.getAutoTimeZone();	 Catch:{ RuntimeException -> 0x02c8 }
        if (r29 == 0) goto L_0x028b;
    L_0x0280:
        r29 = r28.getID();	 Catch:{ RuntimeException -> 0x02c8 }
        r0 = r34;
        r1 = r29;
        r0.setAndBroadcastNetworkSetTimeZone(r1);	 Catch:{ RuntimeException -> 0x02c8 }
    L_0x028b:
        r29 = r28.getID();	 Catch:{ RuntimeException -> 0x02c8 }
        r0 = r34;
        r1 = r29;
        r0.saveNitzTimeZone(r1);	 Catch:{ RuntimeException -> 0x02c8 }
    L_0x0296:
        r29 = "gsm.ignore-nitz";
        r12 = android.os.SystemProperties.get(r29);	 Catch:{ RuntimeException -> 0x02c8 }
        if (r12 == 0) goto L_0x0319;
    L_0x029e:
        r29 = "yes";
        r0 = r29;
        r29 = r12.equals(r0);	 Catch:{ RuntimeException -> 0x02c8 }
        if (r29 == 0) goto L_0x0319;
    L_0x02a8:
        r29 = "NITZ: Not setting clock because gsm.ignore-nitz is set";
        r0 = r34;
        r1 = r29;
        r0.log(r1);	 Catch:{ RuntimeException -> 0x02c8 }
    L_0x02b1:
        return;
    L_0x02b2:
        r21 = 0;
        goto L_0x011b;
    L_0x02b6:
        r9 = 0;
        goto L_0x0138;
    L_0x02b9:
        r29 = -1;
        goto L_0x013c;
    L_0x02bd:
        r29 = "[NITZ] get the TimeZone.";
        r0 = r34;
        r1 = r29;
        r0.loge(r1);	 Catch:{ RuntimeException -> 0x02c8 }
        goto L_0x01ba;
    L_0x02c8:
        r10 = move-exception;
        r29 = new java.lang.StringBuilder;
        r29.<init>();
        r30 = "NITZ: Parsing NITZ time ";
        r29 = r29.append(r30);
        r0 = r29;
        r1 = r35;
        r29 = r0.append(r1);
        r30 = " ex=";
        r29 = r29.append(r30);
        r0 = r29;
        r29 = r0.append(r10);
        r29 = r29.toString();
        r0 = r34;
        r1 = r29;
        r0.loge(r1);
        goto L_0x02b1;
    L_0x02f4:
        r29 = 0;
        goto L_0x0222;
    L_0x02f8:
        if (r9 == 0) goto L_0x030e;
    L_0x02fa:
        r29 = 1;
    L_0x02fc:
        r30 = r7.getTimeInMillis();	 Catch:{ RuntimeException -> 0x02c8 }
        r0 = r34;
        r1 = r25;
        r2 = r29;
        r3 = r30;
        r28 = r0.getNitzTimeZone(r1, r2, r3);	 Catch:{ RuntimeException -> 0x02c8 }
        goto L_0x0230;
    L_0x030e:
        r29 = 0;
        goto L_0x02fc;
    L_0x0311:
        r29 = 0;
        goto L_0x0248;
    L_0x0315:
        r29 = 0;
        goto L_0x0260;
    L_0x0319:
        r0 = r34;
        r0 = r0.mWakeLock;	 Catch:{ all -> 0x042d }
        r29 = r0;
        r29.acquire();	 Catch:{ all -> 0x042d }
        r29 = r34.getAutoTime();	 Catch:{ all -> 0x042d }
        if (r29 == 0) goto L_0x0402;
    L_0x0328:
        r30 = android.os.SystemClock.elapsedRealtime();	 Catch:{ all -> 0x042d }
        r16 = r30 - r36;
        r30 = 0;
        r29 = (r16 > r30 ? 1 : (r16 == r30 ? 0 : -1));
        if (r29 >= 0) goto L_0x035d;
    L_0x0334:
        r29 = new java.lang.StringBuilder;	 Catch:{ all -> 0x042d }
        r29.<init>();	 Catch:{ all -> 0x042d }
        r30 = "NITZ: not setting time, clock has rolled backwards since NITZ time was received, ";
        r29 = r29.append(r30);	 Catch:{ all -> 0x042d }
        r0 = r29;
        r1 = r35;
        r29 = r0.append(r1);	 Catch:{ all -> 0x042d }
        r29 = r29.toString();	 Catch:{ all -> 0x042d }
        r0 = r34;
        r1 = r29;
        r0.log(r1);	 Catch:{ all -> 0x042d }
        r0 = r34;
        r0 = r0.mWakeLock;	 Catch:{ RuntimeException -> 0x02c8 }
        r29 = r0;
        r29.release();	 Catch:{ RuntimeException -> 0x02c8 }
        goto L_0x02b1;
    L_0x035d:
        r30 = 2147483647; // 0x7fffffff float:NaN double:1.060997895E-314;
        r29 = (r16 > r30 ? 1 : (r16 == r30 ? 0 : -1));
        if (r29 <= 0) goto L_0x0394;
    L_0x0364:
        r29 = new java.lang.StringBuilder;	 Catch:{ all -> 0x042d }
        r29.<init>();	 Catch:{ all -> 0x042d }
        r30 = "NITZ: not setting time, processing has taken ";
        r29 = r29.append(r30);	 Catch:{ all -> 0x042d }
        r30 = 86400000; // 0x5265c00 float:7.82218E-36 double:4.2687272E-316;
        r30 = r16 / r30;
        r29 = r29.append(r30);	 Catch:{ all -> 0x042d }
        r30 = " days";
        r29 = r29.append(r30);	 Catch:{ all -> 0x042d }
        r29 = r29.toString();	 Catch:{ all -> 0x042d }
        r0 = r34;
        r1 = r29;
        r0.log(r1);	 Catch:{ all -> 0x042d }
        r0 = r34;
        r0 = r0.mWakeLock;	 Catch:{ RuntimeException -> 0x02c8 }
        r29 = r0;
        r29.release();	 Catch:{ RuntimeException -> 0x02c8 }
        goto L_0x02b1;
    L_0x0394:
        r29 = 14;
        r0 = r16;
        r0 = (int) r0;
        r30 = r0;
        r0 = r29;
        r1 = r30;
        r7.add(r0, r1);	 Catch:{ all -> 0x042d }
        r29 = new java.lang.StringBuilder;	 Catch:{ all -> 0x042d }
        r29.<init>();	 Catch:{ all -> 0x042d }
        r30 = "NITZ: Setting time of day to ";
        r29 = r29.append(r30);	 Catch:{ all -> 0x042d }
        r30 = r7.getTime();	 Catch:{ all -> 0x042d }
        r29 = r29.append(r30);	 Catch:{ all -> 0x042d }
        r30 = " NITZ receive delay(ms): ";
        r29 = r29.append(r30);	 Catch:{ all -> 0x042d }
        r0 = r29;
        r1 = r16;
        r29 = r0.append(r1);	 Catch:{ all -> 0x042d }
        r30 = " gained(ms): ";
        r29 = r29.append(r30);	 Catch:{ all -> 0x042d }
        r30 = r7.getTimeInMillis();	 Catch:{ all -> 0x042d }
        r32 = java.lang.System.currentTimeMillis();	 Catch:{ all -> 0x042d }
        r30 = r30 - r32;
        r29 = r29.append(r30);	 Catch:{ all -> 0x042d }
        r30 = " from ";
        r29 = r29.append(r30);	 Catch:{ all -> 0x042d }
        r0 = r29;
        r1 = r35;
        r29 = r0.append(r1);	 Catch:{ all -> 0x042d }
        r29 = r29.toString();	 Catch:{ all -> 0x042d }
        r0 = r34;
        r1 = r29;
        r0.log(r1);	 Catch:{ all -> 0x042d }
        r30 = r7.getTimeInMillis();	 Catch:{ all -> 0x042d }
        r0 = r34;
        r1 = r30;
        r0.setAndBroadcastNetworkSetTime(r1);	 Catch:{ all -> 0x042d }
        r29 = "GsmSST";
        r30 = "NITZ: after Setting time of day";
        android.telephony.Rlog.i(r29, r30);	 Catch:{ all -> 0x042d }
    L_0x0402:
        r29 = "gsm.nitz.time";
        r30 = r7.getTimeInMillis();	 Catch:{ all -> 0x042d }
        r30 = java.lang.String.valueOf(r30);	 Catch:{ all -> 0x042d }
        android.os.SystemProperties.set(r29, r30);	 Catch:{ all -> 0x042d }
        r30 = r7.getTimeInMillis();	 Catch:{ all -> 0x042d }
        r0 = r34;
        r1 = r30;
        r0.saveNitzTime(r1);	 Catch:{ all -> 0x042d }
        r29 = 1;
        r0 = r29;
        r1 = r34;
        r1.mNitzUpdatedTime = r0;	 Catch:{ all -> 0x042d }
        r0 = r34;
        r0 = r0.mWakeLock;	 Catch:{ RuntimeException -> 0x02c8 }
        r29 = r0;
        r29.release();	 Catch:{ RuntimeException -> 0x02c8 }
        goto L_0x02b1;
    L_0x042d:
        r29 = move-exception;
        r0 = r34;
        r0 = r0.mWakeLock;	 Catch:{ RuntimeException -> 0x02c8 }
        r30 = r0;
        r30.release();	 Catch:{ RuntimeException -> 0x02c8 }
        throw r29;	 Catch:{ RuntimeException -> 0x02c8 }
        */
        throw new UnsupportedOperationException("Method not decompiled: com.android.internal.telephony.gsm.GsmServiceStateTracker.setTimeFromNITZString(java.lang.String, long):void");
    }

    protected boolean getAutoTime() {
        try {
            return Global.getInt(this.mPhone.getContext().getContentResolver(), "auto_time") > 0;
        } catch (SettingNotFoundException e) {
            return true;
        }
    }

    protected boolean getAutoTimeZone() {
        try {
            return Global.getInt(this.mPhone.getContext().getContentResolver(), "auto_time_zone") > 0;
        } catch (SettingNotFoundException e) {
            return true;
        }
    }

    protected void saveNitzTimeZone(String zoneId) {
        this.mSavedTimeZone = zoneId;
    }

    private void saveNitzTime(long time) {
        this.mSavedTime = time;
        this.mSavedAtTime = SystemClock.elapsedRealtime();
    }

    protected void setAndBroadcastNetworkSetTimeZone(String zoneId) {
        log("setAndBroadcastNetworkSetTimeZone: setTimeZone=" + zoneId);
        ((AlarmManager) this.mPhone.getContext().getSystemService("alarm")).setTimeZone(zoneId);
        Intent intent = new Intent("android.intent.action.NETWORK_SET_TIMEZONE");
        intent.addFlags(536870912);
        intent.putExtra("time-zone", zoneId);
        this.mPhone.getContext().sendStickyBroadcastAsUser(intent, UserHandle.ALL);
        log("setAndBroadcastNetworkSetTimeZone: call alarm.setTimeZone and broadcast zoneId=" + zoneId);
    }

    protected void setAndBroadcastNetworkSetTime(long time) {
        log("setAndBroadcastNetworkSetTime: time=" + time + "ms");
        SystemClock.setCurrentTimeMillis(time);
        Intent intent = new Intent("android.intent.action.NETWORK_SET_TIME");
        intent.addFlags(536870912);
        intent.putExtra("time", time);
        this.mPhone.getContext().sendStickyBroadcastAsUser(intent, UserHandle.ALL);
    }

    private void revertToNitzTime() {
        if (Global.getInt(this.mPhone.getContext().getContentResolver(), "auto_time", 0) != 0) {
            log("Reverting to NITZ Time: mSavedTime=" + this.mSavedTime + " mSavedAtTime=" + this.mSavedAtTime);
            if (this.mSavedTime != 0 && this.mSavedAtTime != 0) {
                setAndBroadcastNetworkSetTime(this.mSavedTime + (SystemClock.elapsedRealtime() - this.mSavedAtTime));
            }
        }
    }

    private void revertToNitzTimeZone() {
        if (Global.getInt(this.mPhone.getContext().getContentResolver(), "auto_time_zone", 0) != 0) {
            log("Reverting to NITZ TimeZone: tz='" + this.mSavedTimeZone);
            if (this.mSavedTimeZone != null) {
                setAndBroadcastNetworkSetTimeZone(this.mSavedTimeZone);
            }
        }
    }

    private void updateNationalRoamingMode() {
        this.mRoamingMode = Secure.getInt(this.mPhone.getContext().getContentResolver(), "data_national_roaming_mode", -1);
        log("updateNationalRoamingMode, roamingMode =" + this.mRoamingMode);
        pollState();
    }

    private void setNotification(int notifyType) {
        log("setNotification: create notification " + notifyType);
        Context context = this.mPhone.getContext();
        this.mNotification = new Notification();
        this.mNotification.when = System.currentTimeMillis();
        this.mNotification.flags = 16;
        this.mNotification.icon = 17301642;
        Intent intent = new Intent();
        this.mNotification.contentIntent = PendingIntent.getActivity(context, 0, intent, 268435456);
        CharSequence details = "";
        CharSequence title = context.getText(17039540);
        int notificationId = CS_NOTIFICATION;
        switch (notifyType) {
            case CallFailCause.CDMA_DROP /*1001*/:
                notificationId = PS_NOTIFICATION;
                details = context.getText(17039541);
                break;
            case 1002:
                notificationId = PS_NOTIFICATION;
                break;
            case 1003:
                details = context.getText(17039544);
                break;
            case 1005:
                details = context.getText(17039543);
                break;
            case 1006:
                details = context.getText(17039542);
                break;
        }
        log("setNotification: put notification " + title + " / " + details);
        this.mNotification.tickerText = title;
        this.mNotification.color = context.getResources().getColor(17170520);
        this.mNotification.setLatestEventInfo(context, title, details, this.mNotification.contentIntent);
        NotificationManager notificationManager = (NotificationManager) context.getSystemService("notification");
        if (notifyType == 1002 || notifyType == 1004) {
            notificationManager.cancel(notificationId);
        } else {
            notificationManager.notify(notificationId, this.mNotification);
        }
    }

    private UiccCardApplication getUiccCardApplication() {
        return this.mUiccController.getUiccCardApplication(this.mPhone.getPhoneId(), 1);
    }

    protected void onUpdateIccAvailability() {
        updateRuimLoadedEvent();
        if (this.mUiccController != null) {
            UiccCardApplication newUiccApplication = getUiccCardApplication();
            if (this.mUiccApplcation != newUiccApplication) {
                if (this.mUiccApplcation != null) {
                    log("Removing stale icc objects.");
                    this.mUiccApplcation.unregisterForReady(this);
                    if (this.mIccRecords != null) {
                        this.mIccRecords.unregisterForRecordsLoaded(this);
                        this.IsSIMLoadDone = false;
                        log("IsSIMLoadDone set to false");
                    }
                    this.mIccRecords = null;
                    this.mUiccApplcation = null;
                }
                if (newUiccApplication != null) {
                    log("New card found");
                    this.mUiccApplcation = newUiccApplication;
                    this.mIccRecords = this.mUiccApplcation.getIccRecords();
                    this.mUiccApplcation.registerForReady(this, 17, null);
                    if (this.mIccRecords != null) {
                        this.mIccRecords.registerForRecordsLoaded(this, 16, null);
                    }
                }
            }
        }
    }

    private void updateRuimLoadedEvent() {
        if (this.mUiccController == null) {
            loge("mUiccController is null");
        } else if (this.RuimLoadedEvent) {
            loge("RuimLoadedEvent is already set");
        } else {
            UiccCardApplication ruimUiccApplication = this.mUiccController.getUiccCardApplication(2);
            if (ruimUiccApplication == null) {
                loge("ruimUiccApplication is null");
            } else if (ruimUiccApplication != null) {
                log("[Global mode] Ruim card found");
                IccRecords mRuimRecords = ruimUiccApplication.getIccRecords();
                if (mRuimRecords != null) {
                    loge("register EVENT_RUIM_RECORDS_LOADED");
                    mRuimRecords.registerForRecordsLoaded(this, 27, null);
                    this.RuimLoadedEvent = true;
                }
            }
        }
    }

    private void updateOtaspState() {
        int otaspMode = getOtasp();
        int oldOtaspMode = this.mCurrentOtaspMode;
        this.mCurrentOtaspMode = otaspMode;
        if (oldOtaspMode != this.mCurrentOtaspMode) {
            log(" call notifyOtaspChanged old otaspMode=" + oldOtaspMode + " new otaspMode=" + this.mCurrentOtaspMode);
            if (this.mCurrentOtaspMode == 3) {
                SystemProperties.set("ril.otasp_state", "3");
            }
            if (!"VZW-CDMA".equals("")) {
                this.mPhone.notifyOtaspChanged(this.mCurrentOtaspMode);
            } else if (PreferenceManager.getDefaultSharedPreferences(this.mPhone.getContext()).getBoolean("setup_wizard_skip", false)) {
                this.mPhone.notifyOtaspChanged(3);
                log("SetupWizardSkip : true , send OTASP_NOT_NEEDED to setup Wizard for skipping.");
            } else {
                log("SetupWizardSkip : false , send Current OtaspMode to setup Wizard");
                this.mPhone.notifyOtaspChanged(this.mCurrentOtaspMode);
            }
        }
    }

    private int getOtasp() {
        log("getOtasp: state=" + 3);
        return 3;
    }

    protected void log(String s) {
        Rlog.d(MultiSimManager.appendSimSlot(LOG_TAG, this.mPhone.mPhoneId), s);
    }

    protected void loge(String s) {
        Rlog.e(MultiSimManager.appendSimSlot(LOG_TAG, this.mPhone.mPhoneId), s);
    }

    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        pw.println("GsmServiceStateTracker extends:");
        super.dump(fd, pw, args);
        pw.println(" mPhone=" + this.mPhone);
        pw.println(" mSS=" + this.mSS);
        pw.println(" mNewSS=" + this.mNewSS);
        pw.println(" mCellLoc=" + this.mCellLoc);
        pw.println(" mNewCellLoc=" + this.mNewCellLoc);
        pw.println(" mPreferredNetworkType=" + this.mPreferredNetworkType);
        pw.println(" mMaxDataCalls=" + this.mMaxDataCalls);
        pw.println(" mNewMaxDataCalls=" + this.mNewMaxDataCalls);
        pw.println(" mReasonDataDenied=" + this.mReasonDataDenied);
        pw.println(" mNewReasonDataDenied=" + this.mNewReasonDataDenied);
        pw.println(" mGsmRoaming=" + this.mGsmRoaming);
        pw.println(" mDataRoaming=" + this.mDataRoaming);
        pw.println(" mEmergencyOnly=" + this.mEmergencyOnly);
        pw.println(" mNeedFixZoneAfterNitz=" + this.mNeedFixZoneAfterNitz);
        pw.println(" mZoneOffset=" + this.mZoneOffset);
        pw.println(" mZoneDst=" + this.mZoneDst);
        pw.println(" mZoneTime=" + this.mZoneTime);
        pw.println(" mGotCountryCode=" + this.mGotCountryCode);
        pw.println(" mNitzUpdatedTime=" + this.mNitzUpdatedTime);
        pw.println(" mSavedTimeZone=" + this.mSavedTimeZone);
        pw.println(" mSavedTime=" + this.mSavedTime);
        pw.println(" mSavedAtTime=" + this.mSavedAtTime);
        pw.println(" mStartedGprsRegCheck=" + this.mStartedGprsRegCheck);
        pw.println(" mReportedGprsNoReg=" + this.mReportedGprsNoReg);
        pw.println(" mNotification=" + this.mNotification);
        pw.println(" mWakeLock=" + this.mWakeLock);
        pw.println(" mCurSpn=" + this.mCurSpn);
        pw.println(" mCurShowSpn=" + this.mCurShowSpn);
        pw.println(" mCurPlmn=" + this.mCurPlmn);
        pw.println(" mCurShowPlmn=" + this.mCurShowPlmn);
    }

    public void setImsRegistrationState(boolean registered) {
        if (this.mImsRegistrationOnOff && !registered && this.mAlarmSwitch) {
            this.mImsRegistrationOnOff = registered;
            ((AlarmManager) this.mPhone.getContext().getSystemService("alarm")).cancel(this.mRadioOffIntent);
            this.mAlarmSwitch = false;
            sendMessage(obtainMessage(45));
            return;
        }
        this.mImsRegistrationOnOff = registered;
    }

    private void onSprintRoamingIndicator(boolean roaming) {
        boolean gsmDataGuardEnabled;
        boolean gsmSmsGuardEnabled;
        if (Secure.getInt(this.mCr, "sprint_gsm_data_guard", 1) == 1) {
            gsmDataGuardEnabled = true;
        } else {
            gsmDataGuardEnabled = false;
        }
        if (Secure.getInt(this.mCr, "sprint_gsm_sms_guard", 1) == 1) {
            gsmSmsGuardEnabled = true;
        } else {
            gsmSmsGuardEnabled = false;
        }
        if (gsmDataGuardEnabled && roaming && !this.bshowDataGuard && this.mPhone.getState() == State.IDLE) {
            this.bshowDataGuard = true;
            Secure.putInt(this.mCr, "data_roaming", 0);
            this.mPhone.getContext().sendStickyBroadcastAsUser(new Intent("android.intent.action.ACTION_SHOW_DIALOG_DATA_ROAMING_GUARD"), UserHandle.ALL);
            this.mRoamingOnRegistrants.notifyRegistrants();
        }
        if (gsmSmsGuardEnabled && roaming && !this.bshowSmsGuard && this.mPhone.getState() == State.IDLE) {
            Intent intent = new Intent("android.intent.action.ACTION_ROAMING_STATUS_CHANGED");
            String currentRoam = "roaming";
            intent.putExtra(TextBasedSmsColumns.STATUS, currentRoam);
            log("ACTION_ROAMING_STATUS_CHANGED: " + currentRoam);
            this.mPhone.getContext().sendBroadcast(intent);
            this.mRoamingOnRegistrants.notifyRegistrants();
            this.bshowSmsGuard = true;
        }
    }

    protected void GetTimezoneInfoUsingMcc(String operatorNumeric, boolean checkMccChange, boolean isDelayedTZUpdate, String savedZoneID) {
        if (operatorNumeric == null) {
            log("operatorNumeric is null");
        } else if (operatorNumeric.length() < 3) {
            log("operatorNumeric is Invalid");
            this.MccNumChanged = false;
        } else {
            String zoneId = SystemProperties.get("ril.timezoneID");
            String NewMcc = operatorNumeric.substring(0, 3);
            if ((checkMccChange && NewMcc.equals(this.PrevMcc)) || NewMcc.equals("000") || NewMcc.equals("111") || NewMcc.equals("001") || NewMcc.equals("999")) {
                this.MccNumChanged = false;
                return;
            }
            if (checkMccChange) {
                log("Mcc is changed : " + this.PrevMcc + " --> " + NewMcc);
                this.MccNumChanged = true;
                this.PrevMcc = NewMcc;
            } else {
                Rlog.w(LOG_TAG, "Don't check Mcc");
            }
            log("ZONE ID : " + zoneId);
            Intent intent;
            if (zoneId != null && zoneId.length() > 3) {
                TimeZone manualTimeZone = TimeZone.getTimeZone(zoneId);
                if (manualTimeZone != null) {
                    log("TIMEZONE Update");
                    setAndBroadcastNetworkSetTimeZone(manualTimeZone.getID());
                } else if (getAutoTime() || getAutoTimeZone()) {
                    log("manualTimeZone is NULL. Manual Update Send Intent Action_MCC_SET_TIME.");
                    intent = new Intent("android.intent.action.MCC_SET_TIME");
                    intent.addFlags(536870912);
                    this.mPhone.getContext().sendStickyBroadcast(intent);
                }
            } else if (getAutoTime() || getAutoTimeZone()) {
                log("Multi Time Zone. Manual Update Send Intent Action_MCC_SET_TIME.");
                intent = new Intent("android.intent.action.MCC_SET_TIME");
                intent.addFlags(536870912);
                intent.putExtra("MCC", NewMcc);
                log("putExtra[mcc] : " + NewMcc);
                this.mPhone.getContext().sendStickyBroadcast(intent);
            }
        }
    }

    protected void updateApnPreferredMode() {
        String checkRplmn = SystemProperties.get("persist.radio.rplmn", "");
        DcTrackerBase dcTracker = this.mPhone.mDcTracker;
        boolean isLteRoamingOn = dcTracker.getLteDataOnRoamingEnabled();
        boolean hasRegiedMccInKor = false;
        if (!TextUtils.isEmpty(this.mNewSS.getOperatorNumeric())) {
            hasRegiedMccInKor = this.mNewSS.getOperatorNumeric().startsWith("450");
        }
        if (this.mNewSS.getRoaming()) {
            log("LTE_ROAMING : hasRegistered in Roam, persist.radio.rplmn(" + checkRplmn + "), LteRoamingOn(" + isLteRoamingOn + ")");
            if (("domestic".equals(checkRplmn) || "".equals(checkRplmn)) && !hasRegiedMccInKor) {
                SystemProperties.set("persist.radio.rplmn", "oversea");
                notifyApnChangeToRIL();
                if (!isLteRoamingOn) {
                    log("LTE_ROAMING : Home -> Roaming : setPreferredNetworkType G/W");
                    this.mCi.setPreferredNetworkType(3, null);
                    return;
                }
                return;
            }
            return;
        }
        log("LTE_ROAMING : hasRegistered, persist.radio.rplmn(" + checkRplmn + "), LteRoamingOn(" + isLteRoamingOn + ")");
        if (isLteRoamingOn) {
            dcTracker.setLteDataOnRoamingEnabled(false);
        }
        if ("oversea".equals(checkRplmn)) {
            log("LTE_ROAMING : getPreferredNetworkType by checkRplmn(oversea)");
            SystemProperties.set("persist.radio.rplmn", "domestic");
            notifyApnChangeToRIL();
            this.mCi.getPreferredNetworkType(obtainMessage(107, null));
        } else if (this.mReceivedHomeNetowkNoti) {
            log("LTE_ROAMING : getPreferredNetworkType by mReceivedHomeNetowkNoti");
            this.mCi.getPreferredNetworkType(obtainMessage(107, null));
        } else {
            log("LTE_ROAMING : checkRplmn(null or domestic) && mReceivedHomeNetowkNoti(false)");
        }
        this.mReceivedHomeNetowkNoti = false;
    }

    private void notifyApnChangeToRIL() {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(bos);
        try {
            dos.writeByte(9);
            dos.writeByte(4);
            dos.writeShort(4);
            this.mPhone.invokeOemRilRequestRaw(bos.toByteArray(), null);
            log("LTE_ROAMING : notifyApnChangeToRIL");
        } catch (IOException e) {
            loge("exception occured during refresh Attach APN" + e);
        }
        if (dos != null) {
            try {
                dos.close();
            } catch (IOException e2) {
                loge("close fail!!!");
                return;
            }
        }
        if (bos != null) {
            bos.close();
        }
    }

    private void SyncPreferredNetworkType(int type) {
        boolean isLteOn = true;
        if ("LGT".equals("")) {
            log("LTE_ROAMING : SyncPreferredNetworkType set default PREFERRED_NETWORK_MODE ");
            if (type == RILConstants.PREFERRED_NETWORK_MODE) {
                log("LTE_ROAMING : SyncPreferredNetworkType nothing");
                return;
            } else {
                this.mCi.setPreferredNetworkType(RILConstants.PREFERRED_NETWORK_MODE, null);
                return;
            }
        }
        if (Secure.getInt(this.mPhone.getContext().getContentResolver(), "lte_mode_on", 1) != 1) {
            isLteOn = false;
        }
        log("LTE_ROAMING : GetPreferredNetworkMode type is " + type + ", lte_mode_on is " + (isLteOn ? " enabled " : " disabled "));
        if (isLteOn) {
            if (type == 9) {
                log("LTE_ROAMING : SyncPreferredNetworkType LTE : nothing");
                return;
            }
            log("LTE_ROAMING : SyncPreferredNetworkType LTE : set to G/W/L");
            this.mCi.setPreferredNetworkType(9, null);
        } else if (type == 3 || type == 0) {
            log("LTE_ROAMING : SyncPreferredNetworkType 3G : nothing");
        } else {
            log("LTE_ROAMING : SyncPreferredNetworkType 3G: set to G/W");
            this.mCi.setPreferredNetworkType(3, null);
        }
    }

    public boolean needToRunLteRoaming() {
        String simType = SystemProperties.get("ril.simtype", "");
        if (("2".equals(simType) && "KTT".equals("")) || (("3".equals(simType) && "LGT".equals("")) || ("4".equals(simType) && "SKT".equals("")))) {
            return true;
        }
        return false;
    }

    public String getMdnNumber() {
        return this.mMdn;
    }

    private void notiHomePlmn() {
        String checkRplmn = SystemProperties.get("persist.radio.rplmn", "");
        String simState = getSystemProperty("gsm.sim.state", "");
        if (!"3".equals(SystemProperties.get("ril.simtype", ""))) {
            this.mReceivedHomeNetowkNoti = true;
        }
        log("LTE_ROAMING : notiHomePlmn, persist.radio.rplmn(" + checkRplmn + "), gsm.sim.state(" + simState + ")");
        if ("oversea".equals(checkRplmn)) {
            SystemProperties.set("persist.radio.rplmn", "domestic");
            if ("READY".equals(simState)) {
                log("LTE_ROAMING : Roaming -> Home : Airplane or no service scenario from modem noti");
                notifyApnChangeToRIL();
                this.mCi.getPreferredNetworkType(obtainMessage(107, null));
                return;
            }
            log("LTE_ROAMING : Roaming -> Home : Reboot scenario, from modem noti");
            this.mRetrySyncPrefMode = true;
        }
    }

    private void setMobileNetworkStatus() {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(bos);
        byte status = (byte) Global.getInt(this.mCr, "mobile_network_status", 0);
        log("setMobileNetworkStatus:" + status);
        try {
            dos.writeByte(22);
            dos.writeByte(11);
            dos.writeShort(5);
            dos.writeByte(status);
            this.mCi.invokeOemRilRequestRaw(bos.toByteArray(), null);
        } catch (IOException e) {
            log("exception occured during refresh Attach APN" + e);
        }
        if (dos != null) {
            try {
                dos.close();
            } catch (IOException e2) {
                log("close fail!!!");
                return;
            }
        }
        if (bos != null) {
            bos.close();
        }
    }

    protected void updateSpnWithEons(ServiceState serviceState, GsmCellLocation cellLocation) {
        if (serviceState.getVoiceRegState() != 0) {
            return;
        }
        if (serviceState.getOperatorNumeric() == null) {
            loge("operatorNumeric is null");
        } else if (this.mIccRecords != null && this.mIccRecords.mIsEnabledPNN && this.mIccRecords.mIsPNNExist) {
            String eons = ((SIMRecords) this.mIccRecords).getAllEonsNames(serviceState.getOperatorNumeric(), cellLocation.getLac());
            if (eons != null) {
                serviceState.setOperatorAlphaLong(eons);
                if (!SHIP_BUILD) {
                    log("update operatorAlphaLong to eonsName: " + eons);
                }
            }
        }
    }

    protected int displayLUrejectcause() {
        int id = 0;
        String salesCode = SystemProperties.get("ro.csc.sales_code");
        log("updateSpnDisplay(); mEmergencyOnly: " + this.mEmergencyOnly + ", isOn: " + this.mCi.getRadioState().isOn() + ", ss.getState(): " + this.mSS.getVoiceRegState() + ", mRilRegState: " + this.mRilRegState + ", mLuRejCause: " + this.mLuRejCause + ", after2min:" + this.after2min);
        if (this.mIccRecords.getIMSI() == null) {
            return 0;
        }
        if ((this.mSS.getVoiceRegState() != 1 && this.mSS.getVoiceRegState() != 2) || this.after2min) {
            return 0;
        }
        if ("TMB".equals(salesCode) && (this.mRilRegState == 3 || this.mRilRegState == 2 || this.mRilRegState == 13 || this.mRilRegState == 12)) {
            switch (this.mLuRejCause) {
                case 2:
                case 3:
                case 6:
                    id = 17041433;
                    break;
                case 11:
                case 12:
                case 13:
                    if (this.mSS.getVoiceRegState() != 1) {
                        id = 17040280;
                        break;
                    }
                    id = 17041433;
                    break;
                default:
                    if (!isWFCReigstered) {
                        id = 17041434;
                        break;
                    }
                    log("updateSpnDisplay: display T-Mobile PLMN");
                    break;
            }
        }
        if ("ATT".equals(salesCode) || "AIO".equals(salesCode)) {
            if (this.mRilRegState == 3 || this.mRilRegState == 2 || this.mRilRegState == 13 || this.mRilRegState == 12) {
                switch (this.mLuRejCause) {
                    case 2:
                        if (!this.mWakeLock.isHeld()) {
                            this.mWakeLock.acquire();
                        }
                        sendMessageDelayed(obtainMessage(Threads.ALERT_AMBER_THREAD), 119000);
                        showDeniedDialog(17041435);
                        return 17041435;
                    case 3:
                        if (!this.mWakeLock.isHeld()) {
                            this.mWakeLock.acquire();
                        }
                        sendMessageDelayed(obtainMessage(Threads.ALERT_AMBER_THREAD), 119000);
                        showDeniedDialog(17041436);
                        return 17041436;
                    case 6:
                        if (!this.mWakeLock.isHeld()) {
                            this.mWakeLock.acquire();
                        }
                        sendMessageDelayed(obtainMessage(Threads.ALERT_AMBER_THREAD), 119000);
                        showDeniedDialog(17041437);
                        return 17041437;
                    case 254:
                        if (!this.mWakeLock.isHeld()) {
                            this.mWakeLock.acquire();
                        }
                        sendMessageDelayed(obtainMessage(Threads.ALERT_AMBER_THREAD), 119000);
                        showDeniedDialog(17041438);
                        return 17041438;
                    default:
                        return 17040252;
                }
            } else if (this.mRilRegState != 2 && this.mRilRegState != 0) {
                return id;
            } else {
                int i = this.mLuRejCause;
                return 17040252;
            }
        } else if (!"BMC".equals(salesCode) && !"BWA".equals(salesCode) && !"FMC".equals(salesCode) && !"KDO".equals(salesCode) && !"MTA".equals(salesCode) && !"PCM".equals(salesCode) && !"RWC".equals(salesCode) && !"SOL".equals(salesCode) && !"SPC".equals(salesCode) && !"TLS".equals(salesCode) && !"VMC".equals(salesCode) && !"ESK".equals(salesCode) && !"GLW".equals(salesCode) && !"MCT".equals(salesCode) && !"VTR".equals(salesCode)) {
            return id;
        } else {
            if (this.mRilRegState == 3 || this.mRilRegState == 13) {
                switch (this.mLuRejCause) {
                    case 2:
                    case 3:
                    case 6:
                    case 8:
                        this.getRejectCauseDisplay = true;
                        return 17041439;
                    default:
                        return 17040280;
                }
            } else if (!this.getRejectCauseDisplay) {
                return id;
            } else {
                log("updateSpnDisplay() Already get reject cause so display text");
                return 17041439;
            }
        }
    }

    private void showDeniedDialog(int id) {
        if (!this.IsShow) {
            Builder builder = new Builder(this.mPhone.getContext());
            builder.setTitle(17039380);
            builder.setMessage(id);
            builder.setPositiveButton(17039370, this.onDenidedDialogClickListener);
            builder.setCancelable(false);
            deniedDialog = builder.create();
            deniedDialog.getWindow().setType(2007);
            deniedDialog.show();
            this.IsShow = true;
        }
    }

    private void dismissDeniedDialog() {
        if (deniedDialog != null) {
            try {
                log("dismiss deniedDialog");
                deniedDialog.dismiss();
                this.IsShow = false;
            } catch (Exception e) {
            } finally {
                deniedDialog = null;
            }
        }
    }

    protected void showRescanDialog() {
        Rlog.v("ManualSelectionReceiver", "showRescanDialog");
        Resources r = Resources.getSystem();
        Builder builder = new Builder(this.mPhone.getContext());
        builder.setMessage(r.getString(17041440));
        builder.setPositiveButton(r.getString(17039370), this.onRescanDialogClickListener);
        builder.setNegativeButton(r.getString(17039360), this.onRescanDialogClickListener);
        builder.setOnCancelListener(new C01127());
        mRescanDialog = builder.create();
        mRescanDialog.setOnDismissListener(new C01138());
        mRescanDialog.getWindow().setType(2003);
        mRescanDialog.show();
        this.IsFlightMode = false;
    }

    protected void dismissRescanDialog() {
        if (mRescanDialog != null) {
            try {
                Rlog.v("ManualSelectionReceiver", "dismissRescanDialog");
                mRescanDialog.dismiss();
            } catch (Exception e) {
            } finally {
                mRescanDialog = null;
            }
        }
    }

    protected String updateChinaSpnDisplay(String plmnValue) {
        String simState;
        String mPlmn = plmnValue;
        String mSim = getSystemProperty("gsm.sim.operator.numeric", "");
        String salesCode = SystemProperties.get("ro.csc.sales_code");
        loge("Access updateChinaSpnDisplay");
        if (this.mEmergencyOnly || mPlmn == null || mPlmn.length() <= 0 || this.mSS.getState() != 0) {
            if (mPlmn == null && this.mSS.getState() == 3 && !"CTC".equals(salesCode)) {
                mPlmn = Resources.getSystem().getText(17040252).toString();
            } else if (this.simSlotCount < 2) {
                simState = getSystemProperty("gsm.sim.state", "ABSENT");
                if ("ABSENT".equals(simState) || "UNKNOWN".equals(simState) || "NOT_READY".equals(simState)) {
                    mPlmn = Resources.getSystem().getText(17040267).toString();
                }
            }
        } else if (mSim.startsWith("460")) {
            String mAct = getSystemProperty("gsm.network.type", "");
            if (!this.mSS.getRoaming()) {
                if ("46001".equals(mSim) || "46009".equals(mSim)) {
                    mPlmn = Resources.getSystem().getText(17041521).toString() + getRatString(mAct);
                } else {
                    mPlmn = Resources.getSystem().getText(17041520).toString() + getRatString(mAct);
                }
                String spn = this.mIccRecords != null ? this.mIccRecords.getServiceProviderName() : "";
                if (!(TextUtils.isEmpty(spn) || "CMCC".equals(spn) || "CU".equals(spn))) {
                    loge("MVNO Operator - Copy SPN value to PLMN");
                    mPlmn = spn;
                }
            } else if (!(!"CHM".equals(salesCode) || "46001".equals(mSim) || "46009".equals(mSim))) {
                mPlmn = mPlmn + "(" + Resources.getSystem().getText(17041520).toString() + ")";
                Rlog.e(LOG_TAG, "[ROAM] It is roaming state with CHINA Operators SIM card. Except CMCC, Display registered plmn : " + mPlmn);
            }
        } else if (mSim.startsWith("466")) {
            if (!this.mSS.getRoaming()) {
                if ("46692".equals(mSim)) {
                    mPlmn = Resources.getSystem().getText(17041522).toString();
                } else if ("46601".equals(mSim)) {
                    mPlmn = Resources.getSystem().getText(17041523).toString();
                } else if ("46688".equals(mSim)) {
                    mPlmn = Resources.getSystem().getText(17041524).toString();
                } else if ("46689".equals(mSim)) {
                    mPlmn = Resources.getSystem().getText(17041525).toString();
                } else if ("46697".equals(mSim) || "46693".equals(mSim) || "46699".equals(mSim)) {
                    mPlmn = Resources.getSystem().getText(17041526).toString();
                }
            }
        } else if (mSim.startsWith("454")) {
            if (this.mSS.getRoaming()) {
                String opNum = this.mSS.getOperatorNumeric();
                if ("45407".equals(mSim) && opNum != null && opNum.startsWith("460")) {
                    mPlmn = Resources.getSystem().getText(17041521).toString();
                }
            } else if ("45412".equals(mSim) || "45413".equals(mSim)) {
                mPlmn = Resources.getSystem().getText(17041529).toString();
            } else if ("45407".equals(mSim) && this.mSS.getOperatorNumeric().startsWith("460")) {
                mPlmn = Resources.getSystem().getText(17041521).toString();
            }
        }
        if ("CTC".equals(salesCode)) {
            String CardStatusGSM = getSystemProperty("gsm.sim.state", "ABSENT");
            String CardOffStatus = getSystemProperty("gsm.sim.currentcardstatus", "3");
            int simDBvalue0 = System.getInt(this.mPhone.getContext().getContentResolver(), "phone1_on", 1);
            int simDBvalue1 = System.getInt(this.mPhone.getContext().getContentResolver(), "phone2_on", 1);
            log("CardStatusGSM = " + CardStatusGSM + " CardOffStatus = " + CardOffStatus);
            log("simDBvalue0 = " + simDBvalue0 + " simDBvalue1 = " + simDBvalue1);
            if (("UNKNOWN".equals(CardStatusGSM) || "READY".equals(CardStatusGSM)) && "2".equals(CardOffStatus)) {
                mPlmn = Resources.getSystem().getText(17041532).toString();
            } else if ("ABSENT".equals(CardStatusGSM) || "UNKNOWN".equals(CardStatusGSM) || "NOT_READY".equals(CardStatusGSM)) {
                mPlmn = Resources.getSystem().getText(17041533).toString();
            } else if ("PIN_REQUIRED".equals(CardStatusGSM) || "PUK_REQUIRED".equals(CardStatusGSM)) {
                mPlmn = Resources.getSystem().getText(17041531).toString();
            } else if ((simDBvalue0 == 0 && this.mPhone.mPhoneId == 0 && "3".equals(CardOffStatus)) || (simDBvalue1 == 0 && this.mPhone.mPhoneId == 1 && "3".equals(CardOffStatus))) {
                log("Sim will be activate/deactivate soon, plmn set to --");
                mPlmn = "--";
            }
            if (System.getInt(this.mCr, "airplane_mode_on", 0) == 1) {
                mPlmn = Resources.getSystem().getText(17039630).toString();
                log("Airplane mode, plmn = " + mPlmn);
            }
            if (TextUtils.isEmpty(mPlmn)) {
                return "--";
            }
            return mPlmn;
        } else if (this.simSlotCount >= 2) {
            return mPlmn;
        } else {
            simState = getSystemProperty("gsm.sim.state", "ABSENT");
            if ("ABSENT".equals(simState) || "UNKNOWN".equals(simState) || "NOT_READY".equals(simState)) {
                return Resources.getSystem().getText(17040267).toString();
            }
            return mPlmn;
        }
    }

    /* JADX WARNING: inconsistent code. */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    private void sendCallState(int r9, int r10) {
        /*
        r8 = this;
        r7 = 1;
        r0 = new java.io.ByteArrayOutputStream;
        r0.<init>();
        r1 = new java.io.DataOutputStream;
        r1.<init>(r0);
        r6 = 0;
        r3 = com.android.internal.telephony.PhoneFactory.getPhone(r6);
        r4 = com.android.internal.telephony.PhoneFactory.getPhone(r7);
        r5 = 5;
        if (r9 != 0) goto L_0x0091;
    L_0x0017:
        r6 = 11;
        r1.writeByte(r6);	 Catch:{ IOException -> 0x0061 }
        r6 = 24;
        r1.writeByte(r6);	 Catch:{ IOException -> 0x0061 }
        r1.writeShort(r5);	 Catch:{ IOException -> 0x0061 }
        r1.writeByte(r10);	 Catch:{ IOException -> 0x0061 }
        r6 = r0.toByteArray();	 Catch:{ IOException -> 0x0061 }
        r7 = 0;
        r4.invokeOemRilRequestRaw(r6, r7);	 Catch:{ IOException -> 0x0061 }
        r6 = new java.lang.StringBuilder;	 Catch:{ IOException -> 0x0061 }
        r6.<init>();	 Catch:{ IOException -> 0x0061 }
        r7 = "[DSDS_TWOCHIP] Block slot";
        r6 = r6.append(r7);	 Catch:{ IOException -> 0x0061 }
        r7 = r8.mPhone;	 Catch:{ IOException -> 0x0061 }
        r7 = r7.getPhoneId();	 Catch:{ IOException -> 0x0061 }
        r6 = r6.append(r7);	 Catch:{ IOException -> 0x0061 }
        r7 = " call Value = ";
        r6 = r6.append(r7);	 Catch:{ IOException -> 0x0061 }
        r6 = r6.append(r10);	 Catch:{ IOException -> 0x0061 }
        r6 = r6.toString();	 Catch:{ IOException -> 0x0061 }
        r8.log(r6);	 Catch:{ IOException -> 0x0061 }
        r0.close();	 Catch:{ IOException -> 0x005c }
        r1.close();	 Catch:{ IOException -> 0x005c }
    L_0x005b:
        return;
    L_0x005c:
        r2 = move-exception;
        r2.printStackTrace();
        goto L_0x005b;
    L_0x0061:
        r2 = move-exception;
        r6 = new java.lang.StringBuilder;	 Catch:{ all -> 0x0084 }
        r6.<init>();	 Catch:{ all -> 0x0084 }
        r7 = "[DSDS_TWOCHIP] sendCallState() error:";
        r6 = r6.append(r7);	 Catch:{ all -> 0x0084 }
        r6 = r6.append(r2);	 Catch:{ all -> 0x0084 }
        r6 = r6.toString();	 Catch:{ all -> 0x0084 }
        r8.log(r6);	 Catch:{ all -> 0x0084 }
        r0.close();	 Catch:{ IOException -> 0x007f }
        r1.close();	 Catch:{ IOException -> 0x007f }
        goto L_0x005b;
    L_0x007f:
        r2 = move-exception;
        r2.printStackTrace();
        goto L_0x005b;
    L_0x0084:
        r6 = move-exception;
        r0.close();	 Catch:{ IOException -> 0x008c }
        r1.close();	 Catch:{ IOException -> 0x008c }
    L_0x008b:
        throw r6;
    L_0x008c:
        r2 = move-exception;
        r2.printStackTrace();
        goto L_0x008b;
    L_0x0091:
        if (r9 != r7) goto L_0x005b;
    L_0x0093:
        r6 = 11;
        r1.writeByte(r6);	 Catch:{ IOException -> 0x00bc }
        r6 = 24;
        r1.writeByte(r6);	 Catch:{ IOException -> 0x00bc }
        r1.writeShort(r5);	 Catch:{ IOException -> 0x00bc }
        r1.writeByte(r10);	 Catch:{ IOException -> 0x00bc }
        r6 = r0.toByteArray();	 Catch:{ IOException -> 0x00bc }
        r7 = 0;
        r3.invokeOemRilRequestRaw(r6, r7);	 Catch:{ IOException -> 0x00bc }
        r6 = "[DSDS_TWOCHIP]  Block slot1 call";
        r8.log(r6);	 Catch:{ IOException -> 0x00bc }
        r0.close();	 Catch:{ IOException -> 0x00b7 }
        r1.close();	 Catch:{ IOException -> 0x00b7 }
        goto L_0x005b;
    L_0x00b7:
        r2 = move-exception;
        r2.printStackTrace();
        goto L_0x005b;
    L_0x00bc:
        r2 = move-exception;
        r6 = new java.lang.StringBuilder;	 Catch:{ all -> 0x00e0 }
        r6.<init>();	 Catch:{ all -> 0x00e0 }
        r7 = "[DSDS_TWOCHIP] sendCallState() error:";
        r6 = r6.append(r7);	 Catch:{ all -> 0x00e0 }
        r6 = r6.append(r2);	 Catch:{ all -> 0x00e0 }
        r6 = r6.toString();	 Catch:{ all -> 0x00e0 }
        r8.log(r6);	 Catch:{ all -> 0x00e0 }
        r0.close();	 Catch:{ IOException -> 0x00da }
        r1.close();	 Catch:{ IOException -> 0x00da }
        goto L_0x005b;
    L_0x00da:
        r2 = move-exception;
        r2.printStackTrace();
        goto L_0x005b;
    L_0x00e0:
        r6 = move-exception;
        r0.close();	 Catch:{ IOException -> 0x00e8 }
        r1.close();	 Catch:{ IOException -> 0x00e8 }
    L_0x00e7:
        throw r6;
    L_0x00e8:
        r2 = move-exception;
        r2.printStackTrace();
        goto L_0x00e7;
        */
        throw new UnsupportedOperationException("Method not decompiled: com.android.internal.telephony.gsm.GsmServiceStateTracker.sendCallState(int, int):void");
    }

    protected String getRatString(String netType) {
        String[] mDataType = netType.split(":");
        loge("netType : " + mDataType[0]);
        if ("UMTS".equals(mDataType[0]) || "HSPA".equals(mDataType[0]) || "HSDPA".equals(mDataType[0]) || "HSUPA".equals(mDataType[0]) || "HSPAP".equals(mDataType[0]) || "TD-SCDMA".equals(mDataType[0])) {
            return " 3G";
        }
        if ("LTE".equals(mDataType[0])) {
            return " 4G";
        }
        return "";
    }

    private String checkIgnoreNITZ(String plmnValue, String numericValue) {
        String mPlmn = plmnValue;
        int ntwType = this.mSS.getNetworkType();
        if (this.mEmergencyOnly || TextUtils.isEmpty(mPlmn) || this.mSS.getVoiceRegState() != 0) {
            return mPlmn;
        }
        int ntClass = TelephonyManager.getNetworkClass(ntwType);
        log("[checkIgnoreNITZ] ntClass: " + ntClass);
        if (ntClass != 1) {
            log("[checkIgnoreNITZ] - no change");
            return mPlmn;
        } else if ("72402".equals(numericValue) || "72403".equals(numericValue) || "72404".equals(numericValue)) {
            return "TIM";
        } else {
            if ("72410".equals(numericValue) || "72411".equals(numericValue) || "72406".equals(numericValue) || "72423".equals(numericValue)) {
                return "VIVO";
            }
            return mPlmn;
        }
    }

    private void setSilentReset() {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(bos);
        log("ril.deviceOffReq = 1");
        Intent intent = new Intent("android.intent.action.SILENT_RESETBY_DUALMODE");
        log("[DUALMODE] Broadcast SILENT_RESETBY_DUALMODE");
        this.mPhone.getContext().sendStickyBroadcastAsUser(intent, UserHandle.ALL);
        this.mPhone.setSystemProperty("ril.deviceOffReq", BOOT_WITH_TD);
        SystemClock.sleep(10);
        this.mCi.setRadioPower(false, null);
        SystemClock.sleep(20);
        try {
            dos.writeByte(16);
            dos.writeByte(2);
            dos.writeShort(4);
        } catch (IOException e) {
            e.printStackTrace();
        }
        this.mPhone.invokeOemRilRequestRaw(bos.toByteArray(), null);
        if (dos != null) {
            try {
                dos.close();
            } catch (IOException ex) {
                ex.printStackTrace();
                return;
            }
        }
        if (bos != null) {
            bos.close();
        }
    }

    private void sendMessageForDualmodeSilentReset() {
        String dualmodeTest = checkDualmodeTest();
        String simState = "";
        simState = SystemProperties.get("gsm.sim.state");
        if (!"true".equals(dualmodeTest) && !"ABSENT".equals(simState)) {
            sendMessage(obtainMessage(CallFailCause.KTF_FAIL_CAUSE_115));
        }
    }

    private void sendMessageForChangeNetworkMode() {
        String dualmodeTest = checkDualmodeTest();
        String simState = "";
        simState = SystemProperties.get("gsm.sim.state");
        if (!"true".equals(dualmodeTest) && !"ABSENT".equals(simState)) {
            this.mCi.getPreferredNetworkType(obtainMessage(117, null));
        }
    }

    private void sendMessageForCheckNetworkMode() {
        String dualmodeTest = checkDualmodeTest();
        String simState = "";
        simState = SystemProperties.get("gsm.sim.state");
        if (!"true".equals(dualmodeTest) && !"ABSENT".equals(simState)) {
            this.mCi.getPreferredNetworkType(obtainMessage(118, null));
        }
    }

    private void sendBroadcastRegisterDualmode() {
        Intent intent = new Intent("android.intent.action.REGISTER_DUALMODE");
        log("[DUALMODE] Broadcast REGISTER_DUALMODE");
        this.mPhone.getContext().sendStickyBroadcastAsUser(intent, UserHandle.ALL);
    }

    private String checkDualmodeTest() {
        String dualmodeTest = "";
        dualmodeTest = SystemProperties.get("persist.radio.dualmode.test", "");
        if ("true".equals(SystemProperties.get("persist.radio.master.testmode", ""))) {
            dualmodeTest = "true";
        }
        String sImsi = ((TelephonyManager) this.mPhone.getContext().getSystemService("phone")).getSubscriberId();
        if (sImsi != null && (sImsi.startsWith("999") || sImsi.startsWith("52036") || sImsi.startsWith("45001"))) {
            dualmodeTest = "true";
        }
        log("[DUALMODE] check dualmode test: " + dualmodeTest);
        return dualmodeTest;
    }

    private boolean checkCMCCTestPlmn() {
        String operatorNumeric = this.mSS.getOperatorNumeric();
        if (operatorNumeric == null || (!operatorNumeric.equals("46009") && !operatorNumeric.equals("46008") && !operatorNumeric.equals("46602") && !operatorNumeric.equals("00201") && !operatorNumeric.equals("00321") && !operatorNumeric.equals("00431") && !operatorNumeric.equals("00541") && !operatorNumeric.equals("00651") && !operatorNumeric.equals("00761") && !operatorNumeric.equals("00871") && !operatorNumeric.equals("00981"))) {
            return false;
        }
        return true;
    }

    protected boolean isFakeHomeOn(ServiceState s) {
        String operatorNumeric = s.getOperatorNumeric();
        if (this.mUiccController == null) {
            return false;
        }
        UiccCardApplication uiccApplication = this.mUiccController.getUiccCardApplication(1);
        if (uiccApplication == null) {
            return false;
        }
        IccRecords iccRecords = uiccApplication.getIccRecords();
        if (iccRecords == null) {
            return false;
        }
        String[] fho = iccRecords.getFakeHomeOn();
        if (fho == null || operatorNumeric == null || operatorNumeric.length() < 3) {
            return false;
        }
        for (String h : fho) {
            log("isRoamingBetweenOperators - h[" + h + "], operatorNumeric[" + operatorNumeric + "]");
            if (h.equals(operatorNumeric) || h.equals(operatorNumeric.substring(0, 3))) {
                return true;
            }
        }
        return false;
    }

    protected boolean isFakeRoamingOn(ServiceState s) {
        if (this.mPhone.mIccRecords == null) {
            return false;
        }
        String operatorNumeric = s.getOperatorNumeric();
        UiccCardApplication uiccApplication = this.mUiccController.getUiccCardApplication(1);
        if (uiccApplication == null) {
            return false;
        }
        IccRecords iccRecords = uiccApplication.getIccRecords();
        if (iccRecords == null) {
            return false;
        }
        String[] fro = iccRecords.getFakeRoamingOn();
        if (fro == null || operatorNumeric == null || operatorNumeric.length() < 3) {
            return false;
        }
        for (String r : fro) {
            log("isFakeRoamingBetweenOperators - r[" + r + "], operatorNumeric[" + operatorNumeric + "]");
            if (r.equals(operatorNumeric) || r.equals(operatorNumeric.substring(0, 3))) {
                return true;
            }
        }
        return false;
    }

    protected void displayTimeZoneRecommend(String operatorNumeric, int serviceState) {
        if (!TextUtils.isEmpty(operatorNumeric) && operatorNumeric.length() >= 5 && serviceState == 0) {
            int currentMcc = Integer.parseInt(operatorNumeric.substring(0, 3));
            int currentMnc = Integer.parseInt(operatorNumeric.substring(3, 5));
            String getPreviousMcc = System.getString(this.mPhone.getContext().getContentResolver(), "PREV_REGD_MCC");
            int previousMcc = -1;
            if (!TextUtils.isEmpty(getPreviousMcc)) {
                previousMcc = Integer.parseInt(getPreviousMcc);
            }
            String getPreviousMnc = System.getString(this.mPhone.getContext().getContentResolver(), "PREV_REGD_MNC");
            int previousMnc = -1;
            if (!TextUtils.isEmpty(getPreviousMnc)) {
                previousMnc = Integer.parseInt(getPreviousMnc);
            }
            if (currentMcc != 0) {
                System.putString(this.mPhone.getContext().getContentResolver(), "PREV_REGD_MCC", Integer.toString(currentMcc));
                System.putString(this.mPhone.getContext().getContentResolver(), "PREV_REGD_MNC", Integer.toString(currentMnc));
            }
            if ((this.mPhone.getPhoneId() == 0 || PhoneFactory.getDefaultPhone().getPhoneType() != 2 || PhoneFactory.getDefaultPhone().getServiceState().getState() != 0) && currentMcc != 0 && currentMcc != 460 && currentMcc != 455 && currentMcc != 454 && currentMcc != 466 && !this.mIsNitzReceived) {
                String[] timeZoneList = MccTable.getTimeZonesForMcc(currentMcc);
                if (currentMcc != previousMcc) {
                    if (timeZoneList != null) {
                        Rlog.d(LOG_TAG, "broadcast TZ Recommend");
                        Intent intent = new Intent("android.intent.action.ACTION_TZ_RCMD_TIMEZONE_OF_CURR_MCC");
                        intent.putExtra("currentMcc", currentMcc);
                        this.mPhone.getContext().sendBroadcast(intent);
                        return;
                    }
                    Rlog.d(LOG_TAG, "not broadcast TZ Recommend");
                } else if (currentMnc == previousMnc) {
                    Rlog.d(LOG_TAG, "displayTimeZoneRecommend. Do nothing.");
                } else if (timeZoneList == null || timeZoneList.length <= 1) {
                    Rlog.d(LOG_TAG, "not broadcast TZ Recommend Same Mcc");
                } else {
                    Rlog.d(LOG_TAG, "broadcast TZ Recommend Same Mcc");
                    this.mPhone.getContext().sendBroadcast(new Intent("android.intent.action.ACTION_TZ_RCMD_CURR_MCC_EQUAL_TO_LAST_MCC"));
                }
            }
        }
    }
}
