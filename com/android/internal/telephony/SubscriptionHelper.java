package com.android.internal.telephony;

import android.content.Context;
import android.content.Intent;
import android.os.AsyncResult;
import android.os.Handler;
import android.os.Message;
import android.provider.Settings.System;
import android.telephony.Rlog;
import android.telephony.TelephonyManager;
import android.util.Log;
import com.android.internal.telephony.uicc.UiccCard;
import com.android.internal.telephony.uicc.UiccController;

class SubscriptionHelper extends Handler {
    private static final int EVENT_SET_UICC_SUBSCRIPTION_DONE = 1;
    private static final String LOG_TAG = "SubHelper";
    public static final int SUB_INIT_STATE = -1;
    public static final int SUB_SIM_NOT_INSERTED = -99;
    private static final String[] mPhoneOnKey = new String[]{"phone1_on", "phone2_on", "phone3_on", "phone4_on", "phone5_on"};
    private static final String[] mUiccFamilyName = new String[]{"UNKNOWN", "APP_FAM_3GPP", "APP_FAM_3GPP2", "APP_FAM_IMS"};
    private static SubscriptionHelper sInstance;
    private CommandsInterface[] mCi;
    private Context mContext;
    private int[] mSubStatus;

    public static SubscriptionHelper init(Context c, CommandsInterface[] ci) {
        SubscriptionHelper subscriptionHelper;
        synchronized (SubscriptionHelper.class) {
            if (sInstance == null) {
                sInstance = new SubscriptionHelper(c, ci);
            } else {
                Log.wtf(LOG_TAG, "init() called multiple times!  sInstance = " + sInstance);
            }
            subscriptionHelper = sInstance;
        }
        return subscriptionHelper;
    }

    public static SubscriptionHelper getInstance() {
        if (sInstance == null) {
            Log.wtf(LOG_TAG, "getInstance null");
        }
        return sInstance;
    }

    private SubscriptionHelper(Context c, CommandsInterface[] ci) {
        this.mContext = c;
        this.mCi = ci;
        int numPhones = TelephonyManager.getDefault().getPhoneCount();
        this.mSubStatus = new int[numPhones];
        for (int i = 0; i < numPhones; i++) {
            this.mSubStatus[i] = -1;
        }
        logd("SubscriptionHelper init by Context");
    }

    public void handleMessage(Message msg) {
        switch (msg.what) {
            case 1:
                logd("EVENT_SET_UICC_SUBSCRIPTION_DONE");
                processSetUiccSubscriptionDone((AsyncResult) msg.obj);
                return;
            default:
                return;
        }
    }

    public void updateSimState(int slotId, int simStatus) {
        SubscriptionController subCtrlr = SubscriptionController.getInstance();
        this.mSubStatus[slotId] = simStatus;
        if (this.mSubStatus[slotId] != -99) {
            long[] subId = subCtrlr.getSubId(slotId);
            int subState = System.getInt(this.mContext.getContentResolver(), mPhoneOnKey[slotId], 1);
            logd("setUicc for [" + slotId + "] mPhoneOn = " + subState + ", subId = " + subId[0] + ", current SubState = " + subCtrlr.getSubState(subId[0]));
            setUiccSubscription(slotId, subState);
        } else if (isAllSubsAvailable()) {
            logd("Received all sim info, now update user preferred subs");
            subCtrlr.updateUserPrefs();
        }
    }

    public void setUiccSubscription(int slotId, int subStatus) {
        this.mSubStatus[slotId] = subStatus;
        boolean set3GPPDone = false;
        boolean set3GPP2Done = false;
        UiccCard uiccCard = UiccController.getInstance().getUiccCard(slotId);
        if (uiccCard == null) {
            logd("setUiccSubscription: slotId:" + slotId + " card info not available");
            return;
        }
        int i = 0;
        while (i < uiccCard.getNumApplications()) {
            int appType = uiccCard.getApplicationIndex(i).getType().ordinal();
            if (!set3GPPDone && (appType == 2 || appType == 1)) {
                this.mCi[slotId].setUiccSubscription(slotId, i, slotId, subStatus, Message.obtain(this, 1, new int[]{slotId, 1}));
                set3GPPDone = true;
            } else if (!set3GPP2Done && (appType == 4 || appType == 3)) {
                this.mCi[slotId].setUiccSubscription(slotId, i, slotId, subStatus, Message.obtain(this, 1, new int[]{slotId, 2}));
                set3GPP2Done = true;
            }
            if (!set3GPPDone || !set3GPP2Done) {
                i++;
            } else {
                return;
            }
        }
    }

