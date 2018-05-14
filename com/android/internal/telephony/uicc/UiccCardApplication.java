package com.android.internal.telephony.uicc;

import android.content.Context;
import android.os.AsyncResult;
import android.os.Handler;
import android.os.Message;
import android.os.Registrant;
import android.os.RegistrantList;
import android.telephony.Rlog;
import com.android.internal.telephony.CommandsInterface;
import com.android.internal.telephony.PhoneBase;
import com.android.internal.telephony.uicc.IccCardApplicationStatus.AppState;
import com.android.internal.telephony.uicc.IccCardApplicationStatus.AppType;
import com.android.internal.telephony.uicc.IccCardApplicationStatus.PersoSubState;
import com.android.internal.telephony.uicc.IccCardStatus.PinState;
import com.samsung.android.telephony.MultiSimManager;
import java.io.FileDescriptor;
import java.io.PrintWriter;

public class UiccCardApplication {
    public static final int AUTH_CONTEXT_EAP_AKA = 129;
    public static final int AUTH_CONTEXT_EAP_SIM = 128;
    public static final int AUTH_CONTEXT_UNDEFINED = -1;
    private static final boolean DBG = true;
    private static final int EVENT_CHANGE_FACILITY_FDN_DONE = 5;
    private static final int EVENT_CHANGE_FACILITY_LOCK_DONE = 7;
    private static final int EVENT_CHANGE_PIN1_DONE = 2;
    private static final int EVENT_CHANGE_PIN2_DONE = 3;
    private static final int EVENT_PIN1_DONE = 9;
    private static final int EVENT_PIN1_PUK1_DONE = 1;
    private static final int EVENT_PIN2_DONE = 10;
    private static final int EVENT_PIN2_PUK2_DONE = 8;
    private static final int EVENT_PUK1_DONE = 11;
    private static final int EVENT_PUK2_DONE = 12;
    private static final int EVENT_QUERY_FACILITY_FDN_DONE = 4;
    private static final int EVENT_QUERY_FACILITY_LOCK_DONE = 6;
    private static final int EVENT_WAIT_UPDATE_DONE = 13;
    private static final String LOG_TAG = "UiccCardApplication";
    private static final int PIN1 = 1;
    private static final int PIN2 = 3;
    private static final int PUK1 = 2;
    private static final int PUK2 = 4;
    private String mAid;
    private String mAppLabel;
    private AppState mAppState;
    private AppType mAppType;
    private int mAuthContext;
    private CommandsInterface mCi;
    private Context mContext;
    protected boolean mDbg = true;
    private boolean mDesiredFdnEnabled;
    private boolean mDesiredPinLocked;
    private boolean mDestroyed = false;
    public boolean mFdnState = false;
    private RegistrantList mGetLockInfoRegistrants = new RegistrantList();
    private Handler mHandler = new C01351();
    private boolean mIccFdnAvailable = true;
    private boolean mIccFdnEnabled = false;
    private IccFileHandler mIccFh;
    private boolean mIccLockEnabled;
    private IccRecords mIccRecords;
    private int mIccStateUpdated = 0;
    private final Object mLock = new Object();
    private RegistrantList mNetworkLockedRegistrants = new RegistrantList();
    private RegistrantList mNetworkSubsetLockedRegistrants = new RegistrantList();
    private PersoSubState mPersoSubState;
    protected PhoneBase mPhone;
    private boolean mPin1Replaced;
    private int mPin1RetryCount = -1;
    private PinState mPin1State;
    private int mPin2RetryCount = -1;
    private PinState mPin2State;
    private RegistrantList mPinLockedRegistrants = new RegistrantList();
    private int mPuk1RetryCount = -1;
    private int mPuk2RetryCount = -1;
    private RegistrantList mReadyRegistrants = new RegistrantList();
    private RegistrantList mSPLockedRegistrants = new RegistrantList();
    public boolean mSimPinState = false;
    public boolean mSimRefreshState = false;
    private UiccCard mUiccCard;
    private RegistrantList mUpdateSimLockInfoRegistrants = new RegistrantList();
    private int mperso_unblock_retries = -1;

    class C01351 extends Handler {
        C01351() {
        }

        private void sendResultToTarget(Message m, Throwable e) {
            AsyncResult.forMessage(m).exception = e;
            m.sendToTarget();
        }

