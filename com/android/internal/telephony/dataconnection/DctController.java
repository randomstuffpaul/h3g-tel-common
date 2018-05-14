package com.android.internal.telephony.dataconnection;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.AsyncResult;
import android.os.Handler;
import android.os.Message;
import android.os.Registrant;
import android.os.RegistrantList;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.provider.Settings.Global;
import android.telephony.Rlog;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.util.Log;
import com.android.internal.telephony.DefaultPhoneNotifier.IDataStateChangedCallback;
import com.android.internal.telephony.IccCardConstants.State;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneBase;
import com.android.internal.telephony.PhoneConstants.DataState;
import com.android.internal.telephony.PhoneProxy;
import com.android.internal.telephony.SubscriptionController;
import java.util.HashSet;
import java.util.Iterator;

public class DctController extends Handler {
    private static final boolean DBG = true;
    private static final int EVENT_ALL_DATA_DISCONNECTED = 1;
    private static final int EVENT_PHONE1_DETACH = 1;
    private static final int EVENT_PHONE1_RADIO_OFF = 5;
    private static final int EVENT_PHONE2_DETACH = 2;
    private static final int EVENT_PHONE2_RADIO_OFF = 6;
    private static final int EVENT_PHONE3_DETACH = 3;
    private static final int EVENT_PHONE3_RADIO_OFF = 7;
    private static final int EVENT_PHONE4_DETACH = 4;
    private static final int EVENT_PHONE4_RADIO_OFF = 8;
    private static final int EVENT_SET_DATA_ALLOW_DONE = 2;
    private static final int EVENT_SET_DATA_ALLOW_FOR_MMS_DONE = 3;
    private static final String LOG_TAG = "DctController";
    private static final int PHONE_NONE = -1;
    private static DctController sDctController;
    private Phone mActivePhone;
    private HashSet<String> mApnTypes = new HashSet();
    private Context mContext;
    private int mCurrentDataPhone = -1;
    private IDataStateChangedCallback mDataStateChangedCallback = new C01012();
    private BroadcastReceiver mDataStateReceiver;
    private DcSwitchAsyncChannel[] mDcSwitchAsyncChannel;
    private DcSwitchState[] mDcSwitchState;
    private Handler[] mDcSwitchStateHandler;
    private RegistrantList mNotifyDataSwitchInfo = new RegistrantList();
    private int mPhoneNum;
    private PhoneProxy[] mPhones;
    private int mRequestedDataPhone = -1;
    private Handler mRspHander = new C01001();
    private boolean[] mServicePowerOffFlag;
    private SubscriptionController mSubController = SubscriptionController.getInstance();

    class C01001 extends Handler {
        C01001() {
        }

        public void handleMessage(Message msg) {
            switch (msg.what) {
                case 1:
                case 2:
                case 3:
                case 4:
                    DctController.logd("EVENT_PHONE" + msg.what + "_DETACH: mRequestedDataPhone=" + DctController.this.mRequestedDataPhone);
                    DctController.this.mCurrentDataPhone = -1;
                    if (DctController.this.mRequestedDataPhone != -1) {
                        DctController.this.mCurrentDataPhone = DctController.this.mRequestedDataPhone;
                        DctController.this.mRequestedDataPhone = -1;
                        Iterator<String> itrType = DctController.this.mApnTypes.iterator();
                        while (itrType.hasNext()) {
                            DctController.this.mDcSwitchAsyncChannel[DctController.this.mCurrentDataPhone].connectSync((String) itrType.next());
                        }
                        DctController.this.mApnTypes.clear();
                        return;
                    }
                    return;
                case 5:
                case 6:
                case 7:
                case 8:
                    DctController.logd("EVENT_PHONE" + ((msg.what - 5) + 1) + "_RADIO_OFF.");
                    DctController.this.mServicePowerOffFlag[msg.what - 5] = true;
                    return;
                default:
                    return;
            }
        }
    }

    class C01012 implements IDataStateChangedCallback {
        C01012() {
        }

