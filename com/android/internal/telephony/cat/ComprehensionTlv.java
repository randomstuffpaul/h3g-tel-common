package com.android.internal.telephony.cat;

import java.util.ArrayList;
import java.util.List;

class ComprehensionTlv {
    private static final String LOG_TAG = "ComprehensionTlv";
    private boolean mCr;
    private int mLength;
    private byte[] mRawValue;
    private int mTag;
    private int mValueIndex;

    protected ComprehensionTlv(int tag, boolean cr, int length, byte[] data, int valueIndex) {
        this.mTag = tag;
        this.mCr = cr;
        this.mLength = length;
        this.mValueIndex = valueIndex;
        this.mRawValue = data;
    }

    public int getTag() {
        return this.mTag;
    }

    public boolean isComprehensionRequired() {
        return this.mCr;
    }

    public int getLength() {
        return this.mLength;
    }

    public int getValueIndex() {
        return this.mValueIndex;
    }

    public byte[] getRawValue() {
        return this.mRawValue;
    }

    public static List<ComprehensionTlv> decodeMany(byte[] data, int startIndex) throws ResultException {
        ArrayList<ComprehensionTlv> items = new ArrayList();
        int endIndex = data.length;
        while (startIndex < endIndex) {
            ComprehensionTlv ctlv = decode(data, startIndex);
            if (ctlv == null) {
                CatLog.m2d(LOG_TAG, "decodeMany: ctlv is null, stop decoding");
                break;
            }
            items.add(ctlv);
            startIndex = ctlv.mValueIndex + ctlv.mLength;
        }
        return items;
    }

