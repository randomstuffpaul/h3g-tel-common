package com.android.internal.telephony.dataconnection;

import android.app.AlarmManager;
import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.app.PendingIntent;
import android.app.ProgressDialog;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources.NotFoundException;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.ConnectivityManager;
import android.net.IConnectivityManager;
import android.net.LinkProperties;
import android.net.NetworkCapabilities;
import android.net.NetworkConfig;
import android.net.NetworkFactory;
import android.net.NetworkRequest;
import android.net.NetworkUtils;
import android.net.ProxyInfo;
import android.net.Uri;
import android.os.AsyncResult;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.os.RegistrantList;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.provider.Settings.Global;
import android.provider.Settings.Secure;
import android.provider.Settings.System;
import android.provider.Telephony.Carriers;
import android.telephony.CellLocation;
import android.telephony.PhoneStateListener;
import android.telephony.Rlog;
import android.telephony.ServiceState;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.telephony.cdma.CdmaCellLocation;
import android.telephony.gsm.GsmCellLocation;
import android.text.TextUtils;
import android.util.EventLog;
import android.util.secutil.Log;
import com.android.internal.telephony.DctConstants.State;
import com.android.internal.telephony.EventLogTags;
import com.android.internal.telephony.ITelephony.Stub;
import com.android.internal.telephony.IccCard;
import com.android.internal.telephony.PDPContextStateBroadcaster;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneBase;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.PhoneConstants.DataState;
import com.android.internal.telephony.PhoneFactory;
import com.android.internal.telephony.PhoneProxy;
import com.android.internal.telephony.ServiceStateTracker;
import com.android.internal.telephony.SubscriptionController;
import com.android.internal.telephony.WspTypeDecoder;
import com.android.internal.telephony.cdma.CDMALTEPhone;
import com.android.internal.telephony.cdma.CDMAPhone;
import com.android.internal.telephony.cdma.CdmaSubscriptionSourceManager;
import com.android.internal.telephony.gsm.CallFailCause;
import com.android.internal.telephony.gsm.GSMPhone;
import com.android.internal.telephony.uicc.IccRecords;
import com.android.internal.util.ArrayUtils;
import com.google.android.mms.pdu.CharacterSets;
import com.itsoninc.android.ItsOnOemApi;
import com.samsung.android.feature.FloatingFeature;
import com.samsung.commonimsinterface.imsinterface.CommonIMSInterface;
import com.samsung.commonimsinterface.imsinterface.IMSInterfaceForGeneral;
import com.sec.android.app.CscFeature;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.FileDescriptor;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

public final class DcTracker extends DcTrackerBase {
    static final String APN_ID = "apn_id";
    private static final int CMD_SET_LTE_ROAMING_STATUS = 117;
    static final Uri CURRENT_URI = Uri.parse("content://telephony/carriers/current");
    static final String LOG_LEVEL_PROP = "ro.debug_level";
    static final String LOG_LEVEL_PROP_HIGH = "0x4948";
    static final String LOG_LEVEL_PROP_LOW = "0x4f4c";
    static final String LOG_LEVEL_PROP_MID = "0x494d";
    private static final int POLL_PDP_MILLIS = 5000;
    static final Uri PREFERAPN_NO_UPDATE_URI = Uri.parse("content://telephony/carriers/preferapn_no_update");
    static final Uri PREFERAPN_NO_UPDATE_URI_4G = Uri.parse("content://telephony/carriers/preferapn_no_update_4G");
    static final Uri PREFERAPN_NO_UPDATE_URI_USING_SUBID = Uri.parse("content://telephony/carriers/preferapn_no_update/subId/");
    private static final int PROVISIONING_SPINNER_TIMEOUT_MILLIS = 120000;
    private static final String PUPPET_MASTER_RADIO_STRESS_TEST = "gsm.defaultpdpcontext.active";
    protected final String LOG_TAG = "DCT";
    private int insertedSimCount = 0;
    private RegistrantList mAllDataDisconnectedRegistrants = new RegistrantList();
    private ItsOnOemApi mApi;
    private ApnChangeObserver mApnObserver;
    private AtomicBoolean mAttached = new AtomicBoolean(false);
    int mBeforeDataNetworkType = 0;
    private boolean mCanSetPreferApn = false;
    private CdmaSubscriptionSourceManager mCdmaSSM = null;
    private boolean mDeregistrationAlarmState = false;
    private ArrayList<Message> mDisconnectAllCompleteMsgList = new ArrayList();
    protected int mDisconnectPendingCount = 0;
    private PendingIntent mImsDeregistrationDelayIntent = null;
    public boolean mImsRegistrationState = false;
    private boolean mIsUpdated = false;
    private NetworkFactory mNetworkFactory;
    private Messenger mNetworkFactoryMessenger;
    private NetworkCapabilities mNetworkFilter;
    private PhoneStateListener mPhoneStateListener = new C00911();
    private boolean mPollingStopped = false;
    private final String mProvisionActionName;
    private BroadcastReceiver mProvisionBroadcastReceiver;
    private ProgressDialog mProvisioningSpinner;
    private final BroadcastReceiver mReceiver = new C00944();
    private boolean mReregisterOnReconnectFailure = false;
    private BroadcastReceiver mSIMStatusBroadcastReceiver;
    private Intent mStickyIntentForACM = null;
    private TelephonyManager mTelephonyManager = null;
    private boolean mTetherRequested = false;
    private boolean mVolteTurnOff = false;
    private ApnContext mWaitCleanUpApnContext = null;
    private boolean misVolteOn = false;
    private int retryTCECounter = 0;

    class C00911 extends PhoneStateListener {
        C00911() {
        }

        public void onServiceStateChanged(ServiceState serviceState) {
        }
    }

    class C00922 implements Comparator<ApnContext> {
        C00922() {
        }

        public int compare(ApnContext c1, ApnContext c2) {
            return c2.priority - c1.priority;
        }
    }

    class C00933 implements OnClickListener {
        C00933() {
        }

        public void onClick(DialogInterface dialog, int which) {
            dialog.dismiss();
        }
    }

