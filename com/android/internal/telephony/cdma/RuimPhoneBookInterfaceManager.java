package com.android.internal.telephony.cdma;

import android.os.Message;
import android.telephony.Rlog;
import com.android.internal.telephony.IccPhoneBookInterfaceManager;
import com.android.internal.telephony.uicc.IccFileHandler;
import com.android.internal.telephony.uicc.UsimPhonebookCapaInfo;
import java.util.concurrent.atomic.AtomicBoolean;

public class RuimPhoneBookInterfaceManager extends IccPhoneBookInterfaceManager {
    static final String LOG_TAG = "RuimPhoneBookIM";

    public RuimPhoneBookInterfaceManager(CDMAPhone phone) {
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
            Rlog.d(LOG_TAG, "RuimPhoneBookInterfaceManager finalized");
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

    protected void logd(String msg) {
        Rlog.d(LOG_TAG, "[RuimPbInterfaceManager] " + msg);
    }

    protected void loge(String msg) {
        Rlog.e(LOG_TAG, "[RuimPbInterfaceManager] " + msg);
    }

    public int[] getAdnLikesInfo(int efid) {
        if (DBG) {
            logd("getAdnLikesInfo: efid=" + efid);
        }
        synchronized (this.mLock) {
            checkThread();
            this.recordInfo = new int[5];
            AtomicBoolean status = new AtomicBoolean(false);
            this.mPhone.getIccFileHandler().getAdnLikesRecordInfo(efid, this.mBaseHandler.obtainMessage(5, status));
            waitForResult(status);
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
    public int getAdnLikesSimStatusInfo(int r7) {
        /*
        r6 = this;
        r5 = 28474; // 0x6f3a float:3.99E-41 double:1.4068E-319;
        r2 = DBG;
        if (r2 == 0) goto L_0x000b;
    L_0x0006:
        r2 = "getAdnLikesSimStatusInfo";
        r6.logd(r2);
    L_0x000b:
        r3 = r6.mLock;
        monitor-enter(r3);
        r6.checkThread();	 Catch:{ all -> 0x0068 }
        r1 = new java.util.concurrent.atomic.AtomicBoolean;	 Catch:{ all -> 0x0068 }
        r2 = 0;
        r1.<init>(r2);	 Catch:{ all -> 0x0068 }
        r2 = r6.mBaseHandler;	 Catch:{ all -> 0x0068 }
        r4 = 6;
        r0 = r2.obtainMessage(r4, r1);	 Catch:{ all -> 0x0068 }
        r2 = r6.mPhone;	 Catch:{ all -> 0x0068 }
        if (r2 == 0) goto L_0x002a;
    L_0x0022:
        r2 = r6.mPhone;	 Catch:{ all -> 0x0068 }
        r2 = r2.getIccFileHandler();	 Catch:{ all -> 0x0068 }
        if (r2 != 0) goto L_0x0032;
    L_0x002a:
        r2 = -1;
        if (r7 != r5) goto L_0x0030;
    L_0x002d:
        r4 = 0;
        android.telephony.TelephonyManager.isSelecttelecomDF = r4;	 Catch:{ all -> 0x006f }
    L_0x0030:
        monitor-exit(r3);	 Catch:{ all -> 0x006f }
    L_0x0031:
        return r2;
    L_0x0032:
        if (r7 != r5) goto L_0x0037;
    L_0x0034:
        r2 = 1;
        android.telephony.TelephonyManager.isSelecttelecomDF = r2;	 Catch:{ all -> 0x0068 }
    L_0x0037:
        r2 = r6.mPhone;	 Catch:{ all -> 0x0068 }
        r2 = r2.getIccFileHandler();	 Catch:{ all -> 0x0068 }
        r2.getAdnLikesSimStatusInfo(r7, r0);	 Catch:{ all -> 0x0068 }
        r6.waitForResult(r1);	 Catch:{ all -> 0x0068 }
        if (r7 != r5) goto L_0x0048;
    L_0x0045:
        r2 = 0;
        android.telephony.TelephonyManager.isSelecttelecomDF = r2;	 Catch:{ all -> 0x006f }
    L_0x0048:
        monitor-exit(r3);	 Catch:{ all -> 0x006f }
        r2 = DBG;
        if (r2 == 0) goto L_0x0065;
    L_0x004d:
        r2 = new java.lang.StringBuilder;
        r2.<init>();
        r3 = "getAdnLikesSimStatusInfo result : ";
        r2 = r2.append(r3);
        r3 = r6.mSimFileStatusInfo;
        r2 = r2.append(r3);
        r2 = r2.toString();
        r6.logd(r2);
    L_0x0065:
        r2 = r6.mSimFileStatusInfo;
        goto L_0x0031;
    L_0x0068:
        r2 = move-exception;
        if (r7 != r5) goto L_0x006e;
    L_0x006b:
        r4 = 0;
        android.telephony.TelephonyManager.isSelecttelecomDF = r4;	 Catch:{ all -> 0x006f }
    L_0x006e:
        throw r2;	 Catch:{ all -> 0x006f }
    L_0x006f:
        r2 = move-exception;
        monitor-exit(r3);	 Catch:{ all -> 0x006f }
        throw r2;
        */
        throw new UnsupportedOperationException("Method not decompiled: com.android.internal.telephony.cdma.RuimPhoneBookInterfaceManager.getAdnLikesSimStatusInfo(int):int");
    }
}
