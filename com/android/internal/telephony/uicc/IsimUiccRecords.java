package com.android.internal.telephony.uicc;

import android.app.ActivityManagerNative;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncResult;
import android.os.Message;
import android.telephony.Rlog;
import com.android.internal.telephony.CommandException;
import com.android.internal.telephony.CommandException.Error;
import com.android.internal.telephony.CommandsInterface;
import com.android.internal.telephony.gsm.SimTlv;
import com.android.internal.telephony.uicc.IccRecords.IccRecordLoaded;
import java.io.ByteArrayOutputStream;
import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.net.URI;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;

public final class IsimUiccRecords extends IccRecords implements IsimRecords {
    private static final boolean DBG = true;
    private static final boolean DUMP_RECORDS = true;
    private static final int EVENT_AKA_AUTHENTICATE_DONE = 90;
    private static final int EVENT_APP_READY = 1;
    private static final int EVENT_ISIM_REFRESH = 31;
    public static final String INTENT_ISIM_REFRESH = "com.android.intent.isim_refresh";
    private static final int IST_GBA = 2;
    protected static final String LOG_TAG = "IsimUiccRecords";
    private static final int TAG_ISIM_VALUE = 128;
    private String auth_rsp;
    private String mBtid;
    private boolean mIsGbaSupported;
    private String mIsimDomain;
    private String mIsimImpi;
    private String[] mIsimImpu;
    private String mIsimIst;
    private String mIsimMsisdn;
    private String[] mIsimPcscf;
    private String mKeyLifetime;
    private final Object mLock;
    private byte[] mRand;

    private class EfIsimDomainLoaded implements IccRecordLoaded {
        private EfIsimDomainLoaded() {
        }

        public String getEfName() {
            return "EF_ISIM_DOMAIN";
        }

        public void onRecordLoaded(AsyncResult ar) {
            IsimUiccRecords.this.mIsimDomain = IsimUiccRecords.isimTlvToString((byte[]) ar.result);
            IsimUiccRecords.this.log("EF_DOMAIN=" + IsimUiccRecords.this.mIsimDomain);
        }
    }

    private class EfIsimGbabpLoaded implements IccRecordLoaded {
        private EfIsimGbabpLoaded() {
        }

        public String getEfName() {
            return "EF_ISIM_GBABP";
        }

        public void onRecordLoaded(AsyncResult ar) {
            byte[] data = (byte[]) ar.result;
            try {
                data = (byte[]) ar.result;
                int i = 0 + 1;
                int l = data[0];
                IsimUiccRecords.this.mRand = new byte[l];
                System.arraycopy(data, i, IsimUiccRecords.this.mRand, 0, l);
                int i2 = l + 1;
                i = i2 + 1;
                l = data[i2];
                IsimUiccRecords.this.mBtid = new String(data, i, l, "UTF-8");
                i2 = i + l;
                IsimUiccRecords.this.mKeyLifetime = new String(data, i2 + 1, data[i2], "UTF-8");
                IsimUiccRecords.this.log("mRand=" + IsimUiccRecords.this.mRand);
                IsimUiccRecords.this.log("mBtid=" + IsimUiccRecords.this.mBtid);
                IsimUiccRecords.this.log("mKeyLifetime=" + IsimUiccRecords.this.mKeyLifetime);
            } catch (Exception e) {
                IsimUiccRecords.this.log("Failed to parse GBABP contents: " + e);
                IsimUiccRecords.this.mRand = null;
                IsimUiccRecords.this.mBtid = null;
                IsimUiccRecords.this.mKeyLifetime = null;
            }
        }
    }

    private class EfIsimImpiLoaded implements IccRecordLoaded {
        private EfIsimImpiLoaded() {
        }

        public String getEfName() {
            return "EF_ISIM_IMPI";
        }

        public void onRecordLoaded(AsyncResult ar) {
            IsimUiccRecords.this.mIsimImpi = IsimUiccRecords.isimTlvToString((byte[]) ar.result);
            IsimUiccRecords.this.log("EF_IMPI=" + IsimUiccRecords.this.mIsimImpi);
        }
    }

    private class EfIsimImpuLoaded implements IccRecordLoaded {
        private EfIsimImpuLoaded() {
        }

        public String getEfName() {
            return "EF_ISIM_IMPU";
        }

