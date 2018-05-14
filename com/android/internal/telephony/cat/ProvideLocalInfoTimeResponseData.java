package com.android.internal.telephony.cat;

import java.io.ByteArrayOutputStream;

/* compiled from: ResponseData */
class ProvideLocalInfoTimeResponseData extends ResponseData {
    private byte digit0;
    private byte digit1;
    private byte length = (byte) 7;
    private byte tag = (byte) -90;
    private byte[] timeInfo = new byte[7];

    public ProvideLocalInfoTimeResponseData(int year, int month, int day, int hour, int min, int sec, int zone) {
        this.digit0 = (byte) (year % 10);
        this.digit1 = (byte) ((year % 100) / 10);
        this.timeInfo[0] = (byte) (((this.digit0 & 15) << 4) | this.digit1);
        month++;
        this.digit0 = (byte) (month % 10);
        this.digit1 = (byte) (month / 10);
        this.timeInfo[1] = (byte) (((this.digit0 & 15) << 4) | this.digit1);
        this.digit0 = (byte) (day % 10);
        this.digit1 = (byte) (day / 10);
        this.timeInfo[2] = (byte) (((this.digit0 & 15) << 4) | this.digit1);
        this.digit0 = (byte) (hour % 10);
        this.digit1 = (byte) (hour / 10);
        this.timeInfo[3] = (byte) (((this.digit0 & 15) << 4) | this.digit1);
        this.digit0 = (byte) (min % 10);
        this.digit1 = (byte) (min / 10);
        this.timeInfo[4] = (byte) (((this.digit0 & 15) << 4) | this.digit1);
        this.digit0 = (byte) (sec % 10);
        this.digit1 = (byte) (sec / 10);
        this.timeInfo[5] = (byte) (((this.digit0 & 15) << 4) | this.digit1);
        this.timeInfo[6] = (byte) zone;
    }

    public void format(ByteArrayOutputStream buf) {
        buf.write(this.tag);
        buf.write(this.length);
        for (int i = 0; i < 7; i++) {
            buf.write(this.timeInfo[i]);
        }
    }
}
