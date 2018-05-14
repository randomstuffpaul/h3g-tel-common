package com.android.internal.telephony;

import android.app.ActivityManagerNative;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences.Editor;
import android.net.LinkProperties;
import android.net.NetworkCapabilities;
import android.net.wifi.WifiManager;
import android.os.AsyncResult;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.Registrant;
import android.os.RegistrantList;
import android.os.SystemProperties;
import android.preference.PreferenceManager;
import android.provider.Settings.Global;
import android.provider.Settings.Secure;
import android.provider.Settings.SettingNotFoundException;
import android.provider.Settings.System;
import android.telephony.CellIdentityCdma;
import android.telephony.CellInfo;
import android.telephony.CellInfoCdma;
import android.telephony.DataConnectionRealTimeInfo;
import android.telephony.PhoneNumberUtils;
import android.telephony.Rlog;
import android.telephony.ServiceState;
import android.telephony.SignalStrength;
import android.telephony.TelephonyManager;
import android.telephony.VoLteServiceState;
import android.text.TextUtils;
import android.util.Pair;
import com.android.internal.telephony.Call.SrvccState;
import com.android.internal.telephony.PhoneConstants.DataState;
import com.android.internal.telephony.PhoneConstants.State;
import com.android.internal.telephony.cat.CatService;
import com.android.internal.telephony.dataconnection.DcTrackerBase;
import com.android.internal.telephony.gsm.CallFailCause;
import com.android.internal.telephony.imsphone.ImsPhone;
import com.android.internal.telephony.test.SimulatedRadioControl;
import com.android.internal.telephony.uicc.IccCardApplicationStatus.AppType;
import com.android.internal.telephony.uicc.IccFileHandler;
import com.android.internal.telephony.uicc.IccRecords;
import com.android.internal.telephony.uicc.IsimRecords;
import com.android.internal.telephony.uicc.UiccCard;
import com.android.internal.telephony.uicc.UiccCardApplication;
import com.android.internal.telephony.uicc.UiccController;
import com.android.internal.telephony.uicc.UsimServiceTable;
import com.samsung.android.telephony.MultiSimManager;
import com.sec.android.app.CscFeature;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.concurrent.atomic.AtomicReference;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

public abstract class PhoneBase extends Handler implements Phone {
    public static final String CLIR_KEY = "clir_key";
    public static final String DATA_DISABLED_ON_BOOT_KEY = "disabled_on_boot_key";
    private static final int DEFAULT_MODE = 0;
    public static final String DNS_SERVER_CHECK_DISABLED_KEY = "dns_server_check_disabled_key";
    private static final int DRX_CN6_T32 = 6;
    private static final int DRX_CN7_T64 = 7;
    private static final int DRX_CN8_T128 = 8;
    private static final int DRX_CN9_T256 = 9;
    private static final String DRX_DIRECTORYPATH = "/efs/drx";
    private static final String DRX_FILEPATH = "/efs/drx/reducedmode";
    private static final int DRX_UNKNOWN = 0;
    protected static final String EMERGENCY_CALL_NUMBER_CUSTOMER_SPEC = "emergency_call_number_customer_spec";
    protected static final int EVENT_CALL_RING = 14;
    protected static final int EVENT_CALL_RING_CONTINUE = 15;
    protected static final int EVENT_CDMA_SUBSCRIPTION_SOURCE_CHANGED = 27;
    protected static final int EVENT_DELAY_EXIT_EMERGENCY_CALLBACK = 684;
    protected static final int EVENT_EMERGENCY_CALLBACK_MODE_ENTER = 25;
    protected static final int EVENT_EXIT_EMERGENCY_CALLBACK_INTERNAL = 1007;
    protected static final int EVENT_EXIT_EMERGENCY_CALLBACK_RESPONSE = 26;
    protected static final int EVENT_GET_BASEBAND_VERSION_DONE = 6;
    protected static final int EVENT_GET_CALL_FORWARD_DONE = 13;
    protected static final int EVENT_GET_DEVICE_IDENTITY_DONE = 21;
    protected static final int EVENT_GET_DRX_RESULT = 1001;
    protected static final int EVENT_GET_IMEISV_DONE = 10;
    protected static final int EVENT_GET_IMEI_DONE = 9;
    protected static final int EVENT_GET_MDN_DONE = 35;
    protected static final int EVENT_GET_SIM_POWER_DONE = 2001;
    protected static final int EVENT_GET_SIM_STATUS_DONE = 11;
    protected static final int EVENT_ICC_CHANGED = 30;
    protected static final int EVENT_ICC_RECORD_EVENTS = 29;
    protected static final int EVENT_IMS_STATE_CHANGED = 34;
    protected static final int EVENT_INITIATE_SILENT_REDIAL = 32;
    protected static final int EVENT_LAST = 36;
    protected static final int EVENT_MMI_DONE = 4;
    protected static final int EVENT_NV_READY = 23;
    static final int EVENT_PERMANENT_AUTO_SELECT_DONE = 300;
    protected static final int EVENT_RADIO_AVAILABLE = 1;
    protected static final int EVENT_RADIO_OFF_OR_NOT_AVAILABLE = 8;
    protected static final int EVENT_RADIO_ON = 5;
    protected static final int EVENT_REGISTERED_TO_NETWORK = 19;
    protected static final int EVENT_RESET_PREFERRED_NETWORK_TYPE = 2002;
    protected static final int EVENT_RUIM_RECORDS_LOADED = 22;
    protected static final int EVENT_SERVICE_STATE_CHANGED = 1006;
    protected static final int EVENT_SET_CALL_FORWARD_DONE = 12;
    protected static final int EVENT_SET_CLIR_COMPLETE = 18;
    static final int EVENT_SET_DELAY = 400;
    protected static final int EVENT_SET_DRX_RESULT = 1002;
    protected static final int EVENT_SET_ENHANCED_VP = 24;
    protected static final int EVENT_SET_NETWORK_AUTOMATIC = 28;
    protected static final int EVENT_SET_NETWORK_AUTOMATIC_COMPLETE = 17;
    protected static final int EVENT_SET_NETWORK_MANUAL_COMPLETE = 16;
    protected static final int EVENT_SET_VM_NUMBER_DONE = 20;
    protected static final int EVENT_SIM_RECORDS_LOADED = 3;
    protected static final int EVENT_SRVCC_STATE_CHANGED = 31;
    protected static final int EVENT_SS = 36;
    protected static final int EVENT_SSN = 2;
    protected static final int EVENT_UNSOL_OEM_HOOK_RAW = 33;
    protected static final int EVENT_USSD = 7;
    public static String INTENT_CHAMELEON_TELEPHONY_UPDATE = "android.intent.action.CHAMELEON_TELEPHONY_UPDATE";
    private static final String LOG_TAG = "PhoneBase";
    public static final String NETWORK_SELECTION_KEY = "network_selection_key";
    public static final String NETWORK_SELECTION_KEY2 = "network_selection_key2";
    public static final String NETWORK_SELECTION_NAME_KEY = "network_selection_name_key";
    public static final String NETWORK_SELECTION_NAME_KEY2 = "network_selection_name_key2";
    public static String PROPERTY_CDMA_HOME_OPERATOR_ALPHA = "ro.cdma.home.operator.alpha";
    public static String PROPERTY_CDMA_HOME_OPERATOR_DEFAULT_ALPHA = "ro.cdma.default_alpha";
    public static String PROPERTY_CDMA_HOME_OPERATOR_DEFAULT_NUMERIC = "ro.cdma.default_numeric";
    public static String PROPERTY_CDMA_HOME_OPERATOR_NUMERIC = "ro.cdma.home.operator.numeric";
    public static String PROPERTY_CDMA_HOME_OPERATOR_RESELLERID = "ro.home.operator.carrierid";
    public static String PROPERTY_LEGACY_IMS_ENABLE = "persist.ril.ims.legacy.enabled";
    private static final int REDUCED_MODE = 1;
    protected static final String[] mCardOffIntent = new String[]{"com.samsung.intent.action.Slot1OffCompleted", "com.samsung.intent.action.Slot2OffCompleted", "com.samsung.intent.action.Slot3OffCompleted", "com.samsung.intent.action.Slot4OffCompleted", "com.samsung.intent.action.Slot5OffCompleted"};
    protected static final String[] mCardOnIntent = new String[]{"com.samsung.intent.action.Slot1OnCompleted", "com.samsung.intent.action.Slot2OnCompleted", "com.samsung.intent.action.Slot3OnCompleted", "com.samsung.intent.action.Slot4OnCompleted", "com.samsung.intent.action.Slot5OnCompleted"};
    protected static final String[] mPhoneOnKey = new String[]{"phone1_on", "phone2_on", "phone3_on", "phone4_on", "phone5_on"};
    private static boolean mUsedLegacyIms = false;
    private final String mActionAttached;
    private final String mActionDetached;
    protected final RegistrantList mCallModifyRegistrants;
    int mCallRingContinueToken;
    int mCallRingDelay;
    public CommandsInterface mCi;
    protected final Context mContext;
    protected boolean mCscChameleonFileExists;
    protected RegistrantList mDataConnectionStateChangedRegistrants;
    public DcTrackerBase mDcTracker;
    protected final RegistrantList mDisconnectRegistrants;
    boolean mDnsCheckDisabled;
    boolean mDoesRilSendMultipleCallRing;
    public int mDrx;
    public String mEccNums;
    protected final RegistrantList mHandoverRegistrants;
    public AtomicReference<IccRecords> mIccRecords;
    private BroadcastReceiver mImsIntentReceiver;
    private final Object mImsLock;
    protected ImsPhone mImsPhone;
    private boolean mImsServiceReady;
    protected final RegistrantList mIncomingRingRegistrants;
    public boolean mIsTheCurrentActivePhone;
    boolean mIsVoiceCapable;
    private LegacyIms mLegacyIms;
    protected Looper mLooper;
    protected final RegistrantList mMmiCompleteRegistrants;
    protected final RegistrantList mMmiRegistrants;
    private final String mName;
    protected final RegistrantList mNewRingingConnectionRegistrants;
    protected PhoneNotifier mNotifier;
    public int mPhoneId;
    protected final RegistrantList mPreciseCallStateRegistrants;
    public ServiceState mPrevSs;
    private RcsCallTracker mRcsCallTracker;
    protected final RegistrantList mServiceStateRegistrants;
    protected final RegistrantList mSimRecordsLoadedRegistrants;
    protected SimulatedRadioControl mSimulatedRadioControl;
    public SmsStorageMonitor mSmsStorageMonitor;
    public SmsUsageMonitor mSmsUsageMonitor;
    protected Subscription mSubscriptionData;
    protected final RegistrantList mSuppServiceFailedRegistrants;
    private TelephonyTester mTelephonyTester;
    protected AtomicReference<UiccCardApplication> mUiccApplication;
    protected UiccController mUiccController;
    boolean mUnitTestMode;
    protected final RegistrantList mUnknownConnectionRegistrants;
    private int mVmCount;
    protected String mVmCountKey;
    protected String mVmId;