        public void onRecordLoaded(AsyncResult ar) {
            ArrayList<byte[]> impuList = ar.result;
            IsimUiccRecords.this.log("EF_IMPU record count: " + impuList.size());
            IsimUiccRecords.this.mIsimImpu = new String[impuList.size()];
            int i = 0;
            Iterator i$ = impuList.iterator();
            while (i$.hasNext()) {
                String impu = IsimUiccRecords.isimTlvToString((byte[]) i$.next());
                IsimUiccRecords.this.log("EF_IMPU[" + i + "]=" + impu);
                int i2 = i + 1;
                IsimUiccRecords.this.mIsimImpu[i] = impu;
                if (impu.contains("+") || impu.startsWith("tel")) {
                    IsimUiccRecords.this.mIsimMsisdn = IsimUiccRecords.this.extractNumber(impu);
                }
                i = i2;
            }
        }
    }

    private class EfIsimIstLoaded implements IccRecordLoaded {
        private EfIsimIstLoaded() {
        }

        public String getEfName() {
            return "EF_ISIM_IST";
        }

        public void onRecordLoaded(AsyncResult ar) {
            byte[] data = (byte[]) ar.result;
            IsimUiccRecords.this.mIsGbaSupported = (((byte[]) ((byte[]) ar.result))[0] & 2) != 0;
            IsimUiccRecords.this.mIsimIst = IccUtils.bytesToHexString(data);
            IsimUiccRecords.this.log("EF_IST=" + IsimUiccRecords.this.mIsimIst);
            IsimUiccRecords.this.log("mIsGbaSupported=" + IsimUiccRecords.this.mIsGbaSupported);
        }
    }

    private class EfIsimPcscfLoaded implements IccRecordLoaded {
        private EfIsimPcscfLoaded() {
        }

        public String getEfName() {
            return "EF_ISIM_PCSCF";
        }

        public void onRecordLoaded(AsyncResult ar) {
            ArrayList<byte[]> pcscflist = ar.result;
            IsimUiccRecords.this.log("EF_PCSCF record count: " + pcscflist.size());
            IsimUiccRecords.this.mIsimPcscf = new String[pcscflist.size()];
            int i = 0;
            Iterator i$ = pcscflist.iterator();
            while (i$.hasNext()) {
                String pcscf = IsimUiccRecords.isimTlvToString((byte[]) i$.next());
                IsimUiccRecords.this.log("EF_PCSCF[" + i + "]=" + pcscf);
                int i2 = i + 1;
                IsimUiccRecords.this.mIsimPcscf[i] = pcscf;
                i = i2;
            }
        }
    }

    public String toString() {
        return "IsimUiccRecords: " + super.toString() + " mIsimImpi=" + this.mIsimImpi + " mIsimDomain=" + this.mIsimDomain + " mIsimImpu=" + this.mIsimImpu + " mIsimIst=" + this.mIsimIst + " mIsimPcscf=" + this.mIsimPcscf;
    }

    public IsimUiccRecords(UiccCardApplication app, Context c, CommandsInterface ci) {
        super(app, c, ci);
        this.mIsGbaSupported = false;
        this.mLock = new Object();
        this.mRecordsRequested = false;
        this.mRecordsToLoad = 0;
        resetRecords();
        this.mCi.registerForIccRefresh(this, 31, null);
        this.mParentApp.registerForReady(this, 1, null);
        log("IsimUiccRecords X ctor this=" + this);
    }

    public void dispose() {
        log("Disposing " + this);
        this.mCi.unregisterForIccRefresh(this);
        this.mParentApp.unregisterForReady(this);
        resetRecords();
        super.dispose();
    }

    public void handleMessage(Message msg) {
        if (this.mDestroyed.get()) {
            Rlog.e(LOG_TAG, "Received message " + msg + "[" + msg.what + "] while being destroyed. Ignoring.");
            return;
        }
        loge("IsimUiccRecords: handleMessage " + msg + "[" + msg.what + "] ");
        try {
            AsyncResult ar;
            switch (msg.what) {
                case 1:
                    onReady();
                    return;
                case 31:
                    ar = msg.obj;
                    loge("ISim REFRESH(EVENT_ISIM_REFRESH) with exception: " + ar.exception);
                    if (ar.exception == null) {
                        Intent intent = new Intent(INTENT_ISIM_REFRESH);
                        loge("send ISim REFRESH: com.android.intent.isim_refresh");
                        this.mContext.sendBroadcast(intent);
                        handleIsimRefresh((IccRefreshResponse) ar.result);
                        return;
                    }
                    return;
                case EVENT_AKA_AUTHENTICATE_DONE /*90*/:
                    ar = (AsyncResult) msg.obj;
                    log("EVENT_AKA_AUTHENTICATE_DONE");
                    if (ar.exception != null && (ar.exception instanceof CommandException) && ((CommandException) ar.exception).getCommandError() == Error.MAC_ADDRESS_FAIL) {
                        this.auth_rsp = "9862";
                    }
                    String result = ar.result;
                    if (result != null) {
                        this.auth_rsp = result;
                    }
                    log("ISIM AKA: auth_rsp = " + this.auth_rsp);
                    synchronized (this.mLock) {
                        this.mLock.notifyAll();
                    }
                    return;
                default:
                    super.handleMessage(msg);
                    return;
            }
        } catch (RuntimeException exc) {
            Rlog.w(LOG_TAG, "Exception parsing SIM record", exc);
        }
        Rlog.w(LOG_TAG, "Exception parsing SIM record", exc);
    }

