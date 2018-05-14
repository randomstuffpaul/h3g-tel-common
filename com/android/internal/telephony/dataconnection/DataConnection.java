package com.android.internal.telephony.dataconnection;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.LinkProperties;
import android.net.NetworkAgent;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.net.NetworkInfo.DetailedState;
import android.net.ProxyInfo;
import android.os.AsyncResult;
import android.os.Build;
import android.os.Looper;
import android.os.Message;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.provider.Settings.Global;
import android.telephony.Rlog;
import android.telephony.ServiceState;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Pair;
import android.util.Patterns;
import android.util.TimeUtils;
import com.android.internal.telephony.CommandException;
import com.android.internal.telephony.CommandException.Error;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneBase;
import com.android.internal.telephony.RetryManager;
import com.android.internal.telephony.cdma.sms.SmsEnvelope;
import com.android.internal.telephony.dataconnection.DataCallResponse.SetupResult;
import com.android.internal.telephony.uicc.IccRecords;
import com.android.internal.util.AsyncChannel;
import com.android.internal.util.State;
import com.android.internal.util.StateMachine;
import com.google.android.mms.pdu.CharacterSets;
import com.sec.android.app.CscFeature;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.FileDescriptor;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

public final class DataConnection extends StateMachine {
    static final int BASE = 262144;
    private static final String CBS_DATA_RETRY_CONFIG_FOR_SINGTEL = "max_retries=1, 10000";
    private static final int CMD_TO_STRING_COUNT = 15;
    private static final boolean DBG = true;
    private static final String DEFAULT_DATA_RETRY_CONFIG = "max_retries=infinite,default_randomization=2000,5000,10000,20000,40000,80000:5000,160000:5000,320000:5000,640000:5000,1280000:5000,1800000:5000";
    static final int EVENT_CONNECT = 262144;
    static final int EVENT_DATA_CONNECTION_DRS_OR_RAT_CHANGED = 262155;
    static final int EVENT_DATA_CONNECTION_ROAM_OFF = 262157;
    static final int EVENT_DATA_CONNECTION_ROAM_ON = 262156;
    static final int EVENT_DATA_CONNECTION_STATE_CHANGED = 262158;
    static final int EVENT_DATA_STATE_CHANGED = 262151;
    static final int EVENT_DEACTIVATE_DONE = 262147;
    static final int EVENT_DISCONNECT = 262148;
    static final int EVENT_DISCONNECT_ALL = 262150;
    static final int EVENT_GET_LAST_FAIL_DONE = 262146;
    static final int EVENT_LOST_CONNECTION = 262153;
    static final int EVENT_MAX = 262158;
    static final int EVENT_RETRY_CONNECTION = 262154;
    static final int EVENT_RIL_CONNECTED = 262149;
    static final int EVENT_SETUP_DATA_CONNECTION_DONE = 262145;
    static final int EVENT_TEAR_DOWN_NOW = 262152;
    private static final String IMS_DATA_RETRY_CONFIG = "max_retries=infinite,default_randomization=2000,4000,8000,12000,16000,20000";
    private static final String LOG_LEVEL_PROP = "ro.debug_level";
    private static final String LOG_LEVEL_PROP_HIGH = "0x4948";
    private static final String LOG_LEVEL_PROP_LOW = "0x4f4c";
    private static final String LOG_LEVEL_PROP_MID = "0x494d";
    private static final String NETWORK_TYPE = "MOBILE";
    private static final String NULL_IP = "0.0.0.0";
    private static final String SECONDARY_DATA_RETRY_CONFIG = "max_retries=3, 5000, 5000, 5000";
    private static final String TCP_BUFFER_SIZES_1XRTT = "16384,32768,131072,4096,16384,102400";
    private static final String TCP_BUFFER_SIZES_EDGE = "4093,26280,70800,4096,16384,70800";
    private static final String TCP_BUFFER_SIZES_EHRPD = "131072,262144,1048576,4096,16384,524288";
    private static final String TCP_BUFFER_SIZES_EVDO = "4094,87380,262144,4096,16384,262144";
    private static final String TCP_BUFFER_SIZES_GPRS = "4092,8760,48000,4096,8760,48000";
    private static final String TCP_BUFFER_SIZES_HSDPA = "61167,367002,1101005,8738,52429,262114";
    private static final String TCP_BUFFER_SIZES_HSPA = "40778,244668,734003,16777,100663,301990";
    private static final String TCP_BUFFER_SIZES_HSPAP = "122334,734003,2202010,32040,192239,576717";
    private static final String TCP_BUFFER_SIZES_LTE = "524288,1048576,2097152,262144,524288,1048576";
    private static final String TCP_BUFFER_SIZES_UMTS = "58254,349525,1048576,58254,349525,1048576";
    private static final boolean VDBG = true;
    private static AtomicInteger mInstanceNumber = new AtomicInteger(0);
    private static String[] sCmdToString = new String[15];
    private AsyncChannel mAc;
    private DcActivatingState mActivatingState = new DcActivatingState();
    private DcActiveState mActiveState = new DcActiveState();
    List<ApnContext> mApnContexts = null;
    private ApnSetting mApnSetting;
    int mCid;
    private ConnectionParams mConnectionParams;
    private long mCreateTime;
    private int mDataRegState = Integer.MAX_VALUE;
    private DcController mDcController;
    private DcFailCause mDcFailCause;
    private DcRetryAlarmController mDcRetryAlarmController;
    private DcTesterFailBringUpAll mDcTesterFailBringUpAll;
    private DcTrackerBase mDct = null;
    private DcDefaultState mDefaultState = new DcDefaultState();
    private DisconnectParams mDisconnectParams;
    private DcDisconnectionErrorCreatingConnection mDisconnectingErrorCreatingConnection = new DcDisconnectionErrorCreatingConnection();
    private DcDisconnectingState mDisconnectingState = new DcDisconnectingState();
    private LinkProperties mDummylp = new LinkProperties();
    private String mFixedApnType = null;
    private int mId;
    private DcInactiveState mInactiveState = new DcInactiveState();
    private DcFailCause mLastFailCause;
    private long mLastFailTime;
    private LinkProperties mLinkProperties = new LinkProperties();
    private NetworkAgent mNetworkAgent;
    private NetworkInfo mNetworkInfo;
    protected String[] mPcscfAddr;
    private PhoneBase mPhone;
    PendingIntent mReconnectIntent = null;
    RetryManager mRetryManager = new RetryManager();
    private DcRetryingState mRetryingState = new DcRetryingState();
    private int mRilRat = Integer.MAX_VALUE;
    int mTag;
    private Object mUserData;

    static /* synthetic */ class C00851 {
        static final /* synthetic */ int[] f13xd987aa80 = new int[SetupResult.values().length];

        static {
            try {
                f13xd987aa80[SetupResult.SUCCESS.ordinal()] = 1;
            } catch (NoSuchFieldError e) {
            }
            try {
                f13xd987aa80[SetupResult.ERR_BadCommand.ordinal()] = 2;
            } catch (NoSuchFieldError e2) {
            }
            try {
                f13xd987aa80[SetupResult.ERR_UnacceptableParameter.ordinal()] = 3;
            } catch (NoSuchFieldError e3) {
            }
            try {
                f13xd987aa80[SetupResult.ERR_GetLastErrorFromRil.ordinal()] = 4;
            } catch (NoSuchFieldError e4) {
            }
            try {
                f13xd987aa80[SetupResult.ERR_RilError.ordinal()] = 5;
            } catch (NoSuchFieldError e5) {
            }
            try {
                f13xd987aa80[SetupResult.ERR_Stale.ordinal()] = 6;
            } catch (NoSuchFieldError e6) {
            }
        }
    }

    public static class ConnectionParams {
        ApnContext mApnContext;
        int mInitialMaxRetry;
        Message mOnCompletedMsg;
        int mProfileId;
        boolean mRetryWhenSSChange;
        int mRilRat;
        int mTag;

        ConnectionParams(ApnContext apnContext, int initialMaxRetry, int profileId, int rilRadioTechnology, boolean retryWhenSSChange, Message onCompletedMsg) {
            this.mApnContext = apnContext;
            this.mInitialMaxRetry = initialMaxRetry;
            this.mProfileId = profileId;
            this.mRilRat = rilRadioTechnology;
            this.mRetryWhenSSChange = retryWhenSSChange;
            this.mOnCompletedMsg = onCompletedMsg;
        }

        public String toString() {
            return "{mTag=" + this.mTag + " mApnContext=" + this.mApnContext + " mInitialMaxRetry=" + this.mInitialMaxRetry + " mProfileId=" + this.mProfileId + " mRat=" + this.mRilRat + " mOnCompletedMsg=" + DataConnection.msgToString(this.mOnCompletedMsg) + "}";
        }

        public ApnContext getApnContext() {
            if (CscFeature.getInstance().getEnableStatus("CscFeature_Common_EnableItsOn")) {
                return this.mApnContext;
            }
            return null;
        }
    }

    private class DcActivatingState extends State {
        private DcActivatingState() {
        }

