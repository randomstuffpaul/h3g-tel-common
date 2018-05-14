package com.android.internal.telephony.cat;

/* compiled from: CommandParams */
class OpenChannelParams extends CommandParams {
    BearerDescription mBearerDesc;
    BearerMode mBearerMode;
    int mBufferSize;
    DataDestinationAddress mDataDestinationAddress;
    String mNetworkAccessName;
    TextMessage mPasswordTextMessage;
    TextMessage mTextMessage;
    TransportLevel mTransportLevel;
    TextMessage mUsernameTextMessage;

    OpenChannelParams(CommandDetails cmdDet, BearerDescription bearerDesc, int bufferSize, TransportLevel transportLevel, DataDestinationAddress dataDestinationAddress, String networkAccessName, BearerMode bearerMode, TextMessage textMessage, TextMessage usernameTextMessage, TextMessage passwordTextMessage) {
        super(cmdDet);
        this.mBearerDesc = bearerDesc;
        this.mBufferSize = bufferSize;
        this.mTransportLevel = transportLevel;
        this.mDataDestinationAddress = dataDestinationAddress;
        this.mNetworkAccessName = networkAccessName;
        this.mBearerMode = bearerMode;
        this.mTextMessage = textMessage;
        this.mUsernameTextMessage = usernameTextMessage;
        this.mPasswordTextMessage = passwordTextMessage;
    }

    OpenChannelParams(CommandDetails cmdDet, int bufferSize, TransportLevel transportLevel, DataDestinationAddress dataDestinationAddress, TextMessage textMessage) {
        super(cmdDet);
        this.mBufferSize = bufferSize;
        this.mTransportLevel = transportLevel;
        this.mDataDestinationAddress = dataDestinationAddress;
        this.mTextMessage = textMessage;
    }
}
