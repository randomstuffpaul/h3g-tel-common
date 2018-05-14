package com.android.internal.telephony;

import android.content.res.Resources;
import android.content.res.Resources.NotFoundException;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.telephony.Rlog;
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
}