        public void onDataStateChanged(long subId, String state, String reason, String apnName, String apnType, boolean unavailable) {
            DctController.logd("[DataStateChanged]:state=" + state + ",reason=" + reason + ",apnName=" + apnName + ",apnType=" + apnType + ",from subId=" + subId);
            int phoneId = SubscriptionController.getInstance().getPhoneId(subId);
            DctController.this.mDcSwitchState[phoneId].notifyDataConnection(phoneId, state, reason, apnName, apnType, unavailable);
        }
    }

    private class DataStateReceiver extends BroadcastReceiver {
        private DataStateReceiver() {
        }

        /* JADX WARNING: inconsistent code. */
        /* Code decompiled incorrectly, please refer to instructions dump. */
        public void onReceive(android.content.Context r13, android.content.Intent r14) {
            /*
            r12 = this;
            r10 = 0;
            monitor-enter(r12);
            r6 = r14.getAction();	 Catch:{ all -> 0x007d }
            r7 = "android.intent.action.SERVICE_STATE";
            r6 = r6.equals(r7);	 Catch:{ all -> 0x007d }
            if (r6 == 0) goto L_0x00f6;
        L_0x000f:
            r6 = r14.getExtras();	 Catch:{ all -> 0x007d }
            r2 = android.telephony.ServiceState.newFromBundle(r6);	 Catch:{ all -> 0x007d }
            r6 = "subscription";
            r8 = 0;
            r4 = r14.getLongExtra(r6, r8);	 Catch:{ all -> 0x007d }
            r6 = com.android.internal.telephony.SubscriptionController.getInstance();	 Catch:{ all -> 0x007d }
            r0 = r6.getPhoneId(r4);	 Catch:{ all -> 0x007d }
            r6 = new java.lang.StringBuilder;	 Catch:{ all -> 0x007d }
            r6.<init>();	 Catch:{ all -> 0x007d }
            r7 = "DataStateReceiver: phoneId= ";
            r6 = r6.append(r7);	 Catch:{ all -> 0x007d }
            r6 = r6.append(r0);	 Catch:{ all -> 0x007d }
            r6 = r6.toString();	 Catch:{ all -> 0x007d }
            com.android.internal.telephony.dataconnection.DctController.logd(r6);	 Catch:{ all -> 0x007d }
            r6 = android.telephony.SubscriptionManager.isValidSubId(r4);	 Catch:{ all -> 0x007d }
            if (r6 == 0) goto L_0x0047;
        L_0x0043:
            r6 = (r4 > r10 ? 1 : (r4 == r10 ? 0 : -1));
            if (r6 >= 0) goto L_0x005f;
        L_0x0047:
            r6 = new java.lang.StringBuilder;	 Catch:{ all -> 0x007d }
            r6.<init>();	 Catch:{ all -> 0x007d }
            r7 = "DataStateReceiver: ignore invalid subId=";
            r6 = r6.append(r7);	 Catch:{ all -> 0x007d }
            r6 = r6.append(r4);	 Catch:{ all -> 0x007d }
            r6 = r6.toString();	 Catch:{ all -> 0x007d }
            com.android.internal.telephony.dataconnection.DctController.logd(r6);	 Catch:{ all -> 0x007d }
            monitor-exit(r12);	 Catch:{ all -> 0x007d }
        L_0x005e:
            return;
        L_0x005f:
            r6 = android.telephony.SubscriptionManager.isValidPhoneId(r0);	 Catch:{ all -> 0x007d }
            if (r6 != 0) goto L_0x0080;
        L_0x0065:
            r6 = new java.lang.StringBuilder;	 Catch:{ all -> 0x007d }
            r6.<init>();	 Catch:{ all -> 0x007d }
            r7 = "DataStateReceiver: ignore invalid phoneId=";
            r6 = r6.append(r7);	 Catch:{ all -> 0x007d }
            r6 = r6.append(r0);	 Catch:{ all -> 0x007d }
            r6 = r6.toString();	 Catch:{ all -> 0x007d }
            com.android.internal.telephony.dataconnection.DctController.logd(r6);	 Catch:{ all -> 0x007d }
            monitor-exit(r12);	 Catch:{ all -> 0x007d }
            goto L_0x005e;
        L_0x007d:
            r6 = move-exception;
            monitor-exit(r12);	 Catch:{ all -> 0x007d }
            throw r6;
        L_0x0080:
            r6 = "CG";
            r7 = "DGG";
            r6 = r6.equals(r7);	 Catch:{ all -> 0x007d }
            if (r6 != 0) goto L_0x009e;
        L_0x008a:
            r6 = "GG";
            r7 = "DGG";
            r6 = r6.equals(r7);	 Catch:{ all -> 0x007d }
            if (r6 != 0) goto L_0x009e;
        L_0x0094:
            r6 = "CGG";
            r7 = "DGG";
            r6 = r6.equals(r7);	 Catch:{ all -> 0x007d }
            if (r6 == 0) goto L_0x00a0;
        L_0x009e:
            monitor-exit(r12);	 Catch:{ all -> 0x007d }
            goto L_0x005e;
        L_0x00a0:
            r6 = com.android.internal.telephony.dataconnection.DctController.this;	 Catch:{ all -> 0x007d }
            r6 = r6.mServicePowerOffFlag;	 Catch:{ all -> 0x007d }
            r1 = r6[r0];	 Catch:{ all -> 0x007d }
            if (r2 == 0) goto L_0x00f6;
        L_0x00aa:
            r3 = r2.getState();	 Catch:{ all -> 0x007d }
            switch(r3) {
                case 0: goto L_0x0119;
                case 1: goto L_0x013a;
                case 2: goto L_0x0165;
                case 3: goto L_0x00f9;
                default: goto L_0x00b1;
            };	 Catch:{ all -> 0x007d }
        L_0x00b1:
            r6 = "DataStateReceiver: SERVICE_STATE_CHANGED invalid state";
            com.android.internal.telephony.dataconnection.DctController.logd(r6);	 Catch:{ all -> 0x007d }
        L_0x00b6:
            if (r1 == 0) goto L_0x00f6;
        L_0x00b8:
            r6 = com.android.internal.telephony.dataconnection.DctController.this;	 Catch:{ all -> 0x007d }
            r6 = r6.mServicePowerOffFlag;	 Catch:{ all -> 0x007d }
            r6 = r6[r0];	 Catch:{ all -> 0x007d }
            if (r6 != 0) goto L_0x00f6;
        L_0x00c2:
            r6 = com.android.internal.telephony.dataconnection.DctController.this;	 Catch:{ all -> 0x007d }
            r6 = r6.mCurrentDataPhone;	 Catch:{ all -> 0x007d }
            r7 = -1;
            if (r6 != r7) goto L_0x00f6;
        L_0x00cb:
            r6 = com.android.internal.telephony.dataconnection.DctController.this;	 Catch:{ all -> 0x007d }
            r6 = r6.getDataConnectionFromSetting();	 Catch:{ all -> 0x007d }
            if (r0 != r6) goto L_0x00f6;
        L_0x00d3:
            r6 = new java.lang.StringBuilder;	 Catch:{ all -> 0x007d }
            r6.<init>();	 Catch:{ all -> 0x007d }
            r7 = "DataStateReceiver: Current Phone is none and default phoneId=";
            r6 = r6.append(r7);	 Catch:{ all -> 0x007d }
            r6 = r6.append(r0);	 Catch:{ all -> 0x007d }
            r7 = ", then enableApnType()";
            r6 = r6.append(r7);	 Catch:{ all -> 0x007d }
            r6 = r6.toString();	 Catch:{ all -> 0x007d }
            com.android.internal.telephony.dataconnection.DctController.logd(r6);	 Catch:{ all -> 0x007d }
            r6 = com.android.internal.telephony.dataconnection.DctController.this;	 Catch:{ all -> 0x007d }
            r7 = "default";
            r6.enableApnType(r4, r7);	 Catch:{ all -> 0x007d }
        L_0x00f6:
            monitor-exit(r12);	 Catch:{ all -> 0x007d }
            goto L_0x005e;
        L_0x00f9:
            r6 = com.android.internal.telephony.dataconnection.DctController.this;	 Catch:{ all -> 0x007d }
            r6 = r6.mServicePowerOffFlag;	 Catch:{ all -> 0x007d }
            r7 = 1;
            r6[r0] = r7;	 Catch:{ all -> 0x007d }
            r6 = new java.lang.StringBuilder;	 Catch:{ all -> 0x007d }
            r6.<init>();	 Catch:{ all -> 0x007d }
            r7 = "DataStateReceiver: STATE_POWER_OFF Intent from phoneId=";
            r6 = r6.append(r7);	 Catch:{ all -> 0x007d }
            r6 = r6.append(r0);	 Catch:{ all -> 0x007d }
            r6 = r6.toString();	 Catch:{ all -> 0x007d }
            com.android.internal.telephony.dataconnection.DctController.logd(r6);	 Catch:{ all -> 0x007d }
            goto L_0x00b6;
        L_0x0119:
            r6 = com.android.internal.telephony.dataconnection.DctController.this;	 Catch:{ all -> 0x007d }
            r6 = r6.mServicePowerOffFlag;	 Catch:{ all -> 0x007d }
            r7 = 0;
            r6[r0] = r7;	 Catch:{ all -> 0x007d }
            r6 = new java.lang.StringBuilder;	 Catch:{ all -> 0x007d }
            r6.<init>();	 Catch:{ all -> 0x007d }
            r7 = "DataStateReceiver: STATE_IN_SERVICE Intent from phoneId=";
            r6 = r6.append(r7);	 Catch:{ all -> 0x007d }
            r6 = r6.append(r0);	 Catch:{ all -> 0x007d }
            r6 = r6.toString();	 Catch:{ all -> 0x007d }
            com.android.internal.telephony.dataconnection.DctController.logd(r6);	 Catch:{ all -> 0x007d }
            goto L_0x00b6;
        L_0x013a:
            r6 = new java.lang.StringBuilder;	 Catch:{ all -> 0x007d }
            r6.<init>();	 Catch:{ all -> 0x007d }
            r7 = "DataStateReceiver: STATE_OUT_OF_SERVICE Intent from phoneId=";
            r6 = r6.append(r7);	 Catch:{ all -> 0x007d }
            r6 = r6.append(r0);	 Catch:{ all -> 0x007d }
            r6 = r6.toString();	 Catch:{ all -> 0x007d }
            com.android.internal.telephony.dataconnection.DctController.logd(r6);	 Catch:{ all -> 0x007d }
            r6 = com.android.internal.telephony.dataconnection.DctController.this;	 Catch:{ all -> 0x007d }
            r6 = r6.mServicePowerOffFlag;	 Catch:{ all -> 0x007d }
            r6 = r6[r0];	 Catch:{ all -> 0x007d }
            if (r6 == 0) goto L_0x00b6;
        L_0x015a:
            r6 = com.android.internal.telephony.dataconnection.DctController.this;	 Catch:{ all -> 0x007d }
            r6 = r6.mServicePowerOffFlag;	 Catch:{ all -> 0x007d }
            r7 = 0;
            r6[r0] = r7;	 Catch:{ all -> 0x007d }
            goto L_0x00b6;
        L_0x0165:
            r6 = new java.lang.StringBuilder;	 Catch:{ all -> 0x007d }
            r6.<init>();	 Catch:{ all -> 0x007d }
            r7 = "DataStateReceiver: STATE_EMERGENCY_ONLY Intent from phoneId=";
            r6 = r6.append(r7);	 Catch:{ all -> 0x007d }
            r6 = r6.append(r0);	 Catch:{ all -> 0x007d }
            r6 = r6.toString();	 Catch:{ all -> 0x007d }
            com.android.internal.telephony.dataconnection.DctController.logd(r6);	 Catch:{ all -> 0x007d }
            goto L_0x00b6;
            */
            throw new UnsupportedOperationException("Method not decompiled: com.android.internal.telephony.dataconnection.DctController.DataStateReceiver.onReceive(android.content.Context, android.content.Intent):void");
        }
    }

