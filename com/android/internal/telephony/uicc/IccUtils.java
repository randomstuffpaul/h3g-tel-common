package com.android.internal.telephony.uicc;

import android.content.res.Resources;
import android.content.res.Resources.NotFoundException;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.telephony.Rlog;
import com.android.internal.telephony.GsmAlphabet;
import com.google.android.mms.pdu.CharacterSets;
import java.io.UnsupportedEncodingException;

public class IccUtils {
    static final String LOG_TAG = "IccUtils";

    public static String bcdToString(byte[] data, int offset, int length) {
        StringBuilder ret = new StringBuilder(length * 2);
        for (int i = offset; i < offset + length; i++) {
            int v = data[i] & 15;
            if (v > 9) {
                break;
            }
            ret.append((char) (v + 48));
            v = (data[i] >> 4) & 15;
            if (v != 15) {
                if (v > 9) {
                    break;
                }
                ret.append((char) (v + 48));
            }
        }
        return ret.toString();
    }

    public static String cdmaBcdToString(byte[] data, int offset, int length) {
        StringBuilder ret = new StringBuilder(length);
        int count = 0;
        int i = offset;
        while (count < length) {
            int v = data[i] & 15;
            if (v > 9) {
                v = 0;
            }
            ret.append((char) (v + 48));
            count++;
            if (count == length) {
                break;
            }
            v = (data[i] >> 4) & 15;
            if (v > 9) {
                v = 0;
            }
            ret.append((char) (v + 48));
            count++;
            i++;
        }
        return ret.toString();
    }

    public static int gsmBcdByteToInt(byte b) {
        int ret = 0;
        if ((b & 240) <= 144) {
            ret = (b >> 4) & 15;
        }
        if ((b & 15) <= 9) {
            return ret + ((b & 15) * 10);
        }
        return ret;
    }

    public static int cdmaBcdByteToInt(byte b) {
        int ret = 0;
        if ((b & 240) <= 144) {
            ret = ((b >> 4) & 15) * 10;
        }
        if ((b & 15) <= 9) {
            return ret + (b & 15);
        }
        return ret;
    }

    public static String adnStringFieldToString(byte[] data, int offset, int length) {
        if (length == 0) {
            return "";
        }
        if (length >= 1 && data[offset] == Byte.MIN_VALUE) {
            String ret = null;
            try {
                ret = new String(data, offset + 1, ((length - 1) / 2) * 2, "utf-16be");
            } catch (UnsupportedEncodingException ex) {
                Rlog.e(LOG_TAG, "implausible UnsupportedEncodingException", ex);
            }
            if (ret != null) {
                int ucslen = ret.length();
                while (ucslen > 0 && ret.charAt(ucslen - 1) == '￿') {
                    ucslen--;
                }
                return ret.substring(0, ucslen);
            }
        }
        boolean isucs2 = false;
        char base = '\u0000';
        int len = 0;
        if (length >= 3 && data[offset] == (byte) -127) {
            len = data[offset + 1] & 255;
            if (len > length - 3) {
                len = length - 3;
            }
            base = (char) ((data[offset + 2] & 255) << 7);
            offset += 3;
            isucs2 = true;
        } else if (length >= 4 && data[offset] == (byte) -126) {
            len = data[offset + 1] & 255;
            if (len > length - 4) {
                len = length - 4;
            }
            base = (char) (((data[offset + 2] & 255) << 8) | (data[offset + 3] & 255));
            offset += 4;
            isucs2 = true;
        }
        if (isucs2) {
            StringBuilder ret2 = new StringBuilder();
            while (len > 0) {
                if (data[offset] < (byte) 0) {
                    ret2.append((char) ((data[offset] & 127) + base));
                    offset++;
                    len--;
                }
                int count = 0;
                while (count < len && data[offset + count] >= (byte) 0) {
                    count++;
                }
                ret2.append(GsmAlphabet.gsm8BitUnpackedToString(data, offset, count));
                offset += count;
                len -= count;
            }
            return ret2.toString();
        }
        String defaultCharset = "";
        try {
            defaultCharset = Resources.getSystem().getString(17039406);
        } catch (NotFoundException e) {
        }
        return GsmAlphabet.gsm8BitUnpackedToString(data, offset, length, defaultCharset.trim());
    }