    class C00211 extends BroadcastReceiver {
        C00211() {
        }

        public void onReceive(Context context, Intent intent) {
            if (intent != null) {
                Rlog.d(PhoneBase.LOG_TAG, "Intent: " + intent.getAction());
                if (intent.hasExtra("android:subid") && intent.getLongExtra("android:subid", -1) != PhoneBase.this.getSubId()) {
                    return;
                }
                if (intent.getAction().equals("com.android.ims.IMS_SERVICE_UP")) {
                    if (!PhoneBase.mUsedLegacyIms) {
                        PhoneBase.this.mImsServiceReady = true;
                        PhoneBase.this.updateImsPhone();
                        return;
                    }
                    return;
                } else if (intent.getAction().equals("com.android.ims.IMS_SERVICE_DOWN")) {
                    PhoneBase.this.mImsServiceReady = false;
                    PhoneBase.this.updateImsPhone();
                    return;
                } else {
                    return;
                }
            }
            Rlog.d(PhoneBase.LOG_TAG, "Intent is null");
        }
    }

    private class CscChameleonParser {
        private static final String CSC_CHAMELEON_FILE = "/carrier/chameleon.xml";
        private static final String PATH_OPERATORS_BRANDALPHA = "Operators.BrandAlpha";
        private static final String PATH_OPERATORS_NETWORKCODE = "Operators.AndroidOperatorNetworkCode";
        private static final String PATH_OPERATORS_RESELLERID = "Operators.SubscriberCarrierId";
        private boolean isFileExist = false;
        private Document mDoc;
        private Node mRoot;

