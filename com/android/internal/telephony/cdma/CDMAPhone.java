package com.android.internal.telephony.cdma;

import android.app.ActivityManagerNative;
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
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.os.Registrant;
import android.os.RegistrantList;
import android.os.SystemProperties;
import android.preference.PreferenceManager;
import android.provider.Settings.Secure;
import android.provider.Telephony.Carriers;
import android.telephony.CellLocation;
import android.telephony.PhoneNumberUtils;
import android.telephony.Rlog;
import android.telephony.ServiceState;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.telephony.cdma.CdmaCellLocation;
import android.text.TextUtils;
import com.android.ims.ImsManager;
import com.android.internal.telephony.Call;
import com.android.internal.telephony.CallDetails;
import com.android.internal.telephony.CallStateException;
import com.android.internal.telephony.CallTracker;
import com.android.internal.telephony.CommandException;
import com.android.internal.telephony.CommandException.Error;
import com.android.internal.telephony.CommandsInterface;
import com.android.internal.telephony.CommandsInterface.RadioState;
import com.android.internal.telephony.Connection;
import com.android.internal.telephony.DctConstants.Activity;
import com.android.internal.telephony.DctConstants.State;
import com.android.internal.telephony.IccPhoneBookInterfaceManager;
import com.android.internal.telephony.MccTable;
import com.android.internal.telephony.MmiCode;
import com.android.internal.telephony.Phone.DataActivityState;
import com.android.internal.telephony.Phone.SuppService;
import com.android.internal.telephony.PhoneBase;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.PhoneConstants.DataState;
import com.android.internal.telephony.PhoneFactory;
import com.android.internal.telephony.PhoneNotifier;
import com.android.internal.telephony.PhoneProxy;
import com.android.internal.telephony.PhoneSubInfo;
import com.android.internal.telephony.SMSDispatcher;
import com.android.internal.telephony.ServiceStateTracker;
import com.android.internal.telephony.SubscriptionController;
import com.android.internal.telephony.UUSInfo;
import com.android.internal.telephony.WspTypeDecoder;
import com.android.internal.telephony.dataconnection.DcTracker;
import com.android.internal.telephony.gsm.EccTable;
import com.android.internal.telephony.imsphone.ImsPhone;
import com.android.internal.telephony.uicc.IccException;
import com.android.internal.telephony.uicc.IccRecords;
import com.android.internal.telephony.uicc.RuimRecords;
import com.android.internal.telephony.uicc.SIMRecords;
import com.android.internal.telephony.uicc.UiccCard;
import com.android.internal.telephony.uicc.UiccCardApplication;
import com.itsoninc.android.ItsOnOemApi;
import com.sec.android.app.CscFeature;
import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CDMAPhone extends PhoneBase {
    static final int CANCEL_ECM_TIMER = 1;
    public static final String CONTENT_URI_CURRENT = "current";
    private static final boolean DBG = true;
    private static final int DEFAULT_ECM_EXIT_TIMER_VALUE = 300000;
    private static final int GLOBAL_CDMAMODE = 7;
    private static final int GLOBAL_GLOBALMODE = 5;
    private static final int GLOBAL_GSMMODE = 6;
    private static final int INVALID_SYSTEM_SELECTION_CODE = -1;
    private static final String IS683A_FEATURE_CODE = "*228";
    private static final int IS683A_FEATURE_CODE_NUM_DIGITS = 4;
    private static final int IS683A_SYS_SEL_CODE_NUM_DIGITS = 2;
    private static final int IS683A_SYS_SEL_CODE_OFFSET = 4;
    private static final int IS683_CONST_1900MHZ_A_BLOCK = 2;
    private static final int IS683_CONST_1900MHZ_B_BLOCK = 3;
    private static final int IS683_CONST_1900MHZ_C_BLOCK = 4;
    private static final int IS683_CONST_1900MHZ_D_BLOCK = 5;
    private static final int IS683_CONST_1900MHZ_E_BLOCK = 6;
    private static final int IS683_CONST_1900MHZ_F_BLOCK = 7;
    private static final int IS683_CONST_800MHZ_A_BAND = 0;
    private static final int IS683_CONST_800MHZ_B_BAND = 1;
    static final String LOG_TAG = "CDMAPhone";
    static String PROPERTY_CDMA_HOME_OPERATOR_NUMERIC = "ro.cdma.home.operator.numeric";
    static final int RESTART_ECM_TIMER = 0;
    private static final int SIMTYPE_RUIM = 0;
    private static final int SIMTYPE_SIM = 1;
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
    public static final String VM_COUNT_CDMA = "vm_count_key_cdma";
    private static final String VM_NUMBER_CDMA = "vm_number_key_cdma";
    public static final String VM_PRIORITY_CDMA = "vm_priority_key_cdma";
    private static Pattern pOtaSpNumSchema = Pattern.compile("[,\\s]+");
    protected ItsOnOemApi mApi;
    CdmaCallTracker mCT;
    protected String mCarrierOtaSpNumSchema;
    CdmaSubscriptionSourceManager mCdmaSSM;
    int mCdmaSubscriptionSource = -1;
    private Registrant mEcmExitRespRegistrant;
    private final RegistrantList mEcmTimerResetRegistrants = new RegistrantList();
    private final RegistrantList mEriFileLoadedRegistrants = new RegistrantList();
    EriManager mEriManager;
    private String mEsn;
    private Runnable mExitEcmRunnable = new C00691();
    protected String mImei;
    protected String mImeiSv;
    protected boolean mIsPhoneInEcmState;
    private String mMeid;
    ArrayList<CdmaMmiCode> mPendingMmis = new ArrayList();
    Registrant mPostDialHandler;
    private int mPreferredNetworkType = UNKNOWN_NETWORK_MODE;
    RuimPhoneBookInterfaceManager mRuimPhoneBookInterfaceManager;
    CdmaServiceStateTracker mSST;
    RegistrantList mSsnRegistrants = new RegistrantList();
    PhoneSubInfo mSubInfo;
    private String mVmNumber = null;
    WakeLock mWakeLock;

    class C00691 implements Runnable {
        C00691() {
        }

        public void run() {
            CDMAPhone.this.exitEmergencyCallbackMode();
        }
    }

    static /* synthetic */ class C00702 {
        static final /* synthetic */ int[] $SwitchMap$com$android$internal$telephony$DctConstants$Activity = new int[Activity.values().length];
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
            try {
                $SwitchMap$com$android$internal$telephony$DctConstants$Activity[Activity.DATAIN.ordinal()] = 1;
            } catch (NoSuchFieldError e8) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$DctConstants$Activity[Activity.DATAOUT.ordinal()] = 2;
            } catch (NoSuchFieldError e9) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$DctConstants$Activity[Activity.DATAINANDOUT.ordinal()] = 3;
            } catch (NoSuchFieldError e10) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$DctConstants$Activity[Activity.DORMANT.ordinal()] = 4;
            } catch (NoSuchFieldError e11) {
            }
        }
    }

    public CDMAPhone(Context context, CommandsInterface ci, PhoneNotifier notifier) {
        super("CDMA", notifier, context, ci, false);
        initSstIcc();
        init(context, notifier);
    }

    public CDMAPhone(Context context, CommandsInterface ci, PhoneNotifier notifier, int phoneId) {
        super("CDMA", notifier, context, ci, false, phoneId);
        initSstIcc();
        init(context, notifier);
    }

    public CDMAPhone(Context context, CommandsInterface ci, PhoneNotifier notifier, boolean unitTestMode) {
        super("CDMA", notifier, context, ci, unitTestMode);
        initSstIcc();
        init(context, notifier);
    }

    protected void initSstIcc() {
        if (this.mCT == null) {
            this.mCT = new CdmaCallTracker(this);
        }
        if ("AP".equals(CscFeature.getInstance().getString("CscFeature_RIL_ConfigDrivenTypeForCtcMtrIR")) && ("DCGGS".equals("DGG") || "DCGS".equals("DGG"))) {
            this.mSST = new IRCdmaServiceStateTracker(this);
        } else {
            this.mSST = new CdmaServiceStateTracker(this);
        }
    }

    protected void init(Context context, PhoneNotifier notifier) {
        this.mCi.setPhoneType(2);
        if (this.mCT == null) {
            this.mCT = new CdmaCallTracker(this);
        }
        this.mCdmaSSM = CdmaSubscriptionSourceManager.getInstance(context, this.mCi, this, 27, null);
        this.mDcTracker = new DcTracker(this);
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
        this.mWakeLock = ((PowerManager) context.getSystemService("power")).newWakeLock(1, LOG_TAG);
        if (CscFeature.getInstance().getEnableStatus("CscFeature_Common_EnableItsOn")) {
            this.mApi = ItsOnOemApi.getInstance();
            this.mApi.initTelephony(context);
        }
        SystemProperties.set("gsm.current.phone-type", Integer.toString(2));
        this.mIsPhoneInEcmState = SystemProperties.get("ril.cdma.inecmmode", "false").equals("true");
        if (this.mIsPhoneInEcmState) {
            this.mCi.exitEmergencyCallbackMode(obtainMessage(26));
        }
        this.mCarrierOtaSpNumSchema = SystemProperties.get("ro.cdma.otaspnumschema", "");
        if (TextUtils.isEmpty(SystemProperties.get("gsm.sim.operator.numeric"))) {
            String operatorAlpha = SystemProperties.get("ro.cdma.home.operator.alpha");
            String operatorNumeric = SystemProperties.get(PROPERTY_CDMA_HOME_OPERATOR_NUMERIC);
            log("init: operatorAlpha='" + operatorAlpha + "' operatorNumeric='" + operatorNumeric + "'");
            if (this.mUiccController.getUiccCardApplication(this.mPhoneId, 1) == null) {
                log("init: APP_FAM_3GPP == NULL");
                if (!TextUtils.isEmpty(operatorAlpha)) {
                    log("init: set 'gsm.sim.operator.alpha' to operator='" + operatorAlpha + "'");
                    setSystemProperty("gsm.sim.operator.alpha", operatorAlpha);
                }
                if (!TextUtils.isEmpty(operatorNumeric)) {
                    log("init: set 'gsm.sim.operator.numeric' to operator='" + operatorNumeric + "'");
                    log("update icc_operator_numeric=" + operatorNumeric);
                    setSystemProperty("gsm.sim.operator.numeric", operatorNumeric);
                    SubscriptionController.getInstance().setMccMnc(operatorNumeric, getSubId());
                }
                setIsoCountryProperty(operatorNumeric);
            }
            if ("DCGG".equals("DGG") || "DCGS".equals("DGG") || "DCGGS".equals("DGG")) {
                operatorNumeric = "46003";
            }
            updateCurrentCarrierInProvider(operatorNumeric);
            return;
        }
        updateCurrentCarrierInProvider(SystemProperties.get(PROPERTY_CDMA_HOME_OPERATOR_NUMERIC));
    }

    public void dispose() {
        synchronized (PhoneProxy.lockForRadioTechnologyChange) {
            super.dispose();
            log("dispose");
            unregisterForRuimRecordEvents();
            this.mCi.unregisterForAvailable(this);
            this.mCi.unregisterForOffOrNotAvailable(this);
            this.mCi.unregisterForOn(this);
            this.mSST.unregisterForNetworkAttached(this);
            this.mCi.unSetOnSuppServiceNotification(this);
            this.mCi.unregisterForExitEmergencyCallbackMode(this);
            removeCallbacks(this.mExitEcmRunnable);
            this.mPendingMmis.clear();
            this.mCT.dispose();
            this.mDcTracker.dispose();
            this.mSST.dispose();
            this.mCdmaSSM.dispose(this);
            this.mRuimPhoneBookInterfaceManager.dispose();
            this.mSubInfo.dispose();
            this.mEriManager.dispose();
        }
    }

    public void removeReferences() {
        log("removeReferences");
        this.mRuimPhoneBookInterfaceManager = null;
        this.mSubInfo = null;
        this.mCT = null;
        this.mSST = null;
        this.mEriManager = null;
        this.mExitEcmRunnable = null;
        super.removeReferences();
    }

    protected void finalize() {
        Rlog.d(LOG_TAG, "CDMAPhone finalized");
        if (this.mWakeLock.isHeld()) {
            Rlog.e(LOG_TAG, "UNEXPECTED; mWakeLock is held when finalizing.");
            this.mWakeLock.release();
        }
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

    public CallTracker getCallTracker() {
        return this.mCT;
    }

    public PhoneConstants.State getState() {
        return this.mCT.mState;
    }

    public ServiceStateTracker getServiceStateTracker() {
        return this.mSST;
    }

    public int getPhoneType() {
        return 2;
    }

    public boolean canTransfer() {
        Rlog.e(LOG_TAG, "canTransfer: not possible in CDMA");
        return false;
    }

    public Call getRingingCall() {
        ImsPhone imPhone = this.mImsPhone;
        if (this.mCT.mRingingCall != null && this.mCT.mRingingCall.isRinging()) {
            return this.mCT.mRingingCall;
        }
        if (imPhone != null) {
            return imPhone.getRingingCall();
        }
        return this.mCT.mRingingCall;
    }

    public void setMute(boolean muted) {
        this.mCT.setMute(muted);
    }

    public boolean getMute() {
        return this.mCT.getMute();
    }

    public void conference() {
        if (this.mImsPhone != null && this.mImsPhone.canConference()) {
            log("conference() - delegated to IMS phone");
            this.mImsPhone.conference();
        } else if (canConference()) {
            this.mCT.conference();
        } else {
            Rlog.e(LOG_TAG, "conference: not possible in CDMA");
        }
    }

    public void enableEnhancedVoicePrivacy(boolean enable, Message onComplete) {
        this.mCi.setPreferredVoicePrivacy(enable, onComplete);
    }

    public void getEnhancedVoicePrivacy(Message onComplete) {
        this.mCi.getPreferredVoicePrivacy(onComplete);
    }

    public void clearDisconnected() {
        this.mCT.clearDisconnected();
    }

    public DataActivityState getDataActivityState() {
        DataActivityState ret = DataActivityState.NONE;
        if (this.mSST.getCurrentDataConnectionState() != 0) {
            return ret;
        }
        switch (C00702.$SwitchMap$com$android$internal$telephony$DctConstants$Activity[this.mDcTracker.getActivity().ordinal()]) {
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

    public Connection dial(String dialString, int videoState) throws CallStateException {
        return dial(dialString, null, videoState, 0, 1, null);
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
        return dialInternal(dialString, uusInfo, videoState, callType, callDomain, extras);
    }

    protected Connection dialInternal(String dialString, UUSInfo uusInfo, int videoState) throws CallStateException {
        return dialInternal(dialString, uusInfo, videoState, 0, 1, null);
    }

    protected Connection dialInternal(String dialString, UUSInfo uusInfo, int videoState, int callType, int callDomain, String[] extras) throws CallStateException {
        String newDialString;
        CallDetails callDetails = new CallDetails(callType, callDomain, extras);
        if (callDetails == null || !"unknown".equals(callDetails.getExtraValue("participants"))) {
            newDialString = PhoneNumberUtils.stripSeparators(dialString);
        } else {
            newDialString = dialString;
        }
        return this.mCT.dial(newDialString, 0, callDetails);
    }

    public Connection dial(String dialString, UUSInfo uusInfo, int videoState) throws CallStateException {
        throw new CallStateException("Sending UUS information NOT supported in CDMA!");
    }

    public boolean getMessageWaitingIndicator() {
        return getVoiceMessageCount() > 0;
    }

    public List<? extends MmiCode> getPendingMmiCodes() {
        return this.mPendingMmis;
    }

    public void registerForSuppServiceNotification(Handler h, int what, Object obj) {
        if (CscFeature.getInstance().getEnableStatus("CscFeature_RIL_SupportVolte")) {
            this.mSsnRegistrants.addUnique(h, what, obj);
            if (this.mSsnRegistrants.size() == 1) {
                this.mCi.setSuppServiceNotifications(true, null);
                return;
            }
            return;
        }
        Rlog.e(LOG_TAG, "method registerForSuppServiceNotification is NOT supported in CDMA!");
    }

    public CdmaCall getBackgroundCall() {
        return this.mCT.mBackgroundCall;
    }

    public boolean handleInCallMmiCommands(String dialString) {
        Rlog.e(LOG_TAG, "method handleInCallMmiCommands is NOT supported in CDMA!");
        return false;
    }

    boolean isInCall() {
        return getForegroundCall().getState().isAlive() || getBackgroundCall().getState().isAlive() || getRingingCall().getState().isAlive();
    }

    public void unregisterForSuppServiceNotification(Handler h) {
        if (CscFeature.getInstance().getEnableStatus("CscFeature_RIL_SupportVolte")) {
            this.mSsnRegistrants.remove(h);
            if (this.mSsnRegistrants.size() == 0) {
                this.mCi.setSuppServiceNotifications(false, null);
                return;
            }
            return;
        }
        Rlog.e(LOG_TAG, "method unregisterForSuppServiceNotification is NOT supported in CDMA!");
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

    public void holdCall() throws CallStateException {
        this.mCT.switchWaitingOrHoldingAndActive();
    }

    public String getIccSerialNumber() {
        IccRecords r = (IccRecords) this.mIccRecords.get();
        if (r == null) {
            r = this.mUiccController.getIccRecords(this.mPhoneId, 1);
        }
        return r != null ? r.getIccId() : null;
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

    public String getLine1Number() {
        if (!"DCG".equals("DGG") && !"DCGG".equals("DGG") && !"DCGS".equals("DGG") && !"DCGGS".equals("DGG") && !"CG".equals("DGG")) {
            return this.mSST.getMdnNumber();
        }
        IccRecords r = (IccRecords) this.mIccRecords.get();
        if (r instanceof RuimRecords) {
            return ((RuimRecords) r).getMdnNumber();
        }
        return "";
    }

    public String getCdmaPrlVersion() {
        return this.mSST.getPrlVersion();
    }

    public String getCdmaMin() {
        return this.mSST.getCdmaMin();
    }

    public boolean isMinInfoReady() {
        return this.mSST.isMinInfoReady();
    }

    public void getCallWaiting(Message onComplete) {
        this.mCi.queryCallWaiting(1, onComplete);
    }

    public void setRadioPower(boolean power) {
        this.mSST.setRadioPower(power);
    }

    public String getEsn() {
        return this.mEsn;
    }

    public String getRuimid() {
        return SystemProperties.get("ril.cdma.phone.id", "");
    }

    public String getMeid() {
        return this.mMeid;
    }

    public String getDeviceId() {
        String id = getMeid();
        if (id != null && !id.matches("^0*$")) {
            return id;
        }
        Rlog.d(LOG_TAG, "getDeviceId(): MEID is not initialized use ESN");
        return getEsn();
    }

    public String getDeviceSvn() {
        if ("CTC".equals(SystemProperties.get("ro.csc.sales_code"))) {
            return this.mImeiSv;
        }
        Rlog.d(LOG_TAG, "getDeviceSvn(): return 0");
        return "0";
    }

    public String getSubscriberId() {
        if (!"DCG".equals("DGG") && !"DCGG".equals("DGG") && !"DCGS".equals("DGG") && !"DCGGS".equals("DGG") && !"CG".equals("DGG")) {
            return this.mSST.getImsi();
        }
        IccRecords r = (IccRecords) this.mIccRecords.get();
        String icctype = getSystemProperty("ril.ICC_TYPE", "0");
        if (icctype == null || icctype.equals("")) {
            icctype = "0";
        }
        int type = Integer.parseInt(icctype);
        if (type != 3 && type != 4) {
            return null;
        }
        Rlog.d(LOG_TAG, "getSubscriberId type = " + type);
        if (r == null || !(r instanceof SIMRecords)) {
            return (r == null || ((RuimRecords) r).getIMSI_M() == null) ? null : ((RuimRecords) r).getIMSI_M();
        } else {
            return null;
        }
    }

    public String getGroupIdLevel1() {
        Rlog.e(LOG_TAG, "GID1 is not available in CDMA");
        return null;
    }

    public String getImei() {
        Rlog.e(LOG_TAG, "getImei() called for CDMAPhone");
        return this.mImei;
    }

    public boolean hasIsim() {
        Rlog.e(LOG_TAG, "hasIsim is not available in CDMA");
        return false;
    }

    public byte[] getPsismsc() {
        Rlog.e(LOG_TAG, "getPsismsc is called in CDMA");
        UiccCardApplication UsimUiccApplication = this.mUiccController.getUiccCardApplication(1);
        if (UsimUiccApplication != null) {
            IccRecords mUsimRecords = UsimUiccApplication.getIccRecords();
            if (mUsimRecords != null) {
                return mUsimRecords.getPsismsc();
            }
            Rlog.e(LOG_TAG, "mUsimRecords is null");
            return null;
        }
        Rlog.e(LOG_TAG, "UsimUiccApplication is null");
        return null;
    }

    public boolean canConference() {
        if (this.mImsPhone != null && this.mImsPhone.canConference()) {
            return true;
        }
        if (this.mCT != null && this.mCT.mForegroundCall != null && this.mCT.mForegroundCall.isImsCall()) {
            return this.mCT.canConference();
        }
        Rlog.e(LOG_TAG, "canConference: not possible in CDMA");
        return false;
    }

    public CellLocation getCellLocation() {
        CdmaCellLocation loc = this.mSST.mCellLoc;
        if (Secure.getInt(getContext().getContentResolver(), "location_mode", 0) != 0) {
            return loc;
        }
        CdmaCellLocation privateLoc = new CdmaCellLocation();
        privateLoc.setCellLocationData(loc.getBaseStationId(), Integer.MAX_VALUE, Integer.MAX_VALUE, loc.getSystemId(), loc.getNetworkId());
        privateLoc.setLteCellLocationData(loc.getLteTac(), loc.getLteCellId());
        return privateLoc;
    }

    public CdmaCall getForegroundCall() {
        return this.mCT.mForegroundCall;
    }

    public void setOnPostDialCharacter(Handler h, int what, Object obj) {
        this.mPostDialHandler = new Registrant(h, what, obj);
    }

    public boolean handlePinMmi(String dialString) {
        CdmaMmiCode mmi = CdmaMmiCode.newFromDialString(dialString, this, (UiccCardApplication) this.mUiccApplication.get());
        if (mmi == null) {
            Rlog.e(LOG_TAG, "Mmi is NULL!");
            return false;
        } else if (mmi.isPinPukCommand()) {
            this.mPendingMmis.add(mmi);
            this.mMmiRegistrants.notifyRegistrants(new AsyncResult(null, mmi, null));
            mmi.processCode();
            return true;
        } else {
            Rlog.e(LOG_TAG, "Unrecognized mmi!");
            return false;
        }
    }

    void onMMIDone(CdmaMmiCode mmi) {
        if (this.mPendingMmis.remove(mmi)) {
            this.mMmiCompleteRegistrants.notifyRegistrants(new AsyncResult(null, mmi, null));
        }
    }

    public void setLine1Number(String alphaTag, String number, Message onComplete) {
        Rlog.e(LOG_TAG, "setLine1Number: not possible in CDMA");
    }

    public void setCallWaiting(boolean enable, Message onComplete) {
        Rlog.e(LOG_TAG, "method setCallWaiting is NOT supported in CDMA!");
    }

    public void updateServiceLocation() {
        this.mSST.enableSingleLocationUpdate();
    }

    public void setDataRoamingEnabled(boolean enable) {
        this.mDcTracker.setDataOnRoamingEnabled(enable);
    }

    public String getSelectedApn() {
        return this.mDcTracker.getSelectedApn();
    }

    public void setSelectedApn() {
        this.mDcTracker.setSelectedApn();
    }

    public void registerForCdmaOtaStatusChange(Handler h, int what, Object obj) {
        this.mCi.registerForCdmaOtaProvision(h, what, obj);
    }

    public void unregisterForCdmaOtaStatusChange(Handler h) {
        this.mCi.unregisterForCdmaOtaProvision(h);
    }

    public void registerForSubscriptionInfoReady(Handler h, int what, Object obj) {
        this.mSST.registerForSubscriptionInfoReady(h, what, obj);
    }

    public void unregisterForSubscriptionInfoReady(Handler h) {
        this.mSST.unregisterForSubscriptionInfoReady(h);
    }

    public void setOnEcbModeExitResponse(Handler h, int what, Object obj) {
        this.mEcmExitRespRegistrant = new Registrant(h, what, obj);
    }

    public void unsetOnEcbModeExitResponse(Handler h) {
        this.mEcmExitRespRegistrant.clear();
    }

    public void registerForCallWaiting(Handler h, int what, Object obj) {
        this.mCT.registerForCallWaiting(h, what, obj);
    }

    public void unregisterForCallWaiting(Handler h) {
        this.mCT.unregisterForCallWaiting(h);
    }

    public void setLteBandMode(int bandMode, Message response) {
        this.mCi.setLteBandMode(bandMode, response);
    }

    public void getNeighboringCids(Message response) {
        if (response != null) {
            AsyncResult.forMessage(response).exception = new CommandException(Error.REQUEST_NOT_SUPPORTED);
            response.sendToTarget();
        }
    }

    public DataState getDataConnectionState(String apnType) {
        DataState ret = DataState.DISCONNECTED;
        if (this.mSST != null) {
            if (this.mSST.getCurrentDataConnectionState() == 0) {
                if (this.mDcTracker.isApnTypeEnabled(apnType) && this.mDcTracker.isApnTypeActive(apnType)) {
                    switch (C00702.$SwitchMap$com$android$internal$telephony$DctConstants$State[this.mDcTracker.getState(apnType).ordinal()]) {
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
            }
        } else {
            ret = DataState.DISCONNECTED;
        }
        log("getDataConnectionState apnType=" + apnType + " ret=" + ret);
        return ret;
    }

    public void sendUssdResponse(String ussdMessge) {
        Rlog.e(LOG_TAG, "sendUssdResponse: not possible in CDMA");
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

    public void sendBurstDtmf(String dtmfString, int on, int off, Message onComplete) {
        boolean check = true;
        for (int itr = 0; itr < dtmfString.length(); itr++) {
            if (!PhoneNumberUtils.is12Key(dtmfString.charAt(itr))) {
                Rlog.e(LOG_TAG, "sendDtmf called with invalid character '" + dtmfString.charAt(itr) + "'");
                check = false;
                break;
            }
        }
        if (this.mCT.mState == PhoneConstants.State.OFFHOOK && check) {
            this.mCi.sendBurstDtmf(dtmfString, on, off, onComplete);
        }
    }

    public void getAvailableNetworks(Message response) {
        Rlog.e(LOG_TAG, "getAvailableNetworks: not possible in CDMA");
    }

    public void setOutgoingCallerIdDisplay(int commandInterfaceCLIRMode, Message onComplete) {
        Rlog.e(LOG_TAG, "setOutgoingCallerIdDisplay: not possible in CDMA");
    }

    public void enableLocationUpdates() {
        this.mSST.enableLocationUpdates();
    }

    public void disableLocationUpdates() {
        this.mSST.disableLocationUpdates();
    }

    public void getDataCallList(Message response) {
        this.mCi.getDataCallList(response);
    }

    public boolean getDataRoamingEnabled() {
        return this.mDcTracker.getDataOnRoamingEnabled();
    }

    public void setDataEnabled(boolean enable) {
        this.mDcTracker.setDataEnabled(enable);
    }

    public boolean getDataEnabled() {
        return this.mDcTracker.getDataEnabled();
    }

    public void setVoiceMailNumber(String alphaTag, String voiceMailNumber, Message onComplete) {
        this.mVmNumber = voiceMailNumber;
        Message resp = obtainMessage(20, 0, 0, onComplete);
        IccRecords r = (IccRecords) this.mIccRecords.get();
        if (r != null) {
            r.setVoiceMailNumber(alphaTag, this.mVmNumber, resp);
        }
    }

    public String getVoiceMailNumber() {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(getContext());
        String number = sp.getString("vm_number_key_cdma" + getPhoneId(), null);
        if (TextUtils.isEmpty(number)) {
            number = sp.getString("vm_number_key_cdma", null);
        }
        if (TextUtils.isEmpty(number)) {
            String[] listArray = getContext().getResources().getStringArray(17236026);
            if (listArray != null && listArray.length > 0) {
                for (int i = 0; i < listArray.length; i++) {
                    if (!TextUtils.isEmpty(listArray[i])) {
                        String[] defaultVMNumberArray = listArray[i].split(";");
                        if (defaultVMNumberArray != null && defaultVMNumberArray.length > 0) {
                            if (defaultVMNumberArray.length != 1) {
                                if (defaultVMNumberArray.length == 2 && !TextUtils.isEmpty(defaultVMNumberArray[1]) && defaultVMNumberArray[1].equalsIgnoreCase(getGroupIdLevel1())) {
                                    number = defaultVMNumberArray[0];
                                    break;
                                }
                            }
                            number = defaultVMNumberArray[0];
                        }
                    }
                }
            }
        }
        if (!TextUtils.isEmpty(number)) {
            return number;
        }
        if (getContext().getResources().getBoolean(17956940)) {
            return getLine1Number();
        }
        return "*86";
    }

    public int getVoiceMessageCount() {
        int voicemailCount;
        IccRecords r = (IccRecords) this.mIccRecords.get();
        if (r != null) {
            voicemailCount = r.getVoiceMessageCount();
        } else {
            voicemailCount = 0;
        }
        if (voicemailCount == 0) {
            return PreferenceManager.getDefaultSharedPreferences(getContext()).getInt(VM_COUNT_CDMA + getPhoneId(), 0);
        }
        return voicemailCount;
    }

    public String getVoiceMailAlphaTag() {
        String ret = "";
        if (ret == null || ret.length() == 0) {
            return this.mContext.getText(17039364).toString();
        }
        return ret;
    }

    public void getCallForwardingOption(int commandInterfaceCFReason, Message onComplete) {
        Rlog.e(LOG_TAG, "getCallForwardingOption: not possible in CDMA");
    }

    public void setCallForwardingOption(int commandInterfaceCFAction, int commandInterfaceCFReason, String dialingNumber, int timerSeconds, Message onComplete) {
        Rlog.e(LOG_TAG, "setCallForwardingOption: not possible in CDMA");
    }

    public void getOutgoingCallerIdDisplay(Message onComplete) {
        Rlog.e(LOG_TAG, "getOutgoingCallerIdDisplay: not possible in CDMA");
    }

    public boolean getCallForwardingIndicator() {
        Rlog.e(LOG_TAG, "getCallForwardingIndicator: not possible in CDMA");
        return false;
    }

    public void explicitCallTransfer() {
        Rlog.e(LOG_TAG, "explicitCallTransfer: not possible in CDMA");
    }

    public String getLine1AlphaTag() {
        Rlog.e(LOG_TAG, "getLine1AlphaTag: not possible in CDMA");
        return null;
    }

    void notifyPhoneStateChanged() {
        this.mNotifier.notifyPhoneState(this);
    }

    void notifyPreciseCallStateChanged() {
        super.notifyPreciseCallStateChangedP();
    }

    void notifyServiceStateChanged(ServiceState ss) {
        super.notifyServiceStateChangedP(ss);
    }

    void notifyLocationChanged() {
        this.mNotifier.notifyCellLocation(this);
    }

    public void notifyNewRingingConnection(Connection c) {
        super.notifyNewRingingConnectionP(c);
    }

    void notifyDisconnect(Connection cn) {
        this.mDisconnectRegistrants.notifyResult(cn);
        this.mNotifier.notifyDisconnectCause(cn.getDisconnectCause(), cn.getPreciseDisconnectCause());
    }

    void notifyUnknownConnection(Connection connection) {
        this.mUnknownConnectionRegistrants.notifyResult(connection);
    }

    public boolean isInEmergencyCall() {
        return this.mCT.isInEmergencyCall();
    }

    public boolean isInEcm() {
        return this.mIsPhoneInEcmState;
    }

    void sendEmergencyCallbackModeChange() {
        Intent intent = new Intent("android.intent.action.EMERGENCY_CALLBACK_MODE_CHANGED");
        intent.putExtra("phoneinECMState", this.mIsPhoneInEcmState);
        SubscriptionManager.putPhoneIdAndSubIdExtra(intent, getPhoneId());
        ActivityManagerNative.broadcastStickyIntent(intent, null, -1);
        Rlog.d(LOG_TAG, "sendEmergencyCallbackModeChange");
    }

    void sendEmergencyCallbackModeChangeIms() {
        Intent intent = new Intent("android.intent.action.EMERGENCY_CALLBACK_MODE_CHANGED_IMS");
        intent.putExtra("phoneinECMState", false);
        ActivityManagerNative.broadcastStickyIntent(intent, null, -1);
        Rlog.d(LOG_TAG, "sendEmergencyCallbackModeChangeIms : false");
    }

    public void exitEmergencyCallbackMode() {
        if (this.mWakeLock.isHeld()) {
            this.mWakeLock.release();
        }
        if (CscFeature.getInstance().getEnableStatus("CscFeature_RIL_SupportVolte") && "VZW-CDMA".equals("")) {
            sendEmergencyCallbackModeChangeIms();
            sendMessageDelayed(obtainMessage(CallFailCause.CDMA_PREEMPTED), 1500);
            return;
        }
        this.mCi.exitEmergencyCallbackMode(obtainMessage(26));
    }

    private void handleEnterEmergencyCallbackMode(Message msg) {
        Rlog.d(LOG_TAG, "handleEnterEmergencyCallbackMode,mIsPhoneInEcmState= " + this.mIsPhoneInEcmState);
        if (!this.mIsPhoneInEcmState) {
            this.mIsPhoneInEcmState = true;
            if (CscFeature.getInstance().getEnableStatus("CscFeature_Common_EnableItsOn")) {
                this.mApi.setEmergencyMode(true);
            }
            sendEmergencyCallbackModeChange();
            setSystemProperty("ril.cdma.inecmmode", "true");
            postDelayed(this.mExitEcmRunnable, SystemProperties.getLong("ro.cdma.ecmexittimer", 300000));
            this.mWakeLock.acquire();
        }
    }

    private void handleExitEmergencyCallbackMode(Message msg) {
        AsyncResult ar = msg.obj;
        Rlog.d(LOG_TAG, "handleExitEmergencyCallbackMode,ar.exception , mIsPhoneInEcmState " + ar.exception + this.mIsPhoneInEcmState);
        removeCallbacks(this.mExitEcmRunnable);
        if (CscFeature.getInstance().getEnableStatus("CscFeature_Common_EnableItsOn")) {
            this.mApi.setEmergencyMode(false);
        }
        if (this.mEcmExitRespRegistrant != null) {
            this.mEcmExitRespRegistrant.notifyRegistrant(ar);
        }
        if (ar.exception == null) {
            if (this.mIsPhoneInEcmState) {
                this.mIsPhoneInEcmState = false;
                setSystemProperty("ril.cdma.inecmmode", "false");
            }
            sendEmergencyCallbackModeChange();
            this.mDcTracker.setInternalDataEnabled(true);
        }
    }

    void handleTimerInEmergencyCallbackMode(int action) {
        switch (action) {
            case 0:
                postDelayed(this.mExitEcmRunnable, SystemProperties.getLong("ro.cdma.ecmexittimer", 300000));
                this.mEcmTimerResetRegistrants.notifyResult(Boolean.FALSE);
                return;
            case 1:
                removeCallbacks(this.mExitEcmRunnable);
                this.mEcmTimerResetRegistrants.notifyResult(Boolean.TRUE);
                return;
            default:
                Rlog.e(LOG_TAG, "handleTimerInEmergencyCallbackMode, unsupported action " + action);
                return;
        }
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

    public void handleMessage(Message msg) {
        if (this.mIsTheCurrentActivePhone) {
            int slotId;
            AsyncResult ar;
            switch (msg.what) {
                case 1:
                    this.mCi.getBasebandVersion(obtainMessage(6));
                    this.mCi.getDeviceIdentity(obtainMessage(21));
                    return;
                case 2:
                    Rlog.d(LOG_TAG, "Event EVENT_SSN Received");
                    if (CscFeature.getInstance().getEnableStatus("CscFeature_RIL_SupportVolte")) {
                        this.mSsnRegistrants.notifyRegistrants((AsyncResult) msg.obj);
                        return;
                    }
                    return;
                case 5:
                    Rlog.d(LOG_TAG, "Event EVENT_RADIO_ON Received");
                    handleCdmaSubscriptionSource(this.mCdmaSSM.getCdmaSubscriptionSource());
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
                case 8:
                    Rlog.d(LOG_TAG, "Event EVENT_RADIO_OFF_OR_NOT_AVAILABLE Received");
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
                case 19:
                    Rlog.d(LOG_TAG, "Event EVENT_REGISTERED_TO_NETWORK Received");
                    return;
                case 20:
                    ar = (AsyncResult) msg.obj;
                    if (IccException.class.isInstance(ar.exception)) {
                        storeVoiceMailNumber(this.mVmNumber);
                        ar.exception = null;
                    }
                    Message onComplete = ar.userObj;
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
                        Rlog.d(LOG_TAG, "EVENT_GET_DEVICE_IDENTITY_DONE");
                        return;
                    }
                    return;
                case 22:
                    Rlog.d(LOG_TAG, "Event EVENT_RUIM_RECORDS_LOADED Received");
                    updateCurrentCarrierInProvider();
                    log("notifyMessageWaitingChanged");
                    this.mNotifier.notifyMessageWaitingChanged(this);
                    return;
                case 23:
                    Rlog.d(LOG_TAG, "Event EVENT_NV_READY Received");
                    if (CscFeature.getInstance().getEnableStatus("CscFeature_Common_EnableSprintExtension")) {
                        Intent sprintIntent = new Intent("com.sec.sprextension.PHONEINFO_STARTED");
                        sprintIntent.setClassName("com.sec.sprextension", "com.sec.sprextension.AndroidSprintExtensionService");
                        if (this.mContext.startService(sprintIntent) == null) {
                            sprintIntent = new Intent("com.sec.sprextension.PHONEINFO_STARTED");
                            sprintIntent.setClassName("com.sec.sprextension.phoneinfo", "com.sec.sprextension.phoneinfo.PhoneInfoService");
                            this.mContext.startService(sprintIntent);
                        }
                    }
                    if (!"LGT".equals("")) {
                        prepareEri();
                    }
                    log("notifyMessageWaitingChanged");
                    this.mNotifier.notifyMessageWaitingChanged(this);
                    return;
                case 25:
                    handleEnterEmergencyCallbackMode(msg);
                    return;
                case 26:
                    handleExitEmergencyCallbackMode(msg);
                    return;
                case WspTypeDecoder.WSP_HEADER_IF_UNMODIFIED_SINCE /*27*/:
                    Rlog.d(LOG_TAG, "EVENT_CDMA_SUBSCRIPTION_SOURCE_CHANGED");
                    handleCdmaSubscriptionSource(this.mCdmaSSM.getCdmaSubscriptionSource());
                    return;
                case WspTypeDecoder.WSP_HEADER_LAST_MODIFIED /*29*/:
                    processIccRecordEvents(((Integer) ((AsyncResult) msg.obj).result).intValue());
                    return;
                case CallFailCause.CDMA_ACCESS_FAILURE /*1006*/:
                    log("Service state changed for ecc number");
                    setEmergencyNumbers();
                    return;
                case CallFailCause.CDMA_PREEMPTED /*1007*/:
                    log("Event EVENT_EXIT_EMERGENCY_CALLBACK_INTERNAL Received");
                    this.mCi.exitEmergencyCallbackMode(obtainMessage(26));
                    return;
                case 2001:
                    if ("DCG".equals("DGG") || "DCGG".equals("DGG") || "DCGS".equals("DGG") || "DCGGS".equals("DGG") || "CG".equals("DGG")) {
                        ar = (AsyncResult) msg.obj;
                        if (ar.exception == null) {
                            Rlog.d(LOG_TAG, "ar.result" + ar.result);
                            int data = ((Integer) ar.result).intValue();
                            Rlog.e(LOG_TAG, "data" + data);
                            if (data == 0) {
                                Rlog.d(LOG_TAG, "SIM_POWER_DONE is sent");
                                this.mContext.sendBroadcast(new Intent("SIM_POWER_DONE"));
                                return;
                            }
                            Rlog.d(LOG_TAG, "SIM_POWER_DONE is not sent");
                            return;
                        }
                        return;
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
        return this.mUiccController.getUiccCardApplication(this.mPhoneId, 2);
    }

    protected void onUpdateIccAvailability() {
        if (this.mUiccController != null) {
            UiccCardApplication newUiccApplication = getUiccCardApplication();
            if (newUiccApplication == null) {
                log("can't find 3GPP2 application; trying APP_FAM_3GPP");
                newUiccApplication = this.mUiccController.getUiccCardApplication(this.mPhoneId, 1);
            }
            UiccCardApplication app = (UiccCardApplication) this.mUiccApplication.get();
            if (app != newUiccApplication) {
                if (app != null) {
                    log("Removing stale icc objects.");
                    if (this.mIccRecords.get() != null) {
                        unregisterForRuimRecordEvents();
                        this.mRuimPhoneBookInterfaceManager.updateIccRecords(null);
                    }
                    this.mIccRecords.set(null);
                    this.mUiccApplication.set(null);
                }
                if (newUiccApplication != null) {
                    log("New Uicc application found");
                    this.mUiccApplication.set(newUiccApplication);
                    this.mIccRecords.set(newUiccApplication.getIccRecords());
                    registerForRuimRecordEvents();
                    this.mRuimPhoneBookInterfaceManager.updateIccRecords((IccRecords) this.mIccRecords.get());
                }
            }
        }
    }

    private void processIccRecordEvents(int eventCode) {
        switch (eventCode) {
            case 0:
                notifyMessageWaitingIndicator();
                return;
            default:
                Rlog.e(LOG_TAG, "Unknown icc records event code " + eventCode);
                return;
        }
    }

    private void handleCdmaSubscriptionSource(int newSubscriptionSource) {
        if (newSubscriptionSource != this.mCdmaSubscriptionSource) {
            this.mCdmaSubscriptionSource = newSubscriptionSource;
            if (newSubscriptionSource == 1) {
                sendMessage(obtainMessage(23));
            }
        }
    }

    public PhoneSubInfo getPhoneSubInfo() {
        return this.mSubInfo;
    }

    public IccPhoneBookInterfaceManager getIccPhoneBookInterfaceManager() {
        return this.mRuimPhoneBookInterfaceManager;
    }

    public void registerForEriFileLoaded(Handler h, int what, Object obj) {
        this.mEriFileLoadedRegistrants.add(new Registrant(h, what, obj));
    }

    public void unregisterForEriFileLoaded(Handler h) {
        this.mEriFileLoadedRegistrants.remove(h);
    }

    public void setSystemProperty(String property, String value) {
        TelephonyManager.setTelephonyProperty(property, getSubId(), value);
    }

    public String getSystemProperty(String property, String defValue) {
        return TelephonyManager.getTelephonyProperty(property, getSubId(), defValue);
    }

    public void activateCellBroadcastSms(int activate, Message response) {
        Rlog.e(LOG_TAG, "[CDMAPhone] activateCellBroadcastSms() is obsolete; use SmsManager");
        response.sendToTarget();
    }

    public void getCellBroadcastSmsConfig(Message response) {
        Rlog.e(LOG_TAG, "[CDMAPhone] getCellBroadcastSmsConfig() is obsolete; use SmsManager");
        response.sendToTarget();
    }

    public void setCellBroadcastSmsConfig(int[] configValuesArray, Message response) {
        Rlog.e(LOG_TAG, "[CDMAPhone] setCellBroadcastSmsConfig() is obsolete; use SmsManager");
        response.sendToTarget();
    }

    public boolean needsOtaServiceProvisioning() {
        if ("USC-CDMA".equals("")) {
            if (this.mSST.getOtasp() == 3 || this.mSST.getOtasp() == 4) {
                return false;
            }
            return true;
        } else if (this.mSST.getOtasp() == 3) {
            return false;
        } else {
            return true;
        }
    }

    private static boolean isIs683OtaSpDialStr(String dialStr) {
        if (dialStr.length() != 4) {
            switch (extractSelCodeFromOtaSpNum(dialStr)) {
                case 0:
                case 1:
                case 2:
                case 3:
                case 4:
                case 5:
                case 6:
                case 7:
                    return true;
                default:
                    return false;
            }
        } else if (dialStr.equals(IS683A_FEATURE_CODE)) {
            return true;
        } else {
            return false;
        }
    }

    private static int extractSelCodeFromOtaSpNum(String dialStr) {
        int dialStrLen = dialStr.length();
        int sysSelCodeInt = -1;
        if (dialStr.regionMatches(0, IS683A_FEATURE_CODE, 0, 4) && dialStrLen >= 6) {
            sysSelCodeInt = Integer.parseInt(dialStr.substring(4, 6));
        }
        Rlog.d(LOG_TAG, "extractSelCodeFromOtaSpNum " + sysSelCodeInt);
        return sysSelCodeInt;
    }

    private static boolean checkOtaSpNumBasedOnSysSelCode(int sysSelCodeInt, String[] sch) {
        try {
            int selRc = Integer.parseInt(sch[1]);
            int i = 0;
            while (i < selRc) {
                if (!(TextUtils.isEmpty(sch[i + 2]) || TextUtils.isEmpty(sch[i + 3]))) {
                    int selMin = Integer.parseInt(sch[i + 2]);
                    int selMax = Integer.parseInt(sch[i + 3]);
                    if (sysSelCodeInt >= selMin && sysSelCodeInt <= selMax) {
                        return true;
                    }
                }
                i++;
            }
            return false;
        } catch (NumberFormatException ex) {
            Rlog.e(LOG_TAG, "checkOtaSpNumBasedOnSysSelCode, error", ex);
            return false;
        }
    }

    private boolean isCarrierOtaSpNum(String dialStr) {
        boolean isOtaSpNum = false;
        int sysSelCodeInt = extractSelCodeFromOtaSpNum(dialStr);
        if (sysSelCodeInt == -1) {
            return 0;
        }
        if (TextUtils.isEmpty(this.mCarrierOtaSpNumSchema)) {
            Rlog.d(LOG_TAG, "isCarrierOtaSpNum,ota schema pattern empty");
        } else {
            Matcher m = pOtaSpNumSchema.matcher(this.mCarrierOtaSpNumSchema);
            Rlog.d(LOG_TAG, "isCarrierOtaSpNum,schema" + this.mCarrierOtaSpNumSchema);
            if (m.find()) {
                String[] sch = pOtaSpNumSchema.split(this.mCarrierOtaSpNumSchema);
                if (TextUtils.isEmpty(sch[0]) || !sch[0].equals("SELC")) {
                    if (TextUtils.isEmpty(sch[0]) || !sch[0].equals("FC")) {
                        Rlog.d(LOG_TAG, "isCarrierOtaSpNum,ota schema not supported" + sch[0]);
                    } else {
                        if (dialStr.regionMatches(0, sch[2], 0, Integer.parseInt(sch[1]))) {
                            isOtaSpNum = true;
                        } else {
                            Rlog.d(LOG_TAG, "isCarrierOtaSpNum,not otasp number");
                        }
                    }
                } else if (sysSelCodeInt != -1) {
                    isOtaSpNum = checkOtaSpNumBasedOnSysSelCode(sysSelCodeInt, sch);
                } else {
                    Rlog.d(LOG_TAG, "isCarrierOtaSpNum,sysSelCodeInt is invalid");
                }
            } else {
                Rlog.d(LOG_TAG, "isCarrierOtaSpNum,ota schema pattern not right" + this.mCarrierOtaSpNumSchema);
            }
        }
        return isOtaSpNum;
    }

    public boolean isOtaSpNumber(String dialStr) {
        if ("VZW-CDMA".equals("") || "LRA-CDMA".equals("")) {
            return super.isOtaSpNumber(dialStr);
        }
        boolean isOtaSpNum = false;
        String dialableStr = PhoneNumberUtils.extractNetworkPortionAlt(dialStr);
        if (dialableStr != null) {
            isOtaSpNum = isIs683OtaSpDialStr(dialableStr);
            if (!isOtaSpNum) {
                isOtaSpNum = isCarrierOtaSpNum(dialableStr);
            }
        }
        Rlog.d(LOG_TAG, "isOtaSpNumber " + isOtaSpNum);
        return isOtaSpNum;
    }

    public int getCdmaEriIconIndex() {
        return getServiceState().getCdmaEriIconIndex();
    }

    public int getCdmaEriIconMode() {
        return getServiceState().getCdmaEriIconMode();
    }

    public String getCdmaEriText() {
        return this.mEriManager.getCdmaEriText(getServiceState().getCdmaRoamingIndicator(), getServiceState().getCdmaDefaultRoamingIndicator());
    }

    private void storeVoiceMailNumber(String number) {
        Editor editor = PreferenceManager.getDefaultSharedPreferences(getContext()).edit();
        editor.putString("vm_number_key_cdma" + getPhoneId(), number);
        editor.apply();
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
            loge("setIsoCountryProperty: countryCodeForMcc error", ex);
        } catch (StringIndexOutOfBoundsException ex2) {
            loge("setIsoCountryProperty: countryCodeForMcc error", ex2);
        }
        log("setIsoCountryProperty: set 'gsm.sim.operator.iso-country' to iso=" + iso);
        setSystemProperty("gsm.sim.operator.iso-country", iso);
    }

    boolean updateCurrentCarrierInProvider(String operatorNumeric) {
        log("CDMAPhone: updateCurrentCarrierInProvider called");
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

    boolean updateCurrentCarrierInProvider() {
        return true;
    }

    public void prepareEri() {
        if (this.mEriManager == null) {
            Rlog.e(LOG_TAG, "PrepareEri: Trying to access stale objects");
            return;
        }
        this.mEriManager.loadEriFile();
        if (this.mEriManager.isEriFileLoaded()) {
            log("ERI read, notify registrants");
            this.mEriFileLoadedRegistrants.notifyRegistrants();
        }
    }

    public boolean isEriFileLoaded() {
        return this.mEriManager.isEriFileLoaded();
    }

    protected void registerForRuimRecordEvents() {
        IccRecords r = (IccRecords) this.mIccRecords.get();
        if (r != null) {
            r.registerForRecordsEvents(this, 29, null);
            r.registerForRecordsLoaded(this, 22, null);
        }
    }

    protected void unregisterForRuimRecordEvents() {
        IccRecords r = (IccRecords) this.mIccRecords.get();
        if (r != null) {
            r.unregisterForRecordsEvents(this);
            r.unregisterForRecordsLoaded(this);
        }
    }

    protected void log(String s) {
        Rlog.d(LOG_TAG, s);
    }

    protected void loge(String s, Exception e) {
        Rlog.e(LOG_TAG, s, e);
    }

    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        pw.println("CDMAPhone extends:");
        super.dump(fd, pw, args);
        pw.println(" mVmNumber=" + this.mVmNumber);
        pw.println(" mCT=" + this.mCT);
        pw.println(" mSST=" + this.mSST);
        pw.println(" mCdmaSSM=" + this.mCdmaSSM);
        pw.println(" mPendingMmis=" + this.mPendingMmis);
        pw.println(" mRuimPhoneBookInterfaceManager=" + this.mRuimPhoneBookInterfaceManager);
        pw.println(" mCdmaSubscriptionSource=" + this.mCdmaSubscriptionSource);
        pw.println(" mSubInfo=" + this.mSubInfo);
        pw.println(" mEriManager=" + this.mEriManager);
        pw.println(" mWakeLock=" + this.mWakeLock);
        pw.println(" mIsPhoneInEcmState=" + this.mIsPhoneInEcmState);
        pw.println(" mImei=" + this.mImei);
        pw.println(" mImeiSv=" + this.mImeiSv);
        pw.println(" mEsn=" + this.mEsn);
        pw.println(" mMeid=" + this.mMeid);
        pw.println(" mCarrierOtaSpNumSchema=" + this.mCarrierOtaSpNumSchema);
        pw.println(" getCdmaEriIconIndex()=" + getCdmaEriIconIndex());
        pw.println(" getCdmaEriIconMode()=" + getCdmaEriIconMode());
        pw.println(" getCdmaEriText()=" + getCdmaEriText());
        pw.println(" isMinInfoReady()=" + isMinInfoReady());
        pw.println(" isCspPlmnEnabled()=" + isCspPlmnEnabled());
    }

    public boolean getSMSavailable() {
        return true;
    }

    public boolean getSMSPavailable() {
        return true;
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

    public int getDataServiceState() {
        if (this.mSST == null) {
            return 1;
        }
        return this.mSST.getCurrentDataConnectionState();
    }

    public String getSktImsiM() {
        Rlog.e(LOG_TAG, "SKT IMSI_M is not available in CDMA");
        return null;
    }

    public String getSktIrm() {
        Rlog.e(LOG_TAG, "SKT IRM is not available in CDMA");
        return null;
    }

    public void getPreferredNetworkList(Message response) {
        Rlog.e(LOG_TAG, "method getPreferredNetworkList  is NOT supported in CDMA!");
    }

    public void setPreferredNetworkList(int index, String operator, String plmn, int gsmAct, int gsmCompactAct, int utranAct, int mode, Message response) {
        Rlog.e(LOG_TAG, "method setPreferredNetworkList is NOT supported in CDMA!");
    }

    public void getCallForwardingOption(int commandInterfaceCFReason, String dialingNumber, int serviceClass, Message onComplete) {
        Rlog.e(LOG_TAG, "getCallForwardingOption: not possible in CDMA");
    }

    public void setCallForwardingOption(int commandInterfaceCFReason, int commandInterfaceCFAction, String dialingNumber, int timerSeconds, int serviceClass, Message onComplete) {
        Rlog.e(LOG_TAG, "setCallForwardingOption: not possible in CDMA");
    }

    public void getCallBarringOption(String commandInterfacecbFlavour, Message onComplete) {
        Rlog.e(LOG_TAG, "getCallBarringOption: not possible in CDMA");
    }

    public void getCallBarringOption(String commandInterfacecbFlavour, String password, int serviceClass, Message onComplete) {
        Rlog.e(LOG_TAG, "getCallBarringOption: not possible in CDMA");
    }

    public boolean setCallBarringOption(boolean cbAction, String commandInterfacecbFlavour, String password, Message onComplete) {
        Rlog.e(LOG_TAG, "setCallBarringOption: not possible in CDMA");
        return false;
    }

    public boolean setCallBarringOption(boolean cbAction, String commandInterfacecbFlavour, String password, int serviceClass, Message onComplete) {
        Rlog.e(LOG_TAG, "setCallBarringOption: not possible in CDMA");
        return false;
    }

    public boolean changeBarringPassword(String facility, String oldPwd, String newPwd, Message onComplete) {
        Rlog.e(LOG_TAG, "changeBarringPassword: not possible in CDMA");
        return false;
    }

    public boolean changeBarringPassword(String facility, String oldPwd, String newPwd, String newPwdAgain, Message onComplete) {
        Rlog.e(LOG_TAG, "changeBarringPassword: not possible in CDMA");
        return false;
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

    public void invokeOemRilRequestStrings(String[] strings, Message response) {
        if (strings[0].equals("setEmergencyNumbers")) {
            saveEmergencyCallNumberSpec(strings[1]);
        } else if (strings[0].equals("loadEmergencyCallNumberSpec")) {
            response.obj = loadEmergencyCallNumberSpec();
        } else if (strings[0].equals("getVideoCallForwardingIndicator")) {
            Rlog.e(LOG_TAG, "getVideoCallForwardingIndicator is NOT supported in CDMA!");
            response.obj = new Boolean(false);
        } else {
            super.invokeOemRilRequestStrings(strings, response);
        }
    }

    public boolean IsInternationalRoaming() {
        return this.mEriManager.IsInternationalRoaming(getServiceState().getCdmaRoamingIndicator(), getServiceState().getCdmaDefaultRoamingIndicator());
    }

    public boolean IsDomesticRoaming() {
        return this.mEriManager.IsDomesticRoaming(getServiceState().getCdmaRoamingIndicator(), getServiceState().getCdmaDefaultRoamingIndicator());
    }

    public String getHandsetInfo(String ID) {
        return this.mSST.getHandsetInfo(ID);
    }

    public String[] getSponImsi() {
        IccRecords r = (IccRecords) this.mIccRecords.get();
        if (r != null) {
            return r.getSponImsi();
        }
        return null;
    }

    void notifySuppServiceFailed(SuppService code) {
        this.mSuppServiceFailedRegistrants.notifyResult(code);
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
            String emergencyNumbers = null;
            String PROP_ECC_LIST = "ril.ecclist";
            boolean withSIM = true;
            Rlog.d(LOG_TAG, "setEmergencyNumbers: mPrevSs=[" + this.mPrevSs + "], ss=[" + ss + "]");
            Rlog.d(LOG_TAG, "setEmergencyNumbers: customerSpec=" + customerSpec);
            if (simState == 1 || simState == 0) {
                withSIM = false;
            }
            Rlog.d(LOG_TAG, "setEmergencyNumbers: withSIM=" + withSIM);
            if (withSIM) {
                emergencyNumbers = this.mEccNums;
                Rlog.d(LOG_TAG, "setEmergencyNumbers: ECC from SIM=" + emergencyNumbers);
            }
            String specToUpdate;
            if (customerSpec != null) {
                saveEmergencyCallNumberSpec(customerSpec);
                specToUpdate = customerSpec;
            } else {
                specToUpdate = loadEmergencyCallNumberSpec();
            }
            String emergencyNumbersForOperator = EccTable.emergencyNumbersForPLMN(op, withSIM);
            log("setEmergencyNumbers: ECC for operator=" + emergencyNumbersForOperator);
            if (emergencyNumbers == null || emergencyNumbers.equals("")) {
                emergencyNumbers = emergencyNumbersForOperator;
            } else {
                emergencyNumbers = emergencyNumbers + "," + emergencyNumbersForOperator;
            }
            log("setEmergencyNumbers: emergencyNumbers=" + emergencyNumbers);
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
            this.mPrevSs = new ServiceState(ss);
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

    public boolean getDualSimSlotActivationState() {
        long subId = SubscriptionController.getInstance().getSubIdUsingPhoneId(getPhoneId());
        int subStatus = SubscriptionController.getInstance().getSubState(subId);
        Rlog.d(LOG_TAG, "getDualSimSlotActivationState subStatus:" + subStatus + "phoneId:" + getPhoneId() + ", subId:" + subId);
        if (subStatus == 1) {
            return true;
        }
        return false;
    }

    public void SimSlotOnOff(int on) {
        Rlog.d(LOG_TAG, "CDMAPhoneSimSlotOnOff on : " + on);
        int uid = Binder.getCallingUid();
        if (uid == CallFailCause.CDMA_DROP || uid == 1000) {
            switch (on) {
                case 0:
                    if ("DCGS".equals("DGG") || "DCGGS".equals("DGG")) {
                        this.mSST.SimSlotOnOff(on, obtainMessage(2001));
                        setSystemProperty("gsm.sim.currentcardstatus", "3");
                        return;
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
        Rlog.d(LOG_TAG, "getLine1NumberType() SimType : " + SimType);
        IccRecords r = (IccRecords) this.mIccRecords.get();
        switch (SimType) {
            case 0:
                if (r instanceof RuimRecords) {
                    return ((RuimRecords) r).getMdnNumber();
                }
                return "";
            case 1:
                return r != null ? r.getMsisdnNumber() : "";
            default:
                return "";
        }
    }

    public String getSubscriberIdType(int SimType) {
        Rlog.d(LOG_TAG, "getSubscriberIdType() SimType : " + SimType);
        IccRecords r = (IccRecords) this.mIccRecords.get();
        switch (SimType) {
            case 0:
                if (!(r instanceof RuimRecords)) {
                    return "";
                }
                if (((RuimRecords) r).getIMSI_M() != null) {
                    return ((RuimRecords) r).getIMSI_M();
                }
                return "";
            case 1:
                return r != null ? r.getIMSI() : "";
            default:
                return "";
        }
    }

    public boolean getMsisdnavailable() {
        IccRecords r = (IccRecords) this.mIccRecords.get();
        if (r != null) {
            return ((RuimRecords) r).isAvailableMSISDN;
        }
        return false;
    }

    public boolean getMdnavailable() {
        IccRecords r = (IccRecords) this.mIccRecords.get();
        if (r != null) {
            return ((RuimRecords) r).isAvailableMDN;
        }
        return false;
    }

    public void setGbaBootstrappingParams(byte[] rand, String btid, String keyLifetime, Message onComplete) {
        if (onComplete != null) {
            onComplete.sendToTarget();
        }
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

    public SMSDispatcher getSMSDispatcher() {
        try {
            return ((PhoneProxy) PhoneFactory.getDefaultPhone()).getIccSmsInterfaceManager().getSMSDispatcher();
        } catch (NullPointerException e) {
            Rlog.e(LOG_TAG, "NullPointerException in getSMSDispacher");
            return null;
        }
    }

    public boolean getOCSGLAvailable() {
        Rlog.e(LOG_TAG, "Not supported in CdmaPhone");
        return false;
    }

    public void selectCsg(Message response) {
        Rlog.e(LOG_TAG, "Not supported in CdmaPhone");
    }

    public boolean getFDNavailable() {
        Rlog.e(LOG_TAG, "Not supported in CdmaPhone");
        return false;
    }
}
