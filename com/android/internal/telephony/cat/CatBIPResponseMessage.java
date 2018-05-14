package com.android.internal.telephony.cat;

public class CatBIPResponseMessage {
    int AdditionalInfo;
    ResponseData data;
    boolean hasAdditionalInfo;
    CommandDetails mCmdDet;
    ResultCode resCode;

    public CatBIPResponseMessage(CommandDetails cmd, ResultCode r, boolean AddInfoPresent, int AddInfo, ResponseData d) {
        this.resCode = r;
        this.hasAdditionalInfo = AddInfoPresent;
        this.AdditionalInfo = AddInfo;
        this.data = d;
        this.mCmdDet = cmd;
    }
}
