package com.android.internal.telephony.cat;

/* compiled from: CatService */
enum CallType {
    CALL_TYPE_MO_VOICE(0),
    CALL_TYPE_MO_SMS(1),
    CALL_TYPE_MO_SS(2),
    CALL_TYPE_MO_USSD(3),
    CALL_TYPE_PDP_CTXT(4);
    
    private int mValue;

    private CallType(int value) {
        this.mValue = value;
    }

    public int value() {
        return this.mValue;
    }

    public static CallType fromInt(int value) {
        for (CallType e : values()) {
            if (e.mValue == value) {
                return e;
            }
        }
        return null;
    }
}
