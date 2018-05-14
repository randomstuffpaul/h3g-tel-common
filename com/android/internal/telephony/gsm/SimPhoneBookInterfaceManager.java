package com.android.internal.telephony.gsm;

import android.os.Message;
import android.telephony.Rlog;
import com.android.internal.telephony.IccPhoneBookInterfaceManager;
import com.android.internal.telephony.uicc.IccFileHandler;
import com.android.internal.telephony.uicc.UsimPhonebookCapaInfo;
import java.util.concurrent.atomic.AtomicBoolean;

public class SimPhoneBookInterfaceManager extends IccPhoneBookInterfaceManager {
    static final String LOG_TAG = "SimPhoneBookIM";

    public SimPhoneBookInterfaceManager(GSMPhone phone) {
        super(phone);
    }

    public void dispose() {
        super.dispose();
    }

    protected void finalize() {
        try {
            super.finalize();
        } catch (Throwable throwable) {
            Rlog.e(LOG_TAG, "Error while finalizing:", throwable);
        }
        if (DBG) {
            Rlog.d(LOG_TAG, "SimPhoneBookInterfaceManager finalized");
        }
    }

    public int[] getAdnRecordsSize(int efid) {
        if (DBG) {
            logd("getAdnRecordsSize: efid=" + efid);
        }
        synchronized (this.mLock) {
            checkThread();
            this.mRecordSize = new int[3];
            AtomicBoolean status = new AtomicBoolean(false);
            Message response = this.mBaseHandler.obtainMessage(1, status);
            IccFileHandler fh = this.mPhone.getIccFileHandler();
            if (fh != null) {
                fh.getEFLinearRecordSize(efid, response);
                waitForResult(status);
            }
        }
        return this.mRecordSize;
    }

    public int[] getAdnLikesInfo(int efid) {
        if (DBG) {
            logd("getAdnLikesInfo: efid=" + efid);
        }
        synchronized (this.mLock) {
            checkThread();
            this.recordInfo = new int[5];
            for (int i = 0; i < 5; i++) {
                this.recordInfo[i] = -1;
            }
            AtomicBoolean status = new AtomicBoolean(false);
            Message response = this.mBaseHandler.obtainMessage(5, status);
            IccFileHandler fh = this.mPhone.getIccFileHandler();
            if (fh != null) {
                fh.getAdnLikesRecordInfo(efid, response);
                waitForResult(status);
            }
        }
        return this.recordInfo;
    }

    public UsimPhonebookCapaInfo getUsimPBCapaInfo() {
        if (DBG) {
            logd("getUsimPBCapaInfo");
        }
        synchronized (this.mLock) {
            checkThread();
            AtomicBoolean status = new AtomicBoolean(false);
            Message response = this.mBaseHandler.obtainMessage(4, status);
            if (this.mPhone == null || this.mPhone.getIccFileHandler() == null) {
                return null;
            }
            this.mPhone.getIccFileHandler().getUsimPBCapa(response);
            waitForResult(status);
            return this.mUsimPhonebookCapaInfo;
        }
    }

    /* JADX WARNING: inconsistent code. */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    public int getAdnLikesSimStatusInfo(int r6) {
        /*
        r5 = this;
        r2 = DBG;
        if (r2 == 0) goto L_0x0009;
    L_0x0004:
        r2 = "getAdnLikesSimStatusInfo";
        r5.logd(r2);
    L_0x0009:
        r3 = r5.mLock;
        monitor-enter(r3);
        r5.checkThread();	 Catch:{ all -> 0x0057 }
        r1 = new java.util.concurrent.atomic.AtomicBoolean;	 Catch:{ all -> 0x0057 }
        r2 = 0;
        r1.<init>(r2);	 Catch:{ all -> 0x0057 }
        r2 = r5.mBaseHandler;	 Catch:{ all -> 0x0057 }
        r4 = 6;
        r0 = r2.obtainMessage(r4, r1);	 Catch:{ all -> 0x0057 }
        r2 = r5.mPhone;	 Catch:{ all -> 0x0057 }
        if (r2 == 0) goto L_0x0028;
    L_0x0020:
        r2 = r5.mPhone;	 Catch:{ all -> 0x0057 }
        r2 = r2.getIccFileHandler();	 Catch:{ all -> 0x0057 }
        if (r2 != 0) goto L_0x002b;
    L_0x0028:
        r2 = -1;
        monitor-exit(r3);	 Catch:{ all -> 0x0057 }
    L_0x002a:
        return r2;
    L_0x002b:
        r2 = r5.mPhone;	 Catch:{ all -> 0x0057 }
        r2 = r2.getIccFileHandler();	 Catch:{ all -> 0x0057 }
        r2.getAdnLikesSimStatusInfo(r6, r0);	 Catch:{ all -> 0x0057 }
        r5.waitForResult(r1);	 Catch:{ all -> 0x0057 }
        monitor-exit(r3);	 Catch:{ all -> 0x0057 }
        r2 = DBG;
        if (r2 == 0) goto L_0x0054;
    L_0x003c:
        r2 = new java.lang.StringBuilder;
        r2.<init>();
        r3 = "getAdnLikesSimStatusInfo result : ";
        r2 = r2.append(r3);
        r3 = r5.mSimFileStatusInfo;
        r2 = r2.append(r3);
        r2 = r2.toString();
        r5.logd(r2);
    L_0x0054:
        r2 = r5.mSimFileStatusInfo;
        goto L_0x002a;
    L_0x0057:
        r2 = move-exception;
        monitor-exit(r3);	 Catch:{ all -> 0x0057 }
        throw r2;
        */
        throw new UnsupportedOperationException("Method not decompiled: com.android.internal.telephony.gsm.SimPhoneBookInterfaceManager.getAdnLikesSimStatusInfo(int):int");
    }

    protected void logd(String msg) {
        Rlog.d(LOG_TAG, "[SimPbInterfaceManager] " + msg);
    }

    protected void loge(String msg) {
        Rlog.e(LOG_TAG, "[SimPbInterfaceManager] " + msg);
    }
}