        public CscChameleonParser() {
            Rlog.d(PhoneBase.LOG_TAG, "[CscChameleonParser] init");
            try {
                update(CSC_CHAMELEON_FILE);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        private boolean isFileExists() {
            Rlog.d(PhoneBase.LOG_TAG, "isFileExists : " + this.isFileExist);
            return this.isFileExist;
        }

        private void update(String fileName) throws ParserConfigurationException, SAXException, IOException {
            DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
            if (new File(fileName).exists()) {
                Rlog.d(PhoneBase.LOG_TAG, "[CscChameleonParser] Update");
                this.mDoc = builder.parse(new File(fileName));
                this.mRoot = this.mDoc.getDocumentElement();
                this.isFileExist = true;
                return;
            }
            Rlog.d(PhoneBase.LOG_TAG, "[CscChameleonParser] Update: File not exist");
            this.isFileExist = false;
        }

        public String getOperatorBrandAlpha() {
            return get(PATH_OPERATORS_BRANDALPHA);
        }

        public String getOperatorNetworkCode() {
            return get(PATH_OPERATORS_NETWORKCODE);
        }

        public String getOperatorResellerID() {
            return get(PATH_OPERATORS_RESELLERID);
        }

        public String get(String path) {
            Node node = search(path);
            if (node != null) {
                return node.getFirstChild().getNodeValue();
            }
            return null;
        }

        public Node search(String path) {
            if (path == null) {
                return null;
            }
            Node node = this.mRoot;
            StringTokenizer tokenizer = new StringTokenizer(path, ".");
            while (tokenizer.hasMoreTokens()) {
                String token = tokenizer.nextToken();
                if (node == null) {
                    return null;
                }
                node = search(node, token);
            }
            return node;
        }

        public Node search(Node parent, String name) {
            if (parent == null) {
                return null;
            }
            NodeList children = parent.getChildNodes();
            if (children != null) {
                int n = children.getLength();
                for (int i = 0; i < n; i++) {
                    Node child = children.item(i);
                    if (child.getNodeName().equals(name)) {
                        return child;
                    }
                }
            }
            return null;
        }
    }

    protected static class NetworkSelectMessage {
        public Message message;
        public String operatorAlphaLong;
        public String operatorNumeric;

        protected NetworkSelectMessage() {
        }
    }

    public abstract int getPhoneType();

    public abstract State getState();

    protected abstract void onUpdateIccAvailability();

    public String getPhoneName() {
        return this.mName;
    }

    public String getActionDetached() {
        return this.mActionDetached;
    }

    public String getActionAttached() {
        return this.mActionAttached;
    }

    public DcTrackerBase getDcTracker() {
        return this.mDcTracker;
    }

    public void setSystemProperty(String property, String value) {
        if (!getUnitTestMode()) {
            TelephonyManager.setTelephonyProperty(property, SubscriptionController.getInstance().getSubId(this.mPhoneId)[0], value);
        }
    }

    public String getSystemProperty(String property, String defValue) {
        if (getUnitTestMode()) {
            return null;
        }
        return SystemProperties.get(property, defValue);
    }

    protected PhoneBase(String name, PhoneNotifier notifier, Context context, CommandsInterface ci) {
        this(name, notifier, context, ci, false);
    }

    protected PhoneBase(String name, PhoneNotifier notifier, Context context, CommandsInterface ci, boolean unitTestMode) {
        this(name, notifier, context, ci, unitTestMode, 0);
    }

    protected PhoneBase(String name, PhoneNotifier notifier, Context context, CommandsInterface ci, boolean unitTestMode, int phoneId) {
        boolean z = true;
        this.mImsIntentReceiver = new C00211();
        this.mCscChameleonFileExists = false;
        this.mDrx = -1;
        this.mDataConnectionStateChangedRegistrants = new RegistrantList();
        this.mIsTheCurrentActivePhone = true;
        this.mIsVoiceCapable = true;
        this.mUiccController = null;
        this.mIccRecords = new AtomicReference();
        this.mUiccApplication = new AtomicReference();
        this.mSubscriptionData = null;
        this.mImsLock = new Object();
        this.mImsServiceReady = false;
        this.mImsPhone = null;
        this.mPrevSs = null;
        this.mEccNums = null;
        this.mLegacyIms = null;
        this.mVmCount = 0;
        this.mVmCountKey = "vm_count_key";
        this.mVmId = "vm_id_key";
        this.mPreciseCallStateRegistrants = new RegistrantList();
        this.mHandoverRegistrants = new RegistrantList();
        this.mNewRingingConnectionRegistrants = new RegistrantList();
        this.mIncomingRingRegistrants = new RegistrantList();
        this.mDisconnectRegistrants = new RegistrantList();
        this.mServiceStateRegistrants = new RegistrantList();
        this.mMmiCompleteRegistrants = new RegistrantList();
        this.mMmiRegistrants = new RegistrantList();
        this.mUnknownConnectionRegistrants = new RegistrantList();
        this.mSuppServiceFailedRegistrants = new RegistrantList();
        this.mSimRecordsLoadedRegistrants = new RegistrantList();
        this.mCallModifyRegistrants = new RegistrantList();
        this.mPhoneId = phoneId;
        this.mName = name;
        this.mNotifier = notifier;
        this.mContext = context;
        this.mLooper = Looper.myLooper();
        this.mCi = ci;
        this.mActionDetached = getClass().getPackage().getName() + ".action_detached";
        this.mActionAttached = getClass().getPackage().getName() + ".action_attached";
        if (Build.IS_DEBUGGABLE) {
            this.mTelephonyTester = new TelephonyTester(this);
        }
        setUnitTestMode(unitTestMode);
        this.mDnsCheckDisabled = PreferenceManager.getDefaultSharedPreferences(context).getBoolean(DNS_SERVER_CHECK_DISABLED_KEY, false);
        this.mCi.setOnCallRing(this, 14, null);
        this.mIsVoiceCapable = this.mContext.getResources().getBoolean(17956933);
        this.mDoesRilSendMultipleCallRing = SystemProperties.getBoolean("ro.telephony.call_ring.multiple", true);
        Rlog.d(LOG_TAG, "mDoesRilSendMultipleCallRing=" + this.mDoesRilSendMultipleCallRing);
        this.mCallRingDelay = SystemProperties.getInt("ro.telephony.call_ring.delay", CallFailCause.CAUSE_OFFSET);
        Rlog.d(LOG_TAG, "mCallRingDelay=" + this.mCallRingDelay);
        if (getPhoneType() != 5) {
            setPropertiesByCarrier();
            this.mSmsStorageMonitor = new SmsStorageMonitor(this);
            this.mSmsUsageMonitor = new SmsUsageMonitor(context);
            this.mUiccController = UiccController.getInstance(this);
            this.mUiccController.registerForIccChanged(this, 30, null);
            if (CscFeature.getInstance().getEnableStatus("CscFeature_IMS_EnableRCSe") || CscFeature.getInstance().getEnableStatus("CscFeature_IMS_EnableVoLTE")) {
                this.mRcsCallTracker = new RcsCallTracker(this);
            }
            IntentFilter filter = new IntentFilter();
            filter.addAction("com.android.ims.IMS_SERVICE_UP");
            filter.addAction("com.android.ims.IMS_SERVICE_DOWN");
            this.mContext.registerReceiver(this.mImsIntentReceiver, filter);
            this.mCi.registerForSrvccStateChanged(this, 31, null);
            this.mCi.setOnUnsolOemHookRaw(this, 33, null);
            if (1 != SystemProperties.getInt(PROPERTY_LEGACY_IMS_ENABLE, 1)) {
                z = false;
            }
            mUsedLegacyIms = z;
            Rlog.d(LOG_TAG, "use Legacy IMS?" + mUsedLegacyIms);
            if (mUsedLegacyIms) {
                this.mLegacyIms = new LegacyIms(context, ci, phoneId);
                if (this.mLegacyIms != null) {
                    this.mCi.registerForImsRegistrationStateChanged(this, 34, null);
                } else {
                    Rlog.e(LOG_TAG, "failed to make Legacy IMS");
                }
            }
        }
    }

    public void dispose() {
        synchronized (PhoneProxy.lockForRadioTechnologyChange) {
            this.mContext.unregisterReceiver(this.mImsIntentReceiver);
            this.mCi.unSetOnCallRing(this);
            this.mDcTracker.cleanUpAllConnections(null);
            this.mIsTheCurrentActivePhone = false;
            this.mSmsStorageMonitor.dispose();
            this.mSmsUsageMonitor.dispose();
            this.mUiccController.unregisterForIccChanged(this);
            this.mCi.unregisterForSrvccStateChanged(this);
            this.mCi.unSetOnUnsolOemHookRaw(this);
            this.mCi.unregisterForImsRegistrationStateChanged(this);
            if (this.mTelephonyTester != null) {
                this.mTelephonyTester.dispose();
            }
            ImsPhone imsPhone = this.mImsPhone;
            if (imsPhone != null) {
                imsPhone.unregisterForSilentRedial(this);
                imsPhone.dispose();
            }
            if (CscFeature.getInstance().getEnableStatus("CscFeature_IMS_EnableRCSe") || CscFeature.getInstance().getEnableStatus("CscFeature_IMS_EnableVoLTE")) {
                this.mRcsCallTracker.dispose();
            }
        }
    }

    public void removeReferences() {
        this.mSmsStorageMonitor = null;
        this.mSmsUsageMonitor = null;
        this.mIccRecords.set(null);
        this.mUiccApplication.set(null);
        this.mDcTracker = null;
        this.mUiccController = null;
        ImsPhone imsPhone = this.mImsPhone;
        if (imsPhone != null) {
            imsPhone.removeReferences();
            this.mImsPhone = null;
        }
    }

    public void handleMessage(Message msg) {
        if (this.mIsTheCurrentActivePhone) {
            AsyncResult ar;
            switch (msg.what) {
                case 14:
                    Rlog.d(LOG_TAG, "Event EVENT_CALL_RING Received state=" + getState());
                    if (msg.obj.exception == null) {
                        State state = getState();
                        if (this.mDoesRilSendMultipleCallRing || !(state == State.RINGING || state == State.IDLE)) {
                            notifyIncomingRing();
                            return;
                        }
                        this.mCallRingContinueToken++;
                        sendIncomingCallRingNotification(this.mCallRingContinueToken);
                        return;
                    }
                    return;
                case 15:
                    Rlog.d(LOG_TAG, "Event EVENT_CALL_RING_CONTINUE Received stat=" + getState());
                    if (getState() == State.RINGING) {
                        sendIncomingCallRingNotification(msg.arg1);
                        return;
                    }
                    return;
                case 16:
                    handleSetSelectNetwork((AsyncResult) msg.obj);
                    if (CscFeature.getInstance().getEnableStatus("CscFeature_RIL_FixedAutomaticSearch")) {
                        sendMessageDelayed(obtainMessage(EVENT_SET_DELAY), 30);
                        return;
                    }
                    return;
                case 17:
                    handleSetSelectNetwork((AsyncResult) msg.obj);
                    return;
                case 30:
                    onUpdateIccAvailability();
                    return;
                case 31:
                    ar = (AsyncResult) msg.obj;
                    if (ar.exception == null) {
                        handleSrvccStateChanged((int[]) ar.result);
                        return;
                    } else {
                        Rlog.e(LOG_TAG, "Srvcc exception: " + ar.exception);
                        return;
                    }
                case 32:
                    Rlog.d(LOG_TAG, "Event EVENT_INITIATE_SILENT_REDIAL Received");
                    ar = (AsyncResult) msg.obj;
                    if (ar.exception == null && ar.result != null) {
                        String dialString = ar.result;
                        if (!TextUtils.isEmpty(dialString)) {
                            try {
                                dialInternal(dialString, null, 0);
                                return;
                            } catch (CallStateException e) {
                                Rlog.e(LOG_TAG, "silent redial failed: " + e);
                                return;
                            }
                        }
                        return;
                    }
                    return;
                case 33:
                    ar = (AsyncResult) msg.obj;
                    if (ar.exception == null) {
                        byte[] data = (byte[]) ar.result;
                        Rlog.d(LOG_TAG, "EVENT_UNSOL_OEM_HOOK_RAW data=" + IccUtils.bytesToHexString(data));
                        this.mNotifier.notifyOemHookRawEventForSubscriber(getSubId(), data);
                        return;
                    }
                    Rlog.e(LOG_TAG, "OEM hook raw exception: " + ar.exception);
                    return;
                case 34:
                    ar = (AsyncResult) msg.obj;
                    if (ar.exception != null) {
                        Rlog.e(LOG_TAG, "IMS State query failed!" + ar.exception);
                        return;
                    } else if (ar.result != null && ((int[]) ar.result).length >= 4) {
                        int[] responseArray = (int[]) ar.result;
                        if (this.mLegacyIms != null) {
                            this.mLegacyIms.setLegacyImsRegistration(responseArray);
                            return;
                        } else if (mUsedLegacyIms) {
                            Rlog.e(LOG_TAG, "mLegacyIms is null");
                            return;
                        } else {
                            Rlog.d(LOG_TAG, "Don't use Legacy IMS");
                            return;
                        }
                    } else {
                        return;
                    }
                case EVENT_PERMANENT_AUTO_SELECT_DONE /*300*/:
                    if (((AsyncResult) msg.obj).exception != null) {
                        Rlog.d(LOG_TAG, "Permanent automatic network selection: failed!");
                        return;
                    }
                    return;
                case EVENT_SET_DELAY /*400*/:
                    this.mCi.setNetworkSelectionModeAutomatic(obtainMessage(EVENT_PERMANENT_AUTO_SELECT_DONE, null));
                    return;
                case 1001:
                    ar = (AsyncResult) msg.obj;
                    if (ar.exception != null) {
                        Rlog.e(LOG_TAG, "EVENT_GET_DRX_RESULT - Fail");
                        return;
                    }
                    this.mDrx = ((byte[]) ar.result)[0];
                    Rlog.e(LOG_TAG, "EVENT_GET_DRX_RESULT - Success - DRX: " + this.mDrx);
                    return;
                case 1002:
                    if (((AsyncResult) msg.obj).exception != null) {
                        Rlog.e(LOG_TAG, "EVENT_SET_DRX_RESULT - Fail");
                        return;
                    }
                    Rlog.e(LOG_TAG, "EVENT_SET_DRX_RESULT - Success");
                    sendGetDrx();
                    return;
                default:
                    throw new RuntimeException("unexpected event not handled: " + msg.what);
            }
        }
        Rlog.e(LOG_TAG, "Received message " + msg + "[" + msg.what + "] while being destroyed. Ignoring.");
    }

    private void handleSrvccStateChanged(int[] ret) {
        Rlog.d(LOG_TAG, "handleSrvccStateChanged");
        Connection conn = null;
        ImsPhone imsPhone = this.mImsPhone;
        SrvccState srvccState = SrvccState.NONE;
        if (ret != null && ret.length != 0) {
            int state = ret[0];
            switch (state) {
                case 0:
                    srvccState = SrvccState.STARTED;
                    if (imsPhone == null) {
                        Rlog.d(LOG_TAG, "HANDOVER_STARTED: mImsPhone null");
                        break;
                    } else {
                        conn = imsPhone.getHandoverConnection();
                        break;
                    }
                case 1:
                    srvccState = SrvccState.COMPLETED;
                    if (imsPhone == null) {
                        Rlog.d(LOG_TAG, "HANDOVER_COMPLETED: mImsPhone null");
                        break;
                    } else {
                        imsPhone.notifySrvccState(srvccState);
                        break;
                    }
                case 2:
                case 3:
                    srvccState = SrvccState.FAILED;
                    break;
                default:
                    return;
            }
            getCallTracker().notifySrvccState(srvccState, conn);
            notifyVoLteServiceStateChanged(new VoLteServiceState(state));
        }
    }

    public Context getContext() {
        return this.mContext;
    }

    public void disableDnsCheck(boolean b) {
        this.mDnsCheckDisabled = b;
        Editor editor = PreferenceManager.getDefaultSharedPreferences(getContext()).edit();
        editor.putBoolean(DNS_SERVER_CHECK_DISABLED_KEY, b);
        editor.apply();
    }

    public boolean isDnsCheckDisabled() {
        return this.mDnsCheckDisabled;
    }

    public void registerForPreciseCallStateChanged(Handler h, int what, Object obj) {
        checkCorrectThread(h);
        this.mPreciseCallStateRegistrants.addUnique(h, what, obj);
    }

    public void unregisterForPreciseCallStateChanged(Handler h) {
        this.mPreciseCallStateRegistrants.remove(h);
    }

    protected void notifyPreciseCallStateChangedP() {
        this.mPreciseCallStateRegistrants.notifyRegistrants(new AsyncResult(null, this, null));
        this.mNotifier.notifyPreciseCallState(this);
    }

    public void registerForHandoverStateChanged(Handler h, int what, Object obj) {
        checkCorrectThread(h);
        this.mHandoverRegistrants.addUnique(h, what, obj);
    }

    public void unregisterForHandoverStateChanged(Handler h) {
        this.mHandoverRegistrants.remove(h);
    }

    public void notifyHandoverStateChanged(Connection cn) {
        this.mHandoverRegistrants.notifyRegistrants(new AsyncResult(null, cn, null));
    }

    public void migrateFrom(PhoneBase from) {
        migrate(this.mHandoverRegistrants, from.mHandoverRegistrants);
        migrate(this.mPreciseCallStateRegistrants, from.mPreciseCallStateRegistrants);
        migrate(this.mNewRingingConnectionRegistrants, from.mNewRingingConnectionRegistrants);
        migrate(this.mIncomingRingRegistrants, from.mIncomingRingRegistrants);
        migrate(this.mDisconnectRegistrants, from.mDisconnectRegistrants);
        migrate(this.mServiceStateRegistrants, from.mServiceStateRegistrants);
        migrate(this.mMmiCompleteRegistrants, from.mMmiCompleteRegistrants);
        migrate(this.mMmiRegistrants, from.mMmiRegistrants);
        migrate(this.mUnknownConnectionRegistrants, from.mUnknownConnectionRegistrants);
        migrate(this.mSuppServiceFailedRegistrants, from.mSuppServiceFailedRegistrants);
    }

    public void migrate(RegistrantList to, RegistrantList from) {
        from.removeCleared();
        int n = from.size();
        for (int i = 0; i < n; i++) {
            to.add((Registrant) from.get(i));
        }
    }

    public void registerForUnknownConnection(Handler h, int what, Object obj) {
        checkCorrectThread(h);
        this.mUnknownConnectionRegistrants.addUnique(h, what, obj);
    }

    public void unregisterForUnknownConnection(Handler h) {
        this.mUnknownConnectionRegistrants.remove(h);
    }

    public void registerForNewRingingConnection(Handler h, int what, Object obj) {
        checkCorrectThread(h);
        this.mNewRingingConnectionRegistrants.addUnique(h, what, obj);
    }

    public void unregisterForNewRingingConnection(Handler h) {
        this.mNewRingingConnectionRegistrants.remove(h);
    }

    public void registerForInCallVoicePrivacyOn(Handler h, int what, Object obj) {
        this.mCi.registerForInCallVoicePrivacyOn(h, what, obj);
    }

    public void unregisterForInCallVoicePrivacyOn(Handler h) {
        this.mCi.unregisterForInCallVoicePrivacyOn(h);
    }

    public void registerForInCallVoicePrivacyOff(Handler h, int what, Object obj) {
        this.mCi.registerForInCallVoicePrivacyOff(h, what, obj);
    }

    public void unregisterForInCallVoicePrivacyOff(Handler h) {
        this.mCi.unregisterForInCallVoicePrivacyOff(h);
    }

    public void registerForIncomingRing(Handler h, int what, Object obj) {
        checkCorrectThread(h);
        this.mIncomingRingRegistrants.addUnique(h, what, obj);
    }

    public void unregisterForIncomingRing(Handler h) {
        this.mIncomingRingRegistrants.remove(h);
    }

    public void registerForDisconnect(Handler h, int what, Object obj) {
        checkCorrectThread(h);
        this.mDisconnectRegistrants.addUnique(h, what, obj);
    }

    public void unregisterForDisconnect(Handler h) {
        this.mDisconnectRegistrants.remove(h);
    }

    public void registerForSuppServiceFailed(Handler h, int what, Object obj) {
        checkCorrectThread(h);
        this.mSuppServiceFailedRegistrants.addUnique(h, what, obj);
    }

    public void unregisterForSuppServiceFailed(Handler h) {
        this.mSuppServiceFailedRegistrants.remove(h);
    }

    public void registerForMmiInitiate(Handler h, int what, Object obj) {
        checkCorrectThread(h);
        this.mMmiRegistrants.addUnique(h, what, obj);
    }

    public void unregisterForMmiInitiate(Handler h) {
        this.mMmiRegistrants.remove(h);
    }

    public void registerForMmiComplete(Handler h, int what, Object obj) {
        checkCorrectThread(h);
        this.mMmiCompleteRegistrants.addUnique(h, what, obj);
    }

    public void unregisterForMmiComplete(Handler h) {
        checkCorrectThread(h);
        this.mMmiCompleteRegistrants.remove(h);
    }

    public void registerForSimRecordsLoaded(Handler h, int what, Object obj) {
        logUnexpectedCdmaMethodCall("registerForSimRecordsLoaded");
    }

    public void unregisterForSimRecordsLoaded(Handler h) {
        logUnexpectedCdmaMethodCall("unregisterForSimRecordsLoaded");
    }

    public void setNetworkSelectionModeAutomatic(Message response) {
        NetworkSelectMessage nsm = new NetworkSelectMessage();
        nsm.message = response;
        nsm.operatorNumeric = "";
        nsm.operatorAlphaLong = "";
        this.mCi.setNetworkSelectionModeAutomatic(obtainMessage(17, nsm));
    }

    public void selectNetworkManually(OperatorInfo network, Message response) {
        NetworkSelectMessage nsm = new NetworkSelectMessage();
        nsm.message = response;
        nsm.operatorNumeric = network.getOperatorNumeric();
        nsm.operatorAlphaLong = network.getOperatorAlphaLong();
        this.mCi.setNetworkSelectionModeManual(network.getOperatorNumeric(), obtainMessage(16, nsm));
    }

    private void handleSetSelectNetwork(AsyncResult ar) {
        if (ar.userObj instanceof NetworkSelectMessage) {
            NetworkSelectMessage nsm = ar.userObj;
            if (nsm.message != null) {
                AsyncResult.forMessage(nsm.message, ar.result, ar.exception);
                nsm.message.sendToTarget();
            }
            Editor editor = PreferenceManager.getDefaultSharedPreferences(getContext()).edit();
            editor.putString(MultiSimManager.appendSimSlot(NETWORK_SELECTION_KEY, this.mPhoneId), nsm.operatorNumeric);
            editor.putString(MultiSimManager.appendSimSlot(NETWORK_SELECTION_NAME_KEY, this.mPhoneId), nsm.operatorAlphaLong);
            if (!editor.commit()) {
                Rlog.e(LOG_TAG, "failed to commit network selection preference");
                return;
            }
            return;
        }
        Rlog.e(LOG_TAG, "unexpected result from user object.");
    }

    private String getSavedNetworkSelection() {
        return PreferenceManager.getDefaultSharedPreferences(getContext()).getString(NETWORK_SELECTION_KEY, "");
    }

    public void restoreSavedNetworkSelection(Message response) {
        String networkSelection = getSavedNetworkSelection();
        if (TextUtils.isEmpty(networkSelection)) {
            this.mCi.setNetworkSelectionModeAutomatic(response);
        } else {
            this.mCi.setNetworkSelectionModeManual(networkSelection, response);
        }
    }

    public void setUnitTestMode(boolean f) {
        this.mUnitTestMode = f;
    }

    public boolean getUnitTestMode() {
        return this.mUnitTestMode;
    }

    protected void notifyDisconnectP(Connection cn) {
        this.mDisconnectRegistrants.notifyRegistrants(new AsyncResult(null, cn, null));
    }

    public void registerForServiceStateChanged(Handler h, int what, Object obj) {
        checkCorrectThread(h);
        this.mServiceStateRegistrants.add(h, what, obj);
    }

    public void unregisterForServiceStateChanged(Handler h) {
        this.mServiceStateRegistrants.remove(h);
    }

    public void registerForRingbackTone(Handler h, int what, Object obj) {
        this.mCi.registerForRingbackTone(h, what, obj);
    }

    public void unregisterForRingbackTone(Handler h) {
        this.mCi.unregisterForRingbackTone(h);
    }

    public void registerForOnHoldTone(Handler h, int what, Object obj) {
    }

    public void unregisterForOnHoldTone(Handler h) {
    }

    public void registerForResendIncallMute(Handler h, int what, Object obj) {
        this.mCi.registerForResendIncallMute(h, what, obj);
    }

    public void unregisterForResendIncallMute(Handler h) {
        this.mCi.unregisterForResendIncallMute(h);
    }

    public void setEchoSuppressionEnabled() {
    }

    protected void notifyServiceStateChangedP(ServiceState ss) {
        this.mServiceStateRegistrants.notifyRegistrants(new AsyncResult(null, ss, null));
        this.mNotifier.notifyServiceState(this);
    }

    public SimulatedRadioControl getSimulatedRadioControl() {
        return this.mSimulatedRadioControl;
    }

    private void checkCorrectThread(Handler h) {
        if (h.getLooper() != this.mLooper) {
            throw new RuntimeException("com.android.internal.telephony.Phone must be used from within one thread");
        }
    }

    private void setPropertiesByCarrier() {
        String carrier = SystemProperties.get("ro.carrier");
        if (carrier != null && carrier.length() != 0 && !"unknown".equals(carrier)) {
            CharSequence[] carrierLocales = this.mContext.getResources().getTextArray(17236036);
            for (int i = 0; i < carrierLocales.length; i += 3) {
                if (carrier.equals(carrierLocales[i].toString())) {
                    Locale l = Locale.forLanguageTag(carrierLocales[i + 1].toString().replace('_', '-'));
                    String country = l.getCountry();
                    MccTable.setSystemLocale(this.mContext, l.getLanguage(), country);
                    if (!country.isEmpty()) {
                        try {
                            Global.getInt(this.mContext.getContentResolver(), "wifi_country_code");
                            return;
                        } catch (SettingNotFoundException e) {
                            ((WifiManager) this.mContext.getSystemService("wifi")).setCountryCode(country, false);
                            return;
                        }
                    }
                    return;
                }
            }
        }
    }

    public IccFileHandler getIccFileHandler() {
        IccFileHandler fh;
        UiccCardApplication uiccApplication = (UiccCardApplication) this.mUiccApplication.get();
        if (uiccApplication == null) {
            Rlog.d(LOG_TAG, "getIccFileHandler: uiccApplication == null, return null");
            fh = null;
        } else {
            fh = uiccApplication.getIccFileHandler();
        }
        Rlog.d(LOG_TAG, "getIccFileHandler: fh=" + fh);
        return fh;
    }

    public Handler getHandler() {
        return this;
    }

    public void updatePhoneObject(int voiceRadioTech) {
        PhoneFactory.getPhone(this.mPhoneId).updatePhoneObject(voiceRadioTech);
    }

    public ServiceStateTracker getServiceStateTracker() {
        return null;
    }

    public CallTracker getCallTracker() {
        return null;
    }

    public AppType getCurrentUiccAppType() {
        UiccCardApplication currentApp = (UiccCardApplication) this.mUiccApplication.get();
        if (currentApp != null) {
            return currentApp.getType();
        }
        return AppType.APPTYPE_UNKNOWN;
    }

    public IccCard getIccCard() {
        return null;
    }

    public String getIccSerialNumber() {
        IccRecords r = (IccRecords) this.mIccRecords.get();
        return r != null ? r.getIccId() : null;
    }

    public boolean getIccRecordsLoaded() {
        IccRecords r = (IccRecords) this.mIccRecords.get();
        return r != null ? r.getRecordsLoaded() : false;
    }

    public List<CellInfo> getAllCellInfo() {
        return privatizeCellInfoList(getServiceStateTracker().getAllCellInfo());
    }

    private List<CellInfo> privatizeCellInfoList(List<CellInfo> cellInfoList) {
        if (Secure.getInt(getContext().getContentResolver(), "location_mode", 0) != 0) {
            return cellInfoList;
        }
        ArrayList<CellInfo> privateCellInfoList = new ArrayList(cellInfoList.size());
        for (CellInfo c : cellInfoList) {
            if (c instanceof CellInfoCdma) {
                CellInfoCdma cellInfoCdma = (CellInfoCdma) c;
                CellIdentityCdma cellIdentity = cellInfoCdma.getCellIdentity();
                CellIdentityCdma maskedCellIdentity = new CellIdentityCdma(cellIdentity.getNetworkId(), cellIdentity.getSystemId(), cellIdentity.getBasestationId(), Integer.MAX_VALUE, Integer.MAX_VALUE);
                CellInfoCdma privateCellInfoCdma = new CellInfoCdma(cellInfoCdma);
                privateCellInfoCdma.setCellIdentity(maskedCellIdentity);
                privateCellInfoList.add(privateCellInfoCdma);
            } else {
                privateCellInfoList.add(c);
            }
        }
        return privateCellInfoList;
    }

    public void setCellInfoListRate(int rateInMillis) {
        this.mCi.setCellInfoListRate(rateInMillis, null);
    }

    public boolean getMessageWaitingIndicator() {
        IccRecords r = (IccRecords) this.mIccRecords.get();
        return r != null ? r.getVoiceMessageWaiting() : false;
    }

    public boolean getCallForwardingIndicator() {
        IccRecords r = (IccRecords) this.mIccRecords.get();
        return r != null ? r.getVoiceCallForwardingFlag() : false;
    }

    public void queryCdmaRoamingPreference(Message response) {
        this.mCi.queryCdmaRoamingPreference(response);
    }

    public SignalStrength getSignalStrength() {
        ServiceStateTracker sst = getServiceStateTracker();
        if (sst == null) {
            return new SignalStrength();
        }
        return sst.getSignalStrength();
    }

    public void setCdmaRoamingPreference(int cdmaRoamingType, Message response) {
        this.mCi.setCdmaRoamingPreference(cdmaRoamingType, response);
    }

    public void setCdmaSubscription(int cdmaSubscriptionType, Message response) {
        this.mCi.setCdmaSubscriptionSource(cdmaSubscriptionType, response);
    }

    public void setPreferredNetworkType(int networkType, Message response) {
        this.mCi.setPreferredNetworkType(networkType, response);
    }

    public void getPreferredNetworkType(Message response) {
        this.mCi.getPreferredNetworkType(response);
    }

    public void getSmscAddress(Message result) {
        this.mCi.getSmscAddress(result);
    }

    public void setSmscAddress(String address, Message result) {
        this.mCi.setSmscAddress(address, result);
    }

    public void setTTYMode(int ttyMode, Message onComplete) {
        this.mCi.setTTYMode(ttyMode, onComplete);
    }

    public void queryTTYMode(Message onComplete) {
        this.mCi.queryTTYMode(onComplete);
    }

    public void enableEnhancedVoicePrivacy(boolean enable, Message onComplete) {
        logUnexpectedCdmaMethodCall("enableEnhancedVoicePrivacy");
    }

    public void getEnhancedVoicePrivacy(Message onComplete) {
        logUnexpectedCdmaMethodCall("getEnhancedVoicePrivacy");
    }

    public void setBandMode(int bandMode, Message response) {
        this.mCi.setBandMode(bandMode, response);
    }

    public void queryAvailableBandMode(Message response) {
        this.mCi.queryAvailableBandMode(response);
    }

    public void setLteBandMode(int bandMode, Message response) {
        this.mCi.setLteBandMode(bandMode, response);
    }

    public void invokeOemRilRequestRaw(byte[] data, Message response) {
        this.mCi.invokeOemRilRequestRaw(data, response);
    }

    public void invokeOemRilRequestStrings(String[] strings, Message response) {
        this.mCi.invokeOemRilRequestStrings(strings, response);
    }

    public void nvReadItem(int itemID, Message response) {
        this.mCi.nvReadItem(itemID, response);
    }

    public void nvWriteItem(int itemID, String itemValue, Message response) {
        this.mCi.nvWriteItem(itemID, itemValue, response);
    }

    public void nvWriteCdmaPrl(byte[] preferredRoamingList, Message response) {
        this.mCi.nvWriteCdmaPrl(preferredRoamingList, response);
    }

    public void nvResetConfig(int resetType, Message response) {
        this.mCi.nvResetConfig(resetType, response);
    }

    public void notifyDataActivity() {
        this.mNotifier.notifyDataActivity(this);
    }

    public void notifyMessageWaitingIndicator() {
        if (this.mIsVoiceCapable) {
            this.mNotifier.notifyMessageWaitingChanged(this);
        }
    }

    public void notifyDataConnection(String reason, String apnType, DataState state) {
        this.mNotifier.notifyDataConnection(this, reason, apnType, state);
    }

    public void notifyDataConnection(String reason, String apnType) {
        this.mNotifier.notifyDataConnection(this, reason, apnType, getDataConnectionState(apnType));
    }

    public void notifyDataConnection(String reason) {
        for (String apnType : getActiveApnTypes()) {
            this.mNotifier.notifyDataConnection(this, reason, apnType, getDataConnectionState(apnType));
        }
    }

    public void notifyOtaspChanged(int otaspMode) {
        this.mNotifier.notifyOtaspChanged(this, otaspMode);
    }

    public void notifySignalStrength() {
        this.mNotifier.notifySignalStrength(this);
    }

    public void notifyCellInfo(List<CellInfo> cellInfo) {
        this.mNotifier.notifyCellInfo(this, privatizeCellInfoList(cellInfo));
    }

    public void notifyDataConnectionRealTimeInfo(DataConnectionRealTimeInfo dcRtInfo) {
        this.mNotifier.notifyDataConnectionRealTimeInfo(this, dcRtInfo);
    }

    public void notifyVoLteServiceStateChanged(VoLteServiceState lteState) {
        this.mNotifier.notifyVoLteServiceStateChanged(this, lteState);
    }

    public void notifyFdnUpdated() {
        this.mNotifier.notifyFdnUpdated(this);
    }

    public boolean isInEmergencyCall() {
        return false;
    }

    public boolean isInEcm() {
        return false;
    }

    public int getVoiceMessageCount() {
        return 0;
    }

    public int getCdmaEriIconIndex() {
        logUnexpectedCdmaMethodCall("getCdmaEriIconIndex");
        return -1;
    }

    public int getCdmaEriIconMode() {
        logUnexpectedCdmaMethodCall("getCdmaEriIconMode");
        return -1;
    }

    public String getCdmaEriText() {
        logUnexpectedCdmaMethodCall("getCdmaEriText");
        return "GSM nw, no ERI";
    }

    public String getCdmaMin() {
        logUnexpectedCdmaMethodCall("getCdmaMin");
        return null;
    }

    public boolean isMinInfoReady() {
        logUnexpectedCdmaMethodCall("isMinInfoReady");
        return false;
    }

    public String getCdmaPrlVersion() {
        logUnexpectedCdmaMethodCall("getCdmaPrlVersion");
        return null;
    }

    public void sendBurstDtmf(String dtmfString, int on, int off, Message onComplete) {
        logUnexpectedCdmaMethodCall("sendBurstDtmf");
    }

    public void exitEmergencyCallbackMode() {
        logUnexpectedCdmaMethodCall("exitEmergencyCallbackMode");
    }

    public void registerForCdmaOtaStatusChange(Handler h, int what, Object obj) {
        logUnexpectedCdmaMethodCall("registerForCdmaOtaStatusChange");
    }

    public void unregisterForCdmaOtaStatusChange(Handler h) {
        logUnexpectedCdmaMethodCall("unregisterForCdmaOtaStatusChange");
    }

    public void registerForSubscriptionInfoReady(Handler h, int what, Object obj) {
        logUnexpectedCdmaMethodCall("registerForSubscriptionInfoReady");
    }

    public void unregisterForSubscriptionInfoReady(Handler h) {
        logUnexpectedCdmaMethodCall("unregisterForSubscriptionInfoReady");
    }

    public boolean needsOtaServiceProvisioning() {
        return false;
    }

    public boolean isOtaSpNumber(String dialStr) {
        return false;
    }

    public void registerForCallWaiting(Handler h, int what, Object obj) {
        logUnexpectedCdmaMethodCall("registerForCallWaiting");
    }

    public void unregisterForCallWaiting(Handler h) {
        logUnexpectedCdmaMethodCall("unregisterForCallWaiting");
    }

    public void registerForEcmTimerReset(Handler h, int what, Object obj) {
        logUnexpectedCdmaMethodCall("registerForEcmTimerReset");
    }

    public void unregisterForEcmTimerReset(Handler h) {
        logUnexpectedCdmaMethodCall("unregisterForEcmTimerReset");
    }

    public void registerForSignalInfo(Handler h, int what, Object obj) {
        this.mCi.registerForSignalInfo(h, what, obj);
    }

    public void unregisterForSignalInfo(Handler h) {
        this.mCi.unregisterForSignalInfo(h);
    }

    public void registerForDisplayInfo(Handler h, int what, Object obj) {
        this.mCi.registerForDisplayInfo(h, what, obj);
    }

    public void unregisterForDisplayInfo(Handler h) {
        this.mCi.unregisterForDisplayInfo(h);
    }

    public void registerForNumberInfo(Handler h, int what, Object obj) {
        this.mCi.registerForNumberInfo(h, what, obj);
    }

    public void unregisterForNumberInfo(Handler h) {
        this.mCi.unregisterForNumberInfo(h);
    }

    public void registerForRedirectedNumberInfo(Handler h, int what, Object obj) {
        this.mCi.registerForRedirectedNumberInfo(h, what, obj);
    }

    public void unregisterForRedirectedNumberInfo(Handler h) {
        this.mCi.unregisterForRedirectedNumberInfo(h);
    }

    public void registerForLineControlInfo(Handler h, int what, Object obj) {
        this.mCi.registerForLineControlInfo(h, what, obj);
    }

    public void unregisterForLineControlInfo(Handler h) {
        this.mCi.unregisterForLineControlInfo(h);
    }

    public void registerFoT53ClirlInfo(Handler h, int what, Object obj) {
        this.mCi.registerFoT53ClirlInfo(h, what, obj);
    }

    public void unregisterForT53ClirInfo(Handler h) {
        this.mCi.unregisterForT53ClirInfo(h);
    }

    public void registerForT53AudioControlInfo(Handler h, int what, Object obj) {
        this.mCi.registerForT53AudioControlInfo(h, what, obj);
    }

    public void unregisterForT53AudioControlInfo(Handler h) {
        this.mCi.unregisterForT53AudioControlInfo(h);
    }

    public void setOnEcbModeExitResponse(Handler h, int what, Object obj) {
        logUnexpectedCdmaMethodCall("setOnEcbModeExitResponse");
    }

    public void unsetOnEcbModeExitResponse(Handler h) {
        logUnexpectedCdmaMethodCall("unsetOnEcbModeExitResponse");
    }

    public String[] getActiveApnTypes() {
        return this.mDcTracker.getActiveApnTypes();
    }

    public String getActiveApnHost(String apnType) {
        return this.mDcTracker.getActiveApnString(apnType);
    }

    public LinkProperties getLinkProperties(String apnType) {
        return this.mDcTracker.getLinkProperties(apnType);
    }

    public NetworkCapabilities getNetworkCapabilities(String apnType) {
        return this.mDcTracker.getNetworkCapabilities(apnType);
    }

    public boolean isDataConnectivityPossible() {
        return isDataConnectivityPossible("default");
    }

    public boolean isDataConnectivityPossible(String apnType) {
        return this.mDcTracker != null && this.mDcTracker.isDataPossible(apnType);
    }

    public void notifyNewRingingConnectionP(Connection cn) {
        if (this.mIsVoiceCapable) {
            this.mNewRingingConnectionRegistrants.notifyRegistrants(new AsyncResult(null, cn, null));
        }
    }

    private void notifyIncomingRing() {
        if (this.mIsVoiceCapable) {
            this.mIncomingRingRegistrants.notifyRegistrants(new AsyncResult(null, this, null));
        }
    }

    private void sendIncomingCallRingNotification(int token) {
        if (this.mIsVoiceCapable && !this.mDoesRilSendMultipleCallRing && token == this.mCallRingContinueToken) {
            Rlog.d(LOG_TAG, "Sending notifyIncomingRing");
            notifyIncomingRing();
            sendMessageDelayed(obtainMessage(15, token, 0), (long) this.mCallRingDelay);
            return;
        }
        Rlog.d(LOG_TAG, "Ignoring ring notification request, mDoesRilSendMultipleCallRing=" + this.mDoesRilSendMultipleCallRing + " token=" + token + " mCallRingContinueToken=" + this.mCallRingContinueToken + " mIsVoiceCapable=" + this.mIsVoiceCapable);
    }

    public boolean isCspPlmnEnabled() {
        logUnexpectedGsmMethodCall("isCspPlmnEnabled");
        return false;
    }

    public IsimRecords getIsimRecords() {
        Rlog.e(LOG_TAG, "getIsimRecords() is only supported on LTE devices");
        return null;
    }

    public String getMsisdn() {
        logUnexpectedGsmMethodCall("getMsisdn");
        return null;
    }

    private static void logUnexpectedCdmaMethodCall(String name) {
        Rlog.e(LOG_TAG, "Error! " + name + "() in PhoneBase should not be " + "called, CDMAPhone inactive.");
    }

    public DataState getDataConnectionState() {
        return getDataConnectionState("default");
    }

    private static void logUnexpectedGsmMethodCall(String name) {
        Rlog.e(LOG_TAG, "Error! " + name + "() in PhoneBase should not be " + "called, GSMPhone inactive.");
    }

    public void notifyCallForwardingIndicator() {
        Rlog.e(LOG_TAG, "Error! This function should never be executed, inactive CDMAPhone.");
    }

    public void notifyDataConnectionFailed(String reason, String apnType) {
        this.mNotifier.notifyDataConnectionFailed(this, reason, apnType);
    }

    public void notifyPreciseDataConnectionFailed(String reason, String apnType, String apn, String failCause) {
        this.mNotifier.notifyPreciseDataConnectionFailed(this, reason, apnType, apn, failCause);
    }

    public int getLteOnCdmaMode() {
        return this.mCi.getLteOnCdmaMode();
    }

    public void setVoiceMessageWaiting(int line, int countWaiting) {
        IccRecords r = (IccRecords) this.mIccRecords.get();
        if (r != null) {
            r.setVoiceMessageWaiting(line, countWaiting);
        }
    }

    public void storeVoiceMailCount(int mwi) {
        IccRecords r = (IccRecords) this.mIccRecords.get();
        if (r != null) {
            r.storeVoiceMailCount(mwi);
        }
    }

    public UsimServiceTable getUsimServiceTable() {
        IccRecords r = (IccRecords) this.mIccRecords.get();
        return r != null ? r.getUsimServiceTable() : null;
    }

    public UiccCard getUiccCard() {
        return this.mUiccController.getUiccCard(this.mPhoneId);
    }

    public String[] getPcscfAddress(String apnType) {
        return this.mDcTracker.getPcscfAddress(apnType);
    }

    public void setImsRegistrationState(boolean registered) {
        this.mDcTracker.setImsRegistrationState(registered);
    }

    public Phone getImsPhone() {
        return this.mImsPhone;
    }

    public ImsPhone relinquishOwnershipOfImsPhone() {
        ImsPhone imsPhone = null;
        synchronized (this.mImsLock) {
            if (this.mImsPhone == null) {
            } else {
                imsPhone = this.mImsPhone;
                this.mImsPhone = null;
                CallManager.getInstance().unregisterPhone(imsPhone);
                imsPhone.unregisterForSilentRedial(this);
            }
        }
        return imsPhone;
    }

    public void acquireOwnershipOfImsPhone(ImsPhone imsPhone) {
        synchronized (this.mImsLock) {
            if (imsPhone == null) {
                return;
            }
            if (this.mImsPhone != null) {
                Rlog.e(LOG_TAG, "acquireOwnershipOfImsPhone: non-null mImsPhone. Shouldn't happen - but disposing");
                this.mImsPhone.dispose();
            }
            this.mImsPhone = imsPhone;
            this.mImsServiceReady = true;
            this.mImsPhone.updateParentPhone(this);
            CallManager.getInstance().registerPhone(this.mImsPhone);
            this.mImsPhone.registerForSilentRedial(this, 32, null);
        }
    }

    protected void updateImsPhone() {
        synchronized (this.mImsLock) {
            Rlog.d(LOG_TAG, "updateImsPhone mImsServiceReady=" + this.mImsServiceReady);
            if (this.mImsServiceReady && this.mImsPhone == null) {
                this.mImsPhone = PhoneFactory.makeImsPhone(this.mNotifier, this);
                CallManager.getInstance().registerPhone(this.mImsPhone);
                this.mImsPhone.registerForSilentRedial(this, 32, null);
            } else if (!(this.mImsServiceReady || this.mImsPhone == null)) {
                CallManager.getInstance().unregisterPhone(this.mImsPhone);
                this.mImsPhone.unregisterForSilentRedial(this);
                this.mImsPhone.dispose();
                this.mImsPhone = null;
            }
        }
    }

    protected Connection dialInternal(String dialString, UUSInfo uusInfo, int videoState) throws CallStateException {
        return null;
    }

    public Connection dial(String dialString, int videoState, int callType, int callDomain, String[] extras) throws CallStateException {
        return dial(dialString, null, videoState, callType, callDomain, extras);
    }

    public void sendEncodedUssd(byte[] ussdMessage, int ussdLength, int dcsCode) {
        logUnexpectedGsmMethodCall("sendEncodedUssd");
    }

    public void setmMmiInitBySTK(boolean set) {
        logUnexpectedGsmMethodCall("setmMmiInitBySTK");
    }

    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        pw.println("PhoneBase:");
        pw.println(" mCi=" + this.mCi);
        pw.println(" mDnsCheckDisabled=" + this.mDnsCheckDisabled);
        pw.println(" mDcTracker=" + this.mDcTracker);
        pw.println(" mDoesRilSendMultipleCallRing=" + this.mDoesRilSendMultipleCallRing);
        pw.println(" mCallRingContinueToken=" + this.mCallRingContinueToken);
        pw.println(" mCallRingDelay=" + this.mCallRingDelay);
        pw.println(" mIsTheCurrentActivePhone=" + this.mIsTheCurrentActivePhone);
        pw.println(" mIsVoiceCapable=" + this.mIsVoiceCapable);
        pw.println(" mIccRecords=" + this.mIccRecords.get());
        pw.println(" mUiccApplication=" + this.mUiccApplication.get());
        pw.println(" mSmsStorageMonitor=" + this.mSmsStorageMonitor);
        pw.println(" mSmsUsageMonitor=" + this.mSmsUsageMonitor);
        pw.flush();
        pw.println(" mLooper=" + this.mLooper);
        pw.println(" mContext=" + this.mContext);
        pw.println(" mNotifier=" + this.mNotifier);
        pw.println(" mSimulatedRadioControl=" + this.mSimulatedRadioControl);
        pw.println(" mUnitTestMode=" + this.mUnitTestMode);
        pw.println(" isDnsCheckDisabled()=" + isDnsCheckDisabled());
        pw.println(" getUnitTestMode()=" + getUnitTestMode());
        pw.println(" getState()=" + getState());
        pw.println(" getIccSerialNumber()=" + getIccSerialNumber());
        pw.println(" getIccRecordsLoaded()=" + getIccRecordsLoaded());
        pw.println(" getMessageWaitingIndicator()=" + getMessageWaitingIndicator());
        pw.println(" getCallForwardingIndicator()=" + getCallForwardingIndicator());
        pw.println(" isInEmergencyCall()=" + isInEmergencyCall());
        pw.flush();
        pw.println(" isInEcm()=" + isInEcm());
        pw.println(" getPhoneName()=" + getPhoneName());
        pw.println(" getPhoneType()=" + getPhoneType());
        pw.println(" getVoiceMessageCount()=" + getVoiceMessageCount());
        pw.println(" getActiveApnTypes()=" + getActiveApnTypes());
        pw.println(" isDataConnectivityPossible()=" + isDataConnectivityPossible());
        pw.println(" needsOtaServiceProvisioning=" + needsOtaServiceProvisioning());
        pw.println(" mCscChameleonFileExists=" + this.mCscChameleonFileExists);
        pw.println(" mEccNums=" + this.mEccNums);
    }