    class C00944 extends BroadcastReceiver {
        C00944() {
        }

        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            DcTracker.this.log("mReceiver action: " + action);
            if (action.equals("android.intent.action.ACTION_SUBINFO_RECORD_UPDATED") || action.equals("android.intent.action.ACTION_SUBINFO_CONTENT_CHANGE")) {
                DcTracker.this.getInsertedSimCount();
            }
        }
    }

    static /* synthetic */ class C00955 {
        static final /* synthetic */ int[] $SwitchMap$com$android$internal$telephony$DctConstants$State = new int[State.values().length];

        static {
            try {
                $SwitchMap$com$android$internal$telephony$DctConstants$State[State.CONNECTED.ordinal()] = 1;
            } catch (NoSuchFieldError e) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$DctConstants$State[State.DISCONNECTING.ordinal()] = 2;
            } catch (NoSuchFieldError e2) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$DctConstants$State[State.RETRYING.ordinal()] = 3;
            } catch (NoSuchFieldError e3) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$DctConstants$State[State.CONNECTING.ordinal()] = 4;
            } catch (NoSuchFieldError e4) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$DctConstants$State[State.IDLE.ordinal()] = 5;
            } catch (NoSuchFieldError e5) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$DctConstants$State[State.SCANNING.ordinal()] = 6;
            } catch (NoSuchFieldError e6) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$DctConstants$State[State.FAILED.ordinal()] = 7;
            } catch (NoSuchFieldError e7) {
            }
        }
    }

    private class ApnChangeObserver extends ContentObserver {
        public ApnChangeObserver() {
            super(DcTracker.this.mDataConnectionTracker);
        }

        public void onChange(boolean selfChange) {
            DcTracker.this.sendMessage(DcTracker.this.obtainMessage(270355));
        }
    }

    private class ProvisionNotificationBroadcastReceiver extends BroadcastReceiver {
        private final String mNetworkOperator;
        private final String mProvisionUrl;

        public ProvisionNotificationBroadcastReceiver(String provisionUrl, String networkOperator) {
            this.mNetworkOperator = networkOperator;
            this.mProvisionUrl = provisionUrl;
        }

        private void setEnableFailFastMobileData(int enabled) {
            DcTracker.this.sendMessage(DcTracker.this.obtainMessage(270372, enabled, 0));
        }

        private void enableMobileProvisioning() {
            Message msg = DcTracker.this.obtainMessage(270373);
            msg.setData(Bundle.forPair("provisioningUrl", this.mProvisionUrl));
            DcTracker.this.sendMessage(msg);
        }

        public void onReceive(Context context, Intent intent) {
            DcTracker.this.mProvisioningSpinner = new ProgressDialog(context);
            DcTracker.this.mProvisioningSpinner.setTitle(this.mNetworkOperator);
            DcTracker.this.mProvisioningSpinner.setMessage(context.getText(17040860));
            DcTracker.this.mProvisioningSpinner.setIndeterminate(true);
            DcTracker.this.mProvisioningSpinner.setCancelable(true);
            DcTracker.this.mProvisioningSpinner.getWindow().setType(2009);
            DcTracker.this.mProvisioningSpinner.show();
            DcTracker.this.sendMessageDelayed(DcTracker.this.obtainMessage(270378, DcTracker.this.mProvisioningSpinner), 120000);
            DcTracker.this.setRadio(true);
            setEnableFailFastMobileData(1);
            enableMobileProvisioning();
        }
    }

    private class SIMStatusBroadcastReceiver extends BroadcastReceiver {

        class C00961 extends Thread {
            C00961() {
            }

            public void run() {
                IccRecords r = (IccRecords) DcTracker.this.mIccRecords.get();
                while (true) {
                    if (TextUtils.isEmpty(r != null ? r.getOperatorNumeric() : "")) {
                        try {
                            DcTracker.this.log("SIMStatusBroadcastReceiver: SIMRecords / getOperatorNumeric returned null");
                            Thread.sleep(10);
                        } catch (InterruptedException e) {
                        }
                    } else {
                        DcTracker.this.log("SIMStatusBroadcastReceiver: SIMRecords / getOperatorNumeric returned : " + r.getIMSI());
                        DcTracker.this.createAllApnList();
                        DcTracker.this.setInitialAttachApn();
                        return;
                    }
                }
            }
        }

        private SIMStatusBroadcastReceiver() {
        }

        public void onReceive(Context context, Intent intent) {
            if ("IMSI".equals(intent.getStringExtra(IccCard.INTENT_KEY_ICC_STATE))) {
                DcTracker.this.log("SIMStatusBroadcastReceiver: INTENT_VALUE_ICC_IMSI");
                new C00961().start();
            }
        }
    }

    private class TelephonyNetworkFactory extends NetworkFactory {
        public TelephonyNetworkFactory(Looper l, Context c, String TAG, NetworkCapabilities nc) {
            super(l, c, TAG, nc);
        }

        protected void needNetworkFor(NetworkRequest networkRequest, int score) {
            log("Cellular needs Network for " + networkRequest);
            ApnContext apnContext = DcTracker.this.apnContextForNetworkRequest(networkRequest);
            if (apnContext != null) {
                apnContext.incRefCount();
            }
        }

        protected void releaseNetworkFor(NetworkRequest networkRequest) {
            log("Cellular releasing Network for " + networkRequest);
            ApnContext apnContext = DcTracker.this.apnContextForNetworkRequest(networkRequest);
            if (apnContext != null) {
                apnContext.decRefCount();
            }
        }
    }

    public DcTracker(PhoneBase p) {
        super(p);
        log("GsmDCT.constructor");
        if (CscFeature.getInstance().getEnableStatus("CscFeature_Common_EnableItsOn")) {
            this.mApi = ItsOnOemApi.getInstance();
            this.mApi.setDataConnectionHandler(this, obtainMessage(270339, Phone.REASON_ROAMING_ON));
        }
        this.mDataConnectionTracker = this;
        update();
        this.mApnObserver = new ApnChangeObserver();
        p.getContext().getContentResolver().registerContentObserver(Carriers.CONTENT_URI, true, this.mApnObserver);
        initApnContexts();
        for (ApnContext apnContext : this.mApnContexts.values()) {
            IntentFilter filter = new IntentFilter();
            filter.addAction("com.android.internal.telephony.data-reconnect." + apnContext.getApnType());
            filter.addAction("com.android.internal.telephony.data-restart-trysetup." + apnContext.getApnType());
            this.mPhone.getContext().registerReceiver(this.mIntentReceiver, filter, null, this.mPhone);
        }
        IntentFilter mfilter = new IntentFilter();
        mfilter.addAction("android.intent.action.ACTION_SUBINFO_RECORD_UPDATED");
        mfilter.addAction("android.intent.action.ACTION_SUBINFO_CONTENT_CHANGE");
        this.mPhone.getContext().registerReceiver(this.mReceiver, mfilter);
        ConnectivityManager cm = (ConnectivityManager) p.getContext().getSystemService("connectivity");
        this.mNetworkFilter = new NetworkCapabilities();
        this.mNetworkFilter.addTransportType(0);
        if (!"DCGG".equals("DGG") && !"DGG".equals("DGG") && !"DCGS".equals("DGG") && !"DCGGS".equals("DGG")) {
            this.mNetworkFilter.addCapability(0);
        } else if (this.mPhone.getPhoneId() == 0) {
            this.mNetworkFilter.addCapability(0);
        } else if (this.mPhone.getPhoneId() == 1) {
            this.mNetworkFilter.addCapability(18);
        }
        this.mNetworkFilter.addCapability(1);
        this.mNetworkFilter.addCapability(2);
        this.mNetworkFilter.addCapability(3);
        this.mNetworkFilter.addCapability(4);
        this.mNetworkFilter.addCapability(5);
        this.mNetworkFilter.addCapability(7);
        this.mNetworkFilter.addCapability(8);
        this.mNetworkFilter.addCapability(9);
        this.mNetworkFilter.addCapability(10);
        this.mNetworkFilter.addCapability(13);
        this.mNetworkFilter.addCapability(12);
        this.mNetworkFilter.addCapability(17);
        this.mNetworkFilter.addCapability(19);
        this.mNetworkFactory = new TelephonyNetworkFactory(getLooper(), p.getContext(), "TelephonyNetworkFactory", this.mNetworkFilter);
        this.mNetworkFactory.setScoreFilter(50);
        this.mNetworkFactoryMessenger = new Messenger(this.mNetworkFactory);
        if (!"DCGG".equals("DGG") && !"DGG".equals("DGG") && !"DCGS".equals("DGG") && !"DCGGS".equals("DGG")) {
            cm.registerNetworkFactory(this.mNetworkFactoryMessenger, "Telephony");
        } else if (this.mPhone.getPhoneId() == 0) {
            cm.registerNetworkFactory(this.mNetworkFactoryMessenger, "Telephony");
        } else if (this.mPhone.getPhoneId() == 1) {
            cm.registerNetworkFactory(this.mNetworkFactoryMessenger, "Telephony2");
        }
        if (CscFeature.getInstance().getEnableStatus("CscFeature_CIQ_BroadcastState")) {
            PDPContextStateBroadcaster.enable();
        }
        initEmergencyApnSetting();
        addEmergencyApnSetting();
        getInsertedSimCount();
        this.mProvisionActionName = "com.android.internal.telephony.PROVISION" + p.getPhoneId();
        this.mTelephonyManager = TelephonyManager.getDefault();
        this.mTelephonyManager.listen(this.mPhoneStateListener, 0);
    }

    protected void registerForAllEvents() {
        this.mPhone.mCi.registerForAvailable(this, 270337, null);
        this.mPhone.mCi.registerForOffOrNotAvailable(this, 270342, null);
        this.mPhone.mCi.registerForDataNetworkStateChanged(this, 270340, null);
        this.mPhone.getCallTracker().registerForVoiceCallEnded(this, 270344, null);
        this.mPhone.getCallTracker().registerForVoiceCallStarted(this, 270343, null);
        this.mPhone.getServiceStateTracker().registerForDataConnectionAttached(this, 270352, null);
        this.mPhone.getServiceStateTracker().registerForDataConnectionDetached(this, 270345, null);
        this.mPhone.getServiceStateTracker().registerForRoamingOn(this, 270347, null);
        this.mPhone.getServiceStateTracker().registerForRoamingOff(this, 270348, null);
        this.mPhone.getServiceStateTracker().registerForPsRestrictedEnabled(this, 270358, null);
        this.mPhone.getServiceStateTracker().registerForPsRestrictedDisabled(this, 270359, null);
        this.mPhone.getServiceStateTracker().registerForDataRegStateOrRatChanged(this, 270377, null);
        this.mPhone.getServiceStateTracker().registerForPlmnChanged(this, 270444, null);
        this.mPhone.getServiceStateTracker().registerForRoutingAreaChanged(this, 270439, null);
    }

    public void dispose() {
        log("GsmDCT.dispose");
        if (CscFeature.getInstance().getEnableStatus("CscFeature_CIQ_BroadcastState")) {
            PDPContextStateBroadcaster.disable();
        }
        if (this.mProvisionBroadcastReceiver != null) {
            this.mPhone.getContext().unregisterReceiver(this.mProvisionBroadcastReceiver);
            this.mProvisionBroadcastReceiver = null;
        }
        if (this.mProvisioningSpinner != null) {
            this.mProvisioningSpinner.dismiss();
            this.mProvisioningSpinner = null;
        }
        ((ConnectivityManager) this.mPhone.getContext().getSystemService("connectivity")).unregisterNetworkFactory(this.mNetworkFactoryMessenger);
        this.mNetworkFactoryMessenger = null;
        cleanUpAllConnections(true, null);
        super.dispose();
        this.mPhone.getContext().getContentResolver().unregisterContentObserver(this.mApnObserver);
        this.mApnContexts.clear();
        this.mPrioritySortedApnContexts.clear();
        this.mTelephonyManager.listen(this.mPhoneStateListener, 0);
        destroyDataConnections();
    }

    protected void unregisterForAllEvents() {
        this.mPhone.mCi.unregisterForAvailable(this);
        this.mPhone.mCi.unregisterForOffOrNotAvailable(this);
        IccRecords r = (IccRecords) this.mIccRecords.get();
        if (r != null) {
            r.unregisterForRecordsLoaded(this);
            this.mIccRecords.set(null);
        }
        if ("VZW-CDMA".equals("")) {
            IccRecords usimIccRecords = getUiccRecords(1);
            if (usimIccRecords != null) {
                usimIccRecords.unregisterForRecordsLoaded(this);
            }
        }
        this.mPhone.mCi.unregisterForDataNetworkStateChanged(this);
        this.mPhone.getCallTracker().unregisterForVoiceCallEnded(this);
        this.mPhone.getCallTracker().unregisterForVoiceCallStarted(this);
        this.mPhone.getServiceStateTracker().unregisterForDataConnectionAttached(this);
        this.mPhone.getServiceStateTracker().unregisterForDataConnectionDetached(this);
        this.mPhone.getServiceStateTracker().unregisterForRoamingOn(this);
        this.mPhone.getServiceStateTracker().unregisterForRoamingOff(this);
        this.mPhone.getServiceStateTracker().unregisterForPsRestrictedEnabled(this);
        this.mPhone.getServiceStateTracker().unregisterForPsRestrictedDisabled(this);
        this.mPhone.unregisterForSubscriptionInfoReady(this);
        this.mPhone.getServiceStateTracker().unregisterForPlmnChanged(this);
        this.mPhone.getServiceStateTracker().unregisterForRoutingAreaChanged(this);
    }

    private ApnContext apnContextForNetworkRequest(NetworkRequest nr) {
        ApnContext apnContext = null;
        NetworkCapabilities nc = nr.networkCapabilities;
        if (nc.getTransportTypes().length <= 0 || nc.hasTransport(0)) {
            int type = -1;
            String name = null;
            boolean error = false;
            if (nc.hasCapability(12)) {
                if (null != null) {
                    error = true;
                }
                if (nr.legacyType == 5) {
                    name = "hipri";
                    type = 5;
                } else {
                    name = "default";
                    type = 0;
                }
            }
            if (nc.hasCapability(0)) {
                if (name != null) {
                    error = true;
                }
                name = "mms";
                type = 2;
            }
            if (nc.hasCapability(18)) {
                if (name != null) {
                    error = true;
                }
                name = "mms2";
                type = 26;
            }
            if (nc.hasCapability(1)) {
                if (name != null) {
                    error = true;
                }
                name = "supl";
                type = 3;
            }
            if (nc.hasCapability(2)) {
                if (name != null) {
                    error = true;
                }
                name = "dun";
                type = 4;
            }
            if (nc.hasCapability(3)) {
                if (name != null) {
                    error = true;
                }
                name = "fota";
                type = 10;
            }
            if (nc.hasCapability(4)) {
                if (name != null) {
                    error = true;
                }
                name = "ims";
                type = 11;
            }
            if (nc.hasCapability(5)) {
                if (name != null) {
                    error = true;
                }
                name = "cbs";
                type = 12;
            }
            if (nc.hasCapability(7)) {
                if (name != null) {
                    error = true;
                }
                name = "ia";
                type = 14;
            }
            if (nc.hasCapability(8)) {
                if (name != null) {
                    error = true;
                }
                name = null;
                loge("RCS APN type not yet supported");
            }
            if (nc.hasCapability(9)) {
                if (name != null) {
                    error = true;
                }
                name = "xcap";
                type = 27;
            }
            if (nc.hasCapability(10)) {
                if (name != null) {
                    error = true;
                }
                name = null;
                loge("EIMS APN type not yet supported");
            }
            if (nc.hasCapability(17)) {
                if (name != null) {
                    error = true;
                }
                name = "ent1";
                type = 28;
            }
            if (nc.hasCapability(19)) {
                if (name != null) {
                    error = true;
                }
                name = "bip";
                type = 23;
            }
            if (error) {
                loge("Multiple apn types specified in request - result is unspecified!");
            }
            if (type == -1 || name == null) {
                loge("Unsupported NetworkRequest in Telephony: " + nr);
            } else {
                apnContext = (ApnContext) this.mApnContexts.get(name);
                if (apnContext == null) {
                    loge("Request for unsupported mobile type: " + type);
                }
            }
        }
        return apnContext;
    }

    private void setRadio(boolean on) {
        try {
            Stub.asInterface(ServiceManager.checkService("phone")).setRadio(on);
        } catch (Exception e) {
        }
    }

    public boolean isApnTypeActive(String type) {
        ApnContext apnContext = (ApnContext) this.mApnContexts.get(type);
        if (apnContext == null || apnContext.getDcAc() == null) {
            return false;
        }
        return true;
    }

    public boolean isDataPossible(String apnType) {
        ApnContext apnContext = (ApnContext) this.mApnContexts.get(apnType);
        if (apnContext == null) {
            return false;
        }
        boolean apnTypePossible;
        boolean dataAllowed;
        boolean possible;
        boolean apnContextIsEnabled = apnContext.isEnabled();
        State apnContextState = apnContext.getState();
        if (apnContextIsEnabled && apnContextState == State.FAILED) {
            apnTypePossible = false;
        } else {
            apnTypePossible = true;
        }
        if (apnContext.getApnType().equals("emergency") || isDataAllowed()) {
            dataAllowed = true;
        } else {
            dataAllowed = false;
        }
        if (dataAllowed && apnTypePossible) {
            possible = true;
        } else {
            possible = false;
        }
        if (isDataAllowedOnDataDisabled(apnContext)) {
            if (apnType.equals("ims")) {
                possible = apnTypePossible;
            } else {
                possible = true;
            }
        }
        log(String.format("isDataPossible(%s): possible=%b isDataAllowed=%b apnTypePossible=%b apnContextisEnabled=%b apnContextState()=%s isDataAllowedOnDataDisabled =%b", new Object[]{apnType, Boolean.valueOf(possible), Boolean.valueOf(dataAllowed), Boolean.valueOf(apnTypePossible), Boolean.valueOf(apnContextIsEnabled), apnContextState, Boolean.valueOf(isDataAllowedOnDataDisabled(apnContext))}));
        return possible;
    }

    protected void finalize() {
        log("finalize");
    }

    protected void supplyMessenger() {
        if (isActiveDataSubscription()) {
            ConnectivityManager cm = (ConnectivityManager) this.mPhone.getContext().getSystemService("connectivity");
            cm.supplyMessenger(0, new Messenger(this));
            cm.supplyMessenger(2, new Messenger(this));
            cm.supplyMessenger(3, new Messenger(this));
            cm.supplyMessenger(4, new Messenger(this));
            cm.supplyMessenger(5, new Messenger(this));
            cm.supplyMessenger(10, new Messenger(this));
            cm.supplyMessenger(11, new Messenger(this));
            cm.supplyMessenger(12, new Messenger(this));
            cm.supplyMessenger(15, new Messenger(this));
        }
    }

    private ApnContext addApnContext(String type, NetworkConfig networkConfig) {
        ApnContext apnContext = new ApnContext(this.mPhone.getContext(), type, "DCT", networkConfig, this);
        this.mApnContexts.put(type, apnContext);
        this.mPrioritySortedApnContexts.add(apnContext);
        return apnContext;
    }

    protected void initApnContexts() {
        log("initApnContexts: E");
        for (String networkConfigString : this.mPhone.getContext().getResources().getStringArray(17236008)) {
            ApnContext apnContext;
            NetworkConfig networkConfig = new NetworkConfig(networkConfigString);
            switch (networkConfig.type) {
                case 0:
                    apnContext = addApnContext("default", networkConfig);
                    break;
                case 2:
                    apnContext = addApnContext("mms", networkConfig);
                    break;
                case 3:
                    apnContext = addApnContext("supl", networkConfig);
                    break;
                case 4:
                    apnContext = addApnContext("dun", networkConfig);
                    break;
                case 5:
                    apnContext = addApnContext("hipri", networkConfig);
                    break;
                case 10:
                    apnContext = addApnContext("fota", networkConfig);
                    break;
                case 11:
                    apnContext = addApnContext("ims", networkConfig);
                    break;
                case 12:
                    apnContext = addApnContext("cbs", networkConfig);
                    break;
                case 14:
                    apnContext = addApnContext("ia", networkConfig);
                    break;
                case 15:
                    apnContext = addApnContext("emergency", networkConfig);
                    break;
                case 20:
                    apnContext = addApnContext("cmdm", networkConfig);
                    break;
                case 21:
                    apnContext = addApnContext("cmmail", networkConfig);
                    break;
                case 22:
                    apnContext = addApnContext("wap", networkConfig);
                    break;
                case 23:
                    apnContext = addApnContext("bip", networkConfig);
                    break;
                case 24:
                    apnContext = addApnContext("cas", networkConfig);
                    break;
                case 26:
                    apnContext = addApnContext("mms2", networkConfig);
                    break;
                case WspTypeDecoder.WSP_HEADER_IF_UNMODIFIED_SINCE /*27*/:
                    apnContext = addApnContext("xcap", networkConfig);
                    break;
                case WspTypeDecoder.WSP_HEADER_LOCATION /*28*/:
                    apnContext = addApnContext("ent1", networkConfig);
                    break;
                case WspTypeDecoder.WSP_HEADER_LAST_MODIFIED /*29*/:
                    apnContext = addApnContext("ent2", networkConfig);
                    break;
                default:
                    log("initApnContexts: skipping unknown type=" + networkConfig.type);
                    continue;
            }
            log("initApnContexts: apnContext=" + apnContext);
        }
        log("initApnContexts: X mApnContexts=" + this.mApnContexts);
        Collections.sort(this.mPrioritySortedApnContexts, new C00922());
        log("initApnContexts: X mPrioritySortedApnContexts= " + this.mPrioritySortedApnContexts);
    }

    public LinkProperties getLinkProperties(String apnType) {
        ApnContext apnContext = (ApnContext) this.mApnContexts.get(apnType);
        if (apnContext != null) {
            DcAsyncChannel dcac = apnContext.getDcAc();
            if (dcac != null) {
                log("return link properites for " + apnType);
                return dcac.getLinkPropertiesSync();
            }
        }
        log("return new LinkProperties");
        return new LinkProperties();
    }

    public NetworkCapabilities getNetworkCapabilities(String apnType) {
        ApnContext apnContext = (ApnContext) this.mApnContexts.get(apnType);
        if (apnContext != null) {
            DcAsyncChannel dataConnectionAc = apnContext.getDcAc();
            if (dataConnectionAc != null) {
                log("get active pdp is not null, return NetworkCapabilities for " + apnType);
                return dataConnectionAc.getNetworkCapabilitiesSync();
            }
        }
        log("return new NetworkCapabilities");
        return new NetworkCapabilities();
    }

    public String[] getActiveApnTypes() {
        log("get all active apn types");
        ArrayList<String> result = new ArrayList();
        for (ApnContext apnContext : this.mApnContexts.values()) {
            if (this.mAttached.get() && apnContext.isReady()) {
                result.add(apnContext.getApnType());
            }
        }
        return (String[]) result.toArray(new String[0]);
    }

    public String getActiveApnString(String apnType) {
        ApnContext apnContext = (ApnContext) this.mApnContexts.get(apnType);
        if (apnContext != null) {
            ApnSetting apnSetting = apnContext.getApnSetting();
            if (apnSetting != null) {
                return apnSetting.apn;
            }
        }
        return null;
    }

    public boolean isApnTypeEnabled(String apnType) {
        ApnContext apnContext = (ApnContext) this.mApnContexts.get(apnType);
        if (apnContext == null) {
            return false;
        }
        return apnContext.isEnabled();
    }

    protected void setState(State s) {
        log("setState should not be used in GSM" + s);
    }

    public State getState(String apnType) {
        ApnContext apnContext = (ApnContext) this.mApnContexts.get(apnType);
        if (apnContext != null) {
            return apnContext.getState();
        }
        return State.FAILED;
    }

    protected boolean isProvisioningApn(String apnType) {
        ApnContext apnContext = (ApnContext) this.mApnContexts.get(apnType);
        if (apnContext != null) {
            return apnContext.isProvisioningApn();
        }
        return false;
    }

    public State getOverallState() {
        boolean isConnecting = false;
        boolean isFailed = true;
        boolean isAnyEnabled = false;
        for (ApnContext apnContext : this.mApnContexts.values()) {
            if (apnContext.isEnabled()) {
                isAnyEnabled = true;
                State apnState = apnContext.getState();
                if (apnState == State.DISCONNECTING) {
                    apnState = apnContext.getPreviousState();
                }
                switch (C00955.$SwitchMap$com$android$internal$telephony$DctConstants$State[apnState.ordinal()]) {
                    case 1:
                    case 2:
                        log("overall state is CONNECTED");
                        return State.CONNECTED;
                    case 3:
                    case 4:
                        isConnecting = true;
                        isFailed = false;
                        break;
                    case 5:
                    case 6:
                        isFailed = false;
                        break;
                    default:
                        isAnyEnabled = true;
                        break;
                }
            }
        }
        if (!isAnyEnabled) {
            log("overall state is IDLE");
            return State.IDLE;
        } else if (isConnecting) {
            log("overall state is CONNECTING");
            return State.CONNECTING;
        } else if (isFailed) {
            log("overall state is FAILED");
            return State.FAILED;
        } else {
            log("overall state is IDLE");
            return State.IDLE;
        }
    }

    public boolean isApnTypeAvailable(String type) {
        int radioTech = this.mPhone.getServiceState().getRilDataRadioTechnology();
        if (type.equals("dun") && fetchDunApn() != null) {
            return true;
        }
        if (type.equals("bip") && fetchBipApn() != null) {
            return true;
        }
        if (this.mAllApnSettings != null) {
            Iterator i$ = this.mAllApnSettings.iterator();
            while (i$.hasNext()) {
                if (((ApnSetting) i$.next()).canHandleType(type)) {
                    return true;
                }
            }
        }
        return false;
    }

    public boolean getAnyDataEnabled() {
        boolean z = false;
        synchronized (this.mDataEnabledLock) {
            String reason;
            if (IgnoreDataEnabledOnRoaming()) {
                if (!(this.mInternalDataEnabled && this.sPolicyDataEnabled)) {
                    reason = "";
                    if (!this.mInternalDataEnabled) {
                        reason = reason + " - mInternalDataEnabled = false";
                    }
                    if (!this.sPolicyDataEnabled) {
                        reason = reason + " - sPolicyDataEnabled = false";
                    }
                    log("getAnyDataEnabled: not allowed due to" + reason);
                }
                z = true;
            } else {
                if (!(this.mInternalDataEnabled && this.mUserDataEnabled && this.sPolicyDataEnabled)) {
                    reason = "";
                    if (!this.mInternalDataEnabled) {
                        reason = reason + " - mInternalDataEnabled = false";
                    }
                    if (!this.mUserDataEnabled) {
                        reason = reason + " - mUserDataEnabled = false";
                    }
                    if (!this.sPolicyDataEnabled) {
                        reason = reason + " - sPolicyDataEnabled = false";
                    }
                    log("getAnyDataEnabled: not allowed due to" + reason);
                }
                z = true;
            }
        }
        return z;
    }

    public boolean getAnyDataEnabled(boolean checkUserDataEnabled) {
        boolean z = false;
        synchronized (this.mDataEnabledLock) {
            if (!this.mInternalDataEnabled || ((checkUserDataEnabled && !this.mUserDataEnabled) || (checkUserDataEnabled && !this.sPolicyDataEnabled))) {
            } else {
                for (ApnContext apnContext : this.mApnContexts.values()) {
                    if (isDataAllowed(apnContext)) {
                        z = true;
                        break;
                    }
                }
            }
        }
        return z;
    }

    private void onRoutingAreaChanged() {
        log("onRoutingAreaChanged");
        if ("TCE".equals(CscFeature.getInstance().getString("CscFeature_RIL_PDPRetryMechanism4"))) {
            if (this.retryTCECounter != 0) {
                Iterator i$ = this.mPrioritySortedApnContexts.iterator();
                while (i$.hasNext()) {
                    log("onRoutingAreaChanged PermFailCount" + ((ApnContext) i$.next()).getWaitingApnsPermFailCount() + "retryTCECounter=" + this.retryTCECounter);
                    this.retryTCECounter = 0;
                }
            } else {
                return;
            }
        }
        this.mAttached.set(true);
        if (getOverallState() == State.CONNECTED) {
            log("onDataConnectionAttached: start polling notify attached");
            startNetStatPoll();
            startDataStallAlarm(false);
            notifyDataConnection(Phone.REASON_DATA_ATTACHED);
        } else {
            notifyOffApnsOfAvailability(Phone.REASON_DATA_ATTACHED);
        }
        setupDataOnConnectableApns(Phone.REASON_DATA_ATTACHED);
    }

    private boolean isDataAllowed(ApnContext apnContext) {
        return apnContext.isReady() && isDataAllowed();
    }

    protected void onDataConnectionDetached() {
        log("onDataConnectionDetached: stop polling and notify detached");
        stopNetStatPoll();
        stopDataStallAlarm();
        this.mAttached.set(false);
        notifyDataConnection(Phone.REASON_DATA_DETACHED);
    }

    private void onDataConnectionAttached() {
        log("onDataConnectionAttached");
        this.mAttached.set(true);
        if (getOverallState() == State.CONNECTED) {
            log("onDataConnectionAttached: start polling notify attached");
            startNetStatPoll();
            startDataStallAlarm(false);
            notifyDataConnection(Phone.REASON_DATA_ATTACHED);
        } else {
            notifyOffApnsOfAvailability(Phone.REASON_DATA_ATTACHED);
        }
        setupDataOnConnectableApns(Phone.REASON_DATA_ATTACHED);
    }

    protected boolean isDataAllowed() {
        boolean allowed;
        synchronized (this.mDataEnabledLock) {
            boolean internalDataEnabled = this.mInternalDataEnabled;
        }
        boolean attachedState = this.mAttached.get();
        boolean desiredPowerState = this.mPhone.getServiceStateTracker().getDesiredPowerState();
        IccRecords r = (IccRecords) this.mIccRecords.get();
        boolean recordsLoaded = r != null ? r.getRecordsLoaded() : false;
        boolean roamingEnabled = !this.mPhone.getServiceState().getRoaming() || getDataOnRoamingEnabled();
        boolean needsProvisioning = false;
        if ("VZW-CDMA".equals("") && this.mPhone.getServiceState().getRilDataRadioTechnology() == 6) {
            needsProvisioning = this.mPhone.needsOtaServiceProvisioning();
        }
        if ((attachedState || this.mAutoAttachOnCreation) && recordsLoaded && ((this.mPhone.getState() == PhoneConstants.State.IDLE || this.mPhone.getServiceStateTracker().isConcurrentVoiceAndDataAllowed()) && internalDataEnabled && roamingEnabled && !this.mIsPsRestricted && !needsProvisioning && desiredPowerState)) {
            allowed = true;
        } else {
            allowed = false;
        }
        boolean isToddlerMode = false;
        if (FloatingFeature.getInstance().getEnableStatus("SEC_FLOATING_FEATURE_SETTINGS_SUPPORT_NETWORK_RESTRICTION_MODE")) {
            isToddlerMode = System.getInt(this.mPhone.getContext().getContentResolver(), "toddler_mode_switch", 0) == 1;
            log("isDataAllowed(): isToddlerMode is " + isToddlerMode);
            if (!allowed || isToddlerMode) {
                allowed = false;
            } else {
                allowed = true;
            }
        }
        if (allowed && (("CG".equals("DGG") || "GG".equals("DGG")) && this.insertedSimCount > 1)) {
            for (int simSlot = 0; simSlot < this.insertedSimCount; simSlot++) {
                if (simSlot != SubscriptionController.getInstance().getSlotId(SubscriptionController.getInstance().getDefaultDataSubId())) {
                    try {
                        long[] subId = SubscriptionController.getInstance().getSubId(simSlot);
                        boolean isOtherPhoneCalling = TelephonyManager.getDefault().getCallState(subId[0]) != 0;
                        log("isOtherPhoneCalling : " + isOtherPhoneCalling + ", simSlot : " + simSlot + ", getState : " + TelephonyManager.getDefault().getCallState(subId[0]));
                        if (!allowed || isOtherPhoneCalling) {
                            allowed = false;
                        } else {
                            allowed = true;
                        }
                    } catch (Exception e) {
                        log("Phone App process doesn't finish.");
                    }
                }
            }
        }
        if (!allowed) {
            String reason = "";
            if (!(attachedState || this.mAutoAttachOnCreation)) {
                reason = reason + " - Attached= " + attachedState;
            }
            if (!recordsLoaded) {
                reason = reason + " - SIM not loaded";
            }
            if (!(this.mPhone.getState() == PhoneConstants.State.IDLE || this.mPhone.getServiceStateTracker().isConcurrentVoiceAndDataAllowed())) {
                reason = (reason + " - PhoneState= " + this.mPhone.getState()) + " - Concurrent voice and data not allowed";
            }
            if (!internalDataEnabled) {
                reason = reason + " - mInternalDataEnabled= false";
            }
            if (this.mPhone.getServiceState().getRoaming() && !getDataOnRoamingEnabled()) {
                reason = reason + " - Roaming and data roaming not enabled";
            }
            if (this.mIsPsRestricted) {
                reason = reason + " - mIsPsRestricted= true";
            }
            if (!desiredPowerState) {
                reason = reason + " - desiredPowerState= false";
            }
            if (needsProvisioning) {
                reason = reason + " - needsProvisioning= true";
            }
            if (FloatingFeature.getInstance().getEnableStatus("SEC_FLOATING_FEATURE_SETTINGS_SUPPORT_NETWORK_RESTRICTION_MODE") && isToddlerMode) {
                reason = reason + " - isToddlerMode= true";
            }
            log("isDataAllowed: not allowed due to" + reason);
        }
        return allowed;
    }

    private void setupDataOnConnectableApns(String reason) {
        log("setupDataOnConnectableApns: " + reason);
        Iterator i$ = this.mPrioritySortedApnContexts.iterator();
        while (i$.hasNext()) {
            ApnContext apnContext = (ApnContext) i$.next();
            log("setupDataOnConnectableApns: apnContext " + apnContext);
            if (Phone.REASON_ROAMING_ON.equals(reason)) {
                if (apnContext.getApnType().equals("ent1")) {
                    if (this.mCm.isEntApnEnabled(28)) {
                        continue;
                    } else {
                        log("setupDataOnConnectableApns: setting enabled for " + apnContext.getApnType() + " to " + apnContext.isEnabled() + " roaming state is " + this.mPhone.getServiceState().getRoaming());
                    }
                }
                if (apnContext.getApnType().equals("ent2")) {
                    if (this.mCm.isEntApnEnabled(29)) {
                        continue;
                    } else {
                        log("setupDataOnConnectableApns: setting enabled for " + apnContext.getApnType() + " to " + apnContext.isEnabled() + " roaming state is " + this.mPhone.getServiceState().getRoaming());
                    }
                }
            }
            if (apnContext.getState() == State.FAILED) {
                if (("LGT".equals("") || ("ATT".equals(SystemProperties.get("ro.csc.sales_code")) && TextUtils.equals(apnContext.getApnType(), "ims"))) && Phone.REASON_VOICE_CALL_ENDED.equals(reason) && apnContext.getWaitingApnsPermFailCount() == 0 && !this.mPhone.getServiceState().getRoaming()) {
                    log("don't reset reconnect timer for " + apnContext.getApnType() + " at onVoiceCallEnded ");
                } else if (!"TCE".equals(CscFeature.getInstance().getString("CscFeature_RIL_PDPRetryMechanism4")) || this.retryTCECounter < 3) {
                    apnContext.setState(State.IDLE);
                } else {
                    log("don't reset reconnect timer for " + apnContext.getApnType() + " at retryTCECounter >=3");
                    return;
                }
            }
            if (apnContext.isConnectable()) {
                log("setupDataOnConnectableApns: isConnectable() call trySetupData");
                apnContext.setReason(reason);
                trySetupData(apnContext);
            }
        }
    }

    private boolean trySetupData(ApnContext apnContext) {
        int radioTech;
        String domesticOtaStart = SystemProperties.get("ril.domesticOtaStart");
        String domesticOta = SystemProperties.get("ril.domesticOta");
        log("trySetupData for type:" + apnContext.getApnType() + " due to " + apnContext.getReason() + " apnContext=" + apnContext);
        log("trySetupData with mIsPsRestricted=" + this.mIsPsRestricted);
        if ("CG".equals("DGG") || "DCGG".equals("DGG") || "DGG".equals("DGG") || "DCGS".equals("DGG") || "DCGGS".equals("DGG") || "GG".equals("DGG")) {
            int phoneId = SubscriptionController.getInstance().getPhoneId(SubscriptionController.getInstance().getDefaultDataSubId());
            int slotId = SystemProperties.getInt("ril.datacross.slotid", -1);
            log("trySetupData: getDefaultDataPhoneId()=" + phoneId + ", datacross.slotId=" + slotId);
            if (phoneId != this.mPhone.getPhoneId()) {
                if (!(apnContext.getApnType().equals("mms") || apnContext.getApnType().equals("mms2"))) {
                    log("trySetupData: reject trySetupData, " + apnContext.getApnType());
                    return false;
                }
            } else if (phoneId == this.mPhone.getPhoneId() && slotId != -1) {
                log("trySetupData: slotId, reject trySetupData, " + apnContext.getApnType());
                return false;
            }
        }
        if (!(!CscFeature.getInstance().getEnableStatus("CscFeature_RIL_PromptToDataRoam") || "ims".equals(apnContext.getApnType()) || ((SystemProperties.get("ril.simtype", "").equals("18") && "bip".equals(apnContext.getApnType())) || (false && "true".equals(domesticOtaStart))))) {
            String salesCode = SystemProperties.get("ro.csc.sales_code", "none");
            String numeric = TelephonyManager.getDefault().getSimOperator(SubscriptionController.getInstance().getSubId(this.mPhone.getPhoneId())[0]);
            log("trysetup for preprocess: salesCode= " + salesCode + " numeric= " + numeric);
            if (!("".equals(numeric) || "none".equals(numeric) || "00101".equals(numeric) || "none".equals(salesCode) || isDataAllowedOnDataDisabled(apnContext) || preProcessDataConnection(apnContext.getReason()))) {
                log("preProcessDataConnection called: false");
                return false;
            }
        }
        if ("KDI".equals("")) {
            radioTech = this.mPhone.getServiceState().getRilDataRadioTechnology();
            if (apnContext.getApnType().equals("ims") && isCdmaRat(radioTech)) {
                log("trySetupData: X IMS reject bcz of RAT=" + radioTech);
                return false;
            }
        }
        if ("VZW-CDMA".equals("")) {
            radioTech = this.mPhone.getServiceState().getRilDataRadioTechnology();
            if ("ims".equals(apnContext.getApnType())) {
                if (isDataInLegacy3gpp(radioTech)) {
                    log("Reject IMS Type in 3GPP legacy");
                    return false;
                } else if (DcTrackerBase.is1xEvdo(radioTech)) {
                    log("Reject IMS Type in 1x or EVDO");
                    return false;
                }
            }
        }
        if (this.mPhone.getSimulatedRadioControl() != null) {
            apnContext.setState(State.CONNECTED);
            this.mPhone.notifyDataConnection(apnContext.getReason(), apnContext.getApnType());
            log("trySetupData: X We're on the simulator; assuming connected retValue=true");
            return true;
        }
        boolean isEmergencyApn = apnContext.getApnType().equals("emergency");
        boolean desiredPowerState = this.mPhone.getServiceStateTracker().getDesiredPowerState();
        boolean isAnyDataEnabled = getAnyDataEnabled(!apnContext.getApnType().equals("ims"));
        boolean isDataAllowedByItsOn = true;
        if (CscFeature.getInstance().getEnableStatus("CscFeature_Common_EnableItsOn")) {
            isDataAllowedByItsOn = this.mApi.isDataAllowed((long) this.mPhone.getServiceState().getSystemId(), this.mPhone.getServiceState().getOperatorNumeric());
            log("itson: isDataAllowedByItsOn = " + isDataAllowedByItsOn);
        }
        if (apnContext.isConnectable() && (isEmergencyApn || (((isDataAllowed(apnContext) && isAnyDataEnabled) || (apnContext.isReady() && isDataAllowedOnDataDisabled(apnContext))) && !isEmergency() && isDataAllowedByItsOn))) {
            if (apnContext.getState() == State.FAILED) {
                log("trySetupData: make a FAILED ApnContext IDLE so its reusable");
                apnContext.setState(State.IDLE);
            }
            radioTech = this.mPhone.getServiceState().getRilDataRadioTechnology();
            if (apnContext.getState() == State.IDLE) {
                ArrayList<ApnSetting> waitingApns = buildWaitingApns(apnContext.getApnType(), radioTech);
                if (waitingApns.isEmpty()) {
                    notifyNoData(DcFailCause.MISSING_UNKNOWN_APN, apnContext);
                    notifyOffApnsOfAvailability(apnContext.getReason());
                    log("trySetupData: X No APN found retValue=false");
                    return false;
                }
                apnContext.setWaitingApns(waitingApns);
                if (isDebugLevelNotLow()) {
                    log("trySetupData: Create from mAllApnSettings : " + apnListToString(this.mAllApnSettings));
                }
            }
            log("trySetupData: call setupData, waitingApns : " + apnListToString(apnContext.getWaitingApns()));
            boolean retValue = setupData(apnContext, radioTech);
            notifyOffApnsOfAvailability(apnContext.getReason());
            log("trySetupData: X retValue=" + retValue);
            return retValue;
        }
        if (!apnContext.getApnType().equals("default") && apnContext.isConnectable()) {
            this.mPhone.notifyDataConnectionFailed(apnContext.getReason(), apnContext.getApnType());
        }
        notifyOffApnsOfAvailability(apnContext.getReason());
        log("trySetupData: X apnContext not 'ready' retValue=false");
        return false;
    }

    protected void notifyOffApnsOfAvailability(String reason) {
        for (ApnContext apnContext : this.mApnContexts.values()) {
            if (!this.mAttached.get() || !apnContext.isReady()) {
                PhoneBase phoneBase = this.mPhone;
                String reason2 = reason != null ? reason : apnContext.getReason();
                String apnType = apnContext.getApnType();
                DataState dataState = (Phone.REASON_DATA_DETACHED.equals(reason) && (apnContext.getState() == State.CONNECTED || apnContext.getState() == State.DISCONNECTING)) ? DataState.SUSPENDED : DataState.DISCONNECTED;
                phoneBase.notifyDataConnection(reason2, apnType, dataState);
            }
        }
    }

    protected boolean cleanUpAllConnections(boolean tearDown, String reason) {
        log("cleanUpAllConnections: tearDown=" + tearDown + " reason=" + reason);
        boolean didDisconnect = false;
        boolean specificdisable = false;
        if (!TextUtils.isEmpty(reason)) {
            specificdisable = reason.equals(Phone.REASON_DATA_SPECIFIC_DISABLED);
        }
        if ("DCM".equals("")) {
        } else {
        }
        for (ApnContext apnContext : this.mApnContexts.values()) {
            if (!apnContext.isDisconnected()) {
                didDisconnect = true;
            }
            if (Phone.REASON_DATA_SPECIFIC_DISABLED.equals(reason) && isDataAllowedOnDataDisabled(apnContext)) {
                String salesCode = SystemProperties.get("ro.csc.sales_code", "none");
                log("cleanUpAllConnections: salesCode = " + salesCode);
                if (!"TGY".equals(salesCode) && "BRI".equals(salesCode)) {
                }
            } else if ((!Phone.REASON_PDP_RESET.equals(reason) && !Phone.REASON_VOICE_CALL_STARTED.equals(reason)) || !"ims".equals(apnContext.getApnType())) {
                if (!specificdisable) {
                    apnContext.setReason(reason);
                    cleanUpConnection(tearDown, apnContext);
                } else if (!apnContext.getApnType().equals("ims")) {
                    log("ApnConextType: " + apnContext.getApnType());
                    apnContext.setReason(reason);
                    cleanUpConnection(tearDown, apnContext);
                }
            }
        }
        stopNetStatPoll();
        stopDataStallAlarm();
        this.mRequestedApnType = "default";
        log("cleanUpConnection: mDisconnectPendingCount = " + this.mDisconnectPendingCount);
        if (tearDown && this.mDisconnectPendingCount == 0) {
            notifyDataDisconnectComplete();
            notifyAllDataDisconnected();
        }
        if ("TCE".equals(CscFeature.getInstance().getString("CscFeature_RIL_PDPRetryMechanism4"))) {
            log("cleanUpAllConnections: reset retryTCECounter");
            this.retryTCECounter = 0;
        }
        return didDisconnect;
    }

    protected void onCleanUpAllConnections(String cause) {
        cleanUpAllConnections(true, cause);
    }

    protected void cleanUpConnection(boolean tearDown, ApnContext apnContext) {
        if (apnContext == null) {
            log("cleanUpConnection: apn context is null");
            return;
        }
        DcAsyncChannel dcac = apnContext.getDcAc();
        log("cleanUpConnection: E tearDown=" + tearDown + " reason=" + apnContext.getReason() + " apnContext=" + apnContext);
        if (tearDown) {
            if (CscFeature.getInstance().getEnableStatus("CscFeature_CIQ_BroadcastState")) {
                PDPContextStateBroadcaster.sendDisconnected(this.mPhone.getContext(), apnContext);
            }
            if (apnContext.isDisconnected()) {
                apnContext.setState(State.IDLE);
                this.mPhone.notifyDataConnection(apnContext.getReason(), apnContext.getApnType());
                if (!apnContext.isReady()) {
                    if (dcac != null) {
                        dcac.tearDown(apnContext, "", null);
                    }
                    apnContext.setDataConnectionAc(null);
                }
            } else if (dcac == null) {
                apnContext.setState(State.IDLE);
                this.mPhone.notifyDataConnection(apnContext.getReason(), apnContext.getApnType());
            } else if (apnContext.getState() != State.DISCONNECTING) {
                boolean disconnectAll = false;
                if ("dun".equals(apnContext.getApnType()) && teardownForDun()) {
                    log("tearing down dedicated DUN connection");
                    disconnectAll = true;
                }
                log("cleanUpConnection: tearing down" + (disconnectAll ? " all" : ""));
                Message msg = obtainMessage(270351, apnContext);
                if (disconnectAll) {
                    apnContext.getDcAc().tearDownAll(apnContext.getReason(), msg);
                } else {
                    apnContext.getDcAc().tearDown(apnContext, apnContext.getReason(), msg);
                }
                if (this.mIsHoleOfVoiceCall) {
                    apnContext.setState(State.IDLE);
                }
                apnContext.setState(State.DISCONNECTING);
                this.mDisconnectPendingCount++;
            }
        } else {
            if (dcac != null) {
                dcac.reqReset();
            }
            apnContext.setState(State.IDLE);
            this.mPhone.notifyDataConnection(apnContext.getReason(), apnContext.getApnType());
            apnContext.setDataConnectionAc(null);
        }
        if (dcac != null) {
            cancelReconnectAlarm(apnContext);
        }
        log("cleanUpConnection: X tearDown=" + tearDown + " reason=" + apnContext.getReason() + " apnContext=" + apnContext + " dcac=" + apnContext.getDcAc());
    }

    private boolean teardownForDun() {
        if (!ServiceState.isCdma(this.mPhone.getServiceState().getRilDataRadioTechnology()) && fetchDunApn() == null) {
            return false;
        }
        return true;
    }

    private void cancelReconnectAlarm(ApnContext apnContext) {
        if (apnContext != null) {
            PendingIntent intent = apnContext.getReconnectIntent();
            if (intent != null) {
                ((AlarmManager) this.mPhone.getContext().getSystemService("alarm")).cancel(intent);
                apnContext.setReconnectIntent(null);
            }
        }
    }

    private String[] parseTypes(String types) {
        if (types != null && !types.equals("")) {
            return types.split(",");
        }
        return new String[]{CharacterSets.MIMENAME_ANY_CHARSET};
    }

    private boolean imsiMatches(String imsiDB, String imsiSIM) {
        int len = imsiDB.length();
        if (len <= 0 || len > imsiSIM.length()) {
            return false;
        }
        int idx = 0;
        while (idx < len) {
            char c = imsiDB.charAt(idx);
            if (c != 'x' && c != 'X' && c != imsiSIM.charAt(idx)) {
                return false;
            }
            idx++;
        }
        return true;
    }

    protected boolean mvnoMatches(IccRecords r, String mvnoType, String mvnoMatchData) {
        if (mvnoType == null) {
            return false;
        }
        if (mvnoType.equalsIgnoreCase("spn")) {
            if (r.getServiceProviderName() == null || !r.getServiceProviderName().equalsIgnoreCase(mvnoMatchData)) {
                return false;
            }
            return true;
        } else if (mvnoType.equalsIgnoreCase("imsi")) {
            String imsiSIM = r.getIMSI();
            if (imsiSIM == null || !imsiMatches(mvnoMatchData, imsiSIM)) {
                return false;
            }
            return true;
        } else if (!mvnoType.equalsIgnoreCase("gid")) {
            return false;
        } else {
            String gid1 = r.getGid1();
            int mvno_match_data_length = mvnoMatchData.length();
            if (gid1 == null || gid1.length() < mvno_match_data_length || !gid1.substring(0, mvno_match_data_length).equalsIgnoreCase(mvnoMatchData)) {
                return false;
            }
            return true;
        }
    }

    protected boolean isPermanentFail(DcFailCause dcFailCause) {
        return dcFailCause.isPermanentFail() && !(this.mAttached.get() && dcFailCause == DcFailCause.SIGNAL_LOST);
    }

    private ApnSetting makeApnSetting(Cursor cursor) {
        boolean z;
        boolean z2;
        String[] types = parseTypes(cursor.getString(cursor.getColumnIndexOrThrow("type")));
        int i = cursor.getInt(cursor.getColumnIndexOrThrow("_id"));
        String string = cursor.getString(cursor.getColumnIndexOrThrow(Carriers.NUMERIC));
        String string2 = cursor.getString(cursor.getColumnIndexOrThrow("name"));
        String string3 = cursor.getString(cursor.getColumnIndexOrThrow(Carriers.APN));
        String trimV4AddrZeros = NetworkUtils.trimV4AddrZeros(cursor.getString(cursor.getColumnIndexOrThrow(Carriers.PROXY)));
        String string4 = cursor.getString(cursor.getColumnIndexOrThrow(Carriers.PORT));
        String trimV4AddrZeros2 = NetworkUtils.trimV4AddrZeros(cursor.getString(cursor.getColumnIndexOrThrow(Carriers.MMSC)));
        String trimV4AddrZeros3 = NetworkUtils.trimV4AddrZeros(cursor.getString(cursor.getColumnIndexOrThrow(Carriers.MMSPROXY)));
        String string5 = cursor.getString(cursor.getColumnIndexOrThrow(Carriers.MMSPORT));
        String string6 = cursor.getString(cursor.getColumnIndexOrThrow(Carriers.USER));
        String string7 = cursor.getString(cursor.getColumnIndexOrThrow(Carriers.PASSWORD));
        int i2 = cursor.getInt(cursor.getColumnIndexOrThrow(Carriers.AUTH_TYPE));
        String string8 = cursor.getString(cursor.getColumnIndexOrThrow("protocol"));
        String string9 = cursor.getString(cursor.getColumnIndexOrThrow(Carriers.ROAMING_PROTOCOL));
        if (cursor.getInt(cursor.getColumnIndexOrThrow(Carriers.CARRIER_ENABLED)) == 1) {
            z = true;
        } else {
            z = false;
        }
        int i3 = cursor.getInt(cursor.getColumnIndexOrThrow(Carriers.BEARER));
        int i4 = cursor.getInt(cursor.getColumnIndexOrThrow(Carriers.PROFILE_ID));
        if (cursor.getInt(cursor.getColumnIndexOrThrow(Carriers.MODEM_COGNITIVE)) == 1) {
            z2 = true;
        } else {
            z2 = false;
        }
        return new ApnSetting(i, string, string2, string3, trimV4AddrZeros, string4, trimV4AddrZeros2, trimV4AddrZeros3, string5, string6, string7, i2, types, string8, string9, z, i3, i4, z2, cursor.getInt(cursor.getColumnIndexOrThrow(Carriers.MAX_CONNS)), cursor.getInt(cursor.getColumnIndexOrThrow(Carriers.WAIT_TIME)), cursor.getInt(cursor.getColumnIndexOrThrow(Carriers.MAX_CONNS_TIME)), cursor.getInt(cursor.getColumnIndexOrThrow("mtu")), cursor.getString(cursor.getColumnIndexOrThrow(Carriers.MVNO_TYPE)), cursor.getString(cursor.getColumnIndexOrThrow(Carriers.MVNO_MATCH_DATA)));
    }

    private ApnSetting makeCdmaApnSetting(int bearer) {
        return new ApnSetting(0, "", "", "cdma", "", "", "", "", "", "", "", 0, new String[]{"default", "mms", "supl", "dun", "hipri", "fota", "cbs", "cas"}, "IP", "IP", true, bearer, 0, false, 0, 0, 0, 0, "", "");
    }

    private ArrayList<ApnSetting> createApnList(Cursor cursor) {
        ArrayList<ApnSetting> result;
        ArrayList<ApnSetting> mnoApns = new ArrayList();
        ArrayList<ApnSetting> mvnoApns = new ArrayList();
        IccRecords r = (IccRecords) this.mIccRecords.get();
        if (cursor.moveToFirst()) {
            do {
                ApnSetting apn = makeApnSetting(cursor);
                if (apn != null) {
                    if (apn.hasMvnoParams()) {
                        if (r != null && mvnoMatches(r, apn.mvnoType, apn.mvnoMatchData)) {
                            mvnoApns.add(apn);
                        }
                    } else if ("CG".equals("DGG") || "DCGG".equals("DGG") || "DCGS".equals("DGG") || "DCGGS".equals("DGG")) {
                        int cardType = Integer.parseInt(TelephonyManager.getTelephonyProperty("ril.ICC_TYPE", this.mPhone.getSubId(), "0"));
                        String cardIsCSIM = SystemProperties.get("ril.IsCSIM", "");
                        String operator = TelephonyManager.getTelephonyProperty("gsm.operator.numeric", this.mPhone.getSubId(), "");
                        log("makeApnSetting(), cardType=" + cardType + ", roaming=" + TelephonyManager.getTelephonyProperty("gsm.operator.isroaming", this.mPhone.getSubId(), "") + ", operator=" + operator);
                        if ((cardType == 4 || cardType == 3) && (("ctwap".equalsIgnoreCase(cursor.getString(cursor.getColumnIndexOrThrow(Carriers.APN))) || "live.vodafone.com".equalsIgnoreCase(cursor.getString(cursor.getColumnIndexOrThrow(Carriers.APN))) || "ctlte".equalsIgnoreCase(cursor.getString(cursor.getColumnIndexOrThrow(Carriers.APN)))) && "true".equals(TelephonyManager.getTelephonyProperty("gsm.operator.isroaming", this.mPhone.getSubId(), "")) && !TextUtils.isEmpty(operator) && !"46003".equals(operator))) {
                            log("makeApnSetting(), pass, apn=" + cursor.getString(cursor.getColumnIndexOrThrow(Carriers.APN)));
                        } else if (!"1".equals(cardIsCSIM) && "ctlte".equalsIgnoreCase(cursor.getString(cursor.getColumnIndexOrThrow(Carriers.APN)))) {
                            log("makeApnSetting(), pass , reason = 3G card, apn=" + cursor.getString(cursor.getColumnIndexOrThrow(Carriers.APN)));
                        } else if ((cardType == 4 || cardType == 3) && "live.vodafone.com".equalsIgnoreCase(cursor.getString(cursor.getColumnIndexOrThrow(Carriers.APN))) && ("46003".equals(operator) || "46011".equals(operator))) {
                            log("makeApnSetting(), pass live.vodafone.com, operator=" + operator + ", apn=" + cursor.getString(cursor.getColumnIndexOrThrow(Carriers.APN)));
                        } else {
                            mnoApns.add(apn);
                        }
                    } else {
                        mnoApns.add(apn);
                    }
                }
            } while (cursor.moveToNext());
        }
        if (mvnoApns.isEmpty()) {
            result = mnoApns;
        } else {
            result = mvnoApns;
        }
        if (isDebugLevelNotLow()) {
            log("createApnList: X result=" + result);
        }
        return result;
    }

    private boolean dataConnectionNotInUse(DcAsyncChannel dcac) {
        log("dataConnectionNotInUse: check if dcac is inuse dcac=" + dcac);
        for (ApnContext apnContext : this.mApnContexts.values()) {
            if (apnContext.getDcAc() == dcac) {
                log("dataConnectionNotInUse: in use by apnContext=" + apnContext);
                return false;
            }
        }
        log("dataConnectionNotInUse: tearDownAll");
        dcac.tearDownAll("No connection", null);
        log("dataConnectionNotInUse: not in use return true");
        return true;
    }

    private DcAsyncChannel findFreeDataConnection() {
        for (DcAsyncChannel dcac : this.mDataConnectionAcHashMap.values()) {
            if (dcac.isInactiveSync() && dataConnectionNotInUse(dcac)) {
                log("findFreeDataConnection: found free DataConnection= dcac=" + dcac);
                return dcac;
            }
        }
        log("findFreeDataConnection: NO free DataConnection");
        return null;
    }

    private boolean setupData(ApnContext apnContext, int radioTech) {
        log("setupData: apnContext=" + apnContext);
        DcAsyncChannel dcac = null;
        ApnSetting apnSetting = apnContext.getNextWaitingApn();
        if (apnSetting == null) {
            log("setupData: return for no apn found!");
            return false;
        }
        int profileId = apnSetting.profileId;
        if (profileId == 0) {
            profileId = getApnProfileID(apnContext.getApnType());
        }
        if (!(apnContext.getApnType() == "dun" && teardownForDun())) {
            dcac = checkForCompatibleConnectedApnContext(apnContext);
            if (dcac != null) {
                ApnSetting dcacApnSetting = dcac.getApnSettingSync();
                if (dcacApnSetting != null) {
                    apnSetting = dcacApnSetting;
                }
            }
        }
        if (dcac == null) {
            dcac = checkForDuplicatedConnectedApnContext(apnSetting);
        }
        if (dcac == null) {
            if (!(!isOnlySingleDcAllowed(radioTech) || "CG".equals("DGG") || "DCGS".equals("DGG") || "DCGGS".equals("DGG") || "DCGG".equals("DGG"))) {
                if (isHigherPriorityApnContextActive(apnContext)) {
                    log("setupData: Higher priority ApnContext active.  Ignoring call");
                    return false;
                } else if (cleanUpAllConnections(true, Phone.REASON_SINGLE_PDN_ARBITRATION)) {
                    log("setupData: Some calls are disconnecting first.  Wait and retry");
                    return false;
                } else {
                    log("setupData: Single pdp. Continue setting up data call.");
                }
            }
            dcac = findFreeDataConnection();
            if (dcac == null) {
                dcac = createDataConnection();
            }
            if (dcac == null) {
                log("setupData: No free DataConnection and couldn't create one, WEIRD");
                return false;
            }
        }
        log("setupData: dcac=" + dcac + " apnSetting=" + apnSetting);
        apnContext.setDataConnectionAc(dcac);
        apnContext.setApnSetting(apnSetting);
        apnContext.setState(State.CONNECTING);
        this.mPhone.notifyDataConnection(apnContext.getReason(), apnContext.getApnType());
        if (CscFeature.getInstance().getEnableStatus("CscFeature_CIQ_BroadcastState")) {
            PDPContextStateBroadcaster.sendRequested(this.mPhone.getContext(), apnContext);
        }
        Message msg = obtainMessage();
        msg.what = 270336;
        msg.obj = apnContext;
        dcac.bringUp(apnContext, getInitialMaxRetry(), profileId, radioTech, this.mAutoAttachOnCreation, msg);
        log("setupData: initing!");
        return true;
    }

    private void onApnChanged() {
        boolean isDisconnected;
        boolean z = true;
        State overallState = getOverallState();
        if (overallState == State.IDLE || overallState == State.FAILED) {
            isDisconnected = true;
        } else {
            isDisconnected = false;
        }
        State defaultApnState = getState("default");
        boolean isDefaultDisconnected;
        if (defaultApnState == State.IDLE || defaultApnState == State.FAILED) {
            isDefaultDisconnected = true;
        } else {
            isDefaultDisconnected = false;
        }
        if (this.mPhone instanceof GSMPhone) {
            ((GSMPhone) this.mPhone).updateCurrentCarrierInProvider();
        }
        log("onApnChanged: createAllApnList and cleanUpAllConnections");
        createAllApnList();
        if ("KDI".equals("")) {
            checkAndDisconnectChangedApns();
        } else if (checkForCleanUpConnectionForApnChanged()) {
            if (isDisconnected) {
                z = false;
            }
            cleanUpAllConnections(z, Phone.REASON_APN_CHANGED);
        }
        notifyApnChangeToRIL();
        if (!"DCM".equals("")) {
            if (!isDisconnected) {
            }
            setupDataOnConnectableApns(Phone.REASON_APN_CHANGED);
        } else if (isDisconnected || ((isDomesticModel() || "KDI".equals("") || "DCM".equals("")) && isDefaultDisconnected)) {
            setupDataOnConnectableApns(Phone.REASON_APN_CHANGED);
        }
    }

    private DcAsyncChannel findDataConnectionAcByCid(int cid) {
        for (DcAsyncChannel dcac : this.mDataConnectionAcHashMap.values()) {
            if (dcac.getCidSync() == cid) {
                return dcac;
            }
        }
        return null;
    }

    protected void gotoIdleAndNotifyDataConnection(String reason) {
        log("gotoIdleAndNotifyDataConnection: reason=" + reason);
        notifyDataConnection(reason);
        this.mActiveApn = null;
    }

    private boolean isHigherPriorityApnContextActive(ApnContext apnContext) {
        Iterator i$ = this.mPrioritySortedApnContexts.iterator();
        while (i$.hasNext()) {
            ApnContext otherContext = (ApnContext) i$.next();
            if (apnContext.getApnType().equalsIgnoreCase(otherContext.getApnType())) {
                return false;
            }
            if (!(!otherContext.isEnabled() || otherContext.getState() == State.FAILED || otherContext.getState() == State.IDLE || otherContext.getState() == State.SCANNING)) {
                if (!"KDI".equals("") || !"ims".equals(otherContext.getApnType()) || otherContext.getState() != State.DISCONNECTING) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean isOnlySingleDcAllowed(int rilRadioTech) {
        int[] singleDcRats = this.mPhone.getContext().getResources().getIntArray(17236013);
        boolean onlySingleDcAllowed = false;
        if (Build.IS_DEBUGGABLE && SystemProperties.getBoolean("persist.telephony.test.singleDc", false)) {
            onlySingleDcAllowed = true;
        }
        if (singleDcRats != null) {
            for (int i = 0; i < singleDcRats.length && !onlySingleDcAllowed; i++) {
                if (rilRadioTech == singleDcRats[i]) {
                    onlySingleDcAllowed = true;
                }
            }
        }
        log("isOnlySingleDcAllowed(" + rilRadioTech + "): " + onlySingleDcAllowed);
        return onlySingleDcAllowed;
    }

    protected void restartRadio() {
        log("restartRadio: ************TURN OFF RADIO**************");
        cleanUpAllConnections(true, Phone.REASON_RADIO_TURNED_OFF);
        this.mPhone.getServiceStateTracker().powerOffRadioSafely(this);
        SystemProperties.set("net.ppp.reset-by-timeout", String.valueOf(Integer.parseInt(SystemProperties.get("net.ppp.reset-by-timeout", "0")) + 1));
    }

    private boolean retryAfterDisconnected(ApnContext apnContext) {
        if (Phone.REASON_RADIO_TURNED_OFF.equals(apnContext.getReason()) || (isOnlySingleDcAllowed(this.mPhone.getServiceState().getRilDataRadioTechnology()) && isHigherPriorityApnContextActive(apnContext))) {
            return false;
        }
        return true;
    }

    private void forceDataDeactiveTracker() {
        log("forceDataDeactiveTracker(), mState = " + this.mState.toString());
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(bos);
        try {
            dos.writeByte(13);
            dos.writeByte(14);
            dos.writeShort(7);
            dos.writeByte(3);
            dos.writeByte(2);
            dos.writeByte(0);
            if (dos != null) {
                try {
                    dos.close();
                } catch (Exception e) {
                }
            }
            this.mPhone.mCi.invokeOemRilRequestRaw(bos.toByteArray(), null);
        } catch (IOException e2) {
            if (dos != null) {
                try {
                    dos.close();
                } catch (Exception e3) {
                }
            }
        } catch (Throwable th) {
            if (dos != null) {
                try {
                    dos.close();
                } catch (Exception e4) {
                }
            }
        }
    }

    private void startAlarmForReconnect(int delay, ApnContext apnContext) {
        String apnType = apnContext.getApnType();
        Intent intent = new Intent("com.android.internal.telephony.data-reconnect." + apnType);
        intent.putExtra("reconnect_alarm_extra_reason", apnContext.getReason());
        intent.putExtra("reconnect_alarm_extra_type", apnType);
        intent.addFlags(268435456);
        intent.putExtra("subscription", SubscriptionController.getInstance().getDefaultDataSubId());
        log("startAlarmForReconnect: delay=" + delay + " action=" + intent.getAction() + " apn=" + apnContext);
        PendingIntent alarmIntent = PendingIntent.getBroadcast(this.mPhone.getContext(), 0, intent, 134217728);
        apnContext.setReconnectIntent(alarmIntent);
        this.mAlarmManager.setExact(2, SystemClock.elapsedRealtime() + ((long) delay), alarmIntent);
    }

    private void startAlarmForRestartTrySetup(int delay, ApnContext apnContext) {
        String apnType = apnContext.getApnType();
        Intent intent = new Intent("com.android.internal.telephony.data-restart-trysetup." + apnType);
        intent.addFlags(268435456);
        intent.putExtra("restart_trysetup_alarm_extra_type", apnType);
        log("startAlarmForRestartTrySetup: delay=" + delay + " action=" + intent.getAction() + " apn=" + apnContext);
        PendingIntent alarmIntent = PendingIntent.getBroadcast(this.mPhone.getContext(), 0, intent, 134217728);
        apnContext.setReconnectIntent(alarmIntent);
        this.mAlarmManager.setExact(2, SystemClock.elapsedRealtime() + ((long) delay), alarmIntent);
    }

    private void notifyNoData(DcFailCause lastFailCauseCode, ApnContext apnContext) {
        log("notifyNoData: type=" + apnContext.getApnType());
        if (isPermanentFail(lastFailCauseCode) && !apnContext.getApnType().equals("default")) {
            this.mPhone.notifyDataConnectionFailed(apnContext.getReason(), apnContext.getApnType());
        }
    }

    private void onRecordsLoaded() {
        log("onRecordsLoaded: createAllApnList");
        this.mAutoAttachOnCreationConfig = this.mPhone.getContext().getResources().getBoolean(17956987);
        createAllApnList();
        if (this.mPhone.mCi.getRadioState().isOn()) {
            log("onRecordsLoaded: notifying data availability");
            notifyOffApnsOfAvailability(Phone.REASON_SIM_LOADED);
        }
        setupDataOnConnectableApns(Phone.REASON_SIM_LOADED);
    }

    protected void onSetDependencyMet(String apnType, boolean met) {
        if (!"hipri".equals(apnType)) {
            ApnContext apnContext = (ApnContext) this.mApnContexts.get(apnType);
            if (apnContext == null) {
                loge("onSetDependencyMet: ApnContext not found in onSetDependencyMet(" + apnType + ", " + met + ")");
                return;
            }
            applyNewState(apnContext, apnContext.isEnabled(), met);
            if ("default".equals(apnType)) {
                apnContext = (ApnContext) this.mApnContexts.get("hipri");
                if (apnContext != null) {
                    applyNewState(apnContext, apnContext.isEnabled(), met);
                }
            }
        }
    }

    private void applyNewState(ApnContext apnContext, boolean enabled, boolean met) {
        boolean cleanup = false;
        boolean trySetup = false;
        log("applyNewState(" + apnContext.getApnType() + ", " + enabled + "(" + apnContext.isEnabled() + "), " + met + "(" + apnContext.getDependencyMet() + "))");
        if (apnContext.isReady()) {
            cleanup = true;
            if (enabled && met) {
                switch (C00955.$SwitchMap$com$android$internal$telephony$DctConstants$State[apnContext.getState().ordinal()]) {
                    case 1:
                    case 2:
                    case 4:
                    case 6:
                        log("applyNewState: 'ready' so return");
                        return;
                    case 3:
                    case 5:
                    case 7:
                        trySetup = true;
                        apnContext.setReason(Phone.REASON_DATA_ENABLED);
                        break;
                }
            } else if (met) {
                apnContext.setReason(Phone.REASON_DATA_DISABLED);
                if (apnContext.getApnType() == "dun" && teardownForDun()) {
                    cleanup = true;
                } else {
                    String salesCode = SystemProperties.get("ro.csc.sales_code");
                    cleanup = "GG".equals("DGG") || "CG".equals("DGG") || "ATT".equals(salesCode) || "TMB".equals(salesCode);
                }
            } else {
                apnContext.setReason(Phone.REASON_DATA_DEPENDENCY_UNMET);
            }
        } else if (enabled && met) {
            if (apnContext.isEnabled()) {
                apnContext.setReason(Phone.REASON_DATA_DEPENDENCY_MET);
            } else {
                apnContext.setReason(Phone.REASON_DATA_ENABLED);
            }
            if (apnContext.getState() == State.FAILED) {
                apnContext.setState(State.IDLE);
            }
            trySetup = true;
        }
        apnContext.setEnabled(enabled);
        apnContext.setDependencyMet(met);
        if (cleanup) {
            cleanUpConnection(true, apnContext);
        }
        if (trySetup) {
            trySetupData(apnContext);
        }
        if (false) {
            ApnContext defaultApnContext = (ApnContext) this.mApnContexts.get("default");
            if (defaultApnContext != null) {
                trySetupData(defaultApnContext);
            }
        }
    }

    private DcAsyncChannel checkForCompatibleConnectedApnContext(ApnContext apnContext) {
        String apnType = apnContext.getApnType();
        ApnSetting dunSetting = null;
        if ("dun".equals(apnType)) {
            dunSetting = fetchDunApn();
        }
        log("checkForCompatibleConnectedApnContext: apnContext=" + apnContext);
        if (IncludeFixedApn(apnType)) {
            dunSetting = fetchApn(apnType);
        }
        DcAsyncChannel potentialDcac = null;
        ApnContext potentialApnCtx = null;
        for (ApnContext curApnCtx : this.mApnContexts.values()) {
            DcAsyncChannel curDcac = curApnCtx.getDcAc();
            log("curDcac: " + curDcac);
            String salesCode = SystemProperties.get("ro.csc.sales_code");
            if ("CHN".equals(salesCode) || "CHM".equals(salesCode) || "CHC".equals(salesCode) || "CHU".equals(salesCode) || "CTC".equals(salesCode)) {
                String curApnType = curApnCtx.getApnType();
                if ("default".equals(apnType) && "mms".equals(curApnType)) {
                }
            }
            if (curDcac != null) {
                ApnSetting apnSetting = curApnCtx.getApnSetting();
                log("apnSetting: " + apnSetting);
                if (dunSetting == null) {
                    if (apnSetting != null && apnSetting.canHandleType(apnType)) {
                        switch (C00955.$SwitchMap$com$android$internal$telephony$DctConstants$State[curApnCtx.getState().ordinal()]) {
                            case 1:
                                log("checkForCompatibleConnectedApnContext: found canHandle conn=" + curDcac + " curApnCtx=" + curApnCtx);
                                return curDcac;
                            case 3:
                            case 4:
                                potentialDcac = curDcac;
                                potentialApnCtx = curApnCtx;
                                break;
                            default:
                                break;
                        }
                    }
                } else if (dunSetting.equals(apnSetting)) {
                    switch (C00955.$SwitchMap$com$android$internal$telephony$DctConstants$State[curApnCtx.getState().ordinal()]) {
                        case 1:
                            log("checkForCompatibleConnectedApnContext: found dun conn=" + curDcac + " curApnCtx=" + curApnCtx);
                            return curDcac;
                        case 3:
                        case 4:
                            potentialDcac = curDcac;
                            potentialApnCtx = curApnCtx;
                            break;
                        default:
                            break;
                    }
                } else {
                    continue;
                }
            } else {
                continue;
            }
        }
        if (potentialDcac != null) {
            log("checkForCompatibleConnectedApnContext: found potential conn=" + potentialDcac + " curApnCtx=" + potentialApnCtx);
            return potentialDcac;
        }
        log("checkForCompatibleConnectedApnContext: NO conn apnContext=" + apnContext);
        return null;
    }

    protected void onEnableApn(int apnId, int enabled) {
        boolean z = true;
        ApnContext apnContext = (ApnContext) this.mApnContexts.get(apnIdToType(apnId));
        if (apnContext == null) {
            loge("onEnableApn(" + apnId + ", " + enabled + "): NO ApnContext");
            return;
        }
        log("onEnableApn: apnContext=" + apnContext + " call applyNewState");
        if (enabled != 1) {
            z = false;
        }
        applyNewState(apnContext, z, apnContext.getDependencyMet());
    }

    protected boolean onTrySetupData(String reason) {
        log("onTrySetupData: reason=" + reason);
        setupDataOnConnectableApns(reason);
        return true;
    }

    protected boolean onTrySetupData(ApnContext apnContext) {
        log("onTrySetupData: apnContext=" + apnContext);
        return trySetupData(apnContext);
    }

    protected void onRoamingOff() {
        log("onRoamingOff");
        if (!IgnoreDataEnabledOnRoaming() && !this.mUserDataEnabled) {
            return;
        }
        if (getDataOnRoamingEnabled()) {
            notifyDataConnection(Phone.REASON_ROAMING_OFF);
            return;
        }
        notifyOffApnsOfAvailability(Phone.REASON_ROAMING_OFF);
        setupDataOnConnectableApns(Phone.REASON_ROAMING_OFF);
    }

    protected void onRoamingOn() {
        if (IgnoreDataEnabledOnRoaming() || this.mUserDataEnabled) {
            if (getDataOnRoamingEnabled()) {
                log("onRoamingOn: setup data on roaming");
                setupDataOnConnectableApns(Phone.REASON_ROAMING_ON);
                notifyDataConnection(Phone.REASON_ROAMING_ON);
            } else {
                log("onRoamingOn: Tear down data connection on roaming.");
                cleanUpAllConnections(true, Phone.REASON_ROAMING_ON);
                notifyOffApnsOfAvailability(Phone.REASON_ROAMING_ON);
            }
            setRoamingPreferredApn();
        }
    }

    protected void onRadioAvailable() {
        log("onRadioAvailable");
        if (this.mPhone.getSimulatedRadioControl() != null) {
            notifyDataConnection(null);
            log("onRadioAvailable: We're on the simulator; assuming data is connected");
        }
        IccRecords r = (IccRecords) this.mIccRecords.get();
        if (r != null && r.getRecordsLoaded()) {
            notifyOffApnsOfAvailability(null);
        }
        if (getOverallState() != State.IDLE) {
            cleanUpConnection(true, null);
        }
    }

    protected void onRadioOffOrNotAvailable() {
        this.mReregisterOnReconnectFailure = false;
        if (this.mPhone.getSimulatedRadioControl() != null) {
            log("We're on the simulator; assuming radio off is meaningless");
        } else {
            log("onRadioOffOrNotAvailable: is off and clean up all connections");
            cleanUpAllConnections(false, Phone.REASON_RADIO_TURNED_OFF);
        }
        notifyOffApnsOfAvailability(null);
    }

    protected void completeConnection(ApnContext apnContext) {
        boolean isProvApn = apnContext.isProvisioningApn();
        log("completeConnection: successful, notify the world apnContext=" + apnContext);
        if (this.mIsProvisioning && !TextUtils.isEmpty(this.mProvisioningUrl)) {
            log("completeConnection: MOBILE_PROVISIONING_ACTION url=" + this.mProvisioningUrl);
            Intent newIntent = Intent.makeMainSelectorActivity("android.intent.action.MAIN", "android.intent.category.APP_BROWSER");
            newIntent.setData(Uri.parse(this.mProvisioningUrl));
            newIntent.setFlags(272629760);
            try {
                this.mPhone.getContext().startActivity(newIntent);
            } catch (ActivityNotFoundException e) {
                loge("completeConnection: startActivityAsUser failed" + e);
            }
        }
        this.mIsProvisioning = false;
        this.mProvisioningUrl = null;
        if (this.mProvisioningSpinner != null) {
            sendMessage(obtainMessage(270378, this.mProvisioningSpinner));
        }
        this.mPhone.notifyDataConnection(apnContext.getReason(), apnContext.getApnType());
        startNetStatPoll();
        startDataStallAlarm(false);
        if (CscFeature.getInstance().getEnableStatus("CscFeature_CIQ_BroadcastState")) {
            PDPContextStateBroadcaster.sendConnected(this.mPhone.getContext(), apnContext);
        }
    }

    protected void onDataSetupComplete(AsyncResult ar) {
        DcFailCause cause = DcFailCause.UNKNOWN;
        boolean handleError = false;
        if (ar.userObj instanceof ApnContext) {
            ApnContext apnContext = ar.userObj;
            ApnSetting apn;
            if (ar.exception == null) {
                DcAsyncChannel dcac = apnContext.getDcAc();
                this.retryTCECounter = 0;
                if (dcac == null) {
                    log("onDataSetupComplete: no connection to DC, handle as error");
                    cause = DcFailCause.CONNECTION_TO_DATACONNECTIONAC_BROKEN;
                    handleError = true;
                } else {
                    apn = apnContext.getApnSetting();
                    log("onDataSetupComplete: success apn=" + (apn == null ? "unknown" : apn.apn));
                    if (!(apn == null || apn.proxy == null || apn.proxy.length() == 0)) {
                        try {
                            String port = apn.port;
                            if (TextUtils.isEmpty(port)) {
                                port = "8080";
                            }
                            dcac.setLinkPropertiesHttpProxySync(new ProxyInfo(apn.proxy, Integer.parseInt(port), null));
                        } catch (NumberFormatException e) {
                            loge("onDataSetupComplete: NumberFormatException making ProxyProperties (" + apn.port + "): " + e);
                        }
                    }
                    if (TextUtils.equals(apnContext.getApnType(), "default")) {
                        SystemProperties.set(PUPPET_MASTER_RADIO_STRESS_TEST, "true");
                        if (this.mCanSetPreferApn && this.mPreferredApn == null) {
                            log("onDataSetupComplete: PREFERED APN is null");
                            this.mPreferredApn = apn;
                            if (this.mPreferredApn != null) {
                                setPreferredApn(this.mPreferredApn.id);
                            }
                        }
                    } else {
                        SystemProperties.set(PUPPET_MASTER_RADIO_STRESS_TEST, "false");
                    }
                    apnContext.setState(State.CONNECTED);
                    boolean isProvApn = apnContext.isProvisioningApn();
                    ConnectivityManager cm = ConnectivityManager.from(this.mPhone.getContext());
                    if (this.mProvisionBroadcastReceiver != null) {
                        this.mPhone.getContext().unregisterReceiver(this.mProvisionBroadcastReceiver);
                        this.mProvisionBroadcastReceiver = null;
                    }
                    if ("KDI".equals("")) {
                        isProvApn = false;
                    }
                    if (!isProvApn || this.mIsProvisioning) {
                        cm.setProvisioningNotificationVisible(false, 0, this.mProvisionActionName);
                        completeConnection(apnContext);
                    } else {
                        log("onDataSetupComplete: successful, BUT send connected to prov apn as mIsProvisioning:" + this.mIsProvisioning + " == false" + " && (isProvisioningApn:" + isProvApn + " == true");
                        this.mProvisionBroadcastReceiver = new ProvisionNotificationBroadcastReceiver(cm.getMobileProvisioningUrl(), TelephonyManager.getDefault().getNetworkOperatorName());
                        this.mPhone.getContext().registerReceiver(this.mProvisionBroadcastReceiver, new IntentFilter(this.mProvisionActionName));
                        cm.setProvisioningNotificationVisible(true, 0, this.mProvisionActionName);
                        setRadio(false);
                        Intent intent = new Intent("android.intent.action.DATA_CONNECTION_CONNECTED_TO_PROVISIONING_APN");
                        intent.putExtra(Carriers.APN, apnContext.getApnSetting().apn);
                        intent.putExtra("apnType", apnContext.getApnType());
                        String apnType = apnContext.getApnType();
                        LinkProperties linkProperties = getLinkProperties(apnType);
                        if (linkProperties != null) {
                            intent.putExtra("linkProperties", linkProperties);
                            String iface = linkProperties.getInterfaceName();
                            if (iface != null) {
                                intent.putExtra("iface", iface);
                            }
                        }
                        NetworkCapabilities networkCapabilities = getNetworkCapabilities(apnType);
                        if (networkCapabilities != null) {
                            intent.putExtra("networkCapabilities", networkCapabilities);
                        }
                        this.mPhone.getContext().sendBroadcastAsUser(intent, UserHandle.ALL);
                    }
                    log("onDataSetupComplete: SETUP complete type=" + apnContext.getApnType() + ", reason:" + apnContext.getReason());
                }
            } else {
                cause = (DcFailCause) ar.result;
                apn = apnContext.getApnSetting();
                String str = "onDataSetupComplete: error apn=%s cause=%s";
                Object[] objArr = new Object[2];
                objArr[0] = apn == null ? "unknown" : apn.apn;
                objArr[1] = cause;
                log(String.format(str, objArr));
                if (cause.isEventLoggable()) {
                    int cid = getCellLocationId();
                    EventLog.writeEvent(EventLogTags.PDP_SETUP_FAIL, new Object[]{Integer.valueOf(cause.ordinal()), Integer.valueOf(cid), Integer.valueOf(TelephonyManager.getDefault().getNetworkType())});
                }
                apn = apnContext.getApnSetting();
                this.mPhone.notifyPreciseDataConnectionFailed(apnContext.getReason(), apnContext.getApnType(), apn != null ? apn.apn : "unknown", cause.toString());
                if ("VZW-CDMA".equals("")) {
                    if (cause.isPeramanentFailVzw()) {
                        int radioTech = this.mPhone.getServiceState().getRilDataRadioTechnology();
                        if (radioTech != 14) {
                            log("onDataSetupComplete: skip permanent fail for RAT(" + radioTech + ")");
                        } else {
                            if (apnContext.getPermanentRetryCount() == this.MAX_RETRY_FOR_PERMANENT_FAILURE - 1) {
                                apnContext.decWaitingApnsPermFailCount();
                                this.mPermanentFailedOperatorNumeric.add(this.mPhone.getServiceState().getOperatorNumeric());
                            } else {
                                apnContext.setPermanentRetryCount(apnContext.getPermanentRetryCount() + 1);
                            }
                        }
                    }
                } else if ("TCE".equals(CscFeature.getInstance().getString("CscFeature_RIL_PDPRetryMechanism4")) && (cause == DcFailCause.SERVICE_OPTION_NOT_SUBSCRIBED || cause == DcFailCause.USER_AUTHENTICATION)) {
                    log("retryTCECounter -> " + this.retryTCECounter);
                    if (this.retryTCECounter >= 2) {
                        apnContext.decWaitingApnsPermFailCount();
                    } else {
                        this.retryTCECounter++;
                    }
                } else if (isPermanentFail(cause)) {
                    apnContext.decWaitingApnsPermFailCount();
                }
                apnContext.removeWaitingApn(apnContext.getApnSetting());
                log(String.format("onDataSetupComplete: WaitingApns.size=%d WaitingApnsPermFailureCountDown=%d", new Object[]{Integer.valueOf(apnContext.getWaitingApns().size()), Integer.valueOf(apnContext.getWaitingApnsPermFailCount())}));
                handleError = true;
            }
            if (handleError) {
                onDataSetupCompleteError(ar);
            }
            if (!this.mInternalDataEnabled) {
                if (("DCGG".equals("DGG") || "DGG".equals("DGG") || "DCGS".equals("DGG") || "DCGGS".equals("DGG")) && (apnContext.getApnType().equals("mms") || apnContext.getApnType().equals("mms2"))) {
                    log("type is mms or mms2, allow it when mInternalDataEnabled is false");
                    return;
                } else {
                    cleanUpAllConnections(null);
                    return;
                }
            }
            return;
        }
        throw new RuntimeException("onDataSetupComplete: No apnContext");
    }

    private int getApnDelay() {
        if (this.mFailFast) {
            return SystemProperties.getInt("persist.radio.apn_ff_delay", CallFailCause.CAUSE_OFFSET);
        }
        return SystemProperties.getInt("persist.radio.apn_delay", POLL_PDP_MILLIS);
    }

    protected void onDataSetupCompleteError(AsyncResult ar) {
        String reason = "";
        DcFailCause cause = DcFailCause.UNKNOWN;
        if (ar.userObj instanceof ApnContext) {
            ApnContext apnContext = ar.userObj;
            if (apnContext.getWaitingApns().isEmpty()) {
                apnContext.setState(State.FAILED);
                this.mPhone.notifyDataConnection(Phone.REASON_APN_FAILED, apnContext.getApnType());
                apnContext.setDataConnectionAc(null);
                cause = (DcFailCause) ar.result;
                if (CscFeature.getInstance().getEnableStatus("CscFeature_CIQ_BroadcastState")) {
                    PDPContextStateBroadcaster.sendDisconnected(this.mPhone.getContext(), apnContext, Phone.REASON_APN_FAILED);
                }
                if (apnContext.getWaitingApnsPermFailCount() == 0) {
                    log("onDataSetupCompleteError: All APN's had permanent failures, stop retrying");
                    if (!"TCE".equals(CscFeature.getInstance().getString("CscFeature_RIL_PDPRetryMechanism4"))) {
                        return;
                    }
                    if ((cause == DcFailCause.SERVICE_OPTION_NOT_SUBSCRIBED || cause == DcFailCause.USER_AUTHENTICATION) && this.retryTCECounter >= 2) {
                        log("display TCE popup");
                        Builder builder = new Builder(this.mPhone.getContext());
                        builder.setTitle(17039380);
                        if (cause == DcFailCause.SERVICE_OPTION_NOT_SUBSCRIBED) {
                            builder.setMessage(17041688);
                        } else {
                            builder.setMessage(17041687);
                        }
                        builder.setPositiveButton(17039370, new C00933());
                        builder.setCancelable(false);
                        AlertDialog alertDialog = builder.create();
                        alertDialog.getWindow().setType(2003);
                        alertDialog.show();
                        return;
                    }
                    return;
                }
                int delay = getApnDelay();
                log("onDataSetupCompleteError: Not all APN's had permanent failures delay=" + delay);
                if ("TCE".equals(CscFeature.getInstance().getString("CscFeature_RIL_PDPRetryMechanism4")) && (cause == DcFailCause.SERVICE_OPTION_NOT_SUBSCRIBED || cause == DcFailCause.USER_AUTHENTICATION)) {
                    delay = 45000;
                    log("TCE retry timer 45s");
                }
                startAlarmForRestartTrySetup(delay, apnContext);
                return;
            } else if ("DCM".equals("")) {
                loge("onDataSetupCompleteError: Block Try next APN");
                return;
            } else {
                log("onDataSetupCompleteError: Try next APN");
                apnContext.setState(State.SCANNING);
                startAlarmForReconnect(getApnDelay(), apnContext);
                return;
            }
        }
        throw new RuntimeException("onDataSetupCompleteError: No apnContext");
    }

    protected void onDisconnectDone(int connId, AsyncResult ar) {
        if (ar.userObj instanceof ApnContext) {
            ApnContext apnContext = ar.userObj;
            log("onDisconnectDone: EVENT_DISCONNECT_DONE apnContext=" + apnContext);
            apnContext.setState(State.IDLE);
            this.mPhone.notifyDataConnection(apnContext.getReason(), apnContext.getApnType());
            if (isDisconnected() && this.mPhone.getServiceStateTracker().processPendingRadioPowerOffAfterDataOff()) {
                log("onDisconnectDone: radio will be turned off, no retries");
                apnContext.setApnSetting(null);
                apnContext.setDataConnectionAc(null);
                if (this.mDisconnectPendingCount > 0) {
                    this.mDisconnectPendingCount--;
                }
                if (this.mDisconnectPendingCount == 0) {
                    notifyDataDisconnectComplete();
                    notifyAllDataDisconnected();
                    return;
                }
                return;
            }
            if (this.mAttached.get() && apnContext.isReady() && retryAfterDisconnected(apnContext)) {
                SystemProperties.set(PUPPET_MASTER_RADIO_STRESS_TEST, "false");
                log("onDisconnectDone: attached, ready and retry after disconnect");
                int radioTech = this.mPhone.getServiceState().getRilDataRadioTechnology();
                if (!"DCM".equals("") || "default".equals(apnContext.getApnType())) {
                    startAlarmForReconnect(getApnDelay(), apnContext);
                } else {
                    log("onDisconnectDone: no need to bring ApnType =" + apnContext.getApnType());
                    apnContext.setApnSetting(null);
                    apnContext.setDataConnectionAc(null);
                    setupDataOnConnectableApns(Phone.REASON_SINGLE_PDN_ARBITRATION);
                }
            } else {
                boolean restartRadioAfterProvisioning = this.mPhone.getContext().getResources().getBoolean(17956970);
                if (apnContext.isProvisioningApn() && restartRadioAfterProvisioning) {
                    log("onDisconnectDone: restartRadio after provisioning");
                    restartRadio();
                }
                apnContext.setApnSetting(null);
                apnContext.setDataConnectionAc(null);
                if (isOnlySingleDcAllowed(this.mPhone.getServiceState().getRilDataRadioTechnology())) {
                    log("onDisconnectDone: isOnlySigneDcAllowed true so setup single apn");
                    setupDataOnConnectableApns(Phone.REASON_SINGLE_PDN_ARBITRATION);
                } else {
                    log("onDisconnectDone: not retrying");
                }
            }
            if (this.mDisconnectPendingCount > 0) {
                this.mDisconnectPendingCount--;
            }
            if (this.mDisconnectPendingCount == 0) {
                notifyDataDisconnectComplete();
                notifyAllDataDisconnected();
            }
            if ("DCGS".equals("DGG") && this.mPhone.getPhoneId() == 0 && this.mPhone.getPhoneType() == 2 && TextUtils.equals(apnContext.getApnType(), "default")) {
                forceDataDeactiveTracker();
                return;
            }
            return;
        }
        loge("onDisconnectDone: Invalid ar in onDisconnectDone, ignore");
    }

    protected void onDisconnectDcRetrying(int connId, AsyncResult ar) {
        if (ar.userObj instanceof ApnContext) {
            ApnContext apnContext = ar.userObj;
            apnContext.setState(State.RETRYING);
            log("onDisconnectDcRetrying: apnContext=" + apnContext);
            this.mPhone.notifyDataConnection(apnContext.getReason(), apnContext.getApnType());
            return;
        }
        loge("onDisconnectDcRetrying: Invalid ar in onDisconnectDone, ignore");
    }

    protected void onVoiceCallStarted() {
        log("onVoiceCallStarted");
        this.mInVoiceCall = true;
        if (("GG".equals("DGG") || "CG".equals("DGG")) && this.insertedSimCount > 1) {
            int phoneId = SubscriptionController.getInstance().getPhoneId(SubscriptionController.getInstance().getDefaultDataSubId());
            for (int simSlot = 0; simSlot < this.insertedSimCount; simSlot++) {
                log("onVoiceCallStarted - simSlot : " + simSlot);
                PhoneBase phone = (PhoneBase) ((PhoneProxy) PhoneFactory.getPhone(simSlot)).getActivePhone();
                if ((phone instanceof GSMPhone) || (phone instanceof CDMAPhone)) {
                    DcTracker dcTracker = (DcTracker) phone.getDcTracker();
                    boolean isConcurrentVoiceAndDataAllowed = phoneId == this.mPhone.getPhoneId() ? this.mPhone.getServiceStateTracker().isConcurrentVoiceAndDataAllowed() : false;
                    if (dcTracker.isConnected() && !isConcurrentVoiceAndDataAllowed) {
                        log("DSDA onVoiceCallStarted stop polling");
                        dcTracker.stopNetStatPoll();
                        dcTracker.stopDataStallAlarm();
                        dcTracker.notifyDataConnection(Phone.REASON_VOICE_CALL_STARTED);
                    }
                }
            }
        } else if (isConnected() && !this.mPhone.getServiceStateTracker().isConcurrentVoiceAndDataAllowed()) {
            log("onVoiceCallStarted stop polling");
            if (CscFeature.getInstance().getEnableStatus("CscFeature_RIL_EnableVoicePriority")) {
                this.mPollingStopped = true;
            }
            String salesCode = SystemProperties.get("ro.csc.sales_code");
            if ("CG".equals("DGG") || "GG".equals("DGG") || "DCGG".equals("DGG") || "DCGGS".equals("DGG") || "DCGS".equals("DGG") || "CHN".equals(salesCode) || "CHM".equals(salesCode) || "CHC".equals(salesCode) || "CHU".equals(salesCode) || "TGY".equals(salesCode) || "BRI".equals(salesCode)) {
                stopNetStatPoll();
                stopDataStallAlarm();
            } else {
                this.mIsHoleOfVoiceCall = true;
                cleanUpAllConnections(true, Phone.REASON_VOICE_CALL_STARTED);
            }
            notifyDataConnection(Phone.REASON_VOICE_CALL_STARTED);
        }
    }

    protected void onVoiceCallEnded() {
        log("onVoiceCallEnded");
        this.mInVoiceCall = false;
        if (("GG".equals("DGG") || "CG".equals("DGG")) && this.insertedSimCount > 1) {
            int phoneId = SubscriptionController.getInstance().getPhoneId(SubscriptionController.getInstance().getDefaultDataSubId());
            for (int simSlot = 0; simSlot < this.insertedSimCount; simSlot++) {
                log(" onVoiceCallEnded - simslot " + simSlot);
                PhoneBase phone = (PhoneBase) ((PhoneProxy) PhoneFactory.getPhone(simSlot)).getActivePhone();
                if ((phone instanceof GSMPhone) || (phone instanceof CDMAPhone)) {
                    DcTracker dcTracker = (DcTracker) phone.getDcTracker();
                    if (dcTracker.isConnected()) {
                        boolean isConcurrentVoiceAndDataAllowed;
                        if (phoneId == this.mPhone.getPhoneId()) {
                            isConcurrentVoiceAndDataAllowed = this.mPhone.getServiceStateTracker().isConcurrentVoiceAndDataAllowed();
                        } else {
                            isConcurrentVoiceAndDataAllowed = false;
                        }
                        if (isConcurrentVoiceAndDataAllowed) {
                            dcTracker.resetPollStats();
                        } else {
                            dcTracker.startNetStatPoll();
                            dcTracker.startDataStallAlarm(false);
                            dcTracker.notifyDataConnection(Phone.REASON_VOICE_CALL_ENDED);
                        }
                    }
                    dcTracker.setupDataOnConnectableApns(Phone.REASON_VOICE_CALL_ENDED);
                }
            }
            return;
        }
        if (isConnected()) {
            if (!this.mPhone.getServiceStateTracker().isConcurrentVoiceAndDataAllowed()) {
                startNetStatPoll();
                startDataStallAlarm(false);
                notifyDataConnection(Phone.REASON_VOICE_CALL_ENDED);
            } else if (!CscFeature.getInstance().getEnableStatus("CscFeature_RIL_EnableVoicePriority")) {
                resetPollStats();
            } else if (this.mPollingStopped) {
                this.mPollingStopped = false;
                startNetStatPoll();
                startDataStallAlarm(false);
                notifyDataConnection(Phone.REASON_VOICE_CALL_ENDED);
            } else {
                resetPollStats();
            }
        }
        if (this.mIsHoleOfVoiceCall) {
            this.mIsHoleOfVoiceCall = false;
        }
        setupDataOnConnectableApns(Phone.REASON_VOICE_CALL_ENDED);
    }

    protected void onCleanUpConnection(boolean tearDown, int apnId, String reason) {
        log("onCleanUpConnection");
        ApnContext apnContext = (ApnContext) this.mApnContexts.get(apnIdToType(apnId));
        if (apnContext != null) {
            apnContext.setReason(reason);
            cleanUpConnection(tearDown, apnContext);
        }
    }

    protected boolean isConnected() {
        for (ApnContext apnContext : this.mApnContexts.values()) {
            if (apnContext.getState() == State.CONNECTED) {
                return true;
            }
        }
        return false;
    }

    public boolean isDisconnected() {
        for (ApnContext apnContext : this.mApnContexts.values()) {
            if (!apnContext.isDisconnected()) {
                return false;
            }
        }
        return true;
    }

    public void notifyDataConnectionForSST(String reason) {
        notifyDataConnection(reason);
    }

    protected void notifyDataConnection(String reason) {
        log("notifyDataConnection: reason=" + reason);
        for (ApnContext apnContext : this.mApnContexts.values()) {
            if (this.mAttached.get() && apnContext.isReady()) {
                log("notifyDataConnection: type:" + apnContext.getApnType());
                this.mPhone.notifyDataConnection(reason != null ? reason : apnContext.getReason(), apnContext.getApnType());
            }
        }
        notifyOffApnsOfAvailability(reason);
    }

    private void createAllApnList() {
        this.mAllApnSettings = new ArrayList();
        IccRecords r = (IccRecords) this.mIccRecords.get();
        String operator = r != null ? r.getOperatorNumeric() : "";
        if (operator != null) {
            String selection = "numeric = '" + operator + "'";
            if ("GG".equals("DGG")) {
                if (this.mPhone.getPhoneId() == 0) {
                    selection = selection + " and current = 1";
                } else {
                    selection = selection + " and current1 = 1";
                }
            }
            log("createAllApnList: selection=" + selection);
            Cursor cursor = this.mPhone.getContext().getContentResolver().query(Carriers.CONTENT_URI, null, selection, null, null);
            if (cursor != null) {
                if (cursor.getCount() > 0) {
                    this.mAllApnSettings = createApnList(cursor);
                }
                cursor.close();
            }
        }
        addEmergencyApnSetting();
        dedupeApnSettings();
        if (this.mAllApnSettings.isEmpty()) {
            log("createAllApnList: No APN found for carrier: " + operator);
            this.mPreferredApn = null;
        } else {
            this.mPreferredApn = getPreferredApn();
            if (!(this.mPreferredApn == null || this.mPreferredApn.numeric.equals(operator))) {
                this.mPreferredApn = null;
                setPreferredApn(-1);
            }
            log("createAllApnList: mPreferredApn=" + this.mPreferredApn);
        }
        if (isDebugLevelNotLow()) {
            log("createAllApnList: X mAllApnSettings=" + this.mAllApnSettings);
        }
        setDataProfilesAsNeeded();
    }

    private boolean isFixedApn(ApnSetting apnsetting) {
        String[] type = apnsetting.types;
        if (type.length == 1 && IncludeFixedApn(type[0])) {
            return true;
        }
        return false;
    }

    private void dedupeApnSettings() {
        ArrayList<ApnSetting> resultApns = new ArrayList();
        String salesCode = SystemProperties.get("ro.csc.sales_code");
        for (int i = 0; i < this.mAllApnSettings.size() - 1; i++) {
            ApnSetting first = (ApnSetting) this.mAllApnSettings.get(i);
            int j = i + 1;
            while (j < this.mAllApnSettings.size()) {
                ApnSetting second = (ApnSetting) this.mAllApnSettings.get(j);
                if (second == null || !apnsSimilar(first, second)) {
                    j++;
                } else if ("CHN".equals(salesCode) || "CHM".equals(salesCode) || "CHC".equals(salesCode) || "CHU".equals(salesCode)) {
                    if (second.canHandleType("default") && !first.canHandleType("default")) {
                        this.mAllApnSettings.set(j, mergeApns(second, first));
                        this.mAllApnSettings.remove(i);
                        break;
                    }
                    newApn = mergeApns(first, second);
                    this.mAllApnSettings.set(i, newApn);
                    first = newApn;
                    this.mAllApnSettings.remove(j);
                } else {
                    newApn = mergeApns(first, second);
                    this.mAllApnSettings.set(i, newApn);
                    first = newApn;
                    this.mAllApnSettings.remove(j);
                }
            }
        }
    }

    private boolean apnTypeSameAny(ApnSetting first, ApnSetting second) {
        int index1 = 0;
        while (index1 < first.types.length) {
            int index2 = 0;
            while (index2 < second.types.length) {
                if (first.types[index1].equals(CharacterSets.MIMENAME_ANY_CHARSET) || second.types[index2].equals(CharacterSets.MIMENAME_ANY_CHARSET) || first.types[index1].equals(second.types[index2])) {
                    return true;
                }
                index2++;
            }
            index1++;
        }
        return false;
    }

    private boolean apnsSimilar(ApnSetting first, ApnSetting second) {
        return !first.canHandleType("dun") && !second.canHandleType("dun") && Objects.equals(first.apn, second.apn) && !apnTypeSameAny(first, second) && xorEquals(first.proxy, second.proxy) && xorEquals(first.port, second.port) && first.carrierEnabled == second.carrierEnabled && first.bearer == second.bearer && first.profileId == second.profileId && Objects.equals(first.mvnoType, second.mvnoType) && Objects.equals(first.mvnoMatchData, second.mvnoMatchData) && xorEquals(first.mmsc, second.mmsc) && xorEquals(first.mmsProxy, second.mmsProxy) && xorEquals(first.mmsPort, second.mmsPort) && !isFixedApn(first) && !isFixedApn(second);
    }

    private boolean xorEquals(String first, String second) {
        return Objects.equals(first, second) || TextUtils.isEmpty(first) || TextUtils.isEmpty(second);
    }

    private ApnSetting mergeApns(ApnSetting dest, ApnSetting src) {
        boolean z;
        ArrayList<String> resultTypes = new ArrayList();
        resultTypes.addAll(Arrays.asList(dest.types));
        for (String srcType : src.types) {
            if (!resultTypes.contains(srcType)) {
                resultTypes.add(srcType);
            }
        }
        String mmsc = TextUtils.isEmpty(dest.mmsc) ? src.mmsc : dest.mmsc;
        String mmsProxy = TextUtils.isEmpty(dest.mmsProxy) ? src.mmsProxy : dest.mmsProxy;
        String mmsPort = TextUtils.isEmpty(dest.mmsPort) ? src.mmsPort : dest.mmsPort;
        String proxy = TextUtils.isEmpty(dest.proxy) ? src.proxy : dest.proxy;
        String port = TextUtils.isEmpty(dest.port) ? src.port : dest.port;
        String protocol = "IPV4V6".equals(src.protocol) ? src.protocol : dest.protocol;
        String roamingProtocol = "IPV4V6".equals(src.roamingProtocol) ? src.roamingProtocol : dest.roamingProtocol;
        int i = dest.id;
        String str = dest.numeric;
        String str2 = dest.carrier;
        String str3 = dest.apn;
        String str4 = dest.user;
        String str5 = dest.password;
        int i2 = dest.authType;
        String[] strArr = (String[]) resultTypes.toArray(new String[0]);
        boolean z2 = dest.carrierEnabled;
        int i3 = dest.bearer;
        int i4 = dest.profileId;
        if (dest.modemCognitive || src.modemCognitive) {
            z = true;
        } else {
            z = false;
        }
        return new ApnSetting(i, str, str2, str3, proxy, port, mmsc, mmsProxy, mmsPort, str4, str5, i2, strArr, protocol, roamingProtocol, z2, i3, i4, z, dest.maxConns, dest.waitTime, dest.maxConnsTime, dest.mtu, dest.mvnoType, dest.mvnoMatchData);
    }

    private DcAsyncChannel createDataConnection() {
        log("createDataConnection E");
        int id = this.mUniqueIdGenerator.getAndIncrement();
        DataConnection conn = DataConnection.makeDataConnection(this.mPhone, id, this, this.mDcTesterFailBringUpAll, this.mDcc);
        this.mDataConnections.put(Integer.valueOf(id), conn);
        DcAsyncChannel dcac = new DcAsyncChannel(conn, "DCT");
        int status = dcac.fullyConnectSync(this.mPhone.getContext(), this, conn.getHandler());
        if (status == 0) {
            this.mDataConnectionAcHashMap.put(Integer.valueOf(dcac.getDataConnectionIdSync()), dcac);
        } else {
            loge("createDataConnection: Could not connect to dcac=" + dcac + " status=" + status);
        }
        log("createDataConnection() X id=" + id + " dc=" + conn);
        return dcac;
    }

    private void destroyDataConnections() {
        if (this.mDataConnections != null) {
            log("destroyDataConnections: clear mDataConnectionList");
            this.mDataConnections.clear();
            return;
        }
        log("destroyDataConnections: mDataConnecitonList is empty, ignore");
    }

    private ArrayList<ApnSetting> buildWaitingApns(String requestedApnType, int radioTech) {
        log("buildWaitingApns: E requestedApnType=" + requestedApnType);
        ArrayList<ApnSetting> apnList = new ArrayList();
        if (requestedApnType.equals("dun")) {
            ApnSetting dun = fetchDunApn();
            if (dun != null) {
                apnList.add(dun);
                log("buildWaitingApns: X added APN_TYPE_DUN apnList=" + apnList);
                return apnList;
            }
        }
        if (IncludeFixedApn(requestedApnType)) {
            ApnSetting lockedApn = fetchApn(requestedApnType);
            if (lockedApn != null) {
                apnList.add(lockedApn);
                log("buildWaitingApns: X added " + requestedApnType + " apnList= " + apnList);
                return apnList;
            }
        }
        if (requestedApnType.equals("ent1")) {
            ApnSetting entApn = fetchApn(requestedApnType);
            if (entApn != null) {
                apnList.add(entApn);
                log("buildWaitingApns: X added APN_TYPE_ENT1 apnList=" + apnList);
            }
        } else {
            String operator;
            boolean usePreferred;
            if (requestedApnType.equals("bip")) {
                ApnSetting bip = fetchBipApn();
                if (bip != null) {
                    apnList.add(bip);
                    log("buildWaitingApns: X added APN_TYPE_BIP apnList=" + apnList);
                }
            }
            IccRecords r = (IccRecords) this.mIccRecords.get();
            if (r != null) {
                operator = r.getOperatorNumeric();
            } else {
                operator = "";
            }
            try {
                if (this.mPhone.getContext().getResources().getBoolean(17956969)) {
                    usePreferred = false;
                } else {
                    usePreferred = true;
                }
            } catch (NotFoundException e) {
                log("buildWaitingApns: usePreferred NotFoundException set to true");
                usePreferred = true;
            }
            this.mPreferredApn = getPreferredApn();
            if (isEnterpriseApn(this.mPreferredApn, requestedApnType)) {
                log("mPreferredApn is ent apn, setting to null for " + requestedApnType);
                this.mPreferredApn = null;
            }
            Log.secD("DCT", "buildWaitingApns: usePreferred=" + usePreferred + " canSetPreferApn=" + this.mCanSetPreferApn + " mPreferredApn=" + this.mPreferredApn + " operator=" + operator + " radioTech=" + radioTech);
            if ("DCM".equals("")) {
                if (requestedApnType.equals("default") && usePreferred && this.mCanSetPreferApn) {
                    if (this.mPreferredApn != null) {
                        apnList.add(this.mPreferredApn);
                    }
                    log("buildWaitingApns: X apnList=" + apnList);
                }
            }
            if (usePreferred && this.mCanSetPreferApn && this.mPreferredApn != null && this.mPreferredApn.canHandleType(requestedApnType)) {
                log("buildWaitingApns: Preferred APN:" + operator + ":" + this.mPreferredApn.numeric + ":" + this.mPreferredApn);
                if (!this.mPreferredApn.numeric.equals(operator)) {
                    log("buildWaitingApns: no preferred APN");
                    setPreferredApn(-1);
                    this.mPreferredApn = null;
                } else if (this.mPreferredApn.bearer == 0 || this.mPreferredApn.bearer == radioTech) {
                    apnList.add(this.mPreferredApn);
                    log("buildWaitingApns: X added preferred apnList=" + apnList);
                } else {
                    log("buildWaitingApns: no preferred APN");
                    setPreferredApn(-1);
                    this.mPreferredApn = null;
                }
            }
            if (this.mAllApnSettings != null) {
                log("buildWaitingApns: mAllApnSettings=" + this.mAllApnSettings);
                boolean cdmaRat = isCdmaRat(radioTech);
                Iterator i$ = this.mAllApnSettings.iterator();
                while (i$.hasNext()) {
                    ApnSetting apn = (ApnSetting) i$.next();
                    log("buildWaitingApns: apn=" + apn);
                    if (!apn.canHandleType(requestedApnType)) {
                        log("buildWaitingApns: couldn't handle requesedApnType=" + requestedApnType);
                    } else if (isEnterpriseApn(apn, requestedApnType)) {
                        log("skipping enterprise apn for " + requestedApnType);
                    } else {
                        log("not skipping enterprise apn for " + requestedApnType);
                        if (apn.bearer == 0 || apn.bearer == radioTech) {
                            log("buildWaitingApns: adding apn=" + apn.toString());
                            apnList.add(apn);
                        } else {
                            log("buildWaitingApns: bearer:" + apn.bearer + " != " + "radioTech:" + radioTech);
                        }
                    }
                }
            } else {
                loge("mAllApnSettings is empty!");
            }
            log("buildWaitingApns: X apnList=" + apnList);
        }
        return apnList;
    }

    private boolean isEnterpriseApn(ApnSetting apn, String requestedApnType) {
        boolean isEntApn = false;
        if (apn != null) {
            log("checking isEnterpriseApn " + requestedApnType + " " + apn.numeric);
            if (!("ent1".equals(requestedApnType) || "ent2".equals(requestedApnType) || apn.numeric == null || apn.numeric.length() <= 4)) {
                String mcc = apn.numeric.substring(0, 3);
                String mnc = apn.numeric.substring(3);
                log("checking isEnterpriseApn " + apn.apn + " " + mcc + " " + mnc);
                try {
                    return IConnectivityManager.Stub.asInterface(ServiceManager.getService("connectivity")).isEnterpriseApn(apn.apn, mcc, mnc);
                } catch (RemoteException e) {
                    loge("error in validating enterprise apn " + e);
                    return isEntApn;
                } catch (Throwable th) {
                    return isEntApn;
                }
            }
        }
        return isEntApn;
    }

    private String apnListToString(ArrayList<ApnSetting> apns) {
        StringBuilder result = new StringBuilder();
        int size = apns.size();
        for (int i = 0; i < size; i++) {
            result.append('[').append(((ApnSetting) apns.get(i)).toString()).append(']');
        }
        return result.toString();
    }

    private void setPreferredApn(int pos) {
        if (this.mCanSetPreferApn) {
            log("setPreferredApn: delete");
            ContentResolver resolver = this.mPhone.getContext().getContentResolver();
            if ("CG".equals("DGG")) {
                if (TelephonyManager.getDefault().getNetworkType(this.mPhone.getSubId()) == 13 || TelephonyManager.getDefault().getNetworkType(this.mPhone.getSubId()) == 14) {
                    resolver.delete(Uri.parse(PREFERAPN_NO_UPDATE_URI_USING_SUBID.toString() + "4G"), null, null);
                } else {
                    resolver.delete(Uri.parse(PREFERAPN_NO_UPDATE_URI_USING_SUBID.toString() + this.mPhone.getSubId()), null, null);
                }
            } else if ("GG".equals("DGG") || "DCGG".equals("DGG") || "DGG".equals("DGG") || "DCGS".equals("DGG") || "DCGGS".equals("DGG")) {
                resolver.delete(Uri.parse(PREFERAPN_NO_UPDATE_URI_USING_SUBID.toString() + this.mPhone.getSubId()), null, null);
            } else {
                resolver.delete(PREFERAPN_NO_UPDATE_URI, null, null);
            }
            if (pos >= 0) {
                log("setPreferredApn: insert");
                ContentValues values = new ContentValues();
                values.put(APN_ID, Integer.valueOf(pos));
                if ("CG".equals("DGG")) {
                    if (TelephonyManager.getDefault().getNetworkType(this.mPhone.getSubId()) == 13 || TelephonyManager.getDefault().getNetworkType(this.mPhone.getSubId()) == 14) {
                        resolver.insert(Uri.parse(PREFERAPN_NO_UPDATE_URI_USING_SUBID.toString() + "4G"), values);
                    } else {
                        resolver.insert(Uri.parse(PREFERAPN_NO_UPDATE_URI_USING_SUBID.toString() + this.mPhone.getSubId()), values);
                    }
                } else if ("GG".equals("DGG") || "DCGG".equals("DGG") || "DGG".equals("DGG") || "DCGS".equals("DGG") || "DCGGS".equals("DGG")) {
                    resolver.insert(Uri.parse(PREFERAPN_NO_UPDATE_URI_USING_SUBID.toString() + this.mPhone.getSubId()), values);
                } else {
                    resolver.insert(PREFERAPN_NO_UPDATE_URI, values);
                }
                notifyApnChangeToRIL();
                return;
            }
            return;
        }
        log("setPreferredApn: X !canSEtPreferApn");
    }

    private ApnSetting getPreferredApn() {
        if (this.mAllApnSettings == null || this.mAllApnSettings.isEmpty()) {
            log("getPreferredApn: X not found mAllApnSettings.isEmpty");
            return null;
        }
        Cursor cursor;
        String gid = SystemProperties.get("gsm.sim.operator.gid", "");
        String contryCode = SystemProperties.get("ro.csc.countryiso_code", "");
        if ("CG".equals("DGG")) {
            if (TelephonyManager.getDefault().getNetworkType(this.mPhone.getSubId()) == 13 || TelephonyManager.getDefault().getNetworkType(this.mPhone.getSubId()) == 14) {
                cursor = this.mPhone.getContext().getContentResolver().query(Uri.parse(PREFERAPN_NO_UPDATE_URI_USING_SUBID.toString() + "4G"), new String[]{"_id", "name", Carriers.APN}, null, null, Carriers.DEFAULT_SORT_ORDER);
            } else {
                cursor = this.mPhone.getContext().getContentResolver().query(Uri.parse(PREFERAPN_NO_UPDATE_URI_USING_SUBID.toString() + this.mPhone.getSubId()), new String[]{"_id", "name", Carriers.APN}, null, null, Carriers.DEFAULT_SORT_ORDER);
            }
        } else if ("GG".equals("DGG") || "DCGG".equals("DGG") || "DGG".equals("DGG") || "DCGS".equals("DGG") || "DCGGS".equals("DGG")) {
            cursor = this.mPhone.getContext().getContentResolver().query(Uri.parse(PREFERAPN_NO_UPDATE_URI_USING_SUBID.toString() + this.mPhone.getSubId()), new String[]{"_id", "name", Carriers.APN}, null, null, Carriers.DEFAULT_SORT_ORDER);
        } else if (TextUtils.isEmpty(gid) || !"CA".equals(contryCode)) {
            cursor = this.mPhone.getContext().getContentResolver().query(PREFERAPN_NO_UPDATE_URI, new String[]{"_id", "name", Carriers.APN}, null, null, Carriers.DEFAULT_SORT_ORDER);
        } else {
            cursor = this.mPhone.getContext().getContentResolver().query(PREFERAPN_NO_UPDATE_URI, new String[]{"_id", "name", Carriers.APN}, "mvno_type=\"gid\" and mvno_match_data=\"" + gid + "\"", null, Carriers.DEFAULT_SORT_ORDER);
        }
        if (cursor != null) {
            this.mCanSetPreferApn = true;
        } else {
            this.mCanSetPreferApn = false;
        }
        log("getPreferredApn: mRequestedApnType=" + this.mRequestedApnType + " cursor=" + cursor + " cursor.count=" + (cursor != null ? cursor.getCount() : 0));
        if (this.mCanSetPreferApn && cursor.getCount() > 0) {
            cursor.moveToFirst();
            int pos = cursor.getInt(cursor.getColumnIndexOrThrow("_id"));
            Iterator i$ = this.mAllApnSettings.iterator();
            while (i$.hasNext()) {
                ApnSetting p = (ApnSetting) i$.next();
                log("getPreferredApn: apnSetting=" + p);
                if (p.id == pos && p.canHandleType(this.mRequestedApnType)) {
                    log("getPreferredApn: X found apnSetting" + p);
                    cursor.close();
                    return p;
                }
            }
        }
        if (cursor != null) {
            cursor.close();
        }
        log("getPreferredApn: X not found");
        return null;
    }

    public ApnSetting getPreferredApnEx() {
        return getPreferredApn();
    }

    public void handleMessage(Message msg) {
        boolean tearDown = false;
        log("handleMessage msg=" + msg);
        if (!this.mPhone.mIsTheCurrentActivePhone || this.mIsDisposed) {
            loge("handleMessage: Ignore GSM msgs since GSM phone is inactive");
        } else if (isActiveDataSubscription()) {
            ApnContext apnContext;
            switch (msg.what) {
                case 270338:
                    if ("VZW-CDMA".equals("")) {
                        IccRecords usimIccRecords = getUiccRecords(1);
                        IccRecords ruimIccRecords = getUiccRecords(2);
                        if (usimIccRecords != null && !usimIccRecords.getRecordsLoaded()) {
                            log(" SIMRecords load is not completed");
                            usimIccRecords.registerForRecordsLoaded(this, 270338, null);
                            return;
                        } else if (!(ruimIccRecords == null || ruimIccRecords.getRecordsLoaded())) {
                            log(" RuimRecords load is not completed");
                            ruimIccRecords.registerForRecordsLoaded(this, 270338, null);
                            return;
                        }
                    }
                    onRecordsLoaded();
                    return;
                case 270339:
                    if (msg.obj instanceof ApnContext) {
                        onTrySetupData((ApnContext) msg.obj);
                        return;
                    } else if (msg.obj instanceof String) {
                        onTrySetupData((String) msg.obj);
                        return;
                    } else {
                        loge("EVENT_TRY_SETUP request w/o apnContext or String");
                        return;
                    }
                case 270345:
                    if (CscFeature.getInstance().getEnableStatus("CscFeature_CIQ_BroadcastState")) {
                        for (ApnContext apnContext2 : this.mApnContexts.values()) {
                            if (apnContext2.isReady()) {
                                PDPContextStateBroadcaster.sendDisconnected(this.mPhone.getContext(), apnContext2);
                            }
                        }
                    }
                    onDataConnectionDetached();
                    return;
                case 270352:
                    onDataConnectionAttached();
                    return;
                case 270354:
                    doRecovery();
                    return;
                case 270355:
                    onApnChanged();
                    return;
                case 270358:
                    log("EVENT_PS_RESTRICT_ENABLED " + this.mIsPsRestricted);
                    stopNetStatPoll();
                    stopDataStallAlarm();
                    this.mIsPsRestricted = true;
                    return;
                case 270359:
                    log("EVENT_PS_RESTRICT_DISABLED " + this.mIsPsRestricted);
                    this.mIsPsRestricted = false;
                    if (isConnected()) {
                        startNetStatPoll();
                        startDataStallAlarm(false);
                        return;
                    }
                    if (this.mState == State.FAILED) {
                        cleanUpAllConnections(false, Phone.REASON_PS_RESTRICT_ENABLED);
                        this.mReregisterOnReconnectFailure = false;
                    }
                    apnContext2 = (ApnContext) this.mApnContexts.get("default");
                    if (apnContext2 != null) {
                        apnContext2.setReason(Phone.REASON_PS_RESTRICT_ENABLED);
                        trySetupData(apnContext2);
                        return;
                    }
                    loge("**** Default ApnContext not found ****");
                    if (Build.IS_DEBUGGABLE) {
                        throw new RuntimeException("Default ApnContext not found");
                    }
                    return;
                case 270360:
                    if (msg.arg1 != 0) {
                        tearDown = true;
                    }
                    log("EVENT_CLEAN_UP_CONNECTION tearDown=" + tearDown);
                    if (msg.obj instanceof ApnContext) {
                        cleanUpConnection(tearDown, (ApnContext) msg.obj);
                        return;
                    }
                    loge("EVENT_CLEAN_UP_CONNECTION request w/o apn context, call super");
                    super.handleMessage(msg);
                    return;
                case 270361:
                    onCdmaOtaProvision();
                    return;
                case 270363:
                    boolean enabled;
                    if (msg.arg1 == 1) {
                        enabled = true;
                    } else {
                        enabled = false;
                    }
                    onSetInternalDataEnabled(enabled, (Message) msg.obj);
                    return;
                case 270365:
                    Message mCause = obtainMessage(270365, null);
                    if (msg.obj != null && (msg.obj instanceof String)) {
                        mCause.obj = msg.obj;
                    }
                    super.handleMessage(mCause);
                    return;
                case 270377:
                    String salesCode = SystemProperties.get("ro.csc.sales_code");
                    if ("ATT".equals(salesCode) || "TMB".equals(salesCode)) {
                        log("Discard nwTypeChanged due to better LostMultiApnSupport and MultiApnSupport");
                        return;
                    } else if (!this.mPhone.getServiceStateTracker().isSameGroupRat()) {
                        setupDataOnConnectableApns(Phone.REASON_NW_TYPE_CHANGED);
                        return;
                    } else {
                        return;
                    }
                case 270378:
                    if (this.mProvisioningSpinner == msg.obj) {
                        this.mProvisioningSpinner.dismiss();
                        this.mProvisioningSpinner = null;
                        return;
                    }
                    return;
                case 270437:
                    onChangeVoiceDomain();
                    return;
                case 270438:
                    onVoiceDomainDone();
                    return;
                case 270439:
                    onRoutingAreaChanged();
                    return;
                default:
                    super.handleMessage(msg);
                    return;
            }
        } else {
            loge("Ignore msgs since phone is not the current DDS");
        }
    }

    protected int getApnProfileID(String apnType) {
        if (TextUtils.equals(apnType, "ims")) {
            return 2;
        }
        if (TextUtils.equals(apnType, "fota")) {
            return 3;
        }
        if (TextUtils.equals(apnType, "cbs")) {
            return 4;
        }
        if (TextUtils.equals(apnType, "ia")) {
            return 0;
        }
        if (TextUtils.equals(apnType, "dun")) {
            return 1;
        }
        if (TextUtils.equals(apnType, "mms")) {
            return 5;
        }
        if (TextUtils.equals(apnType, "mms2")) {
            return 5;
        }
        if (TextUtils.equals(apnType, "hipri")) {
            return 7;
        }
        if (TextUtils.equals(apnType, "supl")) {
            return 6;
        }
        if (TextUtils.equals(apnType, "cmdm")) {
            return 9;
        }
        if (TextUtils.equals(apnType, "cmmail")) {
            return 10;
        }
        if (TextUtils.equals(apnType, "wap")) {
            return 11;
        }
        if (TextUtils.equals(apnType, "bip")) {
            return com.android.internal.telephony.cdma.CallFailCause.CDMA_REORDER;
        }
        if (TextUtils.equals(apnType, "cas")) {
            return com.android.internal.telephony.cdma.CallFailCause.CDMA_SO_REJECT;
        }
        if (TextUtils.equals(apnType, "ent1")) {
            return 12;
        }
        if (TextUtils.equals(apnType, "ent2")) {
            return 13;
        }
        if (TextUtils.equals(apnType, "xcap")) {
            return 14;
        }
        return 0;
    }

    private int getCellLocationId() {
        CellLocation loc = this.mPhone.getCellLocation();
        if (loc == null) {
            return -1;
        }
        if (loc instanceof GsmCellLocation) {
            return ((GsmCellLocation) loc).getCid();
        }
        if (loc instanceof CdmaCellLocation) {
            return ((CdmaCellLocation) loc).getBaseStationId();
        }
        return -1;
    }

    public boolean matchIccRecord(String operatorNumeric) {
        IccRecords r = (IccRecords) this.mIccRecords.get();
        if (operatorNumeric.equals(r != null ? r.getOperatorNumeric() : "")) {
            return true;
        }
        return false;
    }

    public void UpdateIccRecords(boolean isCdma) {
        if (this.mUiccController != null) {
            IccRecords newIccRecords;
            if (isCdma) {
                newIccRecords = this.mUiccController.getIccRecords(this.mPhone.getPhoneId(), 2);
            } else {
                newIccRecords = this.mUiccController.getIccRecords(this.mPhone.getPhoneId(), 1);
            }
            IccRecords r = (IccRecords) this.mIccRecords.get();
            if (r != newIccRecords) {
                if (r != null) {
                    log("Removing stale icc objects.");
                    this.mIccRecords.set(null);
                }
                if (newIccRecords != null) {
                    log("New records found");
                    this.mIccRecords.set(newIccRecords);
                }
            }
        }
    }

    private IccRecords getUiccRecords(int appFamily) {
        return this.mUiccController.getIccRecords(this.mPhone.getPhoneId(), appFamily);
    }

    protected void onUpdateIcc() {
        if (this.mUiccController != null) {
            IccRecords newIccRecords = null;
            if (this.mPhone != null) {
                log("onUpdateIcc(): phone type=" + this.mPhone.getPhoneType());
            }
            if (this.mPhone == null || this.mPhone.getPhoneType() != 2) {
                newIccRecords = getUiccRecords(1);
            } else if ("LGT".equals("") || "USC-CDMA".equals("") || "LRA-CDMA".equals("")) {
                newIccRecords = getUiccRecords(1);
                if (newIccRecords == null && "USC-CDMA".equals("")) {
                    log(" No USIM, Only CSIM available");
                    newIccRecords = getUiccRecords(2);
                }
            } else {
                if ("DCGG".equals("DGG") || "DCGS".equals("DGG") || "DCGGS".equals("DGG")) {
                    long[] subId = SubscriptionController.getInstance().getSubId(this.mPhone.getPhoneId());
                    TelephonyManager.getDefault();
                    String iccType = TelephonyManager.getTelephonyProperty("ril.ICC_TYPE", subId[0], "0");
                    log("onUpdateIcc: slot= " + this.mPhone.getPhoneId() + ";iccType=" + iccType);
                    newIccRecords = (!"4".equals(iccType) || "1".equals(SystemProperties.get("ril.IsCSIM", ""))) ? getUiccRecords(2) : getUiccRecords(2);
                }
                if (newIccRecords == null) {
                    log(" No CSIM, Only USIM available");
                    newIccRecords = getUiccRecords(1);
                }
            }
            IccRecords r = (IccRecords) this.mIccRecords.get();
            if (r != newIccRecords) {
                if (r != null) {
                    log("Removing stale icc objects.");
                    r.unregisterForRecordsLoaded(this);
                    this.mIccRecords.set(null);
                }
                if (newIccRecords != null) {
                    log("New records found");
                    this.mIccRecords.set(newIccRecords);
                    newIccRecords.registerForRecordsLoaded(this, 270338, null);
                }
            }
        }
    }

    public void update() {
        boolean z = true;
        log("update sub = " + this.mPhone.getSubId());
        if ("DCGG".equals("DGG") || "DGG".equals("DGG") || "DCGS".equals("DGG") || "DCGGS".equals("DGG")) {
            if (this.mIsUpdated) {
                log("already updated");
                return;
            } else {
                log("update once");
                this.mIsUpdated = true;
            }
        }
        if (isActiveDataSubscription()) {
            log("update(): Active DDS, register for all events now!");
            registerForAllEvents();
            onUpdateIcc();
            if (Global.getInt(this.mPhone.getContext().getContentResolver(), "mobile_data", 1) != 1) {
                z = false;
            }
            this.mUserDataEnabled = z;
            if (this.mPhone instanceof CDMALTEPhone) {
                ((CDMALTEPhone) this.mPhone).updateCurrentCarrierInProvider();
                supplyMessenger();
                return;
            } else if (this.mPhone instanceof GSMPhone) {
                ((GSMPhone) this.mPhone).updateCurrentCarrierInProvider();
                supplyMessenger();
                return;
            } else {
                log("Phone object is not MultiSim. This should not hit!!!!");
                return;
            }
        }
        unregisterForAllEvents();
        log("update(): NOT the active DDS, unregister for all events!");
    }

    public void cleanUpAllConnections(String cause) {
        cleanUpAllConnections(cause, null);
    }

    public void updateRecords() {
        if (isActiveDataSubscription()) {
            onUpdateIcc();
        }
    }

    public void cleanUpAllConnections(String cause, Message disconnectAllCompleteMsg) {
        log("cleanUpAllConnections");
        if (disconnectAllCompleteMsg != null) {
            this.mDisconnectAllCompleteMsgList.add(disconnectAllCompleteMsg);
        }
        Message msg = obtainMessage(270365);
        msg.obj = cause;
        sendMessage(msg);
    }

    protected void notifyDataDisconnectComplete() {
        log("notifyDataDisconnectComplete");
        Iterator i$ = this.mDisconnectAllCompleteMsgList.iterator();
        while (i$.hasNext()) {
            ((Message) i$.next()).sendToTarget();
        }
        this.mDisconnectAllCompleteMsgList.clear();
    }

    protected void notifyAllDataDisconnected() {
        sEnableFailFastRefCounter = 0;
        this.mFailFast = false;
        this.mAllDataDisconnectedRegistrants.notifyRegistrants();
    }

    public void registerForAllDataDisconnected(Handler h, int what, Object obj) {
        this.mAllDataDisconnectedRegistrants.addUnique(h, what, obj);
        if (isDisconnected()) {
            log("notify All Data Disconnected");
            notifyAllDataDisconnected();
        }
    }

    public void unregisterForAllDataDisconnected(Handler h) {
        this.mAllDataDisconnectedRegistrants.remove(h);
    }

    protected void onSetInternalDataEnabled(boolean enable) {
        onSetInternalDataEnabled(enable, null);
    }

    protected void onSetInternalDataEnabled(boolean enabled, Message onCompleteMsg) {
        boolean sendOnComplete = true;
        synchronized (this.mDataEnabledLock) {
            this.mInternalDataEnabled = enabled;
            if (enabled) {
                log("onSetInternalDataEnabled: changed to enabled, try to setup data call");
                onTrySetupData(Phone.REASON_DATA_ENABLED);
            } else {
                sendOnComplete = false;
                log("onSetInternalDataEnabled: changed to disabled, cleanUpAllConnections");
                cleanUpAllConnections(null, onCompleteMsg);
            }
        }
        if (sendOnComplete && onCompleteMsg != null) {
            onCompleteMsg.sendToTarget();
        }
    }

    public boolean setInternalDataEnabledFlag(boolean enable) {
        log("setInternalDataEnabledFlag(" + enable + ")");
        if (this.mInternalDataEnabled != enable) {
            this.mInternalDataEnabled = enable;
        }
        return true;
    }

    public boolean setInternalDataEnabled(boolean enable) {
        return setInternalDataEnabled(enable, null);
    }

    public boolean setInternalDataEnabled(boolean enable, Message onCompleteMsg) {
        log("setInternalDataEnabled(" + enable + ")");
        Message msg = obtainMessage(270363, onCompleteMsg);
        msg.arg1 = enable ? 1 : 0;
        sendMessage(msg);
        return true;
    }

    protected boolean isActiveDataSubscription() {
        long prefSubId = SubscriptionController.getInstance().getDefaultDataSubId();
        int slotId = SubscriptionController.getInstance().getSlotId(prefSubId);
        if ("CG".equals("DGG") || "DCGG".equals("DGG") || "DGG".equals("DGG") || "DCGS".equals("DGG") || "DCGGS".equals("DGG") || "GG".equals("DGG") || slotId <= -1 || slotId >= TelephonyManager.getDefault().getSimCount() || this.mPhone.getSubId() == prefSubId) {
            return true;
        }
        return false;
    }

    public void setDataAllowed(boolean enable, Message response) {
        if ("DCGG".equals("DGG") || "DGG".equals("DGG") || "DCGS".equals("DGG") || "DCGGS".equals("DGG")) {
            log("setDataAllowed, enable=" + enable);
            setInternalDataEnabled(enable, response);
            return;
        }
        this.mPhone.mCi.setDataAllowed(enable, response);
    }

    protected void log(String s) {
        Rlog.d("DCT-" + this.mPhone.getPhoneId() + "/" + this.mPhone.getPhoneType() + "/" + this.mPhone.getSubId(), s);
    }

    protected void loge(String s) {
        Rlog.e("DCT-" + this.mPhone.getPhoneId() + "/" + this.mPhone.getPhoneType() + "/" + this.mPhone.getSubId(), s);
    }

    protected boolean isDebugLevelNotLow() {
        if (SystemProperties.get(LOG_LEVEL_PROP, LOG_LEVEL_PROP_LOW).equalsIgnoreCase(LOG_LEVEL_PROP_LOW)) {
            return false;
        }
        return true;
    }

    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        pw.println("DataConnectionTracker extends:");
        super.dump(fd, pw, args);
        pw.println(" mReregisterOnReconnectFailure=" + this.mReregisterOnReconnectFailure);
        pw.println(" canSetPreferApn=" + this.mCanSetPreferApn);
        pw.println(" mIsUpdated=" + this.mIsUpdated);
        pw.println(" mApnObserver=" + this.mApnObserver);
        pw.println(" getOverallState=" + getOverallState());
        pw.println(" mDataConnectionAsyncChannels=%s\n" + this.mDataConnectionAcHashMap);
        pw.println(" mAttached=" + this.mAttached.get());
    }

    public String[] getPcscfAddress(String apnType) {
        log("getPcscfAddress()");
        if (apnType == null) {
            log("apnType is null, return null");
            return null;
        }
        ApnContext apnContext;
        if (TextUtils.equals(apnType, "emergency")) {
            apnContext = (ApnContext) this.mApnContexts.get("emergency");
        } else if (TextUtils.equals(apnType, "ims")) {
            apnContext = (ApnContext) this.mApnContexts.get("ims");
        } else {
            log("apnType is invalid, return null");
            return null;
        }
        if (apnContext == null) {
            log("apnContext is null, return null");
            return null;
        }
        DcAsyncChannel dcac = apnContext.getDcAc();
        if (dcac == null) {
            return null;
        }
        String[] result = dcac.getPcscfAddr();
        for (int i = 0; i < result.length; i++) {
            log("Pcscf[" + i + "]: " + result[i]);
        }
        return result;
    }

    public void setImsRegistrationState(boolean registered) {
        log("setImsRegistrationState - mImsRegistrationState(before): " + this.mImsRegistrationState + ", registered(current) : " + registered);
        if (this.mPhone != null) {
            ServiceStateTracker sst = this.mPhone.getServiceStateTracker();
            if (sst != null) {
                sst.setImsRegistrationState(registered);
            }
        }
    }

    private void initEmergencyApnSetting() {
        Cursor cursor = this.mPhone.getContext().getContentResolver().query(Carriers.CONTENT_URI, null, "type=\"emergency\"", null, null);
        if (cursor != null) {
            if (cursor.getCount() > 0 && cursor.moveToFirst()) {
                this.mEmergencyApn = makeApnSetting(cursor);
            }
            cursor.close();
        }
    }

    private void addEmergencyApnSetting() {
        if (this.mEmergencyApn == null) {
            return;
        }
        if (this.mAllApnSettings == null) {
            this.mAllApnSettings = new ArrayList();
            return;
        }
        boolean hasEmergencyApn = false;
        Iterator i$ = this.mAllApnSettings.iterator();
        while (i$.hasNext()) {
            if (ArrayUtils.contains(((ApnSetting) i$.next()).types, "emergency")) {
                hasEmergencyApn = true;
                break;
            }
        }
        if (hasEmergencyApn) {
            log("addEmergencyApnSetting - E-APN setting is already present");
        } else {
            this.mAllApnSettings.add(this.mEmergencyApn);
        }
    }

    private void setupDataForRetryConnection() {
        for (ApnContext apnContext : this.mApnContexts.values()) {
            if (this.mApnTypesAllowedOnDataDisabled.contains(apnContext.getApnType())) {
            }
            if (apnContext.getApnType().equals("default") && apnContext.getDcAc() != null) {
                if (apnContext.getState() == State.FAILED) {
                    apnContext.setState(State.IDLE);
                }
                if (apnContext.isConnectable() || apnContext.isConnecting()) {
                    log("setupDataForRetryConnection: isConnectable() or isConnecting() call trySetupData");
                    cancelReconnectAlarm(apnContext);
                    sendMessage(obtainMessage(270339, apnContext));
                }
            }
        }
    }

    private boolean preProcessDataConnection(String reason) {
        int gprsState = this.mPhone.getServiceStateTracker().getCurrentDataConnectionState();
        loge("preProcessDataConnections:" + reason);
        loge("mNeedDataSelctionPopup:" + this.mNeedDataSelctionPopup);
        loge("mNeedRoamingDataSelctionPopup:" + this.mNeedRoamingDataSelctionPopup);
        loge("mWaitingForUserSelection:" + this.mWaitingForUserSelection);
        loge("gprsState:" + gprsState);
        loge("getRoaming:" + this.mPhone.getServiceState().getRoaming());
        if (gprsState != 0) {
            log("preProcessDataConnection : not in service state.. false");
            return false;
        } else if ("trigger_restart_min_framework".equals(SystemProperties.get("vold.decrypt")) || "1".equals(SystemProperties.get("vold.decrypt"))) {
            log("preProcessDataConnection : decrypt... false");
            return false;
        } else {
            String salesCode = SystemProperties.get("ro.csc.sales_code", "none");
            if ("CHU".equals(salesCode) || "CHM".equals(salesCode) || "CHN".equals(salesCode) || "CHC".equals(salesCode) || "BRI".equals(salesCode) || "TGY".equals(salesCode)) {
                log("preProcessDataConnection : CHINA do not support data selection menu. So, adding false value in mNeedDataSelectionPopup parameter.");
                this.mNeedDataSelctionPopup = false;
            }
            if (this.mIsFotaMode) {
                if (this.mPhone.getServiceState().getRoaming()) {
                    this.mNeedRoamingDataSelctionPopup = false;
                } else {
                    this.mNeedDataSelctionPopup = false;
                }
                this.mIsFotaMode = false;
                loge("preProcessDataConnection : Fota Upgrade(Auto Install)");
            } else if ((!this.mPhone.getServiceState().getRoaming() && this.mNeedDataSelctionPopup) || (this.mPhone.getServiceState().getRoaming() && this.mNeedRoamingDataSelctionPopup)) {
                if (this.mWaitingForUserSelection) {
                    return false;
                }
                this.mWaitingForUserSelection = true;
                this.mPhone.getContext().sendBroadcast(new Intent("android.intent.action.ACTION_DATA_SELECTION_POPUP"));
                loge("preProcessDataConnection : send ACTION_DATA_SELECTION_POPUP intent");
                return false;
            }
            return true;
        }
    }

    private boolean checkForCleanUpConnectionForApnChanged() {
        if (this.mAllApnSettings == null || this.mAllApnSettings.isEmpty()) {
            return true;
        }
        for (DcAsyncChannel dcac : this.mDataConnectionAcHashMap.values()) {
            ApnSetting dcacApn = dcac.getApnSettingSync();
            if (dcacApn != null) {
                log("checkForCleanUpConnectionForApnChanged: apn:" + dcacApn);
                if (dcacApn.canHandleType("default")) {
                    ApnSetting preferredApn = getPreferredApn();
                    if (preferredApn == null || !dcacApn.equals(preferredApn)) {
                        return true;
                    }
                } else {
                    boolean isContainedApn = false;
                    Iterator i$ = this.mAllApnSettings.iterator();
                    while (i$.hasNext()) {
                        if (dcacApn.equals((ApnSetting) i$.next())) {
                            isContainedApn = true;
                            break;
                        }
                    }
                    if (!isContainedApn) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private void checkAndDisconnectChangedApns() {
        if (this.mApnContexts != null) {
            if (this.mAllApnSettings == null || this.mAllApnSettings.isEmpty()) {
                cleanUpAllConnections(true, Phone.REASON_APN_CHANGED);
            }
            for (ApnContext curApnContext : this.mApnContexts.values()) {
                if (curApnContext != null) {
                    State state = curApnContext.getState();
                    ApnSetting curApnSetting = curApnContext.getApnSetting();
                    if (curApnSetting == null) {
                        log("checkAndDisconnectChangedApns: Apn is null");
                    } else {
                        boolean bTypeFound = false;
                        Iterator i$ = this.mAllApnSettings.iterator();
                        while (i$.hasNext()) {
                            ApnSetting curDataProfile = (ApnSetting) i$.next();
                            if (curDataProfile != null) {
                                ApnSetting apn = curDataProfile;
                                if (apn.canHandleType(curApnContext.getApnType()) && curApnSetting.bearer == apn.bearer) {
                                    bTypeFound = true;
                                    if (curApnSetting.hasChanged(apn)) {
                                        log("APN: " + apn + " has changed. Disconnect");
                                        curApnContext.setReason(Phone.REASON_APN_CHANGED);
                                        if (state == State.CONNECTED || state == State.CONNECTING || state == State.RETRYING) {
                                            cleanUpConnection(true, curApnContext);
                                        } else {
                                            if (state == State.FAILED) {
                                                curApnContext.setState(State.IDLE);
                                            }
                                            this.mPhone.notifyDataConnection(curApnContext.getReason(), curApnContext.getApnType());
                                        }
                                    }
                                    if (!bTypeFound) {
                                        log("ApnType not found for " + curApnContext.getApnType() + " in mAllApnSettings");
                                        curApnContext.setReason(Phone.REASON_APN_CHANGED);
                                        cleanUpConnection(true, curApnContext);
                                    }
                                }
                            }
                        }
                        if (!bTypeFound) {
                            log("ApnType not found for " + curApnContext.getApnType() + " in mAllApnSettings");
                            curApnContext.setReason(Phone.REASON_APN_CHANGED);
                            cleanUpConnection(true, curApnContext);
                        }
                    }
                }
            }
        }
    }

    private void checkAndDisconnectChangedApnProfiles() {
        if (this.mAllApnSettings != null && !this.mAllApnSettings.isEmpty()) {
            for (DcAsyncChannel dcac : this.mDataConnectionAcHashMap.values()) {
                ApnSetting dcacApn = dcac.getApnSettingSync();
                ApnSetting cleanupApn = null;
                if (dcacApn != null) {
                    Iterator i$;
                    log("checkAndDisconnectChangedApnProfiles: apn:" + dcacApn);
                    if (dcacApn.canHandleType("default")) {
                        ApnSetting preferredApn = getPreferredApn();
                        if (preferredApn == null || !dcacApn.equals(preferredApn)) {
                            cleanupApn = dcacApn;
                        }
                    } else {
                        boolean isContainedApn = false;
                        i$ = this.mAllApnSettings.iterator();
                        while (i$.hasNext()) {
                            if (dcacApn.equals((ApnSetting) i$.next())) {
                                isContainedApn = true;
                                break;
                            }
                        }
                        if (!isContainedApn) {
                            cleanupApn = dcacApn;
                        }
                    }
                    if (cleanupApn != null) {
                        for (ApnContext apnContext : this.mApnContexts.values()) {
                            if (apnContext.getApnSetting() != null && cleanupApn.equals(apnContext.getApnSetting())) {
                                log("checkAndDisconnectChangedApnProfiles: cleanupApn:" + cleanupApn);
                                apnContext.setReason(Phone.REASON_APN_CHANGED);
                                cleanUpConnection(true, apnContext);
                            }
                        }
                    }
                }
            }
        }
    }

    public void DoGprsAttachOrDetach(Phone phone, int value) {
        if (value == -1 || phone == null) {
            log("value == -1 or phone == null");
            return;
        }
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(bos);
        try {
            dos.writeByte(9);
            dos.writeByte(9);
            dos.writeShort(5);
            if (value == 1) {
                dos.writeByte(1);
            } else {
                dos.writeByte(0);
            }
            phone.invokeOemRilRequestRaw(bos.toByteArray(), null);
            log("GprsAttachOrDetach, value = " + value);
        } catch (IOException e) {
            log("GprsAttachOrDetach, e=" + e);
        }
        if (dos != null) {
            try {
                dos.close();
            } catch (IOException e2) {
                log("GprsAttachOrDetach(), close() fail!!!");
                return;
            }
        }
        if (bos != null) {
            bos.close();
        }
    }

    public void setDataSubscription(int phoneId) {
        this.mPhones = PhoneFactory.getPhones();
        log("setDataSubscription to simSlot in GG mode:" + phoneId);
        if (!"GSM".equals(SystemProperties.get("gsm.sim.selectnetwork"))) {
            return;
        }
        if (phoneId == 0) {
            log("Set Slot2 Ps disable, And Slot1 Ps auto-attached");
            DoGprsAttachOrDetach(this.mPhones[1], 0);
            return;
        }
        log("Set Slot1 Ps disable, And Slot2 Ps auto-attached");
        DoGprsAttachOrDetach(this.mPhones[0], 0);
    }

    private void notifyApnChangeToRIL() {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(bos);
        try {
            dos.writeByte(9);
            dos.writeByte(4);
            dos.writeShort(4);
            this.mPhone.mCi.invokeOemRilRequestRaw(bos.toByteArray(), null);
            log("refresh Attach APN");
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

    private void notifyLteRoamingApnChangeToRIL(boolean isLteRoaming) {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(bos);
        try {
            dos.writeByte(22);
            dos.writeByte(12);
            dos.writeShort(5);
            dos.writeByte(isLteRoaming ? 1 : 0);
            this.mPhone.mCi.invokeOemRilRequestRaw(bos.toByteArray(), null);
            Rlog.d("DCT", "LTE_ROAMING : notifyLteRoamingApnChangeToRIL");
        } catch (IOException e) {
            Rlog.i("DCT", "exception occured during refresh Attach APN" + e);
        }
        if (dos != null) {
            try {
                dos.close();
            } catch (IOException e2) {
                Rlog.w("LOG_TAG", "close fail!!!");
                return;
            }
        }
        if (bos != null) {
            bos.close();
        }
    }

    private void setLteRoamingStatus(boolean isLteRoaming) {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(bos);
        try {
            dos.writeByte(22);
            dos.writeByte(CMD_SET_LTE_ROAMING_STATUS);
            dos.writeShort(5);
            dos.writeByte(isLteRoaming ? 1 : 0);
            this.mPhone.mCi.invokeOemRilRequestRaw(bos.toByteArray(), null);
            Rlog.d("DCT", "LTE_ROAMING : notifyLteRoamingStatusChangeToRIL");
        } catch (IOException e) {
            Rlog.i("DCT", "exception occured during refresh Attach APN" + e);
        }
        if (dos != null) {
            try {
                dos.close();
            } catch (IOException e2) {
                Rlog.w("LOG_TAG", "close fail!!!");
                return;
            }
        }
        if (bos != null) {
            bos.close();
        }
    }

    protected void changeConfigureForLteRoaming() {
        Cursor cursor = null;
        IccRecords r = (IccRecords) this.mIccRecords.get();
        String operator = r != null ? r.getOperatorNumeric() : "";
        String roamingApn = "";
        boolean isLteRoaming = getLteDataOnRoamingEnabled();
        if (operator == null && "4".equals(SystemProperties.get("ril.simtype", ""))) {
            operator = "45005";
        }
        String selection = "numeric = '" + operator + "'" + " and carrier_enabled_roaming = 1";
        log("LTE_ROAMING : lte_roaming_mode_on is " + (isLteRoaming ? " enabled " : " disabled "));
        try {
            cursor = this.mPhone.getContext().getContentResolver().query(Uri.parse("content://nwkinfo/nwkinfo/carriers"), null, selection, null, null);
            if (cursor == null) {
                loge("changeConfigureForLteRoaming: No record found.");
            } else if (cursor.getCount() == 1 && cursor.moveToFirst()) {
                do {
                    roamingApn = cursor.getString(cursor.getColumnIndexOrThrow(Carriers.APN));
                } while (cursor.moveToNext());
            }
            if (cursor != null) {
                cursor.close();
            }
        } catch (Exception e) {
            loge("changeConfigureForLteRoaming: exception caught : " + e);
            if (cursor != null) {
                cursor.close();
            }
        } catch (Throwable th) {
            if (cursor != null) {
                cursor.close();
            }
        }
        String where = String.format("numeric = '%s' AND apn ='%s'", new Object[]{operator, roamingApn});
        log("LTE_ROAMING : current Apn is = " + where);
        ContentValues values;
        if ("45005".equals(operator) && roamingApn != null) {
            values = new ContentValues();
            if (isLteRoaming) {
                values.put(Carriers.APN, "lte-roaming.sktelecom.com");
            } else {
                values.put(Carriers.APN, "roaming.sktelecom.com");
            }
            if (this.mResolver.update(Uri.parse("content://nwkinfo/nwkinfo/carriers"), values, where, null) > 0) {
                log("LTE_ROAMING : success to update apn in nwk_info.db");
            } else {
                log("LTE_ROAMING : fail to update apn in nwk_info.db");
            }
            if (this.mResolver.update(Carriers.CONTENT_URI, values, where, null) > 0) {
                log("LTE_ROAMING : success to update apn in telephony.db");
            } else {
                log("LTE_ROAMING : fail to update apn in telephony.db");
            }
        } else if ("3".equals(SystemProperties.get("ril.simtype", "")) && roamingApn != null) {
            values = new ContentValues();
            if (isLteRoaming) {
                values.put(Carriers.APN, "lte-roaming.lguplus.co.kr");
            } else {
                values.put(Carriers.APN, "wroaming.lguplus.co.kr");
            }
            if (this.mResolver.update(Uri.parse("content://nwkinfo/nwkinfo/carriers"), values, where, null) > 0) {
                log("LTE_ROAMING : success to update apn in nwk_info.db");
            } else {
                log("LTE_ROAMING : fail to update apn in nwk_info.db");
            }
            if (this.mResolver.update(Carriers.CONTENT_URI, values, where, null) > 0) {
                log("LTE_ROAMING : success to update apn in telephony.db");
            } else {
                log("LTE_ROAMING : fail to update apn in telephony.db");
            }
            if (!this.mPhone.getServiceState().getRoaming()) {
                return;
            }
            if (getDataOnRoamingEnabled() && isLteRoaming) {
                this.mPhone.mCi.setPreferredNetworkType(9, null);
                return;
            } else {
                this.mPhone.mCi.setPreferredNetworkType(3, null);
                return;
            }
        }
        if ((!this.mPhone.getServiceState().getRoaming() && this.mPhone.getServiceState().getState() == 0) || "domestic".equals(SystemProperties.get("ril.currentplmn", ""))) {
            log("LTE_ROAMING : Don't need to serPreferredNetwork in home area");
        } else if (isLteRoaming) {
            this.mPhone.mCi.setPreferredNetworkType(9, null);
        } else {
            this.mPhone.mCi.setPreferredNetworkType(3, null);
        }
    }

    private void notifyChangeProfileReqToRIL(int type, Message response) {
        int i = 1;
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(bos);
        switch (type) {
            case 1:
                try {
                    dos.writeByte(9);
                    dos.writeByte(5);
                    dos.writeShort(6);
                    dos.writeByte(type);
                    if (!this.misVolteOn) {
                        i = 0;
                    }
                    dos.writeByte(i);
                    log("notifyChangeProfileReqToRIL [" + type + "][" + this.misVolteOn + "]");
                    break;
                } catch (IOException e) {
                    log("exception occured during ChangeProfileToRIL" + e);
                    break;
                }
            case 2:
                dos.writeByte(9);
                dos.writeByte(5);
                dos.writeShort(6);
                dos.writeByte(type);
                if (!this.mTetherRequested) {
                    i = 0;
                }
                dos.writeByte(i);
                log("notifyChangeProfileReqToRIL [" + type + "][" + this.mTetherRequested + "]");
                break;
            case 3:
                dos.writeByte(9);
                dos.writeByte(5);
                dos.writeShort(5);
                dos.writeByte(type);
                log("notifyChangeProfileReqToRIL [" + type + "]");
                break;
            default:
                return;
        }
        this.mPhone.mCi.invokeOemRilRequestRaw(bos.toByteArray(), response);
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

    private void notifyDefaultData(ApnContext apnContext) {
        log("notifyDefaultData: type=" + apnContext.getApnType() + ", reason:" + apnContext.getReason());
        apnContext.setState(State.CONNECTED);
        this.mPhone.notifyDataConnection(apnContext.getReason(), apnContext.getApnType());
        startNetStatPoll();
        startDataStallAlarm(false);
        if (CscFeature.getInstance().getEnableStatus("CscFeature_CIQ_BroadcastState")) {
            PDPContextStateBroadcaster.sendConnected(this.mPhone.getContext(), apnContext);
        }
    }

    private DcAsyncChannel checkForDuplicatedConnectedApnContext(ApnSetting apn) {
        if (this.mApnContexts == null) {
            return null;
        }
        for (ApnContext curApnCtx : this.mApnContexts.values()) {
            DcAsyncChannel curDcac = curApnCtx.getDcAc();
            if (curDcac != null) {
                ApnSetting curApn = curApnCtx.getApnSetting();
                if (curApn != null && curApn.apn.equalsIgnoreCase(apn.apn)) {
                    boolean isDuplicatedUser = false;
                    boolean isDuplicatedPassword = false;
                    boolean isDuplicatedProxy = false;
                    if ((TextUtils.isEmpty(curApn.user) && TextUtils.isEmpty(apn.user)) || (!TextUtils.isEmpty(curApn.user) && curApn.user.equals(apn.user))) {
                        isDuplicatedUser = true;
                    }
                    if ((TextUtils.isEmpty(curApn.password) && TextUtils.isEmpty(apn.password)) || (!TextUtils.isEmpty(curApn.password) && curApn.password.equals(apn.password))) {
                        isDuplicatedPassword = true;
                    }
                    if ((TextUtils.isEmpty(curApn.proxy) && TextUtils.isEmpty(apn.proxy)) || (!TextUtils.isEmpty(curApn.proxy) && curApn.proxy.equals(apn.proxy))) {
                        isDuplicatedProxy = true;
                    }
                    String simNum = TelephonyManager.getDefault().getSimOperator(SubscriptionController.getInstance().getSubId(this.mPhone.getPhoneId())[0]);
                    if ("MM1".equals(SystemProperties.get("ro.csc.sales_code")) || "46000".equals(simNum) || "46001".equals(simNum) || "46002".equals(simNum) || "46007".equals(simNum) || "46008".equals(simNum) || "45412".equals(simNum) || "45413".equals(simNum)) {
                        if (isDuplicatedUser && isDuplicatedPassword) {
                            log("checkForDuplicatedConnectedApnContext: apn=" + apn.apn + " found conn=" + curDcac);
                            return curDcac;
                        }
                    } else if (isDuplicatedUser && isDuplicatedPassword && isDuplicatedProxy) {
                        log("checkForDuplicatedConnectedApnContext: apn=" + apn.apn + " found conn=" + curDcac);
                        return curDcac;
                    }
                }
            }
        }
        log("checkForDuplicatedConnectedApnContext: apn=" + apn.apn + " NO conn");
        return null;
    }

    protected void onScreenStateChanged(boolean screenState) {
        if (screenState) {
            setupDataForRetryConnection();
        }
    }

    protected void onTetherStateChanged(boolean tetherOn) {
        TetherStateChanged(tetherOn);
    }

    private void TetherStateChanged(boolean tetherOn) {
        if (tetherOn) {
            this.mTetherRequested = true;
            notifyChangeProfileReqToRIL(2, null);
            sendMessage(obtainMessage(270355));
            return;
        }
        this.mTetherRequested = false;
        notifyChangeProfileReqToRIL(2, null);
        sendMessage(obtainMessage(270355));
    }

    protected void onVoLteOn(boolean isVoLteOn) {
        this.misVolteOn = isVoLteOn;
        log("onVoLteOn: isVoLteOn= " + isVoLteOn);
        ApnContext apnContext = (ApnContext) this.mApnContexts.get("ims");
        log("onVoLteOn: apnContext(ims) state=" + apnContext.getState());
        if ("DCM".equals("")) {
            IMSInterfaceForGeneral mInterfaceForGeneral = (IMSInterfaceForGeneral) CommonIMSInterface.getInstance(7, this.mPhone.getContext());
            if (mInterfaceForGeneral == null) {
                log("onVoLteOn: mInterfaceForGeneral is null.");
            } else if (!this.misVolteOn) {
                mInterfaceForGeneral.manualDeregister();
                log("onVoLteOn: manualDeregister");
            }
            if (this.misVolteOn || (!this.misVolteOn && apnContext.isDisconnected())) {
                notifyChangeProfileReqToRIL(1, obtainMessage(270437));
                return;
            } else {
                this.mVolteTurnOff = true;
                return;
            }
        }
        sendMessage(obtainMessage(270437));
    }

    protected void onChangeVoiceDomain() {
        this.mPhone.mCi.setVoiceDomainPref(this.misVolteOn ? 3 : 0, obtainMessage(270438));
    }

    protected void onVoiceDomainDone() {
        if ("DCM".equals("")) {
            this.mPhone.mCi.setPreferredNetworkType(SystemProperties.getInt("persist.radio.setnwkmode", 9), null);
            IMSInterfaceForGeneral mInterfaceForGeneral = (IMSInterfaceForGeneral) CommonIMSInterface.getInstance(7, this.mPhone.getContext());
            if (mInterfaceForGeneral == null) {
                log("onVoLteOn: mInterfaceForGeneral is null.");
            } else if (this.misVolteOn) {
                mInterfaceForGeneral.manualRegister();
                log("onVoLteOn: manualRegister");
            }
        }
    }

    public boolean isConnecting() {
        for (ApnContext apnContext : this.mApnContexts.values()) {
            if (apnContext.isConnecting()) {
                return true;
            }
        }
        return false;
    }

    public String getDefaultApnName() {
        ApnSetting preferredApn = getPreferredApn();
        if (preferredApn == null) {
            return null;
        }
        return preferredApn.apn;
    }

    public boolean IsApnExist(String type) {
        if (type.equals("bip") && fetchBipApn() != null) {
            return true;
        }
        if (this.mAllApnSettings != null) {
            Iterator i$ = this.mAllApnSettings.iterator();
            while (i$.hasNext()) {
                if (((ApnSetting) i$.next()).canHandleType(type)) {
                    return true;
                }
            }
        }
        return false;
    }

    private void onCdmaOtaProvision() {
        if (("VZW-CDMA".equals("") || "CSP".equals("")) && !this.mPhone.needsOtaServiceProvisioning()) {
            log("onCdmaOtaProvision: trySetupData if needed");
            setupDataOnConnectableApns(Phone.REASON_CDMA_OTA_PROVISIONED);
        }
    }

    private boolean isDataAllowedOnDataDisabled(ApnContext apnContext) {
        if (apnContext == null) {
            return false;
        }
        boolean isAllowed = false;
        String apnType = apnContext.getApnType();
        if (!this.mApnTypesAllowedOnDataDisabled.contains(apnType)) {
            return false;
        }
        if (!("".equals(CscFeature.getInstance().getString("CscFeature_RIL_ConfigAlwaysOnApn", "")) || this.mConditions4AlwaysOnApns == null)) {
            Set<String> options = (Set) this.mConditions4AlwaysOnApns.get(apnType);
            if (options != null) {
                String[] optArray = (String[]) options.toArray(new String[0]);
                String simNumeric = TelephonyManager.getDefault().getSimOperator(SubscriptionManager.getSubId(this.mPhone.getPhoneId())[0]);
                for (String option : optArray) {
                    try {
                        Integer.parseInt(option);
                        if ((option.length() == 5 || option.length() == 6) && simNumeric.equals(option)) {
                            isAllowed = true;
                            log("isDataAllowedOnDataDisabled: forcing by numeric enabled for " + apnType + " on " + simNumeric);
                        }
                    } catch (NumberFormatException e) {
                    }
                }
            }
        }
        if (!(this.mConditions4AlwaysOnApns == null || this.mConditions4AlwaysOnApns.isEmpty())) {
            if (this.mPhone.getServiceState().getRoaming() && (!getDataOnRoamingEnabled() || (CscFeature.getInstance().getEnableStatus("CscFeature_RIL_PromptToDataRoam") && this.mNeedRoamingDataSelctionPopup))) {
                if (this.mConditions4AlwaysOnApns != null) {
                    Set options2 = (Set) this.mConditions4AlwaysOnApns.get(apnType);
                    if (!(options2 == null || options2.contains("home"))) {
                        isAllowed = true;
                    }
                }
                if (!isAllowed) {
                    log("isDataAllowedOnDataDisabled: not allowed due to roaming");
                    return false;
                }
            } else if (isDomesticModel() && !isDomesticCard()) {
                log("isDataAllowedOnDataDisabled: not allowed in case of doemstic in oversea");
                return false;
            }
        }
        int gprsState = this.mPhone.getServiceStateTracker().getCurrentDataConnectionState();
        IccRecords r = (IccRecords) this.mIccRecords.get();
        boolean recordsLoaded = r != null ? r.getRecordsLoaded() : false;
        boolean desiredPowerState = this.mPhone.getServiceStateTracker().getDesiredPowerState();
        boolean allowed = (gprsState == 0 || this.mAutoAttachOnCreation) && recordsLoaded && ((this.mPhone.getState() == PhoneConstants.State.IDLE || this.mPhone.getServiceStateTracker().isConcurrentVoiceAndDataAllowed()) && !this.mIsPsRestricted && desiredPowerState);
        if (allowed) {
            return allowed;
        }
        String reason = "";
        if (!(gprsState == 0 || this.mAutoAttachOnCreation)) {
            reason = reason + " - gprs= " + gprsState;
        }
        if (!recordsLoaded) {
            reason = reason + " - SIM not loaded";
        }
        if (!(this.mPhone.getState() == PhoneConstants.State.IDLE || this.mPhone.getServiceStateTracker().isConcurrentVoiceAndDataAllowed())) {
            reason = (reason + " - PhoneState= " + this.mPhone.getState()) + " - Concurrent voice and data not allowed";
        }
        if (this.mIsPsRestricted) {
            reason = reason + " - mIsPsRestricted= true";
        }
        if (!desiredPowerState) {
            reason = reason + " - desiredPowerState= false";
        }
        log("isDataAllowedOnDataDisabled: not allowed due to" + reason);
        return allowed;
    }

    private boolean getLTEModeOn() {
        return Secure.getInt(this.mPhone.getContext().getContentResolver(), "lte_mode_on", 1) == 1;
    }

    private int getNetworkType() {
        int result;
        int rilRadioTechnology = this.mPhone.getServiceState().getDataNetworkType();
        switch (rilRadioTechnology) {
            case 4:
            case 5:
            case 6:
            case 7:
                result = 3;
                break;
            case 13:
            case 14:
                result = 4;
                break;
            default:
                result = 0;
                break;
        }
        log("getNetworkType() result=" + result + ", rilRadioTechnology=" + rilRadioTechnology);
        return result;
    }

    protected void changePreferedNetworkByMobileData() {
        if (this.mPhone.getServiceState().getRoaming()) {
            log("changePreferedNetworkByMobileData: Shouldn't set preferred mode in roaming area ");
        } else if (!this.mUserDataEnabled) {
            log("onDataConnectionDbChanged > NETWORK_MODE_GSM_UMTS");
            this.mPhone.mCi.setPreferredNetworkType(3, null);
        } else if (getLTEModeOn()) {
            log("changePreferedNetworkByMobileData > NETWORK_MODE_GSM_WCDMA_LTE");
            this.mPhone.mCi.setPreferredNetworkType(9, null);
        } else {
            log("onDataConnectionDbChanged: Shouldn't set preferred mode because LTE mode is " + getLTEModeOn());
        }
    }

    protected boolean isSprintRoamingEnabled() {
        if (this.mPhone.getPhoneType() != 2) {
            boolean gsmRoamEnabled;
            if (Secure.getInt(this.mPhone.getContext().getContentResolver(), "sprint_gsm_data_roaming", 0) == 1) {
                gsmRoamEnabled = true;
            } else {
                gsmRoamEnabled = false;
            }
            if (!gsmRoamEnabled && this.mPhone.getServiceState().getRoaming()) {
                return false;
            }
        } else if (this.mPhone.IsDomesticRoaming()) {
            if (!(Secure.getInt(this.mPhone.getContext().getContentResolver(), "roam_setting_data_domestic", 0) == 1)) {
                return false;
            }
        } else if (this.mPhone.IsInternationalRoaming()) {
            if (!(Secure.getInt(this.mPhone.getContext().getContentResolver(), "roam_setting_data_international", 0) == 1)) {
                return false;
            }
        }
        return true;
    }

    private boolean isRoamingChanged() {
        String campMcc;
        String prevMcc = SystemProperties.get("persist.radio.prevmcc", "440");
        String campOperator = TelephonyManager.getDefault().getNetworkOperator(SubscriptionController.getInstance().getSubId(this.mPhone.getPhoneId())[0]);
        if (campOperator.length() >= 3) {
            campMcc = campOperator.substring(0, 3);
        } else {
            campMcc = prevMcc;
        }
        if (campMcc.equals("000") || prevMcc.equals(campMcc)) {
            return false;
        }
        SystemProperties.set("persist.radio.prevmcc", campMcc);
        return true;
    }

    public boolean isCdmaRat(int radioTech) {
        switch (radioTech) {
            case 4:
            case 5:
            case 6:
            case 7:
            case 8:
            case 12:
                return true;
            default:
                return false;
        }
    }

    private void restoreCarriersTable() {
        ContentResolver resolver = this.mPhone.getContext().getContentResolver();
        Uri RESTORE_URI = Uri.parse("content://telephony/carriers/restore");
        if (resolver != null && RESTORE_URI != null) {
            resolver.delete(RESTORE_URI, null, null);
        }
    }

    protected void changeCscUpdateStatus() {
        boolean isDisconnected;
        boolean z = true;
        State overallState = getOverallState();
        if (overallState == State.IDLE || overallState == State.FAILED) {
            isDisconnected = true;
        } else {
            isDisconnected = false;
        }
        createAllApnList();
        log("changeCscUpdateStatus: createAllApnList and cleanUpAllConnections");
        if (isDisconnected) {
            z = false;
        }
        cleanUpAllConnections(z, Phone.REASON_CSC_UPDATE_STATUS_CHANGED);
    }

    private void setRoamingPreferredApn() {
        if ("CG".equals("DGG") || "DCGG".equals("DGG") || "DCGS".equals("DGG") || "DCGGS".equals("DGG")) {
            TelephonyManager.getDefault();
            int cardType = Integer.parseInt(TelephonyManager.getTelephonyProperty("ril.ICC_TYPE", this.mPhone.getSubId(), "0"));
            TelephonyManager.getDefault();
            String simNumeric = TelephonyManager.getTelephonyProperty("gsm.sim.operator.numeric", this.mPhone.getSubId(), "0");
            log("setRoamingPreferredApn(), cardType=" + cardType + " ,simNumeric =" + simNumeric);
            if (cardType == 4 || cardType == 3) {
                String id;
                String apn = null;
                String preferApnNumeric = null;
                ContentResolver resolver = this.mPhone.getContext().getContentResolver();
                Cursor cursor = resolver.query(Uri.parse(PREFERAPN_NO_UPDATE_URI_USING_SUBID.toString() + this.mPhone.getSubId()), null, null, null, null);
                if (cursor != null) {
                    try {
                        if (cursor.moveToFirst()) {
                            id = cursor.getString(cursor.getColumnIndexOrThrow("_id"));
                            apn = cursor.getString(cursor.getColumnIndexOrThrow(Carriers.APN));
                            preferApnNumeric = cursor.getString(cursor.getColumnIndexOrThrow(Carriers.NUMERIC));
                            log("preferred apn is not null, id = " + id + " ,apn =" + apn + " ,preferApnNumeric =" + preferApnNumeric);
                        }
                        cursor.close();
                    } catch (Throwable th) {
                        cursor.close();
                    }
                }
                if ("ctnet".equalsIgnoreCase(apn) && simNumeric.equals(preferApnNumeric)) {
                    log("have set CTNET as default APN for this sim card,return");
                    return;
                }
                String where = new String("numeric = \"" + simNumeric + "\" and apn = \"" + "ctnet" + "\"");
                id = null;
                cursor = resolver.query(Uri.parse(TelephonyManager.appendId(CURRENT_URI.toString(), (long) this.mPhone.getPhoneId())), new String[]{"_id", "name", Carriers.APN}, where, null, null);
                log("cursor = " + (cursor != null ? Integer.valueOf(cursor.getCount()) : ":cursor null") + " , where : " + where);
                if (cursor != null) {
                    if (cursor.moveToFirst()) {
                        log("_id = " + cursor.getString(0) + ", name = " + cursor.getString(1) + ", apn = " + cursor.getString(2));
                        id = cursor.getString(0);
                    }
                    cursor.close();
                }
                ContentValues values = new ContentValues();
                if (id != null) {
                    values.put(APN_ID, id);
                    log("Update APN_ID, id = " + id);
                    resolver.update(Uri.parse(PREFERAPN_NO_UPDATE_URI_USING_SUBID.toString() + this.mPhone.getSubId()), values, null, null);
                }
            }
        }
    }

    protected void onUpdateIcc_4G() {
        if (this.mUiccController != null) {
            IccRecords newIccRecords;
            if (this.mPhone != null) {
                log("onUpdateIcc_4G(): phone type = " + this.mPhone.getPhoneType());
            }
            if (this.mPhone == null || this.mPhone.getPhoneType() != 2) {
                newIccRecords = this.mUiccController.getIccRecords(this.mPhone.getPhoneId(), 1);
            } else {
                if ((TelephonyManager.getDefault().getNetworkType(this.mPhone.getSubId()) == 13 || TelephonyManager.getDefault().getNetworkType(this.mPhone.getSubId()) == 14) && this.mPhone.getPhoneId() == 0) {
                    newIccRecords = this.mUiccController.getIccRecords(this.mPhone.getPhoneId(), 1);
                } else {
                    newIccRecords = this.mUiccController.getIccRecords(this.mPhone.getPhoneId(), 2);
                }
                if (newIccRecords == null) {
                    log(" No CSIM, Only USIM available");
                    newIccRecords = this.mUiccController.getIccRecords(this.mPhone.getPhoneId(), 1);
                }
            }
            IccRecords r = (IccRecords) this.mIccRecords.get();
            if (r != newIccRecords) {
                if (r != null) {
                    log("Removing stale icc objects.");
                    r.unregisterForRecordsLoaded(this);
                    this.mIccRecords.set(null);
                }
                if (newIccRecords != null) {
                    log("New records found(for 4G), newIccRecords = " + newIccRecords);
                    this.mIccRecords.set(newIccRecords);
                    if (this.mPhone instanceof GSMPhone) {
                        ((GSMPhone) this.mPhone).updateCurrentCarrierInProvider();
                    } else if (this.mPhone instanceof CDMALTEPhone) {
                        ((CDMALTEPhone) this.mPhone).updateCurrentCarrierInProvider();
                    }
                }
            }
        }
    }

    protected void onRatStateChanged() {
        boolean isDisconnected;
        boolean z = true;
        State overallState = getOverallState();
        if (overallState == State.IDLE || overallState == State.FAILED) {
            isDisconnected = true;
        } else {
            isDisconnected = false;
        }
        onUpdateIcc_4G();
        if (this.mPhone instanceof GSMPhone) {
            ((GSMPhone) this.mPhone).updateCurrentCarrierInProvider();
        } else if (this.mPhone instanceof CDMALTEPhone) {
            ((CDMALTEPhone) this.mPhone).updateCurrentCarrierInProvider();
        }
        createAllApnList();
        log("onRatStateChanged: cleanUpAllConnections");
        if (isDisconnected) {
            z = false;
        }
        cleanUpAllConnections(z, Phone.REASON_RAT_CHANGED);
    }

    protected void getInsertedSimCount() {
        int simCount = TelephonyManager.getDefault().getSimCount();
        int count = 0;
        for (int i = 0; i < simCount; i++) {
            if (!"0".equals(TelephonyManager.getTelephonyProperty("ril.ICC_TYPE", SubscriptionController.getInstance().getSubId(i)[0], "0"))) {
                count++;
            }
        }
        log("getInsertedSimCount() count:" + count);
        this.insertedSimCount = count;
    }
}
