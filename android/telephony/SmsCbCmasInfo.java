package android.telephony;

import android.os.Parcel;
import android.os.Parcelable;
import android.os.Parcelable.Creator;

public class SmsCbCmasInfo implements Parcelable {
    public static final int CMAS_AMBER = 4099;
    public static final int CMAS_CATEGORY_CBRNE = 10;
    public static final int CMAS_CATEGORY_ENV = 7;
    public static final int CMAS_CATEGORY_FIRE = 5;
    public static final int CMAS_CATEGORY_GEO = 0;
    public static final int CMAS_CATEGORY_HEALTH = 6;
    public static final int CMAS_CATEGORY_INFRA = 9;
    public static final int CMAS_CATEGORY_MET = 1;
    public static final int CMAS_CATEGORY_OTHER = 11;
    public static final int CMAS_CATEGORY_RESCUE = 4;
    public static final int CMAS_CATEGORY_SAFETY = 2;
    public static final int CMAS_CATEGORY_SECURITY = 3;
    public static final int CMAS_CATEGORY_TRANSPORT = 8;
    public static final int CMAS_CATEGORY_UNKNOWN = -1;
    public static final int CMAS_CERTAINTY_LIKELY = 1;
    public static final int CMAS_CERTAINTY_OBSERVED = 0;
    public static final int CMAS_CERTAINTY_UNKNOWN = -1;
    public static final int CMAS_CLASS_CHILD_ABDUCTION_EMERGENCY = 3;
    public static final int CMAS_CLASS_CMAS_EXERCISE = 5;
    public static final int CMAS_CLASS_EXTREME_THREAT = 1;
    public static final int CMAS_CLASS_OPERATOR_DEFINED_USE = 6;
    public static final int CMAS_CLASS_PRESIDENTIAL_LEVEL_ALERT = 0;
    public static final int CMAS_CLASS_REQUIRED_MONTHLY_TEST = 4;
    public static final int CMAS_CLASS_SEVERE_THREAT = 2;
    public static final int CMAS_CLASS_UNKNOWN = -1;
    public static final int CMAS_RESPONSE_TYPE_ASSESS = 6;
    public static final int CMAS_RESPONSE_TYPE_AVOID = 5;
    public static final int CMAS_RESPONSE_TYPE_EVACUATE = 1;
    public static final int CMAS_RESPONSE_TYPE_EXECUTE = 3;
    public static final int CMAS_RESPONSE_TYPE_MONITOR = 4;
    public static final int CMAS_RESPONSE_TYPE_NONE = 7;
    public static final int CMAS_RESPONSE_TYPE_PREPARE = 2;
    public static final int CMAS_RESPONSE_TYPE_SHELTER = 0;
    public static final int CMAS_RESPONSE_TYPE_UNKNOWN = -1;
    public static final int CMAS_SERVICE_EXTREME_THREAT_LIFE_PROPERTY = 4097;
    public static final int CMAS_SERVICE_PRESIDENTIAL_LEVEL_ALERT = 4096;
    public static final int CMAS_SERVICE_SEVERE_THREAT_LIFE_PROPERTY = 4098;
    public static final int CMAS_SEVERITY_EXTREME = 0;
    public static final int CMAS_SEVERITY_SEVERE = 1;
    public static final int CMAS_SEVERITY_UNKNOWN = -1;
    public static final int CMAS_TEST_MESSAGE = 4100;
    public static final int CMAS_URGENCY_EXPECTED = 1;
    public static final int CMAS_URGENCY_IMMEDIATE = 0;
    public static final int CMAS_URGENCY_UNKNOWN = -1;
    public static final Creator<SmsCbCmasInfo> CREATOR = new C00021();
    public static final int EUALERT_CLASS_EU_INFO = 7;
    private static final String LOG_TAG = "SmsCbCmasInfo";
    private int mAlertHandling;
    private int mCategory;
    private int mCertainty;
    private int mLanguage;
    private final int mMessageClass;
    private int mMessageID;
    private long mMsgExpires;
    private int mRecordType;
    private int mResponseType;
    private int mSeverity;
    private int mUrgency;

    static class C00021 implements Creator<SmsCbCmasInfo> {
        C00021() {
        }

        public SmsCbCmasInfo createFromParcel(Parcel in) {
            return new SmsCbCmasInfo(in);
        }

        public SmsCbCmasInfo[] newArray(int size) {
            return new SmsCbCmasInfo[size];
        }
    }

    public SmsCbCmasInfo(int messageClass, int category, int responseType, int severity, int urgency, int certainty) {
        this.mMessageClass = messageClass;
        this.mCategory = category;
        this.mResponseType = responseType;
        this.mSeverity = severity;
        this.mUrgency = urgency;
        this.mCertainty = certainty;
        this.mRecordType = 0;
        this.mMessageID = 0;
        this.mAlertHandling = 0;
        this.mMsgExpires = 0;
        this.mLanguage = 0;
    }

