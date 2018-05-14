package com.android.internal.telephony.cat;

import android.graphics.Bitmap;

/* compiled from: CommandParams */
class CallSetupParams extends CommandParams {
    String address;
    TextMessage mCallMsg;
    TextMessage mConfirmMsg;

    CallSetupParams(CommandDetails cmdDet, TextMessage confirmMsg, TextMessage callMsg, String address) {
        super(cmdDet);
        this.address = null;
        this.mConfirmMsg = confirmMsg;
        this.mCallMsg = callMsg;
        this.address = address;
    }

    CallSetupParams(CommandDetails cmdDet, TextMessage confirmMsg, TextMessage callMsg, String address, boolean hasIcon) {
        this(cmdDet, confirmMsg, callMsg, address);
        setHasIconTag(hasIcon);
    }

    boolean setIcon(Bitmap icon) {
        if (icon == null) {
            return false;
        }
        if (this.mConfirmMsg != null && this.mConfirmMsg.icon == null) {
            this.mConfirmMsg.icon = icon;
            return true;
        } else if (this.mCallMsg == null || this.mCallMsg.icon != null) {
            return false;
        } else {
            this.mCallMsg.icon = icon;
            return true;
        }
    }
}