        public boolean processMessage(Message msg) {
            DataConnection.this.log("DcActivatingState: msg=" + DataConnection.msgToString(msg));
            AsyncResult ar;
            ConnectionParams cp;
            switch (msg.what) {
                case SmsEnvelope.TELESERVICE_MWI /*262144*/:
                case DataConnection.EVENT_DATA_CONNECTION_DRS_OR_RAT_CHANGED /*262155*/:
                    DataConnection.this.deferMessage(msg);
                    return true;
                case DataConnection.EVENT_SETUP_DATA_CONNECTION_DONE /*262145*/:
                    ar = msg.obj;
                    cp = ar.userObj;
                    SetupResult result = DataConnection.this.onSetupConnectionCompleted(ar);
                    if (!(result == SetupResult.ERR_Stale || DataConnection.this.mConnectionParams == cp)) {
                        DataConnection.this.loge("DcActivatingState: WEIRD mConnectionsParams:" + DataConnection.this.mConnectionParams + " != cp:" + cp);
                    }
                    DataConnection.this.log("DcActivatingState onSetupConnectionCompleted result=" + result + " dc=" + DataConnection.this);
                    switch (C00851.f13xd987aa80[result.ordinal()]) {
                        case 1:
                            DataConnection.this.mDcFailCause = DcFailCause.NONE;
                            DataConnection.this.transitionTo(DataConnection.this.mActiveState);
                            break;
                        case 2:
                            DataConnection.this.mInactiveState.setEnterNotificationParams(cp, result.mFailCause);
                            DataConnection.this.transitionTo(DataConnection.this.mInactiveState);
                            break;
                        case 3:
                            DataConnection.this.tearDownData(cp);
                            DataConnection.this.transitionTo(DataConnection.this.mDisconnectingErrorCreatingConnection);
                            break;
                        case 4:
                            DataConnection.this.mPhone.mCi.getLastDataCallFailCause(DataConnection.this.obtainMessage(DataConnection.EVENT_GET_LAST_FAIL_DONE, cp));
                            break;
                        case 5:
                            int delay = DataConnection.this.mDcRetryAlarmController.getSuggestedRetryTime(DataConnection.this, ar);
                            DataConnection.this.log("DcActivatingState: ERR_RilError  delay=" + delay + " isRetryNeeded=" + DataConnection.this.mRetryManager.isRetryNeeded() + " result=" + result + " result.isRestartRadioFail=" + result.mFailCause.isRestartRadioFail() + " result.isPermanentFail=" + DataConnection.this.mDct.isPermanentFail(result.mFailCause));
                            if (!result.mFailCause.isRestartRadioFail()) {
                                if (!DataConnection.this.mDct.isPermanentFail(result.mFailCause)) {
                                    if (delay < 0) {
                                        DataConnection.this.log("DcActivatingState: ERR_RilError no retry");
                                        DataConnection.this.mInactiveState.setEnterNotificationParams(cp, result.mFailCause);
                                        DataConnection.this.transitionTo(DataConnection.this.mInactiveState);
                                        break;
                                    }
                                    DataConnection.this.log("DcActivatingState: ERR_RilError retry");
                                    if (delay == 20000) {
                                        DataConnection.this.mPhone.getContext().sendBroadcast(new Intent("com.android.sec.mobilecare.data.retry"));
                                    }
                                    DataConnection.this.mDcRetryAlarmController.startRetryAlarm(DataConnection.EVENT_RETRY_CONNECTION, DataConnection.this.mTag, delay);
                                    DataConnection.this.transitionTo(DataConnection.this.mRetryingState);
                                    break;
                                }
                                DataConnection.this.log("DcActivatingState: ERR_RilError perm error");
                                DataConnection.this.mPhone.getContext().sendBroadcast(new Intent("com.android.sec.mobilecare.data.permanentfail"));
                                DataConnection.this.mInactiveState.setEnterNotificationParams(cp, result.mFailCause);
                                DataConnection.this.transitionTo(DataConnection.this.mInactiveState);
                                break;
                            }
                            DataConnection.this.log("DcActivatingState: ERR_RilError restart radio");
                            DataConnection.this.mDct.sendRestartRadio();
                            DataConnection.this.mInactiveState.setEnterNotificationParams(cp, result.mFailCause);
                            DataConnection.this.transitionTo(DataConnection.this.mInactiveState);
                            break;
                        case 6:
                            DataConnection.this.loge("DcActivatingState: stale EVENT_SETUP_DATA_CONNECTION_DONE tag:" + cp.mTag + " != mTag:" + DataConnection.this.mTag);
                            break;
                        default:
                            throw new RuntimeException("Unknown SetupResult, should not happen");
                    }
                    return true;
                case DataConnection.EVENT_GET_LAST_FAIL_DONE /*262146*/:
                    ar = (AsyncResult) msg.obj;
                    cp = (ConnectionParams) ar.userObj;
                    if (cp.mTag == DataConnection.this.mTag) {
                        if (DataConnection.this.mConnectionParams != cp) {
                            DataConnection.this.loge("DcActivatingState: WEIRD mConnectionsParams:" + DataConnection.this.mConnectionParams + " != cp:" + cp);
                        }
                        DcFailCause cause = DcFailCause.UNKNOWN;
                        if (ar.exception == null) {
                            cause = DcFailCause.fromInt(((int[]) ar.result)[0]);
                            if (cause == DcFailCause.NONE) {
                                DataConnection.this.log("DcActivatingState msg.what=EVENT_GET_LAST_FAIL_DONE BAD: error was NONE, change to UNKNOWN");
                                cause = DcFailCause.UNKNOWN;
                            }
                        }
                        DataConnection.this.mDcFailCause = cause;
                        int retryDelay = DataConnection.this.mRetryManager.getRetryTimer();
                        DataConnection.this.log("DcActivatingState msg.what=EVENT_GET_LAST_FAIL_DONE cause=" + cause + " retryDelay=" + retryDelay + " isRetryNeeded=" + DataConnection.this.mRetryManager.isRetryNeeded() + " dc=" + DataConnection.this);
                        if (cause.isRestartRadioFail()) {
                            DataConnection.this.log("DcActivatingState: EVENT_GET_LAST_FAIL_DONE restart radio");
                            DataConnection.this.mDct.sendRestartRadio();
                            DataConnection.this.mInactiveState.setEnterNotificationParams(cp, cause);
                            DataConnection.this.transitionTo(DataConnection.this.mInactiveState);
                        } else if (DataConnection.this.mDct.isPermanentFail(cause)) {
                            DataConnection.this.log("DcActivatingState: EVENT_GET_LAST_FAIL_DONE perm er");
                            DataConnection.this.mInactiveState.setEnterNotificationParams(cp, cause);
                            DataConnection.this.transitionTo(DataConnection.this.mInactiveState);
                        } else if (retryDelay < 0 || !DataConnection.this.mRetryManager.isRetryNeeded()) {
                            DataConnection.this.log("DcActivatingState: EVENT_GET_LAST_FAIL_DONE no retry");
                            DataConnection.this.mInactiveState.setEnterNotificationParams(cp, cause);
                            DataConnection.this.transitionTo(DataConnection.this.mInactiveState);
                        } else {
                            DataConnection.this.log("DcActivatingState: EVENT_GET_LAST_FAIL_DONE retry");
                            DataConnection.this.mDcRetryAlarmController.startRetryAlarm(DataConnection.EVENT_RETRY_CONNECTION, DataConnection.this.mTag, retryDelay);
                            DataConnection.this.transitionTo(DataConnection.this.mRetryingState);
                        }
                    } else {
                        DataConnection.this.loge("DcActivatingState: stale EVENT_GET_LAST_FAIL_DONE tag:" + cp.mTag + " != mTag:" + DataConnection.this.mTag);
                    }
                    return true;
                default:
                    DataConnection.this.log("DcActivatingState not handled msg.what=" + DataConnection.this.getWhatToString(msg.what) + " RefCount=" + DataConnection.this.mApnContexts.size());
                    return false;
            }
        }
    }

    private class DcActiveState extends State {
        private DcActiveState() {
        }

        /* JADX WARNING: inconsistent code. */
        /* Code decompiled incorrectly, please refer to instructions dump. */
        public void enter() {
            /*
            r13 = this;
            r1 = 0;
            r2 = -1;
            r0 = "ro.csc.sales_code";
            r11 = android.os.SystemProperties.get(r0);
            r0 = com.android.internal.telephony.dataconnection.DataConnection.this;
            r3 = new java.lang.StringBuilder;
            r3.<init>();
            r4 = "DcActiveState: enter dc=";
            r3 = r3.append(r4);
            r4 = com.android.internal.telephony.dataconnection.DataConnection.this;
            r3 = r3.append(r4);
            r3 = r3.toString();
            r0.log(r3);
            r0 = "DCGG";
            r3 = "DGG";
            r0 = r0.equals(r3);
            if (r0 != 0) goto L_0x006a;
        L_0x002c:
            r0 = "DCGS";
            r3 = "DGG";
            r0 = r0.equals(r3);
            if (r0 != 0) goto L_0x006a;
        L_0x0036:
            r0 = "DCGGS";
            r3 = "DGG";
            r0 = r0.equals(r3);
            if (r0 != 0) goto L_0x006a;
        L_0x0040:
            r0 = "DGG";
            r3 = "DGG";
            r0 = r0.equals(r3);
            if (r0 != 0) goto L_0x006a;
        L_0x004a:
            r0 = "CHN";
            r0 = r0.equals(r11);
            if (r0 != 0) goto L_0x006a;
        L_0x0052:
            r0 = "CHM";
            r0 = r0.equals(r11);
            if (r0 != 0) goto L_0x006a;
        L_0x005a:
            r0 = "CHC";
            r0 = r0.equals(r11);
            if (r0 != 0) goto L_0x006a;
        L_0x0062:
            r0 = "CHU";
            r0 = r0.equals(r11);
            if (r0 == 0) goto L_0x00ea;
        L_0x006a:
            r0 = com.android.internal.telephony.dataconnection.DataConnection.this;
            r0 = r0.mConnectionParams;
            if (r0 == 0) goto L_0x00ea;
        L_0x0072:
            r0 = com.android.internal.telephony.dataconnection.DataConnection.this;
            r0 = r0.mConnectionParams;
            r9 = r0.mApnContext;
            r10 = -1;
            r0 = r9.getApnType();
            r3 = r0.hashCode();
            switch(r3) {
                case 108243: goto L_0x019d;
                case 3355583: goto L_0x01a8;
                default: goto L_0x0086;
            };
        L_0x0086:
            r0 = r2;
        L_0x0087:
            switch(r0) {
                case 0: goto L_0x01b3;
                case 1: goto L_0x01b6;
                default: goto L_0x008a;
            };
        L_0x008a:
            r10 = 0;
            r0 = com.android.internal.telephony.dataconnection.DataConnection.this;
            r3 = "other type which is not mms/mms2 is handled as mobile";
            r0.log(r3);
        L_0x0092:
            if (r10 == r2) goto L_0x00ea;
        L_0x0094:
            r0 = com.android.internal.telephony.dataconnection.DataConnection.this;
            r0 = r0.mNetworkInfo;
            r0 = r0.getType();
            if (r10 == r0) goto L_0x00ea;
        L_0x00a0:
            r0 = com.android.internal.telephony.dataconnection.DataConnection.this;
            r2 = new java.lang.StringBuilder;
            r2.<init>();
            r3 = "register network info for ";
            r2 = r2.append(r3);
            r3 = android.net.ConnectivityManager.getNetworkTypeName(r10);
            r2 = r2.append(r3);
            r3 = ", old network: ";
            r2 = r2.append(r3);
            r3 = com.android.internal.telephony.dataconnection.DataConnection.this;
            r3 = r3.mNetworkInfo;
            r3 = r3.getType();
            r3 = android.net.ConnectivityManager.getNetworkTypeName(r3);
            r2 = r2.append(r3);
            r2 = r2.toString();
            r0.log(r2);
            r0 = com.android.internal.telephony.dataconnection.DataConnection.this;
            r0 = r0.mNetworkInfo;
            r0.setType(r10);
            r0 = com.android.internal.telephony.dataconnection.DataConnection.this;
            r0 = r0.mNetworkInfo;
            r2 = android.net.ConnectivityManager.getNetworkTypeName(r10);
            r0.setTypeName(r2);
        L_0x00ea:
            r0 = com.android.internal.telephony.dataconnection.DataConnection.this;
            r0 = r0.mRetryManager;
            r0 = r0.getRetryCount();
            if (r0 == 0) goto L_0x0102;
        L_0x00f4:
            r0 = com.android.internal.telephony.dataconnection.DataConnection.this;
            r2 = "DcActiveState: connected after retrying call notifyAllOfConnected";
            r0.log(r2);
            r0 = com.android.internal.telephony.dataconnection.DataConnection.this;
            r0 = r0.mRetryManager;
            r0.setRetryCount(r1);
        L_0x0102:
            r0 = com.android.internal.telephony.dataconnection.DataConnection.this;
            r1 = "connected";
            r0.notifyAllOfConnected(r1);
            r0 = com.android.internal.telephony.dataconnection.DataConnection.this;
            r0 = r0.mRetryManager;
            r0.restoreCurMaxRetryCount();
            r0 = com.android.internal.telephony.dataconnection.DataConnection.this;
            r0 = r0.mApnSetting;
            if (r0 == 0) goto L_0x0129;
        L_0x0118:
            r0 = com.android.internal.telephony.dataconnection.DataConnection.this;
            r1 = com.android.internal.telephony.dataconnection.DataConnection.this;
            r1 = r1.mApnSetting;
            r2 = "default";
            r1 = r1.canHandleType(r2);
            r0.configureRetry(r1);
        L_0x0129:
            r0 = com.android.internal.telephony.dataconnection.DataConnection.this;
            r0 = r0.mDcController;
            r1 = com.android.internal.telephony.dataconnection.DataConnection.this;
            r0.addActiveDcByCid(r1);
            r0 = com.android.internal.telephony.dataconnection.DataConnection.this;
            r0 = r0.mNetworkInfo;
            r1 = android.net.NetworkInfo.DetailedState.CONNECTED;
            r2 = com.android.internal.telephony.dataconnection.DataConnection.this;
            r2 = r2.mNetworkInfo;
            r2 = r2.getReason();
            r3 = 0;
            r0.setDetailedState(r1, r2, r3);
            r0 = com.android.internal.telephony.dataconnection.DataConnection.this;
            r0 = r0.mNetworkInfo;
            r1 = com.android.internal.telephony.dataconnection.DataConnection.this;
            r1 = r1.mApnSetting;
            r1 = r1.apn;
            r0.setExtraInfo(r1);
            r0 = com.android.internal.telephony.dataconnection.DataConnection.this;
            r1 = com.android.internal.telephony.dataconnection.DataConnection.this;
            r1 = r1.mRilRat;
            r0.updateTcpBufferSizes(r1);
            r12 = com.android.internal.telephony.dataconnection.DataConnection.this;
            r0 = new com.android.internal.telephony.dataconnection.DataConnection$DcNetworkAgent;
            r1 = com.android.internal.telephony.dataconnection.DataConnection.this;
            r2 = com.android.internal.telephony.dataconnection.DataConnection.this;
            r2 = r2.getHandler();
            r2 = r2.getLooper();
            r3 = com.android.internal.telephony.dataconnection.DataConnection.this;
            r3 = r3.mPhone;
            r3 = r3.getContext();
            r4 = "DcNetworkAgent";
            r5 = com.android.internal.telephony.dataconnection.DataConnection.this;
            r5 = r5.mNetworkInfo;
            r6 = com.android.internal.telephony.dataconnection.DataConnection.this;
            r6 = r6.makeNetworkCapabilities();
            r7 = com.android.internal.telephony.dataconnection.DataConnection.this;
            r7 = r7.mLinkProperties;
            r8 = 50;
            r0.<init>(r2, r3, r4, r5, r6, r7, r8);
            r12.mNetworkAgent = r0;
            return;
        L_0x019d:
            r3 = "mms";
            r0 = r0.equals(r3);
            if (r0 == 0) goto L_0x0086;
        L_0x01a5:
            r0 = r1;
            goto L_0x0087;
        L_0x01a8:
            r3 = "mms2";
            r0 = r0.equals(r3);
            if (r0 == 0) goto L_0x0086;
        L_0x01b0:
            r0 = 1;
            goto L_0x0087;
        L_0x01b3:
            r10 = 2;
            goto L_0x0092;
        L_0x01b6:
            r10 = 26;
            goto L_0x0092;
            */
            throw new UnsupportedOperationException("Method not decompiled: com.android.internal.telephony.dataconnection.DataConnection.DcActiveState.enter():void");
        }

