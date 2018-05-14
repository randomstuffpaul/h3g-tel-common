package com.android.internal.telephony.cdma.sms;

import android.content.res.Resources;
import android.os.SystemProperties;
import android.telephony.Rlog;
import android.telephony.SmsCbCmasInfo;
import android.telephony.cdma.CdmaSmsCbProgramData;
import android.telephony.cdma.CdmaSmsCbProgramResults;
import android.text.format.Time;
import com.android.internal.telephony.EncodeException;
import com.android.internal.telephony.GsmAlphabet;
import com.android.internal.telephony.GsmAlphabet.TextEncodingDetails;
import com.android.internal.telephony.SMSDispatcher;
import com.android.internal.telephony.SmsHeader;
import com.android.internal.telephony.SmsHeader.ConcatRef;
import com.android.internal.telephony.gsm.SmsMessage;
import com.android.internal.telephony.uicc.IccUtils;
import com.android.internal.util.BitwiseInputStream;
import com.android.internal.util.BitwiseOutputStream;
import com.android.internal.util.BitwiseOutputStream.AccessException;
import com.google.android.mms.pdu.CharacterSets;
import com.google.android.mms.pdu.PduPart;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.TimeZone;

public final class BearerData {
    public static final int ALERT_DEFAULT = 0;
    public static final int ALERT_HIGH_PRIO = 3;
    public static final int ALERT_LOW_PRIO = 1;
    public static final int ALERT_MEDIUM_PRIO = 2;
    public static final int CMAS_CATEGORY_RECORD = 1;
    public static final int CMAS_IDENTIFIER_RECORD = 2;
    public static final int CMAS_MESSAGE_RECORD = 0;
    public static final int CMAS_PROTOCOL_VER = 0;
    public static final int CMAS_RECORD_TYPE_0 = 0;
    public static final int CMAS_RECORD_TYPE_1 = 1;
    public static final int CMAS_RECORD_TYPE_2 = 2;
    public static final int CMAS_RECORD_TYPE_3 = 3;
    public static final int CMAS_SUPPORT_TYPE = 1;
    public static final int CMAS_TYPE_0_1 = 3;
    public static final int CMAS_TYPE_0_2 = 5;
    public static final int CMAS_TYPE_1_2 = 7;
    public static final int DISPLAY_MODE_DEFAULT = 1;
    public static final int DISPLAY_MODE_IMMEDIATE = 0;
    public static final int DISPLAY_MODE_USER = 2;
    public static final int ERROR_NONE = 0;
    public static final int ERROR_PERMANENT = 3;
    public static final int ERROR_TEMPORARY = 2;
    public static final int ERROR_UNDEFINED = 255;
    public static final int LANGUAGE_CHINESE = 6;
    public static final int LANGUAGE_ENGLISH = 1;
    public static final int LANGUAGE_FRENCH = 2;
    public static final int LANGUAGE_HEBREW = 7;
    public static final int LANGUAGE_JAPANESE = 4;
    public static final int LANGUAGE_KOREAN = 5;
    public static final int LANGUAGE_SPANISH = 3;
    public static final int LANGUAGE_UNKNOWN = 0;
    public static final int LANGUAGE_UNKNOWN_LGT = 64;
    private static final String LOG_TAG = "BearerData";
    public static final int MESSAGE_TYPE_CANCELLATION = 3;
    public static final int MESSAGE_TYPE_DELIVER = 1;
    public static final int MESSAGE_TYPE_DELIVERY_ACK = 4;
    public static final int MESSAGE_TYPE_DELIVER_REPORT = 7;
    public static final int MESSAGE_TYPE_READ_ACK = 6;
    public static final int MESSAGE_TYPE_SUBMIT = 2;
    public static final int MESSAGE_TYPE_SUBMIT_REPORT = 8;
    public static final int MESSAGE_TYPE_USER_ACK = 5;
    public static final int PRIORITY_EMERGENCY = 3;
    public static final int PRIORITY_INTERACTIVE = 1;
    public static final int PRIORITY_NORMAL = 0;
    public static final int PRIORITY_URGENT = 2;
    public static final int PRIVACY_CONFIDENTIAL = 2;
    public static final int PRIVACY_NOT_RESTRICTED = 0;
    public static final int PRIVACY_RESTRICTED = 1;
    public static final int PRIVACY_SECRET = 3;
    public static final int RELATIVE_TIME_DAYS_LIMIT = 196;
    public static final int RELATIVE_TIME_HOURS_LIMIT = 167;
    public static final int RELATIVE_TIME_INDEFINITE = 245;
    public static final int RELATIVE_TIME_MINS_LIMIT = 143;
    public static final int RELATIVE_TIME_MOBILE_INACTIVE = 247;
    public static final int RELATIVE_TIME_NOW = 246;
    public static final int RELATIVE_TIME_RESERVED = 248;
    public static final int RELATIVE_TIME_WEEKS_LIMIT = 244;
    public static final int STATUS_ACCEPTED = 0;
    public static final int STATUS_BLOCKED_DESTINATION = 7;
    public static final int STATUS_CANCELLED = 3;
    public static final int STATUS_CANCEL_FAILED = 6;
    public static final int STATUS_DELIVERED = 2;
    public static final int STATUS_DEPOSITED_TO_INTERNET = 1;
    public static final int STATUS_DUPLICATE_MESSAGE = 9;
    public static final int STATUS_INVALID_DESTINATION = 10;
    public static final int STATUS_MESSAGE_EXPIRED = 13;
    public static final int STATUS_NETWORK_CONGESTION = 4;
    public static final int STATUS_NETWORK_ERROR = 5;
    public static final int STATUS_TEXT_TOO_LONG = 8;
    public static final int STATUS_UNDEFINED = 255;
    public static final int STATUS_UNKNOWN_ERROR = 31;
    private static final byte SUBPARAM_ALERT_ON_MESSAGE_DELIVERY = (byte) 12;
    private static final int SUBPARAM_BG_IMAGE = 128;
    private static final byte SUBPARAM_CALLBACK_NUMBER = (byte) 14;
    private static final byte SUBPARAM_DEFERRED_DELIVERY_TIME_ABSOLUTE = (byte) 6;
    private static final byte SUBPARAM_DEFERRED_DELIVERY_TIME_RELATIVE = (byte) 7;
    private static final byte SUBPARAM_ID_LAST_DEFINED = (byte) 23;
    private static final byte SUBPARAM_LANGUAGE_INDICATOR = (byte) 13;
    private static final byte SUBPARAM_MESSAGE_CENTER_TIME_STAMP = (byte) 3;
    private static final byte SUBPARAM_MESSAGE_DEPOSIT_INDEX = (byte) 17;
    private static final byte SUBPARAM_MESSAGE_DISPLAY_MODE = (byte) 15;
    private static final byte SUBPARAM_MESSAGE_IDENTIFIER = (byte) 0;
    private static final byte SUBPARAM_MESSAGE_STATUS = (byte) 20;
    private static final byte SUBPARAM_NUMBER_OF_MESSAGES = (byte) 11;
    private static final byte SUBPARAM_PRIORITY_INDICATOR = (byte) 8;
    private static final byte SUBPARAM_PRIVACY_INDICATOR = (byte) 9;
    private static final byte SUBPARAM_REPLY_OPTION = (byte) 10;
    private static final byte SUBPARAM_SERVICE_CATEGORY_PROGRAM_DATA = (byte) 18;
    private static final byte SUBPARAM_SERVICE_CATEGORY_PROGRAM_RESULTS = (byte) 19;
    private static final int SUBPARAM_SESSION_INFO = 129;
    private static final byte SUBPARAM_USER_DATA = (byte) 1;
    private static final byte SUBPARAM_USER_RESPONSE_CODE = (byte) 2;
    private static final byte SUBPARAM_VALIDITY_PERIOD_ABSOLUTE = (byte) 4;
    private static final byte SUBPARAM_VALIDITY_PERIOD_RELATIVE = (byte) 5;
    public static char compChar;
    public static int language = 0;
    public static int mBodyOffset;
    public static boolean mIsfourBytesUnicode;
    public static byte[] mlastByte;
    public static int userLength;
    public static byte[] userdata;
    public int alert = 0;
    public boolean alertIndicatorSet = false;
    public CdmaSmsAddress callbackNumber;
    public SmsCbCmasInfo cmasWarningInfo;
    public TimeStamp deferredDeliveryTimeAbsolute;
    public int deferredDeliveryTimeRelative;
    public boolean deferredDeliveryTimeRelativeSet;
    public boolean deliveryAckReq;
    public int depositIndex;
    public int displayMode = 1;
    public boolean displayModeSet = false;
    public int errorClass = 255;
    public boolean hasUserDataHeader;
    public boolean isMitvSet = false;
    public boolean languageIndicatorSet = false;
    public boolean lguCbsSessionSet = false;
    public int messageId;
    public int messageStatus = 255;
    public boolean messageStatusSet = false;
    public int messageType;
    public TimeStamp msgCenterTimeStamp;
    public String msgDeliveryTime;
    public int numberOfMessages;
    public int priority = 0;
    public boolean priorityIndicatorSet = false;
    public int privacy = 0;
    public boolean privacyIndicatorSet = false;
    public boolean readAckReq;
    public boolean reportReq;
    public ArrayList<CdmaSmsCbProgramData> serviceCategoryProgramData;
    public ArrayList<CdmaSmsCbProgramResults> serviceCategoryProgramResults;
    public int sessionId;
    public int sessionSeq;
    public int sessionSeqEos;
    public boolean userAckReq;
    public UserData userData;
    public int userResponseCode;
    public boolean userResponseCodeSet = false;
    public TimeStamp validityPeriodAbsolute;
    public int validityPeriodRelative;
    public boolean validityPeriodRelativeSet;

    private static class CodingException extends Exception {
        public CodingException(String s) {
            super(s);
        }
    }

    private static class Gsm7bitCodingResult {
        byte[] data;
        int septets;

        private Gsm7bitCodingResult() {
        }
    }

    public static class TimeStamp extends Time {
        public TimeStamp() {
            super(TimeZone.getDefault().getID());
        }

        public TimeStamp(String timezone) {
            super(timezone);
        }

        public static TimeStamp fromByteArray(byte[] data) {
            TimeStamp ts = new TimeStamp();
            int year = IccUtils.cdmaBcdByteToInt(data[0]);
            if (year > 99 || year < 0) {
                return null;
            }
            ts.year = year >= 96 ? year + 1900 : year + 2000;
            int month = IccUtils.cdmaBcdByteToInt(data[1]);
            if (month < 1 || month > 12) {
                return null;
            }
            ts.month = month - 1;
            int day = IccUtils.cdmaBcdByteToInt(data[2]);
            if (day < 1 || day > 31) {
                return null;
            }
            ts.monthDay = day;
            int hour = IccUtils.cdmaBcdByteToInt(data[3]);
            if (hour < 0 || hour > 23) {
                return null;
            }
            ts.hour = hour;
            int minute = IccUtils.cdmaBcdByteToInt(data[4]);
            if (minute < 0 || minute > 59) {
                return null;
            }
            ts.minute = minute;
            int second = IccUtils.cdmaBcdByteToInt(data[5]);
            if (second < 0 || second > 59) {
                return null;
            }
            ts.second = second;
            return ts;
        }

