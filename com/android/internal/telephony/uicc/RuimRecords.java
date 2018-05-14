package com.android.internal.telephony.uicc;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.AsyncResult;
import android.os.Message;
import android.os.SystemProperties;
import android.telephony.Rlog;
import com.android.internal.telephony.CommandsInterface;
import com.android.internal.telephony.MccTable;
import com.android.internal.telephony.uicc.IccCardApplicationStatus.AppType;
import com.android.internal.telephony.uicc.IccRecords.IccRecordLoaded;
import com.samsung.android.telephony.MultiSimManager;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Locale;

public final class RuimRecords extends IccRecords {
    private static final int CST_ADN_BIT = 2;
    private static final int CST_ADN_BYTE = 0;
    private static final int CST_CHV1_BIT = 0;
    private static final int CST_CHV1_BYTE = 0;
    private static final int CST_FDN_BIT = 4;
    private static final int CST_FDN_BYTE = 0;
    private static final int CST_SMS_BIT = 6;
    private static final int CST_SMS_BYTE = 0;
    private static final int CST_SPN_BIT = 0;
    private static final int CST_SPN_BYTE = 4;
    private static final int EVENT_APP_READY = 1;
    private static final int EVENT_GET_ALL_SMS_DONE = 18;
    private static final int EVENT_GET_CDMA_SUBSCRIPTION_DONE = 10;
    private static final int EVENT_GET_CST_DONE = 16;
    private static final int EVENT_GET_DEVICE_IDENTITY_DONE = 4;
    private static final int EVENT_GET_EPRL_DONE = 49;
    private static final int EVENT_GET_ICCID_DONE = 5;
    private static final int EVENT_GET_ICCID_WHEN_LOCKED_DONE = 42;
    private static final int EVENT_GET_IMSIM_RETRY = 800;
    private static final int EVENT_GET_IMSI_DONE = 3;
    private static final int EVENT_GET_IMSI_M_DONE = 8;
    private static final int EVENT_GET_MASTERIMSI_DONE = 32;
    private static final int EVENT_GET_MDN_DONE = 6;
    private static final int EVENT_GET_MLPL_DONE = 50;
    private static final int EVENT_GET_MMSSMODE_DONE = 52;
    private static final int EVENT_GET_MSPL_DONE = 51;
    private static final int EVENT_GET_ROAMING_DONE = 36;
    private static final int EVENT_GET_RUIMID_DONE = 37;
    private static final int EVENT_GET_SMS_DONE = 22;
    private static final int EVENT_GET_SPONIMSI1_DONE = 33;
    private static final int EVENT_GET_SPONIMSI2_DONE = 34;
    private static final int EVENT_GET_SPONIMSI3_DONE = 35;
    private static final int EVENT_GET_SST_DONE = 17;
    private static final int EVENT_GET_UICCVER_DONE = 38;
    private static final int EVENT_MARK_SMS_READ_DONE = 19;
    private static final int EVENT_PB_INIT_COMPLETE = 53;
    private static final int EVENT_RUIM_REFRESH = 31;
    private static final int EVENT_SIM_LOCKED = 41;
    private static final int EVENT_SIM_PB_READY = 54;
    private static final int EVENT_SMS_ON_RUIM = 21;
    private static final int EVENT_UPDATE_DONE = 14;
    private static final String ICCID2_PATH = "/data/misc/radio/cicd2";
    private static final String ICCID_PATH = "/data/misc/radio/cicd";
    static final String LOG_TAG = "RuimRecords";
    static final String PROPERTY_CDMA_RUIMID = "ril.cdma.phone.id";
    static final String PROPERTY_SIM_ROAMING = "gsm.sim.roaming";
    static final String PROPERTY_UICC_VERSION = "gsm.sim.version";
    protected static final String UNACTIVATED_MIN_VALUE = "1111110111";
    String cMDN;
    private String countryISO;
    boolean isAvailableADN;
    boolean isAvailableCHV1;
    boolean isAvailableFDN;
    public boolean isAvailableMDN;
    public boolean isAvailableMSISDN;
    boolean isAvailableSMS;
    boolean isAvailableSPN;
    boolean mCsimSpnDisplayCondition;
    private String mCtcMLPL;
    private String mCtcMSPL;
    private String mCtcMprl;
    private byte[] mEFli;
    private byte[] mEFpl;
    private String mHomeNetworkId;
    private String mHomeSystemId;
    boolean mImsiRequest;
    private String mMin;
    private String mMin2Min1;
    private String mMyMobileNumber;
    private boolean mOtaCommited;
    private String mPrlVersion;
    private final BroadcastReceiver mReceiver;
    private String[] mSponImsi;

    private class EfCsimCdmaHomeLoaded implements IccRecordLoaded {
        private EfCsimCdmaHomeLoaded() {
        }

        public String getEfName() {
            return "EF_CSIM_CDMAHOME";
        }

        public void onRecordLoaded(AsyncResult ar) {
            ArrayList<byte[]> dataList = ar.result;
            RuimRecords.this.log("CSIM_CDMAHOME data size=" + dataList.size());
            if (!dataList.isEmpty()) {
                StringBuilder sidBuf = new StringBuilder();
                StringBuilder nidBuf = new StringBuilder();
                Iterator i$ = dataList.iterator();
                while (i$.hasNext()) {
                    byte[] data = (byte[]) i$.next();
                    if (data.length == 5) {
                        int nid = ((data[3] & 255) << 8) | (data[2] & 255);
                        sidBuf.append(((data[1] & 255) << 8) | (data[0] & 255)).append(',');
                        nidBuf.append(nid).append(',');
                    }
                }
                sidBuf.setLength(sidBuf.length() - 1);
                nidBuf.setLength(nidBuf.length() - 1);
                RuimRecords.this.mHomeSystemId = sidBuf.toString();
                RuimRecords.this.mHomeNetworkId = nidBuf.toString();
            }
        }
    }

    private class EfCsimEprlLoaded implements IccRecordLoaded {
        private EfCsimEprlLoaded() {
        }

        public String getEfName() {
            return "EF_CSIM_EPRL";
        }

        public void onRecordLoaded(AsyncResult ar) {
            RuimRecords.this.onGetCSimEprlDone(ar);
        }
    }

    private class EfCsimImsimLoaded implements IccRecordLoaded {
        private EfCsimImsimLoaded() {
        }

        public String getEfName() {
            return "EF_CSIM_IMSIM";
        }

        public void onRecordLoaded(AsyncResult ar) {
            boolean provisioned;
            byte[] data = (byte[]) ar.result;
            RuimRecords.this.log("CSIM_IMSIM=" + IccUtils.bytesToHexString(data));
            if ((data[7] & 128) == 128) {
                provisioned = true;
            } else {
                provisioned = false;
            }
            if (provisioned) {
                int first3digits = ((data[2] & 3) << 8) + (data[1] & 255);
                int second3digits = (((data[5] & 255) << 8) | (data[4] & 255)) >> 6;
                int digit7 = (data[4] >> 2) & 15;
                if (digit7 > 9) {
                    digit7 = 0;
                }
                int last3digits = ((data[4] & 3) << 8) | (data[3] & 255);
                first3digits = RuimRecords.this.adjstMinDigits(first3digits);
                second3digits = RuimRecords.this.adjstMinDigits(second3digits);
                last3digits = RuimRecords.this.adjstMinDigits(last3digits);
                StringBuilder builder = new StringBuilder();
                builder.append(String.format(Locale.US, "%03d", new Object[]{Integer.valueOf(first3digits)}));
                builder.append(String.format(Locale.US, "%03d", new Object[]{Integer.valueOf(second3digits)}));
                builder.append(String.format(Locale.US, "%d", new Object[]{Integer.valueOf(digit7)}));
                builder.append(String.format(Locale.US, "%03d", new Object[]{Integer.valueOf(last3digits)}));
                RuimRecords.this.mMin = builder.toString();
                RuimRecords.this.log("min present=" + RuimRecords.this.mMin);
                return;
            }
            RuimRecords.this.log("min not present");
        }
    }

    private class EfCsimLiLoaded implements IccRecordLoaded {
        private EfCsimLiLoaded() {
        }

        public String getEfName() {
            return "EF_CSIM_LI";
        }

        public void onRecordLoaded(AsyncResult ar) {
            RuimRecords.this.mEFli = (byte[]) ar.result;
            for (int i = 0; i < RuimRecords.this.mEFli.length; i += 2) {
                switch (RuimRecords.this.mEFli[i + 1]) {
                    case (byte) 1:
                        RuimRecords.this.mEFli[i] = (byte) 101;
                        RuimRecords.this.mEFli[i + 1] = (byte) 110;
                        break;
                    case (byte) 2:
                        RuimRecords.this.mEFli[i] = (byte) 102;
                        RuimRecords.this.mEFli[i + 1] = (byte) 114;
                        break;
                    case (byte) 3:
                        RuimRecords.this.mEFli[i] = (byte) 101;
                        RuimRecords.this.mEFli[i + 1] = (byte) 115;
                        break;
                    case (byte) 4:
                        RuimRecords.this.mEFli[i] = (byte) 106;
                        RuimRecords.this.mEFli[i + 1] = (byte) 97;
                        break;
                    case (byte) 5:
                        RuimRecords.this.mEFli[i] = (byte) 107;
                        RuimRecords.this.mEFli[i + 1] = (byte) 111;
                        break;
                    case (byte) 6:
                        RuimRecords.this.mEFli[i] = (byte) 122;
                        RuimRecords.this.mEFli[i + 1] = (byte) 104;
                        break;
                    case (byte) 7:
                        RuimRecords.this.mEFli[i] = (byte) 104;
                        RuimRecords.this.mEFli[i + 1] = (byte) 101;
                        break;
                    default:
                        RuimRecords.this.mEFli[i] = (byte) 32;
                        RuimRecords.this.mEFli[i + 1] = (byte) 32;
                        break;
                }
            }
            RuimRecords.this.log("EF_LI=" + IccUtils.bytesToHexString(RuimRecords.this.mEFli));
        }
    }

    private class EfCsimMdnLoaded implements IccRecordLoaded {
        private EfCsimMdnLoaded() {
        }

        public String getEfName() {
            return "EF_CSIM_MDN";
        }

        public void onRecordLoaded(AsyncResult ar) {
            byte[] data = (byte[]) ar.result;
            RuimRecords.this.log("CSIM_MDN=" + IccUtils.bytesToHexString(data));
            int mdnDigitsNum = data[0] & 15;
            RuimRecords.this.mMdn = IccUtils.cdmaBcdToString(data, 1, mdnDigitsNum);
            RuimRecords.this.log("CSIM MDN=" + RuimRecords.this.mMdn);
        }
    }

    private class EfCsimSpnLoaded implements IccRecordLoaded {
        private EfCsimSpnLoaded() {
        }

        public String getEfName() {
            return "EF_CSIM_SPN";
        }

