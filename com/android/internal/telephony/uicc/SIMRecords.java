package com.android.internal.telephony.uicc;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.os.AsyncResult;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.os.SystemProperties;
import android.preference.PreferenceManager;
import android.sec.enterprise.EnterpriseDeviceManager;
import android.sec.enterprise.PhoneRestrictionPolicy;
import android.telephony.Rlog;
import android.telephony.SmsMessage;
import android.text.TextUtils;
import com.android.internal.telephony.CommandsInterface;
import com.android.internal.telephony.MccTable;
import com.android.internal.telephony.gsm.GSMPhone;
import com.android.internal.telephony.gsm.SimTlv;
import com.android.internal.telephony.uicc.IccCardApplicationStatus.AppState;
import com.android.internal.telephony.uicc.IccCardApplicationStatus.AppType;
import com.android.internal.telephony.uicc.IccRecords.IccRecordLoaded;
import com.android.internal.telephony.uicc.UsimServiceTable.SimService;
import com.android.internal.telephony.uicc.UsimServiceTable.UsimService;
import com.samsung.android.telephony.MultiSimManager;
import com.sec.android.app.CscFeature;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Locale;

public class SIMRecords extends IccRecords {
    static final int CFF_DATA_MASK = 240;
    static final int CFF_DATA_RESET = 15;
    static final int CFF_DATA_SHIFT = 4;
    static final int CFF_LINE1_MASK = 15;
    static final int CFF_LINE1_RESET = 240;
    static final int CFF_UNCONDITIONAL_ACTIVE = 10;
    static final int CFF_UNCONDITIONAL_DEACTIVE = 5;
    private static final int CFIS_ADN_CAPABILITY_ID_OFFSET = 14;
    private static final int CFIS_ADN_EXTENSION_ID_OFFSET = 15;
    private static final int CFIS_BCD_NUMBER_LENGTH_OFFSET = 2;
    private static final int CFIS_TON_NPI_OFFSET = 3;
    private static final int CPHS_SST_MBN_ENABLED = 48;
    private static final int CPHS_SST_MBN_MASK = 48;
    private static final boolean CRASH_RIL = false;
    protected static final boolean DBG = true;
    private static final int EVENT_APP_LOCKED = 41;
    protected static final int EVENT_GET_AD_DONE = 9;
    private static final int EVENT_GET_ALL_SMS_DONE = 18;
    private static final int EVENT_GET_CFF_DONE = 24;
    private static final int EVENT_GET_CFIS_DONE = 32;
    private static final int EVENT_GET_CPHS_MAILBOX_DONE = 11;
    private static final int EVENT_GET_CSP_CPHS_DONE = 33;
    private static final int EVENT_GET_EF_FPLMN_DONE = 60;
    private static final int EVENT_GET_GID1_DONE = 34;
    private static final int EVENT_GET_ICCID_DONE = 4;
    private static final int EVENT_GET_ICCID_WHEN_LOCKED_DONE = 42;
    private static final int EVENT_GET_IMSI_DONE = 3;
    private static final int EVENT_GET_IMSI_M_DONE = 44;
    private static final int EVENT_GET_IMSI_RETRY = 800;
    private static final int EVENT_GET_INFO_CPHS_DONE = 26;
    private static final int EVENT_GET_IRM_DONE = 45;
    private static final int EVENT_GET_MASTERIMSI_DONE = 47;
    private static final int EVENT_GET_MBDN_DONE = 6;
    private static final int EVENT_GET_MBI_DONE = 5;
    protected static final int EVENT_GET_MSISDN_DONE = 10;
    private static final int EVENT_GET_MWIS_DONE = 7;
    private static final int EVENT_GET_OPL_DONE = 37;
    private static final int EVENT_GET_PERSO_DONE = 46;
    private static final int EVENT_GET_PNN_DONE = 15;
    private static final int EVENT_GET_PSISMSC_DONE = 57;
    private static final int EVENT_GET_ROAMING_DONE = 51;
    private static final int EVENT_GET_SMSP_DONE = 61;
    private static final int EVENT_GET_SMS_DONE = 22;
    private static final int EVENT_GET_SPDI_DONE = 13;
    private static final int EVENT_GET_SPN_CPHS_DONE = 35;
    private static final int EVENT_GET_SPN_DONE = 12;
    private static final int EVENT_GET_SPN_SHORT_CPHS_DONE = 36;
    private static final int EVENT_GET_SPONIMSI1_DONE = 48;
    private static final int EVENT_GET_SPONIMSI2_DONE = 49;
    private static final int EVENT_GET_SPONIMSI3_DONE = 50;
    protected static final int EVENT_GET_SST_DONE = 17;
    private static final int EVENT_GET_UICCVER_DONE = 52;
    private static final int EVENT_GET_VOICE_MAIL_INDICATOR_CPHS_DONE = 8;
    private static final int EVENT_MARK_SMS_READ_DONE = 19;
    private static final int EVENT_PB_INIT_COMPLETE = 53;
    private static final int EVENT_SET_CPHS_MAILBOX_DONE = 25;
    private static final int EVENT_SET_CSP_DONE = 38;
    private static final int EVENT_SET_MBDN_DONE = 20;
    private static final int EVENT_SIM_PB_READY = 54;
    private static final int EVENT_SIM_REFRESH = 31;
    private static final int EVENT_SMS_ON_SIM = 21;
    private static final int EVENT_UPDATE_DONE = 14;
    private static final String ICCID2_PATH = "/data/misc/radio/cicd2";
    private static final String ICCID_PATH = "/data/misc/radio/cicd";
    public static int INIT_AUTOPRECONFIG = 3;
    static final String KEY_GID1 = "key_gid1";
    public static final String KEY_ICCID = "key_iccid";
    static final String KEY_PAYSTATE = "key_paystate";
    protected static final String LOG_TAG = "SIMRecords";
    private static final String[] MCCMNC_CODES_HAVING_3DIGITS_MNC = new String[]{"302370", "302720", "310260", "405025", "405026", "405027", "405028", "405029", "405030", "405031", "405032", "405033", "405034", "405035", "405036", "405037", "405038", "405039", "405040", "405041", "405042", "405043", "405044", "405045", "405046", "405047", "405750", "405751", "405752", "405753", "405754", "405755", "405756", "405799", "405800", "405801", "405802", "405803", "405804", "405805", "405806", "405807", "405808", "405809", "405810", "405811", "405812", "405813", "405814", "405815", "405816", "405817", "405818", "405819", "405820", "405821", "405822", "405823", "405824", "405825", "405826", "405827", "405828", "405829", "405830", "405831", "405832", "405833", "405834", "405835", "405836", "405837", "405838", "405839", "405840", "405841", "405842", "405843", "405844", "405845", "405846", "405847", "405848", "405849", "405850", "405851", "405852", "405853", "405875", "405876", "405877", "405878", "405879", "405880", "405881", "405882", "405883", "405884", "405885", "405886", "405908", "405909", "405910", "405911", "405912", "405913", "405914", "405915", "405916", "405917", "405918", "405919", "405920", "405921", "405922", "405923", "405924", "405925", "405926", "405927", "405928", "405929", "405930", "405931", "405932", "502142", "502143", "502145", "502146", "502147", "502148"};
    private static String PROPERTY_CDMA_HOME_OPERATOR_NUMERIC = "ro.cdma.home.operator.numeric";
    static final String PROPERTY_SIM_ROAMING = "gsm.sim.roaming";
    static final String PROPERTY_UICC_VERSION = "gsm.sim.version";
    static final boolean SHIP_BUILD = "true".equals(SystemProperties.get("ro.product_ship", "false"));
    static final int TAG_FULL_NETWORK_NAME = 67;
    static final int TAG_SHORT_NETWORK_NAME = 69;
    static final int TAG_SPDI = 163;
    static final int TAG_SPDI_PLMN_LIST = 128;
    public static final String propNameChangedICC = "ril.isIccChanged";
    private final String ACTION_SIM_ICCID_CHANGED;
    private final String ACTION_SIM_REFRESH_INIT;
    boolean IsOnsExist;
    boolean NV_cfflag_video;
    boolean NV_cfflag_voice;
    int[] OPL_INDEX;
    int[] OPL_LAC1;
    int[] OPL_LAC2;
    String[] OPL_MCCMNC;
    int OPL_count;
    String[] PNN_Value;
    private String countryISO;
    boolean isAvailableCFIS;
    boolean isAvailableCHV1;
    public boolean isAvailableFDN;
    boolean isAvailableMBDN;
    boolean isAvailableMSISDN;
    boolean isAvailableMWIS;
    boolean isAvailableO2PERSO;
    public boolean isAvailableOCSGL;
    public boolean isAvailableOCSGLList;
    public boolean isAvailableSMS;
    public boolean isAvailableSMSP;
    boolean isAvailableSPN;
    boolean isEnabledCSP;
    boolean isRefreshedBySTK;
    Messenger mAutoPreconfigService;
    private ServiceConnection mAutoPreconfigServiceConnection;
    private boolean mCallForwardingEnabled;
    private byte[] mCphsInfo;
    boolean mCspPlmnEnabled;
    byte[] mEfCPHS_MWI;
    byte[] mEfCff;
    byte[] mEfCfis;
    byte[] mEfLi;
    byte[] mEfMWIS;
    byte[] mEfPl;
    private boolean mEonsEnabled;
    String mEonsName;
    boolean mHasIsim;
    boolean mImsiRequest;
    private boolean mIsAPBound;
    boolean mIsEnabledOPL;
    boolean mIsOPLExist;
    String mOldICCID;
    String mPnnHomeName;
    private final BroadcastReceiver mReceiver;
    private int mRetryCountGetImsi;
    private String mSktImsiM;
    private String mSktIrm;
    ArrayList<String> mSpdiNetworks;
    int mSpnDisplayCondition;
    SpnOverride mSpnOverride;
    private GetSpnFsmState mSpnState;
    private String[] mSponImsi;
    UsimServiceTable mUsimServiceTable;
    private int mValidityPeriod;
    VoiceMailConstants mVmConfig;
    private byte[] perso;
    private String selectedNwkName;
    int spnDisplayRuleOverride;
    String spnOverride;
    String spn_cphs;
    boolean videocallForwardingEnabled;
    boolean voicecallForwardingEnabled;

    class C01301 implements ServiceConnection {
        C01301() {
        }

        public void onServiceConnected(ComponentName className, IBinder service) {
            SIMRecords.this.log("onServiceConnected() : AutoPreconfigService");
            if (service != null) {
                SIMRecords.this.mAutoPreconfigService = new Messenger(service);
                SIMRecords.this.InitAutopreconfig();
            }
        }

        public void onServiceDisconnected(ComponentName className) {
            SIMRecords.this.log("onServiceDisconnected() : AutoPreconfigService");
            SIMRecords.this.mIsAPBound = false;
        }
    }

    static /* synthetic */ class C01312 {
        static final /* synthetic */ int[] f16xdc363d50 = new int[GetSpnFsmState.values().length];

        static {
            try {
                f16xdc363d50[GetSpnFsmState.INIT.ordinal()] = 1;
            } catch (NoSuchFieldError e) {
            }
            try {
                f16xdc363d50[GetSpnFsmState.READ_SPN_3GPP.ordinal()] = 2;
            } catch (NoSuchFieldError e2) {
            }
            try {
                f16xdc363d50[GetSpnFsmState.READ_SPN_CPHS.ordinal()] = 3;
            } catch (NoSuchFieldError e3) {
            }
            try {
                f16xdc363d50[GetSpnFsmState.READ_SPN_SHORT_CPHS.ordinal()] = 4;
            } catch (NoSuchFieldError e4) {
            }
        }
    }

    private class EfOcsglLoaded implements IccRecordLoaded {
        private EfOcsglLoaded() {
        }

        public String getEfName() {
            return "EF_OCSGL";
        }

        public void onRecordLoaded(AsyncResult ar) {
            ArrayList<byte[]> ocsglList = ar.result;
            int csgIdCount = 0;
            for (int i = 0; i < ocsglList.size(); i++) {
                byte[] data = (byte[]) ocsglList.get(i);
                if ((data[2] & 255) == 128) {
                    int skipLen;
                    if ((data[3] & 255) == 129) {
                        skipLen = ((data[4] & 255) + 128) + 3;
                    } else {
                        skipLen = (data[3] & 255) + 2;
                    }
                    if ((data[skipLen + 2] & 255) == 129 && (data[(skipLen + 2) + 1] & 255) > 2) {
                        csgIdCount++;
                    }
                }
            }
            if (csgIdCount > 0) {
                SIMRecords.this.isAvailableOCSGLList = true;
            } else {
                SIMRecords.this.isAvailableOCSGLList = false;
            }
            SIMRecords.this.log("EF_OCSGL record count: " + csgIdCount + "/" + ocsglList.size());
        }
    }

    private class EfPlLoaded implements IccRecordLoaded {
        private EfPlLoaded() {
        }

        public String getEfName() {
            return "EF_PL";
        }

        public void onRecordLoaded(AsyncResult ar) {
            SIMRecords.this.mEfPl = (byte[]) ar.result;
            SIMRecords.this.log("EF_PL=" + IccUtils.bytesToHexString(SIMRecords.this.mEfPl));
        }
    }

    private class EfUsimLiLoaded implements IccRecordLoaded {
        private EfUsimLiLoaded() {
        }

        public String getEfName() {
            return "EF_LI";
        }

        public void onRecordLoaded(AsyncResult ar) {
            SIMRecords.this.mEfLi = (byte[]) ar.result;
            SIMRecords.this.log("EF_LI=" + IccUtils.bytesToHexString(SIMRecords.this.mEfLi));
        }
    }

    private enum GetSpnFsmState {
        IDLE,
        INIT,
        READ_SPN_3GPP,
        READ_SPN_CPHS,
        READ_SPN_SHORT_CPHS
    }

    public enum O2Paystate {
        NOT_READY(0),
        O2_PrePay(1),
        O2_PostPay(2);
        
        private int mValue;

        private O2Paystate(int value) {
            this.mValue = value;
        }

        public int value() {
            return this.mValue;
        }
    }

