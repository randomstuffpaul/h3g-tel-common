package com.android.internal.telephony.cat;

import android.os.Parcel;
import android.os.Parcelable;
import android.os.Parcelable.Creator;

public class TransportLevel implements Parcelable {
    public static final Creator<TransportLevel> CREATOR = new C00671();
    public int portNumber;
    public byte transportProtocol;

    static class C00671 implements Creator<TransportLevel> {
        C00671() {
        }

        public TransportLevel createFromParcel(Parcel in) {
            return new TransportLevel(in);
        }

        public TransportLevel[] newArray(int size) {
            return new TransportLevel[size];
        }
    }

    TransportLevel() {
        this.transportProtocol = (byte) 0;
        this.portNumber = 0;
    }

    private TransportLevel(Parcel in) {
        this.transportProtocol = (byte) 0;
        this.portNumber = 0;
        this.transportProtocol = in.readByte();
        this.portNumber = in.readInt();
    }

    public int describeContents() {
        return 0;
    }

    public void writeToParcel(Parcel dest, int flags) {
        dest.writeByte(this.transportProtocol);
        dest.writeInt(this.portNumber);
    }

    public boolean isServer() {
        return this.transportProtocol == (byte) 3;
    }

    public boolean isLocal() {
        return this.transportProtocol == (byte) 4 || this.transportProtocol == (byte) 5;
    }

    public boolean isTCPRemoteClient() {
        return this.transportProtocol == (byte) 2;
    }

    public boolean isUDPRemoteClient() {
        return this.transportProtocol == (byte) 1;
    }

    public boolean isRemoteClient() {
        return this.transportProtocol == (byte) 1 || this.transportProtocol == (byte) 2;
    }
}
