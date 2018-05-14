package android.telephony;

import android.os.Parcel;
import android.os.Parcelable;
import android.os.Parcelable.Creator;

public final class LmsTokenCTC implements Parcelable {
    public static final Creator<LmsTokenCTC> CREATOR = new C00011();
    public static final int LMS_STATUS_COMPLETE = 0;
    public static final int LMS_STATUS_FIRST_DISPLAY_TIMEOUT = 1;
    public static final int LMS_STATUS_MAXIMAL_CONNECTION_TIMEOUT = 2;
    public final String address;
    public final String format;
    public final int msgCount;
    public final int refNumber;

    static class C00011 implements Creator<LmsTokenCTC> {
        C00011() {
        }

        public LmsTokenCTC createFromParcel(Parcel in) {
            return new LmsTokenCTC(in);
        }

        public LmsTokenCTC[] newArray(int size) {
            return new LmsTokenCTC[size];
        }
    }

    public LmsTokenCTC(String address, int refNumber, int msgCount, String format) {
        this.address = address;
        this.refNumber = refNumber;
        this.msgCount = msgCount;
        this.format = format;
    }

    public LmsTokenCTC(LmsTokenCTC token) {
        this.address = token.address;
        this.refNumber = token.refNumber;
        this.msgCount = token.msgCount;
        this.format = token.format;
    }

    private LmsTokenCTC(Parcel in) {
        this.address = in.readString();
        this.refNumber = in.readInt();
        this.msgCount = in.readInt();
        this.format = in.readString();
    }

    public boolean equals(Object o) {
        if (o == this) {
            return true;
        }
        if (!(o instanceof LmsTokenCTC)) {
            return false;
        }
        LmsTokenCTC other = (LmsTokenCTC) o;
        if (this.refNumber == other.refNumber && this.msgCount == other.msgCount && this.format == other.format && this.address.equals(other.address)) {
            return true;
        }
        return false;
    }

    public int hashCode() {
        return ((((((this.address.hashCode() + 527) * 31) + this.refNumber) * 31) + this.msgCount) * 31) + this.format.hashCode();
    }

    public String toString() {
        return String.format("<address=%s; refNumber=%d, msgCount=%d, format=%s>", new Object[]{this.address, Integer.valueOf(this.refNumber), Integer.valueOf(this.msgCount), this.format});
    }

    public int describeContents() {
        return 0;
    }

    public void writeToParcel(Parcel out, int flags) {
        out.writeString(this.address);
        out.writeInt(this.refNumber);
        out.writeInt(this.msgCount);
        out.writeString(this.format);
    }
}
