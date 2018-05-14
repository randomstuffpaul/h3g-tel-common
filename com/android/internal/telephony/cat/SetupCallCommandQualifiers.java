package com.android.internal.telephony.cat;

/* compiled from: CatService */
enum SetupCallCommandQualifiers {
    SET_UP_CALL_BUT_ONLY_IF_NOT_CURRENTLY_BUSY_ON_ANOTHER_CALL(0),
    SET_UP_CALL_BUT_ONLY_IF_NOT_CURRENTLY_BUSY_ON_ANOTHER_CALL_WITH_REDIAL(1),
    SET_UP_CALL_PUTTING_ALL_OTHER_CALLS_ON_HOLD(2),
    SET_UP_CALL_PUTTING_ALL_OTHER_CALLS_ON_HOLD_WITH_REDIAL(3),
    SET_UP_CALL_DISCONNECTING_ALL_OTHER_CALLS(4),
    SET_UP_CALL_DISCONNECTING_ALL_OTHER_CALLS_WITH_REDIAL(5);
    
    private int mValue;

    private SetupCallCommandQualifiers(int value) {
        this.mValue = value;
    }

    public int value() {
        return this.mValue;
    }

    public static SetupCallCommandQualifiers fromInt(int value) {
        for (SetupCallCommandQualifiers e : values()) {
            if (e.mValue == value) {
                return e;
            }
        }
        return null;
    }
}
