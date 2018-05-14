package com.android.internal.telephony.cat;

import android.os.Parcel;
import android.os.Parcelable;
import android.os.Parcelable.Creator;

public class DataDestinationAddress implements Parcelable {
    public static final Creator<DataDestinationAddress> CREATOR = new C00571();
    public byte[] address;
    public byte addressType;

    static class C00571 implements Creator<DataDestinationAddress> {
        C00571() {
        }

        public DataDestinationAddress createFromParcel(Parcel in) {
            return new DataDestinationAddress(in);
        }

        public DataDestinationAddress[] newArray(int size) {
            return new DataDestinationAddress[size];
        }
    }

    DataDestinationAddress() {
    }

    private DataDestinationAddress(Parcel in) {
        this.addressType = in.readByte();
        int addLength = in.readInt();
        if (addLength == -1) {
            this.address = null;
            return;
        }
        this.address = new byte[addLength];
        in.readByteArray(this.address);
    }

    public int describeContents() {
        return 0;
    }

    public void writeToParcel(Parcel dest, int flags) {
        dest.writeByte(this.addressType);
        dest.writeInt(this.address == null ? -1 : this.address.length);
        dest.writeByteArray(this.address);
    }
}
