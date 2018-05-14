package com.android.internal.telephony.gsm;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Message;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.provider.Settings.Global;
import android.provider.Settings.System;
import android.telephony.Rlog;
import android.telephony.ServiceState;
import android.telephony.TelephonyManager;
import android.telephony.gsm.GsmCellLocation;
import android.text.TextUtils;
import android.util.EventLog;
import android.util.TimeUtils;
import com.android.internal.telephony.EventLogTags;
import com.android.internal.telephony.MccTable;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneFactory;
import com.android.internal.telephony.ServiceStateTracker;
import com.samsung.android.telephony.MultiSimManager;
import com.sec.android.app.CscFeature;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.TimeZone;

public class IRGsmServiceStateTracker extends GsmServiceStateTracker {
    static final String LOG_TAG = "IRGSMSST";
    private static final int MANUAL_SELECTION_OOS_TIMER = 90;
    static final int MAX_NUM_DATA_STATE_READS = 150;
    static final boolean VDBG = false;
    public static boolean mNitzRxed = false;
    public static String mPrevMcc = "";
    private static boolean mSleepPendedWhileNetSrchGsm = false;
    public static boolean oosTimerRunning = false;
    private int countCheckDataStateReads = 0;
    private long mCurrentSystemTime = 0;
    private BroadcastReceiver mIrIntentReceiver = new C01151();
    protected int propertyCount = 0;
    PendingIntent sender_ManSrchTimer;
    PendingIntent sender_ManSrchTimer_Dir;
    PendingIntent sender_NetSrchTimer;
    PendingIntent sender_NoSvcChkTimer;
    PendingIntent sender_PendingIntentTimer;
    PendingIntent sender_ReduceSearchTimer;

