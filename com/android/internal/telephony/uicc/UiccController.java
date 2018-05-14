package com.android.internal.telephony.uicc;

import android.content.Context;
import android.os.AsyncResult;
import android.os.Handler;
import android.os.Message;
import android.os.Registrant;
import android.os.RegistrantList;
import android.telephony.Rlog;
import android.telephony.TelephonyManager;
import com.android.internal.telephony.CommandsInterface;
import com.android.internal.telephony.PhoneBase;
import com.android.internal.telephony.SubscriptionController;
import java.io.FileDescriptor;
import java.io.PrintWriter;

public class UiccController extends Handler {
    public static final int APP_FAM_3GPP = 1;
    public static final int APP_FAM_3GPP2 = 2;
    public static final int APP_FAM_IMS = 3;
    private static final boolean DBG = true;
    private static final int EVENT_GET_ICC_STATUS_DONE = 2;
    private static final int EVENT_ICC_STATUS_CHANGED = 1;
    private static final int EVENT_RADIO_UNAVAILABLE = 3;
    private static final int EVENT_SIM_REFRESH = 4;
    private static final String LOG_TAG = "UiccController";
    private static UiccController mInstance;
    private static final Object mLock = new Object();
    private CommandsInterface[] mCis;
    private Context mContext;
    protected RegistrantList mIccChangedRegistrants = new RegistrantList();
    private PhoneBase mPhone = null;
    private PhoneBase[] mPhones = new PhoneBase[TelephonyManager.getDefault().getPhoneCount()];
    private UiccCard[] mUiccCards = new UiccCard[TelephonyManager.getDefault().getPhoneCount()];

    public static UiccController make(Context c, CommandsInterface[] ci) {
        UiccController uiccController;
        synchronized (mLock) {
            if (mInstance != null) {
                throw new RuntimeException("MSimUiccController.make() should only be called once");
            }
            mInstance = new UiccController(c, ci);
            uiccController = mInstance;
        }
        return uiccController;
    }

    private UiccController(Context c, CommandsInterface[] ci) {
        log("Creating UiccController");
        this.mContext = c;
        this.mCis = ci;
        for (int i = 0; i < this.mCis.length; i++) {
            Integer index = new Integer(i);
            this.mCis[i].registerForIccStatusChanged(this, 1, index);
            this.mCis[i].registerForOn(this, 1, index);
            this.mCis[i].registerForNotAvailable(this, 3, index);
            this.mCis[i].registerForIccRefresh(this, 4, null);
        }
    }

    public static UiccController getInstance() {
        UiccController uiccController;
        synchronized (mLock) {
            if (mInstance == null) {
                throw new RuntimeException("UiccController.getInstance can't be called before make()");
            }
            uiccController = mInstance;
        }
        return uiccController;
    }

    public static UiccController getInstance(PhoneBase phone) {
        UiccController uiccController;
        synchronized (mLock) {
            if (mInstance == null) {
                throw new RuntimeException("UiccController.getInstance can't be called before make()");
            }
            mInstance.setCurrentPhone(phone);
            uiccController = mInstance;
        }
        return uiccController;
    }

    public void setCurrentPhone(PhoneBase phone) {
        this.mPhones[phone.getPhoneId()] = phone;
    }

    public UiccCard getUiccCard() {
        return getUiccCard(SubscriptionController.getInstance().getPhoneId(SubscriptionController.getInstance().getDefaultSubId()));
    }

    public UiccCard getUiccCard(int slotId) {
        UiccCard uiccCard;
        synchronized (mLock) {
            if (isValidCardIndex(slotId)) {
                uiccCard = this.mUiccCards[slotId];
            } else {
                uiccCard = null;
            }
        }
        return uiccCard;
    }

    public UiccCard[] getUiccCards() {
        UiccCard[] uiccCardArr;
        synchronized (mLock) {
            uiccCardArr = (UiccCard[]) this.mUiccCards.clone();
        }
        return uiccCardArr;
    }

    public UiccCardApplication getUiccCardApplication(int family) {
        return getUiccCardApplication(SubscriptionController.getInstance().getPhoneId(SubscriptionController.getInstance().getDefaultSubId()), family);
    }

    public IccRecords getIccRecords(int slotId, int family) {
        IccRecords iccRecords;
        synchronized (mLock) {
            UiccCardApplication app = getUiccCardApplication(slotId, family);
            if (app != null) {
                iccRecords = app.getIccRecords();
            } else {
                iccRecords = null;
            }
        }
        return iccRecords;
    }

    public IccFileHandler getIccFileHandler(int slotId, int family) {
        IccFileHandler iccFileHandler;
        synchronized (mLock) {
            UiccCardApplication app = getUiccCardApplication(slotId, family);
            if (app != null) {
                iccFileHandler = app.getIccFileHandler();
            } else {
                iccFileHandler = null;
            }
        }
        return iccFileHandler;
    }

    public void registerForIccChanged(Handler h, int what, Object obj) {
        synchronized (mLock) {
            Registrant r = new Registrant(h, what, obj);
            this.mIccChangedRegistrants.add(r);
            r.notifyRegistrant();
        }
    }

