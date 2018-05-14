package com.android.internal.telephony.cat;

import android.os.Parcel;
import android.os.Parcelable;
import android.os.Parcelable.Creator;

public class BearerDescription implements Parcelable {
    static final byte BEARER_CDMA = (byte) 8;
    static final byte BEARER_CSD = (byte) 1;
    static final byte BEARER_DEFAULT = (byte) 3;
    static final byte BEARER_E_UTRAN = (byte) 11;
    static final byte BEARER_GPRS = (byte) 2;
    public static final Creator<BearerDescription> CREATOR = new C00411();
    public BearerCSD bearerCSD;
    public boolean bearerDefault;
    public BearerEUTRAN bearerEUTRAN;
    public BearerGPRS bearerGPRS;
    public byte bearerType;

    static class C00411 implements Creator<BearerDescription> {
        C00411() {
        }

        public BearerDescription createFromParcel(Parcel in) {
            return new BearerDescription(in);
        }

        public BearerDescription[] newArray(int size) {
            return new BearerDescription[size];
        }
    }

    BearerDescription() {
        this.bearerType = (byte) 0;
        this.bearerCSD = null;
        this.bearerGPRS = null;
        this.bearerEUTRAN = null;
        this.bearerDefault = false;
        this.bearerType = (byte) 0;
        this.bearerDefault = false;
        this.bearerCSD = null;
        this.bearerGPRS = null;
        this.bearerEUTRAN = null;
    }

    private BearerDescription(Parcel in) {
        this.bearerType = (byte) 0;
        this.bearerCSD = null;
        this.bearerGPRS = null;
        this.bearerEUTRAN = null;
        this.bearerDefault = false;
        this.bearerType = in.readByte();
        this.bearerCSD = (BearerCSD) in.readParcelable(null);
        this.bearerGPRS = (BearerGPRS) in.readParcelable(null);
        this.bearerEUTRAN = (BearerEUTRAN) in.readParcelable(null);
        boolean[] tempBooleanArray = new boolean[1];
        in.readBooleanArray(tempBooleanArray);
        this.bearerDefault = tempBooleanArray[0];
    }

    public int describeContents() {
        return 0;
    }

    public void writeToParcel(Parcel dest, int flags) {
        dest.writeByte(this.bearerType);
        dest.writeParcelable(this.bearerCSD, 0);
        dest.writeParcelable(this.bearerGPRS, 0);
        dest.writeParcelable(this.bearerEUTRAN, 0);
        dest.writeBooleanArray(new boolean[]{this.bearerDefault});
    }
}
