package com.android.internal.telephony.gsm;

import android.content.res.Resources;
import android.os.SystemProperties;
import android.telephony.PhoneNumberUtils;
import android.telephony.Rlog;
import android.text.TextUtils;
import android.text.format.Time;
import com.android.internal.telephony.EncodeException;
import com.android.internal.telephony.GsmAlphabet;
import com.android.internal.telephony.GsmAlphabet.TextEncodingDetails;
import com.android.internal.telephony.Sms7BitEncodingTranslator;
import com.android.internal.telephony.SmsAddress;
import com.android.internal.telephony.SmsConstants.MessageClass;
import com.android.internal.telephony.SmsHeader;
import com.android.internal.telephony.SmsHeader.PortAddrs;
import com.android.internal.telephony.SmsMessageBase;
import com.android.internal.telephony.SmsMessageBase.DeliverPduBase;
import com.android.internal.telephony.SmsMessageBase.SubmitPduBase;
import com.android.internal.telephony.uicc.IccUtils;
import com.google.android.mms.pdu.CharacterSets;
import com.google.android.mms.pdu.PduHeaders;
import com.sec.android.app.CscFeature;
import java.io.ByteArrayOutputStream;
import java.io.UnsupportedEncodingException;
import java.text.ParseException;
import java.util.Locale;
import java.util.TimeZone;

public class SmsMessage extends SmsMessageBase {
    private static final boolean CSCFEATURE_RIL_SPECIAL_ADDRESS_HANDLINGFOR = "CTC".equals(CscFeature.getInstance().getString("CscFeature_RIL_SpecialAddressHandlingFor"));
    static final String LOG_TAG = "SmsMessage";
    private static final boolean VDBG = false;
    public static int mDay;
    public static int mHour;
    public static int mMin;
    public static int mMonth;
    public static int mSec;
    public static int mTimezone;
    public static int mYear;
    private static boolean unsupportedDatacodingScheme = false;
    private int mDataCodingScheme;
    private boolean mIsStatusReportMessage = false;
    private int mProtocolIdentifier;
    private GsmSmsAddress mRecipientAddress;
    private boolean mReplyPathPresent = false;
    private int mStatus;
    private int mVoiceMailCount = 0;
    private MessageClass messageClass;

    public static class DeliverPdu extends DeliverPduBase {
    }

    private static class PduParser {
        int mCur;
        byte[] mPdu;
        byte[] mUserData;
        SmsHeader mUserDataHeader;
        int mUserDataSeptetPadding;
        int validityPeriodFormat = 0;

        PduParser(byte[] pdu) {
            this.mPdu = pdu;
            this.mCur = 0;
            this.mUserDataSeptetPadding = 0;
        }

        String getSCAddress() {
            String ret;
            int len = getByte();
            if (len == 0) {
                ret = null;
            } else {
                try {
                    ret = PhoneNumberUtils.calledPartyBCDToString(this.mPdu, this.mCur, len);
                } catch (RuntimeException tr) {
                    Rlog.d(SmsMessage.LOG_TAG, "invalid SC address: ", tr);
                    ret = null;
                }
            }
            this.mCur += len;
            return ret;
        }

        int getByte() {
            byte[] bArr = this.mPdu;
            int i = this.mCur;
            this.mCur = i + 1;
            return bArr[i] & 255;
        }

        GsmSmsAddress getAddress() {
            int lengthBytes = (((this.mPdu[this.mCur] & 255) + 1) / 2) + 2;
            try {
                GsmSmsAddress ret = new GsmSmsAddress(this.mPdu, this.mCur, lengthBytes);
                this.mCur += lengthBytes;
                return ret;
            } catch (ParseException e) {
                throw new RuntimeException(e.getMessage());
            }
        }

        long getSCTimestampMillis() {
            byte[] bArr = this.mPdu;
            int i = this.mCur;
            this.mCur = i + 1;
            int year = IccUtils.gsmBcdByteToInt(bArr[i]);
            bArr = this.mPdu;
            i = this.mCur;
            this.mCur = i + 1;
            int month = IccUtils.gsmBcdByteToInt(bArr[i]);
            bArr = this.mPdu;
            i = this.mCur;
            this.mCur = i + 1;
            int day = IccUtils.gsmBcdByteToInt(bArr[i]);
            bArr = this.mPdu;
            i = this.mCur;
            this.mCur = i + 1;
            int hour = IccUtils.gsmBcdByteToInt(bArr[i]);
            bArr = this.mPdu;
            i = this.mCur;
            this.mCur = i + 1;
            int minute = IccUtils.gsmBcdByteToInt(bArr[i]);
            bArr = this.mPdu;
            i = this.mCur;
            this.mCur = i + 1;
            int second = IccUtils.gsmBcdByteToInt(bArr[i]);
            bArr = this.mPdu;
            i = this.mCur;
            this.mCur = i + 1;
            byte tzByte = bArr[i];
            int timezoneOffset = IccUtils.gsmBcdByteToInt((byte) (tzByte & -9));
            if ((tzByte & 8) != 0) {
                timezoneOffset = -timezoneOffset;
            }
            Time time = new Time("UTC");
            time.year = year >= 90 ? year + 1900 : year + 2000;
            time.month = month - 1;
            time.monthDay = day;
            time.hour = hour;
            time.minute = minute;
            time.second = second;
            return time.toMillis(true) - ((long) (((timezoneOffset * 15) * 60) * 1000));
        }

        int constructUserData(boolean hasUserDataHeader, boolean dataInSeptets) {
            int i;
            int bufferLen;
            int offset = this.mCur;
            switch (this.validityPeriodFormat) {
                case 1:
                case 3:
                    offset = 7;
                    break;
                case 2:
                    offset++;
                    break;
            }
            int offset2 = offset + 1;
            int userDataLength = this.mPdu[offset] & 255;
            int headerSeptets = 0;
            int userDataHeaderLength = 0;
            if (hasUserDataHeader) {
                offset = offset2 + 1;
                userDataHeaderLength = this.mPdu[offset2] & 255;
                byte[] udh = new byte[userDataHeaderLength];
                System.arraycopy(this.mPdu, offset, udh, 0, userDataHeaderLength);
                this.mUserDataHeader = SmsHeader.fromByteArray(udh);
                offset += userDataHeaderLength;
                int headerBits = (userDataHeaderLength + 1) * 8;
                headerSeptets = headerBits / 7;
                if (headerBits % 7 > 0) {
                    i = 1;
                } else {
                    i = 0;
                }
                headerSeptets += i;
                this.mUserDataSeptetPadding = (headerSeptets * 7) - headerBits;
            } else {
                offset = offset2;
            }
            if (dataInSeptets) {
                bufferLen = this.mPdu.length - offset;
            } else {
                if (hasUserDataHeader) {
                    i = userDataHeaderLength + 1;
                } else {
                    i = 0;
                }
                bufferLen = userDataLength - i;
                if (bufferLen < 0) {
                    bufferLen = 0;
                }
            }
            this.mUserData = new byte[bufferLen];
            if (!SmsMessage.unsupportedDatacodingScheme) {
                System.arraycopy(this.mPdu, offset, this.mUserData, 0, this.mUserData.length);
            } else if (hasUserDataHeader) {
                System.arraycopy(this.mPdu, offset, this.mUserData, 0, this.mUserData.length);
            } else {
                Rlog.e(SmsMessage.LOG_TAG, "array copy skip! if dataCodingScheme is unsupporting,\n encodingType is Unknown and messageBody is null");
            }
            this.mCur = offset;
            if (!dataInSeptets) {
                return this.mUserData.length;
            }
            int count = userDataLength - headerSeptets;
            if (count < 0) {
                return 0;
            }
            return count;
        }

        byte[] getUserData() {
            return this.mUserData;
        }

        SmsHeader getUserDataHeader() {
            return this.mUserDataHeader;
        }

        String getUserDataGSM7Bit(int septetCount, int languageTable, int languageShiftTable) {
            String ret = GsmAlphabet.gsm7BitPackedToString(this.mPdu, this.mCur, septetCount, this.mUserDataSeptetPadding, languageTable, languageShiftTable);
            this.mCur += (septetCount * 7) / 8;
            return ret;
        }

        String getUserDataGSM8bit(int byteCount) {
            String ret = GsmAlphabet.gsm8BitUnpackedToString(this.mPdu, this.mCur, byteCount);
            this.mCur += byteCount;
            return ret;
        }

        String getUserDataUCS2(int byteCount) {
            String ret;
            try {
                byte[] nsriUserdata = getUserData();
                ret = new String(this.mPdu, this.mCur, byteCount, CharacterSets.MIMENAME_UTF_16);
            } catch (UnsupportedEncodingException ex) {
                ret = "";
                Rlog.e(SmsMessage.LOG_TAG, "implausible UnsupportedEncodingException", ex);
            }
            this.mCur += byteCount;
            return ret;
        }

