package com.android.internal.telephony;

public interface MmiCode {

    public enum State {
        PENDING,
        CANCELLED,
        COMPLETE,
        FAILED
    }

    void cancel();

    String getDialString();

    CharSequence getMessage();

    Phone getPhone();

    State getState();

    CharSequence getUssdCode();

    boolean isCancelable();

    boolean isUssdRequest();
}
