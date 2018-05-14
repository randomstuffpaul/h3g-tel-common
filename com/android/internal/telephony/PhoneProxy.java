package com.android.internal.telephony;

import android.app.ActivityManagerNative;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.LinkProperties;
import android.net.NetworkCapabilities;
import android.os.AsyncResult;
import android.os.Handler;
import android.os.Message;
import android.os.SystemProperties;
import android.telephony.CellInfo;
import android.telephony.CellLocation;
import android.telephony.Rlog;
import android.telephony.ServiceState;
import android.telephony.SignalStrength;
import android.telephony.SubscriptionManager;
import com.android.internal.telephony.Phone.DataActivityState;
import com.android.internal.telephony.PhoneConstants.DataState;
import com.android.internal.telephony.PhoneConstants.State;
import com.android.internal.telephony.cat.CatService;
import com.android.internal.telephony.cdma.CDMALTEPhone;
import com.android.internal.telephony.cdma.CDMAPhone;
import com.android.internal.telephony.gsm.GSMPhone;
import com.android.internal.telephony.imsphone.ImsPhone;
import com.android.internal.telephony.test.SimulatedRadioControl;
import com.android.internal.telephony.uicc.IccCardProxy;
import com.android.internal.telephony.uicc.IccFileHandler;
import com.android.internal.telephony.uicc.IsimRecords;
import com.android.internal.telephony.uicc.UiccCard;
import com.android.internal.telephony.uicc.UsimServiceTable;
import com.samsung.android.telephony.MultiSimManager;
import java.util.List;
import java.util.Map;

public class PhoneProxy extends Handler implements Phone {
    private static final int EVENT_ENABLE_DATA_CONNECTION = 7;
    private static final int EVENT_FINISH_EFS_RESYNC = 11;
    private static final int EVENT_RADIO_ON = 2;
    private static final int EVENT_REQUEST_PREFERRED_NETWORK_TYPE_DONE = 6;
    private static final int EVENT_REQUEST_VOICE_RADIO_TECH_DONE = 3;
    private static final int EVENT_RIL_CONNECTED = 4;
    private static final int EVENT_SET_LTE_BAND_DONE = 10;
    private static final int EVENT_SET_LTE_MODE_PREF = 8;
    private static final int EVENT_SET_LTE_OFF_MODE_PREF = 9;
    private static final int EVENT_UPDATE_PHONE_OBJECT = 5;
    private static final int EVENT_VOICE_RADIO_TECH_CHANGED = 1;
    private static final String LOG_TAG = "PhoneProxy";
    static final String SECRET_CODE_ACTION = "android.provider.Telephony.SECRET_CODE";
    static final String SECRET_CODE_HOST_ADD_B1 = "147235981";
    static final String SECRET_CODE_HOST_SUB_B1 = "1472359810";
    static final String SECRET_CODE_SCHEME = "android_secret_code";
    public static final Object lockForRadioTechnologyChange = new Object();
    private Phone mActivePhone;
    private CommandsInterface mCommandsInterface;
    private IccCardProxy mIccCardProxy;
    private IccPhoneBookInterfaceManagerProxy mIccPhoneBookInterfaceManagerProxy;
    private IccSmsInterfaceManager mIccSmsInterfaceManager;
    private BroadcastReceiver mIntentReceiver = new C00231();
    private int mPhoneId = 0;
    private PhoneSubInfoProxy mPhoneSubInfoProxy;
    private boolean mResetModemOnRadioTechnologyChange = false;
    private int mRilVersion;

    class C00231 extends BroadcastReceiver {
        C00231() {
        }

        public void onReceive(Context context, Intent intent) {
            PhoneProxy.this.logd("Intent : " + intent.getAction());
            if (intent.getAction().equals("com.android.action.LTE_MODE_CHNAGE")) {
                if (PhoneProxy.this.mActivePhone.getPhoneId() == 0) {
                    Rlog.e(PhoneProxy.LOG_TAG, "disableDataConnectivity");
                    PhoneProxy.this.setInternalDataEnabled(false);
                    if (intent.getBooleanExtra("lte_mode", false)) {
                        PhoneProxy.this.sendMessageDelayed(PhoneProxy.this.obtainMessage(8), 2000);
                    } else {
                        PhoneProxy.this.sendMessageDelayed(PhoneProxy.this.obtainMessage(9), 2000);
                    }
                }
            } else if (PhoneProxy.SECRET_CODE_ACTION.equals(intent.getAction())) {
                String host = intent.getData().getHost();
                if (PhoneProxy.SECRET_CODE_HOST_ADD_B1.equals(host)) {
                    PhoneProxy.this.setLteBandMode(5, PhoneProxy.this.obtainMessage(10));
                } else if (PhoneProxy.SECRET_CODE_HOST_SUB_B1.equals(host)) {
                    PhoneProxy.this.setLteBandMode(4, PhoneProxy.this.obtainMessage(10));
                }
            }
        }
    }

    public PhoneProxy(PhoneBase phone) {
        this.mActivePhone = phone;
        this.mResetModemOnRadioTechnologyChange = SystemProperties.getBoolean("persist.radio.reset_on_switch", false);
        this.mIccPhoneBookInterfaceManagerProxy = new IccPhoneBookInterfaceManagerProxy(phone.getIccPhoneBookInterfaceManager());
        this.mPhoneSubInfoProxy = new PhoneSubInfoProxy(phone.getPhoneSubInfo());
        this.mCommandsInterface = ((PhoneBase) this.mActivePhone).mCi;
        this.mCommandsInterface.registerForRilConnected(this, 4, null);
        this.mCommandsInterface.registerForOn(this, 2, null);
        this.mCommandsInterface.registerForVoiceRadioTechChanged(this, 1, null);
        this.mPhoneId = phone.getPhoneId();
        this.mIccSmsInterfaceManager = new IccSmsInterfaceManager((PhoneBase) this.mActivePhone);
        this.mIccCardProxy = new IccCardProxy(this.mActivePhone.getContext(), this.mCommandsInterface, this.mActivePhone.getPhoneId(), phone);
        if (("CG".equals("DGG") || "DGG".equals("DGG") || "DCG".equals("DGG") || "DCGG".equals("DGG") || "DCGS".equals("DGG") || "DCGGS".equals("DGG")) && this.mActivePhone.getPhoneId() == 0) {
            logd("PhoneProxy mActivePhone.getPhoneId()= " + this.mActivePhone.getPhoneId() + " create TelephonyPropertiesEdit");
            TelephonyPropertiesEdit telephonyPropertiesEdit = new TelephonyPropertiesEdit(phone, phone.getContext());
        }
        if (phone.getPhoneType() == 1) {
            this.mIccCardProxy.setVoiceRadioTech(3);
        } else if (phone.getPhoneType() == 2) {
            this.mIccCardProxy.setVoiceRadioTech(6);
        }
        if ("CTC".equals(SystemProperties.get("ro.csc.sales_code")) && this.mActivePhone.getPhoneId() == 0) {
            IntentFilter filter = new IntentFilter();
            filter.addAction("com.android.action.LTE_MODE_CHNAGE");
            this.mActivePhone.getContext().registerReceiver(this.mIntentReceiver, filter);
            IntentFilter filter2 = new IntentFilter();
            filter2.addAction(SECRET_CODE_ACTION);
            filter2.addDataScheme(SECRET_CODE_SCHEME);
            filter2.addDataAuthority(SECRET_CODE_HOST_ADD_B1, null);
            filter2.addDataAuthority(SECRET_CODE_HOST_SUB_B1, null);
            this.mActivePhone.getContext().registerReceiver(this.mIntentReceiver, filter2);
        }
    }

