package com.android.internal.telephony.cat;

import android.os.Parcel;
import android.os.Parcelable;
import android.os.Parcelable.Creator;

public class ChannelStatus implements Parcelable {
    public static final Creator<ChannelStatus> CREATOR = new C00541();
    public int CS;
    public int additionalInfo;
    public int channelIdentifier;
    public int serverModeUICC;
    public int serverTerminalTCP;
    public int serverTerminalUDP;

    static class C00541 implements Creator<ChannelStatus> {
        C00541() {
        }

        public ChannelStatus createFromParcel(Parcel in) {
            return new ChannelStatus(in);
        }

        public ChannelStatus[] newArray(int size) {
            return new ChannelStatus[size];
        }
    }

    ChannelStatus() {
    }

    private ChannelStatus(Parcel in) {
        this.channelIdentifier = in.readInt();
        this.CS = in.readInt();
        this.serverModeUICC = in.readInt();
        this.serverTerminalTCP = in.readInt();
        this.serverTerminalUDP = in.readInt();
        this.additionalInfo = in.readInt();
    }

    public int describeContents() {
        return 0;
    }

    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(this.channelIdentifier);
        dest.writeInt(this.CS);
        dest.writeInt(this.serverModeUICC);
        dest.writeInt(this.serverTerminalTCP);
        dest.writeInt(this.serverTerminalUDP);
        dest.writeInt(this.additionalInfo);
    }
}