    public IDataStateChangedCallback getDataStateChangedCallback() {
        return this.mDataStateChangedCallback;
    }

    public static DctController getInstance() {
        if (sDctController != null) {
            return sDctController;
        }
        throw new RuntimeException("DCTrackerController.getInstance can't be called before makeDCTController()");
    }

    public static DctController makeDctController(PhoneProxy[] phones) {
        if (sDctController == null) {
            sDctController = new DctController(phones);
        }
        return sDctController;
    }

    private DctController(PhoneProxy[] phones) {
        if (phones != null && phones.length != 0) {
            this.mPhoneNum = phones.length;
            this.mServicePowerOffFlag = new boolean[this.mPhoneNum];
            this.mPhones = phones;
            this.mDcSwitchState = new DcSwitchState[this.mPhoneNum];
            this.mDcSwitchAsyncChannel = new DcSwitchAsyncChannel[this.mPhoneNum];
            this.mDcSwitchStateHandler = new Handler[this.mPhoneNum];
            this.mActivePhone = this.mPhones[0];
            for (int i = 0; i < this.mPhoneNum; i++) {
                int phoneId = i;
                this.mServicePowerOffFlag[i] = true;
                this.mDcSwitchState[i] = new DcSwitchState(this.mPhones[i], "DcSwitchState-" + phoneId, phoneId);
                this.mDcSwitchState[i].start();
                this.mDcSwitchAsyncChannel[i] = new DcSwitchAsyncChannel(this.mDcSwitchState[i], phoneId);
                this.mDcSwitchStateHandler[i] = new Handler();
                if (this.mDcSwitchAsyncChannel[i].fullyConnectSync(this.mPhones[i].getContext(), this.mDcSwitchStateHandler[i], this.mDcSwitchState[i].getHandler()) == 0) {
                    logd("DctController(phones): Connect success: " + i);
                } else {
                    loge("DctController(phones): Could not connect to " + i);
                }
                this.mDcSwitchState[i].registerForIdle(this.mRspHander, i + 1, null);
                ((PhoneBase) this.mPhones[i].getActivePhone()).mCi.registerForOffOrNotAvailable(this.mRspHander, i + 5, null);
            }
            this.mContext = this.mActivePhone.getContext();
            IntentFilter filter = new IntentFilter();
            filter.addAction("android.intent.action.DATA_CONNECTION_FAILED");
            filter.addAction("android.intent.action.SERVICE_STATE");
            this.mDataStateReceiver = new DataStateReceiver();
            Intent intent = this.mContext.registerReceiver(this.mDataStateReceiver, filter);
        } else if (phones == null) {
            loge("DctController(phones): UNEXPECTED phones=null, ignore");
        } else {
            loge("DctController(phones): UNEXPECTED phones.length=0, ignore");
        }
    }

