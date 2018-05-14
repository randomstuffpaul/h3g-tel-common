package com.android.internal.telephony.cat;

/* compiled from: CommandParams */
class SendDTMFParams extends DisplayTextParams {
    byte[] dtmfString;

    SendDTMFParams(CommandDetails cmdDet, TextMessage textMsg, byte[] dtmfString) {
        super(cmdDet, textMsg);
        this.dtmfString = dtmfString;
    }

    SendDTMFParams(CommandDetails cmdDet, TextMessage textMsg, byte[] dtmfString, boolean hasIcon) {
        this(cmdDet, textMsg, dtmfString);
        setHasIconTag(hasIcon);
    }
}