        /* JADX WARNING: inconsistent code. */
        /* Code decompiled incorrectly, please refer to instructions dump. */
        public void onRecordLoaded(android.os.AsyncResult r14) {
            /*
            r13 = this;
            r9 = 1;
            r4 = 32;
            r10 = 0;
            r8 = r14.result;
            r8 = (byte[]) r8;
            r0 = r8;
            r0 = (byte[]) r0;
            r8 = com.android.internal.telephony.uicc.RuimRecords.this;
            r11 = new java.lang.StringBuilder;
            r11.<init>();
            r12 = "CSIM_SPN=";
            r11 = r11.append(r12);
            r12 = com.android.internal.telephony.uicc.IccUtils.bytesToHexString(r0);
            r11 = r11.append(r12);
            r11 = r11.toString();
            r8.log(r11);
            r11 = com.android.internal.telephony.uicc.RuimRecords.this;
            r8 = r0[r10];
            r8 = r8 & 1;
            if (r8 == 0) goto L_0x005b;
        L_0x002f:
            r8 = r9;
        L_0x0030:
            r11.mCsimSpnDisplayCondition = r8;
            r2 = r0[r9];
            r8 = 2;
            r3 = r0[r8];
            r7 = new byte[r4];
            r8 = r0.length;
            r8 = r8 + -3;
            if (r8 >= r4) goto L_0x0041;
        L_0x003e:
            r8 = r0.length;
            r4 = r8 + -3;
        L_0x0041:
            r8 = 3;
            java.lang.System.arraycopy(r0, r8, r7, r10, r4);
            r5 = 0;
        L_0x0046:
            r8 = r7.length;
            if (r5 >= r8) goto L_0x0051;
        L_0x0049:
            r8 = r7[r5];
            r8 = r8 & 255;
            r9 = 255; // 0xff float:3.57E-43 double:1.26E-321;
            if (r8 != r9) goto L_0x005d;
        L_0x0051:
            if (r5 != 0) goto L_0x0060;
        L_0x0053:
            r8 = com.android.internal.telephony.uicc.RuimRecords.this;
            r9 = "";
            r8.setServiceProviderName(r9);
        L_0x005a:
            return;
        L_0x005b:
            r8 = r10;
            goto L_0x0030;
        L_0x005d:
            r5 = r5 + 1;
            goto L_0x0046;
        L_0x0060:
            switch(r2) {
                case 0: goto L_0x00b2;
                case 1: goto L_0x0063;
                case 2: goto L_0x00e9;
                case 3: goto L_0x00da;
                case 4: goto L_0x012d;
                case 5: goto L_0x0063;
                case 6: goto L_0x0063;
                case 7: goto L_0x0063;
                case 8: goto L_0x00b2;
                case 9: goto L_0x00da;
                default: goto L_0x0063;
            };
        L_0x0063:
            r8 = com.android.internal.telephony.uicc.RuimRecords.this;	 Catch:{ Exception -> 0x00c0 }
            r9 = "SPN encoding not supported";
            r8.log(r9);	 Catch:{ Exception -> 0x00c0 }
        L_0x006a:
            r8 = com.android.internal.telephony.uicc.RuimRecords.this;
            r9 = new java.lang.StringBuilder;
            r9.<init>();
            r10 = "spn=";
            r9 = r9.append(r10);
            r10 = com.android.internal.telephony.uicc.RuimRecords.this;
            r10 = r10.getServiceProviderName();
            r9 = r9.append(r10);
            r9 = r9.toString();
            r8.log(r9);
            r8 = com.android.internal.telephony.uicc.RuimRecords.this;
            r9 = new java.lang.StringBuilder;
            r9.<init>();
            r10 = "spnCondition=";
            r9 = r9.append(r10);
            r10 = com.android.internal.telephony.uicc.RuimRecords.this;
            r10 = r10.mCsimSpnDisplayCondition;
            r9 = r9.append(r10);
            r9 = r9.toString();
            r8.log(r9);
            r8 = com.android.internal.telephony.uicc.RuimRecords.this;
            r9 = "gsm.sim.operator.alpha";
            r10 = com.android.internal.telephony.uicc.RuimRecords.this;
            r10 = r10.getServiceProviderName();
            r8.setSystemProperty(r9, r10);
            goto L_0x005a;
        L_0x00b2:
            r8 = com.android.internal.telephony.uicc.RuimRecords.this;	 Catch:{ Exception -> 0x00c0 }
            r9 = new java.lang.String;	 Catch:{ Exception -> 0x00c0 }
            r10 = 0;
            r11 = "ISO-8859-1";
            r9.<init>(r7, r10, r5, r11);	 Catch:{ Exception -> 0x00c0 }
            r8.setServiceProviderName(r9);	 Catch:{ Exception -> 0x00c0 }
            goto L_0x006a;
        L_0x00c0:
            r1 = move-exception;
            r8 = com.android.internal.telephony.uicc.RuimRecords.this;
            r9 = new java.lang.StringBuilder;
            r9.<init>();
            r10 = "spn decode error: ";
            r9 = r9.append(r10);
            r9 = r9.append(r1);
            r9 = r9.toString();
            r8.log(r9);
            goto L_0x006a;
        L_0x00da:
            r8 = com.android.internal.telephony.uicc.RuimRecords.this;	 Catch:{ Exception -> 0x00c0 }
            r9 = 0;
            r10 = r5 * 8;
            r10 = r10 / 7;
            r9 = com.android.internal.telephony.GsmAlphabet.gsm7BitPackedToString(r7, r9, r10);	 Catch:{ Exception -> 0x00c0 }
            r8.setServiceProviderName(r9);	 Catch:{ Exception -> 0x00c0 }
            goto L_0x006a;
        L_0x00e9:
            r6 = new java.lang.String;	 Catch:{ Exception -> 0x00c0 }
            r8 = 0;
            r9 = "US-ASCII";
            r6.<init>(r7, r8, r5, r9);	 Catch:{ Exception -> 0x00c0 }
            r8 = android.text.TextUtils.isPrintableAsciiOnly(r6);	 Catch:{ Exception -> 0x00c0 }
            if (r8 == 0) goto L_0x00fe;
        L_0x00f7:
            r8 = com.android.internal.telephony.uicc.RuimRecords.this;	 Catch:{ Exception -> 0x00c0 }
            r8.setServiceProviderName(r6);	 Catch:{ Exception -> 0x00c0 }
            goto L_0x006a;
        L_0x00fe:
            r8 = com.android.internal.telephony.uicc.RuimRecords.this;	 Catch:{ Exception -> 0x00c0 }
            r9 = new java.lang.StringBuilder;	 Catch:{ Exception -> 0x00c0 }
            r9.<init>();	 Catch:{ Exception -> 0x00c0 }
            r10 = "Some corruption in SPN decoding = ";
            r9 = r9.append(r10);	 Catch:{ Exception -> 0x00c0 }
            r9 = r9.append(r6);	 Catch:{ Exception -> 0x00c0 }
            r9 = r9.toString();	 Catch:{ Exception -> 0x00c0 }
            r8.log(r9);	 Catch:{ Exception -> 0x00c0 }
            r8 = com.android.internal.telephony.uicc.RuimRecords.this;	 Catch:{ Exception -> 0x00c0 }
            r9 = "Using ENCODING_GSM_7BIT_ALPHABET scheme...";
            r8.log(r9);	 Catch:{ Exception -> 0x00c0 }
            r8 = com.android.internal.telephony.uicc.RuimRecords.this;	 Catch:{ Exception -> 0x00c0 }
            r9 = 0;
            r10 = r5 * 8;
            r10 = r10 / 7;
            r9 = com.android.internal.telephony.GsmAlphabet.gsm7BitPackedToString(r7, r9, r10);	 Catch:{ Exception -> 0x00c0 }
            r8.setServiceProviderName(r9);	 Catch:{ Exception -> 0x00c0 }
            goto L_0x006a;
        L_0x012d:
            r8 = com.android.internal.telephony.uicc.RuimRecords.this;	 Catch:{ Exception -> 0x00c0 }
            r9 = new java.lang.String;	 Catch:{ Exception -> 0x00c0 }
            r10 = 0;
            r11 = "utf-16";
            r9.<init>(r7, r10, r5, r11);	 Catch:{ Exception -> 0x00c0 }
            r8.setServiceProviderName(r9);	 Catch:{ Exception -> 0x00c0 }
            goto L_0x006a;
            */
            throw new UnsupportedOperationException("Method not decompiled: com.android.internal.telephony.uicc.RuimRecords.EfCsimSpnLoaded.onRecordLoaded(android.os.AsyncResult):void");
        }
    }

    private class EfPlLoaded implements IccRecordLoaded {
        private EfPlLoaded() {
        }

        public String getEfName() {
            return "EF_PL";
        }

        public void onRecordLoaded(AsyncResult ar) {
            RuimRecords.this.mEFpl = (byte[]) ar.result;
            RuimRecords.this.log("EF_PL=" + IccUtils.bytesToHexString(RuimRecords.this.mEFpl));
        }
    }