    public SmsCbCmasInfo(int messageClass, int category, int responseType, int severity, int urgency, int certainty, int recordTypeAll) {
        this.mMessageClass = messageClass;
        this.mCategory = category;
        this.mResponseType = responseType;
        this.mSeverity = severity;
        this.mUrgency = urgency;
        this.mCertainty = certainty;
        this.mRecordType = recordTypeAll;
    }

    public SmsCbCmasInfo(int messageClass, int messageID, int alertHandling, long msgExpires, int language, int recordTypeAll) {
        this.mMessageClass = messageClass;
        this.mMessageID = messageID;
        this.mAlertHandling = alertHandling;
        this.mMsgExpires = msgExpires;
        this.mLanguage = language;
        this.mRecordType = recordTypeAll;
    }

    public SmsCbCmasInfo(int messageClass, int category, int responseType, int severity, int urgency, int certainty, int messageID, int alertHandling, long msgExpires, int language, int recordTypeAll) {
        this.mMessageClass = messageClass;
        this.mCategory = category;
        this.mResponseType = responseType;
        this.mSeverity = severity;
        this.mUrgency = urgency;
        this.mCertainty = certainty;
        this.mMessageID = messageID;
        this.mAlertHandling = alertHandling;
        this.mMsgExpires = msgExpires;
        this.mLanguage = language;
        this.mRecordType = recordTypeAll;
    }

    SmsCbCmasInfo(Parcel in) {
        this.mMessageClass = in.readInt();
        this.mCategory = in.readInt();
        this.mResponseType = in.readInt();
        this.mSeverity = in.readInt();
        this.mUrgency = in.readInt();
        this.mCertainty = in.readInt();
        this.mMessageID = in.readInt();
        this.mLanguage = in.readInt();
        this.mAlertHandling = in.readInt();
        this.mMsgExpires = in.readLong();
        this.mRecordType = in.readInt();
    }

    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(this.mMessageClass);
        dest.writeInt(this.mCategory);
        dest.writeInt(this.mResponseType);
        dest.writeInt(this.mSeverity);
        dest.writeInt(this.mUrgency);
        dest.writeInt(this.mCertainty);
        dest.writeInt(this.mMessageID);
        dest.writeInt(this.mLanguage);
        dest.writeInt(this.mAlertHandling);
        dest.writeLong(this.mMsgExpires);
        dest.writeInt(this.mRecordType);
    }

    public int getMessageClass() {
        return this.mMessageClass;
    }

    public int getCategory() {
        return this.mCategory;
    }

    public int getResponseType() {
        return this.mResponseType;
    }

    public int getSeverity() {
        return this.mSeverity;
    }

    public int getUrgency() {
        return this.mUrgency;
    }

    public int getCertainty() {
        return this.mCertainty;
    }

    public int getMessageID() {
        return this.mMessageID;
    }

    public int getAlertHandling() {
        return this.mAlertHandling;
    }

    public long getMsgExpires() {
        try {
            return this.mMsgExpires;
        } catch (NullPointerException e) {
            Rlog.e(LOG_TAG, "Null pointer exception in getMsgExpires");
            return 0;
        }
    }

    public int getLanguage() {
        return this.mLanguage;
    }

    public boolean getCMASRecordTypeZeroExists() {
        try {
            if ((this.mRecordType & 1) == 1) {
                return true;
            }
            return false;
        } catch (NullPointerException e) {
            return false;
        }
    }

    public boolean getCMASRecordTypeFirstExists() {
        try {
            return (this.mRecordType & 2) == 2;
        } catch (NullPointerException e) {
            return false;
        }
    }

    public boolean getCMASRecordTypeSecondExists() {
        try {
            return (this.mRecordType & 4) == 4;
        } catch (NullPointerException e) {
            return false;
        }
    }

    public boolean getCMASRecordTypeThirdExists() {
        try {
            return (this.mRecordType & 8) == 8;
        } catch (NullPointerException e) {
            return false;
        }
    }

    public String toString() {
        return "SmsCbCmasInfo{messageClass=" + this.mMessageClass + ", category=" + this.mCategory + ", responseType=" + this.mResponseType + ", severity=" + this.mSeverity + ", urgency=" + this.mUrgency + ", certainty=" + this.mCertainty + ", recordType=" + this.mRecordType + ", messageID=" + this.mMessageID + ", alertHandling=" + this.mAlertHandling + ", language=" + this.mLanguage + ", mMsgExpires=" + this.mMsgExpires + '}';
    }

    public int describeContents() {
        return 0;
    }
}
