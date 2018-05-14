package com.android.internal.telephony.uicc;

import android.os.Parcel;
import android.os.Parcelable;
import android.os.Parcelable.Creator;

public class UsimPhonebookCapaInfo implements Parcelable {
    public static final Creator<UsimPhonebookCapaInfo> CREATOR = new C01371();
    public static final int ENTRY_LENGTH = 2;
    public static final int FIELD_3GPP_ANR = 3;
    public static final int FIELD_3GPP_ANRA = 8;
    public static final int FIELD_3GPP_ANRB = 9;
    public static final int FIELD_3GPP_ANRC = 10;
    public static final int FIELD_3GPP_EMAIL = 4;
    public static final int FIELD_3GPP_EMAILA = 11;
    public static final int FIELD_3GPP_EMAILB = 12;
    public static final int FIELD_3GPP_EMAILC = 13;
    public static final int FIELD_3GPP_GRP = 6;
    public static final int FIELD_3GPP_NAME = 1;
    public static final int FIELD_3GPP_NUMBER = 2;
    public static final int FIELD_3GPP_PBC = 7;
    public static final int FIELD_3GPP_SNE = 5;
    public static final int FIELD_TYPE_TAG = 0;
    static final int MAX_3GPP_FIELD = 13;
    public static final int MAX_DATA_LENGTH = 4;
    public static final int MAX_INDEX = 1;
    public static final int USED_RECORD = 3;
    private int[] mFieldTypeInfo;

    static class C01371 implements Creator<UsimPhonebookCapaInfo> {
        C01371() {
        }

        public UsimPhonebookCapaInfo createFromParcel(Parcel source) {
            int[] temp = new int[52];
            source.readIntArray(temp);
            return new UsimPhonebookCapaInfo(temp);
        }

        public UsimPhonebookCapaInfo[] newArray(int size) {
            return new UsimPhonebookCapaInfo[size];
        }
    }

    public void writeToParcel(Parcel dest, int flags) {
        dest.writeIntArray(this.mFieldTypeInfo);
    }

    public int describeContents() {
        return 0;
    }

    public UsimPhonebookCapaInfo() {
        this.mFieldTypeInfo = new int[52];
    }

    public UsimPhonebookCapaInfo(int[] data) {
        this.mFieldTypeInfo = data;
    }

    public int getFieldInfo(int fieldType, int fieldInfo) {
        for (int i = 0; i < 52; i += 4) {
            if (this.mFieldTypeInfo[i] == fieldType) {
                return this.mFieldTypeInfo[i + fieldInfo];
            }
        }
        return 0;
    }
}
