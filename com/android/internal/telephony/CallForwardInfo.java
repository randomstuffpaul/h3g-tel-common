package com.android.internal.telephony;

import android.telephony.PhoneNumberUtils;

public class CallForwardInfo {
    public String number;
    public int reason;
    public int serviceClass;
    public int status;
    public int timeSeconds;
    public int toa;

    public String toString() {
        return super.toString() + (this.status == 0 ? " not active " : " active ") + " reason: " + this.reason + " serviceClass: " + this.serviceClass + " \"" + PhoneNumberUtils.stringFromStringAndTOA(this.number, this.toa) + "\" " + this.timeSeconds + " seconds";
    }
}
