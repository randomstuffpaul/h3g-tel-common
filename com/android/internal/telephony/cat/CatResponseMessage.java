package com.android.internal.telephony.cat;

public class CatResponseMessage {
    boolean mAdditionalInfo = false;
    int mAdditionalInfoData = 0;
    CommandDetails mCmdDet = null;
    boolean mIncludeAdditionalInfo = false;
    ResultCode mResCode = ResultCode.OK;
    boolean mUsersConfirm = false;
    String mUsersInput = null;
    int mUsersMenuSelection = 0;
    boolean mUsersYesNoSelection = false;

    public CatResponseMessage(CatCmdMessage cmdMsg) {
        this.mCmdDet = cmdMsg.mCmdDet;
    }

    public void setResultCode(ResultCode resCode) {
        this.mResCode = resCode;
    }

    public void setMenuSelection(int selection) {
        this.mUsersMenuSelection = selection;
    }

    public void setInput(String input) {
        this.mUsersInput = input;
    }

    public void setYesNo(boolean yesNo) {
        this.mUsersYesNoSelection = yesNo;
    }

    public void setConfirmation(boolean confirm) {
        this.mUsersConfirm = confirm;
    }

    public void setAdditionalInfo(boolean additionalInfo) {
        this.mIncludeAdditionalInfo = true;
        this.mAdditionalInfo = additionalInfo;
    }

    public void setAdditionalInfoData(int additionalInfoData) {
        this.mAdditionalInfoData = additionalInfoData;
    }

    CommandDetails getCmdDetails() {
        return this.mCmdDet;
    }
}