        public void handleMessage(Message msg) {
            int attemptsRemaining = -1;
            if (UiccCardApplication.this.mDestroyed) {
                UiccCardApplication.this.loge("Received message " + msg + "[" + msg.what + "] while being destroyed. Ignoring.");
                return;
            }
            AsyncResult ar;
            Message response;
            switch (msg.what) {
                case 1:
                case 8:
                    ar = msg.obj;
                    if (!(ar.exception == null || ar.result == null)) {
                        attemptsRemaining = UiccCardApplication.this.parsePinPukErrorResult(ar);
                    }
                    response = ar.userObj;
                    AsyncResult.forMessage(response).exception = ar.exception;
                    response.arg1 = attemptsRemaining;
                    response.sendToTarget();
                    break;
                case 2:
                    ar = (AsyncResult) msg.obj;
                    if (ar.result != null) {
                        attemptsRemaining = UiccCardApplication.this.parsePinPukErrorResult(ar, 1);
                    }
                    response = ar.userObj;
                    AsyncResult.forMessage(response).exception = ar.exception;
                    response.arg1 = attemptsRemaining;
                    response.sendToTarget();
                    break;
                case 3:
                    ar = (AsyncResult) msg.obj;
                    if (ar.result != null) {
                        attemptsRemaining = UiccCardApplication.this.parsePinPukErrorResult(ar, 3);
                    }
                    response = ar.userObj;
                    AsyncResult.forMessage(response).exception = ar.exception;
                    response.arg1 = attemptsRemaining;
                    response.sendToTarget();
                    break;
                case 4:
                    UiccCardApplication.this.onQueryFdnEnabled((AsyncResult) msg.obj);
                    break;
                case 5:
                    UiccCardApplication.this.onChangeFdnDone((AsyncResult) msg.obj);
                    break;
                case 6:
                    UiccCardApplication.this.onQueryFacilityLock((AsyncResult) msg.obj);
                    break;
                case 7:
                    UiccCardApplication.this.onChangeFacilityLock((AsyncResult) msg.obj);
                    break;
                case 9:
                    ar = (AsyncResult) msg.obj;
                    if (ar.result != null) {
                        attemptsRemaining = UiccCardApplication.this.parsePinPukErrorResult(ar, 1);
                    }
                    response = ar.userObj;
                    AsyncResult.forMessage(response).exception = ar.exception;
                    response.arg1 = attemptsRemaining;
                    response.sendToTarget();
                    break;
                case 10:
                    ar = (AsyncResult) msg.obj;
                    if (ar.result != null) {
                        UiccCardApplication.this.parsePinPukErrorResult(ar, 3);
                    }
                    sendResultToTarget((Message) ar.userObj, ar.exception);
                    break;
                case 11:
                    ar = (AsyncResult) msg.obj;
                    if (ar.result != null) {
                        attemptsRemaining = UiccCardApplication.this.parsePinPukErrorResult(ar, 2);
                    }
                    response = ar.userObj;
                    AsyncResult.forMessage(response).exception = ar.exception;
                    response.arg1 = attemptsRemaining;
                    response.sendToTarget();
                    break;
                case 12:
                    ar = (AsyncResult) msg.obj;
                    if (ar.result != null) {
                        attemptsRemaining = UiccCardApplication.this.parsePinPukErrorResult(ar, 4);
                    }
                    UiccCardApplication.this.mIccStateUpdated = 0;
                    sendMessageDelayed(obtainMessage(13, ar), 500);
                    break;
                case 13:
                    UiccCardApplication.this.loge("EVENT_WAIT_UPDATE_DONE");
                    ar = (AsyncResult) msg.obj;
                    if (UiccCardApplication.this.mIccStateUpdated <= 10) {
                        UiccCardApplication.this.mIccStateUpdated = UiccCardApplication.this.mIccStateUpdated + 1;
                        UiccCardApplication.this.loge("EVENT_WAIT_UPDATE_DONE again");
                        sendMessageDelayed(obtainMessage(13, ar), 10);
                        break;
                    }
                    UiccCardApplication.this.loge("EVENT_WAIT_UPDATE_DONE finish");
                    sendResultToTarget((Message) ar.userObj, ar.exception);
                    break;
                default:
                    UiccCardApplication.this.loge("Unknown Event " + msg.what);
                    break;
            }
        }
    }

    static /* synthetic */ class C01362 {
        static final /* synthetic */ int[] f17x5a34abf5 = new int[AppType.values().length];
        static final /* synthetic */ int[] f18x1c108904 = new int[PersoSubState.values().length];
        static final /* synthetic */ int[] f19xe6a897ea = new int[PinState.values().length];

        static {
            try {
                f18x1c108904[PersoSubState.PERSOSUBSTATE_UNKNOWN.ordinal()] = 1;
            } catch (NoSuchFieldError e) {
            }
            try {
                f18x1c108904[PersoSubState.PERSOSUBSTATE_IN_PROGRESS.ordinal()] = 2;
            } catch (NoSuchFieldError e2) {
            }
            try {
                f18x1c108904[PersoSubState.PERSOSUBSTATE_READY.ordinal()] = 3;
            } catch (NoSuchFieldError e3) {
            }
            try {
                f19xe6a897ea[PinState.PINSTATE_DISABLED.ordinal()] = 1;
            } catch (NoSuchFieldError e4) {
            }
            try {
                f19xe6a897ea[PinState.PINSTATE_ENABLED_NOT_VERIFIED.ordinal()] = 2;
            } catch (NoSuchFieldError e5) {
            }
            try {
                f19xe6a897ea[PinState.PINSTATE_ENABLED_VERIFIED.ordinal()] = 3;
            } catch (NoSuchFieldError e6) {
            }
            try {
                f19xe6a897ea[PinState.PINSTATE_ENABLED_BLOCKED.ordinal()] = 4;
            } catch (NoSuchFieldError e7) {
            }
            try {
                f19xe6a897ea[PinState.PINSTATE_ENABLED_PERM_BLOCKED.ordinal()] = 5;
            } catch (NoSuchFieldError e8) {
            }
            try {
                f19xe6a897ea[PinState.PINSTATE_UNKNOWN.ordinal()] = 6;
            } catch (NoSuchFieldError e9) {
            }
            try {
                f17x5a34abf5[AppType.APPTYPE_SIM.ordinal()] = 1;
            } catch (NoSuchFieldError e10) {
            }
            try {
                f17x5a34abf5[AppType.APPTYPE_RUIM.ordinal()] = 2;
            } catch (NoSuchFieldError e11) {
            }
            try {
                f17x5a34abf5[AppType.APPTYPE_USIM.ordinal()] = 3;
            } catch (NoSuchFieldError e12) {
            }
            try {
                f17x5a34abf5[AppType.APPTYPE_CSIM.ordinal()] = 4;
            } catch (NoSuchFieldError e13) {
            }
            try {
                f17x5a34abf5[AppType.APPTYPE_ISIM.ordinal()] = 5;
            } catch (NoSuchFieldError e14) {
            }
        }
    }

