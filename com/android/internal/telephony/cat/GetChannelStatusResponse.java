package com.android.internal.telephony.cat;

import java.io.ByteArrayOutputStream;
import java.util.Iterator;

/* compiled from: ResponseData */
class GetChannelStatusResponse extends ResponseData {
    boolean[] channelIds;
    Iterator f9i = null;

    public void format(ByteArrayOutputStream buf) {
        for (int z = 0; z < 7; z++) {
            if (this.channelIds[z]) {
                CatBIPConnection bipcon = (CatBIPConnection) this.f9i.next();
                buf.write(ComprehensionTlvTag.CHANNEL_STATUS.value() | 128);
                buf.write(2);
                byte s = (byte) (bipcon.channelId & 7);
                if (bipcon.uiccTerminalIface.isServer() || bipcon.uiccTerminalIface.isLocal()) {
                    s = (byte) ((((CatBIPServerConnection) bipcon).linkState << 6) | s);
                } else if (((CatBIPClientConnection) bipcon).isLinkEstablished) {
                    s = (byte) (s | -128);
                }
                buf.write(s);
                buf.write(bipcon.linkStateCause);
                CatLog.m0d((Object) this, "GetChannelStatusResponse-wrote all");
            }
        }
    }

    public GetChannelStatusResponse(Iterator iter, boolean[] chIds) {
        this.f9i = iter;
        this.channelIds = chIds;
    }
}
