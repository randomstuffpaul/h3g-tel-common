package com.android.internal.telephony.cat;

import android.graphics.Bitmap;

/* compiled from: CommandParams */
class LaunchBrowserParams extends CommandParams {
    TextMessage mConfirmMsg;
    String mGatewayProxy;
    LaunchBrowserMode mMode;
    String mUrl;

    LaunchBrowserParams(CommandDetails cmdDet, TextMessage confirmMsg, String url, LaunchBrowserMode mode) {
        super(cmdDet);
        this.mConfirmMsg = confirmMsg;
        this.mMode = mode;
        this.mUrl = url;
    }

    LaunchBrowserParams(CommandDetails cmdDet, TextMessage confirmMsg, String url, LaunchBrowserMode mode, String gatewayProxy) {
        super(cmdDet);
        this.mConfirmMsg = confirmMsg;
        this.mMode = mode;
        this.mUrl = url;
        this.mGatewayProxy = gatewayProxy;
    }

    LaunchBrowserParams(CommandDetails cmdDet, TextMessage confirmMsg, String url, LaunchBrowserMode mode, String gatewayProxy, boolean hasIcon) {
        this(cmdDet, confirmMsg, url, mode, gatewayProxy);
        setHasIconTag(hasIcon);
    }

    boolean setIcon(Bitmap icon) {
        if (icon == null || this.mConfirmMsg == null) {
            return false;
        }
        this.mConfirmMsg.icon = icon;
        return true;
    }
}
