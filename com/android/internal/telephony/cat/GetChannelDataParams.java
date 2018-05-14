package com.android.internal.telephony.cat;

/* compiled from: CommandParams */
class GetChannelDataParams extends CommandParams {
    int mChannelId;

    GetChannelDataParams(CommandDetails cmdDet, int channelId) {
        super(cmdDet);
        this.mChannelId = channelId;
    }
}
