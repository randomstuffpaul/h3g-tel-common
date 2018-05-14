package com.android.internal.telephony.cat;

public enum CloseChannelMode {
    CLOSE_TCP_AND_TCP_IN_CLOSED_STATE(0),
    CLOSE_TCP_AND_TCP_IN_LISTEN_STATE(1);
    
    private int mValue;

    private CloseChannelMode(int value) {
        this.mValue = value;
    }

    public int value() {
        return this.mValue;
    }

    public static CloseChannelMode fromInt(int value) {
        for (CloseChannelMode e : values()) {
            if (e.mValue == value) {
                return e;
            }
        }
        return null;
    }
}