    UiccCardApplication(UiccCard uiccCard, IccCardApplicationStatus as, Context c, CommandsInterface ci) {
        boolean z = true;
        log("Creating UiccApp: " + as);
        this.mUiccCard = uiccCard;
        this.mPhone = this.mUiccCard.mPhone;
        this.mAppState = as.app_state;
        this.mAppType = as.app_type;
        this.mAuthContext = getAuthContext(this.mAppType);
        this.mPersoSubState = as.perso_substate;
        this.mAid = as.aid;
        this.mAppLabel = as.app_label;
        if (as.pin1_replaced == 0) {
            z = false;
        }
        this.mPin1Replaced = z;
        this.mPin1State = as.pin1;
        this.mPin2State = as.pin2;
        this.mPin1RetryCount = as.pin1_num_retries;
        this.mPuk1RetryCount = as.puk1_num_retries;
        this.mPin2RetryCount = as.pin2_num_retries;
        this.mPuk2RetryCount = as.puk2_num_retries;
        this.mperso_unblock_retries = as.perso_unblock_retries;
        this.mContext = c;
        this.mCi = ci;
        this.mIccFh = createIccFileHandler(as.app_type);
        this.mIccRecords = createIccRecords(as.app_type, this.mContext, this.mCi);
        if (this.mAppState == AppState.APPSTATE_READY) {
            queryFdn();
            queryPin1State();
        }
    }

    void update(IccCardApplicationStatus as, Context c, CommandsInterface ci) {
        boolean z = false;
        synchronized (this.mLock) {
            if (this.mDestroyed) {
                loge("Application updated after destroyed! Fix me!");
                return;
            }
            log(this.mAppType + " update. New " + as);
            loge("in update method ");
            this.mContext = c;
            this.mCi = ci;
            AppType oldAppType = this.mAppType;
            AppState oldAppState = this.mAppState;
            PinState oldPin1State = this.mPin1State;
            PersoSubState oldPersoSubState = this.mPersoSubState;
            this.mAppType = as.app_type;
            this.mAuthContext = getAuthContext(this.mAppType);
            this.mAppState = as.app_state;
            this.mPersoSubState = as.perso_substate;
            this.mAid = as.aid;
            this.mAppLabel = as.app_label;
            if (as.pin1_replaced != 0) {
                z = true;
            }
            this.mPin1Replaced = z;
            this.mPin1State = as.pin1;
            this.mPin2State = as.pin2;
            this.mPin1RetryCount = as.pin1_num_retries;
            this.mPuk1RetryCount = as.puk1_num_retries;
            this.mPin2RetryCount = as.pin2_num_retries;
            this.mPuk2RetryCount = as.puk2_num_retries;
            this.mperso_unblock_retries = as.perso_unblock_retries;
            if (this.mAppType != oldAppType) {
                if (this.mIccFh != null) {
                    this.mIccFh.dispose();
                }
                if (this.mIccRecords != null) {
                    this.mIccRecords.dispose();
                }
                this.mIccFh = createIccFileHandler(as.app_type);
                this.mIccRecords = createIccRecords(as.app_type, c, ci);
            }
            if (this.mPersoSubState != oldPersoSubState && isPersoLocked()) {
                notifyNetworkLockedRegistrantsIfNeeded(null);
            }
            if (this.mAppState != oldAppState || this.mSimRefreshState) {
                log(oldAppType + " changed state: " + oldAppState + " -> " + this.mAppState);
                if (this.mAppState == AppState.APPSTATE_READY) {
                    queryFdn();
                    queryPin1State();
                }
                notifyPinLockedRegistrantsIfNeeded(null);
                notifyReadyRegistrantsIfNeeded(null);
                this.mSimRefreshState = false;
            } else if (this.mPin1State != oldPin1State && this.mAppState == AppState.APPSTATE_READY) {
                log(oldAppType + " changed PIN1 state: " + oldPin1State + " -> " + this.mPin1State);
                queryPin1State();
            }
            this.mIccStateUpdated = 100;
        }
    }

    void dispose() {
        synchronized (this.mLock) {
            log(this.mAppType + " being Disposed");
            this.mDestroyed = true;
            if (this.mIccRecords != null) {
                this.mIccRecords.dispose();
            }
            if (this.mIccFh != null) {
                this.mIccFh.dispose();
            }
            this.mIccRecords = null;
            this.mIccFh = null;
        }
    }

    private IccRecords createIccRecords(AppType type, Context c, CommandsInterface ci) {
        if (type == AppType.APPTYPE_USIM || type == AppType.APPTYPE_SIM) {
            return new SIMRecords(this, c, ci);
        }
        if (type == AppType.APPTYPE_RUIM || type == AppType.APPTYPE_CSIM) {
            return new RuimRecords(this, c, ci);
        }
        if (type == AppType.APPTYPE_ISIM) {
            return new IsimUiccRecords(this, c, ci);
        }
        return null;
    }

    private IccFileHandler createIccFileHandler(AppType type) {
        switch (C01362.f17x5a34abf5[type.ordinal()]) {
            case 1:
                return new SIMFileHandler(this, this.mAid, this.mCi);
            case 2:
                return new RuimFileHandler(this, this.mAid, this.mCi);
            case 3:
                return new UsimFileHandler(this, this.mAid, this.mCi);
            case 4:
                return new CsimFileHandler(this, this.mAid, this.mCi);
            case 5:
                return new IsimFileHandler(this, this.mAid, this.mCi);
            default:
                return null;
        }
    }

    void queryFdn() {
        this.mCi.queryFacilityLockForApp(CommandsInterface.CB_FACILITY_BA_FD, "", 7, this.mAid, this.mHandler.obtainMessage(4));
    }