    private State getIccCardState(int phoneId) {
        return this.mPhones[phoneId].getIccCard().getState();
    }

    public synchronized int enableApnType(long subId, String type) {
        int i = 3;
        synchronized (this) {
            int phoneId = SubscriptionController.getInstance().getPhoneId(subId);
            if (phoneId == -1 || !isValidphoneId(phoneId)) {
                logw("enableApnType(): with PHONE_NONE or Invalid PHONE ID");
            } else {
                logd("enableApnType():type=" + type + ",phoneId=" + phoneId + ",powerOff=" + this.mServicePowerOffFlag[phoneId]);
                if (!"default".equals(type)) {
                    int peerphoneId = 0;
                    loop0:
                    while (peerphoneId < this.mPhoneNum) {
                        if (phoneId != peerphoneId) {
                            String[] activeApnTypes = this.mPhones[peerphoneId].getActiveApnTypes();
                            if (!(activeApnTypes == null || activeApnTypes.length == 0)) {
                                int i2 = 0;
                                while (i2 < activeApnTypes.length) {
                                    if (!"default".equals(activeApnTypes[i2]) && this.mPhones[peerphoneId].getDataConnectionState(activeApnTypes[i2]) != DataState.DISCONNECTED) {
                                        logd("enableApnType:Peer Phone still have non-default active APN type:activeApnTypes[" + i2 + "]=" + activeApnTypes[i2]);
                                        break loop0;
                                    }
                                    i2++;
                                }
                                continue;
                            }
                        }
                        peerphoneId++;
                    }
                }
                logd("enableApnType(): CurrentDataPhone=" + this.mCurrentDataPhone + ", RequestedDataPhone=" + this.mRequestedDataPhone);
                if (phoneId == this.mCurrentDataPhone && !this.mDcSwitchAsyncChannel[this.mCurrentDataPhone].isIdleOrDeactingSync()) {
                    this.mRequestedDataPhone = -1;
                    logd("enableApnType(): mRequestedDataPhone equals request PHONE ID.");
                    i = this.mDcSwitchAsyncChannel[phoneId].connectSync(type);
                } else if (this.mCurrentDataPhone == -1) {
                    this.mCurrentDataPhone = phoneId;
                    this.mRequestedDataPhone = -1;
                    logd("enableApnType(): current PHONE is NONE or IDLE, mCurrentDataPhone=" + this.mCurrentDataPhone);
                    i = this.mDcSwitchAsyncChannel[phoneId].connectSync(type);
                } else {
                    logd("enableApnType(): current PHONE:" + this.mCurrentDataPhone + " is active.");
                    if (phoneId != this.mRequestedDataPhone) {
                        this.mApnTypes.clear();
                    }
                    this.mApnTypes.add(type);
                    this.mRequestedDataPhone = phoneId;
                    this.mDcSwitchState[this.mCurrentDataPhone].cleanupAllConnection();
                    i = 1;
                }
            }
        }
        return i;
    }