    private void processSetUiccSubscriptionDone(AsyncResult ar) {
        int[] setSubData = (int[]) ar.userObj;
        int slotId = setSubData[0];
        int uiccFamilyId = setSubData[1];
        if (ar.exception != null) {
            loge("processSetUiccSubscriptionDone: setUiccSubscription failed. mSubStatus[" + slotId + "]:" + this.mSubStatus[slotId] + ". uiccFamily:" + mUiccFamilyName[uiccFamilyId]);
            return;
        }
        SubscriptionController subCtrlr = SubscriptionController.getInstance();
        if (subCtrlr != null) {
            long[] subId = subCtrlr.getSubIdUsingSlotId(slotId);
            int subStatus = subCtrlr.getSubState(subId[0]);
            if (this.mSubStatus[slotId] != subStatus) {
                subCtrlr.setSubState(subId[0], this.mSubStatus[slotId]);
            }
            if (this.mSubStatus[slotId] != System.getInt(this.mContext.getContentResolver(), mPhoneOnKey[slotId], -1)) {
                System.putInt(this.mContext.getContentResolver(), mPhoneOnKey[slotId], subStatus);
            }
        }
        if (isAllSubsAvailable()) {
            logd("Received all subs, now update user preferred subs");
            subCtrlr.updateUserPrefs();
        }
        long[] subIds = subCtrlr.getSubIdUsingSlotId(slotId);
        logd("processSetUiccSubscriptionDone: setUiccSubscription succeed. mSubStatus[" + slotId + "]:" + this.mSubStatus[slotId] + ", subId:" + subIds[0] + ", " + mUiccFamilyName[uiccFamilyId]);
        Intent intent;
        if (this.mSubStatus[slotId] == 1) {
            intent = new Intent("com.android.settings.subscription_activate");
            intent.putExtra("slot", slotId);
            intent.putExtra("subscription", subIds[0]);
            this.mContext.sendBroadcast(intent);
            if (!"CG".equals("DGG")) {
                return;
            }
            if ((slotId == 0 && uiccFamilyId == 2) || (slotId == 1 && uiccFamilyId == 1)) {
                TelephonyManager.setTelephonyProperty("gsm.sim.active", subIds[0], "2");
            }
        } else if (this.mSubStatus[slotId] == 0) {
            intent = new Intent("com.android.settings.subscription_deactivate");
            intent.putExtra("slot", slotId);
            intent.putExtra("subscription", subIds[0]);
            this.mContext.sendBroadcast(intent);
            if (!"CG".equals("DGG")) {
                return;
            }
            if ((slotId == 0 && uiccFamilyId == 2) || (slotId == 1 && uiccFamilyId == 1)) {
                this.mContext.sendBroadcast(new Intent("com.samsung.intent.action.Slot" + (slotId + 1) + "OffCompleted"));
            }
        } else {
            logd("processSetUiccSubscriptionDone: invalid mSubStatus[" + slotId + "]:" + this.mSubStatus[slotId]);
        }
    }

    private boolean isAllSubsAvailable() {
        boolean allSubsAvailable = true;
        int numPhones = TelephonyManager.getDefault().getPhoneCount();
        for (int i = 0; i < numPhones; i++) {
            if (this.mSubStatus[i] == -1) {
                allSubsAvailable = false;
            }
        }
        return allSubsAvailable;
    }

    public static boolean isEnabled() {
        if (!"Combination".equals("Combination") || "DCG".equals("DGG") || "DCGG".equals("DGG") || "DCGS".equals("DGG") || "DCGGS".equals("DGG") || "DGG".equals("DGG")) {
            return false;
        }
        return true;
    }

    private static void logd(String message) {
        Rlog.d(LOG_TAG, message);
    }

    private void logi(String msg) {
        Rlog.i(LOG_TAG, msg);
    }

    private void loge(String msg) {
        Rlog.e(LOG_TAG, msg);
    }
}
