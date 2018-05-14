package com.android.internal.telephony.cat;

import com.android.internal.telephony.EncodeException;
import com.android.internal.telephony.GsmAlphabet;
import java.io.ByteArrayOutputStream;
import java.io.UnsupportedEncodingException;

/* compiled from: ResponseData */
class GetInkeyInputResponseData extends ResponseData {
    protected static final byte GET_INKEY_NO = (byte) 0;
    protected static final byte GET_INKEY_YES = (byte) 1;
    private byte mDuration;
    public String mInData;
    private boolean mIsDuration;
    private boolean mIsPacked;
    private boolean mIsUcs2;
    private boolean mIsYesNo;
    private byte mTimeUnit;
    private boolean mYesNoResponse;

    public GetInkeyInputResponseData(String inData, boolean ucs2, boolean packed) {
        this.mIsUcs2 = ucs2;
        this.mIsPacked = packed;
        this.mInData = inData;
        this.mIsYesNo = false;
    }

    public GetInkeyInputResponseData(boolean yesNoResponse) {
        this.mIsUcs2 = false;
        this.mIsPacked = false;
        this.mInData = "";
        this.mIsYesNo = true;
        this.mYesNoResponse = yesNoResponse;
    }

    public GetInkeyInputResponseData(Duration duration) {
        this.mIsUcs2 = false;
        this.mIsPacked = false;
        this.mInData = "";
        this.mIsYesNo = false;
        this.mYesNoResponse = false;
        this.mIsDuration = true;
        this.mTimeUnit = (byte) duration.timeUnit.value();
        this.mDuration = (byte) duration.timeInterval;
    }

    public void format(ByteArrayOutputStream buf) {
        byte b = (byte) 1;
        if (buf != null) {
            byte[] data;
            if (this.mIsDuration) {
                buf.write(ComprehensionTlvTag.DURATION.value() | 128);
                data = new byte[]{this.mTimeUnit, (byte) (this.mDuration + 1)};
                buf.write(data.length);
                for (byte b2 : data) {
                    buf.write(b2);
                }
                return;
            }
            buf.write(ComprehensionTlvTag.TEXT_STRING.value() | 128);
            if (this.mIsYesNo) {
                data = new byte[1];
                if (!this.mYesNoResponse) {
                    b = (byte) 0;
                }
                data[0] = b;
            } else if (this.mInData == null || this.mInData.length() <= 0) {
                data = new byte[0];
            } else {
                try {
                    if (this.mIsUcs2) {
                        data = this.mInData.getBytes("UTF-16BE");
                    } else if (this.mIsPacked) {
                        int size = this.mInData.length();
                        data = new byte[size];
                        System.arraycopy(GsmAlphabet.stringToGsm7BitPacked(this.mInData, 0, 0), 1, data, 0, size);
                    } else {
                        data = GsmAlphabet.stringToGsm8BitPacked(this.mInData);
                    }
                } catch (UnsupportedEncodingException e) {
                    data = new byte[0];
                } catch (EncodeException e2) {
                    data = new byte[0];
                }
            }
            ResponseData.writeLength(buf, data.length + 1);
            if (this.mIsUcs2) {
                buf.write(8);
            } else if (this.mIsPacked) {
                buf.write(0);
            } else {
                buf.write(4);
            }
            for (byte b22 : data) {
                buf.write(b22);
            }
        }
    }
}