        String getUserDataKSC5601(int byteCount) {
            String ret;
            try {
                byte[] nsriUserdata = getUserData();
                ret = new String(this.mPdu, this.mCur, byteCount, "KSC5601");
            } catch (UnsupportedEncodingException ex) {
                ret = "";
                Rlog.e(SmsMessage.LOG_TAG, "implausible UnsupportedEncodingException", ex);
            }
            this.mCur += byteCount;
            return ret;
        }

        String getUseDataNSRISms(int byteCount) {
            UnsupportedEncodingException ex;
            String str;
            byte[] nsriUserdata = getUserData();
            Rlog.d(SmsMessage.LOG_TAG, "[NSRI_SMS] getUseDataNSRISms");
            try {
                if (!Integer.toHexString(nsriUserdata[0] & 255).equals("f1") || !Integer.toHexString(nsriUserdata[1] & 255).equals("a0")) {
                    return new String(this.mPdu, this.mCur, byteCount, CharacterSets.MIMENAME_UTF_16);
                }
                String ret = new String(this.mPdu, this.mCur, byteCount, "ISO8859_1");
                try {
                    Rlog.d(SmsMessage.LOG_TAG, "[NSRI_SMS] : getUserDataUCS2    ISO8859_1");
                    return ret;
                } catch (UnsupportedEncodingException e) {
                    ex = e;
                    str = ret;
                    str = "";
                    Rlog.e(SmsMessage.LOG_TAG, "implausible UnsupportedEncodingException", ex);
                    return str;
                }
            } catch (UnsupportedEncodingException e2) {
                ex = e2;
                str = "";
                Rlog.e(SmsMessage.LOG_TAG, "implausible UnsupportedEncodingException", ex);
                return str;
            }
        }

        boolean moreDataPresent() {
            return this.mPdu.length > this.mCur;
        }
    }

    public static class SubmitPdu extends SubmitPduBase {
    }

    public static SmsMessage createFromPdu(byte[] pdu) {
        try {
            SmsMessage msg = new SmsMessage();
            msg.parsePdu(pdu);
            return msg;
        } catch (RuntimeException ex) {
            Rlog.e(LOG_TAG, "SMS PDU parsing failed: ", ex);
            return null;
        } catch (OutOfMemoryError e) {
            Rlog.e(LOG_TAG, "SMS PDU parsing failed with out of memory: ", e);
            return null;
        }
    }

    public boolean isTypeZero() {
        return this.mProtocolIdentifier == 64;
    }

    public static SmsMessage newFromCMT(String[] lines) {
        try {
            SmsMessage msg = new SmsMessage();
            msg.parsePdu(IccUtils.hexStringToBytes(lines[1]));
            return msg;
        } catch (RuntimeException ex) {
            Rlog.e(LOG_TAG, "SMS PDU parsing failed: ", ex);
            return null;
        }
    }

    public static SmsMessage newFromCDS(String line) {
        try {
            SmsMessage msg = new SmsMessage();
            msg.parsePdu(IccUtils.hexStringToBytes(line));
            return msg;
        } catch (RuntimeException ex) {
            Rlog.e(LOG_TAG, "CDS SMS PDU parsing failed: ", ex);
            return null;
        }
    }

    public static SmsMessage createFromEfRecord(int index, byte[] data) {
        try {
            SmsMessage msg = new SmsMessage();
            msg.mIndexOnIcc = index;
            if ((data[0] & 1) == 0) {
                Rlog.w(LOG_TAG, "SMS parsing failed: Trying to parse a free record");
                return null;
            }
            msg.mStatusOnIcc = data[0] & 7;
            int size = data.length - 1;
            byte[] pdu = new byte[size];
            System.arraycopy(data, 1, pdu, 0, size);
            msg.parsePdu(pdu);
            return msg;
        } catch (RuntimeException ex) {
            Rlog.e(LOG_TAG, "SMS PDU parsing failed: ", ex);
            return null;
        }
    }

    public static int getTPLayerLengthForPDU(String pdu) {
        return ((pdu.length() / 2) - Integer.parseInt(pdu.substring(0, 2), 16)) - 1;
    }

    public static SubmitPdu getSubmitPdu(String scAddress, String destinationAddress, String message, boolean statusReportRequested, byte[] header) {
        return getSubmitPdu(scAddress, destinationAddress, message, statusReportRequested, header, 0, 0, 0);
    }

    public static SubmitPdu getSubmitPdu(String scAddress, String destinationAddress, String message, boolean statusReportRequested, byte[] header, int encoding, int languageTable, int languageShiftTable) {
        if (message == null || destinationAddress == null) {
            return null;
        }
        byte[] userData;
        if (encoding == 0) {
            TextEncodingDetails ted = calculateLength(message, false);
            encoding = ted.codeUnitSize;
            languageTable = ted.languageTable;
            languageShiftTable = ted.languageShiftTable;
            if (encoding == 1 && !(languageTable == 0 && languageShiftTable == 0)) {
                SmsHeader smsHeader;
                if (header != null) {
                    smsHeader = SmsHeader.fromByteArray(header);
                    if (!(smsHeader.languageTable == languageTable && smsHeader.languageShiftTable == languageShiftTable)) {
                        Rlog.w(LOG_TAG, "Updating language table in SMS header: " + smsHeader.languageTable + " -> " + languageTable + ", " + smsHeader.languageShiftTable + " -> " + languageShiftTable);
                        smsHeader.languageTable = languageTable;
                        smsHeader.languageShiftTable = languageShiftTable;
                        header = SmsHeader.toByteArray(smsHeader);
                    }
                } else {
                    smsHeader = new SmsHeader();
                    smsHeader.languageTable = languageTable;
                    smsHeader.languageShiftTable = languageShiftTable;
                    header = SmsHeader.toByteArray(smsHeader);
                }
            }
        }
        SubmitPdu ret = new SubmitPdu();
        ByteArrayOutputStream bo = getSubmitPduHead(scAddress, destinationAddress, (byte) ((header != null ? 64 : 0) | 1), statusReportRequested, ret);
        if (encoding == 1) {
            try {
                userData = GsmAlphabet.stringToGsm7BitPackedWithHeader(message, header, languageTable, languageShiftTable);
            } catch (EncodeException e) {
                try {
                    userData = encodeUCS2(message, header);
                    encoding = 3;
                } catch (UnsupportedEncodingException uex) {
                    Rlog.e(LOG_TAG, "Implausible UnsupportedEncodingException ", uex);
                    return null;
                }
            }
        }
        try {
            userData = encodeUCS2(message, header);
        } catch (UnsupportedEncodingException uex2) {
            Rlog.e(LOG_TAG, "Implausible UnsupportedEncodingException ", uex2);
            return null;
        }
        if (encoding == 1) {
            if ((userData[0] & 255) > 160) {
                Rlog.e(LOG_TAG, "Message too long (" + (userData[0] & 255) + " septets)");
                return null;
            }
            bo.write(0);
        } else if ((userData[0] & 255) > 140) {
            Rlog.e(LOG_TAG, "Message too long (" + (userData[0] & 255) + " bytes)");
            return null;
        } else {
            bo.write(8);
        }
        bo.write(userData, 0, userData.length);
        ret.encodedMessage = bo.toByteArray();
        return ret;
    }

    private static byte[] encodeUCS2(String message, byte[] header) throws UnsupportedEncodingException {
        byte[] userData;
        byte[] textPart = message.getBytes("utf-16be");
        if (header != null) {
            userData = new byte[((header.length + textPart.length) + 1)];
            userData[0] = (byte) header.length;
            System.arraycopy(header, 0, userData, 1, header.length);
            System.arraycopy(textPart, 0, userData, header.length + 1, textPart.length);
        } else {
            userData = textPart;
        }
        byte[] ret = new byte[(userData.length + 1)];
        ret[0] = (byte) (userData.length & 255);
        System.arraycopy(userData, 0, ret, 1, userData.length);
        return ret;
    }

    public static SubmitPdu getSubmitPdu(String scAddress, String destinationAddress, String message, boolean statusReportRequested) {
        return getSubmitPdu(scAddress, destinationAddress, message, statusReportRequested, null);
    }

    public static SubmitPdu getSubmitPdu(String scAddress, String destinationAddress, String message, boolean statusReportRequested, byte[] header, boolean replyPath, int expiry, int serviceType, int encodingType) {
        return getSubmitPdu(scAddress, destinationAddress, message, statusReportRequested, header, replyPath, expiry, serviceType, encodingType, 0, 0);
    }

