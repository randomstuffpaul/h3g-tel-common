package com.android.internal.telephony;

import android.app.ActivityManagerNative;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.AsyncResult;
import android.os.Handler;
import android.os.Message;
import android.os.SystemProperties;
import android.provider.Settings.System;
import android.telephony.Rlog;
import android.telephony.SubInfoRecord;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import com.android.internal.telephony.cat.AppInterface;
import com.android.internal.telephony.uicc.IccCardApplicationStatus.AppType;
import com.android.internal.telephony.uicc.IccCardStatus.CardState;
import com.android.internal.telephony.uicc.IccConstants;
import com.android.internal.telephony.uicc.IccFileHandler;
import com.android.internal.telephony.uicc.IccRecords;
import com.android.internal.telephony.uicc.IccUtils;
import com.android.internal.telephony.uicc.SpnOverride;
import com.android.internal.telephony.uicc.UiccCard;
import com.android.internal.telephony.uicc.UiccCardApplication;
import com.android.internal.telephony.uicc.UiccController;
import com.samsung.android.telephony.MultiSimManager;
import java.util.List;

public class SubInfoRecordUpdater extends Handler {
    private static final int EVENT_ICCID_READY = 4;
    private static final int EVENT_ICC_CHANGED = 2;
    private static final int EVENT_OFFSET = 8;
    private static final int EVENT_QUERY_ICCID_DONE = 1;
    private static final String ICCID_STRING_FOR_INVALID_ICCID = "00000000000000000000";
    private static final String ICCID_STRING_FOR_NO_SIM = "";
    private static final String LOG_TAG = "SUB";
    private static final int PROJECT_SIM_NUM = TelephonyManager.getDefault().getPhoneCount();
    static final boolean SHIP_BUILD = "true".equals(SystemProperties.get("ro.product_ship", "false"));
    public static final int SIM_CHANGED = -1;
    public static final int SIM_NEW = -2;
    public static final int SIM_NOT_CHANGE = 0;
    public static final int SIM_NOT_INSERT = -99;
    public static final int SIM_REPOSITION = -3;
    public static final int STATUS_NO_SIM_INSERTED = 0;
    public static final int STATUS_SIM1_INSERTED = 1;
    public static final int STATUS_SIM2_INSERTED = 2;
    public static final int STATUS_SIM3_INSERTED = 4;
    public static final int STATUS_SIM4_INSERTED = 8;
    private static boolean[] isSimReset = new boolean[PROJECT_SIM_NUM];
    private static UiccController mUiccController = null;
    private static CardState[] sCardState = new CardState[PROJECT_SIM_NUM];
    private static CommandsInterface[] sCi;
    private static Context sContext = null;
    private static IccFileHandler[] sFh = new IccFileHandler[PROJECT_SIM_NUM];
    private static String[] sIccId = new String[PROJECT_SIM_NUM];
    private static int[] sInsertSimState = new int[PROJECT_SIM_NUM];
    private static boolean sNeedUpdate = true;
    private static Phone[] sPhone;
    private static TelephonyManager sTelephonyMgr = null;
    private IccRecords[] mIccRecords = new IccRecords[PROJECT_SIM_NUM];
    private boolean mIsSystemShutdown = false;
    private final BroadcastReceiver sReceiver = new C00321();

    class C00321 extends BroadcastReceiver {
        C00321() {
        }

