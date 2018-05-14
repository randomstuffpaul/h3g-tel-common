package com.android.internal.telephony.cat;

/* compiled from: CatService */
enum CallStatus {
    CALL_STATUS_OUTGOING(1),
    CALL_STATUS_INCOMING(2),
    CALL_STATUS_CONNECTED(3),
    CALL_STATUS_RELEASED(4);
    
    private int mValue;

    private CallStatus(int value) {
        this.mValue = value;
    }

    public int value() {
        return this.mValue;
    }

    public static CallStatus fromInt(int value) {
        for (CallStatus e : values()) {
            if (e.mValue == value) {
                return e;
            }
        }
        return null;
    }
}