        public void exit() {
            DataConnection.this.log("DcActiveState: exit dc=" + this);
            DataConnection.this.mNetworkInfo.setDetailedState(DetailedState.DISCONNECTED, DataConnection.this.mNetworkInfo.getReason(), DataConnection.this.mNetworkInfo.getExtraInfo());
            DataConnection.this.mNetworkAgent.sendNetworkInfo(DataConnection.this.mNetworkInfo);
            DataConnection.this.mNetworkAgent = null;
        }

        public boolean processMessage(Message msg) {
            DisconnectParams dp;
            switch (msg.what) {
                case SmsEnvelope.TELESERVICE_MWI /*262144*/:
                    ConnectionParams cp = msg.obj;
                    if (DataConnection.this.isDebugLevelNotLow()) {
                        DataConnection.this.log("DcActiveState: EVENT_CONNECT cp=" + cp + " dc=" + DataConnection.this);
                    }
                    if (DataConnection.this.mApnContexts.contains(cp.mApnContext)) {
                        DataConnection.this.log("DcActiveState ERROR already added apnContext=" + cp.mApnContext);
                    } else {
                        DataConnection.this.mApnContexts.add(cp.mApnContext);
                        DataConnection.this.log("DcActiveState msg.what=EVENT_CONNECT RefCount=" + DataConnection.this.mApnContexts.size());
                        if (DataConnection.this.mDct.IncludeFixedApn(cp.mApnContext.getApnType()) && DataConnection.this.mApnContexts.size() > 1) {
                            DataConnection.this.mFixedApnType = cp.mApnContext.getApnType();
                            DataConnection.this.mNetworkAgent.sendNetworkCapabilities(DataConnection.this.makeNetworkCapabilities(cp.mApnContext.getApnType()));
                        }
                    }
                    DataConnection.this.notifyConnectCompleted(cp, DcFailCause.NONE, false);
                    return true;
                case DataConnection.EVENT_DISCONNECT /*262148*/:
                    dp = msg.obj;
                    if (DataConnection.this.isDebugLevelNotLow()) {
                        DataConnection.this.log("DcActiveState: EVENT_DISCONNECT dp=" + dp + " dc=" + DataConnection.this);
                    }
                    if (DataConnection.this.mApnContexts.contains(dp.mApnContext)) {
                        DataConnection.this.log("DcActiveState msg.what=EVENT_DISCONNECT RefCount=" + DataConnection.this.mApnContexts.size());
                        if (DataConnection.this.mApnContexts.size() == 1) {
                            DataConnection.this.mApnContexts.clear();
                            DataConnection.this.mDisconnectParams = dp;
                            DataConnection.this.mConnectionParams = null;
                            dp.mTag = DataConnection.this.mTag;
                            DataConnection.this.tearDownData(dp);
                            DataConnection.this.transitionTo(DataConnection.this.mDisconnectingState);
                        } else {
                            DataConnection.this.mApnContexts.remove(dp.mApnContext);
                            if (DataConnection.this.mDct.IncludeFixedApn(dp.mApnContext.getApnType())) {
                                DataConnection.this.mNetworkAgent.sendNetworkCapabilities(DataConnection.this.makeNetworkCapabilities());
                                DataConnection.this.mFixedApnType = null;
                            }
                            DataConnection.this.notifyDisconnectCompleted(dp, false);
                        }
                    } else {
                        DataConnection.this.log("DcActiveState ERROR no such apnContext=" + dp.mApnContext + " in this dc=" + DataConnection.this);
                        DataConnection.this.notifyDisconnectCompleted(dp, false);
                    }
                    return true;
                case DataConnection.EVENT_DISCONNECT_ALL /*262150*/:
                    DataConnection.this.log("DcActiveState EVENT_DISCONNECT clearing apn contexts, dc=" + DataConnection.this);
                    dp = (DisconnectParams) msg.obj;
                    DataConnection.this.mDisconnectParams = dp;
                    DataConnection.this.mConnectionParams = null;
                    dp.mTag = DataConnection.this.mTag;
                    DataConnection.this.tearDownData(dp);
                    DataConnection.this.transitionTo(DataConnection.this.mDisconnectingState);
                    return true;
                case DataConnection.EVENT_LOST_CONNECTION /*262153*/:
                    DataConnection.this.log("DcActiveState EVENT_LOST_CONNECTION dc=" + DataConnection.this);
                    boolean retry = true;
                    if ("CG".equals("DGG") || "GG".equals("DGG")) {
                        int slotId = SystemProperties.getInt("ril.datacross.slotid", -1);
                        int phoneId = SubscriptionManager.getDefaultDataPhoneId();
                        if (!(slotId == -1 || DataConnection.this.mPhone.getPhoneId() == slotId) || (slotId == -1 && phoneId != DataConnection.this.mPhone.getPhoneId())) {
                            retry = false;
                        }
                        DataConnection.this.log("DcActiveState EVENT_LOST_CONNECTION mPhone.getPhoneId()=" + DataConnection.this.mPhone.getPhoneId() + ", datacross.slotid=" + slotId + ", getDefaultDataPhoneId()=" + phoneId + ", retry=" + retry);
                    }
                    if (DataConnection.this.mRetryManager.isRetryNeeded() && retry) {
                        int delayMillis = DataConnection.this.mRetryManager.getRetryTimer();
                        DataConnection.this.log("DcActiveState EVENT_LOST_CONNECTION startRetryAlarm mTag=" + DataConnection.this.mTag + " delay=" + delayMillis + "ms");
                        if ("DCGS".equals("DGG") && DataConnection.this.mPhone.getPhoneId() == 0 && DataConnection.this.mPhone.getPhoneType() == 2) {
                            DataConnection.this.forceDataDeactiveTracker();
                        }
                        DataConnection.this.mDcRetryAlarmController.startRetryAlarm(DataConnection.EVENT_RETRY_CONNECTION, DataConnection.this.mTag, delayMillis);
                        DataConnection.this.transitionTo(DataConnection.this.mRetryingState);
                    } else {
                        DataConnection.this.mInactiveState.setEnterNotificationParams(DcFailCause.LOST_CONNECTION);
                        DataConnection.this.transitionTo(DataConnection.this.mInactiveState);
                    }
                    return true;
                case DataConnection.EVENT_DATA_CONNECTION_ROAM_ON /*262156*/:
                    DataConnection.this.mNetworkInfo.setRoaming(true);
                    DataConnection.this.mNetworkAgent.sendNetworkInfo(DataConnection.this.mNetworkInfo);
                    return true;
                case DataConnection.EVENT_DATA_CONNECTION_ROAM_OFF /*262157*/:
                    DataConnection.this.mNetworkInfo.setRoaming(false);
                    DataConnection.this.mNetworkAgent.sendNetworkInfo(DataConnection.this.mNetworkInfo);
                    return true;
                default:
                    DataConnection.this.log("DcActiveState not handled msg.what=" + DataConnection.this.getWhatToString(msg.what));
                    return false;
            }
        }
    }

    private class DcDefaultState extends State {
        private DcDefaultState() {
        }

        public void enter() {
            DataConnection.this.log("DcDefaultState: enter");
            DataConnection.this.mPhone.getServiceStateTracker().registerForDataRegStateOrRatChanged(DataConnection.this.getHandler(), DataConnection.EVENT_DATA_CONNECTION_DRS_OR_RAT_CHANGED, null);
            DataConnection.this.mPhone.getServiceStateTracker().registerForRoamingOn(DataConnection.this.getHandler(), DataConnection.EVENT_DATA_CONNECTION_ROAM_ON, null);
            DataConnection.this.mPhone.getServiceStateTracker().registerForRoamingOff(DataConnection.this.getHandler(), DataConnection.EVENT_DATA_CONNECTION_ROAM_OFF, null);
            DataConnection.this.mPhone.registerForDataConnectionStateChanged(DataConnection.this.getHandler(), 262158, null);
            DataConnection.this.mDcController.addDc(DataConnection.this);
        }

        public void exit() {
            DataConnection.this.log("DcDefaultState: exit");
            if (!(DataConnection.this.mPhone.getServiceStateTracker() == null || DataConnection.this.getHandler() == null)) {
                DataConnection.this.mPhone.getServiceStateTracker().unregisterForDataRegStateOrRatChanged(DataConnection.this.getHandler());
                DataConnection.this.mPhone.getServiceStateTracker().unregisterForRoamingOn(DataConnection.this.getHandler());
                DataConnection.this.mPhone.getServiceStateTracker().unregisterForRoamingOff(DataConnection.this.getHandler());
                DataConnection.this.mPhone.unregisterForDataConnectionStateChanged(DataConnection.this.getHandler());
            }
            DataConnection.this.mDcController.removeDc(DataConnection.this);
            if (DataConnection.this.mAc != null) {
                DataConnection.this.mAc.disconnected();
                DataConnection.this.mAc = null;
            }
            DataConnection.this.mDcRetryAlarmController.dispose();
            DataConnection.this.mDcRetryAlarmController = null;
            DataConnection.this.mApnContexts = null;
            DataConnection.this.mReconnectIntent = null;
            DataConnection.this.mDct = null;
            DataConnection.this.mApnSetting = null;
            DataConnection.this.mPhone = null;
            DataConnection.this.mLinkProperties = null;
            DataConnection.this.mLastFailCause = null;
            DataConnection.this.mUserData = null;
            DataConnection.this.mDcController = null;
            DataConnection.this.mDcTesterFailBringUpAll = null;
        }

