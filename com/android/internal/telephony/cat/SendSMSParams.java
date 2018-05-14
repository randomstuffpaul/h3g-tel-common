package com.android.internal.telephony.cat;

import android.graphics.Bitmap;

/* compiled from: CommandParams */
class SendSMSParams extends DisplayTextParams {
    String Format;
    String Pdu;
    String SmscAddress;
    TextMessage textMsg;

    SendSMSParams(CommandDetails cmdDet, TextMessage textMsg, String Smscaddress, String Pdu, String Format) {
        super(cmdDet, textMsg);
        this.SmscAddress = Smscaddress;
        this.Pdu = Pdu;
        this.Format = Format;
    }

    SendSMSParams(CommandDetails cmdDet, TextMessage textMsg, String Smscaddress, String Pdu, String Format, boolean hasIcon) {
        this(cmdDet, textMsg, Smscaddress, Pdu, Format);
        setHasIconTag(hasIcon);
    }

    boolean setIcon(Bitmap icon) {
        if (icon == null || this.textMsg == null) {
            return false;
        }
        this.textMsg.icon = icon;
        return true;
    }
}
