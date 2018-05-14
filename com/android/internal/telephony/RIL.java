package com.android.internal.telephony;

import android.content.Context;
import android.hardware.display.DisplayManager;
import android.hardware.display.DisplayManager.DisplayListener;
import android.net.ConnectivityManager;
import android.net.LocalSocket;
import android.net.LocalSocketAddress;
import android.net.LocalSocketAddress.Namespace;
import android.os.AsyncResult;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.Parcel;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.os.SystemProperties;
import android.preference.PreferenceManager;
import android.provider.Settings.Global;
import android.provider.Settings.System;
import android.provider.Telephony.Threads;
import android.telephony.CellInfo;
import android.telephony.NeighboringCellInfo;
import android.telephony.PhoneNumberUtils;
import android.telephony.Rlog;
import android.telephony.SignalStrength;
import android.telephony.SmsMessage;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.telephony.gsm.CbConfig;
import android.text.TextUtils;
import android.util.Base64;
import android.util.SparseArray;
import android.view.Display;
import com.android.internal.telephony.CommandsInterface.RadioState;
import com.android.internal.telephony.cdma.CdmaCallWaitingNotification;
import com.android.internal.telephony.cdma.CdmaInformationRecords;
import com.android.internal.telephony.cdma.CdmaInformationRecords.CdmaDisplayInfoRec;
import com.android.internal.telephony.cdma.CdmaInformationRecords.CdmaLineControlInfoRec;
import com.android.internal.telephony.cdma.CdmaInformationRecords.CdmaNumberInfoRec;
import com.android.internal.telephony.cdma.CdmaInformationRecords.CdmaRedirectingNumberInfoRec;
import com.android.internal.telephony.cdma.CdmaInformationRecords.CdmaSignalInfoRec;
import com.android.internal.telephony.cdma.CdmaInformationRecords.CdmaT53AudioControlInfoRec;
import com.android.internal.telephony.cdma.CdmaInformationRecords.CdmaT53ClirInfoRec;
import com.android.internal.telephony.cdma.CdmaSmsBroadcastConfigInfo;
import com.android.internal.telephony.dataconnection.DataCallResponse;
import com.android.internal.telephony.dataconnection.DataProfile;
import com.android.internal.telephony.dataconnection.DcFailCause;
import com.android.internal.telephony.gsm.CallFailCause;
import com.android.internal.telephony.gsm.SmsBroadcastConfigInfo;
import com.android.internal.telephony.gsm.SsData;
import com.android.internal.telephony.gsm.SuppServiceNotification;
import com.android.internal.telephony.uicc.AdnRecord;
import com.android.internal.telephony.uicc.IccCardApplicationStatus;
import com.android.internal.telephony.uicc.IccCardStatus;
import com.android.internal.telephony.uicc.IccIoResult;
import com.android.internal.telephony.uicc.IccRefreshResponse;
import com.android.internal.telephony.uicc.IccUtils;
import com.android.internal.telephony.uicc.SimLockInfoResult;
import com.android.internal.telephony.uicc.SimPBEntryResult;
import com.google.android.mms.pdu.CharacterSets;
import com.sec.android.app.CscFeature;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.FileDescriptor;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

public class RIL extends BaseCommands implements CommandsInterface {
    private static final int CDMA_BROADCAST_SMS_NO_OF_SERVICE_CATEGORIES = 31;
    private static final int CDMA_BSI_NO_OF_INTS_STRUCT = 3;
    public static final boolean CELL_BROADCAST_ENABLE = true;
    private static final int DEFAULT_WAKE_LOCK_TIMEOUT = 60000;
    static final int EVENT_SEND = 1;
    static final int EVENT_WAKE_LOCK_TIMEOUT = 2;
    static final String LOG_LEVEL_PROP = "ro.debug_level";
    static final String LOG_LEVEL_PROP_HIGH = "0x4948";
    static final String LOG_LEVEL_PROP_LOW = "0x4f4c";
    static final String LOG_LEVEL_PROP_MID = "0x494d";
    static final int NETTEXT_GSM_SMS_CBMI_LIST_SIZE_MAX = 100;
    static final int RESPONSE_SOLICITED = 0;
    static final int RESPONSE_UNSOLICITED = 1;
    static final boolean RILJ_LOGD = true;
    static final boolean RILJ_LOGV = false;
    static final String RILJ_LOG_TAG = "RILJ";
    static final int RIL_MAX_COMMAND_BYTES = 8192;
    static final boolean SHIP_BUILD = "true".equals(SystemProperties.get("ro.product_ship", "false"));
    static final String[] SOCKET_NAME_RIL = new String[]{"rild", "rild2", "rild3"};
    static final int SOCKET_OPEN_RETRY_MILLIS = 4000;
    static final int USSD_DCS_KS5601 = 148;
    private int initPhoneType;
    Display mDefaultDisplay;
    int mDefaultDisplayState;
    private final DisplayListener mDisplayListener;
    private Integer mInstanceId;
    Object mLastNITZTimeInfo;
    RILReceiver mReceiver;
    Thread mReceiverThread;
    SparseArray<RILRequest> mRequestList;
    RILSender mSender;
    HandlerThread mSenderThread;
    LocalSocket mSocket;
    AtomicBoolean mTestingEmergencyCall;
    WakeLock mWakeLock;
    int mWakeLockCount;
    final int mWakeLockTimeout;

    class C00241 implements DisplayListener {
        C00241() {
        }

        public void onDisplayAdded(int displayId) {
        }

        public void onDisplayRemoved(int displayId) {
        }

        public void onDisplayChanged(int displayId) {
            if (displayId == 0) {
                RIL.this.updateScreenState();
            }
        }
    }

    class RILReceiver implements Runnable {
        byte[] buffer = new byte[8192];

        RILReceiver() {
        }

        public void run() {
            Throwable tr;
            int retryCount = 0;
            String rilSocket = "rild";
            while (true) {
                LocalSocket s = null;
                try {
                    if (RIL.this.mInstanceId == null || RIL.this.mInstanceId.intValue() == 0) {
                        rilSocket = RIL.SOCKET_NAME_RIL[0];
                    } else {
                        rilSocket = RIL.SOCKET_NAME_RIL[RIL.this.mInstanceId.intValue()];
                    }
                    try {
                        LocalSocket s2 = new LocalSocket();
                        try {
                            s2.connect(new LocalSocketAddress(rilSocket, Namespace.RESERVED));
                            retryCount = 0;
                            RIL.this.mSocket = s2;
                            Rlog.i(RIL.RILJ_LOG_TAG, "Connected to '" + rilSocket + "' socket");
                            int length = 0;
                            try {
                                InputStream is = RIL.this.mSocket.getInputStream();
                                while (true) {
                                    length = RIL.readRilMessage(is, this.buffer);
                                    if (length >= 0) {
                                        Parcel p = Parcel.obtain();
                                        p.unmarshall(this.buffer, 0, length);
                                        p.setDataPosition(0);
                                        RIL.this.processResponse(p);
                                        p.recycle();
                                    }
                                    break;
                                }
                            } catch (IOException ex) {
                                Rlog.i(RIL.RILJ_LOG_TAG, "'" + rilSocket + "' socket closed", ex);
                            } catch (Throwable th) {
                                tr = th;
                                s = s2;
                            }
                            Rlog.i(RIL.RILJ_LOG_TAG, "Disconnected from '" + rilSocket + "' socket");
                            RIL.this.setRadioState(RadioState.RADIO_UNAVAILABLE);
                            try {
                                RIL.this.mSocket.close();
                            } catch (IOException e) {
                            }
                            RIL.this.mSocket = null;
                            RILRequest.resetSerial();
                            RIL.this.clearRequestList(1, false);
                        } catch (IOException e2) {
                            s = s2;
                            if (s != null) {
                                try {
                                    s.close();
                                } catch (IOException e3) {
                                }
                            }
                            if (retryCount != 8) {
                                Rlog.e(RIL.RILJ_LOG_TAG, "Couldn't find '" + rilSocket + "' socket after " + retryCount + " times, continuing to retry silently");
                            } else if (retryCount > 0 && retryCount < 8) {
                                Rlog.i(RIL.RILJ_LOG_TAG, "Couldn't find '" + rilSocket + "' socket; retrying after timeout");
                            }
                            try {
                                Thread.sleep(4000);
                            } catch (InterruptedException e4) {
                            }
                            retryCount++;
                        }
                    } catch (IOException e5) {
                        if (s != null) {
                            s.close();
                        }
                        if (retryCount != 8) {
                            Rlog.i(RIL.RILJ_LOG_TAG, "Couldn't find '" + rilSocket + "' socket; retrying after timeout");
                        } else {
                            Rlog.e(RIL.RILJ_LOG_TAG, "Couldn't find '" + rilSocket + "' socket after " + retryCount + " times, continuing to retry silently");
                        }
                        Thread.sleep(4000);
                        retryCount++;
                    }
                } catch (Throwable th2) {
                    tr = th2;
                }
            }
            Rlog.e(RIL.RILJ_LOG_TAG, "Uncaught exception", tr);
            RIL.this.notifyRegistrantsRilConnectionChanged(-1);
        }
    }

    class RILSender extends Handler implements Runnable {
        byte[] dataLength = new byte[4];

        public RILSender(Looper looper) {
            super(looper);
        }

        public void run() {
        }

        public void handleMessage(Message msg) {
            RILRequest rr = (RILRequest) msg.obj;
            switch (msg.what) {
                case 1:
                    try {
                        LocalSocket s = RIL.this.mSocket;
                        if (s == null) {
                            rr.onError(1, null);
                            rr.release();
                            RIL.this.decrementWakeLock();
                            return;
                        }
                        synchronized (RIL.this.mRequestList) {
                            RIL.this.mRequestList.append(rr.mSerial, rr);
                        }
                        byte[] data = rr.mParcel.marshall();
                        rr.mParcel.recycle();
                        rr.mParcel = null;
                        if (data.length > 8192) {
                            throw new RuntimeException("Parcel larger than max bytes allowed! " + data.length);
                        }
                        byte[] bArr = this.dataLength;
                        this.dataLength[1] = (byte) 0;
                        bArr[0] = (byte) 0;
                        this.dataLength[2] = (byte) ((data.length >> 8) & 255);
                        this.dataLength[3] = (byte) (data.length & 255);
                        s.getOutputStream().write(this.dataLength);
                        s.getOutputStream().write(data);
                        return;
                    } catch (IOException ex) {
                        Rlog.e(RIL.RILJ_LOG_TAG, "IOException", ex);
                        if (RIL.this.findAndRemoveRequestFromList(rr.mSerial) != null) {
                            rr.onError(1, null);
                            rr.release();
                            RIL.this.decrementWakeLock();
                            return;
                        }
                        return;
                    } catch (RuntimeException exc) {
                        Rlog.e(RIL.RILJ_LOG_TAG, "Uncaught exception ", exc);
                        if (RIL.this.findAndRemoveRequestFromList(rr.mSerial) != null) {
                            rr.onError(2, null);
                            rr.release();
                            RIL.this.decrementWakeLock();
                            return;
                        }
                        return;
                    }
                case 2:
                    synchronized (RIL.this.mRequestList) {
                        if (RIL.this.clearWakeLock()) {
                            int count = RIL.this.mRequestList.size();
                            Rlog.d(RIL.RILJ_LOG_TAG, "WAKE_LOCK_TIMEOUT  mRequestList=" + count);
                            for (int i = 0; i < count; i++) {
                                rr = (RILRequest) RIL.this.mRequestList.valueAt(i);
                                Rlog.d(RIL.RILJ_LOG_TAG, i + ": [" + rr.mSerial + "] " + RIL.requestToString(rr.mRequest));
                            }
                        }
                    }
                    return;
                default:
                    return;
            }
        }
    }

    private static int readRilMessage(InputStream is, byte[] buffer) throws IOException {
        int offset = 0;
        int remaining = 4;
        do {
            int countRead = is.read(buffer, offset, remaining);
            if (countRead < 0) {
                Rlog.e(RILJ_LOG_TAG, "Hit EOS reading message length");
                return -1;
            }
            offset += countRead;
            remaining -= countRead;
        } while (remaining > 0);
        int messageLength = ((((buffer[0] & 255) << 24) | ((buffer[1] & 255) << 16)) | ((buffer[2] & 255) << 8)) | (buffer[3] & 255);
        offset = 0;
        remaining = messageLength;
        do {
            countRead = is.read(buffer, offset, remaining);
            if (countRead < 0) {
                Rlog.e(RILJ_LOG_TAG, "Hit EOS reading message.  messageLength=" + messageLength + " remaining=" + remaining);
                return -1;
            }
            offset += countRead;
            remaining -= countRead;
        } while (remaining > 0);
        return messageLength;
    }

    public RIL(Context context, int preferredNetworkType, int cdmaSubscription) {
        this(context, preferredNetworkType, cdmaSubscription, null);
    }

    public RIL(Context context, int preferredNetworkType, int cdmaSubscription, Integer instanceId) {
        super(context);
        this.mDefaultDisplayState = 0;
        this.mRequestList = new SparseArray();
        this.initPhoneType = 0;
        this.mTestingEmergencyCall = new AtomicBoolean(false);
        this.mDisplayListener = new C00241();
        riljLog("RIL(context, preferredNetworkType=" + preferredNetworkType + " cdmaSubscription=" + cdmaSubscription + " instanceId=" + instanceId + ")");
        this.mContext = context;
        this.mCdmaSubscription = cdmaSubscription;
        this.mPreferredNetworkType = preferredNetworkType;
        this.mPhoneType = 0;
        this.mInstanceId = instanceId;
        this.mWakeLock = ((PowerManager) context.getSystemService("power")).newWakeLock(1, RILJ_LOG_TAG + (this.mInstanceId == null ? "" : this.mInstanceId));
        this.mWakeLock.setReferenceCounted(false);
        this.mWakeLockTimeout = SystemProperties.getInt("ro.ril.wake_lock_timeout", 60000);
        this.mWakeLockCount = 0;
        this.mSenderThread = new HandlerThread("RILSender");
        this.mSenderThread.start();
        this.mSender = new RILSender(this.mSenderThread.getLooper());
        if (((ConnectivityManager) context.getSystemService("connectivity")).isNetworkSupported(0)) {
            riljLog("Starting RILReceiver");
            this.mReceiver = new RILReceiver();
            this.mReceiverThread = new Thread(this.mReceiver, "RILReceiver");
            this.mReceiverThread.start();
            DisplayManager dm = (DisplayManager) context.getSystemService("display");
            this.mDefaultDisplay = dm.getDisplay(0);
            dm.registerDisplayListener(this.mDisplayListener, null);
        } else {
            riljLog("Not starting RILReceiver: wifi-only");
        }
        TelephonyDevController tdc = TelephonyDevController.getInstance();
        TelephonyDevController.registerRIL(this);
    }

    public void getVoiceRadioTechnology(Message result) {
        RILRequest rr = RILRequest.obtain(108, result);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
        send(rr);
    }

    public void getImsRegistrationState(Message result) {
        RILRequest rr = RILRequest.obtain(112, result);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
        send(rr);
    }

    public void setOnNITZTime(Handler h, int what, Object obj) {
        super.setOnNITZTime(h, what, obj);
        if (this.mLastNITZTimeInfo != null) {
            this.mNITZTimeRegistrant.notifyRegistrant(new AsyncResult(null, this.mLastNITZTimeInfo, null));
            this.mLastNITZTimeInfo = null;
        }
    }

    public void getIccCardStatus(Message result) {
        RILRequest rr = RILRequest.obtain(1, result);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
        send(rr);
    }