    public void handleMessage(Message msg) {
        AsyncResult ar = msg.obj;
        switch (msg.what) {
            case 1:
            case 3:
                String what = msg.what == 1 ? "EVENT_VOICE_RADIO_TECH_CHANGED" : "EVENT_REQUEST_VOICE_RADIO_TECH_DONE";
                if (ar.exception == null) {
                    if (ar.result != null && ((int[]) ar.result).length != 0) {
                        int newVoiceTech = ((int[]) ar.result)[0];
                        logd(what + ": newVoiceTech=" + newVoiceTech);
                        phoneObjectUpdater(newVoiceTech);
                        break;
                    }
                    loge(what + ": has no tech!");
                    break;
                }
                loge(what + ": exception=" + ar.exception);
                break;
            case 2:
                this.mCommandsInterface.getVoiceRadioTechnology(obtainMessage(3));
                break;
            case 4:
                if (ar.exception == null && ar.result != null) {
                    this.mRilVersion = ((Integer) ar.result).intValue();
                    break;
                }
                logd("Unexpected exception on EVENT_RIL_CONNECTED");
                this.mRilVersion = -1;
                break;
                break;
            case 5:
                phoneObjectUpdater(msg.arg1);
                break;
            case 6:
                sendMessageDelayed(obtainMessage(7), 3000);
                break;
            case 7:
                Rlog.e(LOG_TAG, "enableDataConnectivity");
                setInternalDataEnabled(true);
                break;
            case 8:
                setPreferredNetworkType(10, obtainMessage(6));
                break;
            case 9:
                setPreferredNetworkType(7, obtainMessage(6));
                break;
            case 10:
                sendMessageDelayed(obtainMessage(11), 5000);
                break;
            case 11:
                Intent startIntent = new Intent("android.intent.action.ACTION_REQUEST_SHUTDOWN");
                startIntent.setAction("android.intent.action.REBOOT");
                startIntent.putExtra("android.intent.extra.KEY_CONFIRM", false);
                startIntent.setFlags(268435456);
                this.mActivePhone.getContext().startActivity(startIntent);
                break;
            default:
                loge("Error! This handler was not registered for this message type. Message: " + msg.what);
                break;
        }
        super.handleMessage(msg);
    }

    private void logd(String msg) {
        Rlog.d(MultiSimManager.appendSimSlot(LOG_TAG, this.mPhoneId), "[PhoneProxy] " + msg);
    }

    private void loge(String msg) {
        Rlog.e(MultiSimManager.appendSimSlot(LOG_TAG, this.mPhoneId), "[PhoneProxy] " + msg);
    }

    private void phoneObjectUpdater(int newVoiceRadioTech) {
        logd("phoneObjectUpdater: newVoiceRadioTech=" + newVoiceRadioTech);
        if (this.mActivePhone != null) {
            if (newVoiceRadioTech == 14 || newVoiceRadioTech == 0) {
                int volteReplacementRat = this.mActivePhone.getContext().getResources().getInteger(17694810);
                logd("phoneObjectUpdater: volteReplacementRat=" + volteReplacementRat);
                if (volteReplacementRat != 0) {
                    newVoiceRadioTech = volteReplacementRat;
                }
            }
            if (this.mRilVersion == 6 && getLteOnCdmaMode() == 1) {
                if (this.mActivePhone.getPhoneType() == 2) {
                    logd("phoneObjectUpdater: LTE ON CDMA property is set. Use CDMA Phone newVoiceRadioTech=" + newVoiceRadioTech + " mActivePhone=" + this.mActivePhone.getPhoneName());
                    return;
                } else {
                    logd("phoneObjectUpdater: LTE ON CDMA property is set. Switch to CDMALTEPhone newVoiceRadioTech=" + newVoiceRadioTech + " mActivePhone=" + this.mActivePhone.getPhoneName());
                    newVoiceRadioTech = 6;
                }
            } else if ((ServiceState.isCdma(newVoiceRadioTech) && this.mActivePhone.getPhoneType() == 2) || (ServiceState.isGsm(newVoiceRadioTech) && this.mActivePhone.getPhoneType() == 1)) {
                logd("phoneObjectUpdater: No change ignore, newVoiceRadioTech=" + newVoiceRadioTech + " mActivePhone=" + this.mActivePhone.getPhoneName());
                return;
            }
        }
        if (newVoiceRadioTech == 0) {
            logd("phoneObjectUpdater: Unknown rat ignore,  newVoiceRadioTech=Unknown. mActivePhone=" + this.mActivePhone.getPhoneName());
            return;
        }
        boolean oldPowerState = false;
        if (this.mResetModemOnRadioTechnologyChange && this.mCommandsInterface.getRadioState().isOn()) {
            oldPowerState = true;
            logd("phoneObjectUpdater: Setting Radio Power to Off");
            this.mCommandsInterface.setRadioPower(false, null);
        }
        deleteAndCreatePhone(newVoiceRadioTech);
        if (this.mResetModemOnRadioTechnologyChange && oldPowerState) {
            logd("phoneObjectUpdater: Resetting Radio");
            this.mCommandsInterface.setRadioPower(oldPowerState, null);
        }
        this.mIccSmsInterfaceManager.updatePhoneObject((PhoneBase) this.mActivePhone);
        this.mIccPhoneBookInterfaceManagerProxy.setmIccPhoneBookInterfaceManager(this.mActivePhone.getIccPhoneBookInterfaceManager());
        this.mPhoneSubInfoProxy.setmPhoneSubInfo(this.mActivePhone.getPhoneSubInfo());
        this.mCommandsInterface = ((PhoneBase) this.mActivePhone).mCi;
        this.mIccCardProxy.setVoiceRadioTech(newVoiceRadioTech);
        if ("CG".equals("DGG") || "DCG".equals("DGG") || "DCGG".equals("DGG") || "DCGS".equals("DGG") || "DCGGS".equals("DGG")) {
            String simselswitch = SystemProperties.get("gsm.sim.selectnetwork");
            boolean propNotSet = false;
            if (this.mActivePhone.getPhoneId() == 0 && (("CDMA".equals(simselswitch) && this.mActivePhone.getPhoneType() == 1) || ("GSM".equals(simselswitch) && this.mActivePhone.getPhoneType() == 2))) {
                logd("Critical case - PROPERTY_SIM_SELNETWORK(" + simselswitch + ") and phoneType(" + this.mActivePhone.getPhoneType() + ") mismatched!!!");
                propNotSet = true;
            }
            if (propNotSet) {
                if (this.mActivePhone.getPhoneType() == 1) {
                    SystemProperties.set("gsm.sim.selectnetwork", "GSM");
                } else {
                    SystemProperties.set("gsm.sim.selectnetwork", "CDMA");
                }
            }
        }
        Intent intent = new Intent("android.intent.action.RADIO_TECHNOLOGY");
        intent.addFlags(536870912);
        intent.putExtra("phoneName", this.mActivePhone.getPhoneName());
        SubscriptionManager.putPhoneIdAndSubIdExtra(intent, this.mPhoneId);
        ActivityManagerNative.broadcastStickyIntent(intent, null, -1);
    }

