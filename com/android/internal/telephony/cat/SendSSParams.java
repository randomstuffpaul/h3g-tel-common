package com.android.internal.telephony.cat;

/* compiled from: CommandParams */
class SendSSParams extends DisplayTextParams {
    String ssString;

    SendSSParams(CommandDetails cmdDet, TextMessage textMsg, String ssString) {
        super(cmdDet, textMsg);
        this.ssString = ssString;
    }

    SendSSParams(CommandDetails cmdDet, TextMessage textMsg, String ssString, boolean hasIcon) {
        this(cmdDet, textMsg, ssString);
        setHasIconTag(hasIcon);
    }
}
