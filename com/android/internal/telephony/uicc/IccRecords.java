package com.android.internal.telephony.uicc;

import android.content.Context;
import android.content.Intent;
import android.os.AsyncResult;
import android.os.Handler;
import android.os.Message;
import android.os.Registrant;
import android.os.RegistrantList;
import android.os.SystemProperties;
import android.telephony.TelephonyManager;
import android.util.Base64;
import com.android.internal.telephony.CommandsInterface;
import com.android.internal.telephony.PhoneBase;
import com.android.internal.telephony.SubscriptionController;
import com.android.internal.telephony.uicc.IccCardApplicationStatus.AppState;
import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.concurrent.atomic.AtomicBoolean;

public abstract class IccRecords extends Handler implements IccConstants {
    protected static final boolean DBG = true;
    private static final int EVENT_AKA_AUTHENTICATE_DONE = 90;
    protected static final int EVENT_APP_READY = 1;
    public static final int EVENT_CFI = 1;
    public static final int EVENT_EONS = 3;
    public static final int EVENT_GET_ICC_RECORD_DONE = 100;
    public static final int EVENT_MWI = 0;
    protected static final int EVENT_SET_MSISDN_DONE = 30;
    public static final int EVENT_SPN = 2;
    protected static final String ICC_TYPE = "ril.ICC_TYPE";
    static final boolean SHIP_BUILD = "true".equals(SystemProperties.get("ro.product_ship", "false"));
    public static final int SPN_RULE_SHOW_PLMN = 2;
    public static final int SPN_RULE_SHOW_SPN = 1;
    protected static final int UNINITIALIZED = -1;
    protected static final int UNKNOWN = 0;
    private IccIoResult auth_rsp;
    protected AdnRecordCache mAdnCache;
    protected CommandsInterface mCi;
    protected Context mContext;
    protected int mCountVoiceMessages = 0;
    protected AtomicBoolean mDestroyed = new AtomicBoolean(false);
    protected byte[] mEfPsismsc = null;
    public String mEuimid;
    protected IccFileHandler mFh;
    protected String mGid1;
    protected String mIccId;
    protected RegistrantList mIccIdReadyRegistrants = new RegistrantList();
    protected String mIccType = null;
    protected String mImsi;
    protected RegistrantList mImsiReadyRegistrants = new RegistrantList();
    protected boolean mIsAvailablePSISMSC = false;
    protected boolean mIsAvailableSDN = true;
    public boolean mIsEnabledPNN = false;
    public boolean mIsPNNExist = false;
    protected boolean mIsVoiceMailFixed = false;
    private final Object mLock = new Object();
    protected int mMailboxIndex = 0;
    protected String mMdn;
    protected RegistrantList mMdnReadyRegistrants = new RegistrantList();
    protected int mMncLength = -1;
    protected String mMsisdn = null;
    protected String mMsisdnTag = null;
    protected RegistrantList mNetworkSelectionModeAutomaticRegistrants = new RegistrantList();
    protected RegistrantList mNewSmsRegistrants = new RegistrantList();
    protected String mNewVoiceMailNum = null;
    protected String mNewVoiceMailTag = null;
    protected UiccCardApplication mParentApp;
    protected RegistrantList mRecordsEventsRegistrants = new RegistrantList();
    protected RegistrantList mRecordsLoadedRegistrants = new RegistrantList();
    protected boolean mRecordsRequested = false;
    protected int mRecordsToLoad;
    protected String mSpn;
    protected String mVoiceMailNum = null;
    protected String mVoiceMailTag = null;
    protected PhoneBase phone;

    public interface IccRecordLoaded {
        String getEfName();

        void onRecordLoaded(AsyncResult asyncResult);
    }

    public abstract int getDisplayRule(String str);

    public abstract String[] getSponImsi();

    protected abstract void log(String str);

    protected abstract void loge(String str);

    protected abstract void onAllRecordsLoaded();

    public abstract void onReady();

    protected abstract void onRecordLoaded();

    public abstract void onRefresh(boolean z, int[] iArr);

    public abstract void refreshUiccVer();

    public abstract void setVoiceMailNumber(String str, String str2, Message message);

    public abstract void setVoiceMessageWaiting(int i, int i2);