    public long getSubId() {
        return SubscriptionController.getInstance().getSubIdUsingPhoneId(this.mPhoneId);
    }

    public int getPhoneId() {
        return this.mPhoneId;
    }

    public Subscription getSubscriptionInfo() {
        return this.mSubscriptionData;
    }

    public int getVoicePhoneServiceState() {
        ImsPhone imsPhone = this.mImsPhone;
        if ((imsPhone == null || imsPhone.getServiceState().getState() != 0) && !isImsRegistered()) {
            return getServiceState().getState();
        }
        return 0;
    }

    public boolean setOperatorBrandOverride(String brand) {
        return false;
    }

    public boolean isRadioAvailable() {
        return this.mCi.getRadioState().isAvailable();
    }

    public void updateMessageWaitingIndicator(boolean mwi) {
    }

    public void updateMessageWaitingIndicatorWithCount(int mwi) {
    }

    public void shutdownRadio() {
        getServiceStateTracker().requestShutdown();
    }

    public void registerForModifyCallRequest(Handler h, int what, Object obj) {
        this.mCallModifyRegistrants.add(h, what, obj);
    }

    public void unregisterForModifyCallRequest(Handler h) {
        this.mCallModifyRegistrants.remove(h);
    }

    public void notifyModifyCallRequest(Connection c, Throwable e) {
        this.mCallModifyRegistrants.notifyRegistrants(new AsyncResult(null, c, e));
    }