    protected void fetchIsimRecords() {
        this.mRecordsRequested = true;
        this.mFh.loadEFTransparent(28418, obtainMessage(100, new EfIsimImpiLoaded()));
        this.mRecordsToLoad++;
        this.mFh.loadEFLinearFixedAll(IccConstants.EF_IMPU, obtainMessage(100, new EfIsimImpuLoaded()));
        this.mRecordsToLoad++;
        this.mFh.loadEFTransparent(IccConstants.EF_DOMAIN, obtainMessage(100, new EfIsimDomainLoaded()));
        this.mRecordsToLoad++;
        this.mFh.loadEFTransparent(IccConstants.EF_IST, obtainMessage(100, new EfIsimIstLoaded()));
        this.mRecordsToLoad++;
        this.mFh.loadEFLinearFixedAll(IccConstants.EF_PCSCF, obtainMessage(100, new EfIsimPcscfLoaded()));
        this.mRecordsToLoad++;
        this.mFh.loadEFTransparent(IccConstants.EF_GBABP, obtainMessage(100, new EfIsimGbabpLoaded()));
        this.mRecordsToLoad++;
        log("fetchIsimRecords " + this.mRecordsToLoad + " requested: " + this.mRecordsRequested);
    }

    protected void resetRecords() {
        this.mIsimImpi = null;
        this.mIsimDomain = null;
        this.mIsimImpu = null;
        this.mIsimIst = null;
        this.mIsimPcscf = null;
        this.auth_rsp = null;
        this.mIsGbaSupported = false;
        this.mIsimMsisdn = null;
        this.mRecordsRequested = false;
        this.mRand = null;
        this.mBtid = null;
        this.mKeyLifetime = null;
    }

    protected String extractNumber(String number) {
        String msisdn = URI.create(number.trim()).getSchemeSpecificPart().toLowerCase();
        int idx = msisdn.indexOf("@");
        if (idx != -1) {
            return msisdn.substring(0, idx);
        }
        return msisdn;
    }

    private static String isimTlvToString(byte[] record) {
        SimTlv tlv = new SimTlv(record, 0, record.length);
        while (tlv.getTag() != 128) {
            if (!tlv.nextObject()) {
                Rlog.e(LOG_TAG, "[ISIM] can't find TLV tag in ISIM record, returning null");
                return null;
            }
        }
        return new String(tlv.getData(), Charset.forName("UTF-8"));
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
        this.mRecordsLoadedRegistrants.notifyRegistrants(new AsyncResult(null, null, null));
        broadcastIsimLoadedIntent();
    }

    private void handleFileUpdate(int efid) {
        switch (efid) {
            case 28418:
                this.mFh.loadEFTransparent(28418, obtainMessage(100, new EfIsimImpiLoaded()));
                this.mRecordsToLoad++;
                return;
            case IccConstants.EF_DOMAIN /*28419*/:
                this.mFh.loadEFTransparent(IccConstants.EF_DOMAIN, obtainMessage(100, new EfIsimDomainLoaded()));
                this.mRecordsToLoad++;
                return;
            case IccConstants.EF_IMPU /*28420*/:
                this.mFh.loadEFLinearFixedAll(IccConstants.EF_IMPU, obtainMessage(100, new EfIsimImpuLoaded()));
                this.mRecordsToLoad++;
                return;
            case IccConstants.EF_IST /*28423*/:
                this.mFh.loadEFTransparent(IccConstants.EF_IST, obtainMessage(100, new EfIsimIstLoaded()));
                this.mRecordsToLoad++;
                return;
            case IccConstants.EF_PCSCF /*28425*/:
                this.mFh.loadEFLinearFixedAll(IccConstants.EF_PCSCF, obtainMessage(100, new EfIsimPcscfLoaded()));
                this.mRecordsToLoad++;
                break;
        }
        fetchIsimRecords();
    }