    public synchronized int disableApnType(long subId, String type) {
        int i;
        int phoneId = SubscriptionController.getInstance().getPhoneId(subId);
        if (phoneId == -1 || !isValidphoneId(phoneId)) {
            logw("disableApnType(): with PHONE_NONE or Invalid PHONE ID");
            i = 3;
        } else {
            logd("disableApnType():type=" + type + ",phoneId=" + phoneId + ",powerOff=" + this.mServicePowerOffFlag[phoneId]);
            i = this.mDcSwitchAsyncChannel[phoneId].disconnectSync(type);
        }
        return i;
    }

    public boolean isDataConnectivityPossible(String type, int phoneId) {
        if (phoneId != -1 && isValidphoneId(phoneId)) {
            return this.mPhones[phoneId].isDataConnectivityPossible(type);
        }
        logw("isDataConnectivityPossible(): with PHONE_NONE or Invalid PHONE ID");
        return false;
    }

    public boolean isIdleOrDeacting(int phoneId) {
        if (this.mDcSwitchAsyncChannel[phoneId].isIdleOrDeactingSync()) {
            return true;
        }
        return false;
    }

    private boolean isValidphoneId(int phoneId) {
        return phoneId >= 0 && phoneId <= this.mPhoneNum;
    }

    private boolean isValidApnType(String apnType) {
        if (apnType.equals("default") || apnType.equals("mms") || apnType.equals("supl") || apnType.equals("dun") || apnType.equals("hipri") || apnType.equals("fota") || apnType.equals("ims") || apnType.equals("cbs")) {
            return true;
        }
        return false;
    }