    public String toString() {
        return !SHIP_BUILD ? "mDestroyed=" + this.mDestroyed + " mContext=" + this.mContext + " mCi=" + this.mCi + " mFh=" + this.mFh + " mParentApp=" + this.mParentApp + " recordsLoadedRegistrants=" + this.mRecordsLoadedRegistrants + " mImsiReadyRegistrants=" + this.mImsiReadyRegistrants + " mRecordsEventsRegistrants=" + this.mRecordsEventsRegistrants + " mNewSmsRegistrants=" + this.mNewSmsRegistrants + " mNetworkSelectionModeAutomaticRegistrants=" + this.mNetworkSelectionModeAutomaticRegistrants + " recordsToLoad=" + this.mRecordsToLoad + " adnCache=" + this.mAdnCache + " recordsRequested=" + this.mRecordsRequested + " iccid=" + this.mIccId + " msisdn=" + this.mMsisdn + " msisdnTag=" + this.mMsisdnTag + " voiceMailNum=" + this.mVoiceMailNum + " voiceMailTag=" + this.mVoiceMailTag + " newVoiceMailNum=" + this.mNewVoiceMailNum + " newVoiceMailTag=" + this.mNewVoiceMailTag + " isVoiceMailFixed=" + this.mIsVoiceMailFixed + " countVoiceMessages=" + this.mCountVoiceMessages + " mImsi=" + this.mImsi + " mncLength=" + this.mMncLength + " mailboxIndex=" + this.mMailboxIndex + " spn=" + this.mSpn : "xxx";
    }

    public IccRecords(UiccCardApplication app, Context c, CommandsInterface ci) {
        this.mContext = c;
        this.mCi = ci;
        this.mFh = app.getIccFileHandler();
        this.mParentApp = app;
        this.phone = this.mParentApp.mPhone;
        this.mIccType = getSystemProperty(ICC_TYPE, "");
    }

    public void dispose() {
        this.mDestroyed.set(true);
        this.mParentApp = null;
        this.mFh = null;
        this.mCi = null;
        this.mContext = null;
    }

    public AdnRecordCache getAdnCache() {
        return this.mAdnCache;
    }

    public String getIccId() {
        return this.mIccId;
    }

    public void registerForRecordsLoaded(Handler h, int what, Object obj) {
        if (!this.mDestroyed.get()) {
            Registrant r = new Registrant(h, what, obj);
            this.mRecordsLoadedRegistrants.add(r);
            if (this.mRecordsToLoad == 0 && this.mRecordsRequested) {
                r.notifyRegistrant(new AsyncResult(null, null, null));
            }
        }
    }

    public void unregisterForRecordsLoaded(Handler h) {
        this.mRecordsLoadedRegistrants.remove(h);
    }

    public void registerForImsiReady(Handler h, int what, Object obj) {
        if (!this.mDestroyed.get()) {
            Registrant r = new Registrant(h, what, obj);
            this.mImsiReadyRegistrants.add(r);
            if (this.mImsi != null) {
                r.notifyRegistrant(new AsyncResult(null, null, null));
            }
        }
    }

    public void unregisterForImsiReady(Handler h) {
        this.mImsiReadyRegistrants.remove(h);
    }

    public void registerForRecordsEvents(Handler h, int what, Object obj) {
        Registrant r = new Registrant(h, what, obj);
        this.mRecordsEventsRegistrants.add(r);
        r.notifyResult(Integer.valueOf(0));
        r.notifyResult(Integer.valueOf(1));
    }

    public void unregisterForRecordsEvents(Handler h) {
        this.mRecordsEventsRegistrants.remove(h);
    }

    public void registerForNewSms(Handler h, int what, Object obj) {
        this.mNewSmsRegistrants.add(new Registrant(h, what, obj));
    }

    public void unregisterForNewSms(Handler h) {
        this.mNewSmsRegistrants.remove(h);
    }

    public void registerForNetworkSelectionModeAutomatic(Handler h, int what, Object obj) {
        this.mNetworkSelectionModeAutomaticRegistrants.add(new Registrant(h, what, obj));
    }

    public void unregisterForNetworkSelectionModeAutomatic(Handler h) {
        this.mNetworkSelectionModeAutomaticRegistrants.remove(h);
    }