    private void deleteAndCreatePhone(int newVoiceRadioTech) {
        String outgoingPhoneName = "Unknown";
        Phone oldPhone = this.mActivePhone;
        ImsPhone imsPhone = null;
        if (oldPhone != null) {
            outgoingPhoneName = ((PhoneBase) oldPhone).getPhoneName();
        }
        logd("Switching Voice Phone : " + outgoingPhoneName + " >>> " + (ServiceState.isGsm(newVoiceRadioTech) ? "GSM" : "CDMA"));
        if (ServiceState.isCdma(newVoiceRadioTech)) {
            this.mActivePhone = PhoneFactory.getCdmaPhone(this.mPhoneId);
        } else if (ServiceState.isGsm(newVoiceRadioTech)) {
            this.mActivePhone = PhoneFactory.getGsmPhone(this.mPhoneId);
        }
        if ("KDI".equals("")) {
            if (!(this.mActivePhone == null || oldPhone == null)) {
                ((PhoneBase) this.mActivePhone).mEccNums = ((PhoneBase) oldPhone).mEccNums;
                logd("Copy mEccNums - oldPhone.mEccNums: " + ((PhoneBase) oldPhone).mEccNums + ", mActivePhone.mEccNums: " + ((PhoneBase) this.mActivePhone).mEccNums);
            }
            if (this.mActivePhone != null) {
                SystemProperties.set("gsm.network.type", ServiceState.rilRadioTechnologyToString(this.mActivePhone.getServiceState().getRilDataRadioTechnology()));
            }
        }
        if (oldPhone != null) {
            imsPhone = oldPhone.relinquishOwnershipOfImsPhone();
        }
        if (this.mActivePhone != null) {
            CallManager.getInstance().registerPhone(this.mActivePhone);
            if (imsPhone != null) {
                this.mActivePhone.acquireOwnershipOfImsPhone(imsPhone);
            }
        }
        if (oldPhone != null) {
            CallManager.getInstance().unregisterPhone(oldPhone);
            logd("Disposing old phone..");
            oldPhone.dispose();
        }
        if (("CG".equals("DGG") || "DCG".equals("DGG") || "DCGG".equals("DGG") || "DCGS".equals("DGG") || "DCGGS".equals("DGG")) && this.mActivePhone.getPhoneId() == 0) {
            if (ServiceState.isGsm(newVoiceRadioTech)) {
                SystemProperties.set("gsm.sim.selectnetwork", "GSM");
                ActivityManagerNative.broadcastStickyIntent(new Intent("com.samsung.intent.action.setCardDataInit"), null, -1);
            } else {
                SystemProperties.set("gsm.sim.selectnetwork", "CDMA");
                ActivityManagerNative.broadcastStickyIntent(new Intent("com.samsung.intent.action.Slot1setCardDataInit"), null, -1);
            }
        }
    }

    public IccSmsInterfaceManager getIccSmsInterfaceManager() {
        return this.mIccSmsInterfaceManager;
    }

    public PhoneSubInfoProxy getPhoneSubInfoProxy() {
        return this.mPhoneSubInfoProxy;
    }

    public IccPhoneBookInterfaceManagerProxy getIccPhoneBookInterfaceManagerProxy() {
        return this.mIccPhoneBookInterfaceManagerProxy;
    }

    public IccFileHandler getIccFileHandler() {
        return ((PhoneBase) this.mActivePhone).getIccFileHandler();
    }

    public void updatePhoneObject(int voiceRadioTech) {
        logd("updatePhoneObject: radioTechnology=" + voiceRadioTech);
        sendMessage(obtainMessage(5, voiceRadioTech, 0, null));
    }

    public ServiceState getServiceState() {
        return this.mActivePhone.getServiceState();
    }

    public CellLocation getCellLocation() {
        return this.mActivePhone.getCellLocation();
    }

    public List<CellInfo> getAllCellInfo() {
        return this.mActivePhone.getAllCellInfo();
    }

    public void setCellInfoListRate(int rateInMillis) {
        this.mActivePhone.setCellInfoListRate(rateInMillis);
    }

    public DataState getDataConnectionState() {
        return this.mActivePhone.getDataConnectionState("default");
    }

    public DataState getDataConnectionState(String apnType) {
        return this.mActivePhone.getDataConnectionState(apnType);
    }

    public DataActivityState getDataActivityState() {
        return this.mActivePhone.getDataActivityState();
    }

    public Context getContext() {
        return this.mActivePhone.getContext();
    }

    public void disableDnsCheck(boolean b) {
        this.mActivePhone.disableDnsCheck(b);
    }

    public boolean isDnsCheckDisabled() {
        return this.mActivePhone.isDnsCheckDisabled();
    }

    public State getState() {
        return this.mActivePhone.getState();
    }

    public String getPhoneName() {
        return this.mActivePhone.getPhoneName();
    }

    public int getPhoneType() {
        return this.mActivePhone.getPhoneType();
    }

    public String[] getActiveApnTypes() {
        return this.mActivePhone.getActiveApnTypes();
    }

    public String getActiveApnHost(String apnType) {
        return this.mActivePhone.getActiveApnHost(apnType);
    }

    public LinkProperties getLinkProperties(String apnType) {
        return this.mActivePhone.getLinkProperties(apnType);
    }

    public NetworkCapabilities getNetworkCapabilities(String apnType) {
        return this.mActivePhone.getNetworkCapabilities(apnType);
    }

    public SignalStrength getSignalStrength() {
        return this.mActivePhone.getSignalStrength();
    }

    public void registerForUnknownConnection(Handler h, int what, Object obj) {
        this.mActivePhone.registerForUnknownConnection(h, what, obj);
    }

    public void unregisterForUnknownConnection(Handler h) {
        this.mActivePhone.unregisterForUnknownConnection(h);
    }

    public void registerForHandoverStateChanged(Handler h, int what, Object obj) {
        this.mActivePhone.registerForHandoverStateChanged(h, what, obj);
    }

    public void unregisterForHandoverStateChanged(Handler h) {
        this.mActivePhone.unregisterForHandoverStateChanged(h);
    }

    public void registerForPreciseCallStateChanged(Handler h, int what, Object obj) {
        this.mActivePhone.registerForPreciseCallStateChanged(h, what, obj);
    }

    public void unregisterForPreciseCallStateChanged(Handler h) {
        this.mActivePhone.unregisterForPreciseCallStateChanged(h);
    }

    public void registerForNewRingingConnection(Handler h, int what, Object obj) {
        this.mActivePhone.registerForNewRingingConnection(h, what, obj);
    }

    public void unregisterForNewRingingConnection(Handler h) {
        this.mActivePhone.unregisterForNewRingingConnection(h);
    }

