package com.android.internal.telephony.cdma;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences.Editor;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Resources;
import android.database.Cursor;
import android.os.Message;
import android.os.SystemProperties;
import android.preference.PreferenceManager;
import android.provider.Telephony.Sms.Intents;
import android.sec.enterprise.EnterpriseDeviceManager;
import android.sec.enterprise.PhoneRestrictionPolicy;
import android.telephony.Rlog;
import android.telephony.SmsCbMessage;
import android.telephony.SmsMessage;
import android.util.secutil.Log;
import com.android.internal.telephony.CellBroadcastHandler;
import com.android.internal.telephony.ImsSMSDispatcher;
import com.android.internal.telephony.InboundSmsHandler;
import com.android.internal.telephony.InboundSmsTracker;
import com.android.internal.telephony.PhoneBase;
import com.android.internal.telephony.SmsConstants.MessageClass;
import com.android.internal.telephony.SmsHeader;
import com.android.internal.telephony.SmsMessageBase;
import com.android.internal.telephony.SmsStorageMonitor;
import com.android.internal.telephony.WspTypeDecoder;
import com.android.internal.telephony.cdma.sms.SmsEnvelope;
import com.android.internal.util.BitwiseInputStream;
import com.android.internal.util.BitwiseInputStream.AccessException;
import com.android.internal.util.HexDump;
import com.google.android.mms.pdu.CharacterSets;
import com.sec.android.app.CscFeature;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Arrays;

public class CdmaInboundSmsHandler extends InboundSmsHandler {
    private static final byte ALLRECEIVE_MODE = (byte) 3;
    private static final byte COMMERCIAL_MODE = (byte) 0;
    private static final int COUNT_COLUMN = 3;
    private static final int DESTINATION_PORT_COLUMN = 2;
    static final int ETWS_NOTIFICATION = 111;
    private static final byte KDDITEST_MODE = (byte) 2;
    private static final byte MANUFACTURETEST_MODE = (byte) 1;
    private static final int PDU_COLUMN = 0;
    private static final String[] PDU_SEQUENCE_PORT_COUNT_PROJECTION = new String[]{"pdu", "sequence", "destination_port", "count"};
    private static final int SEQUENCE_COLUMN = 1;
    private static final String TAG = "CdmaInboundSmsHandler";
    private final boolean DEBUG = true;
    private final boolean mCheckForDuplicatePortsInOmadmWapPush = Resources.getSystem().getBoolean(17956944);
    private CDMASmsDuplicateFilter mDuplicateFilter;
    private byte[] mLastAcknowledgedSmsFingerprint;
    private byte[] mLastDispatchedSmsFingerprint;
    private Notification mNotification;
    private final CdmaServiceCategoryProgramHandler mServiceCategoryProgramHandler;
    private final CdmaSMSDispatcher mSmsDispatcher;

    private class CDMASmsDuplicateFilter {
        private final int FILTER_SIZE = 10;
        ArrayList<SmsFilterRecord> mHistory = new ArrayList();

        private class SmsFilterRecord {
            private String msgBody;
            private int msgId;
            private String originAddress;
            private long timeStamp;

            public SmsFilterRecord(int msgid, long time, String address, String mBody) {
                this.msgId = msgid;
                this.timeStamp = time;
                this.originAddress = address;
                this.msgBody = mBody;
            }
        }

        CDMASmsDuplicateFilter() {
        }

        public void addMessage(SmsMessageBase msg) {
            if (this.mHistory.size() >= 10) {
                this.mHistory.remove(0);
            }
            if (msg.getTeleserviceId() == 4098 && !msg.isStatusReportMessage()) {
                this.mHistory.add(new SmsFilterRecord(msg.getMessageIdentifier(), msg.getTimestampMillis(), msg.getDisplayOriginatingAddress(), msg.getDisplayMessageBody()));
            }
        }