        public boolean processMessage(Message msg) {
            DataConnection.this.log("DcDefault msg=" + DataConnection.this.getWhatToString(msg.what) + " RefCount=" + DataConnection.this.mApnContexts.size());
            switch (msg.what) {
                case 69633:
                    if (DataConnection.this.mAc == null) {
                        DataConnection.this.mAc = new AsyncChannel();
                        DataConnection.this.mAc.connected(null, DataConnection.this.getHandler(), msg.replyTo);
                        DataConnection.this.log("DcDefaultState: FULL_CONNECTION reply connected");
                        DataConnection.this.mAc.replyToMessage(msg, 69634, 0, DataConnection.this.mId, "hi");
                        break;
                    }
                    DataConnection.this.log("Disconnecting to previous connection mAc=" + DataConnection.this.mAc);
                    DataConnection.this.mAc.replyToMessage(msg, 69634, 3);
                    break;
                case 69636:
                    DataConnection.this.log("CMD_CHANNEL_DISCONNECTED");
                    DataConnection.this.quit();
                    break;
                case SmsEnvelope.TELESERVICE_MWI /*262144*/:
                    DataConnection.this.log("DcDefaultState: msg.what=EVENT_CONNECT, fail not expected");
                    DataConnection.this.notifyConnectCompleted(msg.obj, DcFailCause.UNKNOWN, false);
                    break;
                case DataConnection.EVENT_DISCONNECT /*262148*/:
                    DataConnection.this.log("DcDefaultState deferring msg.what=EVENT_DISCONNECT RefCount=" + DataConnection.this.mApnContexts.size());
                    DataConnection.this.deferMessage(msg);
                    break;
                case DataConnection.EVENT_DISCONNECT_ALL /*262150*/:
                    DataConnection.this.log("DcDefaultState deferring msg.what=EVENT_DISCONNECT_ALL RefCount=" + DataConnection.this.mApnContexts.size());
                    DataConnection.this.deferMessage(msg);
                    break;
                case DataConnection.EVENT_TEAR_DOWN_NOW /*262152*/:
                    DataConnection.this.log("DcDefaultState EVENT_TEAR_DOWN_NOW");
                    DataConnection.this.mPhone.mCi.deactivateDataCall(DataConnection.this.mCid, 0, null);
                    break;
                case DataConnection.EVENT_LOST_CONNECTION /*262153*/:
                    DataConnection.this.logAndAddLogRec("DcDefaultState ignore EVENT_LOST_CONNECTION tag=" + msg.arg1 + ":mTag=" + DataConnection.this.mTag);
                    break;
                case DataConnection.EVENT_RETRY_CONNECTION /*262154*/:
                    DataConnection.this.logAndAddLogRec("DcDefaultState ignore EVENT_RETRY_CONNECTION tag=" + msg.arg1 + ":mTag=" + DataConnection.this.mTag);
                    break;
                case DataConnection.EVENT_DATA_CONNECTION_DRS_OR_RAT_CHANGED /*262155*/:
                    Pair<Integer, Integer> drsRatPair = msg.obj.result;
                    DataConnection.this.mDataRegState = ((Integer) drsRatPair.first).intValue();
                    if (DataConnection.this.mRilRat != ((Integer) drsRatPair.second).intValue()) {
                        DataConnection.this.updateTcpBufferSizes(((Integer) drsRatPair.second).intValue());
                    }
                    DataConnection.this.mRilRat = ((Integer) drsRatPair.second).intValue();
                    DataConnection.this.log("DcDefaultState: EVENT_DATA_CONNECTION_DRS_OR_RAT_CHANGED drs=" + DataConnection.this.mDataRegState + " mRilRat=" + DataConnection.this.mRilRat);
                    int networkType = DataConnection.this.mPhone.getServiceState().getDataNetworkType();
                    DataConnection.this.mNetworkInfo.setSubtype(networkType, TelephonyManager.getNetworkTypeName(networkType));
                    if (DataConnection.this.mNetworkAgent != null) {
                        DataConnection.this.mNetworkAgent.sendNetworkCapabilities(DataConnection.this.makeNetworkCapabilities());
                        DataConnection.this.mNetworkAgent.sendNetworkInfo(DataConnection.this.mNetworkInfo);
                        DataConnection.this.mNetworkAgent.sendLinkProperties(DataConnection.this.mLinkProperties);
                        break;
                    }
                    break;
                case DataConnection.EVENT_DATA_CONNECTION_ROAM_ON /*262156*/:
                    DataConnection.this.mNetworkInfo.setRoaming(true);
                    break;
                case DataConnection.EVENT_DATA_CONNECTION_ROAM_OFF /*262157*/:
                    DataConnection.this.mNetworkInfo.setRoaming(false);
                    break;
                case 262158:
                    Pair<Integer, String[]> statePair = ((AsyncResult) msg.obj).result;
                    DetailedState detailedState = DataConnection.this.getNiDetailedState(((Integer) statePair.first).intValue());
                    String reason = ((String[]) statePair.second)[0];
                    String apnType = ((String[]) statePair.second)[1];
                    boolean sendRequired = "default".equals(apnType);
                    if (!DataConnection.this.mApnContexts.contains((ApnContext) DataConnection.this.mDct.mApnContexts.get("default"))) {
                        sendRequired = false;
                        for (ApnContext apnContext : DataConnection.this.mApnContexts) {
                            if (apnContext.getApnType().equals(apnType)) {
                                sendRequired = true;
                            }
                        }
                    }
                    if (sendRequired) {
                        if (!(detailedState == DetailedState.SUSPENDED || DataConnection.this.mNetworkInfo.getDetailedState() == DetailedState.SUSPENDED)) {
                            sendRequired = false;
                        }
                        if ("ims".equals(apnType)) {
                            sendRequired = false;
                        }
                    }
                    if (DataConnection.this.mNetworkInfo != null && sendRequired) {
                        if (reason != null) {
                            DataConnection.this.log("setDetailed state, " + apnType + ", old = " + DataConnection.this.mNetworkInfo.getDetailedState() + " and new state = " + detailedState + ", reason = " + reason);
                        }
                        if (!(detailedState == DetailedState.IDLE || detailedState == DataConnection.this.mNetworkInfo.getDetailedState())) {
                            DataConnection.this.mNetworkInfo.setDetailedState(detailedState, reason, DataConnection.this.mNetworkInfo.getExtraInfo());
                            if (DataConnection.this.mNetworkAgent != null) {
                                DataConnection.this.mNetworkAgent.sendNetworkInfo(DataConnection.this.mNetworkInfo);
                                if (detailedState != DetailedState.SUSPENDED) {
                                    DataConnection.this.mNetworkAgent.sendLinkProperties(DataConnection.this.mLinkProperties);
                                    break;
                                }
                                DataConnection.this.mNetworkAgent.sendLinkProperties(DataConnection.this.mDummylp);
                                break;
                            }
                        }
                    }
                    break;
                case 266240:
                    boolean val = DataConnection.this.getIsInactive();
                    DataConnection.this.log("REQ_IS_INACTIVE  isInactive=" + val);
                    DataConnection.this.mAc.replyToMessage(msg, DcAsyncChannel.RSP_IS_INACTIVE, val ? 1 : 0);
                    break;
                case DcAsyncChannel.REQ_GET_CID /*266242*/:
                    int cid = DataConnection.this.getCid();
                    DataConnection.this.log("REQ_GET_CID  cid=" + cid);
                    DataConnection.this.mAc.replyToMessage(msg, DcAsyncChannel.RSP_GET_CID, cid);
                    break;
                case DcAsyncChannel.REQ_GET_APNSETTING /*266244*/:
                    ApnSetting apnSetting = DataConnection.this.getApnSetting();
                    DataConnection.this.log("REQ_GET_APNSETTING  mApnSetting=" + apnSetting);
                    DataConnection.this.mAc.replyToMessage(msg, DcAsyncChannel.RSP_GET_APNSETTING, apnSetting);
                    break;
                case DcAsyncChannel.REQ_GET_LINK_PROPERTIES /*266246*/:
                    LinkProperties lp = DataConnection.this.getCopyLinkProperties();
                    DataConnection.this.log("REQ_GET_LINK_PROPERTIES linkProperties" + lp);
                    DataConnection.this.mAc.replyToMessage(msg, DcAsyncChannel.RSP_GET_LINK_PROPERTIES, lp);
                    break;
                case DcAsyncChannel.REQ_SET_LINK_PROPERTIES_HTTP_PROXY /*266248*/:
                    ProxyInfo proxy = msg.obj;
                    DataConnection.this.log("REQ_SET_LINK_PROPERTIES_HTTP_PROXY proxy=" + proxy);
                    DataConnection.this.setLinkPropertiesHttpProxy(proxy);
                    DataConnection.this.mAc.replyToMessage(msg, DcAsyncChannel.RSP_SET_LINK_PROPERTIES_HTTP_PROXY);
                    break;
                case DcAsyncChannel.REQ_GET_NETWORK_CAPABILITIES /*266250*/:
                    NetworkCapabilities nc = DataConnection.this.getCopyNetworkCapabilities();
                    DataConnection.this.log("REQ_GET_NETWORK_CAPABILITIES networkCapabilities" + nc);
                    DataConnection.this.mAc.replyToMessage(msg, DcAsyncChannel.RSP_GET_NETWORK_CAPABILITIES, nc);
                    break;
                case DcAsyncChannel.REQ_RESET /*266252*/:
                    DataConnection.this.log("DcDefaultState: msg.what=REQ_RESET");
                    DataConnection.this.transitionTo(DataConnection.this.mInactiveState);
                    break;
                default:
                    DataConnection.this.log("DcDefaultState: shouldn't happen but ignore msg.what=" + DataConnection.this.getWhatToString(msg.what));
                    break;
            }
            return true;
        }
    }

    private class DcDisconnectingState extends State {
        private DcDisconnectingState() {
        }

        public boolean processMessage(Message msg) {
            switch (msg.what) {
                case SmsEnvelope.TELESERVICE_MWI /*262144*/:
                    DataConnection.this.log("DcDisconnectingState msg.what=EVENT_CONNECT. Defer. RefCount = " + DataConnection.this.mApnContexts.size());
                    DataConnection.this.deferMessage(msg);
                    return true;
                case DataConnection.EVENT_DEACTIVATE_DONE /*262147*/:
                    DataConnection.this.log("DcDisconnectingState msg.what=EVENT_DEACTIVATE_DONE RefCount=" + DataConnection.this.mApnContexts.size());
                    AsyncResult ar = msg.obj;
                    DisconnectParams dp = ar.userObj;
                    if (dp.mTag == DataConnection.this.mTag) {
                        DataConnection.this.mInactiveState.setEnterNotificationParams((DisconnectParams) ar.userObj);
                        DataConnection.this.transitionTo(DataConnection.this.mInactiveState);
                    } else {
                        DataConnection.this.log("DcDisconnectState stale EVENT_DEACTIVATE_DONE dp.tag=" + dp.mTag + " mTag=" + DataConnection.this.mTag);
                    }
                    return true;
                default:
                    DataConnection.this.log("DcDisconnectingState not handled msg.what=" + DataConnection.this.getWhatToString(msg.what));
                    return false;
            }
        }
    }

    private class DcDisconnectionErrorCreatingConnection extends State {
        private DcDisconnectionErrorCreatingConnection() {
        }

        public boolean processMessage(Message msg) {
            switch (msg.what) {
                case DataConnection.EVENT_DEACTIVATE_DONE /*262147*/:
                    ConnectionParams cp = msg.obj.userObj;
                    if (cp.mTag == DataConnection.this.mTag) {
                        DataConnection.this.log("DcDisconnectionErrorCreatingConnection msg.what=EVENT_DEACTIVATE_DONE");
                        DataConnection.this.mInactiveState.setEnterNotificationParams(cp, DcFailCause.UNACCEPTABLE_NETWORK_PARAMETER);
                        DataConnection.this.transitionTo(DataConnection.this.mInactiveState);
                    } else {
                        DataConnection.this.log("DcDisconnectionErrorCreatingConnection stale EVENT_DEACTIVATE_DONE dp.tag=" + cp.mTag + ", mTag=" + DataConnection.this.mTag);
                    }
                    return true;
                default:
                    DataConnection.this.log("DcDisconnectionErrorCreatingConnection not handled msg.what=" + DataConnection.this.getWhatToString(msg.what));
                    return false;
            }
        }
    }

    private class DcInactiveState extends State {
        private DcInactiveState() {
        }

        public void setEnterNotificationParams(ConnectionParams cp, DcFailCause cause) {
            DataConnection.this.log("DcInactiveState: setEnterNoticationParams cp,cause");
            DataConnection.this.mConnectionParams = cp;
            DataConnection.this.mDisconnectParams = null;
            DataConnection.this.mDcFailCause = cause;
        }

        public void setEnterNotificationParams(DisconnectParams dp) {
            DataConnection.this.log("DcInactiveState: setEnterNoticationParams dp");
            DataConnection.this.mConnectionParams = null;
            DataConnection.this.mDisconnectParams = dp;
            DataConnection.this.mDcFailCause = DcFailCause.NONE;
        }

        public void setEnterNotificationParams(DcFailCause cause) {
            DataConnection.this.mConnectionParams = null;
            DataConnection.this.mDisconnectParams = null;
            DataConnection.this.mDcFailCause = cause;
        }