    public void registerForIncomingRing(Handler h, int what, Object obj) {
        this.mActivePhone.registerForIncomingRing(h, what, obj);
    }

    public void unregisterForIncomingRing(Handler h) {
        this.mActivePhone.unregisterForIncomingRing(h);
    }

    public void registerForDisconnect(Handler h, int what, Object obj) {
        this.mActivePhone.registerForDisconnect(h, what, obj);
    }

    public void unregisterForDisconnect(Handler h) {
        this.mActivePhone.unregisterForDisconnect(h);
    }

    public void registerForMmiInitiate(Handler h, int what, Object obj) {
        this.mActivePhone.registerForMmiInitiate(h, what, obj);
    }

    public void unregisterForMmiInitiate(Handler h) {
        this.mActivePhone.unregisterForMmiInitiate(h);
    }

    public void registerForMmiComplete(Handler h, int what, Object obj) {
        this.mActivePhone.registerForMmiComplete(h, what, obj);
    }

    public void unregisterForMmiComplete(Handler h) {
        this.mActivePhone.unregisterForMmiComplete(h);
    }

    public List<? extends MmiCode> getPendingMmiCodes() {
        return this.mActivePhone.getPendingMmiCodes();
    }

    public void sendUssdResponse(String ussdMessge) {
        this.mActivePhone.sendUssdResponse(ussdMessge);
    }

    public void registerForServiceStateChanged(Handler h, int what, Object obj) {
        this.mActivePhone.registerForServiceStateChanged(h, what, obj);
    }

    public void unregisterForServiceStateChanged(Handler h) {
        this.mActivePhone.unregisterForServiceStateChanged(h);
    }

    public void registerForSuppServiceNotification(Handler h, int what, Object obj) {
        this.mActivePhone.registerForSuppServiceNotification(h, what, obj);
    }

    public void unregisterForSuppServiceNotification(Handler h) {
        this.mActivePhone.unregisterForSuppServiceNotification(h);
    }

    public void registerForSuppServiceFailed(Handler h, int what, Object obj) {
        this.mActivePhone.registerForSuppServiceFailed(h, what, obj);
    }

    public void unregisterForSuppServiceFailed(Handler h) {
        this.mActivePhone.unregisterForSuppServiceFailed(h);
    }

    public void registerForInCallVoicePrivacyOn(Handler h, int what, Object obj) {
        this.mActivePhone.registerForInCallVoicePrivacyOn(h, what, obj);
    }

    public void unregisterForInCallVoicePrivacyOn(Handler h) {
        this.mActivePhone.unregisterForInCallVoicePrivacyOn(h);
    }

    public void registerForInCallVoicePrivacyOff(Handler h, int what, Object obj) {
        this.mActivePhone.registerForInCallVoicePrivacyOff(h, what, obj);
    }

    public void unregisterForInCallVoicePrivacyOff(Handler h) {
        this.mActivePhone.unregisterForInCallVoicePrivacyOff(h);
    }

    public void registerForCdmaOtaStatusChange(Handler h, int what, Object obj) {
        this.mActivePhone.registerForCdmaOtaStatusChange(h, what, obj);
    }

    public void unregisterForCdmaOtaStatusChange(Handler h) {
        this.mActivePhone.unregisterForCdmaOtaStatusChange(h);
    }

    public void registerForSubscriptionInfoReady(Handler h, int what, Object obj) {
        this.mActivePhone.registerForSubscriptionInfoReady(h, what, obj);
    }

    public void unregisterForSubscriptionInfoReady(Handler h) {
        this.mActivePhone.unregisterForSubscriptionInfoReady(h);
    }

    public void registerForEcmTimerReset(Handler h, int what, Object obj) {
        this.mActivePhone.registerForEcmTimerReset(h, what, obj);
    }

    public void unregisterForEcmTimerReset(Handler h) {
        this.mActivePhone.unregisterForEcmTimerReset(h);
    }

    public void registerForRingbackTone(Handler h, int what, Object obj) {
        this.mActivePhone.registerForRingbackTone(h, what, obj);
    }

    public void unregisterForRingbackTone(Handler h) {
        this.mActivePhone.unregisterForRingbackTone(h);
    }

    public void registerForOnHoldTone(Handler h, int what, Object obj) {
        this.mActivePhone.registerForOnHoldTone(h, what, obj);
    }

    public void unregisterForOnHoldTone(Handler h) {
        this.mActivePhone.unregisterForOnHoldTone(h);
    }

    public void registerForResendIncallMute(Handler h, int what, Object obj) {
        this.mActivePhone.registerForResendIncallMute(h, what, obj);
    }

    public void unregisterForResendIncallMute(Handler h) {
        this.mActivePhone.unregisterForResendIncallMute(h);
    }

    public void registerForSimRecordsLoaded(Handler h, int what, Object obj) {
        this.mActivePhone.registerForSimRecordsLoaded(h, what, obj);
    }

    public void unregisterForSimRecordsLoaded(Handler h) {
        this.mActivePhone.unregisterForSimRecordsLoaded(h);
    }

    public boolean getIccRecordsLoaded() {
        return this.mIccCardProxy.getIccRecordsLoaded();
    }

    public IccCard getIccCard() {
        return this.mIccCardProxy;
    }

    public void acceptCall(int videoState) throws CallStateException {
        this.mActivePhone.acceptCall(videoState);
    }

    public void rejectCall() throws CallStateException {
        this.mActivePhone.rejectCall();
    }

    public void switchHoldingAndActive() throws CallStateException {
        this.mActivePhone.switchHoldingAndActive();
    }

    public boolean canConference() {
        return this.mActivePhone.canConference();
    }

    public void conference() throws CallStateException {
        this.mActivePhone.conference();
    }

    public void enableEnhancedVoicePrivacy(boolean enable, Message onComplete) {
        this.mActivePhone.enableEnhancedVoicePrivacy(enable, onComplete);
    }

    public void getEnhancedVoicePrivacy(Message onComplete) {
        this.mActivePhone.getEnhancedVoicePrivacy(onComplete);
    }

    public boolean canTransfer() {
        return this.mActivePhone.canTransfer();
    }

    public void explicitCallTransfer() throws CallStateException {
        this.mActivePhone.explicitCallTransfer();
    }

    public void clearDisconnected() {
        this.mActivePhone.clearDisconnected();
    }

    public Call getForegroundCall() {
        return this.mActivePhone.getForegroundCall();
    }

    public Call getBackgroundCall() {
        return this.mActivePhone.getBackgroundCall();
    }

    public Call getRingingCall() {
        return this.mActivePhone.getRingingCall();
    }

    public Connection dial(String dialString, int videoState) throws CallStateException {
        return this.mActivePhone.dial(dialString, videoState);
    }

    public Connection dial(String dialString, UUSInfo uusInfo, int videoState) throws CallStateException {
        return this.mActivePhone.dial(dialString, uusInfo, videoState);
    }

    public Connection dial(String dialString, int videoState, int callType, int callDomain, String[] extras) throws CallStateException {
        return this.mActivePhone.dial(dialString, videoState, callType, callDomain, extras);
    }

    public Connection dial(String dialString, UUSInfo uusInfo, int videoState, int callType, int callDomain, String[] extras) throws CallStateException {
        return this.mActivePhone.dial(dialString, uusInfo, videoState, callType, callDomain, extras);
    }