    public void setUiccSubscription(int slotId, int appIndex, int subId, int subStatus, Message result) {
        RILRequest rr = RILRequest.obtain(122, result);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest) + " slot: " + slotId + " appIndex: " + appIndex + " subId: " + subId + " subStatus: " + subStatus);
        rr.mParcel.writeInt(slotId);
        rr.mParcel.writeInt(appIndex);
        rr.mParcel.writeInt(subId);
        rr.mParcel.writeInt(subStatus);
        send(rr);
    }

    public void setDataAllowed(boolean allowed, Message result) {
        int i = 1;
        RILRequest rr = RILRequest.obtain(123, result);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
        rr.mParcel.writeInt(1);
        Parcel parcel = rr.mParcel;
        if (!allowed) {
            i = 0;
        }
        parcel.writeInt(i);
        send(rr);
    }

    public void supplyIccPin(String pin, Message result) {
        supplyIccPinForApp(pin, null, result);
    }

    public void supplyIccPinForApp(String pin, String aid, Message result) {
        RILRequest rr = RILRequest.obtain(2, result);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
        rr.mParcel.writeInt(2);
        rr.mParcel.writeString(pin);
        rr.mParcel.writeString(aid);
        send(rr);
    }

    public void supplyIccPuk(String puk, String newPin, Message result) {
        supplyIccPukForApp(puk, newPin, null, result);
    }

    public void supplyIccPukForApp(String puk, String newPin, String aid, Message result) {
        RILRequest rr = RILRequest.obtain(3, result);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
        rr.mParcel.writeInt(3);
        rr.mParcel.writeString(puk);
        rr.mParcel.writeString(newPin);
        rr.mParcel.writeString(aid);
        send(rr);
    }

    public void supplyIccPin2(String pin, Message result) {
        supplyIccPin2ForApp(pin, null, result);
    }

    public void supplyIccPin2ForApp(String pin, String aid, Message result) {
        RILRequest rr = RILRequest.obtain(4, result);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
        rr.mParcel.writeInt(2);
        rr.mParcel.writeString(pin);
        rr.mParcel.writeString(aid);
        send(rr);
    }

    public void supplyIccPuk2(String puk2, String newPin2, Message result) {
        supplyIccPuk2ForApp(puk2, newPin2, null, result);
    }

    public void supplyIccPuk2ForApp(String puk, String newPin2, String aid, Message result) {
        RILRequest rr = RILRequest.obtain(5, result);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
        rr.mParcel.writeInt(3);
        rr.mParcel.writeString(puk);
        rr.mParcel.writeString(newPin2);
        rr.mParcel.writeString(aid);
        send(rr);
    }

    public void changeIccPin(String oldPin, String newPin, Message result) {
        changeIccPinForApp(oldPin, newPin, null, result);
    }

    public void changeIccPinForApp(String oldPin, String newPin, String aid, Message result) {
        RILRequest rr = RILRequest.obtain(6, result);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
        rr.mParcel.writeInt(3);
        rr.mParcel.writeString(oldPin);
        rr.mParcel.writeString(newPin);
        rr.mParcel.writeString(aid);
        send(rr);
    }

    public void changeIccPin2(String oldPin2, String newPin2, Message result) {
        changeIccPin2ForApp(oldPin2, newPin2, null, result);
    }

    public void changeIccPin2ForApp(String oldPin2, String newPin2, String aid, Message result) {
        RILRequest rr = RILRequest.obtain(7, result);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
        rr.mParcel.writeInt(3);
        rr.mParcel.writeString(oldPin2);
        rr.mParcel.writeString(newPin2);
        rr.mParcel.writeString(aid);
        send(rr);
    }

    public void changeBarringPassword(String facility, String oldPwd, String newPwd, Message result) {
        RILRequest rr = RILRequest.obtain(44, result);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
        rr.mParcel.writeInt(3);
        rr.mParcel.writeString(facility);
        rr.mParcel.writeString(oldPwd);
        rr.mParcel.writeString(newPwd);
        send(rr);
    }

    public void supplyNetworkDepersonalization(String netpin, Message result) {
        RILRequest rr = RILRequest.obtain(8, result);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
        rr.mParcel.writeInt(1);
        rr.mParcel.writeString(netpin);
        send(rr);
    }

    public void supplyNetworkDepersonalization(String netpin, int lockState, Message result) {
        RILRequest rr = RILRequest.obtain(8, result);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest) + " Type:" + "PERSOSUBSTATE_SIM_NETWORK");
        rr.mParcel.writeInt(lockState);
        rr.mParcel.writeString(netpin);
        send(rr);
    }

    public void getCurrentCalls(Message result) {
        RILRequest rr = RILRequest.obtain(9, result);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
        send(rr);
    }

    @Deprecated
    public void getPDPContextList(Message result) {
        getDataCallList(result);
    }

    public void getDataCallList(Message result) {
        RILRequest rr = RILRequest.obtain(57, result);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
        send(rr);
    }

    public void dial(String address, int clirMode, Message result) {
        dial(address, clirMode, null, result);
    }

    public void dial(String address, int clirMode, UUSInfo uusInfo, Message result) {
        dial(address, clirMode, uusInfo, null, result);
    }

    public void dial(String address, int clirMode, UUSInfo uusInfo, CallDetails callDetails, Message result) {
        RILRequest rr = RILRequest.obtain(10, result);
        rr.mParcel.writeString(address);
        rr.mParcel.writeInt(clirMode);
        if (callDetails != null) {
            rr.mParcel.writeInt(callDetails.call_type);
            rr.mParcel.writeInt(callDetails.call_domain);
            rr.mParcel.writeString(callDetails.getCsvFromExtras());
        } else {
            rr.mParcel.writeInt(0);
            rr.mParcel.writeInt(1);
            rr.mParcel.writeString("");
        }
        if (uusInfo == null) {
            rr.mParcel.writeInt(0);
        } else {
            rr.mParcel.writeInt(1);
            rr.mParcel.writeInt(uusInfo.getType());
            rr.mParcel.writeInt(uusInfo.getDcs());
            rr.mParcel.writeByteArray(uusInfo.getUserData());
        }
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest) + " " + callDetails);
        send(rr);
    }

    public void dialEmergencyCall(String address, int clirMode, Message result) {
        dialEmergencyCall(address, clirMode, null, result);
    }

    public void dialEmergencyCall(String address, int clirMode, CallDetails callDetails, Message result) {
        RILRequest rr = RILRequest.obtain(10001, result);
        rr.mParcel.writeString(address);
        rr.mParcel.writeInt(clirMode);
        if (callDetails != null) {
            rr.mParcel.writeInt(callDetails.call_type);
            rr.mParcel.writeInt(callDetails.call_domain);
            rr.mParcel.writeString("");
        } else {
            rr.mParcel.writeInt(0);
            rr.mParcel.writeInt(3);
            rr.mParcel.writeString("");
        }
        rr.mParcel.writeInt(0);
        if (callDetails != null) {
            riljLog(rr.serialString() + "> " + requestToString(rr.mRequest) + " " + callDetails);
        } else {
            riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
        }
        send(rr);
    }

    public void deflect(String address, Message result) {
        RILRequest rr = RILRequest.obtain(10002, result);
        rr.mParcel.writeString(address);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
        send(rr);
    }

    public void getPreferredNetworkList(Message response) {
        RILRequest rr = RILRequest.obtain(10016, response);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
        send(rr);
    }

    public void setPreferredNetworkList(int index, String operator, String plmn, int gsmAct, int gsmCompactAct, int utranAct, int mode, Message response) {
        RILRequest rr = RILRequest.obtain(10015, response);
        rr.mParcel.writeInt(index);
        rr.mParcel.writeString(operator);
        rr.mParcel.writeString(plmn);
        rr.mParcel.writeInt(gsmAct);
        rr.mParcel.writeInt(gsmCompactAct);
        rr.mParcel.writeInt(utranAct);
        rr.mParcel.writeInt(mode);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest) + ", " + index + ", " + operator + ", " + plmn + ", " + gsmAct + "," + gsmCompactAct + ", " + utranAct + ", " + mode);
        send(rr);
    }

    public void modifyCallInitiate(CallModify callModify, Message result) {
        RILRequest rr = RILRequest.obtain(10003, result);
        rr.mParcel.writeInt(callModify.call_index);
        rr.mParcel.writeInt(callModify.call_details.call_type);
        rr.mParcel.writeInt(callModify.call_details.call_domain);
        rr.mParcel.writeString(callModify.call_details.getCsvFromExtras());
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest) + " " + callModify);
        send(rr);
    }

    public void modifyCallConfirm(CallModify callModify, Message result) {
        RILRequest rr = RILRequest.obtain(10004, result);
        rr.mParcel.writeInt(callModify.call_index);
        rr.mParcel.writeInt(callModify.call_details.call_type);
        rr.mParcel.writeInt(callModify.call_details.call_domain);
        rr.mParcel.writeString(callModify.call_details.getCsvFromExtras());
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest) + " " + callModify);
        send(rr);
    }

    public void setVoiceDomainPref(int pref, Message result) {
        RILRequest rr = RILRequest.obtain(10005, result);
        rr.mParcel.writeInt(1);
        rr.mParcel.writeInt(pref);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest) + " " + pref);
        send(rr);
    }

    private void sendSafemode(boolean on) {
        int i = 1;
        RILRequest rr = RILRequest.obtain(10006, null);
        rr.mParcel.writeInt(1);
        Parcel parcel = rr.mParcel;
        if (!on) {
            i = 0;
        }
        parcel.writeInt(i);
        rr.mParcel.writeInt(0);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest) + " " + on);
        send(rr);
    }

    public void setTransmitPower(int powerLevel, Message result) {
        RILRequest rr = RILRequest.obtain(10007, result);
        rr.mParcel.writeInt(1);
        rr.mParcel.writeInt(powerLevel);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest) + " " + powerLevel);
        send(rr);
    }

    public void getCbConfig(Message response) {
        RILRequest rr = RILRequest.obtain(10008, response);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
        send(rr);
    }

    public void setSimPower(int on, Message result) {
        RILRequest rr = RILRequest.obtain(10023, result);
        rr.mParcel.writeInt(1);
        rr.mParcel.writeInt(on);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest) + " int : " + on);
        send(rr);
    }

    public void getIMSI(Message result) {
        getIMSIForApp(null, result);
    }

    public void getIMSIForApp(String aid, Message result) {
        RILRequest rr = RILRequest.obtain(11, result);
        rr.mParcel.writeInt(1);
        rr.mParcel.writeString(aid);
        String dbgMsg = rr.serialString() + "> getIMSI: " + requestToString(rr.mRequest) + " aid: ";
        if (SHIP_BUILD) {
            dbgMsg = dbgMsg + "xxx";
        } else {
            dbgMsg = dbgMsg + aid;
        }
        riljLog(dbgMsg);
        send(rr);
    }

    public void getIMEI(Message result) {
        RILRequest rr = RILRequest.obtain(38, result);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
        send(rr);
    }

    public void getIMEISV(Message result) {
        RILRequest rr = RILRequest.obtain(39, result);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
        send(rr);
    }

    public void hangupConnection(int gsmIndex, Message result) {
        riljLog("hangupConnection: gsmIndex=" + gsmIndex);
        RILRequest rr = RILRequest.obtain(12, result);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest) + " " + gsmIndex);
        rr.mParcel.writeInt(1);
        rr.mParcel.writeInt(gsmIndex);
        send(rr);
    }

    public void hangupWaitingOrBackground(Message result) {
        RILRequest rr = RILRequest.obtain(13, result);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
        send(rr);
    }

    public void hangupForegroundResumeBackground(Message result) {
        RILRequest rr = RILRequest.obtain(14, result);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
        send(rr);
    }

    public void switchWaitingOrHoldingAndActive(Message result) {
        RILRequest rr = RILRequest.obtain(15, result);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
        send(rr);
    }

    public void conference(Message result) {
        RILRequest rr = RILRequest.obtain(16, result);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
        send(rr);
    }

    public void setPreferredVoicePrivacy(boolean enable, Message result) {
        int i = 1;
        RILRequest rr = RILRequest.obtain(82, result);
        rr.mParcel.writeInt(1);
        Parcel parcel = rr.mParcel;
        if (!enable) {
            i = 0;
        }
        parcel.writeInt(i);
        send(rr);
    }

    public void getPreferredVoicePrivacy(Message result) {
        send(RILRequest.obtain(83, result));
    }

    public void separateConnection(int gsmIndex, Message result) {
        RILRequest rr = RILRequest.obtain(52, result);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest) + " " + gsmIndex);
        rr.mParcel.writeInt(1);
        rr.mParcel.writeInt(gsmIndex);
        send(rr);
    }

    public void acceptCall(Message result) {
        acceptCall(0, result);
    }

    public void acceptCall(int type, Message result) {
        RILRequest rr = RILRequest.obtain(40, result);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest) + " " + type);
        rr.mParcel.writeInt(1);
        rr.mParcel.writeInt(type);
        send(rr);
    }

    public void rejectCall(Message result) {
        RILRequest rr = RILRequest.obtain(17, result);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
        send(rr);
    }

    public void explicitCallTransfer(Message result) {
        RILRequest rr = RILRequest.obtain(72, result);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
        send(rr);
    }

    public void getLastCallFailCause(Message result) {
        RILRequest rr = RILRequest.obtain(18, result);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
        send(rr);
    }

    @Deprecated
    public void getLastPdpFailCause(Message result) {
        getLastDataCallFailCause(result);
    }

    public void getLastDataCallFailCause(Message result) {
        RILRequest rr = RILRequest.obtain(56, result);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
        send(rr);
    }

    public void setMute(boolean enableMute, Message response) {
        int i = 1;
        RILRequest rr = RILRequest.obtain(53, response);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest) + " " + enableMute);
        rr.mParcel.writeInt(1);
        Parcel parcel = rr.mParcel;
        if (!enableMute) {
            i = 0;
        }
        parcel.writeInt(i);
        send(rr);
    }

    public void getMute(Message response) {
        RILRequest rr = RILRequest.obtain(54, response);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
        send(rr);
    }

    public void getSignalStrength(Message result) {
        RILRequest rr = RILRequest.obtain(19, result);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
        send(rr);
    }

    public void getVoiceRegistrationState(Message result) {
        RILRequest rr = RILRequest.obtain(20, result);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
        send(rr);
    }

    public void getDataRegistrationState(Message result) {
        RILRequest rr = RILRequest.obtain(21, result);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
        send(rr);
    }

    public void getOperator(Message result) {
        RILRequest rr = RILRequest.obtain(22, result);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
        send(rr);
    }

    public void getHardwareConfig(Message result) {
        RILRequest rr = RILRequest.obtain(124, result);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
        send(rr);
    }

    public void sendDtmf(char c, Message result) {
        RILRequest rr = RILRequest.obtain(24, result);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
        rr.mParcel.writeString(Character.toString(c));
        send(rr);
    }

    public void startDtmf(char c, Message result) {
        RILRequest rr = RILRequest.obtain(49, result);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
        rr.mParcel.writeString(Character.toString(c));
        send(rr);
    }

    public void stopDtmf(Message result) {
        RILRequest rr = RILRequest.obtain(50, result);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
        send(rr);
    }

    public void sendBurstDtmf(String dtmfString, int on, int off, Message result) {
        RILRequest rr = RILRequest.obtain(85, result);
        rr.mParcel.writeInt(3);
        rr.mParcel.writeString(dtmfString);
        rr.mParcel.writeString(Integer.toString(on));
        rr.mParcel.writeString(Integer.toString(off));
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest) + " : " + dtmfString);
        send(rr);
    }

    private void constructGsmSendSmsRilRequest(RILRequest rr, String smscPDU, String pdu) {
        rr.mParcel.writeInt(2);
        rr.mParcel.writeString(smscPDU);
        rr.mParcel.writeString(pdu);
    }

    public void sendSMS(String smscPDU, String pdu, Message result) {
        RILRequest rr = RILRequest.obtain(25, result);
        constructGsmSendSmsRilRequest(rr, smscPDU, pdu);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
        send(rr);
    }

    public void sendSMSExpectMore(String smscPDU, String pdu, Message result) {
        RILRequest rr = RILRequest.obtain(26, result);
        constructGsmSendSmsRilRequest(rr, smscPDU, pdu);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
        send(rr);
    }

    private void constructCdmaSendSmsRilRequest(RILRequest rr, byte[] pdu) {
        DataInputStream dis = new DataInputStream(new ByteArrayInputStream(pdu));
        try {
            int i;
            rr.mParcel.writeInt(dis.readInt());
            rr.mParcel.writeByte((byte) dis.readInt());
            rr.mParcel.writeInt(dis.readInt());
            rr.mParcel.writeInt(dis.read());
            rr.mParcel.writeInt(dis.read());
            rr.mParcel.writeInt(dis.read());
            rr.mParcel.writeInt(dis.read());
            int address_nbr_of_digits = (byte) dis.read();
            rr.mParcel.writeByte((byte) address_nbr_of_digits);
            for (i = 0; i < address_nbr_of_digits; i++) {
                rr.mParcel.writeByte(dis.readByte());
            }
            rr.mParcel.writeInt(dis.read());
            rr.mParcel.writeByte((byte) dis.read());
            int subaddr_nbr_of_digits = (byte) dis.read();
            rr.mParcel.writeByte((byte) subaddr_nbr_of_digits);
            for (i = 0; i < subaddr_nbr_of_digits; i++) {
                rr.mParcel.writeByte(dis.readByte());
            }
            int bearerDataLength = dis.read();
            rr.mParcel.writeInt(bearerDataLength);
            for (i = 0; i < bearerDataLength; i++) {
                rr.mParcel.writeByte(dis.readByte());
            }
        } catch (IOException ex) {
            riljLog("sendSmsCdma: conversion from input stream to object failed: " + ex);
        }
    }

    public void sendCdmaSms(byte[] pdu, Message result) {
        RILRequest rr = RILRequest.obtain(87, result);
        constructCdmaSendSmsRilRequest(rr, pdu);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
        send(rr);
    }

    public void sendImsGsmSms(String smscPDU, String pdu, int retry, int messageRef, Message result) {
        RILRequest rr = RILRequest.obtain(113, result);
        rr.mParcel.writeInt(1);
        rr.mParcel.writeByte((byte) retry);
        rr.mParcel.writeInt(messageRef);
        constructGsmSendSmsRilRequest(rr, smscPDU, pdu);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
        send(rr);
    }

    public void sendImsCdmaSms(byte[] pdu, int retry, int messageRef, Message result) {
        RILRequest rr = RILRequest.obtain(113, result);
        rr.mParcel.writeInt(2);
        rr.mParcel.writeByte((byte) retry);
        rr.mParcel.writeInt(messageRef);
        constructCdmaSendSmsRilRequest(rr, pdu);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
        send(rr);
    }

    public void deleteSmsOnSim(int index, Message response) {
        RILRequest rr = RILRequest.obtain(64, response);
        rr.mParcel.writeInt(1);
        rr.mParcel.writeInt(index);
        send(rr);
    }

    public void deleteSmsOnRuim(int index, Message response) {
        RILRequest rr = RILRequest.obtain(97, response);
        rr.mParcel.writeInt(1);
        rr.mParcel.writeInt(index);
        send(rr);
    }

    public void writeSmsToSim(int status, String smsc, String pdu, Message response) {
        status = translateStatus(status);
        RILRequest rr = RILRequest.obtain(63, response);
        rr.mParcel.writeInt(status);
        rr.mParcel.writeString(pdu);
        rr.mParcel.writeString(smsc);
        send(rr);
    }

    public void writeSmsToRuim(int status, String pdu, Message response) {
        status = translateStatus(status);
        RILRequest rr = RILRequest.obtain(96, response);
        rr.mParcel.writeInt(status);
        rr.mParcel.writeString(pdu);
        send(rr);
    }

    private int translateStatus(int status) {
        switch (status & 7) {
            case 3:
                return 0;
            case 5:
                return 3;
            case 7:
                return 2;
            default:
                return 1;
        }
    }

    public void setupDataCall(String radioTechnology, String profile, String apn, String user, String password, String authType, String protocol, Message result) {
        RILRequest rr = RILRequest.obtain(27, result);
        rr.mParcel.writeInt(7);
        rr.mParcel.writeString(radioTechnology);
        rr.mParcel.writeString(profile);
        rr.mParcel.writeString(apn);
        rr.mParcel.writeString(user);
        rr.mParcel.writeString(password);
        rr.mParcel.writeString(authType);
        rr.mParcel.writeString(protocol);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest) + " " + radioTechnology + " " + profile + " " + apn + " " + user + " " + password + " " + authType + " " + protocol);
        send(rr);
    }

    public void deactivateDataCall(int cid, int reason, Message result) {
        RILRequest rr = RILRequest.obtain(41, result);
        rr.mParcel.writeInt(2);
        rr.mParcel.writeString(Integer.toString(cid));
        rr.mParcel.writeString(Integer.toString(reason));
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest) + " " + cid + " " + reason);
        send(rr);
    }

    public void setRadioPower(boolean on, Message result) {
        int i = 1;
        RILRequest rr = RILRequest.obtain(23, result);
        rr.mParcel.writeInt(1);
        Parcel parcel = rr.mParcel;
        if (!on) {
            i = 0;
        }
        parcel.writeInt(i);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest) + (on ? " on" : " off"));
        send(rr);
    }

    public void requestShutdown(Message result) {
        RILRequest rr = RILRequest.obtain(129, result);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
        send(rr);
    }

    public void setSuppServiceNotifications(boolean enable, Message result) {
        int i = 1;
        RILRequest rr = RILRequest.obtain(62, result);
        rr.mParcel.writeInt(1);
        Parcel parcel = rr.mParcel;
        if (!enable) {
            i = 0;
        }
        parcel.writeInt(i);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
        send(rr);
    }

    public void acknowledgeLastIncomingGsmSms(boolean success, int cause, Message result) {
        RILRequest rr = RILRequest.obtain(37, result);
        rr.mParcel.writeInt(2);
        rr.mParcel.writeInt(success ? 1 : 0);
        rr.mParcel.writeInt(cause);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest) + " " + success + " " + cause);
        send(rr);
    }

    public void acknowledgeLastIncomingCdmaSms(boolean success, int cause, Message result) {
        RILRequest rr = RILRequest.obtain(88, result);
        rr.mParcel.writeInt(success ? 0 : 1);
        rr.mParcel.writeInt(cause);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest) + " " + success + " " + cause);
        send(rr);
    }

    public void acknowledgeIncomingGsmSmsWithPdu(boolean success, String ackPdu, Message result) {
        RILRequest rr = RILRequest.obtain(106, result);
        rr.mParcel.writeInt(2);
        rr.mParcel.writeString(success ? "1" : "0");
        rr.mParcel.writeString(ackPdu);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest) + ' ' + success + " [" + ackPdu + ']');
        send(rr);
    }

    public void iccIO(int command, int fileid, String path, int p1, int p2, int p3, String data, String pin2, Message result) {
        iccIOForApp(command, fileid, path, p1, p2, p3, data, pin2, null, result);
    }

    public void iccIOForApp(int command, int fileid, String path, int p1, int p2, int p3, String data, String pin2, String aid, Message result) {
        RILRequest rr = RILRequest.obtain(28, result);
        rr.mParcel.writeInt(command);
        rr.mParcel.writeInt(fileid);
        rr.mParcel.writeString(path);
        rr.mParcel.writeInt(p1);
        rr.mParcel.writeInt(p2);
        rr.mParcel.writeInt(p3);
        rr.mParcel.writeString(data);
        rr.mParcel.writeString(pin2);
        rr.mParcel.writeString(aid);
        String dbgMsg = rr.serialString() + "> iccIO: " + requestToString(rr.mRequest) + " 0x" + Integer.toHexString(command) + " 0x" + Integer.toHexString(fileid) + " " + " path: " + path + "," + p1 + "," + p2 + "," + p3 + " aid: ";
        if (SHIP_BUILD) {
            dbgMsg = dbgMsg + "xxx";
        } else {
            dbgMsg = dbgMsg + aid;
        }
        riljLog(dbgMsg);
        send(rr);
    }

    public void getCLIR(Message result) {
        RILRequest rr = RILRequest.obtain(31, result);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
        send(rr);
    }

    public void setCLIR(int clirMode, Message result) {
        RILRequest rr = RILRequest.obtain(32, result);
        rr.mParcel.writeInt(1);
        rr.mParcel.writeInt(clirMode);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest) + " " + clirMode);
        send(rr);
    }

    public void queryCallWaiting(int serviceClass, Message response) {
        RILRequest rr = RILRequest.obtain(35, response);
        rr.mParcel.writeInt(1);
        rr.mParcel.writeInt(serviceClass);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest) + " " + serviceClass);
        send(rr);
    }

    public void setCallWaiting(boolean enable, int serviceClass, Message response) {
        RILRequest rr = RILRequest.obtain(36, response);
        rr.mParcel.writeInt(2);
        rr.mParcel.writeInt(enable ? 1 : 0);
        rr.mParcel.writeInt(serviceClass);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest) + " " + enable + ", " + serviceClass);
        send(rr);
    }

    public void setNetworkSelectionModeAutomatic(Message response) {
        RILRequest rr = RILRequest.obtain(46, response);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
        send(rr);
    }

    public void setNetworkSelectionModeManual(String operatorNumeric, Message response) {
        RILRequest rr = RILRequest.obtain(47, response);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest) + " " + operatorNumeric);
        rr.mParcel.writeString(operatorNumeric);
        send(rr);
    }

    public void getNetworkSelectionMode(Message response) {
        RILRequest rr = RILRequest.obtain(45, response);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
        send(rr);
    }

    public void getAvailableNetworks(Message response) {
        RILRequest rr = RILRequest.obtain(48, response);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
        send(rr);
    }

    public void setCallForward(int action, int cfReason, int serviceClass, String number, int timeSeconds, Message response) {
        RILRequest rr = RILRequest.obtain(34, response);
        rr.mParcel.writeInt(action);
        rr.mParcel.writeInt(cfReason);
        rr.mParcel.writeInt(serviceClass);
        rr.mParcel.writeInt(PhoneNumberUtils.toaFromString(number));
        rr.mParcel.writeString(number);
        rr.mParcel.writeInt(timeSeconds);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest) + " " + action + " " + cfReason + " " + serviceClass + timeSeconds);
        send(rr);
    }

    public void queryCallForwardStatus(int cfReason, int serviceClass, String number, Message response) {
        RILRequest rr = RILRequest.obtain(33, response);
        rr.mParcel.writeInt(2);
        rr.mParcel.writeInt(cfReason);
        rr.mParcel.writeInt(serviceClass);
        rr.mParcel.writeInt(PhoneNumberUtils.toaFromString(number));
        rr.mParcel.writeString(number);
        rr.mParcel.writeInt(0);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest) + " " + cfReason + " " + serviceClass);
        send(rr);
    }

    public void queryCLIP(Message response) {
        RILRequest rr = RILRequest.obtain(55, response);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
        send(rr);
    }

    public void getBasebandVersion(Message response) {
        RILRequest rr = RILRequest.obtain(51, response);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
        send(rr);
    }

    public void queryFacilityLock(String facility, String password, int serviceClass, Message response) {
        queryFacilityLockForApp(facility, password, serviceClass, null, response);
    }

    public void queryFacilityLockForApp(String facility, String password, int serviceClass, String appId, Message response) {
        RILRequest rr = RILRequest.obtain(42, response);
        String dbgMsg = rr.serialString() + "> " + requestToString(rr.mRequest);
        if (!SHIP_BUILD) {
            dbgMsg = dbgMsg + " [" + facility + " " + serviceClass + " " + appId + "]";
        }
        riljLog(dbgMsg);
        rr.mParcel.writeInt(4);
        rr.mParcel.writeString(facility);
        rr.mParcel.writeString(password);
        rr.mParcel.writeString(Integer.toString(serviceClass));
        rr.mParcel.writeString(appId);
        send(rr);
    }

    public void setFacilityLock(String facility, boolean lockState, String password, int serviceClass, Message response) {
        setFacilityLockForApp(facility, lockState, password, serviceClass, null, response);
    }

    public void setFacilityLockForApp(String facility, boolean lockState, String password, int serviceClass, String appId, Message response) {
        RILRequest rr = RILRequest.obtain(43, response);
        String dbgMsg = rr.serialString() + "> " + requestToString(rr.mRequest) + " [" + facility + " " + lockState + " " + serviceClass + " ";
        if (!SHIP_BUILD) {
            dbgMsg = dbgMsg + appId;
        }
        riljLog(dbgMsg + "]");
        rr.mParcel.writeInt(5);
        rr.mParcel.writeString(facility);
        rr.mParcel.writeString(lockState ? "1" : "0");
        rr.mParcel.writeString(password);
        rr.mParcel.writeString(Integer.toString(serviceClass));
        rr.mParcel.writeString(appId);
        send(rr);
    }

    public void sendUSSD(String ussdString, Message response) {
        RILRequest rr = RILRequest.obtain(29, response);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest) + " " + "*******");
        rr.mParcel.writeString(ussdString);
        send(rr);
    }

    public void cancelPendingUssd(Message response) {
        RILRequest rr = RILRequest.obtain(30, response);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
        send(rr);
    }

    public void resetRadio(Message result) {
        RILRequest rr = RILRequest.obtain(58, result);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
        send(rr);
    }

    public void invokeOemRilRequestRaw(byte[] data, Message response) {
        RILRequest rr = RILRequest.obtain(59, response);
        String dbgMsg = IccUtils.bytesToHexString(data);
        if (SHIP_BUILD) {
            if ("15".equals(dbgMsg.substring(0, 2))) {
                dbgMsg = "****";
            } else if ("1627".equals(dbgMsg.substring(0, 4)) && "KOREA".equals(SystemProperties.get("ro.csc.country_code"))) {
                dbgMsg = "****";
            }
        }
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest) + "[" + dbgMsg + "]");
        rr.mParcel.writeByteArray(data);
        send(rr);
    }

    public void invokeOemRilRequestStrings(String[] strings, Message response) {
        RILRequest rr = RILRequest.obtain(60, response);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
        rr.mParcel.writeStringArray(strings);
        send(rr);
    }

    public void setBandMode(int bandMode, Message response) {
        RILRequest rr = RILRequest.obtain(65, response);
        rr.mParcel.writeInt(1);
        rr.mParcel.writeInt(bandMode);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest) + " " + bandMode);
        send(rr);
    }

    public void setLteBandMode(int bandMode, Message response) {
        RILRequest rr = RILRequest.obtain(10024, response);
        rr.mParcel.writeInt(1);
        rr.mParcel.writeInt(bandMode);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest) + " " + bandMode);
        send(rr);
    }

    public void queryAvailableBandMode(Message response) {
        RILRequest rr = RILRequest.obtain(66, response);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
        send(rr);
    }

    public void sendTerminalResponse(String contents, Message response) {
        RILRequest rr = RILRequest.obtain(70, response);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
        rr.mParcel.writeString(contents);
        send(rr);
    }

    public void sendEnvelope(String contents, Message response) {
        RILRequest rr = RILRequest.obtain(69, response);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
        rr.mParcel.writeString(contents);
        send(rr);
    }

    public void sendEnvelopeWithStatus(String contents, Message response) {
        RILRequest rr = RILRequest.obtain(107, response);
        if (SHIP_BUILD) {
            riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
        } else {
            riljLog(rr.serialString() + "> " + requestToString(rr.mRequest) + '[' + contents + ']');
        }
        rr.mParcel.writeString(contents);
        send(rr);
    }

    public void handleCallSetupRequestFromSim(boolean accept, Message response) {
        int i = 1;
        riljLog("handleCallSetupRequestFromSim");
        RILRequest rr = RILRequest.obtain(71, response);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
        int[] param = new int[1];
        if (!accept) {
            i = 0;
        }
        param[0] = i;
        rr.mParcel.writeIntArray(param);
        send(rr);
    }

    public void setPreferredNetworkType(int networkType, Message response) {
        RILRequest rr = RILRequest.obtain(73, response);
        rr.mParcel.writeInt(1);
        rr.mParcel.writeInt(networkType);
        setInitialPhoneType(networkType);
        this.mPreferredNetworkType = networkType;
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest) + " : " + networkType);
        send(rr);
    }

    public void getPreferredNetworkType(Message response) {
        RILRequest rr = RILRequest.obtain(74, response);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
        send(rr);
    }

    public void getNeighboringCids(Message response) {
        RILRequest rr = RILRequest.obtain(75, response);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
        send(rr);
    }

    public void setLocationUpdates(boolean enable, Message response) {
        int i = 1;
        RILRequest rr = RILRequest.obtain(76, response);
        rr.mParcel.writeInt(1);
        Parcel parcel = rr.mParcel;
        if (!enable) {
            i = 0;
        }
        parcel.writeInt(i);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest) + ": " + enable);
        send(rr);
    }

    public void getSmscAddress(Message result) {
        RILRequest rr = RILRequest.obtain(100, result);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
        send(rr);
    }

    public void setSmscAddress(String address, Message result) {
        RILRequest rr = RILRequest.obtain(Threads.ALERT_EXTREME_THREAD, result);
        rr.mParcel.writeString(address);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest) + " : " + address);
        send(rr);
    }

    public void reportSmsMemoryStatus(boolean available, Message result) {
        int i = 1;
        RILRequest rr = RILRequest.obtain(Threads.ALERT_SEVERE_THREAD, result);
        rr.mParcel.writeInt(1);
        Parcel parcel = rr.mParcel;
        if (!available) {
            i = 0;
        }
        parcel.writeInt(i);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest) + ": " + available);
        send(rr);
    }

    public void reportStkServiceIsRunning(Message result) {
        RILRequest rr = RILRequest.obtain(Threads.ALERT_AMBER_THREAD, result);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
        send(rr);
    }

    public void getGsmBroadcastConfig(Message response) {
        RILRequest rr = RILRequest.obtain(89, response);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
        send(rr);
    }

    public void setGsmBroadcastConfig(SmsBroadcastConfigInfo[] config, Message response) {
        RILRequest rr = RILRequest.obtain(90, response);
        rr.mParcel.writeInt(numOfConfig);
        for (int i = 0; i < numOfConfig; i++) {
            int i2;
            rr.mParcel.writeInt(config[i].getFromServiceId());
            rr.mParcel.writeInt(config[i].getToServiceId());
            rr.mParcel.writeInt(config[i].getFromCodeScheme());
            rr.mParcel.writeInt(config[i].getToCodeScheme());
            Parcel parcel = rr.mParcel;
            if (config[i].isSelected()) {
                i2 = 1;
            } else {
                i2 = 0;
            }
            parcel.writeInt(i2);
        }
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest) + " with " + numOfConfig + " configs : ");
        for (SmsBroadcastConfigInfo smsBroadcastConfigInfo : config) {
            riljLog(smsBroadcastConfigInfo.toString());
        }
        send(rr);
    }

    public void setGsmBroadcastActivation(boolean activate, Message response) {
        int i = 1;
        RILRequest rr = RILRequest.obtain(91, response);
        rr.mParcel.writeInt(1);
        Parcel parcel = rr.mParcel;
        if (activate) {
            i = 0;
        }
        parcel.writeInt(i);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
        send(rr);
    }

    private void updateScreenState() {
        int oldState = this.mDefaultDisplayState;
        this.mDefaultDisplayState = this.mDefaultDisplay.getState();
        if (this.mDefaultDisplayState == oldState) {
            return;
        }
        if (oldState != 2 && this.mDefaultDisplayState == 2) {
            sendScreenState(true);
        } else if ((oldState == 2 || oldState == 0) && this.mDefaultDisplayState != 2) {
            sendScreenState(false);
        }
    }

    private void sendScreenState(boolean on) {
        int i = 1;
        RILRequest rr = RILRequest.obtain(61, null);
        rr.mParcel.writeInt(1);
        Parcel parcel = rr.mParcel;
        if (!on) {
            i = 0;
        }
        parcel.writeInt(i);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest) + ": " + on);
        send(rr);
    }

    protected void onRadioAvailable() {
        updateScreenState();
    }

    private RadioState getRadioStateFromInt(int stateInt) {
        switch (stateInt) {
            case 0:
                return RadioState.RADIO_OFF;
            case 1:
                return RadioState.RADIO_UNAVAILABLE;
            case 10:
                return RadioState.RADIO_ON;
            default:
                throw new RuntimeException("Unrecognized RIL_RadioState: " + stateInt);
        }
    }

    private void switchToRadioState(RadioState newState) {
        RadioState oldState = getRadioState();
        setRadioState(newState);
        String salesCode = SystemProperties.get("ro.csc.sales_code");
        if (("CHN".equals(salesCode) || "CHC".equals(salesCode) || "CHU".equals(salesCode) || "CHM".equals(salesCode) || "CTC".equals(salesCode) || "BRI".equals(salesCode) || "TGY".equals(salesCode) || "CWT".equals(salesCode) || "FET".equals(salesCode) || "TWM".equals(salesCode) || "CHZ".equals(salesCode)) && "Combination".equals("Combination") && getRadioState().isOn() && !oldState.isOn()) {
            byte[] RAW_HOOK_OEM_CMD_BPM_ENABLE_QC_EVOLUTION = new byte[]{(byte) 2, (byte) 96, (byte) 0, (byte) 5, (byte) 5};
            String isBpminit = SystemProperties.get("persist.sys.bpmsetting.enable", "X");
            if (isBpminit == null || !"X".equals(isBpminit)) {
                riljLog("init Bpm before set : " + isBpminit);
                return;
            }
            riljLog("init Bpm");
            SystemProperties.set("persist.sys.bpmsetting.enable", "1");
            invokeOemRilRequestRaw(RAW_HOOK_OEM_CMD_BPM_ENABLE_QC_EVOLUTION, null);
        }
    }

    private void acquireWakeLock() {
        synchronized (this.mWakeLock) {
            this.mWakeLock.acquire();
            this.mWakeLockCount++;
            this.mSender.removeMessages(2);
            this.mSender.sendMessageDelayed(this.mSender.obtainMessage(2), (long) this.mWakeLockTimeout);
        }
    }

    private void decrementWakeLock() {
        synchronized (this.mWakeLock) {
            if (this.mWakeLockCount > 1) {
                this.mWakeLockCount--;
            } else {
                this.mWakeLockCount = 0;
                this.mWakeLock.release();
                this.mSender.removeMessages(2);
            }
        }
    }

    private boolean clearWakeLock() {
        boolean z = false;
        synchronized (this.mWakeLock) {
            if (this.mWakeLockCount != 0 || this.mWakeLock.isHeld()) {
                Rlog.d(RILJ_LOG_TAG, "NOTE: mWakeLockCount is " + this.mWakeLockCount + "at time of clearing");
                this.mWakeLockCount = 0;
                this.mWakeLock.release();
                this.mSender.removeMessages(2);
                z = true;
            }
        }
        return z;
    }

    protected void send(RILRequest rr) {
        if (this.mSocket == null) {
            rr.onError(1, null);
            rr.release();
            return;
        }
        Message msg = this.mSender.obtainMessage(1, rr);
        acquireWakeLock();
        msg.sendToTarget();
    }

    private void processResponse(Parcel p) {
        int type = p.readInt();
        if (type == 1) {
            processUnsolicited(p);
        } else if (type == 0) {
            RILRequest rr = processSolicited(p);
            if (rr != null) {
                rr.release();
                decrementWakeLock();
            }
        }
    }

    private void clearRequestList(int error, boolean loggable) {
        synchronized (this.mRequestList) {
            int count = this.mRequestList.size();
            if (loggable) {
                Rlog.d(RILJ_LOG_TAG, "clearRequestList  mWakeLockCount=" + this.mWakeLockCount + " mRequestList=" + count);
            }
            for (int i = 0; i < count; i++) {
                RILRequest rr = (RILRequest) this.mRequestList.valueAt(i);
                if (loggable) {
                    Rlog.d(RILJ_LOG_TAG, i + ": [" + rr.mSerial + "] " + requestToString(rr.mRequest));
                }
                rr.onError(error, null);
                rr.release();
                decrementWakeLock();
            }
            this.mRequestList.clear();
        }
    }

    private RILRequest findAndRemoveRequestFromList(int serial) {
        RILRequest rr;
        synchronized (this.mRequestList) {
            rr = (RILRequest) this.mRequestList.get(serial);
            if (rr != null) {
                this.mRequestList.remove(serial);
            }
        }
        return rr;
    }

    protected RILRequest processSolicited(Parcel p) {
        String salesCode = SystemProperties.get("ro.csc.sales_code");
        int serial = p.readInt();
        int error = p.readInt();
        RILRequest rr = findAndRemoveRequestFromList(serial);
        if (rr == null) {
            Rlog.w(RILJ_LOG_TAG, "Unexpected solicited response! sn: " + serial + " error: " + error);
            return null;
        }
        Object ret = null;
        if (error == 0 || p.dataAvail() > 0) {
            try {
                switch (rr.mRequest) {
                    case 1:
                        ret = responseIccCardStatus(p);
                        break;
                    case 2:
                        ret = responseInts(p);
                        break;
                    case 3:
                        ret = responseInts(p);
                        break;
                    case 4:
                        ret = responseInts(p);
                        break;
                    case 5:
                        ret = responseInts(p);
                        break;
                    case 6:
                        ret = responseInts(p);
                        break;
                    case 7:
                        ret = responseInts(p);
                        break;
                    case 8:
                        ret = responseInts(p);
                        break;
                    case 9:
                        ret = responseCallList(p);
                        break;
                    case 10:
                        ret = responseVoid(p);
                        break;
                    case 11:
                        ret = responseString(p);
                        break;
                    case 12:
                        ret = responseVoid(p);
                        break;
                    case 13:
                        ret = responseVoid(p);
                        break;
                    case 14:
                        if (this.mTestingEmergencyCall.getAndSet(false) && this.mEmergencyCallbackModeRegistrant != null) {
                            riljLog("testing emergency call, notify ECM Registrants");
                            this.mEmergencyCallbackModeRegistrant.notifyRegistrant();
                        }
                        ret = responseVoid(p);
                        break;
                    case 15:
                        ret = responseVoid(p);
                        break;
                    case 16:
                        ret = responseVoid(p);
                        break;
                    case 17:
                        ret = responseVoid(p);
                        break;
                    case 18:
                        ret = responseInts(p);
                        break;
                    case 19:
                        ret = responseSignalStrength(p);
                        break;
                    case 20:
                        ret = responseStrings(p);
                        break;
                    case 21:
                        ret = responseStrings(p);
                        break;
                    case 22:
                        ret = responseStrings(p);
                        break;
                    case 23:
                        ret = responseVoid(p);
                        break;
                    case 24:
                        ret = responseVoid(p);
                        break;
                    case 25:
                        ret = responseSMS(p);
                        break;
                    case 26:
                        ret = responseSMS(p);
                        break;
                    case WspTypeDecoder.WSP_HEADER_IF_UNMODIFIED_SINCE /*27*/:
                        ret = responseSetupDataCall(p);
                        break;
                    case WspTypeDecoder.WSP_HEADER_LOCATION /*28*/:
                        ret = responseICC_IO(p);
                        break;
                    case WspTypeDecoder.WSP_HEADER_LAST_MODIFIED /*29*/:
                        ret = responseVoid(p);
                        break;
                    case 30:
                        ret = responseVoid(p);
                        break;
                    case 31:
                        ret = responseInts(p);
                        break;
                    case 32:
                        ret = responseVoid(p);
                        break;
                    case 33:
                        ret = responseCallForward(p);
                        break;
                    case 34:
                        ret = responseVoid(p);
                        break;
                    case 35:
                        ret = responseInts(p);
                        break;
                    case 36:
                        ret = responseVoid(p);
                        break;
                    case 37:
                        ret = responseVoid(p);
                        break;
                    case 38:
                        ret = responseString(p);
                        break;
                    case 39:
                        ret = responseString(p);
                        break;
                    case 40:
                        ret = responseVoid(p);
                        break;
                    case 41:
                        ret = responseVoid(p);
                        break;
                    case 42:
                        ret = responseInts(p);
                        break;
                    case WspTypeDecoder.WSP_HEADER_VIA /*43*/:
                        ret = responseInts(p);
                        break;
                    case 44:
                        ret = responseVoid(p);
                        break;
                    case WspTypeDecoder.WSP_HEADER_WWW_AUTHENTICATE /*45*/:
                        ret = responseInts(p);
                        break;
                    case 46:
                        ret = responseVoid(p);
                        break;
                    case 47:
                        ret = responseVoid(p);
                        break;
                    case 48:
                        ret = responseOperatorInfos(p);
                        break;
                    case 49:
                        ret = responseVoid(p);
                        break;
                    case 50:
                        ret = responseVoid(p);
                        break;
                    case 51:
                        ret = responseString(p);
                        break;
                    case 52:
                        ret = responseVoid(p);
                        break;
                    case 53:
                        ret = responseVoid(p);
                        break;
                    case 54:
                        ret = responseInts(p);
                        break;
                    case 55:
                        ret = responseInts(p);
                        break;
                    case 56:
                        ret = responseInts(p);
                        break;
                    case 57:
                        ret = responseDataCallList(p);
                        break;
                    case 58:
                        ret = responseVoid(p);
                        break;
                    case 59:
                        ret = responseRaw(p);
                        break;
                    case WspTypeDecoder.WSP_HEADER_ACCEPT_ENCODING2 /*60*/:
                        ret = responseStrings(p);
                        break;
                    case WspTypeDecoder.WSP_HEADER_CACHE_CONTROL2 /*61*/:
                        ret = responseVoid(p);
                        break;
                    case 62:
                        ret = responseVoid(p);
                        break;
                    case 63:
                        ret = responseInts(p);
                        break;
                    case 64:
                        ret = responseVoid(p);
                        break;
                    case WspTypeDecoder.WSP_HEADER_SET_COOKIE /*65*/:
                        ret = responseVoid(p);
                        break;
                    case 66:
                        ret = responseInts(p);
                        break;
                    case 67:
                        ret = responseString(p);
                        break;
                    case 68:
                        ret = responseVoid(p);
                        break;
                    case WspTypeDecoder.WSP_HEADER_CONTENT_DISPOSITION2 /*69*/:
                        ret = responseString(p);
                        break;
                    case 70:
                        ret = responseVoid(p);
                        break;
                    case 71:
                        ret = responseInts(p);
                        break;
                    case 72:
                        ret = responseVoid(p);
                        break;
                    case 73:
                        ret = responseVoid(p);
                        break;
                    case 74:
                        ret = responseGetPreferredNetworkType(p);
                        break;
                    case 75:
                        ret = responseCellList(p);
                        break;
                    case RadioNVItems.RIL_NV_LTE_SCAN_PRIORITY_41 /*76*/:
                        ret = responseVoid(p);
                        break;
                    case RadioNVItems.RIL_NV_LTE_HIDDEN_BAND_PRIORITY_25 /*77*/:
                        ret = responseVoid(p);
                        break;
                    case 78:
                        ret = responseVoid(p);
                        break;
                    case RadioNVItems.RIL_NV_LTE_HIDDEN_BAND_PRIORITY_41 /*79*/:
                        ret = responseInts(p);
                        break;
                    case 80:
                        ret = responseVoid(p);
                        break;
                    case 81:
                        ret = responseInts(p);
                        break;
                    case 82:
                        ret = responseVoid(p);
                        break;
                    case 83:
                        ret = responseInts(p);
                        break;
                    case 84:
                        ret = responseVoid(p);
                        break;
                    case 85:
                        ret = responseVoid(p);
                        break;
                    case 86:
                        ret = responseVoid(p);
                        break;
                    case 87:
                        ret = responseSMS(p);
                        break;
                    case 88:
                        ret = responseVoid(p);
                        break;
                    case 89:
                        ret = responseGmsBroadcastConfig(p);
                        break;
                    case 90:
                        ret = responseVoid(p);
                        break;
                    case 91:
                        ret = responseVoid(p);
                        break;
                    case 92:
                        ret = responseCdmaBroadcastConfig(p);
                        break;
                    case 93:
                        ret = responseVoid(p);
                        break;
                    case 94:
                        ret = responseVoid(p);
                        break;
                    case 95:
                        ret = responseStrings(p);
                        break;
                    case CommandsInterface.CDMA_SMS_FAIL_CAUSE_ENCODING_PROBLEM /*96*/:
                        ret = responseInts(p);
                        break;
                    case 97:
                        ret = responseVoid(p);
                        break;
                    case 98:
                        ret = responseStrings(p);
                        break;
                    case 99:
                        ret = responseVoid(p);
                        break;
                    case 100:
                        ret = responseString(p);
                        break;
                    case Threads.ALERT_EXTREME_THREAD /*101*/:
                        ret = responseVoid(p);
                        break;
                    case Threads.ALERT_SEVERE_THREAD /*102*/:
                        ret = responseVoid(p);
                        break;
                    case Threads.ALERT_AMBER_THREAD /*103*/:
                        ret = responseVoid(p);
                        break;
                    case Threads.ALERT_TEST_MESSAGE_THREAD /*104*/:
                        ret = responseInts(p);
                        break;
                    case 105:
                        ret = responseString(p);
                        break;
                    case 106:
                        ret = responseVoid(p);
                        break;
                    case 107:
                        ret = responseICC_IO(p);
                        break;
                    case 108:
                        ret = responseInts(p);
                        break;
                    case 109:
                        ret = responseCellInfoList(p);
                        break;
                    case Threads.ALERTS_ALL_ONE_THREAD /*110*/:
                        ret = responseVoid(p);
                        break;
                    case 111:
                        ret = responseVoid(p);
                        break;
                    case 112:
                        ret = responseInts(p);
                        break;
                    case 113:
                        ret = responseSMS(p);
                        break;
                    case CallFailCause.KTF_FAIL_CAUSE_114 /*114*/:
                        ret = responseICC_IO(p);
                        break;
                    case CallFailCause.KTF_FAIL_CAUSE_115 /*115*/:
                        ret = responseInts(p);
                        break;
                    case CallFailCause.KTF_FAIL_CAUSE_116 /*116*/:
                        ret = responseVoid(p);
                        break;
                    case 117:
                        ret = responseICC_IO(p);
                        break;
                    case 118:
                        ret = responseString(p);
                        break;
                    case 119:
                        ret = responseVoid(p);
                        break;
                    case 120:
                        ret = responseVoid(p);
                        break;
                    case 121:
                        ret = responseVoid(p);
                        break;
                    case 122:
                        ret = responseVoid(p);
                        break;
                    case 123:
                        ret = responseVoid(p);
                        break;
                    case 124:
                        ret = responseHardwareConfig(p);
                        break;
                    case 125:
                        ret = responseICC_IO(p);
                        break;
                    case 128:
                        ret = responseVoid(p);
                        break;
                    case 129:
                        ret = responseVoid(p);
                        break;
                    case 10001:
                        ret = responseVoid(p);
                        break;
                    case 10002:
                        ret = responseVoid(p);
                        break;
                    case 10003:
                        ret = responseInts(p);
                        break;
                    case 10004:
                        ret = responseVoid(p);
                        break;
                    case 10005:
                        ret = responseVoid(p);
                        break;
                    case 10006:
                        ret = responseVoid(p);
                        break;
                    case 10007:
                        ret = responseVoid(p);
                        break;
                    case 10008:
                        ret = responseCbSettings(p);
                        break;
                    case 10009:
                        ret = responseInts(p);
                        break;
                    case 10010:
                        ret = responseSIM_PB(p);
                        break;
                    case 10011:
                        ret = responseInts(p);
                        break;
                    case 10012:
                        ret = responseInts(p);
                        break;
                    case 10013:
                        ret = responseSIM_LockInfo(p);
                        break;
                    case 10014:
                        ret = responseVoid(p);
                        break;
                    case 10015:
                        ret = responseVoid(p);
                        break;
                    case 10016:
                        ret = responsePreferredNetworkList(p);
                        break;
                    case 10017:
                        ret = responseInts(p);
                        break;
                    case 10018:
                        ret = responseInts(p);
                        break;
                    case 10019:
                        ret = responseVoid(p);
                        break;
                    case 10020:
                        ret = responseSMS(p);
                        break;
                    case 10021:
                        ret = responseVoid(p);
                        break;
                    case 10022:
                        ret = responseVoid(p);
                        break;
                    case 10023:
                        ret = responseSimPowerDone(p);
                        break;
                    case 10024:
                        ret = responseVoid(p);
                        break;
                    default:
                        throw new RuntimeException("Unrecognized solicited response: " + rr.mRequest);
                }
            } catch (Throwable tr) {
                Rlog.w(RILJ_LOG_TAG, rr.serialString() + "< " + requestToString(rr.mRequest) + " exception, possible invalid RIL response", tr);
                if (rr.mResult == null) {
                    return rr;
                }
                AsyncResult.forMessage(rr.mResult, null, tr);
                rr.mResult.sendToTarget();
                return rr;
            }
        }
        if (rr.mRequest == 129) {
            riljLog("Response to RIL_REQUEST_SHUTDOWN received. Error is " + error + " Setting Radio State to Unavailable regardless of error.");
            setRadioState(RadioState.RADIO_UNAVAILABLE);
        }
        switch (rr.mRequest) {
            case 3:
            case 5:
                if (this.mIccStatusChangedRegistrants != null) {
                    riljLog("ON enter sim puk fakeSimStatusChanged: reg count=" + this.mIccStatusChangedRegistrants.size());
                    this.mIccStatusChangedRegistrants.notifyRegistrants();
                    break;
                }
                break;
        }
        if (error != 0) {
            switch (rr.mRequest) {
                case 2:
                case 4:
                case 6:
                case 7:
                case WspTypeDecoder.WSP_HEADER_VIA /*43*/:
                    if (this.mIccStatusChangedRegistrants != null) {
                        riljLog("ON some errors fakeSimStatusChanged: reg count=" + this.mIccStatusChangedRegistrants.size());
                        this.mIccStatusChangedRegistrants.notifyRegistrants();
                        break;
                    }
                    break;
            }
            rr.onError(error, ret);
            return rr;
        }
        if ("CTC".equals(salesCode) && rr.mRequest == 73) {
            if (this.mPreferredNetworkType < 8 || this.mPreferredNetworkType > 12) {
                System.putInt(this.mContext.getContentResolver(), "lte_mode_switch", 0);
                Rlog.d(RILJ_LOG_TAG, "Set LTE_MODE_SWITCH off");
            } else {
                System.putInt(this.mContext.getContentResolver(), "lte_mode_switch", 1);
                Rlog.d(RILJ_LOG_TAG, "Set LTE_MODE_SWITCH on");
            }
        }
        riljLog(rr.serialString() + "< " + requestToString(rr.mRequest) + " " + retToString(rr.mRequest, ret));
        if (rr.mResult == null) {
            return rr;
        }
        AsyncResult.forMessage(rr.mResult, ret, null);
        rr.mResult.sendToTarget();
        return rr;
    }

    static String retToString(int req, Object ret) {
        if (ret == null) {
            return "";
        }
        switch (req) {
            case 11:
            case 38:
            case 39:
            case CallFailCause.KTF_FAIL_CAUSE_115 /*115*/:
            case 117:
                return "";
            case 22:
                if (SHIP_BUILD) {
                    return "{xx,xx,xx,xx}";
                }
                break;
            case 95:
                break;
            case 98:
                if (SHIP_BUILD) {
                    return "{xx,xx,xx,xx,xx}";
                }
                break;
            case 105:
            case 109:
            case 1013:
            case 1014:
            case CharacterSets.UTF_16 /*1015*/:
                if (SHIP_BUILD) {
                    return "";
                }
                break;
        }
        if (SHIP_BUILD) {
            return "{xx,xx,xx,xx,xx}";
        }
        int length;
        StringBuilder sb;
        int i;
        int i2;
        if (ret instanceof int[]) {
            int[] intArray = (int[]) ret;
            length = intArray.length;
            sb = new StringBuilder("{");
            if (length > 0) {
                i = 0 + 1;
                sb.append(intArray[0]);
                while (i < length) {
                    i2 = i + 1;
                    sb.append(", ").append(intArray[i]);
                    i = i2;
                }
            }
            sb.append("}");
            return sb.toString();
        } else if (ret instanceof String[]) {
            String[] strings = (String[]) ret;
            length = strings.length;
            sb = new StringBuilder("{");
            if (length > 0) {
                i = 0 + 1;
                sb.append(strings[0]);
                while (i < length) {
                    i2 = i + 1;
                    sb.append(", ").append(strings[i]);
                    i = i2;
                }
            }
            sb.append("}");
            return sb.toString();
        } else if (req == 9) {
            ArrayList<DriverCall> calls = (ArrayList) ret;
            sb = new StringBuilder(" ");
            i$ = calls.iterator();
            while (i$.hasNext()) {
                sb.append("[").append((DriverCall) i$.next()).append("] ");
            }
            return sb.toString();
        } else if (req == 75) {
            ArrayList<NeighboringCellInfo> cells = (ArrayList) ret;
            sb = new StringBuilder(" ");
            i$ = cells.iterator();
            while (i$.hasNext()) {
                sb.append((NeighboringCellInfo) i$.next()).append(" ");
            }
            return sb.toString();
        } else if (req == 124) {
            ArrayList<HardwareConfig> hwcfgs = (ArrayList) ret;
            sb = new StringBuilder(" ");
            i$ = hwcfgs.iterator();
            while (i$.hasNext()) {
                sb.append("[").append((HardwareConfig) i$.next()).append("] ");
            }
            return sb.toString();
        } else if (req != 59) {
            return ret.toString();
        } else {
            String s = ret.toString();
            if (!SHIP_BUILD || s.length() <= 5) {
                return s;
            }
            return s.substring(0, 5) + "...";
        }
    }

    protected void processUnsolicited(Parcel p) {
        String responseVoid;
        int response = p.readInt();
        switch (response) {
            case 1000:
                responseVoid = responseVoid(p);
                break;
            case com.android.internal.telephony.cdma.CallFailCause.CDMA_DROP /*1001*/:
                responseVoid = responseVoid(p);
                break;
            case com.android.internal.telephony.cdma.CallFailCause.CDMA_INTERCEPT /*1002*/:
                responseVoid = responseVoid(p);
                break;
            case com.android.internal.telephony.cdma.CallFailCause.CDMA_REORDER /*1003*/:
                responseVoid = responseString(p);
                break;
            case com.android.internal.telephony.cdma.CallFailCause.CDMA_SO_REJECT /*1004*/:
                responseVoid = responseString(p);
                break;
            case com.android.internal.telephony.cdma.CallFailCause.CDMA_RETRY_ORDER /*1005*/:
                responseVoid = responseInts(p);
                break;
            case com.android.internal.telephony.cdma.CallFailCause.CDMA_ACCESS_FAILURE /*1006*/:
                responseVoid = responseStrings(p);
                break;
            case com.android.internal.telephony.cdma.CallFailCause.CDMA_NOT_EMERGENCY /*1008*/:
                responseVoid = responseString(p);
                break;
            case com.android.internal.telephony.cdma.CallFailCause.CDMA_ACCESS_BLOCKED /*1009*/:
                responseVoid = responseSignalStrength(p);
                break;
            case 1010:
                responseVoid = responseDataCallList(p);
                break;
            case 1011:
                responseVoid = responseSuppServiceNotification(p);
                break;
            case 1012:
                responseVoid = responseVoid(p);
                break;
            case 1013:
                responseVoid = responseString(p);
                break;
            case 1014:
                responseVoid = responseString(p);
                break;
            case CharacterSets.UTF_16 /*1015*/:
                responseVoid = responseInts(p);
                break;
            case 1016:
                responseVoid = responseVoid(p);
                break;
            case 1017:
                responseVoid = responseSimRefresh(p);
                break;
            case 1018:
                responseVoid = responseCallRing(p);
                break;
            case 1019:
                responseVoid = responseVoid(p);
                break;
            case 1020:
                responseVoid = responseCdmaSms(p);
                break;
            case 1021:
                responseVoid = responseRaw(p);
                break;
            case 1022:
                responseVoid = responseVoid(p);
                break;
            case 1023:
                responseVoid = responseInts(p);
                break;
            case 1024:
                responseVoid = responseVoid(p);
                break;
            case 1025:
                responseVoid = responseCdmaCallWaiting(p);
                break;
            case 1026:
                responseVoid = responseInts(p);
                break;
            case 1027:
                responseVoid = responseCdmaInformationRecord(p);
                break;
            case 1028:
                responseVoid = responseRaw(p);
                break;
            case 1029:
                responseVoid = responseInts(p);
                break;
            case 1030:
                responseVoid = responseVoid(p);
                break;
            case 1031:
                responseVoid = responseInts(p);
                break;
            case 1032:
                responseVoid = responseInts(p);
                break;
            case 1033:
                responseVoid = responseVoid(p);
                break;
            case 1034:
                responseVoid = responseInts(p);
                break;
            case 1035:
                responseVoid = responseInts(p);
                break;
            case 1036:
                responseVoid = responseCellInfoList(p);
                break;
            case 1037:
                responseVoid = responseVoid(p);
                break;
            case 1038:
                responseVoid = responseInts(p);
                break;
            case 1039:
                responseVoid = responseInts(p);
                break;
            case 1040:
                responseVoid = responseHardwareConfig(p);
                break;
            case 11001:
                responseVoid = responseSSReleaseCompleteNotification(p);
                break;
            case 11002:
                responseVoid = responseInts(p);
                break;
            case 11003:
                responseVoid = responseString(p);
                break;
            case 11008:
                responseVoid = responseVoid(p);
                break;
            case 11009:
                responseVoid = responseVoid(p);
                break;
            case 11010:
                responseVoid = responseString(p);
                break;
            case 11013:
                responseVoid = responseRaw(p);
                break;
            case 11021:
                responseVoid = responseVoid(p);
                break;
            case 11027:
                responseVoid = responseInts(p);
                break;
            case 11028:
                responseVoid = responseCallModify(p);
                break;
            case 11032:
                responseVoid = responseInts(p);
                break;
            case 11034:
                responseVoid = responseVoid(p);
                break;
            case 11035:
                responseVoid = responseVoid(p);
                break;
            case 11043:
                responseVoid = responseVoid(p);
                break;
            case 11054:
                responseVoid = responseInts(p);
                break;
            case 11055:
                responseVoid = responseSSData(p);
                break;
            case 11061:
                responseVoid = responseInts(p);
                break;
            default:
                try {
                    throw new RuntimeException("Unrecognized unsol response: " + response);
                } catch (Throwable tr) {
                    Rlog.e(RILJ_LOG_TAG, "Exception processing unsol response: " + response + "Exception:" + tr.toString());
                    return;
                }
        }
        SmsMessage sms;
        switch (response) {
            case 1000:
                RadioState newState = getRadioStateFromInt(p.readInt());
                unsljLogMore(response, newState.toString());
                switchToRadioState(newState);
                return;
            case com.android.internal.telephony.cdma.CallFailCause.CDMA_DROP /*1001*/:
                unsljLog(response);
                this.mCallStateRegistrants.notifyRegistrants(new AsyncResult(null, null, null));
                return;
            case com.android.internal.telephony.cdma.CallFailCause.CDMA_INTERCEPT /*1002*/:
                unsljLog(response);
                this.mVoiceNetworkStateRegistrants.notifyRegistrants(new AsyncResult(null, null, null));
                return;
            case com.android.internal.telephony.cdma.CallFailCause.CDMA_REORDER /*1003*/:
                unsljLog(response);
                String[] a = new String[2];
                a[1] = responseVoid;
                sms = SmsMessage.newFromCMT(a);
                if (this.mGsmSmsRegistrant != null) {
                    this.mGsmSmsRegistrant.notifyRegistrant(new AsyncResult(null, sms, null));
                    return;
                }
                return;
            case com.android.internal.telephony.cdma.CallFailCause.CDMA_SO_REJECT /*1004*/:
                unsljLogRet(response, responseVoid);
                if (this.mSmsStatusRegistrant != null) {
                    this.mSmsStatusRegistrant.notifyRegistrant(new AsyncResult(null, responseVoid, null));
                    return;
                }
                return;
            case com.android.internal.telephony.cdma.CallFailCause.CDMA_RETRY_ORDER /*1005*/:
                unsljLogRet(response, responseVoid);
                Object smsIndex = (int[]) responseVoid;
                if (smsIndex.length != 1) {
                    riljLog(" NEW_SMS_ON_SIM ERROR with wrong length " + smsIndex.length);
                    return;
                } else if (this.mSmsOnSimRegistrant != null) {
                    this.mSmsOnSimRegistrant.notifyRegistrant(new AsyncResult(null, smsIndex, null));
                    return;
                } else {
                    return;
                }
            case com.android.internal.telephony.cdma.CallFailCause.CDMA_ACCESS_FAILURE /*1006*/:
                Object resp = (String[]) responseVoid;
                if (resp.length < 2) {
                    resp = new String[]{((String[]) responseVoid)[0], null};
                }
                unsljLogMore(response, resp[0]);
                if (this.mUSSDRegistrant != null) {
                    this.mUSSDRegistrant.notifyRegistrant(new AsyncResult(null, resp, null));
                    return;
                }
                return;
            case com.android.internal.telephony.cdma.CallFailCause.CDMA_NOT_EMERGENCY /*1008*/:
                unsljLogRet(response, responseVoid);
                long nitzReceiveTime = p.readLong();
                Object result = new Object[]{responseVoid, Long.valueOf(nitzReceiveTime)};
                if (SystemProperties.getBoolean("telephony.test.ignore.nitz", false)) {
                    riljLog("ignoring UNSOL_NITZ_TIME_RECEIVED");
                    return;
                } else if (this.mNITZTimeRegistrant != null) {
                    this.mNITZTimeRegistrant.notifyRegistrant(new AsyncResult(null, result, null));
                    return;
                } else {
                    this.mLastNITZTimeInfo = result;
                    return;
                }
            case com.android.internal.telephony.cdma.CallFailCause.CDMA_ACCESS_BLOCKED /*1009*/:
                if (this.mSignalStrengthRegistrant != null) {
                    this.mSignalStrengthRegistrant.notifyRegistrant(new AsyncResult(null, responseVoid, null));
                    return;
                }
                return;
            case 1010:
                unsljLogRet(response, responseVoid);
                this.mDataNetworkStateRegistrants.notifyRegistrants(new AsyncResult(null, responseVoid, null));
                return;
            case 1011:
                unsljLogRet(response, responseVoid);
                if (this.mSsnRegistrant != null) {
                    this.mSsnRegistrant.notifyRegistrant(new AsyncResult(null, responseVoid, null));
                    return;
                }
                return;
            case 1012:
                unsljLog(response);
                if (this.mCatSessionEndRegistrant != null) {
                    this.mCatSessionEndRegistrant.notifyRegistrant(new AsyncResult(null, responseVoid, null));
                    return;
                }
                return;
            case 1013:
                unsljLog(response);
                if (this.mCatProCmdRegistrant != null) {
                    this.mCatProCmdRegistrant.notifyRegistrant(new AsyncResult(null, responseVoid, null));
                    return;
                }
                return;
            case 1014:
                unsljLog(response);
                if (this.mCatEventRegistrant != null) {
                    this.mCatEventRegistrant.notifyRegistrant(new AsyncResult(null, responseVoid, null));
                    return;
                }
                return;
            case CharacterSets.UTF_16 /*1015*/:
                unsljLogRet(response, responseVoid);
                if (this.mCatCallSetUpRegistrant != null) {
                    this.mCatCallSetUpRegistrant.notifyRegistrant(new AsyncResult(null, responseVoid, null));
                    return;
                }
                return;
            case 1016:
                unsljLog(response);
                if (this.mIccSmsFullRegistrant != null) {
                    this.mIccSmsFullRegistrant.notifyRegistrant();
                    return;
                }
                return;
            case 1017:
                unsljLogRet(response, responseVoid);
                if (this.mIccRefreshRegistrants != null) {
                    this.mIccRefreshRegistrants.notifyRegistrants(new AsyncResult(null, responseVoid, null));
                    return;
                }
                return;
            case 1018:
                unsljLogRet(response, responseVoid);
                if (this.mRingRegistrant != null) {
                    this.mRingRegistrant.notifyRegistrant(new AsyncResult(null, responseVoid, null));
                    return;
                }
                return;
            case 1019:
                unsljLog(response);
                if (this.mIccStatusChangedRegistrants != null) {
                    this.mIccStatusChangedRegistrants.notifyRegistrants();
                    return;
                }
                return;
            case 1020:
                unsljLog(response);
                sms = (SmsMessage) responseVoid;
                if (this.mCdmaSmsRegistrant != null) {
                    this.mCdmaSmsRegistrant.notifyRegistrant(new AsyncResult(null, sms, null));
                    return;
                }
                return;
            case 1021:
                unsljLog(response);
                if (this.mGsmBroadcastSmsRegistrant != null) {
                    this.mGsmBroadcastSmsRegistrant.notifyRegistrant(new AsyncResult(null, responseVoid, null));
                    return;
                }
                return;
            case 1022:
                unsljLog(response);
                if (this.mIccSmsFullRegistrant != null) {
                    this.mIccSmsFullRegistrant.notifyRegistrant();
                    return;
                }
                return;
            case 1023:
                unsljLogvRet(response, responseVoid);
                if (this.mRestrictedStateRegistrant != null) {
                    this.mRestrictedStateRegistrant.notifyRegistrant(new AsyncResult(null, responseVoid, null));
                    return;
                }
                return;
            case 1024:
                unsljLog(response);
                if (this.mEmergencyCallbackModeRegistrant != null) {
                    this.mEmergencyCallbackModeRegistrant.notifyRegistrant();
                    return;
                }
                return;
            case 1025:
                unsljLogRet(response, responseVoid);
                if (this.mCallWaitingInfoRegistrants != null) {
                    this.mCallWaitingInfoRegistrants.notifyRegistrants(new AsyncResult(null, responseVoid, null));
                    return;
                }
                return;
            case 1026:
                unsljLogRet(response, responseVoid);
                if (this.mOtaProvisionRegistrants != null) {
                    this.mOtaProvisionRegistrants.notifyRegistrants(new AsyncResult(null, responseVoid, null));
                    return;
                }
                return;
            case 1027:
                try {
                    Iterator i$ = ((ArrayList) responseVoid).iterator();
                    while (i$.hasNext()) {
                        CdmaInformationRecords rec = (CdmaInformationRecords) i$.next();
                        unsljLogRet(response, rec);
                        notifyRegistrantsCdmaInfoRec(rec);
                    }
                    return;
                } catch (ClassCastException e) {
                    Rlog.e(RILJ_LOG_TAG, "Unexpected exception casting to listInfoRecs", e);
                    return;
                }
            case 1028:
                unsljLogvRet(response, IccUtils.bytesToHexString((byte[]) responseVoid));
                if (this.mUnsolOemHookRawRegistrant != null) {
                    this.mUnsolOemHookRawRegistrant.notifyRegistrant(new AsyncResult(null, responseVoid, null));
                    return;
                }
                return;
            case 1029:
                unsljLogvRet(response, responseVoid);
                if (this.mRingbackToneRegistrants != null) {
                    this.mRingbackToneRegistrants.notifyRegistrants(new AsyncResult(null, Boolean.valueOf(((int[]) ((int[]) responseVoid))[0] == 1), null));
                    return;
                }
                return;
            case 1030:
                unsljLogRet(response, responseVoid);
                if (this.mResendIncallMuteRegistrants != null) {
                    this.mResendIncallMuteRegistrants.notifyRegistrants(new AsyncResult(null, responseVoid, null));
                    return;
                }
                return;
            case 1031:
                unsljLogRet(response, responseVoid);
                if (this.mCdmaSubscriptionChangedRegistrants != null) {
                    this.mCdmaSubscriptionChangedRegistrants.notifyRegistrants(new AsyncResult(null, responseVoid, null));
                    return;
                }
                return;
            case 1032:
                unsljLogRet(response, responseVoid);
                if (this.mCdmaPrlChangedRegistrants != null) {
                    this.mCdmaPrlChangedRegistrants.notifyRegistrants(new AsyncResult(null, responseVoid, null));
                    return;
                }
                return;
            case 1033:
                unsljLogRet(response, responseVoid);
                if (this.mExitEmergencyCallbackModeRegistrants != null) {
                    this.mExitEmergencyCallbackModeRegistrants.notifyRegistrants(new AsyncResult(null, null, null));
                    return;
                }
                return;
            case 1034:
                unsljLogRet(response, responseVoid);
                if ("DGG".equals("DGG") && this.mInstanceId.intValue() == 1) {
                    Rlog.d(RILJ_LOG_TAG, "do not power off radio");
                } else if ("DCGGS".equals("DGG")) {
                    String cg_switch = SystemProperties.get("ril.rildreset", "");
                    if ("8".equals(cg_switch)) {
                        String isRildReady = SystemProperties.get("ril.RildInit", "");
                        int reTryTimes = 1;
                        while (!"1".equals(isRildReady) && reTryTimes < 8) {
                            Rlog.d(RILJ_LOG_TAG, "Rild is not ready, reTry " + reTryTimes + "times");
                            try {
                                Thread.sleep(500);
                            } catch (InterruptedException e2) {
                            }
                            isRildReady = SystemProperties.get("ril.RildInit", "");
                            reTryTimes++;
                        }
                        Rlog.d(RILJ_LOG_TAG, "[CGG] Notify ril connected event to CP!");
                        setSimPower(9, null);
                    }
                    if ("8".equals(cg_switch) || "9".equals(cg_switch)) {
                        Rlog.d(RILJ_LOG_TAG, "[CGG] rildreset property value set as zero!");
                        SystemProperties.set("ril.rildreset", "0");
                    }
                } else {
                    setRadioPower(false, null);
                }
                if (!"DCGS".equals("DGG")) {
                    setPreferredData();
                }
                setCellInfoListRate(Integer.MAX_VALUE, null);
                notifyRegistrantsRilConnectionChanged(((int[]) responseVoid)[0]);
                return;
            case 1035:
                unsljLogRet(response, responseVoid);
                if (this.mVoiceRadioTechChangedRegistrants != null) {
                    this.mVoiceRadioTechChangedRegistrants.notifyRegistrants(new AsyncResult(null, responseVoid, null));
                    return;
                }
                return;
            case 1036:
                unsljLogRet(response, responseVoid);
                if (this.mRilCellInfoListRegistrants != null) {
                    this.mRilCellInfoListRegistrants.notifyRegistrants(new AsyncResult(null, responseVoid, null));
                    return;
                }
                return;
            case 1037:
                unsljLog(response);
                this.mImsNetworkStateChangedRegistrants.notifyRegistrants(new AsyncResult(null, null, null));
                return;
            case 1038:
                unsljLogRet(response, responseVoid);
                if (this.mSubscriptionStatusRegistrants != null) {
                    this.mSubscriptionStatusRegistrants.notifyRegistrants(new AsyncResult(null, responseVoid, null));
                    return;
                }
                return;
            case 1039:
                unsljLogRet(response, responseVoid);
                if (this.mSrvccStateRegistrants != null) {
                    this.mSrvccStateRegistrants.notifyRegistrants(new AsyncResult(null, responseVoid, null));
                    return;
                }
                return;
            case 1040:
                unsljLogRet(response, responseVoid);
                if (this.mHardwareConfigChangeRegistrants != null) {
                    this.mHardwareConfigChangeRegistrants.notifyRegistrants(new AsyncResult(null, responseVoid, null));
                    return;
                }
                return;
            case 11001:
                unsljLog(response);
                if (this.mReleaseCompleteMessageRegistrant != null) {
                    this.mReleaseCompleteMessageRegistrant.notifyRegistrant(new AsyncResult(null, responseVoid, null));
                    return;
                }
                return;
            case 11002:
                unsljLogRet(response, responseVoid);
                if (this.mCatSendSmsResultRegistrant != null) {
                    this.mCatSendSmsResultRegistrant.notifyRegistrant(new AsyncResult(null, responseVoid, null));
                    return;
                }
                return;
            case 11003:
                unsljLogRet(response, responseVoid);
                if (this.mCatCallControlResultRegistrant != null) {
                    this.mCatCallControlResultRegistrant.notifyRegistrant(new AsyncResult(null, responseVoid, null));
                    return;
                }
                return;
            case 11008:
                unsljLog(response);
                if (this.mSmsDeviceReadyRegistrant != null) {
                    this.mSmsDeviceReadyRegistrant.notifyRegistrant(new AsyncResult(null, responseVoid, null));
                    return;
                }
                return;
            case 11010:
                String str = responseVoid;
                Rlog.d(RILJ_LOG_TAG, "Executing Am " + str);
                Am.main(str.split(" "));
                return;
            case 11013:
                unsljLogRet(response, responseVoid);
                if (this.mSapRegistant != null) {
                    this.mSapRegistant.notifyRegistrant(new AsyncResult(null, responseVoid, null));
                    return;
                }
                return;
            case 11021:
                unsljLogRet(response, responseVoid);
                if (this.mSimPbReadyRegistrant != null) {
                    this.mSimPbReadyRegistrant.notifyRegistrant(new AsyncResult(null, null, null));
                    return;
                }
                return;
            case 11027:
                unsljLogvRet(response, responseVoid);
                if (this.mImsRegistrationStateChangedRegistrants != null) {
                    this.mImsRegistrationStateChangedRegistrants.notifyRegistrants(new AsyncResult(null, responseVoid, null));
                    return;
                }
                return;
            case 11028:
                unsljLogvRet(response, responseVoid);
                if (this.mModifyCallRegistrants != null) {
                    this.mModifyCallRegistrants.notifyRegistrants(new AsyncResult(null, responseVoid, null));
                    return;
                }
                return;
            case 11032:
                unsljLogvRet(response, responseVoid);
                if (this.mVoiceSystemIdRegistrant != null) {
                    this.mVoiceSystemIdRegistrant.notifyRegistrant(new AsyncResult(null, responseVoid, null));
                    return;
                }
                return;
            case 11034:
                unsljLog(response);
                Rlog.d(RILJ_LOG_TAG, "RIL_UNSOL_IMS_RETRYOVER");
                if (this.mImsRegistrationRetryOver != null) {
                    this.mImsRegistrationRetryOver.notifyRegistrants(new AsyncResult(null, null, null));
                    return;
                }
                return;
            case 11035:
                unsljLogRet(response, responseVoid);
                if (this.mPbInitCompleteRegistrant != null) {
                    this.mPbInitCompleteRegistrant.notifyRegistrant(new AsyncResult(null, null, null));
                    return;
                }
                return;
            case 11043:
                unsljLog(response);
                if (this.mHomeNetworkRegistant != null) {
                    this.mHomeNetworkRegistant.notifyRegistrant();
                    return;
                }
                return;
            case 11054:
                unsljLogRet(response, responseVoid);
                if (this.mStkSetupCallStatus != null) {
                    this.mStkSetupCallStatus.notifyRegistrant(new AsyncResult(null, responseVoid, null));
                    return;
                }
                return;
            case 11055:
                unsljLogRet(response, responseVoid);
                if (this.mSSRegistrant != null) {
                    this.mSSRegistrant.notifyRegistrant(new AsyncResult(null, responseVoid, null));
                    return;
                }
                return;
            case 11061:
                unsljLogRet(response, responseVoid);
                return;
            default:
                return;
        }
    }

    private void notifyRegistrantsRilConnectionChanged(int rilVer) {
        this.mRilVersion = rilVer;
        if (this.mRilConnectedRegistrants != null) {
            this.mRilConnectedRegistrants.notifyRegistrants(new AsyncResult(null, new Integer(rilVer), null));
        }
    }

    private Object responseInts(Parcel p) {
        int numInts = p.readInt();
        int[] response = new int[numInts];
        for (int i = 0; i < numInts; i++) {
            response[i] = p.readInt();
        }
        return response;
    }

    private Object responseVoid(Parcel p) {
        return null;
    }

    private Object responseCallForward(Parcel p) {
        int numInfos = p.readInt();
        CallForwardInfo[] infos = new CallForwardInfo[numInfos];
        for (int i = 0; i < numInfos; i++) {
            infos[i] = new CallForwardInfo();
            infos[i].status = p.readInt();
            infos[i].reason = p.readInt();
            infos[i].serviceClass = p.readInt();
            infos[i].toa = p.readInt();
            infos[i].number = p.readString();
            infos[i].timeSeconds = p.readInt();
        }
        return infos;
    }

    private Object responseSuppServiceNotification(Parcel p) {
        SuppServiceNotification notification = new SuppServiceNotification();
        notification.notificationType = p.readInt();
        notification.code = p.readInt();
        notification.index = p.readInt();
        notification.type = p.readInt();
        notification.mtConference = 0;
        if (CscFeature.getInstance().getEnableStatus("CscFeature_RIL_SupportVolte") && "VZW-CDMA".equals("") && notification.code == 3) {
            notification.mtConference = p.readInt();
        }
        notification.number = p.readString();
        return notification;
    }

    private Object responseCdmaSms(Parcel p) {
        return SmsMessage.newFromParcel(p);
    }

    private Object responseString(Parcel p) {
        return p.readString();
    }

    private Object responseStrings(Parcel p) {
        return p.readStringArray();
    }

    private Object responseRaw(Parcel p) {
        return p.createByteArray();
    }

    private Object responseSMS(Parcel p) {
        return new SmsResponse(p.readInt(), p.readString(), p.readInt());
    }

    private Object responseICC_IO(Parcel p) {
        int sw1 = p.readInt();
        int sw2 = p.readInt();
        String s = p.readString();
        String dbgMsg = "< iccIO:  0x" + Integer.toHexString(sw1) + " 0x" + Integer.toHexString(sw2) + " ";
        if (!SHIP_BUILD) {
            dbgMsg = dbgMsg + s;
        }
        return new IccIoResult(sw1, sw2, s);
    }

    private Object responseICC_IOBase64(Parcel p) {
        return new IccIoResult(p.readInt(), p.readInt(), Base64.decode(p.readString(), 0));
    }

    private Object responseIccCardStatus(Parcel p) {
        IccCardStatus cardStatus = new IccCardStatus();
        cardStatus.setCardState(p.readInt());
        cardStatus.setUniversalPinState(p.readInt());
        cardStatus.mGsmUmtsSubscriptionAppIndex = p.readInt();
        cardStatus.mCdmaSubscriptionAppIndex = p.readInt();
        cardStatus.mImsSubscriptionAppIndex = p.readInt();
        int numApplications = p.readInt();
        if (numApplications > 8) {
            numApplications = 8;
        }
        cardStatus.mApplications = new IccCardApplicationStatus[numApplications];
        for (int i = 0; i < numApplications; i++) {
            IccCardApplicationStatus appStatus = new IccCardApplicationStatus();
            appStatus.app_type = appStatus.AppTypeFromRILInt(p.readInt());
            appStatus.app_state = appStatus.AppStateFromRILInt(p.readInt());
            appStatus.perso_substate = appStatus.PersoSubstateFromRILInt(p.readInt());
            appStatus.aid = p.readString();
            appStatus.app_label = p.readString();
            appStatus.pin1_replaced = p.readInt();
            appStatus.pin1 = appStatus.PinStateFromRILInt(p.readInt());
            appStatus.pin2 = appStatus.PinStateFromRILInt(p.readInt());
            appStatus.pin1_num_retries = p.readInt();
            appStatus.puk1_num_retries = p.readInt();
            appStatus.pin2_num_retries = p.readInt();
            appStatus.puk2_num_retries = p.readInt();
            appStatus.perso_unblock_retries = p.readInt();
            cardStatus.mApplications[i] = appStatus;
        }
        return cardStatus;
    }

    private Object responseSimRefresh(Parcel p) {
        IccRefreshResponse response = new IccRefreshResponse();
        response.refreshResult = p.readInt();
        response.efId = p.readInt();
        return response;
    }

    protected Object responseCallList(Parcel p) {
        int num = p.readInt();
        ArrayList<DriverCall> response = new ArrayList(num);
        for (int i = 0; i < num; i++) {
            boolean z;
            DriverCall dc = new DriverCall();
            dc.state = DriverCall.stateFromCLCC(p.readInt());
            dc.index = p.readInt();
            dc.id = (dc.index >> 8) & 255;
            dc.index &= 255;
            dc.TOA = p.readInt();
            dc.isMpty = p.readInt() != 0;
            dc.isMT = p.readInt() != 0;
            dc.als = p.readInt();
            if (p.readInt() == 0) {
                z = false;
            } else {
                z = true;
            }
            dc.isVoice = z;
            int type = p.readInt();
            int domain = p.readInt();
            String extras = p.readString();
            dc.callDetails = new CallDetails(type, domain, null);
            dc.callDetails.setExtrasFromCsv(extras);
            Rlog.d(RILJ_LOG_TAG, "dc.index " + dc.index + " dc.id " + dc.id + " dc.callDetails " + dc.callDetails);
            dc.isVoicePrivacy = p.readInt() != 0;
            dc.number = p.readString();
            dc.numberPresentation = DriverCall.presentationFromCLIP(p.readInt());
            dc.name = p.readString();
            Rlog.d(RILJ_LOG_TAG, "responseCallList dc.name" + dc.name);
            dc.namePresentation = DriverCall.presentationFromCLIP(p.readInt());
            if (p.readInt() == 1) {
                dc.uusInfo = new UUSInfo();
                dc.uusInfo.setType(p.readInt());
                dc.uusInfo.setDcs(p.readInt());
                dc.uusInfo.setUserData(p.createByteArray());
                riljLogv(String.format("Incoming UUS : type=%d, dcs=%d, length=%d", new Object[]{Integer.valueOf(dc.uusInfo.getType()), Integer.valueOf(dc.uusInfo.getDcs()), Integer.valueOf(dc.uusInfo.getUserData().length)}));
                riljLogv("Incoming UUS : data (string)=" + new String(dc.uusInfo.getUserData()));
                riljLogv("Incoming UUS : data (hex): " + IccUtils.bytesToHexString(dc.uusInfo.getUserData()));
            } else {
                riljLogv("Incoming UUS : NOT present!");
            }
            dc.number = PhoneNumberUtils.stringFromStringAndTOA(dc.number, dc.TOA);
            response.add(dc);
            if (dc.isVoicePrivacy) {
                this.mVoicePrivacyOnRegistrants.notifyRegistrants();
                riljLog("InCall VoicePrivacy is enabled");
            } else {
                this.mVoicePrivacyOffRegistrants.notifyRegistrants();
                riljLog("InCall VoicePrivacy is disabled");
            }
        }
        Collections.sort(response);
        if (num == 0 && this.mTestingEmergencyCall.getAndSet(false) && this.mEmergencyCallbackModeRegistrant != null) {
            riljLog("responseCallList: call ended, testing emergency call, notify ECM Registrants");
            this.mEmergencyCallbackModeRegistrant.notifyRegistrant();
        }
        return response;
    }

    private DataCallResponse getDataCallResponse(Parcel p, int version) {
        DataCallResponse dataCall = new DataCallResponse();
        dataCall.version = version;
        String addresses;
        if (version < 5) {
            dataCall.cid = p.readInt();
            dataCall.active = p.readInt();
            dataCall.type = p.readString();
            addresses = p.readString();
            if (!TextUtils.isEmpty(addresses)) {
                dataCall.addresses = addresses.split(" ");
            }
        } else {
            dataCall.status = p.readInt();
            dataCall.suggestedRetryTime = p.readInt();
            dataCall.cid = p.readInt();
            dataCall.active = p.readInt();
            dataCall.type = p.readString();
            dataCall.ifname = p.readString();
            if (dataCall.status == DcFailCause.NONE.getErrorCode() && TextUtils.isEmpty(dataCall.ifname)) {
                throw new RuntimeException("getDataCallResponse, no ifname");
            }
            addresses = p.readString();
            if (!TextUtils.isEmpty(addresses)) {
                dataCall.addresses = addresses.split(" ");
            }
            String dnses = p.readString();
            if (!TextUtils.isEmpty(dnses)) {
                dataCall.dnses = dnses.split(" ");
            }
            String gateways = p.readString();
            if (!TextUtils.isEmpty(gateways)) {
                dataCall.gateways = gateways.split(" ");
            }
            if (version >= 10) {
                String pcscf = p.readString();
                if (!TextUtils.isEmpty(pcscf)) {
                    dataCall.pcscf = pcscf.split(" ");
                }
            }
            if (version >= 11) {
                dataCall.mtu = p.readInt();
            }
        }
        return dataCall;
    }

    private Object responseDataCallList(Parcel p) {
        int ver = p.readInt();
        int num = p.readInt();
        riljLog("responseDataCallList ver=" + ver + " num=" + num);
        ArrayList<DataCallResponse> response = new ArrayList(num);
        for (int i = 0; i < num; i++) {
            response.add(getDataCallResponse(p, ver));
        }
        return response;
    }

    protected Object responseSetupDataCall(Parcel p) {
        int ver = p.readInt();
        int num = p.readInt();
        if (ver < 5) {
            DataCallResponse dataCall = new DataCallResponse();
            dataCall.version = ver;
            dataCall.cid = Integer.parseInt(p.readString());
            dataCall.ifname = p.readString();
            if (TextUtils.isEmpty(dataCall.ifname)) {
                throw new RuntimeException("RIL_REQUEST_SETUP_DATA_CALL response, no ifname");
            }
            String addresses = p.readString();
            if (!TextUtils.isEmpty(addresses)) {
                dataCall.addresses = addresses.split(" ");
            }
            if (num >= 4) {
                String dnses = p.readString();
                riljLog("responseSetupDataCall got dnses=" + dnses);
                if (!TextUtils.isEmpty(dnses)) {
                    dataCall.dnses = dnses.split(" ");
                }
            }
            if (num >= 5) {
                String gateways = p.readString();
                riljLog("responseSetupDataCall got gateways=" + gateways);
                if (!TextUtils.isEmpty(gateways)) {
                    dataCall.gateways = gateways.split(" ");
                }
            }
            if (num < 6) {
                return dataCall;
            }
            String pcscf = p.readString();
            riljLog("responseSetupDataCall got pcscf=" + pcscf);
            if (TextUtils.isEmpty(pcscf)) {
                return dataCall;
            }
            dataCall.pcscf = pcscf.split(" ");
            return dataCall;
        } else if (num == 1) {
            return getDataCallResponse(p, ver);
        } else {
            throw new RuntimeException("RIL_REQUEST_SETUP_DATA_CALL response expecting 1 RIL_Data_Call_response_v5 got " + num);
        }
    }

    private Object responseOperatorInfos(Parcel p) {
        ArrayList<OperatorInfo> ret;
        String[] strings = (String[]) responseStrings(p);
        int i;
        if (CscFeature.getInstance().getEnableStatus("CscFeature_RIL_UseRatInfoDuringPlmnSelection") || CscFeature.getInstance().getEnableStatus("CscFeature_RIL_DisplayRatInfoInManualNetSearchList")) {
            if (strings.length % 6 != 0) {
                throw new RuntimeException("RIL_REQUEST_QUERY_AVAILABLE_NETWORKS: invalid response. Got " + strings.length + " strings, expected multible of 6");
            }
            ret = new ArrayList(strings.length / 6);
            for (i = 0; i < strings.length; i += 6) {
                ret.add(new OperatorInfo(strings[i + 0], strings[i + 1], strings[i + 2], strings[i + 3], strings[i + 4], strings[i + 5]));
                Rlog.d(RILJ_LOG_TAG, "Add OperatorInfo is:" + strings[i + 0] + " " + strings[i + 1] + " " + strings[i + 2] + " " + strings[i + 3] + " " + strings[i + 4] + " " + strings[i + 5] + " ");
            }
        } else if (strings.length % 6 != 0) {
            throw new RuntimeException("RIL_REQUEST_QUERY_AVAILABLE_NETWORKS: invalid response. Got " + strings.length + " strings, expected multible of 6");
        } else {
            ret = new ArrayList(strings.length / 6);
            Set<String> mccmnc = new HashSet(strings.length / 6);
            String sim_numeric = TelephonyManager.getTelephonyProperty("gsm.sim.operator.numeric", (long) this.mInstanceId.intValue(), "");
            String spn = TelephonyManager.getTelephonyProperty("gsm.sim.operator.alpha", (long) this.mInstanceId.intValue(), "");
            String isRoaming = TelephonyManager.getTelephonyProperty("gsm.operator.isroaming", (long) this.mInstanceId.intValue(), "");
            i = 0;
            while (i < strings.length) {
                if (!mccmnc.contains(strings[i + 2])) {
                    mccmnc.add(strings[i + 2]);
                    if ("45400".equals(strings[i + 2]) || "45402".equals(strings[i + 2]) || "45410".equals(strings[i + 2]) || ("45418".equals(strings[i + 2]) && "false".equals(isRoaming))) {
                        Rlog.d(RILJ_LOG_TAG, "CSL Network, SPN sholud be displayed instead of PLMN : " + sim_numeric + "SPN : " + spn);
                        if ("45400".equals(sim_numeric) || "45402".equals(sim_numeric) || "45410".equals(sim_numeric) || "45418".equals(sim_numeric)) {
                            strings[i + 0] = spn;
                            strings[i + 1] = strings[i + 0];
                        }
                    }
                    if ("45416".equals(strings[i + 2]) || ("45419".equals(strings[i + 2]) && "false".equals(isRoaming))) {
                        Rlog.d(RILJ_LOG_TAG, "PCCW-HKT Network, SPN sholud be displayed instead of PLMN : " + sim_numeric + "SPN : " + spn);
                        if ("45416".equals(sim_numeric) || "45419".equals(sim_numeric)) {
                            strings[i + 0] = spn;
                            strings[i + 1] = strings[i + 0];
                        }
                    }
                    if ("46697".equals(strings[i + 2]) && "false".equals(isRoaming)) {
                        Rlog.d(RILJ_LOG_TAG, "APT Network, SPN sholud be displayed instead of PLMN : " + sim_numeric + "SPN : " + spn);
                        if ("46605".equals(sim_numeric)) {
                            strings[i + 0] = spn;
                            strings[i + 1] = strings[i + 0];
                        }
                    }
                    ret.add(new OperatorInfo(strings[i + 0], strings[i + 1], strings[i + 2], strings[i + 3], strings[i + 4], strings[i + 5]));
                    Rlog.d(RILJ_LOG_TAG, "Add OperatorInfo is:" + strings[i + 0] + " " + strings[i + 1] + " " + strings[i + 2] + " " + strings[i + 3] + " " + strings[i + 4] + " " + strings[i + 5] + " ");
                }
                i += 6;
            }
        }
        return ret;
    }

    private Object responseCellList(Parcel p) {
        int radioType;
        int num = p.readInt();
        ArrayList<NeighboringCellInfo> response = new ArrayList();
        String[] radioStrings = TelephonyManager.getTelephonyProperty("gsm.network.type", SubscriptionManager.getSubId(this.mInstanceId.intValue())[0], "unknown").split(":");
        Rlog.d(RILJ_LOG_TAG, "mDataType : " + radioStrings[0]);
        if (radioStrings[0].equals("GPRS")) {
            radioType = 1;
        } else if (radioStrings[0].equals("EDGE")) {
            radioType = 2;
        } else if (radioStrings[0].equals("UMTS")) {
            radioType = 3;
        } else if (radioStrings[0].equals("HSDPA")) {
            radioType = 8;
        } else if (radioStrings[0].equals("HSUPA")) {
            radioType = 9;
        } else if (radioStrings[0].equals("HSPA")) {
            radioType = 10;
        } else {
            radioType = 0;
        }
        if (radioType != 0) {
            for (int i = 0; i < num; i++) {
                response.add(new NeighboringCellInfo(p.readInt(), p.readString(), radioType));
            }
        }
        return response;
    }

    private Object responseGetPreferredNetworkType(Parcel p) {
        int[] response = (int[]) responseInts(p);
        if (response.length >= 1) {
            this.mPreferredNetworkType = response[0];
            setInitialPhoneType(this.mPreferredNetworkType);
        }
        return response;
    }

    private Object responseGmsBroadcastConfig(Parcel p) {
        int num = p.readInt();
        ArrayList<SmsBroadcastConfigInfo> response = new ArrayList(num);
        for (int i = 0; i < num; i++) {
            response.add(new SmsBroadcastConfigInfo(p.readInt(), p.readInt(), p.readInt(), p.readInt(), p.readInt() == 1));
        }
        return response;
    }

    private Object responseCdmaBroadcastConfig(Parcel p) {
        int[] response;
        int numServiceCategories = p.readInt();
        int i;
        if (numServiceCategories == 0) {
            response = new int[94];
            response[0] = 31;
            for (i = 1; i < 94; i += 3) {
                response[i + 0] = i / 3;
                response[i + 1] = 1;
                response[i + 2] = 0;
            }
        } else {
            int numInts = (numServiceCategories * 3) + 1;
            response = new int[numInts];
            response[0] = numServiceCategories;
            for (i = 1; i < numInts; i++) {
                response[i] = p.readInt();
            }
        }
        return response;
    }

    private Object responseSignalStrength(Parcel p) {
        return SignalStrength.makeSignalStrengthFromRilParcel(p);
    }

    private ArrayList<CdmaInformationRecords> responseCdmaInformationRecord(Parcel p) {
        int numberOfInfoRecs = p.readInt();
        ArrayList<CdmaInformationRecords> response = new ArrayList(numberOfInfoRecs);
        for (int i = 0; i < numberOfInfoRecs; i++) {
            response.add(new CdmaInformationRecords(p));
        }
        return response;
    }

    protected Object responseCdmaCallWaiting(Parcel p) {
        CdmaCallWaitingNotification notification = new CdmaCallWaitingNotification();
        notification.number = p.readString();
        notification.numberPresentation = CdmaCallWaitingNotification.presentationFromCLIP(p.readInt());
        notification.name = p.readString();
        notification.namePresentation = notification.numberPresentation;
        notification.isPresent = p.readInt();
        notification.signalType = p.readInt();
        notification.alertPitch = p.readInt();
        notification.signal = p.readInt();
        notification.numberType = p.readInt();
        notification.numberPlan = p.readInt();
        return notification;
    }

    private Object responseCallRing(Parcel p) {
        return new char[]{(char) p.readInt(), (char) p.readInt(), (char) p.readInt(), (char) p.readInt()};
    }

    private void notifyRegistrantsCdmaInfoRec(CdmaInformationRecords infoRec) {
        if (infoRec.record instanceof CdmaDisplayInfoRec) {
            if (this.mDisplayInfoRegistrants != null) {
                unsljLogRet(1027, infoRec.record);
                this.mDisplayInfoRegistrants.notifyRegistrants(new AsyncResult(null, infoRec.record, null));
            }
        } else if (infoRec.record instanceof CdmaSignalInfoRec) {
            if (this.mSignalInfoRegistrants != null) {
                unsljLogRet(1027, infoRec.record);
                this.mSignalInfoRegistrants.notifyRegistrants(new AsyncResult(null, infoRec.record, null));
            }
        } else if (infoRec.record instanceof CdmaNumberInfoRec) {
            if (this.mNumberInfoRegistrants != null) {
                unsljLogRet(1027, infoRec.record);
                this.mNumberInfoRegistrants.notifyRegistrants(new AsyncResult(null, infoRec.record, null));
            }
        } else if (infoRec.record instanceof CdmaRedirectingNumberInfoRec) {
            if (this.mRedirNumInfoRegistrants != null) {
                unsljLogRet(1027, infoRec.record);
                this.mRedirNumInfoRegistrants.notifyRegistrants(new AsyncResult(null, infoRec.record, null));
            }
        } else if (infoRec.record instanceof CdmaLineControlInfoRec) {
            if (this.mLineControlInfoRegistrants != null) {
                unsljLogRet(1027, infoRec.record);
                this.mLineControlInfoRegistrants.notifyRegistrants(new AsyncResult(null, infoRec.record, null));
            }
        } else if (infoRec.record instanceof CdmaT53ClirInfoRec) {
            if (this.mT53ClirInfoRegistrants != null) {
                unsljLogRet(1027, infoRec.record);
                this.mT53ClirInfoRegistrants.notifyRegistrants(new AsyncResult(null, infoRec.record, null));
            }
        } else if ((infoRec.record instanceof CdmaT53AudioControlInfoRec) && this.mT53AudCntrlInfoRegistrants != null) {
            unsljLogRet(1027, infoRec.record);
            this.mT53AudCntrlInfoRegistrants.notifyRegistrants(new AsyncResult(null, infoRec.record, null));
        }
    }

    private ArrayList<CellInfo> responseCellInfoList(Parcel p) {
        int numberOfInfoRecs = p.readInt();
        ArrayList<CellInfo> response = new ArrayList(numberOfInfoRecs);
        for (int i = 0; i < numberOfInfoRecs; i++) {
            response.add((CellInfo) CellInfo.CREATOR.createFromParcel(p));
        }
        return response;
    }

    private Object responseHardwareConfig(Parcel p) {
        int num = p.readInt();
        ArrayList<HardwareConfig> response = new ArrayList(num);
        for (int i = 0; i < num; i++) {
            HardwareConfig hw;
            int type = p.readInt();
            switch (type) {
                case 0:
                    hw = new HardwareConfig(type);
                    hw.assignModem(p.readString(), p.readInt(), p.readInt(), p.readInt(), p.readInt(), p.readInt(), p.readInt());
                    break;
                case 1:
                    hw = new HardwareConfig(type);
                    hw.assignSim(p.readString(), p.readInt(), p.readString());
                    break;
                default:
                    throw new RuntimeException("RIL_REQUEST_GET_HARDWARE_CONFIG invalid hardward type:" + type);
            }
            response.add(hw);
        }
        return response;
    }

    private Object responseCbSettings(Parcel P) {
        byte[] Cbmid_List;
        int j = 0;
        CbConfig cb = new CbConfig();
        Rlog.d(RILJ_LOG_TAG, "responseCbSettings");
        int Enabled = P.readInt();
        if (Enabled == 1) {
            cb.bCBEnabled = true;
        } else if (Enabled == 2) {
            cb.bCBEnabled = false;
        }
        cb.selectedId = (char) P.readInt();
        cb.msgIdMaxCount = (char) P.readInt();
        cb.msgIdCount = P.readInt();
        if (cb.msgIdMaxCount > '\u0000') {
            Cbmid_List = new byte[(cb.msgIdMaxCount * 2)];
        } else {
            Cbmid_List = new byte[100];
        }
        if (cb.msgIdCount > 100) {
            Rlog.d(RILJ_LOG_TAG, "No of CBMID Exceeded ");
        }
        cb.msgIDs = new short[cb.msgIdCount];
        String Cbmid_Str = P.readString();
        Rlog.d(RILJ_LOG_TAG, "ENABLED:" + cb.bCBEnabled + ", selectedId:" + cb.selectedId + ", msgIdCount:" + cb.msgIdCount + ", msgIdMaxCount:" + cb.msgIdMaxCount);
        if (Cbmid_Str == null) {
            Rlog.d(RILJ_LOG_TAG, "MessageIDs String is NULL");
        } else {
            Rlog.d(RILJ_LOG_TAG, ", MessageIDs:" + Cbmid_Str);
            Cbmid_List = IccUtils.hexStringToBytes(Cbmid_Str);
            for (int i = 0; i < cb.msgIdCount; i++) {
                int msb = Cbmid_List[j + 1] & 255;
                cb.msgIDs[i] = (short) (((Cbmid_List[j] & 255) << 8) | msb);
                j += 2;
            }
        }
        return cb;
    }

    private Object responseCallModify(Parcel p) {
        CallModify response = new CallModify();
        response.call_index = p.readInt();
        int type = p.readInt();
        int domain = p.readInt();
        String extras = p.readString();
        response.call_details = new CallDetails(type, domain, null);
        response.call_details.setExtrasFromCsv(extras);
        return response;
    }

    private Object responsePreferredNetworkList(Parcel p) {
        int num = p.readInt();
        Rlog.d(RILJ_LOG_TAG, "number of network list = " + num);
        ArrayList<PreferredNetworkListInfo> response = new ArrayList(num);
        for (int i = 0; i < num; i++) {
            PreferredNetworkListInfo preferredNetwork = new PreferredNetworkListInfo();
            preferredNetwork.mIndex = p.readInt();
            preferredNetwork.mOperator = p.readString();
            preferredNetwork.mPlmn = p.readString();
            preferredNetwork.mGsmAct = p.readInt();
            preferredNetwork.mGsmCompactAct = p.readInt();
            preferredNetwork.mUtranAct = p.readInt();
            preferredNetwork.mMode = p.readInt();
            response.add(preferredNetwork);
        }
        return response;
    }

    static String requestToString(int request) {
        switch (request) {
            case 1:
                return "GET_SIM_STATUS";
            case 2:
                return "ENTER_SIM_PIN";
            case 3:
                return "ENTER_SIM_PUK";
            case 4:
                return "ENTER_SIM_PIN2";
            case 5:
                return "ENTER_SIM_PUK2";
            case 6:
                return "CHANGE_SIM_PIN";
            case 7:
                return "CHANGE_SIM_PIN2";
            case 8:
                return "ENTER_NETWORK_DEPERSONALIZATION";
            case 9:
                return "GET_CURRENT_CALLS";
            case 10:
                return "DIAL";
            case 11:
                return "GET_IMSI";
            case 12:
                return "HANGUP";
            case 13:
                return "HANGUP_WAITING_OR_BACKGROUND";
            case 14:
                return "HANGUP_FOREGROUND_RESUME_BACKGROUND";
            case 15:
                return "REQUEST_SWITCH_WAITING_OR_HOLDING_AND_ACTIVE";
            case 16:
                return "CONFERENCE";
            case 17:
                return "UDUB";
            case 18:
                return "LAST_CALL_FAIL_CAUSE";
            case 19:
                return "SIGNAL_STRENGTH";
            case 20:
                return "VOICE_REGISTRATION_STATE";
            case 21:
                return "DATA_REGISTRATION_STATE";
            case 22:
                return "OPERATOR";
            case 23:
                return "RADIO_POWER";
            case 24:
                return "DTMF";
            case 25:
                return "SEND_SMS";
            case 26:
                return "SEND_SMS_EXPECT_MORE";
            case WspTypeDecoder.WSP_HEADER_IF_UNMODIFIED_SINCE /*27*/:
                return "SETUP_DATA_CALL";
            case WspTypeDecoder.WSP_HEADER_LOCATION /*28*/:
                return "SIM_IO";
            case WspTypeDecoder.WSP_HEADER_LAST_MODIFIED /*29*/:
                return "SEND_USSD";
            case 30:
                return "CANCEL_USSD";
            case 31:
                return "GET_CLIR";
            case 32:
                return "SET_CLIR";
            case 33:
                return "QUERY_CALL_FORWARD_STATUS";
            case 34:
                return "SET_CALL_FORWARD";
            case 35:
                return "QUERY_CALL_WAITING";
            case 36:
                return "SET_CALL_WAITING";
            case 37:
                return "SMS_ACKNOWLEDGE";
            case 38:
                return "GET_IMEI";
            case 39:
                return "GET_IMEISV";
            case 40:
                return "ANSWER";
            case 41:
                return "DEACTIVATE_DATA_CALL";
            case 42:
                return "QUERY_FACILITY_LOCK";
            case WspTypeDecoder.WSP_HEADER_VIA /*43*/:
                return "SET_FACILITY_LOCK";
            case 44:
                return "CHANGE_BARRING_PASSWORD";
            case WspTypeDecoder.WSP_HEADER_WWW_AUTHENTICATE /*45*/:
                return "QUERY_NETWORK_SELECTION_MODE";
            case 46:
                return "SET_NETWORK_SELECTION_AUTOMATIC";
            case 47:
                return "SET_NETWORK_SELECTION_MANUAL";
            case 48:
                return "QUERY_AVAILABLE_NETWORKS ";
            case 49:
                return "DTMF_START";
            case 50:
                return "DTMF_STOP";
            case 51:
                return "BASEBAND_VERSION";
            case 52:
                return "SEPARATE_CONNECTION";
            case 53:
                return "SET_MUTE";
            case 54:
                return "GET_MUTE";
            case 55:
                return "QUERY_CLIP";
            case 56:
                return "LAST_DATA_CALL_FAIL_CAUSE";
            case 57:
                return "DATA_CALL_LIST";
            case 58:
                return "RESET_RADIO";
            case 59:
                return "OEM_HOOK_RAW";
            case WspTypeDecoder.WSP_HEADER_ACCEPT_ENCODING2 /*60*/:
                return "OEM_HOOK_STRINGS";
            case WspTypeDecoder.WSP_HEADER_CACHE_CONTROL2 /*61*/:
                return "SCREEN_STATE";
            case 62:
                return "SET_SUPP_SVC_NOTIFICATION";
            case 63:
                return "WRITE_SMS_TO_SIM";
            case 64:
                return "DELETE_SMS_ON_SIM";
            case WspTypeDecoder.WSP_HEADER_SET_COOKIE /*65*/:
                return "SET_BAND_MODE";
            case 66:
                return "QUERY_AVAILABLE_BAND_MODE";
            case 67:
                return "REQUEST_STK_GET_PROFILE";
            case 68:
                return "REQUEST_STK_SET_PROFILE";
            case WspTypeDecoder.WSP_HEADER_CONTENT_DISPOSITION2 /*69*/:
                return "REQUEST_STK_SEND_ENVELOPE_COMMAND";
            case 70:
                return "REQUEST_STK_SEND_TERMINAL_RESPONSE";
            case 71:
                return "REQUEST_STK_HANDLE_CALL_SETUP_REQUESTED_FROM_SIM";
            case 72:
                return "REQUEST_EXPLICIT_CALL_TRANSFER";
            case 73:
                return "REQUEST_SET_PREFERRED_NETWORK_TYPE";
            case 74:
                return "REQUEST_GET_PREFERRED_NETWORK_TYPE";
            case 75:
                return "REQUEST_GET_NEIGHBORING_CELL_IDS";
            case RadioNVItems.RIL_NV_LTE_SCAN_PRIORITY_41 /*76*/:
                return "REQUEST_SET_LOCATION_UPDATES";
            case RadioNVItems.RIL_NV_LTE_HIDDEN_BAND_PRIORITY_25 /*77*/:
                return "RIL_REQUEST_CDMA_SET_SUBSCRIPTION_SOURCE";
            case 78:
                return "RIL_REQUEST_CDMA_SET_ROAMING_PREFERENCE";
            case RadioNVItems.RIL_NV_LTE_HIDDEN_BAND_PRIORITY_41 /*79*/:
                return "RIL_REQUEST_CDMA_QUERY_ROAMING_PREFERENCE";
            case 80:
                return "RIL_REQUEST_SET_TTY_MODE";
            case 81:
                return "RIL_REQUEST_QUERY_TTY_MODE";
            case 82:
                return "RIL_REQUEST_CDMA_SET_PREFERRED_VOICE_PRIVACY_MODE";
            case 83:
                return "RIL_REQUEST_CDMA_QUERY_PREFERRED_VOICE_PRIVACY_MODE";
            case 84:
                return "RIL_REQUEST_CDMA_FLASH";
            case 85:
                return "RIL_REQUEST_CDMA_BURST_DTMF";
            case 86:
                return "RIL_REQUEST_CDMA_VALIDATE_AND_WRITE_AKEY";
            case 87:
                return "RIL_REQUEST_CDMA_SEND_SMS";
            case 88:
                return "RIL_REQUEST_CDMA_SMS_ACKNOWLEDGE";
            case 89:
                return "RIL_REQUEST_GSM_GET_BROADCAST_CONFIG";
            case 90:
                return "RIL_REQUEST_GSM_SET_BROADCAST_CONFIG";
            case 91:
                return "RIL_REQUEST_GSM_BROADCAST_ACTIVATION";
            case 92:
                return "RIL_REQUEST_CDMA_GET_BROADCAST_CONFIG";
            case 93:
                return "RIL_REQUEST_CDMA_SET_BROADCAST_CONFIG";
            case 94:
                return "RIL_REQUEST_CDMA_BROADCAST_ACTIVATION";
            case 95:
                return "RIL_REQUEST_CDMA_SUBSCRIPTION";
            case CommandsInterface.CDMA_SMS_FAIL_CAUSE_ENCODING_PROBLEM /*96*/:
                return "RIL_REQUEST_CDMA_WRITE_SMS_TO_RUIM";
            case 97:
                return "RIL_REQUEST_CDMA_DELETE_SMS_ON_RUIM";
            case 98:
                return "RIL_REQUEST_DEVICE_IDENTITY";
            case 99:
                return "REQUEST_EXIT_EMERGENCY_CALLBACK_MODE";
            case 100:
                return "RIL_REQUEST_GET_SMSC_ADDRESS";
            case Threads.ALERT_EXTREME_THREAD /*101*/:
                return "RIL_REQUEST_SET_SMSC_ADDRESS";
            case Threads.ALERT_SEVERE_THREAD /*102*/:
                return "RIL_REQUEST_REPORT_SMS_MEMORY_STATUS";
            case Threads.ALERT_AMBER_THREAD /*103*/:
                return "RIL_REQUEST_REPORT_STK_SERVICE_IS_RUNNING";
            case Threads.ALERT_TEST_MESSAGE_THREAD /*104*/:
                return "RIL_REQUEST_CDMA_GET_SUBSCRIPTION_SOURCE";
            case 105:
                return "RIL_REQUEST_ISIM_AUTHENTICATION";
            case 106:
                return "RIL_REQUEST_ACKNOWLEDGE_INCOMING_GSM_SMS_WITH_PDU";
            case 107:
                return "RIL_REQUEST_STK_SEND_ENVELOPE_WITH_STATUS";
            case 108:
                return "RIL_REQUEST_VOICE_RADIO_TECH";
            case 109:
                return "RIL_REQUEST_GET_CELL_INFO_LIST";
            case Threads.ALERTS_ALL_ONE_THREAD /*110*/:
                return "RIL_REQUEST_SET_CELL_INFO_LIST_RATE";
            case 111:
                return "RIL_REQUEST_SET_INITIAL_ATTACH_APN";
            case 112:
                return "RIL_REQUEST_IMS_REGISTRATION_STATE";
            case 113:
                return "RIL_REQUEST_IMS_SEND_SMS";
            case CallFailCause.KTF_FAIL_CAUSE_114 /*114*/:
                return "RIL_REQUEST_SIM_TRANSMIT_APDU_BASIC";
            case CallFailCause.KTF_FAIL_CAUSE_115 /*115*/:
                return "RIL_REQUEST_SIM_OPEN_CHANNEL";
            case CallFailCause.KTF_FAIL_CAUSE_116 /*116*/:
                return "RIL_REQUEST_SIM_CLOSE_CHANNEL";
            case 117:
                return "RIL_REQUEST_SIM_TRANSMIT_APDU_CHANNEL";
            case 118:
                return "RIL_REQUEST_NV_READ_ITEM";
            case 119:
                return "RIL_REQUEST_NV_WRITE_ITEM";
            case 120:
                return "RIL_REQUEST_NV_WRITE_CDMA_PRL";
            case 121:
                return "RIL_REQUEST_NV_RESET_CONFIG";
            case 122:
                return "RIL_REQUEST_SET_UICC_SUBSCRIPTION";
            case 123:
                return "RIL_REQUEST_ALLOW_DATA";
            case 124:
                return "GET_HARDWARE_CONFIG";
            case 125:
                return "RIL_REQUEST_SIM_AUTHENTICATION";
            case 128:
                return "RIL_REQUEST_SET_DATA_PROFILE";
            case 129:
                return "RIL_REQUEST_SHUTDOWN";
            case 10001:
                return "DIAL_EMERGENCY_CALL";
            case 10002:
                return "CALL_DEFLECTION";
            case 10003:
                return "MODIFY_CALL_INITIATE";
            case 10004:
                return "MODIFY_CALL_CONFIRM";
            case 10005:
                return "SET_VOICE_DOMAIN_PREF";
            case 10006:
                return "SAFE_MODE";
            case 10007:
                return "SET_TRANSMIT_POWER";
            case 10008:
                return "GET_CELL_BROADCAST_CONFIG";
            case 10009:
                return "GET_PHONEBOOK_STORAGE_INFO";
            case 10010:
                return "GET_PHONEBOOK_ENTRY";
            case 10011:
                return "ACCESS_PHONEBOOK_ENTRY";
            case 10012:
                return "USIM_PB_CAPA";
            case 10013:
                return "LOCK_INFO";
            case 10014:
                return "STK_SIM_INIT_EVENT";
            case 10015:
                return "SET_PREFERRED_NETWORK_LIST";
            case 10016:
                return "GET_PREFERRED_NETWORK_LIST";
            case 10017:
                return "CHANGE_SIM_PERSO";
            case 10018:
                return "ENTER_SIM_PERSO";
            case 10019:
                return "SEND_ENCODED_USSD";
            case 10020:
                return "CDMA_SEND_SMS_EXPECT_MORE";
            case 10021:
                return "HANGUP_VT";
            case 10022:
                return "REQUEST_HOLD";
            case 10023:
                return "SET_SIM_POWER";
            case 10024:
                return "SET_LTE_BAND_MODE";
            default:
                return "<unknown request>";
        }
    }

    static String responseToString(int request) {
        switch (request) {
            case 1000:
                return "UNSOL_RESPONSE_RADIO_STATE_CHANGED";
            case com.android.internal.telephony.cdma.CallFailCause.CDMA_DROP /*1001*/:
                return "UNSOL_RESPONSE_CALL_STATE_CHANGED";
            case com.android.internal.telephony.cdma.CallFailCause.CDMA_INTERCEPT /*1002*/:
                return "UNSOL_RESPONSE_VOICE_NETWORK_STATE_CHANGED";
            case com.android.internal.telephony.cdma.CallFailCause.CDMA_REORDER /*1003*/:
                return "UNSOL_RESPONSE_NEW_SMS";
            case com.android.internal.telephony.cdma.CallFailCause.CDMA_SO_REJECT /*1004*/:
                return "UNSOL_RESPONSE_NEW_SMS_STATUS_REPORT";
            case com.android.internal.telephony.cdma.CallFailCause.CDMA_RETRY_ORDER /*1005*/:
                return "UNSOL_RESPONSE_NEW_SMS_ON_SIM";
            case com.android.internal.telephony.cdma.CallFailCause.CDMA_ACCESS_FAILURE /*1006*/:
                return "UNSOL_ON_USSD";
            case com.android.internal.telephony.cdma.CallFailCause.CDMA_PREEMPTED /*1007*/:
                return "UNSOL_ON_USSD_REQUEST";
            case com.android.internal.telephony.cdma.CallFailCause.CDMA_NOT_EMERGENCY /*1008*/:
                return "UNSOL_NITZ_TIME_RECEIVED";
            case com.android.internal.telephony.cdma.CallFailCause.CDMA_ACCESS_BLOCKED /*1009*/:
                return "UNSOL_SIGNAL_STRENGTH";
            case 1010:
                return "UNSOL_DATA_CALL_LIST_CHANGED";
            case 1011:
                return "UNSOL_SUPP_SVC_NOTIFICATION";
            case 1012:
                return "UNSOL_STK_SESSION_END";
            case 1013:
                return "UNSOL_STK_PROACTIVE_COMMAND";
            case 1014:
                return "UNSOL_STK_EVENT_NOTIFY";
            case CharacterSets.UTF_16 /*1015*/:
                return "UNSOL_STK_CALL_SETUP";
            case 1016:
                return "UNSOL_SIM_SMS_STORAGE_FULL";
            case 1017:
                return "UNSOL_SIM_REFRESH";
            case 1018:
                return "UNSOL_CALL_RING";
            case 1019:
                return "UNSOL_RESPONSE_SIM_STATUS_CHANGED";
            case 1020:
                return "UNSOL_RESPONSE_CDMA_NEW_SMS";
            case 1021:
                return "UNSOL_RESPONSE_NEW_BROADCAST_SMS";
            case 1022:
                return "UNSOL_CDMA_RUIM_SMS_STORAGE_FULL";
            case 1023:
                return "UNSOL_RESTRICTED_STATE_CHANGED";
            case 1024:
                return "UNSOL_ENTER_EMERGENCY_CALLBACK_MODE";
            case 1025:
                return "UNSOL_CDMA_CALL_WAITING";
            case 1026:
                return "UNSOL_CDMA_OTA_PROVISION_STATUS";
            case 1027:
                return "UNSOL_CDMA_INFO_REC";
            case 1028:
                return "UNSOL_OEM_HOOK_RAW";
            case 1029:
                return "UNSOL_RINGBACK_TONE";
            case 1030:
                return "UNSOL_RESEND_INCALL_MUTE";
            case 1031:
                return "CDMA_SUBSCRIPTION_SOURCE_CHANGED";
            case 1032:
                return "UNSOL_CDMA_PRL_CHANGED";
            case 1033:
                return "UNSOL_EXIT_EMERGENCY_CALLBACK_MODE";
            case 1034:
                return "UNSOL_RIL_CONNECTED";
            case 1035:
                return "UNSOL_VOICE_RADIO_TECH_CHANGED";
            case 1036:
                return "UNSOL_CELL_INFO_LIST";
            case 1037:
                return "UNSOL_RESPONSE_IMS_NETWORK_STATE_CHANGED";
            case 1038:
                return "RIL_UNSOL_UICC_SUBSCRIPTION_STATUS_CHANGED";
            case 1039:
                return "UNSOL_SRVCC_STATE_NOTIFY";
            case 1040:
                return "RIL_UNSOL_HARDWARE_CONFIG_CHANGED";
            case 11000:
                return "UNSOL_RESPONSE_NEW_CB_MSG";
            case 11001:
                return "UNSOL_RELEASE_COMPLETE_MESSAGE";
            case 11002:
                return "UNSOL_STK_SEND_SMS_RESULT";
            case 11003:
                return "UNSOL_STK_CALL_CONTROL_RESULT";
            case 11008:
                return "UNSOL_DEVICE_READY_NOTI";
            case 11010:
                return "UNSOL_AM";
            case 11013:
                return "UNSOL_SAP";
            case 11020:
                return "UNSOL_UART";
            case 11021:
                return "UNSOL_SIM_PB_READY";
            case 11024:
                return "UNSOL_VE";
            case 11027:
                return "UNSOL_IMS_REGISTRATION_STATE_CHANGED";
            case 11028:
                return "UNSOL_MODIFY_CALL";
            case 11030:
                return "UNSOL_CS_FALLBACK";
            case 11032:
                return "UNSOL_VOICE_SYSTEM_ID";
            case 11034:
                return "UNSOL_IMS_RETRYOVER";
            case 11035:
                return "UNSOL_PB_INIT_COMPLETE";
            case 11043:
                return "UNSOL_HOME_NETWORK_NOTI";
            case 11054:
                return "UNSOL_STK_CALL_STATUS";
            case 11055:
                return "UNSOL_ON_SS";
            case 11061:
                return "UNSOL_IMS_PREFERENCE_CHANGED";
            default:
                return "<unknown response>";
        }
    }

    private void riljLog(String msg) {
        Rlog.d(RILJ_LOG_TAG, msg + (this.mInstanceId != null ? " [SUB" + this.mInstanceId + "]" : ""));
    }

    private void riljLogv(String msg) {
        Rlog.v(RILJ_LOG_TAG, msg + (this.mInstanceId != null ? " [SUB" + this.mInstanceId + "]" : ""));
    }

    private void unsljLog(int response) {
        riljLog("[UNSL]< " + responseToString(response));
    }

    private void unsljLogMore(int response, String more) {
        riljLog("[UNSL]< " + responseToString(response) + " " + more);
    }

    private void unsljLogRet(int response, Object ret) {
        riljLog("[UNSL]< " + responseToString(response) + " " + retToString(response, ret));
    }

    private void unsljLogvRet(int response, Object ret) {
        riljLogv("[UNSL]< " + responseToString(response) + " " + retToString(response, ret));
    }

    public void getDeviceIdentity(Message response) {
        RILRequest rr = RILRequest.obtain(98, response);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
        send(rr);
    }

    public void getCDMASubscription(Message response) {
        RILRequest rr = RILRequest.obtain(95, response);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
        send(rr);
    }

    public void setPhoneType(int phoneType) {
        riljLog("setPhoneType=" + phoneType + " old value=" + this.mPhoneType);
        this.mPhoneType = phoneType;
    }

    public void queryCdmaRoamingPreference(Message response) {
        RILRequest rr = RILRequest.obtain(79, response);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
        send(rr);
    }

    public void setCdmaRoamingPreference(int cdmaRoamingType, Message response) {
        RILRequest rr = RILRequest.obtain(78, response);
        rr.mParcel.writeInt(1);
        rr.mParcel.writeInt(cdmaRoamingType);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest) + " : " + cdmaRoamingType);
        send(rr);
    }

    public void setCdmaSubscriptionSource(int cdmaSubscription, Message response) {
        RILRequest rr = RILRequest.obtain(77, response);
        rr.mParcel.writeInt(1);
        rr.mParcel.writeInt(cdmaSubscription);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest) + " : " + cdmaSubscription);
        send(rr);
    }

    public void getCdmaSubscriptionSource(Message response) {
        RILRequest rr = RILRequest.obtain(Threads.ALERT_TEST_MESSAGE_THREAD, response);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
        send(rr);
    }

    public void queryTTYMode(Message response) {
        RILRequest rr = RILRequest.obtain(81, response);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
        send(rr);
    }

    public void setTTYMode(int ttyMode, Message response) {
        RILRequest rr = RILRequest.obtain(80, response);
        rr.mParcel.writeInt(1);
        rr.mParcel.writeInt(ttyMode);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest) + " : " + ttyMode);
        send(rr);
    }

    public void sendCDMAFeatureCode(String FeatureCode, Message response) {
        RILRequest rr = RILRequest.obtain(84, response);
        rr.mParcel.writeString(FeatureCode);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest) + " : " + FeatureCode);
        send(rr);
    }

    public void getCdmaBroadcastConfig(Message response) {
        send(RILRequest.obtain(92, response));
    }

    public void setCdmaBroadcastConfig(CdmaSmsBroadcastConfigInfo[] configs, Message response) {
        int i;
        RILRequest rr = RILRequest.obtain(93, response);
        ArrayList<CdmaSmsBroadcastConfigInfo> processedConfigs = new ArrayList();
        for (CdmaSmsBroadcastConfigInfo config : configs) {
            for (i = config.getFromServiceCategory(); i <= config.getToServiceCategory(); i++) {
                processedConfigs.add(new CdmaSmsBroadcastConfigInfo(i, i, config.getLanguage(), config.isSelected()));
            }
        }
        CdmaSmsBroadcastConfigInfo[] rilConfigs = (CdmaSmsBroadcastConfigInfo[]) processedConfigs.toArray(configs);
        rr.mParcel.writeInt(rilConfigs.length);
        for (i = 0; i < rilConfigs.length; i++) {
            rr.mParcel.writeInt(rilConfigs[i].getFromServiceCategory());
            rr.mParcel.writeInt(rilConfigs[i].getLanguage());
            rr.mParcel.writeInt(rilConfigs[i].isSelected() ? 1 : 0);
        }
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest) + " with " + rilConfigs.length + " configs : ");
        send(rr);
    }

    public void setCdmaBroadcastActivation(boolean activate, Message response) {
        int i = 1;
        RILRequest rr = RILRequest.obtain(94, response);
        rr.mParcel.writeInt(1);
        Parcel parcel = rr.mParcel;
        if (activate) {
            i = 0;
        }
        parcel.writeInt(i);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
        send(rr);
    }

    public void exitEmergencyCallbackMode(Message response) {
        RILRequest rr = RILRequest.obtain(99, response);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
        send(rr);
    }

    public void requestIsimAuthentication(String nonce, Message response) {
        RILRequest rr = RILRequest.obtain(105, response);
        rr.mParcel.writeString(nonce);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
        send(rr);
    }

    public void requestIccSimAuthentication(int authContext, String data, String aid, Message response) {
        RILRequest rr = RILRequest.obtain(125, response);
        rr.mParcel.writeInt(authContext);
        rr.mParcel.writeString(data);
        rr.mParcel.writeString(aid);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
        send(rr);
    }

    public void getCellInfoList(Message result) {
        RILRequest rr = RILRequest.obtain(109, result);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
        send(rr);
    }

    public void setCellInfoListRate(int rateInMillis, Message response) {
        riljLog("setCellInfoListRate: " + rateInMillis);
        RILRequest rr = RILRequest.obtain(Threads.ALERTS_ALL_ONE_THREAD, response);
        rr.mParcel.writeInt(1);
        rr.mParcel.writeInt(rateInMillis);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
        send(rr);
    }

    public void setInitialAttachApn(String apn, String protocol, int authType, String username, String password, Message result) {
        RILRequest rr = RILRequest.obtain(111, null);
        riljLog("Set RIL_REQUEST_SET_INITIAL_ATTACH_APN");
        rr.mParcel.writeString(apn);
        rr.mParcel.writeString(protocol);
        rr.mParcel.writeInt(authType);
        rr.mParcel.writeString(username);
        rr.mParcel.writeString(password);
        if (isDebugLevelNotLow()) {
            riljLog(rr.serialString() + "> " + requestToString(rr.mRequest) + ", apn:" + apn + ", protocol:" + protocol + ", authType:" + authType + ", username:" + username + ", password:" + password);
        }
        send(rr);
    }

    public void setDataProfile(DataProfile[] dps, Message result) {
        riljLog("Set RIL_REQUEST_SET_DATA_PROFILE");
        RILRequest rr = RILRequest.obtain(128, null);
        DataProfile.toParcel(rr.mParcel, dps);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest) + " with " + dps + " Data Profiles : ");
        for (DataProfile dataProfile : dps) {
            riljLog(dataProfile.toString());
        }
        send(rr);
    }

    public void testingEmergencyCall() {
        riljLog("testingEmergencyCall");
        this.mTestingEmergencyCall.set(true);
    }

    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        pw.println("RIL: " + this);
        pw.println(" mSocket=" + this.mSocket);
        pw.println(" mSenderThread=" + this.mSenderThread);
        pw.println(" mSender=" + this.mSender);
        pw.println(" mReceiverThread=" + this.mReceiverThread);
        pw.println(" mReceiver=" + this.mReceiver);
        pw.println(" mWakeLock=" + this.mWakeLock);
        pw.println(" mWakeLockTimeout=" + this.mWakeLockTimeout);
        synchronized (this.mRequestList) {
            synchronized (this.mWakeLock) {
                pw.println(" mWakeLockCount=" + this.mWakeLockCount);
            }
            int count = this.mRequestList.size();
            pw.println(" mRequestList count=" + count);
            for (int i = 0; i < count; i++) {
                RILRequest rr = (RILRequest) this.mRequestList.valueAt(i);
                pw.println("  [" + rr.mSerial + "] " + requestToString(rr.mRequest));
            }
        }
        pw.println(" mLastNITZTimeInfo=" + this.mLastNITZTimeInfo);
        pw.println(" mTestingEmergencyCall=" + this.mTestingEmergencyCall.get());
    }

    public void iccOpenLogicalChannel(String AID, Message response) {
        RILRequest rr = RILRequest.obtain(CallFailCause.KTF_FAIL_CAUSE_115, response);
        rr.mParcel.writeString(AID);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
        send(rr);
    }

    public void iccCloseLogicalChannel(int channel, Message response) {
        RILRequest rr = RILRequest.obtain(CallFailCause.KTF_FAIL_CAUSE_116, response);
        rr.mParcel.writeInt(1);
        rr.mParcel.writeInt(channel);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
        send(rr);
    }

    public void iccTransmitApduLogicalChannel(int channel, int cla, int instruction, int p1, int p2, int p3, String data, Message response) {
        if (channel <= 0) {
            throw new RuntimeException("Invalid channel in iccTransmitApduLogicalChannel: " + channel);
        }
        iccTransmitApduHelper(117, channel, cla, instruction, p1, p2, p3, data, response);
    }

    public void iccTransmitApduBasicChannel(int cla, int instruction, int p1, int p2, int p3, String data, Message response) {
        iccTransmitApduHelper(CallFailCause.KTF_FAIL_CAUSE_114, 0, cla, instruction, p1, p2, p3, data, response);
    }

    private void iccTransmitApduHelper(int rilCommand, int channel, int cla, int instruction, int p1, int p2, int p3, String data, Message response) {
        RILRequest rr = RILRequest.obtain(rilCommand, response);
        rr.mParcel.writeInt(channel);
        rr.mParcel.writeInt(cla);
        rr.mParcel.writeInt(instruction);
        rr.mParcel.writeInt(p1);
        rr.mParcel.writeInt(p2);
        rr.mParcel.writeInt(p3);
        rr.mParcel.writeString(data);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
        send(rr);
    }

    public void nvReadItem(int itemID, Message response) {
        RILRequest rr = RILRequest.obtain(118, response);
        rr.mParcel.writeInt(itemID);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest) + ' ' + itemID);
        send(rr);
    }

    public void nvWriteItem(int itemID, String itemValue, Message response) {
        RILRequest rr = RILRequest.obtain(119, response);
        rr.mParcel.writeInt(itemID);
        rr.mParcel.writeString(itemValue);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest) + ' ' + itemID + ": " + itemValue);
        send(rr);
    }

    public void nvWriteCdmaPrl(byte[] preferredRoamingList, Message response) {
        RILRequest rr = RILRequest.obtain(120, response);
        rr.mParcel.writeByteArray(preferredRoamingList);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest) + " (" + preferredRoamingList.length + " bytes)");
        send(rr);
    }

    public void nvResetConfig(int resetType, Message response) {
        RILRequest rr = RILRequest.obtain(121, response);
        rr.mParcel.writeInt(1);
        rr.mParcel.writeInt(resetType);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest) + ' ' + resetType);
        send(rr);
    }

    public void accessPhoneBookEntry(int command, int fileid, int index, AdnRecord adn, String pin2, Message result) {
        boolean isEncodable;
        int i;
        int j;
        RILRequest rr = RILRequest.obtain(10011, result);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
        String alphTag = adn.mAlphaTag;
        String number = adn.mNumber;
        String email = adn.mEmails[0];
        String anr = adn.mAnr;
        String anrA = adn.mAnrA;
        String anrB = adn.mAnrB;
        String anrC = adn.mAnrC;
        String sne = adn.mSne;
        if (anr.length() == 0) {
            anr = null;
        }
        if (anrA.length() == 0) {
            anrA = null;
        }
        if (anrB.length() == 0) {
            anrB = null;
        }
        if (anrC.length() == 0) {
            anrC = null;
        }
        byte[] byteArrayName = new byte[0];
        byte[] byteArrayNameTemp = new byte[0];
        byte[] byteArraySNE = new byte[0];
        byte[] byteArraySNETemp = new byte[0];
        byte[] byteArrayEmail = new byte[0];
        byte[] byteArrayEmailTemp = new byte[0];
        rr.mParcel.writeInt(command);
        rr.mParcel.writeInt(fileid);
        rr.mParcel.writeInt(index);
        try {
            GsmAlphabet.countGsmSeptets(alphTag, true);
            isEncodable = true;
        } catch (Exception e) {
            isEncodable = false;
        }
        if (isEncodable) {
        }
        try {
            byteArrayNameTemp = alphTag.getBytes("ISO-10646-UCS-2");
            byteArrayName = new byte[(byteArrayNameTemp.length - 2)];
            for (i = 0; i < byteArrayNameTemp.length - 2; i++) {
                byteArrayName[i] = byteArrayNameTemp[i + 2];
            }
        } catch (Exception e2) {
        }
        for (i = 0; i < byteArrayName.length; i++) {
            riljLog("name[" + i + " ] = " + byteArrayName[i]);
        }
        rr.mParcel.writeByteArray(byteArrayName);
        rr.mParcel.writeInt(byteArrayName.length);
        rr.mParcel.writeInt(3);
        rr.mParcel.writeString(number);
        if (false) {
            try {
                byteArrayEmailTemp = email.getBytes("ISO-10646-UCS-2");
                byteArrayEmail = new byte[(byteArrayEmailTemp.length - 2)];
                for (j = 0; j < byteArrayEmailTemp.length - 2; j++) {
                    byteArrayEmail[j] = byteArrayEmailTemp[j + 2];
                }
            } catch (Exception e3) {
            }
            riljLog("email = " + email);
            rr.mParcel.writeByteArray(byteArrayEmail);
            rr.mParcel.writeInt(byteArrayEmail.length);
        } else {
            byte[] gsm8bitEmail = GsmAlphabet.stringToGsm8BitPacked(email);
            rr.mParcel.writeByteArray(gsm8bitEmail);
            rr.mParcel.writeInt(gsm8bitEmail.length);
        }
        riljLog("anr = " + anr);
        rr.mParcel.writeString(anr);
        rr.mParcel.writeString(anrA);
        rr.mParcel.writeString(anrB);
        rr.mParcel.writeString(anrC);
        try {
            GsmAlphabet.countGsmSeptets(sne, true);
        } catch (Exception e4) {
        }
        try {
            byteArraySNETemp = sne.getBytes("ISO-10646-UCS-2");
            byteArraySNE = new byte[(byteArraySNETemp.length - 2)];
            for (i = 0; i < byteArraySNETemp.length - 2; i++) {
                byteArraySNE[i] = byteArraySNETemp[i + 2];
            }
        } catch (Exception e5) {
        }
        riljLog("sne = " + sne);
        for (j = 0; j < byteArraySNE.length; j++) {
            riljLog("sne[" + j + " ] = " + byteArraySNE[j]);
        }
        rr.mParcel.writeByteArray(byteArraySNE);
        rr.mParcel.writeInt(byteArraySNE.length);
        rr.mParcel.writeInt(3);
        rr.mParcel.writeString(pin2);
        send(rr);
    }

    public void changeBarringPassword(String facility, String oldPwd, String newPwd, String newPwdAgain, Message result) {
        RILRequest rr = RILRequest.obtain(44, result);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
        rr.mParcel.writeInt(4);
        rr.mParcel.writeString(facility);
        rr.mParcel.writeString(oldPwd);
        rr.mParcel.writeString(newPwd);
        rr.mParcel.writeString(newPwdAgain);
        send(rr);
    }

    public void getPhoneBookStorageInfo(int fileid, Message response) {
        RILRequest rr = RILRequest.obtain(10009, response);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
        rr.mParcel.writeInt(1);
        rr.mParcel.writeInt(fileid);
        send(rr);
    }

    public void getPhoneBookEntry(int command, int fileid, int index, String pin2, Message result) {
        RILRequest rr = RILRequest.obtain(10010, result);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
        rr.mParcel.writeInt(command);
        rr.mParcel.writeInt(fileid);
        rr.mParcel.writeString(null);
        rr.mParcel.writeInt(index);
        rr.mParcel.writeInt(0);
        rr.mParcel.writeInt(0);
        rr.mParcel.writeString(null);
        rr.mParcel.writeString(pin2);
        send(rr);
    }

    public void getUsimPBCapa(Message result) {
        RILRequest rr = RILRequest.obtain(10012, result);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
        send(rr);
    }

    public void getSIMLockInfo(int num_lock_type, int lock_type, Message result) {
        RILRequest rr = RILRequest.obtain(10013, result);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
        rr.mParcel.writeInt(num_lock_type);
        rr.mParcel.writeInt(lock_type);
        send(rr);
    }

    public void setSimInitEvent(Message response) {
        RILRequest rr = RILRequest.obtain(10014, response);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
        send(rr);
    }

    public int modifyNetworkTypeByOperator(int networkType) {
        boolean isDcmLteFeature = SystemProperties.getBoolean("persist.radio.dcmlte", true);
        boolean isRoaming = SystemProperties.getBoolean("gsm.operator.isroaming", false);
        int preferredNetworkType = SystemProperties.getInt("persist.radio.setnwkmode", 9);
        boolean userDataEnabled = Global.getInt(this.mContext.getContentResolver(), "mobile_data", 1) == 1;
        boolean userDataRoamingEnabled = Global.getInt(this.mContext.getContentResolver(), "data_roaming", 1) == 1;
        if (System.getInt(this.mContext.getContentResolver(), "voicecall_type", 1) == 0) {
        }
        String campMcc = SystemProperties.get("gsm.operator.numeric", "44000").substring(0, 3);
        String homeNet = SystemProperties.get("gsm.sim.operator.numeric", "44050");
        if ("00101".equals(homeNet) || "99999".equals(homeNet) || "45001".equals(homeNet)) {
            return networkType;
        }
        if (campMcc.length() < 3 || homeNet.substring(0, 3).equals(campMcc) || "000".equals(campMcc) || "000".equals(homeNet.substring(0, 3))) {
            isRoaming = false;
        } else {
            isRoaming = true;
        }
        riljLog("modifyNetworkTypeByOperator (preferredNetworkType:" + preferredNetworkType + ", userDataEnabled:" + userDataEnabled + ", isRoaming:" + isRoaming + ", userDataRoamingEnabled:" + userDataRoamingEnabled + ")");
        userDataEnabled = userDataEnabled && (!isRoaming || userDataRoamingEnabled);
        if ("KDI".equals("")) {
            boolean LteDataComm = PreferenceManager.getDefaultSharedPreferences(this.mContext).getBoolean("japan_system_select_key", true);
            riljLog("... LteDataComm:" + LteDataComm);
            userDataEnabled = userDataEnabled && LteDataComm;
        }
        networkType = preferredNetworkType;
        if (!userDataEnabled) {
            switch (preferredNetworkType) {
                case 8:
                    networkType = 4;
                    break;
                case 9:
                    networkType = 3;
                    break;
                case 10:
                    networkType = 7;
                    break;
                case 12:
                    networkType = 2;
                    break;
            }
        }
        return networkType;
    }

    public void sendEncodedUSSD(byte[] ussdString, int length, int dcsCode, Message response) {
        RILRequest rr = RILRequest.obtain(10019, response);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest) + " " + IccUtils.bytesToHexString(ussdString) + ", DCS : " + dcsCode);
        rr.mParcel.writeByteArray(ussdString);
        rr.mParcel.writeInt(length);
        rr.mParcel.writeInt(dcsCode);
        send(rr);
    }

    private Object responseSSReleaseCompleteNotification(Parcel p) {
        SSReleaseCompleteNotification notification = new SSReleaseCompleteNotification();
        Rlog.i(RILJ_LOG_TAG, "responseSSReleaseCompleteNotification()");
        notification.size = p.readInt();
        notification.dataLen = p.readInt();
        notification.params = p.readInt();
        notification.status = p.readInt();
        notification.data = p.readString();
        Rlog.i(RILJ_LOG_TAG, "notification.data = " + notification.data);
        return notification;
    }

    private Object responseUSSD(Parcel p) {
        int num = p.readInt();
        int dcs = p.readInt();
        Rlog.d(RILJ_LOG_TAG, "responseUSSD - num " + num);
        String[] response = new String[num];
        int i = 0;
        while (i < num) {
            if (dcs != 148 || i <= 0) {
                response[i] = p.readString();
            } else {
                try {
                    response[i] = new String(IccUtils.hexStringToBytes(p.readString()), CharacterSets.MIMENAME_EUC_KR);
                    Rlog.d(RILJ_LOG_TAG, "responseUSSD :: USSD_DCS_KS5601, response" + response[i]);
                } catch (UnsupportedEncodingException e) {
                    response[i] = "";
                }
            }
            i++;
        }
        return response;
    }

    private Object responseSIM_PB(Parcel p) {
        int[] lengthAlphas = new int[3];
        int[] dataTypeAlphas = new int[3];
        String[] alphaTags = new String[3];
        int[] lengthNumbers = new int[5];
        int[] dataTypeNumbers = new int[5];
        String[] numbers = new String[5];
        p.readIntArray(lengthAlphas);
        p.readIntArray(dataTypeAlphas);
        p.readStringArray(alphaTags);
        if (!SHIP_BUILD) {
            Rlog.i(RILJ_LOG_TAG, "alphaTag is " + alphaTags[0]);
        }
        if (!SHIP_BUILD) {
            Rlog.i(RILJ_LOG_TAG, "SNE is " + alphaTags[1]);
        }
        if (!SHIP_BUILD) {
            Rlog.i(RILJ_LOG_TAG, "email is " + alphaTags[2]);
        }
        p.readIntArray(lengthNumbers);
        Rlog.i(RILJ_LOG_TAG, "lengthNumber is " + lengthNumbers[0]);
        p.readIntArray(dataTypeNumbers);
        p.readStringArray(numbers);
        if (!SHIP_BUILD) {
            Rlog.i(RILJ_LOG_TAG, "number is " + numbers[0]);
        }
        if (!SHIP_BUILD) {
            Rlog.i(RILJ_LOG_TAG, "ANR is " + numbers[1]);
        }
        return new SimPBEntryResult(lengthAlphas, dataTypeAlphas, alphaTags, lengthNumbers, dataTypeNumbers, numbers, p.readInt(), p.readInt());
    }

    private Object responseSIM_LockInfo(Parcel p) {
        int num_lock_type = p.readInt();
        int lock_type = p.readInt();
        int lock_key = p.readInt();
        int num_of_retry = p.readInt();
        Rlog.i(RILJ_LOG_TAG, "num:" + num_lock_type + " lock_type:" + lock_type + " lock_key:" + lock_key + " num_of_retry:" + num_of_retry);
        return new SimLockInfoResult(num_lock_type, lock_type, lock_key, num_of_retry);
    }

    private Object responseSimPowerDone(Parcel p) {
        Rlog.d(RILJ_LOG_TAG, "ResponseSimPowerDone");
        int numInts = p.readInt();
        int[] response = new int[numInts];
        for (int i = 0; i < numInts; i++) {
            response[i] = p.readInt();
        }
        Rlog.d(RILJ_LOG_TAG, "ResponseSimPowerDone : " + response[0]);
        return Integer.valueOf(response[0]);
    }

    private void setInitialPhoneType(int networkType) {
        int phoneType = TelephonyManager.getPhoneType(networkType);
        if (phoneType != this.initPhoneType) {
            SystemProperties.set("persist.radio.initphone-type", String.valueOf(phoneType));
            Rlog.d(RILJ_LOG_TAG, "Initial PhoneType is changed: " + this.initPhoneType + " -> " + phoneType);
            this.initPhoneType = phoneType;
        }
    }

    public void supplyIccPerso(String pin, Message result) {
        RILRequest rr = RILRequest.obtain(10018, result);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
        rr.mParcel.writeInt(1);
        rr.mParcel.writeString(pin);
        send(rr);
    }

    public void changeIccSimPerso(String oldPass, String newPass, Message result) {
        RILRequest rr = RILRequest.obtain(10017, result);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
        rr.mParcel.writeInt(2);
        rr.mParcel.writeString(oldPass);
        rr.mParcel.writeString(newPass);
        send(rr);
    }

    public void sendSMSmore(String smscPDU, String pdu, Message result) {
        RILRequest rr = RILRequest.obtain(26, result);
        Rlog.d(RILJ_LOG_TAG, "smscPDU: " + smscPDU);
        Rlog.d(RILJ_LOG_TAG, "pdu: " + pdu);
        constructGsmSendSmsRilRequest(rr, smscPDU, pdu);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
        send(rr);
    }

    public void sendCdmaSmsMore(byte[] pdu, Message result) {
        RILRequest rr = RILRequest.obtain(10020, result);
        constructCdmaSendSmsRilRequest(rr, pdu);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
        send(rr);
    }

    public void hangupVT(int rejectCause, Message result) {
        RILRequest rr = RILRequest.obtain(10021, result);
        rr.mParcel.writeInt(1);
        rr.mParcel.writeInt(rejectCause);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest) + " rejectCause: " + rejectCause);
        send(rr);
    }

    public void holdCall(Message result) {
        RILRequest rr = RILRequest.obtain(10022, result);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
        send(rr);
    }

    private Object responseSSData(Parcel p) {
        SsData ssData = new SsData();
        ssData.mServiceType = ssData.ServiceTypeFromRILInt(p.readInt());
        ssData.mRequestType = ssData.RequestTypeFromRILInt(p.readInt());
        ssData.mTeleserviceType = ssData.TeleserviceTypeFromRILInt(p.readInt());
        ssData.mServiceClass = p.readInt();
        ssData.mResult = p.readInt();
        int num = p.readInt();
        int i;
        if (ssData.mServiceType.isTypeCF() && ssData.mRequestType.isTypeInterrogation()) {
            ssData.mCfInfo = new CallForwardInfo[num];
            for (i = 0; i < num; i++) {
                ssData.mCfInfo[i] = new CallForwardInfo();
                ssData.mCfInfo[i].status = p.readInt();
                ssData.mCfInfo[i].reason = p.readInt();
                ssData.mCfInfo[i].serviceClass = p.readInt();
                ssData.mCfInfo[i].toa = p.readInt();
                ssData.mCfInfo[i].number = p.readString();
                ssData.mCfInfo[i].timeSeconds = p.readInt();
                riljLogv("[SS Data] CF Info " + i + " : " + ssData.mCfInfo[i]);
            }
        } else {
            ssData.mSsInfo = new int[num];
            for (i = 0; i < num; i++) {
                ssData.mSsInfo[i] = p.readInt();
                riljLogv("[SS Data] SS Info " + i + " : " + ssData.mSsInfo[i]);
            }
        }
        return ssData;
    }

    private void setPreferredData() {
    }

    private boolean isDebugLevelNotLow() {
        if (SystemProperties.get(LOG_LEVEL_PROP, LOG_LEVEL_PROP_LOW).equalsIgnoreCase(LOG_LEVEL_PROP_LOW)) {
            return false;
        }
        return true;
    }
}