        public void enter() {
            DataConnection dataConnection = DataConnection.this;
            dataConnection.mTag++;
            DataConnection.this.log("DcInactiveState: enter() mTag=" + DataConnection.this.mTag);
            if (DataConnection.this.mConnectionParams != null) {
                DataConnection.this.log("DcInactiveState: enter notifyConnectCompleted +ALL failCause=" + DataConnection.this.mDcFailCause);
                DataConnection.this.notifyConnectCompleted(DataConnection.this.mConnectionParams, DataConnection.this.mDcFailCause, true);
            }
            if (DataConnection.this.mDisconnectParams != null) {
                DataConnection.this.log("DcInactiveState: enter notifyDisconnectCompleted +ALL failCause=" + DataConnection.this.mDcFailCause);
                DataConnection.this.notifyDisconnectCompleted(DataConnection.this.mDisconnectParams, true);
            }
            if (DataConnection.this.mDisconnectParams == null && DataConnection.this.mConnectionParams == null && DataConnection.this.mDcFailCause != null) {
                DataConnection.this.log("DcInactiveState: enter notifyAllDisconnectCompleted failCause=" + DataConnection.this.mDcFailCause);
                DataConnection.this.notifyAllDisconnectCompleted(DataConnection.this.mDcFailCause);
            }
            DataConnection.this.mDcController.removeActiveDcByCid(DataConnection.this);
            DataConnection.this.clearSettings();
        }

        public void exit() {
        }

        public boolean processMessage(Message msg) {
            switch (msg.what) {
                case SmsEnvelope.TELESERVICE_MWI /*262144*/:
                    DataConnection.this.log("DcInactiveState: mag.what=EVENT_CONNECT");
                    ConnectionParams cp = msg.obj;
                    if (DataConnection.this.initConnection(cp)) {
                        DataConnection.this.onConnect(DataConnection.this.mConnectionParams);
                        DataConnection.this.transitionTo(DataConnection.this.mActivatingState);
                    } else {
                        DataConnection.this.log("DcInactiveState: msg.what=EVENT_CONNECT initConnection failed");
                        DataConnection.this.notifyConnectCompleted(cp, DcFailCause.UNACCEPTABLE_NETWORK_PARAMETER, false);
                    }
                    return true;
                case DataConnection.EVENT_DISCONNECT /*262148*/:
                    DataConnection.this.log("DcInactiveState: msg.what=EVENT_DISCONNECT");
                    DataConnection.this.notifyDisconnectCompleted((DisconnectParams) msg.obj, false);
                    return true;
                case DataConnection.EVENT_DISCONNECT_ALL /*262150*/:
                    DataConnection.this.log("DcInactiveState: msg.what=EVENT_DISCONNECT_ALL");
                    DataConnection.this.notifyDisconnectCompleted((DisconnectParams) msg.obj, false);
                    return true;
                case DcAsyncChannel.REQ_RESET /*266252*/:
                    DataConnection.this.log("DcInactiveState: msg.what=RSP_RESET, ignore we're already reset");
                    return true;
                default:
                    DataConnection.this.log("DcInactiveState nothandled msg.what=" + DataConnection.this.getWhatToString(msg.what));
                    return false;
            }
        }
    }

    private class DcNetworkAgent extends NetworkAgent {
        public DcNetworkAgent(Looper l, Context c, String TAG, NetworkInfo ni, NetworkCapabilities nc, LinkProperties lp, int score) {
            super(l, c, TAG, ni, nc, lp, score);
        }

        protected void unwanted() {
            if (DataConnection.this.mNetworkAgent != this) {
                log("unwanted found mNetworkAgent=" + DataConnection.this.mNetworkAgent + ", which isn't me.  Aborting unwanted");
            } else if (DataConnection.this.mApnContexts != null) {
                for (ApnContext apnContext : DataConnection.this.mApnContexts) {
                    DataConnection.this.sendMessage(DataConnection.this.obtainMessage(DataConnection.EVENT_DISCONNECT, new DisconnectParams(apnContext, apnContext.getReason(), DataConnection.this.mDct.obtainMessage(270351, apnContext))));
                }
            }
        }
    }

    private class DcRetryingState extends State {
        private DcRetryingState() {
        }

        public void enter() {
            if (DataConnection.this.mConnectionParams.mRilRat == DataConnection.this.mRilRat && DataConnection.this.mDataRegState == 0) {
                DataConnection.this.log("DcRetryingState: enter() mTag=" + DataConnection.this.mTag + ", call notifyAllOfDisconnectDcRetrying lostConnection");
                DataConnection.this.notifyAllOfDisconnectDcRetrying(Phone.REASON_LOST_DATA_CONNECTION);
                DataConnection.this.mDcController.removeActiveDcByCid(DataConnection.this);
                DataConnection.this.mCid = -1;
                return;
            }
            DataConnection.this.logAndAddLogRec("DcRetryingState: enter() not retrying rat changed, mConnectionParams.mRilRat=" + DataConnection.this.mConnectionParams.mRilRat + " != mRilRat:" + DataConnection.this.mRilRat + " transitionTo(mInactiveState)");
            DataConnection.this.mInactiveState.setEnterNotificationParams(DcFailCause.LOST_CONNECTION);
            DataConnection.this.transitionTo(DataConnection.this.mInactiveState);
        }

        public boolean processMessage(Message msg) {
            switch (msg.what) {
                case SmsEnvelope.TELESERVICE_MWI /*262144*/:
                    ConnectionParams cp = msg.obj;
                    DataConnection.this.log("DcRetryingState: msg.what=EVENT_CONNECT RefCount=" + DataConnection.this.mApnContexts.size() + " cp=" + cp + " mConnectionParams=" + DataConnection.this.mConnectionParams);
                    if (DataConnection.this.initConnection(cp)) {
                        DataConnection.this.onConnect(DataConnection.this.mConnectionParams);
                        DataConnection.this.transitionTo(DataConnection.this.mActivatingState);
                    } else {
                        DataConnection.this.log("DcRetryingState: msg.what=EVENT_CONNECT initConnection failed");
                        DataConnection.this.notifyConnectCompleted(cp, DcFailCause.UNACCEPTABLE_NETWORK_PARAMETER, false);
                    }
                    return true;
                case DataConnection.EVENT_DISCONNECT /*262148*/:
                    DisconnectParams dp = msg.obj;
                    if (DataConnection.this.mApnContexts.remove(dp.mApnContext) && DataConnection.this.mApnContexts.size() == 0) {
                        DataConnection.this.log("DcRetryingState msg.what=EVENT_DISCONNECT  RefCount=" + DataConnection.this.mApnContexts.size() + " dp=" + dp);
                        DataConnection.this.mInactiveState.setEnterNotificationParams(dp);
                        DataConnection.this.transitionTo(DataConnection.this.mInactiveState);
                    } else {
                        DataConnection.this.log("DcRetryingState: msg.what=EVENT_DISCONNECT");
                        DataConnection.this.notifyDisconnectCompleted(dp, false);
                    }
                    return true;
                case DataConnection.EVENT_DISCONNECT_ALL /*262150*/:
                    DataConnection.this.log("DcRetryingState msg.what=EVENT_DISCONNECT/DISCONNECT_ALL RefCount=" + DataConnection.this.mApnContexts.size());
                    DataConnection.this.mInactiveState.setEnterNotificationParams(DcFailCause.LOST_CONNECTION);
                    DataConnection.this.transitionTo(DataConnection.this.mInactiveState);
                    return true;
                case DataConnection.EVENT_RETRY_CONNECTION /*262154*/:
                    if ("CG".equals("DGG") || "GG".equals("DGG")) {
                        int slotId = SystemProperties.getInt("ril.datacross.slotid", -1);
                        int phoneId = SubscriptionManager.getDefaultDataPhoneId();
                        if (!(slotId == -1 || DataConnection.this.mPhone.getPhoneId() == slotId) || (slotId == -1 && phoneId != DataConnection.this.mPhone.getPhoneId())) {
                            DataConnection.this.log("DcRetryingState EVENT_RETRY_CONNECTION, But, not handled because mPhone.getPhoneId()=" + DataConnection.this.mPhone.getPhoneId() + ", datacross.slotid=" + slotId + ", getDefaultDataPhoneId()=" + phoneId);
                            DataConnection.this.mInactiveState.setEnterNotificationParams(DataConnection.this.mConnectionParams, DcFailCause.PREF_RADIO_TECH_CHANGED);
                            DataConnection.this.transitionTo(DataConnection.this.mInactiveState);
                            return true;
                        }
                    }
                    if (msg.arg1 == DataConnection.this.mTag) {
                        DataConnection.this.mRetryManager.increaseRetryCount();
                        DataConnection.this.log("DcRetryingState EVENT_RETRY_CONNECTION RetryCount=" + DataConnection.this.mRetryManager.getRetryCount() + " mConnectionParams=" + DataConnection.this.mConnectionParams);
                        DataConnection.this.onConnect(DataConnection.this.mConnectionParams);
                        DataConnection.this.transitionTo(DataConnection.this.mActivatingState);
                    } else {
                        DataConnection.this.log("DcRetryingState stale EVENT_RETRY_CONNECTION tag:" + msg.arg1 + " != mTag:" + DataConnection.this.mTag);
                    }
                    return true;
                case DataConnection.EVENT_DATA_CONNECTION_DRS_OR_RAT_CHANGED /*262155*/:
                    Pair<Integer, Integer> drsRatPair = msg.obj.result;
                    int drs = ((Integer) drsRatPair.first).intValue();
                    int rat = ((Integer) drsRatPair.second).intValue();
                    if (rat == DataConnection.this.mRilRat && drs == DataConnection.this.mDataRegState) {
                        DataConnection.this.log("DcRetryingState: EVENT_DATA_CONNECTION_DRS_OR_RAT_CHANGED strange no change in drs=" + drs + " rat=" + rat + " ignoring");
                    } else if (DataConnection.this.mConnectionParams.mRetryWhenSSChange) {
                        return false;
                    } else {
                        DataConnection.this.log("DcRetryingState: EVENT_DATA_CONNECTION_DRS_OR_RAT_CHANGED giving up changed from  " + DataConnection.this.mRilRat + " to rat =" + rat + " or drs changed from " + DataConnection.this.mDataRegState + " to drs=" + drs);
                        DataConnection.this.mConnectionParams.mRilRat = rat;
                        DataConnection.this.mRilRat = rat;
                        int networkType = DataConnection.this.mPhone.getServiceState().getDataNetworkType();
                        DataConnection.this.mNetworkInfo.setSubtype(networkType, TelephonyManager.getNetworkTypeName(networkType));
                    }
                    return true;
                case DcAsyncChannel.REQ_RESET /*266252*/:
                    DataConnection.this.log("DcRetryingState: msg.what=RSP_RESET, ignore we're already reset");
                    DataConnection.this.mInactiveState.setEnterNotificationParams(DataConnection.this.mConnectionParams, DcFailCause.RESET_BY_FRAMEWORK);
                    DataConnection.this.transitionTo(DataConnection.this.mInactiveState);
                    return true;
                default:
                    DataConnection.this.log("DcRetryingState nothandled msg.what=" + DataConnection.this.getWhatToString(msg.what));
                    return false;
            }
        }
    }

    static class DisconnectParams {
        ApnContext mApnContext;
        Message mOnCompletedMsg;
        String mReason;
        int mTag;

        DisconnectParams(ApnContext apnContext, String reason, Message onCompletedMsg) {
            this.mApnContext = apnContext;
            this.mReason = reason;
            this.mOnCompletedMsg = onCompletedMsg;
        }

        public String toString() {
            return "{mTag=" + this.mTag + " mApnContext=" + this.mApnContext + " mReason=" + this.mReason + " mOnCompletedMsg=" + DataConnection.msgToString(this.mOnCompletedMsg) + "}";
        }
    }

    static class UpdateLinkPropertyResult {
        public LinkProperties newLp;
        public LinkProperties oldLp;
        public SetupResult setupResult = SetupResult.SUCCESS;

        public UpdateLinkPropertyResult(LinkProperties curLp) {
            this.oldLp = curLp;
            this.newLp = curLp;
        }
    }

