package com.android.internal.telephony.cdma;

import android.content.ContentValues;
import android.content.Context;
import android.database.SQLException;
import android.net.Uri;
import android.os.Handler;
import android.os.Message;
import android.os.PowerManager;
import android.os.SystemProperties;
import android.provider.Telephony.Carriers;
import android.telephony.Rlog;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import com.android.internal.telephony.CommandsInterface;
import com.android.internal.telephony.DctConstants.State;
import com.android.internal.telephony.MccTable;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.PhoneConstants.DataState;
import com.android.internal.telephony.PhoneNotifier;
import com.android.internal.telephony.PhoneProxy;
import com.android.internal.telephony.PhoneSubInfo;
import com.android.internal.telephony.SubscriptionController;
import com.android.internal.telephony.dataconnection.DcTracker;
import com.android.internal.telephony.uicc.IccRecords;
import com.android.internal.telephony.uicc.IsimRecords;
import com.android.internal.telephony.uicc.IsimUiccRecords;
import com.android.internal.telephony.uicc.RuimRecords;
import com.android.internal.telephony.uicc.SIMRecords;
import com.android.internal.telephony.uicc.UiccCardApplication;
import com.itsoninc.android.ItsOnOemApi;
import com.sec.android.app.CscFeature;
import java.io.FileDescriptor;
import java.io.PrintWriter;

public class CDMALTEPhone extends CDMAPhone {
    private static final boolean DBG = true;
    static final String LOG_LTE_TAG = "CDMALTEPhone";
    private IsimUiccRecords mIsimUiccRecords;
    private RuimRecords mRuimRecords;
    private SIMRecords mSimRecords;