    public boolean handlePinMmi(String dialString) {
        return this.mActivePhone.handlePinMmi(dialString);
    }

    public boolean handleInCallMmiCommands(String command) throws CallStateException {
        return this.mActivePhone.handleInCallMmiCommands(command);
    }

    public void sendDtmf(char c) {
        this.mActivePhone.sendDtmf(c);
    }

    public void startDtmf(char c) {
        this.mActivePhone.startDtmf(c);
    }

    public void stopDtmf() {
        this.mActivePhone.stopDtmf();
    }

    public void setRadioPower(boolean power) {
        this.mActivePhone.setRadioPower(power);
    }

    public boolean getMessageWaitingIndicator() {
        return this.mActivePhone.getMessageWaitingIndicator();
    }

    public boolean getCallForwardingIndicator() {
        return this.mActivePhone.getCallForwardingIndicator();
    }

    public String getLine1Number() {
        return this.mActivePhone.getLine1Number();
    }

    public String getCdmaMin() {
        return this.mActivePhone.getCdmaMin();
    }

    public boolean isMinInfoReady() {
        return this.mActivePhone.isMinInfoReady();
    }

    public String getCdmaPrlVersion() {
        return this.mActivePhone.getCdmaPrlVersion();
    }

    public String getLine1AlphaTag() {
        return this.mActivePhone.getLine1AlphaTag();
    }

    public void setLine1Number(String alphaTag, String number, Message onComplete) {
        this.mActivePhone.setLine1Number(alphaTag, number, onComplete);
    }

    public String getVoiceMailNumber() {
        return this.mActivePhone.getVoiceMailNumber();
    }

    public int getVoiceMessageCount() {
        return this.mActivePhone.getVoiceMessageCount();
    }

    public String getVoiceMailAlphaTag() {
        return this.mActivePhone.getVoiceMailAlphaTag();
    }

    public void setVoiceMailNumber(String alphaTag, String voiceMailNumber, Message onComplete) {
        this.mActivePhone.setVoiceMailNumber(alphaTag, voiceMailNumber, onComplete);
    }

    public void getCallForwardingOption(int commandInterfaceCFReason, Message onComplete) {
        this.mActivePhone.getCallForwardingOption(commandInterfaceCFReason, onComplete);
    }

    public void setCallForwardingOption(int commandInterfaceCFReason, int commandInterfaceCFAction, String dialingNumber, int timerSeconds, Message onComplete) {
        this.mActivePhone.setCallForwardingOption(commandInterfaceCFReason, commandInterfaceCFAction, dialingNumber, timerSeconds, onComplete);
    }

    public void getOutgoingCallerIdDisplay(Message onComplete) {
        this.mActivePhone.getOutgoingCallerIdDisplay(onComplete);
    }

    public void setOutgoingCallerIdDisplay(int commandInterfaceCLIRMode, Message onComplete) {
        this.mActivePhone.setOutgoingCallerIdDisplay(commandInterfaceCLIRMode, onComplete);
    }

    public void getCallWaiting(Message onComplete) {
        this.mActivePhone.getCallWaiting(onComplete);
    }

    public void setCallWaiting(boolean enable, Message onComplete) {
        this.mActivePhone.setCallWaiting(enable, onComplete);
    }

    public void getAvailableNetworks(Message response) {
        this.mActivePhone.getAvailableNetworks(response);
    }

    public void setNetworkSelectionModeAutomatic(Message response) {
        this.mActivePhone.setNetworkSelectionModeAutomatic(response);
    }

    public void selectNetworkManually(OperatorInfo network, Message response) {
        this.mActivePhone.selectNetworkManually(network, response);
    }

    public void setPreferredNetworkType(int networkType, Message response) {
        this.mActivePhone.setPreferredNetworkType(networkType, response);
    }

    public void getPreferredNetworkType(Message response) {
        this.mActivePhone.getPreferredNetworkType(response);
    }

    public void getNeighboringCids(Message response) {
        this.mActivePhone.getNeighboringCids(response);
    }

    public void setOnPostDialCharacter(Handler h, int what, Object obj) {
        this.mActivePhone.setOnPostDialCharacter(h, what, obj);
    }

    public void setMute(boolean muted) {
        this.mActivePhone.setMute(muted);
    }

    public boolean getMute() {
        return this.mActivePhone.getMute();
    }

    public void setEchoSuppressionEnabled() {
        this.mActivePhone.setEchoSuppressionEnabled();
    }

    public void invokeOemRilRequestRaw(byte[] data, Message response) {
        this.mActivePhone.invokeOemRilRequestRaw(data, response);
    }

    public void invokeOemRilRequestStrings(String[] strings, Message response) {
        this.mActivePhone.invokeOemRilRequestStrings(strings, response);
    }

    public void getDataCallList(Message response) {
        this.mActivePhone.getDataCallList(response);
    }

    public void updateServiceLocation() {
        this.mActivePhone.updateServiceLocation();
    }

    public void enableLocationUpdates() {
        this.mActivePhone.enableLocationUpdates();
    }

    public void disableLocationUpdates() {
        this.mActivePhone.disableLocationUpdates();
    }

    public void setUnitTestMode(boolean f) {
        this.mActivePhone.setUnitTestMode(f);
    }

    public boolean getUnitTestMode() {
        return this.mActivePhone.getUnitTestMode();
    }

    public void setBandMode(int bandMode, Message response) {
        this.mActivePhone.setBandMode(bandMode, response);
    }

    public void queryAvailableBandMode(Message response) {
        this.mActivePhone.queryAvailableBandMode(response);
    }

    public void setLteBandMode(int bandMode, Message response) {
        this.mActivePhone.setLteBandMode(bandMode, response);
    }

    public boolean getDataRoamingEnabled() {
        return this.mActivePhone.getDataRoamingEnabled();
    }

    public void setDataRoamingEnabled(boolean enable) {
        this.mActivePhone.setDataRoamingEnabled(enable);
    }

    public String getSelectedApn() {
        return this.mActivePhone.getSelectedApn();
    }

    public void setSelectedApn() {
        this.mActivePhone.setSelectedApn();
    }

    public boolean getDataEnabled() {
        return this.mActivePhone.getDataEnabled();
    }

    public void setDataEnabled(boolean enable) {
        this.mActivePhone.setDataEnabled(enable);
    }

    public void queryCdmaRoamingPreference(Message response) {
        this.mActivePhone.queryCdmaRoamingPreference(response);
    }

    public void setCdmaRoamingPreference(int cdmaRoamingType, Message response) {
        this.mActivePhone.setCdmaRoamingPreference(cdmaRoamingType, response);
    }

    public void setCdmaSubscription(int cdmaSubscriptionType, Message response) {
        this.mActivePhone.setCdmaSubscription(cdmaSubscriptionType, response);
    }

    public SimulatedRadioControl getSimulatedRadioControl() {
        return this.mActivePhone.getSimulatedRadioControl();
    }

    public boolean isDataConnectivityPossible() {
        return this.mActivePhone.isDataConnectivityPossible("default");
    }