    public boolean IsInternationalRoaming() {
        Rlog.e(LOG_TAG, "Error! IsInternationalRoaming should never be executed in GSM mode");
        return false;
    }

    public boolean IsDomesticRoaming() {
        Rlog.e(LOG_TAG, "Error! IsDomesticRoaming should never be executed in GSM mode");
        return false;
    }

    public void changeConnectionType(Message msg, Connection conn, int newCallType, Map<String, String> newExtras) throws CallStateException {
        if (conn != null) {
            conn.changeConnectionType(msg, newCallType, newExtras);
        } else {
            Rlog.e(LOG_TAG, "connection is null");
        }
    }

    public void acceptConnectionTypeChange(Connection conn, Map<String, String> newExtras) throws CallStateException {
        if (conn != null) {
            conn.acceptConnectionTypeChange(newExtras);
        } else {
            Rlog.e(LOG_TAG, "connection is null");
        }
    }

    public void rejectConnectionTypeChange(Connection conn) throws CallStateException {
        if (conn != null) {
            conn.rejectConnectionTypeChange();
        } else {
            Rlog.e(LOG_TAG, "connection is null");
        }
    }

    public int getProposedConnectionType(Connection conn) throws CallStateException {
        if (conn != null) {
            return conn.getProposedConnectionType();
        }
        Rlog.e(LOG_TAG, "connection is null");
        return 0;
    }