    static /* synthetic */ class C00681 {
        static final /* synthetic */ int[] $SwitchMap$com$android$internal$telephony$DctConstants$State = new int[State.values().length];

        static {
            try {
                $SwitchMap$com$android$internal$telephony$DctConstants$State[State.RETRYING.ordinal()] = 1;
            } catch (NoSuchFieldError e) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$DctConstants$State[State.FAILED.ordinal()] = 2;
            } catch (NoSuchFieldError e2) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$DctConstants$State[State.IDLE.ordinal()] = 3;
            } catch (NoSuchFieldError e3) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$DctConstants$State[State.CONNECTED.ordinal()] = 4;
            } catch (NoSuchFieldError e4) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$DctConstants$State[State.DISCONNECTING.ordinal()] = 5;
            } catch (NoSuchFieldError e5) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$DctConstants$State[State.CONNECTING.ordinal()] = 6;
            } catch (NoSuchFieldError e6) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$DctConstants$State[State.SCANNING.ordinal()] = 7;
            } catch (NoSuchFieldError e7) {
            }
        }
    }

    public CDMALTEPhone(Context context, CommandsInterface ci, PhoneNotifier notifier, int phoneId) {
        this(context, ci, notifier, false, phoneId);
    }

    public CDMALTEPhone(Context context, CommandsInterface ci, PhoneNotifier notifier, boolean unitTestMode, int phoneId) {
        super(context, ci, notifier, phoneId);
        log("CDMALTEPhone: constructor: sub = " + this.mPhoneId);
        this.mDcTracker = new DcTracker(this);
    }

    public CDMALTEPhone(Context context, CommandsInterface ci, PhoneNotifier notifier) {
        super(context, ci, notifier, false);
    }

    public void handleMessage(Message msg) {
        switch (msg.what) {
            case 500:
                log("EVENT_SUBSCRIPTION_ACTIVATED");
                onSubscriptionActivated();
                return;
            case 501:
                log("EVENT_SUBSCRIPTION_DEACTIVATED");
                onSubscriptionDeactivated();
                return;
            default:
                super.handleMessage(msg);
                return;
        }
    }

    protected void initSstIcc() {
        if (this.mCT == null) {
            this.mCT = new CdmaCallTracker(this);
        }
        this.mSST = new CdmaLteServiceStateTracker(this);
    }

    public void dispose() {
        synchronized (PhoneProxy.lockForRadioTechnologyChange) {
            super.dispose();
        }
    }

    public void removeReferences() {
        super.removeReferences();
    }

    public DataState getDataConnectionState(String apnType) {
        DataState ret = DataState.DISCONNECTED;
        if (this.mSST != null) {
            if (this.mSST.getCurrentDataConnectionState() == 0) {
                if (this.mDcTracker.isApnTypeEnabled(apnType)) {
                    switch (C00681.$SwitchMap$com$android$internal$telephony$DctConstants$State[this.mDcTracker.getState(apnType).ordinal()]) {
                        case 1:
                        case 2:
                        case 3:
                            ret = DataState.DISCONNECTED;
                            break;
                        case 4:
                        case 5:
                            if (this.mCT.mState == PhoneConstants.State.IDLE || this.mSST.isConcurrentVoiceAndDataAllowed()) {
                                if (!"default".equals(apnType) || !this.mSST.needBlockData()) {
                                    ret = DataState.CONNECTED;
                                    break;
                                }
                                ret = DataState.SUSPENDED;
                                break;
                            }
                            ret = DataState.SUSPENDED;
                            break;
                            break;
                        case 6:
                        case 7:
                            ret = DataState.CONNECTING;
                            break;
                        default:
                            break;
                    }
                }
                ret = DataState.DISCONNECTED;
            } else {
                ret = DataState.DISCONNECTED;
                log("getDataConnectionState: Data is Out of Service. ret = " + ret + " (eCSFB: " + false + ", CallState: " + this.mCT.mState + ")");
            }
        } else {
            ret = DataState.DISCONNECTED;
        }
        log("getDataConnectionState apnType=" + apnType + " ret=" + ret);
        return ret;
    }

    private boolean checkLTEmode() {
        boolean retVal = false;
        int rilRadioTechnology = getServiceState().getDataNetworkType();
        switch (rilRadioTechnology) {
            case 13:
            case 14:
                retVal = true;
                break;
        }
        log("checkLTEmode = " + rilRadioTechnology + " retVal = " + retVal);
        return retVal;
    }

    boolean updateCurrentCarrierInProvider(String operatorNumeric) {
        boolean retVal;
        String uiccFamilyName = "APP_FAM_3GPP";
        int uiccFamily = 1;
        if ("CTC".equals(SystemProperties.get("ro.csc.sales_code"))) {
            if (checkLTEmode()) {
                uiccFamily = 1;
            } else {
                uiccFamily = 2;
                uiccFamilyName = "APP_FAM_3GPP2";
            }
        }
        if (this.mUiccController.getUiccCardApplication(uiccFamily) == null) {
            log("updateCurrentCarrierInProvider " + uiccFamilyName + " == null");
            retVal = super.updateCurrentCarrierInProvider(operatorNumeric);
        } else {
            log("updateCurrentCarrierInProvider not updated");
            retVal = true;
        }
        log("updateCurrentCarrierInProvider X retVal=" + retVal);
        return retVal;
    }

    public boolean updateCurrentCarrierInProvider() {
        long currentDds = SubscriptionController.getInstance().getDefaultDataSubId();
        String operatorNumeric = getOperatorNumeric();
        String salesCode = SystemProperties.get("ro.csc.sales_code");
        log("updateCurrentCarrierInProvider: mSubscription = " + getSubId() + " currentDds = " + currentDds + " operatorNumeric = " + operatorNumeric);
        Uri uri;
        ContentValues map;
        if ("CTC".equals(salesCode)) {
            if (checkLTEmode()) {
                if (this.mSimRecords != null) {
                    try {
                        uri = Uri.withAppendedPath(Carriers.CONTENT_URI, "current");
                        map = new ContentValues();
                        map.put(Carriers.NUMERIC, operatorNumeric);
                        log("updateCurrentCarrierInProvider from UICC(SIM): numeric=" + operatorNumeric);
                        this.mContext.getContentResolver().insert(uri, map);
                        if (TextUtils.isEmpty(operatorNumeric) || this.mDcTracker.matchIccRecord(operatorNumeric)) {
                            return true;
                        }
                        log("updateCurrentCarrierInProvider : matchIccRecord() = false!");
                        this.mDcTracker.UpdateIccRecords(false);
                        return true;
                    } catch (SQLException e) {
                        loge("Can't store current operator ret false", e);
                    }
                } else {
                    log("updateCurrentCarrierInProvider mIccRecords == null ret false");
                }
            } else if (this.mRuimRecords != null) {
                try {
                    uri = Uri.withAppendedPath(Carriers.CONTENT_URI, "current");
                    map = new ContentValues();
                    map.put(Carriers.NUMERIC, operatorNumeric);
                    log("updateCurrentCarrierInProvider from UICC(RUIM): numeric=" + operatorNumeric);
                    this.mContext.getContentResolver().insert(uri, map);
                    if (TextUtils.isEmpty(operatorNumeric) || this.mDcTracker.matchIccRecord(operatorNumeric)) {
                        return true;
                    }
                    log("updateCurrentCarrierInProvider : matchIccRecord() = false!");
                    this.mDcTracker.UpdateIccRecords(true);
                    return true;
                } catch (SQLException e2) {
                    loge("Can't store current operator ret false", e2);
                }
            } else {
                log("updateCurrentCarrierInProvider mIccRecords == null ret false");
            }
        } else if (!TextUtils.isEmpty(operatorNumeric) && getSubId() == currentDds) {
            try {
                uri = Uri.withAppendedPath(Carriers.CONTENT_URI, "current");
                map = new ContentValues();
                map.put(Carriers.NUMERIC, operatorNumeric);
                this.mContext.getContentResolver().insert(uri, map);
                return true;
            } catch (SQLException e22) {
                loge("Can't store current operator", e22);
            }
        }
        return false;
    }

    public String getSubscriberId() {
        return "CTC".equals(SystemProperties.get("ro.csc.sales_code")) ? this.mRuimRecords != null ? this.mRuimRecords.getIMSI_M() : "" : this.mSimRecords != null ? this.mSimRecords.getIMSI() : "";
    }

    public String getGroupIdLevel1() {
        if ("CTC".equals(SystemProperties.get("ro.csc.sales_code"))) {
            return null;
        }
        return this.mSimRecords != null ? this.mSimRecords.getGid1() : "";
    }

    public String getImei() {
        return this.mImei;
    }

    public String getDeviceSvn() {
        return this.mImeiSv;
    }

    public IsimRecords getIsimRecords() {
        return this.mIsimUiccRecords;
    }

    public String getMsisdn() {
        if ("CTC".equals(SystemProperties.get("ro.csc.sales_code")) || this.mSimRecords == null) {
            return null;
        }
        return this.mSimRecords.getMsisdnNumber();
    }

    public void getAvailableNetworks(Message response) {
        this.mCi.getAvailableNetworks(response);
    }

    protected void onUpdateIccAvailability() {
        if (this.mUiccController != null) {
            String salesCode = SystemProperties.get("ro.csc.sales_code");
            UiccCardApplication newUiccApplication = this.mUiccController.getUiccCardApplication(this.mPhoneId, 3);
            IsimUiccRecords newIsimUiccRecords = null;
            if (newUiccApplication != null) {
                newIsimUiccRecords = (IsimUiccRecords) newUiccApplication.getIccRecords();
            }
            this.mIsimUiccRecords = newIsimUiccRecords;
            if ("CTC".equals(salesCode)) {
                newUiccApplication = this.mUiccController.getUiccCardApplication(this.mPhoneId, 2);
                RuimRecords newRuimRecords = null;
                if (newUiccApplication != null) {
                    newRuimRecords = (RuimRecords) newUiccApplication.getIccRecords();
                }
                if (this.mRuimRecords != newRuimRecords) {
                    if (this.mRuimRecords != null) {
                        log("Removing stale RuimRecords object.");
                        this.mRuimRecords = null;
                    }
                    if (newRuimRecords != null) {
                        log("New RuimRecords found");
                        this.mRuimRecords = newRuimRecords;
                    }
                }
            } else {
                newUiccApplication = this.mUiccController.getUiccCardApplication(this.mPhoneId, 1);
                SIMRecords newSimRecords = null;
                if (newUiccApplication != null) {
                    newSimRecords = (SIMRecords) newUiccApplication.getIccRecords();
                }
                this.mSimRecords = newSimRecords;
            }
            super.onUpdateIccAvailability();
        }
    }

    protected void init(Context context, PhoneNotifier notifier) {
        this.mCi.setPhoneType(2);
        if (this.mCT == null) {
            this.mCT = new CdmaCallTracker(this);
        }
        this.mCdmaSSM = CdmaSubscriptionSourceManager.getInstance(context, this.mCi, this, 27, null);
        this.mRuimPhoneBookInterfaceManager = new RuimPhoneBookInterfaceManager(this);
        this.mSubInfo = new PhoneSubInfo(this);
        this.mEriManager = new EriManager(this, context, 0);
        this.mCi.registerForAvailable(this, 1, null);
        this.mCi.registerForOffOrNotAvailable(this, 8, null);
        this.mCi.registerForOn(this, 5, null);
        this.mCi.setOnSuppServiceNotification(this, 2, null);
        this.mSST.registerForNetworkAttached(this, 19, null);
        this.mCi.setEmergencyCallbackMode(this, 25, null);
        this.mCi.registerForExitEmergencyCallbackMode(this, 26, null);
        this.mWakeLock = ((PowerManager) context.getSystemService("power")).newWakeLock(1, "CDMAPhone");
        this.mIsPhoneInEcmState = SystemProperties.get("ril.cdma.inecmmode", "false").equals("true");
        if (this.mIsPhoneInEcmState) {
            this.mCi.exitEmergencyCallbackMode(obtainMessage(26));
        }
        this.mCarrierOtaSpNumSchema = SystemProperties.get("ro.cdma.otaspnumschema", "");
        setProperties();
        if (CscFeature.getInstance().getEnableStatus("CscFeature_Common_EnableItsOn")) {
            this.mApi = ItsOnOemApi.getInstance();
            this.mApi.initTelephony(context);
        }
    }

    private void onSubscriptionActivated() {
        log("SUBSCRIPTION ACTIVATED : slotId : " + this.mSubscriptionData.slotId + " appid : " + this.mSubscriptionData.m3gpp2Index + " subId : " + this.mSubscriptionData.subId + " subStatus : " + this.mSubscriptionData.subStatus);
        setProperties();
        onUpdateIccAvailability();
        this.mSST.sendMessage(this.mSST.obtainMessage(42));
        ((CdmaLteServiceStateTracker) this.mSST).updateCdmaSubscription();
        ((DcTracker) this.mDcTracker).updateRecords();
    }

    private void onSubscriptionDeactivated() {
        log("SUBSCRIPTION DEACTIVATED");
        this.mSubscriptionData = null;
    }

    private void setProperties() {
        setSystemProperty("gsm.current.phone-type", new Integer(2).toString());
        String operatorAlpha = SystemProperties.get("ro.cdma.home.operator.alpha");
        if (!TextUtils.isEmpty(operatorAlpha)) {
            setSystemProperty("gsm.sim.operator.alpha", operatorAlpha);
        }
        String operatorNumeric = SystemProperties.get(PROPERTY_CDMA_HOME_OPERATOR_NUMERIC);
        if ("CTC".equals(SystemProperties.get("ro.csc.sales_code"))) {
            operatorNumeric = "46003";
        }
        log("update icc_operator_numeric=" + operatorNumeric);
        if (!TextUtils.isEmpty(operatorNumeric)) {
            setSystemProperty("gsm.sim.operator.numeric", operatorNumeric);
            SubscriptionController.getInstance().setMccMnc(operatorNumeric, getSubId());
            setIsoCountryProperty(operatorNumeric);
            log("update mccmnc=" + operatorNumeric);
            MccTable.updateMccMncConfiguration(this.mContext, operatorNumeric, false);
        }
        updateCurrentCarrierInProvider();
    }

    public void setSystemProperty(String property, String value) {
        if (!getUnitTestMode()) {
            TelephonyManager.setTelephonyProperty(property, getSubId(), value);
        }
    }

    public String getSystemProperty(String property, String defValue) {
        if (getUnitTestMode()) {
            return null;
        }
        return TelephonyManager.getTelephonyProperty(property, getSubId(), defValue);
    }

    public void updateDataConnectionTracker() {
        ((DcTracker) this.mDcTracker).update();
    }

    public void setInternalDataEnabled(boolean enable, Message onCompleteMsg) {
        ((DcTracker) this.mDcTracker).setInternalDataEnabled(enable, onCompleteMsg);
    }

    public boolean setInternalDataEnabledFlag(boolean enable) {
        return ((DcTracker) this.mDcTracker).setInternalDataEnabledFlag(enable);
    }

    public String getOperatorNumeric() {
        String operatorNumeric = null;
        IccRecords curIccRecords = null;
        if (this.mCdmaSubscriptionSource == 1) {
            operatorNumeric = SystemProperties.get("ro.cdma.home.operator.numeric");
        } else if (this.mCdmaSubscriptionSource == 0) {
            curIccRecords = this.mSimRecords;
            if (curIccRecords != null) {
                operatorNumeric = curIccRecords.getOperatorNumeric();
            } else {
                curIccRecords = (IccRecords) this.mIccRecords.get();
                if (curIccRecords != null && (curIccRecords instanceof RuimRecords)) {
                    operatorNumeric = ((RuimRecords) curIccRecords).getRUIMOperatorNumeric();
                }
            }
        }
        if (operatorNumeric == null) {
            loge("getOperatorNumeric: Cannot retrieve operatorNumeric: mCdmaSubscriptionSource = " + this.mCdmaSubscriptionSource + " mIccRecords = " + (curIccRecords != null ? Boolean.valueOf(curIccRecords.getRecordsLoaded()) : null));
        }
        log("getOperatorNumeric: mCdmaSubscriptionSource = " + this.mCdmaSubscriptionSource + " operatorNumeric = " + operatorNumeric);
        return operatorNumeric;
    }

    public void registerForAllDataDisconnected(Handler h, int what, Object obj) {
        ((DcTracker) this.mDcTracker).registerForAllDataDisconnected(h, what, obj);
    }

    public void unregisterForAllDataDisconnected(Handler h) {
        ((DcTracker) this.mDcTracker).unregisterForAllDataDisconnected(h);
    }

    public String getLine1Number() {
        String line1number = super.getLine1Number();
        if (!"VZW-CDMA".equals("")) {
            return line1number;
        }
        if ((line1number == null || line1number.length() == 0) && getIsimRecords() != null) {
            return getIsimRecords().getIsimMsisdn();
        }
        return line1number;
    }

    protected void log(String s) {
        Rlog.d(LOG_LTE_TAG, s);
    }

    protected void loge(String s) {
        Rlog.e(LOG_LTE_TAG, s);
    }

    protected void loge(String s, Throwable e) {
        Rlog.e(LOG_LTE_TAG, s, e);
    }

    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        pw.println("CDMALTEPhone extends:");
        super.dump(fd, pw, args);
    }

    public void setGbaBootstrappingParams(byte[] rand, String btid, String keyLifetime, Message onComplete) {
        if (onComplete != null) {
            onComplete.sendToTarget();
        }
    }
}
