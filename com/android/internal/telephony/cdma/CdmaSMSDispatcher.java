package com.android.internal.telephony.cdma;

import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.app.PendingIntent;
import android.app.PendingIntent.CanceledException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.net.Uri;
import android.os.Message;
import android.os.SystemProperties;
import android.provider.Telephony.CellBroadcasts;
import android.provider.Telephony.Sms.Intents;
import android.telephony.Rlog;
import android.telephony.SmsMessage;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import com.android.internal.telephony.GsmAlphabet.TextEncodingDetails;
import com.android.internal.telephony.ImsSMSDispatcher;
import com.android.internal.telephony.PhoneBase;
import com.android.internal.telephony.PhoneConstants.State;
import com.android.internal.telephony.SMSDispatcher;
import com.android.internal.telephony.SmsApplication;
import com.android.internal.telephony.SmsHeader;
import com.android.internal.telephony.SmsHeader.ConcatRef;
import com.android.internal.telephony.SmsMessageBase.SubmitPduBase;
import com.android.internal.telephony.SmsUsageMonitor;
import com.android.internal.telephony.cdma.sms.UserData;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CdmaSMSDispatcher extends SMSDispatcher {
    private static final String CTC_AUTOLOGIN_WAITING_ACTION = "android.intent.action.WAITING_AUTO_LOGIN";
    private static final String TAG = "CdmaSMSDispatcher";
    private static final boolean VDBG = false;
    private BroadcastReceiver mCTCAutoLoginWaitingActionReceiver = new C00771();
    private TelephonyManager mTelephonyManager = null;
    private boolean misDAN = false;

    class C00771 extends BroadcastReceiver {

        class C00751 implements OnClickListener {
            C00751() {
            }

            public void onClick(DialogInterface dialog, int id) {
                dialog.cancel();
                Rlog.d(CdmaSMSDispatcher.TAG, "popupDialog - ClickSend");
                CdmaSMSDispatcher.this.sendAutoLoginSmsResponse();
            }
        }

        class C00762 implements OnClickListener {
            C00762() {
            }

            public void onClick(DialogInterface dialog, int id) {
                Rlog.d(CdmaSMSDispatcher.TAG, "popupDialog - ClickCancel");
                dialog.cancel();
            }
        }

        C00771() {
        }

        public void onReceive(Context context, Intent intent) {
            if (CdmaSMSDispatcher.this.mIsDisposed) {
                Rlog.d(CdmaSMSDispatcher.TAG, "CDMASmsDispatcher Already Disposed!");
            } else if (CdmaSMSDispatcher.this.mPhone.getPhoneId() != 0 || CdmaSMSDispatcher.this.mPhone.getPhoneType() != 2) {
                Rlog.d(CdmaSMSDispatcher.TAG, "Autologin action/SimSlot:" + CdmaSMSDispatcher.this.mPhone.getPhoneId());
                Rlog.d(CdmaSMSDispatcher.TAG, "Autologin action/getPhoneType:" + CdmaSMSDispatcher.this.mPhone.getPhoneType());
            } else if (CdmaSMSDispatcher.CTC_AUTOLOGIN_WAITING_ACTION.equals(intent.getAction())) {
                int theme = 5;
                if (SystemProperties.get("ro.build.scafe.cream").equals("black")) {
                    theme = 4;
                }
                Builder builder = new Builder(CdmaSMSDispatcher.this.mContext, theme);
                Resources r = Resources.getSystem();
                builder.setTitle(17041623);
                builder.setMessage(17041624);
                builder.setCancelable(false);
                builder.setPositiveButton(r.getString(17040594), new C00751());
                builder.setNegativeButton(r.getString(17040595), new C00762());
                AlertDialog alert = builder.create();
                alert.getWindow().setType(2003);
                alert.show();
            }
        }
    }

    public CdmaSMSDispatcher(PhoneBase phone, SmsUsageMonitor usageMonitor, ImsSMSDispatcher imsSMSDispatcher) {
        super(phone, usageMonitor, imsSMSDispatcher);
        Rlog.d(TAG, "CdmaSMSDispatcher created");
        this.mContext.registerReceiver(this.mCTCAutoLoginWaitingActionReceiver, new IntentFilter(CTC_AUTOLOGIN_WAITING_ACTION));
    }

    protected String getFormat() {
        return SmsMessage.FORMAT_3GPP2;
    }

    public void dispose() {
        this.mContext.unregisterReceiver(this.mCTCAutoLoginWaitingActionReceiver);
        super.dispose();
    }

    void sendStatusReportMessage(SmsMessage sms) {
        sendMessage(obtainMessage(10, sms));
    }

    protected void handleStatusReport(Object o) {
        if (o instanceof SmsMessage) {
            handleCdmaStatusReport((SmsMessage) o);
        } else {
            Rlog.e(TAG, "handleStatusReport() called for object type " + o.getClass().getName());
        }
    }

    void handleCdmaStatusReport(SmsMessage sms) {
        Rlog.d(TAG, "handleCdmaStatusReport deliveryPendingList.size(): " + this.deliveryPendingList.size());
        int count = this.deliveryPendingList.size();
        for (int i = 0; i < count; i++) {
            SmsTracker tracker = (SmsTracker) this.deliveryPendingList.get(i);
            Rlog.d(TAG, "handleCdmaStatusReport tracker.mMessageRef: " + tracker.mMessageRef + " sms.mMessageRef: " + sms.mMessageRef);
            if (tracker.mMessageRef == sms.mMessageRef) {
                this.deliveryPendingList.remove(i);
                tracker.updateSentMessageStatus(this.mContext, 0);
                PendingIntent intent = tracker.mDeliveryIntent;
                Intent fillIn = new Intent();
                fillIn.putExtra("pdu", sms.getPdu());
                fillIn.putExtra(CellBroadcasts.MESSAGE_FORMAT, getFormat());
                SubscriptionManager.putPhoneIdAndSubIdExtra(fillIn, this.mPhone.getPhoneId());
                try {
                    intent.send(this.mContext, -1, fillIn);
                    return;
                } catch (CanceledException e) {
                    return;
                }
            }
        }
    }

    protected void sendData(String destAddr, String scAddr, int destPort, byte[] data, PendingIntent sentIntent, PendingIntent deliveryIntent) {
        sendSubmitPdu(getSmsTracker(getSmsTrackerMap(destAddr, scAddr, destPort, data, SmsMessage.getSubmitPdu(scAddr, destAddr, destPort, data, deliveryIntent != null)), sentIntent, deliveryIntent, getFormat(), null, false));
    }

    protected void sendDatawithOrigPort(String destAddr, String scAddr, int destPort, int origPort, byte[] data, PendingIntent sentIntent, PendingIntent deliveryIntent) {
        sendSubmitPdu(getSmsTracker(getSmsTrackerMap(destAddr, scAddr, destPort, data, SmsMessage.getSubmitPdu(scAddr, destAddr, destPort, data, deliveryIntent != null)), sentIntent, deliveryIntent, getFormat(), null, false));
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
        SubmitPduBase pdu = SmsMessage.getSubmitPdu(scAddr, destAddr, text, deliveryIntent != null, null);
        if (pdu != null) {
            if (messageUri == null) {
                if (SmsApplication.shouldWriteMessageForPackage(callingPkg, this.mContext)) {
                    messageUri = writeOutboxMessage(getSubId(), destAddr, text, deliveryIntent != null, callingPkg);
                }
            } else {
                moveToOutbox(getSubId(), messageUri, callingPkg);
            }
            sendSubmitPdu(getSmsTracker(getSmsTrackerMap(destAddr, scAddr, text, pdu), sentIntent, deliveryIntent, getFormat(), messageUri, 1 < 1));
            storeSMS(destAddr, Long.valueOf(Calendar.getInstance().getTimeInMillis()).toString(), text, false);
            return;
        }
        Rlog.e(TAG, "CdmaSMSDispatcher.sendText(): getSubmitPdu() returned null");
    }

    protected void sendText(String destAddr, String scAddr, String text, PendingIntent sentIntent, PendingIntent deliveryIntent, String callbackNumber, int priority) {
        sendSubmitPdu(getSmsTracker(getSmsTrackerMap(destAddr, scAddr, text, SmsMessage.getSubmitPdu(scAddr, destAddr, text, deliveryIntent != null, null, callbackNumber, priority)), sentIntent, deliveryIntent, getFormat(), null, false));
    }

    protected void sendMultipartText(String destAddr, String scAddr, ArrayList<String> parts, ArrayList<PendingIntent> sentIntents, ArrayList<PendingIntent> deliveryIntents, String callbackNumber, int priority) {
        int i;
        int refNumber = SMSDispatcher.getNextConcatenatedRef() & 255;
        int msgCount = parts.size();
        int encoding = 0;
        for (i = 0; i < msgCount; i++) {
            TextEncodingDetails details;
            if ("VZW-CDMA".equals("") || "SPR-CDMA".equals("") || "USC-CDMA".equals("")) {
                details = SmsMessage.calculateLength((CharSequence) parts.get(i), false, true);
            } else {
                details = SmsMessage.calculateLength((CharSequence) parts.get(i), false);
            }
            if (encoding != details.codeUnitSize && (encoding == 0 || encoding == 1)) {
                encoding = details.codeUnitSize;
            }
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
            PendingIntent sentIntent = null;
            if (sentIntents != null && sentIntents.size() > i) {
                sentIntent = (PendingIntent) sentIntents.get(i);
            }
            PendingIntent deliveryIntent = null;
            if (deliveryIntents != null && deliveryIntents.size() > i) {
                deliveryIntent = (PendingIntent) deliveryIntents.get(i);
            }
            UserData uData = new UserData();
            uData.payloadStr = (String) parts.get(i);
            uData.userDataHeader = smsHeader;
            if (encoding == 1) {
                String salesCode = SystemProperties.get("ro.csc.sales_code", "NONE");
                if ("CHN".equals(SmsMessage.salesCountry(salesCode)) || "HKTW".equals(SmsMessage.salesCountry(salesCode))) {
                    uData.msgEncoding = 2;
                } else {
                    uData.msgEncoding = 9;
                }
            } else {
                uData.msgEncoding = 4;
            }
            uData.msgEncodingSet = true;
            boolean z = deliveryIntent != null && i == msgCount - 1;
            sendSubmitPdu(getSmsTracker(getSmsTrackerMap(destAddr, scAddr, (String) parts.get(i), SmsMessage.getSubmitPdu(destAddr, uData, z, callbackNumber, priority)), sentIntent, deliveryIntent, getFormat(), null, false));
            i++;
        }
    }

    protected void sendTextwithOptions(String destAddr, String scAddr, String text, PendingIntent sentIntent, PendingIntent deliveryIntent, Uri messageUri, String callingPkg, boolean replyPath, int expiry, int serviceType, int encodingType, int confirmId) {
        Rlog.d(TAG, "sendTextwithOptions destAddr : " + destAddr);
        if (confirmId > 0) {
            Rlog.d(TAG, "sendTextwithOptions: confirmId is a special GSM function, should never be called in CDMA!");
        } else if (isSMSBlocked(destAddr, true)) {
            try {
                Intent intent = new Intent();
                intent.putExtra(SMSDispatcher.LAST_SENT_MSG_EXTRA, true);
                sentIntent.send(this.mContext, 1, intent);
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else {
            SubmitPduBase pdu = SmsMessage.getSubmitPdu(scAddr, destAddr, text, deliveryIntent != null, null);
            if (pdu != null) {
                if (messageUri == null) {
                    if (SmsApplication.shouldWriteMessageForPackage(callingPkg, this.mContext)) {
                        messageUri = writeOutboxMessage(getSubId(), destAddr, text, deliveryIntent != null, callingPkg);
                    }
                } else {
                    moveToOutbox(getSubId(), messageUri, callingPkg);
                }
                sendSubmitPdu(getSmsTracker(getSmsTrackerMap(destAddr, scAddr, text, pdu), sentIntent, deliveryIntent, getFormat(), messageUri, 1 < 1));
                storeSMS(destAddr, Long.valueOf(Calendar.getInstance().getTimeInMillis()).toString(), text, false);
                return;
            }
            Rlog.e(TAG, "CdmaSMSDispatcher.sendText(): getSubmitPdu() returned null");
        }
    }

    protected void sendMultipartTextwithOptions(String destAddr, String scAddr, ArrayList<String> parts, ArrayList<PendingIntent> sentIntents, ArrayList<PendingIntent> deliveryIntents, Uri messageUri, String callingPkg, boolean replyPath, int expiry, int serviceType, int encodingType) {
        int i;
        int refNumber = SMSDispatcher.getNextConcatenatedRef() & 255;
        int msgCount = parts.size();
        int encoding = 0;
        for (i = 0; i < msgCount; i++) {
            TextEncodingDetails details = SmsMessage.calculateLength((CharSequence) parts.get(i), false);
            if (encoding != details.codeUnitSize && (encoding == 0 || encoding == 1)) {
                encoding = details.codeUnitSize;
            }
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
            PendingIntent sentIntent = null;
            if (sentIntents != null && sentIntents.size() > i) {
                sentIntent = (PendingIntent) sentIntents.get(i);
            }
            PendingIntent deliveryIntent = null;
            if (deliveryIntents != null && deliveryIntents.size() > i) {
                deliveryIntent = (PendingIntent) deliveryIntents.get(i);
            }
            UserData uData = new UserData();
            uData.payloadStr = (String) parts.get(i);
            uData.userDataHeader = smsHeader;
            if (encoding == 1) {
                String salesCode = SystemProperties.get("ro.csc.sales_code", "NONE");
                if ("CHN".equals(SmsMessage.salesCountry(salesCode)) || "HKTW".equals(SmsMessage.salesCountry(salesCode))) {
                    uData.msgEncoding = 2;
                } else {
                    uData.msgEncoding = 9;
                }
            } else {
                uData.msgEncoding = 4;
            }
            uData.msgEncodingSet = true;
            boolean z = deliveryIntent != null && i == msgCount - 1;
            sendSubmitPdu(getSmsTracker(getSmsTrackerMap(destAddr, scAddr, (String) parts.get(i), SmsMessage.getSubmitPdu(destAddr, uData, z)), sentIntent, deliveryIntent, getFormat(), messageUri, false));
            i++;
        }
    }

    protected void sendOTADomestic(String destAddr, String scAddr, String text) {
        Rlog.d(TAG, "Error! sendOTADomestic is not implemented for CDMA.");
    }

    protected void sendTextNSRI(String destAddr, String scAddr, byte[] text, PendingIntent sentIntent, PendingIntent deliveryIntent, int msgCount, int msgTotal) {
        Rlog.d(TAG, "Error! sendTextNSRI is not implemented for CDMA.");
    }

    protected void injectSmsPdu(byte[] pdu, String format, PendingIntent receivedIntent) {
        throw new IllegalStateException("This method must be called only on ImsSMSDispatcher");
    }

    protected TextEncodingDetails calculateLength(CharSequence messageBody, boolean use7bitOnly) {
        return SmsMessage.calculateLength(messageBody, use7bitOnly);
    }

    protected void sendNewSubmitPdu(String destinationAddress, String scAddress, String message, SmsHeader smsHeader, int encoding, PendingIntent sentIntent, PendingIntent deliveryIntent, boolean lastPart, AtomicInteger unsentPartCount, AtomicBoolean anyPartFailed, Uri messageUri) {
        UserData uData = new UserData();
        uData.payloadStr = message;
        uData.userDataHeader = smsHeader;
        if (encoding == 1) {
            String salesCode = SystemProperties.get("ro.csc.sales_code", "NONE");
            if ("CHN".equals(SmsMessage.salesCountry(salesCode)) || "HKTW".equals(SmsMessage.salesCountry(salesCode))) {
                uData.msgEncoding = 2;
            } else {
                uData.msgEncoding = 9;
            }
        } else {
            uData.msgEncoding = 4;
        }
        uData.msgEncodingSet = true;
        boolean z = deliveryIntent != null && lastPart;
        sendSubmitPdu(getSmsTracker(getSmsTrackerMap(destinationAddress, scAddress, message, SmsMessage.getSubmitPdu(destinationAddress, uData, z)), sentIntent, deliveryIntent, getFormat(), unsentPartCount, anyPartFailed, messageUri, smsHeader, false));
    }

    protected void sendSubmitPdu(SmsTracker tracker) {
        if (checkEcmPolicy(true, tracker.mDestAddress)) {
            if (tracker.mSentIntent != null) {
                try {
                    tracker.mSentIntent.send(4);
                } catch (CanceledException e) {
                }
            }
            tracker.onFailed(this.mContext, 4, 0);
            return;
        }
        sendRawPdu(tracker);
    }

    protected void sendSms(SmsTracker tracker) {
        byte[] pdu = (byte[]) tracker.mData.get("pdu");
        Rlog.d(TAG, "sendSms:  isIms()=" + isIms() + " mRetryCount=" + tracker.mRetryCount + " mImsRetry=" + tracker.mImsRetry + " mMessageRef=" + tracker.mMessageRef + " SS=" + this.mPhone.getServiceState().getState());
        BroadcastReceiver resultReceiver = new SMSDispatcherReceiver(tracker);
        Intent intent = new Intent(Intents.SMS_SEND_ACTION);
        if (getCarrierAppPackageName(intent) != null) {
            intent.setPackage(getCarrierAppPackageName(intent));
            intent.putExtra("pdu", pdu);
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

    protected void updateSmsSendStatus(int messageRef, boolean success) {
        Rlog.e(TAG, "updateSmsSendStatus should never be called from here!");
    }

    protected void sendSmsByPstn(SmsTracker tracker) {
        int ss = this.mPhone.getServiceState().getState();
        if (isIms() || ss == 0) {
            Message reply = obtainMessage(2, tracker);
            byte[] pdu = (byte[]) tracker.mData.get("pdu");
            int currentDataNetwork = this.mPhone.getServiceState().getDataNetworkType();
            if ((currentDataNetwork == 14 || (currentDataNetwork == 13 && !this.mPhone.getServiceStateTracker().isConcurrentVoiceAndDataAllowed())) && this.mPhone.getServiceState().getVoiceNetworkType() == 7 && this.mPhone.getState() != State.IDLE) {
            }
            if (tracker.mExpectMore) {
                this.mCi.sendCdmaSmsMore(pdu, reply);
                return;
            } else {
                this.mCi.sendCdmaSms(pdu, reply);
                return;
            }
        }
        tracker.onFailed(this.mContext, SMSDispatcher.getNotInServiceError(ss), 0);
    }

    protected boolean checkEcmPolicy(boolean outgoing, String address) {
        if (SystemProperties.getBoolean("ril.cdma.inecmmode", false)) {
            return true;
        }
        return false;
    }

    protected void dispatchSmsServiceCenter(String smsc_hexstring) {
        Rlog.d(TAG, "dispatchSmsServiceCenter function is not applicable for CDMA");
    }

    public static String getSCADialingNumberFromRil(String smsc_string) {
        boolean isInternational = false;
        Rlog.d(TAG, "smsc_string = " + smsc_string);
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
        Rlog.d(TAG, "Invalid smsc format. " + smsc_string + "\n");
        return null;
    }

    public void clearDuplicatedCbMessages() {
        Rlog.e(TAG, "Error! clearDuplicatedCbMessages is not implemented for CDMA.");
    }

    /* JADX WARNING: inconsistent code. */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    private void sendAutoLoginSmsResponse() {
        /*
        r6 = this;
        r0 = new java.io.ByteArrayOutputStream;
        r0.<init>();
        r1 = new java.io.DataOutputStream;
        r1.<init>(r0);
        r3 = 37;
        r1.writeByte(r3);	 Catch:{ IOException -> 0x003c }
        r3 = 1;
        r1.writeByte(r3);	 Catch:{ IOException -> 0x003c }
        r3 = 0;
        r1.writeByte(r3);	 Catch:{ IOException -> 0x003c }
        r3 = 5;
        r1.writeByte(r3);	 Catch:{ IOException -> 0x003c }
        r3 = 1;
        r1.writeByte(r3);	 Catch:{ IOException -> 0x003c }
        r3 = r6.mPhone;	 Catch:{ IOException -> 0x003c }
        r4 = r0.toByteArray();	 Catch:{ IOException -> 0x003c }
        r5 = 0;
        r3.invokeOemRilRequestRaw(r4, r5);	 Catch:{ IOException -> 0x003c }
        r3 = "CdmaSMSDispatcher";
        r4 = "sendAutoLoginSmsResponse() invokeRilRequestRaw";
        android.telephony.Rlog.d(r3, r4);	 Catch:{ IOException -> 0x003c }
        r0.close();	 Catch:{ IOException -> 0x0037 }
        r1.close();	 Catch:{ IOException -> 0x0037 }
    L_0x0036:
        return;
    L_0x0037:
        r2 = move-exception;
        r2.printStackTrace();
        goto L_0x0036;
    L_0x003c:
        r2 = move-exception;
        r3 = "CdmaSMSDispatcher";
        r4 = new java.lang.StringBuilder;	 Catch:{ all -> 0x0061 }
        r4.<init>();	 Catch:{ all -> 0x0061 }
        r5 = "SendAutoLoginSms() error:";
        r4 = r4.append(r5);	 Catch:{ all -> 0x0061 }
        r4 = r4.append(r2);	 Catch:{ all -> 0x0061 }
        r4 = r4.toString();	 Catch:{ all -> 0x0061 }
        android.telephony.Rlog.e(r3, r4);	 Catch:{ all -> 0x0061 }
        r0.close();	 Catch:{ IOException -> 0x005c }
        r1.close();	 Catch:{ IOException -> 0x005c }
        goto L_0x0036;
    L_0x005c:
        r2 = move-exception;
        r2.printStackTrace();
        goto L_0x0036;
    L_0x0061:
        r3 = move-exception;
        r0.close();	 Catch:{ IOException -> 0x0069 }
        r1.close();	 Catch:{ IOException -> 0x0069 }
    L_0x0068:
        throw r3;
    L_0x0069:
        r2 = move-exception;
        r2.printStackTrace();
        goto L_0x0068;
        */
        throw new UnsupportedOperationException("Method not decompiled: com.android.internal.telephony.cdma.CdmaSMSDispatcher.sendAutoLoginSmsResponse():void");
    }

    public void sendDomainChangeSms(byte type) {
        String testAddr = "0000";
        if ("VZW-CDMA".equals("") && "ABSENT".equals(SystemProperties.get("gsm.sim.state"))) {
            Rlog.d(TAG, "uicc is absent");
        } else if (!this.mDcnAddress.equals(testAddr)) {
            PendingIntent pendingIntent = null;
            sendSubmitPdu(getSmsTracker(getSmsTrackerMap(this.mDcnAddress, null, null, SmsMessage.getDomainChangeNotification(type, this.mDcnAddress)), null, pendingIntent, getFormat(), null, false));
        }
    }
}