        public boolean isDuplicated(SmsMessageBase src) {
            for (int i = 0; i < this.mHistory.size(); i++) {
                boolean addrMatched;
                boolean timeMatched;
                boolean msgIdMatched;
                boolean bodyMatched;
                SmsFilterRecord msg = (SmsFilterRecord) this.mHistory.get(i);
                if (msg.originAddress != null) {
                    addrMatched = msg.originAddress.equals(src.getOriginatingAddress());
                } else if (src.getOriginatingAddress() == null) {
                    addrMatched = true;
                } else {
                    addrMatched = false;
                }
                if (msg.timeStamp == src.getTimestampMillis()) {
                    timeMatched = true;
                } else {
                    timeMatched = false;
                }
                if (msg.msgId == src.getMessageIdentifier()) {
                    msgIdMatched = true;
                } else {
                    msgIdMatched = false;
                }
                if (msg.msgBody != null) {
                    bodyMatched = msg.msgBody.equals(src.getDisplayMessageBody());
                } else if (src.getDisplayMessageBody() == null) {
                    bodyMatched = true;
                } else {
                    bodyMatched = false;
                }
                Log.secD(CdmaInboundSmsHandler.TAG, " isDuplicated()->addrMatched value:  " + addrMatched + " timeMatched value:  " + timeMatched + " msgIdMatched value: " + msgIdMatched + "  bodyMatched value:   " + bodyMatched);
                if (addrMatched && bodyMatched && timeMatched && msgIdMatched) {
                    return true;
                }
            }
            return false;
        }
    }

    private CdmaInboundSmsHandler(Context context, SmsStorageMonitor storageMonitor, PhoneBase phone, CdmaSMSDispatcher smsDispatcher) {
        super(TAG, context, storageMonitor, phone, CellBroadcastHandler.makeCellBroadcastHandler(context, phone));
        this.mSmsDispatcher = smsDispatcher;
        this.mServiceCategoryProgramHandler = CdmaServiceCategoryProgramHandler.makeScpHandler(context, phone.mCi);
        this.mDuplicateFilter = new CDMASmsDuplicateFilter();
        phone.mCi.setOnNewCdmaSms(getHandler(), 1, null);
    }

    protected void onQuitting() {
        this.mPhone.mCi.unSetOnNewCdmaSms(getHandler());
        this.mCellBroadcastHandler.dispose();
        log("unregistered for 3GPP2 SMS");
        super.onQuitting();
    }

    public static CdmaInboundSmsHandler makeInboundSmsHandler(Context context, SmsStorageMonitor storageMonitor, PhoneBase phone, CdmaSMSDispatcher smsDispatcher) {
        CdmaInboundSmsHandler handler = new CdmaInboundSmsHandler(context, storageMonitor, phone, smsDispatcher);
        handler.start();
        return handler;
    }

    private static boolean isInEmergencyCallMode() {
        return "true".equals(SystemProperties.get("ril.cdma.inecmmode", "false"));
    }

    protected boolean is3gpp2() {
        return true;
    }

