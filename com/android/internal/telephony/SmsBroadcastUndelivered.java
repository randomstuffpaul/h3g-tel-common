package com.android.internal.telephony;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.net.Uri;
import android.provider.Telephony.CellBroadcasts;
import android.provider.Telephony.Sms;
import android.telephony.Rlog;
import android.telephony.SmsMessage;
import com.android.internal.telephony.cdma.CdmaInboundSmsHandler;
import com.android.internal.telephony.gsm.GsmInboundSmsHandler;
import com.sec.android.app.CscFeature;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;

public class SmsBroadcastUndelivered implements Runnable {
    private static final boolean DBG = true;
    static final int FORMAT_COLUMN = 8;
    static final long PARTIAL_SEGMENT_EXPIRE_AGE = 2592000000L;
    private static final String[] PDU_PENDING_MESSAGE_FORMAT_PROJECTION = new String[]{"pdu", "sequence", "destination_port", "date", "reference_number", "count", "address", "_id", CellBroadcasts.MESSAGE_FORMAT};
    private static final String[] PDU_PENDING_MESSAGE_PROJECTION = new String[]{"pdu", "sequence", "destination_port", "date", "reference_number", "count", "address", "_id"};
    private static final String TAG = "SmsBroadcastUndelivered";
    private static final Uri sRawUri = Uri.withAppendedPath(Sms.CONTENT_URI, "raw");
    private final CdmaInboundSmsHandler mCdmaInboundSmsHandler;
    private final GsmInboundSmsHandler mGsmInboundSmsHandler;
    private final ContentResolver mResolver;

    private static class SmsReferenceKey {
        final String mAddress;
        final int mMessageCount;
        final int mReferenceNumber;

        SmsReferenceKey(InboundSmsTracker tracker) {
            this.mAddress = tracker.getAddress();
            this.mReferenceNumber = tracker.getReferenceNumber();
            this.mMessageCount = tracker.getMessageCount();
        }

        String[] getDeleteWhereArgs() {
            return new String[]{this.mAddress, Integer.toString(this.mReferenceNumber), Integer.toString(this.mMessageCount)};
        }

        public int hashCode() {
            return (((this.mReferenceNumber * 31) + this.mMessageCount) * 31) + this.mAddress.hashCode();
        }

        public boolean equals(Object o) {
            if (!(o instanceof SmsReferenceKey)) {
                return false;
            }
            SmsReferenceKey other = (SmsReferenceKey) o;
            if (other.mAddress.equals(this.mAddress) && other.mReferenceNumber == this.mReferenceNumber && other.mMessageCount == this.mMessageCount) {
                return true;
            }
            return false;
        }
    }

    public SmsBroadcastUndelivered(Context context, GsmInboundSmsHandler gsmInboundSmsHandler, CdmaInboundSmsHandler cdmaInboundSmsHandler) {
        this.mResolver = context.getContentResolver();
        this.mGsmInboundSmsHandler = gsmInboundSmsHandler;
        this.mCdmaInboundSmsHandler = cdmaInboundSmsHandler;
    }

    public void run() {
        Rlog.d(TAG, "scanning raw table for undelivered messages");
        scanRawTable();
        if (this.mGsmInboundSmsHandler != null) {
            this.mGsmInboundSmsHandler.sendMessage(6);
        }
        if (this.mCdmaInboundSmsHandler != null) {
            this.mCdmaInboundSmsHandler.sendMessage(6);
        }
    }

