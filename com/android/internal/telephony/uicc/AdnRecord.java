package com.android.internal.telephony.uicc;

import android.os.Parcel;
import android.os.Parcelable;
import android.os.Parcelable.Creator;
import android.telephony.PhoneNumberUtils;
import android.telephony.Rlog;
import android.text.TextUtils;
import com.android.internal.telephony.GsmAlphabet;
import java.util.Arrays;

public class AdnRecord implements Parcelable {
    static final int ADN_BCD_NUMBER_LENGTH = 0;
    static final int ADN_CAPABILITY_ID = 12;
    static final int ADN_DIALING_NUMBER_END = 11;
    static final int ADN_DIALING_NUMBER_START = 2;
    static final int ADN_EXTENSION_ID = 13;
    static final int ADN_TON_AND_NPI = 1;
    public static final Creator<AdnRecord> CREATOR = new C01261();
    static final int EXT_RECORD_LENGTH_BYTES = 13;
    static final int EXT_RECORD_TYPE_ADDITIONAL_DATA = 2;
    static final int EXT_RECORD_TYPE_MASK = 3;
    static final int FOOTER_SIZE_BYTES = 14;
    static final String LOG_TAG = "AdnRecord";
    static final int MAX_EXT_CALLED_PARTY_LENGTH = 10;
    static final int MAX_NUMBER_SIZE_BYTES = 11;
    public String mAlphaTag;
    public String mAnr;
    public String mAnrA;
    public String mAnrB;
    public String mAnrC;
    int mEfid;
    public String[] mEmails;
    int mExtRecord;
    public String mNumber;
    public int mRecordNumber;
    public String mSne;

    static class C01261 implements Creator<AdnRecord> {
        C01261() {
        }

        public AdnRecord createFromParcel(Parcel source) {
            return new AdnRecord(source.readInt(), source.readInt(), source.readString(), source.readString(), source.readStringArray(), source.readString(), source.readString(), source.readString(), source.readString(), source.readString());
        }

        public AdnRecord[] newArray(int size) {
            return new AdnRecord[size];
        }
    }

    public AdnRecord(byte[] record) {
        this(0, 0, record);
    }

    public AdnRecord(int efid, int recordNumber, byte[] record) {
        this.mAlphaTag = null;
        this.mNumber = null;
        this.mExtRecord = 255;
        this.mAnr = "";
        this.mAnrA = "";
        this.mAnrB = "";
        this.mAnrC = "";
        this.mSne = "";
        this.mEfid = efid;
        this.mRecordNumber = recordNumber;
        parseRecord(record);
    }

    public AdnRecord(String alphaTag, String number) {
        this(0, 0, alphaTag, number);
    }

    public AdnRecord(String alphaTag, String number, String[] emails) {
        this(0, 0, alphaTag, number, emails);
    }

    public AdnRecord(int efid, int recordNumber, String alphaTag, String number, String[] emails) {
        this.mAlphaTag = null;
        this.mNumber = null;
        this.mExtRecord = 255;
        this.mAnr = "";
        this.mAnrA = "";
        this.mAnrB = "";
        this.mAnrC = "";
        this.mSne = "";
        this.mEfid = efid;
        this.mRecordNumber = recordNumber;
        this.mAlphaTag = alphaTag;
        this.mNumber = number;
        this.mEmails = emails;
    }

    public AdnRecord(int efid, int recordNumber, String alphaTag, String number) {
        this.mAlphaTag = null;
        this.mNumber = null;
        this.mExtRecord = 255;
        this.mAnr = "";
        this.mAnrA = "";
        this.mAnrB = "";
        this.mAnrC = "";
        this.mSne = "";
        this.mEfid = efid;
        this.mRecordNumber = recordNumber;
        this.mAlphaTag = alphaTag;
        this.mNumber = number;
        this.mEmails = new String[1];
        this.mEmails[0] = "";
    }