    public void registerForMdnReady(Handler h, int what, Object obj) {
        if (!this.mDestroyed.get()) {
            Registrant r = new Registrant(h, what, obj);
            this.mMdnReadyRegistrants.add(r);
            if (this.mMdn != null) {
                r.notifyRegistrant(new AsyncResult(null, null, null));
            }
        }
    }

    public void unregisterForMdnReady(Handler h) {
        this.mMdnReadyRegistrants.remove(h);
    }

    public void registerForIccIdReady(Handler h, int what, Object obj) {
        if (!this.mDestroyed.get()) {
            Registrant r = new Registrant(h, what, obj);
            this.mIccIdReadyRegistrants.add(r);
            if (this.mIccId != null) {
                r.notifyRegistrant(new AsyncResult(null, null, null));
            }
        }
    }

    public void unregisterForIccIdReady(Handler h) {
        this.mIccIdReadyRegistrants.remove(h);
    }

    public String getIMSI() {
        return null;
    }

    public void setImsi(String imsi) {
        this.mImsi = imsi;
        this.mImsiReadyRegistrants.notifyRegistrants();
    }

    public String getMsisdnNumber() {
        return this.mMsisdn;
    }

    public String getGid1() {
        return null;
    }

    public void setMsisdnNumber(String alphaTag, String number, Message onComplete) {
        this.mMsisdn = number;
        this.mMsisdnTag = alphaTag;
        if (!SHIP_BUILD) {
            log("Set MSISDN: " + this.mMsisdnTag + " " + this.mMsisdn);
        }
        new AdnRecordLoader(this.mFh).updateEF(new AdnRecord(this.mMsisdnTag, this.mMsisdn), IccConstants.EF_MSISDN, "1".equals(this.mIccType) ? IccConstants.EF_EXT1 : IccConstants.EF_EXT5, 1, null, obtainMessage(30, onComplete));
    }

    public String getMsisdnAlphaTag() {
        return this.mMsisdnTag;
    }

    public String getVoiceMailNumber() {
        return this.mVoiceMailNum;
    }

    public String getServiceProviderName() {
        String providerName = this.mSpn;
        UiccCardApplication parentApp = this.mParentApp;
        if (parentApp != null) {
            UiccCard card = parentApp.getUiccCard();
            if (card != null) {
                String brandOverride = card.getOperatorBrandOverride();
                if (brandOverride != null) {
                    log("getServiceProviderName: override");
                    providerName = brandOverride;
                } else {
                    log("getServiceProviderName: no brandOverride");
                }
            } else {
                log("getServiceProviderName: card is null");
            }
        } else {
            log("getServiceProviderName: mParentApp is null");
        }
        log("getServiceProviderName: providerName=" + providerName);
        return providerName;
    }

    protected void setServiceProviderName(String spn) {
        this.mSpn = spn;
    }

    public String getVoiceMailAlphaTag() {
        return this.mVoiceMailTag;
    }

    public void storeVoiceMailCount(int mwi) {
    }

    public boolean getVoiceMessageWaiting() {
        return this.mCountVoiceMessages != 0;
    }

    public int getVoiceMessageCount() {
        return this.mCountVoiceMessages;
    }

    protected void onIccRefreshInit() {
        this.mAdnCache.reset();
        UiccCardApplication parentApp = this.mParentApp;
        if (parentApp != null && parentApp.getState() == AppState.APPSTATE_READY) {
            sendMessage(obtainMessage(1));
        }
    }

    protected void onSimPhonebookRefresh() {
        this.mAdnCache.reset();
        String intentName = "com.samsung.intent.action.PB_SYNC";
        if (this.phone.getPhoneId() == 1) {
            intentName = "com.samsung.intent.action.PB2_SYNC";
        }
        this.mContext.sendBroadcast(new Intent(intentName));
    }

    public boolean getRecordsLoaded() {
        if (this.mRecordsToLoad == 0 && this.mRecordsRequested) {
            return true;
        }
        return false;
    }

