package com.android.internal.telephony.cat;

/* compiled from: CommandParams */
class CloseChannelParams extends CommandParams {
    int mChannelId;
    CloseChannelMode mCloseChannelMode;
    TextMessage mTextMessage;

    CloseChannelParams(CommandDetails cmdDet, int channelId, CloseChannelMode closeChannelMode, TextMessage textMessage) {
        super(cmdDet);
        this.mChannelId = channelId;
        this.mCloseChannelMode = closeChannelMode;
        this.mTextMessage = textMessage;
    }
}
