package android.telephony;

import android.content.res.Resources;
import android.os.Parcel;
import android.text.TextUtils;
import com.android.internal.telephony.GsmAlphabet;
import com.android.internal.telephony.GsmAlphabet.TextEncodingDetails;
import com.android.internal.telephony.Sms7BitEncodingTranslator;
import com.android.internal.telephony.SmsHeader;
import com.android.internal.telephony.SmsMessageBase;
import com.android.internal.telephony.SmsMessageBase.DeliverPduBase;
import com.android.internal.telephony.SmsMessageBase.SubmitPduBase;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;

public class SmsMessage {
    public static final int ENCODING_16BIT = 3;
    public static final int ENCODING_7BIT = 1;
    public static final int ENCODING_8BIT = 2;
    public static final int ENCODING_EUC_KR = 4;
    public static final int ENCODING_KSC5601 = 4;
    public static final int ENCODING_UNKNOWN = 0;
    public static final String FORMAT_3GPP = "3gpp";
    public static final String FORMAT_3GPP2 = "3gpp2";
    private static final String LOG_TAG = "SmsMessage";
    public static final int MAX_DATA_LEN_WITH_SEGMENT_SEPERATOR = 154;
    public static final int MAX_USER_DATA_BYTES = 140;
    public static final int MAX_USER_DATA_BYTES_WITH_HEADER = 134;
    private static final int MAX_USER_DATA_BYTES_WITH_HEADER_SINGLE_LOCKING_SHIFT = 128;
    private static final int MAX_USER_DATA_BYTES_WITH_HEADER_SINGLE_SHIFT = 131;
    public static final int MAX_USER_DATA_BYTES_WITH_SEGMENT_SEPERATOR = 128;
    public static final int MAX_USER_DATA_SEPTETS = 160;
    public static final int MAX_USER_DATA_SEPTETS_WITH_HEADER = 153;
    private static final int MAX_USER_DATA_SEPTETS_WITH_HEADER_NATIONAL_LANGUAGE = 149;
    private static final int f1x34bbd584 = 147;
    public static final int VALIDITY_PERIOD_FORMAT_ABSOLUTE_FORMAT = 3;
    public static final int VALIDITY_PERIOD_FORMAT_ENHANCED_FORMAT = 1;
    public static final int VALIDITY_PERIOD_FORMAT_NOT_PRESENT = 0;
    public static final int VALIDITY_PERIOD_FORMAT_RELATIVE_FORMAT = 2;
    private static boolean mIsNoEmsSupportConfigListLoaded = false;
    private static NoEmsSupportConfig[] mNoEmsSupportConfigList = null;
    private long mSubId = 0;
    public SmsMessageBase mWrappedSmsMessage;

    static /* synthetic */ class C00061 {
        static final /* synthetic */ int[] f0xf858d1e4 = new int[com.android.internal.telephony.SmsConstants.MessageClass.values().length];

        static {
            try {
                f0xf858d1e4[com.android.internal.telephony.SmsConstants.MessageClass.CLASS_0.ordinal()] = 1;
            } catch (NoSuchFieldError e) {
            }
            try {
                f0xf858d1e4[com.android.internal.telephony.SmsConstants.MessageClass.CLASS_1.ordinal()] = 2;
            } catch (NoSuchFieldError e2) {
            }
            try {
                f0xf858d1e4[com.android.internal.telephony.SmsConstants.MessageClass.CLASS_2.ordinal()] = 3;
            } catch (NoSuchFieldError e3) {
            }
            try {
                f0xf858d1e4[com.android.internal.telephony.SmsConstants.MessageClass.CLASS_3.ordinal()] = 4;
            } catch (NoSuchFieldError e4) {
            }
        }
    }

    public static class DeliverPdu {
        public byte[] encodedMessage;
        public byte[] encodedScAddress;

        public String toString() {
            return "DeliverPdu: encodedScAddress = " + Arrays.toString(this.encodedScAddress) + ", encodedMessage = " + Arrays.toString(this.encodedMessage);
        }

