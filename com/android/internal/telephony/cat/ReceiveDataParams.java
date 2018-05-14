package com.android.internal.telephony.cat;

/* compiled from: CommandParams */
class ReceiveDataParams extends CommandParams {
    byte mChannelDataLength;
    int mChannelId;
    TextMessage mTextMessage;

    ReceiveDataParams(CommandDetails cmdDet, int channelId, byte channelDataLength, TextMessage textMessage) {
        super(cmdDet);
        this.mChannelId = channelId;
        this.mChannelDataLength = channelDataLength;
        this.mTextMessage = textMessage;
    }
}