    public void unregisterForIccChanged(Handler h) {
        synchronized (mLock) {
            this.mIccChangedRegistrants.remove(h);
        }
    }

    /* JADX WARNING: inconsistent code. */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    public void handleMessage(android.os.Message r9) {
        /*
        r8 = this;
        r4 = mLock;
        monitor-enter(r4);
        r2 = r8.getCiIndex(r9);	 Catch:{ all -> 0x005d }
        r3 = r2.intValue();	 Catch:{ all -> 0x005d }
        if (r3 < 0) goto L_0x0016;
    L_0x000d:
        r3 = r2.intValue();	 Catch:{ all -> 0x005d }
        r5 = r8.mCis;	 Catch:{ all -> 0x005d }
        r5 = r5.length;	 Catch:{ all -> 0x005d }
        if (r3 < r5) goto L_0x003c;
    L_0x0016:
        r3 = "UiccController";
        r5 = new java.lang.StringBuilder;	 Catch:{ all -> 0x005d }
        r5.<init>();	 Catch:{ all -> 0x005d }
        r6 = "Invalid index : ";
        r5 = r5.append(r6);	 Catch:{ all -> 0x005d }
        r5 = r5.append(r2);	 Catch:{ all -> 0x005d }
        r6 = " received with event ";
        r5 = r5.append(r6);	 Catch:{ all -> 0x005d }
        r6 = r9.what;	 Catch:{ all -> 0x005d }
        r5 = r5.append(r6);	 Catch:{ all -> 0x005d }
        r5 = r5.toString();	 Catch:{ all -> 0x005d }
        android.telephony.Rlog.e(r3, r5);	 Catch:{ all -> 0x005d }
        monitor-exit(r4);	 Catch:{ all -> 0x005d }
    L_0x003b:
        return;
    L_0x003c:
        r3 = r9.what;	 Catch:{ all -> 0x005d }
        switch(r3) {
            case 1: goto L_0x0060;
            case 2: goto L_0x0076;
            case 3: goto L_0x0083;
            case 4: goto L_0x00b3;
            default: goto L_0x0041;
        };	 Catch:{ all -> 0x005d }
    L_0x0041:
        r3 = "UiccController";
        r5 = new java.lang.StringBuilder;	 Catch:{ all -> 0x005d }
        r5.<init>();	 Catch:{ all -> 0x005d }
        r6 = " Unknown Event ";
        r5 = r5.append(r6);	 Catch:{ all -> 0x005d }
        r6 = r9.what;	 Catch:{ all -> 0x005d }
        r5 = r5.append(r6);	 Catch:{ all -> 0x005d }
        r5 = r5.toString();	 Catch:{ all -> 0x005d }
        android.telephony.Rlog.e(r3, r5);	 Catch:{ all -> 0x005d }
    L_0x005b:
        monitor-exit(r4);	 Catch:{ all -> 0x005d }
        goto L_0x003b;
    L_0x005d:
        r3 = move-exception;
        monitor-exit(r4);	 Catch:{ all -> 0x005d }
        throw r3;
    L_0x0060:
        r3 = "Received EVENT_ICC_STATUS_CHANGED, calling getIccCardStatus";
        r8.log(r3);	 Catch:{ all -> 0x005d }
        r3 = r8.mCis;	 Catch:{ all -> 0x005d }
        r5 = r2.intValue();	 Catch:{ all -> 0x005d }
        r3 = r3[r5];	 Catch:{ all -> 0x005d }
        r5 = 2;
        r5 = r8.obtainMessage(r5, r2);	 Catch:{ all -> 0x005d }
        r3.getIccCardStatus(r5);	 Catch:{ all -> 0x005d }
        goto L_0x005b;
    L_0x0076:
        r3 = "Received EVENT_GET_ICC_STATUS_DONE";
        r8.log(r3);	 Catch:{ all -> 0x005d }
        r0 = r9.obj;	 Catch:{ all -> 0x005d }
        r0 = (android.os.AsyncResult) r0;	 Catch:{ all -> 0x005d }
        r8.onGetIccCardStatusDone(r0, r2);	 Catch:{ all -> 0x005d }
        goto L_0x005b;
    L_0x0083:
        r3 = "EVENT_RADIO_UNAVAILABLE, dispose card";
        r8.log(r3);	 Catch:{ all -> 0x005d }
        r3 = r8.mUiccCards;	 Catch:{ all -> 0x005d }
        r5 = r2.intValue();	 Catch:{ all -> 0x005d }
        r3 = r3[r5];	 Catch:{ all -> 0x005d }
        if (r3 == 0) goto L_0x009d;
    L_0x0092:
        r3 = r8.mUiccCards;	 Catch:{ all -> 0x005d }
        r5 = r2.intValue();	 Catch:{ all -> 0x005d }
        r3 = r3[r5];	 Catch:{ all -> 0x005d }
        r3.dispose();	 Catch:{ all -> 0x005d }
    L_0x009d:
        r3 = r8.mUiccCards;	 Catch:{ all -> 0x005d }
        r5 = r2.intValue();	 Catch:{ all -> 0x005d }
        r6 = 0;
        r3[r5] = r6;	 Catch:{ all -> 0x005d }
        r3 = r8.mIccChangedRegistrants;	 Catch:{ all -> 0x005d }
        r5 = new android.os.AsyncResult;	 Catch:{ all -> 0x005d }
        r6 = 0;
        r7 = 0;
        r5.<init>(r6, r2, r7);	 Catch:{ all -> 0x005d }
        r3.notifyRegistrants(r5);	 Catch:{ all -> 0x005d }
        goto L_0x005b;
    L_0x00b3:
        r3 = "Received EVENT_SIM_REFRESH";
        r8.log(r3);	 Catch:{ all -> 0x005d }
        r1 = 0;
    L_0x00b9:
        r3 = r8.mUiccCards;	 Catch:{ all -> 0x005d }
        r5 = r2.intValue();	 Catch:{ all -> 0x005d }
        r3 = r3[r5];	 Catch:{ all -> 0x005d }
        r3 = r3.getNumApplications();	 Catch:{ all -> 0x005d }
        if (r1 >= r3) goto L_0x005b;
    L_0x00c7:
        r3 = r8.mUiccCards;	 Catch:{ all -> 0x005d }
        r5 = r2.intValue();	 Catch:{ all -> 0x005d }
        r3 = r3[r5];	 Catch:{ all -> 0x005d }
        r3 = r3.getApplicationIndex(r1);	 Catch:{ all -> 0x005d }
        r5 = 1;
        r3.mSimRefreshState = r5;	 Catch:{ all -> 0x005d }
        r1 = r1 + 1;
        goto L_0x00b9;
        */
        throw new UnsupportedOperationException("Method not decompiled: com.android.internal.telephony.uicc.UiccController.handleMessage(android.os.Message):void");
    }