    static int hexCharToInt(char c) {
        if (c >= '0' && c <= '9') {
            return c - 48;
        }
        if (c >= 'A' && c <= 'F') {
            return (c - 65) + 10;
        }
        if (c >= 'a' && c <= 'f') {
            return (c - 97) + 10;
        }
        throw new RuntimeException("invalid hex char '" + c + "'");
    }

    public static byte[] hexStringToBytes(String s) {
        if (s == null) {
            return null;
        }
        int sz = s.length();
        byte[] ret = new byte[(sz / 2)];
        for (int i = 0; i < sz; i += 2) {
            ret[i / 2] = (byte) ((hexCharToInt(s.charAt(i)) << 4) | hexCharToInt(s.charAt(i + 1)));
        }
        return ret;
    }

    public static String bytesToHexString(byte[] bytes) {
        if (bytes == null) {
            return null;
        }
        StringBuilder ret = new StringBuilder(bytes.length * 2);
        for (int i = 0; i < bytes.length; i++) {
            ret.append("0123456789abcdef".charAt((bytes[i] >> 4) & 15));
            ret.append("0123456789abcdef".charAt(bytes[i] & 15));
        }
        return ret.toString();
    }

    public static String networkNameToString(byte[] data, int offset, int length) {
        if ((data[offset] & 128) != 128 || length < 1) {
            return "";
        }
        String gsm7BitPackedToString;
        switch ((data[offset] >>> 4) & 7) {
            case 0:
                gsm7BitPackedToString = GsmAlphabet.gsm7BitPackedToString(data, offset + 1, (((length - 1) * 8) - (data[offset] & 7)) / 7);
                break;
            case 1:
                try {
                    gsm7BitPackedToString = new String(data, offset + 1, length - 1, CharacterSets.MIMENAME_UTF_16);
                    break;
                } catch (UnsupportedEncodingException ex) {
                    gsm7BitPackedToString = "";
                    Rlog.e(LOG_TAG, "implausible UnsupportedEncodingException", ex);
                    break;
                }
            default:
                gsm7BitPackedToString = "";
                break;
        }
        return (data[offset] & 64) != 0 ? gsm7BitPackedToString : gsm7BitPackedToString;
    }

    public static Bitmap parseToBnW(byte[] data, int length) {
        int valueIndex = 0 + 1;
        int width = data[0] & 255;
        int height = data[valueIndex] & 255;
        int numOfPixels = width * height;
        int[] pixels = new int[numOfPixels];
        int bitIndex = 7;
        byte currentByte = (byte) 0;
        int pixelIndex = 0;
        valueIndex++;
        while (pixelIndex < numOfPixels) {
            int valueIndex2;
            if (pixelIndex % 8 == 0) {
                valueIndex2 = valueIndex + 1;
                currentByte = data[valueIndex];
                bitIndex = 7;
            } else {
                valueIndex2 = valueIndex;
            }
            int pixelIndex2 = pixelIndex + 1;
            int bitIndex2 = bitIndex - 1;
            pixels[pixelIndex] = bitToRGB((currentByte >> bitIndex) & 1);
            bitIndex = bitIndex2;
            pixelIndex = pixelIndex2;
            valueIndex = valueIndex2;
        }
        if (pixelIndex != numOfPixels) {
            Rlog.e(LOG_TAG, "parse end and size error");
        }
        return Bitmap.createBitmap(pixels, width, height, Config.ARGB_8888);
    }

    private static int bitToRGB(int bit) {
        if (bit == 1) {
            return -1;
        }
        return -16777216;
    }

    public static Bitmap parseToRGB(byte[] data, int length, boolean transparency) {
        int[] resultArray;
        int valueIndex = 0 + 1;
        int width = data[0] & 255;
        int valueIndex2 = valueIndex + 1;
        int height = data[valueIndex] & 255;
        valueIndex = valueIndex2 + 1;
        int bits = data[valueIndex2] & 255;
        valueIndex2 = valueIndex + 1;
        int colorNumber = data[valueIndex] & 255;
        valueIndex = valueIndex2 + 1;
        valueIndex2 = valueIndex + 1;
        int[] colorIndexArray = getCLUT(data, ((data[valueIndex2] & 255) << 8) | (data[valueIndex] & 255), colorNumber);
        if (true == transparency) {
            colorIndexArray[colorNumber - 1] = 0;
        }
        if (8 % bits == 0) {
            resultArray = mapTo2OrderBitColor(data, valueIndex2, width * height, colorIndexArray, bits);
        } else {
            resultArray = mapToNon2OrderBitColor(data, valueIndex2, width * height, colorIndexArray, bits);
        }
        return Bitmap.createBitmap(resultArray, width, height, Config.RGB_565);
    }

