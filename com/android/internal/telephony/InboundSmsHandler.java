package com.android.internal.telephony;

import android.app.ActivityManagerNative;
import android.app.PendingIntent;
import android.app.PendingIntent.CanceledException;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.pm.UserInfo;
import android.content.res.Resources;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncResult;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.os.Process;
import android.os.RemoteException;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.os.UserManager;
import android.preference.PreferenceManager;
import android.provider.Telephony.Carriers;
import android.provider.Telephony.CellBroadcasts;
import android.provider.Telephony.Sms;
import android.provider.Telephony.Sms.Inbox;
import android.provider.Telephony.Sms.Intents;
import android.provider.Telephony.TextBasedSmsColumns;
import android.sec.enterprise.DeviceInventory;
import android.sec.enterprise.EnterpriseDeviceManager;
import android.sec.enterprise.PhoneRestrictionPolicy;
import android.sec.enterprise.auditlog.AuditLog;
import android.telephony.LmsTokenCTC;
import android.telephony.Rlog;
import android.telephony.SmsManager;
import android.telephony.SmsMessage;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;
import com.android.internal.telephony.SmsHeader.ConcatRef;
import com.android.internal.telephony.SmsHeader.PortAddrs;
import com.android.internal.telephony.cdma.sms.BearerData;
import com.android.internal.telephony.cdma.sms.SmsEnvelope;
import com.android.internal.util.HexDump;
import com.android.internal.util.State;
import com.android.internal.util.StateMachine;
import com.carrieriq.iqagent.client.IQClient;
import com.sec.android.app.CscFeature;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

public abstract class InboundSmsHandler extends StateMachine {
    static final int ADDRESS_COLUMN = 6;
    static final int COUNT_COLUMN = 5;
    private static final boolean CSCFEATURE_RIL_LMS_REASSEMBLE_TIMEOUTS_CTC = "ReassembleTimeout".equals(CscFeature.getInstance().getString("CscFeature_RIL_DisplayPolicyPartialLongSms"));
    static final int DATE_COLUMN = 3;
    protected static final boolean DBG = true;
    private static final byte DELIMITER = (byte) 11;
    static final int DESTINATION_PORT_COLUMN = 2;
    static final int EVENT_BROADCAST_COMPLETE = 3;
    static final int EVENT_BROADCAST_SMS = 2;
    public static final int EVENT_INJECT_SMS = 8;
    public static final int EVENT_NEW_SMS = 1;
    protected static final int EVENT_REASSEMBLE_TIMEOUT = 23;
    static final int EVENT_RELEASE_WAKELOCK = 5;
    static final int EVENT_RETURN_TO_IDLE = 4;
    static final int EVENT_START_ACCEPTING_SMS = 6;
    protected static final int EVENT_STOP_REASSEMBLE = 24;
    static final int EVENT_UPDATE_PHONE_OBJECT = 7;
    public static final int EVENT_WRITE_SMS_COMPLETE = 9;
    static final int ID_COLUMN = 7;
    static final int PDU_COLUMN = 0;
    private static final String[] PDU_PROJECTION = new String[]{"pdu"};
    private static final String[] PDU_SEQUENCE_PORT_PROJECTION = new String[]{"pdu", "sequence", "destination_port"};
    protected static final String[] RAW_PROJECTION = new String[]{"pdu", "reference_number", "sequence", "destination_port"};
    private static final int REASSEMBLE_TIMEOUT = 300000;
    static final int REFERENCE_NUMBER_COLUMN = 4;
    static final String SELECT_BY_ID = "_id=?";
    static final String SELECT_BY_REFERENCE = "address=? AND reference_number=? AND count=?";
    static final int SEQUENCE_COLUMN = 1;
    private static final String SKT_CARRIERLOCK_MODE_FILE = "/efs/sms/sktcarrierlockmode";
    private static final String SKT_CARRIERLOCK_MODE_FOLDER = "/efs/sms";
    private static final long SMS_GARBAGE_COLLECTION_TIME = 600000;
    private static final long SMS_GARBAGE_COLLECTION_TIME_CHN = 172800000;
    private static final long SMS_GARBAGE_COLLECTION_TIME_NTT = 604800000;
    private static final String TAG = "InboundSmsHandler";
    private static final boolean VDBG = false;
    private static final int WAKELOCK_TIMEOUT = 3000;
    private static boolean gcf_flag = false;
    protected static final Uri mRawUri = Uri.withAppendedPath(Sms.CONTENT_URI, "raw");
    private static final Uri sRawUri = Uri.withAppendedPath(Sms.CONTENT_URI, "raw");
    private static int sReassembleRef = new Random().nextInt(256);
    private String mApplicationID;
    private String mApplicationName;
    private byte[] mApplicationSpecificData;
    private BroadcastReceiver mBlockedSmsMmsReceiver = new C00162();
    protected CellBroadcastHandler mCellBroadcastHandler;
    protected final boolean mCheckForDuplicatePortsInOmadmWapPush = Resources.getSystem().getBoolean(17956944);
    protected final CommandsInterface mCi;
    private String mCommand;
    protected final Context mContext;
    final DefaultState mDefaultState = new DefaultState();
    final DeliveringState mDeliveringState = new DeliveringState();
    Handler mHandler = new C00151();
    protected IQClient mIQClient;
    final IdleState mIdleState = new IdleState();
    protected String mLatestSmsAddress = "latest_sms_address";
    protected String mLatestSmsTimestamp = "latest_sms_timestamp";
    protected String mLatestSmsType = "latest_sms_type";
    protected final LmsAssemblyTrackerCTC mLmsAssemblyTracker;
    protected PhoneBase mPhone;
    protected final ContentResolver mResolver;
    protected int mSegmentedSmsCount = 0;
    private final boolean mSmsReceiveDisabled;
    final StartupState mStartupState = new StartupState();
    protected SmsStorageMonitor mStorageMonitor;
    private String mUI;
    private UserManager mUserManager;
    final WaitingState mWaitingState = new WaitingState();
    final WakeLock mWakeLock;
    protected final WapPushOverSms mWapPush;
    protected boolean misWapPush = false;

    class C00151 extends Handler {
        C00151() {
        }

        public void handleMessage(Message msg) {
            switch (msg.what) {
                case 23:
                    Log.secD(InboundSmsHandler.TAG, "EVENT_REASSEMBLE_TIMEOUT is called");
                    InboundSmsHandler.this.handleReassembleTimeout(msg.obj.result);
                    return;
                case 24:
                    Log.secD(InboundSmsHandler.TAG, "EVENT_STOP_REASSEMBLE is called");
                    removeMessages(23, msg.obj);
                    return;
                default:
                    return;
            }
        }
    }

    class C00162 extends BroadcastReceiver {
        C00162() {
        }

        public void onReceive(Context context, Intent intent) {
            InboundSmsHandler.this.log("Received blocked SmsMms intent :" + intent.getAction());
            byte[] pdu = intent.getByteArrayExtra("extra_pdu");
            if (pdu != null) {
                if ("com.android.server.enterprise.restriction.SEND_BLOCKED_SMS".equals(intent.getAction())) {
                    InboundSmsHandler.this.handleBlockedSms(pdu, intent.getIntExtra("send_type", -1));
                }
                if ("com.android.server.enterprise.restriction.SEND_BLOCKED_MMS".equals(intent.getAction()) && InboundSmsHandler.this.mWapPush.dispatchWapPdu(pdu, this, InboundSmsHandler.this) == -1) {
                    String origAddress = intent.getStringExtra("extra_orig_address");
                    String timeStamp = intent.getStringExtra("extra_time_stamp");
                    StringBuilder wapSms = new StringBuilder();
                    for (byte b : pdu) {
                        wapSms.append((char) b);
                    }
                    if (wapSms.toString().length() != 0) {
                        InboundSmsHandler.this.storeSMS(origAddress, timeStamp, wapSms.toString(), true);
                    }
                }
            }
        }
    }

    class DefaultState extends State {
        DefaultState() {
        }

        public boolean processMessage(Message msg) {
            switch (msg.what) {
                case 7:
                    InboundSmsHandler.this.onUpdatePhoneObject((PhoneBase) msg.obj);
                    break;
                case 9:
                    AsyncResult ar = msg.obj;
                    if (ar.exception != null) {
                        Rlog.d(InboundSmsHandler.TAG, "Failed to write SMS-PP message to UICC", ar.exception);
                        InboundSmsHandler.this.mCi.acknowledgeLastIncomingGsmSms(false, 255, null);
                        break;
                    }
                    Rlog.d(InboundSmsHandler.TAG, "Successfully wrote SMS-PP message to UICC");
                    InboundSmsHandler.this.mCi.acknowledgeLastIncomingGsmSms(true, 0, null);
                    break;
                default:
                    String errorText = "processMessage: unhandled message type " + msg.what + " currState=" + InboundSmsHandler.this.getCurrentState().getName();
                    if (!Build.IS_DEBUGGABLE) {
                        InboundSmsHandler.this.loge(errorText);
                        break;
                    }
                    InboundSmsHandler.this.loge("---- Dumping InboundSmsHandler ----");
                    InboundSmsHandler.this.loge("Total records=" + InboundSmsHandler.this.getLogRecCount());
                    for (int i = Math.max(InboundSmsHandler.this.getLogRecSize() - 20, 0); i < InboundSmsHandler.this.getLogRecSize(); i++) {
                        InboundSmsHandler.this.loge("Rec[%d]: %s\n" + i + InboundSmsHandler.this.getLogRec(i).toString());
                    }
                    InboundSmsHandler.this.loge("---- Dumped InboundSmsHandler ----");
                    throw new RuntimeException(errorText);
            }
            return true;
        }
    }

    class DeliveringState extends State {
        DeliveringState() {
        }

        public void enter() {
            InboundSmsHandler.this.log("entering Delivering state");
        }

        public void exit() {
            InboundSmsHandler.this.log("leaving Delivering state");
        }

        public boolean processMessage(Message msg) {
            InboundSmsHandler.this.log("DeliveringState.processMessage:" + msg.what);
            switch (msg.what) {
                case 1:
                    InboundSmsHandler.this.handleNewSms((AsyncResult) msg.obj);
                    InboundSmsHandler.this.sendMessage(4);
                    return true;
                case 2:
                    if (InboundSmsHandler.this.processMessagePart((InboundSmsTracker) msg.obj)) {
                        InboundSmsHandler.this.transitionTo(InboundSmsHandler.this.mWaitingState);
                    }
                    return true;
                case 4:
                    InboundSmsHandler.this.transitionTo(InboundSmsHandler.this.mIdleState);
                    return true;
                case 5:
                    InboundSmsHandler.this.mWakeLock.release();
                    if (!InboundSmsHandler.this.mWakeLock.isHeld()) {
                        InboundSmsHandler.this.loge("mWakeLock released while delivering/broadcasting!");
                    }
                    return true;
                case 8:
                    InboundSmsHandler.this.handleInjectSms((AsyncResult) msg.obj);
                    InboundSmsHandler.this.sendMessage(4);
                    return true;
                default:
                    return false;
            }
        }
    }

    class IdleState extends State {
        IdleState() {
        }

        public void enter() {
            InboundSmsHandler.this.log("entering Idle state");
            InboundSmsHandler.this.sendMessageDelayed(5, 3000);
        }

        public void exit() {
            InboundSmsHandler.this.mWakeLock.acquire();
            InboundSmsHandler.this.log("acquired wakelock, leaving Idle state");
        }

        public boolean processMessage(Message msg) {
            InboundSmsHandler.this.log("IdleState.processMessage:" + msg.what);
            InboundSmsHandler.this.log("Idle state processing message type " + msg.what);
            switch (msg.what) {
                case 1:
                case 2:
                case 8:
                    InboundSmsHandler.this.deferMessage(msg);
                    InboundSmsHandler.this.transitionTo(InboundSmsHandler.this.mDeliveringState);
                    return true;
                case 4:
                    return true;
                case 5:
                    InboundSmsHandler.this.mWakeLock.release();
                    if (InboundSmsHandler.this.mWakeLock.isHeld()) {
                        InboundSmsHandler.this.log("mWakeLock is still held after release");
                        return true;
                    }
                    InboundSmsHandler.this.log("mWakeLock released");
                    return true;
                default:
                    return false;
            }
        }
    }

    static class LmsPartCollector {
        private int mDestPort = -1;
        private final boolean mIsCdmaWapPush;
        private final int mMessageCount;
        private final Map<Integer, byte[]> mPdus = new HashMap();

        LmsPartCollector(int messageCount, boolean isCdmaWapPush) {
            if (messageCount < 1) {
                throw new IllegalArgumentException("messageCount should be >= 1");
            }
            this.mMessageCount = messageCount;
            this.mIsCdmaWapPush = isCdmaWapPush;
        }

        void add(int sequenceNumber, byte[] pdu, Integer destPort) {
            if (!this.mIsCdmaWapPush) {
                sequenceNumber--;
            }
            if (sequenceNumber < 0 || sequenceNumber >= this.mMessageCount) {
                throw new IndexOutOfBoundsException("Illegal sequence number");
            }
            this.mPdus.put(Integer.valueOf(sequenceNumber), pdu);
            if (destPort != null) {
                this.mDestPort = destPort.intValue();
            }
        }

        void addAllFromCursor(Cursor cursor, int sequenceNumberColumn, int pduColumn, int destPortColumn) {
            while (cursor.moveToNext()) {
                int seqNumber = cursor.getInt(sequenceNumberColumn);
                byte[] pdu = HexDump.hexStringToByteArray(cursor.getString(pduColumn));
                Integer destPort = null;
                if (!cursor.isNull(destPortColumn)) {
                    destPort = Integer.valueOf(cursor.getInt(destPortColumn));
                }
                add(seqNumber, pdu, destPort);
            }
        }

        boolean isEmpty() {
            return this.mPdus.isEmpty();
        }

        byte[][] getPdusSequence() {
            byte[][] pdus = new byte[this.mMessageCount][];
            for (int i = 0; i < this.mMessageCount; i++) {
                pdus[i] = (byte[]) this.mPdus.get(Integer.valueOf(i));
            }
            return pdus;
        }

        int getDestPort() {
            return this.mDestPort;
        }
    }

    private final class SmsBroadcastReceiver extends BroadcastReceiver {
        private long mBroadcastTimeNano = System.nanoTime();
        private final String mDeleteWhere;
        private final String[] mDeleteWhereArgs;