    public boolean isDataConnectivityPossible(String apnType) {
        return this.mActivePhone.isDataConnectivityPossible(apnType);
    }

    public String getDeviceId() {
        return this.mActivePhone.getDeviceId();
    }

    public String getDeviceSvn() {
        return this.mActivePhone.getDeviceSvn();
    }

    public String getSubscriberId() {
        return this.mActivePhone.getSubscriberId();
    }

    public String getGroupIdLevel1() {
        return this.mActivePhone.getGroupIdLevel1();
    }

    public String getIccSerialNumber() {
        return this.mActivePhone.getIccSerialNumber();
    }

    public String getEsn() {
        return this.mActivePhone.getEsn();
    }

    public String getRuimid() {
        return this.mActivePhone.getRuimid();
    }

    public String getMeid() {
        return this.mActivePhone.getMeid();
    }

    public String getMsisdn() {
        return this.mActivePhone.getMsisdn();
    }

    public String getImei() {
        return this.mActivePhone.getImei();
    }

    public boolean hasIsim() {
        return this.mActivePhone.hasIsim();
    }

    public byte[] getPsismsc() {
        return this.mActivePhone.getPsismsc();
    }

    public PhoneSubInfo getPhoneSubInfo() {
        return this.mActivePhone.getPhoneSubInfo();
    }

    public IccPhoneBookInterfaceManager getIccPhoneBookInterfaceManager() {
        return this.mActivePhone.getIccPhoneBookInterfaceManager();
    }

    public void setTTYMode(int ttyMode, Message onComplete) {
        this.mActivePhone.setTTYMode(ttyMode, onComplete);
    }

    public void queryTTYMode(Message onComplete) {
        this.mActivePhone.queryTTYMode(onComplete);
    }

    public void activateCellBroadcastSms(int activate, Message response) {
        this.mActivePhone.activateCellBroadcastSms(activate, response);
    }

    public void getCellBroadcastSmsConfig(Message response) {
        this.mActivePhone.getCellBroadcastSmsConfig(response);
    }

    public void setCellBroadcastSmsConfig(int[] configValuesArray, Message response) {
        this.mActivePhone.setCellBroadcastSmsConfig(configValuesArray, response);
    }

    public void notifyDataActivity() {
        this.mActivePhone.notifyDataActivity();
    }

    public void getSmscAddress(Message result) {
        this.mActivePhone.getSmscAddress(result);
    }

    public void setSmscAddress(String address, Message result) {
        this.mActivePhone.setSmscAddress(address, result);
    }

    public int getCdmaEriIconIndex() {
        return this.mActivePhone.getCdmaEriIconIndex();
    }

    public String getCdmaEriText() {
        return this.mActivePhone.getCdmaEriText();
    }

    public int getCdmaEriIconMode() {
        return this.mActivePhone.getCdmaEriIconMode();
    }

    public Phone getActivePhone() {
        return this.mActivePhone;
    }

    public void sendBurstDtmf(String dtmfString, int on, int off, Message onComplete) {
        this.mActivePhone.sendBurstDtmf(dtmfString, on, off, onComplete);
    }

    public void exitEmergencyCallbackMode() {
        this.mActivePhone.exitEmergencyCallbackMode();
    }

    public boolean needsOtaServiceProvisioning() {
        return this.mActivePhone.needsOtaServiceProvisioning();
    }

    public boolean isOtaSpNumber(String dialStr) {
        return this.mActivePhone.isOtaSpNumber(dialStr);
    }

    public void registerForCallWaiting(Handler h, int what, Object obj) {
        this.mActivePhone.registerForCallWaiting(h, what, obj);
    }

    public void unregisterForCallWaiting(Handler h) {
        this.mActivePhone.unregisterForCallWaiting(h);
    }

    public void registerForSignalInfo(Handler h, int what, Object obj) {
        this.mActivePhone.registerForSignalInfo(h, what, obj);
    }

    public void unregisterForSignalInfo(Handler h) {
        this.mActivePhone.unregisterForSignalInfo(h);
    }

    public void registerForDisplayInfo(Handler h, int what, Object obj) {
        this.mActivePhone.registerForDisplayInfo(h, what, obj);
    }

    public void unregisterForDisplayInfo(Handler h) {
        this.mActivePhone.unregisterForDisplayInfo(h);
    }

    public void registerForNumberInfo(Handler h, int what, Object obj) {
        this.mActivePhone.registerForNumberInfo(h, what, obj);
    }

    public void unregisterForNumberInfo(Handler h) {
        this.mActivePhone.unregisterForNumberInfo(h);
    }

    public void registerForRedirectedNumberInfo(Handler h, int what, Object obj) {
        this.mActivePhone.registerForRedirectedNumberInfo(h, what, obj);
    }

    public void unregisterForRedirectedNumberInfo(Handler h) {
        this.mActivePhone.unregisterForRedirectedNumberInfo(h);
    }

    public void registerForLineControlInfo(Handler h, int what, Object obj) {
        this.mActivePhone.registerForLineControlInfo(h, what, obj);
    }

    public void unregisterForLineControlInfo(Handler h) {
        this.mActivePhone.unregisterForLineControlInfo(h);
    }

    public void registerFoT53ClirlInfo(Handler h, int what, Object obj) {
        this.mActivePhone.registerFoT53ClirlInfo(h, what, obj);
    }

    public void unregisterForT53ClirInfo(Handler h) {
        this.mActivePhone.unregisterForT53ClirInfo(h);
    }

    public void registerForT53AudioControlInfo(Handler h, int what, Object obj) {
        this.mActivePhone.registerForT53AudioControlInfo(h, what, obj);
    }

    public void unregisterForT53AudioControlInfo(Handler h) {
        this.mActivePhone.unregisterForT53AudioControlInfo(h);
    }

    public void setOnEcbModeExitResponse(Handler h, int what, Object obj) {
        this.mActivePhone.setOnEcbModeExitResponse(h, what, obj);
    }

    public void unsetOnEcbModeExitResponse(Handler h) {
        this.mActivePhone.unsetOnEcbModeExitResponse(h);
    }

    public boolean isCspPlmnEnabled() {
        return this.mActivePhone.isCspPlmnEnabled();
    }

    public IsimRecords getIsimRecords() {
        return this.mActivePhone.getIsimRecords();
    }

    public int getLteOnCdmaMode() {
        return this.mActivePhone.getLteOnCdmaMode();
    }

    public void setVoiceMessageWaiting(int line, int countWaiting) {
        this.mActivePhone.setVoiceMessageWaiting(line, countWaiting);
    }

    public UsimServiceTable getUsimServiceTable() {
        return this.mActivePhone.getUsimServiceTable();
    }

    public UiccCard getUiccCard() {
        return this.mActivePhone.getUiccCard();
    }

    public void nvReadItem(int itemID, Message response) {
        this.mActivePhone.nvReadItem(itemID, response);
    }

    public void nvWriteItem(int itemID, String itemValue, Message response) {
        this.mActivePhone.nvWriteItem(itemID, itemValue, response);
    }

    public void nvWriteCdmaPrl(byte[] preferredRoamingList, Message response) {
        this.mActivePhone.nvWriteCdmaPrl(preferredRoamingList, response);
    }

