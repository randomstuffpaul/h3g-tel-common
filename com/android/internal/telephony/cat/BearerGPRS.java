package com.android.internal.telephony.cat;

import android.os.Parcel;
import android.os.Parcelable;
import android.os.Parcelable.Creator;
import java.io.ByteArrayOutputStream;

public class BearerGPRS implements Parcelable {
    public static final Creator<BearerGPRS> CREATOR = new C00431();
    public int delayClass;
    public int meanThroughputClass;
    public int packetDataProtocolType;
    public int peakThroughputClass;
    public int precedenceClass;
    public int reliabilityClass;

    static class C00431 implements Creator<BearerGPRS> {
        C00431() {
        }

        public BearerGPRS createFromParcel(Parcel in) {
            return new BearerGPRS(in);
        }

        public BearerGPRS[] newArray(int size) {
            return new BearerGPRS[size];
        }
    }

    BearerGPRS() {
    }

    private BearerGPRS(Parcel in) {
        this.precedenceClass = in.readInt();
        this.delayClass = in.readInt();
        this.reliabilityClass = in.readInt();
        this.peakThroughputClass = in.readInt();
        this.meanThroughputClass = in.readInt();
        this.packetDataProtocolType = in.readInt();
    }

    public int describeContents() {
        return 0;
    }

    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(this.precedenceClass);
        dest.writeInt(this.delayClass);
        dest.writeInt(this.reliabilityClass);
        dest.writeInt(this.peakThroughputClass);
        dest.writeInt(this.meanThroughputClass);
        dest.writeInt(this.packetDataProtocolType);
    }

    public void writeParametersTobuffer(ByteArrayOutputStream buf) {
        buf.write(this.precedenceClass);
        buf.write(this.delayClass);
        buf.write(this.reliabilityClass);
        buf.write(this.peakThroughputClass);
        buf.write(this.meanThroughputClass);
        buf.write(this.packetDataProtocolType);
    }
}