    public void setGbaBootstrappingParams(byte[] rand, String btid, String keyLifetime, Message onComplete) {
        if (onComplete != null) {
            onComplete.sendToTarget();
        }
    }

    public void saveEmergencyCallNumberSpec(String customerSpec) {
        Editor editor = PreferenceManager.getDefaultSharedPreferences(getContext()).edit();
        editor.putString(EMERGENCY_CALL_NUMBER_CUSTOMER_SPEC, customerSpec);
        editor.commit();
    }

    public String loadEmergencyCallNumberSpec() {
        String emergencyCallNumberSpec = PreferenceManager.getDefaultSharedPreferences(getContext()).getString(EMERGENCY_CALL_NUMBER_CUSTOMER_SPEC, null);
        if (emergencyCallNumberSpec == null || !emergencyCallNumberSpec.endsWith("    ")) {
            return emergencyCallNumberSpec;
        }
        int length = emergencyCallNumberSpec.length();
        if (length > 4) {
            return emergencyCallNumberSpec.substring(0, length - 4);
        }
        return emergencyCallNumberSpec;
    }

    public void setTransmitPower(int powerLevel, Message onCompleted) {
    }

    public void SimSlotActivation(boolean activation) {
    }

    public boolean setDrxMode(int drxMode) {
        boolean result = false;
        int drxValue = -1;
        switch (drxMode) {
            case 0:
                drxValue = 0;
                break;
            case 1:
                drxValue = getReducedCycleTime();
                break;
            default:
                Rlog.e(LOG_TAG, "setDrxMode: Wrong DRX mode(" + drxMode + ")");
                break;
        }
        if ("VZW-CDMA".equals("") && drxValue != -1) {
            result = setDrxValue(drxValue);
        }
        Rlog.d(LOG_TAG, "setDrxMode(Mode: " + drxMode + ", Value: " + drxValue + ", Result: " + result + ")");
        return result;
    }

