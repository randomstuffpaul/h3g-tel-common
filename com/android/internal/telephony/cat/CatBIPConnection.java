package com.android.internal.telephony.cat;

public abstract class CatBIPConnection {
    int bufferSize;
    int channelId;
    byte linkStateCause;
    public boolean mBuffsizeModified;
    CatBIPManager mCatBIPManager;
    TransportLevel uiccTerminalIface;

    public abstract void terminateStreams();

    public CatBIPConnection(int buffSize, TransportLevel iface, CatBIPManager catBIPManager) {
        if (buffSize > 1500) {
            buffSize = 1500;
            this.mBuffsizeModified = true;
        } else {
            this.mBuffsizeModified = false;
        }
        this.bufferSize = buffSize;
        this.uiccTerminalIface = iface;
        this.linkStateCause = (byte) 0;
        this.mCatBIPManager = catBIPManager;
    }
}
