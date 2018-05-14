package com.android.internal.telephony;

import android.os.Handler;
import android.os.Message;
import com.android.internal.telephony.IccCardConstants.State;
import com.android.internal.telephony.uicc.IccCardApplicationStatus.AppType;
import com.android.internal.telephony.uicc.IccFileHandler;
import com.android.internal.telephony.uicc.IccRecords;
import com.android.internal.telephony.uicc.SimLockInfoResult;

public interface IccCard {
    public static final String INTENT_KEY_ICC_STATE = "ss";

    void changeIccFdnPassword(String str, String str2, Message message);

    void changeIccLockPassword(String str, String str2, Message message);

    void changeIccSimPersoPassword(String str, String str2, Message message);

    String getFPLMN();

    boolean getIccFdnAvailable();

    boolean getIccFdnEnabled();

    IccFileHandler getIccFileHandler();

    boolean getIccLockEnabled();

    int getIccPin1RetryCount();

    boolean getIccPin2Blocked();

    int getIccPin2RetryCount();

    int getIccPuk1RetryCount();

    boolean getIccPuk2Blocked();

    int getIccPuk2retryCount();

    IccRecords getIccRecords();

    boolean getIccUsimPersoEnabled();

    String getOPLMNwAct();

    String getPLMNwAcT();

    String getServiceProviderName();

    SimLockInfoResult getSimLockInfoResult();

    State getState();

    boolean hasIccCard();

    boolean isApplicationOnIcc(AppType appType);

    void registerForAbsent(Handler handler, int i, Object obj);

    void registerForLocked(Handler handler, int i, Object obj);

    void registerForNetworkLocked(Handler handler, int i, Object obj);

    void registerForNetworkSubsetLocked(Handler handler, int i, Object obj);

    void registerForSPLocked(Handler handler, int i, Object obj);

    void reloadPLMNs();

    void setEPSLOCI(byte[] bArr);

    void setEPSLOCI(byte[] bArr, Message message);

    void setFPLMN(byte[] bArr);

    void setFPLMN(byte[] bArr, Message message);

    void setIccFdnEnabled(boolean z, String str, Message message);

    void setIccLockEnabled(boolean z, String str, Message message);

    void setIccSimPersoEnabled(boolean z, String str, Message message);

    void setLOCI(byte[] bArr);

    void setLOCI(byte[] bArr, Message message);

    void setOPLMNwAct(byte[] bArr);

    void setPLMNwAcT(byte[] bArr);

    void setPSLOCI(byte[] bArr);

    void setPSLOCI(byte[] bArr, Message message);

    void setRoaming(byte[] bArr, Message message);

    void supplyNetworkDepersonalization(String str, Message message);

    void supplyPerso(String str, Message message);

    void supplyPin(String str, Message message);

    void supplyPin2(String str, Message message);

    void supplyPuk(String str, String str2, Message message);

    void supplyPuk2(String str, String str2, Message message);

    void unregisterForAbsent(Handler handler);

    void unregisterForLocked(Handler handler);

    void unregisterForNetworkLocked(Handler handler);

    void unregisterForNetworkSubsetLocked(Handler handler);

    void unregisterForSPLocked(Handler handler);
}
