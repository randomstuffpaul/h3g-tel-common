package com.android.internal.telephony.cat;

import java.io.ByteArrayOutputStream;

/* compiled from: ResponseData */
class OpenChannelResponseData extends ResponseData {
    CatBIPConnection bipcon;
    OpenChannelParams param;

    public void format(ByteArrayOutputStream buf) {
        int bufsize;
        CatLog.m0d((Object) this, " OpenChannelResponseData: format() ");
        if (this.bipcon != null) {
            buf.write(ComprehensionTlvTag.CHANNEL_STATUS.value() | 128);
            buf.write(2);
            byte s = (byte) (this.bipcon.channelId & 7);
            if (this.bipcon.uiccTerminalIface.isServer() || this.bipcon.uiccTerminalIface.isLocal()) {
                s = (byte) ((this.bipcon.linkState << 6) | s);
            } else if (this.bipcon.isLinkEstablished) {
                s = (byte) (s | -128);
            }
            buf.write(s);
            buf.write(this.bipcon.linkStateCause);
        }
        if (this.param.mTransportLevel.isTCPRemoteClient() || this.param.mTransportLevel.isUDPRemoteClient()) {
            CatLog.m0d((Object) this, " OpenChannelResponseData: format() : bipcon is client including Bearer description terminal reponse");
            buf.write(ComprehensionTlvTag.BEARER_DESCRIPTION.value() | 128);
            ByteArrayOutputStream tmp = new ByteArrayOutputStream();
            tmp.write(this.param.mBearerDesc.bearerType);
            switch (this.param.mBearerDesc.bearerType) {
                case (byte) 1:
                    this.param.mBearerDesc.bearerCSD.writeParametersTobuffer(tmp);
                    break;
                case (byte) 2:
                    this.param.mBearerDesc.bearerGPRS.writeParametersTobuffer(tmp);
                    break;
                case (byte) 11:
                    this.param.mBearerDesc.bearerEUTRAN.writeParametersTobuffer(tmp);
                    break;
            }
            buf.write(tmp.size());
            buf.write(tmp.toByteArray(), 0, tmp.size());
        }
        if (this.bipcon != null) {
            bufsize = this.bipcon.bufferSize;
        } else {
            bufsize = this.param.mBufferSize;
        }
        buf.write(ComprehensionTlvTag.BUFFER_SIZE.value() | 128);
        buf.write(2);
        buf.write(bufsize >> 8);
        buf.write(bufsize);
    }

    public OpenChannelResponseData(CatBIPConnection con) {
        this.bipcon = null;
        this.param = null;
        this.bipcon = con;
        this.param = (OpenChannelParams) CatService.mBIPCurrntCmd;
    }

    public OpenChannelResponseData() {
        this.bipcon = null;
        this.param = null;
        this.param = (OpenChannelParams) CatService.mBIPCurrntCmd;
    }
}
