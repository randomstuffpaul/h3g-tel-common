package com.android.internal.telephony.cat;

import android.os.Parcel;
import android.os.Parcelable;
import android.os.Parcelable.Creator;
import java.io.ByteArrayOutputStream;

public class BearerCSD implements Parcelable {
    public static final Creator<BearerCSD> CREATOR = new C00391();
    public int bearerService;
    public int connectionElement;
    public int dataRate;

    static class C00391 implements Creator<BearerCSD> {
        C00391() {
        }

        public BearerCSD createFromParcel(Parcel in) {
            return new BearerCSD(in);
        }

        public BearerCSD[] newArray(int size) {
            return new BearerCSD[size];
        }
    }

    BearerCSD() {
    }

    private BearerCSD(Parcel in) {
        this.dataRate = in.readInt();
        this.bearerService = in.readInt();
        this.connectionElement = in.readInt();
    }

    public int describeContents() {
        return 0;
    }

    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(this.dataRate);
        dest.writeInt(this.bearerService);
        dest.writeInt(this.connectionElement);
    }

    public void writeParametersTobuffer(ByteArrayOutputStream buf) {
        buf.write(this.dataRate);
        buf.write(this.bearerService);
        buf.write(this.connectionElement);
    }
}