    public AdnRecord(int efid, int recordNumber, String alphaTag, String number, String[] emails, String anr, String anrA, String anrB, String anrC, String sne) {
        this.mAlphaTag = null;
        this.mNumber = null;
        this.mExtRecord = 255;
        this.mAnr = "";
        this.mAnrA = "";
        this.mAnrB = "";
        this.mAnrC = "";
        this.mSne = "";
        this.mEfid = efid;
        this.mRecordNumber = recordNumber;
        this.mAlphaTag = alphaTag;
        this.mNumber = number;
        this.mEmails = emails;
        this.mAnr = anr;
        this.mAnrA = anrA;
        this.mAnrB = anrB;
        this.mAnrC = anrC;
        this.mSne = sne;
    }

    public String getAlphaTag() {
        return this.mAlphaTag;
    }

    public String getNumber() {
        return this.mNumber;
    }

    public String[] getEmails() {
        return this.mEmails;
    }

    public int getRecordNumber() {
        return this.mRecordNumber;
    }

    public String getAnr() {
        return this.mAnr;
    }

    public String getAnrA() {
        return this.mAnrA;
    }

    public String getAnrB() {
        return this.mAnrB;
    }

    public String getAnrC() {
        return this.mAnrC;
    }

    public String getSne() {
        return this.mSne;
    }

    public void setEmails(String[] emails) {
        this.mEmails = emails;
    }

    public String toString() {
        return "ADN Record '" + this.mAlphaTag + "' '" + this.mNumber + " " + this.mEmails + "'";
    }

    public boolean isEmpty() {
        return TextUtils.isEmpty(this.mAlphaTag) && TextUtils.isEmpty(this.mNumber) && this.mEmails == null;
    }

    public boolean hasExtendedRecord() {
        return (this.mExtRecord == 0 || this.mExtRecord == 255) ? false : true;
    }

    private static boolean stringCompareNullEqualsEmpty(String s1, String s2) {
        if (s1 == s2) {
            return true;
        }
        if (s1 == null) {
            s1 = "";
        }
        if (s2 == null) {
            s2 = "";
        }
        return s1.equals(s2);
    }

    public boolean isEqual(AdnRecord adn) {
        return this.mAlphaTag.equals(adn.getAlphaTag()) && isEmailEqual(adn) && isNumberEqual(adn) && isAnrEqual(adn) && isAnrAEqual(adn) && isAnrBEqual(adn) && isAnrCEqual(adn) && isSneEqual(adn);
    }

    public boolean isEmailEqual(AdnRecord adn) {
        return Arrays.equals(this.mEmails, adn.getEmails());
    }

    public boolean isNumberEqual(AdnRecord adn) {
        if (this.mNumber != null && !this.mNumber.equals("")) {
            return this.mNumber.equals(adn.getNumber());
        }
        if (adn.getNumber() == null || adn.getNumber().equals("")) {
            return true;
        }
        return false;
    }

    public boolean isAnrEqual(AdnRecord adn) {
        if (this.mAnr != null && !this.mAnr.equals("")) {
            return this.mAnr.equals(adn.getAnr());
        }
        if (adn.getAnr() == null || adn.getAnr().equals("")) {
            return true;
        }
        return false;
    }

    public boolean isAnrAEqual(AdnRecord adn) {
        if (this.mAnrA != null && !this.mAnrA.equals("")) {
            return this.mAnrA.equals(adn.getAnrA());
        }
        if (adn.getAnrA() == null || adn.getAnrA().equals("")) {
            return true;
        }
        return false;
    }

    public boolean isAnrBEqual(AdnRecord adn) {
        if (this.mAnrB != null && !this.mAnrB.equals("")) {
            return this.mAnrB.equals(adn.getAnrB());
        }
        if (adn.getAnrB() == null || adn.getAnrB().equals("")) {
            return true;
        }
        return false;
    }

    public boolean isAnrCEqual(AdnRecord adn) {
        if (this.mAnrC != null && !this.mAnrC.equals("")) {
            return this.mAnrC.equals(adn.getAnrC());
        }
        if (adn.getAnrC() == null || adn.getAnrC().equals("")) {
            return true;
        }
        return false;
    }