    public int getDrxValue() {
        return this.mDrx;
    }

    private boolean setDrxValue(int drxValue) {
        this.mDrx = -1;
        return sendSetDrx(drxValue);
    }

    private boolean sendGetDrx() {
        Rlog.d(LOG_TAG, "sendGetDrx");
        boolean result = false;
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(bos);
        try {
            dos.writeByte(2);
            dos.writeByte(44);
            dos.writeShort(4);
            this.mCi.invokeOemRilRequestRaw(bos.toByteArray(), obtainMessage(1001));
            result = true;
            if (dos != null) {
                try {
                    dos.close();
                } catch (IOException e) {
                    Rlog.e(LOG_TAG, "Exception during sendGetDrx #2: " + e);
                }
            }
            if (bos != null) {
                bos.close();
            }
        } catch (IOException e2) {
            Rlog.e(LOG_TAG, "Exception during sendGetDrx #1: " + e2);
            if (dos != null) {
                try {
                    dos.close();
                } catch (IOException e22) {
                    Rlog.e(LOG_TAG, "Exception during sendGetDrx #2: " + e22);
                }
            }
            if (bos != null) {
                bos.close();
            }
        } catch (Throwable th) {
            if (dos != null) {
                try {
                    dos.close();
                } catch (IOException e222) {
                    Rlog.e(LOG_TAG, "Exception during sendGetDrx #2: " + e222);
                }
            }
            if (bos != null) {
                bos.close();
            }
        }
        return result;
    }

    private boolean sendSetDrx(int drxValue) {
        Rlog.d(LOG_TAG, "sendSetDrx(Value: " + drxValue + ")");
        boolean result = false;
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(bos);
        try {
            dos.writeByte(2);
            dos.writeByte(45);
            dos.writeShort(5);
            dos.writeByte(drxValue);
            this.mCi.invokeOemRilRequestRaw(bos.toByteArray(), obtainMessage(1002));
            result = true;
            if (dos != null) {
                try {
                    dos.close();
                } catch (IOException e) {
                    Rlog.e(LOG_TAG, "Exception during sendSetDrx #2: " + e);
                }
            }
            if (bos != null) {
                bos.close();
            }
        } catch (IOException e2) {
            Rlog.e(LOG_TAG, "Exception during sendSetDrx #1: " + e2);
            if (dos != null) {
                try {
                    dos.close();
                } catch (IOException e22) {
                    Rlog.e(LOG_TAG, "Exception during sendSetDrx #2: " + e22);
                }
            }
            if (bos != null) {
                bos.close();
            }
        } catch (Throwable th) {
            if (dos != null) {
                try {
                    dos.close();
                } catch (IOException e222) {
                    Rlog.e(LOG_TAG, "Exception during sendSetDrx #2: " + e222);
                }
            }
            if (bos != null) {
                bos.close();
            }
        }
        return result;
    }