    private int getDataConnectionFromSetting() {
        return SubscriptionController.getInstance().getPhoneId(SubscriptionController.getInstance().getSubId(0)[0]);
    }

    private void broadcastDefaultDataSubIdCallback(long subId, boolean success) {
        Intent intent;
        if (success) {
            intent = new Intent("android.intent.action.ACTION_DEFAULT_DATA_SUBSCRIPTION_SUCCESS");
        } else {
            intent = new Intent("android.intent.action.ACTION_DEFAULT_DATA_SUBSCRIPTION_FAILED");
        }
        intent.addFlags(536870912);
        intent.putExtra("subscription", subId);
        Rlog.d(LOG_TAG, "[broadcastDefaultDataSubIdCallback] subId=" + subId + ", success=" + success + ", intent=" + intent.getAction());
        this.mContext.sendStickyBroadcastAsUser(intent, UserHandle.ALL);
    }

    private static void logv(String s) {
        Log.v(LOG_TAG, "[DctController] " + s);
    }

    private static void logd(String s) {
        Log.d(LOG_TAG, "[DctController] " + s);
    }

    private static void logw(String s) {
        Log.w(LOG_TAG, "[DctController] " + s);
    }

    private static void loge(String s) {
        Log.e(LOG_TAG, "[DctController] " + s);
    }