    public boolean isSneEqual(AdnRecord adn) {
        if (this.mSne != null && !this.mSne.equals("")) {
            return this.mSne.equals(adn.getSne());
        }
        if (adn.getSne() == null || adn.getSne().equals("")) {
            return true;
        }
        return false;
    }

    public int describeContents() {
        return 0;
    }

    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(this.mEfid);
        dest.writeInt(this.mRecordNumber);
        dest.writeString(this.mAlphaTag);
        dest.writeString(this.mNumber);
        dest.writeStringArray(this.mEmails);
        dest.writeString(this.mAnr);
        dest.writeString(this.mAnrA);
        dest.writeString(this.mAnrB);
        dest.writeString(this.mAnrC);
        dest.writeString(this.mSne);
    }

    public byte[] buildAdnString(int recordSize) {
        boolean cmpResult;
        byte[] adnString;
        int footerOffset = recordSize - 14;
        if (this.mNumber == null || this.mNumber.equals("")) {
            cmpResult = true;
        } else {
            cmpResult = false;
        }
        int i;
        if (cmpResult) {
            Rlog.i(LOG_TAG, "[buildAdnString] Empty alpha tag or mNumber");
            adnString = new byte[recordSize];
            for (i = 0; i < recordSize; i++) {
                adnString[i] = (byte) -1;
            }
        } else if (TextUtils.isEmpty(this.mNumber)) {
            Rlog.w(LOG_TAG, "[buildAdnString] Empty dialing number");
            return null;
        } else if (this.mNumber.length() > 20) {
            Rlog.w(LOG_TAG, "[buildAdnString] Max length of dialing number is 20");
            return null;
        } else if (this.mAlphaTag == null || this.mAlphaTag.length() <= footerOffset) {
            adnString = new byte[recordSize];
            for (i = 0; i < recordSize; i++) {
                adnString[i] = (byte) -1;
            }
            byte[] bcdNumber = PhoneNumberUtils.numberToCalledPartyBCD(this.mNumber);
            System.arraycopy(bcdNumber, 0, adnString, footerOffset + 1, bcdNumber.length);
            adnString[footerOffset + 0] = (byte) bcdNumber.length;
            adnString[footerOffset + 12] = (byte) -1;
            adnString[footerOffset + 13] = (byte) -1;
            if (this.mAlphaTag == null || this.mAlphaTag.equals("")) {
                return adnString;
            }
            byte[] byteTag = GsmAlphabet.stringToGsm8BitPacked(this.mAlphaTag);
            System.arraycopy(byteTag, 0, adnString, 0, byteTag.length);
        } else {
            Rlog.w(LOG_TAG, "[buildAdnString] Max length of tag is " + footerOffset);
            return null;
        }
        return adnString;
    }

    public void appendExtRecord(byte[] extRecord) {
        try {
            if (extRecord.length == 13 && (extRecord[0] & 3) == 2 && (extRecord[1] & 255) <= 10) {
                this.mNumber += PhoneNumberUtils.calledPartyBCDFragmentToString(extRecord, 2, extRecord[1] & 255);
            }
        } catch (RuntimeException ex) {
            Rlog.w(LOG_TAG, "Error parsing AdnRecord ext record", ex);
        }
    }

    private void parseRecord(byte[] record) {
        try {
            this.mAlphaTag = IccUtils.adnStringFieldToString(record, 0, record.length - 14);
            int footerOffset = record.length - 14;
            int numberLength = record[footerOffset] & 255;
            if (numberLength > 11) {
                numberLength = 11;
            }
            this.mNumber = PhoneNumberUtils.calledPartyBCDToString(record, footerOffset + 1, numberLength);
            this.mExtRecord = record[record.length - 1] & 255;
            this.mEmails = null;
        } catch (RuntimeException ex) {
            Rlog.w(LOG_TAG, "Error parsing AdnRecord", ex);
            this.mNumber = "";
            this.mAlphaTag = "";
            this.mEmails = null;
        }
    }
}
