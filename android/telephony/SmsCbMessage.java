package android.telephony;

import android.os.Parcel;
import android.os.Parcelable;
import android.os.Parcelable.Creator;
import com.android.internal.telephony.WspTypeDecoder;

public class SmsCbMessage implements Parcelable {
    public static final Creator<SmsCbMessage> CREATOR = new C00051();
    public static final int GEOGRAPHICAL_SCOPE_CELL_WIDE = 3;
    public static final int GEOGRAPHICAL_SCOPE_CELL_WIDE_IMMEDIATE = 0;
    public static final int GEOGRAPHICAL_SCOPE_LA_WIDE = 2;
    public static final int GEOGRAPHICAL_SCOPE_PLMN_WIDE = 1;
    protected static final String LOG_TAG = "SMSCB";
    public static final int MESSAGE_FORMAT_3GPP = 1;
    public static final int MESSAGE_FORMAT_3GPP2 = 2;
    public static final int MESSAGE_PRIORITY_EMERGENCY = 3;
    public static final int MESSAGE_PRIORITY_INTERACTIVE = 1;
    public static final int MESSAGE_PRIORITY_NORMAL = 0;
    public static final int MESSAGE_PRIORITY_URGENT = 2;
    private final String mBody;
    private final SmsCbCmasInfo mCmasWarningInfo;
    private final SmsCbEtwsInfo mEtwsWarningInfo;
    private final int mGeographicalScope;
    private final String mLanguage;
    private final SmsCbLocation mLocation;
    private final int mMessageFormat;
    private final int mPriority;
    private final int mSerialNumber;
    private final int mServiceCategory;

    static class C00051 implements Creator<SmsCbMessage> {
        C00051() {
        }

        public SmsCbMessage createFromParcel(Parcel in) {
            return new SmsCbMessage(in);
        }

        public SmsCbMessage[] newArray(int size) {
            return new SmsCbMessage[size];
        }
    }

    public SmsCbMessage(int messageFormat, int geographicalScope, int serialNumber, SmsCbLocation location, int serviceCategory, String language, String body, int priority, SmsCbEtwsInfo etwsWarningInfo, SmsCbCmasInfo cmasWarningInfo) {
        this.mMessageFormat = messageFormat;
        this.mGeographicalScope = geographicalScope;
        this.mSerialNumber = serialNumber;
        this.mLocation = location;
        this.mServiceCategory = serviceCategory;
        this.mLanguage = language;
        this.mBody = body;
        this.mPriority = priority;
        this.mEtwsWarningInfo = etwsWarningInfo;
        this.mCmasWarningInfo = cmasWarningInfo;
    }