    private void scanRawTable() {
        long startTime = System.nanoTime();
        HashMap<SmsReferenceKey, Integer> multiPartReceivedCount = new HashMap(4);
        HashSet<SmsReferenceKey> hashSet = new HashSet(4);
        Cursor cursor = null;
        boolean beLmsReassembleCTCRequired = "ReassembleTimeout".equals(CscFeature.getInstance().getString("CscFeature_RIL_DisplayPolicyPartialLongSms"));
        int simSlot = this.mGsmInboundSmsHandler.getPhone().getPhoneId();
        StringBuilder stringBuilder = new StringBuilder("sim_slot =");
        stringBuilder.append(simSlot);
        Rlog.d(TAG, "scanRawTable:" + stringBuilder.toString());
        if (beLmsReassembleCTCRequired) {
            cursor = this.mResolver.query(sRawUri, PDU_PENDING_MESSAGE_FORMAT_PROJECTION, stringBuilder.toString(), null, null);
        } else {
            cursor = this.mResolver.query(sRawUri, PDU_PENDING_MESSAGE_PROJECTION, stringBuilder.toString(), null, null);
        }
        if (cursor == null) {
            Rlog.e(TAG, "error getting pending message cursor");
            return;
        }
        boolean isCurrentFormat3gpp2 = false;
        if (!beLmsReassembleCTCRequired) {
            isCurrentFormat3gpp2 = InboundSmsHandler.isCurrentFormat3gpp2();
        }
        while (cursor.moveToNext()) {
            if (beLmsReassembleCTCRequired) {
                String format = cursor.getString(8);
                if (format != null && SmsMessage.FORMAT_3GPP2.equals(format)) {
                    isCurrentFormat3gpp2 = true;
                }
                Rlog.d(TAG, "current cursor format: " + format + " isCurrentFormat3gpp2:" + isCurrentFormat3gpp2);
            }
            try {
                InboundSmsTracker inboundSmsTracker = new InboundSmsTracker(cursor, isCurrentFormat3gpp2);
            } catch (IllegalArgumentException e) {
                Rlog.e(TAG, "error loading SmsTracker: " + e);
            }
            try {
                if (inboundSmsTracker.getMessageCount() == 1) {
                    broadcastSms(inboundSmsTracker);
                } else {
                    SmsReferenceKey smsReferenceKey = new SmsReferenceKey(inboundSmsTracker);
                    Integer receivedCount = (Integer) multiPartReceivedCount.get(smsReferenceKey);
                    if (receivedCount == null) {
                        multiPartReceivedCount.put(smsReferenceKey, Integer.valueOf(1));
                        if (inboundSmsTracker.getTimestamp() < System.currentTimeMillis() - PARTIAL_SEGMENT_EXPIRE_AGE) {
                            hashSet.add(smsReferenceKey);
                        }
                    } else {
                        int newCount = receivedCount.intValue() + 1;
                        if (newCount == inboundSmsTracker.getMessageCount()) {
                            Rlog.d(TAG, "found complete multi-part message");
                            broadcastSms(inboundSmsTracker);
                            hashSet.remove(smsReferenceKey);
                        } else {
                            multiPartReceivedCount.put(smsReferenceKey, Integer.valueOf(newCount));
                        }
                    }
                }
            } catch (SQLException e2) {
                Rlog.e(TAG, "error reading pending SMS messages", e2);
            } finally {
                if (cursor != null) {
                    cursor.close();
                }
                Rlog.d(TAG, "finished scanning raw table in " + ((System.nanoTime() - startTime) / 1000000) + " ms");
            }
        }
        Iterator i$ = hashSet.iterator();
        while (i$.hasNext()) {
            SmsReferenceKey message = (SmsReferenceKey) i$.next();
            int rows = this.mResolver.delete(sRawUri, "address=? AND reference_number=? AND count=?", message.getDeleteWhereArgs());
            if (rows == 0) {
                Rlog.e(TAG, "No rows were deleted from raw table!");
            } else {
                Rlog.d(TAG, "Deleted " + rows + " rows from raw table for incomplete " + message.mMessageCount + " part message");
            }
        }
        if (cursor != null) {
            cursor.close();
        }
        Rlog.d(TAG, "finished scanning raw table in " + ((System.nanoTime() - startTime) / 1000000) + " ms");
    }

    private void broadcastSms(InboundSmsTracker tracker) {
        InboundSmsHandler handler;
        if (tracker.is3gpp2()) {
            handler = this.mCdmaInboundSmsHandler;
        } else {
            handler = this.mGsmInboundSmsHandler;
        }
        if (handler != null) {
            handler.sendMessage(2, tracker);
        } else {
            Rlog.e(TAG, "null handler for " + tracker.getFormat() + " format, can't deliver.");
        }
    }
}