    public static SubmitPdu getSubmitPdu(String scAddress, String destinationAddress, String message, boolean statusReportRequested, boolean replyPath, int expiry, int serviceType, int encodingType, int a, int b) {
        if (a <= 0 && b <= 0) {
            return getSubmitPdu(scAddress, destinationAddress, message, statusReportRequested, null, replyPath, expiry, serviceType, encodingType, a, b);
        }
        SmsHeader Header = new SmsHeader();
        Header.languageTable = a;
        Header.languageShiftTable = b;
        return getSubmitPdu(scAddress, destinationAddress, message, statusReportRequested, SmsHeader.toByteArray(Header), replyPath, expiry, serviceType, encodingType, a, b);
    }

    public static SubmitPdu getSubmitPdu(String scAddress, String destinationAddress, String message, boolean statusReportRequested, byte[] header, boolean replyPath, int expiry, int serviceType, int encodingType, int languageTable, int languageShiftTable) {
        byte[] userData;
        Rlog.e(LOG_TAG, "getSubmitPdu with Options");
        if (message == null || destinationAddress == null) {
            return null;
        }
        Rlog.e(LOG_TAG, "**getSubmitPdu_Options**");
        Rlog.e(LOG_TAG, "replyPath = " + replyPath);
        Rlog.e(LOG_TAG, "encodingType = " + encodingType);
        Rlog.e(LOG_TAG, "**********************");
        SubmitPdu ret = new SubmitPdu();
        byte mtiByte = (byte) ((header != null ? 64 : 0) | 1);
        if (replyPath) {
            mtiByte = (byte) (mtiByte | 128);
            Rlog.e(LOG_TAG, "mtiByte = " + mtiByte);
        }
        mtiByte = (byte) (mtiByte | 16);
        Rlog.e(LOG_TAG, "mtiByte = " + mtiByte);
        ByteArrayOutputStream bo = getSubmitPduHead(scAddress, destinationAddress, mtiByte, statusReportRequested, ret);
        if (encodingType == 1) {
            try {
                throw new EncodeException("Input Method is Unicode");
            } catch (EncodeException e) {
                try {
                    byte[] textPart = message.getBytes("utf-16be");
                    if (header != null) {
                        userData = new byte[((header.length + textPart.length) + 1)];
                        userData[0] = (byte) header.length;
                        System.arraycopy(header, 0, userData, 1, header.length);
                        System.arraycopy(textPart, 0, userData, header.length + 1, textPart.length);
                    } else {
                        userData = textPart;
                    }
                    if (userData.length > 140) {
                        return null;
                    }
                    bo.write(8);
                    bo.write(expiry);
                    Rlog.e(LOG_TAG, "expirty = " + expiry);
                    bo.write(userData.length);
                    bo.write(userData, 0, userData.length);
                } catch (UnsupportedEncodingException uex) {
                    Rlog.e(LOG_TAG, "Implausible UnsupportedEncodingException ", uex);
                    return null;
                }
            }
        }
        userData = GsmAlphabet.stringToGsm7BitPackedWithHeader(message, header, languageTable, languageShiftTable);
        if ((userData[0] & 255) > 160) {
            return null;
        }
        bo.write(0);
        bo.write(expiry);
        Rlog.e(LOG_TAG, "expirty = " + expiry);
        bo.write(userData, 0, userData.length);
        ret.encodedMessage = bo.toByteArray();
        return ret;
    }

    public static SubmitPdu getSubmitPdu(String scAddress, String destinationAddress, int destinationPort, byte[] data, boolean statusReportRequested) {
        PortAddrs portAddrs = new PortAddrs();
        portAddrs.destPort = destinationPort;
        portAddrs.origPort = 0;
        portAddrs.areEightBits = false;
        SmsHeader smsHeader = new SmsHeader();
        smsHeader.portAddrs = portAddrs;
        byte[] smsHeaderData = SmsHeader.toByteArray(smsHeader);
        if ((data.length + smsHeaderData.length) + 1 > 140) {
            Rlog.e(LOG_TAG, "SMS data message may only contain " + ((140 - smsHeaderData.length) - 1) + " bytes");
            return null;
        }
        SubmitPdu ret = new SubmitPdu();
        ByteArrayOutputStream bo = getSubmitPduHead(scAddress, destinationAddress, (byte) 65, statusReportRequested, ret);
        bo.write(4);
        bo.write((data.length + smsHeaderData.length) + 1);
        bo.write(smsHeaderData.length);
        bo.write(smsHeaderData, 0, smsHeaderData.length);
        bo.write(data, 0, data.length);
        ret.encodedMessage = bo.toByteArray();
        return ret;
    }

    public static SubmitPdu getSubmitPduForKTOTA(String scAddress, String destinationAddress, String message) {
        int i;
        SubmitPdu ret = new SubmitPdu();
        ByteArrayOutputStream bo = new ByteArrayOutputStream(PduHeaders.RECOMMENDED_RETRIEVAL_MODE);
        if (scAddress == null) {
            ret.encodedScAddress = null;
        } else {
            ret.encodedScAddress = PhoneNumberUtils.networkPortionToCalledPartyBCDWithLength(scAddress);
        }
        bo.write((byte) 1);
        bo.write(0);
        byte[] daBytes = PhoneNumberUtils.networkPortionToCalledPartyBCD(destinationAddress);
        int length = (daBytes.length - 1) * 2;
        if ((daBytes[daBytes.length - 1] & 240) == 240) {
            i = 1;
        } else {
            i = 0;
        }
        bo.write(length - i);
        bo.write(daBytes, 0, daBytes.length);
        bo.write(127);
        try {
            byte[] userData = GsmAlphabet.stringToGsm7BitPacked(message);
            if ((userData[0] & 255) > 160) {
                return null;
            }
            bo.write(0);
            bo.write(userData, 0, userData.length);
            ret.encodedMessage = bo.toByteArray();
            return ret;
        } catch (EncodeException ex) {
            Rlog.e(LOG_TAG, "Implausible UnsupportedEncodingException ", ex);
            return null;
        }
    }

    public static SubmitPdu getSubmitPdu(String scAddress, String destinationAddress, int destinationPort, int originationPort, byte[] data, boolean statusReportRequested) {
        PortAddrs portAddrs = new PortAddrs();
        portAddrs.destPort = destinationPort;
        portAddrs.origPort = originationPort;
        portAddrs.areEightBits = false;
        SmsHeader smsHeader = new SmsHeader();
        smsHeader.portAddrs = portAddrs;
        byte[] smsHeaderData = SmsHeader.toByteArray(smsHeader);
        if ((data.length + smsHeaderData.length) + 1 > 140) {
            Rlog.e(LOG_TAG, "SMS data message may only contain " + ((140 - smsHeaderData.length) - 1) + " bytes");
            return null;
        }
        SubmitPdu ret = new SubmitPdu();
        ByteArrayOutputStream bo = getSubmitPduHead(scAddress, destinationAddress, (byte) 65, statusReportRequested, ret);
        bo.write(4);
        bo.write((data.length + smsHeaderData.length) + 1);
        bo.write(smsHeaderData.length);
        bo.write(smsHeaderData, 0, smsHeaderData.length);
        bo.write(data, 0, data.length);
        ret.encodedMessage = bo.toByteArray();
        return ret;
    }

    private static ByteArrayOutputStream getSubmitPduHead(String scAddress, String destinationAddress, byte mtiByte, boolean statusReportRequested, SubmitPdu ret) {
        int i;
        ByteArrayOutputStream bo = new ByteArrayOutputStream(PduHeaders.RECOMMENDED_RETRIEVAL_MODE);
        if (scAddress == null) {
            ret.encodedScAddress = null;
        } else {
            ret.encodedScAddress = PhoneNumberUtils.networkPortionToCalledPartyBCDWithLength(scAddress);
        }
        if (statusReportRequested) {
            mtiByte = (byte) (mtiByte | 32);
        }
        bo.write(mtiByte);
        bo.write(0);
        byte[] daBytes = PhoneNumberUtils.networkPortionToCalledPartyBCD(destinationAddress);
        int length = (daBytes.length - 1) * 2;
        if ((daBytes[daBytes.length - 1] & 240) == 240) {
            i = 1;
        } else {
            i = 0;
        }
        bo.write(length - i);
        bo.write(daBytes, 0, daBytes.length);
        bo.write(0);
        return bo;
    }

    public static TextEncodingDetails calculateLength(CharSequence msgBody, boolean use7bitOnly) {
        CharSequence newMsgBody = null;
        if (Resources.getSystem().getBoolean(17956991)) {
            newMsgBody = Sms7BitEncodingTranslator.translate(msgBody);
        }
        if (TextUtils.isEmpty(newMsgBody)) {
            newMsgBody = msgBody;
        }
        TextEncodingDetails ted = GsmAlphabet.countGsmSeptets(newMsgBody, use7bitOnly);
        if (ted == null) {
            ted = new TextEncodingDetails();
            int octets = newMsgBody.length() * 2;
            ted.codeUnitCount = newMsgBody.length();
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
        }
        return ted;
    }