    /* JADX WARNING: inconsistent code. */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    public void handleMessage(android.os.Message r7) {
        /*
        r6 = this;
        r4 = r7.what;
        switch(r4) {
            case 90: goto L_0x006f;
            case 100: goto L_0x0009;
            default: goto L_0x0005;
        };
    L_0x0005:
        super.handleMessage(r7);
    L_0x0008:
        return;
    L_0x0009:
        r0 = r7.obj;	 Catch:{ RuntimeException -> 0x004f }
        r0 = (android.os.AsyncResult) r0;	 Catch:{ RuntimeException -> 0x004f }
        r3 = r0.userObj;	 Catch:{ RuntimeException -> 0x004f }
        r3 = (com.android.internal.telephony.uicc.IccRecords.IccRecordLoaded) r3;	 Catch:{ RuntimeException -> 0x004f }
        r4 = new java.lang.StringBuilder;	 Catch:{ RuntimeException -> 0x004f }
        r4.<init>();	 Catch:{ RuntimeException -> 0x004f }
        r5 = r3.getEfName();	 Catch:{ RuntimeException -> 0x004f }
        r4 = r4.append(r5);	 Catch:{ RuntimeException -> 0x004f }
        r5 = " LOADED";
        r4 = r4.append(r5);	 Catch:{ RuntimeException -> 0x004f }
        r4 = r4.toString();	 Catch:{ RuntimeException -> 0x004f }
        r6.log(r4);	 Catch:{ RuntimeException -> 0x004f }
        r4 = r0.exception;	 Catch:{ RuntimeException -> 0x004f }
        if (r4 == 0) goto L_0x004b;
    L_0x002f:
        r4 = new java.lang.StringBuilder;	 Catch:{ RuntimeException -> 0x004f }
        r4.<init>();	 Catch:{ RuntimeException -> 0x004f }
        r5 = "Record Load Exception: ";
        r4 = r4.append(r5);	 Catch:{ RuntimeException -> 0x004f }
        r5 = r0.exception;	 Catch:{ RuntimeException -> 0x004f }
        r4 = r4.append(r5);	 Catch:{ RuntimeException -> 0x004f }
        r4 = r4.toString();	 Catch:{ RuntimeException -> 0x004f }
        r6.loge(r4);	 Catch:{ RuntimeException -> 0x004f }
    L_0x0047:
        r6.onRecordLoaded();
        goto L_0x0008;
    L_0x004b:
        r3.onRecordLoaded(r0);	 Catch:{ RuntimeException -> 0x004f }
        goto L_0x0047;
    L_0x004f:
        r2 = move-exception;
        r4 = new java.lang.StringBuilder;	 Catch:{ all -> 0x006a }
        r4.<init>();	 Catch:{ all -> 0x006a }
        r5 = "Exception parsing SIM record: ";
        r4 = r4.append(r5);	 Catch:{ all -> 0x006a }
        r4 = r4.append(r2);	 Catch:{ all -> 0x006a }
        r4 = r4.toString();	 Catch:{ all -> 0x006a }
        r6.loge(r4);	 Catch:{ all -> 0x006a }
        r6.onRecordLoaded();
        goto L_0x0008;
    L_0x006a:
        r4 = move-exception;
        r6.onRecordLoaded();
        throw r4;
    L_0x006f:
        r0 = r7.obj;
        r0 = (android.os.AsyncResult) r0;
        r4 = 0;
        r6.auth_rsp = r4;
        r4 = "EVENT_AKA_AUTHENTICATE_DONE";
        r6.log(r4);
        r4 = r0.exception;
        if (r4 == 0) goto L_0x00a5;
    L_0x007f:
        r4 = new java.lang.StringBuilder;
        r4.<init>();
        r5 = "Exception ICC SIM AKA: ";
        r4 = r4.append(r5);
        r5 = r0.exception;
        r4 = r4.append(r5);
        r4 = r4.toString();
        r6.loge(r4);
    L_0x0097:
        r5 = r6.mLock;
        monitor-enter(r5);
        r4 = r6.mLock;	 Catch:{ all -> 0x00a2 }
        r4.notifyAll();	 Catch:{ all -> 0x00a2 }
        monitor-exit(r5);	 Catch:{ all -> 0x00a2 }
        goto L_0x0008;
    L_0x00a2:
        r4 = move-exception;
        monitor-exit(r5);	 Catch:{ all -> 0x00a2 }
        throw r4;
    L_0x00a5:
        r4 = r0.result;	 Catch:{ Exception -> 0x00c4 }
        r4 = (com.android.internal.telephony.uicc.IccIoResult) r4;	 Catch:{ Exception -> 0x00c4 }
        r6.auth_rsp = r4;	 Catch:{ Exception -> 0x00c4 }
        r4 = new java.lang.StringBuilder;	 Catch:{ Exception -> 0x00c4 }
        r4.<init>();	 Catch:{ Exception -> 0x00c4 }
        r5 = "ICC SIM AKA: auth_rsp = ";
        r4 = r4.append(r5);	 Catch:{ Exception -> 0x00c4 }
        r5 = r6.auth_rsp;	 Catch:{ Exception -> 0x00c4 }
        r4 = r4.append(r5);	 Catch:{ Exception -> 0x00c4 }
        r4 = r4.toString();	 Catch:{ Exception -> 0x00c4 }
        r6.log(r4);	 Catch:{ Exception -> 0x00c4 }
        goto L_0x0097;
    L_0x00c4:
        r1 = move-exception;
        r4 = new java.lang.StringBuilder;
        r4.<init>();
        r5 = "Failed to parse ICC SIM AKA contents: ";
        r4 = r4.append(r5);
        r4 = r4.append(r1);
        r4 = r4.toString();
        r6.loge(r4);
        goto L_0x0097;
        */
        throw new UnsupportedOperationException("Method not decompiled: com.android.internal.telephony.uicc.IccRecords.handleMessage(android.os.Message):void");
    }

