package com.android.internal.telephony.cat;

import android.os.Parcel;
import android.os.Parcelable;
import android.os.Parcelable.Creator;

public class BearerDefault implements Parcelable {
    public static final Creator<BearerDefault> CREATOR = new C00401();
    public int var1;

    static class C00401 implements Creator<BearerDefault> {
        C00401() {
        }

        public BearerDefault createFromParcel(Parcel in) {
            return new BearerDefault(in);
        }

        public BearerDefault[] newArray(int size) {
            return new BearerDefault[size];
        }
    }

    BearerDefault() {
    }

    private BearerDefault(Parcel in) {
        this.var1 = in.readInt();
    }

    public int describeContents() {
        return 0;
    }

    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(this.var1);
    }
}
