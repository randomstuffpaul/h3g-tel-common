package com.android.internal.telephony.cat;

public class CatEnvelopeMessage {
    byte[] additionalInfo = null;
    int destinationID = 0;
    int event = 0;
    int sourceID = 0;

    public CatEnvelopeMessage(int event, int sourceID, int destinationID, byte[] additionalInfo) {
        this.event = event;
        this.sourceID = sourceID;
        this.destinationID = destinationID;
        this.additionalInfo = additionalInfo;
    }
}