    public int getProtocolIdentifier() {
        return this.mProtocolIdentifier;
    }

    int getDataCodingScheme() {
        return this.mDataCodingScheme;
    }

    public boolean isReplace() {
        return (this.mProtocolIdentifier & 192) == 64 && (this.mProtocolIdentifier & 63) > 0 && (this.mProtocolIdentifier & 63) < 8;
    }

    public boolean isCphsMwiMessage() {
        return ((GsmSmsAddress) this.mOriginatingAddress).isCphsVoiceMessageClear() || ((GsmSmsAddress) this.mOriginatingAddress).isCphsVoiceMessageSet();
    }

    public boolean isMWIClearMessage() {
        if (this.mIsMwi && !this.mMwiSense) {
            return true;
        }
        boolean z = this.mOriginatingAddress != null && ((GsmSmsAddress) this.mOriginatingAddress).isCphsVoiceMessageClear();
        return z;
    }

    public boolean isMWISetMessage() {
        if (this.mIsMwi && this.mMwiSense) {
            return true;
        }
        boolean z = this.mOriginatingAddress != null && ((GsmSmsAddress) this.mOriginatingAddress).isCphsVoiceMessageSet();
        return z;
    }

    public boolean isMwiDontStore() {
        if (this.mIsMwi && this.mMwiDontStore) {
            return true;
        }
        if (isCphsMwiMessage()) {
            if (" ".equals(getMessageBody())) {
                return true;
            }
            String SalesCode = SystemProperties.get("ro.csc.sales_code");
            if ("RWC".equals(SalesCode) || "TLS".equals(SalesCode) || "MTA".equals(SalesCode)) {
                Rlog.d(LOG_TAG, "CPHS MWI messages in Canada " + SalesCode + " don't store");
                return true;
            }
        }
        if (getMessageBody() == null || getMessageBody().length() == 0) {
        }
        return false;
    }

    public int getStatus() {
        return this.mStatus;
    }

    public boolean isStatusReportMessage() {
        return this.mIsStatusReportMessage;
    }

    public boolean isReplyPathPresent() {
        return this.mReplyPathPresent;
    }

    private void parsePdu(byte[] pdu) {
        int firstByte;
        this.mPdu = pdu;
        PduParser p = new PduParser(pdu);
        this.mScAddress = p.getSCAddress();
        if (this.mScAddress != null) {
            firstByte = p.getByte();
            this.mMti = firstByte & 3;
        } else {
            firstByte = p.getByte();
            this.mMti = firstByte & 3;
        }
        switch (this.mMti) {
            case 0:
            case 3:
                parseSmsDeliver(p, firstByte);
                return;
            case 1:
                parseSmsSubmit(p, firstByte);
                return;
            case 2:
                parseSmsStatusReport(p, firstByte);
                return;
            default:
                throw new RuntimeException("Unsupported message type");
        }
    }

    private void parseSmsStatusReport(PduParser p, int firstByte) {
        boolean hasUserDataHeader = true;
        this.mIsStatusReportMessage = true;
        this.mMessageRef = p.getByte();
        this.mRecipientAddress = p.getAddress();
        this.mScTimeMillis = p.getSCTimestampMillis();
        p.getSCTimestampMillis();
        this.mStatus = p.getByte();
        if (p.moreDataPresent()) {
            int extraParams = p.getByte();
            int moreExtraParams = extraParams;
            while ((moreExtraParams & 128) != 0) {
                moreExtraParams = p.getByte();
            }
            if ((extraParams & 120) == 0) {
                if ((extraParams & 1) != 0) {
                    this.mProtocolIdentifier = p.getByte();
                }
                if ((extraParams & 2) != 0) {
                    this.mDataCodingScheme = p.getByte();
                }
                if ((extraParams & 4) != 0) {
                    if ((firstByte & 64) != 64) {
                        hasUserDataHeader = false;
                    }
                    parseUserData(p, hasUserDataHeader);
                }
            }
        }
    }

    private void parseSmsDeliver(PduParser p, int firstByte) {
        boolean z;
        if ((firstByte & 128) == 128) {
            z = true;
        } else {
            z = false;
        }
        this.mReplyPathPresent = z;
        this.mOriginatingAddress = p.getAddress();
        if (this.mOriginatingAddress != null && CSCFEATURE_RIL_SPECIAL_ADDRESS_HANDLINGFOR && this.mOriginatingAddress.address.startsWith("+00852")) {
            String origAddress = new String(this.mOriginatingAddress.address).substring(3);
            this.mOriginatingAddress.address = "+";
            StringBuilder stringBuilder = new StringBuilder();
            SmsAddress smsAddress = this.mOriginatingAddress;
            smsAddress.address = stringBuilder.append(smsAddress.address).append(origAddress).toString();
        }
        if (this.mOriginatingAddress != null) {
            this.mProtocolIdentifier = p.getByte();
            this.mDataCodingScheme = p.getByte();
            this.mScTimeMillis = p.getSCTimestampMillis();
        } else {
            this.mProtocolIdentifier = p.getByte();
            this.mDataCodingScheme = p.getByte();
            this.mScTimeMillis = p.getSCTimestampMillis();
        }
        parseUserData(p, (firstByte & 64) == 64);
    }

    private void parseSmsSubmit(PduParser p, int firstByte) {
        boolean z;
        int validityPeriodFormat;
        int validityPeriodLength;
        boolean hasUserDataHeader;
        if ((firstByte & 128) == 128) {
            z = true;
        } else {
            z = false;
        }
        this.mReplyPathPresent = z;
        this.mMessageRef = p.getByte();
        this.recipientAddress = p.getAddress();
        if (this.recipientAddress != null) {
            this.mProtocolIdentifier = p.getByte();
            this.mDataCodingScheme = p.getByte();
            validityPeriodFormat = (firstByte >> 3) & 3;
        } else {
            this.mProtocolIdentifier = p.getByte();
            this.mDataCodingScheme = p.getByte();
            validityPeriodFormat = (firstByte >> 3) & 3;
        }
        if (validityPeriodFormat == 0) {
            validityPeriodLength = 0;
        } else if (2 == validityPeriodFormat) {
            validityPeriodLength = 1;
        } else {
            validityPeriodLength = 7;
        }
        while (true) {
            int validityPeriodLength2 = validityPeriodLength - 1;
            if (validityPeriodLength <= 0) {
                break;
            }
            p.getByte();
            validityPeriodLength = validityPeriodLength2;
        }
        if ((firstByte & 64) == 64) {
            hasUserDataHeader = true;
        } else {
            hasUserDataHeader = false;
        }
        parseUserData(p, hasUserDataHeader);
    }