    /* JADX WARNING: inconsistent code. */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    private void onQueryFdnEnabled(android.os.AsyncResult r8) {
        /*
        r7 = this;
        r3 = 1;
        r4 = 0;
        r5 = r7.mLock;
        monitor-enter(r5);
        r2 = r8.exception;	 Catch:{ all -> 0x0060 }
        if (r2 == 0) goto L_0x0023;
    L_0x0009:
        r2 = new java.lang.StringBuilder;	 Catch:{ all -> 0x0060 }
        r2.<init>();	 Catch:{ all -> 0x0060 }
        r3 = "Error in querying facility lock:";
        r2 = r2.append(r3);	 Catch:{ all -> 0x0060 }
        r3 = r8.exception;	 Catch:{ all -> 0x0060 }
        r2 = r2.append(r3);	 Catch:{ all -> 0x0060 }
        r2 = r2.toString();	 Catch:{ all -> 0x0060 }
        r7.log(r2);	 Catch:{ all -> 0x0060 }
        monitor-exit(r5);	 Catch:{ all -> 0x0060 }
    L_0x0022:
        return;
    L_0x0023:
        r2 = r8.result;	 Catch:{ all -> 0x0060 }
        r2 = (int[]) r2;	 Catch:{ all -> 0x0060 }
        r0 = r2;
        r0 = (int[]) r0;	 Catch:{ all -> 0x0060 }
        r1 = r0;
        r2 = r1.length;	 Catch:{ all -> 0x0060 }
        if (r2 == 0) goto L_0x0071;
    L_0x002e:
        r2 = 0;
        r2 = r1[r2];	 Catch:{ all -> 0x0060 }
        r6 = 2;
        if (r2 != r6) goto L_0x0063;
    L_0x0034:
        r2 = 0;
        r7.mIccFdnEnabled = r2;	 Catch:{ all -> 0x0060 }
        r2 = 0;
        r7.mIccFdnAvailable = r2;	 Catch:{ all -> 0x0060 }
    L_0x003a:
        r2 = new java.lang.StringBuilder;	 Catch:{ all -> 0x0060 }
        r2.<init>();	 Catch:{ all -> 0x0060 }
        r3 = "Query facility FDN : FDN service available: ";
        r2 = r2.append(r3);	 Catch:{ all -> 0x0060 }
        r3 = r7.mIccFdnAvailable;	 Catch:{ all -> 0x0060 }
        r2 = r2.append(r3);	 Catch:{ all -> 0x0060 }
        r3 = " enabled: ";
        r2 = r2.append(r3);	 Catch:{ all -> 0x0060 }
        r3 = r7.mIccFdnEnabled;	 Catch:{ all -> 0x0060 }
        r2 = r2.append(r3);	 Catch:{ all -> 0x0060 }
        r2 = r2.toString();	 Catch:{ all -> 0x0060 }
        r7.log(r2);	 Catch:{ all -> 0x0060 }
    L_0x005e:
        monitor-exit(r5);	 Catch:{ all -> 0x0060 }
        goto L_0x0022;
    L_0x0060:
        r2 = move-exception;
        monitor-exit(r5);	 Catch:{ all -> 0x0060 }
        throw r2;
    L_0x0063:
        r2 = 0;
        r2 = r1[r2];	 Catch:{ all -> 0x0060 }
        if (r2 != r3) goto L_0x006f;
    L_0x0068:
        r2 = r3;
    L_0x0069:
        r7.mIccFdnEnabled = r2;	 Catch:{ all -> 0x0060 }
        r2 = 1;
        r7.mIccFdnAvailable = r2;	 Catch:{ all -> 0x0060 }
        goto L_0x003a;
    L_0x006f:
        r2 = r4;
        goto L_0x0069;
    L_0x0071:
        r2 = "Bogus facility lock response";
        r7.loge(r2);	 Catch:{ all -> 0x0060 }
        goto L_0x005e;
        */
        throw new UnsupportedOperationException("Method not decompiled: com.android.internal.telephony.uicc.UiccCardApplication.onQueryFdnEnabled(android.os.AsyncResult):void");
    }

    private void onChangeFdnDone(AsyncResult ar) {
        synchronized (this.mLock) {
            int attemptsRemaining = -1;
            if (ar.exception == null) {
                this.mIccFdnEnabled = this.mDesiredFdnEnabled;
                attemptsRemaining = parsePinPukErrorResult(ar, 3);
                log("EVENT_CHANGE_FACILITY_FDN_DONE: mIccFdnEnabled=" + this.mIccFdnEnabled);
            } else {
                if (ar.exception.toString().contains("PASSWORD_INCORRECT")) {
                    attemptsRemaining = parsePinPukErrorResult(ar, 3);
                }
                loge("Error change facility fdn with exception " + ar.exception);
            }
            Message response = ar.userObj;
            response.arg1 = attemptsRemaining;
            AsyncResult.forMessage(response).exception = ar.exception;
            response.sendToTarget();
        }
    }

    private void queryPin1State() {
        this.mCi.queryFacilityLockForApp(CommandsInterface.CB_FACILITY_BA_SIM, "", 7, this.mAid, this.mHandler.obtainMessage(6));
    }