    private class RUIMRecordsBroadcastReceiver extends BroadcastReceiver {
        private RUIMRecordsBroadcastReceiver() {
        }

        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if ("com.samsung.intent.action.Slot1setCardDataInit".equals(action)) {
                RuimRecords.this.log("com.samsung.intent.action.Slot1setCardDataInit");
                SystemProperties.set("gsm.sim.selectnetwork", "CDMA");
                RuimRecords.this.setCardDataInit();
            } else if ("com.samsung.intent.action.Slot1SwitchCompleted".equals(action)) {
                if ("DCGS".equals("DGG")) {
                    RuimRecords.this.log("com.samsung.intent.action.Slot1SwitchCompleted");
                    RuimRecords.this.setCardDataInit();
                }
            } else if ("com.samsung.intent.action.Slot1OnCompleted".equals(action) && "DCGS".equals("DGG")) {
                RuimRecords.this.log("com.samsung.intent.action.Slot1OnCompleted");
                RuimRecords.this.setCardDataInit();
            }
        }
    }

    public void handleMessage(android.os.Message r31) {
        /* JADX: method processing error */
/*
Error: java.util.NoSuchElementException
	at java.util.HashMap$HashIterator.nextNode(HashMap.java:1431)
	at java.util.HashMap$KeyIterator.next(HashMap.java:1453)
	at jadx.core.dex.visitors.blocksmaker.BlockFinallyExtract.applyRemove(BlockFinallyExtract.java:535)
	at jadx.core.dex.visitors.blocksmaker.BlockFinallyExtract.extractFinally(BlockFinallyExtract.java:175)
	at jadx.core.dex.visitors.blocksmaker.BlockFinallyExtract.processExceptionHandler(BlockFinallyExtract.java:79)
	at jadx.core.dex.visitors.blocksmaker.BlockFinallyExtract.visit(BlockFinallyExtract.java:51)
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
        r30 = this;
        r11 = 0;
        r0 = r30;
        r0 = r0.mDestroyed;
        r25 = r0;
        r25 = r25.get();
        if (r25 == 0) goto L_0x0042;
    L_0x000d:
        r25 = new java.lang.StringBuilder;
        r25.<init>();
        r26 = "Received message ";
        r25 = r25.append(r26);
        r0 = r25;
        r1 = r31;
        r25 = r0.append(r1);
        r26 = "[";
        r25 = r25.append(r26);
        r0 = r31;
        r0 = r0.what;
        r26 = r0;
        r25 = r25.append(r26);
        r26 = "] while being destroyed. Ignoring.";
        r25 = r25.append(r26);
        r25 = r25.toString();
        r0 = r30;
        r1 = r25;
        r0.loge(r1);
    L_0x0041:
        return;
    L_0x0042:
        r0 = r31;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = r0.what;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r25 = r0;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        switch(r25) {
            case 1: goto L_0x0054;
            case 3: goto L_0x007b;
            case 4: goto L_0x006a;
            case 5: goto L_0x07dc;
            case 6: goto L_0x0168;
            case 8: goto L_0x0277;
            case 10: goto L_0x0744;
            case 14: goto L_0x088b;
            case 16: goto L_0x04a6;
            case 17: goto L_0x08c6;
            case 18: goto L_0x08a4;
            case 19: goto L_0x08a4;
            case 21: goto L_0x08a4;
            case 22: goto L_0x08a4;
            case 31: goto L_0x0918;
            case 32: goto L_0x0934;
            case 33: goto L_0x098f;
            case 34: goto L_0x0a5a;
            case 35: goto L_0x0b25;
            case 36: goto L_0x0bf0;
            case 37: goto L_0x0c3c;
            case 38: goto L_0x0d64;
            case 41: goto L_0x03d8;
            case 42: goto L_0x03f8;
            case 49: goto L_0x0558;
            case 50: goto L_0x0604;
            case 51: goto L_0x068e;
            case 52: goto L_0x0718;
            case 53: goto L_0x0e0c;
            case 54: goto L_0x0e28;
            case 800: goto L_0x0252;
            default: goto L_0x004b;
        };	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
    L_0x004b:
        super.handleMessage(r31);	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
    L_0x004e:
        if (r11 == 0) goto L_0x0041;
    L_0x0050:
        r30.onRecordLoaded();
        goto L_0x0041;
    L_0x0054:
        r30.onReady();	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        goto L_0x004e;
    L_0x0058:
        r9 = move-exception;
        r25 = "RuimRecords";	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r26 = "Exception parsing RUIM record";	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = r25;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r1 = r26;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        android.telephony.Rlog.w(r0, r1, r9);	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        if (r11 == 0) goto L_0x0041;
    L_0x0066:
        r30.onRecordLoaded();
        goto L_0x0041;
    L_0x006a:
        r25 = "Event EVENT_GET_DEVICE_IDENTITY_DONE Received";	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = r30;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r1 = r25;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0.log(r1);	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        goto L_0x004e;
    L_0x0074:
        r25 = move-exception;
        if (r11 == 0) goto L_0x007a;
    L_0x0077:
        r30.onRecordLoaded();
    L_0x007a:
        throw r25;
    L_0x007b:
        r11 = 1;
        r0 = r31;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r5 = r0.obj;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r5 = (android.os.AsyncResult) r5;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = r5.exception;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r25 = r0;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        if (r25 == 0) goto L_0x00a7;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
    L_0x0088:
        r25 = new java.lang.StringBuilder;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r25.<init>();	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r26 = "Exception querying IMSI, Exception:";	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r25 = r25.append(r26);	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = r5.exception;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r26 = r0;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r25 = r25.append(r26);	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r25 = r25.toString();	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = r30;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r1 = r25;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0.loge(r1);	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        goto L_0x004e;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
    L_0x00a7:
        r25 = "DCG";	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r26 = "DGG";	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r25 = r25.equals(r26);	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        if (r25 != 0) goto L_0x00d9;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
    L_0x00b1:
        r25 = "DCGG";	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r26 = "DGG";	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r25 = r25.equals(r26);	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        if (r25 != 0) goto L_0x00d9;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
    L_0x00bb:
        r25 = "DCGS";	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r26 = "DGG";	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r25 = r25.equals(r26);	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        if (r25 != 0) goto L_0x00d9;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
    L_0x00c5:
        r25 = "DCGGS";	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r26 = "DGG";	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r25 = r25.equals(r26);	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        if (r25 != 0) goto L_0x00d9;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
    L_0x00cf:
        r25 = "CG";	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r26 = "DGG";	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r25 = r25.equals(r26);	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        if (r25 == 0) goto L_0x00e4;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
    L_0x00d9:
        r25 = "do not use GSM IMSI in cdma.";	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = r30;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r1 = r25;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0.log(r1);	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        goto L_0x004e;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
    L_0x00e4:
        r0 = r5.result;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r25 = r0;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r25 = (java.lang.String) r25;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = r25;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r1 = r30;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r1.mImsi = r0;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = r30;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = r0.mImsi;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r25 = r0;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        if (r25 == 0) goto L_0x0144;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
    L_0x00f8:
        r0 = r30;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = r0.mImsi;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r25 = r0;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r25 = r25.length();	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r26 = 6;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = r25;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r1 = r26;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        if (r0 < r1) goto L_0x011c;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
    L_0x010a:
        r0 = r30;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = r0.mImsi;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r25 = r0;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r25 = r25.length();	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r26 = 15;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = r25;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r1 = r26;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        if (r0 <= r1) goto L_0x0144;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
    L_0x011c:
        r25 = new java.lang.StringBuilder;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r25.<init>();	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r26 = "invalid IMSI ";	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r25 = r25.append(r26);	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = r30;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = r0.mImsi;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r26 = r0;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r25 = r25.append(r26);	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r25 = r25.toString();	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = r30;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r1 = r25;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0.loge(r1);	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r25 = 0;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = r25;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r1 = r30;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r1.mImsi = r0;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
    L_0x0144:
        r17 = r30.getRUIMOperatorNumeric();	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r25 = new java.lang.StringBuilder;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r25.<init>();	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r26 = "NO update mccmnc=";	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r25 = r25.append(r26);	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = r25;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r1 = r17;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r25 = r0.append(r1);	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r25 = r25.toString();	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = r30;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r1 = r25;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0.log(r1);	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        goto L_0x004e;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
    L_0x0168:
        r25 = "Event EVENT_GET_MDN_DONE Received";	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = r30;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r1 = r25;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0.log(r1);	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r11 = 1;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = r31;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r5 = r0.obj;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r5 = (android.os.AsyncResult) r5;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = r5.result;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r25 = r0;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r25 = (byte[]) r25;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = r25;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = (byte[]) r0;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r6 = r0;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r25 = "";	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = r25;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r1 = r30;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r1.mMyMobileNumber = r0;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = r5.exception;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r25 = r0;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        if (r25 == 0) goto L_0x01a4;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
    L_0x0191:
        r25 = "Invalid or missing EF[RUIM_MDN]";	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = r30;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r1 = r25;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0.log(r1);	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r25 = 1;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = r25;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r1 = r30;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r1.isAvailableMDN = r0;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        goto L_0x004e;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
    L_0x01a4:
        r25 = 0;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r25 = r6[r25];	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r13 = r25 & 15;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r25 = new java.lang.StringBuilder;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r25.<init>();	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r26 = "mdn_length: ";	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r25 = r25.append(r26);	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = r25;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r25 = r0.append(r13);	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r25 = r25.toString();	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = r30;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r1 = r25;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0.log(r1);	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        if (r13 == 0) goto L_0x01d6;
    L_0x01c8:
        r25 = 0;
        r0 = r25;	 Catch:{ IndexOutOfBoundsException -> 0x023d }
        r25 = com.android.internal.telephony.uicc.IccUtils.SetupMDNbcdToString(r6, r0, r13);	 Catch:{ IndexOutOfBoundsException -> 0x023d }
        r0 = r25;	 Catch:{ IndexOutOfBoundsException -> 0x023d }
        r1 = r30;	 Catch:{ IndexOutOfBoundsException -> 0x023d }
        r1.mMyMobileNumber = r0;	 Catch:{ IndexOutOfBoundsException -> 0x023d }
    L_0x01d6:
        r25 = 1;
        r0 = r25;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r1 = r30;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r1.isAvailableMDN = r0;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r25 = SHIP_BUILD;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        if (r25 != 0) goto L_0x0202;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
    L_0x01e2:
        r25 = new java.lang.StringBuilder;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r25.<init>();	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r26 = "MDN: ";	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r25 = r25.append(r26);	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = r30;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = r0.mMyMobileNumber;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r26 = r0;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r25 = r25.append(r26);	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r25 = r25.toString();	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = r30;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r1 = r25;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0.log(r1);	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
    L_0x0202:
        r0 = r30;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = r0.mMyMobileNumber;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r25 = r0;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = r25;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r1 = r30;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r1.cMDN = r0;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r25 = SHIP_BUILD;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        if (r25 != 0) goto L_0x0232;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
    L_0x0212:
        r25 = new java.lang.StringBuilder;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r25.<init>();	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r26 = "cMDN: ";	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r25 = r25.append(r26);	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = r30;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = r0.cMDN;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r26 = r0;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r25 = r25.append(r26);	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r25 = r25.toString();	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = r30;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r1 = r25;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0.log(r1);	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
    L_0x0232:
        r0 = r30;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = r0.mMdnReadyRegistrants;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r25 = r0;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r25.notifyRegistrants();	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        goto L_0x004e;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
    L_0x023d:
        r7 = move-exception;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r25 = "RuimRecords";	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r26 = "MDN: Exception ";	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = r25;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r1 = r26;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        android.telephony.Rlog.w(r0, r1, r7);	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r25 = 1;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = r25;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r1 = r30;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r1.isAvailableMDN = r0;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        goto L_0x01d6;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
    L_0x0252:
        r0 = r30;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = r0.mFh;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r25 = r0;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r26 = 28450; // 0x6f22 float:3.9867E-41 double:1.4056E-319;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r27 = 8;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = r30;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r1 = r27;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r27 = r0.obtainMessage(r1);	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r25.loadEFTransparent(r26, r27);	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = r30;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = r0.mRecordsToLoad;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r25 = r0;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r25 = r25 + 1;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = r25;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r1 = r30;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r1.mRecordsToLoad = r0;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        goto L_0x004e;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
    L_0x0277:
        r25 = "Event EVENT_GET_IMSI_M_DONE Received";	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = r30;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r1 = r25;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0.log(r1);	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r11 = 1;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = r31;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r5 = r0.obj;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r5 = (android.os.AsyncResult) r5;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = r5.result;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r25 = r0;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r25 = (byte[]) r25;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = r25;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = (byte[]) r0;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r6 = r0;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = r5.exception;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r25 = r0;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        if (r25 == 0) goto L_0x02d2;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
    L_0x0298:
        r25 = "Invalid or missing EF[IMSI_M]";	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = r30;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r1 = r25;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0.log(r1);	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r25 = "CG";	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r26 = "DGG";	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r25 = r25.equals(r26);	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        if (r25 == 0) goto L_0x004e;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
    L_0x02ab:
        r0 = r30;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = r0.mImsiRequest;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r25 = r0;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        if (r25 == 0) goto L_0x004e;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
    L_0x02b3:
        r25 = 800; // 0x320 float:1.121E-42 double:3.953E-321;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = r30;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r1 = r25;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r25 = r0.obtainMessage(r1);	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r26 = 500; // 0x1f4 float:7.0E-43 double:2.47E-321;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = r30;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r1 = r25;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r2 = r26;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0.sendMessageDelayed(r1, r2);	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r25 = 0;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = r25;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r1 = r30;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r1.mImsiRequest = r0;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        goto L_0x004e;
    L_0x02d2:
        r25 = com.android.internal.telephony.uicc.IccUtils.extractIMSI(r6);	 Catch:{ IndexOutOfBoundsException -> 0x03be }
        r0 = r25;	 Catch:{ IndexOutOfBoundsException -> 0x03be }
        r1 = r30;	 Catch:{ IndexOutOfBoundsException -> 0x03be }
        r1.mImsi = r0;	 Catch:{ IndexOutOfBoundsException -> 0x03be }
    L_0x02dc:
        r16 = r30.getRUIMOperatorNumeric();	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r25 = new java.lang.StringBuilder;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r25.<init>();	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r26 = "RuimRecords: onAllRecordsLoaded set 'gsm.sim.operator.numeric' to operator='";	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r25 = r25.append(r26);	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = r25;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r1 = r16;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r25 = r0.append(r1);	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r26 = "'";	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r25 = r25.append(r26);	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r25 = r25.toString();	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = r30;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r1 = r25;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0.log(r1);	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        if (r16 == 0) goto L_0x038a;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
    L_0x0306:
        r25 = "DCG";	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r26 = "DGG";	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r25 = r25.equals(r26);	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        if (r25 != 0) goto L_0x0338;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
    L_0x0310:
        r25 = "DCGG";	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r26 = "DGG";	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r25 = r25.equals(r26);	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        if (r25 != 0) goto L_0x0338;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
    L_0x031a:
        r25 = "DCGS";	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r26 = "DGG";	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r25 = r25.equals(r26);	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        if (r25 != 0) goto L_0x0338;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
    L_0x0324:
        r25 = "DCGGS";	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r26 = "DGG";	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r25 = r25.equals(r26);	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        if (r25 != 0) goto L_0x0338;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
    L_0x032e:
        r25 = "CG";	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r26 = "DGG";	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r25 = r25.equals(r26);	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        if (r25 == 0) goto L_0x03cc;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
    L_0x0338:
        r25 = "gsm.sim.selectnetwork";	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r22 = android.os.SystemProperties.get(r25);	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r25 = new java.lang.StringBuilder;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r25.<init>();	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r26 = "simselswitch = ";	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r25 = r25.append(r26);	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = r25;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r1 = r22;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r25 = r0.append(r1);	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r25 = r25.toString();	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = r30;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r1 = r25;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0.log(r1);	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r25 = "CDMA";	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = r25;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r1 = r22;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r25 = r0.equals(r1);	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        if (r25 == 0) goto L_0x037f;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
    L_0x0368:
        r0 = r30;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = r0.phone;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r25 = r0;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r25 = r25.getPhoneId();	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        if (r25 != 0) goto L_0x037f;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
    L_0x0374:
        r25 = "gsm.sim.operator.numeric";	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = r30;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r1 = r25;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r2 = r16;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0.setSystemProperty(r1, r2);	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
    L_0x037f:
        r25 = "gsm.sim.cdmaoperator.numeric";	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = r30;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r1 = r25;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r2 = r16;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0.setSystemProperty(r1, r2);	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
    L_0x038a:
        r0 = r30;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = r0.mImsi;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r25 = r0;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        if (r25 == 0) goto L_0x03b3;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
    L_0x0392:
        r25 = "gsm.sim.operator.iso-country";	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = r30;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = r0.mImsi;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r26 = r0;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r27 = 0;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r28 = 3;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r26 = r26.substring(r27, r28);	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r26 = java.lang.Integer.parseInt(r26);	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r26 = com.android.internal.telephony.MccTable.countryCodeForMcc(r26);	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = r30;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r1 = r25;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r2 = r26;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0.setSystemProperty(r1, r2);	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
    L_0x03b3:
        r0 = r30;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = r0.mImsiReadyRegistrants;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r25 = r0;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r25.notifyRegistrants();	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        goto L_0x004e;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
    L_0x03be:
        r7 = move-exception;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r25 = "RuimRecords";	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r26 = "mImsi: Exception ";	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = r25;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r1 = r26;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        android.telephony.Rlog.w(r0, r1, r7);	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        goto L_0x02dc;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
    L_0x03cc:
        r25 = "gsm.sim.operator.numeric";	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = r30;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r1 = r25;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r2 = r16;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0.setSystemProperty(r1, r2);	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        goto L_0x037f;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
    L_0x03d8:
        r25 = "EVENT_SIM_LOCKED";	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = r30;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r1 = r25;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0.log(r1);	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = r30;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = r0.mFh;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r25 = r0;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r26 = 12258; // 0x2fe2 float:1.7177E-41 double:6.0563E-320;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r27 = 42;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = r30;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r1 = r27;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r27 = r0.obtainMessage(r1);	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r25.loadEFTransparent(r26, r27);	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        goto L_0x004e;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
    L_0x03f8:
        r0 = r31;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r5 = r0.obj;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r5 = (android.os.AsyncResult) r5;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = r5.result;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r25 = r0;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r25 = (byte[]) r25;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = r25;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = (byte[]) r0;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r6 = r0;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = r5.exception;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r25 = r0;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        if (r25 != 0) goto L_0x004e;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
    L_0x040f:
        r25 = "ro.csc.countryiso_code";	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r25 = android.os.SystemProperties.get(r25);	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = r25;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r1 = r30;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r1.countryISO = r0;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r25 = "CN";	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = r30;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = r0.countryISO;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r26 = r0;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r25 = r25.equals(r26);	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        if (r25 != 0) goto L_0x0445;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
    L_0x0429:
        r25 = "HK";	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = r30;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = r0.countryISO;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r26 = r0;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r25 = r25.equals(r26);	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        if (r25 != 0) goto L_0x0445;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
    L_0x0437:
        r25 = "TW";	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = r30;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = r0.countryISO;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r26 = r0;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r25 = r25.equals(r26);	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        if (r25 == 0) goto L_0x0487;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
    L_0x0445:
        r25 = 0;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = r6.length;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r26 = r0;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = r25;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r1 = r26;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r25 = com.android.internal.telephony.uicc.IccUtils.ICCIDbcdToString(r6, r0, r1);	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = r25;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r1 = r30;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r1.mIccId = r0;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
    L_0x0458:
        r0 = r30;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = r0.mIccIdReadyRegistrants;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r25 = r0;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r25.notifyRegistrants();	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r25 = SHIP_BUILD;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        if (r25 != 0) goto L_0x049b;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
    L_0x0465:
        r25 = new java.lang.StringBuilder;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r25.<init>();	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r26 = "EVENT_GET_ICCID_WHEN_LOCKED_DONE mIccId: ";	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r25 = r25.append(r26);	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = r30;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = r0.mIccId;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r26 = r0;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r25 = r25.append(r26);	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r25 = r25.toString();	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = r30;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r1 = r25;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0.log(r1);	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        goto L_0x004e;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
    L_0x0487:
        r25 = 0;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = r6.length;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r26 = r0;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = r25;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r1 = r26;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r25 = com.android.internal.telephony.uicc.IccUtils.bcdToString(r6, r0, r1);	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = r25;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r1 = r30;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r1.mIccId = r0;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        goto L_0x0458;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
    L_0x049b:
        r25 = "EVENT_GET_ICCID_WHEN_LOCKED_DONE mIccId: ******";	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = r30;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r1 = r25;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0.log(r1);	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        goto L_0x004e;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
    L_0x04a6:
        r4 = 0;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r11 = 1;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = r31;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r5 = r0.obj;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r5 = (android.os.AsyncResult) r5;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = r5.result;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r25 = r0;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r25 = (byte[]) r25;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = r25;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = (byte[]) r0;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r6 = r0;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = r5.exception;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r25 = r0;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        if (r25 == 0) goto L_0x04cc;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
    L_0x04bf:
        r25 = "RuimRecords";	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r26 = "RuimRecords EVENT_GET_CST_DONE failed";	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = r5.exception;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r27 = r0;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        android.telephony.Rlog.i(r25, r26, r27);	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        goto L_0x004e;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
    L_0x04cc:
        r4 = com.android.internal.telephony.uicc.IccUtils.bytesToHexString(r6);	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r25 = new java.lang.StringBuilder;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r25.<init>();	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r26 = "CST: ";	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r25 = r25.append(r26);	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = r25;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r25 = r0.append(r4);	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r25 = r25.toString();	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = r30;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r1 = r25;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0.log(r1);	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r25 = "DCG";	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r26 = "DGG";	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r25 = r25.equals(r26);	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        if (r25 != 0) goto L_0x051e;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
    L_0x04f6:
        r25 = "DCGG";	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r26 = "DGG";	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r25 = r25.equals(r26);	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        if (r25 != 0) goto L_0x051e;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
    L_0x0500:
        r25 = "DCGS";	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r26 = "DGG";	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r25 = r25.equals(r26);	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        if (r25 != 0) goto L_0x051e;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
    L_0x050a:
        r25 = "DCGGS";	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r26 = "DGG";	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r25 = r25.equals(r26);	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        if (r25 != 0) goto L_0x051e;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
    L_0x0514:
        r25 = "CG";	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r26 = "DGG";	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r25 = r25.equals(r26);	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        if (r25 == 0) goto L_0x004e;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
    L_0x051e:
        r0 = r30;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0.checkCHV1available(r6);	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = r30;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0.checkADNavailable(r6);	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = r30;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0.checkFDNavailable(r6);	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = r30;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0.checkSMSavailable(r6);	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = r30;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = r0.isAvailableFDN;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r25 = r0;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        if (r25 == 0) goto L_0x0549;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
    L_0x053a:
        r25 = "gsm.sim.fdn_support";	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r26 = "1";	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = r30;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r1 = r25;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r2 = r26;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0.setSystemProperty(r1, r2);	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        goto L_0x004e;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
    L_0x0549:
        r25 = "gsm.sim.fdn_support";	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r26 = "0";	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = r30;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r1 = r25;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r2 = r26;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0.setSystemProperty(r1, r2);	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        goto L_0x004e;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
    L_0x0558:
        r25 = "EVENT_GET_EPRL_DONE: ";	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = r30;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r1 = r25;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0.log(r1);	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r11 = 1;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = r31;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r5 = r0.obj;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r5 = (android.os.AsyncResult) r5;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = r5.result;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r25 = r0;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r25 = (byte[]) r25;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = r25;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = (byte[]) r0;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r6 = r0;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = r5.exception;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r25 = r0;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        if (r25 == 0) goto L_0x0584;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
    L_0x0579:
        r25 = "fail EVENT_GET_EPRL_DONE: ";	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = r30;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r1 = r25;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0.log(r1);	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        goto L_0x004e;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
    L_0x0584:
        r25 = SHIP_BUILD;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        if (r25 != 0) goto L_0x05a6;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
    L_0x0588:
        r25 = new java.lang.StringBuilder;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r25.<init>();	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r26 = "CSIM_EPRL=";	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r25 = r25.append(r26);	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r26 = com.android.internal.telephony.uicc.IccUtils.bytesToHexString(r6);	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r25 = r25.append(r26);	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r25 = r25.toString();	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = r30;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r1 = r25;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0.log(r1);	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
    L_0x05a6:
        r0 = r6.length;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r25 = r0;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r26 = 3;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = r25;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r1 = r26;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        if (r0 <= r1) goto L_0x05de;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
    L_0x05b1:
        r25 = 2;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r25 = r6[r25];	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = r25;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = r0 & 255;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r25 = r0;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r25 = r25 << 8;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r26 = 3;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r26 = r6[r26];	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = r26;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = r0 & 255;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r26 = r0;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r18 = r25 | r26;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r25 = java.lang.Integer.toString(r18);	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = r25;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r1 = r30;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r1.mCtcMprl = r0;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r25 = "ril.CTCMPRL";	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = r30;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = r0.mCtcMprl;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r26 = r0;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        android.os.SystemProperties.set(r25, r26);	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
    L_0x05de:
        r25 = SHIP_BUILD;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        if (r25 != 0) goto L_0x004e;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
    L_0x05e2:
        r25 = new java.lang.StringBuilder;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r25.<init>();	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r26 = "CTCTEST1=";	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r25 = r25.append(r26);	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = r30;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = r0.mCtcMprl;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r26 = r0;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r25 = r25.append(r26);	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r25 = r25.toString();	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = r30;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r1 = r25;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0.log(r1);	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        goto L_0x004e;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
    L_0x0604:
        r25 = "EVENT_GET_MLPL_DONE: ";	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = r30;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r1 = r25;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0.log(r1);	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r11 = 1;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = r31;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r5 = r0.obj;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r5 = (android.os.AsyncResult) r5;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = r5.result;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r25 = r0;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r25 = (byte[]) r25;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = r25;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = (byte[]) r0;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r6 = r0;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = r5.exception;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r25 = r0;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        if (r25 == 0) goto L_0x0630;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
    L_0x0625:
        r25 = "fail EVENT_GET_MLPL_DONE: ";	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = r30;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r1 = r25;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0.log(r1);	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        goto L_0x004e;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
    L_0x0630:
        r0 = r6.length;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r25 = r0;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r26 = 4;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = r25;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r1 = r26;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        if (r0 <= r1) goto L_0x0668;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
    L_0x063b:
        r25 = 3;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r25 = r6[r25];	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = r25;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = r0 & 255;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r25 = r0;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r25 = r25 << 8;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r26 = 4;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r26 = r6[r26];	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = r26;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = r0 & 255;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r26 = r0;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r14 = r25 | r26;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r25 = java.lang.Integer.toString(r14);	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = r25;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r1 = r30;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r1.mCtcMLPL = r0;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r25 = "ril.CTCMLPL";	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = r30;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = r0.mCtcMLPL;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r26 = r0;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        android.os.SystemProperties.set(r25, r26);	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
    L_0x0668:
        r25 = SHIP_BUILD;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        if (r25 != 0) goto L_0x004e;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
    L_0x066c:
        r25 = new java.lang.StringBuilder;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r25.<init>();	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r26 = "CTCTEST2=";	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r25 = r25.append(r26);	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = r30;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = r0.mCtcMLPL;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r26 = r0;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r25 = r25.append(r26);	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r25 = r25.toString();	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = r30;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r1 = r25;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0.log(r1);	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        goto L_0x004e;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
    L_0x068e:
        r25 = "EVENT_GET_MSPL_DONE: ";	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = r30;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r1 = r25;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0.log(r1);	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r11 = 1;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = r31;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r5 = r0.obj;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r5 = (android.os.AsyncResult) r5;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = r5.result;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r25 = r0;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r25 = (byte[]) r25;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = r25;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = (byte[]) r0;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r6 = r0;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = r5.exception;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r25 = r0;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        if (r25 == 0) goto L_0x06ba;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
    L_0x06af:
        r25 = "fail EVENT_GET_MSPL_DONE: ";	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = r30;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r1 = r25;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0.log(r1);	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        goto L_0x004e;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
    L_0x06ba:
        r0 = r6.length;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r25 = r0;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r26 = 4;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = r25;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r1 = r26;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        if (r0 <= r1) goto L_0x06f2;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
    L_0x06c5:
        r25 = 3;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r25 = r6[r25];	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = r25;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = r0 & 255;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r25 = r0;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r25 = r25 << 8;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r26 = 4;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r26 = r6[r26];	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = r26;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = r0 & 255;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r26 = r0;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r15 = r25 | r26;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r25 = java.lang.Integer.toString(r15);	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = r25;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r1 = r30;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r1.mCtcMSPL = r0;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r25 = "ril.CTCMSPL";	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = r30;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = r0.mCtcMSPL;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r26 = r0;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        android.os.SystemProperties.set(r25, r26);	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
    L_0x06f2:
        r25 = SHIP_BUILD;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        if (r25 != 0) goto L_0x004e;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
    L_0x06f6:
        r25 = new java.lang.StringBuilder;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r25.<init>();	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r26 = "CTCTEST3=";	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r25 = r25.append(r26);	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = r30;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = r0.mCtcMSPL;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r26 = r0;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r25 = r25.append(r26);	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r25 = r25.toString();	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = r30;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r1 = r25;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0.log(r1);	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        goto L_0x004e;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
    L_0x0718:
        r25 = "EVENT_GET_MMSSMODE_DONE: ";	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = r30;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r1 = r25;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0.log(r1);	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r11 = 1;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = r31;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r5 = r0.obj;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r5 = (android.os.AsyncResult) r5;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = r5.result;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r25 = r0;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r25 = (byte[]) r25;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = r25;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = (byte[]) r0;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r6 = r0;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = r5.exception;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r25 = r0;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        if (r25 == 0) goto L_0x004e;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
    L_0x0739:
        r25 = "fail EVENT_GET_MMSSMODE_DONE: ";	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = r30;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r1 = r25;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0.log(r1);	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        goto L_0x004e;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
    L_0x0744:
        r0 = r31;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r5 = r0.obj;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r5 = (android.os.AsyncResult) r5;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = r5.result;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r25 = r0;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r25 = (java.lang.String[]) r25;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = r25;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = (java.lang.String[]) r0;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r12 = r0;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = r5.exception;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r25 = r0;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        if (r25 != 0) goto L_0x004e;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
    L_0x075b:
        r25 = 0;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r25 = r12[r25];	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = r25;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r1 = r30;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r1.mMyMobileNumber = r0;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r25 = 3;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r25 = r12[r25];	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = r25;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r1 = r30;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r1.mMin2Min1 = r0;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r25 = 4;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r25 = r12[r25];	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = r25;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r1 = r30;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r1.mPrlVersion = r0;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r25 = SHIP_BUILD;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        if (r25 != 0) goto L_0x07ad;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
    L_0x077d:
        r25 = new java.lang.StringBuilder;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r25.<init>();	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r26 = "MDN: ";	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r25 = r25.append(r26);	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = r30;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = r0.mMyMobileNumber;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r26 = r0;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r25 = r25.append(r26);	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r26 = " MIN: ";	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r25 = r25.append(r26);	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = r30;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = r0.mMin2Min1;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r26 = r0;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r25 = r25.append(r26);	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r25 = r25.toString();	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = r30;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r1 = r25;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0.log(r1);	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
    L_0x07ad:
        r25 = "USC-CDMA";	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r26 = "";	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r25 = r25.equals(r26);	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        if (r25 == 0) goto L_0x004e;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
    L_0x07b7:
        r10 = new android.content.Intent;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r25 = "android.intent.action.MIN_VALUE_CHANGED";	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = r25;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r10.<init>(r0);	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r25 = "mMinValue";	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = r30;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = r0.mMin2Min1;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r26 = r0;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = r25;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r1 = r26;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r10.putExtra(r0, r1);	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = r30;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = r0.mContext;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r25 = r0;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = r25;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0.sendBroadcast(r10);	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        goto L_0x004e;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
    L_0x07dc:
        r11 = 1;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = r31;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r5 = r0.obj;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r5 = (android.os.AsyncResult) r5;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = r5.result;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r25 = r0;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r25 = (byte[]) r25;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = r25;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = (byte[]) r0;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r6 = r0;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = r5.exception;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r25 = r0;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        if (r25 != 0) goto L_0x004e;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
    L_0x07f4:
        r25 = "ro.csc.countryiso_code";	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r25 = android.os.SystemProperties.get(r25);	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = r25;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r1 = r30;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r1.countryISO = r0;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r25 = "CN";	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = r30;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = r0.countryISO;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r26 = r0;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r25 = r25.equals(r26);	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        if (r25 != 0) goto L_0x082a;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
    L_0x080e:
        r25 = "HK";	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = r30;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = r0.countryISO;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r26 = r0;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r25 = r25.equals(r26);	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        if (r25 != 0) goto L_0x082a;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
    L_0x081c:
        r25 = "TW";	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = r30;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = r0.countryISO;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r26 = r0;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r25 = r25.equals(r26);	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        if (r25 == 0) goto L_0x086c;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
    L_0x082a:
        r25 = 0;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = r6.length;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r26 = r0;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = r25;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r1 = r26;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r25 = com.android.internal.telephony.uicc.IccUtils.ICCIDbcdToString(r6, r0, r1);	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = r25;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r1 = r30;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r1.mIccId = r0;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
    L_0x083d:
        r0 = r30;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = r0.mIccIdReadyRegistrants;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r25 = r0;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r25.notifyRegistrants();	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r25 = SHIP_BUILD;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        if (r25 != 0) goto L_0x0880;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
    L_0x084a:
        r25 = new java.lang.StringBuilder;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r25.<init>();	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r26 = "EVENT_GET_ICCID_DONE mIccId: ";	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r25 = r25.append(r26);	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = r30;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = r0.mIccId;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r26 = r0;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r25 = r25.append(r26);	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r25 = r25.toString();	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = r30;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r1 = r25;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0.log(r1);	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        goto L_0x004e;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
    L_0x086c:
        r25 = 0;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = r6.length;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r26 = r0;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = r25;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r1 = r26;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r25 = com.android.internal.telephony.uicc.IccUtils.bcdToString(r6, r0, r1);	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = r25;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r1 = r30;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r1.mIccId = r0;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        goto L_0x083d;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
    L_0x0880:
        r25 = "EVENT_GET_ICCID_DONE mIccId: ******";	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = r30;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r1 = r25;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0.log(r1);	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        goto L_0x004e;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
    L_0x088b:
        r0 = r31;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r5 = r0.obj;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r5 = (android.os.AsyncResult) r5;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = r5.exception;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r25 = r0;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        if (r25 == 0) goto L_0x004e;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
    L_0x0897:
        r25 = "RuimRecords";	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r26 = "RuimRecords update failed";	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = r5.exception;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r27 = r0;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        android.telephony.Rlog.i(r25, r26, r27);	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        goto L_0x004e;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
    L_0x08a4:
        r25 = new java.lang.StringBuilder;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r25.<init>();	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r26 = "Event not supported: ";	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r25 = r25.append(r26);	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = r31;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = r0.what;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r26 = r0;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r25 = r25.append(r26);	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r25 = r25.toString();	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = r30;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r1 = r25;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0.logw(r1);	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        goto L_0x004e;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
    L_0x08c6:
        r11 = 1;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r25 = "Event EVENT_GET_SST_DONE Received";	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = r30;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r1 = r25;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0.log(r1);	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = r31;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r5 = r0.obj;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r5 = (android.os.AsyncResult) r5;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = r5.result;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r25 = r0;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r25 = (byte[]) r25;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = r25;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = (byte[]) r0;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r6 = r0;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = r5.exception;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r25 = r0;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        if (r25 == 0) goto L_0x08f4;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
    L_0x08e7:
        r25 = "RuimRecords";	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r26 = "RuimRecords EVENT_GET_SST_DONE failed";	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = r5.exception;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r27 = r0;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        android.telephony.Rlog.i(r25, r26, r27);	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        goto L_0x004e;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
    L_0x08f4:
        r25 = SHIP_BUILD;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        if (r25 != 0) goto L_0x004e;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
    L_0x08f8:
        r25 = new java.lang.StringBuilder;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r25.<init>();	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r26 = "EF_CST=";	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r25 = r25.append(r26);	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r26 = com.android.internal.telephony.uicc.IccUtils.bytesToHexString(r6);	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r25 = r25.append(r26);	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r25 = r25.toString();	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = r30;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r1 = r25;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0.log(r1);	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        goto L_0x004e;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
    L_0x0918:
        r11 = 0;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = r31;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r5 = r0.obj;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r5 = (android.os.AsyncResult) r5;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = r5.exception;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r25 = r0;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        if (r25 != 0) goto L_0x004e;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
    L_0x0925:
        r0 = r5.result;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r25 = r0;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r25 = (com.android.internal.telephony.uicc.IccRefreshResponse) r25;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = r30;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r1 = r25;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0.handleRuimRefresh(r1);	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        goto L_0x004e;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
    L_0x0934:
        r11 = 1;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = r31;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r5 = r0.obj;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r5 = (android.os.AsyncResult) r5;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = r5.result;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r25 = r0;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r25 = (byte[]) r25;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = r25;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = (byte[]) r0;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r6 = r0;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = r5.exception;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r25 = r0;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        if (r25 == 0) goto L_0x0955;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
    L_0x094c:
        r25 = "RuimRecords";	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r26 = "Invalid or missing EF[masterImsi]";	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        android.telephony.Rlog.d(r25, r26);	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        goto L_0x004e;
    L_0x0955:
        r25 = SHIP_BUILD;	 Catch:{ StringIndexOutOfBoundsException -> 0x0985 }
        if (r25 != 0) goto L_0x004e;	 Catch:{ StringIndexOutOfBoundsException -> 0x0985 }
    L_0x0959:
        r25 = "RuimRecords";	 Catch:{ StringIndexOutOfBoundsException -> 0x0985 }
        r26 = new java.lang.StringBuilder;	 Catch:{ StringIndexOutOfBoundsException -> 0x0985 }
        r26.<init>();	 Catch:{ StringIndexOutOfBoundsException -> 0x0985 }
        r27 = "[masterImsi]: ";	 Catch:{ StringIndexOutOfBoundsException -> 0x0985 }
        r26 = r26.append(r27);	 Catch:{ StringIndexOutOfBoundsException -> 0x0985 }
        r27 = 2;	 Catch:{ StringIndexOutOfBoundsException -> 0x0985 }
        r28 = 8;	 Catch:{ StringIndexOutOfBoundsException -> 0x0985 }
        r0 = r27;	 Catch:{ StringIndexOutOfBoundsException -> 0x0985 }
        r1 = r28;	 Catch:{ StringIndexOutOfBoundsException -> 0x0985 }
        r27 = com.android.internal.telephony.uicc.IccUtils.bcdToString(r6, r0, r1);	 Catch:{ StringIndexOutOfBoundsException -> 0x0985 }
        r28 = 1;	 Catch:{ StringIndexOutOfBoundsException -> 0x0985 }
        r27 = r27.substring(r28);	 Catch:{ StringIndexOutOfBoundsException -> 0x0985 }
        r26 = r26.append(r27);	 Catch:{ StringIndexOutOfBoundsException -> 0x0985 }
        r26 = r26.toString();	 Catch:{ StringIndexOutOfBoundsException -> 0x0985 }
        android.telephony.Rlog.d(r25, r26);	 Catch:{ StringIndexOutOfBoundsException -> 0x0985 }
        goto L_0x004e;
    L_0x0985:
        r7 = move-exception;
        r25 = "RuimRecords";	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r26 = "MASTER_IMSI was not exist in this card";	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        android.telephony.Rlog.d(r25, r26);	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        goto L_0x004e;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
    L_0x098f:
        r11 = 1;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = r31;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r5 = r0.obj;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r5 = (android.os.AsyncResult) r5;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = r5.result;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r25 = r0;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r25 = (byte[]) r25;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = r25;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = (byte[]) r0;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r6 = r0;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = r5.exception;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r25 = r0;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        if (r25 == 0) goto L_0x09b0;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
    L_0x09a7:
        r25 = "RuimRecords";	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r26 = "Invalid or missing EF[sponImsi1]";	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        android.telephony.Rlog.d(r25, r26);	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        goto L_0x004e;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
    L_0x09b0:
        r0 = r30;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = r0.mSponImsi;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r25 = r0;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r26 = 0;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r27 = new java.lang.String;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r27.<init>();	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r25[r26] = r27;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = r30;	 Catch:{ Exception -> 0x0a2f }
        r0 = r0.mSponImsi;	 Catch:{ Exception -> 0x0a2f }
        r25 = r0;	 Catch:{ Exception -> 0x0a2f }
        r26 = 0;	 Catch:{ Exception -> 0x0a2f }
        r27 = new java.lang.StringBuilder;	 Catch:{ Exception -> 0x0a2f }
        r27.<init>();	 Catch:{ Exception -> 0x0a2f }
        r28 = 2;	 Catch:{ Exception -> 0x0a2f }
        r28 = r6[r28];	 Catch:{ Exception -> 0x0a2f }
        r28 = r28 >> 4;	 Catch:{ Exception -> 0x0a2f }
        r28 = r28 & 15;	 Catch:{ Exception -> 0x0a2f }
        r27 = r27.append(r28);	 Catch:{ Exception -> 0x0a2f }
        r28 = 3;	 Catch:{ Exception -> 0x0a2f }
        r29 = 1;	 Catch:{ Exception -> 0x0a2f }
        r29 = r6[r29];	 Catch:{ Exception -> 0x0a2f }
        r29 = r29 + -1;	 Catch:{ Exception -> 0x0a2f }
        r0 = r28;	 Catch:{ Exception -> 0x0a2f }
        r1 = r29;	 Catch:{ Exception -> 0x0a2f }
        r28 = com.android.internal.telephony.uicc.IccUtils.bcdToString(r6, r0, r1);	 Catch:{ Exception -> 0x0a2f }
        r27 = r27.append(r28);	 Catch:{ Exception -> 0x0a2f }
        r28 = ";";	 Catch:{ Exception -> 0x0a2f }
        r27 = r27.append(r28);	 Catch:{ Exception -> 0x0a2f }
        r28 = 555; // 0x22b float:7.78E-43 double:2.74E-321;	 Catch:{ Exception -> 0x0a2f }
        r0 = r6.length;	 Catch:{ Exception -> 0x0a2f }
        r29 = r0;	 Catch:{ Exception -> 0x0a2f }
        r29 = r29 + -1;	 Catch:{ Exception -> 0x0a2f }
        r0 = r28;	 Catch:{ Exception -> 0x0a2f }
        r1 = r29;	 Catch:{ Exception -> 0x0a2f }
        r28 = com.android.internal.telephony.uicc.IccUtils.adnStringFieldToString(r6, r0, r1);	 Catch:{ Exception -> 0x0a2f }
        r27 = r27.append(r28);	 Catch:{ Exception -> 0x0a2f }
        r27 = r27.toString();	 Catch:{ Exception -> 0x0a2f }
        r25[r26] = r27;	 Catch:{ Exception -> 0x0a2f }
    L_0x0a0b:
        r25 = "RuimRecords";	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r26 = new java.lang.StringBuilder;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r26.<init>();	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r27 = "[sponImsi1]: ";	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r26 = r26.append(r27);	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = r30;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = r0.mSponImsi;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r27 = r0;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r28 = 0;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r27 = r27[r28];	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r26 = r26.append(r27);	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r26 = r26.toString();	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        android.telephony.Rlog.d(r25, r26);	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        goto L_0x004e;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
    L_0x0a2f:
        r8 = move-exception;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r25 = "RuimRecords";	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r26 = new java.lang.StringBuilder;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r26.<init>();	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r27 = "[sponImsi1] Ex -";	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r26 = r26.append(r27);	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = r26;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r26 = r0.append(r8);	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r26 = r26.toString();	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        android.telephony.Rlog.d(r25, r26);	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r8.printStackTrace();	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = r30;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = r0.mSponImsi;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r25 = r0;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r26 = 0;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r27 = "";	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r25[r26] = r27;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        goto L_0x0a0b;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
    L_0x0a5a:
        r11 = 1;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = r31;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r5 = r0.obj;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r5 = (android.os.AsyncResult) r5;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = r5.result;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r25 = r0;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r25 = (byte[]) r25;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = r25;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = (byte[]) r0;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r6 = r0;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = r5.exception;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r25 = r0;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        if (r25 == 0) goto L_0x0a7b;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
    L_0x0a72:
        r25 = "RuimRecords";	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r26 = "Invalid or missing EF[sponImsi2]";	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        android.telephony.Rlog.d(r25, r26);	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        goto L_0x004e;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
    L_0x0a7b:
        r0 = r30;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = r0.mSponImsi;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r25 = r0;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r26 = 1;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r27 = new java.lang.String;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r27.<init>();	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r25[r26] = r27;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = r30;	 Catch:{ Exception -> 0x0afa }
        r0 = r0.mSponImsi;	 Catch:{ Exception -> 0x0afa }
        r25 = r0;	 Catch:{ Exception -> 0x0afa }
        r26 = 1;	 Catch:{ Exception -> 0x0afa }
        r27 = new java.lang.StringBuilder;	 Catch:{ Exception -> 0x0afa }
        r27.<init>();	 Catch:{ Exception -> 0x0afa }
        r28 = 2;	 Catch:{ Exception -> 0x0afa }
        r28 = r6[r28];	 Catch:{ Exception -> 0x0afa }
        r28 = r28 >> 4;	 Catch:{ Exception -> 0x0afa }
        r28 = r28 & 15;	 Catch:{ Exception -> 0x0afa }
        r27 = r27.append(r28);	 Catch:{ Exception -> 0x0afa }
        r28 = 3;	 Catch:{ Exception -> 0x0afa }
        r29 = 1;	 Catch:{ Exception -> 0x0afa }
        r29 = r6[r29];	 Catch:{ Exception -> 0x0afa }
        r29 = r29 + -1;	 Catch:{ Exception -> 0x0afa }
        r0 = r28;	 Catch:{ Exception -> 0x0afa }
        r1 = r29;	 Catch:{ Exception -> 0x0afa }
        r28 = com.android.internal.telephony.uicc.IccUtils.bcdToString(r6, r0, r1);	 Catch:{ Exception -> 0x0afa }
        r27 = r27.append(r28);	 Catch:{ Exception -> 0x0afa }
        r28 = ";";	 Catch:{ Exception -> 0x0afa }
        r27 = r27.append(r28);	 Catch:{ Exception -> 0x0afa }
        r28 = 555; // 0x22b float:7.78E-43 double:2.74E-321;	 Catch:{ Exception -> 0x0afa }
        r0 = r6.length;	 Catch:{ Exception -> 0x0afa }
        r29 = r0;	 Catch:{ Exception -> 0x0afa }
        r29 = r29 + -1;	 Catch:{ Exception -> 0x0afa }
        r0 = r28;	 Catch:{ Exception -> 0x0afa }
        r1 = r29;	 Catch:{ Exception -> 0x0afa }
        r28 = com.android.internal.telephony.uicc.IccUtils.adnStringFieldToString(r6, r0, r1);	 Catch:{ Exception -> 0x0afa }
        r27 = r27.append(r28);	 Catch:{ Exception -> 0x0afa }
        r27 = r27.toString();	 Catch:{ Exception -> 0x0afa }
        r25[r26] = r27;	 Catch:{ Exception -> 0x0afa }
    L_0x0ad6:
        r25 = "RuimRecords";	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r26 = new java.lang.StringBuilder;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r26.<init>();	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r27 = "[sponImsi2]: ";	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r26 = r26.append(r27);	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = r30;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = r0.mSponImsi;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r27 = r0;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r28 = 1;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r27 = r27[r28];	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r26 = r26.append(r27);	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r26 = r26.toString();	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        android.telephony.Rlog.d(r25, r26);	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        goto L_0x004e;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
    L_0x0afa:
        r8 = move-exception;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r25 = "RuimRecords";	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r26 = new java.lang.StringBuilder;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r26.<init>();	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r27 = "[sponImsi2] Ex -";	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r26 = r26.append(r27);	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = r26;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r26 = r0.append(r8);	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r26 = r26.toString();	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        android.telephony.Rlog.d(r25, r26);	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r8.printStackTrace();	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = r30;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = r0.mSponImsi;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r25 = r0;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r26 = 1;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r27 = "";	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r25[r26] = r27;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        goto L_0x0ad6;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
    L_0x0b25:
        r11 = 1;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = r31;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r5 = r0.obj;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r5 = (android.os.AsyncResult) r5;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = r5.result;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r25 = r0;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r25 = (byte[]) r25;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = r25;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = (byte[]) r0;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r6 = r0;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = r5.exception;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r25 = r0;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        if (r25 == 0) goto L_0x0b46;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
    L_0x0b3d:
        r25 = "RuimRecords";	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r26 = "Invalid or missing EF[sponImsi3]";	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        android.telephony.Rlog.d(r25, r26);	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        goto L_0x004e;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
    L_0x0b46:
        r0 = r30;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = r0.mSponImsi;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r25 = r0;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r26 = 2;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r27 = new java.lang.String;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r27.<init>();	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r25[r26] = r27;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = r30;	 Catch:{ Exception -> 0x0bc5 }
        r0 = r0.mSponImsi;	 Catch:{ Exception -> 0x0bc5 }
        r25 = r0;	 Catch:{ Exception -> 0x0bc5 }
        r26 = 2;	 Catch:{ Exception -> 0x0bc5 }
        r27 = new java.lang.StringBuilder;	 Catch:{ Exception -> 0x0bc5 }
        r27.<init>();	 Catch:{ Exception -> 0x0bc5 }
        r28 = 2;	 Catch:{ Exception -> 0x0bc5 }
        r28 = r6[r28];	 Catch:{ Exception -> 0x0bc5 }
        r28 = r28 >> 4;	 Catch:{ Exception -> 0x0bc5 }
        r28 = r28 & 15;	 Catch:{ Exception -> 0x0bc5 }
        r27 = r27.append(r28);	 Catch:{ Exception -> 0x0bc5 }
        r28 = 3;	 Catch:{ Exception -> 0x0bc5 }
        r29 = 1;	 Catch:{ Exception -> 0x0bc5 }
        r29 = r6[r29];	 Catch:{ Exception -> 0x0bc5 }
        r29 = r29 + -1;	 Catch:{ Exception -> 0x0bc5 }
        r0 = r28;	 Catch:{ Exception -> 0x0bc5 }
        r1 = r29;	 Catch:{ Exception -> 0x0bc5 }
        r28 = com.android.internal.telephony.uicc.IccUtils.bcdToString(r6, r0, r1);	 Catch:{ Exception -> 0x0bc5 }
        r27 = r27.append(r28);	 Catch:{ Exception -> 0x0bc5 }
        r28 = ";";	 Catch:{ Exception -> 0x0bc5 }
        r27 = r27.append(r28);	 Catch:{ Exception -> 0x0bc5 }
        r28 = 555; // 0x22b float:7.78E-43 double:2.74E-321;	 Catch:{ Exception -> 0x0bc5 }
        r0 = r6.length;	 Catch:{ Exception -> 0x0bc5 }
        r29 = r0;	 Catch:{ Exception -> 0x0bc5 }
        r29 = r29 + -1;	 Catch:{ Exception -> 0x0bc5 }
        r0 = r28;	 Catch:{ Exception -> 0x0bc5 }
        r1 = r29;	 Catch:{ Exception -> 0x0bc5 }
        r28 = com.android.internal.telephony.uicc.IccUtils.adnStringFieldToString(r6, r0, r1);	 Catch:{ Exception -> 0x0bc5 }
        r27 = r27.append(r28);	 Catch:{ Exception -> 0x0bc5 }
        r27 = r27.toString();	 Catch:{ Exception -> 0x0bc5 }
        r25[r26] = r27;	 Catch:{ Exception -> 0x0bc5 }
    L_0x0ba1:
        r25 = "RuimRecords";	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r26 = new java.lang.StringBuilder;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r26.<init>();	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r27 = "[sponImsi3]: ";	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r26 = r26.append(r27);	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = r30;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = r0.mSponImsi;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r27 = r0;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r28 = 2;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r27 = r27[r28];	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r26 = r26.append(r27);	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r26 = r26.toString();	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        android.telephony.Rlog.d(r25, r26);	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        goto L_0x004e;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
    L_0x0bc5:
        r8 = move-exception;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r25 = "RuimRecords";	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r26 = new java.lang.StringBuilder;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r26.<init>();	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r27 = "[sponImsi3] Ex -";	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r26 = r26.append(r27);	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = r26;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r26 = r0.append(r8);	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r26 = r26.toString();	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        android.telephony.Rlog.d(r25, r26);	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r8.printStackTrace();	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = r30;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = r0.mSponImsi;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r25 = r0;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r26 = 2;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r27 = "";	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r25[r26] = r27;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        goto L_0x0ba1;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
    L_0x0bf0:
        r11 = 1;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = r31;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r5 = r0.obj;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r5 = (android.os.AsyncResult) r5;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = r5.result;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r25 = r0;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r25 = (byte[]) r25;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = r25;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = (byte[]) r0;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r6 = r0;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = r5.exception;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r25 = r0;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        if (r25 == 0) goto L_0x0c11;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
    L_0x0c08:
        r25 = "RuimRecords";	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r26 = "Invalid or missing EF[roaming]";	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        android.telephony.Rlog.d(r25, r26);	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        goto L_0x004e;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
    L_0x0c11:
        r19 = com.android.internal.telephony.uicc.IccUtils.bytesToHexString(r6);	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r25 = "RuimRecords";	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r26 = new java.lang.StringBuilder;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r26.<init>();	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r27 = "[roaming]: ";	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r26 = r26.append(r27);	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = r26;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r1 = r19;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r26 = r0.append(r1);	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r26 = r26.toString();	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        android.telephony.Rlog.d(r25, r26);	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r25 = "gsm.sim.roaming";	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = r25;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r1 = r19;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        android.os.SystemProperties.set(r0, r1);	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        goto L_0x004e;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
    L_0x0c3c:
        r21 = "";	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r20 = "";	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r11 = 1;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = r31;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r5 = r0.obj;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r5 = (android.os.AsyncResult) r5;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = r5.result;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r25 = r0;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r25 = (byte[]) r25;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = r25;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = (byte[]) r0;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r6 = r0;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = r5.exception;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r25 = r0;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        if (r25 == 0) goto L_0x0c61;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
    L_0x0c58:
        r25 = "RuimRecords";	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r26 = "Invalid or missing EF[RUIMID]";	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        android.telephony.Rlog.d(r25, r26);	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        goto L_0x004e;
    L_0x0c61:
        r21 = com.android.internal.telephony.uicc.IccUtils.bytesToHexString(r6);	 Catch:{ Exception -> 0x0d45 }
    L_0x0c65:
        r25 = "RuimRecords";	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r26 = new java.lang.StringBuilder;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r26.<init>();	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r27 = "[ruimid]: ";	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r26 = r26.append(r27);	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = r26;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r1 = r21;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r26 = r0.append(r1);	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r26 = r26.toString();	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        android.telephony.Rlog.d(r25, r26);	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r25 = r21.length();	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r26 = 10;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = r25;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r1 = r26;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        if (r0 <= r1) goto L_0x004e;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
    L_0x0c8d:
        r25 = "RuimRecords";	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r26 = new java.lang.StringBuilder;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r26.<init>();	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r27 = "[ruimid]converterd: ";	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r26 = r26.append(r27);	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r27 = 8;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r28 = 10;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = r21;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r1 = r27;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r2 = r28;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r27 = r0.substring(r1, r2);	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r26 = r26.append(r27);	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r27 = 6;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r28 = 8;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = r21;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r1 = r27;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r2 = r28;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r27 = r0.substring(r1, r2);	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r26 = r26.append(r27);	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r27 = 4;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r28 = 6;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = r21;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r1 = r27;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r2 = r28;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r27 = r0.substring(r1, r2);	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r26 = r26.append(r27);	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r27 = 2;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r28 = 4;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = r21;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r1 = r27;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r2 = r28;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r27 = r0.substring(r1, r2);	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r26 = r26.append(r27);	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r26 = r26.toString();	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        android.telephony.Rlog.d(r25, r26);	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r25 = new java.lang.StringBuilder;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r25.<init>();	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r26 = 8;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r27 = 10;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = r21;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r1 = r26;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r2 = r27;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r26 = r0.substring(r1, r2);	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r25 = r25.append(r26);	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r26 = 6;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r27 = 8;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = r21;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r1 = r26;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r2 = r27;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r26 = r0.substring(r1, r2);	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r25 = r25.append(r26);	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r26 = 4;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r27 = 6;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = r21;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r1 = r26;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r2 = r27;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r26 = r0.substring(r1, r2);	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r25 = r25.append(r26);	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r26 = 2;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r27 = 4;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = r21;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r1 = r26;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r2 = r27;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r26 = r0.substring(r1, r2);	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r25 = r25.append(r26);	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r20 = r25.toString();	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r25 = "ril.cdma.phone.id";	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = r25;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r1 = r20;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        android.os.SystemProperties.set(r0, r1);	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        goto L_0x004e;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
    L_0x0d45:
        r8 = move-exception;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r25 = "RuimRecords";	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r26 = new java.lang.StringBuilder;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r26.<init>();	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r27 = "[ruimid] Ex -";	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r26 = r26.append(r27);	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = r26;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r26 = r0.append(r8);	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r26 = r26.toString();	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        android.telephony.Rlog.d(r25, r26);	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r21 = "";	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        goto L_0x0c65;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
    L_0x0d64:
        r24 = "";	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r23 = "";	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r11 = 1;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = r31;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r5 = r0.obj;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r5 = (android.os.AsyncResult) r5;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = r5.result;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r25 = r0;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r25 = (byte[]) r25;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = r25;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = (byte[]) r0;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r6 = r0;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = r5.exception;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r25 = r0;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        if (r25 == 0) goto L_0x0d89;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
    L_0x0d80:
        r25 = "RuimRecords";	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r26 = "Invalid or missing EF[VER]";	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        android.telephony.Rlog.d(r25, r26);	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        goto L_0x004e;
    L_0x0d89:
        r24 = com.android.internal.telephony.uicc.IccUtils.bytesToHexString(r6);	 Catch:{ Exception -> 0x0dee }
    L_0x0d8d:
        r25 = "RuimRecords";	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r26 = new java.lang.StringBuilder;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r26.<init>();	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r27 = "[ver]: ";	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r26 = r26.append(r27);	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = r26;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r1 = r24;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r26 = r0.append(r1);	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r26 = r26.toString();	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        android.telephony.Rlog.d(r25, r26);	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r25 = r24.length();	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r26 = 6;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = r25;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r1 = r26;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        if (r0 <= r1) goto L_0x004e;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
    L_0x0db5:
        r25 = 10;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r26 = 14;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r25 = r24.substring(r25, r26);	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r26 = 16;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r25 = java.lang.Integer.parseInt(r25, r26);	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r23 = java.lang.Integer.toString(r25);	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r25 = "RuimRecords";	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r26 = new java.lang.StringBuilder;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r26.<init>();	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r27 = "[ver]converterd: ";	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r26 = r26.append(r27);	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = r26;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r1 = r23;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r26 = r0.append(r1);	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r26 = r26.toString();	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        android.telephony.Rlog.d(r25, r26);	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r25 = "gsm.sim.version";	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = r25;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r1 = r23;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        android.os.SystemProperties.set(r0, r1);	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        goto L_0x004e;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
    L_0x0dee:
        r8 = move-exception;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r25 = "RuimRecords";	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r26 = new java.lang.StringBuilder;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r26.<init>();	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r27 = "[ver] Ex -";	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r26 = r26.append(r27);	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = r26;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r26 = r0.append(r8);	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r26 = r26.toString();	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        android.telephony.Rlog.d(r25, r26);	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r24 = "";	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        goto L_0x0d8d;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
    L_0x0e0c:
        r25 = "3";	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = r30;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = r0.mIccType;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r26 = r0;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r25 = r25.equals(r26);	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        if (r25 == 0) goto L_0x004e;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
    L_0x0e1a:
        r25 = "EVENT_PB_INIT_COMPLETE, onSimPhonebookRefresh()";	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = r30;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r1 = r25;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0.log(r1);	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r30.onSimPhonebookRefresh();	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        goto L_0x004e;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
    L_0x0e28:
        r25 = "3";	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = r30;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = r0.mIccType;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r26 = r0;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r25 = r25.equals(r26);	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        if (r25 == 0) goto L_0x004e;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
    L_0x0e36:
        r25 = "EVENT_SIM_PB_READY, mParentApp.queryFdn()";	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = r30;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r1 = r25;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0.log(r1);	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = r30;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r0 = r0.mParentApp;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r25 = r0;	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        r25.queryFdn();	 Catch:{ RuntimeException -> 0x0058, all -> 0x0074 }
        goto L_0x004e;
        */
        throw new UnsupportedOperationException("Method not decompiled: com.android.internal.telephony.uicc.RuimRecords.handleMessage(android.os.Message):void");
    }

    public String toString() {
        return "RuimRecords: " + super.toString() + " m_ota_commited" + this.mOtaCommited + " mMyMobileNumber=" + "xxxx" + " mMin2Min1=" + this.mMin2Min1 + " mPrlVersion=" + this.mPrlVersion + " mEFpl=" + this.mEFpl + " mEFli=" + this.mEFli + " mCsimSpnDisplayCondition=" + this.mCsimSpnDisplayCondition + " mMdn=" + this.mMdn + " mMin=" + this.mMin + " mHomeSystemId=" + this.mHomeSystemId + " mHomeNetworkId=" + this.mHomeNetworkId;
    }

    public RuimRecords(UiccCardApplication app, Context c, CommandsInterface ci) {
        super(app, c, ci);
        this.mOtaCommited = false;
        this.mEFpl = null;
        this.mEFli = null;
        this.mCsimSpnDisplayCondition = false;
        this.isAvailableCHV1 = true;
        this.isAvailableADN = true;
        this.isAvailableFDN = false;
        this.isAvailableSMS = true;
        this.isAvailableMDN = true;
        this.isAvailableMSISDN = false;
        this.isAvailableSPN = false;
        this.mReceiver = new RUIMRecordsBroadcastReceiver();
        this.mImsiRequest = true;
        this.mAdnCache = new AdnRecordCache(this.mFh);
        this.mRecordsRequested = false;
        this.mRecordsToLoad = 0;
        this.mCi.registerForIccRefresh(this, 31, null);
        this.mCi.setOnPbInitComplete(this, 53, null);
        this.mCi.setOnSimPbReady(this, 54, null);
        resetRecords();
        this.mParentApp.registerForReady(this, 1, null);
        log("RuimRecords X ctor this=" + this);
        if ("DCG".equals("DGG") || "DCGG".equals("DGG") || "DCGS".equals("DGG") || "DCGGS".equals("DGG") || "CG".equals("DGG")) {
            IntentFilter intentFilter = new IntentFilter();
            intentFilter.addAction("com.samsung.intent.action.Slot1setCardDataInit");
            if ("DCGS".equals("DGG") || "DCGGS".equals("DGG")) {
                intentFilter.addAction("com.samsung.intent.action.Slot1SwitchCompleted");
                intentFilter.addAction("com.samsung.intent.action.Slot1OnCompleted");
            }
            this.mContext.registerReceiver(this.mReceiver, intentFilter);
            this.mParentApp.registerForLocked(this, 41, null);
        }
    }

    public void dispose() {
        log("Disposing RuimRecords " + this);
        this.mCi.unregisterForIccRefresh(this);
        this.mParentApp.unregisterForReady(this);
        this.mCi.unSetOnPbInitComplete(this);
        this.mCi.unSetOnSimPbReady(this);
        if ("DCG".equals("DGG") || "DCGG".equals("DGG") || "DCGS".equals("DGG") || "DCGGS".equals("DGG") || "CG".equals("DGG")) {
            this.mContext.unregisterReceiver(this.mReceiver);
        }
        resetRecords();
        super.dispose();
    }

    protected void finalize() {
        log("RuimRecords finalized");
    }

    protected void resetRecords() {
        this.mCountVoiceMessages = 0;
        this.mMncLength = -1;
        log("setting0 mMncLength" + this.mMncLength);
        this.mIccId = null;
        this.mAdnCache.reset();
        if ("DCGS".equals("DGG") || "DCGGS".equals("DGG")) {
            this.mMsisdn = null;
            this.mImsi = null;
            setSystemProperty("gsm.sim.operator.numeric", null);
            setSystemProperty("gsm.sim.operator.iso-country", null);
            setSystemProperty("gsm.sim.operator.alpha", null);
        }
        this.mRecordsRequested = false;
    }

    public String getIccId() {
        if (!"DCGG".equals("DGG") && !"DCGGS".equals("DGG") && !"CG".equals("DGG")) {
            return this.mIccId;
        }
        if (this.mIccId == null) {
            getIccIdfromFile();
        }
        if (this.mIccId != null) {
            return this.mIccId;
        }
        this.mFh.loadEFTransparent(IccConstants.EF_ICCID, obtainMessage(5));
        this.mRecordsToLoad++;
        return "";
    }

    private void getIccIdfromFile() {
        Throwable th;
        if ("CG".equals("DGG") || "DCG".equals("DGG") || "DCGG".equals("DGG") || "DCGS".equals("DGG") || "DCGGS".equals("DGG")) {
            File file;
            logi("getIccIdfromFile");
            FileInputStream fos = null;
            DataInputStream dos = null;
            if (!"DCGG".equals("DGG") && !"DCGGS".equals("DGG")) {
                file = new File(ICCID_PATH);
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
        setSystemProperty("gsm.sim.operator.numeric", "");
        fetchRuimRecords();
    }

    public String getIMSI_M() {
        return this.mImsi;
    }

    public String getIMSI() {
        return this.mImsi;
    }

    public String getMdnNumber() {
        return this.mMyMobileNumber;
    }

    public String getCdmaMin() {
        return this.mMin2Min1;
    }

    public String getPrlVersion() {
        return this.mPrlVersion;
    }

    public void setVoiceMailNumber(String alphaTag, String voiceNumber, Message onComplete) {
        AsyncResult.forMessage(onComplete).exception = new IccException("setVoiceMailNumber not implemented");
        onComplete.sendToTarget();
        loge("method setVoiceMailNumber is not implemented");
    }

    public void onRefresh(boolean fileChanged, int[] fileList) {
        if (fileChanged) {
            fetchRuimRecords();
        }
    }

    private int adjstMinDigits(int digits) {
        digits += 111;
        if (digits % 10 == 0) {
            digits -= 10;
        }
        if ((digits / 10) % 10 == 0) {
            digits -= 100;
        }
        if ((digits / 100) % 10 == 0) {
            return digits - 1000;
        }
        return digits;
    }

    public String getOperatorNumeric() {
        return getRUIMOperatorNumeric();
    }

    public String getRUIMOperatorNumeric() {
        if (this.mImsi == null) {
            return null;
        }
        if (this.mMncLength != -1 && this.mMncLength != 0) {
            return this.mImsi.substring(0, this.mMncLength + 3);
        }
        return this.mImsi.substring(0, MccTable.smallestDigitsMccForMnc(Integer.parseInt(this.mImsi.substring(0, 3))) + 3);
    }

    private void onGetCSimEprlDone(AsyncResult ar) {
        byte[] data = (byte[]) ar.result;
        log("CSIM_EPRL=" + IccUtils.bytesToHexString(data));
        if (data.length > 3) {
            this.mPrlVersion = Integer.toString(((data[2] & 255) << 8) | (data[3] & 255));
        }
        log("CSIM PRL version=" + this.mPrlVersion);
    }

    private static String[] getAssetLanguages(Context ctx) {
        String[] locales = ctx.getAssets().getLocales();
        String[] localeLangs = new String[locales.length];
        for (int i = 0; i < locales.length; i++) {
            String localeStr = locales[i];
            int separator = localeStr.indexOf(45);
            if (separator < 0) {
                localeLangs[i] = localeStr;
            } else {
                localeLangs[i] = localeStr.substring(0, separator);
            }
        }
        return localeLangs;
    }

    private String findBestLanguage(byte[] languages) {
        String[] assetLanguages = getAssetLanguages(this.mContext);
        if (languages == null || assetLanguages == null) {
            return null;
        }
        for (int i = 0; i + 1 < languages.length; i += 2) {
            try {
                String lang = new String(languages, i, 2, "ISO-8859-1");
                for (String equals : assetLanguages) {
                    if (equals.equals(lang)) {
                        return lang;
                    }
                }
                continue;
            } catch (UnsupportedEncodingException e) {
                log("Failed to parse SIM language records");
            }
        }
        return null;
    }

    private void setLocaleFromCsim() {
        String prefLang = findBestLanguage(this.mEFli);
        if (prefLang == null) {
            prefLang = findBestLanguage(this.mEFpl);
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
        log("No suitable CSIM selected locale");
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
        setLocaleFromCsim();
        this.mRecordsLoadedRegistrants.notifyRegistrants(new AsyncResult(null, null, null));
    }

    public void onReady() {
        fetchRuimRecords();
        this.mCi.getCDMASubscription(obtainMessage(10));
    }

    private void fetchRuimRecords() {
        this.mRecordsRequested = true;
        log("fetchRuimRecords " + this.mRecordsToLoad);
        if ("CG".equals("DGG") || "DCG".equals("DGG") || "DCGG".equals("DGG") || "DCGS".equals("DGG") || "DCGGS".equals("DGG")) {
            log("do not use GSM IMSI in cdma in dualcard");
        } else {
            this.mCi.getIMSIForApp(this.mParentApp.getAid(), obtainMessage(3));
            this.mRecordsToLoad++;
        }
        this.mFh.loadEFLinearFixed(IccConstants.EF_CSIM_MDN, 1, obtainMessage(6));
        this.mRecordsToLoad++;
        this.mFh.loadEFTransparent(IccConstants.EF_ICCID, obtainMessage(5));
        this.mRecordsToLoad++;
        if ("CG".equals("DGG") || "DCG".equals("DGG") || "DCGG".equals("DGG") || "DCGS".equals("DGG") || "DCGGS".equals("DGG")) {
            this.mFh.loadEFTransparent(IccConstants.EF_CSIM_IMSIM, obtainMessage(8));
            this.mRecordsToLoad++;
            this.mFh.loadEFTransparent(IccConstants.EF_CST, obtainMessage(16));
            this.mRecordsToLoad++;
            this.mFh.loadEFTransparent(IccConstants.EF_CSIM_EPRL, 4, obtainMessage(49));
            this.mRecordsToLoad++;
            this.mFh.loadEFTransparent(20256, 5, obtainMessage(50));
            this.mRecordsToLoad++;
            this.mFh.loadEFTransparent(IccConstants.EF_MSPL, 5, obtainMessage(51));
            this.mRecordsToLoad++;
            this.mFh.loadEFTransparent(20258, 5, obtainMessage(52));
            this.mRecordsToLoad++;
        }
        this.mFh.loadEFTransparent(IccConstants.EF_PL, obtainMessage(100, new EfPlLoaded()));
        this.mRecordsToLoad++;
        this.mFh.loadEFTransparent(28474, obtainMessage(100, new EfCsimLiLoaded()));
        this.mRecordsToLoad++;
        this.mFh.loadEFTransparent(28481, obtainMessage(100, new EfCsimSpnLoaded()));
        this.mRecordsToLoad++;
        this.mFh.loadEFLinearFixed(IccConstants.EF_CSIM_MDN, 1, obtainMessage(100, new EfCsimMdnLoaded()));
        this.mRecordsToLoad++;
        this.mFh.loadEFTransparent(IccConstants.EF_CSIM_IMSIM, obtainMessage(100, new EfCsimImsimLoaded()));
        this.mRecordsToLoad++;
        this.mFh.loadEFLinearFixedAll(IccConstants.EF_CSIM_CDMAHOME, obtainMessage(100, new EfCsimCdmaHomeLoaded()));
        this.mRecordsToLoad++;
        this.mFh.loadEFTransparent(IccConstants.EF_CSIM_EPRL, 4, obtainMessage(100, new EfCsimEprlLoaded()));
        this.mRecordsToLoad++;
        this.mFh.loadEFTransparent(IccConstants.EF_RUIMID, obtainMessage(37));
        this.mRecordsToLoad++;
        if ("EFversion".equals("")) {
            this.mFh.loadEFTransparent(IccConstants.EF_VER, obtainMessage(38));
            this.mRecordsToLoad++;
        }
        log("fetchRuimRecords " + this.mRecordsToLoad + " requested: " + this.mRecordsRequested);
    }

    public int getDisplayRule(String plmn) {
        return 0;
    }

    public boolean isProvisioned() {
        if (SystemProperties.getBoolean("persist.radio.test-csim", false)) {
            return true;
        }
        if (this.mParentApp == null) {
            return false;
        }
        if ("CG".equals("DGG") || "DCG".equals("DGG") || "DCGG".equals("DGG") || "DCGS".equals("DGG") || "DCGGS".equals("DGG")) {
            if (this.mParentApp.getType() == AppType.APPTYPE_CSIM && this.mMin == null) {
                return false;
            }
            return true;
        } else if (this.mParentApp.getType() != AppType.APPTYPE_CSIM) {
            return true;
        } else {
            if (this.mMdn == null || this.mMin == null) {
                return false;
            }
            return true;
        }
    }

    public void setVoiceMessageWaiting(int line, int countWaiting) {
        if (line == 1) {
            if (countWaiting < 0) {
                countWaiting = -1;
            } else if (countWaiting > 255) {
                countWaiting = 255;
            }
            this.mCountVoiceMessages = countWaiting;
            this.mRecordsEventsRegistrants.notifyResult(Integer.valueOf(0));
        }
    }

    private void handleRuimRefresh(IccRefreshResponse refreshResponse) {
        if (refreshResponse == null) {
            log("handleRuimRefresh received without input");
        } else if (refreshResponse.aid == null || refreshResponse.aid.equals(this.mParentApp.getAid())) {
            switch (refreshResponse.refreshResult) {
                case 0:
                    log("handleRuimRefresh with SIM_REFRESH_FILE_UPDATED");
                    this.mAdnCache.reset();
                    fetchRuimRecords();
                    return;
                case 1:
                    log("handleRuimRefresh with SIM_REFRESH_INIT");
                    if ("CG".equals("DGG") || "DCG".equals("DGG") || "DCGG".equals("DGG") || "DCGS".equals("DGG") || "DCGGS".equals("DGG")) {
                        this.mAdnCache.reset();
                    }
                    onIccRefreshInit();
                    return;
                case 2:
                    log("handleRuimRefresh with SIM_REFRESH_RESET");
                    if (requirePowerOffOnSimRefreshReset()) {
                        this.mCi.setRadioPower(false, null);
                        return;
                    }
                    return;
                default:
                    log("handleRuimRefresh with unknown operation");
                    return;
            }
        }
    }

    public String getMdn() {
        return this.mMdn;
    }

    public String getMin() {
        return this.mMin;
    }

    public String getSid() {
        return this.mHomeSystemId;
    }

    public String getNid() {
        return this.mHomeNetworkId;
    }

    public boolean getCsimSpnDisplayCondition() {
        return this.mCsimSpnDisplayCondition;
    }

    protected void log(String s) {
        if (this.phone != null) {
            Rlog.d(MultiSimManager.appendSimSlot(LOG_TAG, this.phone.getPhoneId()), s);
        } else {
            Rlog.d(LOG_TAG, "[RuimRecords] " + s);
        }
    }

    protected void loge(String s) {
        if (this.phone != null) {
            Rlog.e(MultiSimManager.appendSimSlot(LOG_TAG, this.phone.getPhoneId()), s);
        } else {
            Rlog.e(LOG_TAG, "[RuimRecords] " + s);
        }
    }

    protected void logi(String s) {
        if (this.phone != null) {
            Rlog.i(MultiSimManager.appendSimSlot(LOG_TAG, this.phone.getPhoneId()), s);
        } else {
            Rlog.i(LOG_TAG, "[RuimRecords] " + s);
        }
    }

    protected void logw(String s) {
        if (this.phone != null) {
            Rlog.w(MultiSimManager.appendSimSlot(LOG_TAG, this.phone.getPhoneId()), s);
        } else {
            Rlog.w(LOG_TAG, "[RuimRecords] " + s);
        }
    }

    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        pw.println("RuimRecords: " + this);
        pw.println(" extends:");
        super.dump(fd, pw, args);
        pw.println(" mOtaCommited=" + this.mOtaCommited);
        pw.println(" mMyMobileNumber=" + this.mMyMobileNumber);
        pw.println(" mMin2Min1=" + this.mMin2Min1);
        pw.println(" mPrlVersion=" + this.mPrlVersion);
        pw.println(" mEFpl[]=" + Arrays.toString(this.mEFpl));
        pw.println(" mEFli[]=" + Arrays.toString(this.mEFli));
        pw.println(" mCsimSpnDisplayCondition=" + this.mCsimSpnDisplayCondition);
        pw.println(" mMdn=" + this.mMdn);
        pw.println(" mMin=" + this.mMin);
        pw.println(" mHomeSystemId=" + this.mHomeSystemId);
        pw.println(" mHomeNetworkId=" + this.mHomeNetworkId);
        pw.flush();
    }

    private void checkSMSavailable(byte[] table) {
        log("Enter  checkSMSavailable");
        try {
            this.isAvailableSMS = findTheEnabledServiceInCST(table[0], 6);
            log("isAvailableSMS is " + this.isAvailableSMS);
            log("isAvailableSMS is " + this.isAvailableSMS);
        } catch (ArrayIndexOutOfBoundsException e) {
            Rlog.d(LOG_TAG, "Exception", e);
            this.isAvailableSMS = true;
        }
    }

    private boolean findTheEnabledServiceInCST(byte b, int position) {
        log("findTheEnabledServiceInCST");
        log("position = " + position);
        if ((((byte) (b >> position)) & 3) == 3) {
            return true;
        }
        return false;
    }

    private void checkCHV1available(byte[] table) {
        log("Enter  checkCHV1available");
        try {
            this.isAvailableCHV1 = findTheEnabledServiceInCST(table[0], 0);
            log("isAvailableCHV1 is " + this.isAvailableCHV1);
            log("isAvailableCHV1 is " + this.isAvailableCHV1);
        } catch (ArrayIndexOutOfBoundsException e) {
            Rlog.d(LOG_TAG, "Exception", e);
            this.isAvailableCHV1 = true;
        }
    }

    private void checkADNavailable(byte[] table) {
        log("Enter  checkADNavailable");
        try {
            this.isAvailableADN = findTheEnabledServiceInCST(table[0], 2);
            log("isAvailableADN is " + this.isAvailableADN);
            if (this.isAvailableADN) {
                SystemProperties.set("gsm.sim.adn1", "true");
            } else {
                SystemProperties.set("gsm.sim.adn1", "false");
            }
            log("isAvailableADN is " + this.isAvailableADN);
        } catch (ArrayIndexOutOfBoundsException e) {
            Rlog.d(LOG_TAG, "Exception", e);
            this.isAvailableADN = true;
            SystemProperties.set("gsm.sim.adn1", "true");
        }
    }

    private void checkFDNavailable(byte[] table) {
        log("Enter  checkFDNavailable");
        try {
            this.isAvailableFDN = findTheEnabledServiceInCST(table[0], 4);
            log("isAvailableFDN is " + this.isAvailableFDN);
            log("isAvailableFDN is " + this.isAvailableFDN);
        } catch (ArrayIndexOutOfBoundsException e) {
            Rlog.d(LOG_TAG, "Exception", e);
            this.isAvailableFDN = false;
        }
    }

    public String[] getSponImsi() {
        return this.mSponImsi != null ? (String[]) this.mSponImsi.clone() : null;
    }

    public void refreshUiccVer() {
        log("[refreshUiccVer] refreshed");
        this.mFh.loadEFTransparent(IccConstants.EF_VER, obtainMessage(38));
    }
}