    protected int dispatchMessageRadioSpecific(SmsMessageBase smsb) {
        if (this.mSmsDispatcher.checkEcmPolicy(false, null)) {
            return -1;
        }
        SmsMessage sms = (SmsMessage) smsb;
        if (1 == sms.getCDMAMessageType()) {
            log("Broadcast type message");
            SmsCbMessage cbMessage = sms.parseBroadcastSms();
            if (cbMessage != null) {
                this.mCellBroadcastHandler.dispatchSmsMessage(cbMessage);
            } else {
                loge("error trying to parse broadcast SMS");
            }
            return 1;
        }
        sms.parseSms();
        this.mLastDispatchedSmsFingerprint = sms.getIncomingSmsFingerprint();
        if (this.mLastAcknowledgedSmsFingerprint != null && Arrays.equals(this.mLastDispatchedSmsFingerprint, this.mLastAcknowledgedSmsFingerprint)) {
            return 1;
        }
        int teleService = sms.getTeleService();
        switch (teleService) {
            case 4097:
            case 4098:
            case SmsEnvelope.TELESERVICE_WEMT /*4101*/:
                if (sms.isStatusReportMessage()) {
                    this.mSmsDispatcher.sendStatusReportMessage(sms);
                    return 1;
                }
                break;
            case 4099:
            case SmsEnvelope.TELESERVICE_MWI /*262144*/:
                handleVoicemailTeleservice(sms);
                return 1;
            case 4100:
            case SmsEnvelope.TELESERVICE_WAP_CTC /*65002*/:
            case SmsEnvelope.TELESERVICE_WAP_CTC_DM /*65009*/:
                break;
            case SmsEnvelope.TELESERVICE_SCPT /*4102*/:
                this.mServiceCategoryProgramHandler.dispatchSmsMessage(sms);
                return 1;
            default:
                loge("unsupported teleservice 0x" + Integer.toHexString(teleService));
                return 4;
        }
        if (!this.mStorageMonitor.isStorageAvailable() && sms.getMessageClass() != MessageClass.CLASS_0) {
            return 3;
        }
        if (4100 == teleService) {
            this.mWapPush.setWpaPushAddressTimeStamp(smsb.getOriginatingAddress(), smsb.getTimestampMillis());
            return processCdmaWapPdu(sms.getUserData(), sms.mMessageRef, sms.getOriginatingAddress(), sms.getTimestampMillis());
        } else if ("CTC".equals(CscFeature.getInstance().getString("CscFeature_RIL_WapPushFormat4")) && 65002 == teleService) {
            this.mWapPush.setWpaPushAddressTimeStamp(smsb.getOriginatingAddress(), smsb.getTimestampMillis());
            BitwiseInputStream bitwiseInputStream = new BitwiseInputStream(sms.getUserData());
            byte[] userData = sms.getUserData();
            int messageReference = 0;
            log("CTC Push Message Decoding");
            try {
                bitwiseInputStream.skip(20);
                messageReference = (bitwiseInputStream.read(8) << 8) | bitwiseInputStream.read(8);
                bitwiseInputStream.skip(4);
                log("CTC Wap Push Reference Id:" + messageReference);
                bitwiseInputStream.skip(21);
                int num_fileds = bitwiseInputStream.read(8);
                log("CTC Wap Push num_fileds: " + num_fileds);
                userData = new byte[num_fileds];
                for (int loop = 0; loop < num_fileds; loop++) {
                    userData[loop] = (byte) bitwiseInputStream.read(8);
                }
            } catch (AccessException ex) {
                log("BearerData decode failed: " + ex);
            }
            return processCdmaWapPdu(userData, messageReference, sms.getOriginatingAddress(), sms.getTimestampMillis());
        } else if ("CTC".equals(CscFeature.getInstance().getString("CscFeature_RIL_WapPushFormat4")) && 65009 == teleService) {
            log("CTC DM Message Decoding");
            sms.parseCtcFota();
            if (sms.isCtcFota()) {
                return addTrackerToRawTableAndSendMessage(new InboundSmsTracker(sms.getUserData(), sms.getTimestampMillis(), SmsHeader.PORT_WAP_PUSH, true, true));
            }
            return 1;
        } else if (ImsSMSDispatcher.isLimitedMode()) {
            Rlog.d(TAG, "limited mode normal sms reject");
            return 2;
        } else {
            SmsHeader smsHeader = sms.getUserDataHeader();
            this.mWapPush.setWpaPushAddressTimeStamp(smsb.getDisplayOriginatingAddress(), smsb.getTimestampMillis());
            EnterpriseDeviceManager edm = EnterpriseDeviceManager.getInstance();
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
                        loge("fail to store blocked sms on mdm database");
                    }
                    return 10;
                }
            }
            return dispatchNormalMessage(smsb);
        }
    }

    protected void acknowledgeLastIncomingSms(boolean success, int result, Message response) {
        if (!this.mSmsDispatcher.checkEcmPolicy(false, null)) {
            int causeCode = resultToCause(result);
            this.mPhone.mCi.acknowledgeLastIncomingCdmaSms(success, causeCode, response);
            if (causeCode == 0) {
                this.mLastAcknowledgedSmsFingerprint = this.mLastDispatchedSmsFingerprint;
            }
            this.mLastDispatchedSmsFingerprint = null;
        }
    }

    protected void onUpdatePhoneObject(PhoneBase phone) {
        super.onUpdatePhoneObject(phone);
        this.mCellBroadcastHandler.updatePhoneObject(phone);
    }

    private static int resultToCause(int rc) {
        switch (rc) {
            case -1:
            case 1:
                return 0;
            case 3:
                return 35;
            case 4:
                return 4;
            default:
                return 96;
        }
    }

    private void handleVoicemailTeleservice(SmsMessage sms) {
        int preVoicemailCount = ((CDMAPhone) this.mPhone).getVoiceMessageCount();
        int voicemailPriority = sms.getMessagePriority();
        int voicemailCount = sms.getNumOfVoicemails();
        Rlog.d(TAG, "VM count : " + voicemailCount);
        Rlog.d(TAG, "VM prev : " + preVoicemailCount);
        Rlog.d(TAG, "VM priority : " + voicemailPriority);
        if (preVoicemailCount != sms.getNumOfVoicemails()) {
            Editor editor = PreferenceManager.getDefaultSharedPreferences(this.mContext).edit();
            editor.putInt(CDMAPhone.VM_COUNT_CDMA, voicemailCount);
            editor.putInt(CDMAPhone.VM_PRIORITY_CDMA, voicemailPriority);
            editor.apply();
            this.mPhone.setVoiceMessageWaiting(1, voicemailCount);
            updateVoicemailCount(voicemailCount);
        }
    }

    private int processCdmaWapPdu(byte[] pdu, int referenceNumber, String address, long timestamp) {
        int index = 0 + 1;
        int msgType = pdu[0] & 255;
        if (msgType != 0) {
            log("Received a WAP SMS which is not WDP. Discard.");
            int i = index;
            return 1;
        }
        i = index + 1;
        int totalSegments = pdu[index] & 255;
        index = i + 1;
        int segment = pdu[i] & 255;
        if (segment >= totalSegments) {
            loge("WDP bad segment #" + segment + " expecting 0-" + (totalSegments - 1));
            i = index;
            return 1;
        }
        byte[] userData;
        int sourcePort = 0;
        int destinationPort = 0;
        if (segment == 0) {
            i = index + 1;
            index = i + 1;
            sourcePort = ((pdu[index] & 255) << 8) | (pdu[i] & 255);
            i = index + 1;
            index = i + 1;
            destinationPort = ((pdu[index] & 255) << 8) | (pdu[i] & 255);
            if (this.mCheckForDuplicatePortsInOmadmWapPush && checkDuplicatePortOmadmWapPush(pdu, index)) {
                i = index + 4;
                log("Received WAP PDU. Type = " + msgType + ", originator = " + address + ", src-port = " + sourcePort + ", dst-port = " + destinationPort + ", ID = " + referenceNumber + ", segment# = " + segment + '/' + totalSegments);
                userData = new byte[(pdu.length - i)];
                System.arraycopy(pdu, i, userData, 0, pdu.length - i);
                return addTrackerToRawTableAndSendMessage(new InboundSmsTracker(userData, timestamp, destinationPort, true, address, referenceNumber, segment, totalSegments, true));
            }
        }
        i = index;
        log("Received WAP PDU. Type = " + msgType + ", originator = " + address + ", src-port = " + sourcePort + ", dst-port = " + destinationPort + ", ID = " + referenceNumber + ", segment# = " + segment + '/' + totalSegments);
        userData = new byte[(pdu.length - i)];
        System.arraycopy(pdu, i, userData, 0, pdu.length - i);
        return addTrackerToRawTableAndSendMessage(new InboundSmsTracker(userData, timestamp, destinationPort, true, address, referenceNumber, segment, totalSegments, true));
    }

    private static boolean checkDuplicatePortOmadmWapPush(byte[] origPdu, int index) {
        index += 4;
        byte[] omaPdu = new byte[(origPdu.length - index)];
        System.arraycopy(origPdu, index, omaPdu, 0, omaPdu.length);
        WspTypeDecoder pduDecoder = new WspTypeDecoder(omaPdu);
        if (!pduDecoder.decodeUintvarInteger(2) || !pduDecoder.decodeContentType(2 + pduDecoder.getDecodedDataLength())) {
            return false;
        }
        return "application/vnd.syncml.notification".equals(pduDecoder.getValueString());
    }

    protected int getEncoding() {
        return 2;
    }

    protected void handleBlockedSms(byte[] pdu, int sendType) {
        if (sendType == getEncoding()) {
            dispatchBlockedSms(pdu, sendType);
        }
    }

    protected String getFormat() {
        return SmsMessage.FORMAT_3GPP2;
    }

    protected boolean completeLGTCBSPdu(SmsMessageBase smsb, Cursor cursor, int cursorCount) {
        SmsMessage sms = (SmsMessage) smsb;
        byte[][] pdus = new byte[(cursorCount + 1)][];
        int sessionCount = 0;
        String address = null;
        if (smsb.getSessionSeqEos() == 1) {
            sessionCount = smsb.getSessionSeq() + 1;
            if (sessionCount != cursorCount + 1) {
                Log.secD(TAG, "[LGU_CBS][1]sessionCount invalid " + sessionCount + " cursorCount = " + cursorCount);
                return false;
            }
        }
        int i = 0;
        while (i < cursorCount) {
            try {
                cursor.moveToNext();
                int count = cursor.getInt(3);
                int sessionSeq = cursor.getInt(1);
                if (count != 0) {
                    sessionCount = count;
                    if (sessionCount != cursorCount + 1) {
                        Log.secD(TAG, "[LGU_CBS][2]sessionCount invalid  " + sessionCount + " cursorCount = " + cursorCount);
                        return false;
                    }
                }
                if (pdus[sessionSeq] != null) {
                    Log.secD(TAG, "[LGU_CBS]duplicated sessionSeq = " + sessionSeq + " count = " + count);
                    return false;
                }
                pdus[sessionSeq] = HexDump.hexStringToByteArray(cursor.getString(0));
                i++;
            } catch (Exception e) {
                Exception e2 = e;
            }
        }
        if (sessionCount == 0) {
            Log.secD(TAG, "[LGU_CBS]sessionCount is zero!!!???");
            return false;
        }
        String str;
        pdus[smsb.getSessionSeq()] = smsb.getPdu();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        StringBuilder body = new StringBuilder();
        i = 0;
        String strTempBody = null;
        while (i < sessionCount) {
            byte[] tempData;
            if (pdus[i] == null) {
                Log.secD(TAG, "[LGU_CBS]pdu is empty i = " + i + " sessionCount = " + sessionCount);
            }
            SmsMessage msg = SmsMessage.createFromPdu(pdus[i]);
            byte[] data = msg.getUserData();
            Log.secD(TAG, "[LGU_CBS] Last byte :  " + Integer.toHexString(data[data.length - 6] & 255) + " " + Integer.toHexString(data[data.length - 5] & 255) + " " + Integer.toHexString(data[data.length - 4] & 255) + " " + Integer.toHexString(data[data.length - 3] & 255) + " " + Integer.toHexString(data[data.length - 2] & 255) + " " + Integer.toHexString(data[data.length - 1] & 255));
            if (data[data.length - 1] == (byte) 0) {
                Log.secD(TAG, "[LGU_CBS] Last Index Remove ");
                output.write(data, 0, data.length - 1);
                tempData = new byte[(data.length - 1)];
                System.arraycopy(data, 0, tempData, 0, data.length - 1);
            } else {
                try {
                    output.write(data, 0, data.length);
                    tempData = new byte[data.length];
                    System.arraycopy(data, 0, tempData, 0, data.length);
                } catch (Exception e3) {
                    e2 = e3;
                    str = strTempBody;
                }
            }
            String trim = new String(tempData, CharacterSets.MIMENAME_EUC_KR).trim();
            str = trim.replaceAll(String.copyValueOf(new char[]{'\t'}), " ");
            Log.secD(TAG, "[LGU_CBS] getMessageBody() :  " + str);
            body.append(str);
            if (msg.getReplyAddress() != null) {
                address = msg.getReplyAddress();
            }
            i++;
            strTempBody = str;
        }
        sms.replacePdu(output.toByteArray(), body.toString(), address);
        str = strTempBody;
        return true;
        e2.printStackTrace();
        Log.secE(TAG, "[LGU_CBS] exception at completeLGTCBSPdu " + e2.getMessage());
        return false;
    }

    protected void insertLGTCBSPdu(SmsMessageBase sms, int messageCount) {
        try {
            ContentValues values = new ContentValues();
            values.put("date", Integer.valueOf(0));
            values.put("pdu", HexDump.toHexString(sms.getPdu()));
            values.put("address", sms.getOriginatingAddress());
            values.put("reference_number", Integer.valueOf(sms.getSessionId() | -256));
            values.put("count", Integer.valueOf(messageCount));
            values.put("sequence", Integer.valueOf(sms.getSessionSeq()));
            values.put("destination_port", Integer.valueOf(SmsEnvelope.TELESERVICE_LGT_CBS_327680));
            this.mResolver.insert(mRawUri, values);
        } catch (Exception e) {
            e.printStackTrace();
            Log.secE(TAG, "[LGU_CBS] exception at insertLGTCBSPdu " + e.getMessage());
        }
    }

    protected int processLGTCBSPdu(SmsMessageBase sms) {
        int i;
        int referenceNumber = sms.getSessionId() | -256;
        int sequenceNumber = sms.getSessionSeq();
        int sessionSeqEos = sms.getSessionSeqEos();
        String address = sms.getOriginatingAddress();
        Cursor cursor = null;
        boolean okToDispatch = false;
        Log.secD(TAG, "[LGU_CBS]processLGTCBSPdu referenceNumber = " + referenceNumber + " sequenceNumber = " + sequenceNumber + "sessionSeqEos = " + sessionSeqEos + "address = " + address);
        try {
            String refNumber = Integer.toString(referenceNumber);
            String seqNumber = Integer.toString(sequenceNumber);
            String destPort = Integer.toString(SmsEnvelope.TELESERVICE_LGT_CBS_327680);
            cursor = this.mResolver.query(mRawUri, PDU_SEQUENCE_PORT_COUNT_PROJECTION, "address=? AND reference_number=? AND sequence=? AND destination_port=?", new String[]{address, refNumber, seqNumber, destPort}, null);
            if (cursor.moveToNext()) {
                Log.secW(TAG, "Discarding duplicate message segment from address=" + address + " refNumber=" + refNumber + " seqNumber=" + seqNumber);
                i = 1;
                if (cursor != null) {
                    cursor.close();
                }
            } else {
                cursor.close();
                this.mResolver.delete(mRawUri, "reference_number<>? AND destination_port=?", new String[]{refNumber, destPort});
                String where = "address=? AND reference_number=? AND destination_port=?";
                String whereEOS = "address=? AND reference_number=? AND destination_port=? AND count<>0";
                String[] whereArgs = new String[]{address, refNumber, destPort};
                int messageCount;
                int cursorCount;
                if (sessionSeqEos == 1) {
                    messageCount = sequenceNumber + 1;
                    cursor = this.mResolver.query(mRawUri, PDU_SEQUENCE_PORT_COUNT_PROJECTION, where, whereArgs, null);
                    cursorCount = cursor.getCount();
                    if (cursorCount + 1 > messageCount) {
                        Log.secD(TAG, "[LGU_CBS]cursorCount goes against messageCount[1]???!!!! cursorCount = " + cursorCount + " messageCount = " + messageCount);
                        i = 1;
                        if (cursor != null) {
                            cursor.close();
                        }
                    } else if (cursorCount + 1 == messageCount) {
                        Log.secD(TAG, "[LGU_CBS]collect the complete set of sequence! cursorCount = " + cursorCount + " sequenceNumber = " + sequenceNumber);
                        okToDispatch = completeLGTCBSPdu(sms, cursor, cursorCount);
                    } else {
                        Log.secD(TAG, "[LGU_CBS]EOS has comed but it can't make complete set! cursorCount = " + cursorCount + " sequenceNumber = " + sequenceNumber);
                        insertLGTCBSPdu(sms, messageCount);
                    }
                } else {
                    cursor = this.mResolver.query(mRawUri, PDU_SEQUENCE_PORT_COUNT_PROJECTION, whereEOS, whereArgs, null);
                    cursorCount = cursor.getCount();
                    if (cursorCount > 1) {
                        Log.secD(TAG, "[LGU_CBS]EOS is more than one???!!!! cursorCount = " + cursorCount + " sequenceNumber = " + sequenceNumber);
                        i = 1;
                        if (cursor != null) {
                            cursor.close();
                        }
                    } else if (cursorCount == 1) {
                        cursor.moveToNext();
                        messageCount = cursor.getInt(3);
                        cursor.close();
                        cursor = this.mResolver.query(mRawUri, PDU_SEQUENCE_PORT_COUNT_PROJECTION, where, whereArgs, null);
                        cursorCount = cursor.getCount();
                        if (cursorCount + 1 > messageCount) {
                            Log.secD(TAG, "[LGU_CBS]cursorCount goes against messageCount[2]???!!!! cursorCount = " + cursorCount + " messageCount = " + messageCount);
                            i = 1;
                            if (cursor != null) {
                                cursor.close();
                            }
                        } else if (cursorCount + 1 == messageCount) {
                            Log.secD(TAG, "[LGU_CBS]collect the complete set of sequence! cursorCount = " + cursorCount + " sequenceNumber = " + sequenceNumber);
                            okToDispatch = completeLGTCBSPdu(sms, cursor, cursorCount);
                        } else {
                            Log.secD(TAG, "[LGU_CBS]It's EOS but it can't make complete set! cursorCount = " + cursorCount + " sequenceNumber = " + sequenceNumber);
                            insertLGTCBSPdu(sms, messageCount);
                        }
                    } else {
                        Log.secD(TAG, "[LGU_CBS]EOS has not been arrived yet cursorCount = " + cursorCount + " sequenceNumber = " + sequenceNumber);
                        insertLGTCBSPdu(sms, 0);
                    }
                }
                if (okToDispatch) {
                    byte[][] pdus = new byte[][]{sms.getPdu()};
                    this.mResolver.delete(mRawUri, where, whereArgs);
                    Log.secD(TAG, "[LGU_CBS]dispatchPdus!! ");
                    dispatchPdus(pdus, true);
                }
                i = 1;
                if (cursor != null) {
                    cursor.close();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            Log.secE(TAG, "[LGU_CBS] exception at processLGTCBSPdu " + e.getMessage());
            i = 1;
            if (cursor != null) {
                cursor.close();
            }
        } catch (Throwable th) {
            if (cursor != null) {
                cursor.close();
            }
        }
        return i;
    }

    protected boolean isDuplicatedSms(SmsMessageBase sms) {
        if (this.mDuplicateFilter.isDuplicated(sms)) {
            Log.secD(TAG, "Duplicate found. Samsung");
            return true;
        }
        this.mDuplicateFilter.addMessage(sms);
        return false;
    }

    protected boolean kddiJudgeDeliveryFromServiceCategory(SmsMessage smsb) {
        int maintenanceMode = 0;
        try {
            Context context = this.mContext;
            Context context2 = this.mContext;
            maintenanceMode = this.mContext.createPackageContext("com.kddi.maintenanceMode", 2).getSharedPreferences("pref", 4).getInt("maintenanceMode", 0);
        } catch (NameNotFoundException e) {
            Log.e(TAG, "maintenanceMode app not found");
        }
        int serviceCategory = smsb.getServiceCategory();
        boolean isDelivery = false;
        switch (maintenanceMode) {
            case 0:
                if (serviceCategory == 1 || (33 <= serviceCategory && serviceCategory <= 63)) {
                    isDelivery = true;
                    break;
                }
            case 1:
                if (serviceCategory == 32769 || (32801 <= serviceCategory && serviceCategory <= 32831)) {
                    isDelivery = true;
                    break;
                }
            case 2:
                if (serviceCategory == 49153 || (49185 <= serviceCategory && serviceCategory <= 49215)) {
                    isDelivery = true;
                    break;
                }
            case 3:
                if (serviceCategory == 1 || ((33 <= serviceCategory && serviceCategory <= 63) || serviceCategory == 32769 || ((32801 <= serviceCategory && serviceCategory <= 32831) || serviceCategory == 49153 || (49185 <= serviceCategory && serviceCategory <= 49215)))) {
                    isDelivery = true;
                    break;
                }
        }
        Log.d(TAG, "kddiJudgeDeliveryFromServiceCategory maintenanceMode : " + maintenanceMode + " serviceCategory: " + serviceCategory + " isDelivery: " + isDelivery);
        return isDelivery;
    }

    protected boolean kddiJudgeDeliveryFromServiceCategoryForWIFI(SmsMessage smsb) {
        int serviceCategory = smsb.getServiceCategory();
        int maintenanceMode = 0;
        boolean isDelivery = false;
        try {
            Context context = this.mContext;
            Context context2 = this.mContext;
            maintenanceMode = this.mContext.createPackageContext("com.kddi.maintenanceMode", 2).getSharedPreferences("pref", 4).getInt("maintenanceMode", 0);
        } catch (NameNotFoundException e) {
            Log.e(TAG, "maintenanceMode app not found");
        }
        switch (maintenanceMode) {
            case 0:
                if (serviceCategory == 1 || serviceCategory == 38 || serviceCategory == 40) {
                    isDelivery = true;
                    break;
                }
            default:
                if (serviceCategory == 32769 || serviceCategory == 32808) {
                    isDelivery = true;
                    break;
                }
        }
        Log.d(TAG, "kddiJudgeDeliveryFromServiceCategoryForWIFI: serviceCategory: " + serviceCategory + " isDelivery: " + isDelivery);
        return isDelivery;
    }

    protected boolean ETWSJudgeDeliveryFromMessageID(SmsMessage smsb) {
        int serviceCategory = smsb.getServiceCategory();
        return false;
    }

    protected void KddiNotifiyCDMASmsToWIFI(byte[][] pdus) {
        Intent intent = new Intent(Intents.SMS_CB_RECEIVED_WIFI_ACTION);
        intent.putExtra("pdus", pdus);
        this.mContext.sendBroadcast(intent);
    }

    private void setNotification() {
        Log.d(TAG, "setNotification: create notification ");
        this.mNotification = new Notification();
        this.mNotification.when = System.currentTimeMillis();
        this.mNotification.flags = 16;
        this.mNotification.icon = 17301642;
        Intent intent = new Intent("android.intent.action.EMERGENCY_START_SERVICE_BY_ORDER");
        intent.putExtra("enabled", true);
        intent.putExtra("flag", 16);
        this.mNotification.contentIntent = PendingIntent.getBroadcast(this.mContext, 0, intent, 0);
        CharSequence title = "";
        CharSequence details = "";
        Log.d(TAG, "setNotification: put notification " + title + " / " + details);
        this.mNotification.tickerText = title;
        this.mNotification.setLatestEventInfo(this.mContext, title, details, this.mNotification.contentIntent);
        Context context = this.mContext;
        Context context2 = this.mContext;
        ((NotificationManager) context.getSystemService("notification")).notify(ETWS_NOTIFICATION, this.mNotification);
    }
}