    /* JADX WARNING: inconsistent code. */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    private void onQueryFacilityLock(android.os.AsyncResult r7) {
        /*
        r6 = this;
        r3 = 0;
        r4 = r6.mLock;
        monitor-enter(r4);
        r2 = r7.exception;	 Catch:{ all -> 0x007e }
        if (r2 == 0) goto L_0x0022;
    L_0x0008:
        r2 = new java.lang.StringBuilder;	 Catch:{ all -> 0x007e }
        r2.<init>();	 Catch:{ all -> 0x007e }
        r3 = "Error in querying facility lock:";
        r2 = r2.append(r3);	 Catch:{ all -> 0x007e }
        r3 = r7.exception;	 Catch:{ all -> 0x007e }
        r2 = r2.append(r3);	 Catch:{ all -> 0x007e }
        r2 = r2.toString();	 Catch:{ all -> 0x007e }
        r6.log(r2);	 Catch:{ all -> 0x007e }
        monitor-exit(r4);	 Catch:{ all -> 0x007e }
    L_0x0021:
        return;
    L_0x0022:
        r2 = r7.result;	 Catch:{ all -> 0x007e }
        r2 = (int[]) r2;	 Catch:{ all -> 0x007e }
        r0 = r2;
        r0 = (int[]) r0;	 Catch:{ all -> 0x007e }
        r1 = r0;
        r2 = r1.length;	 Catch:{ all -> 0x007e }
        if (r2 == 0) goto L_0x0097;
    L_0x002d:
        r2 = new java.lang.StringBuilder;	 Catch:{ all -> 0x007e }
        r2.<init>();	 Catch:{ all -> 0x007e }
        r5 = "Query facility lock : ";
        r2 = r2.append(r5);	 Catch:{ all -> 0x007e }
        r5 = 0;
        r5 = r1[r5];	 Catch:{ all -> 0x007e }
        r2 = r2.append(r5);	 Catch:{ all -> 0x007e }
        r2 = r2.toString();	 Catch:{ all -> 0x007e }
        r6.log(r2);	 Catch:{ all -> 0x007e }
        r2 = 0;
        r2 = r1[r2];	 Catch:{ all -> 0x007e }
        if (r2 == 0) goto L_0x0081;
    L_0x004b:
        r2 = 1;
    L_0x004c:
        r6.mIccLockEnabled = r2;	 Catch:{ all -> 0x007e }
        r2 = r6.mIccLockEnabled;	 Catch:{ all -> 0x007e }
        if (r2 == 0) goto L_0x0057;
    L_0x0052:
        r2 = r6.mPinLockedRegistrants;	 Catch:{ all -> 0x007e }
        r2.notifyRegistrants();	 Catch:{ all -> 0x007e }
    L_0x0057:
        r2 = com.android.internal.telephony.uicc.UiccCardApplication.C01362.f19xe6a897ea;	 Catch:{ all -> 0x007e }
        r3 = r6.mPin1State;	 Catch:{ all -> 0x007e }
        r3 = r3.ordinal();	 Catch:{ all -> 0x007e }
        r2 = r2[r3];	 Catch:{ all -> 0x007e }
        switch(r2) {
            case 1: goto L_0x0083;
            case 2: goto L_0x008d;
            case 3: goto L_0x008d;
            case 4: goto L_0x008d;
            case 5: goto L_0x008d;
            default: goto L_0x0064;
        };	 Catch:{ all -> 0x007e }
    L_0x0064:
        r2 = new java.lang.StringBuilder;	 Catch:{ all -> 0x007e }
        r2.<init>();	 Catch:{ all -> 0x007e }
        r3 = "Ignoring: pin1state=";
        r2 = r2.append(r3);	 Catch:{ all -> 0x007e }
        r3 = r6.mPin1State;	 Catch:{ all -> 0x007e }
        r2 = r2.append(r3);	 Catch:{ all -> 0x007e }
        r2 = r2.toString();	 Catch:{ all -> 0x007e }
        r6.log(r2);	 Catch:{ all -> 0x007e }
    L_0x007c:
        monitor-exit(r4);	 Catch:{ all -> 0x007e }
        goto L_0x0021;
    L_0x007e:
        r2 = move-exception;
        monitor-exit(r4);	 Catch:{ all -> 0x007e }
        throw r2;
    L_0x0081:
        r2 = r3;
        goto L_0x004c;
    L_0x0083:
        r2 = r6.mIccLockEnabled;	 Catch:{ all -> 0x007e }
        if (r2 == 0) goto L_0x007c;
    L_0x0087:
        r2 = "QUERY_FACILITY_LOCK:enabled GET_SIM_STATUS.Pin1:disabled. Fixme";
        r6.loge(r2);	 Catch:{ all -> 0x007e }
        goto L_0x007c;
    L_0x008d:
        r2 = r6.mIccLockEnabled;	 Catch:{ all -> 0x007e }
        if (r2 != 0) goto L_0x0064;
    L_0x0091:
        r2 = "QUERY_FACILITY_LOCK:disabled GET_SIM_STATUS.Pin1:enabled. Fixme";
        r6.loge(r2);	 Catch:{ all -> 0x007e }
        goto L_0x0064;
    L_0x0097:
        r2 = "Bogus facility lock response";
        r6.loge(r2);	 Catch:{ all -> 0x007e }
        goto L_0x007c;
        */
        throw new UnsupportedOperationException("Method not decompiled: com.android.internal.telephony.uicc.UiccCardApplication.onQueryFacilityLock(android.os.AsyncResult):void");
    }

    private void onChangeFacilityLock(AsyncResult ar) {
        synchronized (this.mLock) {
            int attemptsRemaining = -1;
            if (ar.exception == null) {
                this.mIccLockEnabled = this.mDesiredPinLocked;
                attemptsRemaining = parsePinPukErrorResult(ar, 1);
                log("EVENT_CHANGE_FACILITY_LOCK_DONE: mIccLockEnabled= " + this.mIccLockEnabled);
            } else {
                if (ar.exception.toString().contains("PASSWORD_INCORRECT")) {
                    attemptsRemaining = parsePinPukErrorResult(ar, 1);
                }
                loge("Error change facility lock with exception " + ar.exception);
            }
            Message response = ar.userObj;
            AsyncResult.forMessage(response).exception = ar.exception;
            response.arg1 = attemptsRemaining;
            response.sendToTarget();
        }
    }