        public String toString() {
            StringBuilder builder = new StringBuilder();
            builder.append("TimeStamp ");
            builder.append("{ year=" + this.year);
            builder.append(", month=" + this.month);
            builder.append(", day=" + this.monthDay);
            builder.append(", hour=" + this.hour);
            builder.append(", minute=" + this.minute);
            builder.append(", second=" + this.second);
            builder.append(" }");
            return builder.toString();
        }
    }

    public String getLanguage() {
        return getLanguageCodeForValue(language);
    }

    private static String getLanguageCodeForValue(int languageValue) {
        switch (languageValue) {
            case 1:
                return "en";
            case 2:
                return "fr";
            case 3:
                return "es";
            case 4:
                return "ja";
            case 5:
                return "ko";
            case 6:
                return "zh";
            case 7:
                return "he";
            default:
                return null;
        }
    }

    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("BearerData ");
        builder.append("{ messageType=" + this.messageType);
        builder.append(", messageId=" + this.messageId);
        builder.append(", priority=" + (this.priorityIndicatorSet ? Integer.valueOf(this.priority) : "unset"));
        builder.append(", privacy=" + (this.privacyIndicatorSet ? Integer.valueOf(this.privacy) : "unset"));
        builder.append(", alert=" + (this.alertIndicatorSet ? Integer.valueOf(this.alert) : "unset"));
        builder.append(", displayMode=" + (this.displayModeSet ? Integer.valueOf(this.displayMode) : "unset"));
        builder.append(", language=" + (this.languageIndicatorSet ? Integer.valueOf(language) : "unset"));
        builder.append(", errorClass=" + (this.messageStatusSet ? Integer.valueOf(this.errorClass) : "unset"));
        builder.append(", msgStatus=" + (this.messageStatusSet ? Integer.valueOf(this.messageStatus) : "unset"));
        builder.append(", msgCenterTimeStamp=" + (this.msgCenterTimeStamp != null ? this.msgCenterTimeStamp : "unset"));
        builder.append(", validityPeriodAbsolute=" + (this.validityPeriodAbsolute != null ? this.validityPeriodAbsolute : "unset"));
        builder.append(", validityPeriodRelative=" + (this.validityPeriodRelativeSet ? Integer.valueOf(this.validityPeriodRelative) : "unset"));
        builder.append(", deferredDeliveryTimeAbsolute=" + (this.deferredDeliveryTimeAbsolute != null ? this.deferredDeliveryTimeAbsolute : "unset"));
        builder.append(", deferredDeliveryTimeRelative=" + (this.deferredDeliveryTimeRelativeSet ? Integer.valueOf(this.deferredDeliveryTimeRelative) : "unset"));
        builder.append(", userAckReq=" + this.userAckReq);
        builder.append(", deliveryAckReq=" + this.deliveryAckReq);
        builder.append(", readAckReq=" + this.readAckReq);
        builder.append(", reportReq=" + this.reportReq);
        builder.append(", numberOfMessages=" + this.numberOfMessages);
        builder.append(", callbackNumber=" + this.callbackNumber);
        builder.append(", depositIndex=" + this.depositIndex);
        builder.append(", hasUserDataHeader=" + this.hasUserDataHeader);
        builder.append(", userData=" + this.userData);
        builder.append(" }");
        return builder.toString();
    }

    private static void encodeMessageId(BearerData bData, BitwiseOutputStream outStream) throws AccessException {
        outStream.write(8, 3);
        outStream.write(4, bData.messageType);
        outStream.write(8, bData.messageId >> 8);
        outStream.write(8, bData.messageId);
        outStream.write(1, bData.hasUserDataHeader ? 1 : 0);
        outStream.skip(3);
    }

    private static void encode7bitAsciiEms(UserData uData, byte[] udhData, boolean force) throws CodingException {
        byte[] payload = encode7bitAscii(uData.payloadStr, force);
        int payloadCodeUnits = uData.payloadStr.length();
        int udhBytes = udhData.length + 1;
        int totalLength = payload.length + udhBytes;
        if (totalLength > 140) {
            throw new CodingException("encoded user data too large (" + totalLength + " > " + 140 + " bytes)");
        }
        uData.msgEncoding = 2;
        uData.msgEncodingSet = true;
        uData.numFields = udhBytes + payloadCodeUnits;
        uData.payload = new byte[totalLength];
        uData.payload[0] = (byte) udhData.length;
        System.arraycopy(udhData, 0, uData.payload, 1, udhData.length);
        System.arraycopy(payload, 0, uData.payload, udhBytes, payload.length);
    }

    private static byte[] encodeKSC5601(String msg) throws CodingException {
        try {
            return msg.getBytes(CharacterSets.MIMENAME_EUC_KR);
        } catch (UnsupportedEncodingException ex) {
            throw new CodingException("EUC_KR encode failed: " + ex);
        }
    }

    private static int countAsciiSeptets(CharSequence msg, boolean force) {
        int msgLen = msg.length();
        if (force) {
            return msgLen;
        }
        for (int i = 0; i < msgLen; i++) {
            if (UserData.charToAscii.get(msg.charAt(i), -1) == -1) {
                return -1;
            }
        }
        return msgLen;
    }

    public static TextEncodingDetails calcTextEncodingDetails(CharSequence msg, boolean force7BitEncoding) {
        int septets = countAsciiSeptets(msg, force7BitEncoding);
        TextEncodingDetails ted;
        if (septets == -1) {
            if (septets != -1) {
            }
            SystemProperties.set("persist.radio.cdma.lms", "true");
            ted = SmsMessage.calculateLength(msg, force7BitEncoding);
            SystemProperties.set("persist.radio.cdma.lms", "false");
            return ted.msgCount != 1 ? ted : ted;
        } else if (septets != -1 || septets > 160) {
            SystemProperties.set("persist.radio.cdma.lms", "true");
            ted = SmsMessage.calculateLength(msg, force7BitEncoding);
            SystemProperties.set("persist.radio.cdma.lms", "false");
            if (ted.msgCount != 1 && ted.codeUnitSize == 1) {
                ted.codeUnitCount = msg.length();
                int octets = ted.codeUnitCount * 2;
                if (octets > 140) {
                    int max_user_data_bytes_with_header = 134;
                    if (!android.telephony.SmsMessage.hasEmsSupport() && octets <= 1188) {
                        max_user_data_bytes_with_header = 134 - 2;
                    }
                    ted.msgCount = ((max_user_data_bytes_with_header - 1) + octets) / max_user_data_bytes_with_header;
                    ted.codeUnitsRemaining = ((ted.msgCount * max_user_data_bytes_with_header) - octets) / 2;
                } else {
                    ted.msgCount = 1;
                    ted.codeUnitsRemaining = (140 - octets) / 2;
                }
                ted.codeUnitSize = 3;
                return ted;
            }
        } else {
            ted = new TextEncodingDetails();
            ted.msgCount = 1;
            ted.codeUnitCount = septets;
            ted.codeUnitsRemaining = 160 - septets;
            ted.codeUnitSize = 1;
            return ted;
        }
    }

    private static byte[] encode7bitAscii(String msg, boolean force) throws CodingException {
        try {
            BitwiseOutputStream outStream = new BitwiseOutputStream(msg.length());
            int msgLen = msg.length();
            for (int i = 0; i < msgLen; i++) {
                int charCode = UserData.charToAscii.get(msg.charAt(i), -1);
                if (charCode != -1) {
                    outStream.write(7, charCode);
                } else if (force) {
                    outStream.write(7, 32);
                } else {
                    throw new CodingException("cannot ASCII encode (" + msg.charAt(i) + ")");
                }
            }
            return outStream.toByteArray();
        } catch (AccessException ex) {
            throw new CodingException("7bit ASCII encode failed: " + ex);
        }
    }

    private static byte[] encodeUtf16(String msg) throws CodingException {
        try {
            return msg.getBytes("utf-16be");
        } catch (UnsupportedEncodingException ex) {
            throw new CodingException("UTF-16 encode failed: " + ex);
        }
    }

    private static Gsm7bitCodingResult encode7bitGsm(String msg, int septetOffset, boolean force) throws CodingException {
        boolean z = true;
        if (force) {
            z = false;
        }
        try {
            byte[] fullData = GsmAlphabet.stringToGsm7BitPacked(msg, septetOffset, z, 0, 0);
            Gsm7bitCodingResult result = new Gsm7bitCodingResult();
            result.data = new byte[(fullData.length - 1)];
            System.arraycopy(fullData, 1, result.data, 0, fullData.length - 1);
            result.septets = fullData[0] & 255;
            return result;
        } catch (EncodeException ex) {
            throw new CodingException("7bit GSM encode failed: " + ex);
        }
    }

    private static void encode7bitEms(UserData uData, byte[] udhData, boolean force) throws CodingException {
        Gsm7bitCodingResult gcr = encode7bitGsm(uData.payloadStr, (((udhData.length + 1) * 8) + 6) / 7, force);
        uData.msgEncoding = 9;
        uData.msgEncodingSet = true;
        uData.numFields = gcr.septets;
        uData.payload = gcr.data;
        uData.payload[0] = (byte) udhData.length;
        System.arraycopy(udhData, 0, uData.payload, 1, udhData.length);
    }

    private static void encode16bitEms(UserData uData, byte[] udhData) throws CodingException {
        byte[] payload = encodeUtf16(uData.payloadStr);
        int udhBytes = udhData.length + 1;
        int udhCodeUnits = (udhBytes + 1) / 2;
        int payloadCodeUnits = payload.length / 2;
        uData.msgEncoding = 4;
        uData.msgEncodingSet = true;
        uData.numFields = udhCodeUnits + payloadCodeUnits;
        uData.payload = new byte[(uData.numFields * 2)];
        uData.payload[0] = (byte) udhData.length;
        System.arraycopy(udhData, 0, uData.payload, 1, udhData.length);
        System.arraycopy(payload, 0, uData.payload, udhBytes, payload.length);
    }

    private static void encodeEmsUserDataPayload(UserData uData) throws CodingException {
        byte[] headerData = SmsHeader.toByteArray(uData.userDataHeader);
        if (!uData.msgEncodingSet) {
            try {
                encode7bitEms(uData, headerData, false);
            } catch (CodingException e) {
                encode16bitEms(uData, headerData);
            }
        } else if (uData.msgEncoding == 9) {
            encode7bitEms(uData, headerData, true);
        } else if (uData.msgEncoding == 4) {
            encode16bitEms(uData, headerData);
        } else if (uData.msgEncoding == 2) {
            encode7bitAsciiEms(uData, headerData, true);
        } else {
            throw new CodingException("unsupported EMS user data encoding (" + uData.msgEncoding + ")");
        }
    }

    private static byte[] encodeShiftJis(String msg) throws CodingException {
        try {
            return msg.getBytes("Shift_JIS");
        } catch (UnsupportedEncodingException ex) {
            throw new CodingException("Shift-JIS encode failed: " + ex);
        }
    }

    private static void encodeUserDataPayload(UserData uData) throws CodingException {
        if (uData.payloadStr == null && uData.msgEncoding != 0) {
            Rlog.e(LOG_TAG, "user data with null payloadStr");
            uData.payloadStr = "";
        }
        if (uData.userDataHeader != null) {
            encodeEmsUserDataPayload(uData);
        } else if (!uData.msgEncodingSet) {
            try {
                uData.payload = encode7bitAscii(uData.payloadStr, false);
                uData.msgEncoding = 2;
            } catch (CodingException e) {
                uData.payload = encodeUtf16(uData.payloadStr);
                uData.msgEncoding = 4;
            }
            uData.numFields = uData.payloadStr.length();
            uData.msgEncodingSet = true;
        } else if (uData.msgEncoding != 0) {
            if (uData.payloadStr == null) {
                Rlog.e(LOG_TAG, "non-octet user data with null payloadStr");
                uData.payloadStr = "";
            }
            if (uData.msgEncoding == 9) {
                Gsm7bitCodingResult gcr = encode7bitGsm(uData.payloadStr, 0, true);
                uData.payload = gcr.data;
                uData.numFields = gcr.septets;
            } else if (uData.msgEncoding == 2) {
                uData.payload = encode7bitAscii(uData.payloadStr, true);
                uData.numFields = uData.payloadStr.length();
            } else if (uData.msgEncoding == 4) {
                uData.payload = encodeUtf16(uData.payloadStr);
                uData.numFields = uData.payloadStr.length();
            } else if (uData.msgEncoding == 5) {
                uData.payload = encodeShiftJis(uData.payloadStr);
                uData.numFields = uData.payload.length;
            } else {
                throw new CodingException("unsupported user data encoding (" + uData.msgEncoding + ")");
            }
        } else if (uData.payload == null) {
            Rlog.e(LOG_TAG, "user data with octet encoding but null payload");
            uData.payload = new byte[0];
            uData.numFields = 0;
        } else {
            uData.numFields = uData.payload.length;
        }
    }

    private static void encodeUserData(BearerData bData, BitwiseOutputStream outStream) throws AccessException, CodingException {
        boolean z;
        encodeUserDataPayload(bData.userData);
        if (bData.userData.userDataHeader != null) {
            z = true;
        } else {
            z = false;
        }
        bData.hasUserDataHeader = z;
        if (bData.userData.payload.length > 140) {
            throw new CodingException("encoded user data too large (" + bData.userData.payload.length + " > " + 140 + " bytes)");
        }
        int i;
        int dataBits = (bData.userData.payload.length * 8) - bData.userData.paddingBits;
        int paramBits = dataBits + 13;
        if (bData.userData.msgEncoding == 1 || bData.userData.msgEncoding == 10) {
            paramBits += 8;
        }
        int i2 = paramBits / 8;
        if (paramBits % 8 > 0) {
            i = 1;
        } else {
            i = 0;
        }
        int paramBytes = i2 + i;
        int paddingBits = (paramBytes * 8) - paramBits;
        outStream.write(8, paramBytes);
        outStream.write(5, bData.userData.msgEncoding);
        if (bData.userData.msgEncoding == 1 || bData.userData.msgEncoding == 10) {
            outStream.write(8, bData.userData.msgType);
        }
        outStream.write(8, bData.userData.numFields);
        outStream.writeByteArray(dataBits, bData.userData.payload);
        if (paddingBits > 0) {
            outStream.write(paddingBits, 0);
        }
    }

    private static void encodeReplyOption(BearerData bData, BitwiseOutputStream outStream) throws AccessException {
        int i;
        outStream.write(8, 1);
        outStream.write(1, bData.userAckReq ? 1 : 0);
        if (bData.deliveryAckReq) {
            i = 1;
        } else {
            i = 0;
        }
        outStream.write(1, i);
        if (bData.readAckReq) {
            i = 1;
        } else {
            i = 0;
        }
        outStream.write(1, i);
        if (bData.reportReq) {
            i = 1;
        } else {
            i = 0;
        }
        outStream.write(1, i);
        outStream.write(4, 0);
    }

    private static byte[] encodeDtmfSmsAddress(String address) {
        int digits = address.length();
        int dataBits = digits * 4;
        byte[] rawData = new byte[((dataBits / 8) + (dataBits % 8 > 0 ? 1 : 0))];
        for (int i = 0; i < digits; i++) {
            int val;
            char c = address.charAt(i);
            if (c >= '1' && c <= '9') {
                val = c - 48;
            } else if (c == '0') {
                val = 10;
            } else if (c == '*') {
                val = 11;
            } else if (c != '#') {
                return null;
            } else {
                val = 12;
            }
            int i2 = i / 2;
            rawData[i2] = (byte) (rawData[i2] | (val << (4 - ((i % 2) * 4))));
        }
        return rawData;
    }

    private static void encodeCdmaSmsAddress(CdmaSmsAddress addr) throws CodingException {
        if (addr.digitMode == 1) {
            try {
                addr.origBytes = addr.address.getBytes("US-ASCII");
                return;
            } catch (UnsupportedEncodingException e) {
                throw new CodingException("invalid SMS address, cannot convert to ASCII");
            }
        }
        addr.origBytes = encodeDtmfSmsAddress(addr.address);
    }

    private static void encodeCallbackNumber(BearerData bData, BitwiseOutputStream outStream) throws AccessException, CodingException {
        int dataBits;
        int i;
        CdmaSmsAddress addr = bData.callbackNumber;
        encodeCdmaSmsAddress(addr);
        int paramBits = 9;
        if (addr.digitMode == 1) {
            paramBits = 9 + 7;
            dataBits = addr.numberOfDigits * 8;
        } else {
            dataBits = addr.numberOfDigits * 4;
        }
        paramBits += dataBits;
        int i2 = paramBits / 8;
        if (paramBits % 8 > 0) {
            i = 1;
        } else {
            i = 0;
        }
        int paramBytes = i2 + i;
        int paddingBits = (paramBytes * 8) - paramBits;
        outStream.write(8, paramBytes);
        outStream.write(1, addr.digitMode);
        if (addr.digitMode == 1) {
            outStream.write(3, addr.ton);
            outStream.write(4, addr.numberPlan);
        }
        outStream.write(8, addr.numberOfDigits);
        outStream.writeByteArray(dataBits, addr.origBytes);
        if (paddingBits > 0) {
            outStream.write(paddingBits, 0);
        }
    }

    private static void encodeMsgStatus(BearerData bData, BitwiseOutputStream outStream) throws AccessException {
        outStream.write(8, 1);
        outStream.write(2, bData.errorClass);
        outStream.write(6, bData.messageStatus);
    }

    private static void encodeMsgCount(BearerData bData, BitwiseOutputStream outStream) throws AccessException {
        outStream.write(8, 1);
        outStream.write(8, bData.numberOfMessages);
    }

    private static void encodeValidityPeriodRel(BearerData bData, BitwiseOutputStream outStream) throws AccessException {
        outStream.write(8, 1);
        outStream.write(8, bData.validityPeriodRelative);
    }

    private static void encodePrivacyIndicator(BearerData bData, BitwiseOutputStream outStream) throws AccessException {
        outStream.write(8, 1);
        outStream.write(2, bData.privacy);
        outStream.skip(6);
    }

    private static void encodeLanguageIndicator(BearerData bData, BitwiseOutputStream outStream) throws AccessException {
        outStream.write(8, 1);
        outStream.write(8, language);
    }

    private static void encodeDisplayMode(BearerData bData, BitwiseOutputStream outStream) throws AccessException {
        outStream.write(8, 1);
        outStream.write(2, bData.displayMode);
        outStream.skip(6);
    }

    private static void encodePriorityIndicator(BearerData bData, BitwiseOutputStream outStream) throws AccessException {
        outStream.write(8, 1);
        outStream.write(2, bData.priority);
        outStream.skip(6);
    }

    private static void encodeMsgDeliveryAlert(BearerData bData, BitwiseOutputStream outStream) throws AccessException {
        outStream.write(8, 1);
        outStream.write(2, bData.alert);
        outStream.skip(6);
    }

    private static void encodeScpResults(BearerData bData, BitwiseOutputStream outStream) throws AccessException {
        ArrayList<CdmaSmsCbProgramResults> results = bData.serviceCategoryProgramResults;
        outStream.write(8, results.size() * 4);
        Iterator i$ = results.iterator();
        while (i$.hasNext()) {
            CdmaSmsCbProgramResults result = (CdmaSmsCbProgramResults) i$.next();
            int category = result.getCategory();
            outStream.write(8, category >> 8);
            outStream.write(8, category);
            outStream.write(8, result.getLanguage());
            outStream.write(4, result.getCategoryResult());
            outStream.skip(4);
        }
    }

    public static byte[] encode(BearerData bData) {
        boolean z = true;
        if (bData.userData == null || bData.userData.userDataHeader == null) {
            z = false;
        }
        bData.hasUserDataHeader = z;
        try {
            BitwiseOutputStream outStream = new BitwiseOutputStream(PduPart.P_CONTENT_TRANSFER_ENCODING);
            outStream.write(8, 0);
            encodeMessageId(bData, outStream);
            if (bData.userData != null) {
                outStream.write(8, 1);
                encodeUserData(bData, outStream);
            }
            if (bData.callbackNumber != null) {
                outStream.write(8, 14);
                encodeCallbackNumber(bData, outStream);
            }
            if (bData.userAckReq || bData.deliveryAckReq || bData.readAckReq || bData.reportReq) {
                outStream.write(8, 10);
                encodeReplyOption(bData, outStream);
            }
            if (bData.numberOfMessages != 0) {
                outStream.write(8, 11);
                encodeMsgCount(bData, outStream);
            }
            if (bData.validityPeriodRelativeSet) {
                outStream.write(8, 5);
                encodeValidityPeriodRel(bData, outStream);
            }
            if (bData.privacyIndicatorSet) {
                outStream.write(8, 9);
                encodePrivacyIndicator(bData, outStream);
            }
            if (bData.languageIndicatorSet) {
                outStream.write(8, 13);
                encodeLanguageIndicator(bData, outStream);
            }
            if (bData.displayModeSet) {
                outStream.write(8, 15);
                encodeDisplayMode(bData, outStream);
            }
            if (bData.priorityIndicatorSet) {
                outStream.write(8, 8);
                encodePriorityIndicator(bData, outStream);
            }
            if (bData.alertIndicatorSet) {
                outStream.write(8, 12);
                encodeMsgDeliveryAlert(bData, outStream);
            }
            if (bData.messageStatusSet) {
                outStream.write(8, 20);
                encodeMsgStatus(bData, outStream);
            }
            if (bData.serviceCategoryProgramResults != null) {
                outStream.write(8, 19);
                encodeScpResults(bData, outStream);
            }
            return outStream.toByteArray();
        } catch (AccessException ex) {
            Rlog.e(LOG_TAG, "BearerData encode failed: " + ex);
            return null;
        } catch (CodingException ex2) {
            Rlog.e(LOG_TAG, "BearerData encode failed: " + ex2);
            return null;
        }
    }

    private static boolean decodeMessageId(BearerData bData, BitwiseInputStream inStream) throws BitwiseInputStream.AccessException {
        boolean z = true;
        boolean decodeSuccess = false;
        int paramBits = inStream.read(8) * 8;
        if (paramBits >= 24) {
            paramBits -= 24;
            decodeSuccess = true;
            bData.messageType = inStream.read(4);
            bData.messageId = inStream.read(8) << 8;
            bData.messageId |= inStream.read(8);
            if (inStream.read(1) != 1) {
                z = false;
            }
            bData.hasUserDataHeader = z;
            inStream.skip(3);
        }
        if (!decodeSuccess || paramBits > 0) {
            Rlog.d(LOG_TAG, "MESSAGE_IDENTIFIER decode " + (decodeSuccess ? "succeeded" : "failed") + " (extra bits = " + paramBits + ")");
        }
        inStream.skip(paramBits);
        return decodeSuccess;
    }

    private static boolean decodeReserved(BearerData bData, BitwiseInputStream inStream, int subparamId) throws BitwiseInputStream.AccessException, CodingException {
        boolean decodeSuccess = false;
        int subparamLen = inStream.read(8);
        int paramBits = subparamLen * 8;
        if (paramBits <= inStream.available()) {
            decodeSuccess = true;
            inStream.skip(paramBits);
        }
        Rlog.d(LOG_TAG, "RESERVED bearer data subparameter " + subparamId + " decode " + (decodeSuccess ? "succeeded" : "failed") + " (param bits = " + paramBits + ")");
        if (decodeSuccess) {
            return decodeSuccess;
        }
        throw new CodingException("RESERVED bearer data subparameter " + subparamId + " had invalid SUBPARAM_LEN " + subparamLen);
    }

    private static boolean decodeUserData(BearerData bData, BitwiseInputStream inStream) throws BitwiseInputStream.AccessException {
        int paramBits = inStream.read(8) * 8;
        bData.userData = new UserData();
        bData.userData.msgEncoding = inStream.read(5);
        bData.userData.msgEncodingSet = true;
        bData.userData.msgType = 0;
        int consumedBits = 5;
        if (bData.userData.msgEncoding == 1 || bData.userData.msgEncoding == 10) {
            bData.userData.msgType = inStream.read(8);
            consumedBits = 5 + 8;
        }
        bData.userData.numFields = inStream.read(8);
        int dataBits = paramBits - (consumedBits + 8);
        bData.userData.payload = inStream.readByteArray(dataBits);
        return true;
    }

    private static String decodeUtf8(byte[] data, int offset, int numFields) throws CodingException {
        return decodeCharset(data, offset, numFields, 1, "UTF-8");
    }

    private static String decodeUtf16(byte[] data, int offset, int numFields) throws CodingException {
        numFields -= (offset + (offset % 2)) / 2;
        if (!SMSDispatcher.getCDMASmsReassembly()) {
            userLength = (numFields * 2) + offset;
            compChar = (char) ((data[userLength - 2] & 255) << 8);
            compChar = (char) (compChar | ((char) (data[userLength - 1] & 255)));
            if ((compChar == '?' || compChar == '?') && userLength == 140) {
                Rlog.d(LOG_TAG, "emoji is broken in the end of segment");
                mlastByte = new byte[2];
                mlastByte[0] = data[userLength - 2];
                mlastByte[1] = data[userLength - 1];
                mBodyOffset = offset;
                mIsfourBytesUnicode = true;
            } else {
                mIsfourBytesUnicode = false;
            }
        }
        return decodeCharset(data, offset, numFields, 2, "utf-16be");
    }

    private static String decodeCharset(byte[] data, int offset, int numFields, int width, String charset) throws CodingException {
        if (numFields < 0 || (numFields * width) + offset > data.length) {
            int maxNumFields = ((data.length - offset) - (offset % width)) / width;
            if (maxNumFields < 0) {
                throw new CodingException(charset + " decode failed: offset out of range");
            }
            Rlog.e(LOG_TAG, charset + " decode error: offset = " + offset + " numFields = " + numFields + " data.length = " + data.length + " maxNumFields = " + maxNumFields);
            numFields = maxNumFields;
        }
        try {
            return new String(data, offset, numFields * width, charset);
        } catch (UnsupportedEncodingException ex) {
            throw new CodingException(charset + " decode failed: " + ex);
        }
    }

    private static String decode7bitAscii(byte[] data, int offset, int numFields) throws CodingException {
        offset *= 8;
        try {
            StringBuffer strBuf = new StringBuffer(numFields);
            BitwiseInputStream inStream = new BitwiseInputStream(data);
            int wantedBits = (offset * 8) + (numFields * 7);
            if ("CTC".equals(SystemProperties.get("ro.csc.sales_code"))) {
                wantedBits = offset + (numFields * 7);
            }
            if (inStream.available() < wantedBits) {
                throw new CodingException("insufficient data (wanted " + wantedBits + " bits, but only have " + inStream.available() + ")");
            }
            inStream.skip(offset);
            for (int i = 0; i < numFields; i++) {
                int charCode = inStream.read(7);
                if (charCode >= 32 && charCode <= UserData.ASCII_MAP_MAX_INDEX) {
                    strBuf.append(UserData.ASCII_MAP[charCode - 32]);
                } else if (charCode == 10) {
                    strBuf.append('\n');
                } else if (charCode == 13) {
                    strBuf.append('\r');
                } else {
                    strBuf.append(' ');
                }
            }
            return strBuf.toString();
        } catch (BitwiseInputStream.AccessException ex) {
            throw new CodingException("7bit ASCII decode failed: " + ex);
        }
    }

    private static String decode7bitGsm(byte[] data, int offset, int numFields) throws CodingException {
        int offsetBits = offset * 8;
        int offsetSeptets = (offsetBits + 6) / 7;
        String result = GsmAlphabet.gsm7BitPackedToString(data, offset, numFields - offsetSeptets, (offsetSeptets * 7) - offsetBits, 0, 0);
        if (result != null) {
            return result;
        }
        throw new CodingException("7bit GSM decoding failed");
    }

    private static String decodeLatin(byte[] data, int offset, int numFields) throws CodingException {
        return decodeCharset(data, offset, numFields, 1, "ISO-8859-1");
    }

    private static String decodeShiftJis(byte[] data, int offset, int numFields) throws CodingException {
        return decodeCharset(data, offset, numFields, 1, "Shift_JIS");
    }

    private static String decodeUtf16_KDDI(byte[] data, int offset, int numFields) throws CodingException {
        try {
            return new String(data, offset, numFields - offset, "utf-16be");
        } catch (UnsupportedEncodingException ex) {
            throw new CodingException("UTF-16 decode failed: " + ex);
        }
    }

    private static void decodeUserDataPayload(UserData userData, boolean hasUserDataHeader) throws CodingException {
        int offset = 0;
        if (hasUserDataHeader) {
            int udhLen = userData.payload[0] & 255;
            offset = 0 + (udhLen + 1);
            byte[] headerData = new byte[udhLen];
            System.arraycopy(userData.payload, 1, headerData, 0, udhLen);
            userData.userDataHeader = SmsHeader.fromByteArray(headerData);
        }
        switch (userData.msgEncoding) {
            case 0:
                int copyLen;
                boolean decodingtypeUTF8 = Resources.getSystem().getBoolean(17956941);
                byte[] payload = new byte[userData.numFields];
                if (userData.numFields < userData.payload.length) {
                    copyLen = userData.numFields;
                } else {
                    copyLen = userData.payload.length;
                }
                System.arraycopy(userData.payload, 0, payload, 0, copyLen);
                userData.payload = payload;
                if (decodingtypeUTF8) {
                    userData.payloadStr = decodeUtf8(userData.payload, offset, userData.numFields);
                    return;
                } else {
                    userData.payloadStr = decodeLatin(userData.payload, offset, userData.numFields);
                    return;
                }
            case 2:
            case 3:
                userData.payloadStr = decode7bitAscii(userData.payload, offset, userData.numFields);
                return;
            case 4:
                userData.payloadStr = decodeUtf16(userData.payload, offset, userData.numFields);
                return;
            case 5:
                userData.payloadStr = decodeShiftJis(userData.payload, offset, userData.numFields);
                return;
            case 8:
                userData.payloadStr = decodeLatin(userData.payload, offset, userData.numFields);
                return;
            case 9:
                userData.payloadStr = decode7bitGsm(userData.payload, offset, userData.numFields);
                return;
            case 10:
            case 16:
                return;
            default:
                throw new CodingException("unsupported user data encoding (" + userData.msgEncoding + ")");
        }
    }

    private static void decodeIs91VoicemailStatus(BearerData bData) throws BitwiseInputStream.AccessException, CodingException {
        BitwiseInputStream inStream = new BitwiseInputStream(bData.userData.payload);
        int dataLen = inStream.available() / 6;
        int numFields = bData.userData.numFields;
        if (dataLen > 14 || dataLen < 3 || dataLen < numFields) {
            throw new CodingException("IS-91 voicemail status decoding failed");
        }
        try {
            StringBuffer strbuf = new StringBuffer(dataLen);
            while (inStream.available() >= 6) {
                strbuf.append(UserData.ASCII_MAP[inStream.read(6)]);
            }
            String data = strbuf.toString();
            bData.numberOfMessages = Integer.parseInt(data.substring(0, 2));
            char prioCode = data.charAt(2);
            if (prioCode == ' ') {
                bData.priority = 0;
            } else if (prioCode == '!') {
                bData.priority = 2;
            } else {
                throw new CodingException("IS-91 voicemail status decoding failed: illegal priority setting (" + prioCode + ")");
            }
            bData.priorityIndicatorSet = true;
            bData.userData.payloadStr = data.substring(3, numFields - 3);
        } catch (NumberFormatException ex) {
            throw new CodingException("IS-91 voicemail status decoding failed: " + ex);
        } catch (IndexOutOfBoundsException ex2) {
            throw new CodingException("IS-91 voicemail status decoding failed: " + ex2);
        }
    }

    private static void decodeIs91ShortMessage(BearerData bData) throws BitwiseInputStream.AccessException, CodingException {
        BitwiseInputStream inStream = new BitwiseInputStream(bData.userData.payload);
        int dataLen = inStream.available() / 6;
        int numFields = bData.userData.numFields;
        if (numFields > 14 || dataLen < numFields) {
            throw new CodingException("IS-91 short message decoding failed");
        }
        StringBuffer strbuf = new StringBuffer(dataLen);
        for (int i = 0; i < numFields; i++) {
            strbuf.append(UserData.ASCII_MAP[inStream.read(6)]);
        }
        bData.userData.payloadStr = strbuf.toString();
    }

    private static void decodeIs91Cli(BearerData bData) throws CodingException {
        int dataLen = new BitwiseInputStream(bData.userData.payload).available() / 4;
        int numFields = bData.userData.numFields;
        if (dataLen > 14 || dataLen < 3 || dataLen < numFields) {
            throw new CodingException("IS-91 voicemail status decoding failed");
        }
        CdmaSmsAddress addr = new CdmaSmsAddress();
        addr.digitMode = 0;
        addr.origBytes = bData.userData.payload;
        addr.numberOfDigits = (byte) numFields;
        decodeSmsAddress(addr);
        bData.callbackNumber = addr;
    }

    private static void decodeIs91(BearerData bData) throws BitwiseInputStream.AccessException, CodingException {
        switch (bData.userData.msgType) {
            case 130:
                decodeIs91VoicemailStatus(bData);
                return;
            case 131:
            case 133:
                decodeIs91ShortMessage(bData);
                return;
            case 132:
                decodeIs91Cli(bData);
                return;
            default:
                throw new CodingException("unsupported IS-91 message type (" + bData.userData.msgType + ")");
        }
    }

    private static boolean decodeReplyOption(BearerData bData, BitwiseInputStream inStream) throws BitwiseInputStream.AccessException {
        boolean z = true;
        boolean decodeSuccess = false;
        int paramBits = inStream.read(8) * 8;
        if (paramBits >= 8) {
            boolean z2;
            paramBits -= 8;
            decodeSuccess = true;
            bData.userAckReq = inStream.read(1) == 1;
            if (inStream.read(1) == 1) {
                z2 = true;
            } else {
                z2 = false;
            }
            bData.deliveryAckReq = z2;
            if (inStream.read(1) == 1) {
                z2 = true;
            } else {
                z2 = false;
            }
            bData.readAckReq = z2;
            if (inStream.read(1) != 1) {
                z = false;
            }
            bData.reportReq = z;
            inStream.skip(4);
        }
        if (!decodeSuccess || paramBits > 0) {
            Rlog.d(LOG_TAG, "REPLY_OPTION decode " + (decodeSuccess ? "succeeded" : "failed") + " (extra bits = " + paramBits + ")");
        }
        inStream.skip(paramBits);
        return decodeSuccess;
    }

    private static boolean decodeMsgCount(BearerData bData, BitwiseInputStream inStream) throws BitwiseInputStream.AccessException {
        boolean decodeSuccess = false;
        int paramBits = inStream.read(8) * 8;
        if (paramBits >= 8) {
            paramBits -= 8;
            decodeSuccess = true;
            bData.numberOfMessages = IccUtils.cdmaBcdByteToInt((byte) inStream.read(8));
        }
        if (!decodeSuccess || paramBits > 0) {
            Rlog.d(LOG_TAG, "NUMBER_OF_MESSAGES decode " + (decodeSuccess ? "succeeded" : "failed") + " (extra bits = " + paramBits + ")");
        }
        inStream.skip(paramBits);
        return decodeSuccess;
    }

    private static boolean decodeDepositIndex(BearerData bData, BitwiseInputStream inStream) throws BitwiseInputStream.AccessException {
        boolean decodeSuccess = false;
        int paramBits = inStream.read(8) * 8;
        if (paramBits >= 16) {
            paramBits -= 16;
            decodeSuccess = true;
            bData.depositIndex = (inStream.read(8) << 8) | inStream.read(8);
        }
        if (!decodeSuccess || paramBits > 0) {
            Rlog.d(LOG_TAG, "MESSAGE_DEPOSIT_INDEX decode " + (decodeSuccess ? "succeeded" : "failed") + " (extra bits = " + paramBits + ")");
        }
        inStream.skip(paramBits);
        return decodeSuccess;
    }

    private static String decodeDtmfSmsAddress(byte[] rawData, int numFields) throws CodingException {
        StringBuffer strBuf = new StringBuffer(numFields);
        for (int i = 0; i < numFields; i++) {
            int val = (rawData[i / 2] >>> (4 - ((i % 2) * 4))) & 15;
            if (val >= 1 && val <= 9) {
                strBuf.append(Integer.toString(val, 10));
            } else if (val == 10) {
                strBuf.append('0');
            } else if (val == 11) {
                strBuf.append('*');
            } else if (val == 12) {
                strBuf.append('#');
            } else {
                throw new CodingException("invalid SMS address DTMF code (" + val + ")");
            }
        }
        return strBuf.toString();
    }

    private static void decodeSmsAddress(CdmaSmsAddress addr) throws CodingException {
        if (addr.digitMode == 1) {
            try {
                addr.address = new String(addr.origBytes, 0, addr.origBytes.length, "US-ASCII");
                return;
            } catch (UnsupportedEncodingException e) {
                throw new CodingException("invalid SMS address ASCII code");
            }
        }
        addr.address = decodeDtmfSmsAddress(addr.origBytes, addr.numberOfDigits);
    }

    private static boolean decodeCallbackNumber(BearerData bData, BitwiseInputStream inStream) throws BitwiseInputStream.AccessException, CodingException {
        int paramBits = inStream.read(8) * 8;
        if (paramBits < 8) {
            inStream.skip(paramBits);
            return false;
        }
        CdmaSmsAddress addr = new CdmaSmsAddress();
        addr.digitMode = inStream.read(1);
        byte fieldBits = (byte) 4;
        byte consumedBits = (byte) 1;
        if (addr.digitMode == 1) {
            addr.ton = inStream.read(3);
            addr.numberPlan = inStream.read(4);
            fieldBits = SUBPARAM_PRIORITY_INDICATOR;
            consumedBits = (byte) 8;
        }
        addr.numberOfDigits = inStream.read(8);
        int remainingBits = paramBits - ((byte) (consumedBits + 8));
        int dataBits = addr.numberOfDigits * fieldBits;
        int paddingBits = remainingBits - dataBits;
        if (remainingBits < dataBits) {
            throw new CodingException("CALLBACK_NUMBER subparam encoding size error (remainingBits + " + remainingBits + ", dataBits + " + dataBits + ", paddingBits + " + paddingBits + ")");
        }
        addr.origBytes = inStream.readByteArray(dataBits);
        inStream.skip(paddingBits);
        decodeSmsAddress(addr);
        bData.callbackNumber = addr;
        return true;
    }

    private static boolean decodeMsgStatus(BearerData bData, BitwiseInputStream inStream) throws BitwiseInputStream.AccessException {
        boolean decodeSuccess = false;
        int paramBits = inStream.read(8) * 8;
        if (paramBits >= 8) {
            paramBits -= 8;
            decodeSuccess = true;
            bData.errorClass = inStream.read(2);
            bData.messageStatus = inStream.read(6);
        }
        if (!decodeSuccess || paramBits > 0) {
            Rlog.d(LOG_TAG, "MESSAGE_STATUS decode " + (decodeSuccess ? "succeeded" : "failed") + " (extra bits = " + paramBits + ")");
        }
        inStream.skip(paramBits);
        bData.messageStatusSet = decodeSuccess;
        return decodeSuccess;
    }

    private static boolean decodeMsgCenterTimeStamp(BearerData bData, BitwiseInputStream inStream) throws BitwiseInputStream.AccessException {
        boolean decodeSuccess = false;
        int paramBits = inStream.read(8) * 8;
        if (paramBits >= 48) {
            paramBits -= 48;
            decodeSuccess = true;
            bData.msgCenterTimeStamp = TimeStamp.fromByteArray(inStream.readByteArray(48));
        }
        if (!decodeSuccess || paramBits > 0) {
            Rlog.d(LOG_TAG, "MESSAGE_CENTER_TIME_STAMP decode " + (decodeSuccess ? "succeeded" : "failed") + " (extra bits = " + paramBits + ")");
        }
        inStream.skip(paramBits);
        return decodeSuccess;
    }

    private static boolean decodeValidityAbs(BearerData bData, BitwiseInputStream inStream) throws BitwiseInputStream.AccessException {
        boolean decodeSuccess = false;
        int paramBits = inStream.read(8) * 8;
        if (paramBits >= 48) {
            paramBits -= 48;
            decodeSuccess = true;
            bData.validityPeriodAbsolute = TimeStamp.fromByteArray(inStream.readByteArray(48));
        }
        if (!decodeSuccess || paramBits > 0) {
            Rlog.d(LOG_TAG, "VALIDITY_PERIOD_ABSOLUTE decode " + (decodeSuccess ? "succeeded" : "failed") + " (extra bits = " + paramBits + ")");
        }
        inStream.skip(paramBits);
        return decodeSuccess;
    }

    private static boolean decodeDeferredDeliveryAbs(BearerData bData, BitwiseInputStream inStream) throws BitwiseInputStream.AccessException {
        boolean decodeSuccess = false;
        int paramBits = inStream.read(8) * 8;
        if (paramBits >= 48) {
            paramBits -= 48;
            decodeSuccess = true;
            bData.deferredDeliveryTimeAbsolute = TimeStamp.fromByteArray(inStream.readByteArray(48));
        }
        if (!decodeSuccess || paramBits > 0) {
            Rlog.d(LOG_TAG, "DEFERRED_DELIVERY_TIME_ABSOLUTE decode " + (decodeSuccess ? "succeeded" : "failed") + " (extra bits = " + paramBits + ")");
        }
        inStream.skip(paramBits);
        return decodeSuccess;
    }

    private static boolean decodeValidityRel(BearerData bData, BitwiseInputStream inStream) throws BitwiseInputStream.AccessException {
        boolean decodeSuccess = false;
        int paramBits = inStream.read(8) * 8;
        if (paramBits >= 8) {
            paramBits -= 8;
            decodeSuccess = true;
            bData.deferredDeliveryTimeRelative = inStream.read(8);
        }
        if (!decodeSuccess || paramBits > 0) {
            Rlog.d(LOG_TAG, "VALIDITY_PERIOD_RELATIVE decode " + (decodeSuccess ? "succeeded" : "failed") + " (extra bits = " + paramBits + ")");
        }
        inStream.skip(paramBits);
        bData.deferredDeliveryTimeRelativeSet = decodeSuccess;
        return decodeSuccess;
    }

    private static boolean decodeDeferredDeliveryRel(BearerData bData, BitwiseInputStream inStream) throws BitwiseInputStream.AccessException {
        boolean decodeSuccess = false;
        int paramBits = inStream.read(8) * 8;
        if (paramBits >= 8) {
            paramBits -= 8;
            decodeSuccess = true;
            bData.validityPeriodRelative = inStream.read(8);
        }
        if (!decodeSuccess || paramBits > 0) {
            Rlog.d(LOG_TAG, "DEFERRED_DELIVERY_TIME_RELATIVE decode " + (decodeSuccess ? "succeeded" : "failed") + " (extra bits = " + paramBits + ")");
        }
        inStream.skip(paramBits);
        bData.validityPeriodRelativeSet = decodeSuccess;
        return decodeSuccess;
    }

    private static boolean decodePrivacyIndicator(BearerData bData, BitwiseInputStream inStream) throws BitwiseInputStream.AccessException {
        boolean decodeSuccess = false;
        int paramBits = inStream.read(8) * 8;
        if (paramBits >= 8) {
            paramBits -= 8;
            decodeSuccess = true;
            bData.privacy = inStream.read(2);
            inStream.skip(6);
        }
        if (!decodeSuccess || paramBits > 0) {
            Rlog.d(LOG_TAG, "PRIVACY_INDICATOR decode " + (decodeSuccess ? "succeeded" : "failed") + " (extra bits = " + paramBits + ")");
        }
        inStream.skip(paramBits);
        bData.privacyIndicatorSet = decodeSuccess;
        return decodeSuccess;
    }

    private static boolean decodeLanguageIndicator(BearerData bData, BitwiseInputStream inStream) throws BitwiseInputStream.AccessException {
        boolean decodeSuccess = false;
        int paramBits = inStream.read(8) * 8;
        if (paramBits >= 8) {
            paramBits -= 8;
            decodeSuccess = true;
            language = inStream.read(8);
        }
        if (!decodeSuccess || paramBits > 0) {
            Rlog.d(LOG_TAG, "LANGUAGE_INDICATOR decode " + (decodeSuccess ? "succeeded" : "failed") + " (extra bits = " + paramBits + ")");
        }
        inStream.skip(paramBits);
        bData.languageIndicatorSet = decodeSuccess;
        return decodeSuccess;
    }

    private static boolean decodeDisplayMode(BearerData bData, BitwiseInputStream inStream) throws BitwiseInputStream.AccessException {
        boolean decodeSuccess = false;
        int paramBits = inStream.read(8) * 8;
        if (paramBits >= 8) {
            paramBits -= 8;
            decodeSuccess = true;
            bData.displayMode = inStream.read(2);
            inStream.skip(6);
        }
        if (!decodeSuccess || paramBits > 0) {
            Rlog.d(LOG_TAG, "DISPLAY_MODE decode " + (decodeSuccess ? "succeeded" : "failed") + " (extra bits = " + paramBits + ")");
        }
        inStream.skip(paramBits);
        bData.displayModeSet = decodeSuccess;
        return decodeSuccess;
    }

    private static boolean decodePriorityIndicator(BearerData bData, BitwiseInputStream inStream) throws BitwiseInputStream.AccessException {
        boolean decodeSuccess = false;
        int paramBits = inStream.read(8) * 8;
        if (paramBits >= 8) {
            paramBits -= 8;
            decodeSuccess = true;
            bData.priority = inStream.read(2);
            inStream.skip(6);
        }
        if (!decodeSuccess || paramBits > 0) {
            Rlog.d(LOG_TAG, "PRIORITY_INDICATOR decode " + (decodeSuccess ? "succeeded" : "failed") + " (extra bits = " + paramBits + ")");
        }
        inStream.skip(paramBits);
        bData.priorityIndicatorSet = decodeSuccess;
        return decodeSuccess;
    }

    private static boolean decodeMsgDeliveryAlert(BearerData bData, BitwiseInputStream inStream) throws BitwiseInputStream.AccessException {
        boolean decodeSuccess = false;
        int paramBits = inStream.read(8) * 8;
        if (paramBits >= 8) {
            paramBits -= 8;
            decodeSuccess = true;
            bData.alert = inStream.read(2);
            inStream.skip(6);
        }
        if (!decodeSuccess || paramBits > 0) {
            Rlog.d(LOG_TAG, "ALERT_ON_MESSAGE_DELIVERY decode " + (decodeSuccess ? "succeeded" : "failed") + " (extra bits = " + paramBits + ")");
        }
        inStream.skip(paramBits);
        bData.alertIndicatorSet = decodeSuccess;
        return decodeSuccess;
    }

    private static boolean decodeUserResponseCode(BearerData bData, BitwiseInputStream inStream) throws BitwiseInputStream.AccessException {
        boolean decodeSuccess = false;
        int paramBits = inStream.read(8) * 8;
        if (paramBits >= 8) {
            paramBits -= 8;
            decodeSuccess = true;
            bData.userResponseCode = inStream.read(8);
        }
        if (!decodeSuccess || paramBits > 0) {
            Rlog.d(LOG_TAG, "USER_RESPONSE_CODE decode " + (decodeSuccess ? "succeeded" : "failed") + " (extra bits = " + paramBits + ")");
        }
        inStream.skip(paramBits);
        bData.userResponseCodeSet = decodeSuccess;
        return decodeSuccess;
    }

    private static boolean decodeServiceCategoryProgramData(BearerData bData, BitwiseInputStream inStream) throws BitwiseInputStream.AccessException, CodingException {
        if (inStream.available() < 13) {
            throw new CodingException("SERVICE_CATEGORY_PROGRAM_DATA decode failed: only " + inStream.available() + " bits available");
        }
        int paramBits = inStream.read(8) * 8;
        int msgEncoding = inStream.read(5);
        paramBits -= 5;
        if (inStream.available() < paramBits) {
            throw new CodingException("SERVICE_CATEGORY_PROGRAM_DATA decode failed: only " + inStream.available() + " bits available (" + paramBits + " bits expected)");
        }
        ArrayList<CdmaSmsCbProgramData> programDataList = new ArrayList();
        boolean decodeSuccess = false;
        while (paramBits >= 48) {
            int operation = inStream.read(4);
            int category = (inStream.read(8) << 8) | inStream.read(8);
            int language = inStream.read(8);
            int maxMessages = inStream.read(8);
            int alertOption = inStream.read(4);
            int numFields = inStream.read(8);
            paramBits -= 48;
            int textBits = getBitsForNumFields(msgEncoding, numFields);
            if (paramBits < textBits) {
                throw new CodingException("category name is " + textBits + " bits in length," + " but there are only " + paramBits + " bits available");
            }
            UserData userData = new UserData();
            userData.msgEncoding = msgEncoding;
            userData.msgEncodingSet = true;
            userData.numFields = numFields;
            userData.payload = inStream.readByteArray(textBits);
            paramBits -= textBits;
            decodeUserDataPayload(userData, false);
            programDataList.add(new CdmaSmsCbProgramData(operation, category, language, maxMessages, alertOption, userData.payloadStr));
            decodeSuccess = true;
        }
        if (!decodeSuccess || paramBits > 0) {
            Rlog.d(LOG_TAG, "SERVICE_CATEGORY_PROGRAM_DATA decode " + (decodeSuccess ? "succeeded" : "failed") + " (extra bits = " + paramBits + ')');
        }
        inStream.skip(paramBits);
        bData.serviceCategoryProgramData = programDataList;
        return decodeSuccess;
    }

    private static int serviceCategoryToCmasMessageClass(int serviceCategory) {
        switch (serviceCategory) {
            case 4096:
                return 0;
            case 4097:
                return 1;
            case 4098:
                return 2;
            case 4099:
                return 3;
            case 4100:
                return 4;
            default:
                return -1;
        }
    }

    private static int getBitsForNumFields(int msgEncoding, int numFields) throws CodingException {
        switch (msgEncoding) {
            case 0:
            case 5:
            case 6:
            case 7:
            case 8:
                return numFields * 8;
            case 2:
            case 3:
            case 9:
                return numFields * 7;
            case 4:
                return numFields * 16;
            default:
                throw new CodingException("unsupported message encoding (" + msgEncoding + ')');
        }
    }

    private static void decodeCmasUserData(BearerData bData, int serviceCategory) throws BitwiseInputStream.AccessException, CodingException {
        BitwiseInputStream bitwiseInputStream = new BitwiseInputStream(bData.userData.payload);
        if (bitwiseInputStream.available() < 8) {
            throw new CodingException("emergency CB with no CMAE_protocol_version");
        }
        int protocolVersion = bitwiseInputStream.read(8);
        if (protocolVersion != 0) {
            throw new CodingException("unsupported CMAE_protocol_version " + protocolVersion);
        }
        int messageClass = serviceCategoryToCmasMessageClass(serviceCategory);
        int category = -1;
        int responseType = -1;
        int severity = -1;
        int urgency = -1;
        int certainty = -1;
        int messageID = 0;
        int alertHandling = 0;
        TimeStamp msgExpires = new TimeStamp("UTC");
        int recordTypeAll = 0;
        while (bitwiseInputStream.available() >= 16) {
            int recordType = bitwiseInputStream.read(8);
            int recordLen = bitwiseInputStream.read(8);
            switch (recordType) {
                case 0:
                    int numFields;
                    UserData alertUserData = new UserData();
                    alertUserData.msgEncoding = bitwiseInputStream.read(5);
                    alertUserData.msgEncodingSet = true;
                    alertUserData.msgType = 0;
                    Rlog.d(LOG_TAG, "CMAS RecordType(0) len : " + recordLen);
                    switch (alertUserData.msgEncoding) {
                        case 0:
                        case 8:
                            numFields = recordLen - 1;
                            break;
                        case 2:
                        case 3:
                        case 9:
                            numFields = ((recordLen * 8) - 5) / 7;
                            break;
                        case 4:
                            numFields = (recordLen - 1) / 2;
                            break;
                        default:
                            throw new CodingException("unsupported message encoding");
                    }
                    alertUserData.numFields = numFields;
                    alertUserData.payload = bitwiseInputStream.readByteArray((recordLen * 8) - 5);
                    decodeUserDataPayload(alertUserData, false);
                    bData.userData = alertUserData;
                    recordTypeAll |= 1;
                    break;
                case 1:
                    Rlog.d(LOG_TAG, "CMAS RecordType(1) len : " + recordLen);
                    category = bitwiseInputStream.read(8);
                    Rlog.d(LOG_TAG, "CMAS Category : " + category);
                    responseType = bitwiseInputStream.read(8);
                    Rlog.d(LOG_TAG, "CMAS RespType : " + responseType);
                    severity = bitwiseInputStream.read(4);
                    Rlog.d(LOG_TAG, "CMAS Severity : " + severity);
                    urgency = bitwiseInputStream.read(4);
                    Rlog.d(LOG_TAG, "CMAS Urgency : " + urgency);
                    certainty = bitwiseInputStream.read(4);
                    Rlog.d(LOG_TAG, "CMAS Certainty : " + certainty);
                    bitwiseInputStream.skip((recordLen * 8) - 28);
                    recordTypeAll |= 2;
                    break;
                case 2:
                    Rlog.d(LOG_TAG, "CMAS RecordType(2) len : " + recordLen);
                    recordLen *= 8;
                    messageID = (bitwiseInputStream.read(8) << 8) | bitwiseInputStream.read(8);
                    Rlog.d(LOG_TAG, "CMAS Msg Id : " + messageID);
                    recordLen -= 16;
                    alertHandling = bitwiseInputStream.read(8);
                    Rlog.d(LOG_TAG, "CMAS Alert Handling : " + alertHandling);
                    recordLen -= 8;
                    int nExpiryYear = bitwiseInputStream.read(8);
                    if (nExpiryYear >= 96) {
                        nExpiryYear += 1900;
                    } else {
                        nExpiryYear += 2000;
                    }
                    int nExpiryMonth = bitwiseInputStream.read(8) - 1;
                    int nExpiryDay = bitwiseInputStream.read(8);
                    int nExpiryHr = bitwiseInputStream.read(8);
                    int nExpiryMin = bitwiseInputStream.read(8);
                    int nExpirySec = bitwiseInputStream.read(8);
                    Rlog.d(LOG_TAG, "CMAS Expires yy : " + nExpiryYear + " mm : " + nExpiryMonth + " dd : " + nExpiryDay + " h : " + nExpiryHr + " m : " + nExpiryMin + " s : " + nExpirySec);
                    msgExpires.set(nExpirySec, nExpiryMin, nExpiryHr, nExpiryDay, nExpiryMonth, nExpiryYear);
                    recordLen -= 48;
                    language = bitwiseInputStream.read(8);
                    Rlog.d(LOG_TAG, "CMAS Language : " + language);
                    recordLen -= 8;
                    recordTypeAll |= 4;
                    break;
                case 3:
                    recordTypeAll |= 8;
                    break;
                default:
                    Rlog.w(LOG_TAG, "skipping unsupported CMAS record type " + recordType);
                    bitwiseInputStream.skip(recordLen * 8);
                    break;
            }
        }
        Rlog.d(LOG_TAG, "CMAS Message Body = '" + bData.userData.payloadStr + "'");
        Rlog.d(LOG_TAG, "isRecordType(0) : " + ((recordTypeAll & 1) == 1) + "\nisRecordType(1) : " + ((recordTypeAll & 2) == 2) + "\nisRecordType(2) : " + ((recordTypeAll & 4) == 4));
        if (recordTypeAll == 3) {
            bData.cmasWarningInfo = new SmsCbCmasInfo(messageClass, category, responseType, severity, urgency, certainty, recordTypeAll);
        } else if (recordTypeAll == 5) {
            bData.cmasWarningInfo = new SmsCbCmasInfo(messageClass, messageID, alertHandling, msgExpires.toMillis(true), language, recordTypeAll);
        } else if (recordTypeAll == 7) {
            bData.cmasWarningInfo = new SmsCbCmasInfo(messageClass, category, responseType, severity, urgency, certainty, messageID, alertHandling, msgExpires.toMillis(true), language, recordTypeAll);
        } else {
            bData.cmasWarningInfo = new SmsCbCmasInfo(messageClass, category, responseType, severity, urgency, certainty);
        }
    }

    public static BearerData decode(byte[] smsData) {
        return decode(smsData, 0);
    }

    private static boolean isCmasAlertCategory(int category) {
        return category >= 4096 && category <= SmsEnvelope.SERVICE_CATEGORY_CMAS_LAST_RESERVED_VALUE;
    }

    public static BearerData decode(byte[] smsData, int serviceCategory) {
        try {
            BitwiseInputStream inStream = new BitwiseInputStream(smsData);
            BearerData bData = new BearerData();
            int foundSubparamMask = 0;
            while (inStream.available() > 0) {
                int subparamId = inStream.read(8);
                int subparamIdBit = 1 << subparamId;
                if ((foundSubparamMask & subparamIdBit) == 0 || subparamId < 0 || subparamId > 23) {
                    boolean decodeSuccess;
                    switch (subparamId) {
                        case 0:
                            decodeSuccess = decodeMessageId(bData, inStream);
                            break;
                        case 1:
                            decodeSuccess = decodeUserData(bData, inStream);
                            break;
                        case 2:
                            decodeSuccess = decodeUserResponseCode(bData, inStream);
                            break;
                        case 3:
                            decodeSuccess = decodeMsgCenterTimeStamp(bData, inStream);
                            break;
                        case 4:
                            decodeSuccess = decodeValidityAbs(bData, inStream);
                            break;
                        case 5:
                            decodeSuccess = decodeValidityRel(bData, inStream);
                            break;
                        case 6:
                            decodeSuccess = decodeDeferredDeliveryAbs(bData, inStream);
                            break;
                        case 7:
                            decodeSuccess = decodeDeferredDeliveryRel(bData, inStream);
                            break;
                        case 8:
                            decodeSuccess = decodePriorityIndicator(bData, inStream);
                            break;
                        case 9:
                            decodeSuccess = decodePrivacyIndicator(bData, inStream);
                            break;
                        case 10:
                            decodeSuccess = decodeReplyOption(bData, inStream);
                            break;
                        case 11:
                            decodeSuccess = decodeMsgCount(bData, inStream);
                            break;
                        case 12:
                            decodeSuccess = decodeMsgDeliveryAlert(bData, inStream);
                            break;
                        case 13:
                            decodeSuccess = decodeLanguageIndicator(bData, inStream);
                            break;
                        case 14:
                            decodeSuccess = decodeCallbackNumber(bData, inStream);
                            break;
                        case 15:
                            decodeSuccess = decodeDisplayMode(bData, inStream);
                            break;
                        case 17:
                            decodeSuccess = decodeDepositIndex(bData, inStream);
                            break;
                        case 18:
                            decodeSuccess = decodeServiceCategoryProgramData(bData, inStream);
                            break;
                        case 20:
                            decodeSuccess = decodeMsgStatus(bData, inStream);
                            break;
                        case 128:
                            decodeSuccess = decodeLguCbsImage(bData, inStream);
                            break;
                        case 129:
                            decodeSuccess = decodeLguCbsSession(bData, inStream);
                            break;
                        default:
                            decodeSuccess = decodeReserved(bData, inStream, subparamId);
                            break;
                    }
                    if (decodeSuccess && subparamId >= 0 && subparamId <= 23) {
                        foundSubparamMask |= subparamIdBit;
                    }
                } else {
                    throw new CodingException("illegal duplicate subparameter (" + subparamId + ")");
                }
            }
            if ((foundSubparamMask & 1) == 0) {
                throw new CodingException("missing MESSAGE_IDENTIFIER subparam");
            } else if (bData.userData == null) {
                return bData;
            } else {
                if (isCmasAlertCategory(serviceCategory)) {
                    decodeCmasUserData(bData, serviceCategory);
                    return bData;
                } else if (bData.userData.msgEncoding == 1) {
                    if (((foundSubparamMask ^ 1) ^ 2) != 0) {
                        Rlog.e(LOG_TAG, "IS-91 must occur without extra subparams (" + foundSubparamMask + ")");
                    }
                    decodeIs91(bData);
                    return bData;
                } else {
                    decodeUserDataPayload(bData.userData, bData.hasUserDataHeader);
                    if (!SMSDispatcher.getCDMASmsReassembly()) {
                        return bData;
                    }
                    extractPagination(bData.userData.payloadStr, bData.userData);
                    return bData;
                }
            }
        } catch (BitwiseInputStream.AccessException ex) {
            Rlog.e(LOG_TAG, "BearerData decode failed: " + ex);
        } catch (CodingException ex2) {
            Rlog.e(LOG_TAG, "BearerData decode failed: " + ex2);
        }
        return null;
    }

    private static String decodeKSC5601(byte[] data, int offset, int numFields) throws CodingException {
        try {
            return new String(data, offset, numFields - ((offset + (offset % 2)) / 2), CharacterSets.MIMENAME_EUC_KR);
        } catch (UnsupportedEncodingException ex) {
            throw new CodingException("EUC-KR decode failed: " + ex);
        }
    }

    private static void encodeMsgCenterTimeStamp(BearerData bData, BitwiseOutputStream outStream) throws AccessException {
        String date = bData.msgDeliveryTime;
        Rlog.d(LOG_TAG, " delivery date " + date);
        int year = Integer.parseInt(date.substring(0, 2));
        int month = Integer.parseInt(date.substring(2, 4));
        int day = Integer.parseInt(date.substring(4, 6));
        int hour = Integer.parseInt(date.substring(6, 8));
        int minute = Integer.parseInt(date.substring(8, 10));
        int second = Integer.parseInt(date.substring(10, 12));
        Rlog.d(LOG_TAG, "year " + year + " month " + month + " day =" + day);
        Rlog.d(LOG_TAG, " hour =" + hour + " minute " + minute + " second " + second);
        byte yearBcd = IccUtils.cdmaIntToBcdByte(year);
        byte monthBcd = IccUtils.cdmaIntToBcdByte(month);
        byte dayBcd = IccUtils.cdmaIntToBcdByte(day);
        byte hourBcd = IccUtils.cdmaIntToBcdByte(hour);
        byte minuteBcd = IccUtils.cdmaIntToBcdByte(minute);
        byte secondBcd = IccUtils.cdmaIntToBcdByte(second);
        Rlog.d(LOG_TAG, "year " + yearBcd + " month " + monthBcd + " day =" + dayBcd);
        Rlog.d(LOG_TAG, " hour =" + hourBcd + " minute " + minuteBcd + " second " + secondBcd);
        outStream.write(8, 6);
        outStream.write(8, yearBcd);
        outStream.write(8, monthBcd);
        outStream.write(8, dayBcd);
        outStream.write(8, hourBcd);
        outStream.write(8, minuteBcd);
        outStream.write(8, secondBcd);
    }

    public static TextEncodingDetails calcTextEncodingDetails(CharSequence msg, boolean force7BitEncoding, boolean isEms) {
        int septets = countAsciiSeptets(msg, force7BitEncoding);
        TextEncodingDetails ted;
        if (septets == -1) {
            if (septets != -1) {
            }
            ted = SmsMessage.calculateLength(msg, force7BitEncoding);
            return ted.msgCount != 1 ? ted : ted;
        } else if (septets != -1 || septets > 160) {
            ted = SmsMessage.calculateLength(msg, force7BitEncoding);
            if (ted.msgCount != 1 && ted.codeUnitSize == 1) {
                ted.codeUnitCount = msg.length();
                int octets = ted.codeUnitCount * 2;
                if (octets > 140) {
                    ted.msgCount = (octets + 133) / 134;
                    ted.codeUnitsRemaining = ((ted.msgCount * 134) - octets) / 2;
                } else {
                    ted.msgCount = 1;
                    ted.codeUnitsRemaining = (140 - octets) / 2;
                }
                if (isEms) {
                    return ted;
                }
                ted.codeUnitSize = 3;
                return ted;
            }
        } else {
            ted = new TextEncodingDetails();
            ted.msgCount = 1;
            ted.codeUnitCount = septets;
            ted.codeUnitsRemaining = 160 - septets;
            ted.codeUnitSize = 1;
            return ted;
        }
    }

    public static TextEncodingDetails calcTextEncodingDetailsWithEmail(CharSequence msg, boolean force7BitEncoding, int maxEmailLen) {
        TextEncodingDetails ted = new TextEncodingDetails();
        int septets = countAsciiSeptets(msg, force7BitEncoding);
        int maxLenPerSMS = maxEmailLen > 0 ? (160 - maxEmailLen) - 1 : 160;
        int maxLenPerSMSWithHeader = maxEmailLen > 0 ? (154 - maxEmailLen) - 1 : 154;
        if (septets != -1 && septets <= maxLenPerSMS) {
            ted.msgCount = 1;
            ted.codeUnitCount = septets;
            ted.codeUnitsRemaining = maxLenPerSMS - septets;
            ted.codeUnitSize = 1;
        } else if (septets != -1) {
            ted.codeUnitCount = septets;
            if (septets > maxLenPerSMS) {
                ted.msgCount = ((septets - 1) / maxLenPerSMSWithHeader) + 1;
                if (septets % maxLenPerSMSWithHeader > 0) {
                    ted.codeUnitsRemaining = maxLenPerSMSWithHeader - (septets % maxLenPerSMSWithHeader);
                } else {
                    ted.codeUnitsRemaining = 0;
                }
            } else {
                ted.msgCount = 1;
                ted.codeUnitsRemaining = maxLenPerSMS - septets;
            }
            ted.codeUnitSize = 1;
        } else {
            maxEmailLen *= 2;
            maxLenPerSMS = maxEmailLen > 0 ? (140 - maxEmailLen) - 1 : 140;
            maxLenPerSMSWithHeader = maxEmailLen > 0 ? (128 - maxEmailLen) - 1 : 128;
            int octets = msg.length() * 2;
            ted.codeUnitCount = msg.length();
            if (octets > maxLenPerSMS) {
                if (maxEmailLen > maxLenPerSMS - 2) {
                    ted.msgCount = 1000;
                    ted.codeUnitsRemaining = -1;
                } else if (octets % maxLenPerSMSWithHeader != 0) {
                    ted.msgCount = (octets / maxLenPerSMSWithHeader) + 1;
                    ted.codeUnitsRemaining = (maxLenPerSMSWithHeader - (octets % maxLenPerSMSWithHeader)) / 2;
                } else {
                    ted.msgCount = octets / maxLenPerSMSWithHeader;
                    ted.codeUnitsRemaining = 0;
                }
            } else if (maxEmailLen >= maxLenPerSMSWithHeader - 2) {
                ted.msgCount = 1000;
                ted.codeUnitsRemaining = -1;
            } else {
                ted.msgCount = 1;
                ted.codeUnitsRemaining = (maxLenPerSMS - octets) / 2;
            }
            ted.codeUnitSize = 3;
        }
        return ted;
    }

    private static void extractPagination(String payloadStr, UserData userData) {
        int segNum = 0;
        int totNum = 0;
        boolean paginationSuccess = false;
        String payload = payloadStr;
        if (payloadStr == null) {
            Rlog.d(LOG_TAG, "there is no message body");
            return;
        }
        String pagination = null;
        try {
            if (payloadStr.startsWith("(") && payloadStr.contains(")")) {
                pagination = payloadStr.substring(payloadStr.indexOf(40) + 1, payloadStr.indexOf(41));
                payloadStr = payloadStr.substring(payloadStr.indexOf(41) + 2);
            } else if (payloadStr.startsWith("[") && payloadStr.contains("]")) {
                pagination = payloadStr.substring(payloadStr.indexOf(91) + 1, payloadStr.indexOf(93));
                payloadStr = payloadStr.substring(payloadStr.indexOf(93) + 2);
            } else if (payloadStr.startsWith("{") && payloadStr.contains("}")) {
                pagination = payloadStr.substring(payloadStr.indexOf(123) + 1, payloadStr.indexOf(125));
                payloadStr = payloadStr.substring(payloadStr.indexOf(125) + 2);
            } else {
                Rlog.d(LOG_TAG, "there is no pagination pattern maybe / or of ");
            }
            if (pagination != null) {
                payload = payloadStr;
                String[] page = pagination.split("/");
                if (page.length == 2) {
                    segNum = Integer.parseInt(page[0].trim());
                    totNum = Integer.parseInt(page[1].trim());
                    paginationSuccess = true;
                }
            } else {
                if (payloadStr.split(" of ").length >= 2) {
                    String[] pageCount = payloadStr.split(" ");
                    if (pageCount.length >= 3) {
                        try {
                            segNum = Integer.parseInt(pageCount[0].trim());
                            totNum = Integer.parseInt(pageCount[2].trim());
                            payload = payloadStr.substring(((pageCount[0].length() + pageCount[2].length()) + 4) + 1);
                            paginationSuccess = true;
                        } catch (NumberFormatException e) {
                            Rlog.d(LOG_TAG, "there is no pagination yet");
                        }
                    }
                }
                if (!paginationSuccess) {
                    String[] tempPage = payloadStr.split("/");
                    if (tempPage.length >= 2) {
                        totNum = 0;
                        char[] totalNumber = tempPage[1].toCharArray();
                        try {
                            segNum = Integer.parseInt(tempPage[0].trim());
                            int i = 0;
                            while (Character.isDigit(totalNumber[i])) {
                                if (i == 0) {
                                    totNum = Character.getNumericValue(totalNumber[i]);
                                } else if (i == 1) {
                                    totNum = (Character.getNumericValue(totalNumber[0]) * 10) + Character.getNumericValue(totalNumber[1]);
                                } else if (i == 2) {
                                    totNum = ((Character.getNumericValue(totalNumber[0]) * 100) + (Character.getNumericValue(totalNumber[1]) * 10)) + Character.getNumericValue(totalNumber[2]);
                                } else if (i == 3) {
                                    totNum = (((Character.getNumericValue(totalNumber[0]) * 1000) + (Character.getNumericValue(totalNumber[1]) * 100)) + (Character.getNumericValue(totalNumber[2]) * 10)) + Character.getNumericValue(totalNumber[3]);
                                }
                                i++;
                            }
                            payload = payloadStr.substring((tempPage[0].length() + i) + 1);
                            paginationSuccess = true;
                        } catch (NumberFormatException e2) {
                            Rlog.d(LOG_TAG, "there is no pagination");
                        } catch (ArrayIndexOutOfBoundsException ex) {
                            Rlog.e(LOG_TAG, "extractPagination : " + ex);
                            return;
                        }
                    }
                }
            }
            byte[] data = payload.getBytes();
            userLength = data.length;
            Rlog.d(LOG_TAG, "spr segment length : " + userLength);
            if (userData.msgEncoding == 3 && userLength > 1) {
                compChar = (char) ((data[userLength - 2] & 255) << 8);
                compChar = (char) (compChar | ((char) (data[userLength - 1] & 255)));
                if (compChar == '?' || compChar == '?') {
                    Rlog.d(LOG_TAG, "spr emoji is broken in the end of segment");
                    mlastByte = new byte[2];
                    mlastByte[0] = data[userLength - 2];
                    mlastByte[1] = data[userLength - 1];
                    mIsfourBytesUnicode = true;
                    mBodyOffset = 0;
                } else {
                    mIsfourBytesUnicode = false;
                }
            }
            if (paginationSuccess) {
                Rlog.d(LOG_TAG, "segmented number: " + segNum);
                Rlog.d(LOG_TAG, "total number: " + totNum);
                if ((segNum < 0 && segNum > 9999) || (totNum < 0 && segNum > 9999)) {
                    Rlog.d(LOG_TAG, "Its not segmented sms. ");
                    return;
                } else if (segNum == 0 || totNum == 0 || segNum > totNum || totNum > 9999) {
                    Rlog.d(LOG_TAG, "It's not segmented sms.");
                    return;
                } else {
                    Rlog.d(LOG_TAG, "It's segmented sms");
                    ConcatRef concatRef = new ConcatRef();
                    concatRef.seqNumber = segNum;
                    concatRef.msgCount = totNum;
                    userData.userDataHeader = new SmsHeader();
                    userData.userDataHeader.concatRef = concatRef;
                    userData.payloadStr = payload;
                    return;
                }
            }
            Rlog.d(LOG_TAG, "No pagination found");
        } catch (StringIndexOutOfBoundsException ex2) {
            Rlog.e(LOG_TAG, "extractPagination : " + ex2);
        }
    }

    private static boolean decodeUnknownSubParam(BearerData bData, BitwiseInputStream inStream) throws BitwiseInputStream.AccessException, CodingException {
        inStream.skip(inStream.read(8) * 8);
        return true;
    }

    private static boolean decodeLguCbsSession(BearerData bData, BitwiseInputStream inStream) throws BitwiseInputStream.AccessException, CodingException {
        boolean decodeSuccess = false;
        int paramBits = inStream.read(8) * 8;
        if (paramBits >= 16) {
            paramBits -= 16;
            decodeSuccess = true;
            bData.sessionId = inStream.read(8);
            bData.sessionSeq = inStream.read(7);
            bData.sessionSeqEos = inStream.read(1);
        }
        if (!decodeSuccess || paramBits > 0) {
            Rlog.d(LOG_TAG, "LGU CBS SESSION decode " + (decodeSuccess ? "succeeded:ID(" + bData.sessionId + ")/SEQ(" + bData.sessionSeq + ")/SEQEOS(" + bData.sessionSeqEos + ")" : "failed") + " (extra bits = " + paramBits + ")");
        }
        inStream.skip(paramBits);
        bData.lguCbsSessionSet = decodeSuccess;
        return decodeSuccess;
    }

    private static boolean decodeLguCbsImage(BearerData bData, BitwiseInputStream inStream) throws BitwiseInputStream.AccessException, CodingException {
        boolean decodeSuccess = false;
        int paramBits = inStream.read(8) * 8;
        if (paramBits >= 16) {
            paramBits -= 16;
            decodeSuccess = true;
            inStream.readByteArray(16);
        }
        if (!decodeSuccess || paramBits > 0) {
            Rlog.d(LOG_TAG, "decodeCbsImage decode " + (decodeSuccess ? "succeeded" : "failed") + " (extra bits = " + paramBits + ")");
        }
        inStream.skip(paramBits);
        return decodeSuccess;
    }
}
