package com.android.internal.telephony.cat;

import android.content.res.Resources;
import android.content.res.Resources.NotFoundException;
import android.os.SystemProperties;
import com.android.internal.telephony.EncodeException;
import com.android.internal.telephony.GsmAlphabet;
import com.android.internal.telephony.cat.Duration.TimeUnit;
import com.android.internal.telephony.cdma.sms.BearerData;
import com.android.internal.telephony.cdma.sms.CdmaSmsAddress;
import com.android.internal.telephony.cdma.sms.SmsEnvelope;
import com.android.internal.telephony.uicc.IccUtils;
import com.android.internal.util.BitwiseInputStream;
import com.android.internal.util.BitwiseInputStream.AccessException;
import com.google.android.mms.pdu.PduHeaders;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

abstract class ValueParser {

    private static class CodingException extends Exception {
        public CodingException(String s) {
            super(s);
        }
    }

    ValueParser() {
    }

    static CommandDetails retrieveCommandDetails(ComprehensionTlv ctlv) throws ResultException {
        CommandDetails cmdDet = new CommandDetails();
        byte[] rawValue = ctlv.getRawValue();
        int valueIndex = ctlv.getValueIndex();
        try {
            cmdDet.compRequired = ctlv.isComprehensionRequired();
            cmdDet.commandNumber = rawValue[valueIndex] & 255;
            cmdDet.typeOfCommand = rawValue[valueIndex + 1] & 255;
            cmdDet.commandQualifier = rawValue[valueIndex + 2] & 255;
            return cmdDet;
        } catch (IndexOutOfBoundsException e) {
            throw new ResultException(ResultCode.CMD_DATA_NOT_UNDERSTOOD);
        }
    }

    static DeviceIdentities retrieveDeviceIdentities(ComprehensionTlv ctlv) throws ResultException {
        DeviceIdentities devIds = new DeviceIdentities();
        byte[] rawValue = ctlv.getRawValue();
        int valueIndex = ctlv.getValueIndex();
        try {
            devIds.sourceId = rawValue[valueIndex] & 255;
            devIds.destinationId = rawValue[valueIndex + 1] & 255;
            return devIds;
        } catch (IndexOutOfBoundsException e) {
            throw new ResultException(ResultCode.REQUIRED_VALUES_MISSING);
        }
    }

    static Duration retrieveDuration(ComprehensionTlv ctlv) throws ResultException {
        TimeUnit timeUnit = TimeUnit.SECOND;
        byte[] rawValue = ctlv.getRawValue();
        int valueIndex = ctlv.getValueIndex();
        try {
            return new Duration(rawValue[valueIndex + 1] & 255, TimeUnit.values()[rawValue[valueIndex] & 255]);
        } catch (IndexOutOfBoundsException e) {
            throw new ResultException(ResultCode.CMD_DATA_NOT_UNDERSTOOD);
        }
    }

    static Item retrieveItem(ComprehensionTlv ctlv) throws ResultException {
        byte[] rawValue = ctlv.getRawValue();
        int valueIndex = ctlv.getValueIndex();
        int length = ctlv.getLength();
        if (length == 0) {
            return null;
        }
        try {
            return new Item(rawValue[valueIndex] & 255, IccUtils.adnStringFieldToString(rawValue, valueIndex + 1, length - 1));
        } catch (IndexOutOfBoundsException e) {
            throw new ResultException(ResultCode.CMD_DATA_NOT_UNDERSTOOD);
        }
    }

    static int retrieveItemId(ComprehensionTlv ctlv) throws ResultException {
        try {
            return ctlv.getRawValue()[ctlv.getValueIndex()] & 255;
        } catch (IndexOutOfBoundsException e) {
            throw new ResultException(ResultCode.CMD_DATA_NOT_UNDERSTOOD);
        }
    }

    static IconId retrieveIconId(ComprehensionTlv ctlv) throws ResultException {
        IconId id = new IconId();
        byte[] rawValue = ctlv.getRawValue();
        int valueIndex = ctlv.getValueIndex();
        int valueIndex2 = valueIndex + 1;
        try {
            id.selfExplanatory = (rawValue[valueIndex] & 255) == 0;
            id.recordNumber = rawValue[valueIndex2] & 255;
            return id;
        } catch (IndexOutOfBoundsException e) {
            throw new ResultException(ResultCode.CMD_DATA_NOT_UNDERSTOOD);
        }
    }

    static ItemsIconId retrieveItemsIconId(ComprehensionTlv ctlv) throws ResultException {
        CatLog.m2d("ValueParser", "retrieveItemsIconId:");
        ItemsIconId id = new ItemsIconId();
        byte[] rawValue = ctlv.getRawValue();
        int valueIndex = ctlv.getValueIndex();
        int numOfItems = ctlv.getLength() - 1;
        id.recordNumbers = new int[numOfItems];
        int valueIndex2 = valueIndex + 1;
        try {
            id.selfExplanatory = (rawValue[valueIndex] & 255) == 0;
            int i = 0;
            while (i < numOfItems) {
                int index = i + 1;
                valueIndex = valueIndex2 + 1;
                try {
                    id.recordNumbers[i] = rawValue[valueIndex2];
                    i = index;
                    valueIndex2 = valueIndex;
                } catch (IndexOutOfBoundsException e) {
                }
            }
            return id;
        } catch (IndexOutOfBoundsException e2) {
            valueIndex = valueIndex2;
            throw new ResultException(ResultCode.CMD_DATA_NOT_UNDERSTOOD);
        }
    }

    static List<TextAttribute> retrieveTextAttribute(ComprehensionTlv ctlv) throws ResultException {
        ArrayList<TextAttribute> lst = new ArrayList();
        byte[] rawValue = ctlv.getRawValue();
        int valueIndex = ctlv.getValueIndex();
        int length = ctlv.getLength();
        if (length == 0) {
            return null;
        }
        int itemCount = length / 4;
        int i = 0;
        while (i < itemCount) {
            try {
                int start = rawValue[valueIndex] & 255;
                int textLength = rawValue[valueIndex + 1] & 255;
                int format = rawValue[valueIndex + 2] & 255;
                int colorValue = rawValue[valueIndex + 3] & 255;
                TextAlignment align = TextAlignment.fromInt(format & 3);
                FontSize size = FontSize.fromInt((format >> 2) & 3);
                if (size == null) {
                    size = FontSize.NORMAL;
                }
                lst.add(new TextAttribute(start, textLength, align, size, (format & 16) != 0, (format & 32) != 0, (format & 64) != 0, (format & 128) != 0, TextColor.fromInt(colorValue)));
                i++;
                valueIndex += 4;
            } catch (IndexOutOfBoundsException e) {
                throw new ResultException(ResultCode.CMD_DATA_NOT_UNDERSTOOD);
            }
        }
        return lst;
    }