    private int parsePinPukErrorResult(AsyncResult ar) {
        int[] result = (int[]) ar.result;
        if (result == null) {
            return -1;
        }
        int attemptsRemaining = -1;
        if (result.length > 0) {
            attemptsRemaining = result[0];
        }
        log("parsePinPukErrorResult: attemptsRemaining=" + attemptsRemaining);
        return attemptsRemaining;
    }

    private int parsePinPukErrorResult(AsyncResult ar, int PinType) {
        int[] intArray = (int[]) ar.result;
        if (intArray == null || intArray.length == 0) {
            return 0;
        }
        int length = intArray.length;
        switch (PinType) {
            case 1:
                this.mPin1RetryCount = intArray[0];
                break;
            case 2:
                this.mPuk1RetryCount = intArray[0];
                break;
            case 3:
                this.mPin2RetryCount = intArray[0];
                break;
            case 4:
                this.mPuk2RetryCount = intArray[0];
                break;
        }
        log("parsePinPukErrorResu lt intArray[0]=" + intArray[0]);
        return intArray[0];
    }

    public void registerForReady(Handler h, int what, Object obj) {
        synchronized (this.mLock) {
            Registrant r = new Registrant(h, what, obj);
            this.mReadyRegistrants.add(r);
            notifyReadyRegistrantsIfNeeded(r);
        }
    }

    public void unregisterForReady(Handler h) {
        synchronized (this.mLock) {
            this.mReadyRegistrants.remove(h);
        }
    }

    public void registerForLocked(Handler h, int what, Object obj) {
        synchronized (this.mLock) {
            Registrant r = new Registrant(h, what, obj);
            this.mPinLockedRegistrants.add(r);
            notifyPinLockedRegistrantsIfNeeded(r);
        }
    }

    public void unregisterForLocked(Handler h) {
        synchronized (this.mLock) {
            this.mPinLockedRegistrants.remove(h);
        }
    }

    public void registerForNetworkLocked(Handler h, int what, Object obj) {
        synchronized (this.mLock) {
            Registrant r = new Registrant(h, what, obj);
            this.mNetworkLockedRegistrants.add(r);
            notifyNetworkLockedRegistrantsIfNeeded(r);
        }
    }

    public void unregisterForNetworkLocked(Handler h) {
        synchronized (this.mLock) {
            this.mNetworkLockedRegistrants.remove(h);
        }
    }

    public void registerForSPLocked(Handler h, int what, Object obj) {
        Registrant r = new Registrant(h, what, obj);
        this.mSPLockedRegistrants.add(r);
        notifySPLockedRegistrantsIfNeeded(r);
    }

    public void unregisterForSPLocked(Handler h) {
        this.mSPLockedRegistrants.remove(h);
    }

    public void registerForNetworkSubsetLocked(Handler h, int what, Object obj) {
        synchronized (this.mLock) {
            Registrant r = new Registrant(h, what, obj);
            this.mNetworkSubsetLockedRegistrants.add(r);
            notifyNetworkSubsetLockedRegistrantsIfNeeded(r);
        }
    }

    public void unregisterForNetworkSubsetLocked(Handler h) {
        synchronized (this.mLock) {
            this.mNetworkSubsetLockedRegistrants.remove(h);
        }
    }

    public void registerForUpdateLockInfo(Handler h, int what, Object obj) {
        synchronized (this.mLock) {
            this.mUpdateSimLockInfoRegistrants.add(new Registrant(h, what, obj));
        }
    }

    public void unregisterForUpdateLockInfo(Handler h) {
        synchronized (this.mLock) {
            this.mUpdateSimLockInfoRegistrants.remove(h);
        }
    }

    private void notifyReadyRegistrantsIfNeeded(Registrant r) {
        if (this.mDestroyed || this.mAppState != AppState.APPSTATE_READY) {
            return;
        }
        if (this.mPin1State == PinState.PINSTATE_ENABLED_NOT_VERIFIED || this.mPin1State == PinState.PINSTATE_ENABLED_BLOCKED || this.mPin1State == PinState.PINSTATE_ENABLED_PERM_BLOCKED) {
            loge("Sanity check failed! APPSTATE is ready while PIN1 is not verified!!!");
        } else if (r == null) {
            log("Notifying registrants: READY");
            this.mReadyRegistrants.notifyRegistrants();
        } else {
            log("Notifying 1 registrant: READY");
            r.notifyRegistrant(new AsyncResult(null, null, null));
        }
    }

    private void notifyPinLockedRegistrantsIfNeeded(Registrant r) {
        if (!this.mDestroyed) {
            if (this.mAppState != AppState.APPSTATE_PIN && this.mAppState != AppState.APPSTATE_PUK) {
                return;
            }
            if (this.mPin1State == PinState.PINSTATE_ENABLED_VERIFIED || this.mPin1State == PinState.PINSTATE_DISABLED) {
                loge("Sanity check failed! APPSTATE is locked while PIN1 is not!!!");
            } else if (r == null) {
                log("Notifying registrants: LOCKED");
                this.mPinLockedRegistrants.notifyRegistrants();
            } else {
                log("Notifying 1 registrant: LOCKED");
                r.notifyRegistrant(new AsyncResult(null, null, null));
            }
        }
    }

