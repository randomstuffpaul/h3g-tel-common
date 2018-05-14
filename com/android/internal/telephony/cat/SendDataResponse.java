package com.android.internal.telephony.cat;

import java.io.ByteArrayOutputStream;

/* compiled from: ResponseData */
class SendDataResponse extends ResponseData {
    int emptySpaceInTxBuf;

    public void format(ByteArrayOutputStream buf) {
        buf.write(ComprehensionTlvTag.CHANNEL_DATA_LENGTH.value() | 128);
        buf.write(1);
        if (this.emptySpaceInTxBuf < 0) {
            this.emptySpaceInTxBuf = 0;
        }
        if (this.emptySpaceInTxBuf > 254) {
            this.emptySpaceInTxBuf = 255;
        }
        buf.write(this.emptySpaceInTxBuf);
    }

    public SendDataResponse(int emptySpaceInTxBuf) {
        this.emptySpaceInTxBuf = emptySpaceInTxBuf;
    }
}
