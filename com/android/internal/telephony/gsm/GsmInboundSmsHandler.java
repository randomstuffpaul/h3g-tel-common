package com.android.internal.telephony.gsm;

import android.content.Context;
import android.os.Message;
import android.sec.enterprise.EnterpriseDeviceManager;
import android.sec.enterprise.PhoneRestrictionPolicy;
import android.telephony.SmsMessage;
import com.android.internal.telephony.InboundSmsHandler;
import com.android.internal.telephony.PhoneBase;
import com.android.internal.telephony.SmsConstants.MessageClass;
import com.android.internal.telephony.SmsHeader;
import com.android.internal.telephony.SmsMessageBase;
import com.android.internal.telephony.SmsStorageMonitor;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Arrays;

public class GsmInboundSmsHandler extends InboundSmsHandler {
    private static final String TAG = "GsmInboundSmsHandler";
    private final UsimDataDownloadHandler mDataDownloadHandler;
    private GsmSmsDuplicateFilter mDuplicateFilter = new GsmSmsDuplicateFilter();

    private class GsmSmsDuplicateFilter {
        private final int FILTER_SIZE = 15;
        ArrayList<SmsFilterRecord> mHistory = new ArrayList();

        private class SmsFilterRecord {
            byte[] mFingerprint;
            SmsMessageBase mSms;

            SmsFilterRecord(SmsMessageBase sms) {
                this.mSms = sms;
                this.mFingerprint = GsmSmsDuplicateFilter.this.getSmsFingerprint(sms);
            }
        }

        GsmSmsDuplicateFilter() {
        }

        public void addMessage(SmsMessageBase msg) {
            if (this.mHistory.size() >= 15) {
                this.mHistory.remove(0);
            }
            this.mHistory.add(new SmsFilterRecord(msg));
        }

        private byte[] getSmsFingerprint(SmsMessageBase msg) {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            SmsHeader smsHeader = msg.getUserDataHeader();
            if (!(smsHeader == null || SmsHeader.toByteArray(smsHeader) == null)) {
                byte[] hdr = SmsHeader.toByteArray(smsHeader);
                output.write(hdr, 0, hdr.length);
            }
            output.write(msg.getUserData(), 0, msg.getUserData().length);
            return output.toByteArray();
        }

        public boolean isDuplicated(SmsMessageBase src) {
            SmsHeader smsHeader = src.getUserDataHeader();
            for (int i = 0; i < this.mHistory.size(); i++) {
                boolean addrMatched;
                SmsFilterRecord rec = (SmsFilterRecord) this.mHistory.get(i);
                SmsMessageBase msg = rec.mSms;
                if (msg.getOriginatingAddress() != null) {
                    addrMatched = msg.getOriginatingAddress().equals(src.getOriginatingAddress());
                } else {
                    addrMatched = src.getOriginatingAddress() == null;
                }
                boolean timeMatched;
                if (msg.getTimestampMillis() == src.getTimestampMillis()) {
                    timeMatched = true;
                } else {
                    timeMatched = false;
                }
                boolean bodyMatched;
                if (smsHeader != null && (smsHeader.portAddrs != null || smsHeader.concatRef != null)) {
                    bodyMatched = Arrays.equals(rec.mFingerprint, getSmsFingerprint(src));
                } else if (msg.getDisplayMessageBody() != null) {
                    bodyMatched = msg.getDisplayMessageBody().equals(src.getDisplayMessageBody());
                } else {
                    bodyMatched = src.getDisplayMessageBody() == null;
                }
                if (addrMatched && bodyMatched && timeMatched) {
                    return true;
                }
            }
            return false;
        }
    }

    protected GsmInboundSmsHandler(Context context, SmsStorageMonitor storageMonitor, PhoneBase phone) {
        super(TAG, context, storageMonitor, phone, GsmCellBroadcastHandler.makeGsmCellBroadcastHandler(context, phone));
        phone.mCi.setOnNewGsmSms(getHandler(), 1, null);
        this.mDataDownloadHandler = new UsimDataDownloadHandler(phone.mCi);
    }

    protected void onQuitting() {
        this.mPhone.mCi.unSetOnNewGsmSms(getHandler());
        this.mCellBroadcastHandler.dispose();
        log("unregistered for 3GPP SMS");
        super.onQuitting();
    }

    public static GsmInboundSmsHandler makeInboundSmsHandler(Context context, SmsStorageMonitor storageMonitor, PhoneBase phone) {
        GsmInboundSmsHandler handler = new GsmInboundSmsHandler(context, storageMonitor, phone);
        handler.start();
        return handler;
    }