        protected DeliverPdu(DeliverPduBase spb) {
            this.encodedMessage = spb.encodedMessage;
            this.encodedScAddress = spb.encodedScAddress;
        }
    }

    public enum MessageClass {
        UNKNOWN,
        CLASS_0,
        CLASS_1,
        CLASS_2,
        CLASS_3
    }

    public enum MessageTpPid {
        MSG_PID_DEFAULT(0),
        MSG_PID_SMS_HANDLED(64),
        MSG_PID_LBS_PORT(81),
        MSG_PID_APPLICATION_PORT(83);
        
        private int mValue;

        private MessageTpPid(int value) {
            this.mValue = value;
        }

        public int value() {
            return this.mValue;
        }

        public static MessageTpPid fromInt(int value) {
            for (MessageTpPid e : values()) {
                if (e.mValue == value) {
                    return e;
                }
            }
            return null;
        }
    }

    private static class NoEmsSupportConfig {
        String mGid1;
        boolean mIsPrefix;
        String mOperatorNumber;

        public NoEmsSupportConfig(String[] config) {
            this.mOperatorNumber = config[0];
            this.mIsPrefix = "prefix".equals(config[1]);
            this.mGid1 = config.length > 2 ? config[2] : null;
        }

        public String toString() {
            return "NoEmsSupportConfig { mOperatorNumber = " + this.mOperatorNumber + ", mIsPrefix = " + this.mIsPrefix + ", mGid1 = " + this.mGid1 + " }";
        }
    }

    private static class PduFormatChecker {
        private static File file = new File("/data/misc/radio/fmt");
        private static FileInputStream fileInputStream = null;
        private static FileOutputStream fileOutputStream = null;

        private PduFormatChecker() {
        }