    /* JADX WARNING: inconsistent code. */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    private void parseUserData(com.android.internal.telephony.gsm.SmsMessage.PduParser r18, boolean r19) {
        /*
        r17 = this;
        r6 = 0;
        r11 = 0;
        r5 = 0;
        r0 = r17;
        r14 = r0.mDataCodingScheme;
        r14 = r14 & 255;
        r15 = 132; // 0x84 float:1.85E-43 double:6.5E-322;
        if (r14 != r15) goto L_0x00b2;
    L_0x000d:
        r5 = 4;
    L_0x000e:
        r14 = 1;
        if (r5 != r14) goto L_0x02bb;
    L_0x0011:
        r14 = 1;
    L_0x0012:
        r0 = r18;
        r1 = r19;
        r4 = r0.constructUserData(r1, r14);
        r14 = r18.getUserData();
        r0 = r17;
        r0.mUserData = r14;
        r14 = r18.getUserDataHeader();
        r0 = r17;
        r0.mUserDataHeader = r14;
        if (r19 == 0) goto L_0x0308;
    L_0x002c:
        r0 = r17;
        r14 = r0.mUserDataHeader;
        r14 = r14.specialSmsMsgList;
        r14 = r14.size();
        if (r14 == 0) goto L_0x0308;
    L_0x0038:
        r0 = r17;
        r14 = r0.mUserDataHeader;
        r14 = r14.specialSmsMsgList;
        r7 = r14.iterator();
    L_0x0042:
        r14 = r7.hasNext();
        if (r14 == 0) goto L_0x0308;
    L_0x0048:
        r8 = r7.next();
        r8 = (com.android.internal.telephony.SmsHeader.SpecialSmsMsg) r8;
        r14 = r8.msgIndType;
        r9 = r14 & 255;
        if (r9 == 0) goto L_0x0058;
    L_0x0054:
        r14 = 128; // 0x80 float:1.794E-43 double:6.32E-322;
        if (r9 != r14) goto L_0x02ee;
    L_0x0058:
        r14 = 1;
        r0 = r17;
        r0.mIsMwi = r14;
        r14 = 128; // 0x80 float:1.794E-43 double:6.32E-322;
        if (r9 != r14) goto L_0x02be;
    L_0x0061:
        r14 = 0;
        r0 = r17;
        r0.mMwiDontStore = r14;
    L_0x0066:
        r14 = r8.msgCount;
        r14 = r14 & 255;
        r0 = r17;
        r0.mVoiceMailCount = r14;
        r0 = r17;
        r14 = r0.mVoiceMailCount;
        if (r14 <= 0) goto L_0x02e7;
    L_0x0074:
        r14 = 1;
        r0 = r17;
        r0.mMwiSense = r14;
    L_0x0079:
        r14 = "SmsMessage";
        r15 = new java.lang.StringBuilder;
        r15.<init>();
        r16 = "MWI in TP-UDH for Vmail. Msg Ind = ";
        r15 = r15.append(r16);
        r15 = r15.append(r9);
        r16 = " Dont store = ";
        r15 = r15.append(r16);
        r0 = r17;
        r0 = r0.mMwiDontStore;
        r16 = r0;
        r15 = r15.append(r16);
        r16 = " Vmail count = ";
        r15 = r15.append(r16);
        r0 = r17;
        r0 = r0.mVoiceMailCount;
        r16 = r0;
        r15 = r15.append(r16);
        r15 = r15.toString();
        android.telephony.Rlog.w(r14, r15);
        goto L_0x0042;
    L_0x00b2:
        r0 = r17;
        r14 = r0.mDataCodingScheme;
        r14 = r14 & 128;
        if (r14 != 0) goto L_0x014e;
    L_0x00ba:
        r0 = r17;
        r14 = r0.mDataCodingScheme;
        r14 = r14 & 32;
        if (r14 == 0) goto L_0x00f7;
    L_0x00c2:
        r11 = 1;
    L_0x00c3:
        r0 = r17;
        r14 = r0.mDataCodingScheme;
        r14 = r14 & 16;
        if (r14 == 0) goto L_0x00f9;
    L_0x00cb:
        r6 = 1;
    L_0x00cc:
        if (r11 == 0) goto L_0x00fb;
    L_0x00ce:
        r14 = "SmsMessage";
        r15 = new java.lang.StringBuilder;
        r15.<init>();
        r16 = "4 - Unsupported SMS data coding scheme (compression) ";
        r15 = r15.append(r16);
        r0 = r17;
        r0 = r0.mDataCodingScheme;
        r16 = r0;
        r0 = r16;
        r0 = r0 & 255;
        r16 = r0;
        r15 = r15.append(r16);
        r15 = r15.toString();
        android.telephony.Rlog.w(r14, r15);
        r14 = 1;
        unsupportedDatacodingScheme = r14;
        goto L_0x000e;
    L_0x00f7:
        r11 = 0;
        goto L_0x00c3;
    L_0x00f9:
        r6 = 0;
        goto L_0x00cc;
    L_0x00fb:
        r0 = r17;
        r14 = r0.mDataCodingScheme;
        r14 = r14 >> 2;
        r14 = r14 & 3;
        switch(r14) {
            case 0: goto L_0x0108;
            case 1: goto L_0x0114;
            case 2: goto L_0x010e;
            case 3: goto L_0x0124;
            default: goto L_0x0106;
        };
    L_0x0106:
        goto L_0x000e;
    L_0x0108:
        r5 = 1;
        r14 = 0;
        unsupportedDatacodingScheme = r14;
        goto L_0x000e;
    L_0x010e:
        r5 = 3;
        r14 = 0;
        unsupportedDatacodingScheme = r14;
        goto L_0x000e;
    L_0x0114:
        r10 = android.content.res.Resources.getSystem();
        r14 = 17956986; // 0x112007a float:2.6816307E-38 double:8.87193E-317;
        r14 = r10.getBoolean(r14);
        if (r14 == 0) goto L_0x0124;
    L_0x0121:
        r5 = 2;
        goto L_0x000e;
    L_0x0124:
        r14 = "SmsMessage";
        r15 = new java.lang.StringBuilder;
        r15.<init>();
        r16 = "1 - Unsupported SMS data coding scheme ";
        r15 = r15.append(r16);
        r0 = r17;
        r0 = r0.mDataCodingScheme;
        r16 = r0;
        r0 = r16;
        r0 = r0 & 255;
        r16 = r0;
        r15 = r15.append(r16);
        r15 = r15.toString();
        android.telephony.Rlog.w(r14, r15);
        r5 = 2;
        r14 = 0;
        unsupportedDatacodingScheme = r14;
        goto L_0x000e;
    L_0x014e:
        r0 = r17;
        r14 = r0.mDataCodingScheme;
        r14 = r14 & 240;
        r15 = 240; // 0xf0 float:3.36E-43 double:1.186E-321;
        if (r14 != r15) goto L_0x016b;
    L_0x0158:
        r6 = 1;
        r11 = 0;
        r14 = 0;
        unsupportedDatacodingScheme = r14;
        r0 = r17;
        r14 = r0.mDataCodingScheme;
        r14 = r14 & 4;
        if (r14 != 0) goto L_0x0168;
    L_0x0165:
        r5 = 1;
        goto L_0x000e;
    L_0x0168:
        r5 = 2;
        goto L_0x000e;
    L_0x016b:
        r0 = r17;
        r14 = r0.mDataCodingScheme;
        r14 = r14 & 240;
        r15 = 192; // 0xc0 float:2.69E-43 double:9.5E-322;
        if (r14 == r15) goto L_0x0189;
    L_0x0175:
        r0 = r17;
        r14 = r0.mDataCodingScheme;
        r14 = r14 & 240;
        r15 = 208; // 0xd0 float:2.91E-43 double:1.03E-321;
        if (r14 == r15) goto L_0x0189;
    L_0x017f:
        r0 = r17;
        r14 = r0.mDataCodingScheme;
        r14 = r14 & 240;
        r15 = 224; // 0xe0 float:3.14E-43 double:1.107E-321;
        if (r14 != r15) goto L_0x025f;
    L_0x0189:
        r14 = 0;
        unsupportedDatacodingScheme = r14;
        r0 = r17;
        r14 = r0.mDataCodingScheme;
        r14 = r14 & 240;
        r15 = 192; // 0xc0 float:2.69E-43 double:9.5E-322;
        if (r14 != r15) goto L_0x0225;
    L_0x0196:
        r14 = 1;
    L_0x0197:
        r0 = r17;
        r0.mMwiDontStore = r14;
        r14 = 1;
        r0 = r17;
        r0.mIsMwi = r14;
        r0 = r17;
        r14 = r0.mDataCodingScheme;
        r14 = r14 & 240;
        r15 = 224; // 0xe0 float:3.14E-43 double:1.107E-321;
        if (r14 != r15) goto L_0x0228;
    L_0x01aa:
        r5 = 3;
    L_0x01ab:
        r11 = 0;
        r0 = r17;
        r14 = r0.mDataCodingScheme;
        r14 = r14 & 8;
        r15 = 8;
        if (r14 != r15) goto L_0x022a;
    L_0x01b6:
        r2 = 1;
    L_0x01b7:
        r0 = r17;
        r14 = r0.mDataCodingScheme;
        r14 = r14 & 3;
        if (r14 != 0) goto L_0x0234;
    L_0x01bf:
        r14 = 1;
        r0 = r17;
        r0.mIsMwi = r14;
        r0 = r17;
        r0.mMwiSense = r2;
        r0 = r17;
        r14 = r0.mDataCodingScheme;
        r14 = r14 & 240;
        r15 = 192; // 0xc0 float:2.69E-43 double:9.5E-322;
        if (r14 != r15) goto L_0x022c;
    L_0x01d2:
        r14 = 1;
    L_0x01d3:
        r0 = r17;
        r0.mMwiDontStore = r14;
        r14 = 1;
        if (r2 != r14) goto L_0x022e;
    L_0x01da:
        r14 = -1;
        r0 = r17;
        r0.mVoiceMailCount = r14;
    L_0x01df:
        r14 = "SmsMessage";
        r15 = new java.lang.StringBuilder;
        r15.<init>();
        r16 = "MWI in DCS for Vmail. DCS = ";
        r15 = r15.append(r16);
        r0 = r17;
        r0 = r0.mDataCodingScheme;
        r16 = r0;
        r0 = r16;
        r0 = r0 & 255;
        r16 = r0;
        r15 = r15.append(r16);
        r16 = " Dont store = ";
        r15 = r15.append(r16);
        r0 = r17;
        r0 = r0.mMwiDontStore;
        r16 = r0;
        r15 = r15.append(r16);
        r16 = " vmail count = ";
        r15 = r15.append(r16);
        r0 = r17;
        r0 = r0.mVoiceMailCount;
        r16 = r0;
        r15 = r15.append(r16);
        r15 = r15.toString();
        android.telephony.Rlog.w(r14, r15);
        goto L_0x000e;
    L_0x0225:
        r14 = 0;
        goto L_0x0197;
    L_0x0228:
        r5 = 1;
        goto L_0x01ab;
    L_0x022a:
        r2 = 0;
        goto L_0x01b7;
    L_0x022c:
        r14 = 0;
        goto L_0x01d3;
    L_0x022e:
        r14 = 0;
        r0 = r17;
        r0.mVoiceMailCount = r14;
        goto L_0x01df;
    L_0x0234:
        r14 = 0;
        r0 = r17;
        r0.mIsMwi = r14;
        r14 = "SmsMessage";
        r15 = new java.lang.StringBuilder;
        r15.<init>();
        r16 = "MWI in DCS for fax/email/other: ";
        r15 = r15.append(r16);
        r0 = r17;
        r0 = r0.mDataCodingScheme;
        r16 = r0;
        r0 = r16;
        r0 = r0 & 255;
        r16 = r0;
        r15 = r15.append(r16);
        r15 = r15.toString();
        android.telephony.Rlog.w(r14, r15);
        goto L_0x000e;
    L_0x025f:
        r0 = r17;
        r14 = r0.mDataCodingScheme;
        r14 = r14 & 192;
        r15 = 128; // 0x80 float:1.794E-43 double:6.32E-322;
        if (r14 != r15) goto L_0x0292;
    L_0x0269:
        r14 = 1;
        unsupportedDatacodingScheme = r14;
        r14 = "SmsMessage";
        r15 = new java.lang.StringBuilder;
        r15.<init>();
        r16 = "5 - Unsupported SMS data coding scheme ";
        r15 = r15.append(r16);
        r0 = r17;
        r0 = r0.mDataCodingScheme;
        r16 = r0;
        r0 = r16;
        r0 = r0 & 255;
        r16 = r0;
        r15 = r15.append(r16);
        r15 = r15.toString();
        android.telephony.Rlog.w(r14, r15);
        goto L_0x000e;
    L_0x0292:
        r14 = "SmsMessage";
        r15 = new java.lang.StringBuilder;
        r15.<init>();
        r16 = "3 - Unsupported SMS data coding scheme ";
        r15 = r15.append(r16);
        r0 = r17;
        r0 = r0.mDataCodingScheme;
        r16 = r0;
        r0 = r16;
        r0 = r0 & 255;
        r16 = r0;
        r15 = r15.append(r16);
        r15 = r15.toString();
        android.telephony.Rlog.w(r14, r15);
        r14 = 1;
        unsupportedDatacodingScheme = r14;
        goto L_0x000e;
    L_0x02bb:
        r14 = 0;
        goto L_0x0012;
    L_0x02be:
        r0 = r17;
        r14 = r0.mMwiDontStore;
        if (r14 != 0) goto L_0x0066;
    L_0x02c4:
        r0 = r17;
        r14 = r0.mDataCodingScheme;
        r14 = r14 & 240;
        r15 = 208; // 0xd0 float:2.91E-43 double:1.03E-321;
        if (r14 == r15) goto L_0x02d8;
    L_0x02ce:
        r0 = r17;
        r14 = r0.mDataCodingScheme;
        r14 = r14 & 240;
        r15 = 224; // 0xe0 float:3.14E-43 double:1.107E-321;
        if (r14 != r15) goto L_0x02e0;
    L_0x02d8:
        r0 = r17;
        r14 = r0.mDataCodingScheme;
        r14 = r14 & 3;
        if (r14 == 0) goto L_0x0066;
    L_0x02e0:
        r14 = 1;
        r0 = r17;
        r0.mMwiDontStore = r14;
        goto L_0x0066;
    L_0x02e7:
        r14 = 0;
        r0 = r17;
        r0.mMwiSense = r14;
        goto L_0x0079;
    L_0x02ee:
        r14 = "SmsMessage";
        r15 = new java.lang.StringBuilder;
        r15.<init>();
        r16 = "TP_UDH fax/email/extended msg/multisubscriber profile. Msg Ind = ";
        r15 = r15.append(r16);
        r15 = r15.append(r9);
        r15 = r15.toString();
        android.telephony.Rlog.w(r14, r15);
        goto L_0x0042;
    L_0x0308:
        switch(r5) {
            case 0: goto L_0x0335;
            case 1: goto L_0x0359;
            case 2: goto L_0x033b;
            case 3: goto L_0x037a;
            case 4: goto L_0x03d2;
            default: goto L_0x030b;
        };
    L_0x030b:
        r0 = r17;
        r14 = r0.mMessageBody;
        if (r14 == 0) goto L_0x032c;
    L_0x0311:
        r0 = r17;
        r14 = r0.mMessageBody;
        r15 = "\r\n";
        r16 = "\n";
        r14 = r14.replace(r15, r16);
        r15 = 13;
        r16 = 10;
        r14 = r14.replace(r15, r16);
        r0 = r17;
        r0.mMessageBody = r14;
        r17.parseMessageBody();
    L_0x032c:
        if (r6 != 0) goto L_0x03de;
    L_0x032e:
        r14 = com.android.internal.telephony.SmsConstants.MessageClass.UNKNOWN;
        r0 = r17;
        r0.messageClass = r14;
    L_0x0334:
        return;
    L_0x0335:
        r14 = 0;
        r0 = r17;
        r0.mMessageBody = r14;
        goto L_0x030b;
    L_0x033b:
        r10 = android.content.res.Resources.getSystem();
        r14 = 17956986; // 0x112007a float:2.6816307E-38 double:8.87193E-317;
        r14 = r10.getBoolean(r14);
        if (r14 == 0) goto L_0x0353;
    L_0x0348:
        r0 = r18;
        r14 = r0.getUserDataGSM8bit(r4);
        r0 = r17;
        r0.mMessageBody = r14;
        goto L_0x030b;
    L_0x0353:
        r14 = 0;
        r0 = r17;
        r0.mMessageBody = r14;
        goto L_0x030b;
    L_0x0359:
        if (r19 == 0) goto L_0x0375;
    L_0x035b:
        r0 = r17;
        r14 = r0.mUserDataHeader;
        r14 = r14.languageTable;
        r15 = r14;
    L_0x0362:
        if (r19 == 0) goto L_0x0378;
    L_0x0364:
        r0 = r17;
        r14 = r0.mUserDataHeader;
        r14 = r14.languageShiftTable;
    L_0x036a:
        r0 = r18;
        r14 = r0.getUserDataGSM7Bit(r4, r15, r14);
        r0 = r17;
        r0.mMessageBody = r14;
        goto L_0x030b;
    L_0x0375:
        r14 = 0;
        r15 = r14;
        goto L_0x0362;
    L_0x0378:
        r14 = 0;
        goto L_0x036a;
    L_0x037a:
        r13 = r18.getUserData();
        r12 = r13.length;
        if (r12 <= 0) goto L_0x03c6;
    L_0x0381:
        r14 = r12 + -2;
        r14 = r13[r14];
        r14 = r14 & 255;
        r14 = r14 << 8;
        r3 = (char) r14;
        r14 = r12 + -1;
        r14 = r13[r14];
        r14 = r14 & 255;
        r14 = (char) r14;
        r14 = r14 | r3;
        r3 = (char) r14;
        r14 = 55357; // 0xd83d float:7.7572E-41 double:2.735E-319;
        if (r3 == r14) goto L_0x039d;
    L_0x0398:
        r14 = 55356; // 0xd83c float:7.757E-41 double:2.73495E-319;
        if (r3 != r14) goto L_0x03c6;
    L_0x039d:
        r14 = "SmsMessage";
        r15 = "found emoji";
        android.telephony.Rlog.d(r14, r15);
        r14 = 2;
        r14 = new byte[r14];
        r0 = r17;
        r0.mlastByte = r14;
        r0 = r17;
        r14 = r0.mlastByte;
        r15 = 0;
        r16 = r12 + -2;
        r16 = r13[r16];
        r14[r15] = r16;
        r0 = r17;
        r14 = r0.mlastByte;
        r15 = 1;
        r16 = r12 + -1;
        r16 = r13[r16];
        r14[r15] = r16;
        r14 = 1;
        r0 = r17;
        r0.mIsfourBytesUnicode = r14;
    L_0x03c6:
        r0 = r18;
        r14 = r0.getUserDataUCS2(r4);
        r0 = r17;
        r0.mMessageBody = r14;
        goto L_0x030b;
    L_0x03d2:
        r0 = r18;
        r14 = r0.getUserDataKSC5601(r4);
        r0 = r17;
        r0.mMessageBody = r14;
        goto L_0x030b;
    L_0x03de:
        r0 = r17;
        r14 = r0.mDataCodingScheme;
        r14 = r14 & 3;
        switch(r14) {
            case 0: goto L_0x03e9;
            case 1: goto L_0x03f1;
            case 2: goto L_0x03f9;
            case 3: goto L_0x0401;
            default: goto L_0x03e7;
        };
    L_0x03e7:
        goto L_0x0334;
    L_0x03e9:
        r14 = com.android.internal.telephony.SmsConstants.MessageClass.CLASS_0;
        r0 = r17;
        r0.messageClass = r14;
        goto L_0x0334;
    L_0x03f1:
        r14 = com.android.internal.telephony.SmsConstants.MessageClass.CLASS_1;
        r0 = r17;
        r0.messageClass = r14;
        goto L_0x0334;
    L_0x03f9:
        r14 = com.android.internal.telephony.SmsConstants.MessageClass.CLASS_2;
        r0 = r17;
        r0.messageClass = r14;
        goto L_0x0334;
    L_0x0401:
        r14 = com.android.internal.telephony.SmsConstants.MessageClass.CLASS_3;
        r0 = r17;
        r0.messageClass = r14;
        goto L_0x0334;
        */
        throw new UnsupportedOperationException("Method not decompiled: com.android.internal.telephony.gsm.SmsMessage.parseUserData(com.android.internal.telephony.gsm.SmsMessage$PduParser, boolean):void");
    }