    public boolean isCspPlmnEnabled() {
        return false;
    }

    public String getOperatorNumeric() {
        return null;
    }

    public boolean getVoiceCallForwardingFlag() {
        return false;
    }

    public void setVoiceCallForwardingFlag(int line, boolean enable, String number) {
    }

    public boolean isProvisioned() {
        return true;
    }

    public IsimRecords getIsimRecords() {
        return null;
    }

    public UsimServiceTable getUsimServiceTable() {
        return null;
    }

    public String getIccSimChallengeResponse(int authContext, String data) {
        log("getIccSimChallengeResponse:");
        try {
            synchronized (this.mLock) {
                CommandsInterface ci = this.mCi;
                UiccCardApplication parentApp = this.mParentApp;
                if (ci == null || parentApp == null) {
                    loge("getIccSimChallengeResponse: Fail, ci or parentApp is null");
                    return null;
                }
                ci.requestIccSimAuthentication(authContext, data, parentApp.getAid(), obtainMessage(EVENT_AKA_AUTHENTICATE_DONE));
                try {
                    this.mLock.wait();
                    log("getIccSimChallengeResponse: return auth_rsp");
                    return Base64.encodeToString(this.auth_rsp.payload, 2);
                } catch (InterruptedException e) {
                    loge("getIccSimChallengeResponse: Fail, interrupted while trying to request Icc Sim Auth");
                    return null;
                }
            }
        } catch (Exception e2) {
            loge("getIccSimChallengeResponse: Fail while trying to request Icc Sim Auth");
            return null;
        }
    }

