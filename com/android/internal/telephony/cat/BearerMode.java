package com.android.internal.telephony.cat;

import android.os.Parcel;
import android.os.Parcelable;
import android.os.Parcelable.Creator;

/* compiled from: BearerDescription */
class BearerMode implements Parcelable {
    public static final Creator<BearerMode> CREATOR = new C00441();
    public boolean isAutoReconnect;
    public boolean isBackgroundMode;
    public boolean isOnDemand;

    /* compiled from: BearerDescription */
    static class C00441 implements Creator<BearerMode> {
        C00441() {
        }

        public BearerMode createFromParcel(Parcel in) {
            return new BearerMode(in);
        }

        public BearerMode[] newArray(int size) {
            return new BearerMode[size];
        }
    }

    BearerMode() {
        this.isOnDemand = false;
        this.isAutoReconnect = false;
        this.isBackgroundMode = false;
    }

    private BearerMode(Parcel in) {
        this.isOnDemand = false;
        this.isAutoReconnect = false;
        this.isBackgroundMode = false;
        boolean[] tempBooleanArray = new boolean[1];
        in.readBooleanArray(tempBooleanArray);
        this.isOnDemand = tempBooleanArray[0];
        in.readBooleanArray(tempBooleanArray);
        this.isAutoReconnect = tempBooleanArray[0];
        in.readBooleanArray(tempBooleanArray);
        this.isBackgroundMode = tempBooleanArray[0];
    }

    public int describeContents() {
        return 0;
    }

    public void writeToParcel(Parcel dest, int flags) {
        boolean[] tempBooleanArray = new boolean[]{this.isOnDemand};
        dest.writeBooleanArray(tempBooleanArray);
        tempBooleanArray[0] = this.isAutoReconnect;
        dest.writeBooleanArray(tempBooleanArray);
        tempBooleanArray[0] = this.isBackgroundMode;
        dest.writeBooleanArray(tempBooleanArray);
    }
}