    private void notifyNetworkLockedRegistrantsIfNeeded(Registrant r) {
        if (this.mDestroyed || this.mAppState != AppState.APPSTATE_SUBSCRIPTION_PERSO || !isPersoLocked()) {
            return;
        }
        if (r == null) {
            log("Notifying registrants: NETWORK_LOCKED");
            if (this.mPersoSubState == PersoSubState.PERSOSUBSTATE_SIM_SERVICE_PROVIDER) {
                log("Notifying registrants: SP_LOCKED");
                this.mSPLockedRegistrants.notifyRegistrants();
                return;
            } else if (this.mPersoSubState == PersoSubState.PERSOSUBSTATE_SIM_NETWORK_SUBSET) {
                log("Notifying registrants: NETWORK_SUBSET_LOCKED");
                this.mNetworkSubsetLockedRegistrants.notifyRegistrants();
                return;
            } else {
                this.mNetworkLockedRegistrants.notifyRegistrants();
                return;
            }
        }
        log("Notifying 1 registrant: NETWORK_LOCKED");
        if (this.mPersoSubState != PersoSubState.PERSOSUBSTATE_SIM_SERVICE_PROVIDER && this.mPersoSubState != PersoSubState.PERSOSUBSTATE_SIM_NETWORK_SUBSET) {
            r.notifyRegistrant(new AsyncResult(null, null, null));
        }
    }

    private synchronized void notifySPLockedRegistrantsIfNeeded(Registrant r) {
        if (!this.mDestroyed) {
            if (this.mAppState == AppState.APPSTATE_SUBSCRIPTION_PERSO && this.mPersoSubState == PersoSubState.PERSOSUBSTATE_SIM_SERVICE_PROVIDER && r != null) {
                log("Notifying 1 registrant: SP_LOCED");
                r.notifyRegistrant(new AsyncResult(null, null, null));
            }
        }
    }

    private synchronized void notifyNetworkSubsetLockedRegistrantsIfNeeded(Registrant r) {
        if (!this.mDestroyed) {
            if (this.mAppState == AppState.APPSTATE_SUBSCRIPTION_PERSO && this.mPersoSubState == PersoSubState.PERSOSUBSTATE_SIM_NETWORK_SUBSET && r != null) {
                log("Notifying 1 registrant: PERSOSUBSTATE_SIM_NETWORK_SUBSET");
                r.notifyRegistrant(new AsyncResult(null, null, null));
            }
        }
    }

    public AppState getState() {
        AppState appState;
        synchronized (this.mLock) {
            appState = this.mAppState;
        }
        return appState;
    }

    public AppType getType() {
        AppType appType;
        synchronized (this.mLock) {
            appType = this.mAppType;
        }
        return appType;
    }

    public int getAuthContext() {
        int i;
        synchronized (this.mLock) {
            i = this.mAuthContext;
        }
        return i;
    }

    private static int getAuthContext(AppType appType) {
        switch (C01362.f17x5a34abf5[appType.ordinal()]) {
            case 1:
                return 128;
            case 3:
                return 129;
            default:
                return -1;
        }
    }

    public PersoSubState getPersoSubState() {
        PersoSubState persoSubState;
        synchronized (this.mLock) {
            persoSubState = this.mPersoSubState;
        }
        return persoSubState;
    }

    public String getAid() {
        String str;
        synchronized (this.mLock) {
            str = this.mAid;
        }
        return str;
    }

    public String getAppLabel() {
        return this.mAppLabel;
    }

    public PinState getPin1State() {
        PinState universalPinState;
        synchronized (this.mLock) {
            if (this.mPin1Replaced) {
                universalPinState = this.mUiccCard.getUniversalPinState();
            } else {
                universalPinState = this.mPin1State;
            }
        }
        return universalPinState;
    }

    public IccFileHandler getIccFileHandler() {
        IccFileHandler iccFileHandler;
        synchronized (this.mLock) {
            iccFileHandler = this.mIccFh;
        }
        return iccFileHandler;
    }

    public IccRecords getIccRecords() {
        IccRecords iccRecords;
        synchronized (this.mLock) {
            iccRecords = this.mIccRecords;
        }
        return iccRecords;
    }

    public void supplyPin(String pin, Message onComplete) {
        synchronized (this.mLock) {
            this.mCi.supplyIccPinForApp(pin, this.mAid, this.mHandler.obtainMessage(9, onComplete));
        }
    }

    public void supplyPuk(String puk, String newPin, Message onComplete) {
        synchronized (this.mLock) {
            this.mCi.supplyIccPukForApp(puk, newPin, this.mAid, this.mHandler.obtainMessage(11, onComplete));
        }
    }

    public void supplyPin2(String pin2, Message onComplete) {
        synchronized (this.mLock) {
            this.mCi.supplyIccPin2ForApp(pin2, this.mAid, this.mHandler.obtainMessage(10, onComplete));
        }
    }

    public void supplyPuk2(String puk2, String newPin2, Message onComplete) {
        synchronized (this.mLock) {
            this.mCi.supplyIccPuk2ForApp(puk2, newPin2, this.mAid, this.mHandler.obtainMessage(12, onComplete));
        }
    }

    public void supplyNetworkDepersonalization(String pin, Message onComplete) {
        synchronized (this.mLock) {
            log("supplyNetworkDepersonalization");
            this.mCi.supplyNetworkDepersonalization(pin, onComplete);
        }
    }

    public void supplyNetworkDepersonalization(String pin, int lockState, Message onComplete) {
        log("Network Despersonalization: " + lockState);
        this.mCi.supplyNetworkDepersonalization(pin, lockState, onComplete);
    }

    public boolean getIccLockEnabled() {
        return this.mIccLockEnabled;
    }

    public boolean getIccFdnEnabled() {
        boolean z;
        synchronized (this.mLock) {
            z = this.mIccFdnEnabled;
        }
        return z;
    }

    public boolean getIccFdnAvailable() {
        return this.mIccFdnAvailable;
    }

    public void setIccLockEnabled(boolean enabled, String password, Message onComplete) {
        synchronized (this.mLock) {
            this.mDesiredPinLocked = enabled;
            this.mCi.setFacilityLockForApp(CommandsInterface.CB_FACILITY_BA_SIM, enabled, password, 7, this.mAid, this.mHandler.obtainMessage(7, onComplete));
        }
    }