    private void handleIsimRefresh(IccRefreshResponse refreshResponse) {
        if (refreshResponse == null) {
            log("handleIsimRefresh received without input");
        } else if (refreshResponse.aid == null || refreshResponse.aid.equals(this.mParentApp.getAid())) {
            switch (refreshResponse.refreshResult) {
                case 0:
                    log("handleIsimRefresh with REFRESH_RESULT_FILE_UPDATE");
                    handleFileUpdate(refreshResponse.efId);
                    return;
                case 1:
                    log("handleIsimRefresh with REFRESH_RESULT_INIT");
                    fetchIsimRecords();
                    return;
                case 2:
                    log("handleIsimRefresh with REFRESH_RESULT_RESET");
                    if (requirePowerOffOnSimRefreshReset()) {
                        this.mCi.setRadioPower(false, null);
                        return;
                    }
                    return;
                default:
                    log("handleIsimRefresh with unknown operation");
                    return;
            }
        } else {
            log("handleIsimRefresh received different app");
        }
    }

    public String getIsimImpi() {
        return this.mIsimImpi;
    }

    public String getIsimDomain() {
        return this.mIsimDomain;
    }

    public String[] getIsimImpu() {
        return this.mIsimImpu != null ? (String[]) this.mIsimImpu.clone() : null;
    }

    public String getIsimIst() {
        return this.mIsimIst;
    }

    public String[] getIsimPcscf() {
        return this.mIsimPcscf != null ? (String[]) this.mIsimPcscf.clone() : null;
    }

    public byte[] getRand() {
        return this.mRand;
    }

    public String getKeyLifetime() {
        return this.mKeyLifetime;
    }

    public String getBtid() {
        return this.mBtid;
    }

    public String getIsimChallengeResponse(String nonce) {
        log("getIsimChallengeResponse-nonce:" + nonce);
        try {
            synchronized (this.mLock) {
                this.mCi.requestIsimAuthentication(nonce, obtainMessage(EVENT_AKA_AUTHENTICATE_DONE));
                try {
                    this.mLock.wait();
                } catch (InterruptedException e) {
                    log("interrupted while trying to request Isim Auth");
                }
            }
            log("getIsimChallengeResponse-auth_rsp" + this.auth_rsp);
            return this.auth_rsp;
        } catch (Exception e2) {
            log("Fail while trying to request Isim Auth");
            return null;
        }
    }

    private void broadcastIsimLoadedIntent() {
        Intent intent = new Intent("android.intent.action.ISIM_LOADED");
        intent.addFlags(536870912);
        log("Broadcasting intent ACTION_ISIM_LOADED ");
        ActivityManagerNative.broadcastStickyIntent(intent, "android.permission.READ_PHONE_STATE", -1);
    }

    public String getAid() {
        return this.mParentApp.getAid();
    }

    public boolean isGbaSupported() {
        return this.mIsGbaSupported;
    }

    public int getDisplayRule(String plmn) {
        return 0;
    }

    public void onReady() {
        fetchIsimRecords();
    }

    public void onRefresh(boolean fileChanged, int[] fileList) {
        if (fileChanged) {
            fetchIsimRecords();
        }
    }

    public void setVoiceMailNumber(String alphaTag, String voiceNumber, Message onComplete) {
    }

    public void setVoiceMessageWaiting(int line, int countWaiting) {
    }

    protected void log(String s) {
        Rlog.d(LOG_TAG, "[ISIM] " + s);
    }

    protected void loge(String s) {
        Rlog.e(LOG_TAG, "[ISIM] " + s);
    }

    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        pw.println("IsimRecords: " + this);
        pw.println(" extends:");
        super.dump(fd, pw, args);
        pw.println(" mIsimImpi=" + this.mIsimImpi);
        pw.println(" mIsimDomain=" + this.mIsimDomain);
        pw.println(" mIsimImpu[]=" + Arrays.toString(this.mIsimImpu));
        pw.println(" mIsimIst" + this.mIsimIst);
        pw.println(" mIsimPcscf" + this.mIsimPcscf);
        pw.flush();
    }

    public String[] getSponImsi() {
        return null;
    }

    public void refreshUiccVer() {
    }

    private void appendGbaParameter(ByteArrayOutputStream os, byte[] data) {
        int len = data.length;
        if (len > 255) {
            len = 255;
            log("Too long value in GBA Bootstrapping parameters");
        }
        os.write(len);
        os.write(data, 0, len);
    }

    public void setGbaBootstrappingParams(byte[] rand, String btid, String keyLifetime, Message onComplete) {
        log("setGbaBootstrappingParams");
        if (rand != null) {
            this.mRand = rand;
        }
        if (btid != null) {
            this.mBtid = btid;
        }
        if (keyLifetime != null) {
            this.mKeyLifetime = keyLifetime;
        }
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        appendGbaParameter(os, this.mRand);
        appendGbaParameter(os, this.mBtid.getBytes());
        appendGbaParameter(os, this.mKeyLifetime.getBytes());
        this.mParentApp.getIccFileHandler().updateEFTransparent(IccConstants.EF_GBABP, os.toByteArray(), onComplete);
    }

    public String getIsimMsisdn() {
        return this.mIsimMsisdn;
    }
}