    public void setDataSubId(long subId) {
        Rlog.d(LOG_TAG, "setDataAllowed subId :" + subId);
        int phoneId = this.mSubController.getPhoneId(subId);
        int prefPhoneId = this.mSubController.getPhoneId(this.mSubController.getDefaultDataSubId());
        long prefSubId = this.mSubController.getDefaultDataSubId();
        if (prefSubId == -1000) {
            if (SubscriptionManager.isValidPhoneId(phoneId) && TelephonyManager.getDefault().getPhoneCount() == 2) {
                Rlog.d(LOG_TAG, "setDataAllowed, default data sub is not initilized, try to set default data slot to " + phoneId);
                prefPhoneId = phoneId == 0 ? 1 : 0;
            } else {
                Rlog.d(LOG_TAG, "setDataSubId: ignore invalid subId=" + prefSubId);
                return;
            }
        }
        Phone phone = this.mPhones[prefPhoneId].getActivePhone();
        phone = this.mPhones[phoneId].getActivePhone();
        int gprsState = ((PhoneBase) phone).getServiceStateTracker().getCurrentDataConnectionState();
        Rlog.d(LOG_TAG, "setDataSubId subId :" + subId + ", phoneId=" + phoneId + ", prefPhoneId=" + prefPhoneId + ", gprsState=" + gprsState);
        if (phoneId == prefPhoneId && gprsState == 0) {
            Rlog.d(LOG_TAG, "setDataSubId() return");
        } else if (("DCGG".equals("DGG") || "DGG".equals("DGG") || "DCGS".equals("DGG") || "DCGGS".equals("DGG")) && phoneId == prefPhoneId) {
            Rlog.d(LOG_TAG, "setDataSubId() return");
        } else {
            if ("DCGG".equals("DGG") || "DGG".equals("DGG") || "DCGS".equals("DGG") || "DCGGS".equals("DGG")) {
                phone = this.mPhones[prefPhoneId].getActivePhone();
            }
            ((PhoneBase) phone).mDcTracker.setDataAllowed(false, null);
            this.mPhones[prefPhoneId].registerForAllDataDisconnected(this, 1, new Integer(phoneId));
        }
    }

    public void setDataSubIdForMMS(long subId) {
        Rlog.d(LOG_TAG, "setDataSubIdForMMS subId :" + subId);
        int phoneId = this.mSubController.getPhoneId(subId);
        int prefPhoneId = this.mSubController.getPhoneId(this.mSubController.getDefaultDataSubId());
        Phone phone = this.mPhones[phoneId].getActivePhone();
        Rlog.d(LOG_TAG, "setDataSubId subId :" + subId + ", phoneId=" + phoneId + ", prefPhoneId=" + prefPhoneId + ", gprsState=" + ((PhoneBase) phone).getServiceStateTracker().getCurrentDataConnectionState());
        if (phoneId == prefPhoneId) {
            SystemProperties.set("ril.datacross.slotid", Integer.toString(-1));
        } else {
            SystemProperties.set("ril.datacross.slotid", Integer.toString(phoneId));
        }
        Rlog.d(LOG_TAG, "checking property, ril.datacross.slotid = " + SystemProperties.getInt("ril.datacross.slotid", -1));
        ((PhoneBase) phone).mCi.setDataAllowed(false, Message.obtain(this, 3, new Integer(phoneId)));
    }

    public void registerForDataSwitchInfo(Handler h, int what, Object obj) {
        Registrant r = new Registrant(h, what, obj);
        synchronized (this.mNotifyDataSwitchInfo) {
            this.mNotifyDataSwitchInfo.add(r);
        }
    }

