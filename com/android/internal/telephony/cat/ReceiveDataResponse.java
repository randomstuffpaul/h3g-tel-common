package com.android.internal.telephony.cat;

import java.io.ByteArrayOutputStream;

/* compiled from: ResponseData */
class ReceiveDataResponse extends ResponseData {
    int bytesRemaining;
    byte[] data;
    int dataLength;

    public void format(ByteArrayOutputStream buf) {
        buf.write(ComprehensionTlvTag.CHANNEL_DATA.value() | 128);
        if (this.dataLength < 128) {
            buf.write(this.dataLength);
        } else if (this.dataLength >= 128 && this.dataLength < 255) {
            buf.write(129);
            buf.write(this.dataLength);
        }
        for (int i = 0; i < this.dataLength; i++) {
            buf.write(this.data[i]);
        }
        buf.write(ComprehensionTlvTag.CHANNEL_DATA_LENGTH.value() | 128);
        buf.write(1);
        if (this.bytesRemaining < 0) {
            this.bytesRemaining = 0;
        }
        if (this.bytesRemaining > 254) {
            this.bytesRemaining = 255;
        }
        buf.write(this.bytesRemaining);
    }

    public ReceiveDataResponse(byte[] recvdata, int dataLen, int bytesInRxbuf) {
        this.data = recvdata;
        this.dataLength = dataLen;
        CatLog.m0d((Object) this, "temp[] length = " + this.dataLength + " dataLen = " + this.dataLength + " bytesInRxbuf = " + bytesInRxbuf);
        this.bytesRemaining = bytesInRxbuf;
    }
}