    static {
        sCmdToString[0] = "EVENT_CONNECT";
        sCmdToString[1] = "EVENT_SETUP_DATA_CONNECTION_DONE";
        sCmdToString[2] = "EVENT_GET_LAST_FAIL_DONE";
        sCmdToString[3] = "EVENT_DEACTIVATE_DONE";
        sCmdToString[4] = "EVENT_DISCONNECT";
        sCmdToString[5] = "EVENT_RIL_CONNECTED";
        sCmdToString[6] = "EVENT_DISCONNECT_ALL";
        sCmdToString[7] = "EVENT_DATA_STATE_CHANGED";
        sCmdToString[8] = "EVENT_TEAR_DOWN_NOW";
        sCmdToString[9] = "EVENT_LOST_CONNECTION";
        sCmdToString[10] = "EVENT_RETRY_CONNECTION";
        sCmdToString[11] = "EVENT_DATA_CONNECTION_DRS_OR_RAT_CHANGED";
        sCmdToString[12] = "EVENT_DATA_CONNECTION_ROAM_ON";
        sCmdToString[13] = "EVENT_DATA_CONNECTION_ROAM_OFF";
        sCmdToString[14] = "EVENT_DATA_CONNECTION_STATE_CHANGED";
    }

    static String cmdToString(int cmd) {
        String value;
        cmd -= SmsEnvelope.TELESERVICE_MWI;
        if (cmd < 0 || cmd >= sCmdToString.length) {
            value = DcAsyncChannel.cmdToString(cmd + SmsEnvelope.TELESERVICE_MWI);
        } else {
            value = sCmdToString[cmd];
        }
        if (value == null) {
            return "0x" + Integer.toHexString(cmd + SmsEnvelope.TELESERVICE_MWI);
        }
        return value;
    }

    boolean isDebugLevelNotLow() {
        if (SystemProperties.get(LOG_LEVEL_PROP, LOG_LEVEL_PROP_LOW).equalsIgnoreCase(LOG_LEVEL_PROP_LOW)) {
            return false;
        }
        return true;
    }

    static DataConnection makeDataConnection(PhoneBase phone, int id, DcTrackerBase dct, DcTesterFailBringUpAll failBringUpAll, DcController dcc) {
        DataConnection dc = new DataConnection(phone, "DC-" + mInstanceNumber.incrementAndGet(), id, dct, failBringUpAll, dcc);
        dc.start();
        dc.log("Made " + dc.getName());
        return dc;
    }

    void dispose() {
        log("dispose: call quiteNow()");
        quitNow();
    }

    NetworkCapabilities getCopyNetworkCapabilities() {
        if (this.mFixedApnType != null) {
            return makeNetworkCapabilities(this.mFixedApnType);
        }
        return makeNetworkCapabilities();
    }

    LinkProperties getCopyLinkProperties() {
        return new LinkProperties(this.mLinkProperties);
    }

    boolean getIsInactive() {
        return getCurrentState() == this.mInactiveState;
    }

    int getCid() {
        return this.mCid;
    }

    ApnSetting getApnSetting() {
        return this.mApnSetting;
    }

    void setLinkPropertiesHttpProxy(ProxyInfo proxy) {
        this.mLinkProperties.setHttpProxy(proxy);
    }

    public boolean isIpv4Connected() {
        for (InetAddress addr : this.mLinkProperties.getAddresses()) {
            if (addr instanceof Inet4Address) {
                Inet4Address i4addr = (Inet4Address) addr;
                if (!(i4addr.isAnyLocalAddress() || i4addr.isLinkLocalAddress() || i4addr.isLoopbackAddress() || i4addr.isMulticastAddress())) {
                    return true;
                }
            }
        }
        return false;
    }

    public boolean isIpv6Connected() {
        for (InetAddress addr : this.mLinkProperties.getAddresses()) {
            if (addr instanceof Inet6Address) {
                Inet6Address i6addr = (Inet6Address) addr;
                if (!(i6addr.isAnyLocalAddress() || i6addr.isLinkLocalAddress() || i6addr.isLoopbackAddress() || i6addr.isMulticastAddress())) {
                    return true;
                }
            }
        }
        return false;
    }

    UpdateLinkPropertyResult updateLinkProperty(DataCallResponse newState) {
        UpdateLinkPropertyResult result = new UpdateLinkPropertyResult(this.mLinkProperties);
        if (newState != null) {
            result.newLp = new LinkProperties();
            result.setupResult = setLinkProperties(newState, result.newLp);
            if (result.setupResult != SetupResult.SUCCESS) {
                log("updateLinkProperty failed : " + result.setupResult);
            } else {
                if (this.mApnSetting == null || this.mApnSetting.proxy == null || this.mApnSetting.proxy.length() == 0) {
                    result.newLp.setHttpProxy(this.mLinkProperties.getHttpProxy());
                } else {
                    try {
                        String port = this.mApnSetting.port;
                        if (TextUtils.isEmpty(port)) {
                            port = "8080";
                        }
                        result.newLp.setHttpProxy(new ProxyInfo(this.mApnSetting.proxy, Integer.parseInt(port), null));
                        log("set proxy from APN : " + this.mApnSetting.proxy);
                    } catch (NumberFormatException e) {
                        loge("updateLinkProperty: NumberFormatException making ProxyProperties (" + this.mApnSetting.port + "): " + e);
                    }
                }
                this.mLinkProperties = result.newLp;
                updateTcpBufferSizes(this.mRilRat);
                if (!result.oldLp.equals(result.newLp)) {
                    log("updateLinkProperty old LP=" + result.oldLp);
                    log("updateLinkProperty new LP=" + result.newLp);
                }
                if (!(result.newLp.equals(result.oldLp) || this.mNetworkAgent == null)) {
                    this.mNetworkAgent.sendLinkProperties(this.mLinkProperties);
                }
            }
        }
        return result;
    }

    private void checkSetMtu(ApnSetting apn, LinkProperties lp) {
        if (lp != null && apn != null && lp != null) {
            String salesCode = SystemProperties.get("ro.csc.sales_code");
            if (!"CHN".equals(salesCode) && !"CHM".equals(salesCode) && !"CHC".equals(salesCode) && !"CHU".equals(salesCode) && !"CTC".equals(salesCode)) {
                if (lp.getMtu() != 0) {
                    log("MTU set by call response to: " + lp.getMtu());
                } else if (apn == null || apn.mtu == 0) {
                    int mtu = this.mPhone.getContext().getResources().getInteger(17694833);
                    if (mtu != 0) {
                        lp.setMtu(mtu);
                        log("MTU set by config resource to: " + mtu);
                    }
                } else {
                    lp.setMtu(apn.mtu);
                    log("MTU set by APN to: " + apn.mtu);
                }
            }
        }
    }

    private DataConnection(PhoneBase phone, String name, int id, DcTrackerBase dct, DcTesterFailBringUpAll failBringUpAll, DcController dcc) {
        super(name, dcc.getHandler());
        setLogRecSize(300);
        setLogOnlyTransitions(true);
        log("DataConnection constructor E");
        this.mPhone = phone;
        this.mDct = dct;
        this.mDcTesterFailBringUpAll = failBringUpAll;
        this.mDcController = dcc;
        this.mId = id;
        this.mCid = -1;
        this.mDcRetryAlarmController = new DcRetryAlarmController(this.mPhone, this);
        ServiceState ss = this.mPhone.getServiceState();
        this.mRilRat = ss.getRilDataRadioTechnology();
        this.mDataRegState = this.mPhone.getServiceState().getDataRegState();
        int networkType = ss.getDataNetworkType();
        this.mNetworkInfo = new NetworkInfo(0, networkType, NETWORK_TYPE, TelephonyManager.getNetworkTypeName(networkType));
        this.mNetworkInfo.setRoaming(ss.getRoaming());
        this.mNetworkInfo.setIsAvailable(true);
        addState(this.mDefaultState);
        addState(this.mInactiveState, this.mDefaultState);
        addState(this.mActivatingState, this.mDefaultState);
        addState(this.mRetryingState, this.mDefaultState);
        addState(this.mActiveState, this.mDefaultState);
        addState(this.mDisconnectingState, this.mDefaultState);
        addState(this.mDisconnectingErrorCreatingConnection, this.mDefaultState);
        setInitialState(this.mInactiveState);
        this.mApnContexts = new ArrayList();
        log("DataConnection constructor X");
    }

    private String getRetryConfig(boolean forDefault) {
        int nt = this.mPhone.getServiceState().getNetworkType();
        if (Build.IS_DEBUGGABLE) {
            String config = SystemProperties.get("test.data_retry_config");
            if (!TextUtils.isEmpty(config)) {
                return config;
            }
        }
        if (nt == 4 || nt == 7 || nt == 5 || nt == 6 || nt == 12 || nt == 14) {
            return SystemProperties.get("ro.cdma.data_retry_config");
        }
        if (forDefault) {
            return SystemProperties.get("ro.gsm.data_retry_config");
        }
        return SystemProperties.get("ro.gsm.2nd_data_retry_config");
    }

    private void configureRetry(boolean forDefault) {
        if (!this.mRetryManager.configure(getRetryConfig(forDefault))) {
            if (forDefault) {
                if (!this.mRetryManager.configure(DEFAULT_DATA_RETRY_CONFIG)) {
                    loge("configureRetry: Could not configure using DEFAULT_DATA_RETRY_CONFIG=max_retries=infinite,default_randomization=2000,5000,10000,20000,40000,80000:5000,160000:5000,320000:5000,640000:5000,1280000:5000,1800000:5000");
                    this.mRetryManager.configure(5, 2000, 1000);
                }
            } else if (!this.mRetryManager.configure(SECONDARY_DATA_RETRY_CONFIG)) {
                loge("configureRetry: Could note configure using SECONDARY_DATA_RETRY_CONFIG=max_retries=3, 5000, 5000, 5000");
                this.mRetryManager.configure(5, 2000, 1000);
            }
        }
        log("configureRetry: forDefault=" + forDefault + " mRetryManager=" + this.mRetryManager);
    }

    private void onConnect(ConnectionParams cp) {
        log("onConnect: carrier='" + this.mApnSetting.carrier + "' APN='" + this.mApnSetting.apn + "' proxy='" + this.mApnSetting.proxy + "' port='" + this.mApnSetting.port + "'");
        if (this.mDcTesterFailBringUpAll.getDcFailBringUp().mCounter > 0) {
            DataCallResponse response = new DataCallResponse();
            response.version = this.mPhone.mCi.getRilVersion();
            response.status = this.mDcTesterFailBringUpAll.getDcFailBringUp().mFailCause.getErrorCode();
            response.cid = 0;
            response.active = 0;
            response.type = "";
            response.ifname = "";
            response.addresses = new String[0];
            response.dnses = new String[0];
            response.gateways = new String[0];
            response.suggestedRetryTime = this.mDcTesterFailBringUpAll.getDcFailBringUp().mSuggestedRetryTime;
            response.pcscf = new String[0];
            response.mtu = 0;
            Message msg = obtainMessage(EVENT_SETUP_DATA_CONNECTION_DONE, cp);
            AsyncResult.forMessage(msg, response, null);
            sendMessage(msg);
            log("onConnect: FailBringUpAll=" + this.mDcTesterFailBringUpAll.getDcFailBringUp() + " send error response=" + response);
            DcFailBringUp dcFailBringUp = this.mDcTesterFailBringUpAll.getDcFailBringUp();
            dcFailBringUp.mCounter--;
            return;
        }
        String protocol;
        String fetchedApn;
        this.mCreateTime = -1;
        this.mLastFailTime = -1;
        this.mLastFailCause = DcFailCause.NONE;
        msg = obtainMessage(EVENT_SETUP_DATA_CONNECTION_DONE, cp);
        msg.obj = cp;
        int authType = this.mApnSetting.authType;
        if (authType == -1) {
            authType = TextUtils.isEmpty(this.mApnSetting.user) ? 0 : 3;
        }
        if (this.mPhone.getServiceState().getRoaming()) {
            protocol = this.mApnSetting.roamingProtocol;
        } else {
            protocol = this.mApnSetting.protocol;
        }
        IccRecords r = (IccRecords) this.mPhone.mIccRecords.get();
        if ("45005".equals(r != null ? r.getOperatorNumeric() : "") && "SKT".equals("")) {
            fetchedApn = fetchSktApn(this.mApnSetting.apn);
        } else {
            fetchedApn = this.mApnSetting.apn;
        }
        if ("VZW-CDMA".equals("") && isVzwSim(this.mApnSetting.numeric)) {
            if (this.mDct.isDataInLegacy3gpp(this.mPhone.getServiceState().getRilDataRadioTechnology())) {
                log("Support IPv4 only in legacy 3GPP mode");
                protocol = "IP";
            }
        }
        this.mPhone.mCi.setupDataCall(Integer.toString(cp.mRilRat + 2), Integer.toString(cp.mProfileId), fetchedApn, this.mApnSetting.user, this.mApnSetting.password, Integer.toString(authType), protocol, msg);
    }

