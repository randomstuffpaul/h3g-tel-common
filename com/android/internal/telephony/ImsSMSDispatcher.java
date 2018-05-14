package com.android.internal.telephony;

import android.app.PendingIntent;
import android.app.PendingIntent.CanceledException;
import android.content.ContentResolver;
import android.content.IntentFilter;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncResult;
import android.os.Message;
import android.provider.Telephony.Mms.Part;
import android.telephony.Rlog;
import android.telephony.SmsMessage;
import android.telephony.SmsMessage.MessageClass;
import android.telephony.TelephonyManager;
import com.android.internal.telephony.GsmAlphabet.TextEncodingDetails;
import com.android.internal.telephony.SmsMessageBase.SubmitPduBase;
import com.android.internal.telephony.cdma.CdmaInboundSmsHandler;
import com.android.internal.telephony.cdma.CdmaSMSDispatcher;
import com.android.internal.telephony.gsm.GsmInboundSmsHandler;
import com.android.internal.telephony.gsm.GsmSMSDispatcher;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public final class ImsSMSDispatcher extends SMSDispatcher {
    private static final int DAN_DELAY_TIMER = 15000;
    private static final int DAN_RETRY_TIMER = 1000;
    private static final int EVENT_DCN_TIMER_START = 2000;
    private static final int EVENT_DCN_TIMER_STOP = 2001;
    private static final String PROPERTY_IMS_REGISTERED = "persist.radio.ims.reg";
    public static final int SMS_STATUS_1X = 3;
    public static final int SMS_STATUS_3GPP = 4;
    public static final int SMS_STATUS_IMS = 2;
    public static final int SMS_STATUS_INVALID = 0;
    public static final int SMS_STATUS_NO_SMS = 1;
    private static final String TAG = "RIL_ImsSms";
    public static final Uri mFormatUri = Uri.parse("content://com.example.HiddenMenuContentProvider/IMSSETTINGSData");
    private static boolean mLimitedMode = false;
    private SMSDispatcher mCdmaDispatcher;
    private CdmaInboundSmsHandler mCdmaInboundSmsHandler;
    private boolean mDCNMessageTimer = false;
    private boolean mDCNPendingTimer = false;
    private int mDCNRetryCount = 0;
    private boolean mDanFail = false;
    private SMSDispatcher mGsmDispatcher;
    private GsmInboundSmsHandler mGsmInboundSmsHandler;
    private boolean mIms = false;
    private String mImsSmsFormat = "unknown";
    protected ContentResolver mResolver;
    private TelephonyManager mTelephonyManager = null;

    public ImsSMSDispatcher(PhoneBase phone, SmsStorageMonitor storageMonitor, SmsUsageMonitor usageMonitor) {
        super(phone, usageMonitor, null);
        Rlog.d(TAG, "ImsSMSDispatcher created");
        this.mCdmaDispatcher = new CdmaSMSDispatcher(phone, usageMonitor, this);
        this.mGsmInboundSmsHandler = GsmInboundSmsHandler.makeInboundSmsHandler(phone.getContext(), storageMonitor, phone);
        this.mCdmaInboundSmsHandler = CdmaInboundSmsHandler.makeInboundSmsHandler(phone.getContext(), storageMonitor, phone, (CdmaSMSDispatcher) this.mCdmaDispatcher);
        this.mGsmDispatcher = new GsmSMSDispatcher(phone, usageMonitor, this, this.mGsmInboundSmsHandler);
        new Thread(new SmsBroadcastUndelivered(phone.getContext(), this.mGsmInboundSmsHandler, this.mCdmaInboundSmsHandler)).start();
        IntentFilter filter = new IntentFilter();
        filter.addAction("android.intent.action.SIM_STATE_CHANGED");
        filter.addAction("android.intent.action.BOOT_COMPLETED");
        filter.addAction(SMSDispatcher.ACTION_SSMS_STATE_FILE_UPDATE);
        filter.addAction("android.intent.action.LTE_SMS_STATUS");
        this.mContext.registerReceiver(this.mResultReceiver, filter);
        this.mResolver = this.mContext.getContentResolver();
        this.mCi.registerForOn(this, 11, null);
        this.mCi.registerForImsNetworkStateChanged(this, 12, null);
    }

    protected void updatePhoneObject(PhoneBase phone) {
        Rlog.d(TAG, "In IMS updatePhoneObject ");
        super.updatePhoneObject(phone);
        this.mCdmaDispatcher.updatePhoneObject(phone);
        this.mGsmDispatcher.updatePhoneObject(phone);
        this.mGsmInboundSmsHandler.updatePhoneObject(phone);
        this.mCdmaInboundSmsHandler.updatePhoneObject(phone);
    }

    public void dispose() {
        this.mCi.unregisterForOn(this);
        this.mCi.unregisterForImsNetworkStateChanged(this);
        this.mGsmDispatcher.dispose();
        this.mCdmaDispatcher.dispose();
        this.mGsmInboundSmsHandler.dispose();
        this.mCdmaInboundSmsHandler.dispose();
    }

    public void handleMessage(Message msg) {
        switch (msg.what) {
            case 11:
            case 12:
                this.mCi.getImsRegistrationState(obtainMessage(13));
                return;
            case 13:
                AsyncResult ar = msg.obj;
                if (ar.exception == null) {
                    updateImsInfo(ar);
                    return;
                } else {
                    Rlog.e(TAG, "IMS State query failed with exp " + ar.exception);
                    return;
                }
            default:
                super.handleMessage(msg);
                return;
        }
    }

    private void setImsSmsFormat(int format) {
        switch (format) {
            case 1:
                this.mImsSmsFormat = SmsMessage.FORMAT_3GPP;
                return;
            case 2:
                this.mImsSmsFormat = SmsMessage.FORMAT_3GPP2;
                return;
            default:
                this.mImsSmsFormat = "unknown";
                return;
        }
    }

    private void updateImsInfo(AsyncResult ar) {
        int[] responseArray = (int[]) ar.result;
        this.mIms = false;
        if (responseArray[0] == 1) {
            Rlog.d(TAG, "IMS is registered!");
            this.mIms = true;
            mLimitedMode = false;
        } else if (responseArray[0] == 2) {
            Rlog.d(TAG, "IMS is registered! but limited mode");
            mLimitedMode = true;
        } else {
            Rlog.d(TAG, "IMS is NOT registered!");
            mLimitedMode = false;
        }
        setImsSmsFormat(responseArray[1]);
        if ("unknown".equals(this.mImsSmsFormat)) {
            Rlog.e(TAG, "IMS format was unknown!");
            this.mIms = false;
        }
    }

    protected void sendData(String destAddr, String scAddr, int destPort, byte[] data, PendingIntent sentIntent, PendingIntent deliveryIntent) {
        if (isCdmaMo()) {
            this.mCdmaDispatcher.sendData(destAddr, scAddr, destPort, data, sentIntent, deliveryIntent);
        } else {
            this.mGsmDispatcher.sendData(destAddr, scAddr, destPort, data, sentIntent, deliveryIntent);
        }
    }

    protected void sendDatawithOrigPort(String destAddr, String scAddr, int destPort, int origPort, byte[] data, PendingIntent sentIntent, PendingIntent deliveryIntent) {
        Rlog.d(TAG, "sendData");
        if (isCdmaMo()) {
            Rlog.d(TAG, "sendData is not supported");
        } else {
            this.mGsmDispatcher.sendDatawithOrigPort(destAddr, scAddr, destPort, origPort, data, sentIntent, deliveryIntent);
        }
    }

    protected void sendMultipartText(String destAddr, String scAddr, ArrayList<String> parts, ArrayList<PendingIntent> sentIntents, ArrayList<PendingIntent> deliveryIntents, Uri messageUri, String callingPkg) {
        if (isCdmaMo()) {
            this.mCdmaDispatcher.sendMultipartText(destAddr, scAddr, (ArrayList) parts, (ArrayList) sentIntents, (ArrayList) deliveryIntents, messageUri, callingPkg);
        } else {
            this.mGsmDispatcher.sendMultipartText(destAddr, scAddr, (ArrayList) parts, (ArrayList) sentIntents, (ArrayList) deliveryIntents, messageUri, callingPkg);
        }
    }

    protected void sendMultipartTextwithOptions(String destAddr, String scAddr, ArrayList<String> parts, ArrayList<PendingIntent> sentIntents, ArrayList<PendingIntent> deliveryIntents, Uri messageUri, String callingPkg, boolean replyPath, int expiry, int serviceType, int encodingType) {
        if (isCdmaMo()) {
            this.mCdmaDispatcher.sendMultipartTextwithOptions(destAddr, scAddr, parts, sentIntents, deliveryIntents, messageUri, callingPkg, replyPath, expiry, serviceType, encodingType);
        } else {
            this.mGsmDispatcher.sendMultipartTextwithOptions(destAddr, scAddr, parts, sentIntents, deliveryIntents, messageUri, callingPkg, replyPath, expiry, serviceType, encodingType);
        }
    }

    protected void sendSms(SmsTracker tracker) {
        Rlog.e(TAG, "sendSms should never be called from here!, so re-send SMS.");
        if (isCdmaMo()) {
            this.mCdmaDispatcher.sendSms(tracker);
        } else {
            this.mGsmDispatcher.sendSms(tracker);
        }
    }

    protected void sendSmsByPstn(SmsTracker tracker) {
        Rlog.e(TAG, "sendSmsByPstn should never be called from here!");
    }

    protected void updateSmsSendStatus(int messageRef, boolean success) {
        if (isCdmaMo()) {
            updateSmsSendStatusHelper(messageRef, this.mCdmaDispatcher.sendPendingList, this.mCdmaDispatcher, success);
            updateSmsSendStatusHelper(messageRef, this.mGsmDispatcher.sendPendingList, null, success);
            return;
        }
        updateSmsSendStatusHelper(messageRef, this.mGsmDispatcher.sendPendingList, this.mGsmDispatcher, success);
        updateSmsSendStatusHelper(messageRef, this.mCdmaDispatcher.sendPendingList, null, success);
    }

    private void updateSmsSendStatusHelper(int messageRef, List<SmsTracker> sendPendingList, SMSDispatcher smsDispatcher, boolean success) {
        synchronized (sendPendingList) {
            int i = 0;
            int count = sendPendingList.size();
            while (i < count) {
                SmsTracker tracker = (SmsTracker) sendPendingList.get(i);
                if (tracker.mMessageRef == messageRef) {
                    sendPendingList.remove(i);
                    if (success) {
                        Rlog.d(TAG, "Sending SMS by IP succeeded.");
                        sendMessage(obtainMessage(2, new AsyncResult(tracker, null, null)));
                    } else {
                        Rlog.d(TAG, "Sending SMS by IP failed.");
                        if (smsDispatcher != null) {
                            smsDispatcher.sendSmsByPstn(tracker);
                        } else {
                            Rlog.e(TAG, "No feasible way to send this SMS.");
                        }
                    }
                } else {
                    i++;
                }
            }
        }
    }

    public void sendDomainChangeSms(byte type) {
        if (isCdmaMo()) {
            this.mDcnAddress = getDcnAddress();
            this.mCdmaDispatcher.sendDomainChangeSms(type);
            return;
        }
        Rlog.e(TAG, "DomainChangeSMS is not supported in GsmSmsDispatcher");
        setDanFail(true);
    }

    protected void sendSms(SmsTracker tracker, String format) {
        Rlog.d(TAG, "sendSms with smsformat.");
        if (SmsMessage.FORMAT_3GPP2.equals(format)) {
            this.mCdmaDispatcher.sendSms(tracker);
        } else {
            this.mGsmDispatcher.sendSms(tracker);
        }
    }

    protected void sendText(String destAddr, String scAddr, String text, PendingIntent sentIntent, PendingIntent deliveryIntent, Uri messageUri, String callingPkg) {
        Rlog.d(TAG, "sendText");
        if (isCdmaMo()) {
            this.mCdmaDispatcher.sendText(destAddr, scAddr, text, sentIntent, deliveryIntent, messageUri, callingPkg);
        } else {
            this.mGsmDispatcher.sendText(destAddr, scAddr, text, sentIntent, deliveryIntent, messageUri, callingPkg);
        }
    }

    protected void sendText(String destAddr, String scAddr, String text, PendingIntent sentIntent, PendingIntent deliveryIntent, String callbackNumber, int priority) {
        Rlog.d(TAG, "sendTextCBP");
        if (isCdmaMo()) {
            this.mCdmaDispatcher.sendText(destAddr, scAddr, text, sentIntent, deliveryIntent, callbackNumber, priority);
        } else {
            this.mGsmDispatcher.sendText(destAddr, scAddr, text, sentIntent, deliveryIntent, callbackNumber, priority);
        }
    }

    protected void sendTextwithOptions(String destAddr, String scAddr, String text, PendingIntent sentIntent, PendingIntent deliveryIntent, Uri messageUri, String callingPkg, boolean replyPath, int expiry, int serviceType, int encodingType, int confirmId) {
        Rlog.d(TAG, "sendTextwithOptions");
        if (isCdmaMo()) {
            this.mCdmaDispatcher.sendTextwithOptions(destAddr, scAddr, text, sentIntent, deliveryIntent, messageUri, callingPkg, replyPath, expiry, serviceType, encodingType, confirmId);
        } else {
            this.mGsmDispatcher.sendTextwithOptions(destAddr, scAddr, text, sentIntent, deliveryIntent, messageUri, callingPkg, replyPath, expiry, serviceType, encodingType, confirmId);
        }
    }

    protected void sendMultipartText(String destAddr, String scAddr, ArrayList<String> parts, ArrayList<PendingIntent> sentIntents, ArrayList<PendingIntent> deliveryIntents, String callbackNumber, int priority) {
        Rlog.d(TAG, "sendMultipartTextwithCBP");
        if (isCdmaMo()) {
            this.mCdmaDispatcher.sendMultipartText(destAddr, scAddr, (ArrayList) parts, (ArrayList) sentIntents, (ArrayList) deliveryIntents, callbackNumber, priority);
        } else {
            this.mGsmDispatcher.sendMultipartText(destAddr, scAddr, (ArrayList) parts, (ArrayList) sentIntents, (ArrayList) deliveryIntents, callbackNumber, priority);
        }
    }

    protected void sendOTADomestic(String destAddr, String scAddr, String text) {
        Rlog.d(TAG, "sendOTADomestic");
        if (isCdmaMo()) {
            this.mCdmaDispatcher.sendOTADomestic(destAddr, scAddr, text);
        } else {
            this.mGsmDispatcher.sendOTADomestic(destAddr, scAddr, text);
        }
    }

    protected void sendTextNSRI(String destAddr, String scAddr, byte[] text, PendingIntent sentIntent, PendingIntent deliveryIntent, int msgCount, int msgTotal) {
        Rlog.d(TAG, "sendTextNSRI");
        if (isCdmaMo()) {
            Rlog.e(TAG, "sendTextNSRI is not supported in 3gpp2");
        } else {
            this.mGsmDispatcher.sendTextNSRI(destAddr, scAddr, text, sentIntent, deliveryIntent, msgCount, msgTotal);
        }
    }

    protected void injectSmsPdu(byte[] pdu, String format, PendingIntent receivedIntent) {
        Rlog.d(TAG, "ImsSMSDispatcher:injectSmsPdu");
        try {
            SmsMessage msg = SmsMessage.createFromPdu(pdu, format);
            if (msg.getMessageClass() == MessageClass.CLASS_1) {
                AsyncResult ar = new AsyncResult(receivedIntent, msg, null);
                if (format.equals(SmsMessage.FORMAT_3GPP)) {
                    Rlog.i(TAG, "ImsSMSDispatcher:injectSmsText Sending msg=" + msg + ", format=" + format + "to mGsmInboundSmsHandler");
                    this.mGsmInboundSmsHandler.sendMessage(8, ar);
                } else if (format.equals(SmsMessage.FORMAT_3GPP2)) {
                    Rlog.i(TAG, "ImsSMSDispatcher:injectSmsText Sending msg=" + msg + ", format=" + format + "to mCdmaInboundSmsHandler");
                    this.mCdmaInboundSmsHandler.sendMessage(8, ar);
                } else {
                    Rlog.e(TAG, "Invalid pdu format: " + format);
                    if (receivedIntent != null) {
                        receivedIntent.send(2);
                    }
                }
            } else if (receivedIntent != null) {
                receivedIntent.send(2);
            }
        } catch (Exception e) {
            Rlog.e(TAG, "injectSmsPdu failed: ", e);
            if (receivedIntent != null) {
                try {
                    receivedIntent.send(2);
                } catch (CanceledException e2) {
                }
            }
        }
    }

    public void sendRetrySms(SmsTracker tracker) {
        String newFormat;
        String oldFormat = tracker.mFormat;
        if (2 == this.mPhone.getPhoneType()) {
            newFormat = this.mCdmaDispatcher.getFormat();
        } else {
            newFormat = this.mGsmDispatcher.getFormat();
        }
        if (!oldFormat.equals(newFormat)) {
            HashMap map = tracker.mData;
            if (map.containsKey("scAddr") && map.containsKey("destAddr") && (map.containsKey(Part.TEXT) || (map.containsKey("data") && map.containsKey("destPort")))) {
                String scAddr = (String) map.get("scAddr");
                String destAddr = (String) map.get("destAddr");
                SubmitPduBase pdu = null;
                if (map.containsKey(Part.TEXT)) {
                    Rlog.d(TAG, "sms failed was text");
                    String text = (String) map.get(Part.TEXT);
                    if (isCdmaFormat(newFormat)) {
                        boolean z;
                        Rlog.d(TAG, "old format (gsm) ==> new format (cdma)");
                        if (tracker.mDeliveryIntent != null) {
                            z = true;
                        } else {
                            z = false;
                        }
                        pdu = com.android.internal.telephony.cdma.SmsMessage.getSubmitPdu(scAddr, destAddr, text, z, null);
                    } else {
                        Rlog.d(TAG, "old format (cdma) ==> new format (gsm)");
                        pdu = com.android.internal.telephony.gsm.SmsMessage.getSubmitPdu(scAddr, destAddr, text, tracker.mDeliveryIntent != null, null);
                    }
                } else if (map.containsKey("data")) {
                    Rlog.d(TAG, "sms failed was data");
                    byte[] data = (byte[]) map.get("data");
                    Integer destPort = (Integer) map.get("destPort");
                    if (isCdmaFormat(newFormat)) {
                        Rlog.d(TAG, "old format (gsm) ==> new format (cdma)");
                        pdu = com.android.internal.telephony.cdma.SmsMessage.getSubmitPdu(scAddr, destAddr, destPort.intValue(), data, tracker.mDeliveryIntent != null);
                    } else {
                        Rlog.d(TAG, "old format (cdma) ==> new format (gsm)");
                        pdu = com.android.internal.telephony.gsm.SmsMessage.getSubmitPdu(scAddr, destAddr, destPort.intValue(), data, tracker.mDeliveryIntent != null);
                    }
                }
                map.put("smsc", pdu.encodedScAddress);
                map.put("pdu", pdu.encodedMessage);
                SMSDispatcher dispatcher = isCdmaFormat(newFormat) ? this.mCdmaDispatcher : this.mGsmDispatcher;
                tracker.mFormat = dispatcher.getFormat();
                dispatcher.sendSms(tracker);
                return;
            }
            Rlog.e(TAG, "sendRetrySms failed to re-encode per missing fields!");
            tracker.onFailed(this.mContext, 1, 0);
        } else if (isCdmaFormat(newFormat)) {
            Rlog.d(TAG, "old format matched new format (cdma)");
            this.mCdmaDispatcher.sendSms(tracker);
        } else {
            Rlog.d(TAG, "old format matched new format (gsm)");
            this.mGsmDispatcher.sendSms(tracker);
        }
    }

    protected String getFormat() {
        Rlog.e(TAG, "getFormat should never be called from here!");
        return "unknown";
    }

    protected TextEncodingDetails calculateLength(CharSequence messageBody, boolean use7bitOnly) {
        Rlog.e(TAG, "Error! Not implemented for IMS.");
        return null;
    }

    protected void sendNewSubmitPdu(String destinationAddress, String scAddress, String message, SmsHeader smsHeader, int format, PendingIntent sentIntent, PendingIntent deliveryIntent, boolean lastPart, AtomicInteger unsentPartCount, AtomicBoolean anyPartFailed, Uri messageUri) {
        Rlog.e(TAG, "Error! Not implemented for IMS.");
    }

    public boolean isIms() {
        return this.mIms;
    }

    public static boolean isLimitedMode() {
        return mLimitedMode;
    }

    public String getImsSmsFormat() {
        return this.mImsSmsFormat;
    }

    private boolean isCdmaMo() {
        Rlog.d(TAG, "[lteStatus] : " + this.mLteSmsStatus);
        if (isIms()) {
            return isCdmaFormat(this.mImsSmsFormat);
        }
        if ((!"VZW-CDMA".equals("") || !useLte3GPPSms()) && 2 == this.mPhone.getPhoneType()) {
            return true;
        }
        return false;
    }

    public boolean useLte3GPPSms() {
        if (this.mLteSmsStatus != 0 && this.mLteSmsStatus == 4) {
            return true;
        }
        return false;
    }

    private boolean isCdmaFormat(String format) {
        return this.mCdmaDispatcher.getFormat().equals(format);
    }

    protected void dispatchSmsServiceCenter(String smsc_hexstring) {
        if (isCdmaMo()) {
            this.mCdmaDispatcher.dispatchSmsServiceCenter(smsc_hexstring);
        } else {
            this.mGsmDispatcher.dispatchSmsServiceCenter(smsc_hexstring);
        }
    }

    public void clearDuplicatedCbMessages() {
        this.mGsmInboundSmsHandler.clearDuplicatedCbMessages();
    }

    public String getSmsc() {
        Rlog.e(TAG, "getSmsc in ImsSMSDispatcher");
        if (isCdmaMo()) {
            return this.mCdmaDispatcher.getSmsc();
        }
        return this.mGsmDispatcher.getSmsc();
    }

    public String getDcnAddress() {
        String address;
        Cursor c = null;
        try {
            c = this.mResolver.query(mFormatUri, new String[]{"mDcnNumber"}, null, null, null);
            if (c.getCount() > 0) {
                c.moveToFirst();
                Rlog.d(TAG, "Domain Change Address : " + c.getString(0));
                address = c.getString(0);
                if (c != null) {
                    c.close();
                }
            } else {
                Rlog.e(TAG, "Cursor < 1");
                address = "4437501000";
                if (c != null) {
                    c.close();
                }
            }
        } catch (Exception e) {
            address = "4437501000";
            if (c != null) {
                c.close();
            }
        } catch (Throwable th) {
            if (c != null) {
                c.close();
            }
        }
        return address;
    }

    public boolean getDanFail() {
        return this.mDanFail;
    }

    public boolean setDanFail(boolean danFail) {
        this.mDanFail = danFail;
        return this.mDanFail;
    }

    public boolean restartDanTimer() {
        if (!this.mDCNPendingTimer) {
            return false;
        }
        Rlog.d(TAG, "DAN timer restart!");
        this.mDCNPendingTimer = false;
        sendMessageDelayed(obtainMessage(EVENT_DCN_TIMER_STOP), 15000);
        return true;
    }

    protected void sendRawPduSat(byte[] smsc, byte[] pdu, String format, PendingIntent sentIntent, PendingIntent deliveryIntent) {
        Rlog.d(TAG, "sendRawPduSat: smsc= " + smsc + " pdu= " + pdu + " format= " + format + " sentIntent " + sentIntent + " deliveryIntent " + deliveryIntent);
        HashMap<String, Object> map = new HashMap();
        map.put("smsc", smsc);
        map.put("pdu", pdu);
        sendSms(getSmsTracker(map, sentIntent, deliveryIntent, format, null, false), format);
    }
}