    private static int[] mapTo2OrderBitColor(byte[] data, int valueIndex, int length, int[] colorArray, int bits) {
        if (8 % bits != 0) {
            Rlog.e(LOG_TAG, "not event number of color");
            return mapToNon2OrderBitColor(data, valueIndex, length, colorArray, bits);
        }
        int mask = 1;
        switch (bits) {
            case 1:
                mask = 1;
                break;
            case 2:
                mask = 3;
                break;
            case 4:
                mask = 15;
                break;
            case 8:
                mask = 255;
                break;
        }
        int[] resultArray = new int[length];
        int resultIndex = 0;
        int run = 8 / bits;
        int valueIndex2 = valueIndex;
        while (resultIndex < length) {
            valueIndex = valueIndex2 + 1;
            byte tempByte = data[valueIndex2];
            int runIndex = 0;
            int resultIndex2 = resultIndex;
            while (runIndex < run) {
                resultIndex = resultIndex2 + 1;
                resultArray[resultIndex2] = colorArray[(tempByte >> (((run - runIndex) - 1) * bits)) & mask];
                runIndex++;
                resultIndex2 = resultIndex;
            }
            resultIndex = resultIndex2;
            valueIndex2 = valueIndex;
        }
        valueIndex = valueIndex2;
        return resultArray;
    }

    private static int[] mapToNon2OrderBitColor(byte[] data, int valueIndex, int length, int[] colorArray, int bits) {
        if (8 % bits != 0) {
            return new int[length];
        }
        Rlog.e(LOG_TAG, "not odd number of color");
        return mapTo2OrderBitColor(data, valueIndex, length, colorArray, bits);
    }

    private static int[] getCLUT(byte[] rawData, int offset, int number) {
        if (rawData == null) {
            return null;
        }
        int[] result = new int[number];
        int endIndex = offset + (number * 3);
        int valueIndex = offset;
        int colorIndex = 0;
        while (true) {
            int colorIndex2 = colorIndex + 1;
            int valueIndex2 = valueIndex + 1;
            valueIndex = valueIndex2 + 1;
            valueIndex2 = valueIndex + 1;
            result[colorIndex] = ((((rawData[valueIndex] & 255) << 16) | -16777216) | ((rawData[valueIndex2] & 255) << 8)) | (rawData[valueIndex] & 255);
            if (valueIndex2 >= endIndex) {
                return result;
            }
            colorIndex = colorIndex2;
            valueIndex = valueIndex2;
        }
    }

    public static String SetupCallbcdToString(byte[] data, int offset, int length) {
        StringBuilder ret = new StringBuilder(length * 2);
        if ((data[offset] & 255) == 145) {
            ret.append('+');
        }
        for (int i = offset + 1; i < offset + length; i++) {
            int v = data[i] & 15;
            if (v == 10) {
                ret.append('*');
            } else if (v == 11) {
                ret.append('#');
            } else if (v == 12) {
                ret.append(',');
            } else if (v > 9) {
                break;
            } else {
                ret.append((char) (v + 48));
            }
            v = (data[i] >> 4) & 15;
            if (v != 10) {
                if (v != 11) {
                    if (v != 12) {
                        if (v > 9) {
                            break;
                        }
                        ret.append((char) (v + 48));
                    } else {
                        ret.append(',');
                    }
                } else {
                    ret.append('#');
                }
            } else {
                ret.append('*');
            }
        }
        return ret.toString();
    }

    public static String SSbcdToString(byte[] data, int offset, int length) {
        StringBuilder ret = new StringBuilder(length * 2);
        int ton = data[offset] & 255;
        int i = offset + 1;
        while (i < offset + length) {
            int v = data[i] & 15;
            if (v == 10) {
                ret.append('*');
                if (ton == 145 && i - (offset + 1) > 1) {
                    ton = 0;
                    ret.append('+');
                }
            } else if (v == 11) {
                ret.append('#');
            } else if (v > 9) {
                break;
            } else {
                ret.append((char) (v + 48));
            }
            v = (data[i] >> 4) & 15;
            if (v != 10) {
                if (v != 11) {
                    if (v > 9) {
                        break;
                    }
                    ret.append((char) (v + 48));
                } else {
                    ret.append('#');
                }
            } else {
                ret.append('*');
                if (ton == 145 && i - (offset + 1) > 1) {
                    ton = 0;
                    ret.append('+');
                }
            }
            i++;
        }
        return ret.toString();
    }