        public void onReceive(Context context, Intent intent) {
            SubInfoRecordUpdater.logd("[Receiver]+");
            String action = intent.getAction();
            SubInfoRecordUpdater.logd("Action: " + action);
            int slotId;
            if (action.equals("android.intent.action.SIM_STATE_CHANGED")) {
                if (SubInfoRecordUpdater.this.mIsSystemShutdown) {
                    SubInfoRecordUpdater.logd("mIsSystemShutdown: " + SubInfoRecordUpdater.this.mIsSystemShutdown + ", ignore " + action);
                    return;
                }
                String simStatus = intent.getStringExtra(IccCard.INTENT_KEY_ICC_STATE);
                slotId = intent.getIntExtra("slot", -1000);
                SubInfoRecordUpdater.logd("slotId: " + slotId + " simStatus: " + simStatus);
                if (slotId == -1000) {
                    return;
                }
                if ("READY".equals(simStatus) || "LOCKED".equals(simStatus)) {
                    if (SubInfoRecordUpdater.sIccId[slotId] != null && SubInfoRecordUpdater.sIccId[slotId].equals(SubInfoRecordUpdater.ICCID_STRING_FOR_NO_SIM)) {
                        SubInfoRecordUpdater.logd("SIM" + (slotId + 1) + " hot plug in");
                        SubInfoRecordUpdater.sIccId[slotId] = null;
                        SubInfoRecordUpdater.sNeedUpdate = true;
                    }
                    SubInfoRecordUpdater.this.queryIccId(slotId);
                } else if ("LOADED".equals(simStatus)) {
                    SubInfoRecordUpdater.this.queryIccId(slotId);
                    if (SubInfoRecordUpdater.sTelephonyMgr == null) {
                        SubInfoRecordUpdater.sTelephonyMgr = TelephonyManager.from(SubInfoRecordUpdater.sContext);
                    }
                    long subId = intent.getLongExtra("subscription", -1000);
                    if (SubscriptionManager.isValidSubId(subId)) {
                        String msisdn = TelephonyManager.getDefault().getLine1NumberForSubscriber(subId);
                        ContentResolver contentResolver = SubInfoRecordUpdater.sContext.getContentResolver();
                        if (msisdn != null) {
                            ContentValues number = new ContentValues(1);
                            number.put("number", msisdn);
                            contentResolver.update(SubscriptionManager.CONTENT_URI, number, "_id=" + Long.toString(subId), null);
                        }
                        SubInfoRecord subInfo = SubscriptionManager.getSubInfoForSubscriber(subId);
                        if (!(subInfo == null || subInfo.nameSource == 2)) {
                            String nameToSet;
                            SpnOverride mSpnOverride = new SpnOverride();
                            String CarrierName = TelephonyManager.getDefault().getSimOperator(subId);
                            SubInfoRecordUpdater.logd("CarrierName = " + CarrierName);
                            if (mSpnOverride.containsCarrier(CarrierName)) {
                                nameToSet = mSpnOverride.getSpn(CarrierName, ((PhoneProxy) SubInfoRecordUpdater.sPhone[slotId]).getSubscriberId()) + " 0" + Integer.toString(slotId + 1);
                                SubInfoRecordUpdater.logd("Found, name = " + nameToSet);
                            } else {
                                nameToSet = "SUB 0" + Integer.toString(slotId + 1);
                                SubInfoRecordUpdater.logd("Not found, name = " + nameToSet);
                            }
                            ContentValues name = new ContentValues(1);
                            name.put("display_name", nameToSet);
                            contentResolver.update(SubscriptionManager.CONTENT_URI, name, "_id=" + Long.toString(subId), null);
                        }
                    } else {
                        SubInfoRecordUpdater.logd("[Receiver] Invalid subId, could not update ContentResolver");
                    }
                } else if ("ABSENT".equals(simStatus)) {
                    if (!(SubInfoRecordUpdater.sIccId[slotId] == null || SubInfoRecordUpdater.sIccId[slotId].equals(SubInfoRecordUpdater.ICCID_STRING_FOR_NO_SIM))) {
                        SubInfoRecordUpdater.logd("SIM" + (slotId + 1) + " hot plug out");
                        SubInfoRecordUpdater.sNeedUpdate = true;
                    }
                    SubInfoRecordUpdater.sFh[slotId] = null;
                    SubInfoRecordUpdater.sIccId[slotId] = SubInfoRecordUpdater.ICCID_STRING_FOR_NO_SIM;
                    if (SubInfoRecordUpdater.this.isAllIccIdQueryDone() && SubInfoRecordUpdater.sNeedUpdate) {
                        SubInfoRecordUpdater.this.updateSimInfoByIccId();
                    }
                } else if ("UNKNOWN".equals(simStatus) && SubInfoRecordUpdater.isSimReset[slotId]) {
                    SubInfoRecordUpdater.isSimReset[slotId] = false;
                    if (SubscriptionHelper.isEnabled()) {
                        SubscriptionHelper.getInstance().updateSimState(slotId, SubInfoRecordUpdater.sInsertSimState[slotId]);
                    }
                }
            } else if (action.equals(AppInterface.CAT_ICC_STATUS_CHANGE)) {
                int refreshResult = intent.getIntExtra(AppInterface.REFRESH_RESULT, 0);
                slotId = intent.getIntExtra("SLOT_ID", -1000);
                SubInfoRecordUpdater.logd("slotId: " + slotId + " refreshResult: " + refreshResult);
                if (slotId == -1000) {
                    return;
                }
                if (refreshResult == 2) {
                    SubInfoRecordUpdater.isSimReset[slotId] = true;
                }
            } else if (action.equals("android.intent.action.ACTION_SHUTDOWN")) {
                SubInfoRecordUpdater.this.mIsSystemShutdown = true;
            }
            SubInfoRecordUpdater.logd("[Receiver]-");
        }
    }

