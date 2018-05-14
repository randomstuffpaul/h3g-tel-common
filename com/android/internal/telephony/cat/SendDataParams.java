package com.android.internal.telephony.cat;

/* compiled from: CommandParams */
class SendDataParams extends CommandParams {
    byte[] mChannelData;
    int mChannelId;
    boolean mSendImmediate;
    TextMessage mTextMessage;

    SendDataParams(CommandDetails cmdDet, int channelId, byte[] channelData, boolean sendImmediate, TextMessage textMessage) {
        super(cmdDet);
        this.mChannelId = channelId;
        this.mChannelData = channelData;
        this.mSendImmediate = sendImmediate;
        this.mTextMessage = textMessage;
    }
}
