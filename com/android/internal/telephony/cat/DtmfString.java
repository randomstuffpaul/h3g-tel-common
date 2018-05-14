package com.android.internal.telephony.cat;

/* compiled from: CatService */
class DtmfString {
    public String dtfmString;
    public int dtmfStringLength;
    public int pointer = 0;

    DtmfString(int dtmfStringLength, byte[] dtfmString) {
        this.dtmfStringLength = dtmfStringLength;
        this.dtfmString = new String(dtfmString);
    }
}