    public MessageClass getMessageClass() {
        return this.messageClass;
    }

    public int getMessageIdentifier() {
        return 0;
    }

    public static TextEncodingDetails calculateLength(CharSequence msgBody, boolean use7bitOnly, int encodingType) {
        TextEncodingDetails ted = new TextEncodingDetails();
        if (encodingType == 1) {
            ted = null;
        } else if (encodingType == 0) {
            ted = GsmAlphabet.countGsmSeptets(msgBody, true);
        } else {
            ted = GsmAlphabet.countGsmSeptets(msgBody, use7bitOnly);
        }
        if (ted == null) {
            ted = new TextEncodingDetails();
            int octets = msgBody.length() * 2;
            ted.codeUnitCount = msgBody.length();
            if (octets > 140) {
                ted.msgCount = (octets + 133) / 134;
                ted.codeUnitsRemaining = ((ted.msgCount * 134) - octets) / 2;
            } else {
                ted.msgCount = 1;
                ted.codeUnitsRemaining = (140 - octets) / 2;
            }
            ted.codeUnitSize = 3;
        }
        return ted;
    }

    public static TextEncodingDetails calculateLengthWithEmail(CharSequence msgBody, boolean use7bitOnly, int maxEmailLen) {
        TextEncodingDetails ted = GsmAlphabet.CountGsmSeptetsWithEmail(msgBody, use7bitOnly, maxEmailLen);
        if (ted == null) {
            ted = new TextEncodingDetails();
            maxEmailLen *= 2;
            int maxLenPerSMS = maxEmailLen > 0 ? (140 - maxEmailLen) - 1 : 140;
            int maxLenPerSMSWithHeader = maxEmailLen > 0 ? (134 - maxEmailLen) - 1 : 134;
            int octets = msgBody.length() * 2;
            ted.codeUnitCount = msgBody.length();
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

    public static TextEncodingDetails calculateLengthWithEmail(CharSequence msgBody, boolean use7bitOnly, int encodingType, int maxEmailLen) {
        TextEncodingDetails ted = new TextEncodingDetails();
        if (encodingType == 1) {
            ted = null;
        } else if (encodingType == 0) {
            ted = GsmAlphabet.CountGsmSeptetsWithEmail(msgBody, true, maxEmailLen);
        } else {
            ted = GsmAlphabet.CountGsmSeptetsWithEmail(msgBody, use7bitOnly, maxEmailLen);
        }
        if (ted == null) {
            ted = new TextEncodingDetails();
            maxEmailLen *= 2;
            int maxLenPerSMS = maxEmailLen > 0 ? (140 - maxEmailLen) - 1 : 140;
            int maxLenPerSMSWithHeader = maxEmailLen > 0 ? (134 - maxEmailLen) - 1 : 134;
            int octets = msgBody.length() * 2;
            ted.codeUnitCount = msgBody.length();
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

    boolean isUsimDataDownload() {
        return this.messageClass == MessageClass.CLASS_2 && (this.mProtocolIdentifier == 127 || this.mProtocolIdentifier == 124);
    }

    public int getNumOfVoicemails() {
        if (!this.mIsMwi && isCphsMwiMessage()) {
            if (this.mOriginatingAddress == null || !((GsmSmsAddress) this.mOriginatingAddress).isCphsVoiceMessageSet()) {
                this.mVoiceMailCount = 0;
            } else {
                this.mVoiceMailCount = 255;
            }
            Rlog.v(LOG_TAG, "CPHS voice mail message");
        }
        return this.mVoiceMailCount;
    }

    public int getMessagePriority() {
        return 0;
    }

    private static ByteArrayOutputStream getSimSubmitPduHead(String scAddress, String Address, byte mtiByte, SubmitPdu ret) {
        int i;
        ByteArrayOutputStream bo = new ByteArrayOutputStream(PduHeaders.RECOMMENDED_RETRIEVAL_MODE);
        if (scAddress == null) {
            ret.encodedScAddress = null;
        } else {
            ret.encodedScAddress = PhoneNumberUtils.networkPortionToCalledPartyBCDWithLength(scAddress);
        }
        bo.write(mtiByte);
        bo.write(0);
        byte[] daBytes = PhoneNumberUtils.networkPortionToCalledPartyBCD(Address);
        int length = (daBytes.length - 1) * 2;
        if ((daBytes[daBytes.length - 1] & 240) == 240) {
            i = 1;
        } else {
            i = 0;
        }
        bo.write(length - i);
        bo.write(daBytes, 0, daBytes.length);
        bo.write(0);
        return bo;
    }

    public static SubmitPdu getSimSubmitPdu(String scAddress, String Address, String message, byte[] header) {
        if (message == null || Address == null) {
            return null;
        }
        byte[] userData;
        TextEncodingDetails ted = GsmAlphabet.countGsmSeptets(message, false);
        if (ted != null && (ted.languageTable > 0 || ted.languageShiftTable > 0)) {
            Rlog.d(LOG_TAG, "getSimSubmitPdu: ted.languageTable = " + ted.languageTable);
            Rlog.d(LOG_TAG, "getSimSubmitPdu: ted.languageShiftTable = " + ted.languageShiftTable);
            SmsHeader smsHeader = new SmsHeader();
            smsHeader.languageTable = ted.languageTable;
            smsHeader.languageShiftTable = ted.languageShiftTable;
            header = SmsHeader.toByteArray(smsHeader);
        }
        SubmitPdu ret = new SubmitPdu();
        ByteArrayOutputStream bo = getSimSubmitPduHead(scAddress, Address, (byte) ((header != null ? 64 : 0) | 1), ret);
        if (ted != null) {
            try {
                userData = GsmAlphabet.stringToGsm7BitPackedWithHeader(message, header, ted.languageTable, ted.languageShiftTable);
            } catch (EncodeException e) {
                try {
                    byte[] textPart = message.getBytes("utf-16be");
                    if (header != null) {
                        userData = new byte[(header.length + textPart.length)];
                        System.arraycopy(header, 0, userData, 0, header.length);
                        System.arraycopy(textPart, 0, userData, header.length, textPart.length);
                    } else {
                        userData = textPart;
                    }
                    if (userData.length > 140) {
                        return null;
                    }
                    bo.write(8);
                    bo.write(userData.length);
                    bo.write(userData, 0, userData.length);
                } catch (UnsupportedEncodingException uex) {
                    Rlog.e(LOG_TAG, "Implausible UnsupportedEncodingException ", uex);
                    return null;
                }
            }
        }
        userData = GsmAlphabet.stringToGsm7BitPackedWithHeader(message, header, 0, 0);
        if ((userData[0] & 255) > 160) {
            return null;
        }
        bo.write(0);
        bo.write(userData, 0, userData.length);
        ret.encodedMessage = bo.toByteArray();
        return ret;
    }

    private static int decToBcd(int digit) {
        return ((digit % 10) * 10) + (digit / 10);
    }

    public static byte[] ConvertDateStringToBCD(String mDate) {
        String timestamp;
        Rlog.d(LOG_TAG, "date : " + mDate);
        String year = mDate.substring(0, 2);
        String month = mDate.substring(2, 4);
        String day = mDate.substring(4, 6);
        String hour = mDate.substring(6, 8);
        String min = mDate.substring(8, 10);
        String sec = mDate.substring(10, 12);
        Rlog.d(LOG_TAG, "1. year:" + year + " month:" + month + " day:" + day + " hour:" + hour + " min:" + min + " sec:" + sec);
        mYear = decToBcd(Integer.parseInt(year));
        mMonth = decToBcd(Integer.parseInt(month));
        mDay = decToBcd(Integer.parseInt(day));
        mHour = decToBcd(Integer.parseInt(hour));
        mMin = decToBcd(Integer.parseInt(min));
        mSec = decToBcd(Integer.parseInt(sec));
        mTimezone = 0;
        long tzOffset = (long) TimeZone.getDefault().getOffset(System.currentTimeMillis());
        Rlog.d(LOG_TAG, "timezeone" + tzOffset);
        long temp;
        if (tzOffset < 0) {
            temp = (-tzOffset) / 900000;
            Rlog.d(LOG_TAG, "timezeone->temp" + temp);
            int temp2 = decToBcd((int) temp);
            mTimezone = ((temp2 / 10) << 4) + ((temp2 % 10) + 8);
            Rlog.d(LOG_TAG, "mTimezone" + mTimezone);
            Rlog.d(LOG_TAG, "2. year:" + mYear + " month:" + mMonth + " day:" + mDay + " hour:" + mHour + " min:" + mMin + " sec:" + mSec);
            if (Locale.getDefault().getLanguage().equals("ar")) {
                Rlog.d(LOG_TAG, "This is timestamp in Arabic.");
                timestamp = String.format(Locale.US, "%02d%02d%02d%02d%02d%02d%02x", new Object[]{Integer.valueOf(mYear), Integer.valueOf(mMonth), Integer.valueOf(mDay), Integer.valueOf(mHour), Integer.valueOf(mMin), Integer.valueOf(mSec), Integer.valueOf(mTimezone)});
            } else if (Locale.getDefault().getLanguage().equals("fa")) {
                Rlog.d(LOG_TAG, "This is timestamp in Farsi.");
                timestamp = String.format(Locale.US, "%02d%02d%02d%02d%02d%02d%02x", new Object[]{Integer.valueOf(mYear), Integer.valueOf(mMonth), Integer.valueOf(mDay), Integer.valueOf(mHour), Integer.valueOf(mMin), Integer.valueOf(mSec), Integer.valueOf(mTimezone)});
            } else {
                timestamp = String.format("%02d%02d%02d%02d%02d%02d%02x", new Object[]{Integer.valueOf(mYear), Integer.valueOf(mMonth), Integer.valueOf(mDay), Integer.valueOf(mHour), Integer.valueOf(mMin), Integer.valueOf(mSec), Integer.valueOf(mTimezone)});
            }
        } else {
            temp = tzOffset / 900000;
            Rlog.d(LOG_TAG, "timezeone->temp" + temp);
            mTimezone = decToBcd((int) temp);
            Rlog.d(LOG_TAG, "mTimezone" + mTimezone);
            Rlog.d(LOG_TAG, "2. year:" + mYear + " month:" + mMonth + " day:" + mDay + " hour:" + mHour + " min:" + mMin + " sec:" + mSec);
            if (Locale.getDefault().getLanguage().equals("ar")) {
                Rlog.d(LOG_TAG, "This is timestamp in Arabic.");
                timestamp = String.format(Locale.US, "%02d%02d%02d%02d%02d%02d%02d", new Object[]{Integer.valueOf(mYear), Integer.valueOf(mMonth), Integer.valueOf(mDay), Integer.valueOf(mHour), Integer.valueOf(mMin), Integer.valueOf(mSec), Integer.valueOf(mTimezone)});
            } else if (Locale.getDefault().getLanguage().equals("fa")) {
                Rlog.d(LOG_TAG, "This is timestamp in Farsi.");
                timestamp = String.format(Locale.US, "%02d%02d%02d%02d%02d%02d%02d", new Object[]{Integer.valueOf(mYear), Integer.valueOf(mMonth), Integer.valueOf(mDay), Integer.valueOf(mHour), Integer.valueOf(mMin), Integer.valueOf(mSec), Integer.valueOf(mTimezone)});
            } else {
                timestamp = String.format("%02d%02d%02d%02d%02d%02d%02d", new Object[]{Integer.valueOf(mYear), Integer.valueOf(mMonth), Integer.valueOf(mDay), Integer.valueOf(mHour), Integer.valueOf(mMin), Integer.valueOf(mSec), Integer.valueOf(mTimezone)});
            }
        }
        Rlog.d(LOG_TAG, "timestamp string: " + timestamp);
        return IccUtils.hexStringToBytes(timestamp);
    }

    private static ByteArrayOutputStream getSimDeliverPduHead(String scAddress, String Address, byte mtiByte, DeliverPdu ret) {
        int i = 1;
        ByteArrayOutputStream bo = new ByteArrayOutputStream(PduHeaders.RECOMMENDED_RETRIEVAL_MODE);
        if (scAddress == null) {
            ret.encodedScAddress = null;
        } else {
            ret.encodedScAddress = PhoneNumberUtils.networkPortionToCalledPartyBCDWithLength(scAddress);
        }
        bo.write(mtiByte);
        char c = Address.charAt(0);
        byte[] daBytes;
        if (true && PhoneNumberUtils.isDialable(c)) {
            Rlog.e(LOG_TAG, "Address is Numeric.");
            daBytes = PhoneNumberUtils.networkPortionToCalledPartyBCD(Address);
            if (daBytes == null) {
                bo.write(0);
            } else {
                int length = (daBytes.length - 1) * 2;
                if ((daBytes[daBytes.length - 1] & 240) != 240) {
                    i = 0;
                }
                bo.write(length - i);
                bo.write(daBytes, 0, daBytes.length);
            }
        } else {
            Rlog.e(LOG_TAG, "Address is Alphabetic.");
            try {
                daBytes = GsmAlphabet.stringToGsm7BitPacked(Address);
                bo.write((daBytes.length - 1) * 2);
                bo.write(BerTlv.BER_PROACTIVE_COMMAND_TAG);
                bo.write(daBytes, 1, daBytes.length - 1);
            } catch (EncodeException ex) {
                Rlog.e(LOG_TAG, "Implausible UnsupportedEncodingException ", ex);
                return null;
            }
        }
        bo.write(0);
        return bo;
    }

    public static DeliverPdu getSimDeliverPdu(String scAddress, String Address, String message, String date, byte[] header) {
        byte[] userData;
        if (message == null || Address == null) {
            return null;
        }
        TextEncodingDetails ted = GsmAlphabet.countGsmSeptets(message, false);
        if (ted != null && (ted.languageTable > 0 || ted.languageShiftTable > 0)) {
            Rlog.d(LOG_TAG, "getSimDeliverPdu: ted.languageTable = " + ted.languageTable);
            Rlog.d(LOG_TAG, "getSimDeliverPdu: ted.languageShiftTable = " + ted.languageShiftTable);
            SmsHeader smsHeader = new SmsHeader();
            smsHeader.languageTable = ted.languageTable;
            smsHeader.languageShiftTable = ted.languageShiftTable;
            header = SmsHeader.toByteArray(smsHeader);
        }
        DeliverPdu ret = new DeliverPdu();
        ByteArrayOutputStream bo = null;
        byte[] bArr;
        try {
            bo = getSimDeliverPduHead(scAddress, Address, (byte) ((header != null ? 64 : 0) | 0), ret);
            if (ted != null) {
                userData = GsmAlphabet.stringToGsm7BitPackedWithHeader(message, header, ted.languageTable, ted.languageShiftTable);
            } else {
                userData = GsmAlphabet.stringToGsm7BitPackedWithHeader(message, header, 0, 0);
            }
            if ((userData[0] & 255) > 160) {
                return null;
            }
            bo.write(0);
            bArr = new byte[7];
            bArr = ConvertDateStringToBCD(date);
            bo.write(bArr, 0, bArr.length);
            bo.write(userData, 0, userData.length);
            ret.encodedMessage = bo.toByteArray();
            return ret;
        } catch (EncodeException e) {
            try {
                byte[] textPart = message.getBytes("utf-16be");
                if (header != null) {
                    userData = new byte[(header.length + textPart.length)];
                    System.arraycopy(header, 0, userData, 0, header.length);
                    System.arraycopy(textPart, 0, userData, header.length, textPart.length);
                } else {
                    userData = textPart;
                }
                if (userData.length > 140) {
                    return null;
                }
                bo.write(8);
                bArr = new byte[7];
                bArr = ConvertDateStringToBCD(date);
                bo.write(bArr, 0, bArr.length);
                bo.write(userData.length);
                bo.write(userData, 0, userData.length);
            } catch (UnsupportedEncodingException uex) {
                Rlog.e(LOG_TAG, "Implausible UnsupportedEncodingException ", uex);
                return null;
            }
        }
    }
}