    private void tearDownData(Object o) {
        int discReason = 0;
        if (o != null && (o instanceof DisconnectParams)) {
            DisconnectParams dp = (DisconnectParams) o;
            if (TextUtils.equals(dp.mReason, Phone.REASON_RADIO_TURNED_OFF)) {
                discReason = 1;
            } else if (TextUtils.equals(dp.mReason, Phone.REASON_PDP_RESET)) {
                discReason = 2;
            }
        }
        if (this.mPhone.mCi.getRadioState().isOn()) {
            log("tearDownData radio is on, call deactivateDataCall");
            this.mPhone.mCi.deactivateDataCall(this.mCid, discReason, obtainMessage(EVENT_DEACTIVATE_DONE, this.mTag, 0, o));
            return;
        }
        log("tearDownData radio is off sendMessage EVENT_DEACTIVATE_DONE immediately");
        sendMessage(obtainMessage(EVENT_DEACTIVATE_DONE, this.mTag, 0, new AsyncResult(o, null, null)));
    }

    private void notifyAllWithEvent(ApnContext alreadySent, int event, String reason) {
        this.mNetworkInfo.setDetailedState(this.mNetworkInfo.getDetailedState(), reason, this.mNetworkInfo.getExtraInfo());
        for (ApnContext apnContext : this.mApnContexts) {
            if (apnContext != alreadySent) {
                if (reason != null) {
                    apnContext.setReason(reason);
                }
                Message msg = this.mDct.obtainMessage(event, apnContext);
                AsyncResult.forMessage(msg);
                msg.sendToTarget();
            }
        }
    }

    private void notifyAllOfConnected(String reason) {
        notifyAllWithEvent(null, 270336, reason);
    }

    private void notifyAllOfDisconnectDcRetrying(String reason) {
        notifyAllWithEvent(null, 270370, reason);
    }

    private void notifyAllDisconnectCompleted(DcFailCause cause) {
        notifyAllWithEvent(null, 270351, cause.toString());
    }

    private void notifyConnectCompleted(ConnectionParams cp, DcFailCause cause, boolean sendAll) {
        ApnContext alreadySent = null;
        if (!(cp == null || cp.mOnCompletedMsg == null)) {
            Message connectionCompletedMsg = cp.mOnCompletedMsg;
            cp.mOnCompletedMsg = null;
            if (connectionCompletedMsg.obj instanceof ApnContext) {
                alreadySent = connectionCompletedMsg.obj;
            }
            long timeStamp = System.currentTimeMillis();
            connectionCompletedMsg.arg1 = this.mCid;
            if (cause == DcFailCause.NONE) {
                this.mCreateTime = timeStamp;
                AsyncResult.forMessage(connectionCompletedMsg);
            } else {
                this.mLastFailCause = cause;
                this.mLastFailTime = timeStamp;
                if (cause == null) {
                    cause = DcFailCause.UNKNOWN;
                }
                AsyncResult.forMessage(connectionCompletedMsg, cause, new Throwable(cause.toString()));
            }
            log("notifyConnectCompleted at " + timeStamp + " cause=" + cause + " connectionCompletedMsg=" + msgToString(connectionCompletedMsg));
            connectionCompletedMsg.sendToTarget();
        }
        if (sendAll) {
            notifyAllWithEvent(alreadySent, 270371, cause.toString());
        }
    }

    private void notifyDisconnectCompleted(DisconnectParams dp, boolean sendAll) {
        log("NotifyDisconnectCompleted");
        ApnContext alreadySent = null;
        String reason = null;
        if (!(dp == null || dp.mOnCompletedMsg == null)) {
            Message msg = dp.mOnCompletedMsg;
            dp.mOnCompletedMsg = null;
            if (msg.obj instanceof ApnContext) {
                alreadySent = msg.obj;
            }
            reason = dp.mReason;
            String str = "msg=%s msg.obj=%s";
            Object[] objArr = new Object[2];
            objArr[0] = msg.toString();
            objArr[1] = msg.obj instanceof String ? (String) msg.obj : "<no-reason>";
            log(String.format(str, objArr));
            AsyncResult.forMessage(msg);
            msg.sendToTarget();
        }
        if (sendAll) {
            if (reason == null) {
                reason = DcFailCause.UNKNOWN.toString();
            }
            notifyAllWithEvent(alreadySent, 270351, reason);
        }
        log("NotifyDisconnectCompleted DisconnectParams=" + dp);
    }

    public int getDataConnectionId() {
        return this.mId;
    }

    private void clearSettings() {
        log("clearSettings");
        this.mCreateTime = -1;
        this.mLastFailTime = -1;
        this.mLastFailCause = DcFailCause.NONE;
        this.mCid = -1;
        this.mPcscfAddr = new String[5];
        this.mLinkProperties = new LinkProperties();
        this.mApnContexts.clear();
        this.mApnSetting = null;
        this.mDcFailCause = null;
    }

    private SetupResult onSetupConnectionCompleted(AsyncResult ar) {
        DataCallResponse response = ar.result;
        ConnectionParams cp = ar.userObj;
        if (cp.mTag != this.mTag) {
            log("onSetupConnectionCompleted stale cp.tag=" + cp.mTag + ", mtag=" + this.mTag);
            return SetupResult.ERR_Stale;
        } else if (ar.exception != null) {
            log("onSetupConnectionCompleted failed, ar.exception=" + ar.exception + " response=" + response);
            if ((ar.exception instanceof CommandException) && ((CommandException) ar.exception).getCommandError() == Error.RADIO_NOT_AVAILABLE) {
                result = SetupResult.ERR_BadCommand;
                result.mFailCause = DcFailCause.RADIO_NOT_AVAILABLE;
            } else if (response == null || response.version < 4) {
                result = SetupResult.ERR_GetLastErrorFromRil;
            } else {
                result = SetupResult.ERR_RilError;
                result.mFailCause = DcFailCause.fromInt(response.status);
            }
            if (!"DCGS".equals("DGG") || this.mPhone.getPhoneId() != 0 || this.mPhone.getPhoneType() != 2) {
                return result;
            }
            forceDataDeactiveTracker();
            return result;
        } else if (response.status != 0) {
            result = SetupResult.ERR_RilError;
            result.mFailCause = DcFailCause.fromInt(response.status);
            return result;
        } else {
            log("onSetupConnectionCompleted received DataCallResponse: " + response);
            this.mCid = response.cid;
            this.mPcscfAddr = response.pcscf;
            return updateLinkProperty(response).setupResult;
        }
    }

    private boolean isDnsOk(String[] domainNameServers) {
        if (!NULL_IP.equals(domainNameServers[0]) || !NULL_IP.equals(domainNameServers[1]) || this.mPhone.isDnsCheckDisabled() || (this.mApnSetting.types[0].equals("mms") && isIpAddress(this.mApnSetting.mmsProxy))) {
            return true;
        }
        log(String.format("isDnsOk: return false apn.types[0]=%s APN_TYPE_MMS=%s isIpAddress(%s)=%s", new Object[]{this.mApnSetting.types[0], "mms", this.mApnSetting.mmsProxy, Boolean.valueOf(isIpAddress(this.mApnSetting.mmsProxy))}));
        return false;
    }

    private void updateTcpBufferSizes(int rilRat) {
        String sizes = null;
        String ratName = ServiceState.rilRadioTechnologyToString(rilRat).toLowerCase(Locale.ROOT);
        if (rilRat == 7 || rilRat == 8 || rilRat == 12) {
            ratName = "evdo";
        }
        String[] configOverride = this.mPhone.getContext().getResources().getStringArray(17236012);
        for (String split : configOverride) {
            String[] split2 = split.split(":");
            if (ratName.equals(split2[0]) && split2.length == 2) {
                sizes = split2[1];
                break;
            }
        }
        if (sizes == null) {
            switch (rilRat) {
                case 1:
                    sizes = TCP_BUFFER_SIZES_GPRS;
                    break;
                case 2:
                    sizes = TCP_BUFFER_SIZES_EDGE;
                    break;
                case 3:
                    sizes = TCP_BUFFER_SIZES_UMTS;
                    break;
                case 6:
                    sizes = TCP_BUFFER_SIZES_1XRTT;
                    break;
                case 7:
                case 8:
                case 12:
                    sizes = TCP_BUFFER_SIZES_EVDO;
                    break;
                case 9:
                    sizes = TCP_BUFFER_SIZES_HSDPA;
                    break;
                case 10:
                case 11:
                    sizes = TCP_BUFFER_SIZES_HSPA;
                    break;
                case 13:
                    sizes = TCP_BUFFER_SIZES_EHRPD;
                    break;
                case 14:
                    sizes = TCP_BUFFER_SIZES_LTE;
                    break;
                case 15:
                    sizes = TCP_BUFFER_SIZES_HSPAP;
                    break;
            }
        }
        this.mLinkProperties.setTcpBufferSizes(sizes);
    }