    private class SIMRecordsBroadcastReceiver extends BroadcastReceiver {
        private SIMRecordsBroadcastReceiver() {
        }

        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if ("com.samsung.intent.action.setCardDataInit".equals(action)) {
                SIMRecords.this.log("com.samsung.intent.action.setCardDataInit");
                if (!"DCGG".equals("DGG") && !"DCGGS".equals("DGG") && !"CG".equals("DGG")) {
                    SystemProperties.set("gsm.sim.selectnetwork", "GSM");
                    SIMRecords.this.setCardDataInit();
                } else if (SIMRecords.this.phone.getPhoneId() == 0) {
                    SystemProperties.set("gsm.sim.selectnetwork", "GSM");
                    SIMRecords.this.setCardDataInit();
                }
            } else if ("com.samsung.intent.action.Slot2SwitchCompleted".equals(action)) {
                if ("DCGS".equals("DGG")) {
                    SIMRecords.this.log("com.samsung.intent.action.Slot2SwitchCompleted");
                    SIMRecords.this.setCardDataInit();
                }
            } else if ("com.samsung.intent.action.Slot2OnCompleted".equals(action)) {
                if ("DCGS".equals("DGG")) {
                    SIMRecords.this.log("com.samsung.intent.action.Slot2OnCompleted");
                    SIMRecords.this.setCardDataInit();
                }
            } else if ("android.intent.action.CSC_UPDATE_NETWORK_DONE".equals(action)) {
                SIMRecords.this.log("[Voicemail] receive android.intent.action.CSC_UPDATE_NETWORK_DONE");
                String mNetworkName = intent.getStringExtra("networkName");
                if (mNetworkName == null || mNetworkName.isEmpty()) {
                    SIMRecords.this.log("[Voicemail] Voicemail number can not be set because there is no matched networkName!");
                } else if (!SIMRecords.this.isAvailableVoiceMailInSIM()) {
                    SIMRecords.this.log("[Voicemail] Voicemail number can not be set what matched with " + mNetworkName);
                    SIMRecords.this.setVoiceMailByCountry(mNetworkName);
                    SIMRecords.this.selectedNwkName = mNetworkName;
                }
            } else if ("android.intent.action.CSC_UPDATE_VOICEMAIL_DONE".equals(action)) {
                SIMRecords.this.log("[Voicemail] receive android.intent.action.CSC_UPDATE_VOICEMAIL_DONE");
                if (SIMRecords.this.mVmConfig != null) {
                    SIMRecords.this.mVmConfig = new VoiceMailConstants();
                    SIMRecords.this.log("[Voicemail] Reload voicemail-conf.xml as it could be changed.");
                }
                if (!SIMRecords.this.isAvailableVoiceMailInSIM() && SIMRecords.this.selectedNwkName != null) {
                    SIMRecords.this.setVoiceMailByCountry(SIMRecords.this.selectedNwkName);
                }
            }
        }
    }

    public void handleMessage(android.os.Message r55) {
        /* JADX: method processing error */
/*
Error: jadx.core.utils.exceptions.JadxRuntimeException: Can't find block by offset: 0x0040 in list [B:16:0x0061]
	at jadx.core.utils.BlockUtils.getBlockByOffset(BlockUtils.java:42)
	at jadx.core.dex.instructions.IfNode.initBlocks(IfNode.java:60)
	at jadx.core.dex.visitors.blocksmaker.BlockFinish.initBlocksInIfNodes(BlockFinish.java:48)
	at jadx.core.dex.visitors.blocksmaker.BlockFinish.visit(BlockFinish.java:33)
	at jadx.core.dex.visitors.DepthTraversal.visit(DepthTraversal.java:31)
	at jadx.core.dex.visitors.DepthTraversal.visit(DepthTraversal.java:17)
	at jadx.core.ProcessClass.process(ProcessClass.java:37)
	at jadx.core.ProcessClass.processDependencies(ProcessClass.java:59)
	at jadx.core.ProcessClass.process(ProcessClass.java:42)
	at jadx.api.JadxDecompiler.processClass(JadxDecompiler.java:306)
	at jadx.api.JavaClass.decompile(JavaClass.java:62)
	at jadx.api.JadxDecompiler$1.run(JadxDecompiler.java:199)
*/
        /*
        r54 = this;
        r31 = 0;
        r0 = r54;
        r4 = r0.mDestroyed;
        r4 = r4.get();
        if (r4 == 0) goto L_0x0041;
    L_0x000c:
        r4 = new java.lang.StringBuilder;
        r4.<init>();
        r6 = "Received message ";
        r4 = r4.append(r6);
        r0 = r55;
        r4 = r4.append(r0);
        r6 = "[";
        r4 = r4.append(r6);
        r0 = r55;
        r6 = r0.what;
        r4 = r4.append(r6);
        r6 = "] ";
        r4 = r4.append(r6);
        r6 = " while being destroyed. Ignoring.";
        r4 = r4.append(r6);
        r4 = r4.toString();
        r0 = r54;
        r0.loge(r4);
    L_0x0040:
        return;
    L_0x0041:
        r0 = r55;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r0.what;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        switch(r4) {
            case 1: goto L_0x0051;
            case 3: goto L_0x0070;
            case 4: goto L_0x07ed;
            case 5: goto L_0x0469;
            case 6: goto L_0x051b;
            case 7: goto L_0x0668;
            case 8: goto L_0x0740;
            case 9: goto L_0x09f1;
            case 10: goto L_0x0615;
            case 11: goto L_0x051b;
            case 12: goto L_0x13f8;
            case 13: goto L_0x1518;
            case 14: goto L_0x1536;
            case 15: goto L_0x154b;
            case 17: goto L_0x1641;
            case 18: goto L_0x1562;
            case 19: goto L_0x1579;
            case 20: goto L_0x176b;
            case 21: goto L_0x1597;
            case 22: goto L_0x160c;
            case 24: goto L_0x1408;
            case 25: goto L_0x1839;
            case 26: goto L_0x1733;
            case 30: goto L_0x0647;
            case 31: goto L_0x1890;
            case 32: goto L_0x18c1;
            case 33: goto L_0x19d3;
            case 34: goto L_0x1a8f;
            case 35: goto L_0x1bf5;
            case 36: goto L_0x1c82;
            case 37: goto L_0x1cf2;
            case 41: goto L_0x0065;
            case 42: goto L_0x0887;
            case 44: goto L_0x1a29;
            case 45: goto L_0x1a5c;
            case 46: goto L_0x1d10;
            case 47: goto L_0x1d5c;
            case 48: goto L_0x1db0;
            case 49: goto L_0x1e69;
            case 50: goto L_0x1f22;
            case 51: goto L_0x1fdb;
            case 52: goto L_0x2020;
            case 53: goto L_0x2151;
            case 54: goto L_0x2156;
            case 57: goto L_0x20bc;
            case 60: goto L_0x2146;
            case 61: goto L_0x1b77;
            case 800: goto L_0x1b55;
            default: goto L_0x0048;
        };	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x0048:
        super.handleMessage(r55);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x004b:
        if (r31 == 0) goto L_0x0040;
    L_0x004d:
        r54.onRecordLoaded();
        goto L_0x0040;
    L_0x0051:
        r54.onReady();	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        goto L_0x004b;
    L_0x0055:
        r22 = move-exception;
        r4 = "Exception parsing SIM record";	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r1 = r22;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0.logw(r4, r1);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        if (r31 == 0) goto L_0x0040;
    L_0x0061:
        r54.onRecordLoaded();
        goto L_0x0040;
    L_0x0065:
        r54.onLocked();	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        goto L_0x004b;
    L_0x0069:
        r4 = move-exception;
        if (r31 == 0) goto L_0x006f;
    L_0x006c:
        r54.onRecordLoaded();
    L_0x006f:
        throw r4;
    L_0x0070:
        r31 = 1;
        r4 = "EVENT_GET_IMSI_DONE";	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0.loge(r4);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r55;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r12 = r0.obj;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r12 = (android.os.AsyncResult) r12;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r12.exception;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        if (r4 == 0) goto L_0x00d0;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x0083:
        r4 = r12.exception;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = (com.android.internal.telephony.CommandException) r4;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = (com.android.internal.telephony.CommandException) r4;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r20 = r4.getCommandError();	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = com.android.internal.telephony.CommandException.Error.GENERIC_FAILURE;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r20;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        if (r0 != r4) goto L_0x00b4;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x0093:
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r0.mRetryCountGetImsi;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = 20;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        if (r4 >= r6) goto L_0x00b4;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x009b:
        r4 = 800; // 0x320 float:1.121E-42 double:3.953E-321;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r0.obtainMessage(r4);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = 500; // 0x1f4 float:7.0E-43 double:2.47E-321;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0.sendMessageDelayed(r4, r6);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r0.mRetryCountGetImsi;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4 + 1;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0.mRetryCountGetImsi = r4;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x00b4:
        r4 = new java.lang.StringBuilder;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4.<init>();	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = "Exception querying IMSI, Exception:";	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.append(r6);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = r12.exception;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.append(r6);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.toString();	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0.loge(r4);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        goto L_0x004b;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x00d0:
        r4 = r12.result;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = (java.lang.String) r4;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0.mImsi = r4;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r0.mImsi;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        if (r4 == 0) goto L_0x0116;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x00de:
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r0.mImsi;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.length();	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = 6;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        if (r4 < r6) goto L_0x00f5;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x00e9:
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r0.mImsi;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.length();	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = 15;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        if (r4 <= r6) goto L_0x0116;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x00f5:
        r4 = new java.lang.StringBuilder;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4.<init>();	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = "invalid IMSI ";	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.append(r6);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = r0.mImsi;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.append(r6);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.toString();	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0.loge(r4);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = 0;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0.mImsi = r4;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x0116:
        r4 = new java.lang.StringBuilder;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4.<init>();	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = "IMSI: mMncLength=";	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.append(r6);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = r0.mMncLength;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.append(r6);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.toString();	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0.log(r4);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = "IMSI: xxxxxxx";	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0.log(r4);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r0.mMncLength;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        if (r4 == 0) goto L_0x0146;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x013f:
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r0.mMncLength;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = 2;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        if (r4 != r6) goto L_0x0197;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x0146:
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r0.mImsi;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        if (r4 == 0) goto L_0x0197;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x014c:
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r0.mImsi;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.length();	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = 6;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        if (r4 < r6) goto L_0x0197;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x0157:
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r0.mImsi;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = 0;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r7 = 6;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r38 = r4.substring(r6, r7);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r13 = MCCMNC_CODES_HAVING_3DIGITS_MNC;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r13.length;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r34 = r0;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r25 = 0;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x0168:
        r0 = r25;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r1 = r34;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        if (r0 >= r1) goto L_0x0197;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x016e:
        r37 = r13[r25];	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r37.equals(r38);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        if (r4 == 0) goto L_0x0323;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x0176:
        r4 = 3;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0.mMncLength = r4;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = new java.lang.StringBuilder;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4.<init>();	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = "IMSI: setting1 mMncLength=";	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.append(r6);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = r0.mMncLength;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.append(r6);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.toString();	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0.log(r4);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x0197:
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r0.mMncLength;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        if (r4 != 0) goto L_0x01d5;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x019d:
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r0.mImsi;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        if (r4 == 0) goto L_0x01d5;
    L_0x01a3:
        r0 = r54;	 Catch:{ NumberFormatException -> 0x0327 }
        r4 = r0.mImsi;	 Catch:{ NumberFormatException -> 0x0327 }
        r6 = 0;	 Catch:{ NumberFormatException -> 0x0327 }
        r7 = 3;	 Catch:{ NumberFormatException -> 0x0327 }
        r4 = r4.substring(r6, r7);	 Catch:{ NumberFormatException -> 0x0327 }
        r36 = java.lang.Integer.parseInt(r4);	 Catch:{ NumberFormatException -> 0x0327 }
        r4 = com.android.internal.telephony.MccTable.smallestDigitsMccForMnc(r36);	 Catch:{ NumberFormatException -> 0x0327 }
        r0 = r54;	 Catch:{ NumberFormatException -> 0x0327 }
        r0.mMncLength = r4;	 Catch:{ NumberFormatException -> 0x0327 }
        r4 = new java.lang.StringBuilder;	 Catch:{ NumberFormatException -> 0x0327 }
        r4.<init>();	 Catch:{ NumberFormatException -> 0x0327 }
        r6 = "setting2 mMncLength=";	 Catch:{ NumberFormatException -> 0x0327 }
        r4 = r4.append(r6);	 Catch:{ NumberFormatException -> 0x0327 }
        r0 = r54;	 Catch:{ NumberFormatException -> 0x0327 }
        r6 = r0.mMncLength;	 Catch:{ NumberFormatException -> 0x0327 }
        r4 = r4.append(r6);	 Catch:{ NumberFormatException -> 0x0327 }
        r4 = r4.toString();	 Catch:{ NumberFormatException -> 0x0327 }
        r0 = r54;	 Catch:{ NumberFormatException -> 0x0327 }
        r0.log(r4);	 Catch:{ NumberFormatException -> 0x0327 }
    L_0x01d5:
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r0.mMncLength;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        if (r4 == 0) goto L_0x0220;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x01db:
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r0.mMncLength;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = -1;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        if (r4 == r6) goto L_0x0220;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x01e2:
        r4 = new java.lang.StringBuilder;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4.<init>();	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = "update mccmnc=";	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.append(r6);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = r0.mImsi;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r7 = 0;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r8 = r0.mMncLength;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r8 = r8 + 3;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = r6.substring(r7, r8);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.append(r6);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.toString();	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0.log(r4);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r0.mContext;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = r0.mImsi;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r7 = 0;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r8 = r0.mMncLength;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r8 = r8 + 3;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = r6.substring(r7, r8);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r7 = 0;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        com.android.internal.telephony.MccTable.updateMccMncConfiguration(r4, r6, r7);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x0220:
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r0.mImsiReadyRegistrants;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4.notifyRegistrants();	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r0.mImsi;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        if (r4 == 0) goto L_0x0276;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x022d:
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r0.mContext;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r48 = android.preference.PreferenceManager.getDefaultSharedPreferences(r4);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = "cf_imsikey";	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = 0;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r48;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r11 = r0.getString(r4, r6);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r19 = r48.edit();	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = "cf_imsikey";	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = r0.mImsi;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r19;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0.putString(r4, r6);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r19.commit();	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        if (r11 == 0) goto L_0x0370;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x0252:
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r0.mImsi;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.equals(r11);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        if (r4 == 0) goto L_0x034b;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x025c:
        r4 = "cf_iconkey_voice";	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = 0;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r48;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r0.getBoolean(r4, r6);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0.NV_cfflag_voice = r4;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = "cf_iconkey_video";	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = 0;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r48;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r0.getBoolean(r4, r6);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0.NV_cfflag_video = r4;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x0276:
        r4 = "DCGS";	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = "DGG";	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.equals(r6);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        if (r4 != 0) goto L_0x028a;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x0280:
        r4 = "DCGGS";	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = "DGG";	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.equals(r6);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        if (r4 == 0) goto L_0x03a4;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x028a:
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r0.mMncLength;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        if (r4 == 0) goto L_0x0297;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x0290:
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r0.mMncLength;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = -1;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        if (r4 != r6) goto L_0x02b3;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x0297:
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r0.mImsi;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        if (r4 == 0) goto L_0x02b3;
    L_0x029d:
        r0 = r54;	 Catch:{ NumberFormatException -> 0x0395 }
        r4 = r0.mImsi;	 Catch:{ NumberFormatException -> 0x0395 }
        r6 = 0;	 Catch:{ NumberFormatException -> 0x0395 }
        r7 = 3;	 Catch:{ NumberFormatException -> 0x0395 }
        r4 = r4.substring(r6, r7);	 Catch:{ NumberFormatException -> 0x0395 }
        r36 = java.lang.Integer.parseInt(r4);	 Catch:{ NumberFormatException -> 0x0395 }
        r4 = com.android.internal.telephony.MccTable.smallestDigitsMccForMnc(r36);	 Catch:{ NumberFormatException -> 0x0395 }
        r0 = r54;	 Catch:{ NumberFormatException -> 0x0395 }
        r0.mMncLength = r4;	 Catch:{ NumberFormatException -> 0x0395 }
    L_0x02b3:
        r4 = new java.lang.StringBuilder;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4.<init>();	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = "zqg test mMncLength: ";	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.append(r6);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = r0.mMncLength;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.append(r6);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.toString();	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0.log(r4);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r40 = r54.getOperatorNumeric();	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = new java.lang.StringBuilder;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4.<init>();	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = "zqg test set phone.getPhoneName() = ";	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.append(r6);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = r0.phone;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = r6.getPhoneName();	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.append(r6);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.toString();	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0.log(r4);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r0.phone;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.getPhoneId();	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = 1;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        if (r4 != r6) goto L_0x004b;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x02fe:
        r4 = new java.lang.StringBuilder;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4.<init>();	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = "zqg test set PROPERTY_ICC_OPERATOR_NUMERIC to";	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.append(r6);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r40;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.append(r0);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.toString();	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0.log(r4);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = "gsm.sim.operator.numeric";	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r1 = r40;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0.setSystemProperty(r4, r1);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        goto L_0x004b;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x0323:
        r25 = r25 + 1;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        goto L_0x0168;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x0327:
        r18 = move-exception;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = 0;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0.mMncLength = r4;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = new java.lang.StringBuilder;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4.<init>();	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = "Corrupt IMSI! setting3 mMncLength=";	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.append(r6);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = r0.mMncLength;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.append(r6);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.toString();	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0.loge(r4);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        goto L_0x01d5;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x034b:
        r4 = 0;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0.NV_cfflag_voice = r4;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = 0;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0.NV_cfflag_video = r4;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = "cf_iconkey_voice";	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = r0.NV_cfflag_voice;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r19;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0.putBoolean(r4, r6);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = "cf_iconkey_video";	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = r0.NV_cfflag_video;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r19;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0.putBoolean(r4, r6);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r19.commit();	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        goto L_0x0276;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x0370:
        r4 = 0;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0.NV_cfflag_voice = r4;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = 0;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0.NV_cfflag_video = r4;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = "cf_iconkey_voice";	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = r0.NV_cfflag_voice;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r19;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0.putBoolean(r4, r6);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = "cf_iconkey_video";	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = r0.NV_cfflag_video;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r19;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0.putBoolean(r4, r6);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r19.commit();	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        goto L_0x0276;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x0395:
        r18 = move-exception;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = 0;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0.mMncLength = r4;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = "Corrupt IMSI!";	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0.loge(r4);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        goto L_0x02b3;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x03a4:
        r4 = "CG";	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = "DGG";	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.equals(r6);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        if (r4 != 0) goto L_0x03c2;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x03ae:
        r4 = "DCG";	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = "DGG";	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.equals(r6);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        if (r4 != 0) goto L_0x03c2;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x03b8:
        r4 = "DCGG";	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = "DGG";	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.equals(r6);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        if (r4 == 0) goto L_0x004b;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x03c2:
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r0.mMncLength;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        if (r4 == 0) goto L_0x03cf;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x03c8:
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r0.mMncLength;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = -1;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        if (r4 != r6) goto L_0x03eb;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x03cf:
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r0.mImsi;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        if (r4 == 0) goto L_0x03eb;
    L_0x03d5:
        r0 = r54;	 Catch:{ NumberFormatException -> 0x045b }
        r4 = r0.mImsi;	 Catch:{ NumberFormatException -> 0x045b }
        r6 = 0;	 Catch:{ NumberFormatException -> 0x045b }
        r7 = 3;	 Catch:{ NumberFormatException -> 0x045b }
        r4 = r4.substring(r6, r7);	 Catch:{ NumberFormatException -> 0x045b }
        r36 = java.lang.Integer.parseInt(r4);	 Catch:{ NumberFormatException -> 0x045b }
        r4 = com.android.internal.telephony.MccTable.smallestDigitsMccForMnc(r36);	 Catch:{ NumberFormatException -> 0x045b }
        r0 = r54;	 Catch:{ NumberFormatException -> 0x045b }
        r0.mMncLength = r4;	 Catch:{ NumberFormatException -> 0x045b }
    L_0x03eb:
        r4 = new java.lang.StringBuilder;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4.<init>();	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = "zqg test mMncLength: ";	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.append(r6);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = r0.mMncLength;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.append(r6);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.toString();	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0.log(r4);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r40 = r54.getOperatorNumeric();	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = new java.lang.StringBuilder;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4.<init>();	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = "zqg test set phone.getPhoneName() = ";	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.append(r6);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = r0.phone;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = r6.getPhoneName();	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.append(r6);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.toString();	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0.log(r4);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r0.phone;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.getPhoneId();	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = 1;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        if (r4 != r6) goto L_0x004b;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x0436:
        r4 = new java.lang.StringBuilder;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4.<init>();	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = "zqg test set PROPERTY_ICC_OPERATOR_NUMERIC to";	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.append(r6);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r40;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.append(r0);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.toString();	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0.log(r4);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = "gsm.sim.operator.numeric";	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r1 = r40;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0.setSystemProperty(r4, r1);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        goto L_0x004b;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x045b:
        r18 = move-exception;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = 0;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0.mMncLength = r4;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = "Corrupt IMSI!";	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0.loge(r4);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        goto L_0x03eb;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x0469:
        r31 = 1;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r55;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r12 = r0.obj;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r12 = (android.os.AsyncResult) r12;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r12.result;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = (byte[]) r4;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r4;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = (byte[]) r0;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r17 = r0;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r32 = 0;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r12.exception;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        if (r4 != 0) goto L_0x04e5;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x0480:
        r4 = new java.lang.StringBuilder;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4.<init>();	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = "EF_MBI: ";	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.append(r6);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = com.android.internal.telephony.uicc.IccUtils.bytesToHexString(r17);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.append(r6);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.toString();	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0.log(r4);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = 0;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r17[r4];	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4 & 255;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0.mMailboxIndex = r4;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r0.mMailboxIndex;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        if (r4 == 0) goto L_0x04bc;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x04ab:
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r0.mMailboxIndex;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = 255; // 0xff float:3.57E-43 double:1.26E-321;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        if (r4 == r6) goto L_0x04bc;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x04b3:
        r4 = "Got valid mailbox number for MBDN";	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0.log(r4);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r32 = 1;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x04bc:
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r0.mRecordsToLoad;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4 + 1;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0.mRecordsToLoad = r4;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        if (r32 == 0) goto L_0x04fb;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x04c8:
        r4 = new com.android.internal.telephony.uicc.AdnRecordLoader;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = r0.mFh;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4.<init>(r6);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = 28615; // 0x6fc7 float:4.0098E-41 double:1.41377E-319;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r7 = 28616; // 0x6fc8 float:4.01E-41 double:1.4138E-319;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r8 = r0.mMailboxIndex;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r9 = 6;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r9 = r0.obtainMessage(r9);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4.loadFromEF(r6, r7, r8, r9);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        goto L_0x004b;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x04e5:
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r0.isAvailableMBDN;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = 1;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        if (r4 != r6) goto L_0x04bc;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x04ec:
        r4 = "EF MBI doens't exist. read EF MBDN with default record ID 1";	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0.log(r4);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r32 = 1;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = 1;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0.mMailboxIndex = r4;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        goto L_0x04bc;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x04fb:
        r4 = 0;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0.isAvailableMBDN = r4;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = new com.android.internal.telephony.uicc.AdnRecordLoader;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = r0.mFh;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4.<init>(r6);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = 28439; // 0x6f17 float:3.9852E-41 double:1.40507E-319;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r7 = 28490; // 0x6f4a float:3.9923E-41 double:1.4076E-319;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r8 = 1;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r9 = 11;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r9 = r0.obtainMessage(r9);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4.loadFromEF(r6, r7, r8, r9);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        goto L_0x004b;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x051b:
        r4 = 0;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0.mVoiceMailNum = r4;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = 0;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0.mVoiceMailTag = r4;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r31 = 1;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r55;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r12 = r0.obj;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r12 = (android.os.AsyncResult) r12;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r12.exception;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        if (r4 == 0) goto L_0x0587;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x0531:
        r4 = new java.lang.StringBuilder;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4.<init>();	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = "Invalid or missing EF";	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = r4.append(r6);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r55;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r0.what;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r7 = 11;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        if (r4 != r7) goto L_0x0584;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x0544:
        r4 = "[MAILBOX]";	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x0546:
        r4 = r6.append(r4);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.toString();	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0.log(r4);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r55;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r0.what;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = 6;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        if (r4 != r6) goto L_0x004b;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x055a:
        r4 = 0;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0.isAvailableMBDN = r4;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r0.mRecordsToLoad;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4 + 1;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0.mRecordsToLoad = r4;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = new com.android.internal.telephony.uicc.AdnRecordLoader;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = r0.mFh;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4.<init>(r6);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = 28439; // 0x6f17 float:3.9852E-41 double:1.40507E-319;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r7 = 28490; // 0x6f4a float:3.9923E-41 double:1.4076E-319;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r8 = 1;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r9 = 11;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r9 = r0.obtainMessage(r9);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4.loadFromEF(r6, r7, r8, r9);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        goto L_0x004b;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x0584:
        r4 = "[MBDN]";	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        goto L_0x0546;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x0587:
        r5 = r12.result;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r5 = (com.android.internal.telephony.uicc.AdnRecord) r5;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = new java.lang.StringBuilder;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4.<init>();	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = "VM: ";	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.append(r6);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = r4.append(r5);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r55;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r0.what;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r7 = 11;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        if (r4 != r7) goto L_0x05e8;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x05a2:
        r4 = " EF[MAILBOX]";	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x05a4:
        r4 = r6.append(r4);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.toString();	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0.log(r4);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r5.isEmpty();	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        if (r4 == 0) goto L_0x05eb;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x05b7:
        r0 = r55;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r0.what;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = 6;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        if (r4 != r6) goto L_0x05eb;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x05be:
        r4 = 0;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0.isAvailableMBDN = r4;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r0.mRecordsToLoad;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4 + 1;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0.mRecordsToLoad = r4;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = new com.android.internal.telephony.uicc.AdnRecordLoader;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = r0.mFh;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4.<init>(r6);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = 28439; // 0x6f17 float:3.9852E-41 double:1.40507E-319;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r7 = 28490; // 0x6f4a float:3.9923E-41 double:1.4076E-319;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r8 = 1;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r9 = 11;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r9 = r0.obtainMessage(r9);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4.loadFromEF(r6, r7, r8, r9);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        goto L_0x004b;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x05e8:
        r4 = " EF[MBDN]";	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        goto L_0x05a4;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x05eb:
        r4 = com.sec.android.app.CscFeature.getInstance();	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = "CscFeature_RIL_DisableEditingVMNumber";	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.getEnableStatus(r6);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        if (r4 == 0) goto L_0x0603;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x05f7:
        r4 = "SIMRecords";	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = "Voicemail number is fixed";	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        android.telephony.Rlog.d(r4, r6);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = 1;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0.mIsVoiceMailFixed = r4;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x0603:
        r4 = r5.getNumber();	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0.mVoiceMailNum = r4;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r5.getAlphaTag();	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0.mVoiceMailTag = r4;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        goto L_0x004b;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x0615:
        r31 = 1;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r55;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r12 = r0.obj;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r12 = (android.os.AsyncResult) r12;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r12.exception;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        if (r4 == 0) goto L_0x062a;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x0621:
        r4 = "Invalid or missing EF[MSISDN]";	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0.log(r4);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        goto L_0x004b;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x062a:
        r5 = r12.result;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r5 = (com.android.internal.telephony.uicc.AdnRecord) r5;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r5.getNumber();	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0.mMsisdn = r4;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r5.getAlphaTag();	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0.mMsisdnTag = r4;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = "MSISDN: xxxxxxx";	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0.log(r4);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        goto L_0x004b;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x0647:
        r31 = 0;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r55;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r12 = r0.obj;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r12 = (android.os.AsyncResult) r12;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r12.userObj;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        if (r4 == 0) goto L_0x004b;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x0653:
        r4 = r12.userObj;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = (android.os.Message) r4;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = android.os.AsyncResult.forMessage(r4);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = r12.exception;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4.exception = r6;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r12.userObj;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = (android.os.Message) r4;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4.sendToTarget();	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        goto L_0x004b;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x0668:
        r31 = 1;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r55;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r12 = r0.obj;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r12 = (android.os.AsyncResult) r12;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r12.result;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = (byte[]) r4;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r4;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = (byte[]) r0;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r17 = r0;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r12.exception;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        if (r4 == 0) goto L_0x069a;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x067d:
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r0.mFh;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = 28433; // 0x6f11 float:3.9843E-41 double:1.4048E-319;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r7 = 8;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r7 = r0.obtainMessage(r7);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4.loadEFTransparent(r6, r7);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r0.mRecordsToLoad;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4 + 1;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0.mRecordsToLoad = r4;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        goto L_0x004b;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x069a:
        r4 = new java.lang.StringBuilder;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4.<init>();	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = "EF_MWIS: ";	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.append(r6);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = com.android.internal.telephony.uicc.IccUtils.bytesToHexString(r17);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.append(r6);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.toString();	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0.log(r4);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r17;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r1 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r1.mEfMWIS = r0;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = 0;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r17[r4];	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4 & 255;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = 255; // 0xff float:3.57E-43 double:1.26E-321;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        if (r4 != r6) goto L_0x06e9;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x06c5:
        r4 = "Uninitialized record MWIS";	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0.log(r4);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r0.mFh;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = 28433; // 0x6f11 float:3.9843E-41 double:1.4048E-319;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r7 = 8;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r7 = r0.obtainMessage(r7);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4.loadEFTransparent(r6, r7);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r0.mRecordsToLoad;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4 + 1;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0.mRecordsToLoad = r4;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        goto L_0x004b;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x06e9:
        r4 = 0;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r17[r4];	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4 & 1;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        if (r4 == 0) goto L_0x073d;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x06f0:
        r53 = 1;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x06f2:
        r4 = 1;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r17[r4];	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4 & 255;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0.mCountVoiceMessages = r4;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        if (r53 == 0) goto L_0x0708;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x06fd:
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r0.mCountVoiceMessages;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        if (r4 != 0) goto L_0x0708;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x0703:
        r4 = -1;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0.mCountVoiceMessages = r4;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x0708:
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r0.mRecordsEventsRegistrants;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = 0;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = java.lang.Integer.valueOf(r6);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4.notifyResult(r6);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = com.sec.android.app.CscFeature.getInstance();	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = "CscFeature_RIL_SupportOrangeCPHS";	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.getEnableStatus(r6);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        if (r4 == 0) goto L_0x004b;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x0720:
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r0.mFh;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = 28433; // 0x6f11 float:3.9843E-41 double:1.4048E-319;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r7 = 8;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r7 = r0.obtainMessage(r7);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4.loadEFTransparent(r6, r7);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r0.mRecordsToLoad;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4 + 1;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0.mRecordsToLoad = r4;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        goto L_0x004b;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x073d:
        r53 = 0;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        goto L_0x06f2;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x0740:
        r31 = 1;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r55;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r12 = r0.obj;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r12 = (android.os.AsyncResult) r12;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r12.result;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = (byte[]) r4;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r4;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = (byte[]) r0;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r17 = r0;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r12.exception;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        if (r4 != 0) goto L_0x004b;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x0755:
        r0 = r17;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r1 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r1.mEfCPHS_MWI = r0;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = com.sec.android.app.CscFeature.getInstance();	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = "CscFeature_RIL_SupportOrangeCPHS";	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.getEnableStatus(r6);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        if (r4 == 0) goto L_0x07be;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x0767:
        r4 = 0;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r17[r4];	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r28 = r4 & 15;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = new java.lang.StringBuilder;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4.<init>();	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = "Update Orange VOICEMAIL INDI CPHS : countVoiceMessages - ";	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.append(r6);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = r0.mCountVoiceMessages;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.append(r6);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = ", indicator - ";	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.append(r6);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r28;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.append(r0);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.toString();	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0.log(r4);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r0.mCountVoiceMessages;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        if (r4 != 0) goto L_0x004b;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x079a:
        r4 = 10;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r28;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        if (r0 != r4) goto L_0x07b3;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x07a0:
        r4 = -1;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0.mCountVoiceMessages = r4;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x07a5:
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r0.mRecordsEventsRegistrants;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = 0;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = java.lang.Integer.valueOf(r6);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4.notifyResult(r6);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        goto L_0x004b;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x07b3:
        r4 = 5;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r28;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        if (r0 != r4) goto L_0x07a5;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x07b8:
        r4 = 0;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0.mCountVoiceMessages = r4;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        goto L_0x07a5;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x07be:
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r0.mEfMWIS;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        if (r4 != 0) goto L_0x004b;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x07c4:
        r4 = 0;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r17[r4];	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r28 = r4 & 15;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = 10;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r28;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        if (r0 != r4) goto L_0x07e2;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x07cf:
        r4 = -1;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0.mCountVoiceMessages = r4;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x07d4:
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r0.mRecordsEventsRegistrants;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = 0;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = java.lang.Integer.valueOf(r6);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4.notifyResult(r6);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        goto L_0x004b;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x07e2:
        r4 = 5;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r28;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        if (r0 != r4) goto L_0x07d4;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x07e7:
        r4 = 0;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0.mCountVoiceMessages = r4;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        goto L_0x07d4;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x07ed:
        r31 = 1;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r55;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r12 = r0.obj;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r12 = (android.os.AsyncResult) r12;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r12.result;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = (byte[]) r4;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r4;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = (byte[]) r0;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r17 = r0;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r12.exception;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        if (r4 != 0) goto L_0x004b;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x0802:
        r4 = "ro.csc.countryiso_code";	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = android.os.SystemProperties.get(r4);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0.countryISO = r4;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = "CN";	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = r0.countryISO;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.equals(r6);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        if (r4 != 0) goto L_0x0830;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x0818:
        r4 = "HK";	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = r0.countryISO;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.equals(r6);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        if (r4 != 0) goto L_0x0830;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x0824:
        r4 = "TW";	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = r0.countryISO;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.equals(r6);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        if (r4 == 0) goto L_0x0870;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x0830:
        r4 = 0;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r17;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = r0.length;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r17;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = com.android.internal.telephony.uicc.IccUtils.ICCIDbcdToString(r0, r4, r6);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0.mIccId = r4;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x083e:
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r0.mIccIdReadyRegistrants;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4.notifyRegistrants();	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = SHIP_BUILD;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        if (r4 != 0) goto L_0x087f;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x0849:
        r4 = new java.lang.StringBuilder;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4.<init>();	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = "EVENT_GET_ICCID_DONE mIccId: ";	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.append(r6);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = r0.mIccId;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.append(r6);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.toString();	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0.log(r4);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x0865:
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r0.mIccId;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        if (r4 == 0) goto L_0x004b;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x086b:
        r54.checkSimChanged();	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        goto L_0x004b;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x0870:
        r4 = 0;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r17;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = r0.length;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r17;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = com.android.internal.telephony.uicc.IccUtils.bcdToString(r0, r4, r6);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0.mIccId = r4;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        goto L_0x083e;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x087f:
        r4 = "EVENT_GET_ICCID_DONE mIccId: ******";	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0.log(r4);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        goto L_0x0865;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x0887:
        r0 = r55;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r12 = r0.obj;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r12 = (android.os.AsyncResult) r12;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r12.result;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = (byte[]) r4;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r4;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = (byte[]) r0;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r17 = r0;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r12.exception;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        if (r4 != 0) goto L_0x004b;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x089a:
        r4 = "ro.csc.countryiso_code";	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = android.os.SystemProperties.get(r4);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0.countryISO = r4;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = "CN";	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = r0.countryISO;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.equals(r6);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        if (r4 != 0) goto L_0x08c8;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x08b0:
        r4 = "HK";	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = r0.countryISO;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.equals(r6);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        if (r4 != 0) goto L_0x08c8;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x08bc:
        r4 = "TW";	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = r0.countryISO;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.equals(r6);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        if (r4 == 0) goto L_0x09aa;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x08c8:
        r4 = 0;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r17;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = r0.length;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r17;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = com.android.internal.telephony.uicc.IccUtils.ICCIDbcdToString(r0, r4, r6);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0.mIccId = r4;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x08d6:
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r0.mIccIdReadyRegistrants;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4.notifyRegistrants();	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = SHIP_BUILD;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        if (r4 != 0) goto L_0x09ba;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x08e1:
        r4 = new java.lang.StringBuilder;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4.<init>();	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = "EVENT_GET_ICCID_WHEN_LOCKED_DONE mIccId: ";	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.append(r6);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = r0.mIccId;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.append(r6);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.toString();	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0.log(r4);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x08fd:
        r4 = android.sec.enterprise.EnterpriseDeviceManager.getInstance();	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r41 = r4.getPhoneRestrictionPolicy();	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r24 = 0;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        if (r41 == 0) goto L_0x0910;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x0909:
        r4 = 0;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r41;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r24 = r0.isSimLockedByAdmin(r4);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x0910:
        if (r24 == 0) goto L_0x0940;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x0912:
        r29 = new android.content.Intent;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = "com.android.server.enterprise.ICCID_AVAILABLE";	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r29;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0.<init>(r4);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r0.mContext;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r29;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4.sendBroadcast(r0);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = new java.lang.StringBuilder;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4.<init>();	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = "EVENT_GET_ICCID_WHEN_LOCKED_DONE, icc = ";	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.append(r6);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = r0.mIccId;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.append(r6);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.toString();	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0.log(r4);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x0940:
        r4 = "XEC";	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = "ro.csc.sales_code";	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = android.os.SystemProperties.get(r6);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.equals(r6);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        if (r4 != 0) goto L_0x0978;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x094e:
        r4 = "VIA";	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = "ro.csc.sales_code";	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = android.os.SystemProperties.get(r6);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.equals(r6);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        if (r4 != 0) goto L_0x0978;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x095c:
        r4 = "VID";	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = "ro.csc.sales_code";	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = android.os.SystemProperties.get(r6);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.equals(r6);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        if (r4 != 0) goto L_0x0978;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x096a:
        r4 = "O2U";	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = "ro.csc.sales_code";	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = android.os.SystemProperties.get(r6);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.equals(r6);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        if (r4 == 0) goto L_0x004b;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x0978:
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r0.mIccId;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        if (r4 == 0) goto L_0x004b;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x097e:
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r0.mIccId;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.length();	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = 4;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        if (r4 < r6) goto L_0x004b;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x0989:
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r0.mIccId;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = 2;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r7 = 4;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r16 = r4.substring(r6, r7);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = "34";	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r16;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.equals(r0);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        if (r4 == 0) goto L_0x09c3;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x099d:
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r0.mContext;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = "es";	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r7 = "es";	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        com.android.internal.telephony.MccTable.setSystemLocale(r4, r6, r7);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        goto L_0x004b;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x09aa:
        r4 = 0;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r17;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = r0.length;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r17;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = com.android.internal.telephony.uicc.IccUtils.bcdToString(r0, r4, r6);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0.mIccId = r4;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        goto L_0x08d6;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x09ba:
        r4 = "EVENT_GET_ICCID_WHEN_LOCKED_DONE mIccId: ******";	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0.log(r4);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        goto L_0x08fd;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x09c3:
        r4 = "49";	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r16;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.equals(r0);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        if (r4 == 0) goto L_0x09da;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x09cd:
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r0.mContext;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = "de";	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r7 = "de";	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        com.android.internal.telephony.MccTable.setSystemLocale(r4, r6, r7);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        goto L_0x004b;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x09da:
        r4 = "44";	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r16;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.equals(r0);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        if (r4 == 0) goto L_0x004b;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x09e4:
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r0.mContext;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = "en";	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r7 = "gb";	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        com.android.internal.telephony.MccTable.setSystemLocale(r4, r6, r7);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        goto L_0x004b;
    L_0x09f1:
        r31 = 1;
        r0 = r55;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r12 = r0.obj;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r12 = (android.os.AsyncResult) r12;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r12.result;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = (byte[]) r4;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r4;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = (byte[]) r0;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r17 = r0;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r12.exception;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        if (r4 == 0) goto L_0x0be9;
    L_0x0a06:
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r0.mMncLength;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = -1;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        if (r4 == r6) goto L_0x0a1a;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x0a0d:
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r0.mMncLength;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        if (r4 == 0) goto L_0x0a1a;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x0a13:
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r0.mMncLength;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = 2;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        if (r4 != r6) goto L_0x0a85;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x0a1a:
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r0.mImsi;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        if (r4 == 0) goto L_0x0a85;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x0a20:
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r0.mImsi;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.length();	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = 6;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        if (r4 < r6) goto L_0x0a85;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x0a2b:
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r0.mImsi;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = 0;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r7 = 6;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r38 = r4.substring(r6, r7);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = new java.lang.StringBuilder;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4.<init>();	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = "mccmncCode=";	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.append(r6);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r38;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.append(r0);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.toString();	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0.log(r4);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r13 = MCCMNC_CODES_HAVING_3DIGITS_MNC;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r13.length;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r34 = r0;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r25 = 0;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x0a56:
        r0 = r25;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r1 = r34;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        if (r0 >= r1) goto L_0x0a85;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x0a5c:
        r37 = r13[r25];	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r37.equals(r38);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        if (r4 == 0) goto L_0x0b45;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x0a64:
        r4 = 3;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0.mMncLength = r4;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = new java.lang.StringBuilder;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4.<init>();	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = "setting6 mMncLength=";	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.append(r6);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = r0.mMncLength;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.append(r6);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.toString();	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0.log(r4);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x0a85:
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r0.mMncLength;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        if (r4 == 0) goto L_0x0a92;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x0a8b:
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r0.mMncLength;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = -1;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        if (r4 != r6) goto L_0x0aca;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x0a92:
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r0.mImsi;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        if (r4 == 0) goto L_0x0b6d;
    L_0x0a98:
        r0 = r54;	 Catch:{ NumberFormatException -> 0x0b49 }
        r4 = r0.mImsi;	 Catch:{ NumberFormatException -> 0x0b49 }
        r6 = 0;	 Catch:{ NumberFormatException -> 0x0b49 }
        r7 = 3;	 Catch:{ NumberFormatException -> 0x0b49 }
        r4 = r4.substring(r6, r7);	 Catch:{ NumberFormatException -> 0x0b49 }
        r36 = java.lang.Integer.parseInt(r4);	 Catch:{ NumberFormatException -> 0x0b49 }
        r4 = com.android.internal.telephony.MccTable.smallestDigitsMccForMnc(r36);	 Catch:{ NumberFormatException -> 0x0b49 }
        r0 = r54;	 Catch:{ NumberFormatException -> 0x0b49 }
        r0.mMncLength = r4;	 Catch:{ NumberFormatException -> 0x0b49 }
        r4 = new java.lang.StringBuilder;	 Catch:{ NumberFormatException -> 0x0b49 }
        r4.<init>();	 Catch:{ NumberFormatException -> 0x0b49 }
        r6 = "setting7 mMncLength=";	 Catch:{ NumberFormatException -> 0x0b49 }
        r4 = r4.append(r6);	 Catch:{ NumberFormatException -> 0x0b49 }
        r0 = r54;	 Catch:{ NumberFormatException -> 0x0b49 }
        r6 = r0.mMncLength;	 Catch:{ NumberFormatException -> 0x0b49 }
        r4 = r4.append(r6);	 Catch:{ NumberFormatException -> 0x0b49 }
        r4 = r4.toString();	 Catch:{ NumberFormatException -> 0x0b49 }
        r0 = r54;	 Catch:{ NumberFormatException -> 0x0b49 }
        r0.log(r4);	 Catch:{ NumberFormatException -> 0x0b49 }
    L_0x0aca:
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r0.mImsi;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        if (r4 == 0) goto L_0x004b;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x0ad0:
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r0.mMncLength;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        if (r4 == 0) goto L_0x004b;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x0ad6:
        r4 = "DCG";	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = "DGG";	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.equals(r6);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        if (r4 != 0) goto L_0x0b08;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x0ae0:
        r4 = "DCGG";	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = "DGG";	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.equals(r6);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        if (r4 != 0) goto L_0x0b08;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x0aea:
        r4 = "DCGS";	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = "DGG";	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.equals(r6);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        if (r4 != 0) goto L_0x0b08;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x0af4:
        r4 = "DCGGS";	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = "DGG";	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.equals(r6);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        if (r4 != 0) goto L_0x0b08;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x0afe:
        r4 = "CG";	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = "DGG";	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.equals(r6);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        if (r4 == 0) goto L_0x0ba9;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x0b08:
        r4 = "gsm.sim.selectnetwork";	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r46 = android.os.SystemProperties.get(r4);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = new java.lang.StringBuilder;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4.<init>();	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = "simselswitch = ";	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.append(r6);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r46;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.append(r0);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.toString();	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0.log(r4);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = "CDMA";	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r46;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.equals(r0);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        if (r4 == 0) goto L_0x0b90;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x0b32:
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r0.phone;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.getPhoneId();	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        if (r4 != 0) goto L_0x0b90;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x0b3c:
        r4 = "do not set PROPERTY_ICC_OPERATOR_NUMERIC in case of slot1 cdma switching";	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0.log(r4);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        goto L_0x004b;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x0b45:
        r25 = r25 + 1;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        goto L_0x0a56;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x0b49:
        r18 = move-exception;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = 0;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0.mMncLength = r4;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = new java.lang.StringBuilder;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4.<init>();	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = "Corrupt IMSI! setting8 mMncLength=";	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.append(r6);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = r0.mMncLength;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.append(r6);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.toString();	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0.loge(r4);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        goto L_0x0aca;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x0b6d:
        r4 = 0;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0.mMncLength = r4;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = new java.lang.StringBuilder;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4.<init>();	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = "MNC length not present in EF_AD setting9 mMncLength=";	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.append(r6);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = r0.mMncLength;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.append(r6);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.toString();	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0.log(r4);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        goto L_0x0aca;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x0b90:
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r0.mContext;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = r0.mImsi;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r7 = 0;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r8 = r0.mMncLength;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r8 = r8 + 3;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = r6.substring(r7, r8);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r7 = 0;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        com.android.internal.telephony.MccTable.updateMccMncConfiguration(r4, r6, r7);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        goto L_0x004b;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x0ba9:
        r4 = new java.lang.StringBuilder;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4.<init>();	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = "update mccmnc=";	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.append(r6);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = r0.mImsi;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r7 = 0;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r8 = r0.mMncLength;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r8 = r8 + 3;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = r6.substring(r7, r8);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.append(r6);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.toString();	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0.log(r4);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r0.mContext;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = r0.mImsi;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r7 = 0;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r8 = r0.mMncLength;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r8 = r8 + 3;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = r6.substring(r7, r8);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r7 = 0;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        com.android.internal.telephony.MccTable.updateMccMncConfiguration(r4, r6, r7);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        goto L_0x004b;
    L_0x0be9:
        r4 = new java.lang.StringBuilder;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4.<init>();	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = "EF_AD: ";	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.append(r6);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = com.android.internal.telephony.uicc.IccUtils.bytesToHexString(r17);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.append(r6);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.toString();	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0.log(r4);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r17;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r0.length;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = 3;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        if (r4 >= r6) goto L_0x0df5;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x0c0b:
        r4 = "Corrupt AD data on SIM";	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0.log(r4);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r0.mMncLength;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = -1;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        if (r4 == r6) goto L_0x0c26;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x0c19:
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r0.mMncLength;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        if (r4 == 0) goto L_0x0c26;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x0c1f:
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r0.mMncLength;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = 2;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        if (r4 != r6) goto L_0x0c91;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x0c26:
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r0.mImsi;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        if (r4 == 0) goto L_0x0c91;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x0c2c:
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r0.mImsi;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.length();	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = 6;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        if (r4 < r6) goto L_0x0c91;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x0c37:
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r0.mImsi;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = 0;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r7 = 6;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r38 = r4.substring(r6, r7);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = new java.lang.StringBuilder;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4.<init>();	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = "mccmncCode=";	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.append(r6);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r38;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.append(r0);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.toString();	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0.log(r4);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r13 = MCCMNC_CODES_HAVING_3DIGITS_MNC;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r13.length;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r34 = r0;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r25 = 0;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x0c62:
        r0 = r25;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r1 = r34;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        if (r0 >= r1) goto L_0x0c91;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x0c68:
        r37 = r13[r25];	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r37.equals(r38);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        if (r4 == 0) goto L_0x0d51;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x0c70:
        r4 = 3;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0.mMncLength = r4;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = new java.lang.StringBuilder;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4.<init>();	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = "setting6 mMncLength=";	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.append(r6);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = r0.mMncLength;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.append(r6);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.toString();	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0.log(r4);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x0c91:
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r0.mMncLength;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        if (r4 == 0) goto L_0x0c9e;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x0c97:
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r0.mMncLength;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = -1;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        if (r4 != r6) goto L_0x0cd6;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x0c9e:
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r0.mImsi;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        if (r4 == 0) goto L_0x0d79;
    L_0x0ca4:
        r0 = r54;	 Catch:{ NumberFormatException -> 0x0d55 }
        r4 = r0.mImsi;	 Catch:{ NumberFormatException -> 0x0d55 }
        r6 = 0;	 Catch:{ NumberFormatException -> 0x0d55 }
        r7 = 3;	 Catch:{ NumberFormatException -> 0x0d55 }
        r4 = r4.substring(r6, r7);	 Catch:{ NumberFormatException -> 0x0d55 }
        r36 = java.lang.Integer.parseInt(r4);	 Catch:{ NumberFormatException -> 0x0d55 }
        r4 = com.android.internal.telephony.MccTable.smallestDigitsMccForMnc(r36);	 Catch:{ NumberFormatException -> 0x0d55 }
        r0 = r54;	 Catch:{ NumberFormatException -> 0x0d55 }
        r0.mMncLength = r4;	 Catch:{ NumberFormatException -> 0x0d55 }
        r4 = new java.lang.StringBuilder;	 Catch:{ NumberFormatException -> 0x0d55 }
        r4.<init>();	 Catch:{ NumberFormatException -> 0x0d55 }
        r6 = "setting7 mMncLength=";	 Catch:{ NumberFormatException -> 0x0d55 }
        r4 = r4.append(r6);	 Catch:{ NumberFormatException -> 0x0d55 }
        r0 = r54;	 Catch:{ NumberFormatException -> 0x0d55 }
        r6 = r0.mMncLength;	 Catch:{ NumberFormatException -> 0x0d55 }
        r4 = r4.append(r6);	 Catch:{ NumberFormatException -> 0x0d55 }
        r4 = r4.toString();	 Catch:{ NumberFormatException -> 0x0d55 }
        r0 = r54;	 Catch:{ NumberFormatException -> 0x0d55 }
        r0.log(r4);	 Catch:{ NumberFormatException -> 0x0d55 }
    L_0x0cd6:
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r0.mImsi;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        if (r4 == 0) goto L_0x004b;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x0cdc:
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r0.mMncLength;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        if (r4 == 0) goto L_0x004b;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x0ce2:
        r4 = "DCG";	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = "DGG";	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.equals(r6);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        if (r4 != 0) goto L_0x0d14;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x0cec:
        r4 = "DCGG";	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = "DGG";	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.equals(r6);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        if (r4 != 0) goto L_0x0d14;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x0cf6:
        r4 = "DCGS";	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = "DGG";	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.equals(r6);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        if (r4 != 0) goto L_0x0d14;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x0d00:
        r4 = "DCGGS";	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = "DGG";	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.equals(r6);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        if (r4 != 0) goto L_0x0d14;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x0d0a:
        r4 = "CG";	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = "DGG";	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.equals(r6);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        if (r4 == 0) goto L_0x0db5;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x0d14:
        r4 = "gsm.sim.selectnetwork";	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r46 = android.os.SystemProperties.get(r4);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = new java.lang.StringBuilder;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4.<init>();	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = "simselswitch = ";	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.append(r6);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r46;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.append(r0);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.toString();	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0.log(r4);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = "CDMA";	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r46;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.equals(r0);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        if (r4 == 0) goto L_0x0d9c;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x0d3e:
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r0.phone;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.getPhoneId();	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        if (r4 != 0) goto L_0x0d9c;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x0d48:
        r4 = "do not set PROPERTY_ICC_OPERATOR_NUMERIC in case of slot1 cdma switching";	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0.log(r4);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        goto L_0x004b;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x0d51:
        r25 = r25 + 1;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        goto L_0x0c62;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x0d55:
        r18 = move-exception;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = 0;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0.mMncLength = r4;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = new java.lang.StringBuilder;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4.<init>();	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = "Corrupt IMSI! setting8 mMncLength=";	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.append(r6);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = r0.mMncLength;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.append(r6);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.toString();	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0.loge(r4);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        goto L_0x0cd6;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x0d79:
        r4 = 0;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0.mMncLength = r4;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = new java.lang.StringBuilder;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4.<init>();	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = "MNC length not present in EF_AD setting9 mMncLength=";	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.append(r6);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = r0.mMncLength;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.append(r6);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.toString();	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0.log(r4);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        goto L_0x0cd6;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x0d9c:
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r0.mContext;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = r0.mImsi;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r7 = 0;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r8 = r0.mMncLength;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r8 = r8 + 3;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = r6.substring(r7, r8);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r7 = 0;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        com.android.internal.telephony.MccTable.updateMccMncConfiguration(r4, r6, r7);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        goto L_0x004b;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x0db5:
        r4 = new java.lang.StringBuilder;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4.<init>();	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = "update mccmnc=";	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.append(r6);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = r0.mImsi;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r7 = 0;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r8 = r0.mMncLength;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r8 = r8 + 3;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = r6.substring(r7, r8);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.append(r6);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.toString();	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0.log(r4);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r0.mContext;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = r0.mImsi;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r7 = 0;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r8 = r0.mMncLength;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r8 = r8 + 3;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = r6.substring(r7, r8);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r7 = 0;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        com.android.internal.telephony.MccTable.updateMccMncConfiguration(r4, r6, r7);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        goto L_0x004b;
    L_0x0df5:
        r0 = r17;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r0.length;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = 3;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        if (r4 != r6) goto L_0x0fe5;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x0dfb:
        r4 = "MNC length not present in EF_AD";	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0.log(r4);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r0.mMncLength;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = -1;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        if (r4 == r6) goto L_0x0e16;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x0e09:
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r0.mMncLength;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        if (r4 == 0) goto L_0x0e16;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x0e0f:
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r0.mMncLength;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = 2;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        if (r4 != r6) goto L_0x0e81;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x0e16:
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r0.mImsi;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        if (r4 == 0) goto L_0x0e81;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x0e1c:
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r0.mImsi;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.length();	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = 6;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        if (r4 < r6) goto L_0x0e81;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x0e27:
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r0.mImsi;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = 0;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r7 = 6;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r38 = r4.substring(r6, r7);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = new java.lang.StringBuilder;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4.<init>();	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = "mccmncCode=";	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.append(r6);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r38;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.append(r0);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.toString();	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0.log(r4);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r13 = MCCMNC_CODES_HAVING_3DIGITS_MNC;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r13.length;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r34 = r0;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r25 = 0;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x0e52:
        r0 = r25;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r1 = r34;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        if (r0 >= r1) goto L_0x0e81;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x0e58:
        r37 = r13[r25];	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r37.equals(r38);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        if (r4 == 0) goto L_0x0f41;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x0e60:
        r4 = 3;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0.mMncLength = r4;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = new java.lang.StringBuilder;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4.<init>();	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = "setting6 mMncLength=";	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.append(r6);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = r0.mMncLength;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.append(r6);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.toString();	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0.log(r4);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x0e81:
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r0.mMncLength;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        if (r4 == 0) goto L_0x0e8e;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x0e87:
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r0.mMncLength;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = -1;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        if (r4 != r6) goto L_0x0ec6;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x0e8e:
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r0.mImsi;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        if (r4 == 0) goto L_0x0f69;
    L_0x0e94:
        r0 = r54;	 Catch:{ NumberFormatException -> 0x0f45 }
        r4 = r0.mImsi;	 Catch:{ NumberFormatException -> 0x0f45 }
        r6 = 0;	 Catch:{ NumberFormatException -> 0x0f45 }
        r7 = 3;	 Catch:{ NumberFormatException -> 0x0f45 }
        r4 = r4.substring(r6, r7);	 Catch:{ NumberFormatException -> 0x0f45 }
        r36 = java.lang.Integer.parseInt(r4);	 Catch:{ NumberFormatException -> 0x0f45 }
        r4 = com.android.internal.telephony.MccTable.smallestDigitsMccForMnc(r36);	 Catch:{ NumberFormatException -> 0x0f45 }
        r0 = r54;	 Catch:{ NumberFormatException -> 0x0f45 }
        r0.mMncLength = r4;	 Catch:{ NumberFormatException -> 0x0f45 }
        r4 = new java.lang.StringBuilder;	 Catch:{ NumberFormatException -> 0x0f45 }
        r4.<init>();	 Catch:{ NumberFormatException -> 0x0f45 }
        r6 = "setting7 mMncLength=";	 Catch:{ NumberFormatException -> 0x0f45 }
        r4 = r4.append(r6);	 Catch:{ NumberFormatException -> 0x0f45 }
        r0 = r54;	 Catch:{ NumberFormatException -> 0x0f45 }
        r6 = r0.mMncLength;	 Catch:{ NumberFormatException -> 0x0f45 }
        r4 = r4.append(r6);	 Catch:{ NumberFormatException -> 0x0f45 }
        r4 = r4.toString();	 Catch:{ NumberFormatException -> 0x0f45 }
        r0 = r54;	 Catch:{ NumberFormatException -> 0x0f45 }
        r0.log(r4);	 Catch:{ NumberFormatException -> 0x0f45 }
    L_0x0ec6:
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r0.mImsi;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        if (r4 == 0) goto L_0x004b;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x0ecc:
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r0.mMncLength;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        if (r4 == 0) goto L_0x004b;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x0ed2:
        r4 = "DCG";	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = "DGG";	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.equals(r6);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        if (r4 != 0) goto L_0x0f04;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x0edc:
        r4 = "DCGG";	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = "DGG";	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.equals(r6);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        if (r4 != 0) goto L_0x0f04;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x0ee6:
        r4 = "DCGS";	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = "DGG";	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.equals(r6);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        if (r4 != 0) goto L_0x0f04;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x0ef0:
        r4 = "DCGGS";	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = "DGG";	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.equals(r6);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        if (r4 != 0) goto L_0x0f04;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x0efa:
        r4 = "CG";	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = "DGG";	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.equals(r6);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        if (r4 == 0) goto L_0x0fa5;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x0f04:
        r4 = "gsm.sim.selectnetwork";	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r46 = android.os.SystemProperties.get(r4);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = new java.lang.StringBuilder;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4.<init>();	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = "simselswitch = ";	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.append(r6);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r46;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.append(r0);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.toString();	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0.log(r4);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = "CDMA";	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r46;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.equals(r0);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        if (r4 == 0) goto L_0x0f8c;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x0f2e:
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r0.phone;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.getPhoneId();	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        if (r4 != 0) goto L_0x0f8c;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x0f38:
        r4 = "do not set PROPERTY_ICC_OPERATOR_NUMERIC in case of slot1 cdma switching";	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0.log(r4);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        goto L_0x004b;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x0f41:
        r25 = r25 + 1;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        goto L_0x0e52;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x0f45:
        r18 = move-exception;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = 0;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0.mMncLength = r4;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = new java.lang.StringBuilder;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4.<init>();	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = "Corrupt IMSI! setting8 mMncLength=";	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.append(r6);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = r0.mMncLength;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.append(r6);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.toString();	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0.loge(r4);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        goto L_0x0ec6;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x0f69:
        r4 = 0;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0.mMncLength = r4;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = new java.lang.StringBuilder;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4.<init>();	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = "MNC length not present in EF_AD setting9 mMncLength=";	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.append(r6);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = r0.mMncLength;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.append(r6);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.toString();	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0.log(r4);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        goto L_0x0ec6;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x0f8c:
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r0.mContext;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = r0.mImsi;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r7 = 0;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r8 = r0.mMncLength;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r8 = r8 + 3;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = r6.substring(r7, r8);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r7 = 0;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        com.android.internal.telephony.MccTable.updateMccMncConfiguration(r4, r6, r7);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        goto L_0x004b;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x0fa5:
        r4 = new java.lang.StringBuilder;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4.<init>();	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = "update mccmnc=";	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.append(r6);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = r0.mImsi;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r7 = 0;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r8 = r0.mMncLength;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r8 = r8 + 3;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = r6.substring(r7, r8);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.append(r6);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.toString();	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0.log(r4);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r0.mContext;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = r0.mImsi;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r7 = 0;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r8 = r0.mMncLength;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r8 = r8 + 3;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = r6.substring(r7, r8);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r7 = 0;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        com.android.internal.telephony.MccTable.updateMccMncConfiguration(r4, r6, r7);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        goto L_0x004b;
    L_0x0fe5:
        r4 = 3;
        r4 = r17[r4];	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4 & 15;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0.mMncLength = r4;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = new java.lang.StringBuilder;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4.<init>();	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = "setting4 mMncLength=";	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.append(r6);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = r0.mMncLength;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.append(r6);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.toString();	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0.log(r4);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r0.mMncLength;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = 15;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        if (r4 != r6) goto L_0x1033;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x1012:
        r4 = 0;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0.mMncLength = r4;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = new java.lang.StringBuilder;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4.<init>();	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = "setting5 mMncLength=";	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.append(r6);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = r0.mMncLength;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.append(r6);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.toString();	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0.log(r4);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x1033:
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r0.mMncLength;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = -1;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        if (r4 == r6) goto L_0x1047;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x103a:
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r0.mMncLength;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        if (r4 == 0) goto L_0x1047;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x1040:
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r0.mMncLength;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = 2;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        if (r4 != r6) goto L_0x10b2;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x1047:
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r0.mImsi;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        if (r4 == 0) goto L_0x10b2;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x104d:
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r0.mImsi;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.length();	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = 6;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        if (r4 < r6) goto L_0x10b2;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x1058:
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r0.mImsi;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = 0;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r7 = 6;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r38 = r4.substring(r6, r7);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = new java.lang.StringBuilder;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4.<init>();	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = "mccmncCode=";	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.append(r6);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r38;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.append(r0);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.toString();	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0.log(r4);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r13 = MCCMNC_CODES_HAVING_3DIGITS_MNC;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r13.length;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r34 = r0;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r25 = 0;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x1083:
        r0 = r25;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r1 = r34;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        if (r0 >= r1) goto L_0x10b2;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x1089:
        r37 = r13[r25];	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r37.equals(r38);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        if (r4 == 0) goto L_0x1172;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x1091:
        r4 = 3;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0.mMncLength = r4;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = new java.lang.StringBuilder;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4.<init>();	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = "setting6 mMncLength=";	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.append(r6);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = r0.mMncLength;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.append(r6);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.toString();	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0.log(r4);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x10b2:
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r0.mMncLength;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        if (r4 == 0) goto L_0x10bf;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x10b8:
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r0.mMncLength;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = -1;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        if (r4 != r6) goto L_0x10f7;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x10bf:
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r0.mImsi;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        if (r4 == 0) goto L_0x119a;
    L_0x10c5:
        r0 = r54;	 Catch:{ NumberFormatException -> 0x1176 }
        r4 = r0.mImsi;	 Catch:{ NumberFormatException -> 0x1176 }
        r6 = 0;	 Catch:{ NumberFormatException -> 0x1176 }
        r7 = 3;	 Catch:{ NumberFormatException -> 0x1176 }
        r4 = r4.substring(r6, r7);	 Catch:{ NumberFormatException -> 0x1176 }
        r36 = java.lang.Integer.parseInt(r4);	 Catch:{ NumberFormatException -> 0x1176 }
        r4 = com.android.internal.telephony.MccTable.smallestDigitsMccForMnc(r36);	 Catch:{ NumberFormatException -> 0x1176 }
        r0 = r54;	 Catch:{ NumberFormatException -> 0x1176 }
        r0.mMncLength = r4;	 Catch:{ NumberFormatException -> 0x1176 }
        r4 = new java.lang.StringBuilder;	 Catch:{ NumberFormatException -> 0x1176 }
        r4.<init>();	 Catch:{ NumberFormatException -> 0x1176 }
        r6 = "setting7 mMncLength=";	 Catch:{ NumberFormatException -> 0x1176 }
        r4 = r4.append(r6);	 Catch:{ NumberFormatException -> 0x1176 }
        r0 = r54;	 Catch:{ NumberFormatException -> 0x1176 }
        r6 = r0.mMncLength;	 Catch:{ NumberFormatException -> 0x1176 }
        r4 = r4.append(r6);	 Catch:{ NumberFormatException -> 0x1176 }
        r4 = r4.toString();	 Catch:{ NumberFormatException -> 0x1176 }
        r0 = r54;	 Catch:{ NumberFormatException -> 0x1176 }
        r0.log(r4);	 Catch:{ NumberFormatException -> 0x1176 }
    L_0x10f7:
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r0.mImsi;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        if (r4 == 0) goto L_0x004b;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x10fd:
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r0.mMncLength;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        if (r4 == 0) goto L_0x004b;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x1103:
        r4 = "DCG";	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = "DGG";	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.equals(r6);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        if (r4 != 0) goto L_0x1135;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x110d:
        r4 = "DCGG";	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = "DGG";	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.equals(r6);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        if (r4 != 0) goto L_0x1135;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x1117:
        r4 = "DCGS";	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = "DGG";	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.equals(r6);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        if (r4 != 0) goto L_0x1135;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x1121:
        r4 = "DCGGS";	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = "DGG";	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.equals(r6);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        if (r4 != 0) goto L_0x1135;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x112b:
        r4 = "CG";	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = "DGG";	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.equals(r6);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        if (r4 == 0) goto L_0x11d6;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x1135:
        r4 = "gsm.sim.selectnetwork";	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r46 = android.os.SystemProperties.get(r4);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = new java.lang.StringBuilder;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4.<init>();	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = "simselswitch = ";	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.append(r6);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r46;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.append(r0);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.toString();	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0.log(r4);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = "CDMA";	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r46;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.equals(r0);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        if (r4 == 0) goto L_0x11bd;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x115f:
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r0.phone;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.getPhoneId();	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        if (r4 != 0) goto L_0x11bd;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x1169:
        r4 = "do not set PROPERTY_ICC_OPERATOR_NUMERIC in case of slot1 cdma switching";	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0.log(r4);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        goto L_0x004b;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x1172:
        r25 = r25 + 1;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        goto L_0x1083;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x1176:
        r18 = move-exception;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = 0;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0.mMncLength = r4;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = new java.lang.StringBuilder;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4.<init>();	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = "Corrupt IMSI! setting8 mMncLength=";	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.append(r6);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = r0.mMncLength;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.append(r6);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.toString();	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0.loge(r4);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        goto L_0x10f7;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x119a:
        r4 = 0;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0.mMncLength = r4;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = new java.lang.StringBuilder;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4.<init>();	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = "MNC length not present in EF_AD setting9 mMncLength=";	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.append(r6);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = r0.mMncLength;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.append(r6);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.toString();	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0.log(r4);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        goto L_0x10f7;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x11bd:
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r0.mContext;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = r0.mImsi;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r7 = 0;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r8 = r0.mMncLength;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r8 = r8 + 3;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = r6.substring(r7, r8);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r7 = 0;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        com.android.internal.telephony.MccTable.updateMccMncConfiguration(r4, r6, r7);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        goto L_0x004b;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x11d6:
        r4 = new java.lang.StringBuilder;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4.<init>();	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = "update mccmnc=";	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.append(r6);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = r0.mImsi;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r7 = 0;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r8 = r0.mMncLength;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r8 = r8 + 3;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = r6.substring(r7, r8);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.append(r6);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.toString();	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0.log(r4);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r0.mContext;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = r0.mImsi;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r7 = 0;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r8 = r0.mMncLength;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r8 = r8 + 3;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = r6.substring(r7, r8);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r7 = 0;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        com.android.internal.telephony.MccTable.updateMccMncConfiguration(r4, r6, r7);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        goto L_0x004b;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x1216:
        r4 = move-exception;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = r0.mMncLength;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r7 = -1;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        if (r6 == r7) goto L_0x122b;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x121e:
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = r0.mMncLength;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        if (r6 == 0) goto L_0x122b;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x1224:
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = r0.mMncLength;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r7 = 2;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        if (r6 != r7) goto L_0x1296;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x122b:
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = r0.mImsi;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        if (r6 == 0) goto L_0x1296;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x1231:
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = r0.mImsi;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = r6.length();	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r7 = 6;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        if (r6 < r7) goto L_0x1296;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x123c:
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = r0.mImsi;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r7 = 0;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r8 = 6;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r38 = r6.substring(r7, r8);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = new java.lang.StringBuilder;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6.<init>();	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r7 = "mccmncCode=";	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = r6.append(r7);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r38;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = r6.append(r0);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = r6.toString();	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0.log(r6);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r13 = MCCMNC_CODES_HAVING_3DIGITS_MNC;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r13.length;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r34 = r0;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r25 = 0;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x1267:
        r0 = r25;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r1 = r34;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        if (r0 >= r1) goto L_0x1296;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x126d:
        r37 = r13[r25];	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = r37.equals(r38);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        if (r6 == 0) goto L_0x1355;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x1275:
        r6 = 3;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0.mMncLength = r6;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = new java.lang.StringBuilder;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6.<init>();	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r7 = "setting6 mMncLength=";	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = r6.append(r7);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r7 = r0.mMncLength;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = r6.append(r7);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = r6.toString();	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0.log(r6);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x1296:
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = r0.mMncLength;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        if (r6 == 0) goto L_0x12a3;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x129c:
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = r0.mMncLength;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r7 = -1;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        if (r6 != r7) goto L_0x12db;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x12a3:
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = r0.mImsi;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        if (r6 == 0) goto L_0x137d;
    L_0x12a9:
        r0 = r54;	 Catch:{ NumberFormatException -> 0x1359 }
        r6 = r0.mImsi;	 Catch:{ NumberFormatException -> 0x1359 }
        r7 = 0;	 Catch:{ NumberFormatException -> 0x1359 }
        r8 = 3;	 Catch:{ NumberFormatException -> 0x1359 }
        r6 = r6.substring(r7, r8);	 Catch:{ NumberFormatException -> 0x1359 }
        r36 = java.lang.Integer.parseInt(r6);	 Catch:{ NumberFormatException -> 0x1359 }
        r6 = com.android.internal.telephony.MccTable.smallestDigitsMccForMnc(r36);	 Catch:{ NumberFormatException -> 0x1359 }
        r0 = r54;	 Catch:{ NumberFormatException -> 0x1359 }
        r0.mMncLength = r6;	 Catch:{ NumberFormatException -> 0x1359 }
        r6 = new java.lang.StringBuilder;	 Catch:{ NumberFormatException -> 0x1359 }
        r6.<init>();	 Catch:{ NumberFormatException -> 0x1359 }
        r7 = "setting7 mMncLength=";	 Catch:{ NumberFormatException -> 0x1359 }
        r6 = r6.append(r7);	 Catch:{ NumberFormatException -> 0x1359 }
        r0 = r54;	 Catch:{ NumberFormatException -> 0x1359 }
        r7 = r0.mMncLength;	 Catch:{ NumberFormatException -> 0x1359 }
        r6 = r6.append(r7);	 Catch:{ NumberFormatException -> 0x1359 }
        r6 = r6.toString();	 Catch:{ NumberFormatException -> 0x1359 }
        r0 = r54;	 Catch:{ NumberFormatException -> 0x1359 }
        r0.log(r6);	 Catch:{ NumberFormatException -> 0x1359 }
    L_0x12db:
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = r0.mImsi;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        if (r6 == 0) goto L_0x1354;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x12e1:
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = r0.mMncLength;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        if (r6 == 0) goto L_0x1354;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x12e7:
        r6 = "DCG";	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r7 = "DGG";	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = r6.equals(r7);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        if (r6 != 0) goto L_0x1319;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x12f1:
        r6 = "DCGG";	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r7 = "DGG";	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = r6.equals(r7);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        if (r6 != 0) goto L_0x1319;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x12fb:
        r6 = "DCGS";	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r7 = "DGG";	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = r6.equals(r7);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        if (r6 != 0) goto L_0x1319;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x1305:
        r6 = "DCGGS";	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r7 = "DGG";	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = r6.equals(r7);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        if (r6 != 0) goto L_0x1319;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x130f:
        r6 = "CG";	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r7 = "DGG";	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = r6.equals(r7);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        if (r6 == 0) goto L_0x13b8;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x1319:
        r6 = "gsm.sim.selectnetwork";	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r46 = android.os.SystemProperties.get(r6);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = new java.lang.StringBuilder;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6.<init>();	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r7 = "simselswitch = ";	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = r6.append(r7);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r46;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = r6.append(r0);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = r6.toString();	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0.log(r6);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = "CDMA";	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r46;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = r6.equals(r0);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        if (r6 == 0) goto L_0x13a0;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x1343:
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = r0.phone;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = r6.getPhoneId();	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        if (r6 != 0) goto L_0x13a0;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x134d:
        r6 = "do not set PROPERTY_ICC_OPERATOR_NUMERIC in case of slot1 cdma switching";	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0.log(r6);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x1354:
        throw r4;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x1355:
        r25 = r25 + 1;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        goto L_0x1267;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x1359:
        r18 = move-exception;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = 0;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0.mMncLength = r6;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = new java.lang.StringBuilder;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6.<init>();	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r7 = "Corrupt IMSI! setting8 mMncLength=";	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = r6.append(r7);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r7 = r0.mMncLength;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = r6.append(r7);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = r6.toString();	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0.loge(r6);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        goto L_0x12db;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x137d:
        r6 = 0;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0.mMncLength = r6;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = new java.lang.StringBuilder;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6.<init>();	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r7 = "MNC length not present in EF_AD setting9 mMncLength=";	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = r6.append(r7);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r7 = r0.mMncLength;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = r6.append(r7);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = r6.toString();	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0.log(r6);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        goto L_0x12db;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x13a0:
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = r0.mContext;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r7 = r0.mImsi;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r8 = 0;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r9 = r0.mMncLength;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r9 = r9 + 3;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r7 = r7.substring(r8, r9);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r8 = 0;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        com.android.internal.telephony.MccTable.updateMccMncConfiguration(r6, r7, r8);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        goto L_0x1354;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x13b8:
        r6 = new java.lang.StringBuilder;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6.<init>();	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r7 = "update mccmnc=";	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = r6.append(r7);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r7 = r0.mImsi;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r8 = 0;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r9 = r0.mMncLength;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r9 = r9 + 3;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r7 = r7.substring(r8, r9);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = r6.append(r7);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = r6.toString();	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0.log(r6);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = r0.mContext;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r7 = r0.mImsi;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r8 = 0;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r9 = r0.mMncLength;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r9 = r9 + 3;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r7 = r7.substring(r8, r9);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r8 = 0;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        com.android.internal.telephony.MccTable.updateMccMncConfiguration(r6, r7, r8);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        goto L_0x1354;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x13f8:
        r31 = 1;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r55;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r12 = r0.obj;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r12 = (android.os.AsyncResult) r12;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = 0;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0.getSpnFsm(r4, r12);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        goto L_0x004b;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x1408:
        r31 = 1;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r55;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r12 = r0.obj;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r12 = (android.os.AsyncResult) r12;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r12.result;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = (byte[]) r4;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r4;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = (byte[]) r0;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r17 = r0;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r12.exception;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        if (r4 == 0) goto L_0x1422;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x141d:
        r54.notifyCallForwardIndication();	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        goto L_0x004b;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x1422:
        r4 = new java.lang.StringBuilder;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4.<init>();	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = "EF_CFF_CPHS: ";	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.append(r6);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = com.android.internal.telephony.uicc.IccUtils.bytesToHexString(r17);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.append(r6);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.toString();	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0.log(r4);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r17;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r1 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r1.mEfCff = r0;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = com.sec.android.app.CscFeature.getInstance();	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = "CscFeature_RIL_SupportOrangeCPHS";	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.getEnableStatus(r6);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        if (r4 == 0) goto L_0x14cd;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x1450:
        r15 = 0;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = 0;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r17[r4];	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4 & 15;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = 10;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        if (r4 != r6) goto L_0x14c7;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x145a:
        r52 = 1;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x145c:
        r51 = 0;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r17;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r0.length;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = 1;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        if (r4 <= r6) goto L_0x1471;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x1464:
        r4 = 1;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r17[r4];	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4 & 240;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4 >> 4;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = 10;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        if (r4 != r6) goto L_0x14ca;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x146f:
        r51 = 1;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x1471:
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r0.voicecallForwardingEnabled;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        if (r4 != 0) goto L_0x147f;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x1477:
        if (r52 == 0) goto L_0x147f;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x1479:
        r4 = 1;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0.voicecallForwardingEnabled = r4;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r15 = 1;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x147f:
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r0.videocallForwardingEnabled;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        if (r4 != 0) goto L_0x148d;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x1485:
        if (r51 == 0) goto L_0x148d;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x1487:
        r4 = 1;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0.videocallForwardingEnabled = r4;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r15 = 1;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x148d:
        if (r15 == 0) goto L_0x004b;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x148f:
        r4 = new java.lang.StringBuilder;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4.<init>();	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = "Update Orange CFF CPHS : voicecall - ";	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.append(r6);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = r0.voicecallForwardingEnabled;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.append(r6);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = ", videocall - ";	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.append(r6);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = r0.videocallForwardingEnabled;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.append(r6);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.toString();	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0.log(r4);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r0.mRecordsEventsRegistrants;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = 1;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = java.lang.Integer.valueOf(r6);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4.notifyResult(r6);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        goto L_0x004b;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x14c7:
        r52 = 0;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        goto L_0x145c;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x14ca:
        r51 = 0;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        goto L_0x1471;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x14cd:
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r0.mEfCfis;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r0.validEfCfis(r4);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        if (r4 != 0) goto L_0x150f;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x14d9:
        r4 = 0;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r17[r4];	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4 & 15;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = 10;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        if (r4 != r6) goto L_0x150b;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x14e2:
        r4 = 1;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x14e3:
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0.voicecallForwardingEnabled = r4;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r17;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r0.length;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = 1;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        if (r4 <= r6) goto L_0x14fd;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x14ed:
        r4 = 1;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r17[r4];	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4 & 240;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4 >> 4;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = 10;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        if (r4 != r6) goto L_0x150d;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x14f8:
        r4 = 1;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x14f9:
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0.videocallForwardingEnabled = r4;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x14fd:
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r0.mRecordsEventsRegistrants;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = 1;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = java.lang.Integer.valueOf(r6);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4.notifyResult(r6);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        goto L_0x004b;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x150b:
        r4 = 0;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        goto L_0x14e3;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x150d:
        r4 = 0;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        goto L_0x14f9;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x150f:
        r4 = "EVENT_GET_CFF_DONE: EF_CFIS is valid, ignoring EF_CFF_CPHS";	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0.log(r4);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        goto L_0x004b;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x1518:
        r31 = 1;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r55;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r12 = r0.obj;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r12 = (android.os.AsyncResult) r12;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r12.result;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = (byte[]) r4;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r4;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = (byte[]) r0;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r17 = r0;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r12.exception;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        if (r4 != 0) goto L_0x004b;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x152d:
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r1 = r17;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0.parseEfSpdi(r1);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        goto L_0x004b;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x1536:
        r0 = r55;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r12 = r0.obj;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r12 = (android.os.AsyncResult) r12;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r12.exception;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        if (r4 == 0) goto L_0x004b;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x1540:
        r4 = "update failed. ";	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = r12.exception;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0.logw(r4, r6);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        goto L_0x004b;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x154b:
        r31 = 1;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r55;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r12 = r0.obj;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r12 = (android.os.AsyncResult) r12;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r12.exception;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        if (r4 != 0) goto L_0x004b;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x1557:
        r4 = r12.result;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = (java.util.ArrayList) r4;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0.handlePNN(r4);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        goto L_0x004b;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x1562:
        r31 = 1;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r55;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r12 = r0.obj;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r12 = (android.os.AsyncResult) r12;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r12.exception;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        if (r4 != 0) goto L_0x004b;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x156e:
        r4 = r12.result;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = (java.util.ArrayList) r4;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0.handleSmses(r4);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        goto L_0x004b;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x1579:
        r4 = "ENF";	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = new java.lang.StringBuilder;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6.<init>();	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r7 = "marked read: sms ";	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = r6.append(r7);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r55;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r7 = r0.arg1;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = r6.append(r7);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = r6.toString();	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        android.telephony.Rlog.i(r4, r6);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        goto L_0x004b;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x1597:
        r31 = 0;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r55;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r12 = r0.obj;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r12 = (android.os.AsyncResult) r12;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r12.result;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = (int[]) r4;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r4;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = (int[]) r0;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r27 = r0;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r12.exception;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        if (r4 != 0) goto L_0x15b2;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x15ac:
        r0 = r27;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r0.length;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = 1;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        if (r4 == r6) goto L_0x15db;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x15b2:
        r4 = new java.lang.StringBuilder;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4.<init>();	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = "Error on SMS_ON_SIM with exp ";	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.append(r6);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = r12.exception;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.append(r6);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = " length ";	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.append(r6);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r27;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = r0.length;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.append(r6);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.toString();	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0.loge(r4);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        goto L_0x004b;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x15db:
        r4 = new java.lang.StringBuilder;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4.<init>();	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = "READ EF_SMS RECORD index=";	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.append(r6);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = 0;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = r27[r6];	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.append(r6);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.toString();	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0.log(r4);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r0.mFh;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = 28476; // 0x6f3c float:3.9903E-41 double:1.4069E-319;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r7 = 0;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r7 = r27[r7];	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r8 = 22;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r8 = r0.obtainMessage(r8);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4.loadEFLinearFixed(r6, r7, r8);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        goto L_0x004b;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x160c:
        r31 = 0;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r55;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r12 = r0.obj;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r12 = (android.os.AsyncResult) r12;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r12.exception;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        if (r4 != 0) goto L_0x1625;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x1618:
        r4 = r12.result;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = (byte[]) r4;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = (byte[]) r4;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0.handleSms(r4);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        goto L_0x004b;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x1625:
        r4 = new java.lang.StringBuilder;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4.<init>();	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = "Error on GET_SMS with exp ";	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.append(r6);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = r12.exception;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.append(r6);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.toString();	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0.loge(r4);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        goto L_0x004b;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x1641:
        r31 = 1;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = new java.lang.StringBuilder;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4.<init>();	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = "mIccType =";	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.append(r6);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = r0.mIccType;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.append(r6);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.toString();	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0.logi(r4);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r55;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r12 = r0.obj;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r12 = (android.os.AsyncResult) r12;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r12.result;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = (byte[]) r4;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r4;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = (byte[]) r0;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r17 = r0;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r12.exception;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        if (r4 != 0) goto L_0x004b;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x1672:
        r4 = new com.android.internal.telephony.uicc.UsimServiceTable;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r17;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4.<init>(r0);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0.mUsimServiceTable = r4;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = "1";	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = r0.mIccType;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.equals(r6);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        if (r4 == 0) goto L_0x1709;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x1689:
        r4 = "SST read done.";	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0.log(r4);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x1690:
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r1 = r17;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0.checkSMSPavailable(r1);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r1 = r17;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0.checkEONSavailable(r1);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r1 = r17;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0.checkFDNavailable(r1);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r1 = r17;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0.checkSDNavailable(r1);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r1 = r17;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0.checkCHV1available(r1);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r1 = r17;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0.checkSPNavailable(r1);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r1 = r17;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0.checkMSISDNavailable(r1);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r1 = r17;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0.checkSMSavailable(r1);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r1 = r17;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0.checkMBDNavailable(r1);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r1 = r17;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0.checkMWISavailable(r1);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r1 = r17;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0.checkCFISavailable(r1);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = "ATT";	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = "ro.csc.sales_code";	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = android.os.SystemProperties.get(r6);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.equals(r6);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        if (r4 != 0) goto L_0x16f9;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x16eb:
        r4 = "AIO";	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = "ro.csc.sales_code";	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = android.os.SystemProperties.get(r6);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.equals(r6);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        if (r4 == 0) goto L_0x1700;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x16f9:
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r1 = r17;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0.checkOCSGLAvailable(r1);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x1700:
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r1 = r17;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0.checkPSISMSCavailable(r1);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        goto L_0x004b;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x1709:
        r4 = "2";	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = r0.mIccType;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.equals(r6);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        if (r4 == 0) goto L_0x1690;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x1715:
        r4 = new java.lang.StringBuilder;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4.<init>();	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = "UST : ";	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.append(r6);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = r0.mUsimServiceTable;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.append(r6);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.toString();	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0.log(r4);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        goto L_0x1690;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x1733:
        r31 = 1;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r55;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r12 = r0.obj;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r12 = (android.os.AsyncResult) r12;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r12.exception;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        if (r4 != 0) goto L_0x004b;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x173f:
        r4 = r12.result;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = (byte[]) r4;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = (byte[]) r4;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0.mCphsInfo = r4;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = new java.lang.StringBuilder;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4.<init>();	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = "iCPHS: ";	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.append(r6);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = r0.mCphsInfo;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = com.android.internal.telephony.uicc.IccUtils.bytesToHexString(r6);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.append(r6);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.toString();	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0.log(r4);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        goto L_0x004b;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x176b:
        r31 = 0;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r55;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r12 = r0.obj;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r12 = (android.os.AsyncResult) r12;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = new java.lang.StringBuilder;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4.<init>();	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = "EVENT_SET_MBDN_DONE ex:";	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.append(r6);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = r12.exception;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.append(r6);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.toString();	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0.log(r4);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r12.exception;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        if (r4 != 0) goto L_0x17a1;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x1791:
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r0.mNewVoiceMailNum;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0.mVoiceMailNum = r4;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r0.mNewVoiceMailTag;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0.mVoiceMailTag = r4;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x17a1:
        r4 = r54.isCphsMailboxEnabled();	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        if (r4 == 0) goto L_0x17fb;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x17a7:
        r5 = new com.android.internal.telephony.uicc.AdnRecord;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r0.mVoiceMailTag;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = r0.mVoiceMailNum;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r5.<init>(r4, r6);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r12.userObj;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r39 = r0;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r39 = (android.os.Message) r39;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r12.exception;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        if (r4 != 0) goto L_0x17dd;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x17be:
        r4 = r12.userObj;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        if (r4 == 0) goto L_0x17dd;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x17c2:
        r4 = r12.userObj;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = (android.os.Message) r4;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = android.os.AsyncResult.forMessage(r4);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = 0;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4.exception = r6;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r12.userObj;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = (android.os.Message) r4;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4.sendToTarget();	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = "Callback with MBDN successful.";	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0.log(r4);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r39 = 0;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x17dd:
        r4 = new com.android.internal.telephony.uicc.AdnRecordLoader;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = r0.mFh;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4.<init>(r6);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = 28439; // 0x6f17 float:3.9852E-41 double:1.40507E-319;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r7 = 28490; // 0x6f4a float:3.9923E-41 double:1.4076E-319;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r8 = 1;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r9 = 0;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r10 = 25;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r1 = r39;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r10 = r0.obtainMessage(r10, r1);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4.updateEF(r5, r6, r7, r8, r9, r10);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        goto L_0x004b;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x17fb:
        r4 = r12.userObj;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        if (r4 == 0) goto L_0x004b;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x17ff:
        r43 = android.content.res.Resources.getSystem();	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r12.exception;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        if (r4 == 0) goto L_0x182c;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x1807:
        r4 = 17956988; // 0x112007c float:2.6816312E-38 double:8.871931E-317;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r43;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r0.getBoolean(r4);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        if (r4 == 0) goto L_0x182c;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x1812:
        r4 = r12.userObj;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = (android.os.Message) r4;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = android.os.AsyncResult.forMessage(r4);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = new com.android.internal.telephony.uicc.IccVmNotSupportedException;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r7 = "Update SIM voice mailbox error";	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6.<init>(r7);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4.exception = r6;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x1823:
        r4 = r12.userObj;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = (android.os.Message) r4;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4.sendToTarget();	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        goto L_0x004b;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x182c:
        r4 = r12.userObj;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = (android.os.Message) r4;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = android.os.AsyncResult.forMessage(r4);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = r12.exception;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4.exception = r6;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        goto L_0x1823;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x1839:
        r31 = 0;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r55;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r12 = r0.obj;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r12 = (android.os.AsyncResult) r12;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r12.exception;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        if (r4 != 0) goto L_0x1875;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x1845:
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r0.mNewVoiceMailNum;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0.mVoiceMailNum = r4;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r0.mNewVoiceMailTag;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0.mVoiceMailTag = r4;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x1855:
        r4 = r12.userObj;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        if (r4 == 0) goto L_0x004b;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x1859:
        r4 = "Callback with CPHS MB successful.";	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0.log(r4);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r12.userObj;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = (android.os.Message) r4;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = android.os.AsyncResult.forMessage(r4);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = r12.exception;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4.exception = r6;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r12.userObj;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = (android.os.Message) r4;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4.sendToTarget();	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        goto L_0x004b;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x1875:
        r4 = new java.lang.StringBuilder;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4.<init>();	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = "Set CPHS MailBox with exception: ";	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.append(r6);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = r12.exception;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.append(r6);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.toString();	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0.log(r4);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        goto L_0x1855;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x1890:
        r31 = 0;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r55;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r12 = r0.obj;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r12 = (android.os.AsyncResult) r12;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = new java.lang.StringBuilder;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4.<init>();	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = "Sim REFRESH with exception: ";	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.append(r6);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = r12.exception;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.append(r6);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.toString();	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0.log(r4);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r12.exception;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        if (r4 != 0) goto L_0x004b;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x18b6:
        r4 = r12.result;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = (com.android.internal.telephony.uicc.IccRefreshResponse) r4;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0.handleSimRefresh(r4);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        goto L_0x004b;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x18c1:
        r31 = 1;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r55;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r12 = r0.obj;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r12 = (android.os.AsyncResult) r12;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r12.result;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = (byte[]) r4;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r4;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = (byte[]) r0;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r17 = r0;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r12.exception;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        if (r4 == 0) goto L_0x18f3;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x18d6:
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r0.mFh;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = 28435; // 0x6f13 float:3.9846E-41 double:1.4049E-319;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r7 = 24;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r7 = r0.obtainMessage(r7);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4.loadEFTransparent(r6, r7);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r0.mRecordsToLoad;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4 + 1;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0.mRecordsToLoad = r4;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        goto L_0x004b;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x18f3:
        r4 = new java.lang.StringBuilder;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4.<init>();	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = "EF_CFIS: ";	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.append(r6);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = com.android.internal.telephony.uicc.IccUtils.bytesToHexString(r17);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.append(r6);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.toString();	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0.log(r4);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r1 = r17;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r0.validEfCfis(r1);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        if (r4 == 0) goto L_0x199a;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x1919:
        r0 = r17;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r1 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r1.mEfCfis = r0;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = 1;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r17[r4];	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4 & 1;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        if (r4 == 0) goto L_0x1996;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x1926:
        r4 = 1;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x1927:
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0.voicecallForwardingEnabled = r4;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = 1;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r17[r4];	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4 & 16;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        if (r4 == 0) goto L_0x1998;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x1932:
        r4 = 1;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x1933:
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0.videocallForwardingEnabled = r4;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = new java.lang.StringBuilder;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4.<init>();	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = "EF_CFIS: callForwardingEnabled=";	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.append(r6);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = r0.voicecallForwardingEnabled;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.append(r6);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = "EF_CFIS: videocallForwardingEnabled=";	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.append(r6);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = r0.videocallForwardingEnabled;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.append(r6);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.toString();	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0.log(r4);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r0.mRecordsEventsRegistrants;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = 1;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = java.lang.Integer.valueOf(r6);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4.notifyResult(r6);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = com.sec.android.app.CscFeature.getInstance();	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = "CscFeature_RIL_SupportOrangeCPHS";	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.getEnableStatus(r6);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        if (r4 == 0) goto L_0x004b;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x1979:
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r0.mFh;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = 28435; // 0x6f13 float:3.9846E-41 double:1.4049E-319;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r7 = 24;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r7 = r0.obtainMessage(r7);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4.loadEFTransparent(r6, r7);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r0.mRecordsToLoad;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4 + 1;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0.mRecordsToLoad = r4;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        goto L_0x004b;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x1996:
        r4 = 0;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        goto L_0x1927;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x1998:
        r4 = 0;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        goto L_0x1933;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x199a:
        r4 = new java.lang.StringBuilder;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4.<init>();	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = "EF_CFIS: invalid data=";	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.append(r6);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = com.android.internal.telephony.uicc.IccUtils.bytesToHexString(r17);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.append(r6);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.toString();	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0.log(r4);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r0.mFh;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = 28435; // 0x6f13 float:3.9846E-41 double:1.4049E-319;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r7 = 24;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r7 = r0.obtainMessage(r7);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4.loadEFTransparent(r6, r7);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r0.mRecordsToLoad;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4 + 1;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0.mRecordsToLoad = r4;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        goto L_0x004b;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x19d3:
        r31 = 1;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r55;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r12 = r0.obj;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r12 = (android.os.AsyncResult) r12;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r12.exception;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        if (r4 == 0) goto L_0x19fb;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x19df:
        r4 = new java.lang.StringBuilder;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4.<init>();	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = "Exception in fetching EF_CSP data ";	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.append(r6);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = r12.exception;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.append(r6);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.toString();	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0.loge(r4);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        goto L_0x004b;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x19fb:
        r4 = r12.result;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = (byte[]) r4;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r4;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = (byte[]) r0;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r17 = r0;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = new java.lang.StringBuilder;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4.<init>();	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = "EF_CSP: ";	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.append(r6);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = com.android.internal.telephony.uicc.IccUtils.bytesToHexString(r17);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.append(r6);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.toString();	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0.log(r4);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r1 = r17;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0.handleEfCspData(r1);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        goto L_0x004b;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x1a29:
        r31 = 1;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r55;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r12 = r0.obj;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r12 = (android.os.AsyncResult) r12;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r12.exception;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        if (r4 == 0) goto L_0x1a43;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x1a35:
        r4 = "Invalid or missing EF_IMSI_M";	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0.loge(r4);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = 0;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0.mSktImsiM = r4;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        goto L_0x004b;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x1a43:
        r4 = r12.result;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = (byte[]) r4;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r4;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = (byte[]) r0;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r17 = r0;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r1 = r17;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r26 = r0.handleSktEf(r1);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r26;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r1 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r1.mSktImsiM = r0;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        goto L_0x004b;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x1a5c:
        r31 = 1;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r55;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r12 = r0.obj;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r12 = (android.os.AsyncResult) r12;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r12.exception;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        if (r4 == 0) goto L_0x1a76;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x1a68:
        r4 = "Invalid or missing EF_SKT_IRM]";	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0.loge(r4);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = 0;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0.mSktIrm = r4;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        goto L_0x004b;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x1a76:
        r4 = r12.result;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = (byte[]) r4;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r4;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = (byte[]) r0;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r17 = r0;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r1 = r17;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r47 = r0.handleSktEf(r1);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r47;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r1 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r1.mSktIrm = r0;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        goto L_0x004b;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x1a8f:
        r31 = 1;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r55;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r12 = r0.obj;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r12 = (android.os.AsyncResult) r12;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r12.result;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = (byte[]) r4;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r4;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = (byte[]) r0;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r17 = r0;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r23 = "";	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r12.exception;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        if (r4 == 0) goto L_0x1b2c;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x1aa6:
        r4 = new java.lang.StringBuilder;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4.<init>();	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = "Exception in get GID1 ";	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.append(r6);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = r12.exception;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.append(r6);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.toString();	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0.loge(r4);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = 0;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0.mGid1 = r4;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x1ac5:
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r0.mContext;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r48 = android.preference.PreferenceManager.getDefaultSharedPreferences(r4);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r19 = r48.edit();	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r33 = "key_gid1";	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r0.phone;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.getPhoneId();	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        if (r4 == 0) goto L_0x1b04;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x1add:
        r4 = new java.lang.StringBuilder;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4.<init>();	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = "key";	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.append(r6);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = r0.phone;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = r6.getPhoneId();	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = r6 + 1;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = java.lang.Integer.toString(r6);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.append(r6);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = "_gid1";	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.append(r6);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r33 = r4.toString();	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x1b04:
        r0 = r19;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r1 = r33;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r2 = r23;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0.putString(r1, r2);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r19.commit();	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = new java.lang.StringBuilder;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4.<init>();	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = "SimRecord: Load gid1 done: ";	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.append(r6);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r23;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.append(r0);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.toString();	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0.log(r4);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        goto L_0x004b;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x1b2c:
        r4 = com.android.internal.telephony.uicc.IccUtils.bytesToHexString(r17);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0.mGid1 = r4;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r44 = 0;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r14 = 0;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = 0;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r17[r4];	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4 >> 4;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r14 = r4 & 15;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r44 = r14 * 16;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = 0;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r17[r4];	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r14 = r4 & 15;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r44 = r44 + r14;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r23 = java.lang.Integer.toString(r44);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = 255; // 0xff float:3.57E-43 double:1.26E-321;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r44;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        if (r0 != r4) goto L_0x1ac5;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x1b51:
        r23 = "";	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        goto L_0x1ac5;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x1b55:
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r0.mCi;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = r0.mParentApp;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = r6.getAid();	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r7 = 3;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r7 = r0.obtainMessage(r7);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4.getIMSIForApp(r6, r7);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r0.mRecordsToLoad;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4 + 1;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0.mRecordsToLoad = r4;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        goto L_0x004b;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x1b77:
        r31 = 1;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r55;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r12 = r0.obj;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r12 = (android.os.AsyncResult) r12;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r12.result;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = (byte[]) r4;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r4;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = (byte[]) r0;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r17 = r0;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r12.exception;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        if (r4 != 0) goto L_0x004b;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x1b8c:
        r4 = new java.lang.StringBuilder;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4.<init>();	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = "EF_SMSP: ";	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.append(r6);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = com.android.internal.telephony.uicc.IccUtils.bytesToHexString(r17);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.append(r6);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.toString();	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0.log(r4);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r17;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r0.length;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4 + -1;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r17[r4];	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0.mValidityPeriod = r4;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = new java.lang.StringBuilder;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4.<init>();	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = "mValidityPeriod: ";	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.append(r6);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = r0.mValidityPeriod;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.append(r6);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.toString();	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0.log(r4);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r30 = new android.content.Intent;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = "android.intent.action.VALIDITY_PERIOD";	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r30;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0.<init>(r4);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = "mValidityPeriod";	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = r0.mValidityPeriod;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r30;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0.putExtra(r4, r6);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r0.mContext;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r30;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4.sendBroadcast(r0);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = "intent VALIDITY_PERIOD broadcasted";	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0.log(r4);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        goto L_0x004b;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x1bf5:
        r4 = "[handleMessage] EVENT_GET_SPN_CPHS_DONE ";	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0.logi(r4);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r31 = 1;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r55;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r12 = r0.obj;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r12 = (android.os.AsyncResult) r12;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = 0;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0.spn_cphs = r4;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        if (r12 == 0) goto L_0x1c65;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x1c0b:
        r4 = r12.exception;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        if (r4 != 0) goto L_0x1c65;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x1c0f:
        r4 = 1;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0.IsOnsExist = r4;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r12.result;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = (byte[]) r4;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r4;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = (byte[]) r0;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r17 = r0;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = 0;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r17;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = r0.length;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r17;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = com.android.internal.telephony.uicc.IccUtils.adnStringFieldToString(r0, r4, r6);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0.spn_cphs = r4;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = new java.lang.StringBuilder;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4.<init>();	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = "Load EF_SPN_CPHS: ";	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.append(r6);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = r0.mSpn;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.append(r6);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.toString();	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0.log(r4);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = new java.lang.StringBuilder;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4.<init>();	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = " Load EF_SPN_CPHS: ";	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.append(r6);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = r0.spn_cphs;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.append(r6);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.toString();	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0.logi(r4);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        goto L_0x004b;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x1c65:
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r0.mFh;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = 28440; // 0x6f18 float:3.9853E-41 double:1.4051E-319;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r7 = 36;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r7 = r0.obtainMessage(r7);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4.loadEFTransparent(r6, r7);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r0.mRecordsToLoad;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4 + 1;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0.mRecordsToLoad = r4;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        goto L_0x004b;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x1c82:
        r4 = "[handleMessage] EVENT_GET_SPN_SHORT_CPHS_DONE ";	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0.logi(r4);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r31 = 1;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r55;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r12 = r0.obj;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r12 = (android.os.AsyncResult) r12;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = 0;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0.spn_cphs = r4;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        if (r12 == 0) goto L_0x004b;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x1c98:
        r4 = r12.exception;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        if (r4 != 0) goto L_0x004b;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x1c9c:
        r4 = 1;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0.IsOnsExist = r4;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r12.result;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = (byte[]) r4;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r4;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = (byte[]) r0;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r17 = r0;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = 0;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r17;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = r0.length;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r17;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = com.android.internal.telephony.uicc.IccUtils.adnStringFieldToString(r0, r4, r6);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0.spn_cphs = r4;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = new java.lang.StringBuilder;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4.<init>();	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = "Load EF_SPN_SHORT_CPHS: ";	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.append(r6);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = r0.spn_cphs;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.append(r6);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.toString();	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0.log(r4);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = new java.lang.StringBuilder;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4.<init>();	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = " Load EF_SPN_SHORT_CPHS: ";	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.append(r6);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = r0.spn_cphs;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.append(r6);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.toString();	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0.logi(r4);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        goto L_0x004b;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x1cf2:
        r4 = "[handleMessage] EVENT_GET_OPL_DONE ";	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0.logi(r4);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r31 = 1;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r55;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r12 = r0.obj;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r12 = (android.os.AsyncResult) r12;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r12.exception;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        if (r4 != 0) goto L_0x004b;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x1d05:
        r4 = r12.result;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = (java.util.ArrayList) r4;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0.handleOPL(r4);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        goto L_0x004b;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x1d10:
        r31 = 1;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r55;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r12 = r0.obj;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r12 = (android.os.AsyncResult) r12;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r12.result;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = (byte[]) r4;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r4;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = (byte[]) r0;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r17 = r0;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r12.exception;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        if (r4 == 0) goto L_0x1d33;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x1d25:
        r4 = "getting EF_PERSO have exception !!!! ";	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0.loge(r4);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = 0;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0.isAvailableO2PERSO = r4;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        goto L_0x004b;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x1d33:
        r4 = new java.lang.StringBuilder;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4.<init>();	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = "EF_PERSO: ";	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.append(r6);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = com.android.internal.telephony.uicc.IccUtils.bytesToHexString(r17);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.append(r6);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.toString();	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0.logi(r4);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r17;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r1 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r1.perso = r0;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = 1;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0.isAvailableO2PERSO = r4;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        goto L_0x004b;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x1d5c:
        r31 = 1;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r55;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r12 = r0.obj;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r12 = (android.os.AsyncResult) r12;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r12.exception;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        if (r4 == 0) goto L_0x1d71;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x1d68:
        r4 = "Invalid or missing EF[masterImsi]";	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0.loge(r4);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        goto L_0x004b;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x1d71:
        r4 = r12.result;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = (byte[]) r4;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r4;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = (byte[]) r0;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r17 = r0;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = SHIP_BUILD;	 Catch:{ StringIndexOutOfBoundsException -> 0x1da6 }
        if (r4 != 0) goto L_0x004b;	 Catch:{ StringIndexOutOfBoundsException -> 0x1da6 }
    L_0x1d7e:
        r4 = new java.lang.StringBuilder;	 Catch:{ StringIndexOutOfBoundsException -> 0x1da6 }
        r4.<init>();	 Catch:{ StringIndexOutOfBoundsException -> 0x1da6 }
        r6 = "[masterImsi]: ";	 Catch:{ StringIndexOutOfBoundsException -> 0x1da6 }
        r4 = r4.append(r6);	 Catch:{ StringIndexOutOfBoundsException -> 0x1da6 }
        r6 = 2;	 Catch:{ StringIndexOutOfBoundsException -> 0x1da6 }
        r7 = 8;	 Catch:{ StringIndexOutOfBoundsException -> 0x1da6 }
        r0 = r17;	 Catch:{ StringIndexOutOfBoundsException -> 0x1da6 }
        r6 = com.android.internal.telephony.uicc.IccUtils.bcdToString(r0, r6, r7);	 Catch:{ StringIndexOutOfBoundsException -> 0x1da6 }
        r7 = 1;	 Catch:{ StringIndexOutOfBoundsException -> 0x1da6 }
        r6 = r6.substring(r7);	 Catch:{ StringIndexOutOfBoundsException -> 0x1da6 }
        r4 = r4.append(r6);	 Catch:{ StringIndexOutOfBoundsException -> 0x1da6 }
        r4 = r4.toString();	 Catch:{ StringIndexOutOfBoundsException -> 0x1da6 }
        r0 = r54;	 Catch:{ StringIndexOutOfBoundsException -> 0x1da6 }
        r0.log(r4);	 Catch:{ StringIndexOutOfBoundsException -> 0x1da6 }
        goto L_0x004b;
    L_0x1da6:
        r18 = move-exception;
        r4 = "MASTER_IMSI was not exist in this card";	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0.loge(r4);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        goto L_0x004b;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x1db0:
        r31 = 1;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r55;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r12 = r0.obj;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r12 = (android.os.AsyncResult) r12;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r12.exception;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        if (r4 == 0) goto L_0x1dc5;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x1dbc:
        r4 = "Invalid or missing EF[sponImsi1]";	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0.loge(r4);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        goto L_0x004b;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x1dc5:
        r4 = r12.result;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = (byte[]) r4;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r4;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = (byte[]) r0;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r17 = r0;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r0.mSponImsi;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = 0;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r7 = new java.lang.String;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r7.<init>();	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4[r6] = r7;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ Exception -> 0x1e41 }
        r4 = r0.mSponImsi;	 Catch:{ Exception -> 0x1e41 }
        r6 = 0;	 Catch:{ Exception -> 0x1e41 }
        r7 = new java.lang.StringBuilder;	 Catch:{ Exception -> 0x1e41 }
        r7.<init>();	 Catch:{ Exception -> 0x1e41 }
        r8 = 2;	 Catch:{ Exception -> 0x1e41 }
        r8 = r17[r8];	 Catch:{ Exception -> 0x1e41 }
        r8 = r8 >> 4;	 Catch:{ Exception -> 0x1e41 }
        r8 = r8 & 15;	 Catch:{ Exception -> 0x1e41 }
        r7 = r7.append(r8);	 Catch:{ Exception -> 0x1e41 }
        r8 = 3;	 Catch:{ Exception -> 0x1e41 }
        r9 = 1;	 Catch:{ Exception -> 0x1e41 }
        r9 = r17[r9];	 Catch:{ Exception -> 0x1e41 }
        r9 = r9 + -1;	 Catch:{ Exception -> 0x1e41 }
        r0 = r17;	 Catch:{ Exception -> 0x1e41 }
        r8 = com.android.internal.telephony.uicc.IccUtils.bcdToString(r0, r8, r9);	 Catch:{ Exception -> 0x1e41 }
        r7 = r7.append(r8);	 Catch:{ Exception -> 0x1e41 }
        r8 = ";";	 Catch:{ Exception -> 0x1e41 }
        r7 = r7.append(r8);	 Catch:{ Exception -> 0x1e41 }
        r8 = 555; // 0x22b float:7.78E-43 double:2.74E-321;	 Catch:{ Exception -> 0x1e41 }
        r0 = r17;	 Catch:{ Exception -> 0x1e41 }
        r9 = r0.length;	 Catch:{ Exception -> 0x1e41 }
        r9 = r9 + -1;	 Catch:{ Exception -> 0x1e41 }
        r0 = r17;	 Catch:{ Exception -> 0x1e41 }
        r8 = com.android.internal.telephony.uicc.IccUtils.adnStringFieldToString(r0, r8, r9);	 Catch:{ Exception -> 0x1e41 }
        r7 = r7.append(r8);	 Catch:{ Exception -> 0x1e41 }
        r7 = r7.toString();	 Catch:{ Exception -> 0x1e41 }
        r4[r6] = r7;	 Catch:{ Exception -> 0x1e41 }
    L_0x1e1c:
        r4 = SHIP_BUILD;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        if (r4 != 0) goto L_0x004b;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x1e20:
        r4 = new java.lang.StringBuilder;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4.<init>();	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = "[sponImsi1]: ";	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.append(r6);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = r0.mSponImsi;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r7 = 0;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = r6[r7];	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.append(r6);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.toString();	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0.log(r4);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        goto L_0x004b;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x1e41:
        r21 = move-exception;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = new java.lang.StringBuilder;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4.<init>();	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = "[sponImsi1] Ex -";	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.append(r6);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r21;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.append(r0);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.toString();	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0.loge(r4);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r21.printStackTrace();	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r0.mSponImsi;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = 0;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r7 = "";	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4[r6] = r7;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        goto L_0x1e1c;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x1e69:
        r31 = 1;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r55;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r12 = r0.obj;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r12 = (android.os.AsyncResult) r12;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r12.exception;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        if (r4 == 0) goto L_0x1e7e;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x1e75:
        r4 = "Invalid or missing EF[sponImsi2]";	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0.loge(r4);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        goto L_0x004b;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x1e7e:
        r4 = r12.result;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = (byte[]) r4;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r4;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = (byte[]) r0;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r17 = r0;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r0.mSponImsi;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = 1;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r7 = new java.lang.String;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r7.<init>();	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4[r6] = r7;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ Exception -> 0x1efa }
        r4 = r0.mSponImsi;	 Catch:{ Exception -> 0x1efa }
        r6 = 1;	 Catch:{ Exception -> 0x1efa }
        r7 = new java.lang.StringBuilder;	 Catch:{ Exception -> 0x1efa }
        r7.<init>();	 Catch:{ Exception -> 0x1efa }
        r8 = 2;	 Catch:{ Exception -> 0x1efa }
        r8 = r17[r8];	 Catch:{ Exception -> 0x1efa }
        r8 = r8 >> 4;	 Catch:{ Exception -> 0x1efa }
        r8 = r8 & 15;	 Catch:{ Exception -> 0x1efa }
        r7 = r7.append(r8);	 Catch:{ Exception -> 0x1efa }
        r8 = 3;	 Catch:{ Exception -> 0x1efa }
        r9 = 1;	 Catch:{ Exception -> 0x1efa }
        r9 = r17[r9];	 Catch:{ Exception -> 0x1efa }
        r9 = r9 + -1;	 Catch:{ Exception -> 0x1efa }
        r0 = r17;	 Catch:{ Exception -> 0x1efa }
        r8 = com.android.internal.telephony.uicc.IccUtils.bcdToString(r0, r8, r9);	 Catch:{ Exception -> 0x1efa }
        r7 = r7.append(r8);	 Catch:{ Exception -> 0x1efa }
        r8 = ";";	 Catch:{ Exception -> 0x1efa }
        r7 = r7.append(r8);	 Catch:{ Exception -> 0x1efa }
        r8 = 555; // 0x22b float:7.78E-43 double:2.74E-321;	 Catch:{ Exception -> 0x1efa }
        r0 = r17;	 Catch:{ Exception -> 0x1efa }
        r9 = r0.length;	 Catch:{ Exception -> 0x1efa }
        r9 = r9 + -1;	 Catch:{ Exception -> 0x1efa }
        r0 = r17;	 Catch:{ Exception -> 0x1efa }
        r8 = com.android.internal.telephony.uicc.IccUtils.adnStringFieldToString(r0, r8, r9);	 Catch:{ Exception -> 0x1efa }
        r7 = r7.append(r8);	 Catch:{ Exception -> 0x1efa }
        r7 = r7.toString();	 Catch:{ Exception -> 0x1efa }
        r4[r6] = r7;	 Catch:{ Exception -> 0x1efa }
    L_0x1ed5:
        r4 = SHIP_BUILD;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        if (r4 != 0) goto L_0x004b;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x1ed9:
        r4 = new java.lang.StringBuilder;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4.<init>();	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = "[sponImsi2]: ";	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.append(r6);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = r0.mSponImsi;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r7 = 1;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = r6[r7];	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.append(r6);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.toString();	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0.log(r4);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        goto L_0x004b;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x1efa:
        r21 = move-exception;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = new java.lang.StringBuilder;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4.<init>();	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = "[sponImsi2] Ex -";	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.append(r6);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r21;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.append(r0);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.toString();	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0.loge(r4);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r21.printStackTrace();	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r0.mSponImsi;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = 1;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r7 = "";	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4[r6] = r7;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        goto L_0x1ed5;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x1f22:
        r31 = 1;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r55;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r12 = r0.obj;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r12 = (android.os.AsyncResult) r12;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r12.exception;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        if (r4 == 0) goto L_0x1f37;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x1f2e:
        r4 = "Invalid or missing EF[sponImsi3]";	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0.loge(r4);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        goto L_0x004b;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x1f37:
        r4 = r12.result;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = (byte[]) r4;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r4;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = (byte[]) r0;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r17 = r0;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r0.mSponImsi;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = 2;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r7 = new java.lang.String;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r7.<init>();	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4[r6] = r7;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ Exception -> 0x1fb3 }
        r4 = r0.mSponImsi;	 Catch:{ Exception -> 0x1fb3 }
        r6 = 2;	 Catch:{ Exception -> 0x1fb3 }
        r7 = new java.lang.StringBuilder;	 Catch:{ Exception -> 0x1fb3 }
        r7.<init>();	 Catch:{ Exception -> 0x1fb3 }
        r8 = 2;	 Catch:{ Exception -> 0x1fb3 }
        r8 = r17[r8];	 Catch:{ Exception -> 0x1fb3 }
        r8 = r8 >> 4;	 Catch:{ Exception -> 0x1fb3 }
        r8 = r8 & 15;	 Catch:{ Exception -> 0x1fb3 }
        r7 = r7.append(r8);	 Catch:{ Exception -> 0x1fb3 }
        r8 = 3;	 Catch:{ Exception -> 0x1fb3 }
        r9 = 1;	 Catch:{ Exception -> 0x1fb3 }
        r9 = r17[r9];	 Catch:{ Exception -> 0x1fb3 }
        r9 = r9 + -1;	 Catch:{ Exception -> 0x1fb3 }
        r0 = r17;	 Catch:{ Exception -> 0x1fb3 }
        r8 = com.android.internal.telephony.uicc.IccUtils.bcdToString(r0, r8, r9);	 Catch:{ Exception -> 0x1fb3 }
        r7 = r7.append(r8);	 Catch:{ Exception -> 0x1fb3 }
        r8 = ";";	 Catch:{ Exception -> 0x1fb3 }
        r7 = r7.append(r8);	 Catch:{ Exception -> 0x1fb3 }
        r8 = 555; // 0x22b float:7.78E-43 double:2.74E-321;	 Catch:{ Exception -> 0x1fb3 }
        r0 = r17;	 Catch:{ Exception -> 0x1fb3 }
        r9 = r0.length;	 Catch:{ Exception -> 0x1fb3 }
        r9 = r9 + -1;	 Catch:{ Exception -> 0x1fb3 }
        r0 = r17;	 Catch:{ Exception -> 0x1fb3 }
        r8 = com.android.internal.telephony.uicc.IccUtils.adnStringFieldToString(r0, r8, r9);	 Catch:{ Exception -> 0x1fb3 }
        r7 = r7.append(r8);	 Catch:{ Exception -> 0x1fb3 }
        r7 = r7.toString();	 Catch:{ Exception -> 0x1fb3 }
        r4[r6] = r7;	 Catch:{ Exception -> 0x1fb3 }
    L_0x1f8e:
        r4 = SHIP_BUILD;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        if (r4 != 0) goto L_0x004b;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x1f92:
        r4 = new java.lang.StringBuilder;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4.<init>();	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = "[sponImsi3]: ";	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.append(r6);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = r0.mSponImsi;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r7 = 2;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = r6[r7];	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.append(r6);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.toString();	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0.log(r4);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        goto L_0x004b;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x1fb3:
        r21 = move-exception;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = new java.lang.StringBuilder;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4.<init>();	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = "[sponImsi3] Ex -";	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.append(r6);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r21;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.append(r0);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.toString();	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0.loge(r4);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r21.printStackTrace();	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r0.mSponImsi;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = 2;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r7 = "";	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4[r6] = r7;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        goto L_0x1f8e;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x1fdb:
        r31 = 1;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r55;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r12 = r0.obj;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r12 = (android.os.AsyncResult) r12;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r12.exception;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        if (r4 == 0) goto L_0x1ff0;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x1fe7:
        r4 = "Invalid or missing EF[roaming]";	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0.loge(r4);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        goto L_0x004b;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x1ff0:
        r4 = r12.result;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = (byte[]) r4;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r4;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = (byte[]) r0;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r17 = r0;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r45 = com.android.internal.telephony.uicc.IccUtils.bytesToHexString(r17);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = new java.lang.StringBuilder;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4.<init>();	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = "[roaming]: ";	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.append(r6);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r45;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.append(r0);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.toString();	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0.log(r4);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = "gsm.sim.roaming";	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r45;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        android.os.SystemProperties.set(r4, r0);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        goto L_0x004b;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x2020:
        r50 = "";	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r49 = "";	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r31 = 1;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r55;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r12 = r0.obj;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r12 = (android.os.AsyncResult) r12;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r12.exception;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        if (r4 == 0) goto L_0x2039;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x2030:
        r4 = "Invalid or missing EF[VER]";	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0.loge(r4);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        goto L_0x004b;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x2039:
        r4 = r12.result;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = (byte[]) r4;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r4;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = (byte[]) r0;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r17 = r0;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r50 = com.android.internal.telephony.uicc.IccUtils.bytesToHexString(r17);	 Catch:{ Exception -> 0x209e }
    L_0x2046:
        r4 = new java.lang.StringBuilder;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4.<init>();	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = "[ver]: ";	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.append(r6);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r50;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.append(r0);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.toString();	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0.log(r4);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r50.length();	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = 6;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        if (r4 <= r6) goto L_0x004b;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x2067:
        r4 = 10;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = 14;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r50;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r0.substring(r4, r6);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = 16;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = java.lang.Integer.parseInt(r4, r6);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r49 = java.lang.Integer.toString(r4);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = new java.lang.StringBuilder;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4.<init>();	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = "[ver]converterd: ";	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.append(r6);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r49;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.append(r0);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.toString();	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0.log(r4);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = "gsm.sim.version";	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r49;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        android.os.SystemProperties.set(r4, r0);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        goto L_0x004b;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x209e:
        r21 = move-exception;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = new java.lang.StringBuilder;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4.<init>();	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = "[ver] Ex -";	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.append(r6);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r21;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.append(r0);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.toString();	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0.loge(r4);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r50 = "";	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        goto L_0x2046;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x20bc:
        r31 = 1;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r55;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r12 = r0.obj;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r12 = (android.os.AsyncResult) r12;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r12.result;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = (byte[]) r4;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r4;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = (byte[]) r0;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r17 = r0;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r12.exception;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        if (r4 == 0) goto L_0x20df;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x20d1:
        r4 = "getting mIsAvailablePSISMSC have exception !!!! ";	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0.log(r4);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = 0;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0.mIsAvailablePSISMSC = r4;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        goto L_0x004b;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x20df:
        r4 = 1;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r17[r4];	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r4 & 255;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r42 = r0;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = 255; // 0xff float:3.57E-43 double:1.26E-321;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r42;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        if (r0 == r4) goto L_0x004b;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x20ec:
        if (r42 == 0) goto L_0x004b;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x20ee:
        r35 = com.android.internal.telephony.uicc.IccUtils.bytesToHexString(r17);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = new java.lang.StringBuilder;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4.<init>();	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = "PSISMSC from modem: ";	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.append(r6);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r35;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.append(r0);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.toString();	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0.log(r4);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = 4;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = r42 * 2;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = r6 + 4;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r35;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r0.substring(r4, r6);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = com.android.internal.telephony.uicc.IccUtils.hexStringToBytes(r4);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0.mEfPsismsc = r4;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = new java.lang.StringBuilder;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4.<init>();	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = "PSISMSC to IMS: ";	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.append(r6);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = r0.mEfPsismsc;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r6 = com.android.internal.telephony.uicc.IccUtils.bytesToHexString(r6);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.append(r6);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r4.toString();	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0.log(r4);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = 1;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0.mIsAvailablePSISMSC = r4;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        goto L_0x004b;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x2146:
        r31 = 1;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = "EVENT_GET_EF_FPLMN done";	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r0.log(r4);	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        goto L_0x004b;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x2151:
        r54.onSimPhonebookRefresh();	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        goto L_0x004b;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
    L_0x2156:
        r0 = r54;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4 = r0.mParentApp;	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        r4.queryFdn();	 Catch:{ all -> 0x1216, RuntimeException -> 0x0055, all -> 0x0069 }
        goto L_0x004b;
        */
        throw new UnsupportedOperationException("Method not decompiled: com.android.internal.telephony.uicc.SIMRecords.handleMessage(android.os.Message):void");
    }

    public String toString() {
        return "SimRecords: " + super.toString() + " mVmConfig" + this.mVmConfig + " mSpnOverride=" + "mSpnOverride" + " callForwardingEnabled=" + this.mCallForwardingEnabled + " spnState=" + this.mSpnState + " mCphsInfo=" + this.mCphsInfo + " mCspPlmnEnabled=" + this.mCspPlmnEnabled + " efMWIS=" + this.mEfMWIS + " efCPHS_MWI=" + this.mEfCPHS_MWI + " mEfCff=" + this.mEfCff + " mEfCfis=" + this.mEfCfis + " getOperatorNumeric=" + getOperatorNumeric();
    }

    public SIMRecords(UiccCardApplication app, Context c, CommandsInterface ci) {
        super(app, c, ci);
        this.mAutoPreconfigService = null;
        this.mEonsEnabled = SystemProperties.getBoolean("persist.eons.enabled", true);
        this.NV_cfflag_voice = false;
        this.NV_cfflag_video = false;
        this.voicecallForwardingEnabled = false;
        this.videocallForwardingEnabled = false;
        this.mValidityPeriod = -1;
        this.mIsOPLExist = false;
        this.IsOnsExist = false;
        this.mEonsName = null;
        this.mImsiRequest = true;
        this.mReceiver = new SIMRecordsBroadcastReceiver();
        this.perso = null;
        this.isAvailableO2PERSO = false;
        this.mCphsInfo = null;
        this.mCspPlmnEnabled = true;
        this.mEfMWIS = null;
        this.mEfCPHS_MWI = null;
        this.mEfCff = null;
        this.mEfCfis = null;
        this.mEfLi = null;
        this.mEfPl = null;
        this.mSpdiNetworks = null;
        this.mPnnHomeName = null;
        this.ACTION_SIM_ICCID_CHANGED = "com.android.action.SIM_ICCID_CHANGED";
        this.ACTION_SIM_REFRESH_INIT = "com.android.action.SIM_REFRESH_INIT";
        this.mOldICCID = null;
        this.isAvailableSMS = false;
        this.spnOverride = null;
        this.spnDisplayRuleOverride = -1;
        this.mIsEnabledOPL = false;
        this.isAvailableFDN = false;
        this.isAvailableCHV1 = true;
        this.isAvailableSPN = false;
        this.isAvailableMSISDN = false;
        this.isAvailableMBDN = false;
        this.isEnabledCSP = false;
        this.isAvailableMWIS = false;
        this.isAvailableCFIS = false;
        this.isAvailableSMSP = true;
        this.isRefreshedBySTK = false;
        this.isAvailableOCSGL = false;
        this.isAvailableOCSGLList = false;
        this.mHasIsim = false;
        this.mIsAPBound = false;
        this.mRetryCountGetImsi = 0;
        this.mAutoPreconfigServiceConnection = new C01301();
        this.mAdnCache = new AdnRecordCache(this.mFh);
        this.mVmConfig = new VoiceMailConstants();
        this.mSpnOverride = new SpnOverride();
        this.mRecordsRequested = false;
        this.mRecordsToLoad = 0;
        this.mCi.setOnSmsOnSim(this, 21, null);
        this.mCi.registerForIccRefresh(this, 31, null);
        this.mCi.setOnPbInitComplete(this, 53, null);
        this.mCi.setOnSimPbReady(this, 54, null);
        IntentFilter filter = new IntentFilter();
        if ("DCG".equals("DGG") || "DCGG".equals("DGG") || "DCGS".equals("DGG") || "DCGGS".equals("DGG") || "CG".equals("DGG")) {
            filter.addAction("com.samsung.intent.action.setCardDataInit");
            filter.addAction("com.samsung.intent.action.slot1GetGsmImsi");
            if ("DCGS".equals("DGG") || "DCGGS".equals("DGG")) {
                filter.addAction("com.samsung.intent.action.Slot2SwitchCompleted");
                filter.addAction("com.samsung.intent.action.Slot2OnCompleted");
            }
        }
        filter.addAction("android.intent.action.CSC_UPDATE_NETWORK_DONE");
        filter.addAction("android.intent.action.CSC_UPDATE_VOICEMAIL_DONE");
        this.mContext.registerReceiver(this.mReceiver, filter);
        resetRecords();
        this.mParentApp.registerForReady(this, 1, null);
        log("SIMRecords X ctor this=" + this);
        PhoneRestrictionPolicy phoneRestrictionPolicy = EnterpriseDeviceManager.getInstance().getPhoneRestrictionPolicy();
        boolean hasSimLockedByAdmin = false;
        if (phoneRestrictionPolicy != null) {
            hasSimLockedByAdmin = phoneRestrictionPolicy.isSimLockedByAdmin(null);
        }
        this.mParentApp.registerForLocked(this, 41, null);
        if (CscFeature.getInstance().getEnableStatus("CscFeature_RIL_LoadIccIdOnLock") || hasSimLockedByAdmin) {
            this.mParentApp.registerForNetworkLocked(this, 41, null);
        }
    }

    public void dispose() {
        log("Disposing SIMRecords this=" + this);
        this.mCi.unregisterForIccRefresh(this);
        this.mCi.unSetOnSmsOnSim(this);
        this.mParentApp.unregisterForReady(this);
        this.mCi.unSetOnPbInitComplete(this);
        this.mCi.unSetOnSimPbReady(this);
        if ("DCG".equals("DGG") || "DCGG".equals("DGG") || "DCGS".equals("DGG") || "DCGGS".equals("DGG") || "CG".equals("DGG")) {
            this.mContext.unregisterReceiver(this.mReceiver);
        }
        if (!"DISABLE".equals(CscFeature.getInstance().getString("CscFeature_Common_AutoConfigurationType", "DISABLE"))) {
            try {
                if (this.mIsAPBound) {
                    this.mContext.unbindService(this.mAutoPreconfigServiceConnection);
                    this.mIsAPBound = false;
                }
            } catch (IllegalArgumentException e) {
                e.printStackTrace();
            }
        }
        resetRecords();
        super.dispose();
    }

    protected void finalize() {
        log("finalized");
    }

    protected void resetRecords() {
        this.mImsi = null;
        this.mMsisdn = null;
        if ("DCGS".equals("DGG") || "DCGGS".equals("DGG")) {
            this.mMsisdn = null;
            this.mImsi = null;
            setSystemProperty("gsm.sim.operator.numeric", null);
            setSystemProperty("gsm.sim.operator.iso-country", null);
            setSystemProperty("gsm.sim.operator.alpha", null);
        }
        this.mVoiceMailNum = null;
        this.mCountVoiceMessages = 0;
        this.mMncLength = -1;
        log("setting0 mMncLength" + this.mMncLength);
        this.mIccId = null;
        this.mSpnDisplayCondition = -1;
        this.mEfMWIS = null;
        this.mEfCPHS_MWI = null;
        this.mSpdiNetworks = null;
        this.mPnnHomeName = null;
        this.mGid1 = null;
        this.mSktImsiM = null;
        this.mSktIrm = null;
        this.mAdnCache.reset();
        log("SIMRecords: onRadioOffOrNotAvailable set 'gsm.sim.operator.numeric' to operator=null");
        if ("DCG".equals("DGG") || "DCGG".equals("DGG") || "DCGS".equals("DGG") || "DCGGS".equals("DGG") || "CG".equals("DGG")) {
            String simselswitch = SystemProperties.get("gsm.sim.selectnetwork");
            log("resetRecords() simselswitch = " + simselswitch + " phone.getPhoneId() = " + this.phone.getPhoneId());
            if (!("CDMA".equals(simselswitch) && this.phone.getPhoneId() == 0)) {
                log("SIMRecords: onRadioOffOrNotAvailable set 'gsm.sim.operator.numeric' to operator=null");
                log("update icc_operator_numeric=" + null);
                setSystemProperty("gsm.sim.operator.numeric", null);
                setSystemProperty("gsm.sim.operator.alpha", null);
            }
        } else {
            setSystemProperty("gsm.sim.operator.numeric", null);
            setSystemProperty("gsm.sim.operator.alpha", null);
        }
        setSystemProperty("gsm.sim.operator.iso-country", null);
        this.mRecordsRequested = false;
    }

    public String getIMSI() {
        return this.mImsi;
    }

    public String getSktIMSIM() {
        return this.mSktImsiM;
    }

    public String getSktIRM() {
        return this.mSktIrm;
    }

    public boolean hasIsim() {
        return this.mHasIsim;
    }

    public byte[] getPsismsc() {
        return this.mEfPsismsc;
    }

    public String getMsisdnNumber() {
        return this.mMsisdn;
    }

    public String getGid1() {
        return this.mGid1;
    }

    public UsimServiceTable getUsimServiceTable() {
        return this.mUsimServiceTable;
    }

    public void setMsisdnNumber(String alphaTag, String number, Message onComplete) {
        this.mMsisdn = number;
        this.mMsisdnTag = alphaTag;
        log("Set MSISDN: " + this.mMsisdnTag + " " + "xxxxxxx");
        new AdnRecordLoader(this.mFh).updateEF(new AdnRecord(this.mMsisdnTag, this.mMsisdn), IccConstants.EF_MSISDN, IccConstants.EF_EXT1, 1, null, obtainMessage(30, onComplete));
    }

    public String getMsisdnAlphaTag() {
        return this.mMsisdnTag;
    }

    public String getIccId() {
        if (!"DCGG".equals("DGG") && !"DCGGS".equals("DGG") && !"CG".equals("DGG")) {
            return this.mIccId;
        }
        if (this.mIccId == null) {
            getIccIdfromFile();
        }
        if (this.mIccId == null) {
            return "";
        }
        return this.mIccId;
    }

    private void getIccIdfromFile() {
        Throwable th;
        if ("CG".equals("DGG") || "DCG".equals("DGG") || "DCGG".equals("DGG") || "DCGS".equals("DGG") || "DCGGS".equals("DGG")) {
            File file;
            logi("getIccIdfromFile");
            FileInputStream fos = null;
            DataInputStream dos = null;
            if (!"DCGG".equals("DGG") && !"DCGGS".equals("DGG") && !"CG".equals("DGG")) {
                file = new File(ICCID2_PATH);
            } else if (this.phone.getPhoneId() == 0) {
                file = new File(ICCID_PATH);
            } else {
                file = new File(ICCID2_PATH);
            }
            try {
                FileInputStream fos2 = new FileInputStream(file);
                try {
                    byte[] bytes = new byte[((int) file.length())];
                    fos2.read(bytes);
                    this.mIccId = new String(bytes);
                    if (dos != null) {
                        try {
                            dos.close();
                        } catch (IOException e) {
                            return;
                        }
                    }
                    if (fos2 != null) {
                        fos2.close();
                    } else {
                        fos = fos2;
                    }
                } catch (IOException e2) {
                    fos = fos2;
                    if (dos != null) {
                        try {
                            dos.close();
                        } catch (IOException e3) {
                            return;
                        }
                    }
                    if (fos != null) {
                        fos.close();
                    }
                } catch (Throwable th2) {
                    th = th2;
                    fos = fos2;
                    if (dos != null) {
                        try {
                            dos.close();
                        } catch (IOException e4) {
                            throw th;
                        }
                    }
                    if (fos != null) {
                        fos.close();
                    }
                    throw th;
                }
            } catch (IOException e5) {
                if (dos != null) {
                    dos.close();
                }
                if (fos != null) {
                    fos.close();
                }
            } catch (Throwable th3) {
                th = th3;
                if (dos != null) {
                    dos.close();
                }
                if (fos != null) {
                    fos.close();
                }
                throw th;
            }
        }
    }

    private void setCardDataInit() {
        logi("setCardDataInit");
        if ("DCGS".equals("DGG")) {
            this.mIccId = null;
            logi("set iccid null");
        }
        this.mAdnCache.reset();
        if (!"DCGG".equals("DGG") && !"DCGGS".equals("DGG") && !"CG".equals("DGG")) {
            SystemProperties.set("gsm.sim.gsmoperator.numeric", "");
        } else if (this.phone.getPhoneId() == 0) {
            setSystemProperty("gsm.sim.operator.numeric", "");
        }
        fetchSimRecords();
    }

    private boolean findTheEnabledServiceInSST(byte b, int position) {
        logi("findTheEnabledServiceInSST");
        logi("position = " + position);
        logi("[findTheEnabledService] Byte before = " + IccUtils.byteToHexString(b));
        logi("[findTheEnabledService] Byte before = " + b);
        b = (byte) (b >> position);
        logi("[findTheEnabledService] Byte After = " + IccUtils.byteToHexString(b));
        logi("[findTheEnabledService] Byte After = " + b);
        if ((b & 3) == 3) {
            return true;
        }
        return false;
    }

    private boolean findTheEnabledServiceInUST(byte b, int position) {
        logi("findTheEnabledServiceInUST");
        logi("position = " + position);
        logi("[findTheEnabledService] Byte before = " + IccUtils.byteToHexString(b));
        logi("[findTheEnabledService] Byte before = " + b);
        b = (byte) (b >> position);
        logi("[findTheEnabledService] Byte After = " + IccUtils.byteToHexString(b));
        logi("[findTheEnabledService] Byte After = " + b);
        if ((b & 1) == 1) {
            return true;
        }
        return false;
    }

    public String getVoiceMailNumber() {
        return this.mVoiceMailNum;
    }

    public boolean isAvailableVoiceMailInSIM() {
        log("isAvailableMBDN: " + this.isAvailableMBDN + " isCphsMailboxEnabled(): " + isCphsMailboxEnabled());
        if (this.isAvailableMBDN || isCphsMailboxEnabled()) {
            return true;
        }
        return false;
    }

    public void setVoiceMailNumber(String alphaTag, String voiceNumber, Message onComplete) {
        if (this.mIsVoiceMailFixed) {
            AsyncResult.forMessage(onComplete).exception = new IccVmFixedException("Voicemail number is fixed by operator");
            onComplete.sendToTarget();
            return;
        }
        this.mNewVoiceMailNum = voiceNumber;
        this.mNewVoiceMailTag = alphaTag;
        AdnRecord adn = new AdnRecord(this.mNewVoiceMailTag, this.mNewVoiceMailNum);
        if (this.mMailboxIndex != 0 && this.mMailboxIndex != 255 && this.isAvailableMBDN) {
            new AdnRecordLoader(this.mFh).updateEF(adn, IccConstants.EF_MBDN, IccConstants.EF_EXT6, this.mMailboxIndex, null, obtainMessage(20, onComplete));
        } else if (isCphsMailboxEnabled()) {
            new AdnRecordLoader(this.mFh).updateEF(adn, IccConstants.EF_MAILBOX_CPHS, IccConstants.EF_EXT1, 1, null, obtainMessage(25, onComplete));
        } else {
            AsyncResult.forMessage(onComplete).exception = new IccVmNotSupportedException("Update SIM voice mailbox error");
            onComplete.sendToTarget();
        }
    }

    public String getVoiceMailAlphaTag() {
        return this.mVoiceMailTag;
    }

    public void setVoiceMessageWaiting(int line, int countWaiting) {
        int i = 0;
        if (line == 1) {
            if (countWaiting < 0) {
                countWaiting = -1;
            } else if (countWaiting > 255) {
                countWaiting = 255;
            }
            this.mCountVoiceMessages = countWaiting;
            this.mRecordsEventsRegistrants.notifyResult(Integer.valueOf(0));
            try {
                if (this.mEfMWIS != null) {
                    byte[] bArr = this.mEfMWIS;
                    int i2 = this.mEfMWIS[0] & 254;
                    if (this.mCountVoiceMessages != 0) {
                        i = 1;
                    }
                    bArr[0] = (byte) (i | i2);
                    if (countWaiting < 0) {
                        this.mEfMWIS[1] = (byte) 0;
                    } else {
                        this.mEfMWIS[1] = (byte) countWaiting;
                    }
                    this.mFh.updateEFLinearFixed(IccConstants.EF_MWIS, 1, this.mEfMWIS, null, obtainMessage(14, Integer.valueOf(IccConstants.EF_MWIS)));
                }
                if (this.mEfCPHS_MWI != null) {
                    this.mEfCPHS_MWI[0] = (byte) ((this.mCountVoiceMessages == 0 ? 5 : 10) | (this.mEfCPHS_MWI[0] & 240));
                    this.mFh.updateEFTransparent(IccConstants.EF_VOICE_MAIL_INDICATOR_CPHS, this.mEfCPHS_MWI, obtainMessage(14, Integer.valueOf(IccConstants.EF_VOICE_MAIL_INDICATOR_CPHS)));
                }
            } catch (ArrayIndexOutOfBoundsException ex) {
                logw("Error saving voice mail state to SIM. Probably malformed SIM record", ex);
            }
        }
    }

    private boolean validEfCfis(byte[] data) {
        return data != null;
    }

    public boolean getVoiceCallForwardingFlag() {
        return this.voicecallForwardingEnabled;
    }

    /* JADX WARNING: inconsistent code. */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    public void setCallForwardingFlag(int r15, java.lang.Boolean r16, java.lang.Boolean r17, java.lang.String r18) {
        /*
        r14 = this;
        r1 = 1;
        if (r15 == r1) goto L_0x0004;
    L_0x0003:
        return;
    L_0x0004:
        if (r16 == 0) goto L_0x000c;
    L_0x0006:
        r1 = r16.booleanValue();
        r14.voicecallForwardingEnabled = r1;
    L_0x000c:
        if (r17 == 0) goto L_0x0014;
    L_0x000e:
        r1 = r17.booleanValue();
        r14.videocallForwardingEnabled = r1;
    L_0x0014:
        r1 = r14.mRecordsEventsRegistrants;
        r2 = 1;
        r2 = java.lang.Integer.valueOf(r2);
        r1.notifyResult(r2);
        r1 = r14.isAvailableCFIS;	 Catch:{ ArrayIndexOutOfBoundsException -> 0x00f4 }
        if (r1 == 0) goto L_0x013c;
    L_0x0022:
        r1 = r14.mEfCfis;	 Catch:{ ArrayIndexOutOfBoundsException -> 0x00f4 }
        if (r1 == 0) goto L_0x013c;
    L_0x0026:
        if (r16 == 0) goto L_0x003c;
    L_0x0028:
        r1 = java.lang.Boolean.TRUE;	 Catch:{ ArrayIndexOutOfBoundsException -> 0x00f4 }
        r0 = r16;
        r1 = r0.equals(r1);	 Catch:{ ArrayIndexOutOfBoundsException -> 0x00f4 }
        if (r1 == 0) goto L_0x00fc;
    L_0x0032:
        r1 = r14.mEfCfis;	 Catch:{ ArrayIndexOutOfBoundsException -> 0x00f4 }
        r2 = 1;
        r3 = r1[r2];	 Catch:{ ArrayIndexOutOfBoundsException -> 0x00f4 }
        r3 = r3 | 1;
        r3 = (byte) r3;	 Catch:{ ArrayIndexOutOfBoundsException -> 0x00f4 }
        r1[r2] = r3;	 Catch:{ ArrayIndexOutOfBoundsException -> 0x00f4 }
    L_0x003c:
        if (r17 == 0) goto L_0x0052;
    L_0x003e:
        r1 = java.lang.Boolean.TRUE;	 Catch:{ ArrayIndexOutOfBoundsException -> 0x00f4 }
        r0 = r17;
        r1 = r0.equals(r1);	 Catch:{ ArrayIndexOutOfBoundsException -> 0x00f4 }
        if (r1 == 0) goto L_0x0108;
    L_0x0048:
        r1 = r14.mEfCfis;	 Catch:{ ArrayIndexOutOfBoundsException -> 0x00f4 }
        r2 = 1;
        r3 = r1[r2];	 Catch:{ ArrayIndexOutOfBoundsException -> 0x00f4 }
        r3 = r3 | 16;
        r3 = (byte) r3;	 Catch:{ ArrayIndexOutOfBoundsException -> 0x00f4 }
        r1[r2] = r3;	 Catch:{ ArrayIndexOutOfBoundsException -> 0x00f4 }
    L_0x0052:
        if (r18 == 0) goto L_0x007a;
    L_0x0054:
        r1 = r18.length();	 Catch:{ ArrayIndexOutOfBoundsException -> 0x00f4 }
        r2 = 20;
        if (r1 <= r2) goto L_0x0065;
    L_0x005c:
        r1 = 0;
        r2 = 19;
        r0 = r18;
        r18 = r0.substring(r1, r2);	 Catch:{ ArrayIndexOutOfBoundsException -> 0x00f4 }
    L_0x0065:
        r9 = new com.android.internal.telephony.uicc.AdnRecord;	 Catch:{ Exception -> 0x0114 }
        r1 = 0;
        r0 = r18;
        r9.<init>(r1, r0);	 Catch:{ Exception -> 0x0114 }
        r11 = 14;
        r7 = r9.buildAdnString(r11);	 Catch:{ Exception -> 0x0114 }
        r1 = 0;
        r2 = r14.mEfCfis;	 Catch:{ Exception -> 0x0114 }
        r3 = 2;
        java.lang.System.arraycopy(r7, r1, r2, r3, r11);	 Catch:{ Exception -> 0x0114 }
    L_0x007a:
        r1 = r14.mFh;	 Catch:{ ArrayIndexOutOfBoundsException -> 0x00f4 }
        r2 = 28619; // 0x6fcb float:4.0104E-41 double:1.41397E-319;
        r3 = 1;
        r4 = r14.mEfCfis;	 Catch:{ ArrayIndexOutOfBoundsException -> 0x00f4 }
        r5 = 0;
        r6 = 14;
        r13 = 28619; // 0x6fcb float:4.0104E-41 double:1.41397E-319;
        r13 = java.lang.Integer.valueOf(r13);	 Catch:{ ArrayIndexOutOfBoundsException -> 0x00f4 }
        r6 = r14.obtainMessage(r6, r13);	 Catch:{ ArrayIndexOutOfBoundsException -> 0x00f4 }
        r1.updateEFLinearFixed(r2, r3, r4, r5, r6);	 Catch:{ ArrayIndexOutOfBoundsException -> 0x00f4 }
        r1 = com.sec.android.app.CscFeature.getInstance();	 Catch:{ ArrayIndexOutOfBoundsException -> 0x00f4 }
        r2 = "CscFeature_RIL_SupportOrangeCPHS";
        r1 = r1.getEnableStatus(r2);	 Catch:{ ArrayIndexOutOfBoundsException -> 0x00f4 }
        if (r1 == 0) goto L_0x0003;
    L_0x009d:
        r1 = r14.mEfCff;	 Catch:{ ArrayIndexOutOfBoundsException -> 0x00f4 }
        if (r1 == 0) goto L_0x0003;
    L_0x00a1:
        if (r16 == 0) goto L_0x00bc;
    L_0x00a3:
        r1 = java.lang.Boolean.TRUE;	 Catch:{ ArrayIndexOutOfBoundsException -> 0x00f4 }
        r0 = r16;
        r1 = r0.equals(r1);	 Catch:{ ArrayIndexOutOfBoundsException -> 0x00f4 }
        if (r1 == 0) goto L_0x011c;
    L_0x00ad:
        r1 = r14.mEfCff;	 Catch:{ ArrayIndexOutOfBoundsException -> 0x00f4 }
        r2 = 0;
        r3 = r14.mEfCff;	 Catch:{ ArrayIndexOutOfBoundsException -> 0x00f4 }
        r4 = 0;
        r3 = r3[r4];	 Catch:{ ArrayIndexOutOfBoundsException -> 0x00f4 }
        r3 = r3 & 240;
        r3 = r3 | 10;
        r3 = (byte) r3;	 Catch:{ ArrayIndexOutOfBoundsException -> 0x00f4 }
        r1[r2] = r3;	 Catch:{ ArrayIndexOutOfBoundsException -> 0x00f4 }
    L_0x00bc:
        if (r17 == 0) goto L_0x00dd;
    L_0x00be:
        r1 = r14.mEfCff;	 Catch:{ ArrayIndexOutOfBoundsException -> 0x00f4 }
        r1 = r1.length;	 Catch:{ ArrayIndexOutOfBoundsException -> 0x00f4 }
        r2 = 1;
        if (r1 <= r2) goto L_0x00dd;
    L_0x00c4:
        r1 = java.lang.Boolean.TRUE;	 Catch:{ ArrayIndexOutOfBoundsException -> 0x00f4 }
        r0 = r17;
        r1 = r0.equals(r1);	 Catch:{ ArrayIndexOutOfBoundsException -> 0x00f4 }
        if (r1 == 0) goto L_0x012c;
    L_0x00ce:
        r1 = r14.mEfCff;	 Catch:{ ArrayIndexOutOfBoundsException -> 0x00f4 }
        r2 = 1;
        r3 = r14.mEfCff;	 Catch:{ ArrayIndexOutOfBoundsException -> 0x00f4 }
        r4 = 1;
        r3 = r3[r4];	 Catch:{ ArrayIndexOutOfBoundsException -> 0x00f4 }
        r3 = r3 & 15;
        r3 = r3 | 160;
        r3 = (byte) r3;	 Catch:{ ArrayIndexOutOfBoundsException -> 0x00f4 }
        r1[r2] = r3;	 Catch:{ ArrayIndexOutOfBoundsException -> 0x00f4 }
    L_0x00dd:
        r1 = r14.mFh;	 Catch:{ ArrayIndexOutOfBoundsException -> 0x00f4 }
        r2 = 28435; // 0x6f13 float:3.9846E-41 double:1.4049E-319;
        r3 = r14.mEfCff;	 Catch:{ ArrayIndexOutOfBoundsException -> 0x00f4 }
        r4 = 14;
        r5 = 28435; // 0x6f13 float:3.9846E-41 double:1.4049E-319;
        r5 = java.lang.Integer.valueOf(r5);	 Catch:{ ArrayIndexOutOfBoundsException -> 0x00f4 }
        r4 = r14.obtainMessage(r4, r5);	 Catch:{ ArrayIndexOutOfBoundsException -> 0x00f4 }
        r1.updateEFTransparent(r2, r3, r4);	 Catch:{ ArrayIndexOutOfBoundsException -> 0x00f4 }
        goto L_0x0003;
    L_0x00f4:
        r10 = move-exception;
        r1 = "Error saving call fowarding flag to SIM. Probably malformed SIM record";
        r14.loge(r1, r10);
        goto L_0x0003;
    L_0x00fc:
        r1 = r14.mEfCfis;	 Catch:{ ArrayIndexOutOfBoundsException -> 0x00f4 }
        r2 = 1;
        r3 = r1[r2];	 Catch:{ ArrayIndexOutOfBoundsException -> 0x00f4 }
        r3 = r3 & 254;
        r3 = (byte) r3;	 Catch:{ ArrayIndexOutOfBoundsException -> 0x00f4 }
        r1[r2] = r3;	 Catch:{ ArrayIndexOutOfBoundsException -> 0x00f4 }
        goto L_0x003c;
    L_0x0108:
        r1 = r14.mEfCfis;	 Catch:{ ArrayIndexOutOfBoundsException -> 0x00f4 }
        r2 = 1;
        r3 = r1[r2];	 Catch:{ ArrayIndexOutOfBoundsException -> 0x00f4 }
        r3 = r3 & 239;
        r3 = (byte) r3;	 Catch:{ ArrayIndexOutOfBoundsException -> 0x00f4 }
        r1[r2] = r3;	 Catch:{ ArrayIndexOutOfBoundsException -> 0x00f4 }
        goto L_0x0052;
    L_0x0114:
        r10 = move-exception;
        r1 = "Exception for build CF number";
        r14.log(r1);	 Catch:{ ArrayIndexOutOfBoundsException -> 0x00f4 }
        goto L_0x007a;
    L_0x011c:
        r1 = r14.mEfCff;	 Catch:{ ArrayIndexOutOfBoundsException -> 0x00f4 }
        r2 = 0;
        r3 = r14.mEfCff;	 Catch:{ ArrayIndexOutOfBoundsException -> 0x00f4 }
        r4 = 0;
        r3 = r3[r4];	 Catch:{ ArrayIndexOutOfBoundsException -> 0x00f4 }
        r3 = r3 & 240;
        r3 = r3 | 5;
        r3 = (byte) r3;	 Catch:{ ArrayIndexOutOfBoundsException -> 0x00f4 }
        r1[r2] = r3;	 Catch:{ ArrayIndexOutOfBoundsException -> 0x00f4 }
        goto L_0x00bc;
    L_0x012c:
        r1 = r14.mEfCff;	 Catch:{ ArrayIndexOutOfBoundsException -> 0x00f4 }
        r2 = 1;
        r3 = r14.mEfCff;	 Catch:{ ArrayIndexOutOfBoundsException -> 0x00f4 }
        r4 = 1;
        r3 = r3[r4];	 Catch:{ ArrayIndexOutOfBoundsException -> 0x00f4 }
        r3 = r3 & 15;
        r3 = r3 | 80;
        r3 = (byte) r3;	 Catch:{ ArrayIndexOutOfBoundsException -> 0x00f4 }
        r1[r2] = r3;	 Catch:{ ArrayIndexOutOfBoundsException -> 0x00f4 }
        goto L_0x00dd;
    L_0x013c:
        r1 = r14.mEfCff;	 Catch:{ ArrayIndexOutOfBoundsException -> 0x00f4 }
        if (r1 == 0) goto L_0x01e0;
    L_0x0140:
        r1 = "ATT";
        r2 = "ro.csc.sales_code";
        r2 = android.os.SystemProperties.get(r2);	 Catch:{ ArrayIndexOutOfBoundsException -> 0x00f4 }
        r1 = r1.equals(r2);	 Catch:{ ArrayIndexOutOfBoundsException -> 0x00f4 }
        if (r1 != 0) goto L_0x015c;
    L_0x014e:
        r1 = "AIO";
        r2 = "ro.csc.sales_code";
        r2 = android.os.SystemProperties.get(r2);	 Catch:{ ArrayIndexOutOfBoundsException -> 0x00f4 }
        r1 = r1.equals(r2);	 Catch:{ ArrayIndexOutOfBoundsException -> 0x00f4 }
        if (r1 == 0) goto L_0x016d;
    L_0x015c:
        r1 = "1";
        r2 = r14.mIccType;	 Catch:{ ArrayIndexOutOfBoundsException -> 0x00f4 }
        r1 = r1.equals(r2);	 Catch:{ ArrayIndexOutOfBoundsException -> 0x00f4 }
        if (r1 != 0) goto L_0x016d;
    L_0x0166:
        r1 = "Do not update EF_CFF_CPHS";
        r14.log(r1);	 Catch:{ ArrayIndexOutOfBoundsException -> 0x00f4 }
        goto L_0x0003;
    L_0x016d:
        if (r16 == 0) goto L_0x0188;
    L_0x016f:
        r1 = java.lang.Boolean.TRUE;	 Catch:{ ArrayIndexOutOfBoundsException -> 0x00f4 }
        r0 = r16;
        r1 = r0.equals(r1);	 Catch:{ ArrayIndexOutOfBoundsException -> 0x00f4 }
        if (r1 == 0) goto L_0x01c0;
    L_0x0179:
        r1 = r14.mEfCff;	 Catch:{ ArrayIndexOutOfBoundsException -> 0x00f4 }
        r2 = 0;
        r3 = r14.mEfCff;	 Catch:{ ArrayIndexOutOfBoundsException -> 0x00f4 }
        r4 = 0;
        r3 = r3[r4];	 Catch:{ ArrayIndexOutOfBoundsException -> 0x00f4 }
        r3 = r3 & 240;
        r3 = r3 | 10;
        r3 = (byte) r3;	 Catch:{ ArrayIndexOutOfBoundsException -> 0x00f4 }
        r1[r2] = r3;	 Catch:{ ArrayIndexOutOfBoundsException -> 0x00f4 }
    L_0x0188:
        if (r17 == 0) goto L_0x01a9;
    L_0x018a:
        r1 = r14.mEfCff;	 Catch:{ ArrayIndexOutOfBoundsException -> 0x00f4 }
        r1 = r1.length;	 Catch:{ ArrayIndexOutOfBoundsException -> 0x00f4 }
        r2 = 1;
        if (r1 <= r2) goto L_0x01a9;
    L_0x0190:
        r1 = java.lang.Boolean.TRUE;	 Catch:{ ArrayIndexOutOfBoundsException -> 0x00f4 }
        r0 = r17;
        r1 = r0.equals(r1);	 Catch:{ ArrayIndexOutOfBoundsException -> 0x00f4 }
        if (r1 == 0) goto L_0x01d0;
    L_0x019a:
        r1 = r14.mEfCff;	 Catch:{ ArrayIndexOutOfBoundsException -> 0x00f4 }
        r2 = 1;
        r3 = r14.mEfCff;	 Catch:{ ArrayIndexOutOfBoundsException -> 0x00f4 }
        r4 = 1;
        r3 = r3[r4];	 Catch:{ ArrayIndexOutOfBoundsException -> 0x00f4 }
        r3 = r3 & 15;
        r3 = r3 | 160;
        r3 = (byte) r3;	 Catch:{ ArrayIndexOutOfBoundsException -> 0x00f4 }
        r1[r2] = r3;	 Catch:{ ArrayIndexOutOfBoundsException -> 0x00f4 }
    L_0x01a9:
        r1 = r14.mFh;	 Catch:{ ArrayIndexOutOfBoundsException -> 0x00f4 }
        r2 = 28435; // 0x6f13 float:3.9846E-41 double:1.4049E-319;
        r3 = r14.mEfCff;	 Catch:{ ArrayIndexOutOfBoundsException -> 0x00f4 }
        r4 = 14;
        r5 = 28435; // 0x6f13 float:3.9846E-41 double:1.4049E-319;
        r5 = java.lang.Integer.valueOf(r5);	 Catch:{ ArrayIndexOutOfBoundsException -> 0x00f4 }
        r4 = r14.obtainMessage(r4, r5);	 Catch:{ ArrayIndexOutOfBoundsException -> 0x00f4 }
        r1.updateEFTransparent(r2, r3, r4);	 Catch:{ ArrayIndexOutOfBoundsException -> 0x00f4 }
        goto L_0x0003;
    L_0x01c0:
        r1 = r14.mEfCff;	 Catch:{ ArrayIndexOutOfBoundsException -> 0x00f4 }
        r2 = 0;
        r3 = r14.mEfCff;	 Catch:{ ArrayIndexOutOfBoundsException -> 0x00f4 }
        r4 = 0;
        r3 = r3[r4];	 Catch:{ ArrayIndexOutOfBoundsException -> 0x00f4 }
        r3 = r3 & 240;
        r3 = r3 | 5;
        r3 = (byte) r3;	 Catch:{ ArrayIndexOutOfBoundsException -> 0x00f4 }
        r1[r2] = r3;	 Catch:{ ArrayIndexOutOfBoundsException -> 0x00f4 }
        goto L_0x0188;
    L_0x01d0:
        r1 = r14.mEfCff;	 Catch:{ ArrayIndexOutOfBoundsException -> 0x00f4 }
        r2 = 1;
        r3 = r14.mEfCff;	 Catch:{ ArrayIndexOutOfBoundsException -> 0x00f4 }
        r4 = 1;
        r3 = r3[r4];	 Catch:{ ArrayIndexOutOfBoundsException -> 0x00f4 }
        r3 = r3 & 15;
        r3 = r3 | 80;
        r3 = (byte) r3;	 Catch:{ ArrayIndexOutOfBoundsException -> 0x00f4 }
        r1[r2] = r3;	 Catch:{ ArrayIndexOutOfBoundsException -> 0x00f4 }
        goto L_0x01a9;
    L_0x01e0:
        r1 = new java.lang.StringBuilder;	 Catch:{ ArrayIndexOutOfBoundsException -> 0x00f4 }
        r1.<init>();	 Catch:{ ArrayIndexOutOfBoundsException -> 0x00f4 }
        r2 = "Write cf icon voice value: ";
        r1 = r1.append(r2);	 Catch:{ ArrayIndexOutOfBoundsException -> 0x00f4 }
        r2 = r14.voicecallForwardingEnabled;	 Catch:{ ArrayIndexOutOfBoundsException -> 0x00f4 }
        r1 = r1.append(r2);	 Catch:{ ArrayIndexOutOfBoundsException -> 0x00f4 }
        r1 = r1.toString();	 Catch:{ ArrayIndexOutOfBoundsException -> 0x00f4 }
        r14.log(r1);	 Catch:{ ArrayIndexOutOfBoundsException -> 0x00f4 }
        r1 = new java.lang.StringBuilder;	 Catch:{ ArrayIndexOutOfBoundsException -> 0x00f4 }
        r1.<init>();	 Catch:{ ArrayIndexOutOfBoundsException -> 0x00f4 }
        r2 = "Write cf icon video value: ";
        r1 = r1.append(r2);	 Catch:{ ArrayIndexOutOfBoundsException -> 0x00f4 }
        r2 = r14.videocallForwardingEnabled;	 Catch:{ ArrayIndexOutOfBoundsException -> 0x00f4 }
        r1 = r1.append(r2);	 Catch:{ ArrayIndexOutOfBoundsException -> 0x00f4 }
        r1 = r1.toString();	 Catch:{ ArrayIndexOutOfBoundsException -> 0x00f4 }
        r14.log(r1);	 Catch:{ ArrayIndexOutOfBoundsException -> 0x00f4 }
        r1 = r14.mContext;	 Catch:{ ArrayIndexOutOfBoundsException -> 0x00f4 }
        if (r1 == 0) goto L_0x023a;
    L_0x0214:
        r1 = r14.mContext;	 Catch:{ ArrayIndexOutOfBoundsException -> 0x00f4 }
        r12 = android.preference.PreferenceManager.getDefaultSharedPreferences(r1);	 Catch:{ ArrayIndexOutOfBoundsException -> 0x00f4 }
        r8 = r12.edit();	 Catch:{ ArrayIndexOutOfBoundsException -> 0x00f4 }
        r1 = "cf_iconkey_voice";
        r2 = r14.voicecallForwardingEnabled;	 Catch:{ ArrayIndexOutOfBoundsException -> 0x00f4 }
        r8.putBoolean(r1, r2);	 Catch:{ ArrayIndexOutOfBoundsException -> 0x00f4 }
        r1 = "cf_iconkey_video";
        r2 = r14.videocallForwardingEnabled;	 Catch:{ ArrayIndexOutOfBoundsException -> 0x00f4 }
        r8.putBoolean(r1, r2);	 Catch:{ ArrayIndexOutOfBoundsException -> 0x00f4 }
        r1 = "cf_imsikey";
        r2 = r14.getIMSI();	 Catch:{ ArrayIndexOutOfBoundsException -> 0x00f4 }
        r8.putString(r1, r2);	 Catch:{ ArrayIndexOutOfBoundsException -> 0x00f4 }
        r8.commit();	 Catch:{ ArrayIndexOutOfBoundsException -> 0x00f4 }
        goto L_0x0003;
    L_0x023a:
        r1 = "mContext is null, so do not save call forwarding flag on shared preferences";
        r14.log(r1);	 Catch:{ ArrayIndexOutOfBoundsException -> 0x00f4 }
        goto L_0x0003;
        */
        throw new UnsupportedOperationException("Method not decompiled: com.android.internal.telephony.uicc.SIMRecords.setCallForwardingFlag(int, java.lang.Boolean, java.lang.Boolean, java.lang.String):void");
    }

    public void onRefresh(boolean fileChanged, int[] fileList) {
        if (fileChanged) {
            fetchSimRecords();
        }
    }

    public String getOperatorNumeric() {
        if (this.mImsi == null) {
            log("getOperatorNumeric: IMSI == null");
            return null;
        } else if (this.mMncLength != -1 && this.mMncLength != 0) {
            return this.mImsi.substring(0, this.mMncLength + 3);
        } else {
            log("getSIMOperatorNumeric: bad mncLength");
            return null;
        }
    }

    private void checkEONSavailable(byte[] table) {
        boolean z = true;
        logi("Enter checkEONS");
        try {
            if ("1".equals(this.mIccType)) {
                if (this.mUsimServiceTable != null) {
                    logi("isSSTActive(PNN) : " + this.mUsimServiceTable.isSSTActive(SimService.PNN));
                    logi("isSSTAvaiable(PNN) : " + this.mUsimServiceTable.isSSTAvailable(SimService.PNN));
                    boolean z2 = this.mUsimServiceTable.isSSTActive(SimService.PNN) && this.mUsimServiceTable.isSSTAvailable(SimService.PNN);
                    this.mIsEnabledPNN = z2;
                    logi("isSSTActive(OPL) : " + this.mUsimServiceTable.isSSTActive(SimService.OPL));
                    logi("isSSTAvaiable(OPL) : " + this.mUsimServiceTable.isSSTAvailable(SimService.OPL));
                    if (!(this.mUsimServiceTable.isSSTActive(SimService.OPL) && this.mUsimServiceTable.isSSTAvailable(SimService.OPL))) {
                        z = false;
                    }
                    this.mIsEnabledOPL = z;
                }
            } else if (!"2".equals(this.mIccType)) {
                logi("ICCType is Unknown");
                return;
            } else if (this.mUsimServiceTable != null) {
                this.mIsEnabledPNN = this.mUsimServiceTable.isAvailable(UsimService.PLMN_NETWORK_NAME);
                this.mIsEnabledOPL = this.mUsimServiceTable.isAvailable(UsimService.OPERATOR_PLMN_LIST);
            }
            logi("mIsEnabledPNN is " + this.mIsEnabledPNN);
            logi("mIsEnabledOPL is " + this.mIsEnabledOPL);
        } catch (ArrayIndexOutOfBoundsException e) {
            loge("Exception", e);
            this.mIsEnabledPNN = false;
            this.mIsEnabledOPL = false;
        }
    }

    private void checkFDNavailable(byte[] table) {
        logi("Enter  checkFDNavailable");
        try {
            if ("1".equals(this.mIccType)) {
                if (this.mUsimServiceTable != null) {
                    boolean z;
                    logi("isSSTActive(FDN) : " + this.mUsimServiceTable.isSSTActive(SimService.FDN));
                    logi("isSSTAvaiable(FDN) : " + this.mUsimServiceTable.isSSTAvailable(SimService.FDN));
                    if (this.mUsimServiceTable.isSSTActive(SimService.FDN) && this.mUsimServiceTable.isSSTAvailable(SimService.FDN)) {
                        z = true;
                    } else {
                        z = false;
                    }
                    this.isAvailableFDN = z;
                }
            } else if (!"2".equals(this.mIccType)) {
                logi("ICCType is Unknown");
                return;
            } else if (this.mUsimServiceTable != null) {
                this.isAvailableFDN = this.mUsimServiceTable.isAvailable(UsimService.FDN);
            }
            logi("isAvailableFDN is " + this.isAvailableFDN);
        } catch (ArrayIndexOutOfBoundsException e) {
            loge("Exception", e);
            this.isAvailableFDN = false;
        }
    }

    private void checkSDNavailable(byte[] table) {
        logi("Enter  checkSDNavailable");
        try {
            if ("1".equals(this.mIccType)) {
                if (this.mUsimServiceTable != null) {
                    boolean z;
                    logi("isSSTActive(SDN) : " + this.mUsimServiceTable.isSSTActive(SimService.SDN));
                    logi("isSSTAvaiable(SDN) : " + this.mUsimServiceTable.isSSTAvailable(SimService.SDN));
                    if (this.mUsimServiceTable.isSSTActive(SimService.SDN) && this.mUsimServiceTable.isSSTAvailable(SimService.SDN)) {
                        z = true;
                    } else {
                        z = false;
                    }
                    this.mIsAvailableSDN = z;
                }
            } else if (!"2".equals(this.mIccType)) {
                logi("ICCType is Unknown");
                return;
            } else if (this.mUsimServiceTable != null) {
                this.mIsAvailableSDN = this.mUsimServiceTable.isAvailable(UsimService.SDN);
            }
            logi("IsAvailableSDN is " + this.mIsAvailableSDN);
        } catch (ArrayIndexOutOfBoundsException e) {
            loge("Exception", e);
            this.mIsAvailableSDN = false;
        }
    }

    private void checkOCSGLAvailable(byte[] table) {
        boolean isAvailable86 = false;
        boolean isAvailable92 = false;
        try {
            if (this.mUsimServiceTable != null) {
                this.isAvailableOCSGL = this.mUsimServiceTable.isAvailable(UsimService.OPERATOR_CSG_LISTS_AND_INDICATIONS);
                isAvailable86 = this.mUsimServiceTable.isAvailable(UsimService.ALLOWED_CSG_LISTS_AND_INDICATIONS);
                isAvailable92 = this.mUsimServiceTable.isAvailable(UsimService.CSG_DISPLAY_CONTROL);
            } else {
                logi("UST is Null for checking OCSGL");
            }
        } catch (ArrayIndexOutOfBoundsException e) {
            loge("Exception", e);
            this.isAvailableOCSGL = false;
        }
        logi("isAvailableOCSGL is " + this.isAvailableOCSGL + ", " + isAvailable86 + ", " + isAvailable92);
        if (this.isAvailableOCSGL || isAvailable86 || isAvailable92) {
            this.mFh.loadEFLinearFixedAll(IccConstants.EF_OCSGL, obtainMessage(100, new EfOcsglLoaded()));
            this.mRecordsToLoad++;
        }
    }

    private void checkCHV1available(byte[] table) {
        logi("Enter  checkCHV1available");
        try {
            if ("1".equals(this.mIccType)) {
                if (this.mUsimServiceTable != null) {
                    boolean z;
                    logi("isSSTActive(CHV1) : " + this.mUsimServiceTable.isSSTActive(SimService.CHV1_DISABLE));
                    logi("isSSTAvaiable(CHV1) : " + this.mUsimServiceTable.isSSTAvailable(SimService.CHV1_DISABLE));
                    if (this.mUsimServiceTable.isSSTActive(SimService.CHV1_DISABLE) && this.mUsimServiceTable.isSSTAvailable(SimService.CHV1_DISABLE)) {
                        z = true;
                    } else {
                        z = false;
                    }
                    this.isAvailableCHV1 = z;
                }
            } else if ("2".equals(this.mIccType)) {
                logi("3G - CHV1 available");
                this.isAvailableCHV1 = true;
            } else {
                logi("ICCType is Unknown");
                return;
            }
            logi("isAvailableCHV1 is " + this.isAvailableCHV1);
        } catch (ArrayIndexOutOfBoundsException e) {
            loge("Exception", e);
            this.isAvailableCHV1 = true;
        }
    }

    private void checkSPNavailable(byte[] table) {
        logi("Enter  checkSPNavailable");
        try {
            if ("1".equals(this.mIccType)) {
                if (this.mUsimServiceTable != null) {
                    boolean z;
                    logi("isSSTActive(SPN) : " + this.mUsimServiceTable.isSSTActive(SimService.SPN));
                    logi("isSSTAvaiable(SPN) : " + this.mUsimServiceTable.isSSTAvailable(SimService.SPN));
                    if (this.mUsimServiceTable.isSSTActive(SimService.SPN) && this.mUsimServiceTable.isSSTAvailable(SimService.SPN)) {
                        z = true;
                    } else {
                        z = false;
                    }
                    this.isAvailableSPN = z;
                }
            } else if (!"2".equals(this.mIccType)) {
                logi("ICCType is Unknown");
                return;
            } else if (this.mUsimServiceTable != null) {
                this.isAvailableSPN = this.mUsimServiceTable.isAvailable(UsimService.SPN);
            }
            logi("isAvailableSPN is " + this.isAvailableSPN);
        } catch (ArrayIndexOutOfBoundsException e) {
            loge("Exception", e);
            this.isAvailableSPN = false;
        }
    }

    private void checkMSISDNavailable(byte[] table) {
        logi("Enter  checkMSISDNavailable");
        try {
            if ("1".equals(this.mIccType)) {
                if (this.mUsimServiceTable != null) {
                    boolean z;
                    logi("isSSTActive(MSISDN) : " + this.mUsimServiceTable.isSSTActive(SimService.MSISDN));
                    logi("isSSTAvaiable(MSISDN) : " + this.mUsimServiceTable.isSSTAvailable(SimService.MSISDN));
                    if (this.mUsimServiceTable.isSSTActive(SimService.MSISDN) && this.mUsimServiceTable.isSSTAvailable(SimService.MSISDN)) {
                        z = true;
                    } else {
                        z = false;
                    }
                    this.isAvailableMSISDN = z;
                }
            } else if (!"2".equals(this.mIccType)) {
                logi("ICCType is Unknown");
                return;
            } else if (this.mUsimServiceTable != null) {
                this.isAvailableMSISDN = this.mUsimServiceTable.isAvailable(UsimService.MSISDN);
            }
            if (this.isAvailableMSISDN) {
                new AdnRecordLoader(this.mFh).loadFromEF(IccConstants.EF_MSISDN, "1".equals(this.mIccType) ? IccConstants.EF_EXT1 : IccConstants.EF_EXT5, 1, obtainMessage(10));
                this.mRecordsToLoad++;
            }
            logi("isAvailableMSISDN is " + this.isAvailableMSISDN);
        } catch (ArrayIndexOutOfBoundsException e) {
            loge("Exception", e);
            this.isAvailableMSISDN = false;
        }
    }

    private void checkSMSPavailable(byte[] table) {
        logi("Enter  checkSMSPavailable");
        try {
            if ("1".equals(this.mIccType)) {
                if (this.mUsimServiceTable != null) {
                    boolean z;
                    logi("isSSTActive(SMSP) : " + this.mUsimServiceTable.isSSTActive(SimService.SMSP));
                    logi("isSSTAvaiable(SMSP) : " + this.mUsimServiceTable.isSSTAvailable(SimService.SMSP));
                    if (this.mUsimServiceTable.isSSTActive(SimService.SMSP) && this.mUsimServiceTable.isSSTAvailable(SimService.SMSP)) {
                        z = true;
                    } else {
                        z = false;
                    }
                    this.isAvailableSMSP = z;
                }
            } else if (!"2".equals(this.mIccType)) {
                logi("ICCType is Unknown");
                return;
            } else if (this.mUsimServiceTable != null) {
                this.isAvailableSMSP = this.mUsimServiceTable.isAvailable(UsimService.SM_SERVICE_PARAMS);
            }
            logi("isAvailableSMSP is " + this.isAvailableSMSP);
        } catch (ArrayIndexOutOfBoundsException e) {
            loge("Exception", e);
            this.isAvailableSMSP = false;
        }
    }

    private void checkSMSavailable(byte[] table) {
        boolean z = true;
        logi("Enter  checkSMSavailable");
        try {
            if ("1".equals(this.mIccType)) {
                if (("DCG".equals("DGG") || "DCGG".equals("DGG") || "DCGS".equals("DGG") || "DCGGS".equals("DGG") || "DGG".equals("DGG") || "CG".equals("DGG")) && this.phone.getPhoneId() == 1) {
                    logi("table[SST_SMS_BYTE]" + IccUtils.byteToHexString(table[0]));
                    String operatorSIM = getSystemProperty("gsm.sim.gsmoperator.numeric", "");
                    if (operatorSIM == "") {
                        operatorSIM = this.mImsi.substring(0, 5);
                    }
                    logi("checkSMSavailable : " + operatorSIM);
                    this.isAvailableSMS = findTheEnabledServiceInSST(table[0], 6);
                    if (!(this.isAvailableSMS || "00101".equals(operatorSIM))) {
                        logi("table[UST_SMS_BYTE]" + IccUtils.byteToHexString(table[1]));
                        this.isAvailableSMS = findTheEnabledServiceInUST(table[1], 1);
                    }
                } else if (this.mUsimServiceTable != null) {
                    logi("isSSTActive(SMS) : " + this.mUsimServiceTable.isSSTActive(SimService.SMS));
                    logi("isSSTAvaiable(SMS) : " + this.mUsimServiceTable.isSSTAvailable(SimService.SMS));
                    if (!(this.mUsimServiceTable.isSSTActive(SimService.SMS) && this.mUsimServiceTable.isSSTAvailable(SimService.SMS))) {
                        z = false;
                    }
                    this.isAvailableSMS = z;
                }
            } else if ("2".equals(this.mIccType)) {
                if (this.mUsimServiceTable != null) {
                    this.isAvailableSMS = this.mUsimServiceTable.isAvailable(UsimService.SM_STORAGE);
                }
            } else if (!"CG".equals("DGG") && !"DCG".equals("DGG") && !"DCGG".equals("DGG") && !"DCGS".equals("DGG") && !"DCGGS".equals("DGG")) {
                logi("ICCType is Unknown");
                return;
            } else if ("4".equals(this.mIccType)) {
                if (Integer.parseInt(SystemProperties.get("ril.IsCSIM", "0")) == 1) {
                    if (this.mUsimServiceTable != null) {
                        this.isAvailableSMS = this.mUsimServiceTable.isAvailable(UsimService.SM_STORAGE);
                    }
                } else if (this.mUsimServiceTable != null) {
                    logi("isSSTActive(SMS) : " + this.mUsimServiceTable.isSSTActive(SimService.SMS));
                    logi("isSSTAvaiable(SMS) : " + this.mUsimServiceTable.isSSTAvailable(SimService.SMS));
                    if (!(this.mUsimServiceTable.isSSTActive(SimService.SMS) && this.mUsimServiceTable.isSSTAvailable(SimService.SMS))) {
                        z = false;
                    }
                    this.isAvailableSMS = z;
                }
            }
            logi("isAvailableSMS is " + this.isAvailableSMS);
        } catch (ArrayIndexOutOfBoundsException e) {
            loge("Exception", e);
            this.isAvailableSMS = false;
        }
    }

    private void checkPSISMSCavailable(byte[] table) {
        logi("Enter  checkPSISMSCavailable");
        try {
            if ("2".equals(this.mIccType)) {
                if (this.mUsimServiceTable != null) {
                    boolean z;
                    if (this.mUsimServiceTable.isAvailable(UsimService.SM_SERVICE_PARAMS) && this.mUsimServiceTable.isAvailable(UsimService.SM_OVER_IP)) {
                        z = true;
                    } else {
                        z = false;
                    }
                    this.mIsAvailablePSISMSC = z;
                }
                logi("isAvailablePSISMSC is " + this.mIsAvailablePSISMSC);
                return;
            }
            logi("ICCType is Unknown or 2G");
        } catch (ArrayIndexOutOfBoundsException e) {
            loge("Exception", e);
            this.mIsAvailablePSISMSC = false;
        }
    }

    private void checkMBDNavailable(byte[] table) {
        logi("Enter  checkMBDNavailable");
        try {
            if ("1".equals(this.mIccType)) {
                if (this.mUsimServiceTable != null) {
                    boolean z;
                    logi("isSSTActive(MBDN) : " + this.mUsimServiceTable.isSSTActive(SimService.MAILBOX_DIALLING_NUMBERS));
                    logi("isSSTAvaiable(MBDN) : " + this.mUsimServiceTable.isSSTAvailable(SimService.MAILBOX_DIALLING_NUMBERS));
                    if (this.mUsimServiceTable.isSSTActive(SimService.MAILBOX_DIALLING_NUMBERS) && this.mUsimServiceTable.isSSTAvailable(SimService.MAILBOX_DIALLING_NUMBERS)) {
                        z = true;
                    } else {
                        z = false;
                    }
                    this.isAvailableMBDN = z;
                }
            } else if (!"2".equals(this.mIccType)) {
                logi("ICCType is Unknown");
                return;
            } else if (this.mUsimServiceTable != null) {
                this.isAvailableMBDN = this.mUsimServiceTable.isAvailable(UsimService.MBDN);
            }
        } catch (ArrayIndexOutOfBoundsException e) {
            loge("Exception", e);
            this.isAvailableMBDN = false;
        }
        logi("isAvailableMBDN is " + this.isAvailableMBDN);
        if (this.isAvailableMBDN) {
            this.mRecordsToLoad++;
            this.mFh.loadEFLinearFixed(IccConstants.EF_MBI, 1, obtainMessage(5));
            return;
        }
        this.mRecordsToLoad++;
        new AdnRecordLoader(this.mFh).loadFromEF(IccConstants.EF_MAILBOX_CPHS, IccConstants.EF_EXT1, 1, obtainMessage(11));
    }

    private void checkMWISavailable(byte[] table) {
        logi("Enter checkMWIS");
        try {
            if ("1".equals(this.mIccType)) {
                if (this.mUsimServiceTable != null) {
                    boolean z;
                    logi("isSSTActive(MWIS) : " + this.mUsimServiceTable.isSSTActive(SimService.MWIS));
                    logi("isSSTAvaiable(MWIS) : " + this.mUsimServiceTable.isSSTAvailable(SimService.MWIS));
                    if (this.mUsimServiceTable.isSSTActive(SimService.MWIS) && this.mUsimServiceTable.isSSTAvailable(SimService.MWIS)) {
                        z = true;
                    } else {
                        z = false;
                    }
                    this.isAvailableMWIS = z;
                }
            } else if (!"2".equals(this.mIccType)) {
                logi("ICCType is Unknown");
                return;
            } else if (this.mUsimServiceTable != null) {
                this.isAvailableMWIS = this.mUsimServiceTable.isAvailable(UsimService.MWI_STATUS);
            }
            logi("isAvailableMWIS is " + this.isAvailableMWIS);
            if (this.isAvailableMWIS) {
                this.mFh.loadEFLinearFixed(IccConstants.EF_MWIS, 1, obtainMessage(7));
                this.mRecordsToLoad++;
                return;
            }
            this.mFh.loadEFTransparent(IccConstants.EF_VOICE_MAIL_INDICATOR_CPHS, obtainMessage(8));
            this.mRecordsToLoad++;
        } catch (ArrayIndexOutOfBoundsException e) {
            logi("ArrayIndexOutOfBoundsException");
            this.isAvailableMWIS = false;
        }
    }

    private void checkCFISavailable(byte[] table) {
        logi("Enter checkCFIS");
        try {
            if ("1".equals(this.mIccType)) {
                if (this.mUsimServiceTable != null) {
                    boolean z;
                    logi("isSSTActive(CFIS) : " + this.mUsimServiceTable.isSSTActive(SimService.CFIS));
                    logi("isSSTAvaiable(CFIS) : " + this.mUsimServiceTable.isSSTAvailable(SimService.CFIS));
                    if (this.mUsimServiceTable.isSSTActive(SimService.CFIS) && this.mUsimServiceTable.isSSTAvailable(SimService.CFIS)) {
                        z = true;
                    } else {
                        z = false;
                    }
                    this.isAvailableCFIS = z;
                }
            } else if (!"2".equals(this.mIccType)) {
                logi("ICCType is Unknown");
                return;
            } else if (this.mUsimServiceTable != null) {
                this.isAvailableCFIS = this.mUsimServiceTable.isAvailable(UsimService.CFI_STATUS);
            }
            logi("isAvailableCFIS is " + this.isAvailableCFIS);
            if (this.isAvailableCFIS) {
                this.mFh.loadEFLinearFixed(IccConstants.EF_CFIS, 1, obtainMessage(32));
                this.mRecordsToLoad++;
                return;
            }
            this.mFh.loadEFTransparent(IccConstants.EF_CFF_CPHS, obtainMessage(24));
            this.mRecordsToLoad++;
        } catch (ArrayIndexOutOfBoundsException e) {
            logi("ArrayIndexOutOfBoundsException");
            this.isAvailableCFIS = false;
        }
    }

    private void notifyCallForwardIndication() {
        if (this.mParentApp.getState() == AppState.APPSTATE_READY) {
            log("[NAM] SIM Ready - cf icon voice value: " + this.NV_cfflag_voice);
            log("[NAM] SIM Ready - cf icon video value: " + this.NV_cfflag_video);
            this.voicecallForwardingEnabled = this.NV_cfflag_voice;
            this.videocallForwardingEnabled = this.NV_cfflag_video;
            this.mRecordsEventsRegistrants.notifyResult(Integer.valueOf(1));
            return;
        }
        log("[NAM] Not SIM Ready - cf icon voice value: " + this.NV_cfflag_voice);
        log("[NAM] Not SIM Ready - cf icon video value: " + this.NV_cfflag_video);
        this.NV_cfflag_voice = false;
        this.NV_cfflag_video = false;
        Editor editor2 = PreferenceManager.getDefaultSharedPreferences(this.mContext).edit();
        editor2.putBoolean(GSMPhone.CF_ICONKEY_VOICE, this.NV_cfflag_voice);
        editor2.putBoolean(GSMPhone.CF_ICONKEY_VIDEO, this.NV_cfflag_video);
        editor2.commit();
        editor2.putString(GSMPhone.CF_IMSIKEY, null);
        editor2.commit();
    }

    private void checkSimChanged() {
        log("checkSimChanged enter");
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this.mContext);
        Intent intent = new Intent("com.android.action.SIM_ICCID_CHANGED");
        if (this.mOldICCID == null) {
            this.mOldICCID = sp.getString(KEY_ICCID, "");
        }
        String old = this.mOldICCID;
        if (old == null) {
            Editor editor = sp.edit();
            editor.putString(KEY_ICCID, this.mIccId);
            editor.commit();
            setSystemProperty(propNameChangedICC, "1");
            this.mContext.sendBroadcast(intent);
            return;
        }
        if (SHIP_BUILD) {
            logi("old iccid is ******  current is ******");
        } else {
            logi("old iccid is " + old + "  current is " + this.mIccId);
        }
        if (!old.equals(this.mIccId)) {
            editor = sp.edit();
            editor.putString(KEY_ICCID, this.mIccId);
            editor.commit();
            setSystemProperty(propNameChangedICC, "1");
            this.mContext.sendBroadcast(intent);
            return;
        }
        setSystemProperty(propNameChangedICC, "0");
    }

    private void handleFileUpdate(int efid) {
        switch (efid) {
            case IccConstants.EF_CSP_CPHS /*28437*/:
                this.mRecordsToLoad++;
                log("[CSP] SIM Refresh for EF_CSP_CPHS");
                this.mFh.loadEFTransparent(IccConstants.EF_CSP_CPHS, obtainMessage(33));
                return;
            case IccConstants.EF_MAILBOX_CPHS /*28439*/:
                this.mRecordsToLoad++;
                new AdnRecordLoader(this.mFh).loadFromEF(IccConstants.EF_MAILBOX_CPHS, IccConstants.EF_EXT1, 1, obtainMessage(11));
                return;
            case IccConstants.EF_FDN /*28475*/:
                log("SIM Refresh called for EF_FDN");
                this.mParentApp.queryFdn();
                return;
            case IccConstants.EF_SMSP /*28482*/:
                this.mFh.loadEFLinearFixed(IccConstants.EF_SMSP, 1, obtainMessage(61));
                this.mRecordsToLoad++;
                log("loading EF_SMSP on refresh");
                this.mContext.sendBroadcast(new Intent("com.android.action.SIM_REFRESH_INIT"));
                this.isRefreshedBySTK = false;
                return;
            case IccConstants.EF_FPLMN /*28539*/:
                this.mRecordsToLoad++;
                this.mFh.loadEFTransparent(IccConstants.EF_FPLMN, obtainMessage(60));
                return;
            case IccConstants.EF_MBDN /*28615*/:
                this.mRecordsToLoad++;
                new AdnRecordLoader(this.mFh).loadFromEF(IccConstants.EF_MBDN, IccConstants.EF_EXT6, this.mMailboxIndex, obtainMessage(6));
                return;
            default:
                this.mAdnCache.reset();
                fetchSimRecords();
                return;
        }
    }

    private void handleFileUpdateExt(int efid) {
        this.mRecordsRequested = true;
        this.mIccType = getSystemProperty("ril.ICC_TYPE", "");
        logv("SIMRecords:HandleFileUpdateEXT " + this.mRecordsToLoad);
        this.mFh.loadEFTransparent(IccConstants.EF_ICCID, obtainMessage(4));
        this.mRecordsToLoad++;
        switch (efid) {
            case IccConstants.EF_OCSGL /*20356*/:
                this.mFh.loadEFLinearFixedAll(IccConstants.EF_OCSGL, obtainMessage(100, new EfOcsglLoaded()));
                this.mRecordsToLoad++;
                return;
            case IccConstants.EF_SPN_CPHS /*28436*/:
            case IccConstants.EF_SPN_SHORT_CPHS /*28440*/:
            case IccConstants.EF_SPN /*28486*/:
                getSpnFsm(true, null);
                return;
            case IccConstants.EF_CSP_CPHS /*28437*/:
                this.mFh.loadEFTransparent(IccConstants.EF_CSP_CPHS, obtainMessage(33));
                this.mRecordsToLoad++;
                return;
            case IccConstants.EF_INFO_CPHS /*28438*/:
                this.mFh.loadEFTransparent(IccConstants.EF_INFO_CPHS, obtainMessage(26));
                this.mRecordsToLoad++;
                return;
            case IccConstants.EF_SST /*28472*/:
                this.mFh.loadEFTransparent(IccConstants.EF_SST, obtainMessage(17));
                this.mRecordsToLoad++;
                return;
            case IccConstants.EF_MSISDN /*28480*/:
                if (this.isAvailableMSISDN) {
                    new AdnRecordLoader(this.mFh).loadFromEF(IccConstants.EF_MSISDN, IccConstants.EF_EXT1, 1, obtainMessage(10));
                    this.mRecordsToLoad++;
                }
                Intent intent = new Intent("com.samsung.intent.action.PB_SYNC");
                return;
            case IccConstants.EF_AD /*28589*/:
                this.mFh.loadEFTransparent(IccConstants.EF_AD, obtainMessage(9));
                this.mRecordsToLoad++;
                return;
            case IccConstants.EF_PNN /*28613*/:
            case IccConstants.EF_OPL /*28614*/:
                this.mFh.loadEFLinearFixedAll(IccConstants.EF_OPL, obtainMessage(37));
                this.mRecordsToLoad++;
                this.mFh.loadEFLinearFixedAll(IccConstants.EF_PNN, obtainMessage(15));
                this.mRecordsToLoad++;
                return;
            case IccConstants.EF_MBI /*28617*/:
                this.mFh.loadEFLinearFixed(IccConstants.EF_MBI, 1, obtainMessage(5));
                this.mRecordsToLoad++;
                return;
            case IccConstants.EF_MWIS /*28618*/:
                this.mFh.loadEFLinearFixed(IccConstants.EF_MWIS, 1, obtainMessage(7));
                this.mRecordsToLoad++;
                return;
            case IccConstants.EF_CFIS /*28619*/:
                this.mFh.loadEFLinearFixed(IccConstants.EF_CFIS, 1, obtainMessage(32));
                this.mRecordsToLoad++;
                return;
            case IccConstants.EF_SPDI /*28621*/:
                this.mFh.loadEFTransparent(IccConstants.EF_SPDI, obtainMessage(13));
                this.mRecordsToLoad++;
                return;
            default:
                this.isRefreshedBySTK = false;
                return;
        }
    }

    private void handleSimRefresh(IccRefreshResponse refreshResponse) {
        if (refreshResponse == null) {
            log("handleSimRefresh received without input");
        } else if (refreshResponse.aid == null || refreshResponse.aid.equals(this.mParentApp.getAid())) {
            switch (refreshResponse.refreshResult) {
                case 0:
                    log("handleSimRefresh with SIM_FILE_UPDATED");
                    handleFileUpdate(refreshResponse.efId);
                    return;
                case 1:
                    log("handleSimRefresh with SIM_REFRESH_INIT");
                    onIccRefreshInit();
                    return;
                case 2:
                    log("handleSimRefresh with SIM_REFRESH_RESET");
                    if (requirePowerOffOnSimRefreshReset()) {
                        this.mCi.setRadioPower(false, null);
                    }
                    this.mAdnCache.reset();
                    return;
                default:
                    log("handleSimRefresh with unknown operation");
                    return;
            }
        }
    }

    private int dispatchGsmMessage(SmsMessage message) {
        this.mNewSmsRegistrants.notifyResult(message);
        return 0;
    }

    private void handleSms(byte[] ba) {
        if (ba[0] != (byte) 0) {
            Rlog.d("ENF", "status : " + ba[0]);
        }
        if (ba[0] == (byte) 3) {
            int n = ba.length;
            byte[] pdu = new byte[(n - 1)];
            System.arraycopy(ba, 1, pdu, 0, n - 1);
            dispatchGsmMessage(SmsMessage.createFromPdu(pdu, SmsMessage.FORMAT_3GPP));
        }
    }

    private void handleSmses(ArrayList<byte[]> messages) {
        int count = messages.size();
        for (int i = 0; i < count; i++) {
            byte[] ba = (byte[]) messages.get(i);
            if (ba[0] != (byte) 0) {
                Rlog.i("ENF", "status " + i + ": " + ba[0]);
            }
            if (ba[0] == (byte) 3) {
                int n = ba.length;
                byte[] pdu = new byte[(n - 1)];
                System.arraycopy(ba, 1, pdu, 0, n - 1);
                dispatchGsmMessage(SmsMessage.createFromPdu(pdu, SmsMessage.FORMAT_3GPP));
                ba[0] = (byte) 1;
            }
        }
    }

    private String handleSktEf(byte[] data) {
        String Min1;
        char[] mintab = new char[]{'1', '2', '3', '4', '5', '6', '7', '8', '9', '0'};
        long imsi_s2 = (long) (((data[2] & 255) << 8) | (data[1] & 255));
        long imsi_s1 = (long) (((data[5] & 255) << 16) | (((data[4] & 255) << 8) | (data[3] & 255)));
        if (imsi_s1 == 0) {
            Min1 = "0000000";
        } else {
            long temp = imsi_s1 >>> 14;
            Min1 = "" + mintab[(int) (temp / 100)];
            temp %= 100;
            Min1 = (Min1 + mintab[(int) (temp / 10)]) + mintab[(int) (temp % 10)];
            imsi_s1 &= 16383;
            temp = (imsi_s1 >>> 10) & 15;
            StringBuilder append = new StringBuilder().append(Min1);
            if (temp == 10) {
                temp = 0;
            }
            Min1 = append.append(temp).toString();
            temp = imsi_s1 & 1023;
            Min1 = Min1 + mintab[(int) (temp / 100)];
            temp %= 100;
            Min1 = (Min1 + mintab[(int) (temp / 10)]) + mintab[(int) (temp % 10)];
        }
        log("Min1: " + Min1);
        String Min2 = "" + imsi_s2;
        Min2 = "" + mintab[(int) (imsi_s2 / 100)];
        imsi_s2 %= 100;
        Min2 = (Min2 + mintab[(int) (imsi_s2 / 10)]) + mintab[(int) (imsi_s2 % 10)];
        log("Min2: " + Min2);
        if (Min2.charAt(0) != '0') {
            return "0" + Min2 + Min1;
        }
        return Min2 + Min1;
    }

    private void handlePNN(ArrayList messages) {
        int count = messages.size();
        this.PNN_Value = new String[count];
        String fdata = "ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff";
        for (int i = 0; i < count; i++) {
            byte[] data = (byte[]) messages.get(i);
            String sdata = IccUtils.bytesToHexString(data);
            if (data != null) {
                SimTlv tlv = new SimTlv(data, 0, data.length);
                if (tlv.isValidObject() && tlv.getTag() == 67) {
                    if (sdata.compareTo(fdata) == 0) {
                        this.PNN_Value[i] = null;
                    } else {
                        this.PNN_Value[i] = IccUtils.networkNameToString(tlv.getData(), 0, tlv.getData().length);
                    }
                }
                logi("[handlePnns] Load PNN Value[" + i + "] = " + this.PNN_Value[i]);
                this.mIsPNNExist = true;
            } else {
                logi("[handlePNNs] data is Null !!!");
            }
        }
    }

    private void handleOPL(ArrayList messages) {
        String fdata = "ffffffffffffffff";
        int count = messages.size();
        this.OPL_count = 0;
        this.OPL_MCCMNC = new String[count];
        this.OPL_LAC1 = new int[count];
        this.OPL_LAC2 = new int[count];
        this.OPL_INDEX = new int[count];
        for (int i = 0; i < count; i++) {
            String sdata = IccUtils.bytesToHexString((byte[]) messages.get(i));
            if (sdata.compareTo(fdata) == 0) {
                logi("[handleOPL] EF_OPL contains Null");
                this.OPL_MCCMNC[i] = null;
                this.OPL_LAC1[i] = 0;
                this.OPL_LAC2[i] = 0;
                this.OPL_INDEX[i] = 0;
            } else {
                logi("[handleOPL] EF_OPL contains Data(Not Null)");
                this.OPL_MCCMNC[i] = IccUtils.MccMncConvert(sdata.substring(0, 6));
                if (this.OPL_MCCMNC[i] != null) {
                    this.OPL_LAC1[i] = Integer.parseInt(sdata.substring(6, 10), 16);
                    this.OPL_LAC2[i] = Integer.parseInt(sdata.substring(10, 14), 16);
                    this.OPL_INDEX[i] = Integer.parseInt(sdata.substring(14, 16), 16);
                } else {
                    this.OPL_LAC1[i] = 0;
                    this.OPL_LAC2[i] = 0;
                    this.OPL_INDEX[i] = 0;
                }
            }
            logi("[handleOPL]Load OPL_MCCMNC[" + i + "]=" + this.OPL_MCCMNC[i]);
            logi("[handleOPL]Load OPL_LAC1[" + i + "]=" + this.OPL_LAC1[i]);
            logi("[handleOPL]Load OPL_LAC2[" + i + "]=" + this.OPL_LAC2[i]);
            logi("[handleOPL]Load OPL_INDEX[" + i + "]=" + this.OPL_INDEX[i]);
            this.OPL_count++;
            this.mIsOPLExist = true;
        }
    }

    private String findBestLanguage(byte[] languages) {
        String[] locales = this.mContext.getAssets().getLocales();
        if (languages == null || locales == null) {
            return null;
        }
        int i = 0;
        while (i + 1 < languages.length) {
            try {
                String lang = new String(languages, i, 2, "ISO-8859-1");
                log("languages from sim = " + lang);
                int j = 0;
                while (j < locales.length) {
                    if (locales[j] != null && locales[j].length() >= 2 && locales[j].substring(0, 2).equalsIgnoreCase(lang)) {
                        return lang;
                    }
                    j++;
                }
                if (null != null) {
                    break;
                }
                i += 2;
            } catch (UnsupportedEncodingException e) {
                log("Failed to parse USIM language records" + e);
            }
        }
        return null;
    }

    private void setLocaleFromUsim() {
        String prefLang = findBestLanguage(this.mEfLi);
        if (prefLang == null) {
            prefLang = findBestLanguage(this.mEfPl);
        }
        if (prefLang != null) {
            String imsi = getIMSI();
            String country = null;
            if (imsi != null) {
                country = MccTable.countryCodeForMcc(Integer.parseInt(imsi.substring(0, 3)));
            }
            log("Setting locale to " + prefLang + "_" + country);
            MccTable.setSystemLocale(this.mContext, prefLang, country);
            return;
        }
        log("No suitable USIM selected locale");
    }

    private boolean isNeedToShareWithSensor(String operator) {
        if (operator != null && (operator.trim().equalsIgnoreCase("311480") || operator.trim().equalsIgnoreCase("20404") || operator.trim().equalsIgnoreCase("2044"))) {
            return true;
        }
        log("Non VZW SIM");
        return false;
    }

    private void setSysfsForSensor() {
        FileNotFoundException e;
        IOException e2;
        Throwable th;
        File file = new File("/sys/class/sensors/grip_sensor/sim_type");
        FileOutputStream fileOutputStream = null;
        try {
            FileOutputStream out = new FileOutputStream(file);
            if (file == null) {
                try {
                    log("sysfs - sensor/sim_type => null");
                } catch (FileNotFoundException e3) {
                    e = e3;
                    fileOutputStream = out;
                    try {
                        log("sysfs for sensor new File error " + e);
                        if (fileOutputStream != null) {
                            try {
                                fileOutputStream.close();
                            } catch (IOException e22) {
                                log("IOException caught while closing fileOutputStream " + e22);
                                return;
                            }
                        }
                    } catch (Throwable th2) {
                        th = th2;
                        if (fileOutputStream != null) {
                            try {
                                fileOutputStream.close();
                            } catch (IOException e222) {
                                log("IOException caught while closing fileOutputStream " + e222);
                            }
                        }
                        throw th;
                    }
                } catch (IOException e4) {
                    e222 = e4;
                    fileOutputStream = out;
                    log("initSelloutSms createNewFile error " + e222);
                    if (fileOutputStream != null) {
                        try {
                            fileOutputStream.close();
                        } catch (IOException e2222) {
                            log("IOException caught while closing fileOutputStream " + e2222);
                            return;
                        }
                    }
                } catch (Throwable th3) {
                    th = th3;
                    fileOutputStream = out;
                    if (fileOutputStream != null) {
                        fileOutputStream.close();
                    }
                    throw th;
                }
            } else if (!file.exists()) {
                log("There's no sysfs - sensor/sim_type");
            } else if (out != null) {
                out.write(49);
            }
            if (out != null) {
                try {
                    out.close();
                    fileOutputStream = out;
                    return;
                } catch (IOException e22222) {
                    log("IOException caught while closing fileOutputStream " + e22222);
                    fileOutputStream = out;
                    return;
                }
            }
        } catch (FileNotFoundException e5) {
            e = e5;
            log("sysfs for sensor new File error " + e);
            if (fileOutputStream != null) {
                fileOutputStream.close();
            }
        } catch (IOException e6) {
            e22222 = e6;
            log("initSelloutSms createNewFile error " + e22222);
            if (fileOutputStream != null) {
                fileOutputStream.close();
            }
        }
    }

    protected void onRecordLoaded() {
        this.mRecordsToLoad--;
        log("onRecordLoaded " + this.mRecordsToLoad + " requested: " + this.mRecordsRequested);
        if (this.mRecordsToLoad == 0 && this.mRecordsRequested) {
            onAllRecordsLoaded();
        } else if (this.mRecordsToLoad < 0) {
            loge("recordsToLoad <0, programmer error suspected");
            this.mRecordsToLoad = 0;
        }
    }

    protected void onAllRecordsLoaded() {
        log("record load complete");
        setLocaleFromUsim();
        if (this.mParentApp.getState() == AppState.APPSTATE_PIN || this.mParentApp.getState() == AppState.APPSTATE_PUK) {
            this.mRecordsRequested = false;
            return;
        }
        String operator = getOperatorNumeric();
        if (TextUtils.isEmpty(operator)) {
            log("onAllRecordsLoaded empty 'gsm.sim.operator.numeric' skipping");
        } else {
            if ("VZW-CDMA".equals("") && isNeedToShareWithSensor(operator)) {
                setSysfsForSensor();
            }
            log("onAllRecordsLoaded set 'gsm.sim.operator.numeric' to operator='" + operator + "'");
            if ("DCG".equals("DGG") || "DCGG".equals("DGG") || "DCGS".equals("DGG") || "DCGGS".equals("DGG") || "CG".equals("DGG")) {
                String simselswitch;
                if (this.phone.getPhoneId() == 0) {
                    log("slot1 gsm sim operater.");
                    SystemProperties.set("gsm.sim.gsmoperator.numeric", operator);
                }
                simselswitch = SystemProperties.get("gsm.sim.selectnetwork");
                log("simselswitch = " + simselswitch);
                if ("CDMA".equals(simselswitch) && this.phone.getPhoneId() == 0) {
                    log("do not set PROPERTY_ICC_OPERATOR_NUMERIC in case of slot1 cdma switching");
                    SystemProperties.set("gsm.sim.gsmoperator.numeric", operator);
                } else {
                    setSystemProperty("gsm.sim.operator.numeric", operator);
                }
            } else {
                log("update icc_operator_numeric=" + operator);
                setSystemProperty("gsm.sim.operator.numeric", operator);
            }
        }
        if (!(getOperatorNumeric() == null || "DISABLE".equals(CscFeature.getInstance().getString("CscFeature_Common_AutoConfigurationType", "DISABLE")))) {
            logi("Binding AutoPreconfigService");
            Intent preconfigIntent = new Intent();
            preconfigIntent.setClassName("com.sec.android.AutoPreconfig", "com.sec.android.AutoPreconfig.AutoPreconfigService");
            this.mContext.bindService(preconfigIntent, this.mAutoPreconfigServiceConnection, 1);
            this.mIsAPBound = true;
        }
        if (TextUtils.isEmpty(this.mImsi)) {
            log("onAllRecordsLoaded empty imsi skipping setting mcc");
        } else if ("LGT".equals("")) {
            String simType = SystemProperties.get("ril.simtype");
            if (simType != null && ("3".equals(simType) || "18".equals(simType))) {
                this.phone.setSystemProperty("gsm.sim.operator.iso-country", "kr");
            }
        } else if ("DCG".equals("DGG") || "DCGG".equals("DGG") || "DCGS".equals("DGG") || "DCGGS".equals("DGG") || "CG".equals("DGG")) {
            simselswitch = SystemProperties.get("gsm.sim.selectnetwork");
            log("simselswitch = " + simselswitch);
            if ("CDMA".equals(simselswitch) && this.phone.getPhoneId() == 0) {
                log("do not set PROPERTY_ICC_OPERATOR_NUMERIC in case of slot1 cdma switching");
            } else {
                setSystemProperty("gsm.sim.operator.iso-country", MccTable.countryCodeForMcc(Integer.parseInt(this.mImsi.substring(0, 3))));
            }
        } else {
            setSystemProperty("gsm.sim.operator.iso-country", MccTable.countryCodeForMcc(Integer.parseInt(this.mImsi.substring(0, 3))));
        }
        Editor editor_o2 = PreferenceManager.getDefaultSharedPreferences(this.mContext).edit();
        editor_o2.putInt(KEY_PAYSTATE, getO2payState().value());
        editor_o2.commit();
        this.mRecordsLoadedRegistrants.notifyRegistrants(new AsyncResult(null, null, null));
    }

    void InitAutopreconfig() {
        logi("AutoPreconfig : got mccmnc " + getOperatorNumeric());
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this.mContext);
        String gid1 = "";
        if (preferences != null) {
            String key_gid = KEY_GID1;
            if (this.phone.getPhoneId() != 0) {
                key_gid = "key" + Integer.toString(this.phone.getPhoneId() + 1) + "_gid1";
            }
            gid1 = preferences.getString(key_gid, "");
            logi("AutoPreconfig : got gid1 " + gid1);
        }
        String spCode = "";
        int startIdx = getOperatorNumeric().length() + 1;
        spCode = TextUtils.substring(this.mImsi, startIdx, startIdx + 2);
        logi("AutoPreconfig : got spcode " + spCode);
        String spName = "";
        if (this.mSpn != null) {
            spName = this.mSpn;
        }
        logi("AutoPreconfig : got spname " + spName);
        try {
            Intent preconfigParamIntent = new Intent();
            preconfigParamIntent.setClassName("com.sec.android.AutoPreconfig", "com.sec.android.AutoPreconfig.AutoPreconfigService");
            preconfigParamIntent.putExtra("MCCMNC", getOperatorNumeric());
            preconfigParamIntent.putExtra("GID1", gid1);
            preconfigParamIntent.putExtra("SPCODE", spCode);
            preconfigParamIntent.putExtra("SPNAME", spName);
            Message msg = new Message();
            msg.what = INIT_AUTOPRECONFIG;
            msg.obj = preconfigParamIntent;
            this.mAutoPreconfigService.send(msg);
        } catch (RemoteException ex) {
            logi("mAutoPreconfigService RemoteException" + ex);
        }
    }

    private void setSpnFromConfig(String carrier, String imsi) {
        if (this.mSpnOverride.containsCarrier(carrier)) {
            setServiceProviderName(this.mSpnOverride.getSpn(carrier, imsi));
            setSystemProperty("gsm.sim.operator.alpha", getServiceProviderName());
        }
    }

    private void setVoiceMailByCountry(String nwkName) {
        log("setVoiceMailByCountry: NetworkName = " + nwkName);
        if (nwkName != null && this.mVmConfig != null && this.mVmConfig.containsCarrier(nwkName)) {
            this.mVoiceMailNum = this.mVmConfig.getVoiceMailNumber(nwkName);
            this.mVoiceMailTag = this.mVmConfig.getVoiceMailTag(nwkName);
            if (!(TextUtils.isEmpty(this.mVoiceMailNum) || "DTM".equals(SystemProperties.get("ro.csc.sales_code")) || "KPP".equals(SystemProperties.get("ro.csc.sales_code")) || "KPN".equals(SystemProperties.get("ro.csc.sales_code")) || "XFA".equals(SystemProperties.get("ro.csc.sales_code")) || "XFM".equals(SystemProperties.get("ro.csc.sales_code")) || "XFC".equals(SystemProperties.get("ro.csc.sales_code")) || "XFE".equals(SystemProperties.get("ro.csc.sales_code")) || "XFV".equals(SystemProperties.get("ro.csc.sales_code")) || "VDS".equals(SystemProperties.get("ro.csc.sales_code")))) {
                this.mIsVoiceMailFixed = true;
            }
            log("setVoiceMailByCountry: isVoiceMailFixed=" + this.mIsVoiceMailFixed);
        }
    }

    public void onReady() {
        fetchSimRecords();
    }

    private void onLocked() {
        log(" fetch EF_LI, EF_PL and EF_ICCID in lock state");
        loadEfLiAndEfPl();
        this.mFh.loadEFTransparent(IccConstants.EF_ICCID, obtainMessage(42));
    }

    private void loadEfLiAndEfPl() {
        if (this.mParentApp.getType() == AppType.APPTYPE_USIM) {
            this.mRecordsRequested = true;
            this.mFh.loadEFTransparent(IccConstants.EF_LI, obtainMessage(100, new EfUsimLiLoaded()));
            this.mRecordsToLoad++;
            this.mFh.loadEFTransparent(IccConstants.EF_PL, obtainMessage(100, new EfPlLoaded()));
            this.mRecordsToLoad++;
        }
    }

    protected void fetchSimRecords() {
        this.mRecordsRequested = true;
        this.mIccType = getSystemProperty("ril.ICC_TYPE", "");
        log("fetchSimRecords " + this.mRecordsToLoad);
        this.mCi.getIMSIForApp(this.mParentApp.getAid(), obtainMessage(3));
        this.mRecordsToLoad++;
        this.mRetryCountGetImsi = 0;
        this.mFh.loadEFTransparent(IccConstants.EF_ICCID, obtainMessage(4));
        this.mRecordsToLoad++;
        this.mFh.loadEFTransparent(IccConstants.EF_SST, obtainMessage(17));
        this.mRecordsToLoad++;
        if ("EFversion".equals("")) {
            this.mFh.loadEFTransparent(IccConstants.EF_VER, obtainMessage(52));
            this.mRecordsToLoad++;
        }
        this.mFh.loadEFTransparent(IccConstants.EF_GID1, obtainMessage(34));
        this.mRecordsToLoad++;
        this.mFh.loadEFLinearFixed(IccConstants.EF_SMSP, 1, obtainMessage(61));
        this.mRecordsToLoad++;
        this.mFh.loadEFLinearFixed(IccConstants.EF_MBI, 1, obtainMessage(5));
        this.mRecordsToLoad++;
        this.mFh.loadEFTransparent(IccConstants.EF_AD, obtainMessage(9));
        this.mRecordsToLoad++;
        getSpnFsm(true, null);
        this.mFh.loadEFTransparent(IccConstants.EF_SPDI, obtainMessage(13));
        this.mRecordsToLoad++;
        if (this.mEonsEnabled) {
            this.mFh.loadEFLinearFixedAll(IccConstants.EF_OPL, obtainMessage(37));
            this.mRecordsToLoad++;
            this.mFh.loadEFLinearFixedAll(IccConstants.EF_PNN, obtainMessage(15));
            this.mRecordsToLoad++;
        }
        this.mFh.loadEFTransparent(IccConstants.EF_INFO_CPHS, obtainMessage(26));
        this.mRecordsToLoad++;
        this.mFh.loadEFTransparent(IccConstants.EF_CSP_CPHS, obtainMessage(33));
        this.mRecordsToLoad++;
        if (CscFeature.getInstance().getEnableStatus("CscFeature_RIL_EnableOnsDisplay")) {
            log("Try to read ONS");
            this.mFh.loadEFTransparent(IccConstants.EF_SPN_CPHS, obtainMessage(35));
            this.mRecordsToLoad++;
        }
        loadEfLiAndEfPl();
        this.mFh.loadEFLinearFixed(IccConstants.EF_PSI, 1, obtainMessage(57));
        this.mRecordsToLoad++;
        this.mFh.loadEFTransparent(IccConstants.EF_FPLMN, obtainMessage(60));
        this.mRecordsToLoad++;
        this.mFh.loadEFTransparent(28418, obtainMessage(46));
        this.mRecordsToLoad++;
        log("fetchSimRecords " + this.mRecordsToLoad + " requested: " + this.mRecordsRequested);
    }

    public int getDisplayRule(String plmn) {
        if (this.spnDisplayRuleOverride > 0) {
            return this.spnDisplayRuleOverride;
        }
        if (!this.isAvailableSPN) {
            log("[getDisplayRule] SPN service disabled (EF_UST)");
            return 2;
        } else if (this.mSpn == null || this.mSpn.length() == 0 || this.mSpnDisplayCondition == -1) {
            log("[getDisplayRule] showing plmn only");
            return 2;
        } else if (isOnMatchingPlmn(plmn)) {
            if ((this.mSpnDisplayCondition & 1) == 1 || this.mSpn == null || this.mSpn.length() == 0) {
                return 1 | 2;
            }
            return 1;
        } else if ((this.mSpnDisplayCondition & 2) == 0) {
            return 2 | 1;
        } else {
            return 2;
        }
    }

    public String getServiceProviderName() {
        if (this.spnOverride != null) {
            return this.spnOverride;
        }
        return super.getServiceProviderName();
    }

    private boolean isOnMatchingPlmn(String plmn) {
        if (plmn == null) {
            return false;
        }
        if (plmn.equals(getOperatorNumeric())) {
            return true;
        }
        if (this.mSpdiNetworks == null) {
            return false;
        }
        Iterator i$ = this.mSpdiNetworks.iterator();
        while (i$.hasNext()) {
            if (plmn.equals((String) i$.next())) {
                return true;
            }
        }
        return false;
    }

    private void getSpnFsm(boolean start, AsyncResult ar) {
        if (start) {
            if (this.mSpnState == GetSpnFsmState.READ_SPN_3GPP || this.mSpnState == GetSpnFsmState.READ_SPN_CPHS || this.mSpnState == GetSpnFsmState.READ_SPN_SHORT_CPHS || this.mSpnState == GetSpnFsmState.INIT) {
                this.mSpnState = GetSpnFsmState.INIT;
                return;
            }
            this.mSpnState = GetSpnFsmState.INIT;
        }
        byte[] data;
        switch (C01312.f16xdc363d50[this.mSpnState.ordinal()]) {
            case 1:
                setServiceProviderName(null);
                this.mFh.loadEFTransparent(IccConstants.EF_SPN, obtainMessage(12));
                this.mRecordsToLoad++;
                this.mSpnState = GetSpnFsmState.READ_SPN_3GPP;
                return;
            case 2:
                if (ar == null || ar.exception != null) {
                    this.mFh.loadEFTransparent(IccConstants.EF_SPN_CPHS, obtainMessage(12));
                    this.mRecordsToLoad++;
                    this.mSpnState = GetSpnFsmState.READ_SPN_CPHS;
                    this.mSpnDisplayCondition = -1;
                    return;
                }
                data = (byte[]) ar.result;
                this.mSpnDisplayCondition = data[0] & 255;
                setServiceProviderName(IccUtils.adnStringFieldToString(data, 1, data.length - 1));
                log("Load EF_SPN: " + getServiceProviderName() + " spnDisplayCondition: " + this.mSpnDisplayCondition);
                setSystemProperty("gsm.sim.operator.alpha", getServiceProviderName());
                this.mSpnState = GetSpnFsmState.IDLE;
                return;
            case 3:
                if (ar == null || ar.exception != null) {
                    this.mFh.loadEFTransparent(IccConstants.EF_SPN_SHORT_CPHS, obtainMessage(12));
                    this.mRecordsToLoad++;
                    this.mSpnState = GetSpnFsmState.READ_SPN_SHORT_CPHS;
                    return;
                }
                data = (byte[]) ar.result;
                setServiceProviderName(IccUtils.adnStringFieldToString(data, 0, data.length));
                log("Load EF_SPN_CPHS: " + getServiceProviderName());
                setSystemProperty("gsm.sim.operator.alpha", getServiceProviderName());
                this.mSpnState = GetSpnFsmState.IDLE;
                return;
            case 4:
                if (ar == null || ar.exception != null) {
                    log("No SPN loaded in either CHPS or 3GPP");
                } else {
                    data = (byte[]) ar.result;
                    setServiceProviderName(IccUtils.adnStringFieldToString(data, 0, data.length));
                    log("Load EF_SPN_SHORT_CPHS: " + getServiceProviderName());
                    setSystemProperty("gsm.sim.operator.alpha", getServiceProviderName());
                }
                this.mSpnState = GetSpnFsmState.IDLE;
                return;
            default:
                this.mSpnState = GetSpnFsmState.IDLE;
                return;
        }
    }

    private void parseEfSpdi(byte[] data) {
        SimTlv tlv = new SimTlv(data, 0, data.length);
        byte[] plmnEntries = null;
        while (tlv.isValidObject()) {
            if (tlv.getTag() == 163) {
                tlv = new SimTlv(tlv.getData(), 0, tlv.getData().length);
            }
            if (tlv.getTag() == 128) {
                plmnEntries = tlv.getData();
                break;
            }
            tlv.nextObject();
        }
        if (plmnEntries != null) {
            this.mSpdiNetworks = new ArrayList(plmnEntries.length / 3);
            for (int i = 0; i + 2 < plmnEntries.length; i += 3) {
                byte[] singlePlmn = new byte[3];
                System.arraycopy(plmnEntries, i, singlePlmn, 0, 3);
                String plmnCode = IccUtils.MccMncConvert(IccUtils.bytesToHexString(singlePlmn));
                if (plmnCode != null && plmnCode.length() >= 5) {
                    log("EF_SPDI network: " + plmnCode);
                    this.mSpdiNetworks.add(plmnCode);
                }
            }
        }
    }

    private boolean isCphsMailboxEnabled() {
        boolean z = true;
        if (this.mCphsInfo == null) {
            return false;
        }
        if ((this.mCphsInfo[1] & 48) != 48) {
            z = false;
        }
        return z;
    }

    protected void log(String s) {
        if (this.phone != null) {
            Rlog.d(MultiSimManager.appendSimSlot(LOG_TAG, this.phone.getPhoneId()), s);
        } else {
            Rlog.d(LOG_TAG, "[SIMRecords] " + s);
        }
    }

    protected void loge(String s) {
        if (this.phone != null) {
            Rlog.e(MultiSimManager.appendSimSlot(LOG_TAG, this.phone.getPhoneId()), s);
        } else {
            Rlog.e(LOG_TAG, "[SIMRecords] " + s);
        }
    }

    protected void logw(String s, Throwable tr) {
        if (this.phone != null) {
            Rlog.i(MultiSimManager.appendSimSlot(LOG_TAG, this.phone.getPhoneId()), s, tr);
        } else {
            Rlog.w(LOG_TAG, "[SIMRecords] " + s, tr);
        }
    }

    protected void logv(String s) {
        if (this.phone != null) {
            Rlog.i(MultiSimManager.appendSimSlot(LOG_TAG, this.phone.getPhoneId()), s);
        } else {
            Rlog.v(LOG_TAG, "[SIMRecords] " + s);
        }
    }

    protected void loge(String s, Throwable tr) {
        if (this.phone != null) {
            Rlog.i(MultiSimManager.appendSimSlot(LOG_TAG, this.phone.getPhoneId()), s, tr);
        } else {
            Rlog.e(LOG_TAG, "[SIMRecords] " + s, tr);
        }
    }

    protected void logi(String s) {
        if (this.phone != null) {
            Rlog.i(MultiSimManager.appendSimSlot(LOG_TAG, this.phone.getPhoneId()), s);
        } else {
            Rlog.i(LOG_TAG, "[SIMRecords] " + s);
        }
    }

    public String[] getSponImsi() {
        return this.mSponImsi != null ? (String[]) this.mSponImsi.clone() : null;
    }

    public void refreshUiccVer() {
        log("[refreshUiccVer] refreshed");
        this.mFh.loadEFTransparent(IccConstants.EF_VER, obtainMessage(52));
    }

    public boolean isCspPlmnEnabled() {
        return this.mCspPlmnEnabled;
    }

    private void handleEfCspData(byte[] data) {
        int usedCspGroups = data.length / 2;
        this.mCspPlmnEnabled = true;
        for (int i = 0; i < usedCspGroups; i++) {
            if (data[i * 2] == (byte) -64) {
                log("[CSP] found ValueAddedServicesGroup, value " + data[(i * 2) + 1]);
                if ((data[(i * 2) + 1] & 128) == 128) {
                    this.mCspPlmnEnabled = true;
                    return;
                }
                this.mCspPlmnEnabled = false;
                log("[CSP] Set Automatic Network Selection");
                this.mNetworkSelectionModeAutomaticRegistrants.notifyRegistrants();
                return;
            }
        }
        log("[CSP] Value Added Service Group (0xC0), not found!");
    }

    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        pw.println("SIMRecords: " + this);
        pw.println(" extends:");
        super.dump(fd, pw, args);
        pw.println(" mVmConfig=" + this.mVmConfig);
        pw.println(" mSpnOverride=" + this.mSpnOverride);
        pw.println(" mCallForwardingEnabled=" + this.mCallForwardingEnabled);
        pw.println(" mSpnState=" + this.mSpnState);
        pw.println(" mCphsInfo=" + this.mCphsInfo);
        pw.println(" mCspPlmnEnabled=" + this.mCspPlmnEnabled);
        pw.println(" mEfMWIS[]=" + Arrays.toString(this.mEfMWIS));
        pw.println(" mEfCPHS_MWI[]=" + Arrays.toString(this.mEfCPHS_MWI));
        pw.println(" mEfCff[]=" + Arrays.toString(this.mEfCff));
        pw.println(" mEfCfis[]=" + Arrays.toString(this.mEfCfis));
        pw.println(" mSpnDisplayCondition=" + this.mSpnDisplayCondition);
        pw.println(" mSpdiNetworks[]=" + this.mSpdiNetworks);
        pw.println(" mPnnHomeName=" + this.mPnnHomeName);
        pw.println(" mUsimServiceTable=" + this.mUsimServiceTable);
        pw.println(" mGid1=" + this.mGid1);
        pw.println(" mEonsEnabled=" + this.mEonsEnabled);
        pw.println(" mIsOPLExist=" + this.mIsOPLExist);
        pw.println(" mIsEnabledOPL=" + this.mIsEnabledOPL);
        pw.flush();
    }

    public boolean chekcMWISavailable() {
        if (this.mEfMWIS == null && this.mEfCPHS_MWI == null) {
            return false;
        }
        return true;
    }

    private boolean IsNANetwork(String plmn) {
        return plmn.substring(0, 3).matches("31[0-6]|302");
    }

    private boolean isMatchingHplmn(String network, String sim, boolean wild) {
        String networkPlmn = network;
        String simPlmn = sim;
        if (networkPlmn.length() == 5) {
            if (IsNANetwork(networkPlmn)) {
                networkPlmn = networkPlmn + "0";
            } else {
                simPlmn = simPlmn.substring(0, 5);
            }
        }
        if (wild) {
            return networkPlmn.matches("^" + simPlmn.replaceAll("[dD]", ".") + "$");
        }
        return networkPlmn.equals(simPlmn);
    }

    private boolean isMatchingHplmn(String network, String sim) {
        return isMatchingHplmn(network, sim, false);
    }

    public String getAllEonsNames(String MCCMNC, int LAC) {
        return getAllEonsNames(MCCMNC, LAC, true);
    }

    public String getAllEonsNames(String MCCMNC, int LAC, boolean useLAC) {
        String EonsName = null;
        String operatorNumeric = getOperatorNumeric();
        if (MCCMNC == null) {
            log("MCCMNC is null");
            return null;
        } else if (operatorNumeric == null) {
            log("SIMOperatorNumeric is null");
            return null;
        } else {
            try {
                boolean isHPLMN = isMatchingHplmn(MCCMNC, operatorNumeric);
                if (this.mIsPNNExist && this.mIsEnabledPNN) {
                    if (isHPLMN && (!this.mIsOPLExist || !this.mIsEnabledOPL)) {
                        EonsName = this.PNN_Value[0];
                    } else if (this.mIsOPLExist && this.mIsEnabledOPL) {
                        int i = 0;
                        while (i < this.OPL_count) {
                            if (this.OPL_MCCMNC[i] == null || !isMatchingHplmn(MCCMNC, this.OPL_MCCMNC[i], true) || (useLAC && (LAC < this.OPL_LAC1[i] || LAC > this.OPL_LAC2[i]))) {
                                i++;
                            } else if (this.OPL_INDEX[i] != 0) {
                                EonsName = this.PNN_Value[this.OPL_INDEX[i] - 1];
                            }
                        }
                    }
                }
                if (this.IsOnsExist && EonsName == null && !this.spn_cphs.isEmpty()) {
                    if (isHPLMN) {
                        EonsName = this.spn_cphs;
                    } else {
                        EonsName = null;
                    }
                }
                if (EonsName != null && EonsName.length() <= 0) {
                    EonsName = null;
                }
            } catch (RuntimeException e) {
                loge("Got exception while searching for EONS name, falling back to null", e);
                EonsName = null;
            }
            this.mEonsName = EonsName;
            return EonsName;
        }
    }

    public boolean getVideoCallForwardingFlag() {
        return this.videocallForwardingEnabled;
    }

    public void setVoiceCallForwardingFlag(int line, boolean enable) {
        setVoiceCallForwardingFlag(line, new Boolean(enable).booleanValue(), null);
    }

    public void setVoiceCallForwardingFlag(int line, boolean enable, String dialingNumber) {
        setCallForwardingFlag(line, new Boolean(enable), null, dialingNumber);
    }

    public void setVideoCallForwardingFlag(int line, boolean enable) {
        setVideoCallForwardingFlag(line, new Boolean(enable).booleanValue(), null);
    }

    public void setVideoCallForwardingFlag(int line, boolean enable, String dialingNumber) {
        setCallForwardingFlag(line, null, new Boolean(enable), dialingNumber);
    }

    public void setCallForwardingFlag(int line, Boolean voiceEnable, Boolean videoEnable) {
        setCallForwardingFlag(line, new Boolean(voiceEnable.booleanValue()), new Boolean(videoEnable.booleanValue()), null);
    }

    private String getSpnForCurrentLocale(String spnOverrideString) {
        if (spnOverrideString == null) {
            return null;
        }
        Locale curLoc = Locale.getDefault();
        String l = curLoc.getLanguage();
        String c = curLoc.getCountry();
        for (String so : spnOverrideString.split(",")) {
            String[] s = so.split(";");
            if (s.length == 1) {
                return s[0];
            }
            String[] entryLoc = s[0].split("_");
            if (entryLoc[0].equals(l) && (entryLoc.length == 1 || entryLoc[1].equals(c))) {
                return s[1];
            }
        }
        return null;
    }

    public void setSpnDynamic(String currentPlmn) {
        this.spnOverride = null;
        this.spnDisplayRuleOverride = -1;
        String simOper = currentPlmn;
        if (simOper != null && this.mSpnOverride.containsCarrier(simOper)) {
            String spnOverrideString = this.mSpnOverride.getSpn(simOper, getIMSI());
            int spnDisplayRule = this.mSpnOverride.getDisplayRule(simOper, getIMSI());
            String[] onlyOn = this.mSpnOverride.getOverrideOnlyOn(simOper, getIMSI());
            if (spnOverrideString != null || spnDisplayRule >= 0) {
                String spnForCurrentLocale = getSpnForCurrentLocale(spnOverrideString);
                if (onlyOn == null) {
                    this.spnOverride = spnForCurrentLocale;
                    this.spnDisplayRuleOverride = spnDisplayRule;
                } else if (currentPlmn != null && currentPlmn.length() >= 3) {
                    for (String p : onlyOn) {
                        if (p.equals(currentPlmn) || p.equals(currentPlmn.substring(0, 3))) {
                            this.spnOverride = spnForCurrentLocale;
                            this.spnDisplayRuleOverride = spnDisplayRule;
                            return;
                        }
                    }
                }
            }
        }
    }

    public String[] getFakeHomeOn() {
        String simOper = getSystemProperty("gsm.sim.operator.numeric", "");
        if (SHIP_BUILD) {
            logi("getFakeHomeOn() simOper[******], getIMSI()[******]");
        } else {
            logi("getFakeHomeOn() simOper[" + simOper + "], getIMSI()[" + getIMSI() + "]");
        }
        if (simOper != null && simOper.length() >= 3 && this.mSpnOverride.containsCarrier(simOper.substring(0, 3))) {
            logi("getFakeHomeOn() Check only MCC");
            return this.mSpnOverride.getFakeHomeOn(simOper.substring(0, 3), getIMSI());
        } else if (simOper == null || !this.mSpnOverride.containsCarrier(simOper)) {
            return null;
        } else {
            logi("getFakeHomeOn() Check MCC MNC");
            return this.mSpnOverride.getFakeHomeOn(simOper, getIMSI());
        }
    }

    public String[] getFakeRoamingOn() {
        return this.mSpnOverride.getFakeRoamingOn(getSystemProperty("gsm.sim.operator.numeric", ""), getIMSI());
    }

    public O2Paystate getO2payState() {
        String simState = SystemProperties.get("gsm.sim.state");
        String simOper = SystemProperties.get("gsm.sim.operator.numeric");
        O2Paystate result = O2Paystate.NOT_READY;
        log("getO2payState SIMState[" + simState + "]  MCCMNC[" + simOper + "]");
        log("perso " + IccUtils.bytesToHexString(this.perso));
        if (!"23410".equals(simOper) || !"READY".equals(simState)) {
            log("SIM state is not READY or SIM card is not O2 SIM");
        } else if (this.isAvailableO2PERSO) {
            log("isAvailableO2PERSO is true");
            if (this.perso == null) {
                log("It should not enter here!!!");
            } else {
                result = (this.perso[0] & 1) == 1 ? O2Paystate.O2_PostPay : O2Paystate.O2_PrePay;
            }
        } else {
            log("isAvailableO2PERSO is false. Check FDN");
            result = this.isAvailableFDN ? O2Paystate.O2_PostPay : O2Paystate.O2_PrePay;
        }
        log("The result of getO2payState is " + result);
        return result;
    }
}