    public SubInfoRecordUpdater(Context context, Phone[] phoneProxy, CommandsInterface[] ci) {
        int i;
        logd("Constructor invoked");
        sContext = context;
        sPhone = phoneProxy;
        sCi = ci;
        if (SubscriptionHelper.isEnabled()) {
            SubscriptionHelper.init(context, ci);
            for (i = 0; i < PROJECT_SIM_NUM; i++) {
                sCardState[i] = CardState.CARDSTATE_ABSENT;
            }
        }
        if (TelephonyManager.getDefault().getPhoneCount() > 1) {
            for (i = 0; i < PROJECT_SIM_NUM; i++) {
                this.mIccRecords[i] = null;
            }
            mUiccController = UiccController.getInstance();
            mUiccController.registerForIccChanged(this, 2, null);
        }
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(AppInterface.CAT_ICC_STATUS_CHANGE);
        intentFilter.addAction("android.intent.action.SIM_STATE_CHANGED");
        intentFilter.addAction("android.intent.action.ACTION_SHUTDOWN");
        sContext.registerReceiver(this.sReceiver, intentFilter);
    }

    private static int encodeEventId(int event, int slotId) {
        return event << (slotId * 8);
    }

    private boolean isAllIccIdQueryDone() {
        for (int i = 0; i < PROJECT_SIM_NUM; i++) {
            if (sIccId[i] == null) {
                logd("Wait for SIM" + (i + 1) + " IccId");
                return false;
            }
        }
        logd("All IccIds query complete");
        return true;
    }

    public static void setDisplayNameForNewSub(String newSubName, int subId, int newNameSource) {
        SubInfoRecord subInfo = SubscriptionManager.getSubInfoForSubscriber((long) subId);
        if (subInfo != null) {
            int oldNameSource = subInfo.nameSource;
            String oldSubName = subInfo.displayName;
            logd("[setDisplayNameForNewSub] mSubInfoIdx = " + subInfo.subId + ", oldSimName = " + oldSubName + ", oldNameSource = " + oldNameSource + ", newSubName = " + newSubName + ", newNameSource = " + newNameSource);
            if (oldSubName == null || ((oldNameSource == 0 && newSubName != null) || !(oldNameSource != 1 || newSubName == null || newSubName.equals(oldSubName)))) {
                SubscriptionManager.setDisplayName(newSubName, subInfo.subId, (long) newNameSource);
                return;
            }
            return;
        }
        logd(LOG_TAG + (subId + 1) + " SubInfo not created yet");
    }

