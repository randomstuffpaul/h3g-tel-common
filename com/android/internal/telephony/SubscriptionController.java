package com.android.internal.telephony;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.ContentObserver;
import android.database.Cursor;
import android.os.AsyncResult;
import android.os.Handler;
import android.os.Message;
import android.os.Process;
import android.os.ServiceManager;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.provider.Settings.Global;
import android.provider.Settings.SettingNotFoundException;
import android.provider.Telephony.Carriers;
import android.telephony.Rlog;
import android.telephony.SubInfoRecord;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.text.format.Time;
import android.util.Log;
import com.android.internal.telephony.ISub.Stub;
import com.android.internal.telephony.PhoneConstants.State;
import com.android.internal.telephony.dataconnection.DctController;
import com.android.internal.telephony.uicc.SpnOverride;
import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;

public class SubscriptionController extends Stub {
    static final boolean DBG = true;
    protected static final int ENABLE_DATA_SWITCH_DELAY = 3000;
    private static final int EVENT_BROADCAST_SUBSCRIPTION_CHANGE_RESULT = 3;
    private static final int EVENT_SUBSCRIPTION_CHANGE_COMPLETED = 2;
    private static final int EVENT_WRITE_MSISDN_DONE = 1;
    static final String LOG_TAG = "SubController";
    static final int MAX_LOCAL_LOG_LINES = 500;
    private static final boolean MODEL_H = SystemProperties.get("ro.product.device", "unknown").startsWith("hlte");
    private static final boolean MODEL_J = SystemProperties.get("ro.product.device", "unknown").startsWith("ja3g");
    private static final boolean MODEL_K = SystemProperties.get("ro.product.device", "unknown").startsWith("klte");
    private static final boolean MODEL_T = SystemProperties.get("ro.product.device", "unknown").startsWith("trlte");
    private static final int RES_TYPE_BACKGROUND_DARK = 0;
    private static final int RES_TYPE_BACKGROUND_LIGHT = 1;
    public static final boolean SHIP_BUILD = "true".equals(SystemProperties.get("ro.product_ship", "false"));
    static final boolean VDBG = false;
    private static List<SubInfoRecord> mActiveSubInfoList = null;
    private static List<SubInfoRecord> mAllSubInfoList = null;
    private static int mDefaultPhoneId = 0;
    private static long mDefaultVoiceSubId = Long.MAX_VALUE;
    private static HashMap<Integer, Long> mSimInfo = new HashMap();
    private static SubscriptionController sInstance = null;
    protected static PhoneProxy[] sProxyPhones;
    private static final int[] sSimBackgroundDarkRes = setSimResource(0);
    private static final int[] sSimBackgroundLightRes = setSimResource(1);
    protected CallManager mCM;
    private boolean mCalledSetDataAllowed = false;
    private boolean mCalledSetDataSubBySlot = false;
    private boolean mCalledUpdateUserPrefs = false;
    protected Context mContext;
    private ContentObserver mDefaultDataSubIdObserver = new ContentObserver(new Handler()) {
        public void onChange(boolean selfChange) {
            long subId = SubscriptionController.this.getDefaultDataSubId();
            int slotId = SubscriptionController.this.getSlotId(subId);
            if (slotId != SubscriptionController.this.getDefaultDataSlotId()) {
                SubscriptionController.this.logd("[DefaultDataSubIdObserver] changed subId:" + subId + ", slotId:" + slotId);
                SubscriptionController.this.setDefaultDataSlotId(slotId);
            }
        }
    };
    private ContentObserver mDefaultVoiceSubIdObserver = new ContentObserver(new Handler()) {
        public void onChange(boolean selfChange) {
            long subId = SubscriptionController.this.getDefaultVoiceSubId();
            SubscriptionController.this.setDefaultVoiceSlotId(SubscriptionController.this.getSlotId(subId));
            SubscriptionController.this.setDefaultSmsSubId(subId);
        }
    };
    protected Handler mHandler = new C00331();
    private ScLocalLog mLocalLog = new ScLocalLog(MAX_LOCAL_LOG_LINES);
    protected final Object mLock = new Object();
    protected boolean mSuccess;

    class C00331 extends Handler {
        C00331() {
        }

        public void handleMessage(Message msg) {
            switch (msg.what) {
                case 1:
                    AsyncResult ar = msg.obj;
                    synchronized (SubscriptionController.this.mLock) {
                        SubscriptionController.this.mSuccess = ar.exception == null;
                        SubscriptionController.this.logd("EVENT_WRITE_MSISDN_DONE, mSuccess = " + SubscriptionController.this.mSuccess);
                        SubscriptionController.this.mLock.notifyAll();
                    }
                    return;
                case 2:
                    if (((AsyncResult) msg.obj).exception == null) {
                        Message onDelay = obtainMessage(3);
                        onDelay.arg1 = msg.arg1;
                        sendMessageDelayed(onDelay, 3000);
                        SubscriptionController.this.logd("EVENT_SUBSCRIPTION_CHANGE_COMPLETED, subId = " + onDelay.arg1);
                        return;
                    }
                    long prevDataSubId = (long) msg.arg2;
                    Global.putLong(SubscriptionController.this.mContext.getContentResolver(), "multi_sim_data_call", prevDataSubId);
                    SubscriptionController.this.setDefaultDataSlotId(SubscriptionController.this.getSlotId(prevDataSubId));
                    Intent intent = new Intent("android.intent.action.ACTION_DEFAULT_DATA_SUBSCRIPTION_FAILED");
                    intent.addFlags(536870912);
                    intent.putExtra("subscription", msg.arg1);
                    SubscriptionController.this.mContext.sendStickyBroadcastAsUser(intent, UserHandle.ALL);
                    SubscriptionController.this.logd("ACTION_DEFAULT_DATA_SUBSCRIPTION_FAILED : " + prevDataSubId + " -> " + msg.arg1);
                    return;
                case 3:
                    SubscriptionController.this.broadcastDefaultDataSubIdChanged((long) msg.arg1);
                    SubscriptionController.this.updateAllDataConnectionTrackers();
                    return;
                default:
                    return;
            }
        }
    }

    static class ScLocalLog {
        private LinkedList<String> mLog = new LinkedList();
        private int mMaxLines;
        private Time mNow;

        public ScLocalLog(int maxLines) {
            this.mMaxLines = maxLines;
            this.mNow = new Time();
        }

        public synchronized void log(String msg) {
            if (this.mMaxLines > 0) {
                int pid = Process.myPid();
                int tid = Process.myTid();
                this.mNow.setToNow();
                this.mLog.add(this.mNow.format("%m-%d %H:%M:%S") + " pid=" + pid + " tid=" + tid + " " + msg);
                while (this.mLog.size() > this.mMaxLines) {
                    this.mLog.remove();
                }
            }
        }