        SmsBroadcastReceiver(InboundSmsTracker tracker) {
            this.mDeleteWhere = tracker.getDeleteWhere();
            this.mDeleteWhereArgs = tracker.getDeleteWhereArgs();
        }

        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            int rc;
            if (action.equals(Intents.SMS_FILTER_ACTION)) {
                rc = getResultCode();
                if (rc == -1) {
                    Bundle resultExtras = getResultExtras(false);
                    if (resultExtras != null && resultExtras.containsKey("pdus")) {
                        intent.putExtra("pdus", (byte[][]) resultExtras.get("pdus"));
                    }
                    if (intent.hasExtra("destport")) {
                        int destPort = intent.getIntExtra("destport", -1);
                        intent.removeExtra("destport");
                        InboundSmsHandler.this.setAndDirectIntent(intent, destPort);
                        if (SmsManager.getDefault().getAutoPersisting()) {
                            Uri uri = InboundSmsHandler.this.writeInboxMessage(intent);
                            if (uri != null) {
                                intent.putExtra("uri", uri.toString());
                            }
                        }
                        InboundSmsHandler.this.dispatchIntent(intent, "android.permission.RECEIVE_SMS", 16, this, UserHandle.OWNER);
                        return;
                    }
                    InboundSmsHandler.this.loge("destport doesn't exist in the extras for SMS filter action.");
                    return;
                }
                InboundSmsHandler.this.log("SMS filtered by result code " + rc);
                InboundSmsHandler.this.deleteFromRawTable(this.mDeleteWhere, this.mDeleteWhereArgs);
                InboundSmsHandler.this.sendMessage(3);
            } else if (action.equals(Intents.SMS_DELIVER_ACTION)) {
                intent.setAction(Intents.SMS_RECEIVED_ACTION);
                intent.setComponent(null);
                InboundSmsHandler.this.dispatchIntent(intent, "android.permission.RECEIVE_SMS", 16, this, UserHandle.ALL);
            } else if (action.equals(Intents.WAP_PUSH_DELIVER_ACTION)) {
                intent.setAction(Intents.WAP_PUSH_RECEIVED_ACTION);
                intent.setComponent(null);
                InboundSmsHandler.this.dispatchIntent(intent, "android.permission.RECEIVE_SMS", 16, this, UserHandle.OWNER);
            } else {
                if (!(Intents.DATA_SMS_RECEIVED_ACTION.equals(action) || Intents.SMS_RECEIVED_ACTION.equals(action) || Intents.DATA_SMS_RECEIVED_ACTION.equals(action) || Intents.WAP_PUSH_RECEIVED_ACTION.equals(action))) {
                    InboundSmsHandler.this.loge("unexpected BroadcastReceiver action: " + action);
                }
                rc = getResultCode();
                if (rc == -1 || rc == 1) {
                    InboundSmsHandler.this.log("successful broadcast, deleting from raw table.");
                } else {
                    InboundSmsHandler.this.loge("a broadcast receiver set the result code to " + rc + ", deleting from raw table anyway!");
                }
                InboundSmsHandler.this.deleteFromRawTable(this.mDeleteWhere, this.mDeleteWhereArgs);
                InboundSmsHandler.this.sendMessage(3);
                int durationMillis = (int) ((System.nanoTime() - this.mBroadcastTimeNano) / 1000000);
                if (durationMillis >= 5000) {
                    InboundSmsHandler.this.loge("Slow ordered broadcast completion time: " + durationMillis + " ms");
                } else {
                    InboundSmsHandler.this.log("ordered broadcast completed in: " + durationMillis + " ms");
                }
            }
        }
    }

    class StartupState extends State {
        StartupState() {
        }

        public boolean processMessage(Message msg) {
            InboundSmsHandler.this.log("StartupState.processMessage:" + msg.what);
            switch (msg.what) {
                case 1:
                case 2:
                case 8:
                    InboundSmsHandler.this.deferMessage(msg);
                    return true;
                case 6:
                    InboundSmsHandler.this.transitionTo(InboundSmsHandler.this.mIdleState);
                    return true;
                default:
                    return false;
            }
        }
    }

    class WaitingState extends State {
        WaitingState() {
        }

        public boolean processMessage(Message msg) {
            InboundSmsHandler.this.log("WaitingState.processMessage:" + msg.what);
            switch (msg.what) {
                case 2:
                    InboundSmsHandler.this.deferMessage(msg);
                    return true;
                case 3:
                    InboundSmsHandler.this.sendMessage(4);
                    InboundSmsHandler.this.transitionTo(InboundSmsHandler.this.mDeliveringState);
                    return true;
                case 4:
                    return true;
                default:
                    return false;
            }
        }
    }

    private int addTrackerToRawTable(com.android.internal.telephony.InboundSmsTracker r33) {
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
	at jadx.api.JadxDecompiler.processClass(JadxDecompiler.java:306)
	at jadx.api.JavaClass.decompile(JavaClass.java:62)
	at jadx.api.JadxDecompiler$1.run(JadxDecompiler.java:199)
*/
        /*
        r32 = this;
        r2 = r33.getMessageCount();
        r3 = 1;
        if (r2 == r3) goto L_0x017a;
    L_0x0007:
        r10 = 0;
        r2 = "ro.csc.sales_code";
        r22 = android.os.SystemProperties.get(r2);
        r24 = r33.getSequenceNumber();	 Catch:{ SQLException -> 0x0209, all -> 0x0218 }
        r8 = r33.getAddress();	 Catch:{ SQLException -> 0x0209, all -> 0x0218 }
        r2 = r33.getReferenceNumber();	 Catch:{ SQLException -> 0x0209, all -> 0x0218 }
        r19 = java.lang.Integer.toString(r2);	 Catch:{ SQLException -> 0x0209, all -> 0x0218 }
        r2 = r33.getMessageCount();	 Catch:{ SQLException -> 0x0209, all -> 0x0218 }
        r9 = java.lang.Integer.toString(r2);	 Catch:{ SQLException -> 0x0209, all -> 0x0218 }
        r23 = java.lang.Integer.toString(r24);	 Catch:{ SQLException -> 0x0209, all -> 0x0218 }
        r2 = 3;	 Catch:{ SQLException -> 0x0209, all -> 0x0218 }
        r13 = new java.lang.String[r2];	 Catch:{ SQLException -> 0x0209, all -> 0x0218 }
        r2 = 0;	 Catch:{ SQLException -> 0x0209, all -> 0x0218 }
        r13[r2] = r8;	 Catch:{ SQLException -> 0x0209, all -> 0x0218 }
        r2 = 1;	 Catch:{ SQLException -> 0x0209, all -> 0x0218 }
        r13[r2] = r19;	 Catch:{ SQLException -> 0x0209, all -> 0x0218 }
        r2 = 2;	 Catch:{ SQLException -> 0x0209, all -> 0x0218 }
        r13[r2] = r9;	 Catch:{ SQLException -> 0x0209, all -> 0x0218 }
        r2 = "address=? AND reference_number=? AND count=?";	 Catch:{ SQLException -> 0x0209, all -> 0x0218 }
        r0 = r33;	 Catch:{ SQLException -> 0x0209, all -> 0x0218 }
        r0.setDeleteWhere(r2, r13);	 Catch:{ SQLException -> 0x0209, all -> 0x0218 }
        r11 = "address=? AND reference_number=? AND date < ?";	 Catch:{ SQLException -> 0x0209, all -> 0x0218 }
        r28 = r33.getTimestamp();	 Catch:{ SQLException -> 0x0209, all -> 0x0218 }
        r2 = 600000; // 0x927c0 float:8.40779E-40 double:2.964394E-318;	 Catch:{ SQLException -> 0x0209, all -> 0x0218 }
        r26 = r28 - r2;	 Catch:{ SQLException -> 0x0209, all -> 0x0218 }
        r2 = "CHN";	 Catch:{ SQLException -> 0x0209, all -> 0x0218 }
        r0 = r22;	 Catch:{ SQLException -> 0x0209, all -> 0x0218 }
        r2 = r2.equals(r0);	 Catch:{ SQLException -> 0x0209, all -> 0x0218 }
        if (r2 != 0) goto L_0x007a;	 Catch:{ SQLException -> 0x0209, all -> 0x0218 }
    L_0x0052:
        r2 = "CHU";	 Catch:{ SQLException -> 0x0209, all -> 0x0218 }
        r0 = r22;	 Catch:{ SQLException -> 0x0209, all -> 0x0218 }
        r2 = r2.equals(r0);	 Catch:{ SQLException -> 0x0209, all -> 0x0218 }
        if (r2 != 0) goto L_0x007a;	 Catch:{ SQLException -> 0x0209, all -> 0x0218 }
    L_0x005c:
        r2 = "CHM";	 Catch:{ SQLException -> 0x0209, all -> 0x0218 }
        r0 = r22;	 Catch:{ SQLException -> 0x0209, all -> 0x0218 }
        r2 = r2.equals(r0);	 Catch:{ SQLException -> 0x0209, all -> 0x0218 }
        if (r2 != 0) goto L_0x007a;	 Catch:{ SQLException -> 0x0209, all -> 0x0218 }
    L_0x0066:
        r2 = "CHC";	 Catch:{ SQLException -> 0x0209, all -> 0x0218 }
        r0 = r22;	 Catch:{ SQLException -> 0x0209, all -> 0x0218 }
        r2 = r2.equals(r0);	 Catch:{ SQLException -> 0x0209, all -> 0x0218 }
        if (r2 != 0) goto L_0x007a;	 Catch:{ SQLException -> 0x0209, all -> 0x0218 }
    L_0x0070:
        r2 = "TGY";	 Catch:{ SQLException -> 0x0209, all -> 0x0218 }
        r0 = r22;	 Catch:{ SQLException -> 0x0209, all -> 0x0218 }
        r2 = r2.equals(r0);	 Catch:{ SQLException -> 0x0209, all -> 0x0218 }
        if (r2 == 0) goto L_0x007f;	 Catch:{ SQLException -> 0x0209, all -> 0x0218 }
    L_0x007a:
        r2 = 172800000; // 0xa4cb800 float:9.856849E-33 double:8.53745436E-316;	 Catch:{ SQLException -> 0x0209, all -> 0x0218 }
        r26 = r28 - r2;	 Catch:{ SQLException -> 0x0209, all -> 0x0218 }
    L_0x007f:
        r2 = 3;	 Catch:{ SQLException -> 0x0209, all -> 0x0218 }
        r12 = new java.lang.String[r2];	 Catch:{ SQLException -> 0x0209, all -> 0x0218 }
        r2 = 0;	 Catch:{ SQLException -> 0x0209, all -> 0x0218 }
        r12[r2] = r8;	 Catch:{ SQLException -> 0x0209, all -> 0x0218 }
        r2 = 1;	 Catch:{ SQLException -> 0x0209, all -> 0x0218 }
        r12[r2] = r19;	 Catch:{ SQLException -> 0x0209, all -> 0x0218 }
        r2 = 2;	 Catch:{ SQLException -> 0x0209, all -> 0x0218 }
        r3 = java.lang.Long.toString(r26);	 Catch:{ SQLException -> 0x0209, all -> 0x0218 }
        r12[r2] = r3;	 Catch:{ SQLException -> 0x0209, all -> 0x0218 }
        r0 = r32;	 Catch:{ SQLException -> 0x0209, all -> 0x0218 }
        r2 = r0.mResolver;	 Catch:{ SQLException -> 0x0209, all -> 0x0218 }
        r3 = mRawUri;	 Catch:{ SQLException -> 0x0209, all -> 0x0218 }
        r2.delete(r3, r11, r12);	 Catch:{ SQLException -> 0x0209, all -> 0x0218 }
        r0 = r32;	 Catch:{ SQLException -> 0x0209, all -> 0x0218 }
        r2 = r0.mResolver;	 Catch:{ SQLException -> 0x0209, all -> 0x0218 }
        r3 = sRawUri;	 Catch:{ SQLException -> 0x0209, all -> 0x0218 }
        r4 = PDU_PROJECTION;	 Catch:{ SQLException -> 0x0209, all -> 0x0218 }
        r5 = "address=? AND reference_number=? AND count=? AND sequence=?";	 Catch:{ SQLException -> 0x0209, all -> 0x0218 }
        r6 = 4;	 Catch:{ SQLException -> 0x0209, all -> 0x0218 }
        r6 = new java.lang.String[r6];	 Catch:{ SQLException -> 0x0209, all -> 0x0218 }
        r7 = 0;	 Catch:{ SQLException -> 0x0209, all -> 0x0218 }
        r6[r7] = r8;	 Catch:{ SQLException -> 0x0209, all -> 0x0218 }
        r7 = 1;	 Catch:{ SQLException -> 0x0209, all -> 0x0218 }
        r6[r7] = r19;	 Catch:{ SQLException -> 0x0209, all -> 0x0218 }
        r7 = 2;	 Catch:{ SQLException -> 0x0209, all -> 0x0218 }
        r6[r7] = r9;	 Catch:{ SQLException -> 0x0209, all -> 0x0218 }
        r7 = 3;	 Catch:{ SQLException -> 0x0209, all -> 0x0218 }
        r6[r7] = r23;	 Catch:{ SQLException -> 0x0209, all -> 0x0218 }
        r7 = 0;	 Catch:{ SQLException -> 0x0209, all -> 0x0218 }
        r10 = r2.query(r3, r4, r5, r6, r7);	 Catch:{ SQLException -> 0x0209, all -> 0x0218 }
        r2 = r10.moveToNext();	 Catch:{ SQLException -> 0x0209, all -> 0x0218 }
        if (r2 == 0) goto L_0x0172;	 Catch:{ SQLException -> 0x0209, all -> 0x0218 }
    L_0x00bc:
        r2 = new java.lang.StringBuilder;	 Catch:{ SQLException -> 0x0209, all -> 0x0218 }
        r2.<init>();	 Catch:{ SQLException -> 0x0209, all -> 0x0218 }
        r3 = "Discarding duplicate message segment, refNumber=";	 Catch:{ SQLException -> 0x0209, all -> 0x0218 }
        r2 = r2.append(r3);	 Catch:{ SQLException -> 0x0209, all -> 0x0218 }
        r0 = r19;	 Catch:{ SQLException -> 0x0209, all -> 0x0218 }
        r2 = r2.append(r0);	 Catch:{ SQLException -> 0x0209, all -> 0x0218 }
        r3 = " seqNumber=";	 Catch:{ SQLException -> 0x0209, all -> 0x0218 }
        r2 = r2.append(r3);	 Catch:{ SQLException -> 0x0209, all -> 0x0218 }
        r0 = r23;	 Catch:{ SQLException -> 0x0209, all -> 0x0218 }
        r2 = r2.append(r0);	 Catch:{ SQLException -> 0x0209, all -> 0x0218 }
        r2 = r2.toString();	 Catch:{ SQLException -> 0x0209, all -> 0x0218 }
        r0 = r32;	 Catch:{ SQLException -> 0x0209, all -> 0x0218 }
        r0.loge(r2);	 Catch:{ SQLException -> 0x0209, all -> 0x0218 }
        r2 = 0;	 Catch:{ SQLException -> 0x0209, all -> 0x0218 }
        r17 = r10.getString(r2);	 Catch:{ SQLException -> 0x0209, all -> 0x0218 }
        r18 = r33.getPdu();	 Catch:{ SQLException -> 0x0209, all -> 0x0218 }
        r16 = com.android.internal.util.HexDump.hexStringToByteArray(r17);	 Catch:{ SQLException -> 0x0209, all -> 0x0218 }
        r2 = r33.getPdu();	 Catch:{ SQLException -> 0x0209, all -> 0x0218 }
        r0 = r16;	 Catch:{ SQLException -> 0x0209, all -> 0x0218 }
        r2 = java.util.Arrays.equals(r0, r2);	 Catch:{ SQLException -> 0x0209, all -> 0x0218 }
        if (r2 != 0) goto L_0x0202;	 Catch:{ SQLException -> 0x0209, all -> 0x0218 }
    L_0x00fb:
        r2 = "CHN";	 Catch:{ SQLException -> 0x0209, all -> 0x0218 }
        r0 = r22;	 Catch:{ SQLException -> 0x0209, all -> 0x0218 }
        r2 = r2.equals(r0);	 Catch:{ SQLException -> 0x0209, all -> 0x0218 }
        if (r2 != 0) goto L_0x012d;	 Catch:{ SQLException -> 0x0209, all -> 0x0218 }
    L_0x0105:
        r2 = "CHU";	 Catch:{ SQLException -> 0x0209, all -> 0x0218 }
        r0 = r22;	 Catch:{ SQLException -> 0x0209, all -> 0x0218 }
        r2 = r2.equals(r0);	 Catch:{ SQLException -> 0x0209, all -> 0x0218 }
        if (r2 != 0) goto L_0x012d;	 Catch:{ SQLException -> 0x0209, all -> 0x0218 }
    L_0x010f:
        r2 = "CHM";	 Catch:{ SQLException -> 0x0209, all -> 0x0218 }
        r0 = r22;	 Catch:{ SQLException -> 0x0209, all -> 0x0218 }
        r2 = r2.equals(r0);	 Catch:{ SQLException -> 0x0209, all -> 0x0218 }
        if (r2 != 0) goto L_0x012d;	 Catch:{ SQLException -> 0x0209, all -> 0x0218 }
    L_0x0119:
        r2 = "CHC";	 Catch:{ SQLException -> 0x0209, all -> 0x0218 }
        r0 = r22;	 Catch:{ SQLException -> 0x0209, all -> 0x0218 }
        r2 = r2.equals(r0);	 Catch:{ SQLException -> 0x0209, all -> 0x0218 }
        if (r2 != 0) goto L_0x012d;	 Catch:{ SQLException -> 0x0209, all -> 0x0218 }
    L_0x0123:
        r2 = "TGY";	 Catch:{ SQLException -> 0x0209, all -> 0x0218 }
        r0 = r22;	 Catch:{ SQLException -> 0x0209, all -> 0x0218 }
        r2 = r2.equals(r0);	 Catch:{ SQLException -> 0x0209, all -> 0x0218 }
        if (r2 == 0) goto L_0x01d3;	 Catch:{ SQLException -> 0x0209, all -> 0x0218 }
    L_0x012d:
        r30 = "address=? AND reference_number=? AND sequence=?";	 Catch:{ SQLException -> 0x0209, all -> 0x0218 }
        r2 = 3;	 Catch:{ SQLException -> 0x0209, all -> 0x0218 }
        r0 = new java.lang.String[r2];	 Catch:{ SQLException -> 0x0209, all -> 0x0218 }
        r31 = r0;	 Catch:{ SQLException -> 0x0209, all -> 0x0218 }
        r2 = 0;	 Catch:{ SQLException -> 0x0209, all -> 0x0218 }
        r31[r2] = r8;	 Catch:{ SQLException -> 0x0209, all -> 0x0218 }
        r2 = 1;	 Catch:{ SQLException -> 0x0209, all -> 0x0218 }
        r31[r2] = r19;	 Catch:{ SQLException -> 0x0209, all -> 0x0218 }
        r2 = 2;	 Catch:{ SQLException -> 0x0209, all -> 0x0218 }
        r31[r2] = r23;	 Catch:{ SQLException -> 0x0209, all -> 0x0218 }
        r0 = r32;	 Catch:{ SQLException -> 0x0209, all -> 0x0218 }
        r2 = r0.mResolver;	 Catch:{ SQLException -> 0x0209, all -> 0x0218 }
        r3 = mRawUri;	 Catch:{ SQLException -> 0x0209, all -> 0x0218 }
        r0 = r30;	 Catch:{ SQLException -> 0x0209, all -> 0x0218 }
        r1 = r31;	 Catch:{ SQLException -> 0x0209, all -> 0x0218 }
        r2.delete(r3, r0, r1);	 Catch:{ SQLException -> 0x0209, all -> 0x0218 }
        r2 = new java.lang.StringBuilder;	 Catch:{ SQLException -> 0x0209, all -> 0x0218 }
        r2.<init>();	 Catch:{ SQLException -> 0x0209, all -> 0x0218 }
        r3 = "Warning: delete old segment. Dup message segment PDU of length ";	 Catch:{ SQLException -> 0x0209, all -> 0x0218 }
        r2 = r2.append(r3);	 Catch:{ SQLException -> 0x0209, all -> 0x0218 }
        r0 = r18;	 Catch:{ SQLException -> 0x0209, all -> 0x0218 }
        r3 = r0.length;	 Catch:{ SQLException -> 0x0209, all -> 0x0218 }
        r2 = r2.append(r3);	 Catch:{ SQLException -> 0x0209, all -> 0x0218 }
        r3 = " is different from existing PDU of length ";	 Catch:{ SQLException -> 0x0209, all -> 0x0218 }
        r2 = r2.append(r3);	 Catch:{ SQLException -> 0x0209, all -> 0x0218 }
        r0 = r16;	 Catch:{ SQLException -> 0x0209, all -> 0x0218 }
        r3 = r0.length;	 Catch:{ SQLException -> 0x0209, all -> 0x0218 }
        r2 = r2.append(r3);	 Catch:{ SQLException -> 0x0209, all -> 0x0218 }
        r2 = r2.toString();	 Catch:{ SQLException -> 0x0209, all -> 0x0218 }
        r0 = r32;	 Catch:{ SQLException -> 0x0209, all -> 0x0218 }
        r0.loge(r2);	 Catch:{ SQLException -> 0x0209, all -> 0x0218 }
    L_0x0172:
        r10.close();	 Catch:{ SQLException -> 0x0209, all -> 0x0218 }
        if (r10 == 0) goto L_0x017a;
    L_0x0177:
        r10.close();
    L_0x017a:
        r25 = r33.getContentValues();
        r2 = "sim_slot";
        r0 = r32;
        r3 = r0.mPhone;
        r3 = r3.getPhoneId();
        r3 = java.lang.Integer.valueOf(r3);
        r0 = r25;
        r0.put(r2, r3);
        r0 = r32;
        r2 = r0.mResolver;
        r3 = sRawUri;
        r0 = r25;
        r15 = r2.insert(r3, r0);
        r2 = new java.lang.StringBuilder;
        r2.<init>();
        r3 = "URI of new row -> ";
        r2 = r2.append(r3);
        r2 = r2.append(r15);
        r2 = r2.toString();
        r0 = r32;
        r0.log(r2);
        r20 = android.content.ContentUris.parseId(r15);	 Catch:{ Exception -> 0x021f }
        r2 = r33.getMessageCount();	 Catch:{ Exception -> 0x021f }
        r3 = 1;	 Catch:{ Exception -> 0x021f }
        if (r2 != r3) goto L_0x01d1;	 Catch:{ Exception -> 0x021f }
    L_0x01c0:
        r2 = "_id=?";	 Catch:{ Exception -> 0x021f }
        r3 = 1;	 Catch:{ Exception -> 0x021f }
        r3 = new java.lang.String[r3];	 Catch:{ Exception -> 0x021f }
        r4 = 0;	 Catch:{ Exception -> 0x021f }
        r5 = java.lang.Long.toString(r20);	 Catch:{ Exception -> 0x021f }
        r3[r4] = r5;	 Catch:{ Exception -> 0x021f }
        r0 = r33;	 Catch:{ Exception -> 0x021f }
        r0.setDeleteWhere(r2, r3);	 Catch:{ Exception -> 0x021f }
    L_0x01d1:
        r2 = 1;
    L_0x01d2:
        return r2;
    L_0x01d3:
        r2 = new java.lang.StringBuilder;	 Catch:{ SQLException -> 0x0209, all -> 0x0218 }
        r2.<init>();	 Catch:{ SQLException -> 0x0209, all -> 0x0218 }
        r3 = "Warning: dup message segment PDU of length ";	 Catch:{ SQLException -> 0x0209, all -> 0x0218 }
        r2 = r2.append(r3);	 Catch:{ SQLException -> 0x0209, all -> 0x0218 }
        r0 = r18;	 Catch:{ SQLException -> 0x0209, all -> 0x0218 }
        r3 = r0.length;	 Catch:{ SQLException -> 0x0209, all -> 0x0218 }
        r2 = r2.append(r3);	 Catch:{ SQLException -> 0x0209, all -> 0x0218 }
        r3 = " is different from existing PDU of length ";	 Catch:{ SQLException -> 0x0209, all -> 0x0218 }
        r2 = r2.append(r3);	 Catch:{ SQLException -> 0x0209, all -> 0x0218 }
        r0 = r16;	 Catch:{ SQLException -> 0x0209, all -> 0x0218 }
        r3 = r0.length;	 Catch:{ SQLException -> 0x0209, all -> 0x0218 }
        r2 = r2.append(r3);	 Catch:{ SQLException -> 0x0209, all -> 0x0218 }
        r2 = r2.toString();	 Catch:{ SQLException -> 0x0209, all -> 0x0218 }
        r0 = r32;	 Catch:{ SQLException -> 0x0209, all -> 0x0218 }
        r0.loge(r2);	 Catch:{ SQLException -> 0x0209, all -> 0x0218 }
        r2 = 5;
        if (r10 == 0) goto L_0x01d2;
    L_0x01fe:
        r10.close();
        goto L_0x01d2;
    L_0x0202:
        r2 = 5;
        if (r10 == 0) goto L_0x01d2;
    L_0x0205:
        r10.close();
        goto L_0x01d2;
    L_0x0209:
        r14 = move-exception;
        r2 = "Can't access multipart SMS database";	 Catch:{ SQLException -> 0x0209, all -> 0x0218 }
        r0 = r32;	 Catch:{ SQLException -> 0x0209, all -> 0x0218 }
        r0.loge(r2, r14);	 Catch:{ SQLException -> 0x0209, all -> 0x0218 }
        r2 = 2;
        if (r10 == 0) goto L_0x01d2;
    L_0x0214:
        r10.close();
        goto L_0x01d2;
    L_0x0218:
        r2 = move-exception;
        if (r10 == 0) goto L_0x021e;
    L_0x021b:
        r10.close();
    L_0x021e:
        throw r2;
    L_0x021f:
        r14 = move-exception;
        r2 = new java.lang.StringBuilder;
        r2.<init>();
        r3 = "error parsing URI for new row: ";
        r2 = r2.append(r3);
        r2 = r2.append(r15);
        r2 = r2.toString();
        r0 = r32;
        r0.loge(r2, r14);
        r2 = 2;
        goto L_0x01d2;
        */
        throw new UnsupportedOperationException("Method not decompiled: com.android.internal.telephony.InboundSmsHandler.addTrackerToRawTable(com.android.internal.telephony.InboundSmsTracker):int");
    }

    protected abstract void acknowledgeLastIncomingSms(boolean z, int i, Message message);

    protected int dispatchLGUMMSNotitication(byte[] r31, int r32, java.lang.String r33) {
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
	at jadx.api.JadxDecompiler.processClass(JadxDecompiler.java:306)
	at jadx.api.JavaClass.decompile(JavaClass.java:62)
	at jadx.api.JadxDecompiler$1.run(JadxDecompiler.java:199)
*/
        /*
        r30 = this;
        r18 = 0;
        r26 = 0;
        r14 = 0;
        r19 = r18 + 1;
        r20 = r31[r18];
        if (r20 == 0) goto L_0x0016;
    L_0x000b:
        r4 = "InboundSmsHandler";
        r5 = "Received a WAP SMS which is not WDP. Discard.";
        android.util.Log.secW(r4, r5);
        r4 = 1;
        r18 = r19;
    L_0x0015:
        return r4;
    L_0x0016:
        r18 = r19 + 1;
        r27 = r31[r19];
        r19 = r18 + 1;
        r24 = r31[r18];
        if (r24 != 0) goto L_0x02a1;
    L_0x0020:
        r18 = r19 + 1;
        r4 = r31[r19];
        r4 = r4 & 255;
        r26 = r4 << 8;
        r19 = r18 + 1;
        r4 = r31[r18];
        r4 = r4 & 255;
        r26 = r26 | r4;
        r18 = r19 + 1;
        r4 = r31[r19];
        r4 = r4 & 255;
        r14 = r4 << 8;
        r19 = r18 + 1;
        r4 = r31[r18];
        r4 = r4 & 255;
        r14 = r14 | r4;
        r18 = r19;
    L_0x0041:
        r29 = new java.lang.StringBuilder;
        r4 = "reference_number =";
        r0 = r29;
        r0.<init>(r4);
        r0 = r29;
        r1 = r32;
        r0.append(r1);
        r4 = " AND address = ?";
        r0 = r29;
        r0.append(r4);
        r4 = 1;
        r8 = new java.lang.String[r4];
        r4 = 0;
        r8[r4] = r33;
        r4 = "InboundSmsHandler";
        r5 = new java.lang.StringBuilder;
        r5.<init>();
        r6 = "Received WAP PDU. Type = ";
        r5 = r5.append(r6);
        r0 = r20;
        r5 = r5.append(r0);
        r6 = ", originator = ";
        r5 = r5.append(r6);
        r0 = r33;
        r5 = r5.append(r0);
        r6 = ", src-port = ";
        r5 = r5.append(r6);
        r0 = r26;
        r5 = r5.append(r0);
        r6 = ", dst-port = ";
        r5 = r5.append(r6);
        r5 = r5.append(r14);
        r6 = ", ID = ";
        r5 = r5.append(r6);
        r0 = r32;
        r5 = r5.append(r0);
        r6 = ", segment# = ";
        r5 = r5.append(r6);
        r0 = r24;
        r5 = r5.append(r0);
        r6 = "/";
        r5 = r5.append(r6);
        r0 = r27;
        r5 = r5.append(r0);
        r5 = r5.toString();
        android.util.Log.secI(r4, r5);
        r23 = 0;
        r23 = (byte[][]) r23;
        r10 = 0;
        r0 = r30;	 Catch:{ SQLException -> 0x0266, all -> 0x0278 }
        r4 = r0.mResolver;	 Catch:{ SQLException -> 0x0266, all -> 0x0278 }
        r5 = mRawUri;	 Catch:{ SQLException -> 0x0266, all -> 0x0278 }
        r6 = RAW_PROJECTION;	 Catch:{ SQLException -> 0x0266, all -> 0x0278 }
        r7 = r29.toString();	 Catch:{ SQLException -> 0x0266, all -> 0x0278 }
        r9 = 0;	 Catch:{ SQLException -> 0x0266, all -> 0x0278 }
        r10 = r4.query(r5, r6, r7, r8, r9);	 Catch:{ SQLException -> 0x0266, all -> 0x0278 }
        r4 = "pdu";	 Catch:{ SQLException -> 0x0266, all -> 0x0278 }
        r22 = r10.getColumnIndex(r4);	 Catch:{ SQLException -> 0x0266, all -> 0x0278 }
        r4 = "sequence";	 Catch:{ SQLException -> 0x0266, all -> 0x0278 }
        r25 = r10.getColumnIndex(r4);	 Catch:{ SQLException -> 0x0266, all -> 0x0278 }
        r11 = r10.getCount();	 Catch:{ SQLException -> 0x0266, all -> 0x0278 }
        r12 = 0;	 Catch:{ SQLException -> 0x0266, all -> 0x0278 }
        r4 = "InboundSmsHandler";	 Catch:{ SQLException -> 0x0266, all -> 0x0278 }
        r5 = new java.lang.StringBuilder;	 Catch:{ SQLException -> 0x0266, all -> 0x0278 }
        r5.<init>();	 Catch:{ SQLException -> 0x0266, all -> 0x0278 }
        r6 = "segment count in db!! : ";	 Catch:{ SQLException -> 0x0266, all -> 0x0278 }
        r5 = r5.append(r6);	 Catch:{ SQLException -> 0x0266, all -> 0x0278 }
        r5 = r5.append(r11);	 Catch:{ SQLException -> 0x0266, all -> 0x0278 }
        r5 = r5.toString();	 Catch:{ SQLException -> 0x0266, all -> 0x0278 }
        android.util.Log.secE(r4, r5);	 Catch:{ SQLException -> 0x0266, all -> 0x0278 }
        r17 = 0;	 Catch:{ SQLException -> 0x0266, all -> 0x0278 }
    L_0x00ff:
        r0 = r17;	 Catch:{ SQLException -> 0x0266, all -> 0x0278 }
        if (r0 >= r11) goto L_0x014e;	 Catch:{ SQLException -> 0x0266, all -> 0x0278 }
    L_0x0103:
        r10.moveToNext();	 Catch:{ SQLException -> 0x0266, all -> 0x0278 }
        r0 = r25;	 Catch:{ SQLException -> 0x0266, all -> 0x0278 }
        r4 = r10.getLong(r0);	 Catch:{ SQLException -> 0x0266, all -> 0x0278 }
        r12 = (int) r4;	 Catch:{ SQLException -> 0x0266, all -> 0x0278 }
        r4 = "InboundSmsHandler";	 Catch:{ SQLException -> 0x0266, all -> 0x0278 }
        r5 = new java.lang.StringBuilder;	 Catch:{ SQLException -> 0x0266, all -> 0x0278 }
        r5.<init>();	 Catch:{ SQLException -> 0x0266, all -> 0x0278 }
        r6 = "segment in db!! : ";	 Catch:{ SQLException -> 0x0266, all -> 0x0278 }
        r5 = r5.append(r6);	 Catch:{ SQLException -> 0x0266, all -> 0x0278 }
        r5 = r5.append(r12);	 Catch:{ SQLException -> 0x0266, all -> 0x0278 }
        r5 = r5.toString();	 Catch:{ SQLException -> 0x0266, all -> 0x0278 }
        android.util.Log.secE(r4, r5);	 Catch:{ SQLException -> 0x0266, all -> 0x0278 }
        r0 = r24;	 Catch:{ SQLException -> 0x0266, all -> 0x0278 }
        if (r12 != r0) goto L_0x014b;	 Catch:{ SQLException -> 0x0266, all -> 0x0278 }
    L_0x0129:
        r4 = "InboundSmsHandler";	 Catch:{ SQLException -> 0x0266, all -> 0x0278 }
        r5 = new java.lang.StringBuilder;	 Catch:{ SQLException -> 0x0266, all -> 0x0278 }
        r5.<init>();	 Catch:{ SQLException -> 0x0266, all -> 0x0278 }
        r6 = "Received duplicated segment!! : ";	 Catch:{ SQLException -> 0x0266, all -> 0x0278 }
        r5 = r5.append(r6);	 Catch:{ SQLException -> 0x0266, all -> 0x0278 }
        r0 = r24;	 Catch:{ SQLException -> 0x0266, all -> 0x0278 }
        r5 = r5.append(r0);	 Catch:{ SQLException -> 0x0266, all -> 0x0278 }
        r5 = r5.toString();	 Catch:{ SQLException -> 0x0266, all -> 0x0278 }
        android.util.Log.secE(r4, r5);	 Catch:{ SQLException -> 0x0266, all -> 0x0278 }
        r4 = 1;
        if (r10 == 0) goto L_0x0015;
    L_0x0146:
        r10.close();
        goto L_0x0015;
    L_0x014b:
        r17 = r17 + 1;
        goto L_0x00ff;
    L_0x014e:
        r4 = r27 + -1;
        if (r11 == r4) goto L_0x01c1;
    L_0x0152:
        r28 = new android.content.ContentValues;	 Catch:{ SQLException -> 0x0266, all -> 0x0278 }
        r28.<init>();	 Catch:{ SQLException -> 0x0266, all -> 0x0278 }
        r4 = "date";	 Catch:{ SQLException -> 0x0266, all -> 0x0278 }
        r5 = new java.lang.Long;	 Catch:{ SQLException -> 0x0266, all -> 0x0278 }
        r6 = 0;	 Catch:{ SQLException -> 0x0266, all -> 0x0278 }
        r5.<init>(r6);	 Catch:{ SQLException -> 0x0266, all -> 0x0278 }
        r0 = r28;	 Catch:{ SQLException -> 0x0266, all -> 0x0278 }
        r0.put(r4, r5);	 Catch:{ SQLException -> 0x0266, all -> 0x0278 }
        r4 = "pdu";	 Catch:{ SQLException -> 0x0266, all -> 0x0278 }
        r0 = r31;	 Catch:{ SQLException -> 0x0266, all -> 0x0278 }
        r5 = r0.length;	 Catch:{ SQLException -> 0x0266, all -> 0x0278 }
        r5 = r5 - r18;	 Catch:{ SQLException -> 0x0266, all -> 0x0278 }
        r0 = r31;	 Catch:{ SQLException -> 0x0266, all -> 0x0278 }
        r1 = r18;	 Catch:{ SQLException -> 0x0266, all -> 0x0278 }
        r5 = com.android.internal.util.HexDump.toHexString(r0, r1, r5);	 Catch:{ SQLException -> 0x0266, all -> 0x0278 }
        r0 = r28;	 Catch:{ SQLException -> 0x0266, all -> 0x0278 }
        r0.put(r4, r5);	 Catch:{ SQLException -> 0x0266, all -> 0x0278 }
        r4 = "address";	 Catch:{ SQLException -> 0x0266, all -> 0x0278 }
        r0 = r28;	 Catch:{ SQLException -> 0x0266, all -> 0x0278 }
        r1 = r33;	 Catch:{ SQLException -> 0x0266, all -> 0x0278 }
        r0.put(r4, r1);	 Catch:{ SQLException -> 0x0266, all -> 0x0278 }
        r4 = "reference_number";	 Catch:{ SQLException -> 0x0266, all -> 0x0278 }
        r5 = java.lang.Integer.valueOf(r32);	 Catch:{ SQLException -> 0x0266, all -> 0x0278 }
        r0 = r28;	 Catch:{ SQLException -> 0x0266, all -> 0x0278 }
        r0.put(r4, r5);	 Catch:{ SQLException -> 0x0266, all -> 0x0278 }
        r4 = "count";	 Catch:{ SQLException -> 0x0266, all -> 0x0278 }
        r5 = java.lang.Integer.valueOf(r27);	 Catch:{ SQLException -> 0x0266, all -> 0x0278 }
        r0 = r28;	 Catch:{ SQLException -> 0x0266, all -> 0x0278 }
        r0.put(r4, r5);	 Catch:{ SQLException -> 0x0266, all -> 0x0278 }
        r4 = "sequence";	 Catch:{ SQLException -> 0x0266, all -> 0x0278 }
        r5 = java.lang.Integer.valueOf(r24);	 Catch:{ SQLException -> 0x0266, all -> 0x0278 }
        r0 = r28;	 Catch:{ SQLException -> 0x0266, all -> 0x0278 }
        r0.put(r4, r5);	 Catch:{ SQLException -> 0x0266, all -> 0x0278 }
        r4 = "destination_port";	 Catch:{ SQLException -> 0x0266, all -> 0x0278 }
        r5 = java.lang.Integer.valueOf(r14);	 Catch:{ SQLException -> 0x0266, all -> 0x0278 }
        r0 = r28;	 Catch:{ SQLException -> 0x0266, all -> 0x0278 }
        r0.put(r4, r5);	 Catch:{ SQLException -> 0x0266, all -> 0x0278 }
        r0 = r30;	 Catch:{ SQLException -> 0x0266, all -> 0x0278 }
        r4 = r0.mResolver;	 Catch:{ SQLException -> 0x0266, all -> 0x0278 }
        r5 = mRawUri;	 Catch:{ SQLException -> 0x0266, all -> 0x0278 }
        r0 = r28;	 Catch:{ SQLException -> 0x0266, all -> 0x0278 }
        r4.insert(r5, r0);	 Catch:{ SQLException -> 0x0266, all -> 0x0278 }
        r4 = 1;
        if (r10 == 0) goto L_0x0015;
    L_0x01bc:
        r10.close();
        goto L_0x0015;
    L_0x01c1:
        r0 = r27;	 Catch:{ SQLException -> 0x0266, all -> 0x0278 }
        r0 = new byte[r0][];	 Catch:{ SQLException -> 0x0266, all -> 0x0278 }
        r23 = r0;	 Catch:{ SQLException -> 0x0266, all -> 0x0278 }
        r10.moveToFirst();	 Catch:{ SQLException -> 0x0266, all -> 0x0278 }
        r17 = 0;	 Catch:{ SQLException -> 0x0266, all -> 0x0278 }
    L_0x01cc:
        r0 = r17;	 Catch:{ SQLException -> 0x0266, all -> 0x0278 }
        if (r0 >= r11) goto L_0x01f6;	 Catch:{ SQLException -> 0x0266, all -> 0x0278 }
    L_0x01d0:
        r0 = r25;	 Catch:{ SQLException -> 0x0266, all -> 0x0278 }
        r4 = r10.getLong(r0);	 Catch:{ SQLException -> 0x0266, all -> 0x0278 }
        r12 = (int) r4;	 Catch:{ SQLException -> 0x0266, all -> 0x0278 }
        if (r12 != 0) goto L_0x01e4;	 Catch:{ SQLException -> 0x0266, all -> 0x0278 }
    L_0x01d9:
        r4 = "destination_port";	 Catch:{ SQLException -> 0x0266, all -> 0x0278 }
        r15 = r10.getColumnIndex(r4);	 Catch:{ SQLException -> 0x0266, all -> 0x0278 }
        r4 = r10.getLong(r15);	 Catch:{ SQLException -> 0x0266, all -> 0x0278 }
        r14 = (int) r4;	 Catch:{ SQLException -> 0x0266, all -> 0x0278 }
    L_0x01e4:
        r0 = r22;	 Catch:{ SQLException -> 0x0266, all -> 0x0278 }
        r4 = r10.getString(r0);	 Catch:{ SQLException -> 0x0266, all -> 0x0278 }
        r4 = com.android.internal.util.HexDump.hexStringToByteArray(r4);	 Catch:{ SQLException -> 0x0266, all -> 0x0278 }
        r23[r12] = r4;	 Catch:{ SQLException -> 0x0266, all -> 0x0278 }
        r10.moveToNext();	 Catch:{ SQLException -> 0x0266, all -> 0x0278 }
        r17 = r17 + 1;	 Catch:{ SQLException -> 0x0266, all -> 0x0278 }
        goto L_0x01cc;	 Catch:{ SQLException -> 0x0266, all -> 0x0278 }
    L_0x01f6:
        r17 = 0;	 Catch:{ SQLException -> 0x0266, all -> 0x0278 }
    L_0x01f8:
        r0 = r17;	 Catch:{ SQLException -> 0x0266, all -> 0x0278 }
        if (r0 >= r11) goto L_0x0230;	 Catch:{ SQLException -> 0x0266, all -> 0x0278 }
    L_0x01fc:
        r0 = r17;	 Catch:{ SQLException -> 0x0266, all -> 0x0278 }
        r1 = r24;	 Catch:{ SQLException -> 0x0266, all -> 0x0278 }
        if (r0 == r1) goto L_0x022d;	 Catch:{ SQLException -> 0x0266, all -> 0x0278 }
    L_0x0202:
        r4 = r23[r17];	 Catch:{ SQLException -> 0x0266, all -> 0x0278 }
        if (r4 == 0) goto L_0x020b;	 Catch:{ SQLException -> 0x0266, all -> 0x0278 }
    L_0x0206:
        r4 = r23[r17];	 Catch:{ SQLException -> 0x0266, all -> 0x0278 }
        r4 = r4.length;	 Catch:{ SQLException -> 0x0266, all -> 0x0278 }
        if (r4 != 0) goto L_0x022d;	 Catch:{ SQLException -> 0x0266, all -> 0x0278 }
    L_0x020b:
        r4 = "InboundSmsHandler";	 Catch:{ SQLException -> 0x0266, all -> 0x0278 }
        r5 = new java.lang.StringBuilder;	 Catch:{ SQLException -> 0x0266, all -> 0x0278 }
        r5.<init>();	 Catch:{ SQLException -> 0x0266, all -> 0x0278 }
        r6 = "Received duplicated segment!! : ";	 Catch:{ SQLException -> 0x0266, all -> 0x0278 }
        r5 = r5.append(r6);	 Catch:{ SQLException -> 0x0266, all -> 0x0278 }
        r0 = r17;	 Catch:{ SQLException -> 0x0266, all -> 0x0278 }
        r5 = r5.append(r0);	 Catch:{ SQLException -> 0x0266, all -> 0x0278 }
        r5 = r5.toString();	 Catch:{ SQLException -> 0x0266, all -> 0x0278 }
        android.util.Log.secE(r4, r5);	 Catch:{ SQLException -> 0x0266, all -> 0x0278 }
        r4 = 1;
        if (r10 == 0) goto L_0x0015;
    L_0x0228:
        r10.close();
        goto L_0x0015;
    L_0x022d:
        r17 = r17 + 1;
        goto L_0x01f8;
    L_0x0230:
        r0 = r30;	 Catch:{ SQLException -> 0x0266, all -> 0x0278 }
        r4 = r0.mResolver;	 Catch:{ SQLException -> 0x0266, all -> 0x0278 }
        r5 = mRawUri;	 Catch:{ SQLException -> 0x0266, all -> 0x0278 }
        r6 = r29.toString();	 Catch:{ SQLException -> 0x0266, all -> 0x0278 }
        r4.delete(r5, r6, r8);	 Catch:{ SQLException -> 0x0266, all -> 0x0278 }
        if (r10 == 0) goto L_0x0242;
    L_0x023f:
        r10.close();
    L_0x0242:
        r21 = new java.io.ByteArrayOutputStream;
        r21.<init>();
        r17 = 0;
    L_0x0249:
        r0 = r17;
        r1 = r27;
        if (r0 >= r1) goto L_0x028b;
    L_0x024f:
        r0 = r17;
        r1 = r24;
        if (r0 != r1) goto L_0x027f;
    L_0x0255:
        r0 = r31;
        r4 = r0.length;
        r4 = r4 - r18;
        r0 = r21;
        r1 = r31;
        r2 = r18;
        r0.write(r1, r2, r4);
    L_0x0263:
        r17 = r17 + 1;
        goto L_0x0249;
    L_0x0266:
        r16 = move-exception;
        r4 = "InboundSmsHandler";	 Catch:{ SQLException -> 0x0266, all -> 0x0278 }
        r5 = "Can't access multipart SMS database";	 Catch:{ SQLException -> 0x0266, all -> 0x0278 }
        r0 = r16;	 Catch:{ SQLException -> 0x0266, all -> 0x0278 }
        android.util.Log.secE(r4, r5, r0);	 Catch:{ SQLException -> 0x0266, all -> 0x0278 }
        r4 = 2;
        if (r10 == 0) goto L_0x0015;
    L_0x0273:
        r10.close();
        goto L_0x0015;
    L_0x0278:
        r4 = move-exception;
        if (r10 == 0) goto L_0x027e;
    L_0x027b:
        r10.close();
    L_0x027e:
        throw r4;
    L_0x027f:
        r4 = r23[r17];
        r5 = 0;
        r6 = r23[r17];
        r6 = r6.length;
        r0 = r21;
        r0.write(r4, r5, r6);
        goto L_0x0263;
    L_0x028b:
        r13 = r21.toByteArray();
        r4 = 1;
        r0 = new byte[r4][];
        r23 = r0;
        r4 = 0;
        r23[r4] = r13;
        r0 = r30;
        r1 = r23;
        r0.dispatchPortAddressedPdus(r1, r14);
        r4 = -1;
        goto L_0x0015;
    L_0x02a1:
        r18 = r19;
        goto L_0x0041;
        */
        throw new UnsupportedOperationException("Method not decompiled: com.android.internal.telephony.InboundSmsHandler.dispatchLGUMMSNotitication(byte[], int, java.lang.String):int");
    }

    protected abstract int dispatchMessageRadioSpecific(SmsMessageBase smsMessageBase);

    protected abstract int getEncoding();

    protected abstract String getFormat();

    protected void handleReassembleTimeout(android.telephony.SmsMessage r15) {
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
	at jadx.api.JadxDecompiler.processClass(JadxDecompiler.java:306)
	at jadx.api.JavaClass.decompile(JavaClass.java:62)
	at jadx.api.JadxDecompiler$1.run(JadxDecompiler.java:199)
*/
        /*
        r14 = this;
        r2 = 1;
        r1 = 0;
        r12 = r15.getUserDataHeader();
        if (r12 == 0) goto L_0x000c;
    L_0x0008:
        r0 = r12.concatRef;
        if (r0 != 0) goto L_0x0014;
    L_0x000c:
        r0 = "InboundSmsHandler";
        r1 = "it's not proper segmented message";
        android.telephony.Rlog.d(r0, r1);
    L_0x0013:
        return;
    L_0x0014:
        r13 = new java.lang.StringBuilder;
        r0 = "reference_number =";
        r13.<init>(r0);
        r0 = r12.concatRef;
        r0 = r0.refNumber;
        r13.append(r0);
        r0 = " AND address = ?";
        r13.append(r0);
        r4 = new java.lang.String[r2];
        r0 = r15.getOriginatingAddress();
        r4[r1] = r0;
        r6 = 0;
        r0 = "InboundSmsHandler";
        r1 = new java.lang.StringBuilder;
        r1.<init>();
        r2 = "removing reference number : ";
        r1 = r1.append(r2);
        r2 = r12.concatRef;
        r2 = r2.refNumber;
        r1 = r1.append(r2);
        r1 = r1.toString();
        android.telephony.Rlog.d(r0, r1);
        r0 = r14.mResolver;	 Catch:{ SQLException -> 0x00bf, NullPointerException -> 0x00ce, all -> 0x00dd }
        r1 = mRawUri;	 Catch:{ SQLException -> 0x00bf, NullPointerException -> 0x00ce, all -> 0x00dd }
        r2 = RAW_PROJECTION;	 Catch:{ SQLException -> 0x00bf, NullPointerException -> 0x00ce, all -> 0x00dd }
        r3 = r13.toString();	 Catch:{ SQLException -> 0x00bf, NullPointerException -> 0x00ce, all -> 0x00dd }
        r5 = 0;	 Catch:{ SQLException -> 0x00bf, NullPointerException -> 0x00ce, all -> 0x00dd }
        r6 = r0.query(r1, r2, r3, r4, r5);	 Catch:{ SQLException -> 0x00bf, NullPointerException -> 0x00ce, all -> 0x00dd }
        r7 = r6.getCount();	 Catch:{ SQLException -> 0x00bf, NullPointerException -> 0x00ce, all -> 0x00dd }
        if (r7 != 0) goto L_0x0083;	 Catch:{ SQLException -> 0x00bf, NullPointerException -> 0x00ce, all -> 0x00dd }
    L_0x0061:
        r0 = "InboundSmsHandler";	 Catch:{ SQLException -> 0x00bf, NullPointerException -> 0x00ce, all -> 0x00dd }
        r1 = new java.lang.StringBuilder;	 Catch:{ SQLException -> 0x00bf, NullPointerException -> 0x00ce, all -> 0x00dd }
        r1.<init>();	 Catch:{ SQLException -> 0x00bf, NullPointerException -> 0x00ce, all -> 0x00dd }
        r2 = "there is no segmented sms with reference number ";	 Catch:{ SQLException -> 0x00bf, NullPointerException -> 0x00ce, all -> 0x00dd }
        r1 = r1.append(r2);	 Catch:{ SQLException -> 0x00bf, NullPointerException -> 0x00ce, all -> 0x00dd }
        r2 = r12.concatRef;	 Catch:{ SQLException -> 0x00bf, NullPointerException -> 0x00ce, all -> 0x00dd }
        r2 = r2.refNumber;	 Catch:{ SQLException -> 0x00bf, NullPointerException -> 0x00ce, all -> 0x00dd }
        r1 = r1.append(r2);	 Catch:{ SQLException -> 0x00bf, NullPointerException -> 0x00ce, all -> 0x00dd }
        r1 = r1.toString();	 Catch:{ SQLException -> 0x00bf, NullPointerException -> 0x00ce, all -> 0x00dd }
        android.telephony.Rlog.d(r0, r1);	 Catch:{ SQLException -> 0x00bf, NullPointerException -> 0x00ce, all -> 0x00dd }
        if (r6 == 0) goto L_0x0013;
    L_0x007f:
        r6.close();
        goto L_0x0013;
    L_0x0083:
        r0 = "pdu";	 Catch:{ SQLException -> 0x00bf, NullPointerException -> 0x00ce, all -> 0x00dd }
        r10 = r6.getColumnIndex(r0);	 Catch:{ SQLException -> 0x00bf, NullPointerException -> 0x00ce, all -> 0x00dd }
        r11 = new byte[r7][];	 Catch:{ SQLException -> 0x00bf, NullPointerException -> 0x00ce, all -> 0x00dd }
        r9 = 0;	 Catch:{ SQLException -> 0x00bf, NullPointerException -> 0x00ce, all -> 0x00dd }
    L_0x008c:
        if (r9 >= r7) goto L_0x009e;	 Catch:{ SQLException -> 0x00bf, NullPointerException -> 0x00ce, all -> 0x00dd }
    L_0x008e:
        r6.moveToNext();	 Catch:{ SQLException -> 0x00bf, NullPointerException -> 0x00ce, all -> 0x00dd }
        r0 = r6.getString(r10);	 Catch:{ SQLException -> 0x00bf, NullPointerException -> 0x00ce, all -> 0x00dd }
        r0 = com.android.internal.util.HexDump.hexStringToByteArray(r0);	 Catch:{ SQLException -> 0x00bf, NullPointerException -> 0x00ce, all -> 0x00dd }
        r11[r9] = r0;	 Catch:{ SQLException -> 0x00bf, NullPointerException -> 0x00ce, all -> 0x00dd }
        r9 = r9 + 1;	 Catch:{ SQLException -> 0x00bf, NullPointerException -> 0x00ce, all -> 0x00dd }
        goto L_0x008c;	 Catch:{ SQLException -> 0x00bf, NullPointerException -> 0x00ce, all -> 0x00dd }
    L_0x009e:
        r0 = 0;	 Catch:{ SQLException -> 0x00bf, NullPointerException -> 0x00ce, all -> 0x00dd }
        r0 = r11[r0];	 Catch:{ SQLException -> 0x00bf, NullPointerException -> 0x00ce, all -> 0x00dd }
        if (r0 == 0) goto L_0x00a7;	 Catch:{ SQLException -> 0x00bf, NullPointerException -> 0x00ce, all -> 0x00dd }
    L_0x00a3:
        r0 = 1;	 Catch:{ SQLException -> 0x00bf, NullPointerException -> 0x00ce, all -> 0x00dd }
        r14.dispatchPdus(r11, r0);	 Catch:{ SQLException -> 0x00bf, NullPointerException -> 0x00ce, all -> 0x00dd }
    L_0x00a7:
        r0 = r14.mSegmentedSmsCount;	 Catch:{ SQLException -> 0x00bf, NullPointerException -> 0x00ce, all -> 0x00dd }
        r0 = r0 + -1;	 Catch:{ SQLException -> 0x00bf, NullPointerException -> 0x00ce, all -> 0x00dd }
        r14.mSegmentedSmsCount = r0;	 Catch:{ SQLException -> 0x00bf, NullPointerException -> 0x00ce, all -> 0x00dd }
        r0 = r14.mResolver;	 Catch:{ SQLException -> 0x00bf, NullPointerException -> 0x00ce, all -> 0x00dd }
        r1 = mRawUri;	 Catch:{ SQLException -> 0x00bf, NullPointerException -> 0x00ce, all -> 0x00dd }
        r2 = r13.toString();	 Catch:{ SQLException -> 0x00bf, NullPointerException -> 0x00ce, all -> 0x00dd }
        r0.delete(r1, r2, r4);	 Catch:{ SQLException -> 0x00bf, NullPointerException -> 0x00ce, all -> 0x00dd }
        if (r6 == 0) goto L_0x0013;
    L_0x00ba:
        r6.close();
        goto L_0x0013;
    L_0x00bf:
        r8 = move-exception;
        r0 = "InboundSmsHandler";	 Catch:{ SQLException -> 0x00bf, NullPointerException -> 0x00ce, all -> 0x00dd }
        r1 = "can't access multipart sms database";	 Catch:{ SQLException -> 0x00bf, NullPointerException -> 0x00ce, all -> 0x00dd }
        android.telephony.Rlog.e(r0, r1, r8);	 Catch:{ SQLException -> 0x00bf, NullPointerException -> 0x00ce, all -> 0x00dd }
        if (r6 == 0) goto L_0x0013;
    L_0x00c9:
        r6.close();
        goto L_0x0013;
    L_0x00ce:
        r8 = move-exception;
        r0 = "InboundSmsHandler";	 Catch:{ SQLException -> 0x00bf, NullPointerException -> 0x00ce, all -> 0x00dd }
        r1 = "NullPointerException while handle reassemble timeout";	 Catch:{ SQLException -> 0x00bf, NullPointerException -> 0x00ce, all -> 0x00dd }
        android.telephony.Rlog.e(r0, r1, r8);	 Catch:{ SQLException -> 0x00bf, NullPointerException -> 0x00ce, all -> 0x00dd }
        if (r6 == 0) goto L_0x0013;
    L_0x00d8:
        r6.close();
        goto L_0x0013;
    L_0x00dd:
        r0 = move-exception;
        if (r6 == 0) goto L_0x00e3;
    L_0x00e0:
        r6.close();
    L_0x00e3:
        throw r0;
        */
        throw new UnsupportedOperationException("Method not decompiled: com.android.internal.telephony.InboundSmsHandler.handleReassembleTimeout(android.telephony.SmsMessage):void");
    }

    protected abstract boolean is3gpp2();

    protected abstract boolean isDuplicatedSms(SmsMessageBase smsMessageBase);

    boolean processMessagePart(com.android.internal.telephony.InboundSmsTracker r41) {
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
	at jadx.api.JadxDecompiler.processClass(JadxDecompiler.java:306)
	at jadx.api.JavaClass.decompile(JavaClass.java:62)
	at jadx.api.JadxDecompiler$1.run(JadxDecompiler.java:199)
*/
        /*
        r40 = this;
        r29 = r41.getMessageCount();
        r34 = 0;
        r34 = (byte[][]) r34;
        r22 = r41.getDestPort();
        r4 = 1;
        r0 = r29;
        if (r0 != r4) goto L_0x009d;
    L_0x0011:
        r4 = 1;
        r0 = new byte[r4][];
        r34 = r0;
        r4 = 0;
        r5 = r41.getPdu();
        r34[r4] = r5;
    L_0x001d:
        r13 = new com.android.internal.telephony.InboundSmsHandler$SmsBroadcastReceiver;
        r0 = r40;
        r1 = r41;
        r13.<init>(r1);
        r4 = 2948; // 0xb84 float:4.131E-42 double:1.4565E-320;
        r0 = r22;
        if (r0 != r4) goto L_0x02c1;
    L_0x002c:
        r32 = new java.io.ByteArrayOutputStream;
        r32.<init>();
        r16 = r34;
        r0 = r16;
        r0 = r0.length;
        r27 = r0;
        r25 = 0;
    L_0x003a:
        r0 = r25;
        r1 = r27;
        if (r0 >= r1) goto L_0x026b;
    L_0x0040:
        r33 = r16[r25];
        r4 = r41.is3gpp2();
        if (r4 != 0) goto L_0x008f;
    L_0x0048:
        r4 = "3gpp";
        r0 = r33;
        r30 = android.telephony.SmsMessage.createFromPdu(r0, r4);
        r39 = r30.getUserDataHeader();
        r0 = r40;
        r4 = r0.mCheckForDuplicatePortsInOmadmWapPush;
        if (r4 == 0) goto L_0x0265;
    L_0x005a:
        r4 = "InboundSmsHandler";
        r5 = "CheckForDuplicatePortsInOmadmWapPush";
        android.util.Log.secD(r4, r5);
        r4 = r30.getUserData();
        r0 = r40;
        r1 = r39;
        r4 = r0.checkDuplicatedOmadmPort(r4, r1);
        if (r4 == 0) goto L_0x025f;
    L_0x006f:
        r4 = r30.getUserData();
        r4 = r4.length;
        r36 = r4 + -4;
        r0 = r36;
        r0 = new byte[r0];
        r31 = r0;
        r4 = r30.getUserData();
        r5 = 4;
        r6 = 0;
        r0 = r31;
        r1 = r36;
        java.lang.System.arraycopy(r4, r5, r0, r6, r1);
        r33 = r31;
    L_0x008b:
        r33 = r30.getUserData();
    L_0x008f:
        r4 = 0;
        r0 = r33;
        r5 = r0.length;
        r0 = r32;
        r1 = r33;
        r0.write(r1, r4, r5);
        r25 = r25 + 1;
        goto L_0x003a;
    L_0x009d:
        r20 = 0;
        r15 = r41.getAddress();	 Catch:{ SQLException -> 0x0246, all -> 0x0258 }
        r4 = r41.getReferenceNumber();	 Catch:{ SQLException -> 0x0246, all -> 0x0258 }
        r37 = java.lang.Integer.toString(r4);	 Catch:{ SQLException -> 0x0246, all -> 0x0258 }
        r4 = r41.getMessageCount();	 Catch:{ SQLException -> 0x0246, all -> 0x0258 }
        r19 = java.lang.Integer.toString(r4);	 Catch:{ SQLException -> 0x0246, all -> 0x0258 }
        r4 = 3;	 Catch:{ SQLException -> 0x0246, all -> 0x0258 }
        r8 = new java.lang.String[r4];	 Catch:{ SQLException -> 0x0246, all -> 0x0258 }
        r4 = 0;	 Catch:{ SQLException -> 0x0246, all -> 0x0258 }
        r8[r4] = r15;	 Catch:{ SQLException -> 0x0246, all -> 0x0258 }
        r4 = 1;	 Catch:{ SQLException -> 0x0246, all -> 0x0258 }
        r8[r4] = r37;	 Catch:{ SQLException -> 0x0246, all -> 0x0258 }
        r4 = 2;	 Catch:{ SQLException -> 0x0246, all -> 0x0258 }
        r8[r4] = r19;	 Catch:{ SQLException -> 0x0246, all -> 0x0258 }
        r0 = r40;	 Catch:{ SQLException -> 0x0246, all -> 0x0258 }
        r4 = r0.mResolver;	 Catch:{ SQLException -> 0x0246, all -> 0x0258 }
        r5 = sRawUri;	 Catch:{ SQLException -> 0x0246, all -> 0x0258 }
        r6 = PDU_SEQUENCE_PORT_PROJECTION;	 Catch:{ SQLException -> 0x0246, all -> 0x0258 }
        r7 = "address=? AND reference_number=? AND count=?";	 Catch:{ SQLException -> 0x0246, all -> 0x0258 }
        r9 = 0;	 Catch:{ SQLException -> 0x0246, all -> 0x0258 }
        r20 = r4.query(r5, r6, r7, r8, r9);	 Catch:{ SQLException -> 0x0246, all -> 0x0258 }
        r21 = r20.getCount();	 Catch:{ SQLException -> 0x0246, all -> 0x0258 }
        r4 = "InboundSmsHandler";	 Catch:{ SQLException -> 0x0246, all -> 0x0258 }
        r5 = new java.lang.StringBuilder;	 Catch:{ SQLException -> 0x0246, all -> 0x0258 }
        r5.<init>();	 Catch:{ SQLException -> 0x0246, all -> 0x0258 }
        r6 = "cursorCount : ";	 Catch:{ SQLException -> 0x0246, all -> 0x0258 }
        r5 = r5.append(r6);	 Catch:{ SQLException -> 0x0246, all -> 0x0258 }
        r0 = r21;	 Catch:{ SQLException -> 0x0246, all -> 0x0258 }
        r5 = r5.append(r0);	 Catch:{ SQLException -> 0x0246, all -> 0x0258 }
        r5 = r5.toString();	 Catch:{ SQLException -> 0x0246, all -> 0x0258 }
        android.util.Log.secD(r4, r5);	 Catch:{ SQLException -> 0x0246, all -> 0x0258 }
        r0 = r21;	 Catch:{ SQLException -> 0x0246, all -> 0x0258 }
        r1 = r29;	 Catch:{ SQLException -> 0x0246, all -> 0x0258 }
        if (r0 >= r1) goto L_0x0149;	 Catch:{ SQLException -> 0x0246, all -> 0x0258 }
    L_0x00f2:
        r4 = CSCFEATURE_RIL_LMS_REASSEMBLE_TIMEOUTS_CTC;	 Catch:{ SQLException -> 0x0246, all -> 0x0258 }
        if (r4 == 0) goto L_0x013f;	 Catch:{ SQLException -> 0x0246, all -> 0x0258 }
    L_0x00f6:
        r4 = -1;	 Catch:{ SQLException -> 0x0246, all -> 0x0258 }
        r0 = r22;	 Catch:{ SQLException -> 0x0246, all -> 0x0258 }
        if (r0 != r4) goto L_0x0146;	 Catch:{ SQLException -> 0x0246, all -> 0x0258 }
    L_0x00fb:
        r24 = 1;	 Catch:{ SQLException -> 0x0246, all -> 0x0258 }
    L_0x00fd:
        if (r24 == 0) goto L_0x013f;	 Catch:{ SQLException -> 0x0246, all -> 0x0258 }
    L_0x00ff:
        r28 = new android.telephony.LmsTokenCTC;	 Catch:{ SQLException -> 0x0246, all -> 0x0258 }
        r4 = r41.getReferenceNumber();	 Catch:{ SQLException -> 0x0246, all -> 0x0258 }
        r5 = r41.getFormat();	 Catch:{ SQLException -> 0x0246, all -> 0x0258 }
        r0 = r28;	 Catch:{ SQLException -> 0x0246, all -> 0x0258 }
        r1 = r29;	 Catch:{ SQLException -> 0x0246, all -> 0x0258 }
        r0.<init>(r15, r4, r1, r5);	 Catch:{ SQLException -> 0x0246, all -> 0x0258 }
        r4 = "InboundSmsHandler";	 Catch:{ SQLException -> 0x0246, all -> 0x0258 }
        r5 = new java.lang.StringBuilder;	 Catch:{ SQLException -> 0x0246, all -> 0x0258 }
        r5.<init>();	 Catch:{ SQLException -> 0x0246, all -> 0x0258 }
        r6 = "lmsToken = ";	 Catch:{ SQLException -> 0x0246, all -> 0x0258 }
        r5 = r5.append(r6);	 Catch:{ SQLException -> 0x0246, all -> 0x0258 }
        r0 = r28;	 Catch:{ SQLException -> 0x0246, all -> 0x0258 }
        r5 = r5.append(r0);	 Catch:{ SQLException -> 0x0246, all -> 0x0258 }
        r5 = r5.toString();	 Catch:{ SQLException -> 0x0246, all -> 0x0258 }
        android.telephony.Rlog.d(r4, r5);	 Catch:{ SQLException -> 0x0246, all -> 0x0258 }
        r0 = r40;	 Catch:{ SQLException -> 0x0246, all -> 0x0258 }
        r4 = r0.mLmsAssemblyTracker;	 Catch:{ SQLException -> 0x0246, all -> 0x0258 }
        r0 = r28;	 Catch:{ SQLException -> 0x0246, all -> 0x0258 }
        r4 = r4.hasScheduledFirstDisplayTimeout(r0);	 Catch:{ SQLException -> 0x0246, all -> 0x0258 }
        if (r4 != 0) goto L_0x013f;	 Catch:{ SQLException -> 0x0246, all -> 0x0258 }
    L_0x0136:
        r0 = r40;	 Catch:{ SQLException -> 0x0246, all -> 0x0258 }
        r4 = r0.mLmsAssemblyTracker;	 Catch:{ SQLException -> 0x0246, all -> 0x0258 }
        r0 = r28;	 Catch:{ SQLException -> 0x0246, all -> 0x0258 }
        r4.scheduleFirstDisplayTimeout(r0);	 Catch:{ SQLException -> 0x0246, all -> 0x0258 }
    L_0x013f:
        r4 = 0;
        if (r20 == 0) goto L_0x0145;
    L_0x0142:
        r20.close();
    L_0x0145:
        return r4;
    L_0x0146:
        r24 = 0;
        goto L_0x00fd;
    L_0x0149:
        r0 = r29;	 Catch:{ SQLException -> 0x0246, all -> 0x0258 }
        r0 = new byte[r0][];	 Catch:{ SQLException -> 0x0246, all -> 0x0258 }
        r34 = r0;	 Catch:{ SQLException -> 0x0246, all -> 0x0258 }
    L_0x014f:
        r4 = r20.moveToNext();	 Catch:{ SQLException -> 0x0246, all -> 0x0258 }
        if (r4 == 0) goto L_0x018d;	 Catch:{ SQLException -> 0x0246, all -> 0x0258 }
    L_0x0155:
        r4 = 1;	 Catch:{ SQLException -> 0x0246, all -> 0x0258 }
        r0 = r20;	 Catch:{ SQLException -> 0x0246, all -> 0x0258 }
        r4 = r0.getInt(r4);	 Catch:{ SQLException -> 0x0246, all -> 0x0258 }
        r5 = r41.getIndexOffset();	 Catch:{ SQLException -> 0x0246, all -> 0x0258 }
        r26 = r4 - r5;	 Catch:{ SQLException -> 0x0246, all -> 0x0258 }
        r4 = 0;	 Catch:{ SQLException -> 0x0246, all -> 0x0258 }
        r0 = r20;	 Catch:{ SQLException -> 0x0246, all -> 0x0258 }
        r4 = r0.getString(r4);	 Catch:{ SQLException -> 0x0246, all -> 0x0258 }
        r4 = com.android.internal.util.HexDump.hexStringToByteArray(r4);	 Catch:{ SQLException -> 0x0246, all -> 0x0258 }
        r34[r26] = r4;	 Catch:{ SQLException -> 0x0246, all -> 0x0258 }
        if (r26 != 0) goto L_0x014f;	 Catch:{ SQLException -> 0x0246, all -> 0x0258 }
    L_0x0171:
        r4 = 2;	 Catch:{ SQLException -> 0x0246, all -> 0x0258 }
        r0 = r20;	 Catch:{ SQLException -> 0x0246, all -> 0x0258 }
        r4 = r0.isNull(r4);	 Catch:{ SQLException -> 0x0246, all -> 0x0258 }
        if (r4 != 0) goto L_0x014f;	 Catch:{ SQLException -> 0x0246, all -> 0x0258 }
    L_0x017a:
        r4 = 2;	 Catch:{ SQLException -> 0x0246, all -> 0x0258 }
        r0 = r20;	 Catch:{ SQLException -> 0x0246, all -> 0x0258 }
        r35 = r0.getInt(r4);	 Catch:{ SQLException -> 0x0246, all -> 0x0258 }
        r35 = com.android.internal.telephony.InboundSmsTracker.getRealDestPort(r35);	 Catch:{ SQLException -> 0x0246, all -> 0x0258 }
        r4 = -1;	 Catch:{ SQLException -> 0x0246, all -> 0x0258 }
        r0 = r35;	 Catch:{ SQLException -> 0x0246, all -> 0x0258 }
        if (r0 == r4) goto L_0x014f;	 Catch:{ SQLException -> 0x0246, all -> 0x0258 }
    L_0x018a:
        r22 = r35;	 Catch:{ SQLException -> 0x0246, all -> 0x0258 }
        goto L_0x014f;	 Catch:{ SQLException -> 0x0246, all -> 0x0258 }
    L_0x018d:
        r4 = getCDMASmsReassembly();	 Catch:{ SQLException -> 0x0246, all -> 0x0258 }
        if (r4 == 0) goto L_0x01b9;	 Catch:{ SQLException -> 0x0246, all -> 0x0258 }
    L_0x0193:
        r4 = "InboundSmsHandler";	 Catch:{ SQLException -> 0x0246, all -> 0x0258 }
        r5 = new java.lang.StringBuilder;	 Catch:{ SQLException -> 0x0246, all -> 0x0258 }
        r5.<init>();	 Catch:{ SQLException -> 0x0246, all -> 0x0258 }
        r6 = "count for segmented sms in db : ";	 Catch:{ SQLException -> 0x0246, all -> 0x0258 }
        r5 = r5.append(r6);	 Catch:{ SQLException -> 0x0246, all -> 0x0258 }
        r0 = r40;	 Catch:{ SQLException -> 0x0246, all -> 0x0258 }
        r6 = r0.mSegmentedSmsCount;	 Catch:{ SQLException -> 0x0246, all -> 0x0258 }
        r5 = r5.append(r6);	 Catch:{ SQLException -> 0x0246, all -> 0x0258 }
        r5 = r5.toString();	 Catch:{ SQLException -> 0x0246, all -> 0x0258 }
        android.util.Log.secD(r4, r5);	 Catch:{ SQLException -> 0x0246, all -> 0x0258 }
        r0 = r40;	 Catch:{ SQLException -> 0x0246, all -> 0x0258 }
        r4 = r0.mSegmentedSmsCount;	 Catch:{ SQLException -> 0x0246, all -> 0x0258 }
        r4 = r4 + -1;	 Catch:{ SQLException -> 0x0246, all -> 0x0258 }
        r0 = r40;	 Catch:{ SQLException -> 0x0246, all -> 0x0258 }
        r0.mSegmentedSmsCount = r4;	 Catch:{ SQLException -> 0x0246, all -> 0x0258 }
    L_0x01b9:
        r4 = getCDMASmsReassembly();	 Catch:{ SQLException -> 0x0246, all -> 0x0258 }
        if (r4 == 0) goto L_0x01e0;	 Catch:{ SQLException -> 0x0246, all -> 0x0258 }
    L_0x01bf:
        r4 = -1;	 Catch:{ SQLException -> 0x0246, all -> 0x0258 }
        r0 = r22;	 Catch:{ SQLException -> 0x0246, all -> 0x0258 }
        if (r0 != r4) goto L_0x01e0;	 Catch:{ SQLException -> 0x0246, all -> 0x0258 }
    L_0x01c4:
        r0 = r40;	 Catch:{ SQLException -> 0x0246, all -> 0x0258 }
        r4 = r0.mSegmentedSmsCount;	 Catch:{ SQLException -> 0x0246, all -> 0x0258 }
        if (r4 != 0) goto L_0x01e0;	 Catch:{ SQLException -> 0x0246, all -> 0x0258 }
    L_0x01ca:
        r4 = "InboundSmsHandler";	 Catch:{ SQLException -> 0x0246, all -> 0x0258 }
        r5 = "Stop reassemble";	 Catch:{ SQLException -> 0x0246, all -> 0x0258 }
        android.util.Log.secD(r4, r5);	 Catch:{ SQLException -> 0x0246, all -> 0x0258 }
        r0 = r40;	 Catch:{ SQLException -> 0x0246, all -> 0x0258 }
        r4 = r0.mHandler;	 Catch:{ SQLException -> 0x0246, all -> 0x0258 }
        r5 = 24;	 Catch:{ SQLException -> 0x0246, all -> 0x0258 }
        r0 = r40;	 Catch:{ SQLException -> 0x0246, all -> 0x0258 }
        r5 = r0.obtainMessage(r5);	 Catch:{ SQLException -> 0x0246, all -> 0x0258 }
        r4.sendMessage(r5);	 Catch:{ SQLException -> 0x0246, all -> 0x0258 }
    L_0x01e0:
        r4 = CSCFEATURE_RIL_LMS_REASSEMBLE_TIMEOUTS_CTC;	 Catch:{ SQLException -> 0x0246, all -> 0x0258 }
        if (r4 == 0) goto L_0x023f;	 Catch:{ SQLException -> 0x0246, all -> 0x0258 }
    L_0x01e4:
        r4 = 2948; // 0xb84 float:4.131E-42 double:1.4565E-320;	 Catch:{ SQLException -> 0x0246, all -> 0x0258 }
        r0 = r22;	 Catch:{ SQLException -> 0x0246, all -> 0x0258 }
        if (r0 == r4) goto L_0x023f;	 Catch:{ SQLException -> 0x0246, all -> 0x0258 }
    L_0x01ea:
        r4 = "InboundSmsHandler";	 Catch:{ SQLException -> 0x0246, all -> 0x0258 }
        r5 = new java.lang.StringBuilder;	 Catch:{ SQLException -> 0x0246, all -> 0x0258 }
        r5.<init>();	 Catch:{ SQLException -> 0x0246, all -> 0x0258 }
        r6 = "LMS receive completely : ";	 Catch:{ SQLException -> 0x0246, all -> 0x0258 }
        r5 = r5.append(r6);	 Catch:{ SQLException -> 0x0246, all -> 0x0258 }
        r6 = r41.getDeleteWhereArgs();	 Catch:{ SQLException -> 0x0246, all -> 0x0258 }
        r6 = java.util.Arrays.toString(r6);	 Catch:{ SQLException -> 0x0246, all -> 0x0258 }
        r5 = r5.append(r6);	 Catch:{ SQLException -> 0x0246, all -> 0x0258 }
        r5 = r5.toString();	 Catch:{ SQLException -> 0x0246, all -> 0x0258 }
        android.telephony.Rlog.d(r4, r5);	 Catch:{ SQLException -> 0x0246, all -> 0x0258 }
        r28 = new android.telephony.LmsTokenCTC;	 Catch:{ SQLException -> 0x0246, all -> 0x0258 }
        r4 = r41.getReferenceNumber();	 Catch:{ SQLException -> 0x0246, all -> 0x0258 }
        r5 = r41.getFormat();	 Catch:{ SQLException -> 0x0246, all -> 0x0258 }
        r0 = r28;	 Catch:{ SQLException -> 0x0246, all -> 0x0258 }
        r1 = r29;	 Catch:{ SQLException -> 0x0246, all -> 0x0258 }
        r0.<init>(r15, r4, r1, r5);	 Catch:{ SQLException -> 0x0246, all -> 0x0258 }
        r0 = r40;	 Catch:{ SQLException -> 0x0246, all -> 0x0258 }
        r4 = r0.mLmsAssemblyTracker;	 Catch:{ SQLException -> 0x0246, all -> 0x0258 }
        r0 = r28;	 Catch:{ SQLException -> 0x0246, all -> 0x0258 }
        r4.cancelFirstDisplayTimeout(r0);	 Catch:{ SQLException -> 0x0246, all -> 0x0258 }
        r0 = r40;	 Catch:{ SQLException -> 0x0246, all -> 0x0258 }
        r4 = r0.mLmsAssemblyTracker;	 Catch:{ SQLException -> 0x0246, all -> 0x0258 }
        r4.updateMaximalConnectionTimeAlarm();	 Catch:{ SQLException -> 0x0246, all -> 0x0258 }
        r4 = 0;	 Catch:{ SQLException -> 0x0246, all -> 0x0258 }
        r0 = r40;	 Catch:{ SQLException -> 0x0246, all -> 0x0258 }
        r1 = r34;	 Catch:{ SQLException -> 0x0246, all -> 0x0258 }
        r2 = r28;	 Catch:{ SQLException -> 0x0246, all -> 0x0258 }
        r3 = r41;	 Catch:{ SQLException -> 0x0246, all -> 0x0258 }
        r0.dispatchPdusCTC(r1, r2, r4, r3);	 Catch:{ SQLException -> 0x0246, all -> 0x0258 }
        r4 = 1;
        if (r20 == 0) goto L_0x0145;
    L_0x023a:
        r20.close();
        goto L_0x0145;
    L_0x023f:
        if (r20 == 0) goto L_0x001d;
    L_0x0241:
        r20.close();
        goto L_0x001d;
    L_0x0246:
        r23 = move-exception;
        r4 = "Can't access multipart SMS database";	 Catch:{ SQLException -> 0x0246, all -> 0x0258 }
        r0 = r40;	 Catch:{ SQLException -> 0x0246, all -> 0x0258 }
        r1 = r23;	 Catch:{ SQLException -> 0x0246, all -> 0x0258 }
        r0.loge(r4, r1);	 Catch:{ SQLException -> 0x0246, all -> 0x0258 }
        r4 = 0;
        if (r20 == 0) goto L_0x0145;
    L_0x0253:
        r20.close();
        goto L_0x0145;
    L_0x0258:
        r4 = move-exception;
        if (r20 == 0) goto L_0x025e;
    L_0x025b:
        r20.close();
    L_0x025e:
        throw r4;
    L_0x025f:
        r33 = r30.getUserData();
        goto L_0x008b;
    L_0x0265:
        r33 = r30.getUserData();
        goto L_0x008b;
    L_0x026b:
        r0 = r40;
        r4 = r0.mWapPush;
        r5 = r41.getAddress();
        r4.dispatchWapPushAddress(r5);
        r0 = r40;
        r4 = r0.mWapPush;
        r5 = r32.toByteArray();
        r0 = r40;
        r38 = r4.dispatchWapPdu(r5, r13, r0);
        r4 = new java.lang.StringBuilder;
        r4.<init>();
        r5 = "dispatchWapPdu() returned ";
        r4 = r4.append(r5);
        r0 = r38;
        r4 = r4.append(r0);
        r4 = r4.toString();
        r0 = r40;
        r0.log(r4);
        r4 = -1;
        r0 = r38;
        if (r0 == r4) goto L_0x02b6;
    L_0x02a3:
        r4 = r41.getDeleteWhere();
        r5 = r41.getDeleteWhereArgs();
        r0 = r40;
        r0.deleteFromRawTable(r4, r5);
        r4 = 4;
        r0 = r40;
        r0.sendMessage(r4);
    L_0x02b6:
        r4 = -1;
        r0 = r38;
        if (r0 != r4) goto L_0x02be;
    L_0x02bb:
        r4 = 1;
        goto L_0x0145;
    L_0x02be:
        r4 = 0;
        goto L_0x0145;
    L_0x02c1:
        r4 = -1;
        r0 = r22;
        if (r0 != r4) goto L_0x02cb;
    L_0x02c6:
        r4 = 1;
        r0 = r29;
        if (r0 <= r4) goto L_0x02cb;
    L_0x02cb:
        r10 = new android.content.Intent;
        r4 = "android.provider.Telephony.SMS_FILTER";
        r10.<init>(r4);
        r18 = 0;
        r4 = com.android.internal.telephony.uicc.UiccController.getInstance();
        r17 = r4.getUiccCard();
        if (r17 == 0) goto L_0x02ec;
    L_0x02de:
        r0 = r40;
        r4 = r0.mContext;
        r4 = r4.getPackageManager();
        r0 = r17;
        r18 = r0.getCarrierPackageNamesForIntent(r4, r10);
    L_0x02ec:
        if (r18 == 0) goto L_0x0326;
    L_0x02ee:
        r4 = r18.size();
        r5 = 1;
        if (r4 != r5) goto L_0x0326;
    L_0x02f5:
        r4 = 0;
        r0 = r18;
        r4 = r0.get(r4);
        r4 = (java.lang.String) r4;
        r10.setPackage(r4);
        r4 = "destport";
        r0 = r22;
        r10.putExtra(r4, r0);
    L_0x0308:
        r4 = "pdus";
        r0 = r34;
        r10.putExtra(r4, r0);
        r4 = "format";
        r5 = r41.getFormat();
        r10.putExtra(r4, r5);
        r11 = "android.permission.RECEIVE_SMS";
        r12 = 16;
        r14 = android.os.UserHandle.OWNER;
        r9 = r40;
        r9.dispatchIntent(r10, r11, r12, r13, r14);
        r4 = 1;
        goto L_0x0145;
    L_0x0326:
        r0 = r40;
        r1 = r22;
        r0.setAndDirectIntent(r10, r1);
        goto L_0x0308;
        */
        throw new UnsupportedOperationException("Method not decompiled: com.android.internal.telephony.InboundSmsHandler.processMessagePart(com.android.internal.telephony.InboundSmsTracker):boolean");
    }

    protected InboundSmsHandler(String name, Context context, SmsStorageMonitor storageMonitor, PhoneBase phone, CellBroadcastHandler cellBroadcastHandler) {
        boolean z = false;
        super(name);
        this.mContext = context;
        this.mStorageMonitor = storageMonitor;
        this.mPhone = phone;
        this.mCi = phone.mCi;
        this.mCellBroadcastHandler = cellBroadcastHandler;
        this.mResolver = context.getContentResolver();
        this.mWapPush = new WapPushOverSms(context);
        if (!SystemProperties.getBoolean("telephony.sms.receive", this.mContext.getResources().getBoolean(17956934))) {
            z = true;
        }
        this.mSmsReceiveDisabled = z;
        this.mWakeLock = ((PowerManager) this.mContext.getSystemService("power")).newWakeLock(1, name);
        this.mWakeLock.acquire();
        this.mUserManager = (UserManager) this.mContext.getSystemService(Carriers.USER);
        addState(this.mDefaultState);
        addState(this.mStartupState, this.mDefaultState);
        addState(this.mIdleState, this.mDefaultState);
        addState(this.mDeliveringState, this.mDefaultState);
        addState(this.mWaitingState, this.mDeliveringState);
        setInitialState(this.mStartupState);
        this.mContext.registerReceiver(this.mBlockedSmsMmsReceiver, new IntentFilter("com.android.server.enterprise.restriction.SEND_BLOCKED_SMS"), "android.permission.sec.RECEIVE_BLOCKED_SMS_MMS", null);
        this.mContext.registerReceiver(this.mBlockedSmsMmsReceiver, new IntentFilter("com.android.server.enterprise.restriction.SEND_BLOCKED_MMS"), "android.permission.sec.RECEIVE_BLOCKED_SMS_MMS", null);
        if (CSCFEATURE_RIL_LMS_REASSEMBLE_TIMEOUTS_CTC) {
            this.mLmsAssemblyTracker = new LmsAssemblyTrackerCTC(this.mContext, this);
        } else {
            this.mLmsAssemblyTracker = null;
        }
        if ("".equals("ATT")) {
            this.mIQClient = new IQClient();
        }
        log("created InboundSmsHandler");
    }

    public void dispose() {
        quit();
    }

    public void updatePhoneObject(PhoneBase phone) {
        sendMessage(7, phone);
    }

    protected void onQuitting() {
        this.mWapPush.dispose();
        while (this.mWakeLock.isHeld()) {
            this.mWakeLock.release();
        }
    }

    public PhoneBase getPhone() {
        return this.mPhone;
    }

    void handleNewSms(AsyncResult ar) {
        boolean handled = true;
        if (ar.exception != null) {
            loge("Exception processing incoming SMS: " + ar.exception);
            AuditLog.log(5, 5, false, Process.myPid(), getClass().getSimpleName(), "Receiving SMS failed.");
        } else if (isDeviceEncryptionOngoing()) {
            notifyAndAcknowledgeLastIncomingSms(false, 2, null);
            AuditLog.log(5, 5, false, Process.myPid(), getClass().getSimpleName(), "Receiving SMS failed.");
        } else {
            if (SystemProperties.get("ril.sms.gcf-mode").equals("On")) {
                gcf_flag = true;
            } else {
                gcf_flag = false;
            }
            if (ar.exception != null) {
                Log.e(TAG, "Exception processing incoming SMS. Exception:" + ar.exception);
                AuditLog.log(5, 5, false, Process.myPid(), getClass().getSimpleName(), "Receiving SMS failed.");
                return;
            }
            int result;
            SmsMessage sms = null;
            try {
                sms = (SmsMessage) ar.result;
                if (!"".equals("ATT")) {
                    result = dispatchMessage(sms.mWrappedSmsMessage);
                } else if (this.mIQClient.checkSMS(sms.getMessageBody())) {
                    result = 1;
                } else {
                    result = dispatchMessage(sms.mWrappedSmsMessage);
                }
            } catch (RuntimeException ex) {
                loge("Exception dispatching message", ex);
                result = 2;
            }
            if (result == 10) {
                result = 1;
            } else {
                accountSMStoMDM(result, sms.mWrappedSmsMessage);
            }
            if (result != -1) {
                if (result == 2) {
                    AuditLog.log(5, 5, false, Process.myPid(), getClass().getSimpleName(), "Receiving SMS failed.");
                }
                if (result == 8) {
                    notifyAndAcknowledgeLastIncomingSms(true, 1, null);
                    return;
                }
                if (result == 9) {
                    this.mHandler.sendMessageDelayed(this.mHandler.obtainMessage(23, ar), 300000);
                    result = 1;
                }
                if (result != 1) {
                    handled = false;
                }
                notifyAndAcknowledgeLastIncomingSms(handled, result, null);
            }
        }
    }

    protected void accountSMStoMDM(int result, SmsMessageBase smsb) {
        if (result != 2 && smsb != null) {
            SmsHeader smsHeader = smsb.getUserDataHeader();
            boolean isWapPush = false;
            if (!(smsHeader == null || smsHeader.portAddrs == null || SmsHeader.PORT_WAP_PUSH != smsHeader.portAddrs.destPort)) {
                isWapPush = true;
            }
            if (smsb.getMessageBody() != null && !isWapPush) {
                storeSMS(smsb.getOriginatingAddress(), Long.toString(smsb.getTimestampMillis()), smsb.getMessageBody(), true);
                PhoneRestrictionPolicy edm = EnterpriseDeviceManager.getInstance().getPhoneRestrictionPolicy();
                if (edm != null && edm.isLimitNumberOfSmsEnabled()) {
                    edm.addNumberOfIncomingSms();
                }
            }
        }
    }

    void handleInjectSms(AsyncResult ar) {
        int result;
        PendingIntent receivedIntent = null;
        try {
            receivedIntent = (PendingIntent) ar.userObj;
            SmsMessage sms = ar.result;
            if (sms == null) {
                result = 2;
            } else {
                result = dispatchMessage(sms.mWrappedSmsMessage);
            }
        } catch (RuntimeException ex) {
            loge("Exception dispatching message", ex);
            result = 2;
        }
        if (receivedIntent != null) {
            try {
                receivedIntent.send(result);
            } catch (CanceledException e) {
            }
        }
    }

    public int dispatchMessage(SmsMessageBase smsb) {
        if (smsb == null) {
            loge("dispatchSmsMessage: message is null");
            return 2;
        } else if (!this.mSmsReceiveDisabled) {
            return dispatchMessageRadioSpecific(smsb);
        } else {
            log("Received short message on device which doesn't support receiving SMS. Ignored.");
            return 1;
        }
    }

    protected void onUpdatePhoneObject(PhoneBase phone) {
        this.mPhone = phone;
        this.mStorageMonitor = this.mPhone.mSmsStorageMonitor;
        log("onUpdatePhoneObject: phone=" + this.mPhone.getClass().getSimpleName());
    }

    void notifyAndAcknowledgeLastIncomingSms(boolean success, int result, Message response) {
        if (!(success || this.misWapPush)) {
            Intent intent = new Intent(Intents.SMS_REJECTED_ACTION);
            intent.putExtra("result", result);
            this.mContext.sendBroadcast(intent, "android.permission.RECEIVE_SMS");
        }
        this.misWapPush = false;
        acknowledgeLastIncomingSms(success, result, response);
    }

    protected int dispatchNormalMessage(SmsMessageBase sms) {
        SmsHeader smsHeader = sms.getUserDataHeader();
        if (isDuplicatedSms(sms)) {
            Log.secE(TAG, "Discard duplicated message.");
            acknowledgeLastIncomingSms(true, 1, null);
            return -1;
        }
        InboundSmsTracker tracker;
        if (CscFeature.getInstance().getEnableStatus("CscFeature_Common_EnableSprintExtension")) {
            String msgbody = sms.getDisplayMessageBody();
            if (msgbody == null) {
                msgbody = SmsMessage.createFromPdu(sms.getPdu()).getMessageBody();
            }
            if (msgbody != null) {
                if (msgbody.startsWith("//ANDROID")) {
                    if (msgbody.endsWith("//CM")) {
                        Log.secD(TAG, "message body starts with //ANDROID and ends with //CM");
                        String VVM_PACKAGE_NAME = "com.coremobility.app.vnotes";
                        dispatchAppSMSforSPR("com.coremobility.app.vnotes", new byte[][]{sms.getPdu()});
                        return 1;
                    }
                }
            }
        }
        int destPort;
        if (smsHeader == null || smsHeader.concatRef == null) {
            new byte[1][][0] = sms.getPdu();
            destPort = -1;
            if (!(smsHeader == null || smsHeader.portAddrs == null)) {
                destPort = smsHeader.portAddrs.destPort;
                log("destination port: " + destPort);
            }
            tracker = new InboundSmsTracker(sms.getPdu(), sms.getTimestampMillis(), destPort, is3gpp2(), sms.getOriginatingAddress(), false);
        } else {
            ConcatRef concatRef = smsHeader.concatRef;
            PortAddrs portAddrs = smsHeader.portAddrs;
            destPort = portAddrs != null ? portAddrs.destPort : -1;
            if (getCDMASmsReassembly()) {
                boolean isfirstMessage = false;
                StringBuilder stringBuilder = new StringBuilder("count =");
                stringBuilder.append(concatRef.msgCount);
                stringBuilder.append(" AND address = ?");
                Cursor cursor = null;
                try {
                    cursor = this.mResolver.query(mRawUri, RAW_PROJECTION, stringBuilder.toString(), new String[]{sms.getOriginatingAddress()}, null);
                    int cursorCount = cursor.getCount();
                    if (cursorCount != 0) {
                        while (cursor.moveToNext()) {
                            if (((int) cursor.getLong(cursor.getColumnIndex("sequence"))) == concatRef.seqNumber) {
                                Log.e(TAG, "Same sequence number is exist. total : " + concatRef.msgCount + " seq :" + concatRef.seqNumber);
                                isfirstMessage = true;
                                break;
                            }
                        }
                    }
                    if (cursorCount == 0 || isfirstMessage) {
                        smsHeader.concatRef.refNumber = getNextReassembleRef();
                        log("new segment : [ " + smsHeader.concatRef.refNumber + " ]");
                        this.mSegmentedSmsCount++;
                        addTrackerToRawTableAndSendMessage(new InboundSmsTracker(sms.getPdu(), sms.getTimestampMillis(), destPort, is3gpp2(), sms.getOriginatingAddress(), concatRef.refNumber, concatRef.seqNumber, concatRef.msgCount, false));
                        if (cursor == null) {
                            return 9;
                        }
                        cursor.close();
                        return 9;
                    }
                    log("it's not first segment");
                    cursor.moveToFirst();
                    int refNum = (int) cursor.getLong(cursor.getColumnIndex("reference_number"));
                    Log.secD(TAG, "refNum: " + refNum);
                    concatRef.refNumber = refNum;
                } finally {
                    if (cursor != null) {
                        cursor.close();
                    }
                }
            }
            InboundSmsTracker inboundSmsTracker = new InboundSmsTracker(sms.getPdu(), sms.getTimestampMillis(), destPort, is3gpp2(), sms.getOriginatingAddress(), concatRef.refNumber, concatRef.seqNumber, concatRef.msgCount, false);
        }
        return addTrackerToRawTableAndSendMessage(tracker);
    }

    protected int addTrackerToRawTableAndSendMessage(InboundSmsTracker tracker) {
        switch (addTrackerToRawTable(tracker)) {
            case 1:
                sendMessage(2, tracker);
                return 1;
            case 5:
                return 1;
            default:
                return 2;
        }
    }

    protected void dispatchIntent(Intent intent, String permission, int appOp, BroadcastReceiver resultReceiver, UserHandle user) {
        intent.addFlags(134217728);
        SubscriptionManager.putPhoneIdAndSubIdExtra(intent, this.mPhone.getPhoneId());
        if (user.equals(UserHandle.ALL)) {
            int[] users = null;
            try {
                users = ActivityManagerNative.getDefault().getRunningUserIds();
            } catch (RemoteException e) {
            }
            if (users == null) {
                users = new int[]{user.getIdentifier()};
            }
            for (int i = users.length - 1; i >= 0; i--) {
                UserHandle targetUser = new UserHandle(users[i]);
                if (users[i] != 0) {
                    if (!this.mUserManager.hasUserRestriction("no_sms", targetUser)) {
                        UserInfo info = this.mUserManager.getUserInfo(users[i]);
                        if (info != null) {
                            if (info.isManagedProfile()) {
                            }
                        }
                    }
                }
                this.mContext.sendOrderedBroadcastAsUser(intent, targetUser, permission, appOp, users[i] == 0 ? resultReceiver : null, getHandler(), -1, null, null);
            }
            return;
        }
        this.mContext.sendOrderedBroadcastAsUser(intent, user, permission, appOp, resultReceiver, getHandler(), -1, null, null);
    }

    void deleteFromRawTable(String deleteWhere, String[] deleteWhereArgs) {
        int rows = this.mResolver.delete(sRawUri, deleteWhere, deleteWhereArgs);
        if (rows == 0) {
            loge("No rows were deleted from raw table!");
        } else {
            log("Deleted " + rows + " rows from raw table.");
        }
    }

    void setAndDirectIntent(Intent intent, int destPort) {
        if (destPort == -1) {
            intent.setAction(Intents.SMS_DELIVER_ACTION);
            ComponentName componentName = SmsApplication.getDefaultSmsApplication(this.mContext, true);
            if (componentName != null) {
                intent.setComponent(componentName);
                log("Delivering SMS to: " + componentName.getPackageName() + " " + componentName.getClassName());
                return;
            }
            intent.setComponent(null);
            return;
        }
        intent.setAction(Intents.DATA_SMS_RECEIVED_ACTION);
        intent.setData(Uri.parse("sms://localhost:" + destPort));
        intent.setComponent(null);
    }

    static boolean isCurrentFormat3gpp2() {
        return 2 == TelephonyManager.getDefault().getCurrentPhoneType();
    }

    protected void log(String s) {
        Rlog.d(getName(), s);
    }

    protected void loge(String s) {
        Rlog.e(getName(), s);
    }

    protected void loge(String s, Throwable e) {
        Rlog.e(getName(), s, e);
    }

    private Uri writeInboxMessage(Intent intent) {
        Uri uri = null;
        SmsMessage[] messages = Intents.getMessagesFromIntent(intent);
        if (messages == null || messages.length < 1) {
            loge("Failed to parse SMS pdu");
        } else {
            SmsMessage[] arr$ = messages;
            int len$ = arr$.length;
            int i$ = 0;
            while (i$ < len$) {
                try {
                    arr$[i$].getDisplayMessageBody();
                    i$++;
                } catch (NullPointerException e) {
                    loge("NPE inside SmsMessage");
                }
            }
            ContentValues values = parseSmsMessage(messages);
            long identity = Binder.clearCallingIdentity();
            try {
                uri = this.mContext.getContentResolver().insert(Inbox.CONTENT_URI, values);
            } catch (Exception e2) {
                loge("Failed to persist inbox message", e2);
            } finally {
                Binder.restoreCallingIdentity(identity);
            }
        }
        return uri;
    }

    private static ContentValues parseSmsMessage(SmsMessage[] msgs) {
        int i = 0;
        SmsMessage sms = msgs[0];
        ContentValues values = new ContentValues();
        values.put("address", sms.getDisplayOriginatingAddress());
        values.put("body", buildMessageBodyFromPdus(msgs));
        values.put("date_sent", Long.valueOf(sms.getTimestampMillis()));
        values.put("date", Long.valueOf(System.currentTimeMillis()));
        values.put("protocol", Integer.valueOf(sms.getProtocolIdentifier()));
        values.put("seen", Integer.valueOf(0));
        values.put("read", Integer.valueOf(0));
        String subject = sms.getPseudoSubject();
        if (!TextUtils.isEmpty(subject)) {
            values.put(TextBasedSmsColumns.SUBJECT, subject);
        }
        String str = TextBasedSmsColumns.REPLY_PATH_PRESENT;
        if (sms.isReplyPathPresent()) {
            i = 1;
        }
        values.put(str, Integer.valueOf(i));
        values.put(TextBasedSmsColumns.SERVICE_CENTER, sms.getServiceCenterAddress());
        return values;
    }

    private static String buildMessageBodyFromPdus(SmsMessage[] msgs) {
        if (msgs.length == 1) {
            return replaceFormFeeds(msgs[0].getDisplayMessageBody());
        }
        StringBuilder body = new StringBuilder();
        for (SmsMessage msg : msgs) {
            body.append(msg.getDisplayMessageBody());
        }
        return replaceFormFeeds(body.toString());
    }

    private static String replaceFormFeeds(String s) {
        return s == null ? "" : s.replace('\f', '\n');
    }

    protected boolean isDuplicatedLatestSms(String address, long timestamp, boolean iscdma) {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this.mContext);
        String latestAddress = sp.getString(this.mLatestSmsAddress, null);
        long latestTimestamp = sp.getLong(this.mLatestSmsTimestamp, 0);
        String latestType = sp.getString(this.mLatestSmsType, null);
        if (!(iscdma && SmsMessage.FORMAT_3GPP2.equals(latestType)) && (iscdma || !SmsMessage.FORMAT_3GPP.equals(latestType))) {
            boolean addrMatched = address.equals(latestAddress);
            boolean timeMatched = timestamp == latestTimestamp;
            Log.d(TAG, " isDuplicatedLatestSms()->latestAddress value:  " + latestAddress + " address value:  " + address + " latestTimestamp value: " + latestTimestamp + "  timestamp value:   " + timestamp);
            if (addrMatched && timeMatched) {
                return true;
            }
            return false;
        }
        Log.d(TAG, " isDuplicatedLatestSms() Same smsType!!iscdma = " + iscdma);
        return false;
    }

    protected void storeLatestSmsInfo(String address, long timestamp, boolean iscdma) {
        Log.e(TAG, " Storing latest sms information:  for timestamp = " + timestamp + " address = " + address + " in preferences.");
        Editor editor = PreferenceManager.getDefaultSharedPreferences(this.mContext).edit();
        editor.putString(this.mLatestSmsAddress, address);
        editor.putLong(this.mLatestSmsTimestamp, timestamp);
        if (iscdma) {
            editor.putString(this.mLatestSmsType, SmsMessage.FORMAT_3GPP2);
        } else {
            editor.putString(this.mLatestSmsType, SmsMessage.FORMAT_3GPP);
        }
        editor.commit();
    }

    protected void setCarrierLockEnabled(String mode) {
        FileNotFoundException ex;
        IOException e;
        BufferedWriter bufW = null;
        try {
            File folder = new File(SKT_CARRIERLOCK_MODE_FOLDER);
            if (!folder.exists()) {
                boolean status = folder.mkdirs();
                folder.setReadable(true, false);
                folder.setWritable(true, true);
                folder.setExecutable(true, false);
                Log.secD(TAG, "make folder /efs/sms  directory creation status: " + status);
            }
            File f = new File(SKT_CARRIERLOCK_MODE_FILE);
            if (!f.exists()) {
                f.createNewFile();
                Log.secD(TAG, "make /efs/sms/sktcarrierlockmode");
                f.setReadable(true, false);
            }
        } catch (FileNotFoundException ex2) {
            Log.secE(TAG, "FileNotFoundException : " + ex2);
        } catch (IOException e2) {
            Log.secE(TAG, "IOException : " + e2);
        }
        try {
            BufferedWriter bufW2 = new BufferedWriter(new FileWriter(SKT_CARRIERLOCK_MODE_FILE));
            try {
                bufW2.write(mode);
                bufW2.flush();
                Log.secE(TAG, "bufW.write + " + mode);
                bufW2.close();
                bufW = bufW2;
            } catch (FileNotFoundException e3) {
                ex2 = e3;
                bufW = bufW2;
                if (bufW != null) {
                    try {
                        bufW.close();
                    } catch (IOException e1) {
                        e1.printStackTrace();
                    }
                }
                Log.secE(TAG, "FileNotFoundException : " + ex2);
            } catch (IOException e4) {
                e2 = e4;
                bufW = bufW2;
                if (bufW != null) {
                    try {
                        bufW.close();
                    } catch (IOException e12) {
                        e12.printStackTrace();
                    }
                }
                Log.secE(TAG, "IOException : " + e2);
            }
        } catch (FileNotFoundException e5) {
            ex2 = e5;
            if (bufW != null) {
                bufW.close();
            }
            Log.secE(TAG, "FileNotFoundException : " + ex2);
        } catch (IOException e6) {
            e2 = e6;
            if (bufW != null) {
                bufW.close();
            }
            Log.secE(TAG, "IOException : " + e2);
        }
    }

    protected boolean isSMSBlocked(String phoneNumber, boolean send) {
        boolean result = false;
        PhoneRestrictionPolicy restrictionPolicy = EnterpriseDeviceManager.getInstance().getPhoneRestrictionPolicy();
        if (restrictionPolicy != null) {
            result = restrictionPolicy.getEmergencyCallOnly(true);
            if (!result) {
                if (!send) {
                    result = (restrictionPolicy.isIncomingSmsAllowed() && restrictionPolicy.canIncomingSms(phoneNumber)) ? false : true;
                } else if (restrictionPolicy.isOutgoingSmsAllowed() && restrictionPolicy.canOutgoingSms(phoneNumber)) {
                    result = false;
                } else {
                    result = true;
                }
            }
        }
        log("isSMSBlocked=" + result);
        if (result) {
            AuditLog.log(5, 5, false, Process.myPid(), getClass().getSimpleName(), "Receiving sms failed. Blocked by phone restriction policy.");
        }
        return result;
    }

    protected void storeSMS(String address, String timeStamp, String message, boolean isInbound) {
        DeviceInventory deviceInventory = EnterpriseDeviceManager.getInstance().getDeviceInventory();
        PhoneRestrictionPolicy phoneRestriction = EnterpriseDeviceManager.getInstance().getPhoneRestrictionPolicy();
        if (!isInbound && phoneRestriction.isLimitNumberOfSmsEnabled()) {
            phoneRestriction.addNumberOfOutgoingSms();
        }
        if (deviceInventory != null && deviceInventory.isSMSCaptureEnabled()) {
            deviceInventory.storeSMS(address, timeStamp, message, isInbound);
        }
        AuditLog.log(5, 5, true, Process.myPid(), getClass().getSimpleName(), "Receiving sms succeeded.");
    }

    protected void handleBlockedSms(byte[] pdu, int sendType) {
        log("handleBlockedSms() - Default implementation");
    }

    protected void dispatchBlockedSms(byte[] pdu, int sendType) {
        SmsMessage sms = SmsMessage.createFromPdu(pdu);
        PhoneRestrictionPolicy phoneRestriction = EnterpriseDeviceManager.getInstance().getPhoneRestrictionPolicy();
        if (phoneRestriction.isBlockSmsWithStorageEnabled()) {
            phoneRestriction.storeBlockedSmsMms(true, pdu, sms.mWrappedSmsMessage.getDisplayOriginatingAddress(), sendType, null);
        } else if (dispatchNormalMessage(sms.mWrappedSmsMessage) != 2) {
            if (phoneRestriction.isLimitNumberOfSmsEnabled()) {
                log("update number of incoming smss");
                phoneRestriction.addNumberOfIncomingSms();
            }
            if (sms.mWrappedSmsMessage.getMessageBody() != null) {
                storeSMS(sms.mWrappedSmsMessage.getOriginatingAddress(), Long.toString(sms.mWrappedSmsMessage.getTimestampMillis()), sms.mWrappedSmsMessage.getMessageBody(), true);
            }
        }
    }

    protected void dispatchPdusCTC(byte[][] pdus, LmsTokenCTC lmsToken, int lmsStatus, InboundSmsTracker tracker) {
        Intent intent = new Intent();
        Rlog.d(TAG, "dispatchPdusCTC lmsToken : " + lmsToken + " lmsStatus : " + lmsStatus);
        switch (lmsStatus) {
            case 1:
                intent.setAction(Intents.LMS_FIRST_DISPLAY_TIMEOUT_CTC_ACTION);
                break;
            case 2:
                intent.setAction(Intents.LMS_MAXIMAL_CONNECTION_TIMEOUT_CTC_ACTION);
                break;
            default:
                intent.setAction(Intents.SMS_DELIVER_ACTION);
                break;
        }
        intent.putExtra("pdus", pdus);
        if (pdus.length > 1) {
            intent.putExtra(Intents.EXTRA_LMS_TOKEN_CTC, lmsToken);
        }
        Rlog.d(TAG, "dispatchPdusCTC simSlot : " + this.mPhone.getPhoneId());
        SubscriptionManager.putPhoneIdAndSubIdExtra(intent, this.mPhone.getPhoneId());
        intent.putExtra(CellBroadcasts.MESSAGE_FORMAT, lmsToken.format);
        if (lmsStatus != 0) {
            dispatchIntent(intent, "android.permission.RECEIVE_SMS", 16, null, UserHandle.OWNER);
        } else if (tracker != null) {
            BroadcastReceiver resultReceiver = new SmsBroadcastReceiver(tracker);
            ComponentName componentName = SmsApplication.getDefaultSmsApplication(this.mContext, true);
            if (componentName != null) {
                intent.setComponent(componentName);
                log("Delivering SMS to: " + componentName.getPackageName() + " " + componentName.getClassName());
            }
            dispatchIntent(intent, "android.permission.RECEIVE_SMS", 16, resultReceiver, UserHandle.OWNER);
        }
    }

    protected boolean isDeviceEncryptionOngoing() {
        if (!"trigger_restart_min_framework".equals(SystemProperties.get("vold.decrypt")) && !"1".equals(SystemProperties.get("vold.decrypt"))) {
            return false;
        }
        Log.secE(TAG, "On Encryption");
        return true;
    }

    public static boolean getCDMASmsReassembly() {
        boolean onOff = SystemProperties.getBoolean("ril.sms.reassembly", false);
        Log.secD(TAG, "getCDMASmsReassembly = " + onOff);
        return onOff;
    }

    protected static int getNextReassembleRef() {
        sReassembleRef++;
        return sReassembleRef;
    }

    protected boolean checkDuplicatedOmadmPort(byte[] userData, SmsHeader smsHeader) {
        if (!(smsHeader == null || smsHeader.portAddrs == null || smsHeader.concatRef == null || smsHeader.portAddrs.destPort != SmsHeader.PORT_WAP_PUSH || smsHeader.concatRef.seqNumber != 1)) {
            byte[] garbageData = new byte[4];
            System.arraycopy(userData, 0, garbageData, 0, 4);
            if (!smsHeader.portAddrs.areEightBits) {
                int oPort = ((garbageData[0] & 255) << 8) | (garbageData[1] & 255);
                int dPort = ((garbageData[2] & 255) << 8) | (garbageData[3] & 255);
                Rlog.d(TAG, "dPort : " + dPort + " oPort : " + oPort);
                Rlog.d(TAG, "destPort : " + smsHeader.portAddrs.destPort + " origPort : " + smsHeader.portAddrs.origPort);
                if (dPort == smsHeader.portAddrs.destPort && oPort == smsHeader.portAddrs.origPort) {
                    return true;
                }
            }
        }
        return false;
    }

    protected void updateVoicemailCount(int unReadCount) {
        String PACKAGE_NAME = Intents.EXTRA_PACKAGE_NAME;
        String CLASS_NAME = "class";
        String BADGECOUNT_NAME = "badgecount";
        String VVM_PACKAGE = "com.samsung.vvm";
        String VVM_LAUNCHER_CLASS = "com.samsung.vvm.vvmapp.VVMApplication";
        String BADGE_URI = "content://com.sec.badge/apps";
        if ("VZW-CDMA".equals("") && "true".equals(SystemProperties.get("ro.HorizontalVVM", "false"))) {
            try {
                ContentResolver contentResolver = this.mContext.getContentResolver();
                ContentValues newValues = new ContentValues();
                newValues.put(Intents.EXTRA_PACKAGE_NAME, "com.samsung.vvm");
                newValues.put("class", "com.samsung.vvm.vvmapp.VVMApplication");
                newValues.put("badgecount", Integer.valueOf(unReadCount));
                contentResolver.update(Uri.parse("content://com.sec.badge/apps"), newValues, "package='com.samsung.vvm' AND class='com.samsung.vvm.vvmapp.VVMApplication'", null);
                Log.d(TAG, "Updating Unread count badge: " + unReadCount);
            } catch (Exception e) {
                Log.e(TAG, "Excecption for upgrading Badge count");
                e.printStackTrace();
            }
        }
    }

    protected void dispatchPdus(byte[][] pdus) {
        Intent intent = new Intent(Intents.SMS_RECEIVED_ACTION);
        intent.putExtra("pdus", pdus);
        intent.putExtra(CellBroadcasts.MESSAGE_FORMAT, getFormat());
        dispatchIntent(intent, "android.permission.RECEIVE_SMS", 16, null, UserHandle.OWNER);
    }

    protected void dispatchPdus(byte[][] pdus, boolean reassembleTimeout) {
        Intent intent = new Intent(Intents.SMS_DELIVER_ACTION);
        intent.putExtra("pdus", pdus);
        intent.putExtra(CellBroadcasts.MESSAGE_FORMAT, getFormat());
        intent.putExtra("reassembleTimeout", reassembleTimeout);
    }

    protected void dispatchAppSMSforSPR(String packageName, byte[][] pdus) {
        Intent intent = new Intent(Intents.SMS_RECEIVED_ACTION);
        intent.setFlags(32);
        intent.putExtra("pdus", pdus);
        intent.putExtra(CellBroadcasts.MESSAGE_FORMAT, getFormat());
        intent.setPackage(packageName);
        this.mContext.sendBroadcast(intent, "android.permission.RECEIVE_SMS");
    }

    protected void dispatchAppDirectedSMS(ComponentName componentName, String appdirectedSMS, String appPrefix, String originAddress) {
        Log.secD(TAG, "dispatchAppDirectedSMS" + componentName + "/" + appdirectedSMS + "/" + appPrefix);
        Intent intent = new Intent(Intents.DIRECTED_SMS_RECEIVED_ACTION);
        intent.setFlags(268435488);
        intent.setComponent(componentName);
        intent.putExtra("parameters", appdirectedSMS);
        intent.putExtra("originator", originAddress);
        intent.putExtra("prefix", appPrefix);
        this.mContext.sendBroadcast(intent);
    }

    protected void dispatchPortAddressedPdus(byte[][] pdus, int port) {
        Intent intent = new Intent(Intents.DATA_SMS_RECEIVED_ACTION, Uri.parse("sms://localhost:" + port));
        intent.putExtra("pdus", pdus);
        intent.putExtra(CellBroadcasts.MESSAGE_FORMAT, getFormat());
        dispatchIntent(intent, "android.permission.RECEIVE_SMS", 16, null, UserHandle.OWNER);
    }

    protected void dispatchSKTFOTAPortAddressedPdus(byte[] rawPdu) {
        Rlog.d(TAG, "sms dispatchSKTFOTAPortAddressedPdus" + rawPdu);
        Intent intentWapPush = new Intent(Intents.WAP_PUSH_DM_NOTI_RECEIVED_ACTION);
        Rlog.i(TAG, "android.provider.Telephony.WAP_PUSH_DM_NOTI_RECEIVED is sent");
        intentWapPush.putExtra("DMAGENT", 160);
        intentWapPush.putExtra("pdus", rawPdu);
        intentWapPush.addFlags(32);
        this.mContext.sendBroadcast(intentWapPush);
    }

    protected boolean dispatchSKTAndroidCommonSMSPushPdus(byte[][] pdus) {
        SmsMessage[] msgs = new SmsMessage[pdus.length];
        StringBuilder body = new StringBuilder();
        for (int i = 0; i < pdus.length; i++) {
            if (-1 != getEncoding()) {
                msgs[i] = SmsMessage.createFromPdu(pdus[i], getEncoding());
            } else {
                msgs[i] = SmsMessage.createFromPdu(pdus[i]);
            }
            body.append(msgs[i].getMessageBody());
        }
        String SKTCommonPushData = body.toString();
        int dlmtIndex = SKTCommonPushData.indexOf(11, 0);
        this.mUI = SKTCommonPushData.substring(0, dlmtIndex);
        int startIndex = dlmtIndex + 1;
        dlmtIndex = SKTCommonPushData.indexOf(11, startIndex);
        this.mApplicationName = SKTCommonPushData.substring(startIndex, dlmtIndex);
        startIndex = dlmtIndex + 1;
        dlmtIndex = SKTCommonPushData.indexOf(11, startIndex);
        this.mCommand = SKTCommonPushData.substring(startIndex, dlmtIndex);
        startIndex = dlmtIndex + 1;
        dlmtIndex = SKTCommonPushData.indexOf(11, startIndex);
        this.mApplicationID = SKTCommonPushData.substring(startIndex, dlmtIndex);
        this.mApplicationSpecificData = SKTCommonPushData.substring(dlmtIndex + 1, SKTCommonPushData.length()).getBytes();
        Log.secD(TAG, "dispatchSKTAndroidCommonSMSPushPdus  = " + SKTCommonPushData);
        Intent commonPushIntent = new Intent("com.skt.push.SMS_PUSH");
        commonPushIntent.setType(this.mApplicationID + "/*");
        commonPushIntent.putExtra("aid", this.mApplicationID);
        commonPushIntent.putExtra("AID", this.mApplicationID);
        commonPushIntent.putExtra("msg_body", SKTCommonPushData);
        Log.secD("SMSPushSender", "IN : [ Send Intent(action: " + commonPushIntent.getAction() + ") to APP(Broadcast)");
        Log.secD("SMSPushSender", "@#   " + commonPushIntent.getExtras() + ") to APP(Broadcast)");
        this.mContext.sendBroadcast(commonPushIntent);
        acknowledgeLastIncomingSms(true, 1, null);
        return true;
    }

    protected void dispatchSKTFindingLostPhoneSubscribePdus(byte[] rawPdu) {
        Log.secE(TAG, "dispatchSKTFindingLostPhoneSubscribePdus : " + rawPdu);
        setCarrierLockEnabled("ON");
        Log.secD(TAG, "Subscribe set");
        Intent flpSubscribeIntent = new Intent("com.sec.android.FindingLostPhone.SUBSCRIBE");
        Log.secE(TAG, "com.sec.android.FindingLostPhone.SUBSCRIBE is sent");
        flpSubscribeIntent.putExtra("pdus", rawPdu);
        this.mContext.sendBroadcast(flpSubscribeIntent);
        acknowledgeLastIncomingSms(true, 1, null);
    }

    protected void dispatchSKTFindingLostPhoneCancelPdus(byte[] rawPdu) {
        Log.secE(TAG, "dispatchSKTFindingLostPhoneCancelPdus : " + rawPdu);
        setCarrierLockEnabled("OFF");
        Log.secD(TAG, "Cancel set");
        Intent flpCancelIntent = new Intent("com.sec.android.FindingLostPhone.CANCEL");
        Log.secE(TAG, "com.sec.android.FindingLostPhone.CANCEL is sent");
        flpCancelIntent.putExtra("pdus", rawPdu);
        this.mContext.sendBroadcast(flpCancelIntent);
        acknowledgeLastIncomingSms(true, 1, null);
    }

    protected void dispatchKTToAppManagerPdus(byte[][] pdus, int port) {
        Intent intent = new Intent(Intents.SHOW_DATA_SMS_RECEIVED_ACTION);
        Log.secD(TAG, "dispatchPdusToKTAppManager PDU = " + pdus + " PORT = " + port);
        intent.putExtra("pdus", pdus);
        intent.putExtra("port_address", port);
        dispatchIntent(intent, "android.permission.RECEIVE_SMS", 16, null, UserHandle.OWNER);
    }

    protected void dispatchKTToLbsServicePdus(byte[][] pdus, int port) {
        Intent intent = new Intent(Intents.ACTION_KTLBS_DATA_SMS_RECEIVED);
        Log.secD(TAG, "dispatchKTToLbsServicePdus PDU = " + pdus + " PORT = " + port);
        intent.addFlags(32);
        intent.putExtra("pdus", pdus);
        dispatchIntent(intent, "android.permission.RECEIVE_SMS", 16, null, UserHandle.OWNER);
    }

    protected void dispatchLGTFOTAPdus(byte[][] pdus) {
        Intent intent = new Intent(Intents.LGU_FOTA_RECEIVED_ACTION);
        Log.secD(TAG, "disptchLGTFOTAPdus PDU = " + pdus);
        intent.putExtra("pdus", pdus);
        dispatchIntent(intent, "android.permission.RECEIVE_SMS", 16, null, UserHandle.OWNER);
    }

    protected byte[] parseGstkSmsTpdu(byte[] pdus) {
        int userdata_pos;
        byte[] bearerData = SmsMessage.createFromPdu(pdus, getEncoding()).getBearerData();
        int userdata_len = 0;
        int userdata_pos2 = 0 + 1;
        if (bearerData[0] == (byte) 0) {
            userdata_pos = bearerData[userdata_pos2] + 1;
        } else {
            userdata_pos = userdata_pos2;
        }
        userdata_pos++;
        if (bearerData[userdata_pos] == (byte) 1) {
            userdata_pos++;
            userdata_len = bearerData[userdata_pos];
        }
        byte[] userdata = new byte[userdata_len];
        System.arraycopy(bearerData, userdata_pos + 1, userdata, 0, userdata_len);
        byte[] Gstk_Sms_Tpdu = new byte[userdata_len];
        for (int i = 0; i < userdata_len - 1; i++) {
            Gstk_Sms_Tpdu[i] = (byte) (((userdata[i] & 7) << 5) | ((userdata[i + 1] & BearerData.RELATIVE_TIME_RESERVED) >> 3));
        }
        return Gstk_Sms_Tpdu;
    }

    protected void dispatchLGTCATPTPdus(byte[] pdus) {
        Log.secD(TAG, "dispatchLGTCATPTPdus");
        acknowledgeLastIncomingSms(true, 1, null);
    }

    protected void dispatchLGTUnknownTIDPdus(String body, int teleService) {
        Intent intent = new Intent(Intents.LGU_APM_RECEIVED_ACTION);
        Rlog.d(TAG, "disptchLGTUnknownTIDPdus PDU = " + body + " Tid = " + teleService);
        intent.putExtra("tid", teleService);
        intent.putExtra("body", body);
        dispatchIntent(intent, null, 16, null, UserHandle.OWNER);
    }

    protected void dispatchLGTTeleserviceMessage(SmsMessageBase smsb, int teleService) {
        byte[][] pdus = new byte[][]{smsb.getPdu()};
        switch (teleService) {
            case 4097:
            case 4099:
            case SmsEnvelope.TELESERVICE_LGT_CBS_32576 /*32576*/:
            case SmsEnvelope.TELESERVICE_LGT_ETC_SHARE_49162 /*49162*/:
            case SmsEnvelope.TELESERVICE_LGT_WAP_URL_NOTI_49166 /*49166*/:
            case SmsEnvelope.TELESERVICE_LGT_WAP_URL_NOTI_49167 /*49167*/:
            case SmsEnvelope.TELESERVICE_LGT_WAP_URL_NOTI_49168 /*49168*/:
            case SmsEnvelope.TELESERVICE_LGT_WEB_THIRD_49763 /*49763*/:
            case SmsEnvelope.TELESERVICE_LGT_WEB_LGT_49765 /*49765*/:
            case SmsEnvelope.TELESERVICE_LGT_WEB_CP_49767 /*49767*/:
            case SmsEnvelope.TELESERVICE_MWI /*262144*/:
            case SmsEnvelope.TELESERVICE_LGT_CBS_327680 /*327680*/:
                dispatchPdus(pdus);
                return;
            case SmsEnvelope.TELESERVICE_CATPT /*4103*/:
                dispatchLGTCATPTPdus(pdus[0]);
                return;
            case SmsEnvelope.TELESERVICE_LGT_FOTA_SMS /*49783*/:
                dispatchLGTFOTAPdus(pdus);
                return;
            default:
                dispatchLGTUnknownTIDPdus(smsb.getMessageBody(), teleService);
                return;
        }
    }

    protected void dispatchNSRI(byte[][] pdus) {
        Intent intent = new Intent(Intents.NSRISMS_RECEIVED_ACTION);
        intent.putExtra("pdus", pdus);
        dispatchIntent(intent, "android.permission.RECEIVE_SECURITY_SMS", 16, null, UserHandle.OWNER);
    }
}
