package com.android.internal.telephony.cat;

import android.os.Parcel;
import android.os.Parcelable;
import android.os.Parcelable.Creator;
import java.io.ByteArrayOutputStream;

public class BearerEUTRAN implements Parcelable {
    public static final Creator<BearerEUTRAN> CREATOR = new C00421();
    public int mPdnType;
    public int mQci;

    static class C00421 implements Creator<BearerEUTRAN> {
        C00421() {
        }

        public BearerEUTRAN createFromParcel(Parcel in) {
            return new BearerEUTRAN(in);
        }

        public BearerEUTRAN[] newArray(int size) {
            return new BearerEUTRAN[size];
        }
    }

    BearerEUTRAN() {
        this.mQci = -1;
        this.mPdnType = 0;
    }

    private BearerEUTRAN(Parcel in) {
        this.mQci = in.readInt();
        this.mPdnType = in.readInt();
    }

    public void setup(byte[] data, int length, int offset) {
        if (length > 3) {
            this.mQci = data[offset];
        }
        this.mPdnType = data[(offset + length) - 2];
    }

    public int describeContents() {
        return 0;
    }

    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(this.mQci);
        dest.writeInt(this.mPdnType);
    }

    public void writeParametersTobuffer(ByteArrayOutputStream buf) {
        if (this.mQci > 0) {
            buf.write(this.mQci);
        }
        buf.write(this.mPdnType);
    }

    public void dump() {
        CatLog.m2d("Bearer E-UTRAN", "QCI: " + this.mQci + ", PDN Type: " + this.mPdnType);
    }
}