        private static String read() {
            if (!file.exists()) {
                return null;
            }
            StringBuilder stringBuilder = new StringBuilder();
            try {
                fileInputStream = new FileInputStream(file);
                while (true) {
                    int temp = fileInputStream.read();
                    if (temp != -1) {
                        stringBuilder.append((char) temp);
                    }
                    try {
                        break;
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            } catch (IOException e2) {
                Rlog.e(SmsMessage.LOG_TAG, "[PduFormatChecker] faile to read");
            }
            if (fileInputStream != null) {
                fileInputStream.close();
            }
            return stringBuilder.toString();
        }

        private static void write(String pduFormat) {
            byte[] bData = pduFormat.getBytes();
            try {
                if (!file.exists()) {
                    file.createNewFile();
                    file.setReadable(true, false);
                }
                fileOutputStream = new FileOutputStream(file);
                fileOutputStream.write(bData);
                try {
                    if (fileOutputStream != null) {
                        fileOutputStream.close();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            } catch (IOException e2) {
                Rlog.e(SmsMessage.LOG_TAG, "[PduFormatChecker] faile to write");
            }
        }
    }

    public static class SubmitPdu {
        public byte[] encodedMessage;
        public byte[] encodedScAddress;

        public String toString() {
            return "SubmitPdu: encodedScAddress = " + Arrays.toString(this.encodedScAddress) + ", encodedMessage = " + Arrays.toString(this.encodedMessage);
        }

        protected SubmitPdu(SubmitPduBase spb) {
            this.encodedMessage = spb.encodedMessage;
            this.encodedScAddress = spb.encodedScAddress;
        }
    }

    public void setSubId(long subId) {
        this.mSubId = subId;
    }

    public long getSubId() {
        return this.mSubId;
    }

    private SmsMessage(SmsMessageBase smb) {
        this.mWrappedSmsMessage = smb;
    }

    public static SmsMessage createFromPdu(byte[] pdu) {
        int activePhone = TelephonyManager.getDefault().getCurrentPhoneType();
        SmsMessage message = createFromPdu(pdu, 2 == activePhone ? FORMAT_3GPP2 : FORMAT_3GPP);
        if (message != null && message.mWrappedSmsMessage != null) {
            return message;
        }
        return createFromPdu(pdu, 2 == activePhone ? FORMAT_3GPP : FORMAT_3GPP2);
    }

    public static SmsMessage createFromPdu(byte[] pdu, String format) {
        SmsMessageBase wrappedMessage;
        if (FORMAT_3GPP2.equals(format)) {
            wrappedMessage = com.android.internal.telephony.cdma.SmsMessage.createFromPdu(pdu);
        } else if (FORMAT_3GPP.equals(format)) {
            wrappedMessage = com.android.internal.telephony.gsm.SmsMessage.createFromPdu(pdu);
        } else {
            Rlog.e(LOG_TAG, "createFromPdu(): unsupported message format " + format);
            return null;
        }
        return new SmsMessage(wrappedMessage);
    }

    public static SmsMessage createFromPdu(byte[] pdu, int encoding) {
        SmsMessageBase wrappedMessage;
        if (2 == encoding) {
            wrappedMessage = com.android.internal.telephony.cdma.SmsMessage.createFromPdu(pdu);
        } else {
            wrappedMessage = com.android.internal.telephony.gsm.SmsMessage.createFromPdu(pdu);
        }
        return new SmsMessage(wrappedMessage);
    }

    public static SmsMessage newFromCMT(String[] lines) {
        return new SmsMessage(com.android.internal.telephony.gsm.SmsMessage.newFromCMT(lines));
    }

    public static SmsMessage newFromParcel(Parcel p) {
        return new SmsMessage(com.android.internal.telephony.cdma.SmsMessage.newFromParcel(p));
    }

    public static SmsMessage createFromEfRecord(int index, byte[] data) {
        SmsMessageBase wrappedMessage;
        if (isCdmaVoice()) {
            wrappedMessage = com.android.internal.telephony.cdma.SmsMessage.createFromEfRecord(index, data);
        } else {
            wrappedMessage = com.android.internal.telephony.gsm.SmsMessage.createFromEfRecord(index, data);
        }
        return wrappedMessage != null ? new SmsMessage(wrappedMessage) : null;
    }

    public static SmsMessage createFromEfRecord(int index, byte[] data, String format) {
        SmsMessageBase wrappedMessage;
        if (FORMAT_3GPP2.equals(format)) {
            wrappedMessage = com.android.internal.telephony.cdma.SmsMessage.createFromEfRecord(index, data);
        } else {
            wrappedMessage = com.android.internal.telephony.gsm.SmsMessage.createFromEfRecord(index, data);
        }
        return wrappedMessage != null ? new SmsMessage(wrappedMessage) : null;
    }

    public static int getTPLayerLengthForPDU(String pdu) {
        if (isCdmaVoice()) {
            return com.android.internal.telephony.cdma.SmsMessage.getTPLayerLengthForPDU(pdu);
        }
        return com.android.internal.telephony.gsm.SmsMessage.getTPLayerLengthForPDU(pdu);
    }

    public static int[] calculateLength(CharSequence msgBody, boolean use7bitOnly) {
        TextEncodingDetails ted = useCdmaFormatForMoSms() ? com.android.internal.telephony.cdma.SmsMessage.calculateLength(msgBody, use7bitOnly) : com.android.internal.telephony.gsm.SmsMessage.calculateLength(msgBody, use7bitOnly);
        return new int[]{ted.msgCount, ted.codeUnitCount, ted.codeUnitsRemaining, ted.codeUnitSize};
    }

    public static int[] calculateLength(CharSequence msgBody, boolean use7bitOnly, boolean isEms) {
        TextEncodingDetails ted = useCdmaFormatForMoSms() ? com.android.internal.telephony.cdma.SmsMessage.calculateLength(msgBody, use7bitOnly, isEms) : com.android.internal.telephony.gsm.SmsMessage.calculateLength(msgBody, use7bitOnly);
        return new int[]{ted.msgCount, ted.codeUnitCount, ted.codeUnitsRemaining, ted.codeUnitSize};
    }

    public static ArrayList<String> fragmentText(String text) {
        return fragmentText(text, null);
    }

    public static ArrayList<String> fragmentText(String text, SmsManager smsManager) {
        int limit;
        TextEncodingDetails ted = useCdmaFormatForMoSms(smsManager) ? com.android.internal.telephony.cdma.SmsMessage.calculateLength(text, false) : com.android.internal.telephony.gsm.SmsMessage.calculateLength(text, false);
        if (ted.codeUnitSize == 1) {
            int udhLength;
            if (ted.languageTable != 0 && ted.languageShiftTable != 0) {
                udhLength = 7;
            } else if (ted.languageTable == 0 && ted.languageShiftTable == 0) {
                udhLength = 0;
            } else {
                udhLength = 4;
            }
            if (ted.msgCount > 1) {
                udhLength += 6;
            }
            if (udhLength != 0) {
                udhLength++;
            }
            limit = 160 - udhLength;
        } else if (ted.msgCount > 1) {
            limit = 134;
            if (!hasEmsSupport() && ted.msgCount < 10) {
                limit = 134 - 2;
            }
        } else {
            limit = 140;
        }
        String newMsgBody = null;
        if (Resources.getSystem().getBoolean(17956991)) {
            newMsgBody = Sms7BitEncodingTranslator.translate(text);
        }
        if (TextUtils.isEmpty(newMsgBody)) {
            newMsgBody = text;
        }
        int pos = 0;
        int textLen = newMsgBody.length();
        ArrayList<String> result = new ArrayList(ted.msgCount);
        while (pos < textLen) {
            int nextPos;
            if (ted.codeUnitSize != 1) {
                nextPos = pos + Math.min(limit / 2, textLen - pos);
            } else if (useCdmaFormatForMoSms(smsManager) && ted.msgCount == 1) {
                nextPos = pos + Math.min(limit, textLen - pos);
            } else {
                nextPos = GsmAlphabet.findGsmSeptetLimitIndex(newMsgBody, pos, limit, ted.languageTable, ted.languageShiftTable);
            }
            if (nextPos <= pos || nextPos > textLen) {
                Rlog.e(LOG_TAG, "fragmentText failed (" + pos + " >= " + nextPos + " or " + nextPos + " >= " + textLen + ")");
                break;
            }
            result.add(newMsgBody.substring(pos, nextPos));
            pos = nextPos;
        }
        return result;
    }

    public static ArrayList<String> fragmentText(String text, int encodingType) {
        return fragmentText(text, encodingType, null);
    }

    public static ArrayList<String> fragmentText(String text, int encodingType, SmsManager smsManager) {
        TextEncodingDetails ted;
        int limit;
        if (useCdmaFormatForMoSms(smsManager)) {
            ted = com.android.internal.telephony.cdma.SmsMessage.calculateLength(text, false);
        } else if (encodingType == 1) {
            ted = com.android.internal.telephony.gsm.SmsMessage.calculateLength(text, false, encodingType);
        } else {
            ted = com.android.internal.telephony.gsm.SmsMessage.calculateLength(text, false);
        }
        if (ted.msgCount <= 1) {
            limit = ted.codeUnitSize == 1 ? 160 : 140;
        } else if (GsmAlphabet.getEnabledSingleShiftTables().length < 1 || GsmAlphabet.getEnabledLockingShiftTables().length < 1) {
            if (GsmAlphabet.getEnabledSingleShiftTables().length >= 1 || GsmAlphabet.getEnabledLockingShiftTables().length >= 1) {
                limit = ted.codeUnitSize == 1 ? 149 : 131;
            } else {
                limit = ted.codeUnitSize == 1 ? 153 : 134;
            }
        } else if (ted.codeUnitSize == 1) {
            limit = 147;
        } else {
            limit = 128;
        }
        String newMsgBody = null;
        if (Resources.getSystem().getBoolean(17956991)) {
            newMsgBody = Sms7BitEncodingTranslator.translate(text);
        }
        if (TextUtils.isEmpty(newMsgBody)) {
            newMsgBody = text;
        }
        int pos = 0;
        int textLen = newMsgBody.length();
        ArrayList<String> result = new ArrayList(ted.msgCount);
        while (pos < textLen) {
            int nextPos;
            if (ted.codeUnitSize != 1) {
                nextPos = pos + Math.min(limit / 2, textLen - pos);
            } else if (useCdmaFormatForMoSms(smsManager) && ted.msgCount == 1) {
                nextPos = pos + Math.min(limit, textLen - pos);
            } else {
                nextPos = GsmAlphabet.findGsmSeptetLimitIndex(newMsgBody, pos, limit, ted.languageTable, ted.languageShiftTable);
            }
            if (nextPos <= pos || nextPos > textLen) {
                Rlog.d(LOG_TAG, "fragmentText failed (" + pos + " >= " + nextPos + " or " + nextPos + " >= " + textLen + ")");
                break;
            }
            result.add(newMsgBody.substring(pos, nextPos));
            pos = nextPos;
        }
        return result;
    }

    public static int[] calculateLength(String messageBody, boolean use7bitOnly) {
        return calculateLength((CharSequence) messageBody, use7bitOnly);
    }

    public static SubmitPdu getSubmitPdu(String scAddress, String destinationAddress, String message, boolean statusReportRequested) {
        SubmitPduBase spb;
        if (useCdmaFormatForMoSms()) {
            spb = com.android.internal.telephony.cdma.SmsMessage.getSubmitPdu(scAddress, destinationAddress, message, statusReportRequested, null);
        } else {
            spb = com.android.internal.telephony.gsm.SmsMessage.getSubmitPdu(scAddress, destinationAddress, message, statusReportRequested);
        }
        return new SubmitPdu(spb);
    }

    public static SubmitPdu getSubmitPdu(String scAddress, String destinationAddress, short destinationPort, byte[] data, boolean statusReportRequested) {
        SubmitPduBase spb;
        if (useCdmaFormatForMoSms()) {
            spb = com.android.internal.telephony.cdma.SmsMessage.getSubmitPdu(scAddress, destinationAddress, (int) destinationPort, data, statusReportRequested);
        } else {
            spb = com.android.internal.telephony.gsm.SmsMessage.getSubmitPdu(scAddress, destinationAddress, (int) destinationPort, data, statusReportRequested);
        }
        return new SubmitPdu(spb);
    }

    public static SubmitPdu getSubmitPdu(String scAddress, String destinationAddress, String message, boolean statusReportRequested, byte[] header, String callbackNumber, int priority) {
        SubmitPduBase spb;
        if (useCdmaFormatForMoSms()) {
            spb = com.android.internal.telephony.cdma.SmsMessage.getSubmitPdu(scAddress, destinationAddress, message, statusReportRequested, SmsHeader.fromByteArray(header), callbackNumber, priority);
        } else {
            spb = com.android.internal.telephony.gsm.SmsMessage.getSubmitPdu(scAddress, destinationAddress, message, statusReportRequested, header);
        }
        return new SubmitPdu(spb);
    }

    public String getServiceCenterAddress() {
        return this.mWrappedSmsMessage.getServiceCenterAddress();
    }

    public String getOriginatingAddress() {
        return this.mWrappedSmsMessage.getOriginatingAddress();
    }

    public String getDisplayOriginatingAddress() {
        return this.mWrappedSmsMessage.getDisplayOriginatingAddress();
    }

    public String getMessageBody() {
        return this.mWrappedSmsMessage.getMessageBody();
    }

    public MessageClass getMessageClass() {
        switch (C00061.f0xf858d1e4[this.mWrappedSmsMessage.getMessageClass().ordinal()]) {
            case 1:
                return MessageClass.CLASS_0;
            case 2:
                return MessageClass.CLASS_1;
            case 3:
                return MessageClass.CLASS_2;
            case 4:
                return MessageClass.CLASS_3;
            default:
                return MessageClass.UNKNOWN;
        }
    }

    public String getDisplayMessageBody() {
        return this.mWrappedSmsMessage.getDisplayMessageBody();
    }

    public String getPseudoSubject() {
        return this.mWrappedSmsMessage.getPseudoSubject();
    }

    public long getTimestampMillis() {
        return this.mWrappedSmsMessage.getTimestampMillis();
    }

    public boolean isEmail() {
        return this.mWrappedSmsMessage.isEmail();
    }

    public String getEmailBody() {
        return this.mWrappedSmsMessage.getEmailBody();
    }

    public String getEmailFrom() {
        return this.mWrappedSmsMessage.getEmailFrom();
    }

    public int getProtocolIdentifier() {
        return this.mWrappedSmsMessage.getProtocolIdentifier();
    }

    public boolean isReplace() {
        return this.mWrappedSmsMessage.isReplace();
    }

    public boolean isCphsMwiMessage() {
        return this.mWrappedSmsMessage.isCphsMwiMessage();
    }

    public boolean isMWIClearMessage() {
        return this.mWrappedSmsMessage.isMWIClearMessage();
    }

    public boolean isMWISetMessage() {
        return this.mWrappedSmsMessage.isMWISetMessage();
    }

    public boolean isMwiDontStore() {
        return this.mWrappedSmsMessage.isMwiDontStore();
    }

    public byte[] getUserData() {
        return this.mWrappedSmsMessage.getUserData();
    }

    public byte[] getPdu() {
        return this.mWrappedSmsMessage.getPdu();
    }

    @Deprecated
    public int getStatusOnSim() {
        return this.mWrappedSmsMessage.getStatusOnIcc();
    }

    public int getStatusOnIcc() {
        return this.mWrappedSmsMessage.getStatusOnIcc();
    }

    @Deprecated
    public int getIndexOnSim() {
        return this.mWrappedSmsMessage.getIndexOnIcc();
    }

    public int getIndexOnIcc() {
        return this.mWrappedSmsMessage.getIndexOnIcc();
    }

    public int getStatus() {
        return this.mWrappedSmsMessage.getStatus();
    }

    public boolean isStatusReportMessage() {
        return this.mWrappedSmsMessage.isStatusReportMessage();
    }

    public boolean isReplyPathPresent() {
        return this.mWrappedSmsMessage.isReplyPathPresent();
    }

    private static boolean useCdmaFormatForMoSms() {
        if (SmsManager.getDefault().isImsSmsSupported()) {
            return FORMAT_3GPP2.equals(SmsManager.getDefault().getImsSmsFormat());
        }
        return isCdmaVoice();
    }

    private static boolean useCdmaFormatForMoSms(SmsManager smsManager) {
        if (smsManager == null) {
            smsManager = SmsManager.getDefault();
        }
        if (smsManager.isImsSmsSupported()) {
            return FORMAT_3GPP2.equals(smsManager.getImsSmsFormat());
        }
        return FORMAT_3GPP2.equals(smsManager.getCurrentFormat());
    }

    public static int[] calculateLengthWithEmail(CharSequence msgBody, boolean use7bitOnly, int maxEmailLen) {
        TextEncodingDetails ted = useCdmaFormatForMoSms() ? com.android.internal.telephony.cdma.SmsMessage.calculateLengthWithEmail(msgBody, use7bitOnly, maxEmailLen) : com.android.internal.telephony.gsm.SmsMessage.calculateLength(msgBody, use7bitOnly);
        return new int[]{ted.msgCount, ted.codeUnitCount, ted.codeUnitsRemaining, ted.codeUnitSize};
    }

    public static int[] calculateLength(CharSequence msgBody, boolean use7bitOnly, int encodingType) {
        TextEncodingDetails ted = useCdmaFormatForMoSms() ? com.android.internal.telephony.cdma.SmsMessage.calculateLength(msgBody, use7bitOnly) : com.android.internal.telephony.gsm.SmsMessage.calculateLength(msgBody, use7bitOnly, encodingType);
        return new int[]{ted.msgCount, ted.codeUnitCount, ted.codeUnitsRemaining, ted.codeUnitSize};
    }

    private static boolean isCdmaVoice() {
        return 2 == TelephonyManager.getDefault().getCurrentPhoneType();
    }

    public int getMessageIdentifier() {
        return this.mWrappedSmsMessage.getMessageIdentifier();
    }

    public static boolean hasEmsSupport() {
        if (!isNoEmsSupportConfigListExisted()) {
            return true;
        }
        String simOperator = TelephonyManager.getDefault().getSimOperator();
        String gid = TelephonyManager.getDefault().getGroupIdLevel1();
        for (NoEmsSupportConfig currentConfig : mNoEmsSupportConfigList) {
            if (simOperator.startsWith(currentConfig.mOperatorNumber) && (TextUtils.isEmpty(currentConfig.mGid1) || (!TextUtils.isEmpty(currentConfig.mGid1) && currentConfig.mGid1.equalsIgnoreCase(gid)))) {
                return false;
            }
        }
        return true;
    }

    public String getSharedAppId() {
        return this.mWrappedSmsMessage.getSharedAppId();
    }

    public String getSharedCmd() {
        return this.mWrappedSmsMessage.getSharedCmd();
    }

    public int getTeleserviceId() {
        return this.mWrappedSmsMessage.getTeleserviceId();
    }

    public int getSessionId() {
        return this.mWrappedSmsMessage.getSessionId();
    }

    public int getSessionSeq() {
        return this.mWrappedSmsMessage.getSessionSeq();
    }

    public int getSessionSeqEos() {
        return this.mWrappedSmsMessage.getSessionSeqEos();
    }

    public String getSharedPayLoad() {
        return this.mWrappedSmsMessage.getSharedPayLoad();
    }

    public int getMessagePriority() {
        return this.mWrappedSmsMessage.getMessagePriority();
    }

    public String getCallbackNumber() {
        return this.mWrappedSmsMessage.getCallbackNumber();
    }

    public String getlinkUrl() {
        return this.mWrappedSmsMessage.getlinkUrl();
    }

    public SmsHeader getUserDataHeader() {
        return this.mWrappedSmsMessage.getUserDataHeader();
    }

    public int getMessageType() {
        return this.mWrappedSmsMessage.getMessageType();
    }

    public byte[] getBearerData() {
        return this.mWrappedSmsMessage.getBearerData();
    }

    public String getDisplayDestinationAddress() {
        return this.mWrappedSmsMessage.getDisplayDestinationAddress();
    }

    public static SubmitPdu getSimSubmitPdu(String scAddress, String Address, String message, byte[] header, String format) {
        SubmitPduBase spb;
        Rlog.d(LOG_TAG, "android.telephony.SmsMessage.java - getSimSubmitPdu");
        int activePhone = TelephonyManager.getDefault().getCurrentPhoneType();
        if (FORMAT_3GPP2.equals(format)) {
            activePhone = 2;
        } else {
            activePhone = 1;
        }
        if (2 == activePhone) {
            spb = com.android.internal.telephony.cdma.SmsMessage.getRuimSubmitPdu(scAddress, Address, message);
        } else {
            spb = com.android.internal.telephony.gsm.SmsMessage.getSimSubmitPdu(scAddress, Address, message, header);
        }
        return new SubmitPdu(spb);
    }

    public static SubmitPdu getSimSubmitPdu(String scAddress, String Address, String message, byte[] header) {
        SubmitPduBase spb;
        Rlog.d(LOG_TAG, "android.telephony.SmsMessage.java - getSimSubmitPdu");
        if (2 == TelephonyManager.getDefault().getCurrentPhoneType()) {
            spb = com.android.internal.telephony.cdma.SmsMessage.getRuimSubmitPdu(scAddress, Address, message);
        } else {
            spb = com.android.internal.telephony.gsm.SmsMessage.getSimSubmitPdu(scAddress, Address, message, header);
        }
        return new SubmitPdu(spb);
    }

    public static DeliverPdu getSimDeliverPdu(String scAddress, String Address, String message, String date, byte[] header, String format) {
        DeliverPduBase spb;
        Rlog.d(LOG_TAG, "android.telephony.SmsMessage.java - getSimDeliverPdu");
        int activePhone = TelephonyManager.getDefault().getCurrentPhoneType();
        if (FORMAT_3GPP2.equals(format)) {
            activePhone = 2;
        } else {
            activePhone = 1;
        }
        if (2 == activePhone) {
            spb = com.android.internal.telephony.cdma.SmsMessage.getRuimDeliveryPdu(scAddress, Address, message, date);
        } else {
            spb = com.android.internal.telephony.gsm.SmsMessage.getSimDeliverPdu(scAddress, Address, message, date, header);
        }
        return new DeliverPdu(spb);
    }

    public static DeliverPdu getSimDeliverPdu(String scAddress, String Address, String message, String date, byte[] header) {
        DeliverPduBase spb;
        Rlog.d(LOG_TAG, "android.telephony.SmsMessage.java - getSimDeliverPdu");
        if (2 == TelephonyManager.getDefault().getCurrentPhoneType()) {
            spb = com.android.internal.telephony.cdma.SmsMessage.getRuimDeliveryPdu(scAddress, Address, message, date);
        } else {
            spb = com.android.internal.telephony.gsm.SmsMessage.getSimDeliverPdu(scAddress, Address, message, date, header);
        }
        return new DeliverPdu(spb);
    }

    public static boolean shouldAppendPageNumberAsPrefix() {
        if (!isNoEmsSupportConfigListExisted()) {
            return false;
        }
        String simOperator = TelephonyManager.getDefault().getSimOperator();
        String gid = TelephonyManager.getDefault().getGroupIdLevel1();
        for (NoEmsSupportConfig currentConfig : mNoEmsSupportConfigList) {
            if (simOperator.startsWith(currentConfig.mOperatorNumber) && (TextUtils.isEmpty(currentConfig.mGid1) || (!TextUtils.isEmpty(currentConfig.mGid1) && currentConfig.mGid1.equalsIgnoreCase(gid)))) {
                return currentConfig.mIsPrefix;
            }
        }
        return false;
    }

    private static boolean isNoEmsSupportConfigListExisted() {
        if (!mIsNoEmsSupportConfigListLoaded) {
            Resources r = Resources.getSystem();
            if (r != null) {
                String[] listArray = r.getStringArray(17236024);
                if (listArray != null && listArray.length > 0) {
                    mNoEmsSupportConfigList = new NoEmsSupportConfig[listArray.length];
                    for (int i = 0; i < listArray.length; i++) {
                        mNoEmsSupportConfigList[i] = new NoEmsSupportConfig(listArray[i].split(";"));
                    }
                }
                mIsNoEmsSupportConfigListLoaded = true;
            }
        }
        if (mNoEmsSupportConfigList == null || mNoEmsSupportConfigList.length == 0) {
            return false;
        }
        return true;
    }

    public static void writePduFormat(String pduFormat) {
        PduFormatChecker.write(pduFormat);
        String pduFomat = PduFormatChecker.read();
        String str = LOG_TAG;
        StringBuilder append = new StringBuilder().append("[writePduFormat] pduFormat = ");
        if (pduFomat == null) {
            pduFomat = "none";
        }
        Rlog.e(str, append.append(pduFomat).toString());
    }
}
