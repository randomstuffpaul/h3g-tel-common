package com.android.internal.telephony;

import android.app.ActivityManager;
import android.app.ActivityManager.RunningTaskInfo;
import android.app.AppOpsManager;
import android.app.PendingIntent;
import android.app.PendingIntent.CanceledException;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteException;
import android.net.Uri;
import android.os.AsyncResult;
import android.os.Binder;
import android.os.Handler;
import android.os.Message;
import android.os.SystemProperties;
import android.os.UserManager;
import android.provider.Telephony.Carriers;
import android.telephony.Rlog;
import android.telephony.SmsManager;
import android.telephony.SmsMessage;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.telephony.gsm.CbConfig;
import android.util.Log;
import com.android.internal.telephony.cdma.CdmaSmsBroadcastConfigInfo;
import com.android.internal.telephony.gsm.SmsBroadcastConfigInfo;
import com.android.internal.telephony.uicc.IccConstants;
import com.android.internal.telephony.uicc.IccFileHandler;
import com.android.internal.telephony.uicc.UiccController;
import com.android.internal.util.HexDump;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class IccSmsInterfaceManager {
    static final boolean DBG = true;
    private static final int EVENT_GET_CB_CONFIG_DONE = 7;
    private static final int EVENT_LOAD_DONE = 1;
    protected static final int EVENT_SET_BROADCAST_ACTIVATION_DONE = 3;
    protected static final int EVENT_SET_BROADCAST_CONFIG_DONE = 4;
    private static final int EVENT_UPDATE_DONE = 2;
    static final String LOG_TAG = "IccSmsInterfaceManager";
    private static final int SMS_CB_CODE_SCHEME_MAX = 255;
    private static final int SMS_CB_CODE_SCHEME_MIN = 0;
    private static CbConfig mCbConfig;
    protected final AppOpsManager mAppOps;
    private CdmaBroadcastRangeManager mCdmaBroadcastRangeManager = new CdmaBroadcastRangeManager();
    private CellBroadcastRangeManager mCellBroadcastRangeManager = new CellBroadcastRangeManager();
    protected final Context mContext;
    protected SMSDispatcher mDispatcher;
    protected Handler mHandler = new C00141();
    private SMSDispatcher mImsDispatcher;
    protected final Object mLock = new Object();
    protected PhoneBase mPhone;
    private List<SmsRawData> mSms;
    protected String mSmscSet;
    protected boolean mSuccess;
    private final UserManager mUserManager;

    class C00141 extends Handler {
        C00141() {
        }

        public void handleMessage(Message msg) {
            boolean z = true;
            AsyncResult ar;
            IccSmsInterfaceManager iccSmsInterfaceManager;
            switch (msg.what) {
                case 1:
                    ar = (AsyncResult) msg.obj;
                    synchronized (IccSmsInterfaceManager.this.mLock) {
                        if (ar.exception == null) {
                            IccSmsInterfaceManager.this.mSms = IccSmsInterfaceManager.this.buildValidRawData((ArrayList) ar.result);
                            IccSmsInterfaceManager.this.markMessagesAsRead((ArrayList) ar.result);
                        } else {
                            if (Rlog.isLoggable("SMS", 3)) {
                                IccSmsInterfaceManager.this.log("Cannot load Sms records");
                            }
                            if (IccSmsInterfaceManager.this.mSms != null) {
                                IccSmsInterfaceManager.this.mSms.clear();
                            }
                        }
                        IccSmsInterfaceManager.this.mLock.notifyAll();
                    }
                    return;
                case 2:
                    ar = msg.obj;
                    synchronized (IccSmsInterfaceManager.this.mLock) {
                        iccSmsInterfaceManager = IccSmsInterfaceManager.this;
                        if (ar.exception != null) {
                            z = false;
                        }
                        iccSmsInterfaceManager.mSuccess = z;
                        IccSmsInterfaceManager.this.mLock.notifyAll();
                    }
                    return;
                case 3:
                case 4:
                    ar = (AsyncResult) msg.obj;
                    synchronized (IccSmsInterfaceManager.this.mLock) {
                        iccSmsInterfaceManager = IccSmsInterfaceManager.this;
                        if (ar.exception != null) {
                            z = false;
                        }
                        iccSmsInterfaceManager.mSuccess = z;
                        IccSmsInterfaceManager.this.mLock.notifyAll();
                    }
                    return;
                case 7:
                    IccSmsInterfaceManager.this.log("GSM EVENT_GET_CB_CONFIG_DONE");
                    ar = (AsyncResult) msg.obj;
                    synchronized (IccSmsInterfaceManager.this.mLock) {
                        if (ar.exception == null) {
                            IccSmsInterfaceManager.mCbConfig = (CbConfig) ar.result;
                        } else {
                            IccSmsInterfaceManager.this.log("Cannot Get CB Config");
                        }
                        IccSmsInterfaceManager.this.mLock.notifyAll();
                    }
                    return;
                default:
                    return;
            }
        }
    }

    class CdmaBroadcastRangeManager extends IntRangeManager {
        private ArrayList<CdmaSmsBroadcastConfigInfo> mConfigList = new ArrayList();

        CdmaBroadcastRangeManager() {
        }

        protected void startUpdate() {
            this.mConfigList.clear();
        }

        protected void addRange(int startId, int endId, boolean selected) {
            this.mConfigList.add(new CdmaSmsBroadcastConfigInfo(startId, endId, 1, selected));
        }

        protected boolean finishUpdate() {
            if (this.mConfigList.isEmpty()) {
                return true;
            }
            return IccSmsInterfaceManager.this.setCdmaBroadcastConfig((CdmaSmsBroadcastConfigInfo[]) this.mConfigList.toArray(new CdmaSmsBroadcastConfigInfo[this.mConfigList.size()]));
        }
    }

    class CellBroadcastRangeManager extends IntRangeManager {
        private ArrayList<SmsBroadcastConfigInfo> mConfigList = new ArrayList();

        CellBroadcastRangeManager() {
        }

        protected void startUpdate() {
            this.mConfigList.clear();
        }

        protected void addRange(int startId, int endId, boolean selected) {
            this.mConfigList.add(new SmsBroadcastConfigInfo(startId, endId, 0, 255, selected));
        }

        protected boolean finishUpdate() {
            if (this.mConfigList.isEmpty()) {
                return true;
            }
            return IccSmsInterfaceManager.this.setCellBroadcastConfig((SmsBroadcastConfigInfo[]) this.mConfigList.toArray(new SmsBroadcastConfigInfo[this.mConfigList.size()]));
        }
    }

    private boolean isFailedOrDraft(android.content.ContentResolver r14, android.net.Uri r15) {
        /* JADX: method processing error */
/*
Error: jadx.core.utils.exceptions.JadxRuntimeException: Can't find block by offset: 0x004e in list []
	at jadx.core.utils.BlockUtils.getBlockByOffset(BlockUtils.java:42)
	at jadx.core.dex.instructions.IfNode.initBlocks(IfNode.java:60)
	at jadx.core.dex.visitors.blocksmaker.BlockFinish.initBlocksInIfNodes(BlockFinish.java:48)
	at jadx.core.dex.visitors.blocksmaker.BlockFinish.visit(BlockFinish.java:33)
	at jadx.core.dex.visitors.DepthTraversal.visit(DepthTraversal.java:31)
	at jadx.core.dex.visitors.DepthTraversal.visit(DepthTraversal.java:17)
	at jadx.core.ProcessClass.process(ProcessClass.java:37)
	at jadx.api.JadxDecompiler.processClass(JadxDecompiler.java:306)
	at jadx.api.JavaClass.decompile(JavaClass.java:62)
	at jadx.api.JadxDecompiler$1.run(JadxDecompiler.java:199)
*/
        /*
        r13 = this;
        r12 = 1;
        r11 = 0;
        r8 = android.os.Binder.clearCallingIdentity();
        r6 = 0;
        r0 = 1;
        r2 = new java.lang.String[r0];	 Catch:{ SQLiteException -> 0x0041, all -> 0x0052 }
        r0 = 0;	 Catch:{ SQLiteException -> 0x0041, all -> 0x0052 }
        r1 = "type";	 Catch:{ SQLiteException -> 0x0041, all -> 0x0052 }
        r2[r0] = r1;	 Catch:{ SQLiteException -> 0x0041, all -> 0x0052 }
        r3 = 0;	 Catch:{ SQLiteException -> 0x0041, all -> 0x0052 }
        r4 = 0;	 Catch:{ SQLiteException -> 0x0041, all -> 0x0052 }
        r5 = 0;	 Catch:{ SQLiteException -> 0x0041, all -> 0x0052 }
        r0 = r14;	 Catch:{ SQLiteException -> 0x0041, all -> 0x0052 }
        r1 = r15;	 Catch:{ SQLiteException -> 0x0041, all -> 0x0052 }
        r6 = r0.query(r1, r2, r3, r4, r5);	 Catch:{ SQLiteException -> 0x0041, all -> 0x0052 }
        if (r6 == 0) goto L_0x0037;	 Catch:{ SQLiteException -> 0x0041, all -> 0x0052 }
    L_0x001a:
        r0 = r6.moveToFirst();	 Catch:{ SQLiteException -> 0x0041, all -> 0x0052 }
        if (r0 == 0) goto L_0x0037;	 Catch:{ SQLiteException -> 0x0041, all -> 0x0052 }
    L_0x0020:
        r0 = 0;	 Catch:{ SQLiteException -> 0x0041, all -> 0x0052 }
        r10 = r6.getInt(r0);	 Catch:{ SQLiteException -> 0x0041, all -> 0x0052 }
        r0 = 3;
        if (r10 == r0) goto L_0x002b;
    L_0x0028:
        r0 = 5;
        if (r10 != r0) goto L_0x0035;
    L_0x002b:
        r0 = r12;
    L_0x002c:
        if (r6 == 0) goto L_0x0031;
    L_0x002e:
        r6.close();
    L_0x0031:
        android.os.Binder.restoreCallingIdentity(r8);
    L_0x0034:
        return r0;
    L_0x0035:
        r0 = r11;
        goto L_0x002c;
    L_0x0037:
        if (r6 == 0) goto L_0x003c;
    L_0x0039:
        r6.close();
    L_0x003c:
        android.os.Binder.restoreCallingIdentity(r8);
    L_0x003f:
        r0 = r11;
        goto L_0x0034;
    L_0x0041:
        r7 = move-exception;
        r0 = "IccSmsInterfaceManager";	 Catch:{ SQLiteException -> 0x0041, all -> 0x0052 }
        r1 = "[IccSmsInterfaceManager]isFailedOrDraft: query message type failed";	 Catch:{ SQLiteException -> 0x0041, all -> 0x0052 }
        android.util.Log.e(r0, r1, r7);	 Catch:{ SQLiteException -> 0x0041, all -> 0x0052 }
        if (r6 == 0) goto L_0x004e;
    L_0x004b:
        r6.close();
    L_0x004e:
        android.os.Binder.restoreCallingIdentity(r8);
        goto L_0x003f;
    L_0x0052:
        r0 = move-exception;
        if (r6 == 0) goto L_0x0058;
    L_0x0055:
        r6.close();
    L_0x0058:
        android.os.Binder.restoreCallingIdentity(r8);
        throw r0;
        */
        throw new UnsupportedOperationException("Method not decompiled: com.android.internal.telephony.IccSmsInterfaceManager.isFailedOrDraft(android.content.ContentResolver, android.net.Uri):boolean");
    }

    protected IccSmsInterfaceManager(PhoneBase phone) {
        this.mPhone = phone;
        this.mContext = phone.getContext();
        this.mAppOps = (AppOpsManager) this.mContext.getSystemService("appops");
        this.mUserManager = (UserManager) this.mContext.getSystemService(Carriers.USER);
        this.mDispatcher = new ImsSMSDispatcher(phone, phone.mSmsStorageMonitor, phone.mSmsUsageMonitor);
    }

    protected void markMessagesAsRead(ArrayList<byte[]> messages) {
        if (messages != null) {
            IccFileHandler fh = this.mPhone.getIccFileHandler();
            if (fh != null) {
                int count = messages.size();
                for (int i = 0; i < count; i++) {
                    byte[] ba = (byte[]) messages.get(i);
                    if (ba[0] == (byte) 3) {
                        int n = ba.length;
                        byte[] nba = new byte[(n - 1)];
                        System.arraycopy(ba, 1, nba, 0, n - 1);
                        fh.updateEFLinearFixed(IccConstants.EF_SMS, i + 1, makeSmsRecordData(1, nba), null, null);
                        if (Rlog.isLoggable("SMS", 3)) {
                            log("SMS " + (i + 1) + " marked as read");
                        }
                    }
                }
            } else if (Rlog.isLoggable("SMS", 3)) {
                log("markMessagesAsRead - aborting, no icc card present.");
            }
        }
    }

    protected void updatePhoneObject(PhoneBase phone) {
        this.mPhone = phone;
        this.mDispatcher.updatePhoneObject(phone);
    }

    public SMSDispatcher getSMSDispatcher() {
        return this.mDispatcher;
    }

    protected void enforceReceiveAndSend(String message) {
        this.mContext.enforceCallingOrSelfPermission("android.permission.RECEIVE_SMS", message);
        this.mContext.enforceCallingOrSelfPermission("android.permission.SEND_SMS", message);
    }

    public boolean updateMessageOnIccEf(String callingPackage, int index, int status, byte[] pdu) {
        log("updateMessageOnIccEf: index=" + index + " status=" + status + " ==> " + "(" + Arrays.toString(pdu) + ")");
        enforceReceiveAndSend("Updating message on Icc");
        if (this.mAppOps.noteOp(22, Binder.getCallingUid(), callingPackage) != 0 && this.mAppOps.noteOp(22, Binder.getCallingUid(), getTopPackageName()) != 0) {
            return false;
        }
        synchronized (this.mLock) {
            this.mSuccess = false;
            Message response = this.mHandler.obtainMessage(2);
            if (status != 0) {
                IccFileHandler fh = this.mPhone.getIccFileHandler();
                if (fh == null) {
                    response.recycle();
                    boolean z = this.mSuccess;
                    return z;
                }
                fh.updateEFLinearFixed(IccConstants.EF_SMS, index, makeSmsRecordData(status, pdu), null, response);
            } else if (1 != this.mPhone.getPhoneType()) {
                this.mPhone.mCi.deleteSmsOnRuim(index, response);
            } else {
                this.mPhone.mCi.deleteSmsOnSim(index, response);
            }
            try {
                this.mLock.wait();
            } catch (InterruptedException e) {
                log("interrupted while trying to update by index");
            }
            return this.mSuccess;
        }
    }

    public boolean copyMessageToIccEf(String callingPackage, int status, byte[] pdu, byte[] smsc) {
        log("copyMessageToIccEf: status=" + status + " ==> " + "pdu=(" + Arrays.toString(pdu) + "), smsc=(" + Arrays.toString(smsc) + ")");
        enforceReceiveAndSend("Copying message to Icc");
        if (this.mAppOps.noteOp(22, Binder.getCallingUid(), callingPackage) != 0) {
            return false;
        }
        synchronized (this.mLock) {
            this.mSuccess = false;
            Message response = this.mHandler.obtainMessage(2);
            if (1 != this.mPhone.getPhoneType()) {
                this.mPhone.mCi.writeSmsToRuim(status, IccUtils.bytesToHexString(pdu), response);
            } else {
                this.mPhone.mCi.writeSmsToSim(status, IccUtils.bytesToHexString(smsc), IccUtils.bytesToHexString(pdu), response);
            }
            try {
                this.mLock.wait();
            } catch (InterruptedException e) {
                log("interrupted while trying to update by index");
            }
        }
        return this.mSuccess;
    }

    public List<SmsRawData> getAllMessagesFromIccEf(String callingPackage) {
        log("getAllMessagesFromEF");
        this.mContext.enforceCallingOrSelfPermission("android.permission.RECEIVE_SMS", "Reading messages from Icc");
        if (this.mAppOps.noteOp(21, Binder.getCallingUid(), callingPackage) != 0) {
            return new ArrayList();
        }
        synchronized (this.mLock) {
            IccFileHandler fh = this.mPhone.getIccFileHandler();
            if (fh == null) {
                Rlog.e(LOG_TAG, "Cannot load Sms records. No icc card?");
                if (this.mSms != null) {
                    this.mSms.clear();
                    List<SmsRawData> list = this.mSms;
                    return list;
                }
            }
            Message response = this.mHandler.obtainMessage(1);
            if ("ja3gduosctc".equals(SystemProperties.get("ro.product.name"))) {
                TelephonyManager.getDefault();
                if ("4".equals(TelephonyManager.getTelephonyProperty("ril.ICC_TYPE", 0, ""))) {
                    if (TelephonyManager.isSelectTelecomDF()) {
                        fh.loadEFLinearFixedAll(28508, response);
                    } else {
                        fh.loadEFLinearFixedAll(IccConstants.EF_SMS, response);
                    }
                    this.mLock.wait();
                    return this.mSms;
                }
            }
            fh.loadEFLinearFixedAll(IccConstants.EF_SMS, response);
            try {
                this.mLock.wait();
            } catch (InterruptedException e) {
                log("interrupted while trying to load from the Icc");
            }
            return this.mSms;
        }
    }

    public void sendData(String callingPackage, String destAddr, String scAddr, int destPort, byte[] data, PendingIntent sentIntent, PendingIntent deliveryIntent) {
        this.mPhone.getContext().enforceCallingPermission("android.permission.SEND_SMS", "Sending SMS message");
        if (Rlog.isLoggable("SMS", 2)) {
            log("sendData: destAddr=" + destAddr + " scAddr=" + scAddr + " destPort=" + destPort + " data='" + HexDump.toHexString(data) + "' sentIntent=" + sentIntent + " deliveryIntent=" + deliveryIntent);
        }
        if (this.mAppOps.noteOp(20, Binder.getCallingUid(), callingPackage) == 0) {
            this.mDispatcher.sendData(destAddr, scAddr, destPort, data, sentIntent, deliveryIntent);
        }
    }

    public void sendDatawithOrigPort(String callingPackage, String destAddr, String scAddr, int destPort, int origPort, byte[] data, PendingIntent sentIntent, PendingIntent deliveryIntent) {
        this.mContext.enforceCallingPermission("android.permission.SEND_SMS", "Sending SMS message");
        if (Log.isLoggable("SMS", 2)) {
            log("sendData: destAddr=" + destAddr + " scAddr=" + scAddr + " destPort=" + origPort + " origPort=" + destPort + " data='" + HexDump.toHexString(data) + "' sentIntent=" + sentIntent + " deliveryIntent=" + deliveryIntent);
        }
        this.mDispatcher.sendDatawithOrigPort(destAddr, scAddr, destPort, origPort, data, sentIntent, deliveryIntent);
    }

    public void sendText(String callingPackage, String destAddr, String scAddr, String text, PendingIntent sentIntent, PendingIntent deliveryIntent) {
        this.mPhone.getContext().enforceCallingPermission("android.permission.SEND_SMS", "Sending SMS message");
        if (Rlog.isLoggable("SMS", 2)) {
            log("sendText: destAddr=" + destAddr + " scAddr=" + scAddr + " text='" + text + "' sentIntent=" + sentIntent + " deliveryIntent=" + deliveryIntent);
        }
        if (this.mAppOps.noteOp(20, Binder.getCallingUid(), callingPackage) == 0) {
            this.mDispatcher.sendText(destAddr, scAddr, text, sentIntent, deliveryIntent, null, callingPackage);
        }
    }

    public void sendTextwithOptions(String callingPackage, String destAddr, String scAddr, String text, PendingIntent sentIntent, PendingIntent deliveryIntent, boolean replyPath, int expiry, int serviceType, int encodingType) {
        this.mPhone.getContext().enforceCallingPermission("android.permission.SEND_SMS", "Sending SMS message");
        if (Rlog.isLoggable("SMS", 2)) {
            log("sendText: destAddr=" + destAddr + " scAddr=" + scAddr + " text='" + text + "' sentIntent=" + sentIntent + " deliveryIntent=" + deliveryIntent);
        }
        if (this.mAppOps.noteOp(20, Binder.getCallingUid(), callingPackage) == 0) {
            this.mDispatcher.sendTextwithOptions(destAddr, scAddr, text, sentIntent, deliveryIntent, null, callingPackage, replyPath, expiry, serviceType, encodingType, -1);
        }
    }

    public void sendOTADomestic(String callingPackage, String destAddr, String scAddr, String text) {
        this.mPhone.getContext().enforceCallingOrSelfPermission("android.permission.SEND_SMS", "Sending SMS message");
        if (Rlog.isLoggable("SMS", 2)) {
            log("sendText: destAddr=" + destAddr + " scAddr=" + scAddr + " text='" + text);
        }
        if (this.mAppOps.noteOp(20, Binder.getCallingUid(), callingPackage) == 0) {
            this.mDispatcher.sendOTADomestic(destAddr, scAddr, text);
        }
    }

    public void sendTextNSRI(String callingPackage, String destAddr, String scAddr, byte[] text, PendingIntent sentIntent, PendingIntent deliveryIntent, int msgCount, int msgTotal) {
        this.mPhone.getContext().enforceCallingPermission("android.permission.SEND_SMS", "Sending SMS message");
        if (Rlog.isLoggable("SMS", 2)) {
            log("[NSRI_SMS] sendTextNSRI: destAddr=" + destAddr + " scAddr=" + scAddr + " text='" + HexDump.toHexString(text) + "' sentIntent=" + sentIntent + " deliveryIntent=" + deliveryIntent);
        }
        if (callingPackage == null) {
            callingPackage = getTopPackageName();
        }
        if (this.mAppOps.noteOp(20, Binder.getCallingUid(), callingPackage) == 0) {
            this.mDispatcher.sendTextNSRI(destAddr, scAddr, text, sentIntent, deliveryIntent, msgCount, msgTotal);
        }
    }

    public void injectSmsPdu(byte[] pdu, String format, PendingIntent receivedIntent) {
        enforceCarrierPrivilege();
        if (Rlog.isLoggable("SMS", 2)) {
            log("pdu: " + pdu + "\n format=" + format + "\n receivedIntent=" + receivedIntent);
        }
        this.mDispatcher.injectSmsPdu(pdu, format, receivedIntent);
    }

    public void updateSmsSendStatus(int messageRef, boolean success) {
        enforceCarrierPrivilege();
        this.mDispatcher.updateSmsSendStatus(messageRef, success);
    }

    public void sendMultipartText(String callingPackage, String destAddr, String scAddr, List<String> parts, List<PendingIntent> sentIntents, List<PendingIntent> deliveryIntents) {
        int i;
        this.mPhone.getContext().enforceCallingPermission("android.permission.SEND_SMS", "Sending SMS message");
        if (Rlog.isLoggable("SMS", 2)) {
            i = 0;
            for (String part : parts) {
                int i2 = i + 1;
                log("sendMultipartText: destAddr=" + destAddr + ", srAddr=" + scAddr + ", part[" + i + "]=" + part);
                i = i2;
            }
        }
        if (this.mAppOps.noteOp(20, Binder.getCallingUid(), callingPackage) == 0) {
            if (parts.size() <= 1 || parts.size() >= 10 || SmsMessage.hasEmsSupport()) {
                this.mDispatcher.sendMultipartText(destAddr, scAddr, (ArrayList) parts, (ArrayList) sentIntents, (ArrayList) deliveryIntents, null, callingPackage);
                return;
            }
            i = 0;
            while (i < parts.size()) {
                String singlePart = (String) parts.get(i);
                if (SmsMessage.shouldAppendPageNumberAsPrefix()) {
                    singlePart = String.valueOf(i + 1) + '/' + parts.size() + ' ' + singlePart;
                } else {
                    singlePart = singlePart.concat(' ' + String.valueOf(i + 1) + '/' + parts.size());
                }
                PendingIntent singleSentIntent = null;
                if (sentIntents != null && sentIntents.size() > i) {
                    singleSentIntent = (PendingIntent) sentIntents.get(i);
                }
                PendingIntent singleDeliveryIntent = null;
                if (deliveryIntents != null && deliveryIntents.size() > i) {
                    singleDeliveryIntent = (PendingIntent) deliveryIntents.get(i);
                }
                this.mDispatcher.sendText(destAddr, scAddr, singlePart, singleSentIntent, singleDeliveryIntent, null, callingPackage);
                i++;
            }
        }
    }

    public void sendMultipartTextwithOptions(String callingPackage, String destAddr, String scAddr, List<String> parts, List<PendingIntent> sentIntents, List<PendingIntent> deliveryIntents, boolean replyPath, int expiry, int serviceType, int encodingType) {
        int i;
        this.mPhone.getContext().enforceCallingPermission("android.permission.SEND_SMS", "Sending SMS message");
        if (Rlog.isLoggable("SMS", 2)) {
            i = 0;
            for (String part : parts) {
                int i2 = i + 1;
                log("sendMultipartText: destAddr=" + destAddr + ", srAddr=" + scAddr + ", part[" + i + "]=" + part);
                i = i2;
            }
        }
        if (this.mAppOps.noteOp(20, Binder.getCallingUid(), callingPackage) == 0) {
            if (parts.size() <= 1 || parts.size() >= 10 || SmsMessage.hasEmsSupport()) {
                this.mDispatcher.sendMultipartTextwithOptions(destAddr, scAddr, (ArrayList) parts, (ArrayList) sentIntents, (ArrayList) deliveryIntents, null, callingPackage, replyPath, expiry, serviceType, encodingType);
                return;
            }
            i = 0;
            while (i < parts.size()) {
                String singlePart = (String) parts.get(i);
                if (SmsMessage.shouldAppendPageNumberAsPrefix()) {
                    singlePart = String.valueOf(i + 1) + '/' + parts.size() + ' ' + singlePart;
                } else {
                    singlePart = singlePart.concat(' ' + String.valueOf(i + 1) + '/' + parts.size());
                }
                PendingIntent singleSentIntent = null;
                if (sentIntents != null && sentIntents.size() > i) {
                    singleSentIntent = (PendingIntent) sentIntents.get(i);
                }
                PendingIntent singleDeliveryIntent = null;
                if (deliveryIntents != null && deliveryIntents.size() > i) {
                    singleDeliveryIntent = (PendingIntent) deliveryIntents.get(i);
                }
                this.mDispatcher.sendTextwithOptions(destAddr, scAddr, singlePart, singleSentIntent, singleDeliveryIntent, null, callingPackage, replyPath, expiry, serviceType, encodingType, -1);
                i++;
            }
        }
    }

    public void sendTextwithOptionsReadconfirm(String callingPackage, String destAddr, String scAddr, String text, PendingIntent sentIntent, PendingIntent deliveryIntent, boolean replyPath, int expiry, int serviceType, int encodingType, int confirmId) {
        this.mPhone.getContext().enforceCallingPermission("android.permission.SEND_SMS", "Sending SMS message");
        if (Rlog.isLoggable("SMS", 2)) {
            log("sendText: destAddr=" + destAddr + " scAddr=" + scAddr + " text='" + text + "' sentIntent=" + sentIntent + " deliveryIntent=" + deliveryIntent);
        }
        if (this.mAppOps.noteOp(20, Binder.getCallingUid(), callingPackage) == 0) {
            this.mDispatcher.sendTextwithOptions(destAddr, scAddr, text, sentIntent, deliveryIntent, null, callingPackage, replyPath, expiry, serviceType, encodingType, confirmId);
        }
    }

    public int getPremiumSmsPermission(String packageName) {
        return this.mDispatcher.getPremiumSmsPermission(packageName);
    }

    public void setPremiumSmsPermission(String packageName, int permission) {
        this.mDispatcher.setPremiumSmsPermission(packageName, permission);
    }

    protected ArrayList<SmsRawData> buildValidRawData(ArrayList<byte[]> messages) {
        int count = messages.size();
        ArrayList<SmsRawData> ret = new ArrayList(count);
        for (int i = 0; i < count; i++) {
            if (((byte[]) messages.get(i))[0] == (byte) 0) {
                ret.add(null);
            } else {
                ret.add(new SmsRawData((byte[]) messages.get(i)));
            }
        }
        return ret;
    }

    protected byte[] makeSmsRecordData(int status, byte[] pdu) {
        int iccType;
        int recordLength = 176;
        int pduLength = pdu.length;
        long subId = SubscriptionManager.getDefaultSmsSubId();
        try {
            TelephonyManager.getDefault();
            iccType = Integer.parseInt(TelephonyManager.getTelephonyProperty("ril.ICC_TYPE", subId, ""));
        } catch (Exception e) {
            log("IccType is invalid");
            iccType = 0;
        }
        if (iccType == 4) {
            int phoneType = this.mPhone.getPhoneType();
            if (TelephonyManager.isSelectTelecomDF()) {
                if (phoneType == 1) {
                    log("CSIM CDMA Record @ Dective");
                    recordLength = 255;
                }
            } else if (phoneType == 2) {
                log("CSIM CDMA Record @ Active");
                recordLength = 255;
            }
        } else if (this.mPhone.getPhoneType() == 2) {
            log("Use CDMA_SMS_RECORD_LENGTH - iccType:" + iccType);
            recordLength = 255;
        }
        if (recordLength < pduLength) {
            log("length exceeded! recordLength:" + recordLength + " pdu.length:" + pdu.length);
            pduLength = recordLength - 1;
        }
        byte[] data = new byte[recordLength];
        data[0] = (byte) (status & 7);
        System.arraycopy(pdu, 0, data, 1, pduLength);
        for (int j = pduLength + 1; j < recordLength; j++) {
            data[j] = (byte) -1;
        }
        return data;
    }

    public boolean enableCellBroadcast(int messageIdentifier) {
        return enableCellBroadcastRange(messageIdentifier, messageIdentifier);
    }

    public boolean disableCellBroadcast(int messageIdentifier) {
        return disableCellBroadcastRange(messageIdentifier, messageIdentifier);
    }

    public boolean enableCellBroadcastRange(int startMessageId, int endMessageId) {
        String sales_code = SystemProperties.get("ro.csc.sales_code");
        if (1 == this.mPhone.getPhoneType() || "CTC".equals(sales_code)) {
            return enableGsmBroadcastRange(startMessageId, endMessageId);
        }
        return enableCdmaBroadcastRange(startMessageId, endMessageId);
    }

    public boolean disableCellBroadcastRange(int startMessageId, int endMessageId) {
        String sales_code = SystemProperties.get("ro.csc.sales_code");
        if (1 == this.mPhone.getPhoneType() || "CTC".equals(sales_code)) {
            return disableGsmBroadcastRange(startMessageId, endMessageId);
        }
        return disableCdmaBroadcastRange(startMessageId, endMessageId);
    }

    public synchronized boolean enableGsmBroadcastRange(int startMessageId, int endMessageId) {
        boolean z = false;
        synchronized (this) {
            log("enableGsmBroadcastRange");
            Context context = this.mPhone.getContext();
            context.enforceCallingPermission("android.permission.RECEIVE_SMS", "Enabling cell broadcast SMS");
            String client = context.getPackageManager().getNameForUid(Binder.getCallingUid());
            if (this.mCellBroadcastRangeManager.enableRange(startMessageId, endMessageId, client)) {
                log("Added cell broadcast subscription for MID range " + startMessageId + " to " + endMessageId + " from client " + client);
                if (!this.mCellBroadcastRangeManager.isEmpty()) {
                    z = true;
                }
                setCellBroadcastActivation(z);
                z = true;
            } else {
                log("Failed to add cell broadcast subscription for MID range " + startMessageId + " to " + endMessageId + " from client " + client);
            }
        }
        return z;
    }

    public synchronized boolean disableGsmBroadcastRange(int startMessageId, int endMessageId) {
        boolean z = false;
        synchronized (this) {
            log("disableGsmBroadcastRange");
            Context context = this.mPhone.getContext();
            context.enforceCallingPermission("android.permission.RECEIVE_SMS", "Disabling cell broadcast SMS");
            String client = context.getPackageManager().getNameForUid(Binder.getCallingUid());
            if (this.mCellBroadcastRangeManager.disableRange(startMessageId, endMessageId, client)) {
                boolean z2;
                log("Removed cell broadcast subscription for MID range " + startMessageId + " to " + endMessageId + " from client " + client);
                if (this.mCellBroadcastRangeManager.isEmpty()) {
                    z2 = false;
                } else {
                    z2 = true;
                }
                if (setCellBroadcastActivation(z2)) {
                    z = true;
                } else {
                    log("Failed to deactivate cell broadcast subscription for MID range " + startMessageId + " to " + endMessageId + " from client " + client);
                }
            } else {
                log("Failed to remove cell broadcast subscription for MID range " + startMessageId + " to " + endMessageId + " from client " + client);
            }
        }
        return z;
    }

    public synchronized boolean enableCdmaBroadcastRange(int startMessageId, int endMessageId) {
        boolean z = false;
        synchronized (this) {
            log("enableCdmaBroadcastRange");
            Context context = this.mPhone.getContext();
            context.enforceCallingPermission("android.permission.RECEIVE_SMS", "Enabling cdma broadcast SMS");
            String client = context.getPackageManager().getNameForUid(Binder.getCallingUid());
            if (this.mCdmaBroadcastRangeManager.enableRange(startMessageId, endMessageId, client)) {
                log("Added cdma broadcast subscription for MID range " + startMessageId + " to " + endMessageId + " from client " + client);
                if (!this.mCdmaBroadcastRangeManager.isEmpty()) {
                    z = true;
                }
                setCdmaBroadcastActivation(z);
                z = true;
            } else {
                log("Failed to add cdma broadcast subscription for MID range " + startMessageId + " to " + endMessageId + " from client " + client);
            }
        }
        return z;
    }

    public synchronized boolean disableCdmaBroadcastRange(int startMessageId, int endMessageId) {
        boolean z = false;
        synchronized (this) {
            log("disableCdmaBroadcastRange");
            Context context = this.mPhone.getContext();
            context.enforceCallingPermission("android.permission.RECEIVE_SMS", "Disabling cell broadcast SMS");
            String client = context.getPackageManager().getNameForUid(Binder.getCallingUid());
            if (this.mCdmaBroadcastRangeManager.disableRange(startMessageId, endMessageId, client)) {
                log("Removed cdma broadcast subscription for MID range " + startMessageId + " to " + endMessageId + " from client " + client);
                if (!this.mCdmaBroadcastRangeManager.isEmpty()) {
                    z = true;
                }
                setCdmaBroadcastActivation(z);
                z = true;
            } else {
                log("Failed to remove cdma broadcast subscription for MID range " + startMessageId + " to " + endMessageId + " from client " + client);
            }
        }
        return z;
    }

    private boolean setCellBroadcastConfig(SmsBroadcastConfigInfo[] configs) {
        log("Calling setGsmBroadcastConfig with " + configs.length + " configurations");
        synchronized (this.mLock) {
            Message response = this.mHandler.obtainMessage(4);
            this.mSuccess = false;
            this.mPhone.mCi.setGsmBroadcastConfig(configs, response);
            try {
                this.mLock.wait();
            } catch (InterruptedException e) {
                log("interrupted while trying to set cell broadcast config");
            }
        }
        return this.mSuccess;
    }

    private boolean setCellBroadcastActivation(boolean activate) {
        log("Calling setCellBroadcastActivation(" + activate + ')');
        synchronized (this.mLock) {
            Message response = this.mHandler.obtainMessage(3);
            this.mSuccess = false;
            this.mPhone.mCi.setGsmBroadcastActivation(activate, response);
            try {
                this.mLock.wait();
            } catch (InterruptedException e) {
                log("interrupted while trying to set cell broadcast activation");
            }
        }
        return this.mSuccess;
    }

    private boolean setCdmaBroadcastConfig(CdmaSmsBroadcastConfigInfo[] configs) {
        log("Calling setCdmaBroadcastConfig with " + configs.length + " configurations");
        synchronized (this.mLock) {
            Message response = this.mHandler.obtainMessage(4);
            this.mSuccess = false;
            this.mPhone.mCi.setCdmaBroadcastConfig(configs, response);
            try {
                this.mLock.wait();
            } catch (InterruptedException e) {
                log("interrupted while trying to set cdma broadcast config");
            }
        }
        return this.mSuccess;
    }

    private boolean setCdmaBroadcastActivation(boolean activate) {
        log("Calling setCdmaBroadcastActivation(" + activate + ")");
        synchronized (this.mLock) {
            Message response = this.mHandler.obtainMessage(3);
            this.mSuccess = false;
            this.mPhone.mCi.setCdmaBroadcastActivation(activate, response);
            try {
                this.mLock.wait();
            } catch (InterruptedException e) {
                log("interrupted while trying to set cdma broadcast activation");
            }
        }
        return this.mSuccess;
    }

    protected void log(String msg) {
        Log.d(LOG_TAG, "[IccSmsInterfaceManager] " + msg);
    }

    public boolean isImsSmsSupported() {
        return this.mDispatcher.isIms();
    }

    public String getImsSmsFormat() {
        return this.mDispatcher.getImsSmsFormat();
    }

    public boolean getSMSAvailable() {
        enforceReceiveAndSend("getSMSAvailable");
        log("getSMSAvailable : " + this.mPhone.getSMSavailable());
        return this.mPhone.getSMSavailable();
    }

    public boolean getSMSPAvailable() {
        log("getSMSPAvailable : " + this.mPhone.getSMSPavailable());
        return this.mPhone.getSMSPavailable();
    }

    public boolean getSimFullStatus() {
        enforceReceiveAndSend("getSimFullStatus");
        log("getSimFullStatus : " + this.mPhone.mSmsStorageMonitor.getSimFullStatus());
        return this.mPhone.mSmsStorageMonitor.getSimFullStatus();
    }

    public void resetSimFullStatus() {
        enforceReceiveAndSend("resetSimFullStatus");
        this.mPhone.mSmsStorageMonitor.resetSimFullStatus();
    }

    public void setCDMASmsReassembly(boolean onOff) {
        log("setCDMASmsReassembly");
        SMSDispatcher sMSDispatcher = this.mDispatcher;
        SMSDispatcher.setCDMASmsReassembly(onOff);
    }

    public String getSmsc() {
        return this.mDispatcher.getSmsc();
    }

    public byte[] getCbSettings() {
        int i;
        enforceReceiveAndSend("getCbSettings");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        synchronized (this.mLock) {
            this.mPhone.mCi.getCbConfig(this.mHandler.obtainMessage(7));
            try {
                this.mLock.wait();
            } catch (InterruptedException e) {
                log("interrupted");
            }
        }
        log(" bCBEnabled = " + mCbConfig.bCBEnabled + " selectedId = " + mCbConfig.selectedId + " msgIdMaxCount = " + mCbConfig.msgIdMaxCount + " msgIdCount = " + mCbConfig.msgIdCount);
        for (short s : mCbConfig.msgIDs) {
            log("[CB] msgIDs =  " + s);
        }
        if (mCbConfig.bCBEnabled) {
            out.write(1);
        } else {
            out.write(0);
        }
        out.write((byte) mCbConfig.selectedId);
        out.write((byte) mCbConfig.msgIdMaxCount);
        out.write((byte) mCbConfig.msgIdCount);
        for (i = 0; i < mCbConfig.msgIdCount; i++) {
            setByte(mCbConfig.msgIDs[i], out);
        }
        log("CB Config Out Bytes = " + IccUtils.bytesToHexString(out.toByteArray()));
        return out.toByteArray();
    }

    private void setByte(short a, ByteArrayOutputStream out) {
        out.write((byte) (a & 255));
        out.write((byte) ((a >> 8) & 255));
    }

    public void sendStoredText(String callingPkg, Uri messageUri, String scAddress, PendingIntent sentIntent, PendingIntent deliveryIntent) {
        this.mPhone.getContext().enforceCallingPermission("android.permission.SEND_SMS", "Sending SMS message");
        if (Rlog.isLoggable("SMS", 2)) {
            log("sendStoredText: scAddr=" + scAddress + " messageUri=" + messageUri + " sentIntent=" + sentIntent + " deliveryIntent=" + deliveryIntent);
        }
        if (this.mAppOps.noteOp(20, Binder.getCallingUid(), callingPkg) == 0) {
            ContentResolver resolver = this.mPhone.getContext().getContentResolver();
            if (isFailedOrDraft(resolver, messageUri)) {
                String[] textAndAddress = loadTextAndAddress(resolver, messageUri);
                if (textAndAddress == null) {
                    Log.e(LOG_TAG, "[IccSmsInterfaceManager]sendStoredText: can not load text");
                    returnUnspecifiedFailure(sentIntent);
                    return;
                }
                this.mDispatcher.sendText(textAndAddress[1], scAddress, textAndAddress[0], sentIntent, deliveryIntent, messageUri, callingPkg);
                return;
            }
            Log.e(LOG_TAG, "[IccSmsInterfaceManager]sendStoredText: not FAILED or DRAFT message");
            returnUnspecifiedFailure(sentIntent);
        }
    }

    public void sendStoredMultipartText(String callingPkg, Uri messageUri, String scAddress, List<PendingIntent> sentIntents, List<PendingIntent> deliveryIntents) {
        this.mPhone.getContext().enforceCallingPermission("android.permission.SEND_SMS", "Sending SMS message");
        if (this.mAppOps.noteOp(20, Binder.getCallingUid(), callingPkg) == 0) {
            ContentResolver resolver = this.mPhone.getContext().getContentResolver();
            if (isFailedOrDraft(resolver, messageUri)) {
                String[] textAndAddress = loadTextAndAddress(resolver, messageUri);
                if (textAndAddress == null) {
                    Log.e(LOG_TAG, "[IccSmsInterfaceManager]sendStoredMultipartText: can not load text");
                    returnUnspecifiedFailure((List) sentIntents);
                    return;
                }
                ArrayList parts = SmsManager.getDefault().divideMessage(textAndAddress[0]);
                if (parts == null || parts.size() < 1) {
                    Log.e(LOG_TAG, "[IccSmsInterfaceManager]sendStoredMultipartText: can not divide text");
                    returnUnspecifiedFailure((List) sentIntents);
                    return;
                } else if (parts.size() <= 1 || parts.size() >= 10 || SmsMessage.hasEmsSupport()) {
                    this.mDispatcher.sendMultipartText(textAndAddress[1], scAddress, parts, (ArrayList) sentIntents, (ArrayList) deliveryIntents, messageUri, callingPkg);
                    return;
                } else {
                    int i = 0;
                    while (i < parts.size()) {
                        String singlePart = (String) parts.get(i);
                        if (SmsMessage.shouldAppendPageNumberAsPrefix()) {
                            singlePart = String.valueOf(i + 1) + '/' + parts.size() + ' ' + singlePart;
                        } else {
                            singlePart = singlePart.concat(' ' + String.valueOf(i + 1) + '/' + parts.size());
                        }
                        PendingIntent singleSentIntent = null;
                        if (sentIntents != null && sentIntents.size() > i) {
                            singleSentIntent = (PendingIntent) sentIntents.get(i);
                        }
                        PendingIntent singleDeliveryIntent = null;
                        if (deliveryIntents != null && deliveryIntents.size() > i) {
                            singleDeliveryIntent = (PendingIntent) deliveryIntents.get(i);
                        }
                        this.mDispatcher.sendText(textAndAddress[1], scAddress, singlePart, singleSentIntent, singleDeliveryIntent, messageUri, callingPkg);
                        i++;
                    }
                    return;
                }
            }
            Log.e(LOG_TAG, "[IccSmsInterfaceManager]sendStoredMultipartText: not FAILED or DRAFT message");
            returnUnspecifiedFailure((List) sentIntents);
        }
    }

    public boolean updateSmsServiceCenterOnSimEf(byte[] smsc) {
        log("updateSmsServiceCenterOnSimEf: smsc" + Arrays.toString(smsc));
        enforceReceiveAndSend("Updating smsc on SIM");
        synchronized (this.mLock) {
            boolean garbage_value;
            this.mSuccess = false;
            Message response = this.mHandler.obtainMessage(2);
            String smsc_hexstring = IccUtils.bytesToHexString(smsc);
            if (smsc_hexstring.indexOf("f") == -1 && smsc_hexstring.indexOf("F") == -1) {
                garbage_value = false;
                log("smsc_hexstring doesn't have garbage value");
            } else {
                garbage_value = true;
                log("smsc_hexstring has garbage value");
            }
            byte[] scAddress = IccUtils.hexStringToBytes(smsc_hexstring);
            String smsc_addr = this.mDispatcher.getSmscNumber(scAddress, garbage_value);
            this.mSmscSet = smsc_addr;
            smsc_addr = "\"" + smsc_addr + "\"," + (scAddress[1] & 255);
            log(smsc_addr);
            this.mPhone.mCi.setSmscAddress(smsc_addr, response);
            try {
                this.mLock.wait();
            } catch (InterruptedException e) {
                log("interrupted while trying to update by index");
            }
        }
        if (this.mSuccess) {
            this.mDispatcher.Sim_Smsc = this.mSmscSet;
            log("smsc is updated well : " + this.mDispatcher.Sim_Smsc);
        }
        return this.mSuccess;
    }

    private String[] loadTextAndAddress(ContentResolver resolver, Uri messageUri) {
        long identity = Binder.clearCallingIdentity();
        Cursor cursor = null;
        String[] strArr;
        try {
            cursor = resolver.query(messageUri, new String[]{"body", "address"}, null, null, null);
            if (cursor == null || !cursor.moveToFirst()) {
                if (cursor != null) {
                    cursor.close();
                }
                Binder.restoreCallingIdentity(identity);
                return null;
            }
            strArr = new String[]{cursor.getString(0), cursor.getString(1)};
            return strArr;
        } catch (SQLiteException e) {
            strArr = LOG_TAG;
            Log.e(strArr, "[IccSmsInterfaceManager]loadText: query message text failed", e);
        } finally {
            if (cursor != null) {
                cursor.close();
            }
            Binder.restoreCallingIdentity(identity);
        }
    }

    private void returnUnspecifiedFailure(PendingIntent pi) {
        if (pi != null) {
            try {
                pi.send(1);
            } catch (CanceledException e) {
            }
        }
    }

    private void returnUnspecifiedFailure(List<PendingIntent> pis) {
        if (pis != null) {
            for (PendingIntent pi : pis) {
                returnUnspecifiedFailure(pi);
            }
        }
    }

    private void enforceCarrierPrivilege() {
        UiccController controller = UiccController.getInstance();
        if (controller == null || controller.getUiccCard() == null) {
            throw new SecurityException("No Carrier Privilege: No UICC");
        } else if (controller.getUiccCard().getCarrierPrivilegeStatusForCurrentTransaction(this.mContext.getPackageManager()) != 1) {
            throw new SecurityException("No Carrier Privilege.");
        }
    }

    public void sendTextwithCBP(String callingPackage, String destAddr, String scAddr, String text, PendingIntent sentIntent, PendingIntent deliveryIntent, String callbackNumber, int priority) {
        this.mContext.enforceCallingPermission("android.permission.SEND_SMS", "Sending SMS message");
        if (Log.isLoggable("SMS", 2)) {
            log("sendText: destAddr=" + destAddr + " scAddr=" + scAddr + " text='" + text + "' sentIntent=" + sentIntent + " deliveryIntent=" + deliveryIntent);
        }
        if (this.mAppOps.noteOp(20, Binder.getCallingUid(), callingPackage) == 0) {
            this.mDispatcher.sendText(destAddr, scAddr, text, sentIntent, deliveryIntent, callbackNumber, priority);
        }
    }

    public void sendMultipartTextwithCBP(String callingPackage, String destAddr, String scAddr, List<String> parts, List<PendingIntent> sentIntents, List<PendingIntent> deliveryIntents, String callbackNumber, int priority) {
        this.mContext.enforceCallingPermission("android.permission.SEND_SMS", "Sending SMS message");
        if (Log.isLoggable("SMS", 2)) {
            int i = 0;
            for (String part : parts) {
                int i2 = i + 1;
                log("sendMultipartText: destAddr=" + destAddr + ", srAddr=" + scAddr + ", part[" + i + "]=" + part);
                i = i2;
            }
        }
        if (this.mAppOps.noteOp(20, Binder.getCallingUid(), callingPackage) == 0) {
            this.mDispatcher.sendMultipartText(destAddr, scAddr, (ArrayList) parts, (ArrayList) sentIntents, (ArrayList) deliveryIntents, callbackNumber, priority);
        }
    }

    public void sendRawPduSat(byte[] smsc, byte[] pdu, String format, PendingIntent sentIntent, PendingIntent deliveryIntent) {
        this.mDispatcher.sendRawPduSat(smsc, pdu, format, sentIntent, deliveryIntent);
    }

    private String getTopPackageName() {
        ActivityManager am = (ActivityManager) this.mContext.getSystemService("activity");
        if (am == null) {
            return null;
        }
        ComponentName topActivity = ((RunningTaskInfo) am.getRunningTasks(1).get(0)).topActivity;
        if (topActivity == null) {
            return null;
        }
        if ("com.android.mms".equals(topActivity.getPackageName())) {
            return "com.android.phone";
        }
        return topActivity.getPackageName();
    }
}