    protected boolean requirePowerOffOnSimRefreshReset() {
        return this.mContext.getResources().getBoolean(17956971);
    }

    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        int i;
        pw.println("IccRecords: " + this);
        pw.println(" mDestroyed=" + this.mDestroyed);
        pw.println(" mCi=" + this.mCi);
        pw.println(" mFh=" + this.mFh);
        pw.println(" mParentApp=" + this.mParentApp);
        pw.println(" recordsLoadedRegistrants: size=" + this.mRecordsLoadedRegistrants.size());
        for (i = 0; i < this.mRecordsLoadedRegistrants.size(); i++) {
            pw.println("  recordsLoadedRegistrants[" + i + "]=" + ((Registrant) this.mRecordsLoadedRegistrants.get(i)).getHandler());
        }
        pw.println(" mImsiReadyRegistrants: size=" + this.mImsiReadyRegistrants.size());
        for (i = 0; i < this.mImsiReadyRegistrants.size(); i++) {
            pw.println("  mImsiReadyRegistrants[" + i + "]=" + ((Registrant) this.mImsiReadyRegistrants.get(i)).getHandler());
        }
        pw.println(" mRecordsEventsRegistrants: size=" + this.mRecordsEventsRegistrants.size());
        for (i = 0; i < this.mRecordsEventsRegistrants.size(); i++) {
            pw.println("  mRecordsEventsRegistrants[" + i + "]=" + ((Registrant) this.mRecordsEventsRegistrants.get(i)).getHandler());
        }
        pw.println(" mNewSmsRegistrants: size=" + this.mNewSmsRegistrants.size());
        for (i = 0; i < this.mNewSmsRegistrants.size(); i++) {
            pw.println("  mNewSmsRegistrants[" + i + "]=" + ((Registrant) this.mNewSmsRegistrants.get(i)).getHandler());
        }
        pw.println(" mNetworkSelectionModeAutomaticRegistrants: size=" + this.mNetworkSelectionModeAutomaticRegistrants.size());
        for (i = 0; i < this.mNetworkSelectionModeAutomaticRegistrants.size(); i++) {
            pw.println("  mNetworkSelectionModeAutomaticRegistrants[" + i + "]=" + ((Registrant) this.mNetworkSelectionModeAutomaticRegistrants.get(i)).getHandler());
        }
        pw.println(" mRecordsRequested=" + this.mRecordsRequested);
        pw.println(" mRecordsToLoad=" + this.mRecordsToLoad);
        pw.println(" mRdnCache=" + this.mAdnCache);
        pw.println(" mVoiceMailNum=" + this.mVoiceMailNum);
        pw.println(" mVoiceMailTag=" + this.mVoiceMailTag);
        pw.println(" mNewVoiceMailNum=" + this.mNewVoiceMailNum);
        pw.println(" mNewVoiceMailTag=" + this.mNewVoiceMailTag);
        pw.println(" mIsVoiceMailFixed=" + this.mIsVoiceMailFixed);
        pw.println(" mCountVoiceMessages=" + this.mCountVoiceMessages);
        pw.println(" mMncLength=" + this.mMncLength);
        pw.println(" mMailboxIndex=" + this.mMailboxIndex);
        pw.println(" mSpn=" + this.mSpn);
        pw.println(" mIsPNNExist=" + this.mIsPNNExist);
        pw.println(" mIsEnabledPNN=" + this.mIsEnabledPNN);
        if (!SHIP_BUILD) {
            pw.println(" iccid=" + this.mIccId);
            pw.println(" mMsisdn=" + this.mMsisdn);
            pw.println(" mMsisdnTag=" + this.mMsisdnTag);
            pw.println(" mImsi=" + this.mImsi);
        }
        pw.flush();
    }

    public boolean hasIsim() {
        return false;
    }

    public byte[] getPsismsc() {
        return this.mEfPsismsc;
    }

    public boolean isCallForwardStatusStored() {
        return false;
    }

    public void setSpnDynamic(String currentPlmn) {
    }

    public String[] getFakeHomeOn() {
        return null;
    }

    public String[] getFakeRoamingOn() {
        return null;
    }

    public void IncreaseSMSS() {
        log("IncreaseSMSS call in IccRecords");
    }

    public void getSMSS() {
        log("getSMSS call in IccRecords");
    }

    public boolean chekcMWISavailable() {
        return false;
    }

    public boolean getSdnAvailable() {
        return this.mIsAvailableSDN;
    }

    public String getSktIMSIM() {
        return null;
    }

    public String getSktIRM() {
        return null;
    }

    public boolean getVideoCallForwardingFlag() {
        return false;
    }

    public void setCallForwardingFlag(int line, Boolean voiceEnable, Boolean videoEnable) {
    }

    public void setCallForwardingFlag(int line, Boolean voiceEnable, Boolean videoEnable, String dialingNumber) {
    }

    public void setVoiceCallForwardingFlag(int line, boolean enable) {
    }

    public void setVideoCallForwardingFlag(int line, boolean enable) {
    }

    public void setVideoCallForwardingFlag(int line, boolean enable, String dialingNumber) {
    }

    protected String getSystemProperty(String key, String defValue) {
        return TelephonyManager.getTelephonyProperty(key, SubscriptionController.getInstance().getSubId(this.phone.getPhoneId())[0], defValue);
    }

    protected void setSystemProperty(String key, String val) {
        TelephonyManager.setTelephonyProperty(key, SubscriptionController.getInstance().getSubId(this.phone.getPhoneId())[0], val);
    }
}