    protected boolean is3gpp2() {
        return false;
    }

    protected int dispatchMessageRadioSpecific(SmsMessageBase smsb) {
        if (smsb == null) {
            log("dispatchMessage: message is null");
            return 2;
        }
        SmsMessage sms = (SmsMessage) smsb;
        SmsHeader smsHeader = sms.getUserDataHeader();
        if (sms.isTypeZero()) {
            log("Received short message type 0, Don't display or store it. Send Ack");
            return 1;
        } else if (sms.isUsimDataDownload()) {
            return this.mDataDownloadHandler.handleUsimDataDownload(this.mPhone.getUsimServiceTable(), sms);
        } else {
            boolean handled = false;
            if (sms.isMWISetMessage()) {
                this.mPhone.setVoiceMessageWaiting(1, sms.getNumOfVoicemails());
                this.mPhone.storeVoiceMailCount(sms.getNumOfVoicemails());
                handled = sms.isMwiDontStore();
                log("Received voice mail indicator set SMS shouldStore=" + (!handled));
            } else if (sms.isMWIClearMessage()) {
                this.mPhone.setVoiceMessageWaiting(1, 0);
                this.mPhone.storeVoiceMailCount(0);
                handled = sms.isMwiDontStore();
                log("Received voice mail indicator clear SMS shouldStore=" + (!handled));
            }
            if (handled) {
                return 1;
            }
            if (!this.mStorageMonitor.isStorageAvailable() && sms.getMessageClass() != MessageClass.CLASS_0) {
                return 3;
            }
            EnterpriseDeviceManager edm;
            if ((smsHeader != null && smsHeader.concatRef != null) || smsHeader == null || smsHeader.portAddrs == null) {
                this.mWapPush.setWpaPushAddressTimeStamp(smsb.getOriginatingAddress(), smsb.getTimestampMillis());
                edm = EnterpriseDeviceManager.getInstance();
            } else {
                this.mWapPush.setWpaPushAddressTimeStamp(smsb.getOriginatingAddress(), smsb.getTimestampMillis());
                edm = EnterpriseDeviceManager.getInstance();
            }
            Object obj = (smsHeader == null || smsHeader.portAddrs == null || SmsHeader.PORT_WAP_PUSH != smsHeader.portAddrs.destPort) ? null : 1;
            if (obj == null) {
                if (isSMSBlocked(sms.getDisplayOriginatingAddress(), false)) {
                    return 10;
                }
                PhoneRestrictionPolicy phoneRestriction = edm.getPhoneRestrictionPolicy();
                if (phoneRestriction.isBlockSmsWithStorageEnabled()) {
                    try {
                        phoneRestriction.storeBlockedSmsMms(true, smsb.getPdu(), smsb.getDisplayOriginatingAddress(), getEncoding(), null);
                    } catch (Exception e) {
                        log("fail to store blocked sms on mdm database");
                    }
                    return 10;
                }
            }
            return dispatchNormalMessage(smsb);
        }
    }

    protected void acknowledgeLastIncomingSms(boolean success, int result, Message response) {
        this.mPhone.mCi.acknowledgeLastIncomingGsmSms(success, resultToCause(result), response);
    }

    protected void onUpdatePhoneObject(PhoneBase phone) {
        super.onUpdatePhoneObject(phone);
        log("onUpdatePhoneObject: dispose of old CellBroadcastHandler and make a new one");
        this.mCellBroadcastHandler.dispose();
        this.mCellBroadcastHandler = GsmCellBroadcastHandler.makeGsmCellBroadcastHandler(this.mContext, phone);
    }

    protected static int resultToCause(int rc) {
        switch (rc) {
            case -1:
            case 1:
                return 0;
            case 3:
                return 211;
            case 7:
                return 214;
            default:
                return 255;
        }
    }

    protected int getEncoding() {
        return 1;
    }

    protected void handleBlockedSms(byte[] pdu, int sendType) {
        if (sendType == getEncoding()) {
            dispatchBlockedSms(pdu, sendType);
        }
    }

    public void clearDuplicatedCbMessages() {
        ((GsmCellBroadcastHandler) this.mCellBroadcastHandler).clearDuplicatedCbMessages();
    }

    protected boolean isDuplicatedSms(SmsMessageBase sms) {
        if (this.mDuplicateFilter.isDuplicated(sms)) {
            return true;
        }
        this.mDuplicateFilter.addMessage(sms);
        return false;
    }

    protected String getFormat() {
        return SmsMessage.FORMAT_3GPP;
    }
}