    public SmsCbMessage(Parcel in) {
        this.mMessageFormat = in.readInt();
        this.mGeographicalScope = in.readInt();
        this.mSerialNumber = in.readInt();
        this.mLocation = new SmsCbLocation(in);
        this.mServiceCategory = in.readInt();
        this.mLanguage = in.readString();
        this.mBody = in.readString();
        this.mPriority = in.readInt();
        switch (in.readInt()) {
            case 67:
                this.mEtwsWarningInfo = null;
                this.mCmasWarningInfo = new SmsCbCmasInfo(in);
                return;
            case WspTypeDecoder.WSP_HEADER_CONTENT_DISPOSITION2 /*69*/:
                this.mEtwsWarningInfo = new SmsCbEtwsInfo(in);
                this.mCmasWarningInfo = null;
                return;
            default:
                this.mEtwsWarningInfo = null;
                this.mCmasWarningInfo = null;
                return;
        }
    }

    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(this.mMessageFormat);
        dest.writeInt(this.mGeographicalScope);
        dest.writeInt(this.mSerialNumber);
        this.mLocation.writeToParcel(dest, flags);
        dest.writeInt(this.mServiceCategory);
        dest.writeString(this.mLanguage);
        dest.writeString(this.mBody);
        dest.writeInt(this.mPriority);
        if (this.mEtwsWarningInfo != null) {
            dest.writeInt(69);
            this.mEtwsWarningInfo.writeToParcel(dest, flags);
        } else if (this.mCmasWarningInfo != null) {
            dest.writeInt(67);
            this.mCmasWarningInfo.writeToParcel(dest, flags);
        } else {
            dest.writeInt(48);
        }
    }

    public int getGeographicalScope() {
        return this.mGeographicalScope;
    }

    public int getSerialNumber() {
        return this.mSerialNumber;
    }

    public SmsCbLocation getLocation() {
        return this.mLocation;
    }

    public int getServiceCategory() {
        return this.mServiceCategory;
    }

    public String getLanguageCode() {
        return this.mLanguage;
    }

    public String getMessageBody() {
        return this.mBody;
    }

    public int getMessageFormat() {
        return this.mMessageFormat;
    }

    public int getMessagePriority() {
        return this.mPriority;
    }

    public SmsCbEtwsInfo getEtwsWarningInfo() {
        return this.mEtwsWarningInfo;
    }

    public SmsCbCmasInfo getCmasWarningInfo() {
        return this.mCmasWarningInfo;
    }

    public boolean isEmergencyMessage() {
        return this.mPriority == 3;
    }

    public boolean isEtwsMessage() {
        return this.mEtwsWarningInfo != null;
    }

    public boolean isCmasMessage() {
        return this.mCmasWarningInfo != null;
    }

    public int getCmasCategory() {
        try {
            return this.mCmasWarningInfo.getCategory();
        } catch (NullPointerException e) {
            Rlog.e(LOG_TAG, "Null pointer exception in getCmasCategory");
            return 0;
        }
    }

    public int getCmasResponseType() {
        try {
            return this.mCmasWarningInfo.getResponseType();
        } catch (NullPointerException e) {
            Rlog.e(LOG_TAG, "Null pointer exception in getCmasResponseType");
            return 0;
        }
    }

    public int getCmasSeverity() {
        try {
            return this.mCmasWarningInfo.getSeverity();
        } catch (NullPointerException e) {
            Rlog.e(LOG_TAG, "Null pointer exception in getCmasSeverity");
            return 0;
        }
    }

    public int getCmasUrgency() {
        try {
            return this.mCmasWarningInfo.getUrgency();
        } catch (NullPointerException e) {
            Rlog.e(LOG_TAG, "Null pointer exception in getCmasUrgency");
            return 0;
        }
    }

    public int getCmasCertainty() {
        try {
            return this.mCmasWarningInfo.getCertainty();
        } catch (NullPointerException e) {
            Rlog.e(LOG_TAG, "Null pointer exception in getCmasCertainty");
            return 0;
        }
    }

    public int getCmasMessageID() {
        try {
            return this.mCmasWarningInfo.getMessageID();
        } catch (NullPointerException e) {
            Rlog.e(LOG_TAG, "Null pointer exception in getCmasMessageID");
            return 0;
        }
    }

    public int getCmasAlertHandling() {
        try {
            return this.mCmasWarningInfo.getAlertHandling();
        } catch (NullPointerException e) {
            Rlog.e(LOG_TAG, "Null pointer exception in getCmasAlertHandling");
            return 0;
        }
    }

    public long getCmasMsgExpires() {
        try {
            return this.mCmasWarningInfo.getMsgExpires();
        } catch (NullPointerException e) {
            Rlog.e(LOG_TAG, "Null pointer exception in getCmasMsgExpires");
            return 0;
        }
    }

    public boolean getCmasRecordTypeZeroExists() {
        try {
            return this.mCmasWarningInfo.getCMASRecordTypeZeroExists();
        } catch (NullPointerException e) {
            Rlog.e(LOG_TAG, "Null pointer exception in getCmasRecordTypeZeroExists");
            return false;
        }
    }

    public boolean getCmasRecordTypeFirstExists() {
        try {
            return this.mCmasWarningInfo.getCMASRecordTypeFirstExists();
        } catch (NullPointerException e) {
            Rlog.e(LOG_TAG, "Null pointer exception in getCmasRecordTypeFirstExists");
            return false;
        }
    }

    public boolean getCmasRecordTypeSecondExists() {
        try {
            return this.mCmasWarningInfo.getCMASRecordTypeSecondExists();
        } catch (NullPointerException e) {
            Rlog.e(LOG_TAG, "Null pointer exception in getCmasRecordTypeSecondExists");
            return false;
        }
    }

    public boolean getCmasRecordTypeThirdExists() {
        try {
            return this.mCmasWarningInfo.getCMASRecordTypeThirdExists();
        } catch (NullPointerException e) {
            Rlog.e(LOG_TAG, "Null pointer exception in getCmasRecordTypeThirdExists");
            return false;
        }
    }

    public String toString() {
        return "SmsCbMessage{geographicalScope=" + this.mGeographicalScope + ", serialNumber=" + this.mSerialNumber + ", location=" + this.mLocation + ", serviceCategory=" + this.mServiceCategory + ", language=" + this.mLanguage + ", body=" + this.mBody + ", priority=" + this.mPriority + (this.mEtwsWarningInfo != null ? ", " + this.mEtwsWarningInfo.toString() : "") + (this.mCmasWarningInfo != null ? ", " + this.mCmasWarningInfo.toString() : "") + '}';
    }

    public int describeContents() {
        return 0;
    }
}