    static String retrieveAlphaId(ComprehensionTlv ctlv) throws ResultException {
        if (ctlv != null) {
            byte[] rawValue = ctlv.getRawValue();
            int valueIndex = ctlv.getValueIndex();
            int length = ctlv.getLength();
            if (length != 0) {
                try {
                    return IccUtils.adnStringFieldToString(rawValue, valueIndex, length);
                } catch (IndexOutOfBoundsException e) {
                    throw new ResultException(ResultCode.CMD_DATA_NOT_UNDERSTOOD);
                }
            }
            CatLog.m2d("ValueParser", "Alpha Id length=" + length);
            return null;
        }
        boolean noAlphaUsrCnf;
        try {
            noAlphaUsrCnf = Resources.getSystem().getBoolean(17956974);
        } catch (NotFoundException e2) {
            noAlphaUsrCnf = false;
        }
        if (noAlphaUsrCnf) {
            return null;
        }
        return "Default Message";
    }

    static String retrieveTextString(ComprehensionTlv ctlv) throws ResultException {
        byte[] rawValue = ctlv.getRawValue();
        int valueIndex = ctlv.getValueIndex();
        int textLen = ctlv.getLength();
        if (textLen == 0) {
            return null;
        }
        textLen--;
        try {
            String text;
            byte codingScheme = (byte) (rawValue[valueIndex] & 12);
            if (codingScheme == (byte) 0) {
                text = GsmAlphabet.gsm7BitPackedToString(rawValue, valueIndex + 1, (textLen * 8) / 7);
            } else if (codingScheme == (byte) 4) {
                text = GsmAlphabet.gsm8BitUnpackedToString(rawValue, valueIndex + 1, textLen);
            } else if (codingScheme == (byte) 8) {
                text = new String(rawValue, valueIndex + 1, textLen, "UTF-16");
            } else {
                throw new ResultException(ResultCode.CMD_DATA_NOT_UNDERSTOOD);
            }
            return text;
        } catch (IndexOutOfBoundsException e) {
            throw new ResultException(ResultCode.CMD_DATA_NOT_UNDERSTOOD);
        } catch (UnsupportedEncodingException e2) {
            throw new ResultException(ResultCode.CMD_DATA_NOT_UNDERSTOOD);
        }
    }

    static String retrieveAddress(ComprehensionTlv ctlv) throws ResultException {
        byte[] rawValue = ctlv.getRawValue();
        int valueIndex = ctlv.getValueIndex();
        int length = ctlv.getLength();
        if (length == 0) {
            return null;
        }
        try {
            return IccUtils.SetupCallbcdToString(rawValue, valueIndex, length);
        } catch (IndexOutOfBoundsException e) {
            throw new ResultException(ResultCode.CMD_DATA_NOT_UNDERSTOOD);
        }
    }

    static String retrieveSSstring(ComprehensionTlv ctlv) throws ResultException {
        byte[] rawValue = ctlv.getRawValue();
        int valueIndex = ctlv.getValueIndex();
        int length = ctlv.getLength();
        if (length == 0) {
            return null;
        }
        try {
            return IccUtils.SSbcdToString(rawValue, valueIndex, length);
        } catch (IndexOutOfBoundsException e) {
            throw new ResultException(ResultCode.CMD_DATA_NOT_UNDERSTOOD);
        }
    }

    static byte[] retrieveUSSDstring(ComprehensionTlv ctlv) throws ResultException {
        byte[] rawValue = ctlv.getRawValue();
        int valueIndex = ctlv.getValueIndex();
        int length = ctlv.getLength();
        int endOfUssdString = (valueIndex + length) - 1;
        if (length == 0) {
            return null;
        }
        byte[] ussdString = new byte[length];
        int i = 0;
        int valueIndex2 = valueIndex;
        while (valueIndex2 <= endOfUssdString) {
            int i2 = i + 1;
            valueIndex = valueIndex2 + 1;
            ussdString[i] = rawValue[valueIndex2];
            i = i2;
            valueIndex2 = valueIndex;
        }
        valueIndex = valueIndex2;
        return ussdString;
    }

    static byte[] retrieveDTMFstring(ComprehensionTlv ctlv) throws ResultException {
        byte[] rawValue = ctlv.getRawValue();
        int valueIndex = ctlv.getValueIndex();
        int length = ctlv.getLength();
        if (length == 0) {
            return null;
        }
        byte[] dtmfString = new byte[(length + 1)];
        dtmfString[0] = (byte) length;
        int i = 0;
        int valueIndex2 = valueIndex;
        while (i < length) {
            valueIndex = valueIndex2 + 1;
            dtmfString[i + 1] = rawValue[valueIndex2];
            i++;
            valueIndex2 = valueIndex;
        }
        valueIndex = valueIndex2;
        return dtmfString;
    }

    static String retrieveSMSCaddress(ComprehensionTlv ctlv) throws ResultException {
        byte[] rawValue = ctlv.getRawValue();
        int valueIndex = ctlv.getValueIndex();
        int length = ctlv.getLength();
        byte[] rawValueSmscaddress = new byte[(length + 1)];
        int i = 0;
        while (i < length + 1) {
            try {
                rawValueSmscaddress[i] = rawValue[(valueIndex - 1) + i];
                i++;
            } catch (IndexOutOfBoundsException e) {
                throw new ResultException(ResultCode.CMD_DATA_NOT_UNDERSTOOD);
            }
        }
        if (length != 0) {
            return IccUtils.bytesToHexString(rawValueSmscaddress);
        }
        throw new ResultException(ResultCode.CMD_DATA_NOT_UNDERSTOOD);
    }