    public void handleMessage(Message msg) {
        AsyncResult ar = msg.obj;
        int msgNum = msg.what;
        int slotId = 0;
        while (slotId <= 2 && msgNum >= (1 << (slotId * 8))) {
            slotId++;
        }
        slotId--;
        switch (msgNum >> (slotId * 8)) {
            case 1:
                logd("handleMessage : <EVENT_QUERY_ICCID_DONE> SIM" + (slotId + 1));
                if (ar.exception != null) {
                    sIccId[slotId] = ICCID_STRING_FOR_NO_SIM;
                    logd("Query IccId fail: " + ar.exception);
                } else if (ar.result != null) {
                    byte[] data = (byte[]) ar.result;
                    String countryISO = SystemProperties.get("ro.csc.countryiso_code");
                    if ("CN".equals(countryISO) || "HK".equals(countryISO) || "TW".equals(countryISO)) {
                        sIccId[slotId] = IccUtils.ICCIDbcdToString(data, 0, data.length);
                    } else if (isIccIdHasChar(data, data.length)) {
                        sIccId[slotId] = ICCID_STRING_FOR_INVALID_ICCID;
                    } else {
                        sIccId[slotId] = IccUtils.ICCIDbcdToString(data, 0, data.length);
                    }
                } else {
                    logd("Null ar");
                    sIccId[slotId] = ICCID_STRING_FOR_NO_SIM;
                }
                if (SHIP_BUILD) {
                    logd("sIccId[" + slotId + "] = XXX");
                } else {
                    logd("sIccId[" + slotId + "] = " + sIccId[slotId]);
                }
                if (isAllIccIdQueryDone() && sNeedUpdate) {
                    updateSimInfoByIccId();
                    return;
                }
                return;
            case 2:
                if (this.mIsSystemShutdown) {
                    logd("mIsSystemShutdown: " + this.mIsSystemShutdown + ", ignore EVENT_ICC_CHANGED");
                    return;
                } else {
                    updateIccAvailability();
                    return;
                }
            case 4:
                logd("handleMessage : <EVENT_ICCID_READY> SIM" + (slotId + 1));
                if (this.mIccRecords[slotId] == null) {
                    logd("cannot getIccId, null mIccRecords[" + slotId + "]");
                    return;
                }
                sIccId[slotId] = this.mIccRecords[slotId].getIccId();
                if (SHIP_BUILD) {
                    logd("sIccId[" + slotId + "] = ******");
                } else {
                    logd("sIccId[" + slotId + "] = " + sIccId[slotId]);
                }
                if (isAllIccIdQueryDone() && sNeedUpdate) {
                    updateSimInfoByIccId();
                    return;
                }
                return;
            default:
                logd("Unknown msg:" + msg.what);
                return;
        }
    }