    public static int cdmaHexByteToInt(byte b) {
        int ret = 0;
        if ((b & 240) <= 240) {
            ret = ((b >> 4) & 15) * 16;
        }
        if ((b & 15) <= 15) {
            return ret + (b & 15);
        }
        return ret;
    }

    public static byte cdmaIntToBcdByte(int i) {
        byte ret = (byte) 0;
        if ((((byte) (i / 10)) & 240) <= 144) {
            ret = (byte) ((i / 10) << 4);
        }
        if ((((byte) (i % 10)) & 15) <= 9) {
            return (byte) ((i % 10) + ret);
        }
        return ret;
    }

    public static String byteToHexString(byte a) {
        StringBuilder ret = new StringBuilder(2);
        ret.append("0123456789abcdef".charAt((a >> 4) & 15));
        ret.append("0123456789abcdef".charAt(a & 15));
        return ret.toString();
    }

    public static String MccMncConvert(String s) {
        int MCC = 0;
        StringBuilder ret = new StringBuilder(s.length());
        ret.append(s.charAt(1));
        ret.append(s.charAt(0));
        ret.append(s.charAt(3));
        if (ret.toString().compareTo("fff") == 0) {
            Rlog.i(LOG_TAG, "[MccMncConvert] MCC Value is invalid('fff')!");
            return null;
        }
        if (ret.toString().compareTo("ddd") != 0) {
            MCC = Integer.parseInt(ret.toString());
        }
        ret.append(s.charAt(5));
        ret.append(s.charAt(4));
        if (s.charAt(2) != 'F' && s.charAt(2) != 'f') {
            ret.append(s.charAt(2));
        } else if (MCC >= 310 && MCC <= 316) {
            ret.append("0");
        }
        Rlog.i(LOG_TAG, "[MccMncConvert] Convert Result :" + ret.toString());
        return ret.toString();
    }

    private static long unsigned32(int n) {
        return ((long) n) & 4294967295L;
    }

    private static long unsigned32(short n) {
        return ((long) n) & 65535;
    }

    private static long unsigned32(byte n) {
        return ((long) n) & 255;
    }

    private static String getStringMCC(long mcc) {
        long j = 48;
        StringBuilder strMCC = new StringBuilder(3);
        mcc %= 1000;
        strMCC.append((char) ((int) (mcc / 100 == 9 ? 48 : (mcc / 100) + 49)));
        mcc %= 100;
        strMCC.append((char) ((int) (mcc / 10 == 9 ? 48 : (mcc / 10) + 49)));
        if (mcc % 10 != 9) {
            j = (mcc % 10) + 49;
        }
        strMCC.append((char) ((int) j));
        return strMCC.toString();
    }

    private static String getStringMNC(long mnc) {
        long j = 48;
        StringBuilder strMNC = new StringBuilder(2);
        mnc %= 100;
        strMNC.append((char) ((int) (mnc / 10 == 9 ? 48 : (mnc / 10) + 49)));
        if (mnc % 10 != 9) {
            j = (mnc % 10) + 49;
        }
        strMNC.append((char) ((int) j));
        return strMNC.toString();
    }

    private static String getStringMIN1(long min1) {
        StringBuilder strMIN1 = new StringBuilder(7);
        long i;
        if (min1 == 0) {
            for (i = 0; i < 7; i++) {
                strMIN1.append('0');
            }
        } else {
            i = (min1 >> 14) % 1000;
            strMIN1.append((char) ((int) (i / 100 == 9 ? 48 : (i / 100) + 49)));
            i %= 100;
            strMIN1.append((char) ((int) (i / 10 == 9 ? 48 : (i / 10) + 49)));
            strMIN1.append((char) ((int) (i % 10 == 9 ? 48 : (i % 10) + 49)));
            min1 &= 16383;
            i = (min1 >> 10) & 15;
            strMIN1.append((char) ((int) (i == 10 ? 48 : 48 + i)));
            i = (min1 & 1023) % 1000;
            strMIN1.append((char) ((int) (i / 100 == 9 ? 48 : (i / 100) + 49)));
            i %= 100;
            strMIN1.append((char) ((int) (i / 10 == 9 ? 48 : (i / 10) + 49)));
            strMIN1.append((char) ((int) (i % 10 == 9 ? 48 : (i % 10) + 49)));
        }
        return strMIN1.toString();
    }