    class C01151 extends BroadcastReceiver {
        C01151() {
        }

        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            boolean isAirplaneMode = Global.getInt(IRGsmServiceStateTracker.this.mCr, "airplane_mode_on", 0) == 1;
            Rlog.i(IRGsmServiceStateTracker.LOG_TAG, "[Global mode] action = " + action);
            if (!"DCGGS".equals("DGG") || !IRGsmServiceStateTracker.this.noNeedToProcess(action)) {
                if ("android.intent.action.SCREEN_ON".equals(action)) {
                    IRGsmServiceStateTracker.mScreenOn = true;
                    if (!IRGsmServiceStateTracker.this.isPwrSaveModeTimerRunning()) {
                        return;
                    }
                    if (IRGsmServiceStateTracker.mGsmInSvc || IRGsmServiceStateTracker.mCdmaInSvc) {
                        IRGsmServiceStateTracker.this.processPwrSaveModeExpdTimer(IRGsmServiceStateTracker.this.mPhone, true);
                    } else {
                        IRGsmServiceStateTracker.this.processPwrSaveModeExpdTimer(IRGsmServiceStateTracker.this.mPhone, false);
                    }
                } else if ("android.intent.action.SCREEN_OFF".equals(action)) {
                    IRGsmServiceStateTracker.mScreenOn = false;
                } else if ("android.intent.action.ACTION_GLOBAL_NOSVC_CHK_TIMER_EXPIRED_GSM".equals(action)) {
                    if (isAirplaneMode) {
                        Rlog.d(IRGsmServiceStateTracker.LOG_TAG, "[Global mode] Now airplane mode on.");
                        IRGsmServiceStateTracker.this.stopGlobalNoSvcChkTimer();
                        return;
                    }
                    IRGsmServiceStateTracker.this.startGlobalNetworkSearchTimer();
                    if ((IRGsmServiceStateTracker.this.isGsmActive(0) && !IRGsmServiceStateTracker.mGsmInSvc) || (!IRGsmServiceStateTracker.this.isGsmActive(0) && !IRGsmServiceStateTracker.mCdmaInSvc && !IRGsmServiceStateTracker.mGsmInSvc)) {
                        IRGsmServiceStateTracker.this.sendNoServiceNotiIntent();
                    }
                } else if ("android.intent.action.ACTION_GLOBAL_NETWORK_SEARCH_TIMER_EXPIRED_INTERNAL_GSM".equals(action)) {
                    if (isAirplaneMode) {
                        Rlog.d(IRGsmServiceStateTracker.LOG_TAG, "[Global mode] Now airplane mode on.");
                        IRGsmServiceStateTracker.this.stopGlobalNetworkSearchTimer();
                    } else if (IRGsmServiceStateTracker.this.isGlobalMode(IRGsmServiceStateTracker.this.mPhone) && IRGsmServiceStateTracker.this.globalNoSvcChkTimerRequired(IRGsmServiceStateTracker.this.mPhone)) {
                        boolean gsmManSrchOngoing = System.getInt(IRGsmServiceStateTracker.this.mPhone.getContext().getContentResolver(), "GSM_MANUAL_SRCH_ONGOING", 0) == 1;
                        Rlog.d(IRGsmServiceStateTracker.LOG_TAG, "[Global mode] IsManSelMode: " + ServiceStateTracker.IsManSelMode + " gsmManSrchOngoing: " + gsmManSrchOngoing);
                        if (ServiceStateTracker.IsManSelMode || gsmManSrchOngoing || (!"DCGGS".equals("DGG") && IRGsmServiceStateTracker.this.mSS.getIsManualSelection())) {
                            Rlog.d(IRGsmServiceStateTracker.LOG_TAG, "[Global mode] gsm manual mode. Not send ACTION_GLOBAL_NETWORK_SEARCH_TIMER_EXPIRED_INTERNAL_GSM");
                            IRGsmServiceStateTracker.this.stopGlobalNetworkSearchTimer();
                            IRGsmServiceStateTracker.this.startGlobalNetworkSearchTimer();
                        } else if (IRGsmServiceStateTracker.this.isPwrSaveModeRequired()) {
                            IRGsmServiceStateTracker.this.startPwrSaveModeTimer(IRGsmServiceStateTracker.this.mPhone, 2);
                        } else {
                            IRGsmServiceStateTracker.this.sendNetChangeIntent(true);
                            if (IRGsmServiceStateTracker.oosTimerRunning) {
                                Rlog.d(IRGsmServiceStateTracker.LOG_TAG, "[Global mode] oosTimerRunning. stopManualOosTimer.");
                                IRGsmServiceStateTracker.this.stopManualOosTimer();
                            }
                            IRGsmServiceStateTracker.this.incNetSrchCnt(2);
                            ServiceStateTracker.currGsmMccInt = 0;
                        }
                    } else {
                        Rlog.d(IRGsmServiceStateTracker.LOG_TAG, "[Global mode] Ignore EXPIRED_INTERNAL_GSM.");
                        IRGsmServiceStateTracker.this.stopGlobalNetworkSearchTimer();
                    }
                } else if ("android.intent.action.ACTION_GLOBAL_PWR_SAVE_MODE_STAY_TIMER_EXPIRED".equals(action)) {
                    if (isAirplaneMode) {
                        Rlog.d(IRGsmServiceStateTracker.LOG_TAG, "[Global mode] Now airplane mode on.");
                        return;
                    }
                    Rlog.d(IRGsmServiceStateTracker.LOG_TAG, "[Global mode] mCurrentSrchNet : " + IRGsmServiceStateTracker.mCurrentSrchNet + " mPsmStayTimerProcessed : " + IRGsmServiceStateTracker.mPsmStayTimerProcessed);
                    if (!IRGsmServiceStateTracker.this.isPwrSaveModeTimerRunning()) {
                        return;
                    }
                    if (IRGsmServiceStateTracker.mGsmInSvc || IRGsmServiceStateTracker.mCdmaInSvc) {
                        IRGsmServiceStateTracker.this.processPwrSaveModeExpdTimer(IRGsmServiceStateTracker.this.mPhone, true);
                    } else {
                        IRGsmServiceStateTracker.this.processPwrSaveModeExpdTimer(IRGsmServiceStateTracker.this.mPhone, false);
                    }
                } else if ("android.intent.action.ACTION_GLOBAL_NET_SWITCH_SWITCH_BACK_TO_CDMA_IN_CHINA".equals(action)) {
                    IRGsmServiceStateTracker.this.stopManualOosTimerDirectly();
                } else if ("android.intent.action.ACTION_EVENT_OOS_TIMEOUT_RPT".equals(action)) {
                    boolean isGsmActive;
                    if ("DCGGS".equals("DGG")) {
                        isGsmActive = IRGsmServiceStateTracker.this.isGsmActive(1);
                    } else {
                        isGsmActive = "READY".equals(IRGsmServiceStateTracker.this.getSystemProperty("gsm.sim.state", "ABSENT"));
                    }
                    String str = IRGsmServiceStateTracker.LOG_TAG;
                    StringBuilder append = new StringBuilder().append("ACTION_EVENT_OOS_TIMEOUT_RPT expired!!! IsManualSelection : ");
                    boolean z = IRGsmServiceStateTracker.this.mSS.getIsManualSelection() || ServiceStateTracker.IsManSelMode;
                    Rlog.d(str, append.append(z).append(" isAirplaneMode : ").append(isAirplaneMode).append(" isGsmActive : ").append(isGsmActive).append(" mCurrentSrchNet:").append(IRGsmServiceStateTracker.mCurrentSrchNet).toString());
                    ServiceStateTracker.alreadyExpired = true;
                    boolean needToSendOosIntent = "DCGGS".equals("DGG") ? (IRGsmServiceStateTracker.this.mSS.getIsManualSelection() || ServiceStateTracker.IsManSelMode) && IRGsmServiceStateTracker.this.isGsmDfltPhoneIdx(IRGsmServiceStateTracker.this.mPhone) : IRGsmServiceStateTracker.this.mSS.getIsManualSelection() || ServiceStateTracker.IsManSelMode;
                    if (!needToSendOosIntent) {
                        IRGsmServiceStateTracker.this.stopManualOosTimer();
                    } else if (!isAirplaneMode && IRGsmServiceStateTracker.this.mSS.getState() != 0 && isGsmActive) {
                        Intent intentFwd = new Intent("android.intent.action.ACTION_200SEC_OOS_TIMER_EXPIRED");
                        intentFwd.putExtra("currScanNetwork", true);
                        IRGsmServiceStateTracker.this.mPhone.getContext().sendStickyBroadcast(intentFwd);
                        IRGsmServiceStateTracker.this.stopManualOosTimer();
                    }
                } else if ("android.intent.action.ACTION_SIMCARDMANAGER_LAUNCH_RESP".equals(action)) {
                    IRGsmServiceStateTracker.this.switchToCdmaInChinaMacauArea(false);
                } else if ("android.intent.action.ACTION_GLOBAL_NET_SWITCH_SWITCH_BACK_TO_GSM_IN_HONGKONG".equals(action)) {
                    IRGsmServiceStateTracker.this.pollState();
                } else if ("android.intent.action.ACTION_GLOBAL_NET_SWITCH_PENDING".equals(action)) {
                    extra = intent.getExtras();
                    if (!"CDMA".equals(extra.getString("pendedMode"))) {
                        Rlog.d(IRGsmServiceStateTracker.LOG_TAG, "[Global mode] pendedMode error:: " + extra.getString("pendedMode"));
                    } else if (extra.getBoolean("switchToCdmaInChinaMacauArea")) {
                        IRGsmServiceStateTracker.this.startPendingIntentTimer("CDMA", true);
                    } else {
                        IRGsmServiceStateTracker.this.startPendingIntentTimer("CDMA", false);
                    }
                } else if ("android.intent.action.ACTION_GLOBAL_NET_SWITCH_PENDING_TIMER_EXPIRED".equals(action)) {
                    extra = intent.getExtras();
                    if ("CDMA".equals(extra.getString("pendedMode"))) {
                        IRGsmServiceStateTracker.this.stopPendingIntentTimer();
                        if (extra.getBoolean("isSwitchToCdmaInChinaMacauArea")) {
                            IRGsmServiceStateTracker.this.switchToCdmaInChinaMacauArea(true);
                            return;
                        } else {
                            IRGsmServiceStateTracker.this.sendNetChangeIntent(true);
                            return;
                        }
                    }
                    Rlog.d(IRGsmServiceStateTracker.LOG_TAG, "[Global mode] pendedMode error:: " + extra.getString("pendedMode"));
                } else if ("ACTION_DUALMODE_SETTING".equals(action)) {
                    IRGsmServiceStateTracker.this.switchToCdmaInChinaMacauArea(false);
                } else if ("com.samsung.intent.action.Slot1SwitchCompleted".equals(action)) {
                    Rlog.d(IRGsmServiceStateTracker.LOG_TAG, "[Global Mode] ReduceSearchTime - Slot1SwitchCompleted");
                    if (!(ServiceStateTracker.mReduceSearchTimeShouldProceed && IRGsmServiceStateTracker.this.mSS.getState() != 0 && IRGsmServiceStateTracker.this.slot2ReduceSearchTimerAvailable())) {
                        ServiceStateTracker.mReduceSearchTimeShouldProceed = false;
                    }
                    Rlog.d(IRGsmServiceStateTracker.LOG_TAG, "[Global Mode] ReduceSearchTime - mReduceSearchTimeShouldProceed = " + ServiceStateTracker.mReduceSearchTimeShouldProceed);
                    mHasEverSwitchedToGsm = MultiSimManager.getTelephonyProperty("ril.mHasEverSwitchedToGsm", 0, "false");
                    if (ServiceStateTracker.mReduceSearchTimeShouldProceed && PhoneFactory.getPhone(0).getPhoneType() == 1 && "true".equals(mHasEverSwitchedToGsm)) {
                        long timeElapsed = 70000 - (SystemClock.elapsedRealtime() - IRGsmServiceStateTracker.this.mCurrentSystemTime);
                        Rlog.d(IRGsmServiceStateTracker.LOG_TAG, "[Global Mode] ReduceSearchTime - timeElapsed = " + timeElapsed);
                        if (timeElapsed > 1000) {
                            IRGsmServiceStateTracker.this.startReduceSearchTimer(timeElapsed);
                        } else {
                            ServiceStateTracker.mSlot1ShouldSwitchImmediately = true;
                            IRGsmServiceStateTracker.this.sendNetChangeIntent(true);
                        }
                    }
                    ServiceStateTracker.mReduceSearchTimeShouldProceed = false;
                } else if ("com.samsung.intent.action.ReduceSearchTimerExpired".equals(action)) {
                    Rlog.d(IRGsmServiceStateTracker.LOG_TAG, "[Global Mode] ReduceSearchTime - ReduceSearchTimerExpired");
                    mHasEverSwitchedToGsm = MultiSimManager.getTelephonyProperty("ril.mHasEverSwitchedToGsm", 0, "false");
                    if (IRGsmServiceStateTracker.this.mSS.getState() != 0 && PhoneFactory.getPhone(0).getPhoneType() == 1 && "true".equals(mHasEverSwitchedToGsm) && IRGsmServiceStateTracker.this.slot2ReduceSearchTimerAvailable()) {
                        ServiceStateTracker.mSlot1ShouldSwitchImmediately = true;
                        IRGsmServiceStateTracker.this.sendNetChangeIntent(true);
                    }
                } else if ("com.samsung.intent.action.SlotSwitched".equals(action)) {
                    IRGsmServiceStateTracker.this.SlotSwitched();
                } else {
                    Rlog.w(IRGsmServiceStateTracker.LOG_TAG, "RIL received unexpected Intent: " + action);
                }
            }
        }
    }

    public IRGsmServiceStateTracker(GSMPhone phone) {
        super(phone);
        log("phone_sim_slot:" + this.mPhone.getPhoneId() + ", phone_type:" + this.mPhone.getPhoneType());
        if (isGsmDfltPhoneIdx(this.mPhone)) {
            log("isGsmDfltPhoneIdx");
        }
        if (this.mPhone.getPhoneId() == 0) {
            log("[Global mode] switch to GSM, reset fakeDispCanceled to true");
            this.mPhone.setSystemProperty("ril.fakeDispCanceled", "true");
        }
        IntentFilter irFilter = new IntentFilter();
        irFilter.addAction("android.intent.action.ACTION_GLOBAL_NOSVC_CHK_TIMER_EXPIRED_GSM");
        irFilter.addAction("android.intent.action.ACTION_GLOBAL_NETWORK_SEARCH_TIMER_EXPIRED_INTERNAL_GSM");
        irFilter.addAction("android.intent.action.ACTION_GLOBAL_PWR_SAVE_MODE_ENTER_TIMER_EXPIRED");
        irFilter.addAction("android.intent.action.ACTION_GLOBAL_PWR_SAVE_MODE_STAY_TIMER_EXPIRED");
        irFilter.addAction("android.intent.action.SCREEN_ON");
        irFilter.addAction("android.intent.action.SCREEN_OFF");
        irFilter.addAction("android.intent.action.ACTION_EVENT_OOS_TIMEOUT_RPT");
        irFilter.addAction("android.intent.action.ACTION_GLOBAL_NET_SWITCH_SWITCH_BACK_TO_CDMA_IN_CHINA");
        irFilter.addAction("android.intent.action.ACTION_SIMCARDMANAGER_LAUNCH_RESP");
        irFilter.addAction("android.intent.action.ACTION_EVENT_OOS_TIMEOUT_DIRECT_RPT");
        irFilter.addAction("android.intent.action.ACTION_GLOBAL_NET_SWITCH_SWITCH_BACK_TO_GSM_IN_HONGKONG");
        irFilter.addAction("android.intent.action.ACTION_GLOBAL_NET_SWITCH_PENDING");
        irFilter.addAction("android.intent.action.ACTION_GLOBAL_NET_SWITCH_PENDING_TIMER_EXPIRED");
        irFilter.addAction("ACTION_DUALMODE_SETTING");
        irFilter.addAction("com.samsung.intent.action.SlotSwitched");
        if (this.mPhone.getPhoneId() == 1) {
            irFilter.addAction("com.samsung.intent.action.Slot1SwitchCompleted");
            irFilter.addAction("com.samsung.intent.action.ReduceSearchTimerExpired");
        }
        this.mPhone.getContext().registerReceiver(this.mIrIntentReceiver, irFilter);
        this.mCi.registerForRilConnected(this, 62, null);
    }

    public void dispose() {
        mGsmInSvc = false;
        stopManualOosTimer();
        stopManualOosTimerDirectly();
        stopPendingIntentTimer();
        stopGlobalNoSvcChkTimer();
        stopGlobalNetworkSearchTimer();
        super.dispose();
        this.mPhone.getContext().unregisterReceiver(this.mIrIntentReceiver);
    }

    protected void finalize() {
        log("finalize");
    }

    public void handleMessage(Message msg) {
        switch (msg.what) {
            case 50:
                log("EVENT_REQUEST_DISCONNECT_DC");
                if (this.mPhone.getPhoneId() == 1) {
                    Rlog.d(LOG_TAG, "slot2 data clean up for Slot1 switched.");
                    this.mPhone.mDcTracker.cleanUpAllConnections(Phone.REASON_SLOT_SWITCHED);
                    hangupAndPowerOff();
                    return;
                }
                powerOffRadioSafely(this.mPhone.mDcTracker);
                return;
            default:
                super.handleMessage(msg);
                return;
        }
    }

    protected void pollStateDone() {
        boolean hasLocationChanged;
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
        if (this.mNewCellLoc.equals(this.mCellLoc)) {
            hasLocationChanged = false;
        } else {
            hasLocationChanged = true;
        }
        if (hasVoiceRegStateChanged || hasDataRegStateChanged) {
            EventLog.writeEvent(EventLogTags.GSM_SERVICE_STATE_CHANGE, new Object[]{Integer.valueOf(this.mSS.getVoiceRegState()), Integer.valueOf(this.mSS.getDataRegState()), Integer.valueOf(this.mNewSS.getVoiceRegState()), Integer.valueOf(this.mNewSS.getDataRegState())});
        }
        if (this.mSS.getVoiceRegState() == 3 && this.mNewSS.getVoiceRegState() != 3) {
            this.IsFlightMode = true;
            sendMessageDelayed(obtainMessage(4000, null), 30000);
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
        if ("DCGGS".equals("DGG") || "DCGS".equals("DGG")) {
            if (this.mSS.getState() == 0 && isGsmActive(0)) {
                if (isGsmDfltPhoneIdx(this.mPhone)) {
                    mGsmInSvc = true;
                    System.putInt(this.mPhone.getContext().getContentResolver(), "CDMA_MANUAL_SELECTED", 0);
                    if ("DCGGS".equals("DGG") && this.mPhone.getPhoneId() == 1) {
                        if (this.mSS.getState() != 0) {
                            mReduceSearchTimeShouldProceed = false;
                        } else if (this.mCurrentSystemTime == 0 && mReduceSearchTimeShouldProceed && slot2ReduceSearchTimerAvailable()) {
                            this.mCurrentSystemTime = SystemClock.elapsedRealtime();
                            Rlog.d(LOG_TAG, "[Global Mode] ReduceSearchTime - mCurrentSystemTime = " + this.mCurrentSystemTime);
                        }
                    }
                }
            }
            if (isGsmDfltPhoneIdx(this.mPhone)) {
                mGsmInSvc = false;
            }
            if (this.mSS.getState() != 0) {
                this.mCurrentSystemTime = SystemClock.elapsedRealtime();
                Rlog.d(LOG_TAG, "[Global Mode] ReduceSearchTime - mCurrentSystemTime = " + this.mCurrentSystemTime);
            } else {
                mReduceSearchTimeShouldProceed = false;
            }
        }
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
        if (isGprsConsistent(this.mSS.getDataRegState(), this.mSS.getVoiceRegState())) {
            this.mReportedGprsNoReg = false;
        } else if (!(this.mStartedGprsRegCheck || this.mReportedGprsNoReg)) {
            this.mStartedGprsRegCheck = true;
            sendMessageDelayed(obtainMessage(22), (long) Global.getInt(this.mPhone.getContext().getContentResolver(), "gprs_register_check_period_ms", ServiceStateTracker.DEFAULT_GPRS_CHECK_PERIOD_MILLIS));
        }
        if ("DCGGS".equals("DGG") || "DCGS".equals("DGG")) {
            Intent intent;
            String str = LOG_TAG;
            StringBuilder append = new StringBuilder().append("manualselected=");
            boolean z = this.mSS.getIsManualSelection() || IsManSelMode;
            Rlog.d(str, append.append(z).append(", alreadyExpired=").append(alreadyExpired).append(", oosTimerRunning=").append(oosTimerRunning).toString());
            if (!this.mSS.getIsManualSelection() && !IsManSelMode) {
                alreadyExpired = false;
            } else if (this.mSS.getState() == 0) {
                Rlog.d(LOG_TAG, "manual selected but in service now.. Remove timer...");
                stopManualOosTimer();
                alreadyExpired = false;
            } else if (!(alreadyExpired || oosTimerRunning)) {
                Rlog.d(LOG_TAG, "manual selected and oos now...start timer...");
                startManualOosTimer();
            }
            if (hasChanged) {
                int currMccInt = 0;
                String currOperatorNumeric = getSystemProperty("gsm.operator.numeric", "");
                if (currOperatorNumeric.length() >= 5) {
                    currMccInt = Integer.parseInt(currOperatorNumeric.substring(0, 3));
                }
                mPrevMcc = System.getString(this.mPhone.getContext().getContentResolver(), "PREV_REGD_MCC");
                if (this.mSS.getState() == 0) {
                    int cdmaSs;
                    String imsiStr = PhoneFactory.getPhone(1).getSubscriberId();
                    String imsiPlmn = "";
                    String opNumStr = "";
                    if (isGsmActive(0)) {
                        cdmaSs = 1;
                    } else {
                        cdmaSs = PhoneFactory.getDefaultPhone().getServiceState().getState();
                    }
                    if (currOperatorNumeric.length() >= 5) {
                        opNumStr = currOperatorNumeric.substring(0, 5);
                    }
                    if (imsiStr != null && imsiStr.length() >= 5) {
                        imsiPlmn = imsiStr.substring(0, 5);
                    }
                    Rlog.d(LOG_TAG, "[TZ rcmnd] mPrevMcc : " + mPrevMcc + ", curMcc : " + currMccInt + ", cdmaSs : " + cdmaSs + ", imsiPlmn : " + imsiPlmn);
                    if (isFactoryMode(this.mPhone)) {
                        Rlog.d(LOG_TAG, "[TZ rcmnd] Factory mode. Timezone recommend doesn't work.");
                    } else {
                        if (!(currMccInt == 460 || currMccInt == 455 || cdmaSs == 0 || currMccInt == 0 || imsiPlmn.equals(opNumStr))) {
                            if (mNitzRxed) {
                                Rlog.d(LOG_TAG, "[TZ rcmnd] NITZ rxed. or currMccStr : " + currOperatorNumeric);
                            } else if (currOperatorNumeric.substring(0, 3).equals(mPrevMcc)) {
                                Rlog.d(LOG_TAG, "[TZ rcmnd] broadcast ACTION_TZ_RCMD_CURR_MCC_EQUAL_TO_LAST_MCC");
                                this.mPhone.getContext().sendBroadcast(new Intent("android.intent.action.ACTION_TZ_RCMD_CURR_MCC_EQUAL_TO_LAST_MCC"));
                            } else {
                                String[] tzNameArr = MccTable.getTimeZonesForMcc(currMccInt);
                                Rlog.d(LOG_TAG, "[TZ rcmnd] currMccInt : " + currMccInt);
                                Rlog.d(LOG_TAG, "[TZ rcmnd] =====================");
                                if (tzNameArr != null) {
                                    for (String zone2 : tzNameArr) {
                                        Rlog.d(LOG_TAG, "[TZ rcmnd] " + zone2);
                                    }
                                }
                                Rlog.d(LOG_TAG, "[TZ rcmnd] =====================");
                                if (tzNameArr != null) {
                                    intent = new Intent("android.intent.action.ACTION_TZ_RCMD_TIMEZONE_OF_CURR_MCC");
                                    intent.setFlags(270532608);
                                    intent.setComponent(new ComponentName("com.android.phone", "com.android.phone.TimeZoneRecommend"));
                                    intent.putExtra("tz_name_array", tzNameArr);
                                    intent.putExtra("showTimeScheme", true);
                                    this.mPhone.getContext().startActivity(intent);
                                }
                            }
                        }
                        if (currOperatorNumeric.length() >= 5 && currMccInt != 0) {
                            mPrevMcc = currOperatorNumeric.substring(0, 3);
                        }
                        System.putString(this.mPhone.getContext().getContentResolver(), "PREV_REGD_MCC", mPrevMcc);
                    }
                }
            }
            if (hasChanged || GsmServiceStateTracker.mNetSrchTimerRunning) {
                boolean globalmode = isGlobalMode(this.mPhone);
                Rlog.d(LOG_TAG, "[Global mode] globalmode = " + globalmode + " mCurrentSrchNet:" + mCurrentSrchNet);
                if (globalmode) {
                    if (this.mSS.getState() != 0 || mCurrentSrchNet == 1) {
                        if (globalNoSvcChkTimerRequired(this.mPhone) && GsmServiceStateTracker.mFirstCdmaNoSvcChkTimerStarted) {
                            startGlobalNoSvcChkTimer();
                        }
                    } else {
                        Rlog.d(LOG_TAG, "[Global mode] After global mode selected, gsm svc acquired. mNoSvcChkTimerRunning = " + GsmServiceStateTracker.mNoSvcChkTimerRunning + " mNetSrchTimerRunning = " + GsmServiceStateTracker.mNetSrchTimerRunning);
                        if ((GsmServiceStateTracker.mNoSvcChkTimerRunning || GsmServiceStateTracker.mNetSrchTimerRunning) && isGsmActive(0)) {
                            if (isGsmDfltPhoneIdx(this.mPhone)) {
                                stopGlobalNetworkSearchTimer();
                                Rlog.d(LOG_TAG, "[Global mode] Send gsm acquisition noti!");
                                intent = new Intent("android.intent.action.ACTION_GLOBAL_MODE_NETWORK_ACQUIRED");
                                intent.addFlags(536870912);
                                intent.putExtra("acuiredNetwork", true);
                                this.mPhone.getContext().sendStickyBroadcast(intent);
                                stopGlobalNoSvcChkTimer();
                            }
                        }
                    }
                }
            }
            if (hasChanged) {
                String currCdmaOprtNum = MultiSimManager.getTelephonyProperty("gsm.operator.numeric", 0, "");
                String currGsmOprtNum = getSystemProperty("gsm.operator.numeric", "");
                boolean isGsmBootupProgress = System.getInt(this.mPhone.getContext().getContentResolver(), "gsmbootupstart", 0) == 1;
                int currCdmaMccIntTmp = 0;
                int currGsmMccIntTmp = 0;
                if (currCdmaOprtNum.length() >= 5) {
                    currCdmaMccIntTmp = Integer.parseInt(currCdmaOprtNum.substring(0, 3));
                }
                if (currGsmOprtNum.length() >= 5) {
                    currGsmMccIntTmp = Integer.parseInt(currGsmOprtNum.substring(0, 3));
                }
                if (currCdmaMccIntTmp > 0) {
                    currGsmMccInt2 = currCdmaMccIntTmp;
                }
                Rlog.d(LOG_TAG, "[SimCardMngLaunch] currGsmMccIntTmp=" + currGsmMccIntTmp + ", currGsmMccInt2=" + currGsmMccInt2 + ", currCdmaMccIntTmp=" + currCdmaMccIntTmp + ", mSimCardMngLnchTimerRunning=" + mSimCardMngLnchTimerRunning + ", mSimCardMngEverLaunched=" + mSimCardMngEverLaunched + ", isGsmBootupProgress=" + isGsmBootupProgress);
                if (isGsmDfltPhoneIdx(this.mPhone)) {
                    currGsmMccIntTmp = currCdmaMccIntTmp;
                }
                if (currGsmMccIntTmp > 0) {
                    Rlog.d(LOG_TAG, "[SimCardMngLaunch] succeed in getting mcc from network during SimCardMngLnchTimerRunning.");
                    intent = new Intent("android.intent.action.ACTION_SIMCARDMANAGER_LAUNCH");
                    intent.addFlags(536870912);
                    if (isGsmActive(0) && (this.mSS.getState() == 0 || this.mSS.getState() == 2)) {
                        if (currGsmMccIntTmp == 460 || currGsmMccIntTmp == 455) {
                            intent.putExtra("china_mainland", true);
                            this.mPhone.setSystemProperty("gsm.ctc.chinamainland", "true");
                            this.mPhone.setSystemProperty("gsm.ctc.cdmaprefcountry", "false");
                            this.mPhone.setSystemProperty("ril.mIsSwitchedToCdma", "false");
                        } else if (isCdmaPrefAreas(currGsmMccIntTmp)) {
                            intent.putExtra("china_mainland", false);
                            this.mPhone.setSystemProperty("gsm.ctc.chinamainland", "false");
                            this.mPhone.setSystemProperty("gsm.ctc.cdmaprefcountry", "true");
                        } else {
                            intent.putExtra("china_mainland", false);
                            this.mPhone.setSystemProperty("gsm.ctc.chinamainland", "false");
                            this.mPhone.setSystemProperty("gsm.ctc.cdmaprefcountry", "false");
                        }
                        currGsmMccInt = currGsmMccIntTmp;
                        int prevCdmaMcc = Integer.parseInt(MultiSimManager.getTelephonyProperty("gsm.ctc.timedispschmmcc", 0, "0"));
                        String mHasEverSwitchedToGsm = MultiSimManager.getTelephonyProperty("ril.mHasEverSwitchedToGsm", 0, "false");
                        Rlog.d(LOG_TAG, "[global mode] currGsmMccInt : " + currGsmMccIntTmp + " prevGsmMccInt : " + prevGsmMccInt + " prevCdmaMcc: " + prevCdmaMcc);
                        if ("true".equals(mHasEverSwitchedToGsm)) {
                            if (!isDualSlotActive()) {
                                Rlog.d(LOG_TAG, "[Global Mode] in case of Slot1 GSM only, would not reset mHasEverSwitchedToGsm");
                            } else if (!(currCdmaMccIntTmp <= 0 || currCdmaMccIntTmp == prevCdmaMcc || currGsmMccInt == prevGsmMccInt || (isUsAreas(prevGsmMccInt) && isUsAreas(currGsmMccInt)))) {
                                Rlog.d(LOG_TAG, "[Global Mode] mHasEverSwitchedToGsm reset to false");
                                this.mPhone.setSystemProperty("ril.mHasEverSwitchedToGsm", "false");
                            }
                        }
                        if (prevGsmMccInt != currGsmMccInt) {
                            prevGsmMccInt = currGsmMccInt;
                        }
                    } else {
                        if (!isGsmActive(0)) {
                            Rlog.d(LOG_TAG, "[Global Mode] GSM Card is not active. Reset Gsm Mcc");
                        }
                        intent.putExtra("china_mainland", false);
                        this.mPhone.setSystemProperty("gsm.ctc.chinamainland", "false");
                        this.mPhone.setSystemProperty("gsm.ctc.cdmaprefcountry", "false");
                        currGsmMccInt = 0;
                    }
                    Rlog.d(LOG_TAG, "[SimCardMngLaunch] china_mainland : " + MultiSimManager.getTelephonyProperty("gsm.ctc.chinamainland", 0, "") + ", cdmaPrefCountry : " + MultiSimManager.getTelephonyProperty("gsm.ctc.cdmaprefcountry", 0, "") + ", currGsmMccInt : " + currGsmMccInt);
                } else if (this.mSS.getState() != 0) {
                    if (!(getCurrCdmaMcc() == 460 || getCurrCdmaMcc() == 455)) {
                        Rlog.d(LOG_TAG, "[SimCardMngLaunch] china_mainland reset to false.");
                        this.mPhone.setSystemProperty("gsm.ctc.chinamainland", "false");
                        this.mPhone.setSystemProperty("gsm.ctc.cdmaprefcountry", "false");
                    }
                    currGsmMccInt = 0;
                }
                switchToCdmaInChinaMacauArea(false);
                if (this.mSS.getState() == 0 && isPwrSaveModeTimerRunning()) {
                    processPwrSaveModeExpdTimer(this.mPhone, true);
                }
            }
        }
    }

    protected void log(String s) {
        Rlog.d(MultiSimManager.appendSimSlot(LOG_TAG, this.mPhone.mPhoneId), s);
    }

    protected void loge(String s) {
        Rlog.e(MultiSimManager.appendSimSlot(LOG_TAG, this.mPhone.mPhoneId), s);
    }

    boolean noNeedToProcess(String action) {
        if (this.mPhone.getPhoneId() != 1) {
            return false;
        }
        if (!"android.intent.action.ACTION_GLOBAL_NOSVC_CHK_TIMER_EXPIRED_GSM".equals(action) && !"android.intent.action.ACTION_GLOBAL_NETWORK_SEARCH_TIMER_EXPIRED_INTERNAL_GSM".equals(action) && !"android.intent.action.ACTION_GLOBAL_PWR_SAVE_MODE_STAY_TIMER_EXPIRED".equals(action) && !"android.intent.action.ACTION_GLOBAL_NET_SWITCH_SWITCH_BACK_TO_GSM_IN_HONGKONG".equals(action) && !"android.intent.action.ACTION_GLOBAL_NET_SWITCH_PENDING".equals(action) && !"android.intent.action.ACTION_GLOBAL_NET_SWITCH_PENDING_TIMER_EXPIRED".equals(action) && !"ACTION_DUALMODE_SETTING".equals(action)) {
            return false;
        }
        Rlog.d(LOG_TAG, "[global mode] No need to process:: " + action);
        return true;
    }

    public void sendNoServiceNotiIntent() {
        if ("DCGGS".equals("DGG") || "DCGS".equals("DGG")) {
            Intent intentFwd = new Intent("android.intent.action.ACTION_GLOBAL_NO_SERVICE_NOTIFICATION");
            intentFwd.addFlags(536870912);
            this.mPhone.getContext().sendStickyBroadcast(intentFwd);
        }
    }

    public void startGlobalNoSvcChkTimer() {
        if (!"DCGGS".equals("DGG") && !"DCGS".equals("DGG")) {
            return;
        }
        if (isNotFirstPhoneForCgg()) {
            Rlog.d(LOG_TAG, "[Global Mode] Current Phone is not a first Phone. Do not start no service check timer");
        } else if (GsmServiceStateTracker.mNetSrchTimerRunning || GsmServiceStateTracker.mNoSvcChkTimerRunning || GsmServiceStateTracker.mPendingIntentTimerRunning) {
            Rlog.d(LOG_TAG, "[Global mode] Prev Timer running - mNetSrchTimerRunning=" + GsmServiceStateTracker.mNetSrchTimerRunning + ", mNoSvcChkTimerRunning=" + GsmServiceStateTracker.mNoSvcChkTimerRunning + ", Do not start timer");
        } else {
            Rlog.d(LOG_TAG, "[Global mode] GSM startGlobalNoSvcChkTimer Start!!!");
            AlarmManager am = (AlarmManager) this.mPhone.getContext().getSystemService("alarm");
            this.sender_NoSvcChkTimer = PendingIntent.getBroadcast(this.mPhone.getContext(), 0, new Intent("android.intent.action.ACTION_GLOBAL_NOSVC_CHK_TIMER_EXPIRED_GSM"), 0);
            am.set(2, SystemClock.elapsedRealtime() + 5000, this.sender_NoSvcChkTimer);
            GsmServiceStateTracker.mNoSvcChkTimerRunning = true;
        }
    }

    public void stopGlobalNoSvcChkTimer() {
        if (!"DCGGS".equals("DGG") && !"DCGS".equals("DGG")) {
            return;
        }
        if (isNotFirstPhoneForCgg()) {
            Rlog.d(LOG_TAG, "[Global Mode] Current Phone is not a first Phone. Do not stop no service check timer");
            return;
        }
        ((AlarmManager) this.mPhone.getContext().getSystemService("alarm")).cancel(this.sender_NoSvcChkTimer);
        GsmServiceStateTracker.mNoSvcChkTimerRunning = false;
    }

    public void sendNetChangeIntent(boolean changeToCdma) {
        if (!"DCGGS".equals("DGG") && !"DCGS".equals("DGG")) {
            return;
        }
        if (!isNotFirstPhoneForCgg() || mSlot1ShouldSwitchImmediately) {
            mSlot1ShouldSwitchImmediately = false;
            if (!(mSleepPendedWhileNetSrchGsm || this.mWakeLock.isHeld())) {
                this.mWakeLock.acquire();
                sendMessageDelayed(obtainMessage(71), 3000);
                mSleepPendedWhileNetSrchGsm = true;
                Rlog.d(LOG_TAG, "[Global mode] Sleep pended while processing gsm net srch intent.");
            }
            Intent intentFwd = new Intent("android.intent.action.ACTION_GLOBAL_NETWORK_SEARCH_TIMER_EXPIRED");
            intentFwd.addFlags(536870912);
            if (changeToCdma) {
                intentFwd.putExtra("globalmodetype", true);
            } else {
                intentFwd.putExtra("globalmodetype", false);
            }
            this.mPhone.getContext().sendStickyBroadcast(intentFwd);
            Rlog.d(LOG_TAG, "[Global mode] ACTION_GLOBAL_NETWORK_SEARCH_TIMER_EXPIRED sent");
            return;
        }
        Rlog.d(LOG_TAG, "[Global Mode] Current Phone is not a first Phone. Do not send net change intent");
    }

    private void startManualOosTimer() {
        if (!"DCGGS".equals("DGG") && !"DCGS".equals("DGG")) {
            return;
        }
        if (isNotFirstPhoneForCgg()) {
            Rlog.d(LOG_TAG, "[Global Mode] Current Phone is not a first Phone. Do not start OosTimer");
            return;
        }
        AlarmManager am = (AlarmManager) this.mPhone.getContext().getSystemService("alarm");
        this.sender_ManSrchTimer = PendingIntent.getBroadcast(this.mPhone.getContext(), 0, new Intent("android.intent.action.ACTION_EVENT_OOS_TIMEOUT_RPT"), 0);
        am.set(2, SystemClock.elapsedRealtime() + 90000, this.sender_ManSrchTimer);
        oosTimerRunning = true;
    }

    private void stopManualOosTimer() {
        if (!"DCGGS".equals("DGG") && !"DCGS".equals("DGG")) {
            return;
        }
        if (isNotFirstPhoneForCgg()) {
            Rlog.d(LOG_TAG, "[Global Mode] Current Phone is not a first Phone. Do not stop OosTimer");
            return;
        }
        ((AlarmManager) this.mPhone.getContext().getSystemService("alarm")).cancel(this.sender_ManSrchTimer);
        oosTimerRunning = false;
    }

    private void startManualOosTimerDirectly() {
        if (!"DCGGS".equals("DGG") && !"DCGS".equals("DGG")) {
            return;
        }
        if (isNotFirstPhoneForCgg()) {
            Rlog.d(LOG_TAG, "[Global Mode] Current Phone is not a first Phone. Do not start OosTimerDirectly");
            return;
        }
        AlarmManager am = (AlarmManager) this.mPhone.getContext().getSystemService("alarm");
        this.sender_ManSrchTimer_Dir = PendingIntent.getBroadcast(this.mPhone.getContext(), 0, new Intent("android.intent.action.ACTION_EVENT_OOS_TIMEOUT_DIRECT_RPT"), 0);
        am.set(2, SystemClock.elapsedRealtime() + 30000, this.sender_ManSrchTimer_Dir);
    }

    private void stopManualOosTimerDirectly() {
        if (!"DCGGS".equals("DGG") && !"DCGS".equals("DGG")) {
            return;
        }
        if (isNotFirstPhoneForCgg()) {
            Rlog.d(LOG_TAG, "[Global Mode] Current Phone is not a first Phone. Do not stop OosTimerDirectly");
        } else {
            ((AlarmManager) this.mPhone.getContext().getSystemService("alarm")).cancel(this.sender_ManSrchTimer_Dir);
        }
    }

    public void startGlobalNetworkSearchTimer() {
        if (!"DCGGS".equals("DGG") && !"DCGS".equals("DGG")) {
            return;
        }
        if (isNotFirstPhoneForCgg()) {
            Rlog.d(LOG_TAG, "[Global Mode] Current Phone is not a first Phone. Do not start network search timer");
            return;
        }
        boolean isAirplaneMode;
        if (Global.getInt(this.mCr, "airplane_mode_on", 0) == 1) {
            isAirplaneMode = true;
        } else {
            isAirplaneMode = false;
        }
        stopGlobalNoSvcChkTimer();
        if (isAirplaneMode) {
            Rlog.d(LOG_TAG, "[Global mode] Now airplane mode on. Do not start gsm net srch timer");
        } else if (isPwrSaveModeTimerRunning() || GsmServiceStateTracker.mPendingIntentTimerRunning || GsmServiceStateTracker.mNetSrchTimerRunning) {
            Rlog.d(LOG_TAG, "[global mode] Do not start gsm net srch timer:: isPwrSaveModeTimerRunning:" + isPwrSaveModeTimerRunning() + " mPendingIntentTimerRunning:" + GsmServiceStateTracker.mPendingIntentTimerRunning + " mNetSrchTimerRunning:" + GsmServiceStateTracker.mNetSrchTimerRunning);
        } else {
            mCurrentSrchNet = 2;
            Rlog.d(LOG_TAG, "[Global mode] GSM startGlobalNetworkSearchTimer!!!");
            AlarmManager am = (AlarmManager) this.mPhone.getContext().getSystemService("alarm");
            this.sender_NetSrchTimer = PendingIntent.getBroadcast(this.mPhone.getContext(), 0, new Intent("android.intent.action.ACTION_GLOBAL_NETWORK_SEARCH_TIMER_EXPIRED_INTERNAL_GSM"), 0);
            am.set(2, SystemClock.elapsedRealtime() + 75000, this.sender_NetSrchTimer);
            GsmServiceStateTracker.mNetSrchTimerRunning = true;
            if (!GsmServiceStateTracker.mFirstCdmaNoSvcChkTimerStarted) {
                GsmServiceStateTracker.mFirstCdmaNoSvcChkTimerStarted = true;
            }
        }
    }

    public void stopGlobalNetworkSearchTimer() {
        if (!"DCGGS".equals("DGG") && !"DCGS".equals("DGG")) {
            return;
        }
        if (isNotFirstPhoneForCgg()) {
            Rlog.d(LOG_TAG, "[Global Mode] Current Phone is not a first Phone. Do not stop network search timer");
            return;
        }
        Rlog.d(LOG_TAG, "[Global mode] GSM stopGlobalNetworkSearchTimer!!! ");
        ((AlarmManager) this.mPhone.getContext().getSystemService("alarm")).cancel(this.sender_NetSrchTimer);
        GsmServiceStateTracker.mNetSrchTimerRunning = false;
        mCurrentSrchNet = 0;
    }

    public void switchToCdmaInChinaMacauArea(boolean isPendedProcess) {
        if ("DCGGS".equals("DGG") || "DCGS".equals("DGG")) {
            boolean isGsmBootupProgress;
            boolean isDsnetworkRunning;
            boolean isSlot1GsmActive = isGsmActive(0);
            boolean isSlot1GsmInUse = MultiSimManager.getTelephonyProperty("gsm.sim.activity.dual", 0, "false").equals("true");
            if (System.getInt(this.mPhone.getContext().getContentResolver(), "gsmbootupstart", 0) == 1) {
                isGsmBootupProgress = true;
            } else {
                isGsmBootupProgress = false;
            }
            if (System.getInt(this.mPhone.getContext().getContentResolver(), "DSNETWORK", 0) == 1) {
                isDsnetworkRunning = true;
            } else {
                isDsnetworkRunning = false;
            }
            boolean shouldBeSwitched = false;
            String mChinaNetSelCnfWaiting = MultiSimManager.getTelephonyProperty("ril.mChinaNetSelCnfWaiting", 0, "false");
            String mIsSwitchedToCdma = MultiSimManager.getTelephonyProperty("ril.mIsSwitchedToCdma", 0, "false");
            Rlog.d(LOG_TAG, "[global mode] switchToCdmaInChinaMacauArea() isSlot1GsmActive:" + isSlot1GsmActive + ", isSlot1GsmInUse:" + isSlot1GsmInUse + ", currGsmMccInt:" + currGsmMccInt);
            Rlog.d(LOG_TAG, "[global mode] currGsmMccInt2: " + currGsmMccInt2 + ", isGsmBootupProgress:" + isGsmBootupProgress + ", mIsSwitchedToCdma:" + mIsSwitchedToCdma);
            Rlog.d(LOG_TAG, "[global mode] getState:" + this.mSS.getState() + ", isDsnetworkRunning:" + isDsnetworkRunning + ", isGsmDfltPhoneIdx: " + isGsmDfltPhoneIdx(this.mPhone) + ", isPendedProcess: " + isPendedProcess);
            Rlog.d(LOG_TAG, "[global mode] isPendedProcess: " + isPendedProcess + ", mChinaNetSelCnfWaiting:" + mChinaNetSelCnfWaiting);
            if (!isGsmDfltPhoneIdx(this.mPhone)) {
                return;
            }
            if (this.mSS.getState() != 1 || isDualSlotActive()) {
                if (!isDsnetworkRunning) {
                    if (!"true".equals(mChinaNetSelCnfWaiting) || isPendedProcess) {
                        if ((currGsmMccInt == 460 || currGsmMccInt == 455 || currGsmMccInt2 == 460 || currGsmMccInt2 == 455) && "false".equals(mIsSwitchedToCdma)) {
                            shouldBeSwitched = true;
                        }
                        if ((isSlot1GsmActive || isSlot1GsmInUse) && !isGsmBootupProgress && shouldBeSwitched) {
                            if (mPendingIntentTimerRunning) {
                                stopPendingIntentTimer();
                            }
                            if (!(mSleepPendedWhileNetSrchGsm || this.mWakeLock.isHeld())) {
                                this.mWakeLock.acquire();
                                sendMessageDelayed(obtainMessage(71), 3000);
                                mSleepPendedWhileNetSrchGsm = true;
                            }
                            Intent intentFwd = new Intent("android.intent.action.ACTION_GLOBAL_NETWORK_SEARCH_TIMER_EXPIRED");
                            intentFwd.addFlags(536870912);
                            intentFwd.putExtra("globalmodetype", true);
                            intentFwd.putExtra("switchToCdmaInChinaMacauArea", true);
                            this.mPhone.getContext().sendStickyBroadcast(intentFwd);
                            Rlog.d(LOG_TAG, "[Global mode] ACTION_GLOBAL_NETWORK_SEARCH_TIMER_EXPIRED w/ switchToCdmaInChinaMacauArea sent");
                            this.mPhone.setSystemProperty("ril.mChinaNetSelCnfWaiting", "true");
                            return;
                        }
                        return;
                    }
                    Rlog.d(LOG_TAG, "[global mode] mChinaNetSelCnfWaiting. Exit!");
                }
            } else if (isSlot1GsmActive) {
                startGlobalNoSvcChkTimer();
            }
        }
    }

    public void startPendingIntentTimer(String pendedMode, boolean isSwitchToCdmaInChinaMacauArea) {
        if (!"DCGGS".equals("DGG") && !"DCGS".equals("DGG")) {
            return;
        }
        if (isNotFirstPhoneForCgg()) {
            Rlog.d(LOG_TAG, "[Global Mode] Current Phone is not a first Phone. Do not start pending intent timer");
            return;
        }
        boolean isAirplaneMode;
        if (Global.getInt(this.mCr, "airplane_mode_on", 0) == 1) {
            isAirplaneMode = true;
        } else {
            isAirplaneMode = false;
        }
        if (isAirplaneMode) {
            Rlog.d(LOG_TAG, "[Global mode] Now airplane mode on. Do not start gsm pending intent timer");
            return;
        }
        if (GsmServiceStateTracker.mPendingIntentTimerRunning) {
            stopPendingIntentTimer();
        }
        Rlog.d(LOG_TAG, "[Global mode] GSM startPendingIntentTimer pendedMode: " + pendedMode + " isSwitchToCdmaInChinaMacauArea: " + isSwitchToCdmaInChinaMacauArea);
        AlarmManager am = (AlarmManager) this.mPhone.getContext().getSystemService("alarm");
        Intent intent = new Intent("android.intent.action.ACTION_GLOBAL_NET_SWITCH_PENDING_TIMER_EXPIRED");
        if (pendedMode != null) {
            intent.putExtra("pendedMode", pendedMode);
        }
        if (isSwitchToCdmaInChinaMacauArea) {
            intent.putExtra("isSwitchToCdmaInChinaMacauArea", true);
        }
        this.sender_PendingIntentTimer = PendingIntent.getBroadcast(this.mPhone.getContext(), 0, intent, 268435456);
        am.set(2, SystemClock.elapsedRealtime() + 10000, this.sender_PendingIntentTimer);
        GsmServiceStateTracker.mPendingIntentTimerRunning = true;
    }

    public void stopPendingIntentTimer() {
        if (!"DCGGS".equals("DGG") && !"DCGS".equals("DGG")) {
            return;
        }
        if (isNotFirstPhoneForCgg()) {
            Rlog.d(LOG_TAG, "[Global Mode] Current Phone is not a first Phone. Do not stop pending intent timer");
            return;
        }
        Rlog.d(LOG_TAG, "[Global mode] GSM stopPendingIntentTimer!!! ");
        ((AlarmManager) this.mPhone.getContext().getSystemService("alarm")).cancel(this.sender_PendingIntentTimer);
        GsmServiceStateTracker.mPendingIntentTimerRunning = false;
    }

    private boolean slot2ReduceSearchTimerAvailable() {
        boolean isSlot2Gsm;
        if (this.mPhone.getPhoneId() == 1 && this.mPhone.getPhoneType() == 1) {
            isSlot2Gsm = true;
        } else {
            isSlot2Gsm = false;
        }
        boolean isAirplaneMode;
        if (System.getInt(this.mPhone.getContext().getContentResolver(), "airplane_mode_on", 0) == 1) {
            isAirplaneMode = true;
        } else {
            isAirplaneMode = false;
        }
        TelephonyManager tm = TelephonyManager.getDefault();
        boolean pinEnabled;
        if (tm.getSimState(1) == 2 || tm.getSimState(1) == 3) {
            pinEnabled = true;
        } else {
            pinEnabled = false;
        }
        boolean slot2CardAvailable;
        if (Integer.parseInt(getSystemProperty("gsm.sim.currentcardstatus", "9")) == 3) {
            slot2CardAvailable = true;
        } else {
            slot2CardAvailable = false;
        }
        if (("DCGGS".equals("DGG") || "DCGS".equals("DGG")) && isSlot2Gsm && !isAirplaneMode && !pinEnabled && slot2CardAvailable && !mGsmInSvc) {
            return true;
        }
        return false;
    }

    public void startReduceSearchTimer(long timeElapsed) {
        if ("DCGGS".equals("DGG") || "DCGS".equals("DGG")) {
            Rlog.d(LOG_TAG, "[Global Mode] ReduceSearchTime - startReduceSearchTimer, timeElapsed = " + timeElapsed);
            AlarmManager am = (AlarmManager) this.mPhone.getContext().getSystemService("alarm");
            this.sender_ReduceSearchTimer = PendingIntent.getBroadcast(this.mPhone.getContext(), 0, new Intent("com.samsung.intent.action.ReduceSearchTimerExpired"), 0);
            am.set(2, SystemClock.elapsedRealtime() + timeElapsed, this.sender_ReduceSearchTimer);
        }
    }

    public void SlotSwitched() {
        Rlog.d(LOG_TAG, "CdmaServiceStateTracker - SlotSwitched");
        this.mDesiredPowerState = true;
        this.countCheckDataStateReads = 151;
        sendMessage(obtainMessage(50));
    }

    public int getCurrCdmaMcc() {
        int currCdmaMccInt = 0;
        String currCdmaOprtNum = MultiSimManager.getTelephonyProperty("gsm.operator.numeric", 0, "");
        if (currCdmaOprtNum.length() >= 5) {
            currCdmaMccInt = Integer.parseInt(currCdmaOprtNum.substring(0, 3));
        }
        return (!"DCGGS".equals("DGG") || "0".equals(getSlotSelectionInformation())) ? currCdmaMccInt : 0;
    }

    public boolean isNotFirstPhoneForCgg() {
        if (!"DCGGS".equals("DGG") || this.mPhone.getPhoneId() == 0) {
            return false;
        }
        Rlog.d(LOG_TAG, "[Global Mode] This is not a first phone");
        return true;
    }
}
