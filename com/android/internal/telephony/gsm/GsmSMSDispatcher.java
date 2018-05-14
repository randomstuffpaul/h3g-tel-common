package com.android.internal.telephony.gsm;

import android.app.PendingIntent;
import android.app.PendingIntent.CanceledException;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Intent;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.AsyncResult;
import android.os.Handler;
import android.os.Message;
import android.provider.Telephony.CellBroadcasts;
import android.provider.Telephony.Sms.Intents;
import android.telephony.Rlog;
import android.telephony.SmsMessage;
import android.telephony.SubscriptionManager;
import com.android.internal.telephony.GsmAlphabet.TextEncodingDetails;
import com.android.internal.telephony.ImsSMSDispatcher;
import com.android.internal.telephony.PhoneBase;
import com.android.internal.telephony.SMSDispatcher;
import com.android.internal.telephony.SmsApplication;
import com.android.internal.telephony.SmsHeader;
import com.android.internal.telephony.SmsHeader.ConcatRef;
import com.android.internal.telephony.SmsHeader.KTReadConfirm;
import com.android.internal.telephony.SmsMessageBase.SubmitPduBase;
import com.android.internal.telephony.SmsUsageMonitor;
import com.android.internal.telephony.gsm.SmsMessage.SubmitPdu;
import com.android.internal.telephony.uicc.IccRecords;
import com.android.internal.telephony.uicc.IccUtils;
import com.android.internal.telephony.uicc.UiccCardApplication;
import com.android.internal.telephony.uicc.UiccController;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class GsmSMSDispatcher extends SMSDispatcher {
    private static final int EVENT_NEW_SMS_STATUS_REPORT = 100;
    private static int SMSC_ADDRESS_LENGTH = 21;
    private static final String TAG = "GsmSMSDispatcher";
    private static final boolean VDBG = false;
    private static final String hexDigitChars = "0123456789abcdef";
    private DomainPreferenceObserver mDomainPrefObserver;
    private GsmInboundSmsHandler mGsmInboundSmsHandler;
    private AtomicReference<IccRecords> mIccRecords = new AtomicReference();
    private AtomicReference<UiccCardApplication> mUiccApplication = new AtomicReference();
    protected UiccController mUiccController = null;

    private class DomainPreferenceObserver extends ContentObserver {
        public DomainPreferenceObserver() {
            super(new Handler());
            onChange(false, null);
        }

        public void register() {
            ContentResolver cr = GsmSMSDispatcher.this.mContext.getContentResolver();
        }

        public void unRegister() {
            GsmSMSDispatcher.this.mContext.getContentResolver().unregisterContentObserver(this);
        }

        public void onChange(boolean selfChange) {
            onChange(selfChange, null);
        }

        public void onChange(boolean selfChange, Uri uri) {
            ContentResolver cr = GsmSMSDispatcher.this.mContext.getContentResolver();
        }
    }

    public GsmSMSDispatcher(PhoneBase phone, SmsUsageMonitor usageMonitor, ImsSMSDispatcher imsSMSDispatcher, GsmInboundSmsHandler gsmInboundSmsHandler) {
        super(phone, usageMonitor, imsSMSDispatcher);
        this.mCi.setOnSmsStatus(this, 100, null);
        this.mCi.registerForRadioStateChanged(this, 19, null);
        this.mCi.setOnSmsDeviceReady(this, 16, null);
        this.mGsmInboundSmsHandler = gsmInboundSmsHandler;
        this.mUiccController = UiccController.getInstance();
        this.mUiccController.registerForIccChanged(this, 15, null);
        Rlog.d(TAG, "GsmSMSDispatcher created");
    }

    public void dispose() {
        super.dispose();
        this.mCi.unSetOnSmsStatus(this);
        this.mUiccController.unregisterForIccChanged(this);
        this.mCi.unSetOnSmsDeviceReady(this);
        this.mCi.unregisterForRadioStateChanged(this);
    }

    protected String getFormat() {
        return SmsMessage.FORMAT_3GPP;
    }

    public void handleMessage(Message msg) {
        switch (msg.what) {
            case 14:
                this.mGsmInboundSmsHandler.sendMessage(1, msg.obj);
                return;
            case 15:
                onUpdateIccAvailability();
                return;
            case 100:
                handleStatusReport((AsyncResult) msg.obj);
                return;
            default:
                super.handleMessage(msg);
                return;
        }
    }

    private void handleStatusReport(AsyncResult ar) {
        String pduString = ar.result;
        SmsMessage sms = SmsMessage.newFromCDS(pduString);
        if (sms != null) {
            int tpStatus = sms.getStatus();
            int messageRef = sms.mMessageRef;
            Rlog.d(TAG, "handleStatusReport deliveryPendingList.size(): " + this.deliveryPendingList.size());
            int i = 0;
            int count = this.deliveryPendingList.size();
            while (i < count) {
                SmsTracker tracker = (SmsTracker) this.deliveryPendingList.get(i);
                Rlog.d(TAG, "handleStatusReport tracker.mMessageRef: " + tracker.mMessageRef + " messageRef: " + messageRef);
                if (tracker.mMessageRef == messageRef) {
                    if (tpStatus >= 64 || tpStatus < 32) {
                        this.deliveryPendingList.remove(i);
                        tracker.updateSentMessageStatus(this.mContext, tpStatus);
                    }
                    PendingIntent intent = tracker.mDeliveryIntent;
                    Intent fillIn = new Intent();
                    fillIn.putExtra("pdu", IccUtils.hexStringToBytes(pduString));
                    fillIn.putExtra(CellBroadcasts.MESSAGE_FORMAT, getFormat());
                    SubscriptionManager.putPhoneIdAndSubIdExtra(fillIn, this.mPhone.getPhoneId());
                    try {
                        intent.send(this.mContext, -1, fillIn);
                    } catch (CanceledException e) {
                    }
                } else {
                    i++;
                }
            }
        }
        this.mCi.acknowledgeLastIncomingGsmSms(true, 1, null);
    }

    public void sendDomainChangeSms(byte type) {
        Rlog.e(TAG, "DomainChangeSMS is not supported in GsmSmsDispatcher");
    }

    protected void sendData(String destAddr, String scAddr, int destPort, byte[] data, PendingIntent sentIntent, PendingIntent deliveryIntent) {
        if (isSMSBlocked(destAddr, true)) {
            try {
                Intent intent = new Intent();
                intent.putExtra(SMSDispatcher.LAST_SENT_MSG_EXTRA, true);
                sentIntent.send(this.mContext, 1, intent);
                return;
            } catch (Exception e) {
                e.printStackTrace();
                return;
            }
        }
        SubmitPdu pdu = SmsMessage.getSubmitPdu(scAddr, destAddr, destPort, data, deliveryIntent != null);
        if (pdu != null) {
            sendRawPdu(getSmsTracker(getSmsTrackerMap(destAddr, scAddr, destPort, data, pdu), sentIntent, deliveryIntent, getFormat(), null, false));
        } else {
            Rlog.e(TAG, "GsmSMSDispatcher.sendData(): getSubmitPdu() returned null");
        }
    }

    protected void sendDatawithOrigPort(String destAddr, String scAddr, int destPort, int origPort, byte[] data, PendingIntent sentIntent, PendingIntent deliveryIntent) {
        if (isSMSBlocked(destAddr, true)) {
            try {
                Intent intent = new Intent();
                intent.putExtra(SMSDispatcher.LAST_SENT_MSG_EXTRA, true);
                sentIntent.send(this.mContext, 1, intent);
                return;
            } catch (Exception e) {
                e.printStackTrace();
                return;
            }
        }
        SubmitPdu pdu = SmsMessage.getSubmitPdu(scAddr, destAddr, destPort, origPort, data, deliveryIntent != null);
        if (pdu != null) {
            sendRawPdu(getSmsTracker(getSmsTrackerMap(destAddr, scAddr, destPort, origPort, data, pdu), sentIntent, deliveryIntent, getFormat(), null, false));
        } else {
            Rlog.e(TAG, "GsmSMSDispatcher.sendData(): getSubmitPdu() returned null");
        }
    }

    protected void sendText(String destAddr, String scAddr, String text, PendingIntent sentIntent, PendingIntent deliveryIntent, Uri messageUri, String callingPkg) {
        if (isSMSBlocked(destAddr, true)) {
            try {
                Intent intent = new Intent();
                intent.putExtra(SMSDispatcher.LAST_SENT_MSG_EXTRA, true);
                sentIntent.send(this.mContext, 1, intent);
                return;
            } catch (Exception e) {
                e.printStackTrace();
                return;
            }
        }
        SubmitPdu pdu = SmsMessage.getSubmitPdu(scAddr, destAddr, text, deliveryIntent != null);
        if (pdu != null) {
            if (messageUri == null) {
                if (SmsApplication.shouldWriteMessageForPackage(callingPkg, this.mContext)) {
                    messageUri = writeOutboxMessage(getSubId(), destAddr, text, deliveryIntent != null, callingPkg);
                }
            } else {
                moveToOutbox(getSubId(), messageUri, callingPkg);
            }
            sendRawPdu(getSmsTracker(getSmsTrackerMap(destAddr, scAddr, text, pdu), sentIntent, deliveryIntent, getFormat(), messageUri, false));
            storeSMS(destAddr, Long.valueOf(Calendar.getInstance().getTimeInMillis()).toString(), text, false);
            return;
        }
        Rlog.e(TAG, "GsmSMSDispatcher.sendText(): getSubmitPdu() returned null");
    }

    protected void sendText(String destAddr, String scAddr, String text, PendingIntent sentIntent, PendingIntent deliveryIntent, String callbackNumber, int priority) {
        SubmitPdu pdu = SmsMessage.getSubmitPdu(scAddr, destAddr, text, deliveryIntent != null);
        if (scAddr == null) {
            scAddr = this.Sim_Smsc;
        }
        if (pdu != null) {
            sendRawPdu(getSmsTracker(getSmsTrackerMap(destAddr, scAddr, text, pdu), sentIntent, deliveryIntent, getFormat(), null, false));
        } else {
            Rlog.e(TAG, "GsmSMSDispatcher.sendText(): getSubmitPdu() returned null");
        }
    }

    protected void sendMultipartText(String destinationAddress, String scAddress, ArrayList<String> parts, ArrayList<PendingIntent> sentIntents, ArrayList<PendingIntent> deliveryIntents, String callbackNumber, int priority) {
        int i;
        int refNumber = SMSDispatcher.getNextConcatenatedRef() & 255;
        int msgCount = parts.size();
        int encoding = 0;
        TextEncodingDetails[] encodingForParts = new TextEncodingDetails[msgCount];
        for (i = 0; i < msgCount; i++) {
            TextEncodingDetails details = SmsMessage.calculateLength((CharSequence) parts.get(i), false);
            if (encoding != details.codeUnitSize && (encoding == 0 || encoding == 1)) {
                encoding = details.codeUnitSize;
            }
            encodingForParts[i] = details;
        }
        i = 0;
        while (i < msgCount) {
            ConcatRef concatRef = new ConcatRef();
            concatRef.refNumber = refNumber;
            concatRef.seqNumber = i + 1;
            concatRef.msgCount = msgCount;
            concatRef.isEightBits = true;
            SmsHeader smsHeader = new SmsHeader();
            smsHeader.concatRef = concatRef;
            if (encoding == 1) {
                smsHeader.languageTable = encodingForParts[i].languageTable;
                smsHeader.languageShiftTable = encodingForParts[i].languageShiftTable;
            }
            PendingIntent sentIntent = null;
            if (sentIntents != null && sentIntents.size() > i) {
                sentIntent = (PendingIntent) sentIntents.get(i);
            }
            PendingIntent deliveryIntent = null;
            if (deliveryIntents != null && deliveryIntents.size() > i) {
                deliveryIntent = (PendingIntent) deliveryIntents.get(i);
            }
            SubmitPduBase pdus = SmsMessage.getSubmitPdu(scAddress, destinationAddress, (String) parts.get(i), deliveryIntent != null, SmsHeader.toByteArray(smsHeader), encoding, smsHeader.languageTable, smsHeader.languageShiftTable);
            if (pdus != null) {
                HashMap map = getSmsTrackerMap(destinationAddress, scAddress, (String) parts.get(i), pdus);
                map.put("curIndex", Integer.valueOf(concatRef.seqNumber));
                map.put("totalCnt", Integer.valueOf(concatRef.msgCount));
                sendSms(getSmsTracker(map, sentIntent, deliveryIntent, getFormat(), null, concatRef.seqNumber < concatRef.msgCount));
            } else {
                Rlog.e(TAG, "GsmSMSDispatcher.sendMultipartText(): getSubmitPdu() returned null");
            }
            i++;
        }
    }

    protected void sendTextwithOptions(String destAddr, String scAddr, String text, PendingIntent sentIntent, PendingIntent deliveryIntent, Uri messageUri, String callingPkg, boolean replyPath, int expiry, int serviceType, int encodingType, int confirmId) {
        Rlog.d(TAG, "sendTextwithOptions ");
        if (isSMSBlocked(destAddr, true)) {
            try {
                Intent intent = new Intent();
                intent.putExtra(SMSDispatcher.LAST_SENT_MSG_EXTRA, true);
                sentIntent.send(this.mContext, 1, intent);
                return;
            } catch (Exception e) {
                e.printStackTrace();
                return;
            }
        }
        SubmitPduBase pdu;
        if (scAddr == null) {
            scAddr = this.Sim_Smsc;
        }
        TextEncodingDetails details = SmsMessage.calculateLength(text, false);
        if (null == null) {
            pdu = SmsMessage.getSubmitPdu(scAddr, destAddr, text, deliveryIntent != null, replyPath, expiry, serviceType, encodingType, details.languageTable, details.languageShiftTable);
        } else {
            pdu = SmsMessage.getSubmitPdu(scAddr, destAddr, text, deliveryIntent != null, SmsHeader.toByteArray(null), replyPath, expiry, serviceType, encodingType);
        }
        if (pdu != null) {
            if (messageUri == null) {
                if (SmsApplication.shouldWriteMessageForPackage(callingPkg, this.mContext)) {
                    messageUri = writeOutboxMessage(getSubId(), destAddr, text, deliveryIntent != null, callingPkg);
                }
            } else {
                moveToOutbox(getSubId(), messageUri, callingPkg);
            }
            sendRawPdu(getSmsTracker(getSmsTrackerMap(destAddr, scAddr, text, pdu), sentIntent, deliveryIntent, getFormat(), messageUri, 1 < 1));
            storeSMS(destAddr, Long.valueOf(Calendar.getInstance().getTimeInMillis()).toString(), text, false);
            return;
        }
        Rlog.e(TAG, "GsmSMSDispatcher.sendTextwithOptions(): getSubmitPdu() returned null");
    }

    protected void sendMultipartTextwithOptions(String destinationAddress, String scAddress, ArrayList<String> parts, ArrayList<PendingIntent> sentIntents, ArrayList<PendingIntent> deliveryIntents, Uri messageUri, String callingPkg, boolean replyPath, int expiry, int serviceType, int encodingType) {
        int i;
        Rlog.d(TAG, "sendMultipartTextwithOptions");
        if (messageUri == null) {
            if (SmsApplication.shouldWriteMessageForPackage(callingPkg, this.mContext)) {
                boolean z;
                long subId = getSubId();
                String multipartMessageText = getMultipartMessageText(parts);
                if (deliveryIntents == null || deliveryIntents.size() <= 0) {
                    z = false;
                } else {
                    z = true;
                }
                messageUri = writeOutboxMessage(subId, destinationAddress, multipartMessageText, z, callingPkg);
            }
        } else {
            moveToOutbox(getSubId(), messageUri, callingPkg);
        }
        if (scAddress == null) {
            scAddress = this.Sim_Smsc;
        }
        int refNumber = SMSDispatcher.getNextConcatenatedRef() & 255;
        int msgCount = parts.size();
        int encoding = 0;
        TextEncodingDetails[] encodingForParts = new TextEncodingDetails[msgCount];
        for (i = 0; i < msgCount; i++) {
            TextEncodingDetails details = SmsMessage.calculateLength((CharSequence) parts.get(i), false);
            if (encoding != details.codeUnitSize && (encoding == 0 || encoding == 1)) {
                encoding = details.codeUnitSize;
            }
            encodingForParts[i] = details;
        }
        AtomicInteger atomicInteger = new AtomicInteger(msgCount);
        AtomicBoolean anyPartFailed = new AtomicBoolean(false);
        i = 0;
        while (i < msgCount) {
            ConcatRef concatRef = new ConcatRef();
            concatRef.refNumber = refNumber;
            concatRef.seqNumber = i + 1;
            concatRef.msgCount = msgCount;
            concatRef.isEightBits = true;
            SmsHeader smsHeader = new SmsHeader();
            smsHeader.concatRef = concatRef;
            if (encoding == 1) {
                smsHeader.languageTable = encodingForParts[i].languageTable;
                smsHeader.languageShiftTable = encodingForParts[i].languageShiftTable;
            }
            PendingIntent sentIntent = null;
            if (sentIntents != null && sentIntents.size() > i) {
                sentIntent = (PendingIntent) sentIntents.get(i);
            }
            PendingIntent deliveryIntent = null;
            if (deliveryIntents != null && deliveryIntents.size() > i) {
                deliveryIntent = (PendingIntent) deliveryIntents.get(i);
            }
            sendRawPdu(getSmsTracker(getSmsTrackerMap(destinationAddress, scAddress, (String) parts.get(i), SmsMessage.getSubmitPdu(scAddress, destinationAddress, (String) parts.get(i), deliveryIntent != null, SmsHeader.toByteArray(smsHeader), replyPath, expiry, serviceType, encoding == 3 ? 1 : encodingType, smsHeader.languageTable, smsHeader.languageShiftTable)), sentIntent, deliveryIntent, getFormat(), messageUri, concatRef.seqNumber < concatRef.msgCount));
            i++;
        }
    }

    protected void sendOTADomestic(String destAddr, String scAddr, String text) {
        Rlog.d(TAG, "sendOTADomestic: feature turn off");
    }

    protected void sendTextNSRI(String destAddr, String scAddr, byte[] text, PendingIntent sentIntent, PendingIntent deliveryIntent, int msgCount, int msgTotal) {
        Rlog.d(TAG, "[NSRI_SMS_SEND] sendTextNSRI ");
        String strText = null;
        try {
            strText = new String(text, 0, text.length, "ISO8859_1");
        } catch (Exception e) {
            Rlog.d(TAG, "dispatchMessage EUC_KR converting error");
        }
        Rlog.d(TAG, "[NSRI_SMS_SEND] : bytesToHexString=" + strText);
        TextEncodingDetails details = SmsMessage.calculateLength(strText, false);
        sendSms(getSmsTracker(getSmsTrackerMap(destAddr, scAddr, strText, SmsMessage.getSubmitPdu(scAddr, destAddr, strText, deliveryIntent != null, false, 0, 0, 2, details.languageTable, details.languageShiftTable)), sentIntent, deliveryIntent, getFormat(), null, msgCount < msgTotal));
    }

    protected void injectSmsPdu(byte[] pdu, String format, PendingIntent receivedIntent) {
        throw new IllegalStateException("This method must be called only on ImsSMSDispatcher");
    }

    protected TextEncodingDetails calculateLength(CharSequence messageBody, boolean use7bitOnly) {
        return SmsMessage.calculateLength(messageBody, use7bitOnly);
    }

    protected void sendNewSubmitPdu(String destinationAddress, String scAddress, String message, SmsHeader smsHeader, int encoding, PendingIntent sentIntent, PendingIntent deliveryIntent, boolean lastPart, AtomicInteger unsentPartCount, AtomicBoolean anyPartFailed, Uri messageUri) {
        if (!isSMSBlocked(destinationAddress, true)) {
            SubmitPduBase pdu = SmsMessage.getSubmitPdu(scAddress, destinationAddress, message, deliveryIntent != null, SmsHeader.toByteArray(smsHeader), encoding, smsHeader.languageTable, smsHeader.languageShiftTable);
            if (pdu != null) {
                sendRawPdu(getSmsTracker(getSmsTrackerMap(destinationAddress, scAddress, message, pdu), sentIntent, deliveryIntent, getFormat(), unsentPartCount, anyPartFailed, messageUri, smsHeader, !lastPart));
            } else {
                Rlog.e(TAG, "GsmSMSDispatcher.sendNewSubmitPdu(): getSubmitPdu() returned null");
            }
            storeSMS(destinationAddress, Long.valueOf(Calendar.getInstance().getTimeInMillis()).toString(), message, false);
        } else if (sentIntent != null) {
            try {
                Intent intent = new Intent();
                intent.putExtra(SMSDispatcher.LAST_SENT_MSG_EXTRA, true);
                sentIntent.send(this.mContext, 1, intent);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    protected void sendSms(SmsTracker tracker) {
        HashMap<String, Object> map = tracker.mData;
        if (isSMSBlocked((String) map.get("destination"), true)) {
            try {
                Intent intent = new Intent();
                intent.putExtra(SMSDispatcher.LAST_SENT_MSG_EXTRA, true);
                tracker.mSentIntent.send(this.mContext, 1, intent);
                return;
            } catch (Exception e) {
                e.printStackTrace();
                return;
            }
        }
        byte[] pdu = (byte[]) map.get("pdu");
        if (tracker.mRetryCount > 0) {
            Rlog.d(TAG, "sendSms:  mRetryCount=" + tracker.mRetryCount + " mMessageRef=" + tracker.mMessageRef + " SS=" + this.mPhone.getServiceState().getState());
            if ((pdu[0] & 1) == 1) {
                pdu[0] = (byte) (pdu[0] | 4);
                pdu[1] = (byte) tracker.mMessageRef;
            }
        }
        Rlog.d(TAG, "sendSms:  isIms()=" + isIms() + " mRetryCount=" + tracker.mRetryCount + " mImsRetry=" + tracker.mImsRetry + " mMessageRef=" + tracker.mMessageRef + " SS=" + this.mPhone.getServiceState().getState());
        BroadcastReceiver resultReceiver = new SMSDispatcherReceiver(tracker);
        intent = new Intent(Intents.SMS_SEND_ACTION);
        String carrierPackage = getCarrierAppPackageName(intent);
        Rlog.d(TAG, "sendSms: carrierPackage = " + carrierPackage);
        if (carrierPackage != null) {
            intent.setPackage(carrierPackage);
            intent.putExtra("pdu", pdu);
            intent.putExtra("smsc", (byte[]) map.get("smsc"));
            intent.putExtra(CellBroadcasts.MESSAGE_FORMAT, getFormat());
            if (!(tracker.mSmsHeader == null || tracker.mSmsHeader.concatRef == null)) {
                ConcatRef concatRef = tracker.mSmsHeader.concatRef;
                intent.putExtra("concat.refNumber", concatRef.refNumber);
                intent.putExtra("concat.seqNumber", concatRef.seqNumber);
                intent.putExtra("concat.msgCount", concatRef.msgCount);
            }
            intent.addFlags(134217728);
            Rlog.d(TAG, "Sending SMS by carrier app.");
            this.mContext.sendOrderedBroadcast(intent, "android.permission.RECEIVE_SMS", 16, resultReceiver, null, 0, null, null);
            return;
        }
        sendSmsByPstn(tracker);
    }

    protected void sendSmsByPstn(SmsTracker tracker) {
        int ss = this.mPhone.getServiceState().getState();
        if (isIms() || ss == 0) {
            HashMap<String, Object> map = tracker.mData;
            byte[] smsc = (byte[]) map.get("smsc");
            byte[] pdu = (byte[]) map.get("pdu");
            Message reply = obtainMessage(2, tracker);
            if (tracker.mRetryCount > 0 && (pdu[0] & 1) == 1) {
                pdu[0] = (byte) (pdu[0] | 4);
                pdu[1] = (byte) tracker.mMessageRef;
            }
            if (tracker.mRetryCount == 0 && tracker.mExpectMore) {
                this.mCi.sendSMSExpectMore(IccUtils.bytesToHexString(smsc), IccUtils.bytesToHexString(pdu), reply);
                return;
            } else {
                this.mCi.sendSMS(IccUtils.bytesToHexString(smsc), IccUtils.bytesToHexString(pdu), reply);
                return;
            }
        }
        tracker.onFailed(this.mContext, SMSDispatcher.getNotInServiceError(ss), 0);
    }

    protected void updateSmsSendStatus(int messageRef, boolean success) {
        Rlog.e(TAG, "updateSmsSendStatus should never be called from here!");
    }

    protected UiccCardApplication getUiccCardApplication() {
        Rlog.d(TAG, "GsmSMSDispatcher: subId = " + this.mPhone.getSubId() + " slotId = " + this.mPhone.getPhoneId());
        return this.mUiccController.getUiccCardApplication(this.mPhone.getPhoneId(), 1);
    }

    private void onUpdateIccAvailability() {
        if (this.mUiccController != null) {
            UiccCardApplication newUiccApplication = getUiccCardApplication();
            UiccCardApplication app = (UiccCardApplication) this.mUiccApplication.get();
            if (app != newUiccApplication) {
                if (app != null) {
                    Rlog.d(TAG, "Removing stale icc objects.");
                    if (this.mIccRecords.get() != null) {
                        ((IccRecords) this.mIccRecords.get()).unregisterForNewSms(this);
                    }
                    this.mIccRecords.set(null);
                    this.mUiccApplication.set(null);
                }
                if (newUiccApplication != null) {
                    Rlog.d(TAG, "New Uicc application found");
                    this.mUiccApplication.set(newUiccApplication);
                    this.mIccRecords.set(newUiccApplication.getIccRecords());
                    if (this.mIccRecords.get() != null) {
                        ((IccRecords) this.mIccRecords.get()).registerForNewSms(this, 14, null);
                    }
                }
            }
        }
    }

    protected void dispatchSmsServiceCenter(String smsc_string) {
        String[] ret = new String[1];
        String smsc = getSCADialingNumberFromRil(smsc_string);
        if (smsc == null) {
            ret[0] = "NotSet";
        } else {
            ret[0] = smsc;
            this.Sim_Smsc = smsc;
        }
        Rlog.e(TAG, "smsc = " + ret[0]);
        Intent intent = new Intent(Intents.GET_SMSC_ACTION);
        intent.putExtra("smsc", ret);
        SubscriptionManager.putPhoneIdAndSubIdExtra(intent, this.mPhone.getPhoneId());
        this.mContext.sendBroadcast(intent, "android.permission.RECEIVE_SMS");
    }

    private KTReadConfirm getSmsHeaderKTReadConfirm(int readConfim) {
        KTReadConfirm ktReadConfirm = new KTReadConfirm();
        ktReadConfirm.id = 68;
        ByteArrayOutputStream outStream = new ByteArrayOutputStream(140);
        outStream.write(1);
        outStream.write(1);
        outStream.write(readConfim);
        ktReadConfirm.readConfirmID = readConfim;
        return ktReadConfirm;
    }

    public String getSmscNumber(byte[] a, boolean garbage_value) {
        StringBuffer buf = new StringBuffer(SMSC_ADDRESS_LENGTH);
        boolean international = false;
        if (a[0] == (byte) 0) {
            return "NotSet";
        }
        String smsc;
        if (a[1] == (byte) -111) {
            buf.append("+");
            international = true;
        }
        byte[] temp2 = new byte[10];
        System.arraycopy(a, 1 + 1, temp2, 0, a.length - 2);
        for (int cx = 0; cx < temp2.length; cx++) {
            if (temp2[cx] != (byte) -1) {
                int hn = (temp2[cx] & 255) / 16;
                buf.append(hexDigitChars.charAt(temp2[cx] & 15));
                buf.append(hexDigitChars.charAt(hn));
            }
        }
        String temp_smsc = buf.toString();
        int smsc_length = (a[0] - 1) * 2;
        if (international) {
            smsc = temp_smsc.substring(0, smsc_length + 1);
            Rlog.d(TAG, "international even smsc = " + smsc);
        } else {
            smsc = temp_smsc.substring(0, smsc_length);
        }
        if (garbage_value) {
            smsc = smsc.substring(0, smsc.length() - 1);
        }
        Rlog.d(TAG, "smsc = " + smsc);
        return smsc;
    }

    public static String getSCADialingNumberFromRil(String smsc_string) {
        boolean isInternational = false;
        Rlog.e(TAG, "smsc_string = " + smsc_string);
        if (smsc_string == null) {
            Rlog.e(TAG, "smsc is null.\n");
            return null;
        }
        Matcher m = Pattern.compile("\"?(\\+?)([0-9]+)\"?,([0-9]*)").matcher(smsc_string);
        if (m.matches()) {
            int toa;
            if ("+".equals(m.group(1))) {
                isInternational = true;
            }
            String smsc = m.group(2);
            try {
                toa = Integer.parseInt(m.group(3));
            } catch (NumberFormatException e) {
                toa = 0;
            }
            if ((toa & 144) == 144) {
                isInternational = true;
            }
            if (isInternational) {
                return "+" + smsc;
            }
            return smsc;
        }
        Rlog.e(TAG, "Invalid smsc format. " + smsc_string + "\n");
        return null;
    }

    public static String setSCADialingNumberToRil(String addr) {
        int toa = 129;
        try {
            if (addr.startsWith("+")) {
                Double.parseDouble(addr.substring(1));
                toa = 129 | 16;
            } else {
                Double.parseDouble(addr);
            }
            if ((!addr.startsWith("+") || addr.length() <= SMSC_ADDRESS_LENGTH) && (addr.startsWith("+") || addr.length() <= SMSC_ADDRESS_LENGTH - 1)) {
                return "\"" + addr + "\"," + toa;
            }
            return null;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public void clearDuplicatedCbMessages() {
        this.mGsmInboundSmsHandler.clearDuplicatedCbMessages();
    }
}