    public boolean setReducedCycleTime(int cycleTime) {
        Exception e;
        Throwable th;
        boolean result = false;
        FileOutputStream outputStream = null;
        try {
            File drxDirectory = new File(DRX_DIRECTORYPATH);
            if (!drxDirectory.exists()) {
                boolean resultMkdir = drxDirectory.mkdirs();
                drxDirectory.setReadable(true);
                drxDirectory.setWritable(true);
                drxDirectory.setExecutable(true);
                Rlog.d(LOG_TAG, "setReducedCycleTime: Make DRX directory (PATH: /efs/drx, Result: " + resultMkdir + ")");
            }
            FileOutputStream outputStream2 = new FileOutputStream(DRX_FILEPATH);
            if (outputStream2 != null) {
                try {
                    outputStream2.write(new byte[]{(byte) cycleTime});
                    result = true;
                } catch (Exception e2) {
                    e = e2;
                    outputStream = outputStream2;
                    try {
                        Rlog.e(LOG_TAG, "Exception during setReducedCycleTime #1: " + e);
                        if (outputStream != null) {
                            try {
                                outputStream.close();
                            } catch (IOException e3) {
                                Rlog.e(LOG_TAG, "Exception during setReducedCycleTime #2: " + e3);
                            }
                        }
                        Rlog.d(LOG_TAG, "setReducedCycleTime(Time: " + cycleTime + ", Result: " + result + ")");
                        return result;
                    } catch (Throwable th2) {
                        th = th2;
                        if (outputStream != null) {
                            try {
                                outputStream.close();
                            } catch (IOException e32) {
                                Rlog.e(LOG_TAG, "Exception during setReducedCycleTime #2: " + e32);
                            }
                        }
                        throw th;
                    }
                } catch (Throwable th3) {
                    th = th3;
                    outputStream = outputStream2;
                    if (outputStream != null) {
                        outputStream.close();
                    }
                    throw th;
                }
            }
            if (outputStream2 != null) {
                try {
                    outputStream2.close();
                } catch (IOException e322) {
                    Rlog.e(LOG_TAG, "Exception during setReducedCycleTime #2: " + e322);
                    outputStream = outputStream2;
                }
            }
            outputStream = outputStream2;
        } catch (Exception e4) {
            e = e4;
            Rlog.e(LOG_TAG, "Exception during setReducedCycleTime #1: " + e);
            if (outputStream != null) {
                outputStream.close();
            }
            Rlog.d(LOG_TAG, "setReducedCycleTime(Time: " + cycleTime + ", Result: " + result + ")");
            return result;
        }
        Rlog.d(LOG_TAG, "setReducedCycleTime(Time: " + cycleTime + ", Result: " + result + ")");
        return result;
    }

    public int getReducedCycleTime() {
        Exception e;
        Throwable th;
        int cycleTime = 7;
        FileInputStream inputStream = null;
        try {
            FileInputStream inputStream2 = new FileInputStream(DRX_FILEPATH);
            if (inputStream2 != null) {
                try {
                    byte[] inputData = new byte[1];
                    inputStream2.read(inputData);
                    cycleTime = inputData[0] & 255;
                } catch (Exception e2) {
                    e = e2;
                    inputStream = inputStream2;
                    try {
                        Rlog.e(LOG_TAG, "Exception during getReducedCycleTime #1: " + e);
                        if (inputStream != null) {
                            try {
                                inputStream.close();
                            } catch (IOException e3) {
                                Rlog.e(LOG_TAG, "Exception during getReducedCycleTime #2: " + e3);
                            }
                        }
                        Rlog.d(LOG_TAG, "getReducedCycleTime(Time: " + cycleTime + ")");
                        return cycleTime;
                    } catch (Throwable th2) {
                        th = th2;
                        if (inputStream != null) {
                            try {
                                inputStream.close();
                            } catch (IOException e32) {
                                Rlog.e(LOG_TAG, "Exception during getReducedCycleTime #2: " + e32);
                            }
                        }
                        throw th;
                    }
                } catch (Throwable th3) {
                    th = th3;
                    inputStream = inputStream2;
                    if (inputStream != null) {
                        inputStream.close();
                    }
                    throw th;
                }
            }
            if (inputStream2 != null) {
                try {
                    inputStream2.close();
                } catch (IOException e322) {
                    Rlog.e(LOG_TAG, "Exception during getReducedCycleTime #2: " + e322);
                    inputStream = inputStream2;
                }
            }
            inputStream = inputStream2;
        } catch (Exception e4) {
            e = e4;
            Rlog.e(LOG_TAG, "Exception during getReducedCycleTime #1: " + e);
            if (inputStream != null) {
                inputStream.close();
            }
            Rlog.d(LOG_TAG, "getReducedCycleTime(Time: " + cycleTime + ")");
            return cycleTime;
        }
        Rlog.d(LOG_TAG, "getReducedCycleTime(Time: " + cycleTime + ")");
        return cycleTime;
    }

    public boolean isImsRegistered() {
        if (this.mLegacyIms != null) {
            return this.mLegacyIms.isImsRegistered();
        }
        return false;
    }

    public boolean isVolteRegistered() {
        if (this.mLegacyIms != null) {
            return this.mLegacyIms.isVolteRegistered();
        }
        return false;
    }

    public boolean isWfcRegistered() {
        if (this.mLegacyIms != null) {
            return this.mLegacyIms.isWfcRegistered();
        }
        return false;
    }

    public int getImsRegisteredFeature(int connectivityType) {
        if (this.mLegacyIms == null) {
            return 0;
        }
        return this.mLegacyIms.getFeatureMask(this.mLegacyIms.convertNetworkType(connectivityType));
    }

    public int getImsRegisteredFeature() {
        if (this.mLegacyIms != null) {
            return this.mLegacyIms.getFeatureMask(0) | this.mLegacyIms.getFeatureMask(1);
        }
        return 0;
    }

    public LegacyIms getLegacyIms() {
        return this.mLegacyIms;
    }

    public String getVoiceMailNumberForGlobalMode() {
        boolean isRoaming = getServiceState().getRoaming();
        String mdn = getLine1Number();
        String iso = TelephonyManager.getDefault().getNetworkCountryIso();
        if (isRoaming && getPhoneType() == 1) {
            return mdn != null ? "+1" + mdn : mdn;
        } else {
            if ("VZW-CDMA".equals("") && isRoaming && getPhoneType() == 2 && !"us".equals(iso)) {
                if (mdn == null) {
                    return mdn;
                }
                if ("NANP".equals(PhoneNumberUtils.getCurrentOtaCountryNanp(this.mContext))) {
                    return "1" + mdn;
                }
                return PhoneNumberUtils.getCurrentOtaCountryIdd(this.mContext) + "1" + mdn;
            } else if ("VZW-CDMA".equals("")) {
                return "*86";
            } else {
                if ("SPR-CDMA".equals("") && ("us".equals(iso) || "ca".equals(iso))) {
                    return mdn;
                }
                if (!"SPR-CDMA".equals("")) {
                    return !this.mContext.getResources().getBoolean(17956940) ? "*86" : mdn;
                } else {
                    if (mdn != null) {
                        return "+1" + mdn;
                    }
                    return mdn;
                }
            }
        }
    }

    public void registerForDataConnectionStateChanged(Handler h, int what, Object obj) {
        this.mDataConnectionStateChangedRegistrants.add(new Registrant(h, what, obj));
    }

    public void unregisterForDataConnectionStateChanged(Handler h) {
        this.mDataConnectionStateChangedRegistrants.remove(h);
    }

    public void notifyDataConnectionStateChanged(int state, String reason, String apnType) {
        this.mDataConnectionStateChangedRegistrants.notifyResult(new Pair(Integer.valueOf(state), new String[]{reason, apnType}));
    }

    public boolean hasCall(String callType) {
        return false;
    }

    public boolean setDmCmdInfo(int cmd, byte[] info) {
        boolean z = false;
        switch (cmd) {
            case 7:
                CallTracker calltracker = getCallTracker();
                if (calltracker == null) {
                    return false;
                }
                if (info[0] == (byte) 0) {
                    z = true;
                }
                calltracker.setDmHdvAlarmEvent(z);
                return true;
            default:
                return false;
        }
    }

    public CatService getCatService() {
        UiccCard uiccCard = this.mUiccController.getUiccCard();
        if (uiccCard != null) {
            return uiccCard.getCatService();
        }
        Rlog.e(LOG_TAG, "Failed to get UiccCard in PhoneProxy for getCatService");
        return null;
    }

    public boolean getFDNavailable() {
        Rlog.e(LOG_TAG, "Used in ActivePhone");
        return false;
    }

    protected void notifyCardStateChanged(int slotId, int subStatus) {
        Intent intent;
        SubscriptionController subCtrlr = SubscriptionController.getInstance();
        long[] subId = subCtrlr.getSubIdUsingSlotId(slotId);
        int currentSubStatus = SubscriptionController.getInstance().getSubState(subId[0]);
        int phoneOnState = System.getInt(this.mContext.getContentResolver(), mPhoneOnKey[slotId], -1);
        if (subStatus != currentSubStatus) {
            subCtrlr.setSubState(subId[0], subStatus);
        }
        if (subStatus != phoneOnState) {
            System.putInt(this.mContext.getContentResolver(), mPhoneOnKey[slotId], subStatus);
        }
        if (subStatus == 1) {
            intent = new Intent(mCardOnIntent[slotId]);
        } else {
            intent = new Intent(mCardOffIntent[slotId]);
        }
        ActivityManagerNative.broadcastStickyIntent(intent, null, -1);
    }

    public boolean isApnTypeAvailable(String apnType) {
        return this.mDcTracker != null && this.mDcTracker.isApnTypeAvailable(apnType);
    }
}