    public void setIccFdnEnabled(boolean enabled, String password, Message onComplete) {
        synchronized (this.mLock) {
            this.mDesiredFdnEnabled = enabled;
            this.mCi.setFacilityLockForApp(CommandsInterface.CB_FACILITY_BA_FD, enabled, password, 15, this.mAid, this.mHandler.obtainMessage(5, onComplete));
        }
    }

    public void changeIccLockPassword(String oldPassword, String newPassword, Message onComplete) {
        synchronized (this.mLock) {
            log("changeIccLockPassword");
            this.mCi.changeIccPinForApp(oldPassword, newPassword, this.mAid, this.mHandler.obtainMessage(2, onComplete));
        }
    }

    public void changeIccFdnPassword(String oldPassword, String newPassword, Message onComplete) {
        synchronized (this.mLock) {
            log("changeIccFdnPassword");
            this.mCi.changeIccPin2ForApp(oldPassword, newPassword, this.mAid, this.mHandler.obtainMessage(3, onComplete));
        }
    }

    public boolean getIccPin2Blocked() {
        boolean z;
        synchronized (this.mLock) {
            z = this.mPin2State == PinState.PINSTATE_ENABLED_BLOCKED;
        }
        return z;
    }

    public boolean getIccPuk2Blocked() {
        boolean z;
        synchronized (this.mLock) {
            z = this.mPin2State == PinState.PINSTATE_ENABLED_PERM_BLOCKED;
        }
        return z;
    }

    protected UiccCard getUiccCard() {
        return this.mUiccCard;
    }

    private void log(String msg) {
        if (this.mPhone != null) {
            Rlog.d(MultiSimManager.appendSimSlot(LOG_TAG, this.mPhone.getPhoneId()), msg);
        } else {
            Rlog.d(LOG_TAG, msg);
        }
    }

    private void loge(String msg) {
        if (this.mPhone != null) {
            Rlog.e(MultiSimManager.appendSimSlot(LOG_TAG, this.mPhone.getPhoneId()), msg);
        } else {
            Rlog.e(LOG_TAG, msg);
        }
    }

    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        int i;
        pw.println("UiccCardApplication: " + this);
        pw.println(" mUiccCard=" + this.mUiccCard);
        pw.println(" mAppState=" + this.mAppState);
        pw.println(" mAppType=" + this.mAppType);
        pw.println(" mPersoSubState=" + this.mPersoSubState);
        pw.println(" mAid=" + this.mAid);
        pw.println(" mAppLabel=" + this.mAppLabel);
        pw.println(" mPin1Replaced=" + this.mPin1Replaced);
        pw.println(" mPin1State=" + this.mPin1State);
        pw.println(" mPin2State=" + this.mPin2State);
        pw.println(" mIccFdnEnabled=" + this.mIccFdnEnabled);
        pw.println(" mDesiredFdnEnabled=" + this.mDesiredFdnEnabled);
        pw.println(" mIccLockEnabled=" + this.mIccLockEnabled);
        pw.println(" mDesiredPinLocked=" + this.mDesiredPinLocked);
        pw.println(" mCi=" + this.mCi);
        pw.println(" mIccRecords=" + this.mIccRecords);
        pw.println(" mIccFh=" + this.mIccFh);
        pw.println(" mDestroyed=" + this.mDestroyed);
        pw.println(" mReadyRegistrants: size=" + this.mReadyRegistrants.size());
        for (i = 0; i < this.mReadyRegistrants.size(); i++) {
            pw.println("  mReadyRegistrants[" + i + "]=" + ((Registrant) this.mReadyRegistrants.get(i)).getHandler());
        }
        pw.println(" mPinLockedRegistrants: size=" + this.mPinLockedRegistrants.size());
        for (i = 0; i < this.mPinLockedRegistrants.size(); i++) {
            pw.println("  mPinLockedRegistrants[" + i + "]=" + ((Registrant) this.mPinLockedRegistrants.get(i)).getHandler());
        }
        pw.println(" mNetworkLockedRegistrants: size=" + this.mNetworkLockedRegistrants.size());
        for (i = 0; i < this.mNetworkLockedRegistrants.size(); i++) {
            pw.println("  mNetworkLockedRegistrants[" + i + "]=" + ((Registrant) this.mNetworkLockedRegistrants.get(i)).getHandler());
        }
        pw.flush();
    }

    public int getIntType() {
        switch (C01362.f17x5a34abf5[this.mAppType.ordinal()]) {
            case 1:
                return 1;
            case 2:
                return 2;
            default:
                return 0;
        }
    }

    public UiccCard getCard() {
        return this.mUiccCard;
    }

    public boolean isPersoLocked() {
        switch (C01362.f18x1c108904[this.mPersoSubState.ordinal()]) {
            case 1:
            case 2:
            case 3:
                return false;
            default:
                return true;
        }
    }

    public void updateSimLockInfo() {
        this.mUpdateSimLockInfoRegistrants.notifyRegistrants();
    }

    public boolean getIccPinBlocked() {
        boolean z;
        synchronized (this.mLock) {
            z = this.mPin1State == PinState.PINSTATE_ENABLED_BLOCKED;
        }
        return z;
    }

    public boolean getIccPukBlocked() {
        boolean z;
        synchronized (this.mLock) {
            z = this.mPin1State == PinState.PINSTATE_ENABLED_PERM_BLOCKED;
        }
        return z;
    }

    public int getIccPin1RetryCount() {
        return this.mPin1RetryCount;
    }

    public int getIccPin2RetryCount() {
        return this.mPin2RetryCount;
    }

    public int getIccPuk1RetryCount() {
        return this.mPuk1RetryCount;
    }

    public int getIccPuk2RetryCount() {
        return this.mPuk2RetryCount;
    }

    public int getIccPersoRetryCount() {
        return this.mperso_unblock_retries;
    }
}
