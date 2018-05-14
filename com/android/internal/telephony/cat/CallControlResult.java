package com.android.internal.telephony.cat;

/* compiled from: CatService */
enum CallControlResult {
    CALL_CONTROL_NO_CONTROL(0),
    CALL_CONTROL_ALLOWED_NO_MOD(1),
    CALL_CONTROL_NOT_ALLOWED(2),
    CALL_CONTROL_ALLOWED_WITH_MOD(3);
    
    private int mValue;

    private CallControlResult(int value) {
        this.mValue = value;
    }

    public int value() {
        return this.mValue;
    }

    public static CallControlResult fromInt(int value) {
        for (CallControlResult e : values()) {
            if (e.mValue == value) {
                return e;
            }
        }
        return null;
    }
}