        public synchronized void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
            Iterator<String> itr = this.mLog.listIterator(0);
            int i = 0;
            while (itr.hasNext()) {
                int i2 = i + 1;
                pw.println(Integer.toString(i) + ": " + ((String) itr.next()));
                if (i2 % 10 == 0) {
                    pw.flush();
                    i = i2;
                } else {
                    i = i2;
                }
            }
        }
    }

    public int getAllSubInfoCount() {
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
        r9 = this;
        r2 = 0;
        r9.enforceSubscriptionPermission();
        r8 = mAllSubInfoList;
        if (r8 == 0) goto L_0x0023;
    L_0x0008:
        r6 = r8.size();
        r0 = new java.lang.StringBuilder;
        r0.<init>();
        r1 = "[getAllSubInfoCount]- count: ";
        r0 = r0.append(r1);
        r0 = r0.append(r6);
        r0 = r0.toString();
        r9.logd(r0);
    L_0x0022:
        return r6;
    L_0x0023:
        r0 = r9.mContext;
        r0 = r0.getContentResolver();
        r1 = android.telephony.SubscriptionManager.CONTENT_URI;
        r3 = r2;
        r4 = r2;
        r5 = r2;
        r7 = r0.query(r1, r2, r3, r4, r5);
        if (r7 == 0) goto L_0x005a;
    L_0x0034:
        r6 = r7.getCount();	 Catch:{ all -> 0x0066 }
        r0 = new java.lang.StringBuilder;	 Catch:{ all -> 0x0066 }
        r0.<init>();	 Catch:{ all -> 0x0066 }
        r1 = "[getAllSubInfoCount]- ";	 Catch:{ all -> 0x0066 }
        r0 = r0.append(r1);	 Catch:{ all -> 0x0066 }
        r0 = r0.append(r6);	 Catch:{ all -> 0x0066 }
        r1 = " SUB(s) in DB";	 Catch:{ all -> 0x0066 }
        r0 = r0.append(r1);	 Catch:{ all -> 0x0066 }
        r0 = r0.toString();	 Catch:{ all -> 0x0066 }
        r9.logd(r0);	 Catch:{ all -> 0x0066 }
        if (r7 == 0) goto L_0x0022;
    L_0x0056:
        r7.close();
        goto L_0x0022;
    L_0x005a:
        if (r7 == 0) goto L_0x005f;
    L_0x005c:
        r7.close();
    L_0x005f:
        r0 = "[getAllSubInfoCount]- no SUB in DB";
        r9.logd(r0);
        r6 = 0;
        goto L_0x0022;
    L_0x0066:
        r0 = move-exception;
        if (r7 == 0) goto L_0x006c;
    L_0x0069:
        r7.close();
    L_0x006c:
        throw r0;
        */
        throw new UnsupportedOperationException("Method not decompiled: com.android.internal.telephony.SubscriptionController.getAllSubInfoCount():int");
    }

    public static SubscriptionController init(Phone phone) {
        SubscriptionController subscriptionController;
        synchronized (SubscriptionController.class) {
            if (sInstance == null) {
                sInstance = new SubscriptionController(phone);
            } else {
                Log.wtf(LOG_TAG, "init() called multiple times!  sInstance = " + sInstance);
            }
            subscriptionController = sInstance;
        }
        return subscriptionController;
    }

    public static SubscriptionController init(Context c, CommandsInterface[] ci) {
        SubscriptionController subscriptionController;
        synchronized (SubscriptionController.class) {
            if (sInstance == null) {
                sInstance = new SubscriptionController(c);
            } else {
                Log.wtf(LOG_TAG, "init() called multiple times!  sInstance = " + sInstance);
            }
            subscriptionController = sInstance;
        }
        return subscriptionController;
    }

    public static SubscriptionController getInstance() {
        if (sInstance == null) {
            Log.wtf(LOG_TAG, "getInstance null");
        }
        return sInstance;
    }

    private SubscriptionController(Context c) {
        this.mContext = c;
        this.mCM = CallManager.getInstance();
        if (ServiceManager.getService("isub") == null) {
            ServiceManager.addService("isub", this);
        }
        refreshSubInfo();
        this.mContext.getContentResolver().registerContentObserver(Global.getUriFor("multi_sim_voice_call"), false, this.mDefaultVoiceSubIdObserver);
        this.mContext.getContentResolver().registerContentObserver(Global.getUriFor("multi_sim_data_call"), false, this.mDefaultDataSubIdObserver);
        logdl("[SubscriptionController] init by Context");
    }

    private boolean isSubInfoReady() {
        return mSimInfo.size() > 0;
    }

    private SubscriptionController(Phone phone) {
        this.mContext = phone.getContext();
        this.mCM = CallManager.getInstance();
        if (ServiceManager.getService("isub") == null) {
            ServiceManager.addService("isub", this);
        }
        refreshSubInfo();
        this.mContext.getContentResolver().registerContentObserver(Global.getUriFor("multi_sim_voice_call"), false, this.mDefaultVoiceSubIdObserver);
        this.mContext.getContentResolver().registerContentObserver(Global.getUriFor("multi_sim_data_call"), false, this.mDefaultDataSubIdObserver);
        logdl("[SubscriptionController] init by Phone");
    }

    private void enforceSubscriptionPermission() {
        this.mContext.enforceCallingOrSelfPermission("android.permission.READ_PHONE_STATE", "Requires READ_PHONE_STATE");
    }

    private void broadcastSimInfoContentChanged(long subId, String columnName, int intContent, String stringContent) {
        refreshSubInfo();
        Intent intent = new Intent("android.intent.action.ACTION_SUBINFO_CONTENT_CHANGE");
        intent.putExtra("_id", subId);
        intent.putExtra("columnName", columnName);
        intent.putExtra("intContent", intContent);
        intent.putExtra("stringContent", stringContent);
        if (intContent != -100) {
            logd("[broadcastSimInfoContentChanged] subId" + subId + " changed, " + columnName + " -> " + intContent);
        } else {
            logd("[broadcastSimInfoContentChanged] subId" + subId + " changed, " + columnName + " -> " + stringContent);
        }
        this.mContext.sendBroadcast(intent);
    }

    private SubInfoRecord getSubInfoRecord(Cursor cursor) {
        SubInfoRecord info = new SubInfoRecord();
        info.subId = cursor.getLong(cursor.getColumnIndexOrThrow("_id"));
        info.iccId = cursor.getString(cursor.getColumnIndexOrThrow("icc_id"));
        info.slotId = cursor.getInt(cursor.getColumnIndexOrThrow("sim_id"));
        info.displayName = cursor.getString(cursor.getColumnIndexOrThrow("display_name"));
        info.nameSource = cursor.getInt(cursor.getColumnIndexOrThrow("name_source"));
        info.color = cursor.getInt(cursor.getColumnIndexOrThrow("color"));
        info.number = cursor.getString(cursor.getColumnIndexOrThrow("number"));
        info.displayNumberFormat = cursor.getInt(cursor.getColumnIndexOrThrow("display_number_format"));
        info.dataRoaming = cursor.getInt(cursor.getColumnIndexOrThrow("data_roaming"));
        int size = sSimBackgroundDarkRes.length;
        if (info.color >= 0 && info.color < size) {
            info.simIconRes[0] = sSimBackgroundDarkRes[info.color];
            info.simIconRes[1] = sSimBackgroundLightRes[info.color];
        }
        info.mcc = cursor.getInt(cursor.getColumnIndexOrThrow(Carriers.MCC));
        info.mnc = cursor.getInt(cursor.getColumnIndexOrThrow(Carriers.MNC));
        info.mStatus = cursor.getInt(cursor.getColumnIndexOrThrow("sub_state"));
        if (SHIP_BUILD) {
            logd("[getSubInfoRecord] SubId: XXX iccid: XXX slotId: XXX displayName: XXX color: XXX mcc/mnc: XXX sub_state: XXX");
        } else {
            logd("[getSubInfoRecord] SubId:" + info.subId + " iccid:" + info.iccId + " slotId:" + info.slotId + " displayName:" + info.displayName + " color:" + info.color + " mcc/mnc:" + info.mcc + "/" + info.mnc + " sub_state:" + info.mStatus);
        }
        return info;
    }

    private List<SubInfoRecord> getSubInfo(String selection, Object queryKey) {
        Throwable th;
        logd("selection:" + selection + " " + queryKey);
        String[] selectionArgs = null;
        if (queryKey != null) {
            selectionArgs = new String[]{queryKey.toString()};
        }
        ArrayList<SubInfoRecord> subList = null;
        Cursor cursor = this.mContext.getContentResolver().query(SubscriptionManager.CONTENT_URI, null, selection, selectionArgs, null);
        if (cursor != null) {
            ArrayList<SubInfoRecord> subList2 = null;
            while (cursor.moveToNext()) {
                try {
                    SubInfoRecord subInfo = getSubInfoRecord(cursor);
                    if (subInfo != null) {
                        if (subList2 == null) {
                            subList = new ArrayList();
                        } else {
                            subList = subList2;
                        }
                        try {
                            subList.add(subInfo);
                        } catch (Throwable th2) {
                            th = th2;
                        }
                    } else {
                        subList = subList2;
                    }
                    subList2 = subList;
                } catch (Throwable th3) {
                    th = th3;
                    subList = subList2;
                }
            }
            subList = subList2;
        } else {
            logd("Query fail");
        }
        if (cursor != null) {
            cursor.close();
        }
        return subList;
        if (cursor != null) {
            cursor.close();
        }
        throw th;
    }

    public SubInfoRecord getSubInfoForSubscriber(long subId) {
        SubInfoRecord subInfoRecord = null;
        enforceSubscriptionPermission();
        if (subId == Long.MAX_VALUE) {
            subId = getDefaultSubId();
        }
        if (SubscriptionManager.isValidSubId(subId) && isSubInfoReady()) {
            Cursor cursor = this.mContext.getContentResolver().query(SubscriptionManager.CONTENT_URI, null, "_id=?", new String[]{Long.toString(subId)}, null);
            if (cursor != null) {
                try {
                    if (cursor.moveToFirst()) {
                        logd("[getSubInfoForSubscriberx]- Info detail:");
                        subInfoRecord = getSubInfoRecord(cursor);
                        if (cursor != null) {
                            cursor.close();
                        }
                    }
                } catch (Throwable th) {
                    if (cursor != null) {
                        cursor.close();
                    }
                }
            }
            if (cursor != null) {
                cursor.close();
            }
            logd("[getSubInfoForSubscriber]- null info return");
        } else {
            logd("[getSubInfoForSubscriberx]- invalid subId or not ready, subId = " + subId);
        }
        return subInfoRecord;
    }

    public List<SubInfoRecord> getSubInfoUsingIccId(String iccId) {
        Throwable th;
        if (SHIP_BUILD) {
            logd("[getSubInfoUsingIccId]+ iccId: XXX");
        } else {
            logd("[getSubInfoUsingIccId]+ iccId:" + iccId);
        }
        enforceSubscriptionPermission();
        if (iccId == null || !isSubInfoReady()) {
            logd("[getSubInfoUsingIccId]- null iccid or not ready");
            return null;
        }
        Cursor cursor = this.mContext.getContentResolver().query(SubscriptionManager.CONTENT_URI, null, "icc_id=?", new String[]{iccId}, null);
        List<SubInfoRecord> list = null;
        if (cursor != null) {
            ArrayList<SubInfoRecord> subList = null;
            while (cursor.moveToNext()) {
                ArrayList<SubInfoRecord> subList2;
                try {
                    SubInfoRecord subInfo = getSubInfoRecord(cursor);
                    if (subInfo != null) {
                        if (subList == null) {
                            subList2 = new ArrayList();
                        } else {
                            subList2 = subList;
                        }
                        try {
                            subList2.add(subInfo);
                        } catch (Throwable th2) {
                            th = th2;
                        }
                    } else {
                        subList2 = subList;
                    }
                    subList = subList2;
                } catch (Throwable th3) {
                    th = th3;
                    subList2 = subList;
                }
            }
            Object subList3 = subList;
        } else {
            logd("Query fail");
        }
        if (cursor == null) {
            return list;
        }
        cursor.close();
        return list;
        if (cursor != null) {
            cursor.close();
        }
        throw th;
    }

    public List<SubInfoRecord> getSubInfoUsingSlotId(int slotId) {
        return getSubInfoUsingSlotIdWithCheck(slotId, true);
    }

    public List<SubInfoRecord> getAllSubInfoList() {
        enforceSubscriptionPermission();
        List<SubInfoRecord> subList = mAllSubInfoList;
        if (subList == null) {
            subList = getSubInfo(null, null);
            mAllSubInfoList = subList;
        }
        if (subList != null) {
            logd("[getAllSubInfoList]- " + subList.size() + " infos return");
        } else {
            logd("[getAllSubInfoList]- no info return");
        }
        return subList;
    }

    public List<SubInfoRecord> getActiveSubInfoList() {
        enforceSubscriptionPermission();
        if (isSubInfoReady()) {
            List<SubInfoRecord> subList = mActiveSubInfoList;
            if (subList == null) {
                subList = getSubInfo("sim_id!=-1000", null);
                mActiveSubInfoList = subList;
            }
            if (subList == null) {
                logdl("[getActiveSubInfoList]- no info return");
            }
            return subList;
        }
        logdl("[getActiveSubInfoList] Sub Controller not ready");
        return null;
    }

    public int getActiveSubInfoCount() {
        List<SubInfoRecord> records = getActiveSubInfoList();
        if (records != null) {
            return records.size();
        }
        logd("[getActiveSubInfoCount] records null");
        return 0;
    }

    public int addSubInfoRecord(String iccId, int slotId) {
        if (SHIP_BUILD) {
            logdl("[addSubInfoRecord]+ iccId: XXX slotId:" + slotId);
        } else {
            logdl("[addSubInfoRecord]+ iccId:" + iccId + " slotId:" + slotId);
        }
        enforceSubscriptionPermission();
        if (iccId == null) {
            logdl("[addSubInfoRecord]- null iccId");
        }
        long[] subIds = getSubId(slotId);
        if (subIds == null || subIds.length == 0) {
            logdl("[addSubInfoRecord]- getSubId fail");
            return 0;
        }
        String nameToSet;
        long subId;
        ContentValues value;
        Long currentSubId;
        int count;
        int i;
        long defaultSubId;
        SpnOverride mSpnOverride = new SpnOverride();
        String CarrierName = TelephonyManager.getDefault().getSimOperator(subIds[0]);
        logdl("[addSubInfoRecord] CarrierName = " + CarrierName);
        if (mSpnOverride.containsCarrier(CarrierName)) {
            nameToSet = mSpnOverride.getSpn(CarrierName, sProxyPhones[slotId].getSubscriberId()) + " 0" + Integer.toString(slotId + 1);
            logdl("[addSubInfoRecord] Found, name = " + nameToSet);
        } else {
            nameToSet = "SUB 0" + Integer.toString(slotId + 1);
            logdl("[addSubInfoRecord] Not found, name = " + nameToSet);
        }
        ContentResolver resolver = this.mContext.getContentResolver();
        Cursor cursor = resolver.query(SubscriptionManager.CONTENT_URI, new String[]{"_id", "sim_id", "name_source"}, "icc_id=?", new String[]{iccId}, null);
        if (cursor != null) {
            try {
                if (cursor.moveToFirst()) {
                    subId = cursor.getLong(0);
                    int oldSimInfoId = cursor.getInt(1);
                    int nameSource = cursor.getInt(2);
                    value = new ContentValues();
                    if (slotId != oldSimInfoId) {
                        value.put("sim_id", Integer.valueOf(slotId));
                    }
                    if (nameSource != 2) {
                        value.put("display_name", nameToSet);
                    }
                    if (value.size() > 0) {
                        resolver.update(SubscriptionManager.CONTENT_URI, value, "_id=" + Long.toString(subId), null);
                    }
                    logdl("[addSubInfoRecord]- Record already exist");
                    if (cursor != null) {
                        cursor.close();
                    }
                    cursor = resolver.query(SubscriptionManager.CONTENT_URI, null, "sim_id=?", new String[]{String.valueOf(slotId)}, null);
                    if (cursor != null) {
                        if (cursor.moveToFirst()) {
                            do {
                                subId = cursor.getLong(cursor.getColumnIndexOrThrow("_id"));
                                currentSubId = (Long) mSimInfo.get(Integer.valueOf(slotId));
                                if (currentSubId == null && SubscriptionManager.isValidSubId(currentSubId.longValue())) {
                                    try {
                                        logdl("[addSubInfoRecord] currentSubId != null && currentSubId is valid, IGNORE");
                                    } catch (Throwable th) {
                                        if (cursor != null) {
                                            cursor.close();
                                        }
                                    }
                                } else {
                                    int simCount;
                                    mSimInfo.put(Integer.valueOf(slotId), Long.valueOf(subId));
                                    simCount = TelephonyManager.getDefault().getSimCount();
                                    count = 0;
                                    for (i = 0; i < simCount; i++) {
                                        if (!"0".equals(TelephonyManager.getTelephonyProperty("ril.ICC_TYPE", getSubId(i)[0], "0"))) {
                                            count++;
                                        }
                                    }
                                    simCount = count;
                                    defaultSubId = getDefaultSubId();
                                    logdl("[addSubInfoRecord] mSimInfo.size=" + mSimInfo.size() + " slotId=" + slotId + " subId=" + subId + " defaultSubId=" + defaultSubId + " simCount=" + simCount);
                                    if (!SubscriptionManager.isValidSubId(defaultSubId) || simCount == 1) {
                                        setDefaultSubId(subId);
                                    }
                                    if (simCount == 1) {
                                        logdl("[addSubInfoRecord] one sim set defaults to subId=" + subId);
                                        setDefaultDataSubId(subId);
                                        setDefaultSmsSubId(subId);
                                        setDefaultVoiceSubId(subId);
                                    }
                                }
                                logdl("[addSubInfoRecord]- hashmap(" + slotId + "," + subId + ")");
                            } while (cursor.moveToNext());
                        }
                    }
                    if (cursor != null) {
                        cursor.close();
                    }
                    logdl("[addSubInfoRecord]- info size=" + mSimInfo.size());
                    updateAllDataConnectionTrackers();
                    return 1;
                }
            } catch (Throwable th2) {
                if (cursor != null) {
                    cursor.close();
                }
            }
        }
        value = new ContentValues();
        value.put("icc_id", iccId);
        value.put("color", Integer.valueOf(slotId));
        value.put("sim_id", Integer.valueOf(slotId));
        value.put("display_name", nameToSet);
        logdl("[addSubInfoRecord]- New record created: " + resolver.insert(SubscriptionManager.CONTENT_URI, value));
        if (cursor != null) {
            cursor.close();
        }
        cursor = resolver.query(SubscriptionManager.CONTENT_URI, null, "sim_id=?", new String[]{String.valueOf(slotId)}, null);
        if (cursor != null) {
            if (cursor.moveToFirst()) {
                do {
                    subId = cursor.getLong(cursor.getColumnIndexOrThrow("_id"));
                    currentSubId = (Long) mSimInfo.get(Integer.valueOf(slotId));
                    if (currentSubId == null) {
                    }
                    mSimInfo.put(Integer.valueOf(slotId), Long.valueOf(subId));
                    simCount = TelephonyManager.getDefault().getSimCount();
                    count = 0;
                    for (i = 0; i < simCount; i++) {
                        if (!"0".equals(TelephonyManager.getTelephonyProperty("ril.ICC_TYPE", getSubId(i)[0], "0"))) {
                            count++;
                        }
                    }
                    simCount = count;
                    defaultSubId = getDefaultSubId();
                    logdl("[addSubInfoRecord] mSimInfo.size=" + mSimInfo.size() + " slotId=" + slotId + " subId=" + subId + " defaultSubId=" + defaultSubId + " simCount=" + simCount);
                    setDefaultSubId(subId);
                    if (simCount == 1) {
                        logdl("[addSubInfoRecord] one sim set defaults to subId=" + subId);
                        setDefaultDataSubId(subId);
                        setDefaultSmsSubId(subId);
                        setDefaultVoiceSubId(subId);
                    }
                    logdl("[addSubInfoRecord]- hashmap(" + slotId + "," + subId + ")");
                } while (cursor.moveToNext());
            }
        }
        if (cursor != null) {
            cursor.close();
        }
        logdl("[addSubInfoRecord]- info size=" + mSimInfo.size());
        updateAllDataConnectionTrackers();
        return 1;
    }

    public int setColor(int color, long subId) {
        logd("[setColor]+ color:" + color + " subId:" + subId);
        enforceSubscriptionPermission();
        validateSubId(subId);
        int size = sSimBackgroundDarkRes.length;
        if (color < 0 || color >= size) {
            logd("[setColor]- fail");
            return -1;
        }
        ContentValues value = new ContentValues(1);
        value.put("color", Integer.valueOf(color));
        logd("[setColor]- color:" + color + " set");
        int result = this.mContext.getContentResolver().update(SubscriptionManager.CONTENT_URI, value, "_id=" + Long.toString(subId), null);
        broadcastSimInfoContentChanged(subId, "color", color, "N/A");
        return result;
    }

    public int setDisplayName(String displayName, long subId) {
        return setDisplayNameUsingSrc(displayName, subId, -1);
    }

    public int setDisplayNameUsingSrc(String displayName, long subId, long nameSource) {
        String nameToSet;
        logd("[setDisplayName]+  displayName:" + displayName + " subId:" + subId + " nameSource:" + nameSource);
        enforceSubscriptionPermission();
        validateSubId(subId);
        if (displayName == null) {
            nameToSet = this.mContext.getString(17039374);
        } else {
            nameToSet = displayName;
        }
        ContentValues value = new ContentValues(1);
        value.put("display_name", nameToSet);
        if (nameSource >= 0) {
            logd("Set nameSource=" + nameSource);
            value.put("name_source", Long.valueOf(nameSource));
        }
        logd("[setDisplayName]- mDisplayName:" + nameToSet + " set");
        int result = this.mContext.getContentResolver().update(SubscriptionManager.CONTENT_URI, value, "_id=" + Long.toString(subId), null);
        broadcastSimInfoContentChanged(subId, "display_name", -100, nameToSet);
        return result;
    }

    public int setDisplayNumber(String number, long subId) {
        logd("[setDisplayNumber]+ number:" + number + " subId:" + subId);
        enforceSubscriptionPermission();
        validateSubId(subId);
        int result = 0;
        int phoneId = getPhoneId(subId);
        if (number == null || phoneId < 0 || phoneId >= TelephonyManager.getDefault().getPhoneCount()) {
            logd("[setDispalyNumber]- fail");
            return -1;
        }
        ContentValues contentValues = new ContentValues(1);
        contentValues.put("number", number);
        logd("[setDisplayNumber]- number:" + number + " set");
        Phone phone = sProxyPhones[phoneId];
        String alphaTag = TelephonyManager.getDefault().getLine1AlphaTagForSubscriber(subId);
        synchronized (this.mLock) {
            this.mSuccess = false;
            phone.setLine1Number(alphaTag, number, this.mHandler.obtainMessage(1));
            try {
                this.mLock.wait();
            } catch (InterruptedException e) {
                loge("interrupted while trying to write MSISDN");
            }
        }
        if (this.mSuccess) {
            result = this.mContext.getContentResolver().update(SubscriptionManager.CONTENT_URI, contentValues, "_id=" + Long.toString(subId), null);
            logd("[setDisplayNumber]- update result :" + result);
            broadcastSimInfoContentChanged(subId, "number", -100, number);
        }
        return result;
    }

    public int setDisplayNumberFormat(int format, long subId) {
        logd("[setDisplayNumberFormat]+ format:" + format + " subId:" + subId);
        enforceSubscriptionPermission();
        validateSubId(subId);
        if (format < 0) {
            logd("[setDisplayNumberFormat]- fail, return -1");
            return -1;
        }
        ContentValues value = new ContentValues(1);
        value.put("display_number_format", Integer.valueOf(format));
        logd("[setDisplayNumberFormat]- format:" + format + " set");
        int result = this.mContext.getContentResolver().update(SubscriptionManager.CONTENT_URI, value, "_id=" + Long.toString(subId), null);
        broadcastSimInfoContentChanged(subId, "display_number_format", format, "N/A");
        return result;
    }

    public int setDataRoaming(int roaming, long subId) {
        logd("[setDataRoaming]+ roaming:" + roaming + " subId:" + subId);
        enforceSubscriptionPermission();
        validateSubId(subId);
        if (roaming < 0) {
            logd("[setDataRoaming]- fail");
            return -1;
        }
        ContentValues value = new ContentValues(1);
        value.put("data_roaming", Integer.valueOf(roaming));
        logd("[setDataRoaming]- roaming:" + roaming + " set");
        int result = this.mContext.getContentResolver().update(SubscriptionManager.CONTENT_URI, value, "_id=" + Long.toString(subId), null);
        broadcastSimInfoContentChanged(subId, "data_roaming", roaming, "N/A");
        return result;
    }

    public int setMccMnc(String mccMnc, long subId) {
        int mcc = 0;
        int mnc = 0;
        try {
            mcc = Integer.parseInt(mccMnc.substring(0, 3));
            mnc = Integer.parseInt(mccMnc.substring(3));
        } catch (NumberFormatException e) {
            logd("[setMccMnc] - couldn't parse mcc/mnc: " + mccMnc);
        }
        logd("[setMccMnc]+ mcc/mnc:" + mcc + "/" + mnc + " subId:" + subId);
        ContentValues value = new ContentValues(2);
        value.put(Carriers.MCC, Integer.valueOf(mcc));
        value.put(Carriers.MNC, Integer.valueOf(mnc));
        int result = this.mContext.getContentResolver().update(SubscriptionManager.CONTENT_URI, value, "_id=" + Long.toString(subId), null);
        broadcastSimInfoContentChanged(subId, Carriers.MCC, mcc, null);
        return result;
    }

    public int getSlotId(long subId) {
        if (subId == Long.MAX_VALUE) {
            subId = getDefaultSubId();
        }
        if (!SubscriptionManager.isValidSubId(subId)) {
            logd("[getSlotId]- subId invalid");
            return -1000;
        } else if (mSimInfo.size() == 0) {
            logd("[getSlotId]- size == 0, return SIM_NOT_INSERTED instead");
            return -1;
        } else {
            for (Entry<Integer, Long> entry : mSimInfo.entrySet()) {
                int sim = ((Integer) entry.getKey()).intValue();
                if (subId == ((Long) entry.getValue()).longValue()) {
                    return sim;
                }
            }
            logd("[getSlotId]- return fail");
            return -1000;
        }
    }

    @Deprecated
    public long[] getSubId(int slotId) {
        if (slotId == Integer.MAX_VALUE) {
            logd("[getSubId]- default slotId");
            slotId = getSlotId(getDefaultSubId());
        }
        long[] DUMMY_VALUES = new long[]{(long) (-1 - slotId), (long) (-1 - slotId)};
        if (!SubscriptionManager.isValidSlotId(slotId)) {
            logd("[getSubId]- invalid slotId");
            return null;
        } else if (slotId < 0) {
            logd("[getSubId]- slotId < 0, return dummy instead");
            return DUMMY_VALUES;
        } else if (mSimInfo.size() == 0) {
            return DUMMY_VALUES;
        } else {
            ArrayList<Long> subIds = new ArrayList();
            for (Entry<Integer, Long> entry : mSimInfo.entrySet()) {
                int slot = ((Integer) entry.getKey()).intValue();
                long sub = ((Long) entry.getValue()).longValue();
                if (slotId == slot) {
                    subIds.add(Long.valueOf(sub));
                }
            }
            int numSubIds = subIds.size();
            if (numSubIds == 0) {
                logd("[getSubId]- numSubIds == 0, return dummy instead");
                return DUMMY_VALUES;
            }
            long[] subIdArr = new long[numSubIds];
            for (int i = 0; i < numSubIds; i++) {
                subIdArr[i] = ((Long) subIds.get(i)).longValue();
            }
            return subIdArr;
        }
    }

    public int getPhoneId(long subId) {
        if (subId == Long.MAX_VALUE) {
            subId = getDefaultSubId();
            logdl("[getPhoneId] asked for default subId=" + subId);
        }
        if (!SubscriptionManager.isValidSubId(subId)) {
            logdl("[getPhoneId]- invalid subId return=-1000");
            return -1000;
        } else if (subId < 0) {
            return (int) (-1 - subId);
        } else {
            int phoneId;
            if (mSimInfo.size() == 0) {
                phoneId = mDefaultPhoneId;
                logdl("[getPhoneId]- no sims, returning default phoneId=" + phoneId);
                return phoneId;
            }
            for (Entry<Integer, Long> entry : mSimInfo.entrySet()) {
                int sim = ((Integer) entry.getKey()).intValue();
                if (subId == ((Long) entry.getValue()).longValue()) {
                    return sim;
                }
            }
            phoneId = mDefaultPhoneId;
            logdl("[getPhoneId]- subId=" + subId + " not found return default phoneId=" + phoneId);
            return phoneId;
        }
    }

    public int clearSubInfo() {
        enforceSubscriptionPermission();
        logd("[clearSubInfo]+");
        int size = mSimInfo.size();
        if (size == 0) {
            logdl("[clearSubInfo]- no simInfo size=" + size);
            return 0;
        }
        mSimInfo.clear();
        logdl("[clearSubInfo]- clear size=" + size);
        return size;
    }

    private static int[] setSimResource(int type) {
        switch (type) {
            case 0:
                return new int[]{17303478, 17303480, 17303479, 17303481};
            case 1:
                return new int[]{17303482, 17303484, 17303483, 17303485};
            default:
                return null;
        }
    }

    private void logvl(String msg) {
        logv(msg);
        this.mLocalLog.log(msg);
    }

    private void logv(String msg) {
        Rlog.v(LOG_TAG, msg);
    }

    private void logdl(String msg) {
        logd(msg);
        this.mLocalLog.log(msg);
    }

    private static void slogd(String msg) {
        Rlog.d(LOG_TAG, msg);
    }

    private void logd(String msg) {
        Rlog.d(LOG_TAG, msg);
    }

    private void logel(String msg) {
        loge(msg);
        this.mLocalLog.log(msg);
    }

    private void loge(String msg) {
        Rlog.e(LOG_TAG, msg);
    }

    @Deprecated
    public long getDefaultSubId() {
        return mDefaultVoiceSubId;
    }

    public void setDefaultSmsSubId(long subId) {
        if (subId == Long.MAX_VALUE) {
            throw new RuntimeException("setDefaultSmsSubId called with DEFAULT_SUB_ID");
        }
        logdl("[setDefaultSmsSubId] subId=" + subId);
        Global.putLong(this.mContext.getContentResolver(), "multi_sim_sms", subId);
        broadcastDefaultSmsSubIdChanged(subId);
    }

    private void broadcastDefaultSmsSubIdChanged(long subId) {
        logdl("[broadcastDefaultSmsSubIdChanged] subId=" + subId);
        Intent intent = new Intent("android.intent.action.ACTION_DEFAULT_SMS_SUBSCRIPTION_CHANGED");
        intent.addFlags(536870912);
        intent.putExtra("subscription", subId);
        this.mContext.sendStickyBroadcastAsUser(intent, UserHandle.ALL);
    }

    public long getDefaultSmsSubId() {
        return Global.getLong(this.mContext.getContentResolver(), "multi_sim_sms", -1000);
    }

    public void setDefaultVoiceSubId(long subId) {
        if (subId == Long.MAX_VALUE) {
            throw new RuntimeException("setDefaultVoiceSubId called with DEFAULT_SUB_ID");
        }
        logdl("[setDefaultVoiceSubId] subId=" + subId);
        Global.putLong(this.mContext.getContentResolver(), "multi_sim_voice_call", subId);
        if (TelephonyManager.getDefault().getPhoneCount() > 1 && (MODEL_J || MODEL_H || MODEL_K || MODEL_T)) {
            setDefaultVoiceSlotId(getSlotId(subId));
        }
        broadcastDefaultVoiceSubIdChanged(subId);
    }

    private void broadcastDefaultVoiceSubIdChanged(long subId) {
        logdl("[broadcastDefaultVoiceSubIdChanged] subId=" + subId);
        Intent intent = new Intent("android.intent.action.ACTION_DEFAULT_VOICE_SUBSCRIPTION_CHANGED");
        intent.addFlags(536870912);
        intent.putExtra("subscription", subId);
        this.mContext.sendStickyBroadcastAsUser(intent, UserHandle.ALL);
    }

    public long getDefaultVoiceSubId() {
        return Global.getLong(this.mContext.getContentResolver(), "multi_sim_voice_call", -1000);
    }

    public long getDefaultDataSubId() {
        long subId = Global.getLong(this.mContext.getContentResolver(), "multi_sim_data_call", -1000);
        if (!"DCGG".equals("DGG") && !"DCGS".equals("DGG") && !"DGG".equals("DGG") && !"DCGGS".equals("DGG")) {
            return subId;
        }
        if (!MODEL_J && !MODEL_H && !MODEL_K && !MODEL_T) {
            return subId;
        }
        int slot = getDefaultDataSlotId();
        long prefSub = getSubIdUsingPhoneId(slot);
        if (!SubscriptionManager.isValidSlotId(slot) || subId == prefSub) {
            return subId;
        }
        if (mSimInfo.get(Integer.valueOf(slot)) != null && SubscriptionManager.isValidSubId(((Long) mSimInfo.get(Integer.valueOf(slot))).longValue())) {
            logd("the previous card is not in its slot, previous subid = " + subId + ", new sub id = " + prefSub);
            Global.putLong(this.mContext.getContentResolver(), "multi_sim_data_call", prefSub);
        }
        return prefSub;
    }

    public void setDefaultDataSubId(long subId) {
        if (subId == Long.MAX_VALUE) {
            throw new RuntimeException("setDefaultDataSubId called with DEFAULT_SUB_ID");
        }
        logdl("[setDefaultDataSubId] subId=" + subId);
        if ("CG".equals("DGG") || "GG".equals("DGG") || ("DCGGS".equals("DGG") && "GSM".equals(SystemProperties.get("gsm.sim.selectnetwork", "CDMA")))) {
            SystemProperties.set("ril.datacross.slotid", Integer.toString(-1));
            int datacross = SystemProperties.getInt("ril.datacross.slotid", -1);
            if (this.mCM != null) {
                logdl("[setDefaultDataSubId] mCM.getState()=" + this.mCM.getState() + ", datacross=" + datacross);
            }
            Intent intent;
            if (this.mCM != null && this.mCM.getState() != State.IDLE) {
                intent = new Intent("android.net.conn.SwitchDataNetworkDuringVoiceCall");
                intent.addFlags(536870912);
                intent.putExtra("subscription", subId);
                this.mContext.sendStickyBroadcastAsUser(intent, UserHandle.ALL);
                logdl("setDefaultDataSubId(), Broadcast SwitchDataNetworkDuringVoiceCall");
                return;
            } else if (datacross != -1) {
                intent = new Intent("android.net.conn.SwitchDataNetworkDuringMMS");
                intent.addFlags(536870912);
                intent.putExtra("subscription", subId);
                this.mContext.sendStickyBroadcastAsUser(intent, UserHandle.ALL);
                logdl("setDefaultDataSubId(), Broadcast SwitchDataNetworkDuringMMS");
                return;
            }
        }
        this.mCalledSetDataAllowed = true;
        if ("DCGG".equals("DGG") || "DGG".equals("DGG") || "DCGS".equals("DGG") || "DCGGS".equals("DGG")) {
            DctController.getInstance().setDataSubId(subId);
            return;
        }
        Phone phone = sProxyPhones[getPhoneId(subId)].getActivePhone();
        if ("CG".equals("DGG")) {
            Message onCompleted = this.mHandler.obtainMessage(2);
            onCompleted.arg1 = (int) subId;
            onCompleted.arg2 = (int) getDefaultDataSubId();
            ((PhoneBase) phone).mDcTracker.setDataAllowed(true, onCompleted);
        } else {
            ((PhoneBase) phone).mDcTracker.setDataAllowed(true, null);
        }
        Global.putLong(this.mContext.getContentResolver(), "multi_sim_data_call", subId);
        setDefaultDataSlotId(getSlotId(subId));
        if ("CG".equals("DGG")) {
            broadcastDefaultDataSubIdChangeStarted(subId);
            return;
        }
        broadcastDefaultDataSubIdChanged(subId);
        updateAllDataConnectionTrackers();
    }

    public void setDefaultDataSubIdForMMS(long subId) {
        logdl("setDefaultDataSubIdForMMS: subId=" + subId + " FIXME NOP right now");
        DctController.getInstance().setDataSubIdForMMS(subId);
    }

    public boolean isSMSPromptEnabled() {
        int value = 0;
        try {
            value = Global.getInt(this.mContext.getContentResolver(), "multi_sim_sms_prompt");
        } catch (SettingNotFoundException e) {
            loge("Settings Exception Reading Dual Sim SMS Prompt Values");
        }
        return value != 0;
    }

    public void setSMSPromptEnabled(boolean enabled) {
        Global.putInt(this.mContext.getContentResolver(), "multi_sim_sms_prompt", !enabled ? 0 : 1);
        logd("setSMSPromptOption to " + enabled);
    }

    private void updateAllDataConnectionTrackers() {
        int len = sProxyPhones.length;
        logdl("[updateAllDataConnectionTrackers] sProxyPhones.length=" + len);
        for (int phoneId = 0; phoneId < len; phoneId++) {
            logdl("[updateAllDataConnectionTrackers] phoneId=" + phoneId);
            sProxyPhones[phoneId].updateDataConnectionTracker();
        }
    }

    private void broadcastDefaultDataSubIdChanged(long subId) {
        logdl("[broadcastDefaultDataSubIdChanged] subId=" + subId);
        Intent intent = new Intent("android.intent.action.ACTION_DEFAULT_DATA_SUBSCRIPTION_CHANGED");
        intent.addFlags(536870912);
        intent.putExtra("subscription", subId);
        this.mContext.sendStickyBroadcastAsUser(intent, UserHandle.ALL);
    }

    private void broadcastDefaultDataSubIdChangeStarted(long subId) {
        logdl("[broadcastDefaultDataSubIdChangeStarted] subId=" + subId);
        Intent intent = new Intent("android.intent.action.ACTION_DEFAULT_DATA_SUBSCRIPTION_CHANGE_STARTED");
        intent.addFlags(536870912);
        intent.putExtra("subscription", subId);
        this.mContext.sendStickyBroadcastAsUser(intent, UserHandle.ALL);
    }

    public void setDefaultSubId(long subId) {
        if (subId == Long.MAX_VALUE) {
            throw new RuntimeException("setDefaultSubId called with DEFAULT_SUB_ID");
        }
        logdl("[setDefaultSubId] subId=" + subId);
        if (SubscriptionManager.isValidSubId(subId)) {
            int phoneId = getPhoneId(subId);
            if (phoneId < 0) {
                return;
            }
            if (phoneId < TelephonyManager.getDefault().getPhoneCount() || TelephonyManager.getDefault().getSimCount() == 1) {
                logdl("[setDefaultSubId] set mDefaultVoiceSubId=" + subId);
                mDefaultVoiceSubId = subId;
                MccTable.updateMccMncConfiguration(this.mContext, TelephonyManager.getDefault().getSimOperator((long) phoneId), false);
                Intent intent = new Intent("android.intent.action.ACTION_DEFAULT_SUBSCRIPTION_CHANGED");
                intent.addFlags(536870912);
                SubscriptionManager.putPhoneIdAndSubIdExtra(intent, phoneId, subId);
                this.mContext.sendStickyBroadcastAsUser(intent, UserHandle.ALL);
            }
        }
    }

    public void clearDefaultsForInactiveSubIds() {
        List<SubInfoRecord> records = getActiveSubInfoList();
        logdl("[clearDefaultsForInactiveSubIds] records: " + records);
        if (shouldDefaultBeCleared(records, getDefaultDataSubId())) {
            logd("[clearDefaultsForInactiveSubIds] clearing default data sub id");
            setDefaultDataSubId(-1000);
        }
        if (shouldDefaultBeCleared(records, getDefaultSmsSubId())) {
            logdl("[clearDefaultsForInactiveSubIds] clearing default sms sub id");
            setDefaultSmsSubId(-1000);
        }
        if (shouldDefaultBeCleared(records, getDefaultVoiceSubId())) {
            logdl("[clearDefaultsForInactiveSubIds] clearing default voice sub id");
            setDefaultVoiceSubId(-1000);
        }
    }

    private boolean shouldDefaultBeCleared(List<SubInfoRecord> records, long subId) {
        logdl("[shouldDefaultBeCleared: subId] " + subId);
        if (records == null) {
            logdl("[shouldDefaultBeCleared] return true no records subId=" + subId);
            return true;
        } else if (subId != -1001 || records.size() <= 1) {
            for (SubInfoRecord record : records) {
                logdl("[shouldDefaultBeCleared] Record.subId: " + record.subId);
                if (record.subId == subId) {
                    logdl("[shouldDefaultBeCleared] return false subId is active, subId=" + subId);
                    return false;
                }
            }
            logdl("[shouldDefaultBeCleared] return true not active subId=" + subId);
            return true;
        } else {
            logdl("[shouldDefaultBeCleared] return false only one subId, subId=" + subId);
            return false;
        }
    }

    public long getSubIdUsingPhoneId(int phoneId) {
        long[] subIds = getSubId(phoneId);
        if (subIds == null || subIds.length == 0) {
            return -1000;
        }
        return subIds[0];
    }

    public long[] getSubIdUsingSlotId(int slotId) {
        return getSubId(slotId);
    }

    public List<SubInfoRecord> getSubInfoUsingSlotIdWithCheck(int slotId, boolean needCheck) {
        Cursor cursor;
        Throwable th;
        logd("[getSubInfoUsingSlotIdWithCheck]+ slotId:" + slotId);
        enforceSubscriptionPermission();
        if (slotId == Integer.MAX_VALUE) {
            slotId = getSlotId(getDefaultSubId());
        }
        if (!SubscriptionManager.isValidSlotId(slotId)) {
            logd("[getSubInfoUsingSlotIdWithCheck]- invalid slotId");
            return null;
        } else if (!needCheck || isSubInfoReady()) {
            cursor = this.mContext.getContentResolver().query(SubscriptionManager.CONTENT_URI, null, "sim_id=?", new String[]{String.valueOf(slotId)}, null);
            ArrayList<SubInfoRecord> subList = null;
            if (cursor != null) {
                ArrayList<SubInfoRecord> subList2 = null;
                while (cursor.moveToNext()) {
                    try {
                        SubInfoRecord subInfo = getSubInfoRecord(cursor);
                        if (subInfo != null) {
                            if (subList2 == null) {
                                subList = new ArrayList();
                            } else {
                                subList = subList2;
                            }
                            try {
                                subList.add(subInfo);
                            } catch (Throwable th2) {
                                th = th2;
                            }
                        } else {
                            subList = subList2;
                        }
                        subList2 = subList;
                    } catch (Throwable th3) {
                        th = th3;
                        subList = subList2;
                    }
                }
                subList = subList2;
            }
            if (cursor != null) {
                cursor.close();
            }
            logd("[getSubInfoUsingSlotId]- null info return");
            return subList;
        } else {
            logd("[getSubInfoUsingSlotIdWithCheck]- not ready");
            return null;
        }
        if (cursor != null) {
            cursor.close();
        }
        throw th;
    }

    private void validateSubId(long subId) {
        logd("validateSubId subId: " + subId);
        if (!SubscriptionManager.isValidSubId(subId)) {
            throw new RuntimeException("Invalid sub id passed as parameter");
        } else if (subId == Long.MAX_VALUE) {
            throw new RuntimeException("Default sub id passed as parameter");
        }
    }

    public void updatePhonesAvailability(PhoneProxy[] phones) {
        sProxyPhones = phones;
    }

    public boolean isVoicePromptEnabled() {
        int value = 0;
        try {
            value = Global.getInt(this.mContext.getContentResolver(), "multi_sim_voice_prompt");
        } catch (SettingNotFoundException e) {
            loge("Settings Exception Reading Dual Sim Voice Prompt Values");
        }
        return value != 0;
    }

    public void setVoicePromptEnabled(boolean enabled) {
        int value;
        if (enabled) {
            value = 1;
        } else {
            value = 0;
        }
        long subId = -1000;
        Global.putInt(this.mContext.getContentResolver(), "multi_sim_voice_prompt", value);
        if (!enabled) {
            subId = getDefaultVoiceSubId();
        }
        broadcastDefaultVoiceSubIdChanged(subId);
        logd("setVoicePromptOption to " + enabled);
    }

    public void activateSubId(long subId) {
        if (getSubState(subId) == 1) {
            logd("activateSubId: subscription already active, subId = " + subId);
            return;
        }
        int slotId = getSlotId(subId);
        if (SubscriptionManager.isValidSlotId(slotId)) {
            SubscriptionHelper.getInstance().setUiccSubscription(slotId, 1);
        } else {
            logd("[activateSubId]- invalid slotId");
        }
    }

    public void deactivateSubId(long subId) {
        if (getSubState(subId) == 0) {
            logd("activateSubId: subscription already deactivated, subId = " + subId);
            return;
        }
        int slotId = getSlotId(subId);
        if (SubscriptionManager.isValidSlotId(slotId)) {
            SubscriptionHelper.getInstance().setUiccSubscription(slotId, 0);
        } else {
            logd("[deactivateSubId]- invalid slotId");
        }
    }

    public int setSubState(long subId, int subStatus) {
        logd("setSubState, subStatus: " + subStatus + " subId: " + subId);
        ContentValues value = new ContentValues(1);
        value.put("sub_state", Integer.valueOf(subStatus));
        int result = this.mContext.getContentResolver().update(SubscriptionManager.CONTENT_URI, value, "_id=" + Long.toString(subId), null);
        if (subStatus == 0) {
            updateUserPrefs();
        }
        broadcastSimInfoContentChanged(subId, "sub_state", subStatus, "N/A");
        return result;
    }

    public int getSubState(long subId) {
        SubInfoRecord subInfo = getSubInfoForSubscriber(subId);
        if (subInfo == null || subInfo.slotId < 0) {
            return 0;
        }
        return subInfo.mStatus;
    }

    public void updateUserPrefs() {
        logd("[updateUserPrefs]+");
        List<SubInfoRecord> subInfoList = getActiveSubInfoList();
        int mActCount = 0;
        SubInfoRecord mNextActivatedSub = null;
        if (subInfoList == null) {
            logd("updateUserPrefs: subscription are not avaiable ");
            return;
        }
        for (SubInfoRecord subInfo : subInfoList) {
            if (getSubState(subInfo.subId) == 1) {
                mActCount++;
                if (mNextActivatedSub == null) {
                    mNextActivatedSub = subInfo;
                }
            }
        }
        if (mActCount < 2) {
            setSMSPromptEnabled(false);
            setVoicePromptEnabled(false);
        }
        if (mNextActivatedSub != null) {
            if (getSubState(getDefaultDataSubId()) == 0) {
                setDefaultDataSubId(mNextActivatedSub.subId);
            }
            if (getSubState(getDefaultVoiceSubId()) == 0 && !isVoicePromptEnabled()) {
                setDefaultVoiceSubId(mNextActivatedSub.subId);
            }
            if (getSubState(getDefaultSmsSubId()) == 0 && !isSMSPromptEnabled()) {
                setDefaultSmsSubId(mNextActivatedSub.subId);
            }
            this.mCalledUpdateUserPrefs = true;
        }
    }

    public long[] getActiveSubIdList() {
        Set<Entry<Integer, Long>> simInfoSet = mSimInfo.entrySet();
        logdl("[getActiveSubIdList] simInfoSet=" + simInfoSet);
        long[] subIdArr = new long[simInfoSet.size()];
        int i = 0;
        for (Entry<Integer, Long> entry : simInfoSet) {
            subIdArr[i] = ((Long) entry.getValue()).longValue();
            i++;
        }
        logdl("[getActiveSubIdList] X subIdArr.length=" + subIdArr.length);
        return subIdArr;
    }

    public void refreshSubInfo() {
        logd("[refreshSubInfo]+ defaultSubId system:" + getDefaultSubId() + " voice:" + getDefaultVoiceSubId() + " data:" + getDefaultDataSubId() + " sms:" + getDefaultSmsSubId() + " / defaultSlotId voice:" + getDefaultVoiceSlotId() + " data:" + getDefaultDataSlotId());
        if (mAllSubInfoList == null) {
            mAllSubInfoList = new ArrayList();
        }
        if (mActiveSubInfoList == null) {
            mActiveSubInfoList = new ArrayList();
        }
        mAllSubInfoList = getSubInfo(null, null);
        mActiveSubInfoList = getSubInfo("sim_id!=-1000", null);
        if (isSubInfoReady()) {
            if (MODEL_J || MODEL_H || MODEL_K || MODEL_T) {
                setDefaultVoiceSubIdUsingSlotId(getDefaultVoiceSlotId());
                int slot = getDefaultDataSlotId();
                if (SubscriptionManager.isValidSlotId(slot) && mSimInfo.get(Integer.valueOf(slot)) != null && SubscriptionManager.isValidSubId(((Long) mSimInfo.get(Integer.valueOf(slot))).longValue()) && !this.mCalledSetDataSubBySlot) {
                    setDefaultDataSubIdUsingSlotId(slot);
                    this.mCalledSetDataSubBySlot = true;
                }
            }
            if (!this.mCalledUpdateUserPrefs) {
                updateUserPrefs();
            }
            if (!this.mCalledSetDataAllowed && !"DCGG".equals("DGG") && !"DCGS".equals("DGG") && !"DGG".equals("DGG") && !"DCGGS".equals("DGG")) {
                long subId = getDefaultDataSubId();
                if (SubscriptionManager.isValidSubId(subId)) {
                    setDefaultDataSubId(subId);
                }
            }
        }
    }

    private void setDefaultVoiceSubIdUsingSlotId(int slotId) {
        long defaultSubId = getDefaultVoiceSubId();
        long subId = getSubIdUsingPhoneId(slotId);
        if (!SubscriptionManager.isValidSlotId(slotId) || !SubscriptionManager.isValidSubId(subId)) {
            logdl("[setDefaultVoiceSubIdUsingSlotId] defaultSubId=" + defaultSubId + "/ subId:" + subId + ", slotId:" + slotId);
        } else if (subId != defaultSubId) {
            logdl("[setDefaultVoiceSubIdUsingSlotId] subId=" + subId + ", slotId:" + slotId + " / defaultSubId:" + defaultSubId);
            setDefaultVoiceSubId(subId);
        }
    }

    private void setDefaultDataSubIdUsingSlotId(int slotId) {
        long defaultSubId = getDefaultDataSubId();
        long subId = getSubIdUsingPhoneId(slotId);
        if (!SubscriptionManager.isValidSlotId(slotId) || !SubscriptionManager.isValidSubId(subId)) {
            logdl("[setDefaultDataSubIdUsingSlotId] defaultSubId=" + defaultSubId + "/ subId:" + subId + ", slotId:" + slotId);
        } else if (subId != defaultSubId) {
            logdl("[setDefaultDataSubIdUsingSlotId] subId=" + subId + ", slotId:" + slotId + " / defaultSubId:" + defaultSubId);
            setDefaultDataSubId(subId);
        }
    }

    private int getDefaultVoiceSlotId() {
        return Global.getInt(this.mContext.getContentResolver(), "multi_sim_voice_call_slot", -1000);
    }

    private void setDefaultVoiceSlotId(int slotId) {
        Global.putInt(this.mContext.getContentResolver(), "multi_sim_voice_call_slot", slotId);
    }

    private int getDefaultDataSlotId() {
        return Global.getInt(this.mContext.getContentResolver(), "multi_sim_data_call_slot", -1000);
    }

    private void setDefaultDataSlotId(int slotId) {
        Global.putInt(this.mContext.getContentResolver(), "multi_sim_data_call_slot", slotId);
    }

    private static void printStackTrace(String msg) {
        RuntimeException re = new RuntimeException();
        slogd("StackTrace - " + msg);
        boolean first = true;
        for (StackTraceElement ste : re.getStackTrace()) {
            if (first) {
                first = false;
            } else {
                slogd(ste.toString());
            }
        }
    }

    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        this.mContext.enforceCallingOrSelfPermission("android.permission.DUMP", "Requires DUMP");
        pw.println("SubscriptionController:");
        pw.println(" defaultSubId=" + getDefaultSubId());
        pw.println(" defaultDataSubId=" + getDefaultDataSubId());
        pw.println(" defaultVoiceSubId=" + getDefaultVoiceSubId());
        pw.println(" defaultSmsSubId=" + getDefaultSmsSubId());
        pw.println(" defaultDataPhoneId=" + SubscriptionManager.getDefaultDataPhoneId());
        pw.println(" defaultVoicePhoneId=" + SubscriptionManager.getDefaultVoicePhoneId());
        pw.println(" defaultSmsPhoneId=" + SubscriptionManager.getDefaultSmsPhoneId());
        pw.flush();
        for (Entry<Integer, Long> entry : mSimInfo.entrySet()) {
            pw.println(" mSimInfo[" + entry.getKey() + "]: subId=" + entry.getValue());
        }
        pw.flush();
        pw.println("++++++++++++++++++++++++++++++++");
        List<SubInfoRecord> sirl = getActiveSubInfoList();
        if (sirl != null) {
            pw.println(" ActiveSubInfoList:");
            for (SubInfoRecord entry2 : sirl) {
                pw.println("  " + entry2.toString());
            }
        } else {
            pw.println(" ActiveSubInfoList: is null");
        }
        pw.flush();
        pw.println("++++++++++++++++++++++++++++++++");
        sirl = getAllSubInfoList();
        if (sirl != null) {
            pw.println(" AllSubInfoList:");
            for (SubInfoRecord entry22 : sirl) {
                pw.println("  " + entry22.toString());
            }
        } else {
            pw.println(" AllSubInfoList: is null");
        }
        pw.flush();
        pw.println("++++++++++++++++++++++++++++++++");
        this.mLocalLog.dump(fd, pw, args);
        pw.flush();
        pw.println("++++++++++++++++++++++++++++++++");
        pw.flush();
    }
}