    private void updateIccAvailability() {
        if (mUiccController != null) {
            logd("updateIccAvailability: Enter");
            int slotId = 0;
            while (slotId < PROJECT_SIM_NUM) {
                IccRecords newRecords = null;
                UiccCardApplication validApp = null;
                CardState newState = CardState.CARDSTATE_ABSENT;
                UiccCard newCard = mUiccController.getUiccCard(slotId);
                if (newCard != null) {
                    newState = newCard.getCardState();
                    int numApps = newCard.getNumApplications();
                    for (int i = 0; i < numApps; i++) {
                        UiccCardApplication app = newCard.getApplicationIndex(i);
                        if (app != null && app.getType() != AppType.APPTYPE_UNKNOWN) {
                            validApp = app;
                            break;
                        }
                    }
                    if (validApp != null) {
                        newRecords = validApp.getIccRecords();
                    }
                }
                if (this.mIccRecords[slotId] != newRecords) {
                    logd("mIccRecords changed. Reregestering.");
                    if (this.mIccRecords[slotId] != null) {
                        this.mIccRecords[slotId].unregisterForIccIdReady(this);
                    }
                    this.mIccRecords[slotId] = newRecords;
                    if (this.mIccRecords[slotId] != null) {
                        this.mIccRecords[slotId].registerForIccIdReady(this, encodeEventId(4, slotId), null);
                    }
                }
                if (SubscriptionHelper.isEnabled()) {
                    CardState oldState = sCardState[slotId];
                    sCardState[slotId] = newState;
                    logd("Slot[" + slotId + "]: New Card State = " + newState + " " + "Old Card State = " + oldState);
                    if (!newState.isCardPresent()) {
                        String mIccType = MultiSimManager.getTelephonyProperty("ril.ICC_TYPE", slotId, "0");
                        if ("0".equals(mIccType)) {
                            if (!(sIccId[slotId] == null || sIccId[slotId].equals(ICCID_STRING_FOR_NO_SIM))) {
                                logd("SIM" + (slotId + 1) + " hot plug out");
                                sNeedUpdate = true;
                            }
                            sFh[slotId] = null;
                            sIccId[slotId] = ICCID_STRING_FOR_NO_SIM;
                            if (isAllIccIdQueryDone() && sNeedUpdate) {
                                updateSimInfoByIccId();
                            }
                        } else {
                            logd("New Card State = " + newState + ", but mIccType:" + mIccType + ", continue");
                        }
                    } else if (!oldState.isCardPresent() && newState.isCardPresent()) {
                        if (sIccId[slotId] != null && sIccId[slotId].equals(ICCID_STRING_FOR_NO_SIM)) {
                            logd("SIM" + (slotId + 1) + " hot plug in");
                            sIccId[slotId] = null;
                            sNeedUpdate = true;
                        }
                        queryIccId(slotId);
                    }
                }
                slotId++;
            }
        }
    }

    private void queryIccId(int slotId) {
        logd("queryIccId: slotid=" + slotId);
        if (sFh[slotId] == null) {
            logd("Getting IccFileHandler");
            sFh[slotId] = ((PhoneProxy) sPhone[slotId]).getIccFileHandler();
        }
        if (sFh[slotId] != null) {
            String iccId = sIccId[slotId];
            if (iccId == null) {
                logd("Querying IccId");
                sFh[slotId].loadEFTransparent(IccConstants.EF_ICCID, obtainMessage(encodeEventId(1, slotId)));
                return;
            } else if (SHIP_BUILD) {
                logd("NOT Querying IccId its already set sIccid[" + slotId + "]= XXX");
                return;
            } else {
                logd("NOT Querying IccId its already set sIccid[" + slotId + "]=" + iccId);
                return;
            }
        }
        sCardState[slotId] = CardState.CARDSTATE_ABSENT;
        logd("sFh[" + slotId + "] is null, SIM not inserted");
    }