    static String retrieveSMSTPDU(ComprehensionTlv ctlv, boolean ispacking_req) throws ResultException {
        int destaddlen;
        byte[] rawPdu;
        int destaddrlen;
        byte[] rawValue = ctlv.getRawValue();
        int valueIndex = ctlv.getValueIndex();
        int length = ctlv.getLength();
        if (rawValue[valueIndex + 2] % 2 == 0) {
            destaddlen = rawValue[valueIndex + 2] / 2;
        } else {
            destaddlen = (rawValue[valueIndex + 2] + 1) / 2;
        }
        if (length == destaddlen + 6) {
            rawPdu = new byte[(length + 1)];
        } else {
            rawPdu = new byte[length];
        }
        int i = 0;
        while (i < length) {
            try {
                rawPdu[i] = rawValue[valueIndex + i];
                i++;
            } catch (IndexOutOfBoundsException e) {
                throw new ResultException(ResultCode.CMD_DATA_NOT_UNDERSTOOD);
            }
        }
        int tpvplength = 0;
        CatLog.m2d("ValueParser", "rawtpvp:" + rawPdu[0]);
        int tpvpvalue = rawPdu[0] & 24;
        CatLog.m2d("ValueParser", "tpvpvalue:" + tpvpvalue);
        if (tpvpvalue == 0) {
            tpvplength = 0;
        } else if (tpvpvalue == 16) {
            tpvplength = 1;
        } else if (tpvpvalue == 8) {
            tpvplength = 7;
        } else if (tpvpvalue == 24) {
            tpvplength = 7;
        }
        if (rawPdu[2] % 2 == 0) {
            destaddrlen = rawPdu[2] / 2;
        } else {
            destaddrlen = (rawPdu[2] + 1) / 2;
        }
        int packingUserDataIndex = ((((destaddrlen + 3) + 1) + 1) + tpvplength) + 1;
        try {
            int dcs = rawPdu[packingUserDataIndex - (tpvplength + 1)];
            CatLog.m2d("ValueParser", "SEND SMS DCS = " + dcs);
            String MCCMNC = SystemProperties.get("gsm.sim.operator.numeric", "");
            String SALES_CODE = SystemProperties.get("ro.csc.sales_code", "");
            if (ispacking_req && ((dcs & BearerData.RELATIVE_TIME_WEEKS_LIMIT) != 240 || "XXV".equals(SALES_CODE) || "GLB".equals(SALES_CODE) || "SMA".equals(SALES_CODE) || "XTC".equals(SALES_CODE) || "XTE".equals(SALES_CODE) || MCCMNC.startsWith("404") || MCCMNC.startsWith("510") || MCCMNC.startsWith("520") || MCCMNC.startsWith("405") || "51503".equals(MCCMNC) || "28602".equals(MCCMNC) || "20620".equals(MCCMNC))) {
                try {
                    int adjustedUserDatalen;
                    int packingUserDatalen = rawPdu[packingUserDataIndex] & 255;
                    int lengthtoCheck = length - (packingUserDataIndex + 1);
                    CatLog.m2d("ValueParser", "length to be checked:" + lengthtoCheck);
                    byte[] packinUserData;
                    int j;
                    String packinUserDataString;
                    byte[] packedUserData;
                    int packedUserDatalen;
                    int k;
                    if (lengthtoCheck >= packingUserDatalen) {
                        CatLog.m2d("ValueParser", "TPUDL_packingUserDatalen:" + packingUserDatalen);
                        packinUserData = new byte[packingUserDatalen];
                        j = 0;
                        while (j < packingUserDatalen) {
                            try {
                                packinUserData[j] = rawPdu[(packingUserDataIndex + 1) + j];
                                j++;
                            } catch (IndexOutOfBoundsException e2) {
                                throw new ResultException(ResultCode.CMD_DATA_NOT_UNDERSTOOD);
                            }
                        }
                        packinUserDataString = GsmAlphabet.gsm8BitUnpackedToString(packinUserData, 0, packingUserDatalen);
                        CatLog.m2d("ValueParser", "TPUDL_packingUserDatastring:" + packinUserDataString);
                        try {
                            if (packinUserDataString.length() > 160) {
                                String fullSizePackinUserDataString = packinUserDataString.substring(0, PduHeaders.REPLY_CHARGING_ID);
                                rawPdu[packingUserDataIndex] = (byte) (rawPdu[packingUserDataIndex] - (packinUserDataString.length() - 158));
                                packinUserDataString = fullSizePackinUserDataString;
                            }
                            packedUserData = GsmAlphabet.stringToGsm7BitPacked(packinUserDataString);
                            packedUserDatalen = packedUserData.length;
                            CatLog.m2d("ValueParser", "TPUDL_Packed user data len:" + packedUserDatalen);
                            for (k = 1; k < packedUserDatalen; k++) {
                                rawPdu[packingUserDataIndex + k] = packedUserData[k];
                            }
                            adjustedUserDatalen = length - (packingUserDatalen - (packedUserDatalen - 1));
                            CatLog.m2d("ValueParser", "TPUDL_Adjusted user data len:" + adjustedUserDatalen);
                        } catch (IndexOutOfBoundsException e3) {
                            throw new ResultException(ResultCode.CMD_DATA_NOT_UNDERSTOOD);
                        } catch (EncodeException e4) {
                            throw new ResultException(ResultCode.CMD_DATA_NOT_UNDERSTOOD);
                        }
                    }
                    packingUserDatalen = lengthtoCheck + 1;
                    CatLog.m2d("ValueParser", "packingUserDatalen:" + packingUserDatalen);
                    packinUserData = new byte[packingUserDatalen];
                    j = 0;
                    while (j < packingUserDatalen) {
                        try {
                            packinUserData[j] = rawPdu[packingUserDataIndex + j];
                            j++;
                        } catch (IndexOutOfBoundsException e5) {
                            throw new ResultException(ResultCode.CMD_DATA_NOT_UNDERSTOOD);
                        }
                    }
                    packinUserDataString = GsmAlphabet.gsm8BitUnpackedToString(packinUserData, 0, packingUserDatalen);
                    CatLog.m2d("ValueParser", "packingUserDatastring:" + packinUserDataString);
                    try {
                        packedUserData = GsmAlphabet.stringToGsm7BitPacked(packinUserDataString);
                        packedUserDatalen = packedUserData.length;
                        CatLog.m2d("ValueParser", "Packed user data len:" + packedUserDatalen);
                        for (k = 0; k < packedUserDatalen - 1; k++) {
                            rawPdu[packingUserDataIndex + k] = packedUserData[k + 1];
                        }
                        adjustedUserDatalen = length - (packingUserDatalen - (packedUserDatalen - 1));
                        CatLog.m2d("ValueParser", "Adjusted user data len:" + adjustedUserDatalen);
                    } catch (IndexOutOfBoundsException e6) {
                        throw new ResultException(ResultCode.CMD_DATA_NOT_UNDERSTOOD);
                    } catch (EncodeException e7) {
                        throw new ResultException(ResultCode.CMD_DATA_NOT_UNDERSTOOD);
                    }
                    CatLog.m2d("ValueParser", "Data coding scheme:" + rawPdu[packingUserDataIndex - (tpvplength + 1)]);
                    rawPdu[packingUserDataIndex - (tpvplength + 1)] = (byte) (rawPdu[packingUserDataIndex - (tpvplength + 1)] & 240);
                    byte[] packeddata = new byte[adjustedUserDatalen];
                    int l = 0;
                    while (l < adjustedUserDatalen) {
                        try {
                            packeddata[l] = rawPdu[l];
                            l++;
                        } catch (IndexOutOfBoundsException e8) {
                            throw new ResultException(ResultCode.CMD_DATA_NOT_UNDERSTOOD);
                        }
                    }
                    if (adjustedUserDatalen != 0) {
                        return IccUtils.bytesToHexString(packeddata);
                    }
                    throw new ResultException(ResultCode.CMD_DATA_NOT_UNDERSTOOD);
                } catch (IndexOutOfBoundsException e9) {
                    throw new ResultException(ResultCode.CMD_DATA_NOT_UNDERSTOOD);
                }
            } else if (length != 0) {
                return IccUtils.bytesToHexString(rawPdu);
            } else {
                throw new ResultException(ResultCode.CMD_DATA_NOT_UNDERSTOOD);
            }
        } catch (IndexOutOfBoundsException e10) {
            throw new ResultException(ResultCode.CMD_DATA_NOT_UNDERSTOOD);
        }
    }