    private NetworkCapabilities makeNetworkCapabilities() {
        NetworkCapabilities result = new NetworkCapabilities();
        result.addTransportType(0);
        if (this.mApnSetting != null) {
            for (String type : this.mApnSetting.types) {
                Object obj = -1;
                switch (type.hashCode()) {
                    case 42:
                        if (type.equals(CharacterSets.MIMENAME_ANY_CHARSET)) {
                            obj = null;
                            break;
                        }
                        break;
                    case 3352:
                        if (type.equals("ia")) {
                            obj = 10;
                            break;
                        }
                        break;
                    case 97545:
                        if (type.equals("bip")) {
                            obj = 12;
                            break;
                        }
                        break;
                    case 98292:
                        if (type.equals("cbs")) {
                            obj = 9;
                            break;
                        }
                        break;
                    case 99837:
                        if (type.equals("dun")) {
                            obj = 6;
                            break;
                        }
                        break;
                    case 104399:
                        if (type.equals("ims")) {
                            obj = 8;
                            break;
                        }
                        break;
                    case 108243:
                        if (type.equals("mms")) {
                            obj = 2;
                            break;
                        }
                        break;
                    case 3118246:
                        if (type.equals("ent1")) {
                            obj = 11;
                            break;
                        }
                        break;
                    case 3149046:
                        if (type.equals("fota")) {
                            obj = 7;
                            break;
                        }
                        break;
                    case 3355583:
                        if (type.equals("mms2")) {
                            obj = 3;
                            break;
                        }
                        break;
                    case 3541982:
                        if (type.equals("supl")) {
                            obj = 5;
                            break;
                        }
                        break;
                    case 3673178:
                        if (type.equals("xcap")) {
                            obj = 4;
                            break;
                        }
                        break;
                    case 1544803905:
                        if (type.equals("default")) {
                            obj = 1;
                            break;
                        }
                        break;
                }
                ApnSetting fixedApn;
                switch (obj) {
                    case null:
                        result.addCapability(12);
                        result.addCapability(0);
                        result.addCapability(1);
                        result.addCapability(3);
                        result.addCapability(7);
                        result.addCapability(19);
                        if (!this.mDct.IncludeFixedApn("cbs")) {
                            result.addCapability(5);
                        }
                        if (!this.mDct.IncludeFixedApn("ims")) {
                            result.addCapability(4);
                        }
                        if (!this.mDct.IncludeFixedApn("xcap")) {
                            result.addCapability(9);
                            break;
                        }
                        break;
                    case 1:
                        result.addCapability(12);
                        break;
                    case 2:
                        if ((!"DCGG".equals("DGG") && !"DCGS".equals("DGG") && !"DCGGS".equals("DGG") && !"DGG".equals("DGG")) || this.mDct.mPhone.getPhoneId() == 0) {
                            result.addCapability(0);
                            break;
                        }
                        log("NET_CAPABILITY_MMS is only added for slot 1");
                        break;
                    case 3:
                        if ((!"DCGG".equals("DGG") && !"DCGS".equals("DGG") && !"DCGGS".equals("DGG") && !"DGG".equals("DGG")) || this.mDct.mPhone.getPhoneId() == 1) {
                            result.addCapability(18);
                            break;
                        }
                        log("NET_CAPABILITY_MMS2 is only added for slot 2");
                        break;
                    case 4:
                        if (!this.mDct.IncludeFixedApn("xcap")) {
                            result.addCapability(9);
                            break;
                        }
                        fixedApn = this.mDct.fetchApn("xcap");
                        if (fixedApn != null && !fixedApn.equals(this.mApnSetting)) {
                            break;
                        }
                        result.addCapability(9);
                        break;
                    case 5:
                        result.addCapability(1);
                        break;
                    case 6:
                        ApnSetting securedDunApn = this.mDct.fetchDunApn();
                        if (securedDunApn != null && !securedDunApn.equals(this.mApnSetting)) {
                            break;
                        }
                        result.addCapability(2);
                        break;
                        break;
                    case 7:
                        result.addCapability(3);
                        break;
                    case 8:
                        if (!this.mDct.IncludeFixedApn("ims")) {
                            result.addCapability(4);
                            break;
                        }
                        fixedApn = this.mDct.fetchApn("ims");
                        if (fixedApn != null && !fixedApn.equals(this.mApnSetting)) {
                            break;
                        }
                        result.addCapability(4);
                        break;
                        break;
                    case 9:
                        if (!this.mDct.IncludeFixedApn("cbs")) {
                            result.addCapability(5);
                            break;
                        }
                        fixedApn = this.mDct.fetchApn("cbs");
                        if (fixedApn != null && !fixedApn.equals(this.mApnSetting)) {
                            break;
                        }
                        result.addCapability(5);
                        break;
                    case 10:
                        result.addCapability(7);
                        break;
                    case 11:
                        result.addCapability(17);
                        break;
                    case 12:
                        result.addCapability(19);
                        break;
                    default:
                        break;
                }
            }
            result.maybeMarkCapabilitiesRestricted();
        }
        int up = 14;
        int down = 14;
        switch (this.mRilRat) {
            case 1:
                up = 80;
                down = 80;
                break;
            case 2:
                up = 59;
                down = 236;
                break;
            case 3:
                up = 384;
                down = 384;
                break;
            case 4:
            case 5:
                up = 14;
                down = 14;
                break;
            case 6:
                up = 100;
                down = 100;
                break;
            case 7:
                up = 153;
                down = 2457;
                break;
            case 8:
                up = 1843;
                down = 3174;
                break;
            case 9:
                up = 2048;
                down = 14336;
                break;
            case 10:
                up = 5898;
                down = 14336;
                break;
            case 11:
                up = 5898;
                down = 14336;
                break;
            case 12:
                up = 1843;
                down = 5017;
                break;
            case 13:
                up = 153;
                down = 2516;
                break;
            case 14:
                up = 51200;
                down = 102400;
                break;
            case 15:
                up = 11264;
                down = 43008;
                break;
        }
        result.setLinkUpstreamBandwidthKbps(up);
        result.setLinkDownstreamBandwidthKbps(down);
        return result;
    }

    private NetworkCapabilities makeNetworkCapabilities(String ApnType) {
        NetworkCapabilities result = makeNetworkCapabilities();
        int i = -1;
        switch (ApnType.hashCode()) {
            case 98292:
                if (ApnType.equals("cbs")) {
                    i = 2;
                    break;
                }
                break;
            case 99837:
                if (ApnType.equals("dun")) {
                    i = 0;
                    break;
                }
                break;
            case 104399:
                if (ApnType.equals("ims")) {
                    i = 1;
                    break;
                }
                break;
            case 3673178:
                if (ApnType.equals("xcap")) {
                    i = 3;
                    break;
                }
                break;
        }
        switch (i) {
            case 0:
                result.addCapability(2);
                break;
            case 1:
                result.addCapability(4);
                break;
            case 2:
                result.addCapability(5);
                break;
            case 3:
                result.addCapability(9);
                break;
        }
        return result;
    }

    private boolean isIpAddress(String address) {
        if (address == null) {
            return false;
        }
        return Patterns.IP_ADDRESS.matcher(address).matches();
    }

    private SetupResult setLinkProperties(DataCallResponse response, LinkProperties lp) {
        String propertyPrefix = "net." + response.ifname + ".";
        return response.setLinkProperties(lp, isDnsOk(new String[]{SystemProperties.get(propertyPrefix + "dns1"), SystemProperties.get(propertyPrefix + "dns2")}));
    }

    private boolean initConnection(ConnectionParams cp) {
        ApnContext apnContext = cp.mApnContext;
        if (this.mApnSetting == null) {
            this.mApnSetting = apnContext.getApnSetting();
        } else if (!this.mApnSetting.canHandleType(apnContext.getApnType())) {
            log("initConnection: incompatible apnSetting in ConnectionParams cp=" + cp + " dc=" + this);
            return false;
        }
        this.mTag++;
        this.mConnectionParams = cp;
        this.mConnectionParams.mTag = this.mTag;
        if (!this.mApnContexts.contains(apnContext)) {
            this.mApnContexts.add(apnContext);
        }
        configureRetry(this.mApnSetting.canHandleType("default"));
        this.mRetryManager.setRetryCount(0);
        if (apnContext.getWaitingApns().size() > 1) {
            this.mRetryManager.setCurMaxRetryCount(this.mConnectionParams.mInitialMaxRetry);
            this.mRetryManager.setRetryForever(false);
        }
        log("initConnection:  RefCount=" + this.mApnContexts.size() + " mApnList=" + this.mApnContexts + " mConnectionParams=" + this.mConnectionParams);
        return true;
    }

    void tearDownNow() {
        log("tearDownNow()");
        sendMessage(obtainMessage(EVENT_TEAR_DOWN_NOW));
    }

    private void forceDataDeactiveTracker() {
        log("DataConnection : forceDataDeactiveTracker()");
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(bos);
        try {
            dos.writeByte(13);
            dos.writeByte(14);
            dos.writeShort(7);
            dos.writeByte(3);
            dos.writeByte(2);
            dos.writeByte(0);
            if (dos != null) {
                try {
                    dos.close();
                } catch (Exception e) {
                }
            }
            this.mPhone.mCi.invokeOemRilRequestRaw(bos.toByteArray(), null);
        } catch (IOException e2) {
            if (dos != null) {
                try {
                    dos.close();
                } catch (Exception e3) {
                }
            }
        } catch (Throwable th) {
            if (dos != null) {
                try {
                    dos.close();
                } catch (Exception e4) {
                }
            }
        }
    }

    protected String getWhatToString(int what) {
        return cmdToString(what);
    }

    private static String msgToString(Message msg) {
        if (msg == null) {
            return "null";
        }
        StringBuilder b = new StringBuilder();
        b.append("{what=");
        b.append(cmdToString(msg.what));
        b.append(" when=");
        TimeUtils.formatDuration(msg.getWhen() - SystemClock.uptimeMillis(), b);
        if (msg.arg1 != 0) {
            b.append(" arg1=");
            b.append(msg.arg1);
        }
        if (msg.arg2 != 0) {
            b.append(" arg2=");
            b.append(msg.arg2);
        }
        if (msg.obj != null) {
            b.append(" obj=");
            b.append(msg.obj);
        }
        b.append(" target=");
        b.append(msg.getTarget());
        b.append(" replyTo=");
        b.append(msg.replyTo);
        b.append("}");
        return b.toString();
    }

    static void slog(String s) {
        Rlog.d("DC", s);
    }

    protected void log(String s) {
        Rlog.d(getName(), s);
    }

    protected void logd(String s) {
        Rlog.d(getName(), s);
    }

    protected void logv(String s) {
        Rlog.v(getName(), s);
    }

    protected void logi(String s) {
        Rlog.i(getName(), s);
    }

    protected void logw(String s) {
        Rlog.w(getName(), s);
    }

    protected void loge(String s) {
        Rlog.e(getName(), s);
    }

    protected void loge(String s, Throwable e) {
        Rlog.e(getName(), s, e);
    }

    public String toStringSimple() {
        return getName() + ": State=" + getCurrentState().getName() + " mApnSetting=" + this.mApnSetting + " RefCount=" + this.mApnContexts.size() + " mCid=" + this.mCid + " mCreateTime=" + this.mCreateTime + " mLastastFailTime=" + this.mLastFailTime + " mLastFailCause=" + this.mLastFailCause + " mTag=" + this.mTag + " mRetryManager=" + this.mRetryManager + " mLinkProperties=" + this.mLinkProperties + " linkCapabilities=" + (this.mFixedApnType != null ? makeNetworkCapabilities(this.mFixedApnType) : makeNetworkCapabilities());
    }

    public String toString() {
        return "{" + toStringSimple() + " mApnContexts=" + this.mApnContexts + "}";
    }

    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        pw.print("DataConnection ");
        super.dump(fd, pw, args);
        pw.println(" mApnContexts.size=" + this.mApnContexts.size());
        pw.println(" mApnContexts=" + this.mApnContexts);
        pw.flush();
        pw.println(" mDataConnectionTracker=" + this.mDct);
        pw.println(" mApnSetting=" + this.mApnSetting);
        pw.println(" mTag=" + this.mTag);
        pw.println(" mCid=" + this.mCid);
        pw.println(" mRetryManager=" + this.mRetryManager);
        pw.println(" mConnectionParams=" + this.mConnectionParams);
        pw.println(" mDisconnectParams=" + this.mDisconnectParams);
        pw.println(" mDcFailCause=" + this.mDcFailCause);
        pw.flush();
        pw.println(" mPhone=" + this.mPhone);
        pw.flush();
        pw.println(" mLinkProperties=" + this.mLinkProperties);
        pw.flush();
        pw.println(" mDataRegState=" + this.mDataRegState);
        pw.println(" mRilRat=" + this.mRilRat);
        pw.println(" mNetworkCapabilities=" + (this.mFixedApnType != null ? makeNetworkCapabilities(this.mFixedApnType) : makeNetworkCapabilities()));
        pw.println(" mCreateTime=" + TimeUtils.logTimeOfDay(this.mCreateTime));
        pw.println(" mLastFailTime=" + TimeUtils.logTimeOfDay(this.mLastFailTime));
        pw.println(" mLastFailCause=" + this.mLastFailCause);
        pw.flush();
        pw.println(" mUserData=" + this.mUserData);
        pw.println(" mInstanceNumber=" + mInstanceNumber);
        pw.println(" mAc=" + this.mAc);
        pw.println(" mDcRetryAlarmController=" + this.mDcRetryAlarmController);
        pw.flush();
    }

    private boolean isVzwSim(String operator) {
        if ("311480".equals(operator) || "20404".equals(operator)) {
            return true;
        }
        return false;
    }

    private String fetchSktApn(String apn) {
        boolean DataNetworkEnable = true;
        String apnName = apn;
        if (Global.getInt(this.mPhone.getContext().getContentResolver(), "mobile_data", 1) != 1) {
            DataNetworkEnable = false;
        }
        if (!"web.sktelecom.com".equals(apn)) {
            return apnName;
        }
        if (!this.mPhone.getServiceState().getRoaming() && !DataNetworkEnable) {
            apnName = "mmsonly.sktelecom.com";
        } else if (!this.mPhone.getServiceState().getRoaming()) {
            return apnName;
        } else {
            apnName = "roaming.sktelecom.com";
        }
        log("fetchSktApn :" + apnName);
        return apnName;
    }

    private DetailedState getNiDetailedState(int state) {
        switch (state) {
            case 0:
                return DetailedState.DISCONNECTED;
            case 1:
                return DetailedState.CONNECTING;
            case 2:
                return DetailedState.CONNECTED;
            case 3:
                return DetailedState.SUSPENDED;
            default:
                return DetailedState.IDLE;
        }
    }
}