    public synchronized void updateSimInfoByIccId() {
        int i;
        logd("[updateSimInfoByIccId]+ Start");
        sNeedUpdate = false;
        SubscriptionManager.clearSubInfo();
        for (i = 0; i < PROJECT_SIM_NUM; i++) {
            sInsertSimState[i] = 0;
        }
        int insertedSimCount = PROJECT_SIM_NUM;
        int insertedSimStatus = 0;
        for (i = 0; i < PROJECT_SIM_NUM; i++) {
            if (!ICCID_STRING_FOR_NO_SIM.equals(sIccId[i])) {
                switch (i) {
                    case 0:
                        insertedSimStatus |= 1;
                        break;
                    case 1:
                        insertedSimStatus |= 2;
                        break;
                    case 2:
                        insertedSimStatus |= 4;
                        break;
                    default:
                        break;
                }
            }
            insertedSimCount--;
            sInsertSimState[i] = -99;
        }
        logd("insertedSimCount = " + insertedSimCount);
        i = 0;
        while (i < PROJECT_SIM_NUM) {
            if (sInsertSimState[i] != -99) {
                int index = 2;
                int j = i + 1;
                while (j < PROJECT_SIM_NUM) {
                    if (sInsertSimState[j] == 0 && sIccId[i].equals(sIccId[j])) {
                        sInsertSimState[i] = 1;
                        sInsertSimState[j] = index;
                        index++;
                    }
                    j++;
                }
            }
            i++;
        }
        ContentResolver contentResolver = sContext.getContentResolver();
        String[] oldIccId = new String[PROJECT_SIM_NUM];
        i = 0;
        while (i < PROJECT_SIM_NUM) {
            oldIccId[i] = null;
            List<SubInfoRecord> oldSubInfo = SubscriptionController.getInstance().getSubInfoUsingSlotIdWithCheck(i, false);
            if (oldSubInfo != null) {
                oldIccId[i] = ((SubInfoRecord) oldSubInfo.get(0)).iccId;
                logd("oldSubId = " + ((SubInfoRecord) oldSubInfo.get(0)).subId);
                if (sInsertSimState[i] == 0 && !sIccId[i].equals(oldIccId[i])) {
                    sInsertSimState[i] = -1;
                }
                if (sInsertSimState[i] != 0) {
                    ContentValues value = new ContentValues(1);
                    value.put("sim_id", Integer.valueOf(-1000));
                    contentResolver.update(SubscriptionManager.CONTENT_URI, value, "_id=" + Long.toString(((SubInfoRecord) oldSubInfo.get(0)).subId), null);
                }
            } else {
                if (sInsertSimState[i] == 0) {
                    sInsertSimState[i] = -1;
                }
                oldIccId[i] = ICCID_STRING_FOR_NO_SIM;
                logd("No SIM in slot " + i + " last time");
            }
            i++;
        }
        for (i = 0; i < PROJECT_SIM_NUM; i++) {
            if (SHIP_BUILD) {
                logd("oldIccId[" + i + "] = XXX, sIccId[" + i + "] = XXX");
            } else {
                logd("oldIccId[" + i + "] = " + oldIccId[i] + ", sIccId[" + i + "] = " + sIccId[i]);
            }
        }
        int nNewCardCount = 0;
        int nNewSimStatus = 0;
        for (i = 0; i < PROJECT_SIM_NUM; i++) {
            if (sInsertSimState[i] == -99) {
                logd("No SIM inserted in slot " + i + " this time");
            } else {
                if (sInsertSimState[i] > 0) {
                    SubscriptionManager.addSubInfoRecord(sIccId[i] + Integer.toString(sInsertSimState[i]), i);
                    logd(LOG_TAG + (i + 1) + " has invalid IccId");
                } else {
                    SubscriptionManager.addSubInfoRecord(sIccId[i], i);
                }
                if (isNewSim(sIccId[i], oldIccId)) {
                    nNewCardCount++;
                    switch (i) {
                        case 0:
                            nNewSimStatus |= 1;
                            break;
                        case 1:
                            nNewSimStatus |= 2;
                            break;
                        case 2:
                            nNewSimStatus |= 4;
                            break;
                    }
                    sInsertSimState[i] = -2;
                }
            }
        }
        SubscriptionController.getInstance().refreshSubInfo();
        if (!"CTC".equals(SystemProperties.get("ro.csc.sales_code")) && insertedSimCount == 1) {
            logd("insertedSimStatus = " + insertedSimStatus);
            if ((insertedSimStatus & 1) != 0) {
                System.putInt(sContext.getContentResolver(), "phone1_on", 1);
            } else if ((insertedSimStatus & 2) != 0) {
                System.putInt(sContext.getContentResolver(), "phone2_on", 1);
            } else {
                logd("else, insertedSimStatus = " + insertedSimStatus);
            }
        }
        for (i = 0; i < PROJECT_SIM_NUM; i++) {
            if (sInsertSimState[i] == -1) {
                sInsertSimState[i] = -3;
            }
            logd("sInsertSimState[" + i + "] = " + sInsertSimState[i]);
            if ("CG".equals("DGG") || "DGG".equals("DGG") || "DCG".equals("DGG") || "DCGG".equals("DGG") || "DCGS".equals("DGG") || "DCGGS".equals("DGG")) {
                long[] subId = SubscriptionController.getInstance().getSubIdUsingSlotId(i);
                if ("UNKNOWN".equals(MultiSimManager.getTelephonyProperty("gsm.sim.state", i, "UNKNOWN"))) {
                    logd("PROPERTY_SIM_STATE UNKNOWN, ignore");
                } else {
                    SubscriptionController.getInstance().setSubState(subId[0], 1);
                }
            } else if (SubscriptionHelper.isEnabled()) {
                SubscriptionHelper.getInstance().updateSimState(i, sInsertSimState[i]);
            }
        }
        List<SubInfoRecord> subInfos = SubscriptionManager.getActiveSubInfoList();
        int nSubCount = subInfos == null ? 0 : subInfos.size();
        logd("nSubCount = " + nSubCount);
        for (i = 0; i < nSubCount; i++) {
            SubInfoRecord temp = (SubInfoRecord) subInfos.get(i);
            String msisdn = TelephonyManager.getDefault().getLine1NumberForSubscriber(temp.subId);
            if (msisdn != null) {
                value = new ContentValues(1);
                value.put("number", msisdn);
                contentResolver.update(SubscriptionManager.CONTENT_URI, value, "_id=" + Long.toString(temp.subId), null);
            }
        }
        boolean hasSimRemoved = false;
        i = 0;
        while (i < PROJECT_SIM_NUM) {
            if (sIccId[i] == null || !sIccId[i].equals(ICCID_STRING_FOR_NO_SIM) || oldIccId[i].equals(ICCID_STRING_FOR_NO_SIM)) {
                i++;
            } else {
                hasSimRemoved = true;
                if (nNewCardCount == 0) {
                    logd("New SIM detected");
                    setUpdatedData(1, nSubCount, nNewSimStatus);
                } else if (hasSimRemoved) {
                    i = 0;
                    while (i < PROJECT_SIM_NUM) {
                        if (sInsertSimState[i] != -3) {
                            logd("No new SIM detected and SIM repositioned");
                            setUpdatedData(3, nSubCount, nNewSimStatus);
                            if (i == PROJECT_SIM_NUM) {
                                logd("[updateSimInfoByIccId] All SIM inserted into the same slot");
                                setUpdatedData(4, nSubCount, nNewSimStatus);
                            }
                        } else {
                            i++;
                        }
                    }
                    if (i == PROJECT_SIM_NUM) {
                        logd("[updateSimInfoByIccId] All SIM inserted into the same slot");
                        setUpdatedData(4, nSubCount, nNewSimStatus);
                    }
                } else {
                    i = 0;
                    while (i < PROJECT_SIM_NUM) {
                        if (sInsertSimState[i] != -3) {
                            logd("No new SIM detected and SIM repositioned");
                            setUpdatedData(3, nSubCount, nNewSimStatus);
                            if (i == PROJECT_SIM_NUM) {
                                logd("No new SIM detected and SIM removed");
                                setUpdatedData(2, nSubCount, nNewSimStatus);
                            }
                        } else {
                            i++;
                        }
                    }
                    if (i == PROJECT_SIM_NUM) {
                        logd("No new SIM detected and SIM removed");
                        setUpdatedData(2, nSubCount, nNewSimStatus);
                    }
                }
                logd("[updateSimInfoByIccId]- SimInfo update complete");
            }
        }
        if (nNewCardCount == 0) {
            logd("New SIM detected");
            setUpdatedData(1, nSubCount, nNewSimStatus);
        } else if (hasSimRemoved) {
            i = 0;
            while (i < PROJECT_SIM_NUM) {
                if (sInsertSimState[i] != -3) {
                    i++;
                } else {
                    logd("No new SIM detected and SIM repositioned");
                    setUpdatedData(3, nSubCount, nNewSimStatus);
                    if (i == PROJECT_SIM_NUM) {
                        logd("[updateSimInfoByIccId] All SIM inserted into the same slot");
                        setUpdatedData(4, nSubCount, nNewSimStatus);
                    }
                }
            }
            if (i == PROJECT_SIM_NUM) {
                logd("[updateSimInfoByIccId] All SIM inserted into the same slot");
                setUpdatedData(4, nSubCount, nNewSimStatus);
            }
        } else {
            i = 0;
            while (i < PROJECT_SIM_NUM) {
                if (sInsertSimState[i] != -3) {
                    i++;
                } else {
                    logd("No new SIM detected and SIM repositioned");
                    setUpdatedData(3, nSubCount, nNewSimStatus);
                    if (i == PROJECT_SIM_NUM) {
                        logd("No new SIM detected and SIM removed");
                        setUpdatedData(2, nSubCount, nNewSimStatus);
                    }
                }
            }
            if (i == PROJECT_SIM_NUM) {
                logd("No new SIM detected and SIM removed");
                setUpdatedData(2, nSubCount, nNewSimStatus);
            }
        }
        logd("[updateSimInfoByIccId]- SimInfo update complete");
    }