    public void nvResetConfig(int resetType, Message response) {
        this.mActivePhone.nvResetConfig(resetType, response);
    }

    public void dispose() {
        if ("CTC".equals(SystemProperties.get("ro.csc.sales_code")) && this.mActivePhone.getPhoneId() == 0) {
            this.mActivePhone.getContext().unregisterReceiver(this.mIntentReceiver);
        }
        this.mCommandsInterface.unregisterForOn(this);
        this.mCommandsInterface.unregisterForVoiceRadioTechChanged(this);
        this.mCommandsInterface.unregisterForRilConnected(this);
    }

    public void removeReferences() {
        this.mActivePhone = null;
        this.mCommandsInterface = null;
    }

    public boolean updateCurrentCarrierInProvider() {
        if (this.mActivePhone instanceof CDMALTEPhone) {
            return ((CDMALTEPhone) this.mActivePhone).updateCurrentCarrierInProvider();
        }
        if (this.mActivePhone instanceof GSMPhone) {
            return ((GSMPhone) this.mActivePhone).updateCurrentCarrierInProvider();
        }
        loge("Phone object is not MultiSim. This should not hit!!!!");
        return false;
    }

    public void updateDataConnectionTracker() {
        logd("Updating Data Connection Tracker");
        if (this.mActivePhone instanceof CDMALTEPhone) {
            ((CDMALTEPhone) this.mActivePhone).updateDataConnectionTracker();
        } else if (this.mActivePhone instanceof GSMPhone) {
            ((GSMPhone) this.mActivePhone).updateDataConnectionTracker();
        } else {
            loge("Phone object is not MultiSim. This should not hit!!!!");
        }
    }

    public void setInternalDataEnabled(boolean enable) {
        setInternalDataEnabled(enable, null);
    }

    public boolean setInternalDataEnabledFlag(boolean enable) {
        if (this.mActivePhone instanceof CDMALTEPhone) {
            return ((CDMALTEPhone) this.mActivePhone).setInternalDataEnabledFlag(enable);
        }
        if (this.mActivePhone instanceof GSMPhone) {
            return ((GSMPhone) this.mActivePhone).setInternalDataEnabledFlag(enable);
        }
        if (this.mActivePhone instanceof CDMAPhone) {
            return ((CDMAPhone) this.mActivePhone).setInternalDataEnabledFlag(enable);
        }
        loge("Phone object is not MultiSim. This should not hit!!!!");
        return false;
    }

    public void setInternalDataEnabled(boolean enable, Message onCompleteMsg) {
        if (this.mActivePhone instanceof CDMALTEPhone) {
            ((CDMALTEPhone) this.mActivePhone).setInternalDataEnabled(enable, onCompleteMsg);
        } else if (this.mActivePhone instanceof GSMPhone) {
            ((GSMPhone) this.mActivePhone).setInternalDataEnabled(enable, onCompleteMsg);
        } else if (this.mActivePhone instanceof CDMAPhone) {
            ((CDMAPhone) this.mActivePhone).setInternalDataEnabled(enable, onCompleteMsg);
        } else {
            loge("Phone object is not MultiSim. This should not hit!!!!");
        }
    }

    public void registerForAllDataDisconnected(Handler h, int what, Object obj) {
        if (this.mActivePhone instanceof CDMALTEPhone) {
            ((CDMALTEPhone) this.mActivePhone).registerForAllDataDisconnected(h, what, obj);
        } else if (this.mActivePhone instanceof GSMPhone) {
            ((GSMPhone) this.mActivePhone).registerForAllDataDisconnected(h, what, obj);
        } else if (this.mActivePhone instanceof CDMAPhone) {
            ((CDMAPhone) this.mActivePhone).registerForAllDataDisconnected(h, what, obj);
        } else {
            loge("Phone object is not MultiSim. This should not hit!!!!");
        }
    }

    public void unregisterForAllDataDisconnected(Handler h) {
        if (this.mActivePhone instanceof CDMALTEPhone) {
            ((CDMALTEPhone) this.mActivePhone).unregisterForAllDataDisconnected(h);
        } else if (this.mActivePhone instanceof GSMPhone) {
            ((GSMPhone) this.mActivePhone).unregisterForAllDataDisconnected(h);
        } else if (this.mActivePhone instanceof CDMAPhone) {
            ((CDMAPhone) this.mActivePhone).unregisterForAllDataDisconnected(h);
        } else {
            loge("Phone object is not MultiSim. This should not hit!!!!");
        }
    }

    public long getSubId() {
        return this.mActivePhone.getSubId();
    }

    public int getPhoneId() {
        return this.mActivePhone.getPhoneId();
    }

    public String[] getPcscfAddress(String apnType) {
        return this.mActivePhone.getPcscfAddress(apnType);
    }

    public void setImsRegistrationState(boolean registered) {
        logd("setImsRegistrationState - registered: " + registered);
        this.mActivePhone.setImsRegistrationState(registered);
        if (this.mActivePhone.getPhoneName().equals("GSM")) {
            this.mActivePhone.getServiceStateTracker().setImsRegistrationState(registered);
        } else if (this.mActivePhone.getPhoneName().equals("CDMA")) {
            this.mActivePhone.getServiceStateTracker().setImsRegistrationState(registered);
        }
    }

    public Phone getImsPhone() {
        return this.mActivePhone.getImsPhone();
    }

    public ImsPhone relinquishOwnershipOfImsPhone() {
        return null;
    }

    public void acquireOwnershipOfImsPhone(ImsPhone imsPhone) {
    }

    public int getVoicePhoneServiceState() {
        return this.mActivePhone.getVoicePhoneServiceState();
    }

    public boolean setOperatorBrandOverride(String brand) {
        return this.mActivePhone.setOperatorBrandOverride(brand);
    }

    public boolean isRadioAvailable() {
        return this.mCommandsInterface.getRadioState().isAvailable();
    }

    public void shutdownRadio() {
        this.mActivePhone.shutdownRadio();
    }

    public void registerForDataConnectionStateChanged(Handler h, int what, Object obj) {
        this.mActivePhone.registerForDataConnectionStateChanged(h, what, obj);
    }

    public void unregisterForDataConnectionStateChanged(Handler h) {
        this.mActivePhone.unregisterForDataConnectionStateChanged(h);
    }

    public void notifyDataConnectionStateChanged(int state, String reason, String apnType) {
        this.mActivePhone.notifyDataConnectionStateChanged(state, reason, apnType);
    }

    public void changeConnectionType(Message msg, Connection conn, int newCallType, Map<String, String> newExtras) throws CallStateException {
        this.mActivePhone.changeConnectionType(msg, conn, newCallType, newExtras);
    }

    public void acceptConnectionTypeChange(Connection conn, Map<String, String> newExtras) throws CallStateException {
        this.mActivePhone.acceptConnectionTypeChange(conn, newExtras);
    }

    public void rejectConnectionTypeChange(Connection conn) throws CallStateException {
        this.mActivePhone.rejectConnectionTypeChange(conn);
    }

    public int getProposedConnectionType(Connection conn) throws CallStateException {
        return this.mActivePhone.getProposedConnectionType(conn);
    }