    /* JADX WARNING: inconsistent code. */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    public static com.android.internal.telephony.cat.ComprehensionTlv decode(byte[] r12, int r13) throws com.android.internal.telephony.cat.ResultException {
        /*
        r11 = 128; // 0x80 float:1.794E-43 double:6.32E-322;
        r2 = 1;
        r0 = 0;
        r5 = r13;
        r8 = r12.length;
        r6 = r5 + 1;
        r4 = r12[r5];	 Catch:{ IndexOutOfBoundsException -> 0x011a }
        r9 = r4 & 255;
        switch(r9) {
            case 0: goto L_0x0026;
            case 127: goto L_0x00af;
            case 128: goto L_0x0071;
            case 255: goto L_0x0071;
            default: goto L_0x000f;
        };
    L_0x000f:
        r1 = r9;
        r4 = r1 & 128;
        if (r4 == 0) goto L_0x00ce;
    L_0x0014:
        r1 = r1 & -129;
    L_0x0016:
        r5 = r6 + 1;
        r0 = r12[r6];	 Catch:{ IndexOutOfBoundsException -> 0x003f }
        r9 = r0 & 255;
        if (r9 >= r11) goto L_0x00d1;
    L_0x001e:
        r3 = r9;
    L_0x001f:
        r0 = new com.android.internal.telephony.cat.ComprehensionTlv;	 Catch:{ IndexOutOfBoundsException -> 0x003f }
        r4 = r12;
        r0.<init>(r1, r2, r3, r4, r5);	 Catch:{ IndexOutOfBoundsException -> 0x003f }
    L_0x0025:
        return r0;
    L_0x0026:
        r4 = "ComprehensionTlv";
        r10 = "invalid tag data, make dummy tlv";
        android.telephony.Rlog.d(r4, r10);	 Catch:{ IndexOutOfBoundsException -> 0x011a }
        r1 = r9;
        r4 = r1 & 128;
        if (r4 == 0) goto L_0x006f;
    L_0x0032:
        r1 = r1 & -129;
        r5 = r6 + 1;
        r0 = new com.android.internal.telephony.cat.ComprehensionTlv;	 Catch:{ IndexOutOfBoundsException -> 0x003f }
        r3 = r8 - r5;
        r4 = r12;
        r0.<init>(r1, r2, r3, r4, r5);	 Catch:{ IndexOutOfBoundsException -> 0x003f }
        goto L_0x0025;
    L_0x003f:
        r7 = move-exception;
    L_0x0040:
        r0 = new com.android.internal.telephony.cat.ResultException;
        r4 = com.android.internal.telephony.cat.ResultCode.CMD_DATA_NOT_UNDERSTOOD;
        r10 = new java.lang.StringBuilder;
        r10.<init>();
        r11 = "IndexOutOfBoundsException startIndex=";
        r10 = r10.append(r11);
        r10 = r10.append(r13);
        r11 = " curIndex=";
        r10 = r10.append(r11);
        r10 = r10.append(r5);
        r11 = " endIndex=";
        r10 = r10.append(r11);
        r10 = r10.append(r8);
        r10 = r10.toString();
        r0.<init>(r4, r10);
        throw r0;
    L_0x006f:
        r2 = r0;
        goto L_0x0032;
    L_0x0071:
        r0 = "CAT     ";
        r4 = new java.lang.StringBuilder;	 Catch:{ IndexOutOfBoundsException -> 0x011a }
        r4.<init>();	 Catch:{ IndexOutOfBoundsException -> 0x011a }
        r10 = "decode: unexpected first tag byte=";
        r4 = r4.append(r10);	 Catch:{ IndexOutOfBoundsException -> 0x011a }
        r10 = java.lang.Integer.toHexString(r9);	 Catch:{ IndexOutOfBoundsException -> 0x011a }
        r4 = r4.append(r10);	 Catch:{ IndexOutOfBoundsException -> 0x011a }
        r10 = ", startIndex=";
        r4 = r4.append(r10);	 Catch:{ IndexOutOfBoundsException -> 0x011a }
        r4 = r4.append(r13);	 Catch:{ IndexOutOfBoundsException -> 0x011a }
        r10 = " curIndex=";
        r4 = r4.append(r10);	 Catch:{ IndexOutOfBoundsException -> 0x011a }
        r4 = r4.append(r6);	 Catch:{ IndexOutOfBoundsException -> 0x011a }
        r10 = " endIndex=";
        r4 = r4.append(r10);	 Catch:{ IndexOutOfBoundsException -> 0x011a }
        r4 = r4.append(r8);	 Catch:{ IndexOutOfBoundsException -> 0x011a }
        r4 = r4.toString();	 Catch:{ IndexOutOfBoundsException -> 0x011a }
        android.telephony.Rlog.d(r0, r4);	 Catch:{ IndexOutOfBoundsException -> 0x011a }
        r0 = 0;
        r5 = r6;
        goto L_0x0025;
    L_0x00af:
        r4 = r12[r6];	 Catch:{ IndexOutOfBoundsException -> 0x011a }
        r4 = r4 & 255;
        r4 = r4 << 8;
        r10 = r6 + 1;
        r10 = r12[r10];	 Catch:{ IndexOutOfBoundsException -> 0x011a }
        r10 = r10 & 255;
        r1 = r4 | r10;
        r4 = 32768; // 0x8000 float:4.5918E-41 double:1.61895E-319;
        r4 = r4 & r1;
        if (r4 == 0) goto L_0x00cc;
    L_0x00c3:
        r0 = -32769; // 0xffffffffffff7fff float:NaN double:NaN;
        r1 = r1 & r0;
        r5 = r6 + 2;
        r6 = r5;
        goto L_0x0016;
    L_0x00cc:
        r2 = r0;
        goto L_0x00c3;
    L_0x00ce:
        r2 = r0;
        goto L_0x0014;
    L_0x00d1:
        r0 = 129; // 0x81 float:1.81E-43 double:6.37E-322;
        if (r9 != r0) goto L_0x011e;
    L_0x00d5:
        r6 = r5 + 1;
        r0 = r12[r5];	 Catch:{ IndexOutOfBoundsException -> 0x011a }
        r3 = r0 & 255;
        if (r3 >= r11) goto L_0x020a;
    L_0x00dd:
        r0 = new com.android.internal.telephony.cat.ResultException;	 Catch:{ IndexOutOfBoundsException -> 0x011a }
        r4 = com.android.internal.telephony.cat.ResultCode.CMD_DATA_NOT_UNDERSTOOD;	 Catch:{ IndexOutOfBoundsException -> 0x011a }
        r10 = new java.lang.StringBuilder;	 Catch:{ IndexOutOfBoundsException -> 0x011a }
        r10.<init>();	 Catch:{ IndexOutOfBoundsException -> 0x011a }
        r11 = "length < 0x80 length=";
        r10 = r10.append(r11);	 Catch:{ IndexOutOfBoundsException -> 0x011a }
        r11 = java.lang.Integer.toHexString(r3);	 Catch:{ IndexOutOfBoundsException -> 0x011a }
        r10 = r10.append(r11);	 Catch:{ IndexOutOfBoundsException -> 0x011a }
        r11 = " startIndex=";
        r10 = r10.append(r11);	 Catch:{ IndexOutOfBoundsException -> 0x011a }
        r10 = r10.append(r13);	 Catch:{ IndexOutOfBoundsException -> 0x011a }
        r11 = " curIndex=";
        r10 = r10.append(r11);	 Catch:{ IndexOutOfBoundsException -> 0x011a }
        r10 = r10.append(r6);	 Catch:{ IndexOutOfBoundsException -> 0x011a }
        r11 = " endIndex=";
        r10 = r10.append(r11);	 Catch:{ IndexOutOfBoundsException -> 0x011a }
        r10 = r10.append(r8);	 Catch:{ IndexOutOfBoundsException -> 0x011a }
        r10 = r10.toString();	 Catch:{ IndexOutOfBoundsException -> 0x011a }
        r0.<init>(r4, r10);	 Catch:{ IndexOutOfBoundsException -> 0x011a }
        throw r0;	 Catch:{ IndexOutOfBoundsException -> 0x011a }
    L_0x011a:
        r7 = move-exception;
        r5 = r6;
        goto L_0x0040;
    L_0x011e:
        r0 = 130; // 0x82 float:1.82E-43 double:6.4E-322;
        if (r9 != r0) goto L_0x0173;
    L_0x0122:
        r0 = r12[r5];	 Catch:{ IndexOutOfBoundsException -> 0x003f }
        r0 = r0 & 255;
        r0 = r0 << 8;
        r4 = r5 + 1;
        r4 = r12[r4];	 Catch:{ IndexOutOfBoundsException -> 0x003f }
        r4 = r4 & 255;
        r3 = r0 | r4;
        r5 = r5 + 2;
        r0 = 256; // 0x100 float:3.59E-43 double:1.265E-321;
        if (r3 >= r0) goto L_0x001f;
    L_0x0136:
        r0 = new com.android.internal.telephony.cat.ResultException;	 Catch:{ IndexOutOfBoundsException -> 0x003f }
        r4 = com.android.internal.telephony.cat.ResultCode.CMD_DATA_NOT_UNDERSTOOD;	 Catch:{ IndexOutOfBoundsException -> 0x003f }
        r10 = new java.lang.StringBuilder;	 Catch:{ IndexOutOfBoundsException -> 0x003f }
        r10.<init>();	 Catch:{ IndexOutOfBoundsException -> 0x003f }
        r11 = "two byte length < 0x100 length=";
        r10 = r10.append(r11);	 Catch:{ IndexOutOfBoundsException -> 0x003f }
        r11 = java.lang.Integer.toHexString(r3);	 Catch:{ IndexOutOfBoundsException -> 0x003f }
        r10 = r10.append(r11);	 Catch:{ IndexOutOfBoundsException -> 0x003f }
        r11 = " startIndex=";
        r10 = r10.append(r11);	 Catch:{ IndexOutOfBoundsException -> 0x003f }
        r10 = r10.append(r13);	 Catch:{ IndexOutOfBoundsException -> 0x003f }
        r11 = " curIndex=";
        r10 = r10.append(r11);	 Catch:{ IndexOutOfBoundsException -> 0x003f }
        r10 = r10.append(r5);	 Catch:{ IndexOutOfBoundsException -> 0x003f }
        r11 = " endIndex=";
        r10 = r10.append(r11);	 Catch:{ IndexOutOfBoundsException -> 0x003f }
        r10 = r10.append(r8);	 Catch:{ IndexOutOfBoundsException -> 0x003f }
        r10 = r10.toString();	 Catch:{ IndexOutOfBoundsException -> 0x003f }
        r0.<init>(r4, r10);	 Catch:{ IndexOutOfBoundsException -> 0x003f }
        throw r0;	 Catch:{ IndexOutOfBoundsException -> 0x003f }
    L_0x0173:
        r0 = 131; // 0x83 float:1.84E-43 double:6.47E-322;
        if (r9 != r0) goto L_0x01d1;
    L_0x0177:
        r0 = r12[r5];	 Catch:{ IndexOutOfBoundsException -> 0x003f }
        r0 = r0 & 255;
        r0 = r0 << 16;
        r4 = r5 + 1;
        r4 = r12[r4];	 Catch:{ IndexOutOfBoundsException -> 0x003f }
        r4 = r4 & 255;
        r4 = r4 << 8;
        r0 = r0 | r4;
        r4 = r5 + 2;
        r4 = r12[r4];	 Catch:{ IndexOutOfBoundsException -> 0x003f }
        r4 = r4 & 255;
        r3 = r0 | r4;
        r5 = r5 + 3;
        r0 = 65536; // 0x10000 float:9.18355E-41 double:3.2379E-319;
        if (r3 >= r0) goto L_0x001f;
    L_0x0194:
        r0 = new com.android.internal.telephony.cat.ResultException;	 Catch:{ IndexOutOfBoundsException -> 0x003f }
        r4 = com.android.internal.telephony.cat.ResultCode.CMD_DATA_NOT_UNDERSTOOD;	 Catch:{ IndexOutOfBoundsException -> 0x003f }
        r10 = new java.lang.StringBuilder;	 Catch:{ IndexOutOfBoundsException -> 0x003f }
        r10.<init>();	 Catch:{ IndexOutOfBoundsException -> 0x003f }
        r11 = "three byte length < 0x10000 length=0x";
        r10 = r10.append(r11);	 Catch:{ IndexOutOfBoundsException -> 0x003f }
        r11 = java.lang.Integer.toHexString(r3);	 Catch:{ IndexOutOfBoundsException -> 0x003f }
        r10 = r10.append(r11);	 Catch:{ IndexOutOfBoundsException -> 0x003f }
        r11 = " startIndex=";
        r10 = r10.append(r11);	 Catch:{ IndexOutOfBoundsException -> 0x003f }
        r10 = r10.append(r13);	 Catch:{ IndexOutOfBoundsException -> 0x003f }
        r11 = " curIndex=";
        r10 = r10.append(r11);	 Catch:{ IndexOutOfBoundsException -> 0x003f }
        r10 = r10.append(r5);	 Catch:{ IndexOutOfBoundsException -> 0x003f }
        r11 = " endIndex=";
        r10 = r10.append(r11);	 Catch:{ IndexOutOfBoundsException -> 0x003f }
        r10 = r10.append(r8);	 Catch:{ IndexOutOfBoundsException -> 0x003f }
        r10 = r10.toString();	 Catch:{ IndexOutOfBoundsException -> 0x003f }
        r0.<init>(r4, r10);	 Catch:{ IndexOutOfBoundsException -> 0x003f }
        throw r0;	 Catch:{ IndexOutOfBoundsException -> 0x003f }
    L_0x01d1:
        r0 = new com.android.internal.telephony.cat.ResultException;	 Catch:{ IndexOutOfBoundsException -> 0x003f }
        r4 = com.android.internal.telephony.cat.ResultCode.CMD_DATA_NOT_UNDERSTOOD;	 Catch:{ IndexOutOfBoundsException -> 0x003f }
        r10 = new java.lang.StringBuilder;	 Catch:{ IndexOutOfBoundsException -> 0x003f }
        r10.<init>();	 Catch:{ IndexOutOfBoundsException -> 0x003f }
        r11 = "Bad length modifer=";
        r10 = r10.append(r11);	 Catch:{ IndexOutOfBoundsException -> 0x003f }
        r10 = r10.append(r9);	 Catch:{ IndexOutOfBoundsException -> 0x003f }
        r11 = " startIndex=";
        r10 = r10.append(r11);	 Catch:{ IndexOutOfBoundsException -> 0x003f }
        r10 = r10.append(r13);	 Catch:{ IndexOutOfBoundsException -> 0x003f }
        r11 = " curIndex=";
        r10 = r10.append(r11);	 Catch:{ IndexOutOfBoundsException -> 0x003f }
        r10 = r10.append(r5);	 Catch:{ IndexOutOfBoundsException -> 0x003f }
        r11 = " endIndex=";
        r10 = r10.append(r11);	 Catch:{ IndexOutOfBoundsException -> 0x003f }
        r10 = r10.append(r8);	 Catch:{ IndexOutOfBoundsException -> 0x003f }
        r10 = r10.toString();	 Catch:{ IndexOutOfBoundsException -> 0x003f }
        r0.<init>(r4, r10);	 Catch:{ IndexOutOfBoundsException -> 0x003f }
        throw r0;	 Catch:{ IndexOutOfBoundsException -> 0x003f }
    L_0x020a:
        r5 = r6;
        goto L_0x001f;
        */
        throw new UnsupportedOperationException("Method not decompiled: com.android.internal.telephony.cat.ComprehensionTlv.decode(byte[], int):com.android.internal.telephony.cat.ComprehensionTlv");
    }
}