    private Integer getCiIndex(Message msg) {
        Integer index = new Integer(0);
        if (msg == null) {
            return index;
        }
        if (msg.obj != null && (msg.obj instanceof Integer)) {
            return msg.obj;
        }
        if (msg.obj == null || !(msg.obj instanceof AsyncResult)) {
            return index;
        }
        AsyncResult ar = msg.obj;
        if (ar.userObj == null || !(ar.userObj instanceof Integer)) {
            return index;
        }
        return ar.userObj;
    }

    public UiccCardApplication getUiccCardApplication(int slotId, int family) {
        UiccCardApplication uiccCardApplication;
        synchronized (mLock) {
            if (!isValidCardIndex(slotId) || this.mUiccCards[slotId] == null) {
                uiccCardApplication = null;
            } else {
                uiccCardApplication = this.mUiccCards[slotId].getApplication(family);
            }
        }
        return uiccCardApplication;
    }

    private synchronized void onGetIccCardStatusDone(AsyncResult ar, Integer index) {
        if (ar.exception != null) {
            Rlog.e(LOG_TAG, "Error getting ICC status. RIL_REQUEST_GET_ICC_STATUS should never return an error", ar.exception);
        } else if (isValidCardIndex(index.intValue())) {
            IccCardStatus status = ar.result;
            if (this.mUiccCards[index.intValue()] == null) {
                this.mUiccCards[index.intValue()] = new UiccCard(this.mContext, this.mCis[index.intValue()], status, this.mPhones[index.intValue()], index.intValue());
            } else {
                this.mUiccCards[index.intValue()].update(this.mContext, this.mCis[index.intValue()], status, this.mPhones[index.intValue()]);
            }
            log("Notifying IccChangedRegistrants");
            this.mIccChangedRegistrants.notifyRegistrants(new AsyncResult(null, index, null));
        } else {
            Rlog.e(LOG_TAG, "onGetIccCardStatusDone: invalid index : " + index);
        }
    }

    private boolean isValidCardIndex(int index) {
        return index >= 0 && index < this.mUiccCards.length;
    }

    private void log(String string) {
        Rlog.d(LOG_TAG, string);
    }

    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        int i;
        pw.println("UiccController: " + this);
        pw.println(" mContext=" + this.mContext);
        pw.println(" mInstance=" + mInstance);
        for (i = 0; i < this.mCis.length; i++) {
            pw.println(" mCis[" + i + "]=" + this.mCis[i]);
        }
        for (i = 0; i < this.mUiccCards.length; i++) {
            pw.println(" mUiccCards[" + i + "]=" + this.mUiccCards[i]);
        }
        pw.println(" mIccChangedRegistrants: size=" + this.mIccChangedRegistrants.size());
        for (i = 0; i < this.mIccChangedRegistrants.size(); i++) {
            pw.println("  mIccChangedRegistrants[" + i + "]=" + ((Registrant) this.mIccChangedRegistrants.get(i)).getHandler());
        }
        pw.println();
        pw.flush();
        for (UiccCard dump : this.mUiccCards) {
            dump.dump(fd, pw, args);
        }
    }
}
