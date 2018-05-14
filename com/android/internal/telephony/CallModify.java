package com.android.internal.telephony;

public class CallModify {
    public CallDetails call_details;
    public int call_index;

    public CallModify() {
        this.call_index = 0;
        this.call_details = new CallDetails();
    }

    public CallModify(CallDetails callDetails, int callIndex) {
        this.call_index = callIndex;
        this.call_details = callDetails;
    }

    public void setCallDetails(CallDetails calldetails) {
        this.call_details = new CallDetails(calldetails);
    }

    public String toString() {
        return " " + this.call_index + " " + this.call_details;
    }
}
