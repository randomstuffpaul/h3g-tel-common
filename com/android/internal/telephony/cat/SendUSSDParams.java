package com.android.internal.telephony.cat;

/* compiled from: CommandParams */
class SendUSSDParams extends DisplayTextParams {
    int dcsCode;
    int ussdLength;
    byte[] ussdString;

    SendUSSDParams(CommandDetails cmdDet, TextMessage textMsg, byte[] ussdString) {
        super(cmdDet, textMsg);
        this.ussdLength = ussdString.length - 1;
        this.ussdString = new byte[this.ussdLength];
        for (int i = 0; i < this.ussdLength; i++) {
            this.ussdString[i] = ussdString[i + 1];
        }
        this.dcsCode = ussdString[0];
    }

    SendUSSDParams(CommandDetails cmdDet, TextMessage textMsg, byte[] ussdString, boolean hasIcon) {
        this(cmdDet, textMsg, ussdString);
        setHasIconTag(hasIcon);
    }
}
