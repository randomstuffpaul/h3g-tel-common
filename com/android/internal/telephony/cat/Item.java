package com.android.internal.telephony.cat;

import android.graphics.Bitmap;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.Parcelable.Creator;

public class Item implements Parcelable {
    public static final Creator<Item> CREATOR = new C00601();
    public Bitmap icon;
    public int id;
    public String text;

    static class C00601 implements Creator<Item> {
        C00601() {
        }

        public Item createFromParcel(Parcel in) {
            return new Item(in);
        }

        public Item[] newArray(int size) {
            return new Item[size];
        }
    }

    public Item(int id, String text) {
        this.id = id;
        this.text = text;
        this.icon = null;
    }

    public Item(Parcel in) {
        this.id = in.readInt();
        this.text = in.readString();
        this.icon = (Bitmap) in.readParcelable(null);
    }

    public int describeContents() {
        return 0;
    }

    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(this.id);
        dest.writeString(this.text);
        dest.writeParcelable(this.icon, flags);
    }

    public String toString() {
        return this.text;
    }
}
