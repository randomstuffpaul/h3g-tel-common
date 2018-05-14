package com.android.internal.telephony.uicc;

import android.os.SystemProperties;
import com.google.android.mms.pdu.PduHeaders;

public class IccIoResult {
    boolean SHIP_BUILD;
    public byte[] payload;
    public int sw1;
    public int sw2;

    public IccIoResult(int sw1, int sw2, byte[] payload) {
        this.SHIP_BUILD = "true".equals(SystemProperties.get("ro.product_ship", "false"));
        this.sw1 = sw1;
        this.sw2 = sw2;
        this.payload = payload;
    }

    public IccIoResult(int sw1, int sw2, String hexString) {
        this(sw1, sw2, IccUtils.hexStringToBytes(hexString));
    }

    public String toString() {
        if (this.SHIP_BUILD) {
            return "IccIoResponse xx xx";
        }
        return "IccIoResponse sw1:0x" + Integer.toHexString(this.sw1) + " sw2:0x" + Integer.toHexString(this.sw2);
    }

    public boolean success() {
        return this.sw1 == 144 || this.sw1 == 145 || this.sw1 == PduHeaders.REPLY_CHARGING_ID || this.sw1 == PduHeaders.REPLY_CHARGING_SIZE;
    }

    public IccException getException() {
        if (success()) {
            return null;
        }
        switch (this.sw1) {
            case 148:
                if (this.sw2 == 8) {
                    return new IccFileTypeMismatch();
                }
                return new IccFileNotFound();
            default:
                if (this.SHIP_BUILD) {
                    return new IccException("sw1: xx sw2: xx");
                }
                return new IccException("sw1:" + this.sw1 + " sw2:" + this.sw2);
        }
    }
}