    static String retrieveSMSTPDU_CDMA(ComprehensionTlv ctlv, boolean ispacking_req) throws ResultException {
        AccessException ex;
        CodingException ex2;
        byte[] rawValue = ctlv.getRawValue();
        int valueIndex = ctlv.getValueIndex();
        int length = ctlv.getLength();
        byte[] rawPdu = new byte[length];
        int i = 0;
        while (i < length) {
            try {
                rawPdu[i] = rawValue[valueIndex + i];
                i++;
            } catch (IndexOutOfBoundsException e) {
                throw new ResultException(ResultCode.CMD_DATA_NOT_UNDERSTOOD);
            }
        }
        int teleservicelen = 0;
        int destaddrlen = 0;
        CatLog.m2d("retrieveSMSTPDU", "rawPdu : " + IccUtils.bytesToHexString(rawPdu));
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream(100);
            DataOutputStream dataOutputStream = new DataOutputStream(baos);
            byte msgtype = rawPdu[0];
            CatLog.m2d("ValueParser", "teleserparaid:" + rawPdu[1]);
            if (rawPdu[1] == (byte) 0) {
                teleservicelen = rawPdu[2] & 255;
                dataOutputStream.writeInt((rawPdu[3] << 8) | rawPdu[4]);
            }
            dataOutputStream.writeInt(0);
            dataOutputStream.writeInt(0);
            int lenmsgteleser = (teleservicelen + 2) + 1;
            CatLog.m2d("ValueParser", "destaddrparaid:" + rawPdu[lenmsgteleser]);
            CatLog.m2d("ValueParser", "lenght to reach destaddrparamid:" + lenmsgteleser);
            if (rawPdu[lenmsgteleser] == (byte) 4) {
                String destAddrStr = new String();
                destaddrlen = rawPdu[lenmsgteleser + 1] & 255;
                byte[] destaddr = new byte[destaddrlen];
                System.arraycopy(rawPdu, (lenmsgteleser + 1) + 1, destaddr, 0, destaddr.length);
                CdmaSmsAddress addr = new CdmaSmsAddress();
                addr.numberOfDigits = (byte) destaddr.length;
                addr.origBytes = new byte[addr.numberOfDigits];
                System.arraycopy(destaddr, 0, addr.origBytes, 0, destaddr.length);
                CdmaSmsAddress addr1 = new CdmaSmsAddress();
                try {
                    BitwiseInputStream bitwiseInputStream = new BitwiseInputStream(addr.origBytes);
                    String destAddrStr2 = destAddrStr;
                    while (bitwiseInputStream.available() > 0) {
                        try {
                            int paramBytes = addr.numberOfDigits;
                            CatLog.m2d("ValueParser", "SMS PDU parsing :: paramBytes :: " + paramBytes);
                            addr1.digitMode = bitwiseInputStream.read(1);
                            addr1.numberMode = bitwiseInputStream.read(1);
                            byte fieldBits = (byte) 4;
                            byte consumedBits = (byte) 2;
                            if (addr1.digitMode == 1) {
                                addr1.ton = bitwiseInputStream.read(3);
                                addr1.numberPlan = bitwiseInputStream.read(4);
                                fieldBits = (byte) 8;
                                consumedBits = (byte) 9;
                            }
                            addr1.numberOfDigits = bitwiseInputStream.read(8);
                            int remainingBits = (paramBytes * 8) - ((byte) (consumedBits + 8));
                            int dataBits = addr1.numberOfDigits * fieldBits;
                            int paddingBits = remainingBits - dataBits;
                            if (remainingBits < dataBits) {
                                throw new CodingException("Originating_NUMBER subparam encoding size error (remainingBits " + remainingBits + ", dataBits " + dataBits + ", paddingBits " + paddingBits + ")");
                            }
                            addr1.origBytes = bitwiseInputStream.readByteArray(dataBits);
                            bitwiseInputStream.skip(paddingBits);
                            byte[] rawData = addr1.origBytes;
                            int numFields = addr1.numberOfDigits;
                            if (addr1.digitMode == 1) {
                                destAddrStr = new String(addr1.origBytes, 0, addr1.origBytes.length, "US-ASCII");
                            } else {
                                StringBuffer stringBuffer = new StringBuffer(numFields);
                                for (i = 0; i < numFields; i++) {
                                    int val = (rawData[i / 2] >>> (4 - ((i % 2) * 4))) & 15;
                                    if (val >= 1 && val <= 9) {
                                        stringBuffer.append(Integer.toString(val, 10));
                                    } else if (val == 10) {
                                        stringBuffer.append('0');
                                    } else if (val == 11) {
                                        stringBuffer.append('*');
                                    } else if (val == 12) {
                                        stringBuffer.append('#');
                                    } else if (val == 0) {
                                        stringBuffer.append('0');
                                    } else {
                                        throw new CodingException("invalid SMS address DTMF code (" + val + ")");
                                    }
                                }
                                destAddrStr = stringBuffer.toString();
                            }
                            destAddrStr2 = destAddrStr;
                        } catch (UnsupportedEncodingException e2) {
                            throw new CodingException("invalid SMS address ASCII code");
                        } catch (AccessException e3) {
                            ex = e3;
                            destAddrStr = destAddrStr2;
                        } catch (CodingException e4) {
                            ex2 = e4;
                            destAddrStr = destAddrStr2;
                        }
                    }
                    CatLog.m2d("ValueParser", "SMS Destination address: " + destAddrStr2);
                    CatLog.m2d("ValueParser", "SMS Destination address!!!: " + destAddrStr2);
                    CdmaSmsAddress destAddr = CdmaSmsAddress.parse(destAddrStr2);
                    if (destAddr == null) {
                        return null;
                    }
                    new SmsEnvelope().destAddress = destAddr;
                    CatLog.m2d("ValueParser", "SMS Destination destAddr!!!: " + destAddr);
                    destAddr.digitMode = addr1.digitMode;
                    dataOutputStream.write(destAddr.digitMode);
                    dataOutputStream.write(destAddr.numberMode);
                    dataOutputStream.write(destAddr.ton);
                    dataOutputStream.write(destAddr.numberPlan);
                    dataOutputStream.write(destAddr.numberOfDigits);
                    dataOutputStream.write(destAddr.origBytes, 0, destAddr.origBytes.length);
                    CatLog.m2d("ValueParser", "SMS Destination destAddr.digitMode: " + destAddr.digitMode);
                    CatLog.m2d("ValueParser", "SMS Destination destAddr.numberMode: " + destAddr.numberMode);
                    CatLog.m2d("ValueParser", "SMS Destination destAddr.ton: " + destAddr.ton);
                    CatLog.m2d("ValueParser", "SMS Destination destAddr.numberPlan: " + destAddr.numberPlan);
                    CatLog.m2d("ValueParser", "SMS Destination destAddr.origBytes.length: " + destAddr.origBytes.length);
                    CatLog.m2d("ValueParser", "SMS Destination destAddr.origBytes: " + destAddr.origBytes);
                } catch (AccessException e5) {
                    ex = e5;
                    CatLog.m2d("ValueParser", "destination address decode failed: " + ex);
                    throw new ResultException(ResultCode.CMD_DATA_NOT_UNDERSTOOD);
                } catch (CodingException e6) {
                    ex2 = e6;
                    CatLog.m2d("ValueParser", "destination address decode failed: " + ex2);
                    throw new ResultException(ResultCode.CMD_DATA_NOT_UNDERSTOOD);
                }
            }
            dataOutputStream.write(0);
            dataOutputStream.write(0);
            dataOutputStream.write(0);
            int lenmsgteledest = ((lenmsgteleser + 1) + 1) + destaddrlen;
            CatLog.m2d("ValueParser", "bearerdataparaid:" + rawPdu[lenmsgteledest]);
            CatLog.m2d("ValueParser", "lenght to reach Bearerdataparaid:" + lenmsgteledest);
            if (rawPdu[lenmsgteledest] == (byte) 8) {
                byte[] bearerdata = new byte[(rawPdu[lenmsgteledest + 1] & 255)];
                CatLog.m2d("ValueParser", "bearerdata length:" + bearerdata.length);
                System.arraycopy(rawPdu, (lenmsgteledest + 1) + 1, bearerdata, 0, bearerdata.length);
                dataOutputStream.write(bearerdata.length);
                dataOutputStream.write(bearerdata, 0, bearerdata.length);
            }
            dataOutputStream.close();
            String pdustr = IccUtils.bytesToHexString(baos.toByteArray());
            CatLog.m2d("ValueParser", " Pdu : " + pdustr);
            baos.close();
            return pdustr;
        } catch (IOException ex3) {
            CatLog.m2d("ValueParser", "creating SubmitPdu failed: " + ex3);
            throw new ResultException(ResultCode.CMD_DATA_NOT_UNDERSTOOD);
        } catch (IndexOutOfBoundsException e7) {
            throw new ResultException(ResultCode.CMD_DATA_NOT_UNDERSTOOD);
        } catch (NegativeArraySizeException ee) {
            CatLog.m2d("ValueParser", "creating SubmitPdu failed: " + ee);
            throw new ResultException(ResultCode.CMD_DATA_NOT_UNDERSTOOD);
        }
    }

    static String retrieveSMSTPDU_CDMA_Common(ComprehensionTlv ctlv, boolean ispacking_req) throws ResultException {
        byte[] rawValue = ctlv.getRawValue();
        int valueIndex = ctlv.getValueIndex();
        int length = ctlv.getLength();
        byte[] rawPdu = new byte[length];
        int i = 0;
        while (i < length) {
            try {
                rawPdu[i] = rawValue[valueIndex + i];
                i++;
            } catch (IndexOutOfBoundsException e) {
                throw new ResultException(ResultCode.CMD_DATA_NOT_UNDERSTOOD);
            }
        }
        int teleservicelen = 0;
        int rawDestAddrLen = 0;
        CatLog.m2d("retrieveSMSTPDU", "rawPdu : " + IccUtils.bytesToHexString(rawPdu));
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream(100);
            DataOutputStream dos = new DataOutputStream(baos);
            byte msgtype = rawPdu[0];
            CatLog.m2d("ValueParser", "teleserparaid:" + rawPdu[1]);
            if (rawPdu[1] == (byte) 0) {
                teleservicelen = rawPdu[2] & 255;
                dos.writeInt((rawPdu[3] << 8) | rawPdu[4]);
            }
            dos.writeInt(0);
            dos.writeInt(0);
            int lenmsgteleser = (teleservicelen + 2) + 1;
            CatLog.m2d("ValueParser", "destaddrparaid:" + rawPdu[lenmsgteleser]);
            if (rawPdu[lenmsgteleser] == (byte) 4) {
                CdmaSmsAddress destAddr = new CdmaSmsAddress();
                String destAddrStr = new String();
                rawDestAddrLen = rawPdu[lenmsgteleser + 1] & 255;
                byte[] rawDestAddr = new byte[rawDestAddrLen];
                System.arraycopy(rawPdu, (lenmsgteleser + 1) + 1, rawDestAddr, 0, rawDestAddrLen);
                BitwiseInputStream bitwiseInputStream = new BitwiseInputStream(rawDestAddr);
                if (bitwiseInputStream.available() > 0) {
                    CatLog.m2d("ValueParser", "SMS PDU parsing :: rawDestAddrLen :: " + rawDestAddrLen);
                    destAddr.digitMode = bitwiseInputStream.read(1);
                    destAddr.numberMode = bitwiseInputStream.read(1);
                    byte fieldBits = (byte) 4;
                    byte consumedBits = (byte) 2;
                    if (destAddr.digitMode == 1) {
                        destAddr.ton = bitwiseInputStream.read(3);
                        destAddr.numberPlan = bitwiseInputStream.read(4);
                        fieldBits = (byte) 8;
                        consumedBits = (byte) 9;
                    }
                    destAddr.numberOfDigits = bitwiseInputStream.read(8);
                    int remainingBits = (rawDestAddrLen * 8) - ((byte) (consumedBits + 8));
                    int dataBits = destAddr.numberOfDigits * fieldBits;
                    int paddingBits = remainingBits - dataBits;
                    if (remainingBits < dataBits) {
                        throw new CodingException("Originating_NUMBER subparam encoding size error (remainingBits " + remainingBits + ", dataBits " + dataBits + ", paddingBits " + paddingBits + ")");
                    }
                    destAddr.origBytes = bitwiseInputStream.readByteArray(dataBits);
                    bitwiseInputStream.skip(paddingBits);
                    byte[] rawData = destAddr.origBytes;
                    int numFields = destAddr.numberOfDigits;
                    if (destAddr.digitMode == 1) {
                        destAddrStr = new String(destAddr.origBytes, 0, destAddr.origBytes.length, "US-ASCII");
                    } else {
                        StringBuffer stringBuffer = new StringBuffer(numFields);
                        for (i = 0; i < numFields; i++) {
                            int val = (rawData[i / 2] >>> (4 - ((i % 2) * 4))) & 15;
                            if (val >= 1 && val <= 9) {
                                stringBuffer.append(Integer.toString(val, 10));
                            } else if (val == 10) {
                                stringBuffer.append('0');
                            } else if (val == 11) {
                                stringBuffer.append('*');
                            } else if (val == 12) {
                                stringBuffer.append('#');
                            } else if (val == 0) {
                                stringBuffer.append('0');
                            } else {
                                throw new CodingException("invalid SMS address DTMF code (" + val + ")");
                            }
                        }
                        destAddrStr = stringBuffer.toString();
                    }
                }
                CatLog.m2d("ValueParser", "SMS Destination address!!!: " + destAddrStr);
                dos.write(destAddr.digitMode);
                dos.write(destAddr.numberMode);
                dos.write(destAddr.ton);
                dos.write(destAddr.numberPlan);
                dos.write(destAddr.numberOfDigits);
                dos.write(destAddr.origBytes, 0, destAddr.origBytes.length);
                CatLog.m2d("ValueParser", "SMS Destination destAddr.digitMode: " + destAddr.digitMode);
                CatLog.m2d("ValueParser", "SMS Destination destAddr.numberMode: " + destAddr.numberMode);
                CatLog.m2d("ValueParser", "SMS Destination destAddr.ton: " + destAddr.ton);
                CatLog.m2d("ValueParser", "SMS Destination destAddr.numberPlan: " + destAddr.numberPlan);
                CatLog.m2d("ValueParser", "SMS Destination destAddr.origBytes.length: " + destAddr.origBytes.length);
                CatLog.m2d("ValueParser", "SMS Destination destAddr.origBytes: " + destAddr.origBytes);
            }
            dos.write(0);
            dos.write(0);
            dos.write(0);
            int lenmsgteledest = ((lenmsgteleser + 1) + 1) + rawDestAddrLen;
            CatLog.m2d("ValueParser", "bearerdataparaid:" + rawPdu[lenmsgteledest]);
            if (rawPdu[lenmsgteledest] == (byte) 8) {
                byte[] bearerdata = new byte[(rawPdu[lenmsgteledest + 1] & 255)];
                CatLog.m2d("ValueParser", "bearerdata length:" + bearerdata.length);
                System.arraycopy(rawPdu, (lenmsgteledest + 1) + 1, bearerdata, 0, bearerdata.length);
                dos.write(bearerdata.length);
                dos.write(bearerdata, 0, bearerdata.length);
            }
            dos.close();
            String pdustr = IccUtils.bytesToHexString(baos.toByteArray());
            CatLog.m2d("ValueParser", " Pdu : " + pdustr);
            baos.close();
            return pdustr;
        } catch (UnsupportedEncodingException e2) {
            throw new CodingException("invalid SMS address ASCII code");
        } catch (AccessException ex) {
            CatLog.m2d("ValueParser", "destination address decode failed: " + ex);
            throw new ResultException(ResultCode.CMD_DATA_NOT_UNDERSTOOD);
        } catch (CodingException ex2) {
            CatLog.m2d("ValueParser", "destination address decode failed: " + ex2);
            throw new ResultException(ResultCode.CMD_DATA_NOT_UNDERSTOOD);
        } catch (IOException ex3) {
            CatLog.m2d("ValueParser", "creating SubmitPdu failed: " + ex3);
            throw new ResultException(ResultCode.CMD_DATA_NOT_UNDERSTOOD);
        } catch (IndexOutOfBoundsException e3) {
            throw new ResultException(ResultCode.CMD_DATA_NOT_UNDERSTOOD);
        } catch (NegativeArraySizeException ee) {
            CatLog.m2d("ValueParser", "creating SubmitPdu failed: " + ee);
            throw new ResultException(ResultCode.CMD_DATA_NOT_UNDERSTOOD);
        }
    }

    static String retrieveLanguage(ComprehensionTlv ctlv) throws ResultException {
        byte[] rawValue = ctlv.getRawValue();
        int valueIndex = ctlv.getValueIndex();
        int length = ctlv.getLength();
        if (length != 0) {
            return new String(rawValue, valueIndex, length);
        }
        return null;
    }

    static BearerDescription retrieveBearerDescription(ComprehensionTlv ctlv) throws ResultException {
        byte[] rawValue = ctlv.getRawValue();
        int valueIndex = ctlv.getValueIndex();
        int length = ctlv.getLength();
        BearerDescription bearerDesc = new BearerDescription();
        int valueIndex2 = valueIndex + 1;
        try {
            bearerDesc.bearerType = rawValue[valueIndex];
            switch (bearerDesc.bearerType) {
                case (byte) 1:
                    bearerDesc.bearerCSD = new BearerCSD();
                    valueIndex = valueIndex2 + 1;
                    try {
                        bearerDesc.bearerCSD.dataRate = rawValue[valueIndex2];
                        valueIndex2 = valueIndex + 1;
                        bearerDesc.bearerCSD.bearerService = rawValue[valueIndex];
                        valueIndex = valueIndex2 + 1;
                        bearerDesc.bearerCSD.connectionElement = rawValue[valueIndex2];
                        CatLog.m2d("ValueParser", "retrieveBearerDescription: Bearer Type = CSD");
                        return bearerDesc;
                    } catch (IndexOutOfBoundsException e) {
                        CatLog.m2d("ValueParser", "ResultException: retrieveBearerDescription");
                        throw new ResultException(ResultCode.REQUIRED_VALUES_MISSING);
                    } catch (Exception e2) {
                        CatLog.m2d("ValueParser", "Unknown ResultException in retrieveBearerDescription: ");
                        return null;
                    }
                case (byte) 2:
                    bearerDesc.bearerGPRS = new BearerGPRS();
                    valueIndex = valueIndex2 + 1;
                    bearerDesc.bearerGPRS.precedenceClass = rawValue[valueIndex2];
                    valueIndex2 = valueIndex + 1;
                    bearerDesc.bearerGPRS.delayClass = rawValue[valueIndex];
                    valueIndex = valueIndex2 + 1;
                    bearerDesc.bearerGPRS.reliabilityClass = rawValue[valueIndex2];
                    valueIndex2 = valueIndex + 1;
                    bearerDesc.bearerGPRS.peakThroughputClass = rawValue[valueIndex];
                    valueIndex = valueIndex2 + 1;
                    bearerDesc.bearerGPRS.meanThroughputClass = rawValue[valueIndex2];
                    valueIndex2 = valueIndex + 1;
                    bearerDesc.bearerGPRS.packetDataProtocolType = rawValue[valueIndex];
                    CatLog.m2d("ValueParser", "retrieveBearerDescription: Bearer Type = GPRS");
                    valueIndex = valueIndex2;
                    return bearerDesc;
                case (byte) 3:
                    bearerDesc.bearerType = (byte) 3;
                    bearerDesc.bearerDefault = true;
                    CatLog.m2d("ValueParser", "retrieveBearerDescription: Bearer Type = Default");
                    valueIndex = valueIndex2;
                    return bearerDesc;
                case (byte) 8:
                    bearerDesc.bearerType = (byte) 8;
                    CatLog.m2d("ValueParser", "retrieveBearerDescription: Bearer Type = BEARER_CDMA");
                    valueIndex = valueIndex2;
                    return bearerDesc;
                case (byte) 11:
                    bearerDesc.bearerType = (byte) 11;
                    bearerDesc.bearerEUTRAN = new BearerEUTRAN();
                    bearerDesc.bearerEUTRAN.setup(rawValue, length, valueIndex2);
                    bearerDesc.bearerEUTRAN.dump();
                    valueIndex = valueIndex2;
                    return bearerDesc;
                default:
                    CatLog.m2d("ValueParser", "retrieveBearerDescription: Invalid Bearer Type(" + bearerDesc.bearerType + ")");
                    valueIndex = valueIndex2;
                    return bearerDesc;
            }
        } catch (IndexOutOfBoundsException e3) {
            valueIndex = valueIndex2;
            CatLog.m2d("ValueParser", "ResultException: retrieveBearerDescription");
            throw new ResultException(ResultCode.REQUIRED_VALUES_MISSING);
        } catch (Exception e4) {
            valueIndex = valueIndex2;
            CatLog.m2d("ValueParser", "Unknown ResultException in retrieveBearerDescription: ");
            return null;
        }
    }

    static int retrieveBufferSize(ComprehensionTlv ctlv) throws ResultException {
        byte[] rawValue = ctlv.getRawValue();
        int valueIndex = ctlv.getValueIndex();
        CatLog.m2d("ValueParser", "retrieveBufferSize: valueIndex , Length = " + valueIndex + " , " + ctlv.getLength());
        try {
            int secondByte = rawValue[valueIndex + 1] & 255;
            int resultByte = ((rawValue[valueIndex] & 255) << 8) | secondByte;
            CatLog.m2d("ValueParser", "retrieveBufferSize: buffer size = " + resultByte);
            return resultByte;
        } catch (IndexOutOfBoundsException e) {
            CatLog.m2d("ValueParser", "ResultException: retrieveBufferSize");
            throw new ResultException(ResultCode.REQUIRED_VALUES_MISSING);
        } catch (Exception e2) {
            CatLog.m2d("ValueParser", "Unknown ResultException in retrieveBufferSize: ");
            return -1;
        }
    }

    static TransportLevel retrieveTransportLevel(ComprehensionTlv ctlv) throws ResultException {
        TransportLevel transportLevel = new TransportLevel();
        byte[] rawValue = ctlv.getRawValue();
        int valueIndex = ctlv.getValueIndex();
        CatLog.m2d("ValueParser", "retrieveTransportLevel: valueIndex , Length = " + valueIndex + " , " + ctlv.getLength());
        try {
            transportLevel.transportProtocol = rawValue[valueIndex];
            int secondByte = rawValue[valueIndex + 2] & 255;
            transportLevel.portNumber = ((rawValue[valueIndex + 1] & 255) << 8) | secondByte;
            CatLog.m2d("ValueParser", "retrieveTransportLevel: transportProtocol , portNumber = " + transportLevel.transportProtocol + " , " + transportLevel.portNumber);
            return transportLevel;
        } catch (IndexOutOfBoundsException e) {
            CatLog.m2d("ValueParser", "ResultException: retrieveTransportLevel");
            throw new ResultException(ResultCode.REQUIRED_VALUES_MISSING);
        } catch (Exception e2) {
            CatLog.m2d("ValueParser", "Unknown ResultException in retrieveTransportLevel: ");
            return null;
        }
    }

    static DataDestinationAddress retrieveDataDestinationAddress(ComprehensionTlv ctlv) throws ResultException {
        DataDestinationAddress dataDestinationAddress = new DataDestinationAddress();
        byte[] rawValue = ctlv.getRawValue();
        int valueIndex = ctlv.getValueIndex();
        int length = ctlv.getLength() - 1;
        CatLog.m2d("ValueParser", "retrieveDataDestinationAddress: valueIndex , Length = " + valueIndex + " , " + length);
        if (length <= 0) {
            CatLog.m2d("ValueParser", "retrieveDataDestinationAddress: Length is 00. Supply Dynamic IP");
            return null;
        }
        int valueIndex2 = valueIndex + 1;
        try {
            dataDestinationAddress.addressType = rawValue[valueIndex];
            CatLog.m2d("ValueParser", "retrieveDataDestinationAddress: Address Type = " + dataDestinationAddress.addressType + " beginning Byte array copy");
            byte[] tempDataDestAddress = new byte[length];
            int i = 0;
            while (i < length) {
                valueIndex = valueIndex2 + 1;
                try {
                    tempDataDestAddress[i] = rawValue[valueIndex2];
                    CatLog.m2d("ValueParser", " " + tempDataDestAddress[i]);
                    i++;
                    valueIndex2 = valueIndex;
                } catch (IndexOutOfBoundsException e) {
                } catch (UnknownHostException e2) {
                } catch (Exception e3) {
                }
            }
            CatLog.m2d("ValueParser", "retrieveDataDestinationAddress: tempDataDestAddress = " + IccUtils.bytesToHexString(tempDataDestAddress) + " Byte array copy complete");
            InetAddress add = InetAddress.getByAddress(tempDataDestAddress);
            CatLog.m2d("ValueParser", "retrieveDataDestinationAddress : InetAddress retrieved ");
            dataDestinationAddress.address = add.getAddress();
            valueIndex = valueIndex2;
            return dataDestinationAddress;
        } catch (IndexOutOfBoundsException e4) {
            valueIndex = valueIndex2;
            CatLog.m2d("ValueParser", " ResultException: retrieveDataDestinationAddress - IndexOutOfBoundsException");
            throw new ResultException(ResultCode.REQUIRED_VALUES_MISSING);
        } catch (UnknownHostException e5) {
            valueIndex = valueIndex2;
            CatLog.m2d("ValueParser", " ResultException: retrieveDataDestinationAddress - UnknownHostException");
            throw new ResultException(ResultCode.REQUIRED_VALUES_MISSING);
        } catch (Exception e6) {
            valueIndex = valueIndex2;
            CatLog.m2d("ValueParser", "Unknown ResultException in retrieveDataDestinationAddress: ");
            return null;
        }
    }

    static String retrieveNetworkAccessName(ComprehensionTlv ctlv) throws ResultException {
        byte[] rawValue = ctlv.getRawValue();
        int valueIndex = ctlv.getValueIndex();
        int length = ctlv.getLength();
        CatLog.m2d("ValueParser", "retrieveNetworkAccessName: valueIndex , Length = " + valueIndex + " , " + length);
        try {
            byte[] tempNetworkAccessName = new byte[length];
            CatLog.m2d("ValueParser", "retrieveNetworkAccessName: beginning Byte array copy");
            int wordLenIdx = rawValue[valueIndex] + 1;
            int i = 1;
            int idx = 0;
            while (i < length) {
                int idx2;
                if (i == wordLenIdx) {
                    idx2 = idx + 1;
                    tempNetworkAccessName[idx] = (byte) 46;
                    wordLenIdx += rawValue[valueIndex + i] + 1;
                } else {
                    idx2 = idx + 1;
                    tempNetworkAccessName[idx] = rawValue[valueIndex + i];
                }
                i++;
                idx = idx2;
            }
            CatLog.m2d("ValueParser", "retrieveNetworkAccessName: array copy complete");
            String tempName = new String(tempNetworkAccessName, 0, idx, "UTF-8");
            CatLog.m2d("ValueParser", "retrieveNetworkAccessName: tempName = " + tempName);
            return tempName;
        } catch (IndexOutOfBoundsException e) {
            CatLog.m2d("ValueParser", " ResultException: retrieveNetworkAccessName - IndexOutOfBoundsException");
            throw new ResultException(ResultCode.REQUIRED_VALUES_MISSING);
        } catch (UnsupportedEncodingException e2) {
            CatLog.m2d("ValueParser", " ResultException: retrieveNetworkAccessName - UnsupportedEncodingException");
            throw new ResultException(ResultCode.REQUIRED_VALUES_MISSING);
        } catch (Exception e3) {
            CatLog.m2d("ValueParser", "Unknown ResultException in retrieveNetworkAccessName: ");
            return null;
        }
    }

    static byte retrieveChannelDataLength(ComprehensionTlv ctlv) throws ResultException {
        byte[] rawValue = ctlv.getRawValue();
        int valueIndex = ctlv.getValueIndex();
        CatLog.m2d("ValueParser", "retrieveChannelDataLength: valueIndex , Length = " + valueIndex + " , " + ctlv.getLength());
        try {
            return (byte) (rawValue[valueIndex] & 255);
        } catch (IndexOutOfBoundsException e) {
            CatLog.m2d("ValueParser", " ResultException: retrieveChannelDataLength");
            throw new ResultException(ResultCode.REQUIRED_VALUES_MISSING);
        } catch (Exception e2) {
            CatLog.m2d("ValueParser", "Unknown ResultException in retrieveChannelDataLength: ");
            return (byte) 0;
        }
    }

    static byte[] retrieveChannelData(ComprehensionTlv ctlv) throws ResultException {
        byte[] rawValue = ctlv.getRawValue();
        int valueIndex = ctlv.getValueIndex();
        int length = ctlv.getLength();
        CatLog.m2d("ValueParser", "retrieveChannelData : value Index , length " + valueIndex + " , " + length);
        try {
            CatLog.m2d("ValueParser", "retrieveChannelData: beginning Byte array copy");
            byte[] tempChannelData = new byte[length];
            int i = 0;
            int valueIndex2 = valueIndex;
            while (i < length) {
                valueIndex = valueIndex2 + 1;
                tempChannelData[i] = rawValue[valueIndex2];
                i++;
                valueIndex2 = valueIndex;
            }
            try {
                CatLog.m2d("ValueParser", "retrieveChannelData: tempChannelData = " + IccUtils.bytesToHexString(tempChannelData) + " Byte Array Copy Complete");
                valueIndex = valueIndex2;
                return tempChannelData;
            } catch (IndexOutOfBoundsException e) {
                valueIndex = valueIndex2;
                CatLog.m2d("ValueParser", "ResultException: retrieveChannelData");
                throw new ResultException(ResultCode.REQUIRED_VALUES_MISSING);
            } catch (Exception e2) {
                valueIndex = valueIndex2;
                CatLog.m2d("ValueParser", "Unknown ResultException in retrieveChannelData: ");
                return null;
            }
        } catch (IndexOutOfBoundsException e3) {
            CatLog.m2d("ValueParser", "ResultException: retrieveChannelData");
            throw new ResultException(ResultCode.REQUIRED_VALUES_MISSING);
        } catch (Exception e4) {
            CatLog.m2d("ValueParser", "Unknown ResultException in retrieveChannelData: ");
            return null;
        }
    }
}
