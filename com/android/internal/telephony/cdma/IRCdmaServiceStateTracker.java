package com.android.internal.telephony.cdma;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Message;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.provider.Settings.Global;
import android.provider.Settings.System;
import android.provider.Telephony.CellBroadcasts;
import android.telephony.Rlog;
import android.telephony.ServiceState;
import android.telephony.SubscriptionManager;
import android.telephony.cdma.CdmaCellLocation;
import android.text.TextUtils;
import android.util.EventLog;
import com.android.internal.telephony.CommandsInterface.RadioState;
import com.android.internal.telephony.EventLogTags;
import com.android.internal.telephony.MccTable;
import com.android.internal.telephony.PhoneFactory;
import com.android.internal.telephony.ServiceStateTracker;
import com.samsung.android.telephony.MultiSimManager;
import com.sec.android.app.CscFeature;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public class IRCdmaServiceStateTracker extends CdmaServiceStateTracker {
    static final String LOG_TAG = "IRCDMASST";
    static final int MAX_NUM_DATA_STATE_READS = 150;
    private static boolean mSleepPendedWhileNetSrchCdma = false;
    private int countCheckDataStateReads = 0;
    private int mCurrentCdmaMcc = 0;
    private BroadcastReceiver mIrIntentReceiver = new C00831();
    PendingIntent prlGettingRetrySender;
    PendingIntent sender_FakeDispCancelTimer;
    PendingIntent sender_NetSrchTimer;
    PendingIntent sender_NoSvcChkTimer;
    PendingIntent sender_PendingIntentTimer;

    class C00831 extends BroadcastReceiver {
        C00831() {
        }

        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            boolean isAirplaneMode = Global.getInt(IRCdmaServiceStateTracker.this.mCr, "airplane_mode_on", 0) == 1;
            Rlog.i(IRCdmaServiceStateTracker.LOG_TAG, "[Global mode] action = " + action);
            if ("android.intent.action.SCREEN_ON".equals(action)) {
                IRCdmaServiceStateTracker.mScreenOn = true;
                if (!IRCdmaServiceStateTracker.this.isPwrSaveModeTimerRunning()) {
                    return;
                }
                if (IRCdmaServiceStateTracker.mCdmaInSvc || IRCdmaServiceStateTracker.mGsmInSvc) {
                    IRCdmaServiceStateTracker.this.processPwrSaveModeExpdTimer(IRCdmaServiceStateTracker.this.mPhone, true);
                } else {
                    IRCdmaServiceStateTracker.this.processPwrSaveModeExpdTimer(IRCdmaServiceStateTracker.this.mPhone, false);
                }
            } else if ("android.intent.action.SCREEN_OFF".equals(action)) {
                IRCdmaServiceStateTracker.mScreenOn = false;
            } else if ("android.intent.action.ACTION_GLOBAL_NOSVC_CHK_TIMER_EXPIRED_CDMA".equals(action)) {
                if (isAirplaneMode) {
                    Rlog.d(IRCdmaServiceStateTracker.LOG_TAG, "[Global mode] Now airplane mode on.");
                    IRCdmaServiceStateTracker.this.stopGlobalNoSvcChkTimer();
                    return;
                }
                IRCdmaServiceStateTracker.this.startGlobalNetworkSearchTimer();
                if (!IRCdmaServiceStateTracker.mCdmaInSvc && !IRCdmaServiceStateTracker.mGsmInSvc) {
                    IRCdmaServiceStateTracker.this.sendNoServiceNotiIntent();
                }
            } else if ("android.intent.action.ACTION_GLOBAL_NETWORK_SEARCH_TIMER_EXPIRED_INTERNAL_CDMA".equals(action)) {
                if (isAirplaneMode) {
                    Rlog.d(IRCdmaServiceStateTracker.LOG_TAG, "[Global mode] Now airplane mode on.");
                    IRCdmaServiceStateTracker.this.stopGlobalNetworkSearchTimer();
                } else if (IRCdmaServiceStateTracker.this.isGlobalMode(IRCdmaServiceStateTracker.this.mPhone) && IRCdmaServiceStateTracker.this.globalNoSvcChkTimerRequired(IRCdmaServiceStateTracker.this.mPhone) && !IRCdmaServiceStateTracker.this.isChinaAreas()) {
                    if (IRCdmaServiceStateTracker.this.isPwrSaveModeRequired()) {
                        IRCdmaServiceStateTracker.this.startPwrSaveModeTimer(IRCdmaServiceStateTracker.this.mPhone, 1);
                    } else {
                        IRCdmaServiceStateTracker.this.sendNetChangeIntent(false);
                        IRCdmaServiceStateTracker.this.incNetSrchCnt(1);
                    }
                    ServiceStateTracker.alreadyExpired = false;
                    IRCdmaServiceStateTracker.this.mPhone.setSystemProperty("ril.mIsSwitchedToCdma", "false");
                } else {
                    Rlog.d(IRCdmaServiceStateTracker.LOG_TAG, "[Global mode] Ignore EXPIRED_INTERNAL_CDMA.");
                    IRCdmaServiceStateTracker.this.stopGlobalNetworkSearchTimer();
                    if (!IRCdmaServiceStateTracker.this.isGlobalMode(IRCdmaServiceStateTracker.this.mPhone) && !IRCdmaServiceStateTracker.this.isChinaAreas() && IRCdmaServiceStateTracker.this.isSlot1CdmaActive() && IRCdmaServiceStateTracker.this.isSlot1DualCard()) {
                        Rlog.d(IRCdmaServiceStateTracker.LOG_TAG, "[Global mode] Caused by isGlobalMode is false. Start cdma srch timer again.");
                        IRCdmaServiceStateTracker.this.startGlobalNetworkSearchTimer();
                    }
                }
            } else if ("android.intent.action.ACTION_GLOBAL_PWR_SAVE_MODE_STAY_TIMER_EXPIRED".equals(action)) {
                if (isAirplaneMode) {
                    Rlog.d(IRCdmaServiceStateTracker.LOG_TAG, "[Global mode] Now airplane mode on.");
                } else if (!IRCdmaServiceStateTracker.this.isPwrSaveModeTimerRunning()) {
                } else {
                    if (IRCdmaServiceStateTracker.mCdmaInSvc || IRCdmaServiceStateTracker.mGsmInSvc) {
                        IRCdmaServiceStateTracker.this.processPwrSaveModeExpdTimer(IRCdmaServiceStateTracker.this.mPhone, true);
                    } else {
                        IRCdmaServiceStateTracker.this.processPwrSaveModeExpdTimer(IRCdmaServiceStateTracker.this.mPhone, false);
                    }
                }
            } else if ("android.intent.action.ACTION_SIMCARDMANAGER_LAUNCH_TIMER_EXPIRED".equals(action)) {
                if (!IRCdmaServiceStateTracker.mSimCardMngEverLaunched) {
                    int currGsmMccInt = 0;
                    int currCdmaMccInt = IRCdmaServiceStateTracker.this.getCurrCdmaMcc();
                    String currGsmOprtNum = MultiSimManager.getTelephonyProperty("gsm.operator.numeric", 1, "");
                    if (currGsmOprtNum.length() >= 5) {
                        currGsmMccInt = Integer.parseInt(currGsmOprtNum.substring(0, 3));
                    }
                    Rlog.d(IRCdmaServiceStateTracker.LOG_TAG, "[SimCardMngLaunch] simcardmanager launch timer expired.");
                    Rlog.d(IRCdmaServiceStateTracker.LOG_TAG, "[SimCardMngLaunch] currCdmaMccInt : " + currCdmaMccInt + " currGsmMccInt : " + currGsmMccInt);
                    Intent intentFwd = new Intent("android.intent.action.ACTION_SIMCARDMANAGER_LAUNCH");
                    intentFwd.addFlags(536870912);
                    if (currCdmaMccInt == 460 || currCdmaMccInt == 455 || currGsmMccInt == 460 || currGsmMccInt == 455) {
                        intentFwd.putExtra("china_mainland", true);
                    } else {
                        intentFwd.putExtra("china_mainland", false);
                    }
                    IRCdmaServiceStateTracker.this.mPhone.getContext().sendStickyBroadcast(intentFwd);
                    IRCdmaServiceStateTracker.this.stopSimCardMngLaunchTimer(IRCdmaServiceStateTracker.this.mPhone);
                    IRCdmaServiceStateTracker.mSimCardMngEverLaunched = true;
                }
            } else if ("PRL_GETTING_RETRY_TIMER".equals(action)) {
                Rlog.d(IRCdmaServiceStateTracker.LOG_TAG, "[Global mode] PRL_GETTING_RETRY_TIMER expired!!!");
                IRCdmaServiceStateTracker.this.mCi.getBasebandVersion(IRCdmaServiceStateTracker.this.obtainMessage(60));
                IRCdmaServiceStateTracker.this.getSubscriptionInfoAndStartPollingThreads();
                IRCdmaServiceStateTracker.this.getCdmaMin();
                ((AlarmManager) IRCdmaServiceStateTracker.this.mPhone.getContext().getSystemService("alarm")).cancel(IRCdmaServiceStateTracker.this.prlGettingRetrySender);
                if (!"DCGGS".equals("DGG") && !"DCGS".equals("DGG")) {
                    return;
                }
                if (!IRCdmaServiceStateTracker.this.isFirstCdmaNoSvcChkTimerStarted() || IRCdmaServiceStateTracker.this.isNetSrchTimerRunning()) {
                    boolean globalmode = IRCdmaServiceStateTracker.this.isGlobalMode(IRCdmaServiceStateTracker.this.mPhone);
                    Rlog.d(IRCdmaServiceStateTracker.LOG_TAG, "[Global mode] globalmode = " + globalmode + " mCurrentSrchNet:" + IRCdmaServiceStateTracker.mCurrentSrchNet);
                    if (!globalmode) {
                        return;
                    }
                    if (IRCdmaServiceStateTracker.this.mSS.getState() == 0 && "true".equals(IRCdmaServiceStateTracker.this.getSystemProperty("ril.fakeDispCanceled", "false")) && IRCdmaServiceStateTracker.mCurrentSrchNet != 2) {
                        Rlog.d(IRCdmaServiceStateTracker.LOG_TAG, "[Global mode] After global mode selected, cdma svc acquired. mNoSvcChkTimerRunning = " + IRCdmaServiceStateTracker.this.isNoSvcChkTimerRunning() + " mNetSrchTimerRunning = " + IRCdmaServiceStateTracker.this.isNetSrchTimerRunning());
                        if (IRCdmaServiceStateTracker.this.isNoSvcChkTimerRunning() || IRCdmaServiceStateTracker.this.isNetSrchTimerRunning()) {
                            IRCdmaServiceStateTracker.this.stopGlobalNetworkSearchTimer();
                            Rlog.d(IRCdmaServiceStateTracker.LOG_TAG, "[Global mode] Send cdma acquisition noti!");
                            Intent intentNetAcq = new Intent("android.intent.action.ACTION_GLOBAL_MODE_NETWORK_ACQUIRED");
                            intentNetAcq.addFlags(536870912);
                            intentNetAcq.putExtra("acuiredNetwork", false);
                            IRCdmaServiceStateTracker.this.mPhone.getContext().sendStickyBroadcast(intentNetAcq);
                            IRCdmaServiceStateTracker.this.stopGlobalNoSvcChkTimer();
                        }
                    } else if (IRCdmaServiceStateTracker.this.globalNoSvcChkTimerRequired(IRCdmaServiceStateTracker.this.mPhone)) {
                        IRCdmaServiceStateTracker.this.startGlobalNoSvcChkTimer();
                    }
                }
            } else if ("SEND_BACKGROUND_SWITCHING".equals(action)) {
                boolean switchedGsm = false;
                if (!ServiceStateTracker.IsGlobalModeAvail) {
                    ServiceStateTracker.IsGlobalModeAvail = true;
                    switchedGsm = IRCdmaServiceStateTracker.this.switchToGsmInCdmaRoamingArea(false);
                }
                boolean isCdmaManSel = IRCdmaServiceStateTracker.this.isCdmaManSel(IRCdmaServiceStateTracker.this.mPhone);
                Rlog.d(IRCdmaServiceStateTracker.LOG_TAG, "[Global mode] SEND_BACKGROUND_SWITCHING isCdmaManSel:" + isCdmaManSel + " isSlot1DualCard: " + IRCdmaServiceStateTracker.this.isSlot1DualCard());
                if (!(switchedGsm || IRCdmaServiceStateTracker.this.isFirstCdmaNoSvcChkTimerStarted())) {
                    if (("DCGGS".equals("DGG") || "DCGS".equals("DGG")) && "false".equals(IRCdmaServiceStateTracker.this.getSystemProperty("ril.cdmaShortSrched", "false")) && !isCdmaManSel) {
                        IRCdmaServiceStateTracker.this.startGlobalNetworkSearchTimer(true);
                        IRCdmaServiceStateTracker.this.mPhone.setSystemProperty("ril.cdmaShortSrched", "true");
                    } else if (IRCdmaServiceStateTracker.this.globalNoSvcChkTimerRequired(IRCdmaServiceStateTracker.this.mPhone)) {
                        Rlog.d(IRCdmaServiceStateTracker.LOG_TAG, "[Global mode] Currently cdma noSvc timer not started yet. Start here.");
                        IRCdmaServiceStateTracker.this.startGlobalNoSvcChkTimer();
                    }
                }
                if ((isCdmaManSel && "2012".equals(CscFeature.getInstance().getString("CscFeature_RIL_ConfigSpecForCtcMtrIR"))) || !IRCdmaServiceStateTracker.this.isSlot1DualCard()) {
                    Rlog.d(IRCdmaServiceStateTracker.LOG_TAG, "[Global mode] set fakeDispCanceled to true");
                    IRCdmaServiceStateTracker.this.mPhone.setSystemProperty("ril.fakeDispCanceled", "true");
                    IRCdmaServiceStateTracker.this.setFakeDispCancelToCP();
                    IRCdmaServiceStateTracker.this.updateSpnDisplay();
                } else if (IRCdmaServiceStateTracker.this.getCurrCdmaMcc() > 0) {
                    IRCdmaServiceStateTracker.this.startFakeDispCancelTimer();
                }
            } else if ("android.intent.action.ACTION_GLOBAL_NET_SWITCH_SWITCH_BACK_TO_CDMA_IN_CHINA".equals(action)) {
                IRCdmaServiceStateTracker.this.pollState();
            } else if ("android.intent.action.ACTION_GLOBAL_NET_SWITCH_PENDING".equals(action)) {
                extra = intent.getExtras();
                if (!"GSM".equals(extra.getString("pendedMode"))) {
                    Rlog.d(IRCdmaServiceStateTracker.LOG_TAG, "[Global mode] pendedMode error:: " + extra.getString("pendedMode"));
                } else if (extra.getBoolean("switchToGsmInCdmaRoamingArea")) {
                    IRCdmaServiceStateTracker.this.startPendingIntentTimer("GSM", true);
                } else {
                    IRCdmaServiceStateTracker.this.startPendingIntentTimer("GSM", false);
                }
            } else if ("android.intent.action.ACTION_GLOBAL_NET_SWITCH_PENDING_TIMER_EXPIRED".equals(action)) {
                extra = intent.getExtras();
                if ("GSM".equals(extra.getString("pendedMode"))) {
                    IRCdmaServiceStateTracker.this.stopPendingIntentTimer();
                    if (extra.getBoolean("isSwitchToGsmInCdmaRoamingArea")) {
                        IRCdmaServiceStateTracker.this.switchToGsmInCdmaRoamingArea(true);
                        return;
                    } else if (IRCdmaServiceStateTracker.mCdmaInSvc) {
                        Rlog.d(IRCdmaServiceStateTracker.LOG_TAG, "[Global mode] cdma inSvc! no need to switch!!!");
                        return;
                    } else {
                        IRCdmaServiceStateTracker.this.sendNetChangeIntent(false);
                        return;
                    }
                }
                Rlog.d(IRCdmaServiceStateTracker.LOG_TAG, "[Global mode] pendedMode error:: " + extra.getString("pendedMode"));
            } else if ("ACTION_DUALMODE_SETTING".equals(action)) {
                IRCdmaServiceStateTracker.this.switchToGsmInCdmaRoamingArea(false);
            } else if ("android.intent.action.ACTION_FAKE_DISP_CANCEL_TIMER".equals(action)) {
                Rlog.d(IRCdmaServiceStateTracker.LOG_TAG, "[Global mode] set fakeDispCanceled to true");
                IRCdmaServiceStateTracker.this.mPhone.setSystemProperty("ril.fakeDispCanceled", "true");
                IRCdmaServiceStateTracker.this.setFakeDispCancelToCP();
                IRCdmaServiceStateTracker.this.updateSpnDisplay();
                IRCdmaServiceStateTracker.this.stopFakeDispCancelTimer();
            } else if ("com.samsung.intent.action.SlotSwitched".equals(action)) {
                IRCdmaServiceStateTracker.this.SlotSwitched();
            } else {
                Rlog.w(IRCdmaServiceStateTracker.LOG_TAG, "IRCDMASST received unexpected Intent: " + action);
            }
        }
    }

    public IRCdmaServiceStateTracker(CDMAPhone phone) {
        super(phone);
        IntentFilter irFilter = new IntentFilter();
        irFilter.addAction("android.intent.action.ACTION_GLOBAL_NOSVC_CHK_TIMER_EXPIRED_CDMA");
        irFilter.addAction("android.intent.action.ACTION_GLOBAL_NETWORK_SEARCH_TIMER_EXPIRED_INTERNAL_CDMA");
        irFilter.addAction("android.intent.action.ACTION_GLOBAL_PWR_SAVE_MODE_ENTER_TIMER_EXPIRED");
        irFilter.addAction("android.intent.action.ACTION_GLOBAL_PWR_SAVE_MODE_STAY_TIMER_EXPIRED");
        irFilter.addAction("android.intent.action.SCREEN_ON");
        irFilter.addAction("android.intent.action.SCREEN_OFF");
        irFilter.addAction("android.intent.action.ACTION_SIMCARDMANAGER_LAUNCH_TIMER_EXPIRED");
        irFilter.addAction("PRL_GETTING_RETRY_TIMER");
        irFilter.addAction("android.intent.action.ACTION_GLOBAL_NET_SWITCH_SWITCH_BACK_TO_GSM_IN_HONGKONG");
        irFilter.addAction("android.intent.action.ACTION_SIMCARDMANAGER_LAUNCH_RESP");
        irFilter.addAction("SEND_BACKGROUND_SWITCHING");
        irFilter.addAction("android.intent.action.ACTION_GLOBAL_NET_SWITCH_SWITCH_BACK_TO_CDMA_IN_CHINA");
        irFilter.addAction("android.intent.action.ACTION_GLOBAL_NET_SWITCH_PENDING");
        irFilter.addAction("android.intent.action.ACTION_GLOBAL_NET_SWITCH_PENDING_TIMER_EXPIRED");
        irFilter.addAction("ACTION_DUALMODE_SETTING");
        irFilter.addAction("android.intent.action.ACTION_FAKE_DISP_CANCEL_TIMER");
        irFilter.addAction("com.samsung.intent.action.SlotSwitched");
        phone.getContext().registerReceiver(this.mIrIntentReceiver, irFilter);
        this.mCi.registerForRilConnected(this, 62, null);
    }

    public void dispose() {
        log("IRCdmaServiceStateTracker dispose");
        mCdmaInSvc = false;
        stopPendingIntentTimer();
        stopGlobalNoSvcChkTimer();
        stopGlobalNetworkSearchTimer();
        super.dispose();
        this.mPhone.getContext().unregisterReceiver(this.mIrIntentReceiver);
    }

    protected void finalize() {
        log("IRCdmaServiceStateTracker finalized");
    }

    public void handleMessage(Message msg) {
        if (this.mPhone.mIsTheCurrentActivePhone) {
            switch (msg.what) {
                case 50:
                    log("EVENT_REQUEST_DISCONNECT_DC");
                    powerOffRadioSafely(this.mPhone.mDcTracker);
                    return;
                case 62:
                    startSimCardMngLaunchTimer(this.mPhone);
                    return;
                case 74:
                    if (!"DCGGS".equals("DGG") && !"DCGS".equals("DGG")) {
                        return;
                    }
                    if ("1".equals(getSlotSelectionInformation())) {
                        Rlog.d(LOG_TAG, "[Global mode] Already switched. Ignore EVENT_FIRST_CDMA_NET_SRCH_TIMER.");
                        return;
                    } else if (isGlobalMode(this.mPhone) && globalNoSvcChkTimerRequired(this.mPhone) && !isChinaAreas()) {
                        sendNetChangeIntent(false);
                        incNetSrchCnt(1);
                        alreadyExpired = false;
                        this.mPhone.setSystemProperty("ril.mIsSwitchedToCdma", "false");
                        return;
                    } else {
                        Rlog.d(LOG_TAG, "[Global mode] Ignore EVENT_FIRST_CDMA_NET_SRCH_TIMER.");
                        if (!isGlobalMode(this.mPhone) && !isChinaAreas() && isSlot1CdmaActive() && isSlot1DualCard()) {
                            Rlog.d(LOG_TAG, "[Global mode] Caused by isGlobalMode is false. Start cdma srch timer again.");
                            startGlobalNetworkSearchTimer();
                            return;
                        }
                        return;
                    }
                default:
                    super.handleMessage(msg);
                    return;
            }
        }
        loge("Received message " + msg + "[" + msg.what + "]" + " while being destroyed. Ignoring.");
    }

    protected void setPowerStateToDesired() {
        if (this.mDesiredPowerState && this.mCi.getRadioState() == RadioState.RADIO_OFF) {
            this.mCi.setRadioPower(true, null);
            if (("DCGGS".equals("DGG") || "DCGS".equals("DGG")) && isCdmaManSel(this.mPhone)) {
                setFakeDispCancelToCP();
            }
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
            boolean showPlmn = plmn != null;
            if (("DCGGS".equals("DGG") || "DCGS".equals("DGG")) && this.mSS.getState() == 0 && "false".equals(getSystemProperty("ril.fakeDispCanceled", "false"))) {
                log("Hide plmn");
                plmn = "--";
            }
            log("updateSpnDisplay: changed sending intent, showPlmn=" + showPlmn + ", plmn=" + plmn);
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
        if ("DCGGS".equals("DGG") || "DCGS".equals("DGG")) {
            if (this.mSS.getState() == 0) {
                mCdmaInSvc = true;
            } else {
                mCdmaInSvc = false;
            }
        }
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
            if (this.mCi.getRadioState().isOn() && !this.mIsSubscriptionFromRuim) {
                String eriText;
                if (this.mSS.getVoiceRegState() == 0) {
                    eriText = this.mPhone.getCdmaEriText();
                } else {
                    eriText = this.mPhone.getContext().getText(17039570).toString();
                }
                this.mSS.setOperatorAlphaLong(eriText);
            }
            this.mPhone.setSystemProperty("gsm.operator.alpha", this.mSS.getOperatorAlphaLong());
            String prevOperatorNumeric = SystemProperties.get("gsm.operator.numeric", "");
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
        if ("DCGGS".equals("DGG") || "DCGS".equals("DGG")) {
            Intent intent;
            if (hasChanged || !CdmaServiceStateTracker.mFirstCdmaNoSvcChkTimerStarted || CdmaServiceStateTracker.mNetSrchTimerRunning) {
                boolean globalmode = isGlobalMode(this.mPhone);
                Rlog.d(LOG_TAG, "[Global mode] globalmode = " + globalmode + " mCurrentSrchNet:" + mCurrentSrchNet);
                if (globalmode) {
                    if (this.mSS.getState() == 0 || (this.mSS.getDataRegState() == 0 && "true".equals(getSystemProperty("ril.fakeDispCanceled", "false")) && mCurrentSrchNet != 2)) {
                        Rlog.d(LOG_TAG, "[Global mode] After global mode selected, cdma svc acquired. mNoSvcChkTimerRunning = " + CdmaServiceStateTracker.mNoSvcChkTimerRunning + " mNetSrchTimerRunning = " + CdmaServiceStateTracker.mNetSrchTimerRunning);
                        if (CdmaServiceStateTracker.mNoSvcChkTimerRunning || CdmaServiceStateTracker.mNetSrchTimerRunning) {
                            stopGlobalNetworkSearchTimer();
                            Rlog.d(LOG_TAG, "[Global mode] Send cdma acquisition noti!");
                            intent = new Intent("android.intent.action.ACTION_GLOBAL_MODE_NETWORK_ACQUIRED");
                            intent.addFlags(536870912);
                            intent.putExtra("acuiredNetwork", false);
                            this.mPhone.getContext().sendStickyBroadcast(intent);
                            stopGlobalNoSvcChkTimer();
                        }
                    } else {
                        if (globalNoSvcChkTimerRequired(this.mPhone)) {
                            startGlobalNoSvcChkTimer();
                        }
                    }
                }
            }
            if (hasChanged) {
                boolean isSlot1CdmaActive = isSlot1CdmaActive();
                int currCdmaMccInt = getCurrCdmaMcc();
                int prevCdmaMcc = Integer.parseInt(getSystemProperty("gsm.ctc.timedispschmmcc", "0"));
                Rlog.d(LOG_TAG, "[SimCardMngLaunch] currCdmaMccInt : " + currCdmaMccInt + " currGsmMccInt : " + currGsmMccInt + " mSimCardMngLnchTimerRunning : " + mSimCardMngLnchTimerRunning + " mSimCardMngEverLaunched : " + mSimCardMngEverLaunched + " isSlot1CdmaActive : " + isSlot1CdmaActive);
                if (currCdmaMccInt == 460 || currCdmaMccInt == 455 || currCdmaMccInt == 450) {
                    Rlog.d(LOG_TAG, "[Global mode] set fakeDispCanceled to true");
                    this.mPhone.setSystemProperty("ril.fakeDispCanceled", "true");
                } else if (this.mSS.getState() == 0 && currCdmaMccInt > 0 && prevCdmaMcc != currCdmaMccInt) {
                    Rlog.d(LOG_TAG, "[Global mode] set fakeDispCanceled to false");
                    this.mPhone.setSystemProperty("ril.fakeDispCanceled", "false");
                }
                if (currCdmaMccInt > 0 || currGsmMccInt > 0) {
                    Rlog.d(LOG_TAG, "[SimCardMngLaunch] succeed in getting mcc from network during SimCardMngLnchTimerRunning.");
                    intent = new Intent("android.intent.action.ACTION_SIMCARDMANAGER_LAUNCH");
                    intent.addFlags(536870912);
                    if (currCdmaMccInt == 460 || currCdmaMccInt == 455 || currGsmMccInt == 460 || currGsmMccInt == 455) {
                        intent.putExtra("china_mainland", true);
                        this.mPhone.setSystemProperty("gsm.ctc.chinamainland", "true");
                        this.mPhone.setSystemProperty("gsm.ctc.cdmaprefcountry", "false");
                        this.mPhone.setSystemProperty("ril.mIsSwitchedToCdma", "false");
                        Rlog.d(LOG_TAG, "[global mode] China/Macau. CDMA_MANUAL_SELECTED reset.");
                        System.putInt(this.mPhone.getContext().getContentResolver(), "CDMA_MANUAL_SELECTED", 0);
                    } else if (isCdmaPrefAreas(currCdmaMccInt) || isCdmaPrefAreas(currGsmMccInt)) {
                        intent.putExtra("china_mainland", false);
                        this.mPhone.setSystemProperty("gsm.ctc.chinamainland", "false");
                        this.mPhone.setSystemProperty("gsm.ctc.cdmaprefcountry", "true");
                    } else {
                        intent.putExtra("china_mainland", false);
                        this.mPhone.setSystemProperty("gsm.ctc.chinamainland", "false");
                        this.mPhone.setSystemProperty("gsm.ctc.cdmaprefcountry", "false");
                    }
                    Rlog.d(LOG_TAG, "[SimCardMngLaunch] china_mainland : " + getSystemProperty("gsm.ctc.chinamainland", "") + " cdmaPrefCountry : " + getSystemProperty("gsm.ctc.cdmaprefcountry", ""));
                    if (mSimCardMngLnchTimerRunning && !mSimCardMngEverLaunched) {
                        Rlog.d(LOG_TAG, "[SimCardMngLaunch] ACTION_SIMCARDMANAGER_LAUNCH sent");
                        this.mPhone.getContext().sendStickyBroadcast(intent);
                        stopSimCardMngLaunchTimer(this.mPhone);
                        mSimCardMngEverLaunched = true;
                    }
                    String mHasEverSwitchedToGsm = getSystemProperty("ril.mHasEverSwitchedToGsm", "false");
                    Rlog.d(LOG_TAG, "[global mode] currCdmaMccInt : " + currCdmaMccInt + " prevCdmaMcc : " + prevCdmaMcc);
                    if ("true".equals(mHasEverSwitchedToGsm)) {
                        if ("2012".equals(CscFeature.getInstance().getString("CscFeature_RIL_ConfigSpecForCtcMtrIR")) && "DCGGS".equals("DGG")) {
                            if (isDualSlotActive()) {
                                Rlog.d(LOG_TAG, "[Global Mode] Dual SlotActive ");
                                if (currCdmaMccInt > 0 && currCdmaMccInt != 454 && currGsmMccInt > 0 && currGsmMccInt != 454) {
                                    Rlog.d(LOG_TAG, "[Global Mode] mHasEverSwitchedToGsm reset to false, currCdmaMccInt: " + currCdmaMccInt + " currGsmMccInt: " + currGsmMccInt);
                                    this.mPhone.setSystemProperty("ril.mHasEverSwitchedToGsm", "false");
                                }
                            } else if (currCdmaMccInt > 0 && currCdmaMccInt != 454) {
                                Rlog.d(LOG_TAG, "[Global Mode] mHasEverSwitchedToGsm reset to false, currCdmaMccInt: " + currCdmaMccInt);
                                this.mPhone.setSystemProperty("ril.mHasEverSwitchedToGsm", "false");
                            }
                        } else if (isDualSlotActive()) {
                            if (((currCdmaMccInt > 0 && currCdmaMccInt != prevCdmaMcc && currGsmMccInt > 0 && currGsmMccInt != prevGsmMccInt) || ((currCdmaMccInt == 460 && currGsmMccInt == 460) || (currCdmaMccInt == 455 && currGsmMccInt == 455))) && !(isUsAreas(prevCdmaMcc) && isUsAreas(currCdmaMccInt))) {
                                Rlog.d(LOG_TAG, "[Global Mode] mHasEverSwitchedToGsm reset to false");
                                this.mPhone.setSystemProperty("ril.mHasEverSwitchedToGsm", "false");
                            }
                        } else if (((currCdmaMccInt > 0 && currCdmaMccInt != prevCdmaMcc) || currCdmaMccInt == 460 || currCdmaMccInt == 455) && !(isUsAreas(prevCdmaMcc) && isUsAreas(currCdmaMccInt))) {
                            Rlog.d(LOG_TAG, "[Global Mode] mHasEverSwitchedToGsm reset to false");
                            this.mPhone.setSystemProperty("ril.mHasEverSwitchedToGsm", "false");
                        }
                    }
                    if (this.mSS.getState() == 0) {
                        if (!(currCdmaMccInt == 460 || currCdmaMccInt == 455 || prevCdmaMcc == currCdmaMccInt || currCdmaMccInt == 0 || mHasTimeDispPopupDispd || !"true".equals(getSystemProperty("ril.fakeDispCanceled", "false")))) {
                            this.mPhone.getContext().sendStickyBroadcast(new Intent("android.intent.action.ACTION_TIME_DISP_SCHM_LAUNCH"));
                            mHasTimeDispPopupDispd = true;
                        }
                        if (currCdmaMccInt > 0 && prevCdmaMcc != currCdmaMccInt) {
                            this.mPhone.setSystemProperty("gsm.ctc.timedispschmmcc", Integer.toString(currCdmaMccInt));
                            if (prevCdmaMcc != 0) {
                                Rlog.d(LOG_TAG, "[global mode] mHasTimeDispPopupDispd reset.");
                                mHasTimeDispPopupDispd = false;
                            }
                        }
                    }
                } else if (this.mSS.getState() != 0) {
                    Rlog.d(LOG_TAG, "[SimCardMngLaunch] china_mainland reset to false.");
                    this.mPhone.setSystemProperty("gsm.ctc.chinamainland", "false");
                    this.mPhone.setSystemProperty("gsm.ctc.cdmaprefcountry", "false");
                }
                switchToGsmInCdmaRoamingArea(false);
            }
        }
    }

    protected void log(String s) {
        Rlog.d(LOG_TAG, "[CdmaSST] " + s);
    }

    protected void loge(String s) {
        Rlog.e(LOG_TAG, "[CdmaSST] " + s);
    }

    public void sendNoServiceNotiIntent() {
        if ("DCGGS".equals("DGG") || "DCGS".equals("DGG")) {
            Intent intentFwd = new Intent("android.intent.action.ACTION_GLOBAL_NO_SERVICE_NOTIFICATION");
            intentFwd.addFlags(536870912);
            this.mPhone.getContext().sendStickyBroadcast(intentFwd);
        }
    }

    public void sendNetChangeIntent(boolean changeToCdma) {
        if ("DCGGS".equals("DGG") || "DCGS".equals("DGG")) {
            if (!(mSleepPendedWhileNetSrchCdma || this.mWakeLock.isHeld())) {
                this.mWakeLock.acquire();
                sendMessageDelayed(obtainMessage(70), 3000);
                mSleepPendedWhileNetSrchCdma = true;
                Rlog.d(LOG_TAG, "[Global mode] Sleep pended while processing cdma net srch intent.");
            }
            Intent intentFwd = new Intent("android.intent.action.ACTION_GLOBAL_NETWORK_SEARCH_TIMER_EXPIRED");
            intentFwd.addFlags(536870912);
            if (changeToCdma) {
                intentFwd.putExtra("globalmodetype", true);
            } else {
                intentFwd.putExtra("globalmodetype", false);
            }
            if (isSlot2GsmInSvc()) {
                intentFwd.putExtra("isSlot2GsmInSvc", true);
            }
            this.mPhone.getContext().sendStickyBroadcast(intentFwd);
            Rlog.d(LOG_TAG, "[Global mode] ACTION_GLOBAL_NETWORK_SEARCH_TIMER_EXPIRED sent");
        }
    }

    public int getCurrCdmaMcc() {
        int currCdmaMccInt = 0;
        String currCdmaOprtNum = getSystemProperty("gsm.operator.numeric", "");
        if (currCdmaOprtNum.length() >= 5) {
            currCdmaMccInt = Integer.parseInt(currCdmaOprtNum.substring(0, 3));
        }
        return (!"DCGGS".equals("DGG") || "0".equals(getSlotSelectionInformation())) ? currCdmaMccInt : 0;
    }

    public int getCurrCdmaMnc() {
        int currCdmaMncInt = 0;
        String currCdmaOprtNum = getSystemProperty("gsm.operator.numeric", "");
        if (currCdmaOprtNum.length() >= 5) {
            currCdmaMncInt = Integer.parseInt(currCdmaOprtNum.substring(3, 5));
        }
        if (!"DCGGS".equals("DGG") || "0".equals(getSlotSelectionInformation())) {
            return currCdmaMncInt;
        }
        return 0;
    }

    public boolean isFirstCdmaNoSvcChkTimerStarted() {
        return CdmaServiceStateTracker.mFirstCdmaNoSvcChkTimerStarted;
    }

    public boolean isNetSrchTimerRunning() {
        return CdmaServiceStateTracker.mNetSrchTimerRunning;
    }

    public boolean isNoSvcChkTimerRunning() {
        return CdmaServiceStateTracker.mNoSvcChkTimerRunning;
    }

    public void startGlobalNoSvcChkTimer() {
        if (!"DCGGS".equals("DGG") && !"DCGS".equals("DGG")) {
            return;
        }
        if (CdmaServiceStateTracker.mNetSrchTimerRunning || CdmaServiceStateTracker.mNoSvcChkTimerRunning || CdmaServiceStateTracker.mPendingIntentTimerRunning) {
            Rlog.d(LOG_TAG, "[Global mode] Prev Timer running - mNetSrchTimerRunning: " + CdmaServiceStateTracker.mNetSrchTimerRunning + " mNoSvcChkTimerRunning: " + CdmaServiceStateTracker.mNoSvcChkTimerRunning + " Do not start timer");
            return;
        }
        Rlog.d(LOG_TAG, "[Global mode] CDMA startGlobalNoSvcChkTimer Start!!!");
        AlarmManager am = (AlarmManager) this.mPhone.getContext().getSystemService("alarm");
        this.sender_NoSvcChkTimer = PendingIntent.getBroadcast(this.mPhone.getContext(), 0, new Intent("android.intent.action.ACTION_GLOBAL_NOSVC_CHK_TIMER_EXPIRED_CDMA"), 0);
        am.set(2, SystemClock.elapsedRealtime() + 5000, this.sender_NoSvcChkTimer);
        CdmaServiceStateTracker.mNoSvcChkTimerRunning = true;
        if (!CdmaServiceStateTracker.mFirstCdmaNoSvcChkTimerStarted) {
            CdmaServiceStateTracker.mFirstCdmaNoSvcChkTimerStarted = true;
        }
    }

    public void stopGlobalNoSvcChkTimer() {
        if ("DCGGS".equals("DGG") || "DCGS".equals("DGG")) {
            Rlog.d(LOG_TAG, "[Global mode] CDMA stopGlobalNoSvcChkTimer!!! ");
            ((AlarmManager) this.mPhone.getContext().getSystemService("alarm")).cancel(this.sender_NoSvcChkTimer);
            CdmaServiceStateTracker.mNoSvcChkTimerRunning = false;
        }
    }

    public boolean switchToGsmInCdmaRoamingArea(boolean isPendedProcess) {
        boolean result = false;
        if ("DCGGS".equals("DGG") || "DCGS".equals("DGG")) {
            boolean isSlot1CdmaActive = isSlot1CdmaActive();
            boolean isCdmaManSel = isCdmaManSel(this.mPhone);
            boolean shouldBeSwitched = false;
            int currCdmaMccInt = getCurrCdmaMcc();
            int currCdmaMncInt = getCurrCdmaMnc();
            String mHasEverSwitchedToGsm = getSystemProperty("ril.mHasEverSwitchedToGsm", "false");
            String m2ndNetSelCnfWaiting = getSystemProperty("ril.m2ndNetSelCnfWaiting", "false");
            Rlog.d(LOG_TAG, "[global mode] switchToGsmInCdmaRoamingArea() isSlot1CdmaActive:" + isSlot1CdmaActive + ", mCdmaInSvc: " + mCdmaInSvc + ", currCdmaMccInt:" + currCdmaMccInt);
            Rlog.d(LOG_TAG, "[global mode] mHasEverSwitchedToGsm:" + mHasEverSwitchedToGsm + ", isCdmaManSel: " + isCdmaManSel + ", isPendedProcess: " + isPendedProcess);
            if (!isGlobalMode(this.mPhone)) {
                Rlog.d(LOG_TAG, "[global mode] Not Global mode. Exit!");
                return 0;
            } else if ("DCGGS".equals("DGG") && isCdmaManSel && "2012".equals(CscFeature.getInstance().getString("CscFeature_RIL_ConfigSpecForCtcMtrIR"))) {
                Rlog.d(LOG_TAG, "[global mode] CDMA man. selected before pwr up. Exit!");
                return 0;
            } else if (isFactoryMode(this.mPhone)) {
                Rlog.d(LOG_TAG, "[global mode] Factory card. Exit!");
                return 0;
            } else if (!"true".equals(m2ndNetSelCnfWaiting) || isPendedProcess) {
                if (!"2012".equals(CscFeature.getInstance().getString("CscFeature_RIL_ConfigSpecForCtcMtrIR")) || !"DCGGS".equals("DGG")) {
                    shouldBeSwitched = true;
                } else if (currCdmaMccInt == 454 && currCdmaMncInt == 29) {
                    Rlog.d(LOG_TAG, "[global mode] HK PCCW cdma network acquired. Switch to gsm right here.");
                    shouldBeSwitched = true;
                }
                if (isSlot1CdmaActive && mCdmaInSvc && currCdmaMccInt != 0 && currCdmaMccInt != 460 && currCdmaMccInt != 455 && currCdmaMccInt != 450 && "false".equals(mHasEverSwitchedToGsm) && shouldBeSwitched) {
                    if (mPendingIntentTimerRunning) {
                        stopPendingIntentTimer();
                    }
                    if (!(mSleepPendedWhileNetSrchCdma || this.mWakeLock.isHeld())) {
                        this.mWakeLock.acquire();
                        sendMessageDelayed(obtainMessage(70), 3000);
                        mSleepPendedWhileNetSrchCdma = true;
                    }
                    Intent intentFwd = new Intent("android.intent.action.ACTION_GLOBAL_NETWORK_SEARCH_TIMER_EXPIRED");
                    intentFwd.addFlags(536870912);
                    intentFwd.putExtra("globalmodetype", false);
                    intentFwd.putExtra("switchToGsmInCdmaRoamingArea", true);
                    this.mPhone.getContext().sendStickyBroadcast(intentFwd);
                    Rlog.d(LOG_TAG, "[Global mode] ACTION_GLOBAL_NETWORK_SEARCH_TIMER_EXPIRED w/ switchToGsmInCdmaRoamingArea sent");
                    this.mPhone.setSystemProperty("ril.m2ndNetSelCnfWaiting", "true");
                    result = true;
                }
            } else {
                Rlog.d(LOG_TAG, "[global mode] m2ndNetSelCnfWaiting. Exit!");
                return 0;
            }
        }
        return result;
    }

    public void setFakeDispCancelToCP() {
        Rlog.d(LOG_TAG, "[Global mode] set fakeDispCanceled to cp");
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            DataOutputStream dos = new DataOutputStream(bos);
            dos.writeByte(81);
            dos.writeByte(7);
            dos.writeShort(4);
            this.mPhone.invokeOemRilRequestRaw(bos.toByteArray(), null);
        } catch (IOException e) {
            Rlog.d(LOG_TAG, "Error in set fakeDispCanceled to cp, exception is :" + e);
        }
    }

    public void startFakeDispCancelTimer() {
        if ("DCGGS".equals("DGG") || "DCGS".equals("DGG")) {
            Rlog.d(LOG_TAG, "[Global mode] CDMA startFakeDispCancelTimer Start!!!");
            AlarmManager am = (AlarmManager) this.mPhone.getContext().getSystemService("alarm");
            this.sender_FakeDispCancelTimer = PendingIntent.getBroadcast(this.mPhone.getContext(), 0, new Intent("android.intent.action.ACTION_FAKE_DISP_CANCEL_TIMER"), 0);
            am.set(2, SystemClock.elapsedRealtime() + 90000, this.sender_FakeDispCancelTimer);
        }
    }

    public void startPendingIntentTimer(String pendedMode, boolean isSwitchToGsmInCdmaRoamingArea) {
        if ("DCGGS".equals("DGG") || "DCGS".equals("DGG")) {
            if (Global.getInt(this.mCr, "airplane_mode_on", 0) == 1) {
                Rlog.d(LOG_TAG, "[Global mode] Now airplane mode on. Do not start cdma pending intent timer");
            } else if (!isSwitchToGsmInCdmaRoamingArea || mCdmaInSvc) {
                if (CdmaServiceStateTracker.mPendingIntentTimerRunning) {
                    stopPendingIntentTimer();
                }
                Rlog.d(LOG_TAG, "[Global mode] CDMA startPendingIntentTimer pendedMode: " + pendedMode + " isSwitchToGsmInCdmaRoamingArea: " + isSwitchToGsmInCdmaRoamingArea);
                AlarmManager am = (AlarmManager) this.mPhone.getContext().getSystemService("alarm");
                Intent intent = new Intent("android.intent.action.ACTION_GLOBAL_NET_SWITCH_PENDING_TIMER_EXPIRED");
                if (pendedMode != null) {
                    intent.putExtra("pendedMode", pendedMode);
                }
                if (isSwitchToGsmInCdmaRoamingArea) {
                    intent.putExtra("isSwitchToGsmInCdmaRoamingArea", true);
                }
                this.sender_PendingIntentTimer = PendingIntent.getBroadcast(this.mPhone.getContext(), 0, intent, 268435456);
                am.set(2, SystemClock.elapsedRealtime() + 10000, this.sender_PendingIntentTimer);
                CdmaServiceStateTracker.mPendingIntentTimerRunning = true;
            } else {
                Rlog.d(LOG_TAG, "[Global mode] cdma no svc. Do not start cdma pending intent timer");
            }
        }
    }

    public void stopPendingIntentTimer() {
        if ("DCGGS".equals("DGG") || "DCGS".equals("DGG")) {
            Rlog.d(LOG_TAG, "[Global mode] CDMA stopPendingIntentTimer!!! ");
            ((AlarmManager) this.mPhone.getContext().getSystemService("alarm")).cancel(this.sender_PendingIntentTimer);
            CdmaServiceStateTracker.mPendingIntentTimerRunning = false;
        }
    }

    public void stopFakeDispCancelTimer() {
        if ("DCGGS".equals("DGG") || "DCGS".equals("DGG")) {
            Rlog.d(LOG_TAG, "[Global mode] CDMA stopFakeDispCancelTimer!!! ");
            ((AlarmManager) this.mPhone.getContext().getSystemService("alarm")).cancel(this.sender_FakeDispCancelTimer);
        }
    }

    public void SlotSwitched() {
        Rlog.d(LOG_TAG, "CdmaServiceStateTracker - SlotSwitched");
        this.mDesiredPowerState = true;
        this.countCheckDataStateReads = 151;
        sendMessage(obtainMessage(50));
    }

    public void startGlobalNetworkSearchTimer() {
        if ("DCGGS".equals("DGG") || "DCGS".equals("DGG")) {
            boolean isAirplaneMode = Global.getInt(this.mCr, "airplane_mode_on", 0) == 1;
            stopGlobalNoSvcChkTimer();
            if (isAirplaneMode) {
                Rlog.d(LOG_TAG, "[Global mode] Now airplane mode on. Do not start cdma net srch timer");
            } else if (mCdmaInSvc || isPwrSaveModeTimerRunning() || CdmaServiceStateTracker.mPendingIntentTimerRunning) {
                Rlog.d(LOG_TAG, "[global mode] Do not start cdma net srch timer:: mCdmaInSvc:" + mCdmaInSvc + " isPwrSaveModeTimerRunning:" + isPwrSaveModeTimerRunning() + " mPendingIntentTimerRunning:" + CdmaServiceStateTracker.mPendingIntentTimerRunning);
            } else if (this.mSS.getDataRegState() == 0 && "true".equals(getSystemProperty("ril.fakeDispCanceled", "false"))) {
                Rlog.d(LOG_TAG, "[global mode] Do not start cdma net srch timer:: Data is still in service");
            } else {
                Rlog.d(LOG_TAG, "[Global mode] CDMA startGlobalNetworkSearchTimer!!!");
                AlarmManager am = (AlarmManager) this.mPhone.getContext().getSystemService("alarm");
                this.sender_NetSrchTimer = PendingIntent.getBroadcast(this.mPhone.getContext(), 0, new Intent("android.intent.action.ACTION_GLOBAL_NETWORK_SEARCH_TIMER_EXPIRED_INTERNAL_CDMA"), 0);
                am.set(2, SystemClock.elapsedRealtime() + 45000, this.sender_NetSrchTimer);
                stopGlobalNoSvcChkTimer();
                CdmaServiceStateTracker.mNetSrchTimerRunning = true;
                mCurrentSrchNet = 1;
                IsDispdSwitchToGsm = false;
            }
        }
    }

    public void startGlobalNetworkSearchTimer(boolean needShortSrch) {
        boolean isAirplaneMode = false;
        if ("DCGGS".equals("DGG") && needShortSrch) {
            if (Global.getInt(this.mCr, "airplane_mode_on", 0) == 1) {
                isAirplaneMode = true;
            }
            stopGlobalNoSvcChkTimer();
            if (isAirplaneMode) {
                Rlog.d(LOG_TAG, "[Global mode] Now airplane mode on. Do not start cdma net srch timer");
                return;
            } else if (mCdmaInSvc || isPwrSaveModeTimerRunning() || CdmaServiceStateTracker.mPendingIntentTimerRunning) {
                Rlog.d(LOG_TAG, "[global mode] Do not start cdma net srch timer:: mCdmaInSvc:" + mCdmaInSvc + " isPwrSaveModeTimerRunning:" + isPwrSaveModeTimerRunning() + " mPendingIntentTimerRunning:" + CdmaServiceStateTracker.mPendingIntentTimerRunning);
                return;
            } else {
                Rlog.d(LOG_TAG, "[Global mode] First cdma net short srch!!!");
                sendMessageDelayed(obtainMessage(74), 10000);
                CdmaServiceStateTracker.mNetSrchTimerRunning = true;
                mCurrentSrchNet = 1;
                if (!isFirstCdmaNoSvcChkTimerStarted()) {
                    CdmaServiceStateTracker.mFirstCdmaNoSvcChkTimerStarted = true;
                    return;
                }
                return;
            }
        }
        startGlobalNetworkSearchTimer();
    }

    public void stopGlobalNetworkSearchTimer() {
        if ("DCGGS".equals("DGG") || "DCGS".equals("DGG")) {
            Rlog.d(LOG_TAG, "[Global mode] CDMA stopGlobalNetworkSearchTimer!!! ");
            ((AlarmManager) this.mPhone.getContext().getSystemService("alarm")).cancel(this.sender_NetSrchTimer);
            CdmaServiceStateTracker.mNetSrchTimerRunning = false;
            mCurrentSrchNet = 0;
        }
    }

    public boolean isSlot2GsmInSvc() {
        boolean gsmActive;
        if (System.getInt(this.mPhone.getContext().getContentResolver(), "GSM_ACTIVATE", 0) == 1) {
            gsmActive = true;
        } else {
            gsmActive = false;
        }
        boolean isSlotSwitched = getSlotSelectionInformation().equals("1");
        int gsmSs = PhoneFactory.getPhone(1).getServiceState().getState();
        Rlog.i(LOG_TAG, "[global mode] gsmActive : " + gsmActive + " isSlotSwitched : " + isSlotSwitched + " gsmSs : " + gsmSs);
        return gsmActive && !isSlotSwitched && gsmSs == 0;
    }
}