    public void handleMessage(Message msg) {
        Integer phoneId;
        long[] subId;
        AsyncResult ar = null;
        if (!("DCGG".equals("DGG") || "DGG".equals("DGG") || "DCGS".equals("DGG") || "DCGGS".equals("DGG"))) {
            ar = msg.obj;
        }
        Rlog.d(LOG_TAG, "handleMessage msg=" + msg);
        switch (msg.what) {
            case 1:
                if ("DCGG".equals("DGG") || "DGG".equals("DGG") || "DCGS".equals("DGG") || "DCGGS".equals("DGG")) {
                    ar = msg.obj;
                }
                phoneId = ar.userObj;
                int prefPhoneId = this.mSubController.getPhoneId(this.mSubController.getDefaultDataSubId());
                if (this.mSubController.getDefaultDataSubId() == -1000 && SubscriptionManager.isValidPhoneId(phoneId.intValue()) && TelephonyManager.getDefault().getPhoneCount() == 2) {
                    Rlog.d(LOG_TAG, "default data sub is not initilized, try to set one ");
                    prefPhoneId = phoneId.intValue() == 0 ? 1 : 0;
                }
                Rlog.d(LOG_TAG, "EVENT_ALL_DATA_DISCONNECTED phoneId :" + phoneId);
                this.mPhones[prefPhoneId].unregisterForAllDataDisconnected(this);
                if ("DCGG".equals("DGG") || "DGG".equals("DGG") || "DCGS".equals("DGG") || "DCGGS".equals("DGG")) {
                    Global.putLong(this.mContext.getContentResolver(), "multi_sim_data_call", SubscriptionController.getInstance().getSubId(phoneId.intValue())[0]);
                    Global.putInt(this.mContext.getContentResolver(), "multi_sim_data_call_slot", phoneId.intValue());
                }
                if ("DCGGS".equals("DGG") && "GSM".equals(SystemProperties.get("gsm.sim.selectnetwork", "CDMA"))) {
                    Rlog.d(LOG_TAG, "SetDataSubscription in GG mode. phoneId=  :" + phoneId);
                    ((PhoneBase) this.mPhones[phoneId.intValue()].getActivePhone()).mDcTracker.setDataSubscription(phoneId.intValue() == 0 ? 0 : 1);
                }
                if ("DCGG".equals("DGG") || "DGG".equals("DGG") || "DCGS".equals("DGG") || "DCGGS".equals("DGG")) {
                    ((PhoneBase) this.mPhones[phoneId.intValue()].getActivePhone()).mDcTracker.setDataAllowed(true, Message.obtain(this, 2, new Integer(phoneId.intValue())));
                    return;
                }
            case 2:
                break;
            case 3:
                phoneId = (Integer) ar.userObj;
                subId = this.mSubController.getSubId(phoneId.intValue());
                Rlog.d(LOG_TAG, "EVENT_SET_DATA_ALLOW_FOR_MMS_DONE  phoneId=" + phoneId + ", subId[0]=" + subId[0] + ", ar.result=" + ar.result + ", (ar.exception == null)=>" + (ar.exception == null));
                this.mPhones[phoneId.intValue()].updateDataConnectionTracker();
                if ("CG".equals("DGG") || "GG".equals("DGG")) {
                    boolean z;
                    long j = subId[0];
                    if (ar.exception == null) {
                        z = true;
                    } else {
                        z = false;
                    }
                    broadcastDefaultDataSubIdCallback(j, z);
                    return;
                }
                return;
            default:
                return;
        }
        if ("DCGG".equals("DGG") || "DGG".equals("DGG") || "DCGS".equals("DGG") || "DCGGS".equals("DGG")) {
            phoneId = (Integer) msg.obj;
        } else {
            phoneId = (Integer) ar.userObj;
        }
        subId = this.mSubController.getSubId(phoneId.intValue());
        Rlog.d(LOG_TAG, "EVENT_SET_DATA_ALLOWED_DONE  phoneId=" + phoneId + ", subId[0]=" + subId[0]);
        if ("DCGG".equals("DGG") || "DGG".equals("DGG") || "DCGS".equals("DGG") || "DCGGS".equals("DGG")) {
            Rlog.d(LOG_TAG, "[broadcastDefaultDataSubIdChanged] subId=" + subId[0]);
            Intent intent = new Intent("android.intent.action.ACTION_DEFAULT_DATA_SUBSCRIPTION_CHANGED");
            intent.addFlags(536870912);
            intent.putExtra("subscription", subId[0]);
            this.mContext.sendStickyBroadcastAsUser(intent, UserHandle.ALL);
        }
        this.mNotifyDataSwitchInfo.notifyRegistrants(new AsyncResult(null, Long.valueOf(subId[0]), null));
        this.mPhones[phoneId.intValue()].updateDataConnectionTracker();
    }
}
