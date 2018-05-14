package com.android.internal.telephony;

public class PreferredNetworkListInfo {
    public int mGsmAct;
    public int mGsmCompactAct;
    public int mIndex;
    public int mMode;
    public String mOperator;
    public String mPlmn;
    public int mUtranAct;

    public PreferredNetworkListInfo() {
        this.mIndex = 0;
        this.mOperator = "";
        this.mPlmn = "";
        this.mGsmAct = 0;
        this.mGsmCompactAct = 0;
        this.mUtranAct = 0;
        this.mMode = 0;
    }

    public PreferredNetworkListInfo(int index, String operator, String plmn, int gsmact, int gsmcompactact, int utranact, int mode) {
        this.mIndex = index;
        this.mOperator = operator;
        this.mPlmn = plmn;
        this.mGsmAct = gsmact;
        this.mGsmCompactAct = gsmcompactact;
        this.mUtranAct = utranact;
        this.mMode = mode;
    }

    public PreferredNetworkListInfo(PreferredNetworkListInfo s) {
        copyFrom(s);
    }

    protected void copyFrom(PreferredNetworkListInfo s) {
        this.mIndex = s.mIndex;
        this.mOperator = s.mOperator;
        this.mPlmn = s.mPlmn;
        this.mGsmAct = s.mGsmAct;
        this.mGsmCompactAct = s.mGsmCompactAct;
        this.mUtranAct = s.mUtranAct;
        this.mMode = s.mMode;
    }

    public String toString() {
        return "PreferredNetworkListInfo: { index: " + this.mIndex + ", operator: " + this.mOperator + ", plmn: " + this.mPlmn + ", gsmAct: " + this.mGsmAct + ", gsmCompactAct: " + this.mGsmCompactAct + ", utranAct: " + this.mUtranAct + ", mode: " + this.mMode + " }";
    }
}