    private static void setUpdatedData(int detectedType, int subCount, int newSimStatus) {
        Intent intent = new Intent("android.intent.action.ACTION_SUBINFO_RECORD_UPDATED");
        logd("[setUpdatedData]+ ");
        if (detectedType == 1) {
            intent.putExtra("simDetectStatus", 1);
            intent.putExtra("simCount", subCount);
            intent.putExtra("newSIMSlot", newSimStatus);
        } else if (detectedType == 3) {
            intent.putExtra("simDetectStatus", 3);
            intent.putExtra("simCount", subCount);
        } else if (detectedType == 2) {
            intent.putExtra("simDetectStatus", 2);
            intent.putExtra("simCount", subCount);
        } else if (detectedType == 4) {
            intent.putExtra("simDetectStatus", 4);
        }
        logd("broadcast intent ACTION_SUBINFO_RECORD_UPDATED : [" + detectedType + ", " + subCount + ", " + newSimStatus + "]");
        ActivityManagerNative.broadcastStickyIntent(intent, "android.permission.READ_PHONE_STATE", -1);
        logd("[setUpdatedData]- ");
    }

    private static boolean isNewSim(String iccId, String[] oldIccId) {
        boolean newSim = true;
        for (int i = 0; i < PROJECT_SIM_NUM; i++) {
            if (iccId.equals(oldIccId[i])) {
                newSim = false;
                break;
            }
        }
        logd("newSim = " + newSim);
        return newSim;
    }

    public void dispose() {
        logd("[dispose]");
        sContext.unregisterReceiver(this.sReceiver);
    }

    private static void logd(String message) {
        Rlog.d(LOG_TAG, "[SubInfoRecordUpdater]" + message);
    }

    public boolean isIccIdHasChar(byte[] data, int length) {
        boolean All_FF = false;
        int i = 0;
        while (i < length) {
            if ((data[i] & 15) != 15 || ((data[i] >> 4) & 15) != 15) {
                All_FF = false;
                break;
            }
            All_FF = true;
            i++;
        }
        if (All_FF) {
            return false;
        }
        i = 0;
        while (i < length) {
            int b = (data[i] >> 4) & 15;
            if ((data[i] & 15) > 9 || (b > 9 && i != length - 1)) {
                return true;
            }
            i++;
        }
        return false;
    }
}