    public boolean getSMSavailable() {
        return this.mActivePhone.getSMSavailable();
    }

    public boolean getSMSPavailable() {
        return this.mActivePhone.getSMSPavailable();
    }

    public boolean IsInternationalRoaming() {
        return this.mActivePhone.IsInternationalRoaming();
    }

    public boolean IsDomesticRoaming() {
        return this.mActivePhone.IsDomesticRoaming();
    }

    public void setGbaBootstrappingParams(byte[] rand, String btid, String keyLifetime, Message onComplete) {
        this.mActivePhone.setGbaBootstrappingParams(rand, btid, keyLifetime, onComplete);
    }

    public int getDataServiceState() {
        return this.mActivePhone.getDataServiceState();
    }

    public void registerForModifyCallRequest(Handler h, int what, Object obj) {
        this.mActivePhone.registerForModifyCallRequest(h, what, obj);
    }

    public void unregisterForModifyCallRequest(Handler h) {
        this.mActivePhone.unregisterForModifyCallRequest(h);
    }

    public void setTransmitPower(int powerLevel, Message onCompleted) {
        this.mCommandsInterface.setTransmitPower(powerLevel, onCompleted);
    }

    public String getHandsetInfo(String ID) {
        return this.mActivePhone.getHandsetInfo(ID);
    }

    public boolean setDrxMode(int drxMode) {
        return this.mActivePhone.setDrxMode(drxMode);
    }

    public int getDrxValue() {
        return this.mActivePhone.getDrxValue();
    }

    public boolean setReducedCycleTime(int cycleTime) {
        return this.mActivePhone.setReducedCycleTime(cycleTime);
    }

    public int getReducedCycleTime() {
        return this.mActivePhone.getReducedCycleTime();
    }

    public String getSktImsiM() {
        return this.mActivePhone.getSktImsiM();
    }

    public String getSktIrm() {
        return this.mActivePhone.getSktIrm();
    }

    public void getCallForwardingOption(int commandInterfaceCFReason, String dialingNumber, int serviceClass, Message onComplete) {
        this.mActivePhone.getCallForwardingOption(commandInterfaceCFReason, dialingNumber, serviceClass, onComplete);
    }

    public void setCallForwardingOption(int commandInterfaceCFAction, int commandInterfaceCFReason, String dialingNumber, int timerSeconds, int serviceClass, Message onComplete) {
        this.mActivePhone.setCallForwardingOption(commandInterfaceCFAction, commandInterfaceCFReason, dialingNumber, timerSeconds, serviceClass, onComplete);
    }

    public void getPreferredNetworkList(Message response) {
        this.mActivePhone.getPreferredNetworkList(response);
    }

    public void setPreferredNetworkList(int index, String operator, String plmn, int gsmAct, int gsmCompactAct, int utranAct, int mode, Message response) {
        this.mActivePhone.setPreferredNetworkList(index, operator, plmn, gsmAct, gsmCompactAct, utranAct, mode, response);
    }

    public void getCallBarringOption(String commandInterfacecbFlavour, Message onComplete) {
        this.mActivePhone.getCallBarringOption(commandInterfacecbFlavour, onComplete);
    }

    public void getCallBarringOption(String commandInterfacecbFlavour, String password, int serviceClass, Message onComplete) {
        this.mActivePhone.getCallBarringOption(commandInterfacecbFlavour, password, serviceClass, onComplete);
    }

    public boolean setDmCmdInfo(int cmd, byte[] info) {
        return this.mActivePhone.setDmCmdInfo(cmd, info);
    }

    public boolean setCallBarringOption(boolean cbAction, String commandInterfacecbFlavour, String password, Message onComplete) {
        return this.mActivePhone.setCallBarringOption(cbAction, commandInterfacecbFlavour, password, onComplete);
    }

    public boolean setCallBarringOption(boolean cbAction, String commandInterfacecbFlavour, String password, int serviceClass, Message onComplete) {
        return this.mActivePhone.setCallBarringOption(cbAction, commandInterfacecbFlavour, password, serviceClass, onComplete);
    }

    public boolean changeBarringPassword(String facility, String oldPwd, String newPwd, Message onComplete) {
        return this.mActivePhone.changeBarringPassword(facility, oldPwd, newPwd, onComplete);
    }

    public boolean changeBarringPassword(String facility, String oldPwd, String newPwd, String newPwdAgain, Message onComplete) {
        return this.mActivePhone.changeBarringPassword(facility, oldPwd, newPwd, newPwdAgain, onComplete);
    }

    public boolean isImsRegistered() {
        return this.mActivePhone.isImsRegistered();
    }

    public boolean isVolteRegistered() {
        return this.mActivePhone.isVolteRegistered();
    }

    public boolean isWfcRegistered() {
        return this.mActivePhone.isWfcRegistered();
    }

    public int getImsRegisteredFeature(int connectivityType) {
        return this.mActivePhone.getImsRegisteredFeature(connectivityType);
    }

    public int getImsRegisteredFeature() {
        return this.mActivePhone.getImsRegisteredFeature();
    }

    public LegacyIms getLegacyIms() {
        return this.mActivePhone.getLegacyIms();
    }

    public boolean hasCall(String callType) {
        return this.mActivePhone.hasCall(callType);
    }

    public String getLine1NumberType(int SimType) {
        return this.mActivePhone.getLine1NumberType(SimType);
    }

    public String getSubscriberIdType(int SimType) {
        return this.mActivePhone.getSubscriberIdType(SimType);
    }

    public void SimSlotOnOff(int on) {
        logd("SimSlotOnOff");
        this.mActivePhone.SimSlotOnOff(on);
    }

    public void SimSlotActivation(boolean activation) {
        this.mActivePhone.SimSlotActivation(activation);
    }

    public boolean getDualSimSlotActivationState() {
        return this.mActivePhone.getDualSimSlotActivationState();
    }

    public String[] getSponImsi() {
        return this.mActivePhone.getSponImsi();
    }

    public void startGlobalNetworkSearchTimer() {
        this.mActivePhone.startGlobalNetworkSearchTimer();
    }

    public void stopGlobalNetworkSearchTimer() {
        this.mActivePhone.stopGlobalNetworkSearchTimer();
    }

    public void startGlobalNoSvcChkTimer() {
        this.mActivePhone.startGlobalNoSvcChkTimer();
    }

    public void stopGlobalNoSvcChkTimer() {
        this.mActivePhone.stopGlobalNoSvcChkTimer();
    }

    public CatService getCatService() {
        UiccCard uiccCard = getUiccCard();
        if (uiccCard != null) {
            return uiccCard.getCatService();
        }
        loge("Failed to get UiccCard in PhoneProxy for getCatService");
        return null;
    }

    public void holdCall() throws CallStateException {
        this.mActivePhone.holdCall();
    }

    public boolean getOCSGLAvailable() {
        return this.mActivePhone.getOCSGLAvailable();
    }

    public void selectCsg(Message response) {
        this.mActivePhone.selectCsg(response);
    }

    public boolean getFDNavailable() {
        return this.mActivePhone.getFDNavailable();
    }

    public boolean isApnTypeAvailable(String apnType) {
        return this.mActivePhone.isApnTypeAvailable(apnType);
    }
}
