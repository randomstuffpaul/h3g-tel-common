package com.android.internal.telephony.uicc;

import android.util.Log;
import com.android.internal.telephony.GsmAlphabet;
import com.android.internal.telephony.IccUtils;
import java.io.UnsupportedEncodingException;

public class SimPBEntryResult {
    private static final int GSM_TEXT_ENC_ASCII = 1;
    private static final int GSM_TEXT_ENC_GSM7BIT = 2;
    private static final int GSM_TEXT_ENC_HEX = 4;
    private static final int GSM_TEXT_ENC_UCS2 = 3;
    public static final int INDEX_ANR = 1;
    public static final int INDEX_ANRA = 2;
    public static final int INDEX_ANRB = 3;
    public static final int INDEX_ANRC = 4;
    public static final int INDEX_EMAIL = 2;
    public static final int INDEX_NAME = 0;
    public static final int INDEX_NUMBER = 0;
    public static final int INDEX_SNE = 1;
    static final String LOG_TAG = "GSM";
    public static final int NUM_OF_ALPHA = 3;
    public static final int NUM_OF_NUMBER = 5;
    public String[] alphaTags = new String[3];
    public int[] dataTypeAlphas = new int[3];
    public int[] dataTypeNumbers = new int[5];
    public int[] lengthAlphas = new int[3];
    public int[] lengthNumbers = new int[5];
    public int nextIndex;
    public String[] numbers = new String[5];
    public int recordIndex;

    public SimPBEntryResult(int[] lengthAlphas, int[] dataTypeAlphas, String[] alphaTags, int[] lengthNumbers, int[] dataTypeNumbers, String[] numbers, int recordIndex, int nextIndex) {
        int i;
        for (i = 0; i < 3; i++) {
            this.lengthAlphas[i] = lengthAlphas[i];
            this.dataTypeAlphas[i] = dataTypeAlphas[i];
            byte[] alphaTagByte = IccUtils.hexStringToBytes(alphaTags[i]);
            switch (dataTypeAlphas[i]) {
                case 1:
                    this.alphaTags[i] = "";
                    Log.i(LOG_TAG, "Not supported encoding type");
                    break;
                case 2:
                    this.alphaTags[i] = GsmAlphabet.gsm8BitUnpackedToString(alphaTagByte, 0, lengthAlphas[i]);
                    break;
                case 3:
                    try {
                        this.alphaTags[i] = new String(alphaTagByte, 0, lengthAlphas[i], "UTF-16");
                        break;
                    } catch (UnsupportedEncodingException e) {
                        this.alphaTags[i] = "";
                        Log.i(LOG_TAG, "SimPBEntryResult - implausible UnsupportedEncodingException");
                        break;
                    }
                case 4:
                    this.alphaTags[i] = "";
                    Log.i(LOG_TAG, "Not supported encoding type");
                    break;
                default:
                    this.alphaTags[i] = "";
                    Log.i(LOG_TAG, "SimPBEntryResult: default Unknown type");
                    break;
            }
        }
        i = 0;
        while (i < 5) {
            this.lengthNumbers[i] = lengthNumbers[i];
            this.dataTypeNumbers[i] = dataTypeNumbers[i];
            if (lengthNumbers[i] == 0 || numbers[i] == null) {
                this.numbers[i] = "";
            } else {
                this.numbers[i] = numbers[i];
            }
            i++;
        }
        this.recordIndex = recordIndex;
        this.nextIndex = nextIndex;
    }
}
