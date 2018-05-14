package com.android.internal.telephony;

public class SmsResponse {
    String mAckPdu;
    int mErrorClass;
    int mErrorCode;
    int mMessageRef;

    public SmsResponse(int messageRef, String ackPdu, int errorCode) {
        this.mMessageRef = messageRef;
        this.mAckPdu = ackPdu;
        this.mErrorCode = errorCode;
    }

    public SmsResponse(int messageRef, String ackPdu, int errorCode, int errorClass) {
        this.mMessageRef = messageRef;
        this.mAckPdu = ackPdu;
        this.mErrorCode = errorCode;
        this.mErrorClass = errorClass;
    }

    public String toString() {
        return "{ mMessageRef = " + this.mMessageRef + ", mErrorCode = " + this.mErrorCode + ", mAckPdu = " + this.mAckPdu + "}";
    }
}