    private static String getStringMIN2(long min2) {
        long j = 48;
        StringBuilder strMIN2 = new StringBuilder(3);
        min2 %= 1000;
        strMIN2.append((char) ((int) (min2 / 100 == 9 ? 48 : (min2 / 100) + 49)));
        min2 %= 100;
        strMIN2.append((char) ((int) (min2 / 10 == 9 ? 48 : (min2 / 10) + 49)));
        if (min2 % 10 != 9) {
            j = (min2 % 10) + 49;
        }
        strMIN2.append((char) ((int) j));
        return strMIN2.toString();
    }

    public static String extractIMSI(byte[] imsi) {
        Rlog.d(LOG_TAG, "Enter extractIMSI");
        long min1 = ((unsigned32(imsi[5]) << 16) | (unsigned32(imsi[4]) << 8)) | unsigned32(imsi[3]);
        long min2 = (unsigned32(imsi[2]) << 8) | unsigned32(imsi[1]);
        return getStringMCC((unsigned32(imsi[9]) << 8) | unsigned32(imsi[8])) + getStringMNC(unsigned32(imsi[6])) + getStringMIN2(min2) + getStringMIN1(min1);
    }

    public static String unicode2String(byte[] data, int offset, int length) {
        Rlog.d(LOG_TAG, "Enter unicode2String");
        StringBuilder ret = new StringBuilder();
        for (int len = 0; len < length && data[offset] != (byte) -1; len += 2) {
            ret.append((char) ((int) (unsigned32(data[offset] << 8) | unsigned32(data[offset + 1]))));
            offset += 2;
        }
        return ret.toString();
    }

    public static String SetupMDNbcdToString(byte[] data, int offset, int length) {
        StringBuilder ret = new StringBuilder(length * 2);
        for (int i = offset + 1; i < offset + length; i++) {
            int v = data[i] & 15;
            if (v == 10) {
                ret.append('0');
            } else if (v == 11) {
                ret.append('*');
            } else if (v == 12) {
                ret.append('#');
            } else if (v > 9) {
                break;
            } else {
                ret.append((char) (v + 48));
            }
            v = (data[i] >> 4) & 15;
            if (v != 10) {
                if (v != 11) {
                    if (v != 12) {
                        if (v > 9) {
                            break;
                        }
                        ret.append((char) (v + 48));
                    } else {
                        ret.append('#');
                    }
                } else {
                    ret.append('*');
                }
            } else {
                ret.append('0');
            }
        }
        if (ret.toString().length() > length) {
            return ret.toString().substring(0, length);
        }
        return ret.toString();
    }

    public static String SetupIMSIbcdToString(byte[] data, int offset, int length) {
        StringBuilder ret = new StringBuilder(length * 2);
        for (int i = offset + 1; i < offset + length; i++) {
            int v = data[i] & 15;
            if (v == 10) {
                ret.append('*');
            } else if (v == 11) {
                ret.append('#');
            } else if (v == 12) {
                ret.append('P');
            } else {
                ret.append((char) (v + 48));
            }
            v = (data[i] >> 4) & 15;
            if (v == 10) {
                ret.append('*');
            } else if (v == 11) {
                ret.append('#');
            } else if (v == 12) {
                ret.append('P');
            } else if (v == 15) {
                ret.append('\u0000');
            } else {
                ret.append((char) (v + 48));
            }
        }
        return ret.toString();
    }

    public static String ICCIDbcdToString(byte[] data, int offset, int length) {
        StringBuilder ret = new StringBuilder(length * 2);
        for (int i = offset; i < offset + length; i++) {
            int v = data[i] & 15;
            if (v > 9) {
                ret.append((char) (v + 87));
            } else {
                ret.append((char) (v + 48));
            }
            v = (data[i] >> 4) & 15;
            if (v > 9) {
                ret.append((char) (v + 87));
            } else {
                ret.append((char) (v + 48));
            }
        }
        return ret.toString();
    }
}
